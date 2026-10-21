import SwiftUI

/// Плавающий стеклянный остров внизу окна с крупными кнопками управления.
/// Слева — действия над выбранной раздачей (появляются и «вытекают» из острова при выборе),
/// справа — общие: пауза и продолжение для всех.
struct ControlIsland: View {
    @Environment(AppModel.self) private var model
    @State private var confirmRemove = false

    private var anyRunning: Bool { model.torrents.contains { $0.status.isActive } }
    private var anyStopped: Bool { model.torrents.contains { [.paused, .finished, .error].contains($0.status) } }

    var body: some View {
        // Кнопки — капли одного стекла: внутри группы они сливаются, между группами — просвет.
        GlassEffectContainer(spacing: 10) {
            HStack(spacing: 22) {
                if let t = model.selected {
                    selectedControls(t)
                        .transition(.scale(scale: 0.6, anchor: .trailing).combined(with: .opacity))
                }
                HStack(spacing: 8) {
                    IslandButton(symbol: "pause.circle.fill", title: "Пауза всех") { model.pauseAll() }
                        .disabled(!anyRunning)
                    IslandButton(symbol: "play.circle.fill", title: "Продолжить все") { model.resumeAll() }
                        .disabled(!anyStopped)
                }
            }
        }
        .animation(.bouncy(duration: 0.45), value: model.selectedID)
    }

    private func selectedControls(_ t: TorrentSnapshot) -> some View {
        let paused = [.paused, .finished, .error].contains(t.status)
        return HStack(spacing: 8) {
            IslandButton(symbol: paused ? "play.fill" : "pause.fill",
                         title: paused ? "Продолжить" : "Пауза",
                         tint: t.status.color, prominent: true) { model.toggle(t) }
            IslandButton(symbol: t.isSequential ? "text.line.first.and.arrowtriangle.forward" : "square.grid.3x3.fill",
                         title: t.isSequential ? "По порядку" : "Вразнобой",
                         active: t.isSequential) { model.setSequential(t, !t.isSequential) }
                .help("Последовательная загрузка — чтобы смотреть видео во время скачивания")
            IslandButton(symbol: "folder.fill", title: "В Finder") { model.reveal(t) }
            IslandButton(symbol: "link", title: "Magnet") { model.copyMagnet(t) }
            IslandButton(symbol: "trash.fill", title: "Удалить", destructive: true) { confirmRemove = true }
        }
        .confirmationDialog("Удалить «\(t.name)»?", isPresented: $confirmRemove) {
            Button("Убрать из списка") { model.remove(t, deleteFiles: false) }
            Button("Удалить вместе с файлами", role: .destructive) { model.remove(t, deleteFiles: true) }
        }
    }
}

/// Крупная кнопка острова: символ и подпись под ним.
private struct IslandButton: View {
    let symbol: String
    let title: String
    var tint: Color = .accentColor
    var prominent = false
    var active = false
    var destructive = false
    let action: () -> Void

    @Environment(\.isEnabled) private var isEnabled
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(height: 24)
                    .symbolEffect(.bounce, value: hovering)
                Text(title)
                    .font(.system(size: 10.5, weight: .semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(prominent ? AnyShapeStyle(.white) : destructive ? AnyShapeStyle(Color.red) : active ? AnyShapeStyle(tint) : AnyShapeStyle(.primary))
            .frame(width: 78, height: 58)
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .glassEffect(glassStyle, in: .rect(cornerRadius: 18, style: .continuous))
        .opacity(isEnabled ? 1 : 0.4)
        .onHover { hovering = $0 }
        .help(title)
    }

    private var glassStyle: Glass {
        if prominent { return .regular.tint(tint.opacity(0.85)).interactive() }
        if active { return .regular.tint(tint.opacity(0.25)).interactive() }
        return .regular.interactive()
    }
}
