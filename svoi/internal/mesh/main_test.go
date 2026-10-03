package mesh

import (
	"os"
	"testing"
)

func TestMain(m *testing.M) {
	// quic-go warns that it cannot tune buffers of a non-UDPConn; magic sizes the
	// real socket itself, so the warning is noise.
	os.Setenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING", "true")
	os.Exit(m.Run())
}
