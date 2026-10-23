import SwiftUI

/// Строка меню. Клавиши без ⌘ в плеере перехватывает PlayerInputMonitor —
/// здесь они показаны для наглядности и работают, когда монитор не активен.
struct LumenCommands: Commands {
    let model: AppModel

    private var engine: PlayerEngine { model.engine }
    private var inPlayer: Bool { engine.phase == .ready }

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("Открыть…") { model.presentOpenPanel() }
                .keyboardShortcut("o")
            Button("Добавить субтитры…") { model.presentSubtitlePanel() }
                .keyboardShortcut("o", modifiers: [.command, .option])
                .disabled(!inPlayer)
            Divider()
            Button("Сохранить кадр") { engine.captureFrame() }
                .keyboardShortcut("s", modifiers: [.command, .shift])
                .disabled(!inPlayer || !engine.info.hasVideo)
            Button("Показать в Finder") { model.revealCurrentInFinder() }
                .keyboardShortcut("r", modifiers: [.command, .shift])
                .disabled(!engine.hasMedia)
            Divider()
            Button("Закрыть видео") { engine.close() }
                .keyboardShortcut("w", modifiers: [.command, .shift])
                .disabled(!engine.hasMedia)
        }

        CommandMenu("Воспроизведение") {
            Button(engine.isPlaying ? "Пауза" : "Воспроизвести") { engine.togglePlay() }
                .keyboardShortcut(.space, modifiers: [])
                .disabled(!inPlayer)
            Divider()
            Button("Вперёд на \(Int(Prefs.skip)) с") { engine.skip(by: Prefs.skip) }
                .keyboardShortcut(.rightArrow, modifiers: [])
                .disabled(!inPlayer)
            Button("Назад на \(Int(Prefs.skip)) с") { engine.skip(by: -Prefs.skip) }
                .keyboardShortcut(.leftArrow, modifiers: [])
                .disabled(!inPlayer)
            Button("Следующий кадр") { engine.stepFrame(1) }
                .keyboardShortcut(".", modifiers: [])
                .disabled(!inPlayer)
            Button("Предыдущий кадр") { engine.stepFrame(-1) }
                .keyboardShortcut(",", modifiers: [])
                .disabled(!inPlayer)
            Divider()
            Button("Быстрее") { engine.changeRate(by: 1) }
                .keyboardShortcut("]", modifiers: [])
                .disabled(!inPlayer)
            Button("Медленнее") { engine.changeRate(by: -1) }
                .keyboardShortcut("[", modifiers: [])
                .disabled(!inPlayer)
            Button("Обычная скорость") { engine.setRate(1) }
                .keyboardShortcut("\\", modifiers: [])
                .disabled(!inPlayer)
            if engine.hasPlaylist {
                Divider()
                Button("Следующий файл") { engine.next() }
                    .keyboardShortcut(.rightArrow, modifiers: [.command])
                    .disabled(!engine.hasNext)
                Button("Предыдущий файл") { engine.previous() }
                    .keyboardShortcut(.leftArrow, modifiers: [.command])
            }
        }

        CommandMenu("Видео") {
            Button("Полноэкранный режим") { model.toggleFullScreen() }
                .keyboardShortcut("f", modifiers: [])
                .disabled(!engine.hasMedia)
            Button("Картинка в картинке") { engine.togglePictureInPicture() }
                .keyboardShortcut("p", modifiers: [])
                .disabled(!inPlayer)
            Divider()
            Picker("Масштаб", selection: Binding(get: { engine.gravity }, set: { engine.gravity = $0 })) {
                ForEach(VideoGravity.allCases, id: \.self) { Text($0.title).tag($0) }
            }
            Button("Повернуть на 90°") { engine.rotate() }
                .keyboardShortcut("r", modifiers: [])
                .disabled(!inPlayer)
            Button("Отразить") { engine.mirrored.toggle() }
                .disabled(!inPlayer)
            Button("Сбросить геометрию") { engine.resetTransform() }
                .disabled(!inPlayer)
        }

        CommandMenu("Звук") {
            Button(engine.isMuted ? "Включить звук" : "Выключить звук") { engine.toggleMute() }
                .keyboardShortcut("m", modifiers: [])
                .disabled(!inPlayer)
            Button("Громче") { engine.changeVolume(by: 0.05) }
                .keyboardShortcut(.upArrow, modifiers: [])
                .disabled(!inPlayer)
            Button("Тише") { engine.changeVolume(by: -0.05) }
                .keyboardShortcut(.downArrow, modifiers: [])
                .disabled(!inPlayer)
            if !engine.audioOptions.isEmpty {
                Divider()
                Picker("Дорожка", selection: Binding(get: { engine.selectedAudio }, set: { engine.selectAudio($0) })) {
                    ForEach(engine.audioOptions) { option in
                        Text(option.title).tag(option.id).disabled(!option.available)
                    }
                }
            }
        }

        CommandMenu("Субтитры") {
            Picker("Дорожка", selection: Binding(get: { engine.subtitleChoice }, set: { engine.selectSubtitle($0) })) {
                ForEach(engine.subtitleOptions) { option in
                    Text(option.title).tag(option.choice)
                }
            }
            Divider()
            Button("Субтитры раньше на 0,1 с") { engine.shiftSubtitles(by: -0.1) }
                .keyboardShortcut("g", modifiers: [])
                .disabled(!inPlayer)
            Button("Субтитры позже на 0,1 с") { engine.shiftSubtitles(by: 0.1) }
                .keyboardShortcut("h", modifiers: [])
                .disabled(!inPlayer)
            Button("Добавить файл субтитров…") { model.presentSubtitlePanel() }
                .disabled(!inPlayer)
        }
    }
}
