package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/parfentsevandrey-blip/torveil/internal/tor"
)

// isolateConfigDir points the config package at a temporary directory so tests
// never touch the developer's real settings.
func isolateConfigDir(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	t.Setenv("APPDATA", dir)
	t.Setenv("XDG_CONFIG_HOME", dir)
	return dir
}

func TestDefaultsAreUsable(t *testing.T) {
	isolateConfigDir(t)
	c := Default().Normalized()

	if c.Mode != ModeProxy {
		t.Errorf("default mode = %q; proxy mode is the one that works without administrator rights", c.Mode)
	}
	if c.Transport != "snowflake" {
		t.Errorf("default transport = %q, want snowflake", c.Transport)
	}
	if c.Hops != 3 {
		t.Errorf("default hops = %d; three is Tor's own design and the right default", c.Hops)
	}
	if !strings.HasPrefix(c.SOCKSListen, "127.0.0.1:") || !strings.HasPrefix(c.HTTPListen, "127.0.0.1:") {
		t.Errorf("default listeners must be loopback, got %q and %q", c.SOCKSListen, c.HTTPListen)
	}
	if !c.Tunnel.BlockIPv6 || !c.Tunnel.BlockQUIC {
		t.Error("IPv6 and QUIC must be blocked by default: Tor can carry neither, so both would bypass the tunnel")
	}
	if c.AutoConnect {
		t.Error("auto-connect should be off by default")
	}
	if c.DataDir == "" {
		t.Error("a data directory should be derived when none is configured")
	}
}

func TestNormalizeClampsOutOfRangeValues(t *testing.T) {
	isolateConfigDir(t)

	c := Config{Mode: "nonsense", Hops: 99, Transport: "  ", Tunnel: Tunnel{MTU: 3}}
	n := c.Normalized()

	if n.Mode != ModeProxy {
		t.Errorf("mode = %q, want the default for an unrecognised value", n.Mode)
	}
	if n.Hops < 2 || n.Hops > 5 {
		t.Errorf("hops = %d, want it clamped into 2-5", n.Hops)
	}
	if n.Transport == "" {
		t.Error("blank transport should fall back to the default")
	}
	if n.Tunnel.MTU < 576 {
		t.Errorf("MTU = %d, want a usable value", n.Tunnel.MTU)
	}
}

func TestNormalizeLowercasesCountries(t *testing.T) {
	isolateConfigDir(t)

	c := Config{ExitCountry: " DE ", EntryCountry: "NL", ExcludeCountries: []string{" US ", "Gb"}}
	n := c.Normalized()

	if n.ExitCountry != "de" || n.EntryCountry != "nl" {
		t.Errorf("countries = %q/%q, want de/nl", n.EntryCountry, n.ExitCountry)
	}
	// Country codes are compared against Tor's GeoIP output, which is
	// lowercase; a stray capital would silently match nothing.
	for _, cc := range n.ExcludeCountries {
		if cc != strings.ToLower(cc) || strings.TrimSpace(cc) != cc {
			t.Errorf("excluded country %q was not normalised", cc)
		}
	}
}

func TestActiveBridges(t *testing.T) {
	isolateConfigDir(t)

	t.Run("snowflake falls back to the shipped defaults", func(t *testing.T) {
		c := Config{Transport: "snowflake"}
		got := c.ActiveBridges()
		if len(got) != len(tor.DefaultSnowflakeBridges) {
			t.Fatalf("got %d bridge lines, want the %d shipped defaults", len(got), len(tor.DefaultSnowflakeBridges))
		}
	})

	t.Run("configured lines win", func(t *testing.T) {
		custom := "snowflake 192.0.2.9:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://example.invalid/"
		c := Config{Transport: "snowflake", SnowflakeBridges: []string{custom}}
		got := c.ActiveBridges()
		if len(got) != 1 || got[0] != custom {
			t.Errorf("got %v, want the configured line", got)
		}
	})

	t.Run("blank and commented lines are dropped", func(t *testing.T) {
		c := Config{Transport: "obfs4", Obfs4Bridges: []string{"", "   ", "# a note", "obfs4 192.0.2.1:443 AAA cert=x"}}
		if got := c.ActiveBridges(); len(got) != 1 {
			t.Errorf("got %v, want only the real bridge line", got)
		}
	})

	t.Run("a direct connection uses no bridges", func(t *testing.T) {
		c := Config{Transport: "direct", SnowflakeBridges: []string{"snowflake 192.0.2.1:80 AAA"}}
		if got := c.ActiveBridges(); len(got) != 0 {
			t.Errorf("got %v, want none", got)
		}
	})
}

func TestSaveLoadRoundTrip(t *testing.T) {
	dir := isolateConfigDir(t)

	c := Default().Normalized()
	c.Hops = 5
	c.ExitCountry = "se"
	c.ShapingProfile = "paranoid"
	c.ChaffRateOverride = 64 * 1024
	c.PinnedGuard = "2B280B23E1107BB62ABFC40DDCC8824814F80A72"
	c.ExtraTorrc = map[string]string{"ExcludeNodes": "{ru}"}

	if err := c.Save(); err != nil {
		t.Fatalf("Save: %v", err)
	}

	got, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if got.Hops != 5 || got.ExitCountry != "se" || got.ShapingProfile != "paranoid" {
		t.Errorf("round trip lost settings: %+v", got)
	}
	if got.ChaffRateOverride != 64*1024 {
		t.Errorf("chaff override = %d, want %d", got.ChaffRateOverride, 64*1024)
	}
	if got.PinnedGuard != c.PinnedGuard {
		t.Errorf("pinned guard = %q, want %q", got.PinnedGuard, c.PinnedGuard)
	}
	if got.ExtraTorrc["ExcludeNodes"] != "{ru}" {
		t.Errorf("extra torrc options were lost: %v", got.ExtraTorrc)
	}

	// The settings file records which relays you use and where you exit, so it
	// should not be world-readable.
	path, err := Path()
	if err != nil {
		t.Fatalf("Path: %v", err)
	}
	if !strings.HasPrefix(path, dir) {
		t.Fatalf("config path %q escaped the temporary directory %q", path, dir)
	}
	if info, err := os.Stat(path); err == nil {
		if perm := info.Mode().Perm(); perm&0o077 != 0 {
			t.Errorf("config file mode is %o, want no group or world access", perm)
		}
	}
}

func TestLoadWithoutAFileReturnsDefaults(t *testing.T) {
	isolateConfigDir(t)
	c, err := Load()
	if err != nil {
		t.Fatalf("Load on a fresh machine should not fail: %v", err)
	}
	if c.Hops != Default().Hops {
		t.Errorf("hops = %d, want the default", c.Hops)
	}
}

func TestLoadWithBrokenFileFallsBackAndReports(t *testing.T) {
	isolateConfigDir(t)
	path, err := Path()
	if err != nil {
		t.Fatalf("Path: %v", err)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}

	c, err := Load()
	if err == nil {
		t.Error("a corrupt settings file should be reported, not swallowed")
	}
	// It should still hand back something usable, so the application starts
	// and the user can fix the file from the interface.
	if c.Hops < 2 || c.SOCKSListen == "" {
		t.Errorf("fallback config is not usable: %+v", c)
	}
}
