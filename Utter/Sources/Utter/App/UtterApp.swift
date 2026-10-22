import AppKit
import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        UtterApp.main()
    }
}

struct UtterApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        MenuBarExtra {
            MenuView().environment(AppModel.shared)
        } label: {
            MenuBarIcon().environment(AppModel.shared)
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView().environment(AppModel.shared)
        }
    }
}

/// Значок в строке меню: волна в покое, заполненный кружок — пока слушает.
struct MenuBarIcon: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        switch model.phase {
        case .idle: Image(systemName: "waveform")
        case .listening: Image(systemName: model.speaking ? "waveform.circle.fill" : "waveform.circle")
        case .finishing: Image(systemName: "ellipsis.circle")
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        let model = AppModel.shared
        model.start()
        if DemoUI.isOn {
            Task { await DemoUI.run(model) }
        } else if !model.onboardingDone || !model.micGranted || !model.accessibilityGranted {
            model.showOnboarding()
        }
    }
}
