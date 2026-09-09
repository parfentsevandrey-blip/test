package tor

import (
	"context"
	"fmt"
	"net"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Circuit pool tuning.
const (
	// maxPooledCircuits caps how many isolated circuits are kept alive at
	// once. Each one costs relay bandwidth to build, so this trades isolation
	// against politeness to the network.
	maxPooledCircuits = 24

	// circuitMaxAge mirrors Tor's MaxCircuitDirtiness: a circuit stops taking
	// new streams once it is this old, so a long session is not carried end to
	// end by one set of relays.
	circuitMaxAge = 10 * time.Minute

	// circuitBuildTimeout bounds a single build attempt.
	circuitBuildTimeout = 90 * time.Second

	// streamAttachTimeout bounds how long a new stream waits for a policy
	// circuit before it is failed closed.
	streamAttachTimeout = 100 * time.Second
)

// CircuitInfo is a snapshot of one circuit for the UI.
type CircuitInfo struct {
	ID        string     `json:"id"`
	Status    string     `json:"status"`
	Hops      []HopInfo  `json:"hops"`
	Isolation string     `json:"isolation"`
	Streams   int        `json:"streams"`
	CreatedAt time.Time  `json:"createdAt"`
	BuiltAt   *time.Time `json:"builtAt,omitempty"`
}

// HopInfo describes one relay in a circuit.
type HopInfo struct {
	Fingerprint string `json:"fingerprint"`
	Nickname    string `json:"nickname"`
	Country     string `json:"country"`
	Address     string `json:"address"`
	Role        string `json:"role"` // entry | bridge | middle | exit
}

// Circuit is a circuit TorVeil built and owns.
type Circuit struct {
	ID        string
	Path      Path
	Status    string
	Isolation string
	Port      int
	CreatedAt time.Time
	BuiltAt   time.Time
	Streams   int
}

func (c *Circuit) expired() bool {
	return !c.BuiltAt.IsZero() && time.Since(c.BuiltAt) > circuitMaxAge
}

// usable reports whether the circuit can still take new streams.
func (c *Circuit) usable() bool {
	return !c.expired() && c.Status != "FAILED" && c.Status != "CLOSED"
}

// Manager builds multi-hop circuits according to a policy and attaches every
// new stream to one of them.
//
// It works by taking Tor's stream attachment into its own hands
// (__LeaveStreamsUnattached), which is the only way to enforce a path longer
// than three hops or a specific entry/exit country per stream. The trade-off
// is that TorVeil is now responsible for every stream: a stream that is never
// attached hangs, so attachment fails closed with an explicit error rather
// than silently falling back to a Tor-chosen path.
type Manager struct {
	conn *Conn
	dir  *Directory
	log  LogFunc

	mu       sync.Mutex
	policy   PathPolicy
	circuits map[string]*Circuit
	pool     map[string]*Circuit // isolation key -> circuit
	spares   []*Circuit          // pre-built, not yet claimed by a destination
	updated  chan struct{}
	closed   bool

	onChange func()
}

// NewManager creates a circuit manager bound to an authenticated control
// connection.
func NewManager(conn *Conn, dir *Directory, log LogFunc) *Manager {
	m := &Manager{
		conn:     conn,
		dir:      dir,
		log:      log,
		circuits: make(map[string]*Circuit),
		pool:     make(map[string]*Circuit),
		updated:  make(chan struct{}),
	}
	conn.OnEvent("CIRC", m.onCirc)
	conn.OnEvent("STREAM", m.onStream)
	return m
}

// SetOnChange registers a callback fired whenever the circuit set changes.
func (m *Manager) SetOnChange(f func()) {
	m.mu.Lock()
	m.onChange = f
	m.mu.Unlock()
}

func (m *Manager) logf(level, format string, args ...any) {
	if m.log != nil {
		m.log(level, fmt.Sprintf(format, args...))
	}
}

// Engage takes over stream attachment. Every stream created from this point on
// is TorVeil's responsibility.
func (m *Manager) Engage(ctx context.Context) error {
	return m.conn.SetConf(ctx, map[string]string{"__LeaveStreamsUnattached": "1"})
}

// Release hands stream attachment back to Tor.
func (m *Manager) Release(ctx context.Context) error {
	m.mu.Lock()
	m.closed = true
	m.mu.Unlock()
	return m.conn.SetConf(ctx, map[string]string{"__LeaveStreamsUnattached": "0"})
}

// SetPolicy replaces the path policy. Existing circuits are dropped from the
// pool so subsequent streams use the new policy; already-attached streams keep
// running on their old circuit until they close.
func (m *Manager) SetPolicy(p PathPolicy) {
	p = p.Normalize()
	m.mu.Lock()
	m.policy = p
	m.pool = make(map[string]*Circuit)
	m.spares = nil // built under the old policy, so no longer usable
	m.mu.Unlock()
	m.notify()
}

// Policy returns the current path policy.
func (m *Manager) Policy() PathPolicy {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.policy
}

func (m *Manager) notify() {
	m.mu.Lock()
	ch := m.updated
	m.updated = make(chan struct{})
	cb := m.onChange
	m.mu.Unlock()
	close(ch)
	if cb != nil {
		cb()
	}
}

// onCirc keeps circuit state in sync with Tor.
//
// Event body: <CircID> <Status> [<Path>] [KEY=VALUE ...]
func (m *Manager) onCirc(ev Event) {
	f := splitQuoted(ev.Body)
	if len(f) < 2 {
		return
	}
	id, status := f[0], strings.ToUpper(f[1])

	m.mu.Lock()
	c, known := m.circuits[id]
	if !known {
		m.mu.Unlock()
		return // A circuit Tor built for its own use.
	}
	c.Status = status
	switch status {
	case "BUILT":
		if c.BuiltAt.IsZero() {
			c.BuiltAt = time.Now()
		}
	case "FAILED", "CLOSED":
		delete(m.circuits, id)
		for k, pc := range m.pool {
			if pc == c {
				delete(m.pool, k)
			}
		}
	}
	m.mu.Unlock()

	if status == "BUILT" {
		m.logf("info", "circuit %s built: %s", id, c.Path.Describe())
	} else if status == "FAILED" {
		m.logf("warn", "circuit %s failed: %s", id, ev.Body)
	}
	m.notify()
}

// onStream attaches every new stream to a policy circuit.
//
// Event body: <StreamID> <Status> <CircID> <Target> [KEY=VALUE ...]
func (m *Manager) onStream(ev Event) {
	f := splitQuoted(ev.Body)
	if len(f) < 4 {
		return
	}
	streamID, status, target := f[0], strings.ToUpper(f[1]), f[3]

	switch status {
	case "NEW", "NEWRESOLVE":
		go m.attachStream(streamID, target, status == "NEWRESOLVE")
	case "CLOSED", "FAILED":
		m.notify()
	}
}

// attachStream picks or builds a circuit for one stream and attaches it.
func (m *Manager) attachStream(streamID, target string, isResolve bool) {
	ctx, cancel := context.WithTimeout(context.Background(), streamAttachTimeout)
	defer cancel()

	host, port := splitTarget(target)

	// Onion services are reached over rendezvous circuits that Tor builds and
	// manages itself; a controller-selected general-purpose path cannot serve
	// them. Hand those back to Tor.
	if strings.HasSuffix(strings.ToLower(host), ".onion") {
		if err := m.conn.AttachStream(ctx, streamID, "0"); err != nil {
			m.logf("warn", "attach onion stream %s to %s: %v", streamID, host, err)
		}
		return
	}

	// A DNS resolution does not pin the circuit to a port, but it must still
	// share the destination's circuit so the lookup and the connection that
	// follows exit from the same relay.
	isolation := isolationKey(host)
	wantPort := port
	if isResolve {
		wantPort = 0
	}

	c, err := m.circuitFor(ctx, isolation, wantPort)
	if err != nil {
		m.logf("error", "no circuit for %s: %v", target, err)
		// Fail closed: attaching to a Tor-chosen circuit here would quietly
		// ignore the multi-hop policy the user configured.
		_ = m.conn.CloseStream(context.Background(), streamID, 1)
		return
	}

	if err := m.conn.AttachStream(ctx, streamID, c.ID); err != nil {
		m.logf("warn", "attach stream %s to circuit %s: %v", streamID, c.ID, err)
		m.mu.Lock()
		delete(m.pool, isolation)
		m.mu.Unlock()
		_ = m.conn.CloseStream(context.Background(), streamID, 1)
		return
	}

	m.mu.Lock()
	c.Streams++
	m.mu.Unlock()
	m.notify()
}

// circuitFor returns a usable circuit for an isolation key: the one already
// pooled for it, a pre-built spare, or a freshly built circuit.
func (m *Manager) circuitFor(ctx context.Context, isolation string, port int) (*Circuit, error) {
	m.mu.Lock()
	if m.closed {
		m.mu.Unlock()
		return nil, fmt.Errorf("circuit manager is shut down")
	}
	if c, ok := m.pool[isolation]; ok && c.usable() && compatiblePort(c.Port, port) {
		m.mu.Unlock()
		if err := m.waitBuilt(ctx, c.ID); err != nil {
			return nil, err
		}
		return c, nil
	}
	if c := m.takeSpareLocked(port); c != nil {
		c.Isolation = isolation
		m.pool[isolation] = c
		m.mu.Unlock()
		if err := m.waitBuilt(ctx, c.ID); err == nil {
			return c, nil
		}
		// The spare died while it sat in the pool; fall through and build.
		m.mu.Lock()
		delete(m.pool, isolation)
	}
	m.evictLocked()
	m.mu.Unlock()

	return m.buildCircuit(ctx, isolation, port)
}

// compatiblePort reports whether a circuit built for circuitPort can carry a
// stream to wantPort. Zero on either side means "unconstrained".
func compatiblePort(circuitPort, wantPort int) bool {
	return wantPort == 0 || circuitPort == 0 || circuitPort == wantPort
}

// takeSpareLocked claims a pre-built circuit. The caller holds m.mu.
func (m *Manager) takeSpareLocked(port int) *Circuit {
	for i, c := range m.spares {
		if !c.usable() || !compatiblePort(c.Port, port) {
			continue
		}
		m.spares = append(m.spares[:i], m.spares[i+1:]...)
		return c
	}
	return nil
}

// evictLocked drops the oldest pooled circuits once the pool is full. The
// caller holds m.mu.
func (m *Manager) evictLocked() {
	if len(m.pool) < maxPooledCircuits {
		return
	}
	type entry struct {
		key string
		c   *Circuit
	}
	entries := make([]entry, 0, len(m.pool))
	for k, c := range m.pool {
		entries = append(entries, entry{k, c})
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i].c.CreatedAt.Before(entries[j].c.CreatedAt) })
	for i := 0; i < len(entries)/4+1 && i < len(entries); i++ {
		delete(m.pool, entries[i].key)
	}
}

// buildCircuit selects a path and asks Tor to build it.
func (m *Manager) buildCircuit(ctx context.Context, isolation string, port int) (*Circuit, error) {
	policy := m.Policy()

	loadFamilies := func(fps []string) error {
		fctx, cancel := context.WithTimeout(ctx, 15*time.Second)
		defer cancel()
		return m.dir.LoadFamilies(fctx, m.conn, fps)
	}
	path, err := m.dir.SelectPathChecked(loadFamilies, policy, port)
	if err != nil {
		return nil, err
	}

	buildCtx, cancel := context.WithTimeout(ctx, circuitBuildTimeout)
	defer cancel()

	id, err := m.extend(buildCtx, path)
	if err != nil {
		return nil, err
	}

	// The circuit may already be registered by the incremental build path, and
	// a CIRC event may already have advanced its status. Reuse that entry
	// rather than replacing it, or the status the event recorded is lost and
	// the wait below never sees it.
	m.mu.Lock()
	c, ok := m.circuits[id]
	if !ok {
		c = &Circuit{ID: id, Status: "LAUNCHED", CreatedAt: time.Now()}
		m.circuits[id] = c
	}
	c.Path = path
	c.Isolation = isolation
	c.Port = port
	if isolation != "" {
		m.pool[isolation] = c
	}
	m.mu.Unlock()
	m.notify()

	if err := m.waitBuilt(buildCtx, id); err != nil {
		return nil, err
	}
	return c, nil
}

// extend issues the circuit build. The explicit whole-path form is preferred
// because Tor then extends the circuit itself, one hop at a time, without a
// control round-trip per hop. When Tor rejects the whole path — which happens
// with a bridge whose descriptor has not been fetched yet — the circuit is
// grown hop by hop instead.
func (m *Manager) extend(ctx context.Context, path Path) (string, error) {
	id, err := m.conn.ExtendCircuit(ctx, "0", path.Fingerprints)
	if err == nil {
		return id, nil
	}
	m.logf("info", "whole-path build rejected (%v), extending hop by hop", err)

	id, err = m.conn.ExtendCircuit(ctx, "0", path.Fingerprints[:1])
	if err != nil {
		return "", fmt.Errorf("build first hop: %w", err)
	}
	// Register it now so CIRC events for this circuit are not discarded as
	// belonging to one of Tor's own; buildCircuit fills in the rest.
	m.mu.Lock()
	if _, ok := m.circuits[id]; !ok {
		m.circuits[id] = &Circuit{ID: id, Path: path, Status: "LAUNCHED", CreatedAt: time.Now()}
	}
	m.mu.Unlock()

	for i, fp := range path.Fingerprints[1:] {
		if err := m.waitExtended(ctx, id, i+1); err != nil {
			return "", err
		}
		if _, err := m.conn.ExtendCircuit(ctx, id, []string{fp}); err != nil {
			return "", fmt.Errorf("extend to hop %d: %w", i+2, err)
		}
	}
	return id, nil
}

// waitBuilt blocks until the circuit reaches BUILT or fails.
//
// It waits on CIRC events but also re-checks Tor's own circuit table on a
// timer. An event that arrives in the window between EXTENDCIRCUIT returning
// an id and that id being registered has nowhere to go, and without the poll a
// circuit that was already built would wait for a notification that has
// already happened.
func (m *Manager) waitBuilt(ctx context.Context, id string) error {
	for {
		m.mu.Lock()
		c, ok := m.circuits[id]
		ch := m.updated
		var status string
		if ok {
			status = c.Status
		}
		m.mu.Unlock()

		switch {
		case !ok:
			return fmt.Errorf("circuit %s went away before it was built", id)
		case status == "BUILT":
			return nil
		case status == "FAILED" || status == "CLOSED":
			return fmt.Errorf("circuit %s %s", id, strings.ToLower(status))
		}

		select {
		case <-ch:
		case <-time.After(500 * time.Millisecond):
			m.pollCircuitStatus(ctx, id)
		case <-ctx.Done():
			return fmt.Errorf("circuit %s stalled in %s: %w", id, strings.ToLower(status), ctx.Err())
		}
	}
}

// pollCircuitStatus reconciles one circuit's state with Tor's circuit table.
func (m *Manager) pollCircuitStatus(ctx context.Context, id string) {
	info, err := m.conn.GetInfo(ctx, "circuit-status")
	if err != nil {
		return
	}
	status, ok := circuitStatusOf(info["circuit-status"], id)
	if !ok {
		status = "CLOSED"
	}

	m.mu.Lock()
	c, known := m.circuits[id]
	changed := known && c.Status != status
	if changed {
		c.Status = status
		if status == "BUILT" && c.BuiltAt.IsZero() {
			c.BuiltAt = time.Now()
		}
	}
	m.mu.Unlock()

	if changed {
		m.notify()
	}
}

// circuitStatusOf finds a circuit's status in the "circuit-status" table.
func circuitStatusOf(table, id string) (string, bool) {
	for _, line := range strings.Split(table, "\n") {
		f := strings.Fields(strings.TrimSpace(line))
		if len(f) < 2 || f[0] != id {
			continue
		}
		return strings.ToUpper(f[1]), true
	}
	return "", false
}

// waitExtended blocks until the circuit has at least n hops, which is how the
// incremental build path knows it may extend again.
func (m *Manager) waitExtended(ctx context.Context, id string, n int) error {
	for {
		info, err := m.conn.GetInfo(ctx, "circuit-status")
		if err != nil {
			return err
		}
		if hops, ok := circuitHopCount(info["circuit-status"], id); ok && hops >= n {
			return nil
		}
		select {
		case <-time.After(200 * time.Millisecond):
		case <-ctx.Done():
			return fmt.Errorf("circuit %s did not reach %d hops: %w", id, n, ctx.Err())
		}
	}
}

// circuitHopCount finds a circuit in the "circuit-status" table and counts the
// relays currently on it.
func circuitHopCount(status, id string) (int, bool) {
	for _, line := range strings.Split(status, "\n") {
		f := strings.Fields(strings.TrimSpace(line))
		if len(f) < 3 || f[0] != id {
			continue
		}
		if strings.ToUpper(f[1]) == "FAILED" || strings.ToUpper(f[1]) == "CLOSED" {
			return 0, false
		}
		return len(strings.Split(f[2], ",")), true
	}
	return 0, false
}

// Warm pre-builds spare circuits so the first real stream does not pay the
// full build latency. The spares carry no isolation key until a destination
// claims one.
//
// Errors are logged, not returned: failing to warm up is not fatal, the stream
// path still builds on demand.
func (m *Manager) Warm(ctx context.Context, n int) {
	for i := 0; i < n; i++ {
		// Port 443 constrains the exit to one that allows HTTPS, which is
		// what almost every first stream needs.
		c, err := m.buildCircuit(ctx, "", 443)
		if err != nil {
			m.logf("warn", "pre-build circuit %d: %v", i+1, err)
			return
		}
		m.mu.Lock()
		m.spares = append(m.spares, c)
		m.mu.Unlock()
	}
}

// NewIdentity drops every pooled circuit and asks Tor for a fresh identity, so
// subsequent streams take new paths.
func (m *Manager) NewIdentity(ctx context.Context) error {
	m.mu.Lock()
	ids := make([]string, 0, len(m.circuits))
	for id := range m.circuits {
		ids = append(ids, id)
	}
	m.pool = make(map[string]*Circuit)
	m.mu.Unlock()

	for _, id := range ids {
		if err := m.conn.CloseCircuit(ctx, id); err != nil {
			m.logf("info", "close circuit %s: %v", id, err)
		}
	}
	m.notify()
	return m.conn.Signal(ctx, "NEWNYM")
}

// Circuits returns a snapshot of TorVeil's circuits for display.
func (m *Manager) Circuits() []CircuitInfo {
	m.mu.Lock()
	cs := make([]*Circuit, 0, len(m.circuits))
	for _, c := range m.circuits {
		cs = append(cs, c)
	}
	usesBridge := m.policy.UsesBridge()
	m.mu.Unlock()

	sort.Slice(cs, func(i, j int) bool { return cs[i].CreatedAt.Before(cs[j].CreatedAt) })

	out := make([]CircuitInfo, 0, len(cs))
	for _, c := range cs {
		isolation := c.Isolation
		if isolation == "" {
			isolation = "spare"
		}
		info := CircuitInfo{
			ID:        c.ID,
			Status:    c.Status,
			Isolation: isolation,
			Streams:   c.Streams,
			CreatedAt: c.CreatedAt,
		}
		if !c.BuiltAt.IsZero() {
			t := c.BuiltAt
			info.BuiltAt = &t
		}
		for i, fp := range c.Path.Fingerprints {
			hop := HopInfo{Fingerprint: fp, Role: hopRole(i, len(c.Path.Fingerprints), usesBridge)}
			if i < len(c.Path.Relays) && c.Path.Relays[i] != nil {
				r := c.Path.Relays[i]
				hop.Nickname = r.Nickname
				hop.Country = strings.ToUpper(r.Country)
				hop.Address = r.IP
			}
			info.Hops = append(info.Hops, hop)
		}
		out = append(out, info)
	}
	return out
}

func hopRole(i, n int, usesBridge bool) string {
	switch {
	case i == 0 && usesBridge:
		return "bridge"
	case i == 0:
		return "entry"
	case i == n-1:
		return "exit"
	default:
		return "middle"
	}
}

// isolationKey groups streams that may share a circuit. Keying on the
// registrable-ish suffix of the destination keeps a page's subresources on one
// circuit (so it loads at one exit) while still separating unrelated sites.
func isolationKey(host string) string {
	host = strings.ToLower(strings.TrimSuffix(host, "."))
	if host == "" {
		return "default"
	}
	if ip := net.ParseIP(host); ip != nil {
		return host
	}
	parts := strings.Split(host, ".")
	if len(parts) <= 2 {
		return host
	}
	return strings.Join(parts[len(parts)-2:], ".")
}

// splitTarget parses a stream target, which is "host:port" for connections and
// a bare hostname for DNS resolutions.
func splitTarget(target string) (string, int) {
	target = strings.Trim(target, "\"")
	host, portStr, err := net.SplitHostPort(target)
	if err != nil {
		return strings.Trim(target, "[]"), 0
	}
	port, err := strconv.Atoi(portStr)
	if err != nil {
		return host, 0
	}
	return host, port
}
