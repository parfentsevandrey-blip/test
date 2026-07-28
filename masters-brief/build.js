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
const IMG = (n) => fs.readFileSync(path.join(__dirname, 'out', n));

// ── palette ────────────────────────────────────────────────────────────────
const INK = '1F2A44', BRONZE = 'A9762F', MUTED = '70788A',
      LINE = 'D9D4CB', SOFT = 'F5F2ED', HEAD = '1F2A44', RED = 'B3282D';
const CONTENT_W = 9638;            // A4 minus 20 mm side margins, in DXA
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
  spacing: { after: o.after === undefined ? 120 : o.after, line: o.line || 264, lineRule: LR },
  alignment: o.align, indent: o.indent,
});

const kicker = (text, color = BRONZE) => p({
  children: [txt(text, { size: 16, bold: true, color, spacing: 60, caps: true })],
  spacing: { after: 90 },
});

const h1 = (text) => p({
  children: [txt(text, { font: S.GEO, size: 36, bold: true, color: INK })],
  spacing: { after: 160 },
  border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: BRONZE, space: 8 } },
});

const h2 = (text) => p({
  children: [txt(text, { font: S.GEO, size: 24, bold: true, color: INK })],
  spacing: { before: 170, after: 110 },
});

const caption = (text) => p({
  children: [txt(text, { size: 16, color: MUTED, italics: true })],
  spacing: { after: 220 },
});

const note = (text) => p({
  children: [txt(text, { size: 15, color: MUTED })],
  spacing: { after: 60, line: 230, lineRule: LR },
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
      margins: { top: 70, bottom: 70, left: 100, right: 100 },
      verticalAlign: VerticalAlign.CENTER,
      borders: {
        top: top || hair(),
        bottom: hair(),
        left: noBorder, right: noBorder,
      },
      children: [p({
        children: [txt(text, { bold, color: color || '2A2E38', size: size || 17 })],
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

// two-column "label / value" fact sheet (4 columns = 2 pairs per row)
function factSheet(pairs) {
  const W = [1950, 2869, 1950, 2869];
  const rows = [];
  for (let i = 0; i < pairs.length; i += 2) {
    const cells = [];
    [pairs[i], pairs[i + 1] || ['', '']].forEach(([k, v], j) => {
      cells.push(new TableCell({
        width: { size: W[j * 2], type: WidthType.DXA },
        margins: { top: 42, bottom: 42, left: j === 0 ? 0 : 150, right: 60 },
        verticalAlign: VerticalAlign.TOP,
        borders: { top: noBorder, bottom: hair('EAE6DE'), left: noBorder, right: noBorder },
        children: [p({ children: [txt(k, { size: 14, color: MUTED, caps: true, spacing: 16 })], spacing: { after: 0, line: 230, lineRule: LR } })],
      }));
      cells.push(new TableCell({
        width: { size: W[j * 2 + 1], type: WidthType.DXA },
        margins: { top: 42, bottom: 42, left: 60, right: 60 },
        verticalAlign: VerticalAlign.TOP,
        borders: { top: noBorder, bottom: hair('EAE6DE'), left: noBorder, right: noBorder },
        children: [p({ children: [txt(v, { size: 17, bold: true, color: INK })], spacing: { after: 0, line: 230, lineRule: LR } })],
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

const bullets = (items) => items.map((t) => p({
  children: [txt('—   ', { color: BRONZE, bold: true }), ...(Array.isArray(t) ? t : [txt(t)])],
  spacing: { after: 70, line: 258, lineRule: LR }, indent: { left: 170, hanging: 170 },
}));

// ── content ────────────────────────────────────────────────────────────────
const FACTS = [
  ['Застройщик', 'Capital Group'],
  ['Класс', 'Премиум'],
  ['Адрес', 'ул. Викторенко, 16'],
  ['Метро', '«Аэропорт», ≈10 мин пешком'],
  ['Срок сдачи', 'IV квартал 2029 г.'],
  ['Стадия', 'Начальная, готовность ≈1 %'],
  ['Секции', '11 разновысотных секций'],
  ['Этажность', 'от 8 до 25 этажей'],
  ['Квартир', '672 · более 100 планировок'],
  ['Площади', 'от 28 до 161 м²'],
  ['Потолки', 'от 3,1 м'],
  ['Отделка', 'Без отделки'],
  ['Конструктив', 'Монолит'],
  ['Участок', '2,62 га'],
  ['Паркинг', 'Подземный, 2 уровня, 363 м/м'],
  ['Архитектура', 'GAFA · лобби DBA Group'],
  ['Схема продаж', 'ДДУ, эскроу-счета (214-ФЗ)'],
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
  ['Циан — выгрузки по конкурентам на 28.07.2026: «Прайм Парк» (ID 45774) 341 лот, «Царская площадь» (ID 8122) 65, «Лайнер» (ID 5641) 62, «Династия» (ID 19800) 39, «ЛИЦА» (ID 5430) 13', ''],
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
      image('hero.jpg', PX, 245, { after: 170 }),
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
      factSheet(FACTS),
      spacer(150),
      kicker('Расположение', INK),
      image('map_loc.jpg', PX, 294, { after: 60 }),
      note('Камерный квартал между Чапаевским парком, Ходынским полем и Ленинградским проспектом. Картографическая основа: Яндекс Карты.'),

      // ─────────────── RENDERS ───────────────
      p({ children: [new PageBreak()] }),
      h1('Архитектура проекта'),
      body('Ансамбль из 11 разновысотных секций высотой от 8 до 25 этажей, спроектированный бюро GAFA. Пластика фасадов построена на вертикальном ритме бронзовых ламелей и панорамном остеклении, занимающем около 80 % площади фасада; переменная этажность формирует силуэт, раскрывающийся на Ходынское поле и Чапаевский парк.', { after: 220 }),
      image('g1.jpg', PX, 362),
      caption('Панорама ансамбля со стороны линейного парка. Визуализация застройщика.'),
      image('g2.jpg', PX, 362),
      caption('Фасадные решения: вертикальные бронзовые ламели, скруглённые объёмы, панорамное остекление.'),

      p({ children: [new PageBreak()] }),
      h1('Двор и инфраструктура'),
      body('Приватный благоустроенный двор без машин с более чем 600 деревьями и кустарниками, а также линейный парк с амфитеатром, лужайкой для йоги и спортивными зонами. В составе дома — гранд-лобби площадью 160 м² с шестиметровыми потолками и скульптурной лестницей (интерьеры DBA Group), фитнес-студия, коворкинг, детский клуб и премиальная торговая галерея на первых этажах.', { after: 220 }),
      image('g4.jpg', PX, 362),
      caption('Приватный двор и входная группа с озеленением.'),
      spacer(60),
      imagePair('g5.jpg', 'g6.jpg',
        'Гранд-лобби со скульптурной лестницей.',
        'Фитнес-студия для резидентов дома.'),
      spacer(200),
      note('Все изображения — визуализации застройщика (Capital Group). Итоговый облик объекта может отличаться от представленного.'),

      // ─────────────── PRICES ───────────────
      p({ children: [new PageBreak()] }),
      h1('Цены и структура предложения'),
      body([
        txt('По состоянию на '), txt('28 июля 2026 г.', { bold: true }),
        txt(' в экспозиции Циан находится '), txt(`${T.meta.lots} лотов`, { bold: true }),
        txt(` совокупной площадью ${T.meta.area} м² и общим объёмом предложения `),
        txt(`${T.meta.volume} млрд ₽`, { bold: true }),
        txt('. Все лоты реализуются напрямую застройщиком (Capital Group), предложений от частных собственников и агентств нет. 100 % квартир предлагается без отделки.'),
      ], { after: 200 }),

      h2('Цены по типам квартир'),
      dataTable(
        ['Тип', 'Лотов', 'Площадь, м²', 'Цена, млн ₽', 'Ср. цена,\nмлн ₽', 'Цена за м², ₽', 'Ср. за м², ₽'],
        T.types,
        [1830, 780, 1420, 1500, 1080, 1930, 1098],
        { totalLast: true, boldFirstCol: true },
      ),
      spacer(30),
      note('Средняя цена за м² рассчитана как средневзвешенная по площади. Простое среднее по лотам (методика сводки Циан) — ' + T.meta.simple + ' ₽/м². Средняя цена лота по проекту — ' + T.meta.avg_price + ' млн ₽.'),

      h2('Цены по секциям'),
      dataTable(
        ['Секция', 'Этажей', 'Лотов', 'Площадь, м²', 'Цена, млн ₽', 'Ср. за м², ₽', 'Откл. от ЖК'],
        T.sections,
        [1500, 900, 800, 1620, 1720, 1600, 1498],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Самая дорогая по удельной цене — секция 3 (23 этажа, +5 % к среднему по ЖК); наиболее доступная — секция 1 (14 этажей, −6 %). Все секции монолитные.'),

      h2('Цены по этажам'),
      dataTable(
        ['Диапазон этажей', 'Лотов', 'Средняя цена за м², ₽', 'Отклонение от среднего по ЖК'],
        T.floors,
        [2400, 1400, 2900, 2938],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Премия за высоту в проекте выражена: разница между нижним (2–5 эт.) и верхним (16–23 эт.) поясом составляет около 25 % удельной цены. Отдельные видовые лоты верхних этажей выходят за 1,0–1,2 млн ₽/м².'),

      h2('Ключевые наблюдения'),
      ...bullets([
        [txt('Средневзвешенная цена по проекту — '), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt('; диапазон удельных цен — от 582 120 до 1 199 610 ₽/м².')],
        'Ядро предложения — 2-комнатные квартиры (74 лота, 44 % экспозиции) со средней площадью 85 м² и ценой 54,9–79,7 млн ₽.',
        'Удельная цена снижается с ростом площади: студии — 897 тыс. ₽/м², 3-комнатные — 726 тыс. ₽/м². Это типично для премиального сегмента и делает крупные форматы относительно более выгодными в пересчёте на метр.',
        'Порог входа в проект — 26,3 млн ₽ (студия 30,2 м²), максимум экспозиции — 121,6 млн ₽ (3-комнатная 114,9 м² на верхнем этаже секции 3).',
        'В описаниях лотов застройщик заявляет скидку 10 % в июле 2026 г.; фактические цены сделок могут отличаться от цен экспозиции.',
      ]),

      // ─────────────── COMPETITORS ───────────────
      p({ children: [new PageBreak()] }),
      h1('Конкурентное окружение'),
      body('Локация «Аэропорт — Ходынка» насыщена современными жилыми проектами бизнес- и премиум-класса, введёнными в эксплуатацию за последние 10 лет. Они формируют ценовой и качественный ориентир для МАСТЕРС и одновременно являются источником альтернативного предложения на вторичном рынке.', { after: 200 }),

      h2('Реализованные проекты локации'),
      dataTable(COMP_HEAD, COMP_ROWS, COMP_W, { boldFirstCol: true }),
      spacer(60),
      note('* «Прайм Парк» — многофазный проект: основные башни введены в 2021–2024 гг., финальные корпуса заявлены к вводу в 2026 г. Расстояния — по прямой от ул. Викторенко, 16.'),

      image('map_comp.jpg', PX, 478, { after: 60 }),
      caption('Реализованные современные жилые комплексы в радиусе ≈2,5 км от ЖК «МАСТЕРС». Источник картографической основы: Яндекс Карты.'),

      // ─────────────── PRICE BENCHMARK ───────────────
      p({ children: [new PageBreak()] }),
      h1('Ценовое сравнение с конкурентами'),
      body('Сравнение построено на выгрузках Циан по пяти реализованным проектам локации на ту же дату — 28.07.2026, суммарно 520 лотов. Чтобы сопоставление было корректным, из каждой выгрузки взята одна и та же база: квартиры площадью 40–140 м² (диапазон экспозиции МАСТЕРС), без лотов свободной планировки.', { after: 130 }),
      body([
        txt('Ключевая оговорка: цены «в лоб» несопоставимы. ', { bold: true }),
        txt('МАСТЕРС — это 100 % первичного рынка, без отделки, со сроком ввода в 2029 г. Конкуренты — готовое жильё, где среди лотов с определённым уровнем отделки 70–95 % идут с ремонтом. Поэтому ниже приведены два измерения: цена экспозиции как есть и цена, приведённая к общей базе «без отделки».'),
      ], { after: 190 }),

      h2('Экспозиция конкурентов'),
      dataTable(
        ['ЖК', 'Рынок', 'Лотов', 'Ø ₽/м²', 'Диапазон ₽/м²', 'Ср. S,\nм²', 'Ср. лот,\nмлн ₽'],
        C.t1,
        [1720, 1500, 720, 1180, 2200, 1050, 1268],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Все проекты, кроме МАСТЕРС и части экспозиции «Прайм Парка», представлены вторичным рынком — предложениями собственников и агентств.'),

      h2('Уровень отделки и премия за ремонт'),
      dataTable(
        ['ЖК', 'Отделка\nопред.', 'Без отделки / черновая', 'White box / чистовая', 'С ремонтом', 'Премия за\nремонт'],
        C.t2,
        [1620, 900, 2140, 2000, 1770, 1208],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Премия — отношение средней цены лотов с ремонтом к цене лотов без отделки внутри того же ЖК. Медиана по локации +21 %, разброс +5…+47 % (в деньгах 24–194 тыс. ₽/м²). Малые выборки «без отделки» (1–9 лотов) снижают точность отдельных оценок. По «Прайм Парку» в графу white box отнесены 17 лотов с прямым указанием отделки и 136 лотов застройщика: Optima Development реализует квартиры с предчистовой (white box) или чистовой отделкой.'),

      p({ children: [new PageBreak()] }),
      h2('Сравнение: как есть и с поправкой на отделку'),
      image('chart.jpg', PX, 322, { after: 60 }),
      caption('Расчёт по выгрузкам Циан от 28.07.2026, сопоставимая выборка 40–140 м².'),

      h2('Чувствительность к стоимости ремонта'),
      body('Приведённая цена, ₽/м², при трёх ставках вычета за ремонт: 80 тыс. (капитальный), 120 тыс. (базовый сценарий) и 160 тыс. ₽/м² (дизайнерский премиум-уровня).', { after: 140 }),
      dataTable(
        ['Ставка вычета', ...C.order],
        C.t3,
        [1760, 1330, 1200, 1200, 1610, 1000, 1538],
        { boldFirstCol: true },
      ),
      spacer(30),
      note('Ориентиры рынка: капитальный ремонт — 27–36 тыс. ₽/м², дизайнерский — от 50 тыс. ₽/м² (Яндекс Недвижимость, 2026); в премиальном сегменте бюджеты «под ключ» заметно выше. Позиция МАСТЕРС от ставки не зависит — все лоты и так без отделки.'),

      h2('Выводы'),
      ...bullets([
        [txt('По цене экспозиции МАСТЕРС — третий из шести: '), txt('−15 %', { bold: true }), txt(' к «Прайм Парку», −6 % к «Династии», +7 % к «Царской площади», +35 % к «Лицам» и +56 % к «Лайнеру».')],
        [txt('С поправкой на отделку проект поднимается на второе место и его премия к локации растёт: '), txt('−10 %', { bold: true }), txt(' к «Прайм Парку», '), txt('+2 %', { bold: true }), txt(' к «Династии», +28 % к «Царской площади», +60 % к «Лицам», +96 % к «Лайнеру». Иначе говоря, по «сырым» ценам МАСТЕРС выглядит дешевле локации, чем он есть: конкуренты продают метр вместе с ремонтом.')],
        'Единственный проект дороже МАСТЕРС в приведённых ценах — «Прайм Парк», и разрыв всего 10 %. При этом «Прайм Парк» уже построен, ключи выдаются сразу, а квартиры идут с white box. Эти 10 % не компенсируют покупателю ни 3,5 года ожидания, ни расходов на отделку.',
        [txt('Совокупный бюджет входа. '), txt('К 759 654 ₽/м² нужно добавить отделку: при 120–160 тыс. ₽/м² метр к моменту заселения обходится в '), txt('880–920 тыс. ₽/м²', { bold: true }), txt('. Это выше приведённой цены всех конкурентов без исключения и сопоставимо с ценой «Прайм Парка» как есть — но с заселением на 3,5 года позже.')],
        [txt('Заявленная скидка 10 % — ключевой рычаг. '), txt('Она опускает эффективную цену до '), txt('683 689 ₽/м²', { bold: true }), txt(', и в приведённых ценах картина меняется на −19 % к «Прайм Парку» и −8 % к «Династии». Без скидки проект продаётся с премией к локации, со скидкой — с дисконтом к двум сильнейшим конкурентам.')],
        '«Лайнер» и «Лица» — бизнес-класс и прямыми конкурентами по продукту не являются: они задают нижнюю границу локального рынка. Ближайшие сопоставимые ориентиры — «Прайм Парк» (премиум) и «Династия».',
      ]),
      spacer(40),
      note('Ограничения расчёта: выборки «Лиц» (12 лотов) и «Династии» (38 лотов) малы, оценки по ним индикативны. По «Прайм Парку» и «Царской площади» уровень отделки определён не для всех лотов — приведение считалось только по лотам с известной отделкой.'),

      p({ children: [new PageBreak()] }),
      h2('Прямые конкуренты на первичном рынке'),
      body('В шаговой и ближней доступности от станции «Аэропорт» строятся два проекта сопоставимого класса — они конкурируют с МАСТЕРС за одного и того же покупателя.', { after: 160 }),
      dataTable(PIPE_HEAD, PIPE_ROWS, PIPE_W, { boldFirstCol: true }),
      spacer(180),

      h2('Позиционирование МАСТЕРС'),
      ...bullets([
        [txt('По цене экспозиции ('), txt(`${T.meta.w} ₽/м²`, { bold: true }), txt(') проект занимает третью позицию среди шести реализованных проектов локации, а с поправкой на отделку — вторую, уступая только «Прайм Парку». Детальный расчёт — в разделе «Ценовое сравнение с конкурентами».')],
        'Единственный сопоставимый по классу реализованный проект локации — «Прайм Парк». Остальные соседи относятся к бизнес-классу и задают нижнюю границу рынка, а не конкурируют за того же покупателя.',
        'На первичном рынке прямыми конкурентами выступают «Муза» (Мангазея, ориентир около 562 тыс. ₽/м², старт продаж 2026 г.) и «Дом на Часовой» (Dar, ориентир около 678 тыс. ₽/м²). Оба проекта заметно дешевле МАСТЕРС в пересчёте на метр, но уступают по масштабу, составу инфраструктуры и удалённости от Ходынского поля.',
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
