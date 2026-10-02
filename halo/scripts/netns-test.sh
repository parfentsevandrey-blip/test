#!/usr/bin/env bash
# End-to-end test of the tunnel: three nodes in network namespaces on one virtual switch.
#
#   a, b  members of each other's network: ping both ways, full-size packets,
#         iperf3 throughput, recovery after b restarts and after b crashes.
#   c     has a in its member list, but a does not have c: must be rejected.
#
# Suites:
#   1500     members added with their addresses, 1500-byte links.
#   1300     the same on 1300-byte links: a full tunnel packet no longer fits into
#            one QUIC datagram and must be fragmented.
#   lan      members added by id only: nodes find each other over mDNS.
#   journal  b and c are paired with a only and find each other through a's
#            member journal; then a removes c while everything runs, and the
#            removal reaches b.
#   outside  a and b at home behind a router that maps ports over NAT-PMP; c
#            meets them there, then moves to a mobile network behind another
#            NAT and still reaches both, at the public addresses they announced.
#   punch    b in a café and c on a mobile network, both behind NATs without
#            port mapping, reach a at home. a tells each where it sees them, so
#            they can dial each other through their NATs; where the NATs do not
#            let that through (these Linux ones do not), a passes their packets
#            on.
#
# Needs root, iproute2, iputils-ping, iperf3, iptables and python3.
# Usage: sudo scripts/netns-test.sh [suite...]
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
HALO=${HALO:-$ROOT/target/debug/halo}
PORT=7777
WORK=$(mktemp -d)

declare -A IP ID OVERLAY

log() { printf '\n== %s\n' "$*"; }
fail() {
    printf 'FAIL: %s\n' "$*" >&2
    for ns in a b c hr; do
        if [[ -f "$WORK/$ns.log" ]]; then
            echo "--- $ns.log" >&2
            grep -v ' stats ' "$WORK/$ns.log" | tail -25 >&2
            grep ' stats ' "$WORK/$ns.log" | tail -1 >&2
        fi
    done
    exit 1
}
in_ns() { local ns=$1; shift; ip netns exec "halo-$ns" "$@"; }
halo() { local ns=$1; shift; "$HALO" --state-dir "$WORK/$ns" "$@"; }

teardown() {
    pkill -INT -f "$WORK/" 2>/dev/null || true
    if [[ -n ${ROUTER:-} ]]; then
        kill "$ROUTER" 2>/dev/null || true
        ROUTER=
    fi
    sleep 0.5
    pkill -KILL -f "$WORK/" 2>/dev/null || true
    for ns in a b c sw hr mr cr net; do ip netns del "halo-$ns" 2>/dev/null || true; done
}
trap 'teardown; [[ -n ${KEEP:-} ]] && cp -r "$WORK" "$KEEP"; rm -rf "$WORK"' EXIT

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
        # Real machines have a default route; mDNS needs a route for multicast.
        ip -n "halo-$ns" route add 224.0.0.0/4 dev "${ns}0"
        # A fresh device for every suite: key, journal and presences.
        rm -rf "${WORK:?}/$ns"
        ID[$ns]=$(halo "$ns" id | awk '/^id:/ {print $2}')
        OVERLAY[$ns]=$(halo "$ns" id | awk '/^ip:/ {print $2}')
        : >"$WORK/$ns.log"
        i=$((i + 1))
    done
}

# add <ns> <peer> [with-address]
add() {
    local ns=$1 peer=$2 addr=()
    if [[ ${3:-} == with-address ]]; then
        addr=(--addr "${IP[$peer]}:$PORT")
    fi
    halo "$ns" add "${ID[$peer]}" --name "dev-$peer" "${addr[@]}" >/dev/null
}

start() {
    in_ns "$1" "$HALO" --state-dir "$WORK/$1" up --port "$PORT" --stats 1 >>"$WORK/$1.log" 2>&1 &
}

stop() { pkill "-$2" -f "$WORK/$1 up" || true; }

wait_ping() {
    local from=$1 to=$2 deadline=$((SECONDS + ${3:-20}))
    until in_ns "$from" ping -c1 -W1 "${OVERLAY[$to]}" >/dev/null 2>&1; do
        ((SECONDS < deadline)) || fail "$from cannot reach $to over the tunnel"
    done
}

last_stat() { grep -o "$2=[0-9]*" "$WORK/$1.log" | tail -1 | cut -d= -f2; }

suite() {
    local name=$1 mtu=$2 with=${3:-}
    log "suite $name: links with MTU $mtu, members added ${with:-by id only}"
    setup "$mtu"
    add a b $with
    add b a $with
    add c a $with
    start a
    start b
    start c
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
    start b
    wait_ping a b
    echo "reconnected: ok"

    log "b crashes and comes back"
    stop b KILL
    sleep 1
    start b
    wait_ping a b
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

unreachable() {
    local from=$1 to=$2
    ! in_ns "$from" ping -c3 -W1 -q "${OVERLAY[$to]}" >/dev/null 2>&1
}

journal_suite() {
    log "suite journal: b and c are paired with a only"
    setup 1500
    add a b with-address
    add b a with-address
    add a c with-address
    add c a with-address
    start a
    start b
    start c
    wait_ping b c 30
    wait_ping c b 30
    echo "b and c found each other through a: ok"
    halo b members

    log "a removes c while everything runs"
    halo a remove dev-c
    local deadline=$((SECONDS + 20))
    until grep -q "no longer a member.*${OVERLAY[c]}" "$WORK/b.log"; do
        ((SECONDS < deadline)) || fail "the removal did not reach b"
        sleep 0.5
    done
    echo "b dropped c: ok"
    unreachable c a || fail "c still reaches a"
    unreachable c b || fail "c still reaches b"
    unreachable b c || fail "b still reaches c"
    wait_ping b a
    echo "c is out, a and b still talk: ok"
    halo b members
    teardown
}

# outside: a home network behind router hr, which masquerades and maps ports
# over NAT-PMP, and a mobile network behind router mr, which only masquerades.
# The routers meet on 203.0.113.0/24, the internet of this test.
outside_setup() {
    local ns i=2
    for ns in a b c hr mr; do
        ip netns add "halo-$ns"
        ip -n "halo-$ns" link set lo up
    done
    ip -n halo-hr link add br0 type bridge
    ip -n halo-hr addr add 10.10.0.1/24 dev br0
    ip -n halo-hr link set br0 up
    for ns in a b c; do
        ip link add "${ns}0" type veth peer name "${ns}1"
        ip link set "${ns}0" netns "halo-$ns"
        ip link set "${ns}1" netns halo-hr
        ip -n halo-hr link set "${ns}1" master br0 up
        IP[$ns]=10.10.0.$i
        ip -n "halo-$ns" addr add "${IP[$ns]}/24" dev "${ns}0"
        ip -n "halo-$ns" link set "${ns}0" up
        ip -n "halo-$ns" route add default via 10.10.0.1
        rm -rf "${WORK:?}/$ns"
        ID[$ns]=$(halo "$ns" id | awk '/^id:/ {print $2}')
        OVERLAY[$ns]=$(halo "$ns" id | awk '/^ip:/ {print $2}')
        : >"$WORK/$ns.log"
        i=$((i + 1))
    done
    # c's mobile link stays down while c is at home.
    ip link add cm0 type veth peer name cm1
    ip link set cm0 netns halo-c
    ip link set cm1 netns halo-mr
    ip -n halo-mr addr add 10.20.0.1/24 dev cm1
    ip -n halo-mr link set cm1 up
    ip link add hw type veth peer name mw
    ip link set hw netns halo-hr
    ip link set mw netns halo-mr
    ip -n halo-hr addr add 203.0.113.1/24 dev hw
    ip -n halo-hr link set hw up
    ip -n halo-mr addr add 203.0.113.2/24 dev mw
    ip -n halo-mr link set mw up
    for ns in hr mr; do
        in_ns "$ns" sysctl -qw net.ipv4.ip_forward=1
    done
    in_ns hr iptables -t nat -A POSTROUTING -o hw -j MASQUERADE
    in_ns mr iptables -t nat -A POSTROUTING -o mw -j MASQUERADE
    # Not through in_ns: $! has to be the router itself, for teardown to stop it.
    ip netns exec halo-hr python3 "$ROOT/scripts/natpmp-router.py" 10.10.0.1 203.0.113.1 hw \
        >"$WORK/hr.log" 2>&1 &
    ROUTER=$!
    wait_router
}

# The nodes ask the router at start: it has to listen by then.
wait_router() {
    local deadline=$((SECONDS + 10))
    until grep -q 'NAT-PMP on' "$WORK/hr.log" 2>/dev/null; do
        ((SECONDS < deadline)) || fail "the NAT-PMP router did not start"
        sleep 0.1
    done
}

# Waits until <ns> lists <addr> among the addresses of its members.
wait_member_addr() {
    local ns=$1 addr=$2 deadline=$((SECONDS + 60))
    until halo "$ns" members | grep -q "$addr"; do
        ((SECONDS < deadline)) || fail "$ns never learned the address $addr"
        sleep 1
    done
}

outside_suite() {
    log "suite outside: c meets a and b at home, then leaves for a mobile network"
    outside_setup
    add a b with-address
    add b a with-address
    add a c with-address
    add c a with-address
    start a
    start b
    start c
    wait_ping c a 30
    wait_ping c b 30
    echo "at home: ok"

    log "the home router maps a port to every device"
    local deadline=$((SECONDS + 30))
    until grep -qe "-> ${IP[a]}:$PORT" "$WORK/hr.log" && grep -qe "-> ${IP[b]}:$PORT" "$WORK/hr.log"; do
        ((SECONDS < deadline)) || fail "no port mappings for a and b"
        sleep 0.5
    done
    grep mapped "$WORK/hr.log"
    local a_public b_public
    a_public=$(grep -o "203.0.113.1:[0-9]* -> ${IP[a]}:$PORT" "$WORK/hr.log" | head -1 | cut -d' ' -f1)
    b_public=$(grep -o "203.0.113.1:[0-9]* -> ${IP[b]}:$PORT" "$WORK/hr.log" | head -1 | cut -d' ' -f1)
    wait_member_addr c "$a_public"
    wait_member_addr c "$b_public"
    halo a id | grep public
    echo "c knows where a and b are from outside: ok"

    log "c leaves home for the mobile network"
    ip -n halo-c link set c0 down
    ip -n halo-c addr add 10.20.0.2/24 dev cm0
    ip -n halo-c link set cm0 up
    ip -n halo-c route add default via 10.20.0.1
    local moved=$SECONDS
    wait_ping c a 90
    wait_ping c b 90
    echo "back in touch after $((SECONDS - moved)) s"
    in_ns c ping -c5 -i0.2 -q "${OVERLAY[a]}" | tail -1
    wait_ping a c 30
    echo "c reaches a and b from the mobile network: ok"
    teardown
}

# punch: three networks behind their own routers, which meet on 203.0.113.0/24:
# home (hr, NAT with NAT-PMP, a), café (cr, NAT only, b) and mobile (mr, NAT
# only, c). Each router masquerades the way home routers do.
punch_setup() {
    local ns
    for ns in a b c hr cr mr net; do
        ip netns add "halo-$ns"
        ip -n "halo-$ns" link set lo up
    done
    ip -n halo-net link add br0 type bridge
    ip -n halo-net link set br0 up
    local router lan wan device i=1
    for router in hr:10.10.0:a cr:10.30.0:b mr:10.20.0:c; do
        IFS=: read -r ns lan device <<<"$router"
        wan=203.0.113.$i
        ip link add "${ns}w" type veth peer name "${ns}n"
        ip link set "${ns}w" netns "halo-$ns"
        ip link set "${ns}n" netns halo-net
        ip -n halo-net link set "${ns}n" master br0 up
        ip -n "halo-$ns" addr add "$wan/24" dev "${ns}w"
        ip -n "halo-$ns" link set "${ns}w" up
        ip link add "${device}0" type veth peer name "${device}1"
        ip link set "${device}0" netns "halo-$device"
        ip link set "${device}1" netns "halo-$ns"
        ip -n "halo-$ns" addr add "$lan.1/24" dev "${device}1"
        ip -n "halo-$ns" link set "${device}1" up
        IP[$device]=$lan.2
        ip -n "halo-$device" addr add "${IP[$device]}/24" dev "${device}0"
        ip -n "halo-$device" link set "${device}0" up
        ip -n "halo-$device" route add default via "$lan.1"
        in_ns "$ns" sysctl -qw net.ipv4.ip_forward=1
        in_ns "$ns" iptables -t nat -A POSTROUTING -o "${ns}w" -j MASQUERADE
        rm -rf "${WORK:?}/$device"
        ID[$device]=$(halo "$device" id | awk '/^id:/ {print $2}')
        OVERLAY[$device]=$(halo "$device" id | awk '/^ip:/ {print $2}')
        : >"$WORK/$device.log"
        i=$((i + 1))
    done
    ip netns exec halo-hr python3 "$ROOT/scripts/natpmp-router.py" 10.10.0.1 203.0.113.1 hrw \
        >"$WORK/hr.log" 2>&1 &
    ROUTER=$!
    wait_router
}

punch_suite() {
    log "suite punch: b in a café and c on a mobile network, both behind NATs"
    punch_setup
    # Paired with a at home earlier: they know where it is from outside.
    add a b
    add a c
    halo b add "${ID[a]}" --name dev-a --addr "203.0.113.1:$PORT" >/dev/null
    halo c add "${ID[a]}" --name dev-a --addr "203.0.113.1:$PORT" >/dev/null
    start a
    local deadline=$((SECONDS + 30))
    until grep -qe "-> ${IP[a]}:$PORT" "$WORK/hr.log"; do
        ((SECONDS < deadline)) || fail "no port mapping for a"
        sleep 0.5
    done
    start b
    start c
    wait_ping b a 30
    wait_ping c a 30
    echo "b and c reach a at home: ok"

    log "b and c reach each other: directly through their NATs, or through a"
    wait_ping b c 30
    wait_ping c b 30
    in_ns b ping -c5 -i0.2 -q "${OVERLAY[c]}" | tail -1
    if grep -qE "connected peer=${ID[c]:0:10}.*path=Ip\\(203\\.0\\.113\\.3:" "$WORK/b.log"; then
        echo "b and c talk directly through their NATs: ok"
    else
        sleep 1.5
        local relayed
        relayed=$(last_stat a relayed)
        ((relayed > 0)) || fail "b and c talk, but neither directly nor through a"
        echo "b and c talk through a, $relayed packets passed on: ok"
    fi
    teardown
}

[[ -x "$HALO" ]] || fail "build first: cargo build (expected $HALO)"
suites=("$@")
((${#suites[@]})) || suites=(1500 1300 lan journal outside punch)
for name in "${suites[@]}"; do
    case $name in
        1500) suite 1500 1500 with-address ;;
        1300) suite 1300 1300 with-address ;;
        lan) suite lan 1500 ;;
        journal) journal_suite ;;
        outside) outside_suite ;;
        punch) punch_suite ;;
        *) fail "unknown suite $name" ;;
    esac
done
log "all good"
