# Minimal SOCKS5 server imitating Tor's SOCKSPort: CONNECT only, UDP ASSOCIATE refused.
# Like Tor it accepts a pipelined handshake and application data sent before its reply
# (optimistic data); it logs how many bytes arrived before the reply ("early=").
# Special targets:
#   slow.test       replies after 1.5 s (a circuit round trip), then answers HTTP
#   *:7             echo: the reply and "REPLY-THEN-DATA|" leave in one segment, then an echo
#   *:9             refused (host unreachable) after the wait
#   bulk.test       reads until "\n\n" arrives, answers with the byte count and SHA-256
#   *:8             like Tor, sends nothing back for a while: answers when the 10th byte arrived
import hashlib, select, socket, struct, threading, time, sys
LOG = open(sys.argv[1], "a", buffering=1)

def recv_exact(c, n):
    b = b""
    while len(b) < n:
        d = c.recv(n - len(b))
        if not d:
            raise EOFError("eof in handshake")
        b += d
    return b

def early_data(c, seconds):
    """Collects what the client sends before the reply; returns (bytes, eof_seen)."""
    got, end = b"", time.monotonic() + seconds
    while (left := end - time.monotonic()) > 0:
        r, _, _ = select.select([c], [], [], left)
        if not r:
            break
        d = c.recv(65536)
        if not d:
            return got, True
        got += d
    return got, False

def handle(c):
    try:
        ver, n = recv_exact(c, 2); recv_exact(c, n); c.sendall(b"\x05\x00")
        ver, cmd, _, atyp = recv_exact(c, 4)
        if atyp == 1: host = socket.inet_ntoa(recv_exact(c, 4))
        elif atyp == 3: host = recv_exact(c, recv_exact(c, 1)[0]).decode()
        elif atyp == 4: host = socket.inet_ntop(socket.AF_INET6, recv_exact(c, 16))
        port = struct.unpack(">H", recv_exact(c, 2))[0]
        if cmd != 1:
            LOG.write(f"cmd={cmd} target={host}:{port}\n")
            c.sendall(b"\x05\x07\x00\x01" + b"\0"*6); c.close(); return
        wait = 1.5 if host == "slow.test" else (2.0 if host == "bulk.test" else 0.3)
        early, eof = early_data(c, wait)
        LOG.write(f"cmd={cmd} target={host}:{port} early={len(early)} eof_before_reply={int(eof)}\n")
        ok = b"\x05\x00\x00\x01" + b"\0"*6
        if port == 9:
            c.sendall(b"\x05\x04\x00\x01" + b"\0"*6); return
        if port == 8:
            c.sendall(ok); got, t0 = len(early), None
            while got < 10:
                d = c.recv(64)
                if not d: break
                got += len(d)
                if t0 is None and got >= 2: t0 = time.monotonic()
            c.sendall(f"{(time.monotonic() - (t0 or time.monotonic())) * 1000:.1f}".encode()); return
        if port == 7:
            c.sendall(ok + b"REPLY-THEN-DATA|" + early)
            while (d := c.recv(65536)):
                c.sendall(d)
            return
        if host == "bulk.test":
            c.sendall(ok)
            data = early
            while not data.endswith(b"\n\n"):
                d = c.recv(65536)
                if not d: break
                data += d
            c.sendall(f"{len(data)} {hashlib.sha256(data).hexdigest()}".encode()); return
        c.sendall(ok)
        data = early if early else c.recv(4096)
        body = f"target={host}:{port}"
        c.sendall(f"HTTP/1.1 200 OK\r\nContent-Length: {len(body)}\r\nConnection: close\r\n\r\n{body}".encode())
    except Exception as e:
        LOG.write(f"err {e}\n")
    finally:
        c.close()

s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 1080)); s.listen(64)
while True:
    c, _ = s.accept(); threading.Thread(target=handle, args=(c,), daemon=True).start()
