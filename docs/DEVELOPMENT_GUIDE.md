# ChangeGuard AI — Development Guide

Status: Phase 0 baseline

This guide explains the monorepo layout, cross-cutting engineering standards, environment configuration, security requirements, and how modules will be run once implemented. Phase 0 creates structure and documentation only — service modules are intentionally empty (`.gitkeep`) and contain no placeholder business logic.

---

## 1. Repository Layout

```
changeGuard-AI/
├── backend/springboot-app/   # Control Plane — Java 21, Spring Boot 3 (Maven)
├── ml-engine/                # Intelligence Plane — Python 3.12, FastAPI
├── execution-engine/         # Execution Plane — Docker-based test runners
├── frontend/                 # React + Vite + Tailwind CSS
├── infrastructure/           # docker-compose, CI, deployment assets
├── docs/                     # Architecture & engineering docs (this folder)
├── .gitignore
└── .env.example
```

Read first:

| Doc | Contents |
| --- | --- |
| `docs/ARCHITECTURE.md` | Planes, communication, workflow lifecycle, security |
| `docs/API_DESIGN.md` | Versioning, errors, correlation IDs, data contracts |
| `docs/DATABASE_DESIGN.md` | PostgreSQL schema, Flyway strategy, Redis |
| `docs/DEVELOPMENT_GUIDE.md` | This file |

---

## 2. Planes Quick Reference

| Plane | Dir | Stack | Owns |
| --- | --- | --- | --- |
| Control | `backend/springboot-app` | Java 21, Spring Boot 3, JPA, Flyway, Redis | Auth, webhooks, orchestration, decisions, audit |
| Intelligence | `ml-engine` | Python 3.12, FastAPI, Pydantic, ML stack | Parsing, risk, generation, failure analysis |
| Execution | `execution-engine` | Docker, JDK 21, Maven, JUnit 5, Py 3.12, Pytest, Coverage.py | Sandboxed test runs |
| Frontend | `frontend` | React, Vite, Tailwind | UI |
| Infra | `infrastructure` | Docker Compose, GitHub Actions | Local/prod topology |

Rules of engagement (see Architecture §2): Spring Boot owns the DB; FastAPI never touches it; repo code runs only in isolated containers; all workflows async with `workflowId` + `commitSha` + `modelVersion` + evidence.

---

## 3. Engineering Standards

Apply in every future phase:

- Clean architecture, SOLID, modular packages bounded by plane responsibilities.
- Explicit DTOs + Jakarta Validation (Java) / Pydantic (Python); no entity leak to API surfaces.
- Global exception handling → shared error envelope (`docs/API_DESIGN.md`).
- Structured logging with `correlationId` and `workflowId`; JSON logs preferred in non-dev profiles.
- Constructor/`@RequiredArgsConstructor`-style DI; no field injection in new code.
- Shared logic extracted once (no copy-paste across services).
- Typed API contracts; strict unknown-field rejection on mutations.
- Unit + integration tests for every implemented feature; do not report success without running them.
- All environment variables documented in `.env.example` and in module README when a module lands.
- Never hardcode secrets; never invent metrics/results; never ship fake working modules — use `// TODO:` / `TODO:` for gaps.

---

## 4. Environment Variables

Canonical template: repository root `.env.example`.

Key groups (expand as modules land):

| Variable | Component | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Control Plane | `local`, `prod`, … |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Control Plane | PostgreSQL connection |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Control Plane | Redis |
| `GITHUB_APP_ID` | Control Plane | GitHub App id |
| `GITHUB_APP_PRIVATE_KEY_PATH` / `_BASE64` | Control Plane | Private key (path or base64 from secret manager) |
| `GITHUB_WEBHOOK_SECRET` | Control Plane | HMAC verification |
| `ML_ENGINE_BASE_URL` | Control Plane | FastAPI base URL |
| `ML_ENGINE_SERVICE_TOKEN` | Control Plane + ml-engine | S2S bearer |
| `EXECUTION_ENGINE_BASE_URL` | Control Plane | Execution API base URL |
| `EXECUTION_ENGINE_SERVICE_TOKEN` | Control Plane + execution | S2S bearer |
| `JWT_SECRET` | Control Plane | Token signing (prod: rotate, secret manager) |
| `ARTIFACT_STORAGE_URI` | Control Plane/Execution | Where evidence artifacts are stored |
| `DOCKER_NETWORK_MODE` / runner limits | Execution | Isolation config |

Never commit `.env` — only `.env.example` with dummy values.

---

## 5. Security Requirements (engineering checklist)

### 5.1 GitHub credentials

- [ ] Private key and webhook secret only from env/secret manager.
- [ ] No secret values in logs, audit metadata, or exception messages.
- [ ] Webhook HMAC-SHA256 verified before any processing.
- [ ] Installation tokens short-lived; Redis cache TTL-bound if used.
- [ ] Least-privilege GitHub App permissions documented in `infrastructure/` when added.

### 5.2 Repository code

- [ ] Always untrusted; parse-only in ml-engine (no execution there).
- [ ] Never execute on control-plane or ml-engine hosts.
- [ ] Guard path traversal/symlinks when materializing files.
- [ ] Size limits on diffs, files, and generated tests.

### 5.3 Docker execution

- [ ] Ephemeral container per job; destroyed after evidence capture.
- [ ] CPU/memory/pids/wall-clock limits enforced.
- [ ] Non-root user; no privileged mode; no Docker socket; dropped capabilities.
- [ ] Default-deny network with explicit dependency egress allowlist/proxy.
- [ ] No secrets mounted into containers.
- [ ] Incomplete/timeout runs → `INCONCLUSIVE` or `FAIL` per policy — never `PASS`.

---

## 6. Workflow Status (shared)

```text
PENDING | RUNNING | PASS | FAIL | INCONCLUSIVE | CANCELLED
```

Terminal: `PASS`, `FAIL`, `INCONCLUSIVE`, `CANCELLED`. Incomplete work is never `PASS`. ML risk alone never forces `FAIL`.

---

## 7. How to Run (per module)

> Phase 0: modules are empty; commands below become valid only after the corresponding implementation phase. Do not expect these to succeed yet.

### 7.1 Control Plane (`backend/springboot-app`) — future

```powershell
cd backend/springboot-app
# after implementation:
mvn spring-boot:run
# tests:
mvn test
```

Requires PostgreSQL and Redis (see `infrastructure/` when added) and env vars from `.env.example`.

### 7.2 Intelligence Plane (`ml-engine`) — future

```powershell
cd ml-engine
# after implementation:
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
# tests:
pytest
```

### 7.3 Execution Plane (`execution-engine`) — future

Runner invoked via Control Plane job API; local smoke path will use Docker Compose service defined under `infrastructure/`. Direct host execution of repo tests is forbidden.

### 7.4 Frontend (`frontend`) — future

```powershell
cd frontend
npm install
npm run dev
# tests/build as defined in package.json when created
```

### 7.5 Infrastructure — future

```powershell
cd infrastructure
docker compose up -d
```

---

## 8. Branching, CI, Quality Gates (baseline)

- Trunk-based or short-lived feature branches; no force-push to main.
- GitHub Actions (added under `infrastructure/` or `.github/` in a later phase): compile + unit tests for Java, lint+tests for Python, lint+build for frontend.
- Minimum gates before merge: all existing tests pass; new logic has tests; no secrets in diff; Flyway migrations included for schema changes.
- Do not fabricate CI results in reports — run what can be run and state actual outcomes.

---

## 9. Adding a New API (control plane pattern, future)

1. Define/adjust contract in `docs/API_DESIGN.md` if cross-service.
2. DTO + validation.
3. Service interface + implementation (business rule home).
4. Controller mapped under `/api/v1`.
5. Global handler covers errors; no ad-hoc response bodies.
6. Tests: unit for service, `@WebMvcTest`/integration for controller.
7. Update docs if behavior/contract changed.

---

## 10. Phase 0 Scope Boundaries

Done in Phase 0:

- Monorepo directory skeleton.
- Architecture, API, database, and development documentation.
- Cross-cutting conventions (statuses, errors, correlation IDs, versioning, data contracts).
- `.gitignore`, `.env.example`.

Explicitly not done (future phases only, by instruction):

- No Java/Python/React source trees, no fake endpoints, no schema files, no Docker services that pretend to run.
