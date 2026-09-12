const fs = require("fs");
const D = require("docx");
const {
  Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType,
  ShadingType, AlignmentType, BorderStyle, HeadingLevel, PageBreak,
  convertMillimetersToTwip: mm, VerticalAlign, LineRuleType,
} = D;

// ── геометрия страницы ────────────────────────────────────────────────────
const PAGE = { w: mm(210), h: mm(297), mt: mm(20), mb: mm(20), ml: mm(22), mr: mm(22) };
const CW = PAGE.w - PAGE.ml - PAGE.mr;          // ширина полосы набора, DXA
const IMG_PX = 627;                              // 166 мм при 96 dpi

// ── палитра ───────────────────────────────────────────────────────────────
const C = {
  ink: "0B0B0B", ink2: "52514E", muted: "898781",
  accent: "104281", accent2: "256ABF", light: "86B6EF",
  rule: "C3C2B7", hair: "E1E0D9",
  thead: "E8EFF9", zebra: "F6F8FC", callout: "F2F6FD", warn: "FDF4E8",
};
const FS = "Calibri";      // основной
const FH = "Cambria";      // заголовочный

const headings = [];   // реестр заголовков для оглавления

// ── абзацы ────────────────────────────────────────────────────────────────
const runs = (t, o = {}) =>
  (Array.isArray(t) ? t : [t]).map((x) =>
    typeof x === "string"
      ? new TextRun({ text: x, font: o.font || FS, size: o.size || 22, color: o.color || C.ink, bold: o.bold, italics: o.italics })
      : new TextRun({ font: o.font || FS, size: o.size || 22, color: o.color || C.ink, ...x })
  );

const p = (t, o = {}) =>
  new Paragraph({
    children: runs(t, o),
    alignment: o.align || AlignmentType.JUSTIFIED,
    spacing: { before: o.before ?? 0, after: o.after ?? 140, line: o.line ?? 288, lineRule: LineRuleType.AUTO },
    indent: o.indent,
    keepNext: o.keepNext,
    border: o.border,
    shading: o.shading ? { type: ShadingType.CLEAR, fill: o.shading, color: "auto" } : undefined,
  });

const h1 = (t) => (headings.push({ lvl: 1, t }),
  new Paragraph({
    heading: HeadingLevel.HEADING_1, pageBreakBefore: true,
    spacing: { before: 0, after: 260, line: 276, lineRule: LineRuleType.AUTO },
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: C.accent, space: 8 } },
    children: [new TextRun({ text: t, font: FH, size: 40, bold: true, color: C.accent })],
  }));

const h2 = (t) => (headings.push({ lvl: 2, t }),
  new Paragraph({
    heading: HeadingLevel.HEADING_2, keepNext: true,
    spacing: { before: 340, after: 130, line: 276, lineRule: LineRuleType.AUTO },
    children: [new TextRun({ text: t, font: FH, size: 27, bold: true, color: C.accent })],
  }));

const h3 = (t) => (headings.push({ lvl: 3, t }),
  new Paragraph({
    heading: HeadingLevel.HEADING_3, keepNext: true,
    spacing: { before: 250, after: 100, line: 276, lineRule: LineRuleType.AUTO },
    children: [new TextRun({ text: t, font: FH, size: 23, bold: true, color: C.ink2 })],
  }));

const lead = (t) =>
  new Paragraph({
    children: runs(t, { size: 23, color: C.ink2, italics: true }),
    alignment: AlignmentType.JUSTIFIED,
    spacing: { before: 0, after: 220, line: 300, lineRule: LineRuleType.AUTO },
  });

const bullet = (t, lvl = 0) =>
  new Paragraph({
    children: runs(t), numbering: { reference: "dots", level: lvl },
    alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 280, lineRule: LineRuleType.AUTO },
  });

const num = (t, lvl = 0) =>
  new Paragraph({
    children: runs(t), numbering: { reference: "nums", level: lvl },
    alignment: AlignmentType.JUSTIFIED, spacing: { after: 80, line: 280, lineRule: LineRuleType.AUTO },
  });

// врезка
const callout = (title, body, fill = C.callout) => {
  const cell = new TableCell({
    width: { size: CW, type: WidthType.DXA },
    margins: { top: 150, bottom: 150, left: 220, right: 220 },
    shading: { type: ShadingType.CLEAR, fill, color: "auto" },
    borders: {
      top: { style: BorderStyle.NONE, size: 0, color: "auto" },
      bottom: { style: BorderStyle.NONE, size: 0, color: "auto" },
      right: { style: BorderStyle.NONE, size: 0, color: "auto" },
      left: { style: BorderStyle.SINGLE, size: 18, color: C.accent2 },
    },
    children: [
      ...(title ? [p(title, { bold: true, color: C.accent, size: 21, after: 70, align: AlignmentType.LEFT })] : []),
      ...(Array.isArray(body) ? body : [body]).map((b, i, a) =>
        typeof b === "string" ? p(b, { size: 21, after: i === a.length - 1 ? 0 : 90 }) : b),
    ],
  });
  return new Table({
    columnWidths: [CW], width: { size: CW, type: WidthType.DXA },
    borders: { insideHorizontal: { style: BorderStyle.NONE, size: 0, color: "auto" }, insideVertical: { style: BorderStyle.NONE, size: 0, color: "auto" } },
    rows: [new TableRow({ cantSplit: true, children: [cell] })],
  });
};

// ── таблицы ───────────────────────────────────────────────────────────────
// cols: [{t:"Заголовок", w:доля, a:"l|c|r"}], rows: [[...ячейки]]
function table(cols, rows, opt = {}) {
  const totalW = opt.width || CW;
  const sum = cols.reduce((s, c) => s + c.w, 0);
  const widths = cols.map((c) => Math.round((c.w / sum) * totalW));
  widths[widths.length - 1] = totalW - widths.slice(0, -1).reduce((a, b) => a + b, 0);
  const fsz = opt.size || 17;
  const alignOf = (a) => a === "r" ? AlignmentType.RIGHT : a === "c" ? AlignmentType.CENTER : AlignmentType.LEFT;

  const head = new TableRow({
    tableHeader: true,
    children: cols.map((c, i) => new TableCell({
      width: { size: widths[i], type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, fill: C.thead, color: "auto" },
      margins: { top: 70, bottom: 70, left: 90, right: 90 },
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({
        children: [new TextRun({ text: c.t, font: FS, size: fsz, bold: true, color: C.accent })],
        alignment: alignOf(c.a), spacing: { after: 0, line: 240, lineRule: LineRuleType.AUTO },
      })],
    })),
  });

  const body = rows.map((r, ri) => new TableRow({
    children: r.map((cell, i) => {
      const o = typeof cell === "object" && cell !== null && !Array.isArray(cell) ? cell : { v: cell };
      return new TableCell({
        width: { size: widths[i], type: WidthType.DXA },
        margins: { top: 55, bottom: 55, left: 90, right: 90 },
        verticalAlign: VerticalAlign.CENTER,
        shading: { type: ShadingType.CLEAR, fill: o.fill || (ri % 2 ? C.zebra : "FFFFFF"), color: "auto" },
        children: [new Paragraph({
          children: [new TextRun({ text: String(o.v), font: FS, size: fsz, bold: o.b, color: o.c || C.ink })],
          alignment: alignOf(o.a || cols[i].a), spacing: { after: 0, line: 240, lineRule: LineRuleType.AUTO },
        })],
      });
    }),
  }));

  const bd = (sz, col) => ({ style: BorderStyle.SINGLE, size: sz, color: col });
  return new Table({
    columnWidths: widths, width: { size: totalW, type: WidthType.DXA },
    borders: {
      top: bd(10, C.accent), bottom: bd(10, C.accent),
      left: { style: BorderStyle.NONE, size: 0, color: "auto" },
      right: { style: BorderStyle.NONE, size: 0, color: "auto" },
      insideHorizontal: bd(4, C.hair),
      insideVertical: { style: BorderStyle.NONE, size: 0, color: "auto" },
    },
    rows: [head, ...body],
  });
}

// ── иллюстрации ───────────────────────────────────────────────────────────
const DIMS = JSON.parse(fs.readFileSync(__dirname + "/imgdims.json", "utf8"));
let figNo = 0;
function figure(file, caption, opt = {}) {
  const [w, h] = DIMS[file];
  const px = opt.px || IMG_PX;
  figNo += 1;
  return [
    new Paragraph({
      children: [new ImageRun({
        type: "png", data: fs.readFileSync(__dirname + "/" + file),
        transformation: { width: px, height: Math.round((h / w) * px) },
      })],
      alignment: AlignmentType.CENTER, spacing: { before: 160, after: 60 },
      keepNext: true,
    }),
    new Paragraph({
      children: [
        new TextRun({ text: `Рис. ${figNo}. `, font: FS, size: 17, bold: true, color: C.accent }),
        new TextRun({ text: caption, font: FS, size: 17, color: C.ink2 }),
      ],
      alignment: AlignmentType.LEFT, spacing: { after: 260, line: 240, lineRule: LineRuleType.AUTO },
    }),
  ];
}
const tabNoRef = { n: 0 };
const tcap = (t) => {
  tabNoRef.n += 1;
  return new Paragraph({
    children: [
      new TextRun({ text: `Табл. ${tabNoRef.n}. `, font: FS, size: 17, bold: true, color: C.accent }),
      new TextRun({ text: t, font: FS, size: 17, color: C.ink2 }),
    ],
    spacing: { before: 200, after: 90, line: 240, lineRule: LineRuleType.AUTO }, keepNext: true,
  });
};
const note = (t) => new Paragraph({
  children: [new TextRun({ text: t, font: FS, size: 16, color: C.muted, italics: true })],
  spacing: { before: 60, after: 200, line: 230, lineRule: LineRuleType.AUTO }, alignment: AlignmentType.LEFT,
});
const spacer = (n = 200) => new Paragraph({ children: [], spacing: { after: n } });
const pbreak = () => new Paragraph({ children: [new PageBreak()] });

module.exports = { D, PAGE, CW, IMG_PX, C, FS, FH, headings, p, h1, h2, h3, lead, bullet, num,
  callout, table, figure, tcap, note, spacer, pbreak, runs };
