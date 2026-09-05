#!/usr/bin/env bash
set -euo pipefail
umask 077

usage() {
  cat >&2 <<'USAGE'
Usage: restore-postgres.sh <backup.sql> --target-database <database> --confirm

The target database must be named in the selected environment file. The target
must be empty unless RESTORE_ALLOW_NONEMPTY=yes is explicitly set.
USAGE
  exit 2
}

BACKUP_FILE=""
TARGET_DATABASE=""
CONFIRMED=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --confirm)
      CONFIRMED=true
      shift
      ;;
    --target-database)
      [[ $# -ge 2 ]] || usage
      TARGET_DATABASE="$2"
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
      [[ -z "$BACKUP_FILE" ]] || usage
      BACKUP_FILE="$1"
      shift
      ;;
  esac
done

[[ -n "$BACKUP_FILE" && -n "$TARGET_DATABASE" && "$CONFIRMED" == true ]] || usage
if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi
if [[ ! -s "$BACKUP_FILE" ]] || ! head -n 5 "$BACKUP_FILE" | grep -q 'PostgreSQL database dump'; then
  echo "Restore refused: backup is missing or not a PostgreSQL SQL dump" >&2
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

: "${POSTGRES_DB:?POSTGRES_DB is required in $ENV_FILE}"
: "${POSTGRES_USER:?POSTGRES_USER is required in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required in $ENV_FILE}"
if [[ "$TARGET_DATABASE" != "$POSTGRES_DB" ]]; then
  echo "Restore refused: target database '$TARGET_DATABASE' does not match POSTGRES_DB from $ENV_FILE" >&2
  exit 1
fi

CHECKSUM_FILE="$BACKUP_FILE.sha256"
if [[ -f "$CHECKSUM_FILE" ]]; then
  (
    cd "$(dirname "$BACKUP_FILE")"
    sha256sum --check "$(basename "$CHECKSUM_FILE")" >/dev/null
  ) || {
    echo "Restore refused: backup checksum verification failed" >&2
    exit 1
  }
else
  echo "Warning: no checksum sidecar found; continuing after SQL format validation" >&2
fi

ENV_ARGS=(--env-file "$ENV_FILE")
EXISTING_OBJECTS="$(docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  psql -X -qAt --no-password -U "$POSTGRES_USER" -d "$TARGET_DATABASE" \
  -c "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname not in ('pg_catalog', 'information_schema') and c.relkind in ('r', 'p', 'v', 'm', 'f');")"
EXISTING_OBJECTS="${EXISTING_OBJECTS//$'\r'/}"
if [[ "$EXISTING_OBJECTS" =~ ^[1-9][0-9]*$ && "${RESTORE_ALLOW_NONEMPTY:-no}" != yes ]]; then
  echo "Restore refused: target contains $EXISTING_OBJECTS user-owned objects" >&2
  echo "Use an isolated empty database, or set RESTORE_ALLOW_NONEMPTY=yes only after a separate backup and review" >&2
  exit 1
fi

echo "Restoring '$BACKUP_FILE' into explicitly confirmed database '$TARGET_DATABASE'..."
docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  psql -X --no-password --single-transaction --set ON_ERROR_STOP=on \
  -U "$POSTGRES_USER" -d "$TARGET_DATABASE" < "$BACKUP_FILE"

RESTORED_OBJECTS="$(docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  psql -X -qAt --no-password -U "$POSTGRES_USER" -d "$TARGET_DATABASE" \
  -c "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname not in ('pg_catalog', 'information_schema') and c.relkind in ('r', 'p', 'v', 'm', 'f');")"
RESTORED_OBJECTS="${RESTORED_OBJECTS//$'\r'/}"
if [[ ! "$RESTORED_OBJECTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "Restore verification failed: target has no user-owned database objects" >&2
  exit 1
fi

echo "PostgreSQL restore verified: $TARGET_DATABASE (objects: $RESTORED_OBJECTS)"
