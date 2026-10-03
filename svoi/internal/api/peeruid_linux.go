//go:build linux

package api

import (
	"bufio"
	"encoding/binary"
	"encoding/hex"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"strconv"
	"strings"
)

// peerUID tells which user owns the client end of a loopback connection, from the
// kernel's table of TCP sockets: a browser or curl on this machine is a socket with
// the user's uid on it. ok is false when that cannot be found out (not a loopback
// connection, no /proc, ...). It is how a sign-in link made for "the user who runs
// themesh" can refuse everybody else on the machine (see sessions.redeemCode).
func peerUID(r *http.Request) (uid int, ok bool) {
	remote, err := netip.ParseAddrPort(r.RemoteAddr)
	if err != nil {
		return 0, false
	}
	la, _ := r.Context().Value(http.LocalAddrContextKey).(net.Addr)
	if la == nil {
		return 0, false
	}
	local, err := netip.ParseAddrPort(la.String())
	if err != nil {
		return 0, false
	}
	if !remote.Addr().Unmap().IsLoopback() || !local.Addr().Unmap().IsLoopback() {
		return 0, false
	}
	// The client's socket is the one whose local end is the request's remote address.
	for _, table := range []string{"/proc/net/tcp", "/proc/net/tcp6"} {
		f, err := os.Open(table)
		if err != nil {
			continue
		}
		uid, found := uidInTable(f, remote, local)
		f.Close()
		if found {
			return uid, true
		}
	}
	return 0, false
}

// uidInTable looks in a /proc/net/tcp{,6} listing for the socket that is bound to
// client and connected to server, and returns its owner.
func uidInTable(table io.Reader, client, server netip.AddrPort) (uid int, ok bool) {
	sc := bufio.NewScanner(table)
	sc.Buffer(make([]byte, 0, 64<<10), 1<<20)
	sc.Scan() // header
	for sc.Scan() {
		f := strings.Fields(sc.Text())
		if len(f) < 8 { // sl local rem st queues timer retr uid ...
			continue
		}
		l, ok1 := parseProcAddr(f[1])
		r, ok2 := parseProcAddr(f[2])
		if !ok1 || !ok2 || l != client || r != server {
			continue
		}
		u, err := strconv.Atoi(f[7])
		if err != nil {
			return 0, false
		}
		return u, true
	}
	return 0, false
}

// parseProcAddr reads "0100007F:1F90" (or the 32 hex digit IPv6 form) as printed in
// /proc/net/tcp: every 32-bit word of the address is written in the machine's byte
// order, the port as a plain number.
func parseProcAddr(s string) (netip.AddrPort, bool) {
	ip, port, found := strings.Cut(s, ":")
	if !found {
		return netip.AddrPort{}, false
	}
	raw, err := hex.DecodeString(ip)
	if err != nil || (len(raw) != 4 && len(raw) != 16) {
		return netip.AddrPort{}, false
	}
	p, err := strconv.ParseUint(port, 16, 16)
	if err != nil {
		return netip.AddrPort{}, false
	}
	var b [16]byte
	for i := 0; i < len(raw); i += 4 {
		// The hex digits are the bytes of a host-order uint32: undo that.
		binary.NativeEndian.PutUint32(b[i:], binary.BigEndian.Uint32(raw[i:]))
	}
	var addr netip.Addr
	if len(raw) == 4 {
		addr = netip.AddrFrom4([4]byte(b[:4]))
	} else {
		addr = netip.AddrFrom16(b)
	}
	return netip.AddrPortFrom(addr.Unmap(), uint16(p)), true
}
