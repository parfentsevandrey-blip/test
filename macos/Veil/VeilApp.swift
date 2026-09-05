import AppKit
import SwiftUI

@main
struct VeilApp: App {
    @StateObject private var tunnel = TunnelCoordinator()
    @NSApplicationDelegateAdaptor(QuitGuard.self) private var quitGuard

    var body: some Scene {
        Window("Veil", id: "main") {
            RootView()
                .environmentObject(tunnel)
                .frame(minWidth: 820, minHeight: 560)
                // The window's own background is the ground the glass floats
                // over. Extending it under the sidebar is what makes the two
                // read as one surface rather than as two panes meeting.
                .background(WindowBackground())
                .onAppear { quitGuard.tunnel = tunnel }
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }

        // A VPN whose window is shut is still a VPN, so the state has to be
        // visible without one. The menu bar carries the same two facts the
        // window leads with: whether it is up, and what the last beat measured.
        MenuBarExtra {
            MenuBarPanel().environmentObject(tunnel)
        } label: {
            Image(systemName: tunnel.state.isLive ? "shield.lefthalf.filled" : "shield")
        }
        .menuBarExtraStyle(.menu)
    }
}

/// Quitting with the system proxy on would leave the Mac without internet
/// and without the app that could explain why. So a quit first takes the
/// traffic back out, then goes.
@MainActor
final class QuitGuard: NSObject, NSApplicationDelegate {
    weak var tunnel: TunnelCoordinator?

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard let tunnel, tunnel.mode != nil else { return .terminateNow }
        Task { @MainActor in
            await tunnel.detachTraffic()
            sender.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }
}

/// The ground: a quiet gradient rather than a flat fill, because Liquid Glass
/// refracts what is behind it and has nothing to say over a single colour.
private struct WindowBackground: View {
    var body: some View {
        LinearGradient(
            colors: [
                Color(nsColor: .windowBackgroundColor),
                Color(nsColor: .underPageBackgroundColor),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

private struct MenuBarPanel: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Text(tunnel.state.headline)
        if case .connected(let transport, _) = tunnel.state {
            Text("через \(transport.label)")
            if tunnel.pulse.ok {
                Text("пульс \(tunnel.pulse.rttMillis) мс")
            }
        }
        Divider()
        Button(tunnel.state.isLive || tunnel.state.isBusy ? "Отключить" : "Подключить") {
            tunnel.toggle()
        }
        Button("Открыть Veil") { openWindow(id: "main") }
        Divider()
        Button("Выйти") { NSApplication.shared.terminate(nil) }
    }
}
