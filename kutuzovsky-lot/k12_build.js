const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'k12_tables.json'), 'utf8'));
const IMG = (n) => fs.readFileSync(path.join(__dirname, 'k12_out', n));

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
  spacing: { after: 160 }, pageBreakBefore: !!o.br,
  border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BRONZE, space: 8 } },
});

const h2 = (text, o = {}) => p({
  children: [txt(text, { font: S.GEO, size: 24, bold: true, color: INK })],
  spacing: { before: o.br ? 0 : 170, after: 110 }, pageBreakBefore: !!o.br,
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
        align: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER,
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
      children: items.map(([val, lab], i) => new TableCell({
        width: { size: W[i], type: WidthType.DXA },
        shading: { type: ShadingType.CLEAR, fill: SOFT, color: 'auto' },
        margins: { top: 118, bottom: 118, left: 170, right: 110 },
        verticalAlign: VerticalAlign.CENTER,
        borders: {
          top: { style: BorderStyle.SINGLE, size: 18, color: BRONZE },
          bottom: noBorder,
          left: { style: BorderStyle.SINGLE, size: 26, color: 'FCFCFB' },
          right: { style: BorderStyle.SINGLE, size: 26, color: 'FCFCFB' },
        },
        children: [
          p({ children: [txt(val, { font: S.GEO, size: 27, bold: true, color: INK })],
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
    margins: { top: 0, bottom: 220, left: left ? 0 : 150, right: left ? 150 : 0 },
    verticalAlign: VerticalAlign.TOP,
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    children: c ? [
      p({ children: [new ImageRun({ data: IMG(c.img), type: 'jpg',
                                    transformation: { width: pxw, height: pxh } })],
          spacing: { after: 90 } }),
      p({ children: [txt(`ЖК «${c.zhk}»`, { size: 19, bold: true, color: INK })],
          spacing: { after: 50, line: 230, lineRule: LR } }),
      p({ children: [txt(`${c.lot} · ${c.ren}`, { size: 16, color: MUTED })],
          spacing: { after: 50, line: 230, lineRule: LR } }),
      p({ children: [txt(c.price, { size: 19, bold: true, color: BRONZE }),
                     txt(`   ${c.ppm}`, { size: 16, color: MUTED })],
          spacing: { after: 50, line: 230, lineRule: LR } }),
      p({ children: [new ExternalHyperlink({
            children: [txt('Объявление →', { size: 15, color: '2C5FA8' })], link: c.url })],
          spacing: { after: 0, line: 220, lineRule: LR } }),
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
  ['162,5 млн ₽', 'Цена лота'],
  ['95,0 м²', 'Площадь'],
  ['1 710 526 ₽', 'Цена за м²'],
  ['6 / 11', 'Этаж'],
];
const FACTS = [
  ['ЖК', '«Кутузовский XII»'],
  ['Адрес', 'Кутузовский пр-т, 12'],
  ['Комнат', '3'],
  ['Метро', '«Киевская»'],
  ['Отделка', 'Дизайнерский ремонт'],
  ['Дом сдан', '2020 г.'],
  ['Тип продавца', 'Агентство'],
  ['Рынок', 'Вторичный'],
];
const SRC = [
  ['Циан — выгрузка по ЖК «Кутузовский XII» (ID 44404), 14 лотов, 30.07.2026', ''],
  ['Циан — выгрузки по локации на 30.07.2026: «Дом Дау» (ID 4296442) 286 лотов, «Бадаевский» (ID 1900321) 144, Capital Towers (ID 45865) 129, апартаменты Москва-Сити 133', ''],
  ['Объявление по нашему лоту', K.our.url],
  ['Яндекс Карты — картографическая основа', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Аналитика по лоту',
  title: 'ЖК «Кутузовский XII», 3-комн. 95 м² — аналитика по лоту',
  description: 'Ценовая аналитика по квартире 95 м² с премиум-отделкой в ЖК «Кутузовский XII»',
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
        txt('ЖК «Кутузовский XII» · 3-комн. 95 м² · аналитика по лоту от 30.07.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Аналитика по лоту · Москва · 30 июля 2026'),
      p({ children: [txt('Кутузовский XII', { font: S.GEO, size: 48, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('3-комнатная квартира 95 м² с дизайнерским ремонтом', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Москва, ЦАО, Дорогомилово, Кутузовский проспект, 12  ·  м. «Киевская»', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('Параметры лота', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(FACTS),
      spacer(80),
      kicker('Расположение и ближайшие конкуренты', INK),
      image('map.jpg', PX, 402, { after: 50 }),
      note('«Бадаевский» строится на соседнем участке — тот же адрес, Кутузовский проспект, 12. Основа: Яндекс Карты.'),

      // ─────────────── INSIDE THE BUILDING ───────────────
      h1('Позиция лота внутри своего дома', { br: true }),
      body('В экспозиции Циан по ЖК «Кутузовский XII» на 30.07.2026 находится 14 лотов. Наш лот выделен в таблице.', { after: 180 }),
      dataTable(
        ['Тип', 'Площадь, м²', 'Этаж', 'Цена, млн ₽', 'Цена за м², ₽', 'Отделка'],
        K.own, [1780, 1500, 1100, 1600, 1700, 1958],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Средняя цена по дому (средневзвешенная по площади) — 1 367 592 ₽/м². Лоты с дизайнерским ремонтом идут по 1 547 366 ₽/м², без отделки — по 1 300 009 ₽/м²: премия за ремонт внутри дома составляет +19 %.'),

      h2('Что показывает эта таблица'),
      ...bullets([
        [txt('Наш лот — '), txt('второй по цене за метр из четырнадцати', { bold: true }), txt(' и самый дорогой среди всех трёхкомнатных. Дороже только лот 200 м² на том же шестом этаже — 1 800 000 ₽/м².')],
        [txt('Цена лота выше средней по дому на '), txt('+25 %', { bold: true }), txt(' и выше средней по лотам с дизайнерским ремонтом на +11 %. То есть лот стоит в верхней части собственного дома, а не в середине.')],
        [txt('Главный конкурент находится в том же доме. '), txt('Квартира той же площади — 95,0 м² — с таким же дизайнерским ремонтом продаётся на втором этаже за 133,0 млн ₽ (1 400 000 ₽/м²). Разница — 29,5 млн ₽, или −18 %.', { bold: true })],
        'Ещё один близкий аналог — 93,1 м² на втором этаже за 158,0 млн ₽ (1 697 100 ₽/м²). По удельной цене он практически равен нашему лоту, но дешевле по бюджету на 4,5 млн ₽.',
      ]),

      // ─────────────── LOCATION ───────────────
      h1('Сравнение с локацией', { br: true }),
      body('Сопоставимая база — квартиры 85–115 м² в проектах вокруг Кутузовского проспекта и Москва-Сити. Где отделки нет, к цене добавлена отделка по 250 тыс. ₽/м² — ориентир для этого сегмента.', { after: 180 }),
      dataTable(
        ['Проект', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽', 'С отделкой', 'Метр готовой\nквартиры, ₽'],
        K.loc, [2060, 780, 1360, 1560, 1400, 1000, 1478],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('* Апартаменты Москва-Сити (башни «Город Столиц», «ОКО», «Империя», «Федерация», Neva Towers) приведены справочно и в сравнение не входят: это апартаменты, а не квартиры — другой юридический статус, другой набор прав и другая цена. Из 133 лотов выгрузки 122 описаны продавцами как апартаменты.'),

      h2('Метр квартиры, готовой к заселению'),
      image('chart.jpg', PX, 257, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 30.07.2026, квартиры 85–115 м².'),
      spacer(60),
      ...bullets([
        [txt('Наш лот — '), txt('второй по цене метра', { bold: true }), txt(' среди пяти сопоставимых оснований. Дороже только «Бадаевский» в приведённых ценах: 1 857 089 ₽/м² против 1 710 526 ₽/м², разница 8 %.')],
        'Относительно сопоставимых лотов в собственном доме лот дороже на 7 %, относительно Capital Towers — на 28 %, относительно «Дома Дау» с добавленной отделкой — на 35 %.',
        '«Бадаевский» — единственный, кто дороже, но это первичка без отделки со сдачей в будущем. Наш лот — готовая квартира в доме 2020 года, куда можно заехать сразу.',
      ]),

      // ─────────────── SAME MONEY ───────────────
      h1('Что можно купить за те же деньги', { br: true }),
      body('Практическая проверка цены: что предлагает локация за бюджет около 162,5 млн ₽ прямо сейчас.', { after: 180 }),
      dataTable(
        ['Проект', 'Квартира', 'Отделка', 'Цена,\nмлн ₽', 'Цена за\nм², ₽', 'Ссылка'],
        K.alt, [1980, 1900, 1500, 1080, 1250, 1928],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Реальные лоты из выгрузок Циан на 30.07.2026. Первая строка — наш лот.'),

      h2('Выводы'),
      ...bullets([
        [txt('Цена лота обоснована этажом и ремонтом, но находится на верхней границе дома. '), txt('1 710 526 ₽/м² — это +25 % к средней по «Кутузовскому XII» и +11 % к лотам с таким же дизайнерским ремонтом.')],
        [txt('Самый сильный аргумент против — соседняя квартира. '), txt('95 м² с таким же ремонтом на втором этаже стоит 133,0 млн ₽. Покупатель платит 29,5 млн ₽ за четыре этажа разницы; в доме высотой 11 этажей это трудно обосновать видом.', { bold: true })],
        [txt('За те же деньги Capital Towers даёт больше метража. '), txt('130 м² с дизайнерским ремонтом — 156,0 млн ₽: на 35 м² больше и на 6,5 млн ₽ дешевле, в доме 2023 года.')],
        '«Дом Дау» за 162,2 млн ₽ предлагает 158,7 м², но без отделки: с ремонтом по 250 тыс. ₽/м² лот выйдет примерно в 202 млн ₽. Прямой альтернативой по бюджету он не является.',
        'Сравнение с апартаментами Москва-Сити (720 829 ₽/м²) некорректно и в расчёт не бралось: у апартаментов другой юридический статус, они дешевле квартир структурно, а не по качеству.',
        [txt('Вывод по цене. '), txt('Лот оценён агрессивно даже внутри собственного дома. Для быстрой сделки ориентиром выглядит уровень 1,40–1,60 млн ₽/м², то есть 133–152 млн ₽ за эту квартиру, — именно в этом коридоре стоят сопоставимые лоты в том же доме и в локации.')],
      ]),

      spacer(160),
      rule(140),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 70, line: 230, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      spacer(100),
      note('Аналитика подготовлена 30.07.2026 на основе выгрузок Циан. Оценки носят справочный характер и не являются офертой или отчётом об оценке.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'Кутузовский_XII_аналитика_по_лоту.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
