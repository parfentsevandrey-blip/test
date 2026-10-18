import SwiftUI

struct DeviceOverviewView: View {
    @Environment(AppModel.self) private var model
    var specs: DeviceSpecs

    private let columns = [GridItem(.adaptive(minimum: 300), spacing: 16, alignment: .top)]

    var body: some View {
        ZStack {
            AmbientBackground(colors: [.blue, .purple])
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    hero
                    LazyVGrid(columns: columns, spacing: 16) {
                        SpecSection(title: "Система", symbol: "gearshape.2", rows: systemRows)
                        SpecSection(title: "Железо", symbol: "cpu", rows: hardwareRows)
                        SpecSection(title: "Экран и батарея", symbol: "battery.75percent", rows: displayRows)
                        SpecSection(title: "Загрузчик и разделы", symbol: "lock.rectangle.stack", rows: bootRows)
                    }
                }
                .padding(28)
            }
        }
        .navigationTitle(specs.displayName)
        .navigationSubtitle(specs.codename)
        .toolbar {
            ToolbarItem {
                Button { model.reloadSelected() } label: {
                    Label("Обновить", systemImage: "arrow.clockwise")
                }
            }
        }
    }

    private var hero: some View {
        HStack(spacing: 28) {
            PhoneIllustration(batteryLevel: specs.batteryLevel)
            VStack(alignment: .leading, spacing: 10) {
                Text(specs.brand.uppercased())
                    .font(.caption.weight(.semibold))
                    .tracking(2)
                    .foregroundStyle(.secondary)
                Text(specs.displayName)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                HStack(spacing: 8) {
                    Badge(text: "Android \(specs.androidVersion)", symbol: "smartphone", color: .green)
                    Badge(text: specs.codename, symbol: "barcode", color: .blue)
                    if let locked = specs.bootloaderLocked {
                        Badge(text: locked ? "Загрузчик заблокирован" : "Загрузчик разблокирован",
                              symbol: locked ? "lock.fill" : "lock.open.fill",
                              color: locked ? .orange : .green)
                    }
                }
                Text(summaryLine)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
                Button {
                    model.selection = .roms
                } label: {
                    Label("Подобрать прошивку", systemImage: "square.stack.3d.up.fill")
                        .padding(.horizontal, 6)
                }
                .prominentButton()
                .controlSize(.large)
                .padding(.top, 8)
            }
            Spacer(minLength: 0)
        }
        .padding(28)
        .glassCard(cornerRadius: 28)
    }

    private var summaryLine: String {
        var parts: [String] = []
        if let chipset = specs.chipset { parts.append(chipset) }
        if let ram = specs.ramBytes { parts.append("\(roundedGB(ram)) ГБ ОЗУ") }
        if let storage = specs.storageTotalBytes { parts.append("\(roundedGB(storage)) ГБ памяти") }
        return parts.joined(separator: " · ")
    }

    private func roundedGB(_ bytes: Int64) -> Int {
        // Маркетинговые объёмы: 7.6 ГБ → 8, 110 ГБ → 128.
        let gb = Double(bytes) / 1_073_741_824
        let steps = [1.0, 2, 3, 4, 6, 8, 12, 16, 24, 32, 64, 128, 256, 512, 1024]
        return Int(steps.first { $0 >= gb * 0.85 } ?? gb.rounded())
    }

    private var systemRows: [SpecRow] {
        [
            SpecRow("Версия Android", specs.androidVersion + (specs.sdkLevel.map { " (API \($0))" } ?? "")),
            SpecRow("Патч безопасности", specs.securityPatch),
            SpecRow("Сборка", specs.buildID),
            SpecRow("Текущая прошивка", specs.romName),
            SpecRow("Ядро", specs.kernel),
            SpecRow("Отпечаток", specs.fingerprint, monospaced: true),
        ]
    }

    private var hardwareRows: [SpecRow] {
        [
            SpecRow("Производитель", specs.manufacturer.capitalized),
            SpecRow("Модель", specs.model),
            SpecRow("Кодовое имя", specs.codename, monospaced: true),
            SpecRow("Чипсет", specs.chipset),
            SpecRow("Ядер CPU", specs.cpuCores.map(String.init)),
            SpecRow("Архитектура", specs.cpuABI),
            SpecRow("ОЗУ", specs.ramBytes?.formattedBytes),
            SpecRow("Хранилище", storageText),
        ]
    }

    private var displayRows: [SpecRow] {
        [
            SpecRow("Разрешение", specs.screenResolution),
            SpecRow("Плотность", specs.screenDensity.map { "\($0) dpi" }),
            SpecRow("Заряд", specs.batteryLevel.map { "\($0)%" }),
            SpecRow("Состояние батареи", specs.batteryHealth),
            SpecRow("Температура", specs.batteryTemperature.map { String(format: "%.1f °C", $0) }),
            SpecRow("Напряжение", specs.batteryVoltage.map { String(format: "%.2f В", $0) }),
        ]
    }

    private var bootRows: [SpecRow] {
        [
            SpecRow("Загрузчик", specs.bootloaderLocked.map { $0 ? "Заблокирован" : "Разблокирован" }),
            SpecRow("Заводская разблокировка", specs.oemUnlockAllowed.map { $0 ? "Разрешена" : "Выключена" }),
            SpecRow("Verified Boot", specs.verifiedBootState?.capitalized),
            SpecRow("A/B-разделы", specs.isSeamlessUpdate ? "Да (слот \(specs.slotSuffix ?? ""))" : "Нет"),
            SpecRow("Project Treble", specs.trebleEnabled.map { $0 ? "Поддерживается" : "Нет" }),
            SpecRow("Динамические разделы", specs.dynamicPartitions.map { $0 ? "Да" : "Нет" }),
            SpecRow("VNDK", specs.vndkVersion),
            SpecRow("Серийный номер", specs.serial, monospaced: true),
        ]
    }

    private var storageText: String? {
        guard let total = specs.storageTotalBytes else { return nil }
        if let free = specs.storageFreeBytes { return "\(free.formattedBytes) свободно из \(total.formattedBytes)" }
        return total.formattedBytes
    }
}

struct SpecRow: Identifiable {
    let id = UUID()
    let title: String
    let value: String?
    var monospaced = false

    init(_ title: String, _ value: String?, monospaced: Bool = false) {
        self.title = title
        self.value = value
        self.monospaced = monospaced
    }
}

private struct SpecSection: View {
    var title: String
    var symbol: String
    var rows: [SpecRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: title, symbol: symbol)
            VStack(spacing: 0) {
                let visible = rows.filter { !($0.value ?? "").isEmpty }
                ForEach(visible) { row in
                    HStack(alignment: .firstTextBaseline) {
                        Text(row.title).foregroundStyle(.secondary)
                        Spacer(minLength: 16)
                        Text(row.value ?? "—")
                            .font(row.monospaced ? .callout.monospaced() : .callout)
                            .multilineTextAlignment(.trailing)
                            .textSelection(.enabled)
                            .lineLimit(3)
                    }
                    .padding(.vertical, 7)
                    if row.id != visible.last?.id { Divider() }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }
}

/// Стилизованный силуэт телефона с индикатором заряда.
private struct PhoneIllustration: View {
    var batteryLevel: Int?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(LinearGradient(colors: [Color(white: 0.2), Color(white: 0.05)], startPoint: .top, endPoint: .bottom))
                .frame(width: 112, height: 220)
            RoundedRectangle(cornerRadius: 21, style: .continuous)
                .fill(LinearGradient(colors: [.indigo, .teal, .mint], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 100, height: 208)
                .overlay(alignment: .top) {
                    Circle().fill(.black).frame(width: 9, height: 9).padding(.top, 9)
                }
                .overlay {
                    VStack(spacing: 4) {
                        Text(Date.now, format: .dateTime.hour().minute())
                            .font(.system(size: 22, weight: .semibold, design: .rounded))
                        if let batteryLevel {
                            Label("\(batteryLevel)%", systemImage: "battery.100percent")
                                .font(.caption2.weight(.semibold))
                        }
                    }
                    .foregroundStyle(.white)
                }
        }
        .shadow(color: .indigo.opacity(0.35), radius: 20, y: 10)
    }
}
