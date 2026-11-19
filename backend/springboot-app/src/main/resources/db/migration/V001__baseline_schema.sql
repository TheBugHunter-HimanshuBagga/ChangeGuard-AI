-- V001: baseline schema (docs/DATABASE_DESIGN.md)
-- Includes organization_members (Phase 1 addition, documented in DATABASE_DESIGN.md).
-- Email uses VARCHAR + lower() unique index (Hibernate-validate compatible);
-- case-insensitive uniqueness is enforced by the index and app-level normalization.

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(320) NOT NULL,
    display_name   VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(32) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

-- ---------------------------------------------------------- organizations
CREATE TABLE organizations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL UNIQUE,
    slug         VARCHAR(255) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------- organization_members (Phase 1)
CREATE TABLE organization_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_org_members_user ON organization_members (user_id);

-- ------------------------------------------------------------ repositories
CREATE TABLE repositories (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations (id),
    github_repo_id       BIGINT NOT NULL UNIQUE,
    full_name            VARCHAR(255) NOT NULL,
    default_branch       VARCHAR(255),
    languages_enabled    VARCHAR(64)[],
    webhook_secret_set   BOOLEAN NOT NULL DEFAULT FALSE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_repo_org_fullname UNIQUE (organization_id, full_name)
);

CREATE INDEX idx_repositories_org ON repositories (organization_id);

-- ----------------------------------------------------- github_installations
CREATE TABLE github_installations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id   BIGINT NOT NULL UNIQUE,
    account_login     VARCHAR(255) NOT NULL,
    account_type      VARCHAR(32) NOT NULL,
    permissions_json  JSONB,
    suspended         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------- webhook_deliveries
CREATE TABLE webhook_deliveries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id       UUID NOT NULL UNIQUE,
    event_type        VARCHAR(128) NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    processing_status VARCHAR(32) NOT NULL,
    payload_sha256    CHAR(64) NOT NULL
);

-- ------------------------------------------------------------ workflow_runs
CREATE TABLE workflow_runs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id        UUID NOT NULL REFERENCES repositories (id),
    pull_request_number  BIGINT,
    event_type           VARCHAR(64) NOT NULL,
    commit_sha           VARCHAR(40) NOT NULL,
    base_commit_sha      VARCHAR(40),
    status               VARCHAR(16) NOT NULL,
    current_step         VARCHAR(64),
    correlation_id       UUID NOT NULL,
    idempotency_key      VARCHAR(255) NOT NULL UNIQUE,
    risk_max_score       NUMERIC(5, 4),
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_runs_repo_status ON workflow_runs (repository_id, status);
CREATE INDEX idx_workflow_runs_commit ON workflow_runs (commit_sha);
CREATE INDEX idx_workflow_runs_correlation ON workflow_runs (correlation_id);

-- ----------------------------------------------------------- workflow_steps
CREATE TABLE workflow_steps (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id   UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    step_key      VARCHAR(64) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    attempt       INT NOT NULL DEFAULT 1,
    input_ref     TEXT,
    output_ref    TEXT,
    error_code    VARCHAR(64),
    error_message TEXT,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_step_attempt UNIQUE (workflow_id, step_key, attempt)
);

CREATE INDEX idx_workflow_steps_workflow ON workflow_steps (workflow_id);

-- -------------------------------------------------------- analysis_results
CREATE TABLE analysis_results (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id    UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    commit_sha     VARCHAR(40) NOT NULL,
    analysis_type  VARCHAR(32) NOT NULL,
    language       VARCHAR(16),
    payload_json   JSONB NOT NULL,
    engine_version VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analysis_results_workflow_type ON analysis_results (workflow_id, analysis_type);

-- ------------------------------------------------------- risk_predictions
CREATE TABLE risk_predictions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id        UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    commit_sha         VARCHAR(40) NOT NULL,
    unit_id            VARCHAR(255) NOT NULL,
    score              NUMERIC(5, 4) NOT NULL,
    level              VARCHAR(16) NOT NULL,
    model_version      VARCHAR(128) NOT NULL,
    explainability_json JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_predictions_workflow_score ON risk_predictions (workflow_id, score DESC);
CREATE INDEX idx_risk_predictions_model ON risk_predictions (model_version);

-- ------------------------------------------------------------- test_cases
CREATE TABLE test_cases (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id          UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    origin               VARCHAR(24) NOT NULL,
    language             VARCHAR(16) NOT NULL,
    target_unit_id       VARCHAR(255),
    title                VARCHAR(512) NOT NULL,
    framework            VARCHAR(32) NOT NULL,
    code                 TEXT NOT NULL,
    source_path          VARCHAR(512) NOT NULL,
    validation_status    VARCHAR(16) NOT NULL,
    validation_notes_json JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------- execution_jobs
CREATE TABLE execution_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id   UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    commit_sha    VARCHAR(40) NOT NULL,
    language      VARCHAR(16) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    runner_id     VARCHAR(128),
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    exit_code     INT,
    timed_out     BOOLEAN NOT NULL DEFAULT FALSE,
    error_code    VARCHAR(64),
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_execution_jobs_workflow ON execution_jobs (workflow_id);

-- ------------------------------------------------------ execution_artifacts
CREATE TABLE execution_artifacts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID NOT NULL REFERENCES execution_jobs (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    kind         VARCHAR(32) NOT NULL,
    uri          TEXT NOT NULL,
    content_type VARCHAR(128),
    size_bytes   BIGINT,
    sha256       CHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_execution_artifacts_job ON execution_artifacts (job_id);

-- -------------------------------------------------------- failure_findings
CREATE TABLE failure_findings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id       UUID NOT NULL REFERENCES workflow_runs (id) ON DELETE CASCADE,
    job_id            UUID REFERENCES execution_jobs (id) ON DELETE SET NULL,
    test_id           UUID REFERENCES test_cases (id) ON DELETE SET NULL,
    category          VARCHAR(32) NOT NULL,
    summary           TEXT NOT NULL,
    suspect_unit_ids  JSONB,
    confidence        NUMERIC(5, 4),
    model_version     VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --------------------------------------------------------------- decisions
CREATE TABLE decisions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id           UUID NOT NULL UNIQUE REFERENCES workflow_runs (id) ON DELETE CASCADE,
    commit_sha            VARCHAR(40) NOT NULL,
    status                VARCHAR(16) NOT NULL,
    policy_json           JSONB,
    risk_context_json     JSONB,
    rationale             TEXT NOT NULL,
    published_to_github   BOOLEAN NOT NULL DEFAULT FALSE,
    published_at          TIMESTAMPTZ,
    decided_at            TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- -------------------------------------------------------- decision_evidence
CREATE TABLE decision_evidence (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id  UUID NOT NULL REFERENCES decisions (id) ON DELETE CASCADE,
    evidence_type VARCHAR(32) NOT NULL,
    uri          TEXT NOT NULL,
    description  VARCHAR(512),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_decision_evidence_decision ON decision_evidence (decision_id);

-- ------------------------------------------------------------ audit_events
CREATE TABLE audit_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_type     VARCHAR(32) NOT NULL,
    action         VARCHAR(64) NOT NULL,
    target_type    VARCHAR(64),
    target_id      VARCHAR(64),
    workflow_id    UUID,
    correlation_id UUID,
    metadata_json  JSONB
);

CREATE INDEX idx_audit_events_workflow ON audit_events (workflow_id);
CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at);
