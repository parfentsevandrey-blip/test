//go:build windows

package bundle

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	"golang.org/x/sys/windows"
)

// EnsureWintunLoadable makes the bundled wintun.dll findable by the Wintun
// bindings.
//
// Those bindings call LoadLibraryEx with LOAD_LIBRARY_SEARCH_APPLICATION_DIR
// and LOAD_LIBRARY_SEARCH_SYSTEM32, which means the DLL is looked for beside
// the executable and in System32 — and nowhere else. Neither PATH nor
// SetDllDirectory has any effect on a call made with those flags, so the
// unpacked copy in the user's profile is invisible to it.
//
// Two ways out, tried in order:
//
//  1. Copy it beside the executable. That satisfies the search flags
//     directly, and is what happens whenever the program directory is
//     writable, which it is in the normal case of running from Downloads.
//  2. Load it here by absolute path. Once a module with that base name is in
//     the process, the loader returns it rather than searching again, so the
//     later call succeeds without the file ever being beside the executable.
//     This covers an executable installed somewhere read-only.
func EnsureWintunLoadable(dllPath string) error {
	if dllPath == "" {
		return fmt.Errorf("this build carries no wintun.dll")
	}
	if _, err := os.Stat(dllPath); err != nil {
		return fmt.Errorf("bundled wintun.dll is missing: %w", err)
	}

	if exe, err := os.Executable(); err == nil {
		beside := filepath.Join(filepath.Dir(exe), "wintun.dll")
		if sameFileContents(beside, dllPath) {
			return nil
		}
		if err := copyFile(dllPath, beside); err == nil {
			return nil
		}
	}

	if _, err := windows.LoadLibraryEx(dllPath, 0, windows.LOAD_WITH_ALTERED_SEARCH_PATH); err != nil {
		return fmt.Errorf("load bundled wintun.dll from %s: %w", dllPath, err)
	}
	return nil
}

// sameFileContents reports whether both paths exist with identical size. Size
// alone is enough here: the only file that ever lands at the destination is a
// copy of the source, so a match means the copy is already done.
func sameFileContents(a, b string) bool {
	sa, err := os.Stat(a)
	if err != nil {
		return false
	}
	sb, err := os.Stat(b)
	if err != nil {
		return false
	}
	return sa.Size() == sb.Size()
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	// Write to a temporary name and rename, so a copy interrupted halfway
	// cannot leave a truncated DLL that the loader would happily map.
	tmp := dst + ".new"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		os.Remove(tmp)
		return err
	}
	if err := out.Close(); err != nil {
		os.Remove(tmp)
		return err
	}
	// A running tunnel holds the old DLL open, so replacing it fails; that is
	// fine, the loaded one is the same file.
	if err := os.Rename(tmp, dst); err != nil {
		os.Remove(tmp)
		return err
	}
	return nil
}
