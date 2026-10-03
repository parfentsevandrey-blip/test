package app

import (
	"context"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
)

// peerExtras is what a peer offers *us*: its shared folders and TCP services.
// It is fetched over the mesh and cached, so the device list can show
// "2 folders, ssh" without a round trip on every refresh.
type peerExtras struct {
	Shares   int
	Services []services.RemoteService
	At       time.Time
}

type extrasCache struct {
	a  *App
	mu sync.Mutex
	m  map[identity.ID]*peerExtras
}

func newExtrasCache(a *App) *extrasCache {
	return &extrasCache{a: a, m: map[identity.ID]*peerExtras{}}
}

func (e *extrasCache) get(id identity.ID) peerExtras {
	e.mu.Lock()
	defer e.mu.Unlock()
	if x := e.m[id]; x != nil {
		return *x
	}
	return peerExtras{}
}

// refresh queries one peer (best effort).
func (e *extrasCache) refresh(ctx context.Context, p *mesh.Peer) {
	if !p.Online() {
		return
	}
	cctx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()
	x := &peerExtras{At: time.Now()}
	var shares []files.RemoteShare
	if err := p.Call(cctx, "files.shares", nil, &shares); err == nil {
		x.Shares = len(shares)
	}
	var svcs []services.RemoteService
	if err := p.Call(cctx, "svc.list", nil, &svcs); err == nil {
		x.Services = svcs
	}
	e.mu.Lock()
	old := e.m[p.ID]
	e.m[p.ID] = x
	e.mu.Unlock()
	if old == nil || old.Shares != x.Shares || len(old.Services) != len(x.Services) {
		e.a.peersChanged()
	}
}

// run refreshes extras when peers connect and periodically.
func (e *extrasCache) run(ctx context.Context) {
	events, cancel := e.a.node.Subscribe()
	defer cancel()
	tick := time.NewTicker(30 * time.Second)
	defer tick.Stop()
	seen := map[identity.ID]bool{}
	sweep := func(all bool) {
		for _, p := range e.a.node.Peers() {
			online := p.Online()
			if online && (all || !seen[p.ID]) {
				go e.refresh(ctx, p)
			}
			seen[p.ID] = online
		}
	}
	sweep(true)
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
			sweep(true)
		case ev := <-events:
			if ev.Kind == mesh.EvPeer || ev.Kind == mesh.EvMembers {
				sweep(false)
			}
		}
	}
}
