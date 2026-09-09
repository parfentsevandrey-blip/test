package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os/exec"
	"runtime"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/torveil/internal/config"
	"github.com/parfentsevandrey-blip/torveil/internal/core"
	"github.com/parfentsevandrey-blip/torveil/internal/logging"
	"github.com/parfentsevandrey-blip/torveil/internal/proxy"
	"github.com/parfentsevandrey-blip/torveil/internal/shaper"
	"github.com/parfentsevandrey-blip/torveil/internal/tor"
	wruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// statusPushInterval refreshes counters that change continuously (throughput,
// cover-traffic overhead) without waiting for a state transition.
const statusPushInterval = time.Second

// App is the object bound into the web view. Every exported method here is
// callable from the interface as window.go.main.App.<Name>().
type App struct {
	ctx    context.Context
	engine *core.Engine
	logs   *logging.Buffer

	cancelLogSub func()
}

// NewApp creates the binding layer.
func NewApp(engine *core.Engine, logs *logging.Buffer) *App {
	return &App{engine: engine, logs: logs}
}

// startup is called by Wails once the web view exists.
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx

	a.engine.SetOnChange(func() {
		wruntime.EventsEmit(ctx, "status", a.engine.Status())
	})
	a.cancelLogSub = a.logs.Subscribe(func(e logging.Entry) {
		wruntime.EventsEmit(ctx, "log", e)
	})

	go a.pushStatus(ctx)

	if a.engine.Config().AutoConnect {
		go func() {
			// Give the interface a moment to render, so the connection
			// progress is visible rather than happening behind a blank window.
			time.Sleep(500 * time.Millisecond)
			_ = a.Connect()
		}()
	}
}

// shutdown is called by Wails when the window closes. Tearing the session down
// here is what restores the routing table and removes the firewall rules; a
// process that simply exits would leave the machine misconfigured.
func (a *App) shutdown(context.Context) {
	if a.cancelLogSub != nil {
		a.cancelLogSub()
	}
	_ = a.engine.Disconnect()
}

func (a *App) pushStatus(ctx context.Context) {
	ticker := time.NewTicker(statusPushInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			wruntime.EventsEmit(ctx, "status", a.engine.Status())
		}
	}
}

// GetStatus returns the current engine status.
func (a *App) GetStatus() core.Status { return a.engine.Status() }

// GetConfig returns the current configuration.
func (a *App) GetConfig() config.Config { return a.engine.Config() }

// SaveResult tells the interface whether a settings change needs a reconnect.
type SaveResult struct {
	NeedsReconnect bool   `json:"needsReconnect"`
	Error          string `json:"error"`
}

// SaveConfig persists settings, applying what it can to a live session.
func (a *App) SaveConfig(cfg config.Config) SaveResult {
	needsReconnect, err := a.engine.SetConfig(cfg)
	res := SaveResult{NeedsReconnect: needsReconnect}
	if err != nil {
		res.Error = err.Error()
	}
	return res
}

// Connect starts a session.
func (a *App) Connect() string {
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Minute)
	defer cancel()
	if err := a.engine.Connect(ctx); err != nil {
		return err.Error()
	}
	return ""
}

// Disconnect ends the session.
func (a *App) Disconnect() string {
	if err := a.engine.Disconnect(); err != nil {
		return err.Error()
	}
	return ""
}

// NewIdentity rebuilds every circuit.
func (a *App) NewIdentity() string {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := a.engine.NewIdentity(ctx); err != nil {
		return err.Error()
	}
	return ""
}

// GetCountries lists countries with relays, for the entry and exit pickers.
func (a *App) GetCountries() []tor.CountryStat { return a.engine.Countries() }

// GetProfiles returns the traffic-shaping profiles.
func (a *App) GetProfiles() []shaper.Profile { return shaper.Profiles() }

// GetLogs returns the last n log entries.
func (a *App) GetLogs(n int) []logging.Entry { return a.logs.Entries(n) }

// ClearLogs empties the log buffer.
func (a *App) ClearLogs() { a.logs.Clear() }

// GetDefaultSnowflakeBridges returns the shipped bridge lines, so the settings
// screen can offer a one-click reset when a user has pasted broken ones.
func (a *App) GetDefaultSnowflakeBridges() []string { return tor.DefaultSnowflakeBridges }

// ExitCheck is the result of asking the Tor Project which exit relay a request
// appeared to come from.
type ExitCheck struct {
	IP      string `json:"ip"`
	IsTor   bool   `json:"isTor"`
	Error   string `json:"error"`
	Checked string `json:"checked"`
}

// CheckExit asks check.torproject.org how the connection looks from outside.
//
// This is deliberately a button rather than something the application does on
// its own: it is a request to a third party that reveals a live session
// exists, and a privacy tool should not make that request without being asked.
func (a *App) CheckExit() ExitCheck {
	status := a.engine.Status()
	if status.State != core.StateConnected {
		return ExitCheck{Error: "not connected"}
	}
	if status.Proxy.SOCKSAddr == "" {
		return ExitCheck{Error: "the local proxy is not running"}
	}

	upstream := &proxy.Upstream{Address: status.Proxy.SOCKSAddr, Timeout: 60 * time.Second}
	client := &http.Client{
		Timeout:   90 * time.Second,
		Transport: &http.Transport{DialContext: upstream.DialContext},
	}

	resp, err := client.Get("https://check.torproject.org/api/ip")
	if err != nil {
		return ExitCheck{Error: err.Error()}
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 8192))
	if err != nil {
		return ExitCheck{Error: err.Error()}
	}

	var parsed struct {
		IsTor bool   `json:"IsTor"`
		IP    string `json:"IP"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return ExitCheck{Error: fmt.Sprintf("unexpected response: %s", strings.TrimSpace(string(body)))}
	}
	return ExitCheck{
		IP:      parsed.IP,
		IsTor:   parsed.IsTor,
		Checked: time.Now().Format("15:04:05"),
	}
}

// OpenConfigFolder opens the configuration directory in the file manager.
func (a *App) OpenConfigFolder() string {
	dir, err := config.Dir()
	if err != nil {
		return err.Error()
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", dir)
	case "darwin":
		cmd = exec.Command("open", dir)
	default:
		cmd = exec.Command("xdg-open", dir)
	}
	// explorer.exe reports a non-zero exit status even when it succeeds, so
	// the error is deliberately not surfaced here.
	_ = cmd.Start()
	return ""
}
