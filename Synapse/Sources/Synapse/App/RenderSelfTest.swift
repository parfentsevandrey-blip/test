import AppKit
import SwiftUI

/// `--selftest-render <папка>`: визуализации и карточки — в PNG, чтобы посмотреть глазами из CI.
enum RenderSelfTest {
    static func run(_ dir: URL) -> Bool {
        _ = NSApplication.shared
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let model = AppModel()
        var ok = true
        let dark = Color(red: 0.05, green: 0.06, blue: 0.1)

        for (name, energy, progress) in [("orb-idle", 0.2, nil), ("orb-thinking", 1.2, nil), ("orb-progress", 0.8, 0.45)] as [(String, Double, Double?)] {
            let view = NeuralOrb(energy: energy, palette: Section.chat.palette, progress: progress)
                .frame(width: 420, height: 420)
                .background(dark)
                .environment(\.colorScheme, .dark)
            ok = save(view, dir.appendingPathComponent("\(name).png")) && ok
        }

        let recs = model.recommendations
        let cards = HStack(alignment: .top, spacing: 16) {
            ForEach(recs) { RecommendationCard(rec: $0, selected: $0.role == .balanced, highlighted: $0.role == .balanced) {} }
        }
        .padding(30)
        .frame(width: 1240)
        .background(SectionBackground(section: .forYou))
        .environment(model)
        .environment(\.colorScheme, .dark)
        ok = save(cards, dir.appendingPathComponent("recommendations.png")) && ok

        let rows = VStack(spacing: 8) {
            ForEach(Curated.cards.prefix(8)) { CatalogRow(card: $0, fit: model.fit($0)) }
        }
        .padding(24)
        .frame(width: 1100)
        .background(SectionBackground(section: .catalog))
        .environment(model)
        .environment(\.colorScheme, .dark)
        ok = save(rows, dir.appendingPathComponent("catalog-rows.png")) && ok

        let answer = """
        ## Почему локально

        Модель работает **прямо на Mac**, поэтому:

        1. Переписка не покидает компьютер.
        2. Работает без интернета — в самолёте и на даче.
        3. Бесплатно: никаких подписок и лимитов.

        ```swift
        let reply = try await engine.generate(turns)
        ```
        > Скорость зависит от пропускной способности памяти.
        """
        var stats = GenerationStats()
        stats.tokens = 214
        stats.seconds = 4.1
        let messages = VStack(alignment: .leading, spacing: 18) {
            MessageView(message: ChatMessage(role: .user, text: "Чем хороши локальные нейросети?"), streaming: false, isLast: false)
            MessageView(message: ChatMessage(role: .assistant, text: answer, reasoning: "Пользователь спрашивает о плюсах. Перечислю приватность, офлайн и цену.",
                                             stats: stats, model: "Qwen3.5 9B", thinkingSeconds: 2.4), streaming: false, isLast: true)
        }
        .padding(28)
        .frame(width: 860)
        .background(SectionBackground(section: .chat))
        .environment(model)
        .environment(\.colorScheme, .dark)
        ok = save(messages, dir.appendingPathComponent("chat-messages.png")) && ok

        return SelfTest.check(ok, "визуализации отрисованы в \(dir.path)")
    }

    private static func save(_ view: some View, _ url: URL) -> Bool {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        guard let image = renderer.nsImage, let tiff = image.tiffRepresentation,
              let png = NSBitmapImageRep(data: tiff)?.representation(using: .png, properties: [:]) else { return false }
        return (try? png.write(to: url)) != nil
    }
}
