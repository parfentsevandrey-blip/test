#!/usr/bin/env node
/**
 * Builds the weekly "Объекты торгов" Word document from data/objects.json.
 *
 * Every object gets a summary page (description, address, prices, dates,
 * deposit), a location page with the Yandex map screenshot, and photo pages
 * laid out four pictures per page.
 *
 *   node build_doc.js [output.docx]
 */
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, BorderStyle, ShadingType, HeadingLevel, PageBreak,
  ExternalHyperlink, VerticalAlign,
} = require('docx');

const ROOT = __dirname;
const DATA = JSON.parse(fs.readFileSync(path.join(ROOT, 'data', 'objects.json'), 'utf8'));

const FONT = 'Arial';
const INK = '1F3864';        // headings
const ACCENT = 'C00000';     // prices
const MUTED = '595959';
const RULE = 'BFBFBF';

const MARGIN = 1134;                 // 2 cm
const CONTENT = 11906 - 2 * MARGIN;  // A4 width minus margins, in DXA
const LABEL_W = 3100;
const VALUE_W = CONTENT - LABEL_W;

const PHOTO_BOX = { w: 313, h: 235 }; // px @96dpi ≈ 8.3 × 6.2 cm

const noBorder = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder };
const hairline = { style: BorderStyle.SINGLE, size: 4, color: RULE };

const run = (text, opts = {}) => new TextRun({ text, font: FONT, ...opts });

function heading(text, opts = {}) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: opts.before ?? 0, after: opts.after ?? 200 },
    children: [run(text, { bold: true, size: opts.size ?? 30, color: INK })],
  });
}

function label(text) {
  return new Paragraph({
    spacing: { before: 240, after: 120 },
    border: { bottom: hairline },
    children: [run(text.toUpperCase(), { bold: true, size: 20, color: MUTED, characterSpacing: 30 })],
  });
}

function infoRow(name, value, opts = {}) {
  const cell = (children, width, shading) => new TableCell({
    width: { size: width, type: WidthType.DXA },
    margins: { top: 90, bottom: 90, left: 140, right: 140 },
    shading: shading ? { type: ShadingType.CLEAR, fill: shading, color: 'auto' } : undefined,
    verticalAlign: VerticalAlign.CENTER,
    borders: { top: hairline, bottom: hairline, left: hairline, right: hairline },
    children,
  });
  return new TableRow({
    children: [
      cell([new Paragraph({ children: [run(name, { bold: true, size: 20, color: INK })] })], LABEL_W, 'F2F5FA'),
      cell([new Paragraph({
        children: [run(value, {
          size: opts.big ? 26 : 21,
          bold: !!opts.big,
          color: opts.big ? ACCENT : '000000',
        })],
      })], VALUE_W),
    ],
  });
}

function infoTable(rows) {
  return new Table({
    columnWidths: [LABEL_W, VALUE_W],
    width: { size: CONTENT, type: WidthType.DXA },
    rows,
  });
}

function fit(w, h) {
  const s = Math.min(PHOTO_BOX.w / w, PHOTO_BOX.h / h);
  return { width: Math.round(w * s), height: Math.round(h * s) };
}

function photoCell(photo, dir) {
  const children = [];
  if (photo) {
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 60 },
      children: [new ImageRun({
        type: 'jpg',
        data: fs.readFileSync(path.join(dir, photo.file)),
        transformation: fit(photo.width, photo.height),
      })],
    }));
    children.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [run(`Фото ${photo.index}`, { size: 16, color: MUTED })],
    }));
  } else {
    children.push(new Paragraph({ children: [run('')] }));
  }
  return new TableCell({
    width: { size: CONTENT / 2, type: WidthType.DXA },
    margins: { top: 120, bottom: 120, left: 80, right: 80 },
    verticalAlign: VerticalAlign.CENTER,
    borders: noBorders,
    children,
  });
}

function photoPages(photos, dir) {
  const out = [];
  for (let page = 0; page < photos.length; page += 4) {
    const group = photos.slice(page, page + 4);
    if (page > 0) out.push(new Paragraph({ children: [new PageBreak()] }));
    out.push(label(`Фото объекта (${page + 1}–${page + group.length} из ${photos.length})`));
    const rows = [];
    for (let r = 0; r < 4; r += 2) {
      if (!group[r]) break;
      rows.push(new TableRow({
        children: [photoCell(group[r], dir), photoCell(group[r + 1], dir)],
      }));
    }
    out.push(new Table({
      columnWidths: [CONTENT / 2, CONTENT / 2],
      width: { size: CONTENT, type: WidthType.DXA },
      borders: {
        ...noBorders,
        insideHorizontal: noBorder,
        insideVertical: noBorder,
      },
      rows,
    }));
  }
  return out;
}

function objectSection(obj, index) {
  const children = [];
  const dir = path.join(ROOT, obj.photosDir);

  children.push(new Paragraph({
    spacing: { after: 60 },
    children: [run(`ОБЪЕКТ ${index + 1}`, { bold: true, size: 20, color: ACCENT, characterSpacing: 40 })],
  }));
  children.push(heading(obj.shortTitle, { after: 260, size: 28 }));

  children.push(infoTable([
    infoRow('Описание объекта', obj.description),
    infoRow('Точный адрес', obj.address),
    infoRow('Начальная цена', obj.startPrice, { big: true }),
    infoRow('Цена за кв. м.', obj.pricePerSqm),
    infoRow('Дата окончания приёма заявок', obj.applicationDeadline),
    infoRow('Дата проведения торгов', obj.auctionDate),
    infoRow('Задаток', obj.deposit, { big: true }),
  ]));

  if (obj.extras && obj.extras.length) {
    children.push(label('Дополнительные сведения'));
    children.push(infoTable(obj.extras.map(([k, v]) => infoRow(k, v))));
  }

  children.push(new Paragraph({
    spacing: { before: 240 },
    children: [
      run('Объявление: ', { size: 20, color: MUTED }),
      new ExternalHyperlink({
        link: obj.sourceUrl,
        children: [run(obj.sourceUrl, { size: 20, color: '0563C1', underline: {} })],
      }),
    ],
  }));

  // --- location -------------------------------------------------------
  children.push(new Paragraph({ children: [new PageBreak()] }));
  children.push(label('Локация'));
  children.push(new Paragraph({
    spacing: { after: 120 },
    children: [run(obj.address, { size: 20, color: MUTED })],
  }));
  const map = fs.readFileSync(path.join(ROOT, obj.map));
  children.push(new Paragraph({
    alignment: AlignmentType.CENTER,
    children: [new ImageRun({ type: 'png', data: map, transformation: { width: 620, height: 440 } })],
  }));
  children.push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 120 },
    children: [run(`Яндекс Карты · координаты ${obj.coords}`, { size: 16, color: MUTED })],
  }));

  // --- photos ---------------------------------------------------------
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json'), 'utf8'));
  const photos = manifest.map((p, i) => ({ ...p, index: i + 1 }));
  if (photos.length) {
    children.push(new Paragraph({ children: [new PageBreak()] }));
    children.push(...photoPages(photos, dir));
  }
  return children;
}

function build() {
  const children = [];

  children.push(new Paragraph({
    spacing: { after: 60 },
    children: [run(DATA.title.toUpperCase(), { bold: true, size: 44, color: INK, characterSpacing: 40 })],
  }));
  children.push(new Paragraph({
    spacing: { after: 40 },
    children: [run(DATA.subtitle, { size: 28, color: ACCENT })],
  }));
  children.push(new Paragraph({
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: INK } },
    spacing: { after: 240 },
    children: [run(`${DATA.issue} · объектов в подборке: ${DATA.objects.length}`, { size: 20, color: MUTED })],
  }));

  DATA.objects.forEach((obj, i) => {
    if (i > 0) children.push(new Paragraph({ children: [new PageBreak()] }));
    children.push(...objectSection(obj, i));
  });

  return new Document({
    creator: 'Еженедельный обзор торгов',
    title: `${DATA.title} — ${DATA.subtitle}`,
    styles: { default: { document: { run: { font: FONT, size: 21 } } } },
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
