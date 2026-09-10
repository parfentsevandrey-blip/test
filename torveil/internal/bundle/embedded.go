//go:build bundled

package bundle

import (
	"bytes"
	_ "embed"
	"io"
)

// runtimeArchive is the Tor runtime, produced and signature-verified by
// build/fetch-assets.sh. It is not committed: run that script to reproduce it.
//
//go:embed assets/runtime.tar.gz
var runtimeArchive []byte

func archive() []byte { return runtimeArchive }

func newByteReader(b []byte) io.Reader { return bytes.NewReader(b) }
