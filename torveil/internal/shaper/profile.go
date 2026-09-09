// Package shaper implements TorVeil's traffic-shaping layer: the part that
// tries to make the pattern of a user's traffic look less like itself.
//
// # What this can and cannot do
//
// Mullvad's DAITA works inside WireGuard, where the client owns both ends of
// the tunnel and can therefore pad any packet to a constant size and inject
// dummy packets that the server silently drops. Tor gives a client neither of
// those things: a stream to a website carries application bytes, and any byte
// injected into it would be delivered to the website. So the padding cannot
// live in the same stream as the data.
//
// TorVeil splits the job into three mechanisms, each with a different cost and
// a different honest claim:
//
//  1. Timing quantisation of real streams. Every shaped connection may only
//     hand bytes to Tor on a shared clock tick. That does not change how many
//     bytes are sent, but it destroys the fine-grained inter-packet timing
//     that website-fingerprinting classifiers rely on most, and it merges the
//     bursts of concurrent streams into one aggregate burst.
//
//  2. Tor's own padding. Link-level padding to the guard and the WTF-PAD
//     circuit padding machines are enabled at full strength (Tor ships them
//     reduced by default to save bandwidth). This is the cheapest real
//     padding available and it is designed for exactly this threat.
//
//  3. Cover traffic on a chaff channel. Dummy bytes need a destination that
//     discards them, so TorVeil hosts an ephemeral onion service pointed at a
//     local sink and sends the padding there. That traffic shares the single
//     TLS connection to the guard with the user's real traffic, so it masks
//     the volume and shape an observer between the user and the guard sees.
//
// The observer this defends against is the one on the user's own link: an ISP,
// a network operator, or anyone watching the guard connection. It does not
// hide anything from the exit relay, and it is not a defence against an
// adversary who can watch both ends of a circuit at once.
package shaper

import (
	"fmt"
	"strings"
	"time"
)

// ProfileID names a shaping profile.
type ProfileID string

// The four profiles offered in the UI.
const (
	ProfileOff      ProfileID = "off"
	ProfileLight    ProfileID = "light"
	ProfileBalanced ProfileID = "balanced"
	ProfileParanoid ProfileID = "paranoid"
)

// ChaffMode selects when cover traffic is generated.
type ChaffMode string

const (
	// ChaffOff generates no cover traffic.
	ChaffOff ChaffMode = "off"
	// ChaffBurst generates cover traffic only around real activity, so an
	// idle machine stays idle on the wire.
	ChaffBurst ChaffMode = "burst"
	// ChaffConstant keeps the aggregate send rate at a target level whenever
	// the tunnel is up, filling whatever the real traffic does not use.
	ChaffConstant ChaffMode = "constant"
)

// Profile is a complete shaping configuration.
type Profile struct {
	ID          ProfileID `json:"id"`
	Name        string    `json:"name"`
	Description string    `json:"description"`

	// Tick is the shared clock all shaped writes are aligned to. Zero
	// disables timing quantisation.
	Tick time.Duration `json:"-"`

	// Jitter randomises each tick by up to this much, so the grid itself does
	// not become a fingerprint.
	Jitter time.Duration `json:"-"`

	// ChaffMode selects the cover traffic strategy.
	ChaffMode ChaffMode `json:"chaffMode"`

	// ChaffRate is the ceiling on cover traffic in bytes per second, in one
	// direction. Cover traffic on an onion channel costs roughly six relay
	// hops per byte, so this is a real cost imposed on volunteer-run relays
	// and the ceilings are deliberately modest.
	ChaffRate int `json:"chaffRate"`

	// ChaffDownstreamRatio asks the sink to send back this multiple of the
	// upstream chaff. Real browsing is download-heavy, so cover traffic that
	// is upload-only would stand out.
	ChaffDownstreamRatio float64 `json:"chaffDownstreamRatio"`

	// ChaffIdleGrace is how long cover traffic keeps running after the last
	// real byte, in burst mode.
	ChaffIdleGrace time.Duration `json:"-"`

	// TorOptions are torrc settings applied alongside the profile.
	TorOptions map[string]string `json:"-"`

	// EstimatedOverhead is a human-readable summary shown in the UI.
	EstimatedOverhead string `json:"estimatedOverhead"`
}

// TickString renders the tick for display.
func (p Profile) TickString() string {
	if p.Tick == 0 {
		return "off"
	}
	return p.Tick.String()
}

// fullPadding turns on both of Tor's padding mechanisms at full strength.
//
// Tor ships ReducedConnectionPadding and ReducedCircuitPadding enabled on
// mobile-ish defaults to save bandwidth; a privacy client on a desktop link
// should pay the full cost.
func fullPadding() map[string]string {
	return map[string]string{
		"ConnectionPadding":        "1",
		"ReducedConnectionPadding": "0",
		"CircuitPadding":           "1",
		"ReducedCircuitPadding":    "0",
	}
}

// Profiles returns every profile in increasing order of protection and cost.
func Profiles() []Profile {
	return []Profile{
		{
			ID:          ProfileOff,
			Name:        "Off",
			Description: "No shaping. Tor's default padding only. Fastest, and the traffic pattern is whatever the applications produce.",
			ChaffMode:   ChaffOff,
			TorOptions: map[string]string{
				"ConnectionPadding": "1",
				"CircuitPadding":    "1",
			},
			EstimatedOverhead: "~0%",
		},
		{
			ID:                ProfileLight,
			Name:              "Light",
			Description:       "Tor's link and circuit padding at full strength, plus a 25 ms send grid. No cover traffic, so no extra load on the network.",
			Tick:              25 * time.Millisecond,
			Jitter:            5 * time.Millisecond,
			ChaffMode:         ChaffOff,
			TorOptions:        fullPadding(),
			EstimatedOverhead: "~2-5%, latency +0-25 ms",
		},
		{
			ID:                   ProfileBalanced,
			Name:                 "Balanced",
			Description:          "A 15 ms send grid plus cover traffic around real activity, which hides how much is being transferred and when a session starts and stops.",
			Tick:                 15 * time.Millisecond,
			Jitter:               4 * time.Millisecond,
			ChaffMode:            ChaffBurst,
			ChaffRate:            32 * 1024,
			ChaffDownstreamRatio: 3,
			ChaffIdleGrace:       20 * time.Second,
			TorOptions:           fullPadding(),
			EstimatedOverhead:    "~15-30%, latency +0-15 ms",
		},
		{
			ID:                   ProfileParanoid,
			Name:                 "Paranoid",
			Description:          "Constant-rate transmission: the aggregate send rate is held steady whenever the tunnel is up and cover traffic fills whatever the real traffic does not. Hides idle periods entirely, and costs the most bandwidth.",
			Tick:                 10 * time.Millisecond,
			Jitter:               2 * time.Millisecond,
			ChaffMode:            ChaffConstant,
			ChaffRate:            128 * 1024,
			ChaffDownstreamRatio: 3,
			TorOptions:           fullPadding(),
			EstimatedOverhead:    "constant load up to ~128 KiB/s up, latency +0-10 ms",
		},
	}
}

// ProfileByID looks a profile up by identifier.
func ProfileByID(id ProfileID) (Profile, error) {
	want := ProfileID(strings.ToLower(strings.TrimSpace(string(id))))
	for _, p := range Profiles() {
		if p.ID == want {
			return p, nil
		}
	}
	return Profile{}, fmt.Errorf("unknown shaping profile %q", id)
}

// MustProfile returns a profile by ID, falling back to Balanced.
func MustProfile(id ProfileID) Profile {
	p, err := ProfileByID(id)
	if err != nil {
		p, _ = ProfileByID(ProfileBalanced)
	}
	return p
}
