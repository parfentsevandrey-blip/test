# Lumen Write

A premium, minimalist word processor for Windows, built with Electron and
plain HTML/CSS/vanilla JS (no bundler, no UI framework). Part of the Lumen
office suite (see `../DESIGN.md` for the shared design system).

## Features

- **Layout**: frameless titlebar with brand mark, editable document title,
  and a light/dark theme toggle; custom in-page menu bar (File, Edit, View,
  Insert, Format, Help); icon toolbar; a collapsible document-outline
  sidebar; a centered, genuinely paginated document canvas (Letter by
  default, resizable via Page Setup — see below); a status bar with live
  word/character counts, an estimated reading time, a page indicator, and
  a 50–200% zoom slider.
- **Real pagination** (`src/pagination.js`): the document renders as
  discrete pages (816×1056px / US Letter @ 96dpi 1in margins by default —
  see Page Setup below for other sizes) with a ~40px inter-page gap and a
  small centered "Page N" label in each gap — not one ever-growing sheet.
  Under the hood there is still exactly **one** `contentEditable` region
  (so typing, selection, `execCommand` formatting, and undo/redo all keep
  working across a page boundary exactly as they did before); pagination
  is an overlay/measurement effect layered on top of it:
  - After each (debounced, ~200ms) edit, the page's real top-level
    blocks (paragraphs, headings, tables, images, TOC blocks, ...) are
    measured, and any block that would straddle a page boundary gets an
    invisible `padding-top` push that shoves it — and everything after
    it, for free, via normal block flow — down into the next page's
    frame. A decorative "page-frames" layer (the white page boxes,
    shadows, and gap labels) is rendered behind the content, resized to
    match. The same pass also records which page each top-level block
    *starts* on (`data-lw-page`), which is how the Table of Contents (see
    below) knows real page numbers instead of guessing.
  - This is genuinely dynamic: typing past the bottom of a page grows
    the pushes (and adds a page); deleting content shrinks/removes them
    again, live, as you type.
  - A ~20px **ruler** above the page shows inch tick marks (scaled to the
    page's actual width) and a highlighted band for the current margins,
    and scales (along with the page gaps) with the zoom slider.
  - Every page has editable **header and footer bands** (reusing the
    page's own top/bottom margin space — no extra page height is
    added). Header/footer content is shared across every page; click
    into the band on any page to edit it. A `{n}` token resolves live to
    that page's number on every *other* page as you type (and on the
    page you're actively editing once you click away, to avoid
    caret-jumping mid-edit) — `{pages}` resolves to the total page
    count. Header/footer text is saved with the document and carried
    into PDF export (see below).
  - The status bar's page indicator ("Page 2 of 5") tracks whichever
    page is currently scrolled into view and updates live as
    pagination recalculates.
  - Known approximation: an individual block taller than a single
    page's content area (a huge image, a very long table) is not split
    — it's left to flow across the page boundary as-is, the same way a
    plain browser print would handle it. Everything else reflows page
    by page.
- **Page Setup** (File ▸ Page Setup): a dialog to choose page size —
  Letter (8.5×11in), A4 (8.27×11.69in), or Legal (8.5×14in) — and margins
  — Normal (1in), Narrow (0.5in), Wide (1.5in), or Custom (four
  independent top/bottom/left/right inputs, in inches). Applying it
  recomputes the page's real pixel dimensions at 96dpi (e.g. A4 ≈
  794×1123px) and feeds straight into `src/pagination.js`'s page height,
  margin band, and ruler — the document re-paginates immediately, with
  every existing page frame/header/footer band rebuilt at the new
  geometry. Page geometry is per-document: it's saved with the file (see
  File formats below) and restored on open, and also drives the PDF, Word,
  and native Print output so an exported/printed page matches what's on
  screen instead of always defaulting to US Letter.
- **Table of Contents** (Insert ▸ Table of Contents): inserts a block at
  the cursor listing every Heading 1–3 in the document, indented by level,
  each entry showing the heading text and a right-aligned page number
  (from pagination's real per-page tracking, not a guess) with a light
  dotted leader between text and number, styled distinctly from body text
  (no bullets, tighter line-height, a small uppercase label). It's a
  snapshot, not a continuously-live view — like Word's own TOC — so it's
  refreshed on demand: hover or focus the block for a small inline
  "Update" button, or right-click it for "Update Table of Contents".
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
  links, horizontal rules, and a Table of Contents block (see above).
- **File formats**:
  - Native `.lwrite` (JSON: version, title, contentHTML, headerHTML,
    footerHTML, pageSetup, createdAt, modifiedAt) for New/Open/Save/Save
    As — header/footer text and the document's page size/margins
    (Page Setup) both round-trip with the document; New/templates/
    non-`.lwrite` opens fall back to the Letter/Normal-margin default.
  - Open also accepts `.html` (used as-is), `.txt` (each line wrapped in a
    `<p>`), and `.docx` (converted via `mammoth`) — none of these carry a
    page setup, so they load at the default too.
  - Export to PDF (`webContents.printToPDF`), Word `.docx` (via
    `html-to-docx`), Markdown `.md` (via `turndown`), and plain `.txt`.
    PDF export reflects the real multi-page layout: `@media print`
    strips the on-screen pagination overlay (ruler, page-frame
    backgrounds, gap labels, the synthetic per-block `padding-top`
    pushes) back down to one continuous flow so Chromium's own print
    engine paginates the real content against the document's actual page
    size/margins (from Page Setup, not always Letter), and — if the
    header/footer bands have any text — `printToPDF`'s
    `headerTemplate`/`footerTemplate` options render it on every page
    using Chromium's own `pageNumber`/`totalPages` template classes (so
    `{n}`/`{pages}` resolve correctly per page in the exported PDF too).
    Word export passes the same page size/margins to `html-to-docx`'s
    `pageSize`/`margins` options (it auto-detects the `px`/`in` unit
    suffix), so the exported `.docx`'s page setup matches too.
  - Native Print via `webContents.print()` uses the same `@media print`
    rules and the document's Page Setup page size, so print preview/
    physical printing paginate correctly too.
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
  toggle and neutral toasts respectively. The Table of Contents block's
  "Update" affordance uses a plain Unicode glyph (`&#8635;`) instead of a
  new SVG asset — it's text content, not an image, so it doesn't need to
  go through `window.lumen.icons` at all.
- `turndown` (Markdown export) is required only from `main.js`, same as the
  other filesystem/conversion packages.
- **Page geometry is CSS-custom-property-driven**: `src/pagination.js`
  computes page width/height/margins (from either the built-in defaults or
  a Page Setup change) and writes them onto `document.documentElement` as
  `--lw-page-w`/`--lw-page-h`/`--lw-margin-{top,bottom,left,right}`; every
  page/ruler/frame/band rule in `src/styles/app.css` reads those variables
  instead of hardcoding US-Letter pixel sizes. A Page Setup change is the
  only thing that ever writes them, so no other module needs to know Page
  Setup exists — it just re-renders against whatever the variables say.
  `main.js`'s PDF/Word export code paths keep their own small
  page-size/margin tables (CommonJS can't `import` the renderer's ES
  module) that must be kept in sync with `pagination.js`'s `PAGE_SIZES` if
  page sizes ever change.
- **Table of Contents blocks are `contenteditable="false"` islands**
  inside the single outer `contentEditable` region (`src/toc.js`) — the
  same technique browsers use for atomic inline widgets like @mentions.
  That makes a TOC block select/delete as one unit without letting
  keystrokes corrupt its generated entries, while still living inside the
  one big editable region pagination already knows how to measure and
  push. Its "Update" button/context-menu refresh mutates that block's DOM
  directly (not via `execCommand`), so — like header/footer band edits —
  it sits outside the main document's `execCommand` undo/redo stack.
- Recent files live in a small JSON file at
  `app.getPath('userData')/recent.json`, read/written only from `main.js`
  and exposed to the renderer via a single `recent:list` IPC handler
  (`window.lumen.getRecentFiles()`). Each entry carries a stable `id`
  (a random UUID assigned by `main.js`, not derived from the path).
  Opening a recent entry (from Open Recent or the start screen) sends only
  that `id` over IPC (`recent:open`) — `main.js` looks the real path up
  itself in its own `recent.json` and rejects an id that doesn't match a
  current entry. Drag-and-drop uses a separate `file:openDropped` channel
  that *does* accept a renderer-supplied path, since that path can only
  ever have come from a real OS-level `drop` event (see the comment at the
  drop listener in `src/renderer.js`); nothing else is allowed to pass an
  arbitrary path in. Both funnel into the same shared `loadDocumentFromPath`
  used by the regular Open-dialog code path.
- **Trust boundary for writes**: `main.js` tracks the current document's
  on-disk path itself (`currentFilePath`, process-side state — never read
  back from a renderer payload). `file:save` takes content/options only
  (no `filePath` argument) and writes to that tracked path, running the
  same dialog flow as Save As the first time a document is saved. Save As
  and every Export handler (`export:pdf/docx/markdown/txt`) always call
  `dialog.showSaveDialog()` themselves and write only to that result. This
  means a compromised/XSS'd renderer cannot redirect a write (or a
  by-path read) to an arbitrary file — see the comments at the top of
  `main.js` and above each handler for the full reasoning.
- **HTML sanitization on import**: content originating outside the live
  editor — mammoth's `.docx` conversion, a directly-opened `.html` file,
  or a `.lwrite` file's `contentHTML` field (which could be hand-edited/
  tampered with) — is run through a hand-rolled DOM-based sanitizer
  (`src/sanitize.js`) before it ever becomes `contentHTML`, at the single
  point `fileio.js`'s `applyOpenedResult` assigns it. It drops
  `script`/`iframe`/`object`/`embed`/`link`/`meta`/`style` outright, strips
  `on*` attributes and `javascript:`/`data:` URLs (except `data:image/*`
  on `<img src>`, needed for mammoth's embedded images), restricts
  `<a href>` to `http:`/`https:`/`mailto:`, and unwraps (keeps the text of,
  drops the tag of) anything outside the editor's known formatting
  vocabulary. Content the editor produces itself (typing, toolbar
  formatting, inserted images/tables) never passes through this — only
  external/file-sourced HTML does.
- `src/startscreen.js` and `src/outline.js` don't import `src/fileio.js`
  (and vice versa isn't a two-way dependency): `renderer.js` wires the
  start screen's template/recent callbacks to `fileio.js` functions,
  keeping the module graph a tree instead of a cycle.

## Known limitations / scope

- **Pagination is a measured approximation, not a native layout engine.**
  `src/pagination.js` reflows page breaks by measuring the single
  `#page` contentEditable's top-level block children and pushing the
  ones that would straddle a boundary — it isn't pixel-perfect
  Word/browser-native fragmentation. Concretely:
  - A single block taller than one page's content area (a huge image, a
    very long table, one giant unbroken paragraph) is not split across
    pages — it just flows through the boundary as-is, same as a plain
    browser print of a too-tall element would.
  - Pagination only looks at #page's *direct* children as break points;
    it doesn't reflow inside a table row or a list item to avoid
    splitting them mid-row/mid-item.
  - The header/footer token (`{n}`/`{pages}`) resolves live on every
    page *except* the one you're actively typing into (that one shows
    the raw token text until you click away) — a deliberate simplicity
    trade-off to avoid caret-jumping bugs from rewriting a focused
    contentEditable's DOM out from under the user on every keystroke.
  - Header/footer support plain text only (no bold/italic/etc. — the
    app's global Ctrl+B/I/U shortcuts always target the main document,
    not whichever band currently has focus).
- **Page Setup is one global setting per document**, not per-section: there's
  no Word-style "different first page" / odd-even header-footer / mixed
  page-size-within-one-document support — every page in a document shares
  the same size and margins. Margins are clamped to 0.25–3in regardless of
  preset or custom entry, and the header/footer band's height quietly
  scales down (with a 16px floor) for very narrow margins so the band
  never grows taller than its own margin. Only `.lwrite` carries a saved
  Page Setup; opening `.html`/`.txt`/`.docx` (or starting from a template)
  always starts at the Letter/Normal default, same as header/footer text.
- **Table of Contents is refreshed on demand, not continuously live** —
  by design, matching Word's own TOC field (see Insert ▸ Table of
  Contents above). Concretely:
  - A freshly-inserted or freshly-updated TOC reflects page numbers as of
    that moment; further edits to the document (including the TOC
    insertion itself changing the layout enough to push later headings
    onto a different page) won't retroactively update it until you hit
    Update again.
  - Only H1–H3 are listed (matching the document outline sidebar's own
    scope) — no user-selectable heading-level range or page-number-only
    style.
  - A TOC block's "Update" refresh mutates its DOM directly rather than
    going through `execCommand`, so — like header/footer band edits — it
    isn't on the main document's Ctrl+Z/Ctrl+Y undo/redo stack; undoing
    past it requires selecting/deleting the block itself instead.
  - Because a TOC block lives inside the single contentEditable region
    (as a non-editable island — see Architecture notes), its heading
    text and page numbers count toward the status bar's word/character
    counts, same as a table's cell text would.
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
