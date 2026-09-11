import SwiftUI

enum SidebarItem: String, CaseIterable, Identifiable, Hashable {
    case home
    case security
    case locations
    case activity

    var id: SidebarItem { self }

    var title: LocalizedStringKey {
        switch self {
        case .home: "Home"
        case .security: "Security"
        case .locations: "Route"
        case .activity: "Activity"
        }
    }

    var symbol: String {
        switch self {
        case .home: "house.fill"
        case .security: "lock.shield.fill"
        case .locations: "point.3.connected.trianglepath.dotted"
        case .activity: "waveform.path.ecg"
        }
    }
}

struct RootView: View {
    @Environment(AppState.self) private var app
    @Environment(\.openWindow) private var openWindow

    private var selection: Binding<SidebarItem?> {
        Binding(
            get: { app.sidebarSelection },
            set: { app.sidebarSelection = $0 ?? .home }
        )
    }

    private var onboardingPresented: Binding<Bool> {
        Binding(
            get: { !app.settings.onboardingCompleted },
            set: { presented in if !presented { app.completeOnboarding() } }
        )
    }

    var body: some View {
        NavigationSplitView {
            List(SidebarItem.allCases, selection: selection) { item in
                Label(item.title, systemImage: item.symbol)
            }
            .navigationSplitViewColumnWidth(min: 190, ideal: 220, max: 280)
            .safeAreaInset(edge: .bottom) {
                SidebarStatusFooter()
            }
        } detail: {
            ZStack {
                AuroraBackground(state: app.connection)
                    .ignoresSafeArea()
                    .backgroundExtensionEffect()
                detailContent
            }
            .navigationTitle(app.sidebarSelection.title)
            .toolbar { toolbarContent }
        }
        .frame(minWidth: 980, minHeight: 660)
        .sheet(isPresented: onboardingPresented) {
            OnboardingView()
                .environment(app)
        }
    }

    @ViewBuilder
    private var detailContent: some View {
        switch app.sidebarSelection {
        case .home:
            DashboardView(
                openActivity: { app.sidebarSelection = .activity },
                openLocations: { app.sidebarSelection = .locations }
            )
        case .security:
            SecurityView()
        case .locations:
            RouteView()
        case .activity:
            ActivityView()
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            if app.connection.isActive {
                Button {
                    app.toggleConnection()
                } label: {
                    Label("Disconnect", systemImage: "stop.fill")
                }
                .help("Disconnect from Tor (⇧⌘K)")
            } else {
                Button {
                    app.toggleConnection()
                } label: {
                    Label("Connect", systemImage: "power")
                }
                .disabled(app.connection == .disconnecting)
                .help("Connect through Tor (⌘K)")
            }
        }
        ToolbarSpacer(.fixed)
        ToolbarItem {
            Button {
                openWindow(id: "mini")
            } label: {
                Label("Mini window", systemImage: "rectangle.portrait")
            }
            .help("Open the compact window (⇧⌘M)")
        }
        ToolbarItem {
            SettingsLink {
                Label("Settings", systemImage: "gearshape")
            }
            .help("Open Settings (⌘,)")
        }
    }
}

struct SidebarStatusFooter: View {
    @Environment(AppState.self) private var app

    var body: some View {
        HStack(spacing: 10) {
            StatusDot(state: app.connection)
            VStack(alignment: .leading, spacing: 2) {
                Text(app.connection.title)
                    .font(.subheadline.weight(.semibold))
                if app.killSwitchEngaged {
                    Text("Kill switch engaged")
                        .font(.caption2)
                        .foregroundStyle(.red)
                } else if app.turboActive, !app.connection.isActive {
                    Text("YouTube Turbo")
                        .font(.caption2)
                        .foregroundStyle(.red)
                } else if app.isDemo {
                    Text("Demo mode")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else if let version = app.torVersion, app.connection.isConnected {
                    Text("Tor \(version)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else {
                    Text("Tor · Snowflake")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }
}

struct StatusDot: View {
    let state: ConnectionState

    var body: some View {
        Circle()
            .fill(state.tint)
            .frame(width: 10, height: 10)
            .shadow(color: state.tint.opacity(0.8), radius: state.isConnected ? 6 : 0)
            .overlay {
                if state.isBusy {
                    Circle()
                        .stroke(state.tint.opacity(0.6), lineWidth: 2)
                        .scaleEffect(1.9)
                        .opacity(0.6)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: state)
    }
}
