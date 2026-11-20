# ChangeGuard AI — Intelligence Plane (`ml-engine`)

Python 3.12 / FastAPI service for source-code analysis, defect-risk prediction,
test generation and failure analysis (see `docs/ARCHITECTURE.md` §4.2 and
`docs/API_DESIGN.md` §6).

**Phase 2 scope:** service skeleton, health/readiness, correlation IDs,
canonical error envelope, typed data-contract schemas, and abstract provider
interfaces. **No ML models are loaded and no predictions are produced yet.**

## Requirements

- Python 3.12+
- No database access — this service never touches the application PostgreSQL
  (architecture rule 2).

## Installation

```powershell
cd ml-engine
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements-dev.txt   # runtime + pytest + httpx
```

## Configuration

Copy `.env.example` to `.env` and adjust (all values have safe defaults):

| Variable | Default | Purpose |
| --- | --- | --- |
| `ML_ENGINE_HOST` | `0.0.0.0` | Bind address |
| `ML_ENGINE_PORT` | `8000` | HTTP port |
| `ML_ENGINE_LOG_LEVEL` | `INFO` | `DEBUG\|INFO\|WARNING\|ERROR` |
| `ML_ENGINE_SERVICE_VERSION` | `0.1.0` | Reported by `/api/v1/health` |

## Startup

```powershell
# Option A — module entry point (structured JSON logs, recommended)
python -m app.main

# Option B — uvicorn with auto-reload (development)
uvicorn app.main:app --reload --port 8000
```

Verify:

```powershell
Invoke-RestMethod http://localhost:8000/api/v1/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "changeguard-ml-engine",
  "version": "0.1.0",
  "apiVersion": "v1",
  "models": []
}
```

## API surface (v1)

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/health` | public | Liveness + loaded model versions (empty in Phase 2) |
| GET | `/api/v1/ready` | public | Readiness; `?verbose=true` adds diagnostics |
| GET | `/swagger-ui` | public | OpenAPI UI |
| GET | `/v3/api-docs` | public | OpenAPI document |

Cross-cutting (docs/API_DESIGN.md):

- All responses include `X-Correlation-ID` and `X-API-Version: v1`.
- Errors use the canonical envelope:
  `{"error": {"code", "message", "details", "correlationId", "timestamp"}}`.
- Invalid requests return `400 VALIDATION_FAILED` with per-field `details`.
- Logs are single-line JSON including `correlationId`.

## Provider interfaces (for later phases)

`app/interfaces/` defines abstract contracts — implementations are added in
future phases and must never be simulated:

| Interface | Purpose |
| --- | --- |
| `RiskPredictor` | Defect-risk model; must expose `model_version` and stamp predictions |
| `TestGenerator` | AI test-generation provider (JUnit 5 / Pytest) |
| `FailureAnalyzer` | Failure classification with optional `modelVersion` |

Typed request/response contracts live in `app/schemas/` (language-neutral,
Java + Python).

## Tests

```powershell
pytest
```

Covers: health/readiness, correlation propagation, error envelope,
request validation, data-contract schemas, and interface contracts.

## Project layout

```
ml-engine/
├── app/
│   ├── main.py            # create_app() factory + entry point
│   ├── config.py          # pydantic-settings (env-driven)
│   ├── context.py         # correlation contextvar
│   ├── correlation.py     # middleware (propagate + echo + request logs)
│   ├── errors.py          # canonical envelope + exception handlers
│   ├── logging_setup.py   # JSON structured logging
│   ├── api/v1/            # versioned routes (health)
│   ├── schemas/           # data-contract Pydantic models
│   └── interfaces/        # abstract ML/test-gen provider contracts
├── tests/                 # pytest suite
├── requirements.txt
├── requirements-dev.txt
├── pytest.ini
└── .env.example
```
