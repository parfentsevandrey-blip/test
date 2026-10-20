import AVFoundation
import Foundation

/// Безголовая проверка ремуксера: перепаковывает файл, затем реально декодирует
/// кадры и звук из результата через AVAssetReader и печатает отчёт в JSON.
nonisolated enum SelfTest {
    struct Report: Codable {
        var ok = false
        var error: String?
        var mediaFile: String?
        var duration: Double = 0
        var videoCodec: String?
        var width: Double = 0
        var height: Double = 0
        var audioTracks = 0
        var audioNames: [String] = []
        var subtitleTracks = 0
        var subtitleCues = 0
        var chapters = 0
        var decodedVideoFrames = 0
        var decodedAudioBuffers = 0
        var notes: [String] = []
        var outputTracks: [String] = []
        var seconds: Double = 0
    }

    static func run(input: URL, output: URL) -> Int32 {
        let box = ResultBox()
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            box.report = await perform(input: input, output: output)
            done.signal()
        }
        done.wait()
        let report = box.report ?? Report(error: "no report")
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(report) { print(String(decoding: data, as: UTF8.self)) }
        return report.ok ? 0 : 1
    }

    private final class ResultBox: @unchecked Sendable { var report: Report? }

    @concurrent private static func perform(input: URL, output: URL) async -> Report {
        var r = Report()
        let start = Date()
        do {
            try? FileManager.default.removeItem(at: output)
            try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
            let manifest = try await MatroskaRemuxer.remux(input: input, outputDirectory: output) { _, _ in }
            r.seconds = Date().timeIntervalSince(start)
            r.mediaFile = manifest.mediaFile
            r.audioNames = manifest.audioNames
            r.subtitleTracks = manifest.subtitles.count
            r.subtitleCues = manifest.subtitles.reduce(0) { $0 + $1.cues.count }
            r.chapters = manifest.chapters.count
            r.notes = manifest.notes

            let media = output.appendingPathComponent(manifest.mediaFile)
            let asset = AVURLAsset(url: media)
            let info = try await AssetInspector.inspect(asset, original: media)
            r.duration = info.duration
            r.videoCodec = info.videoCodec
            r.width = info.displaySize.width
            r.height = info.displaySize.height
            for track in try await asset.load(.tracks) {
                let formats = try await track.load(.formatDescriptions)
                let codes = formats.map { AssetInspector.fourCC(CMFormatDescriptionGetMediaSubType($0)) }
                r.outputTracks.append("\(track.mediaType.rawValue):\(codes.joined(separator: ","))")
            }
            let audioTracks = try await asset.loadTracks(withMediaType: .audio)
            r.audioTracks = audioTracks.count

            let reader = try AVAssetReader(asset: asset)
            var videoOutput: AVAssetReaderTrackOutput?
            if let video = try await asset.loadTracks(withMediaType: .video).first {
                let out = AVAssetReaderTrackOutput(track: video, outputSettings: [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                ])
                reader.add(out)
                videoOutput = out
            }
            var audioOutput: AVAssetReaderTrackOutput?
            if let audio = audioTracks.first {
                let out = AVAssetReaderTrackOutput(track: audio, outputSettings: [AVFormatIDKey: kAudioFormatLinearPCM])
                reader.add(out)
                audioOutput = out
            }
            guard reader.startReading() else { throw reader.error ?? MatroskaError("reader failed") }
            for _ in 0..<240 {
                var progressed = false
                if let v = videoOutput, r.decodedVideoFrames < 60, v.copyNextSampleBuffer() != nil {
                    r.decodedVideoFrames += 1
                    progressed = true
                }
                if let a = audioOutput, r.decodedAudioBuffers < 60, a.copyNextSampleBuffer() != nil {
                    r.decodedAudioBuffers += 1
                    progressed = true
                }
                if !progressed { break }
            }
            if reader.status == .failed { throw reader.error ?? MatroskaError("decode failed") }
            reader.cancelReading()
            let videoOK = videoOutput == nil || r.decodedVideoFrames > 0
            let audioOK = audioOutput == nil || r.decodedAudioBuffers > 0
            r.ok = videoOK && audioOK && r.duration > 0
            if !r.ok { r.error = "decode check failed" }
        } catch {
            r.error = String(describing: error)
        }
        return r
    }
}
