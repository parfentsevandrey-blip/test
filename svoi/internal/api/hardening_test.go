package api_test

import (
	"bufio"
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"
)

// A browser that has a session is not exempt from the cross-site checks just because
// it also sends *some* Authorization header: only the master token (the command line)
// is.
func TestAnAuthorizationHeaderDoesNotSkipTheCSRFChecks(t *testing.T) {
	e := newEnv(t)
	_, session, _ := e.redeem(e.mintCode())
	post := func(mod func(*http.Request)) int {
		resp, _ := e.req("POST", "/api/peers/xxxxxxxx/alias", `{"alias":"x"}`, func(r *http.Request) {
			r.AddCookie(session)
			mod(r)
		})
		return resp.StatusCode
	}
	for name, mod := range map[string]func(*http.Request){
		"foreign Origin": func(r *http.Request) { r.Header.Set("Origin", "http://evil.example") },
		"foreign Origin and a Basic header": func(r *http.Request) {
			r.Header.Set("Origin", "http://evil.example")
			r.Header.Set("Authorization", "Basic Zm9vOmJhcg==")
		},
		"foreign Origin and a wrong Bearer token": func(r *http.Request) {
			r.Header.Set("Origin", "http://evil.example")
			r.Header.Set("Authorization", "Bearer nope")
		},
		"no X-Svoi and a Basic header": func(r *http.Request) { r.Header.Set("Authorization", "Basic Zm9vOmJhcg==") },
	} {
		if code := post(mod); code != 403 {
			t.Errorf("%s: HTTP %d, want 403", name, code)
		}
	}
	// The command line, with the real token, is not subject to them.
	if resp, _ := e.req("POST", "/api/logout", "{}", e.auth); resp.StatusCode != 200 {
		t.Errorf("the command line was refused: %d", resp.StatusCode)
	}
}

// A live event stream belongs to the session that opened it: signing out ends it, and
// nothing more is delivered on it.
func TestAnEventStreamEndsWhenTheSessionDoes(t *testing.T) {
	e := newEnv(t)
	_, session, _ := e.redeem(e.mintCode())
	req, _ := http.NewRequest("GET", e.srv.URL+"/api/events", nil)
	req.AddCookie(session)
	resp, err := http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != 200 {
		t.Fatalf("events: %v %v", err, resp)
	}
	defer resp.Body.Close()
	lines := make(chan string, 100)
	go func() {
		sc := bufio.NewScanner(resp.Body)
		for sc.Scan() {
			lines <- sc.Text()
		}
		close(lines)
	}()
	waitLine := func(substr string, d time.Duration) bool {
		deadline := time.After(d)
		for {
			select {
			case l, ok := <-lines:
				if !ok {
					return false
				}
				if strings.Contains(l, substr) {
					return true
				}
			case <-deadline:
				return false
			}
		}
	}
	if !waitLine("hello", 3*time.Second) {
		t.Fatal("no hello event")
	}
	// While signed in, events flow.
	e.app.Hub().Publish("notify", map[string]string{"title": "t", "text": "while signed in"})
	if !waitLine("while signed in", 3*time.Second) {
		t.Fatal("a live event was not delivered to a signed-in stream")
	}
	if code := e.call("POST", "/api/logout?all=1", "{}", nil); code != 200 {
		t.Fatalf("logout all: %d", code)
	}
	e.app.Hub().Publish("notify", map[string]string{"title": "secret", "text": "a message arrived after sign-out"})
	if waitLine("a message arrived after sign-out", 2*time.Second) {
		t.Fatal("the event stream of a signed-out session kept delivering live events")
	}
}

// The stream of the command line (master token) has no session to lose.
func TestTheCommandLineStreamIsNotTiedToASession(t *testing.T) {
	e := newEnv(t)
	req, _ := http.NewRequest("GET", e.srv.URL+"/api/events", nil)
	e.auth(req)
	resp, err := http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != 200 {
		t.Fatalf("events: %v %v", err, resp)
	}
	defer resp.Body.Close()
	sc := bufio.NewScanner(resp.Body)
	got := make(chan bool, 1)
	go func() {
		for sc.Scan() {
			if strings.Contains(sc.Text(), "from the command line") {
				got <- true
				return
			}
		}
	}()
	time.Sleep(200 * time.Millisecond)
	_ = e.call("POST", "/api/logout?all=1", "{}", nil)
	e.app.Hub().Publish("notify", map[string]string{"title": "t", "text": "from the command line"})
	select {
	case <-got:
	case <-time.After(3 * time.Second):
		t.Fatal("the command line's stream was cut by a sign-out of browsers")
	}
}

// Whatever path the sign-in link carries, the redirect that follows it stays on this
// site: browsers read "//host", "/\host" and these with a tab or line break (which
// they drop) in between as a different site.
func TestSignInRedirectStaysOnThisSite(t *testing.T) {
	e := newEnv(t)
	noRedirect := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	for _, path := range []string{
		"/", "//evil.example/x", "///evil.example", "/%5Cevil.example/x", "/%09/evil.example", "/%0a/evil.example",
		"/%0d%0a/evil.example", "/\\evil.example", "/.%2e/evil.example", "/%2f/evil.example", "/ok/page.html", "/%e2%80%ae/x",
	} {
		resp, err := noRedirect.Get(e.srv.URL + path + "?t=" + e.mintCode())
		if err != nil {
			// A path Go's client refuses to send is no problem for the server either.
			continue
		}
		resp.Body.Close()
		loc := resp.Header.Get("Location")
		if resp.StatusCode != 302 {
			t.Errorf("%q: status %d", path, resp.StatusCode)
			continue
		}
		u, err := url.Parse(loc)
		if err != nil || u.Host != "" || u.Scheme != "" || !strings.HasPrefix(loc, "/") || strings.HasPrefix(loc, "//") || strings.HasPrefix(loc, "/\\") {
			t.Errorf("%q: Location %q leaves the site (or is not a plain path)", path, loc)
		}
		for _, c := range loc {
			if c < 0x21 || c > 0x7e {
				t.Errorf("%q: Location %q has a control or non-ASCII character", path, loc)
				break
			}
		}
	}
	// An ordinary page keeps its path.
	resp, err := noRedirect.Get(e.srv.URL + "/ok/page.html?x=1&t=" + e.mintCode())
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if loc := resp.Header.Get("Location"); loc != "/ok/page.html?x=1" {
		t.Errorf("a plain path was changed: %q", loc)
	}
}
