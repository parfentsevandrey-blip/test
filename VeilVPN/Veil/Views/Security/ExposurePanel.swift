import SwiftUI

/// Names what bypasses Tor, and makes stopping it one tap. A bypass is never rendered as fine.
struct ExposurePanel: View {
    let posture: SecurityPosture
    @Environment(AppState.self) private var app

    var body: some View {
        SecuritySection(title: "What is outside Tor") {
            VStack(alignment: .leading, spacing: 10) {
                if posture.bypassClasses == 0 {
                    Text("Nothing. Every destination goes through Tor.")
                        .font(.subheadline)
                } else {
                    Text("These see your real IP address, and your provider sees that you visit them.")
                        .settingsCaption()
                    ForEach(rows, id: \.self) { row in
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.up.forward")
                                .font(.caption)
                                .foregroundStyle(.orange)
                            Text(verbatim: row)
                                .font(.subheadline)
                        }
                    }
                    Button("Send everything through Tor") {
                        app.applyFix(.clearBypasses)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
            }
        }
    }

    private var rows: [String] {
        var names: [String] = []
        let policy = app.settings.routingPolicy
        if app.settings.youtubeMode != .tor { names.append("YouTube") }
        for preset in ServiceCatalog.all {
            if let mode = policy.serviceModes[preset.id], mode != .tor { names.append(preset.name) }
        }
        for domain in policy.customDirectDomains.prefix(8) { names.append(domain) }
        return names
    }
}
