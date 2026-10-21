import Foundation

/// Квантование — насколько сжаты веса модели. От него зависят размер файла, скорость и качество.
nonisolated struct Quant: Sendable, Hashable, Codable, Comparable {
    /// Как в имени файла: «Q4_K_M», «UD-Q4_K_XL», «MXFP4_MOE».
    let name: String
    /// Примерное число бит на вес.
    let bits: Double
    /// Доля качества исходной модели (по замерам перплексии сообщества, округлённо).
    let quality: Double

    static func < (a: Quant, b: Quant) -> Bool { a.bits < b.bits }

    /// Без префикса Unsloth Dynamic.
    var base: String { name.uppercased().replacingOccurrences(of: "UD-", with: "") }
    var isDynamic: Bool { name.uppercased().hasPrefix("UD-") }
    var isOriginal: Bool { bits >= 15.9 }

    var label: String {
        switch quality {
        case 0.999...: isOriginal ? "Оригинал без сжатия" : "Почти без потерь"
        case 0.99..<0.999: "Отличное качество"
        case 0.97..<0.99: "Оптимальный баланс"
        case 0.95..<0.97: "Компактная"
        case 0.9..<0.95: "Сильно сжатая"
        default: "Экстремальное сжатие"
        }
    }

    /// Bits per weight и качество для известных схем llama.cpp.
    private static let table: [String: (Double, Double)] = [
        "F32": (32, 1), "F16": (16, 1), "BF16": (16, 1),
        "Q8_0": (8.5, 0.999), "Q8_K_XL": (9.0, 0.999),
        "Q6_K": (6.56, 0.995), "Q6_K_XL": (7.0, 0.996),
        "Q5_K_XL": (6.0, 0.992), "Q5_K_M": (5.69, 0.99), "Q5_K_S": (5.54, 0.988), "Q5_1": (6.0, 0.987), "Q5_0": (5.5, 0.985),
        "Q4_K_XL": (5.0, 0.978), "Q4_K_M": (4.89, 0.975), "Q4_K_S": (4.58, 0.97), "Q4_1": (5.0, 0.968), "Q4_0": (4.55, 0.96),
        "MXFP4_MOE": (4.4, 0.972), "MXFP4": (4.25, 0.97),
        "IQ4_NL": (4.5, 0.968), "IQ4_XS": (4.25, 0.965),
        "Q3_K_XL": (4.1, 0.94), "Q3_K_L": (4.27, 0.94), "Q3_K_M": (3.91, 0.93), "Q3_K_S": (3.5, 0.9),
        "IQ3_M": (3.66, 0.92), "IQ3_S": (3.44, 0.91), "IQ3_XS": (3.3, 0.9), "IQ3_XXS": (3.06, 0.88),
        "Q2_K_XL": (3.2, 0.86), "Q2_K_L": (3.1, 0.85), "Q2_K": (3.0, 0.84),
        "IQ2_M": (2.7, 0.8), "IQ2_S": (2.5, 0.77), "IQ2_XS": (2.31, 0.74), "IQ2_XXS": (2.06, 0.7),
        "IQ1_M": (1.75, 0.6), "IQ1_S": (1.56, 0.55), "TQ1_0": (1.69, 0.5), "TQ2_0": (2.06, 0.55),
    ]

    nonisolated(unsafe) private static let pattern = try! NSRegularExpression(
        pattern: #"(?:^|[-_.])((?:UD-)?(?:IQ\d_(?:XXS|XS|NL|S|M)|Q\d_K(?:_(?:XL|S|M|L))?|Q\d_[01]|MXFP4(?:_MOE)?|BF16|F16|F32|TQ\d_0))(?=[-_.]|$)"#,
        options: [.caseInsensitive])

    /// Квантование из имени файла: «Qwen3.5-9B-UD-Q4_K_XL.gguf» → UD-Q4_K_XL.
    static func parse(_ filename: String) -> Quant? {
        let name = (filename as NSString).lastPathComponent
        let ns = name as NSString
        let matches = pattern.matches(in: name, range: NSRange(location: 0, length: ns.length))
        guard let m = matches.last else { return nil }
        let raw = ns.substring(with: m.range(at: 1)).uppercased()
        return named(raw)
    }

    static func named(_ raw: String) -> Quant? {
        let key = raw.uppercased().replacingOccurrences(of: "UD-", with: "")
        guard let (bits, quality) = table[key] else { return nil }
        return Quant(name: raw.uppercased(), bits: bits, quality: quality)
    }

    /// Порядок предпочтения при выборе «как у всех»: Q4_K_M — стандарт Ollama и LM Studio.
    static let preferred = ["Q4_K_M", "UD-Q4_K_XL", "Q4_K_XL", "MXFP4_MOE", "MXFP4", "Q4_K_S", "IQ4_XS", "Q4_0", "IQ4_NL"]
    static let lighter = ["IQ4_XS", "Q3_K_L", "UD-Q3_K_XL", "Q3_K_XL", "Q3_K_M", "IQ3_M", "IQ3_XXS"]
    static let richer = ["Q8_0", "Q6_K", "UD-Q6_K_XL", "Q5_K_M", "UD-Q5_K_XL"]
}
