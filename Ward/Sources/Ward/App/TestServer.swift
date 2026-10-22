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
    private var routes: [String: (type: String, body: Data)] = [:]
    private var log: [String] = []

    func set(_ path: String, type: String = "text/html; charset=utf-8", body: Data) {
        lock.withLock { routes[path] = (type, body) }
    }

    func set(_ path: String, html: String) { set(path, body: Data(html.utf8)) }

    /// Пути, которые запрашивал браузер (для проверки «до сервера не дошло»).
    var requested: [String] { lock.withLock { log } }

    func start() throws {
        let l = try NWListener(using: .tcp, on: .any)
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
            let route = self.lock.withLock { () -> (type: String, body: Data)? in
                self.log.append(target)
                return self.routes[path]
            }
            var head: String
            var body = Data()
            if let route {
                head = "HTTP/1.1 200 OK\r\nContent-Type: \(route.type)\r\nContent-Length: \(route.body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n"
                body = request.hasPrefix("HEAD") ? Data() : route.body
            } else {
                head = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n"
            }
            c.send(content: Data((head + "\r\n").utf8) + body, completion: .contentProcessed { _ in c.cancel() })
        }
    }

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
