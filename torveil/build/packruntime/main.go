// Command packruntime packs the verified Tor runtime into the archive that
// TorVeil embeds.
//
// It exists as a Go program rather than a line of shell because the archive is
// zstd, and no zstd command-line tool can be assumed present. Anywhere Go
// builds, this runs.
//
// The archive is written deterministically — sorted entries, fixed timestamps,
// no ownership — so the same inputs produce the same bytes and two builds can
// be compared.
package main

import (
	"archive/tar"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"sort"

	"github.com/klauspost/compress/zstd"
)

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: packruntime <source directory> <output archive>")
		os.Exit(2)
	}
	if err := run(os.Args[1], os.Args[2]); err != nil {
		fmt.Fprintf(os.Stderr, "packruntime: %v\n", err)
		os.Exit(1)
	}
}

func run(src, dst string) error {
	files, err := collect(src)
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return fmt.Errorf("%s contains no files", src)
	}

	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return err
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	// Best compression with a large window: this runs once per release, while
	// the size is paid by everyone who downloads the executable.
	zw, err := zstd.NewWriter(out,
		zstd.WithEncoderLevel(zstd.SpeedBestCompression),
		zstd.WithWindowSize(1<<24),
	)
	if err != nil {
		return err
	}
	tw := tar.NewWriter(zw)

	for _, rel := range files {
		if err := add(tw, src, rel); err != nil {
			return fmt.Errorf("add %s: %w", rel, err)
		}
	}

	if err := tw.Close(); err != nil {
		return err
	}
	if err := zw.Close(); err != nil {
		return err
	}
	if err := out.Close(); err != nil {
		return err
	}

	st, err := os.Stat(dst)
	if err != nil {
		return err
	}
	fmt.Printf("packed %d files into %s (%.1f MB)\n", len(files), dst, float64(st.Size())/(1<<20))
	return nil
}

// collect returns every regular file under root, as sorted slash-separated
// relative paths.
func collect(root string) ([]string, error) {
	var files []string
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		// Only regular files. A symlink in the source tree would be a
		// surprise worth failing on rather than silently following.
		if !d.Type().IsRegular() {
			return fmt.Errorf("%s is not a regular file", path)
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		files = append(files, filepath.ToSlash(rel))
		return nil
	})
	if err != nil {
		return nil, err
	}
	sort.Strings(files)
	return files, nil
}

func add(tw *tar.Writer, root, rel string) error {
	path := filepath.Join(root, filepath.FromSlash(rel))
	info, err := os.Stat(path)
	if err != nil {
		return err
	}

	// Everything is packed executable: which members are programs is a
	// Windows question decided by extension, and the extraction side writes
	// its own modes anyway.
	hdr := &tar.Header{
		Name:     rel,
		Mode:     0o700,
		Size:     info.Size(),
		Typeflag: tar.TypeReg,
		Format:   tar.FormatPAX,
	}
	if err := tw.WriteHeader(hdr); err != nil {
		return err
	}

	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	written, err := io.Copy(tw, f)
	if err != nil {
		return err
	}
	if written != info.Size() {
		return fmt.Errorf("wrote %d of %d bytes", written, info.Size())
	}
	return nil
}
