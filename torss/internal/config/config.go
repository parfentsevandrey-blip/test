// Package config holds the user configuration and runtime state of TorSS.
package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Mode is a connection strategy. Modes are tried in order in "auto" mode.
type Mode string

const (
	// ModeAuto is not a real mode: it means "walk ModeOrder until something works".
	ModeAuto Mode = "auto"
	// ModeSSTor: Tor over Shadowsocks (+ShadowTLS) to your own VPS. DPI sees only
	// TLS-looking traffic to one host; you get Tor anonymity on top.
	ModeSSTor Mode = "ss-tor"
	// ModeTorWebtunnel: Tor with webtunnel bridges (looks like HTTPS to a real site).
	ModeTorWebtunnel Mode = "tor-webtunnel"
	// ModeTorSnowflake: Tor with the built-in Snowflake bridges (WebRTC + domain fronting).
	ModeTorSnowflake Mode = "tor-snowflake"
	// ModeTorObfs4: Tor with obfs4 bridges (built-in ones are usually blocked in RU,
	// private ones from bridges.torproject.org often work).
	ModeTorObfs4 Mode = "tor-obfs4"
	// ModeTorMeek: Tor with meek_lite (CDN domain fronting). Slow but hard to block.
	ModeTorMeek Mode = "tor-meek"
	// ModeTorConjure: Tor with the Conjure refraction-networking transport.
	ModeTorConjure Mode = "tor-conjure"
	// ModeTorDirect: plain Tor without bridges (useful outside censored networks).
	ModeTorDirect Mode = "tor-direct"
	// ModeSSOnly: Shadowsocks only, no Tor. Fast, not anonymous.
	ModeSSOnly Mode = "ss-only"
)

// AllModes lists every real mode the engine knows about.
var AllModes = []Mode{
	ModeSSTor, ModeTorWebtunnel, ModeTorSnowflake, ModeTorObfs4,
	ModeTorMeek, ModeTorConjure, ModeTorDirect, ModeSSOnly,
}

// UsesTor reports whether the mode runs a Tor process.
func (m Mode) UsesTor() bool { return m != ModeSSOnly && m != ModeAuto }

// UsesSS reports whether the mode needs the Shadowsocks server.
func (m Mode) UsesSS() bool { return m == ModeSSTor || m == ModeSSOnly }

// Transport returns the pluggable transport name for bridge modes ("" otherwise).
func (m Mode) Transport() string {
	switch m {
	case ModeTorWebtunnel:
		return "webtunnel"
	case ModeTorSnowflake:
		return "snowflake"
	case ModeTorObfs4:
		return "obfs4"
	case ModeTorMeek:
		return "meek_lite"
	case ModeTorConjure:
		return "conjure"
	}
	return ""
}

// Title is a short human readable (Russian) label for the tray menu.
func (m Mode) Title() string {
	switch m {
	case ModeAuto:
		return "Авто (перебор режимов)"
	case ModeSSTor:
		return "Shadowsocks → Tor (рекомендуется)"
	case ModeTorWebtunnel:
		return "Tor: мосты webtunnel"
	case ModeTorSnowflake:
		return "Tor: Snowflake"
	case ModeTorObfs4:
		return "Tor: мосты obfs4"
	case ModeTorMeek:
		return "Tor: meek (CDN, медленно)"
	case ModeTorConjure:
		return "Tor: Conjure"
	case ModeTorDirect:
		return "Tor напрямую (без мостов)"
	case ModeSSOnly:
		return "Только Shadowsocks (без Tor, не анонимно)"
	}
	return string(m)
}

// Valid reports whether m is ModeAuto or one of AllModes.
func (m Mode) Valid() bool {
	if m == ModeAuto {
		return true
	}
	for _, x := range AllModes {
		if x == m {
			return true
		}
	}
	return false
}

// Ports are the local listeners. All bind to 127.0.0.1.
type Ports struct {
	Mixed      int `json:"mixed"`       // sing-box HTTP+SOCKS proxy for apps / system proxy
	SSEntry    int `json:"ss_entry"`    // sing-box SOCKS that goes straight to the SS server (Tor uses it)
	TorSocks   int `json:"tor_socks"`   // Tor SocksPort
	TorControl int `json:"tor_control"` // Tor ControlPort
	TorDNS     int `json:"tor_dns"`     // Tor DNSPort
}

// ShadowTLS obfuscation settings (recommended: makes SS look like TLS to a real site).
type ShadowTLS struct {
	Enabled  bool   `json:"enabled"`
	Password string `json:"password"`
	SNI      string `json:"sni"`
}

// Server describes the Shadowsocks server (your VPS).
type Server struct {
	Address   string    `json:"address"` // IP is strongly preferred over a hostname
	Port      int       `json:"port"`
	Method    string    `json:"method"`
	Password  string    `json:"password"`
	ShadowTLS ShadowTLS `json:"shadowtls"`
}

// Configured reports whether the server block is usable.
func (s Server) Configured() bool {
	return s.Address != "" && s.Port > 0 && s.Password != "" && s.Method != ""
}

// Bridges holds user supplied bridge lines. Built-in bridges are added automatically.
type Bridges struct {
	// Lines are raw bridge lines as handed out by bridges.torproject.org,
	// @GetBridgesBot or bridges@torproject.org, e.g.
	// "webtunnel [2001:db8::1]:443 FINGERPRINT url=https://example.com/xyz ver=0.0.1".
	Lines []string `json:"lines"`
	// UseBuiltin includes the bridges shipped with Tor (snowflake, obfs4, meek).
	UseBuiltin bool `json:"use_builtin"`
}

// Probe settings for connectivity checks.
type Probe struct {
	// TorURLs must answer through Tor; the first is expected to be check.torproject.org.
	TorURLs []string `json:"tor_urls"`
	// PlainURLs are used in ss-only mode.
	PlainURLs []string `json:"plain_urls"`
	// TimeoutSec per request.
	TimeoutSec int `json:"timeout_sec"`
	// IntervalSec between watchdog probes while connected.
	IntervalSec int `json:"interval_sec"`
	// FailuresBeforeReconnect consecutive failures before the engine reconnects.
	FailuresBeforeReconnect int `json:"failures_before_reconnect"`
}

// Config is the on-disk configuration (config.json).
type Config struct {
	Mode        Mode   `json:"mode"`
	ModeOrder   []Mode `json:"mode_order"`
	AllowSSOnly bool   `json:"allow_ss_only"`
	AutoConnect bool   `json:"autoconnect"`

	// TUN: system-wide VPN via a virtual adapter (needs administrator rights).
	// false: only a local proxy (127.0.0.1:<mixed>) and, if SystemProxy, the Windows system proxy.
	TUN         bool `json:"tun"`
	SystemProxy bool `json:"system_proxy"`
	KillSwitch  bool `json:"kill_switch"`

	Ports   Ports   `json:"ports"`
	Server  Server  `json:"server"`
	Bridges Bridges `json:"bridges"`
	Probe   Probe   `json:"probe"`

	LogLevel string `json:"log_level"` // sing-box / tor verbosity: "info" or "debug"
}

// Default returns a configuration that works out of the box (Tor bridges only,
// no server), with the RU-oriented mode order.
func Default() *Config {
	return &Config{
		Mode: ModeAuto,
		ModeOrder: []Mode{
			ModeSSTor, ModeTorWebtunnel, ModeTorSnowflake,
			ModeTorObfs4, ModeTorMeek, ModeSSOnly,
		},
		AllowSSOnly: true,
		AutoConnect: true,
		TUN:         true,
		SystemProxy: true,
		KillSwitch:  false,
		Ports: Ports{
			Mixed:      2080,
			SSEntry:    2081,
			TorSocks:   9050,
			TorControl: 9051,
			TorDNS:     9053,
		},
		Server: Server{
			Port:   443,
			Method: "2022-blake3-aes-128-gcm",
			ShadowTLS: ShadowTLS{
				Enabled: true,
				SNI:     "gateway.icloud.com",
			},
		},
		Bridges: Bridges{UseBuiltin: true},
		Probe: Probe{
			TorURLs: []string{
				"https://check.torproject.org/api/ip",
				"https://www.cloudflare.com/cdn-cgi/trace",
			},
			PlainURLs: []string{
				"https://www.cloudflare.com/cdn-cgi/trace",
				"https://www.google.com/generate_204",
			},
			TimeoutSec:              25,
			IntervalSec:             20,
			FailuresBeforeReconnect: 3,
		},
		LogLevel: "info",
	}
}

// Load reads path; if it does not exist, writes Default() there and returns it.
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		c := Default()
		if werr := c.Save(path); werr != nil {
			return nil, werr
		}
		return c, nil
	}
	if err != nil {
		return nil, err
	}
	c := Default()
	if err := json.Unmarshal(data, c); err != nil {
		return nil, fmt.Errorf("config %s: %w", path, err)
	}
	if err := c.Validate(); err != nil {
		return nil, fmt.Errorf("config %s: %w", path, err)
	}
	return c, nil
}

// Save writes the config as indented JSON.
func (c *Config) Save(path string) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, append(data, '\n'), 0o600)
}

// Validate checks the config for obvious mistakes and normalises it.
func (c *Config) Validate() error {
	if !c.Mode.Valid() {
		return fmt.Errorf("unknown mode %q", c.Mode)
	}
	if len(c.ModeOrder) == 0 {
		c.ModeOrder = Default().ModeOrder
	}
	for _, m := range c.ModeOrder {
		if !m.Valid() || m == ModeAuto {
			return fmt.Errorf("mode_order contains invalid mode %q", m)
		}
	}
	d := Default()
	if c.Ports.Mixed == 0 {
		c.Ports.Mixed = d.Ports.Mixed
	}
	if c.Ports.SSEntry == 0 {
		c.Ports.SSEntry = d.Ports.SSEntry
	}
	if c.Ports.TorSocks == 0 {
		c.Ports.TorSocks = d.Ports.TorSocks
	}
	if c.Ports.TorControl == 0 {
		c.Ports.TorControl = d.Ports.TorControl
	}
	if c.Ports.TorDNS == 0 {
		c.Ports.TorDNS = d.Ports.TorDNS
	}
	seen := map[int]string{}
	for name, p := range map[string]int{
		"mixed": c.Ports.Mixed, "ss_entry": c.Ports.SSEntry, "tor_socks": c.Ports.TorSocks,
		"tor_control": c.Ports.TorControl, "tor_dns": c.Ports.TorDNS,
	} {
		if p < 1 || p > 65535 {
			return fmt.Errorf("port %s out of range: %d", name, p)
		}
		if other, dup := seen[p]; dup {
			return fmt.Errorf("ports %s and %s both use %d", name, other, p)
		}
		seen[p] = name
	}
	if c.Probe.TimeoutSec <= 0 {
		c.Probe.TimeoutSec = d.Probe.TimeoutSec
	}
	if c.Probe.IntervalSec <= 0 {
		c.Probe.IntervalSec = d.Probe.IntervalSec
	}
	if c.Probe.FailuresBeforeReconnect <= 0 {
		c.Probe.FailuresBeforeReconnect = d.Probe.FailuresBeforeReconnect
	}
	if len(c.Probe.TorURLs) == 0 {
		c.Probe.TorURLs = d.Probe.TorURLs
	}
	if len(c.Probe.PlainURLs) == 0 {
		c.Probe.PlainURLs = d.Probe.PlainURLs
	}
	if c.Server.Address != "" && c.Server.Port == 0 {
		c.Server.Port = 443
	}
	if c.Server.ShadowTLS.Enabled && c.Server.Configured() && c.Server.ShadowTLS.Password == "" {
		return errors.New("server.shadowtls.enabled is true but shadowtls.password is empty")
	}
	if c.Server.ShadowTLS.Enabled && c.Server.ShadowTLS.SNI == "" {
		c.Server.ShadowTLS.SNI = d.Server.ShadowTLS.SNI
	}
	if c.LogLevel == "" {
		c.LogLevel = "info"
	}
	c.LogLevel = strings.ToLower(c.LogLevel)
	return nil
}

// State is persisted runtime state (state.json), separate from the user config.
type State struct {
	LastGoodMode Mode `json:"last_good_mode,omitempty"`
	// SysProxyBackup remembers the Windows proxy settings we replaced.
	SysProxyBackup *SysProxyBackup `json:"sysproxy_backup,omitempty"`
	// KillSwitchActive is true while our firewall policy is applied; used to
	// clean up after a crash.
	KillSwitchActive bool `json:"kill_switch_active,omitempty"`
}

// SysProxyBackup is a copy of the WinINET proxy registry values.
type SysProxyBackup struct {
	Enable   uint32 `json:"enable"`
	Server   string `json:"server"`
	Override string `json:"override"`
}

// LoadState reads state.json, returning an empty state if missing.
func LoadState(path string) *State {
	s := &State{}
	data, err := os.ReadFile(path)
	if err != nil {
		return s
	}
	_ = json.Unmarshal(data, s)
	return s
}

// Save writes state.json.
func (s *State) Save(path string) error {
	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o600)
}
