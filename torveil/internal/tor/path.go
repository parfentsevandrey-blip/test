package tor

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"strings"
)

// Hop count bounds. Tor's own circuits are three hops; TorVeil allows extra
// middle hops so that no single relay-plus-observer pair sees both ends of the
// path, at the cost of latency.
const (
	MinHops     = 2
	MaxHops     = 5
	DefaultHops = 3
)

// PathPolicy describes the multi-hop path the user asked for.
type PathPolicy struct {
	// Hops is the total length of the circuit including entry and exit.
	Hops int

	// EntryCountry pins the first hop's country (lowercase ISO code). It is
	// ignored when a bridge is in use, because then the bridge *is* the first
	// hop.
	EntryCountry string

	// ExitCountry pins the last hop's country.
	ExitCountry string

	// ExcludeCountries bars every hop from these countries.
	ExcludeCountries []string

	// PinnedGuard is a fingerprint to reuse as the first hop across circuits.
	//
	// Tor pins guards for a reason: a client that picks a fresh entry relay
	// for every circuit eventually picks a hostile one, and each new guard is
	// another chance to be profiled. TorVeil keeps the same behaviour and only
	// re-rolls the guard when the user asks.
	PinnedGuard string

	// BridgeFingerprint fixes the first hop to a bridge (snowflake, obfs4).
	BridgeFingerprint string
}

// Normalize clamps the policy into the supported range.
func (p PathPolicy) Normalize() PathPolicy {
	if p.Hops < MinHops {
		p.Hops = DefaultHops
	}
	if p.Hops > MaxHops {
		p.Hops = MaxHops
	}
	p.EntryCountry = strings.ToLower(strings.TrimSpace(p.EntryCountry))
	p.ExitCountry = strings.ToLower(strings.TrimSpace(p.ExitCountry))
	for i, c := range p.ExcludeCountries {
		p.ExcludeCountries[i] = strings.ToLower(strings.TrimSpace(c))
	}
	p.PinnedGuard = strings.ToUpper(strings.TrimSpace(p.PinnedGuard))
	p.BridgeFingerprint = strings.ToUpper(strings.TrimSpace(p.BridgeFingerprint))
	return p
}

// UsesBridge reports whether the first hop is a bridge rather than a relay
// chosen from the consensus.
func (p PathPolicy) UsesBridge() bool { return p.BridgeFingerprint != "" }

func (p PathPolicy) excluded(country string) bool {
	if country == "" {
		return false
	}
	for _, c := range p.ExcludeCountries {
		if c != "" && c == country {
			return true
		}
	}
	return false
}

// Path is a selected circuit path. When the first hop is a bridge, Relays[0]
// is nil and BridgeFingerprint holds its identity.
type Path struct {
	Fingerprints []string
	Relays       []*Relay
}

// Describe renders the path for logs and for the UI's circuit view.
func (p Path) Describe() string {
	parts := make([]string, len(p.Fingerprints))
	for i, fp := range p.Fingerprints {
		if i < len(p.Relays) && p.Relays[i] != nil {
			r := p.Relays[i]
			cc := r.Country
			if cc == "" {
				cc = "??"
			}
			parts[i] = fmt.Sprintf("%s(%s)", r.Nickname, strings.ToUpper(cc))
			continue
		}
		parts[i] = "bridge(" + fp[:8] + ")"
	}
	return strings.Join(parts, " -> ")
}

// SelectPath chooses a circuit path satisfying the policy. targetPort is the
// destination port the circuit must be able to exit to; pass 0 to select an
// exit without a port constraint.
func (d *Directory) SelectPath(policy PathPolicy, targetPort int) (Path, error) {
	policy = policy.Normalize()
	relays := d.All()
	if len(relays) == 0 {
		return Path{}, fmt.Errorf("relay directory is empty; wait for the consensus to load")
	}

	var path Path
	used := make(map[string]bool)
	usedNets := make(map[string]bool)

	// Hop 1: a bridge if one is configured, otherwise a guard.
	if policy.UsesBridge() {
		path.Fingerprints = append(path.Fingerprints, policy.BridgeFingerprint)
		// The bridge may or may not appear in the consensus; record it if so,
		// purely so the UI can show a nickname.
		br, _ := d.Get(policy.BridgeFingerprint)
		path.Relays = append(path.Relays, br)
		used[policy.BridgeFingerprint] = true
		if br != nil {
			usedNets[br.slash16()] = true
		}
	} else {
		guard, err := d.selectGuard(policy, relays)
		if err != nil {
			return Path{}, err
		}
		path.Fingerprints = append(path.Fingerprints, guard.Fingerprint)
		path.Relays = append(path.Relays, guard)
		used[guard.Fingerprint] = true
		usedNets[guard.slash16()] = true
	}

	// Choose the exit next: it is the most constrained hop (country plus exit
	// policy), so picking it before the middles avoids painting ourselves into
	// a corner.
	exit, err := d.selectExit(policy, relays, targetPort, used, usedNets)
	if err != nil {
		return Path{}, err
	}

	// Middles fill the space between hop 1 and the exit.
	middles := make([]*Relay, 0, policy.Hops-2)
	for i := 0; i < policy.Hops-2; i++ {
		m, err := pickWeighted(relays, func(r *Relay) bool {
			return r.IsMiddle() &&
				!policy.excluded(r.Country) &&
				!used[r.Fingerprint] &&
				r.Fingerprint != exit.Fingerprint &&
				!usedNets[r.slash16()] &&
				r.slash16() != exit.slash16()
		})
		if err != nil {
			return Path{}, fmt.Errorf("select middle hop %d: %w", i+2, err)
		}
		used[m.Fingerprint] = true
		usedNets[m.slash16()] = true
		middles = append(middles, m)
	}

	for _, m := range middles {
		path.Fingerprints = append(path.Fingerprints, m.Fingerprint)
		path.Relays = append(path.Relays, m)
	}
	path.Fingerprints = append(path.Fingerprints, exit.Fingerprint)
	path.Relays = append(path.Relays, exit)
	return path, nil
}

// ChooseGuard picks the entry relay for a policy, honouring an existing pin.
//
// The engine calls this once per session so it can pin the same guard in Tor's
// own configuration as well. Tor builds circuits of its own — for onion
// service descriptors, and for TorVeil's cover-traffic channel — and if those
// left through a different guard than the user's real traffic, the cover
// traffic would be masking the wrong connection.
func (d *Directory) ChooseGuard(policy PathPolicy) (*Relay, error) {
	return d.selectGuard(policy.Normalize(), d.All())
}

func (d *Directory) selectGuard(policy PathPolicy, relays []*Relay) (*Relay, error) {
	if policy.PinnedGuard != "" {
		if r, ok := d.Get(policy.PinnedGuard); ok && r.IsGuard() {
			if policy.EntryCountry == "" || r.Country == policy.EntryCountry {
				return r, nil
			}
		}
		// The pinned guard left the consensus or no longer matches the
		// requested country; fall through and choose a new one.
	}
	g, err := pickWeighted(relays, func(r *Relay) bool {
		return r.IsGuard() &&
			!policy.excluded(r.Country) &&
			(policy.EntryCountry == "" || r.Country == policy.EntryCountry)
	})
	if err != nil {
		if policy.EntryCountry != "" {
			return nil, fmt.Errorf("no guard relay available in %q: %w", strings.ToUpper(policy.EntryCountry), err)
		}
		return nil, fmt.Errorf("select guard: %w", err)
	}
	return g, nil
}

func (d *Directory) selectExit(policy PathPolicy, relays []*Relay, port int, used map[string]bool, usedNets map[string]bool) (*Relay, error) {
	e, err := pickWeighted(relays, func(r *Relay) bool {
		return r.IsExit() &&
			!policy.excluded(r.Country) &&
			!used[r.Fingerprint] &&
			!usedNets[r.slash16()] &&
			(policy.ExitCountry == "" || r.Country == policy.ExitCountry) &&
			(port == 0 || r.AllowsPort(port))
	})
	if err != nil {
		switch {
		case policy.ExitCountry != "" && port != 0:
			return nil, fmt.Errorf("no exit relay in %q allows port %d: %w", strings.ToUpper(policy.ExitCountry), port, err)
		case policy.ExitCountry != "":
			return nil, fmt.Errorf("no exit relay available in %q: %w", strings.ToUpper(policy.ExitCountry), err)
		default:
			return nil, fmt.Errorf("select exit: %w", err)
		}
	}
	return e, nil
}

// FamilyConflict reports the first pair of hops that declare each other as
// family, which would put two hops of one circuit under one operator.
//
// Family data comes from microdescriptors, so the caller loads it for the
// candidate path with Directory.LoadFamilies before calling this.
func (d *Directory) FamilyConflict(path Path) (string, bool) {
	fams := make([]map[string]bool, len(path.Relays))
	for i, r := range path.Relays {
		set := map[string]bool{}
		if r != nil {
			for _, m := range r.Family {
				set[m] = true
			}
		}
		fams[i] = set
	}
	for i := 0; i < len(path.Fingerprints); i++ {
		for j := i + 1; j < len(path.Fingerprints); j++ {
			a, b := path.Fingerprints[i], path.Fingerprints[j]
			// Tor treats family as mutual: both sides must list the other.
			if fams[i][b] && fams[j][a] {
				return fmt.Sprintf("hops %d and %d are in the same relay family", i+1, j+1), true
			}
		}
	}
	return "", false
}

// SelectPathChecked selects a path and re-rolls it while any two hops turn out
// to belong to the same relay family.
func (d *Directory) SelectPathChecked(loadFamilies func([]string) error, policy PathPolicy, targetPort int) (Path, error) {
	const attempts = 8
	var lastReason string
	for i := 0; i < attempts; i++ {
		path, err := d.SelectPath(policy, targetPort)
		if err != nil {
			return Path{}, err
		}
		if loadFamilies != nil {
			if err := loadFamilies(path.Fingerprints); err != nil {
				// Without family data the /16 rule still applies; proceed
				// rather than refuse to build any circuit at all.
				return path, nil
			}
		}
		reason, conflict := d.FamilyConflict(path)
		if !conflict {
			return path, nil
		}
		lastReason = reason
	}
	return Path{}, fmt.Errorf("could not find a path without family overlap after %d attempts (%s)", attempts, lastReason)
}

// pickWeighted chooses one relay at random, weighted by consensus bandwidth,
// from those matching the predicate. Selection uses the cryptographic RNG:
// path choice is a security decision, not a cosmetic one.
func pickWeighted(relays []*Relay, ok func(*Relay) bool) (*Relay, error) {
	var (
		candidates []*Relay
		total      int64
	)
	for _, r := range relays {
		if ok(r) {
			candidates = append(candidates, r)
			total += int64(weightOf(r))
		}
	}
	if len(candidates) == 0 {
		return nil, fmt.Errorf("no relay matches the requested constraints")
	}
	if total <= 0 {
		i, err := randIndex(len(candidates))
		if err != nil {
			return nil, err
		}
		return candidates[i], nil
	}
	n, err := rand.Int(rand.Reader, big.NewInt(total))
	if err != nil {
		return nil, fmt.Errorf("random path selection: %w", err)
	}
	acc := n.Int64()
	for _, r := range candidates {
		acc -= int64(weightOf(r))
		if acc < 0 {
			return r, nil
		}
	}
	return candidates[len(candidates)-1], nil
}

func weightOf(r *Relay) int {
	if r.Bandwidth < 1 {
		return 1
	}
	return r.Bandwidth
}

func randIndex(n int) (int, error) {
	v, err := rand.Int(rand.Reader, big.NewInt(int64(n)))
	if err != nil {
		return 0, fmt.Errorf("random index: %w", err)
	}
	return int(v.Int64()), nil
}
