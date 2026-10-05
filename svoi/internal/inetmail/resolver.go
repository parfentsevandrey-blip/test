// Package inetmail is the part of The Mesh that speaks Internet mail (SMTP, MIME, DKIM, SPF, DMARC), so that a mesh
// can have mailboxes of its own on a domain of its own, receive letters from Gmail and others, and write to them.
//
// It knows nothing about the mesh: the gateway (internal/mail) hands it letters to send and takes the letters it
// receives. What is here:
//
//   - addresses and MIME: building a letter to send (text, HTML, attachments) and reading one that arrived, with a
//     sanitiser for the HTML of other people's letters (sanitize.go);
//   - the proofs of who wrote a letter: DKIM (signing ours, checking theirs), SPF and DMARC (auth.go);
//   - the DNS records a domain needs and a check of them (dns.go);
//   - the SMTP server that receives (server.go) and the queue and the sender that deliver (queue.go, send.go).
//
// Everything that touches the network takes a Resolver and a dialer, so that the tests run on a DNS and a mail
// server of their own (fakedns.go) and nothing leaves the machine.
package inetmail

import (
	"context"
	"errors"
	"net"
	"strings"
	"time"
)

// Resolver is the part of net.Resolver that this package uses (net.DefaultResolver satisfies it). It is also what
// blitiri.com.ar/go/spf wants, so the same resolver serves SPF.
type Resolver interface {
	LookupTXT(ctx context.Context, name string) ([]string, error)
	LookupMX(ctx context.Context, name string) ([]*net.MX, error)
	LookupIPAddr(ctx context.Context, host string) ([]net.IPAddr, error)
	LookupAddr(ctx context.Context, addr string) ([]string, error)
}

// SystemResolver asks the DNS of the operating system.
var SystemResolver Resolver = net.DefaultResolver

// dnsTimeout bounds one group of lookups that a check or a delivery waits for.
const dnsTimeout = 15 * time.Second

// isNotFound reports whether err says "this name has no such record" (as opposed to "the DNS did not answer").
func isNotFound(err error) bool {
	var de *net.DNSError
	return errors.As(err, &de) && de.IsNotFound
}

// fqdn makes a DNS name comparable: lower case, no trailing dot.
func fqdn(s string) string {
	return strings.ToLower(strings.TrimSuffix(strings.TrimSpace(s), "."))
}

// lookupTXT returns the TXT records of name; a name without any is not an error.
func lookupTXT(ctx context.Context, r Resolver, name string) ([]string, error) {
	txt, err := r.LookupTXT(ctx, name)
	if err != nil {
		if isNotFound(err) {
			return nil, nil
		}
		return nil, err
	}
	return txt, nil
}
