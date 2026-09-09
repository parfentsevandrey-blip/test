package shaper

import (
	"net"
	"testing"
	"time"
)

func TestProfilesAreWellFormed(t *testing.T) {
	seen := map[ProfileID]bool{}
	for _, p := range Profiles() {
		if seen[p.ID] {
			t.Errorf("duplicate profile id %q", p.ID)
		}
		seen[p.ID] = true

		if p.Name == "" || p.Description == "" || p.EstimatedOverhead == "" {
			t.Errorf("profile %q is missing text the UI shows", p.ID)
		}
		if p.ChaffMode != ChaffOff && p.ChaffRate <= 0 {
			t.Errorf("profile %q generates cover traffic with no rate ceiling; that load falls on volunteer relays", p.ID)
		}
		if p.Jitter > p.Tick && p.Tick > 0 {
			t.Errorf("profile %q has more jitter (%s) than tick (%s)", p.ID, p.Jitter, p.Tick)
		}
	}

	for _, id := range []ProfileID{ProfileOff, ProfileLight, ProfileBalanced, ProfileParanoid} {
		if !seen[id] {
			t.Errorf("profile %q is not offered", id)
		}
	}
}

func TestProfileByID(t *testing.T) {
	p, err := ProfileByID("BALANCED")
	if err != nil {
		t.Fatalf("ProfileByID: %v", err)
	}
	if p.ID != ProfileBalanced {
		t.Errorf("got %q, want balanced", p.ID)
	}
	if _, err := ProfileByID("nonsense"); err == nil {
		t.Error("expected an error for an unknown profile")
	}
	if MustProfile("nonsense").ID != ProfileBalanced {
		t.Error("MustProfile should fall back to balanced")
	}
}

func TestComputeQuotaOffProfileGeneratesNothing(t *testing.T) {
	s := New(MustProfile(ProfileOff))
	if q := s.computeQuota(s.Profile(), 0, 100000, 0.01); q != 0 {
		t.Errorf("quota = %d, want 0 when chaff is disabled", q)
	}
}

func TestComputeQuotaFillsTheDeficit(t *testing.T) {
	p := MustProfile(ProfileParanoid)
	s := New(p)

	// A tick with no real traffic should be filled up to the shaped rate.
	const seconds = 0.01
	shaped := 100_000.0
	q := s.computeQuota(p, 0, shaped, seconds)
	want := int64(shaped * seconds)
	if q != want {
		t.Errorf("quota = %d, want %d", q, want)
	}

	// Real traffic displaces cover traffic one byte for one byte.
	q = s.computeQuota(p, want/2, shaped, seconds)
	if q != want-want/2 {
		t.Errorf("quota = %d, want %d", q, want-want/2)
	}

	// Once real traffic meets the target there is nothing left to fill.
	if q := s.computeQuota(p, want*2, shaped, seconds); q != 0 {
		t.Errorf("quota = %d, want 0 when real traffic exceeds the target", q)
	}
}

func TestComputeQuotaRespectsTheRateCeiling(t *testing.T) {
	p := MustProfile(ProfileParanoid)
	p.ChaffRate = 1024 // deliberately tiny
	s := New(p)

	const seconds = 1.0
	q := s.computeQuota(p, 0, 10_000_000, seconds)
	if q > int64(p.ChaffRate) {
		t.Errorf("quota = %d exceeds the ceiling of %d bytes/sec", q, p.ChaffRate)
	}
}

func TestBurstChaffStopsWhenIdle(t *testing.T) {
	p := MustProfile(ProfileBalanced)
	p.ChaffIdleGrace = 50 * time.Millisecond
	s := New(p)

	// Never any real traffic: burst mode must stay silent, so an idle machine
	// stays idle on the wire.
	if q := s.computeQuota(p, 0, 100000, 0.01); q != 0 {
		t.Errorf("quota = %d, want 0 before any real traffic", q)
	}

	s.lastRealAt.Store(time.Now().UnixNano())
	if q := s.computeQuota(p, 0, 100000, 0.01); q <= 0 {
		t.Error("expected cover traffic right after real activity")
	}

	s.lastRealAt.Store(time.Now().Add(-time.Second).UnixNano())
	if q := s.computeQuota(p, 0, 100000, 0.01); q != 0 {
		t.Errorf("quota = %d, want 0 once the idle grace period has passed", q)
	}
}

func TestAdvanceDecaysTheShapedRate(t *testing.T) {
	p := MustProfile(ProfileParanoid)
	s := New(p)

	// A burst of real traffic raises the shaped rate...
	s.tickReal.Store(50_000)
	s.advance(p, 10*time.Millisecond)
	raised := s.shapedRate.Load()
	if raised < 50_000 {
		t.Fatalf("shaped rate = %d, expected it to rise to meet demand", raised)
	}

	// ...and it then decays gradually rather than dropping to idle, which is
	// what stops the end of a transfer from being visible as a cliff edge.
	s.advance(p, 10*time.Millisecond)
	afterOneTick := s.shapedRate.Load()
	if afterOneTick >= raised {
		t.Errorf("rate did not decay: %d -> %d", raised, afterOneTick)
	}
	if afterOneTick < raised/2 {
		t.Errorf("rate collapsed in a single tick: %d -> %d", raised, afterOneTick)
	}
}

func TestShapedRateHasAFloor(t *testing.T) {
	p := MustProfile(ProfileParanoid)
	s := New(p)
	for i := 0; i < 500; i++ {
		s.advance(p, time.Second)
	}
	if got := s.shapedRate.Load(); got < minShapedRate {
		t.Errorf("shaped rate decayed to %d, below the floor of %d", got, minShapedRate)
	}
}

func TestWrapCountsTrafficAndReleasesOnTick(t *testing.T) {
	s := New(MustProfile(ProfileLight))
	s.Start()
	defer s.Stop()

	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()

	shaped := s.Wrap(client)
	payload := []byte("hello over the shaped path")

	done := make(chan error, 1)
	go func() {
		_, err := shaped.Write(payload)
		done <- err
	}()

	buf := make([]byte, len(payload))
	_ = server.SetReadDeadline(time.Now().Add(5 * time.Second))
	if _, err := server.Read(buf); err != nil {
		t.Fatalf("read: %v", err)
	}
	if err := <-done; err != nil {
		t.Fatalf("write: %v", err)
	}
	if string(buf) != string(payload) {
		t.Errorf("payload was altered: %q", buf)
	}

	if got := s.Stats().RealOut; got != int64(len(payload)) {
		t.Errorf("RealOut = %d, want %d", got, len(payload))
	}
}

func TestStatsOverhead(t *testing.T) {
	s := New(MustProfile(ProfileBalanced))
	s.addReal(800, 200)
	s.AddChaff(200, 300)

	got := s.Stats()
	if got.RealOut != 800 || got.RealIn != 200 {
		t.Errorf("real counters = %d/%d", got.RealOut, got.RealIn)
	}
	// 500 bytes of cover traffic against 1000 bytes of real traffic.
	if got.OverheadPct < 49.9 || got.OverheadPct > 50.1 {
		t.Errorf("overhead = %.2f%%, want 50%%", got.OverheadPct)
	}
}

func TestStopReleasesBlockedWriters(t *testing.T) {
	s := New(MustProfile(ProfileParanoid))
	s.Start()

	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()

	shaped := s.Wrap(client)
	shaped.Close() // a closed connection must not park forever on the clock

	done := make(chan struct{})
	go func() {
		_, _ = shaped.Write([]byte("x"))
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(3 * time.Second):
		t.Fatal("Write blocked after the connection was closed")
	}
	s.Stop()
}
