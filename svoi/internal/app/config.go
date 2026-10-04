// Package app wires the node, the file/mail/service managers, the persistent
// settings and the event hub into one object the HTTP API and the CLI use.
package app

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync"

	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
)

// DefaultSTUN are the public STUN servers used to learn our public address.
// STUN is stateless and anonymous (it only echoes back the address a packet came
// from); it can be switched off in the settings, in which case members learn our
// address from each other instead.
var DefaultSTUN = []string{"stun.l.google.com:19302", "stun.cloudflare.com:3478"}

// SocksSettings configures the SOCKS5 proxy.
type SocksSettings struct {
	Enabled bool   `json:"enabled"`
	Listen  string `json:"listen"`
}

// TUNSettings configures the virtual network interface.
type TUNSettings struct {
	Enabled     bool `json:"enabled"`
	ManageHosts bool `json:"manageHosts"`
}

// Config is the persisted configuration of this device (config.json).
type Config struct {
	DownloadDir     string             `json:"downloadDir"`
	AutoAccept      string             `json:"autoAccept"` // own | all | ask
	AutoAcceptMaxMB int                `json:"autoAcceptMaxMB"`
	Relay           bool               `json:"relay"`
	STUNEnabled     bool               `json:"stunEnabled"`
	STUNServers     []string           `json:"stunServers"`
	UDPPort         int                `json:"udpPort"`
	LAN             bool               `json:"lan"`
	Nearby          bool               `json:"nearby"`  // an admin device tells the devices around that it can add them (see mesh/nearby.go)
	PortMap         bool               `json:"portMap"` // ask the home router (UPnP / NAT-PMP) to forward our UDP port
	Socks           SocksSettings      `json:"socks"`
	TUN             TUNSettings        `json:"tun"`
	Shares          []files.Share      `json:"shares"`
	Services        []services.Service `json:"services"`
	Forwards        []services.Forward `json:"forwards"`
}

func defaultConfig(dataDir string) Config {
	dl := filepath.Join(dataDir, "downloads")
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		dl = filepath.Join(home, "Downloads", "The Mesh")
	}
	return Config{
		DownloadDir: dl,
		AutoAccept:  "own",
		Relay:       true,
		PortMap:     true,
		STUNEnabled: true,
		STUNServers: append([]string(nil), DefaultSTUN...),
		LAN:         true,
		Nearby:      true,
		Socks:       SocksSettings{Listen: "127.0.0.1:1080"},
		Shares:      []files.Share{},
		Services:    []services.Service{},
		Forwards:    []services.Forward{},
	}
}

// configStore is a mutex-protected Config persisted atomically.
type configStore struct {
	path string
	mu   sync.RWMutex
	cfg  Config
}

func loadConfig(dataDir string) (*configStore, error) {
	cs := &configStore{path: filepath.Join(dataDir, "config.json"), cfg: defaultConfig(dataDir)}
	raw, err := os.ReadFile(cs.path)
	if errors.Is(err, os.ErrNotExist) {
		return cs, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(raw, &cs.cfg); err != nil {
		return nil, fmt.Errorf("app: %s is corrupt: %w", cs.path, err)
	}
	if cs.cfg.Shares == nil {
		cs.cfg.Shares = []files.Share{}
	}
	if cs.cfg.Services == nil {
		cs.cfg.Services = []services.Service{}
	}
	if cs.cfg.Forwards == nil {
		cs.cfg.Forwards = []services.Forward{}
	}
	if cs.cfg.DownloadDir == "" {
		cs.cfg.DownloadDir = defaultConfig(dataDir).DownloadDir
	}
	if cs.cfg.AutoAccept == "" {
		cs.cfg.AutoAccept = "own"
	}
	return cs, nil
}

// Get returns a copy of the configuration.
func (c *configStore) Get() Config {
	c.mu.RLock()
	defer c.mu.RUnlock()
	cp := c.cfg
	cp.STUNServers = append([]string(nil), c.cfg.STUNServers...)
	cp.Shares = append([]files.Share(nil), c.cfg.Shares...)
	cp.Services = append([]services.Service(nil), c.cfg.Services...)
	cp.Forwards = append([]services.Forward(nil), c.cfg.Forwards...)
	return cp
}

// Update applies f under the lock and persists the result.
func (c *configStore) Update(f func(*Config) error) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	next := c.cfg
	if err := f(&next); err != nil {
		return err
	}
	raw, err := json.MarshalIndent(next, "", " ")
	if err != nil {
		return err
	}
	if err := identity.WriteFileAtomic(c.path, raw, 0o600); err != nil {
		return err
	}
	c.cfg = next
	return nil
}

func validHostPort(s string) bool {
	h, p, err := net.SplitHostPort(s)
	return err == nil && h != "" && p != ""
}
