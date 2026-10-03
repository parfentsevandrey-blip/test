package mesh

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"time"
)

// A joiner puts a token in the server name of its TLS ClientHello:
//
//	<nonce 16 bytes hex>.<mac 16 bytes hex>.join.mesh
//
// where mac = HMAC-SHA256(invitation secret, label || nonce). The inviter checks it
// against its pending invitations *before* it spends anything on the handshake, so
// a stranger who does not know a secret gets no certificate, no state and no share
// of the budget the person holding the invitation needs. Each token is good once
// (a copied one cannot be replayed); the join request itself still proves the
// secret again, bound to the TLS session.
const (
	sniLabel         = "themesh-join-sni/v1\x00"
	sniSuffix        = "join.mesh"
	sniKeepFor       = 10 * time.Minute
	sniMaxRemembered = 4096
)

func joinSNIMAC(secret, nonce []byte) []byte {
	m := hmac.New(sha256.New, secret)
	m.Write([]byte(sniLabel))
	m.Write(nonce)
	return m.Sum(nil)[:16]
}

// joinSNI makes a fresh token for the invitation with this secret.
func joinSNI(secret []byte) string {
	var nonce [16]byte
	_, _ = rand.Read(nonce[:])
	return hex.EncodeToString(nonce[:]) + "." + hex.EncodeToString(joinSNIMAC(secret, nonce[:])) + "." + sniSuffix
}

// checkJoinSNI reports whether sni carries an unused token made with the secret
// of one of our pending invitations, and uses it up.
func (n *Node) checkJoinSNI(sni string) bool {
	parts := strings.Split(strings.TrimSuffix(sni, "."), ".")
	if len(parts) != 4 || parts[2]+"."+parts[3] != sniSuffix {
		return false
	}
	nonce, err1 := hex.DecodeString(parts[0])
	mac, err2 := hex.DecodeString(parts[1])
	if err1 != nil || err2 != nil || len(nonce) != 16 || len(mac) != 16 {
		return false
	}
	now := time.Now()
	ok := false
	n.mu.RLock()
	for _, inv := range n.invites {
		if now.Before(inv.expires) && inv.fails < maxJoinFails && hmac.Equal(mac, joinSNIMAC(inv.secret, nonce)) {
			ok = true
			break
		}
	}
	n.mu.RUnlock()
	if !ok {
		return false
	}
	var key [16]byte
	copy(key[:], nonce)
	n.sniMu.Lock()
	defer n.sniMu.Unlock()
	if n.sniSeen == nil {
		n.sniSeen = map[[16]byte]time.Time{}
	}
	if _, used := n.sniSeen[key]; used {
		return false
	}
	if len(n.sniSeen) >= sniMaxRemembered {
		for k, t := range n.sniSeen {
			if now.Sub(t) > sniKeepFor {
				delete(n.sniSeen, k)
			}
		}
		if len(n.sniSeen) >= sniMaxRemembered { // a flood of valid tokens is not a thing: refuse rather than grow
			return false
		}
	}
	n.sniSeen[key] = now
	return true
}
