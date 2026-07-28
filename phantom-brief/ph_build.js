const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const T  = JSON.parse(fs.readFileSync(path.join(__dirname, 'ph_tables.json'), 'utf8'));
const CO = JSON.parse(fs.readFileSync(path.join(__dirname, 'ph_comp.json'), 'utf8'));
const CM = JSON.parse(fs.readFileSync(path.join(__dirname, 'ph_cmp.json'), 'utf8'));
const IMG = (n) => fs.readFileSync(path.join(__dirname, 'ph_out', n));

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
  ['III кв. 2027', 'Срок сдачи'],
  ['7–9', 'Этажей'],
  ['99', 'Квартир в проекте'],
  [`${T.meta.min} млн ₽`, 'Цена входа, от'],
];

const FACTS = [
  ['Застройщик', 'Sense Development'],
  ['Класс', 'Делюкс'],
  ['Адрес', 'Малая Сухаревская пл., 6'],
  ['Метро', '«Сухаревская», ≈4 мин пешком'],
  ['Корпуса', '3 разновысотных'],
  ['Этажность', '7–9 этажей'],
  ['Площади', '83–280 м²'],
  ['Потолки', '3,2–3,8 м'],
  ['Отделка', 'Авторская дизайнерская'],
  ['Участок', '1,0 га · сад 0,5 га'],
  ['Паркинг', '184 м/м, валет-сервис'],
  ['Архитектура', 'PLP Architecture'],
  ['Схема продаж', 'ДДУ, эскроу (214-ФЗ)'],
  ['В экспозиции', `${T.meta.lots} лота · ${T.meta.volume} млрд ₽`],
];

const SRC = [
  ['Циан — выгрузка по ЖК «ФАНТОМ» (ID 4780951), 52 лота, 28.07.2026', ''],
  ['Циан — выгрузки по конкурентам на 28.07.2026: Turgenev (ID 1252876) 21 лот, «Николь» (ID 4712505) 23, «Дом Франка» (ID 4186702) 21, Zvonarsky Deluxe (ID 7782) 2', ''],
  ['Официальный сайт проекта', 'https://phantom.moscow/'],
  ['Яндекс Недвижимость — карточка ЖК и конкурентное окружение', 'https://realty.yandex.ru/'],
  ['Новострой-М — карточка клубного дома Phantom', 'https://www.novostroy-m.ru/baza/jk_na_maloy_suharevskoy'],
  ['Яндекс Карты — картографическая основа', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'Клубный дом PHANTOM — информационная справка',
  description: 'Клубный дом делюкс-класса PHANTOM, Sense Development, Москва, Малая Сухаревская пл., 6',
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
        txt('Клубный дом PHANTOM · Sense Development · информационная справка от 28.07.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      // ─────────────── TITLE ───────────────
      image('hero.jpg', PX, 189, { after: 130 }),
      kicker('Информационная справка · Москва · 28 июля 2026'),
      p({ children: [txt('Клубный дом PHANTOM', { font: S.GEO, size: 48, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('Резиденции ФАНТОМ · делюкс · Sense Development', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Москва, ЦАО, Мещанский район, Малая Сухаревская площадь, 6  ·  м. «Сухаревская»', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('Основная информация', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(FACTS),
      spacer(80),
      kicker('Расположение', INK),
      image('map_loc.jpg', PX, 355, { after: 50 }),
      note('Между Цветным бульваром и Сретенкой, внутри Садового кольца. Основа: Яндекс Карты.'),

      // ─────────────── ARCHITECTURE ───────────────
      h1('Архитектура проекта', { br: true }),
      body('Три разновысотных корпуса высотой 7–9 этажей на участке 1,0 га. Северный и южный корпуса спроектированы британским бюро PLP Architecture, восточный — Da Costa Mahindroo Architects. Пластика фасадов построена на горизонтальном ритме террас и панорамном остеклении; ансамбль замыкает приватный сад.', { after: 220 }),
      image('g1.jpg', PX, 362),
      caption('Фасады со стороны сада. Визуализация застройщика.'),
      image('g2.jpg', PX, 362),
      caption('Южный корпус и садовое кольцо внутри квартала.'),

      // ─────────────── GARDEN & AMENITIES ───────────────
      h1('Сад и инфраструктура', { br: true }),
      body('Половина территории — приватный ландшафтный сад площадью 0,5 га, спроектированный гонконгским бюро ONE Landscape. В составе клубной инфраструктуры — лобби с зонами отдыха и деловых встреч, спортзал, СПА, детская игровая, кинозал и библиотека. Интерьеры общественных пространств — бюро Modum. Паркинг на 184 машиноместа увеличенной площади с валет-сервисом, автомойкой и кладовыми.', { after: 220 }),
      image('g3.jpg', PX, 362),
      caption('Приватный сад площадью 0,5 га.'),
      spacer(60),
      imagePair('g4.jpg', 'g6.jpg',
        'Главный холл северного корпуса.',
        'Кухня-гостиная в авторской отделке.'),
      spacer(160),
      note('Все изображения — визуализации застройщика (Sense Development). Итоговый облик объекта может отличаться.'),

      // ─────────────── PRICES ───────────────
      h1('Цены и структура предложения', { br: true }),
      body([
        txt('По состоянию на '), txt('28 июля 2026 г.', { bold: true }),
        txt(' в экспозиции Циан находится '), txt(`${T.meta.lots} лота`, { bold: true }),
        txt(` совокупной площадью ${T.meta.area} м² и общим объёмом предложения `),
        txt(`${T.meta.volume} млрд ₽`, { bold: true }),
        txt('. Все лоты продаёт напрямую застройщик. 100 % квартир — с авторской дизайнерской отделкой, что принципиально отличает проект от новостроек, которые продаются «в бетоне».'),
      ], { after: 200 }),

      h2('Цены по типам квартир'),
      dataTable(
        ['Тип квартиры', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nлота, млн ₽', 'Цена за м²\nот — до, ₽', 'Средняя цена\nза м², ₽'],
        T.types, [1760, 700, 1400, 1620, 1200, 1740, 1218],
        { totalLast: true, boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена за м² — средневзвешенная по площади. Простое среднее по лотам — ' + T.meta.simple + ' ₽/м². Средняя цена лота по проекту — ' + T.meta.avg + ' млн ₽.'),

      h2('Цены по корпусам'),
      dataTable(
        ['Корпус', 'Этажей', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽', 'Отклонение от\nсредней по ЖК'],
        T.corps, [1300, 1000, 900, 1600, 1740, 1500, 1598],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Восточный корпус заметно доступнее двух других: −10 % к средней по проекту. Северный и южный идут вровень, по +5 %.'),

      h2('Цены по этажам'),
      dataTable(
        ['Диапазон этажей', 'Лотов в продаже', 'Средняя цена за м², ₽', 'Отклонение от средней по ЖК'],
        T.floors, [2400, 1900, 2600, 2738],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Премия за верхние этажи выражена сильно: разрыв между поясом 1–3 и 7–9 составляет около 39 % удельной цены — на верхних этажах расположены пентхаусы и лоты с видами.'),

      h2('Ключевые наблюдения'),
      ...bullets([
        [txt('Средневзвешенная цена по проекту — '), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt(`; диапазон удельных цен — от 1 650 000 до 3 580 000 ₽/м². Порог входа — ${T.meta.min} млн ₽, максимум экспозиции — ${T.meta.max} млн ₽.`)],
        'Ядро предложения — 2-комнатные квартиры: 25 лотов, 48 % экспозиции, площади 110–359 м². «Двухкомнатная» здесь означает просторную квартиру с мастер-спальней, а не компактное жильё.',
        'Удельная цена почти не зависит от комнатности: разброс средних по типам укладывается в 2,30–2,67 млн ₽/м². Главные ценообразующие факторы — корпус, этаж и вид, а не количество комнат.',
        'Крупные форматы дороже за метр: 4+ комнатные идут по 2,67 млн ₽/м² против 2,44 млн у двух- и трёхкомнатных. В премиальном сегменте обычно наоборот.',
      ]),
      spacer(30),
      note('Вся экспозиция — с готовой авторской отделкой: покупатель получает квартиру, в которую можно въезжать, без дополнительного бюджета на ремонт.'),

      // ─────────────── COMPETITION ───────────────
      h1('Конкурентное окружение', { br: true }),
      body('Локация «Сухаревская — Цветной бульвар — Сретенка» — один из самых насыщенных элитных рынков Москвы. В радиусе полутора километров сосредоточены семь проектов элит-класса: часть уже введена, часть строится и конкурирует с PHANTOM за одного покупателя.', { after: 200 }),
      dataTable(
        ['№  Проект', 'Адрес', 'Застройщик', 'Класс', 'Ввод', 'Расстояние'],
        CO, [2320, 2320, 1780, 780, 1240, 1198],
        { boldFirstCol: true },
      ),
      spacer(50),
      note('Расстояния — по прямой от Малой Сухаревской пл., 6. Ближайший конкурент — «Форум» MR Group, в 100 метрах через Садовое кольцо. За пределами карты остаются «Николь» (Большой Черкасский пер., 4, ввод 2028) и «Сент Николас» (Никольская, 10, сдан 2015) — оба в 1,7–2,0 км, уже в Китай-городе.'),
      image('map_comp.jpg', PX, 439, { after: 60 }),
      caption('Проекты элит-класса в радиусе ≈1,5 км от клубного дома PHANTOM. Источник картографической основы: Яндекс Карты.'),

      // ─────────────── PRICE COMPARISON ───────────────
      h1('Ценовое сравнение с конкурентами', { br: true }),
      body('Сравнение построено на выгрузках Циан по четырём проектам локации на ту же дату — 28.07.2026, суммарно 67 лотов. Из каждой выгрузки взята одна база: квартиры площадью 60–200 м², в которую попадает 36 из 52 лотов PHANTOM.', { after: 130 }),
      body([
        txt('Здесь сравнение проще, чем обычно. ', { bold: true }),
        txt('PHANTOM, «Николь» и Turgenev продаются с готовой отделкой, Zvonarsky Deluxe — вторичное жильё. Единственный, кто идёт без отделки, — «Дом Франка»: только к нему нужна поправка.'),
      ], { after: 190 }),

      h2('Экспозиция конкурентов'),
      dataTable(
        ['ЖК', 'Тип рынка', 'Лотов\nв продаже', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Цена за м²\nот — до, ₽', 'Средняя цена\nза м², ₽'],
        CM.t1, [1500, 1180, 860, 1300, 1420, 2140, 1238],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена за м² — средневзвешенная по площади. Выборка Zvonarsky Deluxe — всего 2 лота, оценка по нему индикативна.'),

      h2('Уровень отделки'),
      dataTable(
        ['ЖК', 'Без отделки, ₽/м²', 'Чистовая, ₽/м²', 'Дизайнерская, ₽/м²', 'Не указана, ₽/м²'],
        CM.t2, [1620, 2020, 1980, 2100, 1918],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('В Turgenev застройщик продаёт квартиры с чистовой отделкой (14 лотов), а четыре лота с дизайнерским ремонтом — это перепродажа от агентств, и они дешевле. В «Николь» один лот выставлен без отделки по 4 950 000 ₽/м² — это пентхаус, а не сопоставимая база.'),

      h2('Сколько стоит квартира, готовая к заселению', { br: true }),
      body('Приводим все проекты к одному состоянию — квартире, в которую можно заехать. У четырёх проектов отделка уже есть, их цена берётся как есть. К «Дому Франка» добавляется отделка: для делюкс-сегмента это 300 тыс. ₽/м².', { after: 170 }),
      image('chart.jpg', PX, 285, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 28.07.2026, сопоставимая выборка 60–200 м².'),
      note('При ставке 250 тыс. ₽/м² метр в «Доме Франка» выходит в 2 029 314 ₽, при 400 тыс. — в 2 179 314 ₽. Порядок проектов не меняется ни в одном сценарии: он остаётся самым доступным в локации.'),

      h2('Выводы'),
      ...bullets([
        [txt('PHANTOM занимает середину элитного рынка локации: '), txt('на 22 % дешевле Turgenev', { bold: true }), txt(' и на 24 % дешевле «Николь», но на 4 % дороже Zvonarsky Deluxe и на 12 % дороже «Дома Франка» с учётом отделки.')],
        'Два самых дорогих проекта — Turgenev и «Николь» — идут по 3,0–3,1 млн ₽/м². PHANTOM держится на уровне 2,34 млн ₽/м², то есть продаётся заметно ниже верхней планки локации при сопоставимом уровне отделки.',
        '«Дом Франка» — единственный, кто конкурирует ценой: 1,78 млн ₽/м² без отделки. Но и с добавленной отделкой он остаётся самым доступным, а его квартиры мельче — до 168 м² против 190 м² у PHANTOM в той же выборке.',
        'Порог входа в локации задаёт «Дом Франка» — 116,7 млн ₽. PHANTOM начинается со 135,0 млн ₽, Turgenev — со 165,6 млн ₽, «Николь» — с 209,2 млн ₽.',
        'Разброс удельных цен внутри PHANTOM (1,68–3,28 млн ₽/м²) уже, чем у Turgenev (1,90–5,64 млн) и «Николь» (2,33–4,95 млн). Ценообразование в проекте более ровное, без резких выбросов на верхних лотах.',
      ]),
      spacer(40),
      note('Ограничения: по Zvonarsky Deluxe доступно 2 лота, уровень отделки в выгрузке не указан — оценка индикативна. «Николь» расположен в Китай-городе, в 1,7 км от PHANTOM, и относится к соседней локации.'),

      // ─────────────── POSITIONING ───────────────
      h2('Позиционирование PHANTOM', { br: true }),
      ...bullets([
        [txt('Средневзвешенная цена '), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt(' ставит проект в верхний сегмент московского рынка. Для сравнения: премиальный МАСТЕРС у метро «Аэропорт» продаётся по 761 тыс. ₽/м² — разница более чем троекратная и отражает разницу классов и локаций.')],
        'Ключевое отличие от большинства новостроек — готовая авторская отделка во всех лотах. В локации без отделки продаётся только «Дом Франка», остальные конкуренты тоже сдают квартиры готовыми.',
        'Преимущества локации: внутри Садового кольца, 4 минуты до метро «Сухаревская», Цветной бульвар и Сретенка в пешей доступности, приватный сад 0,5 га — редкость для центра.',
        'Архитектурный состав участников — PLP Architecture, Da Costa Mahindroo, ONE Landscape, Modum — работает как самостоятельный аргумент в делюкс-сегменте.',
        'Ключевые риски: узкая экспозиция (52 лота), сильная концентрация конкурентов в радиусе километра и высокий порог входа — 135 млн ₽ за самый доступный лот.',
      ]),

      spacer(200),
      rule(140),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 70, line: 230, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      spacer(120),
      note('Справка подготовлена 28.07.2026 на основе открытых данных и выгрузок Циан по PHANTOM и четырём проектам локации. Ценовые оценки носят справочный характер и не являются офертой.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'ЖК_ФАНТОМ_информационная_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
