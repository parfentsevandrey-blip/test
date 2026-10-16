#!/bin/bash
# Every container start/resume: continue the наш.дом.рф scan and today's lot snapshot from where they stopped.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi
timeout 900 "$CLAUDE_PROJECT_DIR/research/ndr/resume_scan.sh" || true
timeout 300 "$CLAUDE_PROJECT_DIR/research/lots_resume.sh" || true
exit 0
