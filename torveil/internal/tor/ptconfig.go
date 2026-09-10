package tor

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

// PTConfig is the part of pt_config.json TorVeil reads.
//
// The Tor Project ships this file beside the pluggable transports and keeps
// the bridge lines in it current. Reading it beats compiling a list into
// TorVeil: Snowflake's fronting domains and STUN servers get rotated, and a
// stale line does not fail loudly — it just never bootstraps.
type PTConfig struct {
	Bridges map[string][]string `json:"bridges"`
}

// LoadPTConfig reads pt_config.json.
func LoadPTConfig(path string) (*PTConfig, error) {
	if path == "" {
		return nil, fmt.Errorf("no pt_config.json was found")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	var cfg PTConfig
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, fmt.Errorf("parse %s: %w", path, err)
	}
	return &cfg, nil
}

// BridgesFor returns the recommended bridge lines for a transport, keeping
// only those that actually parse and name the transport asked for.
func (c *PTConfig) BridgesFor(transport string) []string {
	if c == nil {
		return nil
	}
	want := strings.ToLower(strings.TrimSpace(transport))
	var out []string
	for _, line := range c.Bridges[want] {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		parsed, err := ParseBridgeLine(line)
		if err != nil || !strings.EqualFold(parsed.Transport, want) {
			continue
		}
		out = append(out, line)
	}
	return out
}

// RecommendedBridges returns the bridge lines for a transport, preferring the
// ones shipped alongside the transports and falling back to TorVeil's own
// built-in list when no pt_config.json is available.
func RecommendedBridges(ptConfigPath, transport string) []string {
	if cfg, err := LoadPTConfig(ptConfigPath); err == nil {
		if lines := cfg.BridgesFor(transport); len(lines) > 0 {
			return lines
		}
	}
	if strings.EqualFold(transport, string(TransportSnowflake)) {
		return DefaultSnowflakeBridges
	}
	return nil
}
