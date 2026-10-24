// Генератор иконки Vessel: стеклянное окно-«сосуд», внутри — ещё одно окно с приглашением терминала.
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

let amber = NSColor(srgbRed: 0.98, green: 0.72, blue: 0.18, alpha: 1)
let night = NSColor(srgbRed: 0.05, green: 0.06, blue: 0.16, alpha: 1)
let deep = NSColor(srgbRed: 0.14, green: 0.12, blue: 0.36, alpha: 1)

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

/// Окно: рамка, полоска заголовка с тремя точками.
func drawWindow(_ ctx: CGContext, _ r: CGRect, color: NSColor, line: CGFloat) {
    let path = CGPath(roundedRect: r, cornerWidth: r.width * 0.09, cornerHeight: r.width * 0.09, transform: nil)
    ctx.addPath(path)
    ctx.setStrokeColor(color.cgColor)
    ctx.setLineWidth(line)
    ctx.strokePath()
    let bar = r.maxY - r.height * 0.2
    ctx.move(to: CGPoint(x: r.minX + line / 2, y: bar))
    ctx.addLine(to: CGPoint(x: r.maxX - line / 2, y: bar))
    ctx.strokePath()
    for i in 0..<3 {
        let d = r.height * 0.065
        let x = r.minX + r.width * 0.08 + CGFloat(i) * d * 1.7
        ctx.setFillColor(color.cgColor)
        ctx.fillEllipse(in: CGRect(x: x, y: bar + (r.maxY - bar - d) / 2, width: d, height: d))
    }
}

/// Приглашение терминала «>_».
func drawPrompt(_ ctx: CGContext, _ r: CGRect, color: NSColor, line: CGFloat) {
    ctx.setStrokeColor(color.cgColor)
    ctx.setLineWidth(line)
    ctx.setLineCap(.round)
    ctx.setLineJoin(.round)
    let x = r.minX + r.width * 0.2, y = r.midY - r.height * 0.08, h = r.height * 0.18
    ctx.move(to: CGPoint(x: x, y: y + h))
    ctx.addLine(to: CGPoint(x: x + h * 0.9, y: y))
    ctx.addLine(to: CGPoint(x: x, y: y - h))
    ctx.strokePath()
    ctx.move(to: CGPoint(x: x + h * 1.5, y: y - h))
    ctx.addLine(to: CGPoint(x: x + h * 3.0, y: y - h))
    ctx.strokePath()
}

func outer(_ s: CGFloat) -> CGRect { CGRect(x: s * 0.2, y: s * 0.24, width: s * 0.6, height: s * 0.5) }
func inner(_ s: CGFloat) -> CGRect { CGRect(x: s * 0.3, y: s * 0.3, width: s * 0.4, height: s * 0.3) }

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
    ctx.drawLinearGradient(gradient([deep, night]), start: CGPoint(x: rect.minX, y: rect.maxY), end: CGPoint(x: rect.maxX, y: rect.minY), options: [])
    let c = CGPoint(x: rect.midX, y: rect.midY - rect.height * 0.04)
    ctx.drawRadialGradient(gradient([amber.withAlphaComponent(0.5), amber.withAlphaComponent(0)]),
                           startCenter: c, startRadius: 0, endCenter: c, endRadius: rect.width * 0.42, options: [])
    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.03, color: NSColor.white.withAlphaComponent(0.7).cgColor)
    drawWindow(ctx, outer(s), color: .white, line: s * 0.028)
    ctx.restoreGState()
    ctx.saveGState()
    ctx.setShadow(offset: .zero, blur: s * 0.04, color: amber.cgColor)
    drawWindow(ctx, inner(s), color: amber, line: s * 0.022)
    drawPrompt(ctx, CGRect(x: inner(s).minX, y: inner(s).minY, width: inner(s).width, height: inner(s).height * 0.8), color: amber, line: s * 0.024)
    ctx.restoreGState()
    ctx.restoreGState()
}

for base in [16, 32, 128, 256, 512] {
    try bitmap(base) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base).png"))
    try bitmap(base * 2) { fullIcon($0, $1) }.write(to: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"))
}

let outerLayer = bitmap(1024) { ctx, s in drawWindow(ctx, outer(s), color: .white, line: s * 0.028) }
let innerLayer = bitmap(1024) { ctx, s in
    drawWindow(ctx, inner(s), color: amber, line: s * 0.022)
    drawPrompt(ctx, CGRect(x: inner(s).minX, y: inner(s).minY, width: inner(s).width, height: inner(s).height * 0.8), color: amber, line: s * 0.024)
}
try outerLayer.write(to: assets.appendingPathComponent("outer.png"))
try innerLayer.write(to: assets.appendingPathComponent("inner.png"))

let json = """
{
  "fill" : {
    "linear-gradient" : [
      "srgb:0.14000,0.12000,0.36000,1.00000",
      "srgb:0.05000,0.06000,0.16000,1.00000"
    ]
  },
  "groups" : [
    {
      "layers" : [ { "glass" : true, "image-name" : "inner.png", "name" : "inner" } ],
      "shadow" : { "kind" : "layer-color", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.4 }
    },
    {
      "layers" : [ { "glass" : true, "image-name" : "outer.png", "name" : "outer" } ],
      "shadow" : { "kind" : "neutral", "opacity" : 0.5 },
      "translucency" : { "enabled" : true, "value" : 0.5 }
    }
  ],
  "supported-platforms" : { "squares" : "shared" }
}
"""
try json.write(to: composer.appendingPathComponent("icon.json"), atomically: true, encoding: .utf8)
print("icon: \(iconset.path)")
