package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"text/tabwriter"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

const nearbyUsage = `themesh nearby                                      the devices around that this one can connect with, the invitations, the requests
themesh nearby look [--name NAME]                   follow the list as it changes; meanwhile a device that is not in a mesh
                                                    makes itself known under NAME (Ctrl-C to stop)
themesh nearby connect NAME [--name N] [--mesh M]   connect with the device nearby in a tap: ask it to add this one, invite it to
                                                    this device's mesh, or ask it to make a mesh together; then wait for the end
themesh nearby accept NAME [--name N] [--mesh M]    accept its invitation (or its request to make a mesh: this device makes it, called M)
themesh nearby decline NAME                         say no to its invitation
themesh nearby allow ID [--owner NAME]              add the device that asked (both screens show the same six digits)
themesh nearby deny ID                              refuse a request
`

// cmdNearby is the terminal side of "devices nearby": a server without a screen can be connected with the devices around
// the same way the interface does it.
func cmdNearby(args []string) error {
	sub := ""
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		sub, args = args[0], args[1:]
	}
	switch sub {
	case "":
		return nearbyList(args)
	case "look":
		return nearbyLook(args)
	case "connect", "join": // ("join" is the first version's word)
		return nearbyConnect(args, false)
	case "accept":
		return nearbyConnect(args, true)
	case "decline":
		return nearbyDecline(args)
	case "allow", "deny":
		return nearbyAnswer(sub == "allow", args)
	}
	return fmt.Errorf("unknown nearby command %q\n\n%s", sub, nearbyUsage)
}

func nearbyView(c *client) (app.NearbyView, error) {
	var v app.NearbyView
	err := c.get("/api/nearby", &v)
	return v, err
}

func kindText(kind, mesh string) string {
	if kind == "new" {
		return "not in a mesh"
	}
	return "mesh " + mesh
}

func printNearby(v app.NearbyView) {
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	if len(v.Devices) > 0 {
		fmt.Fprintln(tw, "NEARBY\tWHAT\tOS\tID")
		for _, d := range v.Devices {
			fmt.Fprintf(tw, "%s\t%s\t%s\t%s\n", d.Name, kindText(d.Kind, d.MeshName), d.OS, d.ID)
		}
	}
	if len(v.Invites) > 0 {
		fmt.Fprintln(tw, "\nINVITES THIS DEVICE\tTO\tID")
		for _, i := range v.Invites {
			to := "make a mesh together"
			if i.Kind == "invite" {
				to = "join mesh " + i.MeshName
			}
			fmt.Fprintf(tw, "%s\t%s\t%s\n", i.Name, to, i.ID)
		}
	}
	if len(v.Sent) > 0 {
		fmt.Fprintln(tw, "\nINVITED BY THIS DEVICE\tSTATE\tID")
		for _, s := range v.Sent {
			fmt.Fprintf(tw, "%s\t%s\t%s\n", s.Name, s.State, s.ID)
		}
	}
	if len(v.Requests) > 0 {
		fmt.Fprintln(tw, "\nASKS TO BE ADDED\tDIGITS\tID")
		for _, r := range v.Requests {
			fmt.Fprintf(tw, "%s\t%s\t%s\n", r.Name, r.Code, r.ID)
		}
	}
	tw.Flush()
	if len(v.Devices) == 0 && len(v.Invites) == 0 && len(v.Sent) == 0 && len(v.Requests) == 0 {
		fmt.Println("Nobody nearby right now (a device is listed while it is on the same network and can connect with this one;")
		fmt.Println("one that is not in a mesh, while its person looks at the list).")
	}
}

func nearbyList(args []string) error {
	c, _, err := clientFromFlags("nearby", args, nil)
	if err != nil {
		return err
	}
	v, err := nearbyView(c)
	if err != nil {
		return err
	}
	printNearby(v)
	return nil
}

// looker tells the node that the person looks at the list, every half a minute (that counts for a minute and a half).
type looker struct {
	c    *client
	name string
	last time.Time
}

func (l *looker) look() {
	if time.Since(l.last) >= 30*time.Second {
		l.last = time.Now()
		_ = l.c.post("/api/nearby/look", map[string]any{"name": l.name}, nil)
	}
}

func (l *looker) stop() { _ = l.c.post("/api/nearby/look", map[string]any{"on": false}, nil) }

func nearbyLook(args []string) error {
	var name *string
	c, _, err := clientFromFlags("nearby look", args, func(fs *flag.FlagSet) {
		name = fs.String("name", "", "how this device calls itself while it is not in a mesh (default: its host name)")
	})
	if err != nil {
		return err
	}
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()
	l := &looker{c: c, name: *name}
	defer l.stop()
	shown := ""
	for {
		l.look()
		v, err := nearbyView(c)
		if err != nil {
			return err
		}
		if s := fmt.Sprintf("%v|%v|%v|%v", v.Devices, v.Invites, v.Sent, v.Requests); s != shown {
			shown = s
			fmt.Printf("\n%s\n", time.Now().Format("15:04:05"))
			printNearby(v)
		}
		select {
		case <-ctx.Done():
			return nil
		case <-time.After(time.Second):
		}
	}
}

// findNearby finds the device called name (or with that id, or an id starting with it) among the devices nearby and the
// invitations to this device.
func findNearby(v app.NearbyView, name string) (string, error) {
	match := func(id, n string) bool {
		return strings.EqualFold(n, name) || id == name || (len(name) >= 6 && strings.HasPrefix(id, strings.ToLower(name)))
	}
	found := map[string]bool{}
	for _, i := range v.Invites {
		if match(i.ID, i.Name) {
			found[i.ID] = true
		}
	}
	for _, d := range v.Devices {
		if match(d.ID, d.Name) {
			found[d.ID] = true
		}
	}
	switch len(found) {
	case 0:
		return "", nil
	case 1:
		for id := range found {
			return id, nil
		}
	}
	return "", fmt.Errorf("%q is ambiguous: use the id from `themesh nearby`", name)
}

// nearbyConnect connects with a device nearby (or, accept: takes its invitation), and follows it to the end.
func nearbyConnect(args []string, accept bool) error {
	var deviceName, meshName, peerOwner *string
	cmd := "nearby connect"
	if accept {
		cmd = "nearby accept"
	}
	c, pos, err := clientFromFlags(cmd, args, func(fs *flag.FlagSet) {
		deviceName = fs.String("name", "", "the name this device takes in the mesh (default: its host name)")
		meshName = fs.String("mesh", "", "the name of the mesh, if this device makes one for the two (default: Home)")
		peerOwner = fs.String("owner", "", "whose device the other one is, if this one adds it (default: the same person's)")
	})
	if err != nil {
		return err
	}
	if len(pos) != 1 {
		return fmt.Errorf("usage: themesh %s NAME", cmd)
	}
	l := &looker{c: c, name: *deviceName}
	defer l.stop()
	l.look()

	// The device may need a moment to be heard.
	var id string
	for deadline := time.Now().Add(20 * time.Second); ; {
		v, err := nearbyView(c)
		if err != nil {
			return err
		}
		if id, err = findNearby(v, pos[0]); err != nil {
			return err
		}
		if id != "" || time.Now().After(deadline) {
			break
		}
		time.Sleep(500 * time.Millisecond)
	}
	if id == "" {
		return fmt.Errorf("no device called %q is nearby (see `themesh nearby`)", pos[0])
	}
	mesh := *meshName
	if mesh == "" {
		mesh = "Home"
	}
	body := map[string]any{"deviceName": *deviceName, "meshName": mesh, "peerOwner": *peerOwner}
	if accept {
		body["accept"] = true
		err = c.post("/api/nearby/invites/"+id, body, nil)
	} else {
		body["id"] = id
		err = c.post("/api/nearby/connect", body, nil)
	}
	if err != nil {
		return err
	}
	return followNearby(c, l, id)
}

// followNearby reports how connecting with the device id goes, until it ends: this device's own request to be added
// (v.Join), or the invitation it sent (v.Sent).
func followNearby(c *client, l *looker, id string) error {
	said := map[string]bool{}
	say := func(key, format string, a ...any) {
		if !said[key] {
			said[key] = true
			fmt.Printf(format+"\n", a...)
		}
	}
	for deadline := time.Now().Add(5 * time.Minute); time.Now().Before(deadline); {
		time.Sleep(400 * time.Millisecond)
		l.look()
		v, err := nearbyView(c)
		if err != nil {
			return err
		}
		if j := v.Join; j.Peer != nil && j.Peer.ID == id && j.State != "idle" {
			switch j.State {
			case "connecting":
				say("connecting", "Connecting to %s…", j.Peer.Name)
			case "waiting", "confirmed":
				say("digits", "The six digits: %s (the same on both screens)", j.Code)
				say("wait", "Waiting for the person at %q to add this device…", j.Peer.Name)
			case "joined":
				fmt.Println("Added: this device is part of the mesh now.")
				return nil
			case "denied", "failed", "canceled":
				_ = c.post("/api/nearby/cancel", struct{}{}, nil) // forget how it ended
				msg := j.Error
				if msg == "" {
					msg = j.State
				}
				return fmt.Errorf("not added: %s", msg)
			}
			continue
		}
		for _, s := range v.Sent {
			if s.ID != id {
				continue
			}
			switch s.State {
			case "sent":
				if s.Kind == "pair" {
					say("sent", "Asked %s to make a mesh with this device; waiting for the person there to accept…", s.Name)
				} else {
					say("sent", "Invited %s; waiting for the person there to accept…", s.Name)
				}
			case "joining":
				say("joining", "%s accepted; adding it…", s.Name)
			case "joined":
				fmt.Printf("%s is part of the mesh now.\n", s.Name)
				return nil
			case "declined", "expired", "failed":
				msg := s.State
				if s.Error != "" {
					msg += ": " + s.Error
				}
				return fmt.Errorf("%s did not join: %s", s.Name, msg)
			}
		}
	}
	return context.DeadlineExceeded
}

func nearbyDecline(args []string) error {
	c, pos, err := clientFromFlags("nearby decline", args, nil)
	if err != nil {
		return err
	}
	if len(pos) != 1 {
		return errors.New("usage: themesh nearby decline NAME")
	}
	v, err := nearbyView(c)
	if err != nil {
		return err
	}
	only := app.NearbyView{Invites: v.Invites}
	id, err := findNearby(only, pos[0])
	if err != nil {
		return err
	}
	if id == "" {
		return fmt.Errorf("no invitation from %q (see `themesh nearby`)", pos[0])
	}
	if err := c.post("/api/nearby/invites/"+id, map[string]any{"accept": false}, nil); err != nil {
		return err
	}
	fmt.Println("Declined.")
	return nil
}

func nearbyAnswer(approve bool, args []string) error {
	var owner *string
	c, pos, err := clientFromFlags("nearby", args, func(fs *flag.FlagSet) {
		owner = fs.String("owner", "", "whose device it is (default: the same person as this one)")
	})
	if err != nil {
		return err
	}
	if len(pos) != 1 {
		return errors.New("usage: themesh nearby allow|deny ID")
	}
	v, err := nearbyView(c)
	if err != nil {
		return err
	}
	var id string
	for _, r := range v.Requests {
		if r.ID == pos[0] || strings.HasPrefix(r.ID, pos[0]) {
			id = r.ID
		}
	}
	if id == "" {
		return fmt.Errorf("no such request: %s (see `themesh nearby`)", pos[0])
	}
	if err := c.post("/api/nearby/requests/"+id, map[string]any{"approve": approve, "owner": *owner}, nil); err != nil {
		return err
	}
	if approve {
		fmt.Println("Answered yes: the device is added.")
	} else {
		fmt.Println("Refused.")
	}
	return nil
}
