package api

import (
	"bytes"
	"container/list"
	"context"
	"fmt"
	"image"
	"image/jpeg"
	"io"
	"net/http"
	"strconv"
	"sync"

	// Decoders for the formats phones and cameras produce.
	_ "image/gif"
	_ "image/png"

	_ "golang.org/x/image/bmp"
	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"github.com/parfentsevandrey-blip/test/svoi/internal/files"
)

const maxThumbSource = 30 << 20 // do not decode images larger than this

// thumbCache is a small LRU of encoded thumbnails.
type thumbCache struct {
	mu    sync.Mutex
	items map[string]*list.Element
	order *list.List
	bytes int
}

type thumbEntry struct {
	key  string
	data []byte
}

const thumbCacheBytes = 32 << 20

func newThumbCache() *thumbCache {
	return &thumbCache{items: map[string]*list.Element{}, order: list.New()}
}

func (c *thumbCache) get(key string) ([]byte, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if e, ok := c.items[key]; ok {
		c.order.MoveToFront(e)
		return e.Value.(*thumbEntry).data, true
	}
	return nil, false
}

func (c *thumbCache) put(key string, data []byte) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if _, ok := c.items[key]; ok {
		return
	}
	c.items[key] = c.order.PushFront(&thumbEntry{key, data})
	c.bytes += len(data)
	for c.bytes > thumbCacheBytes && c.order.Len() > 1 {
		last := c.order.Back()
		ent := last.Value.(*thumbEntry)
		c.order.Remove(last)
		delete(c.items, ent.key)
		c.bytes -= len(ent.data)
	}
}

var (
	thumbs   = newThumbCache()
	thumbSem = make(chan struct{}, 2) // decoding is memory hungry: at most two at a time
)

// handleThumb returns a JPEG thumbnail of an image in a share (local or remote).
func (s *Server) handleThumb(w http.ResponseWriter, r *http.Request) {
	id, err := s.peerID(r, "id")
	if err != nil {
		writeError(w, err)
		return
	}
	q := r.URL.Query()
	width, _ := strconv.Atoi(q.Get("w"))
	if width < 32 {
		width = 256
	}
	if width > 1024 {
		width = 1024
	}
	fm := s.app.Files()
	mime := files.MimeFor(q.Get("path"))
	switch mime {
	case "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp":
	default:
		writeError(w, errCode("notfound", "no thumbnail for this type"))
		return
	}

	var rc io.ReadCloser
	var meta files.FileMeta
	if fm.IsLocal(id) {
		f, m, err := fm.OpenRead(files.Actor{}, q.Get("share"), q.Get("path"))
		if err != nil {
			writeError(w, err)
			return
		}
		rc, meta = f, m
	} else {
		ctx, cancel := context.WithCancel(r.Context())
		defer cancel()
		rr, err := fm.RemoteOpen(ctx, id, q.Get("share"), q.Get("path"), 0, 0)
		if err != nil {
			writeError(w, err)
			return
		}
		rc, meta = rr, rr.Meta
	}
	defer rc.Close()
	if meta.Size > maxThumbSource {
		writeError(w, errCode("notfound", "the image is too large for a thumbnail"))
		return
	}
	key := fmt.Sprintf("%s|%s|%s|%d|%d|%d", id, q.Get("share"), q.Get("path"), meta.MTime, meta.Size, width)
	if data, ok := thumbs.get(key); ok {
		sendThumb(w, data)
		return
	}
	select {
	case thumbSem <- struct{}{}:
		defer func() { <-thumbSem }()
	case <-r.Context().Done():
		return
	}
	raw, err := io.ReadAll(io.LimitReader(rc, maxThumbSource+1))
	if err != nil {
		writeError(w, err)
		return
	}
	img, _, err := image.Decode(bytes.NewReader(raw))
	if err != nil {
		writeError(w, errCode("notfound", "cannot decode this image"))
		return
	}
	if mime == "image/jpeg" {
		img = applyOrientation(img, exifOrientation(raw))
	}
	b := img.Bounds()
	if b.Dx() > width {
		h := b.Dy() * width / b.Dx()
		if h < 1 {
			h = 1
		}
		dst := image.NewRGBA(image.Rect(0, 0, width, h))
		draw.CatmullRom.Scale(dst, dst.Bounds(), img, b, draw.Over, nil)
		img = dst
	}
	var out bytes.Buffer
	if err := jpeg.Encode(&out, img, &jpeg.Options{Quality: 78}); err != nil {
		writeError(w, err)
		return
	}
	thumbs.put(key, out.Bytes())
	sendThumb(w, out.Bytes())
}

func sendThumb(w http.ResponseWriter, data []byte) {
	h := w.Header()
	h.Set("Content-Type", "image/jpeg")
	h.Set("Cache-Control", "private, max-age=3600")
	h.Set("Content-Length", strconv.Itoa(len(data)))
	_, _ = w.Write(data)
}

// exifOrientation extracts the EXIF orientation tag (1..8) from a JPEG, or 1.
func exifOrientation(b []byte) int {
	if len(b) < 4 || b[0] != 0xFF || b[1] != 0xD8 {
		return 1
	}
	i := 2
	for i+4 < len(b) {
		if b[i] != 0xFF {
			return 1
		}
		marker := b[i+1]
		if marker == 0xDA { // start of scan: no more metadata
			return 1
		}
		size := int(b[i+2])<<8 | int(b[i+3])
		if marker == 0xE1 && i+10 < len(b) && string(b[i+4:i+10]) == "Exif\x00\x00" {
			tiff := b[i+10:]
			if len(tiff) < 8 {
				return 1
			}
			var u16 func([]byte) int
			var u32 func([]byte) int
			switch string(tiff[:2]) {
			case "II":
				u16 = func(p []byte) int { return int(p[0]) | int(p[1])<<8 }
				u32 = func(p []byte) int { return int(p[0]) | int(p[1])<<8 | int(p[2])<<16 | int(p[3])<<24 }
			case "MM":
				u16 = func(p []byte) int { return int(p[0])<<8 | int(p[1]) }
				u32 = func(p []byte) int { return int(p[0])<<24 | int(p[1])<<16 | int(p[2])<<8 | int(p[3]) }
			default:
				return 1
			}
			off := u32(tiff[4:8])
			if off < 8 || off+2 > len(tiff) {
				return 1
			}
			n := u16(tiff[off:])
			for k := 0; k < n; k++ {
				e := off + 2 + k*12
				if e+12 > len(tiff) {
					return 1
				}
				if u16(tiff[e:]) == 0x0112 {
					if o := u16(tiff[e+8:]); o >= 1 && o <= 8 {
						return o
					}
					return 1
				}
			}
			return 1
		}
		i += 2 + size
	}
	return 1
}

// applyOrientation rotates/flips img according to an EXIF orientation value.
func applyOrientation(src image.Image, o int) image.Image {
	if o <= 1 || o > 8 {
		return src
	}
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	swap := o >= 5
	dw, dh := w, h
	if swap {
		dw, dh = h, w
	}
	dst := image.NewRGBA(image.Rect(0, 0, dw, dh))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			var nx, ny int
			switch o {
			case 2:
				nx, ny = w-1-x, y
			case 3:
				nx, ny = w-1-x, h-1-y
			case 4:
				nx, ny = x, h-1-y
			case 5:
				nx, ny = y, x
			case 6:
				nx, ny = h-1-y, x
			case 7:
				nx, ny = h-1-y, w-1-x
			case 8:
				nx, ny = y, w-1-x
			}
			dst.Set(nx, ny, src.At(b.Min.X+x, b.Min.Y+y))
		}
	}
	return dst
}
