//go:build !linux

package portmap

import "net/netip"

// defaultGateway: only Linux reads the routing table here; elsewhere NAT-PMP is
// tried at the address of the router that answered the UPnP search, if any.
func defaultGateway() netip.Addr { return netip.Addr{} }
