import SwiftUI

/// Per-service routing presets (Discord, Telegram, Twitch, ...).
struct SitesSettingsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        Form {
            Section {
                ForEach(ServiceCatalog.all) { service in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 12) {
                            Image(systemName: service.symbol)
                                .frame(width: 22)
                                .foregroundStyle(.tint)
                            Text(verbatim: service.name)
                                .font(.body.weight(.medium))
                            Spacer()
                            Picker("", selection: Binding(
                                get: { app.serviceRoute(service.id) },
                                set: { app.setServiceRoute(service.id, $0) }
                            )) {
                                ForEach(RouteMode.allCases) { mode in
                                    Text(mode.title).tag(mode)
                                }
                            }
                            .labelsHidden()
                            .frame(width: 230)
                        }
                        if let note = service.note {
                            Text(note)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(.leading, 34)
                        }
                        Text(verbatim: service.domains.joined(separator: ", "))
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                            .padding(.leading, 34)
                        if service.id == "telegram" {
                            HStack(spacing: 10) {
                                Button {
                                    app.addProxyToTelegram()
                                } label: {
                                    Label("Add Veil’s proxy to Telegram", systemImage: "paperplane")
                                }
                                Text("Opens Telegram with a SOCKS5 proxy pointing at Veil; confirm it there. Switch the proxy off inside Telegram when Veil is not running.")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .padding(.leading, 34)
                            .padding(.top, 4)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("Sites")
            } footer: {
                Text("“Through Tor” is the default. Direct modes are fast and bypass throttling, but the site sees your real IP address. YouTube has its own tab.")
            }
        }
        .formStyle(.grouped)
    }
}
