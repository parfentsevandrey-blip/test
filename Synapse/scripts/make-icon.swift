// Генератор иконки Synapse: стеклянная сфера, внутри — сеть нейронов с яркими узлами.
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

let indigo = NSColor(srgbRed: 0.16, green: 0.12, blue: 0.45, alpha: 1)
let violet = NSColor(srgbRed: 0.50, green: 0.30, blue: 1.00, alpha: 1)
let cyan = NSColor(srgbRed: 0.25, green: 0.70, blue: 1.00, alpha: 1)
let pink = NSColor(srgbRed: 0.90, green: 0.40, blue: 0.95, alpha: 1)

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

/// Узлы сети на сфере (проекция), фиксированный «случайный» узор.
func nodes(center c: CGPoint, radius r: CGFloat) -> [(CGPoint, CGFloat)] {
    var result: [(CGPoint, CGFloat)] = []
    let n = 26
    let golden = Double.pi * (3 - sqrt(5))
    for i in 0..<n {
        let y = 1 - (Double(i) + 0.5) / Double(n) * 2
        let rr = sqrt(1 - y * y)
        let a = golden * Double(i) + 0.6
        let x = cos(a) * rr, z = sin(a) * rr
        let tilt = 0.45
        let y2 = y * cos(tilt) - z * sin(tilt)
        let z2 = y * sin(tilt) + z * cos(tilt)
        let p = 1.9 / (1.9 - z2 * 0.5)
        result.append((CGPoint(x: c.x + CGFloat(x * p) * r * 0.82, y: c.y + CGFloat(y2 * p) * r * 0.82), CGFloat((z2 + 1) / 2)))
    }
    return result
}

func drawNetwork(_ ctx: CGContext, center c: CGPoint, radius r: CGFloat, scale s: CGFloat, color: NSColor) {
    let pts = nodes(center: c, radius: r)
    ctx.setLineCap(.round)
    for (i, (p, d)) in pts.enumerated() {
        let near = pts.enumerated().filter { $0.offset != i }
            .sorted { hypot($0.element.0.x - p.x, $0.element.0.y - p.y) < hypot($1.element.0.x - p.x, $1.element.0.y - p.y) }.prefix(3)
        for (j, (q, e)) in near where j > i {
            ctx.setStrokeColor(color.withAlphaComponent(0.25 + 0.45 * (d + e) / 2).cgColor)
            ctx.setLineWidth(s * (0.004 + 0.004 * (d + e) / 2))
            ctx.move(to: p); ctx.addLine(to: q); ctx.strokePath()
        }
    }
    for (p, d) in pts {
        let size = s * (0.012 + 0.022 * d)
        ctx.saveGState()
        ctx.setShadow(offset: .zero, blur: size * 1.6, color: color.withAlphaComponent(0.9).cgColor)
        ctx.setFillColor(color.withAlphaComponent(0.55 + 0.45 * d).cgColor)
        ctx.fillEllipse(in: CGRect(x: p.x - size / 2, y: p.y - size / 2, width: size, height: size))
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
    ctx.drawLinearGradient(gradient([violet, indigo, NSColor(srgbRed: 0.06, green: 0.05, blue: 0.2, alpha: 1)]),
                           start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY)
    let orbR = rect.width * 0.33
    ctx.drawRadialGradient(gradient([cyan.withAlphaComponent(0.55), violet.withAlphaComponent(0.2), violet.withAlphaComponent(0)]),
                           startCenter: c, startRadius: 0, endCenter: c, endRadius: orbR * 1.5, options: [])
    // Стеклянная сфера.
    let orb = CGRect(x: c.x - orbR, y: c.y - orbR, width: orbR * 2, height: orbR * 2)
    ctx.addEllipse(in: orb); ctx.setFillColor(NSColor.white.withAlphaComponent(0.07).cgColor); ctx.fillPath()
    drawNetwork(ctx, center: c, radius: orbR, scale: s, color: .white)
    ctx.addEllipse(in: orb)
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.6).cgColor); ctx.setLineWidth(s * 0.007); ctx.strokePath()
    // Блик.
    ctx.saveGState()
    ctx.addEllipse(in: CGRect(x: c.x - orbR * 0.62, y: c.y + orbR * 0.42, width: orbR * 0.95, height: orbR * 0.42)); ctx.clip()
    ctx.drawLinearGradient(gradient([NSColor.white.withAlphaComponent(0.5), NSColor.white.withAlphaComponent(0)]),
                           start: CGPoint(x: c.x, y: c.y + orbR * 0.84), end: CGPoint(x: c.x, y: c.y + orbR * 0.42), options: [])
    ctx.restoreGState()
    // Розовый отсвет снизу.
    ctx.drawRadialGradient(gradient([pink.withAlphaComponent(0.45), pink.withAlphaComponent(0)]),
                           startCenter: CGPoint(x: c.x, y: c.y - orbR * 0.95), startRadius: 0,
                           endCenter: CGPoint(x: c.x, y: c.y - orbR * 0.95), endRadius: orbR * 0.6, options: [])
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let networkLayer = bitmap(1024) { ctx, s in
    drawNetwork(ctx, center: CGPoint(x: s / 2, y: s / 2), radius: s * 0.27, scale: s, color: .white)
}
let ringLayer = bitmap(1024) { ctx, s in
    let r = s * 0.27
    ctx.addEllipse(in: CGRect(x: s / 2 - r, y: s / 2 - r, width: r * 2, height: r * 2))
    ctx.setStrokeColor(NSColor.white.cgColor); ctx.setLineWidth(s * 0.018); ctx.strokePath()
}
try networkLayer.write(to: assets.appendingPathComponent("network.png"))
try ringLayer.write(to: assets.appendingPathComponent("ring.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.50000,0.30000,1.00000,1.00000",
      "srgb:0.10000,0.08000,0.32000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "network.png", "name" : "network" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.6 },
      "translucency" : { "enabled" : true, "value" : 0.3 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "ring.png", "name" : "ring" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
