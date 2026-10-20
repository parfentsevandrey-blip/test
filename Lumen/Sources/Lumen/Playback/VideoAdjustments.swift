import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation
import os

/// Настройки изображения, применяемые к кадрам в реальном времени через Core Image.
nonisolated struct VideoAdjustments: Equatable, Sendable, Codable {
    var exposure: Double = 0       // −1…1 EV
    var brightness: Double = 0     // −0.3…0.3
    var contrast: Double = 1       // 0.6…1.6
    var saturation: Double = 1     // 0…2
    var vibrance: Double = 0       // −1…1
    var warmth: Double = 0         // −1…1
    var sharpness: Double = 0      // 0…1.5
    var vignette: Double = 0       // 0…1.5

    var isIdentity: Bool { self == VideoAdjustments() }

    struct Preset: Identifiable, Sendable {
        let id: String
        let name: String
        let symbol: String
        let value: VideoAdjustments
    }

    static let presets: [Preset] = [
        Preset(id: "original", name: "Оригинал", symbol: "circle.slash", value: VideoAdjustments()),
        Preset(id: "vivid", name: "Ярко", symbol: "sparkles",
               value: VideoAdjustments(contrast: 1.08, saturation: 1.15, vibrance: 0.35, sharpness: 0.3)),
        Preset(id: "cinema", name: "Кино", symbol: "film",
               value: VideoAdjustments(brightness: -0.02, contrast: 1.12, saturation: 0.92, warmth: 0.25, vignette: 0.6)),
        Preset(id: "warm", name: "Тепло", symbol: "sun.max",
               value: VideoAdjustments(saturation: 1.05, warmth: 0.55)),
        Preset(id: "cool", name: "Холод", symbol: "snowflake",
               value: VideoAdjustments(contrast: 1.04, warmth: -0.5)),
        Preset(id: "noir", name: "Нуар", symbol: "moon.stars",
               value: VideoAdjustments(contrast: 1.3, saturation: 0, vignette: 0.9)),
        Preset(id: "night", name: "Темнота", symbol: "eye",
               value: VideoAdjustments(exposure: 0.45, brightness: 0.04, contrast: 0.92, vibrance: 0.2)),
    ]
}

/// Потокобезопасный контейнер: композиция видео читает значения на своём потоке
/// для каждого кадра, поэтому ползунки меняют картинку без пересборки композиции.
nonisolated final class AdjustmentBox: Sendable {
    private let state = OSAllocatedUnfairLock(initialState: VideoAdjustments())

    var value: VideoAdjustments {
        get { state.withLock { $0 } }
        set { state.withLock { $0 = newValue } }
    }

    func apply(to source: CIImage) -> CIImage {
        let a = value
        var image = source
        if a.exposure != 0 {
            let f = CIFilter.exposureAdjust()
            f.inputImage = image
            f.ev = Float(a.exposure)
            image = f.outputImage ?? image
        }
        if a.warmth != 0 {
            let f = CIFilter.temperatureAndTint()
            f.inputImage = image
            f.neutral = CIVector(x: 6500, y: 0)
            f.targetNeutral = CIVector(x: 6500 - a.warmth * 2200, y: 0)
            image = f.outputImage ?? image
        }
        if a.brightness != 0 || a.contrast != 1 || a.saturation != 1 {
            let f = CIFilter.colorControls()
            f.inputImage = image
            f.brightness = Float(a.brightness)
            f.contrast = Float(a.contrast)
            f.saturation = Float(a.saturation)
            image = f.outputImage ?? image
        }
        if a.vibrance != 0 {
            let f = CIFilter.vibrance()
            f.inputImage = image
            f.amount = Float(a.vibrance)
            image = f.outputImage ?? image
        }
        if a.sharpness > 0 {
            let f = CIFilter.sharpenLuminance()
            f.inputImage = image
            f.sharpness = Float(a.sharpness)
            f.radius = 1.6
            image = f.outputImage ?? image
        }
        if a.vignette > 0 {
            let f = CIFilter.vignette()
            f.inputImage = image
            f.intensity = Float(a.vignette)
            f.radius = 1.8
            image = f.outputImage ?? image
        }
        return image
    }
}
