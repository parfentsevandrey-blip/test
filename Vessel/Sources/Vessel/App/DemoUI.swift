import Foundation

/// `--demo-ui <экран>`: CI запускает приложение с выдуманными машинами и загрузками (ничего не скачивается
/// и не создаётся), и с окна снимается скриншот. Скриншоты дистрибутивов — настоящие, с Commons.
nonisolated enum DemoUI {
    static var isOn: Bool { CommandLine.arguments.contains("--demo-ui") }

    static var screen: String {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--demo-ui"), i + 1 < args.count else { return "hot" }
        return args[i + 1]
    }

    @MainActor static func prepare(_ model: AppModel) async {
        model.machines = demoMachines()
        model.jobs = demoJobs()
        let s = screen
        switch s {
        case "popular": model.screen = .popular
        case "catalog": model.screen = .catalog
        case "filtered":
            model.screen = .catalog
            model.tags = [.light, .privacy]
        case "machines": model.screen = .machines
        case "downloads": model.screen = .downloads
        case "machine":
            model.screen = .machines
            try? await Task.sleep(for: .seconds(1))
            model.openRequest = model.machines[0].id
        default:
            if s.hasPrefix("family-"), let f = Family(rawValue: String(s.dropFirst(7))) {
                model.screen = .family(f)
            } else if s.hasPrefix("detail-") {
                model.screen = .catalog
                try? await Task.sleep(for: .milliseconds(300))
                model.path = [String(s.dropFirst(7))]
            } else if s.hasPrefix("install-"), let d = Catalog.distro(String(s.dropFirst(8))) {
                model.screen = .catalog
                try? await Task.sleep(for: .milliseconds(300))
                model.path = [d.id]
                try? await Task.sleep(for: .milliseconds(500))
                model.installTarget = d
            } else {
                model.screen = .hot
            }
        }
    }

    static func demoMachines() -> [Machine] {
        func m(_ name: String, _ distro: String, _ edition: String, _ r: Resources, _ phase: Machine.Phase, _ version: String, days: Double) -> Machine {
            var x = Machine(name: name, distroID: distro, editionID: edition, resources: r, isoName: nil, macAddress: "",
                            sharedFolder: "/Users/demo/Vessel/\(name)", rosetta: true, clipboard: true, sound: true,
                            microphone: false, version: version)
            x.phase = phase
            x.lastRun = Date().addingTimeInterval(-days * 86_400)
            return x
        }
        return [
            m("Ubuntu", "ubuntu", "lts", Resources(cpu: 6, memoryGB: 8, diskGB: 64), .installed, "26.04.1", days: 0.1),
            m("Fedora KDE", "fedora", "kde", Resources(cpu: 4, memoryGB: 8, diskGB: 48), .installed, "44", days: 2),
            m("Kali", "kali", "installer", Resources(cpu: 4, memoryGB: 6, diskGB: 48), .installed, "2026.3", days: 6),
            m("Arch Linux ARM", "archarm", "archboot", Resources(cpu: 4, memoryGB: 4, diskGB: 32), .installing, "2026.10.23", days: 0.5),
        ]
    }

    @MainActor static func demoJobs() -> [DownloadJob] {
        guard let nix = Catalog.distro("nixos"), let pop = Catalog.distro("popos") else { return [] }
        let a = DownloadJob(plan: InstallPlan(distro: nix, edition: nix.editions[0], name: "NixOS", resources: Catalog.desktop))
        a.state = .downloading
        a.image = ResolvedImage(url: URL(string: "https://releases.nixos.org/")!, fileName: "nixos-graphical-aarch64-linux.iso",
                                version: "26.05", size: 2_610_000_000, hash: .sha256, digest: "")
        a.total = 2_610_000_000
        a.received = 1_720_000_000
        a.speed = 38_400_000
        let b = DownloadJob(plan: InstallPlan(distro: pop, edition: pop.editions[0], name: "Pop!_OS", resources: Catalog.desktop))
        b.state = .paused
        b.total = 2_888_884_224
        b.received = 640_000_000
        b.image = ResolvedImage(url: URL(string: "https://iso.pop-os.org/")!, fileName: "pop-os_24.04_arm64_generic_3.iso",
                                version: "24.04", size: 2_888_884_224, hash: .sha256, digest: "")
        return [a, b]
    }
}
