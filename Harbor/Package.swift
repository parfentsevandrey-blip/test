// swift-tools-version:6.2
import PackageDescription

let package = Package(
    name: "Harbor",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Harbor", targets: ["Harbor"])
    ],
    targets: [
        .executableTarget(
            name: "Harbor",
            path: "Sources/Harbor",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию; установщик и сеть помечены nonisolated
                // или живут в собственных акторах.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
