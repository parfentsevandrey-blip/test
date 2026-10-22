import Foundation
import Network

/// Локальный HTTP-сервер, совместимый с Ollama и OpenAI: другие программы (редакторы кода,
/// плагины, скрипты) могут пользоваться моделями Synapse. Слушает только этот Mac (loopback).
///
/// Ollama: GET /api/tags, /api/ps, /api/version, POST /api/chat, /api/generate, /api/show
/// OpenAI: GET /v1/models, POST /v1/chat/completions (обычный ответ и SSE-поток)
nonisolated final class APIServer: @unchecked Sendable {
    struct Backend: Sendable {
        /// Установленные модели.
        var models: @Sendable () async -> [InstalledModel]
        /// Загрузить модель по имени (или текущую) и вернуть её.
        var prepare: @Sendable (String?) async throws -> InstalledModel
        /// Запрос пришёл — для счётчика в интерфейсе.
        var activity: @Sendable (String) -> Void
    }

    enum State: Equatable, Sendable { case stopped, running(UInt16), failed(String) }

    private let queue = DispatchQueue(label: "app.synapse.api")
    private var listener: NWListener?
    private let backend: Backend
    private let onState: @Sendable (State) -> Void

    init(backend: Backend, onState: @escaping @Sendable (State) -> Void) {
        self.backend = backend
        self.onState = onState
    }

    func start(port: UInt16) {
        stop()
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            params.requiredInterfaceType = .loopback
            guard let p = NWEndpoint.Port(rawValue: port) else { onState(.failed("Неверный порт")); return }
            let listener = try NWListener(using: params, on: p)
            listener.newConnectionHandler = { [weak self] c in self?.accept(c) }
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready: self?.onState(.running(port))
                case .failed(let e):
                    let busy = "\(e)".contains("48") || "\(e)".localizedCaseInsensitiveContains("in use")
                    self?.onState(.failed(busy ? "Порт \(port) занят — возможно, запущена Ollama. Выберите другой." : e.localizedDescription))
                default: break
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            onState(.failed(error.localizedDescription))
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
        onState(.stopped)
    }

    // MARK: - Соединения

    private func accept(_ c: NWConnection) {
        c.start(queue: queue)
        receive(c, buffer: Data())
    }

    private func receive(_ c: NWConnection, buffer: Data) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 1 << 20) { [weak self] data, _, done, error in
            guard let self else { return }
            var buf = buffer
            if let data { buf.append(data) }
            if let request = HTTPRequest.parse(buf) {
                let connection = Connection(c)
                Task { await self.handle(request, connection) }
                return
            }
            if done || error != nil || buf.count > 64 << 20 { c.cancel(); return }
            self.receive(c, buffer: buf)
        }
    }

    // MARK: - Маршруты

    private func handle(_ r: HTTPRequest, _ c: Connection) async {
        backend.activity("\(r.method) \(r.path)")
        if r.method == "OPTIONS" { c.respond(204, body: Data()); return }
        do {
            switch (r.method, r.path) {
            case ("GET", "/"), ("HEAD", "/"):
                c.respond(200, type: "text/plain", body: Data("Synapse is running".utf8))
            case ("GET", "/api/version"):
                c.json(["version": "0.12.0"])
            case ("GET", "/api/tags"):
                let models = await backend.models()
                c.json(["models": models.map(ollamaModel)])
            case ("GET", "/api/ps"):
                let loaded = LlamaEngine.shared.loaded
                let models = await backend.models().filter { $0.primaryPath == loaded?.path }
                c.json(["models": models.map(ollamaModel)])
            case ("POST", "/api/show"):
                let model = try await backend.prepare(r.json["model"] as? String ?? r.json["name"] as? String)
                c.json(["details": details(model), "modelfile": "", "parameters": "", "template": "",
                        "capabilities": model.vision ? ["completion", "vision"] : ["completion"]])
            case ("POST", "/api/chat"):
                try await ollamaChat(r.json, c, generate: false)
            case ("POST", "/api/generate"):
                try await ollamaChat(r.json, c, generate: true)
            case ("GET", "/v1/models"):
                let models = await backend.models()
                c.json(["object": "list", "data": models.map { ["id": $0.apiName, "object": "model", "created": Int($0.installedAt.timeIntervalSince1970), "owned_by": $0.lab] as [String: Any] }])
            case ("POST", "/v1/chat/completions"):
                try await openAIChat(r.json, c)
            default:
                c.json(["error": "Нет такого метода: \(r.method) \(r.path)"], status: 404)
            }
        } catch {
            c.json(["error": error.localizedDescription], status: 500)
        }
    }

    private func ollamaModel(_ m: InstalledModel) -> [String: Any] {
        ["name": m.apiName, "model": m.apiName, "size": m.size, "digest": m.id,
         "modified_at": ISO8601DateFormatter().string(from: m.installedAt), "details": details(m)]
    }

    private func details(_ m: InstalledModel) -> [String: Any] {
        ["format": "gguf", "family": m.architecture ?? "", "parameter_size": String(format: "%.1fB", m.params), "quantization_level": m.quant]
    }

    /// Реплики из запроса. Изображения: Ollama — `images: [base64]`, OpenAI — части `image_url` с data-URL.
    static func turns(from messages: Any?) -> [ChatTurn] {
        (messages as? [[String: Any]] ?? []).compactMap { m in
            let role = ChatTurn.Role(rawValue: m["role"] as? String ?? "user") ?? (m["role"] as? String == "developer" ? .system : .user)
            var text = m["content"] as? String ?? ""
            var images: [String] = (m["images"] as? [String] ?? []).compactMap(saveImage)
            if let parts = m["content"] as? [[String: Any]] {
                text = parts.compactMap { $0["text"] as? String }.joined(separator: "\n")
                for part in parts where part["type"] as? String == "image_url" || part["type"] as? String == "input_image" {
                    let url = (part["image_url"] as? [String: Any])?["url"] as? String ?? part["image_url"] as? String ?? ""
                    if let path = saveImage(url) { images.append(path) }
                }
            }
            return ChatTurn(role: role, content: text, images: images)
        }
    }

    /// base64 или data:image/…;base64,… → временный файл.
    private static func saveImage(_ encoded: String) -> String? {
        let payload = encoded.hasPrefix("data:") ? String(encoded.split(separator: ",", maxSplits: 1).last ?? "") : encoded
        guard let data = Data(base64Encoded: payload, options: .ignoreUnknownCharacters), !data.isEmpty else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("synapse-api-\(UUID().uuidString).img")
        return (try? data.write(to: url)) != nil ? url.path : nil
    }

    private static func settings(_ body: [String: Any], openAI: Bool) -> GenerationSettings {
        var s = GenerationSettings()
        let options = openAI ? body : (body["options"] as? [String: Any] ?? [:])
        if let t = options["temperature"] as? Double { s.temperature = t }
        if let p = options["top_p"] as? Double { s.topP = p }
        if let k = options["top_k"] as? Int { s.topK = k }
        if let seed = options["seed"] as? Int { s.seed = UInt32(truncatingIfNeeded: seed) }
        if let n = (options["num_predict"] ?? options["max_tokens"] ?? options["max_completion_tokens"]) as? Int, n > 0 { s.maxTokens = n }
        if let think = body["think"] as? Bool { s.thinking = think }
        if let effort = body["reasoning_effort"] as? String { s.thinking = effort != "none" && effort != "minimal" }
        return s
    }

    /// Ollama: поток NDJSON (по умолчанию) или один JSON.
    private func ollamaChat(_ body: [String: Any], _ c: Connection, generate: Bool) async throws {
        let model = try await backend.prepare(body["model"] as? String)
        var turns: [ChatTurn]
        if generate {
            turns = []
            if let system = body["system"] as? String { turns.append(ChatTurn(role: .system, content: system)) }
            turns.append(ChatTurn(role: .user, content: body["prompt"] as? String ?? ""))
        } else {
            turns = Self.turns(from: body["messages"])
        }
        let stream = body["stream"] as? Bool ?? true
        let settings = Self.settings(body, openAI: false)
        let started = Date()
        var delta = DeltaTracker()
        if stream { c.begin(type: "application/x-ndjson") }

        var final = GenerationStats()
        for try await event in LlamaEngine.shared.generate(turns, settings: settings) {
            switch event {
            case .text(let t):
                let d = delta.push(t)
                guard stream, let d else { continue }
                var chunk: [String: Any] = ["model": model.apiName, "created_at": ISO8601DateFormatter().string(from: Date()), "done": false]
                if generate {
                    chunk["response"] = d.answer
                    if !d.reasoning.isEmpty { chunk["thinking"] = d.reasoning }
                } else {
                    var message: [String: Any] = ["role": "assistant", "content": d.answer]
                    if !d.reasoning.isEmpty { message["thinking"] = d.reasoning }
                    chunk["message"] = message
                }
                c.line(chunk)
            case .started(let startsInReasoning):
                delta.startsInReasoning = startsInReasoning
            case .finished(let stats):
                final = stats
            case .prompt:
                break
            }
        }
        let parts = delta.parts
        var done: [String: Any] = [
            "model": model.apiName, "created_at": ISO8601DateFormatter().string(from: Date()), "done": true,
            "done_reason": final.stoppedByLimit ? "length" : "stop",
            "total_duration": Int(Date().timeIntervalSince(started) * 1e9), "eval_count": final.tokens,
            "eval_duration": Int(final.seconds * 1e9), "prompt_eval_count": final.promptTokens,
            "prompt_eval_duration": Int(final.promptSeconds * 1e9),
        ]
        if stream {
            if generate { done["response"] = "" } else { done["message"] = ["role": "assistant", "content": ""] }
            c.line(done)
            c.end()
        } else {
            if generate {
                done["response"] = parts.answer
                if !parts.reasoning.isEmpty { done["thinking"] = parts.reasoning }
            } else {
                var message: [String: Any] = ["role": "assistant", "content": parts.answer]
                if !parts.reasoning.isEmpty { message["thinking"] = parts.reasoning }
                done["message"] = message
            }
            c.json(done)
        }
    }

    /// OpenAI Chat Completions: обычный ответ или Server-Sent Events.
    private func openAIChat(_ body: [String: Any], _ c: Connection) async throws {
        let model = try await backend.prepare(body["model"] as? String)
        let turns = Self.turns(from: body["messages"])
        let stream = body["stream"] as? Bool ?? false
        let settings = Self.settings(body, openAI: true)
        let id = "chatcmpl-" + UUID().uuidString.prefix(12).lowercased()
        let created = Int(Date().timeIntervalSince1970)
        var delta = DeltaTracker()
        if stream { c.begin(type: "text/event-stream") }

        var final = GenerationStats()
        var first = true
        for try await event in LlamaEngine.shared.generate(turns, settings: settings) {
            switch event {
            case .text(let t):
                let d = delta.push(t)
                guard stream, let d else { continue }
                var deltaBody: [String: Any] = [:]
                if first { deltaBody["role"] = "assistant"; first = false }
                if !d.answer.isEmpty { deltaBody["content"] = d.answer }
                if !d.reasoning.isEmpty { deltaBody["reasoning_content"] = d.reasoning }
                c.event(["id": id, "object": "chat.completion.chunk", "created": created, "model": model.apiName,
                         "choices": [["index": 0, "delta": deltaBody, "finish_reason": NSNull()] as [String: Any]]])
            case .started(let startsInReasoning):
                delta.startsInReasoning = startsInReasoning
            case .finished(let stats):
                final = stats
            case .prompt:
                break
            }
        }
        let reason = final.stoppedByLimit ? "length" : "stop"
        let usage = ["prompt_tokens": final.promptTokens, "completion_tokens": final.tokens, "total_tokens": final.promptTokens + final.tokens]
        if stream {
            c.event(["id": id, "object": "chat.completion.chunk", "created": created, "model": model.apiName,
                     "choices": [["index": 0, "delta": [String: Any](), "finish_reason": reason] as [String: Any]], "usage": usage])
            c.raw("data: [DONE]\n\n")
            c.end()
        } else {
            let parts = delta.parts
            var message: [String: Any] = ["role": "assistant", "content": parts.answer]
            if !parts.reasoning.isEmpty { message["reasoning_content"] = parts.reasoning }
            c.json(["id": id, "object": "chat.completion", "created": created, "model": model.apiName,
                    "choices": [["index": 0, "message": message, "finish_reason": reason] as [String: Any]], "usage": usage])
        }
    }
}

/// Превращает поток сырого текста в приращения ответа и размышлений.
nonisolated struct DeltaTracker {
    var raw = ""
    var startsInReasoning = false
    private var sentAnswer = ""
    private var sentReasoning = ""

    var parts: ReplyParts { ReplyParser.parse(raw, startsInReasoning: startsInReasoning) }

    mutating func push(_ text: String) -> (answer: String, reasoning: String)? {
        raw += text
        let p = parts
        let a = p.answer.hasPrefix(sentAnswer) ? String(p.answer.dropFirst(sentAnswer.count)) : ""
        let r = p.reasoning.hasPrefix(sentReasoning) ? String(p.reasoning.dropFirst(sentReasoning.count)) : ""
        if a.isEmpty && r.isEmpty { return nil }
        sentAnswer = p.answer
        sentReasoning = p.reasoning
        return (a, r)
    }
}

nonisolated struct HTTPRequest: Sendable {
    var method: String
    var path: String
    var headers: [String: String]
    var body: Data

    var json: [String: Any] { (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] ?? [:] }

    static func parse(_ data: Data) -> HTTPRequest? {
        guard let end = data.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        guard let head = String(data: data[..<end.lowerBound], encoding: .utf8) else { return nil }
        var lines = head.components(separatedBy: "\r\n")
        let first = lines.removeFirst().split(separator: " ")
        guard first.count >= 2 else { return nil }
        var headers: [String: String] = [:]
        for l in lines {
            guard let colon = l.firstIndex(of: ":") else { continue }
            headers[l[..<colon].lowercased()] = l[l.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        let length = Int(headers["content-length"] ?? "0") ?? 0
        let body = data[end.upperBound...]
        guard body.count >= length else { return nil }
        let path = String(first[1].split(separator: "?").first ?? "/")
        return HTTPRequest(method: String(first[0]).uppercased(), path: path, headers: headers, body: Data(body.prefix(length)))
    }
}

/// Ответ клиенту. Все отправки идут по порядку на очереди соединения.
nonisolated struct Connection: @unchecked Sendable {
    let c: NWConnection
    init(_ c: NWConnection) { self.c = c }

    private static let cors = "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n"

    private static func reason(_ status: Int) -> String {
        [200: "OK", 204: "No Content", 400: "Bad Request", 404: "Not Found", 500: "Internal Server Error"][status] ?? "OK"
    }

    func respond(_ status: Int, type: String = "application/json", body: Data) {
        let head = "HTTP/1.1 \(status) \(Self.reason(status))\r\nContent-Type: \(type); charset=utf-8\r\nContent-Length: \(body.count)\r\n\(Self.cors)Connection: close\r\n\r\n"
        c.send(content: Data(head.utf8) + body, isComplete: true, completion: .contentProcessed { _ in self.c.cancel() })
    }

    func json(_ object: [String: Any], status: Int = 200) {
        respond(status, body: (try? JSONSerialization.data(withJSONObject: object, options: [.withoutEscapingSlashes])) ?? Data("{}".utf8))
    }

    func begin(type: String) {
        let head = "HTTP/1.1 200 OK\r\nContent-Type: \(type); charset=utf-8\r\nCache-Control: no-cache\r\n\(Self.cors)Connection: close\r\n\r\n"
        raw(head)
    }

    func raw(_ text: String) {
        c.send(content: Data(text.utf8), completion: .contentProcessed { _ in })
    }

    func line(_ object: [String: Any]) {
        if let data = try? JSONSerialization.data(withJSONObject: object, options: [.withoutEscapingSlashes]) {
            c.send(content: data + Data("\n".utf8), completion: .contentProcessed { _ in })
        }
    }

    func event(_ object: [String: Any]) {
        if let data = try? JSONSerialization.data(withJSONObject: object, options: [.withoutEscapingSlashes]) {
            c.send(content: Data("data: ".utf8) + data + Data("\n\n".utf8), completion: .contentProcessed { _ in })
        }
    }

    func end() {
        c.send(content: nil, isComplete: true, completion: .contentProcessed { _ in self.c.cancel() })
    }
}
