//go:build linux

package api

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"testing"
)

// procAddr writes an address the way /proc/net/tcp does (the inverse of parseProcAddr).
func procAddr(ap netip.AddrPort) string {
	a := ap.Addr()
	var raw []byte
	if a.Is4() {
		b := a.As4()
		raw = b[:]
	} else {
		b := a.As16()
		raw = b[:]
	}
	out := make([]byte, len(raw))
	for i := 0; i < len(raw); i += 4 {
		binary.BigEndian.PutUint32(out[i:], binary.NativeEndian.Uint32(raw[i:]))
	}
	return strings.ToUpper(hex.EncodeToString(out)) + fmt.Sprintf(":%04X", ap.Port())
}

func TestProcAddrRoundTrip(t *testing.T) {
	for _, s := range []string{"127.0.0.1:8777", "127.0.0.1:65535", "[::1]:41710", "10.1.2.3:1", "[fe80::1:2]:80"} {
		ap := netip.MustParseAddrPort(s)
		got, ok := parseProcAddr(procAddr(ap))
		if !ok || got != ap {
			t.Errorf("%s -> %q -> %v (ok %v)", s, procAddr(ap), got, ok)
		}
	}
	// What a little-endian kernel prints for 127.0.0.1:50000 and for ::1:50000.
	if binary.NativeEndian.Uint16([]byte{1, 0}) == 1 {
		if got, ok := parseProcAddr("0100007F:C350"); !ok || got != netip.MustParseAddrPort("127.0.0.1:50000") {
			t.Errorf("0100007F:C350 -> %v %v", got, ok)
		}
		if got, ok := parseProcAddr("00000000000000000000000001000000:C350"); !ok || got != netip.MustParseAddrPort("[::1]:50000") {
			t.Errorf("::1 -> %v %v", got, ok)
		}
	}
	for _, bad := range []string{"", "0100007F", "0100007F:ZZZZ", "01:1F90", "0100007F:1F90FF", "xyz:1F90"} {
		if _, ok := parseProcAddr(bad); ok {
			t.Errorf("%q was accepted", bad)
		}
	}
}

func TestUIDInTable(t *testing.T) {
	client, server := netip.MustParseAddrPort("127.0.0.1:50000"), netip.MustParseAddrPort("127.0.0.1:8777")
	other := netip.MustParseAddrPort("127.0.0.1:50001")
	line := func(i int, l, r netip.AddrPort, uid int) string {
		return fmt.Sprintf("%4d: %s %s 01 00000000:00000000 00:00000000 00000000 %5d        0 %d 1 0000000000000000 20 4 0 10 -1\n",
			i, procAddr(l), procAddr(r), uid, 1000+i)
	}
	table := "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n" +
		line(0, server, client, 111) + // the server's end of the connection (owned by svoi)
		line(1, other, server, 2222) + // somebody else's connection
		line(2, client, server, 1234) // the client's end: the one we want
	if uid, ok := uidInTable(strings.NewReader(table), client, server); !ok || uid != 1234 {
		t.Fatalf("got %d %v, want 1234", uid, ok)
	}
	if uid, ok := uidInTable(strings.NewReader(table), other, server); !ok || uid != 2222 {
		t.Fatalf("got %d %v, want 2222", uid, ok)
	}
	if _, ok := uidInTable(strings.NewReader(table), netip.MustParseAddrPort("127.0.0.1:50002"), server); ok {
		t.Fatal("found a connection that is not in the table")
	}
	if _, ok := uidInTable(strings.NewReader("garbage\nmore garbage here\n"), client, server); ok {
		t.Fatal("garbage was understood")
	}
	if _, ok := uidInTable(strings.NewReader(""), client, server); ok {
		t.Fatal("an empty table was understood")
	}
}

// Against the real kernel: a request this process makes over loopback is ours.
func TestPeerUIDOfARealLoopbackRequest(t *testing.T) {
	if _, err := os.Stat("/proc/net/tcp"); err != nil {
		t.Skip("no /proc/net/tcp here")
	}
	var uid int
	var ok bool
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		uid, ok = peerUID(r)
		io.WriteString(w, strconv.Itoa(uid))
	}))
	defer srv.Close()
	resp, err := http.Get(srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if !ok || uid != os.Getuid() {
		t.Fatalf("peerUID = %d, %v; want %d, true", uid, ok, os.Getuid())
	}
	// A request that does not come over loopback (here: a made-up remote address) is not identified.
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "203.0.113.5:4444"
	if _, ok := peerUID(r); ok {
		t.Fatal("a non-loopback peer was identified")
	}
}
