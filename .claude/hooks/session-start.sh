#!/bin/bash
set -euo pipefail

# Only run in Claude Code cloud sessions
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Scrapling with all extras (fetchers, CLI, MCP server, shell)
if ! command -v scrapling >/dev/null 2>&1 || ! pip show --quiet scrapling >/dev/null 2>&1; then
  pip install --quiet --root-user-action=ignore "scrapling[all]"
fi

# Browsers and system dependencies for the browser-based fetchers
scrapling install
