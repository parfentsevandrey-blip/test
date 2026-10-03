# Live demo of the interface

A copy of the web interface that needs **no server**: the real files of `internal/web/ui/` plus
`web-dev/mock-server.mjs` running *inside the page*. It is meant for looking at the interface and
clicking through it (a static host, a published page, a file on a stick), not for use.

```sh
node web-dev/make-demo.mjs                  # → web-dev/demo-dist/   (first run installs esbuild into web-dev/.demo-cache)
npx serve web-dev/demo-dist                 # or any static file server
NODE_PATH=/opt/node22/lib/node_modules node web-dev/demo-check.mjs [--csp "…"] [--shots DIR]
```

## How it works

* `mock-server.mjs` is bundled for the browser by esbuild with small stand-ins for the Node APIs it
  uses (`shims/`: `http`, `crypto`, `zlib`, `stream`, `fs`, `path`, `url`, `Buffer`, `process`). Its
  `http.createServer` only remembers the request handler.
* `net.js` replaces `fetch`, `XMLHttpRequest` and `EventSource` for `/api/…` and `/__mock/…` and calls that
  handler with a fake request and response. Pictures, music, video and PDFs are requested by the browser
  itself (`<img src>` …), so those `src` properties are intercepted and turned into `data:` / `blob:` URLs.
* `panel.js` is the small **ДЕМО** guide in a corner (shadow DOM): what this is, buttons that make a
  file / a message / a letter arrive, switch the NAS off, show the first run.
* `make-demo.mjs` writes `index.html` as a **fragment** (no `<html>`/`<head>`/`<body>`): the page host wraps
  it in its own skeleton. Service worker and manifest are left out.

## Limits of a hosted demo (what the artifact host allows)

* No downloads (the host blocks them): a download button explains that.
* No query string reaches the page: the first-run / ready-network switch uses `sessionStorage` and a reload.
* Every tab has its own made-up world; nothing is shared and nothing leaves the page.
* Anything the mock does not simulate (real transfers over a real network) is simply not there.

`demo-check.mjs` serves the build inside a skeleton like the host's (and, with `--csp`, under a strict
Content-Security-Policy) and walks through pictures, documents, video, upload, live events, mail, chat,
settings, the guide and the first run, failing on any console error.
