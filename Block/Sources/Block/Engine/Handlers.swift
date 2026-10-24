import AppKit
import CoreServices
import UniformTypeIdentifiers

/// Программы по умолчанию через LaunchServices — как «Открывать в программе… → Изменить всё» в Finder.
enum Handlers {
    static func appURL(_ bundleID: String) -> URL? {
        NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID)
    }

    static func type(_ handler: Handler) -> UTType? {
        switch handler.kind {
        case .contentType: UTType(handler.value)
        case .fileExtension: UTType(filenameExtension: handler.value)
        case .scheme: nil
        }
    }

    /// Программа, которая сейчас открывает этот тип или схему.
    static func current(_ handler: Handler) -> URL? {
        switch handler.kind {
        case .scheme:
            guard let url = URL(string: handler.value + "://example") else { return nil }
            return NSWorkspace.shared.urlForApplication(toOpen: url)
        case .contentType, .fileExtension:
            guard let type = type(handler) else { return nil }
            return NSWorkspace.shared.urlForApplication(toOpen: type)
        }
    }

    static func bundleID(of url: URL?) -> String? {
        url.flatMap { Bundle(url: $0)?.bundleIdentifier }
    }

    enum Failure: Error { case noBundle, status(OSStatus) }

    static func set(_ handler: Handler, to app: URL) async throws {
        switch handler.kind {
        case .scheme:
            // Браузер и почту macOS меняет только с подтверждением человека — это системное окно.
            try await NSWorkspace.shared.setDefaultApplication(at: app, toOpenURLsWithScheme: handler.value)
        case .contentType, .fileExtension:
            // Типы файлов — напрямую в базу LaunchServices: сразу и без окон (как «Изменить всё» в Finder).
            guard let type = type(handler) else { return }
            guard let bundle = Bundle(url: app)?.bundleIdentifier else { throw Failure.noBundle }
            let status = LSSetDefaultRoleHandlerForContentType(type.identifier as CFString, .all, bundle as CFString)
            if status != noErr { throw Failure.status(status) }
        }
    }
}
