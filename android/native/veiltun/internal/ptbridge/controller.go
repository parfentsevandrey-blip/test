package ptbridge

// Package ptbridge starts and stops the Tor Project's pluggable transport
// clients, each listening on a local SOCKS port that tor is then pointed at.
//
// Vendored from IPtProxy (https://github.com/tladesignz/IPtProxy), MIT
// licensed, copyright 2020-2026 Guardian Project; the LICENSE file in this
// directory is theirs. Two changes were made:
//
//   - The package was renamed so it can live inside this module, which is
//     necessary because upstream's go.mod carries a local `replace` for dnstt
//     that cannot be resolved by a consumer.
//   - The dnstt transport was removed, since it is exactly what that replace
//     directive was for. obfs4, meek_lite, webtunnel and Snowflake, which are
//     the transports this app offers, are untouched.

import (
	"errors"
	"io"
	"io/fs"
	"log"
	"net"
	"net/url"
	"os"
	"path"

	"fmt"
	"strconv"

	pt "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/goptlib"
	ptlog "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/common/log"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/transports"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/transports/base"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/event"
	sfversion "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/version"
	"golang.org/x/net/proxy"
)

// LogFileName - the filename of the log residing in `StateDir`.
const LogFileName = "ipt.log"

//goland:noinspection GoUnusedConst
const (
	// ScrambleSuit - DEPRECATED transport implemented in Lyrebird.
	ScrambleSuit = "scramblesuit"

	// Obfs2 - DEPRECATED transport implemented in Lyrebird.
	Obfs2 = "obfs2"

	// Obfs3 - DEPRECATED transport implemented in Lyrebird.
	Obfs3 = "obfs3"

	// Obfs4 - Transport implemented in Lyrebird.
	Obfs4 = "obfs4"

	// MeekLite - Transport implemented in Lyrebird.
	MeekLite = "meek_lite"

	// Webtunnel - Transport implemented in Lyrebird.
	Webtunnel = "webtunnel"

	// Snowflake - Transport implemented in Snowflake.
	Snowflake = "snowflake"
)

// OnTransportEvents - Interface to get notified when the transport stopped again, when errors happened, or when
// the transport actually got a full connection.
//
//goland:noinspection GoUnusedExportedType.
type OnTransportEvents interface {

	// Stopped - Called when the transport stopped again, with or without an error.
	//
	// @param name The transport name that stopped.
	// @param error The error that caused the transport to stop, or nil if the transport stopped without error.
	Stopped(name string, error error)

	// Error - Currently only called when an error happened during Snowflake proxy discovery: Either the WebRTC offer
	// couldn't be created, the broker could not match us with a proxy, or the connection to the given proxy could not
	// be made. This will continue until either Connected is called because of a successful connection to a proxy, or
	// Controller.Stop is used to stop the transport again.
	// When further connections are attempted by the client, the same cycle will repeat.
	//
	// @param name The transport name that errored.
	// @param error The error that occurred.
	Error(name string, error error)

	// Connected - This will always fire immediately before returning from Controller.Start, except with Snowflake,
	// where it fires later, namely every time a successful connection to a proxy was achieved.
	//
	// @param name The transport name that connected.
	Connected(name string)
}

// Controller - Class to start and stop transports.
type Controller struct {

	// SnowflakeIceServers is a comma-separated list of ICE server addresses.
	SnowflakeIceServers string

	// SnowflakeBrokerUrl - URL of signaling broker.
	SnowflakeBrokerUrl string

	// SnowflakeFrontDomains is a comma-separated list of domains for either
	// the domain fronting or AMP cache rendezvous methods.
	SnowflakeFrontDomains string

	// SnowflakeAmpCacheUrl - URL of AMP cache to use as a proxy for signaling.
	// Only needed when you want to do the rendezvous over AMP instead of a domain fronted server.
	SnowflakeAmpCacheUrl string

	// SnowflakeSqsUrl - URL of SQS Queue to use as a proxy for signaling.
	SnowflakeSqsUrl string

	// SnowflakeSqsCreds - Credentials to access SQS Queue.
	SnowflakeSqsCreds string

	// SnowflakeMaxPeers - Capacity for number of multiplexed WebRTC peers. DEFAULTs to 1 if less than that.
	SnowflakeMaxPeers int

	stateDir        string
	transportEvents OnTransportEvents
	listeners       map[string]*pt.SocksListener
	shutdown        map[string]chan struct{}
}

// NewController - Create a new Controller object.
//
// @param enableLogging Log to StateDir/ipt.log.
//
// @param unsafeLogging Disable the address scrubber.
//
// @param logLevel Log level (ERROR/WARN/INFO/DEBUG). Defaults to ERROR if empty string.
//
// @param transportEvents A delegate, which is called when the transport stopped again, when errors happened, or when
// the transport actually got a full connection.
// Will be called on its own thread! You will need to switch to your own UI thread
// if you want to do UI stuff!
//
//goland:noinspection GoUnusedExportedFunction
func NewController(stateDir string, enableLogging, unsafeLogging bool, logLevel string, transportEvents OnTransportEvents) *Controller {
	c := &Controller{
		stateDir:        stateDir,
		transportEvents: transportEvents,
	}

	if logLevel == "" {
		logLevel = "ERROR"
	}

	if err := createStateDir(c.stateDir); err != nil {
		log.Printf("Failed to set up state directory: %s", err)
		return nil
	}
	if err := ptlog.Init(enableLogging,
		path.Join(c.stateDir, LogFileName), unsafeLogging); err != nil {
		log.Printf("Failed to set initialize log: %s", err.Error())
		return nil
	}
	if err := ptlog.SetLogLevel(logLevel); err != nil {
		log.Printf("Failed to set log level: %s", err.Error())
		ptlog.Warnf("Failed to set log level: %s", err.Error())
	}

	if err := transports.Init(); err != nil {
		ptlog.Warnf("Failed to initialize transports: %s", err.Error())
		return nil
	}

	c.listeners = make(map[string]*pt.SocksListener)
	c.shutdown = make(map[string]chan struct{})

	return c
}

// StateDir - The StateDir set in the constructor.
//
// @returns the directory you set in the constructor, where transports store their state and where the log file resides.
func (c *Controller) StateDir() string {
	return c.stateDir
}

// addExtraArgs adds the args in extraArgs to the connection args
func addExtraArgs(conn *pt.SocksConn, extraArgs *pt.Args) {
	if extraArgs == nil {
		return
	}

	if conn.Req.Args == nil {
		conn.Req.Args = make(pt.Args)
	}
	for name := range *extraArgs {
		// Only add if extra arg doesn't already exist, and is not empty.
		if value, ok := conn.Req.Args.Get(name); !ok || value == "" {
			if value, ok := extraArgs.Get(name); ok && value != "" {
				conn.Req.Args.Add(name, value)
			}
		}
	}
}

func acceptLoop(f base.ClientFactory, ln *pt.SocksListener, proxyURL *url.URL,
	extraArgs *pt.Args, shutdown chan struct{}, methodName string, transportEvents OnTransportEvents) {
	defer ln.Close()
	for {
		conn, err := ln.AcceptSocks()
		if err != nil {
			var e net.Error
			if errors.As(err, &e) && !e.Temporary() {
				return
			}

			continue
		}

		go clientHandler(f, conn, proxyURL, extraArgs, shutdown, methodName, transportEvents)
	}
}

func clientHandler(f base.ClientFactory, conn *pt.SocksConn, proxyURL *url.URL,
	extraArgs *pt.Args, shutdown chan struct{}, methodName string, transportEvents OnTransportEvents) {

	defer conn.Close()

	addExtraArgs(conn, extraArgs)
	args, err := f.ParseArgs(&conn.Req.Args)
	if err != nil {
		ptlog.Errorf("Error parsing PT args: %s", err.Error())
		_ = conn.Reject()

		if transportEvents != nil {
			go transportEvents.Stopped(methodName, err)
		}

		return
	}

	dialFn := proxy.Direct.Dial
	if proxyURL != nil {
		dialer, err := proxy.FromURL(proxyURL, proxy.Direct)
		if err != nil {
			ptlog.Errorf("Error getting proxy dialer: %s", err.Error())
			_ = conn.Reject()

			if transportEvents != nil {
				go transportEvents.Stopped(methodName, err)
			}

			return
		}
		dialFn = dialer.Dial
	}

	remote, err := f.Dial("tcp", conn.Req.Target, dialFn, args)
	if err != nil {
		ptlog.Errorf("Error dialing PT: %s", err.Error())

		if transportEvents != nil {
			go transportEvents.Stopped(methodName, err)
		}

		return
	}

	err = conn.Grant(&net.TCPAddr{IP: net.IPv4zero, Port: 0})
	if err != nil {
		ptlog.Errorf("conn.Grant error: %s", err)

		if transportEvents != nil {
			go transportEvents.Stopped(methodName, err)
		}

		return
	}

	defer remote.Close()

	done := make(chan struct{}, 2)
	go copyLoop(conn, remote, done)

	// Wait for the copy loop to finish or for a shutdown signal.
	select {
	case <-shutdown:
	case <-done:
		ptlog.Noticef("copy loop ended")
	}

	if transportEvents != nil {
		ptlog.Noticef("call OnTransportEvents.Stopped")
		go transportEvents.Stopped(methodName, nil)
	}
}

// Exchanges bytes between two ReadWriters.
// (In this case, between a SOCKS connection and a pt conn)
func copyLoop(socks, sfconn io.ReadWriter, done chan struct{}) {
	go func() {
		if _, err := io.Copy(socks, sfconn); err != nil {
			ptlog.Errorf("copying transport to SOCKS resulted in error: %v", err)
		}
		done <- struct{}{}
	}()
	go func() {
		if _, err := io.Copy(sfconn, socks); err != nil {
			ptlog.Errorf("copying SOCKS to transport resulted in error: %v", err)
		}
		done <- struct{}{}
	}()
}

// LocalAddress - Address of the given transport.
//
// @param methodName one of the constants `ScrambleSuit` (deprecated), `Obfs2` (deprecated), `Obfs3` (deprecated),
// `Obfs4`, `MeekLite`, `Webtunnel` or `Snowflake`.
//
// @return address string containing host and port where the given transport listens.
func (c *Controller) LocalAddress(methodName string) string {
	if ln, ok := c.listeners[methodName]; ok {
		return ln.Addr().String()
	}
	return ""
}

// Port - Port of the given transport.
//
// @param methodName one of the constants `ScrambleSuit` (deprecated), `Obfs2` (deprecated), `Obfs3` (deprecated),
// `Obfs4`, `MeekLite`, `Webtunnel` or `Snowflake`.
//
// @return port number on localhost where the given transport listens.
func (c *Controller) Port(methodName string) int {
	if ln, ok := c.listeners[methodName]; ok {
		return int(ln.Addr().(*net.TCPAddr).AddrPort().Port())
	}
	return 0
}

func createStateDir(path string) error {
	info, err := os.Stat(path)

	// If dir does not exist, try to create it.
	if errors.Is(err, os.ErrNotExist) {
		err = os.MkdirAll(path, 0700)

		if err == nil {
			info, err = os.Stat(path)
		}
	}

	// If it is not a dir, return error
	if err == nil && !info.IsDir() {
		err = fs.ErrInvalid
		return err
	}

	// Create a file within dir to test writability.
	tempFile := path + "/.iptproxy-writetest"
	var file *os.File
	file, err = os.Create(tempFile)

	// Remove the test file again.
	if err == nil {
		_ = file.Close()

		err = os.Remove(tempFile)
	}
	return err
}

// Start - Start given transport.
//
// @param methodName one of the constants `ScrambleSuit` (deprecated), `Obfs2` (deprecated), `Obfs3` (deprecated),
// `Obfs4`, `MeekLite`, `Webtunnel` or `Snowflake`.
//
// @param proxy HTTP, SOCKS4 or SOCKS5 proxy to be used behind Lyrebird. E.g. "socks5://127.0.0.1:12345"
//
// @throws if the proxy URL cannot be parsed, if the given `methodName` cannot be found, if the transport cannot
// be initialized, or if it couldn't bind a port for listening.
func (c *Controller) Start(methodName string, proxy string) error {
	var proxyURL *url.URL
	var err error

	if proxy != "" {
		proxyURL, err = url.Parse(proxy)
		if err != nil {
			ptlog.Errorf("Failed to parse proxy address: %s", err.Error())
			return err
		}
	}

	switch methodName {
	case Snowflake:
		if proxyURL != nil {
			ptlog.Errorf("Snowflake does not support proxies")
			return fmt.Errorf("Snowflake does not support proxies")
		}

		extraArgs := &pt.Args{}
		extraArgs.Add("fronts", c.SnowflakeFrontDomains)
		extraArgs.Add("ice", c.SnowflakeIceServers)
		extraArgs.Add("max", strconv.Itoa(max(1, c.SnowflakeMaxPeers)))
		extraArgs.Add("url", c.SnowflakeBrokerUrl)
		extraArgs.Add("ampcache", c.SnowflakeAmpCacheUrl)
		extraArgs.Add("sqsqueue", c.SnowflakeSqsUrl)
		extraArgs.Add("sqscreds", c.SnowflakeSqsCreds)

		t := transports.Get(methodName)
		if t == nil {
			ptlog.Errorf("Failed to initialize %s: no such method", methodName)
			return fmt.Errorf("failed to initialize %s: no such method", methodName)
		}
		f, err := t.ClientFactory(c.stateDir)
		if err != nil {
			ptlog.Errorf("Failed to initialize %s: %s", methodName, err.Error())
			return err
		}
		ln, err := pt.ListenSocks("tcp", "127.0.0.1:0")
		if err != nil {
			ptlog.Errorf("Failed to initialize %s: %s", methodName, err.Error())
			return err
		}

		f.OnEvent(func(e base.TransportEvent) {
			switch ev := e.(type) {
			case event.EventOnOfferCreated:
				if ev.Error != nil && c.transportEvents != nil {
					go c.transportEvents.Error(methodName, ev.Error)
				}

			case event.EventOnBrokerRendezvous:
				if ev.Error != nil && c.transportEvents != nil {
					go c.transportEvents.Error(methodName, ev.Error)
				}

			case event.EventOnSnowflakeConnected:
				if c.transportEvents != nil {
					go c.transportEvents.Connected(methodName)
				}

			case event.EventOnSnowflakeConnectionFailed:
				if ev.Error != nil && c.transportEvents != nil {
					go c.transportEvents.Error(methodName, ev.Error)
				}

			default:
			}
		})

		c.shutdown[methodName] = make(chan struct{})
		c.listeners[methodName] = ln

		go acceptLoop(f, ln, nil, extraArgs, c.shutdown[methodName], methodName, c.transportEvents)

	default:
		// at the moment, everything else is in lyrebird
		t := transports.Get(methodName)
		if t == nil {
			ptlog.Errorf("Failed to initialize %s: no such method", methodName)
			return fmt.Errorf("failed to initialize %s: no such method", methodName)
		}

		f, err := t.ClientFactory(c.stateDir)
		if err != nil {
			ptlog.Errorf("Failed to initialize %s: %s", methodName, err.Error())
			return err
		}

		ln, err := pt.ListenSocks("tcp", "127.0.0.1:0")
		if err != nil {
			ptlog.Errorf("Failed to initialize %s: %s", methodName, err.Error())
			return err
		}

		c.listeners[methodName] = ln
		c.shutdown[methodName] = make(chan struct{})

		go acceptLoop(f, ln, proxyURL, nil, c.shutdown[methodName], methodName, c.transportEvents)

		if c.transportEvents != nil {
			go c.transportEvents.Connected(methodName)
		}
	}

	ptlog.Noticef("Launched transport: %v", methodName)

	return nil
}

// Stop - Stop given transport.
//
// @param methodName one of the constants `ScrambleSuit` (deprecated), `Obfs2` (deprecated), `Obfs3` (deprecated),
// `Obfs4`, `MeekLite`, `Webtunnel` or `Snowflake`.
func (c *Controller) Stop(methodName string) {
	if ln, ok := c.listeners[methodName]; ok {
		_ = ln.Close()

		ptlog.Noticef("Shutting down %s", methodName)

		close(c.shutdown[methodName])
		delete(c.shutdown, methodName)
		delete(c.listeners, methodName)
	} else {
		ptlog.Warnf("No listener for %s", methodName)
	}
}

// SnowflakeVersion - The version of Snowflake bundled with IPtProxy.
//
//goland:noinspection GoUnusedExportedFunction
func SnowflakeVersion() string {
	return sfversion.GetVersion()
}

// LyrebirdVersion - The version of Lyrebird bundled with IPtProxy.
//
//goland:noinspection GoUnusedExportedFunction
func LyrebirdVersion() string {
	return "lyrebird-0.8.1"
}
