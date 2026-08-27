#!/usr/bin/env bash
# Пересобрать оба PDF и Word-документ с речью.
set -euo pipefail
cd "$(dirname "$0")"
OUT=".."
CHROME="${CHROME:-/opt/pw-browsers/chromium-1194/chrome-linux/chrome}"

render () {  # render <входной html> <выходной pdf>
  "$CHROME" --headless --no-sandbox --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files --no-pdf-header-footer --virtual-time-budget=12000 \
    --print-to-pdf="$OUT/$2" "file://$PWD/$1"
}

render simple.html presentation.pdf
render deck.html   presentation-detailed.pdf

node speech.js          # пишет ../speech.docx
python3 model.py        # печатает все расчёты кейса

echo "→ ../presentation.pdf, ../presentation-detailed.pdf, ../speech.docx"
