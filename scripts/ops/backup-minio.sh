#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"
BACKUP_DIR="${BACKUP_DIR:-backups/minio}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

mkdir -p "$BACKUP_DIR"

ENV_ARGS=()
if [[ -f "$ENV_FILE" ]]; then
  ENV_ARGS=(--env-file "$ENV_FILE")
fi

TARGET_DIR="$BACKUP_DIR/minio-data-$TIMESTAMP"
mkdir -p "$TARGET_DIR"

docker compose -f "$COMPOSE_FILE" "${ENV_ARGS[@]}" cp minio:/data/. "$TARGET_DIR/"

echo "MinIO backup created: $TARGET_DIR"

