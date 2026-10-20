#!/usr/bin/env bash
# Ежедневный прогон: слепок МКАД -> просмотры -> анализ. Повторный запуск в тот же день продолжает с места остановки:
# готовые этапы отмечаются файлами *.done, собранные части слепка и просмотров берутся из кэша.
# usage: daily/run.sh [YYYY-MM-DD]   (переменные: WORKERS=2 BUDGET=12000 DAILY_DATA=...)
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
DAY=${1:-$(TZ=Europe/Moscow date +%F)}
PY=.venv/bin/python
W=${WORKERS:-2}
D="${DAILY_DATA:-history}/$DAY"
mkdir -p "$D"
{
  echo "== $(date -Is) start $DAY"
  [ -f "$D/snapshot.done" ] || { $PY -I daily/snapshot.py --day "$DAY" --workers "$W" && touch "$D/snapshot.done"; } \
    || echo "snapshot: часть не собрана -- анализ по собранному, повторный запуск доберёт остальное"
  [ -f "$D/views.done" ] || { $PY -I daily/views.py --day "$DAY" --workers "$W" --budget "${BUDGET:-12000}" && touch "$D/views.done"; }
  $PY -I daily/analyze.py --day "$DAY"
  [ -f "$D/snapshot.done" ] && [ -f "$D/views.done" ] && rm -rf "$D/raw" "$D/views"  # кэш частей больше не нужен
  echo "== $(date -Is) done $DAY"
} 2>&1 | tee -a "$D/run.log"
