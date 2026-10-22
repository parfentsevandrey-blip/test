import AppKit
import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Group {
            if model.onboardingDone {
                main
            } else {
                OnboardingView()
            }
        }
        .sheet(item: $model.detailCard) { card in
            ModelDetailView(card: card).environment(model)
        }
        .alert(item: $model.fitWarning) { warning in
            Alert(title: Text("«\(warning.model.name)» слишком велика для этого Mac"),
                  message: Text(warning.fit.reasons.joined(separator: " ") + " Можно попробовать, но Mac может зависнуть до окончания загрузки."),
                  primaryButton: .destructive(Text("Всё равно загрузить")) { model.select(warning.model, force: true) },
                  secondaryButton: .cancel(Text("Отмена")))
        }
    }

    private var main: some View {
        NavigationSplitView {
            sidebar
                .navigationSplitViewColumnWidth(min: 200, ideal: 220, max: 260)
        } detail: {
            ZStack {
                SectionBackground(section: model.section, energy: model.isGenerating ? 1.1 : 0.2)
                detail
                    .id(model.section)
                    .transition(.opacity.combined(with: .scale(scale: 0.99)))
            }
            .animation(.smooth(duration: 0.3), value: model.section)
            .overlay(alignment: .top) { toast }
        }
    }

    @ViewBuilder
    private var detail: some View {
        switch model.section {
        case .chat: ChatView()
        case .forYou: ForYouView()
        case .catalog: CatalogView()
        case .feed: FeedView()
        case .library: LibraryView()
        }
    }

    private var sidebar: some View {
        List(selection: Binding<Section?>(get: { model.section }, set: { if let s = $0 { model.section = s } })) {
            ForEach(Section.allCases) { section in
                Label {
                    Text(section.title)
                } icon: {
                    Image(systemName: section.symbol)
                        .foregroundStyle(.white)
                        .font(.system(size: 11, weight: .bold))
                        .frame(width: 22, height: 22)
                        .background(RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(LinearGradient(colors: [section.palette[0], section.palette[1]], startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
                .badge(badge(for: section))
                .tag(section)
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { sidebarFooter }
    }

    private func badge(for section: Section) -> Text? {
        switch section {
        case .feed:
            return model.unreadFeed > 0 ? Text("\(model.unreadFeed)") : nil
        case .library:
            if let job = model.downloads.first(where: { $0.state == .running }) { return Text("\(Int(job.fraction * 100))%") }
            return model.installed.isEmpty ? nil : Text("\(model.installed.count)")
        default:
            return nil
        }
    }

    private var sidebarFooter: some View {
        VStack(alignment: .leading, spacing: 8) {
            if case .running(let port) = model.apiState {
                Label("API: localhost:\(String(port))", systemImage: "network").font(.system(size: 10.5)).foregroundStyle(.secondary)
            }
            HStack(spacing: 8) {
                Image(systemName: "cpu").foregroundStyle(Section.forYou.palette[1])
                VStack(alignment: .leading, spacing: 1) {
                    Text(model.mac.chipShort).font(.system(size: 11.5, weight: .semibold))
                    Text("\(Int(model.mac.memoryGB.rounded())) ГБ · \(Int(model.mac.bandwidth)) ГБ/с").font(.system(size: 10.5)).foregroundStyle(.secondary)
                }
            }
            if let m = model.selectedModel {
                HStack(spacing: 8) {
                    Circle().fill(engineColor).frame(width: 7, height: 7)
                    Text(m.name).font(.system(size: 11)).lineLimit(1)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var engineColor: Color {
        switch model.engineState {
        case .ready: .green
        case .loading: .orange
        case .failed: .red
        case .idle: .gray
        }
    }

    @ViewBuilder
    private var toast: some View {
        if let text = model.toast {
            Text(text)
                .font(.system(size: 12.5, weight: .medium))
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .glassEffect(.regular, in: .capsule)
                .padding(.top, 12)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// Настройки приложения (⌘,).
struct SettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        TabView {
            ScrollView { GenerationSettingsView().padding(24) }
                .tabItem { Label("Ответы", systemImage: "text.bubble") }
            ScrollView { EngineSettingsView().padding(24) }
                .tabItem { Label("Память", systemImage: "memorychip") }
            ChatBehaviourSettingsView()
                .padding(24)
                .frame(maxHeight: .infinity, alignment: .top)
                .tabItem { Label("Чат", systemImage: "bubble.left.and.text.bubble.right") }
            server
                .padding(24)
                .tabItem { Label("Сервер", systemImage: "network") }
            storage
                .padding(24)
                .tabItem { Label("Хранилище", systemImage: "internaldrive") }
            engine
                .padding(24)
                .tabItem { Label("Движок", systemImage: "gearshape.2") }
        }
        .frame(width: 640, height: 600)
    }

    private var server: some View {
        @Bindable var model = model
        return VStack(alignment: .leading, spacing: 14) {
            Toggle("Локальный сервер для других программ", isOn: $model.apiEnabled)
                .font(.system(size: 13, weight: .semibold))
            Text("Совместим с Ollama и OpenAI: редакторы кода, плагины и скрипты смогут пользоваться моделями Synapse. Доступен только с этого Mac.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary)
            HStack {
                Text("Порт")
                TextField("11434", value: $model.apiPort, format: .number.grouping(.never)).frame(width: 80)
                Button("Применить") { model.restartAPI() }.disabled(!model.apiEnabled)
            }
            switch model.apiState {
            case .running(let port):
                Label("Работает · запросов: \(model.apiRequests)", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                Text("curl http://localhost:\(String(port))/v1/chat/completions -d '{\"model\":\"\(model.selectedModel?.apiName ?? "model")\",\"messages\":[{\"role\":\"user\",\"content\":\"Привет\"}]}'")
                    .font(.system(size: 10.5, design: .monospaced)).textSelection(.enabled)
                    .padding(8).background(RoundedRectangle(cornerRadius: 8).fill(.primary.opacity(0.05)))
            case .failed(let why):
                Label(why, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
            case .stopped:
                Label("Выключен", systemImage: "pause.circle").foregroundStyle(.secondary)
            }
            Spacer()
        }
        .font(.system(size: 12.5))
    }

    private var storage: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Модели хранятся отдельно от программы").font(.system(size: 13, weight: .semibold))
            Label("Обновление Synapse их не трогает: ставьте новую версию поверх старой — все скачанные модели, чаты и настройки останутся на месте. Даже если список моделей повредится, Synapse найдёт файлы в этой папке и восстановит их сам.",
                  systemImage: "checkmark.shield.fill")
                .font(.system(size: 11.5)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            Text(AppPaths.models.path).font(.system(size: 11, design: .monospaced)).textSelection(.enabled).foregroundStyle(.secondary)
            HStack {
                Button("Показать в Finder") { NSWorkspace.shared.open(AppPaths.models) }
                Text("Занято \(Format.bytes(Library.diskUsage(model.installed)))").foregroundStyle(.secondary)
            }
            Divider()
            Toggle("Уведомлять о новых моделях для этого Mac", isOn: Binding(get: { model.notifyNewModels }, set: { model.notifyNewModels = $0 }))
            Spacer()
        }
        .font(.system(size: 12.5))
    }

    private var engine: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("llama.cpp \(EngineCatalog.version)").font(.system(size: 15, weight: .bold))
            Text("Открытый движок ggml-org/llama.cpp (MIT), собран из исходников вместе с приложением: Metal для видеоядра, Accelerate для процессора.")
                .font(.system(size: 11.5)).foregroundStyle(.secondary)
            Text("Поддерживаемых архитектур моделей: \(EngineCatalog.architectures.count)").font(.system(size: 12))
            if let info = model.modelInfo {
                Divider()
                Text("Сейчас в памяти: \(info.name)").font(.system(size: 12, weight: .semibold))
                Text("\(info.description) · \(info.architecture) · \(info.layers) слоёв · контекст \(info.context) из \(info.trainContext) · формат «\(info.format.title)» · \(info.gpu ? "Metal" : "CPU")")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
            }
            ScrollView {
                Text(LlamaEngine.systemInfo).font(.system(size: 10, design: .monospaced)).foregroundStyle(.tertiary).textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .font(.system(size: 12.5))
    }
}
