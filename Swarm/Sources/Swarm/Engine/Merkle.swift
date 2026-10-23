import Foundation

/// Деревья Меркла BitTorrent v2 (BEP 52): листья — SHA-256 блоков по 16 КБ, недостающие листья — нули.
nonisolated enum Merkle {
    static let blockSize = 16 * 1024
    static let zero = Data(repeating: 0, count: 32)

    static func nextPowerOfTwo(_ n: Int) -> Int {
        var p = 1
        while p < n { p <<= 1 }
        return p
    }

    static func log2(_ n: Int) -> Int { n <= 1 ? 0 : Int.bitWidth - 1 - n.leadingZeroBitCount }

    /// Корень поддерева из 2^levels нулевых листьев.
    static func padHash(levels: Int) -> Data {
        var h = zero
        for _ in 0..<levels { h = Hashing.sha256(h, h) }
        return h
    }

    /// SHA-256 каждого блока по 16 КБ (последний может быть короче).
    static func blockHashes(_ data: Data) -> [Data] {
        var out: [Data] = []
        var at = data.startIndex
        while at < data.endIndex {
            let end = min(at + blockSize, data.endIndex)
            out.append(Hashing.sha256(data[at..<end]))
            at = end
        }
        return out
    }

    /// Корень дерева над `leaves`, дополненными до `width` (степень двойки) хешами `pad`.
    static func root(_ leaves: [Data], width: Int, pad: Data = zero) -> Data {
        guard !leaves.isEmpty else { return pad }
        var layer = leaves
        var padding = pad
        var w = max(1, width)
        while layer.count < w { layer.append(padding) }
        while w > 1 {
            var next: [Data] = []
            next.reserveCapacity(w / 2)
            for i in stride(from: 0, to: w, by: 2) { next.append(Hashing.sha256(layer[i], layer[i + 1])) }
            layer = next
            padding = Hashing.sha256(padding, padding)
            w /= 2
        }
        return layer[0]
    }

    /// Слой кусков полный и сходится к корню файла.
    static func verifyLayer(_ layer: [Data], root expected: Data, blocksPerPiece: Int) -> Bool {
        root(layer, width: nextPowerOfTwo(layer.count), pad: padHash(levels: log2(blocksPerPiece))) == expected
    }

    /// Все слои над `base` (дополненным до степени двойки): [base, ..., [root]].
    static func layers(_ base: [Data], pad: Data) -> [[Data]] {
        var layer = base
        let width = nextPowerOfTwo(base.count)
        var padding = pad
        while layer.count < width { layer.append(padding) }
        var result = [layer]
        while layer.count > 1 {
            var next: [Data] = []
            for i in stride(from: 0, to: layer.count, by: 2) { next.append(Hashing.sha256(layer[i], layer[i + 1])) }
            padding = Hashing.sha256(padding, padding)
            layer = next
            result.append(layer)
        }
        return result
    }

    /// Ответ на запрос хешей: `length` хешей слоя начиная с `index` и «дядья» от вершины
    /// запрошенного поддерева к корню (первые log2(length) − 1 слоёв доказательства опускаются).
    static func proof(layers tree: [[Data]], index: Int, length: Int, proofLayers: Int) -> Data? {
        let base = tree[0]
        guard length >= 2, length & (length - 1) == 0, length <= 512, index >= 0, index % length == 0,
              index + length <= base.count else { return nil }
        var out = Data()
        for h in base[index..<(index + length)] { out.append(h) }
        let subtree = log2(length)
        let uncles = min(max(0, proofLayers - (subtree - 1)), tree.count - 1 - subtree)
        var pos = index / length
        for level in subtree..<(subtree + uncles) {
            out.append(tree[level][pos ^ 1])
            pos /= 2
        }
        return out
    }

    /// Проверка ответа `hashes`: из хешей и дядьёв должен получиться корень файла.
    static func verify(hashes: [Data], uncles: [Data], index: Int, root expected: Data) -> Bool {
        guard hashes.count >= 2, hashes.count & (hashes.count - 1) == 0 else { return false }
        var node = root(hashes, width: hashes.count)
        var pos = index / hashes.count
        for u in uncles {
            node = pos & 1 == 0 ? Hashing.sha256(node, u) : Hashing.sha256(u, node)
            pos /= 2
        }
        return pos == 0 && node == expected
    }
}
