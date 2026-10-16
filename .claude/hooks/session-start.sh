#!/bin/bash
# Every container start/resume: continue the наш.дом.рф MKAD scan from where it stopped.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi
timeout 900 "$CLAUDE_PROJECT_DIR/research/ndr/resume_scan.sh" || true
exit 0
