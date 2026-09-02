// Command ptlab runs the app's transport controller on a desktop so the tor
// side of the design can be exercised for real. Not shipped; not referenced by
// the Android build.
package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"veil.app/veiltun/internal/ptbridge"
)

type events struct{}

func (events) Stopped(name string, err error) { fmt.Println("stopped:", name, err) }
func (events) Error(name string, err error)   { fmt.Println("error:", name, err) }
func (events) Connected(name string)          { fmt.Println("connected:", name) }
func (events) Phase(name, phase, detail string) { fmt.Println("phase:", name, phase, detail) }

func main() {
	dir, err := os.MkdirTemp("", "ptlab")
	if err != nil {
		panic(err)
	}
	c := ptbridge.NewController(dir, true, false, "INFO", events{})
	c.SnowflakeIceServers = "stun:stun.l.google.com:19302,stun:stun.voipgate.com:3478"
	c.SnowflakeBrokerUrl = "https://1098762253.rsc.cdn77.org/"
	c.SnowflakeFrontDomains = "app.datapacket.com,www.datapacket.com"
	c.SnowflakeMaxPeers = 3

	for _, name := range os.Args[1:] {
		if err := c.Start(name, ""); err != nil {
			fmt.Println("start", name, "failed:", err)
			continue
		}
		fmt.Printf("PORT %s %d\n", name, c.Port(name))
	}
	os.Stdout.Sync()

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	<-sig
}
