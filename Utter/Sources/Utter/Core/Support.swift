import Foundation

nonisolated enum Format {
    static func bytes(_ v: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: max(0, v), countStyle: .file)
    }

    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "—" }
        if seconds < 60 { return "\(Int(seconds.rounded())) с" }
        return "\(Int(seconds / 60)) мин \(Int(seconds.truncatingRemainder(dividingBy: 60))) с"
    }

    static func relative(_ date: Date) -> String {
        if date > Date().addingTimeInterval(-60) { return "только что" }
        let f = RelativeDateTimeFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.unitsStyle = .short
        return f.localizedString(for: date, relativeTo: Date())
    }
}

nonisolated enum Hardware {
    static func freeDisk() -> Int64 {
        let v = try? URL(fileURLWithPath: NSHomeDirectory()).resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return v?.volumeAvailableCapacityForImportantUsage ?? 0
    }

    static var memoryGB: Double { Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824 }

    static var performanceCores: Int {
        var value: Int32 = 0
        var size = MemoryLayout<Int32>.size
        if sysctlbyname("hw.perflevel0.physicalcpu", &value, &size, nil, 0) == 0, value > 0 { return Int(value) }
        // Нет деления на производительные и эффективные ядра (виртуальная машина) — все ядра равны.
        return max(1, ProcessInfo.processInfo.activeProcessorCount)
    }

    /// Виртуальная машина (CI): видеоядро эмулируется — считаем на процессоре.
    static var virtualized: Bool {
        var value: Int32 = 0
        var size = MemoryLayout<Int32>.size
        return sysctlbyname("kern.hv_vmm_present", &value, &size, nil, 0) == 0 && value == 1
    }
}

nonisolated enum AppPaths {
    nonisolated(unsafe) static var override: URL? = ProcessInfo.processInfo.environment["UTTER_HOME"].map { URL(fileURLWithPath: $0) }

    static var support: URL {
        let base = override ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Utter", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    static var models: URL {
        let dir = support.appendingPathComponent("Models", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
