package magic

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"net/netip"
)

// A minimal RFC 5389 STUN client: just enough to ask a public server "what
// address do you see me coming from?". It is optional - peers tell each other
// the same thing in Pong messages - but it lets a lone node learn its public
// endpoint before it has met anyone.

const stunCookie = 0x2112A442

const (
	stunBindingRequest  = 0x0001
	stunBindingResponse = 0x0101
	attrMappedAddress   = 0x0001
	attrXorMapped       = 0x0020
)

type stunTxID [12]byte

func newStunRequest() (stunTxID, []byte) {
	var tx stunTxID
	_, _ = rand.Read(tx[:])
	b := make([]byte, 20)
	binary.BigEndian.PutUint16(b[0:], stunBindingRequest)
	binary.BigEndian.PutUint16(b[2:], 0)
	binary.BigEndian.PutUint32(b[4:], stunCookie)
	copy(b[8:], tx[:])
	return tx, b
}

func looksLikeSTUN(p []byte) bool {
	return len(p) >= 20 && p[0] < 0x40 && binary.BigEndian.Uint32(p[4:8]) == stunCookie
}

// parseStunResponse extracts the reflexive address from a binding response.
func parseStunResponse(p []byte) (stunTxID, netip.AddrPort, error) {
	var tx stunTxID
	if !looksLikeSTUN(p) {
		return tx, netip.AddrPort{}, errors.New("stun: not a STUN message")
	}
	if binary.BigEndian.Uint16(p[0:]) != stunBindingResponse {
		return tx, netip.AddrPort{}, errors.New("stun: not a binding success response")
	}
	copy(tx[:], p[8:20])
	n := int(binary.BigEndian.Uint16(p[2:]))
	if len(p) < 20+n {
		return tx, netip.AddrPort{}, errShort
	}
	attrs := p[20 : 20+n]
	var mapped netip.AddrPort
	for len(attrs) >= 4 {
		typ := binary.BigEndian.Uint16(attrs[0:])
		l := int(binary.BigEndian.Uint16(attrs[2:]))
		if len(attrs) < 4+l {
			return tx, netip.AddrPort{}, errShort
		}
		val := attrs[4 : 4+l]
		switch typ {
		case attrXorMapped:
			if ap, ok := decodeStunAddr(val, true, tx); ok {
				return tx, ap, nil
			}
		case attrMappedAddress:
			if ap, ok := decodeStunAddr(val, false, tx); ok {
				mapped = ap
			}
		}
		adv := 4 + l
		if pad := l % 4; pad != 0 {
			adv += 4 - pad
		}
		if adv > len(attrs) {
			break
		}
		attrs = attrs[adv:]
	}
	if mapped.IsValid() {
		return tx, mapped, nil
	}
	return tx, netip.AddrPort{}, errors.New("stun: response has no mapped address")
}

func decodeStunAddr(v []byte, xor bool, tx stunTxID) (netip.AddrPort, bool) {
	if len(v) < 4 {
		return netip.AddrPort{}, false
	}
	port := binary.BigEndian.Uint16(v[2:4])
	if xor {
		port ^= uint16(stunCookie >> 16)
	}
	switch v[1] {
	case 1:
		if len(v) < 8 {
			return netip.AddrPort{}, false
		}
		var ip [4]byte
		copy(ip[:], v[4:8])
		if xor {
			var c [4]byte
			binary.BigEndian.PutUint32(c[:], stunCookie)
			for i := range ip {
				ip[i] ^= c[i]
			}
		}
		return netip.AddrPortFrom(netip.AddrFrom4(ip), port), true
	case 2:
		if len(v) < 20 {
			return netip.AddrPort{}, false
		}
		var ip [16]byte
		copy(ip[:], v[4:20])
		if xor {
			var mask [16]byte
			binary.BigEndian.PutUint32(mask[:4], stunCookie)
			copy(mask[4:], tx[:])
			for i := range ip {
				ip[i] ^= mask[i]
			}
		}
		return netip.AddrPortFrom(netip.AddrFrom16(ip), port), true
	}
	return netip.AddrPort{}, false
}

// buildStunResponse is used by tests (and the simulated STUN server) to answer
// a binding request with the observed source address.
func buildStunResponse(req []byte, observed netip.AddrPort) []byte {
	var val []byte
	ip := observed.Addr().Unmap()
	port := observed.Port() ^ uint16(stunCookie>>16)
	if ip.Is4() {
		val = make([]byte, 8)
		val[1] = 1
		binary.BigEndian.PutUint16(val[2:], port)
		b := ip.As4()
		var c [4]byte
		binary.BigEndian.PutUint32(c[:], stunCookie)
		for i := range b {
			val[4+i] = b[i] ^ c[i]
		}
	} else {
		val = make([]byte, 20)
		val[1] = 2
		binary.BigEndian.PutUint16(val[2:], port)
		b := ip.As16()
		var mask [16]byte
		binary.BigEndian.PutUint32(mask[:4], stunCookie)
		copy(mask[4:], req[8:20])
		for i := range b {
			val[4+i] = b[i] ^ mask[i]
		}
	}
	out := make([]byte, 20+4+len(val))
	binary.BigEndian.PutUint16(out[0:], stunBindingResponse)
	binary.BigEndian.PutUint16(out[2:], uint16(4+len(val)))
	binary.BigEndian.PutUint32(out[4:], stunCookie)
	copy(out[8:20], req[8:20])
	binary.BigEndian.PutUint16(out[20:], attrXorMapped)
	binary.BigEndian.PutUint16(out[22:], uint16(len(val)))
	copy(out[24:], val)
	return out
}
