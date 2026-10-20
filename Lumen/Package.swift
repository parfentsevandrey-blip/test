// swift-tools-version:6.2
import PackageDescription

let package = Package(
    name: "Lumen",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Lumen", targets: ["Lumen"])
    ],
    targets: [
        .executableTarget(
            name: "Lumen",
            path: "Sources/Lumen",
            swiftSettings: [
                // Swift 6.2 "approachable concurrency": UI-код по умолчанию на главном акторе,
                // тяжёлая работа явно уходит с него через @concurrent.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
