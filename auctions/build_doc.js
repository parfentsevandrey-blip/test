#!/usr/bin/env node
/**
 * Builds the weekly "Объекты торгов" Word document from data/objects.json.
 *
 * Every object gets a summary page, a location page with the Yandex map, and
 * photo sheets whose rows are given exact heights so each sheet fills the page
 * instead of trailing off into white space.
 *
 *   node build_doc.js [output.docx]
 */
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, BorderStyle, ShadingType, HeightRule,
  ExternalHyperlink, VerticalAlign, LineRuleType,
} = require('docx');

const ROOT = __dirname;
const DATA = JSON.parse(fs.readFileSync(path.join(ROOT, 'data', 'objects.json'), 'utf8'));

/* ---- design tokens ---------------------------------------------------- */
const FONT = 'Arial';

// one type scale, five steps (half-points)
const T_TITLE = 40;   // 20pt  document title
const T_OBJECT = 26;  // 13pt  object title
const T_LEAD = 22;    // 11pt  headline figures
const T_BODY = 19;    // 9.5pt table text
const T_LABEL = 17;   // 8.5pt section labels, captions

const INK = '1F3864';      // headings and table labels
const TEXT = '1A1A1A';     // body copy
const MUTED = '767676';    // labels, captions
const ACCENT = 'A3161B';   // reserved for the two headline figures
const RULE = 'D0D7E5';
const LABEL_BG = 'F4F6FA';

const SP = 120;            // vertical rhythm unit (twips)

/* ---- page geometry (A4, DXA) ------------------------------------------ */
const PAGE_W = 11906;
const PAGE_H = 16838;
const MARGIN = 1021;                       // 1.8 cm
const CONTENT_W = PAGE_W - 2 * MARGIN;     // 9864
const CONTENT_H = PAGE_H - 2 * MARGIN;     // 14796
const LABEL_W = 3100;
const VALUE_W = CONTENT_W - LABEL_W;

const CELL_X = 85;                         // horizontal cell margin (photo cells)
const CELL_Y = 100;                        // vertical cell margin (photo cells)
const INFO_PAD = 165;                      // vertical padding inside table rows
const PX = 15;                             // DXA per pixel at 96 dpi

// A photo sheet has to fit the page in Word, not just in LibreOffice, so its
// height is pinned rather than estimated: the label paragraph is given an exact
// line height, the rows exact heights, and a margin is left over for whatever
// the two renderers still disagree about. Overshooting by one twip costs a whole
// row — it drops onto the next page — so the slack is deliberately generous.
const LABEL_LINE = 260;                    // exact line height of a section label
const LABEL_H = LABEL_LINE + SP + 70;      // label line + spacing after + rule
const SAFETY = 950;                        // ~1.7 cm of give per photo sheet
const MAP_SAFETY = 700;                    // the map page has fewer moving parts
const PHOTO_BUDGET = CONTENT_H - LABEL_H - SAFETY;
const MIN_ROW = 3900;                      // a row worth printing a photo in

const noBorder = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder };
const hairline = { style: BorderStyle.SINGLE, size: 4, color: RULE };
const boxBorders = { top: hairline, bottom: hairline, left: hairline, right: hairline };

const run = (text, opts = {}) => new TextRun({ text, font: FONT, ...opts });

/**
 * Small uppercase section label with a rule under it. `newPage` starts the
 * page here instead of a separate break paragraph, which would otherwise eat
 * a line at the top of the page and push the sheet over the page height.
 */
function sectionLabel(text, { newPage = false, exact = false } = {}) {
  return new Paragraph({
    pageBreakBefore: newPage,
    spacing: exact
      ? {
        before: newPage ? 0 : SP * 2, after: SP, line: LABEL_LINE, lineRule: LineRuleType.EXACT,
      }
      : { before: SP * 2, after: SP },
    border: { bottom: hairline },
    children: [run(text.toUpperCase(), {
      bold: true, size: T_LABEL, color: MUTED, characterSpacing: 40,
    })],
  });
}

function caption(text, opts = {}) {
  const spacing = { before: opts.before ?? 0, after: opts.after ?? 0 };
  if (opts.line) {
    spacing.line = opts.line;
    spacing.lineRule = LineRuleType.EXACT;
  }
  return new Paragraph({
    alignment: opts.alignment,
    spacing,
    children: [run(text, { size: T_LABEL, color: MUTED })],
  });
}

/* ---- key/value tables -------------------------------------------------- */
function infoRow(name, value, opts = {}) {
  const cell = (children, width, fill) => new TableCell({
    width: { size: width, type: WidthType.DXA },
    margins: { top: INFO_PAD, bottom: INFO_PAD, left: 150, right: 150 },
    shading: fill ? { type: ShadingType.CLEAR, fill, color: 'auto' } : undefined,
    verticalAlign: VerticalAlign.CENTER,
    borders: boxBorders,
    children,
  });
  return new TableRow({
    children: [
      cell([new Paragraph({ children: [run(name, { bold: true, size: T_BODY, color: INK })] })],
        LABEL_W, LABEL_BG),
      cell([new Paragraph({
        children: [run(value, {
          size: opts.lead ? T_LEAD : T_BODY,
          bold: !!opts.lead,
          color: opts.lead ? ACCENT : TEXT,
        })],
      })], VALUE_W),
    ],
  });
}

const infoTable = (rows) => new Table({
  columnWidths: [LABEL_W, VALUE_W],
  width: { size: CONTENT_W, type: WidthType.DXA },
  rows,
});

/* ---- photo sheets ------------------------------------------------------ */

/** Split n photos into pages of at most `max`, balanced so no sheet is nearly empty. */
function balancedPages(n, max) {
  const pages = Math.ceil(n / max);
  const base = Math.floor(n / pages);
  const extra = n % pages;
  return Array.from({ length: pages }, (_, i) => base + (i < extra ? 1 : 0));
}

function fit(w, h, box) {
  const s = Math.min(box.w / w, box.h / h);
  return { width: Math.round(w * s), height: Math.round(h * s) };
}

function photoCell(photo, dir, colWidth, box) {
  return new TableCell({
    width: { size: colWidth, type: WidthType.DXA },
    margins: { top: CELL_Y, bottom: CELL_Y, left: CELL_X, right: CELL_X },
    verticalAlign: VerticalAlign.CENTER,
    borders: noBorders,
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: photo ? [new ImageRun({
        type: 'jpg',
        data: fs.readFileSync(path.join(dir, photo.file)),
        transformation: fit(photo.width, photo.height, box),
      })] : [run('')],
    })],
  });
}

/** One sheet: rows get exact heights that add up to the space it is given. */
function photoSheet(group, dir, budget = PHOTO_BUDGET) {
  const cols = group.length <= 2 && budget > PHOTO_BUDGET * 0.7 ? 1 : 2;
  const rowCount = Math.ceil(group.length / cols);
  const rowH = Math.floor(budget / rowCount);
  const colWidth = Math.floor(CONTENT_W / cols);
  const box = { w: (colWidth - 2 * CELL_X) / PX, h: (rowH - 2 * CELL_Y) / PX };

  const rows = [];
  for (let r = 0; r < rowCount; r += 1) {
    const cells = [];
    for (let c = 0; c < cols; c += 1) {
      cells.push(photoCell(group[r * cols + c], dir, colWidth, box));
    }
    rows.push(new TableRow({
      height: { value: rowH, rule: HeightRule.EXACT },
      cantSplit: true,
      children: cells,
    }));
  }
  return new Table({
    columnWidths: Array(cols).fill(colWidth),
    width: { size: colWidth * cols, type: WidthType.DXA },
    borders: { ...noBorders, insideHorizontal: noBorder, insideVertical: noBorder },
    rows,
  });
}

/**
 * Photo sheets. `tailBudget` is the space still free under the map on the
 * location page — when it can hold a row, the first photos go there rather
 * than leaving the map page half blank.
 */
function photoPages(photos, dir, tailBudget) {
  const out = [];
  const total = photos.length;
  let seen = 0;
  let first = true;

  const sheet = (count, budget) => {
    const group = photos.slice(seen, seen + count);
    out.push(sectionLabel(`Фото объекта · ${seen + 1}–${seen + count} из ${total}`,
      { newPage: !first, exact: true }));
    out.push(photoSheet(group, dir, budget));
    seen += count;
    first = false;
  };

  const tailRoom = tailBudget - LABEL_H - SP * 2;   // the label also gets space above it here
  const rowsInTail = Math.floor(tailRoom / MIN_ROW);
  if (rowsInTail >= 1 && total > 2 * rowsInTail) {
    sheet(2 * rowsInTail, tailRoom);
  }
  balancedPages(total - seen, 6).forEach((count) => sheet(count, PHOTO_BUDGET));
  return out;
}

/* ---- document ---------------------------------------------------------- */
function objectSection(obj, index) {
  const children = [];
  const dir = path.join(ROOT, obj.photosDir);

  children.push(new Paragraph({
    pageBreakBefore: index > 0,
    spacing: { before: SP * 2, after: SP / 2 },
    children: [run(`Объект ${index + 1}`.toUpperCase(), {
      bold: true, size: T_LABEL, color: MUTED, characterSpacing: 40,
    })],
  }));
  children.push(new Paragraph({
    spacing: { after: SP * 2 },
    children: [run(obj.shortTitle, { bold: true, size: T_OBJECT, color: INK })],
  }));

  children.push(infoTable([
    infoRow('Описание объекта', obj.description),
    infoRow('Точный адрес', obj.address),
    infoRow('Начальная цена', obj.startPrice, { lead: true }),
    infoRow('Цена за кв. м.', obj.pricePerSqm),
    infoRow('Дата окончания приёма заявок', obj.applicationDeadline),
    infoRow('Дата проведения торгов', obj.auctionDate),
    infoRow('Задаток', obj.deposit, { lead: true }),
  ]));

  if (obj.extras && obj.extras.length) {
    children.push(sectionLabel('Дополнительные сведения'));
    children.push(infoTable(obj.extras.map(([k, v]) => infoRow(k, v))));
  }

  children.push(new Paragraph({
    spacing: { before: SP * 2 },
    children: [
      run('Объявление: ', { size: T_LABEL, color: MUTED }),
      new ExternalHyperlink({
        link: obj.sourceUrl,
        children: [run(obj.sourceUrl, { size: T_LABEL, color: '0563C1', underline: {} })],
      }),
    ],
  }));

  // --- location -------------------------------------------------------
  children.push(sectionLabel('Локация', { newPage: true, exact: true }));
  children.push(caption(obj.address, { after: SP, line: 240 }));
  const map = fs.readFileSync(path.join(ROOT, obj.map));
  const mapSize = fit(1160, 950, { w: (CONTENT_W - 2) / PX, h: PHOTO_BUDGET / PX });
  // no exact line rule here: Word clips an inline image taller than its line box
  children.push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 0 },
    children: [new ImageRun({ type: 'png', data: map, transformation: mapSize })],
  }));
  children.push(caption(`Яндекс Карты · ${obj.coords} · ближайшее метро «${obj.metro}»`,
    { alignment: AlignmentType.CENTER, before: SP, line: 240 }));

  // label + address line + map + caption, then the usual per-sheet slack
  const mapPageUsed = LABEL_H + (240 + SP) + (mapSize.height * PX + 120) + (SP + 240) + MAP_SAFETY;

  // --- photos ---------------------------------------------------------
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json'), 'utf8'));
  if (manifest.length) {
    children.push(...photoPages(manifest, dir, CONTENT_H - mapPageUsed));
  }
  return children;
}

function build() {
  const children = [];

  children.push(new Paragraph({
    spacing: { after: SP / 2 },
    children: [run(DATA.title.toUpperCase(), {
      bold: true, size: T_TITLE, color: INK, characterSpacing: 40,
    })],
  }));
  children.push(new Paragraph({
    spacing: { after: SP / 2 },
    children: [run(DATA.subtitle, { size: T_LEAD, color: MUTED })],
  }));
  children.push(new Paragraph({
    spacing: { after: SP },
    border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: INK } },
    children: [run(`${DATA.issue} · объектов в подборке: ${DATA.objects.length}`,
      { size: T_LABEL, color: MUTED })],
  }));

  DATA.objects.forEach((obj, i) => children.push(...objectSection(obj, i)));

  return new Document({
    creator: 'Еженедельный обзор торгов',
    title: `${DATA.title} — ${DATA.subtitle}`,
    styles: { default: { document: { run: { font: FONT, size: T_BODY, color: TEXT } } } },
    sections: [{
      properties: { page: { margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN } } },
      children,
    }],
  });
}

const out = process.argv[2] || path.join(ROOT, 'out', 'Торги_Москва_и_Подмосковье.docx');
fs.mkdirSync(path.dirname(out), { recursive: true });
Packer.toBuffer(build()).then((buf) => {
  fs.writeFileSync(out, buf);
  console.log('written:', out, (buf.length / 1024 / 1024).toFixed(2), 'MB');
});
