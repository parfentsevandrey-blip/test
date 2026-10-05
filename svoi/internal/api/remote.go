package api

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/services"
)

func servicesFrom(name, addr, desc string, allow []string) services.Service {
	return services.Service{Name: name, Addr: addr, Description: desc, Allow: allow}
}

// Remote administration: an admin device can configure another device through
// its own UI. The request is carried over the encrypted mesh link as an
// "api.call" RPC and executed on the target against the same handlers that
// serve its local UI - but only for configuration endpoints, and only if the
// caller holds the mesh authority.

var remoteAllowed = []string{
	"/api/shares", "/api/services", "/api/settings", "/api/local/fs", "/api/diag/logs", "/api/state", "/api/mailgw",
}

func remotePathAllowed(p string) bool {
	for _, a := range remoteAllowed {
		if p == a || strings.HasPrefix(p, a+"/") {
			return true
		}
	}
	return false
}

// remoteCtxKey marks a request that arrived over the mesh (api.call) rather than
// from the local browser. Unlike a header, a context value cannot be forged by a client.
type remoteCtxKey struct{}

type remoteCall struct {
	Method string `json:"method"`
	Path   string `json:"path"` // path + query
	Body   []byte `json:"body,omitempty"`
}

type remoteResult struct {
	Status int    `json:"status"`
	Type   string `json:"type"`
	Body   []byte `json:"body"`
}

// registerRemoteHandler installs the RPC that executes forwarded requests.
func (s *Server) registerRemoteHandler() {
	s.app.Node().Handle("api.call", func(ctx context.Context, c *mesh.Call) (any, error) {
		if !c.Peer.Member().Admin {
			return nil, mesh.Errf(mesh.CodeDenied, "only an admin device may manage this device")
		}
		var rc remoteCall
		if err := c.Decode(&rc); err != nil {
			return nil, err
		}
		pathOnly := rc.Path
		if i := strings.IndexByte(pathOnly, '?'); i >= 0 {
			pathOnly = pathOnly[:i]
		}
		if !strings.HasPrefix(pathOnly, "/api/") || strings.Contains(pathOnly, "..") || !remotePathAllowed(pathOnly) {
			return nil, mesh.Errf(mesh.CodeDenied, "this endpoint cannot be used remotely")
		}
		switch rc.Method {
		case http.MethodGet, http.MethodPost, http.MethodPut, http.MethodDelete:
		default:
			return nil, mesh.Errf(mesh.CodeInvalid, "unsupported method")
		}
		req, err := http.NewRequestWithContext(context.WithValue(ctx, remoteCtxKey{}, true), rc.Method, rc.Path, bytes.NewReader(rc.Body))
		if err != nil {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad path")
		}
		req.Header.Set("Content-Type", "application/json")
		rec := &recorder{header: http.Header{}, status: 200}
		// Straight into the mux: authentication happened at the mesh layer.
		s.mux.ServeHTTP(rec, req)
		return remoteResult{Status: rec.status, Type: rec.header.Get("Content-Type"), Body: rec.body.Bytes()}, nil
	})
}

// recorder is a minimal http.ResponseWriter capturing a response.
type recorder struct {
	header http.Header
	status int
	body   bytes.Buffer
}

func (r *recorder) Header() http.Header         { return r.header }
func (r *recorder) WriteHeader(status int)      { r.status = status }
func (r *recorder) Write(b []byte) (int, error) { return r.body.Write(b) }

// handleRemote forwards /api/d/{peer}/{rest...} to another device.
func (s *Server) handleRemote(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "peer")
	if err != nil {
		writeError(w, err)
		return
	}
	rest := "/api/" + r.PathValue("rest")
	if r.URL.RawQuery != "" {
		rest += "?" + r.URL.RawQuery
	}
	pathOnly := strings.SplitN(rest, "?", 2)[0]
	if strings.Contains(pathOnly, "..") || !remotePathAllowed(pathOnly) {
		writeError(w, errCode("denied", "this endpoint cannot be used remotely"))
		return
	}
	if id == s.app.Node().ID() {
		// "Manage this device" through the same URL scheme: run locally.
		r2 := r.Clone(r.Context())
		r2.URL.Path = pathOnly
		s.mux.ServeHTTP(w, r2)
		return
	}
	if !s.app.Node().IsAdmin() {
		writeError(w, errCode("denied", "only an admin device can manage other devices"))
		return
	}
	p := s.app.Node().Peer(id)
	if p == nil {
		writeError(w, errCode("notfound", "unknown device"))
		return
	}
	if !p.Online() {
		writeError(w, errCode("offline", p.Name()+" is not online"))
		return
	}
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 1<<20))
	if err != nil {
		writeError(w, errCode("toolarge", "request body too large"))
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()
	var res remoteResult
	if err := p.Call(ctx, "api.call", remoteCall{Method: r.Method, Path: rest, Body: body}, &res); err != nil {
		writeError(w, err)
		return
	}
	if res.Type == "" {
		res.Type = "application/json; charset=utf-8"
	}
	w.Header().Set("Content-Type", res.Type)
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(res.Status)
	_, _ = w.Write(res.Body)
}

var _ = json.Marshal
