// Собирает оформленный .docx из msk-sites/README.md, встраивая карты как изображения.
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, ImageRun,
  Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle, PageBreak,
  TableOfContents, Header, Footer, PageNumber, ExternalHyperlink, LevelFormat,
  convertMillimetersToTwip,
} = require('/opt/node22/lib/node_modules/docx');

const ROOT = '/home/user/test/msk-sites';
const MD = fs.readFileSync(path.join(ROOT, 'README.md'), 'utf8')
  .replace('Изображения в папке [`maps/`](maps/) собраны', 'Карты собраны')
  .replace('У каждой площадки — прямая ссылка на интерактивную карту.',
           'У каждой площадки — схема и спутниковый снимок, а также ссылка на интерактивную карту.');

const INK = '111827';      // тёмно-синий, как шапка карт
const ACCENT = 'E22028';   // красный акцент
const MUTED = '6B7280';
const RULE = 'D1D5DB';
const HEAD_FONT = 'Georgia';
const BODY_FONT = 'Calibri';
const OK = '15803D', WARN = 'B45309', BAD = 'B91C1C';

const CONTENT_W = 9638; // ширина текста в DXA при полях 20 мм на A4

// ── эмодзи → типографика ───────────────────────────────────────────────
const BADGE = { '🟢': ['ПОДТВЕРЖДЕНО', OK], '🟡': ['ЧАСТИЧНО', WARN], '🔴': ['НЕ РАСКРЫТО', BAD] };

function stripEmoji(s) {
  return s
    .replace(/🟢/g, '').replace(/🟡/g, '').replace(/🔴/g, '')
    .replace(/📍\s*/g, '').replace(/🗺\s*/g, '').replace(/🖼\s*/g, '▸ ')
    .replace(/❌\s*/g, '— ').replace(/⚠️\s*/g, '! ').replace(/⚠\s*/g, '! ')
    .replace(/\\\*/g, '*')
    .trim();
}

// ── инлайновая разметка: **bold**, *italic*, `code`, [text](url) ────────
function runs(text, base = {}) {
  const out = [];
  const re = /(\[[^\]]+\]\([^)\s]+\))|(\*\*[^*]+\*\*)|(`[^`]+`)|(\*[^*]+\*)/g;
  let last = 0, m;
  const push = (t, extra = {}) => {
    if (!t) return;
    out.push(new TextRun({ text: t, font: BODY_FONT, size: 21, color: '1F2937', ...base, ...extra }));
  };
  while ((m = re.exec(text)) !== null) {
    push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith('[')) {
      const [, label, url] = tok.match(/\[([^\]]+)\]\(([^)\s]+)\)/);
      out.push(new ExternalHyperlink({
        link: url,
        children: [new TextRun({
          text: label.replace(/\*\*/g, ''), font: BODY_FONT, size: 21,
          color: '1D4ED8', underline: {}, ...base,
        })],
      }));
    } else if (tok.startsWith('**')) push(tok.slice(2, -2), { bold: true, color: INK });
    else if (tok.startsWith('`')) push(tok.slice(1, -1), { font: 'Consolas', size: 19, color: '374151' });
    else push(tok.slice(1, -1), { italics: true });
    last = m.index + tok.length;
  }
  push(text.slice(last));
  return out;
}

const P = (text, opts = {}) => new Paragraph({ children: runs(text), spacing: { after: 100, line: 264 }, ...opts });

function bullet(text, level) {
  return new Paragraph({
    children: runs(text),
    numbering: { reference: 'dot', level },
    spacing: { after: 70, line: 264 },
  });
}

// ── таблицы ────────────────────────────────────────────────────────────
function cell(text, { header = false, width, alt = false } = {}) {
  const kids = header
    ? [new TextRun({ text: stripEmoji(text), bold: true, font: BODY_FONT, size: 19, color: 'FFFFFF' })]
    : runs(stripEmoji(text));
  return new TableCell({
    width: { size: width, type: WidthType.DXA },
    shading: { type: ShadingType.CLEAR, fill: header ? INK : (alt ? 'F5F6F8' : 'FFFFFF'), color: 'auto' },
    margins: { top: 70, bottom: 70, left: 110, right: 110 },
    children: [new Paragraph({ children: kids, spacing: { after: 0, line: 240 } })],
  });
}

function buildTable(rows) {
  const n = rows[0].length;
  // первая колонка узкая, если это номер
  const narrow = rows.every(r => r[0].length <= 4);
  let widths;
  if (narrow) {
    const rest = Math.floor((CONTENT_W - 500) / (n - 1));
    widths = [CONTENT_W - rest * (n - 1), ...Array(n - 1).fill(rest)];
  } else {
    const w = Math.floor(CONTENT_W / n);
    widths = [CONTENT_W - w * (n - 1), ...Array(n - 1).fill(w)];
  }
  const border = { style: BorderStyle.SINGLE, size: 2, color: RULE };
  return new Table({
    columnWidths: widths,
    width: { size: CONTENT_W, type: WidthType.DXA },
    borders: { top: border, bottom: border, left: border, right: border, insideHorizontal: border, insideVertical: border },
    rows: rows.map((r, i) => new TableRow({
      tableHeader: i === 0,
      children: r.map((c, j) => cell(c, { header: i === 0, width: widths[j], alt: i % 2 === 0 })),
    })),
  });
}

// ── карты ──────────────────────────────────────────────────────────────
function mapBlock(line) {
  const imgs = [...line.matchAll(/\[(Схема)\]\((maps\/[^)\s]+)\)/g)];
  const yandex = line.match(/\[Яндекс\.Карты\]\((https?:\/\/[^)\s]+)\)/);
  const out = [];
  for (const [, label, rel] of imgs) {
    const file = path.join(ROOT, rel);
    if (!fs.existsSync(file)) continue;
    const ext = rel.endsWith('.jpg') ? 'jpg' : 'png';
    out.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 40 },
      children: [new ImageRun({
        type: ext,
        data: fs.readFileSync(file),
        transformation: { width: 567, height: 327 }, // 15 см по ширине, пропорции 1100×634
      })],
    }));
    out.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 160 },
      children: [new TextRun({
        text: 'Схема расположения · Яндекс.Карты',
        font: BODY_FONT, size: 17, color: MUTED, italics: true,
      })],
    }));
  }
  if (yandex) {
    out.push(new Paragraph({
      spacing: { after: 120 },
      children: [
        new TextRun({ text: 'Интерактивная карта: ', font: BODY_FONT, size: 20, color: MUTED }),
        new ExternalHyperlink({
          link: yandex[1],
          children: [new TextRun({ text: 'открыть в Яндекс.Картах', font: BODY_FONT, size: 20, color: '1D4ED8', underline: {} })],
        }),
      ],
    }));
  }
  return out;
}


// ── рендеры: одиночный во всю ширину, несколько — в две колонки ────────
function figure(rel, caption, widthPx) {
  const file = path.join(ROOT, rel);
  const meta = require('/opt/node22/lib/node_modules/image-size');
  let ratio = 0.6;
  try {
    const d = (meta.imageSize || meta.default || meta)(fs.readFileSync(file));
    ratio = d.height / d.width;
  } catch (e) { /* пропорции по умолчанию */ }
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 80, after: 30 },
      keepNext: true,
      children: [new ImageRun({
        type: rel.endsWith('.png') ? 'png' : 'jpg',
        data: fs.readFileSync(file),
        transformation: { width: widthPx, height: Math.round(widthPx * ratio) },
      })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 140 },
      children: [new TextRun({ text: caption, font: BODY_FONT, size: 16, color: MUTED, italics: true })],
    }),
  ];
}

function renderGrid(items) {
  if (items.length === 1) return figure(items[0].rel, items[0].cap, 492);
  const colW = Math.floor(CONTENT_W / 2);
  const rows = [];
  for (let i = 0; i < items.length; i += 2) {
    const pair = items.slice(i, i + 2);
    rows.push(new TableRow({
      children: [0, 1].map(k => new TableCell({
        width: { size: colW, type: WidthType.DXA },
        margins: { top: 40, bottom: 40, left: 60, right: 60 },
        borders: { top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
                   left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE } },
        children: pair[k] ? figure(pair[k].rel, pair[k].cap, 268)
                          : [new Paragraph({ text: '' })],
      })),
    }));
  }
  return [new Table({
    columnWidths: [colW, CONTENT_W - colW],
    width: { size: CONTENT_W, type: WidthType.DXA },
    borders: { top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
               left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
               insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE } },
    rows,
  }), new Paragraph({ text: '', spacing: { after: 120 } })];
}

// ── заголовки ──────────────────────────────────────────────────────────
function h1(text, { pageBreak = false } = {}) {
  const badgeKey = Object.keys(BADGE).find(k => text.includes(k));
  const kids = [];
  if (pageBreak) kids.push(new PageBreak());
  kids.push(new TextRun({ text: stripEmoji(text), bold: true, font: HEAD_FONT, size: 30, color: INK }));
  const paras = [new Paragraph({
    heading: HeadingLevel.HEADING_1,
    children: kids,
    spacing: { before: pageBreak ? 0 : 320, after: 60 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: ACCENT, space: 6 } },
  })];
  if (badgeKey) {
    const [label, color] = BADGE[badgeKey];
    paras.push(new Paragraph({
      spacing: { before: 100, after: 60 },
      children: [new TextRun({ text: label, bold: true, font: BODY_FONT, size: 17, color, characterSpacing: 20 })],
    }));
  }
  return paras;
}

const h2 = (text) => new Paragraph({
  heading: HeadingLevel.HEADING_2,
  children: [new TextRun({ text: stripEmoji(text), bold: true, font: HEAD_FONT, size: 23, color: ACCENT })],
  spacing: { before: 260, after: 90 },
  keepNext: true,
});

// ── разбор markdown ────────────────────────────────────────────────────
function parse(md) {
  const lines = md.split('\n');
  const body = [];
  let i = 0, firstH1 = true;
  let skipUntilFirstHeading = true; // пропускаем титульные строки — их заменит обложка

  while (i < lines.length) {
    const line = lines[i];

    // таблица
    if (/^\s*\|/.test(line) && /^\s*\|/.test(lines[i + 1] || '') && /^[\s|:-]+$/.test(lines[i + 1])) {
      const rows = [];
      while (i < lines.length && /^\s*\|/.test(lines[i])) {
        if (!/^[\s|:-]+$/.test(lines[i])) {
          rows.push(lines[i].trim().replace(/^\||\|$/g, '').split('|').map(s => s.trim()));
        }
        i++;
      }
      body.push(buildTable(rows));
      body.push(new Paragraph({ text: '', spacing: { after: 160 } }));
      continue;
    }

    if (/^#{1,3}\s/.test(line)) {
      const level = line.match(/^#+/)[0].length;
      const text = line.replace(/^#+\s*/, '');
      if (skipUntilFirstHeading) {              // всё до первого «##» — вводные строки README
        if (level === 2) skipUntilFirstHeading = false;
        else { i++; continue; }
      }
      if (level <= 2) {
        const isSiteCard = /^#\s/.test(line);
        body.push(...h1(text, { pageBreak: isSiteCard && !firstH1 }));
        if (isSiteCard) firstH1 = false;
      } else {
        body.push(h2(text));
      }
      i++;
      continue;
    }

    if (/^---+$/.test(line.trim())) { i++; continue; }

    if (/^>\s?/.test(line)) {
      const quote = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) { quote.push(lines[i].replace(/^>\s?/, '')); i++; }
      body.push(new Paragraph({
        children: runs(stripEmoji(quote.join(' ')), { italics: true, color: '374151' }),
        indent: { left: 280 },
        spacing: { before: 100, after: 180, line: 264 },
        border: { left: { style: BorderStyle.SINGLE, size: 12, color: ACCENT, space: 10 } },
      }));
      continue;
    }

    // группа рендеров: ![alt](renders/..) + курсивная подпись
    if (/^!\[[^\]]*\]\(renders\//.test(line)) {
      const items = [];
      while (i < lines.length) {
        const m = lines[i].match(/^!\[([^\]]*)\]\((renders\/[^)\s]+)\)\s*$/);
        if (!m) { if (lines[i].trim() === '') { i++; continue; } break; }
        let cap = m[1];
        if (i + 1 < lines.length && /^\*[^*]+\*\s*$/.test(lines[i + 1])) {
          cap = lines[i + 1].trim().replace(/^\*|\*$/g, '');
          i++;
        }
        items.push({ rel: m[2], cap });
        i++;
      }
      body.push(...renderGrid(items));
      continue;
    }

    // маркированный список
    if (/^(\s*)-\s+/.test(line)) {
      const indent = line.match(/^(\s*)/)[1].length;
      let text = line.replace(/^\s*-\s+/, '');
      // продолжения строк
      while (i + 1 < lines.length && /^\s{2,}\S/.test(lines[i + 1]) && !/^\s*-\s+/.test(lines[i + 1])) {
        text += ' ' + lines[i + 1].trim();
        i++;
      }
      if (/\[Схема\]/.test(text)) body.push(...mapBlock(text));
      else body.push(bullet(stripEmoji(text), indent >= 2 ? 1 : 0));
      i++;
      continue;
    }

    if (line.trim() === '' || skipUntilFirstHeading) { i++; continue; }

    // обычный абзац
    let text = line.trim();
    while (i + 1 < lines.length && lines[i + 1].trim() !== '' && !/^[#>|-]/.test(lines[i + 1].trim())) {
      text += ' ' + lines[i + 1].trim();
      i++;
    }
    body.push(P(stripEmoji(text)));
    i++;
  }
  return body;
}

// ── обложка ────────────────────────────────────────────────────────────
const cover = [
  new Paragraph({ text: '', spacing: { after: 1900 } }),
  new Paragraph({
    spacing: { after: 60 },
    children: [new TextRun({ text: 'ИНФОРМАЦИОННАЯ СПРАВКА', font: BODY_FONT, size: 20, color: ACCENT, bold: true, characterSpacing: 60 })],
  }),
  new Paragraph({
    spacing: { after: 140 },
    children: [new TextRun({ text: 'Площадки будущих ЖК в Москве', font: HEAD_FONT, size: 52, bold: true, color: INK })],
    border: { bottom: { style: BorderStyle.SINGLE, size: 14, color: ACCENT, space: 10 } },
  }),
  new Paragraph({
    spacing: { before: 220, after: 60 },
    children: [new TextRun({ text: 'Сет из 15 площадок · 14 карточек объектов', font: BODY_FONT, size: 24, color: '374151' })],
  }),
  new Paragraph({
    spacing: { after: 900 },
    children: [new TextRun({ text: 'Расположение · характеристики и застройщик · рендеры · старт продаж и цены · последние новости', font: BODY_FONT, size: 20, color: MUTED, italics: true })],
  }),
  new Paragraph({
    spacing: { after: 40 },
    children: [new TextRun({ text: 'Подготовлено 28 июля 2026 г.', font: BODY_FONT, size: 20, color: INK, bold: true })],
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Картография: © Яндекс, © Яндекс.Карты · геокодирование: OpenStreetMap / Nominatim', font: BODY_FONT, size: 17, color: MUTED })],
  }),
  new Paragraph({ children: [new PageBreak()] }),
  new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { after: 200 },
    children: [new TextRun({ text: 'Содержание', bold: true, font: HEAD_FONT, size: 30, color: INK })],
    border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: ACCENT, space: 6 } },
  }),
  new TableOfContents('Содержание', { hyperlink: true, headingStyleRange: '1-1' }),
  new Paragraph({ children: [new PageBreak()] }),
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'Площадки будущих ЖК в Москве',
  description: 'Справка по 15 площадкам: расположение, застройщик, рендеры, цены, новости',
  features: { updateFields: true },
  numbering: {
    config: [{
      reference: 'dot',
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 340, hanging: 200 } } } },
        { level: 1, format: LevelFormat.BULLET, text: '–', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 660, hanging: 200 } } } },
      ],
    }],
  },
  styles: {
    default: {
      document: { run: { font: BODY_FONT, size: 21, color: '1F2937' } },
      heading1: { run: { font: HEAD_FONT, size: 30, bold: true, color: INK } },
      heading2: { run: { font: HEAD_FONT, size: 23, bold: true, color: ACCENT } },
    },
  },
  sections: [{
    properties: {
      page: {
        margin: {
          top: convertMillimetersToTwip(20), bottom: convertMillimetersToTwip(18),
          left: convertMillimetersToTwip(20), right: convertMillimetersToTwip(20),
        },
      },
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          alignment: AlignmentType.RIGHT,
          spacing: { after: 60 },
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } },
          children: [new TextRun({ text: 'Площадки будущих ЖК в Москве · июль 2026', font: BODY_FONT, size: 16, color: MUTED })],
        })],
      }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ children: [PageNumber.CURRENT], font: BODY_FONT, size: 17, color: MUTED })],
        })],
      }),
    },
    children: [...cover, ...parse(MD)],
  }],
});

Packer.toBuffer(doc).then(buf => {
  const out = path.join(ROOT, 'Справка_площадки_ЖК_Москва.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, (buf.length / 1e6).toFixed(1) + ' MB');
});
