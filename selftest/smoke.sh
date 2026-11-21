#!/usr/bin/env bash
set -euo pipefail
MCP=http://127.0.0.1:8080/mcp

call() {
  curl -fsS -X POST "$MCP" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d "$1"
}

echo "== ping =="
call '{"jsonrpc":"2.0","id":1,"method":"ping"}'
echo
echo "== tools/list =="
call '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | head -c 400
echo
