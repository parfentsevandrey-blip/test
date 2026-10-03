package identity

import (
	"crypto/ed25519"
	"crypto/x509"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"golang.org/x/crypto/nacl/box"
)

func TestIDRoundTrip(t *testing.T) {
	d := GenerateDevice()
	s := d.ID.String()
	if len(s) != 52 {
		t.Fatalf("unexpected id length %d: %s", len(s), s)
	}
	got, err := ParseID(strings.ToUpper(s[:20] + "-" + s[20:]))
	if err != nil || got != d.ID {
		t.Fatalf("ParseID: %v %v", got, err)
	}
	if _, err := ParseID("nonsense"); err == nil {
		t.Fatal("expected error for garbage id")
	}
	b, _ := d.ID.MarshalText()
	var back ID
	if err := back.UnmarshalText(b); err != nil || back != d.ID {
		t.Fatal("text marshal round trip failed")
	}
}

func TestDeviceKeyPersistence(t *testing.T) {
	path := filepath.Join(t.TempDir(), "sub", "device.key")
	d1, created, err := LoadOrCreateDevice(path)
	if err != nil || !created {
		t.Fatalf("create: %v created=%v", err, created)
	}
	d2, created, err := LoadOrCreateDevice(path)
	if err != nil || created {
		t.Fatalf("load: %v created=%v", err, created)
	}
	if d1.ID != d2.ID {
		t.Fatal("identity changed across reload")
	}
	st, _ := os.Stat(path)
	if st.Mode().Perm() != 0o600 {
		t.Fatalf("key file mode is %v, want 0600", st.Mode().Perm())
	}
	if err := os.WriteFile(path, []byte("garbage"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := LoadOrCreateDevice(path); err == nil {
		t.Fatal("corrupt key file must be rejected, not silently replaced")
	}
}

// The Ed25519 -> X25519 conversion must be consistent: two devices derive the
// same shared secret from their own private key and the peer's ID alone.
func TestX25519ConversionAgrees(t *testing.T) {
	a, b := GenerateDevice(), GenerateDevice()
	aPriv, aPub := a.X25519()
	bPriv, bPub := b.X25519()
	if pub, err := IDToX25519(a.ID); err != nil || pub != aPub {
		t.Fatal("public conversion disagrees with private-derived public key")
	}
	var s1, s2 [32]byte
	box.Precompute(&s1, &bPub, &aPriv)
	box.Precompute(&s2, &aPub, &bPriv)
	if s1 != s2 {
		t.Fatal("shared secrets differ")
	}
	msg := []byte("hello from the other side")
	var nonce [24]byte
	sealed := box.SealAfterPrecomputation(nil, msg, &nonce, &s1)
	open, ok := box.OpenAfterPrecomputation(nil, sealed, &nonce, &s2)
	if !ok || string(open) != string(msg) {
		t.Fatal("box round trip failed")
	}
}

func TestIssueAndVerify(t *testing.T) {
	auth, err := NewAuthority("Home")
	if err != nil {
		t.Fatal(err)
	}
	if auth.Name() != "Home" {
		t.Fatalf("mesh name %q", auth.Name())
	}
	laptop, nas := GenerateDevice(), GenerateDevice()

	m1, err := auth.Issue(IssueRequest{ID: laptop.ID, Name: "My Laptop!", Owner: "Andrey", Admin: true}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if m1.Name != "my-laptop" || m1.Owner != "Andrey" || !m1.Admin {
		t.Fatalf("unexpected member %+v", m1)
	}
	if !IsOverlayAddr(m1.IPv4) || !IsOverlayAddr(m1.IPv6) {
		t.Fatalf("addresses not in overlay range: %v %v", m1.IPv4, m1.IPv6)
	}
	if got := m1.IPv4; !netip.MustParsePrefix("100.64.0.0/10").Contains(got) {
		t.Fatalf("ipv4 %v outside CGNAT range", got)
	}

	// Same requested name must be made unique.
	m2, err := auth.Issue(IssueRequest{ID: nas.ID, Name: "my laptop"}, []*Member{m1})
	if err != nil {
		t.Fatal(err)
	}
	if m2.Name != "my-laptop-2" {
		t.Fatalf("name not uniquified: %q", m2.Name)
	}
	if m2.Admin {
		t.Fatal("admin must not be implied")
	}

	// A verifier that only has the public root must accept it.
	root, err := ParseRoot(auth.DER)
	if err != nil {
		t.Fatal(err)
	}
	got, err := root.Verify(m2.CertDER)
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != nas.ID || got.Name != m2.Name || got.IPv4 != m2.IPv4 {
		t.Fatalf("round trip mismatch: %+v vs %+v", got, m2)
	}

	// A different mesh must not accept it.
	other, _ := NewAuthority("Other")
	if _, err := other.Verify(m2.CertDER); err == nil {
		t.Fatal("certificate verified under a foreign root")
	}

	// Tampering (flip the admin flag by re-encoding) must break the signature.
	der := append([]byte(nil), m2.CertDER...)
	for i := len(der) - 80; i > 0; i-- { // somewhere inside the TBS part
		der[i] ^= 0x01
		if _, err := root.Verify(der); err == nil {
			t.Fatalf("tampered certificate at byte %d verified", i)
		}
		der[i] ^= 0x01
		if len(der)-i > 300 {
			break
		}
	}

	// Verify ignores the validity window (devices with bad clocks must work).
	c, _ := x509.ParseCertificate(m2.CertDER)
	if c.NotAfter.Before(time.Now().AddDate(20, 0, 0)) {
		t.Fatalf("certificate lifetime too short: %v", c.NotAfter)
	}
}

func TestAuthorityHandoff(t *testing.T) {
	auth, _ := NewAuthority("Home")
	seed := auth.Priv.Seed()
	clone, err := AuthorityFromSeed(seed, auth.DER)
	if err != nil {
		t.Fatal(err)
	}
	dev := GenerateDevice()
	m, err := clone.Issue(IssueRequest{ID: dev.ID, Name: "phone"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := auth.Verify(m.CertDER); err != nil {
		t.Fatalf("cert issued by the clone not accepted by the original: %v", err)
	}
	other, _ := NewAuthority("x")
	if _, err := AuthorityFromSeed(seed, other.DER); err == nil {
		t.Fatal("seed accepted for a root it does not belong to")
	}
}

func TestRevocation(t *testing.T) {
	auth, _ := NewAuthority("Home")
	victim := GenerateDevice()
	rev := auth.Revoke(victim.ID, time.Now())
	root, _ := ParseRoot(auth.DER)
	if err := root.VerifyRevocation(rev); err != nil {
		t.Fatal(err)
	}
	forged := rev
	forged.ID = GenerateDevice().ID
	if err := root.VerifyRevocation(forged); err == nil {
		t.Fatal("forged revocation accepted")
	}
	other, _ := NewAuthority("Other")
	if err := other.VerifyRevocation(rev); err == nil {
		t.Fatal("revocation accepted by a different mesh")
	}
}

func TestInviteRoundTrip(t *testing.T) {
	auth, _ := NewAuthority("Home")
	inviter := GenerateDevice()
	eps := []netip.AddrPort{
		netip.MustParseAddrPort("192.168.1.10:41710"),
		netip.MustParseAddrPort("203.0.113.7:41710"),
		netip.MustParseAddrPort("[2001:db8::1]:41710"),
	}
	inv, err := NewInvite(auth.Pub, inviter.ID, 30*time.Minute, true, eps, "Домашняя сеть")
	if err != nil {
		t.Fatal(err)
	}
	text := inv.Encode()
	if !strings.HasPrefix(text, InvitePrefix) {
		t.Fatalf("missing prefix: %s", text)
	}
	t.Logf("invite (%d chars): %s", len(text), text)

	got, err := ParseInvite(strings.ToLower(strings.ReplaceAll(text, "-", " ")))
	if err != nil {
		t.Fatal(err)
	}
	if got.Inviter != inv.Inviter || got.Secret != inv.Secret || !got.Admin ||
		got.MeshName != inv.MeshName || len(got.Endpoints) != 3 || got.Endpoints[2] != eps[2] ||
		string(got.Root) != string(auth.Pub) || !got.Expires.Equal(inv.Expires) {
		t.Fatalf("round trip mismatch:\n%+v\n%+v", got, inv)
	}
	if got.Handle() != inv.Handle() {
		t.Fatal("handle differs")
	}

	// A typo must be detected by the checksum.
	body := strings.TrimPrefix(text, InvitePrefix)
	bad := InvitePrefix + "A" + body[1:]
	if body[0] == 'A' {
		bad = InvitePrefix + "B" + body[1:]
	}
	if _, err := ParseInvite(bad); err == nil {
		t.Fatal("invite with a typo was accepted")
	}
	if _, err := ParseInvite("hello"); err == nil {
		t.Fatal("garbage accepted as invite")
	}
	if _, err := ParseInvite(text[:len(text)/2]); err == nil {
		t.Fatal("truncated invite accepted")
	}

	if inv.Expired(time.Now()) {
		t.Fatal("fresh invite reported expired")
	}
	if !inv.Expired(time.Now().Add(time.Hour)) {
		t.Fatal("old invite not reported expired")
	}
}

func TestInviteProofIsChannelBound(t *testing.T) {
	auth, _ := NewAuthority("Home")
	inv, _ := NewInvite(auth.Pub, GenerateDevice().ID, time.Minute, false, nil, "")
	p := inv.Proof([]byte("session-1"))
	if !inv.CheckProof([]byte("session-1"), p) {
		t.Fatal("valid proof rejected")
	}
	if inv.CheckProof([]byte("session-2"), p) {
		t.Fatal("proof replayed on another session was accepted")
	}
	other, _ := NewInvite(auth.Pub, GenerateDevice().ID, time.Minute, false, nil, "")
	if other.CheckProof([]byte("session-1"), p) {
		t.Fatal("proof accepted for a different secret")
	}
}

func TestSanitizeName(t *testing.T) {
	cases := map[string]string{
		"My Laptop!":            "my-laptop",
		"  NAS  ":               "nas",
		"Телефон":               "device",
		"a--b__c":               "a-b-c",
		"":                      "device",
		strings.Repeat("x", 80): strings.Repeat("x", 32),
	}
	for in, want := range cases {
		if got := SanitizeName(in); got != want {
			t.Errorf("SanitizeName(%q) = %q, want %q", in, got, want)
		}
	}
	taken := map[string]bool{"nas": true, "nas-2": true}
	if got := UniqueName("nas", taken); got != "nas-3" {
		t.Errorf("UniqueName = %q", got)
	}
}

func TestOverlayAddressesAreStableAndDistinct(t *testing.T) {
	root, _ := NewAuthority("x")
	d := GenerateDevice()
	a := AllocIPv4(d.ID, nil)
	if b := AllocIPv4(d.ID, nil); a != b {
		t.Fatal("IPv4 allocation not deterministic")
	}
	if c := AllocIPv4(d.ID, map[netip.Addr]bool{a: true}); c == a {
		t.Fatal("taken address reused")
	}
	if OverlayIPv6(root.Pub, d.ID) != OverlayIPv6(root.Pub, d.ID) {
		t.Fatal("IPv6 not deterministic")
	}
	seen := map[netip.Addr]bool{}
	for i := 0; i < 2000; i++ {
		ip := AllocIPv4(GenerateDevice().ID, nil)
		if seen[ip] {
			t.Logf("collision after %d devices (possible but rare)", i)
		}
		seen[ip] = true
	}
	_ = ed25519.PublicKeySize
}
