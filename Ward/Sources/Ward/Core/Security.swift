import CryptoKit
import Foundation
import Security

/// Сертификат сайта: кто выдал, на какой срок.
nonisolated struct CertificateInfo: Equatable, Sendable {
    var subject: String
    var issuer: String
    var notBefore: Date?
    var notAfter: Date?

    /// Выпущен совсем недавно: так часто выглядят однодневные мошеннические сайты.
    var isFresh: Bool { notBefore.map { Date().timeIntervalSince($0) < 3 * 86_400 } ?? false }

    static func from(_ trust: SecTrust) -> CertificateInfo? {
        guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate], let leaf = chain.first else { return nil }
        let subject = (SecCertificateCopySubjectSummary(leaf) as String?) ?? ""
        let keys = [kSecOIDX509V1IssuerName, kSecOIDX509V1ValidityNotBefore, kSecOIDX509V1ValidityNotAfter] as CFArray
        let values = (SecCertificateCopyValues(leaf, keys, nil) as? [String: Any]) ?? [:]
        func date(_ key: CFString) -> Date? {
            guard let entry = values[key as String] as? [String: Any], let n = entry[kSecPropertyKeyValue as String] as? NSNumber else { return nil }
            return Date(timeIntervalSinceReferenceDate: n.doubleValue)
        }
        var issuer = ""
        if let entry = values[kSecOIDX509V1IssuerName as String] as? [String: Any],
           let parts = entry[kSecPropertyKeyValue as String] as? [[String: Any]] {
            func part(_ oid: CFString) -> String? {
                parts.first { ($0[kSecPropertyKeyLabel as String] as? String) == oid as String }?[kSecPropertyKeyValue as String] as? String
            }
            let cn = part(kSecOIDCommonName) ?? ""
            let org = part(kSecOIDOrganizationName) ?? ""
            issuer = org.isEmpty ? cn : (cn.isEmpty || cn == org ? org : "\(cn) (\(org))")
        }
        return CertificateInfo(subject: subject, issuer: issuer, notBefore: date(kSecOIDX509V1ValidityNotBefore), notAfter: date(kSecOIDX509V1ValidityNotAfter))
    }
}

/// Зашифрованные файлы: история и открытые вкладки. Ключ AES-256 хранится в Связке ключей macOS,
/// поэтому копия диска или резервная копия без вашего пароля не раскроет, где вы были.
nonisolated enum SecureStore {
    static let service = "app.ward.browser"
    static let account = "history-key"
    nonisolated(unsafe) private static var cachedKey: SymmetricKey?
    private static let lock = NSLock()

    static func key() -> SymmetricKey? {
        lock.lock()
        defer { lock.unlock() }
        if let cachedKey { return cachedKey }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data, data.count == 32 {
            cachedKey = SymmetricKey(data: data)
            return cachedKey
        }
        let fresh = SymmetricKey(size: .bits256)
        let data = fresh.withUnsafeBytes { Data($0) }
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account,
            kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked,
            kSecAttrLabel as String: "Ward — ключ истории",
        ]
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account] as CFDictionary)
        guard SecItemAdd(add as CFDictionary, nil) == errSecSuccess else { return nil }
        cachedKey = fresh
        return fresh
    }

    /// Сохранить зашифрованно. false — ключа нет (Связка ключей недоступна): тогда данные не пишутся на диск вовсе.
    @discardableResult
    static func save<T: Encodable>(_ value: T, _ name: String) -> Bool {
        guard let key = key() else { return false }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let plain = try? encoder.encode(value), let sealed = try? AES.GCM.seal(plain, using: key).combined else { return false }
        do {
            try sealed.write(to: Storage.url(name + ".sealed"), options: .atomic)
            Storage.remove(name)
            return true
        } catch {
            return false
        }
    }

    static func load<T: Decodable>(_ type: T.Type, _ name: String) -> T? {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        if let data = try? Data(contentsOf: Storage.url(name + ".sealed")), let key = key(),
           let box = try? AES.GCM.SealedBox(combined: data), let plain = try? AES.GCM.open(box, using: key) {
            return try? decoder.decode(T.self, from: plain)
        }
        // Файл прошлой версии, ещё не зашифрованный: прочитать, зашифровать, удалить открытую копию.
        if let value = Storage.load(T.self, name) {
            if let encodable = value as? Encodable { _ = saveAny(encodable, name) }
            return value
        }
        return nil
    }

    private static func saveAny(_ value: Encodable, _ name: String) -> Bool {
        func open<E: Encodable>(_ v: E) -> Bool { save(v, name) }
        return open(value)
    }

    static func remove(_ name: String) {
        Storage.remove(name)
        Storage.remove(name + ".sealed")
    }
}
