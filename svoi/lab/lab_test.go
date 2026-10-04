//go:build lab

// Real-NAT integration tests. They need root and iproute2/iptables, and build a
// miniature Internet out of network namespaces (see natlab.sh), then run real
// `themesh` processes inside it.
//
//	sudo go test -tags lab ./lab -v -count=1 -timeout 20m
package lab

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
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
	themeshBin string
	igdBin     string // the stand-in for a router's UPnP service (lab/fakeigd)
	labSh      string
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
	dir, err := os.MkdirTemp("", "themesh-lab-bin")
	if err != nil {
		panic(err)
	}
	themeshBin = filepath.Join(dir, "themesh")
	build := exec.Command("go", "build", "-o", themeshBin, "../cmd/themesh")
	build.Stdout, build.Stderr = os.Stdout, os.Stderr
	if err := build.Run(); err != nil {
		panic(err)
	}
	igdBin = filepath.Join(dir, "fakeigd")
	buildIGD := exec.Command("go", "build", "-o", igdBin, "./fakeigd")
	buildIGD.Stdout, buildIGD.Stderr = os.Stdout, os.Stderr
	if err := buildIGD.Run(); err != nil {
		panic(err)
	}
	labSh, _ = filepath.Abs("natlab.sh")
	code := m.Run()
	exec.Command(labSh, "down").Run()
	os.RemoveAll(dir)
	os.Exit(code)
}

type node struct {
	t       *testing.T
	ns      string
	name    string
	dir     string
	cmd     *exec.Cmd
	portmap bool // let the node ask its router to forward its port (off unless a test is about that)

	env       []string // more environment (NAME=value) for every command of this node
	addrsFile string   // the phone's address file (see asAndroidPhone)
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
		"THEMESH_DIR=" + n.dir, "HOME=" + filepath.Join(n.dir, "home"),
		"QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING=true"}
	full = append(full, n.env...)
	full = append(full, themeshBin)
	return exec.Command("ip", append(full, args...)...)
}

// asAndroidPhone makes the node behave like the program on a phone since Android 11: the system will not list
// its network interfaces, so what it knows about its networks is only the file the app writes (writeAddrs).
func (n *node) asAndroidPhone() {
	n.addrsFile = filepath.Join(n.dir, "local-addrs.txt")
	n.env = append(n.env, "THEMESH_HIDE_INTERFACES=1", "THEMESH_LOCAL_ADDRS_FILE="+n.addrsFile)
}

// writeAddrs is what the phone's app does whenever the network changes: it writes down the address the phone
// has now, with the length of its network prefix.
func (n *node) writeAddrs(cidr string) {
	n.t.Helper()
	if err := os.WriteFile(n.addrsFile, []byte(cidr+"\n"), 0o600); err != nil {
		n.t.Fatal(err)
	}
}

// run executes a one-shot themesh command inside the namespace.
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
			n.t.Fatalf("[%s] themesh %s failed: %v\n%s", n.name, strings.Join(args, " "), err, out.String())
		}
	case <-time.After(90 * time.Second):
		cmd.Process.Kill()
		n.t.Fatalf("[%s] themesh %s timed out\n%s", n.name, strings.Join(args, " "), out.String())
	}
	return out.String()
}

// start runs the node in the background.
func (n *node) start(extra ...string) {
	n.t.Helper()
	args := []string{"up", "--no-browser", "--no-stun", "--debug"}
	if !n.portmap {
		args = append(args, "--no-portmap")
	}
	cmd := n.command(append(args, extra...)...)
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
	IP4      string  `json:"ip4"`
	IP6      string  `json:"ip6"`
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
		PortMap *struct {
			State, Protocol, External string
		} `json:"portmap"`
		Endpoints []struct{ Addr, Kind string } `json:"endpoints"`
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

var codeRe = regexp.MustCompile(`MESH1-[A-Z0-9-]+`)

func invite(t *testing.T, founder *node) string {
	out := founder.run("invite", "--ttl", "5m", "--owner", "lab") // the inviter says whose device it is
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
		if t.Failed() || os.Getenv("THEMESH_LAB_DUMP") != "" {
			dump()
		}
	})

	anchor.run("init", "--mesh", "Lab", "--name", "anchor", "--owner", "lab")
	anchor.start()
	a.run("join", invite(t, anchor), "--name", "a")
	a.start()
	b.run("join", invite(t, anchor), "--name", "b")
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
	// The path settles: direct for punchable NATs, relay otherwise. "any" means
	// the scenario only promises that the devices stay connected; a direct path
	// is welcome but not required, so we give punching a moment and report the result.
	var pa peerState
	if wantPath == "any" {
		time.Sleep(10 * time.Second)
		pa, _ = a.peer("b")
		if !pa.Online {
			t.Fatalf("a lost b: %+v", pa)
		}
	} else {
		waitFor(t, 60*time.Second, "path a->b to become "+wantPath, func() bool {
			var ok bool
			pa, ok = a.peer("b")
			return ok && pa.Online && (pa.Path == wantPath || (wantPath == "direct" && pa.Path == "lan"))
		})
	}
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
	dest := filepath.Join(b.dir, "home", "Downloads", "The Mesh", "payload.bin")
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
// the same peer, so hole punching may fail here (it is not guaranteed either way).
// What must hold is that the devices stay connected through the anchor and a
// file still arrives. See docs/ARCHITECTURE.md ("NAT traversal in the real world").
func TestConeToConePermissiveFirewall(t *testing.T) {
	runScenario(t, "cone", "cone", "permissive", "any")
}

// The overlay: ordinary programs (curl, here) reach another device by its overlay
// address or by <name>.mesh, across two NATs, without knowing themesh exists.
func TestOverlayTUN(t *testing.T) {
	if out, err := exec.Command(labSh, "up", "cone", "cone", "home").CombinedOutput(); err != nil {
		t.Fatalf("natlab up: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	// Each namespace gets its own /etc/hosts so the three nodes do not fight over one file.
	for _, ns := range []string{"svl-A", "svl-B"} {
		dir := filepath.Join("/etc/netns", ns)
		if err := os.MkdirAll(dir, 0o755); err != nil {
			t.Fatal(err)
		}
		hosts, _ := os.ReadFile("/etc/hosts")
		if err := os.WriteFile(filepath.Join(dir, "hosts"), hosts, 0o644); err != nil {
			t.Fatal(err)
		}
		ns := ns
		t.Cleanup(func() { os.RemoveAll(filepath.Join("/etc/netns", ns)) })
	}

	anchor := newNode(t, "svl-anchor", "anchor")
	a := newNode(t, "svl-A", "a")
	b := newNode(t, "svl-B", "b")
	t.Cleanup(func() {
		if t.Failed() {
			for _, n := range []*node{anchor, a, b} {
				t.Logf("---- %s log ----\n%s", n.name, n.logTail(40))
			}
		}
	})
	anchor.run("init", "--mesh", "Lab", "--name", "anchor", "--owner", "lab")
	anchor.start()
	a.run("join", invite(t, anchor), "--name", "a")
	a.start("--tun")
	b.run("join", invite(t, anchor), "--name", "b")
	b.start("--tun")

	var pb peerState
	waitFor(t, 60*time.Second, "a sees b online with a direct path", func() bool {
		var ok bool
		pb, ok = a.peer("b")
		return ok && pb.Online && (pb.Path == "direct" || pb.Path == "lan")
	})
	if pb.IP4 == "" || pb.IP6 == "" {
		t.Fatalf("no overlay addresses: %+v", pb)
	}
	t.Logf("b's overlay addresses: %s %s", pb.IP4, pb.IP6)

	// A web server on b, listening on all addresses (including the overlay).
	www := filepath.Join(b.dir, "www")
	os.MkdirAll(www, 0o755)
	os.WriteFile(filepath.Join(www, "hello.txt"), []byte("hello over the overlay\n"), 0o644)
	big := make([]byte, 3<<20)
	rand.Read(big)
	os.WriteFile(filepath.Join(www, "big.bin"), big, 0o644)
	_, err6 := os.Stat("/proc/net/if_inet6")
	hasV6 := err6 == nil
	bind := "::"
	if !hasV6 {
		bind = "0.0.0.0"
	}
	srv := exec.Command("ip", "netns", "exec", "svl-B", "python3", "-m", "http.server", "8088", "--bind", bind, "--directory", www)
	srv.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := srv.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { syscall.Kill(-srv.Process.Pid, syscall.SIGKILL); srv.Wait() })
	time.Sleep(time.Second)

	curl := func(url string, args ...string) []byte {
		t.Helper()
		full := append([]string{"netns", "exec", "svl-A", "curl", "-sS", "--max-time", "30"}, args...)
		full = append(full, url)
		out, err := exec.Command("ip", full...).CombinedOutput()
		if err != nil {
			t.Fatalf("curl %s: %v\n%s", url, err, out)
		}
		return out
	}
	// By overlay IPv4.
	waitFor(t, 20*time.Second, "overlay IPv4 reachable", func() bool {
		out, err := exec.Command("ip", "netns", "exec", "svl-A", "curl", "-sS", "--max-time", "5",
			fmt.Sprintf("http://%s:8088/hello.txt", pb.IP4)).Output()
		return err == nil && string(out) == "hello over the overlay\n"
	})
	// By overlay IPv6 (when the kernel has IPv6 at all).
	if hasV6 {
		if got := string(curl(fmt.Sprintf("http://[%s]:8088/hello.txt", pb.IP6), "-g")); got != "hello over the overlay\n" {
			t.Fatalf("IPv6 overlay returned %q", got)
		}
	} else {
		t.Log("IPv6 is not available in this kernel: skipping the IPv6 overlay check")
	}
	// By name, resolved through the managed /etc/hosts block.
	defer func() {
		if t.Failed() {
			for _, ns := range []string{"svl-A", "svl-B"} {
				b, _ := os.ReadFile(filepath.Join("/etc/netns", ns, "hosts"))
				t.Logf("---- /etc/netns/%s/hosts ----\n%s", ns, b)
			}
		}
	}()
	waitFor(t, 15*time.Second, "b.mesh resolves", func() bool {
		out, err := exec.Command("ip", "netns", "exec", "svl-A", "getent", "ahostsv4", "b.mesh").Output()
		return err == nil && strings.Contains(string(out), pb.IP4)
	})
	if got := string(curl("http://b.mesh:8088/hello.txt")); got != "hello over the overlay\n" {
		t.Fatalf("by-name request returned %q", got)
	}
	// A multi-megabyte transfer through the tunnel arrives intact.
	start := time.Now()
	gotBig := curl(fmt.Sprintf("http://%s:8088/big.bin", pb.IP4))
	if sha256.Sum256(gotBig) != sha256.Sum256(big) {
		t.Fatalf("3 MB download through the overlay is corrupt (%d bytes)", len(gotBig))
	}
	t.Logf("3 MB through the overlay in %v", time.Since(start).Round(time.Millisecond))
}

// A home network with no way out: the devices are on one switch, nothing routes
// anywhere, there is no anchor and no STUN. They must still find and use each
// other, and after the whole network is re-addressed (a new router, a new DHCP
// range) nobody knows anybody's address anymore - only the LAN beacons can bring
// them back together.
func TestLANOnlyAndReaddressing(t *testing.T) {
	if out, err := exec.Command(labSh, "lan", "7").CombinedOutput(); err != nil {
		t.Fatalf("natlab lan: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	l1 := newNode(t, "svl-L1", "l1")
	l2 := newNode(t, "svl-L2", "l2")
	t.Cleanup(func() {
		if t.Failed() {
			for _, n := range []*node{l1, l2} {
				t.Logf("---- %s log ----\n%s", n.name, n.logTail(80))
			}
		}
	})
	l1.run("init", "--mesh", "Lan", "--name", "l1", "--owner", "lab")
	l1.start()
	l2.run("join", invite(t, l1), "--name", "l2")
	l2.start()

	together := func(what string) {
		waitFor(t, 60*time.Second, what, func() bool {
			p1, ok1 := l1.peer("l2")
			p2, ok2 := l2.peer("l1")
			return ok1 && ok2 && p1.Online && p2.Online && (p1.Path == "lan" || p1.Path == "direct")
		})
		p, _ := l1.peer("l2")
		t.Logf("%s: l1 sees l2 via %s, %.1f ms", what, p.Path, p.RTTms)
	}
	together("devices on a closed LAN find each other")

	send := func(name string) {
		data := make([]byte, 2<<20)
		rand.Read(data)
		src := filepath.Join(l1.dir, name)
		if err := os.WriteFile(src, data, 0o644); err != nil {
			t.Fatal(err)
		}
		l1.run("send", "l2", src)
		dest := filepath.Join(l2.dir, "home", "Downloads", "The Mesh", name)
		waitFor(t, 60*time.Second, "file "+name+" to arrive", func() bool {
			st, err := os.Stat(dest)
			return err == nil && st.Size() == int64(len(data))
		})
	}
	send("before.bin")

	// The whole network moves to 192.168.8.0/24 while both devices are off.
	l1.stop()
	l2.stop()
	for _, c := range [][]string{
		{"readdr", "svl-L1", "192.168.8.101/24"},
		{"readdr", "svl-L2", "192.168.8.102/24"},
	} {
		if out, err := exec.Command(labSh, c...).CombinedOutput(); err != nil {
			t.Fatalf("natlab %v: %v\n%s", c, err, out)
		}
	}
	l1.start()
	l2.start()
	together("devices find each other after the network was re-addressed")
	send("after.bin")

	// Control: the same move with LAN discovery switched off must NOT reconnect
	// (nothing else knows the new addresses), which shows the beacons did the work above.
	setLAN := func(n *node, on bool) {
		path := filepath.Join(n.dir, "config.json")
		raw, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		var cfg map[string]any
		if err := json.Unmarshal(raw, &cfg); err != nil {
			t.Fatal(err)
		}
		cfg["lan"] = on
		raw, _ = json.Marshal(cfg)
		if err := os.WriteFile(path, raw, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	l1.stop()
	l2.stop()
	setLAN(l1, false)
	setLAN(l2, false)
	exec.Command(labSh, "readdr", "svl-L1", "192.168.9.101/24").Run()
	exec.Command(labSh, "readdr", "svl-L2", "192.168.9.102/24").Run()
	l1.start()
	l2.start()
	time.Sleep(20 * time.Second)
	if p, ok := l1.peer("l2"); ok && p.Online {
		t.Fatalf("devices reconnected on a re-addressed network without LAN discovery (path %s): the control is meaningless", p.Path)
	}
	l1.stop()
	l2.stop()
	setLAN(l1, true)
	setLAN(l2, true)
	l1.start()
	l2.start()
	together("LAN discovery switched back on")
}

// The same home network, but one of the devices is a phone with Android 11 or later, whose program cannot ask the
// system for its network interfaces and is told its address by the app instead. This is the case in which the
// phone and the Mac did not find each other on their own: the phone neither announced itself nor believed the
// Mac's announcements. After the whole network is re-addressed (nobody knows anybody's address) each direction
// is tried alone: the other side's beacons are dropped by the firewall of the namespace.
func TestLANDiscoveryOfAPhoneThatCannotListItsInterfaces(t *testing.T) {
	if out, err := exec.Command(labSh, "lan", "7").CombinedOutput(); err != nil {
		t.Fatalf("natlab lan: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	mac := newNode(t, "svl-L1", "mac")
	phone := newNode(t, "svl-L2", "phone")
	phone.asAndroidPhone()
	phone.writeAddrs("192.168.7.2/24")
	t.Cleanup(func() {
		if t.Failed() {
			for _, n := range []*node{mac, phone} {
				t.Logf("---- %s log ----\n%s", n.name, n.logTail(80))
			}
		}
	})
	mac.run("init", "--mesh", "Lan", "--name", "mac", "--owner", "lab")
	mac.start()
	phone.run("join", invite(t, mac), "--name", "phone")
	phone.start()

	together := func(what string) {
		waitFor(t, 60*time.Second, what, func() bool {
			p1, ok1 := mac.peer("phone")
			p2, ok2 := phone.peer("mac")
			return ok1 && ok2 && p1.Online && p2.Online
		})
		p, _ := mac.peer("phone")
		t.Logf("%s: the Mac sees the phone via %s, %.1f ms", what, p.Path, p.RTTms)
	}
	together("a phone that cannot list its interfaces joins and connects")

	// The program says what it announces on: the address the app wrote, not anything it found out itself.
	waitFor(t, 20*time.Second, "the phone to announce itself on the app's network", func() bool {
		return strings.Contains(phone.logTail(200), "192.168.7.2/24")
	})

	dropBeacons := func(ns string, on bool) {
		flag := "-D"
		if on {
			flag = "-I"
		}
		if out, err := exec.Command("ip", "netns", "exec", ns, "iptables", flag, "OUTPUT", "-p", "udp", "--dport", "41711", "-j", "DROP").CombinedOutput(); err != nil {
			t.Fatalf("iptables %s in %s: %v\n%s", flag, ns, err, out)
		}
	}
	round := 0
	for _, c := range []struct{ what, silent string }{
		{"the phone hears the Mac's beacons (the phone's own are dropped)", "svl-L2"},
		{"the Mac hears the phone's beacons (the Mac's own are dropped)", "svl-L1"},
	} {
		round++
		mac.stop()
		phone.stop()
		// The whole network moves to 192.168.(7+round)/24 while both devices are off.
		for _, r := range [][]string{
			{"readdr", "svl-L1", fmt.Sprintf("192.168.%d.101/24", 7+round)},
			{"readdr", "svl-L2", fmt.Sprintf("192.168.%d.102/24", 7+round)},
		} {
			if out, err := exec.Command(labSh, r...).CombinedOutput(); err != nil {
				t.Fatalf("natlab %v: %v\n%s", r, err, out)
			}
		}
		phone.writeAddrs(fmt.Sprintf("192.168.%d.102/24", 7+round)) // the app does this before it starts the program
		dropBeacons(c.silent, true)
		mac.start()
		phone.start()
		together(c.what)
		dropBeacons(c.silent, false)
	}
}

// Two real devices on one home network, one in a mesh (an admin) and one that has just been installed: the new one lists
// the admin without anybody typing or scanning anything, both show the same six digits, and the device is added when both
// people have said yes - as the terminal of a server without a screen would do it (themesh nearby).
func TestNearbyDevicesFindEachOtherAndAreAdded(t *testing.T) {
	if out, err := exec.Command(labSh, "lan", "7").CombinedOutput(); err != nil {
		t.Fatalf("natlab lan: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	mac := newNode(t, "svl-L1", "mac")
	phone := newNode(t, "svl-L2", "phone")
	t.Cleanup(func() {
		if t.Failed() {
			for _, n := range []*node{mac, phone} {
				t.Logf("---- %s log ----\n%s", n.name, n.logTail(80))
			}
		}
	})
	mac.run("init", "--mesh", "Lan", "--name", "mac", "--owner", "lab")
	mac.start()
	phone.start() // installed, not in any mesh

	waitFor(t, 40*time.Second, "the new device to list the admin", func() bool {
		out, err := phone.tryRun(20*time.Second, "nearby")
		return err == nil && strings.Contains(out, "mac") && strings.Contains(out, "Lan")
	})

	ask := func(answer string) (digits string, result string, err error) {
		cmd := phone.command("nearby", "join", "mac", "--name", "phone")
		stdin, _ := cmd.StdinPipe()
		stdout, _ := cmd.StdoutPipe()
		cmd.Stderr = cmd.Stdout
		if err := cmd.Start(); err != nil {
			t.Fatal(err)
		}
		lines := make(chan string, 64)
		go func() {
			sc := bufio.NewScanner(stdout)
			sc.Buffer(make([]byte, 64<<10), 1<<20)
			for sc.Scan() {
				lines <- sc.Text()
			}
			close(lines)
		}()
		var all []string
		wait := func(re *regexp.Regexp, d time.Duration) []string {
			deadline := time.After(d)
			for {
				select {
				case l, ok := <-lines:
					if !ok {
						return nil
					}
					all = append(all, l)
					if m := re.FindStringSubmatch(l); m != nil {
						return m
					}
				case <-deadline:
					return nil
				}
			}
		}
		m := wait(regexp.MustCompile(`The six digits: (\d{6})`), 40*time.Second)
		if m == nil {
			cmd.Process.Kill()
			t.Fatalf("the new device showed no digits:\n%s", strings.Join(all, "\n"))
		}
		digits = m[1]
		// the admin lists the request with the same digits
		var reqID string
		waitFor(t, 20*time.Second, "the request to appear at the admin", func() bool {
			out, err := mac.tryRun(20*time.Second, "nearby")
			if err != nil {
				return false
			}
			for _, l := range strings.Split(out, "\n") {
				f := strings.Fields(l)
				if len(f) >= 4 && f[1] == digits {
					reqID = f[len(f)-1]
					return true
				}
			}
			return false
		})
		if answer == "no" {
			mac.run("nearby", "deny", reqID) // the new device's command, waiting for its person to answer, is told at once
		} else {
			mac.run("nearby", "allow", reqID)
			io.WriteString(stdin, "y\n")
		}
		defer stdin.Close()
		done := make(chan error, 1)
		go func() { done <- cmd.Wait() }()
		select {
		case err = <-done:
		case <-time.After(60 * time.Second):
			cmd.Process.Kill()
			err = fmt.Errorf("the join command did not end")
		}
		for l := range lines {
			all = append(all, l)
		}
		return digits, strings.Join(all, "\n"), err
	}

	// The person at the admin says no: the new device is not added, and says so.
	digits, out, err := ask("no")
	if err == nil || !strings.Contains(out, "not added") {
		t.Fatalf("a refused request ended with %v:\n%s", err, out)
	}
	t.Logf("refused (digits %s): %s", digits, strings.TrimSpace(out))
	if st, ok := phone.state(); ok && st.Self.NAT.Difficulty == "" && len(st.Peers) != 0 {
		t.Fatal("a refused device has peers")
	}

	// Asked again, both say yes.
	digits, out, err = ask("yes")
	if err != nil || !strings.Contains(out, "Added") {
		t.Fatalf("an approved request ended with %v:\n%s", err, out)
	}
	t.Logf("added (digits %s)", digits)
	waitFor(t, 60*time.Second, "the two devices to connect", func() bool {
		p1, ok1 := mac.peer("phone")
		p2, ok2 := phone.peer("mac")
		return ok1 && ok2 && p1.Online && p2.Online
	})
	p, _ := mac.peer("phone")
	t.Logf("the admin sees the new device via %s, %.1f ms", p.Path, p.RTTms)
}

// tryRun is run for a command that is expected to fail: it reports instead of
// failing the test.
func (n *node) tryRun(timeout time.Duration, args ...string) (string, error) {
	cmd := n.command(args...)
	var out bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &out
	if err := cmd.Start(); err != nil {
		return "", err
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	select {
	case err := <-done:
		return out.String(), err
	case <-time.After(timeout):
		cmd.Process.Kill()
		return out.String(), fmt.Errorf("timed out after %v", timeout)
	}
}

// A device behind a home router that only forwards what it was asked to: with
// two symmetric NATs and home firewalls nothing from outside can reach A, so B
// cannot even join. Once A's router offers UPnP (here a fake one that programs
// real iptables rules), A maps its port by itself, the invitation it makes
// contains the router's public address, and B reaches A directly - with no
// public "anchor" anywhere and nobody opening a port by hand.
func TestPortMapMakesAHomeDeviceReachable(t *testing.T) {
	if out, err := exec.Command(labSh, "up", "symmetric", "symmetric", "home").CombinedOutput(); err != nil {
		t.Fatalf("natlab up: %v\n%s", err, out)
	}
	t.Cleanup(func() { exec.Command(labSh, "down").Run() })

	a := newNode(t, "svl-A", "a")
	b := newNode(t, "svl-B", "b")
	a.portmap = true
	t.Cleanup(func() {
		if t.Failed() || os.Getenv("THEMESH_LAB_DUMP") != "" {
			for _, n := range []*node{a, b} {
				t.Logf("---- %s log ----\n%s", n.name, n.logTail(80))
			}
		}
	})
	a.run("init", "--mesh", "Lab", "--name", "a", "--owner", "lab")
	a.start()

	// Control: no router service yet. A has only its private address to offer, so B,
	// in another network behind its own NAT, cannot reach it.
	waitFor(t, 30*time.Second, "A to give up looking for a router", func() bool {
		st, ok := a.state()
		return ok && st.Self.PortMap != nil && st.Self.PortMap.State == "unavailable"
	})
	out, err := b.tryRun(60*time.Second, "join", invite(t, a), "--name", "b")
	if err == nil {
		t.Fatalf("B joined although nothing could reach A - the test would prove nothing:\n%s", out)
	}
	t.Logf("without a port mapping B cannot reach A, as expected: %s", strings.TrimSpace(lastLine(out)))

	// The router starts offering UPnP. A finds it on its next look and maps its port.
	igdLog := filepath.Join(t.TempDir(), "igd.log")
	igd := exec.Command("ip", "netns", "exec", "svl-rA", igdBin, "-lan", "192.168.1.1", "-lanif", "lanA",
		"-wan", "203.0.113.1", "-wanif", "wanA", "-log", igdLog)
	igd.Stderr = os.Stderr
	if err := igd.Start(); err != nil {
		t.Fatal(err)
	}
	igdStopped := false
	stopIGD := func() {
		if !igdStopped {
			igdStopped = true
			igd.Process.Signal(syscall.SIGTERM)
			igd.Wait()
		}
	}
	t.Cleanup(stopIGD)
	var mapped string
	waitFor(t, 150*time.Second, "A to map its port on the router", func() bool {
		st, ok := a.state()
		if ok && st.Self.PortMap != nil && st.Self.PortMap.State == "mapped" {
			mapped = st.Self.PortMap.External
			return true
		}
		return false
	})
	t.Logf("A mapped %s through UPnP", mapped)
	if !strings.HasPrefix(mapped, "203.0.113.1:") {
		t.Fatalf("the mapping should be on the router's public address, got %q", mapped)
	}
	if st, _ := a.state(); st.Self.NAT.Difficulty != "open" {
		t.Fatalf("a device with a mapped port is reachable: difficulty %q", st.Self.NAT.Difficulty)
	}

	// Now B can join through the mapped address, and the two talk directly.
	b.run("join", invite(t, a), "--name", "b")
	b.start()
	waitFor(t, 60*time.Second, "a and b connected", func() bool {
		pa, ok1 := a.peer("b")
		pb, ok2 := b.peer("a")
		return ok1 && ok2 && pa.Online && pb.Online
	})
	waitFor(t, 30*time.Second, "b's path to a to be direct", func() bool {
		pb, ok := b.peer("a")
		return ok && pb.Online && pb.Path == "direct"
	})

	data := make([]byte, 2<<20)
	rand.Read(data)
	src := filepath.Join(b.dir, "payload.bin")
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	b.run("send", "a", src)
	dest := filepath.Join(a.dir, "home", "Downloads", "The Mesh", "payload.bin")
	waitFor(t, 90*time.Second, "the file to arrive at a", func() bool {
		st, err := os.Stat(dest)
		return err == nil && st.Size() == int64(len(data))
	})
	got, _ := os.ReadFile(dest)
	if sha256.Sum256(got) != sha256.Sum256(data) {
		t.Fatal("received file differs from the original")
	}

	// When A stops, it takes its mapping off the router.
	a.stop()
	waitFor(t, 15*time.Second, "the router to be told to drop the mapping", func() bool {
		b, _ := os.ReadFile(igdLog)
		return strings.Contains(string(b), "DEL ext=")
	})
	ipt := "iptables"
	if p, err := exec.LookPath("iptables-legacy"); err == nil {
		ipt = p
	}
	rules, _ := exec.Command("ip", "netns", "exec", "svl-rA", ipt, "-t", "nat", "-S", "PREROUTING").CombinedOutput()
	if strings.Contains(string(rules), "--dport") {
		t.Fatalf("a forwarding rule was left on the router:\n%s", rules)
	}
	stopIGD()
}

func lastLine(s string) string {
	lines := strings.Split(strings.TrimSpace(s), "\n")
	return lines[len(lines)-1]
}
