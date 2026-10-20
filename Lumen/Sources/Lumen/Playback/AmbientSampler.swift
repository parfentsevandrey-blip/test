import AVFoundation
import Observation
import SwiftUI
import simd

/// Цвета кадра в виде сетки 3×3 — из них строится живое свечение вокруг видео
/// и подкрашивается стекло интерфейса.
nonisolated struct AmbientPalette: Equatable, Sendable {
    var colors: [SIMD3<Float>]

    static let neutral = AmbientPalette(colors: Array(repeating: SIMD3(0.16, 0.18, 0.28), count: 9))

    var swiftUIColors: [Color] { colors.map { Color(red: Double($0.x), green: Double($0.y), blue: Double($0.z)) } }

    /// Средний цвет, приглушённый до оттенка для стекла.
    var accent: Color {
        let avg = colors.reduce(SIMD3<Float>(repeating: 0), +) / Float(max(colors.count, 1))
        return Color(red: Double(avg.x), green: Double(avg.y), blue: Double(avg.z))
    }

    func distance(to other: AmbientPalette) -> Float {
        zip(colors, other.colors).map { simd_length($0 - $1) }.max() ?? 1
    }
}

/// Сэмплирует кадры через уменьшенный вывод AVPlayerItemVideoOutput.
/// Масштабирование делает видеоконвейер, поэтому на CPU разбирается картинка 64×36.
@Observable
final class AmbientSampler {
    private(set) var palette: AmbientPalette = .neutral

    @ObservationIgnored private var output: AVPlayerItemVideoOutput?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var forceNext = false

    func attach(to item: AVPlayerItem) {
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: 64,
            kCVPixelBufferHeightKey as String: 36,
        ])
        output.suppressesPlayerRendering = false
        item.add(output)
        self.output = output
        forceNext = true
    }

    func detach() {
        output = nil
        withAnimation(.smooth(duration: 1.2)) { palette = .neutral }
    }

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            while !Task.isCancelled {
                self?.sample()
                try? await Task.sleep(for: .milliseconds(110))
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }

    /// Пересчитать после перемотки на паузе.
    func refresh() {
        forceNext = true
        Task { [weak self] in
            for _ in 0..<6 {
                try? await Task.sleep(for: .milliseconds(80))
                self?.sample()
            }
        }
    }

    private func sample() {
        guard let output else { return }
        let time = output.itemTime(forHostTime: CACurrentMediaTime())
        guard forceNext || output.hasNewPixelBuffer(forItemTime: time),
              let buffer = output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil) else { return }
        forceNext = false
        guard let next = Self.analyze(buffer) else { return }
        guard next.distance(to: palette) > 0.025 else { return }
        withAnimation(.easeInOut(duration: 0.7)) { palette = next }
    }

    nonisolated static func analyze(_ buffer: CVPixelBuffer) -> AmbientPalette? {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return nil }
        let width = CVPixelBufferGetWidth(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        let stride = CVPixelBufferGetBytesPerRow(buffer)
        guard width > 2, height > 2 else { return nil }
        // Если масштабирование не сработало, всё равно читаем не больше ~64×36 точек.
        let stepX = max(1, width / 64)
        let stepY = max(1, height / 36)
        var sums = [SIMD3<Float>](repeating: .zero, count: 9)
        var counts = [Float](repeating: 0, count: 9)
        let pixels = base.assumingMemoryBound(to: UInt8.self)
        var y = 0
        while y < height {
            let gy = min(2, y * 3 / height)
            let row = pixels + y * stride
            var x = 0
            while x < width {
                let gx = min(2, x * 3 / width)
                let p = row + x * 4
                let cell = gy * 3 + gx
                sums[cell] += SIMD3(Float(p[2]), Float(p[1]), Float(p[0]))
                counts[cell] += 1
                x += stepX
            }
            y += stepY
        }
        var colors: [SIMD3<Float>] = []
        colors.reserveCapacity(9)
        for i in 0..<9 {
            var c = sums[i] / max(counts[i], 1) / 255
            // Чуть больше насыщенности и минимальная яркость, чтобы свечение не гасло в тёмных сценах.
            let luma = simd_dot(c, SIMD3(0.2126, 0.7152, 0.0722))
            c = simd_clamp(SIMD3(repeating: luma) + (c - SIMD3(repeating: luma)) * 1.45, .zero, SIMD3(repeating: 1))
            c = simd_max(c, SIMD3(repeating: 0.035))
            colors.append(c)
        }
        return AmbientPalette(colors: colors)
    }
}
