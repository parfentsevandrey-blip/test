// Data-driven DOCX builder for the Netherlands tech-zones report.
// Reads report_data.json (a list of content blocks) and assets/, emits report.docx
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType, PageBreak,
  Footer, Header, PageNumber, TableOfContents, ExternalHyperlink, PositionalTab,
  PositionalTabAlignment, PositionalTabLeader, VerticalAlign, TabStopType, TabStopPosition,
  Tab, LeaderType,
} = require("docx");

const DIR = __dirname;
const DATA = JSON.parse(fs.readFileSync(path.join(DIR, "report_data.json"), "utf8"));

// ---- palette ----
const INK = "14213D";       // deep navy
const ORANGE = "EA580C";    // chips
const BLUE = "2563EB";      // data centers
const PURPLE = "7C3AED";    // AI
const GREEN = "059669";     // research
const MUTED = "5B6472";
const LIGHT = "F1F5F9";
const RULE = "D9DEE7";
const GOLD = "D9A521";

const FONT = "Calibri";
const HFONT = "Calibri";

// A4 content width in DXA (page 11906 - margins 2*1080 = ~9746)
const CONTENT_W = 9360;

function imgSize(file) {
  // read PNG/JPEG dimensions
  const buf = fs.readFileSync(file);
  if (buf[0] === 0x89 && buf[1] === 0x50) { // PNG
    return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  }
  // JPEG
  let i = 2;
  while (i < buf.length) {
    if (buf[i] !== 0xff) { i++; continue; }
    const m = buf[i + 1];
    if (m >= 0xc0 && m <= 0xc3) {
      return { h: buf.readUInt16BE(i + 5), w: buf.readUInt16BE(i + 7) };
    }
    i += 2 + buf.readUInt16BE(i + 2);
  }
  return { w: 1200, h: 800 };
}

function ext(file) {
  return file.toLowerCase().endsWith(".png") ? "png" : "jpg";
}

// scale image to a target display width in points (1pt = 1/72 in), max width in px for layout
function image(file, widthPt, opts = {}) {
  const abs = path.join(DIR, file);
  const { w, h } = imgSize(abs);
  const dispW = widthPt;
  const dispH = Math.round(dispW * h / w);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: opts.before ?? 120, after: opts.after ?? 40 },
    children: [
      new ImageRun({
        type: ext(file),
        data: fs.readFileSync(abs),
        transformation: { width: dispW, height: dispH },
      }),
    ],
  });
}

function caption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [new TextRun({ text, italics: true, size: 17, color: MUTED, font: FONT })],
  });
}

function runsFromText(text, opts = {}) {
  // supports **bold** inline markers
  const parts = text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((p) => {
    if (p.startsWith("**") && p.endsWith("**")) {
      return new TextRun({ text: p.slice(2, -2), bold: true, size: opts.size ?? 21, color: opts.color ?? INK, font: FONT });
    }
    return new TextRun({ text: p, size: opts.size ?? 21, color: opts.color ?? "1F2733", font: FONT });
  });
}

function para(text, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 160, line: 288 },
    alignment: opts.align ?? AlignmentType.JUSTIFIED,
    children: runsFromText(text, opts),
  });
}

function heading(text, kind = "h1") {
  const color = { h1: INK, h2: INK, h3: ORANGE }[kind];
  if (kind === "h1") {
    return [
      new Paragraph({
        heading: HeadingLevel.HEADING_1,
        spacing: { before: 320, after: 60 },
        keepNext: true, keepLines: true,
        border: { bottom: { color: ORANGE, style: BorderStyle.SINGLE, size: 18, space: 6 } },
        children: [new TextRun({ text, bold: true, size: 32, color, font: HFONT })],
      }),
    ];
  }
  if (kind === "h2") {
    return [
      new Paragraph({
        heading: HeadingLevel.HEADING_2,
        spacing: { before: 240, after: 60 },
        keepNext: true, keepLines: true,
        children: [new TextRun({ text, bold: true, size: 26, color, font: HFONT })],
      }),
    ];
  }
  return [
    new Paragraph({
      heading: HeadingLevel.HEADING_3,
      spacing: { before: 180, after: 40 },
      keepNext: true, keepLines: true,
      children: [new TextRun({ text, bold: true, size: 22, color, font: HFONT })],
    }),
  ];
}

function bullet(text, color = ORANGE) {
  return new Paragraph({
    spacing: { after: 90, line: 276 },
    indent: { left: 360, hanging: 220 },
    children: [
      new TextRun({ text: "■  ", color, size: 16, font: FONT }),
      ...runsFromText(text, { size: 21 }),
    ],
  });
}

function calloutBox(title, text, accent = ORANGE) {
  const cell = new TableCell({
    width: { size: CONTENT_W, type: WidthType.DXA },
    shading: { type: ShadingType.CLEAR, fill: LIGHT, color: "auto" },
    borders: {
      top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
      right: { style: BorderStyle.NONE },
      left: { style: BorderStyle.SINGLE, size: 30, color: accent },
    },
    margins: { top: 140, bottom: 140, left: 200, right: 200 },
    children: [
      ...(title ? [new Paragraph({
        spacing: { after: 60 },
        children: [new TextRun({ text: title, bold: true, size: 22, color: accent, font: HFONT })],
      })] : []),
      ...text.split("\n\n").map((t) => new Paragraph({
        spacing: { after: 60, line: 276 },
        children: runsFromText(t, { size: 20, color: "2A3340" }),
      })),
    ],
  });
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: [CONTENT_W],
    borders: {
      top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
      left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
      insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE },
    },
    rows: [new TableRow({ children: [cell] })],
  });
}

function noBorders() {
  return {
    top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
    left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
    insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE },
  };
}

function statStrip(items) {
  // items: [{value,label,color}]
  const n = items.length;
  const colW = Math.floor(CONTENT_W / n);
  const cells = items.map((it) => new TableCell({
    width: { size: colW, type: WidthType.DXA },
    verticalAlign: VerticalAlign.CENTER,
    margins: { top: 120, bottom: 120, left: 80, right: 80 },
    shading: { type: ShadingType.CLEAR, fill: "FBFCFE", color: "auto" },
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      left: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      right: { style: BorderStyle.SINGLE, size: 4, color: RULE },
    },
    children: [
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 30 },
        children: [new TextRun({ text: it.value, bold: true, size: 40, color: it.color || ORANGE, font: HFONT })] }),
      new Paragraph({ alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: it.label, size: 16, color: MUTED, font: FONT })] }),
    ],
  }));
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: items.map(() => colW),
    borders: noBorders(),
    rows: [new TableRow({ children: cells })],
  });
}

function dataTable(headers, rows, widths, accent = INK) {
  const headCells = headers.map((h, i) => new TableCell({
    width: { size: widths[i], type: WidthType.DXA },
    shading: { type: ShadingType.CLEAR, fill: accent, color: "auto" },
    margins: { top: 90, bottom: 90, left: 130, right: 130 },
    children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 19, font: HFONT })] })],
  }));
  const bodyRows = rows.map((r, ri) => new TableRow({
    children: r.map((c, i) => new TableCell({
      width: { size: widths[i], type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, fill: ri % 2 ? "FFFFFF" : "F4F7FB", color: "auto" },
      margins: { top: 80, bottom: 80, left: 130, right: 130 },
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({ children: runsFromText(String(c), { size: 18, color: "26303C" }) })],
    })),
  }));
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: widths,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      insideVertical: { style: BorderStyle.SINGLE, size: 4, color: "FFFFFF" },
    },
    rows: [new TableRow({ tableHeader: true, children: headCells }), ...bodyRows],
  });
}

// zone profile card: image + info panel
function zoneCard(z) {
  const kids = [];
  kids.push(...heading(z.name, "h2"));
  kids.push(new Paragraph({
    spacing: { after: 100 },
    keepNext: true,
    children: [
      new TextRun({ text: `\u{1F4CD} ${z.city}, ${z.region}`, size: 19, color: MUTED, font: FONT }),
      new TextRun({ text: `    ${z.coords}`, size: 17, color: MUTED, italics: true, font: FONT }),
    ],
  }));
  if (z.image) {
    kids.push(image(z.image, 470));
    if (z.caption) kids.push(caption(z.caption));
  }
  kids.push(para(z.summary, { after: 120 }));
  if (z.companies && z.companies.length) {
    kids.push(new Paragraph({ spacing: { before: 60, after: 40 },
      children: [new TextRun({ text: "Ключевые резиденты", bold: true, size: 20, color: ORANGE, font: HFONT })] }));
    z.companies.forEach((c) => kids.push(bullet(c, ORANGE)));
  }
  if (z.facts && z.facts.length) {
    kids.push(new Paragraph({ spacing: { before: 100, after: 40 },
      children: [new TextRun({ text: "Факты и цифры", bold: true, size: 20, color: BLUE, font: HFONT })] }));
    z.facts.forEach((f) => kids.push(bullet(f, BLUE)));
  }
  return kids;
}

// ---- render blocks ----
function renderBlock(b) {
  switch (b.type) {
    case "pagebreak":
      return [new Paragraph({ children: [new PageBreak()] })];
    case "h1": return heading(b.text, "h1");
    case "h2": return heading(b.text, "h2");
    case "h3": return heading(b.text, "h3");
    case "para": return [para(b.text)];
    case "paras": return b.items.map((t) => para(t));
    case "bullets": return b.items.map((t) => bullet(t, b.color || ORANGE));
    case "image": {
      const out = [image(b.file, b.widthPt || 470)];
      if (b.caption) out.push(caption(b.caption));
      return out;
    }
    case "fullimage": {
      const out = [image(b.file, b.widthPt || 500, { before: 40 })];
      if (b.caption) out.push(caption(b.caption));
      return out;
    }
    case "callout": return [calloutBox(b.title, b.text, b.accent || ORANGE), new Paragraph({ spacing: { after: 120 }, children: [] })];
    case "statstrip": return [statStrip(b.items), new Paragraph({ spacing: { after: 160 }, children: [] })];
    case "table": return [dataTable(b.headers, b.rows, b.widths, b.accent || INK), new Paragraph({ spacing: { after: 160 }, children: [] })];
    case "zonecard": return zoneCard(b);
    case "spacer": return [new Paragraph({ spacing: { after: b.after || 120 }, children: [] })];
    case "sources":
      return b.items.map((s, i) => new Paragraph({
        spacing: { after: 60 },
        indent: { left: 360, hanging: 300 },
        children: [
          new TextRun({ text: `${i + 1}. `, size: 17, color: MUTED, font: FONT }),
          new TextRun({ text: s, size: 17, color: "3A4656", font: FONT }),
        ],
      }));
    default:
      return [para(JSON.stringify(b))];
  }
}

// ---- cover page ----
function coverPage(meta) {
  const children = [];
  children.push(new Paragraph({ spacing: { before: 200, after: 40 },
    children: [new TextRun({ text: meta.kicker, bold: true, size: 20, color: ORANGE, font: HFONT, characterSpacing: 60 })] }));
  children.push(new Paragraph({ spacing: { after: 40 },
    children: [new TextRun({ text: meta.title, bold: true, size: 52, color: INK, font: HFONT })] }));
  children.push(new Paragraph({ spacing: { after: 200 },
    border: { bottom: { color: ORANGE, style: BorderStyle.SINGLE, size: 18, space: 8 } },
    children: [new TextRun({ text: meta.subtitle, size: 26, color: MUTED, font: FONT })] }));
  children.push(image(meta.cover, 520, { before: 60, after: 80 }));
  children.push(caption(meta.coverCaption || ""));
  // meta line
  children.push(new Paragraph({ spacing: { before: 220 },
    children: [new TextRun({ text: meta.tagline, size: 21, color: "2A3340", font: FONT, italics: true })] }));
  children.push(new Paragraph({ spacing: { before: 260 },
    children: [
      new TextRun({ text: "Аналитический отчёт", bold: true, size: 19, color: INK, font: HFONT }),
      new TextRun({ text: `   ·   ${meta.date}`, size: 19, color: MUTED, font: FONT }),
    ] }));
  children.push(new Paragraph({ children: [new PageBreak()] }));
  return children;
}

// ---- assemble ----
const body = [];
body.push(...coverPage(DATA.meta));

// TOC — static, with real page numbers and dot leaders
function tocEntry(e) {
  const lvl2 = e.level === 2;
  return new Paragraph({
    spacing: { after: lvl2 ? 70 : 110, before: lvl2 ? 10 : (e.level === 1 ? 70 : 0) },
    indent: lvl2 ? { left: 420 } : undefined,
    tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_W, leader: LeaderType.DOT }],
    children: [
      new TextRun({ text: e.title, bold: !lvl2, size: lvl2 ? 20 : 23, color: lvl2 ? "3A4656" : INK, font: HFONT }),
      new TextRun({ children: [new Tab()], font: FONT }),
      new TextRun({ text: String(e.page), size: lvl2 ? 20 : 22, color: lvl2 ? MUTED : ORANGE, bold: !lvl2, font: FONT }),
    ],
  });
}
body.push(new Paragraph({ spacing: { after: 200 },
  children: [new TextRun({ text: "Содержание", bold: true, size: 34, color: INK, font: HFONT })],
  border: { bottom: { color: ORANGE, style: BorderStyle.SINGLE, size: 18, space: 8 } } }));
(DATA.tocStatic || []).forEach((e) => body.push(tocEntry(e)));
body.push(new Paragraph({ children: [new PageBreak()] }));

DATA.blocks.forEach((b) => { body.push(...renderBlock(b)); });

const doc = new Document({
  creator: "Аналитический отчёт",
  title: DATA.meta.title,
  styles: {
    default: {
      document: { run: { font: FONT, size: 21, color: "1F2733" } },
    },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: HFONT, size: 32, bold: true, color: INK }, paragraph: { spacing: { before: 320, after: 60 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: HFONT, size: 26, bold: true, color: INK }, paragraph: { spacing: { before: 240, after: 60 }, outlineLevel: 1 } },
    ],
  },
  sections: [{
    properties: { titlePage: true, page: { size: { width: 11906, height: 16838 }, margin: { top: 1080, bottom: 1080, left: 1180, right: 1180, header: 560, footer: 560 } } },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT, spacing: { after: 0 },
        border: { bottom: { color: RULE, style: BorderStyle.SINGLE, size: 4, space: 4 } },
        children: [new TextRun({ text: "Промышленные технозоны Нидерландов", size: 15, color: MUTED, font: FONT })] })] }),
      first: new Header({ children: [new Paragraph({ children: [] })] }),
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        border: { top: { color: RULE, style: BorderStyle.SINGLE, size: 4, space: 4 } },
        children: [
          new TextRun({ text: "Аналитический отчёт  ·  ", size: 15, color: MUTED, font: FONT }),
          new TextRun({ children: [PageNumber.CURRENT], size: 15, color: MUTED, font: FONT }),
        ] })] }),
      first: new Footer({ children: [new Paragraph({ children: [] })] }),
    },
    children: body,
  }],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(path.join(DIR, "report.docx"), buf);
  console.log("WROTE report.docx", buf.length, "bytes");
});
