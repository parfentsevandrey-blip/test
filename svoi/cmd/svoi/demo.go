package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/demo"
)

// cmdDemo runs a simulated four-device mesh so the interface can be tried
// without four real devices.
func cmdDemo(args []string) error {
	fs := flag.NewFlagSet("demo", flag.ExitOnError)
	port := fs.Int("port", 8777, "port of the laptop's web interface (the other devices use the next ports)")
	noBrowser := fs.Bool("no-browser", false, "do not open the browser")
	quiet := fs.Bool("quiet", false, "no simulated activity after the start")
	debug := fs.Bool("debug", false, "verbose logging")
	fs.Parse(args)

	fmt.Fprintln(os.Stderr, "Starting a simulated home: laptop, phone (behind a carrier NAT), home server and NAS…")
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	d, err := demo.Start(ctx, demo.Options{UIPort: *port, Logger: newLogger(*debug, os.Stderr), Quiet: *quiet})
	if err != nil {
		return err
	}
	defer d.Close()

	fmt.Fprintf(os.Stderr, "\nsvoi demo is running. Everything here is simulated in this process;\nfiles, mail and chat are real and go through the real protocol stack.\n\n")
	for _, name := range []string{"laptop", "phone", "nas", "home-server"} {
		marker := "  "
		if name == "laptop" {
			marker = "→ "
		}
		fmt.Fprintf(os.Stderr, "%s%-12s %s\n", marker, name, d.Devices[name].URL)
	}
	fmt.Fprintln(os.Stderr, "\nOpen the first address (the laptop). The others are the same interface seen from the other devices.")
	fmt.Fprintln(os.Stderr, "Press Ctrl+C to stop.")
	if !*noBrowser {
		go func() {
			time.Sleep(300 * time.Millisecond)
			openBrowser(d.Devices["laptop"].URL)
		}()
	}
	<-signalChan()
	fmt.Fprintln(os.Stderr, "shutting down…")
	return nil
}
