// Package veiltun bridges an Android VpnService TUN file descriptor to a local
// SOCKS5 endpoint provided by a circumvention engine (Tor, or anything else
// that speaks SOCKS5).
//
// It runs a userspace TCP/IP stack (gVisor netstack) so that no root and no
// kernel routing tricks are required: the VpnService hands us a file
// descriptor, we terminate TCP/UDP on it and re-originate the payload through
// the proxy.
//
// Design notes that matter for a censorship-circumvention app:
//
//   - Tor carries TCP only. Any UDP that escapes the tunnel is both a leak and
//     a fingerprint, so UDP is dropped by default and DNS is special-cased.
//   - DNS never leaves as plain UDP to the network's resolver. It is answered
//     through the tunnel (Tor's DNSPort, DNS-over-TCP through SOCKS, or DoH
//     through SOCKS), which is what stops the most common form of blocking.
//   - Every TCP flow can be given distinct SOCKS credentials so that Tor
//     builds a separate circuit per destination (IsolateSOCKSAuth), which is
//     the same first-party isolation Tor Browser applies.
package veiltun

import (
	"errors"
	"fmt"
	"sync"
	"sync/atomic"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/core/option"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// Isolation strategies for SOCKS credentials.
const (
	IsolateNone = "none" // one shared circuit; fastest, least private
	IsolateHost = "host" // one circuit per destination host (recommended)
	IsolateConn = "conn" // one circuit per connection; slowest, most private
)

// DNS resolution strategies.
const (
	DNSUDPLoopback = "udp"  // forward to a loopback resolver, e.g. Tor's DNSPort
	DNSOverTCP     = "tcp"  // DNS over TCP, dialed through the SOCKS proxy
	DNSOverHTTPS   = "doh"  // RFC 8484, dialed through the SOCKS proxy
	DNSDrop        = "drop" // answer nothing; for diagnostics only
)

// Config is the full description of one tunnel session. gomobile turns this
// into a Java class with getters and setters.
type Config struct {
	// Fd is the TUN file descriptor obtained from
	// ParcelFileDescriptor.detachFd(). veiltun takes ownership and closes it.
	Fd int
	// Mtu must match the value given to VpnService.Builder.setMtu().
	Mtu int

	// SocksAddr is the "host:port" of the upstream SOCKS5 proxy.
	SocksAddr string
	// SocksUser/SocksPass are used when IsolateBy is IsolateNone. With the
	// other strategies credentials are derived per flow instead.
	SocksUser string
	SocksPass string
	// IsolateBy is one of IsolateNone, IsolateHost, IsolateConn.
	IsolateBy string

	// DNSMode is one of DNSUDPLoopback, DNSOverTCP, DNSOverHTTPS, DNSDrop.
	DNSMode string
	// DNSAddr is a "host:port" for the udp/tcp modes or an https:// URL for doh.
	DNSAddr string

	// BlockUDP drops all non-DNS UDP. Required for Tor, which cannot carry it.
	// When false, non-DNS UDP is relayed with a SOCKS5 UDP ASSOCIATE.
	BlockUDP bool
	// UDPTimeoutSec bounds how long an idle UDP flow is kept.
	UDPTimeoutSec int
	// DialTimeoutSec bounds a single upstream TCP dial.
	DialTimeoutSec int
}

// NewConfig returns a Config with defaults appropriate for Tor.
func NewConfig() *Config {
	return &Config{
		Mtu:            1500,
		IsolateBy:      IsolateHost,
		DNSMode:        DNSUDPLoopback,
		BlockUDP:       true,
		UDPTimeoutSec:  60,
		DialTimeoutSec: 30,
	}
}

// Stats is an immutable snapshot of tunnel counters.
type Stats struct {
	RxBytes    int64 // bytes delivered to apps
	TxBytes    int64 // bytes sent upstream
	TCPOpen    int64 // currently open TCP flows
	TCPTotal   int64
	UDPTotal   int64
	DNSQueries int64
	DNSErrors  int64
	DialErrors int64
	Blocked    int64 // UDP datagrams dropped by the leak guard
}

type counters struct {
	rx, tx                      atomic.Int64
	tcpOpen, tcpTotal, udpTotal atomic.Int64
	dnsQueries, dnsErrors       atomic.Int64
	dialErrors, blocked         atomic.Int64
}

type session struct {
	stack  *stack.Stack
	dev    device.Device
	h      *handler
	closed atomic.Bool
}

// errNoController is returned when the transport controller failed to come up.
var errNoController = errors.New("veiltun: transport controller unavailable")

var (
	mu      sync.Mutex
	current *session
	stats   = &counters{}
	// Logf, when set from Java via SetLogger, receives one line per event.
	logSink atomic.Pointer[Logger]
)

// Logger receives tunnel log lines. Implemented on the Java side.
type Logger interface {
	Log(level string, msg string)
}

// SetLogger installs a log sink. Pass nil to remove it.
func SetLogger(l Logger) {
	if l == nil {
		logSink.Store(nil)
		return
	}
	logSink.Store(&l)
}

func logf(level, format string, args ...any) {
	if p := logSink.Load(); p != nil {
		(*p).Log(level, fmt.Sprintf(format, args...))
	}
}

// Start brings the tunnel up. It returns once the stack is running; traffic is
// then served on background goroutines until Stop is called.
func Start(cfg *Config) error {
	if cfg == nil {
		return errors.New("veiltun: nil config")
	}
	if cfg.Fd <= 0 {
		return errors.New("veiltun: invalid tun fd")
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

	dev, err := fdbased.Open(fmt.Sprintf("%d", cfg.Fd), uint32(cfg.Mtu), 0)
	if err != nil {
		return fmt.Errorf("veiltun: open tun fd: %w", err)
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
			// BBR keeps throughput sane over a high-latency, lossy overlay
			// like Tor far better than Reno does.
			option.WithTCPCongestionControl("cubic"),
		},
	})
	if err != nil {
		dev.Close()
		h.Close()
		return fmt.Errorf("veiltun: create stack: %w", err)
	}

	current = &session{stack: st, dev: dev, h: h}
	logf("info", "tunnel up: socks=%s dns=%s/%s isolate=%s blockUDP=%v mtu=%d",
		cfg.SocksAddr, cfg.DNSMode, cfg.DNSAddr, cfg.IsolateBy, cfg.BlockUDP, cfg.Mtu)
	return nil
}

// Stop tears the tunnel down and closes the TUN fd. It is safe to call when
// nothing is running.
func Stop() {
	mu.Lock()
	s := current
	current = nil
	mu.Unlock()
	if s == nil || !s.closed.CompareAndSwap(false, true) {
		return
	}
	s.h.Close()
	s.stack.Close()
	s.dev.Close()
	logf("info", "tunnel down")
}

// IsRunning reports whether a tunnel session is active.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return current != nil
}

// Snapshot returns the current counters.
func Snapshot() *Stats {
	return &Stats{
		RxBytes:    stats.rx.Load(),
		TxBytes:    stats.tx.Load(),
		TCPOpen:    stats.tcpOpen.Load(),
		TCPTotal:   stats.tcpTotal.Load(),
		UDPTotal:   stats.udpTotal.Load(),
		DNSQueries: stats.dnsQueries.Load(),
		DNSErrors:  stats.dnsErrors.Load(),
		DialErrors: stats.dialErrors.Load(),
		Blocked:    stats.blocked.Load(),
	}
}

// ResetStats zeroes the counters. Called when a new session starts so the UI
// shows per-session traffic.
func ResetStats() {
	stats.rx.Store(0)
	stats.tx.Store(0)
	stats.tcpTotal.Store(0)
	stats.udpTotal.Store(0)
	stats.dnsQueries.Store(0)
	stats.dnsErrors.Store(0)
	stats.dialErrors.Store(0)
	stats.blocked.Store(0)
}
