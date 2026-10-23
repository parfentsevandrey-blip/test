import Foundation

/// Распаковка gzip встроенным в систему DEFLATE (NSData.decompressed, алгоритм .zlib — «сырой» DEFLATE).
nonisolated enum Gzip {
    static func decompress(_ data: Data) throws -> Data {
        let b = [UInt8](data)
        guard b.count > 18, b[0] == 0x1F, b[1] == 0x8B, b[2] == 8 else { throw PeerError("Не gzip") }
        let flags = b[3]
        var at = 10
        if flags & 0x04 != 0 { at += 2 + (Int(b[at]) | Int(b[at + 1]) << 8) }
        if flags & 0x08 != 0 { while at < b.count, b[at] != 0 { at += 1 }; at += 1 }
        if flags & 0x10 != 0 { while at < b.count, b[at] != 0 { at += 1 }; at += 1 }
        if flags & 0x02 != 0 { at += 2 }
        guard at < b.count - 8 else { throw PeerError("Повреждённый gzip") }
        let deflated = Data(b[at..<(b.count - 8)])
        return try (deflated as NSData).decompressed(using: .zlib) as Data
    }
}
