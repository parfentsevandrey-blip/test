import SwiftUI
import UniformTypeIdentifiers

struct ROMDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var project: ROMProject
    var specs: DeviceSpecs

    @State private var selectedBuildID: String?
    @State private var localPackage: URL?
    @State private var localRecovery: URL?
    @State private var importTarget: ImportTarget = .package
    @State private var showImporter = false

    enum ImportTarget {
        case package, recovery
    }

    private var availability: ROMAvailability { model.availability(for: project) }
    private var builds: [ROMBuild] { availability.builds }
    private var selectedBuild: ROMBuild? {
        builds.first { $0.id == selectedBuildID } ?? builds.first
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    Text(project.summary)
                        .font(.body)
                        .fixedSize(horizontal: false, vertical: true)
                    features
                    if specs.vendorFamily == .samsung {
                        warning("Samsung устанавливается через Download Mode (Odin/Heimdall), а не fastboot. Автоматическая установка недоступна — воспользуйтесь инструкцией на сайте проекта.")
                    } else if specs.vendorFamily == .huawei {
                        warning("Huawei и Honor не позволяют разблокировать загрузчик — установка альтернативных прошивок невозможна.")
                    }
                    content
                }
                .padding(28)
            }
            Divider()
            footer
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
        }
        .frame(width: 640, height: 680)
        .fileImporter(isPresented: $showImporter,
                      allowedContentTypes: importTarget == .recovery ? [.data] : [.zip]) { result in
            guard case .success(let url) = result else { return }
            switch importTarget {
            case .package: localPackage = url
            case .recovery: localRecovery = url
            }
        }
    }

    private var header: some View {
        HStack(spacing: 18) {
            IconTile(symbol: project.symbol, colors: project.colors, size: 72)
            VStack(alignment: .leading, spacing: 6) {
                Text(project.name).font(.system(size: 28, weight: .bold, design: .rounded))
                Text(project.tagline).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    Badge(text: "На базе \(project.basedOn)", symbol: "square.stack.3d.down.right")
                    availability.badge
                }
            }
            Spacer()
            Button { openURL(project.website) } label: {
                Image(systemName: "safari")
            }
            .glassButton()
            .help("Сайт проекта")
        }
    }

    private var features: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading, spacing: 10) {
            ForEach(project.features, id: \.self) { feature in
                Label(feature, systemImage: "checkmark.circle.fill")
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(project.colors.last ?? .accentColor)
                    .font(.callout)
            }
        }
        .padding(16)
        .glassCard(cornerRadius: 16)
    }

    @ViewBuilder
    private var content: some View {
        switch availability {
        case .checking:
            HStack { ProgressView(); Text("Ищем сборки для \(specs.codename)…") }
        case .available:
            buildsList
        case .notFound:
            infoCard(symbol: "magnifyingglass",
                     title: "Официальной сборки для \(specs.codename) нет",
                     text: "Проект не поддерживает это устройство официально. Неофициальные сборки можно поискать на XDA и установить из файла.")
            localInstall
        case .manual(let url):
            infoCard(symbol: "safari",
                     title: "Сборки публикуются на сайте проекта",
                     text: "Скачайте ZIP для «\(specs.codename)» по ссылке, затем выберите его здесь — дальше ROM Forge всё сделает сам.",
                     link: url)
            localInstall
        case .failed(let message):
            infoCard(symbol: "wifi.exclamationmark", title: "Не удалось проверить сборки", text: message)
            localInstall
        }
    }

    private var buildsList: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "Доступные сборки", symbol: "shippingbox")
            VStack(spacing: 0) {
                ForEach(builds.prefix(6)) { build in
                    Button {
                        selectedBuildID = build.id
                    } label: {
                        BuildRow(build: build, isSelected: build.id == selectedBuild?.id)
                    }
                    .buttonStyle(.plain)
                    if build.id != builds.prefix(6).last?.id { Divider().padding(.leading, 44) }
                }
            }
            .padding(6)
            .glassCard(cornerRadius: 16)
            if let build = selectedBuild {
                Label(installSummary(for: build), systemImage: "info.circle")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var localInstall: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "Установка из файла", symbol: "doc.zipper")
            VStack(spacing: 10) {
                fileRow(title: "Прошивка (.zip)", url: localPackage, target: .package)
                fileRow(title: "Образ recovery (.img, необязательно)", url: localRecovery, target: .recovery)
            }
            .padding(14)
            .glassCard(cornerRadius: 16)
        }
    }

    private func fileRow(title: String, url: URL?, target: ImportTarget) -> some View {
        HStack {
            Image(systemName: url == nil ? "doc" : "doc.fill").foregroundStyle(.tint)
            VStack(alignment: .leading) {
                Text(title)
                Text(url?.lastPathComponent ?? "Не выбран").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Выбрать…") {
                importTarget = target
                showImporter = true
            }
        }
    }

    private var footer: some View {
        HStack {
            Button("Закрыть") { dismiss() }
                .keyboardShortcut(.cancelAction)
            Spacer()
            if let notes = selectedBuild?.notesURL {
                Button("Инструкция проекта") { openURL(notes) }
            }
            Button {
                model.beginInstall(project: project, build: localPackage == nil ? selectedBuild : nil,
                                   localPackage: localPackage, localRecovery: localRecovery)
                dismiss()
            } label: {
                Label(localPackage == nil ? "Установить" : "Установить из файла",
                      systemImage: "arrow.down.circle.fill")
                    .padding(.horizontal, 8)
            }
            .prominentButton()
            .controlSize(.large)
            .keyboardShortcut(.defaultAction)
            .disabled(!canInstall)
        }
    }

    private var canInstall: Bool {
        guard specs.vendorFamily != .samsung, specs.vendorFamily != .huawei else { return false }
        guard model.installSession?.isActive != true else { return false }
        return localPackage != nil || selectedBuild != nil
    }

    private func installSummary(for build: ROMBuild) -> String {
        switch build.kind {
        case .factoryImage:
            return "Будут скачаны заводские образы и прошиты через fastboot. Recovery не требуется."
        case .sideloadZip:
            if let source = build.recoveryImagesSource {
                return "Будет установлен \(source), затем система через ADB sideload."
            }
            return "Для установки нужен custom recovery (TWRP, OrangeFox и т. п.)."
        }
    }

    private func warning(_ text: String) -> some View {
        Label(text, systemImage: "exclamationmark.triangle.fill")
            .foregroundStyle(.orange)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassCard(cornerRadius: 14, tint: .orange)
    }

    private func infoCard(symbol: String, title: String, text: String, link: URL? = nil) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol).font(.title2).foregroundStyle(.tint)
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.headline)
                Text(text).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if let link {
                    Link(destination: link) {
                        Label("Открыть страницу загрузки", systemImage: "arrow.up.right.square")
                    }
                    .padding(.top, 2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .glassCard(cornerRadius: 16)
    }
}

private struct BuildRow: View {
    var build: ROMBuild
    var isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .foregroundStyle(isSelected ? Color.accentColor : .secondary)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(build.version).font(.body.weight(.medium))
                    if let channel = build.channel { Badge(text: channel) }
                }
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if let size = build.package.size {
                Text(size.formattedBytes).font(.callout.monospacedDigit()).foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        var parts: [String] = []
        if let android = build.androidVersion { parts.append("Android \(android)") }
        if let date = build.date { parts.append(date.formatted(date: .abbreviated, time: .omitted)) }
        if let maintainer = build.maintainer { parts.append("сборщик \(maintainer)") }
        return parts.joined(separator: " · ")
    }
}
