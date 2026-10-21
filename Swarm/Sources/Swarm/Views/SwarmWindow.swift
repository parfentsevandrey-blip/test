import SwiftUI

/// Отдельное окно роя: та же 3D-сцена во весь экран, со сводкой раздачи в стеклянной капсуле.
struct SwarmWindowView: View {
    @Environment(AppModel.self) private var model
    let torrentID: String?

    private var torrent: TorrentSnapshot? { model.torrents.first { $0.id == torrentID } }

    var body: some View {
        Group {
            if let t = torrent {
                SwarmConstellation(snapshot: t, tint: t.status.color, expandable: false)
                    .overlay(alignment: .top) { summary(t).padding(.top, 14) }
                    .navigationTitle(t.name)
            } else {
                ContentUnavailableView("Раздача удалена", systemImage: "point.3.connected.trianglepath.dotted")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(red: 0.02, green: 0.025, blue: 0.06))
                    .environment(\.colorScheme, .dark)
            }
        }
        .ignoresSafeArea()
        .frame(minWidth: 640, minHeight: 460)
        .onAppear { if let torrentID { model.watch(torrentID) } }
        .onDisappear { if let torrentID { model.unwatch(torrentID) } }
    }

    private func summary(_ t: TorrentSnapshot) -> some View {
        VStack(spacing: 3) {
            Text(t.name)
                .font(.system(size: 14, weight: .semibold))
                .lineLimit(1)
                .truncationMode(.middle)
            HStack(spacing: 12) {
                Text("\(Format.percent(t.progress)) · \(Format.bytes(t.doneSize)) из \(Format.bytes(t.wantedSize))")
                if let eta = Format.eta(t.eta) { Text("осталось \(eta)") }
                Text(t.status.title)
            }
            .font(.system(size: 11.5, weight: .medium))
            .foregroundStyle(.secondary)
            .monospacedDigit()
            .contentTransition(.numericText())
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 9)
        .frame(maxWidth: 560)
        .glassEffect(.clear, in: .capsule)
        .environment(\.colorScheme, .dark)
    }
}
