#!/usr/bin/env bash
set -euo pipefail

# This script only targets deploy/demo's separately named Compose project and
# requires an explicit confirmation value before removing its DB/storage volumes.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEMO_DIR="$ROOT_DIR/deploy/demo"
COMPOSE_FILE="$DEMO_DIR/docker-compose.yml"
ENV_FILE="${1:-$DEMO_DIR/.env}"
DEMO_PROJECT="cv-role-demo"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Demo env file not found: $ENV_FILE" >&2
  exit 1
fi
ENV_FILE="$(realpath -e "$ENV_FILE")"
DEMO_DIR="$(realpath -e "$DEMO_DIR")"
if [[ "$ENV_FILE" != "$DEMO_DIR/"* ]]; then
  echo "Refusing an environment file outside deploy/demo." >&2
  exit 1
fi

# The file is operator-owned and constrained to deploy/demo above.
# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

if [[ "${DEMO_RESET_CONFIRM:-}" != "RESET_DEMO_ENVIRONMENT" ]]; then
  echo "Refusing reset. Set DEMO_RESET_CONFIRM=RESET_DEMO_ENVIRONMENT in the demo env file." >&2
  exit 1
fi

if [[ "$(basename "$COMPOSE_FILE")" != "docker-compose.yml" || "$COMPOSE_FILE" != *"/deploy/demo/"* ]]; then
  echo "Refusing unexpected Compose target." >&2
  exit 1
fi

docker compose --project-name "$DEMO_PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down -v --remove-orphans
docker compose --project-name "$DEMO_PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

echo "Demo environment reset. It remains loopback-only unless an operator intentionally fronts it with separate access control."
