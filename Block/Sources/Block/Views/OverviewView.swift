import SwiftUI

struct OverviewView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                Constellation()
                    .frame(height: 430)
                if !model.drift.isEmpty { driftBanner }
                tiles
                profiles
            }
            .padding(28)
            .frame(maxWidth: 1040)
            .frame(maxWidth: .infinity)
        }
    }

    private var driftBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.uturn.backward.circle.fill").font(.system(size: 22)).foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 2) {
                Text("Apple вернула своё: \(model.drift.count) \(model.plural(model.drift.count))").font(.system(size: 13, weight: .semibold))
                Text(model.drift.compactMap { Catalog.rule($0)?.title }.prefix(3).joined(separator: " · ")).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Button("Применить заново") { Task { await model.reapply(model.drift.compactMap(Catalog.rule)) } }.buttonStyle(.glassProminent)
        }
        .padding(14)
        .glassEffect(.regular.tint(.orange.opacity(0.15)), in: .rect(cornerRadius: 18))
    }

    private var tiles: some View {
        let applied = model.rules.filter { model.status(of: $0).isBlocked }.count
        let services = Catalog.services.filter { model.status(of: $0) == .blocked }.count
        var hosts = 0
        for rule in Catalog.network where model.status(of: rule) == .blocked {
            if case .network(let g) = rule.action { hosts += g.hosts.count }
        }
        return HStack(spacing: 14) {
            Tile(value: "\(model.stoppedTotal)", title: "запусков остановлено", symbol: "hand.raised.fill", tint: .orange)
            Tile(value: "\(applied)", title: "правил действует", symbol: "checkmark.seal.fill", tint: .green)
            Tile(value: "\(services)", title: "служб остановлено", symbol: "gearshape.2.fill", tint: .purple)
            Tile(value: "\(hosts)", title: "адресов Apple закрыто", symbol: "network.slash", tint: .teal)
        }
    }

    private var profiles: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Профиль").font(.system(size: 17, weight: .bold, design: .rounded))
            HStack(alignment: .top, spacing: 14) {
                ForEach([Profile.gentle, .full]) { profile in
                    ProfileCard(profile: profile)
                }
            }
            Text("Всё обратимо: каждое изменение записано в журнал вместе с прежним значением. «Вернуть Mac как было» — в меню журнала.")
                .font(.system(size: 11)).foregroundStyle(.secondary)
        }
    }
}

struct Tile: View {
    let value: String
    let title: String
    let symbol: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: symbol).font(.system(size: 15, weight: .semibold)).foregroundStyle(tint)
            Text(value).font(.system(size: 26, weight: .bold, design: .rounded)).contentTransition(.numericText())
            Text(title).font(.system(size: 11)).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

struct ProfileCard: View {
    @Environment(AppModel.self) private var model
    let profile: Profile

    var body: some View {
        let changes = model.preview(for: profile)
        let total = model.selection(for: profile).count
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(profile.title).font(.system(size: 17, weight: .bold, design: .rounded))
                Spacer()
                Text("\(total - changes.count) из \(total)").font(.system(size: 12, weight: .semibold).monospacedDigit()).foregroundStyle(.secondary)
            }
            Text(profile.subtitle).font(.system(size: 12)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 6) {
                ForEach(Level.allCases.filter { profile.levels.contains($0) }) { level in
                    Image(systemName: level.symbol).font(.system(size: 11)).foregroundStyle(.secondary).help(level.title)
                }
                Spacer()
                Button(changes.isEmpty ? "Всё сделано" : "Применить: \(changes.count)") { Task { await model.applyProfile(profile) } }
                    .buttonStyle(.glassProminent)
                    .disabled(changes.isEmpty || model.busy)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(model.state.profile == profile ? .regular.tint(.indigo.opacity(0.18)) : .regular, in: .rect(cornerRadius: 20))
    }
}

/// «Созвездие Apple»: сервисы — светящиеся точки вокруг Mac. Выключенный сервис гаснет и уходит
/// на дальнюю орбиту, а рядом загорается его замена из каталога.
struct Constellation: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let near = min(size.width, size.height) * 0.30
            let far = min(size.width * 0.46, size.height * 0.49)
            TimelineView(.animation(minimumInterval: 1 / 30, paused: DemoUI.isOn)) { context in
                let t = DemoUI.isOn ? 0 : context.date.timeIntervalSinceReferenceDate
                ZStack {
                    orbits(center: center, near: near, far: far)
                    ForEach(Array(Surface.allCases.enumerated()), id: \.element) { i, surface in
                        let f = model.surfaceBlocked(surface)
                        let base = Double(i) / Double(Surface.allCases.count) * 2 * .pi
                        let angle = base + t * (0.035 + 0.02 * f)
                        let r = near + (far - near) * f
                        let p = CGPoint(x: center.x + cos(angle) * r, y: center.y + sin(angle) * r * 0.82)
                        Star(surface: surface, blocked: f)
                            .position(p)
                        if let replacement = surface.replacement, f > 0.5, model.installed(replacement) || DemoUI.isOn {
                            let rp = CGPoint(x: center.x + cos(angle) * (r - 46), y: center.y + sin(angle) * (r - 46) * 0.82)
                            ReplacementGlow(app: replacement).position(rp)
                        }
                    }
                    Core(score: model.score).position(center)
                }
            }
        }
        .animation(.smooth(duration: 0.8), value: model.score)
    }

    private func orbits(center: CGPoint, near: CGFloat, far: CGFloat) -> some View {
        ZStack {
            Ellipse().stroke(.primary.opacity(0.07), lineWidth: 1)
                .frame(width: near * 2, height: near * 2 * 0.82).position(center)
            Ellipse().stroke(style: StrokeStyle(lineWidth: 1, dash: [3, 6])).foregroundStyle(.primary.opacity(0.08))
                .frame(width: far * 2, height: far * 2 * 0.82).position(center)
        }
    }
}

private struct Star: View {
    let surface: Surface
    let blocked: Double

    var body: some View {
        let on = blocked < 0.5
        VStack(spacing: 4) {
            Image(systemName: surface.symbol)
                .font(.system(size: on ? 16 : 12, weight: .semibold))
                .foregroundStyle(on ? AnyShapeStyle(.white) : AnyShapeStyle(.secondary))
                .frame(width: on ? 38 : 28, height: on ? 38 : 28)
                .background {
                    Circle().fill(on ? AnyShapeStyle(Color.pink.gradient) : AnyShapeStyle(.quaternary))
                        .shadow(color: on ? .pink.opacity(0.7) : .clear, radius: 12)
                }
            Text(surface.title)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(on ? .primary : .tertiary)
                .lineLimit(1)
                .fixedSize()
        }
        .opacity(0.45 + 0.55 * (1 - blocked * 0.6))
    }
}

private struct ReplacementGlow: View {
    let app: CatalogApp

    var body: some View {
        Image(systemName: app.symbol)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 24, height: 24)
            .background(Circle().fill(Color.green.gradient).shadow(color: .green.opacity(0.8), radius: 10))
            .help("\(app.name) — \(app.role)")
    }
}

private struct Core: View {
    let score: Double

    var body: some View {
        ZStack {
            Circle().stroke(.primary.opacity(0.08), lineWidth: 12).frame(width: 168, height: 168)
            Circle()
                .trim(from: 0, to: score)
                .stroke(AngularGradient(colors: [.indigo, .teal, .green, .indigo], center: .center), style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: 168, height: 168)
                .shadow(color: .teal.opacity(0.5), radius: 10)
            VStack(spacing: 2) {
                Image(systemName: "laptopcomputer").font(.system(size: 30, weight: .light))
                Text("\(Int((score * 100).rounded())) %").font(.system(size: 30, weight: .bold, design: .rounded)).contentTransition(.numericText())
                Text("Mac принадлежит вам").font(.system(size: 10, weight: .medium)).foregroundStyle(.secondary)
            }
        }
        .frame(width: 190, height: 190)
        .glassEffect(.regular, in: .circle)
    }
}
