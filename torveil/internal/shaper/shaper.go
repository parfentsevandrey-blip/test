package shaper

import (
	"crypto/rand"
	"math"
	"math/big"
	"net"
	"sync"
	"sync/atomic"
	"time"
)

// Rate adaptation constants for the constant-rate and burst chaff modes.
const (
	// rateDecayHalfLife is how long the shaped send rate takes to fall to half
	// after real traffic stops.
	//
	// This slow decay is the point of the mechanism: if the rate dropped the
	// instant a download finished, the drop itself would announce that the
	// download had finished.
	rateDecayHalfLife = 25 * time.Second

	// rateHeadroom keeps the shaped rate slightly above observed demand so
	// there is always some chaff mixed into real traffic.
	rateHeadroom = 1.15

	// minShapedRate is the floor of the shaped rate, in bytes per second.
	minShapedRate = 8 * 1024
)

// Stats is a snapshot of what the shaper has moved.
type Stats struct {
	Profile     ProfileID `json:"profile"`
	RealOut     int64     `json:"realOut"`
	RealIn      int64     `json:"realIn"`
	ChaffOut    int64     `json:"chaffOut"`
	ChaffIn     int64     `json:"chaffIn"`
	ShapedRate  int64     `json:"shapedRate"`  // current target, bytes/sec
	OverheadPct float64   `json:"overheadPct"` // chaff as a share of real traffic
	Connections int64     `json:"connections"`
}

// Shaper applies timing quantisation to outbound streams and drives the chaff
// generator. One Shaper is shared by every connection so that all streams are
// released on the same clock: aligning them is what merges their individual
// bursts into a single aggregate burst at the guard.
type Shaper struct {
	mu      sync.RWMutex
	profile Profile

	tickMu sync.Mutex
	tickCh chan struct{}

	running bool
	stopCh  chan struct{}
	wg      sync.WaitGroup

	realOut  atomic.Int64
	realIn   atomic.Int64
	chaffOut atomic.Int64
	chaffIn  atomic.Int64
	conns    atomic.Int64

	// tickReal accumulates real bytes written during the current tick and is
	// drained by the clock.
	tickReal atomic.Int64

	// shapedRate is the current target send rate in bytes per second.
	shapedRate atomic.Int64

	// chaffQuota is the number of bytes the chaff generator should send in
	// the current tick.
	chaffQuota atomic.Int64

	lastRealAt atomic.Int64 // unix nanos
}

// New creates a Shaper for a profile. Start must be called before it shapes
// anything.
func New(p Profile) *Shaper {
	s := &Shaper{
		profile: p,
		tickCh:  make(chan struct{}),
	}
	s.shapedRate.Store(minShapedRate)
	return s
}

// Profile returns the active profile.
func (s *Shaper) Profile() Profile {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.profile
}

// SetProfile swaps the profile at runtime. The clock picks up the new tick on
// its next iteration.
func (s *Shaper) SetProfile(p Profile) {
	s.mu.Lock()
	s.profile = p
	s.mu.Unlock()
}

// Start begins the shared clock.
func (s *Shaper) Start() {
	s.mu.Lock()
	if s.running {
		s.mu.Unlock()
		return
	}
	s.running = true
	s.stopCh = make(chan struct{})
	stop := s.stopCh
	s.mu.Unlock()

	s.wg.Add(1)
	go s.clock(stop)
}

// Stop halts the clock. Pending writes are released so no connection is left
// blocked waiting for a tick that will never come.
func (s *Shaper) Stop() {
	s.mu.Lock()
	if !s.running {
		s.mu.Unlock()
		return
	}
	s.running = false
	close(s.stopCh)
	s.mu.Unlock()

	s.wg.Wait()
	s.broadcast() // release anyone parked on the final tick
}

// Tick returns a channel closed at the next clock tick.
func (s *Shaper) Tick() <-chan struct{} {
	s.tickMu.Lock()
	defer s.tickMu.Unlock()
	return s.tickCh
}

func (s *Shaper) broadcast() {
	s.tickMu.Lock()
	ch := s.tickCh
	s.tickCh = make(chan struct{})
	s.tickMu.Unlock()
	close(ch)
}

// clock releases queued writes and recomputes the chaff quota once per tick.
func (s *Shaper) clock(stop <-chan struct{}) {
	defer s.wg.Done()
	for {
		p := s.Profile()
		interval := p.Tick
		if interval <= 0 {
			interval = 20 * time.Millisecond
		}
		if p.Jitter > 0 {
			interval += jitter(p.Jitter)
		}

		timer := time.NewTimer(interval)
		select {
		case <-stop:
			timer.Stop()
			return
		case <-timer.C:
		}

		s.advance(p, interval)
		s.broadcast()
	}
}

// advance updates the adaptive rate and sets the chaff quota for this tick.
func (s *Shaper) advance(p Profile, interval time.Duration) {
	real := s.tickReal.Swap(0)
	if real > 0 {
		s.lastRealAt.Store(time.Now().UnixNano())
	}

	seconds := interval.Seconds()
	if seconds <= 0 {
		seconds = 0.02
	}
	observed := float64(real) / seconds

	// The shaped rate rises immediately to meet demand and decays slowly, so
	// the end of a transfer is smoothed out instead of being announced by a
	// sudden drop to idle.
	current := float64(s.shapedRate.Load())
	decay := math.Pow(0.5, seconds/rateDecayHalfLife.Seconds())
	next := current * decay
	if want := observed * rateHeadroom; want > next {
		next = want
	}
	if next < minShapedRate {
		next = minShapedRate
	}
	s.shapedRate.Store(int64(next))

	s.chaffQuota.Store(s.computeQuota(p, real, next, seconds))
}

// computeQuota decides how many chaff bytes this tick should carry.
func (s *Shaper) computeQuota(p Profile, real int64, shapedRate, seconds float64) int64 {
	if p.ChaffMode == ChaffOff || p.ChaffRate <= 0 {
		return 0
	}
	if p.ChaffMode == ChaffBurst {
		last := s.lastRealAt.Load()
		if last == 0 || time.Since(time.Unix(0, last)) > p.ChaffIdleGrace {
			return 0
		}
	}

	target := int64(shapedRate * seconds)
	deficit := target - real
	if deficit <= 0 {
		return 0
	}
	// Never let cover traffic exceed the profile's ceiling: the bytes travel
	// six relay hops each, and volunteers pay for them.
	if maxPerTick := int64(float64(p.ChaffRate) * seconds); deficit > maxPerTick {
		deficit = maxPerTick
	}
	return deficit
}

// TakeChaffQuota returns and clears the chaff allowance for the current tick.
func (s *Shaper) TakeChaffQuota() int64 { return s.chaffQuota.Swap(0) }

// Wrap returns conn with its writes aligned to the shared clock. Reads are
// only accounted for: delaying inbound data would add latency without
// changing anything an observer sees.
func (s *Shaper) Wrap(conn net.Conn) net.Conn {
	s.conns.Add(1)
	return &shapedConn{Conn: conn, s: s, closed: make(chan struct{})}
}

func (s *Shaper) addReal(out, in int64) {
	if out > 0 {
		s.realOut.Add(out)
		s.tickReal.Add(out)
	}
	if in > 0 {
		s.realIn.Add(in)
	}
}

// AddChaff records cover traffic. The chaff generator calls this.
func (s *Shaper) AddChaff(out, in int64) {
	if out > 0 {
		s.chaffOut.Add(out)
	}
	if in > 0 {
		s.chaffIn.Add(in)
	}
}

// Stats returns a snapshot of shaper counters.
func (s *Shaper) Stats() Stats {
	realOut, realIn := s.realOut.Load(), s.realIn.Load()
	chaffOut, chaffIn := s.chaffOut.Load(), s.chaffIn.Load()

	var overhead float64
	if total := realOut + realIn; total > 0 {
		overhead = float64(chaffOut+chaffIn) / float64(total) * 100
	}
	return Stats{
		Profile:     s.Profile().ID,
		RealOut:     realOut,
		RealIn:      realIn,
		ChaffOut:    chaffOut,
		ChaffIn:     chaffIn,
		ShapedRate:  s.shapedRate.Load(),
		OverheadPct: overhead,
		Connections: s.conns.Load(),
	}
}

// shapedConn releases writes on the shared clock.
type shapedConn struct {
	net.Conn
	s         *Shaper
	closeOnce sync.Once
	closed    chan struct{}
}

func (c *shapedConn) Write(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if c.s.Profile().Tick <= 0 {
		n, err := c.Conn.Write(p)
		c.s.addReal(int64(n), 0)
		return n, err
	}

	select {
	case <-c.s.Tick():
	case <-c.closed:
		return 0, net.ErrClosed
	}

	n, err := c.Conn.Write(p)
	c.s.addReal(int64(n), 0)
	return n, err
}

func (c *shapedConn) Read(p []byte) (int, error) {
	n, err := c.Conn.Read(p)
	c.s.addReal(0, int64(n))
	return n, err
}

func (c *shapedConn) Close() error {
	c.closeOnce.Do(func() {
		close(c.closed)
		c.s.conns.Add(-1)
	})
	return c.Conn.Close()
}

// CloseWrite forwards the half-close so a proxied stream still ends with a
// clean EOF rather than a reset once the shaper is in the path.
func (c *shapedConn) CloseWrite() error {
	type closeWriter interface{ CloseWrite() error }
	if cw, ok := c.Conn.(closeWriter); ok {
		return cw.CloseWrite()
	}
	return c.Close()
}

// jitter returns a uniformly random duration in [0, max).
func jitter(max time.Duration) time.Duration {
	if max <= 0 {
		return 0
	}
	n, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		return max / 2
	}
	return time.Duration(n.Int64())
}
