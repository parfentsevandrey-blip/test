import AVFoundation
import AppKit
import CoreImage
import ImageIO
import Observation
import UniformTypeIdentifiers

/// Обёртка над AVAssetImageGenerator, которую безопасно вызывать из любого контекста.
nonisolated final class FrameGrabber: @unchecked Sendable {
    private let generator: AVAssetImageGenerator

    init(url: URL, maxSize: CGSize, tolerance: Double) {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = maxSize
        let t = CMTime(seconds: tolerance, preferredTimescale: 600)
        generator.requestedTimeToleranceBefore = t
        generator.requestedTimeToleranceAfter = t
        self.generator = generator
    }

    @concurrent func image(at seconds: Double) async -> CGImage? {
        try? await generator.image(at: CMTime(seconds: max(0, seconds), preferredTimescale: 600)).image
    }
}

/// Превью кадра над шкалой времени. Кадры квантуются по секундам и кэшируются,
/// поэтому повторный проход по шкале мгновенный.
@Observable
final class ScrubPreviewer {
    private(set) var image: CGImage?
    private(set) var imageTime: Double = -1

    @ObservationIgnored private var grabber: FrameGrabber?
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var cache: [Int: CGImage] = [:]
    @ObservationIgnored private var step: Double = 1

    func reset(url: URL?, duration: Double) {
        task?.cancel()
        cache.removeAll()
        image = nil
        imageTime = -1
        step = duration > 3 * 3600 ? 4 : duration > 3600 ? 2 : 1
        grabber = url.map { FrameGrabber(url: $0, maxSize: CGSize(width: 480, height: 270), tolerance: step) }
    }

    func request(_ seconds: Double) {
        let key = Int((seconds / step).rounded())
        if let cached = cache[key] {
            image = cached
            imageTime = seconds
            return
        }
        task?.cancel()
        guard let grabber else { return }
        let time = Double(key) * step
        task = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(16))
            guard !Task.isCancelled, let frame = await grabber.image(at: time) else { return }
            guard let self, !Task.isCancelled else { return }
            if self.cache.count > 400 { self.cache.removeAll() }
            self.cache[key] = frame
            self.image = frame
            self.imageTime = seconds
        }
    }
}

nonisolated enum Thumbnailer {
    struct Result: Sendable {
        var image: CGImage?
        var duration: Double
    }

    /// Обложка для библиотеки: кадр на ~12% длительности, сохраняется JPEG-ом в кэш.
    @concurrent static func make(for url: URL, cacheFile: URL) async -> Result {
        let asset = AVURLAsset(url: url)
        let duration = (try? await asset.load(.duration).seconds) ?? 0
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 720, height: 405)
        let tolerance = CMTime(seconds: 3, preferredTimescale: 600)
        generator.requestedTimeToleranceBefore = tolerance
        generator.requestedTimeToleranceAfter = tolerance
        let at = duration.isFinite && duration > 0 ? min(duration * 0.12, 600) : 1
        guard let image = try? await generator.image(at: CMTime(seconds: at, preferredTimescale: 600)).image else {
            return Result(image: nil, duration: duration.isFinite ? duration : 0)
        }
        writeJPEG(image, to: cacheFile)
        return Result(image: image, duration: duration.isFinite ? duration : 0)
    }

    static func writeJPEG(_ image: CGImage, to url: URL, quality: Double = 0.82) {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return }
        CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        CGImageDestinationFinalize(dest)
    }

    static func writePNG(_ image: CGImage, to url: URL) -> Bool {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return false }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest)
    }

    /// Точный кадр в полном разрешении для снимка экрана, с применёнными настройками изображения.
    @concurrent static func exactFrame(url: URL, at time: CMTime, adjustments: AdjustmentBox?) async -> CGImage? {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        guard let image = try? await generator.image(at: time).image else { return nil }
        guard let adjustments, !adjustments.value.isIdentity else { return image }
        let ci = adjustments.apply(to: CIImage(cgImage: image).clampedToExtent()).cropped(to: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return CIContext().createCGImage(ci, from: ci.extent)
    }
}
