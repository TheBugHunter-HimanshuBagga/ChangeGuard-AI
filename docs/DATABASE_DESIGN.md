# ChangeGuard AI — Database Design

Status: Phase 0 baseline (logical design only)

Persistence is owned exclusively by the Control Plane (`backend/springboot-app`). The Intelligence Plane has no database access. The Execution Plane stores only ephemeral job state/artifacts outside PostgreSQL (or receives artifact URIs); durable application state lives here.

- Engine: PostgreSQL 16+
- Access: Spring Data JPA
- Schema changes: Flyway migrations only (never manual DDL in production)
- Naming: snake_case tables/columns; UUID primary keys unless noted

---

## 1. Migration Strategy

```
backend/springboot-app/src/main/resources/db/migration/
  V001__baseline_schema.sql
  V002__....sql
```

Rules:

- Versioned, immutable migrations (`V` prefix for versioned, `R` only if repair scripts are explicitly adopted later).
- One logical concern per migration where practical.
- Migrations run automatically on application start in non-prod; controlled job in prod (standard Flyway practice).
- No runtime entity auto-DDL (`spring.jpa.hibernate.ddl-auto=validate` in prod).

---

## 2. Entity-Relationship Overview

```
users ──────────────┐
                    ├── workflow_runs ── workflow_steps
organizations ── organization_members        │
                    ├── repositories         │
                    │                        ├── analysis_results
github_installations┘                        ├── risk_predictions
                                             ├── test_cases
                                             ├── execution_jobs ── execution_artifacts
                                             ├── failure_findings
                                             ├── decisions ── decision_evidence
                                             └── audit_events (workflow_id optional)
```

---

## 3. Tables

### 3.1 `users`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| email | varchar(320) UNIQUE + unique index on `lower(email)` | Case-insensitive uniqueness via lower() index (Phase 1: replaced citext for Hibernate `validate` compatibility; emails normalized to lowercase at app level) |
| display_name | varchar(255) | |
| password_hash | varchar(255) | null if external IdP later |
| role | varchar(32) | `ADMIN`, `MEMBER`, `VIEWER` |
| enabled | boolean | default true |
| created_at / updated_at | timestamptz | |

### 3.2 `organizations`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| name | varchar(255) UNIQUE | |
| slug | varchar(255) UNIQUE | |
| created_at / updated_at | timestamptz | |

### 3.2a `organization_members` (added in Phase 1)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| organization_id | uuid FK → organizations ON DELETE CASCADE | |
| user_id | uuid FK → users ON DELETE CASCADE | |
| role | varchar(32) | `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` (org-scoped) |
| created_at / updated_at | timestamptz | |

Unique: `(organization_id, user_id)`. Index on `user_id`.
Note: the global `users.role` (§3.1) and this org-scoped role are distinct.

### 3.3 `repositories`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| organization_id | uuid FK → organizations | |
| github_repo_id | bigint UNIQUE | GitHub numeric id |
| full_name | varchar(255) | e.g. `acme/service` |
| default_branch | varchar(255) | |
| languages_enabled | varchar(64)[] | e.g. `{JAVA,PYTHON}` |
| webhook_secret_set | boolean | never store the secret itself |
| active | boolean | |
| created_at / updated_at | timestamptz | |

### 3.4 `github_installations`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| installation_id | bigint UNIQUE | GitHub installation id |
| account_login | varchar(255) | |
| account_type | varchar(32) | `User`/`Organization` |
| permissions_json | jsonb | snapshot of granted permissions |
| suspended | boolean | |
| created_at / updated_at | timestamptz | |

Token material is **not** persisted here (minted on demand or TTL-cached in Redis).

### 3.5 `webhook_deliveries` (idempotency)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| delivery_id | uuid UNIQUE | `X-GitHub-Delivery` |
| event_type | varchar(128) | |
| received_at | timestamptz | |
| processed_at | timestamptz null | |
| processing_status | varchar(32) | `RECEIVED`, `PROCESSED`, `FAILED`, `DUPLICATE_IGNORED` |
| payload_sha256 | char(64) | integrity/debug, not a secret |

### 3.6 `workflow_runs`

Core workflow state machine row.

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | = `workflowId` |
| repository_id | uuid FK → repositories | |
| pull_request_number | bigint null | null for non-PR runs if ever supported |
| event_type | varchar(64) | e.g. `pull_request.synchronize` |
| commit_sha | varchar(40) | **required head SHA** |
| base_commit_sha | varchar(40) null | |
| status | varchar(16) | `PENDING`,`RUNNING`,`PASS`,`FAIL`,`INCONCLUSIVE`,`CANCELLED` |
| current_step | varchar(64) null | |
| correlation_id | uuid | |
| idempotency_key | varchar(255) UNIQUE | `(repoId, pr, sha, event)` derived |
| risk_max_score | numeric(5,4) null | denormalized for list views |
| started_at / completed_at | timestamptz null | |
| created_at / updated_at | timestamptz | |

Indexes: `(repository_id, status)`, `(commit_sha)`, `(correlation_id)`.

### 3.7 `workflow_steps`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| workflow_id | uuid FK → workflow_runs ON DELETE CASCADE | |
| step_key | varchar(64) | `PARSE`, `CHANGE_DETECTION`, `DEPENDENCY_IMPACT`, `RISK_PREDICTION`, `TEST_SELECTION`, `TEST_GENERATION`, `TEST_VALIDATION`, `EXECUTION`, `COVERAGE`, `FAILURE_ANALYSIS`, `DECISION`, `PUBLISH` |
| status | varchar(16) | canonical statuses |
| attempt | int | default 1 |
| input_ref / output_ref | text null | artifact or payload refs |
| error_code | varchar(64) null | |
| error_message | text null | |
| started_at / finished_at | timestamptz null | |
| created_at / updated_at | timestamptz | |

Unique: `(workflow_id, step_key, attempt)`.

### 3.8 `analysis_results`

Normalized code-analysis contract storage (see `API_DESIGN.md` §8).

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| workflow_id | uuid FK | |
| commit_sha | varchar(40) | |
| analysis_type | varchar(32) | `PARSE`,`CHANGE`,`DEPENDENCY`,`EMBEDDING` |
| language | varchar(16) null | `JAVA`/`PYTHON`/null for cross-cutting |
| payload_json | jsonb | language-neutral contract body |
| engine_version | varchar(64) | ml-engine build/version |
| created_at | timestamptz | |

Index: `(workflow_id, analysis_type)`.

### 3.9 `risk_predictions`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| workflow_id | uuid FK | |
| commit_sha | varchar(40) | |
| unit_id | varchar(255) | language-neutral unit id |
| score | numeric(5,4) | [0,1] |
| level | varchar(16) | `LOW`,`MEDIUM`,`HIGH` |
| model_version | varchar(128) | **mandatory** |
| explainability_json | jsonb null | SHAP payload |
| created_at | timestamptz | |

Index: `(workflow_id, score DESC)`, `(model_version)`.

### 3.10 `test_cases`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | = `testId` |
| workflow_id | uuid FK | |
| origin | varchar(24) | `SELECTED_EXISTING`,`AI_GENERATED` |
| language | varchar(16) | |
| target_unit_id | varchar(255) null | |
| title | varchar(512) | |
| framework | varchar(32) | `JUNIT5`,`PYTEST` |
| code | text | generated code (bounded size) |
| source_path | varchar(512) | path in generated bundle |
| validation_status | varchar(16) | `PENDING`,`VALID`,`INVALID`,`SKIPPED` |
| validation_notes_json | jsonb null | |
| created_at | timestamptz | |

### 3.11 `execution_jobs`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | = `jobId` |
| workflow_id | uuid FK | |
| commit_sha | varchar(40) | |
| language | varchar(16) | |
| status | varchar(16) | canonical statuses |
| runner_id | varchar(128) null | which worker claimed it |
| submitted_at / started_at / finished_at | timestamptz | |
| exit_code | int null | overall |
| timed_out | boolean default false | |
| error_code | varchar(64) null | |
| error_message | text null | |
| created_at / updated_at | timestamptz | |

### 3.12 `execution_artifacts`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| job_id | uuid FK → execution_jobs | |
| name | varchar(255) | e.g. `junit-report.xml` |
| kind | varchar(32) | `STDOUT`,`STDERR`,`TEST_REPORT`,`COVERAGE`,`OTHER` |
| uri | text | object storage / volume ref (not inline blobs) |
| content_type | varchar(128) | |
| size_bytes | bigint null | |
| sha256 | char(64) null | |
| created_at | timestamptz | |

### 3.13 `failure_findings`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| workflow_id | uuid FK | |
| job_id | uuid FK null | |
| test_id | uuid FK null → test_cases | |
| category | varchar(32) | `ASSERTION`,`COMPILE`,`DEPENDENCY`,`INFRA`,`FLAKY_SUSPECT`,`UNKNOWN` |
| summary | text | |
| suspect_unit_ids | jsonb | array of unit ids |
| confidence | numeric(5,4) null | null when not ML-classified |
| model_version | varchar(128) null | set when ML used |
| created_at | timestamptz | |

### 3.14 `decisions`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| workflow_id | uuid UNIQUE FK | one final decision per workflow |
| commit_sha | varchar(40) | |
| status | varchar(16) | terminal only: `PASS`,`FAIL`,`INCONCLUSIVE`,`CANCELLED` |
| policy_json | jsonb | required checks results |
| risk_context_json | jsonb | context only; not sole FAIL cause |
| rationale | text | human-readable |
| published_to_github | boolean default false | |
| published_at | timestamptz null | |
| decided_at | timestamptz | |
| created_at | timestamptz | |

### 3.15 `decision_evidence`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| decision_id | uuid FK → decisions CASCADE | |
| evidence_type | varchar(32) | `EXECUTION`,`COVERAGE`,`FAILURE_ANALYSIS`,`LOG`,`ANALYSIS` |
| uri | text | |
| description | varchar(512) null | |
| created_at | timestamptz | |

### 3.16 `audit_events`

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| occurred_at | timestamptz | |
| actor_user_id | uuid null | null for system/webhook actors |
| actor_type | varchar(32) | `USER`,`SYSTEM`,`GITHUB` |
| action | varchar(64) | e.g. `DECISION_PUBLISHED`, `REPO_LINKED`, `LOGIN` |
| target_type | varchar(64) null | |
| target_id | varchar(64) null | |
| workflow_id | uuid null | |
| correlation_id | uuid null | |
| metadata_json | jsonb null | no secrets |

---

## 4. Redis Usage (not system of record)

| Key pattern | Purpose | TTL |
| --- | --- | --- |
| `webhook:processed:{deliveryId}` | Fast idempotency check (DB remains source of truth) | hours |
| `workflow:progress:{workflowId}` | Latest step progress for UI subscribe | minutes |
| `gh:token:installation:{installationId}` | Short-lived installation token cache | token expiry − skew |
| `ratelimit:*` | API rate limiting | seconds–minutes |

Redis data is disposable; losing it must not lose workflows.

---

## 5. State Machine Transitions (workflow_runs.status)

```
PENDING → RUNNING | CANCELLED
RUNNING → PASS | FAIL | INCONCLUSIVE | CANCELLED
(terminal states have no outgoing transitions)
```

Illegal transitions → `409 CONFLICT` at API layer; enforced in service layer.

---

## 6. Retention & Size Notes

- Diff payloads and large reports live as artifacts (filesystem/S3-style URIs), not unbounded in Postgres; `payload_json` reserved for bounded structured contracts.
- `test_cases.code` capped at configured max (enforced at API).
- Audit events append-only; partitioning by month can be added in a later migration if volume requires.

---

## 7. Future Migration Hints (not created now)

- `V001__baseline_schema.sql` will create all tables above when Control Plane implementation begins.
- Seed data (roles, demo org) only via repeatable/versioned migrations with environment guards — never manual prod inserts.
