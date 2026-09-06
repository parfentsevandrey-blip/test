import Foundation

/// Locations of the Tor Expert Bundle files shipped inside the app
/// (`Contents/MacOS/tor`, `lyrebird`, `conjure-client`; `Contents/Resources/geoip*`, `pt_config.json`).
struct TorBundle: Sendable {
    let directory: URL
    let tor: URL
    let lyrebird: URL?
    let conjure: URL?
    let geoip: URL?
    let geoip6: URL?
    let ptConfig: URL?

    /// Looks next to the main executable first, then in `$VEIL_TOR_DIR`, then in `./build/tor`
    /// (the output of `scripts/fetch-tor.sh` during development).
    static func locate(environment: [String: String] = ProcessInfo.processInfo.environment) -> TorBundle? {
        let fileManager = FileManager.default
        var candidates: [URL] = []
        if let override = environment["VEIL_TOR_DIR"], !override.isEmpty {
            candidates.append(URL(fileURLWithPath: override, isDirectory: true))
        }
        if let executable = Bundle.main.executableURL {
            candidates.append(executable.deletingLastPathComponent())
        }
        candidates.append(URL(fileURLWithPath: fileManager.currentDirectoryPath, isDirectory: true)
            .appendingPathComponent("build/tor", isDirectory: true))

        for directory in candidates {
            let tor = directory.appendingPathComponent("tor")
            guard fileManager.isExecutableFile(atPath: tor.path) else { continue }

            func executable(_ name: String) -> URL? {
                let url = directory.appendingPathComponent(name)
                return fileManager.isExecutableFile(atPath: url.path) ? url : nil
            }
            func resource(_ name: String) -> URL? {
                var places = [directory.appendingPathComponent(name)]
                if let resources = Bundle.main.resourceURL {
                    places.insert(resources.appendingPathComponent(name), at: 0)
                }
                return places.first { fileManager.fileExists(atPath: $0.path) }
            }

            return TorBundle(
                directory: directory,
                tor: tor,
                lyrebird: executable("lyrebird"),
                conjure: executable("conjure-client"),
                geoip: resource("geoip"),
                geoip6: resource("geoip6"),
                ptConfig: resource("pt_config.json")
            )
        }
        return nil
    }
}

/// Tor splits `ClientTransportPlugin ... exec <path>` on whitespace, so the pluggable-transport
/// binaries must live at a path without spaces. If the app was launched from a folder like
/// "/Volumes/Veil VPN/", we expose the binaries through symlinks in the temporary directory.
enum PluggableTransportLocator {
    static func spaceFreeDirectory(for bundle: TorBundle) throws -> URL {
        let directory = bundle.directory
        if directory.path.rangeOfCharacter(from: .whitespacesAndNewlines) == nil {
            return directory
        }
        let fileManager = FileManager.default
        let linkDirectory = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("veil-pt-\(getuid())", isDirectory: true)
        try fileManager.createDirectory(at: linkDirectory, withIntermediateDirectories: true)
        for binary in [bundle.lyrebird, bundle.conjure].compactMap({ $0 }) {
            let link = linkDirectory.appendingPathComponent(binary.lastPathComponent)
            if fileManager.fileExists(atPath: link.path) || (try? link.checkResourceIsReachable()) == true {
                try? fileManager.removeItem(at: link)
            }
            try fileManager.createSymbolicLink(at: link, withDestinationURL: binary)
        }
        return linkDirectory
    }
}
