package tor

import (
	"fmt"
	"strings"
)

// Transport selects how the client reaches the Tor network.
type Transport string

const (
	// TransportDirect connects to guards straight from the consensus.
	TransportDirect Transport = "direct"
	// TransportSnowflake tunnels through a WebRTC proxy pool, with the broker
	// reached over domain fronting. This is the default: it is the hardest of
	// the three to enumerate and block.
	TransportSnowflake Transport = "snowflake"
	// TransportObfs4 uses obfs4 bridges (lyrebird).
	TransportObfs4 Transport = "obfs4"
)

// snowflakeICE is the STUN server list Snowflake uses to find a path to a
// volunteer proxy.
const snowflakeICE = "stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478," +
	"stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478," +
	"stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478," +
	"stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478"

// DefaultSnowflakeBridges is the fallback bridge list, used only when no
// pt_config.json can be read.
//
// Prefer RecommendedBridges, which reads the list shipped beside the
// pluggable transports. These lines are not secret, but they are perishable:
// the broker URL, the fronting domains and the STUN servers all get rotated,
// and a stale line does not fail loudly — Snowflake simply never finds a
// proxy. Anything compiled in here is stale the moment the Tor Project
// changes it, which is why it is the last resort rather than the source of
// truth. The UI keeps them editable so fresh lines can be pasted in.
//
// The addresses are in 192.0.2.0/24, the documentation range: a Snowflake
// bridge has no fixed address, so the line carries a placeholder and the real
// rendezvous happens over WebRTC.
var DefaultSnowflakeBridges = []string{
	"snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
		"fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
		"url=https://1098762253.rsc.cdn77.org/ " +
		"fronts=app.datapacket.com,www.datapacket.com " +
		"ice=" + snowflakeICE + " " +
		"utls-imitate=hellorandomizedalpn",
	"snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA " +
		"fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA " +
		"url=https://1098762253.rsc.cdn77.org/ " +
		"fronts=app.datapacket.com,www.datapacket.com " +
		"ice=" + snowflakeICE + " " +
		"utls-imitate=hellorandomizedalpn",
}

// BridgeLine is a parsed "Bridge" torrc line.
type BridgeLine struct {
	Raw         string
	Transport   string
	Address     string
	Fingerprint string
}

// ParseBridgeLine extracts the transport, address and identity fingerprint
// from a torrc Bridge line. The fingerprint matters to TorVeil beyond torrc:
// it is the first hop's identity, so the circuit builder needs it to lay out
// an explicit multi-hop path that starts at the bridge.
func ParseBridgeLine(line string) (BridgeLine, error) {
	b := BridgeLine{Raw: strings.TrimSpace(line)}
	fields := strings.Fields(b.Raw)
	if len(fields) == 0 {
		return b, fmt.Errorf("empty bridge line")
	}

	i := 0
	// An optional leading transport name; otherwise the line starts with an
	// address, which always contains a colon.
	if !strings.Contains(fields[0], ":") && !isHexFingerprint(fields[0]) {
		b.Transport = fields[0]
		i++
	}
	if i < len(fields) {
		b.Address = fields[i]
		i++
	}
	if i < len(fields) && isHexFingerprint(fields[i]) {
		b.Fingerprint = strings.ToUpper(fields[i])
		i++
	}
	// Fall back to the "fingerprint=" k=v argument used by snowflake.
	if b.Fingerprint == "" {
		for _, f := range fields[i:] {
			if k, v, ok := strings.Cut(f, "="); ok && strings.EqualFold(k, "fingerprint") && isHexFingerprint(v) {
				b.Fingerprint = strings.ToUpper(v)
				break
			}
		}
	}
	if b.Address == "" {
		return b, fmt.Errorf("bridge line has no address: %q", line)
	}
	return b, nil
}

func isHexFingerprint(s string) bool {
	if len(s) != 40 {
		return false
	}
	for _, r := range s {
		switch {
		case r >= '0' && r <= '9':
		case r >= 'a' && r <= 'f':
		case r >= 'A' && r <= 'F':
		default:
			return false
		}
	}
	return true
}
