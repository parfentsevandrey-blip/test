package veiltun

// Blocked UDP is refused, not swallowed.
//
// Tor carries no UDP, so everything but DNS is turned away. How it is turned
// away matters more than it looks. A datagram that simply vanishes leaves the
// application waiting: a browser that opened with QUIC on 443 sits on a
// handshake that will never be answered until its own timer gives up, and
// only then tries TCP. Chrome races the two and loses a few hundred
// milliseconds; libraries that try QUIC first and TCP second lose seconds,
// per host, on the first page.
//
// The network already has a word for this — ICMP "port unreachable" — and an
// application that receives it falls back at once, the way it would on any
// network where the port is closed. So instead of accepting the datagram and
// dropping it, the UDP handler declines it, and the stack answers with the
// ICMP error. Same leak guard, same counter, one round trip instead of a
// timeout.

import (
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

type gatedUDPConn struct {
	*gonet.UDPConn
	id stack.TransportEndpointID
}

func (c *gatedUDPConn) ID() stack.TransportEndpointID { return c.id }

// installUDPGate replaces the stack's UDP handler with one that refuses what
// the leak guard would have dropped, so that the stack sends ICMP port
// unreachable for it, and forwards the rest exactly as before.
func installUDPGate(st *stack.Stack, h *handler) {
	forward := udp.NewForwarder(st, func(r *udp.ForwarderRequest) bool {
		var wq waiter.Queue
		id := r.ID()
		ep, err := r.CreateEndpoint(&wq)
		if err != nil {
			return false
		}
		h.HandleUDP(&gatedUDPConn{UDPConn: gonet.NewUDPConn(&wq, ep), id: id})
		return true
	})
	st.SetTransportProtocolHandler(udp.ProtocolNumber,
		func(id stack.TransportEndpointID, pkt *stack.PacketBuffer) bool {
			if h.refusesUDP(id) {
				stats.blocked.Add(1)
				// Unhandled: the stack answers with ICMP port unreachable.
				return false
			}
			return forward.HandlePacket(id, pkt)
		})
}

// refusesUDP is the leak guard's decision for one flow: everything that is
// neither DNS nor bound for an address a bypassed name resolved to.
func (h *handler) refusesUDP(id stack.TransportEndpointID) bool {
	if !h.cfg.BlockUDP || id.LocalPort == 53 {
		return false
	}
	return !h.bypass.shouldDialDirect(addrFrom(id.LocalAddress))
}
