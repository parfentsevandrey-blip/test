#!/usr/bin/env bash
# Пересобрать PDF из HTML. Требуется Chromium (headless).
set -euo pipefail
cd "$(dirname "$0")"
CHROME="${CHROME:-/opt/pw-browsers/chromium-1194/chrome-linux/chrome}"
"$CHROME" --headless --no-sandbox --disable-gpu --hide-scrollbars \
  --allow-file-access-from-files --no-pdf-header-footer --virtual-time-budget=12000 \
  --print-to-pdf="$PWD/dutch-rental-margins.pdf" "file://$PWD/deck.html"
echo "→ dutch-rental-margins.pdf"
