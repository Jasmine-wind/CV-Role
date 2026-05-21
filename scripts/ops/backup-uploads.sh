#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-.env.production}"
BACKUP_DIR="${BACKUP_DIR:-backups/uploads}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

mkdir -p "$BACKUP_DIR"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

UPLOAD_DIR="${APP_STORAGE_LOCAL_BASE_DIR:-uploads}"
if [[ ! -d "$UPLOAD_DIR" ]]; then
  echo "Upload directory not found: $UPLOAD_DIR" >&2
  exit 1
fi

BACKUP_FILE="$BACKUP_DIR/uploads-$TIMESTAMP.tar.gz"
tar -czf "$BACKUP_FILE" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"

echo "Uploads backup created: $BACKUP_FILE"

