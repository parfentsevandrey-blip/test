// Фон окна установщика: тёмный градиент, мягкие огни и подсказка со стрелкой.
//   swift dmg-background.swift <out.png> <width> <height> <scale>
import AppKit

let args = CommandLine.arguments
let url = URL(fileURLWithPath: args[1])
let w = CGFloat(Double(args[2])!), h = CGFloat(Double(args[3])!), scale = CGFloat(Double(args[4])!)
let px = (Int(w * scale), Int(h * scale))

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: px.0, pixelsHigh: px.1, bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: w, height: h)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let ctx = NSGraphicsContext.current!.cgContext
let space = CGColorSpace(name: CGColorSpace.sRGB)!

func color(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) -> CGColor { CGColor(srgbRed: r, green: g, blue: b, alpha: a) }

let bg = CGGradient(colorsSpace: space, colors: [color(0.05, 0.05, 0.14), color(0.13, 0.07, 0.27), color(0.05, 0.12, 0.22)] as CFArray, locations: nil)!
ctx.drawLinearGradient(bg, start: CGPoint(x: 0, y: h), end: CGPoint(x: w, y: 0), options: [])
for (c, x, y, r) in [(color(0.93, 0.28, 0.58, 0.35), 0.18, 0.3, 0.5), (color(0.2, 0.6, 1, 0.3), 0.85, 0.75, 0.45)] {
    let center = CGPoint(x: w * x, y: h * y)
    let g = CGGradient(colorsSpace: space, colors: [c, c.copy(alpha: 0)!] as CFArray, locations: nil)!
    ctx.drawRadialGradient(g, startCenter: center, startRadius: 0, endCenter: center, endRadius: w * r, options: [])
}

// Стрелка между иконками (иконки стоят на y = 200 от верха, x = 160 и 440).
let arrowY = h - 200
ctx.setStrokeColor(color(1, 1, 1, 0.55))
ctx.setLineWidth(3)
ctx.setLineCap(.round)
ctx.setLineDash(phase: 0, lengths: [2, 9])
ctx.move(to: CGPoint(x: 245, y: arrowY)); ctx.addLine(to: CGPoint(x: 345, y: arrowY)); ctx.strokePath()
ctx.setLineDash(phase: 0, lengths: [])
ctx.move(to: CGPoint(x: 335, y: arrowY + 10)); ctx.addLine(to: CGPoint(x: 350, y: arrowY)); ctx.addLine(to: CGPoint(x: 335, y: arrowY - 10)); ctx.strokePath()

let title = NSAttributedString(string: "Перетащите Lumen в «Программы»", attributes: [
    .font: NSFont.systemFont(ofSize: 17, weight: .semibold),
    .foregroundColor: NSColor.white.withAlphaComponent(0.9),
])
let size = title.size()
title.draw(at: NSPoint(x: (w - size.width) / 2, y: 62))
let hint = NSAttributedString(string: "Первый запуск: правый клик → «Открыть» или Настройки → Конфиденциальность → «Всё равно открыть»", attributes: [
    .font: NSFont.systemFont(ofSize: 10.5, weight: .regular),
    .foregroundColor: NSColor.white.withAlphaComponent(0.55),
])
let hs = hint.size()
hint.draw(at: NSPoint(x: (w - hs.width) / 2, y: 36))

NSGraphicsContext.restoreGraphicsState()
try rep.representation(using: .png, properties: [:])!.write(to: url)
