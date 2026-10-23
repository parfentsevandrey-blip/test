import Foundation

/// Режим шифрования соединений с пирами.
nonisolated enum EncryptionMode: String, Codable, Sendable, CaseIterable, Identifiable {
    /// Исходящие — с шифрованием, но если пир его не умеет, повторяем без него. Входящие принимаем любые.
    case prefer
    /// Только зашифрованные соединения: открытые входящие отклоняются, исходящие без RC4 не устанавливаются.
    case require
    /// Исходящие без шифрования (входящие зашифрованные всё равно принимаются).
    case off

    var id: String { rawValue }

    var title: String {
        switch self {
        case .prefer: "Предпочитать"
        case .require: "Только шифрованные"
        case .off: "Не использовать"
        }
    }
}

nonisolated struct EncryptionError: LocalizedError, Sendable {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/// Message Stream Encryption / Protocol Encryption (MSE/PE, «шифрование протокола»).
///
/// Диффи — Хеллман (768 бит) даёт общий секрет S, из него и info-хеша выводятся ключи RC4 для двух
/// направлений. Провайдер видит случайные байты вместо узнаваемого «BitTorrent protocol», и DPI
/// не может опознать и порезать торрент-трафик по сигнатуре.
nonisolated enum MSE {
    static let vc = Data(repeating: 0, count: 8)
    static let plaintext: UInt32 = 0x01
    static let rc4: UInt32 = 0x02
    private static let maxPad = 512

    private static func key(_ label: String, _ s: Data, _ skey: Data) -> Data {
        Hashing.sha1(Data(label.utf8), s, skey)
    }

    private static func xor(_ a: Data, _ b: Data) -> Data { Data(zip(a, b).map { $0 ^ $1 }) }

    private static func randomPad(_ max: Int) -> Data {
        Data((0..<Int.random(in: 0...max)).map { _ in UInt8.random(in: 0...255) })
    }

    private static func be32(_ d: Data) -> UInt32 { d.readBE32(0) }
    private static func be16(_ d: Data) -> Int { Int(d[d.startIndex]) << 8 | Int(d[d.startIndex + 1]) }

    /// Сторона, начавшая соединение. После успеха соединение уже шифрует/расшифровывает само
    /// (если выбран RC4), и можно отправлять обычное рукопожатие BitTorrent.
    static func initiate(_ conn: PeerConnection, infoHash: Data, allowPlaintext: Bool, timeout: TimeInterval = 12) async throws {
        let priv = DiffieHellman.makePrivateKey()
        conn.sendRaw(DiffieHellman.publicKey(priv) + randomPad(128))
        let yb: Data
        do {
            yb = try await conn.read(DiffieHellman.keyBytes, timeout: timeout)
        } catch {
            throw EncryptionError("Пир не поддерживает шифрование")
        }
        guard let s = DiffieHellman.sharedSecret(yb, priv) else { throw EncryptionError("Неверный ключ пира") }
        let encrypt = RC4(key: key("keyA", s, infoHash))
        let decrypt = RC4(key: key("keyB", s, infoHash))

        var out = Hashing.sha1(Data("req1".utf8), s)
        out.append(xor(Hashing.sha1(Data("req2".utf8), infoHash), Hashing.sha1(Data("req3".utf8), s)))
        var plain = vc
        plain.appendBE(allowPlaintext ? rc4 | plaintext : rc4)
        plain.appendBE(UInt16(0)) // len(PadC)
        plain.appendBE(UInt16(0)) // len(IA): рукопожатие отправим после выбора метода
        out.append(encrypt.apply(plain))
        conn.sendRaw(out)

        // Ответ B: Yb, PadB (до 512 байт), затем ENCRYPT(VC) — ищем его как метку синхронизации.
        let marker = RC4(key: key("keyB", s, infoHash)).apply(vc)
        do {
            _ = try await conn.scan(for: [UInt8](marker), within: maxPad, timeout: timeout)
        } catch {
            throw EncryptionError("Пир не ответил на шифрованное рукопожатие")
        }
        _ = decrypt.apply(vc)
        let header = decrypt.apply(try await conn.read(6, timeout: timeout))
        let select = be32(header.prefix(4))
        let padLength = be16(header.dropFirst(4))
        guard padLength <= maxPad else { throw EncryptionError("Неверное шифрованное рукопожатие") }
        if padLength > 0 { _ = decrypt.apply(try await conn.read(padLength, timeout: timeout)) }

        if select == rc4 {
            conn.setCiphers(encrypt: encrypt, decrypt: decrypt)
        } else if !(select == plaintext && allowPlaintext) {
            throw EncryptionError("Пир выбрал неподдерживаемый метод шифрования")
        }
    }

    /// Принимающая сторона. `hashes` — info-хеши раздач, которые мы ведём (SKEY ищется среди них).
    /// Возвращает выбранный хеш; расшифрованное начало потока (IA) возвращается в буфер соединения.
    static func respond(_ conn: PeerConnection, hashes: [Data], requireRC4: Bool, timeout: TimeInterval = 12) async throws -> Data {
        let ya = try await conn.read(DiffieHellman.keyBytes, timeout: timeout)
        let priv = DiffieHellman.makePrivateKey()
        guard let s = DiffieHellman.sharedSecret(ya, priv) else { throw EncryptionError("Неверный ключ пира") }
        conn.sendRaw(DiffieHellman.publicKey(priv) + randomPad(128))

        _ = try await conn.scan(for: [UInt8](Hashing.sha1(Data("req1".utf8), s)), within: maxPad, timeout: timeout)
        let mixed = try await conn.read(20, timeout: timeout)
        let wanted = xor(mixed, Hashing.sha1(Data("req3".utf8), s))
        guard let skey = hashes.first(where: { Hashing.sha1(Data("req2".utf8), $0) == wanted }) else {
            throw EncryptionError("Раздача не найдена")
        }
        let decrypt = RC4(key: key("keyA", s, skey))
        let encrypt = RC4(key: key("keyB", s, skey))

        let header = decrypt.apply(try await conn.read(14, timeout: timeout))
        guard header.prefix(8) == vc else { throw EncryptionError("Неверное шифрованное рукопожатие") }
        let provide = be32(header.dropFirst(8).prefix(4))
        let padLength = be16(header.dropFirst(12))
        guard padLength <= maxPad else { throw EncryptionError("Неверное шифрованное рукопожатие") }
        if padLength > 0 { _ = decrypt.apply(try await conn.read(padLength, timeout: timeout)) }
        let iaLength = be16(decrypt.apply(try await conn.read(2, timeout: timeout)))
        guard iaLength <= 4096 else { throw EncryptionError("Неверное шифрованное рукопожатие") }
        let ia = iaLength > 0 ? decrypt.apply(try await conn.read(iaLength, timeout: timeout)) : Data()

        let select: UInt32
        if provide & rc4 != 0 { select = rc4 }
        else if provide & plaintext != 0, !requireRC4 { select = plaintext }
        else { throw EncryptionError("Нет общего метода шифрования") }

        var reply = vc
        reply.appendBE(select)
        reply.appendBE(UInt16(0))
        conn.sendRaw(encrypt.apply(reply))
        if select == rc4 { conn.setCiphers(encrypt: encrypt, decrypt: decrypt) }
        if !ia.isEmpty { conn.unread(ia) }
        return skey
    }
}
