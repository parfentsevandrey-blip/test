// Data-driven DOCX builder for the Netherlands tech-zones report.
// Premium "white paper" design system: Nimbus Roman (serif body) + URW Gothic Demi (display).
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType, PageBreak,
  Footer, Header, PageNumber, VerticalAlign, TabStopType, Tab, LeaderType, LineRuleType,
} = require("docx");

const DIR = __dirname;
const DATA = JSON.parse(fs.readFileSync(path.join(DIR, "report_data.json"), "utf8"));

// ---- palette (refined print tones) ----
const INK = "14213D";      // brand navy
const TEXT = "1E2A3A";     // single body text colour
const MUTED = "5B6472";    // secondary
const ORANGE = "C2410C";   // burnt sienna — chips / navigation accent
const BLUE = "1D4ED8";     // data centres
const PURPLE = "6D28D9";   // AI
const GREEN = "047857";    // science / R&D
const RULE = "D9DEE7";     // hairlines
const HAIR = "E6EAF1";     // ultra-thin table verticals
const PANEL = "EEF2F7";
const STAT = "F6F8FB";     // stat tiles + even zebra
const ROWHEAD = "EEF2F8";  // table row-header column
const HEADSEP = "24314F";  // tonal divider inside dark head band
const COVERSUB = "CBD5E1"; // subtitle over dark bands
const PAPER = "FFFFFF";
const CALLOUT_TINT = { [ORANGE]: "FBF4EC", [BLUE]: "EEF3FD", [GREEN]: "ECF6F1", [PURPLE]: "F3EFFB" };
const calloutTint = (a) => CALLOUT_TINT[a] || PANEL;

// Display: URW Gothic (geometric Demi) — renders premium in the PDF; always bold:true.
// Body: Times New Roman — universal (real Times in any Word; Liberation Serif in the PDF).
const HFONT = "URW Gothic";
const FONT = "Times New Roman";

const PAGE_W = 11906, PAGE_H = 16838;
const M = 1134;                        // body side margin (~20mm)
const CONTENT_W = PAGE_W - 2 * M;      // ~9638

// section numbers (shared by h1 headings and TOC)
let _n = 0; const H1NUM = {};
DATA.blocks.forEach((b) => { if (b.type === "h1") { _n++; H1NUM[b.text] = String(_n).padStart(2, "0"); } });

// ---------- image helpers ----------
function imgSize(file) {
  const buf = fs.readFileSync(file);
  if (buf[0] === 0x89 && buf[1] === 0x50) return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  let i = 2;
  while (i < buf.length) {
    if (buf[i] !== 0xff) { i++; continue; }
    const m = buf[i + 1];
    if (m >= 0xc0 && m <= 0xc3) return { h: buf.readUInt16BE(i + 5), w: buf.readUInt16BE(i + 7) };
    i += 2 + buf.readUInt16BE(i + 2);
  }
  return { w: 1200, h: 800 };
}
const ext = (f) => (f.toLowerCase().endsWith(".png") ? "png" : "jpg");

function image(file, widthPt, opts = {}) {
  const abs = path.join(DIR, file);
  const { w, h } = imgSize(abs);
  let dispW = widthPt, dispH = Math.round(dispW * h / w);
  if (opts.maxH && dispH > opts.maxH) { dispH = opts.maxH; dispW = Math.round(dispH * w / h); }
  const frame = opts.frame
    ? { top: { style: BorderStyle.SINGLE, size: opts.frame, color: opts.frameColor || RULE, space: 6 },
        bottom: { style: BorderStyle.SINGLE, size: opts.frame, color: opts.frameColor || RULE, space: 6 },
        left: { style: BorderStyle.SINGLE, size: opts.frame, color: opts.frameColor || RULE, space: 6 },
        right: { style: BorderStyle.SINGLE, size: opts.frame, color: opts.frameColor || RULE, space: 6 } }
    : undefined;
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: opts.before ?? 120, after: opts.after ?? 120 },
    keepNext: opts.keepNext !== false,
    border: frame,
    children: [new ImageRun({ type: ext(file), data: fs.readFileSync(abs), transformation: { width: dispW, height: dispH } })],
  });
}
const caption = (text) => new Paragraph({
  alignment: AlignmentType.CENTER, spacing: { after: 240 }, keepNext: true,
  children: [new TextRun({ text, italics: true, size: 17, color: MUTED, font: FONT })],
});

// ---------- text helpers ----------
function runsFromText(text, opts = {}) {
  return text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean).map((p) =>
    (p.startsWith("**") && p.endsWith("**"))
      ? new TextRun({ text: p.slice(2, -2), bold: true, size: opts.size ?? 21, color: INK, font: FONT })
      : new TextRun({ text: p, size: opts.size ?? 21, color: opts.color ?? TEXT, font: FONT }));
}
const para = (text, opts = {}) => new Paragraph({
  spacing: { after: opts.after ?? 140, line: 276, lineRule: LineRuleType.AUTO },
  alignment: AlignmentType.LEFT,
  children: runsFromText(text, opts),
});
const standfirst = (text) => new Paragraph({
  spacing: { before: 40, after: 160, line: 300, lineRule: LineRuleType.AUTO }, alignment: AlignmentType.LEFT,
  children: [new TextRun({ text: text.replace(/\*\*/g, ""), size: 24, color: INK, font: FONT })],
});
// unified caps label / eyebrow
const label = (text, color = ORANGE, opts = {}) => new Paragraph({
  spacing: { before: opts.before ?? 60, after: opts.after ?? 40 }, keepNext: true,
  border: opts.border,
  children: [new TextRun({ text, bold: true, allCaps: true, size: opts.size ?? 15, color, font: HFONT, characterSpacing: opts.cs ?? 120 })],
});

function heading(text, kind = "h1") {
  if (kind === "h1") {
    const num = H1NUM[text] || "";
    return [
      label(`Раздел ${num}`, ORANGE, { before: 460, after: 40, cs: 120, size: 15 }),
      new Paragraph({
        heading: HeadingLevel.HEADING_1, keepNext: true, keepLines: true,
        spacing: { before: 0, after: 0, line: 264, lineRule: LineRuleType.AUTO },
        children: [new TextRun({ text, bold: true, size: 40, color: INK, font: HFONT, characterSpacing: 4 })],
      }),
      new Paragraph({ spacing: { before: 60, after: 60 }, keepNext: true,
        border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: RULE, space: 6 } }, children: [] }),
    ];
  }
  if (kind === "h2") {
    return [new Paragraph({
      heading: HeadingLevel.HEADING_2, keepNext: true, keepLines: true,
      spacing: { before: 340, after: 90 }, indent: { left: 150 },
      border: { left: { style: BorderStyle.SINGLE, size: 24, color: ORANGE, space: 10 } },
      children: [new TextRun({ text, bold: true, size: 30, color: INK, font: HFONT, characterSpacing: 10 })],
    })];
  }
  return [new Paragraph({
    heading: HeadingLevel.HEADING_3, keepNext: true, keepLines: true,
    spacing: { before: 240, after: 40 },
    children: [
      new TextRun({ text: "◆  ", size: 18, color: ORANGE, font: HFONT, bold: true }),
      new TextRun({ text, bold: true, allCaps: true, size: 20, color: INK, font: HFONT, characterSpacing: 40 }),
    ],
  })];
}

const bullet = (text, color = ORANGE) => new Paragraph({
  spacing: { after: 100, line: 264, lineRule: LineRuleType.AUTO }, indent: { left: 340, hanging: 200 },
  children: [new TextRun({ text: "▪  ", color, size: 14, font: HFONT, bold: true }), ...runsFromText(text, { size: 21 })],
});

const noBorders = () => ({
  top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE }, left: { style: BorderStyle.NONE },
  right: { style: BorderStyle.NONE }, insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE },
});

function calloutBox(title, text, accent = ORANGE) {
  const bar = new TableCell({
    width: { size: 130, type: WidthType.DXA }, shading: { type: ShadingType.CLEAR, fill: accent, color: "auto" },
    borders: noBorders(), margins: { top: 0, bottom: 0, left: 0, right: 0 }, children: [new Paragraph({ children: [] })],
  });
  const body = new TableCell({
    width: { size: CONTENT_W - 130, type: WidthType.DXA }, shading: { type: ShadingType.CLEAR, fill: calloutTint(accent), color: "auto" },
    borders: noBorders(), margins: { top: 170, bottom: 170, left: 230, right: 200 },
    children: [
      ...(title ? [new Paragraph({ spacing: { after: 90 },
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: accent, space: 4 } },
        children: [new TextRun({ text: "◆  " + title, bold: true, allCaps: true, size: 17, color: accent, font: HFONT, characterSpacing: 50 })] })] : []),
      ...text.split("\n\n").map((t, i, a) => new Paragraph({ spacing: { after: i === a.length - 1 ? 0 : 90, line: 294, lineRule: LineRuleType.AUTO },
        children: runsFromText(t, { size: 20, color: TEXT }) })),
    ],
  });
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: [130, CONTENT_W - 130], borders: noBorders(),
    rows: [new TableRow({ cantSplit: true, children: [bar, body] })],
  });
}

function statStrip(items) {
  const n = items.length, colW = Math.floor(CONTENT_W / n);
  const cells = items.map((it) => new TableCell({
    width: { size: colW, type: WidthType.DXA }, verticalAlign: VerticalAlign.CENTER,
    margins: { top: 180, bottom: 160, left: 110, right: 110 }, shading: { type: ShadingType.CLEAR, fill: STAT, color: "auto" },
    borders: { top: { style: BorderStyle.SINGLE, size: 16, color: it.color || ORANGE },
      bottom: { style: BorderStyle.NONE }, left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE } },
    children: [
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 70 },
        children: [new TextRun({ text: String(it.value).replace(/ /g, " "), bold: true, size: 34, color: it.color || ORANGE, font: HFONT, characterSpacing: -4 })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { line: 230, lineRule: LineRuleType.AUTO },
        children: [new TextRun({ text: it.label, allCaps: true, size: 15, color: MUTED, font: FONT, characterSpacing: 24 })] }),
    ],
  }));
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: items.map(() => colW),
    borders: { top: { style: BorderStyle.NONE }, left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE }, insideHorizontal: { style: BorderStyle.NONE },
      insideVertical: { style: BorderStyle.SINGLE, size: 8, color: PAPER } },
    rows: [new TableRow({ cantSplit: true, children: cells })],
  });
}

function dataTable(headers, rows, widths, ribbon = ORANGE) {
  const headCells = headers.map((h, i) => new TableCell({
    width: { size: widths[i], type: WidthType.DXA }, shading: { type: ShadingType.CLEAR, fill: INK, color: "auto" },
    verticalAlign: VerticalAlign.CENTER, margins: { top: 120, bottom: 120, left: 150, right: 150 },
    borders: { bottom: { style: BorderStyle.SINGLE, size: 16, color: ribbon },
      right: i < headers.length - 1 ? { style: BorderStyle.SINGLE, size: 4, color: HEADSEP } : { style: BorderStyle.NONE } },
    children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: PAPER, size: 20, font: HFONT, characterSpacing: 14 })] })],
  }));
  const bodyRows = rows.map((r, ri) => new TableRow({
    children: r.map((c, i) => new TableCell({
      width: { size: widths[i], type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, fill: i === 0 ? ROWHEAD : (ri % 2 ? STAT : PAPER), color: "auto" },
      margins: { top: 100, bottom: 100, left: 150, right: 150 }, verticalAlign: VerticalAlign.CENTER,
      borders: i === 0 ? { right: { style: BorderStyle.SINGLE, size: 4, color: RULE } } : undefined,
      children: [new Paragraph({ spacing: { line: 252, after: 0, lineRule: LineRuleType.AUTO },
        children: i === 0
          ? [new TextRun({ text: String(c).replace(/\*\*/g, ""), bold: true, size: 19, color: INK, font: FONT })]
          : runsFromText(String(c), { size: 19, color: TEXT }) })],
    })),
  }));
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: widths,
    borders: { top: { style: BorderStyle.NONE }, left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE }, insideHorizontal: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      insideVertical: { style: BorderStyle.SINGLE, size: 4, color: HAIR } },
    rows: [new TableRow({ tableHeader: true, cantSplit: true, children: headCells }), ...bodyRows],
  });
}

// zone category by name keyword
function zoneCat(name) {
  const n = name.toLowerCase();
  if (n.includes("asml")) return { cat: "Полупроводники · литография", color: INK };
  if (n.includes("high tech")) return { cat: "Полупроводники · R&D-кампус", color: INK };
  if (n.includes("неймеген")) return { cat: "Полупроводники · производство", color: INK };
  if (n.includes("science park") || n.includes("амстердам")) return { cat: "Дата-центры · интернет-узел", color: BLUE };
  if (n.includes("эмсхавен")) return { cat: "Дата-центры · гиперскейл", color: BLUE };
  if (n.includes("agriport")) return { cat: "Дата-центры · гиперскейл", color: BLUE };
  if (n.includes("делфт")) return { cat: "ИИ и кванты · наука", color: PURPLE };
  return { cat: "Технологическая зона", color: ORANGE };
}

function zoneCard(z) {
  const cc = zoneCat(z.name);
  const kids = [];
  kids.push(new Paragraph({ spacing: { before: 300, after: 20 }, keepNext: true,
    children: [new TextRun({ text: "◆  " + cc.cat.toUpperCase(), bold: true, allCaps: true, size: 16, color: cc.color, font: HFONT, characterSpacing: 30 })] }));
  kids.push(new Paragraph({ spacing: { after: 80 }, keepNext: true,
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: cc.color } },
    children: [new TextRun({ text: z.name, bold: true, size: 30, color: INK, font: HFONT, characterSpacing: 2 })] }));
  kids.push(new Paragraph({ spacing: { after: 120 }, keepNext: true, children: [
    new TextRun({ text: `\u{1F4CD} `, size: 18 }),
    new TextRun({ text: z.city, bold: true, size: 19, color: "3A4656", font: FONT }),
    new TextRun({ text: `, ${z.region}`, size: 19, color: MUTED, font: FONT }),
    new TextRun({ text: `    ${z.coords}`, size: 17, color: MUTED, italics: true, font: FONT }),
  ] }));
  if (z.image) { kids.push(image(z.image, 460, { maxH: z.maxH || 350, frame: 4, frameColor: RULE })); if (z.caption) kids.push(caption(z.caption)); }
  kids.push(para(z.summary, { after: 140 }));
  if (z.companies && z.companies.length) {
    kids.push(label("Ключевые резиденты", ORANGE, { before: 140, after: 60, cs: 24, size: 16,
      border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } } }));
    z.companies.forEach((c) => kids.push(bullet(c, ORANGE)));
  }
  if (z.facts && z.facts.length) {
    kids.push(label("Факты и цифры", BLUE, { before: 140, after: 60, cs: 24, size: 16,
      border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } } }));
    z.facts.forEach((f) => kids.push(bullet(f, BLUE)));
  }
  return kids;
}

const spacer12 = () => new Paragraph({ spacing: { after: 240 }, children: [] });

// ---- render blocks ----
let _stand = false; // set true right after an h1 so the next para becomes standfirst
function renderBlock(b) {
  switch (b.type) {
    case "pagebreak": return [new Paragraph({ spacing: { after: 60 }, children: [] })];
    case "h1": _stand = true; return heading(b.text, "h1");
    case "h2": return heading(b.text, "h2");
    case "h3": return heading(b.text, "h3");
    case "para": { const p = _stand ? standfirst(b.text) : para(b.text); _stand = false; return [p]; }
    case "paras": { const out = b.items.map((t, i) => (_stand && i === 0) ? standfirst(t) : para(t)); _stand = false; return out; }
    case "bullets": _stand = false; return b.items.map((t) => bullet(t, ({ orange: ORANGE, blue: BLUE, green: GREEN, purple: PURPLE })[b.color] || b.color || ORANGE));
    case "image": { _stand = false; const out = [image(b.file, b.widthPt || 460, { maxH: b.maxH || 300, frame: 4, frameColor: RULE })]; if (b.caption) out.push(caption(b.caption)); return out; }
    case "fullimage": { _stand = false; const out = [image(b.file, b.widthPt || 470, { before: 60, maxH: b.maxH || 300, frame: 4, frameColor: RULE })]; if (b.caption) out.push(caption(b.caption)); return out; }
    case "callout": _stand = false; return [calloutBox(b.title, b.text, b.accent || ORANGE), spacer12()];
    case "statstrip": _stand = false; return [statStrip(b.items), spacer12()];
    case "table": _stand = false; return [dataTable(b.headers, b.rows, b.widths, b.ribbon || ORANGE), spacer12()];
    case "zonecard": _stand = false; return zoneCard(b);
    case "spacer": return [new Paragraph({ spacing: { after: b.after || 120 }, children: [] })];
    case "sources": _stand = false; return b.items.map((s, i) => new Paragraph({
      spacing: { after: 60 }, indent: { left: 360, hanging: 300 },
      children: [new TextRun({ text: `${i + 1}. `, bold: true, size: 17, color: ORANGE, font: HFONT }),
        new TextRun({ text: s, size: 17, color: TEXT, font: FONT })] }));
    default: return [para(JSON.stringify(b))];
  }
}

// ---------- full-bleed cover (own section, margin 0) ----------
function bandTable(children, fill, cellM) {
  return new Table({
    width: { size: PAGE_W, type: WidthType.DXA }, columnWidths: [PAGE_W], borders: noBorders(),
    rows: [new TableRow({ children: [new TableCell({
      width: { size: PAGE_W, type: WidthType.DXA }, shading: fill ? { type: ShadingType.CLEAR, fill, color: "auto" } : undefined,
      borders: noBorders(), margins: cellM, children,
    })] })],
  });
}
function coverChildren(meta) {
  const masthead = bandTable([
    new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: meta.kicker, bold: true, allCaps: true, size: 18, color: ORANGE, font: HFONT, characterSpacing: 140 })] }),
    new Paragraph({ spacing: { after: 0, line: 860, lineRule: LineRuleType.EXACT }, children: [new TextRun({ text: meta.title, bold: true, size: 80, color: PAPER, font: HFONT, characterSpacing: 6 })] }),
    new Paragraph({ spacing: { before: 160, line: 300, lineRule: LineRuleType.AUTO }, children: [new TextRun({ text: meta.subtitle, size: 26, color: COVERSUB, font: FONT })] }),
  ], INK, { top: 560, bottom: 460, left: M, right: M });

  const content = bandTable([
    new Paragraph({ spacing: { after: 60 }, children: [] }),
    image(meta.cover, 466, { before: 60, after: 120, frame: 6, frameColor: INK }),
    caption(meta.coverCaption || ""),
    new Paragraph({ spacing: { before: 700, after: 0 }, indent: { right: 4600 },
      border: { top: { style: BorderStyle.SINGLE, size: 12, color: ORANGE, space: 8 } },
      children: [new TextRun({ text: meta.tagline, italics: true, size: 26, color: INK, font: FONT })] }),
  ], null, { top: 380, bottom: 0, left: M, right: M });

  return [masthead, content];
}
const coverColophon = (meta) => new Footer({ children: [bandTable([
  new Paragraph({ tabStops: [{ type: TabStopType.RIGHT, position: PAGE_W - 2 * M }], children: [
    new TextRun({ text: "АНАЛИТИЧЕСКИЙ ОТЧЁТ", bold: true, size: 18, color: PAPER, font: HFONT, characterSpacing: 60 }),
    new TextRun({ children: [new Tab()], font: HFONT }),
    new TextRun({ text: `${meta.date.toUpperCase()} · AMS-IX`, bold: true, size: 18, color: COVERSUB, font: HFONT, characterSpacing: 60 }),
  ] }),
], INK, { top: 210, bottom: 210, left: M, right: M })] });

// ---------- TOC ----------
let _toc = 0;
function tocEntry(e) {
  if (e.level === 2) {
    return new Paragraph({ spacing: { after: 40, before: 20 }, indent: { left: 620 },
      tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.DOT }],
      children: [new TextRun({ text: e.title, size: 20, color: TEXT, font: FONT }),
        new TextRun({ children: [new Tab()], font: FONT }),
        new TextRun({ text: String(e.page), size: 20, color: MUTED, font: FONT })] });
  }
  _toc++;
  const num = String(_toc).padStart(2, "0");
  return new Paragraph({ spacing: { before: 170, after: 40 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: RULE, space: 6 } },
    tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.DOT }],
    children: [
      new TextRun({ text: num + "   ", bold: true, size: 23, color: ORANGE, font: HFONT }),
      new TextRun({ text: e.title, bold: true, size: 23, color: INK, font: HFONT, characterSpacing: 2 }),
      new TextRun({ children: [new Tab()], font: FONT }),
      new TextRun({ text: String(e.page), size: 22, color: ORANGE, bold: true, font: HFONT }),
    ] });
}

// ---------- assemble body (section 2) ----------
const body = [];
body.push(label("Навигация по отчёту", ORANGE, { before: 200, after: 40, cs: 120, size: 15 }));
body.push(new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: "Содержание", bold: true, size: 40, color: INK, font: HFONT, characterSpacing: 4 })] }));
body.push(new Paragraph({ spacing: { before: 40, after: 120 }, border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: RULE, space: 6 } }, children: [] }));
(DATA.tocStatic || []).forEach((e) => body.push(tocEntry(e)));
body.push(new Paragraph({ children: [new PageBreak()] }));
DATA.blocks.forEach((b) => { body.push(...renderBlock(b)); });

const runHead = new Header({ children: [new Paragraph({
  spacing: { after: 0 }, tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W }],
  border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 5 } },
  children: [
    new TextRun({ text: "ТЕХНОЛОГИЧЕСКОЕ СЕРДЦЕ ЕВРОПЫ", bold: true, size: 13, color: INK, font: HFONT, characterSpacing: 80 }),
    new TextRun({ children: [new Tab()], font: HFONT }),
    new TextRun({ text: "НИДЕРЛАНДЫ", bold: true, size: 13, color: MUTED, font: HFONT, characterSpacing: 80 }),
  ] })] });
const runFoot = new Footer({ children: [new Paragraph({
  spacing: { before: 0 }, tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W }],
  border: { top: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 6 } },
  children: [
    new TextRun({ text: "Технологическое сердце Европы", bold: true, size: 14, color: MUTED, font: HFONT, characterSpacing: 40 }),
    new TextRun({ children: [new Tab()], font: HFONT }),
    new TextRun({ children: [PageNumber.CURRENT], bold: true, size: 18, color: INK, font: HFONT }),
    new TextRun({ text: "  /  ", size: 15, color: MUTED, font: HFONT }),
    new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 15, color: MUTED, font: HFONT }),
  ] })] });

const doc = new Document({
  creator: "Аналитический отчёт", title: DATA.meta.title,
  styles: {
    default: { document: { run: { font: FONT, size: 21, color: TEXT } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: HFONT, size: 40, bold: true, color: INK }, paragraph: { spacing: { before: 0, after: 0 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: HFONT, size: 30, bold: true, color: INK }, paragraph: { spacing: { before: 340, after: 90 }, outlineLevel: 1 } },
    ],
  },
  sections: [
    { // cover — full bleed
      properties: { page: { size: { width: PAGE_W, height: PAGE_H }, margin: { top: 0, bottom: 0, left: 0, right: 0, header: 0, footer: 0 } } },
      headers: { default: new Header({ children: [new Paragraph({ children: [] })] }) },
      footers: { default: coverColophon(DATA.meta) },
      children: coverChildren(DATA.meta),
    },
    { // body
      properties: { page: { size: { width: PAGE_W, height: PAGE_H }, margin: { top: 1080, bottom: 1040, left: M, right: M, header: 620, footer: 560 } } },
      headers: { default: runHead },
      footers: { default: runFoot },
      children: body,
    },
  ],
});

Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(path.join(DIR, "report.docx"), buf); console.log("WROTE report.docx", buf.length, "bytes"); });
