import SwiftUI

/// Левая колонка: кнопка, состояние, текущий сервер, адрес выхода, скорость, режим и правила.
struct ConnectionPanel: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 6) {
                    ShieldOrb(status: model.status) { model.toggle() }
                    Text(model.status.title)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .contentTransition(.numericText())
                    statusLine
                }
                .padding(.top, 8)

                currentServer
                if model.status.isOn { traffic }
                modePicker
                routingPicker
            }
            .padding(18)
            .animation(.smooth(duration: 0.35), value: model.status)
        }
        .scrollIndicators(.never)
    }

    @ViewBuilder
    private var statusLine: some View {
        switch model.status {
        case .connected(let since):
            TimelineView(.periodic(from: since, by: 1)) { ctx in
                HStack(spacing: 6) {
                    Text(Format.clock(ctx.date.timeIntervalSince(since))).monospacedDigit()
                    if let exit = model.exitInfo {
                        Text("·")
                        Text("\(Flags.emoji(exit.country)) \(exit.ip)").textSelection(.enabled)
                    }
                }
                .font(.system(size: 12.5, weight: .medium))
                .foregroundStyle(.secondary)
            }
        case .reconnecting(let why):
            Text(why).font(.system(size: 12)).foregroundStyle(.secondary).multilineTextAlignment(.center)
        case .failed(let why):
            Text(why).font(.system(size: 12)).foregroundStyle(.red).multilineTextAlignment(.center).textSelection(.enabled)
        case .connecting:
            Text(model.saved.mode == .tun ? "Поднимаю туннель…" : "Запускаю ядро Xray…").font(.system(size: 12)).foregroundStyle(.secondary)
        case .disconnected:
            Text(model.selected == nil ? "Добавьте сервер справа" : "Нажмите, чтобы подключиться").font(.system(size: 12)).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var currentServer: some View {
        if let s = model.selected {
            GlassCard {
                HStack(spacing: 12) {
                    Text(s.flag.isEmpty ? "🌐" : s.flag).font(.system(size: 30))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(s.title).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                        Text(s.endpoint.summary).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    PingBadge(result: model.ping(s.id), busy: model.pinging.contains(s.id))
                }
            }
        }
    }

    private var traffic: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    stat("arrow.down", Format.speed(model.speedDown), Format.bytes(model.sessionDown), model.status.tint)
                    Spacer()
                    stat("arrow.up", Format.speed(model.speedUp), Format.bytes(model.sessionUp), Color(red: 0.55, green: 0.5, blue: 1))
                }
                SpeedGraph(points: model.speeds, tint: model.status.tint)
                    .frame(height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
    }

    private func stat(_ symbol: String, _ speed: String, _ total: String, _ tint: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 13, weight: .bold)).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 1) {
                Text(speed).font(.system(size: 15, weight: .semibold, design: .rounded)).monospacedDigit()
                Text("за сеанс \(total)").font(.system(size: 10.5)).foregroundStyle(.secondary)
            }
        }
    }

    private var modePicker: some View {
        GlassCard(padding: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Что пускать через VPN").font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    ForEach(ConnectionMode.allCases) { mode in
                        Button { model.setMode(mode) } label: {
                            VStack(spacing: 4) {
                                Image(systemName: mode.symbol).font(.system(size: 15, weight: .semibold))
                                Text(short(mode)).font(.system(size: 10.5, weight: .medium)).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .foregroundStyle(model.saved.mode == mode ? Color.white : .primary)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(model.saved.mode == mode ? model.status.tint.opacity(0.9) : Color.primary.opacity(0.05)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .help(mode.subtitle)
                    }
                }
                Text(model.saved.mode.subtitle).font(.system(size: 11)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func short(_ mode: ConnectionMode) -> String {
        switch mode {
        case .proxy: "Прокси"
        case .tun: "Весь Mac"
        case .local: "Локально"
        }
    }

    private var routingPicker: some View {
        GlassCard(padding: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Маршрутизация").font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
                ForEach(RouteMode.allCases.filter { $0 != .profile || model.saved.routing.profile != nil }) { mode in
                    Button { model.setRouting { $0.mode = mode } } label: {
                        HStack(spacing: 10) {
                            Image(systemName: mode.symbol).frame(width: 20).foregroundStyle(model.saved.routing.mode == mode ? model.status.tint : .secondary)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(mode == .profile ? (model.saved.routing.profile?.name ?? mode.title) : mode.title)
                                    .font(.system(size: 12.5, weight: .semibold))
                                Text(mode.subtitle).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(2)
                            }
                            Spacer(minLength: 0)
                            if model.saved.routing.mode == mode {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(model.status.tint)
                            }
                        }
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(model.saved.routing.mode == mode ? model.status.tint.opacity(0.12) : .clear))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                if model.geoDownloading {
                    Label("Скачиваю базы сайтов…", systemImage: "arrow.down.circle").font(.system(size: 10.5)).foregroundStyle(.secondary)
                }
            }
        }
    }
}
