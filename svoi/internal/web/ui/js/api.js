// HTTP helpers for the local node API. All URLs are relative ("api/…") so the
// UI works under any mount point; every state-changing request carries the
// X-Svoi header the node requires (CSRF guard).
import { setState, state } from "./store.js";

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code);
    this.status = status;
    this.code = code || "internal";
  }
}

const statusCodes = {
  400: "invalid", 401: "unauthorized", 403: "denied", 404: "notfound", 409: "exists",
  412: "notconfigured", 413: "toolarge", 502: "offline", 503: "busy",
};

async function toError(res) {
  let code = statusCodes[res.status] || "internal";
  let message = res.statusText || "";
  try {
    const j = await res.json();
    if (j && j.error) {
      code = j.error.code || code;
      message = j.error.message || message;
    }
  } catch { /* body was not JSON */ }
  if (res.status === 401 || code === "unauthorized") setState({ authError: true });
  return new ApiError(res.status, code, message);
}

/**
 * fetch wrapper: JSON in, JSON out. `body` objects are JSON-encoded; Blob/File
 * bodies are sent raw. Throws ApiError on non-2xx and on network failure.
 */
export async function api(path, { method = "GET", body, headers = {}, signal, raw = false } = {}) {
  const h = { ...headers };
  const init = { method, headers: h, signal, credentials: "same-origin", cache: "no-store" };
  if (method !== "GET" && method !== "HEAD") h["X-Svoi"] = "1";
  if (body !== undefined) {
    if (body instanceof Blob || body instanceof ArrayBuffer || typeof body === "string") {
      init.body = body;
    } else {
      h["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
  }
  let res;
  try {
    res = await fetch("api/" + path, init);
  } catch (e) {
    if (e && e.name === "AbortError") throw e;
    throw new ApiError(0, "network", String(e && e.message || e));
  }
  if (!res.ok) throw await toError(res);
  if (raw) return res;
  if (res.status === 204) return null;
  const ct = res.headers.get("content-type") || "";
  return ct.includes("json") ? res.json() : res.text();
}

export const get = (path, opts) => api(path, opts);
export const post = (path, body = {}, opts) => api(path, { ...opts, method: "POST", body });
export const put = (path, body = {}, opts) => api(path, { ...opts, method: "PUT", body });
export const del = (path, opts) => api(path, { ...opts, method: "DELETE" });

/**
 * Raw-body upload with progress (fetch has no upload progress).
 * Returns { promise, abort }. onProgress(loaded, total).
 */
export function upload(path, body, { method = "POST", onProgress, contentType } = {}) {
  const xhr = new XMLHttpRequest();
  const promise = new Promise((resolve, reject) => {
    xhr.open(method, "api/" + path);
    xhr.setRequestHeader("X-Svoi", "1");
    if (contentType) xhr.setRequestHeader("Content-Type", contentType);
    xhr.responseType = "text";
    if (onProgress) {
      xhr.upload.onprogress = (e) => onProgress(e.loaded, e.lengthComputable ? e.total : body.size || 0);
    }
    xhr.onload = () => {
      let data = null;
      try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch { /* not JSON */ }
      if (xhr.status >= 200 && xhr.status < 300) return resolve(data);
      const code = (data && data.error && data.error.code) || statusCodes[xhr.status] || "internal";
      const msg = (data && data.error && data.error.message) || xhr.statusText;
      if (xhr.status === 401) setState({ authError: true });
      reject(new ApiError(xhr.status, code, msg));
    };
    xhr.onerror = () => reject(new ApiError(0, "network", "network error"));
    xhr.onabort = () => reject(new ApiError(0, "aborted", "aborted"));
    xhr.send(body);
  });
  return { promise, abort: () => xhr.abort() };
}

/** True when the id designates this device. */
export function isSelf(id) {
  return !id || id === "self" || (state.self && id === state.self.id);
}

/** Path prefix for configuration endpoints of a managed device (admins only). */
export function devPrefix(devId) {
  return isSelf(devId) ? "" : `d/${encodeURIComponent(devId)}/`;
}

const enc = encodeURIComponent;

export function peerPath(peerId) {
  return `peers/${enc(isSelf(peerId) ? "self" : peerId)}`;
}

/** Relative URL of a file on a (remote or local) share, for <img>/<video>/downloads. */
export function fileUrl(peerId, share, path, { dl = false } = {}) {
  return `api/${peerPath(peerId)}/file?share=${enc(share)}&path=${enc(path)}${dl ? "&dl=1" : ""}`;
}

export function thumbUrl(peerId, share, path, w = 256) {
  return `api/${peerPath(peerId)}/thumb?share=${enc(share)}&path=${enc(path)}&w=${w}`;
}

export function transferFileUrl(id, dl = false) {
  return `api/transfers/${enc(id)}/file${dl ? "?dl=1" : ""}`;
}

export function mailAttachmentUrl(id, index, dl = false) {
  return `api/mail/${enc(id)}/attachments/${index}${dl ? "?dl=1" : ""}`;
}

export function chatAttachmentUrl(id, index, dl = false) {
  return `api/chat/messages/${enc(id)}/attachments/${index}${dl ? "?dl=1" : ""}`;
}
