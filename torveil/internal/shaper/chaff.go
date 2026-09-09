package shaper

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"
)

// Chaff channel framing.
const (
	chaffMagic     = 0xC7
	chaffHeaderLen = 8
	maxChaffFrame  = 32 * 1024
	// chaffOnionPort is the virtual port of the ephemeral onion service. It
	// never appears on the public internet.
	chaffOnionPort = 9001
)

// Dialer opens connections through Tor.
type Dialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}

// OnionAllocator publishes an ephemeral onion service forwarding virtualPort
// to target and returns the service ID (without the ".onion" suffix).
type OnionAllocator func(ctx context.Context, virtualPort int, target string) (string, error)

// LogFunc receives chaff diagnostics.
type LogFunc func(level, msg string)

// Chaff generates cover traffic on a channel that terminates at an ephemeral
// onion service hosted by this process.
//
// Sending dummy bytes to a real destination would be both useless and rude:
// the destination would receive them. Sending them to our own onion service
// keeps every byte inside the Tor network — it never reaches an exit relay —
// while still sharing the single TLS connection to the guard with the user's
// real traffic. That shared connection is the whole point: an observer on the
// user's link sees one encrypted flow whose volume and timing no longer track
// what the user is actually doing.
//
// The cost is real. Each chaff byte is carried over roughly six relay hops
// (three out to the rendezvous point, three back), all of it donated
// bandwidth, which is why every profile caps the rate.
type Chaff struct {
	shaper  *Shaper
	dialer  Dialer
	allocFn OnionAllocator
	log     LogFunc

	mu       sync.Mutex
	running  bool
	cancel   context.CancelFunc
	sink     net.Listener
	onionID  string
	sinkPort int
	wg       sync.WaitGroup

	// filler is a fixed block of random bytes reused as payload. The content
	// is irrelevant — Tor encrypts it — so there is no reason to burn entropy
	// generating it afresh.
	filler []byte
}

// NewChaff creates a chaff generator.
func NewChaff(s *Shaper, d Dialer, alloc OnionAllocator, log LogFunc) *Chaff {
	filler := make([]byte, maxChaffFrame)
	if _, err := rand.Read(filler); err != nil {
		for i := range filler {
			filler[i] = byte(i * 31)
		}
	}
	return &Chaff{shaper: s, dialer: d, allocFn: alloc, log: log, filler: filler}
}

func (c *Chaff) logf(level, format string, args ...any) {
	if c.log != nil {
		c.log(level, fmt.Sprintf(format, args...))
	}
}

// Running reports whether the chaff channel is active.
func (c *Chaff) Running() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}

// OnionAddress returns the chaff sink's onion address, for display.
func (c *Chaff) OnionAddress() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.onionID == "" {
		return ""
	}
	return c.onionID + ".onion"
}

// Start publishes the sink as an onion service and begins generating cover
// traffic. It is a no-op when the active profile disables chaff.
func (c *Chaff) Start(ctx context.Context) error {
	if c.shaper.Profile().ChaffMode == ChaffOff {
		return nil
	}

	c.mu.Lock()
	if c.running {
		c.mu.Unlock()
		return nil
	}

	sink, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		c.mu.Unlock()
		return fmt.Errorf("chaff sink listen: %w", err)
	}
	c.sink = sink
	c.sinkPort = sink.Addr().(*net.TCPAddr).Port

	onionID, err := c.allocFn(ctx, chaffOnionPort, fmt.Sprintf("127.0.0.1:%d", c.sinkPort))
	if err != nil {
		sink.Close()
		c.sink = nil
		c.mu.Unlock()
		return fmt.Errorf("publish chaff onion service: %w", err)
	}
	c.onionID = onionID

	runCtx, cancel := context.WithCancel(context.Background())
	c.cancel = cancel
	c.running = true
	c.mu.Unlock()

	c.logf("info", "chaff channel published at %s.onion:%d", onionID, chaffOnionPort)

	c.wg.Add(2)
	go c.serveSink(runCtx, sink)
	go c.pump(runCtx, onionID)
	return nil
}

// Stop tears the chaff channel down.
func (c *Chaff) Stop() {
	c.mu.Lock()
	if !c.running {
		c.mu.Unlock()
		return
	}
	c.running = false
	cancel, sink := c.cancel, c.sink
	c.sink, c.cancel, c.onionID = nil, nil, ""
	c.mu.Unlock()

	if cancel != nil {
		cancel()
	}
	if sink != nil {
		sink.Close()
	}
	c.wg.Wait()
}

// serveSink accepts the chaff connection and answers it.
func (c *Chaff) serveSink(ctx context.Context, l net.Listener) {
	defer c.wg.Done()
	for {
		conn, err := l.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				return
			default:
			}
			if errors.Is(err, net.ErrClosed) {
				return
			}
			c.logf("warn", "chaff sink accept: %v", err)
			return
		}
		c.wg.Add(1)
		go func() {
			defer c.wg.Done()
			defer conn.Close()
			c.handleSink(ctx, conn)
		}()
	}
}

// handleSink reads chaff frames, discards the payload, and sends back the
// requested number of bytes so cover traffic exists in both directions.
func (c *Chaff) handleSink(ctx context.Context, conn net.Conn) {
	header := make([]byte, chaffHeaderLen)
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		_ = conn.SetReadDeadline(time.Now().Add(120 * time.Second))
		if _, err := io.ReadFull(conn, header); err != nil {
			return
		}
		if header[0] != chaffMagic {
			return
		}
		payloadLen := int64(header[1])<<16 | int64(header[2])<<8 | int64(header[3])
		requestDown := int64(binary.BigEndian.Uint32(header[4:8]))
		if payloadLen > maxChaffFrame || requestDown > maxChaffFrame {
			return
		}

		if _, err := io.CopyN(io.Discard, conn, payloadLen); err != nil {
			return
		}

		if requestDown > 0 {
			_ = conn.SetWriteDeadline(time.Now().Add(60 * time.Second))
			if _, err := conn.Write(c.filler[:requestDown]); err != nil {
				return
			}
		}
	}
}

// pump keeps a connection to the chaff sink open and feeds it the per-tick
// quota the shaper hands out.
func (c *Chaff) pump(ctx context.Context, onionID string) {
	defer c.wg.Done()

	backoff := time.Second
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		addr := fmt.Sprintf("%s.onion:%d", onionID, chaffOnionPort)
		dialCtx, cancel := context.WithTimeout(ctx, 90*time.Second)
		conn, err := c.dialer.DialContext(dialCtx, "tcp", addr)
		cancel()
		if err != nil {
			select {
			case <-ctx.Done():
				return
			case <-time.After(backoff):
			}
			if backoff < 30*time.Second {
				backoff *= 2
			}
			c.logf("info", "chaff channel dial failed, retrying: %v", err)
			continue
		}
		backoff = time.Second
		c.logf("info", "chaff channel connected")

		c.runChannel(ctx, conn)
		conn.Close()
	}
}

// runChannel writes one chaff frame per tick for as long as the connection
// lasts.
func (c *Chaff) runChannel(ctx context.Context, conn net.Conn) {
	done := make(chan struct{})
	go func() {
		defer close(done)
		buf := make([]byte, 32*1024)
		for {
			n, err := conn.Read(buf)
			if n > 0 {
				c.shaper.AddChaff(0, int64(n))
			}
			if err != nil {
				return
			}
		}
	}()

	header := make([]byte, chaffHeaderLen)
	for {
		select {
		case <-ctx.Done():
			return
		case <-done:
			return
		case <-c.shaper.Tick():
		}

		quota := c.shaper.TakeChaffQuota()
		if quota <= 0 {
			continue
		}
		if quota > maxChaffFrame {
			quota = maxChaffFrame
		}

		profile := c.shaper.Profile()
		down := int64(float64(quota) * profile.ChaffDownstreamRatio)
		if down > maxChaffFrame {
			down = maxChaffFrame
		}
		if down < 0 {
			down = 0
		}

		header[0] = chaffMagic
		header[1] = byte(quota >> 16)
		header[2] = byte(quota >> 8)
		header[3] = byte(quota)
		binary.BigEndian.PutUint32(header[4:8], uint32(down))

		_ = conn.SetWriteDeadline(time.Now().Add(60 * time.Second))
		if _, err := conn.Write(header); err != nil {
			return
		}
		if _, err := conn.Write(c.filler[:quota]); err != nil {
			return
		}
		c.shaper.AddChaff(int64(chaffHeaderLen)+quota, 0)
	}
}
