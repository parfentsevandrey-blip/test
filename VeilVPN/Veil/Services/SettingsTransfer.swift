import AppKit
import Foundation
import UniformTypeIdentifiers

/// Export and import of `AppSettings` as a JSON file.
@MainActor
enum SettingsTransfer {
    static func export(_ settings: AppSettings) {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.json]
        panel.nameFieldStringValue = "Veil-settings.json"
        panel.title = String(localized: "Export Veil settings")
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            try encoder.encode(settings).write(to: url, options: .atomic)
        } catch {
            NSAlert(error: error).runModal()
        }
    }

    static func importSettings() -> AppSettings? {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.json]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.title = String(localized: "Import Veil settings")
        guard panel.runModal() == .OK, let url = panel.url else { return nil }
        do {
            let data = try Data(contentsOf: url)
            return try JSONDecoder().decode(AppSettings.self, from: data)
        } catch {
            NSAlert(error: error).runModal()
            return nil
        }
    }
}
