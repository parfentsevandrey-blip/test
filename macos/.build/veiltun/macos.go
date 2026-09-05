//go:build darwin

package veiltun

// The macOS entry points, which differ from Android's in exactly two ways:
// where the tunnel descriptor comes from (see packetflow.go) and where the
// proxy is (a unix socket in the App Group container rather than a loopback
// port).
//
// The second is not a port detail, it is better. A loopback TCP port is
// reachable by every process on the machine, which is why the Android build
// has a screen listing the ports it is responsible for having open. A socket
// inside the container is reachable by this app and nothing else, so on macOS
// the local-proxy hole is simply closed.

import (
	"errors"
	"fmt"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/option"
)

// StartTunnel brings the tunnel up on a descriptor taken from the packet
// tunnel's flow.
//
// The configuration is the shared one, so everything the Android build
// learned about isolation, DNS and the leak guard applies here without being
// restated. Only Fd and the SOCKS address are macOS-shaped.
func StartTunnel(cfg *Config) error {
	if cfg == nil {
		return errors.New("veiltun: nil config")
	}
	if cfg.Fd <= 0 {
		return errors.New("veiltun: invalid utun fd")
	}
	if cfg.SocksAddr == "" {
		return errors.New("veiltun: empty socks address")
	}
	if cfg.Mtu <= 0 {
		cfg.Mtu = 1500
	}
	if cfg.UDPTimeoutSec <= 0 {
		cfg.UDPTimeoutSec = 60
	}
	if cfg.DialTimeoutSec <= 0 {
		cfg.DialTimeoutSec = 30
	}

	mu.Lock()
	defer mu.Unlock()
	if current != nil {
		return errors.New("veiltun: already running")
	}

	dev, err := openDevice(cfg.Fd, cfg.Mtu)
	if err != nil {
		return fmt.Errorf("veiltun: open utun: %w", err)
	}

	h, err := newHandler(cfg)
	if err != nil {
		dev.Close()
		return fmt.Errorf("veiltun: handler: %w", err)
	}

	st, err := core.CreateStack(&core.Config{
		LinkEndpoint:     dev,
		TransportHandler: h,
		Options: []option.Option{
			option.WithTCPModerateReceiveBuffer(true),
			option.WithTCPSACKEnabled(true),
			option.WithTCPCongestionControl("cubic"),
		},
	})
	if err != nil {
		dev.Close()
		h.Close()
		return fmt.Errorf("veiltun: create stack: %w", err)
	}

	current = &session{stack: st, dev: dev, h: h}
	logf("info", "tunnel up: socks=%s://%s dns=%s/%s isolate=%s blockUDP=%v mtu=%d",
		cfg.socksNetwork(), cfg.SocksAddr, cfg.DNSMode, cfg.DNSAddr,
		cfg.IsolateBy, cfg.BlockUDP, cfg.Mtu)
	return nil
}

// NewTunnelConfig is the constructor the Swift side calls. gomobile exports
// struct fields as properties, but not literals, so the defaults have to come
// from somewhere callable.
//
// The defaults are the ones Tor needs: UDP dropped, because Tor cannot carry
// it and anything that escaped would be both a leak and a fingerprint; and
// names resolved through the tunnel rather than by the machine's resolver.
func NewTunnelConfig(fd int, mtu int, socksSocketPath string) *Config {
	return &Config{
		Fd:             fd,
		Mtu:            mtu,
		SocksAddr:      socksSocketPath,
		SocksNetwork:   "unix",
		IsolateBy:      IsolateNone,
		// DNS over TCP through the tunnel, to a resolver that filters nothing
		// and keeps no logs. It has to be named: the resolver refuses to be
		// built without one, and a tunnel whose names do not resolve looks
		// exactly like a tunnel that does not work.
		DNSMode:        DNSOverTCP,
		DNSAddr:        "9.9.9.9:53",
		BlockUDP:       true,
		UDPTimeoutSec:  60,
		DialTimeoutSec: 30,
	}
}
