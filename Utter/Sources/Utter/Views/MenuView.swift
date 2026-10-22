import AppKit
import SwiftUI

/// Окно из строки меню: большая кнопка, состояние модели, разрешения, последние диктовки.
struct MenuView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            recordButton
            languagePicker
            ModelStatusCard()
            if !model.micGranted || !model.accessibilityGranted { permissions }
            if !model.history.isEmpty { historyList }
            footer
        }
        .padding(16)
        .frame(width: 360)
    }

    private var header: some View {
        HStack(spacing: 12) {
            MicOrb(level: model.level, active: model.phase == .listening, speaking: model.speaking)
                .frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text("Utter").font(.system(size: 17, weight: .bold))
                Text(statusLine).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer()
        }
    }

    private var statusLine: String {
        switch model.phase {
        case .listening: return "Слушаю — говорите в любое поле ввода"
        case .finishing: return "Дописываю последнюю фразу…"
        case .idle:
            if model.hotkeyConflict { return "Сочетание \(model.shortcut.display) занято другой программой — выберите другое" }
            return "Диктовка по \(model.shortcut.display)" + (model.rightOption ? " или правому ⌥" : "")
        }
    }

    /// Язык речи — под рукой: переключается и посреди работы (действует со следующей диктовки).
    private var languagePicker: some View {
        @Bindable var model = model
        return Picker("Язык", selection: $model.speechLanguage) {
            ForEach(SpeechLanguage.allCases) { Text($0.title).tag($0) }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .disabled(model.phase != .idle)
        .help("Язык, на котором вы говорите")
    }

    private var recordButton: some View {
        Button { model.toggle() } label: {
            HStack {
                Image(systemName: model.phase == .idle ? "mic.fill" : "stop.fill")
                Text(model.phase == .idle ? "Начать диктовку" : "Закончить")
                Spacer()
                Text(model.shortcut.display).font(.system(size: 12, design: .rounded)).opacity(0.8)
            }
            .font(.system(size: 14, weight: .semibold))
            .padding(.vertical, 6)
            .padding(.horizontal, 4)
        }
        .buttonStyle(.glassProminent)
        .tint(model.phase == .idle ? Color(red: 0.45, green: 0.4, blue: 1) : .red)
        .controlSize(.large)
        .disabled(model.phase == .finishing)
    }

    private var permissions: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !model.micGranted {
                PermissionRow(symbol: "mic.slash.fill", title: "Нет доступа к микрофону", button: "Разрешить") { model.requestMicrophone() }
            }
            if !model.accessibilityGranted {
                PermissionRow(symbol: "keyboard.badge.exclamationmark", title: "Нет доступа к вводу текста — будет копироваться в буфер",
                              button: "Открыть") { model.requestAccessibility() }
            }
        }
    }

    private var historyList: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Недавнее").font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
            ForEach(model.history.prefix(4)) { entry in
                Button { model.copy(entry) } label: {
                    HStack(alignment: .top, spacing: 8) {
                        Text(entry.text).font(.system(size: 12)).lineLimit(2).multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "doc.on.doc").font(.system(size: 10)).foregroundStyle(.tertiary)
                    }
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.primary.opacity(0.05)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .help("Скопировать")
            }
        }
    }

    private var footer: some View {
        HStack {
            Button("Настройки…") { model.showSettings() }
            .keyboardShortcut(",")
            Spacer()
            Button("Выйти") { NSApp.terminate(nil) }.keyboardShortcut("q")
        }
        .buttonStyle(.glass)
        .controlSize(.small)
    }
}

/// Состояние модели распознавания.
struct ModelStatusCard: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: symbol).font(.system(size: 16)).foregroundStyle(color).frame(width: 22)
            VStack(alignment: .leading, spacing: 3) {
                Text(model.asrModel.title).font(.system(size: 12.5, weight: .semibold))
                switch model.modelState {
                case .downloading(let p):
                    ProgressView(value: p).controlSize(.small)
                    Text("Скачиваю: \(Int(p * 100))% из \(Format.bytes(model.asrModel.size))" + (model.downloadSpeed > 0 ? " · \(Format.bytes(Int64(model.downloadSpeed)))/с" : "")
                         + (model.speechGranted ? " · пока работает распознавание macOS" : ""))
                        .font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
                case .failed(let why):
                    Text(why).font(.system(size: 10.5)).foregroundStyle(.red).lineLimit(2)
                    Button("Повторить") { model.downloadModel() }.controlSize(.small)
                default:
                    Text(detail).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.primary.opacity(0.05)))
    }

    private var symbol: String {
        switch model.modelState {
        case .ready: "checkmark.seal.fill"
        case .downloading: "arrow.down.circle"
        case .loading: "hourglass"
        case .missing: "arrow.down.circle.dotted"
        case .failed: "exclamationmark.triangle.fill"
        }
    }

    private var color: Color {
        switch model.modelState {
        case .ready: .green
        case .failed: .red
        default: .secondary
        }
    }

    private var detail: String {
        switch model.modelState {
        case .ready: return "Работает на этом Mac · \(Hardware.virtualized ? "процессор" : "Metal") · без интернета"
        case .loading: return "Загружаю в память…"
        case .missing: return "Не скачана · \(Format.bytes(model.asrModel.size))"
        default: return ""
        }
    }
}

struct PermissionRow: View {
    let symbol: String
    let title: String
    let button: String
    let action: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).foregroundStyle(.orange)
            Text(title).font(.system(size: 11.5)).lineLimit(2)
            Spacer()
            Button(button, action: action).controlSize(.small).buttonStyle(.glass)
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.orange.opacity(0.1)))
    }
}
