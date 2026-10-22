import SwiftUI

/// Правая часть окна: подписки и серверы.
struct ServerList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Group {
            if model.servers.isEmpty && model.saved.subscriptions.isEmpty {
                EmptyState()
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14, pinnedViews: []) {
                        ForEach(model.saved.subscriptions) { sub in
                            SubscriptionSection(sub: sub)
                        }
                        if !model.manualServers.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Мои серверы").font(.system(size: 13, weight: .bold)).padding(.leading, 6)
                                ServerGroup(servers: model.manualServers)
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                }
                .scrollIndicators(.automatic)
            }
        }
        .searchable(text: $model.search, placement: .toolbar, prompt: "Поиск сервера")
    }
}

struct SubscriptionSection: View {
    @Environment(AppModel.self) private var model
    var sub: Subscription
    @State private var collapsed = false

    var body: some View {
        let servers = model.servers(of: sub)
        VStack(alignment: .leading, spacing: 8) {
            GlassCard(padding: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        Button { withAnimation(.smooth) { collapsed.toggle() } } label: {
                            Image(systemName: "chevron.right").rotationEffect(.degrees(collapsed ? 0 : 90))
                                .font(.system(size: 11, weight: .bold)).foregroundStyle(.secondary).frame(width: 14)
                        }
                        .buttonStyle(.plain)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(sub.displayTitle).font(.system(size: 14, weight: .bold)).lineLimit(1)
                            Text(subtitle(servers.count)).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        if !sub.info.supportURL.isEmpty, let url = URL(string: sub.info.supportURL) {
                            Link(destination: url) { Image(systemName: sub.info.supportURL.contains("t.me") ? "paperplane.fill" : "questionmark.bubble") }
                                .help("Поддержка провайдера")
                        }
                        if !sub.info.webPageURL.isEmpty, let url = URL(string: sub.info.webPageURL) {
                            Link(destination: url) { Image(systemName: "safari") }.help("Сайт провайдера")
                        }
                        Button { Task { await model.update(sub) } } label: {
                            if model.updating.contains(sub.id) { ProgressView().controlSize(.small) } else { Image(systemName: "arrow.clockwise") }
                        }
                        .buttonStyle(.borderless)
                        .help("Обновить подписку")
                        Menu {
                            Button("Проверить задержку") { Task { await model.pingAll(scope: .some(sub.id)) } }
                            Button("Скопировать ссылку подписки") { model.copy(sub.url) }
                            Divider()
                            Button("Удалить подписку", role: .destructive) { model.removeSubscription(sub) }
                        } label: { Image(systemName: "ellipsis.circle") }
                        .menuStyle(.borderlessButton)
                        .fixedSize()
                    }
                    if sub.info.total > 0 || sub.info.used > 0 {
                        VStack(alignment: .leading, spacing: 4) {
                            UsageBar(used: sub.info.used, total: sub.info.total)
                            Text(sub.info.total > 0 ? "\(Format.bytes(sub.info.used)) из \(Format.bytes(sub.info.total))" : "Израсходовано \(Format.bytes(sub.info.used)) · без лимита")
                                .font(.system(size: 10.5)).foregroundStyle(.secondary).monospacedDigit()
                        }
                    }
                    if !sub.info.announce.isEmpty {
                        Label(sub.info.announce, systemImage: "megaphone.fill")
                            .font(.system(size: 11.5)).foregroundStyle(.primary.opacity(0.85))
                            .padding(8).frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.accentColor.opacity(0.1)))
                    }
                    if let error = sub.error {
                        Label(error, systemImage: "exclamationmark.triangle.fill").font(.system(size: 11)).foregroundStyle(.orange)
                    }
                }
            }
            if !collapsed { ServerGroup(servers: servers) }
        }
    }

    private func subtitle(_ count: Int) -> String {
        var parts = ["\(count) \(Format.plural(count, "сервер", "сервера", "серверов"))"]
        if let expire = sub.info.expire { parts.append(Format.expiry(expire)) }
        if let updated = sub.updated { parts.append("обновлена \(Format.relative(updated))") }
        return parts.joined(separator: " · ")
    }
}

struct ServerGroup: View {
    @Environment(AppModel.self) private var model
    var servers: [Server]

    var body: some View {
        VStack(spacing: 2) {
            ForEach(servers) { server in
                ServerRow(server: server)
            }
        }
        .padding(4)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

struct ServerRow: View {
    @Environment(AppModel.self) private var model
    var server: Server
    @State private var hover = false

    var body: some View {
        let selected = model.saved.selectedID == server.id
        let active = selected && model.status.isOn
        Button {
            if selected && !model.status.isOn { model.toggle() } else { model.switchTo(server) }
        } label: {
            HStack(spacing: 12) {
                Text(server.flag.isEmpty ? "🌐" : server.flag).font(.system(size: 22)).frame(width: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text(server.title).font(.system(size: 13, weight: selected ? .semibold : .regular)).lineLimit(1)
                    HStack(spacing: 5) {
                        Tag(text: server.endpoint.summary, tint: .secondary)
                        if let note = server.note { Text(note).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1) }
                        if let issue = server.issue {
                            Text(issue).font(.system(size: 10.5)).foregroundStyle(.orange).lineLimit(1)
                        } else if server.warning != nil {
                            Image(systemName: "exclamationmark.shield").font(.system(size: 10)).foregroundStyle(.orange).help(server.warning ?? "")
                        }
                    }
                }
                Spacer(minLength: 6)
                PingBadge(result: model.ping(server.id), busy: model.pinging.contains(server.id))
                Image(systemName: active ? "checkmark.circle.fill" : selected ? "circle.inset.filled" : "circle")
                    .foregroundStyle(active ? model.status.tint : selected ? Color.accentColor : Color.secondary.opacity(0.4))
                    .font(.system(size: 15))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(selected ? model.status.tint.opacity(0.14) : hover ? Color.primary.opacity(0.05) : .clear))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hover = $0 }
        .opacity(server.issue == nil ? 1 : 0.6)
        .contextMenu {
            Button("Подключиться") { Task { await model.connect(server) } }
            Button("Проверить задержку") { model.pingOne(server) }
            Divider()
            Button("Поделиться (QR-код)") { model.shareServer = server }
            Button("Скопировать ссылку") { model.copy(Links.link(for: server)) }
            if server.subscriptionID == nil {
                Divider()
                Button("Удалить", role: .destructive) { model.remove(server) }
            }
        }
    }
}

/// Первый запуск: одна понятная кнопка.
struct EmptyState: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "link.badge.plus").font(.system(size: 54, weight: .light)).foregroundStyle(.secondary)
            VStack(spacing: 6) {
                Text("Добавьте подписку или сервер").font(.system(size: 20, weight: .bold))
                Text("Скопируйте ссылку от провайдера — https://…, vless://, vmess://, trojan://, ss://, hy2:// или happ://add/… — и нажмите кнопку. Можно и QR-кодом.")
                    .font(.system(size: 12.5)).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 380)
            }
            HStack(spacing: 10) {
                Button {
                    Task {
                        let message = await model.importText(NSPasteboard.general.string(forType: .string) ?? "")
                        model.flash(message)
                    }
                } label: { Label("Вставить из буфера", systemImage: "doc.on.clipboard").padding(.horizontal, 6) }
                    .buttonStyle(.glassProminent).controlSize(.large)
                Button { model.showAdd = true } label: { Label("Другие способы", systemImage: "qrcode.viewfinder") }
                    .buttonStyle(.glass).controlSize(.large)
            }
            Text("Veil не продаёт серверы — нужна подписка от вашего провайдера, как и в Happ.")
                .font(.system(size: 11)).foregroundStyle(.tertiary)
        }
        .padding(30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
