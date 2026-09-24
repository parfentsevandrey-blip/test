#!/usr/bin/env bash
# End-to-end check of the patched hev-socks5-tunnel on a Linux host (needs root for a network
# namespace + /dev/net/tun). Verifies: MapDNS, DNS hijack, TCP-by-hostname through SOCKS,
# fast UDP rejection (ICMP port unreachable -> ECONNREFUSED).
#   sudo tools/hev-host-test/run.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../core/tunnel/src/main/cpp/hev-socks5-tunnel"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp -r "$SRC" "$WORK/hev"
make -C "$WORK/hev" -j"$(nproc)" REV_ID=test >/dev/null
# hev opens dual-stack AF_INET6 sockets; on kernels without IPv6 remap them to IPv4.
PRELOAD=""
if [ ! -d /proc/sys/net/ipv6 ]; then
    gcc -shared -fPIC -O2 -o "$WORK/v4shim.so" "$HERE/v4shim.c" -ldl
    PRELOAD="$WORK/v4shim.so"
fi
unshare -n bash -c "
    set -e
    ip link set lo up
    python3 '$HERE/socks.py' '$WORK/socks.log' & SP=\$!
    LD_PRELOAD='$PRELOAD' '$WORK/hev/bin/hev-socks5-tunnel' '$HERE/hev.yml' & HP=\$!
    sleep 1
    ip route add default dev tun0
    RC=0; python3 '$HERE/client.py' || RC=\$?
    kill \$HP \$SP
    exit \$RC
"
