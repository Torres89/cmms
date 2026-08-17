# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This fork is being repositioned from a generic CMMS into a **machine specialist
platform**: deep per-asset documentation for small companies with a handful of
high-value machines. See `docs/plans/2026-08-15-machine-specialist-platform.md`
for the direction and `docs/machine-specialist-implementation.md` for what has
been built.

Two things shape most decisions here:

1. **We do not run an LLM.** Embeddings, document parsing and OCR run locally on
   CPU because they are high-volume and cheap. Interactive reasoning is
   outsourced to the customer's own model, three ways: their MCP client, their
   API key, or a managed add-on on ours. There is no GPU anywhere.
2. **Customisation must be data, not code.** A customer wanting different PM
   templates gets a pack file (`api/src/main/resources/packs/*.json`), a custom
   field, or a setting - never a branch. Fork proliferation is what kills a
   one-person service business.

Components:

- **api/** - Spring Boot 3.2.3 (Java 17) REST backend on port 8080
- **frontend/** - React 17 + TypeScript + Material-UI 5 web app on port 3000
- **mobile/** - React Native (Expo 53) mobile app
- **cmms-agent/** - Python 3.11 FastAPI AI agent + remote MCP server on port 8001
- **ingest-worker/** - Python document pipeline: Docling parsing, OCR, chunking,
  EmbeddingGemma embeddings. Port 8002. CPU only.
- **telemetry-collector/** - Python poller for MTConnect and ISO 15143-3.
  Optional per customer; started with the `telemetry` compose profile.

## Development Commands

### Full stack (Docker)

```bash
docker compose up -d                       # postgres, api, frontend, agent, ingest-worker, minio
docker compose --profile telemetry up -d   # ...plus the telemetry collector
docker compose down
```

WARNING: Postgres is now `pgvector/pgvector:pg16`, not `postgres:16-alpine`.
That is a musl to glibc change, which changes text collation: **do not start it
on a data directory initialised by the Alpine image.** Dump and restore instead,
or b-tree indexes on text columns can be silently corrupt.

### API (Spring Boot)

```bash
cd api && mvn clean package -DskipTests    # Build JAR
cd api && mvn spring-boot:run              # Run locally
cd api && mvn test                         # Run tests
```

### Frontend (React)

```bash
cd frontend && npm install
cd frontend && npm start        # Dev server (port 3000)
cd frontend && npm run build    # Production build
cd frontend && npm run lint     # ESLint (airbnb-typescript)
cd frontend && npm run lint:fix
cd frontend && npm run format   # Prettier
```

### Mobile (Expo)

```bash
cd mobile && npm install
cd mobile && npx expo start --dev-client   # Dev
cd mobile && npx expo run:android
cd mobile && npx expo run:ios
cd mobile && npm test                      # Jest
```

### Agent (Python)

```bash
cd cmms-agent && pip install -r requirements.txt
cd cmms-agent && python server.py          # FastAPI + MCP server on port 8001
cd cmms-agent && python agent.py           # Interactive CLI (signs in as a real user)
```

### Ingest worker (Python)

```bash
cd ingest-worker && pip install -r requirements.txt
cd ingest-worker && python main.py         # /embed + /ocr on 8002, and drains the queue
```

### Telemetry collector (Python)

```bash
cd telemetry-collector && pip install -r requirements.txt
cd telemetry-collector && python main.py
```

## Architecture

### Backend (api/)

Standard Spring Boot layered architecture under `com.grash`:

- `controller/` - REST endpoints
- `service/` - Business logic
- `repository/` - JPA data access
- `model/` - JPA entities with Hibernate Envers audit
- `mapper/` - MapStruct DTO mapping
- `security/` - JWT auth (14-day expiry) + OAuth2 SSO
- `configuration/` - Spring config beans
- `job/` - Quartz scheduled jobs

Database: PostgreSQL 16, migrations via Liquibase (`api/src/main/resources/db/master.xml` with changelogs in `db/changelog/`). Multi-tenant with org-level isolation.

Storage is abstracted by `STORAGE_TYPE`: `minio`, `gcp`, `s3` (S3 / Cloudflare
R2 - the hosted default) or `local` (filesystem, for self-hosted installs).

WARNING: **Large files must never pass through the API.**
`StorageService.download()` returns a `byte[]`, so every byte lands on the JVM
heap - fine for a 5 MB manual, an OOM for a 500 MB training video. Video and CAD
always go out as signed URLs, and `assertSafeToBuffer` enforces the size
ceiling. On the `local` tier, `LocalFileController` serves signed links with
real HTTP range support.

**The machine-specialist layer** (all under `com.grash`):

- `AssetDossierService` / `GET /assets/{id}/dossier` - the single most important
  endpoint. Everything true about one machine right now, as JSON for the UI and
  as a compact text card for AI clients.
- `ComponentService` - serialized components and their back-to-birth ledger.
  Counters roll forward automatically from every meter reading.
- `KnowledgeService` - hybrid retrieval (pgvector + BM25 fused with RRF). Hybrid
  is required, not an optimisation: pure vector search fails on alarm codes and
  part numbers, which is most of what actually gets searched.
- `AssetPackService` - instantiates a vertical pack onto a machine.
- `DiagnosisService`, `MaintenancePlanProposalService` - assemble evidence for
  the customer's model to reason over. No model runs here.

### Frontend (frontend/)

- State: Redux Toolkit (slices in `src/slices/`)
- Routing: React Router v6 (`src/router/`)
- Forms: Formik + Yup validation
- i18n: i18next (`src/i18n/`)
- Runtime config: `runtime-env-cra` injects env vars at container start
- Served via Nginx in production (see `nginx-custom.conf`)

### Agent (cmms-agent/)

One tool surface, exposed twice: OpenAI-style function tools for the in-app
chat, and MCP tools for external clients.

- `auth.py` - every request is authenticated as the calling user. **There is no
  service account.** `api_client.py` is constructed per request with the
  caller's own token, so all org isolation and role checks apply unchanged.
- `tool_registry.py` - `execute_tool` is where write-safety and auditing live.
  Mutating tools are gated on `confirmed`; this is structural, not a prompt rule
  an external model could talk its way around.
- `mcp_server.py` - Door 1. Remote HTTPS MCP with OAuth 2.1 + PKCE, because
  ChatGPT accepts remote HTTPS endpoints only. The issued access token *is* an
  Atlas JWT.
- `llm_provider.py` - resolves which door a company is on and builds a client.
- `db.py` - chat sessions, the tool audit trail and LLM usage metering, in the
  same Postgres the API uses.

### Ingest worker (ingest-worker/)

Docling parsing (keeps table structure - maintenance manuals are mostly tables),
OCR fallback for scans, structure-aware chunking with page numbers,
EmbeddingGemma-300M embeddings. The queue is a Postgres table drained with
`FOR UPDATE SKIP LOCKED`, serially and overnight: nobody is waiting on it.

## Git Conventions

- **Commit messages**: Conventional Commits enforced by commitlint. Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- **Remotes**: `origin` = fork (Torres89/cmms), `upstream` = source (Grashjs/cmms)
- **CI**: Push to `main` triggers Docker image builds (GHCR) and EC2 deployment via `.github/workflows/deploy.yml`

## Environment Setup

Copy `.env.example` to `.env`. Key variables: `POSTGRES_USER`, `POSTGRES_PWD`, `MINIO_USER`, `MINIO_PASSWORD`, `JWT_SECRET_KEY`, `PUBLIC_FRONT_URL`, `PUBLIC_API_URL`.

Added by the machine-specialist work:

| Variable | Purpose |
|---|---|
| `INTERNAL_SERVICE_TOKEN` | Shared secret for service-to-service calls (agent reads AI config, collector posts telemetry). Must match across services; empty disables `/internal/**` entirely. |
| `SECRET_ENCRYPTION_KEY` | AES-GCM key for customers' stored AI provider keys. Falls back to `JWT_SECRET_KEY`. |
| `EMBEDDING_URL` | The ingest worker, for query embedding and OCR. Empty means retrieval runs lexical-only rather than failing. |
| `MCP_PUBLIC_URL` | Public HTTPS URL of the MCP server, as clients reach it. |
| `STORAGE_S3_*`, `STORAGE_LOCAL_*` | S3/R2 and filesystem storage backends. |
| `SOURCE_CODE_URL` | Where the AGPL source is published (surfaced in the app footer). |

Each Python service has its own `.env.example`: `cmms-agent/`, `ingest-worker/`,
`telemetry-collector/`.

## Licensing

The fork stays on **AGPLv3**. Under section 13, users interacting with a
modified version over a network must be offered the complete corresponding
source, so `SOURCE_CODE_URL` is surfaced in the app footer and the sidebar. Do
not build closed-source feature gating on the Keygen / `LICENSE_KEY` machinery -
`PlanFeatures` gates what is enabled in the hosted service, not the source.

## Key Integrations

- Email: SMTP or SendGrid (controlled by `MAIL_TYPE`)
- Maps: Google Maps API (`GOOGLE_KEY`)
- Payments: Paddle
- Licensing: Keygen (commercial features require `LICENSE_KEY`)
- WebSocket: STOMP protocol for real-time updates

## Rules

- Never publish .env files to git
- Never publish cmms-agent/.env files to git
- Never publish to upstream remote always push to origin remote
- Never publish .env.prod files to git
- Never publish keypair files to git of any other system
- Never modify windows system files without explicit user permission
- Never publish Caddyfile to git or any other system
