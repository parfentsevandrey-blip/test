package magic

import (
	"net/netip"

	"github.com/parfentsevandrey-blip/test/svoi/internal/portmap"
)

// startPortMap asks the home router to forward our UDP port (see package
// portmap). It is best effort and runs in the background.
func (c *Conn) startPortMap() {
	c.portmap = portmap.Start(portmap.Config{
		Port: int(c.port),
		// Only addresses that can sit behind a home router are worth a search.
		LocalAddrs: func() []netip.Addr {
			var out []netip.Addr
			for _, a := range c.cfg.LocalAddrs() {
				if a = a.Unmap(); a.Is4() && a.IsPrivate() {
					out = append(out, a)
				}
			}
			return out
		},
		Logf:    c.cfg.Logf,
		Changed: c.setMapped,
	})
}

// setMapped records the address the router now forwards to us (nil: none).
func (c *Conn) setMapped(m *portmap.Mapping) {
	var ap netip.AddrPort
	if m != nil {
		ap = m.External
	}
	s := &c.self
	s.mu.Lock()
	changed := s.mapped != ap
	s.mapped = ap
	s.mu.Unlock()
	if changed {
		c.refreshEndpoints()
		c.Kick()
	}
}

// PortMapStatus reports what the router port mapping is doing (zero value if it
// is switched off).
func (c *Conn) PortMapStatus() (portmap.Status, bool) {
	if c.portmap == nil {
		return portmap.Status{}, false
	}
	return c.portmap.Status(), true
}
