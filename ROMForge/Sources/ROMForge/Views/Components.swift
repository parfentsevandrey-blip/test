import SwiftUI

// MARK: - Liquid Glass с откатом на материалы для macOS 14–15

extension View {
    @ViewBuilder
    func glassCard(cornerRadius: CGFloat = 22, tint: Color? = nil) -> some View {
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            self.glassEffect(tint.map { Glass.regular.tint($0.opacity(0.25)) } ?? .regular,
                             in: .rect(cornerRadius: cornerRadius))
        } else {
            self.legacyCard(cornerRadius: cornerRadius, tint: tint)
        }
        #else
        self.legacyCard(cornerRadius: cornerRadius, tint: tint)
        #endif
    }

    private func legacyCard(cornerRadius: CGFloat, tint: Color?) -> some View {
        self
            .background {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.regularMaterial)
                    .overlay {
                        if let tint {
                            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(tint.opacity(0.08))
                        }
                    }
            }
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(.white.opacity(0.12), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.08), radius: 12, y: 4)
    }

    @ViewBuilder
    func prominentButton() -> some View {
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            self.buttonStyle(.glassProminent)
        } else {
            self.buttonStyle(.borderedProminent)
        }
        #else
        self.buttonStyle(.borderedProminent)
        #endif
    }

    @ViewBuilder
    func glassButton() -> some View {
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            self.buttonStyle(.glass)
        } else {
            self.buttonStyle(.bordered)
        }
        #else
        self.buttonStyle(.bordered)
        #endif
    }
}

// MARK: - Фон с мягким градиентом, на котором красиво «преломляется» стекло

struct AmbientBackground: View {
    var colors: [Color]

    var body: some View {
        ZStack {
            Color(nsColor: .windowBackgroundColor)
            if #available(macOS 15.0, *) {
                MeshGradient(
                    width: 3, height: 3,
                    points: [
                        [0, 0], [0.5, 0], [1, 0],
                        [0, 0.5], [0.6, 0.4], [1, 0.5],
                        [0, 1], [0.5, 1], [1, 1],
                    ],
                    colors: meshColors
                )
                .opacity(0.35)
            } else {
                LinearGradient(colors: colors.map { $0.opacity(0.3) } + [.clear],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
        .ignoresSafeArea()
    }

    private var meshColors: [Color] {
        let a = colors.first ?? .blue
        let b = colors.dropFirst().first ?? .purple
        return [a, a.opacity(0.5), .clear,
                b.opacity(0.6), .clear, a.opacity(0.3),
                .clear, b.opacity(0.4), .clear]
    }
}

// MARK: - Мелкие элементы

struct IconTile: View {
    var symbol: String
    var colors: [Color]
    var size: CGFloat = 52

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.27, style: .continuous)
            .fill(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: size, height: size)
            .overlay {
                Image(systemName: symbol)
                    .font(.system(size: size * 0.44, weight: .semibold))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.2), radius: 2, y: 1)
            }
            .overlay {
                RoundedRectangle(cornerRadius: size * 0.27, style: .continuous)
                    .strokeBorder(LinearGradient(colors: [.white.opacity(0.5), .clear],
                                                 startPoint: .top, endPoint: .bottom), lineWidth: 1)
            }
            .shadow(color: (colors.last ?? .black).opacity(0.35), radius: 8, y: 4)
    }
}

struct Badge: View {
    var text: String
    var symbol: String?
    var color: Color = .secondary

    var body: some View {
        HStack(spacing: 4) {
            if let symbol { Image(systemName: symbol) }
            Text(text)
        }
        .font(.caption.weight(.semibold))
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .foregroundStyle(color)
        .background(color.opacity(0.14), in: Capsule())
    }
}

struct SectionHeader: View {
    var title: String
    var symbol: String

    var body: some View {
        Label(title, systemImage: symbol)
            .font(.headline)
            .foregroundStyle(.secondary)
    }
}

extension ROMAvailability {
    @ViewBuilder
    var badge: some View {
        switch self {
        case .checking:
            HStack(spacing: 6) {
                ProgressView().controlSize(.mini)
                Text("Поиск…").font(.caption).foregroundStyle(.secondary)
            }
        case .available(let builds):
            Badge(text: builds.count == 1 ? "Доступна" : "Сборок: \(builds.count)", symbol: "checkmark.circle.fill", color: .green)
        case .notFound:
            Badge(text: "Нет для устройства", symbol: "minus.circle", color: .secondary)
        case .manual:
            Badge(text: "Проверить на сайте", symbol: "safari", color: .blue)
        case .failed:
            Badge(text: "Ошибка сети", symbol: "exclamationmark.triangle", color: .orange)
        }
    }
}
