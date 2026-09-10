package tor

import (
	"context"
	"os/exec"
	"strings"
	"testing"
	"time"
)

// TestStartFailureCarriesTorsOwnExplanation drives a real tor into refusing to
// start and checks the error names the reason.
//
// "tor exited during startup: exit status 1" is a dead end for whoever reads
// it: tor has already explained itself and the explanation was being thrown
// away. This is the test that keeps it.
func TestStartFailureCarriesTorsOwnExplanation(t *testing.T) {
	torPath, err := exec.LookPath(exeName("tor"))
	if err != nil {
		t.Skip("no tor binary available")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	geoIP, geoIPv6 := findGeoIP()
	_, startErr := Start(ctx, Options{
		DataDir:   t.TempDir(),
		Transport: TransportDirect,
		Binaries:  Binaries{Tor: torPath, GeoIP: geoIP, GeoIPv6: geoIPv6},
		// An option tor does not know makes it reject the file and exit,
		// which is the failure mode being reproduced.
		ExtraTorrc: map[string]string{"ThisOptionDoesNotExist": "1"},
	})
	if startErr == nil {
		t.Fatal("tor accepted a configuration containing an unknown option")
	}

	msg := startErr.Error()
	if !strings.Contains(msg, "Tor said:") {
		t.Errorf("the error does not include tor's own output:\n%s", msg)
	}
	// Tor names the option it could not parse; that is the actionable part.
	if !strings.Contains(msg, "ThisOptionDoesNotExist") {
		t.Errorf("the error does not name the rejected option, so it is not actionable:\n%s", msg)
	}
}

func TestOutputCaptureKeepsProblemsAndTail(t *testing.T) {
	c := newOutputCapture(nil)

	c.Write([]byte("Jan 01 00:00:00.000 [notice] Tor 0.4.8.10 running\n"))
	c.Write([]byte("Jan 01 00:00:00.000 [notice] Opening Socks listener\n"))
	c.Write([]byte("Jan 01 00:00:00.000 [warn] Something is off\n"))
	// A line split across writes must still be assembled.
	c.Write([]byte("Jan 01 00:00:00.000 [err] Reading config failed"))
	c.Write([]byte(" --- see above\n"))

	problems := c.Problems()
	if len(problems) != 2 {
		t.Fatalf("got %d problem lines, want 2: %v", len(problems), problems)
	}
	if !strings.Contains(problems[1], "Reading config failed --- see above") {
		t.Errorf("a line split across writes was not reassembled: %q", problems[1])
	}

	explain := c.Explain()
	if !strings.Contains(explain, "Something is off") || !strings.Contains(explain, "Reading config failed") {
		t.Errorf("Explain lost a problem line:\n%s", explain)
	}
	// Notices are progress, not failures, and would bury the real cause.
	if strings.Contains(explain, "Opening Socks listener") {
		t.Errorf("Explain should prefer warnings and errors over notices:\n%s", explain)
	}
}

func TestOutputCaptureFallsBackToTheTail(t *testing.T) {
	c := newOutputCapture(nil)
	for i := 0; i < 5; i++ {
		c.Write([]byte("Jan 01 00:00:00.000 [notice] step\n"))
	}
	// With nothing marked as a problem, the last lines are better than
	// nothing at all.
	if c.Explain() == "" {
		t.Error("Explain returned nothing despite output being available")
	}
}

func TestOutputCaptureBoundsWhatItKeeps(t *testing.T) {
	c := newOutputCapture(nil)
	for i := 0; i < recentLines*3; i++ {
		c.Write([]byte("Jan 01 00:00:00.000 [notice] line\n"))
	}
	if got := len(c.Recent()); got > recentLines {
		t.Errorf("kept %d lines, want at most %d", got, recentLines)
	}
}

func TestOutputCaptureFlushesAnUnterminatedLine(t *testing.T) {
	c := newOutputCapture(nil)
	c.Write([]byte("Jan 01 00:00:00.000 [err] died mid-sentence"))
	if len(c.Recent()) != 0 {
		t.Error("an unterminated line should wait for its newline")
	}
	// A process that dies without a trailing newline still has something to
	// say, and it is usually the most important line.
	c.Flush()
	if got := c.Recent(); len(got) != 1 || !strings.Contains(got[0], "died mid-sentence") {
		t.Errorf("Flush did not emit the final line: %v", got)
	}
}
