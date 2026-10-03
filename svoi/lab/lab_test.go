//go:build lab

// Real-NAT integration tests. They need root and iproute2/iptables, and build a
// miniature Internet out of network namespaces (see natlab.sh), then run real
// `svoi` processes inside it.
//
//	sudo go test -tags lab ./lab -v -count=1 -timeout 20m
package lab

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"testing"
	"time"
)

var (
	svoiBin string
	labSh   string
)

func TestMain(m *testing.M) {
	if os.Geteuid() != 0 {
		fmt.Println("lab tests need root; skipping")
		os.Exit(0)
	}
	if _, err := exec.LookPath("ip"); err != nil {
		fmt.Println("lab tests need iproute2; skipping")
		os.Exit(0)
	}
	dir, err := os.MkdirTemp("", "svoi-lab-bin")
	if err != nil {
		panic(err)
	}
	svoiBin = filepath.Join(dir, "svoi")
	build := exec.Command("go", "build", "-o", svoiBin, "../cmd/svoi")
	build.Stdout, build.Stderr = os.Stdout, os.Stderr
	if err := build.Run(); err != nil {
		panic(err)
	}
	labSh, _ = filepath.Abs("natlab.sh")
	code := m.Run()
	exec.Command(labSh, "down").Run()
	os.RemoveAll(dir)
	os.Exit(code)
}

type node struct {
	t    *testing.T
	ns   string
	name string
	dir  string
	cmd  *exec.Cmd
}

func newNode(t *testing.T, ns, name string) *node {
	dir := filepath.Join(t.TempDir(), name)
	if err := os.MkdirAll(filepath.Join(dir, "home"), 0o755); err != nil {
		t.Fatal(err)
	}
	return &node{t: t, ns: ns, name: name, dir: dir}
}

func (n *node) command(args ...string) *exec.Cmd {
	full := []string{"netns", "exec", n.ns, "env",
		"SVOI_DIR=" + n.dir, "HOME=" + filepath.Join(n.dir, "home"),
		"QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING=true", svoiBin}
	return exec.Command("ip", append(full, args...)...)
}

// run executes a one-shot svoi command inside the namespace.
func (n *node) run(args ...string) string {
	n.t.Helper()
	cmd := n.command(args...)
	var out bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &out
	done := make(chan error, 1)
	if err := cmd.Start(); err != nil {
		n.t.Fatal(err)
	}
	go func() { done <- cmd.Wait() }()
	select {
	case err := <-done:
		if err != nil {
			n.t.Fatalf("[%s] svoi %s failed: %v\n%s", n.name, strings.Join(args, " "), err, out.String())
		}
	case <-time.After(90 * time.Second):
		cmd.Process.Kill()
		n.t.Fatalf("[%s] svoi %s timed out\n%s", n.name, strings.Join(args, " "), out.String())
	}
	return out.String()
}

// start runs the node in the background.
func (n *node) start() {
	n.t.Helper()
	cmd := n.command("up", "--no-browser", "--no-stun", "--debug")
	logf, err := os.Create(filepath.Join(n.dir, "node.log"))
	if err != nil {
		n.t.Fatal(err)
	}
	cmd.Stdout, cmd.Stderr = logf, logf
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := cmd.Start(); err != nil {
		n.t.Fatal(err)
	}
	n.cmd = cmd
	n.t.Cleanup(n.stop)
	waitFor(n.t, 15*time.Second, n.name+" API up", func() bool {
		_, err := os.Stat(filepath.Join(n.dir, "ui.addr"))
		return err == nil
	})
}

func (n *node) stop() {
	if n.cmd != nil && n.cmd.Process != nil {
		syscall.Kill(-n.cmd.Process.Pid, syscall.SIGTERM)
		done := make(chan struct{})
		go func() { n.cmd.Wait(); close(done) }()
		select {
		case <-done:
		case <-time.After(5 * time.Second):
			syscall.Kill(-n.cmd.Process.Pid, syscall.SIGKILL)
		}
		n.cmd = nil
	}
}

func (n *node) logTail(lines int) string {
	b, _ := os.ReadFile(filepath.Join(n.dir, "node.log"))
	parts := strings.Split(strings.TrimSpace(string(b)), "\n")
	if len(parts) > lines {
		parts = parts[len(parts)-lines:]
	}
	return strings.Join(parts, "\n")
}

type peerState struct {
	Name     string  `json:"name"`
	Online   bool    `json:"online"`
	Path     string  `json:"path"`
	RelayVia string  `json:"relayVia"`
	RTTms    float64 `json:"rttMs"`
}

type state struct {
	Self struct {
		Relayed struct{ Packets, Bytes uint64 } `json:"relayed"`
		NAT     struct{ Difficulty string }     `json:"nat"`
	} `json:"self"`
	Peers []peerState `json:"peers"`
}

func (n *node) state() (state, bool) {
	cmd := n.command("status", "--json")
	out, err := cmd.Output()
	if err != nil {
		return state{}, false
	}
	var st state
	if json.Unmarshal(out, &st) != nil {
		return state{}, false
	}
	return st, true
}

func (n *node) peer(name string) (peerState, bool) {
	st, ok := n.state()
	if !ok {
		return peerState{}, false
	}
	for _, p := range st.Peers {
		if p.Name == name {
			return p, true
		}
	}
	return peerState{}, false
}

func waitFor(t *testing.T, d time.Duration, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(d)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(500 * time.Millisecond)
	}
	t.Fatalf("timed out after %v waiting for: %s", d, what)
}

var codeRe = regexp.MustCompile(`SVOI1-[A-Z0-9-]+`)

func invite(t *testing.T, founder *node) string {
	out := founder.run("invite", "--ttl", "5m")
	code := codeRe.FindString(out)
	if code == "" {
		t.Fatalf("no invitation code in output:\n%s", out)
	}
	return code
}

func runScenario(t *testing.T, natA, natB, fw, wantPath string) {
	if out, err := exec.Command(labSh, "up", natA, natB, fw).CombinedOutput(); err != nil {
		t.Fatalf("natlab up: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	anchor := newNode(t, "svl-anchor", "anchor")
	a := newNode(t, "svl-A", "a")
	b := newNode(t, "svl-B", "b")
	dump := func() {
		for _, n := range []*node{anchor, a, b} {
			t.Logf("---- %s log ----\n%s", n.name, n.logTail(120))
		}
	}
	t.Cleanup(func() {
		if t.Failed() || os.Getenv("SVOI_LAB_DUMP") != "" {
			dump()
		}
	})

	anchor.run("init", "--mesh", "Lab", "--name", "anchor", "--owner", "lab")
	anchor.start()
	a.run("join", invite(t, anchor), "--name", "a", "--owner", "lab")
	a.start()
	b.run("join", invite(t, anchor), "--name", "b", "--owner", "lab")
	b.start()

	// Timeline of how a sees b, to understand slow or failed punching afterwards.
	stopTL := make(chan struct{})
	tlDone := make(chan struct{})
	go func() {
		defer close(tlDone)
		start := time.Now()
		last := ""
		for {
			select {
			case <-stopTL:
				return
			case <-time.After(time.Second):
			}
			p, ok := a.peer("b")
			cur := fmt.Sprintf("online=%v path=%s via=%q rtt=%.1f", p.Online, p.Path, p.RelayVia, p.RTTms)
			if !ok {
				cur = "(no status)"
			}
			if cur != last {
				t.Logf("[%5.1fs] a sees b: %s", time.Since(start).Seconds(), cur)
				last = cur
			}
		}
	}()
	t.Cleanup(func() { close(stopTL); <-tlDone })

	// Everyone sees everyone.
	waitFor(t, 60*time.Second, "a and b connected through whatever path works", func() bool {
		pa, ok1 := a.peer("b")
		pb, ok2 := b.peer("a")
		return ok1 && ok2 && pa.Online && pb.Online
	})
	// The path settles: direct for punchable NATs, relay otherwise.
	var pa peerState
	waitFor(t, 60*time.Second, "path a->b to become "+wantPath, func() bool {
		var ok bool
		pa, ok = a.peer("b")
		return ok && pa.Online && (pa.Path == wantPath || (wantPath == "direct" && pa.Path == "lan"))
	})
	t.Logf("a sees b: path=%s via=%q rtt=%.1fms", pa.Path, pa.RelayVia, pa.RTTms)
	if wantPath == "relay" && pa.RelayVia != "anchor" {
		t.Fatalf("relay should go through the anchor, got %q", pa.RelayVia)
	}

	// A file really crosses the network.
	data := make([]byte, 4<<20)
	rand.Read(data)
	src := filepath.Join(a.dir, "payload.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	relayedBefore := uint64(0)
	if st, ok := anchor.state(); ok {
		relayedBefore = st.Self.Relayed.Bytes
	}
	a.run("send", "b", src)
	dest := filepath.Join(b.dir, "home", "Downloads", "Svoi", "payload.bin")
	waitFor(t, 90*time.Second, "file to arrive at b", func() bool {
		st, err := os.Stat(dest)
		return err == nil && st.Size() == int64(len(data))
	})
	got, _ := os.ReadFile(dest)
	if sha256.Sum256(got) != sha256.Sum256(data) {
		t.Fatal("received file differs from the original")
	}
	if st, ok := anchor.state(); ok {
		relayed := st.Self.Relayed.Bytes - relayedBefore
		t.Logf("anchor relayed %d bytes during the transfer", relayed)
		if wantPath == "direct" && relayed > 256<<10 {
			t.Fatalf("the transfer should have bypassed the anchor but it relayed %d bytes", relayed)
		}
		if wantPath == "relay" && relayed < 3<<20 {
			t.Fatalf("expected the transfer to go through the anchor, it relayed only %d bytes", relayed)
		}
	}
}

func TestConeToCone(t *testing.T)      { runScenario(t, "cone", "cone", "home", "direct") }
func TestSymmetricToCone(t *testing.T) { runScenario(t, "symmetric", "cone", "home", "relay") }
func TestSymmetricToSymmetric(t *testing.T) {
	runScenario(t, "symmetric", "symmetric", "home", "relay")
}

// The permissive firewall accepts unsolicited packets, which makes Linux create
// conntrack entries that can force a port change when the host later sends to
// the same peer. See docs/ARCHITECTURE.md ("NAT traversal in the real world").
func TestConeToConePermissiveFirewall(t *testing.T) {
	runScenario(t, "cone", "cone", "permissive", "direct")
}
