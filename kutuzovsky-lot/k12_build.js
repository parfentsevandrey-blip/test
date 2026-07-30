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

// bronze-ruled call-out box for the headline recommendation
function callout(title, lines) {
  return new Table({
    columnWidths: [CONTENT_W],
    width: { size: CONTENT_W, type: WidthType.DXA },
    rows: [new TableRow({ children: [new TableCell({
      width: { size: CONTENT_W, type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, fill: SOFT, color: 'auto' },
      margins: { top: 170, bottom: 170, left: 240, right: 240 },
      borders: {
        top: noBorder, bottom: noBorder, right: noBorder,
        left: { style: BorderStyle.SINGLE, size: 24, color: BRONZE },
      },
      children: [
        p({ children: [txt(title, { font: S.GEO, size: 23, bold: true, color: INK })],
            spacing: { after: 90, line: 240, lineRule: LR } }),
        ...lines.map((t, i) => p({
          children: Array.isArray(t) ? t : [txt(t)],
          spacing: { after: i === lines.length - 1 ? 0 : 80, line: 252, lineRule: LR },
        })),
      ],
    })] })],
  });
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
  ['Циан — выгрузка по ЖК «Кутузовский XII» (ID 44404), 14 объявлений = 12 лотов, 30.07.2026', ''],
  ['Циан — выгрузки по локации на 30.07.2026, лотов после схлопывания дублей: «Дом Дау» 286, «Бадаевский» 144, Capital Towers 124, «Веспер Кутузовский» 88, Москва-Сити 131', ''],
  ['Объявление по нашему лоту', K.our.url],
  ['Фото ремонта и цены лотов Capital Towers, Neva Towers — Яндекс Недвижимость', 'https://realty.yandex.ru/'],
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
      image('map.jpg', PX, 378, { after: 50 }),
      note('«Бадаевский» и «Веспер Кутузовский» строятся на соседних участках — тот же адрес, Кутузовский проспект, 12. Основа: Яндекс Карты.'),

      // ─────────────── PROJECTS AROUND ───────────────
      h1('Конкурентные проекты локации', { br: true }),
      body('Пять проектов, между которыми выбирает покупатель с бюджетом «Кутузовского XII»: три стройки на соседних участках и две готовые башни у Москва-Сити.', { after: 180 }),
      dataTable(
        ['Проект', 'Класс', 'Срок сдачи', 'Отделка\nв экспозиции', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽'],
        K.proj, [1350, 950, 1500, 1650, 800, 1100, 1200, 1088],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Застройщики: «Бадаевский» и Capital Towers — Capital Group, «Веспер Кутузовский» — Vesper, «Дом Дау» — «Сумма Элементов». «Кутузовский XII» продаётся только на вторичном рынке. Средняя цена — средневзвешенная по площади, по всей экспозиции проекта на 30.07.2026.'),

      h2('Что из этого следует'),
      ...bullets([
        [txt('Наш дом — единственный готовый и заселённый среди соседей. '), txt('«Бадаевский» сдаётся в 2026–2027 годах, «Веспер Кутузовский» — в 2028-м. Покупатель, который хочет въехать в этом году, на Кутузовском проспекте выбирает практически только между вторичкой в нашем доме и Capital Towers.')],
        [txt('При этом «Кутузовский XII» — бизнес-класс, а соседи заявлены как элит и премиум. '), txt('Это ограничивает верхнюю планку цены: платить за метр в доме бизнес-класса столько же, сколько за элитный метр рядом, покупатель не готов.')],
        'Вся первичка вокруг продаётся без отделки: 144 лота «Бадаевского», 88 «Веспера», 284 из 286 в «Доме Дау». Готовый ремонт в локации — это либо вторичка, либо треть экспозиции Capital Towers.',
        [txt('Объём предложения работает против продавца. '), txt('В соседних проектах одновременно экспонируется 642 лота против 12 в нашем доме: покупателю есть из чего выбирать, и он сравнивает.')],
      ]),

      h2('Сколько метров даёт бюджет 162,5 млн ₽'),
      body('Бюджет нашего лота, пересчитанный по средней цене метра каждого проекта.', { after: 140 }),
      statTiles(K.power),
      spacer(40),
      note('Расчёт по средневзвешенной цене всей экспозиции проекта на 30.07.2026. У «Бадаевского», «Веспера» и «Дома Дау» это цена без отделки — на ремонт нужно добавить ещё примерно 250 тыс. ₽/м². С учётом отделки бюджет даёт там 74, 82 и 125 м² соответственно.'),

      // ─────────────── INSIDE THE BUILDING ───────────────
      h1('Позиция лота внутри своего дома', { br: true }),
      body('В экспозиции Циан по ЖК «Кутузовский XII» на 30.07.2026 — 12 квартир. Наш лот выделен в таблице; ссылка ведёт на объявление.', { after: 180 }),
      dataTable(
        ['Тип', 'Площадь, м²', 'Этаж', 'Цена,\nмлн ₽', 'Цена за м², ₽', 'Отделка', 'Ссылка'],
        K.own, [1500, 1330, 900, 1330, 1450, 1670, 1458],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Выгрузка Циан содержит 14 объявлений, но три из них — это одна и та же квартира 158,3 м², выставленная разными агентствами; дубли схлопнуты. Средняя цена по дому (средневзвешенная по площади) — 1 397 656 ₽/м². Лоты с дизайнерским ремонтом идут по 1 547 366 ₽/м², без отделки — по 1 300 009 ₽/м²: премия за ремонт внутри дома составляет +19 %.'),

      h2('Что показывает эта таблица'),
      ...bullets([
        [txt('Наш лот — '), txt('второй по цене за метр из двенадцати', { bold: true }), txt(' и самый дорогой среди всех трёхкомнатных. Дороже только лот 200 м² на том же шестом этаже — 1 800 000 ₽/м².')],
        [txt('Цена лота выше средней по дому на '), txt('+22 %', { bold: true }), txt(' и выше средней по лотам с дизайнерским ремонтом на +11 %. То есть лот стоит в верхней части собственного дома, а не в середине.')],
        [txt('Главный конкурент находится в том же доме. '), txt('Квартира той же площади — 95,0 м² — с таким же дизайнерским ремонтом продаётся на втором этаже за 133,0 млн ₽ (1 400 000 ₽/м²). Разница — 29,5 млн ₽, или −18 %.', { bold: true })],
        'Ещё один близкий аналог — 93,1 м² на втором этаже за 158,0 млн ₽ (1 697 100 ₽/м²). По удельной цене он практически равен нашему лоту, но дешевле по бюджету на 4,5 млн ₽.',
      ]),

      // ─────────────── LOCATION ───────────────
      h1('Сравнение с локацией', { br: true }),
      body('Сопоставимая база — квартиры 85–115 м² в проектах вокруг Кутузовского проспекта и Москва-Сити. Где отделки нет, к цене добавлена отделка по 250 тыс. ₽/м² — ориентир для этого сегмента.', { after: 180 }),
      dataTable(
        ['Проект', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽', 'Что взято\nв расчёт', 'Метр готовой\nквартиры, ₽'],
        K.loc, [2060, 700, 1200, 1360, 1400, 1440, 1478],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('* Апартаменты Москва-Сити (башни «Город Столиц», «ОКО», «Империя», «Федерация», Neva Towers) приведены справочно и в сравнение не входят: это апартаменты, а не квартиры — другой юридический статус, другой набор прав и другая цена. Из 131 лота выгрузки 120 описаны продавцами как апартаменты.'),

      h2('Метр квартиры, готовой к заселению'),
      image('chart.jpg', PX, 294, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 30.07.2026, квартиры 85–115 м².'),
      spacer(40),
      ...bullets([
        [txt('Наш лот — '), txt('третий по цене метра из шести оснований', { bold: true }), txt('. Дороже только две элитные стройки на соседних участках: «Бадаевский» — 1 857 089 ₽/м² (+8 %) и «Веспер Кутузовский» — 1 788 394 ₽/м² (+5 %). Обе сдаются в 2026–2028 годах.')],
        'Относительно сопоставимых лотов в собственном доме лот дороже на 7 %, относительно Capital Towers — на 27 %, относительно «Дома Дау» с добавленной отделкой — на 35 %.',
        [txt('Дороже нашего лота — только то, во что нельзя заехать. '), txt('«Бадаевский» и «Веспер» — первичка без отделки с ключами через 1,5–2,5 года. Среди готового жилья локации наш лот — самый дорогой метр.')],
      ]),

      // ─────────────── RENOVATION PHOTOS ───────────────
      h1('Какой ремонт продаётся в локации за те же деньги', { br: true }),
      body('Готовые лоты с ремонтом в Capital Towers и Москва-Сити — устоявшаяся вторичка тех же лет, что и наш дом. Первая карточка — наш лот.', { after: 170 }),
      photoCards(K.photos, 306, 204),
      note('* Neva Towers — апартаменты: другой юридический статус, цена не сопоставима напрямую и в расчёт средних не входит. Фотографии — Яндекс Недвижимость, 30.07.2026; цена нашего лота — Циан.'),

      // ─────────────── SAME MONEY ───────────────
      h1('Что можно купить за те же деньги', { br: true }),
      body('Практическая проверка цены: одиннадцать реальных лотов в бюджете 131–170 млн ₽ из проектов, которые участвуют в этой аналитике. Первая строка — наш лот.', { after: 180 }),
      dataTable(
        ['Проект', 'Квартира', 'Отделка', 'Цена,\nмлн ₽', 'Цена за\nм², ₽', 'Ссылка'],
        K.alt, [2080, 1820, 1420, 1180, 1250, 1888],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('* Neva Towers — апартаменты, приведены справочно. Циан и Яндекс Недвижимость, 30.07.2026; ссылка ведёт на объявление. Шесть лотов с фотографиями ремонта показаны на предыдущей странице.'),

      h2('Что показывает эта таблица'),
      ...bullets([
        [txt('Наш лот — самый дорогой метр в списке. '), txt('Ближайший по бюджету аналог — Capital Towers, 102,7 м² за 162,2 млн ₽: та же сумма, тот же готовый дизайнерский ремонт с мебелью, метр на 8 % дешевле, дом 2023 года и вид на Москва-Сити.', { bold: true })],
        'Девять из одиннадцати лотов продаются с готовым ремонтом, и семь из них дешевле нашего по бюджету. Покупателю не нужно ждать стройку и делать ремонт, чтобы найти альтернативу.',
        'Два лота без отделки — «Бадаевский» и «Дом Дау» — по бюджету близки, но требуют ещё 30–40 млн ₽ на ремонт и 1,5–2,5 года ожидания. Как альтернативу «заехать сейчас» их рассматривать нельзя.',
      ]),

      // ─────────────── CONCLUSIONS ───────────────
      h1('Выводы', { br: true }),

      h2('Цена лота внутри своего дома'),
      ...bullets([
        [txt('1 710 526 ₽/м² — это +22 % к средней по «Кутузовскому XII» (1 397 656 ₽/м²) и +11 % к лотам дома с таким же дизайнерским ремонтом (1 547 366 ₽/м²). '), txt('Второй по цене метра из двенадцати лотов и самый дорогой среди всех трёхкомнатных.', { bold: true })],
        'Ближайший ориентир в доме — 93,1 м² за 158,0 млн ₽ (1 697 100 ₽/м²): практически та же удельная цена и такой же дизайнерский ремонт, но на 4,5 млн ₽ дешевле по бюджету. То есть уровень цены в доме продавцы держат — вопрос в том, кто уступит первым.',
        [txt('Главный ограничитель в переговорах — квартира той же площади этажом ниже. '), txt('95,0 м² с таким же дизайнерским ремонтом на втором этаже — 133,0 млн ₽ (1 400 000 ₽/м²). Разница 29,5 млн ₽, или −18 %, за четыре этажа в одиннадцатиэтажном доме.', { bold: true })],
      ]),

      h2('Что даёт локация за те же деньги'),
      ...bullets([
        [txt('Capital Towers, 102,7 м² за 162,2 млн ₽ (1 579 200 ₽/м²) — ровно наш бюджет. '), txt('Дизайнерский ремонт с мебелью, 54-й этаж, панорама Москва-Сити, дом 2023 года: площадь на 7,7 м² больше, метр на 8 % дешевле. Это самый прямой конкурент нашего лота в локации.', { bold: true })],
        'Capital Towers, 130,0 м² с дизайнерским ремонтом — 156,0 млн ₽ (1 200 000 ₽/м²): плюс 35 м² и на 6,5 млн ₽ дешевле.',
        'Capital Towers, 109,3 м² с готовой отделкой — 131,2 млн ₽: на 31,3 млн ₽ дешевле нашего лота. В Capital Towers «готовая отделка» чаще всего означает законченный ремонт без мебели — это дешевле варианта «под ключ», но заехать можно сразу.',
        'Neva Towers, 111–124 м² с дизайнерским ремонтом — 149–160 млн ₽ (1,29–1,34 млн ₽/м²). Это апартаменты: в расчёт средних они не входят, но бюджет покупателя забирают наравне с квартирами.',
        '«Бадаевский» за 168,7 млн ₽ — 119,3 м² без отделки со сдачей в 2026–2027 годах: ещё около 30 млн ₽ на ремонт и полтора года ожидания.',
        '«Дом Дау» за 162,2 млн ₽ даёт 158,7 м², но без отделки: с ремонтом по 250 тыс. ₽/м² лот выйдет примерно в 202 млн ₽ — прямой альтернативой по бюджету он не является.',
      ]),

      spacer(60),
      callout('Рекомендация по цене', [
        [txt('Лот оценён агрессивно. Он стоит в верхней части собственного дома и дороже любого готового к заселению метра в локации: выше него только «Бадаевский» и «Веспер Кутузовский» — элитные стройки с ключами через 1,5–2,5 года.')],
        [txt('Ориентир для сделки в разумный срок — '), txt('1,40–1,60 млн ₽/м², то есть 133–152 млн ₽ за эту квартиру.', { bold: true }), txt(' Именно в этом коридоре стоят сопоставимые лоты в том же доме и готовые квартиры с ремонтом в локации.')],
        [txt('Цена 162,5 млн ₽ реалистична только для покупателя, которому нужен именно этот дом, этаж и готовый ремонт и который не рассматривает Capital Towers. Такой покупатель существует, но ждать его придётся долго.')],
      ]),

      spacer(60),
      rule(140),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 70, line: 230, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      spacer(60),
      note('Аналитика подготовлена 30.07.2026 на основе выгрузок Циан. Оценки носят справочный характер и не являются офертой или отчётом об оценке.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'Кутузовский_XII_аналитика_по_лоту.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
