package mesh

import (
	"bytes"
	"context"
	"crypto/rand"
	"errors"
	"net/netip"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// ---- the packets ----

func randomTicket() (t [16]byte) {
	_, _ = rand.Read(t[:])
	return t
}

func TestNearbyNewcomerPacket(t *testing.T) {
	dev := identity.GenerateDevice()
	a := nearbyNewcomer{ID: dev.ID, Ticket: randomTicket(), Name: "pixel-8", OS: "android"}
	b := a.encode(dev)
	if b[0] == lanVersion || len(b) > nearbyPacketMax {
		t.Fatalf("a packet of %d bytes starting with %#x", len(b), b[0])
	}
	got, ok := decodeNearbyNewcomer(b)
	if !ok || got != a {
		t.Fatalf("round trip: ok=%v %+v, want %+v", ok, got, a)
	}
	for i := range b {
		bad := append([]byte(nil), b...)
		bad[i] ^= 0x01
		if _, ok := decodeNearbyNewcomer(bad); ok {
			t.Fatalf("a packet with byte %d damaged was accepted", i)
		}
	}
	for _, n := range []int{0, 1, 2, 50, len(b) - 1} {
		if _, ok := decodeNearbyNewcomer(b[:n]); ok {
			t.Fatalf("a packet cut to %d bytes was accepted", n)
		}
	}
	other := identity.GenerateDevice()
	forged := a.encode(other)
	copy(forged[2:34], dev.ID[:])
	if _, ok := decodeNearbyNewcomer(forged); ok {
		t.Fatal("a packet signed by another key was accepted")
	}
	// another packet type signed by the same key is not this one (the labels differ)
	ann := nearbyAnnouncement{ID: dev.ID, Port: 41710, Ticket: a.Ticket, Name: "x", MeshName: "y", OS: "z"}
	if _, ok := decodeNearbyNewcomer(ann.encode(dev)); ok {
		t.Fatal("an announcement passed for a newcomer")
	}
	if _, ok := decodeNearbyNewcomer((&nearbyNewcomer{ID: dev.ID, Name: ""}).encode(dev)); ok {
		t.Fatal("a newcomer without a name was accepted")
	}
}

func TestNearbyInvitationPacket(t *testing.T) {
	from, to := identity.GenerateDevice(), identity.GenerateDevice()
	invite := nearbyInvitation{Kind: nearbyKindInvite, From: from.ID, FromTicket: randomTicket(), Port: 41710, To: to.ID, ToTicket: randomTicket(),
		Name: "andreys-macbook", MeshName: strings.Repeat("Дом", 6), OS: "darwin"}
	pair := nearbyInvitation{Kind: nearbyKindPair, From: from.ID, FromTicket: randomTicket(), To: to.ID, ToTicket: randomTicket(), Name: "pixel", OS: "android"}
	for _, v := range []nearbyInvitation{invite, pair} {
		b := v.encode(from)
		if b[0] == lanVersion || len(b) > nearbyPacketMax {
			t.Fatalf("a packet of %d bytes starting with %#x", len(b), b[0])
		}
		got, ok := decodeNearbyInvitation(b)
		if !ok || got != v {
			t.Fatalf("round trip: ok=%v %+v, want %+v", ok, got, v)
		}
		for i := range b {
			bad := append([]byte(nil), b...)
			bad[i] ^= 0x01
			if _, ok := decodeNearbyInvitation(bad); ok {
				t.Fatalf("kind %d: byte %d damaged was accepted", v.Kind, i)
			}
		}
		if _, ok := decodeNearbyInvitation(b[:len(b)-1]); ok {
			t.Fatal("a cut packet was accepted")
		}
	}
	// the longest names still fit the reader's buffer
	long := invite
	long.Name, long.MeshName, long.OS = strings.Repeat("я", 40), strings.Repeat("ж", 40), strings.Repeat("o", 40)
	if b := long.encode(from); len(b) > nearbyPacketMax {
		t.Fatalf("the longest invitation is %d bytes", len(b))
	}
	// what does not make sense is refused even with a good signature
	for name, v := range map[string]nearbyInvitation{
		"an invitation to a mesh without a port":   {Kind: nearbyKindInvite, From: from.ID, To: to.ID, Name: "x", MeshName: "y"},
		"a request to make a mesh with a port":     {Kind: nearbyKindPair, From: from.ID, To: to.ID, Port: 1, Name: "x"},
		"a request to make a mesh naming one":      {Kind: nearbyKindPair, From: from.ID, To: to.ID, Name: "x", MeshName: "y"},
		"an unknown kind":                          {Kind: 7, From: from.ID, To: to.ID, Port: 1, Name: "x"},
		"an invitation to itself":                  {Kind: nearbyKindInvite, From: from.ID, To: from.ID, Port: 1, Name: "x"},
		"an invitation to nobody":                  {Kind: nearbyKindInvite, From: from.ID, Port: 1, Name: "x"},
		"an invitation from a device with no name": {Kind: nearbyKindInvite, From: from.ID, To: to.ID, Port: 1},
	} {
		if _, ok := decodeNearbyInvitation(v.encode(from)); ok {
			t.Fatalf("%s was accepted", name)
		}
	}
	forged := invite.encode(to)
	if _, ok := decodeNearbyInvitation(forged); ok {
		t.Fatal("an invitation signed by another key than the one it names was accepted")
	}
}

func TestNearbyDeclinePacket(t *testing.T) {
	from, to := identity.GenerateDevice(), identity.GenerateDevice()
	d := nearbyDecline{From: from.ID, To: to.ID, ToTicket: randomTicket()}
	b := d.encode(from)
	got, ok := decodeNearbyDecline(b)
	if !ok || got != d {
		t.Fatalf("round trip: ok=%v %+v", ok, got)
	}
	for i := range b {
		bad := append([]byte(nil), b...)
		bad[i] ^= 0x01
		if _, ok := decodeNearbyDecline(bad); ok {
			t.Fatalf("byte %d damaged was accepted", i)
		}
	}
	if _, ok := decodeNearbyDecline(d.encode(to)); ok {
		t.Fatal("a decline signed by another key was accepted")
	}
	self := nearbyDecline{From: from.ID, To: from.ID}
	if _, ok := decodeNearbyDecline(self.encode(from)); ok {
		t.Fatal("a decline to itself was accepted")
	}
}

// A device that is not in a mesh says nothing about itself unless its person looks at the list, and not at all when they
// switched it off; in a mesh it never says that it is not in one.
func TestNearbyANewcomerIsQuietUnlessItsPersonLooks(t *testing.T) {
	n, err := Open(Config{Dir: t.TempDir(), DeviceName: "pixel", LANPort: -1})
	if err != nil {
		t.Fatal(err)
	}
	defer n.Close()
	if n.nearbyNewcomerAdvert() != nil {
		t.Fatal("a newcomer whose person does not look makes itself known")
	}
	n.SetNearbyLooking(true, "Pixel Анны")
	b := n.nearbyNewcomerAdvert()
	a, ok := decodeNearbyNewcomer(b)
	if !ok || a.ID != n.ID() || a.Name != "Pixel Анны" {
		t.Fatalf("while looking: ok=%v %+v", ok, a)
	}
	if !n.ownTicket(a.Ticket[:]) {
		t.Fatal("the packet does not carry the device's ticket")
	}
	n.SetNearbyVisible(false)
	if n.nearbyNewcomerAdvert() != nil {
		t.Fatal("a newcomer that is switched off makes itself known")
	}
	n.SetNearbyVisible(true)
	n.SetNearbyLooking(false, "")
	if n.nearbyNewcomerAdvert() != nil {
		t.Fatal("the person stopped looking, the device still makes itself known")
	}
	n.SetNearbyLooking(true, "")
	if a, _ := decodeNearbyNewcomer(n.nearbyNewcomerAdvert()); a.Name != "Pixel Анны" {
		t.Fatalf("the name chosen before is forgotten: %q", a.Name)
	}
	if err := n.CreateMesh("Дом", "pixel", ""); err != nil {
		t.Fatal(err)
	}
	if n.nearbyNewcomerAdvert() != nil {
		t.Fatal("a device in a mesh says that it is not in one")
	}
}

// ---- real nodes on the network of this machine ----

func listed(n *Node, id identity.ID) (NearbyDevice, bool) {
	for _, d := range n.NearbyList() {
		if d.ID == id.String() {
			return d, true
		}
	}
	return NearbyDevice{}, false
}

func invitedBy(n *Node, id identity.ID) (NearbyInvite, bool) {
	for _, v := range n.NearbyInvites() {
		if v.ID == id.String() {
			return v, true
		}
	}
	return NearbyInvite{}, false
}

func sentTo(n *Node, id identity.ID) (NearbyOutgoing, bool) {
	for _, o := range n.NearbySent() {
		if o.ID == id.String() {
			return o, true
		}
	}
	return NearbyOutgoing{}, false
}

// keepLooking says, as the interface does, that the person looks at the list, until the test ends.
func keepLooking(t *testing.T, name string, nodes ...*Node) {
	t.Helper()
	for _, n := range nodes {
		n.SetNearbyLooking(true, name)
	}
	stop := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		for {
			select {
			case <-stop:
				return
			case <-time.After(10 * time.Second):
			}
			for _, n := range nodes {
				n.SetNearbyLooking(true, name)
			}
		}
	}()
	t.Cleanup(func() { close(stop); wg.Wait() })
}

func bothOnline(t *testing.T, a, b *Node) {
	t.Helper()
	waitNearby(t, 30*time.Second, "the two to connect", func() bool {
		p, q := a.Peer(b.ID()), b.Peer(a.ID())
		return p != nil && p.Online() && q != nil && q.Online()
	})
}

// An administrator sees a device that is not in a mesh, invites it in a tap, and the person there accepts in a tap: it
// is added without a dialog on either side and without anybody comparing digits.
func TestNearbyAnAdministratorInvitesANewcomer(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	keepLooking(t, "Pixel 8", phone)

	var d NearbyDevice
	waitNearby(t, 20*time.Second, "the Mac to list the phone", func() bool {
		var ok bool
		d, ok = listed(mac, phone.ID())
		return ok
	})
	if d.Kind != NearbyKindNew || d.Name != "Pixel 8" || d.MeshName != "" {
		t.Fatalf("listed as %+v", d)
	}
	if err := mac.ConnectNearby(d.ID, NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	if o, ok := sentTo(mac, phone.ID()); !ok || o.State != "sent" || o.Kind != "invite" {
		t.Fatalf("the invitation as the Mac sees it: %+v", o)
	}
	var inv NearbyInvite
	waitNearby(t, 20*time.Second, "the invitation to reach the phone", func() bool {
		var ok bool
		inv, ok = invitedBy(phone, mac.ID())
		return ok
	})
	if inv.Kind != "invite" || inv.MeshName != "Home" || inv.Name != "mac" {
		t.Fatalf("the phone shows %+v", inv)
	}
	if phone.Configured() {
		t.Fatal("the phone joined before its person accepted")
	}
	if err := phone.AcceptNearby(inv.ID, NearbyOptions{DeviceName: "pixel"}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
	if len(mac.NearbyRequests()) != 0 {
		t.Fatalf("the Mac's person was asked about a device they invited: %v", mac.NearbyRequests())
	}
	if got := phone.Self(); got.MeshName != "Home" || got.Admin || got.Name != "pixel" {
		t.Fatalf("the phone is %+v", got)
	}
	if p := mac.Peer(phone.ID()); p == nil || p.Member().Owner != "andrey" || p.Member().Admin {
		t.Fatalf("the Mac's view of the phone: %+v", p)
	}
	waitNearby(t, 10*time.Second, "the Mac to note how the invitation ended", func() bool {
		o, ok := sentTo(mac, phone.ID())
		return ok && o.State == "joined"
	})
	bothOnline(t, mac, phone)
	if _, ok := listed(mac, phone.ID()); ok {
		t.Fatal("the phone is a member now, and still listed as a device that is not in a mesh")
	}
	if got := phone.NearbyInvites(); len(got) != 0 {
		t.Fatalf("the phone still shows invitations: %v", got)
	}
}

// The person at a device that is not in a mesh taps a device that announced its mesh: the request goes on without them
// confirming any digits; the person at the other device says yes.
func TestNearbyANewcomerAsksInATap(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	waitNearby(t, 20*time.Second, "the phone to list the Mac", func() bool {
		d, ok := listed(phone, mac.ID())
		return ok && d.Kind == NearbyKindMesh && d.MeshName == "Home"
	})
	if err := phone.ConnectNearby(mac.ID().String(), NearbyOptions{DeviceName: "phone"}); err != nil {
		t.Fatal(err)
	}
	var req NearbyRequest
	waitNearby(t, 20*time.Second, "the request to reach the Mac, confirmed by the phone", func() bool {
		rs := mac.NearbyRequests()
		if len(rs) == 1 {
			req = rs[0]
		}
		return len(rs) == 1 && rs[0].Confirmed
	})
	if st := phone.NearbyJoinStatus(); st.State != "confirmed" || st.Code != req.Code {
		t.Fatalf("the phone: %+v, the Mac shows the digits %q", st, req.Code)
	}
	if err := mac.AnswerNearbyRequest(req.ID, true, ""); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
	bothOnline(t, mac, phone)
}

// Two devices that are not in a mesh: one asks, the other accepts and makes a mesh, and the first one joins it by itself.
func TestNearbyTwoNewcomersMakeAMesh(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	a := lanNode(t, "a", port)
	b := lanNode(t, "b", port)
	keepLooking(t, "", a, b)
	waitNearby(t, 20*time.Second, "the two to list each other", func() bool {
		da, ok1 := listed(a, b.ID())
		db, ok2 := listed(b, a.ID())
		return ok1 && ok2 && da.Kind == NearbyKindNew && db.Kind == NearbyKindNew
	})
	if err := a.ConnectNearby(b.ID().String(), NearbyOptions{DeviceName: "laptop"}); err != nil {
		t.Fatal(err)
	}
	var inv NearbyInvite
	waitNearby(t, 20*time.Second, "b to be asked", func() bool {
		var ok bool
		inv, ok = invitedBy(b, a.ID())
		return ok
	})
	if inv.Kind != "pair" || inv.MeshName != "" || inv.Name != "laptop" {
		t.Fatalf("b shows %+v", inv)
	}
	if a.Configured() || b.Configured() {
		t.Fatal("a mesh was made before anybody accepted")
	}
	if err := b.AcceptNearby(inv.ID, NearbyOptions{MeshName: "Дача", DeviceName: "phone", Owner: "Анна"}); err != nil {
		t.Fatal(err)
	}
	if !b.IsAdmin() || b.Self().MeshName != "Дача" {
		t.Fatalf("b after accepting: %+v", b.Self())
	}
	waitNearby(t, 30*time.Second, "a to join b's mesh by itself", func() bool { return a.NearbyJoinStatus().State == "joined" })
	if got := a.Self(); got.MeshName != "Дача" || got.Admin || got.Name != "laptop" {
		t.Fatalf("a is %+v", got)
	}
	if p := b.Peer(a.ID()); p == nil || p.Member().Owner != "Анна" {
		t.Fatalf("b's view of a: %+v", p)
	}
	bothOnline(t, a, b)
	if !bytes.Equal(a.Root().Pub, b.Root().Pub) {
		t.Fatal("two meshes")
	}
}

// Two devices that are not in a mesh: both people tap each other at once. When the two requests cross (each went out
// before the other one was heard), the device with the smaller key makes the mesh and the other joins it; the person at
// the bigger one tapping "accept" as well changes nothing.
func TestNearbyBothTapAtOnce(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	a := lanNode(t, "a", port)
	b := lanNode(t, "b", port)
	keepLooking(t, "", a, b)
	waitNearby(t, 20*time.Second, "the two to list each other", func() bool {
		_, ok1 := listed(a, b.ID())
		_, ok2 := listed(b, a.ID())
		return ok1 && ok2
	})
	// What two taps that cross do (ConnectNearby on a device that is not in a mesh, of one that is not either): both
	// requests go out. (A tap that comes after the other request was heard is an "accept" — the next test.)
	for _, p := range [][2]*Node{{a, b}, {b, a}} {
		e, ok := p[0].nearbyEntryOf(p[1].ID().String())
		if !ok {
			t.Fatal("the other device is not listed any more")
		}
		if err := p[0].inviteNearby(e.devID, e.Name, e.OS, e.lan, e.ticket, nearbyPairKind, "", NearbyOptions{MeshName: "Дом"}); err != nil {
			t.Fatal(err)
		}
	}
	big, ida, idb := a, a.ID(), b.ID()
	if bytes.Compare(ida[:], idb[:]) < 0 {
		big = b
	}
	small := a
	if big == a {
		small = b
	}
	waitNearby(t, 20*time.Second, "the bigger key to see the other's request", func() bool { _, ok := invitedBy(big, small.ID()); return ok || big.Configured() })
	_ = big.AcceptNearby(small.ID().String(), NearbyOptions{MeshName: "Дом"})
	waitNearby(t, 40*time.Second, "both to be in one mesh", func() bool {
		return a.Configured() && b.Configured() && (a.NearbyJoinStatus().State == "joined" || b.NearbyJoinStatus().State == "joined")
	})
	if !bytes.Equal(a.Root().Pub, b.Root().Pub) {
		t.Fatal("two meshes were made")
	}
	if !small.IsAdmin() || big.IsAdmin() {
		t.Fatalf("the device with the smaller key is to make the mesh: small admin=%v, big admin=%v", small.IsAdmin(), big.IsAdmin())
	}
	bothOnline(t, a, b)
}

// The same two taps as the people make them, at the same moment: whatever the order the packets take, the two end up in
// one mesh, with one administrator — the device with the smaller key if the requests crossed, else the one whose tap
// came second and so accepted the other's request.
func TestNearbyBothTapAtTheSameMoment(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	a := lanNode(t, "a", port)
	b := lanNode(t, "b", port)
	keepLooking(t, "", a, b)
	waitNearby(t, 20*time.Second, "the two to list each other", func() bool {
		_, ok1 := listed(a, b.ID())
		_, ok2 := listed(b, a.ID())
		return ok1 && ok2
	})
	var wg sync.WaitGroup
	for _, p := range [][2]*Node{{a, b}, {b, a}} {
		wg.Add(1)
		go func(me, other *Node) {
			defer wg.Done()
			if err := me.ConnectNearby(other.ID().String(), NearbyOptions{MeshName: "Дом"}); err != nil {
				t.Error(err)
			}
		}(p[0], p[1])
	}
	wg.Wait()
	waitNearby(t, 40*time.Second, "both to be in one mesh", func() bool {
		return a.Configured() && b.Configured() && (a.NearbyJoinStatus().State == "joined" || b.NearbyJoinStatus().State == "joined")
	})
	if !bytes.Equal(a.Root().Pub, b.Root().Pub) {
		t.Fatal("two meshes were made")
	}
	if a.IsAdmin() == b.IsAdmin() {
		t.Fatalf("one administrator, not %v and %v", a.IsAdmin(), b.IsAdmin())
	}
	// The one that made the mesh invited the other into it; the other had asked to make one together.
	maker, other := a, b
	if b.IsAdmin() {
		maker, other = b, a
	}
	if o, ok := sentTo(maker, other.ID()); !ok || o.Kind != nearbyInviteKind {
		t.Fatalf("the device that made the mesh did not invite the other into it: %+v", o)
	}
	if o, ok := sentTo(other, maker.ID()); ok && o.Kind != nearbyPairKind {
		t.Fatalf("the other device sent %+v", o)
	}
	bothOnline(t, a, b)
}

// A member that is not an administrator adds a device nearby: an administrator that is online signs the certificate,
// and its person is told. Without an administrator online, the member cannot.
func TestNearbyAMemberAddsADevice(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	phone.SetNearbyLooking(true, "phone") // (for a minute and a half)
	waitNearby(t, 20*time.Second, "the Mac to list the phone", func() bool { _, ok := listed(mac, phone.ID()); return ok })
	if err := mac.ConnectNearby(phone.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the invitation to reach the phone", func() bool { _, ok := invitedBy(phone, mac.ID()); return ok })
	if err := phone.AcceptNearby(mac.ID().String(), NearbyOptions{DeviceName: "phone"}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
	bothOnline(t, mac, phone)
	if phone.IsAdmin() {
		t.Fatal("the phone is an administrator")
	}
	// A member does not announce its mesh unless its person looks at the list.
	phone.SetNearbyLooking(false, "")
	if phone.nearbyAdvert() != nil {
		t.Fatal("a member announces itself while nobody looks at its list")
	}

	events, cancel := mac.Subscribe()
	defer cancel()
	tablet := lanNode(t, "tablet", port)
	keepLooking(t, "", phone)
	keepLooking(t, "tablet", tablet)
	if !phone.NearbyCanAdd() || phone.nearbyAdvert() == nil {
		t.Fatal("a member whose person looks at the list, with an administrator online, cannot add a device")
	}
	waitNearby(t, 20*time.Second, "the phone to list the tablet", func() bool { _, ok := listed(phone, tablet.ID()); return ok })
	if err := phone.ConnectNearby(tablet.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	var inv NearbyInvite
	waitNearby(t, 20*time.Second, "the phone's invitation to reach the tablet", func() bool {
		var ok bool
		inv, ok = invitedBy(tablet, phone.ID())
		return ok
	})
	if inv.MeshName != "Home" {
		t.Fatalf("the tablet shows %+v", inv)
	}
	if err := tablet.AcceptNearby(inv.ID, NearbyOptions{DeviceName: "tablet"}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the tablet to be added", func() bool { return tablet.NearbyJoinStatus().State == "joined" })
	if p := mac.Peer(tablet.ID()); p == nil || p.Member().Owner != "andrey" {
		t.Fatalf("the administrator's view of the tablet: %+v", p)
	}
	told := false
	for !told {
		select {
		case ev := <-events:
			told = ev.Kind == EvAdded && ev.Peer == tablet.ID() && ev.By == phone.ID()
		case <-time.After(5 * time.Second):
			t.Fatal("the administrator's person was not told that a member added a device")
		}
	}
	waitNearby(t, 30*time.Second, "the tablet to reach the administrator", func() bool {
		p := tablet.Peer(mac.ID())
		return p != nil && p.Online()
	})

	// The person at the administrator switches "nearby" off: it adds nobody for the members either.
	mac.SetNearbyVisible(false)
	stranger := identity.GenerateDevice().ID
	if _, err := phone.nearbyIssue(context.Background(), nil, stranger, "stranger", ""); !errors.Is(err, ErrNoAdminOnline) {
		t.Fatalf("an administrator that switched nearby off still adds a device for a member: %v", err)
	}
	if mac.Peer(stranger) != nil {
		t.Fatal("the administrator signed a certificate anyway")
	}
	mac.SetNearbyVisible(true)

	// The administrator goes away: the member cannot add anybody, and does not offer to.
	mac.Close()
	waitNearby(t, 60*time.Second, "the phone to notice that the administrator is gone", func() bool { return !phone.NearbyCanAdd() })
	if phone.nearbyAdvert() != nil {
		t.Fatal("a member with no administrator online announces that it can add devices")
	}
}

// The invited person says no; and an invitation can be taken back. Both sides learn it at once.
func TestNearbyDeclineAndTakeBack(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	keepLooking(t, "phone", phone)
	waitNearby(t, 20*time.Second, "the Mac to list the phone", func() bool { _, ok := listed(mac, phone.ID()); return ok })

	if err := mac.ConnectNearby(phone.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the invitation to reach the phone", func() bool { _, ok := invitedBy(phone, mac.ID()); return ok })
	onTheWay := mac.nearbyPending(phone.ID()) // (copies of the invitation that are still on their way when the person says no)
	if len(onTheWay) == 0 {
		t.Fatal("the Mac sends no invitation")
	}
	if err := phone.DeclineNearby(mac.ID().String()); err != nil {
		t.Fatal(err)
	}
	for _, u := range onTheWay {
		phone.nearbyInvited(u.pkt, netip.MustParseAddrPort("192.0.2.1:41711"))
	}
	if _, ok := invitedBy(phone, mac.ID()); ok {
		t.Fatal("a copy of the invitation that came after the answer brought it back")
	}
	waitNearby(t, 5*time.Second, "the Mac to learn the answer", func() bool {
		o, ok := sentTo(mac, phone.ID())
		return ok && o.State == "declined"
	})
	if _, ok := invitedBy(phone, mac.ID()); ok {
		t.Fatal("the declined invitation is still shown")
	}
	time.Sleep(6 * time.Second) // a declined invitation is not sent again
	if _, ok := invitedBy(phone, mac.ID()); ok {
		t.Fatal("a declined invitation came back")
	}

	// Asked again, then taken back.
	if err := mac.CancelNearby(phone.ID().String()); err != nil {
		t.Fatal(err)
	}
	if err := mac.ConnectNearby(phone.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the new invitation to reach the phone", func() bool { _, ok := invitedBy(phone, mac.ID()); return ok })
	if err := mac.CancelNearby(phone.ID().String()); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 5*time.Second, "the phone to forget the invitation that was taken back", func() bool { _, ok := invitedBy(phone, mac.ID()); return !ok })
	if phone.Configured() {
		t.Fatal("joined")
	}
	if err := phone.AcceptNearby(mac.ID().String(), NearbyOptions{}); err == nil {
		t.Fatal("an invitation that was taken back was accepted")
	}
}

// An invitation lets in the device it names and nobody else: a stranger that connects meanwhile (it heard the Mac's
// announcement, so it has a ticket) is an ordinary request that the person at the Mac has to answer.
func TestNearbyAnInvitationIsForItsDeviceOnly(t *testing.T) {
	needLAN(t)
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	phone := lanNode(t, "phone", port)
	stranger := lanNode(t, "stranger", port)
	keepLooking(t, "phone", phone)
	waitNearby(t, 20*time.Second, "the Mac to list the phone", func() bool { _, ok := listed(mac, phone.ID()); return ok })
	if err := mac.ConnectNearby(phone.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the stranger to list the Mac", func() bool { _, ok := listed(stranger, mac.ID()); return ok })
	if _, ok := invitedBy(stranger, mac.ID()); ok {
		t.Fatal("the stranger took the phone's invitation for its own")
	}
	if err := stranger.ConnectNearby(mac.ID().String(), NearbyOptions{DeviceName: "stranger"}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the stranger's request to reach the Mac", func() bool { return len(mac.NearbyRequests()) == 1 })
	time.Sleep(time.Second)
	if stranger.Configured() || mac.Peer(stranger.ID()) != nil {
		t.Fatal("the stranger was added on the phone's invitation")
	}
	if err := mac.AnswerNearbyRequest(mac.NearbyRequests()[0].ID, false, ""); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 20*time.Second, "the stranger to be told no", func() bool { return stranger.NearbyJoinStatus().State == "denied" })
	// The phone, which was invited, still gets in.
	waitNearby(t, 20*time.Second, "the invitation to reach the phone", func() bool { _, ok := invitedBy(phone, mac.ID()); return ok })
	if err := phone.AcceptNearby(mac.ID().String(), NearbyOptions{}); err != nil {
		t.Fatal(err)
	}
	waitNearby(t, 30*time.Second, "the phone to be added", func() bool { return phone.NearbyJoinStatus().State == "joined" })
}
