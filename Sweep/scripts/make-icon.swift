// Генератор иконки Sweep: искра в стеклянной сфере, вокруг — дуга-взмах.
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

let mint = NSColor(srgbRed: 0.10, green: 0.85, blue: 0.70, alpha: 1)
let blue = NSColor(srgbRed: 0.15, green: 0.50, blue: 1.00, alpha: 1)
let violet = NSColor(srgbRed: 0.55, green: 0.32, blue: 1.00, alpha: 1)

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

/// Четырёхлучевая искра.
func sparkle(center c: CGPoint, radius r: CGFloat) -> CGPath {
    let p = CGMutablePath()
    let inner = r * 0.22
    for i in 0..<8 {
        let a = CGFloat(i) * .pi / 4 + .pi / 2
        let rr = i % 2 == 0 ? r : inner
        let pt = CGPoint(x: c.x + cos(a) * rr, y: c.y + sin(a) * rr)
        if i == 0 { p.move(to: pt) } else {
            let mid = CGFloat(i) * .pi / 4 + .pi / 2 - .pi / 8
            let ctrl = CGPoint(x: c.x + cos(mid) * inner * 0.9, y: c.y + sin(mid) * inner * 0.9)
            p.addQuadCurve(to: pt, control: ctrl)
        }
    }
    p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: CGPoint(x: c.x + cos(.pi / 2 - .pi / 8) * inner * 0.9, y: c.y + sin(.pi / 2 - .pi / 8) * inner * 0.9))
    p.closeSubpath()
    return p
}

/// Дуга-взмах: сужающаяся к концу.
func drawSwoosh(_ ctx: CGContext, center c: CGPoint, radius r: CGFloat, width w: CGFloat, color: NSColor) {
    let steps = 60
    for i in 0..<steps {
        let u = CGFloat(i) / CGFloat(steps)
        let a = .pi * 1.15 + u * .pi * 1.25
        let pt = CGPoint(x: c.x + cos(a) * r, y: c.y + sin(a) * r * 0.92)
        let size = w * (0.15 + 0.85 * u)
        ctx.setFillColor(color.withAlphaComponent(0.25 + 0.75 * u).cgColor)
        ctx.fillEllipse(in: CGRect(x: pt.x - size / 2, y: pt.y - size / 2, width: size, height: size))
    }
}

func fullIcon(_ ctx: CGContext, _ s: CGFloat) {
    let inset = s * 0.1
    let rect = CGRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)
    let squircle = CGPath(roundedRect: rect, cornerWidth: rect.width * 0.225, cornerHeight: rect.width * 0.225, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addPath(squircle); ctx.setFillColor(blue.cgColor); ctx.fillPath()
    ctx.restoreGState()

    ctx.saveGState()
    ctx.addPath(squircle); ctx.clip()
    ctx.drawLinearGradient(gradient([mint, blue, violet]), start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let glow = CGPoint(x: rect.midX, y: rect.midY + rect.height * 0.12)
    ctx.drawRadialGradient(gradient([NSColor.white.withAlphaComponent(0.35), NSColor.white.withAlphaComponent(0)]),
                           startCenter: glow, startRadius: 0, endCenter: glow, endRadius: rect.width * 0.5, options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY)

    // Стеклянная сфера.
    let orbR = rect.width * 0.27
    let orb = CGRect(x: c.x - orbR, y: c.y - orbR, width: orbR * 2, height: orbR * 2)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.015), blur: s * 0.05, color: NSColor.black.withAlphaComponent(0.3).cgColor)
    ctx.addEllipse(in: orb); ctx.setFillColor(NSColor.white.withAlphaComponent(0.2).cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.addEllipse(in: orb.insetBy(dx: s * 0.003, dy: s * 0.003))
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.65).cgColor); ctx.setLineWidth(s * 0.006); ctx.strokePath()

    drawSwoosh(ctx, center: c, radius: rect.width * 0.38, width: s * 0.05, color: .white)

    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.04, color: NSColor.white.withAlphaComponent(0.9).cgColor)
    ctx.addPath(sparkle(center: c, radius: orbR * 0.72)); ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
    ctx.addPath(sparkle(center: CGPoint(x: c.x + orbR * 0.95, y: c.y + orbR * 0.95), radius: orbR * 0.22)); ctx.fillPath()
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let sparkleLayer = bitmap(1024) { ctx, s in
    let c = CGPoint(x: s / 2, y: s / 2)
    ctx.addPath(sparkle(center: c, radius: s * 0.16)); ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
    ctx.addPath(sparkle(center: CGPoint(x: c.x + s * 0.2, y: c.y + s * 0.2), radius: s * 0.05)); ctx.fillPath()
}
let swooshLayer = bitmap(1024) { ctx, s in
    drawSwoosh(ctx, center: CGPoint(x: s / 2, y: s / 2), radius: s * 0.31, width: s * 0.05, color: .white)
}
try sparkleLayer.write(to: assets.appendingPathComponent("sparkle.png"))
try swooshLayer.write(to: assets.appendingPathComponent("swoosh.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.10000,0.85000,0.70000,1.00000",
      "srgb:0.55000,0.32000,1.00000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "sparkle.png", "name" : "sparkle" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "swoosh.png", "name" : "swoosh" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
