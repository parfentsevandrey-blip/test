import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import ScreenCaptureKit
import Vision

/// QR-коды: показать ссылку сервера и прочитать ссылку с картинки или с экрана.
nonisolated enum QR {
    static func image(_ text: String, size: CGFloat = 512) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scale = size / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let rep = NSCIImageRep(ciImage: scaled)
        let image = NSImage(size: rep.size)
        image.addRepresentation(rep)
        return image
    }

    /// Все QR-коды на картинке.
    static func read(_ cgImage: CGImage) -> [String] {
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        let handler = VNImageRequestHandler(cgImage: cgImage)
        try? handler.perform([request])
        return (request.results ?? []).compactMap(\.payloadStringValue)
    }

    static func read(_ image: NSImage) -> [String] {
        guard let cg = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return [] }
        return read(cg)
    }

    /// Снимок экрана и поиск QR-кодов (нужно разрешение «Запись экрана»).
    static func scanScreen() async -> [String] {
        guard let content = try? await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true) else { return [] }
        var found: [String] = []
        for display in content.displays {
            let filter = SCContentFilter(display: display, excludingWindows: [])
            let config = SCStreamConfiguration()
            config.width = display.width * 2
            config.height = display.height * 2
            if let image = try? await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config) {
                found += read(image)
            }
        }
        return found
    }
}
