const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'lv_tables.json'), 'utf8'));
const IMG = (n) => fs.readFileSync(path.join(__dirname, 'out', n));

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
  ['57,0 млн ₽', 'Цена лота'],
  ['79,5 м²', 'Площадь'],
  ['716 981 ₽', 'Цена за м²'],
  ['15 / 19', 'Этаж'],
];
const FACTS = [
  ['ЖК', '«Левел Академическая»'],
  ['Адрес', 'Профсоюзная ул., 2/22'],
  ['Комнат', '3'],
  ['Метро', '«Академическая», 1 мин'],
  ['Отделка', 'Без отделки (бетон)'],
  ['Дом сдан', '2025–2026 гг.'],
  ['Класс', 'Бизнес'],
  ['Застройщик', 'Level Group'],
];
const SRC = [
  ['Циан — выгрузка по ЖК «Левел Академическая» (ID 4117148), 30 лотов, 30.07.2026', ''],
  ['Циан — выгрузки по локации на 30.07.2026: «Файв Тауэрс» 114 лотов, Lunar 36, «Новочеремушкинская 17» 6, «Вавилова 52» 6, «Новые Черемушки» 6, VAVILOVE 4, «Вавилов ДОМ» 2', ''],
  ['Объявление по нашему лоту', 'https://www.cian.ru/sale/flat/331215568/'],
  ['Фото ремонта и цены готовых квартир — Яндекс Недвижимость', 'https://realty.yandex.ru/'],
  ['Инфраструктура — сайты застройщиков Level Group, Hutton Development, СЗ «5 Донской», КП УГС', ''],
  ['Яндекс Карты — картографическая основа', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Аналитика по лоту',
  title: 'ЖК «Левел Академическая», 3-комн. 79,5 м² — аналитика по лоту',
  description: 'Ценовая аналитика по квартире 79,5 м² без отделки в ЖК «Левел Академическая»',
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
        txt('ЖК «Левел Академическая» · 3-комн. 79,5 м² · аналитика по лоту от 30.07.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Аналитика по лоту · Москва · 30 июля 2026'),
      p({ children: [txt('Левел Академическая', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('3-комнатная квартира 79,5 м² без отделки', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Москва, ЮЗАО, Академический, Профсоюзная улица, 2/22  ·  м. «Академическая», 1 минута', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('Параметры лота', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(FACTS),
      spacer(80),
      kicker('Расположение и ближайшие конкуренты', INK),
      image('map.jpg', PX, 439, { after: 50 }),
      note('Подписи — средневзвешенная цена метра по всей экспозиции проекта. Координаты сверены по геокодеру OSM. Основа: Яндекс Карты.'),

      // ─────────────── PROJECTS AROUND ───────────────
      h1('Конкурентные проекты локации', { br: true }),
      body('Восемь проектов, между которыми выбирает покупатель с бюджетом «Левел Академической»: две новостройки премиум-класса и пять домов бизнес-класса 2019–2020 годов, которые давно заселены.', { after: 180 }),
      dataTable(
        ['Проект', 'Класс', 'Срок сдачи', 'Отделка\nв экспозиции', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽'],
        K.proj, [1750, 900, 1400, 1550, 700, 1050, 1150, 1138],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Застройщики: «Левел Академическая» — Level Group, Lunar — Hutton Development, «Файв Тауэрс» — СЗ «5 Донской», «Вавилова 52» — КП УГС, VAVILOVE — Ingrad (сейчас Sminex). Остальные продаются только на вторичном рынке. Средняя цена — средневзвешенная по площади, по всей экспозиции проекта на 30.07.2026.'),

      h2('Что из этого следует'),
      ...bullets([
        [txt('Наш дом — единственный новый у самого метро. '), txt('Медиана пешей доступности по выгрузке — 1 минута до «Академической». У «Новых Черемушек» 4 минуты, у Новочерёмушкинской, 17 — 3, у Lunar 14, у «Файв Тауэрс» 9. Это главный и, по сути, единственный физический актив лота.', { bold: true })],
        'Но покупатель выбирает не только среди новостроек. В радиусе двух километров пять домов бизнес-класса 2019–2020 годов, где квартиры давно с ремонтом и стоят 564–652 тыс. ₽ за метр — против 769 тыс. ₽ в нашем доме.',
        [txt('Прямой ценовой конкурент — «Файв Тауэрс». '), txt('Премиум-класс, 114 лотов в продаже, white box (то есть почти готово), средняя цена метра 638 305 ₽ — на 17 % ниже нашего дома. Минус один: ключи в I квартале 2027 года.')],
        'Дороже нас только Lunar (1 084 480 ₽/м², премиум, сдан в 2024-м) и Новочерёмушкинская, 17 (854 374 ₽/м²) — и оба продают готовые квартиры, а не бетон.',
      ]),

      h2('Сколько метров даёт бюджет 68,9 млн ₽'),
      body('Полный бюджет нашего лота — цена плюс отделка — пересчитанный по средней цене метра каждого проекта.', { after: 140 }),
      statTiles(K.power.slice(0, 4)),
      spacer(40),
      statTiles(K.power.slice(4)),
      spacer(40),
      note('Расчёт по средневзвешенной цене всей экспозиции проекта на 30.07.2026. У «Файв Тауэрс» это цена white box — на доводку нужно добавить ещё около 100 тыс. ₽/м².'),

      // ─────────────── INSIDE THE BUILDING ───────────────
      h1('Позиция лота внутри своего дома', { br: true }),
      body('В экспозиции Циан по ЖК «Левел Академическая» на 30.07.2026 — 30 лотов. Наш лот выделен в таблице; ссылка ведёт на объявление.', { after: 180 }),
      dataTable(
        ['Тип', 'Площадь, м²', 'Этаж', 'Цена,\nмлн ₽', 'Цена за м², ₽', 'Отделка', 'Ссылка'],
        K.own, [1500, 1330, 900, 1330, 1450, 1670, 1458],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена по дому (средневзвешенная по площади) — 769 163 ₽/м². Лоты с одинаковой ценой и этажом, но разной площадью — это разные квартиры застройщика: часть с террасами, часть в двух уровнях.'),

      h2('Что показывает эта таблица'),
      ...bullets([
        [txt('Наш лот — 21-й по цене метра из тридцати, на 7 % '), txt('ниже', { bold: true }), txt(' средней по дому. Внутри своего дома он не переоценён — в отличие от типичной ситуации, когда лот стоит в верхней части экспозиции.')],
        'Среди трёхкомнатных 65–95 м² он четвёртый из восьми — ровно в середине. Дороже него только лоты застройщика на 17-м и 19-м этажах.',
        [txt('Прямой конкурент в доме — 70,8 м² за 50,0 млн ₽ на 13-м этаже. '), txt('На 7,0 млн ₽ дешевле по бюджету и почти с той же ценой метра.')],
      ]),

      h2('Проверка ценой этажа'),
      body('Чтобы сравнить лоты честно, цены соседей приведены к нашему пятнадцатому этажу. Надбавка за этаж измерена по прайсу застройщика внутри самого дома: 15 лотов, единый прайс-лист, +0,78 % за этаж (R² = 0,59).', { after: 180 }),
      dataTable(
        ['Площадь,\nм²', 'Этаж', 'Цена,\nмлн ₽', 'Цена за м², ₽', 'Δ этажей\nдо нашего', 'Приведено\nк 15 этажу, ₽', 'Отделка'],
        K.ownCmp, [1150, 900, 1050, 1450, 1350, 1700, 2038],
      ),
      spacer(30),
      note('Все лоты — трёхкомнатные 65–95 м² в том же доме, кроме нашего.'),
      spacer(40),
      ...bullets([
        [txt('Ближайший аналог после приведения — 717 290 ₽/м². '), txt('Наш лот просит 716 981 ₽/м². Это паритет с точностью до 0,04 %: цена внутри дома выставлена ровно там, где должна быть.', { bold: true })],
        'Значит, причина, по которой лот стоит в экспозиции, лежит не внутри дома. Её надо искать в том, с чем лот сравнивают снаружи.',
      ]),

      // ─────────────── LOCATION ───────────────
      h1('Сравнение с локацией'),
      body('Сопоставимая база — квартиры 65–95 м² в проектах Академического и Гагаринского районов. Наш лот в бетоне, поэтому к его цене добавлена отделка по 150 тыс. ₽/м² — ориентир для бизнес-класса. У соседей, наоборот, взяты лоты, которые уже продаются с ремонтом.', { after: 180 }),
      dataTable(
        ['Проект', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽', 'Что взято\nв расчёт', 'Метр готовой\nквартиры, ₽'],
        K.loc, [2160, 700, 1200, 1360, 1350, 1400, 1468],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Где в базе нет лотов с ремонтом, взяты лоты без отделки или white box и к цене добавлено 150 тыс. ₽/м². Лот на Новочерёмушкинской, 17 за 65,0 млн ₽ (1 000 000 ₽/м², «под ключ с мебелью») исключён из расчётов как выброс. Выборки по «Вавилову ДОМ», VAVILOVE и «Новочеремушкинской 17» малы — 1–2 лота, они приведены для полноты картины.'),

      h2('Метр квартиры, готовой к заселению'),
      image('chart.jpg', PX, 322, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 30.07.2026, квартиры 65–95 м².'),
      spacer(40),
      ...bullets([
        [txt('С учётом отделки наш метр стоит 866 981 ₽ — это второе место из восьми. '), txt('Дороже только Lunar (1 136 349 ₽), премиум-проект 2024 года. Новочерёмушкинская, 17 идёт вровень с нами — 869 880 ₽/м², и там в базе тоже бетон.')],
        [txt('Зато вся вторичка 2019–2020 годов дешевле на 33–81 %. '), txt('«Вавилова 52» — 635 472 ₽/м², «Новые Черемушки» — 613 271 ₽/м², «Вавилов ДОМ» — 479 452 ₽/м². Это готовые квартиры с ремонтом, а не бетон.', { bold: true })],
        '«Файв Тауэрс» с доведённым white box — 783 817 ₽/м², на 11 % дешевле нашего лота, при том что это премиум-класс. Разница в одном: ключи там в 2027 году, а у нас на руках.',
      ]),

      // ─────────────── RENOVATION PHOTOS ───────────────
      h1('Какой ремонт продаётся в локации за те же деньги'),
      body('Наш лот — бетон: за 57,0 млн ₽ покупатель получает стены и стяжку, а въехать сможет через полгода-год и ещё 11,9 млн ₽. Вот что продаётся готовым в тех же проектах, что участвуют в этой аналитике.', { after: 170 }),
      photoCards(K.photos, 306, 204),
      note('Фотографии и цены — Яндекс Недвижимость, 30.07.2026. Только проекты из присланных выгрузок. Две последние карточки Lunar — студии 31–34 м²: по площади они с нашим лотом несопоставимы и в ценовые таблицы не входят, но показывают уровень отделки в премиум-доме 2024 года. В VAVILOVE, «Вавиловом ДОМЕ» и на Новочерёмушкинской, 17 лоты с ремонтом есть, но без фотографий на Яндексе.'),

      // ─────────────── WHY IT DOES NOT SELL ───────────────
      h1('Почему квартира не продаётся'),
      body('Случай нетипичный: цена внутри дома выставлена корректно, лот не переоценён относительно соседей. Причины лежат в другом.', { after: 170 }),

      h2('Причина 1. Цена правильная — и это не помогает'),
      ...bullets([
        [txt('Соседняя трёшка 70,8 м² на 13-м этаже стоит 706 215 ₽/м². '), txt('С поправкой на два этажа по прайсу застройщика это 717 290 ₽/м². Наш лот просит 716 981 ₽/м² — паритет.', { bold: true })],
        'Торговаться внутри дома не с чем: лот уже на 7 % дешевле средней по экспозиции и четвёртый из восьми среди сопоставимых трёшек. Снижать цену «чтобы догнать своих» не нужно — он их и так не обгоняет.',
      ]),

      h2('Причина 2. Покупатель считает не 57,0, а 68,9 млн ₽'),
      statTiles([
        ['57,0 млн ₽', 'Цена лота'],
        ['+ 11,9 млн ₽', 'Отделка 79,5 м²'],
        ['68,9 млн ₽', 'Бюджет «под ключ»'],
        ['6–9 мес.', 'Ждать заселения', true],
      ]),
      spacer(50),
      ...bullets([
        'Отделка по 150 тыс. ₽/м² — это 11,9 млн ₽ сверх цены и полгода-год работ. Плюс проект, подрядчик, контроль и риск сметы, которая вырастет по ходу.',
        [txt('В сравнении с готовыми квартирами лот участвует по цене 68,9 млн ₽, а не 57,0. '), txt('По метру это 866 981 ₽ — дороже, чем у всей вторички локации, и на 11 % дороже, чем у премиального «Файв Тауэрс» с доведённым white box.')],
        'Продавец видит 57,0 млн ₽ и считает цену умеренной. Покупатель видит 68,9 млн ₽ и год ожидания и считает её высокой. Обе стороны правы, и в этом разрыве сделка и стоит.',
      ]),

      h2('Причина 3. Локация продаёт готовое дешевле'),
      body('Готовые квартиры с ремонтом в тех же проектах, что участвуют в аналитике. Отсортированы по цене; дороже нашего бюджета «под ключ» только Lunar.', { after: 170 }),
      dataTable(
        ['Что покупают вместо нашей квартиры', 'Квартира', 'Отделка', 'Цена,\nмлн ₽', 'Разница с нашим лотом', 'Ссылка'],
        K.city, [2180, 1620, 1500, 1000, 1900, 1438],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Все двенадцать лотов — из проектов, участвующих в аналитике. Разница считается к бюджету «под ключ» — 68,9 млн ₽. Циан и Яндекс Недвижимость, 30.07.2026; ссылка ведёт на объявление.'),
      spacer(20),
      ...bullets([
        [txt('81,6 м² с евроремонтом в «Новых Черемушках» за 45,0 млн ₽. '), txt('Больше нашего на 2,1 м², дешевле на 23,9 млн ₽, дом 2020 года, 4 минуты до «Академической». Это самый прямой конкурент лота.', { bold: true })],
        '95,3 м² с дизайнерским ремонтом там же за 63,0 млн ₽: на 15,8 м² больше и на 5,9 млн ₽ дешевле. Покупателю, которому нужен метраж, наш лот предложить нечего.',
        'Верхняя граница — Lunar: 77,2 м² с дизайнерским ремонтом за 76,7 млн ₽. Это на 7,8 млн ₽ дороже нашего бюджета «под ключ», но премиум-класс, дом 2024 года и готовая квартира вместо стройплощадки в собственной гостиной.',
        'Ни один из этих домов не стоит в минуте от метро и не построен в 2026 году. Но разрыв в 24–29 млн ₽ покупатель закрывает не торгом, а отказом.',
        [txt('Дешевле нас и тоже в бетоне — только два лота: '), txt('70,8 м² за 50,0 млн ₽ в нашем же доме и 95,0 м² за 65,0 млн ₽ на Новочерёмушкинской, 17, где продавец прямо пишет «не готовая квартира, а чистый холст».')],
      ]),

      h2('Причина 4. В доме восемь таких же трёшек'),
      ...bullets([
        'В экспозиции «Левел Академической» одновременно восемь трёхкомнатных квартир 65–95 м², и семь из них — тоже без отделки. Покупатель, который выбрал дом, выбирает между ними.',
        [txt('Из этих восьми три дешевле нашего лота по бюджету: '), txt('70,8 м² за 50,0 млн ₽, 70,0 м² за 47,9 млн ₽ и 88,3 м² за 59,4 млн ₽ — последняя ещё и больше нашей на 8,8 м².')],
        'Дом сдан, застройщик распродаёт остатки, и его лоты идут по прайсу без торга. Частному продавцу в такой экспозиции нечем выделиться, кроме цены.',
      ]),

      // ─────────────── INFRASTRUCTURE TIER LIST ───────────────
      h1('Инфраструктура: тир-лист проектов'),
      body('Покупатель за 57–69 млн ₽ сравнивает не только метры и цену, но и то, что он получает вместе с квартирой. Проекты сгруппированы по объёму собственной инфраструктуры для жителей — от самого богатого набора к самому скромному.', { after: 180 }),
      dataTable(
        ['Тир', 'Проекты', 'Что получает житель'],
        K.tiers, [700, 2500, 6438],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(40),

      h2('То же по пунктам'),
      dataTable(
        ['Проект', 'Фитнес', 'Коворкинг', 'Детский\nклуб', 'Лаунж', 'Консьерж', 'До метро'],
        K.infra, [2540, 1080, 1300, 1080, 1080, 1280, 1278],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Прочерк означает, что сервис не заявлен в описании проекта, — сравниваются заявленные наборы, а не результат обхода домов. Время до метро — медиана поля «До метро» из выгрузок Циан. Источники: сайты застройщиков и профильные порталы.'),

      h2('Что это значит для покупателя'),
      ...bullets([
        [txt('Наш дом продаёт местоположение, а не сервис. '), txt('Закрытый двор с детской и спортивной площадками, паркинг на 105 мест, консьерж — и всё. Ни фитнеса, ни коворкинга, ни лаунжа, ни детского клуба. Зато метро в минуте пешком, и по этому параметру дом обходит всех.', { bold: true })],
        'У «Файв Тауэрс» за меньшие деньги — фитнес-центр, коворкинг с библиотекой, детский клуб, лаунж с террасой на 49 этаже и консьерж 24/7. У Lunar, который сдан и заселён, — спортзал, детская комната, фитнес-клуб и медцентры на первых этажах.',
        'Обмен понятный: наш дом даёт минуту до метро и новый корпус, конкуренты — сервис и готовую квартиру. Для семьи с детьми и удалённой работой это не в нашу пользу; для того, кто каждый день ездит в центр, — в нашу.',
        [txt('Практический вывод: '), txt('в продаже нужно бить в то, чем дом действительно отличается, — метро в минуте, Академический парк в шести, новый корпус 2026 года. Инфраструктурой этот лот не выигрывает и не выиграет.')],
      ]),

      // ─────────────── WHAT TO DO ───────────────
      h1('Что с этим делать'),
      body('Цена внутри дома выставлена корректно, поэтому обычный рецепт «снизить и подождать» здесь работает плохо. Проблема не в цене метра, а в том, что лот продаётся как бетон, а конкурирует с готовым.', { after: 170 }),
      statTiles([
        ['57,0 млн ₽', 'Цена сейчас'],
        ['68,9 млн ₽', 'Бюджет «под ключ»'],
        ['53–55', 'Если торговаться, млн ₽'],
        ['667–692', 'Цена за метр, тыс. ₽', true],
      ]),
      spacer(70),
      ...bullets([
        [txt('Главный рычаг — не цена, а определённость ремонта. '), txt('Сейчас покупатель считает «57,0 млн плюс сколько-то и когда-то». Дизайн-проект с фиксированной сметой подрядчика превращает это в «57,0 + 11,9 млн и восемь месяцев». Это единственное, что меняет сравнение в нашу пользу, не трогая цену.', { bold: true })],
        'Второй вариант того же — продавать лот уже с выполненным ремонтом. Тогда он попадает в одну корзину с «Новыми Черемушками» и Новочерёмушкинской, 17, где сравнение идёт по готовому продукту, а преимущество в минуте до метро наконец начинает работать.',
        [txt('Если торговаться, разумный коридор — 53–55 млн ₽ (667–692 тыс. ₽/м²). '), txt('«Под ключ» это 64,9–66,9 млн ₽: лот становится дешевле Новочерёмушкинской, 17 за 65,0 млн ₽ и заметно дешевле Lunar. Скидка от текущей цены — 2,0–4,0 млн ₽.')],
        'Ниже 53 млн ₽ опускаться незачем: там лот уходит под цену соседей по дому, приведённую к его этажу, — это уже не рыночная корректировка, а демпинг в экспозиции, где застройщик всё равно держит прайс.',
        [txt('Чего делать точно не стоит — ждать без изменений. '), txt('В доме одновременно семь других трёшек без отделки, застройщик распродаёт остатки по прайсу, а локация предлагает готовые квартиры на 24–27 млн ₽ дешевле. Само по себе это не рассосётся.')],
      ]),

      spacer(40),
      rule(120),
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
  const out = path.join(__dirname, 'Левел_Академическая_аналитика_по_лоту.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
