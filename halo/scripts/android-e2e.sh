#!/usr/bin/env bash
# End-to-end test on an Android emulator: the phone joins a desktop node on the host.
#
#   1. Install the debug APK, allow its VPN without the consent dialog.
#   2. The desktop node on the host adds the phone and comes up.
#   3. The phone adds the desktop at 10.0.2.2 (the host, seen from the emulator)
#      and turns the network on.
#   4. Ping both ways through the tunnel.
#
# Needs a booted emulator (adb), root on the host for the TUN device, a built
# `halo` and the debug APK. Usage: scripts/android-e2e.sh
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HALO=${HALO:-$ROOT/target/release/halo}
APK=${APK:-$ROOT/android/app/build/outputs/apk/debug/app-debug.apk}
PKG=dev.halo.app
WORK=$(mktemp -d)

log() { printf '\n== %s\n' "$*"; }
fail() {
    printf 'FAIL: %s\n' "$*" >&2
    echo "--- phone" >&2
    adb logcat -d -s halo halo-test AndroidRuntime | tail -60 >&2 || true
    echo "--- desktop" >&2
    tail -40 "$WORK/desktop.log" >&2 || true
    exit 1
}
cleanup() {
    sudo pkill -INT -f "$WORK/desk" 2>/dev/null || true
}
trap cleanup EXIT

phone() {
    local cmd=$1
    shift
    adb shell am start -W -n "$PKG/.TestActivity" --es cmd "$cmd" "$@" >/dev/null
}

wait_log() {
    local pattern=$1 deadline=$((SECONDS + ${2:-30}))
    until adb logcat -d -s halo-test | grep -q "$pattern"; do
        ((SECONDS < deadline)) || fail "no '$pattern' from the phone"
        sleep 1
    done
}

log "install the app"
adb install -r "$APK" >/dev/null
adb shell appops set "$PKG" ACTIVATE_VPN allow
adb logcat -c

log "the phone's identity"
phone info
wait_log HALO_INFO
info=$(adb logcat -d -s halo-test | grep -o 'HALO_INFO id=[0-9a-f]* ip=[0-9.]*' | tail -1)
PHONE_ID=$(sed 's/.*id=\([0-9a-f]*\).*/\1/' <<<"$info")
PHONE_IP=$(sed 's/.*ip=\([0-9.]*\).*/\1/' <<<"$info")
echo "phone: $PHONE_IP"

log "the desktop node on the host"
desk() { "$HALO" --state-dir "$WORK/desk" "$@"; }
desk add "$PHONE_ID" --name phone
DESK_ID=$(desk id | awk '/^id:/ {print $2}')
DESK_IP=$(desk id | awk '/^ip:/ {print $2}')
sudo "$HALO" --state-dir "$WORK/desk" up --port 7777 --stats 5 >"$WORK/desktop.log" 2>&1 &
echo "desktop: $DESK_IP"

log "the phone joins"
phone add --es id "$DESK_ID" --es name desktop --es addr 10.0.2.2:7777
wait_log HALO_ADDED
phone connect
wait_log HALO_CONNECTING

log "desktop -> phone"
deadline=$((SECONDS + 90))
until ping -c1 -W1 "$PHONE_IP" >/dev/null 2>&1; do
    ((SECONDS < deadline)) || fail "the desktop cannot reach the phone over the tunnel"
done
ping -c5 -i0.2 "$PHONE_IP" | tail -2

log "phone -> desktop"
adb shell ping -c5 -i0.2 "$DESK_IP" | tail -2 || fail "the phone cannot reach the desktop over the tunnel"

log "the phone's view"
phone status
sleep 1
adb logcat -d -s halo-test | grep HALO_STATUS | tail -1
adb logcat -d -s halo-test | grep HALO_STATUS | tail -1 | grep -q "$DESK_IP=" || fail "the phone does not list the desktop"

log "all good"
