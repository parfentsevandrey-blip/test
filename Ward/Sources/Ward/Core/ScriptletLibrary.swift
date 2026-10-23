import Foundation

extension Scripts {
    /// Скриптлеты для страницы: библиотека Ward и вызовы из списков для этого сайта.
    /// `entries` — записи индекса «1[…]»/«0[…]» (доверенный список или нет).
    nonisolated static func scriptlets(_ entries: [String]) -> String? {
        guard !entries.isEmpty else { return nil }
        let calls = entries.compactMap { entry -> String? in
            guard let first = entry.first, entry.count > 2 else { return nil }
            return "[\(first == "1" ? "true" : "false")," + entry.dropFirst(2)
        }
        return scriptletLibrary.replacingOccurrences(of: "__WARD_CALLS__", with: "[" + calls.joined(separator: ",") + "]")
    }

    /// Библиотека скриптлетов — собственная реализация поведения скриптлетов uBlock Origin.
    nonisolated static let scriptletLibrary = #"""
    (() => {
      'use strict';
      const CALLS = __WARD_CALLS__;
      if (!CALLS.length) return;
      const W = window;
      const defP = Object.defineProperty;
      const gOPD = Object.getOwnPropertyDescriptor;
      const nativeToString = Function.prototype.toString;
      const magic = String.fromCharCode(Date.now() % 26 + 97) + Math.floor(Math.random() * 982451653 + 982451653).toString(36);
      // Ошибки, которые бросают скриптлеты, не должны сыпаться в консоль сайта.
      W.addEventListener('error', (e) => {
        if (typeof e.message === 'string' && e.message.includes(magic)) { e.preventDefault(); e.stopImmediatePropagation(); }
      }, true);

      const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const isRegexText = (s) => /^\/.+\/[dgimsuy]*$/.test(s);
      const toRegex = (pattern, flags) => {
        if (pattern === undefined || pattern === '' || pattern === '*') return /^/;
        const m = /^\/(.+)\/([dgimsuy]*)$/.exec(pattern);
        if (m) { try { return new RegExp(m[1], flags ?? m[2]); } catch (e) { return /^\b\B$/; } }
        return new RegExp(escapeRe(pattern), flags);
      };
      const matcher = (pattern) => {
        let not = false;
        if (typeof pattern === 'string' && pattern.startsWith('!')) { not = true; pattern = pattern.slice(1); }
        const re = toRegex(pattern);
        return (text) => { re.lastIndex = 0; return re.test(text) !== not; };
      };
      const fnText = (f) => {
        try { return typeof f === 'function' ? nativeToString.call(f) : String(f); } catch (e) { return ''; }
      };
      const onDOM = (fn) => {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn, { once: true }); else fn();
      };
      const observe = (fn, attrs) => {
        let timer = null;
        const mo = new MutationObserver(() => { if (!timer) timer = setTimeout(() => { timer = null; fn(); }, 40); });
        const start = () => {
          const options = { childList: true, subtree: true };
          if (attrs && attrs.length) { options.attributes = true; options.attributeFilter = attrs; }
          mo.observe(document.documentElement || document, options);
        };
        if (document.documentElement) start(); else document.addEventListener('readystatechange', start, { once: true });
      };
      const randomText = () => Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
      const extraArgs = (list) => { const o = {}; for (let i = 0; i + 1 < list.length; i += 2) o[list[i]] = list[i + 1]; return o; };

      // «prop:значение» через пробел; без имени — адрес.
      const parseProps = (pattern) => {
        const props = new Map();
        if (!pattern || pattern === '*') return props;
        if (isRegexText(pattern)) { props.set('url', toRegex(pattern)); return props; }
        for (const part of pattern.split(/\s+/).filter(Boolean)) {
          const i = part.indexOf(':');
          if (i > 0 && /^[a-zA-Z]+$/.test(part.slice(0, i)) && !/^https?$/.test(part.slice(0, i))) props.set(part.slice(0, i), toRegex(part.slice(i + 1)));
          else props.set('url', toRegex(part));
        }
        return props;
      };
      const propsMatch = (props, details) => {
        for (const [k, re] of props) {
          const v = details[k];
          re.lastIndex = 0;
          if (v === undefined || !re.test(String(v))) return false;
        }
        return true;
      };
      const fetchDetails = (args) => {
        const [resource, init] = args;
        const d = {};
        if (resource instanceof Request) { d.url = resource.url; d.method = resource.method; d.mode = resource.mode; d.credentials = resource.credentials; }
        else d.url = String(resource);
        if (init && typeof init === 'object') {
          for (const k of ['method', 'body', 'mode', 'credentials', 'headers', 'referrer', 'cache']) if (init[k] !== undefined) d[k] = k === 'body' ? String(init[k]) : init[k];
        }
        if (!d.method) d.method = 'GET';
        return d;
      };
      const rebuildResponse = (resp, text) => {
        const out = new Response(text, { status: resp.status, statusText: resp.statusText, headers: resp.headers });
        try {
          defP(out, 'url', { value: resp.url });
          defP(out, 'type', { value: resp.type });
          defP(out, 'redirected', { value: resp.redirected });
        } catch (e) {}
        return out;
      };

      // Цепочка свойств: ловушка ставится и на ещё не созданные объекты по пути.
      const trapChain = (owner, chain, leaf) => {
        const pos = chain.indexOf('.');
        if (pos === -1) { leaf(owner, chain); return; }
        const prop = chain.slice(0, pos);
        const rest = chain.slice(pos + 1);
        let v = owner[prop];
        if (v instanceof Object || (typeof v === 'object' && v !== null)) { trapChain(v, rest, leaf); return; }
        const desc = gOPD(owner, prop);
        if (desc && desc.set !== undefined) return;
        try {
          defP(owner, prop, {
            configurable: true,
            get() { return v; },
            set(a) { v = a; if (a instanceof Object) trapChain(a, rest, leaf); },
          });
        } catch (e) {}
      };

      const constantValue = (raw, trusted) => {
        switch (raw) {
          case 'undefined': return { v: undefined };
          case 'false': return { v: false };
          case 'true': return { v: true };
          case 'null': return { v: null };
          case "''": case '""': case '': case 'emptyStr': return { v: '' };
          case 'noopFunc': return { v: function () {} };
          case 'trueFunc': return { v: function () { return true; } };
          case 'falseFunc': return { v: function () { return false; } };
          case 'throwFunc': return { v: function () { throw new ReferenceError(magic); } };
          case 'emptyArr': case '[]': return { v: [] };
          case 'emptyObj': case '{}': return { v: {} };
          case 'noopCallbackFunc': return { v: function () { return function () {}; } };
          case 'noopPromiseResolve': return { v: function () { return Promise.resolve(); } };
          case 'noopPromiseReject': return { v: function () { return Promise.reject(); } };
          case 'yes': case 'no': case 'on': case 'off': return { v: raw };
        }
        if (/^-?\d+$/.test(raw)) {
          const n = parseInt(raw, 10);
          if (Math.abs(n) <= 0x7FFF || trusted) return { v: n };
          return null;
        }
        if (trusted) { try { return { v: JSON.parse(raw) }; } catch (e) { return { v: raw }; } }
        return null;
      };

      const setConstant = (trusted, chain, raw) => {
        if (!chain) return;
        const parsed = constantValue(raw ?? '', trusted);
        if (!parsed) return;
        let value = parsed.v;
        let aborted = false;
        const mustAbort = (v) => {
          if (trusted) return false;
          if (aborted) return true;
          aborted = v !== undefined && v !== null && value !== undefined && value !== null && typeof v !== typeof value;
          return aborted;
        };
        trapChain(W, chain, (owner, prop) => {
          const current = owner[prop];
          if (mustAbort(current)) return;
          const desc = gOPD(owner, prop);
          let prevGet, prevSet;
          if (desc instanceof Object) {
            if (desc.configurable === false) { try { owner[prop] = value; } catch (e) {} return; }
            if (typeof desc.get === 'function') prevGet = desc.get;
            if (typeof desc.set === 'function') prevSet = desc.set;
          }
          try {
            defP(owner, prop, {
              configurable: false,
              get() { if (prevGet) { try { prevGet.call(this); } catch (e) {} } return value; },
              set(a) { if (prevSet) { try { prevSet.call(this, a); } catch (e) {} } if (mustAbort(a)) value = a; },
            });
          } catch (e) {}
        });
      };

      const abortOnRead = (chain) => {
        if (!chain) return;
        const getter = function () { throw new ReferenceError(magic); };
        trapChain(W, chain, (owner, prop) => {
          const desc = gOPD(owner, prop);
          if (desc && desc.get === getter) return;
          try { defP(owner, prop, { get: getter, set() {} }); } catch (e) {}
        });
      };

      const abortOnWrite = (chain) => {
        if (!chain) return;
        trapChain(W, chain, (owner, prop) => {
          try { delete owner[prop]; } catch (e) {}
          try { defP(owner, prop, { configurable: true, set() { throw new ReferenceError(magic); } }); } catch (e) {}
        });
      };

      const abortOnStackTrace = (chain, needle) => {
        if (!chain) return;
        const test = matcher(needle);
        trapChain(W, chain, (owner, prop) => {
          let v = owner[prop];
          const check = () => { if (test(new Error().stack || '')) throw new ReferenceError(magic); };
          try { defP(owner, prop, { configurable: true, get() { check(); return v; }, set(a) { check(); v = a; } }); } catch (e) {}
        });
      };

      const abortCurrentScript = (target, needle = '', context = '') => {
        if (!target) return;
        const test = needle ? matcher(needle) : null;
        const contextTest = context ? matcher(context) : null;
        const chain = target.split('.');
        let owner = W;
        let prop;
        for (;;) {
          prop = chain.shift();
          if (chain.length === 0) break;
          if (!(prop in owner)) break;
          owner = owner[prop];
          if (!(owner instanceof Object)) return;
        }
        let value;
        let desc = gOPD(owner, prop);
        if (!(desc instanceof Object) || typeof desc.get !== 'function') { value = owner[prop]; desc = undefined; }
        const validate = () => {
          const e = document.currentScript;
          if (!(e instanceof HTMLScriptElement)) return;
          if (contextTest && !contextTest(e.src)) return;
          if (test && !test(e.textContent || e.src || '')) return;
          throw new ReferenceError(magic);
        };
        try {
          defP(owner, prop, {
            configurable: true,
            get() { validate(); return desc ? desc.get.call(owner) : value; },
            set(a) { validate(); if (desc && desc.set) desc.set.call(owner, a); else value = a; },
          });
        } catch (e) {}
      };

      const timerDefuser = (name, needle = '', delay = '') => {
        const test = matcher(needle);
        let delayNot = false;
        let delayValue;
        if (delay !== '') {
          if (delay.startsWith('!')) { delayNot = true; delay = delay.slice(1); }
          delayValue = parseInt(delay, 10);
        }
        W[name] = new Proxy(W[name], {
          apply(target, thisArg, args) {
            const code = fnText(args[0]);
            const time = args[1];
            const delayOK = delayValue === undefined || isNaN(delayValue) || ((time === delayValue) !== delayNot);
            if (test(code) && delayOK) args[0] = function () {};
            return Reflect.apply(target, thisArg, args);
          },
        });
      };

      const booster = (name, needle = '', delay = '1000', boost = '0.05') => {
        const test = matcher(needle === '*' ? '' : needle);
        let d = delay === '*' ? -1 : parseInt(delay, 10);
        if (isNaN(d)) d = 1000;
        let b = parseFloat(boost);
        if (isNaN(b) || !isFinite(b)) b = 0.05;
        b = Math.min(50, Math.max(0.001, b));
        W[name] = new Proxy(W[name], {
          apply(target, thisArg, args) {
            const [fn, time] = args;
            if ((d === -1 || time === d) && test(fnText(fn))) args[1] = (time || 0) * b;
            return Reflect.apply(target, thisArg, args);
          },
        });
      };

      const addEventListenerDefuser = (type = '', pattern = '') => {
        const typeTest = matcher(type);
        const handlerTest = matcher(pattern);
        const proto = EventTarget.prototype;
        proto.addEventListener = new Proxy(proto.addEventListener, {
          apply(target, thisArg, args) {
            let t = '';
            let h = '';
            try {
              t = String(args[0]);
              const handler = args[1];
              h = fnText(handler && typeof handler === 'object' ? handler.handleEvent : handler);
            } catch (e) {}
            if (typeTest(t) && handlerTest(h)) return undefined;
            return Reflect.apply(target, thisArg, args);
          },
        });
      };

      const noWindowOpen = (pattern = '') => {
        const test = matcher(pattern);
        W.open = new Proxy(W.open, {
          apply(target, thisArg, args) {
            const url = String(args[0] ?? '');
            if (!test(url)) return Reflect.apply(target, thisArg, args);
            const decoy = new Proxy({}, {
              get(t, p) {
                if (p === 'closed') return false;
                if (p === 'window' || p === 'self' || p === 'opener') return decoy;
                if (p === 'location') return { href: url, assign() {}, replace() {} };
                return function () {};
              },
              set() { return true; },
            });
            return decoy;
          },
        });
      };

      const removeAttr = (rawAttrs = '', selector = '', behavior = '') => {
        const attrs = rawAttrs.split(/\s*\|\s*/).filter(Boolean);
        if (!attrs.length) return;
        const sel = selector || attrs.map((a) => `[${CSS.escape(a)}]`).join(',');
        const run = () => {
          try { document.querySelectorAll(sel).forEach((el) => attrs.forEach((a) => el.removeAttribute(a))); } catch (e) {}
        };
        if (behavior.includes('asap')) run();
        onDOM(run);
        observe(run, attrs);
      };

      const removeClass = (rawClasses = '', selector = '', behavior = '') => {
        const classes = rawClasses.split(/\s*\|\s*/).filter(Boolean);
        if (!classes.length) return;
        const sel = selector || classes.map((c) => '.' + CSS.escape(c)).join(',');
        const run = () => {
          try { document.querySelectorAll(sel).forEach((el) => el.classList.remove(...classes)); } catch (e) {}
        };
        if (behavior.includes('asap')) run();
        onDOM(run);
        observe(run, ['class']);
      };

      const setAttr = (trusted, selector = '', attr = '', value = '') => {
        if (!selector || !attr) return;
        let copyFrom = null;
        if (/^\[.+\]$/.test(value)) copyFrom = value.slice(1, -1);
        else if (!trusted && !/^(|true|false|-?\d{1,5}|on|off|yes|no)$/.test(value)) return;
        const run = () => {
          try {
            document.querySelectorAll(selector).forEach((el) => {
              const v = copyFrom ? el.getAttribute(copyFrom) : value;
              if (v !== null && el.getAttribute(attr) !== v) el.setAttribute(attr, v);
            });
          } catch (e) {}
        };
        onDOM(run);
        observe(run, [attr]);
      };

      // Текст узлов (обычно — встроенных скриптов) до их выполнения.
      const nodeText = (nodeName = '', pattern = '', replacement, extra) => {
        if (!nodeName || !pattern) return;
        const replace = replacement !== undefined;
        const re = isRegexText(pattern) ? toRegex(pattern) : new RegExp(escapeRe(pattern), replace ? '' : undefined);
        const options = extraArgs(extra || []);
        const condition = options.condition ? toRegex(options.condition) : null;
        const name = nodeName.toUpperCase();
        const handle = (node) => {
          if (node.nodeName !== name) return;
          const text = node.textContent;
          re.lastIndex = 0;
          if (!re.test(text)) return;
          if (condition) { condition.lastIndex = 0; if (!condition.test(text)) return; }
          re.lastIndex = 0;
          if (replace) node.textContent = text.replace(re, replacement);
          else if (name === 'SCRIPT') node.textContent = '';
          else node.remove();
        };
        const scan = (root) => {
          if (name === '#TEXT') {
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            for (let n = walker.nextNode(); n; n = walker.nextNode()) handle(n);
          } else if (root.querySelectorAll) {
            root.querySelectorAll(nodeName).forEach(handle);
          }
        };
        new MutationObserver((mutations) => {
          for (const m of mutations) {
            for (const n of m.addedNodes) {
              handle(n);
              if (n.nodeType === 1) scan(n);
            }
          }
        }).observe(document, { childList: true, subtree: true });
        if (document.documentElement) scan(document.documentElement);
      };

      const noEval = (needle) => {
        const test = needle === undefined ? null : matcher(needle);
        W.eval = new Proxy(W.eval, {
          apply(target, thisArg, args) {
            if (!test || test(String(args[0]))) return undefined;
            return Reflect.apply(target, thisArg, args);
          },
        });
      };

      const noWebRTC = () => {
        const Fake = function () {
          return {
            close() {}, createDataChannel() {}, createOffer() { return Promise.reject(new DOMException('', 'NotAllowedError')); },
            setLocalDescription() { return Promise.resolve(); }, setRemoteDescription() { return Promise.resolve(); },
            addEventListener() {}, removeEventListener() {},
          };
        };
        for (const name of ['RTCPeerConnection', 'webkitRTCPeerConnection']) if (W[name]) W[name] = Fake;
      };

      const fuckAdBlockDefuser = () => {
        const Fab = function () {};
        Fab.prototype = {
          check() {}, clearEvent() {}, emitEvent() {}, on() { return this; },
          onDetected() { return this; }, onNotDetected(fn) { try { fn(); } catch (e) {} return this; }, setOption() {},
        };
        const instance = new Fab();
        for (const name of ['FuckAdBlock', 'BlockAdBlock', 'SniffAdBlock']) { try { defP(W, name, { value: Fab }); } catch (e) {} }
        for (const name of ['fuckAdBlock', 'blockAdBlock', 'sniffAdBlock']) { try { defP(W, name, { value: instance }); } catch (e) {} }
      };

      const babDefuser = () => {
        const test = /babasbm|blockadblock/i;
        for (const name of ['setTimeout', 'setInterval']) {
          W[name] = new Proxy(W[name], { apply(t, self, args) { if (test.test(fnText(args[0]))) args[0] = function () {}; return Reflect.apply(t, self, args); } });
        }
        W.eval = new Proxy(W.eval, { apply(t, self, args) { if (test.test(String(args[0]))) return undefined; return Reflect.apply(t, self, args); } });
      };

      // XMLHttpRequest: запоминаем адрес запроса и переписываем ответ, если правило просит.
      const xhrInfo = new WeakMap();
      const xhrTransforms = [];
      let xhrHooked = false;
      const hookXHR = () => {
        if (xhrHooked) return;
        xhrHooked = true;
        const X = XMLHttpRequest.prototype;
        X.open = new Proxy(X.open, {
          apply(target, xhr, args) {
            xhrInfo.set(xhr, { method: String(args[0]), url: String(args[1]) });
            return Reflect.apply(target, xhr, args);
          },
        });
        for (const prop of ['responseText', 'response']) {
          const desc = gOPD(X, prop);
          if (!desc || typeof desc.get !== 'function') continue;
          const get = desc.get;
          const getter = new Proxy(get, {
            apply(target, xhr, args) {
              const value = Reflect.apply(target, xhr, args);
              const info = xhrInfo.get(xhr);
              if (!info || xhr.readyState !== 4 || !xhrTransforms.length) return value;
              const key = 'c_' + prop;
              if (key in info) return info[key];
              let out = value;
              for (const tr of xhrTransforms) {
                if (!propsMatch(tr.props, info)) continue;
                try { out = tr.apply(out, xhr.responseType); } catch (e) {}
              }
              info[key] = out;
              return out;
            },
          });
          defP(X, prop, { configurable: true, enumerable: desc.enumerable, get: getter });
        }
      };

      const noXhr = (trusted, pattern = '', response = '') => {
        if (pattern === '') return;
        hookXHR();
        const props = parseProps(pattern);
        const X = XMLHttpRequest.prototype;
        X.send = new Proxy(X.send, {
          apply(target, xhr, args) {
            const info = xhrInfo.get(xhr);
            if (!info || !propsMatch(props, info)) return Reflect.apply(target, xhr, args);
            let body = '';
            if (response === 'true') body = randomText();
            else if (response === 'emptyObj') body = '{}';
            else if (response === 'emptyArr') body = '[]';
            else if (trusted) body = response;
            const set = (k, v) => { try { defP(xhr, k, { value: v, configurable: true }); } catch (e) {} };
            set('readyState', 4); set('status', 200); set('statusText', 'OK'); set('responseURL', info.url);
            set('responseText', body);
            let parsed = body;
            if (xhr.responseType === 'json') { try { parsed = JSON.parse(body); } catch (e) { parsed = null; } }
            set('response', parsed);
            setTimeout(() => { for (const t of ['readystatechange', 'load', 'loadend']) xhr.dispatchEvent(new Event(t)); }, 1);
            return undefined;
          },
        });
      };

      const noFetch = (trusted, pattern = '', body = '') => {
        if (pattern === '') return;
        const props = parseProps(pattern);
        W.fetch = new Proxy(W.fetch, {
          apply(target, thisArg, args) {
            let details;
            try { details = fetchDetails(args); } catch (e) { return Reflect.apply(target, thisArg, args); }
            if (!propsMatch(props, details)) return Reflect.apply(target, thisArg, args);
            let text = '';
            if (body === 'emptyObj') text = '{}';
            else if (body === 'emptyArr') text = '[]';
            else if (body === 'true') text = randomText();
            else if (trusted) text = body;
            const resp = new Response(text, { status: 200, statusText: 'OK' });
            try { defP(resp, 'url', { value: details.url }); } catch (e) {}
            return Promise.resolve(resp);
          },
        });
      };

      const STORAGE_VALUES = new Set(['undefined', 'false', 'true', 'null', 'on', 'off', 'yes', 'no', 'accept', 'accepted', 'reject', 'rejected', 'emptyObj', 'emptyArr', '', '$remove$']);
      const setStorage = (trusted, which, key = '', value = '') => {
        if (!key) return;
        let store;
        try { store = W[which]; } catch (e) { return; }
        if (!store) return;
        if (value === '$remove$') {
          try {
            if (isRegexText(key)) { const re = toRegex(key); for (const k of Object.keys(store)) if (re.test(k)) store.removeItem(k); }
            else store.removeItem(key);
          } catch (e) {}
          return;
        }
        let v;
        if (STORAGE_VALUES.has(value)) v = value === 'emptyObj' ? '{}' : value === 'emptyArr' ? '[]' : value === 'undefined' ? undefined : value;
        else if (/^\d+$/.test(value) && Number(value) <= 32767) v = value;
        else if (trusted) v = value === '$now$' ? String(Date.now()) : value === '$currentDate$' ? String(new Date()) : value;
        else return;
        try { if (v === undefined) store.removeItem(key); else store.setItem(key, v); } catch (e) {}
      };

      const COOKIE_VALUES = /^(true|false|yes|y|no|n|ok|on|off|accept|accepted|notaccepted|reject|rejected|allow|allowed|disallow|deny|denied|hide|hidden|dismiss|dismissed|necessary|required|essential|-?\d{1,5}|)$/i;
      const setCookie = (trusted, name = '', value = '', path = '') => {
        if (!name || /[;=\s]/.test(name)) return;
        if (!trusted && !COOKIE_VALUES.test(value)) return;
        if (value === '$now$') value = String(Date.now());
        let cookie = `${name}=${encodeURIComponent(value)}`;
        cookie += path === 'none' ? '' : `; path=${path === '' || path === '/' ? '/' : '/'}`;
        try { document.cookie = cookie; } catch (e) {}
      };

      const removeCookie = (needle = '') => {
        const test = matcher(needle);
        const run = () => {
          let cookies = '';
          try { cookies = document.cookie; } catch (e) { return; }
          for (const part of cookies.split(';')) {
            const name = part.split('=')[0].trim();
            if (!name || !test(name)) continue;
            const host = location.hostname;
            const labels = host.split('.');
            const domains = ['', host];
            for (let i = 1; i < labels.length - 1; i++) domains.push('.' + labels.slice(i).join('.'));
            for (const d of domains) {
              for (const p of ['/', location.pathname]) {
                try { document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=${p}` + (d ? `; domain=${d}` : ''); } catch (e) {}
              }
            }
          }
        };
        run();
        onDOM(run);
        W.addEventListener('beforeunload', run);
      };

      const hrefSanitizer = (selector = '', source = 'text') => {
        if (!selector) return;
        const run = () => {
          let links;
          try { links = document.querySelectorAll(selector); } catch (e) { return; }
          for (const a of links) {
            let v = '';
            if (source === 'text') v = (a.textContent || '').trim();
            else if (/^\[.+\]$/.test(source)) v = a.getAttribute(source.slice(1, -1)) || '';
            else if (source.startsWith('?')) { try { v = new URL(a.href, location.href).searchParams.get(source.slice(1)) || ''; } catch (e) {} }
            try {
              const u = new URL(v);
              if ((u.protocol === 'http:' || u.protocol === 'https:') && a.href !== u.href) a.setAttribute('href', u.href);
            } catch (e) {}
          }
        };
        onDOM(run);
        observe(run);
      };

      // Разбор JSON: убрать рекламные поля (playerAds, adPlacements…) до того, как их увидит сайт.
      const hasPath = (obj, path) => {
        if (obj === null || typeof obj !== 'object') return false;
        const segs = path.split('.');
        const walk = (o, i) => {
          if (i === segs.length) return true;
          if (o === null || typeof o !== 'object') return false;
          const s = segs[i];
          if (s === '[]' || s === '*' || s === '[-]' || s === '{-}') return Object.keys(o).some((k) => walk(o[k], i + 1));
          return s in o && walk(o[s], i + 1);
        };
        return walk(obj, 0);
      };
      const prunePath = (owner, segs) => {
        if (owner === null || typeof owner !== 'object' || !segs.length) return false;
        const [seg, ...rest] = segs;
        if (!rest.length) {
          if (seg === '*' || seg === '[]') { const keys = Object.keys(owner); keys.forEach((k) => { delete owner[k]; }); return keys.length > 0; }
          if (Object.prototype.hasOwnProperty.call(owner, seg)) { try { delete owner[seg]; } catch (e) {} return true; }
          return false;
        }
        if (seg === '[]' || seg === '*') { let any = false; for (const k of Object.keys(owner)) if (prunePath(owner[k], rest)) any = true; return any; }
        if (seg === '[-]') {
          if (!Array.isArray(owner)) return false;
          let any = false;
          for (let i = owner.length - 1; i >= 0; i--) if (hasPath(owner[i], rest.join('.'))) { owner.splice(i, 1); any = true; }
          return any;
        }
        if (seg === '{-}') {
          let any = false;
          for (const k of Object.keys(owner)) if (hasPath(owner[k], rest.join('.'))) { delete owner[k]; any = true; }
          return any;
        }
        return prunePath(owner[seg], rest);
      };
      const objectPrune = (obj, prune, needles) => {
        if (obj === null || typeof obj !== 'object') return false;
        if (needles.length && !needles.every((p) => hasPath(obj, p))) return false;
        let pruned = false;
        for (const p of prune) if (prunePath(obj, p.split('.'))) pruned = true;
        return pruned;
      };
      const splitPaths = (raw) => (raw || '').split(/ +/).filter(Boolean);

      const jsonPrune = (rawPrune = '', rawNeedle = '', stackNeedle = '') => {
        const prune = splitPaths(rawPrune);
        const needles = splitPaths(rawNeedle);
        if (!prune.length) return;
        const stackTest = stackNeedle ? matcher(stackNeedle) : null;
        JSON.parse = new Proxy(JSON.parse, {
          apply(target, thisArg, args) {
            const result = Reflect.apply(target, thisArg, args);
            if (stackTest && !stackTest(new Error().stack || '')) return result;
            objectPrune(result, prune, needles);
            return result;
          },
        });
        Response.prototype.json = new Proxy(Response.prototype.json, {
          apply(target, thisArg, args) {
            return Reflect.apply(target, thisArg, args).then((o) => { objectPrune(o, prune, needles); return o; });
          },
        });
      };

      const fetchResponseRewriter = (props, rewrite) => {
        W.fetch = new Proxy(W.fetch, {
          apply(target, thisArg, args) {
            const promise = Reflect.apply(target, thisArg, args);
            let details;
            try { details = fetchDetails(args); } catch (e) { return promise; }
            if (!propsMatch(props, details)) return promise;
            return promise.then((resp) => resp.clone().text().then((text) => {
              const out = rewrite(text);
              return out === null || out === text ? resp : rebuildResponse(resp, out);
            }).catch(() => resp));
          },
        });
      };

      const jsonPruneFetchResponse = (rawPrune = '', rawNeedle = '', ...rest) => {
        const prune = splitPaths(rawPrune);
        const needles = splitPaths(rawNeedle);
        if (!prune.length) return;
        const options = extraArgs(rest);
        fetchResponseRewriter(parseProps(options.propsToMatch || ''), (text) => {
          let obj;
          try { obj = JSON.parse(text); } catch (e) { return null; }
          return objectPrune(obj, prune, needles) ? JSON.stringify(obj) : null;
        });
      };

      const jsonPruneXhrResponse = (rawPrune = '', rawNeedle = '', ...rest) => {
        const prune = splitPaths(rawPrune);
        const needles = splitPaths(rawNeedle);
        if (!prune.length) return;
        hookXHR();
        const options = extraArgs(rest);
        xhrTransforms.push({
          props: parseProps(options.propsToMatch || ''),
          apply(value, type) {
            if (type === 'json' && value && typeof value === 'object') { objectPrune(value, prune, needles); return value; }
            if (typeof value !== 'string') return value;
            let obj;
            try { obj = JSON.parse(value); } catch (e) { return value; }
            return objectPrune(obj, prune, needles) ? JSON.stringify(obj) : value;
          },
        });
      };

      const textReplacer = (pattern, replacement) => {
        const re = isRegexText(pattern) ? toRegex(pattern) : new RegExp(escapeRe(pattern), 'g');
        return (text) => { re.lastIndex = 0; return text.replace(re, replacement); };
      };

      const replaceXhrResponse = (pattern = '', replacement = '', propsToMatch = '') => {
        if (!pattern) return;
        hookXHR();
        const replace = textReplacer(pattern, replacement);
        xhrTransforms.push({
          props: parseProps(propsToMatch),
          apply(value) { return typeof value === 'string' ? replace(value) : value; },
        });
      };

      const replaceFetchResponse = (pattern = '', replacement = '', propsToMatch = '') => {
        if (!pattern) return;
        const replace = textReplacer(pattern, replacement);
        fetchResponseRewriter(parseProps(propsToMatch), (text) => replace(text));
      };

      const refreshDefuser = () => {
        onDOM(() => {
          document.querySelectorAll('meta[http-equiv="refresh" i]').forEach((m) => m.remove());
        });
      };

      const noRAF = (needle = '') => {
        const test = matcher(needle);
        W.requestAnimationFrame = new Proxy(W.requestAnimationFrame, {
          apply(target, thisArg, args) {
            if (test(fnText(args[0]))) args[0] = function () {};
            return Reflect.apply(target, thisArg, args);
          },
        });
      };

      const popadsDummy = () => {
        for (const name of ['PopAds', 'popns']) { try { delete W[name]; defP(W, name, { value: {} }); } catch (e) {} }
      };

      const disableNewtabLinks = () => {
        document.addEventListener('click', (e) => {
          let t = e.target;
          while (t && t.localName !== 'a') t = t.parentNode;
          if (t && t.hasAttribute && t.hasAttribute('target')) { e.stopPropagation(); e.preventDefault(); }
        }, true);
      };

      const callNothrow = (chain = '') => {
        if (!chain) return;
        const parts = chain.split('.');
        const prop = parts.pop();
        let owner = W;
        for (const p of parts) { owner = owner[p]; if (!(owner instanceof Object)) return; }
        const fn = owner[prop];
        if (typeof fn !== 'function') return;
        owner[prop] = new Proxy(fn, { apply(t, self, args) { try { return Reflect.apply(t, self, args); } catch (e) { return undefined; } } });
      };

      const closeWindow = (pattern = '') => {
        if (matcher(pattern)(location.href)) { try { W.close(); } catch (e) {} }
      };

      const LIB = {
        'set-constant': (t, chain, value) => setConstant(t, chain, value),
        'abort-on-property-read': (t, chain) => abortOnRead(chain),
        'abort-on-property-write': (t, chain) => abortOnWrite(chain),
        'abort-on-stack-trace': (t, chain, needle) => abortOnStackTrace(chain, needle),
        'abort-current-script': (t, ...a) => abortCurrentScript(...a),
        'no-setTimeout-if': (t, needle, delay) => timerDefuser('setTimeout', needle, delay),
        'no-setInterval-if': (t, needle, delay) => timerDefuser('setInterval', needle, delay),
        'nano-setTimeout-booster': (t, ...a) => booster('setTimeout', ...a),
        'nano-setInterval-booster': (t, ...a) => booster('setInterval', ...a),
        'addEventListener-defuser': (t, type, pattern) => addEventListenerDefuser(type, pattern),
        'no-window-open-if': (t, pattern) => noWindowOpen(pattern),
        'remove-attr': (t, ...a) => removeAttr(...a),
        'remove-class': (t, ...a) => removeClass(...a),
        'set-attr': (t, ...a) => setAttr(t, ...a),
        'remove-node-text': (t, name, pattern, ...extra) => nodeText(name, pattern, undefined, extra),
        'replace-node-text': (t, name, pattern, replacement, ...extra) => { if (t) nodeText(name, pattern, replacement ?? '', extra); },
        'noeval': () => noEval(),
        'noeval-if': (t, needle) => noEval(needle ?? ''),
        'nowebrtc': () => noWebRTC(),
        'fuckadblock-defuser': () => fuckAdBlockDefuser(),
        'bab-defuser': () => babDefuser(),
        'no-xhr-if': (t, pattern, response) => noXhr(t, pattern, response),
        'no-fetch-if': (t, pattern, body) => noFetch(t, pattern, body),
        'set-local-storage-item': (t, key, value) => setStorage(t, 'localStorage', key, value),
        'set-session-storage-item': (t, key, value) => setStorage(t, 'sessionStorage', key, value),
        'set-cookie': (t, ...a) => setCookie(t, ...a),
        'remove-cookie': (t, needle) => removeCookie(needle),
        'href-sanitizer': (t, ...a) => hrefSanitizer(...a),
        'json-prune': (t, ...a) => jsonPrune(...a),
        'json-prune-fetch-response': (t, ...a) => jsonPruneFetchResponse(...a),
        'json-prune-xhr-response': (t, ...a) => jsonPruneXhrResponse(...a),
        'replace-xhr-response': (t, ...a) => { if (t) replaceXhrResponse(...a); },
        'replace-fetch-response': (t, ...a) => { if (t) replaceFetchResponse(...a); },
        'refresh-defuser': () => refreshDefuser(),
        'no-requestAnimationFrame-if': (t, needle) => noRAF(needle),
        'popads-dummy': () => popadsDummy(),
        'disable-newtab-links': () => disableNewtabLinks(),
        'call-nothrow': (t, chain) => callNothrow(chain),
        'close-window': (t, pattern) => closeWindow(pattern),
      };
      for (const [trusted, name, ...args] of CALLS) {
        const fn = LIB[name];
        if (!fn) continue;
        try { fn(trusted, ...args); } catch (e) {}
      }
    })();
    """#
}
