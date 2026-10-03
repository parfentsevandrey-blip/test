package api_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

// mintCode does what `svoi url` does: ask the node for a single-use sign-in code.
func (e *env) mintCode() string {
	e.t.Helper()
	var out struct {
		Code, URL  string
		ExpiresIn  int
		SingleUse  bool
		SessionTtl int
	}
	if code := e.call("POST", "/api/login/code", "{}", &out); code != 200 || len(out.Code) != 48 {
		e.t.Fatalf("login code: %d %+v", code, out)
	}
	return out.Code
}

// redeem opens the link a person would open: GET /?t=<code>, without following
// the redirect, and reports what the server did.
func (e *env) redeem(code string) (status int, session *http.Cookie, location string) {
	e.t.Helper()
	noRedirect := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := noRedirect.Get(e.srv.URL + "/?t=" + code)
	if err != nil {
		e.t.Fatal(err)
	}
	resp.Body.Close()
	for _, c := range resp.Cookies() {
		if c.Name == "svoi_session" && c.MaxAge >= 0 && c.Value != "" {
			session = c
		}
	}
	return resp.StatusCode, session, resp.Header.Get("Location")
}

func (e *env) asBrowser(c *http.Cookie) func(*http.Request) {
	return func(r *http.Request) {
		r.AddCookie(c)
		if r.Method != "GET" {
			r.Header.Set("X-Svoi", "1")
		}
	}
}

// A sign-in link works exactly once; what the browser ends up holding is a
// random session, never the code and never the master token.
func TestLoginCodeIsSingleUseAndBecomesASession(t *testing.T) {
	e := newEnv(t)
	code := e.mintCode()
	status, session, _ := e.redeem(code)
	if status != 302 || session == nil {
		t.Fatalf("first use: %d %+v", status, session)
	}
	if session.Value == code || session.Value == e.tok || len(session.Value) != 64 {
		t.Fatalf("the cookie must be a fresh random session id, got %q", session.Value)
	}
	if session.MaxAge < 7*24*3600 || session.MaxAge > 30*24*3600 {
		t.Fatalf("session lifetime %d s looks wrong", session.MaxAge)
	}
	if status, again, _ := e.redeem(code); status == 302 || again != nil {
		t.Fatalf("a login code worked twice: %d %+v", status, again)
	}
	if resp, _ := e.req("GET", "/api/state", "", e.asBrowser(session)); resp.StatusCode != 200 {
		t.Fatalf("the session does not work: %d", resp.StatusCode)
	}
	// Garbage of every shape is just refused.
	for _, bad := range []string{"", "0", strings.Repeat("a", 47), strings.Repeat("a", 49), strings.Repeat("z", 48), strings.Repeat("0", 48), e.tok} {
		if status, c, _ := e.redeem(bad); status == 302 || c != nil {
			t.Errorf("code %q was accepted", bad)
		}
	}
	// Two links are two independent sessions.
	_, s2, _ := e.redeem(e.mintCode())
	if s2 == nil || s2.Value == session.Value {
		t.Fatal("second sign-in did not get its own session")
	}
}

// The master token is for the command line. It must not show up in anything the
// browser can see or that is stored where a copied profile or file could leak it.
func TestMasterTokenStaysOutOfTheBrowserAndOffTheDisk(t *testing.T) {
	dir := t.TempDir()
	a, err := app.Open(app.Options{Dir: dir, DeviceName: "keys-box", Owner: "tester"})
	if err != nil {
		t.Fatal(err)
	}
	defer a.Close()
	ts := httptest.NewServer(api.New(a, nil).Handler())
	defer ts.Close()
	e := &env{t: t, app: a, srv: ts, tok: a.Token()}

	code := e.mintCode()
	noRedirect := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := noRedirect.Get(ts.URL + "/?t=" + code)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	all := resp.Header.Get("Set-Cookie") + resp.Header.Get("Location")
	if strings.Contains(all, e.tok) {
		t.Fatal("the master token appears in the login response")
	}
	var id string
	for _, c := range resp.Cookies() {
		id = c.Value
	}
	raw, err := os.ReadFile(filepath.Join(dir, "ui.sessions"))
	if err != nil {
		t.Fatalf("sessions are not persisted: %v", err)
	}
	if strings.Contains(string(raw), id) || strings.Contains(string(raw), e.tok) || strings.Contains(string(raw), code) {
		t.Fatal("ui.sessions holds a secret in the clear")
	}
	if runtimeHasUnixPerms() {
		if fi, _ := os.Stat(filepath.Join(dir, "ui.sessions")); fi.Mode().Perm()&0o077 != 0 {
			t.Fatalf("ui.sessions is accessible to others: %v", fi.Mode())
		}
	}
	// The state a signed-in browser reads never carries it either.
	_, body := e.req("GET", "/api/state", "", func(r *http.Request) {
		for _, c := range resp.Cookies() {
			r.AddCookie(c)
		}
	})
	if strings.Contains(string(body), e.tok) {
		t.Fatal("the master token appears in /api/state")
	}
}

func runtimeHasUnixPerms() bool { return os.PathSeparator == '/' }

// Only the command line (master token) can mint sign-in links; a browser that is
// already signed in cannot hand itself a way to outlive its session.
func TestOnlyTheCommandLineMintsLoginLinks(t *testing.T) {
	e := newEnv(t)
	if resp, _ := e.req("POST", "/api/login/code", "{}", nil); resp.StatusCode != 401 {
		t.Fatalf("anonymous: %d", resp.StatusCode)
	}
	_, session, _ := e.redeem(e.mintCode())
	if resp, _ := e.req("POST", "/api/login/code", "{}", e.asBrowser(session)); resp.StatusCode != 403 {
		t.Fatalf("a browser session minted a login link: %d", resp.StatusCode)
	}
	var out struct {
		Code, URL  string
		ExpiresIn  int
		SingleUse  bool
		SessionTtl int
	}
	if code := e.call("POST", "/api/login/code", "{}", &out); code != 200 {
		t.Fatalf("bearer: %d", code)
	}
	if out.ExpiresIn != 600 || !out.SingleUse || !strings.HasSuffix(out.URL, "/?t="+out.Code) || !strings.HasPrefix(out.URL, "http://127.0.0.1:") {
		t.Fatalf("login link: %+v", out)
	}
}

// The command line asks the node to prove it knows the token before sending it.
func TestHandshakeProvesKnowledgeOfTheToken(t *testing.T) {
	e := newEnv(t)
	nonce := "0123456789abcdef0123456789abcdef"
	var out struct{ Proof string }
	resp, body := e.req("GET", "/api/handshake?n="+nonce, "", nil) // no credentials needed
	if resp.StatusCode != 200 {
		t.Fatalf("handshake: %d %s", resp.StatusCode, body)
	}
	if err := json.Unmarshal(body, &out); err != nil {
		t.Fatal(err)
	}
	if out.Proof != api.HandshakeProof(e.tok, nonce) {
		t.Fatal("the proof does not match")
	}
	if out.Proof == e.tok || strings.Contains(string(body), e.tok) {
		t.Fatal("the handshake leaks the token")
	}
	if api.HandshakeProof(e.tok, nonce) == api.HandshakeProof(e.tok, nonce+"0") ||
		api.HandshakeProof(e.tok, nonce) == api.HandshakeProof("another-token", nonce) {
		t.Fatal("the proof does not depend on both the nonce and the token")
	}
	for _, bad := range []string{"", "short", strings.Repeat("a", 129)} {
		if resp, _ := e.req("GET", "/api/handshake?n="+bad, "", nil); resp.StatusCode != 400 {
			t.Errorf("nonce %q: %d", bad, resp.StatusCode)
		}
	}
	// Still behind the Host check (DNS rebinding).
	if resp, _ := e.req("GET", "/api/handshake?n="+nonce, "", func(r *http.Request) { r.Host = "attacker.example.com" }); resp.StatusCode != 421 {
		t.Fatalf("foreign Host: %d", resp.StatusCode)
	}
}

func TestLogout(t *testing.T) {
	e := newEnv(t)
	_, one, _ := e.redeem(e.mintCode())
	_, two, _ := e.redeem(e.mintCode())
	spare := e.mintCode()

	// One browser signs itself out.
	resp, _ := e.req("POST", "/api/logout", "{}", e.asBrowser(one))
	if resp.StatusCode != 200 {
		t.Fatalf("logout: %d", resp.StatusCode)
	}
	cleared := false
	for _, c := range resp.Cookies() {
		if c.Name == "svoi_session" && c.MaxAge < 0 {
			cleared = true
		}
	}
	if !cleared {
		t.Fatal("the cookie was not cleared")
	}
	if resp, _ := e.req("GET", "/api/state", "", e.asBrowser(one)); resp.StatusCode != 401 {
		t.Fatalf("a signed-out session still works: %d", resp.StatusCode)
	}
	if resp, _ := e.req("GET", "/api/state", "", e.asBrowser(two)); resp.StatusCode != 200 {
		t.Fatalf("the other session was affected: %d", resp.StatusCode)
	}
	// Signing everyone out is for the command line.
	if resp, _ := e.req("POST", "/api/logout?all=1", "{}", e.asBrowser(two)); resp.StatusCode != 403 {
		t.Fatalf("a browser signed everyone out: %d", resp.StatusCode)
	}
	if code := e.call("POST", "/api/logout?all=1", "{}", nil); code != 200 {
		t.Fatalf("logout all: %d", code)
	}
	if resp, _ := e.req("GET", "/api/state", "", e.asBrowser(two)); resp.StatusCode != 401 {
		t.Fatalf("session survived sign-out-everywhere: %d", resp.StatusCode)
	}
	if status, c, _ := e.redeem(spare); status == 302 || c != nil {
		t.Fatal("an unused login link survived sign-out-everywhere")
	}
	// The command line itself is unaffected, and can sign a browser in again.
	if _, c, _ := e.redeem(e.mintCode()); c == nil {
		t.Fatal("cannot sign in again")
	}
}

// Sessions live in the data directory, so restarting the node does not sign the
// browser out.
func TestSessionsSurviveARestart(t *testing.T) {
	dir := t.TempDir()
	open := func() (*app.App, *httptest.Server) {
		a, err := app.Open(app.Options{Dir: dir, DeviceName: "keys-box", Owner: "tester"})
		if err != nil {
			t.Fatal(err)
		}
		return a, httptest.NewServer(api.New(a, nil).Handler())
	}
	a, ts := open()
	e := &env{t: t, app: a, srv: ts, tok: a.Token()}
	_, session, _ := e.redeem(e.mintCode())
	unused := e.mintCode()
	ts.Close()
	a.Close()

	a, ts = open()
	defer a.Close()
	defer ts.Close()
	e = &env{t: t, app: a, srv: ts, tok: a.Token()}
	if resp, _ := e.req("GET", "/api/state", "", e.asBrowser(session)); resp.StatusCode != 200 {
		t.Fatalf("the session did not survive a restart: %d", resp.StatusCode)
	}
	// Unused links are not kept across restarts (a restart prints a new one).
	if status, c, _ := e.redeem(unused); status == 302 || c != nil {
		t.Fatal("an unused login link survived a restart")
	}
}

// A login link with a doubled slash in its path must not turn the redirect that
// follows into one to another site ("//host" is a network-path reference).
func TestLoginRedirectStaysOnThisSite(t *testing.T) {
	e := newEnv(t)
	noRedirect := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := noRedirect.Get(e.srv.URL + "//evil.example/path?t=" + e.mintCode())
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	loc := resp.Header.Get("Location")
	if resp.StatusCode != 302 || strings.HasPrefix(loc, "//") || !strings.HasPrefix(loc, "/") {
		t.Fatalf("redirect: %d %q", resp.StatusCode, loc)
	}
}
