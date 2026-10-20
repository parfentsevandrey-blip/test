#!/usr/bin/env bash
# Installs Scrapling (all extras) into tools/scrapling/.venv
# Usage: bash tools/scrapling/setup.sh
set -euo pipefail
cd "$(dirname "$0")"

python3 -m venv .venv
./.venv/bin/pip install --upgrade pip
./.venv/bin/pip install "scrapling[all]"

# Browsers for StealthyFetcher / spiders / MCP browser tools.
# If a Chromium is already installed (e.g. /opt/pw-browsers/chromium), skip this
# and export SCRAPLING_CHROMIUM=/path/to/chrome instead.
if [ -z "${SCRAPLING_CHROMIUM:-}" ]; then
  ./.venv/bin/scrapling install
fi

./.venv/bin/scrapling --version
