const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'ax_tables.json'), 'utf8'));
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
  ['3', 'Площадки в центре'],
  ['21 400 м²', 'Квартир суммарно'],
  ['≈ 64 млрд ₽', 'Оценка выручки'],
  ['нет', 'Цен и прайсов', true],
];
const SRC = [
  ['Сайт застройщика и статусы проектов — aurix-development.ru, проверено 26.08.2026', 'https://aurix-development.ru/projects'],
  ['«Большая Ордынка, 25»: адрес, 17 300 м², концепция и визуализации — портфолио бюро «ЭталонПроект»', 'https://etalon-project.ru/projects/bolshaya-ordynka-25/'],
  ['«Малая Полянка, 3»: 7 941 м², концепция и визуализации — портфолио бюро «ЭталонПроект»', 'https://etalon-project.ru/projects/malaya-polyanka-3/'],
  ['«Малая Полянка, 3»: 34 квартиры, 36 машино-мест, 9 этажей — карточка проекта', 'https://polyanka-3.moscow/'],
  ['Класс, отделка white box и срок сдачи по «Ордынке» — карточка у брокера Whitewill', 'https://whitewill.ru/developments/bolshaya-ordynka-25'],
  ['«Земледельческий, 15»: 77 квартир, 8 000 м², участок 0,3 га, монолитно-кирпичный', 'https://www.kvartiravmoskve.ru/Objects/zhk-zemledelcheskiy-15/'],
  ['Сделка с АО «Бизнес-Недвижимость»: 14,1 млрд ₽, 42 объекта, 18 проектов, 185 млрд ₽ выручки до 2032 года — Группа «Эталон», 28.08.2025', 'https://www.etalongroup.com/'],
  ['Разрешение на строительство первого премиального дома AURIX в Москве, 11.12.2025 — РИА Недвижимость', 'https://realty.ria.ru/20251211/aurix-2061416114.html'],
  ['«Резиденция Омега»: 59 резиденций, 18,2 тыс. м² общей и 8,9 тыс. м² жилой площади — «Элитное.ру»', 'https://elitnoe.ru/complexes/rezidenciia-omega'],
  ['«Арбат 2»: 23 квартиры и 2 пентхауса, паркинг 66 мест, 42 кладовые, здание 1965 года — сайт проекта', 'https://arbat2.ru/'],
  ['«Русские сезоны»: площади, цены, сроки — «Новострой-М»', 'https://www.novostroy-m.ru/baza/jk_russkie_sezony'],
  ['Цены предложения по элитным районам Москвы, I квартал 2026 — NF Group', 'https://nfgroup.ru/analytics/'],
  ['Хроника согласований и ценовые ориентиры отрасли — Telegram-канал Property Insider, посты 31831, 33421, 34170, 35495 (ноябрь 2025 — август 2026)', 'https://t.me/propertyinsider/35495'],
  ['Координаты для карты — OpenStreetMap по адресу. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'AURIX — три площадки в центре Москвы',
  description: 'Информационная справка по трём площадкам AURIX (Группа «Эталон»): Большая Ордынка 25, Земледельческий 15, Малая Полянка 3',
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
        txt('AURIX · три площадки в центре Москвы · справка от 26.08.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      kicker('Информационная справка · Москва · 26 августа 2026'),
      p({ children: [txt('AURIX', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 40 } }),
      p({ children: [txt('Три площадки Группы «Эталон» в центре Москвы', { font: S.GEO, size: 24, color: BRONZE })],
          spacing: { after: 60 } }),
      p({ children: [txt('Большая Ордынка, 25  ·  Земледельческий переулок, 15  ·  Малая Полянка, 3', { size: 18, color: MUTED })],
          spacing: { after: 150 } }),
      rule(130),
      kicker('Основные параметры', INK),
      statTiles(TILES),
      spacer(50),
      factSheet(K.facts),
      spacer(40),
      note('Ни один из трёх проектов не продаётся. На сайте AURIX все три помечены статусом «Скоро» и собственных страниц не имеют: нет ни планировок, ни прайса, ни полного набора показателей. Поэтому справка собрана из того, что раскрыли застройщик и его архитектурное бюро, из документов, всплывших при согласованиях, и из рыночных ориентиров по районам. Всё расчётное помечено словом «оценка».'),
      spacer(40),
      kicker('Где находятся площадки', INK),
      image('map.jpg', PX, 391, { after: 46 }),
      note('Три площадки разнесены по трём разным элитным районам: Замоскворечье, Хамовники и Якиманка. Между крайними — «Земледельческим» и «Ордынкой» — около трёх километров. Общего у них не расположение, а происхождение: все три достались «Эталону» одной сделкой.'),

      // ─────────────── ORIGIN ───────────────
      h1('Откуда взялись эти площадки', { br: true }),
      body('В августе 2025 года Группа «Эталон» объявила о покупке 100 % акций АО «Бизнес-Недвижимость» у АФК «Система» за 14,1 млрд ₽. На балансе компании было 42 объекта в Москве и Петербурге — в основном здания автоматических телефонных станций, построенных с 1920-х по 1970-е годы и потерявших смысл вместе с исчезновением аналоговой связи. Сделку профинансировали через дополнительный выпуск акций на Мосбирже, без нового долга.', { after: 180 }),
      ...bullets([
        [txt('Ценность этих зданий — не в них самих, а в адресах. '), txt('Телефонные станции строили в центре, потому что от них шли медные линии к абонентам. Через сто лет это оказалось портфелем участков в местах, где свободной земли под застройку просто нет. Все три площадки в этой справке — бывшие АТС, и первый премиальный дом AURIX в Москве, «Резиденция Омега», тоже строится на месте станции.', { bold: true })],
        [txt('Заявленный масштаб программы: 18 приоритетных проектов, более 200 тыс. м² и более 185 млрд ₽ выручки до 2032 года. '), txt('Если разделить одно на другое, средний метр всей программы получается около 925 тыс. ₽ — это цифра по всему портфелю, включая Подмосковье и Петербург. Центральные площадки в ней — верхний край.', { bold: true })],
        'Бренд AURIX Группа «Эталон» запустила в апреле 2025 года, отдельно от основного бизнеса. Причина понятная: «Эталон» тридцать лет ассоциируется с массовым и комфорт-классом, и выводить дом за миллиард под этой маркой было бы странно. К августу 2026 года под AURIX уже четыре проекта в продаже — два в Москве и два в Петербурге — и три площадки в статусе «Скоро». Это уже похоже на отдельную платформу, а не на разовый эксперимент.',
      ]),

      h2('Хроника'),
      dataTable(
        ['Когда', 'Что произошло'],
        K.chron, [1900, 7738],
        { boldFirstCol: true, leftCols: [1], vtop: true },
      ),
      spacer(30),
      note('Даты сделки, разрешения на строительство и запуска бренда — из официальных сообщений и деловых СМИ. Даты прохождения согласований — из публикаций отраслевого Telegram-канала, который следит за градостроительными комиссиями.'),

      // ─────────────── PLOT 1: ORDYNKA ───────────────
      h1('Большая Ордынка, 25', { br: true }),
      body('Самая крупная и самая продвинутая по документам площадка из трёх. Класс делюкс, концепцию согласовала ГЗК Москвы в марте 2026 года.', { after: 150 }),
      image('ord_hero.jpg', PX, 222, { after: 34 }),
      caption('Главный ракурс с улицы: семь этажей, светлый камень, скруглённые эркеры и мансардный уровень.'),
      spacer(34),
      imagePair('ord_a.jpg', 'ord_b.jpg',
        'Фасад по улице: витражное остекление и первые этажи под коммерцию.',
        'Внутренний двор — закрытый, с озеленением и вечерним светом.'),
      spacer(36),
      factSheet(K.cardOrd),
      spacer(34),
      image('map_ord.jpg', PX, 188, { after: 34 }),
      note('Участок — в трёхстах метрах от Третьяковской галереи и в двух минутах от метро. Концепция построена как композиция городских усадеб с отсылкой к купеческому Замоскворечью XVII–XVIII веков; на фасадах авторы предложили разместить двенадцать мозаик утраченных церквей района. Материалы — натуральный камень и металл.'),

      // ─────────────── PLOT 2: ZEMLEDELCHESKY ───────────────
      h1('Земледельческий переулок, 15', { br: true }),
      body('Самая «мелкая» по формату площадка: 77 квартир средней площадью около 104 м². По сути это верхний премиум, а не элитка.', { after: 150 }),
      image('zem_hero.jpg', PX, 222, { after: 34 }),
      caption('Входная группа со стороны двора: барельеф на глухой стене, скруглённый объём, тёмное благоустройство.'),
      spacer(34),
      imagePair('zem_a.jpg', 'zem_b.jpg',
        'Башня с ленточными балконами — единственный опубликованный ракурс с улицы.',
        'Венчание объёма: те же скруглённые линии, что и по всей высоте.'),
      spacer(36),
      factSheet(K.cardZem),
      spacer(34),
      image('map_zem.jpg', PX, 188, { after: 34 }),
      note('Это единственные опубликованные визуализации проекта: собственной страницы у него нет ни у застройщика, ни в портфолио бюро. Кадры — из отраслевой публикации от 25.08.2026, где три проекта AURIX показаны вместе. Архитектор, этажность и срок сдачи не раскрыты.'),

      // ─────────────── PLOT 3: POLYANKA ───────────────
      h1('Малая Полянка, 3', { br: true }),
      body('Самая маленькая площадка и самый крупный формат квартиры: 34 резиденции средней площадью около 144 м².', { after: 150 }),
      image('pol_hero.jpg', PX, 222, { after: 34 }),
      caption('Уличный ракурс: девять этажей, белые фасады, скруглённые углы и аркада по первому этажу.'),
      spacer(34),
      imagePair('pol_a.jpg', 'pol_b.jpg',
        'Аркада первого этажа: изумрудная керамика в цоколе и узор на фасаде.',
        'Двор: «зелёная чешуя», тёмное мощение и подсветка по мотивам Врубеля.'),
      spacer(36),
      factSheet(K.cardPol),
      spacer(34),
      image('map_pol.jpg', PX, 188, { after: 34 }),
      note('Участок в двух шагах от метро «Полянка» и в пяти минутах от старой Третьяковки — эта близость, по словам авторов, и подсказала «сказочный образ» с отсылкой к русскому фольклору. Фасады — архитектурный бетон с изумрудной керамикой и моллированные витражи.'),

      // ─────────────── THE THREE PLOTS ───────────────
      h1('Три площадки рядом', { br: true }),
      body('Раскрыто по ним неодинаково. Больше всего известно про «Малую Полянку» и «Земледельческий» — там при согласовании назвали и площадь, и число квартир. По «Большой Ордынке» опубликована только общая площадь комплекса и этажность.', { after: 180 }),
      dataTable(
        ['Площадка', 'Район', 'Что было на участке', 'Раскрытая площадь', 'Квартир', 'Этажей', 'Сдача'],
        K.plots, [2050, 1600, 1500, 1550, 1000, 850, 1088],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(30),
      note('«Не раскрыто» означает, что показатель не публиковался, а не что его нет. Прочерк в графе сдачи — срок не объявлен ни застройщиком, ни в материалах согласований. У «Большой Ордынки» сверх семи этажей есть ещё мансардный уровень с панорамным остеклением.'),
      spacer(40),
      ...bullets([
        [txt('Экономика всех трёх держится на цене метра, потому что метража мало. '), txt('От 4,9 до 17,3 тыс. м² на проект. При таких размерах расходы на согласования, архитектуру и стройку размазываются на очень небольшой метраж, и вытянуть экономику можно только высокой ценой. Это не выбор позиционирования, а следствие размера площадки.', { bold: true })],
        [txt('Охранный статус здания АТС на Ордынке стоит проверить отдельно. '), txt('Это Первая московская автоматическая телефонная станция 1927 года; в описаниях проекта её называют памятником раннего конструктивизма, а работу архитекторов — встраиванием наследия в жилой формат. Формального статуса объекта культурного наследия по открытым источникам подтвердить не удалось. От него зависит, идёт речь о реконструкции или о новом строительстве, а значит и сроки, и себестоимость.', { bold: true })],
        [txt('Форматы квартир различаются вдвое. '), txt('«Земледельческий» — около 104 м² в среднем, «Полянка» — около 144 м². Это разные покупатели: первый ещё в пределах верхнего премиума, второй уже в элитном сегменте. По «Ордынке» число квартир не раскрыто, но при делюксовом позиционировании формат там будет ближе к «Полянке».', { bold: true })],
        [txt('Степень раскрытия по трём площадкам очень разная, и это само по себе показательно. '), txt('По «Полянке» опубликованы и архитектурная концепция, и показатели. По «Ордынке» — концепция и общая площадь, но не число квартир. По «Земледельческому» — только показатели: ни архитектора, ни этажности, ни визуализаций на собственных ресурсах. Похоже на разную степень готовности: «Полянка» вышла на согласование первой, «Земледельческий» — последним.', { bold: true })],
        [txt('С транспортом у всех трёх по-разному. '), txt('«Полянка» и «Ордынка» стоят в двух минутах от одноимённых станций метро. До «Смоленской» от «Земледельческого» около километра — минут пятнадцать пешком. Для элитного сегмента это не решающий фактор, но на «Земледельческом» он добавляется к и без того более скромному формату квартир.')],
      ]),

      // ─────────────── PRICE ───────────────
      h1('Сколько это может стоить'),
      body('Цен нет ни по одной из трёх площадок. Единственный публичный ориентир — отраслевая оценка «не менее 3 млн ₽ за метр», которая звучала и по «Большой Ордынке» в марте 2026 года, и по Хамовникам в мае. От неё и считаем. Это рабочая величина, а не прайс.', { after: 180 }),
      image('chart.jpg', PX, 312, { after: 60 }),
      caption('Синее — средняя цена предложения по району. Красное — ориентир по трём площадкам.'),
      spacer(40),
      ...bullets([
        [txt('Ориентир в 3 млн ₽ за метр по-разному ложится на три района. '), txt('В Якиманке и Хамовниках он почти совпадает со средней ценой предложения — превышение 2 и 7 %. А в Замоскворечье средняя цена 2 020 тыс. ₽, и 3 млн — это уже на 49 % выше района. При этом по классу делюкс в целом по Москве средняя составляет 3 242 тыс. ₽, то есть ориентир идёт на 7 % ниже её.', { bold: true })],
        [txt('Но по «Большой Ордынке» цена, скорее всего, будет выше трёх миллионов. '), txt('Прямо на этой же улице продаются «Русские сезоны» — делюкс со сдачей в конце 2026 года, где метр стоит примерно от 3,4 до 4,2 млн ₽. Средняя по Замоскворечью в 2 020 тыс. ₽ описывает район целиком, вместе с его дешёвой периферией, а не конкретно Ордынку. Проект и называют самым дорогим у «Эталона» в Москве.', { bold: true })],
      ]),

      h2('Что это даёт в деньгах'),
      dataTable(
        ['Площадка', 'Квартир,\nм²', 'Лотов', 'Средняя\nквартира, м²', 'Ориентир,\n₽/м²', 'Выручка,\nмлрд ₽', 'Средний\nчек, млн ₽'],
        K.revenue, [2150, 1750, 1150, 1250, 1250, 1250, 838],
        { boldFirstCol: true, totalLast: true },
      ),
      spacer(30),
      note('По «Земледельческому» и «Полянке» площадь квартир раскрыта прямо. По «Ордынке» известна только общая площадь комплекса — 17,3 тыс. м². Доля квартир в ней у двух проектов AURIX, где опубликованы обе цифры, разная: 49 % у «Резиденции Омега» и 62 % у «Малой Полянки». Поэтому по «Ордынке» дана вилка, а не одно число.'),
      spacer(40),
      ...bullets([
        [txt('Три площадки из сорока двух дают около 64 млрд ₽ выручки. '), txt('Это в четыре с половиной раза больше, чем «Эталон» заплатил за всю «Бизнес-Недвижимость» целиком — за 14,1 млрд ₽ и все 42 объекта. Понятно, что к выручке нужно прибавить стройку, согласования и годы работы, но соотношение показывает, зачем сделка была нужна.', { bold: true })],
        [txt('И ещё нагляднее — доля в программе. '), txt('21,4 тыс. м² квартир на этих трёх участках — это около 11 % от заявленных 200 тыс. м² потенциала застройки. А 64 млрд ₽ — это около 35 % от заявленных 185 млрд ₽ выручки до 2032 года. То есть примерно треть денег всей программы приходится на одну девятую её метража. Центральные адреса и есть та часть портфеля, ради которой всё затевалось.', { bold: true })],
        [txt('Средний чек различается втрое. '), txt('«Земледельческий» — около 312 млн ₽ за квартиру, «Полянка» — около 432 млн, «Ордынка» по оценке — около 465 млн. Это три разных покупателя: первый ещё в пределах верхнего премиума, двое других — уже элитный сегмент, где сделок в Москве в разы меньше.', { bold: true })],
      ]),

      // ─────────────── NEIGHBOURS ───────────────
      h1('С чем это будет конкурировать'),
      body('Прямых аналогов по каждой площадке немного — центр застроен, и новые дома здесь штучные. Ниже то, что даёт ценовой ориентир: соседи по Ордынке и собственные проекты AURIX, уже вышедшие в продажу.', { after: 180 }),
      dataTable(
        ['Проект', 'Адрес', 'Застройщик', 'Площади, м²', 'Цены, млн ₽', 'Метр, тыс. ₽', 'Сдача'],
        K.near, [1900, 1900, 1200, 1200, 1250, 1200, 988],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(30),
      note('«Не публикуется» — цены закрыты и выдаются по запросу. Метр по «Русским сезонам» посчитан из опубликованных диапазонов цены и площади.'),
      spacer(40),
      ...bullets([
        [txt('«Русские сезоны» — главный ориентир по Ордынке. '), txt('Делюкс, квартиры 117–268 м² по 492–917 млн ₽, чистовая отделка включена, сдача в IV квартале 2026 года. Метр там 3,4–4,2 млн ₽. Это то, с чем «Большая Ордынка, 25» будет конкурировать напрямую — с той разницей, что «Сезоны» сдаются через несколько месяцев, а «Ордынка» через пять лет.', { bold: true })],
        [txt('Собственные проекты AURIX цен не раскрывают тоже. '), txt('Ни «Резиденция Омега», ни «Арбат 2» прайс не публикуют — на площадках стоит «цена по запросу». Так что даже откалибровать ориентир по уже работающим домам того же застройщика не получается. Это осознанная политика бренда, а не пробел в данных.', { bold: true })],
        [txt('Зато «Арбат 2» показывает, как AURIX работает с такими зданиями. '), txt('Это тоже бывший объект связи — «Дом связи» 1965 года постройки на Новом Арбате. Из него сделали делюкс на 23 квартиры и 2 пентхауса с видом на Кремль, потолками до 3,5 м, подземным паркингом на 66 мест и 42 кладовыми. Дом готов с 2025 года. Логика та же, что будет на трёх площадках: небольшой объём, дорогой метр, упор на адрес и сервис.', { bold: true })],
      ]),

      // ─────────────── PORTFOLIO ───────────────
      h1('Весь портфель AURIX'),
      dataTable(
        ['Проект', 'Адрес', 'Что это', 'Статус'],
        K.portfolio, [2400, 2600, 3200, 1438],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(30),
      note('По состоянию на 26.08.2026 по данным сайта застройщика. Три площадки из этой справки отмечены значком.'),
      spacer(40),
      ...bullets([
        [txt('Четыре проекта в продаже, три на подходе — и это только начало. '), txt('Группа заявляла 18 приоритетных проектов, то есть портфель должен вырасти ещё более чем вдвое. Причём объявленные семь — это уже два города и как минимум три ценовых уровня: от премиума в Гагаринском районе до делюкса на Новом Арбате.', { bold: true })],
        [txt('Опыта сдачи домов этого класса у бренда пока почти нет. '), txt('AURIX существует полтора года. Единственный готовый объект — «Арбат 2», и это реконструкция, а не стройка с нуля. «Резиденция Омега» сдаётся только в 2030 году, «Большая Ордынка» — в 2031-м. За материнской Группой «Эталон» стоят тридцать лет и десятки сданных домов, но в массовом сегменте; репутация в делюксе строится отдельно и медленно.', { bold: true })],
      ]),

      // ─────────────── CONCLUSIONS ───────────────
      h1('Выводы'),
      ...bullets([
        [txt('Три площадки — это один актив, а не три сделки. '), txt('Все они пришли одним пакетом, у всех одинаковое происхождение и один девелопер. Смотреть на них по отдельности — значит не увидеть главного: «Эталон» купил портфель центральных адресов оптом и теперь распаковывает его по одному.', { bold: true })],
        [txt('Экономика держится на цене метра, потому что метража мало. '), txt('От 4,9 до 17,3 тыс. м² на проект. Такой объём не позволяет зарабатывать на масштабе — только на цене. Отсюда и ориентир в 3 млн ₽ за метр, и делюксовое позиционирование: при меньшей цене эти проекты просто не окупят согласования и стройку в центре.', { bold: true })],
        [txt('Суммарно это около 64 млрд ₽ — треть денег всей программы на одной девятой метража. '), txt('И в четыре с половиной раза больше, чем стоила вся покупка «Бизнес-Недвижимости». Три центральных адреса из сорока двух объектов несут непропорционально большую часть смысла сделки.', { bold: true })],
        [txt('Главная неопределённость — сроки, а не цена. '), txt('Согласования по «Ордынке» прошли необычно быстро, но сдача заявлена только на IV квартал 2031 года, а по двум другим площадкам сроков нет вовсе. Между согласованной концепцией и разрешением на строительство в центре Москвы обычно проходит ещё год-полтора, а элитный проект реализуется четыре-пять лет с начала стройки.', { bold: true })],
        [txt('И главное ограничение самой справки. '), txt('Ни один из трёх проектов не продаётся, цен нет, планировок нет, часть показателей не раскрыта. Всё, что здесь посчитано, опирается на отраслевой ориентир в 3 млн ₽ за метр и на раскрытые площади. Как только появятся прайсы, пересчитывать придётся всё — и выручку, и средние чеки, и позиционирование каждой площадки.', { bold: true })],
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
      note('Справка подготовлена 26.08.2026 по открытым источникам. Ни один из трёх проектов на эту дату не продаётся; цена метра и всё производное от неё — оценка, а не прайс. Документ носит справочный характер и не является офертой или отчётом об оценке.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'AURIX_три_площадки_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
