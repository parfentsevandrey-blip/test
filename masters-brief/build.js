const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const T = JSON.parse(fs.readFileSync(path.join(__dirname, 'tables.json'), 'utf8'));
const C = JSON.parse(fs.readFileSync(path.join(__dirname, 'cmp_tables.json'), 'utf8'));
const PR = JSON.parse(fs.readFileSync(path.join(__dirname, 'prim_tables.json'), 'utf8'));
const EX = JSON.parse(fs.readFileSync(path.join(__dirname, 'ex_links.json'), 'utf8'));
const PL = JSON.parse(fs.readFileSync(path.join(__dirname, 'plans.json'), 'utf8'));
const RC = JSON.parse(fs.readFileSync(path.join(__dirname, 'ren_cards.json'), 'utf8'));
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
        margins: { top: 150, bottom: 150, left: 170, right: 110 },
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
  ['IV кв. 2029', 'Срок сдачи'],
  ['8–25', 'Этажей'],
  ['672', 'Квартиры в проекте'],
  ['от 26,3 млн ₽', 'Цена входа'],
];

const FACTS = [
  ['Застройщик', 'Capital Group'],
  ['Класс', 'Премиум'],
  ['Адрес', 'ул. Викторенко, 16'],
  ['Метро', '«Аэропорт», ≈10 мин пешком'],
  ['Секции', '11 разновысотных'],
  ['Площади', 'от 28 до 161 м²'],
  ['Потолки', 'от 3,1 м'],
  ['Отделка', 'Без отделки'],
  ['Конструктив', 'Монолит'],
  ['Участок', '2,62 га'],
  ['Паркинг', '2 уровня, 363 м/м'],
  ['Архитектура', 'GAFA · лобби DBA Group'],
  ['Схема продаж', 'ДДУ, эскроу (214-ФЗ)'],
  ['В экспозиции', `${T.meta.lots} лотов · ${T.meta.volume} млрд ₽`],
];

const COMP_HEAD = ['№  Проект', 'Адрес', 'Застройщик', 'Класс', 'Ввод', 'Расстояние'];
const PIPE_HEAD = ['Проект', 'Адрес', 'Застройщик', 'Класс', 'Ввод', 'Расстояние'];
const COMP_W = [2260, 2180, 1780, 1000, 1250, 1168];
const COMP_ROWS = [
  ['1   Прайм Парк', 'Ленинградский пр-т, 37', 'Optima Development', 'премиум', '2021–2024*', '1,3 км'],
  ['2   Триумф Палас', 'Чапаевский пер., 3', 'Дон-Строй', 'элит', '2006', '0,4 км'],
  ['3   Лайнер', 'Ходынский б-р, 2', 'ИНТЕКО', 'бизнес', '2017–2019', '1,2 км'],
  ['4   Лица', 'ул. Авиаконструктора Сухого, 2к2', 'Capital Group', 'бизнес', '2021', '1,6 км'],
  ['5   Царская площадь', 'Ленинградский пр-т, 31', 'MR Group / Coalco', 'бизнес', '2018–2020', '2,7 км'],
  ['6   Династия', 'Хорошёвское ш., 25А', 'Sezar Group', 'бизнес', '2019–2023', '2,1 км'],
];

const PIPE_W = [2260, 2180, 1780, 1000, 1250, 1168];
const PIPE_ROWS = [
  ['Муза', 'Красноармейская ул., 11', 'Мангазея', 'премиум', 'I–II кв. 2029', '1,9 км'],
  ['Дом на Часовой', 'Часовая ул.', 'Dar', 'премиум', 'II кв. 2028', '1,6 км'],
];

const SRC = [
  ['Циан — выгрузка по ЖК «Премиальный дом МАСТЕРС» (ID 5747793), 169 лотов, 28.07.2026', ''],
  ['Циан — выгрузки по вторичному рынку на 28.07.2026: «Прайм Парк» (ID 45774) 341 лот, «Царская площадь» (ID 8122) 65, «Лайнер» (ID 5641) 62, «Династия» (ID 19800) 39, «ЛИЦА» (ID 5430) 13', ''],
  ['Циан — выгрузки по первичному рынку на 28.07.2026: «Муза» (ID 5750446) 52 лота, «Дом на Часовой» (ID 5732034) 51 лот', ''],
  ['Яндекс Недвижимость — стоимость ремонта квартиры, 2026', 'https://realty.yandex.ru/journal/post/skolko-stoit-remont-kvartiry/'],
  ['Официальный сайт проекта Capital Group', 'https://cg-projects.ru/projects/masters'],
  ['Новострой-М — карточка ЖК на ул. Викторенко', 'https://www.novostroy-m.ru/baza/jk_na_ul_viktorenko'],
  ['vNovostroike.ru — ЖК МАСТЕРС', 'https://msk.vnovostroike.ru/novostroyki/masters/'],
  ['Яндекс Карты — картографическая основа', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'ЖК «МАСТЕРС» — информационная справка',
  description: 'Премиальный дом MASTERS, Capital Group, Москва, ул. Викторенко, 16',
  styles: {
    default: {
      document: { run: { font: S.SANS, size: 19, color: '2A2E38' }, paragraph: { spacing: { line: 264, lineRule: LR } } },
    },
  },
  sections: [{
    properties: {
      page: {
        margin: {
          top: convertMillimetersToTwip(15), bottom: convertMillimetersToTwip(15),
          left: convertMillimetersToTwip(20), right: convertMillimetersToTwip(20),
          header: convertMillimetersToTwip(9), footer: convertMillimetersToTwip(9),
        },
      },
    },
    footers: {
      default: new Footer({
        children: [p({
          children: [
            txt('ЖК «МАСТЕРС» · Capital Group · информационная справка от 28.07.2026', { size: 14, color: MUTED }),
            txt('\t', {}),
            new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
          ],
          tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
          border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
        })],
      }),
    },
    children: [
      // ─────────────── TITLE PAGE ───────────────
      image('hero.jpg', PX, 228, { after: 150 }),
      kicker('Информационная справка · Москва · 28 июля 2026'),
      p({
        children: [txt('ЖК «МАСТЕРС»', { font: S.GEO, size: 48, bold: true, color: INK })],
        spacing: { after: 40 },
      }),
      p({
        children: [txt('Премиальный дом MASTERS · Capital Group', { font: S.GEO, size: 24, color: BRONZE })],
        spacing: { after: 60 },
      }),
      p({
        children: [txt('Москва, САО, Хорошёвский район, улица Викторенко, 16  ·  м. «Аэропорт»', { size: 18, color: MUTED })],
        spacing: { after: 150 },
      }),
      rule(130),
      kicker('Основная информация', INK),
      statTiles(TILES),
      spacer(70),
      factSheet(FACTS),
      spacer(110),
      kicker('Расположение', INK),
      image('map_loc.jpg', PX, 276, { after: 55 }),
      note('Камерный квартал между Чапаевским парком, Ходынским полем и Ленинградским проспектом. Картографическая основа: Яндекс Карты.'),

      // ─────────────── RENDERS ───────────────
      h1('Архитектура проекта', { br: true }),
      body('Ансамбль из 11 разновысотных секций высотой от 8 до 25 этажей, спроектированный бюро GAFA. Пластика фасадов построена на вертикальном ритме бронзовых ламелей и панорамном остеклении, занимающем около 80 % площади фасада; переменная этажность формирует силуэт, раскрывающийся на Ходынское поле и Чапаевский парк.', { after: 220 }),
      image('g1.jpg', PX, 362),
      caption('Панорама ансамбля со стороны линейного парка. Визуализация застройщика.'),
      image('g2.jpg', PX, 362),
      caption('Фасадные решения: вертикальные бронзовые ламели, скруглённые объёмы, панорамное остекление.'),

      h1('Двор и инфраструктура', { br: true }),
      body('Приватный благоустроенный двор без машин с более чем 600 деревьями и кустарниками, а также линейный парк с амфитеатром, лужайкой для йоги и спортивными зонами. В составе дома — гранд-лобби площадью 160 м² с шестиметровыми потолками и скульптурной лестницей (интерьеры DBA Group), фитнес-студия, коворкинг, детский клуб и премиальная торговая галерея на первых этажах.', { after: 220 }),
      image('g4.jpg', PX, 362),
      caption('Приватный двор и входная группа с озеленением.'),
      spacer(60),
      imagePair('g5.jpg', 'g6.jpg',
        'Гранд-лобби со скульптурной лестницей.',
        'Фитнес-студия для резидентов дома.'),
      spacer(200),
      note('Все изображения — визуализации застройщика (Capital Group). Итоговый облик объекта может отличаться от представленного.'),

      // ─────────────── FLOOR PLANS ───────────────
      h1('Планировочные варианты', { br: true }),
      body('В проекте более 100 планировочных решений при площадях от 28 до 161 м². Ниже — три типовых варианта, по одному на каждую основную комнатность, с параметрами конкретных лотов из текущей экспозиции.', { after: 200 }),
      planBlock(PL.plans[0], 286, 340),
      spacer(180),
      planBlock(PL.plans[1], 288, 240),

      h2('Трёхкомнатные', { br: true }),
      planBlock(PL.plans[2], 204, 380),
      spacer(200),
      h2('Экспозиция по типам'),
      dataTable(
        ['Тип квартиры', 'Площадь\nна плане, м²', 'Этаж', 'Секция', 'Цена лота,\nмлн ₽', 'Цена за\nм², ₽', 'Лотов\nв продаже', 'Площади в типе\nот — до, м²'],
        PL.ptab,
        [1620, 1220, 780, 1180, 1120, 1080, 1080, 1558],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Планировки — с официального сайта проекта Capital Group. Параметры лотов и цены — выгрузка Циан от 28.07.2026. Планировочные решения могут отличаться в зависимости от секции и этажа.'),

      // ─────────────── PRICES ───────────────
      h1('Цены и структура предложения', { br: true }),
      body([
        txt('По состоянию на '), txt('28 июля 2026 г.', { bold: true }),
        txt(' в экспозиции Циан находится '), txt(`${T.meta.lots} лотов`, { bold: true }),
        txt(` совокупной площадью ${T.meta.area} м² и общим объёмом предложения `),
        txt(`${T.meta.volume} млрд ₽`, { bold: true }),
        txt('. Все лоты продаёт напрямую застройщик, предложений от собственников и агентств нет. 100 % квартир — без отделки.'),
      ], { after: 200 }),

      h2('Цены по типам квартир'),
      dataTable(
        ['Тип квартиры', 'Лотов', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nлота, млн ₽', 'Цена за м²\nот — до, ₽', 'Средняя цена\nза м², ₽'],
        T.types,
        [1760, 700, 1400, 1520, 1200, 1930, 1128],
        { totalLast: true, boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена за м² — средневзвешенная по площади. Простое среднее по лотам (методика сводки Циан) — ' + T.meta.simple + ' ₽/м².'),

      h2('Цены по секциям'),
      dataTable(
        ['Секция', 'Этажей\nв секции', 'Лотов\nв продаже', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽', 'Отклонение от\nсредней по ЖК'],
        T.sections,
        [1300, 1080, 980, 1500, 1600, 1500, 1678],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Самая дорогая по удельной цене — секция 3 (23 этажа, +5 % к среднему по ЖК); наиболее доступная — секция 1 (14 этажей, −6 %). Все секции монолитные.'),

      h2('Цены по этажам'),
      dataTable(
        ['Диапазон этажей', 'Лотов в продаже', 'Средняя цена за м², ₽', 'Отклонение от средней по ЖК'],
        T.floors,
        [2400, 1400, 2900, 2938],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Разница между нижним (2–5 эт.) и верхним (16–23 эт.) поясом — около 25 % цены за метр. Отдельные видовые лоты верхних этажей выходят за 1,0–1,2 млн ₽/м².'),

      h2('Ключевые наблюдения'),
      ...bullets([
        [txt('Средневзвешенная цена по проекту — '), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt('; диапазон удельных цен — от 582 120 до 1 199 610 ₽/м².')],
        'Ядро предложения — 2-комнатные квартиры (74 лота, 44 % экспозиции) со средней площадью 85 м² и ценой 54,9–79,7 млн ₽.',
        'Цена за метр снижается с ростом площади: студии — 897 тыс. ₽/м², 3-комнатные — 726 тыс. ₽/м². Крупные форматы выгоднее в пересчёте на метр.',
        'Порог входа в проект — 26,3 млн ₽ (студия 30,2 м²), максимум экспозиции — 121,6 млн ₽ (3-комнатная 114,9 м² на верхнем этаже секции 3).',
      ]),
      spacer(30),
      note('В описаниях лотов застройщик заявляет скидку 10 % в июле 2026 г.; фактические цены сделок могут отличаться от цен экспозиции.'),

      // ─────────────── COMPETITORS ───────────────
      h1('Конкурентное окружение', { br: true }),
      body('Локация «Аэропорт — Ходынка» насыщена современными жилыми проектами бизнес- и премиум-класса, введёнными в эксплуатацию за последние 10 лет. Они формируют ценовой и качественный ориентир для МАСТЕРС и одновременно являются источником альтернативного предложения на вторичном рынке.', { after: 200 }),

      h2('Реализованные проекты локации'),
      dataTable(COMP_HEAD, COMP_ROWS, COMP_W, { boldFirstCol: true }),
      spacer(60),
      note('* «Прайм Парк» — многофазный проект: основные башни введены в 2021–2024 гг., финальные корпуса заявлены к вводу в 2026 г. Расстояния — по прямой от ул. Викторенко, 16.'),

      image('map_comp.jpg', PX, 478, { after: 60 }),
      caption('Реализованные современные жилые комплексы в радиусе ≈2,5 км от ЖК «МАСТЕРС». Источник картографической основы: Яндекс Карты.'),

      // ─────────────── PRICE BENCHMARK ───────────────
      h1('Ценовое сравнение с конкурентами', { br: true }),
      body('Сравнение построено на выгрузках Циан по пяти реализованным проектам локации на ту же дату — 28.07.2026, суммарно 520 лотов. Чтобы сопоставление было корректным, из каждой выгрузки взята одна и та же база: квартиры площадью 40–140 м² (диапазон экспозиции МАСТЕРС), без лотов свободной планировки.', { after: 130 }),
      body([
        txt('Ключевая оговорка: цены «в лоб» несопоставимы. ', { bold: true }),
        txt('МАСТЕРС — это 100 % первичного рынка, без отделки, со сроком ввода в 2029 г. Конкуренты — готовое жильё, где среди лотов с известным уровнем отделки 70–95 % идут с ремонтом. Поэтому сначала показана экспозиция как есть, а затем все проекты приведены к одному состоянию — квартире, в которую можно заехать.'),
      ], { after: 190 }),

      h2('Экспозиция конкурентов'),
      dataTable(
        ['ЖК', 'Тип рынка', 'Лотов\nв продаже', 'Средняя цена\nза м², ₽', 'Цена за м²\nот — до, ₽', 'Средняя\nплощадь, м²', 'Средняя цена\nлота, млн ₽'],
        C.t1,
        [1540, 1420, 900, 1360, 2060, 1120, 1238],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена за м² здесь и далее — средневзвешенная по площади: сумма цен всех лотов, делённая на их суммарную площадь. Все проекты, кроме МАСТЕРС и части экспозиции «Прайм Парка», представлены вторичным рынком — предложениями собственников и агентств.'),

      h2('Уровень отделки и премия за ремонт'),
      dataTable(
        ['ЖК', 'Отделка\nизвестна, лотов', 'Без отделки\nи черновая, ₽/м²', 'White box\nи чистовая, ₽/м²', 'С готовым\nремонтом, ₽/м²', 'Премия\nза ремонт'],
        C.t2,
        [1420, 1180, 2020, 1940, 1880, 1198],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Премия — отношение средней цены лотов с ремонтом к цене лотов без отделки внутри того же ЖК. Медиана по локации +21 %, разброс +5…+47 % (в деньгах 24–194 тыс. ₽/м²). Малые выборки «без отделки» (1–9 лотов) снижают точность отдельных оценок. По «Прайм Парку» в графу white box отнесены 17 лотов с прямым указанием отделки и 136 лотов застройщика: Optima Development реализует квартиры с предчистовой (white box) или чистовой отделкой.'),

      h2('Сколько стоит квартира, готовая к заселению', { br: true }),
      body('Чтобы сравнить честно, приведём все проекты к одному состоянию — квартире, в которую можно заехать. У конкурентов для этого берём лоты, которые уже продаются с ремонтом. К цене МАСТЕРС добавляем отделку, которой в ней нет: по рынку это 120 тыс. ₽/м².', { after: 170 }),
      image('chart.jpg', PX, 303, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 28.07.2026, сопоставимая выборка 40–140 м².'),
      note('Если отделка обойдётся в 80 тыс. ₽/м², метр в МАСТЕРС выйдет в 839 654 ₽; при 160 тыс. — в 919 654 ₽. Порядок проектов от этого почти не меняется: дороже МАСТЕРС в любом сценарии остаётся только «Прайм Парк».'),

      h2('Выводы'),
      ...bullets([
        'Сравнивать цены напрямую нельзя: МАСТЕРС продаётся без отделки и со сроком 2029 года, а у конкурентов среди лотов с известной отделкой 70–95 % уже с готовым ремонтом.',
        [txt('Если привести всё к квартире, в которую можно заехать, метр в МАСТЕРС стоит '), txt('879 654 ₽', { bold: true }), txt(' — цена застройщика плюс отделка. Дороже только «Прайм Парк» (1 045 515 ₽). «Династия» — 867 222 ₽, «Царская площадь» — 709 453 ₽, «Лица» — 604 614 ₽, «Лайнер» — 495 143 ₽.')],
        'Готовая квартира в МАСТЕРС обойдётся дороже, чем готовая квартира сегодня в любом проекте локации, кроме «Прайм Парка», — и это при заселении на три с половиной года позже.',
        [txt('Заявленная скидка 10 % снижает полную цену метра до '), txt('803 689 ₽', { bold: true }), txt('. Это уводит МАСТЕРС ниже «Династии», но всё ещё оставляет дороже «Царской площади», «Лиц» и «Лайнера».')],
        'Наблюдаемая премия за ремонт в локации — от +5 % до +47 %, медиана +21 %. Именно она объясняет, почему цены конкурентов «как есть» выглядят выше, чем они есть по сути.',
        '«Лайнер» и «Лица» — бизнес-класс, прямыми конкурентами по продукту не являются: они задают нижнюю границу рынка.',
      ]),
      spacer(40),
      note('Ограничения: выборки «Лиц» (12 лотов) и «Династии» (38) малы — оценки по ним индикативны. Расчёт ведётся по лотам с известным уровнем отделки.'),

      h2('Какой ремонт продаётся на вторичном рынке', { br: true }),
      body('Цифры выше показывают, сколько стоит метр готовой квартиры. Ниже — как эта готовность выглядит: реальные лоты с ремонтом, которые выставлены в соседних комплексах прямо сейчас.', { after: 190 }),
      photoCards(RC, 300, 225),
      note('Фотографии и параметры лотов — Яндекс Недвижимость, объявления актуальны на 28.07.2026. Уровень отделки указан продавцом. Показан один характерный лот на комплекс; это не самое дешёвое и не самое дорогое предложение в каждом ЖК.'),

      // ─────────────── PRIMARY MARKET ───────────────
      h1('Ценовое сравнение с первичным рынком', { br: true }),
      body('В радиусе полутора километров от МАСТЕРС строятся ещё два премиальных дома — «Муза» (Мангазея) и «Дом на Часовой» (Dar). Это прямые конкуренты: тот же класс, те же сроки, тот же покупатель.', { after: 130 }),
      body('Сравнение здесь чистое, без поправок. Все три проекта продаются только застройщиком, и во всех трёх 100 % лотов идут без отделки — сопоставлять можно напрямую.', { after: 180 }),
      image('map_prim.jpg', PX, 331, { after: 60 }),
      caption('Расположение трёх премиальных новостроек и средняя цена в каждой. Картографическая основа: Яндекс Карты.'),

      h2('Экспозиция трёх проектов'),
      dataTable(
        ['ЖК', 'Застройщик', 'Ввод\nв эксплуатацию', 'Лотов\nв продаже', 'Площадь\nот — до, м²', 'Цена лота\nот — до, млн ₽', 'Средняя цена\nза м², ₽'],
        PR.main,
        [1560, 1400, 1440, 900, 1400, 1580, 1358],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('«Дом на Часовой» — три корпуса высотой 14–24 этажа, архитектура бюро СПИЧ. «Муза» — два корпуса на 309 квартир, потолки 3,5–4,65 м. Расстояние от МАСТЕРС: до «Музы» 1,5 км, до «Дома на Часовой» 1,2 км.'),

      h2('Цена по типам квартир', { br: true }),
      image('chart_prim.jpg', PX, 257, { after: 60 }),
      spacer(40),
      dataTable(['Тип квартиры', ...PR.cols.map((n) => `${n}\nсредняя цена за м², ₽`)], PR.byroom,
                [2200, 2480, 2480, 2478], { boldFirstCol: true }),
      spacer(30),
      note('В скобках — число лотов в продаже. Студий в «Музе» нет. Порядок цен сохраняется во всех типологиях без исключения.'),

      h2('Выводы по первичному рынку'),
      ...bullets([
        [txt('МАСТЕРС — середина премиального сегмента локации: '), txt('на 27 % дешевле «Музы»', { bold: true }), txt(' (761 469 против 1 042 477 ₽/м²) и на 14 % дороже «Дома на Часовой» (668 643 ₽/м²).')],
        'Порядок цен одинаков во всех типологиях — от студий до трёхкомнатных. Это не эффект структуры предложения, а устойчивая разница в позиционировании трёх проектов.',
        [txt('Со скидкой 10 % МАСТЕРС практически сравнивается с «Домом на Часовой» (683 689 против 668 643 ₽/м², разница '), txt('всего 2 %', { bold: true }), txt('), а отставание от «Музы» доходит до 34 %.')],
        '«Муза» дороже, но это камерный проект на 309 квартир с потолками 3,5–4,65 м и террасами — другой продукт. МАСТЕРС берёт масштабом, инфраструктурой и выходом на Ходынское поле.',
        '«Дом на Часовой» дешевле и сдаётся на полтора года раньше (II кв. 2028), но это меньший проект с квартирами до 94 м² — верхние форматы МАСТЕРС там просто не с чем сравнивать.',
        'Бюджет входа сильно различается: 21,8 млн ₽ в «Доме на Часовой», 26,3 млн ₽ в МАСТЕРС и 43,7 млн ₽ в «Музе». По нижней границе МАСТЕРС ближе к «Дому на Часовой», по верхней (121,6 млн ₽) — к «Музе».',
      ]),

      // ─────────────── WHAT THE SAME MONEY BUYS TODAY ───────────────
      h1('Что можно купить за те же деньги сегодня', { br: true }),
      body('Самый практичный способ проверить цену — взять конкретный лот МАСТЕРС и посмотреть, что за те же деньги продаётся в локации прямо сейчас. Возьмём типичную двухкомнатную квартиру: 80,9 м² на 17 этаже, 61,6 млн ₽, без отделки, ключи в конце 2029 года.', { after: 180 }),
      dataTable(
        ['ЖК', 'Квартира', 'Отделка', 'Готовность', 'Цена лота,\nмлн ₽', 'Цена за\nм², ₽', 'Ссылка'],
        PR.ex.map((r, i) => [...r, {
          text: 'Циан →',
          link: EX.ex[['МАСТЕРС', 'Прайм Парк', 'Царская площадь', 'Династия', 'Лица', 'Лайнер'][i]].url,
        }]),
        [1660, 1620, 1420, 1320, 1120, 1150, 1348],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Реальные лоты из выгрузок Циан на 28.07.2026, площадь 72–82 м². Для каждого конкурента показан самый доступный лот с ремонтом в этом диапазоне площадей.'),

      h2('Как это читать'),
      body('За 61,6 млн ₽ сегодня в «Прайм Парке» продаётся квартира 81,7 м² на 39 этаже — с дизайнерским ремонтом, в доме 2023 года, с ключами на руках. Тот же класс, та же площадь, те же деньги. Разница в том, что в одном случае вы въезжаете сейчас, а в другом ждёте до конца 2029 года и потом ещё делаете ремонт.', { after: 130 }),
      body('Если готовы отойти от премиум-класса к бизнесу, разрыв становится ещё заметнее. В «Лайнере» аналогичная по площади квартира с дизайнерским ремонтом стоит 35,7 млн ₽ — на 26 миллионов меньше. В «Лицах» — 39,9 млн ₽, в «Династии» — 42,0 млн ₽, в «Царской площади» — 44,9 млн ₽. Во всех случаях это готовое жильё, куда можно заехать с чемоданом.', { after: 130 }),
      body('Обратная сторона тоже есть. Верхняя часть премиального рынка локации всё-таки дороже: в «Прайм Парке» 23 готовых лота площадью 70–100 м² стоят от 69,5 млн ₽ (962 604 ₽/м²), в «Династии» — от 67,0 млн ₽. То есть МАСТЕРС с учётом будущей отделки (71,3 млн ₽ за тот же метраж) попадает ровно в нижнюю границу этого диапазона, а не ниже него.', { after: 130 }),
      body([
        txt('Вывод простой. ', { bold: true }),
        txt('МАСТЕРС не является выгодной покупкой «здесь и сейчас» — за те же деньги в локации доступно готовое жильё с ремонтом, в том числе премиального класса. Проект имеет смысл при одном из двух условий: покупатель рассчитывает на рост цены к вводу в 2029 году, либо ему принципиально важны именно новый дом, конкретная локация у Чапаевского парка и возможность сделать отделку под себя с нуля. Скидка 10 % заметно улучшает картину, но не меняет её сути.'),
      ], { after: 180 }),

      h2('Позиционирование МАСТЕРС'),
      ...bullets([
        [txt('По цене экспозиции ('), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt(') проект занимает третью позицию среди шести реализованных проектов локации, а с поправкой на отделку — вторую, уступая только «Прайм Парку». Детальный расчёт — в разделе «Ценовое сравнение с конкурентами».')],
        'Единственный сопоставимый по классу реализованный проект локации — «Прайм Парк». Остальные соседи относятся к бизнес-классу и задают нижнюю границу рынка, а не конкурируют за того же покупателя.',
        'На первичном рынке прямыми конкурентами выступают «Муза» (Мангазея, 1 042 477 ₽/м²) и «Дом на Часовой» (Dar, 668 643 ₽/м²). МАСТЕРС расположен между ними: на 27 % дешевле «Музы» и на 14 % дороже «Дома на Часовой».',
        'Ключевые преимущества локации МАСТЕРС: непосредственное соседство с Чапаевским парком и парком Героев Первой мировой войны, выход на Ходынское поле, 10 минут пешком до метро «Аэропорт», 7 минут до съезда на ТТК.',
        'Ключевые риски: ранняя стадия строительства (готовность около 1 %) при сроке ввода IV кв. 2029 г. и отсутствие отделки во всех лотах экспозиции — метр к моменту заселения обходится в 880–920 тыс. ₽ против 760 тыс. ₽ по прайсу.',
      ]),

      spacer(200),
      rule(140),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 70, line: 230, lineRule: LR }, indent: { left: 170, hanging: 170 },
      })),
      spacer(120),
      note('Справка подготовлена 28.07.2026 на основе открытых данных и выгрузки Циан. Ценовые ориентиры по конкурентам приведены по данным открытых источников на дату подготовки и носят справочный характер; они не являются офертой и не заменяют проверку по первичным документам застройщиков.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'ЖК_МАСТЕРС_информационная_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
