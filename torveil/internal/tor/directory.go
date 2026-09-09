package tor

import (
	"context"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"net"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Relay is one router status entry from the consensus, enriched with the
// country Tor's GeoIP database assigns to its address.
type Relay struct {
	Nickname    string
	Fingerprint string // 40 uppercase hex characters
	IP          string
	ORPort      int
	Flags       map[string]bool
	Bandwidth   int    // consensus weight, in KB/s
	ExitPolicy  string // summary from the "p" line
	Country     string // lowercase ISO 3166-1 alpha-2, or "" when unknown
	Family      []string
}

// HasFlag reports whether the consensus assigned the relay a flag.
func (r *Relay) HasFlag(f string) bool { return r.Flags[f] }

// IsUsable filters out relays the consensus says should not carry traffic.
func (r *Relay) IsUsable() bool {
	return r.HasFlag("Running") && r.HasFlag("Valid") && r.IP != ""
}

// IsGuard reports whether the relay is suitable as a first hop.
func (r *Relay) IsGuard() bool {
	return r.IsUsable() && r.HasFlag("Guard") && r.HasFlag("Fast") && r.HasFlag("Stable")
}

// IsExit reports whether the relay may be used as a last hop.
func (r *Relay) IsExit() bool {
	return r.IsUsable() && r.HasFlag("Exit") && !r.HasFlag("BadExit")
}

// IsMiddle reports whether the relay is suitable as an intermediate hop.
func (r *Relay) IsMiddle() bool {
	return r.IsUsable() && r.HasFlag("Fast")
}

// AllowsPort evaluates the consensus exit policy summary for a port. The
// summary is either "accept <ports>" or "reject <ports>", where ports is a
// comma-separated list of numbers and ranges.
func (r *Relay) AllowsPort(port int) bool {
	verb, list, ok := strings.Cut(strings.TrimSpace(r.ExitPolicy), " ")
	if !ok {
		return false
	}
	listed := false
	for _, part := range strings.Split(list, ",") {
		lo, hi, found := strings.Cut(part, "-")
		low, err := strconv.Atoi(strings.TrimSpace(lo))
		if err != nil {
			continue
		}
		high := low
		if found {
			if h, err := strconv.Atoi(strings.TrimSpace(hi)); err == nil {
				high = h
			}
		}
		if port >= low && port <= high {
			listed = true
			break
		}
	}
	if strings.EqualFold(verb, "accept") {
		return listed
	}
	return !listed
}

// slash16 returns the /16 of the relay's address, which is the granularity
// Tor uses when it refuses to place two hops of one circuit too close
// together on the network.
func (r *Relay) slash16() string {
	ip := net.ParseIP(r.IP)
	if ip == nil {
		return ""
	}
	if v4 := ip.To4(); v4 != nil {
		return fmt.Sprintf("%d.%d", v4[0], v4[1])
	}
	return ip.Mask(net.CIDRMask(32, 128)).String()
}

// Directory is a snapshot of the consensus as Tor currently sees it.
type Directory struct {
	mu        sync.RWMutex
	relays    map[string]*Relay
	order     []*Relay // stable, bandwidth-descending
	fetchedAt time.Time
}

// NewDirectory returns an empty directory.
func NewDirectory() *Directory {
	return &Directory{relays: make(map[string]*Relay)}
}

// FetchedAt reports when the snapshot was last refreshed.
func (d *Directory) FetchedAt() time.Time {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return d.fetchedAt
}

// Len returns the number of relays in the snapshot.
func (d *Directory) Len() int {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return len(d.relays)
}

// Get returns a relay by fingerprint.
func (d *Directory) Get(fp string) (*Relay, bool) {
	d.mu.RLock()
	defer d.mu.RUnlock()
	r, ok := d.relays[strings.ToUpper(fp)]
	return r, ok
}

// All returns every relay, ordered by descending consensus weight.
func (d *Directory) All() []*Relay {
	d.mu.RLock()
	defer d.mu.RUnlock()
	return append([]*Relay(nil), d.order...)
}

// Refresh reloads the consensus over the control port and resolves the
// country of every relay.
func (d *Directory) Refresh(ctx context.Context, c *Conn) error {
	raw, err := c.GetInfoValue(ctx, "ns/all")
	if err != nil {
		return fmt.Errorf("fetch consensus: %w", err)
	}
	relays, err := parseNetworkStatus(raw)
	if err != nil {
		return err
	}
	if len(relays) == 0 {
		return fmt.Errorf("consensus contained no relays")
	}
	if err := resolveCountries(ctx, c, relays); err != nil {
		// Country data is a policy nicety, not a correctness requirement:
		// without it the UI simply cannot offer country pinning.
		return fmt.Errorf("resolve relay countries: %w", err)
	}

	order := make([]*Relay, 0, len(relays))
	byFP := make(map[string]*Relay, len(relays))
	for _, r := range relays {
		byFP[r.Fingerprint] = r
		order = append(order, r)
	}
	sort.Slice(order, func(i, j int) bool {
		if order[i].Bandwidth != order[j].Bandwidth {
			return order[i].Bandwidth > order[j].Bandwidth
		}
		return order[i].Fingerprint < order[j].Fingerprint
	})

	d.mu.Lock()
	d.relays, d.order, d.fetchedAt = byFP, order, time.Now()
	d.mu.Unlock()
	return nil
}

// parseNetworkStatus parses the router status entries returned by
// "GETINFO ns/all".
func parseNetworkStatus(raw string) ([]*Relay, error) {
	var (
		out     []*Relay
		current *Relay
	)
	for _, line := range strings.Split(raw, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		kw, rest, _ := strings.Cut(line, " ")
		switch kw {
		case "r":
			// r Nickname Base64Identity Base64Digest Date Time IP ORPort DirPort
			f := strings.Fields(rest)
			if len(f) < 8 {
				current = nil
				continue
			}
			fp, err := base64FingerprintToHex(f[1])
			if err != nil {
				current = nil
				continue
			}
			orPort, _ := strconv.Atoi(f[6])
			current = &Relay{
				Nickname:    f[0],
				Fingerprint: fp,
				IP:          f[5],
				ORPort:      orPort,
				Flags:       map[string]bool{},
			}
			out = append(out, current)
		case "s":
			if current == nil {
				continue
			}
			for _, flag := range strings.Fields(rest) {
				current.Flags[flag] = true
			}
		case "w":
			if current == nil {
				continue
			}
			for _, f := range strings.Fields(rest) {
				if k, v, ok := strings.Cut(f, "="); ok && k == "Bandwidth" {
					current.Bandwidth, _ = strconv.Atoi(v)
				}
			}
		case "p":
			if current == nil {
				continue
			}
			current.ExitPolicy = rest
		}
	}
	return out, nil
}

// base64FingerprintToHex converts the unpadded base64 identity digest used in
// the consensus into the uppercase hex form the control protocol expects.
func base64FingerprintToHex(b64 string) (string, error) {
	raw, err := base64.RawStdEncoding.DecodeString(strings.TrimRight(b64, "="))
	if err != nil {
		return "", err
	}
	if len(raw) != 20 {
		return "", fmt.Errorf("identity digest is %d bytes, want 20", len(raw))
	}
	return strings.ToUpper(hex.EncodeToString(raw)), nil
}

// countryBatchSize keeps each GETINFO line comfortably short; Tor accepts
// multiple keys per command, which turns ~8000 round-trips into ~130.
const countryBatchSize = 64

// resolveCountries fills in Relay.Country using Tor's own GeoIP database, so
// TorVeil and Tor always agree on which relay is in which country.
func resolveCountries(ctx context.Context, c *Conn, relays []*Relay) error {
	// One lookup per distinct address; large operators reuse addresses.
	byIP := make(map[string][]*Relay, len(relays))
	for _, r := range relays {
		if r.IP != "" {
			byIP[r.IP] = append(byIP[r.IP], r)
		}
	}
	ips := make([]string, 0, len(byIP))
	for ip := range byIP {
		ips = append(ips, ip)
	}

	for start := 0; start < len(ips); start += countryBatchSize {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		end := start + countryBatchSize
		if end > len(ips) {
			end = len(ips)
		}
		batch := ips[start:end]
		keys := make([]string, len(batch))
		for i, ip := range batch {
			keys[i] = "ip-to-country/" + ip
		}
		res, err := c.GetInfo(ctx, keys...)
		if err != nil {
			return err
		}
		for k, v := range res {
			ip := strings.TrimPrefix(k, "ip-to-country/")
			cc := strings.ToLower(strings.TrimSpace(v))
			if cc == "??" || cc == "" {
				continue
			}
			for _, r := range byIP[ip] {
				r.Country = cc
			}
		}
	}
	return nil
}

// CountryStat summarises how much of the network sits in one country, which
// is what the UI needs to offer a sensible country list.
type CountryStat struct {
	Code   string `json:"code"`
	Guards int    `json:"guards"`
	Exits  int    `json:"exits"`
	Relays int    `json:"relays"`
}

// Countries summarises relay counts per country, ordered by relay count.
func (d *Directory) Countries() []CountryStat {
	d.mu.RLock()
	defer d.mu.RUnlock()

	stats := make(map[string]*CountryStat)
	for _, r := range d.order {
		if r.Country == "" || !r.IsUsable() {
			continue
		}
		s := stats[r.Country]
		if s == nil {
			s = &CountryStat{Code: r.Country}
			stats[r.Country] = s
		}
		s.Relays++
		if r.IsGuard() {
			s.Guards++
		}
		if r.IsExit() {
			s.Exits++
		}
	}

	out := make([]CountryStat, 0, len(stats))
	for _, s := range stats {
		out = append(out, *s)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Relays != out[j].Relays {
			return out[i].Relays > out[j].Relays
		}
		return out[i].Code < out[j].Code
	})
	return out
}

// LoadFamilies fetches microdescriptors for the given fingerprints and records
// each relay's declared family.
//
// The consensus alone does not carry family information, and family is what
// stops a circuit from being routed through several relays run by the same
// operator. Fetching it for a handful of candidate hops is cheap; fetching it
// for the whole network is not, so the path builder loads it lazily.
func (d *Directory) LoadFamilies(ctx context.Context, c *Conn, fingerprints []string) error {
	var missing []string
	d.mu.RLock()
	for _, fp := range fingerprints {
		r, ok := d.relays[strings.ToUpper(fp)]
		if ok && r.Family == nil {
			missing = append(missing, r.Fingerprint)
		}
	}
	d.mu.RUnlock()
	if len(missing) == 0 {
		return nil
	}

	keys := make([]string, len(missing))
	for i, fp := range missing {
		keys[i] = "md/id/" + fp
	}
	res, err := c.GetInfo(ctx, keys...)
	if err != nil {
		return err
	}

	d.mu.Lock()
	defer d.mu.Unlock()
	for k, md := range res {
		fp := strings.ToUpper(strings.TrimPrefix(k, "md/id/"))
		r, ok := d.relays[fp]
		if !ok {
			continue
		}
		// Record an empty non-nil slice so a relay with no family is not
		// re-fetched on every path build.
		r.Family = parseFamily(md)
	}
	return nil
}

// parseFamily extracts the fingerprints listed on a microdescriptor's family
// line. Members are written as "$HEXFINGERPRINT"; bare nicknames are
// unverifiable and ignored.
func parseFamily(md string) []string {
	fam := []string{}
	for _, line := range strings.Split(md, "\n") {
		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line, "family ") {
			continue
		}
		for _, member := range strings.Fields(strings.TrimPrefix(line, "family ")) {
			member = strings.TrimPrefix(member, "$")
			if i := strings.IndexAny(member, "=~"); i > 0 {
				member = member[:i]
			}
			if isHexFingerprint(member) {
				fam = append(fam, strings.ToUpper(member))
			}
		}
	}
	return fam
}
