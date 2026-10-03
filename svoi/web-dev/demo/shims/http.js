// node:http for a page: createServer() only remembers the request handler; net.js
// calls it for every fetch / XMLHttpRequest / EventSource aimed at /api or /__mock.
export function createServer(handler) {
  globalThis.__themeshMockHandler = handler;
  return {
    listen(port, host, cb) { if (typeof host === "function") cb = host; if (cb) setTimeout(cb, 0); return this; },
    address: () => ({ port: 0 }),
    on() { return this; },
    close() {},
  };
}
export default { createServer };
