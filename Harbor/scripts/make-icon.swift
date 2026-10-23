// Генератор иконки Harbor: парусник в стеклянной линзе над волнами гавани.
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

let sky = NSColor(srgbRed: 0.33, green: 0.80, blue: 0.95, alpha: 1)
let sea = NSColor(srgbRed: 0.08, green: 0.45, blue: 0.86, alpha: 1)
let deep = NSColor(srgbRed: 0.10, green: 0.18, blue: 0.55, alpha: 1)

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

/// Парусник: корпус, мачта, большой и малый паруса.
func boatPath(center c: CGPoint, size s: CGFloat) -> CGPath {
    let p = CGMutablePath()
    // Корпус.
    let hullTop = c.y - s * 0.20, hullBottom = c.y - s * 0.40
    p.move(to: CGPoint(x: c.x - s * 0.46, y: hullTop))
    p.addLine(to: CGPoint(x: c.x + s * 0.46, y: hullTop))
    p.addQuadCurve(to: CGPoint(x: c.x + s * 0.26, y: hullBottom), control: CGPoint(x: c.x + s * 0.40, y: hullBottom))
    p.addLine(to: CGPoint(x: c.x - s * 0.26, y: hullBottom))
    p.addQuadCurve(to: CGPoint(x: c.x - s * 0.46, y: hullTop), control: CGPoint(x: c.x - s * 0.40, y: hullBottom))
    p.closeSubpath()
    // Большой парус.
    p.move(to: CGPoint(x: c.x - s * 0.02, y: c.y + s * 0.46))
    p.addQuadCurve(to: CGPoint(x: c.x + s * 0.36, y: hullTop + s * 0.07), control: CGPoint(x: c.x + s * 0.30, y: c.y + s * 0.20))
    p.addLine(to: CGPoint(x: c.x - s * 0.02, y: hullTop + s * 0.07))
    p.closeSubpath()
    // Малый парус.
    p.move(to: CGPoint(x: c.x - s * 0.08, y: c.y + s * 0.36))
    p.addLine(to: CGPoint(x: c.x - s * 0.08, y: hullTop + s * 0.07))
    p.addQuadCurve(to: CGPoint(x: c.x - s * 0.36, y: hullTop + s * 0.07), control: CGPoint(x: c.x - s * 0.24, y: hullTop + s * 0.04))
    p.closeSubpath()
    return p
}

func wavesPath(rect: CGRect, s: CGFloat, offset: CGFloat) -> CGPath {
    let p = CGMutablePath()
    let y = rect.minY + rect.height * offset
    p.move(to: CGPoint(x: rect.minX, y: y))
    let n = 4
    for i in 0..<n {
        let x0 = rect.minX + rect.width * CGFloat(i) / CGFloat(n)
        let x1 = rect.minX + rect.width * CGFloat(i + 1) / CGFloat(n)
        p.addQuadCurve(to: CGPoint(x: x1, y: y), control: CGPoint(x: (x0 + x1) / 2, y: y + s * (i % 2 == 0 ? 0.035 : -0.035)))
    }
    p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
    p.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
    p.closeSubpath()
    return p
}

func fullIcon(_ ctx: CGContext, _ s: CGFloat) {
    let inset = s * 0.1
    let rect = CGRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)
    let squircle = CGPath(roundedRect: rect, cornerWidth: rect.width * 0.225, cornerHeight: rect.width * 0.225, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addPath(squircle); ctx.setFillColor(sea.cgColor); ctx.fillPath()
    ctx.restoreGState()

    ctx.saveGState()
    ctx.addPath(squircle); ctx.clip()
    ctx.drawLinearGradient(gradient([sky, sea, deep]), start: CGPoint(x: rect.midX, y: rect.maxY), end: CGPoint(x: rect.midX, y: rect.minY), options: [])
    let glow = CGPoint(x: rect.midX + rect.width * 0.18, y: rect.maxY - rect.height * 0.2)
    ctx.drawRadialGradient(gradient([NSColor.white.withAlphaComponent(0.45), NSColor.white.withAlphaComponent(0)]),
                           startCenter: glow, startRadius: 0, endCenter: glow, endRadius: rect.width * 0.45, options: [])
    // Волны.
    ctx.addPath(wavesPath(rect: rect, s: s, offset: 0.27)); ctx.setFillColor(NSColor.white.withAlphaComponent(0.16).cgColor); ctx.fillPath()
    ctx.addPath(wavesPath(rect: rect, s: s, offset: 0.17)); ctx.setFillColor(NSColor.white.withAlphaComponent(0.12).cgColor); ctx.fillPath()

    // Стеклянная линза.
    let c = CGPoint(x: rect.midX, y: rect.midY + rect.height * 0.04)
    let lensR = rect.width * 0.32
    let lens = CGRect(x: c.x - lensR, y: c.y - lensR, width: lensR * 2, height: lensR * 2)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.04, color: NSColor.black.withAlphaComponent(0.3).cgColor)
    ctx.addEllipse(in: lens); ctx.setFillColor(NSColor.white.withAlphaComponent(0.2).cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.addEllipse(in: lens.insetBy(dx: s * 0.003, dy: s * 0.003))
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.6).cgColor); ctx.setLineWidth(s * 0.006); ctx.strokePath()

    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.03, color: NSColor.white.withAlphaComponent(0.7).cgColor)
    ctx.addPath(boatPath(center: c, size: lensR * 1.2)); ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let boatLayer = bitmap(1024) { ctx, s in
    ctx.addPath(boatPath(center: CGPoint(x: s / 2, y: s / 2 + s * 0.03), size: s * 0.42))
    ctx.setFillColor(NSColor.white.cgColor); ctx.fillPath()
}
let waveLayer = bitmap(1024) { ctx, s in
    let rect = CGRect(x: 0, y: 0, width: s, height: s)
    ctx.addPath(wavesPath(rect: rect, s: s, offset: 0.24)); ctx.setFillColor(NSColor.white.withAlphaComponent(0.8).cgColor); ctx.fillPath()
}
try boatLayer.write(to: assets.appendingPathComponent("boat.png"))
try waveLayer.write(to: assets.appendingPathComponent("waves.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.33000,0.80000,0.95000,1.00000",
      "srgb:0.10000,0.18000,0.55000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "boat.png", "name" : "boat" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "waves.png", "name" : "waves" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.3 },
      "translucency" : { "enabled" : true, "value" : 0.6 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
