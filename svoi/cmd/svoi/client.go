package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"text/tabwriter"
	"time"

	qrcode "github.com/skip2/go-qrcode"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

// client talks to the running node's local API (the same one the browser uses).
type client struct {
	base  string
	token string
	http  *http.Client
}

func newClient(dir string) (*client, error) {
	addr, err := os.ReadFile(filepath.Join(dir, "ui.addr"))
	if err != nil {
		return nil, errors.New("svoi is not running (start it with `svoi up`)")
	}
	tok, err := os.ReadFile(filepath.Join(dir, "ui.token"))
	if err != nil {
		return nil, err
	}
	return &client{
		base:  "http://" + strings.TrimSpace(string(addr)),
		token: strings.TrimSpace(string(tok)),
		http:  &http.Client{Timeout: 0},
	}, nil
}

func (c *client) do(ctx context.Context, method, path string, body io.Reader, out any) error {
	req, err := http.NewRequestWithContext(ctx, method, c.base+path, body)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return errors.New("cannot reach the running svoi: " + err.Error())
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		var e struct {
			Error struct{ Code, Message string } `json:"error"`
		}
		if json.Unmarshal(data, &e) == nil && e.Error.Message != "" {
			return errors.New(e.Error.Message)
		}
		return fmt.Errorf("%s", resp.Status)
	}
	if out != nil {
		return json.Unmarshal(data, out)
	}
	return nil
}

func (c *client) post(path string, in, out any) error {
	b, _ := json.Marshal(in)
	return c.do(context.Background(), http.MethodPost, path, bytes.NewReader(b), out)
}

func (c *client) get(path string, out any) error {
	return c.do(context.Background(), http.MethodGet, path, nil, out)
}

func clientFromFlags(name string, args []string, extra func(*flag.FlagSet)) (*client, *flag.FlagSet, error) {
	fs := flag.NewFlagSet(name, flag.ExitOnError)
	var cf commonFlags
	cf.register(fs)
	if extra != nil {
		extra(fs)
	}
	fs.Parse(args)
	c, err := newClient(cf.dir)
	return c, fs, err
}

func cmdStatus(args []string) error {
	c, _, err := clientFromFlags("status", args, nil)
	if err != nil {
		return err
	}
	var st app.State
	if err := c.get("/api/state", &st); err != nil {
		return err
	}
	if !st.Configured {
		fmt.Println("This device is not part of a mesh yet. Open the web interface (`svoi open`) to create or join one.")
		return nil
	}
	role := ""
	if st.Self.Admin {
		role = "  [admin]"
	}
	fmt.Printf("This device: %s (%s)  %s%s\n", st.Self.Name, st.Self.Owner, st.Self.IP4, role)
	fmt.Printf("Mesh:        %s (%s)\n", st.Self.MeshName, st.Self.MeshID)
	fmt.Printf("Network:     %s", st.Self.NAT.Difficulty)
	if len(st.Self.NAT.Public) > 0 {
		fmt.Printf("  public %s", strings.Join(st.Self.NAT.Public, ", "))
	}
	fmt.Println()
	peers := st.Peers
	sort.Slice(peers, func(i, j int) bool {
		if peers[i].Online != peers[j].Online {
			return peers[i].Online
		}
		return peers[i].Name < peers[j].Name
	})
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	fmt.Fprintln(tw, "\nDEVICE\tSTATE\tPATH\tRTT\tADDRESS\tOS")
	for _, p := range peers {
		state, path, rtt := "offline", "-", "-"
		if p.Online {
			state = "online"
			path = p.Path
			if p.RelayVia != "" {
				path += " via " + p.RelayVia
			}
			if p.RTTms > 0 {
				rtt = fmt.Sprintf("%.1f ms", p.RTTms)
			}
		} else if p.LastSeen > 0 {
			state = "seen " + ago(time.Unix(p.LastSeen, 0))
		}
		fmt.Fprintf(tw, "%s\t%s\t%s\t%s\t%s\t%s\n", p.Name, state, path, rtt, p.IP4, joinNonEmpty("/", p.OS, p.Arch))
	}
	tw.Flush()
	return nil
}

func ago(t time.Time) string {
	d := time.Since(t)
	switch {
	case d < time.Minute:
		return "just now"
	case d < time.Hour:
		return fmt.Sprintf("%d min ago", int(d.Minutes()))
	case d < 48*time.Hour:
		return fmt.Sprintf("%d h ago", int(d.Hours()))
	}
	return fmt.Sprintf("%d days ago", int(d.Hours()/24))
}

func cmdInvite(args []string) error {
	var admin *bool
	var ttl *time.Duration
	c, _, err := clientFromFlags("invite", args, func(fs *flag.FlagSet) {
		admin = fs.Bool("admin", false, "make the new device an administrator (gives it the mesh key)")
		ttl = fs.Duration("ttl", 30*time.Minute, "how long the invitation stays valid")
	})
	if err != nil {
		return err
	}
	var inv app.InviteView
	if err := c.post("/api/invites", map[string]any{"admin": *admin, "ttlMinutes": int(ttl.Minutes())}, &inv); err != nil {
		return err
	}
	if q, err := qrcode.New(inv.Code, qrcode.Medium); err == nil {
		fmt.Println(q.ToSmallString(false))
	}
	fmt.Println("Invitation code (valid until " + time.Unix(inv.Expires, 0).Format("15:04") + "):")
	fmt.Println()
	fmt.Println(inv.Code)
	fmt.Println()
	fmt.Println("On the other device run:  svoi join " + inv.Code)
	fmt.Println("or choose «Join with an invitation» in its web interface.")
	return nil
}

func cmdPing(args []string) error {
	c, fs, err := clientFromFlags("ping", args, nil)
	if err != nil {
		return err
	}
	if fs.NArg() != 1 {
		return errors.New("usage: svoi ping <device>")
	}
	var out struct{ MS float64 }
	for i := 0; i < 4; i++ {
		if err := c.get("/api/diag/ping?peer="+url.QueryEscape(fs.Arg(0)), &out); err != nil {
			return err
		}
		fmt.Printf("reply from %s: %.1f ms\n", fs.Arg(0), out.MS)
		time.Sleep(400 * time.Millisecond)
	}
	return nil
}

func cmdSend(args []string) error {
	c, fs, err := clientFromFlags("send", args, nil)
	if err != nil {
		return err
	}
	if fs.NArg() < 2 {
		return errors.New("usage: svoi send <device> <file>...")
	}
	dev := fs.Arg(0)
	for _, path := range fs.Args()[1:] {
		f, err := os.Open(path)
		if err != nil {
			return err
		}
		st, err := f.Stat()
		if err != nil || st.IsDir() {
			f.Close()
			return fmt.Errorf("%s is not a file", path)
		}
		q := url.Values{"to": {dev}, "name": {filepath.Base(path)}}
		var out struct {
			Transfers []struct{ ID, State string }
		}
		err = c.do(context.Background(), http.MethodPost, "/api/transfers?"+q.Encode(), f, &out)
		f.Close()
		if err != nil {
			return err
		}
		fmt.Printf("queued %s (%d bytes) for %s\n", filepath.Base(path), st.Size(), dev)
	}
	return nil
}

func cmdOpen(args []string) error {
	var cf commonFlags
	fs := flag.NewFlagSet("open", flag.ExitOnError)
	cf.register(fs)
	fs.Parse(args)
	u, err := uiURL(cf.dir)
	if err != nil {
		return err
	}
	fmt.Println(u)
	openBrowser(u)
	return nil
}

func cmdURL(args []string) error {
	var cf commonFlags
	fs := flag.NewFlagSet("url", flag.ExitOnError)
	cf.register(fs)
	fs.Parse(args)
	u, err := uiURL(cf.dir)
	if err != nil {
		return err
	}
	fmt.Println(u)
	return nil
}

func uiURL(dir string) (string, error) {
	c, err := newClient(dir)
	if err != nil {
		return "", err
	}
	return c.base + "/?t=" + c.token, nil
}

func cmdLeave(args []string) error {
	c, _, err := clientFromFlags("leave", args, nil)
	if err != nil {
		return err
	}
	fmt.Print("Leave the mesh? This device will forget all other devices (type yes): ")
	var ans string
	fmt.Scanln(&ans)
	if strings.ToLower(ans) != "yes" {
		fmt.Println("cancelled")
		return nil
	}
	return c.post("/api/mesh/leave", map[string]any{}, nil)
}
