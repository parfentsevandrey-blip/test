package tor

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"torss/internal/bridges"
	"torss/internal/config"
)

func testLayout(t *testing.T) Layout {
	// Paths deliberately contain a space: Tor must still parse the torrc.
	dir := filepath.Join(t.TempDir(), "tor bundle")
	if err := os.MkdirAll(filepath.Join(dir, "pluggable_transports"), 0o755); err != nil {
		t.Fatal(err)
	}
	for _, f := range []string{"pluggable_transports/lyrebird", "pluggable_transports/conjure-client"} {
		if err := os.WriteFile(filepath.Join(dir, f), []byte("#!/bin/sh\n"), 0o755); err != nil {
			t.Fatal(err)
		}
	}
	return Layout{
		Dir:        dir,
		Exe:        "tor",
		Lyrebird:   filepath.Join("pluggable_transports", "lyrebird"),
		Conjure:    filepath.Join("pluggable_transports", "conjure-client"),
		HasConjure: true,
	}
}

func TestTorrcModes(t *testing.T) {
	torBin := os.Getenv("TOR_BIN")
	l := testLayout(t)
	builtin := bridges.Builtin("")
	user := []string{
		"webtunnel [2001:db8::1]:443 0123456789ABCDEF0123456789ABCDEF01234567 url=https://example.com/abc ver=0.0.1",
		"obfs4 198.51.100.7:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0",
	}
	for _, mode := range config.AllModes {
		if !mode.UsesTor() {
			continue
		}
		dataDir := filepath.Join(t.TempDir(), "data dir "+string(mode))
		if err := os.MkdirAll(dataDir, 0o700); err != nil {
			t.Fatal(err)
		}
		var lines []string
		if tr := mode.Transport(); tr != "" {
			lines = bridges.ForTransport(tr, user, builtin, true)
			if len(lines) == 0 {
				if mode == config.ModeTorConjure { // no built-in conjure bridges: user must supply
					continue
				}
				t.Fatalf("no bridges for %s", mode)
			}
		}
		text, err := Torrc(l, Options{
			DataDir: dataDir,
			Ports:   config.Default().Ports,
			Mode:    mode,
			Bridges: lines,
		})
		if err != nil {
			t.Fatalf("%s: %v", mode, err)
		}
		switch mode {
		case config.ModeSSTor:
			if !strings.Contains(text, "Socks5Proxy 127.0.0.1:2081") {
				t.Errorf("ss-tor torrc lacks Socks5Proxy:\n%s", text)
			}
			if strings.Contains(text, "UseBridges") {
				t.Errorf("ss-tor torrc must not use bridges")
			}
		case config.ModeTorDirect:
			if strings.Contains(text, "UseBridges") || strings.Contains(text, "Socks5Proxy") {
				t.Errorf("tor-direct torrc has bridges/proxy:\n%s", text)
			}
		default:
			if !strings.Contains(text, "UseBridges 1") {
				t.Errorf("%s torrc lacks UseBridges:\n%s", mode, text)
			}
		}
		if torBin == "" {
			continue
		}
		p := filepath.Join(dataDir, "torrc")
		if err := os.WriteFile(p, []byte(text), 0o600); err != nil {
			t.Fatal(err)
		}
		cmd := exec.Command(torBin, "--verify-config", "-f", p)
		cmd.Dir = l.Dir
		out, err := cmd.CombinedOutput()
		if err != nil {
			t.Errorf("tor --verify-config failed for %s: %v\n%s\n%s", mode, err, out, text)
		}
	}
}

func TestParseBootstrap(t *testing.T) {
	b, ok := ParseBootstrap(`NOTICE BOOTSTRAP PROGRESS=45 TAG=requesting_descriptors SUMMARY="Asking for relay descriptors"`)
	if !ok || b.Progress != 45 || b.Tag != "requesting_descriptors" || b.Summary != "Asking for relay descriptors" {
		t.Fatalf("bad parse: %+v", b)
	}
	b, ok = ParseBootstrap(`Sep 16 10:00:00.000 [notice] Bootstrapped 100% (done): Done`)
	if !ok || b.Progress != 100 || b.Tag != "done" || b.Summary != "Done" {
		t.Fatalf("log line parse: %+v", b)
	}
	if _, ok := ParseBootstrap("[notice] Opening Socks listener on 127.0.0.1:9050"); ok {
		t.Fatal("unrelated line must not parse")
	}
}
