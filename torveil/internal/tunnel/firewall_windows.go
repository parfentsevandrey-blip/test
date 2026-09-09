//go:build windows

package tunnel

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
)

// Rule names. They are also the handle used to delete them, so they must stay
// stable across versions or an upgrade would orphan rules.
const (
	rulePrefix    = "TorVeil - "
	ruleBlockIPv6 = rulePrefix + "Block IPv6"
	ruleBlockQUIC = rulePrefix + "Block QUIC (UDP 443)"
	ruleBlockDNSU = rulePrefix + "Block plain DNS (UDP)"
	ruleBlockDNST = rulePrefix + "Block plain DNS (TCP)"
	ruleLockdown  = rulePrefix + "Lockdown block all"
	ruleAllowPfx  = rulePrefix + "Allow "
	journalName   = "firewall-state.json"
)

// journal records which firewall changes are outstanding, so a process that
// dies without cleaning up can be undone on the next start rather than leaving
// the machine in a state the user cannot explain.
type journal struct {
	Rules          []string `json:"rules"`
	PolicyModified bool     `json:"policyModified"`
	PreviousPolicy string   `json:"previousPolicy"`
}

// firewall drives Windows Firewall through netsh.
//
// netsh is used rather than the Windows Filtering Platform API because every
// rule it creates is visible and removable in the Windows Firewall UI. A
// privacy tool that silently installs invisible packet filters is harder to
// trust and harder to recover from.
type firewall struct {
	log     LogFunc
	dataDir string

	mu    sync.Mutex
	added []string
	entry journal
}

func newFirewall(dataDir string, log LogFunc) *firewall {
	return &firewall{log: log, dataDir: dataDir}
}

func (f *firewall) logf(level, format string, args ...any) {
	if f.log != nil {
		f.log(level, fmt.Sprintf(format, args...))
	}
}

func (f *firewall) journalPath() string {
	return filepath.Join(f.dataDir, journalName)
}

func (f *firewall) saveJournal() {
	if f.dataDir == "" {
		return
	}
	if err := os.MkdirAll(f.dataDir, 0o700); err != nil {
		return
	}
	f.entry.Rules = append([]string(nil), f.added...)
	raw, err := json.MarshalIndent(f.entry, "", "  ")
	if err != nil {
		return
	}
	_ = os.WriteFile(f.journalPath(), raw, 0o600)
}

func (f *firewall) clearJournal() {
	if f.dataDir == "" {
		return
	}
	_ = os.Remove(f.journalPath())
}

// ApplyLeakRules installs the block rules that stay in force while the tunnel
// is up.
//
// These are blocks only, never a default-deny: application traffic destined
// for the tunnel is still ordinary outbound traffic as far as Windows Firewall
// is concerned, so a default-deny policy would block the very traffic the
// tunnel exists to carry.
func (f *firewall) ApplyLeakRules(opts Options) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	var errs []string

	if opts.BlockIPv6 {
		// Tor's exits are reached over IPv4 and the tunnel carries no IPv6,
		// so an IPv6-capable machine would otherwise reach any IPv6-reachable
		// site straight past the tunnel.
		if err := f.addRule(ruleBlockIPv6, "dir=out", "action=block", "remoteip=::/0", "protocol=any"); err != nil {
			errs = append(errs, err.Error())
		}
	}
	if opts.BlockQUIC {
		// Tor carries no UDP. Blocking QUIC makes browsers fall back to TCP,
		// which the tunnel can carry, instead of stalling.
		if err := f.addRule(ruleBlockQUIC, "dir=out", "action=block", "protocol=udp", "remoteport=443"); err != nil {
			errs = append(errs, err.Error())
		}
	}

	// DNS must reach Tor through the loopback forwarder. Windows Firewall does
	// not filter loopback, so blocking DNS to remote addresses stops queries
	// leaking to the ISP's resolver without touching the forwarder.
	if err := f.addRule(ruleBlockDNSU, "dir=out", "action=block", "protocol=udp", "remoteport=53"); err != nil {
		errs = append(errs, err.Error())
	}
	if err := f.addRule(ruleBlockDNST, "dir=out", "action=block", "protocol=tcp", "remoteport=53"); err != nil {
		errs = append(errs, err.Error())
	}

	f.saveJournal()
	if len(errs) > 0 {
		return fmt.Errorf("apply leak-protection rules: %s", strings.Join(errs, "; "))
	}
	return nil
}

// EngageLockdown blocks all outbound traffic except the listed programs.
//
// This is the kill switch, and it applies when the tunnel is *not* up. While
// the tunnel is running, routing is what keeps traffic inside it; the danger
// is the moment the adapter disappears, because the routes vanish with it and
// the machine silently falls back to the open network. Locking down at that
// moment turns a silent leak into a visible loss of connectivity.
func (f *firewall) EngageLockdown(allowPrograms []string) error {
	f.mu.Lock()
	defer f.mu.Unlock()

	prev, err := f.currentOutboundPolicy()
	if err != nil {
		f.logf("warn", "read firewall policy: %v", err)
		prev = "allowoutbound"
	}

	for _, p := range allowPrograms {
		if p == "" {
			continue
		}
		name := ruleAllowPfx + filepath.Base(p)
		if err := f.addRule(name, "dir=out", "action=allow", "program="+p, "enable=yes"); err != nil {
			f.logf("warn", "allow %s through the kill switch: %v", p, err)
		}
	}

	if err := f.addRule(ruleLockdown, "dir=out", "action=block", "protocol=any", "remoteip=0.0.0.0/0"); err != nil {
		return fmt.Errorf("engage kill switch: %w", err)
	}
	if err := f.addRule(ruleLockdown+" (IPv6)", "dir=out", "action=block", "protocol=any", "remoteip=::/0"); err != nil {
		f.logf("warn", "kill switch IPv6 rule: %v", err)
	}

	f.entry.PolicyModified = false
	f.entry.PreviousPolicy = prev
	f.saveJournal()
	f.logf("warn", "kill switch engaged: outbound traffic is blocked until the tunnel comes back or the kill switch is cleared")
	return nil
}

// Clear removes every rule TorVeil added and restores the outbound policy.
func (f *firewall) Clear() {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.clearLocked()
}

func (f *firewall) clearLocked() {
	for i := len(f.added) - 1; i >= 0; i-- {
		f.deleteRule(f.added[i])
	}
	f.added = nil

	if f.entry.PolicyModified && f.entry.PreviousPolicy != "" {
		if err := f.setOutboundPolicy(f.entry.PreviousPolicy); err != nil {
			f.logf("error", "restore firewall policy to %q: %v", f.entry.PreviousPolicy, err)
		}
	}
	f.entry = journal{}
	f.clearJournal()
}

func (f *firewall) addRule(name string, args ...string) error {
	full := append([]string{"advfirewall", "firewall", "add", "rule", "name=" + name}, args...)
	if out, err := runNetsh(full...); err != nil {
		return fmt.Errorf("netsh %s: %w (%s)", name, err, strings.TrimSpace(out))
	}
	f.added = append(f.added, name)
	return nil
}

func (f *firewall) deleteRule(name string) {
	if out, err := runNetsh("advfirewall", "firewall", "delete", "rule", "name="+name); err != nil {
		// "No rules match" is the normal result when a rule was never added.
		if !strings.Contains(strings.ToLower(out), "no rules match") {
			f.logf("info", "remove firewall rule %q: %v", name, err)
		}
	}
}

func (f *firewall) currentOutboundPolicy() (string, error) {
	out, err := runNetsh("advfirewall", "show", "currentprofile", "firewallpolicy")
	if err != nil {
		return "", err
	}
	lower := strings.ToLower(out)
	if strings.Contains(lower, "blockoutbound") {
		return "blockoutbound", nil
	}
	return "allowoutbound", nil
}

func (f *firewall) setOutboundPolicy(policy string) error {
	_, err := runNetsh("advfirewall", "set", "allprofiles", "firewallpolicy", "blockinbound,"+policy)
	return err
}

// RecoverStaleState removes firewall changes left behind by a previous run
// that did not shut down cleanly.
func RecoverStaleState(dataDir string, log LogFunc) {
	f := newFirewall(dataDir, log)
	raw, err := os.ReadFile(f.journalPath())
	if err != nil {
		return
	}
	var j journal
	if err := json.Unmarshal(raw, &j); err != nil {
		_ = os.Remove(f.journalPath())
		return
	}
	if len(j.Rules) == 0 && !j.PolicyModified {
		_ = os.Remove(f.journalPath())
		return
	}
	f.logf("warn", "cleaning up %d firewall rule(s) left by a previous session", len(j.Rules))
	f.added = j.Rules
	f.entry = j
	f.mu.Lock()
	f.clearLocked()
	f.mu.Unlock()
}

// runNetsh executes netsh without flashing a console window.
func runNetsh(args ...string) (string, error) {
	cmd := exec.Command("netsh", args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	out, err := cmd.CombinedOutput()
	return string(out), err
}
