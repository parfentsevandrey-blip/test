package app

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/user"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
	"github.com/parfentsevandrey-blip/test/svoi/internal/tun"
)

// Options configure Open.
type Options struct {
	// Dir is the data directory (device key, mesh state, config, mail, blobs).
	Dir string
	// Logger receives log output (default: discard). The app also keeps a ring
	// buffer of recent lines for the UI's log viewer.
	Logger *slog.Logger
	// Mesh carries optional overrides for the node (simulation hooks, timing, loopback).
	Mesh mesh.Config
	// DeviceName and Owner are the defaults offered when creating or joining a mesh.
	DeviceName string
	Owner      string
}

// App is one running themesh device.
type App struct {
	opts Options
	dir  string
	log  *slog.Logger

	cfg   *configStore
	node  *mesh.Node
	db    *store.DB
	blobs *blob.Store
	files *files.Manager
	mail  *mail.Manager
	svc   *services.Manager
	fwd   *services.Forwarder
	tun   *tun.Manager
	hub   *Hub
	logs  func(limit int) []LogLine
	extra *extrasCache

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup

	mu    sync.Mutex
	socks *services.SOCKSServer

	token        string
	peersDebounc *debouncer
	baseNet      mesh.Config // what the node was started with, to detect restart-requiring changes
}

// Open starts a device from its data directory.
func Open(opts Options) (*App, error) {
	if opts.Dir == "" {
		return nil, errors.New("app: Options.Dir is required")
	}
	if err := identity.EnsurePrivateDir(opts.Dir); err != nil {
		return nil, err
	}
	cfg, err := loadConfig(opts.Dir)
	if err != nil {
		return nil, err
	}
	var inner slog.Handler
	if opts.Logger != nil {
		inner = opts.Logger.Handler()
	}
	ring, logs := newLogRing(inner)
	log := slog.New(ring)

	a := &App{opts: opts, dir: opts.Dir, log: log, cfg: cfg, hub: NewHub(), logs: logs}
	a.ctx, a.cancel = context.WithCancel(context.Background())
	a.peersDebounc = newDebouncer(150*time.Millisecond, a.publishPeers)
	a.token, err = loadOrCreateToken(opts.Dir)
	if err != nil {
		return nil, err
	}

	c := cfg.Get()
	mc := opts.Mesh
	mc.Dir = opts.Dir
	mc.Logger = log
	mc.UDPPort = c.UDPPort
	if c.STUNEnabled {
		mc.STUN = c.STUNServers
	}
	mc.NoRelay = !c.Relay
	mc.PortMap = c.PortMap
	if !c.LAN {
		mc.LANPort = -1
	}
	if mc.DeviceName == "" {
		mc.DeviceName = firstNonEmpty(opts.DeviceName, hostName())
	}
	if mc.Owner == "" {
		mc.Owner = firstNonEmpty(opts.Owner, osUser())
	}
	a.baseNet = mc
	a.node, err = mesh.Open(mc)
	if err != nil {
		return nil, err
	}

	a.db, err = store.Open(filepath.Join(opts.Dir, "themesh.db"))
	if err != nil {
		a.node.Close()
		return nil, err
	}
	a.blobs, err = blob.Open(filepath.Join(opts.Dir, "blobs"))
	if err != nil {
		a.Close()
		return nil, err
	}

	a.files = files.NewManager(files.Config{Node: a.node, Shares: func() []files.Share { return a.cfg.Get().Shares }, Protected: []string{opts.Dir}})
	a.files.RegisterRPC(a.node)
	if err := a.files.InitTransfers(a.db, opts.Dir, a.transferSettings, a.onTransfer); err != nil {
		a.Close()
		return nil, err
	}
	a.mail, err = mail.New(a.node, a.db, a.blobs, a.onMail)
	if err != nil {
		a.Close()
		return nil, err
	}
	a.mail.Register()
	a.blobs.RegisterRPC(a.node)
	a.svc = services.NewManager(a.node, func() []services.Service { return a.cfg.Get().Services })
	a.svc.RegisterRPC()
	a.fwd = services.NewForwarder(a.svc, a.forwardsChanged)
	a.extra = newExtrasCache(a)
	a.registerExtrasRPC()
	a.tun = tun.New(a.node, "", c.TUN.ManageHosts)

	a.start()
	return a, nil
}

func (a *App) start() {
	a.fwd.Start(a.ctx)
	for _, f := range a.cfg.Get().Forwards {
		if a.node.Peer(f.Peer) != nil {
			if _, err := a.fwd.Open(f); err != nil {
				a.log.Warn("cannot restore port forward", "listen", f.Listen, "err", err)
			}
		}
	}
	a.applySocks()
	a.applyTUN()
	run := func(f func()) {
		a.wg.Add(1)
		go func() { defer a.wg.Done(); f() }()
	}
	run(func() { a.files.RunTransfers(a.ctx) })
	run(func() { a.mail.Run(a.ctx) })
	run(func() { a.extra.run(a.ctx) })
	run(a.bridgeNodeEvents)
}

// Close stops everything.
func (a *App) Close() error {
	a.cancel()
	a.mu.Lock()
	if a.socks != nil {
		a.socks.Close()
	}
	a.mu.Unlock()
	if a.tun != nil {
		a.tun.Stop()
	}
	if a.node != nil {
		a.node.Close()
	}
	a.wg.Wait()
	if a.db != nil {
		a.db.Close()
	}
	return nil
}

// Node returns the underlying mesh node.
func (a *App) Node() *mesh.Node { return a.node }

// Files returns the file manager.
func (a *App) Files() *files.Manager { return a.files }

// Mail returns the mail manager.
func (a *App) Mail() *mail.Manager { return a.mail }

// Blobs returns the attachment store.
func (a *App) Blobs() *blob.Store { return a.blobs }

// ServiceManager returns the TCP service manager.
func (a *App) ServiceManager() *services.Manager { return a.svc }

// Hub returns the event hub.
func (a *App) Hub() *Hub { return a.hub }

// Token is the secret the local UI session is authenticated with.
func (a *App) Token() string { return a.token }

// Dir returns the data directory.
func (a *App) Dir() string { return a.dir }

// Logger returns the app logger.
func (a *App) Logger() *slog.Logger { return a.log }

// Logs returns recent log lines.
func (a *App) Logs(limit int) []LogLine { return a.logs(limit) }

func (a *App) transferSettings() files.TransferSettings {
	c := a.cfg.Get()
	return files.TransferSettings{
		DownloadDir: c.DownloadDir,
		AutoAccept:  c.AutoAccept,
		MaxAutoByte: int64(c.AutoAcceptMaxMB) << 20,
	}
}

func loadOrCreateToken(dir string) (string, error) {
	path := filepath.Join(dir, "ui.token")
	if b, err := os.ReadFile(path); err == nil && len(strings.TrimSpace(string(b))) >= 32 {
		return strings.TrimSpace(string(b)), nil
	}
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	tok := hex.EncodeToString(b)
	if err := identity.WriteFileAtomic(path, []byte(tok+"\n"), 0o600); err != nil {
		return "", err
	}
	return tok, nil
}

func firstNonEmpty(ss ...string) string {
	for _, s := range ss {
		if strings.TrimSpace(s) != "" {
			return s
		}
	}
	return ""
}

func hostName() string {
	h, _ := os.Hostname()
	if h == "" {
		return "device"
	}
	if i := strings.IndexByte(h, '.'); i > 0 {
		h = h[:i]
	}
	return h
}

func osUser() string {
	if u, err := user.Current(); err == nil && u.Username != "" {
		if u.Name != "" {
			return u.Name
		}
		return u.Username
	}
	return ""
}

// ---- event bridging ----

func (a *App) peersChanged() { a.peersDebounc.trigger() }

func (a *App) publishPeers() {
	a.hub.Publish("peers", a.PeerViews())
	a.hub.Publish("self", a.node.Self())
}

func (a *App) bridgeNodeEvents() {
	events, cancel := a.node.Subscribe()
	defer cancel()
	for {
		select {
		case <-a.ctx.Done():
			return
		case ev := <-events:
			switch ev.Kind {
			case mesh.EvRemoved:
				a.onRemoved()
			case mesh.EvMembers:
				a.peersChanged()
				a.hub.Publish("invites", a.Invites())
			default:
				a.peersChanged()
			}
		}
	}
}

func (a *App) onTransfer(t files.Transfer) {
	a.hub.Publish("transfer", t)
	if t.State == files.StateOffered || t.State == files.StateDone || t.State == files.StateQueued {
		a.publishCounters()
	}
	if t.Dir == "in" && t.State == files.StateOffered {
		a.hub.Publish("notify", map[string]any{
			"level": "info", "title": t.PeerName,
			"text": fmt.Sprintf("%s: %s", t.PeerName, t.Name), "link": "#/files",
		})
	}
}

func (a *App) onMail(e mail.Event) {
	switch e.Kind {
	case "mail":
		a.hub.Publish("mail", map[string]any{"id": e.ID, "folder": e.Folder, "unread": e.Unread})
		if e.Unread {
			if m, ok := a.mail.Get(e.ID); ok {
				a.hub.Publish("notify", map[string]any{"level": "info", "title": m.From.Name, "text": m.Subject, "link": "#/mail"})
			}
		}
	case "chat":
		if cm, peer, ok := a.mail.ChatMessageByID(e.ID); ok {
			a.hub.Publish("chat", map[string]any{
				"id": cm.ID, "from": cm.From, "to": cm.To, "mine": cm.Mine, "text": cm.Text, "ts": cm.TS,
				"state": cm.State, "attachments": cm.Attachments, "peer": peer,
			})
			if e.Unread && !cm.Mine {
				a.hub.Publish("notify", map[string]any{"level": "info", "title": a.peerName(peer), "text": cm.Text, "link": "#/chat"})
			}
		}
	}
	a.publishCounters()
}

// Counters are the unread/pending badges.
type Counters struct {
	Mail   int `json:"mail"`
	Chat   int `json:"chat"`
	Offers int `json:"offers"`
}

// Counters returns the current badges.
func (a *App) Counters() Counters {
	m, c := a.mail.Counters()
	return Counters{Mail: m, Chat: c, Offers: a.files.PendingOffers()}
}

func (a *App) publishCounters() { a.hub.Publish("counters", a.Counters()) }

func (a *App) forwardsChanged() { a.hub.Publish("forwards", a.Forwards()) }

func (a *App) peerName(id identity.ID) string {
	if p := a.node.Peer(id); p != nil {
		return p.Name()
	}
	return id.Short()
}

// ---- peers and state ----

// PeerView is a peer as the UI sees it (see docs/UI-API.md).
type PeerView struct {
	ID          string                   `json:"id"`
	Short       string                   `json:"short"`
	Name        string                   `json:"name"`
	DeviceName  string                   `json:"deviceName"`
	Alias       string                   `json:"alias"`
	Owner       string                   `json:"owner"`
	IP4         string                   `json:"ip4"`
	IP6         string                   `json:"ip6"`
	Admin       bool                     `json:"admin"`
	Online      bool                     `json:"online"`
	Path        string                   `json:"path"`
	RelayVia    string                   `json:"relayVia,omitempty"`
	RTTms       float64                  `json:"rttMs"`
	Addr        string                   `json:"addr,omitempty"`
	LastSeen    int64                    `json:"lastSeen"`
	ConnectedAt int64                    `json:"connectedAt"`
	OS          string                   `json:"os"`
	Arch        string                   `json:"arch"`
	Version     string                   `json:"version"`
	Caps        []string                 `json:"caps"`
	Uptime      int64                    `json:"uptime"`
	Shares      int                      `json:"shares"`
	Services    []services.RemoteService `json:"services"`
	TxBytes     uint64                   `json:"txBytes"`
	RxBytes     uint64                   `json:"rxBytes"`
	TxRelay     uint64                   `json:"txRelay"`
	RxRelay     uint64                   `json:"rxRelay"`
	LastError   string                   `json:"lastError"`
	Endpoints   []string                 `json:"endpoints"`
	ClockSkewMs int64                    `json:"clockSkewMs"`
}

// PeerViews returns all other devices.
func (a *App) PeerViews() []PeerView {
	peers := a.node.Peers()
	out := make([]PeerView, 0, len(peers))
	for _, p := range peers {
		out = append(out, a.peerView(p))
	}
	return out
}

func (a *App) peerView(p *mesh.Peer) PeerView {
	i := p.Info()
	v := PeerView{
		ID: i.ID, Short: i.Short, Name: i.Name, DeviceName: i.DeviceName, Alias: i.Alias, Owner: i.Owner,
		IP4: i.IP4, IP6: i.IP6, Admin: i.Admin, Online: i.Online, Path: i.Path, RelayVia: i.RelayVia,
		RTTms: i.RTTms, Addr: i.Addr, LastSeen: i.LastSeen, ConnectedAt: i.ConnectedAt,
		TxBytes: i.TxBytes, RxBytes: i.RxBytes, TxRelay: i.TxRelay, RxRelay: i.RxRelay,
		LastError: i.LastError, Endpoints: i.Endpoints, ClockSkewMs: i.Skew,
		Caps: []string{}, Services: []services.RemoteService{},
	}
	if v.Endpoints == nil {
		v.Endpoints = []string{}
	}
	if h := i.Hello; h != nil {
		v.OS, v.Arch, v.Version = h.OS, h.Arch, h.Version
		if h.Caps != nil {
			v.Caps = h.Caps
		}
		if h.Started > 0 {
			v.Uptime = time.Now().Unix() - h.Started
			if v.Uptime < 0 {
				v.Uptime = 0
			}
		}
	}
	x := a.extra.get(p.ID)
	v.Shares = x.Shares
	if x.Services != nil {
		v.Services = x.Services
	}
	return v
}

// InviteView is an invitation with its QR code.
type InviteView struct {
	ID      string `json:"id"`
	Code    string `json:"code"`
	Admin   bool   `json:"admin"`
	Owner   string `json:"owner"` // whose device the invitation is for (set by the inviter)
	Created int64  `json:"created"`
	Expires int64  `json:"expires"`
	QRSvg   string `json:"qrSvg"`
}

func (a *App) inviteView(i mesh.InviteInfo) InviteView {
	return InviteView{ID: i.ID, Code: i.Code, Admin: i.Admin, Owner: i.Owner, Created: i.Created, Expires: i.Expires, QRSvg: qrSVG(qrPayload(i.Code))}
}

// Invites lists pending invitations.
func (a *App) Invites() []InviteView {
	list := a.node.Invites()
	out := make([]InviteView, 0, len(list))
	for _, i := range list {
		out = append(out, a.inviteView(i))
	}
	return out
}

// NewInvite creates an invitation (admin only). owner is the label the joining
// device will carry; empty means this device's own owner ("my other device").
func (a *App) NewInvite(admin bool, ttl time.Duration, owner string) (InviteView, error) {
	i, err := a.node.NewInviteFor(admin, ttl, owner)
	if err != nil {
		return InviteView{}, err
	}
	v := a.inviteView(i)
	a.hub.Publish("invites", a.Invites())
	return v, nil
}

// CancelInvite withdraws an invitation.
func (a *App) CancelInvite(id string) bool {
	ok := a.node.CancelInvite(id)
	a.hub.Publish("invites", a.Invites())
	return ok
}

// State is the full snapshot served by GET /api/state.
type State struct {
	Version    string           `json:"version"`
	Configured bool             `json:"configured"`
	Self       mesh.SelfInfo    `json:"self"`
	Peers      []PeerView       `json:"peers"`
	Transfers  []files.Transfer `json:"transfers"`
	Counters   Counters         `json:"counters"`
	Invites    []InviteView     `json:"invites"`
	Settings   Settings         `json:"settings"`
	// Removed is set while this device is outside any mesh because an
	// administrator removed it from one.
	Removed *mesh.RemovedInfo `json:"removed,omitempty"`
}

// State assembles the snapshot.
func (a *App) State() State {
	st := State{
		Version:    mesh.Version,
		Configured: a.node.Configured(),
		Self:       a.node.Self(),
		Peers:      []PeerView{},
		Transfers:  []files.Transfer{},
		Invites:    []InviteView{},
		Settings:   a.Settings(),
	}
	if !st.Configured {
		st.Removed = a.node.Removed()
		return st
	}
	st.Peers = a.PeerViews()
	st.Counters = a.Counters()
	st.Invites = a.Invites()
	all := a.files.Transfers()
	done := 0
	for _, t := range all {
		switch t.State {
		case files.StateDone, files.StateFailed, files.StateDeclined, files.StateCanceled:
			if done >= 50 {
				continue
			}
			done++
		}
		st.Transfers = append(st.Transfers, t)
	}
	return st
}

// ---- mesh lifecycle ----

// CreateMesh founds a new mesh.
func (a *App) CreateMesh(meshName, deviceName, owner string) error {
	if err := a.node.CreateMesh(meshName, deviceName, owner); err != nil {
		return err
	}
	a.applyTUN()
	a.peersChanged()
	return nil
}

// JoinMesh joins an existing mesh by invitation code.
func (a *App) JoinMesh(ctx context.Context, code, deviceName string) error {
	if err := a.node.JoinMesh(ctx, code, deviceName); err != nil {
		return err
	}
	a.applyTUN()
	a.peersChanged()
	return nil
}

// LeaveMesh forgets the mesh; the device key is kept.
func (a *App) LeaveMesh() error {
	a.forgetMeshScoped()
	if err := a.node.Leave(); err != nil {
		return err
	}
	a.peersChanged()
	return nil
}

// forgetMeshScoped drops everything that only made sense inside the mesh being
// left: forwards, the virtual interface and — because they were granted to that
// mesh's members — the shared folders and published services. Otherwise a device
// that joins another mesh would silently offer its folders to strangers.
func (a *App) forgetMeshScoped() {
	for _, f := range a.cfg.Get().Forwards {
		a.fwd.Close(f.ID)
	}
	_ = a.cfg.Update(func(c *Config) error {
		c.Forwards = []services.Forward{}
		c.Shares = []files.Share{}
		c.Services = []services.Service{}
		return nil
	})
	a.tun.Stop()
}

// onRemoved cleans up after an administrator removed this device from the mesh
// (the node itself forgets the mesh and takes a new identity).
func (a *App) onRemoved() {
	a.forgetMeshScoped()
	name := ""
	if ri := a.node.Removed(); ri != nil {
		name = ri.MeshName
	}
	a.hub.Publish("notify", map[string]any{"level": "warn", "title": name, "text": "removed", "link": "#/"})
	a.peersChanged()
}

// NetCheck re-runs address discovery and returns the refreshed self info.
func (a *App) NetCheck(ctx context.Context) mesh.SelfInfo {
	if mg := a.node.Magic(); mg != nil {
		mg.NetCheck()
		select {
		case <-time.After(1500 * time.Millisecond):
		case <-ctx.Done():
		}
	}
	return a.node.Self()
}

// Ping measures the application round trip to a peer.
func (a *App) Ping(ctx context.Context, id identity.ID) (time.Duration, error) {
	p := a.node.Peer(id)
	if p == nil {
		return 0, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	if !p.Online() {
		return 0, mesh.Errf(mesh.CodeOffline, "%s is not online", p.Name())
	}
	return p.Ping(ctx)
}

// SetAlias sets a local nickname for a device.
func (a *App) SetAlias(id identity.ID, alias string) error {
	if err := a.node.SetAlias(id, alias); err != nil {
		return err
	}
	a.peersChanged()
	return nil
}

// Revoke removes a device from the mesh for good (admin only).
func (a *App) Revoke(id identity.ID) error {
	if err := a.node.Revoke(id); err != nil {
		return err
	}
	a.peersChanged()
	return nil
}

// Rename changes a device's name (admin only).
func (a *App) Rename(id identity.ID, name string) error {
	admin := false
	if id == a.node.ID() {
		admin = a.node.Self().Admin
	} else if p := a.node.Peer(id); p != nil {
		admin = p.Member().Admin
	} else {
		return mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	return a.node.Reissue(id, name, admin)
}

// GrantAdmin makes another device a full administrator: it receives the mesh
// authority key over the encrypted link. This cannot be revoked.
func (a *App) GrantAdmin(ctx context.Context, id identity.ID) error {
	if err := a.node.GrantAdmin(ctx, id); err != nil {
		return err
	}
	a.peersChanged()
	return nil
}

// ---- helpers shared with the HTTP layer ----

func randID(prefix string) string {
	b := make([]byte, 6)
	_, _ = rand.Read(b)
	return prefix + hex.EncodeToString(b)
}

// ReadAllLimit reads at most n bytes and fails if there is more.
func ReadAllLimit(r io.Reader, n int64) ([]byte, error) {
	b, err := io.ReadAll(io.LimitReader(r, n+1))
	if err != nil {
		return nil, err
	}
	if int64(len(b)) > n {
		return nil, mesh.Errf(mesh.CodeTooLarge, "request body too large")
	}
	return b, nil
}
