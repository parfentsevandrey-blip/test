import SwiftUI

struct SettingsView: View {
    var body: some View {
        TabView {
            Tab("Основные", systemImage: "gearshape") { GeneralSettings() }
            Tab("Вид", systemImage: "sparkles.tv") { VisualSettings() }
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

    var body: some View {
        Form {
            Section("Воспроизведение") {
                Toggle("Продолжать с места остановки", isOn: $resume)
                Toggle("Играть следующий файл, если открыто несколько", isOn: $autoplay)
                Toggle("Включать субтитры автоматически", isOn: $autoSubtitles)
                Picker("Шаг перемотки", selection: $skip) {
                    Text("5 секунд").tag(5.0)
                    Text("10 секунд").tag(10.0)
                    Text("15 секунд").tag(15.0)
                    Text("30 секунд").tag(30.0)
                }
            }
            Section("Окно") {
                Toggle("Подгонять окно под пропорции видео", isOn: $fitWindow)
                LabeledContent("Позиции просмотра") {
                    Button("Забыть") { ResumeStore.clear() }
                }
            }
        }
        .formStyle(.grouped)
    }
}

private struct VisualSettings: View {
    @AppStorage(Prefs.ambientEnabled) private var ambient = true
    @AppStorage(Prefs.ambientIntensity) private var intensity = 0.85
    @AppStorage(Prefs.cinemaStage) private var stage = false
    @AppStorage(Prefs.subtitleScale) private var subtitleScale = 1.0
    @AppStorage(Prefs.subtitleGlass) private var subtitleGlass = false

    var body: some View {
        Form {
            Section("Атмосфера") {
                Toggle("Живая подсветка из цветов кадра", isOn: $ambient)
                Slider(value: $intensity, in: 0.2...1) { Text("Интенсивность") }
                    .disabled(!ambient)
                Toggle("Сцена: видео парит над свечением в окне", isOn: $stage)
            }
            Section("Панель управления") {
                LabeledContent("Положение") {
                    Button("Вернуть вниз по центру") {
                        UserDefaults.standard.set(0.5, forKey: Prefs.islandX)
                        UserDefaults.standard.set(1.0, forKey: Prefs.islandY)
                    }
                }
                Text("Панель можно перетащить в любое место окна — у краёв и в углах она примагничивается.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
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
    var body: some View {
        Form {
            Section("Системные кодеки macOS") {
                LabeledContent("Контейнеры", value: "MP4, MOV, M4V, 3GP")
                LabeledContent("Видео", value: "H.264, HEVC, AV1, ProRes, Dolby Vision")
                LabeledContent("Звук", value: "AAC, AC-3, E-AC-3, MP3, ALAC, FLAC")
            }
            Section("Собственный движок Lumen") {
                LabeledContent("Контейнеры", value: "MKV, MKA, WebM")
                LabeledContent("Видео", value: "H.264, HEVC, AV1, VP9")
                LabeledContent("Звук", value: "DTS, AAC, AC-3, E-AC-3, FLAC, Opus, MP3, PCM")
                LabeledContent("Субтитры", value: "SRT, ASS/SSA, WebVTT, Blu-ray (PGS)")
                Text("Matroska читается потоком прямо во время просмотра: открытие мгновенное при любом размере файла, без копий на диске.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }
}
