import SwiftUI

/// The main screen: one control, and the measurements that say whether it is
/// telling the truth.
///
/// The readouts are the point rather than decoration. A coloured shield says
/// the app believes it is connected; a round trip measured eight seconds ago
/// and a rate in kilobytes say the path exists and is carrying traffic now.
/// The Android build learned the difference the hard way, and the interface
/// should not hide it.
struct TunnelView: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    @Namespace private var glass

    var body: some View {
        ScrollView {
            GlassGroup(spacing: Metrics.regular) {
                VStack(spacing: Metrics.regular) {
                    ConnectPanel(namespace: glass)

                    if tunnel.state.isLive {
                        MeasurementsPanel()
                        ModePanel()
                        RoutePanel()
                    } else if case .failed(let reason) = tunnel.state {
                        FailurePanel(reason: reason)
                    } else {
                        ExplainerPanel()
                    }
                }
            }
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}

// MARK: - The control

private struct ConnectPanel: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    var namespace: Namespace.ID

    var body: some View {
        GlassPanel(padding: Metrics.loose) {
            HStack(alignment: .center, spacing: Metrics.loose) {
                VStack(alignment: .leading, spacing: Metrics.snug) {
                    HStack(spacing: Metrics.tight) {
                        StatusDot(tone: tone, animating: tunnel.state.isBusy)
                        Text(tunnel.state.headline)
                            .font(.veilTitle)
                    }

                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    if case .bootstrapping(_, let percent, _) = tunnel.state {
                        ProgressView(value: Double(percent), total: 100)
                            .progressViewStyle(.linear)
                            .frame(maxWidth: 280)
                            .padding(.top, 2)
                    }
                }

                Spacer(minLength: 0)

                ConnectButton()
            }
        }
    }

    private var tone: StatusTone {
        switch tunnel.state.tone {
        case .idle: return .idle
        case .working: return .working
        case .live: return .live
        case .failed: return .failed
        }
    }

    private var subtitle: String {
        switch tunnel.state {
        case .idle:
            return "Ни аккаунта, ни подписки, ни одного нашего сервера. Подключение по методу, выбранному на экране «Маршруты»."
        case .verifying:
            return "Tor отчитался о готовности. Проверяю настоящим соединением — «подключено» появится только когда оно пройдёт."
        case .connected(let transport, let since):
            return "Через \(transport.label), \(Self.duration.string(from: since, to: Date()) ?? "")."
        case .failed:
            return "Ни одна попытка не дошла до сети."
        default:
            return "Идёт подключение."
        }
    }

    private static let duration: DateComponentsFormatter = {
        let f = DateComponentsFormatter()
        f.allowedUnits = [.hour, .minute, .second]
        f.unitsStyle = .abbreviated
        return f
    }()
}

/// The only surface in the app allowed to carry colour in its material.
private struct ConnectButton: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator
    @State private var hovering = false

    var body: some View {
        Button(action: tunnel.toggle) {
            VStack(spacing: Metrics.tight) {
                Image(systemName: tunnel.state.isLive ? "power" : "power")
                    .font(.system(size: 26, weight: .medium))
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
            }
            .frame(width: 128, height: 104)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .foregroundStyle(tunnel.state.isLive ? Color.green : Color.accentColor)
        .veilProminentGlass(
            shape: .rect(cornerRadius: Radius.panel),
            tint: tunnel.state.isLive ? .green : .accentColor
        )
        .scaleEffect(hovering ? 1.02 : 1)
        .animation(.smooth(duration: 0.2), value: hovering)
        .onHover { hovering = $0 }
        .accessibilityLabel(label)
    }

    private var label: String {
        if tunnel.state.isLive { return "Отключить" }
        if tunnel.state.isBusy { return "Остановить" }
        return "Подключить"
    }
}

// MARK: - Panels

private struct MeasurementsPanel: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    var body: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: Metrics.regular) {
                HStack(spacing: Metrics.loose) {
                    Readout(
                        label: "Пульс",
                        value: pulseText,
                        systemImage: "waveform.path.ecg",
                        tone: tunnel.pulse.ok ? .primary : .orange
                    )
                    Readout(
                        label: "Скорость",
                        value: tunnel.pulse.kilobytesPerSecond > 0
                            ? "\(tunnel.pulse.kilobytesPerSecond) КБ/с" : "—",
                        systemImage: "gauge.with.dots.needle.50percent"
                    )
                    Readout(
                        label: "Принято",
                        value: format(tunnel.stats.rxBytes),
                        systemImage: "arrow.down"
                    )
                    Readout(
                        label: "Передано",
                        value: format(tunnel.stats.txBytes),
                        systemImage: "arrow.up"
                    )
                }

                if tunnel.settings.blockAds {
                    Divider().opacity(0.5)
                    HStack(spacing: Metrics.tight) {
                        Image(systemName: "hand.raised")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                        Text("\(tunnel.stats.dnsBlocked) рекламных имён отклонено")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var pulseText: String {
        guard tunnel.pulse.hasMeasurement else { return "—" }
        if !tunnel.pulse.ok { return "нет ответа ×\(tunnel.pulse.failures)" }
        return "\(tunnel.pulse.rttMillis) мс"
    }

    private func format(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .binary)
    }
}

/// Which way the machine's traffic reaches tor, said plainly.
///
/// In proxy mode the one honest limitation is stated rather than hidden: an
/// application that ignores the system proxy is not protected. Telegram is
/// the common one, and it has its own one-click way in.
private struct ModePanel: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    var body: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: Metrics.snug) {
                switch tunnel.mode {
                case .tunnel:
                    Label("Системный туннель", systemImage: "shield.checkered")
                        .font(.veilTitle)
                    Text("Весь трафик Mac идёт через Tor — каждая программа, без настройки.")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                case .proxy:
                    Label("Системный прокси", systemImage: "network")
                        .font(.veilTitle)
                    Text("macOS не загрузил сетевое расширение (для него нужна подпись платной командой Apple), поэтому через Tor направлены системные прокси. Safari, Chrome, Firefox и большинство программ идут через них. Программы со своей сетью — игры, часть мессенджеров — остаются напрямую.")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: Metrics.snug) {
                        Button {
                            SystemProxy.openTelegram(socksPort: tunnel.loopbackSocksPort)
                        } label: {
                            Label("Настроить Telegram", systemImage: "paperplane")
                        }
                        .buttonStyle(.bordered)
                        Text("SOCKS5 127.0.0.1:\(String(tunnel.loopbackSocksPort))  ·  HTTP 127.0.0.1:\(String(tunnel.loopbackHTTPPort))")
                            .font(.veilMono)
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)
                    }
                    .padding(.top, 2)

                case nil:
                    EmptyView()
                }
            }
        }
    }
}

private struct RoutePanel: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    var body: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: Metrics.snug) {
                Text("Цепочка")
                    .font(.veilLabel)
                    .kerning(0.6)
                    .foregroundStyle(.secondary)

                Text(tunnel.circuit ?? "строится")
                    .font(.veilMono)
                    .textSelection(.enabled)

                HStack {
                    if let transport = tunnel.state.transport {
                        Chip(text: transport.label, tone: .primary)
                    }
                    Spacer()
                    Button("Новая цепочка") { tunnel.newCircuit() }
                        .buttonStyle(.borderless)
                        .font(.system(size: 12))
                }
            }
        }
    }
}

private struct FailurePanel: View {
    let reason: String

    var body: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: Metrics.snug) {
                Label("Не удалось", systemImage: "exclamationmark.triangle")
                    .font(.veilTitle)
                    .foregroundStyle(.orange)
                Text(reason)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Попробуйте другой метод обфускации на экране «Маршруты». То, что не сработало здесь, обычно работает там.")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct ExplainerPanel: View {
    var body: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: Metrics.snug) {
                Text("Что произойдёт при подключении")
                    .font(.veilTitle)
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .firstTextBaseline, spacing: Metrics.snug) {
                        Text("\(index + 1)")
                            .font(.veilLabel)
                            .foregroundStyle(.secondary)
                            .frame(width: 14, alignment: .trailing)
                        Text(step)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    private let steps = [
        "Запускаются транспорты и Tor — сразу с ними, чтобы смена маршрута потом не требовала перезапуска.",
        "Каталог сети берётся из самого приложения, а не скачивается.",
        "Tor строит канал через выбранную обфускацию.",
        "Через готовый канал открывается настоящее соединение. Только когда оно проходит, появляется «Подключено».",
        "Трафик машины уходит в тоннель.",
    ]
}
