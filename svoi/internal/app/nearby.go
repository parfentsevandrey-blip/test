package app

import (
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// NearbyView is what the interface shows about the devices around (see mesh/nearby.go): the devices this one can connect
// with in a tap, the invitations to it and the ones it sent, its own request to be added, and the requests of devices
// that want to be added by it.
type NearbyView struct {
	// Visible: this device makes itself known nearby — that it can add devices (in a mesh), or its name while its person
	// looks at the list (not in a mesh) (Settings).
	Visible bool `json:"visible"`
	// Looking: the person looks at the list now (the interface says so, POST /api/nearby/look).
	Looking bool `json:"looking"`
	// CanAdd: this device is in a mesh and can add a device nearby to it now (an administrator; a member while an
	// administrator is online).
	CanAdd   bool                  `json:"canAdd"`
	Devices  []mesh.NearbyDevice   `json:"devices"`
	Invites  []mesh.NearbyInvite   `json:"invites"`
	Sent     []mesh.NearbyOutgoing `json:"sent"`
	Join     mesh.NearbyJoinStatus `json:"join"`
	Requests []mesh.NearbyRequest  `json:"requests"`
}

// Nearby returns the current picture.
func (a *App) Nearby() NearbyView {
	return NearbyView{
		Visible:  a.node.NearbyVisible(),
		Looking:  a.node.NearbyLooking(),
		CanAdd:   a.node.NearbyCanAdd(),
		Devices:  a.node.NearbyList(),
		Invites:  a.node.NearbyInvites(),
		Sent:     a.node.NearbySent(),
		Join:     a.node.NearbyJoinStatus(),
		Requests: a.node.NearbyRequests(),
	}
}

// publishNearby sends the picture to the interface (which opens the dialog for a request that is new; the shells of
// the desktop and the phone tell the person natively), and finishes what joining a mesh needs here once a request of
// this device has been granted.
func (a *App) publishNearby() {
	v := a.Nearby()
	a.hub.Publish("nearby", v)

	a.mu.Lock()
	joined := v.Join.State == "joined" && !a.nearbyJoined
	if joined {
		a.nearbyJoined = true
	} else if v.Join.State != "joined" {
		a.nearbyJoined = false
	}
	a.mu.Unlock()

	if joined {
		// Joined like with an invitation: the same things follow.
		a.applyTUN()
		a.peersChanged()
	}
}

// syncConfigured does what getting into a mesh needs here when the node got into one by itself — a device nearby that
// this device's person accepted made it, or this device made one for the two (mesh/nearbyinvite.go).
func (a *App) syncConfigured() {
	now := a.node.Configured()
	a.mu.Lock()
	changed := now != a.wasConfigured
	a.wasConfigured = now
	a.mu.Unlock()
	if changed && now {
		a.applyTUN()
	}
}

// onNearbyAdded tells the person at this administrator that a member added a device nearby (the member's person said yes
// to it; this device signed its certificate).
func (a *App) onNearbyAdded(ev mesh.Event) {
	name, by := ev.Peer.Short(), ev.By.Short()
	if p := a.node.Peer(ev.Peer); p != nil {
		name = p.Name()
	}
	if p := a.node.Peer(ev.By); p != nil {
		by = p.Name()
	}
	a.hub.Publish("notify", map[string]any{"level": "info", "title": name, "text": "added", "by": by, "link": "#/devices"})
}

// NearbyLook: the person looks at the list of devices nearby (on), or no longer does; name is how this device calls itself
// while it is not in a mesh (empty: as before).
func (a *App) NearbyLook(on bool, name string) { a.node.SetNearbyLooking(on, name) }

// NearbyConnect connects this device with the device nearby id in a tap (mesh.Node.ConnectNearby).
func (a *App) NearbyConnect(id string, o mesh.NearbyOptions) error {
	return a.node.ConnectNearby(id, o)
}

// NearbyInvite accepts or declines the invitation of the device id.
func (a *App) NearbyInvite(id string, accept bool, o mesh.NearbyOptions) error {
	if accept {
		return a.node.AcceptNearby(id, o)
	}
	return a.node.DeclineNearby(id)
}

// NearbyTakeBack takes back the invitation this device sent to the device id (or forgets how it ended).
func (a *App) NearbyTakeBack(id string) error { return a.node.CancelNearby(id) }

// NearbyConfirm: the person says that the digits on both screens match (a request made the first way, StartNearbyJoin).
func (a *App) NearbyConfirm() error { return a.node.ConfirmNearbyJoin() }

// NearbyCancel gives up the request of this device (or forgets how it ended).
func (a *App) NearbyCancel() { a.node.CancelNearbyJoin() }

// NearbyAnswer approves or refuses the request of a device nearby; owner is whose device it is (empty: this one's person).
func (a *App) NearbyAnswer(id string, approve bool, owner string) error {
	return a.node.AnswerNearbyRequest(id, approve, owner)
}
