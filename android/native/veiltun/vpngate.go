package veiltun

import (
	"context"
	"errors"
	"sync"
	"time"

	"veil.app/veiltun/internal/vpngate"
)

// VpnGateEvents reports what a VPN Gate session is doing, so the app can log it
// the same way it logs a transport.
type VpnGateEvents interface {
	Connected(server string, millis int64)
	Failed(server string, reason string)
	Stopped(reason string)
}

var (
	gateMu      sync.Mutex
	gateSession *vpngate.Session
)

// StartVpnGate brings up a tunnel to one volunteer server and returns the port
// of the loopback SOCKS5 proxy that carries traffic through it.
//
// The returned port is used exactly as Tor's SOCKS port is: it is what the
// tunnel is pointed at. That is the whole reason this exists in the shape it
// does — an alternative to Tor that plugs into the same socket means the TUN
// interface, per-app routing, DNS and the rest are untouched by the choice.
//
// Only one session at a time. A second call replaces the first, because two
// tunnels fighting over the same interface is never what was meant.
func StartVpnGate(stateDir, ovpnConfig string, timeoutSeconds int, events VpnGateEvents) (int, error) {
	if ovpnConfig == "" {
		return 0, errors.New("vpngate: no configuration given")
	}
	StopVpnGate()

	if timeoutSeconds <= 0 {
		timeoutSeconds = 45
	}
	ctx, cancel := context.WithTimeout(
		context.Background(),
		time.Duration(timeoutSeconds)*time.Second,
	)
	defer cancel()

	session, err := vpngate.Dial(ctx, stateDir, ovpnConfig, eventBridge{events})
	if err != nil {
		return 0, err
	}

	gateMu.Lock()
	gateSession = session
	gateMu.Unlock()
	return session.Port, nil
}

// StopVpnGate tears the session down. Safe to call when nothing is running.
func StopVpnGate() {
	gateMu.Lock()
	session := gateSession
	gateSession = nil
	gateMu.Unlock()
	if session != nil {
		session.Close()
	}
}

// IsVpnGateRunning reports whether a session is up.
func IsVpnGateRunning() bool {
	gateMu.Lock()
	defer gateMu.Unlock()
	return gateSession != nil
}

// VpnGateAddress is the address the server assigned us, for the diagnostic.
func VpnGateAddress() string {
	gateMu.Lock()
	defer gateMu.Unlock()
	if gateSession == nil {
		return ""
	}
	return gateSession.Address
}

// eventBridge adapts the bound interface, which may be nil.
type eventBridge struct{ events VpnGateEvents }

func (b eventBridge) Connected(server string, millis int64) {
	if b.events != nil {
		b.events.Connected(server, millis)
	}
}

func (b eventBridge) Failed(server, reason string) {
	if b.events != nil {
		b.events.Failed(server, reason)
	}
}

func (b eventBridge) Stopped(reason string) {
	if b.events != nil {
		b.events.Stopped(reason)
	}
}
