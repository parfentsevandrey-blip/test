// Package api is the local HTTP interface of a svoi device: the JSON API the web
// UI talks to (docs/UI-API.md), the live event stream and the embedded UI itself.
package api

import (
	"crypto/subtle"
	"errors"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

const cookieName = "svoi_session"

// Server serves the API and the UI.
type Server struct {
	app      *app.App
	mux      *http.ServeMux
	ui       fs.FS
	loopback bool // listening on a loopback address only
	srv      *http.Server
	ln       net.Listener
}

// New creates the server. ui may be nil (API only).
func New(a *app.App, ui fs.FS) *Server {
	s := &Server{app: a, ui: ui, mux: http.NewServeMux(), loopback: true}
	s.routes()
	s.registerRemoteHandler()
	return s
}

// Handler returns the full handler (middleware included), for tests.
func (s *Server) Handler() http.Handler { return http.HandlerFunc(s.serveHTTP) }

// Listen binds the address. If the port is taken and strict is false, the next
// free ports are tried, so a second instance still comes up.
func (s *Server) Listen(addr string, strict bool) (net.Listener, error) {
	host, portStr, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	port, _ := strconv.Atoi(portStr)
	var ln net.Listener
	for i := 0; i < 30; i++ {
		ln, err = net.Listen("tcp", net.JoinHostPort(host, strconv.Itoa(port+i)))
		if err == nil || strict || port == 0 {
			break
		}
	}
	if err != nil {
		return nil, err
	}
	s.ln = ln
	if ip := net.ParseIP(host); host != "localhost" && (ip == nil || !ip.IsLoopback()) {
		s.loopback = false
	}
	return ln, nil
}

// Serve runs the server on ln until it is closed.
func (s *Server) Serve(ln net.Listener) error {
	s.srv = &http.Server{
		Handler:           s.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	err := s.srv.Serve(ln)
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

// Close stops the server.
func (s *Server) Close() error {
	if s.srv != nil {
		return s.srv.Close()
	}
	return nil
}

// URL returns the address to open in a browser, including the one-time login token.
func (s *Server) URL() string {
	if s.ln == nil {
		return ""
	}
	_, port, _ := net.SplitHostPort(s.ln.Addr().String())
	host := "127.0.0.1"
	if !s.loopback {
		if h, _, err := net.SplitHostPort(s.ln.Addr().String()); err == nil && h != "" && h != "::" && h != "0.0.0.0" {
			host = h
		}
	}
	return fmt.Sprintf("http://%s:%s/?t=%s", host, port, url.QueryEscape(s.app.Token()))
}

// Addr returns the listening address.
func (s *Server) Addr() string {
	if s.ln == nil {
		return ""
	}
	return s.ln.Addr().String()
}

// ---- middleware ----

func hostOnly(h string) string {
	if host, _, err := net.SplitHostPort(h); err == nil {
		return strings.Trim(host, "[]")
	}
	return strings.Trim(h, "[]")
}

func (s *Server) hostAllowed(r *http.Request) bool {
	if !s.loopback {
		return true
	}
	switch strings.ToLower(hostOnly(r.Host)) {
	case "127.0.0.1", "localhost", "::1":
		return true
	}
	return false
}

func (s *Server) authorized(r *http.Request) bool {
	tok := s.app.Token()
	if c, err := r.Cookie(cookieName); err == nil && subtle.ConstantTimeCompare([]byte(c.Value), []byte(tok)) == 1 {
		return true
	}
	if h := r.Header.Get("Authorization"); strings.HasPrefix(h, "Bearer ") &&
		subtle.ConstantTimeCompare([]byte(strings.TrimPrefix(h, "Bearer ")), []byte(tok)) == 1 {
		return true
	}
	return false
}

func setSecurityHeaders(w http.ResponseWriter) {
	h := w.Header()
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("Referrer-Policy", "no-referrer")
	h.Set("X-Frame-Options", "SAMEORIGIN")
	h.Set("Content-Security-Policy",
		"default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; "+
			"script-src 'self'; connect-src 'self'; frame-src 'self'; object-src 'self'; base-uri 'none'; form-action 'self'")
}

func (s *Server) serveHTTP(w http.ResponseWriter, r *http.Request) {
	setSecurityHeaders(w)
	if !s.hostAllowed(r) {
		http.Error(w, "unexpected Host header", http.StatusMisdirectedRequest)
		return
	}
	// Login handshake: /?t=<token> sets the session cookie and drops the token from the URL.
	if t := r.URL.Query().Get("t"); t != "" && !strings.HasPrefix(r.URL.Path, "/api/") {
		if subtle.ConstantTimeCompare([]byte(t), []byte(s.app.Token())) == 1 {
			http.SetCookie(w, &http.Cookie{
				Name: cookieName, Value: s.app.Token(), Path: "/", HttpOnly: true,
				SameSite: http.SameSiteStrictMode, MaxAge: 365 * 24 * 3600,
			})
			q := r.URL.Query()
			q.Del("t")
			target := r.URL.Path
			if enc := q.Encode(); enc != "" {
				target += "?" + enc
			}
			http.Redirect(w, r, target, http.StatusFound)
			return
		}
	}
	if strings.HasPrefix(r.URL.Path, "/api/") {
		if !s.authorized(r) {
			writeError(w, errCode("unauthorized", "open the link printed by `svoi up` (or run `svoi open`) to sign in"))
			return
		}
		if r.Method != http.MethodGet && r.Method != http.MethodHead && r.Method != http.MethodOptions {
			if r.Header.Get("Authorization") == "" { // bearer-token clients (CLI) are not browsers
				if r.Header.Get("X-Svoi") != "1" {
					writeError(w, errCode("denied", "missing X-Svoi header"))
					return
				}
				if o := r.Header.Get("Origin"); o != "" {
					if u, err := url.Parse(o); err != nil || u.Host != r.Host {
						writeError(w, errCode("denied", "cross-origin request refused"))
						return
					}
				}
			}
		}
		s.mux.ServeHTTP(w, r)
		return
	}
	s.serveUI(w, r)
}

// serveUI serves the embedded single-page app.
func (s *Server) serveUI(w http.ResponseWriter, r *http.Request) {
	if s.ui == nil {
		http.NotFound(w, r)
		return
	}
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	p := strings.TrimPrefix(r.URL.Path, "/")
	if p == "" {
		p = "index.html"
	}
	if _, err := fs.Stat(s.ui, p); err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Cache-Control", "no-cache")
	r2 := new(http.Request)
	*r2 = *r
	r2.URL = new(url.URL)
	*r2.URL = *r.URL
	r2.URL.Path = "/" + p
	http.FileServerFS(s.ui).ServeHTTP(w, r2)
}
