//go:build darwin

package veiltun

// How packets get between macOS and this stack.
//
// On Android the VpnService hands over a file descriptor and gVisor's
// `fdbased` endpoint reads it directly. That endpoint is Linux-only — it uses
// readv/writev semantics and packet-info framing that do not exist here — so
// macOS needs a different way in, and there are two.
//
// The obvious one is NEPacketTunnelFlow: `readPackets` and `writePackets`,
// the supported API. It is also the wrong one for a tunnel that carries a
// whole machine's traffic, because every packet would cross the Go/Objective-C
// bridge one object at a time.
//
// So this takes the same route the WireGuard client takes: the packet tunnel's
// utun socket has a real file descriptor behind it, the extension reads it off
// the flow with a key path, and the descriptor is handed here exactly as
// Android hands over its own. What arrives is then an ordinary file, and
// `iobased` turns an io.ReadWriter into a link endpoint.
//
// The one macOS detail that matters: a utun descriptor is not raw IP. Every
// packet, in both directions, carries a four-byte address-family header in
// front of it (AF_INET or AF_INET6, big-endian). `iobased` already knows how
// to skip a fixed prefix — that is what its offset argument is for — so
// reading needs nothing special. Writing does: the header has to be put back,
// and with the right family, or the kernel drops the packet without a word.

import (
	"encoding/binary"
	"errors"
	"os"
	"sync"
	"syscall"

	"github.com/xjasonlyu/tun2socks/v2/core/device"
	"github.com/xjasonlyu/tun2socks/v2/core/device/iobased"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// utunHeaderSize is the address-family prefix on every utun packet.
const utunHeaderSize = 4

// Address families as the utun header spells them. These are the darwin
// values, which is the only place this file builds.
const (
	afINET  = 2
	afINET6 = 30
)

// utunFile is the io.ReadWriter given to the link endpoint.
//
// Reads pass straight through: the endpoint is told the packets have a
// four-byte prefix and skips it itself. Writes have to add the prefix back,
// which means looking at the first nibble of the packet to tell IPv4 from
// IPv6 — the same test the kernel will make when it reads it.
type utunFile struct {
	file *os.File

	// One buffer, reused, because a write is a single syscall only if the
	// header and the packet are contiguous. A tunnel writes a packet per
	// received segment, and allocating for each one is a cost paid at line
	// rate for nothing.
	mu  sync.Mutex
	buf []byte
}

func newUtunFile(fd int, mtu int) (*utunFile, error) {
	// Keep the descriptor blocking. The endpoint reads it from a dedicated
	// goroutine and a non-blocking descriptor there turns a read into a spin.
	if err := syscall.SetNonblock(fd, false); err != nil {
		return nil, err
	}
	return &utunFile{
		file: os.NewFile(uintptr(fd), "utun"),
		buf:  make([]byte, utunHeaderSize+mtu+utunHeaderSize),
	}, nil
}

func (u *utunFile) Read(p []byte) (int, error) { return u.file.Read(p) }

func (u *utunFile) Write(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	family := uint32(afINET)
	if p[0]>>4 == 6 {
		family = afINET6
	}

	u.mu.Lock()
	defer u.mu.Unlock()
	if len(u.buf) < utunHeaderSize+len(p) {
		u.buf = make([]byte, utunHeaderSize+len(p))
	}
	binary.BigEndian.PutUint32(u.buf[:utunHeaderSize], family)
	copy(u.buf[utunHeaderSize:], p)

	n, err := u.file.Write(u.buf[:utunHeaderSize+len(p)])
	if err != nil {
		return 0, err
	}
	// Report what the caller wrote, not what went to the kernel: the header
	// is ours and a short count would look like a partial packet.
	if n < utunHeaderSize {
		return 0, errors.New("veiltun: short utun write")
	}
	return n - utunHeaderSize, nil
}

func (u *utunFile) Close() error { return u.file.Close() }

// utunDevice is the endpoint plus the two names device.Device asks for.
type utunDevice struct {
	*iobased.Endpoint
	rw *utunFile
}

func (d *utunDevice) Name() string { return "utun" }
func (d *utunDevice) Type() string { return "packet-tunnel" }

// Close takes no error, because stack.LinkEndpoint's does not: the stack
// closes its endpoint on teardown and has nowhere to report a failure to.
func (d *utunDevice) Close() {
	d.Endpoint.Close()
	if err := d.rw.Close(); err != nil {
		logf("warn", "closing the utun descriptor: %v", err)
	}
}

// openDevice wraps a utun descriptor as a link endpoint.
func openDevice(fd int, mtu int) (device.Device, error) {
	if fd <= 0 {
		return nil, errors.New("veiltun: invalid utun fd")
	}
	rw, err := newUtunFile(fd, mtu)
	if err != nil {
		return nil, err
	}
	endpoint, err := iobased.New(rw, uint32(mtu), utunHeaderSize)
	if err != nil {
		_ = rw.Close()
		return nil, err
	}
	return &utunDevice{Endpoint: endpoint, rw: rw}, nil
}

// Compile-time assurance that the endpoint really is a link endpoint; the
// alternative is finding out when the stack refuses to attach.
var _ stack.LinkEndpoint = (*utunDevice)(nil)
