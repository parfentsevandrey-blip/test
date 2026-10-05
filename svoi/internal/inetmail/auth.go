package inetmail

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"strings"

	"blitiri.com.ar/go/spf"
	"github.com/emersion/go-msgauth/dkim"
	"github.com/emersion/go-msgauth/dmarc"
	"golang.org/x/net/publicsuffix"
)

// ---- DKIM: the signature of our letters ----

// DKIMKey is the key a domain signs its letters with: the private half stays on the gateway, the public half is
// published in DNS under <selector>._domainkey.<domain>. RSA 2048 is what every big receiver understands.
type DKIMKey struct {
	Selector string
	priv     *rsa.PrivateKey
}

// NewDKIMKey makes a new key for a selector (a short word: "mesh", or "mesh2025" when it is replaced).
func NewDKIMKey(selector string) (*DKIMKey, error) {
	selector = strings.ToLower(strings.TrimSpace(selector))
	if !selectorRe(selector) {
		return nil, errors.New("the selector of a DKIM key is letters, digits and dashes")
	}
	k, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, err
	}
	return &DKIMKey{Selector: selector, priv: k}, nil
}

func selectorRe(s string) bool {
	if s == "" || len(s) > 40 {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if !(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-') {
			return false
		}
	}
	return true
}

// ParseDKIMKey reads a key saved with PEM.
func ParseDKIMKey(selector string, pemBytes []byte) (*DKIMKey, error) {
	b, _ := pem.Decode(pemBytes)
	if b == nil {
		return nil, errors.New("the DKIM key is not PEM")
	}
	k, err := x509.ParsePKCS8PrivateKey(b.Bytes)
	if err != nil {
		return nil, fmt.Errorf("the DKIM key cannot be read: %w", err)
	}
	rk, ok := k.(*rsa.PrivateKey)
	if !ok {
		return nil, errors.New("the DKIM key is not an RSA key")
	}
	return &DKIMKey{Selector: selector, priv: rk}, nil
}

// PEM is the private key to be stored (PKCS#8).
func (k *DKIMKey) PEM() []byte {
	der, _ := x509.MarshalPKCS8PrivateKey(k.priv)
	return pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})
}

// DNSValue is the text of the TXT record that publishes the public key.
func (k *DKIMKey) DNSValue() string {
	der, _ := x509.MarshalPKIXPublicKey(&k.priv.PublicKey)
	return "v=DKIM1; k=rsa; p=" + base64.StdEncoding.EncodeToString(der)
}

// RecordName is where that record goes.
func (k *DKIMKey) RecordName(domain string) string { return k.Selector + "._domainkey." + domain }

// signedHeaders are the header fields a signature covers; a field that a letter does not have is covered too, so that
// nobody can add one afterwards.
var signedHeaders = []string{
	"From", "To", "Cc", "Subject", "Date", "Message-ID", "In-Reply-To", "References", "Reply-To",
	"MIME-Version", "Content-Type", "Content-Transfer-Encoding",
}

// Signature computes the DKIM-Signature header field (with its line break) for the letter that r reads. It is put in
// front of the letter when the letter is sent, so the letter itself is stored as it was made.
func (k *DKIMKey) Signature(domain string, r io.Reader) (string, error) {
	s, err := dkim.NewSigner(&dkim.SignOptions{
		Domain: domain, Selector: k.Selector, Signer: k.priv, Hash: crypto.SHA256,
		HeaderCanonicalization: dkim.CanonicalizationRelaxed, BodyCanonicalization: dkim.CanonicalizationRelaxed,
		HeaderKeys: signedHeaders,
	})
	if err != nil {
		return "", err
	}
	if _, err := io.Copy(s, r); err != nil {
		_ = s.Close()
		return "", err
	}
	if err := s.Close(); err != nil {
		return "", err
	}
	return s.Signature(), nil
}

// DKIMResult is the verdict on one signature of a letter we received.
type DKIMResult struct {
	Domain string `json:"domain"`
	Pass   bool   `json:"pass"`
	Err    string `json:"err,omitempty"`
}

// VerifyDKIM checks every signature of the letter that r reads.
func VerifyDKIM(ctx context.Context, res Resolver, r io.Reader) []DKIMResult {
	vs, err := dkim.VerifyWithOptions(r, &dkim.VerifyOptions{
		LookupTXT: func(name string) ([]string, error) {
			c, cancel := context.WithTimeout(ctx, dnsTimeout)
			defer cancel()
			return res.LookupTXT(c, name)
		},
		MaxVerifications: 5,
	})
	var out []DKIMResult
	for _, v := range vs {
		d := DKIMResult{Domain: fqdn(v.Domain), Pass: v.Err == nil}
		if v.Err != nil {
			d.Err = v.Err.Error()
		}
		out = append(out, d)
	}
	if err != nil && len(out) == 0 && !errors.Is(err, dkim.ErrTooManySignatures) {
		// a letter that cannot be read at all has no signatures to speak of
		return nil
	}
	return out
}

// ---- SPF: which machines may send for a domain ----

// SPFResult is the answer of an SPF check, in the words of RFC 7208.
type SPFResult string

// The results of an SPF check.
const (
	SPFNone      SPFResult = "none"
	SPFNeutral   SPFResult = "neutral"
	SPFPass      SPFResult = "pass"
	SPFFail      SPFResult = "fail"
	SPFSoftFail  SPFResult = "softfail"
	SPFTempError SPFResult = "temperror"
	SPFPermError SPFResult = "permerror"
)

// CheckSPF asks whether the machine at ip may send mail for the domain of mailFrom (or of helo, when the sender of the
// envelope is empty: a bounce).
func CheckSPF(ctx context.Context, res Resolver, ip net.IP, helo, mailFrom string) SPFResult {
	if ip == nil {
		return SPFNone
	}
	sender := mailFrom
	if sender == "" {
		sender = "postmaster@" + helo
	}
	r, _ := spf.CheckHostWithSender(ip, helo, sender, spf.WithContext(ctx), spf.WithResolver(res))
	switch r {
	case spf.Pass:
		return SPFPass
	case spf.Fail:
		return SPFFail
	case spf.SoftFail:
		return SPFSoftFail
	case spf.Neutral:
		return SPFNeutral
	case spf.TempError:
		return SPFTempError
	case spf.PermError:
		return SPFPermError
	}
	return SPFNone
}

// ---- DMARC: what a domain wants done with letters that fail ----

// DMARCResult is the verdict of DMARC on a letter.
type DMARCResult struct {
	// Result: "pass", "fail", "none" (the domain has no policy) or "temperror" (the DNS did not answer).
	Result string `json:"result"`
	// Policy is what the domain asks to be done with a letter that fails: "none", "quarantine" or "reject".
	Policy     string `json:"policy,omitempty"`
	FromDomain string `json:"fromDomain,omitempty"`
}

// OrgDomain is the registrable part of a domain ("mail.example.co.uk" → "example.co.uk").
func OrgDomain(d string) string {
	d = fqdn(d)
	if o, err := publicsuffix.EffectiveTLDPlusOne(d); err == nil {
		return o
	}
	return d
}

func aligned(strict bool, a, b string) bool {
	a, b = fqdn(a), fqdn(b)
	if a == "" || b == "" {
		return false
	}
	if strict {
		return a == b
	}
	return OrgDomain(a) == OrgDomain(b)
}

// EvalDMARC evaluates DMARC for a letter whose From domain is fromDomain, given what SPF and DKIM found. spfDomain is
// the domain SPF was checked for (that of the envelope sender, or the HELO name).
func EvalDMARC(ctx context.Context, res Resolver, fromDomain string, spfRes SPFResult, spfDomain string, dk []DKIMResult) DMARCResult {
	fromDomain = fqdn(fromDomain)
	out := DMARCResult{Result: "none", FromDomain: fromDomain}
	if fromDomain == "" {
		return out
	}
	look := func(d string) (*dmarc.Record, error) {
		return dmarc.LookupWithOptions(d, &dmarc.LookupOptions{LookupTXT: func(name string) ([]string, error) {
			c, cancel := context.WithTimeout(ctx, dnsTimeout)
			defer cancel()
			return res.LookupTXT(c, name)
		}})
	}
	rec, err := look(fromDomain)
	fromOrg := OrgDomain(fromDomain)
	subdomain := false
	if errors.Is(err, dmarc.ErrNoPolicy) && fromOrg != fromDomain {
		rec, err = look(fromOrg)
		subdomain = true
	}
	if err != nil {
		if errors.Is(err, dmarc.ErrNoPolicy) {
			return out
		}
		if dmarc.IsTempFail(err) {
			out.Result = "temperror"
		}
		return out // a record that cannot be read is the same as no record
	}
	policy := string(rec.Policy)
	if subdomain && rec.SubdomainPolicy != "" {
		policy = string(rec.SubdomainPolicy)
	}
	out.Policy = policy
	strictSPF, strictDKIM := rec.SPFAlignment == dmarc.AlignmentStrict, rec.DKIMAlignment == dmarc.AlignmentStrict
	pass := spfRes == SPFPass && aligned(strictSPF, spfDomain, fromDomain)
	for _, d := range dk {
		if d.Pass && aligned(strictDKIM, d.Domain, fromDomain) {
			pass = true
		}
	}
	if pass {
		out.Result = "pass"
		return out
	}
	out.Result = "fail"
	if rec.Percent != nil && *rec.Percent < 100 {
		// "pct": the policy is applied to that share of the letters that fail, the rest is treated as "none"
		if n, err := rand.Int(rand.Reader, big.NewInt(100)); err == nil && int(n.Int64()) >= *rec.Percent {
			out.Policy = "none"
		}
	}
	return out
}

// Auth is everything that was found out about who wrote a letter we received.
type Auth struct {
	SPF       SPFResult    `json:"spf"`
	SPFDomain string       `json:"spfDomain,omitempty"`
	DKIM      []DKIMResult `json:"dkim,omitempty"`
	DMARC     DMARCResult  `json:"dmarc"`
}

// Verdicts.
const (
	VerdictVerified   = "verified"   // the domain of the sender vouches for the letter (DMARC passes)
	VerdictUnverified = "unverified" // nothing proves it, nothing disproves it
	VerdictSuspicious = "suspicious" // checks failed
)

// Verdict sums the checks up for a person: a letter is "verified" when it passes DMARC, or - for a domain that has no
// policy - when a signature of the sender's own domain checks out; "suspicious" when DMARC or SPF failed.
func (a Auth) Verdict() string {
	switch {
	case a.DMARC.Result == "pass":
		return VerdictVerified
	case a.DMARC.Result == "fail" || a.SPF == SPFFail:
		return VerdictSuspicious
	}
	for _, d := range a.DKIM {
		if d.Pass && a.DMARC.FromDomain != "" && aligned(false, d.Domain, a.DMARC.FromDomain) {
			return VerdictVerified
		}
	}
	return VerdictUnverified
}

// Header is the value of the Authentication-Results header field that a receiver puts on a letter (RFC 8601).
func (a Auth) Header(host string) string {
	var b strings.Builder
	b.WriteString(host)
	b.WriteString(";")
	fmt.Fprintf(&b, " spf=%s", a.SPF)
	if a.SPFDomain != "" {
		fmt.Fprintf(&b, " smtp.mailfrom=%s", a.SPFDomain)
	}
	if len(a.DKIM) == 0 {
		b.WriteString("; dkim=none")
	}
	for _, d := range a.DKIM {
		res := "pass"
		if !d.Pass {
			res = "fail"
		}
		fmt.Fprintf(&b, "; dkim=%s header.d=%s", res, d.Domain)
	}
	fmt.Fprintf(&b, "; dmarc=%s", a.DMARC.Result)
	if a.DMARC.FromDomain != "" {
		fmt.Fprintf(&b, " header.from=%s", a.DMARC.FromDomain)
	}
	return b.String()
}
