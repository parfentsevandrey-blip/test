// swift-tools-version:6.2
import PackageDescription

// Block — Mac без сервисов Apple. Сторонних библиотек нет: правила, сторож запуска, журнал и интерфейс
// написаны с нуля на системных API (CFPreferences, LaunchServices, launchctl, NSWorkspace).
let package = Package(
    name: "Block",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Block", targets: ["Block"])
    ],
    targets: [
        .executableTarget(
            name: "Block",
            path: "Sources/Block",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию. Запуск системных команд помечен nonisolated.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
