// swift-tools-version: 5.9
// Development convenience: `swift run Veil` launches the UI in demo mode (no bundled Tor).
// The distributable app is built from the XcodeGen project — see the Makefile.
import PackageDescription

let package = Package(
    name: "Veil",
    defaultLocalization: "en",
    platforms: [.macOS("26.0")],
    targets: [
        .executableTarget(
            name: "Veil",
            path: "Veil",
            exclude: ["Resources/Assets.xcassets"],
            resources: [.process("Resources")],
            linkerSettings: [
                .linkedFramework("Network"),
                .linkedFramework("SystemConfiguration"),
                .linkedFramework("ServiceManagement"),
                .linkedFramework("Security"),
            ]
        ),
    ]
)
