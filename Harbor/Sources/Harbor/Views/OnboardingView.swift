import AppKit
import SwiftUI

/// Знакомство при первом запуске: что такое Harbor, как он обновляет и что можно включить.
struct OnboardingView: View {
    @Environment(Store.self) private var store
    @State private var page = DemoUI.onboardingPage ?? 0
    @State private var launchAtLogin = true
    @State private var cleared: Int?

    private let pages = 4

    var body: some View {
        @Bindable var store = store
        VStack(spacing: 0) {
            Spacer(minLength: 20)
            ZStack {
                switch page {
                case 0: welcome
                case 1: updates
                case 2: gatekeeper
                default: finish(autoUpdate: $store.autoUpdate)
                }
            }
            .frame(maxWidth: 620)
            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                    removal: .move(edge: .leading).combined(with: .opacity)))
            .id(page)
            Spacer(minLength: 20)
            HStack {
                HStack(spacing: 7) {
                    ForEach(0..<pages, id: \.self) { i in
                        Capsule().fill(i == page ? Color.primary : Color.primary.opacity(0.2))
                            .frame(width: i == page ? 22 : 7, height: 7)
                    }
                }
                .animation(.snappy, value: page)
                Spacer()
                if page > 0 {
                    Button("Назад") { withAnimation(.smooth) { page -= 1 } }
                        .buttonStyle(.glass)
                }
                Button(page == pages - 1 ? "Начать" : "Далее") {
                    if page == pages - 1 {
                        if store.launchAtLogin != launchAtLogin { store.launchAtLogin = launchAtLogin }
                        store.onboarded = true
                        Task { await store.check() }
                    } else {
                        withAnimation(.smooth) { page += 1 }
                    }
                }
                .buttonStyle(.glassProminent)
                .keyboardShortcut(.defaultAction)
            }
            .frame(maxWidth: 620)
            .padding(.bottom, 36)
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Страницы

    private var welcome: some View {
        VStack(spacing: 22) {
            IconOrbit(apps: store.catalog?.apps.filter { $0.bundleID != Bundle.main.bundleIdentifier } ?? [])
                .frame(width: 320, height: 320)
            title("Все наши приложения — в одной гавани",
                  "Видеоплеер, браузер, VPN, торренты, нейросети, диктовка и уборка Mac. Свободные, без подписок, рекламы и слежки. Ставятся в один клик.")
        }
    }

    private var updates: some View {
        VStack(spacing: 22) {
            symbol("arrow.triangle.2.circlepath", tint: .teal)
            title("Обновления приходят сами",
                  "Harbor проверяет новые версии несколько раз в день. Незапущенная программа обновляется сразу, запущенная — как только вы её закроете. Новая версия уже скачана и проверена, замена занимает мгновение.")
            facts([
                ("externaldrive.badge.checkmark", "Данные и настройки на месте", "Они хранятся отдельно от программы, обновляется только она сама."),
                ("checkmark.shield", "Проверка каждой сборки", "Контрольная сумма SHA-256 и подпись — до установки."),
                ("arrow.uturn.backward", "Можно вернуть", "Предыдущая версия хранится, откат — одной кнопкой."),
            ])
        }
    }

    private var gatekeeper: some View {
        VStack(spacing: 22) {
            symbol("lock.open.display", tint: .orange)
            title("Без «Всё равно открыть»",
                  "Предупреждение о неизвестном разработчике macOS показывает для файлов с меткой «скачано из интернета». Harbor скачивает сам, проверяет и снимает метку — программы просто открываются.")
            VStack(alignment: .leading, spacing: 12) {
                Label {
                    Text("Чтобы Harbor мог заменять программы при обновлении, macOS один раз попросит разрешение «Управление приложениями». Включите Harbor в списке — больше спрашивать не будет.")
                        .font(.system(size: 13)).fixedSize(horizontal: false, vertical: true)
                } icon: {
                    Image(systemName: "hand.raised.fill").foregroundStyle(.orange)
                }
                HStack {
                    Button("Открыть «Управление приложениями»") { Store.openAppManagementSettings() }
                        .buttonStyle(.glass)
                    Button("Снять метки с уже установленных") { cleared = store.clearQuarantine() }
                        .buttonStyle(.glass)
                    if let cleared {
                        Text(cleared == 0 ? "Меток нет" : "Готово: \(cleared)")
                            .font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                }
            }
            .padding(18)
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private func finish(autoUpdate: Binding<Bool>) -> some View {
        VStack(spacing: 22) {
            symbol("sailboat.fill", tint: Color(red: 0.1, green: 0.52, blue: 0.86))
            title("Всё готово", "Harbor живёт в строке меню и следит за обновлениями, даже когда окно закрыто.")
            VStack(spacing: 0) {
                Toggle(isOn: autoUpdate) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Обновлять автоматически").font(.system(size: 14, weight: .semibold))
                        Text("Иначе Harbor покажет, что вышло, и обновит по кнопке").font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                }
                .padding(14)
                Divider()
                Toggle(isOn: $launchAtLogin) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Запускать при входе").font(.system(size: 14, weight: .semibold))
                        Text("Чтобы обновления приходили без вашего участия").font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                }
                .padding(14)
            }
            .toggleStyle(.switch)
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    // MARK: - Детали

    private func title(_ head: String, _ body: String) -> some View {
        VStack(spacing: 12) {
            Text(head).font(.system(size: 32, weight: .bold, design: .rounded)).multilineTextAlignment(.center)
            Text(body).font(.system(size: 15)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func symbol(_ name: String, tint: Color) -> some View {
        Image(systemName: name)
            .font(.system(size: 54, weight: .semibold))
            .foregroundStyle(tint.gradient)
            .frame(width: 120, height: 120)
            .glassEffect(.regular.tint(tint.opacity(0.18)), in: Circle())
    }

    private func facts(_ items: [(String, String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(items.indices, id: \.self) { i in
                let item = items[i]
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: item.0).font(.system(size: 18)).foregroundStyle(.teal).frame(width: 26)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.1).font(.system(size: 14, weight: .semibold))
                        Text(item.2).font(.system(size: 12.5)).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// Иконки приложений на медленно вращающейся орбите вокруг кораблика.
struct IconOrbit: View {
    let apps: [CatalogApp]
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate / 18
            GeometryReader { geo in
                let c = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
                let r = min(geo.size.width, geo.size.height) / 2 - 36
                ZStack {
                    Circle().stroke(.primary.opacity(0.08), lineWidth: 1).frame(width: r * 2, height: r * 2)
                    Image(systemName: "sailboat.fill")
                        .font(.system(size: 56, weight: .semibold))
                        .foregroundStyle(LinearGradient(colors: [.teal, .blue], startPoint: .top, endPoint: .bottom))
                        .frame(width: 118, height: 118)
                        .glassEffect(.regular, in: Circle())
                        .position(c)
                    ForEach(apps.indices, id: \.self) { i in
                        let a = t * 2 * .pi + Double(i) / Double(max(apps.count, 1)) * 2 * .pi
                        AppIconView(app: apps[i], size: 58)
                            .position(x: c.x + cos(a) * r, y: c.y + sin(a) * r * 0.92)
                    }
                }
            }
        }
    }
}
