import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Изображения для зрения модели: декодирование через ImageIO (JPEG, PNG, HEIC, WebP, TIFF, GIF…),
/// поворот по EXIF, уменьшение до разумного размера и пиксели RGB — в том виде, который ждёт проектор.
nonisolated enum ImageInput {
    struct RGB {
        var width: Int
        var height: Int
        var pixels: [UInt8]
    }

    /// Проекторы сами приводят картинку к своему разрешению; больше 1536 точек по стороне — лишняя работа и память.
    static func rgb(path: String, maxSide: Int = 1536) -> RGB? {
        let url = URL(fileURLWithPath: path) as CFURL
        guard let source = CGImageSourceCreateWithURL(url, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxSide,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return rgb(image)
    }

    static func rgb(_ image: CGImage) -> RGB? {
        let w = image.width, h = image.height
        guard w > 0, h > 0 else { return nil }
        var rgba = [UInt8](repeating: 255, count: w * h * 4)
        let drawn = rgba.withUnsafeMutableBytes { buf -> Bool in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            // Прозрачный фон — белый, как его видит человек.
            ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
            ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return nil }
        var rgb = [UInt8](repeating: 0, count: w * h * 3)
        for i in 0..<(w * h) {
            rgb[i * 3] = rgba[i * 4]
            rgb[i * 3 + 1] = rgba[i * 4 + 1]
            rgb[i * 3 + 2] = rgba[i * 4 + 2]
        }
        return RGB(width: w, height: h, pixels: rgb)
    }

    /// Сохраняет вложение в папку приложения (JPEG, не больше 2048 точек) — переписка хранит копию,
    /// даже если исходный файл удалят. Возвращает имя файла.
    static func storeAttachment(from url: URL) -> String? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 2048,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return store(image)
    }

    static func storeAttachment(data: Data) -> String? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: 2048,
              ] as CFDictionary) else { return nil }
        return store(image)
    }

    static func store(_ image: CGImage) -> String? {
        let name = UUID().uuidString + ".jpg"
        let url = AppPaths.attachments.appendingPathComponent(name)
        guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
        return CGImageDestinationFinalize(dest) ? name : nil
    }

    // MARK: - Буфер обмена

    private static let imageTypes: [NSPasteboard.PasteboardType] = [.png, .tiff, .init("public.jpeg"), .init("public.heic"), .init("org.webmproject.webp")]
    private static let fileOptions: [NSPasteboard.ReadingOptionKey: Any] = [
        .urlReadingFileURLsOnly: true,
        .urlReadingContentsConformToTypes: [UTType.image.identifier],
    ]

    /// В буфере картинка, а не текст: скопированный файл-изображение, снимок экрана, «Скопировать изображение»
    /// в браузере. Если вместе с картинкой есть текст (так копируют, например, из Word), это текст — его
    /// вставит обычное поле ввода.
    static func pasteboardHasImage(_ pb: NSPasteboard = .general) -> Bool {
        if pb.canReadObject(forClasses: [NSURL.self], options: fileOptions) { return true }
        return pb.availableType(from: imageTypes) != nil && pb.availableType(from: [.string]) == nil
    }

    /// Сохраняет картинки из буфера как вложения. Возвращает имена файлов.
    static func storeFromPasteboard(_ pb: NSPasteboard = .general) -> [String] {
        if let urls = pb.readObjects(forClasses: [NSURL.self], options: fileOptions) as? [URL], !urls.isEmpty {
            return urls.compactMap { storeAttachment(from: $0) }
        }
        for type in imageTypes {
            if let data = pb.data(forType: type), let name = storeAttachment(data: data) { return [name] }
        }
        return []
    }

    static func path(_ name: String) -> String { AppPaths.attachments.appendingPathComponent(name).path }
}
