// refUtils.js — A1-style cell/range reference parsing & manipulation.
// Pure, stateless helpers shared by formulaEngine.js and grid.js.

/** Convert a 0-indexed column number to spreadsheet letters (0 -> "A", 26 -> "AA"). */
export function colToLetter(col) {
  let n = col + 1;
  let s = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    s = String.fromCharCode(65 + rem) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

/** Convert spreadsheet letters ("A", "AA") to a 0-indexed column number. */
export function letterToCol(letters) {
  let col = 0;
  const up = letters.toUpperCase();
  for (let i = 0; i < up.length; i++) {
    col = col * 26 + (up.charCodeAt(i) - 64);
  }
  return col - 1;
}

const CELL_REF_RE = /^(\$?)([A-Za-z]{1,3})(\$?)([0-9]+)$/;

/** Returns true if the given identifier string looks like a cell reference, e.g. "A1", "$B$12". */
export function isCellRefToken(str) {
  return CELL_REF_RE.test(str);
}

/** Parse a single cell reference string into { col, row, colLocked, rowLocked } (0-indexed), or null. */
export function parseCellRefStr(ref) {
  const m = CELL_REF_RE.exec(String(ref).trim());
  if (!m) return null;
  const [, colLock, letters, rowLock, rowStr] = m;
  return {
    col: letterToCol(letters),
    row: parseInt(rowStr, 10) - 1,
    colLocked: colLock === '$',
    rowLocked: rowLock === '$',
  };
}

/** Build a plain (unlocked) A1-style key from 0-indexed col/row, e.g. (0,0) -> "A1". */
export function cellKeyFromRC(col, row) {
  return `${colToLetter(col)}${row + 1}`;
}

/** Parse a range string ("A1:B10" or a single "A1") into { startCol, startRow, endCol, endRow }. */
export function parseRangeStr(rangeStr) {
  const parts = String(rangeStr).split(':');
  if (parts.length === 1) {
    const c = parseCellRefStr(parts[0]);
    if (!c) return null;
    return { startCol: c.col, startRow: c.row, endCol: c.col, endRow: c.row };
  }
  const a = parseCellRefStr(parts[0]);
  const b = parseCellRefStr(parts[1]);
  if (!a || !b) return null;
  return {
    startCol: Math.min(a.col, b.col),
    endCol: Math.max(a.col, b.col),
    startRow: Math.min(a.row, b.row),
    endRow: Math.max(a.row, b.row),
  };
}

/** Enumerate every cell key within a range (inclusive), row-major. */
export function* iterRangeKeys(range) {
  for (let r = range.startRow; r <= range.endRow; r++) {
    for (let c = range.startCol; c <= range.endCol; c++) {
      yield cellKeyFromRC(c, r);
    }
  }
}

/**
 * Shift a single ref string by (colOffset, rowOffset), respecting $ locks.
 * Returns { text, outOfBounds } where outOfBounds is true if the shifted
 * reference would land before column A / row 1 (caller may render this as #REF!).
 */
export function shiftRefString(ref, colOffset, rowOffset) {
  const parsed = parseCellRefStr(ref);
  if (!parsed) return { text: ref, outOfBounds: false };
  const newCol = parsed.colLocked ? parsed.col : parsed.col + colOffset;
  const newRow = parsed.rowLocked ? parsed.row : parsed.row + rowOffset;
  if (newCol < 0 || newRow < 0) return { text: '#REF!', outOfBounds: true };
  const colStr = (parsed.colLocked ? '$' : '') + colToLetter(newCol);
  const rowStr = (parsed.rowLocked ? '$' : '') + (newRow + 1);
  return { text: `${colStr}${rowStr}`, outOfBounds: false };
}

/** Shift a range string "A1:B10" the same way as shiftRefString, part by part. */
export function shiftRangeString(rangeStr, colOffset, rowOffset) {
  const parts = String(rangeStr).split(':');
  const shifted = parts.map((p) => shiftRefString(p, colOffset, rowOffset));
  const anyOOB = shifted.some((s) => s.outOfBounds);
  return { text: shifted.map((s) => s.text).join(':'), outOfBounds: anyOOB };
}
