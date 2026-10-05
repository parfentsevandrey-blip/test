package api_test

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/smtp"
	"strings"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/api"
	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
	"net/http/httptest"
)

// newMailEnv is a device whose mail gateway asks the DNS given here, not the real one.
func newMailEnv(t *testing.T, dns inetmail.Resolver) *env {
	t.Helper()
	a, err := app.Open(app.Options{Dir: t.TempDir(), DeviceName: "gateway-box", Owner: "tester", MailResolver: dns})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { a.Close() })
	off := false
	if _, err := a.UpdateSettings(app.SettingsPatch{STUNEnabled: &off, PortMap: &off}); err != nil {
		t.Fatal(err)
	}
	ts := httptest.NewServer(api.New(a, nil).Handler())
	t.Cleanup(ts.Close)
	return &env{t: t, app: a, srv: ts, tok: a.Token()}
}

type gwView struct {
	Enabled      bool   `json:"enabled"`
	Domain       string `json:"domain"`
	Host         string `json:"host"`
	Listen       string `json:"listen"`
	DKIMSelector string `json:"dkimSelector"`
	PublicIPv4   string `json:"publicIPv4"`
	Mailboxes    []struct {
		Name    string   `json:"name"`
		Address string   `json:"address"`
		Devices []string `json:"devices"`
	} `json:"mailboxes"`
	Relay *struct {
		Host        string `json:"host"`
		Port        int    `json:"port"`
		Username    string `json:"username"`
		Password    string `json:"password"`
		PasswordSet bool   `json:"passwordSet"`
		Mode        string `json:"mode"`
	} `json:"relay"`
	Status struct {
		Enabled     bool   `json:"enabled"`
		Running     bool   `json:"running"`
		Listening   bool   `json:"listening"`
		ListenAddr  string `json:"listenAddr"`
		ListenError string `json:"listenError"`
		Queue       int    `json:"queue"`
	} `json:"status"`
	Defaults struct {
		Host   string `json:"host"`
		Listen string `json:"listen"`
	} `json:"defaults"`
}

type dnsView struct {
	Domain   string `json:"domain"`
	Host     string `json:"host"`
	PublicIP string `json:"publicIPv4"`
	Report   struct {
		Ready   bool `json:"ready"`
		Records []struct {
			ID       string `json:"id"`
			Type     string `json:"type"`
			Name     string `json:"name"`
			Value    string `json:"value"`
			Required bool   `json:"required"`
			State    string `json:"state"`
		} `json:"records"`
	} `json:"report"`
}

type letterView struct {
	ID      string `json:"id"`
	Subject string `json:"subject"`
	Folder  string `json:"folder"`
	Body    string `json:"body"`
	Ext     *struct {
		Dir     string `json:"dir"`
		Verdict string `json:"verdict"`
		HasHTML bool   `json:"hasHtml"`
		Images  int    `json:"remoteImages"`
		From    struct {
			Addr string `json:"addr"`
			Name string `json:"name"`
		} `json:"from"`
		Out []struct {
			Addr  string `json:"addr"`
			State string `json:"state"`
			Text  string `json:"text"`
		} `json:"recipients"`
	} `json:"ext"`
}

func (e *env) gw() gwView {
	e.t.Helper()
	var v gwView
	if code := e.call("GET", "/api/mailgw", "", &v); code != 200 {
		e.t.Fatalf("GET /api/mailgw: %d", code)
	}
	return v
}

func TestMailGatewayIsOffUntilSetUpAndAnswersWhatIsWrong(t *testing.T) {
	e := newMailEnv(t, inetmail.NewFakeDNS())
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)

	v := e.gw()
	if v.Enabled || v.Status.Enabled || v.Status.Running || v.Status.Listening {
		t.Fatalf("a fresh device runs a mail server: %+v", v)
	}
	if v.Mailboxes == nil || len(v.Mailboxes) != 0 || v.Defaults.Listen != ":25" {
		t.Fatalf("fresh view: %+v", v)
	}
	// Nothing is listed for the composer, and the queue is empty, not null.
	var gws struct {
		Gateways []any `json:"gateways"`
	}
	if code := e.call("GET", "/api/mail/gateways", "", &gws); code != 200 || gws.Gateways == nil || len(gws.Gateways) != 0 {
		t.Fatalf("gateways: %d %+v", code, gws)
	}
	var q struct {
		Items []any `json:"items"`
	}
	if code := e.call("GET", "/api/mailgw/queue", "", &q); code != 200 || len(q.Items) != 0 {
		t.Fatalf("queue: %d %+v", code, q)
	}

	bad := map[string]string{
		"on without a domain":            `{"enabled":true,"mailboxes":[{"name":"a","devices":["self"]}]}`,
		"on without a mailbox":           `{"enabled":true,"domain":"example.org","mailboxes":[]}`,
		"a mailbox nobody has":           `{"domain":"example.org","mailboxes":[{"name":"a","devices":[]}]}`,
		"a domain that is not one":       `{"domain":"not a domain","mailboxes":[{"name":"a","devices":["self"]}]}`,
		"a mailbox with an @":            `{"domain":"example.org","mailboxes":[{"name":"a@b","devices":["self"]}]}`,
		"a mailbox twice":                `{"domain":"example.org","mailboxes":[{"name":"a","devices":["self"]},{"name":"A","devices":["self"]}]}`,
		"a port that is not a number":    `{"domain":"example.org","listen":"127.0.0.1:smtp","mailboxes":[{"name":"a","devices":["self"]}]}`,
		"an IPv6 as the public one":      `{"domain":"example.org","publicIPv4":"::1","mailboxes":[{"name":"a","devices":["self"]}]}`,
		"a way of connecting nobody has": `{"domain":"example.org","mailboxes":[{"name":"a","devices":["self"]}],"relay":{"host":"smtp.example.net","mode":"carrier-pigeon"}}`,
	}
	for what, body := range bad {
		var out struct {
			Error struct{ Code, Message string }
		}
		if code := e.call("PUT", "/api/mailgw", body, &out); code != 400 {
			t.Errorf("%s: %d, want 400 (%+v)", what, code, out)
		}
	}
	// A device that is not in the mesh cannot have a mailbox.
	if code := e.call("PUT", "/api/mailgw", `{"domain":"example.org","mailboxes":[{"name":"a","devices":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]}]}`, nil); code != 400 && code != 404 {
		t.Errorf("a stranger's mailbox: %d", code)
	}
	// None of that has changed anything.
	if v := e.gw(); v.Domain != "" || len(v.Mailboxes) != 0 {
		t.Fatalf("a refused setup was kept: %+v", v)
	}
	// An administrator reaches the gateway of this very device through the remote path too.
	if code := e.call("GET", "/api/d/self/mailgw", "", nil); code != 200 {
		t.Fatalf("/api/d/self/mailgw: %d", code)
	}
}

func publishPlan(dns *inetmail.FakeDNS, d dnsView) {
	for _, r := range d.Report.Records {
		switch r.Type {
		case "MX":
			dns.AddMX(r.Name, strings.TrimSuffix(strings.Fields(r.Value)[1], "."), 10)
		case "A", "AAAA":
			dns.AddA(r.Name, r.Value)
		case "TXT":
			dns.AddTXT(r.Name, r.Value)
		}
	}
}

func TestMailGatewaySetupDNSCheckAndTheRelayPassword(t *testing.T) {
	dns := inetmail.NewFakeDNS()
	e := newMailEnv(t, dns)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	var self struct{ Self struct{ ID string } }
	e.call("GET", "/api/state", "", &self)

	var v gwView
	body := `{"enabled":true,"domain":" Example.ORG ","listen":"127.0.0.1:0","publicIPv4":"203.0.113.7","mailboxes":[{"name":"Andrey","devices":["self"]}]}`
	if code := e.call("PUT", "/api/mailgw", body, &v); code != 200 {
		t.Fatalf("setup: %d", code)
	}
	if v.Domain != "example.org" || len(v.Mailboxes) != 1 || v.Mailboxes[0].Name != "andrey" || v.Mailboxes[0].Address != "andrey@example.org" {
		t.Fatalf("the setup is not cleaned: %+v", v)
	}
	if got := v.Mailboxes[0].Devices; len(got) != 1 || got[0] != self.Self.ID {
		t.Fatalf("\"self\" is not the id of this device: %v (%s)", got, self.Self.ID)
	}
	if !v.Status.Running || !v.Status.Listening || v.Status.ListenAddr == "" || v.Status.ListenError != "" {
		t.Fatalf("the gateway does not run: %+v", v.Status)
	}
	if v.Defaults.Host != "mail.example.org" {
		t.Fatalf("the default name of the mail server: %q", v.Defaults.Host)
	}

	// The composer is told where to write through.
	var gws struct {
		Gateways []struct {
			Self      bool     `json:"self"`
			Domain    string   `json:"domain"`
			Mailboxes []string `json:"mailboxes"`
		} `json:"gateways"`
	}
	waitUntil(t, "the gateway is listed", func() bool {
		gws.Gateways = nil
		e.call("GET", "/api/mail/gateways", "", &gws)
		return len(gws.Gateways) == 1
	})
	if g := gws.Gateways[0]; !g.Self || g.Domain != "example.org" || len(g.Mailboxes) != 1 || !strings.HasPrefix(g.Mailboxes[0], "andrey") {
		t.Fatalf("gateways: %+v", gws)
	}

	// DNS: nothing is published yet, so every record that mail needs is missing; after the records of the plan are entered, it is ready.
	var d dnsView
	if code := e.call("GET", "/api/mailgw/dns", "", &d); code != 200 {
		t.Fatalf("dns: %d", code)
	}
	if d.Report.Ready || d.Host != "mail.example.org" || d.PublicIP != "203.0.113.7" || len(d.Report.Records) < 5 {
		t.Fatalf("dns before: %+v", d)
	}
	ids := map[string]bool{}
	for _, r := range d.Report.Records {
		ids[r.ID] = true
		if r.Required && r.State != "missing" {
			t.Errorf("%s is %q before anything was published", r.ID, r.State)
		}
	}
	for _, id := range []string{"mx", "a", "spf", "dkim", "dmarc"} {
		if !ids[id] {
			t.Errorf("the plan has no %q record: %+v", id, ids)
		}
	}
	publishPlan(dns, d)
	var d2 dnsView
	e.call("GET", "/api/mailgw/dns", "", &d2)
	if !d2.Report.Ready {
		t.Fatalf("dns after the records were entered: %+v", d2.Report)
	}

	// The password of a mail service goes in and never comes out, and a setup that does not repeat it keeps it.
	withRelay := `{"enabled":true,"domain":"example.org","listen":"127.0.0.1:0","mailboxes":[{"name":"andrey","devices":["self"]}],` +
		`"relay":{"host":"smtp.relay.example","username":"me","password":"s3cret-p4ss","mode":"starttls","spfInclude":"_spf.relay.example"}}`
	resp, raw := e.req("PUT", "/api/mailgw", withRelay, e.auth)
	if resp.StatusCode != 200 || strings.Contains(string(raw), "s3cret-p4ss") {
		t.Fatalf("relay setup: %d %s", resp.StatusCode, raw)
	}
	json.Unmarshal(raw, &v)
	if v.Relay == nil || !v.Relay.PasswordSet || v.Relay.Port != 587 || v.Relay.Password != "" {
		t.Fatalf("relay: %+v", v.Relay)
	}
	again := strings.Replace(withRelay, `"password":"s3cret-p4ss",`, "", 1)
	if resp, raw = e.req("PUT", "/api/mailgw", again, e.auth); resp.StatusCode != 200 {
		t.Fatalf("relay again: %d %s", resp.StatusCode, raw)
	}
	json.Unmarshal(raw, &v)
	if v.Relay == nil || !v.Relay.PasswordSet {
		t.Fatalf("the password was lost: %+v", v.Relay)
	}
	if resp, raw := e.req("GET", "/api/mailgw", "", e.auth); strings.Contains(string(raw), "s3cret-p4ss") || resp.StatusCode != 200 {
		t.Fatalf("the password comes back: %s", raw)
	}
	// With a relay the plan asks the domain to name it in the SPF record.
	var d3 dnsView
	e.call("GET", "/api/mailgw/dns", "", &d3)
	foundInclude := false
	for _, r := range d3.Report.Records {
		if r.ID == "spf" && strings.Contains(r.Value, "include:_spf.relay.example") {
			foundInclude = true
		}
	}
	if !foundInclude {
		t.Errorf("the SPF record does not name the relay: %+v", d3.Report.Records)
	}

	// Switching it off stops the server, and the setup stays for the next time.
	off := strings.Replace(again, `"enabled":true`, `"enabled":false`, 1)
	if code := e.call("PUT", "/api/mailgw", off, &v); code != 200 || v.Status.Listening || v.Status.Running || v.Domain != "example.org" {
		t.Fatalf("switch off: %d %+v", code, v)
	}
}

func TestLettersToTheInternetWaitInTheQueueAndCanBeGivenUp(t *testing.T) {
	dns := inetmail.NewFakeDNS()
	dns.AddMX("gmail.com", "mx.gmail.example", 5) // a mail server that has no address (yet): the letter waits and is tried again
	e := newMailEnv(t, dns)
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)

	// Without a gateway there is nothing to write through: the answer says so.
	var out struct {
		Error struct{ Code, Message string }
	}
	if code := e.call("POST", "/api/mail", `{"to":[],"emailTo":["friend@gmail.com"],"subject":"hi","body":"hello"}`, &out); code < 400 || code >= 500 || out.Error.Message == "" {
		t.Fatalf("a letter to the Internet without a gateway: %d %+v", code, out)
	}

	body := `{"enabled":true,"domain":"example.org","listen":"127.0.0.1:0","mailboxes":[{"name":"andrey","devices":["self"]}]}`
	if code := e.call("PUT", "/api/mailgw", body, nil); code != 200 {
		t.Fatalf("setup: %d", code)
	}
	waitUntil(t, "the gateway is known to the mail manager", func() bool {
		var g struct{ Gateways []any }
		e.call("GET", "/api/mail/gateways", "", &g)
		return len(g.Gateways) == 1
	})
	for what, send := range map[string]string{
		"a made-up sender":       `{"from":"boss@example.org","emailTo":["friend@gmail.com"],"subject":"hi","body":"hello"}`,
		"an address that is not": `{"emailTo":["friend at gmail"],"subject":"hi","body":"hello"}`,
		"an empty letter":        `{"emailTo":[],"to":[],"subject":"hi","body":"hello"}`,
	} {
		if code := e.call("POST", "/api/mail", send, nil); code < 400 || code >= 500 {
			t.Errorf("%s: %d", what, code)
		}
	}

	var waiting struct {
		ID string `json:"id"`
	}
	if code := e.call("POST", "/api/mail", `{"emailTo":["Friend <friend@gmail.com>"],"subject":"Привет","body":"Это письмо уходит на gmail."}`, &waiting); code != 200 || waiting.ID == "" {
		t.Fatalf("send: %d", code)
	}
	var q struct {
		Items []struct {
			ID    string `json:"id"`
			From  string `json:"from"`
			Rcpts []struct {
				Addr  string `json:"addr"`
				State string `json:"state"`
				Text  string `json:"text"`
			} `json:"rcpts"`
		} `json:"items"`
	}
	waitUntil(t, "the letter waits in the queue for another try", func() bool {
		q.Items = nil
		e.call("GET", "/api/mailgw/queue", "", &q)
		return len(q.Items) == 1 && len(q.Items[0].Rcpts) == 1 && q.Items[0].Rcpts[0].State == "deferred"
	})
	if it := q.Items[0]; it.From != "andrey@example.org" || it.Rcpts[0].Addr != "friend@gmail.com" || !strings.Contains(it.Rcpts[0].Text, "no address") {
		t.Fatalf("the queue: %+v", it)
	}
	var m letterView
	if code := e.call("GET", "/api/mail/"+waiting.ID, "", &m); code != 200 || m.Ext == nil || m.Ext.Dir != "out" || len(m.Ext.Out) != 1 || m.Ext.Out[0].Addr != "friend@gmail.com" {
		t.Fatalf("the sent letter: %d %+v", code, m)
	}
	if m.Folder != "sent" {
		t.Errorf("folder: %q", m.Folder)
	}
	if g := e.gw(); g.Status.Queue != 1 {
		t.Errorf("the status counts %d letters in the queue", g.Status.Queue)
	}

	// A domain that does not exist is a final answer: the letter does not wait, and its recipient says why.
	var gone struct {
		ID string `json:"id"`
	}
	if code := e.call("POST", "/api/mail", `{"emailTo":["x@no-such-domain.example"],"subject":"hi","body":"hello"}`, &gone); code != 200 {
		t.Fatalf("send to nowhere: %d", code)
	}
	waitUntil(t, "the letter to nowhere has failed", func() bool {
		var l letterView
		e.call("GET", "/api/mail/"+gone.ID, "", &l)
		return l.Ext != nil && len(l.Ext.Out) == 1 && l.Ext.Out[0].State == "failed"
	})
	var l letterView
	e.call("GET", "/api/mail/"+gone.ID, "", &l)
	if txt := l.Ext.Out[0].Text; !strings.Contains(txt, "does not exist") {
		t.Errorf("the reason is not given: %q", txt)
	}

	if code := e.call("POST", "/api/mailgw/queue/retry", "", nil); code != 200 {
		t.Fatalf("retry: %d", code)
	}
	if code := e.call("POST", "/api/mailgw/queue/no-such-letter/cancel", "", nil); code != 404 {
		t.Fatalf("cancel of nothing: %d", code)
	}
	if code := e.call("POST", "/api/mailgw/queue/"+q.Items[0].ID+"/cancel", "", nil); code != 200 {
		t.Fatalf("cancel: %d", code)
	}
	waitUntil(t, "the queue is empty and the letter says it was given up", func() bool {
		q.Items = nil
		e.call("GET", "/api/mailgw/queue", "", &q)
		var l letterView
		e.call("GET", "/api/mail/"+waiting.ID, "", &l)
		return len(q.Items) == 0 && l.Ext != nil && l.Ext.Out[0].State == "failed"
	})
}

// A tiny picture (1x1, PNG) for a letter that carries one inside its text.
const tinyPNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

func TestLettersFromTheInternetArriveAndTheirHTMLIsServedInertly(t *testing.T) {
	e := newMailEnv(t, inetmail.NewFakeDNS())
	e.call("POST", "/api/mesh/create", `{"meshName":"M","deviceName":"box"}`, nil)
	var v gwView
	if code := e.call("PUT", "/api/mailgw", `{"enabled":true,"domain":"example.org","listen":"127.0.0.1:0","mailboxes":[{"name":"andrey","devices":["self"]}]}`, &v); code != 200 || !v.Status.Listening {
		t.Fatalf("setup: %d %+v", code, v.Status)
	}

	msg := strings.ReplaceAll(`From: Friend <friend@gmail.com>
To: Andrey <andrey@example.org>
Subject: =?utf-8?B?0J/RgNC40LLQtdGC?=
Date: Mon, 05 Oct 2026 10:00:00 +0000
Message-ID: <letter-1@mail.gmail.com>
MIME-Version: 1.0
Content-Type: multipart/related; boundary=B1

--B1
Content-Type: multipart/alternative; boundary=B2

--B2
Content-Type: text/plain; charset=utf-8

Plain hello
--B2
Content-Type: text/html; charset=utf-8

<p>Hello <b>world</b></p><script>alert(1)</script><img src="cid:logo"><img src="https://tracker.example/p.gif"><a href="javascript:alert(2)">x</a><a href="https://example.com/a">ok</a>
--B2--
--B1
Content-Type: image/png; name="logo.png"
Content-ID: <logo>
Content-Disposition: inline; filename="logo.png"
Content-Transfer-Encoding: base64

`+tinyPNG+`
--B1--
`, "\n", "\r\n")

	c, err := smtp.Dial(v.Status.ListenAddr)
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	if err := c.Hello("mail-gmail.example"); err != nil {
		t.Fatal(err)
	}
	if err := c.Mail("friend@gmail.com"); err != nil {
		t.Fatal(err)
	}
	if err := c.Rcpt("andrey@example.org"); err != nil {
		t.Fatalf("a letter for a mailbox of the domain is refused: %v", err)
	}
	if err := c.Rcpt("stranger@example.org"); err == nil {
		t.Error("a mailbox that does not exist is accepted")
	}
	if err := c.Rcpt("someone@elsewhere.example"); err == nil {
		t.Error("the gateway relays for another domain")
	}
	w, err := c.Data()
	if err != nil {
		t.Fatal(err)
	}
	if _, err := w.Write([]byte(msg)); err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatalf("the letter is refused: %v", err)
	}
	c.Quit()

	var inbox struct {
		Items []letterView `json:"items"`
		Total int          `json:"total"`
	}
	waitUntil(t, "the letter reached the inbox", func() bool {
		e.call("GET", "/api/mail?folder=inbox", "", &inbox)
		return inbox.Total == 1
	})
	l := inbox.Items[0]
	if l.Subject != "Привет" || l.Ext == nil || l.Ext.Dir != "in" || l.Ext.From.Addr != "friend@gmail.com" || !l.Ext.HasHTML || l.Ext.Images != 1 {
		t.Fatalf("the letter: %+v ext=%+v", l, l.Ext)
	}
	if l.Ext.Verdict == "" {
		t.Error("the letter has no verdict about its sender")
	}
	var full letterView
	if code := e.call("GET", "/api/mail/"+l.ID, "", &full); code != 200 || !strings.Contains(full.Body, "Plain hello") {
		t.Fatalf("the text: %d %q", code, full.Body)
	}
	if resp, raw := e.req("GET", "/api/mail/"+l.ID, "", e.auth); strings.Contains(string(raw), "<script") {
		t.Fatalf("the JSON of the letter carries HTML (%d)", resp.StatusCode)
	}

	// The formatted text: cleaned, with the picture of the letter inside the page and nothing else loaded from anywhere.
	resp, page := e.req("GET", "/api/mail/"+l.ID+"/html", "", e.auth)
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("html: %d %s", resp.StatusCode, page)
	}
	p := string(page)
	for _, bad := range []string{"<script", "alert(", "javascript:", "tracker.example", "cid:logo"} {
		if strings.Contains(p, bad) {
			t.Errorf("the page of the letter contains %q:\n%s", bad, p)
		}
	}
	for _, want := range []string{"Hello <b>world</b>", `href="https://example.com/a"`, "data:image/png;base64," + tinyPNG[:30]} {
		if !strings.Contains(p, want) {
			t.Errorf("the page of the letter lacks %q:\n%s", want, p)
		}
	}
	if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/html") {
		t.Errorf("content type %q", ct)
	}
	csp := resp.Header.Get("Content-Security-Policy")
	for _, want := range []string{"default-src 'none'", "img-src data:", "form-action 'none'", "frame-ancestors 'self'"} {
		if !strings.Contains(csp, want) {
			t.Errorf("CSP lacks %q: %s", want, csp)
		}
	}
	if strings.Contains(csp, "script-src") || strings.Contains(csp, "connect-src") {
		t.Errorf("CSP lets something run or connect: %s", csp)
	}
	if resp.Header.Get("Referrer-Policy") != "no-referrer" || resp.Header.Get("X-Content-Type-Options") != "nosniff" || !strings.Contains(resp.Header.Get("Cache-Control"), "no-store") {
		t.Errorf("headers: %v", resp.Header)
	}
	if strings.Contains(csp, "https:") {
		t.Errorf("the pictures of the Internet are allowed without being asked for: %s", csp)
	}
	// Not for anyone without the key, not for a letter that has no HTML, not for one that does not exist.
	if resp, _ := e.req("GET", "/api/mail/"+l.ID+"/html", "", nil); resp.StatusCode != 401 {
		t.Errorf("the page of a letter without credentials: %d", resp.StatusCode)
	}
	var note struct{ ID string }
	e.call("POST", "/api/mail", `{"to":["self"],"subject":"todo","body":"buy milk"}`, &note)
	if code := e.call("GET", "/api/mail/"+note.ID+"/html", "", nil); code != 404 {
		t.Errorf("a note to self has a page: %d", code)
	}
	if code := e.call("GET", "/api/mail/nonexistent/html", "", nil); code != 404 {
		t.Errorf("a letter that does not exist has a page: %d", code)
	}
	// The picture that is part of the letter is also an attachment that can be downloaded, as a file and never as a page.
	if resp, got := e.req("GET", "/api/mail/"+l.ID+"/attachments/0?dl=1", "", e.auth); resp.StatusCode != 200 {
		t.Errorf("attachment: %d", resp.StatusCode)
	} else if b, _ := base64.StdEncoding.DecodeString(tinyPNG); string(got) != string(b) {
		t.Errorf("the attachment is not the picture that was sent")
	}
}
