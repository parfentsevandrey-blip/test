import Foundation
import UserNotifications

/// User notifications for connection events. Silently unavailable when there is no app bundle.
@MainActor
final class NotificationManager {
    static let shared = NotificationManager()

    private var requested = false
    private var granted = false

    private var available: Bool {
        Bundle.main.bundleIdentifier != nil
    }

    func requestAuthorizationIfNeeded() async {
        guard available, !requested else { return }
        requested = true
        do {
            granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        } catch {
            granted = false
        }
    }

    func post(identifier: String, title: String, body: String) {
        guard available, granted else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = nil
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
