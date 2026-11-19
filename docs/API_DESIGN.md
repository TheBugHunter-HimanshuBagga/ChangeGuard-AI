# ChangeGuard AI — API Design

Status: Phase 0 baseline (contract definition only)

This document defines cross-cutting API conventions and the versioned contracts between: Frontend → Control Plane, Control Plane → Intelligence Plane, and Control Plane → Execution Plane. Endpoint lists are design targets for future implementation phases; they are not yet implemented.

---

## 1. API Versioning

- All HTTP APIs are versioned in the path: `/api/v1/...`.
- Breaking contract changes require a new major version (`/api/v2`). Additive, backward-compatible changes stay within `v1`.
- The Intelligence Plane and Execution Plane also expose `/api/v1` so their contracts can evolve independently of the Frontend.
- Every response includes header `X-API-Version: v1`.

---

## 2. Correlation IDs

- Header: `X-Correlation-ID`.
- If absent on ingress, the Control Plane generates a UUID v4.
- Propagated on every outbound inter-service call.
- Required in structured logs for every handled request and async step.
- Workflow-scoped operations additionally carry `workflowId` in the body and logs.

Format: UUID v4 string, e.g. `8f14e45f-ea2f-4a2d-9b6c-1d2e3f4a5b6c`.

---

## 3. Canonical Error Envelope

All non-2xx responses share one shape:

```json
{
  "error": {
    "code": "WORKFLOW_NOT_FOUND",
    "message": "Workflow not found",
    "details": [
      { "field": "workflowId", "issue": "No workflow exists with the given id" }
    ],
    "correlationId": "8f14e45f-ea2f-4a2d-9b6c-1d2e3f4a5b6c",
    "timestamp": "2026-11-14T12:00:00Z"
  }
}
```

Rules:

- `code` is a stable SCREAMING_SNAKE_CASE machine code.
- `message` is human-readable, safe for display; no stack traces.
- `details` is optional; used for field-level validation issues.
- `correlationId` echoes the request correlation ID.
- `timestamp` is ISO-8601 UTC.

### 3.1 Standard error codes (initial set)

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Request body/query failed validation |
| 401 | `UNAUTHENTICATED` | Missing/invalid credentials |
| 403 | `FORBIDDEN` | Authenticated but not authorized |
| 404 | `RESOURCE_NOT_FOUND` | Entity does not exist |
| 409 | `CONFLICT` | State conflict (duplicate delivery, illegal transition) |
| 413 | `PAYLOAD_TOO_LARGE` | Body or artifact exceeds limits |
| 422 | `SEMANTIC_REJECTED` | Well-formed but business-rule rejection |
| 429 | `RATE_LIMITED` | Too many requests |
| 500 | `INTERNAL_ERROR` | Unexpected server error |
| 502 | `UPSTREAM_BAD_RESPONSE` | Downstream service returned invalid response |
| 503 | `SERVICE_UNAVAILABLE` | Dependency down or shutting down |
| 504 | `UPSTREAM_TIMEOUT` | Downstream call timed out |

Service-specific codes extend the set but keep the same envelope (e.g. `WEBHOOK_SIGNATURE_INVALID`, `EXECUTION_TIMEOUT`, `MODEL_VERSION_UNSUPPORTED`).

---

## 4. Workflow Status Contract

Shared enum (Control Plane API, Intelligence Plane responses that reference workflows, Frontend):

```text
PENDING | RUNNING | PASS | FAIL | INCONCLUSIVE | CANCELLED
```

Rules:

- Terminal: `PASS`, `FAIL`, `INCONCLUSIVE`, `CANCELLED`.
- `PASS` only when execution completed and required policy checks passed.
- ML risk score is never sufficient for `FAIL`.

---

## 5. Control Plane Public API (`Control Plane` — `/api/v1`)

Design targets (implemented in future phases):

### 5.1 Auth

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Register a new user (added in Phase 1; returns JWT) |
| POST | `/auth/login` | Obtain session/JWT |
| POST | `/auth/logout` | Invalidate session (stateless: token expiry; revocation TODO) |
| GET | `/auth/me` | Current principal |

### 5.2 Organizations & repositories

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/organizations` | List/create orgs |
| GET | `/organizations/{orgId}` | Organization detail (membership required) |
| GET/POST | `/organizations/{orgId}/members` | List/add members (add: OWNER/ADMIN) |
| DELETE | `/organizations/{orgId}/members/{userId}` | Remove member (last owner protected) |
| GET/POST | `/organizations/{orgId}/repositories` | List/link repositories |
| GET | `/repositories/{repoId}` | Repository detail incl. GitHub linkage state |

### 5.3 Webhooks

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/webhooks/github` | GitHub delivery ingress (signature-verified) |

### 5.4 Workflows & jobs

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/workflows` | Create workflow (idempotent on repo+PR+sha+event; added Phase 1) |
| GET | `/workflows` | Query workflows (filters: repo, status, commitSha) |
| GET | `/workflows/{workflowId}` | Workflow detail with steps and terminal status |
| GET | `/workflows/{workflowId}/evidence` | Evidence bundle references |
| POST | `/workflows/{workflowId}/cancel` | Request cancellation |
| POST | `/workflows/{workflowId}/jobs` | Register an execution job (PENDING; Phase 1) |
| GET | `/workflows/{workflowId}/jobs` | List jobs of a workflow (Phase 1) |
| GET | `/jobs/{jobId}` | Job detail (Phase 1) |

### 5.5 Decisions & reports

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/workflows/{workflowId}/decision` | Final decision + rationale + evidence links |

All endpoints require authentication except `POST /webhooks/github`. Response DTOs are explicit; no entity serialization to clients.

---

## 6. Control Plane → Intelligence Plane API (`ml-engine` — `/api/v1`)

Stateless request/response. No database access by this service. Auth: service-to-service bearer token (env-configured shared secret or mTLS later).

### 6.1 Health

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/health` | Liveness/readiness + loaded model versions |

### 6.2 Analysis

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/analysis/parse` | Parse changed files, return structural facts |
| POST | `/api/v1/analysis/changes` | Change detection summary from provided diff payload |
| POST | `/api/v1/analysis/dependencies` | Dependency/impact graph for changed units |
| POST | `/api/v1/analysis/risk-predict` | Defect risk prediction + model version + SHAP |
| POST | `/api/v1/analysis/embeddings` | Code embeddings for ranking/dedup (optional use) |

### 6.3 Testing support

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/tests/generate` | Generate test cases for changed units |
| POST | `/api/v1/tests/validate` | Pre-execution sanity validation of generated tests |
| POST | `/api/v1/failure/analyze` | Analyze execution failures → classified findings |

### 6.4 Common response metadata

Every analysis response includes:

```json
{
  "correlationId": "…",
  "workflowId": "…",
  "commitSha": "…",
  "analysis": { }
}
```

Risk prediction additionally includes:

```json
{
  "risk": {
    "score": 0.0,
    "level": "LOW|MEDIUM|HIGH",
    "modelVersion": "string",
    "explainability": { "method": "SHAP", "features": [ ] }
  }
}
```

`score` is normalized [0.0, 1.0]. `modelVersion` is mandatory — never omitted.

---

## 7. Control Plane → Execution Plane API (`execution-engine` — `/api/v1`)

Job-based; workers are isolated from Spring Boot.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/health` | Runner health |
| POST | `/api/v1/jobs` | Submit execution job (language, commands, artifacts ref) |
| GET | `/api/v1/jobs/{jobId}` | Job status + evidence summary |
| GET | `/api/v1/jobs/{jobId}/artifacts` | Signed/short-lived references to evidence artifacts |
| POST | `/api/v1/jobs/{jobId}/cancel` | Cooperative cancellation |

Job request sketch:

```json
{
  "workflowId": "uuid",
  "jobId": "uuid",
  "correlationId": "uuid",
  "language": "JAVA|PYTHON",
  "source": { "commitSha": "…", "uri": "…", "resolvedBy": "control-plane" },
  "steps": [
    { "id": "install-deps", "command": "…", "timeoutSeconds": 600 },
    { "id": "run-tests", "command": "…", "timeoutSeconds": 1800 }
  ],
  "limits": { "cpu": 2, "memoryMb": 4096, "wallClockSeconds": 2400 },
  "collect": { "coverage": true, "testReportFormats": ["junit-xml"] }
}
```

Job result includes: `status` (reuse canonical statuses where applicable), per-step exit codes, artifact URIs, duration, and container metadata. Incomplete runs must never report success.

---

## 8. Normalized Code-Analysis Data Contract (Java & Python)

All language-specific parsing is normalized into one language-neutral contract before leaving the Intelligence Plane. Control Plane and Execution Plane never branch on language except to pick toolchains.

### 8.1 Language enum

```text
JAVA | PYTHON
```

### 8.2 Analysis unit identity

```json
{
  "language": "JAVA",
  "path": "src/main/java/com/example/Foo.java",
  "pathRepositoryRelative": true,
  "unitId": "JAVA:com.example.Foo",
  "symbolName": "com.example.Foo",
  "symbolKind": "CLASS|METHOD|FUNCTION|MODULE|FILE"
}
```

- `unitId` is stable and language-prefixed: `JAVA:<fqcn or file>` / `PYTHON:<module path or file>`.
- For Python, prefer `PYTHON:<module.dotted.path>`; fall back to `PYTHON:<relative/path.py>` for non-module scripts.
- For Java, prefer fully qualified class name; methods may use `JAVA:<fqcn>#methodName(paramTypes)` when method-level granularity is available.

### 8.3 Structural fact

```json
{
  "unitId": "…",
  "language": "JAVA",
  "path": "…",
  "complexity": 12,
  "loc": 80,
  "changed": true,
  "changeType": "ADDED|MODIFIED|DELETED|RENAMED",
  "parentUnitId": null,
  "metadata": { }
}
```

`metadata` holds optional language-specific extras (annotations, decorators) without breaking the neutral shape.

### 8.4 Change set

```json
{
  "workflowId": "uuid",
  "commitSha": "head sha",
  "baseCommitSha": "base sha",
  "files": [
    {
      "path": "…",
      "status": "ADDED|MODIFIED|DELETED|RENAMED",
      "previousPath": null,
      "insertions": 10,
      "deletions": 2,
      "language": "JAVA|PYTHON|UNKNOWN",
      "diffRef": "opaque reference to stored diff, if any"
    }
  ]
}
```

### 8.5 Dependency impact

```json
{
  "workflowId": "uuid",
  "commitSha": "…",
  "nodes": [
    { "unitId": "…", "language": "…", "path": "…" }
  ],
  "edges": [
    {
      "fromUnitId": "…",
      "toUnitId": "…",
      "kind": "CALLS|IMPORTS|INHERITS|REFERENCES|TESTS"
    }
  ],
  "impactedUnitIds": ["…"],
  "rationale": "optional human-readable impact summary"
}
```

### 8.6 Risk prediction

```json
{
  "workflowId": "uuid",
  "commitSha": "…",
  "modelVersion": "mandatory string",
  "predictions": [
    {
      "unitId": "…",
      "score": 0.0,
      "level": "LOW|MEDIUM|HIGH",
      "explainability": {
        "method": "SHAP",
        "features": [ { "name": "…", "value": 0.0, "contribution": 0.0 } ]
      }
    }
  ]
}
```

### 8.7 Test case (selected or generated)

```json
{
  "testId": "uuid",
  "workflowId": "uuid",
  "origin": "SELECTED_EXISTING|AI_GENERATED",
  "language": "JAVA|PYTHON",
  "targetUnitId": "…",
  "title": "…",
  "framework": "JUNIT5|PYTEST",
  "code": "…",
  "sourcePath": "path within generated test bundle",
  "validationStatus": "PENDING|VALID|INVALID|SKIPPED",
  "validationNotes": []
}
```

### 8.8 Execution evidence

```json
{
  "jobId": "uuid",
  "workflowId": "uuid",
  "commitSha": "…",
  "language": "…",
  "status": "PENDING|RUNNING|PASS|FAIL|INCONCLUSIVE|CANCELLED",
  "steps": [
    {
      "id": "run-tests",
      "exitCode": 0,
      "timedOut": false,
      "durationMs": 1234,
      "stdoutRef": "artifact ref",
      "stderrRef": "artifact ref"
    }
  ],
  "testSummary": {
    "total": 0, "passed": 0, "failed": 0, "skipped": 0, "errored": 0
  },
  "coverage": {
    "tool": "JACOCO|COVERAGE_PY|NONE",
    "lineRate": null,
    "reportRef": null
  },
  "artifacts": [ { "name": "…", "uri": "…", "contentType": "…" } ]
}
```

### 8.9 Failure analysis result

```json
{
  "workflowId": "uuid",
  "jobId": "uuid",
  "commitSha": "…",
  "modelVersion": "if ML classifier used, else omitted",
  "findings": [
    {
      "testId": "optional",
      "category": "ASSERTION|COMPILE|DEPENDENCY|INFRA|FLAKY_SUSPECT|UNKNOWN",
      "summary": "…",
      "suspectUnitIds": ["…"],
      "confidence": null
    }
  ]
}
```

### 8.10 Decision record (Control Plane output)

```json
{
  "workflowId": "uuid",
  "commitSha": "…",
  "status": "PASS|FAIL|INCONCLUSIVE|CANCELLED",
  "policy": {
    "requiredChecks": [ { "id": "…", "result": "PASS|FAIL|SKIPPED", "mandatory": true } ]
  },
  "riskContext": {
    "maxScore": 0.0,
    "modelVersion": "…",
    "note": "Risk informs prioritization and reporting only; not a standalone FAIL cause"
  },
  "evidenceRefs": [ { "type": "EXECUTION|COVERAGE|FAILURE_ANALYSIS|LOG", "uri": "…" } ],
  "publishedToGitHub": false,
  "decidedAt": "timestamp"
}
```

---

## 9. Validation & DTO Rules

- Request DTOs validated with framework validators (Jakarta Validation on Control Plane; Pydantic on ml-engine).
- Responses use explicit response DTOs; JPA entities never serialized directly.
- Unknown fields: reject on mutations (strict) to catch contract drift.
- All timestamps ISO-8601 UTC; all IDs UUID v4 strings unless noted (`commitSha` hex).

---

## 10. Idempotency & Delivery Semantics

- GitHub webhook: dedupe on delivery ID (`X-GitHub-Delivery`) stored in Control Plane; duplicate deliveries return success without reprocessing.
- Workflow creation from a PR event: natural idempotency key = `(repositoryId, prNumber, headCommitSha, eventType)`.
- Execution job submission: `jobId` chosen by Control Plane; Execution Plane treats `POST /jobs` with existing `jobId` as idempotent.
- Non-idempotent operations (decision publication) guard with state machine transitions (409 on illegal transition).

---

## 11. Auth Summary

| Surface | Mechanism |
| --- | --- |
| Frontend → Control Plane | Session cookie or JWT bearer (Spring Security) |
| GitHub → Control Plane | HMAC webhook signature |
| Control Plane → ml-engine | Service bearer token (env) |
| Control Plane → Execution Plane | Service bearer token (env) |
