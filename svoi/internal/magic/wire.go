package magic

import (
	"encoding/binary"
	"errors"
	"net/netip"
)

// Packet type bytes. They are all >= 0xA0 so they can never be confused with a
// STUN message (first byte < 0x40) or a BitTorrent DHT datagram ('d' = 0x64).
const (
	typeData     byte = 0xA1 // [type][src8][quic packet]
	typeRelayOut byte = 0xA2 // [type][src8][dst8][mac8][inner packet]   client -> relay
	typeRelayIn  byte = 0xA3 // [type][relay8][src8][mac8][inner packet] relay -> client
	typeAnon     byte = 0xA4 // [type][quic packet]                      joiner traffic, no membership yet
	typeDisco    byte = 0xA5 // [type][sender pub 32][nonce 24][sealed box]
)

const (
	dataHeader     = 1 + 8
	relayHeader    = 1 + 8 + 8 + 8
	discoHeaderLen = 1 + 32 + 24
	maxPacket      = 2048
)

// Disco message kinds (first byte of the decrypted payload).
const (
	msgPing        byte = 1
	msgPong        byte = 2
	msgCallMeMaybe byte = 3
	msgPeerState   byte = 4
)

// Flags in a ping.
const (
	pingFlagCert      byte = 1 // a member certificate follows
	pingFlagWantState byte = 2 // "send me your peer-state"
)

var errShort = errors.New("magic: truncated message")

type wbuf struct{ b []byte }

func (w *wbuf) u8(v byte)      { w.b = append(w.b, v) }
func (w *wbuf) bytes(v []byte) { w.b = append(w.b, v...) }
func (w *wbuf) u16(v uint16) {
	w.b = binary.BigEndian.AppendUint16(w.b, v)
}

// ap writes an address in the compact form [kind][ip][port]; kind 0 means "none".
func (w *wbuf) ap(a netip.AddrPort) {
	if !a.IsValid() {
		w.u8(0)
		return
	}
	ip := a.Addr().Unmap()
	if ip.Is4() {
		w.u8(4)
	} else {
		w.u8(6)
	}
	w.bytes(ip.AsSlice())
	w.u16(a.Port())
}

func (w *wbuf) aps(list []netip.AddrPort) {
	if len(list) > 24 {
		list = list[:24]
	}
	w.u8(byte(len(list)))
	for _, a := range list {
		w.ap(a)
	}
}

type rbuf struct {
	b   []byte
	err error
}

func (r *rbuf) take(n int) []byte {
	if r.err != nil || len(r.b) < n {
		r.err = errShort
		return make([]byte, n)
	}
	v := r.b[:n]
	r.b = r.b[n:]
	return v
}

func (r *rbuf) u8() byte    { return r.take(1)[0] }
func (r *rbuf) u16() uint16 { return binary.BigEndian.Uint16(r.take(2)) }

func (r *rbuf) ap() netip.AddrPort {
	switch r.u8() {
	case 0:
		return netip.AddrPort{}
	case 4:
		ip := netip.AddrFrom4([4]byte(r.take(4)))
		return netip.AddrPortFrom(ip, r.u16())
	case 6:
		ip := netip.AddrFrom16([16]byte(r.take(16)))
		return netip.AddrPortFrom(ip, r.u16())
	default:
		r.err = errors.New("magic: bad address kind")
		return netip.AddrPort{}
	}
}

func (r *rbuf) aps() []netip.AddrPort {
	n := int(r.u8())
	out := make([]netip.AddrPort, 0, n)
	for i := 0; i < n && r.err == nil; i++ {
		if a := r.ap(); a.IsValid() {
			out = append(out, a)
		}
	}
	return out
}
