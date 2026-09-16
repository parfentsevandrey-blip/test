// Package bridges knows the built-in Tor bridges and classifies bridge lines
// by pluggable transport.
package bridges

import (
	_ "embed"
	"encoding/json"
	"os"
	"strings"
)

// pt_config.json is the file Tor Browser / the Tor Expert Bundle ship with the
// current built-in bridges. The embedded copy is a fallback; if the bundle in
// bin/tor/pluggable_transports/ has a newer one we prefer it.
//
//go:embed pt_config.json
var embeddedPTConfig []byte

type ptConfig struct {
	Bridges map[string][]string `json:"bridges"`
}

// Transport returns the transport name of a bridge line ("obfs4", "webtunnel",
// "snowflake", "meek_lite", "conjure") or "" for a plain "IP:port fingerprint" bridge.
func Transport(line string) string {
	f := strings.Fields(strings.TrimSpace(line))
	if len(f) == 0 {
		return ""
	}
	first := strings.ToLower(f[0])
	if first == "bridge" && len(f) > 1 { // tolerate "Bridge obfs4 ..." copy-paste
		first = strings.ToLower(f[1])
	}
	switch first {
	case "obfs4", "webtunnel", "snowflake", "meek_lite", "conjure", "meek", "obfs3", "scramblesuit":
		if first == "meek" {
			return "meek_lite"
		}
		return first
	}
	return ""
}

// Normalize strips a leading "Bridge " keyword, comments and blank lines.
func Normalize(lines []string) []string {
	var out []string
	for _, l := range lines {
		l = strings.TrimSpace(l)
		if l == "" || strings.HasPrefix(l, "#") {
			continue
		}
		if f := strings.Fields(l); len(f) > 1 && strings.EqualFold(f[0], "bridge") {
			l = strings.Join(f[1:], " ")
		}
		out = append(out, l)
	}
	return out
}

// Builtin returns the built-in bridge lines grouped by transport, reading
// ptConfigPath if it exists and falling back to the embedded copy.
func Builtin(ptConfigPath string) map[string][]string {
	var cfg ptConfig
	if ptConfigPath != "" {
		if data, err := os.ReadFile(ptConfigPath); err == nil {
			if json.Unmarshal(data, &cfg) == nil && len(cfg.Bridges) > 0 {
				return normaliseKeys(cfg.Bridges)
			}
		}
	}
	cfg = ptConfig{}
	_ = json.Unmarshal(embeddedPTConfig, &cfg)
	return normaliseKeys(cfg.Bridges)
}

func normaliseKeys(m map[string][]string) map[string][]string {
	out := map[string][]string{}
	for k, v := range m {
		k = strings.ToLower(k)
		if k == "meek" {
			k = "meek_lite"
		}
		out[k] = append(out[k], Normalize(v)...)
	}
	return out
}

// ForTransport returns the bridge lines to use for the given transport: the
// user's lines of that transport first, then built-in ones if useBuiltin.
func ForTransport(transport string, userLines []string, builtin map[string][]string, useBuiltin bool) []string {
	var out []string
	seen := map[string]bool{}
	add := func(l string) {
		if !seen[l] {
			seen[l] = true
			out = append(out, l)
		}
	}
	for _, l := range Normalize(userLines) {
		if Transport(l) == transport {
			add(l)
		}
	}
	if useBuiltin {
		for _, l := range builtin[transport] {
			add(l)
		}
	}
	return out
}

// UserTransports lists the transports present in the user's bridge lines.
func UserTransports(userLines []string) map[string]bool {
	out := map[string]bool{}
	for _, l := range Normalize(userLines) {
		if t := Transport(l); t != "" {
			out[t] = true
		}
	}
	return out
}
