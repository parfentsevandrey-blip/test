import Foundation

/// Декодер ядра DTS Coherent Acoustics (ETSI TS 102 114, раздел 5 и приложение C) — с нуля, без сторонних библиотек.
///
/// macOS не умеет DTS, а на Blu-ray-ремуксах это почти всегда единственный звук. Дорожки DTS-HD MA и DTS-HD HRA
/// несут внутри обычное ядро DTS (до 5.1, 1,5 Мбит/с) — его и декодируем; расширения (XLL, XBR, X96, XCH) пропускаются.
///
/// Путь кадра: заголовок → боковая информация подкадров (распределение бит, масштабы, предсказание) →
/// отсчёты 32 поддиапазонов (Хаффман, блочные коды, VQ) → обратное ADPCM → совместная интенсивность и
/// сумма-разность → 32-полосная банка фильтров QMF → PCM. Канал LFE восстанавливается интерполяцией.
nonisolated final class DTSDecoder {
    enum Speaker: Sendable, Equatable { case left, right, center, lfe, leftSurround, rightSurround, centerSurround }

    /// Декодированный кадр: отсчёты float, чередуются по каналам в порядке `speakers`.
    struct Output: Sendable {
        var sampleRate: Int
        var speakers: [Speaker]
        var samples: [Float]
        var frames: Int
    }

    struct DecodeError: Error, CustomStringConvertible {
        let description: String
        init(_ d: String) { description = d }
    }

    static let maxChannels = 5
    private static let subbands = 32

    // Состояние между кадрами.
    private var history = [[Float]](repeating: [Float](repeating: 0, count: 32 * 4), count: maxChannels)
    private var qmf = [QMFBank](repeating: QMFBank(), count: maxChannels)
    private var lfeHistory = [Float](repeating: 0, count: 8)

    /// Делит на 2^23: декодер работает в шкале 24-битного PCM.
    private static let outputScale: Float = 1 / 8_388_608

    init() {}

    func reset() {
        history = [[Float]](repeating: [Float](repeating: 0, count: 32 * 4), count: Self.maxChannels)
        qmf = [QMFBank](repeating: QMFBank(), count: Self.maxChannels)
        lfeHistory = [Float](repeating: 0, count: 8)
    }

    // MARK: - Синхронизация и формат потока

    /// Приводит кадр к 16-битным словам big-endian (так записан DTS в Matroska и на Blu-ray).
    /// Поддерживаются и 14-битные/little-endian варианты из WAV и CD-DTS.
    static func normalize(_ input: [UInt8]) -> [UInt8]? {
        guard input.count >= 16 else { return nil }
        let head = UInt32(input[0]) << 24 | UInt32(input[1]) << 16 | UInt32(input[2]) << 8 | UInt32(input[3])
        switch head {
        case 0x7FFE_8001:
            return input
        case 0xFE7F_0180:
            var out = input
            var i = 0
            while i + 1 < out.count { out.swapAt(i, i + 1); i += 2 }
            return out
        case 0x1FFF_E800, 0xFF1F_00E8:
            // 14 бит в 16-битных словах: собираем поток из младших 14 бит каждого слова.
            let little = head == 0xFF1F_00E8
            var bits: UInt64 = 0
            var count = 0
            var out: [UInt8] = []
            out.reserveCapacity(input.count * 14 / 16 + 2)
            var i = 0
            while i + 1 < input.count {
                let word = little ? UInt16(input[i + 1]) << 8 | UInt16(input[i]) : UInt16(input[i]) << 8 | UInt16(input[i + 1])
                bits = bits << 14 | UInt64(word & 0x3FFF)
                count += 14
                while count >= 8 {
                    out.append(UInt8(truncatingIfNeeded: bits >> UInt64(count - 8)))
                    count -= 8
                }
                i += 2
            }
            return out
        default:
            return nil
        }
    }

    // MARK: - Кадр

    func decode(_ packet: [UInt8]) throws -> Output {
        guard let data = Self.normalize(packet) else { throw DecodeError("нет синхрослова DTS") }
        var r = DTSBits(data)
        _ = r.read(32)                 // SYNC
        let normalFrame = r.read(1) == 1
        _ = r.read(5)                  // SHORT
        let crcPresent = r.read(1) == 1
        let blocks = Int(r.read(7)) + 1
        let frameSize = Int(r.read(14)) + 1
        let amode = Int(r.read(6))
        let sfreq = Int(r.read(4))
        let rate = Int(r.read(5))
        _ = r.read(1)                  // FixedBit
        let dynf = r.read(1) == 1
        let timef = r.read(1) == 1
        let auxf = r.read(1) == 1
        _ = r.read(1)                  // HDCD
        _ = r.read(3)                  // EXT_AUDIO_ID
        _ = r.read(1)                  // EXT_AUDIO
        let aspf = r.read(1) == 1
        let lff = Int(r.read(2))
        let hflag = r.read(1) == 1
        if crcPresent { _ = r.read(16) }
        let perfect = r.read(1) == 1   // FILTS
        let vernum = Int(r.read(4))
        _ = r.read(2)                  // CHIST
        _ = r.read(3)                  // PCMR
        let sumf = r.read(1) == 1
        let sums = r.read(1) == 1
        _ = r.read(4)                  // DIALNORM / UNSPEC
        _ = (normalFrame, timef, auxf)

        guard frameSize >= 96, frameSize <= data.count + 2 else { throw DecodeError("неверный размер кадра \(frameSize)") }
        guard blocks >= 6 || !normalFrame else { throw DecodeError("неверное число блоков \(blocks)") }
        guard vernum <= 7 else { throw DecodeError("версия кодера \(vernum) несовместима") }
        guard lff != 3 else { throw DecodeError("неверный флаг LFE") }
        let sampleRate = [0, 8000, 16000, 32000, 0, 0, 11025, 22050, 44100, 0, 0, 12000, 24000, 48000, 0, 0][sfreq]
        guard sampleRate > 0 else { throw DecodeError("неверная частота") }

        // Заголовок основного звука (5.4.3).
        let subframeCount = Int(r.read(4)) + 1
        let channelCount = Int(r.read(3)) + 1
        guard channelCount <= Self.maxChannels else { throw DecodeError("каналов ядра: \(channelCount)") }
        var nSubbands = [Int](repeating: 0, count: channelCount)
        var vqStart = [Int](repeating: 0, count: channelCount)
        var joinx = [Int](repeating: 0, count: channelCount)
        var thuff = [Int](repeating: 0, count: channelCount)
        var shuff = [Int](repeating: 0, count: channelCount)
        var bhuff = [Int](repeating: 0, count: channelCount)
        for ch in 0..<channelCount { nSubbands[ch] = min(Int(r.read(5)) + 2, 32) }
        for ch in 0..<channelCount { vqStart[ch] = min(Int(r.read(5)) + 1, 32) }
        for ch in 0..<channelCount { joinx[ch] = Int(r.read(3)) }
        for ch in 0..<channelCount { thuff[ch] = Int(r.read(2)) }
        for ch in 0..<channelCount { shuff[ch] = Int(r.read(3)) }
        for ch in 0..<channelCount { bhuff[ch] = Int(r.read(3)) }
        var sel = [[Int]](repeating: [Int](repeating: 0, count: 26), count: channelCount)
        for n in 0..<10 {
            let width = n == 0 ? 1 : (n < 5 ? 2 : 3)
            for ch in 0..<channelCount { sel[ch][n] = Int(r.read(width)) }
        }
        var adjust = [[Float]](repeating: [Float](repeating: 1, count: 26), count: channelCount)
        let adjTable: [Float] = [1, 1.125, 1.25, 1.4375]
        for n in 0..<10 {
            let limit = n == 0 ? 1 : (n < 5 ? 3 : 7)
            for ch in 0..<channelCount where sel[ch][n] < limit {
                adjust[ch][n] = adjTable[Int(r.read(2))]
            }
        }
        if crcPresent { _ = r.read(16) }
        for ch in 0..<channelCount {
            guard shuff[ch] != 7, bhuff[ch] != 7 else { throw DecodeError("неверная кодовая книга") }
            if joinx[ch] > 0 && joinx[ch] - 1 >= ch { throw DecodeError("неверный источник совместного кодирования") }
        }

        if !hflag {
            for ch in 0..<channelCount { for i in history[ch].indices { history[ch][i] = 0 } }
        }

        let speakers = Self.layout(amode: amode, channels: channelCount)
        let totalPerChannel = blocks * 32
        var pcm = [[Float]](repeating: [Float](repeating: 0, count: totalPerChannel), count: channelCount)
        var lfe = [Float](repeating: 0, count: lff > 0 ? totalPerChannel : 0)
        var written = 0
        let stepTable = rate == 0x1F ? DTSTables.stepLossless : DTSTables.stepLossy

        for _ in 0..<subframeCount {
            // Боковая информация подкадра (5.5.1).
            let subsubframes = Int(r.read(2)) + 1
            _ = r.read(3) // PSC
            let samplesPerBand = subsubframes * 8
            var pmode = [[Bool]](repeating: [Bool](repeating: false, count: 32), count: channelCount)
            var pvq = [[Int]](repeating: [Int](repeating: 0, count: 32), count: channelCount)
            for ch in 0..<channelCount {
                for n in 0..<nSubbands[ch] { pmode[ch][n] = r.read(1) == 1 }
            }
            for ch in 0..<channelCount {
                for n in 0..<nSubbands[ch] where pmode[ch][n] { pvq[ch][n] = Int(r.read(12)) }
            }
            var abits = [[Int]](repeating: [Int](repeating: 0, count: 32), count: channelCount)
            for ch in 0..<channelCount {
                for n in 0..<min(vqStart[ch], nSubbands[ch]) {
                    let value: Int
                    switch bhuff[ch] {
                    case 0...4: value = try Huffman.decode(Huffman.bitAllocation[bhuff[ch]], &r)
                    case 5: value = Int(r.read(4))
                    default: value = Int(r.read(5))
                    }
                    guard value <= 26 else { throw DecodeError("неверное распределение бит") }
                    abits[ch][n] = value
                }
            }
            var tmode = [[Int]](repeating: [Int](repeating: 0, count: 32), count: channelCount)
            if subsubframes > 1 {
                for ch in 0..<channelCount {
                    for n in 0..<min(vqStart[ch], nSubbands[ch]) where abits[ch][n] > 0 {
                        tmode[ch][n] = try Huffman.decode(Huffman.transient[thuff[ch]], &r)
                    }
                }
            }
            var scales = [[(Float, Float)]](repeating: [(Float, Float)](repeating: (0, 0), count: 32), count: channelCount)
            for ch in 0..<channelCount {
                let table = shuff[ch] == 6 ? DTSTables.scale7 : DTSTables.scale6
                var sum = 0
                func next() throws -> Float {
                    if shuff[ch] < 5 {
                        sum += try Huffman.decode(Huffman.scales[shuff[ch]], &r)
                    } else {
                        sum = Int(r.read(shuff[ch] == 6 ? 7 : 6))
                    }
                    guard sum >= 0, sum < table.count else { throw DecodeError("неверный масштаб") }
                    return Float(table[sum])
                }
                for n in 0..<min(vqStart[ch], nSubbands[ch]) where abits[ch][n] > 0 {
                    scales[ch][n].0 = try next()
                    if tmode[ch][n] > 0 { scales[ch][n].1 = try next() }
                }
                if vqStart[ch] < nSubbands[ch] {
                    for n in vqStart[ch]..<nSubbands[ch] { scales[ch][n].0 = try next() }
                }
            }
            var joinSel = [Int](repeating: 0, count: channelCount)
            for ch in 0..<channelCount where joinx[ch] > 0 { joinSel[ch] = Int(r.read(3)) }
            var joinScale = [[Float]](repeating: [Float](repeating: 0, count: 32), count: channelCount)
            for ch in 0..<channelCount where joinx[ch] > 0 {
                let source = joinx[ch] - 1
                guard joinSel[ch] != 7 else { throw DecodeError("неверная книга совместного кодирования") }
                if nSubbands[ch] < nSubbands[source] {
                    for n in nSubbands[ch]..<nSubbands[source] {
                        var index: Int
                        if joinSel[ch] < 5 { index = try Huffman.decode(Huffman.scales[joinSel[ch]], &r) }
                        else { index = Int(r.read(joinSel[ch] == 6 ? 7 : 6)) }
                        index += 64
                        guard index >= 0, index < DTSTables.jointScale.count else { throw DecodeError("неверный масштаб интенсивности") }
                        joinScale[ch][n] = DTSTables.jointScale[index]
                    }
                }
            }
            if dynf { _ = r.read(8) } // RANGE: сжатие динамического диапазона не применяем — звук как в оригинале
            if crcPresent { _ = r.read(16) }

            // Данные подкадра (5.6): VQ высоких частот, LFE, затем подподкадры.
            var subband = [[[Float]]](repeating: [[Float]](repeating: [Float](repeating: 0, count: samplesPerBand), count: 32), count: channelCount)
            for ch in 0..<channelCount where vqStart[ch] < nSubbands[ch] {
                for n in vqStart[ch]..<nSubbands[ch] {
                    let index = Int(r.read(10))
                    let scale = scales[ch][n].0 / 16
                    for m in 0..<samplesPerBand {
                        subband[ch][n][m] = scale * Float(DTSTables.highFreqVQ[index * 32 + min(m, 31)])
                    }
                }
            }
            if lff > 0 {
                let count = 2 * lff * subsubframes
                var decimated = [Float](repeating: 0, count: count)
                for i in 0..<count { decimated[i] = Float(Int8(bitPattern: UInt8(r.read(8)))) }
                let scaleIndex = Int(r.read(8))
                let scale = Float(DTSTables.scale7[min(scaleIndex, 127)]) * 0.035
                for i in 0..<count { decimated[i] *= scale }
                interpolateLFE(decimated, factor: lff == 1 ? 128 : 64, into: &lfe, at: written)
            }
            for ssf in 0..<subsubframes {
                for ch in 0..<channelCount {
                    for n in 0..<min(vqStart[ch], nSubbands[ch]) {
                        let bits = abits[ch][n]
                        var values = [Int](repeating: 0, count: 8)
                        if bits > 0 {
                            let choice = bits <= 10 ? sel[ch][bits - 1] : 0
                            let books = Self.codebookCount(bits)
                            if choice == books - 1 {
                                if bits <= 7 {
                                    let levels = Self.levels[bits]
                                    let width = Self.blockBits[bits]
                                    for half in 0..<2 {
                                        var code = Int(r.read(width))
                                        let offset = (levels - 1) / 2
                                        for k in 0..<4 {
                                            values[half * 4 + k] = code % levels - offset
                                            code /= levels
                                        }
                                        if code != 0 { throw DecodeError("ошибка блочного кода") }
                                    }
                                } else {
                                    let width = bits - 3
                                    for k in 0..<8 {
                                        let raw = Int(r.read(width))
                                        values[k] = raw >= 1 << (width - 1) ? raw - (1 << width) : raw
                                    }
                                }
                            } else {
                                let book = Huffman.audio(levels: Self.levels[bits], book: choice)
                                for k in 0..<8 { values[k] = try Huffman.decode(book, &r) }
                            }
                        }
                        let step = Float(stepTable[bits]) / 4_194_304
                        let transient = tmode[ch][n] == 0 ? subsubframes : tmode[ch][n]
                        var scale = step * (ssf < transient ? scales[ch][n].0 : scales[ch][n].1)
                        if bits > 0, bits <= 10 { scale *= adjust[ch][bits - 1] }
                        let base = ssf * 8
                        for k in 0..<8 { subband[ch][n][base + k] = scale * Float(values[k]) }
                        // Обратное ADPCM: к остатку добавляется предсказание по четырём прошлым отсчётам.
                        if pmode[ch][n] {
                            let v = pvq[ch][n] * 4
                            let c0 = Float(DTSTables.adpcmVQ[v]) / 8192, c1 = Float(DTSTables.adpcmVQ[v + 1]) / 8192
                            let c2 = Float(DTSTables.adpcmVQ[v + 2]) / 8192, c3 = Float(DTSTables.adpcmVQ[v + 3]) / 8192
                            for k in 0..<8 {
                                let m = base + k
                                let p1 = m >= 1 ? subband[ch][n][m - 1] : history[ch][n * 4 + 3 + m - 0]
                                let p2 = m >= 2 ? subband[ch][n][m - 2] : history[ch][n * 4 + 2 + m]
                                let p3 = m >= 3 ? subband[ch][n][m - 3] : history[ch][n * 4 + 1 + m]
                                let p4 = m >= 4 ? subband[ch][n][m - 4] : history[ch][n * 4 + m]
                                subband[ch][n][m] += c0 * p1 + c1 * p2 + c2 * p3 + c3 * p4
                            }
                        }
                    }
                }
                if ssf == subsubframes - 1 || aspf {
                    let check = r.read(16)
                    if check != 0xFFFF { throw DecodeError("нет DSYNC — кадр повреждён") }
                }
            }
            // История для предсказания в следующем подкадре: последние четыре отсчёта каждого поддиапазона.
            for ch in 0..<channelCount {
                for n in 0..<32 {
                    for k in 0..<4 {
                        let m = samplesPerBand - 4 + k
                        history[ch][n * 4 + k] = m >= 0 ? subband[ch][n][m] : 0
                    }
                }
            }

            // Совместная интенсивность (C.3.4).
            var active = nSubbands
            for ch in 0..<channelCount where joinx[ch] > 0 {
                let source = joinx[ch] - 1
                if nSubbands[ch] < nSubbands[source] {
                    for n in nSubbands[ch]..<nSubbands[source] {
                        for m in 0..<samplesPerBand { subband[ch][n][m] = joinScale[ch][n] * subband[source][n][m] }
                    }
                }
                active[ch] = max(active[ch], nSubbands[source])
            }
            // Сумма-разность (C.3.5).
            if let (l, rr) = Self.frontPair(amode: amode, channels: channelCount), sumf || amode == 3 {
                Self.sumDifference(&subband, l, rr, bands: max(active[l], active[rr]), samples: samplesPerBand)
            }
            if sums, let (l, rr) = Self.surroundPair(amode: amode, channels: channelCount) {
                Self.sumDifference(&subband, l, rr, bands: max(active[l], active[rr]), samples: samplesPerBand)
            }
            // Банка фильтров (C.3.6).
            let coefficients = perfect ? DTSTables.qmfPerfect : DTSTables.qmfNonPerfect
            for ch in 0..<channelCount {
                guard written + samplesPerBand * 32 <= totalPerChannel else { throw DecodeError("подкадров больше, чем блоков") }
                qmf[ch].synthesize(subband[ch], bands: active[ch], samples: samplesPerBand, coefficients: coefficients,
                                   into: &pcm[ch], at: written)
            }
            written += samplesPerBand * 32
        }

        // Вывод: чередуем каналы, LFE — после фронтальных, как принято в macOS.
        var order: [(Speaker, Int)] = speakers.enumerated().map { ($0.element, $0.offset) }
        if lff > 0 { order.append((.lfe, -1)) }
        order.sort { Self.rank($0.0) < Self.rank($1.0) }
        let frames = written
        var out = [Float](repeating: 0, count: frames * order.count)
        for (slot, (_, source)) in order.enumerated() {
            if source >= 0 {
                pcm[source].withUnsafeBufferPointer { src in
                    for i in 0..<frames { out[i * order.count + slot] = src[i] * Self.outputScale }
                }
            } else {
                for i in 0..<min(frames, lfe.count) { out[i * order.count + slot] = lfe[i] * Self.outputScale }
            }
        }
        return Output(sampleRate: sampleRate, speakers: order.map(\.0), samples: out, frames: frames)
    }

    // MARK: - Части

    /// Интерполяция LFE (C.3.7): каждый прореженный отсчёт даёт `factor` отсчётов, КИХ из 512 коэффициентов.
    private func interpolateLFE(_ decimated: [Float], factor: Int, into out: inout [Float], at start: Int) {
        let coefficients = factor == 128 ? DTSTables.lfe128 : DTSTables.lfe64
        let taps = 512 / factor
        // lfeHistory хранит последние прореженные отсчёты прошлого подкадра (от новых к старым).
        var window = lfeHistory
        var index = start
        for sample in decimated {
            window.insert(sample, at: 0)
            window.removeLast()
            for k in 0..<factor {
                var acc: Float = 0
                for j in 0..<taps { acc += window[j] * coefficients[k + j * factor] }
                if index < out.count { out[index] = acc }
                index += 1
            }
        }
        lfeHistory = window
    }

    private static func sumDifference(_ s: inout [[[Float]]], _ a: Int, _ b: Int, bands: Int, samples: Int) {
        for n in 0..<min(bands, 32) {
            for m in 0..<samples {
                let x = s[a][n][m], y = s[b][n][m]
                s[a][n][m] = x + y
                s[b][n][m] = x - y
            }
        }
    }

    /// Порядок каналов ядра по AMODE (таблица 5-4).
    static func layout(amode: Int, channels: Int) -> [Speaker] {
        let map: [Speaker]
        switch amode {
        case 0: map = [.center]
        case 1, 2, 3, 4: map = [.left, .right]
        case 5: map = [.center, .left, .right]
        case 6: map = [.left, .right, .centerSurround]
        case 7: map = [.center, .left, .right, .centerSurround]
        case 8: map = [.left, .right, .leftSurround, .rightSurround]
        default: map = [.center, .left, .right, .leftSurround, .rightSurround]
        }
        if map.count == channels { return map }
        let fallback: [[Speaker]] = [[.center], [.left, .right], [.center, .left, .right], [.left, .right, .leftSurround, .rightSurround],
                                     [.center, .left, .right, .leftSurround, .rightSurround]]
        return fallback[max(0, min(channels, 5) - 1)]
    }

    private static func frontPair(amode: Int, channels: Int) -> (Int, Int)? {
        let map = layout(amode: amode, channels: channels)
        guard let l = map.firstIndex(of: .left), let r = map.firstIndex(of: .right) else { return nil }
        return (l, r)
    }

    private static func surroundPair(amode: Int, channels: Int) -> (Int, Int)? {
        let map = layout(amode: amode, channels: channels)
        guard let l = map.firstIndex(of: .leftSurround), let r = map.firstIndex(of: .rightSurround) else { return nil }
        return (l, r)
    }

    private static func rank(_ s: Speaker) -> Int {
        switch s {
        case .left: 0
        case .right: 1
        case .center: 2
        case .lfe: 3
        case .leftSurround: 4
        case .rightSurround: 5
        case .centerSurround: 6
        }
    }

    /// Число уровней квантователя по ABITS (таблица 5-26); для Хаффмана — 33/65/129.
    static let levels = [0, 3, 5, 7, 9, 13, 17, 25, 33, 65, 129]
    /// Длина блочного кода: 4 отсчёта в одном слове (приложение D.6).
    static let blockBits = [0, 7, 10, 12, 13, 15, 17, 19]

    /// Сколько кодовых книг в группе ABITS: последняя — блочный код или «без кодирования».
    static func codebookCount(_ abits: Int) -> Int {
        switch abits {
        case 1: 2
        case 2...5: 4
        case 6...10: 8
        default: 1
        }
    }
}

/// Чтение бит старшим вперёд.
nonisolated struct DTSBits {
    private let bytes: [UInt8]
    private(set) var position = 0

    init(_ bytes: [UInt8]) { self.bytes = bytes }

    var bitsLeft: Int { bytes.count * 8 - position }

    mutating func read(_ count: Int) -> UInt32 {
        var value: UInt32 = 0
        for _ in 0..<count { value = value << 1 | readBit() }
        return value
    }

    @inline(__always) mutating func readBit() -> UInt32 {
        let byte = position >> 3
        guard byte < bytes.count else { position += 1; return 0 }
        let bit = UInt32(bytes[byte] >> (7 - UInt8(position & 7))) & 1
        position += 1
        return bit
    }
}

/// Кодовые книги Хаффмана приложения D.5 в виде двоичных деревьев.
nonisolated enum Huffman {
    struct Tree: Sendable {
        /// Пары детей узла: ≥ 0 — внутренний узел, < 0 — лист (значение = -(x + 1) - сдвиг).
        var children: [Int32]
        var offset: Int32
    }

    static func build(_ name: String) -> Tree {
        guard let (first, count) = DTSTables.huffmanIndex[name] else { return Tree(children: [0, 0], offset: 0) }
        var children: [Int32] = [0, 0]
        let codes = DTSTables.huffmanCodes
        let offset: Int32 = 1 << 20
        for row in first..<(first + count) {
            let value = codes[row * 3], length = Int(codes[row * 3 + 1]), code = Int(codes[row * 3 + 2])
            var node = 0
            for i in stride(from: length - 1, through: 0, by: -1) {
                let bit = (code >> i) & 1
                let slot = node * 2 + bit
                if i == 0 {
                    children[slot] = -(value + offset) - 1
                } else {
                    if children[slot] <= 0 {
                        children.append(contentsOf: [0, 0])
                        children[slot] = Int32(children.count / 2 - 1)
                    }
                    node = Int(children[slot])
                }
            }
        }
        return Tree(children: children, offset: offset)
    }

    static func decode(_ tree: Tree, _ r: inout DTSBits) throws -> Int {
        var node = 0
        for _ in 0..<24 {
            let next = tree.children[node * 2 + Int(r.readBit())]
            if next < 0 { return Int(-(next + 1) - tree.offset) }
            if next == 0 { break }
            node = Int(next)
        }
        throw DTSDecoder.DecodeError("ошибка кода Хаффмана")
    }

    static let bitAllocation = ["A12", "B12", "C12", "D12", "E12"].map(build)
    static let transient = ["A4", "B4", "C4", "D4"].map(build)
    static let scales = ["SA129", "SB129", "SC129", "SD129", "SE129"].map(build)
    private static let audioBooks: [Int: [Tree]] = {
        var out: [Int: [Tree]] = [:]
        for levels in [3, 5, 7, 9, 13, 17, 25, 33, 65, 129] {
            out[levels] = ["A", "B", "C", "D", "E", "F", "G"].compactMap { letter in
                DTSTables.huffmanIndex["\(letter)\(levels)"] != nil ? build("\(letter)\(levels)") : nil
            }
        }
        return out
    }()

    static func audio(levels: Int, book: Int) -> Tree {
        let books = audioBooks[levels] ?? []
        return books[min(book, max(books.count - 1, 0))]
    }
}

/// 32-полосная синтезирующая банка фильтров DTS (C.3.6) со своей историей.
nonisolated struct QMFBank: Sendable {
    private var x = [Float](repeating: 0, count: 512)
    private var z = [Float](repeating: 0, count: 64)

    private static let gain = Float(128 * 2.0.squareRoot())
    private static let cos1: [Float] = table { k, i in cos(Double((2 * i + 1) * (2 * k + 1)) * Double.pi / 64) }
    private static let cos2: [Float] = table { k, i in cos(Double(i * (2 * k + 1)) * Double.pi / 32) }
    private static let sumScale: [Float] = (0..<16).map { k in
        let angle = Double(2 * k + 1) * Double.pi / 128
        return Float(0.25 / (2 * cos(angle)))
    }
    private static let diffScale: [Float] = (0..<16).map { k in
        let angle = Double(2 * k + 1) * Double.pi / 128
        return Float(-0.25 / (2 * sin(angle)))
    }

    private static func table(_ f: (Int, Int) -> Double) -> [Float] {
        var out: [Float] = []
        for k in 0..<16 { for i in 0..<16 { out.append(Float(f(k, i))) } }
        return out
    }

    mutating func synthesize(_ subbands: [[Float]], bands: Int, samples: Int, coefficients: [Float], into out: inout [Float], at start: Int) {
        var input = [Float](repeating: 0, count: 32)
        var a = [Float](repeating: 0, count: 16), b = [Float](repeating: 0, count: 16)
        for s in 0..<samples {
            for i in 0..<32 { input[i] = i < bands ? subbands[i][s] : 0 }
            for k in 0..<16 {
                var accA: Float = 0, accB: Float = 0
                for i in 0..<16 {
                    accA += (input[2 * i] + input[2 * i + 1]) * Self.cos1[k * 16 + i]
                    accB += (i > 0 ? input[2 * i] + input[2 * i - 1] : input[0]) * Self.cos2[k * 16 + i]
                }
                a[k] = accA
                b[k] = accB
            }
            for k in 0..<16 {
                x[k] = Self.sumScale[k] * (a[k] + b[k])
                x[31 - k] = Self.diffScale[k] * (a[k] - b[k])
            }
            for i in 0..<32 {
                let k = 31 - i
                var acc1: Float = 0, acc2: Float = 0
                var j = 0
                while j < 512 {
                    acc1 += coefficients[i + j] * (x[i + j] - x[j + k])
                    acc2 += coefficients[32 + i + j] * (-x[i + j] - x[j + k])
                    j += 64
                }
                z[i] += acc1
                z[32 + i] += acc2
            }
            let base = start + s * 32
            // Нормировка банки: псевдокод стандарта оставляет её «на усмотрение» (rScale), для шкалы
            // 24-битного PCM это 2^7,5 — проверено сравнением с эталонным декодером.
            for i in 0..<32 where base + i < out.count { out[base + i] = z[i] * Self.gain }
            for i in stride(from: 511, through: 32, by: -1) { x[i] = x[i - 32] }
            for i in 0..<32 {
                z[i] = z[i + 32]
                z[i + 32] = 0
            }
        }
    }
}
