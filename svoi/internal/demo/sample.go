package demo

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"math"
	"math/rand"
	"os"
	"path/filepath"
)

// Procedurally generated sample files so the demo has something to browse,
// preview and play without shipping any binary assets.

func writeFile(path string, data []byte) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o644)
}

// scenePNG paints a soft "landscape": a sky gradient, a sun and layered hills.
func scenePNG(w, h int, seed int64, warm bool) []byte {
	rng := rand.New(rand.NewSource(seed))
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	top, bottom := color.RGBA{30, 60, 120, 255}, color.RGBA{250, 190, 120, 255}
	if !warm {
		top, bottom = color.RGBA{20, 90, 140, 255}, color.RGBA{200, 230, 240, 255}
	}
	lerp := func(a, b uint8, t float64) uint8 { return uint8(float64(a)*(1-t) + float64(b)*t) }
	for y := 0; y < h; y++ {
		t := float64(y) / float64(h)
		c := color.RGBA{lerp(top.R, bottom.R, t), lerp(top.G, bottom.G, t), lerp(top.B, bottom.B, t), 255}
		for x := 0; x < w; x++ {
			img.SetRGBA(x, y, c)
		}
	}
	sx, sy, sr := float64(w)*(0.2+0.6*rng.Float64()), float64(h)*(0.25+0.15*rng.Float64()), float64(h)*0.09
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			d := math.Hypot(float64(x)-sx, float64(y)-sy)
			if d < sr*3 {
				a := math.Max(0, 1-d/(sr*3))
				c := img.RGBAAt(x, y)
				glow := 255 * a * a
				img.SetRGBA(x, y, color.RGBA{clamp(float64(c.R) + glow), clamp(float64(c.G) + glow*0.85), clamp(float64(c.B) + glow*0.5), 255})
			}
			if d < sr {
				img.SetRGBA(x, y, color.RGBA{255, 244, 214, 255})
			}
		}
	}
	for layer := 0; layer < 3; layer++ {
		base := float64(h) * (0.62 + 0.12*float64(layer))
		amp := float64(h) * (0.07 - 0.015*float64(layer))
		ph1, ph2 := rng.Float64()*6, rng.Float64()*6
		shade := uint8(70 - layer*22)
		for x := 0; x < w; x++ {
			ridge := base - amp*(math.Sin(float64(x)/float64(w)*5+ph1)+0.5*math.Sin(float64(x)/float64(w)*11+ph2))
			for y := int(ridge); y < h; y++ {
				if y >= 0 {
					g := shade + uint8(float64(y-int(ridge))/float64(h)*40)
					img.SetRGBA(x, y, color.RGBA{shade / 2, g, shade / 2, 255})
				}
			}
		}
	}
	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	return buf.Bytes()
}

func clamp(v float64) uint8 {
	if v > 255 {
		return 255
	}
	return uint8(v)
}

// wavTone renders a short arpeggio as a 16-bit mono WAV.
func wavTone(seconds float64, notes []float64) []byte {
	const rate = 22050
	n := int(seconds * rate)
	data := make([]byte, 0, n*2)
	per := n / len(notes)
	for i := 0; i < n; i++ {
		f := notes[min(i/per, len(notes)-1)]
		t := float64(i) / rate
		env := math.Exp(-3 * math.Mod(float64(i), float64(per)) / float64(per))
		v := 0.45 * env * (math.Sin(2*math.Pi*f*t) + 0.3*math.Sin(4*math.Pi*f*t))
		s := int16(v * 32000)
		data = binary.LittleEndian.AppendUint16(data, uint16(s))
	}
	var b bytes.Buffer
	b.WriteString("RIFF")
	binary.Write(&b, binary.LittleEndian, uint32(36+len(data)))
	b.WriteString("WAVEfmt ")
	binary.Write(&b, binary.LittleEndian, uint32(16))
	binary.Write(&b, binary.LittleEndian, uint16(1))
	binary.Write(&b, binary.LittleEndian, uint16(1))
	binary.Write(&b, binary.LittleEndian, uint32(rate))
	binary.Write(&b, binary.LittleEndian, uint32(rate*2))
	binary.Write(&b, binary.LittleEndian, uint16(2))
	binary.Write(&b, binary.LittleEndian, uint16(16))
	b.WriteString("data")
	binary.Write(&b, binary.LittleEndian, uint32(len(data)))
	b.Write(data)
	return b.Bytes()
}

// tinyPDF builds a valid one-page PDF with a line of text.
func tinyPDF(text string) []byte {
	var b bytes.Buffer
	offs := []int{}
	obj := func(body string) {
		offs = append(offs, b.Len())
		fmt.Fprintf(&b, "%d 0 obj\n%s\nendobj\n", len(offs), body)
	}
	b.WriteString("%PDF-1.4\n")
	obj("<< /Type /Catalog /Pages 2 0 R >>")
	obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
	obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>")
	content := fmt.Sprintf("BT /F1 22 Tf 30 110 Td (%s) Tj ET", text)
	obj(fmt.Sprintf("<< /Length %d >>\nstream\n%s\nendstream", len(content), content))
	obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
	xref := b.Len()
	fmt.Fprintf(&b, "xref\n0 %d\n0000000000 65535 f \n", len(offs)+1)
	for _, o := range offs {
		fmt.Fprintf(&b, "%010d 00000 n \n", o)
	}
	fmt.Fprintf(&b, "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n", len(offs)+1, xref)
	return b.Bytes()
}

// seedNAS fills the shared folders of the demo NAS.
func seedNAS(root string) (photos, music, docs string, err error) {
	photos = filepath.Join(root, "Фото")
	music = filepath.Join(root, "Музыка")
	docs = filepath.Join(root, "Документы")
	folders := map[string][]string{
		"Отпуск 2024": {"Море", "Скалы", "Закат", "Порт", "Утро", "Набережная"},
		"Дача":        {"Яблоня", "Веранда", "Огород", "Костёр"},
	}
	seed := int64(1)
	for folder, names := range folders {
		for i, n := range names {
			seed++
			name := fmt.Sprintf("IMG_%04d %s.png", 100+int(seed)*7, n)
			if err = writeFile(filepath.Join(photos, folder, name), scenePNG(960, 640, seed, i%2 == 0)); err != nil {
				return
			}
		}
	}
	if err = writeFile(filepath.Join(photos, "README.txt"), []byte("Демо-фотографии, нарисованные программой.\n")); err != nil {
		return
	}
	tracks := map[string][]float64{
		"Утренний бриз.wav":  {392, 494, 587, 784, 587, 494},
		"Дождь за окном.wav": {330, 392, 494, 392, 330, 262},
		"Вечерний чай.wav":   {262, 330, 392, 523, 392, 330},
	}
	for name, notes := range tracks {
		if err = writeFile(filepath.Join(music, "Плейлист", name), wavTone(6, notes)); err != nil {
			return
		}
	}
	if err = writeFile(filepath.Join(docs, "Заметки.txt"), []byte("Купить:\n- молоко\n- хлеб\n- кофе\n\nНе забыть оплатить интернет до 5-го числа.\n")); err != nil {
		return
	}
	if err = writeFile(filepath.Join(docs, "Бюджет.csv"), []byte("месяц,доход,расход\nянварь,120000,84000\nфевраль,120000,79500\nмарт,125000,91200\n")); err != nil {
		return
	}
	err = writeFile(filepath.Join(docs, "Договор.pdf"), tinyPDF("themesh demo document"))
	return
}
