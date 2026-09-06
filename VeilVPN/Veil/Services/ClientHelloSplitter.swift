import Foundation

/// Fragments a TLS ClientHello so that throttling/blocking DPI boxes, which typically inspect only
/// the first TCP segment or the first TLS record for the SNI host name, never see it whole.
/// The same idea as GoodbyeDPI / ByeDPI / zapret "split" and "tlsrec" modes, in user space.
enum ClientHelloSplitter {
    /// True when the buffer starts with a TLS handshake record carrying a ClientHello.
    static func isClientHello(_ data: Data) -> Bool {
        let bytes = [UInt8](data.prefix(6))
        return bytes.count >= 6 && bytes[0] == 0x16 && bytes[1] == 0x03 && bytes[5] == 0x01
    }

    /// Total length (header + payload) of the first TLS record, if the header is complete.
    static func firstRecordLength(_ data: Data) -> Int? {
        let bytes = [UInt8](data.prefix(5))
        guard bytes.count == 5 else { return nil }
        return 5 + (Int(bytes[3]) << 8 | Int(bytes[4]))
    }

    /// Byte range of the SNI host name inside a complete ClientHello record.
    static func sniRange(in bytes: [UInt8]) -> Range<Int>? {
        // record header (5) + handshake header (4) + client_version (2) + random (32)
        var index = 43
        guard bytes.count > index else { return nil }
        let sessionIDLength = Int(bytes[index])
        index += 1 + sessionIDLength
        guard bytes.count >= index + 2 else { return nil }
        let cipherSuitesLength = Int(bytes[index]) << 8 | Int(bytes[index + 1])
        index += 2 + cipherSuitesLength
        guard bytes.count >= index + 1 else { return nil }
        let compressionLength = Int(bytes[index])
        index += 1 + compressionLength
        guard bytes.count >= index + 2 else { return nil }
        let extensionsLength = Int(bytes[index]) << 8 | Int(bytes[index + 1])
        index += 2
        let extensionsEnd = min(bytes.count, index + extensionsLength)
        while index + 4 <= extensionsEnd {
            let type = Int(bytes[index]) << 8 | Int(bytes[index + 1])
            let length = Int(bytes[index + 2]) << 8 | Int(bytes[index + 3])
            index += 4
            if type == 0 { // server_name
                // server_name_list length (2), name_type (1), host_name length (2), host_name
                guard index + 5 <= extensionsEnd else { return nil }
                let nameLength = Int(bytes[index + 3]) << 8 | Int(bytes[index + 4])
                let start = index + 5
                guard nameLength > 0, start + nameLength <= extensionsEnd else { return nil }
                return start..<(start + nameLength)
            }
            index += length
        }
        return nil
    }

    static func sniHost(in record: Data) -> String? {
        let bytes = [UInt8](record)
        guard let range = sniRange(in: bytes) else { return nil }
        return String(decoding: bytes[range], as: UTF8.self)
    }

    /// The pieces to write, in order, instead of the original record.
    static func chunks(for record: Data, strategy: DPIStrategy) -> [Data] {
        let bytes = [UInt8](record)
        guard bytes.count > 6 else { return [record] }
        let sni = sniRange(in: bytes)

        switch strategy {
        case .firstByte:
            return [Data(bytes[0..<1]), Data(bytes[1...])]

        case .segmentAtSNI:
            let position = sni.map { $0.lowerBound + $0.count / 2 } ?? 1
            guard position > 0, position < bytes.count else { return [record] }
            return [Data(bytes[0..<position]), Data(bytes[position...])]

        case .recordAtSNI, .recordAndSegmentAtSNI:
            // Split the handshake payload into two TLS records; TLS allows a handshake message to span records.
            let position = sni.map { $0.lowerBound + $0.count / 2 } ?? 6
            guard position > 5, position < bytes.count else { return [record] }
            let version = (bytes[1], bytes[2])
            func makeRecord(_ payload: ArraySlice<UInt8>) -> Data {
                var data = Data([0x16, version.0, version.1, UInt8((payload.count >> 8) & 0xFF), UInt8(payload.count & 0xFF)])
                data.append(contentsOf: payload)
                return data
            }
            let first = makeRecord(bytes[5..<position])
            let second = makeRecord(bytes[position...])
            if strategy == .recordAtSNI {
                return [first + second]
            }
            return [first, second]
        }
    }
}
