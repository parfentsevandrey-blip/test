// Package blob is a content-addressed file store (SHA-256) used for mail and
// chat attachments. Because blobs are addressed by their hash, a device can
// safely fetch one from any peer: the bytes are verified before they are kept.
package blob

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sync"

	"github.com/parfentsevandrey-blip/test/svoi/internal/diskfree"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

var shaRe = regexp.MustCompile(`^[0-9a-f]{64}$`)

// ErrNoSpace is returned when the volume cannot hold a blob that is being fetched.
var ErrNoSpace = errors.New("blob: not enough free disk space")

const freeReserve = 64 << 20

// ValidSHA reports whether s looks like a hex SHA-256.
func ValidSHA(s string) bool { return shaRe.MatchString(s) }

// Store keeps blobs under dir/ab/abcdef….
type Store struct {
	dir string
	mu  sync.Mutex
	// inflight prevents two goroutines fetching the same blob at once.
	inflight map[string]chan struct{}
	// mayServe, when set, decides whether a member may be sent a blob. Without it
	// the hash alone would be the key to a file, and asking for one would reveal
	// whether this device holds it.
	mayServe func(peer identity.ID, sha string) bool
}

// SetAuthorizer limits who blob.get serves to whoever fn approves.
func (s *Store) SetAuthorizer(fn func(peer identity.ID, sha string) bool) {
	s.mu.Lock()
	s.mayServe = fn
	s.mu.Unlock()
}

// Open creates the store directory if needed.
func Open(dir string) (*Store, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	return &Store{dir: dir, inflight: map[string]chan struct{}{}}, nil
}

// Path returns where the blob with this hash lives (it may not exist).
func (s *Store) Path(sha string) string {
	if !ValidSHA(sha) {
		return ""
	}
	return filepath.Join(s.dir, sha[:2], sha)
}

// Has reports whether the blob exists and its size.
func (s *Store) Has(sha string) (int64, bool) {
	p := s.Path(sha)
	if p == "" {
		return 0, false
	}
	st, err := os.Stat(p)
	if err != nil || !st.Mode().IsRegular() {
		return 0, false
	}
	return st.Size(), true
}

// Put stores everything r yields and returns its hash and size.
func (s *Store) Put(r io.Reader) (sha string, size int64, err error) {
	tmp, err := os.CreateTemp(s.dir, ".put-*")
	if err != nil {
		return "", 0, err
	}
	name := tmp.Name()
	defer os.Remove(name)
	h := sha256.New()
	size, err = io.Copy(io.MultiWriter(tmp, h), r)
	if cerr := tmp.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		return "", 0, err
	}
	sha = hex.EncodeToString(h.Sum(nil))
	return sha, size, s.install(name, sha)
}

func (s *Store) install(tmp, sha string) error {
	dst := s.Path(sha)
	if err := os.MkdirAll(filepath.Dir(dst), 0o700); err != nil {
		return err
	}
	if _, err := os.Stat(dst); err == nil {
		return nil // already present
	}
	return os.Rename(tmp, dst)
}

// Open opens a stored blob.
func (s *Store) Open(sha string) (*os.File, error) {
	p := s.Path(sha)
	if p == "" {
		return nil, os.ErrNotExist
	}
	return os.Open(p)
}

// Remove deletes a blob.
func (s *Store) Remove(sha string) {
	if p := s.Path(sha); p != "" {
		os.Remove(p)
	}
}

// RegisterRPC serves blobs to other members.
func (s *Store) RegisterRPC(n *mesh.Node) {
	n.HandleStream("blob.get", func(ctx context.Context, c *mesh.Call, st *mesh.ServerStream) error {
		var a struct {
			SHA    string
			Offset int64
		}
		if err := c.Decode(&a); err != nil {
			return err
		}
		s.mu.Lock()
		allowed := s.mayServe
		s.mu.Unlock()
		// The answer is the same for "not yours" and "not here": no existence oracle.
		if allowed != nil && !allowed(c.Peer.ID, a.SHA) {
			return mesh.Errf(mesh.CodeNotFound, "no such blob")
		}
		f, err := s.Open(a.SHA)
		if err != nil {
			return mesh.Errf(mesh.CodeNotFound, "no such blob")
		}
		defer f.Close()
		info, err := f.Stat()
		if err != nil {
			return err
		}
		if a.Offset < 0 || a.Offset > info.Size() {
			return mesh.Errf(mesh.CodeInvalid, "offset out of range")
		}
		if _, err := f.Seek(a.Offset, io.SeekStart); err != nil {
			return err
		}
		if err := st.Reply(map[string]int64{"size": info.Size()}); err != nil {
			return err
		}
		_, err = io.Copy(st, f)
		return err
	})
}

// Fetch downloads a blob from a peer, verifying its hash. progress (optional)
// receives the number of bytes received so far.
func (s *Store) Fetch(ctx context.Context, p *mesh.Peer, sha string, wantSize int64, progress func(int64)) error {
	if !ValidSHA(sha) {
		return errors.New("blob: invalid hash")
	}
	if _, ok := s.Has(sha); ok {
		return nil
	}
	// De-duplicate concurrent fetches of the same blob.
	s.mu.Lock()
	if ch, ok := s.inflight[sha]; ok {
		s.mu.Unlock()
		select {
		case <-ch:
		case <-ctx.Done():
			return ctx.Err()
		}
		if _, ok := s.Has(sha); ok {
			return nil
		}
		return errors.New("blob: concurrent fetch failed")
	}
	ch := make(chan struct{})
	s.inflight[sha] = ch
	s.mu.Unlock()
	defer func() {
		s.mu.Lock()
		delete(s.inflight, sha)
		s.mu.Unlock()
		close(ch)
	}()

	cs, err := p.OpenStream(ctx, "blob.get", map[string]any{"sha": sha})
	if err != nil {
		return err
	}
	stop := context.AfterFunc(ctx, cs.Cancel)
	defer stop()
	defer cs.Close()
	if err := cs.CloseWrite(); err != nil {
		return err
	}
	var meta struct{ Size int64 }
	if err := cs.ReadResponse(&meta); err != nil {
		return err
	}
	// The size comes from a signed message; what the peer streams must match it
	// exactly (negative: not known in advance), or it could make us store
	// whatever it likes before the hash check fails.
	if wantSize >= 0 && meta.Size != wantSize {
		return fmt.Errorf("blob: size mismatch (%d, expected %d)", meta.Size, wantSize)
	}
	if meta.Size < 0 {
		return errors.New("blob: bad size")
	}
	if free, ok := diskfree.Free(s.dir); ok && free < uint64(meta.Size)+freeReserve {
		return ErrNoSpace
	}
	tmp, err := os.CreateTemp(s.dir, ".fetch-*")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	h := sha256.New()
	var got int64
	buf := make([]byte, 128<<10)
	for {
		n, rerr := cs.Read(buf)
		if n > 0 {
			if _, werr := tmp.Write(buf[:n]); werr != nil {
				tmp.Close()
				return werr
			}
			h.Write(buf[:n])
			got += int64(n)
			if got > meta.Size {
				tmp.Close()
				return errors.New("blob: the peer sent more than it announced")
			}
			if progress != nil {
				progress(got)
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			tmp.Close()
			return rerr
		}
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if got != meta.Size {
		return io.ErrUnexpectedEOF
	}
	if hex.EncodeToString(h.Sum(nil)) != sha {
		return errors.New("blob: content does not match its hash")
	}
	return s.install(name, sha)
}
