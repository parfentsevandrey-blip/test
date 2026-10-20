import AppKit
import SwiftUI

/// Живое свечение: сетка цветов кадра, растянутая меш-градиентом и размытая.
struct AmbientGlow: View {
    let palette: AmbientPalette

    var body: some View {
        MeshGradient(
            width: 3, height: 3,
            points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [0.5, 0.5], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ],
            colors: palette.swiftUIColors
        )
        .scaleEffect(1.12)
        .blur(radius: 80)
        .saturation(1.25)
        .allowsHitTesting(false)
    }
}

struct SubtitleOverlay: View {
    let text: String?
    let lift: CGFloat

    @AppStorage(Prefs.subtitleScale) private var scale = 1.0
    @AppStorage(Prefs.subtitleGlass) private var glass = true

    var body: some View {
        GeometryReader { geo in
            let size = max(17, min(geo.size.height * 0.036, 44)) * scale
            VStack {
                Spacer()
                if let text {
                    Text(text)
                        .font(.system(size: size, weight: .semibold, design: .rounded))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(glass ? 0.35 : 0.9), radius: glass ? 2 : 3, y: 1)
                        .padding(.horizontal, glass ? 18 : 0)
                        .padding(.vertical, glass ? 9 : 0)
                        .glassEffect(glass ? .regular.tint(.black.opacity(0.28)) : .identity, in: .rect(cornerRadius: 16))
                        .id(text)
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 60)
            .padding(.bottom, lift)
        }
        .animation(.smooth(duration: 0.35), value: lift)
        .animation(.easeOut(duration: 0.12), value: text)
        .allowsHitTesting(false)
    }
}

/// Индикатор в стиле системного HUD громкости: иконка, подпись и шкала уровня.
struct HUDView: View {
    let message: HUDMessage

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: message.symbol)
                .font(.system(size: 34, weight: .semibold))
                .symbolRenderingMode(.hierarchical)
                .contentTransition(.symbolEffect(.replace))
                .symbolEffect(.bounce, value: message.id)
                .frame(height: 40)
            Text(message.title)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .lineLimit(1)
            if let level = message.level {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.18))
                        Capsule().fill(.white).frame(width: geo.size.width * level)
                    }
                }
                .frame(height: 5)
                .animation(.smooth(duration: 0.2), value: level)
            }
        }
        .padding(18)
        .frame(width: 168)
        .glassEffect(.regular, in: .rect(cornerRadius: 30))
    }
}

struct PreparingCard: View {
    let title: String
    let progress: Double?
    let message: String
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            ZStack {
                ProgressRing(progress: progress, lineWidth: 8)
                    .frame(width: 92, height: 92)
                if let progress {
                    Text("\(Int(progress * 100))%")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText(value: progress))
                        .animation(.smooth, value: Int(progress * 100))
                } else {
                    Image(systemName: "film")
                        .font(.system(size: 24, weight: .semibold))
                        .symbolEffect(.breathe)
                }
            }
            VStack(spacing: 6) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                Text(message)
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .contentTransition(.opacity)
            }
            if progress != nil {
                Text("Копия кэшируется: следующий запуск будет мгновенным")
                    .font(.system(size: 11.5))
                    .foregroundStyle(.tertiary)
            }
            Button("Отмена", action: onCancel)
                .buttonStyle(.glass)
                .controlSize(.large)
        }
        .padding(30)
        .frame(width: 380)
        .glassEffect(.regular, in: .rect(cornerRadius: 34))
    }
}

struct FailureCard: View {
    let message: String
    let onRetry: () -> Void
    let onLibrary: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundStyle(.yellow)
                .symbolEffect(.wiggle, options: .nonRepeating)
            Text("Не получилось открыть видео")
                .font(.system(size: 17, weight: .semibold))
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 10) {
                Button("Библиотека", action: onLibrary).buttonStyle(.glass)
                Button("Повторить", action: onRetry).buttonStyle(.glassProminent)
            }
            .controlSize(.large)
        }
        .padding(30)
        .frame(width: 400)
        .glassEffect(.regular, in: .rect(cornerRadius: 34))
    }
}

struct ResumeToast: View {
    let time: Double
    let onRestart: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.system(size: 15, weight: .semibold))
                .symbolEffect(.rotate, options: .nonRepeating)
            Text("Продолжаю с \(TimeFormat.string(time))")
                .font(.system(size: 13, weight: .semibold))
                .monospacedDigit()
            Button("С начала", action: onRestart)
                .buttonStyle(.glass)
                .controlSize(.small)
            Button(action: onDismiss) { Image(systemName: "xmark").font(.system(size: 10, weight: .bold)) }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
        }
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .padding(.vertical, 9)
        .glassEffect(.regular, in: .capsule)
    }
}

/// Снимок кадра «улетает» в угол, как скриншот в macOS.
struct CaptureToast: View {
    let capture: CapturedFrame

    var body: some View {
        Button {
            NSWorkspace.shared.activateFileViewerSelecting([capture.url])
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Image(nsImage: capture.image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                Label("Сохранено в «Изображения» и в буфер обмена", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .frame(width: 200, alignment: .leading)
            }
            .padding(10)
        }
        .buttonStyle(PressScaleStyle())
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 20))
    }
}

struct NotesBanner: View {
    let notes: [String]
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill").foregroundStyle(.yellow)
            VStack(alignment: .leading, spacing: 3) {
                ForEach(notes, id: \.self) { Text($0) }
            }
            .font(.system(size: 12, weight: .medium))
            Button(action: onDismiss) { Image(systemName: "xmark").font(.system(size: 10, weight: .bold)) }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(maxWidth: 520)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
    }
}

/// Для аудиофайлов: стеклянный диск с «дышащей» волной вместо пустого чёрного экрана.
struct AudioOnlyView: View {
    let title: String
    let isPlaying: Bool

    var body: some View {
        VStack(spacing: 22) {
            Image(systemName: "waveform")
                .font(.system(size: 64, weight: .semibold))
                .symbolEffect(.variableColor.iterative.dimInactiveLayers, isActive: isPlaying)
                .frame(width: 180, height: 180)
                .glassEffect(.regular.tint(.purple.opacity(0.25)), in: .circle)
            Text(title)
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }
}

/// Верхняя панель: назад в библиотеку, название с бейджами качества и боковые панели.
struct PlayerTopBar: View {
    @Environment(AppModel.self) private var model
    @Binding var panel: PlayerPanel?
    let tint: Color

    @Namespace private var glass

    private var engine: PlayerEngine { model.engine }

    var body: some View {
        HStack(spacing: 12) {
            Button {
                model.showLibrary()
            } label: {
                Label("Библиотека", systemImage: "chevron.backward")
                    .font(.system(size: 13, weight: .semibold))
            }
            .buttonStyle(.glass)
            .controlSize(.large)
            .help("Библиотека (⌘L)")

            HStack(spacing: 8) {
                Text(engine.displayTitle)
                    .font(.system(size: 13.5, weight: .semibold))
                    .lineLimit(1)
                if let resolution = engine.info.resolutionLabel { Badge(text: resolution) }
                if let hdr = engine.info.dynamicRange { Badge(text: hdr, glow: true) }
                if let codec = engine.info.videoCodec { Badge(text: codec, subtle: true) }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .glassEffect(.regular, in: .capsule)

            Spacer(minLength: 12)

            GlassEffectContainer(spacing: 8) {
                HStack(spacing: 8) {
                    if engine.info.hasVideo {
                        Button { engine.captureFrame() } label: { Image(systemName: "camera.viewfinder") }
                            .help("Сохранить кадр (⇧⌘S)")
                            .buttonStyle(.glass)
                    }
                    ForEach(PlayerPanel.allCases) { kind in
                        let button = Button {
                            withAnimation(.bouncy) { panel = panel == kind ? nil : kind }
                        } label: {
                            Image(systemName: kind.symbol)
                        }
                        .help(kind.title)
                        if panel == kind {
                            button.buttonStyle(.glassProminent)
                        } else {
                            button.buttonStyle(.glass)
                        }
                    }
                }
                .controlSize(.large)
                .buttonBorderShape(.circle)
            }
        }
    }
}

struct Badge: View {
    let text: String
    var glow = false
    var subtle = false

    var body: some View {
        Text(text)
            .font(.system(size: 10.5, weight: .heavy, design: .rounded))
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .foregroundStyle(glow ? AnyShapeStyle(LinearGradient(colors: [.yellow, .orange, .pink], startPoint: .leading, endPoint: .trailing)) : AnyShapeStyle(subtle ? .secondary : .primary))
            .background {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .strokeBorder(glow ? AnyShapeStyle(LinearGradient(colors: [.yellow, .pink], startPoint: .leading, endPoint: .trailing)) : AnyShapeStyle(.white.opacity(subtle ? 0.2 : 0.45)), lineWidth: 1.2)
            }
            .shadow(color: glow ? .orange.opacity(0.7) : .clear, radius: 6)
    }
}
