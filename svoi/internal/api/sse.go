package api

import (
	"fmt"
	"net/http"
	"time"
)

// handleEvents streams server-sent events: everything the UI shows live.
func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		writeError(w, errCode("internal", "streaming is not supported"))
		return
	}
	h := w.Header()
	h.Set("Content-Type", "text/event-stream")
	h.Set("Cache-Control", "no-cache, no-transform")
	h.Set("Connection", "keep-alive")
	h.Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)

	// A stream is authorised when it is opened, but a sign-out (or the end of the
	// session) must end it too: a browser's stream is checked against its session
	// before every event and at every keepalive. The command line has no session.
	session := ""
	if c, err := r.Cookie(s.cookieName()); err == nil && !s.bearerOK(r) {
		session = c.Value
	}
	signedOut := func() bool { return session != "" && !s.sess.alive(session) }

	ch, cancel := s.app.Hub().Subscribe()
	defer cancel()
	fmt.Fprintf(w, "retry: 2000\n\n")
	fmt.Fprintf(w, "event: hello\ndata: {\"serverTime\":%d}\n\n", time.Now().Unix())
	fl.Flush()

	keepalive := time.NewTicker(15 * time.Second)
	defer keepalive.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case msg, open := <-ch:
			if !open || signedOut() {
				return // too slow (the browser reconnects and re-fetches the state), or signed out
			}
			fmt.Fprintf(w, "event: %s\ndata: %s\n\n", msg.Event, msg.Data)
			fl.Flush()
		case <-keepalive.C:
			if signedOut() {
				return
			}
			fmt.Fprint(w, ": keepalive\n\n")
			fl.Flush()
		}
	}
}
