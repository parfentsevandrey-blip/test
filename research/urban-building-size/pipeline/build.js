const fs = require("fs");
const L = require("./doclib");
const { D, C, FS, FH, PAGE } = L;
const {
  Document, Packer, Paragraph, TextRun, Footer, Header, PageNumber,
  AlignmentType, LevelFormat, convertMillimetersToTwip: mm, PageOrientation, BorderStyle,
  TabStopType, LeaderType,
} = D;

const P1 = require("./part1");
const P2 = require("./part2");
const P3 = require("./part3");
const P4 = require("./part4");

const footer = (withNum = true) =>
  new Footer({
    children: [
      new Paragraph({
        border: { top: { style: BorderStyle.SINGLE, size: 4, color: C.hair, space: 6 } },
        tabStops: [{ type: "right", position: PAGE.w - PAGE.ml - PAGE.mr }],
        children: [
          new TextRun({ text: "Анатомия мегаполиса · сентябрь 2026", font: FS, size: 15, color: C.muted }),
          ...(withNum
            ? [new TextRun({ text: "\t", font: FS, size: 15 }),
               new TextRun({ children: [PageNumber.CURRENT], font: FS, size: 15, color: C.ink2, bold: true })]
            : []),
        ],
        spacing: { before: 120 },
      }),
    ],
  });

const footerLand = () =>
  new Footer({
    children: [
      new Paragraph({
        border: { top: { style: BorderStyle.SINGLE, size: 4, color: C.hair, space: 6 } },
        tabStops: [{ type: "right", position: PAGE.h - PAGE.ml - PAGE.mr }],
        children: [
          new TextRun({ text: "Анатомия мегаполиса · приложения", font: FS, size: 15, color: C.muted }),
          new TextRun({ text: "\t", font: FS, size: 15 }),
          new TextRun({ children: [PageNumber.CURRENT], font: FS, size: 15, color: C.ink2, bold: true }),
        ],
        spacing: { before: 120 },
      }),
    ],
  });

// ── статическое оглавление ────────────────────────────────────────────────
const PAGES = (() => { try { return require("./toc_pages.json"); } catch (e) { return {}; } })();

const tocLine = (h) => {
  const pg = PAGES[h.t] !== undefined ? String(PAGES[h.t]) : "00";
  const sizes = { 1: 22, 2: 20, 3: 19 };
  const indents = { 1: 0, 2: 300, 3: 620 };
  const CWv = L.CW;
  return new Paragraph({
    indent: { left: indents[h.lvl] },
    tabStops: [{ type: TabStopType.RIGHT, position: CWv, leader: LeaderType.DOT }],
    spacing: { before: h.lvl === 1 ? 150 : 20, after: 20, line: 250, lineRule: D.LineRuleType.AUTO },
    children: [
      new TextRun({ text: h.t, font: h.lvl === 1 ? FH : FS, size: sizes[h.lvl],
        bold: h.lvl === 1, color: h.lvl === 1 ? C.accent : (h.lvl === 2 ? C.ink : C.ink2) }),
      new TextRun({ text: "\t" + pg, font: FS, size: sizes[h.lvl], bold: h.lvl === 1,
        color: h.lvl === 1 ? C.accent : C.ink2 }),
    ],
  });
};

const tocSection = [
  new Paragraph({
    children: [new TextRun({ text: "Содержание", font: FH, size: 40, bold: true, color: C.accent })],
    spacing: { after: 240, line: 276, lineRule: D.LineRuleType.AUTO },
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: C.accent, space: 8 } },
  }),
  ...L.headings.map(tocLine),
  new Paragraph({ children: [new D.PageBreak()] }),
];

const numbering = {
  config: [
    {
      reference: "dots",
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: "▪", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 340, hanging: 200 } },
                   run: { color: C.accent2, font: FS } } },
        { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 680, hanging: 200 } },
                   run: { color: C.muted, font: FS } } },
      ],
    },
    {
      reference: "nums",
      levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 400, hanging: 260 } },
                   run: { color: C.accent, bold: true, font: FS } } },
      ],
    },
  ],
};

const styles = {
  default: {
    document: { run: { font: FS, size: 22, color: C.ink }, paragraph: { spacing: { line: 288, lineRule: D.LineRuleType.AUTO } } },
    heading1: { run: { font: FH, size: 40, bold: true, color: C.accent } },
    heading2: { run: { font: FH, size: 27, bold: true, color: C.accent } },
    heading3: { run: { font: FH, size: 23, bold: true, color: C.ink2 } },
  },
};

const portraitProps = {
  page: {
    size: { width: PAGE.w, height: PAGE.h },
    margin: { top: PAGE.mt, bottom: PAGE.mb, left: PAGE.ml, right: PAGE.mr, footer: mm(11) },
  },
};

const landscapeProps = {
  page: {
    size: { width: PAGE.w, height: PAGE.h, orientation: PageOrientation.LANDSCAPE },
    margin: { top: mm(16), bottom: mm(16), left: mm(18), right: mm(18), footer: mm(9) },
  },
};

const doc = new Document({
  creator: "Аналитическое исследование застройки",
  title: "Анатомия мегаполиса: размерная структура зданий 34 крупнейших городов мира",
  description: "Сравнительное исследование размерной структуры застройки на шести континентах по спутниковым контурам зданий",
  features: { updateFields: true },
  numbering,
  styles,
  sections: [
    { properties: portraitProps, footers: { default: footer() },
      children: [
        ...P1.cover, ...tocSection, ...P1.summary, ...P1.methodology, ...P1.overview,
        ...P2.continents,
        ...P3.patterns, ...P3.stories, ...P3.market, ...P3.sources,
        ...P4.conclusions,
      ] },
    { properties: landscapeProps, footers: { default: footerLand() },
      children: [...P4.appA, ...P4.appB, ...P4.appC] },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  const out = __dirname + "/Анатомия_мегаполиса_исследование_застройки.docx";
  fs.writeFileSync(out, buf);
  console.log("написан:", out, (buf.length / 1024 / 1024).toFixed(2), "МБ");
});
