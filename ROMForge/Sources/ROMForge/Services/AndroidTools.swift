import Foundation

/// Обёртка над adb.
struct ADB: Sendable {
    let executable: URL

    func devices() async -> [ConnectedDevice] {
        guard let result = try? await Shell.run(executable, ["devices", "-l"]) else { return [] }
        return result.stdout
            .split(separator: "\n")
            .dropFirst()  // "List of devices attached"
            .compactMap(Self.parseDeviceLine)
    }

    static func parseDeviceLine(_ line: Substring) -> ConnectedDevice? {
        let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
        guard parts.count >= 2, !parts[0].hasPrefix("*") else { return nil }
        let mode: ConnectedDevice.Mode = switch parts[1] {
        case "device": .android
        case "unauthorized": .unauthorized
        case "offline": .offline
        case "recovery": .recovery
        case "sideload": .sideload
        case "bootloader": .bootloader
        default: .unknown
        }
        var info: [String: String] = [:]
        for part in parts.dropFirst(2) {
            let pair = part.split(separator: ":", maxSplits: 1).map(String.init)
            if pair.count == 2 { info[pair[0]] = pair[1] }
        }
        return ConnectedDevice(serial: parts[0], mode: mode, model: info["model"], codenameHint: info["device"])
    }

    @discardableResult
    func run(_ serial: String, _ arguments: [String],
             onLine: (@Sendable (String) -> Void)? = nil) async throws -> ShellResult {
        try await Shell.run(executable, ["-s", serial] + arguments, onLine: onLine)
    }

    func shell(_ serial: String, _ command: String) async -> String {
        (try? await run(serial, ["shell", command]))?.stdout.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    func properties(_ serial: String) async -> [String: String] {
        let output = await shell(serial, "getprop")
        var props: [String: String] = [:]
        // Формат: [ro.product.model]: [Pixel 7a]
        for line in output.split(separator: "\n") {
            guard let keyEnd = line.range(of: "]: ["), line.hasPrefix("["), line.hasSuffix("]") else { continue }
            let key = line[line.index(after: line.startIndex)..<keyEnd.lowerBound]
            let value = line[keyEnd.upperBound..<line.index(before: line.endIndex)]
            props[String(key)] = String(value)
        }
        return props
    }

    func reboot(_ serial: String, to target: String? = nil) async throws {
        try await Shell.runChecked(executable, ["-s", serial, "reboot"] + (target.map { [$0] } ?? []))
    }

    /// `adb sideload` с разбором процентов из вывода вида `serving: 'x.zip'  (~47%)`.
    func sideload(_ serial: String, file: URL, log: @escaping @Sendable (String) -> Void,
                  progress: @escaping @Sendable (Double) -> Void) async throws {
        let result = try await run(serial, ["sideload", file.path]) { line in
            if let open = line.range(of: "(~"), let close = line.range(of: "%)", range: open.upperBound..<line.endIndex),
               let percent = Double(line[open.upperBound..<close.lowerBound]) {
                progress(percent / 100)
            } else {
                log(line)
            }
        }
        // Recovery часто обрывает соединение на 47–100% с ненулевым кодом, хотя
        // установка успешна; признак успеха — строка «Total xfer».
        if !result.succeeded && !result.combined.contains("Total xfer") {
            throw ShellError.nonZeroExit(command: "adb sideload", status: result.status, output: result.combined)
        }
        progress(1)
    }

    /// Сбор характеристик устройства.
    func specs(for serial: String) async -> DeviceSpecs {
        let props = await properties(serial)
        func prop(_ keys: String...) -> String? {
            keys.lazy.compactMap { props[$0] }.first { !$0.isEmpty }
        }

        var specs = DeviceSpecs(serial: serial)
        specs.manufacturer = prop("ro.product.manufacturer", "ro.product.vendor.manufacturer") ?? ""
        specs.brand = prop("ro.product.brand", "ro.product.vendor.brand") ?? ""
        specs.model = prop("ro.product.model", "ro.product.vendor.model") ?? serial
        specs.marketName = prop("ro.product.marketname", "ro.product.vendor.marketname",
                                "ro.vendor.product.display", "ro.config.marketing_name")
        specs.codename = prop("ro.product.device", "ro.product.vendor.device", "ro.build.product") ?? ""
        var candidates: [String] = []
        for key in ["ro.product.device", "ro.product.vendor.device", "ro.build.product",
                    "ro.lineage.device", "ro.product.board", "ro.product.name"] {
            if let value = props[key]?.lowercased(), !value.isEmpty, !candidates.contains(value) {
                candidates.append(value)
            }
        }
        specs.codenameCandidates = candidates

        specs.androidVersion = prop("ro.build.version.release_or_codename", "ro.build.version.release") ?? "?"
        specs.sdkLevel = prop("ro.build.version.sdk").flatMap(Int.init)
        specs.securityPatch = prop("ro.build.version.security_patch")
        specs.buildID = prop("ro.build.display.id", "ro.build.id")
        specs.fingerprint = prop("ro.build.fingerprint")
        specs.romName = prop("ro.lineage.display.version", "ro.modversion", "ro.build.version.incremental")

        specs.chipset = prop("ro.soc.model", "ro.hardware.chipname", "ro.board.platform", "ro.hardware")
        if let manufacturer = prop("ro.soc.manufacturer"), let model = specs.chipset,
           !model.lowercased().contains(manufacturer.lowercased()) {
            specs.chipset = "\(manufacturer) \(model)"
        }
        specs.cpuABI = prop("ro.product.cpu.abi")
        specs.slotSuffix = prop("ro.boot.slot_suffix")
        specs.trebleEnabled = prop("ro.treble.enabled").map { $0 == "true" }
        specs.dynamicPartitions = prop("ro.boot.dynamic_partitions").map { $0 == "true" }
        specs.vndkVersion = prop("ro.vndk.version")
        specs.verifiedBootState = prop("ro.boot.verifiedbootstate")
        if let locked = prop("ro.boot.flash.locked") {
            specs.bootloaderLocked = locked == "1"
        } else if let state = specs.verifiedBootState {
            specs.bootloaderLocked = state == "green"
        }
        specs.oemUnlockAllowed = prop("sys.oem_unlock_allowed").map { $0 == "1" }

        // Остальное — одной командой, чтобы не гонять adb десятки раз.
        let marker = "@@ROMFORGE@@"
        let script = [
            "nproc", "grep MemTotal /proc/meminfo", "df -k /data | tail -1", "wm size", "wm density",
            "dumpsys battery", "uname -r",
        ].joined(separator: "; echo \(marker); ")
        let sections = await shell(serial, script).components(separatedBy: marker)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        func section(_ index: Int) -> String { index < sections.count ? sections[index] : "" }

        specs.cpuCores = Int(section(0))
        if let kb = section(1).split(separator: " ").compactMap({ Int64($0) }).first {
            specs.ramBytes = kb * 1024
        }
        let df = section(2).split(separator: " ").compactMap { Int64($0) }
        if df.count >= 3 {
            specs.storageTotalBytes = df[0] * 1024
            specs.storageFreeBytes = df[2] * 1024
        }
        let sizeLines = section(3).split(separator: "\n")
        let sizeLine = sizeLines.first { $0.contains("Override") } ?? sizeLines.first
        specs.screenResolution = sizeLine?.split(separator: ":").last.map {
            $0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "x", with: " × ")
        }
        specs.screenDensity = section(4).split(separator: "\n").first?
            .split(separator: ":").last.flatMap { Int($0.trimmingCharacters(in: .whitespaces)) }

        var battery: [String: String] = [:]
        for line in section(5).split(separator: "\n") {
            let pair = line.split(separator: ":", maxSplits: 1)
            if pair.count == 2 {
                battery[pair[0].trimmingCharacters(in: .whitespaces)] = pair[1].trimmingCharacters(in: .whitespaces)
            }
        }
        specs.batteryLevel = battery["level"].flatMap(Int.init)
        specs.batteryTemperature = battery["temperature"].flatMap(Double.init).map { $0 / 10 }
        specs.batteryVoltage = battery["voltage"].flatMap(Double.init).map { $0 / 1000 }
        specs.batteryHealth = battery["health"].flatMap(Int.init).map {
            switch $0 {
            case 2: "Хорошее"
            case 3: "Перегрев"
            case 4: "Изношена"
            case 5: "Перенапряжение"
            case 7: "Холодная"
            default: "Неизвестно"
            }
        }
        specs.kernel = section(6).isEmpty ? nil : section(6)
        return specs
    }
}

/// Обёртка над fastboot. Важно: fastboot пишет ответы getvar в stderr.
struct Fastboot: Sendable {
    let executable: URL

    func devices() async -> [ConnectedDevice] {
        guard let result = try? await Shell.run(executable, ["devices"]) else { return [] }
        return result.combined.split(separator: "\n").compactMap { line in
            let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" }).map(String.init)
            guard parts.count >= 2, parts[1] == "fastboot" || parts[1] == "fastbootd" else { return nil }
            return ConnectedDevice(serial: parts[0], mode: parts[1] == "fastbootd" ? .fastbootd : .bootloader)
        }
    }

    func getvar(_ serial: String, _ name: String) async -> String? {
        guard let result = try? await Shell.run(executable, ["-s", serial, "getvar", name]) else { return nil }
        for line in result.combined.split(separator: "\n") {
            // "unlocked: yes" или "(bootloader) unlocked:yes"
            let cleaned = line.replacingOccurrences(of: "(bootloader) ", with: "")
            let pair = cleaned.split(separator: ":", maxSplits: 1)
            if pair.count == 2, pair[0].trimmingCharacters(in: .whitespaces) == name {
                return pair[1].trimmingCharacters(in: .whitespaces)
            }
        }
        return nil
    }

    func isUnlocked(_ serial: String) async -> Bool? {
        guard let value = await getvar(serial, "unlocked") else { return nil }
        return value == "yes"
    }

    @discardableResult
    func run(_ serial: String, _ arguments: [String],
             onLine: (@Sendable (String) -> Void)? = nil) async throws -> ShellResult {
        try await Shell.runChecked(executable, ["-s", serial] + arguments, onLine: onLine)
    }
}
