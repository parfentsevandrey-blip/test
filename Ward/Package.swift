// swift-tools-version:6.2
import PackageDescription

// Ward — браузер на WebKit (системный движок macOS). Сторонних библиотек нет: блокировщик, защита
// от отпечатка, очистка ссылок и интерфейс написаны с нуля.
let package = Package(
    name: "Ward",
    platforms: [.macOS(.v26)],
    products: [
        .executable(name: "Ward", targets: ["Ward"])
    ],
    targets: [
        .executableTarget(
            name: "Ward",
            path: "Sources/Ward",
            swiftSettings: [
                // Интерфейс и WebKit — на главном акторе по умолчанию. Разбор фильтров и сеть помечены
                // nonisolated и работают в фоне.
                .defaultIsolation(MainActor.self),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]
        )
    ]
)
