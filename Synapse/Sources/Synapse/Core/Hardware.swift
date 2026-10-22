import AppKit
import Foundation
import IOKit
import Metal

/// Всё о Mac, что важно для локальных моделей.
///
/// Скорость генерации упирается в пропускную способность памяти (каждый токен — проход по всем
/// активным весам), а размер модели — в объём памяти, который macOS отдаёт видеоядру.
nonisolated struct MacProfile: Codable, Sendable, Equatable {
    var chip: String
    var modelID: String
    var memory: Int64
    var performanceCores: Int
    var efficiencyCores: Int
    var gpuCores: Int
    /// Пропускная способность памяти, ГБ/с.
    var bandwidth: Double
    /// Значение из таблицы Apple (иначе — оценка).
    var bandwidthKnown: Bool
    /// Сколько памяти Metal рекомендует занимать видеоядру (recommendedMaxWorkingSetSize).
    var gpuBudget: Int64
    var freeDisk: Int64
    var gpuName: String
    /// Виртуальная машина: видеоядро эмулируется, Metal для нейросетей ненадёжен.
    var virtualized: Bool
    var osVersion: String

    var memoryGB: Double { Double(memory) / 1_073_741_824 }
    var gpuBudgetGB: Double { Double(gpuBudget) / 1_073_741_824 }

    /// «M3 Pro» без «Apple».
    var chipShort: String {
        chip.replacingOccurrences(of: "Apple ", with: "").replacingOccurrences(of: " (Virtual)", with: "")
    }

    var generation: Int? {
        guard let r = chipShort.range(of: #"M(\d+)"#, options: .regularExpression) else { return nil }
        return Int(chipShort[r].dropFirst())
    }

    var tier: String {
        for t in ["Ultra", "Max", "Pro"] where chipShort.contains(t) { return t }
        return ""
    }

    /// Человеческое описание класса машины.
    var summary: String {
        let gb = Int(memoryGB.rounded())
        switch gb {
        case ..<12: return "\(gb) ГБ памяти — хватит для компактных моделей до 4–8 млрд параметров."
        case ..<20: return "\(gb) ГБ памяти — уверенно работают модели до 12–14 млрд параметров."
        case ..<40: return "\(gb) ГБ памяти — доступны сильные модели на 24–35 млрд параметров."
        case ..<80: return "\(gb) ГБ памяти — тянет большие модели до 70 млрд параметров."
        default: return "\(gb) ГБ памяти — рабочая станция: доступны даже модели на 100+ млрд параметров."
        }
    }
}

nonisolated enum Hardware {
    static func scan() -> MacProfile {
        let chip = sysctlString("machdep.cpu.brand_string") ?? "Apple Silicon"
        let memory = sysctlInt("hw.memsize") ?? Int64(ProcessInfo.processInfo.physicalMemory)
        let perf = Int(sysctlInt("hw.perflevel0.physicalcpu") ?? Int64(ProcessInfo.processInfo.activeProcessorCount))
        let eff = Int(sysctlInt("hw.perflevel1.physicalcpu") ?? 0)
        let device = MTLCreateSystemDefaultDevice()
        let gpuName = device?.name ?? "—"
        let gpuCores = gpuCoreCount()
        let virtualized = gpuName.localizedCaseInsensitiveContains("paravirtual") || chip.contains("Virtual")
            || (sysctlInt("kern.hv_vmm_present") ?? 0) == 1
        var budget = Int64(device?.recommendedMaxWorkingSetSize ?? 0)
        if budget <= 0 || budget > memory { budget = Int64(Double(memory) * (memory >= 34_000_000_000 ? 0.75 : 0.66)) }
        // Предел, поднятый в «Выжать максимум» (sysctl iogpu.wired_limit_mb, до перезагрузки).
        if let raised = GPUMemory.raisedLimit, raised <= memory { budget = max(budget, raised) }
        let (bw, known) = bandwidth(chip: chip, gpuCores: gpuCores)
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return MacProfile(chip: chip, modelID: sysctlString("hw.model") ?? "Mac", memory: memory,
                          performanceCores: perf, efficiencyCores: eff, gpuCores: gpuCores,
                          bandwidth: bw, bandwidthKnown: known, gpuBudget: budget, freeDisk: freeDisk(),
                          gpuName: gpuName, virtualized: virtualized,
                          osVersion: "macOS \(v.majorVersion).\(v.minorVersion)")
    }

    static var performanceCores: Int {
        Int(sysctlInt("hw.perflevel0.physicalcpu") ?? Int64(max(1, ProcessInfo.processInfo.activeProcessorCount / 2)))
    }

    static func freeDisk() -> Int64 {
        let url = URL(fileURLWithPath: NSHomeDirectory())
        let v = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey, .volumeAvailableCapacityKey])
        return v?.volumeAvailableCapacityForImportantUsage ?? Int64(v?.volumeAvailableCapacity ?? 0)
    }

    /// Пропускная способность памяти по таблицам Apple. Для будущих чипов — осторожная оценка.
    static func bandwidth(chip: String, gpuCores: Int) -> (Double, Bool) {
        let name = chip.replacingOccurrences(of: "Apple ", with: "")
        func has(_ s: String) -> Bool { name.contains(s) }
        let gen = name.range(of: #"M(\d+)"#, options: .regularExpression).flatMap { Int(name[$0].dropFirst()) } ?? 0
        let tier = has("Ultra") ? 3 : has("Max") ? 2 : has("Pro") ? 1 : 0
        switch (gen, tier) {
        case (1, 0): return (68, true)
        case (1, 1), (2, 1): return (200, true)
        case (1, 2), (2, 2): return (400, true)
        case (1, 3), (2, 3): return (800, true)
        case (2, 0), (3, 0): return (100, true)
        case (3, 1): return (150, true)
        case (3, 2): return (gpuCores > 0 && gpuCores <= 30 ? 300 : 400, true)
        case (3, 3): return (819, true)
        case (4, 0): return (120, true)
        case (4, 1): return (273, true)
        case (4, 2): return (gpuCores > 0 && gpuCores <= 32 ? 410 : 546, true)
        case (5, 0): return (153, true)
        case (5, 1): return (307, false)
        case (5, 2): return (gpuCores > 0 && gpuCores <= 32 ? 460 : 614, false)
        default:
            // Новое поколение: каждое примерно на 15% быстрее предыдущего.
            let base: [Double] = [153, 307, 614, 1228]
            let factor = gen > 5 ? pow(1.15, Double(gen - 5)) : 1
            return (base[tier] * factor, false)
        }
    }

    /// Число ядер видеоядра из IORegistry (AGXAccelerator → gpu-core-count).
    static func gpuCoreCount() -> Int {
        var iterator: io_iterator_t = 0
        guard IOServiceGetMatchingServices(kIOMainPortDefault, IOServiceMatching("AGXAccelerator"), &iterator) == KERN_SUCCESS else { return 0 }
        defer { IOObjectRelease(iterator) }
        var service = IOIteratorNext(iterator)
        while service != 0 {
            defer { IOObjectRelease(service); service = IOIteratorNext(iterator) }
            if let value = IORegistryEntryCreateCFProperty(service, "gpu-core-count" as CFString, kCFAllocatorDefault, 0)?.takeRetainedValue() as? Int {
                return value
            }
        }
        return 0
    }

    static func sysctlString(_ name: String) -> String? {
        var size = 0
        guard sysctlbyname(name, nil, &size, nil, 0) == 0, size > 0 else { return nil }
        var buf = [CChar](repeating: 0, count: size)
        guard sysctlbyname(name, &buf, &size, nil, 0) == 0 else { return nil }
        return String(decoding: buf.prefix(while: { $0 != 0 }).map { UInt8(bitPattern: $0) }, as: UTF8.self)
    }

    static func sysctlInt(_ name: String) -> Int64? {
        var value: Int64 = 0
        var size = MemoryLayout<Int64>.size
        guard sysctlbyname(name, &value, &size, nil, 0) == 0 else {
            var small: Int32 = 0
            var s32 = MemoryLayout<Int32>.size
            return sysctlbyname(name, &small, &s32, nil, 0) == 0 ? Int64(small) : nil
        }
        return size == 4 ? Int64(Int32(truncatingIfNeeded: value)) : value
    }

    /// Выдуманный Mac для проверок и примеров «а что, если».
    static func simulated(_ chip: String, memoryGB: Int, gpuCores: Int = 10, freeDiskGB: Int = 200) -> MacProfile {
        let memory = Int64(memoryGB) * 1_073_741_824
        let (bw, known) = bandwidth(chip: chip, gpuCores: gpuCores)
        return MacProfile(chip: chip, modelID: "Mac", memory: memory, performanceCores: 4, efficiencyCores: 4,
                          gpuCores: gpuCores, bandwidth: bw, bandwidthKnown: known,
                          gpuBudget: Int64(Double(memory) * (memoryGB >= 36 ? 0.75 : 0.66)),
                          freeDisk: Int64(freeDiskGB) * 1_073_741_824, gpuName: chip, virtualized: false, osVersion: "macOS 26")
    }
}

/// Сколько памяти macOS разрешает занять видеоядру.
///
/// По умолчанию — примерно 2/3 памяти (3/4 на Mac с 36 ГБ и больше). Предел можно поднять командой
/// `sysctl iogpu.wired_limit_mb`: она действует до перезагрузки, после — всё как было. Это и делает
/// кнопка в «Выжать максимум»: macOS сама спросит пароль администратора.
nonisolated enum GPUMemory {
    static let mb: Int64 = 1_048_576

    /// Поднятый предел в байтах; `nil` — стандартный.
    static var raisedLimit: Int64? {
        let value = Hardware.sysctlInt("iogpu.wired_limit_mb") ?? 0
        return value > 0 ? value * mb : nil
    }

    /// Сколько можно отдать видеоядру, оставив macOS и программам не меньше 4 ГБ (и не меньше 15% памяти).
    /// На Mac меньше 16 ГБ не предлагаем: системе самой тесно.
    static func target(_ mac: MacProfile) -> Int64? {
        guard mac.memoryGB >= 15, !mac.virtualized else { return nil }
        let reserve = max(4 * 1_073_741_824, Int64(Double(mac.memory) * 0.15))
        let target = (mac.memory - reserve) / mb * mb
        return target > mac.gpuBudget + 512 * mb ? target : nil
    }

    /// Тот же Mac, если поднять предел.
    static func unlocked(_ mac: MacProfile) -> MacProfile? {
        guard let target = target(mac) else { return nil }
        var m = mac
        m.gpuBudget = target
        return m
    }

    /// Поднять предел (`bytes`) или вернуть стандартный (`nil`). Пароль спрашивает macOS.
    /// Возвращает текст ошибки; `nil` — получилось, `""` — пользователь отменил.
    @MainActor
    static func set(_ bytes: Int64?) async -> String? {
        let value = bytes.map { $0 / mb } ?? 0
        let prompt = bytes == nil ? "Synapse возвращает стандартный предел памяти видеоядра."
            : "Synapse разрешает видеоядру занять до \(value / 1024) ГБ памяти — до перезагрузки Mac."
        let source = "do shell script \"/usr/sbin/sysctl iogpu.wired_limit_mb=\(value)\" with prompt \"\(prompt)\" with administrator privileges"
        var error: NSDictionary?
        NSAppleScript(source: source)?.executeAndReturnError(&error)
        guard let error else { return nil }
        let code = error[NSAppleScript.errorNumber] as? Int ?? 0
        return code == -128 ? "" : (error[NSAppleScript.errorMessage] as? String ?? "ошибка \(code)")
    }
}
