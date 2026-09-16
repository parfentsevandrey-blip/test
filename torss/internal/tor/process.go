package tor

import (
	"context"
	"errors"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"torss/internal/proc"
)

// Instance is a running tor process.
type Instance struct {
	layout Layout
	opts   Options
	torrc  string
	proc   *proc.Process
	ctl    *Control
	logf   func(string, ...any)
	mu     sync.Mutex
	lastBS Bootstrap
	fatal  string
}

// Start writes the torrc into opts.DataDir and launches tor.
func Start(l Layout, o Options, logf func(string, ...any)) (*Instance, error) {
	if err := os.MkdirAll(o.DataDir, 0o700); err != nil {
		return nil, err
	}
	text, err := Torrc(l, o)
	if err != nil {
		return nil, err
	}
	torrcPath := filepath.Join(o.DataDir, "torrc")
	if err := os.WriteFile(torrcPath, []byte(text), 0o600); err != nil {
		return nil, err
	}
	// A stale lock from a crashed tor makes the new one refuse to start.
	_ = os.Remove(filepath.Join(o.DataDir, "lock"))

	in := &Instance{layout: l, opts: o, torrc: torrcPath, logf: logf}
	p, err := proc.Start(filepath.Join(l.Dir, l.Exe), []string{"-f", torrcPath}, proc.Options{
		Dir:    l.Dir,
		OnLine: in.onLine,
	})
	if err != nil {
		return nil, err
	}
	in.proc = p
	return in, nil
}

func (in *Instance) onLine(line string) {
	in.logf("tor: %s", line)
	if b, ok := ParseBootstrap(line); ok {
		in.mu.Lock()
		in.lastBS = b
		in.mu.Unlock()
	}
	lower := strings.ToLower(line)
	switch {
	case strings.Contains(lower, "could not bind to"),
		strings.Contains(lower, "is it already running"),
		strings.Contains(lower, "failed to parse/validate config"),
		strings.Contains(lower, "reading config failed"):
		in.mu.Lock()
		in.fatal = line
		in.mu.Unlock()
	}
}

// Done is closed when the tor process exits.
func (in *Instance) Done() <-chan struct{} { return in.proc.Done() }

// Control returns the control connection (nil until connected).
func (in *Instance) Control() *Control { return in.ctl }

// connectControl retries the control port until tor accepts us.
func (in *Instance) connectControl(ctx context.Context) error {
	addr := fmt.Sprintf("127.0.0.1:%d", in.opts.Ports.TorControl)
	cookie := filepath.Join(in.opts.DataDir, "control_auth_cookie")
	var lastErr error
	for {
		select {
		case <-ctx.Done():
			return fmt.Errorf("control port: %w (last: %v)", ctx.Err(), lastErr)
		case <-in.proc.Done():
			return fmt.Errorf("tor exited before control port was ready: %v", in.proc.Err())
		case <-time.After(500 * time.Millisecond):
		}
		c, err := Dial(addr, cookie, 2*time.Second)
		if err == nil {
			in.ctl = c
			return nil
		}
		lastErr = err
	}
}

// WaitBootstrapped waits until Tor reports 100 %, or fails when the process
// dies, the total timeout passes, or progress stalls for `stall`.
// onProgress is called on every change.
func (in *Instance) WaitBootstrapped(ctx context.Context, timeout, stall time.Duration, onProgress func(Bootstrap)) error {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	cctx, ccancel := context.WithTimeout(ctx, 30*time.Second)
	err := in.connectControl(cctx)
	ccancel()
	if err != nil {
		return err
	}

	last := -1
	lastChange := time.Now()
	t := time.NewTicker(time.Second)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			in.mu.Lock()
			bs := in.lastBS
			in.mu.Unlock()
			return fmt.Errorf("bootstrap timeout at %d%% (%s)", bs.Progress, bs.Summary)
		case <-in.proc.Done():
			in.mu.Lock()
			f := in.fatal
			in.mu.Unlock()
			if f != "" {
				return fmt.Errorf("tor exited: %s", f)
			}
			return fmt.Errorf("tor exited: %v", in.proc.Err())
		case <-t.C:
		}
		bs, err := in.ctl.BootstrapStatus()
		if err != nil {
			in.mu.Lock()
			bs = in.lastBS
			in.mu.Unlock()
		}
		in.mu.Lock()
		if bs.Progress >= in.lastBS.Progress {
			in.lastBS = bs
		} else {
			bs = in.lastBS
		}
		f := in.fatal
		in.mu.Unlock()
		if f != "" {
			return errors.New(f)
		}
		if bs.Progress != last {
			last = bs.Progress
			lastChange = time.Now()
			if onProgress != nil {
				onProgress(bs)
			}
		}
		if bs.Progress >= 100 {
			return nil
		}
		if time.Since(lastChange) > stall {
			return fmt.Errorf("bootstrap stalled at %d%% (%s)", bs.Progress, bs.Summary)
		}
	}
}

// Progress returns the last known bootstrap state.
func (in *Instance) Progress() Bootstrap {
	in.mu.Lock()
	defer in.mu.Unlock()
	return in.lastBS
}

// NewIdentity requests new circuits.
func (in *Instance) NewIdentity() error {
	if in.ctl == nil {
		return errors.New("control port not connected")
	}
	return in.ctl.NewIdentity()
}

// Stop shuts tor down (control SHUTDOWN first, then kill).
func (in *Instance) Stop() {
	if in == nil {
		return
	}
	if in.ctl != nil {
		_ = in.ctl.Shutdown()
		select {
		case <-in.proc.Done():
		case <-time.After(3 * time.Second):
		}
		_ = in.ctl.Close()
	}
	in.proc.Stop(5 * time.Second)
}

// PortFree reports whether 127.0.0.1:port can be bound right now.
func PortFree(port int) bool {
	l, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return false
	}
	l.Close()
	return true
}
