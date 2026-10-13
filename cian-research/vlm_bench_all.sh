#!/bin/bash
# Последовательный прогон всех кандидатов на эталоне; лог — market-data/gold/bench.log
cd "$(dirname "$0")"
M=${VLM_MODELS:-/tmp/claude-0/-home-user-test/91795489-9a81-5d0c-ab47-d3353bb9d504/scratchpad/models}
for key in ${VLM_KEYS:-qwen3vl-4b gemma4-e4b qwen35-4b qwen3vl-2b minicpmv46 gemma4-e2b qwen3vl-8b qwen25vl-3b}; do
  echo "=== $key $(date +%T)"
  python3 -u vlm_eval.py "$key" --max-tokens 96 2>&1 | grep --line-buffered -v -E "Warning|warn|Fetching|Download|slow"
  python3 vlm_eval.py report
  rm -rf "$M/$key"
done
echo "=== DONE $(date +%T)"
