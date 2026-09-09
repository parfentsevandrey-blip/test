package tor

import (
	"strings"
	"testing"
)

func baseOptions() TorrcOptions {
	return TorrcOptions{
		DataDir:  `C:\Users\a b\AppData\Roaming\TorVeil\tor`,
		Ports:    Ports{SOCKS: 9150, Control: 9151, DNS: 9152},
		Binaries: Binaries{Tor: `C:\Tor\tor.exe`},
	}
}

func TestRenderTorrcDirect(t *testing.T) {
	o := baseOptions()
	o.Transport = TransportDirect

	body, err := RenderTorrc(o)
	if err != nil {
		t.Fatalf("RenderTorrc: %v", err)
	}

	for _, want := range []string{
		"SocksPort 127.0.0.1:9150",
		"ControlPort 127.0.0.1:9151",
		"DNSPort 127.0.0.1:9152",
		"CookieAuthentication 1",
		"UseBridges 0",
		"ClientOnly 1",
	} {
		if !strings.Contains(body, want) {
			t.Errorf("torrc is missing %q:\n%s", want, body)
		}
	}

	// The SOCKS listener must never be reachable from the network: an open
	// Tor proxy on a LAN is a far worse exposure than anything it protects.
	if !strings.Contains(body, "SocksPolicy accept 127.0.0.1/32") || !strings.Contains(body, "SocksPolicy reject *") {
		t.Errorf("torrc does not restrict the SOCKS port to loopback:\n%s", body)
	}
}

func TestRenderTorrcQuotesWindowsPaths(t *testing.T) {
	o := baseOptions()
	o.Transport = TransportDirect

	body, err := RenderTorrc(o)
	if err != nil {
		t.Fatalf("RenderTorrc: %v", err)
	}
	// Tor reads C-style quoted strings, so a Windows path's backslashes have
	// to be doubled or the data directory silently becomes something else.
	want := `DataDirectory "C:\\Users\\a b\\AppData\\Roaming\\TorVeil\\tor"`
	if !strings.Contains(body, want) {
		t.Errorf("expected %s in:\n%s", want, body)
	}
}

func TestRenderTorrcSnowflake(t *testing.T) {
	o := baseOptions()
	o.Transport = TransportSnowflake
	o.Binaries.Snowflake = `C:\Tor\pluggable_transports\snowflake-client.exe`
	o.Bridges = DefaultSnowflakeBridges

	body, err := RenderTorrc(o)
	if err != nil {
		t.Fatalf("RenderTorrc: %v", err)
	}
	if !strings.Contains(body, "UseBridges 1") {
		t.Error("snowflake requires UseBridges 1")
	}
	if !strings.Contains(body, `ClientTransportPlugin snowflake exec "C:\\Tor\\pluggable_transports\\snowflake-client.exe"`) {
		t.Errorf("transport plugin line is wrong:\n%s", body)
	}
	if n := strings.Count(body, "\nBridge snowflake "); n != len(DefaultSnowflakeBridges) {
		t.Errorf("got %d bridge lines, want %d:\n%s", n, len(DefaultSnowflakeBridges), body)
	}
}

func TestRenderTorrcRejectsMissingPieces(t *testing.T) {
	t.Run("snowflake without the client binary", func(t *testing.T) {
		o := baseOptions()
		o.Transport = TransportSnowflake
		o.Bridges = DefaultSnowflakeBridges
		if _, err := RenderTorrc(o); err == nil {
			t.Fatal("expected an error when snowflake-client is missing")
		}
	})

	t.Run("bridge transport with no matching bridge lines", func(t *testing.T) {
		o := baseOptions()
		o.Transport = TransportObfs4
		o.Binaries.Obfs4 = `C:\Tor\lyrebird.exe`
		o.Bridges = DefaultSnowflakeBridges // wrong transport
		if _, err := RenderTorrc(o); err == nil {
			t.Fatal("expected an error when no obfs4 bridge lines are configured")
		}
	})

	t.Run("unassigned ports", func(t *testing.T) {
		o := baseOptions()
		o.Ports = Ports{}
		if _, err := RenderTorrc(o); err == nil {
			t.Fatal("expected an error when ports have not been allocated")
		}
	})
}

func TestRenderTorrcAppliesShapingOptions(t *testing.T) {
	o := baseOptions()
	o.Transport = TransportDirect
	o.Extra = map[string]string{
		"ConnectionPadding":        "1",
		"ReducedConnectionPadding": "0",
		"VirtualAddrNetworkIPv4":   "10.192.0.0/10",
	}

	body, err := RenderTorrc(o)
	if err != nil {
		t.Fatalf("RenderTorrc: %v", err)
	}
	for _, want := range []string{
		"ConnectionPadding 1",
		"ReducedConnectionPadding 0",
		"VirtualAddrNetworkIPv4 10.192.0.0/10",
	} {
		if !strings.Contains(body, want) {
			t.Errorf("torrc is missing %q:\n%s", want, body)
		}
	}
}
