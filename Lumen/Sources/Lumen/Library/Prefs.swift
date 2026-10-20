import Foundation

/// Ключи настроек (используются и в @AppStorage, и в движке).
nonisolated enum Prefs {
    static let resumePlayback = "resumePlayback"
    static let autoplayNext = "autoplayNext"
    static let fitWindowToVideo = "fitWindowToVideo"
    static let ambientEnabled = "ambientEnabled"
    static let ambientIntensity = "ambientIntensity"
    static let cinemaStage = "cinemaStage"
    static let subtitleScale = "subtitleScale"
    static let subtitleGlass = "subtitleGlass"
    static let autoSubtitles = "autoSubtitles"
    static let skipInterval = "skipInterval"
    static let volume = "volume"
    static let skimmingPreviews = "skimmingPreviews"

    static func registerDefaults() {
        UserDefaults.standard.register(defaults: [
            resumePlayback: true,
            autoplayNext: true,
            fitWindowToVideo: true,
            ambientEnabled: true,
            ambientIntensity: 0.85,
            cinemaStage: true,
            subtitleScale: 1.0,
            subtitleGlass: true,
            autoSubtitles: false,
            skipInterval: 10.0,
            volume: 1.0,
            skimmingPreviews: true,
        ])
    }

    static var skip: Double { UserDefaults.standard.double(forKey: skipInterval) }
}
