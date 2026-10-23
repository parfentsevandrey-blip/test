import Foundation
import WebKit

/// Правдоподобие вымышленного устройства: та же батарея проверок, что у детекторов подделки (CreepJS,
/// FingerprintJS, защита от ботов), — на невидимой странице со всеми скриптами Ward. Подделка, которую
/// видно, хуже честного Mac: такой браузер выделяется из толпы, и сайты перестают его пускать.
struct CoherenceReport: Sendable, Equatable {
    struct Check: Sendable, Equatable, Identifiable {
        /// Какая проверка (см. `Coherence.titles`).
        var key: String
        var profile: String
        var ok: Bool
        var detail: String
        var id: String { profile + "|" + key }
        var title: String { Coherence.titles[key] ?? key }
        var explanation: String { Coherence.explanations[key] ?? "" }
    }

    var checks: [Check]
    var date: Date

    var passed: Int { checks.filter(\.ok).count }
    var score: Double { checks.isEmpty ? 0 : Double(passed) / Double(checks.count) }
    var failed: [Check] { checks.filter { !$0.ok } }
}

enum Coherence {
    /// Профили: обычный сайт под защитой и сайт в изоляторе (вымышленное устройство, отказы в разрешениях).
    static let profiles: [(id: String, title: String)] = [("site", "Обычные сайты"), ("isolator", "Изолятор")]

    static let titles: [String: String] = [
        "native": "Подменённые функции выглядят встроенными",
        "own-props": "Без лишних свойств у функций",
        "illegal": "Ошибки — как у настоящего Safari",
        "instance": "Свойства на своём месте (не на объекте)",
        "construct": "Функции не становятся конструкторами",
        "iframe": "Во фрейме — то же устройство",
        "worker": "В фоновом потоке — то же устройство",
        "canvas": "Холст стабилен при повторном снимке",
        "foreign": "Нет функций чужих браузеров",
        "webgl": "Видеокарта как у Mac",
        "screen": "Экран совпадает с CSS",
        "timezone": "Часовой пояс без следов подмены",
        "permissions": "Разрешения в обычном состоянии",
        "gpc": "Сигнал GPC на месте",
        "webrtc": "WebRTC на месте",
    ]

    static let explanations: [String: String] = [
        "native": "Детекторы печатают текст функции: у встроенной это «[native code]», у подменённой — её код.",
        "own-props": "У встроенных функций нет prototype и лишних полей — их наличие выдаёт обёртку.",
        "illegal": "Вызов «не на том объекте» у встроенной функции падает с TypeError — у обёртки может не падать.",
        "instance": "Подмена прямо на navigator или screen видна через Object.getOwnPropertyNames.",
        "construct": "new встроенный_метод() — ошибка; у обычной функции-обёртки — нет.",
        "iframe": "Чистые функции берут из нового фрейма: там должны быть те же ядра, языки, пояс и холст.",
        "worker": "В фоновый поток скрипты страницы не попадают: если значения там другие — подмена раскрыта.",
        "canvas": "Шум холста должен быть одинаковым при повторном снимке, иначе это шум, а не устройство.",
        "foreign": "deviceMemory, getBattery, userAgentData есть только в Chrome — в Safari их быть не может.",
        "webgl": "Строка Mac-браузера и видеокарта не от Apple — противоречие.",
        "screen": "Размер экрана в JavaScript сверяют с CSS-медиазапросами.",
        "timezone": "Подмена через Proxy видна по тексту конструктора; пояс UTC должен давать смещение 0.",
        "permissions": "«Запрещено всё» встречается редко и само по себе отпечаток; в чистом Safari — «спросить».",
        "gpc": "navigator.globalPrivacyControl = true, как у Firefox, Brave, DuckDuckGo.",
        "webrtc": "Без прокси исчезнувший WebRTC не прячет IP, но выдаёт «подделанный браузер».",
    ]

    /// Проверить оба профиля. Занимает около секунды.
    static func run(model: AppModel) async -> CoherenceReport {
        var checks: [CoherenceReport.Check] = []
        for profile in profiles {
            let host = "coherence.ward.invalid"
            let configuration = WKWebViewConfiguration()
            configuration.websiteDataStore = .nonPersistent()
            let controller = WKUserContentController()
            let scripts = model.pageScripts(host: host, protected: true, secret: model.sessionSecret, proxied: false,
                                            jar: profile.id == "isolator" ? host : nil)
            for script in scripts.list { controller.addUserScript(script) }
            configuration.userContentController = controller
            let page = HiddenPage(configuration: configuration)
            guard await page.loadHTML("<!doctype html><html><head><title>Ward</title></head><body><p>Ward</p></body></html>",
                                      baseURL: URL(string: "https://\(host)/")) else { continue }
            let expect = ["gpc": model.prefs.gpc, "webrtc": !model.prefs.blockWebRTC, "tz": model.prefs.spoofTimezone && profile.id == "site"]
            let raw = await page.run(battery, arguments: ["expect": expect]) as? String ?? "[]"
            let list = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [[String: Any]] ?? []
            for item in list {
                guard let id = item["id"] as? String else { continue }
                checks.append(.init(key: id, profile: profile.title, ok: item["ok"] as? Bool ?? false, detail: item["detail"] as? String ?? ""))
            }
        }
        return CoherenceReport(checks: checks, date: Date())
    }

    /// Батарея проверок в мире страницы — там же, где их запускают детекторы.
    static let battery = #"""
    const checks = [];
    const add = (id, ok, detail) => checks.push({ id, ok: !!ok, detail: String(detail || '') });
    const fts = Function.prototype.toString;
    const text = (fn) => { try { return fts.call(fn); } catch (e) { return 'error: ' + e.name; } };
    const desc = (o, k) => (o ? Object.getOwnPropertyDescriptor(o, k) : undefined);
    // Образцы встроенных функций, которые Ward не трогает: формат текста берётся у них.
    const refMethod = text(Document.prototype.getElementById);
    const refGetter = text(desc(Document.prototype, 'title').get);
    const refSetter = text(desc(Document.prototype, 'title').set);
    const expected = (ref, refName, name) => ref.replace(refName, name);

    const methods = [
      [HTMLCanvasElement.prototype, 'toDataURL'], [HTMLCanvasElement.prototype, 'toBlob'],
      [CanvasRenderingContext2D.prototype, 'getImageData'], [CanvasRenderingContext2D.prototype, 'measureText'],
      [Element.prototype, 'getBoundingClientRect'], [Element.prototype, 'getClientRects'],
      [window.WebGLRenderingContext && WebGLRenderingContext.prototype, 'getParameter'],
      [window.AudioBuffer && AudioBuffer.prototype, 'getChannelData'], [window, 'fetch'], [Function.prototype, 'toString'],
      [window.MediaDevices && MediaDevices.prototype, 'enumerateDevices'], [window.StorageManager && StorageManager.prototype, 'estimate'],
      [window.Permissions && Permissions.prototype, 'query'], [window.Geolocation && Geolocation.prototype, 'getCurrentPosition'],
      [window.SpeechSynthesis && SpeechSynthesis.prototype, 'getVoices'], [window, 'open'],
    ];
    const getters = [
      [Navigator.prototype, 'hardwareConcurrency'], [Screen.prototype, 'availWidth'], [Screen.prototype, 'availHeight'],
      [Document.prototype, 'referrer'], [HTMLIFrameElement.prototype, 'contentWindow'], [Navigator.prototype, 'globalPrivacyControl'],
    ];
    const bad = [];
    const props = [];
    for (const [o, k] of methods) {
      const d = desc(o, k);
      if (!d || typeof d.value !== 'function') continue;
      if (text(d.value) !== expected(refMethod, 'getElementById', k)) bad.push(k);
      const keys = Reflect.ownKeys(d.value).map(String).sort().join(',');
      if (keys !== 'length,name') props.push(k + ':' + keys);
    }
    for (const [o, k] of getters) {
      const d = desc(o, k);
      if (!d || typeof d.get !== 'function') continue;
      if (text(d.get) !== expected(refGetter, 'title', k)) bad.push('get ' + k);
      const keys = Reflect.ownKeys(d.get).map(String).sort().join(',');
      if (keys !== 'length,name') props.push('get ' + k + ':' + keys);
    }
    const cookieSet = desc(Document.prototype, 'cookie');
    if (cookieSet && cookieSet.set && text(cookieSet.set) !== expected(refSetter, 'title', 'cookie')) bad.push('set cookie');
    add('native', bad.length === 0, bad.join(', ') || 'все ' + (methods.length + getters.length));
    add('own-props', props.length === 0, props.join('; '));

    const throwsType = (f) => { try { f(); return false; } catch (e) { return e instanceof TypeError; } };
    const illegal = [];
    if (!throwsType(() => Navigator.prototype.hardwareConcurrency)) illegal.push('hardwareConcurrency');
    if (!throwsType(() => Screen.prototype.availWidth)) illegal.push('availWidth');
    if (!throwsType(() => Document.prototype.referrer)) illegal.push('referrer');
    if (!throwsType(() => HTMLCanvasElement.prototype.toDataURL.call({}))) illegal.push('toDataURL');
    add('illegal', illegal.length === 0, illegal.join(', '));

    add('instance', Object.getOwnPropertyNames(navigator).length === 0 && Object.getOwnPropertyNames(screen).length === 0,
        Object.getOwnPropertyNames(navigator).concat(Object.getOwnPropertyNames(screen)).join(', '));
    add('construct', throwsType(() => new HTMLCanvasElement.prototype.toDataURL()) && throwsType(() => new CanvasRenderingContext2D.prototype.getImageData()), '');

    // Один и тот же рисунок и одни и те же вопросы — в окне, во фрейме и в потоке.
    const draw = (ctx) => { ctx.fillStyle = '#f60'; ctx.fillRect(0, 0, 64, 32); ctx.fillStyle = '#069'; ctx.fillRect(8, 6, 30, 14); ctx.fillStyle = 'rgba(102,204,0,0.7)'; ctx.fillRect(20, 10, 40, 20); };
    const hashData = (data) => { let h = 2166136261; for (let i = 0; i < data.length; i++) { h ^= data[i]; h = Math.imul(h, 16777619); } return (h >>> 0).toString(16); };
    const snapshot = (w) => {
      const c = w.document.createElement('canvas'); c.width = 64; c.height = 32;
      const ctx = c.getContext('2d'); draw(ctx);
      return hashData(ctx.getImageData(0, 0, 64, 32).data);
    };
    const facts = (w) => ({ hc: w.navigator.hardwareConcurrency, lang: String(w.navigator.languages), gpc: w.navigator.globalPrivacyControl === true,
                            tz: new w.Date(2025, 0, 15, 12).getTimezoneOffset(), zone: new w.Intl.DateTimeFormat().resolvedOptions().timeZone });
    const here = facts(window);
    here.canvas = snapshot(window);
    const second = snapshot(window);
    add('canvas', here.canvas === second, here.canvas + ' / ' + second);

    const frame = document.createElement('iframe');
    document.body.appendChild(frame);
    const there = facts(frame.contentWindow);
    there.canvas = snapshot(frame.contentWindow);
    const diff = (a, b) => Object.keys(a).filter((k) => String(a[k]) !== String(b[k])).map((k) => k + ': ' + a[k] + ' ≠ ' + b[k]);
    add('iframe', diff(here, there).length === 0, diff(here, there).join('; '));

    let offscreen = '';
    if (window.OffscreenCanvas) {
      const oc = new OffscreenCanvas(64, 32); const octx = oc.getContext('2d'); draw(octx);
      offscreen = hashData(octx.getImageData(0, 0, 64, 32).data);
    }
    const workerFacts = await new Promise((resolve) => {
      const source = `
        const draw = ${draw.toString()};
        const hashData = ${hashData.toString()};
        let canvas = '';
        if (self.OffscreenCanvas) { const oc = new OffscreenCanvas(64, 32); const ctx = oc.getContext('2d'); draw(ctx); canvas = hashData(ctx.getImageData(0, 0, 64, 32).data); }
        postMessage({ hc: navigator.hardwareConcurrency, lang: String(navigator.languages), gpc: navigator.globalPrivacyControl === true,
                      tz: new Date(2025, 0, 15, 12).getTimezoneOffset(), zone: new Intl.DateTimeFormat().resolvedOptions().timeZone, canvas });`;
      try {
        const w = new Worker(URL.createObjectURL(new Blob([source], { type: 'text/javascript' })));
        w.onmessage = (e) => resolve(e.data);
        w.onerror = (e) => resolve({ error: String(e.message || 'error') });
        setTimeout(() => resolve({ error: 'timeout' }), 4000);
      } catch (e) { resolve({ error: String(e) }); }
    });
    if (workerFacts.error) add('worker', false, workerFacts.error);
    else {
      const mine = Object.assign({}, here, { canvas: offscreen });
      add('worker', diff(mine, workerFacts).length === 0, diff(mine, workerFacts).join('; '));
    }

    const foreign = ['deviceMemory', 'getBattery', 'userAgentData', 'connection'].filter((k) => k in navigator);
    add('foreign', foreign.length === 0, foreign.join(', '));

    try {
      const gl = document.createElement('canvas').getContext('webgl');
      if (gl) {
        const vendor = gl.getParameter(0x9245), renderer = gl.getParameter(0x9246);
        add('webgl', vendor === 'Apple Inc.' && renderer === 'Apple GPU', vendor + ' / ' + renderer);
      }
    } catch (e) {}

    add('screen', matchMedia('(device-width: ' + screen.width + 'px)').matches && screen.availWidth === screen.width, screen.width + '×' + screen.height);

    const tzText = text(Intl.DateTimeFormat), dateText = text(Date);
    const tzOK = tzText === expected(refMethod, 'getElementById', 'DateTimeFormat') && dateText === expected(refMethod, 'getElementById', 'Date')
      && (here.zone !== 'UTC' || here.tz === 0) && (!expect.tz || here.zone === 'UTC');
    add('timezone', tzOK, here.zone + ', ' + here.tz + (tzOK ? '' : ' · ' + tzText.slice(0, 40) + ' · ' + dateText.slice(0, 40)));

    try {
      const state = await Promise.race([navigator.permissions.query({ name: 'geolocation' }).then((s) => s.state), new Promise((r) => setTimeout(() => r('timeout'), 1500))]);
      add('permissions', state !== 'denied' && (typeof Notification === 'undefined' || Notification.permission !== 'denied'),
          state + (typeof Notification === 'undefined' ? '' : ', уведомления: ' + Notification.permission));
    } catch (e) {
      // WKWebView сам не поддерживает этот запрос — так же отвечает и любой браузер на WebKit без Ward.
      add('permissions', e && e.name === 'NotSupportedError', String(e));
    }

    if (expect.gpc) add('gpc', navigator.globalPrivacyControl === true, String(navigator.globalPrivacyControl));
    if (expect.webrtc) add('webrtc', typeof RTCPeerConnection === 'function', typeof RTCPeerConnection);
    return JSON.stringify(checks);
    """#
}
