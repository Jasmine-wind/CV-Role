#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-backups/minio}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required in $ENV_FILE}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required in $ENV_FILE}"
: "${MINIO_BUCKET:?MINIO_BUCKET is required in $ENV_FILE}"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
TARGET_DIR="$BACKUP_DIR/minio-data-$TIMESTAMP"
mkdir -p "$TARGET_DIR/objects"
TARGET_DIR="$(realpath -e "$TARGET_DIR")"
chmod 700 "$TARGET_DIR" "$TARGET_DIR/objects"
cleanup() {
  if [[ -d "$TARGET_DIR" && ! -f "$TARGET_DIR/SHA256SUMS" ]]; then
    rm -rf "$TARGET_DIR"
  fi
}
trap cleanup EXIT

ENV_ARGS=(--env-file "$ENV_FILE")
# Use the S3 API through mc rather than copying MinIO's live /data directory.
# This avoids copying internal metadata and gives us an object-level manifest.
docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" run --rm --no-deps -T \
  -v "$TARGET_DIR/objects:/backup:Z" \
  --entrypoint /bin/sh minio-init -c '
    set -eu
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    mc ls "local/$MINIO_BUCKET" >/dev/null
    mc mirror --overwrite --preserve --checksum SHA256 "local/$MINIO_BUCKET" /backup
  '

OBJECT_COUNT="$(find "$TARGET_DIR/objects" -type f -print | wc -l | tr -d ' ')"
printf 'format=CV_ROLE_MINIO_BACKUP_V1\nbucket=%s\nobject_count=%s\ncreated_at=%s\n' \
  "$MINIO_BUCKET" "$OBJECT_COUNT" "$TIMESTAMP" > "$TARGET_DIR/METADATA"
(
  cd "$TARGET_DIR/objects"
  find . -type f -print0 | while IFS= read -r -d '' file; do
    sha256sum "$file"
  done
) > "$TARGET_DIR/SHA256SUMS"

if ! grep -q '^format=CV_ROLE_MINIO_BACKUP_V1$' "$TARGET_DIR/METADATA"; then
  echo "MinIO backup verification failed: metadata is invalid" >&2
  exit 1
fi
if ! grep -q "^object_count=$OBJECT_COUNT$" "$TARGET_DIR/METADATA"; then
  echo "MinIO backup verification failed: object count is invalid" >&2
  exit 1
fi
(
  cd "$TARGET_DIR/objects"
  if [[ "$OBJECT_COUNT" -gt 0 ]]; then
    sha256sum --check ../SHA256SUMS >/dev/null
  fi
)
chmod 600 "$TARGET_DIR/METADATA" "$TARGET_DIR/SHA256SUMS"
trap - EXIT

echo "MinIO backup verified: $TARGET_DIR (objects: $OBJECT_COUNT)"
