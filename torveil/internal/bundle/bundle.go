// Package bundle unpacks the Tor runtime that TorVeil carries inside its own
// executable.
//
// TorVeil ships Tor rather than asking the user to install it, so a single
// file is the whole application. The binaries are not modified: they are the
// Tor Project's own Expert Bundle and WireGuard LLC's Wintun, downloaded and
// signature-verified at build time by build/fetch-assets.sh, packed into one
// archive, and written out on first launch.
//
// They are unpacked to disk rather than run from memory because tor.exe is a
// separate process and Windows needs a real file to execute, and because
// wintun.dll must be a file the loader can map.
package bundle

import (
	"archive/tar"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/klauspost/compress/zstd"
)

// ErrNotBundled reports that this build carries no runtime, which is what a
// plain "go build" without the bundled tag produces.
var ErrNotBundled = errors.New("this build does not embed a Tor runtime")

// maxEntrySize caps a single extracted file, so a corrupted or hostile archive
// cannot fill the disk. The largest real member is lyrebird.exe at ~18 MB.
const maxEntrySize = 256 << 20

// LogFunc receives progress messages; extraction takes a moment on first run.
type LogFunc func(level, msg string)

// Runtime describes an unpacked runtime on disk.
type Runtime struct {
	// Dir is the root the archive was extracted into.
	Dir string

	// Version identifies the archive by content, so a rebuilt bundle unpacks
	// beside the old one rather than over it.
	Version string

	// WintunDLL is the path to wintun.dll, or "" when the archive has none.
	//
	// It needs handling of its own: the Wintun bindings load the library with
	// LOAD_LIBRARY_SEARCH_APPLICATION_DIR, which looks next to the executable
	// and nowhere else, so this copy has to be pre-loaded by full path.
	WintunDLL string
}

// SearchDir returns the directory to hand to the Tor locator.
func (r Runtime) SearchDir() string { return r.Dir }

// Available reports whether this build carries a runtime.
func Available() bool { return len(archive()) > 0 }

// Ensure unpacks the embedded runtime under baseDir and returns it, extracting
// only when this exact archive is not already there.
//
// Extraction is atomic: it unpacks into a temporary directory and renames it
// into place, so an interrupted first run cannot leave a half-written Tor that
// looks complete on the next launch.
func Ensure(baseDir string, log LogFunc) (Runtime, error) {
	data := archive()
	if len(data) == 0 {
		return Runtime{}, ErrNotBundled
	}

	sum := sha256.Sum256(data)
	version := hex.EncodeToString(sum[:])[:16]
	dir := filepath.Join(baseDir, "runtime", version)

	rt := Runtime{Dir: dir, Version: version}
	if wintun := filepath.Join(dir, "wintun", "wintun.dll"); fileExists(wintun) {
		rt.WintunDLL = wintun
	}

	if fileExists(filepath.Join(dir, ".complete")) {
		return rt, nil
	}

	if log != nil {
		log("info", fmt.Sprintf("unpacking the bundled Tor runtime (%s) into %s", version, dir))
	}

	tmp := dir + ".unpacking"
	if err := os.RemoveAll(tmp); err != nil {
		return Runtime{}, fmt.Errorf("clear stale unpack directory: %w", err)
	}
	if err := os.MkdirAll(tmp, 0o700); err != nil {
		return Runtime{}, fmt.Errorf("create unpack directory: %w", err)
	}
	defer os.RemoveAll(tmp)

	if err := extract(data, tmp); err != nil {
		return Runtime{}, fmt.Errorf("unpack the bundled Tor runtime: %w", err)
	}
	if err := os.WriteFile(filepath.Join(tmp, ".complete"), []byte(version+"\n"), 0o600); err != nil {
		return Runtime{}, fmt.Errorf("mark the runtime complete: %w", err)
	}

	// Another instance may have won the race and already renamed its copy
	// into place; its content is identical, so that is a success, not a
	// conflict.
	if err := os.Rename(tmp, dir); err != nil {
		if fileExists(filepath.Join(dir, ".complete")) {
			return rt, nil
		}
		return Runtime{}, fmt.Errorf("move the unpacked runtime into place: %w", err)
	}

	if wintun := filepath.Join(dir, "wintun", "wintun.dll"); fileExists(wintun) {
		rt.WintunDLL = wintun
	}
	pruneOldVersions(filepath.Join(baseDir, "runtime"), version, log)
	return rt, nil
}

// extract writes the zstd-compressed tar in data into dir.
//
// zstd rather than gzip: it is a third smaller here, which is a third off the
// download, and it decompresses fast enough that unpacking 55 MB on first
// launch is not something the user waits for.
func extract(data []byte, dir string) error {
	zr, err := zstd.NewReader(newByteReader(data))
	if err != nil {
		return fmt.Errorf("read the runtime archive: %w", err)
	}
	defer zr.Close()

	tr := tar.NewReader(zr)
	for {
		hdr, err := tr.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}

		target, err := safeJoin(dir, hdr.Name)
		if err != nil {
			return err
		}

		switch hdr.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0o700); err != nil {
				return err
			}
		case tar.TypeReg:
			if hdr.Size > maxEntrySize {
				return fmt.Errorf("archive member %q is %d bytes, which is implausible", hdr.Name, hdr.Size)
			}
			if err := os.MkdirAll(filepath.Dir(target), 0o700); err != nil {
				return err
			}
			// 0o700: the runtime lives in the user's own profile and only
			// they need to run it.
			f, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o700)
			if err != nil {
				return err
			}
			written, err := io.Copy(f, io.LimitReader(tr, maxEntrySize+1))
			closeErr := f.Close()
			if err != nil {
				return fmt.Errorf("write %s: %w", hdr.Name, err)
			}
			if closeErr != nil {
				return fmt.Errorf("write %s: %w", hdr.Name, closeErr)
			}
			if written != hdr.Size {
				return fmt.Errorf("%s is truncated: wrote %d of %d bytes", hdr.Name, written, hdr.Size)
			}
		default:
			// The archive is produced by our own build script and contains
			// only files and directories. Anything else means it is not the
			// archive we think it is.
			return fmt.Errorf("unexpected entry type %q in the runtime archive (%s)", hdr.Typeflag, hdr.Name)
		}
	}
}

// safeJoin resolves an archive member against dir, refusing anything that
// would escape it.
//
// The archive is ours, but path traversal is cheap to prevent and expensive to
// discover later, and this code writes executables that are then run.
func safeJoin(dir, name string) (string, error) {
	clean := filepath.Clean(strings.ReplaceAll(name, `\`, "/"))
	clean = strings.TrimPrefix(clean, "./")
	if clean == "." || clean == "" {
		return dir, nil
	}
	if filepath.IsAbs(clean) || strings.HasPrefix(clean, "..") {
		return "", fmt.Errorf("archive member %q escapes the destination directory", name)
	}
	target := filepath.Join(dir, clean)
	if !strings.HasPrefix(target, dir+string(os.PathSeparator)) && target != dir {
		return "", fmt.Errorf("archive member %q escapes the destination directory", name)
	}
	return target, nil
}

// pruneOldVersions deletes runtimes left by earlier builds, which are ~70 MB
// each and are never used again once the executable has changed.
func pruneOldVersions(root, keep string, log LogFunc) {
	entries, err := os.ReadDir(root)
	if err != nil {
		return
	}
	for _, e := range entries {
		if !e.IsDir() || e.Name() == keep {
			continue
		}
		if err := os.RemoveAll(filepath.Join(root, e.Name())); err == nil && log != nil {
			log("info", "removed the runtime from a previous version: "+e.Name())
		}
	}
}

func fileExists(path string) bool {
	st, err := os.Stat(path)
	return err == nil && !st.IsDir()
}
