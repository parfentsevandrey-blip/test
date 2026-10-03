package api

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

func (s *Server) routes() {
	m := s.mux
	// state & lifecycle
	m.HandleFunc("GET /api/state", s.handleState)
	m.HandleFunc("GET /api/events", s.handleEvents)
	m.HandleFunc("POST /api/mesh/create", s.handleMeshCreate)
	m.HandleFunc("POST /api/mesh/join", s.handleMeshJoin)
	m.HandleFunc("POST /api/mesh/leave", s.handleMeshLeave)
	m.HandleFunc("POST /api/netcheck", s.handleNetcheck)
	m.HandleFunc("GET /api/diag/logs", s.handleLogs)
	m.HandleFunc("GET /api/diag/ping", s.handlePing)
	// devices & invites
	m.HandleFunc("GET /api/invites", s.handleInvites)
	m.HandleFunc("POST /api/invites", s.handleInviteCreate)
	m.HandleFunc("DELETE /api/invites/{id}", s.handleInviteDelete)
	m.HandleFunc("POST /api/peers/{id}/alias", s.handlePeerAlias)
	m.HandleFunc("POST /api/peers/{id}/revoke", s.handlePeerRevoke)
	m.HandleFunc("POST /api/peers/{id}/rename", s.handlePeerRename)
	m.HandleFunc("POST /api/peers/{id}/admin", s.handlePeerAdmin)
	// files
	m.HandleFunc("GET /api/peers/{id}/shares", s.handleRemoteShares)
	m.HandleFunc("GET /api/peers/{id}/fs", s.handleFS)
	m.HandleFunc("POST /api/peers/{id}/fs", s.handleFSOp)
	m.HandleFunc("GET /api/peers/{id}/file", s.handleFileGet)
	m.HandleFunc("PUT /api/peers/{id}/file", s.handleFilePut)
	m.HandleFunc("GET /api/peers/{id}/thumb", s.handleThumb)
	m.HandleFunc("GET /api/peers/{id}/services", s.handleRemoteServices)
	// transfers
	m.HandleFunc("GET /api/transfers", s.handleTransfers)
	m.HandleFunc("POST /api/transfers", s.handleTransferSend)
	m.HandleFunc("POST /api/transfers/{id}/accept", s.handleTransferAction("accept"))
	m.HandleFunc("POST /api/transfers/{id}/decline", s.handleTransferAction("decline"))
	m.HandleFunc("POST /api/transfers/{id}/cancel", s.handleTransferAction("cancel"))
	m.HandleFunc("POST /api/transfers/{id}/retry", s.handleTransferAction("retry"))
	m.HandleFunc("DELETE /api/transfers/{id}", s.handleTransferRemove)
	m.HandleFunc("GET /api/transfers/{id}/file", s.handleTransferFile)
	// own shares
	m.HandleFunc("GET /api/shares", s.handleShares)
	m.HandleFunc("POST /api/shares", s.handleShareSave)
	m.HandleFunc("PUT /api/shares/{id}", s.handleShareSave)
	m.HandleFunc("DELETE /api/shares/{id}", s.handleShareDelete)
	m.HandleFunc("GET /api/local/fs", s.handleLocalFS)
	// mail
	m.HandleFunc("GET /api/mail", s.handleMailList)
	m.HandleFunc("POST /api/mail", s.handleMailSend)
	m.HandleFunc("GET /api/mail/{id}", s.handleMailGet)
	m.HandleFunc("POST /api/mail/{id}/flags", s.handleMailFlags)
	m.HandleFunc("DELETE /api/mail/{id}", s.handleMailDelete)
	m.HandleFunc("GET /api/mail/{id}/attachments/{index}", s.handleMailAttachment)
	m.HandleFunc("POST /api/blobs", s.handleBlobUpload)
	// chat
	m.HandleFunc("GET /api/chat/threads", s.handleChatThreads)
	m.HandleFunc("GET /api/chat/messages/{id}/attachments/{index}", s.handleChatAttachment)
	m.HandleFunc("GET /api/chat/{peer}", s.handleChatMessages)
	m.HandleFunc("POST /api/chat/{peer}", s.handleChatSend)
	m.HandleFunc("POST /api/chat/{peer}/read", s.handleChatRead)
	// services
	m.HandleFunc("GET /api/services", s.handleServices)
	m.HandleFunc("POST /api/services", s.handleServiceSave)
	m.HandleFunc("PUT /api/services/{id}", s.handleServiceSave)
	m.HandleFunc("DELETE /api/services/{id}", s.handleServiceDelete)
	m.HandleFunc("GET /api/forwards", s.handleForwards)
	m.HandleFunc("POST /api/forwards", s.handleForwardAdd)
	m.HandleFunc("DELETE /api/forwards/{id}", s.handleForwardDelete)
	// settings
	m.HandleFunc("GET /api/settings", s.handleSettingsGet)
	m.HandleFunc("PUT /api/settings", s.handleSettingsPut)
	// remote administration of another device
	m.HandleFunc("/api/d/{peer}/{rest...}", s.handleRemote)
}

func (s *Server) peerID(r *http.Request, name string) (identity.ID, error) {
	return s.app.PeerID(r.PathValue(name))
}

// ---- state & lifecycle ----

func (s *Server) handleState(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.app.State())
}

func (s *Server) handleMeshCreate(w http.ResponseWriter, r *http.Request) {
	var in struct{ MeshName, DeviceName, Owner string }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	if err := s.app.CreateMesh(in.MeshName, in.DeviceName, in.Owner); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handleMeshJoin(w http.ResponseWriter, r *http.Request) {
	var in struct{ Invite, DeviceName, Owner string }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 40*time.Second)
	defer cancel()
	if err := s.app.JoinMesh(ctx, in.Invite, in.DeviceName, in.Owner); err != nil {
		writeError(w, errCode("invalid", err.Error()))
		return
	}
	ok(w)
}

func (s *Server) handleMeshLeave(w http.ResponseWriter, r *http.Request) {
	if err := s.app.LeaveMesh(); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handleNetcheck(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	writeJSON(w, http.StatusOK, map[string]any{"self": s.app.NetCheck(ctx)})
}

func (s *Server) handleLogs(w http.ResponseWriter, r *http.Request) {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 || limit > 1000 {
		limit = 200
	}
	writeJSON(w, http.StatusOK, map[string]any{"lines": s.app.Logs(limit)})
}

func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	id, err := s.app.PeerID(r.URL.Query().Get("peer"))
	if err != nil {
		writeError(w, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	d, err := s.app.Ping(ctx, id)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]float64{"ms": float64(d.Microseconds()) / 1000})
}

// ---- devices & invites ----

func (s *Server) handleInvites(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.app.Invites())
}

func (s *Server) handleInviteCreate(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Admin      bool `json:"admin"`
		TTLMinutes int  `json:"ttlMinutes"`
	}
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	ttl := time.Duration(in.TTLMinutes) * time.Minute
	v, err := s.app.NewInvite(in.Admin, ttl)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, v)
}

func (s *Server) handleInviteDelete(w http.ResponseWriter, r *http.Request) {
	if !s.app.CancelInvite(r.PathValue("id")) {
		writeError(w, errCode("notfound", "no such invitation"))
		return
	}
	ok(w)
}

func (s *Server) handlePeerAlias(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	var in struct{ Alias string }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	if err := s.app.SetAlias(id, in.Alias); err != nil {
		writeError(w, errCode("notfound", err.Error()))
		return
	}
	ok(w)
}

func (s *Server) handlePeerRevoke(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	if err := s.app.Revoke(id); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handlePeerRename(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	var in struct{ Name string }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	if err := s.app.Rename(id, in.Name); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

func (s *Server) handlePeerAdmin(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	var in struct{ Admin bool }
	if err := decode(r, &in); err != nil {
		writeError(w, err)
		return
	}
	if !in.Admin {
		writeError(w, errCode("unsupported", "administrator rights cannot be taken back once granted (the device already holds the mesh key); remove the device and add it again as a regular one"))
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	if err := s.app.GrantAdmin(ctx, id); err != nil {
		writeError(w, err)
		return
	}
	ok(w)
}

// ---- settings ----

func (s *Server) handleSettingsGet(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.app.Settings())
}

func (s *Server) handleSettingsPut(w http.ResponseWriter, r *http.Request) {
	var patch app.SettingsPatch
	if err := decode(r, &patch); err != nil {
		writeError(w, err)
		return
	}
	var opts []app.SettingsOption
	if r.Context().Value(remoteCtxKey{}) != nil {
		// Over the mesh: a network restart must not cut the link the answer travels on.
		opts = append(opts, app.DeferNetworkRestart())
	}
	res, err := s.app.UpdateSettings(patch, opts...)
	if err != nil {
		writeError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}
