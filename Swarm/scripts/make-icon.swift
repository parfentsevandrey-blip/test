// Генератор иконки Swarm: стрелка загрузки в стеклянной линзе, вокруг — рой частиц.
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

let teal = NSColor(srgbRed: 0.10, green: 0.78, blue: 0.74, alpha: 1)
let blue = NSColor(srgbRed: 0.13, green: 0.40, blue: 0.95, alpha: 1)
let violet = NSColor(srgbRed: 0.42, green: 0.20, blue: 0.86, alpha: 1)

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

/// Стрелка вниз со скруглениями.
func arrowPath(center c: CGPoint, size s: CGFloat) -> CGPath {
    let p = CGMutablePath()
    let shaft = s * 0.16
    let headW = s * 0.5
    let top = c.y + s * 0.42, mid = c.y - s * 0.02, tip = c.y - s * 0.42
    p.move(to: CGPoint(x: c.x - shaft, y: top))
    p.addLine(to: CGPoint(x: c.x + shaft, y: top))
    p.addLine(to: CGPoint(x: c.x + shaft, y: mid))
    p.addLine(to: CGPoint(x: c.x + headW, y: mid))
    p.addLine(to: CGPoint(x: c.x, y: tip))
    p.addLine(to: CGPoint(x: c.x - headW, y: mid))
    p.addLine(to: CGPoint(x: c.x - shaft, y: mid))
    p.closeSubpath()
    return p.copy(strokingWithWidth: s * 0.05, lineCap: .round, lineJoin: .round, miterLimit: 1)
        .union(p)
}

/// Частицы роя на эллиптической орбите: размер и яркость растут к «передней» части орбиты.
func drawSwarm(_ ctx: CGContext, center c: CGPoint, radius r: CGFloat, scale s: CGFloat, color: NSColor) {
    let count = 18
    for i in 0..<count {
        let t = CGFloat(i) / CGFloat(count) * 2 * .pi
        let depth = (sin(t) + 1) / 2
        let x = c.x + cos(t) * r
        let y = c.y + sin(t) * r * 0.42 - r * 0.05
        let size = s * (0.012 + 0.03 * depth)
        ctx.setFillColor(color.withAlphaComponent(0.35 + 0.65 * depth).cgColor)
        ctx.fillEllipse(in: CGRect(x: x - size, y: y - size, width: size * 2, height: size * 2))
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
    ctx.drawLinearGradient(gradient([teal, blue, violet]), start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let glow = CGPoint(x: rect.midX, y: rect.midY + rect.height * 0.1)
    ctx.drawRadialGradient(gradient([NSColor.white.withAlphaComponent(0.35), NSColor.white.withAlphaComponent(0)]),
                           startCenter: glow, startRadius: 0, endCenter: glow, endRadius: rect.width * 0.5, options: [])

    let c = CGPoint(x: rect.midX, y: rect.midY)
    drawSwarm(ctx, center: c, radius: rect.width * 0.36, scale: s, color: .white)

    // Стеклянная линза.
    let lensR = rect.width * 0.25
    let lens = CGRect(x: c.x - lensR, y: c.y - lensR, width: lensR * 2, height: lensR * 2)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.04, color: NSColor.black.withAlphaComponent(0.3).cgColor)
    ctx.addEllipse(in: lens); ctx.setFillColor(NSColor.white.withAlphaComponent(0.18).cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.addEllipse(in: lens.insetBy(dx: s * 0.003, dy: s * 0.003))
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.6).cgColor); ctx.setLineWidth(s * 0.006); ctx.strokePath()

    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.03, color: NSColor.white.withAlphaComponent(0.8).cgColor)
    ctx.addPath(arrowPath(center: c, size: lensR * 1.05)); ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let arrowLayer = bitmap(1024) { ctx, s in
    ctx.addPath(arrowPath(center: CGPoint(x: s / 2, y: s / 2), size: s * 0.21))
    ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
}
let swarmLayer = bitmap(1024) { ctx, s in
    drawSwarm(ctx, center: CGPoint(x: s / 2, y: s / 2), radius: s * 0.29, scale: s, color: .white)
}
try arrowLayer.write(to: assets.appendingPathComponent("arrow.png"))
try swarmLayer.write(to: assets.appendingPathComponent("swarm.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.10000,0.78000,0.74000,1.00000",
      "srgb:0.42000,0.20000,0.86000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "arrow.png", "name" : "arrow" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "swarm.png", "name" : "swarm" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
