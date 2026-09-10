package tor

import (
	"context"
	"errors"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Bootstrap reports Tor's start-up progress.
type Bootstrap struct {
	Percent int
	Tag     string
	Summary string
	Warning string
}

// Done reports whether the client is fully connected to the Tor network.
func (b Bootstrap) Done() bool { return b.Percent >= 100 }

// LogFunc receives log lines from Tor and from the supervisor itself.
type LogFunc func(level, msg string)

// Options configures a Process.
type Options struct {
	DataDir     string
	Binaries    Binaries
	Transport   Transport
	Bridges     []string
	ExtraTorrc  map[string]string
	Log         LogFunc
	OnBootstrap func(Bootstrap)
}

// Process supervises a tor child process and owns the authenticated control
// connection to it.
type Process struct {
	opts  Options
	ports Ports

	cmd     *exec.Cmd
	control *Conn
	output  *outputCapture

	mu        sync.RWMutex
	bootstrap Bootstrap
	exited    chan struct{}
	exitErr   error

	stopOnce sync.Once
}

// SOCKSAddress returns the loopback address of Tor's SOCKS listener.
func (p *Process) SOCKSAddress() string { return net.JoinHostPort("127.0.0.1", itoa(p.ports.SOCKS)) }

// DNSAddress returns the loopback address of Tor's DNS listener.
func (p *Process) DNSAddress() string { return net.JoinHostPort("127.0.0.1", itoa(p.ports.DNS)) }

// ControlAddress returns the loopback address of Tor's control listener.
func (p *Process) ControlAddress() string {
	return net.JoinHostPort("127.0.0.1", itoa(p.ports.Control))
}

// Control returns the authenticated control connection.
func (p *Process) Control() *Conn { return p.control }

// Bootstrap returns the most recent bootstrap status.
func (p *Process) Bootstrap() Bootstrap {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.bootstrap
}

func itoa(i int) string { return strconv.Itoa(i) }

func (p *Process) logf(level, format string, args ...any) {
	if p.opts.Log == nil {
		return
	}
	p.opts.Log(level, fmt.Sprintf(format, args...))
}

// Start launches tor, waits for the control port and authenticates. The
// returned Process is running but not necessarily bootstrapped; use
// WaitBootstrapped for that.
func Start(ctx context.Context, opts Options) (*Process, error) {
	if opts.Binaries.Tor == "" {
		return nil, ErrTorNotFound
	}
	if err := os.MkdirAll(opts.DataDir, 0o700); err != nil {
		return nil, fmt.Errorf("create data directory: %w", err)
	}

	ports, err := allocatePorts()
	if err != nil {
		return nil, err
	}

	p := &Process{opts: opts, ports: ports, exited: make(chan struct{})}

	torrcPath, err := WriteTorrc(TorrcOptions{
		DataDir:   opts.DataDir,
		Binaries:  opts.Binaries,
		Ports:     ports,
		Transport: opts.Transport,
		Bridges:   opts.Bridges,
		Extra:     opts.ExtraTorrc,
		OwningPID: os.Getpid(),
	})
	if err != nil {
		return nil, err
	}

	cmd := exec.Command(opts.Binaries.Tor, "-f", torrcPath)
	cmd.Dir = filepath.Dir(opts.Binaries.Tor)
	hideWindow(cmd)

	p.output = newOutputCapture(opts.Log)
	cmd.Stdout = p.output
	cmd.Stderr = p.output

	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("start tor (%s): %w", opts.Binaries.Tor, err)
	}
	p.cmd = cmd
	p.logf("info", "tor started (pid %d), control port %d", cmd.Process.Pid, ports.Control)

	go func() {
		p.exitErr = cmd.Wait()
		p.output.Flush()
		close(p.exited)
	}()

	if err := p.connectControl(ctx); err != nil {
		p.Stop()
		return nil, err
	}
	return p, nil
}

// startupFailure explains why Tor would not start, using Tor's own words.
//
// "exit status 1" tells the user nothing they can act on; Tor has already said
// what it objected to, and discarding that leaves them with a dead end.
func (p *Process) startupFailure(reason error) error {
	explanation := ""
	if p.output != nil {
		explanation = p.output.Explain()
	}
	if explanation == "" {
		return fmt.Errorf("%w\n\nTor produced no output, which usually means the executable "+
			"could not run at all — antivirus or SmartScreen may have blocked it", reason)
	}
	return fmt.Errorf("%w\n\nTor said:\n%s", reason, explanation)
}

// connectControl dials the control port, retrying while Tor starts up, then
// authenticates and subscribes to the events the engine needs.
func (p *Process) connectControl(ctx context.Context) error {
	deadline := time.Now().Add(45 * time.Second)
	var lastErr error
	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-p.exited:
			return p.startupFailure(fmt.Errorf("tor exited during startup: %w", p.exitErr))
		default:
		}

		dialCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
		conn, err := Dial(dialCtx, p.ControlAddress())
		cancel()
		if err != nil {
			lastErr = err
			time.Sleep(250 * time.Millisecond)
			continue
		}

		authCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
		err = conn.Authenticate(authCtx, "")
		cancel()
		if err != nil {
			conn.Close()
			// The cookie file may not be written yet; keep retrying.
			lastErr = err
			time.Sleep(250 * time.Millisecond)
			continue
		}

		p.control = conn
		conn.OnEvent("STATUS_CLIENT", p.onStatusClient)
		if err := conn.SetEvents(ctx, "STATUS_CLIENT", "CIRC", "STREAM", "NOTICE", "WARN", "ERR"); err != nil {
			conn.Close()
			return err
		}
		p.refreshBootstrap(ctx)
		return nil
	}
	if lastErr == nil {
		lastErr = errors.New("timeout")
	}
	return p.startupFailure(fmt.Errorf("could not reach Tor's control port: %w", lastErr))
}

// onStatusClient turns Tor's STATUS_CLIENT events into Bootstrap updates.
//
// The event body looks like:
//
//	NOTICE BOOTSTRAP PROGRESS=25 TAG=enough_dirinfo SUMMARY="Loading..."
func (p *Process) onStatusClient(ev Event) {
	fields := splitQuoted(ev.Body)
	if len(fields) < 2 || !strings.EqualFold(fields[1], "BOOTSTRAP") {
		return
	}
	b := p.Bootstrap()
	for _, f := range fields[2:] {
		k, v, ok := strings.Cut(f, "=")
		if !ok {
			continue
		}
		switch strings.ToUpper(k) {
		case "PROGRESS":
			if n, err := strconv.Atoi(v); err == nil {
				b.Percent = n
			}
		case "TAG":
			b.Tag = unquote(v)
		case "SUMMARY":
			b.Summary = unquote(v)
		case "WARNING":
			b.Warning = unquote(v)
		}
	}
	p.setBootstrap(b)
}

func (p *Process) setBootstrap(b Bootstrap) {
	p.mu.Lock()
	changed := p.bootstrap != b
	p.bootstrap = b
	p.mu.Unlock()
	if changed && p.opts.OnBootstrap != nil {
		p.opts.OnBootstrap(b)
	}
}

// refreshBootstrap polls the current phase, which matters when the controller
// attaches after Tor has already made progress and the events were missed.
func (p *Process) refreshBootstrap(ctx context.Context) {
	v, err := p.control.GetInfoValue(ctx, "status/bootstrap-phase")
	if err != nil {
		return
	}
	p.onStatusClient(Event{Type: "STATUS_CLIENT", Body: v})
}

// WaitBootstrapped blocks until Tor reports 100% bootstrapped, the context is
// cancelled, or tor exits.
func (p *Process) WaitBootstrapped(ctx context.Context) error {
	if p.Bootstrap().Done() {
		return nil
	}
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			b := p.Bootstrap()
			return fmt.Errorf("tor bootstrap stalled at %d%% (%s): %w", b.Percent, b.Summary, ctx.Err())
		case <-p.exited:
			return p.startupFailure(fmt.Errorf("tor exited during bootstrap: %w", p.exitErr))
		case <-ticker.C:
			p.refreshBootstrap(ctx)
			if p.Bootstrap().Done() {
				return nil
			}
		}
	}
}

// Stop shuts Tor down, preferring a clean control-port shutdown and falling
// back to killing the process.
func (p *Process) Stop() {
	p.stopOnce.Do(func() {
		if p.control != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			_ = p.control.Signal(ctx, "HALT")
			cancel()
			p.control.Close()
		}
		if p.cmd == nil || p.cmd.Process == nil {
			return
		}
		select {
		case <-p.exited:
			return
		case <-time.After(4 * time.Second):
		}
		p.logf("warn", "tor did not exit cleanly, terminating")
		_ = p.cmd.Process.Kill()
		select {
		case <-p.exited:
		case <-time.After(3 * time.Second):
		}
	})
}

// Exited returns a channel closed when the tor process terminates.
func (p *Process) Exited() <-chan struct{} { return p.exited }

// allocatePorts asks the OS for three free loopback ports. There is an
// unavoidable race between closing the listener and Tor binding the port, but
// the OS does not hand the same ephemeral port out twice in quick succession,
// and a collision surfaces immediately as a Tor start-up failure.
func allocatePorts() (Ports, error) {
	var p Ports
	var listeners []net.Listener
	defer func() {
		for _, l := range listeners {
			l.Close()
		}
	}()
	for _, target := range []*int{&p.SOCKS, &p.Control, &p.DNS} {
		l, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			return p, fmt.Errorf("allocate loopback port: %w", err)
		}
		listeners = append(listeners, l)
		*target = l.Addr().(*net.TCPAddr).Port
	}
	return p, nil
}
