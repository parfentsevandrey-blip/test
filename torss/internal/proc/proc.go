// Package proc starts helper binaries (tor, sing-box, netsh) as hidden child
// processes whose lifetime is tied to ours.
package proc

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"sync"
	"time"
)

// Process is a running child process.
type Process struct {
	cmd  *exec.Cmd
	done chan struct{}
	err  error
	once sync.Once
}

// Options for Start.
type Options struct {
	Dir string
	Env []string
	// OnLine receives every stdout/stderr line (already trimmed). May be nil.
	OnLine func(line string)
}

// Start launches exe with args. The process is hidden (no console window) and
// on Windows placed in a job object so it is killed if we die.
func Start(exe string, args []string, opt Options) (*Process, error) {
	cmd := exec.Command(exe, args...)
	cmd.Dir = opt.Dir
	if opt.Env != nil {
		cmd.Env = opt.Env
	}
	prepare(cmd)

	pr, pw := io.Pipe()
	cmd.Stdout = pw
	cmd.Stderr = pw

	if err := cmd.Start(); err != nil {
		pw.Close()
		return nil, fmt.Errorf("start %s: %w", exe, err)
	}
	if err := attachToJob(cmd); err != nil {
		// Not fatal: we still kill children explicitly on exit.
		if opt.OnLine != nil {
			opt.OnLine("warning: job object: " + err.Error())
		}
	}

	p := &Process{cmd: cmd, done: make(chan struct{})}
	go func() {
		sc := bufio.NewScanner(pr)
		sc.Buffer(make([]byte, 64*1024), 1024*1024)
		for sc.Scan() {
			if opt.OnLine != nil {
				opt.OnLine(sc.Text())
			}
		}
	}()
	go func() {
		p.err = cmd.Wait()
		pw.Close()
		close(p.done)
	}()
	return p, nil
}

// Done is closed when the process exits.
func (p *Process) Done() <-chan struct{} { return p.done }

// Exited reports whether the process has exited.
func (p *Process) Exited() bool {
	select {
	case <-p.done:
		return true
	default:
		return false
	}
}

// Err returns the exit error after Done is closed.
func (p *Process) Err() error { return p.err }

// PID returns the process id.
func (p *Process) PID() int {
	if p.cmd.Process == nil {
		return 0
	}
	return p.cmd.Process.Pid
}

// Stop kills the process and waits up to timeout for it to exit.
func (p *Process) Stop(timeout time.Duration) {
	if p == nil {
		return
	}
	p.once.Do(func() {
		if p.cmd.Process != nil && !p.Exited() {
			_ = p.cmd.Process.Kill()
		}
	})
	select {
	case <-p.done:
	case <-time.After(timeout):
	}
}

// Run executes exe synchronously, returning combined output. Used for netsh etc.
func Run(ctx context.Context, exe string, args ...string) (string, error) {
	cmd := exec.CommandContext(ctx, exe, args...)
	prepare(cmd)
	out, err := cmd.CombinedOutput()
	if err != nil {
		var ee *exec.ExitError
		if errors.As(err, &ee) {
			return string(out), fmt.Errorf("%s %v: exit %d: %s", exe, args, ee.ExitCode(), string(out))
		}
		return string(out), err
	}
	return string(out), nil
}
