import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @State private var dropTargeted = false

    var body: some View {
        ZStack {
            if model.engine.hasMedia {
                PlayerView()
                    .transition(.opacity)
            } else {
                EmptyStage()
                    .transition(.opacity)
            }
            if dropTargeted {
                DropOverlay()
                    .transition(.opacity)
            }
        }
        .frame(minWidth: 640, minHeight: 400)
        .background(Color.black)
        .background(WindowAccessor { model.attach(window: $0) })
        .preferredColorScheme(.dark)
        .tint(Color(red: 0.45, green: 0.62, blue: 1))
        .animation(.smooth(duration: 0.4), value: model.engine.hasMedia)
        .dropDestination(for: URL.self) { urls, _ in
            model.open(urls)
            return true
        } isTargeted: { targeted in
            withAnimation(.smooth(duration: 0.25)) { dropTargeted = targeted }
        }
    }
}

/// Пустое окно: бросьте файл — и смотрите. Ничего больше.
struct EmptyStage: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "play.rectangle.on.rectangle")
                .font(.system(size: 46, weight: .light))
                .foregroundStyle(.secondary)
            Text("Перетащите видео в окно")
                .font(.system(size: 18, weight: .semibold, design: .rounded))
            Button("Открыть…") { model.presentOpenPanel() }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .keyboardShortcut("o")
            Text("MKV, MP4, MOV, WebM и звук · ⌘O")
                .font(.system(size: 12))
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { model.presentOpenPanel() }
    }
}

/// Оверлей перетаскивания: стеклянная карточка с «бегущей» пунктирной рамкой.
struct DropOverlay: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
            VStack(spacing: 14) {
                Image(systemName: "arrow.down.circle.fill")
                    .font(.system(size: 50, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .symbolEffect(.bounce.up.byLayer, options: .repeat(.continuous))
                Text("Отпустите, чтобы смотреть")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
            }
            .padding(42)
            .glassEffect(.regular.tint(.accentColor.opacity(0.2)), in: .rect(cornerRadius: 34))
            .overlay {
                RoundedRectangle(cornerRadius: 34, style: .continuous)
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
