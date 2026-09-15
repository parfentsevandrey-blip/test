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
    let radius: CGFloat = 24

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

    /// The straight line under every node, optionally shifted up or down a little.
    func railPath(offsetY: CGFloat = 0) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x(0), y: railY + offsetY))
        path.addLine(to: CGPoint(x: x(count - 1), y: railY + offsetY))
        return path
    }

    /// A point along the rail, 0 at the Mac and 1 at the Internet.
    func railPoint(_ fraction: CGFloat, offsetY: CGFloat = 0) -> CGPoint {
        let clamped = min(1, max(0, fraction))
        return CGPoint(x: x(0) + (x(count - 1) - x(0)) * clamped, y: railY + offsetY)
    }

    var laneStart: CGPoint { CGPoint(x: x(0), y: railY - radius) }
    var laneEnd: CGPoint { CGPoint(x: x(count - 1), y: railY - radius) }
    /// Quadratic control point that makes the arc peak exactly at `laneTop`.
    var laneControl: CGPoint { CGPoint(x: size.width / 2, y: 2 * laneTop.y - (railY - radius)) }

    func lanePath() -> Path {
        var path = Path()
        path.move(to: laneStart)
        path.addQuadCurve(to: laneEnd, control: laneControl)
        return path
    }

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

/// The live route: Mac → bridge → relays → Internet. Traffic is drawn as a few soft comets of
/// light gliding along the rail — download toward the Mac, upload toward the Internet — whose
/// brightness and length follow the real throughput while their pace stays unhurried. Nodes light
/// up as Tor bootstraps, can be hovered for details and clicked to jump to the matching screen.
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
                TimelineView(.animation(minimumInterval: mode.isLive ? 1.0 / 24.0 : 1.0 / 10.0, paused: reduceMotion)) { context in
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
        .animation(.smooth(duration: 0.6), value: mode)
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
                    .font(.system(size: 24))
            } else {
                Image(systemName: node.symbol)
                    .font(.system(size: 19, weight: .regular))
                    .foregroundStyle(lit ? AnyShapeStyle(node.accent) : AnyShapeStyle(.secondary))
            }
        }
        .frame(width: layout.radius * 2, height: layout.radius * 2)
        .glassEffect(.regular.tint(lit ? node.accent.opacity(0.14) : nil).interactive(), in: .circle)
        .overlay {
            Circle()
                .strokeBorder(lit ? node.accent.opacity(0.55) : Color.primary.opacity(0.10), lineWidth: hovered ? 1.8 : 1)
        }
        .shadow(color: lit ? node.accent.opacity(hovered ? 0.5 : 0.26) : .clear, radius: hovered ? 14 : 9)
        .scaleEffect(hovered ? 1.08 : 1)
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
        .glassEffect(.regular.tint(node.accent.opacity(0.12)).interactive(), in: .capsule)
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
        let rail = layout.railPath()
        let dashed = StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [2, 7])
        switch mode {
        case .idle, .turbo:
            context.stroke(rail, with: .color(.primary.opacity(0.12)), style: dashed)
        case .failed:
            context.stroke(rail, with: .color(.red.opacity(0.35)), style: dashed)
        case .blocked:
            context.stroke(rail, with: .color(.red.opacity(0.22)), style: dashed)
            drawBarrier(&context, layout: layout, time: time)
        case .connecting(let progress):
            context.stroke(rail, with: .color(.primary.opacity(0.10)), style: dashed)
            drawBootstrap(&context, rail: rail, layout: layout, progress: progress, time: time)
        case .connected:
            drawConnectedRail(&context, rail: rail, layout: layout)
            drawComets(&context, layout: layout, time: time, rate: downloadRate, towardMac: true, color: .cyan, seed: 0)
            drawComets(&context, layout: layout, time: time, rate: uploadRate, towardMac: false, color: .orange, seed: 1)
            if paddingRate > 0 {
                drawShimmer(&context, rail: rail, time: time)
            }
            drawPing(&context, layout: layout, time: time)
        }
        if fastLane != nil {
            drawFastLane(&context, layout: layout, time: time)
        }
    }

    /// A thin line whose colour drifts from one node's accent to the next, resting on a soft glow.
    private func drawConnectedRail(_ context: inout GraphicsContext, rail: Path, layout: FlowLayout) {
        let stops = nodes.enumerated().map { index, node in
            Gradient.Stop(color: node.accent, location: CGFloat(index) / CGFloat(max(1, nodes.count - 1)))
        }
        let start = layout.railPoint(0)
        let end = layout.railPoint(1)
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.opacity = 0.22
            layer.stroke(rail, with: .linearGradient(Gradient(stops: stops), startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: 6, lineCap: .round))
        }
        context.drawLayer { layer in
            layer.opacity = 0.5
            layer.stroke(rail, with: .linearGradient(Gradient(stops: stops), startPoint: start, endPoint: end), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
    }

    /// The bootstrap: the rail fills up from the Mac with a warm gradient and a breathing head.
    private func drawBootstrap(_ context: inout GraphicsContext, rail: Path, layout: FlowLayout, progress: Double, time: TimeInterval) {
        let head = CGFloat(min(1, max(0.006, progress)))
        let done = rail.trimmedPath(from: 0, to: head)
        let start = layout.railPoint(0)
        let tip = layout.railPoint(head)
        let shading = GraphicsContext.Shading.linearGradient(Gradient(colors: [.orange.opacity(0.15), .orange]), startPoint: start, endPoint: tip)
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.opacity = 0.35
            layer.stroke(done, with: shading, style: StrokeStyle(lineWidth: 6, lineCap: .round))
        }
        context.stroke(done, with: shading, style: StrokeStyle(lineWidth: 2, lineCap: .round))
        if head < 1 {
            let pulse = CGFloat(0.7 + 0.3 * sin(time * 2.2))
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 4))
                let halo = 8 * pulse
                layer.fill(Path(ellipseIn: CGRect(x: tip.x - halo, y: tip.y - halo, width: halo * 2, height: halo * 2)), with: .color(.orange.opacity(0.55)))
            }
            context.fill(Path(ellipseIn: CGRect(x: tip.x - 2.5, y: tip.y - 2.5, width: 5, height: 5)), with: .color(.orange))
        }
    }

    /// Soft comets of light gliding along the rail. Download travels toward the Mac a hair below
    /// the line, upload toward the Internet a hair above it. Throughput sets how many, how long
    /// and how bright; the pace stays unhurried.
    private func drawComets(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval, rate: Double, towardMac: Bool, color: Color, seed: Int) {
        let offsetY: CGFloat = towardMac ? 2.5 : -2.5
        let path = layout.railPath(offsetY: offsetY)
        let count = Self.cometCount(for: rate)
        let length = CGFloat(Self.cometLength(for: rate))
        let speed = Self.cometSpeed(for: rate)
        let brightness = Self.cometBrightness(for: rate)
        for index in 0..<count {
            let travel = Self.progress(time: time, speed: speed, lane: index, segment: count, seed: seed)
            let headFraction = towardMac ? CGFloat(1 - travel) : CGFloat(travel)
            let tailFraction = towardMac ? min(1, headFraction + length) : max(0, headFraction - length)
            let from = min(headFraction, tailFraction)
            let to = max(headFraction, tailFraction)
            guard to - from > 0.002 else { continue }
            let comet = path.trimmedPath(from: from, to: to)
            let headPoint = layout.railPoint(headFraction, offsetY: offsetY)
            let tailPoint = layout.railPoint(tailFraction, offsetY: offsetY)
            let shading = GraphicsContext.Shading.linearGradient(
                Gradient(colors: [color.opacity(0), color.opacity(brightness)]),
                startPoint: tailPoint, endPoint: headPoint
            )
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 4))
                layer.opacity = 0.55
                layer.stroke(comet, with: shading, style: StrokeStyle(lineWidth: 6, lineCap: .round))
            }
            context.stroke(comet, with: shading, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
            context.fill(Path(ellipseIn: CGRect(x: headPoint.x - 2, y: headPoint.y - 2, width: 4, height: 4)), with: .color(color.opacity(brightness)))
        }
    }

    /// Traffic padding: a faint violet stipple drifting along the rail, the noise layered on the signal.
    private func drawShimmer(_ context: inout GraphicsContext, rail: Path, time: TimeInterval) {
        let phase = CGFloat((time * 6).truncatingRemainder(dividingBy: 14))
        let alpha = 0.22 + 0.10 * (0.5 + 0.5 * sin(time * 0.9))
        context.stroke(rail, with: .color(.purple.opacity(alpha)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round, dash: [1.5, 12.5], dashPhase: -phase))
    }

    /// A slow ripple leaving the Internet node every few seconds.
    private func drawPing(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval) {
        guard let last = nodes.last else { return }
        let period = 4.5
        let phase = CGFloat((time / period).truncatingRemainder(dividingBy: 1))
        let center = layout.center(nodes.count - 1)
        let radius = layout.radius + 2 + phase * 20
        let alpha = Double(1 - phase) * 0.3
        context.stroke(
            Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
            with: .color(last.accent.opacity(alpha)),
            lineWidth: 1
        )
    }

    private func drawBarrier(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval) {
        guard nodes.count > 1 else { return }
        let x = (layout.x(0) + layout.x(1)) / 2
        let y = layout.railY
        let pulse = 0.6 + 0.3 * (0.5 + 0.5 * sin(time * 2.4))
        let radius: CGFloat = 11
        context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(.red.opacity(pulse)))
        var cross = Path()
        cross.move(to: CGPoint(x: x - 5, y: y - 5))
        cross.addLine(to: CGPoint(x: x + 5, y: y + 5))
        cross.move(to: CGPoint(x: x + 5, y: y - 5))
        cross.addLine(to: CGPoint(x: x - 5, y: y + 5))
        context.stroke(cross, with: .color(.white), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }

    /// The YouTube bypass: a thin dashed arc whose dashes drift toward the Internet.
    private func drawFastLane(_ context: inout GraphicsContext, layout: FlowLayout, time: TimeInterval) {
        let lane = layout.lanePath()
        let phase = CGFloat((time * 7).truncatingRemainder(dividingBy: 11))
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 3))
            layer.opacity = 0.25
            layer.stroke(lane, with: .color(.red), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        }
        context.stroke(lane, with: .color(.red.opacity(0.45)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [3, 8], dashPhase: -phase))
    }

    // MARK: Comet maths

    /// How many comets a direction gets: one when quiet, three when busy.
    static func cometCount(for rate: Double) -> Int {
        if rate < 40_000 { return 1 }
        if rate < 800_000 { return 2 }
        return 3
    }

    /// Length of a comet as a fraction of the whole rail.
    static func cometLength(for rate: Double) -> Double {
        0.07 + min(0.10, log10(1 + max(0, rate) / 2048) * 0.028)
    }

    /// Full-rail traversals per second: roughly 18 s when idle, 9 s when busy.
    static func cometSpeed(for rate: Double) -> Double {
        0.055 + min(0.055, log10(1 + max(0, rate) / 4096) * 0.016)
    }

    static func cometBrightness(for rate: Double) -> Double {
        0.55 + min(0.45, log10(1 + max(0, rate) / 2048) * 0.13)
    }

    static func progress(time: TimeInterval, speed: Double, lane: Int, segment: Int, seed: Int) -> Double {
        let offset = Double(lane) / Double(max(1, segment)) + Double(seed) * 0.37
        let value = (time * speed + offset).truncatingRemainder(dividingBy: 1)
        return value < 0 ? value + 1 : value
    }
}
