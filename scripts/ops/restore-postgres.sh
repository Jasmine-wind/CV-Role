#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup.sql>" >&2
  exit 1
fi

BACKUP_FILE="$1"
if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"

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

cat "$BACKUP_FILE" | docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

echo "PostgreSQL restored from: $BACKUP_FILE"

