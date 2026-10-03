package api

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// How the browser signs in. The master token (ui.token, 0600) is for the command
// line only: it is never printed, never put in a URL or a cookie. A person gets a
// short-lived, single-use *login code* in the link `svoi up` prints (or `svoi url`
// mints); opening it exchanges the code for a random *session*, kept by the
// server (as a hash, so the file on disk cannot be replayed) and by the browser
// as an HttpOnly cookie. Whoever sees the link in a log or the process list
// afterwards finds a code that has been used up or has expired.
const (
	loginCodeTTL = 10 * time.Minute
	sessionTTL   = 14 * 24 * time.Hour // sliding: renewed while the browser keeps being used
	maxSessions  = 50
	maxCodes     = 20 // unused links at most
	codeLen      = 48 // hex characters (192 bits)
	sessionIDLen = 64 // hex characters (256 bits)
)

type sessions struct {
	mu    sync.Mutex
	now   func() time.Time
	path  string
	codes map[string]time.Time // sha256(code) -> expiry
	sess  map[string]int64     // sha256(session id) -> expiry (unix seconds)
}

func newSessions(dir string) *sessions {
	s := &sessions{now: time.Now, codes: map[string]time.Time{}, sess: map[string]int64{}}
	if dir != "" {
		s.path = filepath.Join(dir, "ui.sessions")
		if raw, err := os.ReadFile(s.path); err == nil {
			_ = json.Unmarshal(raw, &s.sess)
		}
	}
	s.pruneLocked(s.now())
	return s
}

func hashOf(v string) string {
	h := sha256.Sum256([]byte(v))
	return hex.EncodeToString(h[:])
}

func randomHex(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic("api: no randomness: " + err.Error())
	}
	return hex.EncodeToString(b)
}

func (s *sessions) pruneLocked(now time.Time) {
	for k, exp := range s.codes {
		if !now.Before(exp) {
			delete(s.codes, k)
		}
	}
	for k, exp := range s.sess {
		if now.Unix() >= exp {
			delete(s.sess, k)
		}
	}
}

func (s *sessions) saveLocked() {
	if s.path == "" {
		return
	}
	if raw, err := json.Marshal(s.sess); err == nil {
		_ = identity.WriteFileAtomic(s.path, raw, 0o600)
	}
}

// newCode returns a fresh single-use login code.
func (s *sessions) newCode() string {
	code := randomHex(codeLen / 2)
	now := s.now()
	s.mu.Lock()
	s.pruneLocked(now)
	for len(s.codes) >= maxCodes { // forget the one closest to expiry
		var oldK string
		var oldE time.Time
		for k, e := range s.codes {
			if oldK == "" || e.Before(oldE) {
				oldK, oldE = k, e
			}
		}
		delete(s.codes, oldK)
	}
	s.codes[hashOf(code)] = now.Add(loginCodeTTL)
	s.mu.Unlock()
	return code
}

// redeem exchanges a login code for a new session id ("" if the code is unknown,
// used or expired). A code works exactly once.
func (s *sessions) redeem(code string) string {
	if len(code) != codeLen {
		return ""
	}
	key := hashOf(code)
	now := s.now()
	s.mu.Lock()
	defer s.mu.Unlock()
	exp, ok := s.codes[key]
	delete(s.codes, key)
	if !ok || !now.Before(exp) {
		return ""
	}
	s.pruneLocked(now)
	for len(s.sess) >= maxSessions { // drop the one closest to expiry
		var oldK string
		var oldE int64
		for k, e := range s.sess {
			if oldK == "" || e < oldE {
				oldK, oldE = k, e
			}
		}
		delete(s.sess, oldK)
	}
	id := randomHex(sessionIDLen / 2)
	s.sess[hashOf(id)] = now.Add(sessionTTL).Unix()
	s.saveLocked()
	return id
}

// check reports whether id is a live session. renewed is true when the session
// was extended, in which case the caller refreshes the browser's cookie too.
func (s *sessions) check(id string) (ok, renewed bool) {
	if len(id) != sessionIDLen {
		return false, false
	}
	key := hashOf(id)
	now := s.now()
	s.mu.Lock()
	defer s.mu.Unlock()
	exp, found := s.sess[key]
	if !found {
		return false, false
	}
	if now.Unix() >= exp {
		delete(s.sess, key)
		s.saveLocked()
		return false, false
	}
	if time.Unix(exp, 0).Sub(now) < sessionTTL/2 {
		s.sess[key] = now.Add(sessionTTL).Unix()
		s.saveLocked()
		return true, true
	}
	return true, false
}

// alive reports whether id is a live session, without extending it.
func (s *sessions) alive(id string) bool {
	if len(id) != sessionIDLen {
		return false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	exp, found := s.sess[hashOf(id)]
	return found && s.now().Unix() < exp
}

// drop ends one session.
func (s *sessions) drop(id string) {
	s.mu.Lock()
	if _, ok := s.sess[hashOf(id)]; ok {
		delete(s.sess, hashOf(id))
		s.saveLocked()
	}
	s.mu.Unlock()
}

// dropAll signs every browser out and cancels unused login links.
func (s *sessions) dropAll() {
	s.mu.Lock()
	s.sess = map[string]int64{}
	s.codes = map[string]time.Time{}
	s.saveLocked()
	s.mu.Unlock()
}

func (s *Server) sessionCookie(id string) *http.Cookie {
	return &http.Cookie{
		Name: s.cookieName(), Value: id, Path: "/", HttpOnly: true,
		SameSite: http.SameSiteStrictMode, MaxAge: int(sessionTTL / time.Second),
	}
}

// HandshakeProof is what a node answers to a handshake nonce: an HMAC under the
// master token. The command line asks before it sends the token, so a stranger
// who grabbed the recorded port after the node died never gets to see it.
func HandshakeProof(token, nonce string) string {
	m := hmac.New(sha256.New, []byte(token))
	m.Write([]byte("svoi-handshake/v1\x00" + nonce))
	return hex.EncodeToString(m.Sum(nil))
}

// GET /api/handshake?n=<nonce> — the only /api endpoint that needs no sign-in.
func (s *Server) handleHandshake(w http.ResponseWriter, r *http.Request) {
	n := r.URL.Query().Get("n")
	if len(n) < 16 || len(n) > 128 {
		writeError(w, errCode("invalid", "n must be 16 to 128 characters"))
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"proof": HandshakeProof(s.app.Token(), n)})
}

// POST /api/login/code — mint a single-use link that signs a browser in. Only the
// command line (master token) may ask: a browser session cannot mint more of them.
func (s *Server) handleLoginCode(w http.ResponseWriter, r *http.Request) {
	if !s.bearerOK(r) {
		writeError(w, errCode("denied", "login links are issued to the command line only (svoi url / svoi open)"))
		return
	}
	code := s.sess.newCode()
	writeJSON(w, http.StatusOK, map[string]any{
		"code":       code,
		"url":        "http://" + r.Host + "/?t=" + code,
		"expiresIn":  int(loginCodeTTL / time.Second),
		"singleUse":  true,
		"sessionTtl": int(sessionTTL / time.Second),
	})
}

// POST /api/logout — end this browser's session. From the command line, ?all=1
// ends every session and cancels the unused links.
func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	if r.URL.Query().Get("all") == "1" {
		if !s.bearerOK(r) {
			writeError(w, errCode("denied", "signing out everywhere is for the command line only"))
			return
		}
		s.sess.dropAll()
	} else if c, err := r.Cookie(s.cookieName()); err == nil {
		s.sess.drop(c.Value)
	}
	http.SetCookie(w, &http.Cookie{Name: s.cookieName(), Value: "", Path: "/", HttpOnly: true, SameSite: http.SameSiteStrictMode, MaxAge: -1})
	ok(w)
}
