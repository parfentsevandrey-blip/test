import SwiftUI

/// Первый запуск: разрешения, модель, проба прямо здесь.
struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @State private var sample = ""
    @FocusState private var sampleFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                MicOrb(level: model.level, active: model.phase == .listening, speaking: model.speaking)
                    .frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Utter — диктовка в любое поле").font(.system(size: 24, weight: .bold))
                    Text("Нажмите \(model.shortcut.display), говорите по-русски — текст печатается там, где стоит курсор. Всё распознаётся на этом Mac.")
                        .font(.system(size: 13)).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 18)

            step(1, "Микрофон", done: model.micGranted, text: "Чтобы слышать вас. Звук никуда не отправляется.") {
                Button("Разрешить") { model.requestMicrophone() }.buttonStyle(.glassProminent)
            }
            step(2, "Ввод текста", done: model.accessibilityGranted,
                 text: "Разрешение «Универсальный доступ» — чтобы печатать в поле другой программы. Откроются настройки: включите Utter в списке.") {
                Button("Открыть настройки") { model.requestAccessibility() }.buttonStyle(.glassProminent)
            }
            step(3, "Модель распознавания", done: model.modelState == .ready, text: modelText) {
                switch model.modelState {
                case .missing, .failed: Button("Скачать") { model.downloadModel() }.buttonStyle(.glassProminent)
                case .downloading(let p): ProgressView(value: p).frame(width: 120)
                case .loading: ProgressView().controlSize(.small)
                case .ready: EmptyView()
                }
            }
            if model.modelState != .ready && !model.speechGranted {
                HStack(spacing: 8) {
                    Image(systemName: "bolt.fill").foregroundStyle(.yellow)
                    Text("Не хотите ждать загрузку? Пока модель качается, можно диктовать встроенным распознаванием macOS.")
                        .font(.system(size: 11.5)).foregroundStyle(.secondary)
                    Spacer()
                    Button("Включить") { model.requestSpeech() }.buttonStyle(.glass).controlSize(.small)
                }
                .padding(.leading, 44)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("4. Попробуйте прямо здесь").font(.system(size: 14, weight: .semibold))
                Text("Щёлкните в поле ниже, нажмите \(model.shortcut.display) и скажите: «Привет! Это проверка диктовки». Короткое нажатие — включить и выключить, удержание — говорить, пока держите.")
                    .font(.system(size: 11.5)).foregroundStyle(.secondary)
                TextEditor(text: $sample)
                    .font(.system(size: 14))
                    .focused($sampleFocused)
                    .scrollContentBackground(.hidden)
                    .padding(10)
                    .frame(height: 110)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.primary.opacity(0.05)))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(sampleFocused ? Color.accentColor.opacity(0.6) : .primary.opacity(0.1)))
            }

            HStack {
                Text("Utter живёт в строке меню — значок \(Image(systemName: "waveform")) вверху экрана.")
                    .font(.system(size: 11.5)).foregroundStyle(.secondary)
                Spacer()
                Button("Готово") { model.closeOnboarding() }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(28)
        .frame(width: 640)
    }

    private var modelText: String {
        let m = model.asrModel
        switch model.modelState {
        case .downloading(let p): return "\(m.title) — \(Int(p * 100))% из \(Format.bytes(m.size)). Подобрана под ваш Mac (\(Int(Hardware.memoryGB.rounded())) ГБ памяти)."
        case .ready: return "\(m.title) готова: распознаёт \(Hardware.virtualized ? "на процессоре" : "на видеоядре") Mac, без интернета."
        case .loading: return "\(m.title) загружается в память…"
        case .failed(let why): return why
        case .missing: return "\(m.title) · \(Format.bytes(m.size)). \(m.summary)"
        }
    }

    private func step<Trailing: View>(_ n: Int, _ title: String, done: Bool, text: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(alignment: .center, spacing: 14) {
            ZStack {
                Circle().fill(done ? Color.green : Color.primary.opacity(0.08)).frame(width: 30, height: 30)
                if done {
                    Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                } else {
                    Text("\(n)").font(.system(size: 13, weight: .bold)).foregroundStyle(.secondary)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14, weight: .semibold))
                Text(text).font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(3)
            }
            Spacer(minLength: 10)
            if !done { trailing() }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(.primary.opacity(done ? 0.03 : 0.05)))
    }
}
