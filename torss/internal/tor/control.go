package tor

import (
	"bufio"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Control is a minimal Tor control-port client (cookie authentication).
type Control struct {
	mu   sync.Mutex
	conn net.Conn
	r    *bufio.Reader
}

// Dial connects to the control port and authenticates with the cookie file.
func Dial(addr, cookiePath string, timeout time.Duration) (*Control, error) {
	cookie, err := os.ReadFile(cookiePath)
	if err != nil {
		return nil, fmt.Errorf("read control cookie: %w", err)
	}
	conn, err := net.DialTimeout("tcp", addr, timeout)
	if err != nil {
		return nil, err
	}
	c := &Control{conn: conn, r: bufio.NewReader(conn)}
	if _, err := c.cmd("AUTHENTICATE " + hex.EncodeToString(cookie)); err != nil {
		conn.Close()
		return nil, fmt.Errorf("authenticate: %w", err)
	}
	// Tor exits when this connection closes; belt and braces next to the job object.
	_, _ = c.cmd("TAKEOWNERSHIP")
	_, _ = c.cmd("RESETCONF __OwningControllerProcess")
	return c, nil
}

// Close closes the control connection.
func (c *Control) Close() error {
	if c == nil || c.conn == nil {
		return nil
	}
	return c.conn.Close()
}

// cmd sends one command and returns the reply lines (without status codes).
func (c *Control) cmd(line string) ([]string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	_ = c.conn.SetDeadline(time.Now().Add(15 * time.Second))
	if _, err := c.conn.Write([]byte(line + "\r\n")); err != nil {
		return nil, err
	}
	var lines []string
	for {
		raw, err := c.r.ReadString('\n')
		if err != nil {
			return nil, err
		}
		raw = strings.TrimRight(raw, "\r\n")
		if len(raw) < 4 {
			return nil, fmt.Errorf("short control reply %q", raw)
		}
		code, sep, text := raw[:3], raw[3], raw[4:]
		if sep == '+' { // data reply: read until "."
			for {
				l, err := c.r.ReadString('\n')
				if err != nil {
					return nil, err
				}
				l = strings.TrimRight(l, "\r\n")
				if l == "." {
					break
				}
				lines = append(lines, l)
			}
			continue
		}
		lines = append(lines, text)
		if sep == ' ' {
			if code != "250" {
				return lines, fmt.Errorf("tor control: %s %s", code, text)
			}
			return lines, nil
		}
	}
}

// Bootstrap is the parsed status/bootstrap-phase.
type Bootstrap struct {
	Progress int
	Tag      string
	Summary  string
	Warning  string
}

var (
	reProgress = regexp.MustCompile(`PROGRESS=(\d+)`)
	reTag      = regexp.MustCompile(`TAG=(\S+)`)
	reSummary  = regexp.MustCompile(`SUMMARY="([^"]*)"`)
	reWarning  = regexp.MustCompile(`WARNING="([^"]*)"`)
	// log format: "Bootstrapped 45% (requesting_descriptors): Asking for relay descriptors"
	reLog = regexp.MustCompile(`Bootstrapped (\d+)% \(([^)]*)\): (.*)$`)
)

// ParseBootstrap parses a bootstrap status line (control reply or log line).
func ParseBootstrap(s string) (Bootstrap, bool) {
	if m := reLog.FindStringSubmatch(s); m != nil {
		b := Bootstrap{Tag: m[2], Summary: strings.TrimSpace(m[3])}
		b.Progress, _ = strconv.Atoi(m[1])
		return b, true
	}
	m := reProgress.FindStringSubmatch(s)
	if m == nil {
		return Bootstrap{}, false
	}
	b := Bootstrap{}
	b.Progress, _ = strconv.Atoi(m[1])
	if t := reTag.FindStringSubmatch(s); t != nil {
		b.Tag = t[1]
	}
	if t := reSummary.FindStringSubmatch(s); t != nil {
		b.Summary = t[1]
	}
	if t := reWarning.FindStringSubmatch(s); t != nil {
		b.Warning = t[1]
	}
	return b, true
}

// BootstrapStatus asks Tor for its bootstrap phase.
func (c *Control) BootstrapStatus() (Bootstrap, error) {
	lines, err := c.cmd("GETINFO status/bootstrap-phase")
	if err != nil {
		return Bootstrap{}, err
	}
	for _, l := range lines {
		if b, ok := ParseBootstrap(l); ok {
			return b, nil
		}
	}
	return Bootstrap{}, errors.New("no bootstrap info in reply")
}

// NewIdentity asks Tor for fresh circuits.
func (c *Control) NewIdentity() error {
	_, err := c.cmd("SIGNAL NEWNYM")
	return err
}

// Shutdown asks Tor to exit cleanly.
func (c *Control) Shutdown() error {
	_, err := c.cmd("SIGNAL SHUTDOWN")
	return err
}
