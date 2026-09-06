import AppKit
import SwiftUI

struct SettingsView: View {
    var body: some View {
        TabView {
            GeneralSettingsView()
                .tabItem { Label("General", systemImage: "gearshape") }
            NetworkSettingsView()
                .tabItem { Label("Network", systemImage: "network") }
            BridgesSettingsView()
                .tabItem { Label("Bridges", systemImage: "snowflake") }
            PrivacySettingsView()
                .tabItem { Label("Privacy", systemImage: "lock.shield") }
            AboutView()
                .tabItem { Label("About", systemImage: "info.circle") }
        }
        .frame(width: 560)
    }
}

struct GeneralSettingsView: View {
    @Environment(AppState.self) private var app
    @State private var launchAtLogin = LaunchAtLogin.isEnabled
    @State private var launchAtLoginError: String?

    var body: some View {
        @Bindable var app = app
        Form {
            Section("Application") {
                Toggle("Show Veil in the menu bar", isOn: $app.settings.showInMenuBar)
                Toggle("Launch at login", isOn: $launchAtLogin)
                    .onChange(of: launchAtLogin) { _, enabled in
                        guard enabled != LaunchAtLogin.isEnabled else { return }
                        do {
                            try LaunchAtLogin.set(enabled)
                            launchAtLoginError = nil
                        } catch {
                            launchAtLoginError = error.localizedDescription
                            launchAtLogin = LaunchAtLogin.isEnabled
                        }
                    }
                if let launchAtLoginError {
                    Text(verbatim: launchAtLoginError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
                Toggle("Connect when Veil starts", isOn: $app.settings.connectOnLaunch)
            }
            Section("Connection") {
                Toggle("Verify the exit with check.torproject.org after connecting", isOn: $app.settings.checkAfterConnect)
                Toggle("Verbose Tor log", isOn: $app.settings.verboseLogs)
            }
        }
        .formStyle(.grouped)
    }
}

struct NetworkSettingsView: View {
    @Environment(AppState.self) private var app
    @State private var copied = false

    var body: some View {
        @Bindable var app = app
        Form {
            Section {
                Toggle("Route system traffic through Tor automatically", isOn: $app.settings.configureSystemProxy)
                Text("Sets the SOCKS, HTTP and HTTPS proxies of every active network service to Veil while connected and restores them afterwards. macOS asks for an administrator password the first time.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } header: {
                Text("System proxy")
            }
            Section {
                TextField("SOCKS5 port", value: $app.settings.socksPort, format: .number.grouping(.never))
                TextField("HTTP proxy port", value: $app.settings.httpPort, format: .number.grouping(.never))
                Text("Ports apply on the next connection. If a port is busy, Veil picks the next free one and shows it in the log.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } header: {
                Text("Local ports")
            }
            Section {
                Text(verbatim: app.terminalSnippet)
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    Button {
                        app.copyTerminalSnippet()
                        copied = true
                        Task {
                            try? await Task.sleep(for: .seconds(2))
                            copied = false
                        }
                    } label: {
                        Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                    Text("Tools such as curl, git and Homebrew ignore the system proxy; paste this into the terminal instead.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text("Terminal")
            }
        }
        .formStyle(.grouped)
    }
}

struct BridgesSettingsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        @Bindable var app = app
        Form {
            Section {
                Picker("Transport", selection: $app.settings.transport) {
                    ForEach(AppSettings.Transport.allCases) { transport in
                        Label(transport.title, systemImage: transport.symbol)
                            .tag(transport)
                    }
                }
                .pickerStyle(.inline)
                .labelsHidden()
                Text(app.settings.transport.details)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } header: {
                Text("How to reach the Tor network")
            }

            if app.settings.transport == .custom {
                Section {
                    TextEditor(text: $app.settings.customBridges)
                        .font(.system(.caption, design: .monospaced))
                        .frame(minHeight: 140)
                    HStack(spacing: 14) {
                        Link("bridges.torproject.org", destination: URL(string: "https://bridges.torproject.org/")!)
                        Link("Telegram @GetBridgesBot", destination: URL(string: "https://t.me/GetBridgesBot")!)
                    }
                    .font(.caption)
                } header: {
                    Text("Bridge lines")
                } footer: {
                    Text("One bridge per line, e.g. “obfs4 1.2.3.4:443 FINGERPRINT cert=… iat-mode=0”. A leading “Bridge” keyword is optional.")
                }
            }

            Section {
                Text("Changes take effect on the next connection.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }
}

struct PrivacySettingsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        Form {
            Section {
                Toggle("Traffic padding (DAITA-style)", isOn: Binding(
                    get: { app.settings.paddingEnabled },
                    set: { app.setPaddingEnabled($0) }
                ))
                Text("Injects dummy traffic into the tunnel so that an observer between this Mac and the Snowflake proxy cannot easily recognise which sites you visit from packet sizes and timing (website fingerprinting). Inspired by Mullvad’s DAITA and the Maybenot framework; unlike DAITA it never delays real packets, it only adds noise.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } header: {
                Text("Defence against traffic analysis")
            }

            if app.settings.paddingEnabled {
                Section {
                    Picker("Intensity", selection: Binding(
                        get: { app.settings.paddingLevel },
                        set: { app.setPaddingLevel($0) }
                    )) {
                        ForEach(PaddingLevel.allCases) { level in
                            Text(level.title).tag(level)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    Text(app.settings.paddingLevel.details)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                } header: {
                    Text("Intensity")
                }
            }

            Section {
                Text("Dummy traffic is bounced through the Tor network to a private onion service on this Mac, so no website ever receives it. Tor’s own circuit and connection padding are forced on as well. Padding costs bandwidth — yours, the Snowflake volunteer’s and the Tor relays’ — so use the lightest level that fits.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } header: {
                Text("How it works")
            }
        }
        .formStyle(.grouped)
    }
}

struct AboutView: View {
    @Environment(AppState.self) private var app

    private var version: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "dev"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        return "\(short) (\(build))"
    }

    var body: some View {
        VStack(spacing: 14) {
            Image(nsImage: NSApp.applicationIconImage)
                .resizable()
                .frame(width: 96, height: 96)
            Text("Veil")
                .font(.title.weight(.bold))
            Text("Version \(version)")
                .foregroundStyle(.secondary)
            if let torVersion = app.torVersion {
                Text("Tor \(torVersion)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text("A Liquid Glass client for the Tor network with Snowflake bridges. Tor, Snowflake and Lyrebird are © The Tor Project, Inc. and distributed under their own licenses.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 420)
            HStack(spacing: 16) {
                Link("torproject.org", destination: URL(string: "https://www.torproject.org/")!)
                Link("snowflake.torproject.org", destination: URL(string: "https://snowflake.torproject.org/")!)
            }
            .font(.caption)
        }
        .padding(28)
        .frame(maxWidth: .infinity)
    }
}
