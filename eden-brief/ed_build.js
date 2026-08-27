const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'ed_tables.json'), 'utf8'));
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
  ['22', 'Резиденции'],
  ['от 1,1 млрд ₽', 'Старт продаж'],
  ['≈ 3,5 млн ₽', 'Оценка цены метра'],
  ['III кв. 2029', 'Срок сдачи', true],
];
const SRC = [
  ['Официальные сайты проекта и застройщика — eden.moscow, sense.ru', 'https://eden.moscow/'],
  ['Цена старта продаж («от 1,1 млрд ₽ за резиденцию площадью около 250 кв. м») и закрытый формат продаж — обзор рынка от 06.04.2026', 'https://novostroev.ru/articles/blizhayshie-starty-prodazh-novostroek-moskvy-v-aprele-mae-2026/'],
  ['Параметры дома: площади, потолки, паркинг, лифты, участок, общая площадь — карточка проекта на «Элитное.ру»', 'https://elitnoe.ru/complexes/1099-zhk-eden-nizhnii-kislovskii-7'],
  ['Состав квартир, срок сдачи, стадия строительства, отделка, эскроу — «Новострой-М» и «Новостроев»', 'https://www.novostroy-m.ru/baza/jk_na__nijniy'],
  ['Застройщик и проектная декларация — ЕРЗ.РФ, ООО СЗ «СЕНС.КИСЛОВСКИЙ»', 'https://erzrf.ru/novostroyki/zhk-eden-28740607001'],
  ['Цены по элитным районам, число сделок и объём предложения в делюксе — NF Group, отчёты за 2025 год и I полугодие 2026', 'https://nfgroup.ru/analytics/'],
  ['Сделки дороже 1 млрд ₽ — Whitewill, Kalinka Ecosystem и Intermark (РБК Недвижимость)', 'https://realty.rbc.ru/'],
  ['Цены конкурентов — выгрузки Циан от 17.08.2026: «Люче» 26 лотов, «Кло 17» 17, «Никитский-6» 16, Turandot 8, «Брюсов» 4, Stella di Mosca 3 — всего 74 квартиры после схлопывания дублей', ''],
  ['Параметры и срок сдачи клубного дома «Брюсов» (Vos’hod) — «Новострой.ру» и «Элитное.ру»', 'https://www.novostroy.ru/buildings/bryusov-by-loro-piana/'],
  ['Визуализации, хроника согласований и отраслевые оценки цены — Telegram-каналы «Старты продаж» (посты 9568, 9725, 11406) и Property Insider (32429), 2024–2025', 'https://t.me/startyprodazh/11406'],
  ['Координаты для карты — OpenStreetMap по адресу и карточки проектов. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'ЖК EDEN Private Residence — информационная справка',
  description: 'Информационная справка по клубному дому EDEN Private Residence, Нижний Кисловский пер., 7',
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
        txt('EDEN Private Residence · информационная справка от 02.08.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Информационная справка · Москва · 2 августа 2026'),
      p({ children: [txt('EDEN', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('Private Residence — клубный дом де-люкс на 22 резиденции', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Москва, ЦАО, Нижний Кисловский переулок, 7  ·  застройщик Sense  ·  м. «Арбатская»', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('Основные параметры', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(K.facts),
      spacer(40),
      note('Сам EDEN продаётся закрыто: рекламной кампании нет, прайс-лист не публикуется, объявлений на площадках нет. За базовую принята оценка 3,5 млн ₽ за метр, и всё посчитанное из неё помечено словом «оценка». Зато по шести соседним клубным домам данные полные: 74 квартиры из выгрузок Циан от 17.08.2026.'),
      spacer(40),
      kicker('Расположение и ближайшие конкуренты', INK),
      image('map.jpg', PX, 391, { after: 46 }),
      note('Все шесть конкурентов — в пределах километра от EDEN, четыре из них ближе полукилометра. Цена метра в подписях — из выгрузок Циан от 17.08.2026, у EDEN — оценка. «Stella di Mosca» (Б. Никитская, 9/15) в таблицах участвует, но на карту не нанесена: координаты дома подтвердить не удалось.'),

      // ─────────────── RENDERS ───────────────
      h1('Как будет выглядеть дом', { br: true }),
      body('Официальной галереи застройщик не публикует, но визуализации проекта попали в отраслевые Telegram-каналы в 2024–2025 годах. Из них видно главное: светлый каменный фасад в классической стилистике, аркада по первому этажу, террасы на верхних уровнях и переменная этажность, которой дом подстраивается под соседей.', { after: 170 }),
      image('r1.jpg', PX, 447, { after: 40 }),
      caption('Главный фасад: шесть этажей по улице, рустованный цоколь, террасы с озеленением наверху.'),
      spacer(46),
      imagePair('r2.jpg', 'r3.jpg',
        'Угловой ракурс: аркада первого этажа и рустованный цоколь.',
        'Вид с Воздвиженки — справа особняк Арсения Морозова.'),
      spacer(46),
      image('r4.jpg', PX, 404, { after: 40 }),
      caption('Вид из переулка: дом встраивается в историческую застройку Кисловской слободы.'),
      spacer(30),
      note('Изображения взяты из публикаций отраслевых Telegram-каналов и, по словам авторов публикаций, найдены в открытых источниках. Это не официальные материалы застройщика, и итоговый облик здания может отличаться.'),

      // ─────────────── WHAT IT IS ───────────────
      h1('Что это за дом'),
      body('EDEN — не жилой комплекс, а один клубный дом на 22 квартиры в историческом центре. Для понимания масштаба: жилой площади в нём 6,2 тыс. м² при общей площади здания 17,1 тыс. м² и участке 0,3 гектара. То есть на жильё приходится чуть больше трети здания, остальное — паркинг на трёх подземных уровнях, лобби, технические и сервисные помещения.', { after: 180 }),
      dataTable(
        ['Тип квартир', 'Сколько', 'Площади', 'Ориентировочная цена'],
        K.mix, [2600, 1500, 2600, 2938],
        { boldFirstCol: true, totalLast: true },
      ),
      spacer(30),
      note('Цены в последней колонке — оценка: площади умножены на 3,5 млн ₽ за метр. Застройщик поквартирный прайс не публикует, поэтому это порядок величины, а не прайс-лист.'),
      spacer(40),
      ...bullets([
        [txt('22 резиденции при средней площади 282 м² — это формат, где квартира занимает целый этаж или половину этажа. '), txt('Потолки 3,6–4,2 метра, панорамное остекление, переменная этажность от шести до девяти этажей, чтобы вписаться в историческую застройку переулка.', { bold: true })],
        [txt('Паркинга хватает с запасом: 43 машиноместа на 22 квартиры, почти два на резиденцию. '), txt('Для центра это редкость: в соседнем «Никитском 6» на 61 квартиру приходится 135 мест, тоже с запасом, а в большинстве старых домов района паркинга нет вовсе.')],
        'Лифтовое хозяйство под стать: восемь пассажирских и три грузовых лифта на 22 квартиры. Архитектура — американское бюро Gregory Tuck Architects, застройщик Sense (среди совладельцев — группа ПИК).',
        [txt('Квартиры продаются без отделки. '), txt('Это норма для сегмента, но означает, что к цене покупателю нужно прибавить ремонт: при площади 282 м² и де-люксовом уровне отделки это ещё несколько сотен миллионов рублей и год-полтора работ после сдачи.', { bold: true })],
      ]),

      // ─────────────── PRICE ───────────────
      h1('Сколько это стоит'),
      body('Прайс-листа у EDEN нет, поэтому цена метра здесь — оценка: 3,5 млн ₽, откуда она берётся, разобрано первым пунктом после таблицы. А вот по конкурентам данные полные: шесть клубных домов в пешей доступности, 74 квартиры после схлопывания дублей.', { after: 180 }),
      dataTable(
        ['Проект', 'Адрес', 'Квартир', 'Площади,\nм²', 'Цены,\nмлн ₽', 'Цена за м²,\n₽', 'Отделка', 'Метр «под\nключ», ₽'],
        K.price, [1900, 1900, 850, 1100, 1200, 1150, 1500, 1188],
        { boldFirstCol: true, leftCols: [1, 6] },
      ),
      spacer(30),
      note('Последняя колонка приводит всё к одному состоянию — квартире, в которую можно заехать. Там, где квартиры продаются без отделки, к цене метра добавлено 750 тыс. ₽ — столько стоит отделка де-люкс уровня в центре. Где часть квартир с отделкой, а часть без, доплата добавлена пропорционально. Циан, выгрузки от 17.08.2026.'),
      spacer(40),
      image('chart.jpg', PX, 285, { after: 60 }),
      caption('Синее — цена метра сейчас, бронзовое — доплата за отделку. Красный — расчёт по EDEN.'),
      spacer(40),
      ...bullets([
        [txt('Откуда взялись 3,5 млн ₽ за метр. '), txt('Опубликована одна цифра — «от 1,1 млрд ₽ за резиденцию площадью около 250 кв. м». Делением получается 4,4 млн ₽ за метр, но это верхняя граница: формулировка описывает конкретный лот, а площадь в ней округлена. При 3,5 млн ₽ те же 1,1 млрд соответствуют резиденции 314 м², что ближе к реальному составу дома — средняя площадь 282 м², 18 квартир из 22 от четырёх комнат. Дальше в справке считается по 3,5 млн; 4,4 млн стоит держать в голове как потолок.', { bold: true })],
        [txt('EDEN дороже 64 квартир из 74. '), txt('Средняя по конкурентам — 2 973 тыс. ₽ за метр, у EDEN оценочные 3 500 тыс.: это на 18 % выше. Более дорогой метр у десяти лотов из 74.', { bold: true })],
        [txt('Верхнюю границу рынка держит «Брюсов», и держит с большим отрывом. '), txt('Клубный дом Vos’hod в Брюсовом переулке, в полукилометре от EDEN: четыре резиденции 208,7–329,3 м² по 1,00–1,81 млрд ₽, метр от 4 318 до 6 843 тыс. ₽, в среднем 5 608 тыс. Это на 32 % выше метра EDEN «под ключ» (4 250 тыс.). Причём «Брюсов» сдаётся в 2026 году и продаётся с готовой отделкой по дизайну Loro Piana Interiors, а EDEN — котлован без отделки со сдачей в 2029-м. То есть по цене EDEN идёт заметно ниже проверенного потолка локации, и это его сильная сторона.', { bold: true })],
        [txt('Нижнюю границу задаёт «Кло 17» на Староваганьковском. '), txt('Тот же формат клубного дома в центре, те же 17 квартир без отделки от застройщика — самый близкий аналог по продукту. Метр там 2 627 тыс. ₽, то есть EDEN дороже на 33 %. С учётом отделки у обоих: 4 250 против 3 377 тыс. ₽ — разрыв 26 %.', { bold: true })],
        'В деньгах по EDEN: самая маленькая резиденция 107,3 м² — около 376 млн ₽, самая большая 410,6 м² — около 1,44 млрд ₽, средняя — примерно 0,99 млрд ₽. Весь дом целиком — порядка 22 млрд ₽.',
        [txt('Ипотека в проекте не работает: '), txt('ипотечные и военные программы по дому недоступны. Для сегмента это норма — сделки идут за собственные средства, и круг покупателей ограничен наличием живых денег, а не кредитоспособностью.')],
      ]),

      h2('Что покупают за сопоставимые деньги'),
      body('Двенадцать самых дорогих квартир формата EDEN — от 200 до 420 м² — из тех же шести домов. Это то, что покупатель видит рядом.', { after: 170 }),
      dataTable(
        ['Проект', 'Квартира', 'Отделка', 'Цена,\nмлн ₽', 'Метр «под\nключ», ₽', 'Ссылка'],
        K.city, [2100, 1900, 1900, 1100, 1500, 1138],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(30),
      note('Для квартир без отделки в последней колонке добавлены 750 тыс. ₽ за метр. Единственному лоту с неуказанной отделкой — вторичной квартире 209,0 м² в «Брюсове» — доплата не добавлена: это готовое жильё, а не бетон. Циан, 17.08.2026; ссылка ведёт на объявление.'),
      spacer(40),
      ...bullets([
        [txt('Дороже миллиарда рублей во всей подборке — шесть лотов из семидесяти четырёх, и четыре из них в «Брюсове». '), txt('Плюс 358,9 м² в «Фамильном доме Люче» за 2,15 млрд ₽ и 198,1 м² в «Stella di Mosca» за 1,07 млрд ₽. При 3,5 млн ₽ за метр порог в миллиард у EDEN проходят резиденции от 286 м², то есть примерно половина дома — около десяти квартир из 22.', { bold: true })],
        'Формат EDEN — квартиры от 200 м² — в подборке представлен неплохо: 30 лотов из 74. Так что дело не в том, что таких площадей нет рядом. Дело в цене, по которой они предлагаются.',
        [txt('И отдельно — про точность самой оценки. '), txt('3,5 млн ₽ за метр — это рабочая величина, а не прайс. Верхняя граница по опубликованному старту продаж — 4,4 млн; при ней средняя резиденция стоила бы 1,24 млрд вместо 0,99 млрд, а весь дом — 27 млрд вместо 22. Все выводы ниже устойчивы в этом диапазоне, но конкретные суммы поедут вслед за реальным прайсом.', { bold: true })],
      ]),

      // ─────────────── MARKET ───────────────
      h1('В какой рынок продаётся этот дом'),
      body('Дальше — не про сам дом, а про то, сколько покупателей на такой формат вообще существует. Цифры взяты из отчётов консультантов и относятся ко всей Москве.', { after: 180 }),
      dataTable(
        ['Показатель рынка делюкс', 'Значение', 'Источник'],
        K.market, [4900, 2600, 2138],
        { boldFirstCol: true, leftCols: [0] },
      ),
      spacer(30),
      note('Делюкс — самый узкий класс московского рынка жилья: по критериям NF Group это проекты в пределах ЦАО со средней площадью квартиры от 110 м². EDEN в эти критерии попадает.'),
      spacer(40),

      h2('Сколько бывает сделок дороже миллиарда'),
      dataTable(
        ['Период', 'Сколько', 'Источник'],
        K.billion, [3900, 2600, 3138],
        { boldFirstCol: true, leftCols: [0, 2] },
      ),
      spacer(30),
      ...bullets([
        [txt('Пять сделок дороже миллиарда за 2023 год, девять за 2024-й, двадцать за 2025-й. '), txt('Intermark оценивает годовой спрос в 20–25 таких лотов на всю Москву. При этом предложение растёт быстрее спроса: на март 2026 в продаже стояло 85 лотов дороже миллиарда против 73 годом ранее.', { bold: true })],
        [txt('До сдачи 37 месяцев, чуть больше трёх лет. '), txt('Чтобы распродаться к вводу, дому нужно закрывать 7,1 сделки в год. От всего класса делюкс это немного — 2,4 % сделок 2025 года. Но средний чек здесь около 0,99 млрд ₽ — вдвое выше средней сделки в делюксе (437–515 млн ₽ по NF Group). Считать долю нужно от верхнего среза рынка, а он в разы уже.', { bold: true })],
        [txt('И отдельно — по самым дорогим резиденциям. '), txt('При 3,5 млн ₽ за метр дороже миллиарда стоят квартиры от 286 м², то есть примерно половина дома — около десяти лотов. На них нужно около 3,2 сделки в год, а это 13–16 % всего московского рынка сделок дороже миллиарда, три года подряд. Это и есть самое узкое место проекта.', { bold: true })],
        'Это не приговор проекту: элитные дома в Москве продаются в среднем четыре-пять лет с начала строительства, и застройщик явно рассчитывает на срок за горизонтом ввода. Но это задаёт реалистичную рамку ожиданий: быстрой распродажи в таком формате не бывает.',
        [txt('Класс делюкс за первое полугодие 2026 года дал 110 сделок против 300 за весь 2025-й — '), txt('— падение на 35 % к прошлому году. Одновременно объём предложения вырос до 1 090 квартир, плюс 11 %. Спрос сжимается, предложение растёт — NF Group прямо называет это «рынком покупателя».', { bold: true })],
      ]),

      // ─────────────── DEVELOPER ───────────────
      h1('Кто строит'),
      body('Sense — молодой девелопер: компанию возглавляет Юрий Матвеев, учредитель — ООО «Проф Инжиниринг», связанное с группой ПИК и её акционером. Портфель небольшой и почти весь ещё не сдан.', { after: 180 }),
      dataTable(
        ['Проект', 'Адрес', 'Что это', 'Срок'],
        K.sense, [2700, 2400, 3200, 1338],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(30),
      ...bullets([
        [txt('Готовый объект у застройщика пока один — апарт-комплекс Logos в Даниловском районе. '), txt('Всё остальное строится: клубный дом PHANTOM на Малой Сухаревской сдаётся в 2027 году, бизнес-центр Omni Tower — в 2028-м, EDEN — в 2029-м. Опыта сдачи жилья де-люкс у компании ещё нет, и ЕРЗ по этой причине пока не присваивает проекту потребительский рейтинг.', { bold: true })],
        'Отдельная линия работы — реставрация двух городских усадеб XVIII и XX веков. Для проекта в историческом переулке это уместный опыт: EDEN встраивается в охраняемую историческую среду, и переменная этажность 6–9 этажей — следствие именно этих ограничений.',
        [txt('Продажи идут по ДДУ с эскроу. '), txt('То есть деньги покупателя лежат в банке до сдачи дома, и застройщик получает их только после ввода. Для проекта на стадии котлована со сроком 2029 года это принципиально: риск недостроя закрыт стандартным механизмом 214-ФЗ.')],
      ]),

      h2('Хроника проекта'),
      body('Что известно о ходе проекта по документам и публикациям в отраслевых каналах.', { after: 170 }),
      dataTable(
        ['Когда', 'Что произошло'],
        K.chron, [1900, 7738],
        { boldFirstCol: true, leftCols: [1], vtop: true },
      ),
      spacer(30),
      ...bullets([
        [txt('Заявленная площадь комплекса за полтора года уменьшилась. '), txt('Весной 2024 года по проекту ожидалось около 20 000 м², в сентябре 2024-го, после прохождения экспертизы, названа цифра около 12 700 м². Профильные карточки проекта при этом указывают 17,1 тыс. м² общей площади. Единой подтверждённой цифры нет, и это стоит держать в голове при любых расчётах, опирающихся на объём здания.', { bold: true })],
        [txt('Средняя площадь квартиры подтверждается независимо. '), txt('В декабре 2025 года в отраслевом канале названа средняя площадь квартиры 281 м². Наш расчёт по опубликованным данным — 282 м² (6,2 тыс. м² жилья на 22 резиденции). Две независимые оценки сошлись.', { bold: true })],
        [txt('С бюро Gregory Tuck Architects ранее работали над московскими проектами Lion Gate и Noble Row. '), txt('В отрасли из-за этого обсуждают, насколько концепция EDEN самостоятельна. На качество дома это не влияет, но в сегменте, где отдельно платят за уникальность, вопрос не праздный.')],
        [txt('Ожидания по цене в отрасли изначально были высокими. '), txt('В декабре 2025 года в отраслевом канале прогнозировали, что Sense выйдет с ценой заметно выше рынка, а «Никитский-6» оценивали «на уровне 3 миллионов с метра». Оценка по «Никитскому-6» подтвердилась точно: по выгрузкам он идёт по 3,19 млн ₽ за метр. Насколько подтвердится вторая, покажет прайс: оценочные 3,5 млн у EDEN выше соседа лишь на 10 %, а верхняя граница 4,4 млн — уже на 38 %.', { bold: true })],
      ]),

      // ─────────────── CONCLUSIONS ───────────────
      h1('Выводы'),
      ...bullets([
        [txt('22 резиденции средней площадью 282 м² с потолками до 4,2 метра — в переулке между Большой Никитской и Воздвиженкой, в двухстах метрах от Александровского сада. '), txt('Почти два машиноместа на квартиру для центра — отдельная ценность.', { bold: true })],
        [txt('Цена по оценке в 3,5 млн ₽ за метр смотрится обоснованно. '), txt('Это дороже 64 квартир из 74 в шести соседних клубных домах и на 18 % выше средней по ним, но заметно ниже проверенного потолка локации. Нижнюю границу задаёт «Кло 17» без отделки — 2,63 млн ₽ за метр, EDEN дороже на 33 %. Верхнюю держит «Брюсов» — 5,61 млн ₽ за метр «под ключ» против 4,25 млн у EDEN, разрыв 32 %.', { bold: true })],
        [txt('«Брюсов» в полукилометре продаёт готовые квартиры с отделкой по 5,6 млн ₽ за метр и сдаётся в 2026 году. '), txt('EDEN при 3,5 млн стоит на треть дешевле — и это разумная плата за то, что покупателю ждать три года и делать ремонт самому. Если же реальный прайс выйдет к верхней границе оценки, в 4,4 млн, этот запас почти исчезнет: метр «под ключ» станет 5,15 млн против 5,61 млн у готового соседа.', { bold: true })],
        [txt('Главный риск — ёмкость спроса. '), txt('Дому нужно продавать 7,1 резиденции в год со средним чеком около 0,99 млрд ₽ — вдвое выше средней сделки в делюксе. Примерно половина дома, около десяти лотов, дороже миллиарда: на них нужно 3,2 сделки в год, то есть 13–16 % всего московского рынка таких сделок, три года подряд. Предложение при этом растёт быстрее спроса. Срок реализации будет длинным, и он должен быть в ожиданиях с самого начала.', { bold: true })],
        [txt('Все соседи готовы раньше, и это остаётся минусом. '), txt('«Никитский-6» в ста пятидесяти метрах — 3,19 млн ₽ за метр с чистовой отделкой и сдачей в 2026 году. «Фамильный дом Люче» от MR Group в двухстах метрах — 2,94 млн ₽ с отделкой, 26 квартир в продаже. «Кло 17» — 2,63 млн ₽ без отделки. Все они дешевле EDEN по метру, но и разрыв невелик: у «Никитского-6» с отделкой метр всего на 9 % дешевле оценочного метра EDEN без отделки.', { bold: true })],
        [txt('И главное ограничение самой справки. '), txt('Цены конкурентов взяты из выгрузок и достоверны, а метр EDEN — оценка: поквартирного прайса нет, объявлений на площадках нет. Диапазон оценки — 3,5–4,4 млн ₽ за метр, в справке считается по нижней границе. Как появится реальный прайс, всё помеченное словом «оценка» нужно пересчитать.', { bold: true })],
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
      note('Справка подготовлена 02.08.2026 по открытым источникам. Цена метра EDEN и всё производное от неё — оценка, а не прайс. Документ носит справочный характер и не является офертой или отчётом об оценке.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'ЖК_EDEN_информационная_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
