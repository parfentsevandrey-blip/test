import SwiftUI

/// Где сейчас курсор над шкалой (для превью кадра над островом).
struct ScrubHover: Equatable {
    var fraction: Double
    /// Координата X в пространстве «island».
    var islandX: CGFloat
}

/// Шкала времени: прогресс рисуется в такт дисплею (до 120 Гц на ProMotion),
/// при наведении шкала утолщается, а над островом всплывает превью кадра.
struct Scrubber: View {
    let engine: PlayerEngine
    let tint: Color
    @Binding var hover: ScrubHover?

    @State private var dragFraction: Double?
    @State private var chapterIndex = -1

    private var expanded: Bool { hover != nil || dragFraction != nil }

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            let barHeight: CGFloat = expanded ? 9 : 5
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.16))

                if let a = engine.loopA, engine.duration > 0 {
                    let b = engine.loopB ?? engine.currentTime
                    Capsule()
                        .fill(Color.yellow.opacity(0.35))
                        .frame(width: max(2, CGFloat((b - a) / engine.duration) * width))
                        .offset(x: CGFloat(a / engine.duration) * width)
                }

                if let h = hover, dragFraction == nil {
                    Capsule().fill(.white.opacity(0.14)).frame(width: CGFloat(h.fraction) * width)
                }

                TimelineView(.animation(paused: !engine.isPlaying || dragFraction != nil)) { _ in
                    let fraction = progress()
                    Capsule()
                        .fill(LinearGradient(colors: [tint.mix(with: .white, by: 0.55), .white], startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(barHeight, CGFloat(fraction) * width))
                        .shadow(color: .white.opacity(expanded ? 0.55 : 0.25), radius: expanded ? 8 : 3)
                }

                ForEach(engine.chapters.dropFirst(), id: \.self) { chapter in
                    if engine.duration > 0 {
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
                    Circle()
                        .fill(.white)
                        .frame(width: 15, height: 15)
                        .shadow(color: .black.opacity(0.35), radius: 4, y: 1)
                        .offset(x: CGFloat(dragFraction ?? hover?.fraction ?? progress()) * width - 7.5)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .onContinuousHover(coordinateSpace: .local) { phase in
                switch phase {
                case .active(let point):
                    let fraction = Double(min(max(point.x / width, 0), 1))
                    let islandX = geo.frame(in: .named("island")).minX + point.x
                    withAnimation(.smooth(duration: 0.2)) { hover = ScrubHover(fraction: fraction, islandX: islandX) }
                    engine.previewer.request(fraction * engine.duration)
                    trackChapter(fraction)
                case .ended:
                    if dragFraction == nil { withAnimation(.smooth(duration: 0.25)) { hover = nil } }
                }
            }
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let fraction = Double(min(max(value.location.x / width, 0), 1))
                        dragFraction = fraction
                        engine.isScrubbing = true
                        engine.seek(to: fraction * engine.duration, precise: false)
                        engine.previewer.request(fraction * engine.duration)
                        hover = ScrubHover(fraction: fraction, islandX: geo.frame(in: .named("island")).minX + value.location.x)
                        trackChapter(fraction)
                    }
                    .onEnded { value in
                        let fraction = Double(min(max(value.location.x / width, 0), 1))
                        engine.isScrubbing = false
                        engine.seek(to: fraction * engine.duration, precise: true)
                        dragFraction = nil
                        withAnimation(.smooth(duration: 0.25)) { hover = nil }
                    }
            )
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: expanded)
        }
        .frame(height: 22)
        // Тактильный отклик трекпада Force Touch при переходе через границу главы.
        .sensoryFeedback(.alignment, trigger: chapterIndex)
    }

    private func progress() -> Double {
        if let dragFraction { return dragFraction }
        guard engine.duration > 0 else { return 0 }
        let time = engine.isPlaying ? engine.player.currentTime().seconds : engine.currentTime
        return min(max(time / engine.duration, 0), 1)
    }

    private func trackChapter(_ fraction: Double) {
        guard engine.chapters.count > 1 else { return }
        let time = fraction * engine.duration
        let index = engine.chapters.lastIndex { $0.start <= time } ?? 0
        if index != chapterIndex { chapterIndex = index }
    }
}

/// Превью кадра, всплывающее из острова при наведении на шкалу.
struct ScrubPreviewBubble: View {
    let engine: PlayerEngine
    let hover: ScrubHover

    var body: some View {
        let time = hover.fraction * engine.duration
        VStack(spacing: 6) {
            Group {
                if let image = engine.previewer.image, engine.info.hasVideo {
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    Rectangle().fill(.black.opacity(0.3))
                        .overlay { Image(systemName: engine.info.hasVideo ? "photo" : "waveform").foregroundStyle(.secondary) }
                }
            }
            .frame(width: 208, height: 117)
            .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))

            VStack(spacing: 1) {
                Text(TimeFormat.string(time))
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .monospacedDigit()
                if let chapter = engine.chapters.last(where: { $0.start <= time }), !chapter.title.isEmpty {
                    Text(chapter.title)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(width: 200)
        }
        .padding(7)
    }
}
