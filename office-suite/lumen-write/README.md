# Lumen Write

A premium, minimalist word processor for Windows, built with Electron and
plain HTML/CSS/vanilla JS (no bundler, no UI framework). Part of the Lumen
office suite (see `../DESIGN.md` for the shared design system).

## Features

- **Layout**: frameless titlebar with brand mark, editable document title,
  and a light/dark theme toggle; custom in-page menu bar (File, Edit, View,
  Insert, Format, Help); icon toolbar; a collapsible document-outline
  sidebar; a centered US-Letter "page" canvas; status bar with live
  word/character counts, an estimated reading time, and a 50–200% zoom
  slider.
- **Start screen**: shown in place of the editor canvas on a fresh launch
  and from File ▸ New — a template gallery (Blank, Letter, Report, Resume,
  each with a short placeholder document and an abstract preview) plus a
  "Recent" list of your last 8 documents. Clicking a template or a recent
  file reveals the normal editor.
- **Document outline sidebar** (View ▸ Toggle Outline, or the toolbar
  panel icon): lists every H1/H2/H3 in the current document, indented by
  level, updating live (debounced) as you type or change heading styles.
  Clicking an entry scrolls the canvas to that heading and briefly flashes
  its background. Shows a muted placeholder when the document has no
  headings.
- **Toast notifications**: small floating confirmations (bottom-right) for
  successful Save/Save As, Export, and Open, and for any file-operation
  error — instead of a blocking dialog for routine confirmations.
- **Recent files**: the last 8 opened/saved documents (path, title, time)
  are remembered across launches and surfaced both in File ▸ Open Recent
  and on the start screen. Reopening a moved/deleted file removes it from
  the list and shows an error toast instead of failing silently.
- **Drag-and-drop open**: dropping a supported file (`.lwrite`, `.html`,
  `.txt`, `.docx`) onto the window opens it the same way as File ▸ Open.
- **Rich text editing** via `document.execCommand`: bold, italic, underline,
  strikethrough, alignment (left/center/right/justify), bulleted/numbered
  lists, indent/outdent, paragraph styles — Paragraph, Heading 1–3, Quote
  (a bordered, italic blockquote) and Code (a monospace block) — undo/redo,
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
    `html-to-docx`), Markdown `.md` (via `turndown`), and plain `.txt`.
  - Native Print via `webContents.print()`.
- **Unsaved-changes protection**: a small dot next to the title marks the
  document dirty; New, Open, and closing the window all show the app's own
  in-page confirmation dialog (never Electron's native message box) before
  discarding changes.
- **Theme**: light/dark toggle persisted to `localStorage['lumen-theme']`,
  driven by `document.documentElement.dataset.theme` (matches the shared
  Lumen design tokens in `src/styles/theme.css`).
- **Keyboard Shortcuts dialog** (Help ▸ Keyboard Shortcuts): a two-column
  reference of every shortcut the app supports, in the app's own dialog
  component.

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+N | New document |
| Ctrl+O | Open… |
| Ctrl+S | Save |
| Ctrl+Shift+S | Save As… |
| Ctrl+P | Print… |
| Ctrl+Z / Ctrl+Y | Undo / Redo |
| Ctrl+B / Ctrl+I / Ctrl+U | Bold / Italic / Underline |
| Ctrl+F | Find & Replace |
| Ctrl+= / Ctrl+- | Zoom in / out |
| Ctrl+X / Ctrl+C / Ctrl+V | Cut / Copy / Paste |
| Esc | Close the open dialog or menu |

(Cut/Copy/Paste use the browser's native Ctrl+X/C/V handling in the
contentEditable page; Print is available from the File menu. The full,
authoritative list is also available in-app via Help ▸ Keyboard
Shortcuts.)

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
npm install html-to-docx mammoth turndown
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
  `sidebar.svg` and `info.svg` were added for this round (hand-drawn,
  simple 2–3 primitive shapes, not sourced from Lucide) for the outline
  toggle and neutral toasts respectively.
- `turndown` (Markdown export) is required only from `main.js`, same as the
  other filesystem/conversion packages.
- Recent files live in a small JSON file at
  `app.getPath('userData')/recent.json`, read/written only from `main.js`
  and exposed to the renderer via a single `recent:list` IPC handler
  (`window.lumen.getRecentFiles()`); opening a path (from Open Recent, the
  start screen, or drag-and-drop) all go through one `file:openPath` IPC
  channel shared with the regular Open-dialog code path.
- `src/startscreen.js` and `src/outline.js` don't import `src/fileio.js`
  (and vice versa isn't a two-way dependency): `renderer.js` wires the
  start screen's template/recent callbacks to `fileio.js` functions,
  keeping the module graph a tree instead of a cycle.

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
- **Start screen template previews are deliberately abstract** — a few
  gray bars/blocks suggesting a layout — not literal thumbnails/screenshots
  of the actual starter content. Regenerating a real rendered preview for
  four small static templates wasn't worth the complexity.
- **Document outline is headings-only** (H1–H3, in document order). It does
  not surface images, tables, lists, or any other block type, and it does
  not renumber/react to nesting beyond the three heading levels the
  toolbar/Format menu expose.
- **Recent files list is capped at 8** and only tracks path/title/opened
  time — no pinning, no per-file thumbnails, no "clear list" UI (delete
  `recent.json` in the app's userData folder to reset it manually).
- **Markdown export (`turndown`) is one-way** (HTML → Markdown only); there
  is no Markdown *import*. Like the `.docx` import/export pair, very
  unusual HTML (nested tables, custom inline styles) may not have a clean
  Markdown equivalent and turndown will do its best-effort conversion.
- **Quote/Code paragraph styles** are plain `formatBlock` blockquote/pre
  elements styled via CSS — there's no separate language-aware syntax
  highlighting for Code blocks, and Quote doesn't support nested/attributed
  citations. Consistent with the rest of the app's block-style formatting
  (Heading 1–3), which is equally plain.
- **Drag-and-drop** only opens files with a recognized extension
  (`.lwrite`/`.html`/`.htm`/`.txt`/`.docx`); dropping anything else (or
  multiple files at once — only the first is used) is silently ignored
  rather than showing an error, matching how an unsupported file picked
  via the Open dialog's filters would never reach the app in the first
  place.
- **Toasts are fire-and-forget**: there's no persistent notification
  history/log to revisit a dismissed toast, and only one toast type shows
  per event (no "undo" affordance inside a toast).
