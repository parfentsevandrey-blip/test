// Генератор иконки Utter: тёплый градиент, стеклянная капсула-микрофон, внутри — звуковая волна.
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

let coral = NSColor(srgbRed: 1.00, green: 0.42, blue: 0.40, alpha: 1)
let magenta = NSColor(srgbRed: 0.78, green: 0.25, blue: 0.75, alpha: 1)
let indigo = NSColor(srgbRed: 0.28, green: 0.20, blue: 0.75, alpha: 1)
let night = NSColor(srgbRed: 0.10, green: 0.07, blue: 0.28, alpha: 1)

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

/// Высоты столбиков волны: колокол с «речевыми» неровностями.
let bars: [CGFloat] = [0.18, 0.34, 0.58, 0.42, 0.86, 1.0, 0.7, 0.92, 0.5, 0.3, 0.16]

func drawWave(_ ctx: CGContext, center c: CGPoint, width w: CGFloat, height h: CGFloat, color: NSColor, glow: Bool) {
    let step = w / CGFloat(bars.count)
    let barW = step * 0.56
    for (i, v) in bars.enumerated() {
        let x = c.x - w / 2 + step * (CGFloat(i) + 0.5)
        let bh = max(barW, h * v)
        let rect = CGRect(x: x - barW / 2, y: c.y - bh / 2, width: barW, height: bh)
        ctx.saveGState()
        if glow { ctx.setShadow(offset: .zero, blur: barW * 1.2, color: color.withAlphaComponent(0.8).cgColor) }
        ctx.addPath(CGPath(roundedRect: rect, cornerWidth: barW / 2, cornerHeight: barW / 2, transform: nil))
        ctx.setFillColor(color.cgColor)
        ctx.fillPath()
        ctx.restoreGState()
    }
}

func capsule(_ c: CGPoint, _ s: CGFloat) -> CGRect {
    CGRect(x: c.x - s * 0.17, y: c.y - s * 0.25, width: s * 0.34, height: s * 0.5)
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
    ctx.drawLinearGradient(gradient([coral, magenta, indigo, night]),
                           start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY + s * 0.02)
    ctx.drawRadialGradient(gradient([NSColor.white.withAlphaComponent(0.28), NSColor.white.withAlphaComponent(0)]),
                           startCenter: c, startRadius: 0, endCenter: c, endRadius: s * 0.4, options: [])
    // Стеклянная капсула микрофона.
    let cap = capsule(c, rect.width)
    let capPath = CGPath(roundedRect: cap, cornerWidth: cap.width / 2, cornerHeight: cap.width / 2, transform: nil)
    ctx.addPath(capPath); ctx.setFillColor(NSColor.white.withAlphaComponent(0.13).cgColor); ctx.fillPath()
    drawWave(ctx, center: c, width: rect.width * 0.62, height: cap.height * 0.66, color: .white, glow: true)
    ctx.addPath(capPath)
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.65).cgColor); ctx.setLineWidth(s * 0.008); ctx.strokePath()
    // Блик на стекле.
    ctx.saveGState()
    let shine = CGRect(x: cap.minX + cap.width * 0.16, y: cap.maxY - cap.height * 0.36, width: cap.width * 0.24, height: cap.height * 0.26)
    ctx.addPath(CGPath(roundedRect: shine, cornerWidth: shine.width / 2, cornerHeight: shine.width / 2, transform: nil)); ctx.clip()
    ctx.drawLinearGradient(gradient([NSColor.white.withAlphaComponent(0.55), NSColor.white.withAlphaComponent(0)]),
                           start: CGPoint(x: shine.midX, y: shine.maxY), end: CGPoint(x: shine.midX, y: shine.minY), options: [])
    ctx.restoreGState()
    // Подставка микрофона.
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.55).cgColor)
    ctx.setLineWidth(s * 0.014); ctx.setLineCap(.round)
    ctx.addArc(center: CGPoint(x: c.x, y: cap.minY + cap.width * 0.5), radius: cap.width * 0.72, startAngle: .pi * 1.08, endAngle: .pi * 1.92, clockwise: false)
    ctx.strokePath()
    ctx.move(to: CGPoint(x: c.x, y: cap.minY + cap.width * 0.5 - cap.width * 0.72))
    ctx.addLine(to: CGPoint(x: c.x, y: rect.minY + rect.height * 0.1))
    ctx.strokePath()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let waveLayer = bitmap(1024) { ctx, s in
    drawWave(ctx, center: CGPoint(x: s / 2, y: s / 2), width: s * 0.5, height: s * 0.28, color: .white, glow: false)
}
let capsuleLayer = bitmap(1024) { ctx, s in
    let cap = capsule(CGPoint(x: s / 2, y: s / 2), s * 0.8)
    ctx.addPath(CGPath(roundedRect: cap, cornerWidth: cap.width / 2, cornerHeight: cap.width / 2, transform: nil))
    ctx.setStrokeColor(NSColor.white.cgColor); ctx.setLineWidth(s * 0.018); ctx.strokePath()
}
try waveLayer.write(to: assets.appendingPathComponent("wave.png"))
try capsuleLayer.write(to: assets.appendingPathComponent("capsule.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:1.00000,0.42000,0.40000,1.00000",
      "srgb:0.28000,0.20000,0.75000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "wave.png", "name" : "wave" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.6 },
      "translucency" : { "enabled" : true, "value" : 0.3 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "capsule.png", "name" : "capsule" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("Иконка: \(iconset.path), \(composer.path)")
