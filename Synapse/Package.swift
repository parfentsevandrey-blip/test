// swift-tools-version:6.2
import Foundation
import PackageDescription

// Движок llama.cpp собирается из исходников скриптом scripts/build-engine.sh в Vendor/llama.
let engineLib = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("Vendor/llama/lib").path

let package = Package(
    name: "Synapse",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Synapse", targets: ["Synapse"])
    ],
    targets: [
        .systemLibrary(name: "CLlama", path: "Sources/CLlama"),
        .executableTarget(
            name: "Synapse",
            dependencies: ["CLlama"],
            path: "Sources/Synapse",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию. Движок, сеть и загрузки помечены
                // nonisolated и работают в своих очередях.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ],
            linkerSettings: [.unsafeFlags(["-L", engineLib])]
        )
    ]
)
