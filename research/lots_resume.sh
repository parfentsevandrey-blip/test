#!/usr/bin/env bash
# Resume today's snapshots of the resale pilot after a container restart: ЦИАН lots (collector.py) and
# developer price lists ([С1] devcat.py). Idempotent, never fails the caller.
set -uo pipefail
REPO="${CLAUDE_PROJECT_DIR:-/home/user/test}"
SCRATCH=/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad
WORK="${LOTS_WORK:-$SCRATCH}"
[ -f "$WORK/lots/collector.py" ] || { mkdir -p "$WORK"; cp -r "$REPO/research/ndr/lots" "$WORK/" 2>/dev/null; cp -r "$REPO/research/ndr/ndr_workflow" "$WORK/" 2>/dev/null; }
[ -f "$WORK/lots/collector.py" ] || { echo "lots: collector not found"; exit 0; }
exec 9>/tmp/lots_resume.lock
flock -n 9 || { echo "lots: another resume in progress"; exit 0; }
cd "$WORK/lots" || exit 0
DAY=$(TZ=Europe/Moscow date +%F)

finished() {  # finished <db>: today's run row has a finish time; devcat: every group done, blocked, or paused by a pause still on (L40)
python3 - "$1" "$DAY" <<'PY'
import importlib.util, sqlite3, sys
db, day = sys.argv[1], sys.argv[2]
try:
    c = sqlite3.connect(db)
    r = c.execute("select finished from runs where day=?", (day,)).fetchone()
    if r and r[0]:
        sys.exit(0)
    if db.endswith("devcat.db"):
        spec = importlib.util.spec_from_file_location("cooldown", "lib/cooldown.py")
        cd = importlib.util.module_from_spec(spec); spec.loader.exec_module(cd)
        site = {"lsr": "lsr.ru", "fsk": "fsk.ru", "donstroy": "donstroy.moscow"}
        groups = c.execute("select grp, status, finished from gruns where day=?", (day,)).fetchall()
        open_ = [g for g, st, fin in groups if not fin and not (st or "").startswith("BLOCKED")
                 and not (st == "paused" and cd.active(site.get(g, "")))]
        sys.exit(0 if groups and not open_ else 1)
except sqlite3.OperationalError:
    pass
sys.exit(1)
PY
}

resume() {  # resume <label> <script.py> <db> <runner.sh> [site paused after a block -> skip, L32]
  local label=$1 py=$2 db=$3 runner=$4 site=${5:-}
  [ -f "$py" ] || return 0
  if [ -n "$site" ] && ! python3 -I lib/cooldown.py check "$site" >/dev/null 2>&1; then
    echo "$label: $site paused ($(python3 -I lib/cooldown.py check "$site" | head -c 120))"; return 0; fi
  if pgrep -f "^python3 -I ${py%.py}[.]py run" >/dev/null; then echo "$label: collecting ($(python3 -I "$py" status))"; return 0; fi
  if finished "$db"; then echo "$label: today's snapshot done ($(python3 -I "$py" status))"; return 0; fi
  setsid nohup "./$runner" > /dev/null 2>&1 < /dev/null 9>&- &
  sleep 2
  echo "$label: snapshot started/resumed for $DAY"
}

resume lots collector.py lots.db run_lots.sh cian.ru
resume devcat devcat.py devcat.db run_devcat.sh
exit 0
