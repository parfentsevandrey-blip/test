#!/usr/bin/env bash
# Runs ndr_workflow.py with a consistent Russian desktop environment on a virtual display.
# Usage: ./run.sh preflight | run | loop | report
set -euo pipefail
cd "$(dirname "$0")"
locale -a | grep -qi '^ru_RU.utf8$' || localedef -i ru_RU -f UTF-8 ru_RU.UTF-8
export LANG=ru_RU.UTF-8 LANGUAGE=ru_RU:ru LC_ALL=ru_RU.UTF-8 TZ=Europe/Moscow
case "${1:-run}" in report|seed) exec python3 -I ndr_workflow.py "$@";; esac
exec xvfb-run -a -s "-screen 0 1920x1080x24" python3 -I ndr_workflow.py "${1:-run}"
