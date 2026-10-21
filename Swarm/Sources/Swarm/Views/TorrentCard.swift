import SwiftUI

/// Карточка раздачи в списке. Это контент, поэтому без стекла: стеклянные только кнопки,
/// которые всплывают поверх при наведении.
struct TorrentCard: View {
    @Environment(AppModel.self) private var model
    let torrent: TorrentSnapshot
    let selected: Bool

    @State private var hovering = false
    @State private var confirmRemove = false

    private var kind: ContentKind { ContentKind(torrent) }
    private var tint: Color { torrent.status.color }
    private var isPaused: Bool { [.paused, .finished, .error].contains(torrent.status) }

    var body: some View {
        HStack(spacing: 14) {
            KindIcon(kind: kind)
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 8) {
                    Text(torrent.name)
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                    if torrent.isPrivate {
                        Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 8)
                    statusBadge
                }
                LiquidProgress(progress: displayedProgress, tint: tint,
                               active: torrent.status == .downloading && torrent.downRate > 0)
                if torrent.pieceCount > 0 {
                    PieceMosaic(snapshot: torrent, tint: tint, cell: 3, gap: 1)
                        .frame(height: 3)
                        .opacity(0.8)
                }
                details
            }
            actions
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.background.opacity(selected ? 0.85 : 0.6))
                .shadow(color: selected ? tint.opacity(0.35) : .black.opacity(0.08), radius: selected ? 16 : 6, y: 3)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(selected ? tint.opacity(0.7) : .primary.opacity(0.06), lineWidth: selected ? 1.5 : 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onHover { h in withAnimation(.smooth(duration: 0.2)) { hovering = h } }
        .onTapGesture(count: 2) { model.reveal(torrent) }
        .onTapGesture { withAnimation(.bouncy) { model.selectedID = selected ? nil : torrent.id } }
        .contextMenu { menu }
        .confirmationDialog("Удалить «\(torrent.name)»?", isPresented: $confirmRemove) {
            Button("Убрать из списка") { model.remove(torrent, deleteFiles: false) }
            Button("Удалить вместе с файлами", role: .destructive) { model.remove(torrent, deleteFiles: true) }
        }
    }

    private var displayedProgress: Double {
        switch torrent.status {
        case .checking: torrent.checkProgress ?? 0
        case .metadata: torrent.metadataProgress ?? 0
        default: torrent.progress
        }
    }

    private var statusBadge: some View {
        HStack(spacing: 5) {
            Image(systemName: torrent.status.symbol)
                .symbolEffect(.pulse, isActive: torrent.status == .metadata || torrent.status == .checking)
            Text(torrent.status.title)
        }
        .font(.system(size: 11, weight: .semibold))
        .foregroundStyle(tint)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Capsule().fill(tint.opacity(0.12)))
    }

    private var details: some View {
        HStack(spacing: 14) {
            Text(summary)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 4)
            if torrent.downRate > 0 || torrent.status == .downloading {
                Label(Format.rate(torrent.downRate), systemImage: "arrow.down")
                    .foregroundStyle(torrent.downRate > 0 ? Color.blue : .secondary)
            }
            if torrent.upRate > 0 || torrent.status == .seeding {
                Label(Format.rate(torrent.upRate), systemImage: "arrow.up")
                    .foregroundStyle(torrent.upRate > 0 ? Color(red: 0.15, green: 0.75, blue: 0.55) : .secondary)
            }
            Label("\(torrent.peersConnected)", systemImage: "person.2.fill")
                .foregroundStyle(.secondary)
        }
        .font(.system(size: 11.5, weight: .medium))
        .monospacedDigit()
        .contentTransition(.numericText())
    }

    private var summary: String {
        switch torrent.status {
        case .metadata:
            return torrent.peersConnected > 0 ? "Получаю описание раздачи от пиров…" : "Ищу пиров в DHT…"
        case .checking:
            return "Проверяю файлы · \(Format.percent(torrent.checkProgress ?? 0))"
        case .error:
            return torrent.error ?? "Ошибка"
        default:
            var parts = ["\(Format.percent(torrent.progress)) · \(Format.bytes(torrent.doneSize)) из \(Format.bytes(torrent.wantedSize))"]
            if let eta = Format.eta(torrent.eta) { parts.append("осталось \(eta)") }
            if torrent.status == .seeding { parts.append(String(format: "рейтинг %.2f", torrent.ratio)) }
            return parts.joined(separator: " · ")
        }
    }

    private var actions: some View {
        GlassEffectContainer(spacing: 8) {
            HStack(spacing: 8) {
                if hovering {
                    Group {
                        Button { model.reveal(torrent) } label: { Image(systemName: "folder") }
                            .help("Показать в Finder")
                        Button { confirmRemove = true } label: { Image(systemName: "trash") }
                            .help("Удалить")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.large)
                    .transition(.scale(scale: 0.6, anchor: .trailing).combined(with: .opacity))
                }
                // Главная кнопка видна всегда: крупная, в цвет состояния раздачи.
                Button { model.toggle(torrent) } label: {
                    Image(systemName: isPaused ? "play.fill" : "pause.fill")
                        .font(.system(size: 17, weight: .bold))
                        .contentTransition(.symbolEffect(.replace))
                        .frame(width: 30, height: 30)
                }
                .buttonStyle(.glassProminent)
                .tint(isPaused ? Color.accentColor : tint)
                .controlSize(.large)
                .help(isPaused ? "Продолжить" : "Пауза")
            }
            .buttonBorderShape(.circle)
        }
    }

    @ViewBuilder
    private var menu: some View {
        Button(isPaused ? "Продолжить" : "Пауза") { model.toggle(torrent) }
        Button("Показать в Finder") { model.reveal(torrent) }
        Button("Скопировать magnet-ссылку") { model.copyMagnet(torrent) }
        Divider()
        Toggle("Последовательная загрузка", isOn: Binding(get: { torrent.isSequential }, set: { model.setSequential(torrent, $0) }))
        Divider()
        Button("Удалить…", role: .destructive) { confirmRemove = true }
    }
}
