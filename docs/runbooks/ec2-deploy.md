# Deploying the machine-specialist release to EC2

A one-time deploy guide for merging `feat/machine-specialist-platform` into
`main`. Later releases will not need most of this — the parts that make it long
are the Postgres image change and the fact that this is the first deploy to add
new services.

Work through the phases in order. Phases 0-2 are preparation and can be done
any time beforehand with the site up. Phase 3 is the maintenance window.

**Expect the site to be down from the start of phase 3 until phase 4 finishes.**
The dump and restore itself is quick — this instance's database measured 15 MB,
which dumps and restores in seconds. What actually sets the window is the CI
build in phase 4 (the ingest-worker image carries Docling and CPU torch, so
10-20 minutes) plus the ~3 GB image pull on the box afterwards. Budget 30-45
minutes end to end, and note that most of it is spent waiting on GitHub rather
than on anything you can hurry.

Conventions used below: the instance user is `ubuntu`, the checkout is
`~/atlas-cmms`, the GHCR namespace is `torres89`, and the compose project is
`atlas-cmms` (so the database volume is `atlas-cmms_postgres_data`).

The instance takes a key, so every SSH command in this guide is:

```bash
ssh -i ~/Documents/Maint/keypair/atlas-prod-key.pem ubuntu@98.83.54.9
```

On Windows the key path is `C:\Users\alfre\Documents\Maint\keypair\atlas-prod-key.pem`.
`keypair/` is gitignored (`.gitignore:19`), so the `.pem` itself is never
committed — only this path to it is. To run a single command without an
interactive session, append it in quotes:

```bash
ssh -i ~/Documents/Maint/keypair/atlas-prod-key.pem ubuntu@98.83.54.9 \
  'cd ~/atlas-cmms && git status --porcelain'
```

---

## What this release changes

| Change | Why it needs attention |
|---|---|
| Postgres moves to `pgvector/pgvector:pg16` | musl to glibc. Same major version, so it mounts and starts happily while every text index is left sorted under the wrong collation. Phase 3 exists entirely for this. |
| Two new services: `ingest-worker`, `telemetry-collector` | New GHCR packages, which are created **private** by default. Phase 0. |
| New env wiring (`MCP_PUBLIC_URL`, `AGENT_URL`, …) | Interpolated without defaults, so a missing value is blank rather than sensible. Phase 2. |
| The deploy now syncs the checkout on the box | Requires `~/atlas-cmms` to be a clean checkout on `main`. On this instance it was a hand-assembled directory with no `.git`, so it has to be converted first. Phase 1.1 and 1.5. |

---

## Phase 0 — GitHub (5 minutes, no downtime)

### 0.1 Add a GHCR pull token

This build publishes two packages that have never existed before,
`atlas-cmms-ingest` and `atlas-cmms-telemetry`. **GitHub creates new packages
as private**, so the instance cannot pull them anonymously even if your
existing three are public. Without this the deploy fails at `docker compose
pull`.

1. Create a personal access token (classic) with the **`read:packages`** scope:
   <https://github.com/settings/tokens>
2. Add it as a repository secret named `GHCR_PAT`:
   `https://github.com/Torres89/cmms/settings/secrets/actions`

The workflow already uses it if present and skips the login if not.

> Alternative: let the first build run, then set both new packages to public at
> <https://github.com/Torres89?tab=packages>. That costs you one failed deploy
> and a re-run, so the token is the easier path.

### 0.2 Confirm the existing secrets are still set

At `https://github.com/Torres89/cmms/settings/secrets/actions` you should see
`EC2_HOST` and `EC2_SSH_KEY` alongside the new `GHCR_PAT`.

---

## Phase 1 — Instance preflight (10 minutes, no downtime)

SSH in:

```bash
ssh -i ~/Documents/Maint/keypair/atlas-prod-key.pem ubuntu@98.83.54.9
cd ~/atlas-cmms
```

### 1.1 The checkout must be clean and on `main`

The deploy now fast-forwards the repo before running compose. It uses
`--ff-only`, so it will **stop rather than discard** anything local — which
means a dirty checkout blocks the deploy instead of silently deploying stale
compose files.

```bash
git remote -v                      # expect origin -> Torres89/cmms
git rev-parse --abbrev-ref HEAD    # expect main
git status --porcelain             # expect NO output
```

If `git status` shows modified **tracked** files, decide per file:

```bash
git diff                           # see what was changed on the box
git stash                          # park it, or
git checkout -- <file>             # discard it
```

Untracked files are fine and are left alone. `.env` and `Caddyfile` are
gitignored, so nothing below will touch them.

If `~/atlas-cmms` turns out **not** to be a git checkout at all — every `git`
command answers `fatal: not a git repository` — then it was assembled by hand
from copied files, which is how this instance was originally built. The deploy
cannot work against it: `git fetch` is the first command in the deploy script
and `script_stop: true` aborts the job there. Fix it with
[1.5 Converting a hand-assembled directory](#15-converting-a-hand-assembled-directory-into-a-checkout)
before going any further.

### 1.2 Disk and memory

The ingest worker image carries Docling and CPU torch, roughly 2-3 GB.

```bash
df -h /                            # want 5 GB+ free
free -h                            # m7i-flex.xlarge = 16 GB
docker system df                   # reclaimable space, if you are tight
```

If short on space:

```bash
docker image prune -a -f           # removes images no container is using
```

### 1.3 Note what is currently running

Useful to compare against afterwards.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker inspect atlas_db --format '{{.Config.Image}}'   # expect postgres:16-alpine
```

If that already reads `pgvector/pgvector:pg16`, the image swap has somehow
already happened — skip to [If Postgres was already
swapped](#if-postgres-was-already-swapped).

### 1.4 Check the security group

The compose file publishes several ports on `0.0.0.0`. If your security group
only admits 80 and 443, nothing needs doing. If it is wider, `5432` is the one
worth closing — that is Postgres on the public internet.

```bash
sudo ss -tlnp | grep -E '5432|8080|8001|9000'
```

The ingest worker is already bound to `127.0.0.1` in this release, so it is not
exposed regardless.

### 1.5 Converting a hand-assembled directory into a checkout

Only needed if 1.1 showed no git repository. This restarts nothing — a checkout
does not touch running containers — so it is still a no-downtime step.

The directory holds a mix of things that are in the repo (`docker-compose.yml`,
`docker-compose.prod.yml`) and things that are deliberately not (`.env`,
`Caddyfile`, `cmms-agent/.env`, `logo/`). The three latter are gitignored, so a
checkout leaves them exactly where they are. The compose files **will** be
overwritten by the repo's versions — which is the point, since they are part of
the release — so diff them first and rescue any local edits.

```bash
cd ~
tar czf ~/atlas-cmms-preconvert-$(date +%F-%H%M).tar.gz atlas-cmms
ls -lh ~/atlas-cmms-preconvert-*.tar.gz     # your way back
```

Turn it into a real checkout. `-f` is required because the compose files exist
as untracked files that the checkout needs to replace:

```bash
cd ~/atlas-cmms
git init -q
git remote add origin https://github.com/Torres89/cmms.git
git fetch origin                            # full history: --ff-only needs it later
git checkout -f -b main --track origin/main
```

Verify it looks like what the deploy expects:

```bash
git rev-parse --abbrev-ref HEAD    # main
git status --porcelain             # empty
ls .env Caddyfile cmms-agent/.env  # all three still present
```

**Rescuing the local compose edits.** On this instance
`docker-compose.prod.yml` had been edited to hardcode admin bootstrap values:

```yaml
  api:
    environment:
      - ADMIN_EMAIL=admin@test.com
      - ADMIN_PASSWORD=Admin1234
      - ADMIN_COMPANY_NAME=TestCo
```

That was a workaround for a stale `docker-compose.yml` predating the
`ADMIN_*` lines. The repo's `docker-compose.yml` already passes
`ADMIN_EMAIL: ${ADMIN_EMAIL:-}` and friends through from `.env`, so the fix is
to put the values in `.env` (phase 2) and drop the override. Do not re-apply
the edit to the tracked file — a modified tracked file will block every future
deploy at `--ff-only`.

Anything else your diff turned up goes the same way: into `.env`, or upstream
into the repo as a commit — never as a local edit on the box.

---

## Phase 2 — Environment variables (10 minutes, no downtime)

Still on the box, in `~/atlas-cmms`.

### 2.1 Back up the current .env

```bash
cp .env .env.backup-$(date +%F)
```

### 2.2 Generate two secrets

```bash
echo "INTERNAL_SERVICE_TOKEN=$(openssl rand -hex 32)"
echo "SECRET_ENCRYPTION_KEY=$(openssl rand -hex 32)"
```

Keep the output — you are about to paste it in.

### 2.3 Append the new variables

Open `.env` and add the block below, substituting your real domain for
`cmms.example.com` and the two generated secrets. Anything already present in
your `.env` should not be duplicated — check first with
`grep -E 'MCP_PUBLIC_URL|AGENT_URL|INTERNAL_SERVICE_TOKEN' .env`.

```bash
nano .env
```

```ini
# --- machine-specialist release ---

# Service-to-service shared secret. Empty disables /internal/** entirely.
INTERNAL_SERVICE_TOKEN=<paste the generated value>

# Encrypts customers' stored AI provider keys. Falls back to JWT_SECRET_KEY.
SECRET_ENCRYPTION_KEY=<paste the generated value>

# The ingest worker, over the compose network. Empty makes retrieval
# lexical-only instead of failing.
EMBEDDING_URL=http://ingest-worker:8002
WORKER_ROLE=both

# Remote MCP server. This is the BASE origin, with no /mcp on the end:
# mcp_server.py appends the paths itself (`{MCP_PUBLIC_URL}/mcp` at line 126,
# `/oauth/authorize` and friends at 137-140). Putting /mcp here yields
# /mcp/mcp and an OAuth handshake no client can complete. A blank value leaves
# MCP advertising an endpoint nobody can reach.
MCP_ENABLED=true
MCP_PUBLIC_URL=https://agent.cmms-demo.automationhr-ai.com

# How the browser reaches the agent for in-app chat. Blank here is worse than
# unset: docker-compose.prod.yml interpolates it with no default, overriding
# the localhost value from the base compose file.
AGENT_URL=https://agent.cmms-demo.automationhr-ai.com

# Admin bootstrap. These moved out of a local docker-compose.prod.yml edit in
# phase 1.5 — the base compose file passes them through from here. Inert once
# the admin user exists in the database.
ADMIN_EMAIL=admin@test.com
ADMIN_PASSWORD=<pick something better than the old Admin1234>
ADMIN_COMPANY_NAME=TestCo

# AGPL section 13 — surfaced in the app footer.
SOURCE_CODE_URL=https://github.com/Torres89/cmms
```

`.env.example` in the repo lists these plus the S3/R2 and local-storage
options if you need them.

### 2.4 Make sure Caddy actually routes those two URLs

`MCP_PUBLIC_URL` and `AGENT_URL` are promises about your reverse proxy. If the
Caddyfile has no matching route they will resolve to 404s.

```bash
grep -nE 'agent|mcp|8001' Caddyfile
```

On this instance the route already exists and **nothing needs changing**:

```caddy
agent.cmms-demo.automationhr-ai.com {
    reverse_proxy agent:8001
}
```

Because that proxies the whole subdomain rather than one path, every endpoint
the MCP server publishes is already covered — `/mcp`, `/oauth/authorize`,
`/oauth/token`, `/oauth/register`, and the two `/.well-known/...` documents
that clients fetch before authenticating. That is exactly why `MCP_PUBLIC_URL`
is the bare origin.

The agent listens on `8001` inside the compose network. If there were no route,
you would add one before phase 3 — a reverse proxy to `agent:8001` on the host
named in `MCP_PUBLIC_URL` and `AGENT_URL`. `Caddyfile` is gitignored and lives
only on the box, so it is yours to edit directly.

---

## Phase 3 — Postgres migration (the maintenance window)

This is the part that cannot be automated and cannot be undone without the
dump. Read the whole phase before starting it.

The order matters: you check the new branch out **first**, so that when you
start Postgres again compose gives you the pgvector image. The dump itself is
still taken from the old container, which is running the old binary.

### 3.1 Check out the release branch

```bash
cd ~/atlas-cmms
git fetch origin
git checkout feat/machine-specialist-platform
export GH_USER_OR_ORG=torres89
```

Nothing restarts here. The running containers are unaffected by a checkout.

### 3.2 Stop the writers, leave the database up

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  stop api agent frontend ingest-worker
```

The site is now down. Postgres stays up because the dump has to be taken by the
old binary.

### 3.3 Dump

```bash
set -a; . ./.env; set +a
STAMP=$(date +%F-%H%M)

docker exec atlas_db pg_dump -U "$POSTGRES_USER" --clean --if-exists atlas \
  | gzip > ~/atlas-pre-pgvector-$STAMP.sql.gz
```

Check it is real before trusting it:

```bash
ls -lh ~/atlas-pre-pgvector-$STAMP.sql.gz     # not a few hundred bytes
zcat ~/atlas-pre-pgvector-$STAMP.sql.gz | tail -5   # ends cleanly, no error text
```

Get a copy off the instance. This is your only way back.

The instance has no AWS CLI installed, so pull it down over SSH instead —
run this **from your own machine**, not from the box:

```bash
scp -i ~/Documents/Maint/keypair/atlas-prod-key.pem \
  ubuntu@98.83.54.9:'~/atlas-pre-pgvector-*.sql.gz' .
```

Check it arrived intact before going on:

```bash
ls -lh atlas-pre-pgvector-*.sql.gz
gzip -t atlas-pre-pgvector-*.sql.gz && echo "archive is valid"
```

**Do not continue until that copy exists on your machine and passes `gzip -t`.**
If you would rather keep it in S3 as well, install the CLI on the box
(`sudo snap install aws-cli --classic`) and upload from there — but the local
copy is the one that matters, because it does not share a failure domain with
the instance.

### 3.4 Drop the old data directory

The wrong collation lives in the data directory, so it has to go. This is the
irreversible step.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
docker volume ls | grep postgres              # confirm the name first
docker volume rm atlas-cmms_postgres_data
```

### 3.5 Start the new Postgres on its own

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres
docker logs -f atlas_db                        # wait for "database system is ready"
```

Ctrl-C out of the logs once it is ready, then confirm you got the right image
and that pgvector is available:

```bash
docker inspect atlas_db --format '{{.Config.Image}}'
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT 1 FROM pg_available_extensions WHERE name='vector';"
```

One row is what you want. **No rows means you are still on a plain Postgres
image** — stop and fix that, because the knowledge-layer migrations are written
to mark themselves as run and fall back to lexical-only retrieval rather than
fail, so this will not announce itself later.

### 3.6 Restore

```bash
zcat ~/atlas-pre-pgvector-$STAMP.sql.gz \
  | docker exec -i atlas_db psql -U "$POSTGRES_USER" -d atlas -v ON_ERROR_STOP=1
```

`ON_ERROR_STOP=1` matters — without it psql skips failed statements and still
exits 0. Errors mentioning the `vector` type are expected and harmless; the
dump predates the extension.

Sanity check the data came back:

```bash
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT count(*) FROM asset;" -c "SELECT count(*) FROM work_order;"
```

Compare against what you expect. If these are zero and should not be, stop —
do not proceed to phase 4, and see [Rollback](#rollback).

### 3.7 Return the checkout to main

The deploy expects to fast-forward `main`, so put the branch back:

```bash
git checkout main
git status --porcelain          # must be empty
```

Postgres keeps running on the new image. Leave the rest of the stack down;
the deploy in phase 4 brings it up.

---

## Phase 4 — Merge and deploy

Back on your own machine (or in the GitHub UI).

### 4.1 Open and merge the pull request

<https://github.com/Torres89/cmms/pull/new/feat/machine-specialist-platform>

Merging to `main` triggers `.github/workflows/deploy.yml` automatically.

### 4.2 Watch it

<https://github.com/Torres89/cmms/actions>

The run has two jobs:

1. **build-and-push** — five images to GHCR. The ingest worker is the slow one;
   the whole job is typically 10-20 minutes.
2. **deploy** — SSHes to the instance, fast-forwards `main`, logs in to GHCR,
   pulls, and brings everything up.

If `deploy` fails, see [Troubleshooting](#troubleshooting). It now stops at the
first failing command rather than running on and reporting green.

---

## Phase 5 — Verify

On the box:

```bash
cd ~/atlas-cmms
export GH_USER_OR_ORG=torres89
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Expect `atlas_db`, `atlas-cmms-backend`, `atlas-cmms-frontend`,
`atlas-cmms-agent`, `atlas-cmms-ingest`, `atlas_minio` and `caddy-proxy` up.
`atlas-cmms-telemetry` should **not** be there — it is profile-gated.

### 5.1 Migrations applied

```bash
docker logs atlas-cmms-backend 2>&1 | grep -iE 'liquibase|changelog|ERROR' | tail -30
```

### 5.2 pgvector is installed, not merely available

```bash
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT extversion FROM pg_extension WHERE extname='vector';"
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas -c "\d document_chunk"
```

An empty first result or a missing table means the knowledge layer silently
skipped its migrations. The data is fine; retrieval is just lexical-only until
you fix the extension and re-run.

### 5.3 The new services are healthy

```bash
curl -s localhost:8002/health                  # ingest worker, loopback-only
docker logs atlas-cmms-ingest --tail 30
docker logs atlas-cmms-agent --tail 30
```

### 5.4 Through the front door

```bash
curl -sI https://cmms.example.com | head -1
curl -sI https://cmms.example.com/agent | head -1
```

Then in a browser: log in, open an asset, and load its dossier. That single
page exercises the API, the new tables and the retrieval path together.

---

## Rollback

**If the restore in phase 3 went wrong** and the stack is not yet redeployed,
you have the dump. Drop the volume again, restart Postgres, and re-run the
restore from the S3 copy.

**If the deploy is up but the application is broken**, roll the code back and
leave the database alone — the restored schema tolerates the older code, since
the new tables are simply unused:

```bash
git revert -m 1 <merge-commit-sha>
git push origin main
```

That triggers a fresh build and deploy of the previous code.

**A full database rollback** means restoring the dump over the current volume,
which discards anything written since. Only worth it if the data itself is
wrong rather than the app:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
docker volume rm atlas-cmms_postgres_data
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres
zcat ~/atlas-pre-pgvector-<STAMP>.sql.gz \
  | docker exec -i atlas_db psql -U "$POSTGRES_USER" -d atlas -v ON_ERROR_STOP=1
```

---

## Troubleshooting

**`denied` or `manifest unknown` during `docker compose pull`**
The new packages are private and `GHCR_PAT` is missing, expired, or lacks
`read:packages`. Fix the secret and re-run the workflow, or make the packages
public at <https://github.com/Torres89?tab=packages>.

**Deploy fails at `git merge --ff-only`**
The checkout on the box has local commits or a dirty tracked file. This is the
guard doing its job — it refuses rather than discarding your changes. SSH in,
resolve it as in [1.1](#11-the-checkout-must-be-clean-and-on-main), re-run.

**`ingest-worker` restarts in a loop**
Usually the model download on first boot. `docker logs atlas-cmms-ingest`. The
weights land on the `model_cache` volume, so it only happens once; if it is
being killed, check `free -h` and `docker stats`.

**The knowledge search returns nothing**
Expected until documents have been ingested — the queue is drained serially and
deliberately unhurried. Confirm the worker is alive with
`curl -s localhost:8002/health` before digging further.

**Agent or MCP unreachable**
Almost always `AGENT_URL` / `MCP_PUBLIC_URL` not matching a Caddy route. Check
`grep -nE 'agent|mcp' Caddyfile` against the values in `.env`.

### If Postgres was already swapped

If Postgres ever gets restarted on the pgvector image over the old data
directory, assume the text indexes are suspect. The heap rows are fine — only
the index ordering is wrong — so rebuilding is enough:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  stop api agent frontend ingest-worker
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas -c "REINDEX DATABASE atlas;"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

`REINDEX DATABASE` takes an exclusive lock per index, so it is downtime too. A
unique index that fails to rebuild means duplicate rows slipped in while the
ordering was wrong; those have to be reconciled by hand. That is the reason
phase 3 prefers dump-and-restore, which cannot end up in that state.

---

## Quick checklist

- [ ] `GHCR_PAT` secret added with `read:packages`
- [ ] `~/atlas-cmms` is a clean checkout on `main` (converted per 1.5 if it was not one)
- [ ] Local compose edits rescued into `.env`, tracked files unmodified
- [ ] 5 GB+ free on `/`
- [ ] `.env` backed up, new variables added
- [ ] `MCP_PUBLIC_URL` is a bare origin with no `/mcp` suffix
- [ ] Caddy routes exist for `AGENT_URL` and `MCP_PUBLIC_URL`
- [ ] Dump taken **and copied off the instance**
- [ ] Old volume removed, new Postgres up, `pg_available_extensions` shows `vector`
- [ ] Restore run with `ON_ERROR_STOP=1`, row counts sane
- [ ] Checkout returned to `main`, working tree clean
- [ ] PR merged, both workflow jobs green
- [ ] `document_chunk` exists and `pg_extension` lists `vector`
- [ ] Asset dossier loads in the browser
