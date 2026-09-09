// Package logging provides TorVeil's in-memory log buffer.
//
// Logs stay in memory and are never written to disk. Tor's notices, circuit
// paths and connection errors together describe a session in enough detail to
// be worth protecting; a privacy client that leaves that on the filesystem for
// anyone with the machine to read has undone part of its own purpose. The
// buffer is bounded, so it is also self-limiting: what falls off the end is
// gone.
package logging

import (
	"fmt"
	"sync"
	"time"
)

// Entry is one log line.
type Entry struct {
	Time    time.Time `json:"time"`
	Level   string    `json:"level"`
	Message string    `json:"message"`
}

// Buffer is a bounded, subscribable ring of log entries.
type Buffer struct {
	mu      sync.RWMutex
	entries []Entry
	max     int

	subs   map[int]func(Entry)
	nextID int
}

// New creates a buffer holding at most max entries.
func New(max int) *Buffer {
	if max < 16 {
		max = 16
	}
	return &Buffer{
		entries: make([]Entry, 0, max),
		max:     max,
		subs:    make(map[int]func(Entry)),
	}
}

// Log appends an entry and notifies subscribers.
func (b *Buffer) Log(level, message string) {
	e := Entry{Time: time.Now(), Level: level, Message: message}

	b.mu.Lock()
	if len(b.entries) >= b.max {
		// Drop the oldest quarter at once rather than shifting on every
		// append, which keeps a busy session from spending its time copying.
		drop := b.max / 4
		b.entries = append(b.entries[:0], b.entries[drop:]...)
	}
	b.entries = append(b.entries, e)
	subs := make([]func(Entry), 0, len(b.subs))
	for _, f := range b.subs {
		subs = append(subs, f)
	}
	b.mu.Unlock()

	for _, f := range subs {
		f(e)
	}
}

// Logf appends a formatted entry.
func (b *Buffer) Logf(level, format string, args ...any) {
	b.Log(level, fmt.Sprintf(format, args...))
}

// Func returns a callback suitable for the LogFunc parameters the other
// packages accept.
func (b *Buffer) Func() func(level, msg string) {
	return func(level, msg string) { b.Log(level, msg) }
}

// Entries returns up to the last n entries, oldest first. Pass 0 for all.
func (b *Buffer) Entries(n int) []Entry {
	b.mu.RLock()
	defer b.mu.RUnlock()
	if n <= 0 || n > len(b.entries) {
		n = len(b.entries)
	}
	out := make([]Entry, n)
	copy(out, b.entries[len(b.entries)-n:])
	return out
}

// Subscribe registers a callback for new entries and returns a function that
// cancels the subscription.
func (b *Buffer) Subscribe(f func(Entry)) func() {
	b.mu.Lock()
	id := b.nextID
	b.nextID++
	b.subs[id] = f
	b.mu.Unlock()

	return func() {
		b.mu.Lock()
		delete(b.subs, id)
		b.mu.Unlock()
	}
}

// Clear discards every buffered entry.
func (b *Buffer) Clear() {
	b.mu.Lock()
	b.entries = b.entries[:0]
	b.mu.Unlock()
}
