package app

import (
	"crypto/ed25519"
	"crypto/rand"
	"net/netip"
	"regexp"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

func qrSize(t *testing.T, svg string) int {
	t.Helper()
	m := regexp.MustCompile(`viewBox="0 0 (\d+) (\d+)"`).FindStringSubmatch(svg)
	if m == nil || m[1] != m[2] {
		t.Fatalf("no square viewBox in %.80s", svg)
	}
	n, _ := strconv.Atoi(m[1])
	return n
}

func testInvite(t *testing.T, eps ...string) *identity.Invite {
	t.Helper()
	root, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	var inviter identity.ID
	if _, err := rand.Read(inviter[:]); err != nil {
		t.Fatal(err)
	}
	var list []netip.AddrPort
	for _, e := range eps {
		list = append(list, netip.MustParseAddrPort(e))
	}
	inv, err := identity.NewInvite(root, inviter, time.Hour, false, list, "Дом")
	if err != nil {
		t.Fatal(err)
	}
	return inv
}

// The QR of an invitation carries the code without the dashes that group it for the eye: still an invitation (parsing ignores
// them), still recognisable by its prefix, and a QR that is not larger than the one of the dashed code, usually smaller.
func TestQRCarriesTheCompactCode(t *testing.T) {
	for name, eps := range map[string][]string{
		"home network only":   {"192.168.1.23:41710"},
		"typical":             {"192.168.1.23:41710", "203.0.113.5:41710", "[2001:db8::1]:41710"},
		"the most it carries": {"127.0.0.1:41710", "198.51.100.7:41710", "192.168.1.23:41710", "203.0.113.5:41710", "[2001:db8::1]:41710", "[2001:db8::2]:41710"},
	} {
		inv := testInvite(t, eps...)
		code := inv.Encode()
		p := qrPayload(code)
		if !strings.HasPrefix(p, identity.InvitePrefix) || strings.Contains(strings.TrimPrefix(p, identity.InvitePrefix), "-") {
			t.Fatalf("%s: payload %q: want the prefix with its dash and no other", name, p)
		}
		if len(p) >= len(code) {
			t.Fatalf("%s: the payload (%d) is not shorter than the code (%d)", name, len(p), len(code))
		}
		back, err := identity.ParseInvite(p)
		if err != nil || back.Handle() != inv.Handle() || len(back.Endpoints) != len(eps) {
			t.Fatalf("%s: the payload does not parse back to the invitation: %v", name, err)
		}
		dashed, compact := qrSize(t, qrSVG(code)), qrSize(t, qrSVG(p))
		t.Logf("%-22s code %3d chars -> payload %3d chars; QR %dx%d modules (dashed code: %dx%d)", name, len(code), len(p), compact-8, compact-8, dashed-8, dashed-8)
		if compact > dashed {
			t.Fatalf("%s: the compact QR (%d) is larger than the dashed one (%d)", name, compact, dashed)
		}
		if compact-8 > 73 { // version 14: past this a screen QR gets hard to read for a phone at arm's length
			t.Fatalf("%s: the QR has %d modules a side", name, compact-8)
		}
	}
}

// The view of an invitation carries the compact QR, and the code shown for copying stays grouped.
func TestInviteViewShowsTheGroupedCodeAndTheCompactQR(t *testing.T) {
	inv := testInvite(t, "192.168.1.23:41710", "203.0.113.5:41710")
	v := (&App{}).inviteView(mesh.InviteInfo{ID: "00", Code: inv.Encode(), Expires: inv.Expires.Unix()})
	if v.Code != inv.Encode() || !strings.Contains(v.Code, "-") {
		t.Fatalf("the code for copying: %q", v.Code)
	}
	if want := qrSVG(qrPayload(inv.Encode())); v.QRSvg != want {
		t.Fatal("the QR is not the one of the compact payload")
	}
}
