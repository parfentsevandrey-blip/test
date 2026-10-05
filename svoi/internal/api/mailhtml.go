package api

import (
	"encoding/base64"
	"html"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
)

// The formatted text (HTML) of a letter from the Internet is somebody else's code. The interface shows it in a frame
// without scripts; this is the page that frame loads. It carries three walls of its own, so that a letter that got
// through the cleaning of internal/inetmail still can do nothing: a policy that forbids everything but its own styles
// and pictures that are part of the letter (data:), no referrer for the links that are followed, and nothing cached.

const (
	maxInlinePicture = 2 << 20 // a picture that is part of a letter, put in the page as data:
	maxInlineTotal   = 8 << 20
)

var (
	cidSrc = regexp.MustCompile(`src="cid:([^"]{1,300})"`)
	// the pictures that live on the Internet: what the cleaning keeps of them is src="https://..." (see inetmail.SanitizeHTML)
	remoteSrc = regexp.MustCompile(`\s(?:src|srcset)="[^"]*"`)
)

// pageHead gives a letter the white paper that mail has always been read on, whatever theme the interface has: the
// colours inside a letter are chosen by its sender for that.
const pageHead = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">` +
	`<meta name="color-scheme" content="light"><meta name="referrer" content="no-referrer">` +
	`<style>html{background:#fff;color:#1b1c1f}body{margin:0;padding:14px 16px;font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;overflow-wrap:anywhere}` +
	`img{max-width:100%;height:auto}table{max-width:100%}pre{white-space:pre-wrap}a{color:#0a58ca}blockquote{margin:0 0 0 4px;padding-left:12px;border-left:3px solid #d0d4da;color:#4a4f57}</style></head><body>`

func (s *Server) handleMailHTML(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	text, ok := s.app.Mail().HTMLOf(id)
	if !ok || text == "" {
		writeError(w, errCode("notfound", "this letter has no formatted text"))
		return
	}
	if msg, ok := s.app.Mail().Get(id); ok {
		text = s.inlinePictures(id, msg, text)
	}
	// The pictures of the Internet are never loaded: a picture tells its sender that the letter was opened, when, and from where. They are
	// taken out of the page, and the policy forbids them as well (what the cleaning of internal/inetmail kept of them is src="https://...").
	text = remoteSrc.ReplaceAllStringFunc(text, func(m string) string {
		if strings.Contains(m, `"data:`) {
			return m
		}
		return ""
	})
	h := w.Header()
	h.Set("Content-Type", "text/html; charset=utf-8")
	h.Set("Content-Security-Policy", "default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'")
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("Referrer-Policy", "no-referrer")
	h.Set("Cache-Control", "private, no-store")
	_, _ = io.WriteString(w, pageHead+text+"</body></html>")
}

// inlineImageTypes are the pictures a letter may bring along: what the bytes say counts, not what the letter claims.
var inlineImageTypes = map[string]bool{"image/png": true, "image/jpeg": true, "image/gif": true, "image/webp": true}

// inlinePictures replaces src="cid:..." by the picture the letter brought (as data:), when this device has it already.
func (s *Server) inlinePictures(id string, msg *mail.Message, text string) string {
	if !strings.Contains(text, `src="cid:`) {
		return text
	}
	budget := int64(maxInlineTotal)
	done := map[string]string{}
	return cidSrc.ReplaceAllStringFunc(text, func(m string) string {
		cid := html.UnescapeString(cidSrc.FindStringSubmatch(m)[1])
		if uri, ok := done[cid]; ok {
			return `src="` + uri + `"`
		}
		for i, a := range msg.Attachments {
			if a.ContentID == "" || !strings.EqualFold(a.ContentID, cid) || a.Size <= 0 || a.Size > maxInlinePicture || a.Size > budget {
				continue
			}
			path, _, err := s.app.Mail().AttachmentFile(id, i)
			if err != nil {
				continue
			}
			f, err := os.Open(path)
			if err != nil {
				continue
			}
			b, err := io.ReadAll(io.LimitReader(f, maxInlinePicture+1))
			f.Close()
			if err != nil || len(b) > maxInlinePicture {
				continue
			}
			ct := http.DetectContentType(b)
			if !inlineImageTypes[ct] {
				continue
			}
			budget -= int64(len(b))
			uri := "data:" + ct + ";base64," + base64.StdEncoding.EncodeToString(b)
			done[cid] = uri
			return `src="` + uri + `"`
		}
		return m
	})
}
