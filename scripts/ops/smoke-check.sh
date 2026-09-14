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

bounded_http_diagnostic() {
  local url="$1"
  # These smoke targets are public, body-less GETs. Keep failure output useful but
  # bounded so a broken upstream cannot flood CI/operator logs.
  curl "${CURL_ARGS[@]}" --include "$url" 2>&1 | head -c 2048 || true
  printf '\n' >&2
}

expect_status() {
  local url="$1"
  local expected="$2"
  local actual
  if ! actual="$(http_status "$url")"; then
    echo "Smoke check failed: could not request $url" >&2
    bounded_http_diagnostic "$url" >&2
    return 1
  fi
  if [[ "$actual" != "$expected" ]]; then
    echo "Smoke check failed: $url returned HTTP $actual (expected $expected)" >&2
    bounded_http_diagnostic "$url" >&2
    return 1
  fi
  echo "ok: $url -> $actual"
}

expect_status "$BASE_URL/healthz" 200
expect_status "$BASE_URL/" 200
# Production intentionally disables Springdoc. Backend liveness/readiness are
# checked directly by the production Compose restart gate. GET on this POST-only
# endpoint has no side effect and its 405 proves that Nginx reached Spring MVC;
# accepting an arbitrary 4xx could mistake an Nginx/local 404 for backend health.
expect_status "$BASE_URL/api/auth/login" 405

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
