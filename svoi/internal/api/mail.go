package api

import (
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
)

func (s *Server) handleMailList(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	folder := q.Get("folder")
	if folder == "" {
		folder = mail.FolderInbox
	}
	limit, _ := strconv.Atoi(q.Get("limit"))
	before, _ := strconv.ParseInt(q.Get("before"), 10, 64)
	writeJSON(w, http.StatusOK, s.app.Mail().List(folder, q.Get("q"), limit, before))
}

func (s *Server) handleMailGet(w http.ResponseWriter, r *http.Request) {
	m, ok := s.app.Mail().Get(r.PathValue("id"))
	if !ok {
		writeError(w, errCode("notfound", "no such message"))
		return
	}
	writeJSON(w, http.StatusOK, m)
}

func (s *Server) parseRecipients(ids []string) ([]identity.ID, error) {
	var out []identity.ID
	for _, p := range ids {
		id, err := s.app.PeerID(p)
		if err != nil {
			return nil, err
		}
		out = append(out, id)
	}
	return out, nil
}

func (s *Server) handleMailSend(w http.ResponseWriter, r *http.Request) {
	var in struct {
		To          []string `json:"to"`
		Subject     string   `json:"subject"`
		Body        string   `json:"body"`
		Attachments []string `json:"attachments"`
		InReplyTo   string   `json:"inReplyTo"`
	}
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	to, err := s.parseRecipients(in.To)
	if err != nil {
		writeError(w, err)
		return
	}
	id, err := s.app.Mail().Send(mail.SendInput{
		Kind: "mail", To: to, Subject: in.Subject, Body: in.Body, Attach: in.Attachments, InReplyTo: in.InReplyTo,
	})
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"id": id})
}

func (s *Server) handleMailFlags(w http.ResponseWriter, r *http.Request) {
	var f mail.Flags
	if err := decode(r, &f); err != nil {
		writeError(w, err)
		return
	}
	if err := s.app.Mail().SetFlags(r.PathValue("id"), f); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handleMailDelete(w http.ResponseWriter, r *http.Request) {
	if err := s.app.Mail().Delete(r.PathValue("id")); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) serveAttachment(w http.ResponseWriter, r *http.Request, id, idxStr string) {
	idx, err := strconv.Atoi(idxStr)
	if err != nil {
		writeError(w, errCode("invalid", "bad attachment index"))
		return
	}
	path, att, err := s.app.Mail().AttachmentFile(id, idx)
	if err != nil {
		writeError(w, err)
		return
	}
	f, err := os.Open(path)
	if err != nil {
		writeError(w, errCode("notfound", "the attachment is not available"))
		return
	}
	defer f.Close()
	st, _ := f.Stat()
	ctype := att.Mime
	if ctype == "" || ctype == "application/octet-stream" {
		ctype = files.MimeFor(att.Name)
	}
	serveContent(w, r, att.Name, ctype, st.ModTime(), f, r.URL.Query().Get("dl") == "1")
}

func (s *Server) handleMailAttachment(w http.ResponseWriter, r *http.Request) {
	s.serveAttachment(w, r, r.PathValue("id"), r.PathValue("index"))
}

func (s *Server) handleChatAttachment(w http.ResponseWriter, r *http.Request) {
	s.serveAttachment(w, r, r.PathValue("id"), r.PathValue("index"))
}

// handleAttachmentFetch is the user's consent to download an attachment that is
// too large to be fetched automatically (or a retry of one that failed).
func (s *Server) handleAttachmentFetch(w http.ResponseWriter, r *http.Request) {
	idx, err := strconv.Atoi(r.PathValue("index"))
	if err != nil {
		writeError(w, errCode("invalid", "bad attachment index"))
		return
	}
	if err := s.app.Mail().FetchAttachment(r.PathValue("id"), idx); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

// handleBlobUpload stages an attachment (raw body) and returns its hash.
func (s *Server) handleBlobUpload(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	name, _ := url.QueryUnescape(q.Get("name"))
	name = files.SanitizeName(name)
	ctype := q.Get("mime")
	if ctype == "" {
		ctype = files.MimeFor(name)
	}
	sha, size, err := s.app.Blobs().Put(r.Body)
	if err != nil {
		writeError(w, err)
		return
	}
	if !blob.ValidSHA(sha) {
		writeError(w, errCode("internal", "bad hash"))
		return
	}
	if err := s.app.Mail().RegisterUpload(sha, name, ctype, size); err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"id": sha, "name": name, "size": size, "mime": ctype})
}

// ---- chat ----

func (s *Server) handleChatThreads(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, nonNil(s.app.Mail().ChatThreads()))
}

func (s *Server) handleChatMessages(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "peer")
	if err != nil {
		writeError(w, err)
		return
	}
	q := r.URL.Query()
	limit, _ := strconv.Atoi(q.Get("limit"))
	before, _ := strconv.ParseInt(q.Get("before"), 10, 64)
	writeJSON(w, http.StatusOK, map[string]any{"messages": nonNil(s.app.Mail().ChatMessages(id, before, limit))})
}

func (s *Server) handleChatSend(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "peer")
	if err != nil {
		writeError(w, err)
		return
	}
	var in struct {
		Text        string   `json:"text"`
		Attachments []string `json:"attachments"`
	}
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	if strings.TrimSpace(in.Text) == "" && len(in.Attachments) == 0 {
		writeError(w, errCode("invalid", "the message is empty"))
		return
	}
	mid, err := s.app.Mail().Send(mail.SendInput{Kind: "chat", To: []identity.ID{id}, Body: in.Text, Attach: in.Attachments})
	if err != nil {
		writeError(w, err)
		return
	}
	cm, _, _ := s.app.Mail().ChatMessageByID(mid)
	writeJSON(w, http.StatusOK, cm)
}

func (s *Server) handleChatRead(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "peer")
	if err != nil {
		writeError(w, err)
		return
	}
	s.app.Mail().ChatRead(id)
	ok(w)
}

// ---- services & forwards ----

func (s *Server) handleServices(w http.ResponseWriter, r *http.Request) {
	list := s.app.Services()
	if list == nil {
		list = nil
	}
	writeJSON(w, http.StatusOK, nonNil(list))
}

func (s *Server) handleServiceSave(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Name        string   `json:"name"`
		Addr        string   `json:"addr"`
		Description string   `json:"description"`
		Allow       []string `json:"allow"`
	}
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	svc, err := s.app.SaveService(r.PathValue("id"), servicesFrom(in.Name, in.Addr, in.Description, in.Allow))
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, svc)
}

func (s *Server) handleServiceDelete(w http.ResponseWriter, r *http.Request) {
	if err := s.app.DeleteService(r.PathValue("id")); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handleForwards(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, nonNil(s.app.Forwards()))
}

func (s *Server) handleForwardAdd(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Peer    string `json:"peer"`
		Service string `json:"service"`
		Listen  string `json:"listen"`
	}
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	id, err := s.app.PeerID(in.Peer)
	if err != nil {
		writeError(w, err)
		return
	}
	fw, err := s.app.AddForward(id, in.Service, in.Listen)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, fw)
}

func (s *Server) handleForwardDelete(w http.ResponseWriter, r *http.Request) {
	if err := s.app.DeleteForward(r.PathValue("id")); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

var _ = time.Second
