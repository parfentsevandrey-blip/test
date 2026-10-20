import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @State private var dropTargeted = false

    var body: some View {
        ZStack {
            switch model.screen {
            case .library:
                LibraryView()
                    .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.985)),
                                            removal: .opacity.combined(with: .scale(scale: 1.015))))
            case .player:
                PlayerView()
                    .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 1.03)),
                                            removal: .opacity))
            }
            if dropTargeted {
                DropOverlay()
                    .transition(.opacity)
            }
        }
        .frame(minWidth: 760, minHeight: 480)
        .background(Color.black)
        .background(WindowAccessor { model.attach(window: $0) })
        .preferredColorScheme(.dark)
        .tint(Color(red: 0.45, green: 0.62, blue: 1))
        .dropDestination(for: URL.self) { urls, _ in
            model.open(urls)
            return true
        } isTargeted: { targeted in
            withAnimation(.smooth(duration: 0.25)) { dropTargeted = targeted }
        }
    }
}

/// Оверлей перетаскивания: стеклянная карточка с «бегущей» пунктирной рамкой.
struct DropOverlay: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
            VStack(spacing: 16) {
                Image(systemName: "arrow.down.circle.fill")
                    .font(.system(size: 54, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .symbolEffect(.bounce.up.byLayer, options: .repeat(.continuous))
                Text("Отпустите, чтобы смотреть")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                Text("Файлы и папки целиком — папка встанет в очередь")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            }
            .padding(46)
            .glassEffect(.regular.tint(.accentColor.opacity(0.2)), in: .rect(cornerRadius: 36))
            .overlay {
                RoundedRectangle(cornerRadius: 36, style: .continuous)
                    .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [10, 8], dashPhase: phase))
                    .foregroundStyle(.white.opacity(0.5))
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = -36 }
        }
    }
}
