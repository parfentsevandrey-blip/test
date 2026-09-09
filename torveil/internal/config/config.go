// Package config defines TorVeil's persisted settings.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/parfentsevandrey-blip/torveil/internal/tor"
)

// Mode selects how application traffic reaches the tunnel.
type Mode string

const (
	// ModeProxy exposes loopback SOCKS5 and HTTP listeners. No driver, no
	// administrator rights, and applications opt in individually.
	ModeProxy Mode = "proxy"

	// ModeTunnel additionally brings up a Wintun adapter and routes the
	// machine's TCP and DNS through Tor.
	ModeTunnel Mode = "tunnel"
)

// Tunnel holds the full-tunnel settings.
type Tunnel struct {
	// AdapterName is the Wintun adapter's display name.
	AdapterName string `json:"adapterName"`

	// Address is the tunnel interface address in CIDR form.
	Address string `json:"address"`

	// Gateway is the address the stack answers on; it is also the DNS server
	// handed to the system, and queries sent there are forwarded to Tor's
	// DNSPort.
	Gateway string `json:"gateway"`

	// MTU of the tunnel interface. 1500 is safe because the tunnel terminates
	// locally: packets are turned into TCP streams inside this process rather
	// than encapsulated.
	MTU int `json:"mtu"`

	// BypassCIDRs are destinations routed around the tunnel, over the
	// physical interface.
	//
	// Tor's own traffic must not re-enter the tunnel it creates. TorVeil
	// discovers most of these automatically by watching what tor and the
	// pluggable transport connect to; this list is for anything that has to
	// be pinned by hand.
	BypassCIDRs []string `json:"bypassCidrs"`

	// ExcludeLAN keeps private address ranges on the local network instead of
	// sending them into Tor, so printers and NAS boxes keep working.
	ExcludeLAN bool `json:"excludeLan"`

	// BlockIPv6 drops IPv6 while the tunnel is up. Tor's exits are
	// IPv4-reachable and the tunnel carries no IPv6, so without this an
	// IPv6-capable machine would simply bypass the tunnel for any
	// IPv6-reachable site.
	BlockIPv6 bool `json:"blockIpv6"`

	// BlockQUIC drops outbound UDP/443. Tor carries no UDP, so QUIC cannot be
	// tunnelled; blocking it makes browsers fall back to TCP instead of
	// stalling on a protocol that has nowhere to go.
	BlockQUIC bool `json:"blockQuic"`

	// KillSwitch blocks all non-tunnel outbound traffic except Tor's own,
	// so a crash cannot silently drop the machine back onto the open network.
	KillSwitch bool `json:"killSwitch"`
}

// Config is the persisted application configuration.
type Config struct {
	Mode Mode `json:"mode"`

	// Transport selects direct, snowflake or obfs4.
	Transport string `json:"transport"`

	// SnowflakeBridges and Obfs4Bridges are torrc Bridge lines. They are
	// editable because the Tor Project rotates them.
	SnowflakeBridges []string `json:"snowflakeBridges"`
	Obfs4Bridges     []string `json:"obfs4Bridges"`

	// Hops is the circuit length, 2 to 5.
	Hops int `json:"hops"`

	// EntryCountry pins the first hop's country; ignored when a bridge is in
	// use, since the bridge is then the first hop.
	EntryCountry string `json:"entryCountry"`

	// ExitCountry pins the last hop's country.
	ExitCountry string `json:"exitCountry"`

	// ExcludeCountries bars every hop from these countries.
	ExcludeCountries []string `json:"excludeCountries"`

	// PinnedGuard is the fingerprint of the entry relay to keep reusing.
	PinnedGuard string `json:"pinnedGuard"`

	// ShapingProfile is the traffic-shaping profile ID.
	ShapingProfile string `json:"shapingProfile"`

	// ChaffRateOverride replaces the profile's cover-traffic ceiling, in
	// bytes per second. Zero keeps the profile default.
	ChaffRateOverride int `json:"chaffRateOverride"`

	// SOCKSListen and HTTPListen are the loopback proxy addresses.
	SOCKSListen string `json:"socksListen"`
	HTTPListen  string `json:"httpListen"`

	Tunnel Tunnel `json:"tunnel"`

	// TorSearchDirs are extra directories searched for tor and the pluggable
	// transports before the built-in locations.
	TorSearchDirs []string `json:"torSearchDirs"`

	// ExtraTorrc holds additional torrc options, for anything the interface
	// does not cover: an upstream HTTPSProxy on a corporate network,
	// ExcludeNodes, bandwidth limits.
	//
	// It is applied before the shaping profile's own options, so a profile
	// still controls the padding settings it is defined by. Everything else is
	// yours to set.
	ExtraTorrc map[string]string `json:"extraTorrc,omitempty"`

	// DataDir holds the Tor data directory and the generated torrc.
	DataDir string `json:"dataDir"`

	// AutoConnect starts the tunnel as soon as the application launches.
	AutoConnect bool `json:"autoConnect"`
}

// Default returns the shipped configuration.
func Default() Config {
	return Config{
		Mode:             ModeProxy,
		Transport:        "snowflake",
		SnowflakeBridges: nil, // filled from the tor package's defaults
		Hops:             3,
		ShapingProfile:   "balanced",
		SOCKSListen:      "127.0.0.1:9150",
		HTTPListen:       "127.0.0.1:9151",
		Tunnel: Tunnel{
			AdapterName: "TorVeil",
			Address:     "10.65.0.2/24",
			Gateway:     "10.65.0.1",
			MTU:         1500,
			ExcludeLAN:  true,
			BlockIPv6:   true,
			BlockQUIC:   true,
			KillSwitch:  true,
		},
		AutoConnect: false,
	}
}

// Dir returns the per-user configuration directory.
func Dir() (string, error) {
	if runtime.GOOS == "windows" {
		if appData := os.Getenv("APPDATA"); appData != "" {
			return filepath.Join(appData, "TorVeil"), nil
		}
	}
	base, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("locate configuration directory: %w", err)
	}
	return filepath.Join(base, "torveil"), nil
}

// Path returns the configuration file path.
func Path() (string, error) {
	dir, err := Dir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "config.json"), nil
}

// Load reads the configuration, returning defaults when none exists yet.
func Load() (Config, error) {
	cfg := Default()
	path, err := Path()
	if err != nil {
		return cfg, err
	}
	raw, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return cfg.withDerivedDefaults(), nil
	}
	if err != nil {
		return cfg, fmt.Errorf("read configuration: %w", err)
	}
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return Default().withDerivedDefaults(), fmt.Errorf("parse configuration %s: %w", path, err)
	}
	return cfg.withDerivedDefaults(), nil
}

// Save writes the configuration atomically, so a crash mid-write cannot leave
// an unreadable file behind.
func (c Config) Save() error {
	path, err := Path()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("create configuration directory: %w", err)
	}
	raw, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return fmt.Errorf("encode configuration: %w", err)
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, append(raw, '\n'), 0o600); err != nil {
		return fmt.Errorf("write configuration: %w", err)
	}
	if err := os.Rename(tmp, path); err != nil {
		return fmt.Errorf("replace configuration: %w", err)
	}
	return nil
}

// ActiveBridges returns the bridge lines for the selected transport, falling
// back to the shipped defaults when the user has not supplied their own.
func (c Config) ActiveBridges() []string {
	switch strings.ToLower(strings.TrimSpace(c.Transport)) {
	case "snowflake":
		if lines := nonEmpty(c.SnowflakeBridges); len(lines) > 0 {
			return lines
		}
		return tor.DefaultSnowflakeBridges
	case "obfs4":
		return nonEmpty(c.Obfs4Bridges)
	default:
		return nil
	}
}

func nonEmpty(in []string) []string {
	out := make([]string, 0, len(in))
	for _, s := range in {
		if t := strings.TrimSpace(s); t != "" && !strings.HasPrefix(t, "#") {
			out = append(out, t)
		}
	}
	return out
}

// Normalized returns the configuration with environment-derived defaults
// filled in and out-of-range values clamped.
func (c Config) Normalized() Config { return c.withDerivedDefaults() }

// withDerivedDefaults fills in values that depend on the environment and
// clamps anything out of range.
func (c Config) withDerivedDefaults() Config {
	d := Default()
	if c.Mode != ModeProxy && c.Mode != ModeTunnel {
		c.Mode = d.Mode
	}
	if strings.TrimSpace(c.Transport) == "" {
		c.Transport = d.Transport
	}
	if c.Hops < 2 || c.Hops > 5 {
		c.Hops = d.Hops
	}
	if strings.TrimSpace(c.ShapingProfile) == "" {
		c.ShapingProfile = d.ShapingProfile
	}
	if strings.TrimSpace(c.SOCKSListen) == "" {
		c.SOCKSListen = d.SOCKSListen
	}
	if strings.TrimSpace(c.HTTPListen) == "" {
		c.HTTPListen = d.HTTPListen
	}
	if strings.TrimSpace(c.Tunnel.AdapterName) == "" {
		c.Tunnel.AdapterName = d.Tunnel.AdapterName
	}
	if strings.TrimSpace(c.Tunnel.Address) == "" {
		c.Tunnel.Address = d.Tunnel.Address
	}
	if strings.TrimSpace(c.Tunnel.Gateway) == "" {
		c.Tunnel.Gateway = d.Tunnel.Gateway
	}
	if c.Tunnel.MTU < 576 || c.Tunnel.MTU > 9000 {
		c.Tunnel.MTU = d.Tunnel.MTU
	}
	if strings.TrimSpace(c.DataDir) == "" {
		if dir, err := Dir(); err == nil {
			c.DataDir = filepath.Join(dir, "tor")
		}
	}
	c.EntryCountry = strings.ToLower(strings.TrimSpace(c.EntryCountry))
	c.ExitCountry = strings.ToLower(strings.TrimSpace(c.ExitCountry))
	for i, cc := range c.ExcludeCountries {
		c.ExcludeCountries[i] = strings.ToLower(strings.TrimSpace(cc))
	}
	return c
}
