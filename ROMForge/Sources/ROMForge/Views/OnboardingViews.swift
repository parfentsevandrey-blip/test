import SwiftUI

/// Первый запуск: поиск или загрузка adb/fastboot.
struct ToolsSetupView: View {
    @Environment(AppModel.self) private var model
    var error: String?

    var body: some View {
        ZStack {
            AmbientBackground(colors: [.teal, .indigo])
            VStack(spacing: 20) {
                IconTile(symbol: "hammer.fill", colors: [.teal, .indigo], size: 84)
                Text("Подготовка инструментов")
                    .font(.largeTitle.weight(.bold))
                Text("ROM Forge использует официальные Android Platform Tools (adb и fastboot). Если их нет на Mac, приложение скачает их с сайта Google.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: 460)

                switch model.tools.state {
                case .downloading(let value):
                    ProgressView(value: value) { Text("Загрузка Platform Tools… \(Int(value * 100))%") }
                        .frame(maxWidth: 320)
                case .failed:
                    Label(error ?? "Ошибка", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 480)
                    HStack {
                        Button("Указать папку…") { chooseFolder() }
                            .glassButton()
                            .controlSize(.large)
                        Button("Повторить") { model.retryTools() }
                            .prominentButton()
                            .controlSize(.large)
                    }
                default:
                    ProgressView().controlSize(.large)
                }
            }
            .padding(40)
            .glassCard(cornerRadius: 32)
            .padding(40)
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.message = "Выберите папку, в которой лежат adb и fastboot (например, platform-tools)"
        panel.prompt = "Использовать"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        model.tools.useCustomDirectory(url)
        model.retryTools()
    }
}

/// Экран, когда телефон не подключён или ещё не разрешил отладку.
struct ConnectDeviceView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ZStack {
            AmbientBackground(colors: [.green, .blue])
            ScrollView {
                VStack(spacing: 28) {
                    header
                    if let device = model.selectedDevice {
                        statusCard(for: device)
                    }
                    HStack(alignment: .top, spacing: 16) {
                        StepCard(number: 1, symbol: "hand.tap",
                                 title: "Режим разработчика",
                                 text: "Настройки → О телефоне → 7 раз нажмите «Номер сборки».")
                        StepCard(number: 2, symbol: "ladybug",
                                 title: "Отладка по USB",
                                 text: "Настройки → Система → Для разработчиков → включите «Отладка по USB».")
                        StepCard(number: 3, symbol: "cable.connector",
                                 title: "Подключите кабель",
                                 text: "Соедините телефон с Mac и разрешите отладку во всплывающем окне на телефоне.")
                    }
                    .frame(maxWidth: 820)
                }
                .padding(40)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Подключение")
    }

    private var header: some View {
        VStack(spacing: 14) {
            Image(systemName: "smartphone")
                .font(.system(size: 72, weight: .light))
                .foregroundStyle(.tint)
                .symbolEffect(.pulse)
            Text("Подключите Android-смартфон")
                .font(.largeTitle.weight(.bold))
            Text("ROM Forge покажет характеристики телефона и все прошивки, которые можно на него установить.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 30)
    }

    @ViewBuilder
    private func statusCard(for device: ConnectedDevice) -> some View {
        HStack(spacing: 14) {
            switch device.mode {
            case .unauthorized:
                Image(systemName: "lock.iphone").font(.title).foregroundStyle(.orange)
                VStack(alignment: .leading) {
                    Text("Разрешите отладку на телефоне").font(.headline)
                    Text("На экране «\(device.displayName)» появилось окно «Разрешить отладку по USB?». Отметьте «Всегда разрешать с этого компьютера» и нажмите «Разрешить».")
                        .foregroundStyle(.secondary)
                }
            case .android:
                ProgressView()
                Text("Считываем характеристики \(device.displayName)…").font(.headline)
            default:
                Image(systemName: "bolt.horizontal.circle").font(.title).foregroundStyle(.blue)
                VStack(alignment: .leading) {
                    Text("Телефон в режиме \(device.mode.title)").font(.headline)
                    Text("Чтобы увидеть характеристики, загрузите телефон в Android.")
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: 820)
        .glassCard(tint: device.mode == .unauthorized ? .orange : nil)
    }
}

private struct StepCard: View {
    var number: Int
    var symbol: String
    var title: String
    var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: symbol)
                    .font(.title2)
                    .foregroundStyle(.tint)
                Spacer()
                Text("\(number)")
                    .font(.title3.weight(.bold).monospacedDigit())
                    .foregroundStyle(.tertiary)
            }
            Text(title).font(.headline)
            Text(text).font(.callout).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 170, alignment: .topLeading)
        .glassCard()
    }
}
