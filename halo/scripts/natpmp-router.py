#!/usr/bin/env python3
"""A NAT-PMP server for tests: hands out UDP port mappings on a Linux router.

    natpmp-router.py <lan-ip> <wan-ip> <wan-interface>

Like a home router, it forwards a port on its public address to the device that
asks (RFC 6886), by adding an iptables DNAT rule in its network namespace. PCP
requests get NAT-PMP's "unsupported version" answer, so clients fall back.
"""

import socket
import struct
import subprocess
import sys
import time

PORT = 5351
UNSUPPORTED_VERSION, UNSUPPORTED_OPCODE = 1, 5

lan_ip, wan_ip, wan_if = sys.argv[1:4]
started = time.monotonic()
mappings = {}  # (device ip, internal port) -> external port


def epoch():
    return int(time.monotonic() - started)


def dnat(action, external, device, internal):
    subprocess.run(
        ["iptables", "-t", "nat", action, "PREROUTING", "-i", wan_if, "-p", "udp",
         "--dport", str(external), "-j", "DNAT", "--to-destination", f"{device}:{internal}"],
        check=True,
    )


def answer(data, device):
    version, opcode = data[0], data[1]
    if version != 0:
        return struct.pack("!BBHI", 0, 128 | opcode, UNSUPPORTED_VERSION, epoch())
    if opcode == 0:
        return struct.pack("!BBHI4s", 0, 128, 0, epoch(), socket.inet_aton(wan_ip))
    if opcode != 1 or len(data) < 12:
        return struct.pack("!BBHI", 0, 128 | opcode, UNSUPPORTED_OPCODE, epoch())
    _, _, _, internal, suggested, lifetime = struct.unpack("!BBHHHI", data[:12])
    key = (device, internal)
    if lifetime == 0:
        external = mappings.pop(key, None)
        if external is not None:
            dnat("-D", external, device, internal)
        return struct.pack("!BBHIHHI", 0, 129, 0, epoch(), internal, 0, 0)
    external = mappings.get(key)
    if external is None:
        external = suggested or internal
        while external in mappings.values():
            external += 1
        dnat("-A", external, device, internal)
        mappings[key] = external
        print(f"mapped {wan_ip}:{external} -> {device}:{internal}", flush=True)
    return struct.pack("!BBHIHHI", 0, 129, 0, epoch(), internal, external, lifetime)


sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((lan_ip, PORT))
print(f"NAT-PMP on {lan_ip}:{PORT}, public address {wan_ip}", flush=True)
while True:
    data, (device, port) = sock.recvfrom(1100)
    if len(data) >= 2:
        sock.sendto(answer(data, device), (device, port))
