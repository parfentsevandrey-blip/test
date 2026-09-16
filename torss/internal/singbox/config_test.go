package singbox

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"torss/internal/config"
)

func testServer() config.Server {
	return config.Server{
		Address:  "203.0.113.10",
		Port:     443,
		Method:   "2022-blake3-aes-128-gcm",
		Password: "8JCsPssfgS8tiRwiMlhARg==",
		ShadowTLS: config.ShadowTLS{
			Enabled:  true,
			Password: "stls-secret",
			SNI:      "gateway.icloud.com",
		},
	}
}

func TestGenerateAllModes(t *testing.T) {
	singbox := os.Getenv("SINGBOX_BIN")
	for _, tun := range []bool{true, false} {
		for _, mode := range config.AllModes {
			for _, stls := range []bool{true, false} {
				srv := testServer()
				srv.ShadowTLS.Enabled = stls
				cfg, err := Generate(Options{
					Mode:             mode,
					TUN:              tun,
					Ports:            config.Default().Ports,
					Server:           srv,
					LogLevel:         "info",
					CacheDB:          "cache.db",
					ExcludeProcesses: []string{"tor.exe", "lyrebird.exe", "conjure-client.exe"},
				})
				if err != nil {
					t.Fatalf("%s tun=%v: %v", mode, tun, err)
				}
				var parsed map[string]any
				if err := json.Unmarshal(cfg, &parsed); err != nil {
					t.Fatalf("%s: invalid json: %v", mode, err)
				}
				if singbox == "" {
					continue
				}
				dir := t.TempDir()
				p := filepath.Join(dir, "c.json")
				if err := os.WriteFile(p, cfg, 0o600); err != nil {
					t.Fatal(err)
				}
				out, err := exec.Command(singbox, "check", "-c", p).CombinedOutput()
				if err != nil {
					t.Errorf("sing-box check failed for %s tun=%v stls=%v: %v\n%s\n%s", mode, tun, stls, err, out, cfg)
				}
			}
		}
	}
}

func TestGenerateWithoutServer(t *testing.T) {
	_, err := Generate(Options{Mode: config.ModeSSTor, Ports: config.Default().Ports})
	if err == nil {
		t.Fatal("expected error for ss-tor without server")
	}
	cfg, err := Generate(Options{Mode: config.ModeTorSnowflake, TUN: true, Ports: config.Default().Ports})
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		Outbounds []struct{ Tag string } `json:"outbounds"`
		Inbounds  []struct{ Tag string } `json:"inbounds"`
	}
	if err := json.Unmarshal(cfg, &parsed); err != nil {
		t.Fatal(err)
	}
	for _, o := range parsed.Outbounds {
		if o.Tag == "ss" || o.Tag == "shadowtls" {
			t.Fatalf("unexpected outbound %s without server", o.Tag)
		}
	}
	for _, i := range parsed.Inbounds {
		if i.Tag == "ss-entry" {
			t.Fatal("unexpected ss-entry inbound without server")
		}
	}
}
