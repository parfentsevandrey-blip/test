import SwiftUI

/// One stop on the way from this Mac to the Internet.
struct FlowNode: Identifiable, Equatable {
    enum Kind: Equatable {
        case mac
        case bridge
        case relay(CircuitHop.Role)
        case internet
        case youtube
    }

    let id: String
    let kind: Kind
    let title: String
    let subtitle: String?
    let flag: String?
    let symbol: String
    let accent: Color
    /// Lines shown in the hover card.
    let details: [String]
}

/// What the diagram is showing.
enum FlowMode: Equatable {
    case idle
    case connecting(progress: Double)
    case connected
    case failed
    case blocked
    case turbo

    var isLive: Bool { self == .connected || self == .turbo }
}

/// Positions of the nodes, the rail and the YouTube fast lane for a given size.
struct FlowLayout {
    let size: CGSize
    let count: Int
    let radius: CGFloat = 26

    var margin: CGFloat { 60 }
    var railY: CGFloat { size.height * 0.5 }
    var labelY: CGFloat { railY + radius + 21 }
    /// Highest point of the fast lane arc.
    var laneTop: CGPoint { CGPoint(x: size.width / 2, y: max(16, railY - radius - 22)) }

    func x(_ index: Int) -> CGFloat {
        guard count > 1 else { return size.width / 2 }
        return margin + (size.width - 2 * margin) * CGFloat(index) / CGFloat(max(1, count - 1))
    }

    func center(_ index: Int) -> CGPoint {
        CGPoint(x: x(index), y: railY)
    }

    var laneStart: CGPoint { CGPoint(x: x(0), y: railY - radius) }
    var laneEnd: CGPoint { CGPoint(x: x(count - 1), y: railY - radius) }
    /// Quadratic control point that makes the arc peak exactly at `laneTop`.
    var laneControl: CGPoint { CGPoint(x: size.width / 2, y: 2 * laneTop.y - (railY - radius)) }

    func lanePoint(_ t: CGFloat) -> CGPoint {
        let p0 = laneStart, p1 = laneEnd, c = laneControl
        let u = 1 - t
        return CGPoint(
            x: u * u * p0.x + 2 * u * t * c.x + t * t * p1.x,
            y: u * u * p0.y + 2 * u * t * c.y + t * t * p1.y
        )
    }

    func nearestNode(to point: CGPoint, tolerance: CGFloat = 34) -> Int? {
        var best: (index: Int, distance: CGFloat)?
        for index in 0..<count {
            let c = center(index)
            let distance = hypot(c.x - point.x, c.y - point.y)
            if distance <= tolerance, best == nil || distance < best!.distance {
                best = (index, distance)
            }
        }
        return best?.index
    }

    func isNearLane(_ point: CGPoint) -> Bool {
        hypot(point.x - laneTop.x, point.y - laneTop.y) <= 32
    }
}

/// The live route: Mac → bridge → relays → Internet, with traffic drawn as particles whose number
/// and speed follow the real throughput. Nodes light up as Tor bootstraps, can be hovered for
/// details and clicked to jump to the matching screen.
struct TunnelFlowView: View {
    let nodes: [FlowNode]
    let mode: FlowMode
    let downloadRate: Double
    let uploadRate: Double
    let paddingRate: Double
    /// Sites that bypass Tor (YouTube, presets): an extra lane from the Mac straight to the Internet.
    let fastLane: FlowNode?
    let onSelect: @MainActor (FlowNode) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hoveredID: String?

    private var everyNode: [FlowNode] {
        if let fastLane { return nodes + [fastLane] }
        return nodes
    }

    var body: some View {
        GeometryReader { geometry in
            let layout = FlowLayout(size: geometry.size, count: nodes.count)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(minimumInterval: mode.isLive ? 1.0 / 30.0 : 1.0 / 12.0, paused: reduceMotion)) { context in
                    Canvas { graphics, size in
                        draw(&graphics, size: size, time: context.date.timeIntervalSinceReferenceDate, layout: layout)
                    }
                }
                ForEach(Array(nodes.enumerated()), id: \.element.id) { index, node in
                    badge(node, index: index, layout: layout)
                    label(node)
                        .position(x: layout.x(index), y: layout.labelY)
                }
                if let fastLane {
                    laneBadge(fastLane, layout: layout)
                }
                if let hoveredID, let node = everyNode.first(where: { $0.id == hoveredID }) {
                    hoverCard(node, layout: layout)
                }
            }
            .contentShape(Rectangle())
            .onContinuousHover { phase in
                switch phase {
                case .active(let point):
                    let id = hit(point, layout: layout)
                    if id != hoveredID { hoveredID = id }
                case .ended:
                    hoveredID = nil
                }
            }
            .onTapGesture { point in
                guard let id = hit(point, layout: layout), let node = everyNode.first(where: { $0.id == id }) else { return }
                onSelect(node)
            }
            .pointerStyle(hoveredID == nil ? .default : .link)
        }
        .animation(.snappy(duration: 0.22), value: hoveredID)
        .animation(.smooth(duration: 0.5), value: mode)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Route diagram"))
        .accessibilityValue(Text(verbatim: nodes.map(\.title).joined(separator: " → ")))
    }

    // MARK: Hit testing

    private func hit(_ point: CGPoint, layout: FlowLayout) -> String? {
        if let index = layout.nearestNode(to: point) { return nodes[index].id }
        if let fastLane, layout.isNearLane(point) { return fastLane.id }
        return nil
    }

    private func isLit(_ node: FlowNode, index: Int) -> Bool {
        switch mode {
        case .connected: return true
        case .connecting(let progress): return Double(index) <= progress * Double(max(1, nodes.count - 1)) + 0.001
        case .turbo: return node.kind == .mac || node.kind == .internet
        case .blocked: return node.kind == .mac
        case .idle, .failed: return false
        }
    }

    // MARK: SwiftUI layers

    private func badge(_ node: FlowNode, index: Int, layout: FlowLayout) -> some View {
        let lit = isLit(node, index: index)
        let hovered = hoveredID == node.id
        return ZStack {
            if let flag = node.flag, !flag.isEmpty {
                Text(verbatim: flag)
                    .font(.system(size: 26))
            } else {
                Image(systemName: node.symbol)
                    .font(.system(size: 21, weight: .medium))
                    .foregroundStyle(lit ? AnyShapeStyle(node.accent) : AnyShapeStyle(.secondary))
            }
        }
        .frame(width: layout.radius * 2, height: layout.radius * 2)
        .glassEffect(.regular.tint(lit ? node.accent.opacity(0.28) : nil).interactive(), in: .circle)
        .overlay {
            Circle()
                .strokeBorder(lit ? node.accent.opacity(0.8) : Color.primary.opacity(0.12), lineWidth: hovered ? 2.5 : 1.5)
        }
        .shadow(color: lit ? node.accent.opacity(hovered ? 0.7 : 0.45) : .clear, radius: hovered ? 16 : 10)
        .scaleEffect(hovered ? 1.12 : 1)
        .position(layout.center(index))
    }

    private func label(_ node: FlowNode) -> some View {
        VStack(spacing: 1) {
            Text(verbatim: node.title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
            Text(verbatim: node.subtitle ?? " ")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .multilineTextAlignment(.center)
        .frame(width: 118)
    }

    private func laneBadge(_ node: FlowNode, layout: FlowLayout) -> some View {
        let hovered = hoveredID == node.id
        return HStack(spacing: 6) {
            Image(systemName: node.symbol)
            Text(verbatim: node.title)
            if let subtitle = node.subtitle {
                Text(verbatim: subtitle)
                    .foregroundStyle(.secondary)
            }
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(node.accent)
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .glassEffect(.regular.tint(node.accent.opacity(0.2)).interactive(), in: .capsule)
        .scaleEffect(hovered ? 1.06 : 1)
        .position(layout.laneTop)
    }

    private func hoverCard(_ node: FlowNode, layout: FlowLayout) -> some View {
        let anchorX: CGFloat
        let y: CGFloat
        if node.kind == .youtube {
            anchorX = layout.laneTop.x
            y = layout.laneTop.y + 40
        } else {
            anchorX = nodes.firstIndex(of: node).map { layout.x($0) } ?? layout.size.width / 2
            y = layout.railY - layout.radius - 36
        }
        let x = min(max(anchorX, 112), layout.size.width - 112)
        return VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: node.title)
                .font(.caption.weight(.semibold))
            ForEach(Array(node.details.enumerated()), id: \.offset) { _, line in
                Text(verbatim: line)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .frame(width: 216, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 12))
        .position(x: x, y: y)
        .transition(.opacity.combined(with: .scale(scale: 0.94)))
        .allowsHitTesting(false)
    }

    // MARK: Canvas

    private func draw(_ context: inout GraphicsContext, size: CGSize, time: TimeInterval, layout: FlowLayout) {
        guard nodes.count > 1 else { return }
        let dashed = StrokeStyle(lineWidth: 2, lineCap: .round, dash: [3, 8])
        let segments = nodes.count - 1
        for index in 0..<segments {
            let start = layout.center(index)
            let end = layout.center(index + 1)
            let a = CGPoint(x: start.x + layout.radius + 7, y: start.y)
            let b = CGPoint(x: end.x - layout.radius - 7, y: end.y)
            var rail = Path()
            rail.move(to: a)
            rail.addLine(to: b)
            switch mode {
            case .idle, .turbo:
                context.stroke(rail, with: .color(.primary.opacity(0.16)), style: dashed)
            case .failed:
                context.stroke(rail, with: .color(.red.opacity(0.4)), style: dashed)
            case .blocked:
                context.stroke(rail, with: .color(.red.opacity(index == 0 ? 0.5 : 0.2)), style: dashed)
            case .connecting(let progress):
                context.stroke(rail, with: .color(.primary.opacity(0.14)), style: dashed)
                let head = progress * Double(segments)
                let fill = min(1, max(0, head - Double(index)))
                if fill > 0 {
                    let tip = CGPoint(x: a.x + (b.x - a.x) * CGFloat(fill), y: a.y)
                    var done = Path()
                    done.move(to: a)
                    done.addLine(to: tip)
                    context.stroke(
                        done,
                        with: .linearGradient(Gradient(colors: [.orange.opacity(0.35), .orange]), startPoint: a, endPoint: tip),
                        style: StrokeStyle(lineWidth: 3, lineCap: .round)
                    )
                    if fill < 1 {
                        let pulse = 1 + 0.3 * sin(time * 6)
                        let halo = CGFloat(10 * pulse)
                        context.fill(Path(ellipseIn: CGRect(x: tip.x - halo, y: tip.y - halo, width: halo * 2, height: halo * 2)), with: .color(.orange.opacity(0.22)))
                        context.fill(Path(ellipseIn: CGRect(x: tip.x - 4, y: tip.y - 4, width: 8, height: 8)), with: .color(.orange))
                    }
                }
            case .connected:
                context.stroke(
                    rail,
                    with: .linearGradient(Gradient(colors: [nodes[index].accent.opacity(0.55), nodes[index + 1].accent.opacity(0.55)]), startPoint: a, endPoint: b),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round)
                )
                drawParticles(&context, from: a, to: b, segment: index, time: time)
            }
        }
        if mode == .blocked {
            drawBarrier(&context, layout: layout, time: time)
        }
        if fastLane != nil {
            drawFastLane(&context, layout: layout, time: time)
        }
    }

    private func drawParticles(_ context: inout GraphicsContext, from a: CGPoint, to b: CGPoint, segment: Int, time: TimeInterval) {
        let length = b.x - a.x
        guard length > 20 else { return }
        func dot(progress: Double, lane: Int, towardMac: Bool, color: Color, radius: CGFloat) {
            let t = towardMac ? 1 - progress : progress
            let x = a.x + length * CGFloat(t)
            let spread = CGFloat(3 + lane * 2)
            let y = a.y + (towardMac ? spread : -spread)
            let halo = radius * 2.4
            context.fill(Path(ellipseIn: CGRect(x: x - halo, y: y - halo, width: halo * 2, height: halo * 2)), with: .color(color.opacity(0.16)))
            context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
        }
        let downSpeed = Self.speed(for: downloadRate)
        for lane in 0..<Self.lanes(for: downloadRate) {
            dot(progress: Self.progress(time: time, speed: downSpeed, lane: lane, segment: segment, seed: 0), lane: lane, towardMac: true, color: .cyan, radius: 3.2)
        }
        let upSpeed = Self.speed(for: uploadRate)
        for lane in 0..<Self.lanes(for: uploadRate) {
            dot(progress: Self.progress(time: time, speed: upSpeed, lane: lane, segment: segment, seed: 1), lane: lane, towardMac: false, color: .orange, radius: 3)
        }
        if paddingRate > 0 {
            let padSpeed = Self.speed(for: paddingRate) * 0.8
            for lane in 0..<2 {
                dot(progress: Self.progress(time: time, speed: padSpeed, lane: lane + 3, segment: segment, seed: 2), lane: lane + 3, towardMac: lane == 0, color: .purple, radius: 2.4)
            }
        }
    }

    private func drawBarrier(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval) {
        guard nodes.count > 1 else { return }
        let x = (layout.x(0) + layout.x(1)) / 2
        let y = layout.railY
        let pulse = 0.65 + 0.35 * (0.5 + 0.5 * sin(time * 3))
        let radius: CGFloat = 13
        context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(.red.opacity(pulse)))
        var cross = Path()
        cross.move(to: CGPoint(x: x - 6, y: y - 6))
        cross.addLine(to: CGPoint(x: x + 6, y: y + 6))
        cross.move(to: CGPoint(x: x + 6, y: y - 6))
        cross.addLine(to: CGPoint(x: x - 6, y: y + 6))
        context.stroke(cross, with: .color(.white), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
    }

    private func drawFastLane(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval) {
        var lane = Path()
        lane.move(to: layout.laneStart)
        lane.addQuadCurve(to: layout.laneEnd, control: layout.laneControl)
        context.stroke(lane, with: .color(.red.opacity(0.45)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [4, 6]))
        for index in 0..<3 {
            let progress = Self.progress(time: time, speed: 0.3, lane: index, segment: 0, seed: 3)
            let t = index == 1 ? 1 - progress : progress
            let point = layout.lanePoint(CGFloat(t))
            context.fill(Path(ellipseIn: CGRect(x: point.x - 7, y: point.y - 7, width: 14, height: 14)), with: .color(.red.opacity(0.16)))
            context.fill(Path(ellipseIn: CGRect(x: point.x - 3, y: point.y - 3, width: 6, height: 6)), with: .color(.red))
        }
    }

    // MARK: Particle maths

    /// How many particles a direction gets: one slow "keepalive" dot when idle, up to seven when busy.
    static func lanes(for rate: Double) -> Int {
        guard rate >= 512 else { return 1 }
        return min(7, 1 + Int(log2(1 + rate / 4096)))
    }

    /// Cycles per second along a segment.
    static func speed(for rate: Double) -> Double {
        0.22 + min(1.3, log10(1 + max(0, rate) / 1024) * 0.42)
    }

    static func progress(time: TimeInterval, speed: Double, lane: Int, segment: Int, seed: Int) -> Double {
        let offset = Double(lane) * 0.618034 + Double(segment) * 0.377 + Double(seed) * 0.213
        let value = (time * speed + offset).truncatingRemainder(dividingBy: 1)
        return value < 0 ? value + 1 : value
    }
}
