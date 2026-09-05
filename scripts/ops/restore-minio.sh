#!/usr/bin/env bash
set -euo pipefail
umask 077

usage() {
  cat >&2 <<'USAGE'
Usage: restore-minio.sh <backup-directory> --target-bucket <bucket> --confirm

The target bucket is explicitly named because restore removes target objects
that are not present in the backup. Use an isolated bucket for a drill.
USAGE
  exit 2
}

BACKUP_DIR=""
TARGET_BUCKET=""
CONFIRMED=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --confirm)
      CONFIRMED=true
      shift
      ;;
    --target-bucket)
      [[ $# -ge 2 ]] || usage
      TARGET_BUCKET="$2"
      shift 2
      ;;
    --help|-h)
      usage
      ;;
    --*)
      echo "Unknown option: $1" >&2
      usage
      ;;
    *)
      [[ -z "$BACKUP_DIR" ]] || usage
      BACKUP_DIR="$1"
      shift
      ;;
  esac
done

[[ -n "$BACKUP_DIR" && -n "$TARGET_BUCKET" && "$CONFIRMED" == true ]] || usage
if [[ ! -d "$BACKUP_DIR" || ! -d "$BACKUP_DIR/objects" || ! -f "$BACKUP_DIR/METADATA" || ! -f "$BACKUP_DIR/SHA256SUMS" ]]; then
  echo "Restore refused: backup directory is incomplete: $BACKUP_DIR" >&2
  exit 1
fi
if ! grep -q '^format=CV_ROLE_MINIO_BACKUP_V1$' "$BACKUP_DIR/METADATA"; then
  echo "Restore refused: unsupported MinIO backup format" >&2
  exit 1
fi
EXPECTED_COUNT="$(awk -F= '$1 == "object_count" { print $2 }' "$BACKUP_DIR/METADATA")"
if [[ ! "$EXPECTED_COUNT" =~ ^[0-9]+$ ]]; then
  echo "Restore refused: invalid object count in backup metadata" >&2
  exit 1
fi
(
  cd "$BACKUP_DIR/objects"
  if [[ "$EXPECTED_COUNT" -gt 0 ]]; then
    sha256sum --check ../SHA256SUMS >/dev/null
  fi
)
ACTUAL_COUNT="$(find "$BACKUP_DIR/objects" -type f -print | wc -l | tr -d ' ')"
if [[ "$ACTUAL_COUNT" != "$EXPECTED_COUNT" ]]; then
  echo "Restore refused: backup object count mismatch (metadata=$EXPECTED_COUNT, files=$ACTUAL_COUNT)" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
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

ENV_ARGS=(--env-file "$ENV_FILE")
echo "Restoring '$BACKUP_DIR' into explicitly confirmed bucket '$TARGET_BUCKET'..."
docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" run --rm --no-deps -T \
  -e "RESTORE_TARGET_BUCKET=$TARGET_BUCKET" \
  -e "EXPECTED_OBJECT_COUNT=$EXPECTED_COUNT" \
  -v "$(realpath -e "$BACKUP_DIR"):/backup-root:ro,Z" \
  --entrypoint /bin/sh minio-init -c '
    set -eu
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    mc mb --ignore-existing "local/$RESTORE_TARGET_BUCKET"
    mc mirror --overwrite --preserve --remove /backup-root/objects "local/$RESTORE_TARGET_BUCKET"
    rm -rf /tmp/minio-restore-verification
    mc mirror --overwrite "local/$RESTORE_TARGET_BUCKET" /tmp/minio-restore-verification
    restored_count=$(mc ls --recursive --quiet "local/$RESTORE_TARGET_BUCKET" | wc -l | tr -d " ")
    expected_count="$EXPECTED_OBJECT_COUNT"
    [ "$restored_count" = "$expected_count" ]
    if [ "$expected_count" -gt 0 ]; then
      (cd /tmp/minio-restore-verification && sha256sum -c /backup-root/SHA256SUMS 2>/dev/null)
    fi
  '

echo "MinIO restore verified: $TARGET_BUCKET (objects: $EXPECTED_COUNT)"
