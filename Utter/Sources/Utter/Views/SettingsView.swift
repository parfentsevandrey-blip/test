import AppKit
import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = DemoUI.settingsTab

    var body: some View {
        TabView(selection: $tab) {
            general.tabItem { Label("Основное", systemImage: "keyboard") }.tag(0)
            recognition.tabItem { Label("Распознавание", systemImage: "waveform") }.tag(1)
            input.tabItem { Label("Ввод", systemImage: "character.cursor.ibeam") }.tag(2)
            historyTab.tabItem { Label("История", systemImage: "clock") }.tag(3)
        }
        .frame(width: 560, height: 470)
    }

    // MARK: Основное

    private var general: some View {
        @Bindable var model = model
        return Form {
            Section("Горячая клавиша") {
                LabeledContent("Сочетание") { ShortcutRecorder() }
                if model.hotkeyConflict {
                    Label("Это сочетание занято другой программой — выберите другое", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange).font(.system(size: 11.5))
                }
                Picker("Режим", selection: $model.trigger) {
                    ForEach(Trigger.allCases) { Text($0.title).tag($0) }
                }
                Text(model.trigger.hint).font(.system(size: 11)).foregroundStyle(.secondary)
                Toggle("Также — удерживать правый ⌥ (как рацию)", isOn: $model.rightOption)
                Text("Esc во время диктовки — закончить.").font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Удобство") {
                Toggle("Звук в начале и в конце диктовки", isOn: $model.sounds)
                Toggle("Запускать при входе в систему", isOn: Binding(get: { model.launchAtLogin }, set: { model.launchAtLogin = $0 }))
            }
        }
        .formStyle(.grouped)
    }

    // MARK: Распознавание

    private var recognition: some View {
        @Bindable var model = model
        return Form {
            Section("Модель") {
                ForEach(AsrModel.all) { m in
                    HStack(spacing: 10) {
                        Image(systemName: model.asrModelID == m.id ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(model.asrModelID == m.id ? Color.accentColor : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(m.title).font(.system(size: 13, weight: .semibold))
                                if m.id == AsrModel.recommended.id {
                                    Text("для вашего Mac").font(.system(size: 10, weight: .semibold)).foregroundStyle(.white)
                                        .padding(.horizontal, 6).padding(.vertical, 1).background(Capsule().fill(Color.accentColor))
                                }
                            }
                            Text("\(m.summary) \(Format.bytes(m.size)).").font(.system(size: 11)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if m.installed {
                            if model.asrModelID != m.id { Button("Удалить") { model.deleteModel(m) }.controlSize(.small) }
                        } else if case .downloading = model.modelState, model.asrModelID == m.id {
                            ProgressView().controlSize(.small)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        model.asrModelID = m.id
                        model.downloadModel(m)
                    }
                }
                Text("Qwen3-ASR работает целиком на вашем Mac через llama.cpp (Metal): звук никуда не отправляется, интернет не нужен.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Фразы") {
                LabeledContent("Пауза, после которой фраза дописывается") {
                    HStack {
                        Slider(value: Binding(get: { Double(model.pauseMs) }, set: { model.pauseMs = Int($0) }), in: 400...1500, step: 50)
                            .frame(width: 160)
                        Text(String(format: "%.2f с", Double(model.pauseMs) / 1000)).monospacedDigit().frame(width: 52)
                    }
                }
                Toggle("Голосовые команды", isOn: $model.commands)
                Text("«Новая строка», «Новый абзац», «Удали это» — отдельной фразой.").font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Мой словарь") {
                TextEditor(text: $model.dictionary)
                    .font(.system(size: 12))
                    .frame(height: 70)
                Text("Имена, термины, названия через запятую — модель будет писать их правильно. Например: Утёсов, Kubernetes, ВТБ.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }

    // MARK: Ввод

    private var input: some View {
        @Bindable var model = model
        return Form {
            Section("Как текст попадает в поле") {
                Picker("Способ", selection: $model.insertion) {
                    ForEach(Typer.Mode.allCases, id: \.self) { Text($0.title).tag($0) }
                }
                .pickerStyle(.radioGroup)
                Text(model.insertion == .typing
                     ? "Слова появляются в поле по мере речи, как будто их печатают. Подходит почти везде."
                     : "Фраза вставляется целиком после паузы через буфер обмена (его содержимое восстанавливается). Для программ, которые плохо принимают быстрый ввод.")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            Section("Разрешения") {
                permission("Микрофон", granted: model.micGranted) { model.requestMicrophone() }
                permission("Универсальный доступ (ввод текста)", granted: model.accessibilityGranted) { model.requestAccessibility() }
                permission("Распознавание macOS (пока модель скачивается)", granted: model.speechGranted) { model.requestSpeech() }
            }
        }
        .formStyle(.grouped)
    }

    private func permission(_ title: String, granted: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            Image(systemName: granted ? "checkmark.circle.fill" : "exclamationmark.circle.fill").foregroundStyle(granted ? .green : .orange)
            Text(title)
            Spacer()
            if !granted { Button("Разрешить", action: action).controlSize(.small) }
        }
    }

    // MARK: История

    private var historyTab: some View {
        @Bindable var model = model
        return VStack(alignment: .leading, spacing: 10) {
            Toggle("Хранить историю диктовок (только на этом Mac)", isOn: $model.keepHistory)
            List(model.history) { entry in
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.text).font(.system(size: 12)).lineLimit(3).textSelection(.enabled)
                    Text([Format.relative(entry.date), entry.app].compactMap { $0 }.joined(separator: " · "))
                        .font(.system(size: 10.5)).foregroundStyle(.secondary)
                }
                .contextMenu { Button("Скопировать") { model.copy(entry) } }
            }
            .overlay { if model.history.isEmpty { ContentUnavailableView("Пока пусто", systemImage: "text.bubble") } }
            HStack {
                Spacer()
                Button("Очистить") { model.history = [] }.disabled(model.history.isEmpty)
            }
        }
        .padding(20)
    }
}

/// Кнопка записи сочетания: нажмите и затем нужные клавиши.
struct ShortcutRecorder: View {
    @Environment(AppModel.self) private var model
    @State private var monitor: Any?

    var body: some View {
        Button {
            model.recordingShortcut ? stop() : start()
        } label: {
            Text(model.recordingShortcut ? "Нажмите сочетание…" : model.shortcut.display)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .frame(minWidth: 140)
        }
        .buttonStyle(.glass)
        .onDisappear { stop() }
    }

    private func start() {
        model.recordingShortcut = true
        monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            if event.keyCode == 53 && event.modifierFlags.intersection(.deviceIndependentFlagsMask).isEmpty {
                stop()
                return nil
            }
            if let shortcut = Shortcut.from(event) {
                model.shortcut = shortcut
                stop()
            }
            return nil
        }
    }

    private func stop() {
        if let monitor { NSEvent.removeMonitor(monitor) }
        monitor = nil
        if model.recordingShortcut { model.recordingShortcut = false }
    }
}
