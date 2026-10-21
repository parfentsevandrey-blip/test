import SwiftUI

/// Созвездие роя в духе Liquid Glass.
///
/// В центре — жидкая стеклянная сфера: уровень жидкости внутри равен прогрессу, а капли данных от пиров
/// сливаются с ней, как ртуть (метаболы: размытие + порог альфы). Пиры — стеклянные бусины на наклонных
/// орбитах с настоящей глубиной: дальние проходят за сферой, ближние — перед ней. Данные летят кометами
/// по изогнутым нитям, каждый проверенный кусок расходится волной по плоскости орбит.
/// Новые пиры вылетают из ядра с пружинным перелётом, ушедшие — затягиваются обратно.
struct SwarmConstellation: View {
    let snapshot: TorrentSnapshot
    let tint: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var memory = ConstellationMemory()
    @State private var hover: CGPoint?

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(minimumInterval: 1 / 120, paused: reduceMotion)) { ctx in
                let scene = memory.scene(for: snapshot, size: geo.size,
                                         now: ctx.date.timeIntervalSinceReferenceDate, settled: reduceMotion)
                let focus = hover.flatMap { scene.nearest(to: $0) }
                ZStack {
                    Canvas { g, size in
                        ConstellationRenderer(scene: scene, tint: tint, focused: focus?.id).draw(&g, size: size)
                    }
                    coreLabel(core: scene.core)
                        .position(scene.center)
                    if let focus {
                        PeerTooltip(peer: focus.peer)
                            .position(tooltipPoint(focus, in: geo.size))
                            .transition(.scale(scale: 0.85).combined(with: .opacity))
                    }
                }
            }
            .rotation3DEffect(.degrees(tilt(in: geo.size).x), axis: (x: 1, y: 0, z: 0), perspective: 0.5)
            .rotation3DEffect(.degrees(tilt(in: geo.size).y), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
        }
        .overlay(alignment: .topLeading) { rates.padding(10) }
        .overlay(alignment: .bottom) { legend.padding(10) }
        .onContinuousHover { phase in
            switch phase {
            case .active(let p): hover = p
            case .ended: hover = nil
            }
        }
        .animation(.smooth(duration: 0.35), value: hover)
        .environment(\.colorScheme, .dark)
    }

    // MARK: - Подписи

    private func coreLabel(core: CGFloat) -> some View {
        Group {
            if snapshot.progress >= 1 {
                Image(systemName: "checkmark").font(.system(size: core * 0.55, weight: .heavy))
            } else {
                Text("\(Int(snapshot.progress * 100))%")
                    .font(.system(size: core * 0.46, weight: .heavy, design: .rounded))
                    .contentTransition(.numericText(value: snapshot.progress))
            }
        }
        .monospacedDigit()
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
        .animation(.smooth, value: Int(snapshot.progress * 100))
        .allowsHitTesting(false)
    }

    private var rates: some View {
        HStack(spacing: 10) {
            Label(Format.rate(snapshot.downRate), systemImage: "arrow.down")
            Label(Format.rate(snapshot.upRate), systemImage: "arrow.up")
        }
        .font(.system(size: 10.5, weight: .semibold))
        .monospacedDigit()
        .contentTransition(.numericText())
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .glassEffect(.clear, in: .capsule)
    }

    private var legend: some View {
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 8) {
                HStack(spacing: 10) {
                    dot(ConstellationMemory.seedColor, "Сиды")
                    dot(ConstellationMemory.leechColor, "Качают")
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .glassEffect(.clear, in: .capsule)
                Spacer(minLength: 4)
                Group {
                    if snapshot.peers.isEmpty && snapshot.status.isActive {
                        Label("Ищу пиров…", systemImage: "dot.radiowaves.left.and.right")
                            .symbolEffect(.variableColor.iterative, options: .repeating)
                    } else {
                        Text("\(snapshot.peersConnected) подключено · \(snapshot.peersKnown) известно")
                            .contentTransition(.numericText())
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .glassEffect(.clear, in: .capsule)
            }
        }
        .font(.system(size: 10.5, weight: .semibold))
        .monospacedDigit()
    }

    private func dot(_ color: Color, _ title: String) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(RadialGradient(colors: [color.mix(with: .white, by: 0.6), color], center: UnitPoint(x: 0.35, y: 0.3),
                                     startRadius: 0, endRadius: 5))
                .frame(width: 8, height: 8)
                .shadow(color: color.opacity(0.8), radius: 3)
            Text(title)
        }
    }

    // MARK: - Наведение

    private func tooltipPoint(_ node: ConstellationNode, in size: CGSize) -> CGPoint {
        let x = min(max(node.point.x, 95), size.width - 95)
        let above = node.point.y - node.radius - 38
        return CGPoint(x: x, y: above > 34 ? above : node.point.y + node.radius + 38)
    }

    /// Лёгкий наклон всей сцены за курсором — как будто смотришь в стеклянный шар.
    private func tilt(in size: CGSize) -> (x: Double, y: Double) {
        guard let hover, size.width > 0, size.height > 0, !reduceMotion else { return (0, 0) }
        let dx = hover.x / size.width - 0.5
        let dy = hover.y / size.height - 0.5
        return (-dy * 8, dx * 10)
    }
}

/// Стеклянная подсказка о пире под курсором.
private struct PeerTooltip: View {
    let peer: PeerSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Circle()
                    .fill(peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor)
                    .frame(width: 7, height: 7)
                Text(peer.client).font(.system(size: 12, weight: .semibold))
                if peer.isIncoming {
                    Image(systemName: "arrow.down.forward.circle.fill").foregroundStyle(.secondary).help("Входящее")
                }
            }
            Text(peer.address)
                .font(.system(size: 10.5, design: .monospaced))
                .foregroundStyle(.secondary)
            HStack(spacing: 10) {
                Label(Format.rate(peer.downRate), systemImage: "arrow.down")
                Label(Format.rate(peer.upRate), systemImage: "arrow.up")
                Text(peer.isSeed ? "сид" : Format.percent(peer.progress)).foregroundStyle(.secondary)
            }
            .font(.system(size: 10.5, weight: .semibold))
            .monospacedDigit()
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 8)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .fixedSize()
        .allowsHitTesting(false)
    }
}

// MARK: - Сцена

struct ConstellationNode: Identifiable {
    let id: String
    let peer: PeerSnapshot
    let point: CGPoint
    /// Контрольная точка изогнутой нити к ядру.
    let control: CGPoint
    /// −1 — далеко за ядром, 1 — ближе всего к зрителю.
    let depth: Double
    let radius: CGFloat
    let alpha: Double
    /// 0…1 — нить и поток данных (появляются, когда пир долетел до орбиты).
    let link: Double
}

struct ConstellationScene {
    var center: CGPoint
    var core: CGFloat
    var radii: CGSize
    var t: Double
    /// От дальних к ближним.
    var nodes: [ConstellationNode]
    /// Фазы 0…1 волн от проверенных кусков.
    var ripples: [Double]
    var progress: Double
    var activity: Double
    var searching: Bool

    func nearest(to p: CGPoint) -> ConstellationNode? {
        var best: (ConstellationNode, CGFloat)?
        for n in nodes where n.alpha > 0.3 {
            let d = hypot(n.point.x - p.x, n.point.y - p.y)
            if d < max(14, n.radius + 8), d < (best?.1 ?? .infinity) { best = (n, d) }
        }
        return best?.0
    }
}

/// Память сцены между кадрами: когда пир появился и ушёл, какие куски уже «отзвучали» волной.
final class ConstellationMemory {
    static let seedColor = Color(red: 0.2, green: 0.88, blue: 0.72)
    static let leechColor = Color(red: 0.62, green: 0.5, blue: 1.0)

    private var torrentID: String?
    private var born: [String: Double] = [:]
    private var known: [String: PeerSnapshot] = [:]
    private var departed: [String: (peer: PeerSnapshot, at: Double)] = [:]
    private var seenPieces: Set<Int> = []
    private var rippleStarts: [Double] = []

    private static let spawnDuration = 1.1
    private static let departDuration = 0.9
    private static let rippleDuration = 1.8

    func scene(for s: TorrentSnapshot, size: CGSize, now: Double, settled: Bool) -> ConstellationScene {
        if torrentID != s.id {
            torrentID = s.id
            born = [:]
            known = [:]
            departed = [:]
            rippleStarts = []
            seenPieces = Set(s.recentPieces.keys)
        }

        // Кто пришёл и кто ушёл.
        let peers = Array(s.peers.prefix(80))
        var current: [String: PeerSnapshot] = [:]
        for (index, p) in peers.enumerated() where current[p.id] == nil {
            current[p.id] = p
            departed[p.id] = nil
            // При первом показе пиры вылетают из ядра по очереди.
            if born[p.id] == nil { born[p.id] = settled ? now - 10 : now + Double(index % 24) * 0.035 }
        }
        for (id, p) in known where current[id] == nil {
            departed[id] = (p, now)
            born[id] = nil
        }
        known = current
        departed = settled ? [:] : departed.filter { now - $0.value.at < Self.departDuration }

        // Волна на каждый проверенный кусок (не чаще четырёх в секунду).
        for piece in s.recentPieces.keys where !seenPieces.contains(piece) {
            seenPieces.insert(piece)
            if !settled, now - (rippleStarts.last ?? 0) > 0.25 { rippleStarts.append(now) }
        }
        if seenPieces.count > 50_000 { seenPieces = Set(s.recentPieces.keys) }
        rippleStarts = Array(rippleStarts.filter { now - $0 < Self.rippleDuration }.suffix(6))

        // Геометрия.
        let t = now.truncatingRemainder(dividingBy: 86_400)
        let center = CGPoint(x: size.width / 2, y: size.height * 0.48)
        let rx = max(40, min(size.width * 0.42, size.height * 1.1))
        let ry = rx * 0.34
        let core = min(32, max(20, rx * 0.17))

        func place(_ p: PeerSnapshot) -> (CGPoint, Double) {
            let orbit = p.isSeed ? 1.0 : 0.46 + 0.44 * p.progress
            let speed = 0.05 + 0.1 * (1.1 - orbit) // внутренние орбиты быстрее, как у Кеплера
            let theta = p.angle + t * speed
            let lift = sin(t * 0.7 + p.angle * 5) * 0.07 + cos(p.angle * 3) * 0.14
            let depth = sin(theta)
            let perspective = 0.9 + 0.1 * (depth + 1) / 2
            return (CGPoint(x: center.x + cos(theta) * rx * orbit * perspective,
                            y: center.y + sin(theta) * ry * orbit + lift * ry), depth)
        }

        var nodes: [ConstellationNode] = []
        func add(_ p: PeerSnapshot, reach: Double, grow: Double, fade: Double) {
            let (target, depth) = place(p)
            let point = CGPoint(x: center.x + (target.x - center.x) * reach, y: center.y + (target.y - center.y) * reach)
            let rate = p.downRate + p.upRate
            let base = 3.4 + 4.6 * min(1, log10(1 + rate / 8000) / 2.6)
            let scale = 0.7 + 0.3 * (depth + 1) / 2
            let dx = center.x - point.x, dy = center.y - point.y
            let bend = sin(p.angle * 7) * 0.24
            let control = CGPoint(x: (point.x + center.x) / 2 - dy * bend, y: (point.y + center.y) / 2 + dx * bend)
            let alpha = (0.5 + 0.5 * (depth + 1) / 2) * fade * (p.isChokingUs && !p.isSeed ? 0.8 : 1)
            nodes.append(ConstellationNode(id: p.id, peer: p, point: point, control: control, depth: depth,
                                           radius: base * scale * (p.isSeed ? 1.12 : 1) * grow, alpha: alpha,
                                           link: min(grow, fade) * min(1, max(0, (reach - 0.6) / 0.4))))
        }

        for p in current.values {
            let age = now - (born[p.id] ?? now)
            let x = age / Self.spawnDuration
            // Пружина с перелётом: пир вылетает из ядра и качается на своей орбите.
            let reach = x <= 0 ? 0 : x >= 1 ? 1 : 1 - exp(-6 * x) * cos(7.5 * x)
            add(p, reach: reach, grow: min(1, max(0, age * 3)), fade: 1)
        }
        for (_, gone) in departed {
            let d = min(1, (now - gone.at) / Self.departDuration)
            add(gone.peer, reach: 1 - d * d, grow: 1 - d * 0.6, fade: 1 - d) // затягивается в ядро
        }
        nodes.sort { $0.depth < $1.depth }

        let rate = s.downRate + s.upRate
        return ConstellationScene(
            center: center, core: core, radii: CGSize(width: rx, height: ry), t: t, nodes: nodes,
            ripples: rippleStarts.map { (now - $0) / Self.rippleDuration },
            progress: min(1, max(0, s.progress)),
            activity: rate > 0 ? min(1, log10(1 + rate / 50_000) / 2.5) : 0,
            searching: s.peers.isEmpty && s.status.isActive)
    }
}

// MARK: - Отрисовка

private struct Particle {
    var head: CGPoint
    var tail: [CGPoint]
    var radius: CGFloat
    var color: Color
    var alpha: Double
    /// Положение на нити: 0 — у пира, 1 — у ядра.
    var position: Double
}

struct ConstellationRenderer {
    let scene: ConstellationScene
    let tint: Color
    let focused: String?

    private var c: CGPoint { scene.center }
    private var t: Double { scene.t }

    func draw(_ g: inout GraphicsContext, size: CGSize) {
        background(g, size)
        orbits(g)
        ripples(g)
        let back = scene.nodes.filter { $0.depth < 0 }
        let front = scene.nodes.filter { $0.depth >= 0 }
        links(g, back)
        for n in back { bead(g, n) }
        core(g)
        links(g, front)
        for n in front { bead(g, n) }
    }

    // MARK: Фон: глубокий колодец с туманностями и мерцающими звёздами

    private func background(_ g: GraphicsContext, _ size: CGSize) {
        let rect = CGRect(origin: .zero, size: size)
        g.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [Color(red: 0.06, green: 0.07, blue: 0.14), Color(red: 0.02, green: 0.025, blue: 0.06)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))

        var glow = g
        glow.blendMode = .plusLighter
        let a = CGPoint(x: size.width * (0.32 + 0.1 * sin(t * 0.11)), y: size.height * (0.38 + 0.12 * cos(t * 0.09)))
        let b = CGPoint(x: size.width * (0.7 + 0.1 * cos(t * 0.07)), y: size.height * (0.6 + 0.1 * sin(t * 0.13)))
        glow.fill(Path(rect), with: .radialGradient(Gradient(colors: [tint.opacity(0.22 + 0.2 * scene.activity), .clear]),
                                                    center: a, startRadius: 0, endRadius: size.width * 0.55))
        glow.fill(Path(rect), with: .radialGradient(Gradient(colors: [ConstellationMemory.leechColor.opacity(0.14), .clear]),
                                                    center: b, startRadius: 0, endRadius: size.width * 0.45))

        for i in 0..<56 {
            let fx = frac(sin(Double(i) * 12.9898) * 43758.5453)
            let fy = frac(sin(Double(i) * 78.233) * 12345.6789)
            let twinkle = 0.15 + 0.45 * (0.5 + 0.5 * sin(t * (0.6 + fx * 1.8) + Double(i) * 1.7))
            let r = 0.4 + fy * 0.8
            let p = CGPoint(x: fx * size.width, y: fy * size.height)
            g.fill(circle(p, r), with: .color(.white.opacity(twinkle)))
        }
    }

    // MARK: Орбиты и волны

    private func orbits(_ g: GraphicsContext) {
        let rx = scene.radii.width, ry = scene.radii.height
        for o in [0.46, 0.73, 1.0] {
            let rect = CGRect(x: c.x - rx * o, y: c.y - ry * o, width: rx * o * 2, height: ry * o * 2)
            g.stroke(Path(ellipseIn: rect),
                     with: .linearGradient(Gradient(colors: [.white.opacity(0.03), .white.opacity(0.17)]),
                                           startPoint: CGPoint(x: c.x, y: rect.minY), endPoint: CGPoint(x: c.x, y: rect.maxY)),
                     style: StrokeStyle(lineWidth: 1, dash: [2, 6], dashPhase: CGFloat(-t * 7 * o)))
        }
    }

    private func ripples(_ g: GraphicsContext) {
        var phases = scene.ripples
        if scene.searching {
            // Сонар: ищем пиров.
            phases += (0..<3).map { frac(t * 0.45 + Double($0) / 3) }
        }
        guard !phases.isEmpty else { return }
        var glow = g
        glow.blendMode = .plusLighter
        let rx = scene.radii.width, ry = scene.radii.height
        for phase in phases {
            let e = 1 - pow(1 - phase, 3)
            let w = scene.core + (rx * 1.08 - scene.core) * e
            let h = scene.core + (ry * 1.08 - scene.core) * e
            let ring = Path(ellipseIn: CGRect(x: c.x - w, y: c.y - h, width: w * 2, height: h * 2))
            let fade = pow(1 - phase, 2) * (scene.searching && scene.ripples.isEmpty ? 0.5 : 1)
            glow.stroke(ring, with: .color(tint.opacity(0.35 * fade)), lineWidth: 7 * (1 - phase) + 1)
            glow.stroke(ring, with: .color(tint.mix(with: .white, by: 0.5).opacity(0.8 * fade)), lineWidth: 1.2)
        }
    }

    // MARK: Нити и кометы данных

    private func edge(_ n: ConstellationNode) -> CGPoint {
        let dx = n.control.x - c.x, dy = n.control.y - c.y
        let len = max(0.001, hypot(dx, dy))
        let r = min(scene.core * 0.9, len)
        return CGPoint(x: c.x + dx / len * r, y: c.y + dy / len * r)
    }

    private func bezier(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ s: Double) -> CGPoint {
        let a = (1 - s) * (1 - s), b = 2 * (1 - s) * s, d = s * s
        return CGPoint(x: a * p0.x + b * p1.x + d * p2.x, y: a * p0.y + b * p1.y + d * p2.y)
    }

    private func strength(_ rate: Double) -> Double {
        rate > 0 ? min(1, log10(1 + rate / 6000) / 2.6) : 0
    }

    private func particles(_ n: ConstellationNode) -> [Particle] {
        guard n.link > 0.05 else { return [] }
        var out: [Particle] = []
        let p0 = n.point, p1 = n.control, p2 = edge(n)
        let depthScale = 0.75 + 0.25 * (n.depth + 1) / 2
        func stream(_ rate: Double, inbound: Bool) {
            guard rate > 400 else { return }
            let k = strength(rate)
            let count = min(7, 1 + Int(rate / 90_000))
            let speed = 0.28 + 0.8 * k
            let trail = 0.06 + 0.1 * k
            for i in 0..<count {
                let u = frac(t * speed + Double(i) / Double(count) + n.peer.angle * 3.1 + (inbound ? 0 : 0.5))
                // К ядру — с ускорением (капля падает в сферу), от ядра — выстрел и торможение.
                let s = inbound ? u * u : (1 - u) * (1 - u)
                let tailEnd = inbound ? max(0, s - trail) : min(1, s + trail)
                let tail = (0...5).map { bezier(p0, p1, p2, tailEnd + (s - tailEnd) * Double($0) / 5) }
                let alpha = min(1, u * 5) * (inbound ? 1 : min(1, (1 - u) * 4)) * n.link
                out.append(Particle(head: bezier(p0, p1, p2, s), tail: tail, radius: (1.3 + 1.7 * k) * depthScale,
                                    color: inbound ? .white : tint.mix(with: .white, by: 0.45), alpha: alpha, position: s))
            }
        }
        stream(n.peer.downRate, inbound: true)
        stream(n.peer.upRate, inbound: false)
        return out
    }

    private func links(_ g: GraphicsContext, _ nodes: [ConstellationNode]) {
        guard !nodes.isEmpty else { return }
        var glow = g
        glow.blendMode = .plusLighter

        // Мягкое свечение нитей одним размытым слоем.
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            for n in nodes {
                let k = strength(n.peer.downRate + n.peer.upRate)
                guard k > 0.05 else { continue }
                let base = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
                layer.stroke(path(n), with: .linearGradient(
                    Gradient(colors: [base.opacity(0.1 * k * n.alpha * n.link), tint.opacity(0.42 * k * n.alpha * n.link)]),
                    startPoint: n.point, endPoint: c), lineWidth: 3 + 6 * k)
            }
        }

        for n in nodes {
            let k = strength(n.peer.downRate + n.peer.upRate)
            let base = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
            let a = n.alpha * n.link * (focused == n.id ? 1.6 : 1)
            g.stroke(path(n), with: .linearGradient(
                Gradient(colors: [base.opacity(min(1, (0.1 + 0.4 * k) * a)), tint.opacity(min(1, (0.06 + 0.5 * k) * a))]),
                startPoint: n.point, endPoint: c), lineWidth: 0.6 + 1.4 * k + (focused == n.id ? 1 : 0))

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

    private func path(_ n: ConstellationNode) -> Path {
        var p = Path()
        p.move(to: n.point)
        p.addQuadCurve(to: edge(n), control: n.control)
        return p
    }

    // MARK: Ядро: жидкая стеклянная сфера

    private func core(_ g: GraphicsContext) {
        let breathe = 1 + 0.03 * sin(t * 2.2) + 0.04 * scene.activity * sin(t * 6.5)
        let r = scene.core * breathe

        var glow = g
        glow.blendMode = .plusLighter
        glow.fill(circle(c, r * 3.4), with: .radialGradient(
            Gradient(colors: [tint.opacity(0.5 + 0.35 * scene.activity), tint.opacity(0.1), .clear]),
            center: c, startRadius: r * 0.5, endRadius: r * 3.4))

        // Струи данных — метаболы: подлетающие капли сливаются с поверхностью сферы светящейся жидкостью.
        let blobs = scene.nodes.flatMap { particles($0) }.filter { $0.position > 0.72 && $0.alpha > 0.3 }
        g.drawLayer { liquid in
            liquid.addFilter(.alphaThreshold(min: 0.5, color: tint.mix(with: .white, by: 0.3)))
            liquid.addFilter(.blur(radius: 5))
            liquid.drawLayer { b in
                b.fill(circle(c, r), with: .color(.black))
                for k in 0..<3 {
                    let a = t * (0.9 + 0.25 * Double(k)) + Double(k) * 2.1
                    let wobble = r * (0.8 + 0.06 * sin(t * 3 + Double(k)))
                    b.fill(circle(CGPoint(x: c.x + cos(a) * wobble, y: c.y + sin(a) * wobble), r * 0.3), with: .color(.black))
                }
                for p in blobs { b.fill(circle(p.head, p.radius * 2.2), with: .color(.black)) }
            }
        }

        let box = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
        var inside = g
        inside.clip(to: Path(ellipseIn: box))
        // Пустая часть — тёмное прозрачное стекло, сквозь которое видно, сколько ещё осталось.
        inside.fill(Path(box), with: .color(Color(red: 0.03, green: 0.05, blue: 0.12).opacity(0.94)))
        inside.fill(Path(box), with: .linearGradient(Gradient(colors: [tint.opacity(0.3), tint.opacity(0.06)]),
                                                     startPoint: CGPoint(x: c.x, y: box.minY), endPoint: CGPoint(x: c.x, y: box.maxY)))

        // Жидкость: уровень — прогресс. Задняя волна темнее, передняя — яркая, с бликом мениска.
        let level = box.maxY - box.height * scene.progress
        let amp = scene.progress > 0.02 && scene.progress < 0.98 ? 2.2 + 1.6 * scene.activity : 0
        func surface(_ shift: Double, _ lift: CGFloat) -> [CGPoint] {
            (0...28).map { step in
                let x = box.minX + box.width * Double(step) / 28
                return CGPoint(x: x, y: level - lift + sin(x * 0.21 + t * 2.6 + shift) * amp)
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
            let back = surface(2.4, 2)
            inside.fill(body(back), with: .color(tint.mix(with: .black, by: 0.25).opacity(0.9)))
            let front = surface(0, 0)
            inside.fill(body(front), with: .linearGradient(
                Gradient(colors: [tint.mix(with: .white, by: 0.45), tint, tint.mix(with: .black, by: 0.3)]),
                startPoint: CGPoint(x: c.x, y: level - amp), endPoint: CGPoint(x: c.x, y: box.maxY)))
            var shine = inside
            shine.blendMode = .plusLighter
            var meniscus = Path()
            meniscus.addLines(front)
            shine.stroke(meniscus, with: .linearGradient(Gradient(colors: [.white.opacity(0.1), .white.opacity(0.85), .white.opacity(0.1)]),
                                                         startPoint: CGPoint(x: box.minX, y: level), endPoint: CGPoint(x: box.maxX, y: level)),
                         lineWidth: 1.3)
            // Пузырьки поднимаются со дна, быстрее при высокой скорости.
            if scene.progress > 0.06 {
                for i in 0..<7 {
                    let phase = frac(t * (0.25 + 0.3 * scene.activity) + Double(i) * 0.143)
                    let x = box.minX + box.width * (0.22 + 0.56 * frac(sin(Double(i) * 91.3) * 437.5)) + sin(t * 2 + Double(i)) * 2
                    let y = box.maxY - (box.maxY - level) * phase
                    let br = 0.7 + 1.3 * frac(Double(i) * 0.37)
                    shine.stroke(circle(CGPoint(x: x, y: y), br), with: .color(.white.opacity(0.55 * (1 - phase))), lineWidth: 0.7)
                }
            }
            // Жидкость светится изнутри.
            shine.fill(Path(box), with: .radialGradient(Gradient(colors: [tint.opacity(0.45), .clear]),
                                                        center: CGPoint(x: c.x, y: box.maxY), startRadius: 0, endRadius: r * 1.3))
        }
        // Объём и каустика.
        inside.fill(Path(box), with: .radialGradient(Gradient(colors: [.clear, .black.opacity(0.3)]),
                                                     center: CGPoint(x: c.x - r * 0.25, y: c.y - r * 0.3), startRadius: r * 0.3, endRadius: r * 1.35))
        let caustic = CGRect(x: c.x - r * 0.55, y: c.y + r * 0.42, width: r * 1.1, height: r * 0.42)
        inside.fill(Path(ellipseIn: caustic), with: .radialGradient(Gradient(colors: [.white.opacity(0.35), .clear]),
                                                                     center: CGPoint(x: caustic.midX, y: caustic.midY), startRadius: 0, endRadius: r * 0.55))

        // Блики и ободок стекла.
        let spec = CGRect(x: c.x - r * 0.68, y: c.y - r * 0.8, width: r * 0.9, height: r * 0.5)
        let turn = CGAffineTransform(translationX: spec.midX, y: spec.midY).rotated(by: -0.45).translatedBy(x: -spec.midX, y: -spec.midY)
        g.fill(Path(ellipseIn: spec).applying(turn), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.9), .white.opacity(0)]),
            startPoint: CGPoint(x: spec.midX, y: spec.minY), endPoint: CGPoint(x: spec.midX, y: spec.maxY)))
        g.fill(circle(CGPoint(x: c.x + r * 0.42, y: c.y - r * 0.52), r * 0.07), with: .color(.white.opacity(0.7)))
        g.stroke(Path(ellipseIn: box), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.85), .white.opacity(0.05), .white.opacity(0.4)]),
            startPoint: CGPoint(x: box.minX, y: box.minY), endPoint: CGPoint(x: box.maxX, y: box.maxY)), lineWidth: 1.2)

        progressRing(g, radius: r + 9)
    }

    private func progressRing(_ g: GraphicsContext, radius: CGFloat) {
        g.stroke(circle(c, radius), with: .color(.white.opacity(0.08)), lineWidth: 3)
        let p = scene.progress
        guard p > 0 else { return }
        var arc = Path()
        arc.addArc(center: c, radius: radius, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * p), clockwise: false)
        var glow = g
        glow.blendMode = .plusLighter
        glow.drawLayer { layer in
            layer.addFilter(.blur(radius: 4))
            layer.stroke(arc, with: .color(tint.opacity(0.9)), lineWidth: 5)
        }
        let gradient = Gradient(stops: [
            .init(color: tint, location: 0),
            .init(color: tint.mix(with: .white, by: 0.75), location: max(0.01, p)),
        ])
        g.stroke(arc, with: .conicGradient(gradient, center: c, angle: .degrees(-90)),
                 style: StrokeStyle(lineWidth: 3, lineCap: .round))

        // Сияющая «голова» кольца; на 100% по кольцу бежит блик.
        let headAngle = p >= 1 ? -Double.pi / 2 + t * 1.6 : -Double.pi / 2 + 2 * Double.pi * p
        let head = CGPoint(x: c.x + cos(headAngle) * radius, y: c.y + sin(headAngle) * radius)
        let pulse = 0.75 + 0.25 * sin(t * 5)
        glow.fill(circle(head, 9), with: .radialGradient(Gradient(colors: [tint.mix(with: .white, by: 0.5).opacity(0.9 * pulse), .clear]),
                                                         center: head, startRadius: 0, endRadius: 9))
        g.fill(circle(head, 2.2), with: .color(.white))
    }

    // MARK: Пиры: стеклянные бусины

    private func bead(_ g: GraphicsContext, _ n: ConstellationNode) {
        guard n.radius > 0.3, n.alpha > 0.01 else { return }
        let p = n.point
        let r = n.radius * (focused == n.id ? 1.35 : 1)
        let color = n.peer.isSeed ? ConstellationMemory.seedColor : ConstellationMemory.leechColor
        let active = n.peer.downRate + n.peer.upRate > 400
        var layer = g
        layer.opacity = n.alpha

        // Тень на «плоскости» и ореол.
        layer.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y + r * 1.1, width: r * 2, height: r * 0.55)),
                   with: .radialGradient(Gradient(colors: [.black.opacity(0.35), .clear]),
                                         center: CGPoint(x: p.x, y: p.y + r * 1.37), startRadius: 0, endRadius: r))
        var glow = layer
        glow.blendMode = .plusLighter
        let halo = r * (active ? 2.8 + 0.5 * sin(t * 3.5 + n.peer.angle * 9) : 1.9)
        glow.fill(circle(p, halo), with: .radialGradient(Gradient(colors: [color.opacity(active ? 0.55 : 0.25), .clear]),
                                                         center: p, startRadius: 0, endRadius: halo))

        // Тело: преломление — светлый центр смещён к источнику света, к краю цвет густеет.
        layer.fill(circle(p, r), with: .radialGradient(
            Gradient(colors: [color.mix(with: .white, by: 0.75), color, color.mix(with: .black, by: 0.4)]),
            center: CGPoint(x: p.x - r * 0.35, y: p.y - r * 0.4), startRadius: 0, endRadius: r * 1.55))
        // Блик и ободок.
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

    // MARK: Утилиты

    private func circle(_ p: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
    }
}

private func frac(_ x: Double) -> Double { x - x.rounded(.down) }
