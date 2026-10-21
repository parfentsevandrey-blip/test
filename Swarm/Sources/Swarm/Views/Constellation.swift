import SwiftUI

/// Рой в 3D. Настоящая перспективная камера над маленькой вселенной раздачи:
/// - в центре — планета из жидкого стекла, уровень жидкости внутри равен прогрессу;
/// - вокруг неё 3D-кольцо прогресса и «кольца Сатурна» из пылинок — это сами куски раздачи:
///   скачанные светятся, недостающие тусклые, только что проверенные вспыхивают;
/// - пиры — стеклянные бусины в объёме на разных широтах, данные летят к планете кометами по 3D-дугам
///   и вливаются в неё, как ртуть;
/// - всё обёрнуто голографической сферой-сеткой на фоне звёзд с параллаксом.
/// Сцену можно вращать мышью (с инерцией), двойной щелчок возвращает камеру; кнопка разворачивает рой в отдельное окно.
struct SwarmConstellation: View {
    let snapshot: TorrentSnapshot
    let tint: Color
    var expandable = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.openWindow) private var openWindow
    @State private var memory = ConstellationMemory()
    @State private var hover: CGPoint?
    @State private var orbit = OrbitControl()

    /// На паузе цвет статуса серый — для вселенной берём спокойный синий, чтобы сцена не выцветала.
    private var accent: Color {
        [.paused, .error, .finished].contains(snapshot.status) ? Color(red: 0.36, green: 0.52, blue: 0.98) : tint
    }

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(minimumInterval: 1 / 120, paused: reduceMotion)) { ctx in
                let now = ctx.date.timeIntervalSinceReferenceDate
                let scene = memory.scene(for: snapshot, size: geo.size, now: now, settled: reduceMotion,
                                         yaw: orbit.yaw(at: now), pitch: orbit.currentPitch)
                let focus = hover.flatMap { scene.nearest(to: $0) }
                ZStack {
                    Canvas { g, size in
                        ConstellationRenderer(scene: scene, tint: accent, focused: focus?.id).draw(&g, size: size)
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
        }
        .contentShape(Rectangle())
        .gesture(drag)
        .onTapGesture(count: 2) { orbit = OrbitControl() }
        // В отдельном окне слева «светофор» — там скорость уходит вправо.
        .overlay(alignment: expandable ? .topLeading : .topTrailing) { rates.padding(expandable ? 10 : 16) }
        .overlay(alignment: .topTrailing) { if expandable { expandButton.padding(10) } }
        .overlay(alignment: .bottom) { legend.padding(10) }
        .onContinuousHover { phase in
            switch phase {
            case .active(let p): hover = p
            case .ended: hover = nil
            }
        }
        .animation(.smooth(duration: 0.3), value: hover == nil)
        .environment(\.colorScheme, .dark)
        .help("Тяните, чтобы вращать · двойной щелчок — вернуть камеру")
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 3)
            .onChanged { v in
                orbit.dragYaw = Double(v.translation.width) * 0.008
                orbit.dragPitch = Double(v.translation.height) * 0.005
            }
            .onEnded { v in
                orbit.commit(now: Date().timeIntervalSinceReferenceDate, velocity: Double(v.velocity.width))
            }
    }

    // MARK: - Подписи

    private func coreLabel(core: CGFloat) -> some View {
        Group {
            if snapshot.progress >= 1 {
                Image(systemName: "checkmark").font(.system(size: core * 0.5, weight: .heavy))
            } else {
                Text("\(Int(snapshot.progress * 100))%")
                    .font(.system(size: core * 0.42, weight: .heavy, design: .rounded))
                    .contentTransition(.numericText(value: snapshot.progress))
            }
        }
        .monospacedDigit()
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.6), radius: 3, y: 1)
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
        .lineLimit(1)
        .fixedSize()
        .contentTransition(.numericText())
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .glassEffect(.clear, in: .capsule)
    }

    private var expandButton: some View {
        Button {
            openWindow(id: "swarm", value: snapshot.id)
        } label: {
            Image(systemName: "arrow.up.left.and.arrow.down.right")
                .font(.system(size: 11, weight: .bold))
                .frame(width: 26, height: 26)
        }
        .buttonStyle(.plain)
        .glassEffect(.clear.interactive(), in: .circle)
        .help("Открыть рой в отдельном окне")
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
                        Label("\(snapshot.peersConnected) из \(snapshot.peersKnown)", systemImage: "person.2.fill")
                            .contentTransition(.numericText())
                    }
                }
                .lineLimit(1)
                .fixedSize()
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
        .lineLimit(1)
        .fixedSize()
    }

    private func tooltipPoint(_ node: ConstellationNode, in size: CGSize) -> CGPoint {
        let x = min(max(node.point.x, 95), size.width - 95)
        let above = node.point.y - node.radius - 38
        return CGPoint(x: x, y: above > 34 ? above : node.point.y + node.radius + 38)
    }
}

/// Вращение камеры мышью: текущий жест, накопленный угол и затухающая инерция после броска.
struct OrbitControl {
    var yaw = 0.0
    var pitch = 0.0
    var dragYaw = 0.0
    var dragPitch = 0.0
    var spin = 0.0
    var spinStart = 0.0

    func yaw(at now: Double) -> Double {
        let dt = max(0, now - spinStart)
        return yaw + dragYaw + spin * (1 - exp(-dt * 1.4)) / 1.4
    }

    var currentPitch: Double { min(0.85, max(-0.38, pitch + dragPitch)) }

    mutating func commit(now: Double, velocity: Double) {
        yaw = yaw(at: now)
        pitch = currentPitch
        dragYaw = 0
        dragPitch = 0
        spin = max(-6, min(6, velocity * 0.008))
        spinStart = now
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
                    Image(systemName: "arrow.down.forward.circle.fill").foregroundStyle(.secondary)
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

// MARK: - 3D-математика

struct Vec3 {
    var x: Double
    var y: Double
    var z: Double

    static let zero = Vec3(x: 0, y: 0, z: 0)
    static func + (a: Vec3, b: Vec3) -> Vec3 { Vec3(x: a.x + b.x, y: a.y + b.y, z: a.z + b.z) }
    static func - (a: Vec3, b: Vec3) -> Vec3 { Vec3(x: a.x - b.x, y: a.y - b.y, z: a.z - b.z) }
    static func * (a: Vec3, k: Double) -> Vec3 { Vec3(x: a.x * k, y: a.y * k, z: a.z * k) }
    var length: Double { (x * x + y * y + z * z).squareRoot() }
    var normalized: Vec3 { self * (1 / max(1e-9, length)) }
    func cross(_ b: Vec3) -> Vec3 { Vec3(x: y * b.z - z * b.y, y: z * b.x - x * b.z, z: x * b.y - y * b.x) }

    /// Квадратичная кривая Безье в пространстве.
    static func bezier(_ a: Vec3, _ c: Vec3, _ b: Vec3, _ s: Double) -> Vec3 {
        a * ((1 - s) * (1 - s)) + c * (2 * (1 - s) * s) + b * (s * s)
    }
}

/// Перспективная камера, смотрящая на начало координат.
struct Camera3D {
    var yaw: Double
    var pitch: Double
    var center: CGPoint
    var scale: Double
    var distance = 9.0

    struct Projection {
        var point: CGPoint
        /// Больше — ближе к зрителю.
        var depth: Double
        /// Перспективный масштаб (1 — на уровне центра сцены).
        var k: Double
    }

    func project(_ p: Vec3) -> Projection? {
        let cy = cos(yaw), sy = sin(yaw)
        let x1 = p.x * cy + p.z * sy
        let z1 = -p.x * sy + p.z * cy
        let cp = cos(pitch), sp = sin(pitch)
        let y2 = p.y * cp - z1 * sp
        let z2 = p.y * sp + z1 * cp
        let d = distance - z2
        guard d > 0.3 else { return nil }
        let k = distance / d
        return Projection(point: CGPoint(x: center.x + x1 * k * scale, y: center.y - y2 * k * scale), depth: z2, k: k)
    }
}

/// Плоскость колец: чуть наклонена относительно экватора — так кольца «Сатурна» читаются объёмно.
enum RingPlane {
    private static let cx = cos(0.24), sx = sin(0.24)
    private static let cz = cos(0.14), sz = sin(0.14)

    static func point(radius r: Double, angle a: Double, lift: Double = 0) -> Vec3 {
        let v = Vec3(x: r * cos(a), y: lift, z: r * sin(a))
        let x = Vec3(x: v.x, y: v.y * cx - v.z * sx, z: v.y * sx + v.z * cx)
        return Vec3(x: x.x * cz - x.y * sz, y: x.x * sz + x.y * cz, z: x.z)
    }
}

// MARK: - Сцена

struct ConstellationNode: Identifiable {
    let id: String
    let peer: PeerSnapshot
    let world: Vec3
    /// Контрольная точка 3D-дуги к планете.
    let control: Vec3
    let point: CGPoint
    let depth: Double
    let k: Double
    let radius: CGFloat
    let alpha: Double
    /// 0…1 — нить и поток данных (появляются, когда пир долетел до орбиты).
    let link: Double
}

struct ConstellationScene {
    static let coreRadius = 0.55
    static let progressRadius = 0.74
    static let ringInner = 0.9
    static let ringOuter = 1.38
    static let shellRadius = 2.35

    var camera: Camera3D
    var now: Double
    var t: Double
    var nodes: [ConstellationNode]
    var ripples: [Double]
    var progress: Double
    var activity: Double
    var searching: Bool
    var pieceCount: Int
    var have: Bitfield
    var inProgress: Set<Int>
    var recent: [Int: Date]
    /// Сколько пылинок в кольце (зависит от размера вида).
    var ringCells: Int

    var center: CGPoint { camera.center }
    var core: CGFloat { Self.coreRadius * camera.scale }

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

    private static let spawnDuration = 1.3
    private static let departDuration = 0.9
    private static let rippleDuration = 2.2

    func scene(for s: TorrentSnapshot, size: CGSize, now: Double, settled: Bool,
               yaw: Double = 0, pitch: Double = 0) -> ConstellationScene {
        if torrentID != s.id {
            torrentID = s.id
            born = [:]
            known = [:]
            departed = [:]
            rippleStarts = []
            seenPieces = Set(s.recentPieces.keys)
        }

        let peers = Array(s.peers.prefix(90))
        var current: [String: PeerSnapshot] = [:]
        for (index, p) in peers.enumerated() where current[p.id] == nil {
            current[p.id] = p
            departed[p.id] = nil
            if born[p.id] == nil { born[p.id] = settled ? now - 10 : now + Double(index % 30) * 0.04 }
        }
        for (id, p) in known where current[id] == nil {
            departed[id] = (p, now)
            born[id] = nil
        }
        known = current
        departed = settled ? [:] : departed.filter { now - $0.value.at < Self.departDuration }

        for piece in s.recentPieces.keys where !seenPieces.contains(piece) {
            seenPieces.insert(piece)
            if !settled, now - (rippleStarts.last ?? 0) > 0.3 { rippleStarts.append(now) }
        }
        if seenPieces.count > 50_000 { seenPieces = Set(s.recentPieces.keys) }
        rippleStarts = Array(rippleStarts.filter { now - $0 < Self.rippleDuration }.suffix(5))

        // Камера: медленный облёт плюс то, что накрутил пользователь.
        let t = now.truncatingRemainder(dividingBy: 86_400)
        let fit = min(Double(size.width) / (2 * ConstellationScene.shellRadius * 1.15),
                      Double(size.height) / (2 * ConstellationScene.shellRadius * 1.0))
        let camera = Camera3D(yaw: (settled ? 0.6 : t * 0.07) + yaw, pitch: 0.4 + pitch,
                              center: CGPoint(x: size.width / 2, y: size.height * 0.5), scale: max(20, fit))

        var nodes: [ConstellationNode] = []
        func add(_ p: PeerSnapshot, reach: Double, grow: Double, fade: Double) {
            let orbit = p.isSeed ? 2.05 : 1.55 + 0.42 * p.progress
            let lat = (frac(p.angle * 7.31) - 0.5) * 1.5
            let lon = p.angle + t * (0.05 + 0.07 * (2.1 - orbit))
            let bob = sin(t * 0.6 + p.angle * 5) * 0.05
            let target = Vec3(x: cos(lat) * cos(lon) * orbit, y: sin(lat) * orbit + bob, z: cos(lat) * sin(lon) * orbit)
            let world = target * reach
            guard let pr = camera.project(world) else { return }
            let surface = world.normalized * (ConstellationScene.coreRadius * 0.92)
            let side = world.cross(Vec3(x: 0, y: 1, z: 0)).normalized
            let control = (world + surface) * 0.5 + side * (0.45 * sin(p.angle * 7)) + world.normalized * 0.25
            let rate = p.downRate + p.upRate
            let base = 0.05 + 0.055 * min(1, log10(1 + rate / 8000) / 2.6)
            let near = min(1, max(0, (pr.depth + 2.2) / 4.4))
            nodes.append(ConstellationNode(
                id: p.id, peer: p, world: world, control: control, point: pr.point, depth: pr.depth, k: pr.k,
                radius: base * (p.isSeed ? 1.12 : 1) * pr.k * camera.scale * grow,
                alpha: (0.45 + 0.55 * near) * fade * (p.isChokingUs && !p.isSeed ? 0.8 : 1),
                link: min(grow, fade) * min(1, max(0, (reach - 0.6) / 0.4))))
        }
        for p in current.values {
            let age = now - (born[p.id] ?? now)
            let x = age / Self.spawnDuration
            let reach = x <= 0 ? 0 : x >= 1 ? 1 : 1 - exp(-6 * x) * cos(7.5 * x)
            add(p, reach: reach, grow: min(1, max(0, age * 3)), fade: 1)
        }
        for (_, gone) in departed {
            let d = min(1, (now - gone.at) / Self.departDuration)
            add(gone.peer, reach: 1 - d * d, grow: 1 - d * 0.6, fade: 1 - d)
        }
        nodes.sort { $0.depth < $1.depth }

        let rate = s.downRate + s.upRate
        let cells = s.pieceCount > 0 ? min(s.pieceCount, max(300, Int(Double(size.width) * 2.4)), 3000) : 480
        return ConstellationScene(
            camera: camera, now: now, t: t, nodes: nodes,
            ripples: rippleStarts.map { (now - $0) / Self.rippleDuration },
            progress: min(1, max(0, s.progress)),
            activity: rate > 0 ? min(1, log10(1 + rate / 50_000) / 2.5) : 0,
            searching: s.peers.isEmpty && s.status.isActive,
            pieceCount: s.pieceCount,
            have: Bitfield(count: s.pieceCount, data: s.havePieces),
            inProgress: Set(s.inProgressPieces),
            recent: s.recentPieces,
            ringCells: cells)
    }
}

func frac(_ x: Double) -> Double { x - x.rounded(.down) }
