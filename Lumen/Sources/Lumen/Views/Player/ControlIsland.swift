import SwiftUI

enum IslandPopover: String, Hashable, Sendable {
    case volume, speed, chapters

    var leading: Bool { self != .chapters }
}

/// Панель управления в духе QuickTime: компактный стеклянный остров, который можно утащить
/// в любое место окна. Отпущенный рядом с краем или углом, он примагничивается к нему.
struct ControlIsland: View {
    @Environment(AppModel.self) private var model
    @Binding var popover: IslandPopover?
    @Binding var panel: PlayerPanel?
    /// Всплывающие панели открываются вверх, если остров внизу окна, и вниз — если вверху.
    let popoversAbove: Bool
    let tint: Color
    let onInteract: () -> Void

    @Namespace private var glass

    private var engine: PlayerEngine { model.engine }

    var body: some View {
        GlassEffectContainer(spacing: 22) {
            island
                .glassEffect(.regular.tint(tint.opacity(0.16)), in: .rect(cornerRadius: 26))
                .glassEffectID("island", in: glass)
                .overlay(alignment: overlayAlignment) {
                    if let popover {
                        popoverContent(popover)
                            .padding(14)
                            .glassEffect(.regular.tint(tint.opacity(0.12)), in: .rect(cornerRadius: 22))
                            .glassEffectID(popover, in: glass)
                            .fixedSize()
                            .alignmentGuide(popoversAbove ? VerticalAlignment.top : VerticalAlignment.bottom) { d in
                                popoversAbove ? d[.bottom] + 10 : d[.top] - 10
                            }
                    }
                }
        }
        .onHover { _ in onInteract() }
    }

    private var overlayAlignment: Alignment {
        let leading = popover?.leading ?? true
        switch (popoversAbove, leading) {
        case (true, true): return .topLeading
        case (true, false): return .topTrailing
        case (false, true): return .bottomLeading
        case (false, false): return .bottomTrailing
        }
    }

    // MARK: Остров

    private var island: some View {
        VStack(spacing: 6) {
            HStack(spacing: 2) {
                HStack(spacing: 2) {
                    IslandButton(symbol: engine.volumeSymbol, size: 13, help: "Громкость (↑ ↓, M)", isActive: popover == .volume) {
                        toggle(.volume)
                    }
                    speedButton
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 4) {
                    if engine.hasPlaylist {
                        IslandButton(symbol: "backward.end.fill", size: 12, help: "Предыдущее (⌘←)") { engine.previous() }
                    }
                    IslandButton(symbol: "gobackward.\(Int(Prefs.skip))", size: 15, help: "Назад (←)") { engine.skip(by: -Prefs.skip) }
                    playButton
                    IslandButton(symbol: "goforward.\(Int(Prefs.skip))", size: 15, help: "Вперёд (→)") { engine.skip(by: Prefs.skip) }
                    if engine.hasPlaylist {
                        IslandButton(symbol: "forward.end.fill", size: 12, help: "Следующее (⌘→)", isEnabled: engine.hasNext) { engine.next() }
                    }
                }

                HStack(spacing: 2) {
                    if engine.chapters.count > 1 {
                        IslandButton(symbol: "list.bullet", size: 13, help: "Главы", isActive: popover == .chapters) { toggle(.chapters) }
                    }
                    IslandButton(symbol: engine.subtitleChoice == .off ? "captions.bubble" : "captions.bubble.fill", size: 13,
                                 help: "Звук и субтитры (T)", isActive: panel == .tracks) {
                        withAnimation(.bouncy) { panel = panel == .tracks ? nil : .tracks }
                    }
                    IslandButton(symbol: engine.isPictureInPictureActive ? "pip.exit" : "pip.enter", size: 13, help: "Картинка в картинке (P)") {
                        engine.togglePictureInPicture()
                    }
                    IslandButton(symbol: model.isFullScreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right",
                                 size: 13, help: "Полный экран (F)") {
                        model.toggleFullScreen()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }

            HStack(spacing: 10) {
                Text(TimeFormat.string(engine.currentTime))
                    .frame(minWidth: 44, alignment: .trailing)
                Scrubber(engine: engine, tint: tint)
                Text("−" + TimeFormat.string(max(0, engine.duration - engine.currentTime)))
                    .frame(minWidth: 50, alignment: .leading)
            }
            .font(.system(size: 11.5, weight: .semibold, design: .rounded))
            .monospacedDigit()
            .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .padding(.bottom, 10)
    }

    private var playButton: some View {
        Button { engine.togglePlay() } label: {
            Image(systemName: engine.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 20, weight: .bold))
                .contentTransition(.symbolEffect(.replace.magic(fallback: .downUp)))
                .frame(width: 46, height: 46)
                .contentShape(Circle())
        }
        .buttonStyle(PressScaleStyle())
        .glassEffect(.regular.tint(tint.opacity(0.5)).interactive(), in: .circle)
        .help("Воспроизведение / пауза (пробел)")
    }

    private var speedButton: some View {
        Button { toggle(.speed) } label: {
            Text(PlayerEngine.rateLabel(engine.rate))
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText(value: Double(engine.rate)))
                .frame(minWidth: 32, minHeight: 30)
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
                .frame(width: 180)
            Text("\(Int((engine.isMuted ? 0 : engine.volume) * 100))%")
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .frame(width: 38, alignment: .trailing)
                .contentTransition(.numericText())
        }
    }

    private var speedPopover: some View {
        HStack(spacing: 6) {
            ForEach(PlayerEngine.speeds, id: \.self) { speed in
                GlassChip(title: PlayerEngine.rateLabel(speed), isSelected: engine.rate == speed) {
                    withAnimation(.snappy) { engine.setRate(speed) }
                }
            }
        }
    }

    private var chaptersPopover: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Главы", systemImage: "list.bullet")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
                .padding(.bottom, 4)
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
}
