# Lumen Sheet

A premium, minimalist spreadsheet desktop app for Windows, built with
Electron and plain HTML/CSS/JavaScript — no bundler, no UI framework. Part of
the Lumen office suite (see `../DESIGN.md` for the shared design system).

## Features

- **Layout**: custom titlebar (editable workbook title, theme toggle) →
  in-page menu bar (File/Edit/View/Insert/Format/Data/Help) → toolbar →
  formula bar → scrollable grid with sticky column-letter/row-number headers
  → sheet tabs → status bar (Sum/Average/Count + zoom).
- **Grid**: default 100 rows × 26 columns, growable via Insert Row/Column
  (toolbar Insert menu or right-click a header). Column width and row height
  are drag-resizable from the header edges.
- **Selection & editing**: click to select a cell, shift-click or drag for a
  rectangular range. Arrow keys move the active cell. Typing a character,
  pressing F2, or double-clicking starts in-cell editing via a floating
  `<input class="cell-editor">` positioned over the cell. Enter/Shift+Enter/
  Tab/Shift+Tab commit and move; Escape cancels. The formula bar is a second,
  independent way to view/edit the active cell's raw content.
- **Fill handle**: a small `var(--accent-600)` square at the bottom-right
  corner of the active cell/selection. Dragging it down or right previews the
  extended range live; on release the whole fill is applied as one undoable
  action: formula sources have their relative references shifted (reusing
  `shiftFormulaRefsByOffset` from `grid.js`/`refUtils.js`, the same helper
  paste uses), a 2+ cell arithmetic or weekday/month-name sequence continues
  (e.g. `1, 2` → `3, 4, 5…`, `Mon, Tue` → `Wed, Thu…`, `Jan, Feb` → `Mar…`),
  and anything else copies its literal value/format as-is (cyclically, if the
  source block has more than one cell). Only downward/rightward drags extend
  the selection — see scope cuts below. Refuses (with a toast) to start or
  complete a fill whose source or target range contains a merged cell.
- **Formulas**: a hand-written tokenizer, recursive-descent parser, and
  evaluator (no third-party formula library). Supports `SUM`, `AVERAGE`,
  `MIN`, `MAX`, `COUNT`, `COUNTA`, `IF`, `AND`, `OR`, `NOT`, `CONCAT` /
  `CONCATENATE`, `ROUND`, `ABS`, `POWER`, `SQRT`, `LEN`, `UPPER`, `LOWER`,
  `TRIM`, `MOD`, `INT`, `TODAY`, `NOW`, `PI`, `VLOOKUP`, `INDEX`, `MATCH`,
  `SUMIF`, `COUNTIF`, `AVERAGEIF`, `DATE`, `YEAR`, `MONTH`, `DAY`, `WEEKDAY`.
  Operator precedence (tightest to loosest): `^` › unary `+`/`-` › `*` `/` ›
  `+` `-` › `&` (concat) › comparisons (`=` `<>` `<` `>` `<=` `>=`). A
  dependency graph recalculates a cell and all transitive dependents in
  topological order on every edit; circular references resolve to
  `#CIRCULAR!`. Errors surfaced: `#DIV/0!`, `#REF!` (deleted/out-of-bounds
  reference, or a `VLOOKUP`/`INDEX` column/row out of range), `#NAME?`
  (unknown function / bad parse), `#VALUE!` (type mismatch), `#N/A`
  (`VLOOKUP`/`MATCH` lookup value not found — matching the real-spreadsheet
  convention).
- **Formatting**: bold/italic/underline, left/center/right align (numbers
  default right, text left), text color & fill color (curated swatches +
  native color picker), a single all-sides border toggle, and a number
  format preset per cell: General / Number / Currency / Percent / Date (see
  "Number format semantics" below).
- **Conditional formatting** (Format ▸ Conditional Formatting): per-selection
  rules — "Highlight cells greater than / less than / equal to a value" with
  a color swatch, or a low-saturation red→white→green-ish "Color scale"
  (min/mid/max colors, all overridable). Rules are stored per-sheet and
  persisted in the `.lsheet` file, and are applied as a computed visual
  overlay at render time: they only supply a background color when the cell
  doesn't already have its own manual fill color, so they never clobber
  manual formatting. Non-numeric cells are never matched.
- **Cell merge** (Format ▸ Merge Cells / Unmerge Cells): select a range and
  merge it into one visual cell, rendered as a single `<td colspan rowspan>`
  at the range's top-left anchor (every other cell in the range is simply
  omitted from the table — native table layout then makes that one `<td>`
  cover the whole visual area). Merging keeps only the top-left cell's
  content; every other cell in the range is cleared. Clicking anywhere in a
  merged region selects it as a single unit (this falls out of the browser's
  own colspan/rowspan hit-testing — there's no special-case click logic
  needed); arrow-key navigation skips over the hidden interior cells
  (`moveActive` in `renderer.js`) and always lands on/leaves from the
  region's anchor. A formula referencing any cell inside a merged region
  (anchor or hidden interior) resolves to the anchor's value
  (`Sheet._resolveMergeAnchorKey` in `grid.js`). Merged ranges are stored
  per-sheet (`sheet.merges`, an array of `"A1:C3"`-style strings) and
  persisted in the `.lsheet` file. Merging a selection that only partially
  overlaps an existing merged range is blocked with a toast rather than
  silently corrupting the layout; a selection that fully *contains* one or
  more existing merges instead absorbs them into the new, larger merge. Sort
  and the fill handle both refuse (with an explanatory toast) to run when
  the source or target range contains a merged cell — see scope cuts.
- **Freeze panes** (View ▸ Freeze Panes / Freeze Top Row / Freeze First
  Column / Unfreeze Panes): the real Excel mechanism — select any cell and
  View ▸ Freeze Panes freezes every row above and every column left of it,
  both sticky and scrolling independently of the rest of the grid (matching
  real spreadsheet behavior, not just a first-row/first-column toggle).
  Freeze Top Row and Freeze First Column are thin convenience wrappers that
  call the same mechanism with `(row=1, col=0)` and `(row=0, col=1)`
  respectively — each replaces any existing freeze boundary rather than
  combining with it. Because column widths and row heights aren't uniform,
  the sticky offsets for the frozen band are computed per-cell
  (`computeStickyOffsets` in `renderer.js`) rather than using a fixed
  pixel constant; the boundary itself is marked with a heavier
  accent-colored border, distinct from the always-sticky letter/number
  gutter. The freeze boundary (a row count + column count) is stored and
  persisted per-sheet.
- **Data validation** (Data ▸ Data Validation…): select a range and enter a
  comma-separated list of allowed values. Every cell in that range then shows
  a small chevron affordance once it's the sole active cell (click it for a
  popover list of the allowed values — clicking one fills the cell). Typing a
  value that isn't on the list is rejected at commit time with a toast and
  the cell editor stays open so you can fix it, rather than silently
  accepting or visually flagging it after the fact — one consistent
  rejection path, matching how invalid ranges/failed saves are already
  reported elsewhere in the app. Rules are stored and persisted per-sheet
  (`sheet.dataValidations`), same "list of rules per range" shape as
  conditional-formatting rules. Validation is enforced only on direct typed
  entry (cell editor or formula bar) — see scope cuts.
- **Sort** (Data ▸ Sort Selection Ascending/Descending): sorts the selected
  rows by the computed value of the selection's first column — numbers
  numerically, text alphabetically, blanks always last — moving each row's
  full set of cells (values and formats). See scope cuts for how formulas are
  handled. Refuses (with a toast) to sort a selection that contains a merged
  cell.
- **Charts** (Insert ▸ Chart…): hand-rolled inline SVG bar/line/pie charts
  (no charting dependency) styled entirely from theme tokens. A dialog
  confirms the source range (defaults to the current selection) and chart
  type; see scope cuts for the label-orientation heuristic and the
  single-series limitation. Charts float over the grid, are draggable by
  their header, have a manual Refresh button, and also redraw automatically
  whenever any cell recalculates (falls out of the existing
  `renderAllCellContents` render pass). Position/size/type/range are
  persisted per-sheet in the `.lsheet` file.
- **Undo/redo**: a hand-rolled linear undo stack. Cell edits, paste, clear,
  fills, and formatting changes snapshot affected cells' raw+format
  before/after; row/column insert/delete snapshot the whole sheet (since
  indices shift). Ctrl+Z/Ctrl+Y (or Ctrl+Shift+Z) walk the stacks; any new
  edit clears redo. (Sort, conditional-formatting rule changes, merge/unmerge,
  and data-validation rule changes are *not* on the undo stack — see scope
  cuts.)
- **Clipboard**: Ctrl+C/X/V use an in-memory clipboard (not the OS
  clipboard). Pasting a formula shifts its relative (non-`$`) cell
  references by the row/column offset between source and destination.
- **File I/O**:
  - Native `.lsheet` (JSON) via File > New/Open/Save/Save As
    (Ctrl+N/O/S/Shift+S), with an unsaved-changes confirm dialog. Charts,
    conditional-formatting rules, freeze-pane state, merged ranges, data
    validation rules, and page setup all round-trip through this format.
  - Import/Export `.xlsx` via the `xlsx` (SheetJS Community Edition)
    package, used only in `main.js` — formula cells round-trip as formula
    text; **on import we always recompute values with our own formula
    engine and ignore SheetJS's cached computed values**. Charts,
    conditional-formatting rules, merges, and data validation are **not**
    exported to `.xlsx` (scope cut).
  - Import/Export `.csv` via the same package's `sheet_to_csv` /
    `csv_to_sheet` helpers.
- **Print / Page Setup** (File ▸ Page Setup…, File ▸ Print…, File ▸ Export
  PDF…): Page Setup stores a page size (Letter/A4/Legal), orientation
  (Portrait/Landscape), and an explicit print-area range string per sheet
  (defaulting to the sheet's used range). Print and Export PDF share one code
  path (`buildPrintRoot`/`buildPrintTable` in `renderer.js`): an off-screen
  `<table>` is built reproducing just the print area's computed
  values/formatting (merges included, clipped to the area if one hangs off
  its edge), scaled with a CSS transform to fit a single page of the chosen
  size/orientation, alongside a dynamically generated `@page` CSS rule. Print
  hands off to the browser's native `window.print()` (Electron wires this to
  the OS print dialog with no IPC needed); Export PDF hands the same
  already-rendered page to `main.js`, which calls Electron's
  `webContents.printToPDF({ preferCSSPageSize: true })` — honoring that same
  `@page` rule — and writes the result to disk. Single-page scale-to-fit
  only; see scope cuts.
- **Find**: Ctrl+F opens a dialog that searches raw content and computed/
  formatted display values across the active sheet, highlights all matches,
  and steps through them with Next/Previous.
- **Toast notifications** (`src/toast.js`): small floating confirmations
  (bottom-right, above the status bar, auto-dismiss ~3s) for successful
  Save/Save As, Export (xlsx/csv), Open/Import, and any file-operation error.
- **Recent files**: the last 8 opened/saved files (de-duped by path) are
  tracked in `<userData>/recent.json` by `main.js` and exposed via IPC. File ▸
  Open Recent lists them as a submenu; the Start Screen also lists them.
- **Start screen**: shown on a fresh launch and via File ▸ New, in place of
  the grid. Offers four workbook templates — Blank, Budget Tracker, Simple
  Invoice, To-Do List — each with a small abstract grid preview (colored
  rectangles, not a screenshot) and real starter data/formulas, plus the
  Recent list below.
- **Keyboard Shortcuts dialog** (Help ▸ Keyboard Shortcuts): a reference list
  of every shortcut in the table below, plus the fill handle.

## Lookup / reference function semantics

- **`VLOOKUP(value, range, colIndex, [rangeLookup])`**: searches the first
  column of `range` for `value`. `rangeLookup` defaults to `TRUE`
  (approximate match — assumes the first column is sorted ascending, returns
  the row for the largest value `<= value`); pass `FALSE` for an exact match.
  Returns `#N/A` if nothing matches, `#REF!` if `colIndex` is out of range.
- **`INDEX(range, row, [col])`**: 1-indexed. If `range` is a single row and
  `col` is omitted, `row` addresses a position along that row (the common
  one-dimensional usage). Out-of-range indices return `#REF!`.
- **`MATCH(value, range, [matchType])`**: `matchType` defaults to `1`
  (approximate, ascending); `0` is exact; `-1` is approximate descending.
  Returns the 1-based position, or `#N/A` if not found.
- **`SUMIF`/`COUNTIF`/`AVERAGEIF(range, criteria, [sumRange/avgRange])`**:
  `criteria` may be a bare value (equality) or a string starting with `>`,
  `<`, `>=`, `<=`, or `<>`.

## Chart label-orientation heuristic

For Insert ▸ Chart, the source range's first row and first column are
inspected: if the first row is mostly non-numeric (text) *and* the first
column (below/beyond it) is mostly numeric, the first row is treated as
category labels and the first data row beneath it as the (single) series.
Otherwise — the more common spreadsheet layout — labels come down the first
column and values from the next column, skipping the top row as a text
header if it looks like one. Only a single data series is charted; see scope
cuts.

## Number format semantics

- **General**: raw computed value; integers print as-is, other numbers are
  rounded to 10 significant decimal places to hide floating-point noise.
- **Number**: fixed 2 decimal places.
- **Currency**: `$` followed by 2 decimal places (`-$` prefix for negatives).
- **Percent**: value × 100, 2 decimal places, with a trailing `%`.
- **Date**: if the cell's computed value is numeric, it is treated as a day
  count since 1970-01-01 (Unix epoch day, *not* Excel's 1899-12-30 epoch)
  and rendered as `YYYY-MM-DD`. If the value is already a non-numeric,
  date-like string, it passes through unchanged. `TODAY()`/`NOW()` return
  serials on this same 1970-01-01-based day count.

## Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| Ctrl+N / Ctrl+O / Ctrl+S / Ctrl+Shift+S | New / Open / Save / Save As |
| Ctrl+Z / Ctrl+Y (or Ctrl+Shift+Z) | Undo / Redo |
| Ctrl+C / Ctrl+X / Ctrl+V | Copy / Cut / Paste (internal clipboard) |
| Ctrl+B / Ctrl+I / Ctrl+U | Bold / Italic / Underline |
| Ctrl+F | Find |
| Ctrl+P | Print |
| Arrow keys | Move active cell (Shift+Arrow extends selection; skips over merged cells) |
| Enter / Shift+Enter | Move down / up (or commit an edit and move) |
| Tab / Shift+Tab | Move right / left (or commit an edit and move) |
| F2 / double-click | Start editing the active cell |
| Delete / Backspace | Clear selected cells' contents |
| Escape | Cancel an in-progress edit |
| Drag the fill handle | Fill / extend a series into adjacent cells |

The full list is also available from Help ▸ Keyboard Shortcuts.

## Development

```bash
npm install
npm start
```

`npm start` runs `electron .`. In a headless/CI environment without a
display, pass `--no-sandbox` and a virtual display, or rely on the
automated build below.

## Building the Windows executable

```bash
npm install xlsx@0.18.5
ELECTRON_MIRROR=https://cdn.npmmirror.com/binaries/electron/ \
ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/ \
npm install --save-dev electron electron-builder
npm run build:win
```

Output lands in `dist/`: an NSIS installer (`Lumen Sheet Setup 1.0.0.exe`)
and a portable executable (`LumenSheet-portable-1.0.0.exe`).

## Known limitations / scope cuts

- **Grid size**: default 100 rows × 26 columns. Growable via Insert
  Row/Column, but there's no "infinite scroll" auto-growth.
- **No cross-sheet references** — a formula can only reference cells on its
  own sheet (e.g. `Sheet2!A1` is not supported). Documented, deliberate cut.
- **Borders**: a single "toggle all borders" action per selection rather
  than a per-side (top/right/bottom/left) border picker.
- **Sheet-level undo**: adding, renaming, or deleting a sheet is not on the
  undo/redo stack — only cell edits, formatting, paste/clear, and row/column
  insert/delete are undoable.
- **Clipboard is in-memory only** (not the OS clipboard) — you cannot paste
  spreadsheet cells into another application, or paste external OS-clipboard
  content in with formatting.
- **xlsx/csv import** discards original cell formatting (bold, colors,
  number formats) — only raw cell content (literal or formula) is imported;
  everything is recomputed by Lumen Sheet's own formula engine, never trusting
  SheetJS's cached values.
- **Date handling** is intentionally simple: numeric day-count since
  1970-01-01, not Excel's 1899-12-30 serial date system. `DATE`/`YEAR`/
  `MONTH`/`DAY`/`WEEKDAY` operate on that same day-count. `WEEKDAY`'s
  `type` argument supports `1` (Sun=1..Sat=7, default), `2` (Mon=1..Sun=7),
  and `3` (Mon=0..Sun=6) — not every exotic type code real spreadsheets
  support.
- **Find** searches the active sheet only, not the whole workbook.
- Numbers are coerced fairly liberally in aggregate functions (`SUM`,
  `AVERAGE`, etc.) — a numeric-looking string counts as a number, matching
  common spreadsheet behavior but not identical to Excel's exact type rules
  in every edge case.
- **Fill handle** only extends selections downward or rightward (dragging
  up/left is a no-op) — matching the common simplified fill-handle behavior.
  A single numeric cell (not 2+) copies its value rather than
  auto-incrementing, and there's no Ctrl-drag override of that default.
- **Charts** support a single data series only; a genuinely multi-series
  range (several data columns/rows) still charts just one series, per the
  label-orientation heuristic above. Charts are not exported to `.xlsx`.
  Inserting/deleting rows or columns does not re-point existing chart source
  ranges, conditional-formatting rule ranges, merged ranges, or data
  validation ranges (unlike formulas, which do shift) — adjust them manually
  via Insert ▸ Chart / Format ▸ Conditional Formatting / Format ▸ Merge Cells
  / Data ▸ Data Validation again if a structural edit moves your data.
- **Conditional formatting** only evaluates numeric computed values (text
  cells never match a rule) and only ever sets a background color — it
  can't set text color, bold, etc. Rule changes are not on the undo/redo
  stack (same simplification already applied to sheet add/rename/delete).
  The color-scale bounds are recomputed by scanning the rule's range on
  every render rather than cached, which is fine at this app's grid sizes.
- **Cell merge**: merging keeps only the top-left cell's content — every
  other cell in the range is cleared immediately, and that clearing (like
  the merge/unmerge action itself) is **not** on the undo/redo stack, the
  same precedent already applied to conditional-formatting rule changes and
  sheet add/rename/delete; unmerging only removes the merge boundary, it
  does not resurrect cleared content. Sort and the fill handle both refuse
  to run (with an explanatory toast) rather than operate on a source or
  target range containing a merged cell — a deliberate scope cut rather than
  attempting to make either operation merge-aware. Copy/paste of a merged
  anchor cell carries only its value/format, not the merge itself. Because a
  merged region is a single clickable/selectable unit by construction (see
  Features above), the UI has no way to construct a selection that only
  *partially* overlaps an existing merge — partial-overlap detection in
  `mergeSelection()` is there mainly to keep the data model honest if merges
  are ever manipulated another way (e.g. a hand-edited `.lsheet` file).
- **Data validation** is enforced only on direct typed entry (the cell
  editor or the formula bar) — pasting, filling, or a formula that evaluates
  to a disallowed value are not checked against the rule. Only an exact
  (case-insensitive) match against the allowed list is accepted; there's no
  "reject vs. warn" toggle — invalid entries are always rejected with a
  toast, never silently accepted or merely flagged, for one consistent
  behavior across the app. Rule changes are not on the undo/redo stack (same
  precedent as conditional formatting).
- **Freeze panes**: the general "freeze rows above / columns left of the
  active cell" mechanism is implemented (not just first-row/first-column
  toggles), but it's still a single boundary — there's no support for, say,
  freezing a middle band of rows while leaving others scrollable on both
  sides, which Excel doesn't support either.
- **Sort** moves values and formats, not formulas — any formula in the
  sorted rows is replaced by its last computed value at sort time (a
  deliberate simplification: a generic row reorder can't preserve
  relative-formula semantics the way row/column insert/delete can, since
  the sort order isn't known in advance). Also refuses to sort a selection
  containing a merged cell (see "Cell merge" above).
- **Print / Page Setup** is a single-page scale-to-fit, not Excel's full
  page-break-preview/multi-page-tiling system: content larger than one page
  at the chosen size/orientation is shrunk to fit rather than spanning
  multiple pages. Print margins are a fixed 0.4in, not user-configurable.
  The printed/exported table reproduces values, number formatting, basic
  cell formatting (bold/italic/underline/align/colors), merges, and the
  conditional-formatting computed background color — but not charts, which
  float over the grid and aren't part of the cell table print builds.
- **Start screen templates** are fixed content (Blank, Budget Tracker,
  Simple Invoice, To-Do List) — there's no user-defined/custom template
  gallery.
