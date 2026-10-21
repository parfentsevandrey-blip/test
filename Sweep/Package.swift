// swift-tools-version:6.2
import PackageDescription

let package = Package(
    name: "Sweep",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Sweep", targets: ["Sweep"])
    ],
    targets: [
        .executableTarget(
            name: "Sweep",
            path: "Sources/Sweep",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию. Сканеры и удаление помечены nonisolated
                // и работают в фоновых задачах.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ],
            linkerSettings: [.linkedLibrary("sqlite3")]
        )
    ]
)
