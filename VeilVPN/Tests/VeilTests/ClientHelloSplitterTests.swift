import XCTest
@testable import Veil

final class ClientHelloSplitterTests: XCTestCase {
    /// Builds a minimal but well-formed ClientHello record with one SNI extension.
    static func clientHello(host: String) -> Data {
        var body: [UInt8] = [0x03, 0x03]                     // client_version
        body += [UInt8](repeating: 0xAB, count: 32)          // random
        body += [0x00]                                       // session id length
        body += [0x00, 0x04, 0x13, 0x01, 0x13, 0x02]         // cipher suites
        body += [0x01, 0x00]                                 // compression methods
        let hostBytes = [UInt8](host.utf8)
        var sni: [UInt8] = []
        sni += [0x00, 0x00]                                  // extension type: server_name
        let listLength = 3 + hostBytes.count
        sni += [UInt8((listLength + 2) >> 8), UInt8((listLength + 2) & 0xFF)]
        sni += [UInt8(listLength >> 8), UInt8(listLength & 0xFF)]
        sni += [0x00]                                        // host_name
        sni += [UInt8(hostBytes.count >> 8), UInt8(hostBytes.count & 0xFF)]
        sni += hostBytes
        body += [UInt8(sni.count >> 8), UInt8(sni.count & 0xFF)]
        body += sni
        var handshake: [UInt8] = [0x01, UInt8((body.count >> 16) & 0xFF), UInt8((body.count >> 8) & 0xFF), UInt8(body.count & 0xFF)]
        handshake += body
        var record: [UInt8] = [0x16, 0x03, 0x01, UInt8(handshake.count >> 8), UInt8(handshake.count & 0xFF)]
        record += handshake
        return Data(record)
    }

    func testRecognisesClientHelloAndSNI() {
        let record = Self.clientHello(host: "www.youtube.com")
        XCTAssertTrue(ClientHelloSplitter.isClientHello(record))
        XCTAssertEqual(ClientHelloSplitter.firstRecordLength(record), record.count)
        XCTAssertEqual(ClientHelloSplitter.sniHost(in: record), "www.youtube.com")
        XCTAssertFalse(ClientHelloSplitter.isClientHello(Data("GET / HTTP/1.1".utf8)))
    }

    func testTCPSplitKeepsBytesAndCutsInsideHostName() {
        let record = Self.clientHello(host: "www.youtube.com")
        let chunks = ClientHelloSplitter.chunks(for: record, strategy: .segmentAtSNI)
        XCTAssertEqual(chunks.count, 2)
        XCTAssertEqual(chunks[0] + chunks[1], record)
        // Neither segment may contain the whole host name.
        XCTAssertNil(String(data: chunks[0], encoding: .isoLatin1)?.range(of: "www.youtube.com"))
        XCTAssertNil(String(data: chunks[1], encoding: .isoLatin1)?.range(of: "www.youtube.com"))
    }

    func testFirstByteSplit() {
        let record = Self.clientHello(host: "example.org")
        let chunks = ClientHelloSplitter.chunks(for: record, strategy: .firstByte)
        XCTAssertEqual(chunks.count, 2)
        XCTAssertEqual(chunks[0].count, 1)
        XCTAssertEqual(chunks[0] + chunks[1], record)
    }

    func testRecordSplitProducesTwoValidRecordsWithSamePayload() {
        let record = Self.clientHello(host: "www.youtube.com")
        let chunks = ClientHelloSplitter.chunks(for: record, strategy: .recordAndSegmentAtSNI)
        XCTAssertEqual(chunks.count, 2)
        var payload = Data()
        for chunk in chunks {
            let bytes = [UInt8](chunk)
            XCTAssertEqual(bytes[0], 0x16)
            XCTAssertEqual(bytes[1], 0x03)
            XCTAssertEqual(bytes[2], 0x01)
            let length = Int(bytes[3]) << 8 | Int(bytes[4])
            XCTAssertEqual(length, chunk.count - 5)
            payload.append(chunk.dropFirst(5))
        }
        XCTAssertEqual(payload, record.dropFirst(5))

        let joined = ClientHelloSplitter.chunks(for: record, strategy: .recordAtSNI)
        XCTAssertEqual(joined.count, 1)
        XCTAssertEqual(joined[0].count, record.count + 5)
    }

    func testGarbageIsPassedThrough() {
        let garbage = Data([0x16, 0x03, 0x01, 0x00, 0x02, 0x01, 0x00])
        XCTAssertEqual(ClientHelloSplitter.chunks(for: garbage, strategy: .segmentAtSNI).count, 2)
        XCTAssertEqual(ClientHelloSplitter.chunks(for: garbage, strategy: .recordAndSegmentAtSNI).reduce(Data(), +), garbage)
    }
}
