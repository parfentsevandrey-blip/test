// pack gzips the helper binaries from bin/ into cmd/torss/bundle/ so that
// `go build ./cmd/torss` produces a single self-contained torss.exe.
//
//	go run ./tools/pack            # bin/ -> cmd/torss/bundle/
//	go run ./tools/pack -clean     # remove the bundle again (small exe, external bin/)
package main

import (
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

type manifest struct {
	Version string            `json:"version"`
	Files   map[string]string `json:"files"`
}

// Only these files are needed at runtime (docs, tor-gencert etc. are skipped).
var wanted = []string{
	"sing-box.exe",
	"wintun.dll",
	"tor/tor.exe",
	"tor/pluggable_transports/lyrebird.exe",
	"tor/pluggable_transports/conjure-client.exe",
	"tor/pluggable_transports/pt_config.json",
}

// GeoIP databases (26 MB) are only needed for ExcludeNodes / country stats;
// they are packed only with -geoip.
var geoip = []string{"tor/data/geoip", "tor/data/geoip6"}

func main() {
	src := flag.String("bin", "bin", "source directory with binaries")
	dst := flag.String("out", filepath.Join("cmd", "torss", "bundle"), "bundle directory")
	version := flag.String("version", "", "version string stored in the manifest")
	clean := flag.Bool("clean", false, "remove packed files")
	withGeoIP := flag.Bool("geoip", false, "also pack Tor's GeoIP databases")
	flag.Parse()
	files := wanted
	if *withGeoIP {
		files = append(files, geoip...)
	}

	entries, _ := os.ReadDir(*dst)
	for _, e := range entries {
		if e.Name() != "README.txt" {
			_ = os.RemoveAll(filepath.Join(*dst, e.Name()))
		}
	}
	if *clean {
		fmt.Println("bundle cleaned")
		return
	}

	m := manifest{Version: *version, Files: map[string]string{}}
	total := int64(0)
	for _, rel := range files {
		in := filepath.Join(*src, filepath.FromSlash(rel))
		st, err := os.Stat(in)
		if err != nil {
			if strings.Contains(rel, "conjure") || strings.Contains(rel, "geoip") {
				fmt.Printf("skip optional %s\n", rel)
				continue
			}
			fatal("missing %s (run scripts/fetch-deps.ps1 first)", in)
		}
		out := filepath.Join(*dst, filepath.FromSlash(rel)+".gz")
		sum, err := packFile(in, out)
		if err != nil {
			fatal("%s: %v", rel, err)
		}
		m.Files[rel] = sum
		total += st.Size()
		ost, _ := os.Stat(out)
		fmt.Printf("%-48s %9d -> %9d\n", rel, st.Size(), ost.Size())
	}
	data, _ := json.MarshalIndent(m, "", "  ")
	if err := os.WriteFile(filepath.Join(*dst, "manifest.json"), data, 0o644); err != nil {
		fatal("%v", err)
	}
	fmt.Printf("packed %d files, %d bytes uncompressed -> %s\n", len(m.Files), total, *dst)
}

func packFile(in, out string) (string, error) {
	if err := os.MkdirAll(filepath.Dir(out), 0o755); err != nil {
		return "", err
	}
	f, err := os.Open(in)
	if err != nil {
		return "", err
	}
	defer f.Close()
	o, err := os.Create(out)
	if err != nil {
		return "", err
	}
	defer o.Close()
	gz, err := gzip.NewWriterLevel(o, gzip.BestCompression)
	if err != nil {
		return "", err
	}
	h := sha256.New()
	if _, err := io.Copy(io.MultiWriter(gz, h), f); err != nil {
		return "", err
	}
	if err := gz.Close(); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func fatal(format string, a ...any) {
	fmt.Fprintf(os.Stderr, "pack: "+format+"\n", a...)
	os.Exit(1)
}
