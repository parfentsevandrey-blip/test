import AppKit
import SwiftUI

extension CatalogApp {
    var color: Color {
        guard accent.count == 3 else { return .accentColor }
        return Color(red: accent[0], green: accent[1], blue: accent[2])
    }
}

enum Format {
    static func bytes(_ v: Int64?) -> String {
        guard let v else { return "—" }
        return ByteCountFormatter.string(fromByteCount: v, countStyle: .file)
    }

    static func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let n10 = abs(n) % 10, n100 = abs(n) % 100
        if n10 == 1 && n100 != 11 { return one }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return few }
        return many
    }
}

/// Иконка приложения: из каталога, а пока грузится — стеклянная плитка с первой буквой в его цвете.
struct AppIconView: View {
    let app: CatalogApp
    var size: CGFloat = 64

    var body: some View {
        ZStack {
            if let local = DemoUI.icon(for: app) {
                Image(nsImage: local).resizable().interpolation(.high)
            } else if let url = app.icon {
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().interpolation(.high) } else { placeholder }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .shadow(color: app.color.opacity(0.35), radius: size * 0.12, y: size * 0.05)
    }

    private var placeholder: some View {
        RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)
            .fill(LinearGradient(colors: [app.color, app.color.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay {
                Text(String(app.name.prefix(1)))
                    .font(.system(size: size * 0.48, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
            }
            .padding(size * 0.08)
    }
}

/// Главная кнопка: установить, обновить, открыть — с кольцом прогресса во время работы.
struct ActionButton: View {
    @Environment(Store.self) private var store
    let app: CatalogApp
    var large = false

    var body: some View {
        let state = store.state(app)
        Group {
            switch state {
            case .notInstalled:
                button("Установить", tint: app.color) { Task { await store.install(app) } }
            case .update:
                button("Обновить", tint: app.color) { Task { await store.install(app) } }
            case .installed:
                if app.bundleID == Bundle.main.bundleIdentifier {
                    Text("Это вы").font(.system(size: large ? 14 : 12, weight: .semibold)).foregroundStyle(.secondary)
                        .padding(.horizontal, 14).padding(.vertical, 6)
                } else {
                    button("Открыть", tint: nil) { store.open(app) }
                }
            case .working(let label, let progress):
                HStack(spacing: 8) {
                    ProgressRing(progress: progress, tint: app.color)
                        .frame(width: large ? 20 : 16, height: large ? 20 : 16)
                    Text(progress.map { "\(label) \(Int($0 * 100))%" } ?? label)
                        .font(.system(size: large ? 14 : 12, weight: .semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                }
                .padding(.horizontal, 14)
                .padding(.vertical, large ? 9 : 6)
                .glassEffect(.regular, in: Capsule())
            case .comingSoon:
                Text("Скоро").font(.system(size: large ? 14 : 12, weight: .semibold)).foregroundStyle(.secondary)
                    .padding(.horizontal, 14).padding(.vertical, 6)
                    .glassEffect(.regular, in: Capsule())
            }
        }
        .animation(.smooth, value: state)
    }

    private func button(_ title: String, tint: Color?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: large ? 14 : 12, weight: .bold))
                .padding(.horizontal, large ? 22 : 12)
                .padding(.vertical, large ? 4 : 1)
        }
        .buttonStyle(.glassProminent)
        .tint(tint)
        .controlSize(large ? .large : .regular)
    }
}

struct ProgressRing: View {
    let progress: Double?
    let tint: Color
    @State private var spin = false

    var body: some View {
        ZStack {
            Circle().stroke(tint.opacity(0.2), lineWidth: 2.5)
            Circle()
                .trim(from: 0, to: progress ?? 0.28)
                .stroke(tint, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(progress == nil ? (spin ? 360 : 0) : -90))
                .animation(progress == nil ? .linear(duration: 0.9).repeatForever(autoreverses: false) : .smooth, value: spin)
                .animation(.smooth, value: progress)
        }
        .onAppear { spin = true }
    }
}

/// Мягкий фон окна: меш-градиент в цвете выбранного приложения.
struct HarborBackground: View {
    let tint: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 20, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            MeshGradient(width: 3, height: 3, points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [Float(0.5 + 0.12 * sin(t / 6)), Float(0.5 + 0.1 * cos(t / 7))], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ], colors: [
                tint.opacity(0.22), .clear, Color.blue.opacity(0.10),
                .clear, tint.opacity(0.14), .clear,
                Color.teal.opacity(0.10), .clear, tint.opacity(0.18),
            ])
        }
        .background(.background)
        .ignoresSafeArea()
        .animation(.smooth(duration: 0.8), value: tint)
    }
}

struct Toast: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "checkmark.circle.fill")
            .font(.system(size: 13, weight: .semibold))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glassEffect(.regular, in: Capsule())
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
