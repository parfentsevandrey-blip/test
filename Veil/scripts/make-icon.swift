// Генератор иконки Veil: глубокий сине-бирюзовый градиент, стеклянный щит, внутри — мягкие волны вуали.
//   swift make-icon.swift <out-dir>
// Создаёт AppIcon.iconset (для .icns) и AppIcon.icon (слои для Liquid Glass-иконки macOS 26+).
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

let teal = NSColor(srgbRed: 0.10, green: 0.78, blue: 0.70, alpha: 1)
let blue = NSColor(srgbRed: 0.16, green: 0.42, blue: 0.95, alpha: 1)
let indigo = NSColor(srgbRed: 0.18, green: 0.13, blue: 0.52, alpha: 1)
let night = NSColor(srgbRed: 0.05, green: 0.06, blue: 0.20, alpha: 1)

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

func gradient(_ colors: [NSColor]) -> CGGradient {
    CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: nil)!
}

/// Контур щита: плоский верх с мягкими углами, бока сходятся к острому низу.
func shield(_ c: CGPoint, _ w: CGFloat) -> CGPath {
    let h = w * 1.18
    let top = c.y + h * 0.5, bottom = c.y - h * 0.5
    let left = c.x - w / 2, right = c.x + w / 2
    let p = CGMutablePath()
    p.move(to: CGPoint(x: c.x, y: top))
    p.addCurve(to: CGPoint(x: right, y: top - h * 0.12), control1: CGPoint(x: c.x + w * 0.22, y: top), control2: CGPoint(x: right, y: top - h * 0.02))
    p.addCurve(to: CGPoint(x: c.x, y: bottom), control1: CGPoint(x: right, y: c.y - h * 0.1), control2: CGPoint(x: c.x + w * 0.28, y: bottom + h * 0.1))
    p.addCurve(to: CGPoint(x: left, y: top - h * 0.12), control1: CGPoint(x: c.x - w * 0.28, y: bottom + h * 0.1), control2: CGPoint(x: left, y: c.y - h * 0.1))
    p.addCurve(to: CGPoint(x: c.x, y: top), control1: CGPoint(x: left, y: top - h * 0.02), control2: CGPoint(x: c.x - w * 0.22, y: top))
    p.closeSubpath()
    return p
}

/// Волны вуали внутри щита.
func drawWaves(_ ctx: CGContext, _ c: CGPoint, _ w: CGFloat, _ s: CGFloat, glow: Bool) {
    ctx.setLineCap(.round)
    for i in 0..<4 {
        let y = c.y + w * (0.26 - CGFloat(i) * 0.17)
        let amp = w * (0.06 + CGFloat(i) * 0.012)
        let p = CGMutablePath()
        p.move(to: CGPoint(x: c.x - w * 0.36, y: y))
        p.addCurve(to: CGPoint(x: c.x + w * 0.36, y: y),
                   control1: CGPoint(x: c.x - w * 0.12, y: y + amp * 2), control2: CGPoint(x: c.x + w * 0.12, y: y - amp * 2))
        ctx.saveGState()
        if glow { ctx.setShadow(offset: .zero, blur: s * 0.02, color: NSColor.white.withAlphaComponent(0.8).cgColor) }
        ctx.addPath(p)
        ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.95 - CGFloat(i) * 0.17).cgColor)
        ctx.setLineWidth(s * (0.026 - CGFloat(i) * 0.003))
        ctx.strokePath()
        ctx.restoreGState()
    }
}

func fullIcon(_ ctx: CGContext, _ s: CGFloat) {
    let inset = s * 0.1
    let rect = CGRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)
    let squircle = CGPath(roundedRect: rect, cornerWidth: rect.width * 0.225, cornerHeight: rect.width * 0.225, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addPath(squircle); ctx.setFillColor(indigo.cgColor); ctx.fillPath()
    ctx.restoreGState()

    ctx.saveGState()
    ctx.addPath(squircle); ctx.clip()
    ctx.drawLinearGradient(gradient([teal, blue, indigo, night]),
                           start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY - s * 0.01)
    ctx.drawRadialGradient(gradient([teal.withAlphaComponent(0.5), teal.withAlphaComponent(0)]),
                           startCenter: CGPoint(x: c.x, y: c.y + s * 0.1), startRadius: 0, endCenter: CGPoint(x: c.x, y: c.y + s * 0.1), endRadius: s * 0.42, options: [])
    let w = rect.width * 0.5
    let sh = shield(c, w)
    ctx.addPath(sh); ctx.setFillColor(NSColor.white.withAlphaComponent(0.12).cgColor); ctx.fillPath()
    ctx.saveGState()
    ctx.addPath(sh); ctx.clip()
    drawWaves(ctx, c, w, s, glow: true)
    // Блик сверху.
    ctx.drawLinearGradient(gradient([NSColor.white.withAlphaComponent(0.35), NSColor.white.withAlphaComponent(0)]),
                           start: CGPoint(x: c.x, y: c.y + w * 0.6), end: CGPoint(x: c.x, y: c.y + w * 0.1), options: [])
    ctx.restoreGState()
    ctx.addPath(sh)
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.75).cgColor); ctx.setLineWidth(s * 0.009); ctx.strokePath()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let wavesLayer = bitmap(1024) { ctx, s in
    let c = CGPoint(x: s / 2, y: s / 2)
    ctx.addPath(shield(c, s * 0.4)); ctx.clip()
    drawWaves(ctx, c, s * 0.4, s, glow: false)
}
let shieldLayer = bitmap(1024) { ctx, s in
    ctx.addPath(shield(CGPoint(x: s / 2, y: s / 2), s * 0.4))
    ctx.setStrokeColor(NSColor.white.cgColor); ctx.setLineWidth(s * 0.018); ctx.strokePath()
}
try wavesLayer.write(to: assets.appendingPathComponent("waves.png"))
try shieldLayer.write(to: assets.appendingPathComponent("shield.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.10000,0.78000,0.70000,1.00000",
      "srgb:0.18000,0.13000,0.52000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "waves.png", "name" : "waves" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.6 },
      "translucency" : { "enabled" : true, "value" : 0.3 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "shield.png", "name" : "shield" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("Иконка: \(iconset.path), \(composer.path)")
