import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            sidebar
                .navigationSplitViewColumnWidth(min: 210, ideal: 228, max: 260)
        } detail: {
            ZStack {
                Backdrop(score: model.score)
                detail
                    .id(model.screen)
                    .transition(.opacity.combined(with: .scale(scale: 0.985)))
            }
            .animation(.smooth(duration: 0.3), value: model.screen)
            .overlay(alignment: .top) { toast }
            .overlay { if model.busy { ProgressView().controlSize(.large).padding(24).glassEffect(.regular, in: .rect(cornerRadius: 18)) } }
        }
        .sheet(isPresented: $model.showOnboarding) {
            OnboardingView()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            if !DemoUI.isOn && !model.busy { model.refresh() }
        }
    }

    @ViewBuilder
    private var detail: some View {
        switch model.screen {
        case .overview: OverviewView()
        case .apps: AppsView()
        case .settings:
            RuleListScreen(title: "Настройки Apple", subtitle: "Ключи, которые меняет и сама панель «Системные настройки». Где надёжного ключа нет, Block открывает нужную панель и подсказывает, что нажать.",
                           rules: model.rules.filter { $0.level == .settings })
        case .services:
            RuleListScreen(title: "Службы", subtitle: "Фоновые агенты выключенных сервисов. Службы под защитой системы (SIP) Block не ломает — показывает «защищена системой».",
                           rules: model.rules.filter { $0.level == .services })
        case .network: NetworkView()
        case .replacements: ReplacementsView()
        case .journal: JournalView()
        }
    }

    private var sidebar: some View {
        List(selection: Binding<Screen?>(get: { model.screen }, set: { if let s = $0 { model.screen = s } })) {
            Section {
                ForEach(Screen.allCases) { screen in
                    Label {
                        HStack {
                            Text(screen.title)
                            Spacer()
                            if let badge = badge(screen) {
                                Text(badge).font(.system(size: 11, weight: .semibold).monospacedDigit()).foregroundStyle(.secondary)
                            }
                        }
                    } icon: {
                        Image(systemName: screen.symbol)
                            .foregroundStyle(.white)
                            .font(.system(size: 11, weight: .bold))
                            .frame(width: 22, height: 22)
                            .background(RoundedRectangle(cornerRadius: 6).fill(screen.tint.gradient))
                    }
                    .tag(screen)
                }
            } header: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Block").font(.system(size: 22, weight: .bold, design: .rounded)).foregroundStyle(.primary)
                    Text("Mac без сервисов Apple").font(.system(size: 11)).foregroundStyle(.secondary)
                }
                .padding(.bottom, 8)
                .textCase(nil)
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) {
            Button { model.showOnboarding = true } label: {
                Label("Пройти вопросы заново", systemImage: "questionmark.bubble")
            }
            .buttonStyle(.glass)
            .padding(12)
        }
    }

    private func badge(_ screen: Screen) -> String? {
        let level: Level? = switch screen {
        case .apps: .launchGuard
        case .settings: .settings
        case .services: .services
        case .network: .network
        case .replacements: .defaults
        default: nil
        }
        guard let level else { return screen == .journal && !model.drift.isEmpty ? "!" : nil }
        let list = model.rules.filter { $0.level == level && model.status(of: $0).counts }
        guard !list.isEmpty else { return nil }
        return "\(list.filter { model.status(of: $0).isBlocked }.count)/\(list.count)"
    }

    @ViewBuilder
    private var toast: some View {
        if let text = model.toast {
            Text(text)
                .font(.system(size: 13, weight: .medium))
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .glassEffect(.regular, in: .capsule)
                .padding(.top, 12)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// Фон: мягкие пятна цвета — чем больше Mac «ваш», тем спокойнее и зеленее.
struct Backdrop: View {
    let score: Double

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color.pink.opacity(0.16 * (1 - score)), .clear], center: .topTrailing, startRadius: 10, endRadius: 520)
            RadialGradient(colors: [Color.teal.opacity(0.10 + 0.12 * score), .clear], center: .bottomLeading, startRadius: 10, endRadius: 560)
            RadialGradient(colors: [Color.indigo.opacity(0.10), .clear], center: .center, startRadius: 10, endRadius: 700)
        }
        .ignoresSafeArea()
        .animation(.smooth(duration: 1), value: score)
    }
}
