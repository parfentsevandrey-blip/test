package bundle

import (
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"testing/fstest"
)

func gz(t *testing.T, data []byte) []byte {
	var b bytes.Buffer
	w := gzip.NewWriter(&b)
	if _, err := w.Write(data); err != nil {
		t.Fatal(err)
	}
	w.Close()
	return b.Bytes()
}

func TestExtract(t *testing.T) {
	sb := []byte("fake sing-box")
	tor := []byte("fake tor")
	sum := func(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:]) }
	m := Manifest{Version: "test", Files: map[string]string{
		"sing-box.exe": sum(sb),
		"tor/tor.exe":  sum(tor),
	}}
	mj, _ := json.Marshal(m)
	efs := fstest.MapFS{
		"bundle/manifest.json":   {Data: mj},
		"bundle/sing-box.exe.gz": {Data: gz(t, sb)},
		"bundle/tor/tor.exe.gz":  {Data: gz(t, tor)},
	}
	dest := t.TempDir()
	n, err := Extract(efs, "bundle", dest, nil)
	if err != nil {
		t.Fatal(err)
	}
	if n != 2 {
		t.Fatalf("extracted %d, want 2", n)
	}
	got, _ := os.ReadFile(filepath.Join(dest, "tor", "tor.exe"))
	if string(got) != "fake tor" {
		t.Fatalf("bad content %q", got)
	}
	// second run: nothing to do
	n, err = Extract(efs, "bundle", dest, nil)
	if err != nil || n != 0 {
		t.Fatalf("second extract: n=%d err=%v", n, err)
	}
	// corrupted file gets replaced
	os.WriteFile(filepath.Join(dest, "sing-box.exe"), []byte("garbage"), 0o755)
	n, err = Extract(efs, "bundle", dest, nil)
	if err != nil || n != 1 {
		t.Fatalf("repair extract: n=%d err=%v", n, err)
	}
	// hash mismatch is refused
	m.Files["tor/tor.exe"] = sum([]byte("other"))
	mj, _ = json.Marshal(m)
	efs["bundle/manifest.json"] = &fstest.MapFile{Data: mj}
	os.Remove(filepath.Join(dest, "tor", "tor.exe"))
	if _, err := Extract(efs, "bundle", dest, nil); err == nil {
		t.Fatal("expected sha256 mismatch error")
	}
}

func TestNoBundle(t *testing.T) {
	efs := fstest.MapFS{"bundle/README.txt": {Data: []byte("x")}}
	m, err := ReadManifest(efs, "bundle")
	if err != nil || m != nil {
		t.Fatalf("m=%v err=%v", m, err)
	}
	n, err := Extract(efs, "bundle", t.TempDir(), nil)
	if err != nil || n != 0 {
		t.Fatalf("n=%d err=%v", n, err)
	}
}
