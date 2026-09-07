#!/usr/bin/env node
/**
 * lots.json -> .docx
 *
 *   node tools/cian/render-docx.js --data build/lots.json --out document.docx
 *
 * Порядок карточек и строк таблицы — по возрастанию цены за квадратный метр.
 */
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType,
  PageBreak, ExternalHyperlink, LevelFormat, LineRuleType, convertMillimetersToTwip,
} = require('docx');

const ACCENT = '1F5F5B';
const INK = '16202B';
const MUTED = '6B7885';
const LINE = 'D9DEE4';
const TINT = 'EDF3F2';

const SERIF = 'Georgia';
const SANS = 'Calibri';

/* Ширина колонки текста: A4 21 см минус поля по 2 см. 1 см = 567 twip. */
const CONTENT_TWIP = Math.round(17 * 567);

const NONE = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
const NO_BORDERS = { top: NONE, bottom: NONE, left: NONE, right: NONE,
  insideHorizontal: NONE, insideVertical: NONE };

const money = (v) => v === null || v === undefined
  ? '—' : String(Math.round(v)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

const dec = (v, d = 1) => v === null || v === undefined
  ? '—' : String(Number(v).toFixed(d)).replace(/\.?0+$/, '').replace('.', ',');

const mln = (v) => v === null || v === undefined ? '—' : dec(v / 1e6, 1);

/* Размеры кадра нужны, чтобы вставить его без искажения пропорций.
   Читаем их из самого JPEG: маркеры SOF несут высоту и ширину. */
function jpegSize(buf) {
  let i = 2;
  while (i < buf.length) {
    if (buf[i] !== 0xff) { i++; continue; }
    const marker = buf[i + 1];
    // SOF0..SOF15, кроме DHT(c4), JPGA(c8) и DAC(cc) — они не рамки кадра.
    if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
      return { height: buf.readUInt16BE(i + 5), width: buf.readUInt16BE(i + 7) };
    }
    i += 2 + buf.readUInt16BE(i + 2);
  }
  return null;
}

/* Вписываем в габарит, а не тянем по ширине: среди кадров попадаются
   портретные планировки, и от общей ширины они вырастают вдвое выше
   соседей — ряд миниатюр рассыпается. */
function dataUriToImage(uri, maxW, maxH) {
  if (!uri) return null;
  const buf = Buffer.from(uri.split(',', 2)[1], 'base64');
  const size = jpegSize(buf) || { width: 720, height: 480 };
  const scale = Math.min(maxW / size.width, (maxH ?? Infinity) / size.height);
  return new ImageRun({
    type: 'jpg',
    data: buf,
    transformation: {
      width: Math.round(size.width * scale),
      height: Math.round(size.height * scale),
    },
  });
}

const text = (t, o = {}) => new TextRun({
  text: t, font: o.font || SANS, size: o.size || 19,
  color: o.color || INK, bold: o.bold, italics: o.italics,
  allCaps: o.caps, characterSpacing: o.spacing,
});

/* Межстрочный интервал задаём только там, где есть текст, и всегда с явным
   lineRule. Без него `w:line` понимают как точную высоту строки: абзац с
   картинкой сплющивается до 13 пунктов, и от снимка остаётся полоска.
   Для абзацев с изображением интервал не ставим вовсе — `line: false`. */
const p = (runs, o = {}) => new Paragraph({
  children: Array.isArray(runs) ? runs : [runs],
  spacing: {
    before: o.before ?? 0,
    after: o.after ?? 100,
    ...(o.line === false ? {} : { line: o.line ?? 264, lineRule: LineRuleType.AUTO }),
  },
  alignment: o.align,
  border: o.border,
  keepNext: o.keepNext,
  indent: o.indent,
});

function cell(children, o = {}) {
  return new TableCell({
    children,
    width: { size: o.width, type: WidthType.DXA },
    margins: { top: o.mt ?? 60, bottom: o.mb ?? 60, left: o.ml ?? 90, right: o.mr ?? 90 },
    shading: o.fill ? { type: ShadingType.CLEAR, fill: o.fill, color: 'auto' } : undefined,
    borders: o.borders,
    columnSpan: o.span,
    verticalAlign: o.valign,
  });
}

/* ---------- характеристики лота ---------- */
function specRows(lot) {
  const r = [];
  const add = (k, v) => { if (v !== null && v !== undefined && v !== '') r.push([k, String(v)]); };
  add('Площадь', `${dec(lot.area)} м²`);
  add('Тип объекта', lot.kind);
  add('Район', lot.district);
  add('Округ', lot.okrug);
  add('Этаж', lot.floor);
  add('Этажей в здании', lot.floorsCount);
  add('Тип здания', lot.buildingType);
  add('Год постройки', lot.buildYear);
  add('Состояние', lot.condition);
  add('Отопление', lot.heating);
  if (lot.electricity) add('Электричество', `${lot.electricity} кВт`);
  add('Налог', lot.vat);
  if (lot.bargain) add('Торг', 'возможен');
  const inc = (lot.monthlyIncome || {}).income;
  if (inc) {
    add('Арендный поток', `${money(inc)} ₽/мес`);
    if (lot.priceTotal) add('Окупаемость', `${dec(lot.priceTotal / (inc * 12))} лет`);
  }
  if (lot.features && lot.features.length) add('Оснащение', lot.features.join(', '));
  return r;
}

function specTable(lot) {
  const rows = specRows(lot);
  const kw = Math.round(CONTENT_TWIP * 0.32);
  const vw = CONTENT_TWIP - kw;
  return new Table({
    width: { size: CONTENT_TWIP, type: WidthType.DXA },
    columnWidths: [kw, vw],
    borders: {
      top: NONE, bottom: NONE, left: NONE, right: NONE, insideVertical: NONE,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: LINE },
    },
    rows: rows.map(([k, v]) => new TableRow({
      children: [
        cell([p(text(k, { color: MUTED, size: 18 }), { after: 0 })], { width: kw, ml: 0 }),
        cell([p(text(v, { size: 18 }), { after: 0 })], { width: vw, mr: 0 }),
      ],
    })),
  });
}

/* ---------- описание ---------- */
function descriptionParagraphs(desc, numbering) {
  if (!desc || !desc.trim()) {
    return [p(text('Описание в объявлении не заполнено.', { color: MUTED, italics: true }))];
  }
  const out = [];
  for (const block of desc.split(/\n\s*\n/).map((b) => b.trim()).filter(Boolean)) {
    const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
    const bullets = lines.filter((l) => /^[-—•]/.test(l));
    if (lines.length > 1 && bullets.length >= 2) {
      const lead = lines.filter((l) => !/^[-—•]/.test(l));
      if (lead.length) out.push(p(text(lead.join(' '))));
      for (const b of bullets) {
        out.push(new Paragraph({
          children: [text(b.replace(/^[-—•]\s*/, '').trim())],
          numbering: { reference: numbering, level: 0 },
          spacing: { after: 40, line: 264, lineRule: LineRuleType.AUTO },
        }));
      }
    } else {
      out.push(p(text(lines.join(' '))));
    }
  }
  return out;
}

/* ---------- карточка ---------- */
function lotSection(lot, rank, isFirst) {
  const out = [];
  if (!isFirst) out.push(new Paragraph({ children: [new PageBreak()] }));

  out.push(new Paragraph({
    children: [
      text(`${rank}. `, { font: SERIF, size: 26, bold: true, color: ACCENT }),
      text(lot.address, { font: SERIF, size: 26, bold: true }),
    ],
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 0, after: 60 },
    keepNext: true,
  }));

  const sub = [lot.kind, lot.district].filter(Boolean).join(' · ');
  out.push(p(text(sub, { color: MUTED, size: 18 }), { after: 60, keepNext: true }));

  if (lot.metro && lot.metro.length) {
    const runs = [];
    lot.metro.forEach((m, i) => {
      if (i) runs.push(text('   ', { size: 18 }));
      runs.push(text('● ', { size: 18, color: (m.color || '#888').replace('#', '') }));
      runs.push(text(m.name, { size: 18 }));
      if (m.time) runs.push(text(`  ${m.time} мин`, { size: 17, color: MUTED }));
    });
    out.push(p(runs, { after: 120, keepNext: true }));
  }

  // Ключевые цифры — полосой на подложке, чтобы не тонули в тексте.
  out.push(new Table({
    width: { size: CONTENT_TWIP, type: WidthType.DXA },
    columnWidths: [Math.round(CONTENT_TWIP / 3), Math.round(CONTENT_TWIP / 3),
      CONTENT_TWIP - 2 * Math.round(CONTENT_TWIP / 3)],
    borders: NO_BORDERS,
    rows: [new TableRow({
      children: [
        [`${money(lot.pricePerM2)} ₽`, 'за квадратный метр'],
        [`${money(lot.priceTotal)} ₽`, 'цена объекта'],
        [`${dec(lot.area)} м²`, 'площадь'],
      ].map(([big, small], i) => cell([
        p(text(big, { font: SERIF, size: 24, bold: true, color: i === 0 ? ACCENT : INK }),
          { after: 0 }),
        p(text(small, { size: 16, color: MUTED }), { after: 0 }),
      ], { width: Math.round(CONTENT_TWIP / 3), fill: TINT, mt: 100, mb: 100 })),
    })],
  }));
  out.push(p(text(''), { after: 100 }));

  // Кадр и карта рядом: карта уже, потому что важнее её содержимое, а не размер.
  const imgs = lot.images || [];
  const photoW = Math.round(CONTENT_TWIP * 0.56);
  const mapW = CONTENT_TWIP - photoW;
  const main = imgs[0] ? dataUriToImage(imgs[0], 300, 230) : null;
  const map = lot.map ? dataUriToImage(lot.map, 232, 230) : null;
  if (main || map) {
    out.push(new Table({
      width: { size: CONTENT_TWIP, type: WidthType.DXA },
      columnWidths: [photoW, mapW],
      borders: NO_BORDERS,
      rows: [new TableRow({
        children: [
          cell([main ? p(main, { after: 0, line: false })
            : p(text('В объявлении нет фотографий', { color: MUTED, italics: true }))],
          { width: photoW, ml: 0 }),
          cell([
            map ? p(map, { after: 40, line: false }) : p(text('')),
            p(text(map ? `Яндекс Карты · ${lot.lat.toFixed(5)}, ${lot.lng.toFixed(5)}`
              : 'Координаты не указаны', { size: 15, color: MUTED }), { after: 0 }),
          ], { width: mapW, mr: 0 }),
        ],
      })],
    }));
    out.push(p(text(''), { after: 80 }));
  }

  // Остальные кадры — по три в ряд.
  const rest = imgs.slice(1);
  if (rest.length) {
    const perRow = 3;
    const cw = Math.round(CONTENT_TWIP / perRow);
    const rows = [];
    for (let i = 0; i < rest.length; i += perRow) {
      const chunk = rest.slice(i, i + perRow);
      rows.push(new TableRow({
        children: Array.from({ length: perRow }, (_, j) => cell(
          [chunk[j] ? p(dataUriToImage(chunk[j], 165, 124), { after: 0, line: false })
            : p(text(''), { after: 0 })],
          { width: cw, ml: j === 0 ? 0 : 60, mr: 60 })),
      }));
    }
    out.push(new Table({
      width: { size: CONTENT_TWIP, type: WidthType.DXA },
      columnWidths: Array(perRow).fill(cw),
      borders: NO_BORDERS,
      rows,
    }));
    out.push(p(text(''), { after: 120 }));
  }

  out.push(p(text('Характеристики', { size: 17, bold: true, color: MUTED, caps: true, spacing: 20 }),
    { after: 60, keepNext: true }));
  out.push(specTable(lot));
  out.push(p(text(''), { after: 120 }));

  out.push(p(text('Описание из объявления',
    { size: 17, bold: true, color: MUTED, caps: true, spacing: 20 }), { after: 60, keepNext: true }));
  out.push(...descriptionParagraphs(lot.description, 'bullets'));

  out.push(new Paragraph({
    children: [new ExternalHyperlink({
      children: [text(`Объявление на Циан № ${lot.id}`, { size: 17, color: ACCENT })],
      link: lot.url,
    })],
    spacing: { before: 120, after: 0 },
  }));

  return out;
}

/* ---------- сводная таблица ---------- */
function summaryTable(lots) {
  const widths = [480, 2600, 1800, 1120, 1180, 1220, CONTENT_TWIP - 480 - 2600 - 1800 - 1120 - 1180 - 1220];
  const head = ['', 'Адрес', 'Тип', 'Площадь, м²', 'Цена, млн ₽', '₽/м²', 'Аренда, ₽/мес'];
  const thin = { style: BorderStyle.SINGLE, size: 2, color: LINE };

  /* Заголовки без разрядки и капители: в колонках по 1100 твипов
     «АРЕНДА, ₽/МЕС» не помещается в строку и рвётся посередине слова. */
  const rows = [new TableRow({
    tableHeader: true,
    cantSplit: true,
    children: head.map((h, i) => cell(
      [p(text(h, { size: 16, bold: true, color: MUTED }),
        { after: 0, line: false, align: i >= 3 ? AlignmentType.RIGHT : undefined })],
      { width: widths[i], fill: TINT, mt: 90, mb: 90 })),
  })];

  lots.forEach((l, i) => {
    const inc = (l.monthlyIncome || {}).income;
    const cells = [
      { v: String(i + 1), color: MUTED },
      { v: l.address, sub: l.district },
      { v: l.kind || '' },
      { v: dec(l.area), right: true },
      { v: mln(l.priceTotal), right: true },
      { v: money(l.pricePerM2), right: true, bold: true },
      { v: inc ? money(inc) : '—', right: true },
    ];
    rows.push(new TableRow({
      cantSplit: true,   // иначе адрес уезжает на страницу выше своего района
      children: cells.map((c, j) => cell([
        p(text(c.v, { size: 17, bold: c.bold, color: c.color || INK }),
          { after: 0, align: c.right ? AlignmentType.RIGHT : undefined }),
        ...(c.sub ? [p(text(c.sub, { size: 15, color: MUTED }), { after: 0 })] : []),
      ], { width: widths[j], mt: 70, mb: 70 })),
    }));
  });

  return new Table({
    width: { size: CONTENT_TWIP, type: WidthType.DXA },
    columnWidths: widths,
    borders: {
      top: thin, bottom: thin, left: NONE, right: NONE,
      insideHorizontal: thin, insideVertical: NONE,
    },
    rows,
  });
}

/* ---------- документ ---------- */
function main() {
  const argv = process.argv.slice(2);
  const arg = (k, d) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : d; };
  const dataPath = arg('--data');
  const outPath = arg('--out', 'document.docx');
  if (!dataPath) { console.error('нужен --data'); process.exit(1); }

  const lots = JSON.parse(fs.readFileSync(dataPath, 'utf8'));
  lots.sort((a, b) => (a.pricePerM2 || 0) - (b.pricePerM2 || 0));

  const ppms = lots.map((l) => l.pricePerM2).filter(Boolean).sort((a, b) => a - b);
  const lo = ppms[0], hi = ppms[ppms.length - 1];
  const med = ppms.length % 2
    ? ppms[(ppms.length - 1) / 2]
    : (ppms[ppms.length / 2 - 1] + ppms[ppms.length / 2]) / 2;
  const totalArea = lots.reduce((s, l) => s + (l.area || 0), 0);
  const totalSum = lots.reduce((s, l) => s + (l.priceTotal || 0), 0);
  const photos = lots.reduce((s, l) => s + (l.images || []).length, 0);
  const districts = new Set(lots.map((l) => l.district).filter(Boolean));

  const children = [];

  children.push(p(text('Коммерческая недвижимость · Москва',
    { size: 16, bold: true, color: ACCENT, caps: true, spacing: 30 }), { after: 140 }));
  children.push(new Paragraph({
    children: [text('Двадцать лотов в центре', { font: SERIF, size: 48, bold: true })],
    heading: HeadingLevel.TITLE,
    spacing: { after: 200 },
  }));
  children.push(p(text(
    'Подборка объектов, выставленных на продажу на Циан: отдельно стоящие здания, '
    + 'помещения свободного назначения, торговые площади и готовый бизнес. Карточки и '
    + `итоговая таблица упорядочены по возрастанию цены за квадратный метр — от ${money(lo)} `
    + `до ${money(hi)} ₽/м².`, { size: 21, color: '3B4855' }), { after: 240, line: 300 }));

  const statW = Math.round(CONTENT_TWIP / 5);
  children.push(new Table({
    width: { size: CONTENT_TWIP, type: WidthType.DXA },
    columnWidths: [statW, statW, statW, statW, CONTENT_TWIP - 4 * statW],
    borders: NO_BORDERS,
    rows: [new TableRow({
      children: [
        [String(lots.length), 'объектов'],
        [money(Math.round(totalArea)), 'м² суммарно'],
        [dec(totalSum / 1e9, 2), 'млрд ₽ суммарно'],
        [money(med), '₽/м² медиана'],
        [String(districts.size), 'районов'],
      ].map(([big, small]) => cell([
        p(text(big, { font: SERIF, size: 24, bold: true }), { after: 0 }),
        p(text(small, { size: 15, color: MUTED }), { after: 0 }),
      ], { width: statW, fill: TINT, mt: 110, mb: 110 })),
    })],
  }));

  children.push(p(text(
    'Данные, фотографии и координаты получены из объявлений Циан; карты — Яндекс Карты '
    + 'по координатам объявления. В исходном списке из 21 ссылки объявление № 331069037 '
    + `встречалось дважды, поэтому объектов двадцать. Фотографий в документе ${photos} — `
    + 'до шести на объект; полные галереи и актуальные цены остаются на Циан.',
    { size: 17, color: MUTED }), { before: 240, after: 0, line: 260 }));

  lots.forEach((lot, i) => children.push(...lotSection(lot, i + 1, false)));

  children.push(new Paragraph({ children: [new PageBreak()] }));
  children.push(new Paragraph({
    children: [text('Сводная таблица', { font: SERIF, size: 32, bold: true })],
    heading: HeadingLevel.HEADING_1,
    spacing: { after: 60 },
  }));
  children.push(p(text('По возрастанию цены за квадратный метр.',
    { size: 18, color: MUTED }), { after: 160 }));
  children.push(summaryTable(lots));
  children.push(p(text('Источник: cian.ru · картография: Яндекс Карты',
    { size: 15, color: MUTED }), { before: 200 }));

  const doc = new Document({
    creator: 'Циан-подборка',
    title: 'Двадцать лотов в центре',
    description: 'Коммерческие объекты Москвы по возрастанию цены за квадратный метр',
    numbering: {
      config: [{
        reference: 'bullets',
        levels: [{
          level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 340, hanging: 200 } } },
        }],
      }],
    },
    styles: {
      default: {
        document: { run: { font: SANS, size: 19, color: INK } },
      },
    },
    sections: [{
      properties: {
        page: {
          size: { width: convertMillimetersToTwip(210), height: convertMillimetersToTwip(297) },
          margin: {
            top: convertMillimetersToTwip(20), bottom: convertMillimetersToTwip(20),
            left: convertMillimetersToTwip(20), right: convertMillimetersToTwip(20),
          },
        },
      },
      children,
    }],
  });

  Packer.toBuffer(doc).then((buf) => {
    fs.writeFileSync(outPath, buf);
    console.log(`${outPath} — ${(buf.length / 1e6).toFixed(1)} МБ, ${lots.length} лотов, `
      + `${photos} фото, ₽/м² от ${money(lo)} до ${money(hi)}, медиана ${money(med)}`);
  });
}

main();
