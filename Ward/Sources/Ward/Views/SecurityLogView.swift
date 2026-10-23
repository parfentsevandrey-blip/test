import AppKit
import SwiftUI
import UniformTypeIdentifiers

/// Карточка журнала на панели безопасности: включить, сколько хранить, открыть, выгрузить, очистить.
struct SecurityLogCard: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow
    @State private var summary: (days: Int, bytes: Int64) = (0, 0)
    @State private var confirmClear = false

    var body: some View {
        @Bindable var model = model
        VStack(alignment: .leading, spacing: 12) {
            Text("Каждое событие защиты — что остановлено, где и как — записывается в файлы на этом Mac. По журналу видно, какие сайты что пытались сделать, и какие защиты срабатывают чаще всего. События частных окон и одноразовых вкладок записываются без имён сайтов. Никуда не отправляется.")
                .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 16) {
                Toggle("Вести журнал", isOn: $model.prefs.securityLog)
                    .toggleStyle(.switch)
                Picker("Хранить", selection: $model.prefs.logRetentionDays) {
                    Text("7 дней").tag(7)
                    Text("30 дней").tag(30)
                    Text("90 дней").tag(90)
                    Text("Год").tag(365)
                }
                .frame(width: 180)
                Spacer()
                Text(summary.days == 0 ? "Журнал пуст" : "\(summary.days) \(Format.plural(summary.days, "день", "дня", "дней")) · \(ByteCountFormatter.string(fromByteCount: summary.bytes, countStyle: .file))")
                    .font(.callout).foregroundStyle(.secondary).monospacedDigit()
            }
            HStack {
                Button("Открыть журнал") { openWindow(id: "security-log") }
                    .buttonStyle(.borderedProminent)
                Button("Выгрузить…") { LogExport.run(days: model.prefs.logRetentionDays) }
                Button("Показать в Finder") { NSWorkspace.shared.activateFileViewerSelecting([SecurityLog.shared.directory]) }
                Spacer()
                Button("Очистить журнал…", role: .destructive) { confirmClear = true }
            }
        }
        .task(id: model.recent.first?.id) { await refresh() }
        .onChange(of: model.prefs.logRetentionDays) {
            SecurityLog.shared.prune(keepingDays: model.prefs.logRetentionDays)
            Task { await refresh() }
        }
        .confirmationDialog("Удалить весь журнал безопасности?", isPresented: $confirmClear) {
            Button("Удалить", role: .destructive) {
                SecurityLog.shared.clear()
                Task { await refresh() }
            }
        }
    }

    private func refresh() async {
        summary = await Task.detached(priority: .utility) { SecurityLog.shared.summary() }.value
    }
}

/// Выгрузка журнала: JSON Lines или CSV (для таблиц).
enum LogExport {
    static func run(days: Int) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "Ward — журнал безопасности \(SecurityLog.day(Date())).csv"
        panel.allowedContentTypes = [.commaSeparatedText, UTType(filenameExtension: "jsonl") ?? .json]
        panel.allowsOtherFileTypes = true
        panel.message = "CSV откроется в Numbers и Excel; JSONL — для своих скриптов анализа."
        guard panel.runModal() == .OK, let url = panel.url else { return }
        let csv = url.pathExtension.lowercased() != "jsonl"
        Task.detached(priority: .userInitiated) { try? SecurityLog.shared.export(to: url, csv: csv, days: days) }
    }
}

/// Окно журнала: поиск, фильтр по виду, таблица; щелчок по строке — отчёт по этому виду событий.
struct SecurityLogView: View {
    @Environment(AppModel.self) private var model
    @State private var entries: [LogEntry] = []
    @State private var query = ""
    @State private var kind: String = ""
    @State private var days = 7
    @State private var selection: LogEntry.ID?
    @State private var loading = false
    @State private var report: DashboardReport?

    private var filtered: [LogEntry] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return entries.filter { e in
            (kind.isEmpty || e.kind == kind)
                && (q.isEmpty || e.title.lowercased().contains(q) || e.detail.lowercased().contains(q))
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                TextField("Поиск: сайт, компания, событие", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .frame(maxWidth: 340)
                Picker("Вид", selection: $kind) {
                    Text("Все события").tag("")
                    Divider()
                    ForEach(SecurityEvent.Kind.allCases, id: \.rawValue) { k in
                        Label(k.title, systemImage: k.icon).tag(k.rawValue)
                    }
                }
                .frame(width: 220)
                Picker("За", selection: $days) {
                    Text("сегодня").tag(1)
                    Text("7 дней").tag(7)
                    Text("30 дней").tag(30)
                    Text("всё время").tag(3650)
                }
                .frame(width: 160)
                Spacer()
                Text("\(Format.count(filtered.count)) из \(Format.count(entries.count))").foregroundStyle(.secondary).monospacedDigit()
                Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                    .help("Обновить")
                Button("Выгрузить…") { LogExport.run(days: days) }
            }
            .padding(12)
            Divider()
            if !model.prefs.securityLog {
                Label("Журнал выключен: новые события не записываются. Включить можно на панели безопасности.", systemImage: "pause.circle")
                    .foregroundStyle(.orange).padding(10)
            }
            Table(filtered, selection: $selection) {
                TableColumn("Время") { e in
                    Text(e.date.formatted(date: .numeric, time: .standard)).monospacedDigit().foregroundStyle(.secondary)
                }
                .width(min: 140, ideal: 160, max: 190)
                TableColumn("Вид") { e in
                    let k = SecurityEvent.Kind(rawValue: e.kind)
                    Label(k?.title ?? e.kind, systemImage: k?.icon ?? "circle")
                        .foregroundStyle(k?.color ?? .secondary)
                }
                .width(min: 120, ideal: 150, max: 190)
                TableColumn("Событие", value: \.title)
                TableColumn("Подробности", value: \.detail)
            }
            .overlay {
                if loading && entries.isEmpty { ProgressView() }
                else if !loading && filtered.isEmpty {
                    ContentUnavailableView("Событий нет", systemImage: "list.bullet.rectangle", description: Text("Здесь появится всё, что Ward остановит."))
                }
            }
            .onChange(of: selection) { _, id in
                guard let id, let e = entries.first(where: { $0.id == id }), let k = SecurityEvent.Kind(rawValue: e.kind) else { return }
                withAnimation(.spring(duration: 0.4, bounce: 0.2)) { report = DashboardReport.for(k) }
            }
        }
        .overlay {
            if let report {
                ReportOverlay(report: report) { withAnimation(.spring(duration: 0.35)) { self.report = nil; selection = nil } }
                    .transition(.scale(scale: 0.94).combined(with: .opacity))
            }
        }
        .navigationTitle("Журнал безопасности")
        .task(id: days) { await load() }
        .task {
            // Живой журнал: новые события подтягиваются, пока окно открыто.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                await load()
            }
        }
    }

    private func load() async {
        loading = true
        let d = days
        entries = await Task.detached(priority: .userInitiated) { SecurityLog.shared.entries(days: d, limit: 50000) }.value
        loading = false
    }
}
