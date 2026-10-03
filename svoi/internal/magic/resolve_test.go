package magic

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"testing"
	"time"

	"golang.org/x/net/dns/dnsmessage"
)

// A tiny DNS server that answers every A query with 192.0.2.7.
func fakeDNS(t *testing.T) string {
	t.Helper()
	pc, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Skip("no loopback UDP:", err)
	}
	t.Cleanup(func() { pc.Close() })
	go func() {
		buf := make([]byte, 512)
		for {
			n, from, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			var m dnsmessage.Message
			if m.Unpack(buf[:n]) != nil || len(m.Questions) == 0 {
				continue
			}
			q := m.Questions[0]
			resp := dnsmessage.Message{
				Header:    dnsmessage.Header{ID: m.ID, Response: true, Authoritative: true, RecursionAvailable: true},
				Questions: m.Questions,
			}
			if q.Type == dnsmessage.TypeA {
				resp.Answers = []dnsmessage.Resource{{
					Header: dnsmessage.ResourceHeader{Name: q.Name, Type: dnsmessage.TypeA, Class: dnsmessage.ClassINET, TTL: 60},
					Body:   &dnsmessage.AResource{A: [4]byte{192, 0, 2, 7}},
				}}
			}
			out, err := resp.Pack()
			if err == nil {
				_, _ = pc.WriteTo(out, from)
			}
		}
	}()
	return pc.LocalAddr().String()
}

func TestResolveFallsBackWhenSystemResolverIsBroken(t *testing.T) {
	oldSys, oldFb := systemLookup, fallbackLookup
	t.Cleanup(func() { systemLookup, fallbackLookup = oldSys, oldFb })

	systemLookup = func(context.Context, string) ([]string, error) { return nil, errors.New("no resolv.conf here") }
	good := fakeDNS(t)
	fallbackLookup = func(ctx context.Context, host string) ([]string, error) {
		return dnsLookup(ctx, host, []string{"127.0.0.1:1", good}) // the first server is dead, the second answers
	}

	start := time.Now()
	got, err := resolveHostPort("stun.example.test:3478")
	if err != nil {
		t.Fatal(err)
	}
	want := netip.MustParseAddrPort("192.0.2.7:3478")
	if len(got) != 1 || got[0] != want {
		t.Fatalf("got %v, want [%v]", got, want)
	}
	if time.Since(start) > 3*time.Second {
		t.Fatalf("took %v", time.Since(start))
	}
}

func TestResolvePrefersSystemAndReportsFailure(t *testing.T) {
	oldSys, oldFb := systemLookup, fallbackLookup
	t.Cleanup(func() { systemLookup, fallbackLookup = oldSys, oldFb })

	// IP literals never touch DNS.
	systemLookup = func(context.Context, string) ([]string, error) {
		t.Fatal("DNS used for an IP literal")
		return nil, nil
	}
	if got, err := resolveHostPort("192.0.2.9:19302"); err != nil || len(got) != 1 || got[0].Addr().String() != "192.0.2.9" {
		t.Fatalf("literal: %v %v", got, err)
	}
	// A working system resolver wins; the fallback is not consulted.
	systemLookup = func(context.Context, string) ([]string, error) { return []string{"198.51.100.4"}, nil }
	fallbackLookup = func(context.Context, string) ([]string, error) {
		t.Fatal("fallback used although the system resolver worked")
		return nil, nil
	}
	if got, err := resolveHostPort("stun.example.test:3478"); err != nil || len(got) != 1 || got[0].Addr().String() != "198.51.100.4" {
		t.Fatalf("system resolver: %v %v", got, err)
	}
	// Both broken: an error, quickly.
	systemLookup = func(context.Context, string) ([]string, error) { return nil, errors.New("down") }
	fallbackLookup = func(ctx context.Context, host string) ([]string, error) {
		return dnsLookup(ctx, host, []string{"127.0.0.1:1"})
	}
	start := time.Now()
	if _, err := resolveHostPort("stun.example.test:3478"); err == nil {
		t.Fatal("expected an error")
	}
	if time.Since(start) > 5*time.Second {
		t.Fatalf("failure took %v", time.Since(start))
	}
}

// A black-holed server must not stall the lookup for long, and the next one is tried.
func TestDNSLookupSkipsSilentServer(t *testing.T) {
	silent, err := net.ListenPacket("udp4", "127.0.0.1:0") // reads nothing, never answers
	if err != nil {
		t.Skip(err)
	}
	defer silent.Close()
	good := fakeDNS(t)
	start := time.Now()
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()
	got, err := dnsLookup(ctx, "stun.example.test", []string{silent.LocalAddr().String(), good})
	if err != nil || len(got) != 1 || got[0] != "192.0.2.7" {
		t.Fatalf("got %v, %v", got, err)
	}
	if d := time.Since(start); d > 3*time.Second {
		t.Fatalf("took %v", d)
	}
}
