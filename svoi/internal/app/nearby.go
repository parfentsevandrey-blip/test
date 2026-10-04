package app

import (
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// NearbyView is what the interface shows about the devices around (see mesh/nearby.go): to a device that is not in a
// mesh, the devices that can add it and its own request; to an admin, the requests of devices that want to be added.
type NearbyView struct {
	// Visible: this device, when it is an admin, tells the devices around that it can add them (Settings).
	Visible  bool                  `json:"visible"`
	Devices  []mesh.NearbyDevice   `json:"devices"`
	Join     mesh.NearbyJoinStatus `json:"join"`
	Requests []mesh.NearbyRequest  `json:"requests"`
}

// Nearby returns the current picture.
func (a *App) Nearby() NearbyView {
	return NearbyView{
		Visible:  a.node.NearbyVisible(),
		Devices:  a.node.NearbyList(),
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

// NearbyConnect asks the device nearby with this id to add this one.
func (a *App) NearbyConnect(id, deviceName string) error {
	return a.node.StartNearbyJoin(id, deviceName)
}

// NearbyConfirm: the person says that the digits on both screens match.
func (a *App) NearbyConfirm() error { return a.node.ConfirmNearbyJoin() }

// NearbyCancel gives up the request of this device (or forgets how it ended).
func (a *App) NearbyCancel() { a.node.CancelNearbyJoin() }

// NearbyAnswer approves or refuses the request of a device nearby; owner is whose device it is (empty: this one's person).
func (a *App) NearbyAnswer(id string, approve bool, owner string) error {
	return a.node.AnswerNearbyRequest(id, approve, owner)
}
