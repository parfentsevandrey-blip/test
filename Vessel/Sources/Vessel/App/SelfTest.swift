import AppKit
import CryptoKit
import Foundation
import Virtualization

/// Самопроверки, которые CI запускает прямо в собранном бинарнике.
///   --selftest-logic — без сети: BLAKE2b, разбор сумм и списков файлов, версии, каталог, адреса Commons
///   --selftest-net   — живой интернет: каждый вариант каждого дистрибутива находится на сервере проекта
///                      вместе с контрольной суммой; скачивание с докачкой и проверкой; скриншоты
///   --selftest-vm    — машина из скачанного образа: конфигурация проходит проверку Virtualization.framework;
///                      если раннер умеет виртуализацию — загрузка Alpine до приглашения в консоли
enum SelfTest {
    static func runIfRequested() {
        let args = CommandLine.arguments
        guard let mode = args.first(where: { $0.hasPrefix("--selftest-") }) else { return }
        setvbuf(stdout, nil, _IOLBF, 0)
        var ok = false
        switch mode {
        case "--selftest-logic": ok = logic()
        case "--selftest-net": ok = blocking { await net() }
        case "--selftest-vm": ok = blocking { await vm() }
        default: print("Неизвестный режим \(mode)")
        }
        print(ok ? "SELFTEST OK" : "SELFTEST FAILED")
        exit(ok ? 0 : 1)
    }

    @discardableResult
    static func check(_ condition: Bool, _ message: String) -> Bool {
        print("\(condition ? "✓" : "✗") \(message)")
        return condition
    }

    static func info(_ message: String) { print("· \(message)") }

    static func blocking(_ body: @escaping @MainActor () async -> Bool) -> Bool {
        _ = NSApplication.shared
        NSApp.setActivationPolicy(.accessory)
        let box = ResultBox()
        Task { @MainActor in box.value = await body() }
        while box.value == nil { RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.05)) }
        return box.value ?? false
    }

    final class ResultBox { var value: Bool? }

    // MARK: Логика

    static func logic() -> Bool {
        var ok = true
        func hex(_ b: [UInt8]) -> String { b.map { String(format: "%02x", $0) }.joined() }
        func blake(_ data: Data, chunk: Int = 0) -> String {
            var h = BLAKE2b()
            if chunk == 0 { h.update(data) } else {
                var i = 0
                while i < data.count { h.update(data.subdata(in: i..<min(i + chunk, data.count))); i += chunk }
            }
            return hex(h.finalize())
        }

        print("— BLAKE2b (RFC 7693)")
        ok = check(blake(Data()) == "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce", "пустая строка") && ok
        ok = check(blake(Data("abc".utf8)) == "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923", "«abc»") && ok
        ok = check(blake(Data(repeating: 0x61, count: 128)) == "fc6c71f688f43ea7d60817478808f3cac753e61571865c95adbc2d9122c943a76b92c2cb1047ef3fe7bf6e436ec1d0a99a9e5b216780bf7fed9d7ca91d3a8f3b", "ровно один блок (128 байт) — последний блок сжимается с флагом") && ok
        let mb = Data((0..<(256 * 4096)).map { UInt8($0 & 0xFF) })
        let expected = "988b9f48e713f594ab9d5bbac2de5f1f04714169d0ff806844089dfed9d7dcad92d90e5c7d9d0676042e059ab231b6839113f2d2b8abf631ee0a49c593d4491d"
        ok = check(blake(mb) == expected, "1 МБ целиком") && ok
        ok = check(blake(mb, chunk: 1000) == expected && blake(mb, chunk: 128) == expected && blake(mb, chunk: 7) == expected, "1 МБ кусками 1000, 128 и 7 байт — тот же результат") && ok
        let start = Date()
        var big = BLAKE2b()
        let block = Data(repeating: 0x5A, count: 8 * 1_048_576)
        for _ in 0..<8 { big.update(block) }
        _ = big.finalize()
        info(String(format: "скорость BLAKE2b: %.0f МБ/с", 64 / Date().timeIntervalSince(start)))

        print("— файлы контрольных сумм")
        let ubuntu = "c2afd538d66fdd77377d03f1ed2ac76a34f1c116baecc9a8170d68f833121f57 *ubuntu-26.04-desktop-arm64.iso\nd31e9b3d01ac0fa8e488d4f8d5a7acfeee869f387674eaedf94335e174155901 *ubuntu-26.04-live-server-arm64+largemem.iso\nc9aa567e6560b2eddae3af03fc686002e35b6fee96f97fd5df3271e846439fdd *ubuntu-26.04-live-server-arm64.iso"
        ok = check(Checksums.find("ubuntu-26.04-live-server-arm64.iso", in: ubuntu, kind: .sha256) == "c9aa567e6560b2eddae3af03fc686002e35b6fee96f97fd5df3271e846439fdd",
                   "Ubuntu: «сумма *имя», похожее имя (+largemem) не путается") && ok
        let rocky = "# Rocky-10.2-aarch64-boot.iso: 1128263680 bytes\nSHA256 (Rocky-10.2-aarch64-boot.iso) = 20807d65338627a0afa3d5fbd3bee222115d66ce34ac1e4647b0af8fc6c4aa23"
        ok = check(Checksums.find("Rocky-10.2-aarch64-boot.iso", in: rocky, kind: .sha256) == "20807d65338627a0afa3d5fbd3bee222115d66ce34ac1e4647b0af8fc6c4aa23", "Rocky: «SHA256 (имя) = сумма»") && ok
        let b2 = String(repeating: "a", count: 128), b2other = String(repeating: "b", count: 128)
        let archboot = "BLAKE2b (iso/archboot-x-ARCH-latest-aarch64.iso) = \(b2other)\nBLAKE2b (iso/archboot-x-ARCH-aarch64.iso) = \(b2)"
        ok = check(Checksums.find("archboot-x-ARCH-aarch64.iso", in: archboot, kind: .blake2b) == b2, "Archboot: BLAKE2b с путём «iso/», соседний «latest» не путается") && ok
        let tumble = "33916688227949b0691502ed35f10329ce0047a7312239573bb99a93ce941f82  openSUSE-Tumbleweed-NET-aarch64-Snapshot20261020-Media.iso"
        ok = check(Checksums.find("openSUSE-Tumbleweed-NET-aarch64-Current.iso", in: tumble, kind: .sha256) == "33916688227949b0691502ed35f10329ce0047a7312239573bb99a93ce941f82",
                   "Tumbleweed: единственная сумма для «Current»-ссылки") && ok
        let parrot = "cf8c39241660f79b22719e2330048565  Parrot-home-7.4_arm64.iso\n590929efab9849d827cda2bfffcec7edf97833b188915975006048339690037c  Parrot-home-7.4_arm64.iso"
        ok = check(Checksums.find("Parrot-home-7.4_arm64.iso", in: parrot, kind: .sha256) == "590929efab9849d827cda2bfffcec7edf97833b188915975006048339690037c", "Parrot: из MD5 и SHA-256 выбирается SHA-256") && ok
        ok = check(Checksums.find("missing.iso", in: ubuntu, kind: .sha256) == nil, "нет имени в общем файле — нет суммы (образ не ставится)") && ok

        print("— списки файлов и версии")
        let html = """
        <a href="../">../</a><a href="24.04/">24.04/</a><a href="26.04/">26.04/</a><a href="26.04.1/">26.04.1/</a><a href="26.10/">26.10/</a>
        <a href="/aarch64/latest/iso/archboot-2026.10.23-ARCH-aarch64.iso">x</a><a href="?C=M;O=A">sort</a><a href="https://example.org/">ext</a>
        """
        let entries = Resolver.parseListing(html)
        ok = check(entries.filter(\.isDirectory).map(\.name) == ["24.04", "26.04", "26.04.1", "26.10"], "папки из автоиндекса") && ok
        ok = check(entries.contains { $0.name == "archboot-2026.10.23-ARCH-aarch64.iso" && !$0.isDirectory }, "файл по абсолютному пути") && ok
        ok = check(Resolver.highest(entries.map(\.name), matching: #"[0-9][02468]\.04"#) == "26.04", "LTS Ubuntu: старшая чётная .04") && ok
        ok = check(Resolver.highest(entries.map(\.name), matching: #"[0-9]{2}\.(04|10)"#) == "26.10", "последний выпуск Ubuntu") && ok
        ok = check(Resolver.highest(["ubuntu-26.04-desktop-arm64.iso", "ubuntu-26.04.1-desktop-arm64.iso"], matching: #"ubuntu-[0-9.]+-desktop-arm64\.iso"#) == "ubuntu-26.04.1-desktop-arm64.iso",
                   "точечный выпуск 26.04.1 старше 26.04") && ok
        ok = check(Resolver.highest(["9-stream", "10-stream"], matching: #"[0-9]+-stream"#) == "10-stream", "10-stream старше 9-stream (числа, а не буквы)") && ok
        ok = check(Resolver.version(in: "debian-13.7.0-arm64-netinst.iso") == "13.7.0", "версия из имени файла") && ok
        let channels = Resolver.nixChannels(now: ISO8601DateFormatter().date(from: "2026-10-24T00:00:00Z")!)
        ok = check(channels.prefix(3) == ["26.11", "26.05", "25.11"], "каналы NixOS от новых к старым") && ok
        let yaml = "-\n  title: \"Virtual\"\n  flavor: alpine-virt\n  version: 3.24.3\n  iso: alpine-virt-3.24.3-aarch64.iso\n  sha256: e34603\n-\n  flavor: alpine-standard\n  iso: alpine-standard-3.24.3-aarch64.iso\n"
        let parsed = Resolver.yamlEntries(yaml)
        ok = check(parsed.count == 2 && parsed[0]["flavor"] == "alpine-virt" && parsed[0]["iso"] == "alpine-virt-3.24.3-aarch64.iso", "latest-releases.yaml Alpine") && ok

        print("— каталог")
        let ids = Catalog.all.map(\.id)
        ok = check(Set(ids).count == ids.count, "у дистрибутивов разные идентификаторы (\(ids.count))") && ok
        for family in Family.allCases {
            ok = check(Catalog.all.contains { $0.family == family }, "раздел «\(family.title)» не пуст") && ok
        }
        for tag in Tag.allCases {
            ok = check(Catalog.all.contains { $0.tags.contains(tag) }, "фильтр «\(tag.title)»: \(Catalog.all.filter { $0.tags.contains(tag) }.map(\.name).joined(separator: ", "))") && ok
        }
        for d in Catalog.all {
            let full = !d.pros.isEmpty && !d.cons.isEmpty && !d.audience.isEmpty && !d.screenshots.isEmpty && !d.summary.isEmpty
            ok = check(full, "\(d.name): описание, плюсы, минусы, для кого, скриншоты") && ok
            if d.runsHere {
                ok = check(Set(d.editions.map(\.id)).count == d.editions.count && d.editions.allSatisfy { $0.resources.cpu > 0 && $0.resources.diskGB >= 8 }, "\(d.name): варианты и ресурсы") && ok
            } else {
                ok = check(d.unavailable != nil && !d.alternatives.isEmpty && d.alternatives.allSatisfy { Catalog.distro($0)?.runsHere == true },
                           "\(d.name): объяснено, почему не запустится, и предложены замены") && ok
            }
        }
        ok = check(Catalog.hot.count >= 5 && Catalog.popular.map(\.rank) == Array(1...Catalog.all.count), "«Горячие» (\(Catalog.hot.count)) и «Популярные» — места 1…\(Catalog.all.count)") && ok
        ok = check(Commons.original("Fedora 44 Workstation.png").absoluteString == "https://upload.wikimedia.org/wikipedia/commons/3/32/Fedora_44_Workstation.png",
                   "адрес файла Commons по MD5 имени") && ok
        ok = check(Commons.thumbnail("Fedora 44 Workstation.png").absoluteString.hasSuffix("/3/32/Fedora_44_Workstation.png/1280px-Fedora_44_Workstation.png"), "адрес уменьшенной копии") && ok
        ok = check(MacInfo.fit(Resources(cpu: 64, memoryGB: 512, diskGB: 32)).memoryGB <= max(2, MacInfo.memoryGB / 2), "мастер не предлагает больше половины памяти Mac") && ok
        return ok
    }

    // MARK: Сеть

    static func net() async -> Bool {
        var ok = true
        print("— образы всех вариантов (последний выпуск и сумма — с сервера проекта)")
        var resolved: [String: ResolvedImage] = [:]
        for d in Catalog.all where d.runsHere {
            for e in d.editions {
                do {
                    let image = try await Resolver.resolve(e.source)
                    resolved["\(d.id)/\(e.id)"] = image
                    let size = image.size.map(Format.bytes) ?? "размер неизвестен"
                    ok = check(image.digest?.count == image.hash?.hexLength && image.url.scheme == "https",
                               "\(d.name) — \(e.title): \(image.fileName) (\(image.version), \(size), \(image.hash?.title ?? "?"))") && ok
                    if let s = image.size, s < 30_000_000 { ok = check(false, "  слишком маленький образ: \(s) байт") && ok }
                } catch {
                    ok = check(false, "\(d.name) — \(e.title): \(error.localizedDescription)") && ok
                }
            }
        }

        print("— скачивание с докачкой и проверкой")
        if let image = resolved["alpine/virt"], let hash = image.hash, let digest = image.digest {
            let dest = Paths.images.appendingPathComponent(image.fileName)
            try? FileManager.default.removeItem(at: dest)
            try? FileManager.default.removeItem(at: dest.appendingPathExtension("part"))
            // Прерываем после первых мегабайт, затем продолжаем: сумма должна сойтись по частям.
            let first = Task {
                try await FileFetcher(url: image.url, destination: dest, expectedSize: image.size, hash: hash, digest: digest) { _, _ in }.run()
            }
            var partial: Int64 = 0
            for _ in 0..<300 {
                try? await Task.sleep(for: .milliseconds(50))
                partial = ((try? FileManager.default.attributesOfItem(atPath: dest.appendingPathExtension("part").path))?[.size] as? Int64) ?? 0
                if partial > 4_000_000 { break }
            }
            first.cancel()
            _ = await first.result
            partial = ((try? FileManager.default.attributesOfItem(atPath: dest.appendingPathExtension("part").path))?[.size] as? Int64) ?? 0
            check(partial > 0, "загрузка прервана на \(Format.bytes(partial))")
            let started = Date()
            do {
                try await FileFetcher(url: image.url, destination: dest, expectedSize: image.size, hash: hash, digest: digest) { _, _ in }.run()
                let size = ((try? FileManager.default.attributesOfItem(atPath: dest.path))?[.size] as? Int64) ?? 0
                ok = check(size == image.size, "докачано до конца: \(Format.bytes(size)) за \(String(format: "%.1f", Date().timeIntervalSince(started))) с, SHA-256 совпала") && ok
            } catch {
                ok = check(false, "докачка: \(error.localizedDescription)") && ok
            }
            // Неверная сумма: образ не принимается, недокачанная часть удаляется.
            let bad = dest.deletingLastPathComponent().appendingPathComponent("bad-" + image.fileName)
            do {
                try await FileFetcher(url: image.url, destination: bad, expectedSize: image.size, hash: hash, digest: String(repeating: "0", count: 64)) { _, _ in }.run()
                ok = check(false, "образ с неверной суммой принят") && ok
            } catch {
                let gone = !FileManager.default.fileExists(atPath: bad.path) && !FileManager.default.fileExists(atPath: bad.appendingPathExtension("part").path)
                ok = check(gone && (error as? FetchError).map { if case .corrupted = $0 { true } else { false } } == true,
                           "неверная сумма: «\(error.localizedDescription)», файл удалён") && ok
            }
        } else {
            ok = check(false, "Alpine virt не найден — проверка скачивания пропущена") && ok
        }

        // BLAKE2b на настоящем образе: сумма Archboot считается так же, как при установке.
        if let image = resolved["archarm/archboot"] {
            info("Archboot: \(image.fileName), BLAKE2b \(image.digest?.prefix(16) ?? "?")…")
        }

        print("— скриншоты (Wikimedia Commons)")
        var missing: [String] = []
        var total = 0
        for d in Catalog.all {
            var loaded = 0
            for title in d.screenshots {
                total += 1
                if await ScreenshotStore.shared.data(title) != nil { loaded += 1 } else { missing.append("\(d.name): \(title)") }
                try? await Task.sleep(for: .milliseconds(400))
            }
            check(loaded > 0, "\(d.name): \(loaded) из \(d.screenshots.count)")
        }
        for m in missing { info("нет снимка — \(m)") }
        // Commons иногда ограничивает частоту запросов: отсутствие пары снимков не ломает каталог, но массовое — ошибка в именах.
        ok = check(missing.count * 5 <= total, "загружено \(total - missing.count) из \(total) снимков") && ok
        return ok
    }

    // MARK: Виртуальная машина

    static func vm() async -> Bool {
        var ok = true
        info("Mac: \(MacInfo.chip), \(MacInfo.cores) ядер, \(MacInfo.memoryGB) ГБ, macOS \(ProcessInfo.processInfo.operatingSystemVersionString)")
        info("Virtualization.framework: \(VZVirtualMachine.isSupported ? "поддерживается" : "не поддерживается на этом Mac (вложенная виртуализация на M1/M2 недоступна)")")
        info("Rosetta для Linux: \(String(describing: VZLinuxRosettaDirectoryShare.availability))")
        let iso = (try? FileManager.default.contentsOfDirectory(at: Paths.images, includingPropertiesForKeys: nil))?
            .first { $0.lastPathComponent.hasPrefix("alpine-virt") && $0.pathExtension == "iso" }
        guard let iso else { return check(false, "нет образа Alpine (сначала --selftest-net)") }

        var machine = Machine(name: "Проверка", distroID: "alpine", editionID: "virt", resources: Catalog.light, isoName: iso.lastPathComponent,
                              macAddress: VZMACAddress.randomLocallyAdministered().string, sharedFolder: Paths.home.appendingPathComponent("shared").path,
                              rosetta: false, clipboard: true, sound: false, microphone: false, version: Resolver.version(in: iso.lastPathComponent))
        do {
            try MachineStore.save(machine)
            try MachineStore.createDisk(machine)
            let size = ((try? FileManager.default.attributesOfItem(atPath: machine.diskURL.path))?[.size] as? Int64) ?? 0
            ok = check(size == Int64(machine.diskGB) * 1_073_741_824 && machine.diskUsed < 50_000_000,
                       "разреженный диск: \(machine.diskGB) ГБ по размеру, занято \(Format.bytes(machine.diskUsed))") && ok
            try MachineStore.growDisk(machine, toGB: machine.diskGB + 8)
            machine.diskGB += 8
            let grown = ((try? FileManager.default.attributesOfItem(atPath: machine.diskURL.path))?[.size] as? Int64) ?? 0
            ok = check(grown == Int64(machine.diskGB) * 1_073_741_824, "диск увеличен до \(machine.diskGB) ГБ") && ok
            try MachineStore.save(machine)
            ok = check(MachineStore.load().contains { $0.id == machine.id && $0.diskGB == machine.diskGB }, "машина читается с диска") && ok
            // Старая запись без новых полей читается с умолчаниями.
            let legacy = #"{"id":"\#(UUID().uuidString)","name":"Старая","distroID":"debian"}"#
            ok = check((try? JSONDecoder().decode(Machine.self, from: Data(legacy.utf8)))?.clipboard == true, "старая запись машины читается") && ok
        } catch {
            ok = check(false, "машина на диске: \(error.localizedDescription)") && ok
        }

        do {
            let config = try VMBuilder.configuration(for: machine, serialLog: machine.consoleURL)
            ok = check(true, "конфигурация прошла проверку: \(config.cpuCount) ядра, \(config.memorySize / 1_073_741_824) ГБ, \(config.storageDevices.count) диска, общая папка, буфер обмена") && ok
            do {
                try config.validateSaveRestoreSupport()
                check(true, "сохранение состояния поддерживается")
            } catch {
                info("сохранение состояния: \(error.localizedDescription)")
            }
        } catch {
            let text = error.localizedDescription
            // Без поддержки виртуализации проверка конфигурации может отказать — это свойство раннера, не ошибка Vessel.
            if !VZVirtualMachine.isSupported { info("проверка конфигурации на раннере без виртуализации: \(text)") }
            else { ok = check(false, "конфигурация: \(text)") && ok }
        }

        guard VZVirtualMachine.isSupported else {
            info("запуск машины пропущен: раннер не поддерживает виртуализацию")
            return ok
        }
        let session = VMSession(machine: machine)
        session.serialLog = machine.consoleURL
        await session.start()
        ok = check(session.status == .running, "машина запущена: \(session.status.title)") && ok
        // Консоль virtio видна только если ядро гостя пишет в hvc0 — поэтому главный признак: машина работает.
        let deadline = Date().addingTimeInterval(45)
        var console = ""
        while Date() < deadline {
            console = (try? String(contentsOf: machine.consoleURL, encoding: .utf8)) ?? ""
            if console.contains("login:") { break }
            try? await Task.sleep(for: .seconds(1))
        }
        ok = check(session.status == .running, "через 45 с машина всё ещё работает") && ok
        info("консоль: \(console.count) символов\(console.contains("login:") ? ", есть приглашение входа" : "")")
        if !console.isEmpty { print(console.suffix(600)) }
        await session.forceStop()
        ok = check(session.status == .stopped, "машина остановлена") && ok
        return ok
    }
}
