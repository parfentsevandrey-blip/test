// Разбор книги .xlsx в ТО ЖЕ семантическое дерево, что и normalizeSpreadsheetML()
// из check_workbook.mjs. Нужен ровно для одного: сравнить книгу до миграции
// формата и после неё. Байты у них общего не имеют — смысл обязан совпадать.
//
// Это НЕ полноценный читатель xlsx: он понимает ровно то, что кладёт наш
// сборщик (inline-строки, cellXfs, pane, mergeCells, hyperlinks, table,
// colorScale). Чужой файл он читать не обязан.

import zlib from "node:zlib";
import { parseXml, colName, maskDates } from "./check_workbook.mjs";

/* ── zip → Map<путь, текст> ────────────────────────────────────────────────
   Разбираем по ЦЕНТРАЛЬНОМУ КАТАЛОГУ: локальные заголовки могут врать о
   размерах, когда пишущая сторона использует data descriptor. */
export function unzipParts(buf) {
  const eocd = findEocd(buf);
  if (eocd < 0) throw new Error("не найден EOCD: это не zip");
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const parts = new Map();
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error("битая запись каталога #" + i);
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    const compSize = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const cmtLen = buf.readUInt16LE(p + 32);
    const lho = buf.readUInt32LE(p + 42);
    const name = buf.toString("utf8", p + 46, p + 46 + nameLen);
    // данные в локальном заголовке начинаются после его собственных полей
    const lNameLen = buf.readUInt16LE(lho + 26);
    const lExtraLen = buf.readUInt16LE(lho + 28);
    const start = lho + 30 + lNameLen + lExtraLen;
    const raw = buf.subarray(start, start + compSize);
    const data = method === 0 ? raw : zlib.inflateRawSync(raw);
    if (zlib.crc32 && zlib.crc32(data) !== crc) throw new Error("CRC не сошёлся: " + name);
    parts.set(name, data.toString("utf8"));
    p += 46 + nameLen + extraLen + cmtLen;
  }
  return parts;
}

function findEocd(buf) {
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 66000; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) return i;
  }
  return -1;
}

/* ── вспомогательное ─────────────────────────────────────────────────────── */
const kid = (n, name) => (n.children || []).find((c) => c.name === name);
const kids = (n, name) => (n.children || []).filter((c) => c.name === name);
const text = (n) => (n ? (n.text || "") : "");
const upColor = (c) => {
  if (!c) return null;
  const h = String(c).replace(/^#/, "").toUpperCase();
  return "#" + (h.length === 8 ? h.slice(2) : h).slice(0, 6);
};
const EMPTY = Object.freeze({ v: null, t: null, fmt: null, href: null, fill: null, bold: false });

// «A5» -> {col: 1, row: 5}
function parseRef(ref) {
  const m = /^([A-Z]+)(\d+)$/.exec(ref || "");
  if (!m) return null;
  let c = 0;
  for (const ch of m[1]) c = c * 26 + (ch.charCodeAt(0) - 64);
  return { col: c, row: +m[2] };
}

/* ── styles.xml -> массив описаний по индексу cellXfs ─────────────────────── */
function parseStyles(xml) {
  if (!xml) return [];
  const root = kid(parseXml(xml), "styleSheet");
  if (!root) return [];
  const numFmts = {};
  const nf = kid(root, "numFmts");
  if (nf) for (const f of kids(nf, "numFmt")) numFmts[f.attrs.numFmtId] = f.attrs.formatCode;

  const fonts = kids(kid(root, "fonts") || { children: [] }, "font")
    .map((f) => ({ bold: !!kid(f, "b") }));
  const fills = kids(kid(root, "fills") || { children: [] }, "fill").map((f) => {
    const pf = kid(f, "patternFill");
    if (!pf || pf.attrs.patternType === "none") return null;
    const fg = kid(pf, "fgColor");
    return fg ? upColor(fg.attrs.rgb) : null;
  });

  return kids(kid(root, "cellXfs") || { children: [] }, "xf").map((xf) => ({
    fmt: numFmts[xf.attrs.numFmtId] || null,
    fill: xf.attrs.applyFill === "1" ? (fills[+xf.attrs.fillId] || null) : null,
    bold: xf.attrs.applyFont === "1" ? !!(fonts[+xf.attrs.fontId] || {}).bold : false,
  }));
}

/* ── лист ─────────────────────────────────────────────────────────────────── */
function parseSheet(name, xml, relsXml, tableXml) {
  const ws = kid(parseXml(xml), "worksheet");
  if (!ws) throw new Error("нет <worksheet> в листе " + name);

  // гиперссылки: ref -> адрес (через rels листа)
  const targets = {};
  if (relsXml) {
    const rels = kid(parseXml(relsXml), "Relationships");
    for (const r of kids(rels || { children: [] }, "Relationship")) targets[r.attrs.Id] = r.attrs.Target;
  }
  const hrefs = {};
  const hl = kid(ws, "hyperlinks");
  if (hl) for (const h of kids(hl, "hyperlink")) hrefs[h.attrs.ref] = targets[h.attrs["r:id"]] || null;

  // ширины: в xlsx они в символах, дерево хранит отношение к первой колонке
  const widthsRaw = [];
  const colsEl = kid(ws, "cols");
  if (colsEl) {
    for (const c of kids(colsEl, "col")) {
      for (let i = +c.attrs.min; i <= +c.attrs.max; i++) widthsRaw[i - 1] = parseFloat(c.attrs.width);
    }
  }
  const base = widthsRaw[0];
  const colWidths = widthsRaw.map((w) => (w == null || !base ? null : Math.round((w / base) * 100) / 100));

  // заморозка
  let freeze = null;
  const pane = kid(kid(kid(ws, "sheetViews") || { children: [] }, "sheetView") || { children: [] }, "pane");
  if (pane) freeze = { rows: +(pane.attrs.ySplit || 0), cols: +(pane.attrs.xSplit || 0) };

  const merges = kids(kid(ws, "mergeCells") || { children: [] }, "mergeCell").map((m) => m.attrs.ref);

  // автофильтр: либо свой, либо диапазон таблицы
  let autoFilter = null;
  const af = kid(ws, "autoFilter");
  if (af) autoFilter = af.attrs.ref;
  else if (tableXml) {
    const t = kid(parseXml(tableXml), "table");
    if (t) autoFilter = t.attrs.ref;
  }

  const condFormats = kids(ws, "conditionalFormatting").map((cf) => {
    const rule = kid(cf, "cfRule");
    const cs = rule && kid(rule, "colorScale");
    return {
      ref: cf.attrs.sqref,
      type: rule ? rule.attrs.type : null,
      colors: cs ? kids(cs, "color").map((c) => upColor(c.attrs.rgb)) : [],
    };
  });

  return { name, colWidths, freeze, autoFilter, merges, condFormats, ws, hrefs };
}

/* ── публичное: части книги -> дерево ─────────────────────────────────────── */
export function partsToTree(parts, styles) {
  const wb = kid(parseXml(parts.get("xl/workbook.xml")), "workbook");
  const rels = kid(parseXml(parts.get("xl/_rels/workbook.xml.rels")), "Relationships");
  const relTarget = {};
  for (const r of kids(rels || { children: [] }, "Relationship")) relTarget[r.attrs.Id] = r.attrs.Target;
  const xfs = styles || parseStyles(parts.get("xl/styles.xml"));

  const sheets = kids(kid(wb, "sheets"), "sheet").map((sh) => {
    const target = relTarget[sh.attrs["r:id"]];
    const path = "xl/" + target.replace(/^\/?xl\//, "");
    const file = path.split("/").pop();
    const relsPath = "xl/worksheets/_rels/" + file + ".rels";
    const no = (/sheet(\d+)\.xml$/.exec(file) || [])[1];
    const meta = parseSheet(sh.attrs.name, parts.get(path), parts.get(relsPath),
      parts.get("xl/tables/table" + no + ".xml"));

    // ячейки
    const rows = [];
    for (const r of kids(kid(meta.ws, "sheetData") || { children: [] }, "row")) {
      const idx = +r.attrs.r - 1;
      const cells = [];
      for (const c of kids(r, "c")) {
        const ref = parseRef(c.attrs.r);
        const st = xfs[+(c.attrs.s || 0)] || {};
        let v = null, t = null;
        if (c.attrs.t === "inlineStr") {
          v = maskDates(text(kid(kid(c, "is"), "t")).replace(/\s+$/, ""));
          t = "s";
        } else {
          const raw = text(kid(c, "v"));
          if (raw !== "") { v = Number(raw); t = "n"; }
        }
        const cell = v === null
          ? { ...EMPTY }
          : { v, t, fmt: st.fmt || null, href: meta.hrefs[c.attrs.r] || null, fill: st.fill || null, bold: !!st.bold };
        cells[ref.col - 1] = cell;
      }
      for (let i = 0; i < cells.length; i++) if (!cells[i]) cells[i] = { ...EMPTY };
      rows[idx] = cells;
    }
    for (let i = 0; i < rows.length; i++) if (!rows[i]) rows[i] = [];

    return {
      name: meta.name,
      colWidths: meta.colWidths,
      freeze: meta.freeze,
      autoFilter: meta.autoFilter,
      merges: meta.merges,
      rows,
      condFormats: meta.condFormats,
    };
  });

  return { sheets };
}

export { colName };
