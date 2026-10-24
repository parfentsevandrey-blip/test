import AppKit
import SwiftUI
import Virtualization

/// Экран машины: VZVirtualMachineView во всё окно, сверху — стеклянная панель управления.
struct MachineWindow: View {
    @Environment(AppModel.self) private var model
    let id: UUID
    @State private var showTips = true

    var body: some View {
        if let machine = model.machine(id), let session = model.sessions[id] {
            let distro = machine.distro ?? Catalog.all[0]
            ZStack(alignment: .top) {
                if let vm = session.vm {
                    VMDisplay(vm: vm).ignoresSafeArea()
                } else {
                    idle(session, distro: distro)
                }
                VStack(spacing: 10) {
                    if let notice = session.notice { noticeBanner(notice, session: session) }
                    if machine.phase == .installing && session.vm != nil && showTips && !distro.installTips.isEmpty {
                        tipsCard(distro)
                    }
                }
                .padding(.top, 10)
                .padding(.horizontal, 16)
                .frame(maxWidth: 720)
            }
            .navigationTitle(machine.name)
            .navigationSubtitle(session.status.title)
            .toolbar { toolbar(session, machine: machine) }
            .frame(minWidth: 800, minHeight: 520)
        } else if let machine = model.machine(id) {
            // Окно восстановлено системой после перезапуска: сессии ещё нет — создаём вне обновления экрана.
            ProgressView().task { _ = model.session(machine) }
        } else {
            EmptyHint(title: "Машина удалена", symbol: "trash", text: "Это окно можно закрыть.")
        }
    }

    private func idle(_ session: VMSession, distro: Distro) -> some View {
        ZStack {
            ScreenshotImage(title: distro.screenshots.first, distro: distro).blur(radius: 30).opacity(0.6).ignoresSafeArea()
            VStack(spacing: 16) {
                DistroCover(distro: distro).frame(width: 96, height: 96).clipShape(RoundedRectangle(cornerRadius: 24))
                Text(session.machine.name).font(.system(size: 28, weight: .bold, design: .rounded))
                if case .failed(let why) = session.status {
                    Text(why).font(.system(size: 13)).foregroundStyle(.red).multilineTextAlignment(.center).frame(maxWidth: 460)
                } else {
                    Text(session.status.title).foregroundStyle(.secondary)
                }
                if session.status == .starting || session.status == .restoring {
                    ProgressView().controlSize(.large)
                } else {
                    Button {
                        Task { await session.start() }
                    } label: {
                        Label(session.machine.hasSavedState ? "Продолжить с того же места" : "Запустить", systemImage: "play.fill")
                            .font(.system(size: 15, weight: .semibold)).padding(.horizontal, 12).padding(.vertical, 4)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(distro.color)
                    .controlSize(.large)
                    .disabled(!model.virtualizationSupported)
                }
            }
            .padding(40)
            .glassEffect(.regular, in: .rect(cornerRadius: 30))
        }
    }

    private func noticeBanner(_ text: String, session: VMSession) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "info.circle.fill").foregroundStyle(.blue)
            Text(text).font(.system(size: 12))
            Spacer()
            Button { session.notice = nil } label: { Image(systemName: "xmark") }.buttonStyle(.plain)
        }
        .padding(12)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private func tipsCard(_ distro: Distro) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label("Установка \(distro.name)", systemImage: "wand.and.stars").font(.system(size: 12, weight: .semibold))
                Spacer()
                Button { withAnimation { showTips = false } } label: { Image(systemName: "xmark") }.buttonStyle(.plain)
            }
            ForEach(Array(distro.installTips.enumerated()), id: \.offset) { i, tip in
                Text("\(i + 1). \(tip)").font(.system(size: 11)).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(12)
        .glassEffect(.regular.tint(distro.color.opacity(0.1)), in: .rect(cornerRadius: 16))
    }

    @ToolbarContentBuilder
    private func toolbar(_ session: VMSession, machine: Machine) -> some ToolbarContent {
        ToolbarItemGroup(placement: .primaryAction) {
            if session.status == .running || session.status == .paused {
                Button { Task { session.status == .paused ? await session.resume() : await session.pause() } } label: {
                    Label(session.status == .paused ? "Продолжить" : "Пауза", systemImage: session.status == .paused ? "play.fill" : "pause.fill")
                }
                Button { Task { await session.suspend() } } label: { Label("Приостановить и закрыть", systemImage: "moon.zzz.fill") }
                    .help("Сохранить состояние на диск — в следующий раз машина продолжит с того же места")
                Button { session.requestStop() } label: { Label("Выключить", systemImage: "power") }
                    .help("Мягкое выключение: системе приходит нажатие кнопки питания")
                Menu {
                    Button("Выключить сразу (как выдернуть шнур)") { Task { await session.forceStop() } }
                    if machine.phase == .installing {
                        Button("Извлечь установочный диск") { session.ejectInstaller() }
                    }
                    if let folder = machine.sharedFolder {
                        Button("Открыть общую папку на Mac") { NSWorkspace.shared.open(URL(fileURLWithPath: folder)) }
                        Button("Как подключить её в Linux") {
                            session.notice = "В терминале машины: sudo mkdir -p /mnt/vessel && sudo mount -t virtiofs vessel /mnt/vessel"
                        }
                    }
                } label: { Label("Ещё", systemImage: "ellipsis.circle") }
            } else if !session.status.isActive {
                Button { Task { await session.start() } } label: { Label("Запустить", systemImage: "play.fill") }
                    .disabled(!model.virtualizationSupported)
            }
        }
    }
}

/// Экран машины. Сам меняет разрешение системы под размер окна (если гость это умеет).
struct VMDisplay: NSViewRepresentable {
    let vm: VZVirtualMachine

    func makeNSView(context: Context) -> VZVirtualMachineView {
        let view = VZVirtualMachineView()
        view.capturesSystemKeys = true
        view.automaticallyReconfiguresDisplay = true
        view.virtualMachine = vm
        DispatchQueue.main.async { view.window?.makeFirstResponder(view) }
        return view
    }

    func updateNSView(_ view: VZVirtualMachineView, context: Context) {
        if view.virtualMachine !== vm { view.virtualMachine = vm }
    }
}
