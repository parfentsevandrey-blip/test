import AppKit
import SwiftUI

/// «Скорость»: память, диск, тяжёлые процессы, автозапуск и обслуживание системы.
struct PerformanceView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ModuleHeader(title: "Скорость", subtitle: "macOS сама хорошо управляет памятью — поэтому здесь только то, что действительно помогает.") {
                    EmptyView()
                }
                .padding(.horizontal, -28)
                gauges
                section("Автозапуск", subtitle: "Службы, которые программы запускают при входе и держат в фоне. Выключение обратимо.") {
                    launchItems
                }
                section("Обслуживание", subtitle: "Безопасные команды macOS. Задачи с замком попросят пароль администратора один раз.") {
                    maintenance
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 28)
        }
        .scrollEdgeEffectStyle(.soft, for: .top)
        .onAppear { model.loadLaunchItems() }
    }

    private var gauges: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 280), spacing: 14, alignment: .top)], spacing: 14) {
            ContentCard {
                HStack(spacing: 18) {
                    ZStack {
                        RingGauge(fraction: model.memory.usedFraction, colors: pressureColors)
                        VStack(spacing: 0) {
                            Text("\(Int(model.memory.usedFraction * 100))%").font(.system(size: 22, weight: .bold, design: .rounded)).monospacedDigit()
                            Text("память").font(.system(size: 10.5)).foregroundStyle(.secondary)
                        }
                    }
                    .frame(width: 110, height: 110)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(pressureTitle).font(.system(size: 14, weight: .semibold)).foregroundStyle(pressureColors[0])
                        stat("Занято", Int64(model.memory.used))
                        stat("Сжато", Int64(model.memory.compressed))
                        stat("Кэш файлов", Int64(model.memory.cached))
                        stat("Всего", Int64(model.memory.total))
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            ContentCard {
                HStack(spacing: 18) {
                    ZStack {
                        RingGauge(fraction: model.disk.usedFraction, colors: [Color(red: 0.6, green: 0.4, blue: 1), Color(red: 0.3, green: 0.6, blue: 1)])
                        VStack(spacing: 0) {
                            Text(Format.bytes(model.disk.availableImportant)).font(.system(size: 15, weight: .bold, design: .rounded)).monospacedDigit()
                            Text("свободно").font(.system(size: 10.5)).foregroundStyle(.secondary)
                        }
                    }
                    .frame(width: 110, height: 110)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Загрузочный диск").font(.system(size: 14, weight: .semibold))
                        stat("Ёмкость", model.disk.total)
                        stat("Очищаемое", model.disk.purgeable)
                        stat("Корзина", model.trashSize)
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            ContentCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Больше всего памяти").font(.system(size: 13, weight: .semibold))
                    ForEach(model.processes.prefix(6)) { p in
                        HStack(spacing: 8) {
                            FileIcon(url: NSRunningApplication(processIdentifier: p.pid)?.bundleURL, symbol: "gearshape.fill", size: 18)
                            Text(p.name).font(.system(size: 11.5)).lineLimit(1)
                            Spacer()
                            Text(String(format: "%.0f%%", p.cpu)).font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
                            Text(Format.bytes(Int64(p.memory))).font(.system(size: 11, weight: .semibold)).monospacedDigit()
                            if NSRunningApplication(processIdentifier: p.pid)?.activationPolicy == .regular {
                                Button { model.quitProcess(p) } label: { Image(systemName: "xmark.circle.fill") }
                                    .buttonStyle(.borderless).foregroundStyle(.secondary).help("Завершить программу")
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var pressureColors: [Color] {
        switch model.memory.pressure {
        case 4: [.red, .orange]
        case 2: [.orange, .yellow]
        default: [Color(red: 0.2, green: 0.8, blue: 0.55), Color(red: 0.2, green: 0.7, blue: 0.95)]
        }
    }

    private var pressureTitle: String {
        switch model.memory.pressure {
        case 4: "Памяти не хватает"
        case 2: "Нагрузка на память"
        default: "Памяти достаточно"
        }
    }

    private func stat(_ title: String, _ value: Int64) -> some View {
        HStack {
            Text(title).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(Format.bytes(value)).monospacedDigit()
        }
        .font(.system(size: 11.5))
        .frame(maxWidth: 190)
    }

    private func section<C: View>(_ title: String, subtitle: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 17, weight: .bold))
            Text(subtitle).font(.system(size: 12)).foregroundStyle(.secondary)
            ContentCard { content() }
        }
    }

    private var launchItems: some View {
        VStack(spacing: 0) {
            if model.launchItems.isEmpty {
                Text("Сторонних служб автозапуска нет").foregroundStyle(.secondary).padding(20).frame(maxWidth: .infinity)
            }
            ForEach(model.launchItems) { item in
                HStack(spacing: 12) {
                    FileIcon(url: item.ownerURL, symbol: "gearshape.2.fill", size: 30)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(item.ownerName ?? item.label).font(.system(size: 12.5, weight: .semibold)).lineLimit(1)
                            if item.needsAdmin { Image(systemName: "lock.fill").font(.system(size: 9)).foregroundStyle(.secondary) }
                            if item.keepAlive { Badge(text: "всегда в фоне", color: .orange) }
                            if item.ownerMissing { Badge(text: "программа удалена", color: .red) }
                        }
                        Text(item.label).font(.system(size: 10.5, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                    Text(domainTitle(item.domain)).font(.system(size: 10.5)).foregroundStyle(.tertiary)
                    Toggle("", isOn: Binding(get: { item.enabled }, set: { model.setLaunchItem(item, enabled: $0) }))
                        .toggleStyle(.switch)
                        .labelsHidden()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .contextMenu { Button("Показать plist в Finder") { NSWorkspace.shared.activateFileViewerSelecting([item.url]) } }
                Divider().opacity(0.35).padding(.leading, 56)
            }
            HStack {
                Text("Объекты входа из «Системных настроек» macOS управляет сама.").font(.system(size: 11)).foregroundStyle(.secondary)
                Spacer()
                Button("Открыть объекты входа") { Permissions.openLoginItemsSettings() }.buttonStyle(.glass).controlSize(.small)
            }
            .padding(12)
        }
    }

    private func domainTitle(_ d: LaunchItem.Domain) -> String {
        switch d {
        case .user: "при входе"
        case .systemAgent: "для всех"
        case .systemDaemon: "служба"
        }
    }

    private var maintenance: some View {
        VStack(spacing: 0) {
            ForEach(MaintenanceTask.allCases) { task in
                HStack(spacing: 12) {
                    Toggle("", isOn: Binding(get: { model.maintenanceSelection.contains(task) },
                                             set: { if $0 { model.maintenanceSelection.insert(task) } else { model.maintenanceSelection.remove(task) } }))
                        .toggleStyle(.checkbox).labelsHidden()
                    Image(systemName: task.symbol).font(.system(size: 15, weight: .semibold)).foregroundStyle(Module.performance.tint).frame(width: 26)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(task.title).font(.system(size: 12.5, weight: .semibold))
                            if task.needsAdmin { Image(systemName: "lock.fill").font(.system(size: 9)).foregroundStyle(.secondary) }
                        }
                        Text(task.explanation).font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    if let ok = model.maintenanceResults[task] {
                        Image(systemName: ok ? "checkmark.circle.fill" : "xmark.circle.fill").foregroundStyle(ok ? .green : .red)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                Divider().opacity(0.35).padding(.leading, 56)
            }
            HStack {
                Spacer()
                Button {
                    model.runMaintenance()
                } label: {
                    Label(model.maintenanceRunning ? "Выполняю…" : "Выполнить выбранное", systemImage: "wrench.and.screwdriver.fill").padding(.horizontal, 8)
                }
                .buttonStyle(.glassProminent)
                .tint(Module.performance.tint)
                .controlSize(.large)
                .disabled(model.maintenanceRunning || model.maintenanceSelection.isEmpty)
            }
            .padding(12)
        }
        .animation(.bouncy, value: model.maintenanceResults.count)
    }
}

/// «Журнал»: всё удалённое с возможностью вернуть из Корзины, и список исключений.
struct JournalView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        VStack(spacing: 0) {
            ModuleHeader(title: "Журнал", subtitle: "Каждое удаление записано. То, что ушло в Корзину, можно вернуть на прежнее место.") {
                Toggle("Кэши тоже в Корзину", isOn: $model.deleteCachesToTrash)
                    .toggleStyle(.switch)
                    .help("По умолчанию кэши удаляются сразу — программы восстанавливают их сами")
            }
            HStack(alignment: .top, spacing: 16) {
                ContentCard {
                    if model.journal.isEmpty {
                        ContentUnavailableView("Пока ничего не удалялось", systemImage: "clock.arrow.circlepath").frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ForEach(model.journal.prefix(800)) { entry in
                                    JournalRow(entry: entry)
                                    Divider().opacity(0.3).padding(.leading, 48)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                ContentCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Никогда не трогать").font(.system(size: 14, weight: .semibold))
                        Text("Добавляется из контекстного меню любой находки.").font(.system(size: 11)).foregroundStyle(.secondary)
                        if model.ignored.isEmpty {
                            Text("Список пуст").font(.system(size: 12)).foregroundStyle(.tertiary).padding(.top, 8)
                        }
                        ScrollView {
                            VStack(alignment: .leading, spacing: 6) {
                                ForEach(model.ignored, id: \.self) { path in
                                    HStack {
                                        Text(Format.path(URL(fileURLWithPath: path), home: model.home)).font(.system(size: 11.5)).lineLimit(1).truncationMode(.middle)
                                        Spacer()
                                        Button { model.unignore(path) } label: { Image(systemName: "minus.circle.fill") }.buttonStyle(.borderless)
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
                .frame(width: 320)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
    }
}

struct JournalRow: View {
    @Environment(AppModel.self) private var model
    let entry: JournalEntry

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: entry.method == .trash ? "trash" : entry.method == .admin ? "lock.fill" : "xmark.bin")
                .foregroundStyle(.secondary).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(URL(fileURLWithPath: entry.original).lastPathComponent).font(.system(size: 12.5, weight: .medium)).lineLimit(1)
                Text("\(entry.category) · \(Format.path(URL(fileURLWithPath: entry.original).deletingLastPathComponent(), home: model.home))")
                    .font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
            }
            Spacer()
            Text(Format.relative(entry.date)).font(.system(size: 10.5)).foregroundStyle(.tertiary)
            if entry.size > 0 { Text(Format.bytes(entry.size)).font(.system(size: 11.5, weight: .semibold)).monospacedDigit() }
            if entry.restored {
                Label("Возвращено", systemImage: "checkmark").font(.system(size: 11)).foregroundStyle(.green)
            } else if entry.trashed != nil {
                Button("Вернуть") { model.restore(entry) }.buttonStyle(.glass).controlSize(.small)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
    }
}
