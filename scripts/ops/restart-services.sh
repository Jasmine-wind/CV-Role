#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
ENV_ARGS=(--env-file "$ENV_FILE")

if [[ "$#" -eq 0 ]]; then
  set -- backend nginx
fi

for service in "$@"; do
  case "$service" in
    backend|nginx|postgres|redis|minio)
      ;;
    minio-init|certbot)
      echo "Refusing to restart one-shot service: $service" >&2
      exit 1
      ;;
    *)
      echo "Unknown or unsupported service: $service" >&2
      echo "Supported services: backend nginx postgres redis minio" >&2
      exit 1
      ;;
  esac
done

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" restart "$@"

