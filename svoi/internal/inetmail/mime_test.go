package inetmail

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"strings"
	"testing"
	"time"
)

func bytesOpen(b []byte) func() (io.ReadCloser, error) {
	return func() (io.ReadCloser, error) { return io.NopCloser(bytes.NewReader(b)), nil }
}

// memStore keeps the files of a letter in memory and names them by their hash, like the blob store does.
type memStore map[string][]byte

func (m memStore) put(name, mimeType string, r io.Reader) (string, int64, error) {
	b, err := io.ReadAll(r)
	if err != nil {
		return "", 0, err
	}
	sum := sha256.Sum256(b)
	sha := hex.EncodeToString(sum[:])
	m[sha] = b
	return sha, int64(len(b)), nil
}

func TestBuildPlainLetter(t *testing.T) {
	var buf bytes.Buffer
	when := time.Date(2026, 5, 17, 9, 30, 0, 0, time.UTC)
	id, err := BuildMIME(&buf, &OutMessage{
		From:    Address{Name: "Андрей", Local: "andrey", Domain: "example.org"},
		To:      []Address{{Name: "Alice", Local: "alice", Domain: "gmail.com"}},
		Cc:      []Address{{Local: "bob", Domain: "example.net"}},
		Subject: "Привет, мир", Text: "Первая строка\nВторая строка\n", InReplyTo: "<orig@gmail.com>",
		References: []string{"<root@gmail.com>", "orig@gmail.com"}, Date: when, Mailer: "The Mesh",
	})
	if err != nil {
		t.Fatal(err)
	}
	raw := buf.String()
	if strings.Contains(strings.ReplaceAll(raw, "\r\n", ""), "\n") {
		t.Errorf("every line must end with CRLF:\n%q", raw)
	}
	for _, want := range []string{
		"Date: Sun, 17 May 2026 09:30:00 +0000", "Message-Id: <" + id + ">", "Mime-Version: 1.0", "X-Mailer: The Mesh",
		"Content-Type: text/plain; charset=utf-8", "In-Reply-To: <orig@gmail.com>", "References: <root@gmail.com> <orig@gmail.com>",
		"<andrey@example.org>", "<alice@gmail.com>", "bob@example.net",
	} {
		if !strings.Contains(strings.ToLower(raw), strings.ToLower(want)) {
			t.Errorf("%q is missing from:\n%s", want, raw)
		}
	}
	if strings.Contains(raw, "Привет") {
		t.Errorf("a subject in Russian must be encoded in the header:\n%s", raw)
	}

	p, err := ParseMessage(strings.NewReader(raw), nil, ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if p.Subject != "Привет, мир" || p.Text != "Первая строка\nВторая строка" || p.MessageID != id {
		t.Errorf("round trip: %+v", p)
	}
	if len(p.From) != 1 || p.From[0].Name != "Андрей" || p.From[0].Addr() != "andrey@example.org" {
		t.Errorf("From: %+v", p.From)
	}
	if len(p.To) != 1 || p.To[0].Addr() != "alice@gmail.com" || len(p.Cc) != 1 {
		t.Errorf("To/Cc: %+v %+v", p.To, p.Cc)
	}
	if p.InReplyTo != "orig@gmail.com" || len(p.References) != 2 || !p.Date.Equal(when) {
		t.Errorf("threading: %q %v %v", p.InReplyTo, p.References, p.Date)
	}
	if p.HTML != "" || len(p.Attachments) != 0 || p.Truncated {
		t.Errorf("a plain letter has no HTML and no files: %+v", p)
	}
}

func TestBuildWithHTMLAndAttachments(t *testing.T) {
	blob := make([]byte, 100_000)
	for i := range blob {
		blob[i] = byte(i * 7)
	}
	var buf bytes.Buffer
	_, err := BuildMIME(&buf, &OutMessage{
		From: Address{Local: "me", Domain: "example.org"}, To: []Address{{Local: "you", Domain: "example.com"}},
		Subject: "Отчёт", Text: "see attached", HTML: "<p>See <b>attached</b></p>",
		Attachments: []OutAttachment{
			{Name: "отчёт 2026.pdf", Mime: "application/pdf", Open: bytesOpen(blob)},
			{Name: "../../etc/passwd", Mime: "bad\r\nX-Evil: 1", Open: bytesOpen([]byte("secret"))},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	raw := buf.String()
	if strings.Contains(raw, "X-Evil") {
		t.Fatalf("a header was injected through a content type:\n%s", raw)
	}
	if !strings.Contains(raw, "multipart/mixed") || !strings.Contains(raw, "multipart/alternative") {
		t.Errorf("structure:\n%s", raw[:600])
	}
	st := memStore{}
	p, err := ParseMessage(strings.NewReader(raw), st.put, ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if p.Text != "see attached" || !strings.Contains(p.HTML, "<b>attached</b>") {
		t.Errorf("bodies: %q %q", p.Text, p.HTML)
	}
	if len(p.Attachments) != 2 {
		t.Fatalf("attachments: %+v", p.Attachments)
	}
	a := p.Attachments[0]
	if a.Name != "отчёт 2026.pdf" || a.Mime != "application/pdf" || a.Size != int64(len(blob)) || !bytes.Equal(st[a.SHA256], blob) {
		t.Errorf("first attachment: %+v", a)
	}
	b := p.Attachments[1]
	if b.Name != "passwd" || b.Mime != "application/octet-stream" || string(st[b.SHA256]) != "secret" {
		t.Errorf("second attachment (a folder in the name and a bad type): %+v", b)
	}
}

func TestBuildRefusesEmptyEnvelope(t *testing.T) {
	var buf bytes.Buffer
	if _, err := BuildMIME(&buf, &OutMessage{To: []Address{{Local: "a", Domain: "example.org"}}, Text: "x"}); err == nil {
		t.Error("no sender")
	}
	if _, err := BuildMIME(&buf, &OutMessage{From: Address{Local: "a", Domain: "example.org"}, Text: "x"}); err == nil {
		t.Error("no recipient")
	}
}

func TestSubjectCannotInjectHeaders(t *testing.T) {
	var buf bytes.Buffer
	_, err := BuildMIME(&buf, &OutMessage{
		From: Address{Local: "a", Domain: "example.org"}, To: []Address{{Local: "b", Domain: "example.com"}},
		Subject: "hi\r\nBcc: victim@example.com", Text: "x",
	})
	if err != nil {
		t.Fatal(err)
	}
	head, _, _ := strings.Cut(buf.String(), "\r\n\r\n")
	for _, line := range strings.Split(head, "\r\n") {
		if strings.HasPrefix(strings.ToLower(line), "bcc:") {
			t.Fatalf("a Bcc header was injected:\n%s", head)
		}
	}
}

func TestParseLegacyEncodings(t *testing.T) {
	// windows-1251 in base64, a subject in koi8-r in an encoded word, a letter in Latin-1 quoted-printable
	raw := "From: =?koi8-r?B?8NLJ18XU?= <old@example.ru>\r\nTo: you@example.org\r\nSubject: =?koi8-r?B?8NLJ18XU?=\r\n" +
		"MIME-Version: 1.0\r\nContent-Type: text/plain; charset=windows-1251\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
		"z/Do4uXy\r\n"
	p, err := ParseMessage(strings.NewReader(raw), nil, ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if p.Subject != "Привет" || p.Text != "Привет" || p.From[0].Name != "Привет" {
		t.Errorf("legacy encodings: %q %q %q", p.Subject, p.Text, p.From[0].Name)
	}
	q := "From: a@example.org\r\nTo: b@example.org\r\nSubject: caf=?iso-8859-1?Q?=E9?=\r\nContent-Type: text/plain; charset=iso-8859-1\r\n" +
		"Content-Transfer-Encoding: quoted-printable\r\n\r\ncaf=E9 cr=E8me\r\n"
	p, err = ParseMessage(strings.NewReader(q), nil, ParseLimits{})
	if err != nil || p.Text != "café crème" || p.Subject != "café" {
		t.Errorf("latin-1: %+v %v", p, err)
	}
	// a charset that nobody knows must not lose the letter
	u := "From: a@example.org\r\nTo: b@example.org\r\nSubject: x\r\nContent-Type: text/plain; charset=x-nonsense\r\n\r\nplain ascii body\r\n"
	p, err = ParseMessage(strings.NewReader(u), nil, ParseLimits{})
	if err != nil || !strings.Contains(p.Text, "plain ascii body") {
		t.Errorf("unknown charset: %+v %v", p, err)
	}
}

func TestParseRelatedInlineImage(t *testing.T) {
	raw := "From: shop@example.org\r\nTo: you@example.com\r\nSubject: Receipt\r\nMIME-Version: 1.0\r\n" +
		"Content-Type: multipart/related; boundary=REL\r\n\r\n" +
		"--REL\r\nContent-Type: text/html; charset=utf-8\r\n\r\n<p>Thanks!</p><img src=\"cid:logo@shop\" width=\"40\"><img src=\"https://tracker.example/p.gif\">\r\n" +
		"--REL\r\nContent-Type: image/png; name=\"logo.png\"\r\nContent-Transfer-Encoding: base64\r\nContent-ID: <logo@shop>\r\nContent-Disposition: inline; filename=\"logo.png\"\r\n\r\n" +
		"iVBORw0KGgo=\r\n--REL--\r\n"
	st := memStore{}
	p, err := ParseMessage(strings.NewReader(raw), st.put, ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if len(p.Attachments) != 1 || !p.Attachments[0].Inline || p.Attachments[0].ContentID != "logo@shop" || p.Attachments[0].Name != "logo.png" {
		t.Fatalf("inline image: %+v", p.Attachments)
	}
	if !strings.Contains(p.HTML, `src="cid:logo@shop"`) || p.RemoteImages != 1 {
		t.Errorf("html: %q (remote %d)", p.HTML, p.RemoteImages)
	}
	if p.Text != "Thanks!" {
		t.Errorf("text made from the HTML: %q", p.Text)
	}
}

func TestParseLimitsAndBrokenLetters(t *testing.T) {
	big := strings.Repeat("A", 3000)
	raw := "From: a@example.org\r\nTo: b@example.org\r\nSubject: x\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=B\r\n\r\n" +
		"--B\r\nContent-Type: text/plain\r\n\r\nbody\r\n" +
		"--B\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=big.bin\r\n\r\n" + big + "\r\n" +
		"--B\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=small.bin\r\n\r\nsmall\r\n--B--\r\n"
	p, err := ParseMessage(strings.NewReader(raw), nil, ParseLimits{MaxAttachmentBytes: 1000})
	if err != nil {
		t.Fatal(err)
	}
	if len(p.Attachments) != 1 || p.Attachments[0].Name != "small.bin" || !p.Truncated {
		t.Errorf("a file over the limit is left out, and the letter says so: %+v truncated=%v", p.Attachments, p.Truncated)
	}
	// a letter cut off in the middle gives what there is
	cut := raw[:strings.Index(raw, "--B\r\nContent-Type: application/octet-stream")+60]
	if p, err := ParseMessage(strings.NewReader(cut), nil, ParseLimits{}); err != nil || p.Text != "body" {
		t.Errorf("cut letter: %+v %v", p, err)
	}
	// text that is longer than the limit is cut, and the letter says so
	long := "From: a@example.org\r\nTo: b@example.org\r\nSubject: x\r\nContent-Type: text/plain\r\n\r\n" + strings.Repeat("word ", 100)
	if p, _ := ParseMessage(strings.NewReader(long), nil, ParseLimits{MaxText: 50}); len(p.Text) > 50 || !p.Truncated {
		t.Errorf("long text: %d %v", len(p.Text), p.Truncated)
	}
	// nested depth and part counts stay bounded
	var nest strings.Builder
	nest.WriteString("From: a@example.org\r\nSubject: x\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=B\r\n\r\n")
	for i := 0; i < 400; i++ {
		nest.WriteString("--B\r\nContent-Type: text/plain\r\n\r\nx\r\n")
	}
	nest.WriteString("--B--\r\n")
	if p, err := ParseMessage(strings.NewReader(nest.String()), nil, ParseLimits{MaxParts: 50}); err != nil || !p.Truncated {
		t.Errorf("too many parts: %v %v", p != nil && p.Truncated, err)
	}
}
