package magic

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLocalAddrsFromTheAppsFile(t *testing.T) {
	f := filepath.Join(t.TempDir(), "addrs")
	body := "192.168.1.5, fe80::1\n10.0.0.7;;127.0.0.1 not-an-address 2001:db8::5 192.168.1.5 224.0.0.1 0.0.0.0\n"
	if err := os.WriteFile(f, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("THEMESH_LOCAL_ADDRS_FILE", f)
	got := map[string]bool{}
	for _, a := range DefaultLocalAddrs() {
		got[a.String()] = true
	}
	for _, want := range []string{"192.168.1.5", "10.0.0.7", "2001:db8::5"} {
		if !got[want] {
			t.Errorf("%s from the file is missing: %v", want, got)
		}
	}
	for _, bad := range []string{"fe80::1", "127.0.0.1", "224.0.0.1", "0.0.0.0", "not-an-address"} {
		if got[bad] {
			t.Errorf("%s must not be offered as an endpoint", bad)
		}
	}
	t.Setenv("THEMESH_LOCAL_ADDRS_FILE", filepath.Join(t.TempDir(), "missing"))
	_ = DefaultLocalAddrs() // a missing file is just an empty list
}
