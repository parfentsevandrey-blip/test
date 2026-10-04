// Command themesh is a private network of your own devices, with no cloud and no
// account: encrypted direct connections between your phone, laptop, home
// server and NAS, and one web interface for files, mail, chat and shared
// services. Run it without arguments to start (and open the interface).
package main

import (
	"flag"
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"
)

func init() {
	// quic-go warns when it cannot tune the UDP buffers of a custom socket; the
	// real socket is sized by themesh itself.
	if os.Getenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING") == "" {
		os.Setenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING", "true")
	}
}

const usage = `The Mesh (themesh) — a private mesh network of your own devices (no cloud, no account)

Usage:
  themesh [up] [flags]            start this device and open the web interface (default)
  themesh init [flags]            create a new mesh with this device as the first member
  themesh join <invite>           join an existing mesh with an invitation code
  themesh invite [--admin]        print an invitation code (and QR) for another device
  themesh nearby                  devices around that can add this one, or ask to be added (join, allow, deny)
  themesh status                  show this device and the other devices
  themesh send <device> <file>... send files to a device
  themesh ping <device>           measure the round trip to a device
  themesh open                    open the web interface in the browser (signs it in)
  themesh url                     print a one-time sign-in link to the web interface
  themesh signout                 sign every browser out of the web interface
  themesh leave [--yes]           leave the mesh (keeps the device key)
  themesh demo                    run a simulated four-device mesh to try the interface
  themesh version

Common flags:
  --dir PATH   data directory (default: ~/.config/themesh, or $THEMESH_DIR)

Run "themesh <command> -h" for the flags of a command.
`

func main() {
	args := os.Args[1:]
	cmd := "up"
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		cmd, args = args[0], args[1:]
	}
	var err error
	switch cmd {
	case "up", "start", "run":
		err = cmdUp(args)
	case "init":
		err = cmdInit(args)
	case "join":
		err = cmdJoin(args)
	case "invite":
		err = cmdInvite(args)
	case "nearby":
		err = cmdNearby(args)
	case "status", "peers":
		err = cmdStatus(args)
	case "send":
		err = cmdSend(args)
	case "ping":
		err = cmdPing(args)
	case "open":
		err = cmdOpen(args)
	case "url":
		err = cmdURL(args)
	case "signout":
		err = cmdSignout(args)
	case "leave":
		err = cmdLeave(args)
	case "demo":
		err = cmdDemo(args)
	case "version", "-v", "--version":
		fmt.Printf("themesh %s (%s/%s, %s)\n", version(), runtime.GOOS, runtime.GOARCH, runtime.Version())
	case "help", "-h", "--help":
		fmt.Print(usage)
	default:
		fmt.Fprintf(os.Stderr, "themesh: unknown command %q\n\n%s", cmd, usage)
		os.Exit(2)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "themesh:", err)
		os.Exit(1)
	}
}

// signalContext is cancelled on SIGINT/SIGTERM.
func signalChan() <-chan os.Signal {
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM)
	return ch
}

// parseInterspersed parses flags that may appear before, between or after
// positional arguments (Go's flag package stops at the first positional one),
// and returns the positional arguments.
func parseInterspersed(fs *flag.FlagSet, args []string) []string {
	var pos []string
	for {
		fs.Parse(args)
		args = fs.Args()
		if len(args) == 0 {
			return pos
		}
		pos = append(pos, args[0])
		args = args[1:]
	}
}
