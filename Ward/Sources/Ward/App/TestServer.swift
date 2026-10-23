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
                let status = route.headers["Location"] != nil ? "302 Found" : "200 OK"
                head = "HTTP/1.1 \(status)\r\nContent-Type: \(route.type)\r\nContent-Length: \(route.body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n"
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
        // Страница, которая снимает отпечаток всеми способами сразу — как FingerprintJS.
        set("/fp-all.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Отпечаток всеми способами</title></head><body>
        <canvas id="c" width="200" height="50"></canvas>
        <script>
        const c = document.getElementById('c'), ctx = c.getContext('2d');
        ctx.fillText('Ward', 4, 20); c.toDataURL();
        try {
          const gl = document.createElement('canvas').getContext('webgl');
          const ext = gl && gl.getExtension('WEBGL_debug_renderer_info');
          if (gl && ext) gl.getParameter(ext.UNMASKED_RENDERER_WEBGL);
        } catch (e) {}
        try { const a = new OfflineAudioContext(1, 5000, 44100); const o = a.createOscillator(); o.connect(a.destination); o.start(0); a.startRendering(); } catch (e) {}
        const fonts = ['Arial','Courier','Georgia','Helvetica','Times','Verdana','Monaco','Menlo','Futura','Optima','Palatino','Didot',
                       'Avenir','Baskerville','Copperplate','Papyrus','Herculanum','Chalkboard','Skia','Zapfino','Impact','Tahoma'];
        for (const f of fonts) { ctx.font = '16px ' + f + ', monospace'; ctx.measureText('mmmmmmmmmmlli'); }
        try { speechSynthesis.getVoices(); } catch (e) {}
        </script></body></html>
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
        // Плашки поверх статьи, как на новостных сайтах: шапка с меню остаётся, «Подпишитесь», затемнение,
        // окно «отключите блокировщик» и форма рассылки убираются, прокрутка возвращается.
        set("/annoy.html", html: #"""
<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;font:16px sans-serif;overflow:hidden}
#bg{position:fixed;inset:0;z-index:-1;background:linear-gradient(#eef,#fff)}
header{position:fixed;top:0;left:0;right:0;height:120px;background:#000;color:#fff;z-index:10}
header a{color:#fff;margin:8px}
main{padding:140px 60px;height:3000px}
#sheet{position:fixed;left:0;right:0;bottom:0;height:260px;background:#222;color:#fff;z-index:100;text-align:center}
#fade{position:fixed;left:0;right:0;bottom:260px;height:300px;background:linear-gradient(transparent,#ddd);pointer-events:none;z-index:99}
#dlg{position:fixed;top:30%;left:30%;width:40%;height:200px;background:#fff;border:1px solid;z-index:200;display:none}
</style></head><body>
<div id="bg"></div>
<header><b>Bloomberg</b> <a href="#">Markets</a><a href="#">Finance</a><a href="#">Economics</a><a href="#">Industries</a><a href="#">Tech</a><a href="#">Politics</a><a href="#">Opinion</a><a href="#">Video</a><a href="#">More</a><button>Subscribe</button></header>
<main><h1>In Riot-Hit Cities, the French Snap Up Weapons</h1><p>Article text...</p><button id="open" onclick="document.getElementById('dlg').style.display='block'">Share</button></main>
<div id="fade"></div>
<div id="sheet"><h2>Subscribe now for unlimited access to Bloomberg.com and the Bloomberg app</h2><p>Global news that uncovers a new tomorrow. Cancel anytime.</p><button>Claim This Offer</button></div>
<div id="dlg"><h3>Sign up to share this article</h3><button>OK</button></div>
<script>
window.results = {};
setTimeout(()=>{const m=document.createElement('div');m.id='adb';m.style.cssText='position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:500';m.innerHTML='<div style="background:#fff;margin:20% auto;width:400px;padding:20px">Please disable your ad blocker to continue</div>';document.body.appendChild(m);},1500);
setTimeout(()=>{const n=document.createElement('div');n.id='news';n.style.cssText='position:fixed;right:20px;bottom:300px;width:320px;height:180px;background:#fff;z-index:600;border:1px solid';n.innerHTML='Get our newsletter <input type=email><button>Go</button>';document.body.appendChild(n);},2500);
</script>
</body></html>
"""#)
        // Следы: GPC, Referer, cookie «навсегда», «отскоки», домашняя сеть, скрипт-сборщик отпечатка.
        set("/gpc.html", html: "<!doctype html><title>gpc</title><script>window.results = { gpc: navigator.globalPrivacyControl };</script>")
        set("/ref-a.html", html: """
        <!doctype html><html><head><meta name="referrer" content="unsafe-url"></head><body>
        <a id="go" href="http://localhost:\(p)/ref-b.html?from=a">дальше</a></body></html>
        """)
        set("/ref-b.html", html: """
        <!doctype html><script>
        document.cookie = 'forever=1; max-age=315360000; path=/';
        document.cookie = 'session=1; path=/';
        window.results = { referrer: document.referrer };
        </script>
        """)
        set("/bstart.html", html: """
        <!doctype html><body><a id="go" href="http://localhost:\(p)/bounce">server</a>
        <a id="js" href="http://localhost:\(p)/jsb.html">script</a></body>
        """)
        set("/bounce", headers: ["Location": "http://127.0.0.1:\(p)/bfinal.html?server", "Set-Cookie": "bounce_id=42; Path=/; Max-Age=31536000"], body: Data())
        set("/jsb.html", html: """
        <!doctype html><script>document.cookie = 'bounce_js=1; path=/'; location.replace('http://127.0.0.1:\(p)/bfinal.html?js');</script>
        """)
        set("/bfinal.html", html: "<!doctype html><title>final</title><p>Конечная страница</p>")
        set("/fp-collector.js", type: "application/javascript", body: Data("""
        (() => {
          const c = document.createElement('canvas'); c.width = 120; c.height = 40;
          const x = c.getContext('2d'); x.fillText('Ward 😀', 4, 20); c.toDataURL();
          const ctx = new OfflineAudioContext(1, 4410, 44100); const o = ctx.createOscillator(); o.connect(ctx.destination); o.start(0); ctx.startRendering();
          for (const f of ['Arial','Helvetica','Times','Courier','Verdana','Georgia','Palatino','Garamond','Bookman','Tahoma','Trebuchet MS','Arial Black','Impact','Comic Sans MS','Monaco','Menlo','Optima','Futura','Gill Sans','Didot','Avenir']) { x.font = '16px ' + f; x.measureText('mmmmmmmmmmlli'); }
          window.collected = true;
        })();
        """.utf8))
        set("/fp-ext.html", html: "<!doctype html><title>сборщик</title><script src=\"http://127.0.0.1:\(p)/fp-collector.js\"></script>")
        set("/lan-pixel.png", type: "image/png", body: Self.pixel)
        set("/lan-fetch", type: "text/plain", body: Data("ok".utf8))

        // Фоновые потоки: относительные адреса внутри потока, модульный поток и страница с CSP без blob:.
        let js = "application/javascript"
        set("/wk/w.js", type: js, body: Data("""
        importScripts('helper.js');
        fetch('data.json').then((r) => r.json()).then((d) => postMessage({ hc: navigator.hardwareConcurrency, gpc: navigator.globalPrivacyControl === true,
          href: String(self.location.href), helper: self.helperLoaded === true, data: d.ok }), (e) => postMessage({ error: String(e) }));
        """.utf8))
        set("/wk/helper.js", type: js, body: Data("self.helperLoaded = true;".utf8))
        set("/wk/data.json", type: "application/json", body: Data(#"{"ok": 1}"#.utf8))
        set("/wk/m.js", type: js, body: Data("import { v } from './dep.js'; postMessage({ v, hc: navigator.hardwareConcurrency });".utf8))
        set("/wk/dep.js", type: js, body: Data("export const v = 42;".utf8))
        let workerPage = """
        <!doctype html><html><body><script>
        window.results = { hc: navigator.hardwareConcurrency };
        const w = new Worker('w.js');
        w.onmessage = (e) => { window.results.worker = e.data; };
        w.onerror = (e) => { window.results.workerError = String(e.message || 'error'); };
        try {
          const m = new Worker('m.js', { type: 'module' });
          m.onmessage = (e) => { window.results.module = e.data; };
          m.onerror = (e) => { window.results.moduleError = String(e.message || 'error'); };
        } catch (e) { window.results.moduleError = String(e); }
        window.results.native = Function.prototype.toString.call(Worker).includes('[native code]') && (w instanceof Worker);
        </script></body></html>
        """
        set("/wk/page.html", html: workerPage)
        set("/wk/csp.html", headers: ["Content-Security-Policy": "worker-src 'self'; script-src 'self' 'unsafe-inline'"], body: Data(workerPage.utf8))
        // Как у Bloomberg: плашка в fixed-обёртке нулевой высоты, страница заперта классами на html и body,
        // «туман» — псевдоэлемент шапки, внизу плашки — десяток ссылок.
        set("/bbg.html", html: #"""
<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;font:16px sans-serif}
nav[data-component=nav]{position:relative;height:60px;background:#111;color:#fff}
nav a{color:#fff;margin:6px}
main{padding:40px 60px;height:3000px}
html._stopScroll_1ot87_3,body._stopScroll_1ot87_3{overflow:hidden!important;position:fixed!important;height:100%!important;width:100%!important;pointer-events:none!important}
._restrictScroll_1ot87_45 nav[data-component=nav]:after{content:"";position:absolute;left:0;top:60px;width:100%;height:100vh;background:linear-gradient(180deg,#fff3 .57%,#0006 15.48%);z-index:10}
._sheetWrapper_1ot87_12{position:fixed;bottom:0;z-index:14678;display:block;width:100%;margin:0;box-sizing:border-box;pointer-events:none}
._sheet_1ot87_12{position:fixed;bottom:0;height:100vh;overflow-y:hidden;z-index:9999;width:100%}
.wall{position:absolute;bottom:0;left:0;right:0;height:260px;background:#222;color:#fff;pointer-events:auto;text-align:center;animation:fadein 1.5s}
.wall a{color:#aaa;font-size:11px;margin:3px}
@keyframes fadein{from{opacity:0;transform:translateY(100%)}to{opacity:1;transform:none}}
</style></head><body>
<nav data-component="nav"><b>Bloomberg</b> <a href="/m">Markets</a><a href="/f">Finance</a><a href="/e">Economics</a><a href="/i">Industries</a><a href="/t">Tech</a><a href="/p">Politics</a><a href="/o">Opinion</a><a href="/v">Video</a><a href="/x">More</a></nav>
<main><h1>Nigeria's Solar Boom Has Newlyweds Coveting Panels</h1><p>Article...</p><button id="open" onclick="window.clicked=1">Share</button></main>
<div id="fortress-container-root"></div>
<script>
setTimeout(() => {
  document.documentElement.classList.add('_stopScroll_1ot87_3');
  document.body.classList.add('_stopScroll_1ot87_3', '_restrictScroll_1ot87_45');
  document.getElementById('fortress-container-root').innerHTML =
    '<div class="_sheetWrapper_1ot87_12" id="wrap"><div class="_sheet_1ot87_12" id="bsheet"><div class="wall" id="wall">' +
    '<h2>Subscribe now for unlimited access to Bloomberg.com and the Bloomberg app</h2><p>Global news that uncovers a new tomorrow. Cancel anytime.</p>' +
    '<button>Claim This Offer</button><div>' + ['Terms of Service','Privacy Policy','Cookie Settings','Help','Contact','Accessibility','Careers','Ad Choices','Sitemap','Trademarks'].map((t, i) => '<a href="/l' + i + '">' + t + '</a>').join('') +
    '</div></div></div></div>';
}, 1200);
</script>
</body></html>
"""#)
        // Изолятор: страница просит всё подряд и записывает, что получила.
        set("/iso.html", html: """
        <!doctype html><html><head><meta charset="utf-8"><title>Изолятор</title><script>
        window.results = {};
        const r = window.results;
        document.cookie = 'ward_iso=1; path=/';
        try { localStorage.setItem('ward_iso', '1'); } catch (e) {}
        r.cores = navigator.hardwareConcurrency; r.screen = screen.width;
        r.native = String(navigator.geolocation.getCurrentPosition).includes('[native code]');
        navigator.geolocation.getCurrentPosition(() => r.geo = 'granted', (e) => r.geo = e.code);
        if (navigator.clipboard) navigator.clipboard.readText().then(() => r.clip = 'read', (e) => r.clip = e.name); else r.clip = 'none';
        if (typeof Notification !== 'undefined') { r.notif = Notification.permission; Notification.requestPermission().then((x) => r.notifReq = x); } else { r.notif = 'none'; }
        if (navigator.mediaDevices) {
          navigator.mediaDevices.getUserMedia({ video: true, audio: true }).then(() => r.gum = 'granted', (e) => r.gum = e.name);
          navigator.mediaDevices.enumerateDevices().then((d) => { r.devices = d.length; r.labels = d.filter((x) => x.label || x.deviceId).length; });
        } else { r.gum = 'none'; r.devices = 0; r.labels = 0; }
        r.webrtc = typeof RTCPeerConnection;
        if (navigator.permissions) navigator.permissions.query({ name: 'geolocation' }).then((s) => r.perm = s.state, () => r.perm = 'error'); else r.perm = 'none';
        window.addEventListener('devicemotion', () => {});
        </script></head><body>изолятор</body></html>
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
