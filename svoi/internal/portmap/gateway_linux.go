//go:build linux

package portmap

import (
	"bufio"
	"encoding/binary"
	"net/netip"
	"os"
	"strconv"
	"strings"
)

// defaultGateway reads the IPv4 default route from /proc/net/route.
func defaultGateway() netip.Addr {
	f, err := os.Open("/proc/net/route")
	if err != nil {
		return netip.Addr{}
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	sc.Scan() // header
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 4 || fields[1] != "00000000" {
			continue
		}
		flags, _ := strconv.ParseUint(fields[3], 16, 32)
		if flags&0x3 != 0x3 { // up and a gateway
			continue
		}
		gw, err := strconv.ParseUint(fields[2], 16, 32)
		if err != nil || gw == 0 {
			continue
		}
		var b [4]byte
		binary.LittleEndian.PutUint32(b[:], uint32(gw))
		return netip.AddrFrom4(b)
	}
	return netip.Addr{}
}
