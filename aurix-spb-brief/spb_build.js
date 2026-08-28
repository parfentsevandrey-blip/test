const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'spb_tables.json'), 'utf8'));
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
  ['2', 'Проекта в Петербурге'],
  ['129', 'Лотов в продаже'],
  ['15,2 млрд ₽', 'Сумма всех лотов'],
  ['26.08.2026', 'Дата среза', true],
];
const SRC = [
  ['Параметры проектов, визуализации и координаты — страницы проектов на сайте застройщика', 'https://aurix-development.ru/projects'],
  ['Поквартирные данные: площадь, комнатность, корпус, этаж, цена, цена за м² и ссылки — подбор недвижимости на сайте застройщика, срез 26.08.2026', 'https://aurix-development.ru/vybor-po-parametram/kvartiry'],
  ['Планировки — карточки лотов на том же сайте; в справку взят вариант «с мебелью»', 'https://aurix-development.ru/vybor-po-parametram/kvartiry'],
  ['Координаты станций метро и расстояния по прямой — OpenStreetMap. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'AURIX — «ЛДМ» и «Мариинка Делюкс»',
  description: 'Выгрузка по двум петербургским проектам AURIX: параметры, особенности, визуализации, карты, цены и планировки',
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
        txt('AURIX · «ЛДМ» и «Мариинка Делюкс» · срез 26.08.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Выгрузка по проектам · Санкт-Петербург · 26 августа 2026'),
      p({ children: [txt('AURIX', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('«ЛДМ» и «Мариинка Делюкс» — два петербургских проекта в продаже', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Параметры  ·  особенности  ·  расположение  ·  визуализации  ·  цены  ·  планировки', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('О выгрузке', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(K.facts),
      spacer(40),
      note('Данные взяты из подбора недвижимости на сайте застройщика — это единственное место, где AURIX публикует цены поквартирно. Всего в подборе 149 лотов по четырём проектам бренда, и 129 из них приходится на два петербургских: 110 в «ЛДМ» и 19 в «Мариинке Делюкс». То есть Петербург — это почти весь объём открытого предложения AURIX. Цены на 26.08.2026 и меняются; ссылка в таблице ведёт на карточку конкретного лота.'),
      spacer(40),
      kicker('Оба проекта на карте Петербурга', INK),
      image('map.jpg', PX, 391, { after: 46 }),
      note('Между домами 5,3 км по прямой. «ЛДМ» стоит на Аптекарском острове у Малой Невки, «Мариинка Делюкс» — на Матисовом острове, на берегу Пряжки, в западной части Адмиралтейского района.'),

      // ═══════════════ ЛДМ ═══════════════
      h1('ЛДМ', { br: true }),
      body('Премиум-класс на Аптекарском острове, на месте бывшего Ленинградского дворца молодёжи. Семь многоквартирных корпусов и восемь сити-вилл, готовность — 2027 год.', { after: 150 }),
      image('ldm_hero.jpg', PX, 245, { after: 30 }),
      caption('Вид с воды: корпуса вдоль Песочной набережной Малой Невки.'),
      spacer(32),
      imageTrio(['ldm_a.jpg', 'ldm_b.jpg', 'ldm_c.jpg'],
        ['Панорама квартала с реки', 'Корпуса вдоль набережной', 'Терраса у воды']),
      spacer(32),
      imageTrio(['ldm_d.jpg', 'ldm_e.jpg', 'ldm_f.jpg'],
        ['Приватный двор', 'Дворовые зоны отдыха', 'Двор сверху']),
      spacer(32),
      imageTrio(['ldm_g.jpg', 'ldm_h.jpg', 'ldm_i.jpg'],
        ['Гостиная с каминной зоной', 'Вид из окна на Малую Невку', 'Подземный паркинг']),
      spacer(28),
      note('Визуализации — с сайта застройщика. Дом не построен: итоговый облик может отличаться.'),

      h1('ЛДМ — характеристики и особенности', { br: true }),
      factSheet(K.cardLdm),
      spacer(40),
      ...bullets(K.featLdm.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(30),
      image('map_ldm.jpg', PX, 340, { after: 30 }),
      caption('Красное — дом, тёмное — станции метро с расстоянием по прямой.'),
      spacer(24),
      note('До «Крестовского острова» 1,37 км, до «Чкаловской» 1,39 км, до «Петроградской» 1,63 км. Пешей доступности метро у проекта нет — расчёт на автомобиль, и двухуровневый подземный паркинг это подтверждает.'),

      h1('ЛДМ — цены', { br: true }),
      body('Сто десять лотов в семи корпусах, от однокомнатных 45,0 м² до сити-виллы 428,5 м². Разброс цены метра почти трёхкратный: от 775 до 2 223 тыс. ₽, средневзвешенный — 1 208 тыс. ₽.', { after: 170 }),
      dataTable(
        ['Площадь,\nм²', 'Комнат', 'Цена, ₽', 'Цена за м², ₽', 'Корп.', 'Этаж', 'Лот'],
        K.priceLdm, [1150, 1250, 1950, 1750, 900, 1250, 1388],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Ссылка в последней колонке открывает карточку лота на сайте застройщика. Цены на 26.08.2026, публичной офертой не являются.'),
      spacer(36),
      ...bullets([
        [txt('Метр различается между корпусами в полтора раза: 1 553 тыс. ₽ в седьмом против 974 тыс. в четвёртом. '), txt('Седьмой корпус — восьмиэтажный, и все десять его лотов от 91,7 м²; четвёртый — девятиэтажный, с самыми скромными по цене метра предложениями. Разница в 60 % внутри одного проекта означает, что корпуса продаются как разные продукты.', { bold: true })],
        [txt('Пятьдесят три лота из ста десяти — двухкомнатные. '), txt('Ещё 24 трёхкомнатных, 16 однокомнатных, 13 четырёхкомнатных и 4 пятикомнатных. Ядро предложения — квартиры 60–90 м², то есть проект метит в семейного покупателя, а не в инвестора под аренду.', { bold: true })],
        [txt('Одна сити-вилла в открытой продаже: 428,5 м² за 436,1 млн ₽. '), txt('Это самый дорогой лот проекта и одновременно один из самых дешёвых по метру — 1 018 тыс. ₽. За объём здесь дают заметную скидку: средний метр по проекту на 19 % выше.', { bold: true })],
        [txt('Сумма всех ста десяти лотов — 13,62 млрд ₽ за 11 275 м². '), txt('Это девять десятых всего открытого предложения AURIX в деньгах. Петербургский портфель бренда сейчас крупнее московского и по числу лотов, и по выручке.', { bold: true })],
      ]),

      h1('ЛДМ — планировки', { br: true }),
      body('Сто десять лотов в порядке возрастания площади. Планировки — с карточек лотов, вариант с расстановкой мебели.', { after: 170 }),
      flatCards(K.cardsLdm, 4),

      // ═══════════════ МАРИИНКА ДЕЛЮКС ═══════════════
      spacer(60),
      h1('Мариинка Делюкс'),
      body('Премиум-класс на Матисовом острове, в пятнадцати минутах пешком от Мариинского театра. Один дом на 94 квартиры, готовность — 2027 год.', { after: 150 }),
      image('mar_hero.jpg', PX, 245, { after: 30 }),
      caption('Фасады в стиле XIX века: карнизы, рельефы, панорамное остекление.'),
      spacer(32),
      imageTrio(['mar_a.jpg', 'mar_b.jpg', 'mar_c.jpg'],
        ['Дом со стороны набережной', 'Угловой ракурс снизу', 'Деталь фасада']),
      spacer(32),
      imageTrio(['mar_d.jpg', 'mar_e.jpg', 'mar_f.jpg'],
        ['Карнизы и рельефы вблизи', 'Первый этаж с витринами', 'Входная группа']),
      spacer(28),
      note('Визуализации — с сайта застройщика. Дом не построен: итоговый облик может отличаться.'),

      h1('Мариинка Делюкс — характеристики и особенности', { br: true }),
      factSheet(K.cardMar),
      spacer(40),
      ...bullets(K.featMar.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(30),
      image('map_mar.jpg', PX, 340, { after: 30 }),
      caption('Красное — дом, тёмное — станция метро с расстоянием по прямой.'),
      spacer(24),
      note('До «Спасской» 2,11 км, до «Балтийской» 2,19 км. Это самая удалённая от метро площадка во всём портфеле AURIX; зато до Мариинского театра пятнадцать минут пешком, а до «Новой Голландии» десять минут на машине.'),

      h1('Мариинка Делюкс — цены', { br: true }),
      body('Девятнадцать лотов с первого по восьмой этаж, 47,8–122,1 м². Метр 956–1 191 тыс. ₽, средневзвешенный — 1 057 тыс. ₽.', { after: 170 }),
      dataTable(
        ['Площадь,\nм²', 'Комнат', 'Цена, ₽', 'Цена за м², ₽', 'Корп.', 'Этаж', 'Лот'],
        K.priceMar, [1150, 1250, 1950, 1750, 900, 1250, 1388],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Ссылка в последней колонке открывает карточку лота на сайте застройщика. Цены на 26.08.2026, публичной офертой не являются.'),
      spacer(36),
      ...bullets([
        [txt('Девятнадцать лотов из 94 квартир — пятая часть дома. '), txt('Комнатность распределена ровно: шесть однокомнатных, шесть двухкомнатных и семь трёхкомнатных. Пентхаусы с террасами и квартиры девятого этажа, о которых пишет застройщик, в открытую продажу не выведены — верхний этаж в подборе восьмой.', { bold: true })],
        [txt('Разброс цены метра всего 25 % — от 956 до 1 191 тыс. ₽. '), txt('Для дома, который сдаётся через год с небольшим, это узкий коридор: прайс явно сформирован единой сеткой, без сильных надбавок за этаж или вид.', { bold: true })],
        [txt('Сумма девятнадцати лотов — 1,59 млрд ₽ за 1 504 м². '), txt('Средний чек 83,6 млн ₽ — вдвое ниже, чем в «ЛДМ», и это следствие формата: здесь нет ни сити-вилл, ни лотов крупнее 122 м².', { bold: true })],
      ]),
      spacer(40),
      flatCards(K.cardsMar, 4),

      // ═══════════════ СРАВНЕНИЕ ═══════════════
      h1('Два проекта рядом', { br: true }),
      dataTable(
        ['Параметр', 'ЛДМ', 'Мариинка Делюкс'],
        K.compare, [3100, 3269, 3269],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(36),
      ...bullets([
        [txt('Метр в «ЛДМ» дороже на 14 %: 1 208 тыс. ₽ против 1 057 тыс. '), txt('Оба дома сдаются в 2027 году, поэтому срок в разрыв не играет — разница в локации и в продукте. Аптекарский остров с видами на Малую Невку против Матисова острова, где вид на Пряжку и промышленное окружение западной Коломны.', { bold: true })],
        [txt('«ЛДМ» больше «Мариинки» почти в шесть раз по числу лотов и в восемь с половиной раз по деньгам. '), txt('110 лотов на 13,62 млрд ₽ против 19 лотов на 1,59 млрд. Это разные стадии продаж: «ЛДМ» только раскрывает объём, «Мариинка» показывает узкую витрину.', { bold: true })],
        [txt('Форматы пересекаются лишь частично. '), txt('В «Мариинке» всё умещается в 47,8–122,1 м², в «ЛДМ» диапазон от 45,0 до 428,5 м² и есть сити-виллы. Покупатель до 120 м² выбирает между двумя проектами, покупатель крупного формата — только «ЛДМ».', { bold: true })],
        [txt('Оба дома далеко от метро: 1,37 км и 2,11 км. '), txt('Для петербургского премиума это обычная история — оба острова застраивались как тихие анклавы, и пешая доступность подземки там отсутствует по географии, а не по недосмотру. Но обоим проектам это добавляет зависимость от автомобиля.', { bold: true })],
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
  const out = path.join(__dirname, 'AURIX_ЛДМ_и_Мариинка_выгрузка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
