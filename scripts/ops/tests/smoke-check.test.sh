#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP_DIR="$(mktemp -d)"
SERVER_PID=""
cleanup() {
  if [[ -n "$SERVER_PID" ]]; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

python3 - "$TMP_DIR/port" "$TMP_DIR/requests" "$TMP_DIR/force-auth-404" <<'PY' &
import http.server
import pathlib
import sys

port_file = pathlib.Path(sys.argv[1])
request_log = pathlib.Path(sys.argv[2])
auth_404_flag = pathlib.Path(sys.argv[3])

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        with request_log.open("a", encoding="utf-8") as stream:
            stream.write(f"{self.path}\n")
        if self.path in ("/", "/healthz"):
            status = 200
        elif self.path == "/api/auth/login":
            status = 404 if auth_404_flag.exists() else 405
        else:
            status = 404
        body = b"ok\n" if status in (200, 405) else b"x" * 8192
        self.send_response(status)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        pass

server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
port_file.write_text(str(server.server_port), encoding="utf-8")
server.serve_forever()
PY
SERVER_PID=$!

for attempt in {1..50}; do
  [[ -s "$TMP_DIR/port" ]] && break
  sleep 0.1
done
[[ -s "$TMP_DIR/port" ]]
BASE_URL="http://127.0.0.1:$(<"$TMP_DIR/port")"

BASE_URL="$BASE_URL" "$ROOT_DIR/scripts/ops/smoke-check.sh" >/dev/null
grep -qx '/api/auth/login' "$TMP_DIR/requests"
if grep -q '/v3/api-docs' "$TMP_DIR/requests"; then
  echo 'Smoke check still requested the production-disabled Springdoc endpoint' >&2
  exit 1
fi

touch "$TMP_DIR/force-auth-404"
if BASE_URL="$BASE_URL" "$ROOT_DIR/scripts/ops/smoke-check.sh" >"$TMP_DIR/auth-404.out" 2>&1; then
  echo 'Smoke check unexpectedly accepted a local 404 for the backend route' >&2
  exit 1
fi

auth_diagnostic_size=$(wc -c < "$TMP_DIR/auth-404.out")
if (( auth_diagnostic_size > 2300 )); then
  echo "Smoke diagnostic exceeded its bounded allowance: $auth_diagnostic_size bytes" >&2
  exit 1
fi
diagnostic_body_bytes=$(grep -o 'x' "$TMP_DIR/auth-404.out" | wc -l)
if (( diagnostic_body_bytes <= 0 || diagnostic_body_bytes >= 8192 )); then
  echo 'Smoke diagnostic did not include a bounded response prefix' >&2
  exit 1
fi

echo 'smoke-check red/green contract: PASS'
