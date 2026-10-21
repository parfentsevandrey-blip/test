import AppKit
import SwiftUI

/// Сегмент «солнечной» диаграммы.
struct SunSegment: Identifiable {
    var id: String { node.id }
    let node: SpaceNode
    let depth: Int
    let start: Double
    let end: Double
    let color: Color
}

/// «Карта диска»: интерактивная солнечная диаграмма — каждое кольцо — уровень вложенности.
struct SpaceLensView: View {
    @Environment(AppModel.self) private var model
    @State private var hovered: SunSegment?
    @State private var hoverPoint: CGPoint = .zero

    var body: some View {
        Group {
            switch model.spacePhase {
            case .idle:
                ModuleHero(module: .space, headline: "Куда ушло место",
                           text: "Интерактивная карта папок: кольца — уровни вложенности, размер дуги — доля места. Нажмите на сегмент, чтобы провалиться внутрь.",
                           button: "Построить карту дома") { model.scanSpace() }
            case .scanning(_, let label) where model.spaceRoot == nil:
                ModuleHero(module: .space, headline: "Куда ушло место", text: "Считаю занятое место, а не размер файлов — так честнее для сжатых и разреженных файлов.",
                           button: "", busy: label) {}
            default:
                content
            }
        }
    }

    private var current: SpaceNode? { model.spacePath.last }

    private var content: some View {
        VStack(spacing: 0) {
            ModuleHeader(title: "Карта диска", subtitle: "Свободно \(Format.bytes(model.disk.availableImportant)) из \(Format.bytes(model.disk.total)).") {
                HStack(spacing: 8) {
                    if case .scanning(_, let label) = model.spacePhase { Text(label).foregroundStyle(.secondary).font(.system(size: 11.5)) }
                    Button { chooseFolder() } label: { Label("Другая папка…", systemImage: "folder") }.buttonStyle(.glass)
                    Button { model.scanSpace(model.spacePath.first?.url) } label: { Label("Заново", systemImage: "arrow.clockwise") }.buttonStyle(.glass)
                }
            }
            breadcrumbs
            HStack(alignment: .top, spacing: 16) {
                if let node = current {
                    sunburst(node)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .id(node.id)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                    childList(node).frame(width: 360)
                }
            }
            .animation(.smooth(duration: 0.4), value: current?.id)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
    }

    private var breadcrumbs: some View {
        GlassEffectContainer(spacing: 4) {
            HStack(spacing: 4) {
                ForEach(Array(model.spacePath.enumerated()), id: \.element.id) { index, node in
                    if index > 0 { Image(systemName: "chevron.right").font(.system(size: 9, weight: .bold)).foregroundStyle(.tertiary) }
                    Button {
                        withAnimation(.smooth) { model.spacePath = Array(model.spacePath.prefix(index + 1)) }
                    } label: {
                        Text(index == 0 ? "⌂ \(node.name)" : node.name).font(.system(size: 12, weight: .semibold)).lineLimit(1)
                            .padding(.horizontal, 10).padding(.vertical, 5)
                    }
                    .buttonStyle(.plain)
                    .glassEffect(index == model.spacePath.count - 1 ? .regular.tint(Module.space.tint.opacity(0.4)).interactive() : .regular.interactive(), in: .capsule)
                }
                Spacer()
            }
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 10)
    }

    // MARK: Диаграмма

    private func segments(_ root: SpaceNode) -> [SunSegment] {
        var out: [SunSegment] = []
        let palette = Module.space.palette + [Color(red: 0.2, green: 0.8, blue: 0.75), Color(red: 1, green: 0.6, blue: 0.3), Color(red: 0.35, green: 0.7, blue: 1)]
        func walk(_ node: SpaceNode, depth: Int, start: Double, end: Double, base: Color?) {
            guard depth <= 4, node.size > 0 else { return }
            var a = start
            for (i, child) in node.children.enumerated() where child.size > 0 {
                let sweep = (end - start) * Double(child.size) / Double(max(1, node.size))
                guard sweep > 0.006 else { a += sweep; continue }
                let color = child.isRest ? Color.gray.opacity(0.5)
                    : (base ?? palette[i % palette.count]).mix(with: .white, by: Double(depth - 1) * 0.16)
                out.append(SunSegment(node: child, depth: depth, start: a, end: a + sweep, color: color))
                if child.isDirectory { walk(child, depth: depth + 1, start: a, end: a + sweep, base: base ?? palette[i % palette.count]) }
                a += sweep
            }
        }
        walk(root, depth: 1, start: -.pi / 2, end: 1.5 * .pi, base: nil)
        return out
    }

    private func sunburst(_ node: SpaceNode) -> some View {
        let segs = segments(node)
        return GeometryReader { geo in
            let size = geo.size
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let outer = min(size.width, size.height) * 0.48
            let inner = outer * 0.26
            let ring = (outer - inner) / 4
            ZStack {
                Canvas { g, _ in
                    for s in segs {
                        let r0 = inner + ring * CGFloat(s.depth - 1) + 1.5
                        let r1 = inner + ring * CGFloat(s.depth) - 1.5
                        var p = Path()
                        p.addArc(center: c, radius: r1, startAngle: .radians(s.start + 0.004), endAngle: .radians(s.end - 0.004), clockwise: false)
                        p.addArc(center: c, radius: r0, startAngle: .radians(s.end - 0.004), endAngle: .radians(s.start + 0.004), clockwise: true)
                        p.closeSubpath()
                        let hot = hovered?.id == s.id
                        g.fill(p, with: .radialGradient(Gradient(colors: [s.color.opacity(hot ? 1 : 0.82), s.color.mix(with: .black, by: 0.18)]),
                                                        center: c, startRadius: r0, endRadius: r1))
                        if hot {
                            var glow = g
                            glow.blendMode = .plusLighter
                            glow.stroke(p, with: .color(.white.opacity(0.9)), lineWidth: 2)
                        }
                        if s.node.locked {
                            g.stroke(p, with: .color(.white.opacity(0.5)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        }
                    }
                }
                // Центр — текущая папка; нажатие поднимает на уровень выше.
                Button {
                    if model.spacePath.count > 1 { withAnimation(.smooth) { _ = model.spacePath.popLast() } }
                } label: {
                    VStack(spacing: 2) {
                        Text(node.name).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        Text(Format.bytes(node.size)).font(.system(size: 20, weight: .bold, design: .rounded)).monospacedDigit()
                        if model.spacePath.count > 1 { Text("↑ наверх").font(.system(size: 10)).foregroundStyle(.secondary) }
                    }
                    .frame(width: inner * 1.7, height: inner * 1.7)
                    .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .glassEffect(.regular.interactive(), in: .circle)
                .position(c)

                if let h = hovered {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(h.node.name).font(.system(size: 12.5, weight: .semibold)).lineLimit(1)
                        Text("\(Format.bytes(h.node.size)) · \(percent(h.node.size, of: node.size))").font(.system(size: 11)).monospacedDigit().foregroundStyle(.secondary)
                        if h.node.locked { Text("Нужен доступ — откройте папку в Finder").font(.system(size: 10.5)).foregroundStyle(.orange) }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .fixedSize()
                    .position(x: min(max(hoverPoint.x, 100), size.width - 100), y: max(30, hoverPoint.y - 36))
                    .allowsHitTesting(false)
                }
            }
            .contentShape(Rectangle())
            .onContinuousHover { phase in
                switch phase {
                case .active(let p):
                    hoverPoint = p
                    hovered = hit(p, c: c, inner: inner, ring: ring, segs: segs)
                case .ended:
                    hovered = nil
                }
            }
            .onTapGesture { location in
                if let s = hit(location, c: c, inner: inner, ring: ring, segs: segs), s.node.isDirectory, !s.node.children.isEmpty {
                    withAnimation(.smooth) { model.spacePath.append(s.node) }
                    hovered = nil
                }
            }
        }
    }

    private func hit(_ p: CGPoint, c: CGPoint, inner: CGFloat, ring: CGFloat, segs: [SunSegment]) -> SunSegment? {
        let dx = p.x - c.x, dy = p.y - c.y
        let r = hypot(dx, dy)
        guard r > inner else { return nil }
        let depth = Int((r - inner) / ring) + 1
        var a = atan2(dy, dx)
        if a < -.pi / 2 { a += 2 * .pi }
        return segs.first { $0.depth == depth && a >= $0.start && a < $0.end }
    }

    private func percent(_ part: Int64, of total: Int64) -> String {
        total > 0 ? String(format: "%.1f%%", Double(part) / Double(total) * 100) : "—"
    }

    // MARK: Список

    private func childList(_ node: SpaceNode) -> some View {
        ContentCard {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(node.children) { child in
                        HStack(spacing: 10) {
                            FileIcon(url: child.isRest ? nil : child.url, symbol: child.isDirectory ? "folder.fill" : "doc.fill", size: 26)
                            VStack(alignment: .leading, spacing: 3) {
                                HStack {
                                    Text(child.name).font(.system(size: 12, weight: .medium)).lineLimit(1).truncationMode(.middle)
                                    if child.locked { Image(systemName: "lock.fill").font(.system(size: 9)).foregroundStyle(.orange) }
                                }
                                GeometryReader { g in
                                    Capsule().fill(Module.space.tint.gradient)
                                        .frame(width: max(2, g.size.width * Double(child.size) / Double(max(1, node.size))))
                                }
                                .frame(height: 4)
                            }
                            Text(Format.bytes(child.size)).font(.system(size: 11.5, weight: .semibold)).monospacedDigit().frame(width: 72, alignment: .trailing)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if child.isDirectory, !child.children.isEmpty { withAnimation(.smooth) { model.spacePath.append(child) } }
                        }
                        .contextMenu {
                            if !child.isRest {
                                Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([child.url]) }
                                Button("В Корзину…") { model.trashSpaceNode(child) }
                                Button("Никогда не трогать") { model.ignore(child.url) }
                            }
                        }
                        Divider().opacity(0.3).padding(.leading, 48)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.directoryURL = model.home
        panel.prompt = "Построить карту"
        if panel.runModal() == .OK, let url = panel.url { model.scanSpace(url) }
    }
}
