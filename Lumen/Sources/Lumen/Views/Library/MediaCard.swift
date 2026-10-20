import AppKit
import SwiftUI

/// Карточка видео. При наведении: 3D-наклон за курсором, блик, бегущий по стеклу,
/// и «скимминг» — движение мыши по карточке листает кадры фильма, как в Final Cut.
struct MediaCard: View {
    enum Style { case grid, wide }

    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage(Prefs.skimmingPreviews) private var skimmingEnabled = true

    let item: MediaItem
    let list: [MediaItem]
    let style: Style

    @State private var hovering = false
    @State private var point = CGPoint(x: 0.5, y: 0.5)
    @State private var skimFrame: CGImage?
    @State private var skimCache: [Int: CGImage] = [:]
    @State private var grabber: FrameGrabber?
    @State private var skimTask: Task<Void, Never>?

    private var current: MediaItem { model.library.item(id: item.id) ?? item }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            artwork
            caption
        }
        .contentShape(Rectangle())
        .onTapGesture { model.play(current, in: list) }
        .contextMenu { menu }
        .task(id: item.id) { await model.library.loadThumbnail(for: item) }
    }

    // MARK: Обложка

    private var artwork: some View {
        GeometryReader { geo in
            ZStack {
                if let skimFrame, hovering {
                    Image(decorative: skimFrame, scale: 1).resizable().aspectRatio(contentMode: .fill)
                } else if let image = model.library.thumbnails[item.id] {
                    Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
                } else {
                    placeholder
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
            .overlay {
                // Блик следует за курсором.
                RadialGradient(colors: [.white.opacity(0.32), .clear],
                               center: UnitPoint(x: point.x, y: point.y), startRadius: 0, endRadius: geo.size.width * 0.75)
                    .blendMode(.plusLighter)
                    .opacity(hovering ? 1 : 0)
            }
            .overlay(alignment: .bottom) {
                LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
                    .allowsHitTesting(false)
            }
            .overlay(alignment: .bottomLeading) { badges.padding(10) }
            .overlay(alignment: .topTrailing) {
                if current.isFavorite {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.pink)
                        .padding(7)
                        .glassEffect(.regular, in: .circle)
                        .padding(8)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .overlay {
                if hovering {
                    Image(systemName: "play.fill")
                        .font(.system(size: 20, weight: .bold))
                        .frame(width: 54, height: 54)
                        .glassEffect(.regular.interactive(), in: .circle)
                        .transition(.scale(scale: 0.6).combined(with: .opacity))
                }
            }
            .overlay(alignment: .bottom) {
                if current.progress > 0.01 || hovering {
                    progressLine(width: geo.size.width)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(.white.opacity(hovering ? 0.35 : 0.1), lineWidth: 1)
            }
            .onContinuousHover(coordinateSpace: .local) { phase in
                switch phase {
                case .active(let p):
                    let unit = CGPoint(x: min(max(p.x / geo.size.width, 0), 1), y: min(max(p.y / geo.size.height, 0), 1))
                    if !hovering {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { hovering = true }
                    }
                    withAnimation(.interactiveSpring(response: 0.25, dampingFraction: 0.8)) { point = unit }
                    skim(to: unit.x)
                case .ended:
                    withAnimation(.spring(response: 0.45, dampingFraction: 0.75)) {
                        hovering = false
                        point = CGPoint(x: 0.5, y: 0.5)
                    }
                    skimTask?.cancel()
                }
            }
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .shadow(color: .black.opacity(hovering ? 0.55 : 0.3), radius: hovering ? 26 : 12, y: hovering ? 16 : 6)
        .rotation3DEffect(.degrees(tilt.x), axis: (x: 1, y: 0, z: 0), perspective: 0.55)
        .rotation3DEffect(.degrees(tilt.y), axis: (x: 0, y: 1, z: 0), perspective: 0.55)
        .scaleEffect(hovering ? 1.035 : 1)
        .zIndex(hovering ? 1 : 0)
    }

    private var tilt: (x: Double, y: Double) {
        guard hovering, !reduceMotion else { return (0, 0) }
        return ((Double(point.y) - 0.5) * -9, (Double(point.x) - 0.5) * 11)
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.16, green: 0.18, blue: 0.32), Color(red: 0.1, green: 0.08, blue: 0.2)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            Image(systemName: current.exists ? "film" : "questionmark.folder")
                .font(.system(size: 30, weight: .medium))
                .foregroundStyle(.white.opacity(0.35))
        }
    }

    private var badges: some View {
        HStack(spacing: 6) {
            if hovering, current.duration > 0 {
                Text(TimeFormat.string(Double(point.x) * current.duration))
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .glassEffect(.regular, in: .capsule)
                    .transition(.opacity)
            }
            Spacer()
            if current.duration > 0 {
                Text(TimeFormat.string(current.duration))
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .glassEffect(.regular, in: .capsule)
            }
        }
    }

    private func progressLine(width: CGFloat) -> some View {
        let fraction = hovering && skimmingEnabled ? Double(point.x) : current.progress
        return ZStack(alignment: .leading) {
            Rectangle().fill(.white.opacity(0.2))
            Rectangle().fill(.tint).frame(width: width * fraction)
        }
        .frame(height: 3)
    }

    // MARK: Подпись

    private var caption: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(current.title)
                .font(.system(size: style == .wide ? 15 : 13.5, weight: .semibold))
                .lineLimit(1)
            Text(subtitle)
                .font(.system(size: 11.5))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 4)
    }

    private var subtitle: String {
        var parts: [String] = []
        if current.isInProgress {
            parts.append("Осталось \(TimeFormat.duration(current.remaining))")
        } else if current.isWatched {
            parts.append("Просмотрено")
        }
        parts.append(current.url.pathExtension.uppercased())
        if current.fileSize > 0 { parts.append(Units.bytes(current.fileSize)) }
        if !current.exists { parts.append("файл не найден") }
        return parts.joined(separator: " · ")
    }

    // MARK: Контекстное меню

    @ViewBuilder
    private var menu: some View {
        Button("Воспроизвести") { model.play(current, in: list) }
        Button("Воспроизвести с начала") {
            model.library.update(item.id) { $0.position = 0 }
            model.play(current, in: list)
        }
        Divider()
        Button(current.isFavorite ? "Убрать из избранного" : "В избранное") {
            withAnimation(.bouncy) { model.library.update(item.id) { $0.isFavorite.toggle() } }
        }
        Button(current.isWatched ? "Отметить непросмотренным" : "Отметить просмотренным") {
            model.library.update(item.id) { $0.position = $0.isWatched ? 0 : $0.duration }
        }
        Divider()
        Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([item.url]) }
        Button("Убрать из библиотеки", role: .destructive) {
            withAnimation(.smooth) { model.library.remove(item.id) }
        }
    }

    // MARK: Скимминг

    private func skim(to fraction: CGFloat) {
        guard skimmingEnabled, current.duration > 0 else { return }
        let steps = 48
        let key = Int((Double(fraction) * Double(steps)).rounded())
        if let cached = skimCache[key] {
            skimFrame = cached
            return
        }
        if grabber == nil {
            let source = MediaResolver.cachedSource(for: item.url)?.url
                ?? (MatroskaRemuxer.fileExtensions.contains(item.url.pathExtension.lowercased()) ? nil : item.url)
            guard let source else { return }
            grabber = FrameGrabber(url: source, maxSize: CGSize(width: 640, height: 360), tolerance: max(1, current.duration / Double(steps) / 2))
        }
        guard let grabber else { return }
        let time = Double(key) / Double(steps) * current.duration
        skimTask?.cancel()
        skimTask = Task {
            guard let frame = await grabber.image(at: time), !Task.isCancelled else { return }
            skimCache[key] = frame
            skimFrame = frame
        }
    }
}
