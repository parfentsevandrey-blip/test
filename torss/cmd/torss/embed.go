package main

import "embed"

// bundled holds gzip-compressed helper binaries produced by `go run ./tools/pack`
// (bundle/manifest.json + *.gz). When only README.txt is present the build is
// "lite" and expects bin/ next to the executable.
//
//go:embed bundle
var bundled embed.FS
