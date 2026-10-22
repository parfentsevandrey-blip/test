// swift-tools-version:6.2
import PackageDescription

// Движок Xray-core собирается из исходников скриптом scripts/build-core.sh в Vendor/xray
// и кладётся в бандл отдельной программой: если ядро упадёт, Veil перезапустит его, а сам останется жив.
let package = Package(
    name: "Veil",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Veil", targets: ["Veil"]),
        .executable(name: "VeilHelper", targets: ["VeilHelper"]),
    ],
    targets: [
        // Общий протокол приложения и помощника (запросы, путь сокета, очистка конфигурации).
        .target(name: "VeilShared", path: "Sources/VeilShared"),
        .executableTarget(
            name: "Veil",
            dependencies: ["VeilShared"],
            path: "Sources/Veil",
            swiftSettings: [
                // Интерфейс — на главном акторе по умолчанию. Ядро, сеть и разбор ссылок помечены
                // nonisolated и работают в своих очередях.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        ),
        // Помощник с правами root: системный прокси и TUN. Ставится один раз с паролем администратора.
        .executableTarget(name: "VeilHelper", dependencies: ["VeilShared"], path: "Sources/VeilHelper"),
    ]
)
