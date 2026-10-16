#!/usr/bin/env bash
# Resume today's lot snapshot (resale pilot) after a container restart. Idempotent, never fails the caller.
set -uo pipefail
REPO="${CLAUDE_PROJECT_DIR:-/home/user/test}"
SCRATCH=/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad
WORK="${LOTS_WORK:-$SCRATCH}"
[ -f "$WORK/lots/collector.py" ] || { mkdir -p "$WORK"; cp -r "$REPO/research/ndr/lots" "$WORK/" 2>/dev/null; cp -r "$REPO/research/ndr/ndr_workflow" "$WORK/" 2>/dev/null; }
[ -f "$WORK/lots/collector.py" ] || { echo "lots: collector not found"; exit 0; }
exec 9>/tmp/lots_resume.lock
flock -n 9 || { echo "lots: another resume in progress"; exit 0; }
if pgrep -f '^python3 -I collector[.]py run' >/dev/null; then echo "lots: collecting ($(cd "$WORK/lots" && python3 -I collector.py status))"; exit 0; fi
cd "$WORK/lots" || exit 0
DAY=$(TZ=Europe/Moscow date +%F)
if python3 - "$DAY" <<'PY'
import sqlite3, sys
db = sqlite3.connect("lots.db")
try:
    r = db.execute("select finished from runs where day=?", (sys.argv[1],)).fetchone()
except sqlite3.OperationalError:
    r = None
sys.exit(0 if r and r[0] else 1)
PY
then echo "lots: today's snapshot done ($(python3 -I collector.py status))"; exit 0; fi
setsid nohup ./run_lots.sh > /dev/null 2>&1 < /dev/null 9>&- &
sleep 2
echo "lots: snapshot started/resumed for $DAY"
exit 0
