#!/usr/bin/env bash
# Запуск «Барометра мобилизации».
#   ./run.sh            — поставить зависимости и запустить веб-сервер
#   PORT=8080 ./run.sh  — на другом порту
set -e
cd "$(dirname "$0")"

PY="${PYTHON:-python3}"

echo "→ Установка зависимостей (flask, requests, anthropic)…"
$PY -m pip install --quiet --ignore-installed blinker flask requests anthropic || \
  $PY -m pip install --quiet flask requests anthropic || true

echo "→ Запуск. Откройте http://127.0.0.1:${PORT:-5000}"
echo "  (опц.) ANTHROPIC_API_KEY — анализ Claude; X_BEARER_TOKEN — реальный X/Twitter"
exec $PY app.py
