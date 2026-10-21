import AppKit
import SwiftUI

/// Живая иконка в Dock: поверх иконки приложения — текущая скорость загрузки и общий прогресс.
/// Картинка рендерится через ImageRenderer только когда меняются цифры, так что Dock не дёргается зря.
final class DockTileController {
    private let imageView = NSImageView()
    private var shown = false
    private var lastKey = ""

    func update(down: Double, progress: Double?, active: Int) {
        let tile = NSApp.dockTile
        guard active > 0 else {
            if shown {
                tile.contentView = nil
                tile.display()
                shown = false
                lastKey = ""
            }
            return
        }
        let speed = DockSpeed(down)
        let key = "\(speed.value)\(speed.unit)|\(Int((progress ?? 0) * 100))|\(active)"
        guard key != lastKey else { return }
        lastKey = key

        let renderer = ImageRenderer(content: DockTileView(icon: NSApp.applicationIconImage, speed: speed,
                                                           progress: progress, active: active))
        renderer.scale = 2
        guard let image = renderer.nsImage else { return }
        imageView.image = image
        imageView.imageScaling = .scaleProportionallyUpOrDown
        imageView.frame = NSRect(origin: .zero, size: tile.size)
        if !shown {
            tile.contentView = imageView
            shown = true
        }
        tile.display()
    }
}

/// Скорость, разбитая на крупное число и единицу — в Dock иконка маленькая, нужны крупные цифры.
struct DockSpeed {
    let value: String
    let unit: String

    init(_ bps: Double) {
        switch bps {
        case ..<1000:
            value = "0"; unit = "КБ/с"
        case ..<1_000_000:
            value = String(format: "%.0f", bps / 1000); unit = "КБ/с"
        case ..<100_000_000:
            value = String(format: "%.1f", bps / 1_000_000); unit = "МБ/с"
        default:
            value = String(format: "%.0f", bps / 1_000_000); unit = "МБ/с"
        }
    }
}

private struct DockTileView: View {
    let icon: NSImage
    let speed: DockSpeed
    let progress: Double?
    let active: Int

    var body: some View {
        ZStack(alignment: .bottom) {
            Image(nsImage: icon)
                .resizable()
                .frame(width: 128, height: 128)

            // Капсула «под стекло»: ImageRenderer не рисует настоящий glassEffect, поэтому собираем её сами.
            VStack(spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Image(systemName: "arrow.down")
                        .font(.system(size: 17, weight: .heavy))
                    Text(speed.value)
                        .font(.system(size: 30, weight: .heavy, design: .rounded))
                        .minimumScaleFactor(0.6)
                    Text(speed.unit)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .opacity(0.85)
                }
                .lineLimit(1)
                if let progress {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.25))
                            Capsule()
                                .fill(LinearGradient(colors: [Color(red: 0.35, green: 0.95, blue: 0.85), .white],
                                                     startPoint: .leading, endPoint: .trailing))
                                .frame(width: max(5, geo.size.width * progress))
                        }
                    }
                    .frame(height: 5)
                }
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.top, 5)
            .padding(.bottom, progress == nil ? 6 : 8)
            .frame(width: 118)
            .background {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(LinearGradient(colors: [Color(red: 0.12, green: 0.36, blue: 0.95), Color(red: 0.32, green: 0.18, blue: 0.78)],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .overlay {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(LinearGradient(colors: [.white.opacity(0.35), .white.opacity(0)], startPoint: .top, endPoint: .center))
                    }
                    .overlay {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(LinearGradient(colors: [.white.opacity(0.8), .white.opacity(0.15)], startPoint: .top, endPoint: .bottom),
                                          lineWidth: 1.2)
                    }
                    .shadow(color: .black.opacity(0.35), radius: 4, y: 2)
            }
            .padding(.bottom, 4)

            if active > 1 {
                Text("\(active)")
                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(minWidth: 30, minHeight: 30)
                    .background(Circle().fill(Color.red).shadow(color: .black.opacity(0.3), radius: 2, y: 1))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
        }
        .frame(width: 128, height: 128)
    }
}
