package singbox

import (
	"context"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"time"

	"torss/internal/proc"
)

// Instance is a running sing-box.
type Instance struct {
	proc    *proc.Process
	cfgPath string
	logf    func(string, ...any)
	errLine string
}

// Start writes cfg into workDir/singbox.json and launches sing-box.
func Start(exe, workDir string, cfg []byte, logf func(string, ...any)) (*Instance, error) {
	if err := os.MkdirAll(workDir, 0o700); err != nil {
		return nil, err
	}
	cfgPath := filepath.Join(workDir, "singbox.json")
	if err := os.WriteFile(cfgPath, cfg, 0o600); err != nil {
		return nil, err
	}
	in := &Instance{cfgPath: cfgPath, logf: logf}
	p, err := proc.Start(exe, []string{"run", "-c", cfgPath, "-D", workDir, "--disable-color"}, proc.Options{
		Dir:    filepath.Dir(exe), // wintun.dll must be found next to sing-box.exe
		OnLine: in.onLine,
	})
	if err != nil {
		return nil, err
	}
	in.proc = p
	return in, nil
}

func (in *Instance) onLine(line string) {
	in.logf("sing-box: %s", line)
	l := strings.ToLower(line)
	if strings.Contains(l, "fatal") || strings.Contains(l, "error") {
		in.errLine = line
	}
}

// Done is closed when sing-box exits.
func (in *Instance) Done() <-chan struct{} { return in.proc.Done() }

// Err returns the exit error (valid after Done is closed) with the last
// error line sing-box printed.
func (in *Instance) Err() error {
	if err := in.proc.Err(); err != nil {
		return fmt.Errorf("%v (%s)", err, in.errLine)
	}
	return nil
}

// WaitReady waits until the mixed inbound accepts connections.
func (in *Instance) WaitReady(ctx context.Context, port int, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-in.proc.Done():
			return fmt.Errorf("sing-box exited: %v (%s)", in.proc.Err(), in.errLine)
		case <-time.After(300 * time.Millisecond):
		}
		c, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second)
		if err == nil {
			c.Close()
			return nil
		}
	}
	return fmt.Errorf("sing-box did not open port %d in time (%s)", port, in.errLine)
}

// Stop kills sing-box (it removes its routes and TUN adapter on exit).
func (in *Instance) Stop() {
	if in == nil {
		return
	}
	in.proc.Stop(8 * time.Second)
}
