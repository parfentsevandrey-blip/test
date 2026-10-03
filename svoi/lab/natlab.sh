#!/usr/bin/env bash
# natlab.sh — a miniature Internet with real NAT, built from Linux network
# namespaces, for testing svoi's hole punching against the kernel's conntrack.
#
#   svl-anchor (203.0.113.100)   public host: a VPS / home server with an open port
#   svl-rA     NAT router A  wan 203.0.113.1   lan 192.168.1.0/24  -> svl-A  192.168.1.10
#   svl-rB     NAT router B  wan 203.0.113.2   lan 192.168.2.0/24  -> svl-B  192.168.2.10
#   svl-core   an L2 bridge joining the three public-side links
#
# usage (root):  natlab.sh up [natA] [natB] [firewall]
#                   natX     = cone (default) | symmetric
#                   firewall = home (default: like a typical home router, drops unsolicited
#                              inbound packets) | permissive (accepts them: the kernel then
#                              creates conntrack entries that can break port preservation)
#                natlab.sh down
set -euo pipefail

IPT=$(command -v iptables-legacy || command -v iptables)
NAMES="svl-core svl-anchor svl-rA svl-rB svl-A svl-B"

down() {
  for ns in $NAMES; do ip netns del "$ns" 2>/dev/null || true; done
}

link() { # link <nsX> <ifX> <nsY> <ifY>
  ip link add "$2" type veth peer name "$4"
  ip link set "$2" netns "$1"
  ip link set "$4" netns "$3"
}

nat_rule() { # nat_rule <ns> <wan-if> <kind> <firewall>
  local ns=$1 wan=$2 kind=$3 fw=${4:-home}
  ip netns exec "$ns" sysctl -qw net.ipv4.ip_forward=1
  if [ "$fw" = home ]; then
    # what a typical home router does: LAN may talk to the router, from the WAN only
    # replies to what the LAN started are accepted, everything else is dropped
    ip netns exec "$ns" "$IPT" -P INPUT DROP
    ip netns exec "$ns" "$IPT" -A INPUT -i lo -j ACCEPT
    ip netns exec "$ns" "$IPT" -A INPUT ! -i "$wan" -j ACCEPT
    ip netns exec "$ns" "$IPT" -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
    ip netns exec "$ns" "$IPT" -P FORWARD DROP
    ip netns exec "$ns" "$IPT" -A FORWARD ! -i "$wan" -j ACCEPT
    ip netns exec "$ns" "$IPT" -A FORWARD -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  fi
  if [ "$kind" = symmetric ]; then
    # a new random external port for every flow: endpoint-dependent mapping
    ip netns exec "$ns" "$IPT" -t nat -A POSTROUTING -o "$wan" -j MASQUERADE --random
  else
    ip netns exec "$ns" "$IPT" -t nat -A POSTROUTING -o "$wan" -j MASQUERADE
  fi
}

up() {
  local natA=${1:-cone} natB=${2:-cone} fw=${3:-home}
  down
  for ns in $NAMES; do ip netns add "$ns"; ip -n "$ns" link set lo up; done

  ip -n svl-core link add br0 type bridge
  ip -n svl-core link set br0 up

  # public side: anchor, router A and router B hang off the bridge
  link svl-anchor wan0 svl-core pa
  ip -n svl-anchor addr add 203.0.113.100/24 dev wan0
  link svl-rA wanA svl-core pcA
  ip -n svl-rA addr add 203.0.113.1/24 dev wanA
  link svl-rB wanB svl-core pcB
  ip -n svl-rB addr add 203.0.113.2/24 dev wanB
  for p in pa pcA pcB; do ip -n svl-core link set "$p" master br0; ip -n svl-core link set "$p" up; done
  ip -n svl-anchor link set wan0 up
  ip -n svl-rA link set wanA up
  ip -n svl-rB link set wanB up

  # private side
  link svl-rA lanA svl-A ethA
  ip -n svl-rA addr add 192.168.1.1/24 dev lanA
  ip -n svl-A addr add 192.168.1.10/24 dev ethA
  link svl-rB lanB svl-B ethB
  ip -n svl-rB addr add 192.168.2.1/24 dev lanB
  ip -n svl-B addr add 192.168.2.10/24 dev ethB
  for x in "svl-rA lanA" "svl-A ethA" "svl-rB lanB" "svl-B ethB"; do set -- $x; ip -n "$1" link set "$2" up; done
  ip -n svl-A route add default via 192.168.1.1
  ip -n svl-B route add default via 192.168.2.1

  nat_rule svl-rA wanA "$natA" "$fw"
  nat_rule svl-rB wanB "$natB" "$fw"
  echo "lab up: A behind a $natA NAT, B behind a $natB NAT ($fw firewall), anchor 203.0.113.100"
}

case "${1:-}" in
  up) shift; up "$@" ;;
  down) down ;;
  *) echo "usage: $0 up [cone|symmetric] [cone|symmetric] [home|permissive] | down" >&2; exit 2 ;;
esac
