#!/usr/bin/env bash
# [С1] Daily developer price-list snapshot (resumable): four groups in parallel (each site keeps its own pace), then save.
cd "$(dirname "$0")"
for g in lists lsr visitor donstroy fsk; do
  pgrep -f "^python3 -I devcat[.]py run $g\$" >/dev/null || python3 -I devcat.py run "$g" >> devcat.out 2>&1 &
done
wait
python3 -I ../ndr_workflow/sync_repo.py "Devcat snapshot $(TZ=Europe/Moscow date +%F): $(python3 -I devcat.py status)" >> devcat.out 2>&1
