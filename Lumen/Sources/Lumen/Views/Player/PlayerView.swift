import AppKit
import SwiftUI

struct PlayerView: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Prefs.ambientEnabled) private var ambientEnabled = true
    @AppStorage(Prefs.ambientIntensity) private var ambientIntensity = 0.85
    @AppStorage(Prefs.cinemaStage) private var cinemaStage = true

    @State private var controlsVisible = true
    @State private var panel: PlayerPanel?
    @State private var popover: IslandPopover?
    @State private var scrubHover: ScrubHover?
    @State private var idleTask: Task<Void, Never>?
    @State private var input = PlayerInputMonitor()
    @State private var zoomAtGestureStart: CGFloat = 1
    @State private var notesDismissed = false

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

                SubtitleOverlay(text: engine.subtitleText, lift: (controlsVisible ? 150 : 46) + stageInset)

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
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                        .padding(.trailing, 24)
                        .padding(.bottom, controlsVisible ? 150 : 24)
                        .transition(.asymmetric(insertion: .scale(scale: 1.6, anchor: .center).combined(with: .opacity),
                                                removal: .move(edge: .trailing).combined(with: .opacity)))
                }
            }
            .animation(.smooth(duration: 0.35), value: engine.hud)
            .animation(.smooth(duration: 0.5), value: controlsVisible)
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
            poke()
        }
        .onDisappear {
            input.uninstall()
            idleTask?.cancel()
            model.setChromeVisible(true)
        }
        .onChange(of: engine.isPlaying) { poke() }
        .onChange(of: engine.current?.id) { notesDismissed = false }
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
            VideoSurface(engine: engine, cornerRadius: inset > 0 ? 18 : 0, gravity: engine.gravity,
                         zoom: engine.zoom, rotation: engine.rotation, mirrored: engine.mirrored)
                .frame(width: frame.width, height: frame.height)
                .opacity(engine.phase == .ready ? 1 : 0)
                .animation(.smooth(duration: 0.6), value: engine.phase == .ready)
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { model.toggleFullScreen() }
                .onTapGesture {
                    if popover != nil || panel != nil {
                        withAnimation(.bouncy) { popover = nil; panel = nil }
                    } else if engine.phase == .ready {
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

    // MARK: Состояния

    @ViewBuilder
    private var stateOverlay: some View {
        switch engine.phase {
        case .preparing(let progress, let message):
            PreparingCard(title: engine.displayTitle, progress: progress, message: message) {
                engine.stop()
                model.showLibrary()
            }
            .transition(.scale(scale: 0.92).combined(with: .opacity))
        case .failed(let message):
            FailureCard(message: message, onRetry: { engine.retry() }, onLibrary: { model.showLibrary() })
                .transition(.scale(scale: 0.92).combined(with: .opacity))
        case .ready:
            if engine.isBuffering {
                ProgressView()
                    .controlSize(.large)
                    .padding(22)
                    .glassEffect(.regular, in: .circle)
                    .transition(.opacity)
            }
        case .idle:
            EmptyView()
        }
    }

    // MARK: Хром

    private func chrome(in size: CGSize) -> some View {
        ZStack {
            VStack(spacing: 10) {
                PlayerTopBar(panel: $panel, tint: tint)
                if !engine.notes.isEmpty, !notesDismissed {
                    NotesBanner(notes: engine.notes) { withAnimation(.smooth) { notesDismissed = true } }
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
                Spacer()
            }
            .padding(.leading, model.isFullScreen ? 20 : 84)
            .padding(.trailing, 20)
            .padding(.top, model.isFullScreen ? 16 : 10)

            HStack(alignment: .bottom, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Spacer()
                    if let resumed = engine.resumedFrom {
                        ResumeToast(time: resumed, onRestart: {
                            engine.seek(to: 0)
                            engine.dismissResume()
                        }, onDismiss: { engine.dismissResume() })
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                    if engine.hasMedia {
                        ControlIsland(popover: $popover, panel: $panel, hover: $scrubHover, tint: tint) { poke() }
                            .frame(maxWidth: 900)
                            .frame(maxWidth: .infinity)
                    }
                }
                if panel != nil {
                    SidePanel(panel: $panel)
                        .frame(maxHeight: max(200, size.height - 80))
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 18)
            .padding(.top, 70)
        }
        .animation(.bouncy, value: engine.resumedFrom)
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
            guard engine.isPlaying, panel == nil, popover == nil, scrubHover == nil, !engine.isScrubbing else { return }
            controlsVisible = false
            model.setChromeVisible(false)
            NSCursor.setHiddenUntilMouseMoves(true)
        }
    }
}
