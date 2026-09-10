package tor

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

// TestRenderedTorrcIsAcceptedByTor renders each transport's configuration and
// hands it to a real tor with --verify-config.
//
// This is the test that catches "tor exited during startup: exit status 1".
// Every other test here checks that the file contains what we meant to write;
// only tor can say whether tor will accept it, and a config it rejects is a
// failure the user sees as an application that will not start.
//
// Skipped when no tor binary is installed, so it costs nothing on a machine
// without one and runs wherever there is.
func TestRenderedTorrcIsAcceptedByTor(t *testing.T) {
	torPath, err := exec.LookPath(exeName("tor"))
	if err != nil {
		t.Skip("no tor binary available to validate against")
	}

	// Stand-ins for the pluggable transports: --verify-config parses the
	// configuration, it does not execute them.
	ptDir := t.TempDir()
	lyrebird := filepath.Join(ptDir, exeName("lyrebird"))
	if err := os.WriteFile(lyrebird, []byte("#!/bin/sh\n"), 0o700); err != nil {
		t.Fatal(err)
	}

	geoIP, geoIPv6 := findGeoIP()

	cases := []struct {
		name      string
		transport Transport
		bridges   []string
	}{
		{"direct", TransportDirect, nil},
		{"snowflake", TransportSnowflake, DefaultSnowflakeBridges},
		{"obfs4", TransportObfs4, []string{
			"obfs4 192.0.2.1:443 8DDE47B3E8B0F4C2A0F4C3D3B1E4A5F60718293A cert=abcd iat-mode=0",
		}},
	}

	for _, tc := range cases {
		t.Run(string(tc.name), func(t *testing.T) {
			dataDir := t.TempDir()
			opts := TorrcOptions{
				DataDir:   dataDir,
				Ports:     Ports{SOCKS: 19150, Control: 19151, DNS: 19152},
				Transport: tc.transport,
				Bridges:   tc.bridges,
				Binaries: Binaries{
					Tor:       torPath,
					Snowflake: lyrebird,
					Obfs4:     lyrebird,
					GeoIP:     geoIP,
					GeoIPv6:   geoIPv6,
				},
				// Everything the shaping profiles and full-tunnel mode add, so
				// the options they set are validated too.
				Extra: map[string]string{
					"ConnectionPadding":        "1",
					"ReducedConnectionPadding": "0",
					"CircuitPadding":           "1",
					"ReducedCircuitPadding":    "0",
					"VirtualAddrNetworkIPv4":   "10.192.0.0/10",
				},
				OwningPID: os.Getpid(),
			}

			path, err := WriteTorrc(opts)
			if err != nil {
				t.Fatalf("WriteTorrc: %v", err)
			}

			cmd := exec.Command(torPath, "--verify-config", "-f", path)
			out, err := cmd.CombinedOutput()
			if err != nil {
				body, _ := os.ReadFile(path)
				t.Fatalf("tor rejected the generated configuration: %v\n\n--- tor said ---\n%s\n--- torrc ---\n%s",
					err, strings.TrimSpace(string(out)), body)
			}
			if !strings.Contains(string(out), "Configuration was valid") {
				t.Errorf("unexpected output from --verify-config:\n%s", out)
			}
		})
	}
}

// findGeoIP locates Tor's country database on the test machine, if there is
// one. An empty result simply leaves the option out of the rendered file.
func findGeoIP() (string, string) {
	for _, dir := range []string{"/usr/share/tor", "/usr/local/share/tor"} {
		v4 := filepath.Join(dir, "geoip")
		v6 := filepath.Join(dir, "geoip6")
		if _, err := os.Stat(v4); err == nil {
			if _, err := os.Stat(v6); err != nil {
				v6 = ""
			}
			return v4, v6
		}
	}
	return "", ""
}
