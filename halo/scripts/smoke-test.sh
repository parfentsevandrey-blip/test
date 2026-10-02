#!/usr/bin/env bash
# Smoke test of the shipped program on the machine it runs on: installs Halo as
# a system service, reaches a member device through the tunnel, updates the
# service in place, uninstalls. Linux (systemd), macOS (launchd) and Windows
# (Git Bash, as administrator). It changes system settings: for CI runners.
#
#   scripts/smoke-test.sh <halo> <echo-peer>
#
# The member is echo-peer (crates/halo-core/examples), which answers pings
# without a TUN device of its own: the system's own ping goes through the TUN
# device, the node and a QUIC connection, and back.

set -euo pipefail

HALO=$1
ECHO=$2
WORK=$(mktemp -d)
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) os=windows ;;
  Darwin) os=macos ;;
  *) os=linux ;;
esac
as_root=()
if [[ $os != windows && $(id -u) != 0 ]]; then
  as_root=(sudo)
fi

halo() {
  "${as_root[@]}" "$HALO" "$@"
}

# A Windows folder such as ProgramData, as a path for this shell.
win_dir() {
  cygpath -u "$(cmd //c "echo %$1%" | tr -d '\r')"
}

fail() {
  echo "FAIL: $*" >&2
  halo status >&2 || true
  echo "--- node log" >&2
  case $os in
    linux) sudo journalctl -u halo --no-pager -n 60 >&2 || true ;;
    macos) sudo tail -n 60 "/Library/Application Support/Halo/halo.log" >&2 || true ;;
    windows) tail -n 60 "$(win_dir ProgramData)/Halo/halo.log" >&2 || true ;;
  esac
  echo "--- echo-peer" >&2
  cat "$WORK/echo.err" >&2 || true
  exit 1
}

cleanup() {
  if [[ -n ${ECHO_PID:-} ]]; then
    kill "$ECHO_PID" 2>/dev/null || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

# Waits up to $1 seconds for `halo status` to match $2. The output is read
# whole first: `grep -q` stops reading at a match, and the pipe would fail.
wait_status() {
  local seconds=$1 pattern=$2 out
  for _ in $(seq "$seconds"); do
    out=$(halo status 2>/dev/null || true)
    if grep -qE "$pattern" <<<"$out"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

"$ECHO" "$WORK/echo" 7778 >"$WORK/echo.out" 2>"$WORK/echo.err" &
ECHO_PID=$!
for _ in $(seq 100); do
  grep -q '^ip ' "$WORK/echo.out" 2>/dev/null && break
  sleep 0.2
done
echo_id=$(awk '/^id /{print $2}' "$WORK/echo.out")
echo_ip=$(awk '/^ip /{print $2}' "$WORK/echo.out")
[[ -n $echo_ip ]] || fail "echo-peer did not start"

halo add "$echo_id" --name echo --addr 127.0.0.1:7778
halo install || fail "halo install"
wait_status 60 'echo +[0-9.]+ +direct' || fail "the service did not connect to the member"
halo status
wait_status 5 'service: +running' || fail "the service is not running"

ping_through() {
  if [[ $os == windows ]]; then
    local out
    out=$(ping -n 3 "$echo_ip" || true)
    echo "$out"
    grep -q 'TTL=' <<<"$out"
  else
    ping -c 3 "$echo_ip"
  fi
}
ping_through || fail "no ping reply through the tunnel"
echo "ping through the tunnel: ok"

# Installing again updates the service in place: it comes back on its own.
halo install || fail "second halo install"
wait_status 60 'echo +[0-9.]+ +direct' || fail "no connection after the update"
ping_through || fail "no ping reply after the update"
echo "update in place: ok"

halo uninstall
wait_status 10 'service: +not installed' || fail "the service is still installed"
wait_status 10 'network: +off' || fail "the network is still on"
if [[ $os == windows ]]; then
  [[ ! -e "$(win_dir ProgramFiles)/Halo" ]] || fail "the program files are still there"
else
  [[ ! -e /usr/local/bin/halo ]] || fail "/usr/local/bin/halo is still there"
fi
echo "smoke test on $os: ok"
