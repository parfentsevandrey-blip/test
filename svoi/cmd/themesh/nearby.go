package main

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"strings"
	"text/tabwriter"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

const nearbyUsage = `themesh nearby                          devices nearby that can add this one (and, on an admin, the requests of devices that want to be added)
themesh nearby join NAME [--name NAME]   ask the device nearby to add this one; compare the six digits it shows with the ones here
themesh nearby allow ID [--owner NAME]   (admin) add the device that asked, once the digits on both screens are the same
themesh nearby deny ID                   (admin) refuse a request
`

// cmdNearby is the terminal side of "devices nearby": a server without a screen can be added to a mesh, and can add a
// device, the same way the interface does it.
func cmdNearby(args []string) error {
	sub := ""
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		sub, args = args[0], args[1:]
	}
	switch sub {
	case "":
		return nearbyList(args)
	case "join":
		return nearbyJoin(args)
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

func nearbyList(args []string) error {
	c, _, err := clientFromFlags("nearby", args, nil)
	if err != nil {
		return err
	}
	v, err := nearbyView(c)
	if err != nil {
		return err
	}
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	if len(v.Devices) > 0 {
		fmt.Fprintln(tw, "NEARBY\tMESH\tOS\tID")
		for _, d := range v.Devices {
			fmt.Fprintf(tw, "%s\t%s\t%s\t%s\n", d.Name, d.MeshName, d.OS, d.ID)
		}
	}
	if len(v.Requests) > 0 {
		fmt.Fprintln(tw, "\nASKS TO BE ADDED\tDIGITS\tCONFIRMED THERE\tID")
		for _, r := range v.Requests {
			fmt.Fprintf(tw, "%s\t%s\t%v\t%s\n", r.Name, r.Code, r.Confirmed, r.ID)
		}
	}
	tw.Flush()
	if len(v.Devices) == 0 && len(v.Requests) == 0 {
		fmt.Println("Nobody nearby right now (a device is listed while it can add this one and is on the same network).")
	}
	return nil
}

func nearbyJoin(args []string) error {
	var deviceName *string
	c, pos, err := clientFromFlags("nearby join", args, func(fs *flag.FlagSet) {
		deviceName = fs.String("name", "", "the name this device takes in the mesh (default: its host name)")
	})
	if err != nil {
		return err
	}
	if len(pos) != 1 {
		return errors.New("usage: themesh nearby join NAME")
	}
	v, err := nearbyView(c)
	if err != nil {
		return err
	}
	var id string
	for _, d := range v.Devices {
		if strings.EqualFold(d.Name, pos[0]) || d.ID == pos[0] || (len(pos[0]) >= 6 && strings.HasPrefix(d.ID, strings.ToLower(pos[0]))) {
			if id != "" {
				return fmt.Errorf("%q is ambiguous: use the id from `themesh nearby`", pos[0])
			}
			id = d.ID
		}
	}
	if id == "" {
		return fmt.Errorf("no device called %q is nearby (see `themesh nearby`)", pos[0])
	}
	if err := c.post("/api/nearby/connect", map[string]string{"id": id, "deviceName": *deviceName}, nil); err != nil {
		return err
	}
	deadline := time.Now().Add(5 * time.Minute)
	asked, confirmed := false, false
	answers := make(chan string, 1) // what the person types; read apart from the polling, so that a refusal is noticed at once
	for time.Now().Before(deadline) {
		time.Sleep(400 * time.Millisecond)
		v, err := nearbyView(c)
		if err != nil {
			return err
		}
		j := v.Join
		switch j.State {
		case "waiting":
			if !asked {
				asked = true
				who := ""
				if j.Peer != nil {
					who = j.Peer.Name
				}
				fmt.Printf("The six digits: %s\nThe device %q shows the same? Say yes only if they are the same. [y/N] ", j.Code, who)
				go func() {
					line, _ := bufio.NewReader(os.Stdin).ReadString('\n')
					answers <- line
				}()
			}
			select {
			case line := <-answers:
				if a := strings.ToLower(strings.TrimSpace(line)); a != "y" && a != "yes" {
					_ = c.post("/api/nearby/cancel", struct{}{}, nil)
					return errors.New("cancelled")
				}
				if err := c.post("/api/nearby/confirm", struct{}{}, nil); err != nil {
					return err
				}
				confirmed = true
				fmt.Println("Waiting for the person at the other device to add this one…")
			default:
			}
		case "confirmed":
			if !confirmed {
				confirmed = true
				fmt.Println("Waiting for the person at the other device to add this one…")
			}
		case "joined":
			fmt.Println("Added: this device is part of the mesh now.")
			return nil
		case "denied", "failed", "canceled":
			fmt.Println()
			_ = c.post("/api/nearby/cancel", struct{}{}, nil) // forget how it ended
			msg := j.Error
			if msg == "" {
				msg = j.State
			}
			return fmt.Errorf("not added: %s", msg)
		}
	}
	_ = c.post("/api/nearby/cancel", struct{}{}, nil)
	return context.DeadlineExceeded
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
		fmt.Println("Answered yes: the device is added once the person there says that the digits match.")
	} else {
		fmt.Println("Refused.")
	}
	return nil
}
