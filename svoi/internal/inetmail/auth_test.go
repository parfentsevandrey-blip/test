package inetmail

import (
	"bytes"
	"context"
	"net"
	"strings"
	"testing"
)

const plainLetter = "From: Alice <alice@example.org>\r\nTo: bob@example.net\r\nSubject: Hello\r\nDate: Mon, 02 Jan 2006 15:04:05 +0000\r\n" +
	"Message-ID: <abc@example.org>\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nHi Bob,\r\nhow are you?\r\n"

func signedWith(t *testing.T, k *DKIMKey, domain, letter string) string {
	t.Helper()
	sig, err := k.Signature(domain, strings.NewReader(letter))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(sig, "DKIM-Signature:") || !strings.HasSuffix(sig, "\r\n") {
		t.Fatalf("a signature is a header field with its line break: %q", sig)
	}
	return sig + letter
}

func TestDKIMRoundTrip(t *testing.T) {
	k, err := NewDKIMKey("mesh")
	if err != nil {
		t.Fatal(err)
	}
	dns := NewFakeDNS()
	dns.AddTXT(k.RecordName("example.org"), k.DNSValue())
	ctx := context.Background()

	signed := signedWith(t, k, "example.org", plainLetter)
	res := VerifyDKIM(ctx, dns, strings.NewReader(signed))
	if len(res) != 1 || !res[0].Pass || res[0].Domain != "example.org" {
		t.Fatalf("a signed letter must verify: %+v", res)
	}

	// the key survives being stored
	k2, err := ParseDKIMKey("mesh", k.PEM())
	if err != nil || k2.DNSValue() != k.DNSValue() {
		t.Fatalf("PEM round trip: %v", err)
	}

	// changes to what is signed break the signature: the body, a signed header, a header that was not there
	for name, bad := range map[string]string{
		"body":    strings.Replace(signed, "how are you?", "send me money", 1),
		"subject": strings.Replace(signed, "Subject: Hello", "Subject: Hallo", 1),
		"added":   strings.Replace(signed, "Subject: Hello", "Subject: Hello\r\nSubject: Another", 1),
		"from":    strings.Replace(signed, "alice@example.org", "ceo@example.org", 1),
	} {
		res := VerifyDKIM(ctx, dns, strings.NewReader(bad))
		if len(res) != 1 || res[0].Pass {
			t.Errorf("%s changed: the signature must fail, got %+v", name, res)
		}
	}

	// a key that is not published cannot be verified
	other := NewFakeDNS()
	if res := VerifyDKIM(ctx, other, strings.NewReader(signed)); len(res) != 1 || res[0].Pass {
		t.Fatalf("no key in DNS: %+v", res)
	}
	// and a letter without a signature has no results
	if res := VerifyDKIM(ctx, dns, strings.NewReader(plainLetter)); len(res) != 0 {
		t.Fatalf("no signature: %+v", res)
	}
}

func TestDKIMKeyChecks(t *testing.T) {
	if _, err := NewDKIMKey("bad selector"); err == nil {
		t.Error("a selector with a space must be refused")
	}
	if _, err := ParseDKIMKey("x", []byte("not pem")); err == nil {
		t.Error("garbage is not a key")
	}
}

func TestSPF(t *testing.T) {
	dns := NewFakeDNS()
	dns.AddTXT("example.org", "v=spf1 ip4:203.0.113.7 -all")
	dns.AddTXT("soft.example", "v=spf1 ip4:203.0.113.7 ~all")
	ctx := context.Background()
	ok, other := net.ParseIP("203.0.113.7"), net.ParseIP("198.51.100.9")
	for _, c := range []struct {
		ip     net.IP
		domain string
		want   SPFResult
	}{
		{ok, "example.org", SPFPass},
		{other, "example.org", SPFFail},
		{other, "soft.example", SPFSoftFail},
		{ok, "nospf.example", SPFNone},
	} {
		if got := CheckSPF(ctx, dns, c.ip, "mail."+c.domain, "a@"+c.domain); got != c.want {
			t.Errorf("%s from %s: got %s, want %s", c.domain, c.ip, got, c.want)
		}
	}
	dns.Fail("broken.example", true)
	if got := CheckSPF(ctx, dns, ok, "x", "a@broken.example"); got != SPFTempError {
		t.Errorf("a DNS that does not answer: %s", got)
	}
	// a bounce has no sender: the HELO name is checked
	if got := CheckSPF(ctx, dns, ok, "example.org", ""); got != SPFPass {
		t.Errorf("bounce: %s", got)
	}
}

func TestDMARC(t *testing.T) {
	dns := NewFakeDNS()
	dns.AddTXT("_dmarc.example.org", "v=DMARC1; p=reject")
	dns.AddTXT("_dmarc.strict.example", "v=DMARC1; p=quarantine; adkim=s; aspf=s")
	dns.AddTXT("_dmarc.sub.example", "v=DMARC1; p=reject; sp=none")
	ctx := context.Background()
	pass := []DKIMResult{{Domain: "example.org", Pass: true}}
	fail := []DKIMResult{{Domain: "example.org", Pass: false}}

	for _, c := range []struct {
		name       string
		from       string
		spf        SPFResult
		spfDom     string
		dkim       []DKIMResult
		result     string
		policy     string
		wantPolicy bool
	}{
		{"dkim aligned", "example.org", SPFNone, "", pass, "pass", "reject", true},
		{"spf aligned", "example.org", SPFPass, "bounce.example.org", fail, "pass", "reject", true},
		{"spf pass but for someone else", "example.org", SPFPass, "other.example", nil, "fail", "reject", true},
		{"dkim of another domain", "example.org", SPFNone, "", []DKIMResult{{Domain: "other.example", Pass: true}}, "fail", "reject", true},
		{"subdomain sees the policy of the organisation", "mail.example.org", SPFNone, "", pass, "pass", "reject", true},
		{"no record at all", "nodmarc.example", SPFPass, "nodmarc.example", pass, "none", "", false},
		{"strict alignment refuses a subdomain", "strict.example", SPFPass, "mail.strict.example", nil, "fail", "quarantine", true},
		{"sp for subdomains", "x.sub.example", SPFNone, "", nil, "fail", "none", true},
	} {
		got := EvalDMARC(ctx, dns, c.from, c.spf, c.spfDom, c.dkim)
		if got.Result != c.result || (c.wantPolicy && got.Policy != c.policy) {
			t.Errorf("%s: got %+v, want %s/%s", c.name, got, c.result, c.policy)
		}
	}
	dns.Fail("_dmarc.broken.example", true)
	if got := EvalDMARC(ctx, dns, "broken.example", SPFNone, "", nil); got.Result != "temperror" {
		t.Errorf("DNS failure: %+v", got)
	}
}

func TestVerdictAndHeader(t *testing.T) {
	a := Auth{SPF: SPFPass, SPFDomain: "example.org", DKIM: []DKIMResult{{Domain: "example.org", Pass: true}}, DMARC: DMARCResult{Result: "pass", FromDomain: "example.org"}}
	if a.Verdict() != VerdictVerified {
		t.Errorf("verdict %s", a.Verdict())
	}
	h := a.Header("mx.example.net")
	for _, want := range []string{"mx.example.net;", "spf=pass smtp.mailfrom=example.org", "dkim=pass header.d=example.org", "dmarc=pass header.from=example.org"} {
		if !strings.Contains(h, want) {
			t.Errorf("%q is not in %q", want, h)
		}
	}
	// no policy, but the sender's own signature checks out
	if v := (Auth{SPF: SPFNone, DKIM: []DKIMResult{{Domain: "mail.example.org", Pass: true}}, DMARC: DMARCResult{Result: "none", FromDomain: "example.org"}}).Verdict(); v != VerdictVerified {
		t.Errorf("signature of the own domain: %s", v)
	}
	if v := (Auth{SPF: SPFNone, DMARC: DMARCResult{Result: "none", FromDomain: "example.org"}}).Verdict(); v != VerdictUnverified {
		t.Errorf("nothing at all: %s", v)
	}
	if v := (Auth{SPF: SPFFail, DMARC: DMARCResult{Result: "none"}}).Verdict(); v != VerdictSuspicious {
		t.Errorf("spf fail: %s", v)
	}
	if v := (Auth{SPF: SPFPass, DMARC: DMARCResult{Result: "fail"}}).Verdict(); v != VerdictSuspicious {
		t.Errorf("dmarc fail: %s", v)
	}
	if !bytes.Contains([]byte((Auth{DMARC: DMARCResult{Result: "none"}}).Header("h")), []byte("dkim=none")) {
		t.Error("no signature is said")
	}
}

func TestOrgDomain(t *testing.T) {
	for in, want := range map[string]string{
		"mail.example.org": "example.org", "example.org": "example.org", "a.b.example.co.uk": "example.co.uk",
		"Mail.Example.ORG.": "example.org", "foo.пример.рф": "пример.рф",
	} {
		if got := OrgDomain(in); got != want {
			t.Errorf("%s: got %s, want %s", in, got, want)
		}
	}
}
