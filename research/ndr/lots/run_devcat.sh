#!/usr/bin/env bash
# [С1] Daily developer price-list snapshot (resumable): collect, then save to the repo. Started by lots_resume.sh / by hand.
cd "$(dirname "$0")"
python3 -I devcat.py run >> devcat.out 2>&1
python3 -I ../ndr_workflow/sync_repo.py "Devcat snapshot $(TZ=Europe/Moscow date +%F): $(python3 -I devcat.py status)" >> devcat.out 2>&1
