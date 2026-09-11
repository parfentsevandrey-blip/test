import SwiftUI

/// Mesh gradient that gives the Liquid Glass surfaces something to refract. The colour is keyed to
/// connection state, which is a real signal; the drift is not, so it is off wherever the screen is
/// read rather than admired.
struct AuroraBackground: View {
    let state: ConnectionState
    var animated = false

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if animated, !reduceMotion {
                TimelineView(.animation(minimumInterval: 1.0 / 24.0, paused: reduceMotion)) { context in
                    MeshGradient(width: 3, height: 3,
                                 points: Self.points(at: context.date.timeIntervalSinceReferenceDate),
                                 colors: palette)
                }
            } else {
                MeshGradient(width: 3, height: 3, points: Self.points(at: 0), colors: palette)
            }
        }
        .overlay {
            RadialGradient(
                colors: [.clear, .black.opacity(colorScheme == .dark ? 0.35 : 0.08)],
                center: .center,
                startRadius: 200,
                endRadius: 900
            )
        }
        .animation(.easeInOut(duration: 1.6), value: state)
        .animation(.easeInOut(duration: 0.6), value: colorScheme)
    }

    private var palette: [Color] {
        switch (state, colorScheme) {
        case (.connected, .dark):
            return Self.colors([0x052A2A, 0x0B4A4A, 0x0A2E4A, 0x0E5A45, 0x0B3D5E, 0x0F6A5A, 0x06303A, 0x0D4F5E, 0x072A40])
        case (.connected, _):
            return Self.colors([0xC7F5EA, 0xA8EDDC, 0xC7E6FF, 0xB3F0E0, 0xB8E4FF, 0x9FE8D8, 0xD0F5EE, 0xB5EEE6, 0xC2E8FF])
        case (.connecting, .dark), (.disconnecting, .dark):
            return Self.colors([0x2A1B0F, 0x4A2A0B, 0x3A1F4A, 0x5A3A0B, 0x3E2A5B, 0x6B3F0B, 0x2C1A3D, 0x4B2E12, 0x1E1236])
        case (.connecting, _), (.disconnecting, _):
            return Self.colors([0xFFE7C7, 0xFFD8A8, 0xF3D6FF, 0xFFDFB3, 0xE9D3FF, 0xFFD1A1, 0xF7E0FF, 0xFFE1BF, 0xEEDCFF])
        case (.failed, .dark):
            return Self.colors([0x2A0F14, 0x4A1220, 0x2E1030, 0x5A1A24, 0x3B1436, 0x6B1F2B, 0x2C1122, 0x4B1830, 0x1E0B1A])
        case (.failed, _):
            return Self.colors([0xFFD9DD, 0xFFC5CC, 0xF3D0FF, 0xFFC9D2, 0xEBCBFF, 0xFFBFC8, 0xF9D6F5, 0xFFD3DA, 0xF0D0FF])
        case (.disconnected, .dark):
            return Self.colors([0x0B0F2A, 0x1B1E4B, 0x0D2B4A, 0x2A1B5E, 0x16213E, 0x0B3A5B, 0x1A0F3C, 0x221A5C, 0x071A2E])
        case (.disconnected, _):
            return Self.colors([0xDCE3FF, 0xC9D2FF, 0xE3D9FF, 0xC7DBFF, 0xD9CCFF, 0xBFD4FF, 0xE6E0FF, 0xCFD9FF, 0xD5E4FF])
        }
    }

    private static func colors(_ values: [UInt32]) -> [Color] {
        values.map { Color(hex: $0) }
    }

    private static func points(at t: Double) -> [SIMD2<Float>] {
        func wobble(_ a: Double, _ b: Double, speed: Double, amplitude: Float) -> Float {
            Float(sin(t * speed + a) * cos(t * speed * 0.7 + b)) * amplitude
        }
        return [
            [0, 0], [0.5 + wobble(1, 2, speed: 0.35, amplitude: 0.12), 0], [1, 0],
            [0, 0.5 + wobble(3, 1, speed: 0.30, amplitude: 0.12)],
            [0.5 + wobble(5, 4, speed: 0.25, amplitude: 0.22), 0.5 + wobble(2, 6, speed: 0.30, amplitude: 0.22)],
            [1, 0.5 + wobble(4, 3, speed: 0.28, amplitude: 0.12)],
            [0, 1], [0.5 + wobble(6, 1, speed: 0.33, amplitude: 0.12), 1], [1, 1],
        ]
    }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
