#!/usr/bin/env bash
# Resume the MKAD scan after a container restart. Idempotent: safe to call from the SessionStart hook,
# the hourly check-in and by hand at the same time (flock + "already running" check).
# Never fails the caller: every problem is reported on stdout and the script exits 0.
set -uo pipefail

REPO="${CLAUDE_PROJECT_DIR:-/home/user/test}"
COPY="$REPO/research/ndr"                                   # committed copy (state.json after every pass)
SCRATCH=/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad
if [ -n "${NDR_WORK:-}" ]; then WORK="$NDR_WORK"
elif [ -f "$SCRATCH/ndr_workflow/state.json" ]; then WORK="$SCRATCH"   # live copy, keeps the cleared browser session
else WORK="$HOME/ndr_work"; fi

exec 9>/tmp/ndr_resume.lock
flock -n 9 || { echo "ndr scan: another resume is in progress"; exit 0; }

if pgrep -f '^python3 -I ndr_workflow[.]py loop' >/dev/null; then
  echo "ndr scan: already running ($WORK)"; exit 0
fi

# 1. Work dir lost with the container: restore code + collected state from the repo.
#    The browser profile is never committed, so the site sees a new session (clearance is earned again).
if [ ! -f "$WORK/ndr_workflow/state.json" ]; then
  [ -f "$COPY/ndr_workflow/state.json" ] || { echo "ndr scan: no state in $COPY, nothing to resume"; exit 0; }
  mkdir -p "$WORK"
  cp -r "$COPY"/ndr_workflow "$COPY"/moscow_scan "$COPY"/ndr "$COPY"/report "$WORK"/
  cp "$COPY"/*.py "$COPY"/*.json "$COPY"/*.txt "$WORK"/ 2>/dev/null
  cp "$COPY"/README.md "$WORK"/ndr_workflow/README_research.md
  python3 - "$WORK/ndr_workflow/state.json" <<'EOF'
import json, sys
p = sys.argv[1]; st = json.load(open(p))
st["session"] = None                     # the old profile path does not exist here
json.dump(st, open(p, "w"), ensure_ascii=False, indent=1)
EOF
  echo "ndr scan: restored work dir $WORK from the repo"
fi

# 2. Finished? Then there is nothing to start.
progress=$(python3 - "$WORK/ndr_workflow/state.json" <<'EOF'
import json, sys
st = json.load(open(sys.argv[1]))
items = [it["href"] for v in st["searches"].values() for it in v]
left = sum(h not in st["objects"] for h in items)
print(f"{len(items) - left} {len(items)} {left}")
EOF
) || { echo "ndr scan: state.json unreadable"; exit 0; }
read -r got total left <<<"$progress"
if [ "$left" = "0" ]; then echo "ndr scan: complete ($got/$total)"; exit 0; fi

# 3. Dependencies (normally already in the image; installed only if missing).
command -v xvfb-run >/dev/null || timeout 300 apt-get install -y -qq xvfb >/dev/null 2>&1
python3 -c "import scrapling" 2>/dev/null || timeout 600 pip install -q "scrapling[fetchers]" >/dev/null 2>&1
missing=""
command -v google-chrome >/dev/null || missing="$missing google-chrome"
command -v xvfb-run >/dev/null || missing="$missing xvfb-run"
python3 -c "import scrapling" 2>/dev/null || missing="$missing scrapling"
if [ -n "$missing" ]; then echo "ndr scan: NOT started, missing:$missing"; exit 0; fi

# 4. Start detached; the loop itself keeps the pass gap after a restart and commits after every pass.
cd "$WORK/ndr_workflow" || exit 0
echo "$(date -u +%FT%T+00:00) resume_scan: starting loop" >> loop.out
setsid nohup ./run.sh loop >> loop.out 2>&1 < /dev/null 9>&- &   # 9>&-: the loop must not hold the lock
sleep 2
if pgrep -f '^python3 -I ndr_workflow[.]py loop' >/dev/null; then
  echo "ndr scan: resumed ($got/$total collected, $left left) in $WORK"
  # a pass cut short by the container never reaches the post-pass commit: save what it collected now
  (python3 ../moscow_scan/scan_export.py && python3 sync_repo.py "MKAD scan progress: $got/$total pages collected") \
    >> loop.out 2>&1 < /dev/null 9>&- &
else
  echo "ndr scan: start failed, see $WORK/ndr_workflow/loop.out"; tail -3 loop.out
fi
exit 0
