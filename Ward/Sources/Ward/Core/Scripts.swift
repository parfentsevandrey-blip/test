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
    /// `event` — имя сигнала для панели безопасности (см. `fingerprintRelay`); пусто — без сигналов.
    /// `wrapWorkers` — запускать фоновые потоки страницы через загрузчик с защитой (нельзя, если CSP страницы
    /// не разрешает потоки из blob:).
    static func fingerprint(secret: String, blockWebRTC: Bool, spoofTimezone: Bool = false, spoofLanguage: Bool = false, event: String = "", wrapWorkers: Bool = true) -> String {
        fingerprintSource
            .replacingOccurrences(of: "__WARD_WORKERS__", with: wrapWorkers ? "true" : "false")
            .replacingOccurrences(of: "__WARD_SECRET__", with: secret)
            .replacingOccurrences(of: "__WARD_EVENT__", with: event)
            .replacingOccurrences(of: "__WARD_WEBRTC__", with: blockWebRTC ? "true" : "false")
            .replacingOccurrences(of: "__WARD_TZ__", with: spoofTimezone ? "true" : "false")
            .replacingOccurrences(of: "__WARD_LANG__", with: spoofLanguage ? "true" : "false")
    }

    static let fingerprintSource = #"""
    (() => {
      'use strict';
      // Вся защита — одна самодостаточная функция: её же текст уходит в фоновые потоки страницы (Worker),
      // чтобы там отпечаток был тем же, что и в окне. `base` — настоящий адрес скрипта потока (в окне — пусто).
      const ward = function (w, site, base) {
        const SECRET = '__WARD_SECRET__';
        const BLOCK_WEBRTC = __WARD_WEBRTC__;
        const SPOOF_TZ = __WARD_TZ__;
        const SPOOF_LANG = __WARD_LANG__;
        const WRAP_WORKERS = __WARD_WORKERS__;
        // Сигнал для панели безопасности: «такой-то скрипт пытался снять отпечаток таким-то способом».
        // Функции для сигнала взяты до скриптов сайта: подменить их он не успеет.
        const EVENT = '__WARD_EVENT__';
        const signalDoc = w.document;
        const dispatch = w.EventTarget && w.EventTarget.prototype.dispatchEvent;
        const NativeCustomEvent = w.CustomEvent;
        const NativeError = w.Error;
        const signalled = new Set();
        // Чей скрипт вызвал: первый адрес http(s) в стеке вызовов (сами скрипты Ward адреса не имеют).
        const caller = () => {
          try {
            const stack = String(new NativeError().stack || '');
            for (const line of stack.split('\n')) {
              const m = /(https?:\/\/[^\/:\s]+)[^\s]*:\d+:\d+\s*$/.exec(line);
              if (m) return m[1].replace(/^https?:\/\//, '').toLowerCase();
            }
          } catch (e) {}
          return '';
        };
        const signal = (kind) => {
          if (!EVENT || !signalDoc || !dispatch) return;
          const who = caller();
          const key = kind + '|' + who;
          if (signalled.has(key) || signalled.size > 120) return;
          signalled.add(key);
          try { dispatch.call(signalDoc, new NativeCustomEvent(EVENT + '-' + kind, { detail: who })); } catch (e) {}
        };
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
        const nativeToString = w.Function.prototype.toString;
        const masks = new WeakMap();
        const mask = (fn, name) => { masks.set(fn, 'function ' + name + '() {\n    [native code]\n}'); return fn; };
        const toString = { toString() { return masks.has(this) ? masks.get(this) : nativeToString.call(this); } }.toString;
        mask(toString, 'toString');

        // Где на самом деле лежит свойство (у потоков методы живут на прототипах глобального объекта).
        const owner = (obj, key) => {
          for (let o = obj; o; o = Object.getPrototypeOf(o)) if (Object.getOwnPropertyDescriptor(o, key)) return o;
          return null;
        };
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
        // Геттер-замена сначала зовёт настоящий: вызов «не на том объекте» падает той же ошибкой, что у Safari.
        const replaceGetter = (target, key, impl) => {
          if (!target) return;
          const desc = Object.getOwnPropertyDescriptor(target, key);
          if (!desc || typeof desc.get !== 'function') return;
          const original = desc.get;
          const holder = { get [key]() { const value = original.call(this); return impl.call(this, value); } };
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

        // Фоновые потоки страницы (Worker): в них WebKit не вставляет скрипты Ward, поэтому поток запускается
        // через маленький загрузчик — сначала эта же защита, потом настоящий скрипт. Адреса внутри потока
        // считаются от настоящего скрипта, а не от загрузчика. Если загрузчик не разрешён (CSP) — поток как есть.
        // CSP страницы (из <meta>): разрешены ли потоки из blob:. Заголовок CSP проверяет сам Ward.
        const cspAllowsBlobWorker = (policy) => {
          for (const one of String(policy).split(',')) {
            const dirs = {};
            for (const part of one.split(';')) { const t = part.trim().split(/\s+/); if (t[0]) dirs[t[0].toLowerCase()] = t.slice(1); }
            const list = dirs['worker-src'] || dirs['child-src'] || dirs['script-src'] || dirs['default-src'];
            if (list && !list.some((x) => x.toLowerCase() === 'blob:')) return false;
          }
          return true;
        };
        const metaAllowsBlob = (g) => {
          try {
            for (const m of g.document ? g.document.querySelectorAll('meta[http-equiv]') : []) {
              if (/^content-security-policy$/i.test(m.httpEquiv) && !cspAllowsBlobWorker(m.content)) return false;
            }
          } catch (e) {}
          return true;
        };
        const wrapWorkers = (g) => {
          const Native = g.Worker;
          if (!WRAP_WORKERS || !Native || !g.Blob || !g.URL || !g.URL.createObjectURL) return;
          const source = nativeToString.call(ward);
          const Worker = function Worker(url, options) {
            if (!new.target) return Native(url);
            let abs = null;
            try { abs = new g.URL(String(url), String(g.location.href)); } catch (e) {}
            const fresh = (u) => Reflect.construct(Native, [u, options], new.target === Worker ? Native : new.target);
            if (!abs || !/^(https?|blob):$/.test(abs.protocol)) return fresh(url);
            if (abs.protocol !== 'blob:' && abs.origin !== g.location.origin) return fresh(url);
            if (!metaAllowsBlob(g)) return fresh(url);
            const module = !!(options && options.type === 'module');
            let boot;
            try {
              const text = 'const ward = ' + source + ';\nward(self, ' + JSON.stringify(site) + ', ' + JSON.stringify(abs.href) + ');\n'
                + (module ? 'import ' + JSON.stringify(abs.href) + ';' : 'importScripts(' + JSON.stringify(abs.href) + ');');
              boot = g.URL.createObjectURL(new g.Blob([text], { type: 'text/javascript' }));
            } catch (e) { return fresh(url); }
            try {
              const worker = fresh(boot);
              setTimeout(() => { try { g.URL.revokeObjectURL(boot); } catch (e) {} }, 30000);
              return worker;
            } catch (e) { return fresh(url); }
          };
          Object.defineProperty(Worker, 'length', { value: Native.length, configurable: true });
          Object.defineProperty(Worker, 'prototype', { value: Native.prototype, writable: false, enumerable: false, configurable: false });
          mask(Worker, 'Worker');
          try { Object.defineProperty(Native.prototype, 'constructor', { value: Worker, writable: true, enumerable: false, configurable: true }); } catch (e) {}
          Object.setPrototypeOf(Worker, Object.getPrototypeOf(Native));
          const desc = Object.getOwnPropertyDescriptor(g, 'Worker') || { writable: true, enumerable: false, configurable: true };
          Object.defineProperty(g, 'Worker', { value: Worker, writable: desc.writable !== false, enumerable: !!desc.enumerable, configurable: desc.configurable !== false });
        };

        // Внутри потока: адреса — от настоящего скрипта (importScripts, fetch, XHR, location).
        const workerShims = (g) => {
          const real = new g.URL(base);
          const resolve = (u) => {
            if (typeof u !== 'string' && !(u instanceof g.URL)) return u;
            try { return new g.URL(String(u), real.href).href; } catch (e) { return u; }
          };
          const location = Object.create(null);
          for (const key of ['href', 'origin', 'protocol', 'host', 'hostname', 'port', 'pathname', 'search', 'hash']) location[key] = real[key];
          location.toString = mask({ toString() { return real.href; } }.toString, 'toString');
          Object.freeze(location);
          replaceGetter(g.WorkerGlobalScope && g.WorkerGlobalScope.prototype, 'location', function () { return location; });
          const scope = owner(g, 'importScripts');
          replace(scope, 'importScripts', function (original, args) { return original.apply(this, args.map(resolve)); });
          replace(owner(g, 'fetch'), 'fetch', function (original, args) { if (args.length) args[0] = resolve(args[0]); return original.apply(this, args); });
          if (g.XMLHttpRequest) replace(g.XMLHttpRequest.prototype, 'open', function (original, args) { if (args.length > 1) args[1] = resolve(args[1]); return original.apply(this, args); });
        };

        const patched = new WeakSet();
        // Фрейм, где уже работает своя копия защиты (WebKit вставляет скрипты и во фреймы): второй слой
        // шума отменил бы первый (шум — это XOR), поэтому такой фрейм не трогаем.
        const protectedElsewhere = (other) => {
          try {
            const probe = other.HTMLCanvasElement && other.HTMLCanvasElement.prototype.toDataURL;
            return !!probe && !/\[native code\]/.test(nativeToString.call(probe));
          } catch (e) { return false; }
        };
        const patch = (w) => {
          if (!w || patched.has(w)) return;
          patched.add(w);
          if (w !== self && protectedElsewhere(w)) return;
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
            replace(w.HTMLCanvasElement.prototype, 'toDataURL', function (original, args) { signal('canvas'); return original.apply(noisyCopy(this) || this, args); });
            replace(w.HTMLCanvasElement.prototype, 'toBlob', function (original, args) { signal('canvas'); return original.apply(noisyCopy(this) || this, args); });
          }
          const imageDataNoise = function (original, args) {
            // Снимок небольшого холста целиком — типичный отпечаток; большие холсты читают редакторы и игры.
            try { if (this.canvas && this.canvas.width * this.canvas.height <= 90000) signal('canvas'); } catch (e) {}
            const image = original.apply(this, args);
            try { noisePixels(image.data, image.width, image.height); } catch (e) {}
            return image;
          };
          if (w.CanvasRenderingContext2D) replace(w.CanvasRenderingContext2D.prototype, 'getImageData', imageDataNoise);
          if (w.OffscreenCanvasRenderingContext2D) replace(w.OffscreenCanvasRenderingContext2D.prototype, 'getImageData', imageDataNoise);

          // Ширина текста: по ней узнают установленные шрифты. Сдвиг в стотысячные доли пикселя.
          // Двадцать разных шрифтов на одной странице — это уже перебор, а не вёрстка.
          const fontsSeen = new Set();
          const measure = function (original, args) {
            try { if (fontsSeen.size < 20) { fontsSeen.add(String(this.font)); if (fontsSeen.size === 20) signal('fonts'); } } catch (e) {}
            const metrics = original.apply(this, args);
            try {
              const width = metrics.width;
              const shift = ((hash(String(args[0]) + '|' + this.font) ^ seed) % 1000) / 1000 * 0.00004 * Math.max(1, width);
              Object.defineProperty(metrics, 'width', { configurable: true, enumerable: true, value: width + shift });
            } catch (e) {}
            return metrics;
          };
          if (w.CanvasRenderingContext2D) replace(w.CanvasRenderingContext2D.prototype, 'measureText', measure);
          if (w.OffscreenCanvasRenderingContext2D) replace(w.OffscreenCanvasRenderingContext2D.prototype, 'measureText', measure);

          // WebGL: одинаковое имя видеокарты для всех Mac и шум в считанных пикселях.
          for (const ctor of [w.WebGLRenderingContext, w.WebGL2RenderingContext]) {
            if (!ctor) continue;
            replace(ctor.prototype, 'getParameter', function (original, args) {
              if (args[0] === 0x9245) { signal('webgl'); return 'Apple Inc.'; }
              if (args[0] === 0x9246) { signal('webgl'); return 'Apple GPU'; }
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
          // Обработка звука «в уме» (OfflineAudioContext) почти всегда означает отпечаток.
          for (const ctor of [w.OfflineAudioContext, w.webkitOfflineAudioContext]) {
            if (ctor) replace(ctor.prototype, 'startRendering', function (original, args) { signal('audio'); return original.apply(this, args); });
          }
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

          // Число ядер — не больше восьми, как у большинства Mac (и одинаково в окне, фреймах и потоках);
          // сигнал «не продавать мои данные» (Global Privacy Control).
          for (const nav of [w.Navigator, w.WorkerNavigator]) {
            if (!nav) continue;
            replaceGetter(nav.prototype, 'hardwareConcurrency', function (value) { return Math.min(value, 8); });
            defineGetter(nav.prototype, 'globalPrivacyControl', true);
          }

          // Квота хранилища выдаёт размер диска: у всех — не больше 16 ГБ.
          if (w.StorageManager) replace(w.StorageManager.prototype, 'estimate', function (original, args) {
            signal('storage');
            return original.apply(this, args).then((r) => {
              try { if (r && typeof r.quota === 'number' && r.quota > 17179869184) return Object.assign({}, r, { quota: 17179869184 }); } catch (e) {}
              return r;
            });
          });
          // Список камер и микрофонов, перебор разрешений — частые шаги сборщиков отпечатка.
          if (w.MediaDevices) replace(w.MediaDevices.prototype, 'enumerateDevices', function (original, args) { signal('devices'); return original.apply(this, args); });
          const permissionsAsked = new Set();
          if (w.Permissions) replace(w.Permissions.prototype, 'query', function (original, args) {
            try { permissionsAsked.add(String(args[0] && args[0].name)); if (permissionsAsked.size === 4) signal('permissions'); } catch (e) {}
            return original.apply(this, args);
          });

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
            signal('voices');
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
              w.Intl.DateTimeFormat = mask(new Proxy(DTF, {
                construct(target, args) { return Reflect.construct(target, withUTC(args)); },
                apply(target, thisArg, args) { return Reflect.apply(target, thisArg, withUTC(args)); },
              }), 'DateTimeFormat');
            }
            // «2025-01-01T10:00» без пояса — тоже по UTC, как и всё остальное.
            const NativeDate = w.Date;
            const localISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;
            w.Date = mask(new Proxy(NativeDate, {
              construct(target, args) {
                if (args.length >= 2 && args.every((a) => typeof a === 'number')) return new target(NativeDate.UTC(...args));
                if (args.length === 1 && typeof args[0] === 'string' && localISO.test(args[0])) return new target(args[0] + 'Z');
                return Reflect.construct(target, args);
              },
            }), 'Date');
          }

          // Язык (по желанию): английский, как у большинства пользователей в мире.
          if (SPOOF_LANG) {
            for (const nav of [w.Navigator, w.WorkerNavigator]) {
              if (!nav) continue;
              replaceGetter(nav.prototype, 'language', function () { return 'en-US'; });
              replaceGetter(nav.prototype, 'languages', function () { return Object.freeze(['en-US', 'en']); });
            }
          }

          // WebRTC может раскрыть настоящий IP-адрес даже через VPN — по желанию отключается.
          if (BLOCK_WEBRTC) {
            for (const name of ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'RTCSessionDescription', 'RTCIceCandidate']) {
              try { if (name in w) Object.defineProperty(w, name, { configurable: true, writable: true, value: undefined }); } catch (e) {}
            }
          }

          // Фоновые потоки — с той же защитой.
          wrapWorkers(w);

          // Чистые функции часто берут из нового пустого фрейма или окна — защищаем и их.
          for (const ctor of [w.HTMLIFrameElement, w.HTMLFrameElement, w.HTMLObjectElement]) {
            if (!ctor) continue;
            replaceGetter(ctor.prototype, 'contentWindow', function (child) {
              try { if (child) patch(child); } catch (e) {}
              return child;
            });
            replaceGetter(ctor.prototype, 'contentDocument', function (doc) {
              try { if (doc && doc.defaultView) patch(doc.defaultView); } catch (e) {}
              return doc;
            });
          }
          if (w.document) replace(w, 'open', function (original, args) {
            const child = original.apply(this, args);
            try { if (child) patch(child); } catch (e) {}
            return child;
          });
        };
        if (base) workerShims(w);
        patch(w);
      };
      // Сайт — по настоящему источнику: у пустого фрейма (about:blank) адреса нет, но источник — родителя.
      let host = '';
      try { host = self.origin && self.origin !== 'null' ? new URL(self.origin).hostname : (location.hostname || ''); } catch (e) {}
      const labels = host.split('.');
      const site = labels.length > 2 && !/^[0-9.]+$/.test(host) ? labels.slice(-2).join('.') : host;
      ward(window, site, '');
    })();
    """#

    /// Следы, которые страница узнаёт о вас помимо cookie: сигнал «не продавать мои данные» (GPC), адрес,
    /// с которого вы пришли (другому сайту — только сам сайт), «вечные» cookie из скриптов (живут не дольше
    /// `cookieDays` дней) и попытки обратиться к устройствам домашней сети. Мир страницы, все фреймы.
    static func privacy(gpc: Bool, trimReferrer: Bool, cookieDays: Int, lan: Bool, event: String) -> String {
        privacySource
            .replacingOccurrences(of: "__WARD_GPC__", with: gpc ? "true" : "false")
            .replacingOccurrences(of: "__WARD_TRIM__", with: trimReferrer ? "true" : "false")
            .replacingOccurrences(of: "__WARD_COOKIE_DAYS__", with: String(max(0, cookieDays)))
            .replacingOccurrences(of: "__WARD_LAN__", with: lan ? "true" : "false")
            .replacingOccurrences(of: "__WARD_EVENT__", with: event)
    }

    static let privacySource = #"""
    (() => {
      'use strict';
      const GPC = __WARD_GPC__;
      const TRIM = __WARD_TRIM__;
      const COOKIE_DAYS = __WARD_COOKIE_DAYS__;
      const LAN = __WARD_LAN__;
      const EVENT = '__WARD_EVENT__';
      const w = window;
      const doc = document;
      const dispatch = EventTarget.prototype.dispatchEvent;
      const NativeCustomEvent = CustomEvent;
      const NativeURL = URL;
      const parseDate = Date.parse;
      const now = Date.now;
      const sent = new Set();
      const signal = (kind, detail) => {
        if (!EVENT) return;
        const key = kind + '|' + (detail || '');
        if (sent.has(key) || sent.size > 200) return;
        sent.add(key);
        try { dispatch.call(doc, new NativeCustomEvent(EVENT + '-' + kind, { detail: String(detail || '') })); } catch (e) {}
      };

      // Подменённые функции выглядят как встроенные (свой учёт масок: скрипты Ward независимы друг от друга).
      const nativeToString = Function.prototype.toString;
      const masks = new WeakMap();
      const mask = (fn, name) => { masks.set(fn, 'function ' + name + '() {\n    [native code]\n}'); return fn; };
      const toString = { toString() { return masks.has(this) ? masks.get(this) : nativeToString.call(this); } }.toString;
      mask(toString, 'toString');
      try { Object.defineProperty(Function.prototype, 'toString', { configurable: true, enumerable: false, writable: true, value: toString }); } catch (e) {}
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
      const replaceAccessor = (target, key, getImpl, setImpl) => {
        if (!target) return;
        const desc = Object.getOwnPropertyDescriptor(target, key);
        if (!desc || typeof desc.get !== 'function') return;
        const get = desc.get, set = desc.set;
        const holder = {
          get [key]() { const value = get.call(this); return getImpl ? getImpl.call(this, value) : value; },
          set [key](value) { return set.call(this, setImpl ? setImpl.call(this, value) : value); },
        };
        const d = Object.getOwnPropertyDescriptor(holder, key);
        mask(d.get, 'get ' + key);
        mask(d.set, 'set ' + key);
        Object.defineProperty(target, key, { configurable: desc.configurable, enumerable: desc.enumerable, get: getImpl ? d.get : get, set: set ? (setImpl ? d.set : set) : undefined });
      };
      const defineGetter = (target, key, value) => {
        if (!target || key in target) return;
        const getter = Object.getOwnPropertyDescriptor({ get [key]() { return value; } }, key).get;
        mask(getter, 'get ' + key);
        Object.defineProperty(target, key, { configurable: true, enumerable: true, get: getter });
      };
      const siteOf = (host) => {
        host = String(host || '').toLowerCase();
        if (/^[0-9.]+$/.test(host) || host.includes(':')) return host;
        const labels = host.split('.');
        const second = labels.length > 2 ? labels[labels.length - 2] : '';
        // co.uk, com.ru и подобные: сайт — это три последние части.
        if (labels.length > 2 && second.length <= 3 && /^(co|com|org|net|gov|edu|ac|msk|spb)$/.test(second)) return labels.slice(-3).join('.');
        return labels.slice(-2).join('.');
      };
      let here = '';
      let originHost = '';
      try { originHost = self.origin && self.origin !== 'null' ? new NativeURL(self.origin).hostname : location.hostname; here = siteOf(originHost); } catch (e) {}

      // «Не продавать мои данные» — Global Privacy Control.
      if (GPC && w.Navigator) defineGetter(w.Navigator.prototype, 'globalPrivacyControl', true);

      // Адрес, с которого пришли: другому сайту — только сам сайт, без страницы и параметров.
      const STRICT = 'strict-origin-when-cross-origin';
      const WEAK = /^\s*(unsafe-url|no-referrer-when-downgrade|origin-when-cross-origin)\s*$/i;
      if (TRIM) {
        replaceAccessor(w.Document && w.Document.prototype, 'referrer', function (value) {
          if (!value) return value;
          try {
            const u = new NativeURL(value);
            if (siteOf(u.hostname) !== here) { signal('referrer'); return u.origin + '/'; }
          } catch (e) {}
          return value;
        });
        const strict = (v) => (WEAK.test(String(v)) ? STRICT : v);
        for (const name of ['HTMLAnchorElement', 'HTMLAreaElement', 'HTMLIFrameElement', 'HTMLImageElement', 'HTMLLinkElement', 'HTMLScriptElement']) {
          if (w[name]) replaceAccessor(w[name].prototype, 'referrerPolicy', null, strict);
        }
        // Политика из разметки: <meta name="referrer" content="unsafe-url"> и атрибут referrerpolicy.
        const fix = (el) => {
          if (!el || el.nodeType !== 1) return;
          if (el.tagName === 'META' && /^referrer$/i.test(el.getAttribute('name') || '')) {
            if (WEAK.test(el.getAttribute('content') || '')) el.setAttribute('content', STRICT);
          } else if (el.hasAttribute('referrerpolicy') && WEAK.test(el.getAttribute('referrerpolicy'))) {
            el.setAttribute('referrerpolicy', STRICT);
          }
        };
        try {
          new MutationObserver((list) => {
            for (const m of list) {
              if (m.type === 'attributes') fix(m.target);
              else for (const node of m.addedNodes) {
                if (node.nodeType !== 1) continue;
                fix(node);
                if (node.querySelectorAll) node.querySelectorAll('meta[name], [referrerpolicy]').forEach(fix);
              }
            }
          }).observe(doc, { subtree: true, childList: true, attributes: true, attributeFilter: ['referrerpolicy', 'content'] });
        } catch (e) {}
      }

      // Устройства домашней сети: роутер, принтер, NAS, сервисы на этом Mac.
      const LOCAL = /^(localhost|.+\.localhost|.+\.local|127(\.\d+){3}|10(\.\d+){3}|192\.168(\.\d+){2}|169\.254(\.\d+){2}|172\.(1[6-9]|2\d|3[01])(\.\d+){2}|0\.0\.0\.0|\[(::1|f[cd][0-9a-f:]*|fe80:[0-9a-f:]*)\])$/i;
      let pageLocal = true;
      try { pageLocal = !originHost || LOCAL.test(originHost) || !/^https?:/.test(self.origin); } catch (e) {}
      const lanCheck = (u) => {
        if (!LAN || pageLocal) return;
        try {
          const x = new NativeURL(String(u), location.href);
          if (LOCAL.test(x.hostname)) signal('lan', x.host);
        } catch (e) {}
      };

      // fetch: усилить политику Referer и заметить обращения в домашнюю сеть.
      replace(w, 'fetch', function (original, args) {
        try {
          const input = args[0];
          lanCheck(typeof input === 'string' || input instanceof NativeURL ? input : input && input.url);
          if (TRIM && args[1] && WEAK.test(args[1].referrerPolicy || '')) args[1] = Object.assign({}, args[1], { referrerPolicy: STRICT });
        } catch (e) {}
        return original.apply(this, args);
      });
      if (LAN && w.XMLHttpRequest) replace(w.XMLHttpRequest.prototype, 'open', function (original, args) { lanCheck(args[1]); return original.apply(this, args); });
      if (LAN && w.navigator && w.Navigator) replace(w.Navigator.prototype, 'sendBeacon', function (original, args) { lanCheck(args[0]); return original.apply(this, args); });

      // Cookie из скриптов страницы живут не дольше COOKIE_DAYS дней (как в Safari): «вечный» идентификатор
      // в cookie превращается в недельный. Cookie входа ставит сервер — их это не касается.
      if (COOKIE_DAYS > 0) {
        const MAX = COOKIE_DAYS * 86400;
        const cap = (value) => {
          let text = String(value);
          let capped = false;
          text = text.replace(/;\s*max-age\s*=\s*(\d+)/i, (all, n) => (Number(n) > MAX ? (capped = true, '; max-age=' + MAX) : all));
          text = text.replace(/;\s*expires\s*=\s*([^;]+)/i, (all, d) => {
            const t = parseDate(d);
            if (!isNaN(t) && t - now() > MAX * 1000) { capped = true; return '; expires=' + new Date(now() + MAX * 1000).toUTCString(); }
            return all;
          });
          if (capped) signal('cookie', text.split('=')[0].trim().slice(0, 60));
          return text;
        };
        replaceAccessor(w.Document && w.Document.prototype, 'cookie', null, cap);
        if (w.CookieStore) replace(w.CookieStore.prototype, 'set', function (original, args) {
          try {
            const o = args[0];
            if (o && typeof o === 'object' && typeof o.expires === 'number' && o.expires - now() > MAX * 1000) {
              args[0] = Object.assign({}, o, { expires: now() + MAX * 1000 });
              signal('cookie', String(o.name || '').slice(0, 60));
            }
          } catch (e) {}
          return original.apply(this, args);
        });
      }
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
      // Вы сами что-то нажали на странице: сайт «настоящий» для вас (а не промежуточный трекер-«отскок»).
      if (window === window.top) {
        let lastActivation = 0;
        const activation = (e) => {
          if (!e.isTrusted || Date.now() - lastActivation < 20000) return;
          lastActivation = Date.now();
          try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'activation' }); } catch (err) {}
        };
        document.addEventListener('pointerdown', activation, true);
        document.addEventListener('keydown', activation, true);
      }
      // Правый щелчок по ссылке — для пункта «Скопировать чистую ссылку».
      document.addEventListener('contextmenu', (e) => {
        const a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
        try { window.webkit.messageHandlers.wardEvent.postMessage({ type: 'context-link', href: a ? a.href : '' }); } catch (err) {}
      }, true);
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
