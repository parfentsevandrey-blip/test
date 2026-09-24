# Minimal SOCKS5 server imitating Tor's SOCKSPort: CONNECT only, UDP ASSOCIATE refused.
import socket, struct, threading, sys
LOG = open(sys.argv[1], "a", buffering=1)
def handle(c):
    try:
        ver, n = c.recv(2); c.recv(n); c.sendall(b"\x05\x00")
        ver, cmd, _, atyp = c.recv(4)
        if atyp == 1: host = socket.inet_ntoa(c.recv(4))
        elif atyp == 3: host = c.recv(c.recv(1)[0]).decode()
        elif atyp == 4: host = socket.inet_ntop(socket.AF_INET6, c.recv(16))
        port = struct.unpack(">H", c.recv(2))[0]
        LOG.write(f"cmd={cmd} target={host}:{port}\n")
        if cmd != 1:
            c.sendall(b"\x05\x07\x00\x01" + b"\0"*6); c.close(); return
        c.sendall(b"\x05\x00\x00\x01" + b"\0"*6)
        data = c.recv(4096)
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
