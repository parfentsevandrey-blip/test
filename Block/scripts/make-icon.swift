// Генератор иконки Block: Mac в центре, сервисы — погасшие точки на дальней орбите, одна зелёная — замена.
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

let indigo = NSColor(srgbRed: 0.30, green: 0.26, blue: 0.95, alpha: 1)
let night = NSColor(srgbRed: 0.06, green: 0.05, blue: 0.20, alpha: 1)
let teal = NSColor(srgbRed: 0.10, green: 0.80, blue: 0.75, alpha: 1)
let green = NSColor(srgbRed: 0.25, green: 0.90, blue: 0.45, alpha: 1)

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

/// Ноутбук: экран со скруглёнными углами и основание.
func drawLaptop(_ ctx: CGContext, center c: CGPoint, width w: CGFloat, color: NSColor) {
    let screen = CGRect(x: c.x - w * 0.36, y: c.y - w * 0.12, width: w * 0.72, height: w * 0.46)
    let frame = CGPath(roundedRect: screen, cornerWidth: w * 0.05, cornerHeight: w * 0.05, transform: nil)
    ctx.addPath(frame)
    ctx.setStrokeColor(color.cgColor)
    ctx.setLineWidth(w * 0.045)
    ctx.strokePath()
    let base = CGRect(x: c.x - w * 0.5, y: c.y - w * 0.2, width: w, height: w * 0.055)
    ctx.addPath(CGPath(roundedRect: base, cornerWidth: w * 0.027, cornerHeight: w * 0.027, transform: nil))
    ctx.setFillColor(color.cgColor)
    ctx.fillPath()
}

/// Орбита и точки-сервисы на ней.
func drawOrbit(_ ctx: CGContext, center c: CGPoint, radius r: CGFloat, s: CGFloat) {
    let orbit = CGRect(x: c.x - r, y: c.y - r * 0.62, width: r * 2, height: r * 1.24)
    ctx.addEllipse(in: orbit)
    ctx.setStrokeColor(NSColor.white.withAlphaComponent(0.35).cgColor)
    ctx.setLineWidth(s * 0.008)
    ctx.setLineDash(phase: 0, lengths: [s * 0.02, s * 0.025])
    ctx.strokePath()
    ctx.setLineDash(phase: 0, lengths: [])
    for (i, a) in [0.35, 1.3, 2.4, 3.5, 4.4, 5.4].enumerated() {
        let p = CGPoint(x: c.x + cos(a) * r, y: c.y + sin(a) * r * 0.62)
        let d = s * (i == 4 ? 0.07 : 0.045)
        let color = i == 4 ? green : NSColor.white.withAlphaComponent(0.32)
        ctx.saveGState()
        if i == 4 { ctx.setShadow(offset: .zero, blur: s * 0.04, color: green.cgColor) }
        ctx.setFillColor(color.cgColor)
        ctx.fillEllipse(in: CGRect(x: p.x - d / 2, y: p.y - d / 2, width: d, height: d))
        ctx.restoreGState()
    }
}

func fullIcon(_ ctx: CGContext, _ s: CGFloat) {
    let inset = s * 0.1
    let rect = CGRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2)
    let squircle = CGPath(roundedRect: rect, cornerWidth: rect.width * 0.225, cornerHeight: rect.width * 0.225, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.35).cgColor)
    ctx.addPath(squircle); ctx.setFillColor(night.cgColor); ctx.fillPath()
    ctx.restoreGState()

    ctx.saveGState()
    ctx.addPath(squircle); ctx.clip()
    ctx.drawLinearGradient(gradient([indigo, night]), start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY)
    ctx.drawRadialGradient(gradient([teal.withAlphaComponent(0.55), teal.withAlphaComponent(0)]),
                           startCenter: c, startRadius: 0, endCenter: c, endRadius: rect.width * 0.36, options: [])
    drawOrbit(ctx, center: c, radius: rect.width * 0.4, s: s)
    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.035, color: NSColor.white.withAlphaComponent(0.8).cgColor)
    drawLaptop(ctx, center: CGPoint(x: c.x, y: c.y - rect.height * 0.02), width: rect.width * 0.42, color: .white)
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let laptopLayer = bitmap(1024) { ctx, s in
    drawLaptop(ctx, center: CGPoint(x: s / 2, y: s / 2), width: s * 0.36, color: .white)
}
let orbitLayer = bitmap(1024) { ctx, s in
    drawOrbit(ctx, center: CGPoint(x: s / 2, y: s / 2), radius: s * 0.34, s: s)
}
try laptopLayer.write(to: assets.appendingPathComponent("laptop.png"))
try orbitLayer.write(to: assets.appendingPathComponent("orbit.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.30000,0.26000,0.95000,1.00000",
      "srgb:0.06000,0.05000,0.20000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "laptop.png", "name" : "laptop" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "orbit.png", "name" : "orbit" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.4 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
