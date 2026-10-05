package files

import (
	"context"
	"errors"
	"io"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// RegisterRPC makes this device's shares available to other members.
func (m *Manager) RegisterRPC(n *mesh.Node) {
	n.Handle("files.shares", func(ctx context.Context, c *mesh.Call) (any, error) {
		return m.VisibleShares(c.Peer.ID), nil
	})
	n.Handle("files.list", func(ctx context.Context, c *mesh.Call) (any, error) {
		var a struct{ Share, Path string }
		if err := c.Decode(&a); err != nil {
			return nil, err
		}
		return m.List(c.Peer.ID, a.Share, a.Path)
	})
	n.HandleStream("files.get", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		var a struct {
			Share, Path    string
			Offset, Length int64
		}
		if err := c.Decode(&a); err != nil {
			return err
		}
		f, meta, err := m.OpenRead(c.Peer.ID, a.Share, a.Path)
		if err != nil {
			return err
		}
		defer f.Close()
		if a.Offset < 0 || a.Offset > meta.Size {
			return mesh.Errf(mesh.CodeInvalid, "offset out of range")
		}
		if a.Offset > 0 {
			if _, err := f.Seek(a.Offset, io.SeekStart); err != nil {
				return err
			}
		}
		n := meta.Size - a.Offset
		if a.Length > 0 && a.Length < n {
			n = a.Length
		}
		if err := s.Reply(meta); err != nil {
			return err
		}
		_, err = io.CopyN(s, f, n)
		return err
	})
	n.HandleStream("files.put", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		var a struct {
			Share, Path string
			Size        int64
			Overwrite   bool
		}
		if err := c.Decode(&a); err != nil {
			return err
		}
		pf, err := m.Create(c.Peer.ID, a.Share, a.Path, a.Overwrite)
		if err != nil {
			return err
		}
		got, err := copyN(pf, s, a.Size)
		if err != nil {
			pf.Abort()
			return err
		}
		if err := pf.Commit(); err != nil {
			return err
		}
		return s.Reply(map[string]int64{"size": got})
	})
	n.Handle("files.op", func(ctx context.Context, c *mesh.Call) (any, error) {
		var a struct{ Share, Op, Path, To string }
		if err := c.Decode(&a); err != nil {
			return nil, err
		}
		if err := m.Op(c.Peer.ID, a.Share, a.Op, a.Path, a.To); err != nil {
			return nil, err
		}
		return map[string]bool{"ok": true}, nil
	})
}

// ---- client side: operate on a remote device's shares ----

func (m *Manager) peer(id Actor) (*mesh.Peer, error) {
	p := m.cfg.Node.Peer(id)
	if p == nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	if !p.Online() {
		return nil, mesh.Errf(mesh.CodeOffline, "%s is not online", p.Name())
	}
	return p, nil
}

// RemoteShares lists what a device shares with us.
func (m *Manager) RemoteShares(ctx context.Context, id identity.ID) ([]RemoteShare, error) {
	p, err := m.peer(id)
	if err != nil {
		return nil, err
	}
	var out []RemoteShare
	if err := p.Call(ctx, "files.shares", nil, &out); err != nil {
		return nil, err
	}
	return out, nil
}

// RemoteList lists a directory on a remote share.
func (m *Manager) RemoteList(ctx context.Context, id identity.ID, share, rel string) (*ListResult, error) {
	p, err := m.peer(id)
	if err != nil {
		return nil, err
	}
	var out ListResult
	if err := p.Call(ctx, "files.list", map[string]string{"share": share, "path": rel}, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

// RemoteReader streams (part of) a remote file. Close must be called.
//
// If the link it reads from is closed because another link to the same device took its place (it
// happens when two devices dial each other at the same moment, see mesh.IsReplaced), the reader asks
// for the rest of the file on the link that stays and goes on, so whoever reads does not see the break.
type RemoteReader struct {
	cs   *mesh.ClientStream
	Meta FileMeta

	// what is needed to ask for the rest on another link
	m       *Manager
	ctx     context.Context
	id      identity.ID
	share   string
	rel     string
	offset  int64 // where the stream that is being read begins in the file
	length  int64 // how much of the file it was asked for (<= 0: to the end)
	got     int64 // how much of it has been handed out
	resumes int   // how many times the reader went on another link
}

// maxResumes is how many times one reader goes on after its link was replaced: a link is replaced once
// when two devices meet, so more than a few means something else is wrong.
const maxResumes = 3

// replacementWait is how long a reader waits for the link that replaces its own to be installed.
const replacementWait = 3 * time.Second

// Read implements io.Reader.
func (r *RemoteReader) Read(p []byte) (int, error) {
	for {
		if r.length > 0 && r.got >= r.length {
			return 0, io.EOF // everything that was asked for has come, whatever the stream says now
		}
		n, err := r.cs.Read(p)
		r.got += int64(n)
		if err == nil || err == io.EOF || !mesh.IsReplaced(err) || r.resumes >= maxResumes {
			return n, err
		}
		if n > 0 {
			return n, nil // hand out what came; the next call goes on over the new link
		}
		if r.resume() != nil {
			return 0, err
		}
	}
}

// resume asks for the rest of the file on the link that replaced the one that broke.
func (r *RemoteReader) resume() error {
	r.resumes++
	offset := r.offset + r.got
	length := r.length
	if length > 0 {
		length -= r.got
	}
	cs, meta, err := r.m.openGet(r.ctx, r.id, r.share, r.rel, offset, length, true)
	if err != nil {
		return err
	}
	if meta.Size != r.Meta.Size || meta.MTime != r.Meta.MTime {
		cs.Cancel() // the file is not the one that was being read: its rest would be the rest of another file
		return errChanged
	}
	r.cs.Cancel()
	r.cs, r.offset, r.length, r.got = cs, offset, length, 0
	return nil
}

var errChanged = errors.New("files: the file changed while it was being read")

// Close aborts/ends the transfer.
func (r *RemoteReader) Close() error { return r.cs.Close() }

// RemoteOpen opens a remote file starting at offset (length <= 0: to the end).
func (m *Manager) RemoteOpen(ctx context.Context, id identity.ID, share, rel string, offset, length int64) (*RemoteReader, error) {
	cs, meta, err := m.openGet(ctx, id, share, rel, offset, length, false)
	if err != nil {
		return nil, err
	}
	return &RemoteReader{cs: cs, Meta: meta, m: m, ctx: ctx, id: id, share: share, rel: rel, offset: offset, length: length}, nil
}

// openGet sends the request for (part of) a file and reads the header of the answer. A link that is
// replaced before the answer comes is no reason to fail: the request goes again on the link that stays.
// afterReplace says that the caller's own link has just been replaced: the old one is gone and the new
// one may not be installed yet, for that moment the device looks offline, so the request waits a little.
func (m *Manager) openGet(ctx context.Context, id identity.ID, share, rel string, offset, length int64, afterReplace bool) (*mesh.ClientStream, FileMeta, error) {
	deadline := time.Now().Add(replacementWait)
	for replaced := 0; ; {
		cs, meta, err := m.openGetOnce(ctx, id, share, rel, offset, length)
		switch {
		case err == nil:
			return cs, meta, nil
		case mesh.IsReplaced(err) && replaced < maxResumes:
			replaced++
		case afterReplace && mesh.IsCode(err, mesh.CodeOffline) && time.Now().Before(deadline):
			select {
			case <-time.After(20 * time.Millisecond):
			case <-ctx.Done():
				return nil, FileMeta{}, err
			}
		default:
			return nil, FileMeta{}, err
		}
	}
}

func (m *Manager) openGetOnce(ctx context.Context, id identity.ID, share, rel string, offset, length int64) (*mesh.ClientStream, FileMeta, error) {
	p, err := m.peer(id)
	if err != nil {
		return nil, FileMeta{}, err
	}
	cs, err := p.OpenStream(ctx, "files.get", map[string]any{"share": share, "path": rel, "offset": offset, "length": length})
	if err != nil {
		return nil, FileMeta{}, err
	}
	var meta FileMeta
	if err := cs.CloseWrite(); err != nil {
		cs.Cancel()
		return nil, FileMeta{}, err
	}
	if err := cs.ReadResponse(&meta); err != nil {
		cs.Cancel()
		return nil, FileMeta{}, err
	}
	return cs, meta, nil
}

// RemotePut uploads size bytes from r into a remote read-write share.
//
// If the link of the upload is closed because another link to the same device took its place (it happens when two devices dial each
// other at the same moment, see mesh.IsReplaced), the upload goes again on the link that stays, from the start, when r can be rewound
// (a file, a buffer). A source that cannot be rewound (the body of an HTTP request) has been read in part and cannot be sent twice, so
// there the upload fails and says what happened to its link. The remote end writes under a temporary name and renames when all of the
// bytes are there, so a broken try leaves nothing behind. (If the break came after everything was written, only the answer was lost,
// and the second try without overwrite is told that the file exists: it is the same file, and nothing is lost.)
func (m *Manager) RemotePut(ctx context.Context, id identity.ID, share, rel string, r io.Reader, size int64, overwrite bool) (int64, error) {
	seeker, canRewind := r.(io.Seeker)
	var start int64
	if canRewind {
		var err error
		if start, err = seeker.Seek(0, io.SeekCurrent); err != nil {
			canRewind = false
		}
	}
	deadline := time.Now().Add(replacementWait)
	for replaced := 0; ; {
		n, err := m.remotePutOnce(ctx, id, share, rel, r, size, overwrite)
		switch {
		case err == nil:
			return n, nil
		case canRewind && mesh.IsReplaced(err) && replaced < maxResumes:
			replaced++
			if _, serr := seeker.Seek(start, io.SeekStart); serr != nil {
				return 0, err
			}
		case canRewind && replaced > 0 && mesh.IsCode(err, mesh.CodeOffline) && time.Now().Before(deadline):
			// the old link is gone and the one that replaces it is not installed yet: for that moment the device looks offline
			select {
			case <-time.After(20 * time.Millisecond):
			case <-ctx.Done():
				return 0, err
			}
		default:
			return 0, err
		}
	}
}

func (m *Manager) remotePutOnce(ctx context.Context, id identity.ID, share, rel string, r io.Reader, size int64, overwrite bool) (int64, error) {
	p, err := m.peer(id)
	if err != nil {
		return 0, err
	}
	cs, err := p.OpenStream(ctx, "files.put", map[string]any{"share": share, "path": rel, "size": size, "overwrite": overwrite})
	if err != nil {
		return 0, err
	}
	stop := context.AfterFunc(ctx, cs.Cancel)
	defer stop()
	if _, err := copyN(writerOnly{cs}, r, size); err != nil {
		cs.Cancel()
		return 0, err
	}
	if err := cs.CloseWrite(); err != nil {
		return 0, err
	}
	var res struct{ Size int64 }
	if err := cs.ReadResponse(&res); err != nil {
		cs.Cancel()
		return 0, err
	}
	cs.Close()
	return res.Size, nil
}

// writerOnly hides ReadFrom-style shortcuts so copyN uses plain writes.
type writerOnly struct{ w io.Writer }

func (w writerOnly) Write(p []byte) (int, error) { return w.w.Write(p) }

// RemoteOp performs mkdir/rename/delete on a remote share.
func (m *Manager) RemoteOp(ctx context.Context, id identity.ID, share, op, rel, to string) error {
	p, err := m.peer(id)
	if err != nil {
		return err
	}
	return p.Call(ctx, "files.op", map[string]string{"share": share, "op": op, "path": rel, "to": to}, nil)
}

// ---- Unified access: local or remote, chosen by device ID ----

// IsLocal reports whether id refers to this device (the zero ID or our own).
func (m *Manager) IsLocal(id identity.ID) bool { return id.IsZero() || id == m.cfg.Node.ID() }

// ListAny lists a directory on this or a remote device.
func (m *Manager) ListAny(ctx context.Context, id identity.ID, share, rel string) (*ListResult, error) {
	if m.IsLocal(id) {
		return m.List(Actor{}, share, rel)
	}
	return m.RemoteList(ctx, id, share, rel)
}

// SharesAny lists shares of this or a remote device.
func (m *Manager) SharesAny(ctx context.Context, id identity.ID) ([]RemoteShare, error) {
	if m.IsLocal(id) {
		return m.VisibleShares(Actor{}), nil
	}
	return m.RemoteShares(ctx, id)
}

// OpAny performs a modifying operation on this or a remote device.
func (m *Manager) OpAny(ctx context.Context, id identity.ID, share, op, rel, to string) error {
	if m.IsLocal(id) {
		return m.Op(Actor{}, share, op, rel, to)
	}
	return m.RemoteOp(ctx, id, share, op, rel, to)
}

// ReaderAt provides random access to a local or remote file for HTTP Range
// serving. It reads lazily with one stream per read burst.
type ReaderAt struct {
	m     *Manager
	ctx   context.Context
	id    identity.ID
	share string
	path  string
	Meta  FileMeta
	local bool
}

// OpenAny returns a seekable view of a file on this or a remote device.
func (m *Manager) OpenAny(ctx context.Context, id identity.ID, share, rel string) (*ReaderAt, error) {
	r := &ReaderAt{m: m, ctx: ctx, id: id, share: share, path: rel}
	if m.IsLocal(id) {
		f, meta, err := m.OpenRead(Actor{}, share, rel)
		if err != nil {
			return nil, err
		}
		f.Close()
		r.local, r.Meta = true, meta
		return r, nil
	}
	rr, err := m.RemoteOpen(ctx, id, share, rel, 0, 1)
	if err != nil {
		return nil, err
	}
	r.Meta = rr.Meta
	rr.Close()
	return r, nil
}

// Section returns a reader for bytes [off, off+n).
func (r *ReaderAt) Section(off, n int64) (io.ReadCloser, error) {
	if r.local {
		f, _, err := r.m.OpenRead(Actor{}, r.share, r.path)
		if err != nil {
			return nil, err
		}
		if _, err := f.Seek(off, io.SeekStart); err != nil {
			f.Close()
			return nil, err
		}
		return &limitedFile{Reader: io.LimitReader(f, n), c: f}, nil
	}
	rr, err := r.m.RemoteOpen(r.ctx, r.id, r.share, r.path, off, n)
	if err != nil {
		return nil, err
	}
	return &limitedRemote{Reader: io.LimitReader(rr, n), rr: rr}, nil
}

type limitedFile struct {
	io.Reader
	c io.Closer
}

func (l *limitedFile) Close() error { return l.c.Close() }

type limitedRemote struct {
	io.Reader
	rr *RemoteReader
}

func (l *limitedRemote) Close() error { return l.rr.Close() }
