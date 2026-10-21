// swift-tools-version:6.2
import PackageDescription

let package = Package(
    name: "Swarm",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Swarm", targets: ["Swarm"])
    ],
    targets: [
        .executableTarget(
            name: "Swarm",
            path: "Sources/Swarm",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию. Всё сетевое ядро помечено
                // nonisolated или живёт в собственных акторах.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
