package identity

import (
	"bytes"
	"crypto/ed25519"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net/netip"
	"strings"
	"time"
)

// InvitePrefix starts every invitation code.
const InvitePrefix = "MESH1-"

// Invite is a one-time pass to join a mesh. It is handed over out of band (as
// text or a QR code), so it carries everything the newcomer needs to find and
// authenticate the inviter without any server: the mesh root key, the inviter's
// device key, a shared secret and a few addresses to try.
type Invite struct {
	Root      ed25519.PublicKey
	Inviter   ID
	Secret    [16]byte
	Expires   time.Time
	Admin     bool
	Endpoints []netip.AddrPort
	MeshName  string
}

// NewInvite creates an invite with a fresh random secret.
func NewInvite(root ed25519.PublicKey, inviter ID, ttl time.Duration, admin bool, eps []netip.AddrPort, meshName string) (*Invite, error) {
	inv := &Invite{
		Root:      append(ed25519.PublicKey(nil), root...),
		Inviter:   inviter,
		Expires:   time.Now().Add(ttl).Truncate(time.Second),
		Admin:     admin,
		Endpoints: eps,
		MeshName:  meshName,
	}
	if _, err := rand.Read(inv.Secret[:]); err != nil {
		return nil, err
	}
	if len(inv.Endpoints) > 8 {
		inv.Endpoints = inv.Endpoints[:8]
	}
	return inv, nil
}

// Handle is a public identifier of the invite (a hash of the secret) that the
// joiner sends so the inviter knows which pending invite it refers to.
func (i *Invite) Handle() [8]byte {
	h := sha256.Sum256(append([]byte("themesh/invite-handle/v1"), i.Secret[:]...))
	var out [8]byte
	copy(out[:], h[:8])
	return out
}

// Proof demonstrates knowledge of the secret, bound to one TLS session via its
// exported keying material so it cannot be replayed on another connection.
func (i *Invite) Proof(exporter []byte) []byte {
	m := hmac.New(sha256.New, i.Secret[:])
	m.Write([]byte("themesh/join/v1"))
	m.Write(exporter)
	return m.Sum(nil)
}

// CheckProof verifies a proof in constant time.
func (i *Invite) CheckProof(exporter, proof []byte) bool {
	return hmac.Equal(i.Proof(exporter), proof)
}

// Expired reports whether the invite is past its lifetime.
func (i *Invite) Expired(now time.Time) bool { return !now.Before(i.Expires) }

// Encode renders the invite as the text users copy around.
func (i *Invite) Encode() string {
	var b bytes.Buffer
	b.WriteByte(1) // format version
	var flags byte
	if i.Admin {
		flags |= 1
	}
	b.WriteByte(flags)
	var exp [4]byte
	binary.BigEndian.PutUint32(exp[:], uint32(i.Expires.Unix()))
	b.Write(exp[:])
	b.Write(i.Root)
	b.Write(i.Inviter[:])
	b.Write(i.Secret[:])
	eps := i.Endpoints
	if len(eps) > 8 {
		eps = eps[:8]
	}
	b.WriteByte(byte(len(eps)))
	for _, ep := range eps {
		a := ep.Addr().Unmap()
		if a.Is4() {
			b.WriteByte(4)
		} else {
			b.WriteByte(6)
		}
		b.Write(a.AsSlice())
		var p [2]byte
		binary.BigEndian.PutUint16(p[:], ep.Port())
		b.Write(p[:])
	}
	name := []byte(i.MeshName)
	if len(name) > 40 {
		name = name[:40]
	}
	b.WriteByte(byte(len(name)))
	b.Write(name)
	sum := sha256.Sum256(b.Bytes())
	b.Write(sum[:4]) // typo detector
	enc := strings.ToUpper(b32.EncodeToString(b.Bytes()))
	var out strings.Builder
	out.WriteString(InvitePrefix)
	for n := 0; n < len(enc); n += 8 {
		if n > 0 {
			out.WriteByte('-')
		}
		end := n + 8
		if end > len(enc) {
			end = len(enc)
		}
		out.WriteString(enc[n:end])
	}
	return out.String()
}

// ParseInvite decodes text produced by Encode. Whitespace, dashes and case are
// ignored so it survives being pasted from chat apps.
func ParseInvite(s string) (*Invite, error) {
	s = strings.ToUpper(strings.NewReplacer("-", "", " ", "", "\n", "", "\r", "", "\t", "").Replace(s))
	p := strings.ReplaceAll(InvitePrefix, "-", "")
	// The prefix is "MESH1"; tolerate a "themesh://join/" style wrapper too.
	if i := strings.Index(s, p); i >= 0 {
		s = s[i+len(p):]
	} else {
		return nil, errors.New("identity: this is not an invitation to The Mesh")
	}
	raw, err := b32.DecodeString(s)
	if err != nil {
		return nil, errors.New("identity: invite contains invalid characters")
	}
	if len(raw) < 1+1+4+32+32+16+1+1+4 {
		return nil, errors.New("identity: invite is too short")
	}
	body, sum := raw[:len(raw)-4], raw[len(raw)-4:]
	want := sha256.Sum256(body)
	if !bytes.Equal(sum, want[:4]) {
		return nil, errors.New("identity: invite checksum mismatch (typo?)")
	}
	r := bytes.NewReader(body)
	rd := func(n int) []byte {
		buf := make([]byte, n)
		if _, e := io.ReadFull(r, buf); e != nil && n > 0 {
			err = errors.New("identity: invite is truncated")
		}
		return buf
	}
	if v := rd(1); err == nil && v[0] != 1 {
		return nil, fmt.Errorf("identity: unsupported invite version %d", v[0])
	}
	inv := &Invite{}
	flags := rd(1)
	inv.Admin = flags[0]&1 != 0
	inv.Expires = time.Unix(int64(binary.BigEndian.Uint32(rd(4))), 0)
	inv.Root = rd(32)
	copy(inv.Inviter[:], rd(32))
	copy(inv.Secret[:], rd(16))
	n := int(rd(1)[0])
	for k := 0; k < n && err == nil; k++ {
		kind := rd(1)[0]
		var addr netip.Addr
		switch kind {
		case 4:
			addr = netip.AddrFrom4([4]byte(rd(4)))
		case 6:
			addr = netip.AddrFrom16([16]byte(rd(16)))
		default:
			return nil, errors.New("identity: invite has a malformed address")
		}
		port := binary.BigEndian.Uint16(rd(2))
		inv.Endpoints = append(inv.Endpoints, netip.AddrPortFrom(addr, port))
	}
	nl := int(rd(1)[0])
	inv.MeshName = string(rd(nl))
	if err != nil {
		return nil, err
	}
	return inv, nil
}
