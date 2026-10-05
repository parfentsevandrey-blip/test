package inetmail

import (
	"context"
	"net"
	"strings"
	"testing"
)

func testCfg(t *testing.T) DNSConfig {
	k, err := NewDKIMKey("mesh")
	if err != nil {
		t.Fatal(err)
	}
	return DNSConfig{Domain: "example.org", MailHost: "mail.example.org", IPv4: []net.IP{net.ParseIP("203.0.113.7")}, DKIM: k}
}

// publish puts every record of the plan in the fake DNS, as a person would at the registrar.
func publish(dns *FakeDNS, specs []RecordSpec) {
	for _, s := range specs {
		switch s.Type {
		case "MX":
			dns.AddMX(s.Name, strings.TrimSuffix(strings.Fields(s.Value)[1], "."), 10)
		case "A", "AAAA":
			dns.AddA(s.Name, s.Value)
		case "TXT":
			dns.AddTXT(s.Name, s.Value)
		case "PTR":
			dns.AddPTR(ptrIP(s.Name).String(), strings.TrimSuffix(s.Value, "."))
		}
	}
}

func byID(r DNSReport, id string) RecordCheck {
	for _, c := range r.Records {
		if c.ID == id {
			return c
		}
	}
	return RecordCheck{}
}

func TestPlanDNS(t *testing.T) {
	c := testCfg(t)
	got := map[string]RecordSpec{}
	for _, s := range PlanDNS(c) {
		got[s.ID] = s
	}
	if g := got[RecMX]; g.Name != "example.org" || g.Value != "10 mail.example.org." || !g.Required {
		t.Errorf("MX: %+v", g)
	}
	if g := got[RecA]; g.Name != "mail.example.org" || g.Value != "203.0.113.7" {
		t.Errorf("A: %+v", g)
	}
	if g := got[RecSPF]; g.Value != "v=spf1 mx ~all" {
		t.Errorf("SPF: %+v", g)
	}
	if g := got[RecDKIM]; g.Name != "mesh._domainkey.example.org" || !strings.HasPrefix(g.Value, "v=DKIM1; k=rsa; p=") {
		t.Errorf("DKIM: %+v", g)
	}
	if g := got[RecDMARC]; g.Name != "_dmarc.example.org" || g.Required {
		t.Errorf("DMARC: %+v", g)
	}
	if g := got[RecPTR]; g.Name != "7.113.0.203.in-addr.arpa" || g.Value != "mail.example.org." {
		t.Errorf("PTR: %+v", g)
	}
	// through a mail service: its SPF is included, and the gateway's own reverse record is not asked for
	c.Relay, c.SPFInclude = true, "spf.relay.example."
	got = map[string]RecordSpec{}
	for _, s := range PlanDNS(c) {
		got[s.ID] = s
	}
	if got[RecSPF].Value != "v=spf1 mx include:spf.relay.example ~all" {
		t.Errorf("SPF with a relay: %q", got[RecSPF].Value)
	}
	if _, ok := got[RecPTR]; ok {
		t.Error("no PTR is asked for when the letters leave through a service")
	}
	// the address is not known yet
	c = testCfg(t)
	c.IPv4 = nil
	if a := PlanDNS(c)[1]; a.ID != RecA || a.Value != "" {
		t.Errorf("an A record without an address: %+v", a)
	}
}

func TestReverseNames(t *testing.T) {
	for _, ip := range []string{"203.0.113.7", "2001:db8::1"} {
		if got := ptrIP(reverseName(net.ParseIP(ip))); got == nil || !got.Equal(net.ParseIP(ip)) {
			t.Errorf("%s -> %s -> %v", ip, reverseName(net.ParseIP(ip)), got)
		}
	}
}

func TestCheckDNS(t *testing.T) {
	ctx := context.Background()
	c := testCfg(t)
	dns := NewFakeDNS()

	// nothing is published
	r := CheckDNS(ctx, dns, c)
	if r.Ready {
		t.Fatal("an empty DNS is not ready")
	}
	for _, rec := range r.Records {
		if rec.State != StateMissing {
			t.Errorf("%s: %s", rec.ID, rec.State)
		}
	}

	// everything as planned
	publish(dns, PlanDNS(c))
	r = CheckDNS(ctx, dns, c)
	if !r.Ready {
		t.Fatalf("a DNS set up as planned must be ready: %+v", r)
	}
	for _, rec := range r.Records {
		if rec.State != StateOK {
			t.Errorf("%s: %s %s %v", rec.ID, rec.State, rec.Detail, rec.Found)
		}
	}

	// a long DKIM value that the registrar split and quoted still counts
	d2 := NewFakeDNS()
	publish(d2, PlanDNS(c))
	val := c.DKIM.DNSValue()
	d2.SetTXT(c.DKIM.RecordName("example.org"), val[:100]+`" "`+val[100:])
	if got := byID(CheckDNS(ctx, d2, c), RecDKIM); got.State != StateOK {
		t.Errorf("split key: %+v", got)
	}

	// each kind of mistake
	mistakes := map[string]struct {
		do     func(d *FakeDNS)
		id     string
		state  string
		detail string
	}{
		"mx elsewhere": {func(d *FakeDNS) {
			d.mx["example.org"] = nil
			d.AddMX("example.org", "mx.other.example", 5)
		}, RecMX, StateWrong, "other-host"},
		"a elsewhere": {func(d *FakeDNS) {
			d.addr["mail.example.org"] = nil
			d.AddA("mail.example.org", "198.51.100.1")
		}, RecA, StateWrong, "other-address"},
		"spf for another machine": {func(d *FakeDNS) { d.SetTXT("example.org", "v=spf1 ip4:198.51.100.9 ~all") }, RecSPF, StateWrong, "ip-not-allowed:softfail"},
		"two spf records":         {func(d *FakeDNS) { d.AddTXT("example.org", "v=spf1 mx -all") }, RecSPF, StateWrong, "two-records"},
		"spf missing":             {func(d *FakeDNS) { d.SetTXT("example.org") }, RecSPF, StateMissing, ""},
		"dkim of another key":     {func(d *FakeDNS) { d.SetTXT("mesh._domainkey.example.org", "v=DKIM1; k=rsa; p=AAAAB3NzaC1yc2E=") }, RecDKIM, StateWrong, "other-key"},
		"dmarc missing":           {func(d *FakeDNS) { d.SetTXT("_dmarc.example.org") }, RecDMARC, StateMissing, ""},
		"ptr elsewhere": {func(d *FakeDNS) {
			d.ptr["203.0.113.7"] = []string{"static-7.isp.example."}
		}, RecPTR, StateWrong, "other-name"},
		"dns does not answer": {func(d *FakeDNS) { d.Fail("example.org", true) }, RecMX, StateUnknown, "dns-error"},
	}
	for name, m := range mistakes {
		d := NewFakeDNS()
		publish(d, PlanDNS(c))
		m.do(d)
		got := byID(CheckDNS(ctx, d, c), m.id)
		if got.State != m.state || !strings.HasPrefix(got.Detail, m.detail) {
			t.Errorf("%s: got %s %q (found %v), want %s %q", name, got.State, got.Detail, got.Found, m.state, m.detail)
		}
	}
	// an optional record that is missing does not stop the mail
	d3 := NewFakeDNS()
	publish(d3, PlanDNS(c))
	d3.SetTXT("_dmarc.example.org")
	d3.ptr = map[string][]string{}
	if r := CheckDNS(ctx, d3, c); !r.Ready {
		t.Errorf("DMARC and PTR are recommended, not required: %+v", r)
	}
}
