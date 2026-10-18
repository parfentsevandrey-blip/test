import SwiftUI

@main
struct ROMForgeApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup("ROM Forge") {
            RootView()
                .environment(model)
                .frame(minWidth: 980, minHeight: 640)
                .task { model.start() }
        }
        .windowToolbarStyle(.unified)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("Устройство") {
                Button("Обновить данные") { model.reloadSelected() }
                    .keyboardShortcut("r")
                    .disabled(model.selectedDevice == nil)
            }
        }

        Settings {
            SettingsView()
                .environment(model)
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        // При запуске через `swift run` приложение без бандла не становится активным само.
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationSplitView {
            SidebarView()
                .navigationSplitViewColumnWidth(min: 220, ideal: 240, max: 300)
        } detail: {
            detail
        }
    }

    @ViewBuilder
    private var detail: some View {
        switch model.tools.state {
        case .searching, .downloading:
            ToolsSetupView()
        case .failed(let message):
            ToolsSetupView(error: message)
        case .ready:
            switch model.selection ?? .overview {
            case .install:
                if let session = model.installSession {
                    InstallView(session: session)
                } else {
                    ContentUnavailableView("Нет активной установки", systemImage: "square.and.arrow.down",
                                           description: Text("Выберите прошивку в каталоге, чтобы начать."))
                }
            case .roms:
                if let specs = model.selectedSpecs {
                    ROMCatalogView(specs: specs)
                } else {
                    ConnectDeviceView()
                }
            case .overview:
                if let specs = model.selectedSpecs {
                    DeviceOverviewView(specs: specs)
                } else {
                    ConnectDeviceView()
                }
            }
        }
    }
}

struct SidebarView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        List(selection: $model.selection) {
            Section("Устройство") {
                Label("Обзор", systemImage: "iphone.gen3")
                    .tag(SidebarItem.overview)
                Label("Прошивки", systemImage: "square.stack.3d.up")
                    .badge(availableCount)
                    .tag(SidebarItem.roms)
                if let session = model.installSession {
                    Label {
                        Text("Установка")
                    } icon: {
                        Image(systemName: session.isActive ? "arrow.down.circle.dotted" : "checkmark.circle")
                            .symbolEffect(.pulse, isActive: session.isActive)
                    }
                    .tag(SidebarItem.install)
                }
            }

            Section("Подключённые") {
                if model.devices.isEmpty {
                    Label("Нет устройств", systemImage: "cable.connector.slash")
                        .foregroundStyle(.secondary)
                }
                ForEach(model.devices) { device in
                    Button {
                        model.selectedSerial = device.serial
                        if model.selection == .install { model.selection = .overview }
                    } label: {
                        DeviceRow(device: device, isSelected: device.serial == model.selectedDevice?.serial)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) {
            ToolsStatusFooter()
                .padding(12)
        }
    }

    private var availableCount: Int {
        ROMCatalog.all.filter { model.availability(for: $0).isAvailable }.count
    }
}

private struct DeviceRow: View {
    var device: ConnectedDevice
    var isSelected: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: device.mode.isFastboot ? "bolt.horizontal.circle" : "smartphone")
                .font(.title3)
                .foregroundStyle(isSelected ? Color.accentColor : .secondary)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 1) {
                Text(device.displayName).lineLimit(1)
                Text(device.mode.title).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
        }
        .contentShape(Rectangle())
    }

    private var color: Color {
        switch device.mode {
        case .android: .green
        case .unauthorized, .offline: .orange
        case .unknown: .gray
        default: .blue
        }
    }
}

private struct ToolsStatusFooter: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "hammer.circle.fill")
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 0) {
                Text("Platform Tools").font(.caption.weight(.medium))
                Text(model.tools.version.map { "Версия \($0)" } ?? statusText)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(10)
        .glassCard(cornerRadius: 12)
    }

    private var statusText: String {
        switch model.tools.state {
        case .searching: "Поиск…"
        case .downloading(let value): "Загрузка \(Int(value * 100))%"
        case .ready: "Готово"
        case .failed: "Ошибка"
        }
    }
}
