// Command torveild runs the TorVeil engine without a user interface.
//
// It exists for three reasons: to run TorVeil on a headless machine, to give
// the graphical build something that can be scripted and tested, and to make
// failures legible — every step prints what it is doing, so a connection that
// stalls says where.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/parfentsevandrey-blip/torveil/internal/bundle"
	"github.com/parfentsevandrey-blip/torveil/internal/config"
	"github.com/parfentsevandrey-blip/torveil/internal/core"
	"github.com/parfentsevandrey-blip/torveil/internal/logging"
	"github.com/parfentsevandrey-blip/torveil/internal/shaper"
	"github.com/parfentsevandrey-blip/torveil/internal/tor"
	"github.com/parfentsevandrey-blip/torveil/internal/tunnel"
)

func main() {
	var (
		mode      = flag.String("mode", "", "traffic capture: proxy or tunnel (default: from config)")
		transport = flag.String("transport", "", "direct, snowflake or obfs4 (default: from config)")
		hops      = flag.Int("hops", 0, "circuit length, 2-5 (default: from config)")
		exitCC    = flag.String("exit", "", "exit country, e.g. de (default: any)")
		entryCC   = flag.String("entry", "", "entry country; ignored when a bridge is in use")
		profileID = flag.String("shaping", "", "traffic shaping profile: off, light, balanced, paranoid")
		socks     = flag.String("socks", "", "SOCKS5 listen address")
		httpAddr  = flag.String("http", "", "HTTP proxy listen address")
		torDir    = flag.String("tor-dir", "", "directory containing tor and the pluggable transports")
		quiet     = flag.Bool("quiet", false, "only print warnings and errors")
		statusInt = flag.Duration("status-interval", 30*time.Second, "how often to print a status line; 0 disables")
		locate    = flag.Bool("locate", false, "report which Tor binaries were found, then exit")
		listProf  = flag.Bool("profiles", false, "list the traffic shaping profiles, then exit")
	)
	flag.Parse()

	if *listProf {
		printProfiles()
		return
	}

	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "warning: %v (continuing with defaults)\n", err)
	}
	cfg = applyFlags(cfg, flagValues{
		mode: *mode, transport: *transport, hops: *hops,
		exitCC: *exitCC, entryCC: *entryCC, profile: *profileID,
		socks: *socks, http: *httpAddr, torDir: *torDir,
	})

	if *locate {
		printLocated(cfg)
		return
	}

	logs := logging.New(4000)
	logs.Subscribe(func(e logging.Entry) {
		if *quiet && e.Level == "info" {
			return
		}
		fmt.Printf("%s  %-5s %s\n", e.Time.Format("15:04:05"), e.Level, e.Message)
	})

	tunnel.RecoverStaleState(cfg.DataDir, logs.Func())

	engine := core.New(cfg, logs)

	// Shut down on a signal rather than letting the process die: the tunnel
	// owns routing-table entries and firewall rules that have to be handed
	// back, and a killed process leaves the machine misconfigured.
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Minute)
	defer cancel()

	go func() {
		<-sig
		fmt.Println("\nshutting down…")
		cancel()
		_ = engine.Disconnect()
		os.Exit(0)
	}()

	if err := engine.Connect(ctx); err != nil {
		fmt.Fprintf(os.Stderr, "\nfailed to connect: %v\n", err)
		_ = engine.Disconnect()
		os.Exit(1)
	}

	s := engine.Status()
	fmt.Printf("\nconnected — %s transport, %d hops, %s shaping\n", s.Transport, s.Hops, s.Profile.Name)
	if s.Proxy.SOCKSAddr != "" {
		fmt.Printf("  SOCKS5 proxy : %s\n", s.Proxy.SOCKSAddr)
		fmt.Printf("  HTTP proxy   : %s\n", s.Proxy.HTTPAddr)
	}
	if s.Tunnel.Running {
		fmt.Printf("  tunnel       : %s on %s\n", s.Tunnel.Adapter, s.Tunnel.Address)
	}
	fmt.Println("\npress Ctrl-C to disconnect")

	if *statusInt > 0 {
		go printStatusLoop(engine, *statusInt)
	}
	select {}
}

type flagValues struct {
	mode, transport, exitCC, entryCC, profile, socks, http, torDir string
	hops                                                           int
}

func applyFlags(cfg config.Config, f flagValues) config.Config {
	if f.mode != "" {
		cfg.Mode = config.Mode(f.mode)
	}
	if f.transport != "" {
		cfg.Transport = f.transport
	}
	if f.hops != 0 {
		cfg.Hops = f.hops
	}
	if f.exitCC != "" {
		cfg.ExitCountry = f.exitCC
	}
	if f.entryCC != "" {
		cfg.EntryCountry = f.entryCC
	}
	if f.profile != "" {
		cfg.ShapingProfile = f.profile
	}
	if f.socks != "" {
		cfg.SOCKSListen = f.socks
	}
	if f.http != "" {
		cfg.HTTPListen = f.http
	}
	if f.torDir != "" {
		cfg.TorSearchDirs = append([]string{f.torDir}, cfg.TorSearchDirs...)
	}
	return cfg.Normalized()
}

func printProfiles() {
	for _, p := range shaper.Profiles() {
		fmt.Printf("%-10s %s\n", p.ID, p.Name)
		fmt.Printf("           %s\n", p.Description)
		fmt.Printf("           send grid: %-6s  cover traffic: %-8s  cost: %s\n\n",
			p.TickString(), p.ChaffMode, p.EstimatedOverhead)
	}
}

func printLocated(cfg config.Config) {
	// Unpack the bundled runtime first, so this reports what a real
	// connection would use rather than only what is installed on the machine.
	dirs := cfg.TorSearchDirs
	if bundle.Available() {
		rt, err := bundle.Ensure(cfg.DataDir, func(level, msg string) {
			fmt.Printf("%-18s %s\n", level+":", msg)
		})
		if err != nil {
			fmt.Fprintf(os.Stderr, "bundled runtime: %v\n", err)
		} else {
			dirs = append([]string{rt.SearchDir()}, dirs...)
			fmt.Printf("%-18s %s (version %s)\n", "bundled runtime:", rt.Dir, rt.Version)
		}
	} else {
		fmt.Printf("%-18s %s\n", "bundled runtime:", "not in this build (built without -tags bundled)")
	}

	bins, err := tor.Locate(dirs)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
	for _, row := range [][2]string{
		{"tor", bins.Tor},
		{"snowflake", bins.Snowflake},
		{"obfs4 (lyrebird)", bins.Obfs4},
		{"pt_config.json", bins.PTConfig},
		{"geoip", bins.GeoIP},
		{"geoip6", bins.GeoIPv6},
	} {
		value := row[1]
		if value == "" {
			value = "not found"
		}
		fmt.Printf("%-18s %s\n", row[0]+":", value)
	}

	if lines := tor.RecommendedBridges(bins.PTConfig, cfg.Transport); len(lines) > 0 {
		fmt.Printf("%-18s %d line(s) for %s\n", "bridges:", len(lines), cfg.Transport)
	}
}

func printStatusLoop(engine *core.Engine, every time.Duration) {
	ticker := time.NewTicker(every)
	defer ticker.Stop()
	for range ticker.C {
		s := engine.Status()
		if s.State != core.StateConnected {
			continue
		}
		var exits []string
		for _, c := range s.Circuits {
			if len(c.Hops) == 0 || c.Status != "BUILT" {
				continue
			}
			last := c.Hops[len(c.Hops)-1]
			exits = append(exits, last.Nickname+"/"+last.Country)
		}
		fmt.Printf("%s  stat  %d circuits [%s] | real %s up %s down | cover %s | overhead %.1f%%\n",
			time.Now().Format("15:04:05"),
			len(s.Circuits), strings.Join(exits, " "),
			humanBytes(s.Shaper.RealOut), humanBytes(s.Shaper.RealIn),
			humanBytes(s.Shaper.ChaffOut+s.Shaper.ChaffIn), s.Shaper.OverheadPct)
	}
}

func humanBytes(n int64) string {
	const unit = 1024
	if n < unit {
		return fmt.Sprintf("%dB", n)
	}
	div, exp := int64(unit), 0
	for v := n / unit; v >= unit && exp < 4; v /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f%ciB", float64(n)/float64(div), "KMGT"[exp])
}
