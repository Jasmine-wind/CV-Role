#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
BASE_URL="${BASE_URL%/}"
CURL_INSECURE="${CURL_INSECURE:-no}"
CURL_MAX_TIME="${CURL_MAX_TIME:-20}"

CURL_ARGS=(--silent --show-error --max-time "$CURL_MAX_TIME")
if [[ "$CURL_INSECURE" == yes ]]; then
  CURL_ARGS+=(--insecure)
fi

http_status() {
  curl "${CURL_ARGS[@]}" --output /dev/null --write-out '%{http_code}' "$1"
}

expect_status() {
  local url="$1"
  local expected="$2"
  local actual
  actual="$(http_status "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Smoke check failed: $url returned HTTP $actual (expected $expected)" >&2
    return 1
  fi
  echo "ok: $url -> $actual"
}

expect_status "$BASE_URL/healthz" 200
expect_status "$BASE_URL/" 200
expect_status "$BASE_URL/v3/api-docs/" 200
# This route is intentionally called without credentials or a request body. Its
# exact application error status may vary, but a gateway/server failure must not.
api_status="$(http_status "$BASE_URL/api/auth/login")"
if [[ ! "$api_status" =~ ^[234][0-9][0-9]$ ]]; then
  echo "Smoke check failed: backend route returned HTTP $api_status" >&2
  exit 1
fi
echo "ok: $BASE_URL/api/auth/login -> $api_status"

if [[ "${EXPECT_HTTPS_REDIRECT:-no}" == yes ]]; then
  HTTP_URL="${SMOKE_HTTP_URL:-}"
  if [[ -z "$HTTP_URL" ]]; then
    if [[ "$BASE_URL" == https://* ]]; then
      HTTP_URL="http://${BASE_URL#https://}"
    else
      HTTP_URL="$BASE_URL"
    fi
  fi
  redirect="$(curl "${CURL_ARGS[@]}" --head --output /dev/null --write-out '%{http_code} %{redirect_url}' "$HTTP_URL")"
  if [[ "$redirect" != 301\ * && "$redirect" != 308\ * ]]; then
    echo "Smoke check failed: expected an HTTPS redirect, got '$redirect'" >&2
    exit 1
  fi
  echo "ok: HTTP target redirects to HTTPS ($redirect)"
fi

echo "Smoke check passed: $BASE_URL"
