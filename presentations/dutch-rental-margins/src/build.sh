#!/usr/bin/env bash
# Пересобрать PDF разбора и Word-документ с речью.
set -euo pipefail
cd "$(dirname "$0")"
CHROME="${CHROME:-/opt/pw-browsers/chromium-1194/chrome-linux/chrome}"

"$CHROME" --headless --no-sandbox --disable-gpu --hide-scrollbars \
  --allow-file-access-from-files --no-pdf-header-footer --virtual-time-budget=15000 \
  --print-to-pdf="$PWD/../presentation.pdf" "file://$PWD/news.html"

node speech.js       # пишет ../speech.docx
python3 model.py     # печатает все расчёты по квартире

echo "→ ../presentation.pdf, ../speech.docx"
