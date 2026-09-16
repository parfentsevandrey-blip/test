// Package engine is the connection state machine: it walks the configured
// modes until one works, keeps probing the tunnel, and reconnects or
// switches mode when it breaks.
package engine

import (
	"context"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"

	"torss/internal/bridges"
	"torss/internal/config"
	"torss/internal/killswitch"
	"torss/internal/probe"
	"torss/internal/singbox"
	"torss/internal/sysproxy"
	"torss/internal/tor"
)

// Phase of the engine.
type Phase int

const (
	Disconnected Phase = iota
	Connecting
	Connected
	Failed
)

func (p Phase) String() string {
	switch p {
	case Disconnected:
		return "Отключено"
	case Connecting:
		return "Подключение"
	case Connected:
		return "Подключено"
	case Failed:
		return "Ошибка"
	}
	return "?"
}

// Status is a snapshot for the UI.
type Status struct {
	Phase    Phase
	Mode     config.Mode
	Detail   string
	Progress int
	Since    time.Time
}

// Paths tells the engine where binaries and data live.
type Paths struct {
	BinDir  string
	DataDir string
	SingBox string
	Tor     tor.Layout
	// SelfExe is torss itself (allowed through the kill switch for probes).
	SelfExe string
}

// Engine drives the tunnel.
type Engine struct {
	cfg       *config.Config
	cfgPath   string
	state     *config.State
	statePath string
	paths     Paths
	logf      func(string, ...any)
	onChange  func(Status)

	mu      sync.Mutex
	status  Status
	desired bool
	cancel  context.CancelFunc
	wg      sync.WaitGroup
	tor     *tor.Instance
	sb      *singbox.Instance
	ksOn    bool
	proxyOn bool
}

// New creates an engine. onChange may be nil.
func New(cfg *config.Config, cfgPath string, state *config.State, statePath string, paths Paths,
	logf func(string, ...any), onChange func(Status)) *Engine {
	if logf == nil {
		logf = func(string, ...any) {}
	}
	return &Engine{
		cfg: cfg, cfgPath: cfgPath, state: state, statePath: statePath, paths: paths,
		logf: logf, onChange: onChange,
		status: Status{Phase: Disconnected, Mode: cfg.Mode, Detail: "готов"},
	}
}

// Config returns the live config (UI reads it).
func (e *Engine) Config() *config.Config { return e.cfg }

// Status returns the current snapshot.
func (e *Engine) Status() Status {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.status
}

func (e *Engine) setStatus(p Phase, m config.Mode, detail string, progress int) {
	e.mu.Lock()
	if p != e.status.Phase || m != e.status.Mode {
		e.status.Since = time.Now()
	}
	e.status.Phase, e.status.Mode, e.status.Detail, e.status.Progress = p, m, detail, progress
	s := e.status
	cb := e.onChange
	e.mu.Unlock()
	e.logf("status: %s %s %s", p, m, detail)
	if cb != nil {
		cb(s)
	}
}

// CleanupAfterCrash undoes system changes left by a previous run.
func (e *Engine) CleanupAfterCrash() {
	if e.state.KillSwitchActive {
		e.logf("cleanup: disabling kill switch left from a previous run")
		if err := killswitch.Disable(); err != nil {
			e.logf("cleanup: kill switch: %v", err)
		}
		e.state.KillSwitchActive = false
		_ = e.state.Save(e.statePath)
	}
	if e.state.SysProxyBackup != nil {
		e.logf("cleanup: restoring system proxy left from a previous run")
		if err := sysproxy.Restore(e.state.SysProxyBackup); err != nil {
			e.logf("cleanup: sysproxy: %v", err)
		}
		e.state.SysProxyBackup = nil
		_ = e.state.Save(e.statePath)
	}
}

// Connect starts the connection loop (no-op if already running).
func (e *Engine) Connect() {
	e.mu.Lock()
	if e.desired {
		e.mu.Unlock()
		return
	}
	e.desired = true
	ctx, cancel := context.WithCancel(context.Background())
	e.cancel = cancel
	e.wg.Add(1)
	e.mu.Unlock()
	go func() {
		defer e.wg.Done()
		e.run(ctx)
	}()
}

// Disconnect stops everything and restores system settings. Blocks until done.
func (e *Engine) Disconnect() {
	e.mu.Lock()
	if !e.desired {
		e.mu.Unlock()
		return
	}
	e.desired = false
	cancel := e.cancel
	e.mu.Unlock()
	e.setStatus(Connecting, e.Status().Mode, "отключение...", 0)
	cancel()
	e.wg.Wait()
	e.teardownProcesses()
	e.removeSystemIntegration()
	e.setStatus(Disconnected, e.cfg.Mode, "готов", 0)
}

// Reconnect restarts the loop (e.g. after a config change).
func (e *Engine) Reconnect() {
	e.Disconnect()
	e.Connect()
}

// Running reports whether the user asked to be connected.
func (e *Engine) Running() bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.desired
}

// SetMode changes the selected mode and reconnects if running.
func (e *Engine) SetMode(m config.Mode) error {
	if !m.Valid() {
		return fmt.Errorf("invalid mode %s", m)
	}
	e.cfg.Mode = m
	if err := e.cfg.Save(e.cfgPath); err != nil {
		return err
	}
	if e.Running() {
		go e.Reconnect()
	} else {
		e.setStatus(Disconnected, m, "готов", 0)
	}
	return nil
}

// SetKillSwitch toggles the kill switch (applied immediately if connected).
func (e *Engine) SetKillSwitch(on bool) error {
	e.cfg.KillSwitch = on
	if err := e.cfg.Save(e.cfgPath); err != nil {
		return err
	}
	if e.Status().Phase == Connected {
		if on {
			e.enableKillSwitch()
		} else {
			e.disableKillSwitch()
		}
	}
	return nil
}

// NewIdentity asks Tor for new circuits.
func (e *Engine) NewIdentity() error {
	e.mu.Lock()
	t := e.tor
	e.mu.Unlock()
	if t == nil {
		return fmt.Errorf("Tor не запущен в текущем режиме")
	}
	return t.NewIdentity()
}

// ResetTorState deletes Tor's cached consensus/guards. Only when disconnected.
func (e *Engine) ResetTorState() error {
	if e.Running() {
		return fmt.Errorf("сначала отключитесь")
	}
	return os.RemoveAll(filepath.Join(e.paths.DataDir, "tor"))
}

// ---------------------------------------------------------------------------

func (e *Engine) run(ctx context.Context) {
	backoff := 20 * time.Second
	for ctx.Err() == nil {
		modes, why := e.candidates()
		if len(modes) == 0 {
			e.setStatus(Failed, e.cfg.Mode, "нет доступных режимов: "+why, 0)
			<-ctx.Done()
			return
		}
		var connected config.Mode
		for _, m := range modes {
			if ctx.Err() != nil {
				return
			}
			e.setStatus(Connecting, m, "запуск...", 0)
			err := e.tryMode(ctx, m)
			if err == nil {
				connected = m
				break
			}
			e.logf("mode %s failed: %v", m, err)
			e.setStatus(Connecting, m, "не сработало: "+short(err.Error()), 0)
			e.teardownProcesses()
		}
		if ctx.Err() != nil {
			return
		}
		if connected == "" {
			e.setStatus(Failed, e.cfg.Mode, fmt.Sprintf("ни один режим не сработал, повтор через %s", backoff.Round(time.Second)), 0)
			select {
			case <-ctx.Done():
				return
			case <-time.After(backoff):
			}
			if backoff < 5*time.Minute {
				backoff *= 2
			}
			continue
		}
		backoff = 20 * time.Second
		e.state.LastGoodMode = connected
		_ = e.state.Save(e.statePath)
		e.applySystemIntegration()
		e.setStatus(Connected, connected, e.connectedDetail(connected, 0), 100)
		e.watch(ctx, connected)
		if ctx.Err() != nil {
			return
		}
		e.setStatus(Connecting, connected, "переподключение...", 0)
		e.teardownProcesses()
	}
}

func (e *Engine) connectedDetail(m config.Mode, rtt time.Duration) string {
	var where string
	if e.cfg.TUN {
		where = "VPN (TUN)"
	} else {
		where = fmt.Sprintf("прокси 127.0.0.1:%d", e.cfg.Ports.Mixed)
	}
	if rtt > 0 {
		return fmt.Sprintf("%s, %s, проверка %s", m.Title(), where, rtt.Round(100*time.Millisecond))
	}
	return fmt.Sprintf("%s, %s", m.Title(), where)
}

func short(s string) string {
	if len(s) > 90 {
		return s[:90] + "…"
	}
	return s
}

// candidates returns the modes to try, in order, and a reason if none.
func (e *Engine) candidates() ([]config.Mode, string) {
	var order []config.Mode
	if e.cfg.Mode != config.ModeAuto {
		order = []config.Mode{e.cfg.Mode}
	} else {
		if lg := e.state.LastGoodMode; lg != "" && contains(e.cfg.ModeOrder, lg) {
			order = append(order, lg)
		}
		for _, m := range e.cfg.ModeOrder {
			if !contains(order, m) {
				order = append(order, m)
			}
		}
	}
	var out []config.Mode
	var reasons string
	for _, m := range order {
		if why := e.unusable(m); why != "" {
			e.logf("skip %s: %s", m, why)
			reasons += string(m) + ": " + why + "; "
			continue
		}
		out = append(out, m)
	}
	return out, reasons
}

func (e *Engine) unusable(m config.Mode) string {
	if m.UsesSS() && !e.cfg.Server.Configured() {
		return "сервер Shadowsocks не настроен"
	}
	if m == config.ModeSSOnly && !e.cfg.AllowSSOnly {
		return "allow_ss_only выключен"
	}
	if tr := m.Transport(); tr != "" {
		if tr == "conjure" && !e.paths.Tor.HasConjure {
			return "conjure-client.exe не найден"
		}
		if len(e.bridgesFor(m)) == 0 {
			return "нет мостов " + tr
		}
	}
	return ""
}

func (e *Engine) bridgesFor(m config.Mode) []string {
	tr := m.Transport()
	if tr == "" {
		return nil
	}
	builtin := bridges.Builtin(filepath.Join(e.paths.Tor.Dir, "pluggable_transports", "pt_config.json"))
	return bridges.ForTransport(tr, e.cfg.Bridges.Lines, builtin, e.cfg.Bridges.UseBuiltin)
}

func contains(list []config.Mode, m config.Mode) bool {
	for _, x := range list {
		if x == m {
			return true
		}
	}
	return false
}

func bootstrapTimeouts(m config.Mode) (total, stall time.Duration) {
	switch m {
	case config.ModeTorSnowflake, config.ModeTorMeek, config.ModeTorConjure:
		return 180 * time.Second, 90 * time.Second
	case config.ModeTorObfs4, config.ModeTorWebtunnel:
		return 120 * time.Second, 60 * time.Second
	default:
		return 90 * time.Second, 60 * time.Second
	}
}

var excludeProcesses = []string{"tor.exe", "lyrebird.exe", "conjure-client.exe"}

// tryMode brings the tunnel up in mode m, or returns an error.
func (e *Engine) tryMode(ctx context.Context, m config.Mode) error {
	if m.UsesSS() {
		e.setStatus(Connecting, m, "проверка доступности сервера...", 0)
		addr := net.JoinHostPort(e.cfg.Server.Address, fmt.Sprint(e.cfg.Server.Port))
		c, err := (&net.Dialer{Timeout: 6 * time.Second}).DialContext(ctx, "tcp", addr)
		if err != nil {
			return fmt.Errorf("сервер %s недоступен: %v", addr, err)
		}
		c.Close()
	}

	// 1. sing-box (SS client, TUN, local proxy)
	e.setStatus(Connecting, m, "запуск sing-box...", 0)
	sbCfg, err := singbox.Generate(singbox.Options{
		Mode:             m,
		TUN:              e.cfg.TUN,
		Ports:            e.cfg.Ports,
		Server:           e.cfg.Server,
		LogLevel:         e.cfg.LogLevel,
		CacheDB:          filepath.Join(e.paths.DataDir, "cache.db"),
		ExcludeProcesses: excludeProcesses,
	})
	if err != nil {
		return err
	}
	sb, err := singbox.Start(e.paths.SingBox, filepath.Join(e.paths.DataDir, "singbox"), sbCfg, e.logf)
	if err != nil {
		return err
	}
	e.mu.Lock()
	e.sb = sb
	e.mu.Unlock()
	if err := sb.WaitReady(ctx, e.cfg.Ports.Mixed, 20*time.Second); err != nil {
		return fmt.Errorf("sing-box: %w", err)
	}

	// 2. tor
	if m.UsesTor() {
		e.setStatus(Connecting, m, "запуск Tor...", 0)
		t, err := tor.Start(e.paths.Tor, tor.Options{
			DataDir:  filepath.Join(e.paths.DataDir, "tor"),
			Ports:    e.cfg.Ports,
			Mode:     m,
			Bridges:  e.bridgesFor(m),
			LogLevel: e.cfg.LogLevel,
		}, e.logf)
		if err != nil {
			return err
		}
		e.mu.Lock()
		e.tor = t
		e.mu.Unlock()
		total, stall := bootstrapTimeouts(m)
		err = t.WaitBootstrapped(ctx, total, stall, func(b tor.Bootstrap) {
			e.setStatus(Connecting, m, fmt.Sprintf("Tor %d%%: %s", b.Progress, b.Summary), b.Progress)
		})
		if err != nil {
			return fmt.Errorf("tor: %w", err)
		}
	}

	// 3. end-to-end probe through the local proxy
	e.setStatus(Connecting, m, "проверка соединения...", 100)
	res, err := e.probeOnce(ctx, m)
	if err != nil {
		// one retry: fresh circuits / a second attempt
		if t := e.currentTor(); t != nil {
			_ = t.NewIdentity()
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(3 * time.Second):
		}
		res, err = e.probeOnce(ctx, m)
		if err != nil {
			return fmt.Errorf("проверка не прошла: %w", err)
		}
	}
	e.logf("probe ok via %s in %s (mode %s)", res.URL, res.Elapsed.Round(time.Millisecond), m)
	return nil
}

func (e *Engine) currentTor() *tor.Instance {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.tor
}

func (e *Engine) probeOnce(ctx context.Context, m config.Mode) (probe.Result, error) {
	pc := probe.New(fmt.Sprintf("127.0.0.1:%d", e.cfg.Ports.Mixed), time.Duration(e.cfg.Probe.TimeoutSec)*time.Second)
	if m.UsesTor() {
		return pc.Check(ctx, e.cfg.Probe.TorURLs, true)
	}
	return pc.Check(ctx, e.cfg.Probe.PlainURLs, false)
}

// watch probes periodically; returns when the tunnel must be rebuilt.
func (e *Engine) watch(ctx context.Context, m config.Mode) {
	e.mu.Lock()
	sb, t := e.sb, e.tor
	e.mu.Unlock()
	var torDone <-chan struct{}
	if t != nil {
		torDone = t.Done()
	}
	interval := time.Duration(e.cfg.Probe.IntervalSec) * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	failures := 0
	for {
		select {
		case <-ctx.Done():
			return
		case <-sb.Done():
			e.logf("sing-box exited unexpectedly: %v", sb.Err())
			return
		case <-torDone:
			e.logf("tor exited unexpectedly")
			return
		case <-ticker.C:
		}
		res, err := e.probeOnce(ctx, m)
		if err == nil {
			if failures > 0 {
				e.logf("probe recovered via %s", res.URL)
			}
			failures = 0
			e.setStatus(Connected, m, e.connectedDetail(m, res.Elapsed), 100)
			continue
		}
		failures++
		e.logf("probe failed (%d/%d): %v", failures, e.cfg.Probe.FailuresBeforeReconnect, err)
		e.setStatus(Connected, m, fmt.Sprintf("%s — проверка не прошла %d/%d", m.Title(), failures, e.cfg.Probe.FailuresBeforeReconnect), 100)
		if t != nil && failures == e.cfg.Probe.FailuresBeforeReconnect-1 {
			e.logf("requesting new Tor identity before giving up")
			_ = t.NewIdentity()
		}
		if failures >= e.cfg.Probe.FailuresBeforeReconnect {
			return
		}
	}
}

func (e *Engine) teardownProcesses() {
	e.mu.Lock()
	t, sb := e.tor, e.sb
	e.tor, e.sb = nil, nil
	e.mu.Unlock()
	if t != nil {
		t.Stop()
	}
	if sb != nil {
		sb.Stop()
	}
}

// --- system integration ----------------------------------------------------

func (e *Engine) applySystemIntegration() {
	if !e.cfg.TUN && e.cfg.SystemProxy && !e.proxyOn {
		if e.state.SysProxyBackup == nil {
			if b, err := sysproxy.Backup(); err == nil {
				e.state.SysProxyBackup = b
				_ = e.state.Save(e.statePath)
			}
		}
		if err := sysproxy.Set(e.cfg.Ports.Mixed); err != nil {
			e.logf("system proxy: %v", err)
		} else {
			e.proxyOn = true
		}
	}
	if e.cfg.KillSwitch {
		e.enableKillSwitch()
	}
}

func (e *Engine) removeSystemIntegration() {
	if e.proxyOn || e.state.SysProxyBackup != nil {
		if err := sysproxy.Restore(e.state.SysProxyBackup); err != nil {
			e.logf("system proxy restore: %v", err)
		}
		e.state.SysProxyBackup = nil
		_ = e.state.Save(e.statePath)
		e.proxyOn = false
	}
	e.disableKillSwitch()
}

func (e *Engine) enableKillSwitch() {
	if e.ksOn {
		return
	}
	programs := []string{
		e.paths.SingBox,
		filepath.Join(e.paths.Tor.Dir, e.paths.Tor.Exe),
		filepath.Join(e.paths.Tor.Dir, e.paths.Tor.Lyrebird),
	}
	if e.paths.Tor.HasConjure {
		programs = append(programs, filepath.Join(e.paths.Tor.Dir, e.paths.Tor.Conjure))
	}
	if e.paths.SelfExe != "" {
		programs = append(programs, e.paths.SelfExe)
	}
	e.state.KillSwitchActive = true
	_ = e.state.Save(e.statePath)
	if err := killswitch.Enable(programs); err != nil {
		e.logf("kill switch: %v", err)
		return
	}
	e.ksOn = true
	e.logf("kill switch enabled")
}

func (e *Engine) disableKillSwitch() {
	if !e.ksOn && !e.state.KillSwitchActive {
		return
	}
	if err := killswitch.Disable(); err != nil {
		e.logf("kill switch disable: %v", err)
	}
	e.ksOn = false
	e.state.KillSwitchActive = false
	_ = e.state.Save(e.statePath)
	e.logf("kill switch disabled")
}
