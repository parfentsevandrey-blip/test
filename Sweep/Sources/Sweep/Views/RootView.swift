import Combine
import AppKit
import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            sidebar
                .navigationSplitViewColumnWidth(min: 210, ideal: 230, max: 260)
        } detail: {
            ZStack {
                ModuleBackground(module: model.module, energy: energy)
                detail
                    .id(model.module)
                    .transition(.opacity.combined(with: .scale(scale: 0.985)))
            }
            .animation(.smooth(duration: 0.3), value: model.module)
            .overlay(alignment: .top) { toast }
        }
        .sheet(item: $model.pendingRemoval) { pending in
            ConfirmRemovalSheet(pending: pending)
        }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            model.refreshPermissions()
        }
    }

    private var energy: Double {
        if model.isBusy(model.smartPhase) || model.isBusy(model.cleanupPhase) || model.isBusy(model.spacePhase) { return 1.2 }
        return 0.2
    }

    @ViewBuilder
    private var detail: some View {
        switch model.module {
        case .smart: SmartScanView()
        case .cleanup: CleanupView()
        case .privacy: PrivacyView()
        case .uninstaller: UninstallerView()
        case .clutter: ClutterView()
        case .space: SpaceLensView()
        case .performance: PerformanceView()
        case .journal: JournalView()
        }
    }

    private var sidebar: some View {
        List(selection: Binding<Module?>(get: { model.module }, set: { if let m = $0 { model.module = m } })) {
            Section {
                ForEach(Module.allCases) { module in
                    Label {
                        Text(module.title)
                    } icon: {
                        Image(systemName: module.symbol)
                            .foregroundStyle(.white)
                            .font(.system(size: 11, weight: .bold))
                            .frame(width: 22, height: 22)
                            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(LinearGradient(colors: [module.palette[0], module.palette[1]], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    }
                    .badge(badge(for: module))
                    .tag(module)
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { sidebarFooter }
    }

    private func badge(for module: Module) -> Text? {
        switch module {
        case .smart:
            if case .results = model.smartPhase, model.smartSelectedSize > 0 { return Text(Format.bytes(model.smartSelectedSize)) }
        case .cleanup:
            if model.cleanupSelectedSize > 0 { return Text(Format.bytes(model.cleanupSelectedSize)) }
        case .uninstaller:
            if !model.apps.isEmpty { return Text("\(model.apps.count)") }
        case .clutter:
            let wasted = model.clutter.duplicates.reduce(0) { $0 + $1.wasted }
            if wasted > 0 { return Text(Format.bytes(wasted)) }
        default: break
        }
        return nil
    }

    private var sidebarFooter: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !model.fullDiskAccess {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Нет полного доступа", systemImage: "lock.shield.fill").font(.system(size: 12, weight: .semibold)).foregroundStyle(.orange)
                    Text("Без него не видны Корзина, Почта и кэши программ из App Store.").font(.system(size: 10.5)).foregroundStyle(.secondary)
                    Button("Дать доступ") { Permissions.openFullDiskAccessSettings() }
                        .buttonStyle(.glass).controlSize(.small)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.orange.opacity(0.1)))
            }
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("Macintosh HD").font(.system(size: 11, weight: .semibold))
                    Spacer()
                    Text("\(Format.bytes(model.disk.availableImportant)) свободно").font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
                }
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.primary.opacity(0.1))
                        Capsule().fill(LinearGradient(colors: [Color(red: 0.55, green: 0.4, blue: 1), Color(red: 0.3, green: 0.65, blue: 1)], startPoint: .leading, endPoint: .trailing))
                            .frame(width: g.size.width * model.disk.usedFraction)
                    }
                }
                .frame(height: 6)
            }
        }
        .padding(14)
    }

    @ViewBuilder
    private var toast: some View {
        if let text = model.toast {
            Text(text)
                .font(.system(size: 12.5, weight: .medium))
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .glassEffect(.regular, in: .capsule)
                .padding(.top, 14)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// Подтверждение удаления: что именно и каким способом уйдёт.
struct ConfirmRemovalSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let pending: PendingRemoval

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                Image(systemName: "trash.circle.fill")
                    .font(.system(size: 40))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, Module.cleanup.tint)
                VStack(alignment: .leading, spacing: 3) {
                    Text(pending.title).font(.system(size: 18, weight: .bold))
                    Text("\(pending.items.count) объектов · \(Format.bytes(pending.size))").foregroundStyle(.secondary).monospacedDigit()
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                let deleteCount = pending.items.count - pending.trashCount - pending.adminCount
                if deleteCount > 0 {
                    Label("\(deleteCount) — удалятся сразу: это кэши и журналы, программы восстановят их сами", systemImage: "xmark.bin")
                }
                if pending.trashCount > 0 {
                    Label("\(pending.trashCount) — уйдут в Корзину: вернуть можно из «Журнала»", systemImage: "trash")
                }
                if pending.adminCount > 0 {
                    Label("\(pending.adminCount) — системные файлы: macOS попросит пароль администратора", systemImage: "lock.fill")
                }
            }
            .font(.system(size: 12.5))
            .foregroundStyle(.secondary)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    ForEach(pending.items.prefix(300)) { item in
                        HStack {
                            FileIcon(url: item.url, bundleID: item.bundleID, size: 18)
                            Text(item.title).font(.system(size: 12)).lineLimit(1)
                            Text(Format.path(item.url, home: model.home)).font(.system(size: 10.5)).foregroundStyle(.tertiary).lineLimit(1).truncationMode(.middle)
                            Spacer()
                            Text(Format.bytes(item.size)).font(.system(size: 11)).monospacedDigit().foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(10)
            }
            .frame(height: 220)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.primary.opacity(0.04)))

            HStack {
                Spacer()
                Button("Отмена") { model.pendingRemoval = nil; dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .buttonStyle(.glass)
                Button("Удалить") { model.confirmPending(); dismiss() }
                    .keyboardShortcut(.defaultAction)
                    .buttonStyle(.glassProminent)
                    .tint(.red)
            }
            .controlSize(.large)
        }
        .padding(24)
        .frame(width: 620)
    }
}

/// Мини-панель в строке меню.
struct MenuBarView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 18) {
                gauge(model.memory.usedFraction, "Память", "\(Int(model.memory.usedFraction * 100))%",
                      model.memory.pressure > 1 ? [.orange, .red] : [Color(red: 0.2, green: 0.8, blue: 0.55), Color(red: 0.2, green: 0.7, blue: 0.95)])
                gauge(model.disk.usedFraction, "Диск", Format.bytes(model.disk.availableImportant), [Color(red: 0.6, green: 0.4, blue: 1), Color(red: 0.3, green: 0.6, blue: 1)])
            }
            if model.trashSize > 0 {
                HStack {
                    Label("Корзина", systemImage: "trash")
                    Spacer()
                    Text(Format.bytes(model.trashSize)).monospacedDigit().foregroundStyle(.secondary)
                }
                .font(.system(size: 12))
            }
            Divider()
            GlassEffectContainer(spacing: 6) {
                VStack(spacing: 6) {
                    Button { open(.smart) } label: { Label("Умная проверка", systemImage: "sparkles").frame(maxWidth: .infinity) }
                        .buttonStyle(.glassProminent).tint(Module.smart.tint)
                    HStack(spacing: 6) {
                        Button { model.emptyTrashWithFinder() } label: { Label("Очистить Корзину", systemImage: "trash").frame(maxWidth: .infinity) }
                        Button { model.maintenanceSelection = [.freeMemory]; model.runMaintenance() } label: {
                            Label("Память", systemImage: "memorychip").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.glass)
                }
            }
            HStack {
                Button("Открыть Sweep") { open(model.module) }
                Spacer()
                Button("Выйти") { NSApp.terminate(nil) }
            }
            .buttonStyle(.borderless)
            .font(.system(size: 12))
        }
        .padding(16)
        .frame(width: 300)
    }

    private func gauge(_ fraction: Double, _ title: String, _ value: String, _ colors: [Color]) -> some View {
        HStack(spacing: 10) {
            RingGauge(fraction: fraction, colors: colors, lineWidth: 7).frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 11)).foregroundStyle(.secondary)
                Text(value).font(.system(size: 13, weight: .semibold)).monospacedDigit()
            }
        }
    }

    private func open(_ module: Module) {
        model.module = module
        openWindow(id: "main")
        NSApp.activate()
    }
}
