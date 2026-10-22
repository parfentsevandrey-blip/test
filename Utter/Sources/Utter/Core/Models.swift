import Foundation

/// Файл модели на Hugging Face с размером и SHA-256 (для проверки после загрузки).
nonisolated struct ModelFile: Sendable, Hashable {
    var path: String
    var size: Int64
    var sha256: String
}

/// Модель распознавания речи.
///
/// Qwen3-ASR (Alibaba, 2026): 30 языков, включая русский, пунктуация и регистр «из коробки»,
/// подсказки через контекст (имена, термины). Работает в llama.cpp через libmtmd: аудиокодер
/// (mmproj) превращает звук в эмбеддинги, языковая модель пишет текст. Q8_0 — почти без потерь.
nonisolated struct AsrModel: Sendable, Hashable, Identifiable {
    var id: String
    var title: String
    var summary: String
    var repo: String
    var model: ModelFile
    var projector: ModelFile
    /// Ориентировочная нагрузка: сколько памяти нужно для работы.
    var memoryGB: Double
    /// Потоковая модель: слушает звук непрерывно и пишет текст с постоянной задержкой (Voxtral Realtime),
    /// а не перераспознаёт фразу целиком.
    var realtime = false

    var size: Int64 { model.size + projector.size }
    var folder: URL { AppPaths.models.appendingPathComponent(id, isDirectory: true) }
    var modelPath: String { folder.appendingPathComponent(model.path).path }
    var projectorPath: String { folder.appendingPathComponent(projector.path).path }
    var installed: Bool {
        FileManager.default.fileExists(atPath: modelPath) && FileManager.default.fileExists(atPath: projectorPath)
    }

    static func url(repo: String, path: String) -> URL {
        URL(string: "https://huggingface.co/\(repo)/resolve/main/\(path)?download=true")!
    }

    static let fast = AsrModel(
        id: "qwen3-asr-0.6b", title: "Qwen3-ASR 0.6B", summary: "Быстрая: мгновенный отклик даже на MacBook Air.",
        repo: "ggml-org/Qwen3-ASR-0.6B-GGUF",
        model: ModelFile(path: "Qwen3-ASR-0.6B-Q8_0.gguf", size: 804_749_248, sha256: "bca259818b50ca7c4c05e9bdb35a5dc04fa039653a6d6f3f0f331f96f6aa1971"),
        projector: ModelFile(path: "mmproj-Qwen3-ASR-0.6B-Q8_0.gguf", size: 214_392_480, sha256: "41a342b5e4c514e968cb756de6cd1b7be39eff43c44c57a2ef5fc6522e36603d"),
        memoryGB: 1.4)

    static let accurate = AsrModel(
        id: "qwen3-asr-1.7b", title: "Qwen3-ASR 1.7B", summary: "Точнее: лучше с терминами, шумом и быстрой речью.",
        repo: "ggml-org/Qwen3-ASR-1.7B-GGUF",
        model: ModelFile(path: "Qwen3-ASR-1.7B-Q8_0.gguf", size: 2_165_034_944, sha256: "58e22d0532d4eacaf034cfac17a6fed159f37c41390c710186783be439d1fc57"),
        projector: ModelFile(path: "mmproj-Qwen3-ASR-1.7B-Q8_0.gguf", size: 355_709_344, sha256: "46c1d533af3f354ceb37ce855dbceff7da7fa7cf1e6a523df3b13440bd164c0d"),
        memoryGB: 3.2)

    /// Voxtral Mini 4B Realtime (Mistral, 2026): распознаёт поток с задержкой около полсекунды — текст идёт
    /// за голосом, без «хвоста» после фразы. 13 языков; на русском FLEURS у авторов 6,0% ошибок при 480 мс.
    /// В llama.cpp — через потоковый ввод звука libmtmd (mtmd_audio_stream).
    static let instant = AsrModel(
        id: "voxtral-rt-4b", title: "Voxtral Realtime 4B",
        summary: "Мгновенная: печатает прямо во время речи, с задержкой около полсекунды, без ожидания конца фразы.",
        repo: "ggml-org/Voxtral-Mini-4B-Realtime-2602-GGUF",
        model: ModelFile(path: "Voxtral-Mini-4B-Realtime-2602-Q4_K_M.gguf", size: 2_551_244_000, sha256: "aa7a91fd78a896562642c61950887ec18c999425ab064bfc5ca9e64f8f5b29be"),
        projector: ModelFile(path: "mmproj-Voxtral-Mini-4B-Realtime-2602-Q8_0.gguf", size: 1_063_756_032, sha256: "f28ae3e40f2b610c1c39b5c936ae854b73d0fc487518c6f69b76c22dbcea9ee3"),
        memoryGB: 4.6, realtime: true)

    static let all = [fast, accurate, instant]

    /// Для Mac с 16 ГБ и больше — точная, иначе быстрая (распознавание идёт всё время, пока вы говорите).
    static var recommended: AsrModel { Hardware.memoryGB >= 15 ? accurate : fast }

    static func named(_ id: String?) -> AsrModel? { all.first { $0.id == id } }
}
