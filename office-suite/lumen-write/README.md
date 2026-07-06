# Lumen Write

A premium, minimalist word processor for Windows, built with Electron and
plain HTML/CSS/vanilla JS (no bundler, no UI framework). Part of the Lumen
office suite (see `../DESIGN.md` for the shared design system).

## Features

- **Layout**: frameless titlebar with brand mark, editable document title,
  and a light/dark theme toggle; custom in-page menu bar (File, Edit, View,
  Insert, Format, Help); icon toolbar; a centered US-Letter "page" canvas;
  status bar with live word/character counts and a 50–200% zoom slider.
- **Rich text editing** via `document.execCommand`: bold, italic, underline,
  strikethrough, alignment (left/center/right/justify), bulleted/numbered
  lists, indent/outdent, paragraph/heading styles (H1–H3), undo/redo,
  horizontal rules, and links.
- **Font family, font size, text color, and highlight color** are applied
  with a custom `wrapSelection()` helper that wraps the current selection in
  a styled `<span>` (via `Range.surroundContents`, falling back to
  `extractContents` + re-insert for selections that straddle multiple
  nodes) — `execCommand`'s font/color commands are unreliable across
  browsers, so this app never uses them.
- **Color pickers**: ~10 curated swatches per picker (text color pulls from
  the theme's ink/accent/semantic tokens, resolved to their literal computed
  color so exports stay portable; highlighter uses a few theme "soft" tones
  plus standard highlighter hues) plus a native `<input type="color">` for
  any custom color.
- **Find & Replace**: Find Next uses the legacy-but-functional
  `window.find()` for in-page highlighting; Replace/Replace All walk the
  page's text nodes with a `TreeWalker` for precise, structure-preserving
  substitution.
- **Insert**: images (native file picker → base64 data URL →
  `insertImage`), tables (rows/cols prompt → basic bordered `<table>`),
  links, and horizontal rules.
- **File formats**:
  - Native `.lwrite` (JSON: version, title, contentHTML, createdAt,
    modifiedAt) for New/Open/Save/Save As.
  - Open also accepts `.html` (used as-is), `.txt` (each line wrapped in a
    `<p>`), and `.docx` (converted via `mammoth`).
  - Export to PDF (`webContents.printToPDF`), Word `.docx` (via
    `html-to-docx`), and plain `.txt`.
  - Native Print via `webContents.print()`.
- **Unsaved-changes protection**: a small dot next to the title marks the
  document dirty; New, Open, and closing the window all show the app's own
  in-page confirmation dialog (never Electron's native message box) before
  discarding changes.
- **Theme**: light/dark toggle persisted to `localStorage['lumen-theme']`,
  driven by `document.documentElement.dataset.theme` (matches the shared
  Lumen design tokens in `src/styles/theme.css`).

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+N | New document |
| Ctrl+O | Open… |
| Ctrl+S | Save |
| Ctrl+Shift+S | Save As… |
| Ctrl+Z / Ctrl+Y | Undo / Redo |
| Ctrl+B / Ctrl+I / Ctrl+U | Bold / Italic / Underline |
| Ctrl+F | Find & Replace |

(Cut/Copy/Paste use the browser's native Ctrl+X/C/V handling in the
contentEditable page; Print is available from the File menu.)

## Development

```
npm install
npm start
```

(`npm start` runs `electron .`. If you're on a headless/Linux dev box
without a display, `npm start -- --no-sandbox` under Xvfb also works for
smoke-testing.)

## Building the Windows executable

```
npm install html-to-docx mammoth
ELECTRON_MIRROR=https://cdn.npmmirror.com/binaries/electron/ \
ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/ \
npm install --save-dev electron electron-builder

npm run build:win
```

`npm run build:win` already exports the mirror env vars for you; the two
`npm install` steps above need them set manually the first time so
Electron's own binary and electron-builder's NSIS tooling can download from
npmmirror.com instead of github.com. Output lands in `dist/`: an NSIS
installer (`Lumen Write Setup <version>.exe`) and a portable exe
(`LumenWrite-portable-<version>.exe`).

## Architecture notes

- `main.js` / `preload.js` are plain CommonJS (Node/Electron main +
  preload). No bundler is used anywhere in this app.
- All renderer code lives under `src/` and is loaded as native ES modules
  (`<script type="module">`) — plain `import`/`export` with explicit `.js`
  extensions, resolved natively by Chromium, no webpack/vite/rollup.
- `contextIsolation: true`, `nodeIntegration: false`, `sandbox: false`. The
  renderer never touches Node APIs directly; it only calls the small
  `window.lumen` API surface exposed by `preload.js` via `contextBridge`.
  Any npm package that touches the filesystem or does real work
  (`html-to-docx`, `mammoth`, `fs`, `dialog`, `printToPDF`) is required only
  from `main.js`.
- Icons are loaded once at startup in `preload.js` (`fs.readdirSync` +
  `fs.readFileSync` over `src/assets/icons/*.svg`) and exposed as raw SVG
  strings (`window.lumen.icons.<name>`), set via `button.innerHTML` so
  `stroke="currentColor"` picks up the button's text color in both themes.

## Known limitations / scope

- **No real pagination.** The document is a single continuous scrollable
  "page" element, not a paginated layout with hard page breaks — matching
  the from-scratch, no-framework scope of this project. Exporting a very
  long document to PDF will still paginate correctly (Chromium's print
  engine handles that), but you won't see page breaks while editing.
- **`document.execCommand` is deprecated** but still fully functional in
  Chromium/Electron, and was a deliberate, pragmatic choice over building or
  adopting a full rich-text editing framework from scratch. It is not
  expected to disappear from Chromium any time soon; if it ever does, the
  editing layer (`src/editor.js`) is isolated enough to be swapped out.
- **`window.find()`** (used for Find Next highlighting) is a non-standard,
  Chromium-only API. That's an acceptable target here since this is an
  Electron (Chromium) app, not a cross-browser web page.
- **`mammoth` (.docx import) fidelity**: mammoth intentionally converts
  Word documents to clean, semantic HTML and does not attempt to preserve
  exact visual layout — complex tables, multi-column layouts, headers/
  footers, footnotes, and unusual styles may not round-trip perfectly (or
  at all). The app surfaces mammoth's own conversion warnings in a dialog
  after import so you know when this happened.
- **`html-to-docx` (.docx export) fidelity**: similarly, very complex CSS
  (custom fonts beyond common system fonts, absolute positioning, some
  table styling) may not translate 1:1 into Word's native styles. Plain
  text, headings, basic tables, lists, links, images, and inline
  formatting export reliably.
- **Highlight color swatches** are not 100% theme-token-derived: 7 of the
  10 curated swatches are standard highlighter hues (yellow/green/blue/
  pink/orange/purple/gray) rather than app-chrome tokens, since document
  content colors are a different concern from UI chrome colors. The other
  3 swatches (plus all 10 text-color swatches) are resolved from
  `theme.css` custom properties.
- **Table editing** is intentionally basic: you can insert a table with a
  chosen row/column count and type into its cells, but there's no
  merge/split/resize/add-row-or-column UI — out of scope for this app.
- **Cut/Copy/Paste** menu items call `execCommand('cut'/'copy'/'paste')`;
  in practice users will mostly rely on the browser's native Ctrl+X/C/V
  handling in the contentEditable page, which works regardless.
