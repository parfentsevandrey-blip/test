package main

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"testing"
	"time"
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
	args := append([]string{"up", "--no-browser", "--no-stun", "--loopback", "--ui", "127.0.0.1:0"}, extra...)
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
	a.start()
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

	// A second device joins with an invitation code.
	code := inviteRe.FindString(a.mustRun("invite", "--ttl", "5m"))
	if code == "" {
		t.Fatal("no invitation code printed")
	}
	b.mustRun("join", code, "--name", "beta", "--owner", "tester")
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
