package identity

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/asn1"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net"
	"net/netip"
	"sort"
	"strings"
	"time"
	"unicode"

	"golang.org/x/text/unicode/norm"
)

// DNSSuffix is the pseudo top-level domain under which members are addressable.
const DNSSuffix = "mesh"

// oidMemberExt marks the themesh extension inside member certificates. The
// 2.999 arc is reserved by ITU-T X.660 for examples and private experiments, so
// it can never collide with a registered meaning.
var oidMemberExt = asn1.ObjectIdentifier{2, 999, 7, 1}

type memberExt struct {
	Admin bool `json:"a,omitempty"`
}

// Root is the public half of a mesh authority: everything a member needs to
// verify other members.
type Root struct {
	Cert *x509.Certificate
	DER  []byte
	Pub  ed25519.PublicKey
}

// Authority is a Root plus the private key, held by admin devices. Whoever has
// it can enrol and revoke devices.
type Authority struct {
	*Root
	Priv ed25519.PrivateKey
}

// Member is a verified device certificate in a convenient form.
type Member struct {
	ID      ID         `json:"id"`
	Name    string     `json:"name"`
	Owner   string     `json:"owner"`
	IPv4    netip.Addr `json:"ip4"`
	IPv6    netip.Addr `json:"ip6"`
	Admin   bool       `json:"admin"`
	Serial  uint64     `json:"serial"`
	Issued  time.Time  `json:"issued"`
	CertDER []byte     `json:"-"`
}

// Newer reports whether m supersedes o (a re-issued certificate for the same device).
func (m *Member) Newer(o *Member) bool {
	if o == nil {
		return true
	}
	if !m.Issued.Equal(o.Issued) {
		return m.Issued.After(o.Issued)
	}
	return m.Serial > o.Serial
}

// NewAuthority creates a brand new mesh root.
func NewAuthority(meshName string) (*Authority, error) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	return authorityFromKey(priv, pub, meshName)
}

// AuthorityFromSeed rebuilds an authority from its 32 byte seed (used when an
// admin invite hands the root key to a new device). rootDER must be the root
// certificate of the same mesh.
func AuthorityFromSeed(seed []byte, rootDER []byte) (*Authority, error) {
	if len(seed) != ed25519.SeedSize {
		return nil, errors.New("identity: bad authority seed")
	}
	priv := ed25519.NewKeyFromSeed(seed)
	root, err := ParseRoot(rootDER)
	if err != nil {
		return nil, err
	}
	if !bytes.Equal(root.Pub, priv.Public().(ed25519.PublicKey)) {
		return nil, errors.New("identity: authority key does not match root certificate")
	}
	return &Authority{Root: root, Priv: priv}, nil
}

func authorityFromKey(priv ed25519.PrivateKey, pub ed25519.PublicKey, meshName string) (*Authority, error) {
	meshName = strings.TrimSpace(meshName)
	if meshName == "" {
		meshName = "mesh"
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 62))
	if err != nil {
		return nil, err
	}
	tpl := &x509.Certificate{
		SerialNumber:          serial,
		Subject:               pkix.Name{CommonName: "themesh mesh root", Organization: []string{meshName}},
		NotBefore:             time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC),
		NotAfter:              time.Date(2120, 1, 1, 0, 0, 0, 0, time.UTC),
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		BasicConstraintsValid: true,
		IsCA:                  true,
		MaxPathLen:            0,
		MaxPathLenZero:        true,
		SignatureAlgorithm:    x509.PureEd25519,
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, pub, priv)
	if err != nil {
		return nil, err
	}
	root, err := ParseRoot(der)
	if err != nil {
		return nil, err
	}
	return &Authority{Root: root, Priv: priv}, nil
}

// ParseRoot parses and sanity-checks a root certificate.
func ParseRoot(der []byte) (*Root, error) {
	c, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, fmt.Errorf("identity: bad root certificate: %w", err)
	}
	pub, ok := c.PublicKey.(ed25519.PublicKey)
	if !ok || !c.IsCA {
		return nil, errors.New("identity: root certificate must be an Ed25519 CA")
	}
	if err := c.CheckSignatureFrom(c); err != nil {
		return nil, errors.New("identity: root certificate is not self-signed")
	}
	return &Root{Cert: c, DER: append([]byte(nil), der...), Pub: pub}, nil
}

// MeshID is a short stable identifier of the mesh derived from the root key.
func (r *Root) MeshID() string {
	h := sha256.Sum256(r.Pub)
	return strings.ToLower(b32.EncodeToString(h[:]))[:12]
}

// Name returns the human readable mesh name stored in the root certificate.
func (r *Root) Name() string {
	if len(r.Cert.Subject.Organization) > 0 {
		return r.Cert.Subject.Organization[0]
	}
	return "mesh"
}

// LANKey is the symmetric key members seal their LAN beacons with. It comes from
// the root's *public* key, which every member (and everybody who has seen an
// invitation) knows: it keeps a bystander on the same Wi-Fi from reading a beacon
// or following a device from one network to the next, but it is not a secret of the
// mesh. What makes a beacon trustworthy is the device signature inside it.
func (r *Root) LANKey() [32]byte {
	h := sha256.New()
	h.Write([]byte("themesh/lan-key/v2"))
	h.Write(r.Pub)
	var out [32]byte
	copy(out[:], h.Sum(nil))
	return out
}

// IssueRequest describes a device to enrol.
type IssueRequest struct {
	ID    ID
	Name  string
	Owner string
	Admin bool
}

// Issue signs a member certificate. existing is the set of members already
// known to the issuer; it is used to keep names and overlay addresses unique.
func (a *Authority) Issue(req IssueRequest, existing []*Member) (*Member, error) {
	if req.ID.IsZero() {
		return nil, errors.New("identity: empty device id")
	}
	if _, err := IDToX25519(req.ID); err != nil {
		return nil, err
	}
	if err := checkStrongKey(req.ID); err != nil {
		return nil, err
	}
	owner := SanitizeOwner(req.Owner)
	names := map[string]bool{}
	ips := map[netip.Addr]bool{}
	for _, m := range existing {
		if m.ID == req.ID {
			continue
		}
		names[m.Name] = true
		ips[m.IPv4] = true
	}
	name := UniqueName(SanitizeName(req.Name), names)
	ip4 := AllocIPv4(req.ID, ips)
	ip6 := OverlayIPv6(a.Pub, req.ID)

	ext, _ := json.Marshal(memberExt{Admin: req.Admin})
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 62))
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC().Truncate(time.Second)
	tpl := &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			CommonName:   name,
			Organization: []string{owner},
		},
		DNSNames:           []string{name + "." + DNSSuffix},
		IPAddresses:        []net.IP{ip4.AsSlice(), ip6.AsSlice()},
		NotBefore:          now.Add(-24 * time.Hour),
		NotAfter:           now.AddDate(30, 0, 0),
		KeyUsage:           x509.KeyUsageDigitalSignature,
		ExtKeyUsage:        []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
		ExtraExtensions:    []pkix.Extension{{Id: oidMemberExt, Value: ext}},
		SignatureAlgorithm: x509.PureEd25519,
	}
	if owner == "" {
		tpl.Subject.Organization = nil
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, a.Cert, ed25519.PublicKey(req.ID[:]), a.Priv)
	if err != nil {
		return nil, err
	}
	return a.Verify(der)
}

// Verify checks that der is a member certificate signed by this root and
// returns its decoded content. It deliberately ignores the validity window:
// devices (phones, NAS boxes) routinely have wrong clocks, and revocation, not
// expiry, is how access is withdrawn.
func (r *Root) Verify(der []byte) (*Member, error) {
	c, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, fmt.Errorf("identity: bad member certificate: %w", err)
	}
	if err := c.CheckSignatureFrom(r.Cert); err != nil {
		return nil, errors.New("identity: certificate is not signed by this mesh")
	}
	pub, ok := c.PublicKey.(ed25519.PublicKey)
	if !ok {
		return nil, errors.New("identity: member key must be Ed25519")
	}
	id, err := IDFromPublicKey(pub)
	if err != nil {
		return nil, err
	}
	m := &Member{
		ID:      id,
		Name:    c.Subject.CommonName,
		Serial:  c.SerialNumber.Uint64(),
		Issued:  c.NotBefore.Add(24 * time.Hour),
		CertDER: append([]byte(nil), der...),
	}
	if len(c.Subject.Organization) > 0 {
		m.Owner = c.Subject.Organization[0]
	}
	for _, ip := range c.IPAddresses {
		if a, ok := netip.AddrFromSlice(ip); ok {
			a = a.Unmap()
			if a.Is4() {
				m.IPv4 = a
			} else {
				m.IPv6 = a
			}
		}
	}
	for _, e := range c.Extensions {
		if e.Id.Equal(oidMemberExt) {
			var x memberExt
			if err := json.Unmarshal(e.Value, &x); err == nil {
				m.Admin = x.Admin
			}
		}
	}
	if SanitizeName(m.Name) != m.Name || m.Name == "" {
		return nil, errors.New("identity: certificate carries an invalid device name")
	}
	if SanitizeOwner(m.Owner) != m.Owner {
		return nil, errors.New("identity: certificate carries an invalid owner")
	}
	return m, nil
}

// translit maps Cyrillic letters to the Latin ones used in device names, so that
// «Кухонный ноутбук» becomes kukhonnyy-noutbuk instead of nothing at all.
var translit = map[rune]string{
	'а': "a", 'б': "b", 'в': "v", 'г': "g", 'д': "d", 'е': "e", 'ё': "yo", 'ж': "zh", 'з': "z", 'и': "i",
	'й': "y", 'к': "k", 'л': "l", 'м': "m", 'н': "n", 'о': "o", 'п': "p", 'р': "r", 'с': "s", 'т': "t",
	'у': "u", 'ф': "f", 'х': "kh", 'ц': "ts", 'ч': "ch", 'ш': "sh", 'щ': "shch", 'ъ': "", 'ы': "y", 'ь': "",
	'э': "e", 'ю': "yu", 'я': "ya",
	// Ukrainian and Belarusian
	'і': "i", 'ї': "yi", 'є': "ye", 'ґ': "g", 'ў': "u",
}

// SanitizeName reduces s to a valid DNS label (lowercase ASCII letters, digits
// and hyphens, at most 32 characters). Cyrillic letters are transliterated and
// Latin letters with accents lose them; anything else becomes a hyphen.
func SanitizeName(s string) string {
	s = strings.ToLower(norm.NFC.String(strings.TrimSpace(s)))
	var pre strings.Builder
	for _, r := range s {
		if t, ok := translit[r]; ok {
			pre.WriteString(t)
		} else {
			pre.WriteRune(r)
		}
	}
	var b strings.Builder
	lastDash := true
	for _, r := range norm.NFD.String(pre.String()) {
		switch {
		case unicode.Is(unicode.Mn, r):
			// an accent that NFD split off a letter: drop it
		case r < unicode.MaxASCII && (unicode.IsLetter(r) || unicode.IsDigit(r)):
			b.WriteRune(r)
			lastDash = false
		case !lastDash:
			b.WriteByte('-')
			lastDash = true
		}
	}
	out := strings.Trim(b.String(), "-")
	if len(out) > 32 {
		out = strings.Trim(out[:32], "-")
	}
	if out == "" {
		out = "device"
	}
	return out
}

// MaxOwnerRunes is the longest owner label a certificate may carry.
const MaxOwnerRunes = 64

// SanitizeOwner turns the owner label a joining device typed into something safe
// to sign, gossip to every member and show in their interfaces: surrounding and
// repeated blanks collapse, control and invisible formatting characters go, and
// it is cut to MaxOwnerRunes.
func SanitizeOwner(s string) string {
	var b strings.Builder
	space := false
	n := 0
	for _, r := range strings.TrimSpace(s) {
		switch {
		case unicode.IsSpace(r):
			space = true
			continue
		case unicode.IsControl(r) || unicode.Is(unicode.Cf, r) || r == unicode.ReplacementChar || !unicode.IsPrint(r):
			continue
		}
		if space && b.Len() > 0 {
			b.WriteByte(' ')
			n++
		}
		space = false
		if n >= MaxOwnerRunes {
			break
		}
		b.WriteRune(r)
		n++
	}
	return strings.TrimSpace(b.String())
}

// UniqueName appends -2, -3, ... until the name is not in taken.
func UniqueName(name string, taken map[string]bool) string {
	if !taken[name] {
		return name
	}
	for i := 2; ; i++ {
		suffix := fmt.Sprintf("-%d", i)
		cand := name
		if len(cand)+len(suffix) > 32 {
			cand = cand[:32-len(suffix)]
		}
		cand += suffix
		if !taken[cand] {
			return cand
		}
	}
}

// AllocIPv4 picks an overlay IPv4 address in 100.64.0.0/10 derived from the
// device key. Deriving it makes collisions between independently issued
// certificates astronomically unlikely; taken lets the issuer step around the
// ones that are known.
func AllocIPv4(id ID, taken map[netip.Addr]bool) netip.Addr {
	for salt := uint32(0); ; salt++ {
		h := sha256.New()
		h.Write([]byte("themesh/ipv4/v1"))
		h.Write(id[:])
		var s [4]byte
		binary.BigEndian.PutUint32(s[:], salt)
		h.Write(s[:])
		sum := h.Sum(nil)
		// 22 host bits, skipping the all-zero and all-one host parts.
		n := (binary.BigEndian.Uint32(sum[:4])&(1<<22-1))%(1<<22-2) + 1
		v := (uint32(100)<<24 | uint32(64)<<16) + n
		a := netip.AddrFrom4([4]byte{byte(v >> 24), byte(v >> 16), byte(v >> 8), byte(v)})
		if !taken[a] {
			return a
		}
	}
}

// OverlayIPv6 derives a unique-local IPv6 address: a /48 per mesh (from the
// root key) and a /64 interface identifier per device.
func OverlayIPv6(rootPub ed25519.PublicKey, id ID) netip.Addr {
	g := sha256.Sum256(append([]byte("themesh/ula/v1"), rootPub...))
	i := sha256.Sum256(append([]byte("themesh/iid/v1"), id[:]...))
	var b [16]byte
	b[0] = 0xfd
	copy(b[1:6], g[:5])
	copy(b[8:16], i[:8])
	return netip.AddrFrom16(b)
}

// IsOverlayAddr reports whether a is inside the themesh overlay ranges.
func IsOverlayAddr(a netip.Addr) bool {
	a = a.Unmap()
	if a.Is4() {
		return netip.MustParsePrefix("100.64.0.0/10").Contains(a)
	}
	return a.Is6() && a.As16()[0] == 0xfd
}

// Revocation withdraws a device from the mesh. It is signed by the authority
// so it can be gossiped through untrusted paths.
type Revocation struct {
	ID  ID     `json:"id"`
	At  int64  `json:"at"`
	Sig []byte `json:"sig"`
}

func revocationMessage(root ed25519.PublicKey, id ID, at int64) []byte {
	var buf bytes.Buffer
	buf.WriteString("themesh/revoke/v1")
	buf.Write(root)
	buf.Write(id[:])
	var t [8]byte
	binary.BigEndian.PutUint64(t[:], uint64(at))
	buf.Write(t[:])
	return buf.Bytes()
}

// Revoke signs a revocation for id.
func (a *Authority) Revoke(id ID, at time.Time) Revocation {
	r := Revocation{ID: id, At: at.Unix()}
	r.Sig = ed25519.Sign(a.Priv, revocationMessage(a.Pub, id, r.At))
	return r
}

// VerifyRevocation checks the authority's signature.
func (r *Root) VerifyRevocation(rev Revocation) error {
	if !ed25519.Verify(r.Pub, revocationMessage(r.Pub, rev.ID, rev.At), rev.Sig) {
		return errors.New("identity: revocation signature invalid")
	}
	return nil
}

// SortMembers orders members by name for stable display.
func SortMembers(ms []*Member) {
	sort.Slice(ms, func(i, j int) bool {
		if ms[i].Name != ms[j].Name {
			return ms[i].Name < ms[j].Name
		}
		return bytes.Compare(ms[i].ID[:], ms[j].ID[:]) < 0
	})
}
