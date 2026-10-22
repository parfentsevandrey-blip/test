// swift-tools-version:6.2
import Foundation
import PackageDescription

// Движок llama.cpp собирается из исходников скриптом scripts/build-engine.sh в Vendor/llama.
let engineLib = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("Vendor/llama/lib").path

let package = Package(
    name: "Utter",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Utter", targets: ["Utter"])
    ],
    targets: [
        .systemLibrary(name: "CLlama", path: "Sources/CLlama"),
        .executableTarget(
            name: "Utter",
            dependencies: ["CLlama"],
            path: "Sources/Utter",
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
