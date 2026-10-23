import Foundation

/// Субтитры Blu-ray (HDMV Presentation Graphics, «PGS», в Matroska — S_HDMV/PGS): картинки с палитрой,
/// сжатые RLE. Каждый набор сегментов (PCS … END) — новое состояние экрана с момента своего времени.
nonisolated struct PGSImage: Sendable {
    /// Время показа, секунды; до следующего набора (или до `end`, если он известен).
    var start: Double
    var end: Double?
    /// Размер «холста» — кадра видео, под который рисовали субтитры (обычно 1920×1080).
    var canvasWidth: Int
    var canvasHeight: Int
    /// Картинки, из которых состоит экран (пусто — экран очищается).
    var objects: [Bitmap]

    nonisolated struct Bitmap: Sendable {
        var x: Int
        var y: Int
        var width: Int
        var height: Int
        /// RGBA, 8 бит на канал, с предумноженной прозрачностью.
        var rgba: [UInt8]
    }

    var isEmpty: Bool { objects.isEmpty }
}

nonisolated final class PGSDecoder {
    private struct Object {
        var width = 0
        var height = 0
        var data: [UInt8] = []
        var expected = 0
    }

    private struct Composition {
        var objectID: Int
        var x: Int
        var y: Int
        var crop: (x: Int, y: Int, width: Int, height: Int)?
    }

    private var palettes: [Int: [UInt32]] = [:]
    private var objects: [Int: Object] = [:]
    private var width = 1920
    private var height = 1080
    private var compositions: [Composition] = []
    private var paletteID = 0
    private var pendingTime: Double?

    init() {}

    /// Разбирает один блок Matroska (один или несколько сегментов). Возвращает готовые экраны.
    func decode(_ data: [UInt8], time: Double) -> [PGSImage] {
        var out: [PGSImage] = []
        var i = 0
        while i + 3 <= data.count {
            let type = data[i]
            let size = Int(data[i + 1]) << 8 | Int(data[i + 2])
            let start = i + 3
            let end = min(data.count, start + size)
            let segment = Array(data[start..<end])
            switch type {
            case 0x16: composition(segment, time: time)
            case 0x14: palette(segment)
            case 0x15: object(segment)
            case 0x80:
                if let image = render() { out.append(image) }
            default: break // 0x17 — окна: положение берём из композиции
            }
            i = end
        }
        return out
    }

    private func u16(_ s: [UInt8], _ o: Int) -> Int { o + 1 < s.count ? Int(s[o]) << 8 | Int(s[o + 1]) : 0 }

    private func composition(_ s: [UInt8], time: Double) {
        guard s.count >= 11 else { return }
        width = max(1, u16(s, 0))
        height = max(1, u16(s, 2))
        let state = s[7]
        paletteID = Int(s[9])
        let count = Int(s[10])
        // Начало эпохи: старые картинки и палитры больше не нужны.
        if state & 0x80 != 0 {
            objects.removeAll()
            palettes.removeAll()
        }
        compositions = []
        var o = 11
        for _ in 0..<count {
            guard o + 8 <= s.count else { break }
            var c = Composition(objectID: u16(s, o), x: u16(s, o + 4), y: u16(s, o + 6), crop: nil)
            let cropped = s[o + 3] & 0x40 != 0
            o += 8
            if cropped, o + 8 <= s.count {
                c.crop = (u16(s, o), u16(s, o + 2), u16(s, o + 4), u16(s, o + 6))
                o += 8
            }
            compositions.append(c)
        }
        pendingTime = time
    }

    private func palette(_ s: [UInt8]) {
        guard s.count >= 2 else { return }
        let id = Int(s[0])
        var table = palettes[id] ?? [UInt32](repeating: 0, count: 256)
        var o = 2
        while o + 5 <= s.count {
            table[Int(s[o])] = Self.rgba(y: s[o + 1], cr: s[o + 2], cb: s[o + 3], alpha: s[o + 4], hd: width > 720)
            o += 5
        }
        palettes[id] = table
    }

    private func object(_ s: [UInt8]) {
        guard s.count >= 4 else { return }
        let id = u16(s, 0)
        let sequence = s[3]
        if sequence & 0x80 != 0 {
            // Первый фрагмент: длина данных (3 байта, включая 4 байта размеров), ширина, высота.
            guard s.count >= 11 else { return }
            let length = Int(s[4]) << 16 | Int(s[5]) << 8 | Int(s[6])
            var object = Object(width: u16(s, 7), height: u16(s, 9))
            object.expected = max(0, length - 4)
            object.data = Array(s[11...])
            objects[id] = object
        } else if var object = objects[id] {
            object.data += s[4...]
            objects[id] = object
        }
    }

    private func render() -> PGSImage? {
        guard let time = pendingTime else { return nil }
        pendingTime = nil
        let table = palettes[paletteID] ?? [UInt32](repeating: 0, count: 256)
        var bitmaps: [PGSImage.Bitmap] = []
        for c in compositions {
            guard let object = objects[c.objectID], object.width > 0, object.height > 0,
                  let indices = Self.runLength(object.data, width: object.width, height: object.height) else { continue }
            let crop = c.crop ?? (0, 0, object.width, object.height)
            let cw = max(0, min(crop.width, object.width - crop.x)), ch = max(0, min(crop.height, object.height - crop.y))
            guard cw > 0, ch > 0 else { continue }
            var rgba = [UInt8](repeating: 0, count: cw * ch * 4)
            for y in 0..<ch {
                let row = (crop.y + y) * object.width + crop.x
                for x in 0..<cw {
                    let color = table[Int(indices[row + x])]
                    let o = (y * cw + x) * 4
                    rgba[o] = UInt8(color >> 24)
                    rgba[o + 1] = UInt8((color >> 16) & 0xFF)
                    rgba[o + 2] = UInt8((color >> 8) & 0xFF)
                    rgba[o + 3] = UInt8(color & 0xFF)
                }
            }
            bitmaps.append(.init(x: c.x, y: c.y, width: cw, height: ch, rgba: rgba))
        }
        return PGSImage(start: time, end: nil, canvasWidth: width, canvasHeight: height, objects: bitmaps)
    }

    /// RLE PGS: ненулевой байт — одна точка; 0x00 и флаг — серия точек или конец строки.
    static func runLength(_ data: [UInt8], width: Int, height: Int) -> [UInt8]? {
        var out = [UInt8](repeating: 0, count: width * height)
        var x = 0, y = 0, i = 0
        @inline(__always) func put(_ color: UInt8, _ count: Int) {
            guard y < height else { return }
            let n = min(count, width - x)
            if n > 0, color != 0 {
                let base = y * width + x
                for k in 0..<n { out[base + k] = color }
            }
            x += max(n, 0)
        }
        while i < data.count, y < height {
            let b = data[i]; i += 1
            if b != 0 { put(b, 1); continue }
            guard i < data.count else { break }
            let flag = data[i]; i += 1
            if flag == 0 {
                x = 0
                y += 1
                continue
            }
            let long = flag & 0x40 != 0
            let colored = flag & 0x80 != 0
            var count = Int(flag & 0x3F)
            if long {
                guard i < data.count else { break }
                count = count << 8 | Int(data[i]); i += 1
            }
            var color: UInt8 = 0
            if colored {
                guard i < data.count else { break }
                color = data[i]; i += 1
            }
            put(color, count)
        }
        return out
    }

    /// YCbCr (BT.709 для HD, BT.601 для SD; ограниченный диапазон) → RGBA с предумноженной прозрачностью.
    static func rgba(y: UInt8, cr: UInt8, cb: UInt8, alpha: UInt8, hd: Bool) -> UInt32 {
        let yy = (Double(y) - 16) * 255 / 219
        let pb = (Double(cb) - 128) * 255 / 224, pr = (Double(cr) - 128) * 255 / 224
        let (kr, kb) = hd ? (0.2126, 0.0722) : (0.299, 0.114)
        let kg = 1 - kr - kb
        let r = yy + 2 * (1 - kr) * pr
        let b = yy + 2 * (1 - kb) * pb
        let g = (yy - kr * r - kb * b) / kg
        let a = Double(alpha) / 255
        func c(_ v: Double) -> UInt32 { UInt32(max(0, min(255, (v * a).rounded()))) }
        return c(r) << 24 | c(g) << 16 | c(b) << 8 | UInt32(alpha)
    }
}
