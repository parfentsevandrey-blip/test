package mesh

import (
	"crypto/ed25519"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

const stateVersion = 1

// stateFile is what is persisted in <dir>/mesh.json (0600: it may contain the
// authority seed on admin devices).
type stateFile struct {
	Version  int                   `json:"version"`
	MeshName string                `json:"mesh_name"`
	RootCert []byte                `json:"root_cert"`
	AuthSeed []byte                `json:"auth_seed,omitempty"`
	MyCert   []byte                `json:"my_cert"`
	Members  []memberRec           `json:"members,omitempty"`
	Revoked  []identity.Revocation `json:"revoked,omitempty"`
	Invites  []inviteRec           `json:"invites,omitempty"`
}

type memberRec struct {
	Cert      []byte   `json:"cert"`
	Alias     string   `json:"alias,omitempty"`
	Endpoints []string `json:"endpoints,omitempty"`
	LastSeen  int64    `json:"last_seen,omitempty"`
}

type inviteRec struct {
	Secret  []byte `json:"secret"`
	Expires int64  `json:"expires"`
	Admin   bool   `json:"admin"`
	Owner   string `json:"owner,omitempty"`
	Created int64  `json:"created"`
	Fails   int    `json:"fails,omitempty"`
	Code    string `json:"code,omitempty"`
}

func (n *Node) statePath() string { return filepath.Join(n.cfg.Dir, "mesh.json") }

// loadState reads mesh.json. A missing file means "not part of any mesh yet".
func (n *Node) loadState() error {
	raw, err := os.ReadFile(n.statePath())
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	var sf stateFile
	if err := json.Unmarshal(raw, &sf); err != nil {
		return fmt.Errorf("mesh: %s is corrupt: %w", n.statePath(), err)
	}
	if sf.Version > stateVersion {
		return fmt.Errorf("mesh: %s was written by a newer version", n.statePath())
	}
	root, err := identity.ParseRoot(sf.RootCert)
	if err != nil {
		return err
	}
	self, err := root.Verify(sf.MyCert)
	if err != nil {
		return fmt.Errorf("mesh: own certificate invalid: %w", err)
	}
	if self.ID != n.device().ID {
		return errors.New("mesh: state belongs to a different device key")
	}
	n.root = root
	n.meshName = sf.MeshName
	n.self = self
	if len(sf.AuthSeed) > 0 {
		auth, err := identity.AuthorityFromSeed(sf.AuthSeed, sf.RootCert)
		if err != nil {
			return err
		}
		n.auth = auth
	}
	for _, rv := range sf.Revoked {
		if root.VerifyRevocation(rv) == nil {
			n.revoked[rv.ID] = rv
		}
	}
	for _, rec := range sf.Members {
		m, err := root.Verify(rec.Cert)
		if err != nil || m.ID == n.device().ID {
			continue
		}
		if _, rev := n.revoked[m.ID]; rev {
			continue
		}
		p := newPeer(n, m)
		p.alias = rec.Alias
		p.storedEndpoints = rec.Endpoints
		if rec.LastSeen > 0 {
			p.lastSeen.Store(rec.LastSeen)
		}
		n.peers[m.ID] = p
	}
	for _, ir := range sf.Invites {
		if time.Now().Unix() >= ir.Expires {
			continue
		}
		inv := &invite{secret: ir.Secret, expires: time.Unix(ir.Expires, 0), admin: ir.Admin, owner: ir.Owner, created: time.Unix(ir.Created, 0), fails: ir.Fails, code: ir.Code}
		var h [8]byte
		i := identity.Invite{}
		copy(i.Secret[:], ir.Secret)
		h = i.Handle()
		n.invites[h] = inv
	}
	return nil
}

// saveState writes mesh.json atomically.
func (n *Node) saveState() error {
	n.mu.RLock()
	if n.root == nil {
		n.mu.RUnlock()
		return nil
	}
	sf := stateFile{
		Version:  stateVersion,
		MeshName: n.meshName,
		RootCert: n.root.DER,
		MyCert:   n.self.CertDER,
	}
	if n.auth != nil {
		sf.AuthSeed = n.auth.Priv.Seed()
	}
	for _, p := range n.peers {
		p.mu.RLock()
		rec := memberRec{Cert: p.member.CertDER, Alias: p.alias, LastSeen: p.lastSeen.Load()}
		p.mu.RUnlock()
		rec.Endpoints = n.endpointsFor(p)
		sf.Members = append(sf.Members, rec)
	}
	sort.Slice(sf.Members, func(i, j int) bool { return string(sf.Members[i].Cert) < string(sf.Members[j].Cert) })
	for _, rv := range n.revoked {
		sf.Revoked = append(sf.Revoked, rv)
	}
	sort.Slice(sf.Revoked, func(i, j int) bool { return sf.Revoked[i].At < sf.Revoked[j].At })
	for _, inv := range n.invites {
		sf.Invites = append(sf.Invites, inviteRec{
			Secret: inv.secret, Expires: inv.expires.Unix(), Admin: inv.admin, Owner: inv.owner, Created: inv.created.Unix(), Fails: inv.fails, Code: inv.code,
		})
	}
	n.mu.RUnlock()
	raw, err := json.MarshalIndent(sf, "", " ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(n.cfg.Dir, 0o700); err != nil {
		return err
	}
	return identity.WriteFileAtomic(n.statePath(), raw, 0o600)
}

// saveNow writes mesh.json before it returns, for the changes that must survive a crash a moment
// later: a member that has just joined, a revocation, the key of a new administrator, an invitation
// that has just been handed out. (A device that lost its newest member to a crash inside the
// debounce below would not know it again after a restart, and that member's old link would sit
// unnoticed until it timed out.)
func (n *Node) saveNow() {
	n.saveMu.Lock()
	if n.saveTimer != nil {
		n.saveTimer.Stop()
		n.saveTimer = nil
	}
	n.saveMu.Unlock()
	if err := n.saveState(); err != nil {
		n.log.Error("saving state failed", "err", err)
	}
}

// saveSoon schedules a debounced save.
func (n *Node) saveSoon() {
	n.saveMu.Lock()
	defer n.saveMu.Unlock()
	if n.saveTimer != nil {
		return
	}
	n.saveTimer = time.AfterFunc(500*time.Millisecond, func() {
		n.saveMu.Lock()
		n.saveTimer = nil
		n.saveMu.Unlock()
		if err := n.saveState(); err != nil {
			n.log.Error("saving state failed", "err", err)
		}
	})
}

type invite struct {
	secret  []byte
	expires time.Time
	admin   bool
	owner   string // label for the joining device, chosen by the inviter
	created time.Time
	fails   int
	code    string
}

func (i *invite) toIdentity(root ed25519.PublicKey, inviter identity.ID) *identity.Invite {
	inv := &identity.Invite{Root: root, Inviter: inviter, Expires: i.expires, Admin: i.admin}
	copy(inv.Secret[:], i.secret)
	return inv
}
