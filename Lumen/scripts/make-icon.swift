// Генератор иконки Lumen.
//   swift make-icon.swift <out-dir>
// Создаёт:
//   <out-dir>/AppIcon.iconset/*.png  — классическая иконка для .icns
//   <out-dir>/AppIcon.icon/          — слои для Icon Composer: macOS 26+ сама
//                                       рисует из них «жидкое стекло» и тёмный/тонированный режимы.
import AppKit

let out = URL(fileURLWithPath: CommandLine.arguments[1])
let fm = FileManager.default
let iconset = out.appendingPathComponent("AppIcon.iconset")
let composer = out.appendingPathComponent("AppIcon.icon")
let assets = composer.appendingPathComponent("Assets")
try? fm.removeItem(at: iconset)
try? fm.removeItem(at: composer)
try fm.createDirectory(at: iconset, withIntermediateDirectories: true)
try fm.createDirectory(at: assets, withIntermediateDirectories: true)

let indigo = NSColor(srgbRed: 0.16, green: 0.10, blue: 0.48, alpha: 1)
let violet = NSColor(srgbRed: 0.45, green: 0.18, blue: 0.78, alpha: 1)
let magenta = NSColor(srgbRed: 0.93, green: 0.28, blue: 0.58, alpha: 1)
let amber = NSColor(srgbRed: 1.00, green: 0.62, blue: 0.28, alpha: 1)

func bitmap(_ px: Int, _ draw: (CGContext, CGFloat) -> Void) -> Data {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: px, pixelsHigh: px, bitsPerSample: 8,
                               samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    let ctx = NSGraphicsContext(bitmapImageRep: rep)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = ctx
    draw(ctx.cgContext, CGFloat(px))
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

/// Треугольник «play» со скруглёнными углами, оптически смещённый вправо.
func playPath(center: CGPoint, size: CGFloat) -> CGPath {
    let r = size * 0.5
    let a = CGPoint(x: center.x - r * 0.62, y: center.y + r * 0.86)
    let b = CGPoint(x: center.x - r * 0.62, y: center.y - r * 0.86)
    let c = CGPoint(x: center.x + r * 0.98, y: center.y)
    let path = CGMutablePath()
    let corner = size * 0.09
    path.move(to: CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2))
    path.addArc(tangent1End: b, tangent2End: c, radius: corner)
    path.addArc(tangent1End: c, tangent2End: a, radius: corner)
    path.addArc(tangent1End: a, tangent2End: b, radius: corner)
    path.closeSubpath()
    return path
}

func gradient(_ colors: [NSColor]) -> CGGradient {
    CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: nil)!
}

/// Полная иконка: сквиркл с градиентом, стеклянная линза и светящийся треугольник.
func fullIcon(_ ctx: CGContext, _ s: CGFloat) {
    let inset = s * 0.1
    let rect = CGRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)
    let squircle = CGPath(roundedRect: rect, cornerWidth: rect.width * 0.225, cornerHeight: rect.width * 0.225, transform: nil)

    // тень под иконкой
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addPath(squircle); ctx.setFillColor(indigo.cgColor); ctx.fillPath()
    ctx.restoreGState()

    ctx.saveGState()
    ctx.addPath(squircle); ctx.clip()
    ctx.drawLinearGradient(gradient([indigo, violet, magenta, amber]),
                           start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    // мягкие «огни» за стеклом
    for (color, x, y, r) in [(amber, 0.78, 0.25, 0.42), (NSColor.cyan, 0.2, 0.8, 0.38)] as [(NSColor, CGFloat, CGFloat, CGFloat)] {
        let c = CGPoint(x: rect.minX + rect.width * x, y: rect.minY + rect.height * y)
        ctx.drawRadialGradient(gradient([color.withAlphaComponent(0.55), color.withAlphaComponent(0)]),
                               startCenter: c, startRadius: 0, endCenter: c, endRadius: rect.width * r, options: [])
    }

    // стеклянная линза
    let center = CGPoint(x: rect.midX, y: rect.midY)
    let lensR = rect.width * 0.33
    let lens = CGRect(x: center.x - lensR, y: center.y - lensR, width: lensR * 2, height: lensR * 2)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.015), blur: s * 0.05, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addEllipse(in: lens); ctx.setFillColor(NSColor.white.withAlphaComponent(0.16).cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.saveGState()
    ctx.addEllipse(in: lens); ctx.clip()
    ctx.drawLinearGradient(gradient([NSColor.white.withAlphaComponent(0.42), NSColor.white.withAlphaComponent(0.04)]),
                           start: CGPoint(x: lens.minX, y: lens.maxY), end: CGPoint(x: lens.maxX, y: lens.minY), options: [])
    ctx.restoreGState()
    // кромка и блик
    ctx.addEllipse(in: lens.insetBy(dx: s * 0.004, dy: s * 0.004))
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.55).cgColor); ctx.setLineWidth(s * 0.006); ctx.strokePath()
    ctx.saveGState()
    ctx.addArc(center: center, radius: lensR * 0.86, startAngle: .pi * 0.58, endAngle: .pi * 0.92, clockwise: false)
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.85).cgColor); ctx.setLineWidth(s * 0.018); ctx.setLineCap(.round); ctx.strokePath()
    ctx.restoreGState()

    // треугольник с подсветкой
    let play = playPath(center: CGPoint(x: center.x + lensR * 0.06, y: center.y), size: lensR * 1.05)
    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.04, color: NSColor.white.withAlphaComponent(0.7).cgColor)
    ctx.addPath(play); ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

// Слои для Icon Composer (холст 1024, фон задаётся градиентом в icon.json).
let lensLayer = bitmap(1024) { ctx, s in
    let r = s * 0.27
    ctx.addEllipse(in: CGRect(x: s / 2 - r, y: s / 2 - r, width: r * 2, height: r * 2))
    ctx.setFillColor(NSColor.white.withAlphaComponent(0.35).cgColor)
    ctx.fillPath()
}
let playLayer = bitmap(1024) { ctx, s in
    ctx.addPath(playPath(center: CGPoint(x: s / 2 + s * 0.016, y: s / 2), size: s * 0.29))
    ctx.setFillColor(NSColor.white.cgColor)
    ctx.fillPath()
}
try lensLayer.write(to: assets.appendingPathComponent("lens.png"))
try playLayer.write(to: assets.appendingPathComponent("play.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.16000,0.10000,0.48000,1.00000",
      "srgb:0.93000,0.28000,0.58000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [
        { "glass" : true, "image-name" : "play.png", "name" : "play" }
      ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [
        { "glass" : true, "image-name" : "lens.png", "name" : "lens" }
      ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.6 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path), \(composer.path)")
