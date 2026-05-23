#!/usr/bin/env bash
#
# Boot the Spring AI MCP server locally and expose it through a Cloudflare
# quick tunnel so it can be added as a Custom Connector in claude.ai.
#
# Both processes run in the background; Ctrl+C tears them down together.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${TMPDIR:-/tmp}/open-library-mcp"
mkdir -p "$LOG_DIR"
SPRING_LOG="$LOG_DIR/spring.log"
TUNNEL_LOG="$LOG_DIR/cloudflared.log"

SPRING_PID=""
TUNNEL_PID=""

cleanup() {
    echo
    echo "Shutting down…"
    if [[ -n "$TUNNEL_PID" ]] && kill -0 "$TUNNEL_PID" 2>/dev/null; then
        kill "$TUNNEL_PID" 2>/dev/null || true
    fi
    if [[ -n "$SPRING_PID" ]] && kill -0 "$SPRING_PID" 2>/dev/null; then
        # bootRun is the gradle wrapper; kill its java child too
        pkill -P "$SPRING_PID" 2>/dev/null || true
        kill "$SPRING_PID" 2>/dev/null || true
    fi
    wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

command -v cloudflared >/dev/null \
    || { echo "cloudflared not found. Install it first" >&2; exit 1; }

cd "$REPO_ROOT"

echo "Starting Spring Boot server (logs: $SPRING_LOG)…"
./gradlew --no-daemon bootRun > "$SPRING_LOG" 2>&1 &
SPRING_PID=$!

echo -n "Waiting for http://localhost:3001/mcp"
for i in $(seq 1 90); do
    if lsof -i :3001 -sTCP:LISTEN >/dev/null 2>&1; then
        echo " ready."
        break
    fi
    if ! kill -0 "$SPRING_PID" 2>/dev/null; then
        echo
        echo "Server exited before binding the port. Last log lines:" >&2
        tail -40 "$SPRING_LOG" >&2
        exit 1
    fi
    echo -n "."
    sleep 1
done

if ! lsof -i :3001 -sTCP:LISTEN >/dev/null 2>&1; then
    echo
    echo "Timed out waiting for port 3001." >&2
    exit 1
fi

echo "Starting Cloudflare quick tunnel (logs: $TUNNEL_LOG)…"
cloudflared tunnel --url http://localhost:3001 > "$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!

PUBLIC_URL=""
for i in $(seq 1 30); do
    PUBLIC_URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -1 || true)
    if [[ -n "$PUBLIC_URL" ]]; then break; fi
    if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
        echo "cloudflared exited. Last log lines:" >&2
        tail -40 "$TUNNEL_LOG" >&2
        exit 1
    fi
    sleep 1
done

if [[ -z "$PUBLIC_URL" ]]; then
    echo "Could not extract tunnel URL from log. Last lines:" >&2
    tail -40 "$TUNNEL_LOG" >&2
    exit 1
fi

cat <<EOF

────────────────────────────────────────────────────────────────
  Public MCP endpoint:
      ${PUBLIC_URL}/mcp

  Add as a Custom Connector:
      https://claude.ai/  →  Settings  →  Connectors  →  Add custom connector
      Paste the URL above.
────────────────────────────────────────────────────────────────

  Logs:
      Server : tail -f $SPRING_LOG
      Tunnel : tail -f $TUNNEL_LOG

  Press Ctrl+C to stop both processes.

EOF

wait
