package app

import (
	"fmt"
	"strings"

	qrcode "github.com/skip2/go-qrcode"
)

// qrSVG renders text as a scalable QR code (black modules on a white card with
// a quiet zone), ready to be injected into the page.
func qrSVG(text string) string {
	q, err := qrcode.New(text, qrcode.Medium)
	if err != nil {
		return ""
	}
	bm := q.Bitmap() // includes the 4-module quiet zone
	n := len(bm)
	var path strings.Builder
	for y, row := range bm {
		for x := 0; x < len(row); {
			if !row[x] {
				x++
				continue
			}
			run := 1
			for x+run < len(row) && row[x+run] {
				run++
			}
			fmt.Fprintf(&path, "M%d %dh%dv1h-%dz", x, y, run, run)
			x += run
		}
	}
	return fmt.Sprintf(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges" role="img" aria-label="QR"><rect width="%d" height="%d" fill="#fff"/><path d="%s" fill="#000"/></svg>`,
		n, n, n, n, path.String())
}
