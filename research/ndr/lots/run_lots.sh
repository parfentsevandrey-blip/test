#!/usr/bin/env bash
# Daily lot snapshot (resumable): collect, then save to the repo. Started by resume_scan.sh / by hand.
cd "$(dirname "$0")"
python3 -I collector.py run >> run.out 2>&1
python3 -I model.py > /dev/null 2>&1; python3 -I c2.py > /dev/null 2>&1; python3 -I c3.py > /dev/null 2>&1; python3 -I paper.py >> run_report.out 2>&1   # signals С2, С3 and the П6 journal
python3 -I registry.py >> run_report.out 2>&1; python3 -I qa.py >> run_report.out 2>&1   # registries and the double check
python3 -I unify.py > /dev/null 2>&1; python3 -I run_report.py >> run_report.out 2>&1   # mandatory run log (RUNLOG.md)
python3 -I ../ndr_workflow/sync_repo.py "Lots snapshot $(TZ=Europe/Moscow date +%F): $(python3 -I collector.py status)" >> run.out 2>&1
