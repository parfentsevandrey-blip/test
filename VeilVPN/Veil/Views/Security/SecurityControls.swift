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
                Divider()
                Group {
                    Toggle("Block plain HTTP through Tor", isOn: Binding(get: { app.settings.httpsOnly },
                                                                         set: { app.setHTTPSOnly($0) }))
                    Text("An exit relay can read and change anything that is not encrypted.")
                        .settingsCaption()
                    Toggle("A circuit per site", isOn: Binding(get: { app.settings.isolatePerSite },
                                                               set: { app.setIsolatePerSite($0) }))
                    Text("Applies to browser traffic immediately; apps using Veil’s SOCKS port directly pick it up on their next connection.")
                        .settingsCaption()
                }
                Divider()
                Group {
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
                Divider()
                Group {
                    Toggle("Traffic padding", isOn: Binding(get: { app.settings.paddingEnabled },
                                                            set: { app.setPaddingEnabled($0) }))
                    Text("Adds cover traffic through a private onion loop so the shape of your browsing is harder to read.")
                        .settingsCaption()
                    Toggle("Avoid Five Eyes countries", isOn: Binding(get: { app.settings.avoidFiveEyes },
                                                                      set: { app.setAvoidFiveEyes($0) }))
                    Text("Keeps US, UK, Canadian, Australian and New Zealand relays out of your circuits.")
                        .settingsCaption()
                }
                Group {
                    Divider()
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
                Divider()
                Group {
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

extension Text {
    /// The small grey line under a control. One definition, so captions never drift apart.
    func settingsCaption() -> some View {
        font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }
}
