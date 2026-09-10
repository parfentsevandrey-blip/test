//go:build !bundled

package bundle

import (
	"bytes"
	"io"
)

// archive returns nothing in a build made without the "bundled" tag, which is
// what a plain "go build" produces when the runtime archive has not been
// fetched. TorVeil then falls back to looking for a Tor installed on the
// machine, exactly as it did before it carried one.
func archive() []byte { return nil }

func newByteReader(b []byte) io.Reader { return bytes.NewReader(b) }
