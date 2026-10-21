import Foundation

/// Оценка: подойдёт ли вариант модели этому Mac и как быстро будет работать.
nonisolated struct FitReport: Sendable, Hashable {
    enum Level: Int, Sendable, Comparable {
        case great, good, ok, slow, tight, tooBig, noDisk

        static func < (a: Level, b: Level) -> Bool { a.rawValue < b.rawValue }
    }

    var level: Level
    /// Сколько памяти займёт: веса + контекст + служебное.
    var memory: Int64
    /// Оценка скорости генерации, токенов в секунду.
    var tokensPerSecond: Double
    var context: Int
    var reasons: [String]

    var runnable: Bool { level <= .tight }
    var comfortable: Bool { level <= .slow }

    var title: String {
        switch level {
        case .great: "Летает"
        case .good: "Быстро"
        case .ok: "Комфортно"
        case .slow: "Медленно"
        case .tight: "На пределе"
        case .tooBig: "Не поместится"
        case .noDisk: "Нет места на диске"
        }
    }

    var symbol: String {
        switch level {
        case .great: "bolt.fill"
        case .good: "hare.fill"
        case .ok: "checkmark.circle.fill"
        case .slow: "tortoise.fill"
        case .tight: "exclamationmark.triangle.fill"
        case .tooBig: "xmark.octagon.fill"
        case .noDisk: "externaldrive.fill.badge.xmark"
        }
    }

    /// Скорость простыми словами.
    var speedText: String {
        let t = tokensPerSecond
        if t <= 0 { return "—" }
        let words = Int((t * 0.75 * 60).rounded(.down) / 10) * 10 // ~0.75 слова на токен
        return "≈\(Int(t.rounded())) ток/с · ~\(words) слов в минуту"
    }
}

/// Советник: сколько памяти нужно, с какой скоростью пойдёт, что выбрать.
nonisolated enum Advisor {
    static let gb = 1_073_741_824.0

    /// Рабочие границы памяти.
    /// comfortable — модель работает, а Mac остаётся отзывчивым для других программ;
    /// hard — дальше macOS не отдаст память видеоядру и начнёт сбрасывать на диск.
    static func budget(_ mac: MacProfile) -> (comfortable: Int64, hard: Int64) {
        let memory = Double(mac.memory)
        let comfortable = min(Double(mac.gpuBudget) * 0.92, memory * 0.64)
        let hard = max(comfortable, min(memory - 2.5 * gb, Double(mac.gpuBudget) * 1.12))
        return (Int64(comfortable), Int64(hard))
    }

    /// Контекст, который откроем по умолчанию: длиннее — если памяти много.
    static func defaultContext(_ mac: MacProfile) -> Int {
        switch mac.memoryGB {
        case ..<12: 4096
        case ..<48: 8192
        case ..<96: 16384
        default: 32768
        }
    }

    /// Кэш внимания на один токен контекста (KV-кэш), приблизительно.
    static func kvPerToken(params: Double) -> Double {
        (64 + 8 * pow(max(params, 0.5), 0.8)) * 1024
    }

    static func estimate(size: Int64, params: Double, active: Double?, mac: MacProfile, context: Int? = nil) -> FitReport {
        let ctx = context ?? defaultContext(mac)
        let weights = Double(size)
        let kv = kvPerToken(params: params) * Double(ctx)
        let overhead = 0.35 * gb + weights * 0.04
        let need = weights + kv + overhead
        let (comfortable, hard) = budget(mac)

        // Каждый токен — чтение всех активных весов. У MoE читается только доля экспертов
        // (плюс общие слои — поэтому с запасом 25%).
        let activeFraction = active.map { min(1, $0 / max(params, 0.1) * 1.25) } ?? 1
        let perToken = weights * activeFraction / gb + 0.12
        var tps = mac.bandwidth * 0.62 / perToken
        if mac.virtualized { tps *= 0.25 }

        var reasons: [String] = []
        let level: FitReport.Level
        if Double(size) > Double(mac.freeDisk) - 3 * gb {
            level = .noDisk
            reasons.append("Нужно \(Format.bytes(size)) на диске, свободно \(Format.bytes(mac.freeDisk)).")
        } else if need > Double(hard) {
            level = .tooBig
            reasons.append("Модели нужно ≈\(Format.gb(need)) памяти, а этому Mac доступно не больше \(Format.gb(Double(hard))).")
            reasons.append("Она не запустится или будет работать с постоянной подкачкой с диска.")
        } else if need > Double(comfortable) {
            level = .tight
            tps *= 0.7
            reasons.append("Займёт ≈\(Format.gb(need)) из \(Format.gb(Double(mac.memory))) памяти — почти всё, что можно отдать видеоядру.")
            reasons.append("Перед запуском закройте тяжёлые программы, иначе Mac станет подтормаживать.")
        } else {
            switch tps {
            case 35...: level = .great
            case 16..<35: level = .good
            case 7..<16: level = .ok
            default: level = .slow
            }
            reasons.append("Займёт ≈\(Format.gb(need)) памяти из \(Format.gb(Double(mac.memory))) — запас для других программ останется.")
            if level == .slow { reasons.append("Ответ будет появляться медленнее, чем вы читаете.") }
        }
        if active != nil && level <= .tight {
            reasons.append("Смесь экспертов: на каждый токен работает лишь часть параметров, поэтому быстрее плотной модели того же размера.")
        }
        return FitReport(level: level, memory: Int64(need), tokensPerSecond: tps, context: ctx, reasons: reasons)
    }

    static func estimate(_ variant: ModelVariant, card: ModelCard, mac: MacProfile) -> FitReport {
        estimate(size: variant.size, params: card.params, active: card.activeParams, mac: mac,
                 context: card.contextLength.map { min($0, defaultContext(mac)) })
    }

    /// Лучший вариант модели для этого Mac.
    ///
    /// По умолчанию Q4_K_M — стандарт, на котором почти не теряется качество. Совсем маленьким моделям
    /// (их квантование портит сильнее) даём Q8_0, если остаётся быстро; если тесно — сжимаем сильнее, но не ниже Q3.
    static func pick(_ variants: [ModelVariant], card: ModelCard, mac: MacProfile, upgrade: Bool = true) -> (variant: ModelVariant, fit: FitReport)? {
        let usable = variants.filter { $0.quant.quality >= 0.9 && $0.quant.bits <= 9 }
        guard !usable.isEmpty else {
            return variants.min(by: { $0.size < $1.size }).map { (variant: $0, fit: estimate($0, card: card, mac: mac)) }
        }
        func find(_ names: [String]) -> ModelVariant? {
            for n in names {
                if let v = usable.first(where: { $0.quant.name.caseInsensitiveCompare(n) == .orderedSame }) { return v }
            }
            // «UD-Q4_K_M» у Unsloth — тот же Q4_K_M с улучшенной калибровкой.
            for n in names {
                if let v = usable.first(where: { $0.quant.base.caseInsensitiveCompare(n) == .orderedSame }) { return v }
            }
            return nil
        }
        let standard = find(Quant.preferred) ?? usable.min(by: { abs($0.quant.bits - 4.85) < abs($1.quant.bits - 4.85) })!
        let standardFit = estimate(standard, card: card, mac: mac)

        if standardFit.comfortable {
            if upgrade && card.params <= 2.5 {
                for name in ["Q8_0", "Q6_K"] {
                    if let v = find([name]) {
                        let fit = estimate(v, card: card, mac: mac)
                        if fit.comfortable && fit.tokensPerSecond >= max(20, standardFit.tokensPerSecond * 0.6) { return (variant: v, fit: fit) }
                    }
                }
            }
            return (variant: standard, fit: standardFit)
        }
        for name in Quant.lighter {
            if let v = find([name]) {
                let fit = estimate(v, card: card, mac: mac)
                if fit.comfortable { return (variant: v, fit: fit) }
            }
        }
        return (variant: standard, fit: standardFit)
    }

    // MARK: - Рекомендации

    enum Role: String, CaseIterable, Sendable {
        case balanced, fast, best, coder

        var title: String {
            switch self {
            case .balanced: "Сбалансированная"
            case .fast: "Самая быстрая"
            case .best: "Максимум ума"
            case .coder: "Для кода"
            }
        }

        var subtitle: String {
            switch self {
            case .balanced: "Лучшее сочетание ума и скорости для вашего Mac"
            case .fast: "Отвечает мгновенно, экономит память и батарею"
            case .best: "Самая умная из тех, что поместятся; думает дольше"
            case .coder: "Пишет и объясняет программы"
            }
        }

        var symbol: String {
            switch self {
            case .balanced: "scalemass.fill"
            case .fast: "bolt.fill"
            case .best: "brain.fill"
            case .coder: "chevron.left.forwardslash.chevron.right"
            }
        }
    }

    struct Recommendation: Identifiable, Sendable, Hashable {
        var role: Role
        var card: ModelCard
        var variant: ModelVariant
        var fit: FitReport
        var id: String { role.rawValue + card.id }
    }

    /// Подбор моделей под Mac. `variants` — точные списки файлов, если уже загружены с Hugging Face.
    static func recommend(cards: [ModelCard], variants: [String: [ModelVariant]], mac: MacProfile) -> [Recommendation] {
        typealias Option = (card: ModelCard, variant: ModelVariant, fit: FitReport)
        func options(upgrade: Bool) -> [Option] {
            cards.compactMap { card in
                let list = variants[card.id] ?? ModelVariant.estimates(params: card.params)
                guard let picked = pick(list, card: card, mac: mac, upgrade: upgrade), picked.fit.runnable else { return nil }
                return (card: card, variant: picked.variant, fit: picked.fit)
            }
        }
        let normal = options(upgrade: true)
        let light = options(upgrade: false)
        let comfortableBudget = Double(budget(mac).comfortable)

        var result: [Recommendation] = []
        var used: Set<String> = []
        func add(_ role: Role, _ o: Option?) {
            guard let o else { return }
            used.insert(o.card.id)
            result.append(Recommendation(role: role, card: o.card, variant: o.variant, fit: o.fit))
        }

        // Небольшой штраф за нестандартное сжатие: при прочих равных лучше Q4_K_M.
        func quantBonus(_ o: Option) -> Double { (o.variant.quant.quality - 0.975) * 200 }

        // Сбалансированная: самая умная из тех, что пишут быстрее, чем человек читает (с запасом).
        let balanced = normal.filter { $0.fit.comfortable && $0.fit.tokensPerSecond >= 10 }
            .max { a, b in
                a.card.quality + min(a.fit.tokensPerSecond, 40) * 0.08 + quantBonus(a) < b.card.quality + min(b.fit.tokensPerSecond, 40) * 0.08 + quantBonus(b)
            }
            ?? normal.filter { $0.fit.comfortable && $0.fit.tokensPerSecond >= 5 }.max { $0.card.quality < $1.card.quality }
        add(.balanced, balanced)

        // Быстрая: лёгкая и заметно шустрее сбалансированной. Если та и так «летает» — не нужна.
        if let b = balanced, b.fit.tokensPerSecond < 35 {
            let floor = max(20, b.fit.tokensPerSecond * 1.3)
            add(.fast, light.filter { !used.contains($0.card.id) && $0.fit.comfortable && $0.fit.tokensPerSecond >= floor
                    && Double($0.fit.memory) <= comfortableBudget * 0.55 }
                .max { $0.card.quality + $0.fit.tokensPerSecond * 0.15 < $1.card.quality + $1.fit.tokensPerSecond * 0.15 })
        }

        // Максимум ума: заметно умнее сбалансированной; может быть «на пределе» — карточка предупредит.
        add(.best, normal.filter { !used.contains($0.card.id) && $0.fit.tokensPerSecond >= 5
                && $0.card.quality > (balanced?.card.quality ?? 0) + 4 }
            .max { $0.card.quality < $1.card.quality })

        // Для кода — если нашлась подходящая специализированная.
        add(.coder, normal.filter { !used.contains($0.card.id) && $0.card.tags.contains(.code) && $0.fit.comfortable && $0.fit.tokensPerSecond >= 8 }
            .max { $0.card.quality < $1.card.quality })
        return result
    }
}

extension Format {
    nonisolated static func gb(_ bytes: Double) -> String {
        let v = bytes / Advisor.gb
        return v < 10 ? String(format: "%.1f ГБ", v).replacingOccurrences(of: ".", with: ",") : "\(Int(v.rounded())) ГБ"
    }
}
