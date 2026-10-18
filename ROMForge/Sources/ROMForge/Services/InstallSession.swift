import Foundation

enum InstallError: LocalizedError {
    case unsupported(String)
    case deviceLost(String)
    case missingFile(String)

    var errorDescription: String? {
        switch self {
        case .unsupported(let message), .deviceLost(let message), .missingFile(let message): message
        }
    }
}

/// Пошаговая установка прошивки. Всё, что можно сделать командами adb/fastboot,
/// выполняется автоматически; там, где нужен палец пользователя (кнопки
/// громкости, пункты меню recovery), показывается короткая инструкция, а
/// приложение по возможности само замечает, что шаг выполнен.
@Observable
@MainActor
final class InstallSession: Identifiable {
    enum StepID: String {
        case tools, backup, download, unlock, recovery, bootRecovery, wipe, sideload, flashFactory, relock, reboot
    }

    enum StepState: Equatable {
        case pending, running, waiting, done, skipped(String), failed(String)
    }

    struct Step: Identifiable {
        let id: StepID
        var title: String
        var symbol: String
        var state: StepState = .pending
        var progress: Double?
        var detail: String?
    }

    struct Prompt: Equatable {
        var title: String
        var message: String
        var bullets: [String] = []
        var symbol: String = "hand.tap"
        var primary: String? = "Готово"
        var secondary: String?
        var link: URL?
        var destructive = false
        var autoDetect = false
    }

    enum Choice { case primary, secondary }

    enum Outcome: Equatable {
        case success, cancelled, failed(String)
    }

    struct LogLine: Identifiable {
        let id = UUID()
        let date = Date()
        let text: String
        let isCommand: Bool
    }

    let id = UUID()
    let project: ROMProject
    let build: ROMBuild?
    let specs: DeviceSpecs

    private(set) var steps: [Step]
    private(set) var prompt: Prompt?
    private(set) var log: [LogLine] = []
    private(set) var outcome: Outcome?
    private(set) var currentIndex = 0

    var isActive: Bool { outcome == nil }
    var failedIndex: Int? {
        steps.firstIndex { step in
            if case .failed = step.state { return true }
            return false
        }
    }

    private unowned let model: AppModel
    private let localPackage: URL?
    private let localRecovery: URL?
    private var serial: String
    private var fastbootSerial: String
    private var localFiles: [String: URL] = [:]
    private var recoveryBootedTemporarily = false
    private var runTask: Task<Void, Never>?
    private var promptContinuation: CheckedContinuation<Choice, Error>?
    private var detectTask: Task<Void, Never>?

    init(model: AppModel, project: ROMProject, build: ROMBuild?, specs: DeviceSpecs,
         localPackage: URL?, localRecovery: URL?) {
        self.model = model
        self.project = project
        self.build = build
        self.specs = specs
        self.localPackage = localPackage
        self.localRecovery = localRecovery
        self.serial = specs.serial
        self.fastbootSerial = specs.serial

        let kind = build?.kind ?? .sideloadZip
        var steps: [Step] = [
            Step(id: .tools, title: "Подготовка инструментов", symbol: "wrench.and.screwdriver"),
            Step(id: .backup, title: "Резервная копия и подтверждение", symbol: "externaldrive.badge.checkmark"),
            Step(id: .download, title: "Загрузка и проверка прошивки", symbol: "arrow.down.circle"),
            Step(id: .unlock, title: "Разблокировка загрузчика", symbol: "lock.open"),
        ]
        switch kind {
        case .sideloadZip:
            steps += [
                Step(id: .recovery, title: "Установка recovery", symbol: "cross.case"),
                Step(id: .bootRecovery, title: "Загрузка в recovery", symbol: "arrow.triangle.2.circlepath"),
                Step(id: .wipe, title: "Сброс данных", symbol: "trash"),
                Step(id: .sideload, title: "Установка системы", symbol: "square.and.arrow.down.on.square"),
            ]
        case .factoryImage:
            steps += [
                Step(id: .flashFactory, title: "Прошивка заводских образов", symbol: "bolt.horizontal"),
                Step(id: .relock, title: "Блокировка загрузчика", symbol: "lock"),
            ]
        }
        steps.append(Step(id: .reboot, title: "Первый запуск", symbol: "sparkles"))
        self.steps = steps
    }

    // MARK: - Управление

    func start(from index: Int = 0) {
        runTask?.cancel()
        outcome = nil
        for i in index..<steps.count {
            steps[i].state = .pending
            steps[i].progress = nil
            steps[i].detail = nil
        }
        runTask = Task { [weak self] in await self?.run(from: index) }
    }

    func retry() {
        start(from: failedIndex ?? 0)
    }

    func cancel() {
        runTask?.cancel()
        respond(throwing: CancellationError())
    }

    func respond(_ choice: Choice) {
        promptContinuation?.resume(returning: choice)
        promptContinuation = nil
    }

    private func respond(throwing error: Error) {
        promptContinuation?.resume(throwing: error)
        promptContinuation = nil
    }

    private func run(from start: Int) async {
        for index in start..<steps.count {
            currentIndex = index
            steps[index].state = .running
            appendLog("— \(steps[index].title)", isCommand: true)
            do {
                if let skipReason = try await perform(steps[index].id) {
                    steps[index].state = .skipped(skipReason)
                } else {
                    steps[index].state = .done
                }
                steps[index].progress = nil
            } catch {
                prompt = nil
                if Task.isCancelled || error is CancellationError {
                    steps[index].state = .failed("Отменено")
                    outcome = .cancelled
                } else {
                    let message = error.localizedDescription
                    steps[index].state = .failed(message)
                    appendLog("Ошибка: \(message)", isCommand: false)
                    outcome = .failed(message)
                }
                return
            }
        }
        outcome = .success
    }

    /// Возвращает причину пропуска шага или nil, если шаг выполнен.
    private func perform(_ step: StepID) async throws -> String? {
        switch step {
        case .tools: return try await prepareTools()
        case .backup: return try await confirmBackup()
        case .download: return try await download()
        case .unlock: return try await unlockBootloader()
        case .recovery: return try await installRecovery()
        case .bootRecovery: return try await bootRecovery()
        case .wipe: return try await wipe()
        case .sideload: return try await sideload()
        case .flashFactory: return try await flashFactory()
        case .relock: return try await relock()
        case .reboot: return try await finish()
        }
    }

    // MARK: - Шаги

    private func prepareTools() async throws -> String? {
        let directory = try await model.tools.prepare()
        appendLog("adb и fastboot: \(directory.path)", isCommand: false)
        return nil
    }

    private func confirmBackup() async throws -> String? {
        var bullets = [
            "Все данные на телефоне будут удалены: фото, сообщения, приложения.",
            "Сохраните важное в облако или на Mac перед продолжением.",
            "Не отключайте кабель до окончания установки.",
        ]
        if let level = specs.batteryLevel, level < 50 {
            bullets.append("Заряд батареи \(level)% — лучше зарядить телефон хотя бы до 50%.")
        }
        _ = try await ask(Prompt(
            title: "Перед началом",
            message: "Установка \(project.name) на \(specs.displayName) полностью заменит текущую систему.",
            bullets: bullets,
            symbol: "exclamationmark.triangle.fill",
            primary: "Данные сохранены, продолжить",
            destructive: true
        ))
        return nil
    }

    private func download() async throws -> String? {
        if let localPackage {
            localFiles["package"] = localPackage
            if let localRecovery { localFiles["recovery-local"] = localRecovery }
            return "Используется выбранный файл \(localPackage.lastPathComponent)"
        }
        guard let build else { throw InstallError.missingFile("Не выбрана сборка") }

        let folder = AppPaths.downloads
            .appendingPathComponent(project.id, isDirectory: true)
            .appendingPathComponent((build.package.fileName as NSString).deletingPathExtension, isDirectory: true)
        let files = [build.package] + build.recoveryImages
        let totalBytes = files.reduce(Int64(0)) { $0 + ($1.size ?? 0) }
        var doneBytes: Int64 = 0

        for file in files {
            let target = folder.appendingPathComponent(file.fileName)
            let key = file == build.package ? "package" : file.partition
            let fileBytes = file.size ?? 0

            if FileManager.default.fileExists(atPath: target.path),
               (try? await Checksum.verify(file, at: target)) == true {
                appendLog("Уже скачан: \(file.fileName)", isCommand: false)
                localFiles[key] = target
                doneBytes += fileBytes
                continue
            }

            appendLog("Скачивание \(file.url.absoluteString)", isCommand: true)
            let base = doneBytes
            try await FileDownloader.download(from: file.url, to: target) { [weak self] progress in
                Task { @MainActor in
                    guard let self else { return }
                    let expected = totalBytes > 0 ? totalBytes : progress.expected
                    let current = base + progress.received
                    self.updateCurrent(
                        progress: expected > 0 ? Double(current) / Double(expected) : nil,
                        detail: "\(file.fileName) · \(current.formattedBytes) из \(expected.formattedBytes) · \(Int64(progress.bytesPerSecond).formattedBytes)/с"
                    )
                }
            }
            updateCurrent(progress: nil, detail: "Проверка контрольной суммы \(file.fileName)…")
            let verified = try await Checksum.verify(file, at: target) { [weak self] fraction in
                Task { @MainActor in self?.updateCurrent(progress: fraction, detail: "Проверка \(file.fileName)…") }
            }
            appendLog(verified ? "Контрольная сумма совпала: \(file.fileName)" : "Для \(file.fileName) нет контрольной суммы",
                      isCommand: false)
            localFiles[key] = target
            doneBytes += fileBytes
        }
        updateCurrent(progress: 1, detail: "Файлы в \(folder.path)")
        return nil
    }

    private func unlockBootloader() async throws -> String? {
        if specs.bootloaderLocked == false {
            return "Загрузчик уже разблокирован"
        }

        switch specs.vendorFamily {
        case .samsung:
            throw InstallError.unsupported(
                "Samsung не поддерживает fastboot: прошивка ставится через Download Mode (Odin/Heimdall). Откройте инструкцию проекта для вашей модели."
            )
        case .huawei:
            throw InstallError.unsupported("Huawei и Honor больше не выдают коды разблокировки загрузчика — установка невозможна.")
        case .xiaomi:
            _ = try await ask(Prompt(
                title: "Разблокировка через Mi Unlock",
                message: "Xiaomi разрешает разблокировку только через официальную утилиту после периода ожидания.",
                bullets: [
                    "Привяжите Mi-аккаунт: Настройки → Для разработчиков → Статус Mi Unlock.",
                    "Дождитесь окончания срока ожидания (обычно 7 дней).",
                    "Разблокируйте загрузчик через Mi Unlock Tool (работает на Windows).",
                    "Снова подключите телефон к Mac и нажмите «Загрузчик разблокирован».",
                ],
                symbol: "lock.open.trianglebadge.exclamationmark",
                primary: "Загрузчик разблокирован",
                link: URL(string: "https://en.miui.com/unlock/")
            ))
            return nil
        case .motorola, .sony:
            let isSony = specs.vendorFamily == .sony
            _ = try await ask(Prompt(
                title: "Нужен код разблокировки",
                message: "\(isSony ? "Sony" : "Motorola") выдаёт код разблокировки на своём сайте.",
                bullets: [
                    "Включите «Заводская разблокировка» в настройках для разработчиков.",
                    "Получите код на сайте производителя (понадобится идентификатор устройства).",
                    "Выполните разблокировку по инструкции с сайта и нажмите «Готово».",
                ],
                symbol: "key",
                link: URL(string: isSony
                          ? "https://developer.sony.com/open-source/aosp-on-xperia-open-devices/get-started/unlock-bootloader"
                          : "https://en-us.support.motorola.com/app/standalone/bootloader/unlock-your-device-a")
            ))
            return nil
        default:
            break
        }

        // 1. Заводская разблокировка в настройках.
        if let adb = model.adb, specs.oemUnlockAllowed != true {
            let serial = self.serial
            _ = try await ask(Prompt(
                title: "Включите «Заводскую разблокировку»",
                message: "Это переключатель, который разрешает разблокировать загрузчик. Приложение заметит изменение само.",
                bullets: [
                    "Настройки → О телефоне → 7 раз нажмите «Номер сборки».",
                    "Настройки → Система → Для разработчиков.",
                    "Включите «Заводская разблокировка» (OEM unlocking) и подтвердите.",
                ],
                symbol: "switch.2",
                autoDetect: true
            ), detect: {
                await adb.shell(serial, "getprop sys.oem_unlock_allowed") == "1"
            })
        }

        // 2. Перезагрузка в fastboot и проверка статуса.
        try await ensureFastboot()
        guard let fastboot = model.fastboot else { throw InstallError.missingFile("fastboot не найден") }
        if await fastboot.isUnlocked(fastbootSerial) == true {
            return "Загрузчик уже разблокирован"
        }

        // 3. Команда разблокировки — подтверждение на самом телефоне.
        let fbSerial = fastbootSerial
        appendLog("fastboot flashing unlock", isCommand: true)
        let unlockTask = Task.detached { () -> Bool in
            if (try? await fastboot.run(fbSerial, ["flashing", "unlock"])) != nil { return true }
            return (try? await fastboot.run(fbSerial, ["oem", "unlock"])) != nil
        }
        _ = try await ask(Prompt(
            title: "Подтвердите на телефоне",
            message: "На экране появится предупреждение о разблокировке.",
            bullets: [
                "Кнопками громкости выберите «Unlock the bootloader».",
                "Нажмите кнопку питания для подтверждения.",
                "Телефон сотрёт данные — это ожидаемо.",
            ],
            symbol: "iphone.radiowaves.left.and.right",
            primary: "Я подтвердил",
            autoDetect: true
        ), detect: { [weak self] in
            guard let self else { return false }
            if await fastboot.isUnlocked(fbSerial) == true { return true }
            return await self.adbDevice() != nil
        })
        _ = await unlockTask.value
        appendLog("Загрузчик разблокирован", isCommand: false)
        return nil
    }

    private func installRecovery() async throws -> String? {
        guard let fastboot = model.fastboot else { throw InstallError.missingFile("fastboot не найден") }

        if let image = localFiles["recovery-local"] {
            try await ensureFastboot()
            // Неизвестный образ безопаснее загрузить временно, не прошивая.
            appendLog("fastboot boot \(image.lastPathComponent)", isCommand: true)
            try await fastboot.run(fastbootSerial, ["boot", image.path], onLine: lineLogger())
            recoveryBootedTemporarily = true
            return nil
        }

        let order = ["dtbo", "vendor_kernel_boot", "vendor_boot", "vbmeta", "recovery", "boot"]
        let images = order.compactMap { partition in localFiles[partition].map { (partition, $0) } }
        if images.isEmpty {
            let choice = try await ask(Prompt(
                title: "Нужен custom recovery",
                message: "Для этой прошивки нет готового образа recovery под ваше устройство.",
                bullets: [
                    "Если TWRP, OrangeFox или другой recovery уже установлен — просто продолжайте.",
                    "Иначе скачайте образ recovery на странице прошивки и начните установку из файла.",
                ],
                symbol: "cross.case",
                primary: "Recovery уже установлен",
                secondary: "Отменить",
                link: project.website
            ))
            if choice == .secondary { throw CancellationError() }
            return "Используется установленный recovery"
        }

        try await ensureFastboot()
        for (index, (partition, file)) in images.enumerated() {
            updateCurrent(progress: Double(index) / Double(images.count), detail: "Прошивка раздела \(partition)…")
            appendLog("fastboot flash \(partition) \(file.lastPathComponent)", isCommand: true)
            try await fastboot.run(fastbootSerial, ["flash", partition, file.path], onLine: lineLogger())
        }
        updateCurrent(progress: 1, detail: build?.recoveryImagesSource)
        return nil
    }

    private func bootRecovery() async throws -> String? {
        if !recoveryBootedTemporarily, let fastboot = model.fastboot {
            appendLog("fastboot reboot recovery", isCommand: true)
            if (try? await fastboot.run(fastbootSerial, ["reboot", "recovery"])) == nil {
                _ = try await ask(Prompt(
                    title: "Выберите Recovery mode",
                    message: "Не удалось перезагрузить телефон автоматически.",
                    bullets: ["Кнопками громкости выберите «Recovery mode».", "Подтвердите кнопкой питания."],
                    symbol: "arrow.up.arrow.down"
                ))
            }
        }
        _ = try await ask(Prompt(
            title: "Ждём recovery",
            message: "Телефон загружается в режим восстановления. Это занимает до минуты.",
            bullets: ["Когда появится меню recovery, нажмите «Recovery открылся»."],
            symbol: "hourglass",
            primary: "Recovery открылся",
            autoDetect: true
        ), detect: { [weak self] in
            await self?.adbDevice(modes: [.recovery, .sideload]) != nil
        })
        return nil
    }

    private func wipe() async throws -> String? {
        _ = try await ask(Prompt(
            title: "Сотрите данные в recovery",
            message: "Управление в recovery — кнопками громкости и питания (или касанием).",
            bullets: [
                "Выберите «Factory reset» → «Format data / factory reset».",
                "Подтвердите форматирование.",
                "В TWRP: Wipe → Format Data → введите «yes».",
                "Вернитесь в главное меню.",
            ],
            symbol: "trash",
            primary: "Данные стёрты",
            destructive: true
        ))
        return nil
    }

    private func sideload() async throws -> String? {
        guard let adb = model.adb else { throw InstallError.missingFile("adb не найден") }
        guard let package = localFiles["package"] else { throw InstallError.missingFile("Файл прошивки не найден — повторите загрузку") }

        _ = try await ask(Prompt(
            title: "Включите ADB sideload",
            message: "В главном меню recovery:",
            bullets: [
                "«Apply update» → «Apply from ADB».",
                "В TWRP: Advanced → ADB Sideload → смахните для старта.",
                "Приложение начнёт установку автоматически.",
            ],
            symbol: "cable.connector",
            primary: nil,
            autoDetect: true
        ), detect: { [weak self] in
            await self?.adbDevice(modes: [.sideload]) != nil
        })

        guard let device = await adbDevice(modes: [.sideload]) else {
            throw InstallError.deviceLost("Телефон пропал из режима sideload. Подключите кабель и повторите шаг.")
        }
        serial = device.serial
        prompt = Prompt(
            title: "Идёт установка",
            message: "Не отключайте кабель. Если recovery спросит про проверку подписи — выберите «Yes».",
            symbol: "arrow.down.app",
            primary: nil
        )
        appendLog("adb sideload \(package.lastPathComponent)", isCommand: true)
        try await adb.sideload(device.serial, file: package, log: lineLogger()) { [weak self] fraction in
            Task { @MainActor in
                self?.updateCurrent(progress: fraction, detail: "Передано \(Int(fraction * 100))%")
            }
        }
        prompt = nil
        return nil
    }

    private func flashFactory() async throws -> String? {
        guard let archive = localFiles["package"], let tools = model.tools.directory else {
            throw InstallError.missingFile("Архив образов не найден — повторите загрузку")
        }
        updateCurrent(progress: nil, detail: "Распаковка архива…")
        let folder = archive.deletingPathExtension()
        if !FileManager.default.fileExists(atPath: folder.path) {
            try await Shell.runChecked(URL(fileURLWithPath: "/usr/bin/ditto"),
                                       ["-x", "-k", archive.path, folder.path])
        }
        guard let script = findFile(named: "flash-all.sh", in: folder) else {
            throw InstallError.missingFile("В архиве нет flash-all.sh")
        }

        try await ensureFastboot()
        updateCurrent(progress: nil, detail: "Прошивка… телефон может перезагружаться — это нормально")
        prompt = Prompt(
            title: "Идёт прошивка",
            message: "Не отключайте телефон. Процесс займёт несколько минут.",
            symbol: "bolt.fill",
            primary: nil
        )
        appendLog("bash flash-all.sh", isCommand: true)
        let path = tools.path + ":" + (ProcessInfo.processInfo.environment["PATH"] ?? "/usr/bin:/bin")
        try await Shell.runChecked(
            URL(fileURLWithPath: "/bin/bash"),
            [script.path],
            environment: ["PATH": path, "ANDROID_SERIAL": fastbootSerial],
            currentDirectory: script.deletingLastPathComponent(),
            onLine: lineLogger()
        )
        prompt = nil
        return nil
    }

    private func relock() async throws -> String? {
        let choice = try await ask(Prompt(
            title: "Заблокировать загрузчик?",
            message: "\(project.name) поддерживает повторную блокировку — это включает проверенную загрузку и защищает от подмены системы.",
            bullets: ["Данные будут ещё раз стёрты (телефон пока пустой).", "Рекомендуется для максимальной безопасности."],
            symbol: "lock.fill",
            primary: "Заблокировать",
            secondary: "Пропустить"
        ))
        if choice == .secondary { return "Загрузчик оставлен разблокированным" }

        try await ensureFastboot()
        guard let fastboot = model.fastboot else { throw InstallError.missingFile("fastboot не найден") }
        let fbSerial = fastbootSerial
        appendLog("fastboot flashing lock", isCommand: true)
        let lockTask = Task.detached { try? await fastboot.run(fbSerial, ["flashing", "lock"]) }
        _ = try await ask(Prompt(
            title: "Подтвердите на телефоне",
            message: "Кнопками громкости выберите «Lock the bootloader» и нажмите питание.",
            symbol: "lock.iphone",
            primary: "Я подтвердил",
            autoDetect: true
        ), detect: {
            await fastboot.isUnlocked(fbSerial) == false
        })
        _ = await lockTask.value
        return nil
    }

    private func finish() async throws -> String? {
        if build?.kind == .factoryImage, let fastboot = model.fastboot {
            appendLog("fastboot reboot", isCommand: true)
            _ = try? await fastboot.run(fastbootSerial, ["reboot"])
        } else if let adb = model.adb, let device = await adbDevice(modes: [.recovery, .sideload]) {
            appendLog("adb reboot", isCommand: true)
            try? await adb.reboot(device.serial)
        }
        _ = try await ask(Prompt(
            title: "Почти готово!",
            message: "\(project.name) установлена. Первый запуск может занять до 10 минут.",
            bullets: [
                "Если телефон остался в recovery — выберите «Reboot system now».",
                "Если recovery предложит установить приложение — выберите «Do not install».",
                "Сервисы Google (если нужны) ставятся отдельно — см. страницу прошивки.",
            ],
            symbol: "checkmark.seal.fill",
            primary: "Завершить"
        ))
        return nil
    }

    // MARK: - Помощники

    /// Показывает инструкцию и ждёт нажатия кнопки или срабатывания автоопределения.
    private func ask(_ prompt: Prompt, detect: (@MainActor () async -> Bool)? = nil) async throws -> Choice {
        try Task.checkCancellation()
        self.prompt = prompt
        steps[currentIndex].state = .waiting
        defer {
            self.prompt = nil
            detectTask?.cancel()
            detectTask = nil
            if steps[currentIndex].state == .waiting { steps[currentIndex].state = .running }
        }
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                promptContinuation = continuation
                if let detect {
                    detectTask = Task { [weak self] in
                        while !Task.isCancelled {
                            try? await Task.sleep(for: .seconds(2))
                            if Task.isCancelled { return }
                            if await detect() {
                                self?.respond(.primary)
                                return
                            }
                        }
                    }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.respond(throwing: CancellationError()) }
        }
    }

    /// Переводит телефон в fastboot: сначала командой adb, при неудаче — просит зажать кнопки.
    private func ensureFastboot() async throws {
        if let device = await fastbootDevice() {
            fastbootSerial = device.serial
            return
        }
        if let adb = model.adb, let device = await adbDevice(modes: [.android, .recovery, .sideload]) {
            appendLog("adb reboot bootloader", isCommand: true)
            try? await adb.reboot(device.serial, to: "bootloader")
            for _ in 0..<30 {
                try await Task.sleep(for: .seconds(2))
                if let device = await fastbootDevice() {
                    fastbootSerial = device.serial
                    return
                }
            }
        }
        _ = try await ask(Prompt(
            title: "Включите режим fastboot",
            message: "Не получилось перезагрузить телефон автоматически.",
            bullets: [
                "Выключите телефон.",
                "Зажмите «Питание» + «Громкость вниз» (на OnePlus — обе кнопки громкости и питание).",
                "Отпустите, когда появится экран fastboot / bootloader.",
            ],
            symbol: "power",
            primary: nil,
            autoDetect: true
        ), detect: { [weak self] in
            await self?.fastbootDevice() != nil
        })
        guard let device = await fastbootDevice() else {
            throw InstallError.deviceLost("Телефон в режиме fastboot не найден.")
        }
        fastbootSerial = device.serial
    }

    private func fastbootDevice() async -> ConnectedDevice? {
        guard let fastboot = model.fastboot else { return nil }
        let list = await fastboot.devices()
        return list.first { $0.serial == serial || $0.serial == fastbootSerial } ?? (list.count == 1 ? list.first : nil)
    }

    private func adbDevice(modes: Set<ConnectedDevice.Mode> = [.android]) async -> ConnectedDevice? {
        guard let adb = model.adb else { return nil }
        let list = await adb.devices().filter { modes.contains($0.mode) }
        return list.first { $0.serial == serial } ?? (list.count == 1 ? list.first : nil)
    }

    private func findFile(named name: String, in folder: URL) -> URL? {
        let enumerator = FileManager.default.enumerator(at: folder, includingPropertiesForKeys: nil)
        while let url = enumerator?.nextObject() as? URL {
            if url.lastPathComponent == name { return url }
        }
        return nil
    }

    private func updateCurrent(progress: Double?, detail: String?) {
        guard steps.indices.contains(currentIndex) else { return }
        steps[currentIndex].progress = progress
        steps[currentIndex].detail = detail
    }

    private func appendLog(_ text: String, isCommand: Bool) {
        log.append(LogLine(text: text, isCommand: isCommand))
        if log.count > 3000 { log.removeFirst(log.count - 3000) }
    }

    /// Колбэк для вывода процессов, приходящего с фоновых потоков.
    private func lineLogger() -> @Sendable (String) -> Void {
        { [weak self] line in
            DispatchQueue.main.async {
                MainActor.assumeIsolated { self?.appendLog(line, isCommand: false) }
            }
        }
    }
}
