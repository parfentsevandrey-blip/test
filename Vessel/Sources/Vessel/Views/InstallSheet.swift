import SwiftUI
import Virtualization

/// Мастер установки: одна страница. По умолчанию всё уже выбрано — достаточно нажать «Скачать и установить».
struct InstallSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let distro: Distro

    @State private var editionID = ""
    @State private var name = ""
    @State private var cpu = 2
    @State private var memory = 4
    @State private var disk = 32
    @State private var share = true
    @State private var clipboard = true
    @State private var sound = true
    @State private var microphone = false
    @State private var rosetta = false
    @State private var tune = false

    private var edition: Edition { distro.editions.first { $0.id == editionID } ?? distro.editions[0] }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if !model.virtualizationSupported { unsupported }
                    editionPicker
                    summary
                    DisclosureGroup(isExpanded: $tune) { tuning.padding(.top, 10) } label: {
                        Label("Настроить машину", systemImage: "slider.horizontal.3").font(.system(size: 13, weight: .semibold))
                    }
                    options
                }
                .padding(24)
            }
            Divider()
            HStack {
                Text("Образ скачивается с сервера проекта и проверяется по \(edition.source.hashTitle).")
                    .font(.system(size: 11)).foregroundStyle(.secondary)
                Spacer()
                Button("Отмена") { dismiss() }.keyboardShortcut(.cancelAction)
                Button {
                    let plan = InstallPlan(distro: distro, edition: edition, name: name.trimmingCharacters(in: .whitespaces).isEmpty ? distro.name : name,
                                           resources: Resources(cpu: cpu, memoryGB: memory, diskGB: disk), shareFolder: share, rosetta: rosetta,
                                           clipboard: clipboard, sound: sound, microphone: microphone)
                    model.install(plan)
                    dismiss()
                } label: {
                    Label("Скачать и установить", systemImage: "arrow.down.circle.fill").padding(.horizontal, 6)
                }
                .buttonStyle(.glassProminent)
                .tint(distro.color)
                .keyboardShortcut(.defaultAction)
            }
            .padding(16)
        }
        .frame(width: 620, height: 640)
        .onAppear(perform: setup)
        .onChange(of: editionID) { apply(edition.resources) }
    }

    private var header: some View {
        ZStack(alignment: .bottomLeading) {
            ScreenshotImage(title: distro.screenshots.first, distro: distro)
                .frame(height: 140)
            LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
            HStack(spacing: 14) {
                DistroCover(distro: distro).frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Установить \(distro.name)").font(.system(size: 22, weight: .bold, design: .rounded)).foregroundStyle(.white)
                    Text(distro.tagline).font(.system(size: 12)).foregroundStyle(.white.opacity(0.85))
                }
            }
            .padding(18)
        }
        .frame(height: 140)
        .clipped()
    }

    private var unsupported: some View {
        Label("Этот Mac не может запускать виртуальные машины (нужен Apple Silicon). Образ скачается, но машина не запустится.",
              systemImage: "exclamationmark.triangle.fill")
            .font(.system(size: 12)).foregroundStyle(.orange)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(.regular.tint(.orange.opacity(0.12)), in: .rect(cornerRadius: 14))
    }

    private var editionPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Вариант").font(.system(size: 13, weight: .semibold))
            ForEach(distro.editions) { e in
                Button { withAnimation(.snappy) { editionID = e.id } } label: {
                    HStack(spacing: 12) {
                        Image(systemName: e.id == edition.id ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: 16)).foregroundStyle(e.id == edition.id ? distro.color : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(e.title).font(.system(size: 13, weight: .semibold))
                            Text([e.desktop, e.live ? "живой образ" : nil, e.note].compactMap { $0 }.joined(separator: " · "))
                                .font(.system(size: 11)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Format.gb(e.sizeGB)).font(.system(size: 12).monospacedDigit()).foregroundStyle(.secondary)
                    }
                    .padding(10)
                    .contentShape(Rectangle())
                    .background(e.id == edition.id ? distro.color.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var summary: some View {
        HStack(spacing: 12) {
            step("arrow.down.circle", "Скачаю", "≈ \(Format.gb(edition.sizeGB))")
            step("checkmark.seal", "Проверю", edition.source.hashTitle)
            step("cpu", "Машина", "\(Format.plural(cpu, "ядро", "ядра", "ядер")), \(memory) ГБ")
            step("internaldrive", "Диск", "до \(disk) ГБ")
        }
    }

    private func step(_ symbol: String, _ title: String, _ value: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 20)).foregroundStyle(distro.color)
            Text(title).font(.system(size: 11)).foregroundStyle(.secondary)
            Text(value).font(.system(size: 12, weight: .semibold)).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .glassEffect(.regular, in: .rect(cornerRadius: 14))
    }

    private var tuning: some View {
        VStack(alignment: .leading, spacing: 14) {
            LabeledContent("Имя") { TextField(distro.name, text: $name).textFieldStyle(.roundedBorder).frame(width: 260) }
            slider("Ядра процессора", value: $cpu, range: 1...MacInfo.maxCPU, unit: "из \(MacInfo.cores) на этом Mac")
            slider("Память", value: $memory, range: 1...MacInfo.maxMemoryGB, unit: "ГБ из \(MacInfo.memoryGB)")
            slider("Диск", value: $disk, range: 8...512, step: 8, unit: "ГБ — занимает место только по мере заполнения")
        }
        .font(.system(size: 12))
    }

    private func slider(_ title: String, value: Binding<Int>, range: ClosedRange<Int>, step: Int = 1, unit: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                Spacer()
                Text("\(value.wrappedValue)").font(.system(size: 12, weight: .semibold).monospacedDigit())
                Text(unit).foregroundStyle(.secondary)
            }
            Slider(value: Binding(get: { Double(value.wrappedValue) }, set: { value.wrappedValue = Int($0) }),
                   in: Double(range.lowerBound)...Double(max(range.upperBound, range.lowerBound + 1)), step: Double(step))
                .tint(distro.color)
        }
    }

    private var options: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Связь с Mac").font(.system(size: 13, weight: .semibold))
            toggle("Общая папка", "~/Vessel/\(name.isEmpty ? distro.name : name) — видна в машине как «vessel» (mount -t virtiofs vessel /mnt)", $share)
            toggle("Общий буфер обмена", "Копируйте текст между Mac и машиной (в Linux нужен spice-vdagent)", $clipboard)
            toggle("Звук", "Звук машины — в динамики Mac", $sound)
            toggle("Микрофон", "Машина слышит микрофон Mac — только если нужно", $microphone)
            toggle("Rosetta для программ Intel", rosettaNote, $rosetta)
                .disabled(VZLinuxRosettaDirectoryShare.availability == .notSupported)
        }
    }

    private var rosettaNote: String {
        switch VZLinuxRosettaDirectoryShare.availability {
        case .installed: "Программы для x86-64 запускаются внутри Linux через Rosetta"
        case .notInstalled: "macOS установит Rosetta при создании машины"
        default: "Недоступна на этом Mac"
        }
    }

    private func toggle(_ title: String, _ note: String, _ value: Binding<Bool>) -> some View {
        Toggle(isOn: value) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 13))
                Text(note).font(.system(size: 11)).foregroundStyle(.secondary)
            }
        }
        .toggleStyle(.switch)
        .tint(distro.color)
    }

    private func setup() {
        editionID = model.installEdition.flatMap { id in distro.editions.first { $0.id == id }?.id } ?? distro.editions[0].id
        name = model.suggestedName(distro)
        apply(edition.resources)
    }

    private func apply(_ wanted: Resources) {
        let fit = MacInfo.fit(wanted)
        cpu = fit.cpu
        memory = fit.memoryGB
        disk = fit.diskGB
    }
}

extension ImageSource {
    var hashTitle: String {
        switch self {
        case .listing(_, _, _, let sums), .direct(_, let sums):
            switch sums {
            case .file(_, let k), .sidecar(_, let k): k.title
            }
        default: "SHA-256"
        }
    }
}
