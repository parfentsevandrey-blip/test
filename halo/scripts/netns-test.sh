#!/usr/bin/env bash
# End-to-end test of the tunnel: three nodes in network namespaces on one virtual switch.
#
#   a, b  members of each other's network: ping both ways, full-size packets,
#         iperf3 throughput, recovery after b restarts and after b crashes.
#   c     knows a's address and key but is not a member: must be rejected.
#
# The suite runs twice: on 1500-byte links, and on 1300-byte links where a full
# tunnel packet no longer fits into one QUIC datagram and must be fragmented.
#
# Needs root, iproute2, iputils-ping and iperf3. Usage: sudo scripts/netns-test.sh
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HALO=${HALO:-$ROOT/target/debug/halo}
PORT=7777
WORK=$(mktemp -d)

declare -A IP ID OVERLAY

log() { printf '\n== %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
in_ns() { local ns=$1; shift; ip netns exec "halo-$ns" "$@"; }

teardown() {
    for ns in a b c; do
        in_ns "$ns" pkill -INT -f "$WORK" 2>/dev/null || true
    done
    sleep 0.5
    for ns in a b c sw; do ip netns del "halo-$ns" 2>/dev/null || true; done
}
trap 'teardown; rm -rf "$WORK"' EXIT

setup() {
    local mtu=$1 i=1
    ip netns add halo-sw
    ip -n halo-sw link add br0 type bridge
    ip -n halo-sw link set br0 up
    for ns in a b c; do
        ip netns add "halo-$ns"
        ip -n "halo-$ns" link set lo up
        ip link add "${ns}0" mtu "$mtu" type veth peer name "${ns}1" mtu "$mtu"
        ip link set "${ns}0" netns "halo-$ns"
        ip link set "${ns}1" netns halo-sw
        ip -n halo-sw link set "${ns}1" master br0 up
        IP[$ns]=10.99.0.$i
        ip -n "halo-$ns" addr add "${IP[$ns]}/24" dev "${ns}0"
        ip -n "halo-$ns" link set "${ns}0" up
        ID[$ns]=$("$HALO" --state-dir "$WORK/$ns" id | awk '/^id:/ {print $2}')
        OVERLAY[$ns]=$("$HALO" --state-dir "$WORK/$ns" id | awk '/^ip:/ {print $2}')
        i=$((i + 1))
    done
}

start() {
    local ns=$1 peer=$2
    in_ns "$ns" "$HALO" --state-dir "$WORK/$ns" up --port "$PORT" --stats 1 \
        --peer "${ID[$peer]}@${IP[$peer]}:$PORT" >>"$WORK/$ns.log" 2>&1 &
}

stop() { in_ns "$1" pkill "-$2" -f "$WORK/$1 " || true; }

wait_ping() {
    local from=$1 to=$2 deadline=$((SECONDS + ${3:-20}))
    until in_ns "$from" ping -c1 -W1 "${OVERLAY[$to]}" >/dev/null 2>&1; do
        ((SECONDS < deadline)) || fail "$from cannot reach $to over the tunnel"
    done
}

last_stat() { grep -o "$2=[0-9]*" "$WORK/$1.log" | tail -1 | cut -d= -f2; }

suite() {
    local mtu=$1
    log "links with MTU $mtu"
    setup "$mtu"
    : >"$WORK/a.log"; : >"$WORK/b.log"; : >"$WORK/c.log"
    start a b
    start b a
    start c a
    echo "a=${OVERLAY[a]} b=${OVERLAY[b]} c=${OVERLAY[c]}"

    wait_ping a b
    in_ns a ping -c5 -i0.2 -q "${OVERLAY[b]}" | tail -2
    in_ns b ping -c5 -i0.2 -q "${OVERLAY[a]}" | tail -1

    log "full-size tunnel packets (1280 bytes, don't fragment)"
    in_ns a ping -c3 -i0.2 -q -s 1252 -M do "${OVERLAY[b]}" | tail -1

    log "iperf3 a -> b over the tunnel"
    in_ns b iperf3 -s -1 -B "${OVERLAY[b]}" >/dev/null 2>&1 &
    local server=$!
    sleep 0.5
    in_ns a iperf3 -c "${OVERLAY[b]}" -t 5 | grep -E 'sender|receiver'
    wait "$server"

    log "a non-member is rejected"
    if in_ns c ping -c3 -W1 -q "${OVERLAY[a]}" >/dev/null 2>&1; then
        fail "c reached a without being a member"
    fi
    grep -q 'rejected a device that is not a member' "$WORK/a.log" || fail "a did not log the rejection"
    echo "c rejected: ok"

    log "b restarts"
    stop b INT
    sleep 1
    start b a
    wait_ping a b
    echo "reconnected: ok"

    log "b crashes and comes back"
    stop b KILL
    sleep 1
    start b a
    wait_ping a b 15
    echo "reconnected: ok"

    sleep 1.5
    local fragmented spoofed looped
    fragmented=$(last_stat a fragmented)
    spoofed=$(last_stat a dropped_spoofed)
    looped=$(last_stat a dropped_loop)
    echo "a: fragmented=$fragmented dropped_spoofed=$spoofed dropped_loop=$looped"
    [[ "$spoofed" == 0 ]] || fail "a dropped spoofed packets from a member"
    if ((mtu < 1400)); then
        ((fragmented > 1000)) || fail "expected fragmentation on a narrow path"
    fi
    teardown
}

[[ -x "$HALO" ]] || fail "build first: cargo build (expected $HALO)"
suite 1500
suite 1300
log "all good"
