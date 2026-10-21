import SwiftUI

/// Отрисовка 3D-роя на Canvas. Всё, что есть в сцене, проецируется перспективной камерой и рисуется
/// от дальнего к ближнему: звёзды → задняя половина оболочки → всё, что за планетой → планета → всё, что перед ней.
struct ConstellationRenderer {
    let scene: ConstellationScene
    let tint: Color
    let focused: String?

    private var cam: Camera3D { scene.camera }
    private var t: Double { scene.t }
    private var c: CGPoint { scene.center }

    func draw(_ g: inout GraphicsContext, size: CGSize) {
        background(g, size)
        stars(g)
        shell(g, front: false)
        let cells = ringCells()
        ripples(g, front: false)
        progressRing(g, front: false)
        ring(g, cells.filter { $0.depth < 0 })
        let back = scene.nodes.filter { $0.depth < 0 }
        let front = scene.nodes.filter { $0.depth >= 0 }
        links(g, back)
        for n in back { bead(g, n) }
        planet(g)
        progressRing(g, front: true)
        ripples(g, front: true)
        ring(g, cells.filter { $0.depth >= 0 })
        links(g, front)
        for n in front { bead(g, n) }
        shell(g, front: true)
    }

    // MARK: - Космос

    private func background(_ g: GraphicsContext, _ size: CGSize) {
        let rect = CGRect(origin: .zero, size: size)
        g.fill(Path(rect), with: .radialGradient(
            Gradient(colors: [Color(red: 0.07, green: 0.09, blue: 0.2), Color(red: 0.025, green: 0.03, blue: 0.075), Color(red: 0.01, green: 0.01, blue: 0.03)]),
            center: c, startRadius: 0, endRadius: max(size.width, size.height) * 0.75))
        var glow = g
        glow.blendMode = .plusLighter
        let a = CGPoint(x: size.width * (0.28 + 0.06 * sin(t * 0.07)), y: size.height * (0.3 + 0.08 * cos(t * 0.05)))
        let b = CGPoint(x: size.width * (0.75 + 0.06 * cos(t * 0.06)), y: size.height * (0.72 + 0.06 * sin(t * 0.08)))
        glow.fill(Path(rect), with: .radialGradient(Gradient(colors: [tint.opacity(0.2 + 0.15 * scene.activity), .clear]),
                                                    center: a, startRadius: 0, endRadius: size.width * 0.6))
        glow.fill(Path(rect), with: .radialGradient(Gradient(colors: [ConstellationMemory.leechColor.opacity(0.13), .clear]),
                                                    center: b, startRadius: 0, endRadius: size.width * 0.5))
    }

    /// Звёзды на далёкой сфере: при вращении камеры дают настоящий параллакс.
    private func stars(_ g: GraphicsContext) {
        for i in 0..<260 {
            let u = frac(sin(Double(i) * 12.9898) * 43758.5453)
            let v = frac(sin(Double(i) * 78.233) * 12345.6789)
            let lon = u * 2 * .pi
            let lat = asin(2 * v - 1)
            let dir = Vec3(x: cos(lat) * cos(lon), y: sin(lat), z: cos(lat) * sin(lon))
            guard let p = cam.project(dir * 7.5) else { continue }
            let size = frac(Double(i) * 0.618) * 1.1 + 0.35
            let twinkle = 0.25 + 0.55 * (0.5 + 0.5 * sin(t * (0.5 + u * 1.7) + Double(i)))
            g.fill(circle(p.point, size * min(1.6, p.k)), with: .color(.white.opacity(twinkle * min(1, p.k))))
        }
    }

    /// Голографическая сфера-сетка вокруг всей сцены.
    private func shell(_ g: GraphicsContext, front: Bool) {
        let r = ConstellationScene.shellRadius
        var lines: [[Vec3]] = []
        for m in 0..<12 {
            let lon = Double(m) * .pi / 6 + t * 0.015
            lines.append((0...40).map { i in
                let lat = -Double.pi / 2 + Double.pi * Double(i) / 40
                return Vec3(x: cos(lat) * cos(lon) * r, y: sin(lat) * r, z: cos(lat) * sin(lon) * r)
            })
        }
        for lat in [-1.0, -0.5, 0.0, 0.5, 1.0] {
            lines.append((0...64).map { i in
                let lon = 2 * Double.pi * Double(i) / 64
                return Vec3(x: cos(lat) * cos(lon) * r, y: sin(lat) * r, z: cos(lat) * sin(lon) * r)
            })
        }
        var path = Path()
        for line in lines {
            var previous: Camera3D.Projection?
            for point in line {
                let p = cam.project(point)
                if let a = previous, let b = p, ((a.depth + b.depth) / 2 >= 0) == front {
                    path.move(to: a.point)
                    path.addLine(to: b.point)
                }
                previous = p
            }
        }
        let color = tint.mix(with: .white, by: 0.55)
        g.stroke(path, with: .color(color.opacity(front ? 0.13 : 0.05)), lineWidth: front ? 0.8 : 0.6)
    }

    // MARK: - Кольца из кусков

    private struct Cell {
        var point: CGPoint
        var depth: Double
        var k: Double
        var fraction: Double
        var active: Bool
        var fresh: Double
    }

    private func ringCells() -> [Cell] {
        let n = scene.ringCells
        let count = scene.pieceCount
        var out: [Cell] = []
        out.reserveCapacity(n)
        let perCell = count > 0 ? Double(count) / Double(n) : 0
        let spin = t * 0.035
        for i in 0..<n {
            let h = frac(sin(Double(i) * 12.9898) * 43758.5453)
            let h2 = frac(sin(Double(i) * 4.1414) * 24634.6345)
            let band = ConstellationScene.ringInner + (ConstellationScene.ringOuter - ConstellationScene.ringInner) * h
            // Внутренние пылинки летят быстрее — кольцо «течёт».
            let angle = 2 * .pi * Double(i) / Double(n) + spin * (1.6 - band * 0.6) + h2 * 0.02
            guard let p = cam.project(RingPlane.point(radius: band, angle: angle, lift: (h2 - 0.5) * 0.035)) else { continue }
            var fraction = 0.0, active = false, fresh = 0.0
            if count > 0 {
                let first = Int(Double(i) * perCell)
                let last = max(first, min(count - 1, Int(Double(i + 1) * perCell) - 1))
                var done = 0
                for piece in first...last {
                    if scene.have[piece] { done += 1 }
                    if scene.inProgress.contains(piece) { active = true }
                    if let at = scene.recent[piece] {
                        fresh = max(fresh, 1 - (scene.now - at.timeIntervalSinceReferenceDate) / 3)
                    }
                }
                fraction = Double(done) / Double(last - first + 1)
            }
            out.append(Cell(point: p.point, depth: p.depth, k: p.k, fraction: fraction, active: active, fresh: max(0, fresh)))
        }
        return out
    }

    private func ring(_ g: GraphicsContext, _ cells: [Cell]) {
        guard !cells.isEmpty else { return }
        let lit = tint.mix(with: .white, by: 0.35)
        let pulse = 0.5 + 0.5 * sin(t * 5)
        var glow = g
        glow.blendMode = .plusLighter
        var dim = Path(), bright = Path(), working = Path()
        for cell in cells {
            let near = min(1, max(0, (cell.depth + 1.5) / 3))
            let r = (0.009 + 0.012 * cell.fraction) * cell.k * cam.scale * (0.7 + 0.3 * near)
            let rect = CGRect(x: cell.point.x - r, y: cell.point.y - r, width: r * 2, height: r * 2)
            if cell.fraction > 0.5 { bright.addEllipse(in: rect) }
            else if cell.active { working.addEllipse(in: rect) }
            else { dim.addEllipse(in: rect) }
            if cell.fresh > 0 {
                let gr = r * 6
                glow.fill(circle(cell.point, gr), with: .radialGradient(
                    Gradient(colors: [Color.white.opacity(0.9 * cell.fresh), tint.opacity(0.4 * cell.fresh), .clear]),
                    center: cell.point, startRadius: 0, endRadius: gr))
            }
        }
        g.fill(dim, with: .color(.white.opacity(0.16)))
        g.fill(working, with: .color(tint.opacity(0.35 + 0.45 * pulse)))
        glow.fill(bright, with: .color(lit.opacity(0.95)))
    }

    /// 3D-кольцо прогресса вплотную к планете.
    private func progressRing(_ g: GraphicsContext, front: Bool) {
        let r = ConstellationScene.progressRadius
        let start = -Double.pi * 0.75
        func arc(from a0: Double, to a1: Double, steps: Int) -> Path {
            var path = Path()
            var previous: Camera3D.Projection?
            for i in 0...steps {
                let a = a0 + (a1 - a0) * Double(i) / Double(steps)
                let p = cam.project(RingPlane.point(radius: r, angle: a))
                if let pa = previous, let pb = p, ((pa.depth + pb.depth) / 2 >= 0) == front {
                    path.move(to: pa.point)
                    path.addLine(to: pb.point)
                }
                previous = p
            }
            return path
        }
        let width = 0.035 * cam.scale
        g.stroke(arc(from: 0, to: 2 * .pi, steps: 96), with: .color(.white.opacity(0.1)), lineWidth: width)
        let p = scene.progress
        guard p > 0 else { return }
        let done = arc(from: start, to: start + 2 * .pi * p, steps: max(4, Int(96 * p)))
        var glow = g
        glow.blendMode = .plusLighter
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.stroke(done, with: .color(tint.opacity(0.9)), lineWidth: width * 2.4)
        }
        g.stroke(done, with: .color(tint.mix(with: .white, by: 0.45)), style: StrokeStyle(lineWidth: width, lineCap: .round))
        if let head = cam.project(RingPlane.point(radius: r, angle: start + 2 * .pi * p)), (head.depth >= 0) == front {
            let pulse = 0.75 + 0.25 * sin(t * 5)
            let gr = width * 4
            glow.fill(circle(head.point, gr), with: .radialGradient(Gradient(colors: [Color.white.opacity(0.95 * pulse), tint.opacity(0.5), .clear]),
                                                                    center: head.point, startRadius: 0, endRadius: gr))
        }
    }

    /// Волны от проверенных кусков расходятся по плоскости колец; сонар — пока ищем пиров.
    private func ripples(_ g: GraphicsContext, front: Bool) {
        var phases = scene.ripples
        if scene.searching { phases += (0..<3).map { frac(t * 0.4 + Double($0) / 3) } }
        guard !phases.isEmpty else { return }
        var glow = g
        glow.blendMode = .plusLighter
        for phase in phases {
            let e = 1 - pow(1 - phase, 3)
            let r = ConstellationScene.coreRadius + (ConstellationScene.shellRadius - ConstellationScene.coreRadius) * e
            var path = Path()
            var previous: Camera3D.Projection?
            for i in 0...72 {
                let p = cam.project(RingPlane.point(radius: r, angle: 2 * .pi * Double(i) / 72))
                if let a = previous, let b = p, ((a.depth + b.depth) / 2 >= 0) == front {
                    path.move(to: a.point)
                    path.addLine(to: b.point)
                }
                previous = p
            }
            let fade = pow(1 - phase, 2) * (scene.searching && scene.ripples.isEmpty ? 0.55 : 1)
            glow.stroke(path, with: .color(tint.opacity(0.3 * fade)), lineWidth: 8 * (1 - phase) + 1)
            glow.stroke(path, with: .color(tint.mix(with: .white, by: 0.55).opacity(0.85 * fade)), lineWidth: 1.2)
        }
    }

    // MARK: - Нити и кометы

    private struct Particle {
        var head: CGPoint
        var tail: [CGPoint]
        var radius: CGFloat
        var color: Color
        var alpha: Double
        /// 0 — у пира, 1 — у планеты.
        var position: Double
    }

    private func strength(_ rate: Double) -> Double {
        rate > 0 ? min(1, log10(1 + rate / 6000) / 2.6) : 0
    }

    private func surface(_ n: ConstellationNode) -> Vec3 {
        n.world.normalized * (ConstellationScene.coreRadius * 0.92)
    }

    private func particles(_ n: ConstellationNode) -> [Particle] {
        guard n.link > 0.05 else { return [] }
        var out: [Particle] = []
        let a = n.world, b = surface(n), ctl = n.control
        func stream(_ rate: Double, inbound: Bool) {
            guard rate > 400 else { return }
            let k = strength(rate)
            let count = min(7, 1 + Int(rate / 90_000))
            let speed = 0.25 + 0.7 * k
            let trail = 0.07 + 0.1 * k
            for i in 0..<count {
                let u = frac(t * speed + Double(i) / Double(count) + n.peer.angle * 3.1 + (inbound ? 0 : 0.5))
                let s = inbound ? u * u : (1 - u) * (1 - u)
                let tailEnd = inbound ? max(0, s - trail) : min(1, s + trail)
                let samples = (0...5).compactMap { j in cam.project(Vec3.bezier(a, ctl, b, tailEnd + (s - tailEnd) * Double(j) / 5)) }
                guard let head = samples.last else { continue }
                let alpha = min(1, u * 5) * (inbound ? 1 : min(1, (1 - u) * 4)) * n.link
                out.append(Particle(head: head.point, tail: samples.map(\.point),
                                    radius: (0.018 + 0.022 * k) * head.k * cam.scale,
                                    color: inbound ? .white : tint.mix(with: .white, by: 0.45), alpha: alpha, position: s))
            }
        }
        stream(n.peer.downRate, inbound: true)
        stream(n.peer.upRate, inbound: false)
        return out
    }

    private func linkPath(_ n: ConstellationNode) -> Path {
        let a = n.world, b = surface(n), ctl = n.control
        var path = Path()
        var started = false
        for i in 0...18 {
            guard let p = cam.project(Vec3.bezier(a, ctl, b, Double(i) / 18)) else { continue }
            if started { path.addLine(to: p.point) } else { path.move(to: p.point); started = true }
        }
        return path
    }

    private func links(_ g: GraphicsContext, _ nodes: [ConstellationNode]) {
        guard !nodes.isEmpty else { return }
        var glow = g
        glow.blendMode = .plusLighter
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            for n in nodes {
                let k = strength(n.peer.downRate + n.peer.upRate)
                guard k > 0.05 else { continue }
                let base = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
                layer.stroke(linkPath(n), with: .linearGradient(
                    Gradient(colors: [base.opacity(0.1 * k * n.alpha * n.link), tint.opacity(0.42 * k * n.alpha * n.link)]),
                    startPoint: n.point, endPoint: c), lineWidth: 3 + 6 * k)
            }
        }
        for n in nodes {
            let k = strength(n.peer.downRate + n.peer.upRate)
            let base = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
            let a = n.alpha * n.link * (focused == n.id ? 1.6 : 1)
            g.stroke(linkPath(n), with: .linearGradient(
                Gradient(colors: [base.opacity(min(1, (0.08 + 0.4 * k) * a)), tint.opacity(min(1, (0.05 + 0.5 * k) * a))]),
                startPoint: n.point, endPoint: c), lineWidth: (0.6 + 1.4 * k) * n.k + (focused == n.id ? 1 : 0))
            for p in particles(n) {
                var trail = Path()
                trail.addLines(p.tail)
                glow.stroke(trail, with: .linearGradient(Gradient(colors: [p.color.opacity(0), p.color.opacity(0.85 * p.alpha)]),
                                                         startPoint: p.tail.first ?? p.head, endPoint: p.head),
                            style: StrokeStyle(lineWidth: p.radius * 1.5, lineCap: .round))
                glow.fill(circle(p.head, p.radius * 3.2), with: .radialGradient(
                    Gradient(colors: [tint.opacity(0.42 * p.alpha), .clear]), center: p.head, startRadius: 0, endRadius: p.radius * 3.2))
                g.fill(circle(p.head, p.radius), with: .color(p.color.opacity(p.alpha)))
            }
        }
    }

    // MARK: - Планета из жидкого стекла

    private func planet(_ g: GraphicsContext) {
        let breathe = 1 + 0.025 * sin(t * 2.2) + 0.035 * scene.activity * sin(t * 6.5)
        let r = scene.core * breathe

        var glow = g
        glow.blendMode = .plusLighter
        glow.fill(circle(c, r * 3.6), with: .radialGradient(
            Gradient(colors: [tint.opacity(0.55 + 0.3 * scene.activity), tint.opacity(0.12), .clear]),
            center: c, startRadius: r * 0.6, endRadius: r * 3.6))

        // Струи данных — метаболы: капли сливаются с поверхностью планеты светящейся жидкостью.
        let blobs = scene.nodes.flatMap { particles($0) }.filter { $0.position > 0.75 && $0.alpha > 0.3 }
        g.drawLayer { liquid in
            liquid.addFilter(.alphaThreshold(min: 0.5, color: tint.mix(with: .white, by: 0.3)))
            liquid.addFilter(.blur(radius: 5))
            liquid.drawLayer { b in
                b.fill(circle(c, r), with: .color(.black))
                for k in 0..<3 {
                    let a = t * (0.9 + 0.25 * Double(k)) + Double(k) * 2.1
                    let wobble = r * (0.82 + 0.05 * sin(t * 3 + Double(k)))
                    b.fill(circle(CGPoint(x: c.x + cos(a) * wobble, y: c.y + sin(a) * wobble), r * 0.28), with: .color(.black))
                }
                for p in blobs { b.fill(circle(p.head, p.radius * 2.2), with: .color(.black)) }
            }
        }

        let box = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
        var inside = g
        inside.clip(to: Path(ellipseIn: box))
        inside.fill(Path(box), with: .color(Color(red: 0.03, green: 0.05, blue: 0.12).opacity(0.94)))
        inside.fill(Path(box), with: .linearGradient(Gradient(colors: [tint.opacity(0.32), tint.opacity(0.06)]),
                                                     startPoint: CGPoint(x: c.x, y: box.minY), endPoint: CGPoint(x: c.x, y: box.maxY)))

        // Жидкость: уровень — прогресс. Поверхность наклоняется вместе с камерой, по ней бегут волны.
        let level = box.maxY - box.height * scene.progress
        let tilt = sin(cam.yaw * 0.5) * 0.08
        let amp = scene.progress > 0.02 && scene.progress < 0.98 ? r * 0.06 + r * 0.05 * scene.activity : 0
        func surfaceLine(_ shift: Double, _ lift: CGFloat) -> [CGPoint] {
            (0...32).map { step in
                let x = box.minX + box.width * Double(step) / 32
                let slope = (x - c.x) * tilt
                return CGPoint(x: x, y: level - lift + slope + sin((x - box.minX) / r * 3.2 + t * 2.6 + shift) * amp)
            }
        }
        func body(_ top: [CGPoint]) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: box.minX, y: box.maxY))
            p.addLines(top)
            p.addLine(to: CGPoint(x: box.maxX, y: box.maxY))
            p.closeSubpath()
            return p
        }
        if scene.progress > 0 {
            inside.fill(body(surfaceLine(2.4, r * 0.05)), with: .color(tint.mix(with: .black, by: 0.25).opacity(0.9)))
            let front = surfaceLine(0, 0)
            inside.fill(body(front), with: .linearGradient(
                Gradient(colors: [tint.mix(with: .white, by: 0.45), tint, tint.mix(with: .black, by: 0.3)]),
                startPoint: CGPoint(x: c.x, y: level - amp), endPoint: CGPoint(x: c.x, y: box.maxY)))
            var shine = inside
            shine.blendMode = .plusLighter
            var meniscus = Path()
            meniscus.addLines(front)
            shine.stroke(meniscus, with: .linearGradient(Gradient(colors: [.white.opacity(0.1), .white.opacity(0.85), .white.opacity(0.1)]),
                                                         startPoint: CGPoint(x: box.minX, y: level), endPoint: CGPoint(x: box.maxX, y: level)),
                         lineWidth: max(1, r * 0.03))
            if scene.progress > 0.06 {
                for i in 0..<9 {
                    let phase = frac(t * (0.22 + 0.3 * scene.activity) + Double(i) * 0.111)
                    let x = box.minX + box.width * (0.2 + 0.6 * frac(sin(Double(i) * 91.3) * 437.5)) + sin(t * 2 + Double(i)) * r * 0.04
                    let y = box.maxY - (box.maxY - level) * phase
                    let br = r * (0.015 + 0.03 * frac(Double(i) * 0.37))
                    shine.stroke(circle(CGPoint(x: x, y: y), br), with: .color(.white.opacity(0.55 * (1 - phase))), lineWidth: 0.7)
                }
            }
            shine.fill(Path(box), with: .radialGradient(Gradient(colors: [tint.opacity(0.45), .clear]),
                                                        center: CGPoint(x: c.x, y: box.maxY), startRadius: 0, endRadius: r * 1.3))
        }
        // Объём, атмосфера по краю (эффект Френеля) и каустика.
        inside.fill(Path(box), with: .radialGradient(Gradient(colors: [.clear, .black.opacity(0.3)]),
                                                     center: CGPoint(x: c.x - r * 0.25, y: c.y - r * 0.3), startRadius: r * 0.3, endRadius: r * 1.35))
        var rim = inside
        rim.blendMode = .plusLighter
        rim.fill(Path(box), with: .radialGradient(Gradient(colors: [.clear, .clear, tint.mix(with: .white, by: 0.4).opacity(0.55)]),
                                                  center: c, startRadius: 0, endRadius: r))
        let caustic = CGRect(x: c.x - r * 0.55, y: c.y + r * 0.42, width: r * 1.1, height: r * 0.42)
        inside.fill(Path(ellipseIn: caustic), with: .radialGradient(Gradient(colors: [.white.opacity(0.35), .clear]),
                                                                     center: CGPoint(x: caustic.midX, y: caustic.midY), startRadius: 0, endRadius: r * 0.55))

        // Блики и ободок.
        let spec = CGRect(x: c.x - r * 0.68, y: c.y - r * 0.8, width: r * 0.9, height: r * 0.5)
        let turn = CGAffineTransform(translationX: spec.midX, y: spec.midY).rotated(by: -0.45).translatedBy(x: -spec.midX, y: -spec.midY)
        g.fill(Path(ellipseIn: spec).applying(turn), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.9), .white.opacity(0)]),
            startPoint: CGPoint(x: spec.midX, y: spec.minY), endPoint: CGPoint(x: spec.midX, y: spec.maxY)))
        g.fill(circle(CGPoint(x: c.x + r * 0.42, y: c.y - r * 0.52), r * 0.07), with: .color(.white.opacity(0.7)))
        g.stroke(Path(ellipseIn: box), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.85), .white.opacity(0.05), .white.opacity(0.4)]),
            startPoint: CGPoint(x: box.minX, y: box.minY), endPoint: CGPoint(x: box.maxX, y: box.maxY)), lineWidth: 1.2)

        // Анаморфный блик через всю сцену — ярче, когда данные идут быстрее.
        let flareWidth = r * 9, flareHeight = max(1.5, r * 0.07)
        glow.fill(Path(ellipseIn: CGRect(x: c.x - flareWidth / 2, y: c.y - flareHeight / 2, width: flareWidth, height: flareHeight)),
                  with: .linearGradient(Gradient(colors: [.clear, tint.opacity(0.35 + 0.35 * scene.activity), .white.opacity(0.5 + 0.3 * scene.activity),
                                                          tint.opacity(0.35 + 0.35 * scene.activity), .clear]),
                                        startPoint: CGPoint(x: c.x - flareWidth / 2, y: c.y), endPoint: CGPoint(x: c.x + flareWidth / 2, y: c.y)))
    }

    // MARK: - Пиры: стеклянные бусины

    private func bead(_ g: GraphicsContext, _ n: ConstellationNode) {
        guard n.radius > 0.3, n.alpha > 0.01 else { return }
        let p = n.point
        let r = n.radius * (focused == n.id ? 1.35 : 1)
        let color = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
        let active = n.peer.downRate + n.peer.upRate > 400
        var layer = g
        layer.opacity = n.alpha

        var glow = layer
        glow.blendMode = .plusLighter
        let halo = r * (active ? 2.8 + 0.5 * sin(t * 3.5 + n.peer.angle * 9) : 1.9)
        glow.fill(circle(p, halo), with: .radialGradient(Gradient(colors: [color.opacity(active ? 0.55 : 0.25), .clear]),
                                                         center: p, startRadius: 0, endRadius: halo))
        layer.fill(circle(p, r), with: .radialGradient(
            Gradient(colors: [color.mix(with: .white, by: 0.75), color, color.mix(with: .black, by: 0.4)]),
            center: CGPoint(x: p.x - r * 0.35, y: p.y - r * 0.4), startRadius: 0, endRadius: r * 1.55))
        let spec = CGRect(x: p.x - r * 0.62, y: p.y - r * 0.78, width: r * 0.85, height: r * 0.5)
        layer.fill(Path(ellipseIn: spec), with: .linearGradient(Gradient(colors: [.white.opacity(0.9), .white.opacity(0)]),
                                                               startPoint: CGPoint(x: spec.midX, y: spec.minY),
                                                               endPoint: CGPoint(x: spec.midX, y: spec.maxY)))
        layer.stroke(circle(p, r), with: .linearGradient(Gradient(colors: [.white.opacity(0.75), .white.opacity(0.05)]),
                                                         startPoint: CGPoint(x: p.x, y: p.y - r), endPoint: CGPoint(x: p.x, y: p.y + r)),
                     lineWidth: 0.8)
        if n.peer.isIncoming {
            layer.stroke(circle(p, r + 3), with: .color(.white.opacity(0.45)),
                         style: StrokeStyle(lineWidth: 0.8, dash: [2, 3], dashPhase: CGFloat(t * 8)))
        }
        if focused == n.id {
            glow.stroke(circle(p, r + 5), with: .color(color.mix(with: .white, by: 0.5)), lineWidth: 1.5)
        }
    }

    private func circle(_ p: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
    }
}
