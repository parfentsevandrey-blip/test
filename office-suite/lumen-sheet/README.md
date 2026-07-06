# Lumen Sheet

A premium, minimalist spreadsheet desktop app for Windows, built with
Electron and plain HTML/CSS/JavaScript — no bundler, no UI framework. Part of
the Lumen office suite (see `../DESIGN.md` for the shared design system).

## Features

- **Layout**: custom titlebar (editable workbook title, theme toggle) →
  in-page menu bar (File/Edit/View/Insert/Format/Help) → toolbar → formula
  bar → scrollable grid with sticky column-letter/row-number headers → sheet
  tabs → status bar (Sum/Average/Count + zoom).
- **Grid**: default 100 rows × 26 columns, growable via Insert Row/Column
  (toolbar Insert menu or right-click a header). Column width and row height
  are drag-resizable from the header edges.
- **Selection & editing**: click to select a cell, shift-click or drag for a
  rectangular range. Arrow keys move the active cell. Typing a character,
  pressing F2, or double-clicking starts in-cell editing via a floating
  `<input class="cell-editor">` positioned over the cell. Enter/Shift+Enter/
  Tab/Shift+Tab commit and move; Escape cancels. The formula bar is a second,
  independent way to view/edit the active cell's raw content.
- **Formulas**: a hand-written tokenizer, recursive-descent parser, and
  evaluator (no third-party formula library). Supports `SUM`, `AVERAGE`,
  `MIN`, `MAX`, `COUNT`, `COUNTA`, `IF`, `AND`, `OR`, `NOT`, `CONCAT` /
  `CONCATENATE`, `ROUND`, `ABS`, `POWER`, `SQRT`, `LEN`, `UPPER`, `LOWER`,
  `TRIM`, `MOD`, `INT`, `TODAY`, `NOW`, `PI`. Operator precedence (tightest
  to loosest): `^` › unary `+`/`-` › `*` `/` › `+` `-` › `&` (concat) ›
  comparisons (`=` `<>` `<` `>` `<=` `>=`). A dependency graph recalculates a
  cell and all transitive dependents in topological order on every edit;
  circular references resolve to `#CIRCULAR!`. Errors surfaced: `#DIV/0!`,
  `#REF!` (deleted/out-of-bounds reference), `#NAME?` (unknown function /
  bad parse), `#VALUE!` (type mismatch).
- **Formatting**: bold/italic/underline, left/center/right align (numbers
  default right, text left), text color & fill color (curated swatches +
  native color picker), a single all-sides border toggle, and a number
  format preset per cell: General / Number / Currency / Percent / Date (see
  "Number format semantics" below).
- **Undo/redo**: a hand-rolled linear undo stack. Cell edits, paste, clear,
  and formatting changes snapshot affected cells' raw+format before/after;
  row/column insert/delete snapshot the whole sheet (since indices shift).
  Ctrl+Z/Ctrl+Y (or Ctrl+Shift+Z) walk the stacks; any new edit clears redo.
- **Clipboard**: Ctrl+C/X/V use an in-memory clipboard (not the OS
  clipboard). Pasting a formula shifts its relative (non-`$`) cell
  references by the row/column offset between source and destination.
- **File I/O**:
  - Native `.lsheet` (JSON) via File > New/Open/Save/Save As
    (Ctrl+N/O/S/Shift+S), with an unsaved-changes confirm dialog.
  - Import/Export `.xlsx` via the `xlsx` (SheetJS Community Edition)
    package, used only in `main.js` — formula cells round-trip as formula
    text; **on import we always recompute values with our own formula
    engine and ignore SheetJS's cached computed values**.
  - Import/Export `.csv` via the same package's `sheet_to_csv` /
    `csv_to_sheet` helpers.
- **Find**: Ctrl+F opens a dialog that searches raw content and computed/
  formatted display values across the active sheet, highlights all matches,
  and steps through them with Next/Previous.

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
| Arrow keys | Move active cell (Shift+Arrow extends selection) |
| Enter / Shift+Enter | Move down / up (or commit an edit and move) |
| Tab / Shift+Tab | Move right / left (or commit an edit and move) |
| F2 / double-click | Start editing the active cell |
| Delete / Backspace | Clear selected cells' contents |
| Escape | Cancel an in-progress edit |

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
  1970-01-01, not Excel's 1899-12-30 serial date system, and no date
  arithmetic helper functions (e.g. `DATE`, `YEAR`) beyond `TODAY`/`NOW`.
- **Find** searches the active sheet only, not the whole workbook.
- Numbers are coerced fairly liberally in aggregate functions (`SUM`,
  `AVERAGE`, etc.) — a numeric-looking string counts as a number, matching
  common spreadsheet behavior but not identical to Excel's exact type rules
  in every edge case.
