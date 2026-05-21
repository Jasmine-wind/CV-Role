#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"
BACKUP_DIR="${BACKUP_DIR:-backups/postgres}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

mkdir -p "$BACKUP_DIR"

ENV_ARGS=()
if [[ -f "$ENV_FILE" ]]; then
  ENV_ARGS=(--env-file "$ENV_FILE")
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

POSTGRES_DB="${POSTGRES_DB:-ai_resume_optimizer}"
POSTGRES_USER="${POSTGRES_USER:-dawn}"
BACKUP_FILE="$BACKUP_DIR/postgres-$TIMESTAMP.sql"

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" > "$BACKUP_FILE"

echo "PostgreSQL backup created: $BACKUP_FILE"

