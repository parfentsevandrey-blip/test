#!/bin/sh
# Снимок рынка в архив. Ставить в планировщик — раз в сутки достаточно.
#
#   crontab:  17 4 * * *  cd /path/to/repo && tools/cian/snapshot.sh >> /var/log/cian.log 2>&1
#
# Смысл в повторении: один снимок не говорит ничего, второй показывает
# снижения цены, третий — что ушло с рынка. Пока снимков меньше двух,
# «настоящий срок экспозиции» держится только на близнецах.
set -eu
cd "$(dirname "$0")/../.."

node tools/cian/cian.js snapshot --queries docs/cian/watchlist.json --all --pages 4

# История цены и просмотры — точечно и дорого (полная загрузка страницы,
# капча примерно на каждой пятой). Берём только то, что уже оценено.
node tools/cian/cian.js card --from docs/cian/grades.json --limit 12 || true

git add docs/cian/archive.json
git diff --cached --quiet || git commit -m "Snapshot $(date +%F)"
