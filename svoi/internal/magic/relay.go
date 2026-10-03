package magic

import (
	"crypto/subtle"
	"net/netip"
	"time"
)

// Relaying: when two members cannot reach each other directly (for example
// both are behind symmetric NATs), a third member that has direct paths to both
// forwards their packets. The relay sees only ciphertext - the inner packets are
// QUIC (or sealed disco) traffic between the two ends - and it only forwards
// between authenticated members: every hop carries a MAC keyed by the
// Diffie-Hellman secret of that hop's two devices.

// sendRelayed wraps inner (a complete data or disco packet) for the relay `via`,
// addressed to dst. buf is scratch space with enough capacity.
func (c *Conn) sendRelayed(via, dst *peer, inner []byte, buf []byte) bool {
	now := time.Now()
	via.mu.Lock()
	addr, ok := via.directLocked(now, c.cfg.Timing.PathTTL)
	via.mu.Unlock()
	if !ok {
		return false
	}
	pkt := buf[:0]
	pkt = append(pkt, typeRelayOut)
	pkt = append(pkt, c.r8[:]...)
	pkt = append(pkt, dst.r8[:]...)
	mac := mac8(&via.macKey, pkt[:17], inner)
	pkt = append(pkt, mac[:]...)
	pkt = append(pkt, inner...)
	_, _ = c.sock.WriteToAddrPort(pkt, addr)
	return true
}

// handleRelayOut runs on the relay: forward a packet from one member to another.
func (c *Conn) handleRelayOut(pkt []byte) {
	if len(pkt) <= relayHeader || !c.cfg.AllowRelay() {
		return
	}
	src := c.peerByR8([8]byte(pkt[1:9]))
	dst := c.peerByR8([8]byte(pkt[9:17]))
	if src == nil || dst == nil || src == dst {
		return
	}
	inner := pkt[25:]
	want := mac8(&src.macKey, pkt[:17], inner)
	if subtle.ConstantTimeCompare(want[:], pkt[17:25]) != 1 {
		return
	}
	// Only data and disco packets may be relayed: no nested relaying, no abuse
	// of the relay as a general UDP reflector.
	if inner[0] != typeData && inner[0] != typeDisco {
		return
	}
	now := time.Now()
	dst.mu.Lock()
	addr, ok := dst.directLocked(now, c.cfg.Timing.PathTTL)
	dst.mu.Unlock()
	if !ok {
		return
	}
	bp := c.bufPool.Get().(*[]byte)
	defer c.bufPool.Put(bp)
	out := (*bp)[:0]
	out = append(out, typeRelayIn)
	out = append(out, c.r8[:]...)
	out = append(out, src.r8[:]...)
	mac := mac8(&dst.macKey, out[:17], inner)
	out = append(out, mac[:]...)
	out = append(out, inner...)
	_, _ = c.sock.WriteToAddrPort(out, addr)
	c.self.relayedPkts.Add(1)
	c.self.relayedBytes.Add(uint64(len(inner)))
}

// handleRelayIn runs on the destination: unwrap a packet a relay forwarded.
func (c *Conn) handleRelayIn(pkt []byte) {
	if len(pkt) <= relayHeader {
		return
	}
	relay := c.peerByR8([8]byte(pkt[1:9]))
	src := c.peerByR8([8]byte(pkt[9:17]))
	if relay == nil || src == nil {
		return
	}
	inner := pkt[25:]
	want := mac8(&relay.macKey, pkt[:17], inner)
	if subtle.ConstantTimeCompare(want[:], pkt[17:25]) != 1 {
		return
	}
	switch inner[0] {
	case typeData:
		if len(inner) <= dataHeader || [8]byte(inner[1:9]) != src.r8 {
			return
		}
		src.mu.Lock()
		src.relayOK = time.Now()
		src.mu.Unlock()
		c.deliver(src, inner[dataHeader:], true)
	case typeDisco:
		c.handleDisco(inner, netip.AddrPort{}, relay)
	}
}
