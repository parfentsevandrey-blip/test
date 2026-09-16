// Package bundle extracts helper binaries embedded into torss.exe, so a single
// file is enough to run: sing-box.exe, wintun.dll and the Tor Expert Bundle
// are stored gzip-compressed under cmd/torss/bundle/ at build time.
package bundle

import (
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

// Manifest lists the bundled files; written by tools/pack.
type Manifest struct {
	Version string            `json:"version"` // free-form (component versions)
	Files   map[string]string `json:"files"`   // relative path -> sha256 of the uncompressed file
}

const manifestName = "manifest.json"

// ReadManifest returns the manifest from an embedded FS rooted at dir, or nil
// if the build carries no bundle.
func ReadManifest(efs fs.FS, dir string) (*Manifest, error) {
	data, err := fs.ReadFile(efs, dir+"/"+manifestName)
	if err != nil {
		return nil, nil
	}
	var m Manifest
	if err := json.Unmarshal(data, &m); err != nil {
		return nil, fmt.Errorf("bundle manifest: %w", err)
	}
	return &m, nil
}

// Extract writes the bundled files into dest (skipping files whose sha256
// already matches). It returns the number of files written.
func Extract(efs fs.FS, dir, dest string, logf func(string, ...any)) (int, error) {
	m, err := ReadManifest(efs, dir)
	if err != nil {
		return 0, err
	}
	if m == nil {
		return 0, nil
	}
	written := 0
	for rel, want := range m.Files {
		target := filepath.Join(dest, filepath.FromSlash(rel))
		if have, err := fileSHA256(target); err == nil && have == want {
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return written, err
		}
		src, err := efs.Open(dir + "/" + rel + ".gz")
		if err != nil {
			return written, fmt.Errorf("bundle: %s missing: %w", rel, err)
		}
		if err := writeGunzip(src, target, want); err != nil {
			src.Close()
			return written, fmt.Errorf("bundle: extract %s: %w", rel, err)
		}
		src.Close()
		written++
		if logf != nil {
			logf("bundle: extracted %s", rel)
		}
	}
	// Keep a copy of the manifest next to the files for humans.
	data, _ := json.MarshalIndent(m, "", "  ")
	_ = os.WriteFile(filepath.Join(dest, manifestName), data, 0o644)
	return written, nil
}

func writeGunzip(src io.Reader, target, wantSHA string) error {
	gz, err := gzip.NewReader(src)
	if err != nil {
		return err
	}
	defer gz.Close()
	tmp := target + ".tmp"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	h := sha256.New()
	if _, err := io.Copy(io.MultiWriter(f, h), gz); err != nil {
		f.Close()
		os.Remove(tmp)
		return err
	}
	if err := f.Close(); err != nil {
		os.Remove(tmp)
		return err
	}
	if got := hex.EncodeToString(h.Sum(nil)); !strings.EqualFold(got, wantSHA) {
		os.Remove(tmp)
		return fmt.Errorf("sha256 mismatch: %s != %s", got, wantSHA)
	}
	// A running old copy (previous torss still exiting) can hold the file: retry once.
	if err := os.Rename(tmp, target); err != nil {
		_ = os.Remove(target)
		if err := os.Rename(tmp, target); err != nil {
			os.Remove(tmp)
			return err
		}
	}
	return nil
}

func fileSHA256(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
