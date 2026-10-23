import Foundation

/// Скрипты, которые Ward добавляет в страницы.
///
/// - Защита от отпечатка — в мире страницы (сайт должен видеть уже подменённые функции), до любых
///   скриптов сайта, во всех фреймах.
/// - Наблюдатель — в отдельном мире Ward: страница не видит его и не может подделать его сообщения.
nonisolated enum Scripts {
    /// Защита от отпечатка браузера (fingerprinting).
    ///
    /// Сайты и трекеры узнают вас без cookie: рисуют невидимую картинку на холсте, звук в аудио,
    /// спрашивают видеокарту — у каждого компьютера результат чуть свой, и это «отпечаток».
    /// Ward добавляет в такие ответы незаметный шум: для каждого сайта свой и новый при каждом запуске.
    /// Внутри сайта отпечаток стабилен (сайт не ломается), между сайтами и сеансами — разный (связать нельзя).
    static func fingerprint(secret: String, blockWebRTC: Bool, spoofTimezone: Bool = false, spoofLanguage: Bool = false) -> String {
        fingerprintSource
            .replacingOccurrences(of: "__WARD_SECRET__", with: secret)
            .replacingOccurrences(of: "__WARD_WEBRTC__", with: blockWebRTC ? "true" : "false")
            .replacingOccurrences(of: "__WARD_TZ__", with: spoofTimezone ? "true" : "false")
            .replacingOccurrences(of: "__WARD_LANG__", with: spoofLanguage ? "true" : "false")
    }

    static let fingerprintSource = #"""
    (() => {
      'use strict';
      const SECRET = '__WARD_SECRET__';
      const BLOCK_WEBRTC = __WARD_WEBRTC__;
      const SPOOF_TZ = __WARD_TZ__;
      const SPOOF_LANG = __WARD_LANG__;
      let host = '';
      try { host = location.hostname || ''; } catch (e) {}
      const labels = host.split('.');
      const site = labels.length > 2 && !/^[0-9.]+$/.test(host) ? labels.slice(-2).join('.') : host;
      const hash = (text) => {
        let h = 0x811c9dc5;
        for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 0x01000193); }
        return h >>> 0;
      };
      const seed = hash(SECRET + '|' + site);
      const random = (s) => () => {
        s = (s + 0x6D2B79F5) | 0;
        let t = Math.imul(s ^ (s >>> 15), 1 | s);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };

      // Подменённые функции выглядят как встроенные: toString() отвечает «[native code]».
      const nativeToString = Function.prototype.toString;
      const masks = new WeakMap();
      const mask = (fn, name) => { masks.set(fn, 'function ' + name + '() {\n    [native code]\n}'); return fn; };
      const toString = { toString() { return masks.has(this) ? masks.get(this) : nativeToString.call(this); } }.toString;
      mask(toString, 'toString');

      const replace = (target, key, impl) => {
        if (!target) return;
        const desc = Object.getOwnPropertyDescriptor(target, key);
        if (!desc || typeof desc.value !== 'function') return;
        const original = desc.value;
        const wrapped = { [key](...args) { return impl.call(this, original, args); } }[key];
        Object.defineProperty(wrapped, 'length', { value: original.length, configurable: true });
        mask(wrapped, key);
        Object.defineProperty(target, key, { configurable: desc.configurable, enumerable: desc.enumerable, writable: desc.writable, value: wrapped });
      };
      const replaceGetter = (target, key, impl) => {
        if (!target) return;
        const desc = Object.getOwnPropertyDescriptor(target, key);
        if (!desc || typeof desc.get !== 'function') return;
        const original = desc.get;
        const holder = { get [key]() { return impl.call(this, original); } };
        const getter = Object.getOwnPropertyDescriptor(holder, key).get;
        mask(getter, 'get ' + key);
        Object.defineProperty(target, key, { configurable: desc.configurable, enumerable: desc.enumerable, get: getter, set: desc.set });
      };
      const defineGetter = (target, key, value) => {
        if (!target || key in target) return;
        const getter = Object.getOwnPropertyDescriptor({ get [key]() { return value; } }, key).get;
        mask(getter, 'get ' + key);
        Object.defineProperty(target, key, { configurable: true, enumerable: true, get: getter });
      };

      // Шум в пикселях: младший бит одного канала у нескольких непрозрачных точек.
      // Положение точек зависит только от сайта и размера — повторный снимок даёт тот же результат.
      const noisePixels = (data, width, height) => {
        const count = data.length >> 2;
        if (count === 0 || count > 4000000) return;
        const r = random(seed ^ Math.imul(width | 0, 7919) ^ (height | 0));
        const want = Math.max(4, Math.min(64, count >> 9));
        const stride = 7919;
        let index = Math.floor(r() * count);
        let done = 0;
        for (let tries = 0; tries < want * 64 && done < want; tries++) {
          index = (index + stride + Math.floor(r() * 97)) % count;
          const p = index << 2;
          if (data[p + 3] === 0) continue;
          data[p + Math.floor(r() * 3)] ^= 1;
          done++;
        }
      };

      const patched = new WeakSet();
      const patch = (w) => {
        if (!w || patched.has(w)) return;
        patched.add(w);
        try { Object.defineProperty(w.Function.prototype, 'toString', { configurable: true, enumerable: false, writable: true, value: toString }); } catch (e) {}

        // Холст: снимок (toDataURL, toBlob) делается с копии с шумом, сам холст не меняется.
        const getImageData = w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype.getImageData;
        const noisyCopy = (canvas) => {
          try {
            const width = canvas.width, height = canvas.height;
            if (!width || !height || width * height > 4000000 || !canvas.ownerDocument) return null;
            const copy = canvas.ownerDocument.createElement('canvas');
            copy.width = width; copy.height = height;
            const ctx = copy.getContext('2d');
            ctx.drawImage(canvas, 0, 0);
            const image = getImageData.call(ctx, 0, 0, width, height);
            noisePixels(image.data, width, height);
            ctx.putImageData(image, 0, 0);
            return copy;
          } catch (e) { return null; }
        };
        if (w.HTMLCanvasElement) {
          replace(w.HTMLCanvasElement.prototype, 'toDataURL', function (original, args) { return original.apply(noisyCopy(this) || this, args); });
          replace(w.HTMLCanvasElement.prototype, 'toBlob', function (original, args) { return original.apply(noisyCopy(this) || this, args); });
        }
        const imageDataNoise = function (original, args) {
          const image = original.apply(this, args);
          try { noisePixels(image.data, image.width, image.height); } catch (e) {}
          return image;
        };
        if (w.CanvasRenderingContext2D) replace(w.CanvasRenderingContext2D.prototype, 'getImageData', imageDataNoise);
        if (w.OffscreenCanvasRenderingContext2D) replace(w.OffscreenCanvasRenderingContext2D.prototype, 'getImageData', imageDataNoise);

        // Ширина текста: по ней узнают установленные шрифты. Сдвиг в стотысячные доли пикселя.
        if (w.CanvasRenderingContext2D) replace(w.CanvasRenderingContext2D.prototype, 'measureText', function (original, args) {
          const metrics = original.apply(this, args);
          try {
            const width = metrics.width;
            const shift = ((hash(String(args[0]) + '|' + this.font) ^ seed) % 1000) / 1000 * 0.00004 * Math.max(1, width);
            Object.defineProperty(metrics, 'width', { configurable: true, enumerable: true, value: width + shift });
          } catch (e) {}
          return metrics;
        });

        // WebGL: одинаковое имя видеокарты для всех Mac и шум в считанных пикселях.
        for (const ctor of [w.WebGLRenderingContext, w.WebGL2RenderingContext]) {
          if (!ctor) continue;
          replace(ctor.prototype, 'getParameter', function (original, args) {
            if (args[0] === 0x9245) return 'Apple Inc.';
            if (args[0] === 0x9246) return 'Apple GPU';
            return original.apply(this, args);
          });
          replace(ctor.prototype, 'readPixels', function (original, args) {
            const result = original.apply(this, args);
            try {
              const pixels = args[6];
              if (pixels instanceof Uint8Array || pixels instanceof Uint8ClampedArray) noisePixels(pixels, args[2], args[3]);
            } catch (e) {}
            return result;
          });
        }

        // Звук: неслышимый шум (десятимиллионные доли) в обработанном сигнале.
        const touched = new WeakSet();
        if (w.AudioBuffer) replace(w.AudioBuffer.prototype, 'getChannelData', function (original, args) {
          const data = original.apply(this, args);
          try {
            if (!touched.has(data)) {
              touched.add(data);
              const r = random(seed ^ data.length);
              for (let i = Math.floor(r() * 50); i < data.length; i += 50) data[i] += (r() - 0.5) * 2e-7;
            }
          } catch (e) {}
          return data;
        });
        if (w.AnalyserNode) {
          replace(w.AnalyserNode.prototype, 'getFloatFrequencyData', function (original, args) {
            const result = original.apply(this, args);
            try {
              const array = args[0];
              const r = random(seed ^ array.length);
              for (let i = 0; i < array.length; i++) array[i] += (r() - 0.5) * 1e-4;
            } catch (e) {}
            return result;
          });
        }

        // Число ядер — как у большинства Mac; сигнал «не продавать мои данные» (Global Privacy Control).
        if (w.Navigator) {
          replaceGetter(w.Navigator.prototype, 'hardwareConcurrency', function () { return 8; });
          defineGetter(w.Navigator.prototype, 'globalPrivacyControl', true);
        }

        // Размеры элементов на странице: по ним вычисляют шрифты и особенности отрисовки. Сдвиг — стотысячные доли пикселя.
        const rectShift = (r) => ((seed ^ Math.imul(Math.round(r.width * 64) | 0, 2654435761) ^ Math.imul(Math.round(r.height * 64) | 0, 40503)) >>> 0) % 997 / 997 * 0.00003;
        const noisyRect = (r) => {
          if (!r || !w.DOMRect) return r;
          const d = rectShift(r);
          return new w.DOMRect(r.x + d, r.y + d, r.width + d, r.height + d);
        };
        const noisyList = (list) => {
          const items = Array.from(list, noisyRect);
          const out = Object.create(w.DOMRectList ? w.DOMRectList.prototype : Object.prototype);
          items.forEach((r, i) => Object.defineProperty(out, i, { value: r, enumerable: true }));
          Object.defineProperty(out, 'length', { value: items.length });
          Object.defineProperty(out, 'item', { value: mask(function item(i) { return items[i] || null; }, 'item') });
          Object.defineProperty(out, Symbol.iterator, { value: function* () { yield* items; } });
          return out;
        };
        for (const ctor of [w.Element, w.Range]) {
          if (!ctor) continue;
          replace(ctor.prototype, 'getBoundingClientRect', function (original, args) { return noisyRect(original.apply(this, args)); });
          replace(ctor.prototype, 'getClientRects', function (original, args) { return noisyList(original.apply(this, args)); });
        }

        // Экран: доступная область — как весь экран (иначе видны размер Dock и строки меню).
        if (w.Screen) {
          replaceGetter(w.Screen.prototype, 'availWidth', function () { return this.width; });
          replaceGetter(w.Screen.prototype, 'availHeight', function () { return this.height; });
          replaceGetter(w.Screen.prototype, 'availLeft', function () { return 0; });
          replaceGetter(w.Screen.prototype, 'availTop', function () { return 0; });
        }

        // Голоса синтеза речи: набор установленных голосов почти уникален — оставляем стандартные.
        const BASE_VOICES = /^(Samantha|Alex|Fred|Victoria|Daniel|Karen|Moira|Tessa|Milena|Yuri|Anna|Thomas|Amelie|Alice|Monica|Kyoko|Ting-Ting|Yuna|Luciana|Paulina)\b/;
        if (w.SpeechSynthesis) replace(w.SpeechSynthesis.prototype, 'getVoices', function (original, args) {
          const voices = original.apply(this, args);
          try { return voices.filter((v) => BASE_VOICES.test(v.name)); } catch (e) { return voices; }
        });

        // Часовой пояс — UTC (по желанию): сайты не узнают, в какой стране вы находитесь.
        if (SPOOF_TZ && w.Date) {
          const D = w.Date.prototype;
          for (const part of ['Date', 'Day', 'FullYear', 'Hours', 'Milliseconds', 'Minutes', 'Month', 'Seconds']) {
            const utcGet = D['getUTC' + part];
            if (utcGet) replace(D, 'get' + part, function (original, args) { return utcGet.apply(this, args); });
            const utcSet = D['setUTC' + part];
            if (utcSet) replace(D, 'set' + part, function (original, args) { return utcSet.apply(this, args); });
          }
          replace(D, 'getTimezoneOffset', function () { return 0; });
          const pad = (n) => String(n).padStart(2, '0');
          const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
          const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
          const dateText = (d) => `${DAYS[d.getUTCDay()]} ${MONTHS[d.getUTCMonth()]} ${pad(d.getUTCDate())} ${d.getUTCFullYear()}`;
          const timeText = (d) => `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())} GMT+0000 (Coordinated Universal Time)`;
          replace(D, 'toString', function (original, args) { return isNaN(this) ? original.apply(this, args) : dateText(this) + ' ' + timeText(this); });
          replace(D, 'toDateString', function (original, args) { return isNaN(this) ? original.apply(this, args) : dateText(this); });
          replace(D, 'toTimeString', function (original, args) { return isNaN(this) ? original.apply(this, args) : timeText(this); });
          for (const name of ['toLocaleString', 'toLocaleDateString', 'toLocaleTimeString']) {
            replace(D, name, function (original, args) {
              const options = Object.assign({}, args[1] || {});
              if (!options.timeZone) options.timeZone = 'UTC';
              return original.call(this, args[0], options);
            });
          }
          if (w.Intl && w.Intl.DateTimeFormat) {
            const DTF = w.Intl.DateTimeFormat;
            const withUTC = (args) => { const a = Array.from(args); const o = Object.assign({}, a[1] || {}); if (!o.timeZone) o.timeZone = 'UTC'; a[1] = o; return a; };
            w.Intl.DateTimeFormat = new Proxy(DTF, {
              construct(target, args) { return Reflect.construct(target, withUTC(args)); },
              apply(target, thisArg, args) { return Reflect.apply(target, thisArg, withUTC(args)); },
            });
          }
          // «2025-01-01T10:00» без пояса — тоже по UTC, как и всё остальное.
          const NativeDate = w.Date;
          const localISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;
          w.Date = new Proxy(NativeDate, {
            construct(target, args) {
              if (args.length >= 2 && args.every((a) => typeof a === 'number')) return new target(NativeDate.UTC(...args));
              if (args.length === 1 && typeof args[0] === 'string' && localISO.test(args[0])) return new target(args[0] + 'Z');
              return Reflect.construct(target, args);
            },
          });
        }

        // Язык (по желанию): английский, как у большинства пользователей в мире.
        if (SPOOF_LANG && w.Navigator) {
          replaceGetter(w.Navigator.prototype, 'language', function () { return 'en-US'; });
          replaceGetter(w.Navigator.prototype, 'languages', function () { return Object.freeze(['en-US', 'en']); });
        }

        // WebRTC может раскрыть настоящий IP-адрес даже через VPN — по желанию отключается.
        if (BLOCK_WEBRTC) {
          for (const name of ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'RTCSessionDescription', 'RTCIceCandidate']) {
            try { Object.defineProperty(w, name, { configurable: true, writable: true, value: undefined }); } catch (e) {}
          }
        }

        // Чистые функции часто берут из нового пустого фрейма — защищаем и его.
        if (w.HTMLIFrameElement) {
          replaceGetter(w.HTMLIFrameElement.prototype, 'contentWindow', function (original) {
            const child = original.call(this);
            try { if (child) patch(child); } catch (e) {}
            return child;
          });
          replaceGetter(w.HTMLIFrameElement.prototype, 'contentDocument', function (original) {
            const doc = original.call(this);
            try { if (doc && doc.defaultView) patch(doc.defaultView); } catch (e) {}
            return doc;
          });
        }
      };
      patch(window);
    })();
    """#

    /// Наблюдатель: какие сторонние адреса страница пытается загрузить. Список сравнивается со списками
    /// трекеров в Ward — так в щите видно, кто пытался следить на этой странице.
    static let observer = #"""
    (() => {
      const seen = new Set();
      const failedSeen = new Set();
      let failed = [];
      let queue = [];
      let timer = null;
      const flush = () => {
        timer = null;
        if (!queue.length && !failed.length) return;
        const hosts = queue, broken = failed;
        queue = [];
        failed = [];
        try { window.webkit.messageHandlers.wardHosts.postMessage({ hosts: hosts, failed: broken, top: window === window.top }); } catch (e) {}
      };
      const add = (value) => {
        if (!value || typeof value !== 'string') return;
        let hostname;
        try {
          const url = new URL(value, location.href);
          if (url.protocol !== 'http:' && url.protocol !== 'https:') return;
          hostname = url.hostname;
        } catch (e) { return; }
        if (!hostname || seen.has(hostname)) return;
        seen.add(hostname);
        queue.push(hostname);
        if (!timer) timer = setTimeout(flush, 600);
      };
      const LINKS = /stylesheet|preload|prefetch|preconnect|dns-prefetch|modulepreload|icon/i;
      const scan = (el) => {
        if (!el || el.nodeType !== 1) return;
        switch (el.tagName) {
          case 'SCRIPT': case 'IMG': case 'IFRAME': case 'EMBED': case 'SOURCE': case 'VIDEO': case 'AUDIO': case 'TRACK':
            add(el.getAttribute('src')); break;
          case 'LINK':
            if (LINKS.test(el.getAttribute('rel') || '')) add(el.getAttribute('href')); break;
          case 'OBJECT':
            add(el.getAttribute('data')); break;
        }
      };
      const SELECTOR = 'script[src],img[src],iframe[src],embed[src],source[src],video[src],audio[src],track[src],link[href],object[data]';
      const scanTree = (root) => {
        scan(root);
        if (root.querySelectorAll) root.querySelectorAll(SELECTOR).forEach(scan);
      };
      new MutationObserver((mutations) => {
        for (const m of mutations) {
          if (m.type === 'attributes') scan(m.target);
          else m.addedNodes.forEach((node) => { if (node.nodeType === 1) scanTree(node); });
        }
      }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'href', 'data'] });
      try {
        new PerformanceObserver((list) => list.getEntries().forEach((entry) => add(entry.name)))
          .observe({ type: 'resource', buffered: true });
      } catch (e) {}
      // Не загрузившиеся картинки, скрипты и фреймы: так видно запросы, остановленные по пути, а не по домену.
      document.addEventListener('error', (event) => {
        const el = event.target;
        if (!el || !el.getAttribute) return;
        const value = el.getAttribute('src') || el.getAttribute('href') || el.getAttribute('data');
        if (!value) return;
        try {
          const hostname = new URL(value, location.href).hostname;
          if (hostname && !failedSeen.has(hostname)) {
            failedSeen.add(hostname);
            failed.push(hostname);
            if (!timer) timer = setTimeout(flush, 600);
          }
        } catch (e) {}
      }, true);
      document.addEventListener('DOMContentLoaded', () => { if (document.documentElement) scanTree(document.documentElement); });
      // Пароль на странице без шифрования: предупредить, как только поле пароля получит фокус.
      if (location.protocol === 'http:' && window === window.top) {
        let warned = false;
        document.addEventListener('focusin', (e) => {
          const t = e.target;
          if (warned || !t || t.tagName !== 'INPUT' || String(t.type).toLowerCase() !== 'password') return;
          warned = true;
          try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'password-http' }); } catch (err) {}
        }, true);
      }
    })();
    """#

    /// Проверка селекторов скрытия движком страниц: неверный селектор в правиле WebKit отключает всё правило.
    static let validateSelectors = """
    const bad = [];
    const frag = document.createDocumentFragment();
    for (const s of list) {
      try { frag.querySelector(s); } catch (e) { bad.push(s); }
    }
    return bad;
    """
}
