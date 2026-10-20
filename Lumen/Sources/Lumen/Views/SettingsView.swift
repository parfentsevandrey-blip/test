import SwiftUI

struct SettingsView: View {
    var body: some View {
        TabView {
            Tab("Основные", systemImage: "gearshape") { GeneralSettings() }
            Tab("Изображение", systemImage: "sparkles.tv") { VisualSettings() }
            Tab("Форматы", systemImage: "film.stack") { FormatSettings() }
        }
        .frame(width: 540, height: 430)
    }
}

private struct GeneralSettings: View {
    @AppStorage(Prefs.resumePlayback) private var resume = true
    @AppStorage(Prefs.autoplayNext) private var autoplay = true
    @AppStorage(Prefs.fitWindowToVideo) private var fitWindow = true
    @AppStorage(Prefs.autoSubtitles) private var autoSubtitles = false
    @AppStorage(Prefs.skipInterval) private var skip = 10.0
    @AppStorage(Prefs.skimmingPreviews) private var skimming = true
    @Environment(AppModel.self) private var model

    var body: some View {
        Form {
            Section("Воспроизведение") {
                Toggle("Продолжать с места остановки", isOn: $resume)
                Toggle("Автоматически включать следующее видео", isOn: $autoplay)
                Toggle("Включать субтитры автоматически", isOn: $autoSubtitles)
                Picker("Шаг перемотки", selection: $skip) {
                    Text("5 секунд").tag(5.0)
                    Text("10 секунд").tag(10.0)
                    Text("15 секунд").tag(15.0)
                    Text("30 секунд").tag(30.0)
                }
            }
            Section("Окно и библиотека") {
                Toggle("Подгонять окно под пропорции видео", isOn: $fitWindow)
                Toggle("Листать кадры при наведении на обложку", isOn: $skimming)
                LabeledContent("История") {
                    Button("Сбросить позиции просмотра") { model.library.clearHistory() }
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct VisualSettings: View {
    @AppStorage(Prefs.ambientEnabled) private var ambient = true
    @AppStorage(Prefs.ambientIntensity) private var intensity = 0.85
    @AppStorage(Prefs.cinemaStage) private var stage = true
    @AppStorage(Prefs.subtitleScale) private var subtitleScale = 1.0
    @AppStorage(Prefs.subtitleGlass) private var subtitleGlass = true

    var body: some View {
        Form {
            Section("Атмосфера") {
                Toggle("Живая подсветка из цветов кадра", isOn: $ambient)
                Slider(value: $intensity, in: 0.2...1) { Text("Интенсивность") }
                    .disabled(!ambient)
                Toggle("Сцена: видео парит над свечением в окне", isOn: $stage)
            }
            Section("Субтитры") {
                Slider(value: $subtitleScale, in: 0.6...1.8) { Text("Размер") }
                Toggle("Стеклянная подложка под текстом", isOn: $subtitleGlass)
            }
        }
        .formStyle(.grouped)
    }
}

private struct FormatSettings: View {
    @State private var cacheSize: Int64 = 0

    var body: some View {
        Form {
            Section("Без перекодирования (системные кодеки)") {
                LabeledContent("Контейнеры", value: "MP4, MOV, M4V, 3GP")
                LabeledContent("Видео", value: "H.264, HEVC, AV1, ProRes, Dolby Vision")
                LabeledContent("Звук", value: "AAC, AC-3, E-AC-3, MP3, ALAC, FLAC")
            }
            Section("Собственный демультиплексор Lumen") {
                LabeledContent("Контейнеры", value: "MKV, MKA, WebM")
                LabeledContent("Перепаковка 1:1", value: "H.264, HEVC, AV1 + AAC, AC-3, E-AC-3, MP3")
                LabeledContent("Перекодирование", value: "VP9 → HEVC (медиадвижок Apple)")
                LabeledContent("Субтитры", value: "SRT, ASS/SSA, WebVTT, главы")
                Text("Matroska перепаковывается один раз и кэшируется: качество не теряется, следующий запуск мгновенный.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            Section("Кэш") {
                LabeledContent("Занято", value: Units.bytes(cacheSize))
                Button("Очистить кэш перепакованных файлов") {
                    MediaResolver.clearCache()
                    cacheSize = 0
                }
            }
        }
        .formStyle(.grouped)
        .task {
            cacheSize = await Task.detached(priority: .utility) { MediaResolver.cacheSize() }.value
        }
    }
}
