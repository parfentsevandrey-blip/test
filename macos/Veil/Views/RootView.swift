import SwiftUI

/// The window: a sidebar that floats, and one panel of content beside it.
///
/// Tahoe's sidebar is glass over the window's own background rather than a
/// separate pane with a divider, which is why the background is extended
/// under it and why nothing here draws a separator.
struct RootView: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    // Optional because that is what a List's single-selection
    // binding is; nil simply means nothing is highlighted yet.
    @State private var section: Section? = .tunnel

    enum Section: String, CaseIterable, Identifiable {
        case tunnel, routes, settings, log
        var id: String { rawValue }

        var title: String {
            switch self {
            case .tunnel: return "Тоннель"
            case .routes: return "Маршруты"
            case .settings: return "Настройки"
            case .log: return "Журнал"
            }
        }

        var symbol: String {
            switch self {
            case .tunnel: return "shield.lefthalf.filled"
            case .routes: return "point.3.filled.connected.trianglepath.dotted"
            case .settings: return "slider.horizontal.3"
            case .log: return "text.alignleft"
            }
        }
    }

    var body: some View {
        NavigationSplitView {
            List(Section.allCases, selection: $section) { item in
                Label(item.title, systemImage: item.symbol)
                    .tag(item)
            }
            .navigationSplitViewColumnWidth(min: 190, ideal: 200, max: 240)
            .safeAreaInset(edge: .bottom) { SidebarStatus() }
        } detail: {
            Group {
                switch section ?? .tunnel {
                case .tunnel: TunnelView()
                case .routes: RoutesView()
                case .settings: SettingsView()
                case .log: LogView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(Metrics.loose)
        }
        .navigationTitle((section ?? .tunnel).title)
    }
}

/// The one fact worth having in view on every screen.
private struct SidebarStatus: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    var body: some View {
        HStack(spacing: Metrics.snug) {
            StatusDot(tone: tone, animating: tunnel.state.isBusy)
            VStack(alignment: .leading, spacing: 1) {
                Text(tunnel.state.isLive ? "Подключено" : tunnel.state.headline)
                    .font(.system(size: 12, weight: .medium))
                    .lineLimit(1)
                if let transport = tunnel.state.transport {
                    Text(transport.label)
                        .font(.veilLabel)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Metrics.snug)
        .padding(.vertical, Metrics.snug)
        .veilGlass(shape: .rect(cornerRadius: Radius.control))
        .padding(Metrics.snug)
    }

    private var tone: StatusTone {
        switch tunnel.state.tone {
        case .idle: return .idle
        case .working: return .working
        case .live: return .live
        case .failed: return .failed
        }
    }
}
