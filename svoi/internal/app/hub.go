package app

import (
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"time"
)

// Msg is one server-sent event.
type Msg struct {
	Event string
	Data  []byte
}

// Hub fans events out to connected UI clients (SSE).
type Hub struct {
	mu   sync.Mutex
	subs map[int]chan Msg
	next int
}

// NewHub creates an empty hub.
func NewHub() *Hub { return &Hub{subs: map[int]chan Msg{}} }

// Subscribe registers a client. If the client cannot keep up its channel is
// closed; the SSE handler then ends the response and the browser reconnects and
// re-fetches the full state, so nothing is ever silently lost.
func (h *Hub) Subscribe() (<-chan Msg, func()) {
	ch := make(chan Msg, 256)
	h.mu.Lock()
	h.next++
	id := h.next
	h.subs[id] = ch
	h.mu.Unlock()
	return ch, func() {
		h.mu.Lock()
		if c, ok := h.subs[id]; ok {
			delete(h.subs, id)
			close(c)
		}
		h.mu.Unlock()
	}
}

// Publish sends an event with a JSON payload to everyone.
func (h *Hub) Publish(event string, v any) {
	raw, err := json.Marshal(v)
	if err != nil {
		return
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	for id, ch := range h.subs {
		select {
		case ch <- Msg{Event: event, Data: raw}:
		default:
			delete(h.subs, id)
			close(ch)
		}
	}
}

// Clients returns the number of connected clients.
func (h *Hub) Clients() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.subs)
}

// debouncer coalesces bursts of triggers into one call.
type debouncer struct {
	mu    sync.Mutex
	timer *time.Timer
	d     time.Duration
	f     func()
}

func newDebouncer(d time.Duration, f func()) *debouncer { return &debouncer{d: d, f: f} }

func (b *debouncer) trigger() {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.timer != nil {
		return
	}
	b.timer = time.AfterFunc(b.d, func() {
		b.mu.Lock()
		b.timer = nil
		b.mu.Unlock()
		b.f()
	})
}

// ---- log ring buffer ----

// LogLine is one retained log entry.
type LogLine struct {
	TS    int64  `json:"ts"`
	Level string `json:"level"`
	Msg   string `json:"msg"`
}

// logRing keeps the most recent log lines for the UI's log viewer and also
// forwards everything to an inner handler.
type logRing struct {
	inner slog.Handler
	mu    *sync.Mutex
	buf   *[]LogLine
	attrs []slog.Attr
}

const logCap = 1000

func newLogRing(inner slog.Handler) (*logRing, func(limit int) []LogLine) {
	r := &logRing{inner: inner, mu: &sync.Mutex{}, buf: &[]LogLine{}}
	return r, func(limit int) []LogLine {
		r.mu.Lock()
		defer r.mu.Unlock()
		b := *r.buf
		if limit > 0 && len(b) > limit {
			b = b[len(b)-limit:]
		}
		return append([]LogLine(nil), b...)
	}
}

func (r *logRing) Enabled(ctx context.Context, l slog.Level) bool { return true }

func (r *logRing) Handle(ctx context.Context, rec slog.Record) error {
	var sb strings.Builder
	sb.WriteString(rec.Message)
	add := func(a slog.Attr) bool {
		sb.WriteString(" ")
		sb.WriteString(a.Key)
		sb.WriteString("=")
		sb.WriteString(a.Value.String())
		return true
	}
	for _, a := range r.attrs {
		add(a)
	}
	rec.Attrs(add)
	r.mu.Lock()
	*r.buf = append(*r.buf, LogLine{TS: rec.Time.Unix(), Level: strings.ToLower(rec.Level.String()), Msg: sb.String()})
	if len(*r.buf) > logCap {
		*r.buf = (*r.buf)[len(*r.buf)-logCap:]
	}
	r.mu.Unlock()
	if r.inner != nil && r.inner.Enabled(ctx, rec.Level) {
		return r.inner.Handle(ctx, rec)
	}
	return nil
}

func (r *logRing) WithAttrs(attrs []slog.Attr) slog.Handler {
	var in slog.Handler
	if r.inner != nil {
		in = r.inner.WithAttrs(attrs)
	}
	return &logRing{inner: in, mu: r.mu, buf: r.buf, attrs: append(append([]slog.Attr(nil), r.attrs...), attrs...)}
}

func (r *logRing) WithGroup(name string) slog.Handler {
	var in slog.Handler
	if r.inner != nil {
		in = r.inner.WithGroup(name)
	}
	return &logRing{inner: in, mu: r.mu, buf: r.buf, attrs: r.attrs}
}
