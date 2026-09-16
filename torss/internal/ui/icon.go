package ui

import (
	"bytes"
	"encoding/binary"
	"image"
	"image/color"
	"image/draw"
	"math"
)

// icon renders a filled circle of the given colour as a Windows .ico with
// 16x16 and 32x32 entries, so we ship no binary assets.
func icon(c color.RGBA) []byte {
	sizes := []int{16, 32}
	var images [][]byte
	for _, s := range sizes {
		images = append(images, bmpEntry(circle(s, c)))
	}
	var b bytes.Buffer
	// ICONDIR
	binary.Write(&b, binary.LittleEndian, uint16(0))
	binary.Write(&b, binary.LittleEndian, uint16(1))
	binary.Write(&b, binary.LittleEndian, uint16(len(sizes)))
	offset := 6 + 16*len(sizes)
	for i, s := range sizes {
		// ICONDIRENTRY
		b.WriteByte(byte(s))                              // width (256 -> 0, not used here)
		b.WriteByte(byte(s))                              // height
		b.WriteByte(0)                                    // colour count
		b.WriteByte(0)                                    // reserved
		binary.Write(&b, binary.LittleEndian, uint16(1))  // planes
		binary.Write(&b, binary.LittleEndian, uint16(32)) // bpp
		binary.Write(&b, binary.LittleEndian, uint32(len(images[i])))
		binary.Write(&b, binary.LittleEndian, uint32(offset))
		offset += len(images[i])
	}
	for _, img := range images {
		b.Write(img)
	}
	return b.Bytes()
}

func circle(size int, c color.RGBA) *image.RGBA {
	img := image.NewRGBA(image.Rect(0, 0, size, size))
	draw.Draw(img, img.Bounds(), image.Transparent, image.Point{}, draw.Src)
	cx, cy := float64(size)/2-0.5, float64(size)/2-0.5
	r := float64(size)/2 - 1
	dark := color.RGBA{R: c.R / 2, G: c.G / 2, B: c.B / 2, A: 255}
	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			d := math.Hypot(float64(x)-cx, float64(y)-cy)
			switch {
			case d <= r-1.2:
				img.SetRGBA(x, y, c)
			case d <= r:
				img.SetRGBA(x, y, dark)
			case d <= r+0.7: // soft edge
				a := uint8(255 * (r + 0.7 - d) / 0.7)
				img.SetRGBA(x, y, color.RGBA{R: dark.R, G: dark.G, B: dark.B, A: a})
			}
		}
	}
	return img
}

// bmpEntry encodes an RGBA image as the BITMAPINFOHEADER + XOR + AND data
// block used inside .ico files (bottom-up rows, BGRA, 32 bpp).
func bmpEntry(img *image.RGBA) []byte {
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	var b bytes.Buffer
	binary.Write(&b, binary.LittleEndian, uint32(40))    // biSize
	binary.Write(&b, binary.LittleEndian, int32(w))      // biWidth
	binary.Write(&b, binary.LittleEndian, int32(h*2))    // biHeight (XOR + AND)
	binary.Write(&b, binary.LittleEndian, uint16(1))     // biPlanes
	binary.Write(&b, binary.LittleEndian, uint16(32))    // biBitCount
	binary.Write(&b, binary.LittleEndian, uint32(0))     // biCompression
	binary.Write(&b, binary.LittleEndian, uint32(w*h*4)) // biSizeImage
	binary.Write(&b, binary.LittleEndian, int32(0))      // biXPelsPerMeter
	binary.Write(&b, binary.LittleEndian, int32(0))      // biYPelsPerMeter
	binary.Write(&b, binary.LittleEndian, uint32(0))     // biClrUsed
	binary.Write(&b, binary.LittleEndian, uint32(0))     // biClrImportant
	for y := h - 1; y >= 0; y-- {
		for x := 0; x < w; x++ {
			c := img.RGBAAt(x, y)
			b.Write([]byte{c.B, c.G, c.R, c.A})
		}
	}
	// AND mask: 1 bpp, rows padded to 4 bytes; all zero (alpha does the job).
	rowBytes := ((w + 31) / 32) * 4
	b.Write(make([]byte, rowBytes*h))
	return b.Bytes()
}

var (
	iconGrey   = icon(color.RGBA{R: 140, G: 140, B: 140, A: 255})
	iconYellow = icon(color.RGBA{R: 240, G: 180, B: 20, A: 255})
	iconGreen  = icon(color.RGBA{R: 40, G: 170, B: 70, A: 255})
	iconRed    = icon(color.RGBA{R: 200, G: 50, B: 50, A: 255})
)
