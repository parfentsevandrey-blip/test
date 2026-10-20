import SwiftUI

/// Боковая стеклянная панель с вкладками: изображение, очередь, информация.
struct SidePanel: View {
    @Environment(AppModel.self) private var model
    @Binding var panel: PlayerPanel?
    @Namespace private var tabs

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 4) {
                ForEach(PlayerPanel.allCases) { kind in
                    Button {
                        withAnimation(.snappy) { panel = kind }
                    } label: {
                        Label(kind.title, systemImage: kind.symbol)
                            .labelStyle(.titleAndIcon)
                            .font(.system(size: 12, weight: .semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .frame(maxWidth: .infinity)
                            .background {
                                if panel == kind {
                                    Capsule().fill(.white.opacity(0.16))
                                        .matchedGeometryEffect(id: "tab", in: tabs)
                                }
                            }
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Button {
                    withAnimation(.bouncy) { panel = nil }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .bold))
                        .frame(width: 26, height: 26)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
            }
            .padding(4)
            .background(Capsule().fill(.black.opacity(0.18)))

            ScrollView {
                Group {
                    switch panel {
                    case .adjust: AdjustPanel()
                    case .queue: QueuePanel()
                    case .info: InfoPanel()
                    case nil: EmptyView()
                    }
                }
                .padding(.bottom, 8)
            }
            .scrollIndicators(.never)
            .scrollEdgeEffectStyle(.soft, for: .all)
        }
        .padding(14)
        .frame(width: 340)
        .glassEffect(.regular, in: .rect(cornerRadius: 30))
    }
}

private struct PanelSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .tracking(0.8)
                .foregroundStyle(.secondary)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(0.06)))
    }
}

private struct AdjustSlider: View {
    let title: String
    let symbol: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let neutral: Double
    var format: (Double) -> String = { String(format: "%+.2f", $0) }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Label(title, systemImage: symbol)
                    .font(.system(size: 12, weight: .medium))
                Spacer()
                Text(format(value))
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(value == neutral ? .secondary : .primary)
                    .contentTransition(.numericText(value: value))
                    .onTapGesture(count: 2) { withAnimation(.smooth) { value = neutral } }
                    .help("Двойной щелчок — сбросить")
            }
            Slider(value: $value, in: range)
                .controlSize(.small)
        }
    }
}

struct AdjustPanel: View {
    @Environment(AppModel.self) private var model
    @AppStorage(Prefs.ambientEnabled) private var ambientEnabled = true
    @AppStorage(Prefs.ambientIntensity) private var ambientIntensity = 0.85
    @AppStorage(Prefs.cinemaStage) private var cinemaStage = true
    @AppStorage(Prefs.subtitleScale) private var subtitleScale = 1.0
    @AppStorage(Prefs.subtitleGlass) private var subtitleGlass = true

    var body: some View {
        @Bindable var engine = model.engine
        VStack(spacing: 12) {
            PanelSection(title: "Стиль") {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 8)], spacing: 8) {
                    ForEach(VideoAdjustments.presets) { preset in
                        let selected = engine.adjustments == preset.value
                        Button { engine.applyPreset(preset) } label: {
                            VStack(spacing: 6) {
                                Image(systemName: preset.symbol)
                                    .font(.system(size: 16, weight: .semibold))
                                    .symbolEffect(.bounce, value: selected)
                                Text(preset.name).font(.system(size: 11.5, weight: .semibold))
                            }
                            .frame(maxWidth: .infinity, minHeight: 58)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(selected ? AnyShapeStyle(.tint.opacity(0.45)) : AnyShapeStyle(.white.opacity(0.07))))
                            .contentShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(PressScaleStyle())
                    }
                }
            }

            PanelSection(title: "Тонкая настройка") {
                AdjustSlider(title: "Экспозиция", symbol: "plusminus.circle", value: $engine.adjustments.exposure, range: -1...1, neutral: 0)
                AdjustSlider(title: "Яркость", symbol: "sun.max", value: $engine.adjustments.brightness, range: -0.3...0.3, neutral: 0)
                AdjustSlider(title: "Контраст", symbol: "circle.lefthalf.filled", value: $engine.adjustments.contrast, range: 0.6...1.6, neutral: 1,
                             format: { String(format: "%.2f", $0) })
                AdjustSlider(title: "Насыщенность", symbol: "drop", value: $engine.adjustments.saturation, range: 0...2, neutral: 1,
                             format: { String(format: "%.2f", $0) })
                AdjustSlider(title: "Сочность", symbol: "sparkles", value: $engine.adjustments.vibrance, range: -1...1, neutral: 0)
                AdjustSlider(title: "Теплота", symbol: "thermometer.medium", value: $engine.adjustments.warmth, range: -1...1, neutral: 0)
                AdjustSlider(title: "Резкость", symbol: "triangle", value: $engine.adjustments.sharpness, range: 0...1.5, neutral: 0,
                             format: { String(format: "%.2f", $0) })
                AdjustSlider(title: "Виньетка", symbol: "circle.dashed", value: $engine.adjustments.vignette, range: 0...1.5, neutral: 0,
                             format: { String(format: "%.2f", $0) })
                Button {
                    withAnimation(.smooth) { engine.adjustments = VideoAdjustments() }
                } label: {
                    Label("Сбросить всё", systemImage: "arrow.counterclockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .disabled(engine.adjustments.isIdentity)
            }

            PanelSection(title: "Геометрия") {
                Picker("Масштаб", selection: $engine.gravity) {
                    ForEach(VideoGravity.allCases, id: \.self) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                HStack {
                    Button { engine.rotate() } label: { Label("Повернуть", systemImage: "rotate.right") }
                    Button { engine.mirrored.toggle() } label: { Label("Отразить", systemImage: "arrow.left.and.right.righttriangle.left.righttriangle.right") }
                }
                .buttonStyle(.glass)
                .controlSize(.small)
                AdjustSlider(title: "Увеличение", symbol: "plus.magnifyingglass",
                             value: Binding(get: { Double(engine.zoom) }, set: { engine.zoom = CGFloat($0) }),
                             range: 1...3, neutral: 1, format: { String(format: "%.1f×", $0) })
            }

            PanelSection(title: "Атмосфера") {
                Toggle("Живая подсветка из кадра", isOn: $ambientEnabled)
                if ambientEnabled {
                    AdjustSlider(title: "Интенсивность", symbol: "light.max", value: $ambientIntensity, range: 0.2...1, neutral: 0.85,
                                 format: { "\(Int($0 * 100))%" })
                }
                Toggle("Сцена: видео парит над свечением", isOn: $cinemaStage)
            }
            .toggleStyle(.switch)
            .controlSize(.small)

            PanelSection(title: "Субтитры") {
                AdjustSlider(title: "Размер", symbol: "textformat.size", value: $subtitleScale, range: 0.6...1.8, neutral: 1,
                             format: { "\(Int($0 * 100))%" })
                Toggle("Стеклянная подложка", isOn: $subtitleGlass)
                    .toggleStyle(.switch)
                    .controlSize(.small)
            }
        }
        .font(.system(size: 12.5))
    }
}

struct QueuePanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var engine = model.engine
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Button { engine.shuffle.toggle() } label: { Image(systemName: "shuffle") }
                    .buttonStyle(.glass)
                    .tint(engine.shuffle ? .accentColor : nil)
                    .help("Перемешать")
                Button { engine.cycleRepeat() } label: { Image(systemName: engine.repeatMode.symbol) }
                    .buttonStyle(.glass)
                    .tint(engine.repeatMode != .off ? .accentColor : nil)
                    .help("Повтор")
                Spacer()
                Button { model.presentOpenPanel() } label: { Label("Добавить", systemImage: "plus") }
                    .buttonStyle(.glass)
            }
            .controlSize(.small)

            VStack(spacing: 4) {
                ForEach(Array(engine.queue.enumerated()), id: \.element.id) { index, item in
                    QueueRow(item: item, index: index, isCurrent: index == engine.queueIndex)
                        .onTapGesture { engine.playQueueItem(at: index) }
                        .contextMenu {
                            Button("Воспроизвести") { engine.playQueueItem(at: index) }
                            Button("Убрать из очереди") { engine.removeFromQueue(at: IndexSet(integer: index)) }
                                .disabled(index == engine.queueIndex)
                            if index > 0 {
                                Button("Поднять выше") { engine.moveInQueue(from: IndexSet(integer: index), to: index - 1) }
                            }
                        }
                }
            }
            if engine.queue.count <= 1 {
                Text("Откройте папку (⇧⌘O) — все видео в ней встанут в очередь")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
        }
    }
}

private struct QueueRow: View {
    @Environment(AppModel.self) private var model
    let item: MediaItem
    let index: Int
    let isCurrent: Bool
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                if let image = model.library.thumbnails[item.id] {
                    Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
                } else {
                    Rectangle().fill(.white.opacity(0.08))
                    Image(systemName: "film").foregroundStyle(.secondary)
                }
                if isCurrent {
                    Rectangle().fill(.black.opacity(0.4))
                    Image(systemName: "waveform")
                        .symbolEffect(.variableColor.iterative, isActive: model.engine.isPlaying)
                        .foregroundStyle(.white)
                }
            }
            .frame(width: 72, height: 40)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.system(size: 12.5, weight: isCurrent ? .semibold : .regular))
                    .lineLimit(2)
                Text(TimeFormat.string(item.duration))
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            Spacer(minLength: 0)
        }
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(.white.opacity(isCurrent ? 0.14 : (hovering ? 0.07 : 0))))
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
        .task { await model.library.loadThumbnail(for: item) }
    }
}

struct InfoPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let engine = model.engine
        let info = engine.info
        VStack(spacing: 12) {
            PanelSection(title: "Файл") {
                row("Имя", engine.current?.url.lastPathComponent ?? "—")
                row("Контейнер", info.container)
                row("Размер", info.fileSize > 0 ? Units.bytes(info.fileSize) : "—")
                row("Длительность", TimeFormat.string(info.duration))
                if let converted = info.convertedFrom {
                    row("Исходные кодеки", converted)
                    row("Воспроизведение", "Собственный демультиплексор Lumen")
                }
            }
            if info.hasVideo {
                PanelSection(title: "Видео") {
                    row("Кодек", info.videoCodec ?? "—")
                    row("Разрешение", "\(Int(info.displaySize.width)) × \(Int(info.displaySize.height))")
                    row("Частота кадров", info.frameRate > 0 ? String(format: "%.3g к/с", info.frameRate) : "—")
                    row("Битрейт", Units.bitrate(info.videoBitrate))
                    row("Динамический диапазон", info.dynamicRange ?? "SDR")
                }
            }
            PanelSection(title: "Звук") {
                row("Кодек", info.audioCodec ?? "—")
                row("Каналы", Units.channels(info.audioChannels))
                row("Частота", info.sampleRate > 0 ? String(format: "%.1f кГц", info.sampleRate / 1000) : "—")
                row("Дорожек", "\(max(engine.audioOptions.count, info.audioCodec == nil ? 0 : 1))")
            }
            if !engine.notes.isEmpty {
                PanelSection(title: "Замечания") {
                    ForEach(engine.notes, id: \.self) { note in
                        Text(note).font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                }
            }
            PanelSection(title: "Горячие клавиши") {
                ForEach(Self.shortcuts, id: \.0) { key, action in
                    row(key, action)
                }
            }
        }
    }

    static let shortcuts: [(String, String)] = [
        ("Пробел / K", "Пауза"),
        ("← → / J L", "Перемотка (⇧ ×3, ⌥ 1 с)"),
        ("↑ ↓", "Громкость"),
        (", .", "Покадрово"),
        ("[ ] \\", "Скорость"),
        ("S / A", "Субтитры / дорожка"),
        ("G H", "Сдвиг субтитров"),
        ("B", "Повтор A–B"),
        ("F / ⏎", "Полный экран"),
        ("P", "Картинка в картинке"),
        ("Z R", "Масштаб, поворот"),
        ("0–9", "Перейти к 0–90%"),
        ("Свайп", "Перемотка трекпадом"),
        ("Щипок", "Увеличение"),
    ]

    private func row(_ title: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value).multilineTextAlignment(.trailing).textSelection(.enabled)
        }
        .font(.system(size: 12))
    }
}
