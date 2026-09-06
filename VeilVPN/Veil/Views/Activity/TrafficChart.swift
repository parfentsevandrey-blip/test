import Charts
import SwiftUI

struct TrafficChart: View {
    let samples: [TrafficSample]
    let downloadRate: Double
    let uploadRate: Double
    let totalDownload: UInt64
    let totalUpload: UInt64
    var paddingRate: Double = 0
    var showsPadding: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Label("Throughput", systemImage: "waveform.path.ecg")
                    .font(.headline)
                Spacer()
                LegendItem(color: .cyan, title: "Download", rate: downloadRate, total: totalDownload)
                LegendItem(color: .orange, title: "Upload", rate: uploadRate, total: totalUpload)
                if showsPadding {
                    LegendItem(color: .purple, title: "Padding", rate: paddingRate, total: nil)
                }
            }

            Chart {
                ForEach(samples) { sample in
                    AreaMark(
                        x: .value("Time", sample.date),
                        y: .value("Rate", sample.download),
                        series: .value("Direction", "Download"),
                        stacking: .unstacked
                    )
                    .foregroundStyle(LinearGradient(colors: [.cyan.opacity(0.45), .cyan.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)

                    LineMark(
                        x: .value("Time", sample.date),
                        y: .value("Rate", sample.download),
                        series: .value("Direction", "Download")
                    )
                    .foregroundStyle(.cyan)
                    .lineStyle(StrokeStyle(lineWidth: 2))
                    .interpolationMethod(.catmullRom)

                    AreaMark(
                        x: .value("Time", sample.date),
                        y: .value("Rate", sample.upload),
                        series: .value("Direction", "Upload"),
                        stacking: .unstacked
                    )
                    .foregroundStyle(LinearGradient(colors: [.orange.opacity(0.4), .orange.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)

                    LineMark(
                        x: .value("Time", sample.date),
                        y: .value("Rate", sample.upload),
                        series: .value("Direction", "Upload")
                    )
                    .foregroundStyle(.orange)
                    .lineStyle(StrokeStyle(lineWidth: 2))
                    .interpolationMethod(.catmullRom)

                    if showsPadding {
                        LineMark(
                            x: .value("Time", sample.date),
                            y: .value("Rate", sample.padding),
                            series: .value("Direction", "Padding")
                        )
                        .foregroundStyle(.purple)
                        .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                        .interpolationMethod(.catmullRom)
                    }
                }
            }
            .chartXAxis(.hidden)
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) { value in
                    AxisGridLine()
                        .foregroundStyle(.primary.opacity(0.08))
                    AxisValueLabel {
                        if let rate = value.as(Double.self) {
                            Text(verbatim: ByteFormat.rate(rate))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .chartLegend(.hidden)
            .overlay {
                if samples.isEmpty {
                    Text("Throughput appears here while you are connected.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(20)
        .glassEffect(.regular, in: .rect(cornerRadius: 22))
    }
}

private struct LegendItem: View {
    let color: Color
    let title: LocalizedStringKey
    let rate: Double
    let total: UInt64?

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(verbatim: total.map { "\(ByteFormat.rate(rate)) · \(ByteFormat.total($0))" } ?? ByteFormat.rate(rate))
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
            }
        }
        .padding(.leading, 12)
    }
}
