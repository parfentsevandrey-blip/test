import CryptoKit
import Foundation

/// Хеши, которыми пользуется движок: SHA-1 (v1, DHT, шифрование) и SHA-256 (v2).
nonisolated enum Hashing {
    static func sha1(_ parts: Data...) -> Data {
        var hasher = Insecure.SHA1()
        for p in parts { hasher.update(data: p) }
        return Data(hasher.finalize())
    }

    static func sha256(_ data: Data) -> Data {
        Data(SHA256.hash(data: data))
    }

    static func sha256(_ a: Data, _ b: Data) -> Data {
        var hasher = SHA256()
        hasher.update(data: a)
        hasher.update(data: b)
        return Data(hasher.finalize())
    }
}

/// Потоковый шифр RC4 для MSE. Первые 1024 байта гаммы отбрасываются, как требует протокол.
nonisolated final class RC4: @unchecked Sendable {
    private var s = [UInt8](repeating: 0, count: 256)
    private var i: UInt8 = 0
    private var j: UInt8 = 0

    init(key: Data, discard: Int = 1024) {
        let k = [UInt8](key)
        for n in 0..<256 { s[n] = UInt8(n) }
        var jj: UInt8 = 0
        for n in 0..<256 {
            jj = jj &+ s[n] &+ k[n % k.count]
            s.swapAt(n, Int(jj))
        }
        var skip = [UInt8](repeating: 0, count: discard)
        process(&skip)
    }

    func process(_ bytes: inout [UInt8]) {
        var i = self.i, j = self.j
        s.withUnsafeMutableBufferPointer { s in
            bytes.withUnsafeMutableBufferPointer { b in
                for n in b.indices {
                    i = i &+ 1
                    j = j &+ s[Int(i)]
                    let t = s[Int(i)]
                    s[Int(i)] = s[Int(j)]
                    s[Int(j)] = t
                    b[n] ^= s[Int(s[Int(i)] &+ s[Int(j)])]
                }
            }
        }
        self.i = i
        self.j = j
    }

    func apply(_ data: Data) -> Data {
        var bytes = [UInt8](data)
        process(&bytes)
        return Data(bytes)
    }
}

/// Диффи — Хеллман над 768-битным простым числом из спецификации MSE.
/// Своя длинная арифметика: умножение Монтгомери на 32-битных словах.
nonisolated enum DiffieHellman {
    static let keyBytes = 96
    private static let limbs = 24

    /// P (big-endian, 96 байт), G = 2.
    static let primeHex = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563"

    private static let p: [UInt32] = limbsFrom(bigEndian: hexBytes(primeHex))
    /// −p⁻¹ mod 2³².
    private static let n0: UInt32 = {
        var inv: UInt32 = 1
        for _ in 0..<5 { inv = inv &* (2 &- p[0] &* inv) }
        return 0 &- inv
    }()
    /// R² mod p, где R = 2^768.
    private static let r2: [UInt32] = {
        var x = [UInt32](repeating: 0, count: limbs)
        x[0] = 1
        for _ in 0..<(2 * 32 * limbs) { x = doubleMod(x) }
        return x
    }()

    /// Случайный 160-битный закрытый ключ.
    static func makePrivateKey() -> Data {
        Data((0..<20).map { _ in UInt8.random(in: 0...255) })
    }

    static func publicKey(_ privateKey: Data) -> Data {
        var g = [UInt32](repeating: 0, count: limbs)
        g[0] = 2
        return bigEndian(modPow(g, privateKey))
    }

    /// Общий секрет S из открытого ключа собеседника. nil — ключ вне допустимого диапазона.
    static func sharedSecret(_ theirPublic: Data, _ privateKey: Data) -> Data? {
        guard theirPublic.count == keyBytes else { return nil }
        var y = limbsFrom(bigEndian: [UInt8](theirPublic))
        if compare(y, p) >= 0 { y = subtract(y, p) }
        if y.allSatisfy({ $0 == 0 }) { return nil }
        return bigEndian(modPow(y, privateKey))
    }

    // MARK: - Арифметика

    private static func modPow(_ base: [UInt32], _ exponent: Data) -> [UInt32] {
        let bm = montMul(base, r2)
        var one = [UInt32](repeating: 0, count: limbs)
        one[0] = 1
        var acc = montMul(one, r2)
        for byte in exponent {
            for bit in (0..<8).reversed() {
                acc = montMul(acc, acc)
                if byte >> UInt8(bit) & 1 == 1 { acc = montMul(acc, bm) }
            }
        }
        return montMul(acc, one)
    }

    private static func montMul(_ a: [UInt32], _ b: [UInt32]) -> [UInt32] {
        let n = limbs
        var t = [UInt32](repeating: 0, count: n + 2)
        for i in 0..<n {
            var c: UInt64 = 0
            let bi = UInt64(b[i])
            for j in 0..<n {
                let x = UInt64(t[j]) + UInt64(a[j]) * bi + c
                t[j] = UInt32(truncatingIfNeeded: x)
                c = x >> 32
            }
            var x = UInt64(t[n]) + c
            t[n] = UInt32(truncatingIfNeeded: x)
            t[n + 1] = UInt32(x >> 32)
            let m = UInt64(t[0] &* n0)
            x = UInt64(t[0]) + m * UInt64(p[0])
            c = x >> 32
            for j in 1..<n {
                x = UInt64(t[j]) + m * UInt64(p[j]) + c
                t[j - 1] = UInt32(truncatingIfNeeded: x)
                c = x >> 32
            }
            x = UInt64(t[n]) + c
            t[n - 1] = UInt32(truncatingIfNeeded: x)
            t[n] = t[n + 1] &+ UInt32(x >> 32)
        }
        var r = Array(t[0..<n])
        if t[n] != 0 || compare(r, p) >= 0 { r = subtract(r, p) }
        return r
    }

    private static func doubleMod(_ x: [UInt32]) -> [UInt32] {
        var r = [UInt32](repeating: 0, count: limbs)
        var carry: UInt32 = 0
        for i in 0..<limbs {
            r[i] = x[i] << 1 | carry
            carry = x[i] >> 31
        }
        if carry != 0 || compare(r, p) >= 0 { r = subtract(r, p) }
        return r
    }

    private static func compare(_ a: [UInt32], _ b: [UInt32]) -> Int {
        for i in (0..<limbs).reversed() where a[i] != b[i] { return a[i] < b[i] ? -1 : 1 }
        return 0
    }

    /// a − b по модулю 2^768 (заём за старшим словом отбрасывается).
    private static func subtract(_ a: [UInt32], _ b: [UInt32]) -> [UInt32] {
        var r = [UInt32](repeating: 0, count: limbs)
        var borrow: UInt64 = 0
        for i in 0..<limbs {
            let d = UInt64(a[i]) &- UInt64(b[i]) &- borrow
            r[i] = UInt32(truncatingIfNeeded: d)
            borrow = (d >> 63) & 1
        }
        return r
    }

    private static func limbsFrom(bigEndian bytes: [UInt8]) -> [UInt32] {
        var padded = [UInt8](repeating: 0, count: max(0, keyBytes - bytes.count)) + bytes.suffix(keyBytes)
        padded.reverse()
        var out = [UInt32](repeating: 0, count: limbs)
        for i in 0..<limbs {
            var v = UInt32(padded[i * 4])
            v |= UInt32(padded[i * 4 + 1]) << 8
            v |= UInt32(padded[i * 4 + 2]) << 16
            v |= UInt32(padded[i * 4 + 3]) << 24
            out[i] = v
        }
        return out
    }

    private static func bigEndian(_ x: [UInt32]) -> Data {
        var out = Data(capacity: keyBytes)
        for limb in x.reversed() {
            out.append(contentsOf: [UInt8(limb >> 24), UInt8(limb >> 16 & 0xFF), UInt8(limb >> 8 & 0xFF), UInt8(limb & 0xFF)])
        }
        return out
    }

    static func hexBytes(_ hex: String) -> [UInt8] {
        let chars = Array(hex.utf8)
        return stride(from: 0, to: chars.count - 1, by: 2).compactMap { UInt8(String(decoding: chars[$0...$0 + 1], as: UTF8.self), radix: 16) }
    }
}
