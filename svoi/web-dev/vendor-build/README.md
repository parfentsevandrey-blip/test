# Rebuilding `internal/web/ui/vendor/preact-htm.js`

The interface has no build step; its only dependency is one vendored ES module that bundles
[Preact](https://preactjs.com) 10.x (MIT), `preact/hooks` and [htm](https://github.com/developit/htm) 3.x
(Apache-2.0) behind one import. To rebuild or upgrade it:

```sh
mkdir /tmp/vend && cd /tmp/vend
npm init -y && npm i preact@10 htm@3 esbuild
cp <repo>/web-dev/vendor-build/entry.js .
./node_modules/.bin/esbuild entry.js --bundle --format=esm --minify --target=es2020 --legal-comments=none \
  --banner:js='/*! preact X.Y.Z (MIT) + preact/hooks + htm X.Y.Z (Apache-2.0), bundled by esbuild — see LICENSE-preact.txt, LICENSE-htm.txt */' \
  --outfile=<repo>/internal/web/ui/vendor/preact-htm.js
```

An earlier hand-assembled bundle was an old Preact build with a hooks bug (passive effects queued
while flushing were dropped), which made dialogs rendered through the custom portal lose autofocus,
Escape handling and typed characters. Keep the bundle current and run `node web-e2e/run.mjs`
after upgrading.
