import AppKit
import SwiftUI

enum Format {
    static func bytes(_ v: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: v, countStyle: .file)
    }

    static func rate(_ bps: Double) -> String {
        guard bps >= 1 else { return "0 КБ/с" }
        if bps >= 1_000_000 { return String(format: "%.1f МБ/с", bps / 1_000_000) }
        return String(format: "%.0f КБ/с", bps / 1000)
    }

    static func eta(_ seconds: TimeInterval?) -> String? {
        guard let s = seconds, s.isFinite, s > 0 else { return nil }
        let total = Int(s)
        if total >= 86_400 { return "\(total / 86_400) д \(total % 86_400 / 3600) ч" }
        if total >= 3600 { return "\(total / 3600) ч \(total % 3600 / 60) мин" }
        if total >= 60 { return "\(total / 60) мин" }
        return "\(total) с"
    }

    static func percent(_ p: Double) -> String {
        p >= 1 ? "100%" : String(format: p < 0.1 ? "%.1f%%" : "%.0f%%", p * 100)
    }
}

/// Тип содержимого раздачи — для иконки и цвета карточки.
enum ContentKind {
    case video, audio, image, archive, app, book, other

    init(_ t: TorrentSnapshot) {
        let ext = (t.files.max { $0.size < $1.size }?.path ?? t.name).split(separator: ".").last.map { $0.lowercased() } ?? ""
        switch ext {
        case "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "wmv", "flv": self = .video
        case "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "alac", "ape": self = .audio
        case "jpg", "jpeg", "png", "heic", "gif", "webp", "tiff", "raw": self = .image
        case "zip", "rar", "7z", "tar", "gz", "xz", "bz2": self = .archive
        case "iso", "dmg", "pkg", "img", "app", "exe": self = .app
        case "pdf", "epub", "fb2", "djvu", "mobi": self = .book
        default: self = .other
        }
    }

    var symbol: String {
        switch self {
        case .video: "film.fill"
        case .audio: "music.note"
        case .image: "photo.fill"
        case .archive: "archivebox.fill"
        case .app: "opticaldiscdrive.fill"
        case .book: "book.fill"
        case .other: "doc.fill"
        }
    }

    var colors: [Color] {
        switch self {
        case .video: [Color(red: 0.98, green: 0.36, blue: 0.42), Color(red: 0.62, green: 0.20, blue: 0.86)]
        case .audio: [Color(red: 1.0, green: 0.55, blue: 0.25), Color(red: 0.95, green: 0.25, blue: 0.45)]
        case .image: [Color(red: 0.30, green: 0.80, blue: 0.55), Color(red: 0.10, green: 0.55, blue: 0.75)]
        case .archive: [Color(red: 0.95, green: 0.72, blue: 0.25), Color(red: 0.85, green: 0.45, blue: 0.20)]
        case .app: [Color(red: 0.35, green: 0.60, blue: 1.0), Color(red: 0.30, green: 0.30, blue: 0.85)]
        case .book: [Color(red: 0.55, green: 0.45, blue: 0.95), Color(red: 0.35, green: 0.25, blue: 0.70)]
        case .other: [Color(red: 0.55, green: 0.60, blue: 0.70), Color(red: 0.35, green: 0.40, blue: 0.50)]
        }
    }
}

struct KindIcon: View {
    let kind: ContentKind
    var size: CGFloat = 46

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
            .fill(LinearGradient(colors: kind.colors, startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay {
                Image(systemName: kind.symbol)
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
            }
            .overlay {
                RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                    .strokeBorder(.white.opacity(0.35), lineWidth: 0.8)
            }
            .frame(width: size, height: size)
            .shadow(color: kind.colors[0].opacity(0.35), radius: 8, y: 3)
    }
}

extension TorrentStatus {
    var color: Color {
        switch self {
        case .downloading: Color(red: 0.25, green: 0.55, blue: 1.0)
        case .metadata, .checking: Color(red: 0.55, green: 0.45, blue: 0.95)
        case .seeding: Color(red: 0.15, green: 0.78, blue: 0.62)
        case .paused, .finished: .secondary
        case .error: .red
        }
    }

    var symbol: String {
        switch self {
        case .downloading: "arrow.down.circle.fill"
        case .metadata: "magnifyingglass.circle.fill"
        case .checking: "checkmark.shield.fill"
        case .seeding: "arrow.up.circle.fill"
        case .paused: "pause.circle.fill"
        case .finished: "checkmark.circle.fill"
        case .error: "exclamationmark.triangle.fill"
        }
    }
}

/// «Жидкий» индикатор прогресса: при загрузке по заливке бегут блики,
/// а передний край светится — видно, что данные текут прямо сейчас.
struct LiquidProgress: View {
    let progress: Double
    let tint: Color
    var active = false
    var height: CGFloat = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width * min(max(progress, 0), 1)
            ZStack(alignment: .leading) {
                Capsule().fill(.primary.opacity(0.08))
                TimelineView(.animation(minimumInterval: 1 / 60, paused: !active || reduceMotion)) { ctx in
                    let t = ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 100)
                    Canvas { g, size in
                        guard w > 0 else { return }
                        let rect = CGRect(x: 0, y: 0, width: w, height: size.height)
                        let shape = Path(roundedRect: rect, cornerRadius: size.height / 2)
                        g.fill(shape, with: .linearGradient(Gradient(colors: [tint.opacity(0.75), tint]),
                                                            startPoint: .zero, endPoint: CGPoint(x: w, y: 0)))
                        guard active else { return }
                        g.clip(to: shape)
                        // Бегущие блики.
                        let spacing: CGFloat = 26
                        let offset = CGFloat(t * 40).truncatingRemainder(dividingBy: spacing)
                        var x = -spacing + offset
                        while x < w {
                            var stripe = Path()
                            stripe.move(to: CGPoint(x: x, y: size.height))
                            stripe.addLine(to: CGPoint(x: x + 8, y: 0))
                            stripe.addLine(to: CGPoint(x: x + 16, y: 0))
                            stripe.addLine(to: CGPoint(x: x + 8, y: size.height))
                            g.fill(stripe, with: .color(.white.opacity(0.16)))
                            x += spacing
                        }
                        // Свечение переднего края.
                        let pulse = 0.55 + 0.45 * sin(t * 4)
                        g.fill(Path(ellipseIn: CGRect(x: w - 10, y: -2, width: 14, height: size.height + 4)),
                               with: .radialGradient(Gradient(colors: [.white.opacity(0.8 * pulse), .clear]),
                                                     center: CGPoint(x: w - 3, y: size.height / 2), startRadius: 0, endRadius: 9))
                    }
                }
            }
        }
        .frame(height: height)
        .animation(.smooth(duration: 0.6), value: progress)
    }
}

/// Сияние на фоне окна. Чем выше суммарная скорость, тем ярче и быстрее оно движется.
struct AuroraBackground: View {
    let activity: Double

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { ctx in
            let t = Float(ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600)) * Float(0.6 + activity * 1.6)
            MeshGradient(width: 3, height: 3, points: Self.points(t), colors: colors, smoothsColors: true)
        }
        .ignoresSafeArea()
        .animation(.smooth(duration: 2), value: activity)
    }

    private var colors: [Color] {
        let a = activity
        if scheme == .dark {
            return [
                Color(red: 0.04, green: 0.06, blue: 0.12), Color(red: 0.05, green: 0.10 + 0.10 * a, blue: 0.22), Color(red: 0.03, green: 0.07, blue: 0.14),
                Color(red: 0.03, green: 0.14 + 0.18 * a, blue: 0.20 + 0.10 * a), Color(red: 0.10 + 0.12 * a, green: 0.08, blue: 0.26 + 0.12 * a), Color(red: 0.03, green: 0.18 + 0.20 * a, blue: 0.24),
                Color(red: 0.03, green: 0.05, blue: 0.10), Color(red: 0.08, green: 0.06, blue: 0.18), Color(red: 0.03, green: 0.08, blue: 0.13),
            ]
        }
        return [
            Color(red: 0.93, green: 0.95, blue: 0.99), Color(red: 0.86, green: 0.92 - 0.04 * a, blue: 1.0), Color(red: 0.94, green: 0.96, blue: 0.99),
            Color(red: 0.84, green: 0.96, blue: 0.94 - 0.04 * a), Color(red: 0.90 - 0.06 * a, green: 0.88, blue: 1.0), Color(red: 0.84, green: 0.95, blue: 0.97),
            Color(red: 0.95, green: 0.96, blue: 0.99), Color(red: 0.92, green: 0.90, blue: 0.99), Color(red: 0.94, green: 0.97, blue: 0.98),
        ]
    }

    private static func points(_ t: Float) -> [SIMD2<Float>] {
        let top = SIMD2<Float>(0.5 + 0.12 * sin(t * 0.13), 0)
        let left = SIMD2<Float>(0, 0.5 + 0.12 * cos(t * 0.11))
        let center = SIMD2<Float>(0.5 + 0.15 * sin(t * 0.09), 0.5 + 0.12 * cos(t * 0.12))
        let right = SIMD2<Float>(1, 0.5 + 0.12 * sin(t * 0.15))
        let bottom = SIMD2<Float>(0.5 + 0.12 * cos(t * 0.1), 1)
        return [SIMD2(0, 0), top, SIMD2(1, 0), left, center, right, SIMD2(0, 1), bottom, SIMD2(1, 1)]
    }
}

/// Капсула-чип фильтра с числом.
struct FilterChip: View {
    let title: String
    let count: Int
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title)
                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Capsule().fill(.primary.opacity(0.12)))
                        .contentTransition(.numericText())
                }
            }
            .font(.system(size: 12.5, weight: .semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .glassEffect(selected ? .regular.tint(.accentColor.opacity(0.45)).interactive() : .regular.interactive(), in: .capsule)
    }
}

struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> Probe {
        let v = Probe()
        v.onWindow = onWindow
        return v
    }

    func updateNSView(_ nsView: Probe, context: Context) {}

    final class Probe: NSView {
        var onWindow: ((NSWindow) -> Void)?
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { onWindow?(window) }
        }
    }
}
