import AppKit

/// Haptics (trackpad) and optional sounds for connection events.
@MainActor
enum Feedback {
    static func connected(sound: Bool, haptic: Bool) {
        if haptic {
            NSHapticFeedbackManager.defaultPerformer.perform(.levelChange, performanceTime: .default)
        }
        if sound {
            NSSound(named: NSSound.Name("Glass"))?.play()
        }
    }

    static func disconnected(sound: Bool, haptic: Bool) {
        if haptic {
            NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .default)
        }
        if sound {
            NSSound(named: NSSound.Name("Pop"))?.play()
        }
    }

    /// A light click for interactive dashboard elements.
    static func tap(haptic: Bool) {
        if haptic {
            NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .now)
        }
    }

    static func failed(sound: Bool, haptic: Bool) {
        if haptic {
            NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .default)
        }
        if sound {
            NSSound(named: NSSound.Name("Basso"))?.play()
        }
    }
}
