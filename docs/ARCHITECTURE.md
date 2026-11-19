# ChangeGuard AI — Architecture

Status: Phase 0 baseline (design only, no runtime implementation yet)

This document defines the system architecture, plane boundaries, service responsibilities, communication patterns, workflow lifecycle, and integration dependencies for ChangeGuard AI.

---

## 1. Vision Summary

ChangeGuard AI is an autonomous AI-powered Software Quality Engineering platform that inspects software changes (GitHub Pull Requests), predicts defect risks, understands code dependencies, generates tests, executes tests in isolated environments, analyses failures, and publishes evidence-backed decisions to GitHub.

Supported repository languages (Phase 0 scope definition): **Java** and **Python**, through a single language-neutral analysis contract.

---

## 2. Architectural Principles

These rules are binding for all future phases:

1. Spring Boot owns all persistent application data.
2. FastAPI must never directly access or modify the main application database.
3. Spring Boot talks to FastAPI only through documented, versioned HTTP APIs.
4. Execution workers are isolated from the Spring Boot application.
5. Repository code is untrusted and must never execute directly on the backend host.
6. All long-running workflows are asynchronous.
7. Every workflow instance has a unique workflow ID.
8. Every analysis records its source commit SHA.
9. Every model prediction records its model version.
10. Every final decision has traceable evidence.
11. Java and Python analysis share one language-neutral interface.
12. Production schemas change only through database migrations (Flyway).

---

## 3. High-Level Component Diagram

```
                         ┌──────────────────────────────┐
                         │          GitHub App          │
                         │  (webhooks, checks, PR API)  │
                         └──────────────┬───────────────┘
                                        │ HTTPS (webhook / REST)
                                        ▼
┌───────────────────────────────────────────────────────────────────┐
│                     CONTROL PLANE (Spring Boot)                  │
│  Auth · Orgs/Repos · Webhooks · Workflow orchestration · Jobs    │
│  Decision engine · Reporting · Audit logging                     │
│                                                                   │
│  Owns: PostgreSQL (Flyway migrations) · Redis (cache/queues)     │
└────────────┬──────────────────────────┬───────────────────────────┘
             │ HTTPS (versioned JSON)   │ HTTPS (versioned JSON)
             ▼                          ▼
┌─────────────────────────┐   ┌─────────────────────────────────────┐
│  INTELLIGENCE PLANE     │   │  EXECUTION PLANE                    │
│  (FastAPI)              │   │  (isolated Docker workers)          │
│  Parse · Features ·     │   │  JDK 21 + Maven + JUnit 5           │
│  Defect prediction ·    │   │  Python 3.12 + Pytest + Coverage.py │
│  Embeddings · Risk ·    │   │  Sandboxed clone/build/test runs    │
│  Test gen · Failure     │   └─────────────────────────────────────┘
│  analysis · SHAP        │
└─────────────────────────┘

┌───────────────────────────────────────────────────────────────────┐
│  FRONTEND (React + Vite + Tailwind CSS)                          │
│  Talks only to Control Plane public API                          │
└───────────────────────────────────────────────────────────────────┘
```

---

## 4. Planes and Responsibilities

### 4.1 Control Plane — `backend/springboot-app`

| Aspect | Choice |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3 |
| Security | Spring Security |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Cache / async coordination | Redis |
| Build | Maven |

Responsibilities:

- Authentication and authorization (users, roles, API clients).
- Organization and repository management.
- GitHub App integration (webhook ingestion, installation management, status/check publishing).
- Webhook signature verification and event deduplication.
- Workflow orchestration (state machine, step scheduling, retry policy).
- Job management (persisted job records, progress, cancellation).
- Analysis coordination (calls Intelligence Plane, stores results).
- Decision engine (policy evaluation; risk informs prioritization only).
- Reporting (PR summaries, evidence bundles).
- Audit logging (who/what/when for security-relevant actions).

Non-responsibilities:

- Parsing source code or running ML models (Intelligence Plane).
- Executing repository tests (Execution Plane).
- Direct React UI concerns.

### 4.2 Intelligence Plane — `ml-engine`

| Aspect | Choice |
| --- | --- |
| Runtime | Python 3.12 |
| API | FastAPI + Pydantic |
| ML | scikit-learn, XGBoost, PyTorch, Hugging Face Transformers, CodeBERT, SHAP |
| Parsing | Tree-sitter |

Responsibilities:

- Source-code analysis and parsing (Java, Python) via Tree-sitter.
- Change detection inputs (diff-aware structural analysis).
- Feature extraction (language-neutral feature vector + structural facts).
- Defect risk prediction (model inference; always returns model version).
- Code embeddings (CodeBERT / transformer-based).
- Risk analysis and ranking support.
- Test generation (language-aware test case synthesis).
- Failure analysis (classify/summarize test failures).
- Explainability (SHAP contributions attached to predictions).

Non-responsibilities:

- No direct reads/writes to the PostgreSQL application database.
- No GitHub credentials handling beyond receiving already-authorized input payloads from Control Plane.
- No long-term persistence owned by FastAPI (stateless; results returned to Control Plane).

### 4.3 Execution Plane — `execution-engine`

Responsibilities:

- Isolated, ephemeral execution of repository code and tests.
- Dependency installation inside the container (Maven / pip).
- Existing test suite execution.
- AI-generated test execution.
- Coverage collection (JaCoCo for Java via Maven reports; coverage.py for Python).
- Structured execution evidence (exit codes, stdout/stderr, test result XML, coverage artifacts).

Isolation rules:

- Runs only inside Docker containers with resource limits, no host Docker socket, no cloud metadata access, restricted network by default.
- Repository content is mounted read-only where possible; work happens in a disposable container filesystem.
- Never executes on the Spring Boot host process.

### 4.4 Frontend — `frontend`

- React + Vite + Tailwind CSS.
- Consumes Control Plane REST API only (versioned, `/api/v1`).
- Displays workflow status, risk scores with explanations, test results, and decision evidence.

### 4.5 Infrastructure — `infrastructure`

- Docker Compose for local development (PostgreSQL, Redis, control plane, ml-engine, execution engine runner, frontend).
- GitHub App manifest/config templates.
- GitHub Actions workflows for CI.
- Future: AWS deployment definitions (later phases).

---

## 5. Communication Patterns

| From → To | Protocol | Pattern | Notes |
| --- | --- | --- | --- |
| GitHub → Control Plane | HTTPS webhook | Push, signed (`X-Hub-Signature-256`) | Deduplicated by delivery ID; async processing |
| Control Plane → GitHub | HTTPS REST | Pull (commit/PR details), Push (check runs, PR comments) | GitHub App installation tokens, short-lived |
| Control Plane → Intelligence Plane | HTTPS JSON, `/api/v1` | Request/response over HTTP for analysis units of work | Synchronous HTTP per analysis call; long workflows stay async at Control Plane level |
| Control Plane → Execution Plane | HTTPS JSON (job API) or queue-backed worker | Submit job → poll/receive completion | Workers pull jobs; never invoked in-process by Spring Boot |
| Frontend → Control Plane | HTTPS JSON, `/api/v1` | REST | Bearer/session auth |
| Control Plane ↔ Redis | TCP | Cache, idempotency keys, pub/sub progress events | Not a source of truth; PostgreSQL is |

Rules:

- All inter-service payloads are typed JSON contracts (see `docs/API_DESIGN.md`).
- Every HTTP request carries `X-Correlation-ID` (generated at ingress if absent).
- All workflow-scoped calls carry `workflowId`.
- No plane may bypass the Control Plane to mutate application state.

---

## 6. Workflow Lifecycle

### 6.1 End-to-end pipeline

```
GitHub Pull Request opened/synchronized
  → Webhook received (signature verified, delivery deduped)
  → Repository + commit SHA retrieved (head SHA recorded)
  → Workflow created (status: PENDING, workflowId issued)
  → Code parsing (Intelligence Plane)
  → Change detection (diff vs base)
  → Dependency impact analysis
  → ML risk prediction (model version + SHAP recorded)
  → Test selection (risk-informed prioritization)
  → AI test generation
  → Test validation (static/sanity checks before execution)
  → Isolated Docker execution (existing + generated tests)
  → Coverage collection
  → Failure analysis (if failures)
  → Decision engine (policy checks + evidence)
  → GitHub check run + PR report published
  → Workflow terminal status recorded with evidence links
```

### 6.2 Status model (canonical)

Used consistently across DB, APIs, and UI:

| Status | Meaning |
| --- | --- |
| `PENDING` | Created, not yet started or waiting on a prerequisite |
| `RUNNING` | Actively executing one or more steps |
| `PASS` | Completed; required checks passed; eligible under policy |
| `FAIL` | Completed; required checks failed |
| `INCONCLUSIVE` | Completed, but evidence is insufficient to decide (e.g. infra error, timeout, flaky ambiguity) |
| `CANCELLED` | Stopped by user or supersession before completion |

Invariants:

- Never mark an incomplete execution as `PASS`.
- Terminal states: `PASS`, `FAIL`, `INCONCLUSIVE`, `CANCELLED`.
- A high ML risk score alone never forces `FAIL`; it affects test priority and reporting. Merge eligibility comes from required policy checks.

### 6.3 Workflow identity

- `workflowId` — UUID, unique per workflow instance, persisted from creation to terminal state.
- Every analysis/result row references `workflowId` and `commitSha`.
- `correlationId` — per-request trace ID propagated across HTTP boundaries (may span multiple workflows in one webhook batch; workflow rows still store their own ID).

---

## 7. Integration Dependencies

| Dependency | Used by | Purpose | Phase introduced |
| --- | --- | --- | --- |
| GitHub App (webhooks + REST) | Control Plane | PR events, check publishing, repo content | Implementation phase TBD |
| PostgreSQL 16+ | Control Plane | System of record | Implementation phase TBD |
| Redis | Control Plane | Cache, idempotency, progress pub/sub | Implementation phase TBD |
| FastAPI `/api/v1` | Control Plane client | Analysis, generation, failure analysis | Implementation phase TBD |
| Docker daemon | Execution Plane runner | Isolated test runs | Implementation phase TBD |
| Maven Central / PyPI (via proxy if needed) | Execution Plane | Dependencies inside containers | Implementation phase TBD |
| Model artifacts (file/registry TBD) | Intelligence Plane | Versioned model inference | Implementation phase TBD |

External contracts are defined in `docs/API_DESIGN.md`; persistence design in `docs/DATABASE_DESIGN.md`.

---

## 8. Security Requirements

### 8.1 GitHub credentials

- GitHub App private key and webhook secret are supplied only via environment variables / secret managers — never committed, never logged.
- Short-lived installation access tokens are minted per request where possible; never written to the database in plaintext; if cached in Redis, TTL-bound and access-controlled.
- Webhook payloads verified with HMAC-SHA256 before processing.
- Least-privilege GitHub App permissions (contents: read, pull requests: read/write, checks: write, metadata: read).

### 8.2 Repository code

- Treated as untrusted input at all times.
- Parsed by Intelligence Plane without execution (Tree-sitter parse only).
- Never executed on the Spring Boot host or the FastAPI host.
- Path traversal, symlink escape, and oversized input protections on any file materialization.

### 8.3 Docker execution

- One ephemeral container per execution job; destroyed after evidence capture.
- Resource limits: CPU, memory, pids, wall-clock timeout, disk quota where feasible.
- Non-root user inside container.
- No Docker socket mount; no privileged mode; dropped Linux capabilities.
- Network egress restricted by default (dependency fetch via explicit allowlist/proxy when required).
- Read-only base mounts for shared inputs; writable scratch space only inside container.
- Secrets never injected into execution containers.

### 8.4 Application security baseline

- Spring Security for authn/authz; hashed passwords (or external IdP in later phases).
- All APIs under `/api/v1`; admin and mutation endpoints authorized by role.
- Global exception handler returns the shared error envelope (no stack traces to clients).
- Structured logs include `correlationId` and `workflowId`; never log secrets or raw tokens.
- Audit log for auth events, repo linkage, decisions published, and configuration changes.

---

## 9. Decision Engine Placement

- Lives in the Control Plane (owns final status and GitHub publication).
- Inputs: required policy check results, test outcomes, coverage evidence, failure analysis summary, ML risk (context only).
- Outputs: final workflow status + decision rationale + evidence references.
- Risk score must never be the sole determinant of `FAIL`.

---

## 10. Directory Map (monorepo)

```
changeGuard-AI/
├── backend/springboot-app/     # Control Plane (Java 21, Spring Boot 3)
├── ml-engine/                  # Intelligence Plane (Python 3.12, FastAPI)
├── execution-engine/           # Execution Plane (Docker runners, executor configs)
├── frontend/                   # React + Vite + Tailwind
├── infrastructure/             # docker-compose, CI, deployment assets
├── docs/                       # Architecture and engineering documentation
├── .gitignore
└── .env.example
```

---

## 11. Phasing Note

Phase 0 delivers design and structure only. No runnable service modules are created in this phase. Future phases implement planes incrementally per instruction, without inventing placeholder functionality.
