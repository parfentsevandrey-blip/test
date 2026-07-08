// grid.js — spreadsheet data model: sheets, cells, formatting, number
// formatting, and dependency-graph-driven recalculation. Uses formulaEngine.js
// for the actual formula language; this module owns *state*.

import { parseFormula, evaluate, collectRefs, isError, FormulaError, ParseError } from './formulaEngine.js';
import {
  parseCellRefStr,
  cellKeyFromRC,
  parseRangeStr,
  colToLetter,
  shiftRefString,
} from './refUtils.js';

export const DEFAULT_ROWS = 100;
export const DEFAULT_COLS = 26;
export const DEFAULT_COL_WIDTH = 88;
export const DEFAULT_ROW_HEIGHT = 26;

export function defaultFormat() {
  return {
    bold: false,
    italic: false,
    underline: false,
    align: null, // null = auto (numbers right, text left)
    numberFormat: 'general', // general | number | currency | percent | date
    color: null,
    bg: null,
    border: false,
  };
}

export function cloneFormat(fmt) {
  return { ...defaultFormat(), ...fmt };
}

function isBlank(raw) {
  return raw === undefined || raw === null || raw === '';
}

/** Parse a literal (non-formula) raw string into a typed value: number, boolean, or string. */
export function literalValue(raw) {
  if (isBlank(raw)) return '';
  const trimmed = String(raw).trim();
  if (/^(true|false)$/i.test(trimmed)) return /^true$/i.test(trimmed);
  if (trimmed !== '' && !Number.isNaN(Number(trimmed)) && /^[+-]?(\d+\.?\d*|\.\d+)$/.test(trimmed)) {
    return Number(trimmed);
  }
  return String(raw);
}

export class Sheet {
  constructor(name) {
    this.name = name;
    this.cells = new Map(); // key -> { raw, format, computed, deps: Set<key> }
    this.colWidths = {}; // colIndex -> px
    this.rowHeights = {}; // rowIndex -> px
    this.rowCount = DEFAULT_ROWS;
    this.colCount = DEFAULT_COLS;
    this.dependents = new Map(); // key -> Set<key> of formulas that read this key

    // v1.1 additions — see README for full semantics of each.
    this.charts = []; // [{ id, type, range, x, y, w, h, title }]
    this.condFormats = []; // [{ id, range, kind, op, value, color, minColor, midColor, maxColor }]

    // v1.2 ("depth") additions — see README for full semantics of each.
    // freezeRow/freezeCol are *counts* (0 = none): freezeRow=N pins the top N
    // rows, freezeCol=M pins the left M columns — the general Freeze Panes
    // mechanism. (Older .lsheet files stored these as booleans; fromJSON()
    // below migrates true/false -> 1/0.)
    this.freezeRow = 0;
    this.freezeCol = 0;
    this.merges = []; // ["B2:D4", ...] — top-left cell of each range is the visible anchor
    this.dataValidations = []; // [{ id, range, values: ["Yes","No"] }]
    this.pageSetup = { pageSize: 'letter', orientation: 'portrait', printArea: null }; // printArea: explicit range string, or null = use the used range
  }

  /** The merge (as a parsed range) containing (col,row) anywhere within it (anchor or hidden interior), or null. */
  getMergeContainingCell(col, row) {
    for (const m of this.merges) {
      const r = parseRangeStr(m);
      if (r && col >= r.startCol && col <= r.endCol && row >= r.startRow && row <= r.endRow) return r;
    }
    return null;
  }

  /** Resolve (col,row) to the cell key formulas should actually read: a merged
   * region's top-left anchor if (col,row) falls inside one, else itself. */
  _resolveMergeAnchorKey(col, row) {
    const merge = this.getMergeContainingCell(col, row);
    if (merge) return cellKeyFromRC(merge.startCol, merge.startRow);
    return cellKeyFromRC(col, row);
  }

  getCell(key) {
    return this.cells.get(key);
  }

  ensureCell(key) {
    let cell = this.cells.get(key);
    if (!cell) {
      cell = { raw: '', format: defaultFormat(), computed: '', deps: new Set() };
      this.cells.set(key, cell);
    }
    return cell;
  }

  /** Snapshot a cell's raw+format for undo/redo (deep-ish clone, safe to store). */
  snapshotCell(key) {
    const cell = this.cells.get(key);
    if (!cell) return { raw: '', format: defaultFormat() };
    return { raw: cell.raw, format: { ...cell.format } };
  }

  /** Restore a cell from a snapshot produced by snapshotCell(), recalculating dependents. */
  restoreCell(key, snapshot) {
    const cell = this.ensureCell(key);
    cell.raw = snapshot.raw;
    cell.format = { ...snapshot.format };
    this._reparse(key);
    this._recalcFrom(key);
    this._pruneIfEmpty(key);
  }

  _pruneIfEmpty(key) {
    const cell = this.cells.get(key);
    if (!cell) return;
    const fmtIsDefault = JSON.stringify(cell.format) === JSON.stringify(defaultFormat());
    if (isBlank(cell.raw) && fmtIsDefault && (!cell.deps || cell.deps.size === 0)) {
      this.cells.delete(key);
    }
  }

  /** Set a cell's raw content (formula or literal) and recalculate it + dependents. */
  setRaw(key, raw) {
    const cell = this.ensureCell(key);
    cell.raw = raw;
    this._reparse(key);
    this._recalcFrom(key);
    this._pruneIfEmpty(key);
  }

  /** Merge a partial format patch into a cell's format and re-render display (no recalculation needed). */
  setFormat(key, patch) {
    const cell = this.ensureCell(key);
    cell.format = { ...cell.format, ...patch };
    this._pruneIfEmpty(key);
  }

  _reparse(key) {
    const cell = this.cells.get(key);
    if (!cell) return;
    const oldDeps = cell.deps || new Set();
    for (const dep of oldDeps) {
      const set = this.dependents.get(dep);
      if (set) {
        set.delete(key);
        if (set.size === 0) this.dependents.delete(dep);
      }
    }
    let newDeps = new Set();
    if (typeof cell.raw === 'string' && cell.raw.startsWith('=')) {
      try {
        const ast = parseFormula(cell.raw.slice(1));
        cell.ast = ast;
        newDeps = collectRefs(ast);
      } catch (e) {
        cell.ast = null;
        cell.parseError = true;
      }
    } else {
      cell.ast = null;
      cell.parseError = false;
    }
    cell.deps = newDeps;
    for (const dep of newDeps) {
      let set = this.dependents.get(dep);
      if (!set) {
        set = new Set();
        this.dependents.set(dep, set);
      }
      set.add(key);
    }
  }

  /** BFS the transitive dependent closure of a set of start keys (each start
   * key is included too). Used to figure out exactly which cells a change
   * can possibly affect, so a recalc pass never has to touch anything else. */
  _collectAffected(startKeys) {
    const affected = new Set();
    const stack = [...startKeys];
    while (stack.length) {
      const k = stack.pop();
      if (affected.has(k)) continue;
      affected.add(k);
      const deps = this.dependents.get(k);
      if (deps) for (const d of deps) stack.push(d);
    }
    return affected;
  }

  /** Topologically sort `affected` (a Set of cell keys) and evaluate each one
   * exactly once, in dependency order. Any cell left over once the queue
   * drains sits on a cycle and gets #CIRCULAR!. This is the one and only
   * place cells get evaluated during a recalc — callers build the affected
   * set (via _collectAffected or otherwise) and hand it here once, so a
   * formula with N precedent cells gets evaluated once per pass, never once
   * per precedent (see recalcAll()/_recalcFrom() below).
   */
  _recalcSet(affected) {
    const inDegree = new Map();
    for (const k of affected) {
      const cell = this.cells.get(k);
      const deps = cell && cell.deps ? cell.deps : new Set();
      let count = 0;
      for (const d of deps) if (affected.has(d)) count++;
      inDegree.set(k, count);
    }
    const queue = [...affected].filter((k) => inDegree.get(k) === 0);
    const order = [];
    const remaining = new Set(affected);
    while (queue.length) {
      const k = queue.shift();
      if (!remaining.has(k)) continue;
      remaining.delete(k);
      order.push(k);
      const dependents = this.dependents.get(k);
      if (dependents) {
        for (const d of dependents) {
          if (!affected.has(d)) continue;
          inDegree.set(d, inDegree.get(d) - 1);
          if (inDegree.get(d) === 0) queue.push(d);
        }
      }
    }
    for (const k of order) this._evalCell(k);
    for (const k of remaining) {
      const cell = this.cells.get(k);
      if (cell) cell.computed = new FormulaError('#CIRCULAR!');
    }
  }

  _recalcFrom(startKey) {
    this._recalcSet(this._collectAffected([startKey]));
  }

  _evalCell(key) {
    const cell = this.cells.get(key);
    if (!cell) return;
    if (typeof cell.raw === 'string' && cell.raw.startsWith('=')) {
      if (cell.parseError || !cell.ast) {
        cell.computed = new FormulaError('#NAME?');
        return;
      }
      const ctx = {
        getCellValue: (ref) => this._getCellValueForFormula(ref),
        getRange: (rangeRef) => this._getRangeForFormula(rangeRef),
      };
      try {
        cell.computed = evaluate(cell.ast, ctx);
      } catch (e) {
        cell.computed = new FormulaError('#VALUE!');
      }
    } else {
      cell.computed = literalValue(cell.raw);
    }
  }

  // A ref/range that lands inside a merged region resolves to that region's
  // top-left anchor cell — see README "Cell merge" section. This applies
  // whether the merge's own non-anchor cells were already blanked out by the
  // merge action or not, so formula semantics don't depend on that detail.
  _getCellValueForFormula(ref) {
    const parsed = parseCellRefStr(ref);
    if (!parsed) return new FormulaError('#REF!');
    const key = this._resolveMergeAnchorKey(parsed.col, parsed.row);
    const cell = this.cells.get(key);
    if (!cell) return '';
    return cell.computed === undefined ? '' : cell.computed;
  }

  _getRangeForFormula(rangeRef) {
    const range = parseRangeStr(rangeRef);
    if (!range) return [];
    const out = [];
    for (let r = range.startRow; r <= range.endRow; r++) {
      for (let c = range.startCol; c <= range.endCol; c++) {
        const key = this._resolveMergeAnchorKey(c, r);
        const cell = this.cells.get(key);
        out.push(cell && cell.computed !== undefined ? cell.computed : '');
      }
    }
    return out;
  }

  /** Recompute every formula cell from scratch (used after bulk structural
   * changes). Visits every cell in ONE topological pass rather than calling
   * _recalcFrom() per cell — the latter re-walks and re-evaluates the full
   * transitive-dependent closure from scratch for every single starting
   * cell, so a formula with N precedent cells (e.g. =SUM(A1:Z10000)) would
   * get independently recomputed N times per pass instead of once. */
  recalcAll() {
    for (const key of this.cells.keys()) this._reparse(key);
    this._recalcSet(new Set(this.cells.keys()));
  }

  insertRow(atIndex) {
    this._insertLine('row', atIndex);
  }
  deleteRow(atIndex) {
    this._deleteLine('row', atIndex);
  }
  insertCol(atIndex) {
    this._insertLine('col', atIndex);
  }
  deleteCol(atIndex) {
    this._deleteLine('col', atIndex);
  }

  _insertLine(kind, atIndex) {
    const newCells = new Map();
    for (const [key, cell] of this.cells) {
      const parsed = parseCellRefStr(key);
      let { col, row } = parsed;
      if (kind === 'row' && row >= atIndex) row += 1;
      if (kind === 'col' && col >= atIndex) col += 1;
      newCells.set(cellKeyFromRC(col, row), cell);
    }
    this.cells = newCells;
    if (kind === 'row') this.rowCount += 1;
    else this.colCount += 1;
    this._shiftSizeMap(kind === 'row' ? this.rowHeights : this.colWidths, atIndex, 1);
    this._rebuildDependencyGraph();
    this._shiftFormulaRefs(kind, atIndex, 1);
  }

  _deleteLine(kind, atIndex) {
    const newCells = new Map();
    for (const [key, cell] of this.cells) {
      const parsed = parseCellRefStr(key);
      let { col, row } = parsed;
      if (kind === 'row') {
        if (row === atIndex) continue; // dropped
        if (row > atIndex) row -= 1;
      } else {
        if (col === atIndex) continue;
        if (col > atIndex) col -= 1;
      }
      newCells.set(cellKeyFromRC(col, row), cell);
    }
    this.cells = newCells;
    if (kind === 'row') this.rowCount = Math.max(1, this.rowCount - 1);
    else this.colCount = Math.max(1, this.colCount - 1);
    this._shiftSizeMap(kind === 'row' ? this.rowHeights : this.colWidths, atIndex, -1);
    this._rebuildDependencyGraph();
    this._shiftFormulaRefs(kind, atIndex, -1);
  }

  _shiftSizeMap(map, atIndex, delta) {
    const entries = Object.entries(map).map(([k, v]) => [parseInt(k, 10), v]);
    const next = {};
    for (const [idx, v] of entries) {
      if (delta > 0) {
        next[idx >= atIndex ? idx + 1 : idx] = v;
      } else if (idx === atIndex) {
        // dropped
      } else {
        next[idx > atIndex ? idx - 1 : idx] = v;
      }
    }
    for (const k of Object.keys(map)) delete map[k];
    Object.assign(map, next);
  }

  // Adjust every formula's textual refs after a row/col insert or delete, then
  // rebuild the AST/deps/computed values for all formula cells.
  _shiftFormulaRefs(kind, atIndex, delta) {
    for (const cell of this.cells.values()) {
      if (typeof cell.raw !== 'string' || !cell.raw.startsWith('=')) continue;
      cell.raw = shiftFormulaText(cell.raw, kind, atIndex, delta);
    }
    this.recalcAll();
  }

  _rebuildDependencyGraph() {
    this.dependents = new Map();
    for (const [key, cell] of this.cells) {
      cell.deps = new Set();
    }
  }

  toJSON() {
    const cells = {};
    for (const [key, cell] of this.cells) {
      cells[key] = { raw: cell.raw, format: cell.format };
    }
    return {
      name: this.name,
      cells,
      colWidths: this.colWidths,
      rowHeights: this.rowHeights,
      rowCount: this.rowCount,
      colCount: this.colCount,
      freezeRow: this.freezeRow,
      freezeCol: this.freezeCol,
      charts: this.charts,
      condFormats: this.condFormats,
      merges: this.merges,
      dataValidations: this.dataValidations,
      pageSetup: this.pageSetup,
    };
  }

  // Tolerant of hand-corrupted/partially-written .lsheet input: `data` (and
  // any of its nested fields) may be null, the wrong type, or simply
  // missing — every field below falls back to a sane default rather than
  // dereferencing something that might not be there. This is what stops a
  // shape like {"cells": {"A1": null}} from throwing deep in the open flow
  // (see README/data-safety audit) — the outer Workbook.fromJSON() also
  // wraps the whole parse in try/catch as defense in depth, but individual
  // cells being null is common enough (a partially-written save, a
  // hand-edited file) that it's worth tolerating here directly instead of
  // discarding the whole document over one bad cell.
  static fromJSON(data) {
    if (!data || typeof data !== 'object' || Array.isArray(data)) data = {};
    const sheet = new Sheet(typeof data.name === 'string' ? data.name : 'Sheet1');
    sheet.rowCount = typeof data.rowCount === 'number' && data.rowCount > 0 ? data.rowCount : DEFAULT_ROWS;
    sheet.colCount = typeof data.colCount === 'number' && data.colCount > 0 ? data.colCount : DEFAULT_COLS;
    sheet.colWidths = data.colWidths && typeof data.colWidths === 'object' ? data.colWidths : {};
    sheet.rowHeights = data.rowHeights && typeof data.rowHeights === 'object' ? data.rowHeights : {};
    // freezeRow/freezeCol used to be booleans (v1.1); migrate true/false -> 1/0
    // so older .lsheet files still load under the new "how many rows/cols"
    // Freeze Panes model.
    sheet.freezeRow = typeof data.freezeRow === 'number' ? data.freezeRow : data.freezeRow ? 1 : 0;
    sheet.freezeCol = typeof data.freezeCol === 'number' ? data.freezeCol : data.freezeCol ? 1 : 0;
    sheet.charts = Array.isArray(data.charts)
      ? data.charts.filter((c) => c && typeof c === 'object').map((c) => ({ ...c }))
      : [];
    sheet.condFormats = Array.isArray(data.condFormats)
      ? data.condFormats.filter((r) => r && typeof r === 'object').map((r) => ({ ...r }))
      : [];
    sheet.merges = Array.isArray(data.merges) ? data.merges.filter((m) => typeof m === 'string') : [];
    sheet.dataValidations = Array.isArray(data.dataValidations)
      ? data.dataValidations
          .filter((r) => r && typeof r === 'object')
          .map((r) => ({ ...r, values: Array.isArray(r.values) ? r.values.slice() : [] }))
      : [];
    sheet.pageSetup = {
      pageSize: 'letter',
      orientation: 'portrait',
      printArea: null,
      ...(data.pageSetup && typeof data.pageSetup === 'object' ? data.pageSetup : {}),
    };
    const cells = data.cells && typeof data.cells === 'object' && !Array.isArray(data.cells) ? data.cells : {};
    for (const key of Object.keys(cells)) {
      const c = cells[key];
      const raw = c && typeof c === 'object' && typeof c.raw !== 'undefined' ? c.raw || '' : '';
      const format = c && typeof c === 'object' && c.format && typeof c.format === 'object' ? c.format : {};
      sheet.cells.set(key, { raw, format: cloneFormat(format), computed: '', deps: new Set() });
    }
    sheet.recalcAll();
    return sheet;
  }
}

// Re-tokenize a formula's textual refs, shifting any ref on the affected axis
// at/after atIndex by delta. Cell refs whose row/col equals the *deleted*
// index become #REF!. Locked ($col or $row) components are still shifted —
// a $ lock only affects copy/paste relative-adjustment, not structural
// insert/delete, matching Excel's behavior.
function shiftFormulaText(raw, kind, atIndex, delta) {
  const body = raw.slice(1);
  const re = /(\$?)([A-Za-z]{1,3})(\$?)([0-9]+)/g;
  const shifted = body.replace(re, (match, colLock, letters, rowLock, rowStr) => {
    const parsed = parseCellRefStr(`${colLock}${letters}${rowLock}${rowStr}`);
    if (!parsed) return match;
    let { col, row } = parsed;
    if (kind === 'row') {
      if (delta < 0 && row === atIndex) return '#REF!';
      if (row >= atIndex + (delta < 0 ? 1 : 0)) row += delta;
    } else {
      if (delta < 0 && col === atIndex) return '#REF!';
      if (col >= atIndex + (delta < 0 ? 1 : 0)) col += delta;
    }
    if (row < 0 || col < 0) return '#REF!';
    return `${colLock}${colToLetter(col)}${rowLock}${row + 1}`;
  });
  return '=' + shifted;
}

/**
 * Shift every cell reference inside a formula's raw text ("=A1+B2") by a
 * (colOffset, rowOffset) pair, respecting $ locks. Used for copy/paste.
 * Refs that would land out of bounds become "#REF!".
 */
export function shiftFormulaRefsByOffset(raw, colOffset, rowOffset) {
  const body = raw.slice(1);
  const re = /(\$?)([A-Za-z]{1,3})(\$?)([0-9]+)/g;
  const shifted = body.replace(re, (match, colLock, letters, rowLock, rowStr) => {
    const ref = `${colLock}${letters}${rowLock}${rowStr}`;
    const { text } = shiftRefString(ref, colOffset, rowOffset);
    return text;
  });
  return '=' + shifted;
}

export class Workbook {
  constructor() {
    this.sheets = [new Sheet('Sheet1')];
    this.activeSheetIndex = 0;
    this.title = 'Untitled spreadsheet';
    this.dirty = false;
  }

  get activeSheet() {
    return this.sheets[this.activeSheetIndex];
  }

  addSheet(name) {
    const sheet = new Sheet(name || this._nextSheetName());
    this.sheets.push(sheet);
    return sheet;
  }

  _nextSheetName() {
    let n = this.sheets.length + 1;
    let name = `Sheet${n}`;
    const names = new Set(this.sheets.map((s) => s.name));
    while (names.has(name)) {
      n++;
      name = `Sheet${n}`;
    }
    return name;
  }

  removeSheet(index) {
    if (this.sheets.length <= 1) return false;
    this.sheets.splice(index, 1);
    if (this.activeSheetIndex >= this.sheets.length) this.activeSheetIndex = this.sheets.length - 1;
    return true;
  }

  toJSON() {
    return {
      version: 1,
      title: this.title,
      sheets: this.sheets.map((s) => s.toJSON()),
      activeSheet: this.activeSheetIndex,
    };
  }

  // Throws WorkbookParseError for shapes that aren't plausibly a Lumen Sheet
  // document at all (null, wrong type, missing "sheets" array, missing/
  // unrecognized "version") rather than silently opening a blank workbook or
  // letting a TypeError escape deep inside Sheet.fromJSON — callers should
  // catch this and show a friendly "couldn't be opened" message. Individual
  // sheets/cells within an otherwise-plausible document are tolerated by
  // Sheet.fromJSON itself (see its comment) rather than rejected wholesale.
  static fromJSON(data) {
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      throw new WorkbookParseError('Document is not a Lumen Sheet workbook.');
    }
    if (typeof data.version !== 'number') {
      throw new WorkbookParseError('Missing or unrecognized document version.');
    }
    if (!Array.isArray(data.sheets)) {
      throw new WorkbookParseError('Document has no "sheets" array.');
    }
    const wb = new Workbook();
    wb.title = typeof data.title === 'string' ? data.title : 'Untitled spreadsheet';
    wb.sheets = data.sheets.map((s) => Sheet.fromJSON(s));
    if (wb.sheets.length === 0) wb.sheets = [new Sheet('Sheet1')];
    const activeSheet = typeof data.activeSheet === 'number' ? data.activeSheet : 0;
    wb.activeSheetIndex = Math.min(Math.max(0, activeSheet), wb.sheets.length - 1);
    wb.dirty = false;
    return wb;
  }
}

/** Thrown by Workbook.fromJSON() when the parsed JSON isn't a plausible
 * Lumen Sheet document shape (as opposed to a JSON.parse syntax error, which
 * main.js already turns into a friendly message before the renderer ever
 * sees this data). Callers should catch this alongside any other exception
 * from the parse and show the same friendly "couldn't be opened" message —
 * see openWithDirtyCheck() in renderer.js. */
export class WorkbookParseError extends Error {}

// ---------------------------------------------------------------------------
// Number formatting / display
// ---------------------------------------------------------------------------

/** True if a computed value should be treated as numeric for alignment/aggregation purposes. */
export function isNumericValue(v) {
  return typeof v === 'number';
}

function daysSinceEpochToDateStr(serial) {
  const ms = Math.round(serial) * 86400000;
  const d = new Date(ms);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Format a computed cell value for display according to its number format.
 * See README for exact semantics of each preset.
 */
export function formatValue(value, numberFormat) {
  if (isError(value)) return value.error;
  if (value === '' || value === null || value === undefined) return '';
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  const fmt = numberFormat || 'general';
  if (typeof value !== 'number') {
    if (fmt === 'date') return String(value); // literal date-like text passes through unchanged
    return String(value);
  }
  switch (fmt) {
    case 'number':
      return value.toFixed(2);
    case 'currency':
      return (value < 0 ? '-$' : '$') + Math.abs(value).toFixed(2);
    case 'percent':
      return (value * 100).toFixed(2) + '%';
    case 'date':
      return daysSinceEpochToDateStr(value);
    case 'general':
    default:
      return formatGeneralNumber(value);
  }
}

function formatGeneralNumber(value) {
  if (Number.isInteger(value)) return String(value);
  // Trim to a reasonable number of significant digits to avoid float noise.
  const rounded = Math.round(value * 1e10) / 1e10;
  return String(rounded);
}
