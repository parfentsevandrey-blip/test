// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "ROMForge",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "ROMForge", targets: ["ROMForge"])
    ],
    targets: [
        .executableTarget(
            name: "ROMForge",
            path: "Sources/ROMForge"
        )
    ]
)
