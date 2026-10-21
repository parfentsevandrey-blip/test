import AppKit
import SwiftUI

/// «Мои модели»: загрузки и установленные модели.
struct LibraryView: View {
    @Environment(AppModel.self) private var model
    @State private var confirmDelete: InstalledModel?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                SectionHeader(title: "Мои модели", subtitle: "Занято \(Format.bytes(Library.diskUsage(model.installed))) · свободно \(Format.bytes(Hardware.freeDisk()))") {
                    Button { NSWorkspace.shared.open(AppPaths.models) } label: { Label("Показать в Finder", systemImage: "folder") }
                        .buttonStyle(.glass)
                }
                .padding(.horizontal, -28)

                let jobs = model.downloads.filter { $0.state != .done }
                if !jobs.isEmpty {
                    Text("Загрузки").font(.system(size: 17, weight: .bold))
                    VStack(spacing: 10) { ForEach(jobs) { DownloadRow(job: $0) } }
                }

                Text("Установлено").font(.system(size: 17, weight: .bold))
                if model.installed.isEmpty {
                    ContentCard(padding: 24) {
                        HStack(spacing: 18) {
                            Image(systemName: "shippingbox").font(.system(size: 34)).foregroundStyle(.secondary)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Пока ни одной модели").font(.system(size: 15, weight: .semibold))
                                Text("Загляните в «Для этого Mac» — там уже подобраны модели под ваше железо.").font(.system(size: 12.5)).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("Подобрать") { model.section = .forYou }.buttonStyle(.glassProminent).tint(Section.forYou.tint)
                        }
                    }
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 12) {
                        ForEach(model.installed.sorted { ($0.lastUsed ?? $0.installedAt) > ($1.lastUsed ?? $1.installedAt) }) { m in
                            InstalledCard(item: m) { confirmDelete = m }
                        }
                    }
                }
            }
            .padding(.horizontal, 28)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.never)
        .alert("Удалить «\(confirmDelete?.name ?? "")»?", isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } })) {
            Button("Удалить", role: .destructive) { if let m = confirmDelete { model.uninstall(m) } }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Освободится \(Format.bytes(confirmDelete?.size ?? 0)). Переписка останется, модель можно будет скачать снова.")
        }
    }
}

struct DownloadRow: View {
    @Environment(AppModel.self) private var model
    let job: DownloadJob

    var body: some View {
        ContentCard(padding: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    ModelAvatar(lab: job.card.lab, size: 38)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(job.card.name).font(.system(size: 14, weight: .semibold))
                        Text(status).font(.system(size: 11.5)).foregroundStyle(statusColor).monospacedDigit()
                    }
                    Spacer()
                    switch job.state {
                    case .running, .queued:
                        Button { model.pause(job) } label: { Image(systemName: "pause.fill") }.buttonStyle(.glass).help("Пауза")
                    case .paused, .failed:
                        Button { model.resume(job) } label: { Image(systemName: job.state == .paused ? "play.fill" : "arrow.clockwise") }
                            .buttonStyle(.glass).help("Продолжить")
                    default:
                        EmptyView()
                    }
                    Button { model.cancel(job) } label: { Image(systemName: "xmark") }.buttonStyle(.glass).help("Отменить и удалить скачанное")
                }
                GlowBar(fraction: job.fraction)
            }
        }
    }

    private var status: String {
        switch job.state {
        case .queued: return "В очереди · \(Format.bytes(job.total))"
        case .running:
            let eta = job.eta.isFinite ? " · осталось \(Format.duration(job.eta))" : ""
            return "\(Format.bytes(job.received)) из \(Format.bytes(job.total)) · \(Format.speed(job.speed))\(eta)"
        case .paused: return "Пауза · \(Format.bytes(job.received)) из \(Format.bytes(job.total)) — продолжится с того же места"
        case .installing: return "Проверяю и устанавливаю…"
        case .done: return "Готово"
        case .failed(let why): return why
        }
    }

    private var statusColor: Color {
        if case .failed = job.state { return .red }
        return .secondary
    }
}

struct InstalledCard: View {
    @Environment(AppModel.self) private var model
    let item: InstalledModel
    let onDelete: () -> Void

    var body: some View {
        let fit = Advisor.estimate(size: item.size, params: item.params, active: item.activeParams, mac: model.mac)
        let active = model.loadedModelID == item.id
        ContentCard(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    ModelAvatar(lab: item.lab, size: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                        Text("\(item.lab) · \(item.quant) · \(Format.bytes(item.size))").font(.system(size: 11.5)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                    if active, case .ready = model.engineState {
                        Label("В памяти", systemImage: "memorychip.fill").font(.system(size: 10.5, weight: .semibold)).foregroundStyle(.green)
                    }
                }
                HStack(spacing: 8) {
                    FitBadge(fit: fit, compact: true)
                    Label(item.verified ? "Проверена SHA-256" : "Без проверки", systemImage: item.verified ? "checkmark.shield.fill" : "shield")
                        .font(.system(size: 10.5)).foregroundStyle(.secondary)
                    Spacer()
                    Text(item.lastUsed.map { "Работала \(Format.relative($0))" } ?? "Ещё не запускалась").font(.system(size: 10.5)).foregroundStyle(.tertiary)
                }
                HStack {
                    Button {
                        model.select(item)
                        model.section = .chat
                    } label: { Label("Общаться", systemImage: "bubble.left.and.bubble.right.fill") }
                        .buttonStyle(.glassProminent)
                        .tint(Section.chat.tint)
                    Spacer()
                    Button(role: .destructive, action: onDelete) { Image(systemName: "trash") }
                        .buttonStyle(.glass)
                        .help("Удалить модель")
                }
            }
        }
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(active ? Section.chat.tint.opacity(0.6) : .clear, lineWidth: 1.5))
    }
}
