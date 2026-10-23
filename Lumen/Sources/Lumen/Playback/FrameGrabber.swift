import AVFoundation
import CoreImage
import UniformTypeIdentifiers

/// Снимки кадров: точный кадр файла и запись PNG.
nonisolated enum Thumbnailer {
    static func writePNG(_ image: CGImage, to url: URL) -> Bool {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return false }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest)
    }

    /// Точный кадр в полном разрешении (для файлов, которые открывает сама macOS).
    @concurrent static func exactFrame(url: URL, at time: CMTime) async -> CGImage? {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        return try? await generator.image(at: time).image
    }
}
