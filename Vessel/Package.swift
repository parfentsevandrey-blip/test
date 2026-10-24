// swift-tools-version:6.2
import PackageDescription

// Vessel — другие системы на Mac. Сторонних библиотек нет: виртуальные машины — Virtualization.framework,
// каталог, загрузчик, проверка образов (SHA-256 и BLAKE2b) и интерфейс написаны с нуля.
let package = Package(
    name: "Vessel",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Vessel", targets: ["Vessel"])
    ],
    targets: [
        .executableTarget(
            name: "Vessel",
            path: "Sources/Vessel",
            swiftSettings: [
                // Интерфейс и виртуальные машины — на главном акторе (очередь VZVirtualMachine — главная).
                // Сеть, загрузки и хеши помечены nonisolated.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
