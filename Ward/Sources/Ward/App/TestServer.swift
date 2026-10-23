import Foundation
import Network

/// Крошечный HTTP-сервер для самопроверок и демо: страницы с «рекламой» и «трекерами» на этом Mac.
/// Один и тот же сервер доступен как localhost и как 127.0.0.1 — для WebKit это разные сайты,
/// так проверяются сторонние запросы.
nonisolated final class MiniHTTP: @unchecked Sendable {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "app.ward.minihttp")
    private(set) var port = 0
    private let lock = NSLock()
    private var routes: [String: (type: String, headers: [String: String], body: Data)] = [:]
    private var log: [String] = []
    private var heads: [String: String] = [:]

    func set(_ path: String, type: String = "text/html; charset=utf-8", headers: [String: String] = [:], body: Data) {
        lock.withLock { routes[path] = (type, headers, body) }
    }

    /// Заголовки последнего запроса к пути (для проверки Accept-Language и т. п.).
    func requestHead(_ path: String) -> String? { lock.withLock { heads[path] } }

    func set(_ path: String, html: String) { set(path, body: Data(html.utf8)) }

    /// Пути, которые запрашивал браузер (для проверки «до сервера не дошло»).
    var requested: [String] { lock.withLock { log } }

    func start() throws {
        // Только loopback: стенд не виден в локальной сети, и macOS не спрашивает разрешения на неё.
        let parameters = NWParameters.tcp
        parameters.requiredInterfaceType = .loopback
        let l = try NWListener(using: parameters, on: .any)
        let ready = DispatchSemaphore(value: 0)
        l.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        l.newConnectionHandler = { [weak self] c in self?.serve(c) }
        l.start(queue: queue)
        _ = ready.wait(timeout: .now() + 5)
        port = Int(l.port?.rawValue ?? 0)
        listener = l
    }

    func stop() { listener?.cancel() }

    private func serve(_ c: NWConnection) {
        c.start(queue: queue)
        c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            guard let self, let data else { c.cancel(); return }
            let request = String(decoding: data, as: UTF8.self)
            let target = request.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
            let path = target.split(separator: "?").first.map(String.init) ?? target
            let route = self.lock.withLock { () -> (type: String, headers: [String: String], body: Data)? in
                self.log.append(target)
                self.heads[path] = request.components(separatedBy: "\r\n\r\n").first
                return self.routes[path]
            }
            var head: String
            var body = Data()
            if let route {
                head = "HTTP/1.1 200 OK\r\nContent-Type: \(route.type)\r\nContent-Length: \(route.body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n"
                for (k, v) in route.headers { head += "\(k): \(v)\r\n" }
                body = request.hasPrefix("HEAD") ? Data() : route.body
            } else {
                head = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n"
            }
            c.send(content: Data((head + "\r\n").utf8) + body, completion: .contentProcessed { _ in c.cancel() })
        }
    }

    static let downloadBody = Data((0..<4096).map { UInt8($0 % 251) })

    /// PNG 1×1.
    static let pixel = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")!

    /// Стенд: страница с рекламой, трекерами и холстом для проверки отпечатка.
    func installTestSite() {
        let p = port
        set("/adbanners/a.png", type: "image/png", body: Self.pixel)
        set("/ads/banners/b.png", type: "image/png", body: Self.pixel)
        set("/images/photo.png", type: "image/png", body: Self.pixel)
        set("/ads/cbr.js", type: "application/javascript", body: Data("window.cbr = 1;".utf8))
        set("/static/app.js", type: "application/javascript", body: Data("window.app = 1;".utf8))
        set("/page.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Стенд Ward</title>
        <script>window.results = {}; function mark(id, v) { window.results[id] = v; }</script>
        </head><body>
        <h1>Проверка защиты</h1>
        <div id="ad_banner" style="height:60px;background:#f80">РЕКЛАМА</div>
        <div class="content" id="content">Нужный текст</div>
        <img id="adimg" src="http://127.0.0.1:\(p)/adbanners/a.png" onload="mark('adimg','load')" onerror="mark('adimg','error')">
        <img id="adimg2" src="http://127.0.0.1:\(p)/ads/banners/b.png" onload="mark('adimg2','load')" onerror="mark('adimg2','error')">
        <img id="okimg" src="http://127.0.0.1:\(p)/images/photo.png" onload="mark('okimg','load')" onerror="mark('okimg','error')">
        <script src="http://127.0.0.1:\(p)/static/app.js" onload="mark('okjs','load')" onerror="mark('okjs','error')"></script>
        <script async src="https://www.googletagmanager.com/gtag/js?id=G-WARDTEST" onload="mark('gtag','load')" onerror="mark('gtag','error')"></script>
        <script async src="https://mc.yandex.ru/metrika/tag.js" onload="mark('metrika','load')" onerror="mark('metrika','error')"></script>
        <script async src="https://connect.facebook.net/en_US/fbevents.js" onload="mark('fb','load')" onerror="mark('fb','error')"></script>
        <script async src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js" onload="mark('cdn','load')" onerror="mark('cdn','error')"></script>
        </body></html>
        """)
        set("/fp.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Отпечаток</title></head><body><canvas id="c" width="240" height="60"></canvas></body></html>
        """)
        set("/clean.html", html: "<!doctype html><title>Чистая ссылка</title><p>ok</p>")
        // Скриптлеты и процедурная косметика (правила для localhost подставляет самопроверка).
        set("/scriptlets.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Скриптлеты</title>
        <script>window.results = {}; window.adsOn = true; window.results.adsOn = String(window.adsOn);
        try { window.results.pruned = String(JSON.parse('{"ads":[1],"ok":1}').ads === undefined); } catch (e) { window.results.pruned = 'error'; }
        try { window.detectAdBlock; window.results.aopr = 'read'; } catch (e) { window.results.aopr = 'aborted'; }</script>
        </head><body>
        <div class="promo" id="promo1">Реклама: купите слона</div>
        <div class="promo" id="promo2">Обычный текст</div>
        <div class="wrap"><div class="inner"><span class="badge">sponsored</span></div></div>
        </body></html>
        """)
        set("/consent.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Cookie</title><script>window.results = {};</script></head><body>
        <div id="onetrust-banner-sdk"><p>Мы используем cookie</p>
        <button id="onetrust-accept-btn-handler" onclick="window.results.consent='accepted'">Accept All</button>
        <button id="onetrust-reject-all-handler" onclick="window.results.consent='rejected'">Reject All</button></div>
        </body></html>
        """)
        set("/consent2.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Cookie 2</title><script>window.results = {};</script></head><body>
        <div class="my-cookie-banner"><p>Этот сайт использует файлы cookie</p>
        <button onclick="window.results.consent='accepted'">Принять</button>
        <button onclick="window.results.consent='rejected'">Отклонить все</button></div>
        </body></html>
        """)
        set("/password.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Вход</title></head><body>
        <form><input id="login" type="text"><input id="pw" type="password"></form></body></html>
        """)
        set("/cookie.html", html: "<!doctype html><title>cookie</title><script>document.cookie = 'ward_test=1; max-age=3600; path=/';</script><p>cookie</p>")
        set("/strict.html", html: "<!doctype html><title>Строгий</title><canvas id='c'></canvas>")
        set("/rects.html", html: "<!doctype html><title>rects</title><div id='box' style='width:123.4px;height:56.7px'>Ward</div>")
        set("/file.bin", type: "application/octet-stream", headers: ["Content-Disposition": "attachment; filename=\"ward-test.bin\""], body: Self.downloadBody)
        set("/target.html", html: "<!doctype html><title>Цель</title><p>target</p>")
    }

    /// Отпечаток холста, как его снимают трекеры: текст, фигуры, toDataURL → хеш.
    static let canvasProbe = """
    const c = document.getElementById('c') || document.body.appendChild(document.createElement('canvas'));
    c.width = 240; c.height = 60;
    const ctx = c.getContext('2d');
    ctx.textBaseline = 'top';
    ctx.font = "16px 'Arial'";
    ctx.fillStyle = '#f60';
    ctx.fillRect(100, 1, 62, 20);
    ctx.fillStyle = '#069';
    ctx.fillText('Ward отпечаток 😃', 2, 15);
    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)';
    ctx.fillText('Ward отпечаток 😃', 4, 17);
    const data = c.toDataURL();
    let h = 0;
    for (let i = 0; i < data.length; i++) { h = (Math.imul(h, 31) + data.charCodeAt(i)) | 0; }
    return {
      hash: String(h >>> 0),
      gpc: navigator.globalPrivacyControl === true,
      cores: navigator.hardwareConcurrency,
      native: Function.prototype.toString.call(HTMLCanvasElement.prototype.toDataURL).includes('[native code]'),
      ua: navigator.userAgent
    };
    """
}

/// Крошечный SOCKS5-прокси для самопроверки: любой адрес ведёт на HTTP-стенд, а сервер запоминает,
/// пришло ли имя сайта (значит, DNS тоже идёт через прокси) или уже IP-адрес.
nonisolated final class MiniSOCKS: @unchecked Sendable {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "app.ward.minisocks")
    private let lock = NSLock()
    private(set) var port = 0
    private var _requests: [(name: String, isDomain: Bool)] = []
    let targetPort: Int

    init(targetPort: Int) { self.targetPort = targetPort }

    var requests: [(name: String, isDomain: Bool)] { lock.withLock { _requests } }

    func start() throws {
        // Только loopback: стенд не виден в локальной сети, и macOS не спрашивает разрешения на неё.
        let parameters = NWParameters.tcp
        parameters.requiredInterfaceType = .loopback
        let l = try NWListener(using: parameters, on: .any)
        let ready = DispatchSemaphore(value: 0)
        l.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        l.newConnectionHandler = { [weak self] c in self?.serve(c) }
        l.start(queue: queue)
        _ = ready.wait(timeout: .now() + 5)
        port = Int(l.port?.rawValue ?? 0)
        listener = l
    }

    func stop() { listener?.cancel() }

    private func serve(_ client: NWConnection) {
        client.start(queue: queue)
        // Приветствие: версия 5, без пароля.
        client.receive(minimumIncompleteLength: 2, maximumLength: 64) { [weak self] data, _, _, _ in
            guard let self, let data, data.first == 5 else { client.cancel(); return }
            client.send(content: Data([5, 0]), completion: .contentProcessed { _ in
                client.receive(minimumIncompleteLength: 5, maximumLength: 512) { data, _, _, _ in
                    guard let data, data.count >= 7, data[0] == 5, data[1] == 1 else { client.cancel(); return }
                    let bytes = [UInt8](data)
                    var name = ""
                    var isDomain = false
                    switch bytes[3] {
                    case 1: name = bytes[4..<8].map(String.init).joined(separator: ".")
                    case 3:
                        let n = Int(bytes[4])
                        if bytes.count >= 5 + n { name = String(decoding: bytes[5..<(5 + n)], as: UTF8.self) }
                        isDomain = true
                    default: name = "ipv6"
                    }
                    self.lock.withLock { self._requests.append((name, isDomain)) }
                    let upstream = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: UInt16(self.targetPort))!, using: .tcp)
                    upstream.start(queue: self.queue)
                    client.send(content: Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { _ in
                        self.pipe(client, upstream)
                        self.pipe(upstream, client)
                    })
                }
            })
        }
    }

    private func pipe(_ from: NWConnection, _ to: NWConnection) {
        from.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, error in
            if let data, !data.isEmpty {
                to.send(content: data, completion: .contentProcessed { _ in
                    if done || error != nil { to.cancel() } else { self?.pipe(from, to) }
                })
            } else if done || error != nil {
                to.cancel()
                from.cancel()
            } else {
                self?.pipe(from, to)
            }
        }
    }
}
