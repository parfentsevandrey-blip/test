import AppKit
import SwiftUI

/// «Мои машины»: загрузки в работе и созданные машины.
struct MachinesView: View {
    @Environment(AppModel.self) private var model
    @State private var editing: Machine?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ScreenHeader(title: "Мои машины", subtitle: subtitle) {
                    Button { model.screen = .hot } label: { Label("Новая машина", systemImage: "plus") }
                        .buttonStyle(.glassProminent)
                }
                if !model.virtualizationSupported {
                    Label("Этот Mac не поддерживает Virtualization.framework: нужен Apple Silicon (а внутри другой виртуальной машины — M3 или новее).",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 12)).foregroundStyle(.orange)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .glassEffect(.regular.tint(.orange.opacity(0.12)), in: .rect(cornerRadius: 14))
                }
                let active = model.jobs.filter { $0.state != .done }
                if !active.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Загружается").font(.system(size: 17, weight: .bold, design: .rounded))
                        ForEach(active) { JobCard(job: $0) }
                    }
                }
                if model.machines.isEmpty && active.isEmpty {
                    VStack(spacing: 14) {
                        Image(systemName: "macwindow.on.rectangle").font(.system(size: 54)).foregroundStyle(.secondary)
                        Text("Здесь появятся ваши машины").font(.system(size: 20, weight: .semibold))
                        Text("Выберите систему в каталоге — Vessel скачает её, проверит и поставит сам.")
                            .font(.system(size: 13)).foregroundStyle(.secondary)
                        Button("Открыть «Горячие»") { model.screen = .hot }.buttonStyle(.glassProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 60)
                    .glassEffect(.regular, in: .rect(cornerRadius: 24))
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 320, maximum: 420), spacing: 18)], spacing: 18) {
                    ForEach(model.machines) { m in MachineCard(machine: m, edit: { editing = m }) }
                }
            }
            .padding(28)
            .frame(maxWidth: 1240)
            .frame(maxWidth: .infinity)
        }
        .sheet(item: $editing) { MachineSettingsSheet(machine: $0) }
    }

    private var subtitle: String {
        let n = model.machines.count
        let disk = model.machines.reduce(Int64(0)) { $0 + $1.diskUsed }
        return n == 0 ? "Ни одной машины — самое время попробовать что-то новое."
            : "\(Format.plural(n, "машина", "машины", "машин")), на диске \(Format.bytes(disk)). Машины работают в отдельных окнах."
    }
}

struct JobCard: View {
    @Environment(AppModel.self) private var model
    let job: DownloadJob

    var body: some View {
        let d = job.plan.distro
        HStack(spacing: 14) {
            ZStack {
                Circle().stroke(d.color.opacity(0.2), lineWidth: 6)
                Circle().trim(from: 0, to: job.fraction).stroke(d.color.gradient, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.smooth, value: job.fraction)
                Text(d.monogram).font(.system(size: 15, weight: .heavy, design: .rounded))
            }
            .frame(width: 54, height: 54)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(job.plan.name).font(.system(size: 14, weight: .semibold))
                    Text(job.plan.edition.title).font(.system(size: 11)).foregroundStyle(.secondary)
                    if let v = job.image?.version, !v.isEmpty { Pill(text: v, tint: d.color) }
                }
                ProgressView(value: job.fraction).tint(d.color)
                HStack(spacing: 10) {
                    if case .failed = job.state {
                        Label(job.stateTitle, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
                    } else {
                        Text(job.stateTitle)
                    }
                    if job.state == .downloading {
                        Text("\(Format.bytes(job.received)) из \(job.total > 0 ? Format.bytes(job.total) : "…")")
                        if job.speed > 0 { Text(Format.speed(job.speed)) }
                        if job.eta.isFinite { Text("осталось \(Format.duration(job.eta))") }
                    }
                }
                .font(.system(size: 11).monospacedDigit())
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
            Spacer()
            switch job.state {
            case .downloading, .resolving:
                Button { model.pause(job) } label: { Image(systemName: "pause.fill") }.buttonStyle(.glass).buttonBorderShape(.circle)
            case .paused, .failed:
                Button { model.run(job) } label: { Image(systemName: "arrow.clockwise") }.buttonStyle(.glass).buttonBorderShape(.circle)
                    .help("Продолжить — скачанное не пропадёт")
            default:
                EmptyView()
            }
            Button { model.remove(job) } label: { Image(systemName: "xmark") }.buttonStyle(.glass).buttonBorderShape(.circle)
        }
        .padding(14)
        .glassEffect(.regular.tint(d.color.opacity(0.08)), in: .rect(cornerRadius: 20))
    }
}

struct MachineCard: View {
    @Environment(AppModel.self) private var model
    let machine: Machine
    let edit: () -> Void

    var body: some View {
        let distro = machine.distro ?? Catalog.all[0]
        let status = model.sessions[machine.id]?.status ?? .stopped
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                ScreenshotImage(title: distro.screenshots.first, distro: distro)
                    .frame(height: 160)
                    .saturation(status.isActive ? 1 : 0.6)
                HStack(spacing: 6) {
                    Circle().fill(color(status)).frame(width: 8, height: 8)
                    Text(machine.phase == .installing && !status.isActive ? "Идёт установка" : status.title)
                        .font(.system(size: 11, weight: .semibold))
                }
                .padding(.horizontal, 10).padding(.vertical, 5)
                .glassEffect(.regular, in: Capsule())
                .padding(10)
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(machine.name).font(.system(size: 16, weight: .semibold))
                    Spacer()
                    Menu {
                        Button("Настройки…", action: edit).disabled(status.isActive)
                        Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([machine.bundle]) }
                        if let folder = machine.sharedFolder {
                            Button("Открыть общую папку") { NSWorkspace.shared.open(URL(fileURLWithPath: folder)) }
                        }
                        if machine.phase == .installing {
                            Button("Извлечь установочный диск") { model.session(machine).ejectInstaller() }
                        }
                        Divider()
                        Button("Удалить машину…", role: .destructive) { confirmDelete() }
                    } label: { Image(systemName: "ellipsis.circle").font(.system(size: 16)) }
                        .menuStyle(.borderlessButton)
                        .menuIndicator(.hidden)
                        .frame(width: 28)
                }
                Text([machine.edition?.title ?? distro.name, machine.version.map { "версия \($0)" }].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                HStack(spacing: 12) {
                    Label(Format.plural(machine.cpu, "ядро", "ядра", "ядер"), systemImage: "cpu")
                    Label("\(machine.memoryGB) ГБ", systemImage: "memorychip")
                    Label("\(Format.bytes(machine.diskUsed)) из \(machine.diskGB) ГБ", systemImage: "internaldrive")
                }
                .font(.system(size: 11)).foregroundStyle(.secondary)
                HStack {
                    Button {
                        model.open(machine, start: !status.isActive)
                    } label: {
                        Label(status.isActive ? "Открыть" : (machine.hasSavedState ? "Продолжить" : "Запустить"),
                              systemImage: status.isActive ? "macwindow" : "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(distro.color)
                    .disabled(!model.virtualizationSupported)
                }
            }
            .padding(14)
        }
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private func color(_ status: VMSession.Status) -> Color {
        switch status {
        case .running: .green
        case .paused, .saving, .restoring: .yellow
        case .starting, .stopping: .blue
        case .failed: .red
        case .stopped: machine.phase == .installing ? .orange : .gray
        }
    }

    private func confirmDelete() {
        let alert = NSAlert()
        alert.messageText = "Удалить «\(machine.name)»?"
        alert.informativeText = "Машина и её диск (\(Format.bytes(machine.diskUsed))) уйдут в Корзину. Общая папка на Mac останется."
        alert.addButton(withTitle: "Удалить")
        alert.addButton(withTitle: "Отмена")
        alert.buttons.first?.hasDestructiveAction = true
        if alert.runModal() == .alertFirstButtonReturn { model.delete(machine) }
    }
}

/// Настройки выключенной машины.
struct MachineSettingsSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State var machine: Machine
    @State private var original: Machine?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Настройки «\(original?.name ?? machine.name)»").font(.system(size: 20, weight: .bold, design: .rounded))
            Form {
                TextField("Имя", text: $machine.name)
                Stepper("Ядра: \(machine.cpu)", value: $machine.cpu, in: 1...MacInfo.maxCPU)
                Stepper("Память: \(machine.memoryGB) ГБ", value: $machine.memoryGB, in: 1...MacInfo.maxMemoryGB)
                Stepper("Диск: \(machine.diskGB) ГБ (только увеличить)", value: $machine.diskGB, in: (original?.diskGB ?? 8)...1024, step: 8)
                Picker("Разрешение экрана", selection: Binding(get: { "\(machine.displayWidth)x\(machine.displayHeight)" }, set: { v in
                    let p = v.split(separator: "x").compactMap { Int($0) }
                    if p.count == 2 { machine.displayWidth = p[0]; machine.displayHeight = p[1] }
                })) {
                    ForEach(["1280x800", "1440x900", "1920x1200", "2560x1600"], id: \.self) { Text($0.replacingOccurrences(of: "x", with: " × ")).tag($0) }
                }
                Toggle("Общий буфер обмена", isOn: $machine.clipboard)
                Toggle("Звук", isOn: $machine.sound)
                Toggle("Микрофон", isOn: $machine.microphone)
                Toggle("Rosetta для программ Intel", isOn: $machine.rosetta)
                Toggle("Установочный диск подключён", isOn: Binding(get: { machine.phase == .installing }, set: { machine.phase = $0 ? .installing : .installed }))
                    .disabled(machine.isoName == nil)
            }
            .formStyle(.grouped)
            HStack {
                Spacer()
                Button("Отмена") { dismiss() }.keyboardShortcut(.cancelAction)
                Button("Сохранить") { model.save(machine); dismiss() }.buttonStyle(.glassProminent).keyboardShortcut(.defaultAction)
            }
        }
        .padding(22)
        .frame(width: 520, height: 600)
        .onAppear { original = machine }
    }
}

/// Скачанные образы: место на диске и удаление.
struct DownloadsView: View {
    @Environment(AppModel.self) private var model
    @State private var refresh = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ScreenHeader(title: "Загрузки", subtitle: "Образы хранятся, пока нужны: из одного образа можно сделать сколько угодно машин.")
                ForEach(model.jobs) { JobCard(job: $0) }
                let images = model.storedImages()
                if images.isEmpty && model.jobs.isEmpty {
                    EmptyHint(title: "Образов нет", symbol: "opticaldisc", text: "Они появятся, когда вы установите первую систему.")
                }
                ForEach(images) { image in
                    HStack(spacing: 12) {
                        Image(systemName: "opticaldisc.fill").font(.system(size: 24)).foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(image.id).font(.system(size: 13, weight: .medium, design: .monospaced)).lineLimit(1)
                            Text(image.inUse ? "Подключён к машине" : "Не используется").font(.system(size: 11)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Format.bytes(image.size)).font(.system(size: 12).monospacedDigit()).foregroundStyle(.secondary)
                        Button { NSWorkspace.shared.activateFileViewerSelecting([image.url]) } label: { Image(systemName: "folder") }.buttonStyle(.glass)
                        Button(role: .destructive) { model.deleteImage(image); refresh += 1 } label: { Image(systemName: "trash") }
                            .buttonStyle(.glass).disabled(image.inUse)
                    }
                    .padding(12)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
                }
            }
            .id(refresh)
            .padding(28)
            .frame(maxWidth: 980)
            .frame(maxWidth: .infinity)
        }
    }
}
