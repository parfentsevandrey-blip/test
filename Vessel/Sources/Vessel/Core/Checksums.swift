import CryptoKit
import Foundation

/// Разбор файлов контрольных сумм всех видов, которые встречаются у дистрибутивов:
/// «сумма  имя», «сумма *имя», «SHA256 (имя) = сумма», «BLAKE2b (iso/имя) = сумма»,
/// подписанные PGP файлы и файлы с одной суммой (рядом с «Current»-ссылкой).
nonisolated enum Checksums {
    static func find(_ fileName: String, in text: String, kind: HashKind) -> String? {
        let lines = text.split(whereSeparator: \.isNewline).map(String.init)
        for line in lines where mentions(line, fileName) {
            if let hex = hexTokens(line, length: kind.hexLength).first { return hex }
        }
        // Файл с одной суммой: имя внутри может отличаться («…-Current.iso» → «…-Snapshot20261020-Media.iso»).
        let all = lines.filter { !$0.hasPrefix("#") }.flatMap { hexTokens($0, length: kind.hexLength) }
        return Set(all).count == 1 ? all.first : nil
    }

    /// Имя файла упомянуто отдельным словом (не как часть более длинного имени).
    static func mentions(_ line: String, _ name: String) -> Bool {
        var search = line[...]
        while let r = search.range(of: name) {
            let before = r.lowerBound == line.startIndex ? nil : line[line.index(before: r.lowerBound)]
            let after = r.upperBound == line.endIndex ? nil : line[r.upperBound]
            let okBefore = before == nil || " *(/\t".contains(before!)
            let okAfter = after == nil || " )\t\r".contains(after!)
            if okBefore && okAfter { return true }
            search = line[r.upperBound...]
        }
        return false
    }

    static func hexTokens(_ line: String, length: Int) -> [String] {
        line.split(whereSeparator: { !$0.isHexDigit }).map(String.init).filter { $0.count == length }.map { $0.lowercased() }
    }
}

/// Хеш, который считается на лету во время загрузки.
nonisolated struct StreamHasher {
    private var sha = SHA256()
    private var blake = BLAKE2b()
    let kind: HashKind

    init(_ kind: HashKind) { self.kind = kind }

    mutating func update(_ data: Data) {
        switch kind {
        case .sha256: sha.update(data: data)
        case .blake2b: blake.update(data)
        }
    }

    func hex() -> String {
        switch kind {
        case .sha256: sha.finalize().map { String(format: "%02x", $0) }.joined()
        case .blake2b: blake.finalize().map { String(format: "%02x", $0) }.joined()
        }
    }
}

/// BLAKE2b-512 по RFC 7693. В CryptoKit его нет, а Archboot публикует только такие суммы.
nonisolated struct BLAKE2b {
    private static let iv: [UInt64] = [
        0x6a09e667f3bcc908, 0xbb67ae8584caa73b, 0x3c6ef372fe94f82b, 0xa54ff53a5f1d36f1,
        0x510e527fade682d1, 0x9b05688c2b3e6c1f, 0x1f83d9abfb41bd6b, 0x5be0cd19137e2179,
    ]
    private static let sigma: [[Int]] = [
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
        [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
        [11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4],
        [7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8],
        [9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13],
        [2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9],
        [12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11],
        [13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10],
        [6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5],
        [10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0],
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
        [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
    ]

    private var h: [UInt64]
    private var counter: (UInt64, UInt64) = (0, 0)
    private var buffer = [UInt8](repeating: 0, count: 128)
    private var filled = 0
    private let outLength: Int

    init(outLength: Int = 64) {
        self.outLength = outLength
        h = Self.iv
        h[0] ^= 0x0101_0000 ^ UInt64(outLength)
    }

    mutating func update(_ data: Data) {
        data.withUnsafeBytes { raw in
            guard let base = raw.bindMemory(to: UInt8.self).baseAddress else { return }
            update(UnsafeBufferPointer(start: base, count: raw.count))
        }
    }

    mutating func update(_ bytes: UnsafeBufferPointer<UInt8>) {
        var i = 0
        let n = bytes.count
        while i < n {
            // Последний блок сжимается только в finalize — с флагом «последний».
            if filled == 128 {
                increment(128)
                compress(last: false)
                filled = 0
            }
            let take = min(128 - filled, n - i)
            let at = filled, from = i
            buffer.withUnsafeMutableBufferPointer { dst in
                for k in 0..<take { dst[at + k] = bytes[from + k] }
            }
            filled += take
            i += take
        }
    }

    func finalize() -> [UInt8] {
        var copy = self
        copy.increment(UInt64(copy.filled))
        for k in copy.filled..<128 { copy.buffer[k] = 0 }
        copy.compress(last: true)
        var out: [UInt8] = []
        out.reserveCapacity(64)
        for word in copy.h {
            for b in 0..<8 { out.append(UInt8(truncatingIfNeeded: word >> (8 * UInt64(b)))) }
        }
        return Array(out.prefix(outLength))
    }

    private mutating func increment(_ by: UInt64) {
        let (low, overflow) = counter.0.addingReportingOverflow(by)
        counter = (low, counter.1 &+ (overflow ? 1 : 0))
    }

    private mutating func compress(last: Bool) {
        var m = [UInt64](repeating: 0, count: 16)
        buffer.withUnsafeBufferPointer { b in
            for i in 0..<16 {
                var w: UInt64 = 0
                for k in 0..<8 { w |= UInt64(b[i * 8 + k]) << (8 * UInt64(k)) }
                m[i] = w
            }
        }
        var v = h + Self.iv
        v[12] ^= counter.0
        v[13] ^= counter.1
        if last { v[14] = ~v[14] }
        @inline(__always) func rotr(_ x: UInt64, _ n: UInt64) -> UInt64 { (x >> n) | (x << (64 - n)) }
        @inline(__always) func g(_ v: inout [UInt64], _ a: Int, _ b: Int, _ c: Int, _ d: Int, _ x: UInt64, _ y: UInt64) {
            v[a] = v[a] &+ v[b] &+ x; v[d] = rotr(v[d] ^ v[a], 32)
            v[c] = v[c] &+ v[d];      v[b] = rotr(v[b] ^ v[c], 24)
            v[a] = v[a] &+ v[b] &+ y; v[d] = rotr(v[d] ^ v[a], 16)
            v[c] = v[c] &+ v[d];      v[b] = rotr(v[b] ^ v[c], 63)
        }
        for r in 0..<12 {
            let s = Self.sigma[r]
            g(&v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            g(&v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            g(&v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            g(&v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            g(&v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            g(&v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            g(&v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            g(&v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for i in 0..<8 { h[i] ^= v[i] ^ v[i + 8] }
    }
}
