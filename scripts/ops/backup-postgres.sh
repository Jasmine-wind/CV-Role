#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-backups/postgres}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

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

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/postgres-$TIMESTAMP.sql"
TEMP_FILE="$(mktemp "$BACKUP_DIR/.postgres-$TIMESTAMP.XXXXXX")"
cleanup() {
  rm -f "$TEMP_FILE"
}
trap cleanup EXIT

ENV_ARGS=(--env-file "$ENV_FILE")

# Query first so a successful dump is also tied to a live database with user tables.
OBJECT_COUNT="$(docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  psql -X -qAt --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname not in ('pg_catalog', 'information_schema') and c.relkind in ('r', 'p', 'v', 'm', 'f');")"
OBJECT_COUNT="${OBJECT_COUNT//$'\r'/}"
if [[ ! "$OBJECT_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "Backup refused: source database has no user-owned database objects" >&2
  exit 1
fi

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  pg_dump --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" > "$TEMP_FILE"

if [[ ! -s "$TEMP_FILE" ]] || ! head -n 5 "$TEMP_FILE" | grep -q 'PostgreSQL database dump'; then
  echo "Backup verification failed: dump is missing or not a PostgreSQL SQL dump" >&2
  exit 1
fi
if ! grep -qE '(^|[[:space:]])CREATE[[:space:]]+(TABLE|SCHEMA|EXTENSION)' "$TEMP_FILE"; then
  echo "Backup verification failed: dump contains no schema objects" >&2
  exit 1
fi

mv "$TEMP_FILE" "$BACKUP_FILE"
CHECKSUM_FILE="$BACKUP_FILE.sha256"
(
  cd "$(dirname "$BACKUP_FILE")"
  sha256sum "$(basename "$BACKUP_FILE")" > "$(basename "$CHECKSUM_FILE")"
  sha256sum --check "$(basename "$CHECKSUM_FILE")" >/dev/null
)
chmod 600 "$BACKUP_FILE" "$CHECKSUM_FILE"
trap - EXIT

echo "PostgreSQL backup verified: $BACKUP_FILE (source objects: $OBJECT_COUNT)"
echo "SHA-256 checksum: $CHECKSUM_FILE"
