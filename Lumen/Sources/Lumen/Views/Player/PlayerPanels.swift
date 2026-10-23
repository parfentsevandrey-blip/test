import SwiftUI

/// Боковая стеклянная панель: дорожки звука и субтитров, сведения о файле.
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
                    case .tracks: TracksPanel()
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
        .frame(width: 330)
        .glassEffect(.regular, in: .rect(cornerRadius: 28))
    }
}

private struct PanelSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .tracking(0.8)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(0.06)))
    }
}

/// Звук и субтитры текущего видео: всё видно сразу, переключение — один щелчок.
/// Дорожки, которые macOS не воспроизводит, показаны приглушёнными, без всплывающих ошибок.
struct TracksPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let engine = model.engine
        VStack(spacing: 12) {
            PanelSection(title: "Звук") {
                if engine.audioOptions.isEmpty {
                    Text("В файле нет звука").font(.system(size: 12.5)).foregroundStyle(.secondary).padding(.horizontal, 4)
                }
                ForEach(engine.audioOptions) { option in
                    TrackRow(title: option.title, detail: option.detail, selected: engine.selectedAudio == option.id,
                             enabled: option.available) {
                        engine.selectAudio(option.id)
                    }
                }
            }

            PanelSection(title: "Субтитры") {
                ForEach(engine.subtitleOptions) { option in
                    TrackRow(title: option.title, detail: option.detail, selected: engine.subtitleChoice == option.choice) {
                        engine.selectSubtitle(option.choice)
                    }
                }
                if engine.subtitleChoice != .off {
                    HStack(spacing: 8) {
                        Text("Задержка").foregroundStyle(.secondary)
                        Spacer()
                        Button { engine.shiftSubtitles(by: -0.1) } label: { Image(systemName: "minus") }
                            .buttonStyle(.glass).controlSize(.small)
                        Text(String(format: "%+.1f с", engine.subtitleDelay))
                            .monospacedDigit().frame(width: 54)
                        Button { engine.shiftSubtitles(by: 0.1) } label: { Image(systemName: "plus") }
                            .buttonStyle(.glass).controlSize(.small)
                    }
                    .font(.system(size: 12.5, weight: .medium))
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                }
                Button {
                    model.presentSubtitlePanel()
                } label: {
                    Label("Добавить файл субтитров…", systemImage: "plus.circle")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .font(.system(size: 12.5, weight: .medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
                .padding(.top, 4)
            }
        }
    }
}

private struct TrackRow: View {
    let title: String
    var detail = ""
    let selected: Bool
    var enabled = true
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .opacity(selected ? 1 : 0)
                    .frame(width: 14)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).lineLimit(1)
                        .font(.system(size: 13, weight: selected ? .semibold : .regular))
                    if !detail.isEmpty {
                        Text(detail).lineLimit(1)
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 5)
            .background(RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(.white.opacity(selected ? 0.13 : (hovering && enabled ? 0.07 : 0))))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
        .onHover { hovering = $0 }
        .help(enabled ? "" : "Этот формат звука macOS не воспроизводит")
    }
}

struct InfoPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let engine = model.engine
        let info = engine.info
        VStack(spacing: 12) {
            PanelSection(title: "Файл") {
                row("Имя", engine.url?.lastPathComponent ?? "—")
                row("Контейнер", info.container)
                row("Размер", info.fileSize > 0 ? Units.bytes(info.fileSize) : "—")
                row("Длительность", TimeFormat.string(info.duration))
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
        ("S / A", "Субтитры / звук по кругу"),
        ("T", "Звук и субтитры"),
        ("G H", "Сдвиг субтитров"),
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
