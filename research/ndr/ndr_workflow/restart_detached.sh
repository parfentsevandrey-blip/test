#!/usr/bin/env bash
# Wait for the gap between passes, stop the tool-bound loop by PID, start it again detached from the tool session.
cd "$(dirname "$0")"
until tail -1 workflow.log | grep -q "next pass in"; do sleep 10; done
for p in $(pgrep -f "python3 -I ndr_workflow.py loop"); do kill "$p" 2>/dev/null; done
sleep 3
setsid nohup ./run.sh loop > loop.out 2>&1 < /dev/null &
sleep 5
echo "restarted detached at $(date -u +%T): $(pgrep -f 'python3 -I ndr_workflow.py loop' | tr '\n' ' ')"
