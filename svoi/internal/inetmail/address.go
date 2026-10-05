package inetmail

import (
	"errors"
	"fmt"
	"net"
	"net/mail"
	"regexp"
	"strings"

	"golang.org/x/net/idna"
)

// Address is one mailbox: a display name (optional) and local@domain. The domain is always in its ASCII form
// (punycode) and lower case; the local part is kept as written (case matters to nobody in practice, but it is not ours
// to change for other people's addresses).
type Address struct {
	Name   string
	Local  string
	Domain string
}

// Addr is "local@domain".
func (a Address) Addr() string {
	if a.Local == "" && a.Domain == "" {
		return ""
	}
	return a.Local + "@" + a.Domain
}

// Header is the address as a header value: Name <local@domain> with the name encoded as it has to be.
func (a Address) Header() string {
	return (&mail.Address{Name: a.Name, Address: a.Addr()}).String()
}

// Display is what a person reads: the name if there is one, else the address.
func (a Address) Display() string {
	if strings.TrimSpace(a.Name) != "" {
		return a.Name
	}
	return a.Addr()
}

// Same reports whether two addresses are the same mailbox.
func (a Address) Same(b Address) bool {
	return strings.EqualFold(a.Local, b.Local) && a.Domain == b.Domain
}

const (
	maxLocal   = 64
	maxDomain  = 253
	maxAddress = 254
)

// localAtext is what the local part of an address may be made of when it is not quoted (RFC 5322 atext and the dot).
var localRe = regexp.MustCompile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$")

// ParseAddress reads one address: "a@b.c" or "Name <a@b.c>". Addresses that are legal but that nobody uses and that are
// a classic way to confuse a mail system are refused: quoted local parts, domain literals ([1.2.3.4]), names without a
// dot, non-ASCII local parts.
func ParseAddress(s string) (Address, error) {
	s = strings.TrimSpace(s)
	if s == "" || len(s) > 998 {
		return Address{}, errors.New("empty or too long address")
	}
	m, err := mail.ParseAddress(s)
	if err != nil {
		return Address{}, fmt.Errorf("not an address: %q", clip(s, 60))
	}
	return fromNet(m)
}

// ParseAddressList reads "a@b, Name <c@d>"; an empty string is an empty list.
func ParseAddressList(s string) ([]Address, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	list, err := mail.ParseAddressList(s)
	if err != nil {
		return nil, fmt.Errorf("not a list of addresses: %q", clip(s, 60))
	}
	out := make([]Address, 0, len(list))
	for _, m := range list {
		a, err := fromNet(m)
		if err != nil {
			return nil, err
		}
		out = append(out, a)
	}
	return out, nil
}

func fromNet(m *mail.Address) (Address, error) {
	at := strings.LastIndexByte(m.Address, '@')
	if at <= 0 || at == len(m.Address)-1 {
		return Address{}, fmt.Errorf("not an address: %q", clip(m.Address, 60))
	}
	local, dom := m.Address[:at], m.Address[at+1:]
	if len(local) > maxLocal || !localRe.MatchString(local) {
		return Address{}, fmt.Errorf("the part of %q before @ is not usable", clip(m.Address, 60))
	}
	d, err := CleanDomain(dom)
	if err != nil {
		return Address{}, err
	}
	a := Address{Name: cleanName(m.Name), Local: local, Domain: d}
	if len(a.Addr()) > maxAddress {
		return Address{}, errors.New("address is too long")
	}
	return a, nil
}

// CleanDomain turns a domain into its canonical ASCII form (lower case, punycode, no trailing dot) and refuses what
// cannot be a mail domain: no dot, an IP address, a label that is too long or has odd characters.
func CleanDomain(d string) (string, error) { return cleanHostName(d, false) }

// CleanDNSName is CleanDomain for a name that is looked up in the DNS but is not the domain of anybody's mail: the names that SPF
// records include (_spf.example.net) begin with an underscore.
func CleanDNSName(d string) (string, error) { return cleanHostName(d, true) }

func cleanHostName(d string, underscores bool) (string, error) {
	d = strings.TrimSuffix(strings.TrimSpace(d), ".")
	if d == "" || strings.ContainsAny(d, " \t\r\n@<>[]()\\,;:\"") {
		return "", fmt.Errorf("not a domain: %q", clip(d, 60))
	}
	a := strings.ToLower(d)
	if !underscores || !isASCII(d) { // (the IDNA rules refuse the underscore, which is the point of this variant)
		var err error
		if a, err = idna.Lookup.ToASCII(d); err != nil {
			return "", fmt.Errorf("not a domain: %q", clip(d, 60))
		}
		a = strings.ToLower(a)
	}
	if len(a) > maxDomain || net.ParseIP(a) != nil {
		return "", fmt.Errorf("not a domain: %q", clip(d, 60))
	}
	labels := strings.Split(a, ".")
	if len(labels) < 2 {
		return "", fmt.Errorf("%q has no dot: it is not a domain on the Internet", clip(d, 60))
	}
	for _, l := range labels {
		if l == "" || len(l) > 63 || l[0] == '-' || l[len(l)-1] == '-' {
			return "", fmt.Errorf("not a domain: %q", clip(d, 60))
		}
		for i := 0; i < len(l); i++ {
			c := l[i]
			if !(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || underscores && c == '_') {
				return "", fmt.Errorf("not a domain: %q", clip(d, 60))
			}
		}
	}
	if allDigits(labels[len(labels)-1]) {
		return "", fmt.Errorf("not a domain: %q", clip(d, 60))
	}
	return a, nil
}

func isASCII(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] >= 0x80 {
			return false
		}
	}
	return true
}

func allDigits(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] < '0' || s[i] > '9' {
			return false
		}
	}
	return s != ""
}

// ownLocalRe is what the name of a mailbox of ours may look like (the part before @): short, plain, easy to dictate.
var ownLocalRe = regexp.MustCompile(`^[a-z0-9][a-z0-9._-]{0,30}[a-z0-9]$`)

// ReservedLocals are names that belong to the mail system itself (RFC 2142 and 5321): nobody can have them as a
// mailbox. "postmaster" is answered by the gateway's owner.
var ReservedLocals = map[string]bool{
	"postmaster": true, "abuse": true, "mailer-daemon": true, "hostmaster": true, "webmaster": true,
	"root": true, "noc": true, "security": true,
}

// CleanMailboxName checks a name for a new mailbox ("andrey") and returns it in the form it is stored in (lower case).
func CleanMailboxName(s string) (string, error) {
	s = strings.ToLower(strings.TrimSpace(s))
	switch {
	case s == "":
		return "", errors.New("the name of the mailbox is empty")
	case !ownLocalRe.MatchString(s) || strings.Contains(s, ".."):
		return "", errors.New("a mailbox name is latin letters, digits, dot, dash and underscore (2 to 32 characters, starting and ending with a letter or a digit)")
	case ReservedLocals[s]:
		return "", fmt.Errorf("%q belongs to the mail system itself, choose another name", s)
	}
	return s, nil
}

// cleanName strips what must not be in a display name that is shown or put in a header.
func cleanName(s string) string {
	s = strings.Map(func(r rune) rune {
		if r < 32 || r == 127 {
			return ' '
		}
		return r
	}, s)
	s = strings.Join(strings.Fields(s), " ")
	return clip(s, 200)
}

func clip(s string, n int) string {
	if len(s) <= n {
		return s
	}
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n]) + "…"
}
