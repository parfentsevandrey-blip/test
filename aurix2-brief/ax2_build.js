const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'ax2_tables.json'), 'utf8'));
const IMG = (n) => {
  for (const d of ['assets', 'out']) {
    const p = path.join(__dirname, d, n);
    if (fs.existsSync(p)) return fs.readFileSync(p);
  }
  throw new Error('не найдена картинка ' + n);
};

// ── palette ────────────────────────────────────────────────────────────────
const INK = '1F2A44', BRONZE = 'A9762F', MUTED = '70788A',
      LINE = 'D9D4CB', SOFT = 'F5F2ED', HEAD = '1F2A44', RED = 'B3282D';
const CONTENT_W = 9638;            // A4 minus 20 mm side margins, in DXA
const MEASURE = 1560;              // right indent for running text: ~14,2 cm ≈ 80 знаков
const PX = 643;                    // same width in px @96dpi

const S = { GEO: 'Georgia', SANS: 'Arial' };

// ── helpers ────────────────────────────────────────────────────────────────
const noBorder = { style: BorderStyle.NONE, size: 0, color: 'auto' };
const hair = (color = LINE) => ({ style: BorderStyle.SINGLE, size: 4, color });

const p = (opts) => new Paragraph(opts);

const txt = (text, o = {}) => new TextRun({
  text, font: o.font || S.SANS, size: o.size || 19, bold: !!o.bold,
  italics: !!o.italics, color: o.color || '2A2E38', characterSpacing: o.spacing,
  allCaps: o.caps,
});

const body = (text, o = {}) => p({
  children: Array.isArray(text) ? text : [txt(text, o)],
  spacing: { after: o.after === undefined ? 100 : o.after, line: o.line || 258, lineRule: LR },
  alignment: o.align, indent: o.indent || { right: MEASURE },
});

const kicker = (text, color = BRONZE) => p({
  children: [txt(text, { size: 16, bold: true, color, spacing: 60, caps: true })],
  spacing: { after: 90 },
});

const h1 = (text, o = {}) => p({
  children: [txt(text, { font: S.GEO, size: 36, bold: true, color: INK })],
  spacing: { after: 160 }, pageBreakBefore: !!o.br, keepNext: true, keepLines: true,
  border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BRONZE, space: 8 } },
});

const h2 = (text, o = {}) => p({
  children: [txt(text, { font: S.GEO, size: 24, bold: true, color: INK })],
  spacing: { before: o.br ? 0 : 170, after: 110 }, pageBreakBefore: !!o.br,
  keepNext: true, keepLines: true,
});

const caption = (text) => p({
  children: [txt(text, { size: 16, color: MUTED, italics: true })],
  spacing: { after: 220 }, indent: { right: MEASURE },
});

const note = (text) => p({
  children: [txt(text, { size: 15, color: MUTED })],
  spacing: { after: 60, line: 230, lineRule: LR }, indent: { right: MEASURE },
});

const image = (file, w, h, o = {}) => p({
  children: [new ImageRun({ data: IMG(file), type: 'jpg', transformation: { width: w, height: h } })],
  spacing: { after: o.after === undefined ? 80 : o.after },
  alignment: AlignmentType.CENTER,
});

const spacer = (n = 120) => p({ children: [txt('')], spacing: { after: n } });

const rule = (n = 160) => p({
  children: [txt('')],
  spacing: { after: n },
  border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: LINE, space: 2 } },
});

// generic data table
function dataTable(headers, rows, widths, o = {}) {
  const cell = (text, { bold, align, fill, color, size, w, last, first, top } = {}) =>
    new TableCell({
      width: { size: w, type: WidthType.DXA },
      shading: fill ? { type: ShadingType.CLEAR, fill, color: 'auto' } : undefined,
      margins: { top: 52, bottom: 52, left: 100, right: 100 },
      verticalAlign: VerticalAlign.CENTER,
      borders: {
        top: top || hair(),
        bottom: hair(),
        left: noBorder, right: noBorder,
      },
      children: [p({
        children: (text && typeof text === 'object' && text.link)
          ? [new ExternalHyperlink({
              children: [txt(text.text, { bold, size: size || 17, color: '2C5FA8' })],
              link: text.link })]
          : [txt(text, { bold, color: color || '2A2E38', size: size || 17 })],
        alignment: align, spacing: { after: 0, line: 240, lineRule: LR },
      })],
    });

  const headRow = new TableRow({
    tableHeader: true,
    children: headers.map((hd, i) => cell(hd, {
      bold: true, w: widths[i], fill: HEAD, color: 'FFFFFF', size: 16,
      align: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER,
      top: { style: BorderStyle.SINGLE, size: 4, color: HEAD },
    })),
  });

  const bodyRows = rows.map((r, ri) => {
    const isTotal = o.totalLast && ri === rows.length - 1;
    return new TableRow({
      children: r.map((c, i) => cell(c, {
        w: widths[i],
        bold: isTotal || (o.boldFirstCol && i === 0),
        fill: isTotal ? SOFT : (ri % 2 === 1 ? 'FBFAF8' : undefined),
        align: (i === 0 || (o.leftCols || []).includes(i)) ? AlignmentType.LEFT : AlignmentType.CENTER,
        color: isTotal ? INK : undefined,
      })),
    });
  });

  return new Table({
    columnWidths: widths,
    width: { size: widths.reduce((a, b) => a + b, 0), type: WidthType.DXA },
    rows: [headRow, ...bodyRows],
  });
}

// four hero stat tiles — the headline numbers of the project
function statTiles(items) {
  const w = Math.floor(CONTENT_W / items.length);
  const W = items.map((_, i) => (i === items.length - 1 ? CONTENT_W - w * (items.length - 1) : w));
  return new Table({
    columnWidths: W,
    width: { size: CONTENT_W, type: WidthType.DXA },
    rows: [new TableRow({
      cantSplit: true,
      children: items.map(([val, lab, accent], i) => new TableCell({
        width: { size: W[i], type: WidthType.DXA },
        shading: { type: ShadingType.CLEAR, fill: accent ? 'F7EEEE' : SOFT, color: 'auto' },
        margins: { top: 118, bottom: 118, left: 170, right: 110 },
        verticalAlign: VerticalAlign.CENTER,
        borders: {
          top: { style: BorderStyle.SINGLE, size: 18, color: accent ? RED : BRONZE },
          bottom: noBorder,
          left: { style: BorderStyle.SINGLE, size: 26, color: 'FCFCFB' },
          right: { style: BorderStyle.SINGLE, size: 26, color: 'FCFCFB' },
        },
        children: [
          p({ children: [txt(val, { font: S.GEO, size: 27, bold: true, color: accent ? RED : INK })],
              spacing: { after: 40, line: 240, lineRule: LR } }),
          p({ children: [txt(lab, { size: 13, color: MUTED, caps: true, spacing: 24 })],
              spacing: { after: 0, line: 200, lineRule: LR } }),
        ],
      })),
    })],
  });
}

// two-column "label / value" fact sheet (4 columns = 2 pairs per row)
function factSheet(pairs) {
  const W = [1900, 2919, 1900, 2919];
  const rows = [];
  for (let i = 0; i < pairs.length; i += 2) {
    const cells = [];
    const shade = (i / 2) % 2 === 0 ? 'FAF8F5' : null;
    [pairs[i], pairs[i + 1] || ['', '']].forEach(([k, v], j) => {
      const common = {
        shading: shade ? { type: ShadingType.CLEAR, fill: shade, color: 'auto' } : undefined,
        verticalAlign: VerticalAlign.CENTER,
        borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
      };
      cells.push(new TableCell({
        ...common,
        width: { size: W[j * 2], type: WidthType.DXA },
        margins: { top: 58, bottom: 58, left: j === 0 ? 170 : 220, right: 60 },
        children: [p({ children: [txt(k, { size: 14, color: MUTED, caps: true, spacing: 16 })],
                       spacing: { after: 0, line: 220, lineRule: LR } })],
      }));
      cells.push(new TableCell({
        ...common,
        width: { size: W[j * 2 + 1], type: WidthType.DXA },
        margins: { top: 58, bottom: 58, left: 60, right: j === 1 ? 170 : 60 },
        children: [p({ children: [txt(v, { size: 17, bold: true, color: INK })],
                       spacing: { after: 0, line: 220, lineRule: LR } })],
      }));
    });
    rows.push(new TableRow({ children: cells }));
  }
  return new Table({ columnWidths: W, width: { size: CONTENT_W, type: WidthType.DXA }, rows });
}

// side-by-side images
// Карточки лотов: планировка + параметры, N в ряд.
function flatCards(cards, cols = 3) {
  const w = Math.floor(9520 / cols), gap = 90;
  const pxw = Math.floor((w - gap * 2) / 15);   // DXA -> px при 96 dpi
  const cellOf = (c, i) => new TableCell({
    width: { size: w, type: WidthType.DXA },
    margins: { top: 0, bottom: 0, left: i === 0 ? 0 : gap, right: i === cols - 1 ? 0 : gap },
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    shading: { type: ShadingType.CLEAR, fill: SOFT, color: 'auto' },
    children: c ? [
      p({
        children: [new ImageRun({ data: IMG(c.file), type: 'png',
          transformation: { width: pxw, height: pxw } })],
        spacing: { after: 20 }, alignment: AlignmentType.CENTER,
      }),
      p({ children: [txt(`${c.rooms}  № ${c.number}`, { size: 15, color: MUTED })],
          spacing: { after: 20 }, alignment: AlignmentType.CENTER }),
      p({ children: [txt(c.area, { size: 30, bold: true, color: INK, font: S.GEO }),
                     txt(' м²', { size: 16, color: MUTED })],
          spacing: { after: 30 }, alignment: AlignmentType.CENTER }),
      p({ children: [txt(`корпус ${c.building}  ·  этаж ${c.floor}`, { size: 14, color: MUTED })],
          spacing: { after: 40 }, alignment: AlignmentType.CENTER }),
      p({ children: [txt(c.price + ' ₽', { size: 19, bold: true, color: INK })],
          spacing: { after: 16 }, alignment: AlignmentType.CENTER }),
      p({ children: [txt(c.ppm + ' ₽ за м²', { size: 14, color: BRONZE })],
          spacing: { after: 0 }, alignment: AlignmentType.CENTER }),
    ] : [p({ children: [txt('')] })],
  });
  const rows = [];
  for (let i = 0; i < cards.length; i += cols) {
    const chunk = cards.slice(i, i + cols);
    while (chunk.length < cols) chunk.push(null);
    rows.push(new TableRow({ cantSplit: true, children: chunk.map(cellOf) }));
    rows.push(new TableRow({
      cantSplit: true,
      children: chunk.map(() => new TableCell({
        width: { size: w, type: WidthType.DXA },
        borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
        children: [p({ children: [txt('')], spacing: { after: 0, line: 200, lineRule: LR } })],
      })),
    }));
  }
  return new Table({
    columnWidths: Array(cols).fill(w),
    width: { size: w * cols, type: WidthType.DXA },
    rows,
  });
}

function imageTrio(files, caps) {
  const w = 3173, pxw = 203, pxh = Math.round(pxw * 9 / 16);
  const cellOf = (file, cap, i) => new TableCell({
    width: { size: w, type: WidthType.DXA },
    margins: { top: 0, bottom: 0, left: i === 0 ? 0 : 70, right: i === 2 ? 0 : 70 },
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    children: [
      p({
        children: [new ImageRun({ data: IMG(file), type: 'jpg', transformation: { width: pxw, height: pxh } })],
        spacing: { after: 50 },
      }),
      p({ children: [txt(cap, { size: 14, color: MUTED, italics: true })], spacing: { after: 0, line: 210, lineRule: LR } }),
    ],
  });
  return new Table({
    columnWidths: [w, w, w], width: { size: 9519, type: WidthType.DXA },
    rows: [new TableRow({ children: files.map((f, i) => cellOf(f, caps[i], i)) })],
  });
}

function imagePair(a, b, capA, capB) {
  const w = 4760, pxw = 310, pxh = Math.round(pxw * 9 / 16);
  const cellOf = (file, cap, left) => new TableCell({
    width: { size: w, type: WidthType.DXA },
    margins: { top: 0, bottom: 0, left: left ? 0 : 110, right: left ? 110 : 0 },
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    children: [
      p({
        children: [new ImageRun({ data: IMG(file), type: 'jpg', transformation: { width: pxw, height: pxh } })],
        spacing: { after: 60 },
      }),
      p({ children: [txt(cap, { size: 15, color: MUTED, italics: true })], spacing: { after: 0, line: 220, lineRule: LR } }),
    ],
  });
  return new Table({
    columnWidths: [w, w], width: { size: 9520, type: WidthType.DXA },
    rows: [new TableRow({ children: [cellOf(a, capA, true), cellOf(b, capB, false)] })],
  });
}

function planBlock(pl, pxw, pxh) {
  const W = [4300, 5338];
  const spec = [['Площадь', `${pl.area} м²`], ['Этаж', pl.floor], ['Секция', pl.corp],
                ['Цена', `${pl.price} млн ₽`], ['Цена за м²', `${pl.ppm} ₽`], ['Отделка', 'Без отделки']];
  return new Table({
    columnWidths: W,
    width: { size: CONTENT_W, type: WidthType.DXA },
    rows: [new TableRow({ children: [
      new TableCell({
        width: { size: W[0], type: WidthType.DXA },
        margins: { top: 60, bottom: 60, left: 0, right: 200 },
        verticalAlign: VerticalAlign.TOP,
        borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
        children: [p({
          children: [new ImageRun({ data: IMG(pl.img), type: 'jpg',
                                    transformation: { width: pxw, height: pxh } })],
          spacing: { after: 0 },
        })],
      }),
      new TableCell({
        width: { size: W[1], type: WidthType.DXA },
        margins: { top: 60, bottom: 60, left: 0, right: 0 },
        verticalAlign: VerticalAlign.TOP,
        borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
        children: [
          p({ children: [txt(`${pl.rooms}-комнатная квартира`, { font: S.GEO, size: 26, bold: true, color: INK })],
              spacing: { after: 130, line: 240, lineRule: LR } }),
          ...spec.map(([k, v], i) => p({
            children: [txt(k, { size: 15, color: MUTED, caps: true, spacing: 16 }),
                       txt('\t', {}), txt(v, { size: 18, bold: true, color: INK })],
            tabStops: [{ type: D.TabStopType.LEFT, position: 2100 }],
            spacing: { after: 78, line: 230, lineRule: LR },
          })),
          p({ children: [new ExternalHyperlink({
                children: [txt('Смотреть лот на Циан →', { size: 16, color: '2C5FA8' })], link: pl.url })],
              spacing: { before: 60, after: 0, line: 230, lineRule: LR } }),
        ],
      }),
    ] })],
  });
}

function photoCards(cards, pxw, pxh) {
  const W = [4819, 4819];
  const cellOf = (c, left) => new TableCell({
    width: { size: W[0], type: WidthType.DXA },
    margins: { top: 0, bottom: 150, left: left ? 0 : 150, right: left ? 150 : 0 },
    verticalAlign: VerticalAlign.TOP,
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    children: c ? [
      p({ children: [new ImageRun({ data: IMG(c.img), type: 'jpg',
                                    transformation: { width: pxw, height: pxh } })],
          spacing: { after: 80 } }),
      p({ children: [txt(c.title, { size: 18, bold: true, color: c.our ? RED : INK })],
          spacing: { after: 40, line: 226, lineRule: LR } }),
      p({ children: [txt(c.spec, { size: 15, color: MUTED })],
          spacing: { after: 40, line: 226, lineRule: LR } }),
      p({ children: [txt(c.price, { size: 18, bold: true, color: BRONZE }),
                     txt(`   ${c.ppm}`, { size: 15, color: MUTED })],
          spacing: { after: 40, line: 226, lineRule: LR } }),
      p({ children: [new ExternalHyperlink({
            children: [txt('Объявление →', { size: 14, color: '2C5FA8' })], link: c.url })],
          spacing: { after: 0, line: 216, lineRule: LR } }),
    ] : [p({ children: [txt('')] })],
  });
  const rows = [];
  for (let i = 0; i < cards.length; i += 2) {
    rows.push(new TableRow({ children: [cellOf(cards[i], true), cellOf(cards[i + 1], false)] }));
  }
  return new Table({ columnWidths: W, width: { size: CONTENT_W, type: WidthType.DXA }, rows });
}

const bullets = (items) => items.map((t) => p({
  children: [txt('—   ', { color: BRONZE, bold: true }), ...(Array.isArray(t) ? t : [txt(t)])],
  spacing: { after: 58, line: 252, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
}));


// ── content ────────────────────────────────────────────────────────────────
const TILES = [
  ['2', 'Проекта в Москве'],
  ['20', 'Лотов в продаже'],
  ['4,1 млрд ₽', 'Сумма всех лотов'],
  ['26.08.2026', 'Дата среза', true],
];
const SRC = [
  ['Параметры проектов, визуализации и координаты — страницы проектов на сайте застройщика', 'https://aurix-development.ru/projects'],
  ['Поквартирные данные: площадь, комнатность, корпус, этаж, цена, цена за м² и ссылки — подбор недвижимости на сайте застройщика, срез 26.08.2026', 'https://aurix-development.ru/vybor-po-parametram/kvartiry'],
  ['Планировки — карточки лотов на том же сайте; в справку взят вариант «с мебелью»', 'https://aurix-development.ru/vybor-po-parametram/kvartiry'],
  ['Часть визуализаций — лендинги arbat2.ru и omega-residence.com. В подвале обоих указано, что официальными сайтами застройщика они не являются; числовые данные оттуда не брались', 'https://omega-residence.com/'],
  ['Координаты станций метро и расстояния по прямой — OpenStreetMap. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'AURIX — «Арбат 2» и «Резиденция Омега»',
  description: 'Выгрузка по двум московским проектам AURIX: параметры, особенности, визуализации, карты, цены и планировки',
  styles: { default: { document: {
    run: { font: S.SANS, size: 19, color: '2A2E38' },
    paragraph: { spacing: { line: 258, lineRule: LR } } } } },
  sections: [{
    properties: { page: { margin: {
      top: convertMillimetersToTwip(15), bottom: convertMillimetersToTwip(15),
      left: convertMillimetersToTwip(20), right: convertMillimetersToTwip(20),
      header: convertMillimetersToTwip(9), footer: convertMillimetersToTwip(9) } } },
    footers: { default: new Footer({ children: [p({
      children: [
        txt('AURIX · «Арбат 2» и «Резиденция Омега» · срез 26.08.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Выгрузка по проектам · Москва · 26 августа 2026'),
      p({ children: [txt('AURIX', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('«Арбат 2» и «Резиденция Омега» — два московских проекта в продаже', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Параметры  ·  особенности  ·  расположение  ·  визуализации  ·  цены  ·  планировки', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('О выгрузке', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(K.facts),
      spacer(40),
      note('Данные взяты из подбора недвижимости на сайте застройщика — это единственное место, где AURIX публикует цены поквартирно. Всего в подборе 149 лотов по четырём проектам бренда, из них 20 приходится на два московских: 17 в «Арбате 2» и 3 в «Резиденции Омега». Цены — на 26.08.2026 и меняются; ссылка в таблице ведёт на карточку конкретного лота.'),
      spacer(40),
      kicker('Оба проекта на карте Москвы', INK),
      image('map.jpg', PX, 391, { after: 46 }),
      note('Между домами 6,4 км. «Арбат 2» стоит на Новом Арбате, в двухстах метрах от Кремля по прямой; «Резиденция Омега» — в глубине Гагаринского района, рядом с Воробьёвыми горами и научным кластером вокруг МГУ.'),

      // ═══════════════ АРБАТ 2 ═══════════════
      h1('Арбат 2', { br: true }),
      body('Deluxe-класс на Новом Арбате. Реконструкция «Дома связи» 1965 года; дом построен в 2025 году, продаются квартиры на двух верхних жилых этажах.', { after: 150 }),
      image('ar_hero.jpg', PX, 245, { after: 30 }),
      caption('Фасад с Нового Арбата: зеркальные плоскости с наложением декоративных ламелей.'),
      spacer(32),
      imageTrio(['ar_a.jpg', 'ar_j.jpg', 'ar_c.jpg'],
        ['Тот же фасад вечером', 'Геометрия ламелей вблизи', 'Новый Арбат ночью']),
      spacer(32),
      imageTrio(['ar_d.jpg', 'ar_e.jpg', 'ar_f.jpg'],
        ['Гостиная с видом на город', 'Камин в общей зоне', 'Спальня']),
      spacer(32),
      imageTrio(['ar_g.jpg', 'ar_h.jpg', 'ar_i.jpg'],
        ['Лобби-бар', 'Фитнес-зал', 'Переговорная']),
      spacer(28),
      note('Визуализации — с сайта застройщика и лендинга проекта. Дом построен, но интерьеры показаны в проектных изображениях.'),

      h1('Арбат 2 — характеристики и особенности', { br: true }),
      factSheet(K.cardArbat),
      spacer(40),
      ...bullets(K.featArbat.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(30),
      image('map_arbat.jpg', PX, 340, { after: 30 }),
      caption('Красное — дом, тёмное — станция метро с расстоянием по прямой.'),
      spacer(24),
      note('До «Арбатской» 174 метра. В пешей доступности Кремль, Александровский сад, Дом Пашкова, театр Вахтангова и Старый Арбат; офис продаж — на Кутузовском проспекте.'),

      h1('Арбат 2 — цены', { br: true }),
      body('Семнадцать лотов на 10-м и 11-м этажах, от однокомнатных 46,6 м² до четырёхкомнатной 168,7 м². Метр держится в узком коридоре 2,71–2,97 млн ₽, средневзвешенный — 2 822 тыс. ₽.', { after: 170 }),
      dataTable(
        ['Площадь,\nм²', 'Комнат', 'Цена, ₽', 'Цена за м², ₽', 'Этаж', 'Лот'],
        K.priceArbat, [1250, 1350, 2100, 1900, 1400, 1638],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Ссылка в последней колонке открывает карточку лота на сайте застройщика. Цены на 26.08.2026, публичной офертой не являются.'),
      spacer(36),
      ...bullets([
        [txt('Разброс цены метра — всего 10 % между самым дешёвым и самым дорогим лотом. '), txt('От 2 706 тыс. ₽ до 2 968 тыс. ₽. Для готового дома это признак того, что прайс сформирован разом, а не по мере продаж: обычно к концу реализации разброс шире.', { bold: true })],
        [txt('Восемь однокомнатных, семь двухкомнатных, одна трёхкомнатная и одна четырёхкомнатная. '), txt('Половина предложения — лоты до 55 м². В deluxe-сегменте это нетипично мелкий формат, и он согласуется с тем, что дом рассчитан в том числе на сдачу в аренду: управляющая команда берёт на себя подбор жильцов.', { bold: true })],
        [txt('Сумма всех семнадцати лотов — 3,42 млрд ₽ за 1 212 м². '), txt('При 23 квартирах в доме это означает, что распродано около четверти, а основной объём предложения сосредоточен на двух верхних жилых этажах.', { bold: true })],
        [txt('Одиннадцатый этаж дороже десятого на 2,2 % по метру: 2 863 тыс. ₽ против 2 800 тыс. '), txt('Надбавка за этаж есть, но она символическая. Крупные лоты тоже дороже мелких всего на 1,3 % по метру — то есть скидки за объём в этом прайсе нет, и цена почти линейна по площади.', { bold: true })],
      ]),

      h1('Арбат 2 — планировки', { br: true }),
      body('Семнадцать лотов в порядке возрастания площади. Планировки — с карточек лотов, вариант с расстановкой мебели.', { after: 170 }),
      flatCards(K.cardsArbat, 3),

      // ═══════════════ РЕЗИДЕНЦИЯ ОМЕГА ═══════════════
      h1('Резиденция Омега', { br: true }),
      body('Премиальный клубный дом в Гагаринском районе на 59 квартир. Строится, сдача заявлена на 2030 год; в продаже три лота.', { after: 150 }),
      image('om_hero.jpg', PX, 245, { after: 30 }),
      caption('Фасады построены на ритме, который авторы сравнивают с делениями циферблата.'),
      spacer(32),
      imageTrio(['om_a.jpg', 'om_b.jpg', 'om_c.jpg'],
        ['Вид с МГУ на заднем плане', 'Дом в окружении квартала', 'Уличный ракурс']),
      spacer(32),
      imageTrio(['om_d.jpg', 'om_e.jpg', 'om_f.jpg'],
        ['Аркада и закрытый двор', 'Двор вечером', 'Верхние этажи с террасами']),
      spacer(32),
      imageTrio(['om_g.jpg', 'om_h.jpg', 'om_i.jpg'],
        ['Гостиная с видом на зелень', 'Фитнес-зал в гранд-лобби', 'Подземный паркинг']),
      spacer(28),
      note('Визуализации — с сайта застройщика и лендинга проекта. Дом не построен: итоговый облик может отличаться.'),

      h1('Резиденция Омега — характеристики и особенности', { br: true }),
      factSheet(K.cardOmega),
      spacer(40),
      ...bullets(K.featOmega.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(30),
      image('map_omega.jpg', PX, 340, { after: 30 }),
      caption('Красное — дом, тёмное — станция метро с расстоянием по прямой.'),
      spacer(24),
      note('До «Воробьёвых гор» 1,19 км, до «Академической» 1,5 км, до «Ленинского проспекта» 1,76 км. Пешей доступности метро у дома нет — это сознательный выбор локации в глубине квартала, но фактор стоит учитывать.'),

      h1('Резиденция Омега — цены и планировки', { br: true }),
      body('Три лота на 4-м, 5-м и 6-м этажах: одна двухкомнатная и две трёхкомнатных. Метр 1,92–2,13 млн ₽ — заметно ниже, чем в «Арбате 2».', { after: 170 }),
      dataTable(
        ['Площадь,\nм²', 'Комнат', 'Цена, ₽', 'Цена за м², ₽', 'Этаж', 'Лот'],
        K.priceOmega, [1250, 1350, 2100, 1900, 1400, 1638],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Ссылка в последней колонке открывает карточку лота на сайте застройщика. Цены на 26.08.2026, публичной офертой не являются.'),
      spacer(40),
      flatCards(K.cardsOmega, 3),
      spacer(30),
      note('Три лота из 59 квартир — это витрина, а не полный прайс. Пентхаусы от 218 м² с террасами 22–89 м², о которых пишет застройщик, в открытую продажу пока не выведены.'),

      // ═══════════════ СРАВНЕНИЕ ═══════════════
      h1('Два проекта рядом'),
      dataTable(
        ['Параметр', 'Арбат 2', 'Резиденция Омега'],
        K.compare, [3100, 3269, 3269],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(36),
      ...bullets([
        [txt('Метр в «Арбате 2» дороже на 43 %: 2 822 тыс. ₽ против 1 974 тыс. '), txt('Разрыв объясняется двумя вещами сразу. «Арбат 2» построен и передаётся сейчас, а «Омега» сдаётся в 2030 году — четыре года ожидания стоят денег. И адрес: Новый Арбат против Гагаринского района, где метр по рынку в полтора раза дешевле.', { bold: true })],
        [txt('Паркинг устроен противоположно. '), txt('В «Арбате 2» 18 мест на 23 квартиры, в «Омеге» — 66 на 59. То есть в готовом доме в центре мест меньше, чем квартир, а в строящемся — больше одного на квартиру. Для покупателя на Новом Арбате это означает, что место в паркинге нужно закладывать в бюджет отдельно и заранее.', { bold: true })],
        [txt('Форматы почти не пересекаются. '), txt('В «Арбате 2» продаются лоты от 46,6 до 168,7 м², половина — до 55 м². В «Омеге» открыты 89,8–134,1 м². Пересечение есть только в середине диапазона, и покупатель выбирает скорее между «маленькой квартирой в готовом доме на Новом Арбате» и «полноценной квартирой в строящемся доме у Воробьёвых гор».', { bold: true })],
        [txt('Доступность метро различается в семь раз. '), txt('174 метра до «Арбатской» против 1,19 км до «Воробьёвых гор». В премиальном сегменте это не главный критерий, но у «Омеги» это единственный заметный минус локации: во всём остальном — парки, МГУ, научный кластер и тишина квартала — она выигрывает.', { bold: true })],
      ]),

      spacer(20),
      rule(100),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 32, line: 206, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      note('Выгрузка сделана 26.08.2026 по открытым данным сайта застройщика. Цены и состав предложения меняются; документ носит справочный характер и не является офертой.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'AURIX_Арбат2_и_Омега_выгрузка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
