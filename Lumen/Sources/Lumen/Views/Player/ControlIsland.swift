import SwiftUI

enum IslandPopover: String, Hashable, Sendable {
    case volume, speed, subtitles, audio, chapters

    var alignment: Alignment {
        switch self {
        case .volume, .speed: .leading
        case .subtitles, .audio, .chapters: .trailing
        }
    }
}

/// Плавающий остров управления. Всплывающие панели и превью кадра — отдельные
/// стеклянные формы в одном GlassEffectContainer, поэтому они «вытекают» из острова
/// и сливаются с ним, как капли жидкого стекла.
struct ControlIsland: View {
    @Environment(AppModel.self) private var model
    @Binding var popover: IslandPopover?
    @Binding var panel: PlayerPanel?
    @Binding var hover: ScrubHover?
    let tint: Color
    let onInteract: () -> Void

    @Namespace private var glass

    private var engine: PlayerEngine { model.engine }

    var body: some View {
        GlassEffectContainer(spacing: 22) {
            VStack(spacing: 10) {
                if let hover, engine.duration > 0 {
                    ScrubPreviewBubble(engine: engine, hover: hover)
                        .glassEffect(.regular, in: .rect(cornerRadius: 20))
                        .glassEffectID("preview", in: glass)
                        .offset(x: previewOffset(hover))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .allowsHitTesting(false)
                }
                if let popover {
                    popoverContent(popover)
                        .padding(14)
                        .glassEffect(.regular.tint(tint.opacity(0.12)), in: .rect(cornerRadius: 22))
                        .glassEffectID(popover, in: glass)
                        .frame(maxWidth: .infinity, alignment: popover.alignment)
                }
                island
                    .glassEffect(.regular.tint(tint.opacity(0.16)), in: .rect(cornerRadius: 28))
                    .glassEffectID("island", in: glass)
            }
        }
        .onHover { _ in onInteract() }
    }

    private func previewOffset(_ hover: ScrubHover) -> CGFloat {
        // ширина пузыря 222; держим его внутри ширины острова
        min(max(hover.islandX - 111, 0), max(islandWidth - 222, 0))
    }

    @State private var islandWidth: CGFloat = 800

    // MARK: Остров

    private var island: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                Text(TimeFormat.string(engine.currentTime))
                    .frame(minWidth: 52, alignment: .trailing)
                Scrubber(engine: engine, tint: tint, hover: $hover)
                Text("−" + TimeFormat.string(max(0, engine.duration - engine.currentTime)))
                    .frame(minWidth: 58, alignment: .leading)
            }
            .font(.system(size: 12, weight: .semibold, design: .rounded))
            .monospacedDigit()
            .foregroundStyle(.secondary)

            ZStack {
                HStack(spacing: 2) {
                    IslandButton(symbol: engine.volumeSymbol, size: 14, help: "Громкость (↑ ↓, M)", isActive: popover == .volume) {
                        toggle(.volume)
                    }
                    speedButton
                    if engine.loopA != nil {
                        IslandButton(symbol: engine.loopB == nil ? "a.circle.fill" : "repeat.circle.fill", size: 14, help: "Сбросить повтор A–B", isActive: true) {
                            engine.clearLoop()
                        }
                        .transition(.scale.combined(with: .opacity))
                    }
                    Spacer()
                    if engine.chapters.count > 1 {
                        IslandButton(symbol: "list.bullet", size: 14, help: "Главы", isActive: popover == .chapters) { toggle(.chapters) }
                    }
                    if engine.audioOptions.count > 1 {
                        IslandButton(symbol: "waveform", size: 14, help: "Звуковая дорожка (A)", isActive: popover == .audio) { toggle(.audio) }
                    }
                    IslandButton(symbol: engine.subtitleChoice == .off ? "captions.bubble" : "captions.bubble.fill", size: 14,
                                 help: "Субтитры (S)", isActive: popover == .subtitles) { toggle(.subtitles) }
                    IslandButton(symbol: "slider.horizontal.3", size: 14, help: "Изображение (E)", isActive: panel == .adjust) {
                        withAnimation(.bouncy) { panel = panel == .adjust ? nil : .adjust }
                    }
                    IslandButton(symbol: engine.isPictureInPictureActive ? "pip.exit" : "pip.enter", size: 14, help: "Картинка в картинке (P)") {
                        engine.togglePictureInPicture()
                    }
                    IslandButton(symbol: model.isFullScreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right",
                                 size: 14, help: "Полный экран (F)") {
                        model.toggleFullScreen()
                    }
                }

                HStack(spacing: 6) {
                    IslandButton(symbol: "backward.end.fill", size: 14, help: "Предыдущее", isEnabled: engine.hasMedia) { engine.previous() }
                    IslandButton(symbol: "gobackward.\(Int(Prefs.skip))", size: 17, help: "Назад (←)") { engine.skip(by: -Prefs.skip) }
                    playButton
                    IslandButton(symbol: "goforward.\(Int(Prefs.skip))", size: 17, help: "Вперёд (→)") { engine.skip(by: Prefs.skip) }
                    IslandButton(symbol: "forward.end.fill", size: 14, help: "Следующее", isEnabled: engine.hasNext) { engine.next() }
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .coordinateSpace(.named("island"))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { islandWidth = $0 }
        .animation(.bouncy, value: engine.loopA)
    }

    private var playButton: some View {
        Button { engine.togglePlay() } label: {
            Image(systemName: engine.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 22, weight: .bold))
                .contentTransition(.symbolEffect(.replace.magic(fallback: .downUp)))
                .frame(width: 52, height: 52)
                .contentShape(Circle())
        }
        .buttonStyle(PressScaleStyle())
        .glassEffect(.regular.tint(tint.opacity(0.5)).interactive(), in: .circle)
        .help("Воспроизведение / пауза (пробел)")
    }

    private var speedButton: some View {
        Button { toggle(.speed) } label: {
            Text(PlayerEngine.rateLabel(engine.rate))
                .font(.system(size: 12.5, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText(value: Double(engine.rate)))
                .frame(minWidth: 34, minHeight: 32)
                .padding(.horizontal, 4)
                .background(Capsule().fill(.white.opacity(popover == .speed || engine.rate != 1 ? 0.14 : 0)))
                .contentShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
        .help("Скорость ([ ])")
    }

    private func toggle(_ kind: IslandPopover) {
        withAnimation(.bouncy(duration: 0.42)) { popover = popover == kind ? nil : kind }
    }

    // MARK: Всплывающие панели

    @ViewBuilder
    private func popoverContent(_ kind: IslandPopover) -> some View {
        switch kind {
        case .volume: volumePopover
        case .speed: speedPopover
        case .subtitles: subtitlesPopover
        case .audio: audioPopover
        case .chapters: chaptersPopover
        }
    }

    private var volumePopover: some View {
        HStack(spacing: 12) {
            Button { engine.toggleMute() } label: {
                Image(systemName: engine.volumeSymbol)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 22)
            }
            .buttonStyle(.plain)
            Slider(value: Binding(get: { Double(engine.isMuted ? 0 : engine.volume) },
                                  set: { engine.isMuted = false; engine.volume = Float($0) }), in: 0...1)
                .frame(width: 190)
            Text("\(Int((engine.isMuted ? 0 : engine.volume) * 100))%")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .frame(width: 38, alignment: .trailing)
                .contentTransition(.numericText())
        }
    }

    private var speedPopover: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Скорость воспроизведения", systemImage: "gauge.with.dots.needle.67percent")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
            HStack(spacing: 6) {
                ForEach(PlayerEngine.speeds, id: \.self) { speed in
                    GlassChip(title: PlayerEngine.rateLabel(speed), isSelected: engine.rate == speed) {
                        withAnimation(.snappy) { engine.setRate(speed) }
                    }
                }
            }
        }
    }

    private var subtitlesPopover: some View {
        VStack(alignment: .leading, spacing: 4) {
            popoverHeader("Субтитры", symbol: "captions.bubble")
            ForEach(engine.subtitleOptions) { option in
                optionRow(option.title, selected: engine.subtitleChoice == option.choice) {
                    engine.selectSubtitle(option.choice)
                }
            }
            Divider().padding(.vertical, 4)
            HStack {
                Text("Задержка").foregroundStyle(.secondary)
                Spacer()
                Button { engine.shiftSubtitles(by: -0.1) } label: { Image(systemName: "minus") }
                    .buttonStyle(.glass).controlSize(.small)
                Text(String(format: "%+.1f с", engine.subtitleDelay))
                    .monospacedDigit().frame(width: 58)
                Button { engine.shiftSubtitles(by: 0.1) } label: { Image(systemName: "plus") }
                    .buttonStyle(.glass).controlSize(.small)
            }
            .font(.system(size: 12.5, weight: .medium))
            Button {
                model.presentSubtitlePanel()
            } label: {
                Label("Добавить файл…", systemImage: "plus.circle")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .font(.system(size: 12.5, weight: .medium))
            .padding(.top, 4)
        }
        .frame(width: 280)
    }

    private var audioPopover: some View {
        VStack(alignment: .leading, spacing: 4) {
            popoverHeader("Звуковая дорожка", symbol: "waveform")
            ForEach(Array(engine.audioOptions.enumerated()), id: \.offset) { index, name in
                optionRow(name, selected: engine.selectedAudio == index) { engine.selectAudio(index) }
            }
        }
        .frame(width: 280)
    }

    private var chaptersPopover: some View {
        VStack(alignment: .leading, spacing: 4) {
            popoverHeader("Главы", symbol: "list.bullet")
            ScrollView {
                VStack(spacing: 2) {
                    ForEach(Array(engine.chapters.enumerated()), id: \.offset) { index, chapter in
                        let current = engine.chapters.lastIndex { $0.start <= engine.currentTime } == index
                        Button {
                            engine.seek(to: chapter.start)
                        } label: {
                            HStack {
                                Text(chapter.title.isEmpty ? "Глава \(index + 1)" : chapter.title).lineLimit(1)
                                Spacer()
                                Text(TimeFormat.string(chapter.start)).monospacedDigit().foregroundStyle(.secondary)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 9).fill(.white.opacity(current ? 0.14 : 0)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxHeight: 260)
        }
        .font(.system(size: 12.5, weight: .medium))
        .frame(width: 300)
    }

    private func popoverHeader(_ title: String, symbol: String) -> some View {
        Label(title, systemImage: symbol)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(.secondary)
            .padding(.bottom, 4)
    }

    private func optionRow(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .opacity(selected ? 1 : 0)
                    .frame(width: 14)
                Text(title).lineLimit(1)
                Spacer()
            }
            .font(.system(size: 13, weight: selected ? .semibold : .regular))
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(.white.opacity(selected ? 0.12 : 0)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
