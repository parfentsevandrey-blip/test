import Foundation

nonisolated struct BencodeError: LocalizedError, Sendable {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/// Значение bencode. Ключи словаря — байтовые строки; храним их как Latin-1,
/// это взаимно однозначно отображает любые байты и сохраняет порядок сортировки.
nonisolated indirect enum BValue: Sendable, Equatable {
    case int(Int64)
    case bytes(Data)
    case list([BValue])
    case dict([String: BValue])

    subscript(key: String) -> BValue? {
        if case .dict(let d) = self { return d[key] }
        return nil
    }

    var int: Int64? { if case .int(let v) = self { return v }; return nil }
    var data: Data? { if case .bytes(let v) = self { return v }; return nil }
    var list: [BValue]? { if case .list(let v) = self { return v }; return nil }
    var dict: [String: BValue]? { if case .dict(let v) = self { return v }; return nil }

    /// Текст в UTF-8 (имена файлов, сообщения трекеров).
    var string: String? { data.map { String(decoding: $0, as: UTF8.self) } }

    static func str(_ s: String) -> BValue { .bytes(Data(s.utf8)) }

    var encoded: Data {
        var out = Data()
        encode(into: &out)
        return out
    }

    private func encode(into out: inout Data) {
        switch self {
        case .int(let v):
            out.append(contentsOf: Array("i\(v)e".utf8))
        case .bytes(let d):
            out.append(contentsOf: Array("\(d.count):".utf8))
            out.append(d)
        case .list(let items):
            out.append(UInt8(ascii: "l"))
            for item in items { item.encode(into: &out) }
            out.append(UInt8(ascii: "e"))
        case .dict(let d):
            out.append(UInt8(ascii: "d"))
            let keys = d.keys.sorted { Bencode.keyBytes($0).lexicographicallyPrecedes(Bencode.keyBytes($1)) }
            for key in keys {
                let raw = Bencode.keyBytes(key)
                out.append(contentsOf: Array("\(raw.count):".utf8))
                out.append(contentsOf: raw)
                d[key]!.encode(into: &out)
            }
            out.append(UInt8(ascii: "e"))
        }
    }
}

nonisolated enum Bencode {
    static func keyBytes(_ key: String) -> [UInt8] {
        key.unicodeScalars.map { UInt8(truncatingIfNeeded: $0.value) }
    }

    static func key(_ bytes: ArraySlice<UInt8>) -> String {
        String(String.UnicodeScalarView(bytes.map { Unicode.Scalar($0) }))
    }

    static func decode(_ data: Data) throws -> BValue {
        var parser = Parser(bytes: [UInt8](data), capture: nil)
        let value = try parser.value()
        return value
    }

    /// Декодирует значение в начале буфера и возвращает, сколько байт оно заняло
    /// (в ut_metadata за словарём сразу идут сырые данные).
    static func decodePrefix(_ data: Data) throws -> (BValue, Int) {
        var parser = Parser(bytes: [UInt8](data), capture: nil)
        let value = try parser.value()
        return (value, parser.index)
    }

    /// Декодирует и запоминает точные байты значения по ключу верхнего уровня
    /// (info-хеш считается от сырых байт словаря info, а не от перекодированных).
    static func decode(_ data: Data, capturing key: String) throws -> (BValue, Data?) {
        let bytes = [UInt8](data)
        var parser = Parser(bytes: bytes, capture: key)
        let value = try parser.value()
        return (value, parser.captured.map { Data(bytes[$0]) })
    }

    private struct Parser {
        let bytes: [UInt8]
        let capture: String?
        var index = 0
        var depth = 0
        var captured: Range<Int>?

        init(bytes: [UInt8], capture: String?) {
            self.bytes = bytes
            self.capture = capture
        }

        mutating func value() throws -> BValue {
            guard index < bytes.count else { throw BencodeError("Неожиданный конец данных") }
            guard depth < 64 else { throw BencodeError("Слишком глубокая вложенность") }
            switch bytes[index] {
            case UInt8(ascii: "i"):
                index += 1
                guard let end = bytes[index...].firstIndex(of: UInt8(ascii: "e")),
                      let v = Int64(String(decoding: bytes[index..<end], as: UTF8.self)) else {
                    throw BencodeError("Неверное число")
                }
                index = end + 1
                return .int(v)
            case UInt8(ascii: "l"):
                index += 1
                depth += 1
                defer { depth -= 1 }
                var items: [BValue] = []
                while true {
                    guard index < bytes.count else { throw BencodeError("Незакрытый список") }
                    if bytes[index] == UInt8(ascii: "e") { index += 1; break }
                    items.append(try value())
                }
                return .list(items)
            case UInt8(ascii: "d"):
                index += 1
                depth += 1
                defer { depth -= 1 }
                var dict: [String: BValue] = [:]
                while true {
                    guard index < bytes.count else { throw BencodeError("Незакрытый словарь") }
                    if bytes[index] == UInt8(ascii: "e") { index += 1; break }
                    let keyRange = try byteString()
                    let key = Bencode.key(bytes[keyRange])
                    let start = index
                    let v = try value()
                    if depth == 1, key == capture { captured = start..<index }
                    dict[key] = v
                }
                return .dict(dict)
            case UInt8(ascii: "0")...UInt8(ascii: "9"):
                let range = try byteString()
                return .bytes(Data(bytes[range]))
            default:
                throw BencodeError("Неверный байт bencode")
            }
        }

        mutating func byteString() throws -> Range<Int> {
            guard let colon = bytes[index...].firstIndex(of: UInt8(ascii: ":")),
                  let length = Int(String(decoding: bytes[index..<colon], as: UTF8.self)),
                  length >= 0, colon + 1 + length <= bytes.count else {
                throw BencodeError("Неверная строка")
            }
            let range = (colon + 1)..<(colon + 1 + length)
            index = range.upperBound
            return range
        }
    }
}
