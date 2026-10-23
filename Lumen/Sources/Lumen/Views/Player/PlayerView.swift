import AppKit
import SwiftUI

struct PlayerView: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Prefs.ambientEnabled) private var ambientEnabled = true
    @AppStorage(Prefs.ambientIntensity) private var ambientIntensity = 0.85
    @AppStorage(Prefs.cinemaStage) private var cinemaStage = false
    @AppStorage(Prefs.islandX) private var islandX = 0.5
    @AppStorage(Prefs.islandY) private var islandY = 1.0

    @State private var controlsVisible = true
    @State private var panel: PlayerPanel?
    @State private var popover: IslandPopover?
    @State private var islandHovered = false
    @State private var islandDrag: CGSize = .zero
    @State private var islandSize = CGSize(width: 520, height: 88)
    @State private var idleTask: Task<Void, Never>?
    @State private var input = PlayerInputMonitor()
    @State private var zoomAtGestureStart: CGFloat = 1
    @State private var showSpinner = false

    private var engine: PlayerEngine { model.engine }
    private var tint: Color { engine.ambient.palette.accent }

    private var stageInset: CGFloat {
        cinemaStage && !model.isFullScreen && engine.gravity == .fit && engine.info.hasVideo ? PlayerLayout.stageInset : 0
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black

                if ambientEnabled, engine.info.hasVideo {
                    AmbientGlow(palette: engine.ambient.palette)
                        .opacity(ambientIntensity)
                        .transition(.opacity)
                }

                video(in: geo.size)

                if engine.phase == .ready, !engine.info.hasVideo {
                    AudioOnlyView(title: engine.displayTitle, isPlaying: engine.isPlaying)
                }

                SubtitleOverlay(text: engine.subtitleText, lift: subtitleLift(in: geo.size))

                stateOverlay

                if let hud = engine.hud {
                    HUDView(message: hud)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                        .id("hud")
                }

                if controlsVisible {
                    chrome(in: geo.size)
                        .transition(.opacity)
                }

                if let capture = engine.capture {
                    CaptureToast(capture: capture)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                        .padding(.trailing, 20)
                        .padding(.top, 64)
                        .transition(.asymmetric(insertion: .scale(scale: 1.6, anchor: .center).combined(with: .opacity),
                                                removal: .move(edge: .trailing).combined(with: .opacity)))
                }
            }
            .coordinateSpace(.named("stage"))
            .animation(.smooth(duration: 0.35), value: engine.hud)
            .animation(.smooth(duration: 0.4), value: controlsVisible)
            .animation(.bouncy(duration: 0.45), value: panel)
            .animation(.smooth(duration: 0.6), value: stageInset)
        }
        .ignoresSafeArea()
        .onContinuousHover { phase in
            if case .active = phase { poke() }
        }
        .onAppear {
            input.install(.init(
                model: model,
                togglePanel: { kind in withAnimation(.bouncy) { panel = panel == kind ? nil : kind } },
                dismissOverlays: {
                    if popover != nil { withAnimation(.bouncy) { popover = nil }; return true }
                    if panel != nil { withAnimation(.bouncy) { panel = nil }; return true }
                    return false
                },
                panelOpen: { panel != nil },
                poke: { poke() }
            ))
            if let demo = DemoUI.panel { panel = demo }
            poke()
        }
        .onDisappear {
            input.uninstall()
            idleTask?.cancel()
            model.setChromeVisible(true)
        }
        .onChange(of: engine.isPlaying) { poke() }
        .onChange(of: engine.phase) { _, phase in
            // Индикатор загрузки — только если открытие затянулось (сетевой диск и т.п.).
            showSpinner = false
            guard phase == .opening else { return }
            Task {
                try? await Task.sleep(for: .milliseconds(500))
                if engine.phase == .opening { withAnimation { showSpinner = true } }
            }
        }
    }

    // MARK: Видео

    @ViewBuilder
    private func video(in size: CGSize) -> some View {
        let inset = stageInset
        let frame = videoFrame(in: size, inset: inset)
        ZStack {
            if inset > 0 {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(.black)
                    .frame(width: frame.width, height: frame.height)
                    .shadow(color: tint.opacity(0.55), radius: 40)
                    .shadow(color: .black.opacity(0.6), radius: 18, y: 10)
            }
            VideoSurface(videoLayer: engine.core?.videoLayer, cornerRadius: inset > 0 ? 18 : 0,
                         zoom: engine.zoom, rotation: engine.rotation, mirrored: engine.mirrored)
                .frame(width: frame.width, height: frame.height)
                .opacity(engine.phase == .ready ? 1 : 0)
                .animation(.smooth(duration: 0.3), value: engine.phase == .ready)
            if let bitmap = engine.subtitleImage {
                BitmapSubtitleOverlay(bitmap: bitmap, video: imageRect(in: size, frame: frame))
                    .frame(width: size.width, height: size.height)
                    .allowsHitTesting(false)
            }
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { model.toggleFullScreen() }
                .onTapGesture {
                    if popover != nil || panel != nil {
                        withAnimation(.bouncy) { popover = nil; panel = nil }
                    } else {
                        engine.togglePlay()
                    }
                }
                .gesture(
                    MagnifyGesture()
                        .onChanged { value in
                            engine.zoom = min(max(zoomAtGestureStart * value.magnification, 1), 4)
                        }
                        .onEnded { _ in
                            if engine.zoom < 1.06 { engine.zoom = 1 }
                            zoomAtGestureStart = engine.zoom
                        }
                )
        }
        .frame(width: size.width, height: size.height)
    }

    private func videoFrame(in size: CGSize, inset: CGFloat) -> CGSize {
        let available = CGSize(width: max(1, size.width - inset * 2), height: max(1, size.height - inset * 2))
        var video = engine.info.displaySize
        guard engine.gravity == .fit, video.width > 0, video.height > 0 else { return size }
        if engine.rotation % 180 != 0 { video = CGSize(width: video.height, height: video.width) }
        let scale = min(available.width / video.width, available.height / video.height)
        return CGSize(width: video.width * scale, height: video.height * scale)
    }

    /// Где на экране лежит сам кадр — к нему привязаны субтитры Blu-ray.
    private func imageRect(in size: CGSize, frame: CGSize) -> CGRect {
        let video = engine.info.displaySize
        var rect = CGRect(x: (size.width - frame.width) / 2, y: (size.height - frame.height) / 2, width: frame.width, height: frame.height)
        if engine.gravity == .fill, video.width > 0, video.height > 0 {
            let scale = max(size.width / video.width, size.height / video.height)
            let w = video.width * scale, h = video.height * scale
            rect = CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h)
        }
        return rect
    }

    private func subtitleLift(in size: CGSize) -> CGFloat {
        let islandLow = islandY > 0.7 && engine.hasMedia
        return (controlsVisible && islandLow ? islandSize.height + 30 : 40) + stageInset
    }

    // MARK: Состояния

    @ViewBuilder
    private var stateOverlay: some View {
        switch engine.phase {
        case .opening:
            if showSpinner {
                ProgressView()
                    .controlSize(.large)
                    .padding(22)
                    .glassEffect(.regular, in: .circle)
                    .transition(.opacity)
            }
        case .failed(let message):
            FailureCard(message: message, onRetry: { engine.retry() }, onOpen: { model.presentOpenPanel() })
                .transition(.scale(scale: 0.92).combined(with: .opacity))
        case .ready, .idle:
            EmptyView()
        }
    }

    // MARK: Хром

    private func chrome(in size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            PlayerTopBar(panel: $panel)
                .padding(.leading, model.isFullScreen ? 20 : 84)
                .padding(.trailing, 20)
                .padding(.top, model.isFullScreen ? 16 : 10)
                .frame(maxWidth: .infinity, alignment: .top)

            if panel != nil {
                SidePanel(panel: $panel)
                    .frame(maxHeight: max(200, size.height - Island.top - Island.margin))
                    .padding(.top, Island.top)
                    .padding(.trailing, Island.margin)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }

            if let resumed = engine.resumedFrom {
                ResumeToast(time: resumed, onRestart: {
                    engine.seek(to: 0)
                    engine.dismissResume()
                }, onDismiss: { engine.dismissResume() })
                .frame(maxWidth: .infinity)
                .padding(.top, Island.top)
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            if engine.phase == .ready {
                island(in: size)
            }
        }
        .frame(width: size.width, height: size.height)
        .animation(.bouncy, value: engine.resumedFrom)
    }

    private func island(in size: CGSize) -> some View {
        let width = min(Island.width, size.width - Island.margin * 2)
        let center = Island.center(x: islandX, y: islandY, island: CGSize(width: width, height: islandSize.height), stage: size)
        return ControlIsland(popover: $popover, panel: $panel, popoversAbove: islandY > 0.4, tint: tint) { poke() }
            .frame(width: width)
            .onGeometryChange(for: CGSize.self) { $0.size } action: { islandSize = $0 }
            .onHover { islandHovered = $0 }
            .gesture(
                DragGesture(minimumDistance: 3, coordinateSpace: .named("stage"))
                    .onChanged { value in
                        islandDrag = value.translation
                        poke()
                    }
                    .onEnded { value in
                        let island = CGSize(width: width, height: islandSize.height)
                        let dropped = CGPoint(x: center.x + value.translation.width, y: center.y + value.translation.height)
                        let target = Island.fractions(for: dropped, island: island, stage: size)
                        withAnimation(.bouncy(duration: 0.45, extraBounce: 0.08)) {
                            islandX = target.x
                            islandY = target.y
                            islandDrag = .zero
                        }
                    }
            )
            .position(x: center.x + islandDrag.width, y: center.y + islandDrag.height)
    }

    // MARK: Автоскрытие

    private func poke() {
        if !controlsVisible {
            controlsVisible = true
            model.setChromeVisible(true)
        }
        idleTask?.cancel()
        idleTask = Task {
            try? await Task.sleep(for: .seconds(2.8))
            guard !Task.isCancelled else { return }
            guard engine.isPlaying, panel == nil, popover == nil, !islandHovered, !engine.isScrubbing, islandDrag == .zero else { return }
            controlsVisible = false
            model.setChromeVisible(false)
            NSCursor.setHiddenUntilMouseMoves(true)
        }
    }
}

/// Геометрия плавающей панели: положение хранится долями свободного места, поэтому
/// панель остаётся в своём углу при любом размере окна и в полноэкранном режиме.
enum Island {
    static let width: CGFloat = 540
    static let margin: CGFloat = 18
    /// Сверху остаётся место под строку с названием.
    static let top: CGFloat = 62
    /// Насколько близко к краю или центру нужно отпустить панель, чтобы она к нему прилипла.
    static let snap = 0.1

    static func center(x: Double, y: Double, island: CGSize, stage: CGSize) -> CGPoint {
        let freeW = max(0, stage.width - island.width - margin * 2)
        let freeH = max(0, stage.height - island.height - top - margin)
        return CGPoint(x: margin + island.width / 2 + freeW * x, y: top + island.height / 2 + freeH * y)
    }

    static func fractions(for point: CGPoint, island: CGSize, stage: CGSize) -> (x: Double, y: Double) {
        let freeW = max(1, stage.width - island.width - margin * 2)
        let freeH = max(1, stage.height - island.height - top - margin)
        var x = min(max(Double((point.x - margin - island.width / 2) / freeW), 0), 1)
        var y = min(max(Double((point.y - top - island.height / 2) / freeH), 0), 1)
        for anchor in [0, 0.5, 1] where abs(x - anchor) < snap { x = anchor }
        for anchor in [0, 1] where abs(y - anchor) < snap { y = anchor }
        return (x, y)
    }
}

/// Субтитры Blu-ray: картинки, нарисованные под кадр 1920×1080, масштабируются вместе с видео.
struct BitmapSubtitleOverlay: View {
    let bitmap: SubtitleBitmap
    let video: CGRect

    var body: some View {
        Canvas { context, _ in
            let sx = video.width / bitmap.canvas.width
            let sy = video.height / bitmap.canvas.height
            for piece in bitmap.pieces {
                let rect = CGRect(x: video.minX + piece.rect.minX * sx, y: video.minY + piece.rect.minY * sy,
                                  width: piece.rect.width * sx, height: piece.rect.height * sy)
                context.draw(Image(decorative: piece.image, scale: 1), in: rect)
            }
        }
    }
}
