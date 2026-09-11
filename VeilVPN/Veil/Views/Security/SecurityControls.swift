import SwiftUI

/// Every protective control in one place, so there is exactly one home for each.
struct SecurityControls: View {
    @Environment(AppState.self) private var app

    var body: some View {
        SecuritySection(title: "Controls") {
            VStack(alignment: .leading, spacing: 14) {
                Group {
                    Toggle("Kill switch", isOn: Binding(get: { app.settings.killSwitch },
                                                        set: { app.setKillSwitch($0) }))
                    Text("Keeps the system proxy pointed at Veil when Tor dies, so nothing falls back to a direct connection.")
                        .settingsCaption()
                    Toggle("Cut open connections too", isOn: Binding(get: { app.settings.closeSessionsOnKillSwitch },
                                                                     set: { app.setCloseSessionsOnKillSwitch($0) }))
                        .disabled(!app.settings.killSwitch)
                    Text("Without this the kill switch refuses new connections but a download already in flight keeps going.")
                        .settingsCaption()
                }
                Group {
                    Divider()
                    Toggle("Block plain HTTP through Tor", isOn: Binding(get: { app.settings.httpsOnly },
                                                                         set: { app.setHTTPSOnly($0) }))
                    Text("An exit relay can read and change anything that is not encrypted.")
                        .settingsCaption()
                    Toggle("A circuit per site", isOn: Binding(get: { app.settings.isolatePerSite },
                                                               set: { app.setIsolatePerSite($0) }))
                    Text("Applies to browser traffic immediately; apps using Veil’s SOCKS port directly pick it up on their next connection.")
                        .settingsCaption()
                }
                Group {
                    Divider()
                    Toggle("Measured circuits", isOn: Binding(get: { app.settings.lanePoolEnabled },
                                                              set: { app.setLanePoolEnabled($0) }))
                    Text("Keeps several circuits open, measures them continuously and sends each new connection down a fast one. Open connections stay where they are.")
                        .settingsCaption()
                    if app.settings.lanePoolEnabled {
                        Stepper(value: Binding(get: { app.settings.lanePoolSize },
                                               set: { app.setLanePoolSize($0) }), in: 2...6) {
                            Text("Circuits kept open") + Text(verbatim: ": \(app.settings.lanePoolSize)")
                        }
                        Toggle("Re-try a slow connection on a second circuit", isOn: Binding(get: { app.settings.lanePoolHedging },
                                                                                             set: { app.setLanePoolHedging($0) }))
                        Text("Only fires above about a second, so it can never slow down a connection that was about to succeed.")
                            .settingsCaption()
                    }
                }
                MultihopControls()
                Group {
                    Divider()
                    Toggle("Traffic padding", isOn: Binding(get: { app.settings.paddingEnabled },
                                                            set: { app.setPaddingEnabled($0) }))
                    Text("Adds cover traffic through a private onion loop so the shape of your browsing is harder to read.")
                        .settingsCaption()
                    Picker("Fast connect", selection: Binding(get: { app.settings.warmStart },
                                                              set: { app.setWarmStart($0) })) {
                        Text("Off").tag(AppSettings.WarmStart.off)
                        Text("Standby").tag(AppSettings.WarmStart.standby)
                        Text("Connect at launch").tag(AppSettings.WarmStart.preBootstrap)
                    }
                    .pickerStyle(.segmented)
                    Text(warmStartCaption)
                        .settingsCaption()
                }
                Group {
                    Divider()
                    Toggle("Check for updates only after connecting", isOn: Binding(get: { app.settings.updateCheckAfterConnect },
                                                                                     set: { app.setUpdateCheckAfterConnect($0) }))
                    Toggle("Leave addresses out of exported diagnostics", isOn: Binding(get: { app.settings.redactDiagnostics },
                                                                                         set: { app.setRedactDiagnostics($0) }))
                    Picker("When Veil quits", selection: Binding(get: { app.settings.forgetPolicy },
                                                                  set: { app.setForgetPolicy($0) })) {
                        Text("Keep everything").tag(AppSettings.ForgetPolicy.off)
                        Text("Forget cached relays").tag(AppSettings.ForgetPolicy.caches)
                        Text("Forget everything").tag(AppSettings.ForgetPolicy.everything)
                    }
                    Text(forgetCaption)
                        .settingsCaption()
                    Button("Forget which networks this Mac has used") { app.forgetConnectHistory() }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                }
            }
        }
    }

    private var warmStartCaption: LocalizedStringKey {
        switch app.settings.warmStart {
        case .off:
            "Tor is started only when you connect. Nothing runs in the background."
        case .standby:
            "One tor process stays loaded but offline: no listener, no packets. Connecting then takes a single command."
        case .preBootstrap:
            "Veil connects to the Tor network as soon as it launches, before you press Connect. Your provider will see Tor traffic you did not ask for."
        }
    }

    private var forgetCaption: LocalizedStringKey {
        switch app.settings.forgetPolicy {
        case .off: "Tor keeps its cached relay data and its entry guard between runs."
        case .caches: "Cached relay data is removed; the entry guard is kept, which is better for anonymity."
        case .everything: "Everything is removed, including the entry guard. Tor will pick a new one next time, which exposes you to more relays over time."
        }
    }
}

/// Where the circuit goes, and how often it moves. This belongs next to the kill switch rather
/// than on the map: it is a protective choice, not a place picker.
struct MultihopControls: View {
    @Environment(AppState.self) private var app

    private static let rotationChoices = [0, 15, 30, 60, 180]

    var body: some View {
        Group {
            Divider()
            Toggle("Pin the middle relay’s country", isOn: Binding(get: { app.settings.multihopEnabled },
                                                                   set: { app.setMultihopEnabled($0) }))
            Text("Tor always builds three hops. This fixes where the middle one may live, so the first and last hops cannot both be chosen by one operator.")
                .settingsCaption()
            HStack {
                Text("Excluded countries")
                Spacer(minLength: 8)
                Text(verbatim: "\(app.settings.route.excludedCountries.count)")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                Button("Choose on the Route screen") { app.sidebarSelection = .locations }
                    .buttonStyle(.link)
            }
            Picker("Move the route every", selection: Binding(get: { app.settings.rotateRouteMinutes },
                                                              set: { app.setRotateRouteMinutes($0) })) {
                ForEach(Self.rotationChoices, id: \.self) { minutes in
                    if minutes == 0 {
                        Text("Never").tag(0)
                    } else {
                        Text(verbatim: "\(minutes) min").tag(minutes)
                    }
                }
            }
            Text("A timed rotation steers away from the relays it just used, so the route really moves. Existing connections keep theirs until they finish.")
                .settingsCaption()
        }
    }
}

extension Text {
    /// The small grey line under a control. One definition, so captions never drift apart.
    func settingsCaption() -> some View {
        font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }
}
