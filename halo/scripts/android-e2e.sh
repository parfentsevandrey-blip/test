#!/usr/bin/env bash
# End-to-end test on an Android emulator: the phone joins two desktop nodes.
#
#   1. Install the debug APK.
#   2. Desktop B, in its own network namespace, is a member of desktop A's network.
#   3. Pairing: A shows a code (`halo pair`), the phone joins it, both confirm
#      the emoji and add each other. The phone learns A's addresses from the
#      code: no mDNS in the emulator, no manual ids.
#   4. A and B come up; the phone turns the network on, tapping OK in the system
#      VPN consent dialog. Ping both ways through the tunnel.
#   5. The phone never paired with B: it learns about B from A's member journal,
#      restarts its tunnel with a route to B and reaches it, and B lets it in.
#
# Needs a booted emulator (adb), root on the host for the TUN devices and the
# namespace, a built `halo` and the debug APK. Usage: scripts/android-e2e.sh
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
    echo "--- desktop pairing" >&2
    tail -20 "$WORK/pair.log" >&2 || true
    echo "--- desktop A" >&2
    tail -40 "$WORK/desktop.log" >&2 || true
    echo "--- desktop B" >&2
    tail -30 "$WORK/b.log" >&2 || true
    exit 1
}
cleanup() {
    sudo pkill -INT -f "$WORK/desk" 2>/dev/null || true
    sudo ip netns del halo-b 2>/dev/null || true
    sudo ip link del halo-vb0 2>/dev/null || true
}
trap cleanup EXIT

phone() {
    local cmd=$1
    shift
    adb shell am start -W -n "$PKG/.TestActivity" --es cmd "$cmd" "$@" >/dev/null
}

# Taps the button with the given text, if it is on screen.
tap() {
    local bounds
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 0
    bounds=$(adb shell cat /sdcard/ui.xml | sed 's/>/>\n/g' | grep -iE "text=\"$1\"" |
        grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1) || true
    [[ -n $bounds ]] || return 0
    read -r x1 y1 x2 y2 <<<"$(sed 's/[^0-9]/ /g' <<<"$bounds")"
    adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
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
adb logcat -c

log "the phone's identity"
phone info
wait_log HALO_INFO
info=$(adb logcat -d -s halo-test | grep -o 'HALO_INFO id=[0-9a-f]* ip=[0-9.]*' | tail -1)
PHONE_ID=$(sed 's/.*id=\([0-9a-f]*\).*/\1/' <<<"$info")
PHONE_IP=$(sed 's/.*ip=\([0-9.]*\).*/\1/' <<<"$info")
echo "phone: $PHONE_IP ($PHONE_ID)"

log "desktop B: a member of A's network, in its own network namespace"
desk() { "$HALO" --state-dir "$WORK/desk" "$@"; }
deskb() { sudo "$HALO" --state-dir "$WORK/deskb" "$@"; }
DESK_ID=$(desk id | awk '/^id:/ {print $2}')
DESK_IP=$(desk id | awk '/^ip:/ {print $2}')
B_ID=$(deskb id | awk '/^id:/ {print $2}')
B_IP=$(deskb id | awk '/^ip:/ {print $2}')
sudo ip netns add halo-b
sudo ip link add halo-vb0 type veth peer name halo-vb1
sudo ip link set halo-vb1 netns halo-b
sudo ip addr add 10.201.0.1/24 dev halo-vb0
sudo ip link set halo-vb0 up
sudo ip -n halo-b addr add 10.201.0.2/24 dev halo-vb1
sudo ip -n halo-b link set halo-vb1 up
sudo ip -n halo-b link set lo up
desk add "$B_ID" --name desk-b --addr 10.201.0.2:7777 >/dev/null
deskb add "$DESK_ID" --name desk-a --addr 10.201.0.1:7777 >/dev/null
echo "B: $B_IP ($B_ID)"

log "pairing: A shows a code, the phone joins"
echo y | desk pair --port 7777 >"$WORK/pair.log" 2>&1 &
pair_pid=$!
deadline=$((SECONDS + 30))
until grep -q 'halo pair --join' "$WORK/pair.log"; do
    ((SECONDS < deadline)) || fail "the desktop shows no pairing code"
    sleep 0.5
done
CODE=$(grep -o 'halo pair --join [A-Z0-9/:.+]*' "$WORK/pair.log" | awk '{print $4}')
phone pair --es code "$CODE"
wait_log HALO_PAIRED 90
adb logcat -d -s halo-test | grep -o 'HALO_PAIR_EMOJI.*' | tail -1
adb logcat -d -s halo-test | grep -o 'HALO_PAIRED.*' | tail -1
wait "$pair_pid" || fail "the desktop did not pair"
grep -E 'wants to pair|Paired' -A3 "$WORK/pair.log" | grep -v '^--' | sed -n '1,8p'
desk members | grep -q "$PHONE_IP" || fail "the desktop did not add the phone"

log "the desktop nodes come up"
sudo "$HALO" --state-dir "$WORK/desk" up --port 7777 --stats 5 >"$WORK/desktop.log" 2>&1 &
sudo ip netns exec halo-b "$HALO" --state-dir "$WORK/deskb" up --port 7777 >"$WORK/b.log" 2>&1 &
echo "A: $DESK_IP ($DESK_ID)"

log "the phone turns the network on"
phone connect
deadline=$((SECONDS + 60))
until adb logcat -d -s halo-test | grep -q HALO_CONNECTING; do
    ((SECONDS < deadline)) || fail "the VPN consent dialog did not go through"
    tap OK
    sleep 1
done

log "desktop -> phone"
deadline=$((SECONDS + 90))
until ping -c1 -W1 "$PHONE_IP" >/dev/null 2>&1; do
    ((SECONDS < deadline)) || fail "the desktop cannot reach the phone over the tunnel"
done
ping -c5 -i0.2 "$PHONE_IP" | tail -2

log "phone -> desktop"
adb shell ping -c5 -i0.2 "$DESK_IP" | tail -2 || fail "the phone cannot reach the desktop over the tunnel"

log "the phone reaches B, which it never paired with"
deadline=$((SECONDS + 120))
until adb shell ping -c1 -W1 "$B_IP" >/dev/null 2>&1; do
    ((SECONDS < deadline)) || fail "the phone did not learn about B from A's journal"
done
adb shell ping -c5 -i0.2 "$B_IP" | tail -2
sudo ip netns exec halo-b ping -c5 -i0.2 "$PHONE_IP" | tail -2 || fail "B does not let the phone in"

log "the phone's view"
phone status
sleep 1
adb logcat -d -s halo-test | grep HALO_STATUS | tail -1
status=$(adb logcat -d -s halo-test | grep HALO_STATUS | tail -1)
grep -q "$DESK_IP=" <<<"$status" || fail "the phone does not list A"
grep -q "$B_IP=" <<<"$status" || fail "the phone does not list B"
adb logcat -d -s halo | grep -m1 'the members changed' || true

log "all good"
