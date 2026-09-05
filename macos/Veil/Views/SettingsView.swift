import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    var body: some View {
        ScrollView {
            GlassGroup(spacing: Metrics.regular) {
                VStack(spacing: Metrics.regular) {
                    GlassPanel {
                        VStack(alignment: .leading, spacing: Metrics.regular) {
                            SectionTitle("Защита")

                            Toggle(isOn: $tunnel.settings.killSwitch) {
                                SettingLabel(
                                    "Не выпускать трафик мимо тоннеля",
                                    "Если тоннель падает, машина остаётся без сети, а не уходит в открытую. Включено — это и есть смысл kill switch."
                                )
                            }

                            Toggle(isOn: $tunnel.settings.blockUDP) {
                                SettingLabel(
                                    "Отбрасывать UDP",
                                    "Tor умеет только TCP. Всё, что ушло бы по UDP, — это и утечка, и отпечаток. QUIC и WebRTC сами откатятся на TCP."
                                )
                            }
                        }
                    }

                    GlassPanel {
                        VStack(alignment: .leading, spacing: Metrics.regular) {
                            SectionTitle("Скорость и чистота")

                            Toggle(isOn: $tunnel.settings.blockAds) {
                                SettingLabel(
                                    "Блокировать рекламу и трекеры",
                                    "Около 80 000 имён получают ответ «нет такого имени» ещё до тоннеля. На пути через браузер добровольца это не только баннеры: страница с дюжиной трекеров грузится в разы дольше."
                                )
                            }

                            Toggle(isOn: $tunnel.settings.pulse) {
                                SettingLabel(
                                    "Пульс канала",
                                    "Каждые 20 секунд — крошечный запрос через тоннель, раз в пять минут — замер скорости. Держит путь живым после простоя и замечает обрыв за 40 секунд, а не за две минуты."
                                )
                            }
                        }
                    }

                    GlassPanel {
                        VStack(alignment: .leading, spacing: Metrics.snug) {
                            SectionTitle("О приложении")
                            Text("Ни аккаунта, ни подписки, ни одного нашего сервера. Только Tor и его собственные мосты; всё, что нужно для первого подключения, лежит внутри приложения.")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)

                            HStack(spacing: Metrics.tight) {
                                Chip(text: "arm64")
                                Chip(text: "macOS 26")
                                if !Core.isAvailable {
                                    Chip(text: "ядро не собрано", tone: .orange)
                                }
                            }
                            .padding(.top, 2)
                        }
                    }
                }
            }
        }
        .toggleStyle(.switch)
        .scrollBounceBehavior(.basedOnSize)
    }
}

private struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text.uppercased())
            .font(.veilLabel)
            .kerning(0.6)
            .foregroundStyle(.secondary)
    }
}

private struct SettingLabel: View {
    let title: String
    let detail: String
    init(_ title: String, _ detail: String) {
        self.title = title
        self.detail = detail
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.system(size: 13))
            Text(detail)
                .font(.system(size: 11.5))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
