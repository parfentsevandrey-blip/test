package files

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

// Transfers are AirDrop-style: the sender *offers* a file (name, size), the
// recipient accepts (or auto-accepts) and then *pulls* it, resuming from
// wherever a previous attempt stopped. Pulling rather than pushing makes the
// whole thing robust against disconnects and sleeping devices: the file stays
// on the sender, the recipient simply retries whenever the sender is reachable.

// Transfer states (see docs/UI-API.md).
const (
	StateOffered  = "offered"
	StateQueued   = "queued"
	StateActive   = "active"
	StateDone     = "done"
	StateFailed   = "failed"
	StateDeclined = "declined"
	StateCanceled = "canceled"
)

const (
	bucketTransfers = "transfers"
	maxPendingIn    = 200
	maxConcurrentIn = 3
)

// Transfer is the API view of a transfer.
type Transfer struct {
	ID       string      `json:"id"`
	Dir      string      `json:"dir"`
	Peer     identity.ID `json:"peer"`
	PeerName string      `json:"peerName"`
	Name     string      `json:"name"`
	Size     int64       `json:"size"`
	Done     int64       `json:"done"`
	Mime     string      `json:"mime,omitempty"`
	State    string      `json:"state"`
	Speed    float64     `json:"speed"`
	Error    string      `json:"error"`
	Created  int64       `json:"created"`
	Updated  int64       `json:"updated"`
	Finished *int64      `json:"finished"`
	Path     string      `json:"path,omitempty"`
}

// record is the persisted form.
type record struct {
	ID       string      `json:"id"`
	Dir      string      `json:"dir"`
	Peer     identity.ID `json:"peer"`
	Name     string      `json:"name"`
	Size     int64       `json:"size"`
	Mime     string      `json:"mime,omitempty"`
	State    string      `json:"state"`
	Error    string      `json:"error,omitempty"`
	Created  int64       `json:"created"`
	Updated  int64       `json:"updated"`
	Finished int64       `json:"finished,omitempty"`
	Src      string      `json:"src,omitempty"`   // out: file to serve
	Stage    string      `json:"stage,omitempty"` // out: staging directory to clean up
	Part     string      `json:"part,omitempty"`  // in: partial download
	Dest     string      `json:"dest,omitempty"`  // in: final path once done

	// runtime only
	done     int64
	speed    speedometer
	running  bool
	cancel   context.CancelFunc
	nextTry  time.Time
	lastEmit time.Time
}

// TransferSettings are read at the time they matter, so changes apply at once.
type TransferSettings struct {
	DownloadDir string
	AutoAccept  string // own | all | ask
	MaxAutoByte int64  // 0 = unlimited
}

type transfers struct {
	m        *Manager
	db       *store.DB
	dataDir  string
	settings func() TransferSettings
	onChange func(Transfer)

	mu    sync.Mutex
	items map[string]*record
	kick  chan struct{}
	sem   chan struct{}
}

// InitTransfers enables the transfer features. dataDir holds staged outgoing files.
func (m *Manager) InitTransfers(db *store.DB, dataDir string, settings func() TransferSettings, onChange func(Transfer)) error {
	t := &transfers{
		m: m, db: db, dataDir: dataDir, settings: settings, onChange: onChange,
		items: map[string]*record{}, kick: make(chan struct{}, 1), sem: make(chan struct{}, maxConcurrentIn),
	}
	err := db.ForEach(bucketTransfers, func(key string, raw []byte) error {
		var r record
		if err := json.Unmarshal(raw, &r); err != nil {
			return nil
		}
		// A crash may have interrupted a download: it simply resumes.
		if r.State == StateActive {
			r.State = StateQueued
		}
		if r.Dir == "in" && r.Part != "" {
			if st, err := os.Stat(r.Part); err == nil {
				r.done = st.Size()
			}
		}
		if r.Dir == "out" && r.State == StateDone {
			r.done = r.Size
		}
		t.items[r.ID] = &r
		return nil
	})
	if err != nil {
		return err
	}
	m.mu.Lock()
	m.transfer = t
	m.mu.Unlock()
	m.registerTransferRPC()
	return nil
}

func (m *Manager) tr() *transfers {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.transfer
}

// RunTransfers drives offers and downloads until ctx ends.
func (m *Manager) RunTransfers(ctx context.Context) {
	t := m.tr()
	if t == nil {
		return
	}
	events, cancel := m.cfg.Node.Subscribe()
	defer cancel()
	tick := time.NewTicker(2 * time.Second)
	defer tick.Stop()
	t.process(ctx)
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		case <-t.kick:
		case ev := <-events:
			if ev.Kind != mesh.EvPeer {
				continue
			}
		}
		t.process(ctx)
	}
}

// KickTransfers triggers an immediate scheduling pass.
func (m *Manager) KickTransfers() {
	if t := m.tr(); t != nil {
		select {
		case t.kick <- struct{}{}:
		default:
		}
	}
}

func (t *transfers) peerName(id identity.ID) string {
	if p := t.m.cfg.Node.Peer(id); p != nil {
		return p.Name()
	}
	return id.Short()
}

func (r *record) view(t *transfers) Transfer {
	v := Transfer{
		ID: r.ID, Dir: r.Dir, Peer: r.Peer, PeerName: t.peerName(r.Peer), Name: r.Name, Size: r.Size,
		Done: r.done, Mime: r.Mime, State: r.State, Error: r.Error, Created: r.Created, Updated: r.Updated,
	}
	if r.State == StateActive {
		v.Speed = r.speed.rate()
	}
	if r.Finished != 0 {
		f := r.Finished
		v.Finished = &f
	}
	if r.Dir == "in" && r.State == StateDone {
		v.Path = r.Dest
	}
	if r.State == StateDone && r.Dir == "out" {
		v.Done = r.Size
	}
	return v
}

func (t *transfers) save(r *record) {
	if err := t.db.PutJSON(bucketTransfers, r.ID, r); err != nil {
		t.m.cfg.Node.Logger().Error("saving transfer failed", "err", err)
	}
}

func (t *transfers) emitLocked(r *record, force bool) {
	now := time.Now()
	if !force && now.Sub(r.lastEmit) < 250*time.Millisecond {
		return
	}
	r.lastEmit = now
	if t.onChange != nil {
		v := r.view(t)
		go t.onChange(v)
	}
}

// setState changes a transfer's state, persists and publishes it.
func (t *transfers) setStateLocked(r *record, state, errMsg string) {
	r.State = state
	r.Error = errMsg
	r.Updated = time.Now().Unix()
	switch state {
	case StateDone, StateFailed, StateDeclined, StateCanceled:
		r.Finished = r.Updated
		r.speed = speedometer{}
	}
	t.save(r)
	t.emitLocked(r, true)
}

// ---- listing and user actions ----

// Transfers returns all transfers, newest first.
func (m *Manager) Transfers() []Transfer {
	t := m.tr()
	if t == nil {
		return nil
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	out := make([]Transfer, 0, len(t.items))
	for _, r := range t.items {
		out = append(out, r.view(t))
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Created > out[j].Created })
	return out
}

// Transfer returns one transfer.
func (m *Manager) Transfer(id string) (Transfer, bool) {
	t := m.tr()
	if t == nil {
		return Transfer{}, false
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	if r := t.items[id]; r != nil {
		return r.view(t), true
	}
	return Transfer{}, false
}

// PendingOffers counts incoming offers waiting for a decision.
func (m *Manager) PendingOffers() int {
	t := m.tr()
	if t == nil {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	n := 0
	for _, r := range t.items {
		if r.Dir == "in" && r.State == StateOffered {
			n++
		}
	}
	return n
}

func newID(prefix string) string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return prefix + hex.EncodeToString(b)
}

// SendFile stages the bytes from r once and offers the file to each recipient.
func (m *Manager) SendFile(peers []identity.ID, name, mimeType string, r io.Reader) ([]Transfer, error) {
	t := m.tr()
	if t == nil {
		return nil, errors.New("files: transfers are not enabled")
	}
	name = SanitizeName(name)
	for _, p := range peers {
		if m.cfg.Node.Peer(p) == nil {
			return nil, mesh.Errf(mesh.CodeNotFound, "unknown device")
		}
	}
	stage := filepath.Join(t.dataDir, "outbox", newID("b_"))
	if err := os.MkdirAll(stage, 0o700); err != nil {
		return nil, err
	}
	src := filepath.Join(stage, name)
	f, err := os.OpenFile(src, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		os.RemoveAll(stage)
		return nil, err
	}
	n, err := io.Copy(f, r)
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		os.RemoveAll(stage)
		return nil, err
	}
	if mimeType == "" {
		mimeType = MimeFor(name)
	}
	return t.create(peers, name, mimeType, n, src, stage), nil
}

// SendPath offers an existing local file (no copy is made).
func (m *Manager) SendPath(peers []identity.ID, path string) ([]Transfer, error) {
	t := m.tr()
	if t == nil {
		return nil, errors.New("files: transfers are not enabled")
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return nil, err
	}
	st, err := os.Stat(abs)
	if err != nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "%v", err)
	}
	if st.IsDir() {
		return nil, mesh.Errf(mesh.CodeInvalid, "folders cannot be sent; share the folder instead")
	}
	return t.create(peers, SanitizeName(st.Name()), MimeFor(st.Name()), st.Size(), abs, ""), nil
}

func (t *transfers) create(peers []identity.ID, name, mimeType string, size int64, src, stage string) []Transfer {
	now := time.Now().Unix()
	var out []Transfer
	t.mu.Lock()
	for _, p := range peers {
		r := &record{
			ID: newID("t_"), Dir: "out", Peer: p, Name: name, Size: size, Mime: mimeType,
			State: StateQueued, Created: now, Updated: now, Src: src, Stage: stage,
		}
		t.items[r.ID] = r
		t.save(r)
		t.emitLocked(r, true)
		out = append(out, r.view(t))
	}
	t.mu.Unlock()
	t.m.KickTransfers()
	return out
}

// Accept lets an offered incoming transfer proceed.
func (m *Manager) Accept(id string) (Transfer, error) {
	return m.userAction(id, func(t *transfers, r *record) error {
		if r.Dir != "in" || (r.State != StateOffered && r.State != StateDeclined && r.State != StateFailed) {
			return mesh.Errf(mesh.CodeInvalid, "this transfer cannot be accepted")
		}
		r.nextTry = time.Time{}
		t.setStateLocked(r, StateQueued, "")
		return nil
	})
}

// Decline refuses an incoming offer.
func (m *Manager) Decline(id string) (Transfer, error) {
	return m.userAction(id, func(t *transfers, r *record) error {
		if r.Dir != "in" || (r.State != StateOffered && r.State != StateQueued && r.State != StateActive) {
			return mesh.Errf(mesh.CodeInvalid, "this transfer cannot be declined")
		}
		t.stopLocked(r)
		t.setStateLocked(r, StateDeclined, "")
		go t.tell(r.Peer, "xfer.cancel", map[string]string{"id": r.ID})
		t.cleanupPartLocked(r)
		return nil
	})
}

// Cancel stops a transfer in either direction.
func (m *Manager) Cancel(id string) (Transfer, error) {
	return m.userAction(id, func(t *transfers, r *record) error {
		switch r.State {
		case StateDone, StateFailed, StateDeclined, StateCanceled:
			return mesh.Errf(mesh.CodeInvalid, "this transfer is already finished")
		}
		t.stopLocked(r)
		t.setStateLocked(r, StateCanceled, "")
		go t.tell(r.Peer, "xfer.cancel", map[string]string{"id": r.ID})
		t.cleanupPartLocked(r)
		t.cleanupStageLocked(r)
		return nil
	})
}

// Retry restarts a failed or canceled transfer.
func (m *Manager) Retry(id string) (Transfer, error) {
	return m.userAction(id, func(t *transfers, r *record) error {
		if r.State != StateFailed && r.State != StateCanceled && r.State != StateDeclined {
			return mesh.Errf(mesh.CodeInvalid, "only failed or canceled transfers can be retried")
		}
		if r.Dir == "out" && !t.srcAvailable(r) {
			return mesh.Errf(mesh.CodeNotFound, "the source file is no longer available")
		}
		r.Finished = 0
		r.nextTry = time.Time{}
		t.setStateLocked(r, StateQueued, "")
		return nil
	})
}

// Remove deletes a finished transfer from the history.
func (m *Manager) Remove(id string) error {
	t := m.tr()
	if t == nil {
		return errors.New("files: transfers are not enabled")
	}
	t.mu.Lock()
	r := t.items[id]
	if r == nil {
		t.mu.Unlock()
		return mesh.Errf(mesh.CodeNotFound, "no such transfer")
	}
	switch r.State {
	case StateOffered, StateQueued, StateActive:
		t.mu.Unlock()
		return mesh.Errf(mesh.CodeInvalid, "cancel the transfer first")
	}
	delete(t.items, id)
	_ = t.db.Delete(bucketTransfers, id)
	t.cleanupStageLocked(r)
	t.mu.Unlock()
	return nil
}

// ReceivedFile returns the path of a completed incoming file.
func (m *Manager) ReceivedFile(id string) (path, name string, err error) {
	t := m.tr()
	if t == nil {
		return "", "", errors.New("files: transfers are not enabled")
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	r := t.items[id]
	if r == nil || r.Dir != "in" || r.State != StateDone {
		return "", "", mesh.Errf(mesh.CodeNotFound, "no such received file")
	}
	if _, err := os.Stat(r.Dest); err != nil {
		return "", "", mesh.Errf(mesh.CodeNotFound, "the file was moved or deleted")
	}
	return r.Dest, r.Name, nil
}

func (m *Manager) userAction(id string, f func(*transfers, *record) error) (Transfer, error) {
	t := m.tr()
	if t == nil {
		return Transfer{}, errors.New("files: transfers are not enabled")
	}
	t.mu.Lock()
	r := t.items[id]
	if r == nil {
		t.mu.Unlock()
		return Transfer{}, mesh.Errf(mesh.CodeNotFound, "no such transfer")
	}
	err := f(t, r)
	v := r.view(t)
	t.mu.Unlock()
	if err == nil {
		m.KickTransfers()
	}
	return v, err
}

func (t *transfers) stopLocked(r *record) {
	if r.cancel != nil {
		r.cancel()
	}
}

func (t *transfers) cleanupPartLocked(r *record) {
	if r.Dir == "in" && r.Part != "" {
		os.Remove(r.Part)
		r.done = 0
	}
}

// cleanupStageLocked removes a staged outgoing file once no live transfer needs it.
func (t *transfers) cleanupStageLocked(r *record) {
	if r.Stage == "" {
		return
	}
	for _, o := range t.items {
		if o != r && o.Stage == r.Stage {
			switch o.State {
			case StateOffered, StateQueued, StateActive, StateFailed:
				return
			}
		}
	}
	os.RemoveAll(r.Stage)
}

func (t *transfers) srcAvailable(r *record) bool {
	_, err := os.Stat(r.Src)
	return err == nil
}

func (t *transfers) tell(peer identity.ID, method string, args any) {
	p := t.m.cfg.Node.Peer(peer)
	if p == nil || !p.Online() {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = p.Call(ctx, method, args, nil)
}

// ---- scheduling ----

func (t *transfers) process(ctx context.Context) {
	now := time.Now()
	t.mu.Lock()
	var offers, downloads []*record
	for _, r := range t.items {
		if r.running || now.Before(r.nextTry) {
			continue
		}
		p := t.m.cfg.Node.Peer(r.Peer)
		if p == nil || !p.Online() {
			continue
		}
		switch {
		case r.Dir == "out" && r.State == StateQueued:
			offers = append(offers, r)
		case r.Dir == "in" && r.State == StateQueued:
			downloads = append(downloads, r)
		}
	}
	for _, r := range offers {
		r.running = true
		go t.offer(ctx, r)
	}
	for _, r := range downloads {
		select {
		case t.sem <- struct{}{}:
			r.running = true
			go t.download(ctx, r)
		default:
		}
	}
	t.mu.Unlock()
}

type offerMsg struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Size int64  `json:"size"`
	Mime string `json:"mime,omitempty"`
}

// offer tells the recipient about the file.
func (t *transfers) offer(ctx context.Context, r *record) {
	defer func() {
		t.mu.Lock()
		r.running = false
		t.mu.Unlock()
	}()
	t.mu.Lock()
	msg := offerMsg{ID: r.ID, Name: r.Name, Size: r.Size, Mime: r.Mime}
	peer := r.Peer
	if !t.srcAvailable(r) {
		t.setStateLocked(r, StateFailed, "the source file is no longer available")
		t.mu.Unlock()
		return
	}
	t.mu.Unlock()

	p := t.m.cfg.Node.Peer(peer)
	if p == nil {
		return
	}
	cctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	var resp struct{ State string }
	err := p.Call(cctx, "xfer.offer", msg, &resp)
	t.mu.Lock()
	defer t.mu.Unlock()
	if r.State != StateQueued {
		return // canceled meanwhile
	}
	if err != nil {
		r.nextTry = time.Now().Add(5 * time.Second)
		if mesh.IsCode(err, mesh.CodeDenied) || mesh.IsCode(err, mesh.CodeTooLarge) || mesh.IsCode(err, mesh.CodeBusy) {
			t.setStateLocked(r, StateFailed, describeErr(err))
		}
		return
	}
	switch resp.State {
	case "declined":
		t.setStateLocked(r, StateDeclined, "")
	default:
		t.setStateLocked(r, StateOffered, "")
	}
}

type speedometer struct {
	last   time.Time
	bytes  int64
	ema    float64
	primed bool
}

func (s *speedometer) add(n int64) {
	now := time.Now()
	if s.last.IsZero() {
		s.last = now
		s.bytes = n
		return
	}
	s.bytes += n
	if d := now.Sub(s.last); d >= 500*time.Millisecond {
		inst := float64(s.bytes) / d.Seconds()
		if !s.primed {
			s.ema, s.primed = inst, true
		} else {
			s.ema = 0.6*s.ema + 0.4*inst
		}
		s.last, s.bytes = now, 0
	}
}

func (s *speedometer) rate() float64 { return s.ema }

// ---- receiving side ----

func (t *transfers) download(ctx context.Context, r *record) {
	defer func() { <-t.sem }()
	dctx, cancel := context.WithCancel(ctx)
	t.mu.Lock()
	r.cancel = cancel
	set := t.settings()
	peer := r.Peer
	if r.Part == "" {
		dir := set.DownloadDir
		_ = os.MkdirAll(dir, 0o755)
		r.Part = filepath.Join(dir, ".svoi-"+r.ID+".part")
	}
	part, id := r.Part, r.ID
	var offset int64
	if st, err := os.Stat(part); err == nil {
		offset = st.Size()
	}
	r.done = offset
	t.setStateLocked(r, StateActive, "")
	t.mu.Unlock()

	err := t.pull(dctx, id, peer, part, offset)

	cancel()
	t.mu.Lock()
	defer t.mu.Unlock()
	r.cancel = nil
	r.running = false
	if r.State != StateActive {
		return // canceled or declined while downloading
	}
	switch {
	case err == nil:
		dest, ferr := t.finalize(r, set)
		if ferr != nil {
			t.setStateLocked(r, StateFailed, ferr.Error())
			return
		}
		r.Dest = dest
		r.done = r.Size
		t.setStateLocked(r, StateDone, "")
		go t.tell(peer, "xfer.done", map[string]string{"id": id})
	case mesh.IsCode(err, mesh.CodeNotFound), mesh.IsCode(err, mesh.CodeDenied):
		t.setStateLocked(r, StateFailed, "the sender no longer offers this file")
	default:
		// Transient: stay queued and resume from the partial file.
		r.nextTry = time.Now().Add(3 * time.Second)
		t.setStateLocked(r, StateQueued, "")
	}
}

// pull downloads the remainder of the file into part.
func (t *transfers) pull(ctx context.Context, id string, peerID identity.ID, part string, offset int64) error {
	p := t.m.cfg.Node.Peer(peerID)
	if p == nil || !p.Online() {
		return mesh.Errf(mesh.CodeOffline, "sender is offline")
	}
	cs, err := p.OpenStream(ctx, "xfer.get", map[string]any{"id": id, "offset": offset})
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
	t.mu.Lock()
	r := t.items[id]
	if r == nil {
		t.mu.Unlock()
		return errors.New("transfer vanished")
	}
	r.Size = meta.Size
	t.mu.Unlock()
	if offset > meta.Size {
		os.Remove(part)
		return errors.New("partial file is larger than the source; restarting")
	}

	f, err := os.OpenFile(part, os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	buf := make([]byte, 256<<10)
	got := offset
	for got < meta.Size {
		n, rerr := cs.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				return werr
			}
			got += int64(n)
			t.mu.Lock()
			if r := t.items[id]; r != nil {
				r.done = got
				r.speed.add(int64(n))
				t.emitLocked(r, false)
			}
			t.mu.Unlock()
		}
		if rerr != nil {
			if rerr == io.EOF && got >= meta.Size {
				break
			}
			if rerr == io.EOF {
				return io.ErrUnexpectedEOF
			}
			return rerr
		}
	}
	return f.Sync()
}

// finalize moves the completed download to its final unique name.
func (t *transfers) finalize(r *record, set TransferSettings) (string, error) {
	dir := set.DownloadDir
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	st, err := os.Stat(r.Part)
	if err != nil {
		return "", err
	}
	if st.Size() != r.Size {
		os.Remove(r.Part)
		r.done = 0
		return "", fmt.Errorf("size mismatch (%d of %d bytes); try again", st.Size(), r.Size)
	}
	dest := uniquePath(dir, r.Name)
	if err := os.Rename(r.Part, dest); err != nil {
		return "", err
	}
	r.Part = ""
	return dest, nil
}

func uniquePath(dir, name string) string {
	ext := filepath.Ext(name)
	base := strings.TrimSuffix(name, ext)
	p := filepath.Join(dir, name)
	for i := 1; ; i++ {
		if _, err := os.Lstat(p); errors.Is(err, os.ErrNotExist) {
			return p
		}
		p = filepath.Join(dir, fmt.Sprintf("%s (%d)%s", base, i, ext))
	}
}

// SanitizeName reduces a remote-supplied file name to a safe local one.
func SanitizeName(name string) string {
	name = strings.ReplaceAll(name, "\\", "/")
	if i := strings.LastIndex(name, "/"); i >= 0 {
		name = name[i+1:]
	}
	name = strings.Map(func(r rune) rune {
		if r < 32 || r == 127 || strings.ContainsRune(`<>:"|?*`, r) || unicode.Is(unicode.Cf, r) {
			return -1
		}
		return r
	}, name)
	name = strings.Trim(name, " .")
	if len(name) > 200 {
		ext := filepath.Ext(name)
		if len(ext) > 20 {
			ext = ""
		}
		cut := 200 - len(ext)
		for cut > 0 && !isRuneStart(name[cut]) {
			cut--
		}
		name = name[:cut] + ext
	}
	switch strings.ToUpper(strings.TrimSuffix(name, filepath.Ext(name))) {
	case "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "LPT1", "LPT2", "LPT3":
		name = "_" + name
	}
	if name == "" {
		name = "file"
	}
	return name
}

func isRuneStart(b byte) bool { return b&0xC0 != 0x80 }

func describeErr(err error) string {
	var re *mesh.RPCError
	if errors.As(err, &re) {
		return re.Msg
	}
	return err.Error()
}

// ---- RPC handlers ----

func (m *Manager) registerTransferRPC() {
	n := m.cfg.Node
	n.Handle("xfer.offer", func(ctx context.Context, c *mesh.Call) (any, error) {
		t := m.tr()
		var o offerMsg
		if err := c.Decode(&o); err != nil {
			return nil, err
		}
		if o.ID == "" || o.Size < 0 {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad offer")
		}
		set := t.settings()
		t.mu.Lock()
		defer t.mu.Unlock()
		if r := t.items[o.ID]; r != nil {
			if r.Peer != c.Peer.ID || r.Dir != "in" {
				return nil, mesh.Errf(mesh.CodeDenied, "unknown transfer")
			}
			return map[string]string{"state": offerReply(r)}, nil // idempotent retry
		}
		pending := 0
		for _, r := range t.items {
			if r.Dir == "in" && r.State == StateOffered {
				pending++
			}
		}
		if pending >= maxPendingIn {
			return nil, mesh.Errf(mesh.CodeBusy, "too many pending offers")
		}
		now := time.Now().Unix()
		r := &record{
			ID: o.ID, Dir: "in", Peer: c.Peer.ID, Name: SanitizeName(o.Name), Size: o.Size, Mime: o.Mime,
			State: StateOffered, Created: now, Updated: now,
		}
		if t.autoAccept(c.Peer, o.Size, set) {
			r.State = StateQueued
		}
		t.items[r.ID] = r
		t.save(r)
		t.emitLocked(r, true)
		if r.State == StateQueued {
			m.KickTransfers()
		}
		return map[string]string{"state": offerReply(r)}, nil
	})

	n.HandleStream("xfer.get", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		t := m.tr()
		var a struct {
			ID     string
			Offset int64
		}
		if err := c.Decode(&a); err != nil {
			return err
		}
		t.mu.Lock()
		r := t.items[a.ID]
		if r == nil || r.Dir != "out" || r.Peer != c.Peer.ID {
			t.mu.Unlock()
			return mesh.Errf(mesh.CodeNotFound, "no such transfer")
		}
		switch r.State {
		case StateOffered, StateQueued, StateActive:
		case StateDone:
			// the recipient may re-fetch (e.g. it lost the file); allow.
		default:
			t.mu.Unlock()
			return mesh.Errf(mesh.CodeNotFound, "this transfer is no longer available")
		}
		src := r.Src
		t.mu.Unlock()

		f, err := os.Open(src)
		if err != nil {
			t.mu.Lock()
			t.setStateLocked(r, StateFailed, "the source file is no longer available")
			t.mu.Unlock()
			return mesh.Errf(mesh.CodeNotFound, "the source file is no longer available")
		}
		defer f.Close()
		st, err := f.Stat()
		if err != nil {
			return err
		}
		if a.Offset < 0 || a.Offset > st.Size() {
			return mesh.Errf(mesh.CodeInvalid, "offset out of range")
		}
		if _, err := f.Seek(a.Offset, io.SeekStart); err != nil {
			return err
		}
		if err := s.Reply(map[string]any{"size": st.Size(), "name": r.Name}); err != nil {
			return err
		}
		t.mu.Lock()
		r.Size = st.Size()
		r.done = a.Offset
		if r.State != StateDone {
			t.setStateLocked(r, StateActive, "")
		}
		t.mu.Unlock()

		buf := make([]byte, 256<<10)
		sent := a.Offset
		for sent < st.Size() {
			n, rerr := f.Read(buf)
			if n > 0 {
				if _, werr := s.Write(buf[:n]); werr != nil {
					t.sendInterrupted(r)
					return werr
				}
				sent += int64(n)
				t.mu.Lock()
				if r.State == StateActive {
					r.done = sent
					r.speed.add(int64(n))
					t.emitLocked(r, false)
				}
				t.mu.Unlock()
			}
			if rerr != nil {
				if rerr == io.EOF {
					break
				}
				t.sendInterrupted(r)
				return rerr
			}
		}
		t.mu.Lock()
		if r.State == StateActive && sent >= st.Size() {
			r.done = st.Size()
			t.setStateLocked(r, StateDone, "")
			t.cleanupStageLocked(r)
		}
		t.mu.Unlock()
		return nil
	})

	n.Handle("xfer.cancel", func(ctx context.Context, c *mesh.Call) (any, error) {
		t := m.tr()
		var a struct{ ID string }
		if err := c.Decode(&a); err != nil {
			return nil, err
		}
		t.mu.Lock()
		defer t.mu.Unlock()
		r := t.items[a.ID]
		if r == nil || r.Peer != c.Peer.ID {
			return map[string]bool{"ok": true}, nil
		}
		switch r.State {
		case StateDone, StateFailed, StateDeclined, StateCanceled:
			return map[string]bool{"ok": true}, nil
		}
		t.stopLocked(r)
		if r.Dir == "out" {
			t.setStateLocked(r, StateDeclined, "")
			t.cleanupStageLocked(r)
		} else {
			t.setStateLocked(r, StateCanceled, "canceled by the sender")
			t.cleanupPartLocked(r)
		}
		return map[string]bool{"ok": true}, nil
	})

	n.Handle("xfer.done", func(ctx context.Context, c *mesh.Call) (any, error) {
		t := m.tr()
		var a struct{ ID string }
		if err := c.Decode(&a); err != nil {
			return nil, err
		}
		t.mu.Lock()
		defer t.mu.Unlock()
		r := t.items[a.ID]
		if r != nil && r.Dir == "out" && r.Peer == c.Peer.ID && r.State != StateDone {
			r.done = r.Size
			t.setStateLocked(r, StateDone, "")
			t.cleanupStageLocked(r)
		}
		return map[string]bool{"ok": true}, nil
	})
}

func offerReply(r *record) string {
	if r.State == StateDeclined || r.State == StateCanceled {
		return "declined"
	}
	if r.State == StateOffered {
		return "pending"
	}
	return "accepted"
}

func (t *transfers) sendInterrupted(r *record) {
	t.mu.Lock()
	if r.State == StateActive {
		// The recipient will resume; until then the offer stands.
		t.setStateLocked(r, StateOffered, "")
	}
	t.mu.Unlock()
}

// autoAccept decides whether an incoming offer is accepted without asking.
func (t *transfers) autoAccept(p *mesh.Peer, size int64, set TransferSettings) bool {
	if set.MaxAutoByte > 0 && size > set.MaxAutoByte {
		return false
	}
	switch set.AutoAccept {
	case "all":
		return true
	case "ask":
		return false
	default: // "own": devices that belong to the same person
		me := t.m.cfg.Node.Self()
		return me.Owner != "" && strings.EqualFold(p.Member().Owner, me.Owner)
	}
}
