package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net/http"
	"path"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

type apiError struct {
	Code string `json:"code"`
	Msg  string `json:"message"`
}

func (e *apiError) Error() string { return e.Code + ": " + e.Msg }

func errCode(code, msg string) error { return &apiError{Code: code, Msg: msg} }

var statusFor = map[string]int{
	"invalid":       http.StatusBadRequest,
	"unauthorized":  http.StatusUnauthorized,
	"denied":        http.StatusForbidden,
	"notfound":      http.StatusNotFound,
	"exists":        http.StatusConflict,
	"notconfigured": http.StatusPreconditionFailed,
	"toolarge":      http.StatusRequestEntityTooLarge,
	"offline":       http.StatusBadGateway,
	"expired":       http.StatusGone,
	"busy":          http.StatusServiceUnavailable,
	"unsupported":   http.StatusNotImplemented,
	"internal":      http.StatusInternalServerError,
}

// classify converts any error into an API error.
func classify(err error) *apiError {
	var ae *apiError
	if errors.As(err, &ae) {
		return ae
	}
	var re *mesh.RPCError
	if errors.As(err, &re) {
		return &apiError{Code: re.Code, Msg: re.Msg}
	}
	switch {
	case errors.Is(err, mesh.ErrNotConfigured):
		return &apiError{Code: "notconfigured", Msg: "this device is not part of a mesh yet"}
	case errors.Is(err, mesh.ErrNotAdmin):
		return &apiError{Code: "denied", Msg: "only an admin device can do this"}
	case errors.Is(err, context.DeadlineExceeded):
		return &apiError{Code: "busy", Msg: "the operation timed out"}
	case errors.Is(err, context.Canceled):
		return &apiError{Code: "busy", Msg: "the operation was canceled"}
	}
	return &apiError{Code: "internal", Msg: err.Error()}
}

func writeError(w http.ResponseWriter, err error) {
	ae := classify(err)
	status, ok := statusFor[ae.Code]
	if !ok {
		status = http.StatusInternalServerError
	}
	writeJSON(w, status, map[string]any{"error": ae})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	b, err := json.Marshal(v)
	if err != nil {
		http.Error(w, "encoding error", http.StatusInternalServerError)
		return
	}
	h := w.Header()
	h.Set("Content-Type", "application/json; charset=utf-8")
	h.Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_, _ = w.Write(b)
}

func ok(w http.ResponseWriter) { writeJSON(w, http.StatusOK, map[string]bool{"ok": true}) }

const maxJSONBody = 4 << 20

// decode reads a JSON request body into v; an empty body is allowed.
func decode(r *http.Request, v any) error {
	body := http.MaxBytesReader(nil, r.Body, maxJSONBody)
	b, err := io.ReadAll(body)
	if err != nil {
		return errCode("toolarge", "request body too large")
	}
	if len(strings.TrimSpace(string(b))) == 0 {
		return nil
	}
	if err := json.Unmarshal(b, v); err != nil {
		return errCode("invalid", "malformed JSON: "+err.Error())
	}
	return nil
}

// ---- serving file content safely ----

// dangerousTypes could execute script if served from the UI's origin, so they
// are always delivered as plain text (the UI shows them in a <pre>).
func safeContentType(ct, name string) string {
	mt, _, err := mime.ParseMediaType(ct)
	if err != nil || mt == "" {
		return "application/octet-stream"
	}
	switch mt {
	case "text/html", "application/xhtml+xml", "text/xml", "application/xml", "application/javascript",
		"text/javascript", "application/x-javascript", "text/css", "application/x-sh", "application/wasm":
		return "text/plain; charset=utf-8"
	}
	if strings.HasSuffix(mt, "+xml") && mt != "image/svg+xml" {
		return "text/plain; charset=utf-8"
	}
	return ct
}

func contentDisposition(name string, attachment bool) string {
	kind := "inline"
	if attachment {
		kind = "attachment"
	}
	return mime.FormatMediaType(kind, map[string]string{"filename": path.Base(name)})
}

// serveContent serves a seekable body with Range support under strict headers:
// whatever is inside the file, it can never run script in the UI's origin.
func serveContent(w http.ResponseWriter, r *http.Request, name, ctype string, mtime time.Time, rs io.ReadSeeker, download bool) {
	h := w.Header()
	h.Set("Content-Type", safeContentType(ctype, name))
	h.Set("Content-Disposition", contentDisposition(name, download))
	h.Set("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'; sandbox")
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("Cache-Control", "private, max-age=0, must-revalidate")
	http.ServeContent(w, r, name, mtime, rs)
}

// seekReader gives http.ServeContent a lazily-streamed view of a file that
// lives on another device. Seeking is free; bytes are requested from the remote
// device only when read, starting at the current position.
type seekReader struct {
	open func(off, n int64) (io.ReadCloser, error)
	size int64
	pos  int64
	cur  io.ReadCloser
}

func (s *seekReader) Read(p []byte) (int, error) {
	if s.pos >= s.size {
		return 0, io.EOF
	}
	if s.cur == nil {
		rc, err := s.open(s.pos, s.size-s.pos)
		if err != nil {
			return 0, err
		}
		s.cur = rc
	}
	n, err := s.cur.Read(p)
	s.pos += int64(n)
	if err == io.EOF {
		s.cur.Close()
		s.cur = nil
		if s.pos < s.size {
			return n, io.ErrUnexpectedEOF
		}
	}
	return n, err
}

func (s *seekReader) Seek(off int64, whence int) (int64, error) {
	var np int64
	switch whence {
	case io.SeekStart:
		np = off
	case io.SeekCurrent:
		np = s.pos + off
	case io.SeekEnd:
		np = s.size + off
	default:
		return 0, errors.New("bad whence")
	}
	if np < 0 {
		return 0, errors.New("negative position")
	}
	if np != s.pos && s.cur != nil {
		s.cur.Close()
		s.cur = nil
	}
	s.pos = np
	return np, nil
}

func (s *seekReader) Close() {
	if s.cur != nil {
		s.cur.Close()
		s.cur = nil
	}
}
