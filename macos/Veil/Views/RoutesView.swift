import SwiftUI

/// One screen, one decision: which obfuscation to connect with.
///
/// There is no automatic mode and no ladder that falls through to something
/// else. That is a deliberate trade the Android build settled on: falling back
/// connects more often, but being told plainly that the chosen method did not
/// work is what lets someone pick a different one on purpose, instead of
/// watching the app quietly do something else and never learning which method
/// their network actually allows.
struct RoutesView: View {
    @EnvironmentObject private var tunnel: TunnelCoordinator

    private let columns = [GridItem(.adaptive(minimum: 240), spacing: Metrics.regular)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Metrics.regular) {
                Text("Подключение идёт только выбранным методом. Приложение не перебирает остальные за вашей спиной.")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 2)

                GlassGroup(spacing: Metrics.regular) {
                    LazyVGrid(columns: columns, spacing: Metrics.regular) {
                        ForEach(Transport.allCases.filter(\.isOffered), id: \.self) { transport in
                            TransportCard(
                                transport: transport,
                                selected: tunnel.settings.transport == transport
                            ) {
                                tunnel.settings.transport = transport
                            }
                        }
                    }
                }

                GlassPanel {
                    VStack(alignment: .leading, spacing: Metrics.tight) {
                        Text("Почему здесь нет прямого подключения и obfs4")
                            .font(.system(size: 12, weight: .semibold))
                        Text("На сети, против которой это измерялось, прямое соединение умирает в TLS-рукопожатии на 10%, а obfs4 доходит до моста и не строит цепочку. Показывать их как выбор — значит продать пользователю неудачное подключение ради того, что уже известно.")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}

private struct TransportCard: View {
    let transport: Transport
    let selected: Bool
    let choose: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: choose) {
            VStack(alignment: .leading, spacing: Metrics.snug) {
                HStack {
                    Image(systemName: transport.symbol)
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(selected ? Color.accentColor : .secondary)
                    Spacer()
                    if selected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.accentColor)
                    }
                }

                Text(transport.label)
                    .font(.system(size: 15, weight: .semibold))

                Text(transport.summary)
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer(minLength: 0)

                if selected {
                    Text("Используется при каждом подключении")
                        .font(.veilLabel)
                        .foregroundStyle(Color.accentColor)
                }
            }
            .padding(Metrics.panelPadding)
            .frame(height: 172, alignment: .topLeading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .veilGlass(
            shape: .rect(cornerRadius: Radius.panel),
            tint: selected ? .accentColor : nil,
            interactive: true
        )
        .scaleEffect(hovering ? 1.01 : 1)
        .animation(.smooth(duration: 0.18), value: hovering)
        .onHover { hovering = $0 }
    }
}
