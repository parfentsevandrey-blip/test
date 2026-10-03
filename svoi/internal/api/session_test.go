package api

import (
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"
)

type fakeClock struct{ t time.Time }

func (c *fakeClock) now() time.Time { return c.t }

func newTestSessions(t *testing.T) (*sessions, *fakeClock, string) {
	t.Helper()
	dir := t.TempDir()
	clk := &fakeClock{t: time.Unix(1_700_000_000, 0)}
	s := newSessions(dir)
	s.now = clk.now
	return s, clk, dir
}

func TestLoginCodeExpires(t *testing.T) {
	s, clk, _ := newTestSessions(t)
	code := s.newCode()
	clk.t = clk.t.Add(loginCodeTTL - time.Second)
	// still valid just before the deadline
	if s.redeem(code) == "" {
		t.Fatal("a code was refused before it expired")
	}
	code = s.newCode()
	clk.t = clk.t.Add(loginCodeTTL)
	if s.redeem(code) != "" {
		t.Fatal("an expired code was accepted")
	}
}

func TestSessionExpiresAndSlides(t *testing.T) {
	s, clk, _ := newTestSessions(t)
	id := s.redeem(s.newCode())
	if ok, renewed := s.check(id); !ok || renewed {
		t.Fatalf("fresh session: ok=%v renewed=%v", ok, renewed)
	}
	// Used again after more than half its life: extended (and the cookie is refreshed).
	clk.t = clk.t.Add(sessionTTL/2 + time.Hour)
	if ok, renewed := s.check(id); !ok || !renewed {
		t.Fatalf("session past half-life: ok=%v renewed=%v", ok, renewed)
	}
	// So it outlives its original deadline...
	clk.t = clk.t.Add(sessionTTL/2 + 2*time.Hour)
	if ok, _ := s.check(id); !ok {
		t.Fatal("an active session expired on its original deadline")
	}
	// ...but not an idle one.
	clk.t = clk.t.Add(sessionTTL + time.Second)
	if ok, _ := s.check(id); ok {
		t.Fatal("an idle session never expired")
	}
}

func TestSessionLimits(t *testing.T) {
	s, clk, _ := newTestSessions(t)
	var first string
	for i := 0; i < maxSessions+10; i++ {
		clk.t = clk.t.Add(time.Second)
		id := s.redeem(s.newCode())
		if i == 0 {
			first = id
		}
	}
	if len(s.sess) != maxSessions {
		t.Fatalf("%d sessions kept, want %d", len(s.sess), maxSessions)
	}
	if ok, _ := s.check(first); ok {
		t.Fatal("the oldest session should have been the one dropped")
	}
	for i := 0; i < maxCodes+10; i++ {
		s.newCode()
	}
	if len(s.codes) != maxCodes {
		t.Fatalf("%d unused codes kept, want %d", len(s.codes), maxCodes)
	}
}

func TestSessionFileIsPrunedOnLoad(t *testing.T) {
	_, _, dir := newTestSessions(t)
	// One session that expired long ago and one that is still alive.
	raw := `{"` + hashOf("dead") + `":1,"` + hashOf("alive") + `":` + strconv.FormatInt(time.Now().Add(time.Hour).Unix(), 10) + `}`
	if err := os.WriteFile(filepath.Join(dir, "ui.sessions"), []byte(raw), 0o600); err != nil {
		t.Fatal(err)
	}
	r := newSessions(dir)
	if len(r.sess) != 1 {
		t.Fatalf("expired sessions were kept: %v", r.sess)
	}
	// A damaged file is not fatal: everybody just signs in again.
	if err := os.WriteFile(filepath.Join(dir, "ui.sessions"), []byte("{broken"), 0o600); err != nil {
		t.Fatal(err)
	}
	if r := newSessions(dir); len(r.sess) != 0 {
		t.Fatal("garbage produced sessions")
	}
}
