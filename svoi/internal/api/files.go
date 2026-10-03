package api

import (
	"context"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

func (s *Server) handleRemoteShares(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	list, err := s.app.Files().SharesAny(ctx, id)
	if err != nil {
		writeError(w, err)
		return
	}
	if list == nil {
		list = []files.RemoteShare{}
	}
	writeJSON(w, http.StatusOK, list)
}

func (s *Server) handleFS(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	defer cancel()
	q := r.URL.Query()
	res, err := s.app.Files().ListAny(ctx, id, q.Get("share"), q.Get("path"))
	if err != nil {
		writeError(w, err)
		return
	}
	if res.Entries == nil {
		res.Entries = []files.Entry{}
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleFSOp(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	var in struct{ Op, Share, Path, To string }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()
	if err := s.app.Files().OpAny(ctx, id, in.Share, in.Op, in.Path, in.To); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

// handleFileGet streams a file from this or a remote device with Range support.
func (s *Server) handleFileGet(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	q := r.URL.Query()
	fm := s.app.Files()
	download := q.Get("dl") == "1"
	if fm.IsLocal(id) {
		f, meta, err := fm.OpenRead(files.Actor{}, q.Get("share"), q.Get("path"))
		if err != nil {
			writeError(w, err)
			return
		}
		defer f.Close()
		serveContent(w, r, meta.Name, meta.Mime, time.Unix(meta.MTime, 0), f, download)
		return
	}
	ra, err := fm.OpenAny(r.Context(), id, q.Get("share"), q.Get("path"))
	if err != nil {
		writeError(w, err)
		return
	}
	sr := &seekReader{size: ra.Meta.Size, open: ra.Section}
	defer sr.Close()
	serveContent(w, r, ra.Meta.Name, ra.Meta.Mime, time.Unix(ra.Meta.MTime, 0), sr, download)
}

// handleFilePut uploads the request body into a read-write share.
func (s *Server) handleFilePut(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	q := r.URL.Query()
	overwrite := q.Get("overwrite") == "1"
	fm := s.app.Files()
	size := r.ContentLength
	if fm.IsLocal(id) {
		pf, err := fm.Create(files.Actor{}, q.Get("share"), q.Get("path"), overwrite)
		if err != nil {
			writeError(w, err)
			return
		}
		n, err := io.Copy(pf, r.Body)
		if err != nil {
			pf.Abort()
			writeError(w, err)
			return
		}
		if err := pf.Commit(); err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "size": n})
		return
	}
	n, err := fm.RemotePut(r.Context(), id, q.Get("share"), q.Get("path"), r.Body, size, overwrite)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "size": n})
}

func (s *Server) handleRemoteServices(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	list, err := s.app.RemoteServices(ctx, id)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, nonNil(list))
}

func nonNil[T any](s []T) []T {
	if s == nil {
		return []T{}
	}
	return s
}

// ---- transfers ----

func (s *Server) handleTransfers(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, nonNil(s.app.Files().Transfers()))
}

func (s *Server) handleTransferSend(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	var peers []identity.ID
	for _, p := range strings.Split(q.Get("to"), ",") {
		if p = strings.TrimSpace(p); p == "" {
			continue
		}
		id, err := s.app.PeerID(p)
		if err != nil {
			writeError(w, err)
			return
		}
		peers = append(peers, id)
	}
	if len(peers) == 0 {
		writeError(w, errCode("invalid", "choose at least one recipient"))
		return
	}
	name, _ := url.QueryUnescape(q.Get("name"))
	if strings.TrimSpace(name) == "" {
		writeError(w, errCode("invalid", "a file name is required"))
		return
	}
	trs, err := s.app.Files().SendFile(peers, name, q.Get("mime"), r.Body)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"transfers": trs})
}

func (s *Server) handleTransferAction(action string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		fm := s.app.Files()
		id := r.PathValue("id")
		var (
			t   files.Transfer
			err error
		)
		switch action {
		case "accept":
			t, err = fm.Accept(id)
		case "decline":
			t, err = fm.Decline(id)
		case "cancel":
			t, err = fm.Cancel(id)
		case "retry":
			t, err = fm.Retry(id)
		}
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, t)
	}
}

func (s *Server) handleTransferRemove(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if err := s.app.Files().Remove(id); err != nil {
		writeError(w, err)
		return
	}
	s.app.Hub().Publish("transfer.removed", map[string]string{"id": id})
	ok(w)
}

func (s *Server) handleTransferFile(w http.ResponseWriter, r *http.Request) {
	path, name, err := s.app.Files().ReceivedFile(r.PathValue("id"))
	if err != nil {
		writeError(w, err)
		return
	}
	f, err := os.Open(path)
	if err != nil {
		writeError(w, errCode("notfound", "the file was moved or deleted"))
		return
	}
	defer f.Close()
	st, _ := f.Stat()
	serveContent(w, r, name, files.MimeFor(name), st.ModTime(), f, r.URL.Query().Get("dl") != "0")
}

// ---- own shares ----

func (s *Server) handleShares(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, nonNil(s.app.Shares()))
}

func (s *Server) handleShareSave(w http.ResponseWriter, r *http.Request) {
	var in files.Share
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	v, err := s.app.SaveShare(r.PathValue("id"), in)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, v)
}

func (s *Server) handleShareDelete(w http.ResponseWriter, r *http.Request) {
	if err := s.app.DeleteShare(r.PathValue("id")); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handleLocalFS(w http.ResponseWriter, r *http.Request) {
	d, err := s.app.LocalFS(r.URL.Query().Get("path"))
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, d)
}

var _ = mesh.CodeInvalid
