package veiltun

import (
	"encoding/binary"
	"net/netip"
	"strings"
	"sync"
	"time"
)

// bypass decides which destinations leave the device without entering the
// tunnel.
//
// This exists for a practical rather than a privacy reason: sites and banking
// apps hosted inside the user's own country routinely refuse connections that
// arrive from a Tor exit, so a tunnel that carries everything makes the phone
// less usable than no tunnel at all. Routing those destinations around the
// tunnel fixes that, at the cost of showing them to the local network — which
// is exactly the trade-off the user has to make consciously, so it is off
// unless asked for.
//
// The decision is made on the *name*, not the address, and it has to be,
// because tor's AutomapHostsOnResolve replaces every resolved address with a
// virtual one. A name that matches gets resolved on the real network instead of
// through tor, and the addresses that come back are remembered for as long as
// their TTL says, so the connection that follows can be dialled directly.
// Addresses nobody looked up are never dialled directly: an application that
// connects to a hard-coded address still goes through the tunnel.
type bypass struct {
	suffixes  []string
	resolvers []string

	mu    sync.RWMutex
	known map[netip.Addr]time.Time
}

func newBypass(suffixes, resolvers string) *bypass {
	b := &bypass{known: make(map[netip.Addr]time.Time)}
	for _, s := range strings.Split(suffixes, ",") {
		s = strings.ToLower(strings.TrimSpace(s))
		if s == "" {
			continue
		}
		if !strings.HasPrefix(s, ".") {
			s = "." + s
		}
		b.suffixes = append(b.suffixes, s)
	}
	for _, r := range strings.Split(resolvers, ",") {
		if r = strings.TrimSpace(r); r != "" {
			b.resolvers = append(b.resolvers, r)
		}
	}
	return b
}

func (b *bypass) enabled() bool {
	return b != nil && len(b.suffixes) > 0 && len(b.resolvers) > 0
}

// matchesName reports whether a queried name should skip the tunnel.
func (b *bypass) matchesName(name string) bool {
	if !b.enabled() {
		return false
	}
	name = "." + strings.ToLower(strings.TrimSuffix(name, "."))
	for _, suffix := range b.suffixes {
		if strings.HasSuffix(name, suffix) {
			return true
		}
	}
	return false
}

// shouldDialDirect reports whether an address came from a bypassed lookup and
// is still within the lifetime the answer claimed.
func (b *bypass) shouldDialDirect(addr netip.Addr) bool {
	if !b.enabled() {
		return false
	}
	b.mu.RLock()
	expiry, ok := b.known[addr]
	b.mu.RUnlock()
	if !ok {
		return false
	}
	if time.Now().After(expiry) {
		b.mu.Lock()
		delete(b.known, addr)
		b.mu.Unlock()
		return false
	}
	return true
}

func (b *bypass) remember(addr netip.Addr, ttl time.Duration) {
	if !addr.IsValid() {
		return
	}
	if ttl < minBypassTTL {
		ttl = minBypassTTL
	}
	if ttl > maxBypassTTL {
		ttl = maxBypassTTL
	}
	b.mu.Lock()
	b.known[addr.Unmap()] = time.Now().Add(ttl)
	b.mu.Unlock()
}

const (
	minBypassTTL = 60 * time.Second
	maxBypassTTL = 30 * time.Minute
)

// --- Just enough DNS wire format ----------------------------------------

// queryName returns the name from a query's question section.
//
// Only the question section is parsed, where names are never compressed, so
// this stays a short loop over length-prefixed labels.
func queryName(msg []byte) string {
	if len(msg) < 13 {
		return ""
	}
	if questions := binary.BigEndian.Uint16(msg[4:6]); questions < 1 {
		return ""
	}
	var out strings.Builder
	offset := 12
	for offset < len(msg) {
		length := int(msg[offset])
		if length == 0 {
			return out.String()
		}
		if length&0xC0 != 0 || offset+1+length > len(msg) {
			return ""
		}
		if out.Len() > 0 {
			out.WriteByte('.')
		}
		out.Write(msg[offset+1 : offset+1+length])
		offset += 1 + length
	}
	return ""
}

// answerAddresses pulls the A and AAAA records out of a response, so the
// addresses a bypassed name resolved to can be dialled directly later.
func answerAddresses(msg []byte) []addrWithTTL {
	if len(msg) < 12 {
		return nil
	}
	questions := int(binary.BigEndian.Uint16(msg[4:6]))
	answers := int(binary.BigEndian.Uint16(msg[6:8]))
	if answers == 0 {
		return nil
	}

	offset := 12
	for i := 0; i < questions; i++ {
		next, ok := skipName(msg, offset)
		if !ok || next+4 > len(msg) {
			return nil
		}
		offset = next + 4
	}

	var found []addrWithTTL
	for i := 0; i < answers; i++ {
		next, ok := skipName(msg, offset)
		if !ok || next+10 > len(msg) {
			return found
		}
		recordType := binary.BigEndian.Uint16(msg[next : next+2])
		ttl := binary.BigEndian.Uint32(msg[next+4 : next+8])
		length := int(binary.BigEndian.Uint16(msg[next+8 : next+10]))
		data := next + 10
		if data+length > len(msg) {
			return found
		}
		switch {
		case recordType == 1 && length == 4:
			if addr, ok := netip.AddrFromSlice(msg[data : data+4]); ok {
				found = append(found, addrWithTTL{addr, time.Duration(ttl) * time.Second})
			}
		case recordType == 28 && length == 16:
			if addr, ok := netip.AddrFromSlice(msg[data : data+16]); ok {
				found = append(found, addrWithTTL{addr, time.Duration(ttl) * time.Second})
			}
		}
		offset = data + length
	}
	return found
}

type addrWithTTL struct {
	addr netip.Addr
	ttl  time.Duration
}

// skipName walks past a name, following a compression pointer if it finds one.
func skipName(msg []byte, offset int) (int, bool) {
	for offset < len(msg) {
		length := int(msg[offset])
		switch {
		case length == 0:
			return offset + 1, true
		case length&0xC0 == 0xC0:
			// A pointer is two bytes and always ends the name.
			return offset + 2, offset+2 <= len(msg)
		case offset+1+length <= len(msg):
			offset += 1 + length
		default:
			return 0, false
		}
	}
	return 0, false
}
