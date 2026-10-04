package mesh

import (
	"bytes"
	"crypto/rand"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

func sampleAnnouncement(dev *identity.Device) nearbyAnnouncement {
	a := nearbyAnnouncement{ID: dev.ID, Port: 41710, Name: "andreys-macbook", MeshName: "Дом", OS: "darwin"}
	_, _ = rand.Read(a.Ticket[:])
	return a
}

func TestNearbyAnnouncementRoundTrip(t *testing.T) {
	dev := identity.GenerateDevice()
	a := sampleAnnouncement(dev)
	b := a.encode(dev)
	if b[0] == lanVersion {
		t.Fatal("an announcement must not look like a member's beacon")
	}
	if len(b) > 256 {
		t.Fatalf("an announcement of %d bytes does not fit the buffer of the reader", len(b))
	}
	got, ok := decodeNearbyAnnouncement(b)
	if !ok || got != a {
		t.Fatalf("round trip: ok=%v %+v, want %+v", ok, got, a)
	}
}

// Every byte of an announcement is covered by the signature of the device it names: nobody can make one up for somebody
// else's key, nor change the name, the port or the ticket of a real one.
func TestNearbyAnnouncementRejectsDamage(t *testing.T) {
	dev := identity.GenerateDevice()
	good := func() []byte { a := sampleAnnouncement(dev); return a.encode(dev) }()
	for i := range good {
		bad := append([]byte(nil), good...)
		bad[i] ^= 0x01
		if _, ok := decodeNearbyAnnouncement(bad); ok {
			t.Fatalf("an announcement with byte %d damaged was accepted", i)
		}
	}
	for _, n := range []int{0, 1, 2, 60, len(good) - 1} {
		if _, ok := decodeNearbyAnnouncement(good[:n]); ok {
			t.Fatalf("an announcement cut to %d bytes was accepted", n)
		}
	}
	if _, ok := decodeNearbyAnnouncement(append(append([]byte(nil), good...), 0)); ok {
		t.Fatal("an announcement with a byte added was accepted")
	}
	// somebody else's signature under this device's name
	other := identity.GenerateDevice()
	a := sampleAnnouncement(dev)
	forged := a.encode(other)
	copy(forged[2:34], dev.ID[:]) // names the first device, signed by the second
	if _, ok := decodeNearbyAnnouncement(forged); ok {
		t.Fatal("an announcement signed by another key was accepted")
	}
}

func TestNearbyAnnouncementTextsAreMadeSafe(t *testing.T) {
	dev := identity.GenerateDevice()
	a := nearbyAnnouncement{ID: dev.ID, Port: 41710, Name: "evil\x00\n‮<script>", MeshName: strings.Repeat("Дом", 40), OS: "DARWIN"}
	got, ok := decodeNearbyAnnouncement(a.encode(dev))
	if !ok {
		t.Fatal("a signed announcement with odd texts was refused")
	}
	if strings.ContainsAny(got.Name, "\x00\n‮") {
		t.Fatalf("control characters survived: %q", got.Name)
	}
	if len(got.MeshName) > nearbyTextMax {
		t.Fatalf("a mesh name of %d bytes is longer than the limit", len(got.MeshName))
	}
	if got.OS != "darwin" {
		t.Fatalf("os %q", got.OS)
	}
	// no name, no port: not an announcement of anything
	for _, bad := range []nearbyAnnouncement{{ID: dev.ID, Port: 41710}, {ID: dev.ID, Name: "x"}} {
		if _, ok := decodeNearbyAnnouncement(bad.encode(dev)); ok {
			t.Fatalf("%+v was accepted", bad)
		}
	}
}

func TestNearbyTicketsAreChecked(t *testing.T) {
	n := &Node{}
	now := time.Now()
	n.nearby.mu.Lock()
	cur, _ := n.nearby.tickets(now)
	n.nearby.mu.Unlock()
	if !n.checkNearbySNI(nearbySNI(cur)) {
		t.Fatal("the current ticket is refused")
	}
	// rotation: the previous one still works for a while, the one before it does not
	n.nearby.mu.Lock()
	n.nearby.rotated = now.Add(-nearbyTicketLife - time.Second)
	next, prev := n.nearby.tickets(now)
	n.nearby.mu.Unlock()
	if prev != cur || next == cur {
		t.Fatal("tickets did not rotate")
	}
	if !n.checkNearbySNI(nearbySNI(prev)) || !n.checkNearbySNI(nearbySNI(next)) {
		t.Fatal("the previous or the new ticket is refused")
	}
	n.nearby.mu.Lock()
	n.nearby.rotated = now.Add(-nearbyTicketLife - time.Second)
	n.nearby.tickets(now)
	n.nearby.mu.Unlock()
	if n.checkNearbySNI(nearbySNI(cur)) {
		t.Fatal("a ticket two rotations old is accepted")
	}
	for _, bad := range []string{"", "nearby.mesh", ".nearby.mesh", "zz.nearby.mesh", strings.Repeat("0", 32) + ".join.mesh", strings.Repeat("0", 32) + ".nearby.mesh", strings.Repeat("0", 30) + ".nearby.mesh", "themesh"} {
		if n.checkNearbySNI(bad) {
			t.Fatalf("%q was accepted as a ticket", bad)
		}
	}
}

func TestNearbyCodeIsSixDigitsFromTheSession(t *testing.T) {
	a := nearbyCode([]byte{0, 0, 0, 0, 0, 0, 0, 7})
	if a != "000007" {
		t.Fatalf("code %q", a)
	}
	seen := map[string]bool{}
	for i := 0; i < 50; i++ {
		b := make([]byte, 8)
		_, _ = rand.Read(b)
		c := nearbyCode(b)
		if len(c) != 6 || strings.Trim(c, "0123456789") != "" {
			t.Fatalf("code %q is not six digits", c)
		}
		seen[c] = true
	}
	if len(seen) < 40 {
		t.Fatalf("the codes of 50 random sessions are too alike: %d different", len(seen))
	}
}

// ---- two real nodes ----

// lanNode opens a node on the real network of this machine, as the program does on a phone or a computer.
func lanNode(t *testing.T, name string, lanPort int) *Node {
	t.Helper()
	n, err := Open(Config{
		Dir:         t.TempDir(),
		DeviceName:  name,
		Owner:       "tester",
		Timing:      testTiming,
		LANPort:     lanPort,
		SyncEvery:   2 * time.Second,
		ConnectTick: 100 * time.Millisecond,
		PassiveWait: time.Second,
		DialTimeout: 8 * time.Second,
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { n.Close() })
	return n
}

func needLAN(t *testing.T) {
	t.Helper()
	for _, n := range magic.LocalNets() {
		if _, ok := n.Broadcast(); ok && n.Iface != nil {
			return
		}
	}
	t.Skip("no network card with a multicast-capable IPv4 address and a broadcast address here")
}

func waitNearby(t *testing.T, d time.Duration, what string, cond func() bool) {
	t.Helper()
	waitFor(t, d, what, cond)
}

// An admin device announces itself, a device that is not in a mesh lists it, both screens show the same digits, and the
// device is added when - and only when - both people say yes.
func TestNearbyAddingADevice(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)

	var found NearbyDevice
	waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
		for _, d := range phone.NearbyList() {
			if d.ID == mac.ID().String() {
				found = d
				return true
			}
		}
		return false
	})
	if found.Name != "mac" || found.MeshName != "Home" {
		t.Fatalf("listed as %+v", found)
	}
	// The Mac is in a mesh, so it lists nobody; and the phone gets no say about a request before it makes one.
	if got := mac.NearbyList(); len(got) != 0 {
		t.Fatalf("a device in a mesh lists %v", got)
	}
	if err := phone.ConfirmNearbyJoin(); err == nil {
		t.Fatal("a confirmation before any request was accepted")
	}

	if err := phone.StartNearbyJoin(found.ID, "phone"); err != nil {
		t.Fatal(err)
	}
	var code string
	waitNearby(t, 20*time.Second, "the digits to appear on the phone", func() bool {
		st := phone.NearbyJoinStatus()
		code = st.Code
		return st.State == "waiting" && len(code) == 6
	})
	var req NearbyRequest
	waitNearby(t, 10*time.Second, "the request to appear on the Mac", func() bool {
		rs := mac.NearbyRequests()
		if len(rs) != 1 {
			return false
		}
		req = rs[0]
		return true
	})
	if req.Code != code || req.Name != "phone" || req.Confirmed {
		t.Fatalf("the Mac shows %+v, the phone shows the digits %q", req, code)
	}

	// Nobody is added by one yes: the person at the phone has not said that the digits match yet.
	if err := mac.AnswerNearbyRequest(req.ID, true, ""); err != nil {
		t.Fatal(err)
	}
	time.Sleep(500 * time.Millisecond)
	if phone.Configured() || mac.Peer(phone.ID()) != nil {
		t.Fatal("the phone was added before its person confirmed the digits")
	}
	if err := phone.ConfirmNearbyJoin(); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
	if !phone.Configured() {
		t.Fatal("joined, but not configured")
	}
	if got := phone.Self(); got.MeshName != "Home" || got.Admin {
		t.Fatalf("the phone is %+v", got)
	}
	if p := mac.Peer(phone.ID()); p == nil || p.Member().Owner != "andrey" || p.Member().Admin { // the Mac's own owner, unless its person says otherwise
		t.Fatalf("the Mac's view of the phone: %+v", p)
	}
	waitNearby(t, 20*time.Second, "the two to connect", func() bool {
		p := mac.Peer(phone.ID())
		q := phone.Peer(mac.ID())
		return p != nil && p.Online() && q != nil && q.Online()
	})
	if got := mac.NearbyRequests(); len(got) != 0 {
		t.Fatalf("the answered request is still listed: %v", got)
	}

	// A device that leaves its mesh starts from a clean slate: the way its last request ended is forgotten, so that the
	// start screen does not show "you joined" for a device that is not in a mesh.
	if err := phone.Leave(); err != nil {
		t.Fatal(err)
	}
	if st := phone.NearbyJoinStatus(); st.State != "idle" {
		t.Fatalf("after leaving the status of the last request is still %+v", st)
	}
}

func TestNearbyRefusalAndOwner(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	find := func() string {
		var id string
		waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
			for _, d := range phone.NearbyList() {
				id = d.ID
			}
			return id != ""
		})
		return id
	}
	ask := func() NearbyRequest {
		if err := phone.StartNearbyJoin(find(), "phone"); err != nil {
			t.Fatal(err)
		}
		var req NearbyRequest
		waitNearby(t, 20*time.Second, "the request to appear on the Mac", func() bool {
			rs := mac.NearbyRequests()
			if len(rs) == 1 {
				req = rs[0]
			}
			return len(rs) == 1
		})
		return req
	}

	// The person at the Mac says no.
	req := ask()
	if err := mac.AnswerNearbyRequest(req.ID, false, ""); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the phone to be told no", func() bool { return phone.NearbyJoinStatus().State == "denied" })
	if phone.Configured() || mac.Peer(phone.ID()) != nil {
		t.Fatal("a refused device was added")
	}
	if st := phone.NearbyJoinStatus(); st.Reason != "denied" || st.Code != "" {
		t.Fatalf("status after a refusal: %+v", st)
	}
	phone.CancelNearbyJoin() // forgets how it ended
	if st := phone.NearbyJoinStatus(); st.State != "idle" {
		t.Fatalf("after cancel: %+v", st)
	}
	waitNearby(t, 10*time.Second, "the refused request to leave the Mac", func() bool { return len(mac.NearbyRequests()) == 0 })

	// Asking again, and the Mac's person says that the phone is somebody else's.
	req = ask()
	if err := mac.AnswerNearbyRequest(req.ID, true, "Анна"); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the phone to confirm", func() bool { return phone.NearbyJoinStatus().State == "waiting" })
	if err := phone.ConfirmNearbyJoin(); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
	if p := mac.Peer(phone.ID()); p == nil || p.Member().Owner != "Анна" {
		t.Fatalf("the phone's owner: %+v", p)
	}
}

// A device that does not say that the digits match is not added, whatever the other person does; neither is one that goes
// away. And an announcement alone adds nobody: a stranger that dials without a ticket gets nothing.
func TestNearbyNobodyIsAddedWithoutBothPeople(t *testing.T) {
	needLAN(t)
	old := nearbyLife()
	nearbyLifeNanos.Store(int64(3 * time.Second))
	t.Cleanup(func() { nearbyLifeNanos.Store(int64(old)) })
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	var id string
	waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
		for _, d := range phone.NearbyList() {
			id = d.ID
		}
		return id != ""
	})
	if err := phone.StartNearbyJoin(id, "phone"); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the request to appear on the Mac", func() bool { return len(mac.NearbyRequests()) == 1 })
	// The Mac's person says yes; the phone's person never confirms: after the time is up nothing happened.
	if err := mac.AnswerNearbyRequest(mac.NearbyRequests()[0].ID, true, ""); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the request to expire", func() bool { return len(mac.NearbyRequests()) == 0 })
	if phone.Configured() || mac.Peer(phone.ID()) != nil {
		t.Fatal("a device that never confirmed was added")
	}
	waitNearby(t, 20*time.Second, "the phone to be told", func() bool {
		st := phone.NearbyJoinStatus()
		return st.State != "waiting" && st.State != "connecting"
	})
	if st := phone.NearbyJoinStatus(); st.State == "joined" {
		t.Fatalf("status %+v", st)
	}
}

func TestNearbyCancelAndSwitch(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	var id string
	waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
		for _, d := range phone.NearbyList() {
			id = d.ID
		}
		return id != ""
	})
	// The person at the phone gives up: the request leaves the Mac.
	if err := phone.StartNearbyJoin(id, "phone"); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the request to appear on the Mac", func() bool { return len(mac.NearbyRequests()) == 1 })
	phone.CancelNearbyJoin()
	waitNearby(t, 20*time.Second, "the request to leave the Mac", func() bool { return len(mac.NearbyRequests()) == 0 })
	waitNearby(t, 10*time.Second, "the phone to be canceled", func() bool { return phone.NearbyJoinStatus().State == "canceled" })

	// The Mac stops announcing: it leaves the list, and a request that has no announcement behind it is refused.
	mac.SetNearbyVisible(false)
	if mac.NearbyVisible() {
		t.Fatal("still visible")
	}
	waitNearby(t, 40*time.Second, "the Mac to leave the list", func() bool { return len(phone.NearbyList()) == 0 })
	phone.CancelNearbyJoin()
	if err := phone.StartNearbyJoin(id, "phone"); err == nil {
		t.Fatal("a request to a device that is no longer listed was started")
	}
	mac.SetNearbyVisible(true)
	waitNearby(t, 20*time.Second, "the Mac to be listed again", func() bool { return len(phone.NearbyList()) == 1 })
}

// Whoever dials without a ticket from a recent announcement (somebody who found the UDP port from afar, or kept an old
// ticket) is not answered, so it cannot even put a request on the screen of the person at the inviting device.
func TestNearbyAStrangerWithoutATicketGetsNothing(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	var id string
	waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
		for _, d := range phone.NearbyList() {
			id = d.ID
		}
		return id != ""
	})
	// the phone "remembers" a ticket that is not (or no longer) the Mac's
	phone.nearby.mu.Lock()
	for _, e := range phone.nearby.seen {
		_, _ = rand.Read(e.ticket[:])
	}
	phone.nearby.mu.Unlock()
	if err := phone.StartNearbyJoin(id, "phone"); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the attempt to fail", func() bool { return phone.NearbyJoinStatus().State == "failed" })
	if st := phone.NearbyJoinStatus(); st.Reason != "offline" {
		t.Fatalf("status %+v", st)
	}
	if got := mac.NearbyRequests(); len(got) != 0 {
		t.Fatalf("a request without a ticket reached the Mac: %v", got)
	}
}

// A device that is in a mesh is not an inviter unless it is an admin: a member without the authority key announces nothing.
func TestNearbyOnlyAnAdminAnnounces(t *testing.T) {
	mac := &Node{cfg: Config{}}
	if mac.nearbyOpen() {
		t.Fatal("a device that is not in a mesh announces")
	}
	if got := mac.nearbyAdvert(); got != nil {
		t.Fatalf("announcement %x", got)
	}
	var zero netip.Addr
	if mac.nearbyShouldReply(zero) != true || mac.nearbyShouldReply(zero) != false {
		t.Fatal("queries from one address are not answered at most once in a short while")
	}
	_ = bytes.Equal
}
