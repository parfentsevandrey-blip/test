package inetmail

import (
	"context"
	"fmt"
	"net"
	"sort"
	"strings"
	"sync"
)

// A domain that sends and receives mail needs a handful of DNS records. PlanDNS says which, CheckDNS looks at the real
// DNS and says what is there, what is missing and what is wrong - in codes, which the interface turns into words.

// DNSConfig describes the domain that is being set up.
type DNSConfig struct {
	Domain   string   // example.org
	MailHost string   // mail.example.org: what the MX record points at and what the gateway says in HELO
	IPv4     []net.IP // the addresses the Internet sees the gateway at (empty: not known yet)
	IPv6     []net.IP
	DKIM     *DKIMKey
	// Relay is true when the letters leave through a mail service (a "smart host") instead of straight from the gateway:
	// the service's SPF is to be included, and the gateway's own address and its reverse record matter less.
	Relay      bool
	SPFInclude string // the SPF domain of that service, e.g. "spf.example-relay.net"
}

// Record codes.
const (
	RecMX    = "mx"
	RecA     = "a"
	RecAAAA  = "aaaa"
	RecSPF   = "spf"
	RecDKIM  = "dkim"
	RecDMARC = "dmarc"
	RecPTR   = "ptr"
)

// Record states.
const (
	StateOK      = "ok"      // there and right
	StateMissing = "missing" // not there
	StateWrong   = "wrong"   // there, but not what is needed
	StateUnknown = "unknown" // could not be checked (the DNS did not answer, or the address is not known yet)
)

// RecordSpec is one record to be entered at the registrar (or, for PTR, at the provider of the address).
type RecordSpec struct {
	ID       string `json:"id"`
	Type     string `json:"type"`     // MX, A, AAAA, TXT, PTR
	Name     string `json:"name"`     // the full name, as it is entered
	Value    string `json:"value"`    // what is entered
	Required bool   `json:"required"` // mail does not work properly without it
}

// RecordCheck is a record together with what the DNS says about it.
type RecordCheck struct {
	RecordSpec
	State  string   `json:"state"`
	Found  []string `json:"found,omitempty"`
	Detail string   `json:"detail,omitempty"` // why it is wrong: a code ("other-key", "ip-not-allowed", "two-records", ...)
}

// DNSReport is the result of CheckDNS.
type DNSReport struct {
	Records []RecordCheck `json:"records"`
	// Ready is true when every required record is right: mail can be received and sent.
	Ready bool `json:"ready"`
}

func ipStrings(ips []net.IP) []string {
	var out []string
	for _, ip := range ips {
		out = append(out, ip.String())
	}
	return out
}

// reverseName is the name that carries the reverse record of ip (4.3.2.1.in-addr.arpa).
func reverseName(ip net.IP) string {
	if v4 := ip.To4(); v4 != nil {
		return fmt.Sprintf("%d.%d.%d.%d.in-addr.arpa", v4[3], v4[2], v4[1], v4[0])
	}
	ip = ip.To16()
	var b strings.Builder
	for i := 15; i >= 0; i-- {
		fmt.Fprintf(&b, "%x.%x.", ip[i]&0x0f, ip[i]>>4)
	}
	return b.String() + "ip6.arpa"
}

// SPFValue is the text of the SPF record for the domain: the machines the MX records name may send for it, and the
// mail service too when there is one. "~all": whatever else sends for the domain is suspect, not rejected.
func (c DNSConfig) SPFValue() string {
	v := "v=spf1 mx"
	if c.Relay && c.SPFInclude != "" {
		v += " include:" + fqdn(c.SPFInclude)
	}
	return v + " ~all"
}

// DMARCValue is the text of the DMARC record: letters that fail are only reported, not touched (the policy can be
// made strict later, when it is known that everything of the domain passes).
func (c DNSConfig) DMARCValue() string { return "v=DMARC1; p=none; adkim=r; aspf=r" }

// PlanDNS lists the records the domain needs.
func PlanDNS(c DNSConfig) []RecordSpec {
	d, host := fqdn(c.Domain), fqdn(c.MailHost)
	var out []RecordSpec
	out = append(out, RecordSpec{ID: RecMX, Type: "MX", Name: d, Value: "10 " + host + ".", Required: true})
	if len(c.IPv4) == 0 {
		out = append(out, RecordSpec{ID: RecA, Type: "A", Name: host, Value: "", Required: true})
	}
	for _, ip := range c.IPv4 {
		out = append(out, RecordSpec{ID: RecA, Type: "A", Name: host, Value: ip.String(), Required: true})
	}
	for _, ip := range c.IPv6 {
		out = append(out, RecordSpec{ID: RecAAAA, Type: "AAAA", Name: host, Value: ip.String()})
	}
	out = append(out, RecordSpec{ID: RecSPF, Type: "TXT", Name: d, Value: c.SPFValue(), Required: true})
	if c.DKIM != nil {
		out = append(out, RecordSpec{ID: RecDKIM, Type: "TXT", Name: c.DKIM.RecordName(d), Value: c.DKIM.DNSValue(), Required: true})
	}
	out = append(out, RecordSpec{ID: RecDMARC, Type: "TXT", Name: "_dmarc." + d, Value: c.DMARCValue()})
	if !c.Relay {
		for _, ip := range append(append([]net.IP(nil), c.IPv4...), c.IPv6...) {
			out = append(out, RecordSpec{ID: RecPTR, Type: "PTR", Name: reverseName(ip), Value: host + "."})
		}
	}
	return out
}

// CheckDNS looks at the real DNS (through res) and says, record by record, whether the domain is set up.
func CheckDNS(ctx context.Context, res Resolver, c DNSConfig) DNSReport {
	ctx, cancel := context.WithTimeout(ctx, 2*dnsTimeout)
	defer cancel()
	specs := PlanDNS(c)
	checks := make([]RecordCheck, len(specs))
	var wg sync.WaitGroup
	for i, sp := range specs {
		wg.Add(1)
		go func(i int, sp RecordSpec) {
			defer wg.Done()
			checks[i] = checkRecord(ctx, res, c, sp)
		}(i, sp)
	}
	wg.Wait()
	rep := DNSReport{Records: checks, Ready: true}
	for _, ch := range checks {
		if ch.Required && ch.State != StateOK {
			rep.Ready = false
		}
	}
	return rep
}

func dnsFail(sp RecordSpec, err error) RecordCheck {
	return RecordCheck{RecordSpec: sp, State: StateUnknown, Detail: "dns-error: " + err.Error()}
}

func checkRecord(ctx context.Context, res Resolver, c DNSConfig, sp RecordSpec) RecordCheck {
	d, host := fqdn(c.Domain), fqdn(c.MailHost)
	rc := RecordCheck{RecordSpec: sp, State: StateMissing}
	switch sp.ID {
	case RecMX:
		mx, err := res.LookupMX(ctx, d)
		if err != nil && !isNotFound(err) {
			return dnsFail(sp, err)
		}
		sort.Slice(mx, func(i, j int) bool { return mx[i].Pref < mx[j].Pref })
		for _, m := range mx {
			rc.Found = append(rc.Found, fmt.Sprintf("%d %s", m.Pref, m.Host))
		}
		switch {
		case len(mx) == 0:
		case fqdn(mx[0].Host) == host:
			rc.State = StateOK
		default:
			rc.State, rc.Detail = StateWrong, "other-host"
		}

	case RecA, RecAAAA:
		if sp.Value == "" {
			rc.State, rc.Detail = StateUnknown, "address-unknown"
			return rc
		}
		addrs, err := res.LookupIPAddr(ctx, host)
		if err != nil && !isNotFound(err) {
			return dnsFail(sp, err)
		}
		want := net.ParseIP(sp.Value)
		for _, a := range addrs {
			if (sp.ID == RecA) == (a.IP.To4() != nil) {
				rc.Found = append(rc.Found, a.IP.String())
				if a.IP.Equal(want) {
					rc.State = StateOK
				}
			}
		}
		if rc.State != StateOK && len(rc.Found) > 0 {
			rc.State, rc.Detail = StateWrong, "other-address"
		}

	case RecSPF:
		txt, err := lookupTXT(ctx, res, d)
		if err != nil {
			return dnsFail(sp, err)
		}
		var spfs []string
		for _, t := range txt {
			if strings.HasPrefix(strings.ToLower(strings.TrimSpace(t)), "v=spf1") {
				spfs = append(spfs, t)
			}
		}
		rc.Found = spfs
		switch {
		case len(spfs) == 0:
		case len(spfs) > 1:
			rc.State, rc.Detail = StateWrong, "two-records" // two SPF records are an error in themselves
		default:
			rc.State = StateOK
			if len(c.IPv4) > 0 && !c.Relay {
				// the record is there; is it right for the machine that sends? ask it as a receiver would
				if r := CheckSPF(ctx, res, c.IPv4[0], host, "postmaster@"+d); r != SPFPass {
					rc.State, rc.Detail = StateWrong, "ip-not-allowed:"+string(r)
				}
			}
		}

	case RecDKIM:
		txt, err := lookupTXT(ctx, res, sp.Name)
		if err != nil {
			return dnsFail(sp, err)
		}
		rc.Found = txt
		want := dkimKey(sp.Value)
		for _, t := range txt {
			if strings.Contains(strings.ToLower(t), "v=dkim1") || strings.Contains(strings.ToLower(t), "p=") {
				if dkimKey(t) == want && want != "" {
					rc.State, rc.Detail = StateOK, ""
					break
				}
				rc.State, rc.Detail = StateWrong, "other-key"
			}
		}

	case RecDMARC:
		txt, err := lookupTXT(ctx, res, sp.Name)
		if err != nil {
			return dnsFail(sp, err)
		}
		for _, t := range txt {
			if strings.HasPrefix(strings.ToLower(strings.TrimSpace(t)), "v=dmarc1") {
				rc.Found = append(rc.Found, t)
				rc.State = StateOK
			}
		}

	case RecPTR:
		ip := ptrIP(sp.Name)
		if ip == nil {
			return rc
		}
		names, err := res.LookupAddr(ctx, ip.String())
		if err != nil && !isNotFound(err) {
			return dnsFail(sp, err)
		}
		for _, n := range names {
			rc.Found = append(rc.Found, fqdn(n))
			if fqdn(n) == host {
				rc.State = StateOK
			}
		}
		if rc.State != StateOK && len(names) > 0 {
			rc.State, rc.Detail = StateWrong, "other-name"
		}
	}
	return rc
}

// dkimKey pulls the p= part out of the text of a DKIM record, without the spaces some registrars add.
func dkimKey(record string) string {
	for _, part := range strings.Split(record, ";") {
		part = strings.TrimSpace(part)
		if strings.HasPrefix(strings.ToLower(part), "p=") {
			return strings.Map(func(r rune) rune {
				if r == ' ' || r == '\t' || r == '"' {
					return -1
				}
				return r
			}, part[2:])
		}
	}
	return ""
}

// ptrIP reads the address back out of the name of a reverse record.
func ptrIP(name string) net.IP {
	name = fqdn(name)
	if s, ok := strings.CutSuffix(name, ".in-addr.arpa"); ok {
		p := strings.Split(s, ".")
		if len(p) == 4 {
			return net.ParseIP(p[3] + "." + p[2] + "." + p[1] + "." + p[0])
		}
	}
	if s, ok := strings.CutSuffix(name, ".ip6.arpa"); ok {
		p := strings.Split(s, ".")
		if len(p) == 32 {
			var b strings.Builder
			for i := 31; i >= 0; i-- {
				b.WriteString(p[i])
				if i%4 == 0 && i != 0 {
					b.WriteByte(':')
				}
			}
			return net.ParseIP(b.String())
		}
	}
	return nil
}
