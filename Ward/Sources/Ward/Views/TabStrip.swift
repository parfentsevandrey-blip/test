import AppKit
import SwiftUI

/// Полоса вкладок под панелью.
struct TabStrip: View {
    let browser: BrowserState

    var body: some View {
        HStack(spacing: 6) {
            if browser.usesProxy {
                Label("Через прокси", systemImage: "network.badge.shield.half.filled")
                    .font(.callout.weight(.medium))
                    .foregroundStyle(.blue)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.blue.opacity(0.14), in: Capsule())
                    .help("Страницы этого окна идут через \(AppModel.shared.prefs.proxyHost):\(AppModel.shared.prefs.proxyPort)")
            }
            if browser.isPrivate {
                Label("Частное окно", systemImage: "eye.slash")
                    .font(.callout.weight(.medium))
                    .foregroundStyle(.purple)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.purple.opacity(0.16), in: Capsule())
                    .help("Cookie, история и кэш этого окна живут только в памяти и исчезнут, когда вы его закроете.")
            }
            GeometryReader { geo in
                let width = chipWidth(geo.size.width - 36)
                ScrollViewReader { scroller in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 4) {
                            ForEach(browser.tabs) { tab in
                                TabChip(tab: tab, browser: browser, selected: tab.id == browser.selected?.id)
                                    .frame(width: width)
                                    .id(tab.id)
                            }
                            NewTabButton(browser: browser)
                                .id("new-tab")
                            // Двойной щелчок по пустому месту полосы — новая вкладка.
                            Color.clear
                                .frame(minWidth: 20, maxWidth: .infinity, minHeight: 28)
                                .contentShape(Rectangle())
                                .onTapGesture(count: 2) { browser.newTab() }
                        }
                        .padding(.vertical, 4)
                        .frame(minWidth: geo.size.width, alignment: .leading)
                    }
                    .onChange(of: browser.selected?.id) { _, id in
                        if let id { withAnimation(.smooth) { scroller.scrollTo(id) } }
                    }
                }
            }
            .frame(height: 36)
        }
        .padding(.horizontal, 10)
        .background(browser.isPrivate ? AnyShapeStyle(Color.purple.opacity(0.06)) : AnyShapeStyle(.background.secondary))
    }

    private func chipWidth(_ total: CGFloat) -> CGFloat {
        let n = CGFloat(max(1, browser.tabs.count))
        return max(120, min(240, (total - 4 * n) / n))
    }
}

struct TabChip: View {
    let tab: Tab
    let browser: BrowserState
    let selected: Bool
    @State private var hover = false

    var body: some View {
        HStack(spacing: 7) {
            if tab.inIsolator {
                Image(systemName: "cube.transparent.fill").font(.caption).foregroundStyle(.teal)
                    .help("Изолятор: сайт заперт в своей банке с вымышленным устройством")
            } else if tab.jarKind == .corporation, let jar = tab.jar {
                Image(systemName: "building.2.crop.circle.fill").font(.caption).foregroundStyle(.orange)
                    .help("Банка «\(Jars.title(of: jar))»: все её сервисы вместе, но без связи с другими корпорациями")
            } else if tab.ephemeral {
                Image(systemName: "flame.fill").font(.caption).foregroundStyle(.orange)
            } else {
                SiteTile(host: tab.host, size: 16)
            }
            Text(tab.displayTitle)
                .lineLimit(1)
                .font(.callout)
                .foregroundStyle(selected ? .primary : .secondary)
            Spacer(minLength: 0)
            if tab.isLoading {
                ProgressView().controlSize(.mini)
            } else if tab.blockedCount > 0 && !hover {
                Text("\(tab.blockedCount)")
                    .font(.caption2.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.green)
            }
            if hover || selected {
                Button { browser.close(tab) } label: {
                    Image(systemName: "xmark").font(.caption2.weight(.bold)).frame(width: 16, height: 16)
                }
                .buttonStyle(.borderless)
                .help("Закрыть вкладку (⌘W)")
            }
        }
        .padding(.horizontal, 10)
        .frame(height: 28)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(selected ? AnyShapeStyle(.background) : AnyShapeStyle(hover ? Color.primary.opacity(0.06) : .clear))
                .shadow(color: .black.opacity(selected ? 0.12 : 0), radius: 2, y: 1)
        }
        .overlay(alignment: .bottom) {
            if tab.inCoffin, let jar = tab.jar {
                // Тонкая чёрная полоска: вкладка открыта «в гробу» — изоляторе или банке корпорации.
                Capsule().fill(Color.black).frame(height: 3).padding(.horizontal, 8)
                    .help(tab.inIsolator ? "Изолятор: сайт заперт отдельно от всего" : "Банка «\(Jars.title(of: jar))»: корпорация заперта отдельно от других")
            } else if let c = tab.container {
                Capsule().fill(ContainerPalette.color(c.color)).frame(height: 3).padding(.horizontal, 8)
                    .help("Контейнер «\(c.name)»")
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { browser.select(tab) }
        // Средняя кнопка мыши закрывает вкладку, как во всех браузерах.
        .overlay(MiddleClick { browser.close(tab) })
        .onHover { hover = $0 }
        .contextMenu {
            Button("Обновить") { tab.reload() }
            Button("Закрыть") { browser.close(tab) }
            Button("Закрыть другие вкладки") { browser.closeOthers(tab) }.disabled(browser.tabs.count < 2)
            if !browser.isPrivate {
                Divider()
                Menu("Открыть в контейнере") {
                    Button("Обычная вкладка") { browser.reopen(tab, container: nil) }
                    ForEach(AppModel.shared.containers) { c in
                        Button(c.name) { browser.reopen(tab, container: c) }
                    }
                }
                Button("Открыть в одноразовой вкладке") { browser.reopen(tab, container: nil, ephemeral: true) }
                if !tab.inIsolator, !tab.site.isEmpty {
                    Button("Открыть в изоляторе") { browser.isolate(tab) }
                }
            }
        }
    }
}

/// Значок сайта без запроса к сайту: первая буква домена на цветном фоне (цвет зависит от домена).
struct SiteTile: View {
    let host: String
    var size: CGFloat = 16

    var body: some View {
        let site = Hosts.site(host)
        let letter = site.first.map { String($0).uppercased() } ?? "•"
        RoundedRectangle(cornerRadius: size * 0.28)
            .fill(color(site).gradient)
            .frame(width: size, height: size)
            .overlay(Text(letter).font(.system(size: size * 0.58, weight: .bold, design: .rounded)).foregroundStyle(.white))
    }

    private func color(_ site: String) -> Color {
        guard !site.isEmpty else { return .gray }
        var h: UInt32 = 2166136261
        for b in site.utf8 { h = (h ^ UInt32(b)) &* 16777619 }
        return Color(hue: Double(h % 360) / 360, saturation: 0.55, brightness: 0.78)
    }
}

/// Ловит только щелчок средней кнопкой мыши; обычные щелчки проходят сквозь.
struct MiddleClick: NSViewRepresentable {
    let action: () -> Void

    final class Catcher: NSView {
        var action: (() -> Void)?

        override func hitTest(_ point: NSPoint) -> NSView? {
            let type = NSApp.currentEvent?.type
            return type == .otherMouseDown || type == .otherMouseUp ? super.hitTest(point) : nil
        }

        override func otherMouseUp(with event: NSEvent) {
            if event.buttonNumber == 2 { action?() }
        }
    }

    func makeNSView(context: Context) -> Catcher {
        let view = Catcher()
        view.action = action
        return view
    }

    func updateNSView(_ view: Catcher, context: Context) {
        view.action = action
    }
}
