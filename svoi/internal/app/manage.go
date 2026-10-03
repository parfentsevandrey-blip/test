package app

import (
	"context"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
	"github.com/parfentsevandrey-blip/test/svoi/internal/tun"
)

// ---- settings ----

// Settings is the user-visible subset of Config (see docs/UI-API.md).
type Settings struct {
	DownloadDir     string        `json:"downloadDir"`
	AutoAccept      string        `json:"autoAccept"`
	AutoAcceptMaxMB int           `json:"autoAcceptMaxMB"`
	Relay           bool          `json:"relay"`
	STUNEnabled     bool          `json:"stunEnabled"`
	STUNServers     []string      `json:"stunServers"`
	UDPPort         int           `json:"udpPort"`
	LAN             bool          `json:"lan"`
	PortMap         bool          `json:"portMap"`
	Socks           SocksSettings `json:"socks"`
	TUN             TUNView       `json:"tun"`
	RestartRequired bool          `json:"restartRequired"`
}

// TUNView is the TUN configuration together with its live status.
type TUNView struct {
	Enabled     bool `json:"enabled"`
	ManageHosts bool `json:"manageHosts"`
	tun.Status
}

// TUNPatch updates the TUN configuration.
type TUNPatch struct {
	Enabled     *bool `json:"enabled"`
	ManageHosts *bool `json:"manageHosts"`
}

// SettingsPatch updates any subset of Settings.
type SettingsPatch struct {
	DownloadDir     *string        `json:"downloadDir"`
	AutoAccept      *string        `json:"autoAccept"`
	AutoAcceptMaxMB *int           `json:"autoAcceptMaxMB"`
	Relay           *bool          `json:"relay"`
	STUNEnabled     *bool          `json:"stunEnabled"`
	STUNServers     *[]string      `json:"stunServers"`
	UDPPort         *int           `json:"udpPort"`
	LAN             *bool          `json:"lan"`
	PortMap         *bool          `json:"portMap"`
	Socks           *SocksSettings `json:"socks"`
	TUN             *TUNPatch      `json:"tun"`
}

// Settings returns the current settings.
func (a *App) Settings() Settings {
	c := a.cfg.Get()
	s := Settings{
		DownloadDir: c.DownloadDir, AutoAccept: c.AutoAccept, AutoAcceptMaxMB: c.AutoAcceptMaxMB,
		Relay: c.Relay, STUNEnabled: c.STUNEnabled, STUNServers: c.STUNServers, UDPPort: c.UDPPort,
		LAN: c.LAN, PortMap: c.PortMap, Socks: c.Socks,
		TUN: TUNView{Enabled: c.TUN.Enabled, ManageHosts: c.TUN.ManageHosts, Status: a.tun.Status()},
	}
	if s.STUNServers == nil {
		s.STUNServers = []string{}
	}
	if s.UDPPort == 0 {
		s.UDPPort = a.node.Self().UDPPort
	}
	return s
}

// SettingsOption tunes UpdateSettings.
type SettingsOption func(*settingsOpts)

type settingsOpts struct{ deferNetwork bool }

// DeferNetworkRestart makes UpdateSettings apply changes that restart the
// network (UDP port, STUN, LAN discovery) a moment after it returns. A request
// that arrives over the mesh itself (remote administration) needs this: the
// restart would otherwise cut the very link its answer travels on.
func DeferNetworkRestart() SettingsOption { return func(o *settingsOpts) { o.deferNetwork = true } }

// UpdateSettings validates and applies a patch.
func (a *App) UpdateSettings(p SettingsPatch, opts ...SettingsOption) (Settings, error) {
	var so settingsOpts
	for _, o := range opts {
		o(&so)
	}
	before := a.cfg.Get()
	err := a.cfg.Update(func(c *Config) error {
		if p.DownloadDir != nil {
			d := strings.TrimSpace(*p.DownloadDir)
			if d == "" || !filepath.IsAbs(d) {
				return mesh.Errf(mesh.CodeInvalid, "the download folder must be an absolute path")
			}
			c.DownloadDir = filepath.Clean(d)
		}
		if p.AutoAccept != nil {
			switch *p.AutoAccept {
			case "own", "all", "ask":
				c.AutoAccept = *p.AutoAccept
			default:
				return mesh.Errf(mesh.CodeInvalid, "autoAccept must be own, all or ask")
			}
		}
		if p.AutoAcceptMaxMB != nil {
			if *p.AutoAcceptMaxMB < 0 {
				return mesh.Errf(mesh.CodeInvalid, "the size limit cannot be negative")
			}
			c.AutoAcceptMaxMB = *p.AutoAcceptMaxMB
		}
		if p.Relay != nil {
			c.Relay = *p.Relay
		}
		if p.PortMap != nil {
			c.PortMap = *p.PortMap
		}
		if p.STUNEnabled != nil {
			c.STUNEnabled = *p.STUNEnabled
		}
		if p.STUNServers != nil {
			var list []string
			for _, s := range *p.STUNServers {
				s = strings.TrimSpace(s)
				if s == "" {
					continue
				}
				if !validHostPort(s) {
					return mesh.Errf(mesh.CodeInvalid, "STUN server %q must look like host:port", s)
				}
				list = append(list, s)
			}
			c.STUNServers = list
		}
		if p.UDPPort != nil {
			if *p.UDPPort < 0 || *p.UDPPort > 65535 {
				return mesh.Errf(mesh.CodeInvalid, "the port must be between 0 and 65535")
			}
			c.UDPPort = *p.UDPPort
		}
		if p.LAN != nil {
			c.LAN = *p.LAN
		}
		if p.Socks != nil {
			if p.Socks.Listen != "" && !validHostPort(p.Socks.Listen) {
				return mesh.Errf(mesh.CodeInvalid, "the proxy address must look like host:port")
			}
			c.Socks = *p.Socks
			if c.Socks.Listen == "" {
				c.Socks.Listen = "127.0.0.1:1080"
			}
		}
		if p.TUN != nil {
			if p.TUN.Enabled != nil {
				c.TUN.Enabled = *p.TUN.Enabled
			}
			if p.TUN.ManageHosts != nil {
				c.TUN.ManageHosts = *p.TUN.ManageHosts
			}
		}
		return nil
	})
	if err != nil {
		return Settings{}, err
	}
	after := a.cfg.Get()
	if before.Relay != after.Relay {
		a.node.SetRelay(after.Relay) // takes effect at once, links stay up
	}
	restartNet := before.STUNEnabled != after.STUNEnabled ||
		strings.Join(before.STUNServers, ",") != strings.Join(after.STUNServers, ",") ||
		before.UDPPort != after.UDPPort || before.LAN != after.LAN || before.PortMap != after.PortMap
	if restartNet {
		reconf := func() error {
			return a.node.Reconfigure(func(mc *mesh.Config) {
				mc.NoRelay = !after.Relay
				mc.PortMap = after.PortMap
				mc.UDPPort = after.UDPPort
				mc.STUN = nil
				if after.STUNEnabled {
					mc.STUN = after.STUNServers
				}
				mc.LANPort = a.baseNet.LANPort
				if !after.LAN {
					mc.LANPort = -1
				}
			})
		}
		if so.deferNetwork {
			go func() {
				select {
				case <-a.ctx.Done():
					return
				case <-time.After(500 * time.Millisecond): // let the answer leave first
				}
				if err := reconf(); err != nil {
					a.log.Warn("applying the network settings failed", "err", err)
				}
			}()
		} else if err := reconf(); err != nil {
			return Settings{}, err
		}
	}
	if before.Socks != after.Socks {
		a.applySocks()
	}
	if before.TUN != after.TUN {
		a.applyTUN()
		if p.TUN != nil && p.TUN.Enabled != nil && *p.TUN.Enabled {
			if st := a.tun.Status(); st.State == "error" {
				return a.Settings(), mesh.Errf(mesh.CodeUnsupported, "%s", st.Error)
			}
		}
	}
	return a.Settings(), nil
}

// applyTUN starts or stops the virtual interface according to the settings.
func (a *App) applyTUN() {
	c := a.cfg.Get()
	a.tun.SetManageHosts(c.TUN.ManageHosts)
	if c.TUN.Enabled && a.node.Configured() {
		if err := a.tun.Start(); err != nil {
			a.log.Warn("cannot start the TUN interface", "err", err)
		}
		return
	}
	a.tun.Stop()
}

func (a *App) applySocks() {
	c := a.cfg.Get()
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.socks != nil {
		a.socks.Close()
		a.socks = nil
	}
	if !c.Socks.Enabled {
		return
	}
	srv, err := a.svc.ListenSOCKS(a.ctx, c.Socks.Listen)
	if err != nil {
		a.log.Warn("cannot start the SOCKS proxy", "listen", c.Socks.Listen, "err", err)
		return
	}
	a.socks = srv
	a.log.Info("SOCKS5 proxy listening", "addr", srv.Addr())
}

// ---- shared folders ----

// ShareView is a share with a liveness check.
type ShareView struct {
	files.Share
	Exists bool `json:"exists"`
	// Blocked: the folder would expose the device's own keys and is not served.
	Blocked bool `json:"blocked,omitempty"`
}

// Shares lists the folders this device shares.
func (a *App) Shares() []ShareView {
	list := a.cfg.Get().Shares
	out := make([]ShareView, 0, len(list))
	for _, s := range list {
		st, err := os.Stat(s.Path)
		out = append(out, ShareView{Share: s, Exists: err == nil && st.IsDir(), Blocked: a.files.CheckShareRoot(s.Path) != nil})
	}
	return out
}

func normalizeAllow(in []string) ([]string, error) {
	if len(in) == 0 {
		return []string{"*"}, nil
	}
	seen := map[string]bool{}
	var out []string
	for _, s := range in {
		s = strings.TrimSpace(s)
		if s == "*" {
			return []string{"*"}, nil
		}
		id, err := identity.ParseID(s)
		if err != nil {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad device id in the access list")
		}
		if !seen[id.String()] {
			seen[id.String()] = true
			out = append(out, id.String())
		}
	}
	return out, nil
}

// SaveShare creates (id empty) or updates a shared folder.
func (a *App) SaveShare(id string, in files.Share) (ShareView, error) {
	in.Name = strings.TrimSpace(in.Name)
	if in.Name == "" || len(in.Name) > 100 {
		return ShareView{}, mesh.Errf(mesh.CodeInvalid, "give the folder a name (up to 100 characters)")
	}
	if in.Mode != "ro" && in.Mode != "rw" {
		in.Mode = "ro"
	}
	path := strings.TrimSpace(in.Path)
	if !filepath.IsAbs(path) {
		return ShareView{}, mesh.Errf(mesh.CodeInvalid, "the folder path must be absolute")
	}
	path = filepath.Clean(path)
	st, err := os.Stat(path)
	if err != nil || !st.IsDir() {
		return ShareView{}, mesh.Errf(mesh.CodeInvalid, "this folder does not exist on the device")
	}
	if err := a.files.CheckShareRoot(path); err != nil {
		return ShareView{}, mesh.Errf(mesh.CodeInvalid, "%v. Choose a folder that does not contain it", err)
	}
	allow, err := normalizeAllow(in.Allow)
	if err != nil {
		return ShareView{}, err
	}
	in.Path, in.Allow = path, allow
	var saved files.Share
	err = a.cfg.Update(func(c *Config) error {
		if id == "" {
			in.ID = randID("sh_")
			c.Shares = append(c.Shares, in)
			saved = in
			return nil
		}
		for i := range c.Shares {
			if c.Shares[i].ID == id {
				in.ID = id
				c.Shares[i] = in
				saved = in
				return nil
			}
		}
		return mesh.Errf(mesh.CodeNotFound, "no such shared folder")
	})
	if err != nil {
		return ShareView{}, err
	}
	a.hub.Publish("shares", a.Shares())
	a.notifyPeers()
	return ShareView{Share: saved, Exists: true}, nil
}

// DeleteShare stops sharing a folder.
func (a *App) DeleteShare(id string) error {
	err := a.cfg.Update(func(c *Config) error {
		for i := range c.Shares {
			if c.Shares[i].ID == id {
				c.Shares = append(c.Shares[:i], c.Shares[i+1:]...)
				return nil
			}
		}
		return mesh.Errf(mesh.CodeNotFound, "no such shared folder")
	})
	if err == nil {
		a.hub.Publish("shares", a.Shares())
		a.notifyPeers()
	}
	return err
}

// ---- published services ----

// Services lists TCP services this device publishes.
func (a *App) Services() []services.Service { return a.cfg.Get().Services }

// SaveService creates (id empty) or updates a published service.
func (a *App) SaveService(id string, in services.Service) (services.Service, error) {
	in.Name = strings.TrimSpace(in.Name)
	if in.Name == "" || len(in.Name) > 40 || strings.ContainsAny(in.Name, " /\\:") {
		return services.Service{}, mesh.Errf(mesh.CodeInvalid, "the service name must be short, without spaces or slashes (for example ssh)")
	}
	in.Addr = strings.TrimSpace(in.Addr)
	if !validHostPort(in.Addr) {
		return services.Service{}, mesh.Errf(mesh.CodeInvalid, "the address must look like 127.0.0.1:22")
	}
	allow, err := normalizeAllow(in.Allow)
	if err != nil {
		return services.Service{}, err
	}
	in.Allow = allow
	var saved services.Service
	err = a.cfg.Update(func(c *Config) error {
		for _, s := range c.Services {
			if strings.EqualFold(s.Name, in.Name) && s.ID != id {
				return mesh.Errf(mesh.CodeExists, "a service with this name already exists")
			}
		}
		if id == "" {
			in.ID = randID("sv_")
			c.Services = append(c.Services, in)
			saved = in
			return nil
		}
		for i := range c.Services {
			if c.Services[i].ID == id {
				in.ID = id
				c.Services[i] = in
				saved = in
				return nil
			}
		}
		return mesh.Errf(mesh.CodeNotFound, "no such service")
	})
	if err == nil {
		a.notifyPeers()
	}
	return saved, err
}

// DeleteService stops publishing a service.
func (a *App) DeleteService(id string) error {
	err := a.cfg.Update(func(c *Config) error {
		for i := range c.Services {
			if c.Services[i].ID == id {
				c.Services = append(c.Services[:i], c.Services[i+1:]...)
				return nil
			}
		}
		return mesh.Errf(mesh.CodeNotFound, "no such service")
	})
	if err == nil {
		a.notifyPeers()
	}
	return err
}

// RemoteServices lists the services a device offers us.
func (a *App) RemoteServices(ctx context.Context, id identity.ID) ([]services.RemoteService, error) {
	if id == a.node.ID() {
		return a.svc.Visible(identity.ID{}), nil
	}
	return a.svc.RemoteServices(ctx, id)
}

// ---- port forwards ----

// Forwards lists configured forwards with their state.
func (a *App) Forwards() []services.ForwardStatus {
	list := a.cfg.Get().Forwards
	out := make([]services.ForwardStatus, 0, len(list))
	for _, f := range list {
		out = append(out, a.fwd.Status(f))
	}
	return out
}

// AddForward binds a local address to a service of another device.
func (a *App) AddForward(peer identity.ID, service, listen string) (services.ForwardStatus, error) {
	if a.node.Peer(peer) == nil {
		return services.ForwardStatus{}, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	service = strings.TrimSpace(service)
	if service == "" {
		return services.ForwardStatus{}, mesh.Errf(mesh.CodeInvalid, "choose a service")
	}
	if strings.TrimSpace(listen) == "" {
		listen = "127.0.0.1:0"
	}
	if !validHostPort(listen) {
		return services.ForwardStatus{}, mesh.Errf(mesh.CodeInvalid, "the local address must look like 127.0.0.1:2222")
	}
	fw := services.Forward{ID: randID("fw_"), Peer: peer, Service: service, Listen: listen}
	opened, err := a.fwd.Open(fw)
	if err != nil {
		return services.ForwardStatus{}, mesh.Errf(mesh.CodeBusy, "%v", err)
	}
	fw = opened
	if err := a.cfg.Update(func(c *Config) error { c.Forwards = append(c.Forwards, fw); return nil }); err != nil {
		a.fwd.Close(fw.ID)
		return services.ForwardStatus{}, err
	}
	a.forwardsChanged()
	return a.fwd.Status(fw), nil
}

// DeleteForward stops and removes a forward.
func (a *App) DeleteForward(id string) error {
	found := false
	err := a.cfg.Update(func(c *Config) error {
		for i := range c.Forwards {
			if c.Forwards[i].ID == id {
				c.Forwards = append(c.Forwards[:i], c.Forwards[i+1:]...)
				found = true
				return nil
			}
		}
		return mesh.Errf(mesh.CodeNotFound, "no such forward")
	})
	if found {
		a.fwd.Close(id)
		a.forwardsChanged()
	}
	return err
}

// ---- local folder picker ----

// LocalDir is a listing for the folder picker.
type LocalDir struct {
	Path    string          `json:"path"`
	Parent  string          `json:"parent"`
	Home    string          `json:"home"`
	Sep     string          `json:"sep"`
	Roots   []string        `json:"roots"`
	Entries []LocalDirEntry `json:"entries"`
}

// LocalDirEntry is one sub-folder.
type LocalDirEntry struct {
	Name  string `json:"name"`
	IsDir bool   `json:"isDir"`
}

// LocalFS lists sub-folders of a directory on this device (folders only).
func (a *App) LocalFS(path string) (*LocalDir, error) {
	home, _ := os.UserHomeDir()
	if path == "" {
		path = home
		if path == "" {
			path = string(filepath.Separator)
		}
	}
	if !filepath.IsAbs(path) {
		return nil, mesh.Errf(mesh.CodeInvalid, "the path must be absolute")
	}
	path = filepath.Clean(path)
	ents, err := os.ReadDir(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, mesh.Errf(mesh.CodeNotFound, "no such folder")
		}
		return nil, mesh.Errf(mesh.CodeDenied, "cannot open this folder")
	}
	d := &LocalDir{Path: path, Home: home, Sep: string(filepath.Separator), Roots: roots(), Entries: []LocalDirEntry{}}
	if parent := filepath.Dir(path); parent != path {
		d.Parent = parent
	}
	for _, e := range ents {
		if strings.HasPrefix(e.Name(), ".") {
			continue
		}
		isDir := e.IsDir()
		if !isDir && e.Type()&os.ModeSymlink != 0 {
			if st, err := os.Stat(filepath.Join(path, e.Name())); err == nil && st.IsDir() {
				isDir = true
			}
		}
		if isDir {
			d.Entries = append(d.Entries, LocalDirEntry{Name: e.Name(), IsDir: true})
		}
	}
	sort.Slice(d.Entries, func(i, j int) bool {
		return strings.ToLower(d.Entries[i].Name) < strings.ToLower(d.Entries[j].Name)
	})
	return d, nil
}

func roots() []string {
	if runtime.GOOS != "windows" {
		return []string{"/"}
	}
	var out []string
	for c := 'A'; c <= 'Z'; c++ {
		p := string(c) + `:\`
		if _, err := os.Stat(p); err == nil {
			out = append(out, p)
		}
	}
	return out
}

// PeerID parses a device ID from the API, accepting "self".
func (a *App) PeerID(s string) (identity.ID, error) {
	if s == "self" || s == "" {
		return a.node.ID(), nil
	}
	if p := a.node.FindPeer(s); p != nil {
		return p.ID, nil
	}
	id, err := identity.ParseID(s)
	if err != nil {
		return identity.ID{}, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	return id, nil
}
