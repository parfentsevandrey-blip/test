//go:build unix

package main

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/http/httputil"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
)

// These tests run the real binary: two separate processes on this machine
// (talking over loopback UDP like two devices would over the Internet), driven
// only through the command line and the HTTP interface, exactly as a person would.

var testBin string

func TestMain(m *testing.M) {
	dir, err := os.MkdirTemp("", "svoi-e2e")
	if err != nil {
		panic(err)
	}
	testBin = filepath.Join(dir, "svoi")
	build := exec.Command("go", "build", "-o", testBin, ".")
	build.Stdout, build.Stderr = os.Stderr, os.Stderr
	if err := build.Run(); err != nil {
		fmt.Fprintln(os.Stderr, "cannot build svoi:", err)
		os.Exit(1)
	}
	code := m.Run()
	os.RemoveAll(dir)
	os.Exit(code)
}

type proc struct {
	t    *testing.T
	name string
	dir  string
	home string
	cmd  *exec.Cmd
	log  *os.File
}

func newProc(t *testing.T, name string) *proc {
	root := t.TempDir()
	p := &proc{t: t, name: name, dir: filepath.Join(root, "data"), home: filepath.Join(root, "home")}
	for _, d := range []string{p.dir, p.home} {
		if err := os.MkdirAll(d, 0o700); err != nil {
			t.Fatal(err)
		}
	}
	return p
}

func (p *proc) command(args ...string) *exec.Cmd {
	c := exec.Command(testBin, args...)
	c.Env = append(os.Environ(), "SVOI_DIR="+p.dir, "HOME="+p.home, "XDG_CONFIG_HOME="+filepath.Join(p.home, ".config"))
	c.Env = append(c.Env, "DISPLAY=", "WAYLAND_DISPLAY=") // never try to open a browser
	return c
}

// run executes a one-shot command and returns its combined output.
func (p *proc) run(args ...string) (string, error) {
	c := p.command(args...)
	var out bytes.Buffer
	c.Stdout, c.Stderr = &out, &out
	if err := c.Start(); err != nil {
		return "", err
	}
	done := make(chan error, 1)
	go func() { done <- c.Wait() }()
	select {
	case err := <-done:
		return out.String(), err
	case <-time.After(60 * time.Second):
		c.Process.Kill()
		return out.String(), fmt.Errorf("timed out")
	}
}

func (p *proc) mustRun(args ...string) string {
	p.t.Helper()
	out, err := p.run(args...)
	if err != nil {
		p.t.Fatalf("[%s] svoi %s: %v\n%s", p.name, strings.Join(args, " "), err, out)
	}
	return out
}

// start launches `svoi up` in the background and waits for its interface.
func (p *proc) start(extra ...string) {
	p.t.Helper()
	args := append([]string{"up", "--no-browser", "--no-stun", "--no-portmap", "--loopback", "--ui", "127.0.0.1:0"}, extra...)
	c := p.command(args...)
	var err error
	if p.log, err = os.Create(filepath.Join(p.dir, "..", p.name+".log")); err != nil {
		p.t.Fatal(err)
	}
	c.Stdout, c.Stderr = p.log, p.log
	c.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := c.Start(); err != nil {
		p.t.Fatal(err)
	}
	p.cmd = c
	p.t.Cleanup(func() { p.stop() })
	waitFor(p.t, 15*time.Second, p.name+" web interface", func() bool {
		_, err := os.Stat(filepath.Join(p.dir, "ui.addr"))
		return err == nil
	})
}

// stop asks the process to quit and returns how it ended.
func (p *proc) stop() error {
	if p.cmd == nil {
		return nil
	}
	c := p.cmd
	p.cmd = nil
	_ = syscall.Kill(-c.Process.Pid, syscall.SIGINT)
	done := make(chan error, 1)
	go func() { done <- c.Wait() }()
	select {
	case err := <-done:
		return err
	case <-time.After(10 * time.Second):
		_ = syscall.Kill(-c.Process.Pid, syscall.SIGKILL)
		<-done
		return fmt.Errorf("did not stop within 10s")
	}
}

func (p *proc) logs() string {
	b, _ := os.ReadFile(filepath.Join(p.dir, "..", p.name+".log"))
	return string(b)
}

func (p *proc) apiBase() string {
	b, err := os.ReadFile(filepath.Join(p.dir, "ui.addr"))
	if err != nil {
		p.t.Fatal(err)
	}
	return "http://" + strings.TrimSpace(string(b))
}

func (p *proc) token() string {
	b, err := os.ReadFile(filepath.Join(p.dir, "ui.token"))
	if err != nil {
		p.t.Fatal(err)
	}
	return strings.TrimSpace(string(b))
}

type statusJSON struct {
	Configured bool `json:"configured"`
	Self       struct{ Name, IP4 string }
	Peers      []struct {
		Name   string
		Online bool
		Path   string
	}
}

func (p *proc) status() statusJSON {
	var st statusJSON
	out, err := p.run("status", "--json")
	if err != nil {
		return st
	}
	_ = json.Unmarshal([]byte(out), &st)
	return st
}

var loginLinkRe = regexp.MustCompile(`http://127\.0\.0\.1:\d+/\?t=([0-9a-f]+)`)

// open follows a sign-in link the way a browser does, without following the
// redirect, and returns the session cookie it was given (nil if refused).
func openLink(t *testing.T, link string) *http.Cookie {
	t.Helper()
	cl := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := cl.Get(link)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusFound {
		return nil
	}
	for _, c := range resp.Cookies() {
		if strings.HasPrefix(c.Name, "svoi_session") && c.Value != "" {
			return c
		}
	}
	return nil
}

func (p *proc) apiAs(cookie *http.Cookie, path string) int {
	p.t.Helper()
	req, _ := http.NewRequest("GET", p.apiBase()+path, nil)
	req.AddCookie(cookie)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		p.t.Fatal(err)
	}
	resp.Body.Close()
	return resp.StatusCode
}

// checkSignIn covers how a person gets into the web interface: with a link that
// works once, while the master token stays on the command line.
func (p *proc) checkSignIn(t *testing.T) {
	t.Helper()
	tok := p.token()
	log := p.logs()
	if strings.Contains(log, tok) {
		t.Fatal("the master token was written to the log")
	}
	// The link `svoi up` printed works once.
	m := loginLinkRe.FindStringSubmatch(log)
	if m == nil {
		t.Fatalf("no sign-in link in the banner:\n%s", log)
	}
	if m[1] == tok || len(m[1]) != 48 {
		t.Fatalf("the banner link carries %q, not a one-time code", m[1])
	}
	first := openLink(t, m[0])
	if first == nil {
		t.Fatal("the banner link did not sign in")
	}
	if openLink(t, m[0]) != nil {
		t.Fatal("the banner link worked twice")
	}
	if openLink(t, p.apiBase()+"/?t="+tok) != nil {
		t.Fatal("the master token worked as a sign-in link")
	}
	if first.Value == tok || p.apiAs(first, "/api/state") != 200 {
		t.Fatalf("the session is wrong: value=%q", first.Value)
	}
	// `svoi url` mints a new one each time.
	l1 := strings.TrimSpace(p.mustRun("url"))
	l2 := strings.TrimSpace(p.mustRun("url"))
	if l1 == l2 || strings.Contains(l1, tok) || !loginLinkRe.MatchString(l1) {
		t.Fatalf("svoi url: %q / %q", l1, l2)
	}
	second := openLink(t, l1)
	if second == nil || second.Value == first.Value {
		t.Fatal("svoi url did not sign in")
	}
	// The link `svoi open` hands to the browser it starts is tied to this user (Linux), and
	// works for this user, from this very process: a real loopback connection between two
	// processes, identified by the kernel's socket table.
	req, _ := http.NewRequest("POST", p.apiBase()+"/api/login/code", strings.NewReader(`{"local":true}`))
	req.Header.Set("Authorization", "Bearer "+tok)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	var minted struct {
		Code  string
		Bound bool
	}
	_ = json.NewDecoder(resp.Body).Decode(&minted)
	resp.Body.Close()
	if len(minted.Code) != 48 {
		t.Fatalf("local link: %d %+v", resp.StatusCode, minted)
	}
	if runtime.GOOS == "linux" && !minted.Bound {
		t.Log("the kernel's socket table could not be read here: the local link is not tied to a user")
	}
	if c := openLink(t, p.apiBase()+"/?t="+minted.Code); c == nil || p.apiAs(c, "/api/state") != 200 {
		t.Fatalf("the user a local link was made for could not use it (bound=%v)", minted.Bound)
	}
	// `svoi signout` ends every browser session and cancels unused links.
	p.mustRun("signout")
	if p.apiAs(first, "/api/state") != 401 || p.apiAs(second, "/api/state") != 401 {
		t.Fatal("sessions survived `svoi signout`")
	}
	if openLink(t, l2) != nil {
		t.Fatal("an unused link survived `svoi signout`")
	}
	if c := openLink(t, strings.TrimSpace(p.mustRun("url"))); c == nil || p.apiAs(c, "/api/state") != 200 {
		t.Fatal("cannot sign in again after signout")
	}
}

// A node that runs without a terminal (a service, a container) must not write a live
// sign-in link into its log: the journal keeps it long after the ten minutes, for
// whoever can read it. `svoi url` is how a person gets one.
func TestHeadlessUpKeepsTheSignInLinkOutOfTheLog(t *testing.T) {
	if testing.Short() {
		t.Skip("starts a real process")
	}
	p := newProc(t, "headless")
	p.mustRun("init", "--mesh", "Дом", "--name", "headless", "--owner", "tester")
	p.start() // its output is a file: not a terminal
	log := p.logs()
	if strings.Contains(log, "?t=") || regexp.MustCompile(`[0-9a-f]{48}`).MatchString(log) {
		t.Fatalf("a sign-in link or code is in the log:\n%s", log)
	}
	if !strings.Contains(log, "svoi url") {
		t.Fatalf("the log does not say how to get a link:\n%s", log)
	}
	link := strings.TrimSpace(p.mustRun("url"))
	if c := openLink(t, link); c == nil || p.apiAs(c, "/api/state") != 200 {
		t.Fatalf("`svoi url` did not give a working link: %q", link)
	}
	// `svoi open` prints a link that can be copied anywhere, and starts a browser (here there is none).
	out := p.mustRun("open")
	m := loginLinkRe.FindString(out)
	if m == "" {
		t.Fatalf("svoi open printed no link:\n%s", out)
	}
	if c := openLink(t, m); c == nil {
		t.Fatal("the link `svoi open` printed does not work")
	}
	// And the opposite, on request.
	q := newProc(t, "printing")
	q.mustRun("init", "--mesh", "Дом", "--name", "printing", "--owner", "tester")
	q.start("--print-link")
	if !loginLinkRe.MatchString(q.logs()) {
		t.Fatalf("--print-link printed no link:\n%s", q.logs())
	}
}

// If whatever answers on the recorded port cannot prove it knows the token (a
// stale ui.addr, a squatter), the command line must not hand the token over.
func TestCommandLineDoesNotSendTheTokenToAnImpostor(t *testing.T) {
	const secret = "5e6c7a1d9b0f4e2a8c3d5b7f9a1e3c5d7b9f1a3c5e7d9b1f"
	p := newProc(t, "victim")
	var mu sync.Mutex
	var seen []string
	imp := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		dump, _ := httputil.DumpRequest(r, true)
		mu.Lock()
		seen = append(seen, string(dump))
		mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"proof":"00"}`) // answers, but cannot know the token
	}))
	defer imp.Close()
	if err := os.WriteFile(filepath.Join(p.dir, "ui.addr"), []byte(strings.TrimPrefix(imp.URL, "http://")+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(p.dir, "ui.token"), []byte(secret+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	for _, args := range [][]string{{"status"}, {"url"}, {"signout"}, {"ping", "somebody"}} {
		out, err := p.run(args...)
		if err == nil || !strings.Contains(out, "not this svoi") {
			t.Errorf("svoi %s against an impostor: err=%v out=%q", strings.Join(args, " "), err, out)
		}
	}
	mu.Lock()
	defer mu.Unlock()
	if len(seen) == 0 {
		t.Fatal("the impostor was never contacted: the test checks nothing")
	}
	for _, r := range seen {
		if strings.Contains(r, secret) || strings.Contains(strings.ToLower(r), "authorization") {
			t.Fatalf("the token (or an Authorization header) reached the impostor:\n%s", r)
		}
	}
}

// Even something that really is the node (it proves the token) must not be able to send
// the command line, with its Authorization header, somewhere else with a redirect.
func TestCommandLineDoesNotFollowRedirects(t *testing.T) {
	const secret = "0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6071"
	p := newProc(t, "redirected")
	var mu sync.Mutex
	var stolen []string
	other := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		stolen = append(stolen, r.Header.Get("Authorization"))
		mu.Unlock()
	}))
	defer other.Close()
	node := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/handshake" {
			fmt.Fprintf(w, `{"proof":%q}`, api.HandshakeProof(secret, r.URL.Query().Get("n")))
			return
		}
		http.Redirect(w, r, other.URL+"/collect", http.StatusTemporaryRedirect)
	}))
	defer node.Close()
	if err := os.WriteFile(filepath.Join(p.dir, "ui.addr"), []byte(strings.TrimPrefix(node.URL, "http://")+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(p.dir, "ui.token"), []byte(secret+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	out, err := p.run("status")
	if err == nil {
		t.Fatalf("svoi status followed a redirect and succeeded: %q", out)
	}
	mu.Lock()
	defer mu.Unlock()
	if len(stolen) != 0 {
		t.Fatalf("the redirect target was contacted: %v", stolen)
	}
}

func waitFor(t *testing.T, d time.Duration, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(d)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(300 * time.Millisecond)
	}
	t.Fatalf("timed out after %v waiting for: %s", d, what)
}

var inviteRe = regexp.MustCompile(`SVOI1-[A-Z0-9-]+`)

func TestTwoProcessesEndToEnd(t *testing.T) {
	if testing.Short() {
		t.Skip("starts real processes")
	}
	a, b := newProc(t, "alpha"), newProc(t, "beta")
	dump := func() {
		t.Logf("---- alpha ----\n%s\n---- beta ----\n%s", a.logs(), b.logs())
	}
	t.Cleanup(func() {
		if t.Failed() {
			dump()
		}
	})

	// The first device creates the mesh and starts.
	a.mustRun("init", "--mesh", "Дом", "--name", "alpha", "--owner", "tester")
	a.start("--print-link") // (output is a file here, not a terminal: the link is only printed on request)
	st := a.status()
	if !st.Configured || st.Self.Name != "alpha" || st.Self.IP4 == "" {
		t.Fatalf("alpha after init: %+v", st)
	}

	// Its interface is protected and serves the web UI.
	base := a.apiBase()
	resp, err := http.Get(base + "/api/state")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 401 {
		t.Fatalf("API without a token answered %d", resp.StatusCode)
	}
	req, _ := http.NewRequest("GET", base+"/", nil)
	req.Header.Set("Authorization", "Bearer "+a.token())
	if resp, err = http.DefaultClient.Do(req); err != nil {
		t.Fatal(err)
	}
	page, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != 200 || !strings.Contains(resp.Header.Get("Content-Type"), "text/html") || !bytes.Contains(bytes.ToLower(page), []byte("<html")) {
		t.Fatalf("the web interface is not served: %d %q %.80q", resp.StatusCode, resp.Header.Get("Content-Type"), page)
	}

	a.checkSignIn(t)

	// A second device joins with an invitation code.
	code := inviteRe.FindString(a.mustRun("invite", "--ttl", "5m", "--owner", "tester"))
	if code == "" {
		t.Fatal("no invitation code printed")
	}
	b.mustRun("join", code, "--name", "beta")
	b.start()
	waitFor(t, 40*time.Second, "alpha and beta see each other", func() bool {
		sa, sb := a.status(), b.status()
		return len(sa.Peers) == 1 && sa.Peers[0].Online && sa.Peers[0].Name == "beta" &&
			len(sb.Peers) == 1 && sb.Peers[0].Online && sb.Peers[0].Name == "alpha"
	})
	// The invitation was one-time: a third device cannot reuse it.
	c := newProc(t, "gamma")
	if out, err := c.run("join", code, "--name", "gamma"); err == nil {
		t.Fatalf("a used invitation was accepted twice:\n%s", out)
	}

	// Round trip and a file.
	out := a.mustRun("ping", "beta")
	if !strings.Contains(out, "ms") {
		t.Fatalf("ping output: %q", out)
	}
	data := make([]byte, 3<<20+123)
	_, _ = rand.Read(data)
	src := filepath.Join(a.home, "payload.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	a.mustRun("send", "beta", src)
	dest := filepath.Join(b.home, "Downloads", "Svoi", "payload.bin")
	waitFor(t, 40*time.Second, "the file to arrive at beta", func() bool {
		st, err := os.Stat(dest)
		return err == nil && st.Size() == int64(len(data))
	})
	got, _ := os.ReadFile(dest)
	if sha256.Sum256(got) != sha256.Sum256(data) {
		t.Fatal("the received file differs")
	}

	// beta restarts and finds alpha again by itself (no coordinator involved).
	if err := b.stop(); err != nil {
		t.Fatalf("beta did not stop cleanly: %v", err)
	}
	waitFor(t, 30*time.Second, "alpha to notice beta is gone", func() bool {
		s := a.status()
		return len(s.Peers) == 1 && !s.Peers[0].Online
	})
	b.start()
	waitFor(t, 40*time.Second, "beta to reconnect after a restart", func() bool {
		s := a.status()
		return len(s.Peers) == 1 && s.Peers[0].Online
	})

	// Clean shutdown on Ctrl+C: exit code 0, the interface files are removed.
	if err := a.stop(); err != nil {
		t.Fatalf("alpha did not stop cleanly: %v", err)
	}
	if _, err := os.Stat(filepath.Join(a.dir, "ui.addr")); err == nil {
		t.Fatal("ui.addr left behind after shutdown")
	}
	// The CLI says so plainly when nothing is running.
	if out, err := a.run("status"); err == nil || !strings.Contains(out, "not running") {
		t.Fatalf("status with no node: err=%v out=%q", err, out)
	}
}

func TestLeaveKeepsDeviceKey(t *testing.T) {
	if testing.Short() {
		t.Skip("starts real processes")
	}
	a := newProc(t, "solo")
	a.mustRun("init", "--mesh", "Temp", "--name", "solo")
	key, err := os.ReadFile(filepath.Join(a.dir, "device.key"))
	if err != nil {
		t.Fatal(err)
	}
	if fi, _ := os.Stat(filepath.Join(a.dir, "device.key")); fi.Mode().Perm() != 0o600 {
		t.Fatalf("device.key has mode %v", fi.Mode().Perm())
	}
	a.start()
	if _, err := a.run("leave", "--yes"); err != nil {
		t.Fatal(err)
	}
	if st := a.status(); st.Configured {
		t.Fatal("still configured after leave")
	}
	after, _ := os.ReadFile(filepath.Join(a.dir, "device.key"))
	if !bytes.Equal(key, after) {
		t.Fatal("leaving the mesh changed the device key")
	}
}
