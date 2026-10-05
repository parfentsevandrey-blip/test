package inetmail

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"mime"
	"strings"
	"time"

	"github.com/emersion/go-message/mail"
)

// OutAttachment is a file that goes with a letter. Open is called when the letter is written, so a big file is never
// held in memory.
type OutAttachment struct {
	Name string
	Mime string
	Open func() (io.ReadCloser, error)
}

// OutMessage is a letter to be sent.
type OutMessage struct {
	From       Address
	To, Cc     []Address
	Subject    string
	Text       string
	HTML       string
	InReplyTo  string   // Message-ID of the letter this one answers, with or without <>
	References []string // Message-IDs of the conversation
	MessageID  string   // without <>; made up when empty
	Date       time.Time
	// Mailer is shown as X-Mailer ("The Mesh"): receivers like a letter to say what wrote it.
	Mailer      string
	Attachments []OutAttachment
}

// NewMessageID makes a Message-ID (without <>) that is unique and belongs to domain.
func NewMessageID(domain string) string {
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	return fmt.Sprintf("%d.%s@%s", time.Now().UnixNano(), hex.EncodeToString(b), domain)
}

func trimID(s string) string {
	s = strings.TrimSpace(s)
	s = strings.TrimPrefix(s, "<")
	s = strings.TrimSuffix(s, ">")
	return s
}

func netAddrs(l []Address) []*mail.Address {
	out := make([]*mail.Address, len(l))
	for i, a := range l {
		out[i] = &mail.Address{Name: a.Name, Address: a.Addr()}
	}
	return out
}

// BuildMIME writes the letter m to w as an RFC 5322 message: a plain text letter when there is only text, text and HTML
// as alternatives, and a mixed message when files go with it. It returns the Message-ID that was used (without <>).
func BuildMIME(w io.Writer, m *OutMessage) (string, error) {
	if m.From.Local == "" || m.From.Domain == "" {
		return "", errors.New("the letter has no sender")
	}
	if len(m.To)+len(m.Cc) == 0 {
		return "", errors.New("the letter has no recipient")
	}
	var h mail.Header
	date := m.Date
	if date.IsZero() {
		date = time.Now()
	}
	h.SetDate(date.UTC().Truncate(time.Second))
	h.SetAddressList("From", netAddrs([]Address{m.From}))
	if len(m.To) > 0 {
		h.SetAddressList("To", netAddrs(m.To))
	}
	if len(m.Cc) > 0 {
		h.SetAddressList("Cc", netAddrs(m.Cc))
	}
	h.SetSubject(cleanHeader(m.Subject))
	id := trimID(m.MessageID)
	if id == "" {
		id = NewMessageID(m.From.Domain)
	}
	h.SetMessageID(id)
	if r := trimID(m.InReplyTo); r != "" {
		h.SetMsgIDList("In-Reply-To", []string{r})
	}
	if len(m.References) > 0 {
		refs := make([]string, 0, len(m.References))
		for _, r := range m.References {
			if r = trimID(r); r != "" {
				refs = append(refs, r)
			}
		}
		if len(refs) > 0 {
			h.SetMsgIDList("References", refs)
		}
	}
	h.Set("MIME-Version", "1.0")
	if m.Mailer != "" {
		h.Set("X-Mailer", cleanHeader(m.Mailer))
	}

	text := m.Text
	if text == "" && m.HTML != "" {
		text = HTMLToText(m.HTML)
	}
	text = strings.ReplaceAll(strings.ReplaceAll(text, "\r\n", "\n"), "\r", "\n")

	writeBodies := func(open func(mail.InlineHeader) (io.WriteCloser, error)) error {
		var th mail.InlineHeader
		th.Set("Content-Type", "text/plain; charset=utf-8")
		tw, err := open(th)
		if err != nil {
			return err
		}
		if _, err := io.WriteString(tw, text); err != nil {
			return err
		}
		return tw.Close()
	}

	switch {
	case len(m.Attachments) == 0 && m.HTML == "":
		h.Set("Content-Type", "text/plain; charset=utf-8")
		iw, err := mail.CreateSingleInlineWriter(w, h)
		if err != nil {
			return "", err
		}
		if _, err := io.WriteString(iw, text); err != nil {
			return "", err
		}
		return id, iw.Close()

	case len(m.Attachments) == 0:
		iw, err := mail.CreateInlineWriter(w, h)
		if err != nil {
			return "", err
		}
		if err := writeAlternative(iw, text, m.HTML); err != nil {
			return "", err
		}
		return id, iw.Close()
	}

	mw, err := mail.CreateWriter(w, h)
	if err != nil {
		return "", err
	}
	if m.HTML != "" {
		iw, err := mw.CreateInline()
		if err != nil {
			return "", err
		}
		if err := writeAlternative(iw, text, m.HTML); err != nil {
			return "", err
		}
		if err := iw.Close(); err != nil {
			return "", err
		}
	} else if err := writeBodies(mw.CreateSingleInline); err != nil {
		return "", err
	}
	for _, a := range m.Attachments {
		if err := writeAttachment(mw, a); err != nil {
			return "", err
		}
	}
	return id, mw.Close()
}

func writeAlternative(iw *mail.InlineWriter, text, html string) error {
	var th mail.InlineHeader
	th.Set("Content-Type", "text/plain; charset=utf-8")
	tw, err := iw.CreatePart(th)
	if err != nil {
		return err
	}
	if _, err := io.WriteString(tw, text); err != nil {
		return err
	}
	if err := tw.Close(); err != nil {
		return err
	}
	var hh mail.InlineHeader
	hh.Set("Content-Type", "text/html; charset=utf-8")
	hw, err := iw.CreatePart(hh)
	if err != nil {
		return err
	}
	if _, err := io.WriteString(hw, strings.ReplaceAll(strings.ReplaceAll(html, "\r\n", "\n"), "\r", "\n")); err != nil {
		return err
	}
	return hw.Close()
}

func writeAttachment(mw *mail.Writer, a OutAttachment) error {
	rc, err := a.Open()
	if err != nil {
		return fmt.Errorf("attachment %q: %w", a.Name, err)
	}
	defer rc.Close()
	var ah mail.AttachmentHeader
	typ := a.Mime
	if mt, _, err := mime.ParseMediaType(typ); err != nil || mt == "" || strings.ContainsAny(typ, "\r\n") {
		typ = "application/octet-stream"
	}
	ah.Set("Content-Type", typ)
	ah.SetFilename(cleanAttachmentName(a.Name))
	w, err := mw.CreateAttachment(ah)
	if err != nil {
		return err
	}
	if _, err := io.Copy(w, rc); err != nil {
		return err
	}
	return w.Close()
}

// cleanHeader makes a value fit in one header field: no line breaks, no control characters.
func cleanHeader(s string) string {
	s = strings.Map(func(r rune) rune {
		if r < 32 || r == 127 {
			return ' '
		}
		return r
	}, s)
	return strings.TrimSpace(s)
}

// cleanAttachmentName is the name of a file as it goes into a letter or comes out of one: no folders, no control
// characters, not empty, not long.
func cleanAttachmentName(s string) string {
	s = strings.ReplaceAll(s, "\\", "/")
	if i := strings.LastIndexByte(s, '/'); i >= 0 {
		s = s[i+1:]
	}
	s = strings.TrimSpace(cleanHeader(s))
	s = strings.Trim(s, ". ")
	if s == "" {
		s = "file"
	}
	if r := []rune(s); len(r) > 150 {
		s = string(r[:150])
	}
	return s
}
