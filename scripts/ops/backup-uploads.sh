#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-backups/uploads}"
TIMESTAMP="$(date +%F_%H-%M-%S)"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

UPLOAD_DIR="${APP_STORAGE_LOCAL_BASE_DIR:-uploads}"
if [[ ! -d "$UPLOAD_DIR" ]]; then
  echo "Upload directory not found: $UPLOAD_DIR" >&2
  exit 1
fi

BACKUP_FILE="$BACKUP_DIR/uploads-$TIMESTAMP.tar.gz"
tar -czf "$BACKUP_FILE" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
[[ -s "$BACKUP_FILE" ]]
chmod 600 "$BACKUP_FILE"

echo "Uploads backup created: $BACKUP_FILE"

