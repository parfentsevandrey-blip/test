#!/usr/bin/env bash
# Idempotent install of the web-search toolchain. Safe to re-run.
set -euo pipefail
cd "$(dirname "$0")"

export PLAYWRIGHT_BROWSERS_PATH="$PWD/.browsers"
export CRAWL4AI_NO_CLOUD_NOTICE=1

# Python: Scrapling + crawl4ai
[ -x .venv/bin/python ] || uv venv -q --python 3.13 .venv
uv pip install -q --python .venv/bin/python -r requirements.txt

# Chromium matching the installed Playwright (kept inside the project)
.venv/bin/python -m playwright install chromium >/dev/null
.venv/bin/python -m playwright install-deps chromium >/dev/null 2>&1 || true

# rea (reverse engineering CLI + MCP server)
command -v rea >/dev/null || npm install -g --silent rea-agents@6.7.0

echo "web-search toolchain ready"
