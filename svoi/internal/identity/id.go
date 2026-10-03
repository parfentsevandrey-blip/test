// Package identity holds everything that answers the question "who is this
// device and why do we trust it": device keys, the mesh certificate authority,
// member certificates, revocations and invitation codes.
//
// There is no server in the picture. A mesh is defined by a root key pair; every
// device carries an X.509 certificate signed by that root, so any two members
// can authenticate each other offline.
package identity

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base32"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"filippo.io/edwards25519"
)

// ID is a device identity: its Ed25519 public key.
type ID [32]byte

var b32 = base32.StdEncoding.WithPadding(base32.NoPadding)

// String returns the canonical text form: 52 lowercase base32 characters.
func (id ID) String() string { return strings.ToLower(b32.EncodeToString(id[:])) }

// Short returns an 8 character prefix that is good enough for display.
func (id ID) Short() string { return id.String()[:8] }

// IsZero reports whether the ID is unset.
func (id ID) IsZero() bool { return id == ID{} }

// PublicKey returns the identity as an Ed25519 public key.
func (id ID) PublicKey() ed25519.PublicKey { return ed25519.PublicKey(id[:]) }

// Route8 is the 8 byte identifier used in packet headers on the wire. It is a
// truncated hash so that the public key itself never appears in data packets.
func (id ID) Route8() [8]byte {
	h := sha256.New()
	h.Write([]byte("svoi/route8/v1"))
	h.Write(id[:])
	var out [8]byte
	copy(out[:], h.Sum(nil))
	return out
}

// MarshalText implements encoding.TextMarshaler so IDs are readable in JSON.
func (id ID) MarshalText() ([]byte, error) { return []byte(id.String()), nil }

// UnmarshalText implements encoding.TextUnmarshaler.
func (id *ID) UnmarshalText(b []byte) error {
	v, err := ParseID(string(b))
	if err != nil {
		return err
	}
	*id = v
	return nil
}

// ParseID parses the text form of an ID. It is case-insensitive and ignores
// spaces and dashes so IDs can be pasted from formatted displays.
func ParseID(s string) (ID, error) {
	s = strings.ToUpper(strings.NewReplacer("-", "", " ", "").Replace(strings.TrimSpace(s)))
	raw, err := b32.DecodeString(s)
	if err != nil || len(raw) != 32 {
		return ID{}, errors.New("identity: malformed device id")
	}
	var id ID
	copy(id[:], raw)
	return id, nil
}

// IDFromPublicKey converts an Ed25519 public key to an ID.
func IDFromPublicKey(pub ed25519.PublicKey) (ID, error) {
	if len(pub) != ed25519.PublicKeySize {
		return ID{}, errors.New("identity: bad public key length")
	}
	var id ID
	copy(id[:], pub)
	if err := checkStrongKey(id); err != nil {
		return ID{}, err
	}
	return id, nil
}

// checkStrongKey rejects public keys that are not a valid curve point or whose
// order divides the cofactor (the identity point and its small-order relatives):
// for those, signatures can be forged by anyone and the key-agreement result is a
// constant, so such a "device" would be impersonable by every other member.
func checkStrongKey(id ID) error {
	p, err := new(edwards25519.Point).SetBytes(id[:])
	if err != nil {
		return errors.New("identity: not a valid public key")
	}
	if new(edwards25519.Point).MultByCofactor(p).Equal(edwards25519.NewIdentityPoint()) == 1 {
		return errors.New("identity: weak (small-order) public key")
	}
	return nil
}

// Device is this machine's long-term identity.
type Device struct {
	Priv ed25519.PrivateKey
	ID   ID
}

// GenerateDevice creates a fresh random device identity.
func GenerateDevice() *Device {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		panic("identity: entropy source failed: " + err.Error())
	}
	d := &Device{Priv: priv}
	copy(d.ID[:], pub)
	return d
}

const deviceKeyPrefix = "svoi-device-key-v1:"

// LoadOrCreateDevice reads the device key at path or creates a new one with
// 0600 permissions. created reports whether a new key was generated.
func LoadOrCreateDevice(path string) (dev *Device, created bool, err error) {
	raw, err := os.ReadFile(path)
	if err == nil {
		s := strings.TrimSpace(string(raw))
		if !strings.HasPrefix(s, deviceKeyPrefix) {
			return nil, false, fmt.Errorf("identity: %s is not a device key file", path)
		}
		seed, derr := b32.DecodeString(strings.ToUpper(strings.TrimPrefix(s, deviceKeyPrefix)))
		if derr != nil || len(seed) != ed25519.SeedSize {
			return nil, false, fmt.Errorf("identity: %s is corrupt", path)
		}
		priv := ed25519.NewKeyFromSeed(seed)
		d := &Device{Priv: priv}
		copy(d.ID[:], priv.Public().(ed25519.PublicKey))
		return d, false, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return nil, false, err
	}
	d := GenerateDevice()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, false, err
	}
	body := deviceKeyPrefix + strings.ToLower(b32.EncodeToString(d.Priv.Seed())) + "\n"
	if err := writeFileAtomic(path, []byte(body), 0o600); err != nil {
		return nil, false, err
	}
	return d, true, nil
}

// X25519 returns the device key converted for Diffie-Hellman use (NaCl box).
func (d *Device) X25519() (priv, pub [32]byte) {
	h := sha512.Sum512(d.Priv.Seed())
	copy(priv[:], h[:32])
	priv[0] &= 248
	priv[31] &= 127
	priv[31] |= 64
	pk, err := IDToX25519(d.ID)
	if err != nil {
		panic("identity: own key is not a valid curve point: " + err.Error())
	}
	return priv, pk
}

// IDToX25519 converts an Ed25519 public key (an ID) to its X25519 counterpart.
func IDToX25519(id ID) ([32]byte, error) {
	p, err := new(edwards25519.Point).SetBytes(id[:])
	if err != nil {
		return [32]byte{}, errors.New("identity: not a valid public key")
	}
	var out [32]byte
	copy(out[:], p.BytesMontgomery())
	return out, nil
}

// writeFileAtomic writes via a temp file + rename so a crash never leaves a
// half-written key or state file behind.
func writeFileAtomic(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	if err := tmp.Chmod(perm); err != nil {
		tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(name, path)
}

// WriteFileAtomic is exported for packages that persist small state files.
func WriteFileAtomic(path string, data []byte, perm os.FileMode) error {
	return writeFileAtomic(path, data, perm)
}
