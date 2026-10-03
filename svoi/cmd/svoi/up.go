package main

import (
	"flag"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/web"
)

func version() string { return mesh.Version }

// defaultDir picks the data directory.
func defaultDir() string {
	if d := os.Getenv("SVOI_DIR"); d != "" {
		return d
	}
	if d, err := os.UserConfigDir(); err == nil && d != "" {
		return filepath.Join(d, "svoi")
	}
	if os.Geteuid() == 0 {
		return "/var/lib/svoi"
	}
	return ".svoi"
}

type commonFlags struct {
	dir string
}

func (c *commonFlags) register(fs *flag.FlagSet) {
	fs.StringVar(&c.dir, "dir", defaultDir(), "data directory")
}

func newLogger(debug bool, w io.Writer) *slog.Logger {
	lvl := slog.LevelInfo
	if debug {
		lvl = slog.LevelDebug
	}
	return slog.New(slog.NewTextHandler(w, &slog.HandlerOptions{Level: lvl}))
}

func cmdUp(args []string) error {
	fs := flag.NewFlagSet("up", flag.ExitOnError)
	var cf commonFlags
	cf.register(fs)
	uiAddr := fs.String("ui", "127.0.0.1:8777", "address for the web interface (loopback by default)")
	noBrowser := fs.Bool("no-browser", false, "do not open the browser")
	name := fs.String("name", "", "device name offered when creating/joining a mesh (default: hostname)")
	owner := fs.String("owner", "", "owner name offered when creating/joining a mesh")
	debug := fs.Bool("debug", false, "verbose logging")
	loopback := fs.Bool("loopback", false, "also advertise 127.0.0.1 (several nodes on one machine)")
	tunOn := fs.Bool("tun", false, "create the svoi0 network interface (Linux, needs root): reach devices by IP or <name>.svoi from any program")
	noSTUN := fs.Bool("no-stun", false, "do not use public STUN servers (peers still tell each other how they see us)")
	fs.Parse(args)

	a, err := app.Open(app.Options{
		Dir:        cf.dir,
		Logger:     newLogger(*debug, os.Stderr),
		DeviceName: *name,
		Owner:      *owner,
		Mesh:       mesh.Config{Loopback: *loopback},
	})
	if err != nil {
		return err
	}
	defer a.Close()
	if *noSTUN && a.Settings().STUNEnabled {
		off := false
		if _, err := a.UpdateSettings(app.SettingsPatch{STUNEnabled: &off}); err != nil {
			return err
		}
	}

	if *tunOn && !a.Settings().TUN.Enabled {
		on, hosts := true, true
		if _, err := a.UpdateSettings(app.SettingsPatch{TUN: &app.TUNPatch{Enabled: &on, ManageHosts: &hosts}}); err != nil {
			fmt.Fprintln(os.Stderr, "warning: TUN mode:", err)
		}
	}

	srv := api.New(a, web.UI())
	ln, err := srv.Listen(*uiAddr, false)
	if err != nil {
		return fmt.Errorf("cannot start the web interface on %s: %w", *uiAddr, err)
	}
	_ = os.WriteFile(filepath.Join(cf.dir, "ui.addr"), []byte(ln.Addr().String()+"\n"), 0o600)
	defer os.Remove(filepath.Join(cf.dir, "ui.addr"))

	self := a.Node().Self()
	fmt.Fprintf(os.Stderr, "\nsvoi %s\n", version())
	if self.Configured {
		fmt.Fprintf(os.Stderr, "  device:  %s  (%s)\n  mesh:    %s\n", self.Name, self.IP4, self.MeshName)
	} else {
		fmt.Fprintf(os.Stderr, "  this device is not part of a mesh yet — create one or join in the browser\n")
	}
	loginURL := srv.URL()
	fmt.Fprintf(os.Stderr, "  open:    %s\n           (a one-time link, valid 10 minutes; `svoi open` makes a new one)\n  data:    %s\n\n", loginURL, cf.dir)

	errc := make(chan error, 1)
	go func() { errc <- srv.Serve(ln) }()
	if !*noBrowser && interactive() {
		go func() {
			time.Sleep(300 * time.Millisecond)
			openBrowser(loginURL)
		}()
	}
	select {
	case <-signalChan():
		fmt.Fprintln(os.Stderr, "shutting down…")
		srv.Close()
		return nil
	case err := <-errc:
		return err
	}
}

// interactive reports whether we were started by a person (double-click or a
// terminal) rather than as a background service.
func interactive() bool {
	if runtime.GOOS == "windows" || runtime.GOOS == "darwin" {
		return true
	}
	if os.Getenv("DISPLAY") != "" || os.Getenv("WAYLAND_DISPLAY") != "" {
		return true
	}
	_, termux := os.LookupEnv("TERMUX_VERSION")
	return termux
}

func openBrowser(url string) {
	var cmd *exec.Cmd
	switch {
	case os.Getenv("TERMUX_VERSION") != "":
		cmd = exec.Command("termux-open-url", url)
	case runtime.GOOS == "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case runtime.GOOS == "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}

func joinNonEmpty(sep string, ss ...string) string {
	var out []string
	for _, s := range ss {
		if s != "" {
			out = append(out, s)
		}
	}
	return strings.Join(out, sep)
}
