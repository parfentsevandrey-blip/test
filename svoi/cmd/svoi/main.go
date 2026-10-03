// Command svoi is a private network of your own devices, with no cloud and no
// account: encrypted direct connections between your phone, laptop, home
// server and NAS, and one web interface for files, mail, chat and shared
// services. Run it without arguments to start (and open the interface).
package main

import (
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"
)

func init() {
	// quic-go warns when it cannot tune the UDP buffers of a custom socket; the
	// real socket is sized by svoi itself.
	if os.Getenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING") == "" {
		os.Setenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING", "true")
	}
}

const usage = `svoi — a private mesh network of your own devices (no cloud, no account)

Usage:
  svoi [up] [flags]        start this device and open the web interface (default)
  svoi init [flags]        create a new mesh with this device as the first member
  svoi join <invite>       join an existing mesh with an invitation code
  svoi invite [--admin]    print an invitation code (and QR) for another device
  svoi status              show this device and the other devices
  svoi send <device> <file>...   send files to a device
  svoi ping <device>       measure the round trip to a device
  svoi open                open the web interface in the browser
  svoi url                 print the web interface address
  svoi leave               leave the mesh (keeps the device key)
  svoi demo                run a simulated four-device mesh to try the interface
  svoi version

Common flags:
  --dir PATH   data directory (default: ~/.config/svoi, or $SVOI_DIR)

Run "svoi <command> -h" for the flags of a command.
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
	case "leave":
		err = cmdLeave(args)
	case "demo":
		err = cmdDemo(args)
	case "version", "-v", "--version":
		fmt.Printf("svoi %s (%s/%s, %s)\n", version(), runtime.GOOS, runtime.GOARCH, runtime.Version())
	case "help", "-h", "--help":
		fmt.Print(usage)
	default:
		fmt.Fprintf(os.Stderr, "svoi: unknown command %q\n\n%s", cmd, usage)
		os.Exit(2)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "svoi:", err)
		os.Exit(1)
	}
}

// signalContext is cancelled on SIGINT/SIGTERM.
func signalChan() <-chan os.Signal {
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM)
	return ch
}
