package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

// A link that is about to be put on a command line (xdg-open) is read by every user
// of the machine. It is tied to the user it was made for: somebody else who grabs it
// is turned away, and the owner can still use it afterwards.
func TestLoginCodeMadeForAUserIsRefusedToOthers(t *testing.T) {
	s, clk, _ := newTestSessions(t)
	_ = clk
	code := s.newCodeFor(1000)
	as := func(uid int, known bool) func() (int, bool) { return func() (int, bool) { return uid, known } }

	if id, res := s.redeemCode(code, as(1001, true)); id != "" || res != redeemWrongUser {
		t.Fatalf("another user redeemed it: %q %v", id, res)
	}
	if id, res := s.redeemCode(code, as(0, true)); id != "" || res != redeemWrongUser {
		t.Fatalf("root's uid was not treated as another user: %q %v", id, res)
	}
	// The refusal did not use the code up: the person it was made for still gets in...
	id, res := s.redeemCode(code, as(1000, true))
	if id == "" || res != redeemOK {
		t.Fatalf("the user it was made for was refused: %q %v", id, res)
	}
	// ...once.
	if id, _ := s.redeemCode(code, as(1000, true)); id != "" {
		t.Fatal("a code worked twice")
	}
	// Where the system cannot tell who is asking, the code works (a link must not become useless).
	if id, res := s.redeemCode(s.newCodeFor(1000), as(0, false)); id == "" || res != redeemOK {
		t.Fatalf("a connection of unknown user was refused: %q %v", id, res)
	}
	// A code for nobody in particular is for anyone.
	if id, res := s.redeemCode(s.newCode(), as(4242, true)); id == "" || res != redeemOK {
		t.Fatalf("a plain code was refused to a user: %q %v", id, res)
	}
}

type uidEnv struct {
	t   *testing.T
	srv *Server
	ts  *httptest.Server
	tok string
}

func newUIDEnv(t *testing.T) *uidEnv {
	t.Helper()
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "test-box", Owner: "tester"})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { a.Close() })
	off := false
	if _, err := a.UpdateSettings(app.SettingsPatch{STUNEnabled: &off, PortMap: &off}); err != nil {
		t.Fatal(err)
	}
	s := New(a, nil)
	// Every request says in a header which user it pretends to come from.
	s.callerUID = func(r *http.Request) (int, bool) {
		v := r.Header.Get("X-Test-Uid")
		if v == "" {
			return 0, false
		}
		uid, err := strconv.Atoi(v)
		return uid, err == nil
	}
	ts := httptest.NewServer(s.Handler())
	t.Cleanup(ts.Close)
	return &uidEnv{t: t, srv: s, ts: ts, tok: a.Token()}
}

func (e *uidEnv) mint(body string, uid string) (code string, bound bool) {
	e.t.Helper()
	req, _ := http.NewRequest("POST", e.ts.URL+"/api/login/code", strings.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+e.tok)
	req.Header.Set("Content-Type", "application/json")
	if uid != "" {
		req.Header.Set("X-Test-Uid", uid)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer resp.Body.Close()
	var out struct {
		Code  string
		Bound bool
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil || resp.StatusCode != 200 {
		e.t.Fatalf("mint: %d %v", resp.StatusCode, err)
	}
	return out.Code, out.Bound
}

func (e *uidEnv) open(code, uid string) (status int, cookie bool, body string) {
	e.t.Helper()
	req, _ := http.NewRequest("GET", e.ts.URL+"/?t="+code, nil)
	if uid != "" {
		req.Header.Set("X-Test-Uid", uid)
	}
	noRedirect := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := noRedirect.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer resp.Body.Close()
	var b bytes.Buffer
	_, _ = b.ReadFrom(resp.Body)
	for _, c := range resp.Cookies() {
		if strings.HasPrefix(c.Name, cookiePrefix) && c.Value != "" && c.MaxAge >= 0 {
			cookie = true
		}
	}
	return resp.StatusCode, cookie, b.String()
}

func TestLocalLinkOfTheCommandLineIsTiedToItsUser(t *testing.T) {
	e := newUIDEnv(t)

	// `svoi open` asks for a link for a browser on this machine: tied to the asking user.
	code, bound := e.mint(`{"local":true}`, "1000")
	if !bound {
		t.Fatal("a local link was not tied to the user")
	}
	status, cookie, body := e.open(code, "1001") // somebody who read it off `ps`
	if status != http.StatusForbidden || cookie || !strings.Contains(body, "svoi url") {
		t.Fatalf("another user: status %d, cookie %v, body %q", status, cookie, body)
	}
	if status, cookie, _ := e.open(code, "1000"); status != http.StatusFound || !cookie {
		t.Fatalf("the user it was made for: status %d, cookie %v", status, cookie)
	}
	if status, cookie, _ := e.open(code, "1000"); status == http.StatusFound || cookie {
		t.Fatal("a code worked twice")
	}

	// `svoi url` prints a link to be copied, perhaps to another computer: not tied.
	code, bound = e.mint(`{}`, "1000")
	if bound {
		t.Fatal("a link for copying was tied to a user")
	}
	if status, cookie, _ := e.open(code, "1001"); status != http.StatusFound || !cookie {
		t.Fatalf("a printed link was refused to a user: status %d, cookie %v", status, cookie)
	}
	code, _ = e.mint(`{"local":false}`, "1000")
	if status, cookie, _ := e.open(code, "2000"); status != http.StatusFound || !cookie {
		t.Fatalf("local:false was tied to a user: status %d", status)
	}

	// When the system cannot say who is asking, the link is simply not tied (and works).
	code, bound = e.mint(`{"local":true}`, "")
	if bound {
		t.Fatal("a link was tied to a user nobody could identify")
	}
	if status, cookie, _ := e.open(code, "1001"); status != http.StatusFound || !cookie {
		t.Fatalf("an untied link was refused: status %d", status)
	}

	// Only the command line may mint links at all.
	req, _ := http.NewRequest("POST", e.ts.URL+"/api/login/code", strings.NewReader(`{"local":true}`))
	if resp, err := http.DefaultClient.Do(req); err != nil || resp.StatusCode != 401 {
		t.Fatalf("minting without the token: %v %v", err, resp)
	}
}

// What `svoi up` opens in the browser it starts itself is for the user running it;
// what it prints is for anybody.
func TestUpOpensALinkForItsOwnUserAndPrintsOneForAnybody(t *testing.T) {
	e := newUIDEnv(t)
	ln, err := e.srv.Listen("127.0.0.1:0", false)
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	codeOf := func(u string) string { return u[strings.LastIndex(u, "?t=")+3:] }
	printed, local := codeOf(e.srv.URL()), codeOf(e.srv.LocalURL())
	e.srv.sess.mu.Lock()
	pc, lc := e.srv.sess.codes[hashOf(printed)], e.srv.sess.codes[hashOf(local)]
	e.srv.sess.mu.Unlock()
	if pc.uid != anyUser {
		t.Errorf("the printed link is tied to user %d", pc.uid)
	}
	if want := os.Getuid(); lc.uid != want {
		t.Errorf("the link for the browser is tied to user %d, want %d (the one running svoi)", lc.uid, want)
	}
}
