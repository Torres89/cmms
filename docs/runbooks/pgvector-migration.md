# Migrating Postgres to the pgvector image

`docker-compose.yml` moves Postgres from `postgres:16-alpine` to
`pgvector/pgvector:pg16`. The major version is the same, so the container will
mount the existing data directory and start without complaining. **That is the
danger.** Alpine is musl, the pgvector image is Debian/glibc, and the two sort
text differently. Every b-tree index on a text column was built under musl's
collation and would then be read under glibc's, which produces wrong answers to
range and equality lookups without an error anywhere.

Run this once, in a maintenance window, **before** merging
`feat/machine-specialist-platform` to `main`. The deploy on merge does
`pull && up -d`, which would otherwise perform the swap unsupervised.

Everything below runs on the EC2 box, from `~/atlas-cmms`:

```bash
ssh -i ~/Documents/Maint/keypair/atlas-prod-key.pem ubuntu@98.83.54.9
cd ~/atlas-cmms
```

Step 4 assumes `~/atlas-cmms` is a git checkout. If it is not, convert it
first — see
[1.5 in the EC2 deploy guide](./ec2-deploy.md#15-converting-a-hand-assembled-directory-into-a-checkout).

## 0. Pre-flight

```bash
cd ~/atlas-cmms
set -a; . ./.env; set +a          # POSTGRES_USER / POSTGRES_PWD

docker compose ps                  # note what is running
docker inspect atlas_db --format '{{.Config.Image}}'   # expect postgres:16-alpine
df -h /                            # need room for the dump + ~3 GB of new images
```

If the image already reads `pgvector/pgvector:pg16`, the swap has happened.
Skip to [If the swap already happened](#if-the-swap-already-happened).

## 1. Quiesce the writers, keep the database up

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  stop api agent frontend ingest-worker
```

Postgres stays running — the dump has to be taken by the *old* binary.

## 2. Dump, while still on Alpine

```bash
STAMP=$(date +%F-%H%M)
docker exec atlas_db pg_dump -U "$POSTGRES_USER" --clean --if-exists atlas \
  | gzip > ~/atlas-pre-pgvector-$STAMP.sql.gz

ls -lh ~/atlas-pre-pgvector-$STAMP.sql.gz     # sanity: not a few hundred bytes
zcat ~/atlas-pre-pgvector-$STAMP.sql.gz | tail -5   # should end cleanly
```

Get it off the instance before touching the volume. There is no AWS CLI on the
box, so copy it down over SSH — **from your own machine**:

```bash
scp -i ~/Documents/Maint/keypair/atlas-prod-key.pem \
  ubuntu@98.83.54.9:'~/atlas-pre-pgvector-*.sql.gz' .
gzip -t atlas-pre-pgvector-*.sql.gz && echo "archive is valid"
```

Do not continue until that copy exists off the instance and passes `gzip -t`.

## 3. Drop the old data directory

The collation problem lives in the data directory, so it has to go. This is the
irreversible step; step 2 is your only way back.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
docker volume rm atlas-cmms_postgres_data
```

If the name does not resolve, find it with `docker volume ls | grep postgres`.
The compose project is named `atlas-cmms`, so the volume is normally
`atlas-cmms_postgres_data`.

## 4. Start the new Postgres alone

```bash
git pull                                     # or: git fetch && git checkout main
export GH_USER_OR_ORG=torres89
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres

docker logs -f atlas_db                      # wait for "database system is ready"
```

A fresh `initdb` runs under glibc and `POSTGRES_DB=atlas` creates an empty
database. Confirm the extension is available:

```bash
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT 1 FROM pg_available_extensions WHERE name='vector';"
```

One row means the image is right. No rows means you are still on a plain
Postgres image — stop and fix that first, or the knowledge layer will silently
mark its migrations as run and fall back to lexical-only retrieval.

## 5. Restore

```bash
zcat ~/atlas-pre-pgvector-$STAMP.sql.gz \
  | docker exec -i atlas_db psql -U "$POSTGRES_USER" -d atlas -v ON_ERROR_STOP=1
```

`ON_ERROR_STOP=1` matters: without it psql reports success after skipping
failed statements. Errors mentioning the `vector` type are the exception and
are expected — the old dump predates it.

The dump carries `databasechangelog` with it, so Liquibase will recognise every
changeset that had already run and apply only the seven new ones.

## 6. Bring the stack up

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker logs -f atlas-cmms-backend       # watch Liquibase apply the new changelogs
```

## 7. Verify

```bash
# Collation is consistent again — this should report no mismatches.
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT datname, datcollate, datctype FROM pg_database WHERE datname='atlas';"

# pgvector is really installed, not merely available.
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT extversion FROM pg_extension WHERE extname='vector';"

# The knowledge layer's table exists rather than having been skipped.
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas -c "\d document_chunk"

# Row counts match what you had before.
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas \
  -c "SELECT count(*) FROM asset; SELECT count(*) FROM work_order;"
```

Then exercise the app: log in, open an asset, and load its dossier
(`GET /assets/{id}/dossier`).

## If the swap already happened

If Postgres was restarted on the pgvector image over the old data directory,
assume the text indexes are suspect. Rebuilding them is enough — the heap rows
are fine, only the index ordering is wrong:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop api agent frontend ingest-worker
docker exec atlas_db psql -U "$POSTGRES_USER" -d atlas -c "REINDEX DATABASE atlas;"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

A unique index that fails to rebuild means duplicate rows slipped in while the
ordering was wrong. That has to be reconciled by hand before the reindex will
complete; it is also the reason to prefer the dump-and-restore path above,
which cannot land in that state.

`REINDEX DATABASE` takes an exclusive lock per index, so treat it as downtime.
