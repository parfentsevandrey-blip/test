package mesh

import (
	"context"
	"encoding/json"
	"net/netip"
	"runtime"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

func osName() string   { return runtime.GOOS }
func archName() string { return runtime.GOARCH }

// AddHelloProvider lets an application publish a small piece of state in the
// hello message other devices see (for example the list of exposed services).
func (n *Node) AddHelloProvider(key string, f func() any) {
	n.hmu.Lock()
	n.helloProviders[key] = f
	n.hmu.Unlock()
}

func (n *Node) buildHello() Hello {
	n.mu.RLock()
	self := n.self
	started := n.started
	mg := n.magic
	n.mu.RUnlock()
	h := Hello{
		OS:      osName(),
		Arch:    archName(),
		Version: Version,
		Proto:   1,
		Started: started.Unix(),
		Now:     time.Now().UnixMilli(),
		Caps:    []string{"files", "mail", "chat", "tunnel"},
	}
	if self != nil {
		h.Name, h.Owner = self.Name, self.Owner
	}
	if !n.cfg.NoRelay {
		h.Caps = append(h.Caps, "relay")
	}
	if mg != nil {
		for _, e := range mg.Endpoints() {
			h.Endpoints = append(h.Endpoints, e.Addr.String())
		}
	}
	n.hmu.RLock()
	providers := make(map[string]func() any, len(n.helloProviders))
	for k, f := range n.helloProviders {
		providers[k] = f
	}
	n.hmu.RUnlock()
	if len(providers) > 0 {
		h.Extra = map[string]json.RawMessage{}
		for k, f := range providers {
			if b, err := json.Marshal(f()); err == nil {
				h.Extra[k] = b
			}
		}
	}
	return h
}

// syncMsg is exchanged in both directions by mesh.sync: it carries everything
// two members need to converge on the same membership view, and tells each
// other where third parties were last reachable.
type syncMsg struct {
	Hello   Hello                 `json:"hello"`
	Certs   [][]byte              `json:"certs,omitempty"`
	Revoked []identity.Revocation `json:"revoked,omitempty"`
	Hints   []endpointHint        `json:"hints,omitempty"`
}

type endpointHint struct {
	ID    identity.ID `json:"id"`
	Addrs []string    `json:"addrs"`
}

func (n *Node) buildSync(to *Peer) syncMsg {
	msg := syncMsg{Hello: n.buildHello()}
	n.mu.RLock()
	msg.Certs = append(msg.Certs, n.self.CertDER)
	for _, rv := range n.revoked {
		msg.Revoked = append(msg.Revoked, rv)
	}
	peers := make([]*Peer, 0, len(n.peers))
	for _, p := range n.peers {
		peers = append(peers, p)
	}
	mg := n.magic
	n.mu.RUnlock()
	for _, p := range peers {
		msg.Certs = append(msg.Certs, p.Member().CertDER)
		if p == to || mg == nil {
			continue
		}
		if eps := mg.KnownEndpoints(p.ID, 3); len(eps) > 0 {
			h := endpointHint{ID: p.ID}
			for _, ap := range eps {
				h.Addrs = append(h.Addrs, ap.String())
			}
			msg.Hints = append(msg.Hints, h)
		}
	}
	return msg
}

// applySync merges a sync message received from p.
func (n *Node) applySync(p *Peer, msg syncMsg) {
	p.mu.Lock()
	h := msg.Hello
	p.hello = &h
	p.helloAt = time.Now()
	p.mu.Unlock()
	p.touch()

	for _, rv := range msg.Revoked {
		n.applyRevocation(rv)
	}
	root := n.Root()
	if root != nil {
		for _, der := range msg.Certs {
			m, err := root.Verify(der)
			if err != nil {
				continue
			}
			n.learnMember(m)
		}
	}
	if mg := n.Magic(); mg != nil {
		for _, hint := range msg.Hints {
			var eps []netip.AddrPort
			for _, s := range hint.Addrs {
				if ap, err := netip.ParseAddrPort(s); err == nil {
					eps = append(eps, ap)
				}
			}
			if hint.ID != n.dev.ID {
				mg.AddCandidates(hint.ID, eps, magic.SrcGossip)
			}
		}
		var eps []netip.AddrPort
		for _, s := range msg.Hello.Endpoints {
			if ap, err := netip.ParseAddrPort(s); err == nil {
				eps = append(eps, ap)
			}
		}
		mg.AddCandidates(p.ID, eps, magic.SrcGossip)
	}
	n.emit(Event{Kind: EvPeer, Peer: p.ID})
}

func (n *Node) registerCoreHandlers() {
	n.Handle("mesh.sync", func(ctx context.Context, c *Call) (any, error) {
		var req syncMsg
		if err := c.Decode(&req); err != nil {
			return nil, err
		}
		n.applySync(c.Peer, req)
		return n.buildSync(c.Peer), nil
	})
	n.Handle("mesh.ping", func(ctx context.Context, c *Call) (any, error) {
		return map[string]int64{"t": time.Now().UnixMilli()}, nil
	})
}

// syncWith exchanges membership and hello with one connected peer.
func (n *Node) syncWith(p *Peer) {
	ctx, cancel := context.WithTimeout(n.ctxOrBackground(), 20*time.Second)
	defer cancel()
	var resp syncMsg
	if err := p.Call(ctx, "mesh.sync", n.buildSync(p), &resp); err != nil {
		n.log.Debug("sync failed", "peer", p.Name(), "err", err)
		return
	}
	n.applySync(p, resp)
}

func (n *Node) ctxOrBackground() context.Context {
	n.mu.RLock()
	defer n.mu.RUnlock()
	if n.ctx != nil {
		return n.ctx
	}
	return context.Background()
}

// pushSyncToAll syncs with every online peer (after a membership change).
func (n *Node) pushSyncToAll() {
	var wg sync.WaitGroup
	for _, p := range n.peerList() {
		if p.Online() {
			wg.Add(1)
			go func() {
				defer wg.Done()
				n.syncWith(p)
			}()
		}
	}
	wg.Wait()
}

// Ping measures the application-level round trip to a peer.
func (p *Peer) Ping(ctx context.Context) (time.Duration, error) {
	start := time.Now()
	var out map[string]int64
	if err := p.Call(ctx, "mesh.ping", nil, &out); err != nil {
		return 0, err
	}
	return time.Since(start), nil
}
