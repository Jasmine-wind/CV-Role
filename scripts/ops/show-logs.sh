#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
TAIL_LINES="${TAIL_LINES:-200}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
ENV_ARGS=(--env-file "$ENV_FILE")

if [[ $# -eq 0 ]]; then
  set -- backend nginx postgres redis minio
fi

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" logs -f --tail "$TAIL_LINES" "$@"

