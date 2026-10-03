package demo

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"
)

type apiClient struct {
	t     *testing.T
	base  string
	token string
}

func newAPI(t *testing.T, d *Device) *apiClient {
	u, err := url.Parse(d.URL)
	if err != nil {
		t.Fatal(err)
	}
	return &apiClient{t: t, base: "http://" + u.Host, token: u.Query().Get("t")}
}

func (c *apiClient) do(method, path string, body io.Reader, out any) int {
	c.t.Helper()
	req, err := http.NewRequest(method, c.base+path, body)
	if err != nil {
		c.t.Fatal(err)
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatal(err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if out != nil {
		if err := json.Unmarshal(data, out); err != nil {
			c.t.Fatalf("%s %s: bad JSON %q: %v", method, path, data, err)
		}
	}
	return resp.StatusCode
}

func (c *apiClient) get(path string, out any) int { return c.do("GET", path, nil, out) }

// The demo is also the broadest integration test there is: four devices, three
// kinds of NAT, a relay, the LAN, and the whole HTTP API on top.
func TestDemoMeshEndToEnd(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
	defer cancel()
	d, err := Start(ctx, Options{Dir: t.TempDir(), UIPort: 18777, Quiet: true})
	if err != nil {
		t.Fatal(err)
	}
	defer d.Close()
	laptop := newAPI(t, d.Devices["laptop"])

	var st struct {
		Configured bool `json:"configured"`
		Self       struct {
			Name  string
			Admin bool
		}
		Peers []struct {
			ID, Name, Path, RelayVia string
			Online                   bool
			Shares                   int
			Services                 []struct{ Name string }
			OS                       string
		}
		Counters  struct{ Mail, Chat, Offers int }
		Transfers []struct{ State, Dir string }
	}
	if code := laptop.get("/api/state", &st); code != 200 || !st.Configured || st.Self.Name != "laptop" || !st.Self.Admin {
		t.Fatalf("state: code=%d %+v", code, st)
	}
	byName := map[string]int{}
	for i, p := range st.Peers {
		byName[p.Name] = i
		if !p.Online {
			t.Errorf("%s is offline", p.Name)
		}
	}
	if len(st.Peers) != 3 {
		t.Fatalf("expected 3 peers, got %d", len(st.Peers))
	}
	// Laptop and NAS share a LAN: direct. The phone sits behind a symmetric NAT: relayed.
	if p := st.Peers[byName["nas"]]; p.Path != "lan" && p.Path != "direct" {
		t.Errorf("nas path = %s, want a direct path", p.Path)
	}
	phone := st.Peers[byName["phone"]]
	meshWait := time.Now().Add(30 * time.Second)
	for phone.Path != "relay" && time.Now().Before(meshWait) {
		time.Sleep(500 * time.Millisecond)
		laptop.get("/api/state", &st)
		phone = st.Peers[byName["phone"]]
	}
	if phone.Path != "relay" || phone.RelayVia == "" || phone.OS != "android" {
		t.Errorf("phone: %+v", phone)
	}
	// Delivery and extras are asynchronous: poll until the expected state shows up.
	settle := time.Now().Add(30 * time.Second)
	for time.Now().Before(settle) {
		laptop.get("/api/state", &st)
		if st.Peers[byName["nas"]].Shares == 3 && st.Counters.Mail == 3 && st.Counters.Chat == 3 && st.Counters.Offers == 1 {
			break
		}
		time.Sleep(400 * time.Millisecond)
	}
	if st.Peers[byName["nas"]].Shares != 3 {
		t.Errorf("nas shares = %d, want 3", st.Peers[byName["nas"]].Shares)
	}
	if st.Counters.Mail != 3 || st.Counters.Chat != 3 || st.Counters.Offers != 1 {
		t.Errorf("counters %+v, want mail=3 chat=3 offers=1", st.Counters)
	}

	// Browse the NAS from the laptop and stream a file from it (with a range).
	nasID := st.Peers[byName["nas"]].ID
	var shares []struct{ ID, Name, Mode string }
	laptop.get("/api/peers/"+nasID+"/shares", &shares)
	var photos string
	for _, s := range shares {
		if s.Name == "Фото" {
			photos = s.ID
		}
	}
	if photos == "" {
		t.Fatalf("no Photos share in %+v", shares)
	}
	var ls struct {
		Entries []struct {
			Name  string
			IsDir bool
		}
	}
	laptop.get("/api/peers/"+nasID+"/fs?share="+photos+"&path=/", &ls)
	if len(ls.Entries) < 3 || !ls.Entries[0].IsDir {
		t.Fatalf("listing: %+v", ls)
	}
	req, _ := http.NewRequest("GET", laptop.base+"/api/peers/"+nasID+"/file?share="+photos+"&path="+url.QueryEscape("/Дача/IMG_0123 Яблоня.png"), nil)
	_ = req
	var inner struct{ Entries []struct{ Name string } }
	laptop.get("/api/peers/"+nasID+"/fs?share="+photos+"&path="+url.QueryEscape("/Дача"), &inner)
	if len(inner.Entries) == 0 {
		t.Fatal("Дача is empty")
	}
	freq, _ := http.NewRequest("GET", laptop.base+"/api/peers/"+nasID+"/file?share="+photos+"&path="+url.QueryEscape("/Дача/"+inner.Entries[0].Name), nil)
	freq.Header.Set("Authorization", "Bearer "+laptop.token)
	freq.Header.Set("Range", "bytes=0-7")
	fresp, err := http.DefaultClient.Do(freq)
	if err != nil {
		t.Fatal(err)
	}
	head, _ := io.ReadAll(fresp.Body)
	fresp.Body.Close()
	if fresp.StatusCode != 206 || string(head[1:4]) != "PNG" {
		t.Fatalf("range request: %d %q", fresp.StatusCode, head)
	}

	// Accept the phone's offer; it lands in the laptop's download folder.
	var transfers []struct{ ID, State, Dir, Name string }
	laptop.get("/api/transfers", &transfers)
	var offer string
	for _, tr := range transfers {
		if tr.Dir == "in" && tr.State == "offered" {
			offer = tr.ID
		}
	}
	if offer == "" {
		t.Fatalf("no offer in %+v", transfers)
	}
	laptop.do("POST", "/api/transfers/"+offer+"/accept", strings.NewReader("{}"), nil)
	deadline := time.Now().Add(60 * time.Second)
	for time.Now().Before(deadline) {
		laptop.get("/api/transfers", &transfers)
		done := false
		for _, tr := range transfers {
			if tr.ID == offer && tr.State == "done" {
				done = true
			}
		}
		if done {
			break
		}
		time.Sleep(500 * time.Millisecond)
	}
	for _, tr := range transfers {
		if tr.ID == offer && tr.State != "done" {
			t.Fatalf("transfer did not finish: %+v", tr)
		}
	}

	// Mail: read the NAS report and its attachment.
	var inbox struct {
		Items []struct {
			ID, Subject string
			Attachments int
		}
	}
	laptop.get("/api/mail?folder=inbox", &inbox)
	var report string
	for _, m := range inbox.Items {
		if strings.Contains(m.Subject, "резервного") {
			report = m.ID
		}
	}
	if report == "" {
		t.Fatalf("inbox lacks the backup report: %+v", inbox)
	}
	attDeadline := time.Now().Add(20 * time.Second)
	for time.Now().Before(attDeadline) {
		r, _ := http.NewRequest("GET", laptop.base+"/api/mail/"+report+"/attachments/0", nil)
		r.Header.Set("Authorization", "Bearer "+laptop.token)
		resp, err := http.DefaultClient.Do(r)
		if err != nil {
			t.Fatal(err)
		}
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		if resp.StatusCode == 200 && strings.Contains(string(body), "Резервное копирование") {
			break
		}
		if time.Now().After(attDeadline.Add(-500 * time.Millisecond)) {
			t.Fatalf("attachment not available: %d %q", resp.StatusCode, body)
		}
		time.Sleep(400 * time.Millisecond)
	}

	// Services on the home server can be reached through a forward.
	var hsID string
	for _, p := range st.Peers {
		if p.Name == "home-server" {
			hsID = p.ID
		}
	}
	var fw struct{ ID, Listen, State string }
	code := laptop.do("POST", "/api/forwards", strings.NewReader(`{"peer":"`+hsID+`","service":"web","listen":"127.0.0.1:0"}`), &fw)
	if code != 200 || fw.State != "listening" {
		t.Fatalf("forward: %d %+v", code, fw)
	}
	resp, err := http.Get("http://" + fw.Listen + "/")
	if err != nil {
		t.Fatal(err)
	}
	page, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if !strings.Contains(string(page), "Домашняя страница") {
		t.Fatalf("service through the forward returned %q", page)
	}

	// Remote administration (the laptop is an admin). Switching relay on the NAS
	// takes effect at once and keeps the link; a setting that restarts the NAS's
	// network (LAN discovery here) must still get its answer, and the NAS comes back.
	nasAPI := newAPI(t, d.Devices["nas"])
	type peerCaps struct {
		Online bool
		Caps   []string
		Name   string
	}
	nasCaps := func() peerCaps {
		var s struct{ Peers []peerCaps }
		laptop.get("/api/state", &s)
		for _, p := range s.Peers {
			if p.Name == "nas" {
				return p
			}
		}
		return peerCaps{}
	}
	hasRelay := func(c peerCaps) bool {
		for _, x := range c.Caps {
			if x == "relay" {
				return true
			}
		}
		return false
	}
	if !hasRelay(nasCaps()) {
		t.Fatalf("the NAS should relay by default: %+v", nasCaps())
	}
	var rs struct{ Relay, LAN bool }
	if code := laptop.do("PUT", "/api/d/"+nasID+"/settings", strings.NewReader(`{"relay":false}`), &rs); code != 200 || rs.Relay {
		t.Fatalf("remote relay off: %d %+v", code, rs)
	}
	relayOff := time.Now().Add(15 * time.Second)
	for hasRelay(nasCaps()) && time.Now().Before(relayOff) {
		if c := nasCaps(); !c.Online {
			t.Fatal("the link to the NAS dropped when only its relay setting changed")
		}
		time.Sleep(300 * time.Millisecond)
	}
	if c := nasCaps(); hasRelay(c) || !c.Online {
		t.Fatalf("after switching relay off: %+v", c)
	}
	if code := laptop.do("PUT", "/api/d/"+nasID+"/settings", strings.NewReader(`{"lan":false}`), &rs); code != 200 || rs.LAN {
		t.Fatalf("remote LAN setting: the answer was lost (%d %+v)", code, rs)
	}
	var local struct{ Relay, LAN bool }
	nasAPI.get("/api/settings", &local)
	if local.LAN || local.Relay {
		t.Fatalf("settings on the NAS itself: %+v", local)
	}
	back := time.Now().Add(30 * time.Second)
	for !nasCaps().Online && time.Now().Before(back) {
		time.Sleep(300 * time.Millisecond)
	}
	if !nasCaps().Online {
		t.Fatal("the NAS did not come back after its network restarted")
	}
}
