// Package web embeds the single-page UI into the binary.
package web

import (
	"embed"
	"io/fs"
)

//go:embed all:ui
var uiFS embed.FS

// UI returns the UI files rooted at index.html.
func UI() fs.FS {
	sub, err := fs.Sub(uiFS, "ui")
	if err != nil {
		panic(err)
	}
	return sub
}
