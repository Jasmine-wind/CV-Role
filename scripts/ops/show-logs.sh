#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"
TAIL_LINES="${TAIL_LINES:-200}"

ENV_ARGS=()
if [[ -f "$ENV_FILE" ]]; then
  ENV_ARGS=(--env-file "$ENV_FILE")
fi

if [[ $# -eq 0 ]]; then
  set -- backend nginx postgres redis minio
fi

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" logs -f --tail "$TAIL_LINES" "$@"

