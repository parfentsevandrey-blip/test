import SwiftUI

struct ActivityView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        VStack(spacing: 18) {
            if app.bridgeRunning {
                RoutingStatsRow(stats: app.bridgeStats)
            }
            TrafficChart(
                samples: app.traffic.samples,
                downloadRate: app.traffic.downloadRate,
                uploadRate: app.traffic.uploadRate,
                totalDownload: app.traffic.totalDownload,
                totalUpload: app.traffic.totalUpload,
                paddingRate: app.padding.rate,
                showsPadding: app.settings.paddingEnabled
            )
            .frame(height: 230)

            LogConsole(entries: app.logs, onClear: { app.clearLogs() }, onCopy: { app.copyLogs() })
        }
        .padding(28)
    }
}
