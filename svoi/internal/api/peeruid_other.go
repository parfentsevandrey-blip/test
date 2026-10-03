//go:build !linux

package api

import "net/http"

// peerUID: only Linux can say which user is on the other end of a loopback
// connection (from /proc/net/tcp). Elsewhere a sign-in link is not tied to a user.
func peerUID(*http.Request) (int, bool) { return 0, false }
