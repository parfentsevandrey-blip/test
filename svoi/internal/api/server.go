// Package api is the local HTTP interface of a themesh device: the JSON API the web
// UI talks to (docs/UI-API.md), the live event stream and the embedded UI itself.
package api

import (
	"bytes"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"errors"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
)

const cookiePrefix = "themesh_session"

// cookieName: browsers share cookies between the ports of one host, so the session
// cookie carries this node's port in its name. Otherwise signing in to a second node
// on the same machine (the demo's four devices, two real nodes) would replace the
// first one's cookie and sign it out.
func (s *Server) cookieName() string {
	if s.ln != nil {
		if _, port, err := net.SplitHostPort(s.ln.Addr().String()); err == nil && port != "" {
			return cookiePrefix + "_" + port
		}
	}
	return cookiePrefix
}

// Server serves the API and the UI.
type Server struct {
	app      *app.App
	mux      *http.ServeMux
	ui       fs.FS
	uiOnce   sync.Once
	uiVer    string
	sess     *sessions
	loopback bool // listening on a loopback address only
	ln       net.Listener

	// callerUID says which user a loopback request comes from (see peerUID); tests replace it.
	callerUID func(*http.Request) (int, bool)

	mu     sync.Mutex // guards srv and closed: Close may race with Serve starting up
	srv    *http.Server
	closed bool
}

// New creates the server. ui may be nil (API only).
func New(a *app.App, ui fs.FS) *Server {
	s := &Server{app: a, ui: ui, mux: http.NewServeMux(), loopback: true, sess: newSessions(a.Dir()), callerUID: peerUID}
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
	srv := &http.Server{
		Handler:           s.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	s.mu.Lock()
	if s.closed { // Close won the race against this goroutine starting
		s.mu.Unlock()
		_ = ln.Close()
		return nil
	}
	s.srv = srv
	s.mu.Unlock()
	err := srv.Serve(ln)
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

// Close stops the server. It is safe to call at any moment, also right after Serve
// was started in another goroutine and before that goroutine got going.
func (s *Server) Close() error {
	s.mu.Lock()
	s.closed = true
	srv, ln := s.srv, s.ln
	s.mu.Unlock()
	if srv != nil {
		return srv.Close()
	}
	if ln != nil { // Serve has not started: free the port ourselves
		return ln.Close()
	}
	return nil
}

// URL returns the address to open in a browser. It carries a fresh single-use
// login code (valid for ten minutes), not the master token. Anybody who sees it can
// use it: it is for printing, not for a command line.
func (s *Server) URL() string { return s.loginURL(anyUser) }

// LocalURL is URL for a browser that is started on this machine by this process
// (xdg-open and the like): the link goes through a command line, so it only works
// for the user running this process (where the system can tell).
func (s *Server) LocalURL() string { return s.loginURL(os.Getuid()) }

func (s *Server) loginURL(uid int) string {
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
	return fmt.Sprintf("http://%s:%s/?t=%s", host, port, url.QueryEscape(s.sess.newCodeFor(uid)))
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

// localPath makes a path safe to put in a redirect: it stays on this site whatever
// the request asked for. Browsers read "//host", "/\host" (and these with tabs or
// line breaks in between, which they drop) as another site, so anything but plain
// path characters becomes "/".
func localPath(p string) string {
	if p == "" || p[0] != '/' || strings.HasPrefix(p, "//") {
		return "/"
	}
	for i := 0; i < len(p); i++ {
		c := p[i]
		switch {
		case c >= 'a' && c <= 'z', c >= 'A' && c <= 'Z', c >= '0' && c <= '9':
		case c == '/', c == '-', c == '.', c == '_', c == '~':
		default:
			return "/"
		}
	}
	return p
}

// bearerOK: the command line, with the master token. Browsers never hold it.
func (s *Server) bearerOK(r *http.Request) bool {
	h := r.Header.Get("Authorization")
	return strings.HasPrefix(h, "Bearer ") &&
		subtle.ConstantTimeCompare([]byte(strings.TrimPrefix(h, "Bearer ")), []byte(s.app.Token())) == 1
}

// authorized reports whether the request is signed in, and refreshes a browser's
// cookie when its session was extended.
func (s *Server) authorized(w http.ResponseWriter, r *http.Request) bool {
	if c, err := r.Cookie(s.cookieName()); err == nil {
		if ok, renewed := s.sess.check(c.Value); ok {
			if renewed {
				http.SetCookie(w, s.sessionCookie(c.Value))
			}
			return true
		}
	}
	return s.bearerOK(r)
}

func setSecurityHeaders(w http.ResponseWriter) {
	h := w.Header()
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("Referrer-Policy", "no-referrer")
	h.Set("X-Frame-Options", "SAMEORIGIN")
	h.Set("Content-Security-Policy",
		"default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; "+
			"script-src 'self'; connect-src 'self'; frame-src 'self' blob:; object-src 'self'; base-uri 'none'; form-action 'self'")
}

func (s *Server) serveHTTP(w http.ResponseWriter, r *http.Request) {
	setSecurityHeaders(w)
	if !s.hostAllowed(r) {
		http.Error(w, "unexpected Host header", http.StatusMisdirectedRequest)
		return
	}
	// Login: /?t=<one-time code> becomes a session cookie, and the code leaves the URL.
	if t := r.URL.Query().Get("t"); t != "" && !strings.HasPrefix(r.URL.Path, "/api/") {
		id, res := s.sess.redeemCode(t, func() (int, bool) { return s.callerUID(r) })
		if res == redeemWrongUser {
			http.Error(w, "This sign-in link was made for the user who started themesh on this computer, and you are another user.\n"+
				"Ask that user to run `themesh url`: that prints a link you can open from any browser.", http.StatusForbidden)
			return
		}
		if id != "" {
			http.SetCookie(w, s.sessionCookie(id))
			q := r.URL.Query()
			q.Del("t")
			target := localPath(r.URL.Path)
			if enc := q.Encode(); enc != "" {
				target += "?" + enc
			}
			http.Redirect(w, r, target, http.StatusFound)
			return
		}
	}
	if r.URL.Path == "/api/handshake" && (r.Method == http.MethodGet || r.Method == http.MethodHead) {
		s.handleHandshake(w, r)
		return
	}
	if strings.HasPrefix(r.URL.Path, "/api/") {
		if !s.authorized(w, r) {
			writeError(w, errCode("unauthorized", "open the link printed by `themesh up` (or run `themesh open`) to sign in"))
			return
		}
		if r.Method != http.MethodGet && r.Method != http.MethodHead && r.Method != http.MethodOptions {
			if !s.bearerOK(r) { // only a valid master token marks a client that is not a browser
				if r.Header.Get("X-Themesh") != "1" {
					writeError(w, errCode("denied", "missing X-Themesh header"))
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

// uiVersion identifies the embedded interface: a hash over its files. The
// service worker's cache is named after it, so a new binary that brings a new
// interface replaces what the browser kept instead of being shown yesterday's
// files first.
func (s *Server) uiVersion() string {
	s.uiOnce.Do(func() {
		h := sha256.New()
		_ = fs.WalkDir(s.ui, ".", func(p string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() || p == "sw.js" {
				return nil
			}
			b, _ := fs.ReadFile(s.ui, p)
			h.Write([]byte(p))
			h.Write([]byte{0})
			h.Write(b)
			h.Write([]byte{0})
			return nil
		})
		s.uiVer = hex.EncodeToString(h.Sum(nil))[:12]
	})
	return s.uiVer
}

var swVersionRe = regexp.MustCompile(`const VERSION = "[^"\n]*";`)

// serveServiceWorker serves sw.js with its cache name tied to the interface
// version (see uiVersion).
func (s *Server) serveServiceWorker(w http.ResponseWriter, r *http.Request) {
	data, err := fs.ReadFile(s.ui, "sw.js")
	if err != nil {
		http.NotFound(w, r)
		return
	}
	data = swVersionRe.ReplaceAll(data, []byte(`const VERSION = "themesh-ui-`+s.uiVersion()+`";`))
	w.Header().Set("Content-Type", "text/javascript; charset=utf-8")
	http.ServeContent(w, r, "sw.js", time.Time{}, bytes.NewReader(data))
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
	w.Header().Set("Cache-Control", "no-cache")
	if p == "sw.js" {
		s.serveServiceWorker(w, r)
		return
	}
	if p == "" || p == "index.html" {
		// http.FileServer redirects every request for index.html to "./", which
		// would loop for "/", so the entry page is served directly.
		data, err := fs.ReadFile(s.ui, "index.html")
		if err != nil {
			http.NotFound(w, r)
			return
		}
		http.ServeContent(w, r, "index.html", time.Time{}, bytes.NewReader(data))
		return
	}
	if fi, err := fs.Stat(s.ui, p); err != nil || fi.IsDir() {
		http.NotFound(w, r)
		return
	}
	r2 := new(http.Request)
	*r2 = *r
	r2.URL = new(url.URL)
	*r2.URL = *r.URL
	r2.URL.Path = "/" + p
	http.FileServerFS(s.ui).ServeHTTP(w, r2)
}
