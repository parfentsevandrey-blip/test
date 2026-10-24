import Foundation
import Virtualization

/// Этот Mac: ядра, память, свободное место — чтобы мастер предлагал разумные ресурсы.
nonisolated enum MacInfo {
    static var cores: Int { ProcessInfo.processInfo.activeProcessorCount }
    static var memoryGB: Int { Int(ProcessInfo.processInfo.physicalMemory / 1_073_741_824) }

    static var chip: String {
        var size = 0
        sysctlbyname("machdep.cpu.brand_string", nil, &size, nil, 0)
        guard size > 0 else { return "Mac" }
        var buffer = [CChar](repeating: 0, count: size)
        sysctlbyname("machdep.cpu.brand_string", &buffer, &size, nil, 0)
        return String(decoding: buffer.prefix { $0 != 0 }.map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    static func freeDisk(at url: URL = Paths.home) -> Int64 {
        let values = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return values?.volumeAvailableCapacityForImportantUsage ?? Int64.max
    }

    static var virtualizationSupported: Bool { VZVirtualMachine.isSupported }

    /// Не больше половины ядер и памяти Mac, не меньше минимума системы.
    static func fit(_ wanted: Resources) -> Resources {
        let maxCPU = max(1, min(VZVirtualMachineConfiguration.maximumAllowedCPUCount, cores - max(2, cores / 4)))
        let maxMemory = max(2, memoryGB / 2)
        return Resources(cpu: max(1, min(wanted.cpu, maxCPU)), memoryGB: max(1, min(wanted.memoryGB, maxMemory)), diskGB: wanted.diskGB)
    }

    static var maxCPU: Int { max(1, min(VZVirtualMachineConfiguration.maximumAllowedCPUCount, cores)) }
    static var maxMemoryGB: Int { max(2, Int(Double(memoryGB) * 0.75)) }
}

/// Где Vessel хранит образы и машины. VESSEL_HOME — для самопроверок.
nonisolated enum Paths {
    static var home: URL {
        if let custom = ProcessInfo.processInfo.environment["VESSEL_HOME"], !custom.isEmpty {
            return URL(fileURLWithPath: custom, isDirectory: true)
        }
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Vessel", isDirectory: true)
    }

    static var images: URL { home.appendingPathComponent("Images", isDirectory: true) }
    static var machines: URL { home.appendingPathComponent("Machines", isDirectory: true) }
    static var screenshots: URL { home.appendingPathComponent("Screenshots", isDirectory: true) }

    /// Общие папки по умолчанию — там, где их легко найти в Finder.
    static var shared: URL {
        FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Vessel", isDirectory: true)
    }
}

nonisolated enum Format {
    static func bytes(_ n: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: n, countStyle: .file)
    }

    static func gb(_ value: Double) -> String {
        value < 1 ? "\(Int((value * 1000).rounded())) МБ" : String(format: "%.1f ГБ", value).replacingOccurrences(of: ".", with: ",")
    }

    static func speed(_ bytesPerSecond: Double) -> String {
        bytes(Int64(bytesPerSecond)) + "/с"
    }

    static func duration(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "—" }
        let s = Int(seconds)
        if s < 60 { return "\(s) с" }
        if s < 3600 { return "\(s / 60) мин" }
        return "\(s / 3600) ч \(s % 3600 / 60) мин"
    }

    /// «1 ядро», «2 ядра», «5 ядер».
    static func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let m10 = n % 10, m100 = n % 100
        let word = m10 == 1 && m100 != 11 ? one : (2...4).contains(m10) && !(12...14).contains(m100) ? few : many
        return "\(n) \(word)"
    }
}
