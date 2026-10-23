import SwiftUI

/// Экран вместо страницы: объясняет, что случилось, и даёт выбор.
struct InterstitialView: View {
    let tab: Tab
    let kind: Interstitial

    var body: some View {
        ZStack {
            background.ignoresSafeArea()
            VStack(spacing: 18) {
                Image(systemName: icon)
                    .font(.system(size: 54))
                    .foregroundStyle(tint.gradient)
                Text(title)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .multilineTextAlignment(.center)
                Text(message)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 560)
                    .fixedSize(horizontal: false, vertical: true)
                Text(address)
                    .font(.callout.monospaced())
                    .foregroundStyle(.tertiary)
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .frame(maxWidth: 560)
                HStack(spacing: 12) {
                    Button(primaryTitle) { primary() }
                        .buttonStyle(.glassProminent)
                        .tint(tint)
                        .controlSize(.large)
                        .keyboardShortcut(.defaultAction)
                    if let secondaryTitle {
                        Button(secondaryTitle) { secondary() }
                            .buttonStyle(.glass)
                            .controlSize(.large)
                    }
                }
                .padding(.top, 6)
            }
            .padding(40)
        }
    }

    private var url: URL { kind.url }

    private var address: String { url.absoluteString }

    private var background: some View {
        LinearGradient(colors: [tint.opacity(0.10), Color(nsColor: .windowBackgroundColor)], startPoint: .top, endPoint: .bottom)
            .background(Color(nsColor: .windowBackgroundColor))
    }

    private var icon: String {
        switch kind {
        case .danger: "exclamationmark.octagon.fill"
        case .lookalike: "theatermasks.fill"
        case .insecure: "lock.open.trianglebadge.exclamationmark.fill"
        case .blocked: "hand.raised.fill"
        case .failed: "wifi.exclamationmark"
        }
    }

    private var tint: Color {
        switch kind {
        case .danger: .red
        case .lookalike: .red
        case .insecure: .orange
        case .blocked: .purple
        case .failed: .gray
        }
    }

    private var title: String {
        switch kind {
        case .danger: "Опасный сайт"
        case .lookalike(_, let original): "Похоже на подделку под \(original)"
        case .insecure: "Сайт не поддерживает защищённое соединение"
        case .blocked: "Ward остановил эту страницу"
        case .failed: "Страница не открылась"
        }
    }

    private var message: String {
        switch kind {
        case .danger:
            "Этот адрес есть в списках вредоносных и мошеннических сайтов (URLhaus, uBlock Origin). Отсюда распространяют вирусы или крадут пароли и данные карт. Ward не стал к нему обращаться."
        case .lookalike(let u, let original):
            "Адрес «\(Punycode.display(u.host ?? "").text)» очень похож на \(original), но это другой сайт. Так делают мошенники: копируют страницу входа банка или сервиса и ждут, что вы не заметите лишнюю или подменённую букву. Вы здесь ещё не бывали."
        case .insecure:
            "Ward попробовал открыть \(url.host ?? "сайт") по HTTPS, но сайт не ответил. Без шифрования всё, что вы видите и вводите, видно провайдеру и любой сети по пути — и это можно подменить. Не вводите здесь пароли."
        case .blocked:
            "Адрес целиком в списках рекламы или слежки — обычно это рекламный или следящий сервер, а не сайт. Если вы открыли его намеренно, можно открыть один раз без защиты."
        case .failed(_, let reason):
            reason
        }
    }

    private var primaryTitle: String {
        switch kind {
        case .lookalike(_, let original): "Перейти на \(original)"
        case .danger, .insecure, .blocked: tab.canGoBack ? "Вернуться назад" : "На новую вкладку"
        case .failed: "Попробовать снова"
        }
    }

    private var secondaryTitle: String? {
        switch kind {
        case .danger: "Всё равно открыть"
        case .lookalike: "Это не подделка"
        case .insecure: "Открыть без защиты"
        case .blocked: "Открыть один раз"
        case .failed: nil
        }
    }

    private func primary() {
        switch kind {
        case .failed: tab.reload()
        case .lookalike(_, let original):
            if let u = URL(string: "https://" + original) { tab.load(u) }
        default:
            if tab.canGoBack {
                tab.interstitial = nil
                tab.goBack()
            } else {
                tab.interstitial = nil
                tab.url = nil
                tab.title = ""
            }
        }
    }

    private func secondary() {
        switch kind {
        case .danger(let u): tab.proceedDanger(u)
        case .lookalike(let u, _): tab.proceedLookalike(u)
        case .insecure(let u): tab.proceedInsecure(u)
        case .blocked(let u): tab.proceedBlocked(u)
        case .failed: break
        }
    }
}
