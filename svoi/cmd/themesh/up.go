package main

import (
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
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
	if d := os.Getenv("THEMESH_DIR"); d != "" {
		return d
	}
	if d, err := os.UserConfigDir(); err == nil && d != "" {
		return filepath.Join(d, "themesh")
	}
	if os.Geteuid() == 0 {
		return "/var/lib/themesh"
	}
	return ".themesh"
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
	tunOn := fs.Bool("tun", false, "create the themesh0 network interface (Linux, needs root): reach devices by IP or <name>.mesh from any program")
	noSTUN := fs.Bool("no-stun", false, "do not use public STUN servers (peers still tell each other how they see us); saved in the settings")
	printLink := fs.Bool("print-link", false, "print the one-time sign-in link even when the output is not a terminal (it then stays in the log)")
	noPortMap := fs.Bool("no-portmap", false, "do not ask the home router (UPnP / NAT-PMP) to forward our UDP port; saved in the settings")
	exitOnStdin := fs.Bool("exit-when-stdin-closes", false, "quit when standard input is closed (for the desktop and phone apps that run themesh as a background process: if the app goes away, so does themesh)")
	fs.Parse(args)

	// Started a second time (a double click on the icon, say): the first copy owns the
	// data directory, so do not start another node on the same keys - bring the running
	// one to the screen. A service that finds its predecessor still alive must fail instead.
	if c, err := newClient(cf.dir); err == nil {
		_ = c
		if *noBrowser || !interactive() {
			return errors.New("themesh is already running with this data directory (" + cf.dir + ")")
		}
		fmt.Fprintln(os.Stderr, "The Mesh уже запущен на этом устройстве — открываю интерфейс в браузере.")
		fmt.Fprintln(os.Stderr, "The Mesh is already running on this device — opening the interface in your browser.")
		if printed, err := uiURL(cf.dir, false); err == nil {
			fmt.Fprintln(os.Stderr, "  "+printed)
		}
		if opened, err := uiURL(cf.dir, true); err == nil {
			openBrowser(opened)
		}
		return nil
	}

	a, err := app.Open(app.Options{
		Dir:        cf.dir,
		Logger:     newLogger(*debug, os.Stderr),
		DeviceName: *name,
		Owner:      *owner,
		Mesh:       mesh.Config{Loopback: *loopback, Platform: platformFromEnv()},
	})
	if err != nil {
		return err
	}
	defer a.Close()
	if (*noSTUN && a.Settings().STUNEnabled) || (*noPortMap && a.Settings().PortMap) {
		off := false
		patch := app.SettingsPatch{}
		if *noSTUN {
			patch.STUNEnabled = &off
		}
		if *noPortMap {
			patch.PortMap = &off
		}
		if _, err := a.UpdateSettings(patch); err != nil {
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
	if isTerminal(os.Stderr) {
		// A person is looking: plain Russian, the link is theirs to copy (it scrolls away).
		fmt.Fprintf(os.Stderr, "\nThe Mesh %s запущен. Интерфейс откроется в браузере сам.\n\n", version())
		if self.Configured {
			fmt.Fprintf(os.Stderr, "  Это устройство:    %s, сеть «%s»\n", self.Name, self.MeshName)
		} else {
			fmt.Fprintf(os.Stderr, "  Это устройство пока не в сети: создайте свою сеть или подключитесь по приглашению — в браузере.\n")
		}
		fmt.Fprintf(os.Stderr, "  Адрес интерфейса:  %s\n", srv.URL())
		fmt.Fprintf(os.Stderr, "                     (одноразовая ссылка на 10 минут; новую даёт команда `themesh open`)\n")
		fmt.Fprintf(os.Stderr, "  Ваши данные:       %s\n\n", cf.dir)
		fmt.Fprintf(os.Stderr, "Не закрывайте это окно: пока оно открыто, программа работает.\nЗакрыть окно или нажать Ctrl+C — значит выйти (данные сохранятся).\n\n")
	} else {
		fmt.Fprintf(os.Stderr, "\nthemesh %s\n", version())
		if self.Configured {
			fmt.Fprintf(os.Stderr, "  device:  %s  (%s)\n  mesh:    %s\n", self.Name, self.IP4, self.MeshName)
		} else {
			fmt.Fprintf(os.Stderr, "  this device is not part of a mesh yet — create one or join in the browser\n")
		}
		// The link is a key for ten minutes. On a terminal a person reads it and it scrolls
		// away; in a log (systemd's journal, docker logs) it would stay for others to find.
		if *printLink || os.Getenv("THEMESH_PRINT_LINK") != "" {
			fmt.Fprintf(os.Stderr, "  open:    %s\n           (a one-time link, valid 10 minutes; `themesh open` makes a new one)\n  data:    %s\n\n", srv.URL(), cf.dir)
		} else {
			fmt.Fprintf(os.Stderr, "  open:    run `themesh url` on this machine for a one-time sign-in link\n           (it is not printed here, a log would keep it; --print-link overrides)\n  data:    %s\n\n", cf.dir)
		}
	}

	errc := make(chan error, 1)
	go func() { errc <- srv.Serve(ln) }()
	stdinClosed := make(chan struct{})
	if *exitOnStdin {
		go func() {
			_, _ = io.Copy(io.Discard, os.Stdin) // returns when the parent closes its end (or dies)
			close(stdinClosed)
		}()
	}
	if !*noBrowser && interactive() {
		go func() {
			time.Sleep(300 * time.Millisecond)
			openBrowser(srv.LocalURL()) // for this user only: it passes through a command line
		}()
	}
	select {
	case <-stdinClosed:
		fmt.Fprintln(os.Stderr, "shutting down (the app that started themesh has gone)…")
		srv.Close()
		return nil
	case <-signalChan():
		if isTerminal(os.Stderr) {
			fmt.Fprintln(os.Stderr, "Выход…")
		} else {
			fmt.Fprintln(os.Stderr, "shutting down…")
		}
		srv.Close()
		return nil
	case err := <-errc:
		return err
	}
}

// isTerminal reports whether f is a terminal, that is, whether a person is looking at
// it (rather than a file, a pipe or a service manager's journal).
func isTerminal(f *os.File) bool {
	fi, err := f.Stat()
	if err != nil || fi.Mode()&os.ModeCharDevice == 0 {
		return false
	}
	if null, err := os.Stat(os.DevNull); err == nil && os.SameFile(fi, null) {
		return false
	}
	return true
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

// platformRE is what THEMESH_PLATFORM may hold: an operating system and, after a slash, a processor ("android/arm64").
var platformRE = regexp.MustCompile(`^[a-z][a-z0-9]{1,15}(/[a-z0-9_]{1,15})?$`)

// platformFromEnv is the "os/arch" this program reports about itself when the program that starts it knows better than the
// build does: the phone app runs a Linux build on Android and says so (THEMESH_PLATFORM=android/arm64), so that the other
// devices show a phone as an Android phone and not as a Linux machine. Anything that does not look like a platform is ignored.
func platformFromEnv() string {
	p := strings.ToLower(strings.TrimSpace(os.Getenv("THEMESH_PLATFORM")))
	if platformRE.MatchString(p) {
		return p
	}
	return ""
}
