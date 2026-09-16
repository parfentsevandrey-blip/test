package engine

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"torss/internal/config"
	"torss/internal/tor"
)

// TestSmoke brings the real chain up on the host running the tests:
// a local sing-box acting as the Shadowsocks+ShadowTLS *server*, then the
// engine in ss-only, ss-tor and tor-direct modes (proxy mode, no TUN).
// Needs network access and:
//
//	TORSS_SMOKE=1 SINGBOX_BIN=/path/sing-box TOR_BUNDLE_DIR=/path/tor-expert-bundle/tor go test ./internal/engine -run Smoke -v
func TestSmoke(t *testing.T) {
	if os.Getenv("TORSS_SMOKE") == "" {
		t.Skip("set TORSS_SMOKE=1 to run")
	}
	singbox := os.Getenv("SINGBOX_BIN")
	torDir := os.Getenv("TOR_BUNDLE_DIR")
	if singbox == "" || torDir == "" {
		t.Fatal("SINGBOX_BIN and TOR_BUNDLE_DIR are required")
	}
	exe := func(b string) string {
		if runtime.GOOS == "windows" {
			return b + ".exe"
		}
		return b
	}

	// --- local "VPS": sing-box with shadowtls -> shadowsocks-2022 ------------
	const stlsPort, ssPort = 18443, 18388
	const ssPassword = "8JCsPssfgS8tiRwiMlhARg=="
	const stlsPassword = "smoke-shadowtls-password"
	serverCfg := map[string]any{
		"log": map[string]any{"level": "warn"},
		"inbounds": []map[string]any{
			{
				"type": "shadowtls", "tag": "stls-in", "listen": "127.0.0.1", "listen_port": stlsPort,
				"version": 3, "users": []map[string]any{{"password": stlsPassword}},
				"handshake":   map[string]any{"server": "gateway.icloud.com", "server_port": 443},
				"strict_mode": true, "detour": "ss-in",
			},
			{
				"type": "shadowsocks", "tag": "ss-in", "listen": "127.0.0.1", "listen_port": ssPort,
				"method": "2022-blake3-aes-128-gcm", "password": ssPassword,
			},
		},
		"outbounds": []map[string]any{{"type": "direct", "tag": "direct"}},
	}
	dir := t.TempDir()
	srvPath := filepath.Join(dir, "server.json")
	data, _ := json.MarshalIndent(serverCfg, "", "  ")
	if err := os.WriteFile(srvPath, data, 0o600); err != nil {
		t.Fatal(err)
	}
	srv := exec.Command(singbox, "run", "-c", srvPath, "-D", dir)
	srv.Stdout, srv.Stderr = os.Stdout, os.Stderr
	if err := srv.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = srv.Process.Kill(); _, _ = srv.Process.Wait() })
	time.Sleep(1500 * time.Millisecond)

	// --- engine --------------------------------------------------------------
	cfg := config.Default()
	cfg.TUN = false
	cfg.SystemProxy = false
	cfg.Server = config.Server{
		Address: "127.0.0.1", Port: stlsPort, Method: "2022-blake3-aes-128-gcm", Password: ssPassword,
		ShadowTLS: config.ShadowTLS{Enabled: true, Password: stlsPassword, SNI: "gateway.icloud.com"},
	}
	cfg.LogLevel = "info"
	dataDir := filepath.Join(dir, "data")
	paths := Paths{
		BinDir:  filepath.Dir(singbox),
		DataDir: dataDir,
		SingBox: singbox,
		Tor: tor.Layout{
			Dir:      torDir,
			Exe:      exe("tor"),
			Lyrebird: filepath.Join("pluggable_transports", exe("lyrebird")),
		},
	}
	logf := func(f string, a ...any) { t.Logf(f, a...) }
	state := &config.State{}
	e := New(cfg, filepath.Join(dir, "config.json"), state, filepath.Join(dir, "state.json"), paths, logf, nil)

	for _, m := range []config.Mode{config.ModeSSOnly, config.ModeSSTor, config.ModeTorDirect} {
		t.Run(string(m), func(t *testing.T) {
			ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
			defer cancel()
			start := time.Now()
			err := e.tryMode(ctx, m)
			e.teardownProcesses()
			if err != nil {
				t.Fatalf("mode %s failed after %s: %v", m, time.Since(start).Round(time.Second), err)
			}
			fmt.Printf("SMOKE OK: %s in %s\n", m, time.Since(start).Round(time.Second))
		})
	}
}
