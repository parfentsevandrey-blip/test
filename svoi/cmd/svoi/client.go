package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
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

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

// client talks to the running node's local API (the same one the browser uses).
type client struct {
	base  string
	token string
	http  *http.Client
}

// newClient finds the running node from its data directory and checks that what
// answers on the recorded port really is that node before the master token is
// ever sent to it (a stale ui.addr may point at somebody else's server by now).
func newClient(dir string) (*client, error) {
	addr, err := os.ReadFile(filepath.Join(dir, "ui.addr"))
	if err != nil {
		return nil, errors.New("svoi is not running (start it with `svoi up`)")
	}
	tok, err := os.ReadFile(filepath.Join(dir, "ui.token"))
	if err != nil {
		return nil, err
	}
	c := &client{
		base:  "http://" + strings.TrimSpace(string(addr)),
		token: strings.TrimSpace(string(tok)),
		http:  &http.Client{Timeout: 0, CheckRedirect: noRedirect},
	}
	if err := c.handshake(); err != nil {
		return nil, err
	}
	return c, nil
}

// noRedirect: the node never redirects an API call, so whatever does is not the node
// (and Go would carry the Authorization header along to the same host).
func noRedirect(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }

func (c *client) handshake() error {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return err
	}
	nonce := hex.EncodeToString(raw[:])
	resp, err := (&http.Client{Timeout: 5 * time.Second, CheckRedirect: noRedirect}).Get(c.base + "/api/handshake?n=" + nonce)
	if err != nil {
		return errors.New("cannot reach the running svoi: " + err.Error())
	}
	defer resp.Body.Close()
	var out struct {
		Proof string `json:"proof"`
	}
	if resp.StatusCode != http.StatusOK || json.NewDecoder(io.LimitReader(resp.Body, 4096)).Decode(&out) != nil ||
		subtle.ConstantTimeCompare([]byte(out.Proof), []byte(api.HandshakeProof(c.token, nonce))) != 1 {
		return fmt.Errorf("what listens on %s is not this svoi node (is ui.addr stale?); the token was not sent", strings.TrimPrefix(c.base, "http://"))
	}
	return nil
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

func clientFromFlags(name string, args []string, extra func(*flag.FlagSet)) (*client, []string, error) {
	fs := flag.NewFlagSet(name, flag.ExitOnError)
	var cf commonFlags
	cf.register(fs)
	if extra != nil {
		extra(fs)
	}
	pos := parseInterspersed(fs, args)
	c, err := newClient(cf.dir)
	return c, pos, err
}

func cmdStatus(args []string) error {
	var asJSON *bool
	c, _, err := clientFromFlags("status", args, func(fs *flag.FlagSet) {
		asJSON = fs.Bool("json", false, "print the full state as JSON")
	})
	if err != nil {
		return err
	}
	var st app.State
	if err := c.get("/api/state", &st); err != nil {
		return err
	}
	if *asJSON {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", " ")
		return enc.Encode(st)
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
	var owner *string
	c, _, err := clientFromFlags("invite", args, func(fs *flag.FlagSet) {
		admin = fs.Bool("admin", false, "make the new device an administrator (gives it the mesh key)")
		ttl = fs.Duration("ttl", 30*time.Minute, "how long the invitation stays valid")
		owner = fs.String("owner", "", "whose device this is (default: your own: files from your own devices are accepted without asking)")
	})
	if err != nil {
		return err
	}
	var inv app.InviteView
	if err := c.post("/api/invites", map[string]any{"admin": *admin, "ttlMinutes": int(ttl.Minutes()), "owner": *owner}, &inv); err != nil {
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
	c, pos, err := clientFromFlags("ping", args, nil)
	if err != nil {
		return err
	}
	if len(pos) != 1 {
		return errors.New("usage: svoi ping <device>")
	}
	var out struct{ MS float64 }
	for i := 0; i < 4; i++ {
		if err := c.get("/api/diag/ping?peer="+url.QueryEscape(pos[0]), &out); err != nil {
			return err
		}
		fmt.Printf("reply from %s: %.1f ms\n", pos[0], out.MS)
		time.Sleep(400 * time.Millisecond)
	}
	return nil
}

func cmdSend(args []string) error {
	c, pos, err := clientFromFlags("send", args, nil)
	if err != nil {
		return err
	}
	if len(pos) < 2 {
		return errors.New("usage: svoi send <device> <file>...")
	}
	dev := pos[0]
	for _, path := range pos[1:] {
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
	// What is printed can be copied to any computer; what the browser is started with
	// goes through a command line, where other users of this machine can read it, so
	// that one only works for this user.
	printed, err := uiURL(cf.dir, false)
	if err != nil {
		return err
	}
	fmt.Println(printed)
	if opened, err := uiURL(cf.dir, true); err == nil {
		openBrowser(opened)
	}
	return nil
}

func cmdURL(args []string) error {
	var cf commonFlags
	fs := flag.NewFlagSet("url", flag.ExitOnError)
	cf.register(fs)
	fs.Parse(args)
	u, err := uiURL(cf.dir, false)
	if err != nil {
		return err
	}
	fmt.Println(u)
	return nil
}

// uiURL asks the running node for a fresh single-use sign-in link. local says the
// link is for a browser on this machine that is about to be started with it on its
// command line (`svoi open`): it then only works for this user. A link that is
// printed (`svoi url`) works for whoever has it, so it can be carried to another
// computer.
func uiURL(dir string, local bool) (string, error) {
	c, err := newClient(dir)
	if err != nil {
		return "", err
	}
	var out struct {
		Code string `json:"code"`
	}
	if err := c.post("/api/login/code", map[string]bool{"local": local}, &out); err != nil {
		return "", err
	}
	return c.base + "/?t=" + out.Code, nil
}

// cmdSignout ends every browser session of this node's web interface.
func cmdSignout(args []string) error {
	c, _, err := clientFromFlags("signout", args, nil)
	if err != nil {
		return err
	}
	if err := c.post("/api/logout?all=1", struct{}{}, nil); err != nil {
		return err
	}
	fmt.Println("All browsers are signed out; unused sign-in links are cancelled. `svoi open` signs this one back in.")
	return nil
}

func cmdLeave(args []string) error {
	var yes bool
	c, _, err := clientFromFlags("leave", args, func(fs *flag.FlagSet) {
		fs.BoolVar(&yes, "yes", false, "do not ask for confirmation")
		fs.BoolVar(&yes, "y", false, "same as --yes")
	})
	if err != nil {
		return err
	}
	if !yes {
		fmt.Print("Leave the mesh? This device will forget all other devices (type yes): ")
		var ans string
		fmt.Scanln(&ans)
		if strings.ToLower(ans) != "yes" {
			return errors.New("cancelled")
		}
	}
	return c.post("/api/mesh/leave", map[string]any{}, nil)
}
