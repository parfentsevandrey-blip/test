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
    /// Поместится только с коротким контекстом (2K) и сжатой памятью контекста.
    var shrunk = false

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

    /// `compressedKV` — память контекста в 8 битах: вдвое меньше.
    /// `onDisk` — модель уже скачана, место на диске не проверяем.
    /// `shrink` — если не помещается, проверить запуск с коротким контекстом и сжатой памятью контекста
    /// (так Synapse и загрузит модель, см. `loadPlan`).
    static func estimate(size: Int64, params: Double, active: Double?, mac: MacProfile, context: Int? = nil,
                         compressedKV: Bool = false, onDisk: Bool = false, shrink: Bool = true) -> FitReport {
        var ctx = context ?? defaultContext(mac)
        let weights = Double(size)
        let kv = kvPerToken(params: params) * Double(ctx) * (compressedKV ? 0.53 : 1)
        let overhead = 0.35 * gb + weights * 0.04
        var need = weights + kv + overhead
        let (comfortable, hard) = budget(mac)

        // Каждый токен — чтение всех активных весов. У MoE читается только доля экспертов
        // (плюс общие слои — поэтому с запасом 25%).
        let activeFraction = active.map { min(1, $0 / max(params, 0.1) * 1.25) } ?? 1
        let perToken = weights * activeFraction / gb + 0.12
        var tps = mac.bandwidth * 0.62 / perToken
        if mac.virtualized { tps *= 0.25 }

        var reasons: [String] = []
        var shrunk = false
        let level: FitReport.Level
        // Короткий контекст и 8-битная память контекста — крайний вариант, с которым модель ещё запустится.
        let shortNeed = weights + kvPerToken(params: params) * 2048 * 0.53 + overhead
        if !onDisk && Double(size) > Double(mac.freeDisk) - 3 * gb {
            level = .noDisk
            reasons.append("Нужно \(Format.bytes(size)) на диске, свободно \(Format.bytes(mac.freeDisk)) (с запасом 3 ГБ для системы).")
        } else if need > Double(hard) && shrink && ctx > 2048 && shortNeed <= Double(hard) {
            level = .tight
            tps *= 0.7
            need = shortNeed
            ctx = 2048
            shrunk = true
            reasons.append("Поместится только с коротким контекстом: ≈\(Format.gb(need)) из \(Format.gb(Double(hard))), которые можно отдать видеоядру. Synapse сам уменьшит контекст до 2K и сожмёт его память.")
            reasons.append("Модель будет помнить лишь последние пару страниц разговора. Перед запуском закройте тяжёлые программы.")
        } else if need > Double(hard) {
            level = .tooBig
            let (needText, hardText) = Format.gbDistinct(need, Double(hard))
            reasons.append("Модели нужно ≈\(needText) памяти, а этому Mac можно отдать видеоядру не больше \(hardText) — не хватает ≈\(Format.gb(need - Double(hard))).")
            reasons.append("Она не запустится или будет работать с постоянной подкачкой с диска. Возьмите вариант посильнее сжатый — или загляните в «Выжать максимум».")
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
        return FitReport(level: level, memory: Int64(need), tokensPerSecond: tps, context: ctx, reasons: reasons, shrunk: shrunk)
    }

    /// Как загрузить модель, чтобы она поместилась: контекст и сжатие его памяти.
    /// `context` и `compressKV` из Настроек: 0 и `nil` — решаем сами. Если не помещается, уменьшаем контекст
    /// до 2K и сжимаем его память — так запускаются модели «на пределе».
    static func loadPlan(size: Int64, params: Double, active: Double?, trainContext: Int?, mac: MacProfile,
                         context: Int = 0, compressKV: Bool? = nil, onDisk: Bool = false) -> (context: Int, compressKV: Bool, fit: FitReport) {
        let limit = trainContext ?? .max
        func fit(_ ctx: Int, _ compress: Bool) -> FitReport {
            estimate(size: size, params: params, active: active, mac: mac, context: ctx, compressedKV: compress, onDisk: onDisk, shrink: false)
        }
        if context > 0 {
            let ctx = min(context, limit)
            let compress = compressKV ?? (fit(ctx, false).level >= .tight)
            return (ctx, compress, fit(ctx, compress))
        }
        let base = min(defaultContext(mac), limit)
        var options: [(Int, Bool)] = []
        if compressKV != true { options.append((base, false)) }
        if compressKV != false { options.append((base, true)) }
        for ctx in [4096, 2048] where ctx < base { options.append((ctx, compressKV ?? true)) }
        let plans = options.map { ctx, compress in (context: ctx, compressKV: compress, fit: fit(ctx, compress)) }
        // Сначала — без тесноты. Если тесно в любом случае — со сжатой памятью контекста (качество то же,
        // памяти меньше) и самым длинным контекстом, с которым модель запустится.
        return plans.first { $0.fit.level < .tight } ?? plans.first { $0.fit.runnable && $0.compressKV }
            ?? plans.first { $0.fit.runnable } ?? plans[plans.count - 1]
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
                // В рекомендации — только то, что работает с нормальным контекстом; остальное — в «Выжать максимум».
                guard let picked = pick(list, card: card, mac: mac, upgrade: upgrade), picked.fit.runnable, !picked.fit.shrunk else { return nil }
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

// MARK: - Выжать максимум

extension Advisor {
    /// Модель классом выше рекомендованной, сжатая так, чтобы поместиться в память Mac.
    nonisolated struct Stretch: Identifiable, Sendable, Hashable {
        var card: ModelCard
        var variant: ModelVariant
        /// Оценка с тем контекстом и сжатием памяти контекста, с которыми Synapse её запустит.
        var fit: FitReport
        var compressKV: Bool
        /// Поместится, только если отдать видеоядру больше памяти.
        var needsMoreGPU: Bool
        var id: String { card.id }

        /// Насколько модель крупнее той, что советуем каждый день.
        func times(_ other: ModelCard) -> Double { card.params / max(other.params, 0.1) }
    }

    /// Самое сильное сжатие, после которого модель ещё полезна: ниже ~2 бит она начинает путаться.
    nonisolated static let stretchFloor = 0.7
    /// Меньше 3 токенов в секунду — ответ из нескольких абзацев ждать минуты.
    nonisolated static let stretchMinSpeed = 3.0

    /// Лучший вариант модели, который вообще запустится на этом Mac: самое мягкое сжатие из возможных,
    /// контекст и память контекста ужимаются, если нужно.
    nonisolated static func squeeze(_ variants: [ModelVariant], card: ModelCard, mac: MacProfile) -> (variant: ModelVariant, fit: FitReport, compressKV: Bool)? {
        let usable = variants.filter { $0.quant.quality >= stretchFloor && $0.quant.bits <= 9 }.sorted { $0.quant.bits > $1.quant.bits }
        for v in usable {
            let plan = loadPlan(size: v.size, params: card.params, active: card.activeParams, trainContext: card.contextLength, mac: mac)
            if plan.fit.runnable && plan.fit.tokensPerSecond >= stretchMinSpeed { return (v, plan.fit, plan.compressKV) }
        }
        return nil
    }

    /// Модели классом выше `above` (качество рекомендованной), которые этот Mac потянет только сжатыми.
    /// Если предел видеоядра можно поднять, в список попадают и те, кому нужно больше памяти.
    nonisolated static func stretch(cards: [ModelCard], variants: [String: [ModelVariant]], mac: MacProfile, above: Double) -> [Stretch] {
        let unlocked = GPUMemory.unlocked(mac)
        var result: [Stretch] = []
        for card in cards where card.quality > above + 2 && EngineCatalog.supports(card.architecture) {
            let list = variants[card.id] ?? ModelVariant.estimates(params: card.params, deep: true)
            if let s = squeeze(list, card: card, mac: mac) {
                result.append(Stretch(card: card, variant: s.variant, fit: s.fit, compressKV: s.compressKV, needsMoreGPU: false))
            } else if let unlocked, let s = squeeze(list, card: card, mac: unlocked) {
                result.append(Stretch(card: card, variant: s.variant, fit: s.fit, compressKV: s.compressKV, needsMoreGPU: true))
            }
        }
        return result.sorted { a, b in
            a.card.quality != b.card.quality ? a.card.quality > b.card.quality : a.card.downloads > b.card.downloads
        }
    }
}

// MARK: - Тихие и лёгкие

extension Advisor {
    /// Модель, которую Mac почти не заметит: мало памяти, мало вычислений, ответ — за секунды.
    nonisolated struct Quiet: Identifiable, Sendable, Hashable {
        var card: ModelCard
        var variant: ModelVariant
        var fit: FitReport
        var id: String { card.id }

        /// Доля памяти Mac, которую займёт модель.
        func share(_ mac: MacProfile) -> Double { Double(fit.memory) / Double(max(mac.memory, 1)) }
        /// Сколько секунд пишется ответ в ~300 слов (≈750 токенов).
        var answerSeconds: Double { fit.tokensPerSecond > 0 ? 750 / fit.tokensPerSecond : .infinity }
    }

    /// Не больше четверти памяти: остальные программы ничего не почувствуют.
    nonisolated static let quietShare = 0.25
    /// На каждый токен работает не больше ~5 млрд параметров — видеоядро не успевает нагреться.
    nonisolated static let quietActive = 5.0
    /// Не медленнее 35 ток/с: ответ готов за десяток-другой секунд, нагрузка короткая.
    nonisolated static let quietSpeed = 35.0

    /// Модели, которые работают на этом Mac без нагрева и шума вентилятора.
    nonisolated static func quiet(cards: [ModelCard], variants: [String: [ModelVariant]], mac: MacProfile) -> [Quiet] {
        let limit = max(2.5 * gb, Double(mac.memory) * quietShare)
        var result: [Quiet] = []
        for card in cards where (card.activeParams ?? card.params) <= quietActive && EngineCatalog.supports(card.architecture) {
            let list = variants[card.id] ?? ModelVariant.estimates(params: card.params)
            guard let picked = pick(list, card: card, mac: mac), picked.fit.comfortable,
                  Double(picked.fit.memory) <= limit, picked.fit.tokensPerSecond >= quietSpeed else { continue }
            result.append(Quiet(card: card, variant: picked.variant, fit: picked.fit))
        }
        // Проверенные Synapse — выше: оценка качества у живых сборок приблизительная.
        func rank(_ q: Quiet) -> Double { q.card.quality + (q.card.curated ? 8 : 0) }
        return result.sorted { a, b in
            rank(a) != rank(b) ? rank(a) > rank(b) : a.fit.tokensPerSecond > b.fit.tokensPerSecond
        }
    }
}

extension Format {
    nonisolated static func gb(_ bytes: Double) -> String {
        let v = bytes / Advisor.gb
        return v < 10 ? String(format: "%.1f ГБ", v).replacingOccurrences(of: ".", with: ",") : "\(Int(v.rounded())) ГБ"
    }

    /// Два объёма, которые после округления не сливаются: не «нужно 20, доступно 20», а «20,4 и 19,8».
    nonisolated static func gbDistinct(_ a: Double, _ b: Double) -> (String, String) {
        let (x, y) = (gb(a), gb(b))
        guard x == y else { return (x, y) }
        func precise(_ bytes: Double) -> String { String(format: "%.1f ГБ", bytes / Advisor.gb).replacingOccurrences(of: ".", with: ",") }
        return (precise(a), precise(b))
    }
}
