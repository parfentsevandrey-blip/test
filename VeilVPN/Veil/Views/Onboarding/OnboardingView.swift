import SwiftUI

/// Three glass pages shown on the first launch.
struct OnboardingView: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0

    private let pageCount = 3

    var body: some View {
        ZStack {
            AuroraBackground(state: page == 2 ? .connected : .disconnected, animated: true)
                .ignoresSafeArea()
            VStack(spacing: 24) {
                Group {
                    switch page {
                    case 0: welcomePage
                    case 1: routingPage
                    default: permissionsPage
                    }
                }
                .frame(maxWidth: 520)
                .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))
                .id(page)

                HStack(spacing: 8) {
                    ForEach(0..<pageCount, id: \.self) { index in
                        Capsule()
                            .fill(index == page ? Color.primary : Color.primary.opacity(0.25))
                            .frame(width: index == page ? 22 : 8, height: 8)
                    }
                }
                .animation(.smooth, value: page)

                HStack {
                    if page > 0 {
                        Button("Back") { withAnimation(.smooth) { page -= 1 } }
                            .buttonStyle(.glass)
                    }
                    Spacer()
                    if page < pageCount - 1 {
                        Button("Continue") { withAnimation(.smooth) { page += 1 } }
                            .buttonStyle(.glassProminent)
                            .keyboardShortcut(.defaultAction)
                    } else {
                        Button("Get started") {
                            app.completeOnboarding()
                            dismiss()
                        }
                        .buttonStyle(.glassProminent)
                        .keyboardShortcut(.defaultAction)
                    }
                }
                .frame(maxWidth: 520)
            }
            .padding(36)
        }
        .frame(width: 640, height: 560)
        .animation(.smooth(duration: 0.35), value: page)
    }

    private var welcomePage: some View {
        OnboardingCard(symbol: "snowflake", tint: .cyan, title: "Welcome to Veil") {
            Text("Veil routes your Mac through the Tor network using Snowflake bridges, so it works even where Tor is blocked. Press the power button and wait for the three-hop circuit to build — the first connection can take up to a minute.")
            Text("Everything that respects the system proxy (Safari, browsers, most apps) goes through Tor. Terminal tools need the snippet from Settings → Network.")
        }
    }

    private var routingPage: some View {
        OnboardingCard(symbol: "play.rectangle.fill", tint: .red, title: "YouTube and other sites") {
            Text("YouTube through Tor is slow and asks for sign-ins. In Settings → YouTube you can send it directly with anti-throttling instead: the TLS handshake is fragmented so DPI throttling cannot recognise it. YouTube Turbo does the same without Tor at all.")
            Text("Direct modes are fast, but the site sees your real IP address. Everything else still goes through Tor.")
        }
    }

    private var permissionsPage: some View {
        OnboardingCard(symbol: "lock.shield.fill", tint: .mint, title: "One password, once") {
            Text("To switch the system proxy macOS asks for an administrator password the first time. Veil keeps that authorization for the session, so disconnecting does not ask again.")
            Text("The kill switch keeps proxied apps blocked whenever Tor is down, and Veil reconnects automatically when the network comes back.")
        }
    }
}

private struct OnboardingCard<Content: View>: View {
    let symbol: String
    let tint: Color
    let title: LocalizedStringKey
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Image(systemName: symbol)
                .font(.system(size: 44, weight: .medium))
                .foregroundStyle(tint)
                .symbolEffect(.bounce, value: title)
            Text(title)
                .font(.system(size: 28, weight: .bold, design: .rounded))
            VStack(alignment: .leading, spacing: 10) {
                content
            }
            .font(.callout)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(28)
        .glassEffect(.regular, in: .rect(cornerRadius: 26))
    }
}
