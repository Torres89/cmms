#!/bin/bash
# Backup script for Atlas CMMS
#
# On the S3/R2 storage tier, files already live in the object store with its own
# durability, so backup is just the Postgres dump. Set STORAGE_TYPE=minio (or
# local) to also sync the file volume — on those tiers the files sit on one
# volume on one box and the sync is the only thing standing between the customer
# and losing their manual library.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/tmp/atlas_backups}"
S3_BUCKET="${BACKUP_S3_BUCKET:-s3://YOUR-S3-BUCKET-NAME}"
DB_CONTAINER="${DB_CONTAINER:-atlas_db}"
DB_USER="${POSTGRES_USER:-my_secure_db_user}"
DB_NAME="${POSTGRES_DB:-atlas}"
STORAGE_TYPE="${STORAGE_TYPE:-minio}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
DATE=$(date +%F)

mkdir -p "$BACKUP_DIR"
trap 'rm -rf "$BACKUP_DIR"' EXIT

# 1. Postgres — the only thing that is genuinely ours to lose.
echo "Backing up PostgreSQL..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" \
  | gzip > "$BACKUP_DIR/db_backup_$DATE.sql.gz"

echo "Uploading database dump..."
aws s3 cp "$BACKUP_DIR/db_backup_$DATE.sql.gz" "$S3_BUCKET/postgres/"

# 2. Files — only on tiers where they are not already in an object store.
case "$(echo "$STORAGE_TYPE" | tr '[:upper:]' '[:lower:]')" in
  s3|gcp)
    echo "STORAGE_TYPE=$STORAGE_TYPE — files already live in object storage, nothing to sync."
    ;;
  local)
    echo "Syncing local file storage to S3..."
    aws s3 sync "${STORAGE_LOCAL_PATH:-/var/lib/docker/volumes/atlas-cmms_file_data/_data}" \
      "$S3_BUCKET/files/"
    ;;
  *)
    echo "Syncing MinIO data to S3..."
    aws s3 sync /var/lib/docker/volumes/atlas-cmms_minio_data/_data "$S3_BUCKET/minio/"
    ;;
esac

# 3. Prune old dumps so the bucket doesn't grow forever.
if [ "$RETENTION_DAYS" -gt 0 ]; then
  cutoff=$(date -d "$RETENTION_DAYS days ago" +%F 2>/dev/null || true)
  if [ -n "$cutoff" ]; then
    echo "Pruning dumps older than $cutoff..."
    aws s3 ls "$S3_BUCKET/postgres/" | awk '{print $4}' | while read -r key; do
      [ -z "$key" ] && continue
      stamp=$(echo "$key" | sed -n 's/^db_backup_\(....-..-..\)\.sql\.gz$/\1/p')
      [ -z "$stamp" ] && continue
      if [[ "$stamp" < "$cutoff" ]]; then
        aws s3 rm "$S3_BUCKET/postgres/$key"
      fi
    done
  fi
fi

echo "Backup completed for $DATE!"
