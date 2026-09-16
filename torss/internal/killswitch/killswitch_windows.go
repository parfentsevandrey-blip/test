//go:build windows

// Package killswitch blocks all outbound traffic except our tunnel binaries
// using the Windows Firewall, so nothing leaks if the tunnel drops.
package killswitch

import (
	"context"
	"fmt"
	"strings"
	"time"

	"torss/internal/proc"
)

const rulePrefix = "TorSS-KillSwitch"

func netsh(ctx context.Context, args ...string) error {
	_, err := proc.Run(ctx, "netsh", args...)
	return err
}

// Enable switches the firewall to block-outbound and allows only the given
// program paths (sing-box, tor, pluggable transports) plus DHCP.
func Enable(programs []string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	// Clean leftovers from a previous run first.
	_ = deleteRules(ctx)
	for i, p := range programs {
		name := fmt.Sprintf("%s-prog-%d", rulePrefix, i)
		if err := netsh(ctx, "advfirewall", "firewall", "add", "rule",
			"name="+name, "dir=out", "action=allow", "enable=yes", "program="+p); err != nil {
			return err
		}
	}
	if err := netsh(ctx, "advfirewall", "firewall", "add", "rule",
		"name="+rulePrefix+"-dhcp", "dir=out", "action=allow", "enable=yes",
		"protocol=UDP", "localport=68", "remoteport=67"); err != nil {
		return err
	}
	// Loopback is not filtered by the Windows Firewall, so the local proxy
	// ports keep working for every application.
	return netsh(ctx, "advfirewall", "set", "allprofiles", "firewallpolicy", "blockinbound,blockoutbound")
}

// Disable restores the default outbound policy and removes our rules.
func Disable() error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	err := netsh(ctx, "advfirewall", "set", "allprofiles", "firewallpolicy", "blockinbound,allowoutbound")
	if derr := deleteRules(ctx); derr != nil && err == nil {
		err = derr
	}
	return err
}

func deleteRules(ctx context.Context) error {
	out, _ := proc.Run(ctx, "netsh", "advfirewall", "firewall", "show", "rule", "name=all", "dir=out")
	var names []string
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if i := strings.Index(line, rulePrefix); i >= 0 {
			names = append(names, strings.TrimSpace(line[i:]))
		}
	}
	seen := map[string]bool{}
	for _, n := range names {
		if seen[n] {
			continue
		}
		seen[n] = true
		_ = netsh(ctx, "advfirewall", "firewall", "delete", "rule", "name="+n)
	}
	return nil
}
