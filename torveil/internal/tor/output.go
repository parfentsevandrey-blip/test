package tor

import (
	"bytes"
	"strings"
	"sync"
)

// recentLines is how much of Tor's output is kept for an error message. Tor
// explains a fatal startup problem in its last few lines, so this is generous.
const recentLines = 60

// outputCapture receives Tor's stdout and stderr.
//
// It is an io.Writer rather than a pipe on purpose. exec.Cmd.Wait closes a
// StdoutPipe as soon as the process exits, which races with whoever is still
// reading it and truncates exactly the last lines — the ones explaining why
// Tor is exiting. Handed a Writer, Wait instead waits for the copy to finish,
// so the output is complete before the exit status is known.
type outputCapture struct {
	log LogFunc

	mu      sync.Mutex
	partial bytes.Buffer
	recent  []string
}

func newOutputCapture(log LogFunc) *outputCapture {
	return &outputCapture{log: log, recent: make([]string, 0, recentLines)}
}

func (c *outputCapture) Write(p []byte) (int, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.partial.Write(p)
	for {
		line, err := c.partial.ReadString('\n')
		if err != nil {
			// An incomplete line; keep it until the rest arrives.
			c.partial.Reset()
			c.partial.WriteString(line)
			break
		}
		c.emit(strings.TrimRight(line, "\r\n"))
	}
	return len(p), nil
}

// Flush emits whatever is left when the process exits without a final newline.
func (c *outputCapture) Flush() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if line := strings.TrimRight(c.partial.String(), "\r\n"); line != "" {
		c.emit(line)
	}
	c.partial.Reset()
}

// emit records a line and forwards it. The caller holds c.mu.
func (c *outputCapture) emit(line string) {
	line = strings.TrimSpace(line)
	if line == "" {
		return
	}

	if len(c.recent) == cap(c.recent) {
		copy(c.recent, c.recent[1:])
		c.recent = c.recent[:len(c.recent)-1]
	}
	c.recent = append(c.recent, line)

	if c.log != nil {
		c.log(severityOf(line), "tor: "+line)
	}
}

// Recent returns the last lines Tor produced.
func (c *outputCapture) Recent() []string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return append([]string(nil), c.recent...)
}

// Problems returns only the lines Tor marked as warnings or errors, which is
// what belongs in a failure message: its notices are mostly progress.
func (c *outputCapture) Problems() []string {
	var out []string
	for _, line := range c.Recent() {
		if severityOf(line) != "info" {
			out = append(out, line)
		}
	}
	return out
}

// Explain renders Tor's own account of a failure, preferring its complaints
// and falling back to the tail of its output when it did not make any.
func (c *outputCapture) Explain() string {
	lines := c.Problems()
	if len(lines) == 0 {
		lines = c.Recent()
		if len(lines) > 8 {
			lines = lines[len(lines)-8:]
		}
	}
	if len(lines) == 0 {
		return ""
	}
	return "    " + strings.Join(lines, "\n    ")
}

// severityOf maps a Tor log line onto our levels.
func severityOf(line string) string {
	switch {
	case strings.Contains(line, "[err]"):
		return "error"
	case strings.Contains(line, "[warn]"):
		return "warn"
	default:
		return "info"
	}
}
