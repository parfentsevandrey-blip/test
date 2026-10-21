import AppKit
import SwiftUI

/// Ответ модели в Markdown: заголовки, списки, цитаты, таблицы-как-текст и блоки кода с кнопкой «Скопировать».
struct MarkdownText: View {
    let text: String
    var size: CGFloat = 14

    enum Block: Hashable {
        case paragraph(String)
        case heading(String, Int)
        case bullet(String, Int, String)
        case quote(String)
        case code(String, String)
        case rule
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(Self.blocks(text).enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        .textSelection(.enabled)
    }

    @ViewBuilder
    private func view(for block: Block) -> some View {
        switch block {
        case .paragraph(let s):
            Text(Self.inline(s)).font(.system(size: size)).lineSpacing(3).frame(maxWidth: .infinity, alignment: .leading)
        case .heading(let s, let level):
            Text(Self.inline(s)).font(.system(size: size + CGFloat(max(1, 5 - level) * 2), weight: .bold)).padding(.top, 4)
        case .bullet(let s, let indent, let marker):
            HStack(alignment: .firstTextBaseline, spacing: 7) {
                Text(marker).font(.system(size: size, weight: .semibold)).foregroundStyle(.secondary)
                Text(Self.inline(s)).font(.system(size: size)).lineSpacing(3).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.leading, CGFloat(indent) * 16)
        case .quote(let s):
            HStack(spacing: 10) {
                Capsule().fill(.secondary.opacity(0.5)).frame(width: 3)
                Text(Self.inline(s)).font(.system(size: size)).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
            }
        case .code(let code, let lang):
            CodeBlock(code: code, language: lang)
        case .rule:
            Divider().padding(.vertical, 4)
        }
    }

    static func inline(_ s: String) -> AttributedString {
        (try? AttributedString(markdown: s, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(s)
    }

    /// Простой разбор Markdown по строкам: этого хватает для ответов моделей и не ломается на недописанном потоке.
    static func blocks(_ text: String) -> [Block] {
        var blocks: [Block] = []
        var paragraph: [String] = []
        var code: [String]? = nil
        var lang = ""
        func flush() {
            if !paragraph.isEmpty { blocks.append(.paragraph(paragraph.joined(separator: "\n"))); paragraph = [] }
        }
        for raw in text.components(separatedBy: "\n") {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("```") {
                if let c = code {
                    blocks.append(.code(c.joined(separator: "\n"), lang))
                    code = nil
                } else {
                    flush()
                    code = []
                    lang = String(line.dropFirst(3)).trimmingCharacters(in: .whitespaces)
                }
                continue
            }
            if code != nil { code!.append(raw); continue }
            if line.isEmpty { flush(); continue }
            if line.hasPrefix("#") {
                let level = line.prefix(while: { $0 == "#" }).count
                if level <= 6, line.dropFirst(level).first == " " {
                    flush()
                    blocks.append(.heading(String(line.dropFirst(level + 1)), level))
                    continue
                }
            }
            if line == "---" || line == "***" || line == "___" { flush(); blocks.append(.rule); continue }
            if line.hasPrefix("> ") { flush(); blocks.append(.quote(String(line.dropFirst(2)))); continue }
            let indent = (raw.prefix(while: { $0 == " " }).count) / 2
            if line.hasPrefix("- ") || line.hasPrefix("* ") || line.hasPrefix("• ") {
                flush()
                blocks.append(.bullet(String(line.dropFirst(2)), indent, "•"))
                continue
            }
            if let dot = line.firstIndex(of: "."), line[..<dot].allSatisfy(\.isNumber), !line[..<dot].isEmpty,
               line[line.index(after: dot)...].hasPrefix(" ") {
                flush()
                blocks.append(.bullet(String(line[line.index(dot, offsetBy: 2)...]), indent, String(line[...dot])))
                continue
            }
            paragraph.append(raw)
        }
        // Поток мог оборваться внутри блока кода — показываем то, что есть.
        if let c = code { blocks.append(.code(c.joined(separator: "\n"), lang)) }
        flush()
        return blocks
    }
}

struct CodeBlock: View {
    let code: String
    let language: String
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(language.isEmpty ? "код" : language).font(.system(size: 11, weight: .medium)).foregroundStyle(.secondary)
                Spacer()
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(code, forType: .string)
                    copied = true
                    Task { try? await Task.sleep(for: .seconds(1.5)); copied = false }
                } label: {
                    Label(copied ? "Скопировано" : "Скопировать", systemImage: copied ? "checkmark" : "doc.on.doc").font(.system(size: 11))
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(.primary.opacity(0.05))
            ScrollView(.horizontal) {
                Text(code)
                    .font(.system(size: 12.5, design: .monospaced))
                    .padding(12)
                    .fixedSize(horizontal: true, vertical: true)
            }
            .scrollIndicators(.automatic)
        }
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(nsColor: .textBackgroundColor).opacity(0.7)))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(.primary.opacity(0.08)))
    }
}
