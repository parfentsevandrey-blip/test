package veiltun

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/ulikunitz/xz"
)

// ExtractXz decompresses one xz file into destination, atomically.
//
// This exists for the directory seed: the consensus, the microdescriptors and
// the authority certificates that tor would otherwise download on its first
// run, shipped in the APK compressed. xz because the material is base64 keys
// and it makes the difference between two megabytes and eighteen; Go because
// the app has no Java xz decoder available and already carries this module.
//
// Written to a temporary file beside the destination and renamed into place,
// so a kill part-way through leaves no half-written cache for tor to trip on.
func ExtractXz(source, destination string) error {
	in, err := os.Open(source)
	if err != nil {
		return fmt.Errorf("seed: %w", err)
	}
	defer in.Close()

	reader, err := xz.NewReader(in)
	if err != nil {
		return fmt.Errorf("seed: not xz: %w", err)
	}

	if err := os.MkdirAll(filepath.Dir(destination), 0o700); err != nil {
		return fmt.Errorf("seed: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(destination), ".seed-*")
	if err != nil {
		return fmt.Errorf("seed: %w", err)
	}
	tmpName := tmp.Name()
	if _, err := io.Copy(tmp, reader); err != nil {
		tmp.Close()
		os.Remove(tmpName)
		return fmt.Errorf("seed: %w", err)
	}
	if err := tmp.Close(); err != nil {
		os.Remove(tmpName)
		return fmt.Errorf("seed: %w", err)
	}
	if err := os.Chmod(tmpName, 0o600); err != nil {
		os.Remove(tmpName)
		return fmt.Errorf("seed: %w", err)
	}
	if err := os.Rename(tmpName, destination); err != nil {
		os.Remove(tmpName)
		return fmt.Errorf("seed: %w", err)
	}
	return nil
}
