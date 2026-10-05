package inetmail

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"mime"
	"path"
	"strings"
	"time"

	"github.com/emersion/go-message"
	gomail "github.com/emersion/go-message/mail"
	"golang.org/x/net/idna"

	_ "github.com/emersion/go-message/charset" // reads the letters that are not in UTF-8
)

// ParseLimits bound what is taken from a letter; the defaults are the ones the gateway uses.
type ParseLimits struct {
	MaxText            int   // bytes of plain text that are kept (default 1 MiB)
	MaxHTML            int   // bytes of HTML read before it is cleaned (default 2 MiB)
	MaxParts           int   // parts of the letter that are looked at (default 300)
	MaxAttachments     int   // files that are kept (default 100)
	MaxAttachmentBytes int64 // the size of one file (default 40 MiB)
}

func (l ParseLimits) withDefaults() ParseLimits {
	if l.MaxText <= 0 {
		l.MaxText = 1 << 20
	}
	if l.MaxHTML <= 0 {
		l.MaxHTML = 2 << 20
	}
	if l.MaxParts <= 0 {
		l.MaxParts = 300
	}
	if l.MaxAttachments <= 0 {
		l.MaxAttachments = 100
	}
	if l.MaxAttachmentBytes <= 0 {
		l.MaxAttachmentBytes = 40 << 20
	}
	return l
}

// ParsedAttachment is a file that came with a letter.
type ParsedAttachment struct {
	Name      string `json:"name"`
	Mime      string `json:"mime"`
	Size      int64  `json:"size"`
	SHA256    string `json:"sha256"`
	Inline    bool   `json:"inline,omitempty"`    // a picture that the HTML of the letter shows (cid:)
	ContentID string `json:"contentId,omitempty"` // what the HTML calls it, without <>
}

// Parsed is what is read from a letter.
type Parsed struct {
	MessageID  string
	From       []Address
	To, Cc     []Address
	ReplyTo    []Address
	Subject    string
	Date       time.Time // zero when the letter has none (or an unreadable one)
	InReplyTo  string
	References []string
	Text       string // plain text (made from the HTML when the letter has only that)
	HTML       string // cleaned (SanitizeHTML), empty when the letter has none
	// RemoteImages counts the pictures of the HTML that live on the Internet: they stay in the HTML, and the interface
	// does not load them unless the person asks.
	RemoteImages int
	Attachments  []ParsedAttachment
	// Truncated is true when something was left out: a part that was too long, too many parts or files, a part that
	// could not be read.
	Truncated bool
}

// AttachmentStore keeps a file of a letter (the blob store of the node) and says what to call it by: its SHA-256 and
// its size.
type AttachmentStore func(name, mimeType string, r io.Reader) (sha string, size int64, err error)

func lenientAddresses(l []*gomail.Address) []Address {
	var out []Address
	for _, m := range l {
		at := strings.LastIndexByte(m.Address, '@')
		if at <= 0 || at == len(m.Address)-1 {
			continue
		}
		dom := strings.ToLower(strings.TrimSuffix(m.Address[at+1:], "."))
		if a, err := idna.Lookup.ToASCII(dom); err == nil {
			dom = a
		}
		out = append(out, Address{Name: cleanName(m.Name), Local: clip(m.Address[:at], maxLocal), Domain: clip(dom, maxDomain)})
		if len(out) >= 100 {
			break
		}
	}
	return out
}

func okReadErr(err error) bool {
	return err == nil || message.IsUnknownCharset(err) || message.IsUnknownEncoding(err)
}

// ParseMessage reads a letter. Its files are handed to store (nil: they are only counted and hashed), its HTML is
// cleaned. A letter that is broken half way gives what could be read, with Truncated set; one that cannot be read at
// all (not even its header) is an error.
func ParseMessage(r io.Reader, store AttachmentStore, lim ParseLimits) (p *Parsed, err error) {
	defer func() {
		if rec := recover(); rec != nil {
			p, err = nil, fmt.Errorf("the letter cannot be read: %v", rec)
		}
	}()
	lim = lim.withDefaults()
	mr, err := gomail.CreateReader(r)
	if !okReadErr(err) {
		return nil, fmt.Errorf("the letter cannot be read: %w", err)
	}
	defer mr.Close()
	p = &Parsed{}
	h := mr.Header
	p.Subject, _ = h.Subject()
	p.Subject = clip(cleanHeader(p.Subject), 1000)
	if l, err := h.AddressList("From"); err == nil || len(l) > 0 {
		p.From = lenientAddresses(l)
	}
	for key, dst := range map[string]*[]Address{"To": &p.To, "Cc": &p.Cc, "Reply-To": &p.ReplyTo} {
		if l, err := h.AddressList(key); err == nil || len(l) > 0 {
			*dst = lenientAddresses(l)
		}
	}
	p.MessageID, _ = h.MessageID()
	if ids, _ := h.MsgIDList("In-Reply-To"); len(ids) > 0 {
		p.InReplyTo = ids[0]
	}
	if ids, _ := h.MsgIDList("References"); len(ids) > 0 {
		if len(ids) > 50 {
			ids = ids[len(ids)-50:]
		}
		p.References = ids
	}
	if d, err := h.Date(); err == nil && !d.IsZero() {
		p.Date = d
	}

	var texts, htmls []string
	htmlBytes := 0
	parts := 0
	for {
		part, err := mr.NextPart()
		if err == io.EOF {
			break
		}
		if !okReadErr(err) {
			p.Truncated = true
			break
		}
		if parts++; parts > lim.MaxParts {
			p.Truncated = true
			break
		}
		switch ph := part.Header.(type) {
		case *gomail.InlineHeader:
			ct, params, _ := ph.ContentType()
			switch ct {
			case "text/plain", "":
				b, cut := readLimited(part.Body, lim.MaxText)
				p.Truncated = p.Truncated || cut
				texts = append(texts, normalizeText(b))
			case "text/html":
				b, cut := readLimited(part.Body, lim.MaxHTML-htmlBytes)
				htmlBytes += len(b)
				p.Truncated = p.Truncated || cut
				htmls = append(htmls, strings.ToValidUTF8(string(b), "�"))
			default: // a picture or another file that the letter shows in its text
				if _, ok := keepAttachment(p, part.Body, store, lim, params["name"], ct, cidOf(ph.Header), true); !ok {
					p.Truncated = true
				}
			}
		case *gomail.AttachmentHeader:
			ct, params, _ := ph.ContentType()
			name, _ := ph.Filename()
			if name == "" {
				name = params["name"]
			}
			if _, ok := keepAttachment(p, part.Body, store, lim, name, ct, cidOf(ph.Header), false); !ok {
				p.Truncated = true
			}
		}
	}

	p.Text = strings.TrimSpace(strings.Join(nonEmpty(texts), "\n\n"))
	if len(htmls) > 0 {
		s := SanitizeHTML(strings.Join(htmls, "\n"), SanitizeOptions{RemoteImages: true})
		p.HTML = s.HTML
		p.RemoteImages = s.Remote
		if p.Text == "" {
			p.Text = HTMLToText(p.HTML)
		}
	}
	if len(p.Text) > lim.MaxText {
		p.Text = strings.ToValidUTF8(p.Text[:lim.MaxText], "")
		p.Truncated = true
	}
	return p, nil
}

func nonEmpty(l []string) []string {
	var out []string
	for _, s := range l {
		if strings.TrimSpace(s) != "" {
			out = append(out, s)
		}
	}
	return out
}

func cidOf(h message.Header) string {
	return clip(trimID(h.Get("Content-Id")), 200)
}

// readLimited reads at most max bytes of r; cut tells that there was more.
func readLimited(r io.Reader, max int) (b []byte, cut bool) {
	if max < 0 {
		max = 0
	}
	b, _ = io.ReadAll(io.LimitReader(r, int64(max)+1))
	if len(b) > max {
		return b[:max], true
	}
	return b, false
}

func normalizeText(b []byte) string {
	s := strings.ToValidUTF8(string(b), "�")
	s = strings.ReplaceAll(strings.ReplaceAll(s, "\r\n", "\n"), "\r", "\n")
	return strings.Map(func(r rune) rune {
		if (r < 32 && r != '\n' && r != '\t') || r == 127 {
			return -1
		}
		return r
	}, s)
}

type countingReader struct {
	r io.Reader
	n int64
}

func (c *countingReader) Read(b []byte) (int, error) {
	n, err := c.r.Read(b)
	c.n += int64(n)
	return n, err
}

// keepAttachment stores one file of a letter and records it. ok is false when it was left out.
func keepAttachment(p *Parsed, body io.Reader, store AttachmentStore, lim ParseLimits, name, typ, cid string, inline bool) (ParsedAttachment, bool) {
	if len(p.Attachments) >= lim.MaxAttachments {
		return ParsedAttachment{}, false
	}
	if mt, _, err := mime.ParseMediaType(typ); err == nil && mt != "" {
		typ = mt
	} else {
		typ = "application/octet-stream"
	}
	if strings.TrimSpace(name) == "" {
		switch {
		case typ == "message/rfc822":
			name = "message.eml"
		case typ == "text/calendar":
			name = "invite.ics"
		case strings.HasPrefix(typ, "image/"):
			name = "image." + path.Base(typ)
		default:
			name = "attachment"
		}
	}
	name = cleanAttachmentName(name)
	src := &countingReader{r: io.LimitReader(body, lim.MaxAttachmentBytes+1)}
	var sha string
	var size int64
	if store != nil {
		var err error
		sha, size, err = store(name, typ, src)
		if err != nil {
			return ParsedAttachment{}, false
		}
	} else {
		h := sha256.New()
		n, _ := io.Copy(h, src)
		sha, size = hex.EncodeToString(h.Sum(nil)), n
	}
	if src.n > lim.MaxAttachmentBytes {
		return ParsedAttachment{}, false // too big: what was stored is the caller's to forget (it is not listed)
	}
	a := ParsedAttachment{Name: name, Mime: typ, Size: size, SHA256: sha, Inline: inline && cid != "", ContentID: cid}
	p.Attachments = append(p.Attachments, a)
	return a, true
}

// ErrTooLarge is what the receiving side says about a letter that is longer than it takes.
var ErrTooLarge = errors.New("the letter is too large")
