// torss — a small tray VPN client for Windows 11: Tor over Shadowsocks with
// automatic fallback to Tor bridges, built for networks with aggressive DPI.
package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"sync"

	"fyne.io/systray"

	"torss/internal/bundle"
	"torss/internal/config"
	"torss/internal/engine"
	"torss/internal/tor"
	"torss/internal/ui"
	"torss/internal/winutil"
)

const appName = "TorSS"

func exeName(base string) string {
	if runtime.GOOS == "windows" {
		return base + ".exe"
	}
	return base
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

func dataDir() string {
	if runtime.GOOS == "windows" {
		if la := os.Getenv("LOCALAPPDATA"); la != "" {
			return filepath.Join(la, appName)
		}
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "."+strings.ToLower(appName))
}

// setupLog writes to data/torss.log (rotating once at 5 MB) and optionally stdout.
func setupLog(dir string, console bool) (func(string, ...any), func()) {
	_ = os.MkdirAll(dir, 0o700)
	path := filepath.Join(dir, "torss.log")
	if st, err := os.Stat(path); err == nil && st.Size() > 5*1024*1024 {
		_ = os.Rename(path, path+".1")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	var w io.Writer = os.Stdout
	if err == nil {
		w = f
		if console {
			w = io.MultiWriter(f, os.Stdout)
		}
	}
	l := log.New(w, "", log.LstdFlags)
	var mu sync.Mutex
	logf := func(format string, a ...any) {
		mu.Lock()
		defer mu.Unlock()
		l.Printf(format, a...)
	}
	closeFn := func() {
		if f != nil {
			f.Close()
		}
	}
	return logf, closeFn
}

// locateBin picks the helper-binary directory: bin/ next to the exe, else the
// bundle embedded into the exe (extracted to <data>\bin on first run / update).
func locateBin(exeDir, data string, logf func(string, ...any)) (string, error) {
	external := filepath.Join(exeDir, "bin")
	if exists(filepath.Join(external, exeName("sing-box"))) && exists(filepath.Join(external, "tor", exeName("tor"))) {
		return external, nil
	}
	if m, err := bundle.ReadManifest(bundled, "bundle"); err != nil {
		return "", err
	} else if m != nil {
		dest := filepath.Join(data, "bin")
		n, err := bundle.Extract(bundled, "bundle", dest, logf)
		if err != nil {
			return "", fmt.Errorf("не удалось распаковать встроенные компоненты в %s:\n%v", dest, err)
		}
		if n > 0 {
			logf("bundle: %d file(s) extracted to %s (version %s)", n, dest, m.Version)
		}
		return dest, nil
	}
	if exists(filepath.Join(exeDir, "..", "..", "bin")) { // go run ./cmd/torss from the source tree
		return filepath.Join(exeDir, "..", "..", "bin"), nil
	}
	return external, nil
}

func findPaths(bin string) (engine.Paths, error) {
	torDir := filepath.Join(bin, "tor")
	p := engine.Paths{
		BinDir:  bin,
		SingBox: filepath.Join(bin, exeName("sing-box")),
		Tor: tor.Layout{
			Dir:      torDir,
			Exe:      exeName("tor"),
			Lyrebird: filepath.Join("pluggable_transports", exeName("lyrebird")),
			Conjure:  filepath.Join("pluggable_transports", exeName("conjure-client")),
		},
	}
	p.Tor.HasConjure = exists(filepath.Join(torDir, p.Tor.Conjure))
	if exists(filepath.Join(torDir, "data", "geoip")) {
		p.Tor.GeoIP = filepath.Join("data", "geoip")
	}
	if exists(filepath.Join(torDir, "data", "geoip6")) {
		p.Tor.GeoIP6 = filepath.Join("data", "geoip6")
	}
	var missing []string
	for _, f := range []string{
		p.SingBox,
		filepath.Join(torDir, p.Tor.Exe),
		filepath.Join(torDir, p.Tor.Lyrebird),
	} {
		if !exists(f) {
			missing = append(missing, f)
		}
	}
	if runtime.GOOS == "windows" && !exists(filepath.Join(bin, "wintun.dll")) {
		missing = append(missing, filepath.Join(bin, "wintun.dll"))
	}
	if len(missing) > 0 {
		return p, fmt.Errorf("не найдены файлы:\n  %s\n\nЗапустите scripts\\fetch-deps.ps1 или распакуйте готовую сборку целиком.",
			strings.Join(missing, "\n  "))
	}
	if self, err := os.Executable(); err == nil {
		p.SelfExe = self
	}
	return p, nil
}

func main() {
	var (
		cfgFlag   = flag.String("config", "", "путь к config.json (по умолчанию %LOCALAPPDATA%\\TorSS\\config.json)")
		console   = flag.Bool("console", false, "дублировать лог в консоль")
		noElevate = flag.Bool("no-elevate", false, "не запрашивать права администратора")
		cleanup   = flag.Bool("cleanup", false, "только снять kill switch / системный прокси и выйти")
		noConnect = flag.Bool("no-autoconnect", false, "не подключаться автоматически при запуске")
	)
	flag.Parse()

	dir := dataDir()
	logf, closeLog := setupLog(dir, *console)
	defer closeLog()

	cfgPath := *cfgFlag
	if cfgPath == "" {
		cfgPath = filepath.Join(dir, "config.json")
	}
	cfg, err := config.Load(cfgPath)
	if err != nil {
		logf("config: %v", err)
		winutil.MessageBox(appName, "Ошибка конфигурации:\n"+err.Error(), true)
		os.Exit(2)
	}
	statePath := filepath.Join(dir, "state.json")
	state := config.LoadState(statePath)

	exe, _ := os.Executable()
	bin, err := locateBin(filepath.Dir(exe), dir, logf)
	if err != nil {
		logf("%v", err)
		winutil.MessageBox(appName, err.Error(), true)
		os.Exit(2)
	}
	paths, err := findPaths(bin)
	if err != nil {
		logf("%v", err)
		winutil.MessageBox(appName, err.Error(), true)
		os.Exit(2)
	}

	needAdmin := cfg.TUN || cfg.KillSwitch || state.KillSwitchActive
	if needAdmin && !winutil.IsElevated() && !*noElevate && runtime.GOOS == "windows" {
		logf("relaunching with administrator rights (tun=%v kill_switch=%v)", cfg.TUN, cfg.KillSwitch)
		if err := winutil.RelaunchElevated(); err != nil {
			winutil.MessageBox(appName,
				"Для режима VPN (TUN) и kill switch нужны права администратора.\n"+err.Error()+
					"\n\nЛибо выставьте \"tun\": false в config.json для работы в режиме прокси.", true)
			os.Exit(3)
		}
		return
	}

	if err := winutil.SingleInstance(appName); err != nil {
		if err == winutil.ErrAlreadyRunning {
			winutil.MessageBox(appName, "TorSS уже запущен (смотрите значок в трее).", false)
			os.Exit(0)
		}
		logf("single instance: %v", err)
	}

	logf("==== %s starting: exe=%s data=%s config=%s tun=%v mode=%s", appName, exe, dir, cfgPath, cfg.TUN, cfg.Mode)

	var tray *ui.Tray
	eng := engine.New(cfg, cfgPath, state, statePath, paths, logf, func(s engine.Status) {
		if tray != nil {
			tray.Update(s)
		}
	})
	eng.CleanupAfterCrash()
	if *cleanup {
		logf("cleanup done")
		return
	}

	tray = ui.New(eng, dir, cfgPath, logf, func() { logf("==== exit") })

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt)
	go func() {
		<-sig
		logf("interrupt: disconnecting")
		eng.Disconnect()
		systray.Quit()
	}()

	if cfg.AutoConnect && !*noConnect {
		eng.Connect()
	}
	tray.Run()
}
