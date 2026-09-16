// Package singbox generates sing-box configurations and runs sing-box.
//
// sing-box does three jobs for us:
//   - the Shadowsocks (+ShadowTLS) client to your VPS;
//   - the TUN adapter that captures all Windows traffic (VPN mode) plus a
//     local HTTP/SOCKS proxy (proxy mode);
//   - DNS: fake-IP so that domain names travel to Tor / the SS server and are
//     resolved remotely (no DNS leaks).
package singbox

import (
	"encoding/json"
	"errors"
	"net"

	"torss/internal/config"
)

// Options for Generate.
type Options struct {
	Mode     config.Mode
	TUN      bool
	Ports    config.Ports
	Server   config.Server
	LogLevel string
	CacheDB  string // path of cache.db (fake-IP persistence), "" to disable
	// ExcludeProcesses: Windows process names whose traffic must bypass the TUN
	// (tor.exe and its pluggable transports), otherwise they would loop.
	ExcludeProcesses []string
}

// M is a JSON object.
type M = map[string]any

// Generate renders the sing-box JSON config for the given mode.
func Generate(o Options) ([]byte, error) {
	if o.Mode == config.ModeAuto {
		return nil, errors.New("cannot generate config for mode auto")
	}
	useSS := o.Server.Configured()
	if o.Mode.UsesSS() && !useSS {
		return nil, errors.New("mode requires a configured server")
	}
	level := "warn"
	if o.LogLevel == "debug" {
		level = "debug"
	} else if o.LogLevel == "info" {
		level = "info"
	}

	var finalOutbound string
	if o.Mode.UsesTor() {
		finalOutbound = "tor"
	} else {
		finalOutbound = "ss"
	}

	// ---- outbounds ------------------------------------------------------
	outbounds := []M{}
	if o.Mode.UsesTor() {
		outbounds = append(outbounds, M{
			"type":        "socks",
			"tag":         "tor",
			"server":      "127.0.0.1",
			"server_port": o.Ports.TorSocks,
			"version":     "5",
		})
	}
	if useSS {
		ss := M{
			"type":        "shadowsocks",
			"tag":         "ss",
			"server":      o.Server.Address,
			"server_port": o.Server.Port,
			"method":      o.Server.Method,
			"password":    o.Server.Password,
		}
		if o.Server.ShadowTLS.Enabled {
			// ShadowTLS carries only TCP; UDP-over-TCP keeps UDP (DNS, QUIC) working.
			ss["udp_over_tcp"] = true
			ss["detour"] = "shadowtls"
			outbounds = append(outbounds, M{
				"type":        "shadowtls",
				"tag":         "shadowtls",
				"server":      o.Server.Address,
				"server_port": o.Server.Port,
				"version":     3,
				"password":    o.Server.ShadowTLS.Password,
				"tls": M{
					"enabled":     true,
					"server_name": o.Server.ShadowTLS.SNI,
					"utls": M{
						"enabled":     true,
						"fingerprint": "chrome",
					},
				},
			})
		}
		outbounds = append(outbounds, ss)
	}
	outbounds = append(outbounds, M{"type": "direct", "tag": "direct"})

	// ---- inbounds -------------------------------------------------------
	inbounds := []M{
		{
			"type":        "mixed",
			"tag":         "local-in",
			"listen":      "127.0.0.1",
			"listen_port": o.Ports.Mixed,
		},
	}
	if useSS {
		inbounds = append(inbounds, M{
			"type":        "socks",
			"tag":         "ss-entry",
			"listen":      "127.0.0.1",
			"listen_port": o.Ports.SSEntry,
		})
	}
	privateNets := []string{
		"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
		"127.0.0.0/8", "224.0.0.0/4", "255.255.255.255/32",
		"fe80::/10", "fc00::/7", "ff00::/8",
	}
	if o.TUN {
		exclude := append([]string{}, privateNets...)
		if ip := net.ParseIP(o.Server.Address); useSS && ip != nil {
			if ip.To4() != nil {
				exclude = append(exclude, ip.String()+"/32")
			} else {
				exclude = append(exclude, ip.String()+"/128")
			}
		}
		inbounds = append(inbounds, M{
			"type":                  "tun",
			"tag":                   "tun-in",
			"interface_name":        "TorSS",
			"address":               []string{"172.19.0.1/30", "fdfe:dcba:9876::1/126"},
			"mtu":                   1400,
			"auto_route":            true,
			"strict_route":          true,
			"stack":                 "gvisor",
			"route_exclude_address": exclude,
		})
	}

	// ---- dns ------------------------------------------------------------
	// A/AAAA -> fake IP, so the real name reaches Tor / SS and is resolved at
	// the far end. Everything else (HTTPS/SVCB records etc.) goes over the
	// tunnel to a public resolver, never to the ISP.
	dnsServers := []M{
		{
			"type":        "fakeip",
			"tag":         "fakeip",
			"inet4_range": "198.18.0.0/15",
			"inet6_range": "fc00::/18",
		},
		{
			"type":   "https",
			"tag":    "remote-dns",
			"server": "8.8.8.8",
			"detour": finalOutbound,
		},
		{
			"type": "local",
			"tag":  "local-dns",
		},
	}
	dnsRules := []M{}
	if len(o.ExcludeProcesses) > 0 {
		// Tor / lyrebird resolve bridge hosts themselves: give them real answers.
		dnsRules = append(dnsRules, M{
			"process_name": o.ExcludeProcesses,
			"server":       "local-dns",
		})
	}
	dnsRules = append(dnsRules, M{
		"query_type": []string{"A", "AAAA"},
		"server":     "fakeip",
	})
	dns := M{
		"servers": dnsServers,
		"rules":   dnsRules,
		"final":   "remote-dns",
	}

	// ---- route ----------------------------------------------------------
	rules := []M{
		{"action": "sniff"},
		{"protocol": "dns", "action": "hijack-dns"},
	}
	if len(o.ExcludeProcesses) > 0 {
		rules = append(rules, M{"process_name": o.ExcludeProcesses, "outbound": "direct"})
	}
	if useSS {
		rules = append(rules, M{"inbound": []string{"ss-entry"}, "outbound": "ss"})
	}
	rules = append(rules, M{"ip_cidr": privateNets, "outbound": "direct"})
	if o.Mode.UsesTor() {
		// Tor carries TCP only. Reject UDP fast so browsers fall back from QUIC
		// to TCP immediately instead of waiting for timeouts.
		rules = append(rules, M{"network": "udp", "action": "reject"})
	}
	route := M{
		"rules":                   rules,
		"final":                   finalOutbound,
		"auto_detect_interface":   true,
		"default_domain_resolver": "local-dns",
	}

	cfg := M{
		"log":       M{"level": level, "timestamp": true},
		"dns":       dns,
		"inbounds":  inbounds,
		"outbounds": outbounds,
		"route":     route,
	}
	if o.CacheDB != "" {
		cfg["experimental"] = M{
			"cache_file": M{
				"enabled":      true,
				"path":         o.CacheDB,
				"store_fakeip": true,
			},
		}
	}
	return json.MarshalIndent(cfg, "", "  ")
}
