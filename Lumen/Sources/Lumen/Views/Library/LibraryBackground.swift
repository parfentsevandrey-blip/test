import SwiftUI

/// Медленно «дышащий» меш-градиент: ночное небо, сквозь которое преломляется стекло.
/// Цвета подстраиваются под подсветку последнего просмотренного видео.
struct LibraryBackground: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let base: [Color] = [
        Color(red: 0.05, green: 0.06, blue: 0.16), Color(red: 0.12, green: 0.08, blue: 0.28), Color(red: 0.04, green: 0.14, blue: 0.24),
        Color(red: 0.20, green: 0.07, blue: 0.30), Color(red: 0.09, green: 0.12, blue: 0.30), Color(red: 0.03, green: 0.22, blue: 0.32),
        Color(red: 0.06, green: 0.04, blue: 0.16), Color(red: 0.16, green: 0.05, blue: 0.22), Color(red: 0.04, green: 0.09, blue: 0.18),
    ]

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { context in
            MeshGradient(
                width: 3, height: 3,
                points: Self.points(at: Float(context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600))),
                colors: colors,
                smoothsColors: true
            )
        }
        .overlay {
            // Лёгкая виньетка, чтобы стекло шапки читалось.
            LinearGradient(colors: [.black.opacity(0.35), .clear, .black.opacity(0.25)], startPoint: .top, endPoint: .bottom)
        }
        .ignoresSafeArea()
    }

    private static func points(at t: Float) -> [SIMD2<Float>] {
        let top = SIMD2<Float>(0.5 + 0.08 * sin(t * 0.21), 0)
        let left = SIMD2<Float>(0, 0.5 + 0.1 * cos(t * 0.17))
        let center = SIMD2<Float>(0.5 + 0.12 * sin(t * 0.13), 0.5 + 0.1 * cos(t * 0.19))
        let right = SIMD2<Float>(1, 0.5 + 0.1 * sin(t * 0.23))
        let bottom = SIMD2<Float>(0.5 + 0.08 * cos(t * 0.15), 1)
        return [SIMD2(0, 0), top, SIMD2(1, 0), left, center, right, SIMD2(0, 1), bottom, SIMD2(1, 1)]
    }

    private var colors: [Color] {
        let palette = model.engine.ambient.palette
        guard palette != .neutral else { return Self.base }
        return zip(Self.base, palette.swiftUIColors).map { $0.mix(with: $1, by: 0.35) }
    }
}
