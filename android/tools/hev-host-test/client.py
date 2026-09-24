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
print(f"{sum(results)}/{len(results)} passed")
