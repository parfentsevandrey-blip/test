// The "network" of the demo page.
//
// web-dev/mock-server.mjs is a Node http server. In the demo it runs inside the page
// (see shims/http.js): its request handler is called directly, with a tiny fake
// request and response, whenever the interface talks to /api or /__mock:
//
//   fetch / XMLHttpRequest / EventSource   -> replaced here
//   <img src>, <video src>, <audio src>, <iframe src>, <a href download>
//                                          -> the browser makes those requests itself,
//                                             so they are intercepted at the DOM level
//                                             and turned into data: / blob: URLs
//
// Nothing leaves the page; the interface cannot tell the difference.

const enc = new TextEncoder();
const dec = new TextDecoder();
const API_RE = /\/(api|__mock)\//;

class Emitter {
  constructor() { this._l = {}; }
  on(ev, fn) { (this._l[ev] ||= []).push(fn); return this; }
  once(ev, fn) { const w = (...a) => { this.off(ev, w); fn(...a); }; return this.on(ev, w); }
  off(ev, fn) { this._l[ev] = (this._l[ev] || []).filter((f) => f !== fn); return this; }
  removeListener(ev, fn) { return this.off(ev, fn); }
  emit(ev, ...a) { for (const f of [...(this._l[ev] || [])]) f(...a); return true; }
}

class FakeReq extends Emitter {
  constructor(method, url, headers, body) {
    super();
    this.method = method;
    this.url = url;
    this.headers = headers;
    this.destroyed = false;
    this._body = body;
    this._started = false;
  }
  on(ev, fn) {
    super.on(ev, fn);
    if (ev === "data" || ev === "end") this.resume();
    return this;
  }
  resume() {
    if (!this._started) { this._started = true; queueMicrotask(() => this._flow()); }
    return this;
  }
  _flow() {
    const b = this._body;
    if (b && b.length) for (let i = 0; i < b.length; i += 65536) this.emit("data", Buffer.from(b.subarray(i, i + 65536)));
    this.emit("end");
  }
  destroy() { this.destroyed = true; this.emit("close"); }
}

class FakeRes extends Emitter {
  constructor(onHead, onChunk, onEnd) {
    super();
    this.statusCode = 200;
    this.headersSent = false;
    this.writableEnded = false;
    this._h = {};
    this._onHead = onHead;
    this._onChunk = onChunk;
    this._onEnd = onEnd;
  }
  setHeader(k, v) { this._h[String(k).toLowerCase()] = v; }
  getHeader(k) { return this._h[String(k).toLowerCase()]; }
  writeHead(status, headers) {
    this.statusCode = status;
    for (const [k, v] of Object.entries(headers || {})) this.setHeader(k, v);
    this._sendHead();
    return this;
  }
  _sendHead() {
    if (this.headersSent) return;
    this.headersSent = true;
    this._onHead(this.statusCode, this._h);
  }
  write(chunk) {
    this._sendHead();
    if (typeof chunk === "string") chunk = enc.encode(chunk);
    this._onChunk(chunk);
    return true;
  }
  end(chunk) {
    if (chunk != null) this.write(chunk);
    this._sendHead();
    if (!this.writableEnded) {
      this.writableEnded = true;
      this._onEnd();
      this.emit("finish");
      this.emit("close");
    }
    return this;
  }
  destroy() { this.emit("close"); }
}

const lowerHeaders = (h) => {
  const o = {};
  new Headers(h || {}).forEach((v, k) => { o[k.toLowerCase()] = v; });
  return o;
};

async function toBytes(body) {
  if (body == null) return null;
  if (typeof body === "string") return enc.encode(body);
  if (body instanceof Uint8Array) return body;
  if (body instanceof ArrayBuffer) return new Uint8Array(body);
  if (ArrayBuffer.isView(body)) return new Uint8Array(body.buffer, body.byteOffset, body.byteLength);
  if (body instanceof Blob) return new Uint8Array(await body.arrayBuffer());
  if (body instanceof URLSearchParams) return enc.encode(body.toString());
  return new Uint8Array(await new Response(body).arrayBuffer());
}

/** "/api/..." (with the query) if the address is for the fake node, else null. */
function routeOf(input) {
  try {
    const u = new URL(typeof input === "string" ? input : input.url || String(input), location.href);
    const m = API_RE.exec(u.pathname);
    if (!m || u.origin !== location.origin) return null;
    return u.pathname.slice(m.index) + u.search;
  } catch { return null; }
}

const abortError = () => new DOMException("The operation was aborted.", "AbortError");

/** Run one request through the mock's handler; resolves with a Response as soon as the head is known. */
function dispatch(handler, method, route, headers, body, signal) {
  return new Promise((resolve, reject) => {
    if (signal && signal.aborted) return reject(abortError());
    let controller;
    let closed = false;
    const stream = new ReadableStream({
      start(c) { controller = c; },
      cancel() { closed = true; res.emit("close"); },
    });
    const bodyless = method === "HEAD";
    const res = new FakeRes(
      (status, h) => {
        const noBody = bodyless || status === 204 || status === 304;
        resolve(new Response(noBody ? null : stream, { status, headers: h }));
        if (noBody) closed = true;
      },
      (chunk) => { if (!closed) controller.enqueue(chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk)); },
      () => { if (!closed) { closed = true; try { controller.close(); } catch { /* cancelled */ } } },
    );
    const req = new FakeReq(method, route, headers, body);
    if (signal) {
      signal.addEventListener("abort", () => {
        closed = true;
        try { controller.error(abortError()); } catch { /* done */ }
        res.emit("close");
        req.emit("close");
        reject(abortError());
      }, { once: true });
    }
    Promise.resolve(handler(req, res)).catch((e) => { console.error(e); try { res.end(); } catch { /* done */ } });
  });
}

// ------------------------------------------------------------------ fetch

function installFetch(handler) {
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async function (input, init = {}) {
    const isReq = typeof Request !== "undefined" && input instanceof Request;
    const route = routeOf(isReq ? input.url : input);
    if (!route) return nativeFetch(input, init);
    const method = String(init.method || (isReq && input.method) || "GET").toUpperCase();
    const headers = lowerHeaders(init.headers || (isReq ? input.headers : {}));
    let body = init.body;
    if (body === undefined && isReq && method !== "GET" && method !== "HEAD") body = await input.arrayBuffer();
    return dispatch(handler, method, route, headers, await toBytes(body), init.signal || (isReq ? input.signal : undefined));
  };
  return (method, route, headers = {}, body = null, signal) => dispatch(handler, method, route, headers, body, signal);
}

// ------------------------------------------------------------------ XMLHttpRequest (uploads with progress)

function installXHR(handler) {
  const Native = window.XMLHttpRequest;
  class FakeXHR {
    constructor() {
      this.upload = {};
      this.readyState = 0;
      this.status = 0;
      this.statusText = "";
      this.responseText = "";
      this.responseType = "";
      this._h = {};
      this._native = null;
    }
    open(method, url) {
      this._method = method;
      this._url = url;
      this._route = routeOf(url);
      if (!this._route) { this._native = new Native(); this._native.open(method, url); }
    }
    setRequestHeader(k, v) { if (this._native) this._native.setRequestHeader(k, v); else this._h[k.toLowerCase()] = v; }
    abort() {
      if (this._native) return this._native.abort();
      this._aborted = true;
      if (this.onabort) this.onabort();
    }
    send(body) {
      if (this._native) {
        for (const k of ["onload", "onerror", "onabort"]) this._native[k] = this[k];
        this._native.upload.onprogress = this.upload.onprogress;
        this._native.responseType = this.responseType;
        this._native.send(body);
        return;
      }
      (async () => {
        const bytes = await toBytes(body);
        const total = bytes ? bytes.length : 0;
        // The upload "takes time": a few progress steps, as a real network would show.
        const steps = total > 2e6 ? 8 : total > 0 ? 3 : 0;
        for (let i = 1; i <= steps && !this._aborted; i++) {
          await new Promise((r) => setTimeout(r, 120));
          if (this.upload.onprogress) this.upload.onprogress({ loaded: Math.round((total * i) / steps), total, lengthComputable: true });
        }
        if (this._aborted) return;
        try {
          const res = await dispatch(handler, this._method, this._route, this._h, bytes);
          this.responseText = await res.text();
          this.status = res.status;
          this.statusText = res.statusText || "";
          this.readyState = 4;
          if (this.onload) this.onload();
        } catch {
          if (this.onerror) this.onerror();
        }
      })();
    }
  }
  window.XMLHttpRequest = FakeXHR;
}

// ------------------------------------------------------------------ EventSource (live events)

function installEventSource(dispatchRaw) {
  const Native = window.EventSource;
  class FakeEventSource extends EventTarget {
    constructor(url) {
      super();
      this._route = routeOf(url);
      if (!this._route) return new Native(url);
      this.url = url;
      this.readyState = 0;
      this.withCredentials = false;
      this.onopen = this.onmessage = this.onerror = null;
      this._abort = new AbortController();
      this._run();
    }
    _fire(type, init) {
      const ev = type === "open" || type === "error" ? new Event(type) : new MessageEvent(type, init);
      const h = this["on" + type];
      if (typeof h === "function") h.call(this, ev);
      this.dispatchEvent(ev);
    }
    async _run() {
      try {
        const res = await dispatchRaw("GET", this._route, { accept: "text/event-stream" }, null, this._abort.signal);
        if (!res.ok) throw new Error("HTTP " + res.status);
        this.readyState = 1;
        this._fire("open");
        const reader = res.body.getReader();
        let buf = "";
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });
          let i;
          while ((i = buf.indexOf("\n\n")) >= 0) {
            const block = buf.slice(0, i);
            buf = buf.slice(i + 2);
            let type = "message";
            const data = [];
            for (const line of block.split("\n")) {
              if (line.startsWith(":")) continue;
              if (line.startsWith("event:")) type = line.slice(6).trim();
              else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
            }
            if (data.length) this._fire(type, { data: data.join("\n"), lastEventId: "" });
          }
        }
      } catch (e) {
        if (this._abort.signal.aborted) return;
      }
      if (this.readyState !== 2) { this.readyState = 2; this._fire("error"); }
    }
    close() { this.readyState = 2; this._abort.abort(); }
  }
  FakeEventSource.CONNECTING = 0;
  FakeEventSource.OPEN = 1;
  FakeEventSource.CLOSED = 2;
  window.EventSource = FakeEventSource;
}

// ------------------------------------------------------------------ pictures, media, downloads

const urlCache = new Map();
const isApiUrl = (v) => typeof v === "string" && !/^(data|blob|javascript|mailto):/i.test(v) && routeOf(v) !== null && routeOf(v).startsWith("/api/");

function toDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });
}

/** Fetch an /api URL through the fake node and return a URL the browser can load by itself. */
function localUrl(absOrRel, kind) {
  const key = kind + "|" + new URL(absOrRel, location.href).href;
  if (!urlCache.has(key)) {
    urlCache.set(key, (async () => {
      const res = await window.fetch(absOrRel);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const blob = await res.blob();
      return kind === "data" ? toDataUrl(blob) : URL.createObjectURL(blob);
    })());
  }
  return urlCache.get(key);
}

function patchSrc(proto, prop, kind) {
  const d = Object.getOwnPropertyDescriptor(proto, prop);
  if (!d || !d.set) return;
  Object.defineProperty(proto, prop, {
    configurable: true,
    enumerable: d.enumerable,
    get() { return d.get.call(this); },
    set(v) {
      const s = String(v);
      if (!isApiUrl(s)) { d.set.call(this, v); return; }
      const p = localUrl(s, kind).then((u) => { if (this.__pending === p) { d.set.call(this, u); } }).catch(() => d.set.call(this, s));
      this.__pending = p;
    },
  });
}

function installDomUrls() {
  patchSrc(HTMLImageElement.prototype, "src", "data"); // data: works under the strictest img-src
  patchSrc(HTMLSourceElement.prototype, "src", "blob");
  patchSrc(HTMLMediaElement.prototype, "src", "blob");
  patchSrc(HTMLIFrameElement.prototype, "src", "blob");
  // A <video> asked to play before its address is ready waits for it.
  for (const m of ["play", "load"]) {
    const orig = HTMLMediaElement.prototype[m];
    HTMLMediaElement.prototype[m] = function (...a) {
      const p = this.__pending;
      return p ? p.then(() => orig.apply(this, a), () => orig.apply(this, a)) : orig.apply(this, a);
    };
  }
  const setAttr = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function (name, value) {
    const n = String(name).toLowerCase();
    if (n === "src" && "src" in this && isApiUrl(String(value))) { this.src = value; return; }
    return setAttr.call(this, name, value);
  };
  // Downloads: the hosting frame blocks every download a page starts, and there is no server
  // to ask anyway, so say what a real device would do instead of doing nothing.
  document.addEventListener("click", (e) => {
    const a = e.target && e.target.closest ? e.target.closest("a[href]") : null;
    if (!a || e.defaultPrevented) return;
    if (!isApiUrl(a.getAttribute("href"))) return;
    e.preventDefault();
    e.stopPropagation();
    window.dispatchEvent(new CustomEvent("svoi-demo-notice", { detail: "В демо файлы не скачиваются. В настоящей программе он сохранится на вашем устройстве." }));
  }, true);
}

// ------------------------------------------------------------------ service worker: none in a demo

function stubServiceWorker() {
  try {
    Object.defineProperty(navigator, "serviceWorker", {
      configurable: true,
      value: { controller: null, register: () => Promise.reject(new Error("demo")), addEventListener() {}, ready: new Promise(() => {}) },
    });
  } catch { /* leave it */ }
}

export function installNet(handler) {
  if (typeof handler !== "function") throw new Error("demo: the mock server did not register a request handler");
  stubServiceWorker();
  const raw = installFetch(handler);
  installXHR(handler);
  installEventSource(raw);
  installDomUrls();
  return raw;
}
