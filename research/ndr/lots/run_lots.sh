#!/usr/bin/env bash
# Daily lot snapshot (resumable): collect, then save to the repo. Started by resume_scan.sh / by hand.
cd "$(dirname "$0")"
python3 -I collector.py run >> run.out 2>&1
python3 -I unify.py > /dev/null 2>&1; python3 -I run_report.py >> run_report.out 2>&1   # mandatory run log (RUNLOG.md)
python3 -I ../ndr_workflow/sync_repo.py "Lots snapshot $(TZ=Europe/Moscow date +%F): $(python3 -I collector.py status)" >> run.out 2>&1
