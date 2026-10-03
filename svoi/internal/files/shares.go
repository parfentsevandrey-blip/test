// Package files implements the file features of svoi: shared folders that other
// devices can browse, stream and upload to (the NAS use case), and AirDrop-style
// file transfers between devices.
package files

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// Share is a local folder exposed to other devices.
type Share struct {
	ID    string   `json:"id"`
	Name  string   `json:"name"`
	Path  string   `json:"path"`
	Mode  string   `json:"mode"`  // "ro" or "rw"
	Allow []string `json:"allow"` // "*" or device IDs
}

// RemoteShare is what a device tells others about a share.
type RemoteShare struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Mode string `json:"mode"`
}

// Entry is one item of a directory listing.
type Entry struct {
	Name  string `json:"name"`
	IsDir bool   `json:"isDir"`
	Size  int64  `json:"size"`
	MTime int64  `json:"mtime"`
	Mime  string `json:"mime,omitempty"`
}

// ListResult is a directory listing.
type ListResult struct {
	Path     string  `json:"path"`
	CanWrite bool    `json:"canWrite"`
	Entries  []Entry `json:"entries"`
}

// Actor identifies who performs a file operation. The zero value means the
// local user of this device (full access to their own shares).
type Actor = identity.ID

// maxListEntries bounds how much a single listing may return.
const maxListEntries = 20000

// Manager owns the shares and transfers of this device.
type Manager struct {
	cfg Config

	mu       sync.RWMutex
	transfer *transfers
}

// Config wires the manager into the rest of the application.
type Config struct {
	Node *mesh.Node
	// Shares returns the current share definitions (owned by the app config).
	Shares func() []Share
}

// NewManager creates a manager. Call RegisterRPC to serve other devices.
func NewManager(cfg Config) *Manager {
	return &Manager{cfg: cfg}
}

func (m *Manager) share(id string) (Share, bool) {
	for _, s := range m.cfg.Shares() {
		if s.ID == id {
			return s, true
		}
	}
	return Share{}, false
}

func shareAllows(s Share, actor Actor) bool {
	if actor.IsZero() {
		return true
	}
	for _, a := range s.Allow {
		if a == "*" || a == actor.String() {
			return true
		}
	}
	return false
}

// VisibleShares lists the shares the actor may see.
func (m *Manager) VisibleShares(actor Actor) []RemoteShare {
	var out []RemoteShare
	for _, s := range m.cfg.Shares() {
		if shareAllows(s, actor) {
			out = append(out, RemoteShare{ID: s.ID, Name: s.Name, Mode: s.Mode})
		}
	}
	return out
}

func (m *Manager) authorize(actor Actor, id string, write bool) (Share, error) {
	s, ok := m.share(id)
	if !ok || !shareAllows(s, actor) {
		// Do not reveal whether a share exists to someone who may not see it.
		return Share{}, mesh.Errf(mesh.CodeNotFound, "no such shared folder")
	}
	if write && s.Mode != "rw" {
		return Share{}, mesh.Errf(mesh.CodeDenied, "this folder is read-only")
	}
	return s, nil
}

// cleanRel turns a user supplied path ("/a/b", "a/../b") into a clean relative
// path usable with os.Root ("." for the root itself).
func cleanRel(p string) (string, error) {
	if strings.ContainsRune(p, 0) {
		return "", mesh.Errf(mesh.CodeInvalid, "invalid path")
	}
	p = strings.ReplaceAll(p, "\\", "/")
	c := path.Clean("/" + p)
	c = strings.TrimPrefix(c, "/")
	if c == "" {
		return ".", nil
	}
	return c, nil
}

// validName checks a single path component for creation/rename.
func validName(n string) error {
	if n == "" || n == "." || n == ".." || strings.ContainsAny(n, "/\\\x00") || len(n) > 255 {
		return mesh.Errf(mesh.CodeInvalid, "invalid name")
	}
	return nil
}

func mapFSError(err error) error {
	switch {
	case err == nil:
		return nil
	case errors.Is(err, fs.ErrNotExist):
		return mesh.Errf(mesh.CodeNotFound, "not found")
	case errors.Is(err, fs.ErrExist):
		return mesh.Errf(mesh.CodeExists, "already exists")
	case errors.Is(err, fs.ErrPermission):
		return mesh.Errf(mesh.CodeDenied, "permission denied")
	}
	var re *mesh.RPCError
	if errors.As(err, &re) {
		return err
	}
	// os.Root reports escapes (symlinks pointing outside, "..") with a distinct
	// error text; treat them as plain "not found" to leak nothing.
	if strings.Contains(err.Error(), "escapes from parent") || strings.Contains(err.Error(), "path escapes") {
		return mesh.Errf(mesh.CodeNotFound, "not found")
	}
	return mesh.Errf(mesh.CodeInternal, "%v", err)
}

// List returns the entries of a directory inside a share.
func (m *Manager) List(actor Actor, shareID, rel string) (*ListResult, error) {
	s, err := m.authorize(actor, shareID, false)
	if err != nil {
		return nil, err
	}
	c, err := cleanRel(rel)
	if err != nil {
		return nil, err
	}
	root, err := os.OpenRoot(s.Path)
	if err != nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "the shared folder is not available on that device")
	}
	defer root.Close()
	f, err := root.Open(c)
	if err != nil {
		return nil, mapFSError(err)
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return nil, mapFSError(err)
	}
	if !st.IsDir() {
		return nil, mesh.Errf(mesh.CodeInvalid, "not a directory")
	}
	des, err := f.ReadDir(-1)
	if err != nil {
		return nil, mapFSError(err)
	}
	res := &ListResult{Path: "/", CanWrite: s.Mode == "rw"}
	if c != "." {
		res.Path = "/" + c
	}
	for _, de := range des {
		if len(res.Entries) >= maxListEntries {
			break
		}
		name := de.Name()
		info, err := de.Info()
		if err != nil {
			continue
		}
		if de.Type()&fs.ModeSymlink != 0 {
			// Follow links, but only if they stay inside the share.
			resolved, err := root.Stat(path.Join(c, name))
			if err != nil {
				continue
			}
			info = resolved
		}
		e := Entry{Name: name, IsDir: info.IsDir(), MTime: info.ModTime().Unix()}
		if !e.IsDir {
			e.Size = info.Size()
			e.Mime = MimeFor(name)
		}
		res.Entries = append(res.Entries, e)
	}
	sort.Slice(res.Entries, func(i, j int) bool {
		a, b := res.Entries[i], res.Entries[j]
		if a.IsDir != b.IsDir {
			return a.IsDir
		}
		return strings.ToLower(a.Name) < strings.ToLower(b.Name)
	})
	return res, nil
}

// FileMeta describes a regular file inside a share.
type FileMeta struct {
	Name  string `json:"name"`
	Size  int64  `json:"size"`
	MTime int64  `json:"mtime"`
	Mime  string `json:"mime"`
}

// OpenRead opens a regular file for reading. The returned closer also closes
// the root handle.
func (m *Manager) OpenRead(actor Actor, shareID, rel string) (*os.File, FileMeta, error) {
	s, err := m.authorize(actor, shareID, false)
	if err != nil {
		return nil, FileMeta{}, err
	}
	c, err := cleanRel(rel)
	if err != nil {
		return nil, FileMeta{}, err
	}
	root, err := os.OpenRoot(s.Path)
	if err != nil {
		return nil, FileMeta{}, mesh.Errf(mesh.CodeNotFound, "the shared folder is not available on that device")
	}
	defer root.Close() // the file handle stays valid after the root is closed
	f, err := root.Open(c)
	if err != nil {
		return nil, FileMeta{}, mapFSError(err)
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, FileMeta{}, mapFSError(err)
	}
	if st.IsDir() {
		f.Close()
		return nil, FileMeta{}, mesh.Errf(mesh.CodeInvalid, "this is a folder, not a file")
	}
	return f, FileMeta{Name: path.Base(c), Size: st.Size(), MTime: st.ModTime().Unix(), Mime: MimeFor(path.Base(c))}, nil
}

// PendingFile is a file being written atomically: bytes go to a temporary
// sibling and appear under the final name only on Commit.
type PendingFile struct {
	root      *os.Root
	tmp       string
	dst       string
	overwrite bool
	f         *os.File
	done      bool
}

// Write implements io.Writer.
func (p *PendingFile) Write(b []byte) (int, error) { return p.f.Write(b) }

// Commit moves the data to its final name.
func (p *PendingFile) Commit() error {
	if p.done {
		return nil
	}
	p.done = true
	if err := p.f.Close(); err != nil {
		p.root.Remove(p.tmp)
		p.root.Close()
		return mapFSError(err)
	}
	defer p.root.Close()
	if !p.overwrite {
		if _, err := p.root.Lstat(p.dst); err == nil {
			p.root.Remove(p.tmp)
			return mesh.Errf(mesh.CodeExists, "a file with this name already exists")
		}
	}
	if err := p.root.Rename(p.tmp, p.dst); err != nil {
		p.root.Remove(p.tmp)
		return mapFSError(err)
	}
	return nil
}

// Abort discards the data.
func (p *PendingFile) Abort() {
	if p.done {
		return
	}
	p.done = true
	p.f.Close()
	p.root.Remove(p.tmp)
	p.root.Close()
}

// Create starts writing a file inside a read-write share.
func (m *Manager) Create(actor Actor, shareID, rel string, overwrite bool) (*PendingFile, error) {
	s, err := m.authorize(actor, shareID, true)
	if err != nil {
		return nil, err
	}
	c, err := cleanRel(rel)
	if err != nil {
		return nil, err
	}
	if c == "." {
		return nil, mesh.Errf(mesh.CodeInvalid, "a file name is required")
	}
	if err := validName(path.Base(c)); err != nil {
		return nil, err
	}
	root, err := os.OpenRoot(s.Path)
	if err != nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "the shared folder is not available on that device")
	}
	dir := path.Dir(c)
	if st, err := root.Stat(dir); err != nil || !st.IsDir() {
		root.Close()
		return nil, mesh.Errf(mesh.CodeNotFound, "the target folder does not exist")
	}
	tmp := path.Join(dir, fmt.Sprintf(".svoi-part-%d-%s", time.Now().UnixNano(), randHex(4)))
	f, err := root.OpenFile(tmp, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o644)
	if err != nil {
		root.Close()
		return nil, mapFSError(err)
	}
	return &PendingFile{root: root, tmp: tmp, dst: c, overwrite: overwrite, f: f}, nil
}

// Op performs mkdir / rename / delete in a read-write share.
func (m *Manager) Op(actor Actor, shareID, op, rel, to string) error {
	s, err := m.authorize(actor, shareID, true)
	if err != nil {
		return err
	}
	c, err := cleanRel(rel)
	if err != nil {
		return err
	}
	if c == "." {
		return mesh.Errf(mesh.CodeInvalid, "cannot modify the root of a shared folder")
	}
	root, err := os.OpenRoot(s.Path)
	if err != nil {
		return mesh.Errf(mesh.CodeNotFound, "the shared folder is not available on that device")
	}
	defer root.Close()
	switch op {
	case "mkdir":
		if err := validName(path.Base(c)); err != nil {
			return err
		}
		return mapFSError(root.Mkdir(c, 0o755))
	case "delete":
		st, err := root.Lstat(c)
		if err != nil {
			return mapFSError(err)
		}
		if st.IsDir() {
			return mapFSError(root.RemoveAll(c))
		}
		return mapFSError(root.Remove(c))
	case "rename":
		dst, err := cleanRel(to)
		if err != nil {
			return err
		}
		if dst == "." {
			return mesh.Errf(mesh.CodeInvalid, "invalid destination")
		}
		if err := validName(path.Base(dst)); err != nil {
			return err
		}
		if _, err := root.Lstat(dst); err == nil {
			return mesh.Errf(mesh.CodeExists, "the destination already exists")
		}
		return mapFSError(root.Rename(c, dst))
	}
	return mesh.Errf(mesh.CodeInvalid, "unknown operation %q", op)
}

// ---- helpers ----

var builtinMime = map[string]string{
	".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".gif": "image/gif",
	".webp": "image/webp", ".svg": "image/svg+xml", ".bmp": "image/bmp", ".avif": "image/avif",
	".heic": "image/heic", ".ico": "image/x-icon",
	".mp4": "video/mp4", ".m4v": "video/mp4", ".webm": "video/webm", ".mkv": "video/x-matroska",
	".mov": "video/quicktime", ".avi": "video/x-msvideo",
	".mp3": "audio/mpeg", ".m4a": "audio/mp4", ".ogg": "audio/ogg", ".opus": "audio/ogg",
	".flac": "audio/flac", ".wav": "audio/wav", ".aac": "audio/aac",
	".pdf": "application/pdf", ".txt": "text/plain; charset=utf-8", ".md": "text/markdown; charset=utf-8",
	".json": "application/json", ".csv": "text/csv; charset=utf-8", ".html": "text/html; charset=utf-8",
	".zip": "application/zip", ".gz": "application/gzip", ".tar": "application/x-tar",
	".log": "text/plain; charset=utf-8", ".xml": "application/xml", ".yml": "text/plain; charset=utf-8",
	".yaml": "text/plain; charset=utf-8",
}

// MimeFor guesses a content type from a file name without depending on the
// operating system's MIME database (which minimal NAS firmwares often lack).
func MimeFor(name string) string {
	ext := strings.ToLower(filepath.Ext(name))
	if m, ok := builtinMime[ext]; ok {
		return m
	}
	if m := mime.TypeByExtension(ext); m != "" {
		return m
	}
	return "application/octet-stream"
}

func randHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// copyN copies exactly n bytes (or until EOF if n < 0).
func copyN(dst io.Writer, src io.Reader, n int64) (int64, error) {
	if n < 0 {
		return io.Copy(dst, src)
	}
	return io.CopyN(dst, src, n)
}
