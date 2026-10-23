import SwiftUI

/// Шкала времени: прогресс рисуется в такт дисплею (до 120 Гц на ProMotion), при наведении
/// шкала утолщается. Никаких превью — только время и метки глав.
struct Scrubber: View {
    let engine: PlayerEngine
    let tint: Color

    @State private var hovering = false
    @State private var dragFraction: Double?
    @State private var chapterIndex = -1

    private var expanded: Bool { hovering || dragFraction != nil }

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            let barHeight: CGFloat = expanded ? 8 : 5
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.16))

                TimelineView(.animation(paused: !engine.isPlaying || dragFraction != nil)) { _ in
                    Capsule()
                        .fill(LinearGradient(colors: [tint.mix(with: .white, by: 0.55), .white], startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(barHeight, CGFloat(progress()) * width))
                }

                if engine.duration > 0 {
                    ForEach(engine.chapters.dropFirst(), id: \.self) { chapter in
                        Rectangle()
                            .fill(.black.opacity(0.55))
                            .frame(width: 2)
                            .offset(x: CGFloat(chapter.start / engine.duration) * width - 1)
                    }
                }
            }
            .frame(height: barHeight)
            .clipShape(Capsule())
            .overlay(alignment: .leading) {
                if expanded {
                    TimelineView(.animation(paused: !engine.isPlaying || dragFraction != nil)) { _ in
                        Circle()
                            .fill(.white)
                            .frame(width: 13, height: 13)
                            .shadow(color: .black.opacity(0.35), radius: 3, y: 1)
                            .offset(x: CGFloat(progress()) * width - 6.5)
                    }
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .onHover { h in withAnimation(.smooth(duration: 0.2)) { hovering = h } }
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let fraction = Double(min(max(value.location.x / width, 0), 1))
                        dragFraction = fraction
                        engine.isScrubbing = true
                        engine.seek(to: fraction * engine.duration, precise: false)
                        trackChapter(fraction)
                    }
                    .onEnded { value in
                        let fraction = Double(min(max(value.location.x / width, 0), 1))
                        engine.isScrubbing = false
                        engine.seek(to: fraction * engine.duration, precise: true)
                        dragFraction = nil
                    }
            )
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: expanded)
        }
        .frame(height: 18)
        // Тактильный отклик трекпада Force Touch при переходе через границу главы.
        .sensoryFeedback(.alignment, trigger: chapterIndex)
    }

    private func progress() -> Double {
        if let dragFraction { return dragFraction }
        guard engine.duration > 0 else { return 0 }
        return min(max(engine.liveTime / engine.duration, 0), 1)
    }

    private func trackChapter(_ fraction: Double) {
        guard engine.chapters.count > 1 else { return }
        let time = fraction * engine.duration
        let index = engine.chapters.lastIndex { $0.start <= time } ?? 0
        if index != chapterIndex { chapterIndex = index }
    }
}
