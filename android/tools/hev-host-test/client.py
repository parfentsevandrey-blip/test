import socket, struct, time, random, ipaddress
def dns_query(server, name, qtype=1, family=socket.AF_INET):
    tid = random.randint(0, 65535)
    q = struct.pack(">HHHHHH", tid, 0x0100, 1, 0, 0, 0)
    q += b"".join(bytes([len(p)]) + p.encode() for p in name.split(".")) + b"\0" + struct.pack(">HH", qtype, 1)
    s = socket.socket(family, socket.SOCK_DGRAM); s.settimeout(3)
    s.sendto(q, (server, 53)); r, _ = s.recvfrom(2048)
    an = struct.unpack(">H", r[6:8])[0]; rcode = r[3] & 0xF
    ips = []
    if an:
        off = len(q)
        for _ in range(an):
            off += 2; t, c, ttl, l = struct.unpack(">HHIH", r[off:off+10]); off += 10
            if t == 1: ips.append(socket.inet_ntoa(r[off:off+4]))
            off += l
    return rcode, an, ips
results = []
def check(name, ok, detail=""):
    results.append(ok); print(("PASS " if ok else "FAIL ") + name + (" -- " + detail if detail else ""))
rc, an, ips = dns_query("198.18.0.2", "example.com")
check("MapDNS A answer in 100.64/10", an == 1 and ipaddress.ip_address(ips[0]) in ipaddress.ip_network("100.64.0.0/10"), str(ips))
fake = ips[0]
rc, an, ips2 = dns_query("8.8.8.8", "check.torproject.org")
check("DNS to any resolver hijacked by MapDNS", an == 1 and ipaddress.ip_address(ips2[0]) in ipaddress.ip_network("100.64.0.0/10"), str(ips2))
rc, an, _ = dns_query("198.18.0.2", "example.com", qtype=28)
check("AAAA -> NOERROR/NODATA", rc == 0 and an == 0, f"rcode={rc} an={an}")
t = socket.create_connection((fake, 80), timeout=5)
t.sendall(b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n"); resp = t.recv(4096).decode(); t.close()
check("TCP via fake IP reaches SOCKS with hostname", "target=example.com:80" in resp, resp.splitlines()[-1])
t = socket.create_connection(("93.184.215.14", 443), timeout=5); t.sendall(b"x"); resp = t.recv(4096).decode(); t.close()
check("TCP to IPv4 literal passes as IP", "target=93.184.215.14:443" in resp, resp.splitlines()[-1])
for fam, addr in ((socket.AF_INET, ("1.1.1.1", 443)), (socket.AF_INET, ("8.8.4.4", 3478))):
    u = socket.socket(fam, socket.SOCK_DGRAM); u.settimeout(5); u.connect(addr)
    t0 = time.monotonic(); u.send(b"quic-initial")
    try:
        u.recv(100); check(f"UDP {addr[0]} rejected", False, "got data?!")
    except ConnectionRefusedError:
        dt = (time.monotonic() - t0) * 1000
        check(f"UDP {addr[0]}:443 refused fast (ECONNREFUSED)", dt < 200, f"{dt:.1f} ms")
    except socket.timeout:
        check(f"UDP {addr[0]} refused fast", False, "timeout (dropped)")

# Optimistic data (socks5.optimistic-data): the application's first bytes reach the SOCKS server
# before its reply, the reply never leaks into the stream, failures still reset the connection.
import hashlib, os, sys
LOG, OPTIMISTIC = sys.argv[1], sys.argv[2] == "1"
def logged(target):
    for line in reversed(open(LOG).read().splitlines()):
        if f"target={target} " in line:
            return dict(kv.split("=", 1) for kv in line.split()[1:])
    return {}
rc, an, ips = dns_query("198.18.0.2", "slow.test")
t0 = time.monotonic(); t = socket.create_connection((ips[0], 80), timeout=10)
t.sendall(b"GET / HTTP/1.1\r\nHost: slow.test\r\n\r\n"); first = t.recv(4096); dt = time.monotonic() - t0
while (d := t.recv(4096)): first += d
t.close(); early = int(logged("slow.test:80").get("early", -1))
check("request answered through the delayed reply", b"target=slow.test:80" in first, f"{dt:.2f} s")
check("data before the reply" if OPTIMISTIC else "no data before the reply",
      (early > 0) if OPTIMISTIC else (early == 0), f"early={early}")
t = socket.create_connection(("10.1.2.3", 7), timeout=10); t.sendall(b"hello"); got = b""
while len(got) < len(b"REPLY-THEN-DATA|hello"): got += t.recv(4096)
t.sendall(b"|more"); got += t.recv(4096); t.close()
check("reply consumed exactly, data behind it delivered", got == b"REPLY-THEN-DATA|hello|more", repr(got))
t = socket.create_connection(("10.1.2.3", 9), timeout=10); t.sendall(b"x")
try:
    d = t.recv(4096); refused = d == b""
except ConnectionResetError:
    refused = True
check("refused request resets the connection", refused)
blob = os.urandom(300_000) + b"\n\n"
rc, an, ips = dns_query("198.18.0.2", "bulk.test")
t = socket.create_connection((ips[0], 80), timeout=20); t.sendall(blob); r = t.recv(4096).decode(); t.close()
check("300 KB sent before the reply arrive intact", r == f"{len(blob)} {hashlib.sha256(blob).hexdigest()}", r[:24])
slow = dns_query("198.18.0.2", "slow.test")[2][0]
t = socket.create_connection((slow, 81), timeout=10)
t.sendall(b"GET / HTTP/1.1\r\nHost: slow.test\r\n\r\n"); t.shutdown(socket.SHUT_WR); r = b""
while (d := t.recv(4096)): r += d
t.close(); e = logged("slow.test:81")
check("end of input waits for the reply", b"200 OK" in r and e.get("eof_before_reply") == "0", str(e))
# No Nagle towards the SOCKS server: Tor sends no data back for a while, so a small write behind
# an unacknowledged one would wait for a delayed ACK (40 ms). Nine bytes, 2 ms apart: ~16 ms.
t = socket.create_connection(("10.1.2.3", 8), timeout=10); t.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
t.send(b"0"); time.sleep(0.5)
for ch in b"123456789":
    t.send(bytes([ch])); time.sleep(0.002)
ms = float(t.recv(64).decode()); t.close()
check("small writes are not held back (no Nagle)", ms < 32, f"{ms:.1f} ms for 9 writes 2 ms apart")
print(f"{sum(results)}/{len(results)} passed")
