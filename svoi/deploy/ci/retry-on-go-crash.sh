#!/usr/bin/env bash
# Runs the command given as arguments. On the Windows machines of CI (Windows Server 2025) a program written in
# Go now and then dies inside the Go runtime itself: an access violation in the garbage collector or at a wild
# address, with no call of ours in it (golang/go#76614, #77955). A run that failed *that way* is repeated, at most
# RETRIES times (default 2), and says so loudly. A run that failed any other way - a failed check, a panic in our
# own code, any failure on Linux or macOS - is never repeated.
set -u
retries=${RETRIES:-2}
crash='^Exception 0x[0-9a-f]+ 0x[0-9a-f]+|fatal error: unexpected signal during runtime execution|runtime: g [0-9]+[^:]*: unknown pc|traceback did not unwind completely'
out=$(mktemp)
attempt=0
while :; do
  "$@" 2>&1 | tee "$out"
  status=${PIPESTATUS[0]}
  [ "$status" = 0 ] && exit 0
  if [ "${RUNNER_OS:-}" = Windows ] && [ "$attempt" -lt "$retries" ] && grep -qE "$crash" "$out"; then
    attempt=$((attempt + 1))
    echo "::warning::the Go runtime crashed on this Windows machine (golang/go#76614, #77955); the command is run again ($attempt of $retries): $*"
    continue
  fi
  exit "$status"
done
