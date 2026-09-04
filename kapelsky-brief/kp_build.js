const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'kp_tables.json'), 'utf8'));
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





// Карточки квартир: кадр из объявления и параметры лота, N в ряд.
function photoCards(cards, cols = 3) {
  const w = Math.floor(9520 / cols), gap = 90;
  const pxw = Math.floor((w - gap * 2) / 15);          // DXA -> px при 96 dpi
  const pxh = Math.round(pxw / 1.5);
  const cellOf = (c, i) => new TableCell({
    width: { size: w, type: WidthType.DXA },
    margins: { top: 0, bottom: 0, left: i === 0 ? 0 : gap, right: i === cols - 1 ? 0 : gap },
    borders: { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder },
    children: c ? [
      p({
        children: [new ImageRun({ data: IMG(c.file), type: 'jpg',
          transformation: { width: pxw, height: pxh } })],
        spacing: { after: 46 },
      }),
      p({ children: [txt(c.name, { size: 17, bold: true, color: INK })],
          spacing: { after: 18, line: 212, lineRule: LR } }),
      p({ children: [txt(`${c.area} м²  ·  этаж ${c.floor}`, { size: 14, color: MUTED })],
          spacing: { after: 26, line: 206, lineRule: LR } }),
      p({ children: [txt(c.price + ' млн ₽', { size: 19, bold: true, color: INK })],
          spacing: { after: 14, line: 212, lineRule: LR } }),
      p({ children: [txt(c.ppm + ' ₽ за м²', { size: 14, color: BRONZE })],
          spacing: { after: 16, line: 206, lineRule: LR } }),
      p({ children: [new ExternalHyperlink({
            children: [txt('Объявление →', { size: 13, color: '2C5FA8' })], link: c.url })],
          spacing: { after: 0, line: 200, lineRule: LR } }),
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
        children: [p({ children: [txt('')], spacing: { after: 0, line: 190, lineRule: LR } })],
      })),
    }));
  }
  return new Table({
    columnWidths: Array(cols).fill(w),
    width: { size: w * cols, type: WidthType.DXA },
    rows,
  });
}

// «1 лот», «2 лота», «5 лотов» — иначе в тексте попадаются «201 лотов»
const plural = (n, [one, few, many]) => {
  const a = Math.abs(n) % 100, b = a % 10;
  if (a > 10 && a < 20) return `${n} ${many}`;
  if (b > 1 && b < 5) return `${n} ${few}`;
  if (b === 1) return `${n} ${one}`;
  return `${n} ${many}`;
};

const bullets = (items) => items.map((t) => p({
  children: [txt('—   ', { color: BRONZE, bold: true }), ...(Array.isArray(t) ? t : [txt(t)])],
  spacing: { after: 58, line: 252, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
}));

// ── content ────────────────────────────────────────────────────────────────
const N = K.nums, C = K.coh, CB = K.club;

const TILES = [
  ['46', 'Резиденций'],
  ['54 – 345 м²', 'Площади'],
  ['≈ 1,3 млн ₽', 'Метр в закрытых продажах', true],
  ['2029', 'Срок сдачи'],
];

const SRC = [
  ['Параметры, визуализации и статус продаж — официальный сайт проекта', 'https://www.kapelsky5.ru/'],
  ['Старт закрытых продаж в апреле 2026, средняя площадь резиденции и срок передачи ключей — обзор стартов продаж «Новострой-Медиа»', 'https://novostroev.ru/articles/starty-prodazh-novostroek-moskvy-v-aprele-2026/'],
  ['Этажность, количество лотов, срок сдачи и класс — карточки проекта у брокеров', 'https://whitewill.ru/developments/kapelskiy-5'],
  ['Планировочные параметры, паркинг и благоустройство — карточка «Новострой-М»', 'https://www.novostroy-m.ru/baza/jk_kapelskiy_5'],
  ['АО «МАКО»: ОГРН, дата регистрации, руководитель, ОКВЭД, финансовый результат', 'https://companies.rbc.ru/id/1027739638805-ao-mako/'],
  ['Цены элитной первички Москвы по классам, I квартал 2026 — данные NF Group', 'https://www.mirkvartir.ru/journal/news/2026/04/23/elite-novostroiki/'],
  ['Объявления о продаже — Циан: 478 лотов вокруг участка (Мещанский район и пешая доступность от «Проспекта Мира» и «Рижской», срез 02.09.2026) и 43 лота в клубных домах «Кло 17» и «Фамильный дом Люче» (срез 04.09.2026)', 'https://www.cian.ru/'],
  ['Координаты дома, станций метро и расстояния по прямой — OpenStreetMap. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'Клубный дом «Капельский, 5»',
  description: 'Справка по проекту: параметры, локация, визуализации, цена метра и сопоставление с рынком',
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
        txt('Клубный дом «Капельский, 5» · Мещанский район · срез 01.09.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      // ═══════════════ СТРАНИЦА 1 ═══════════════
      kicker('Информационная справка · Москва · 1 сентября 2026'),
      p({ children: [txt('Капельский, 5', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 60 } }),
      p({ children: [txt('Клубный дом на 46 резиденций в Мещанском районе', { size: 22, color: MUTED })],
          spacing: { after: 260 } }),

      statTiles(TILES),
      spacer(230),

      body('Дом строится в Капельском переулке — короткой улице между улицей Щепкина и проспектом Мира, в 410 метрах по прямой от станции метро «Проспект Мира». Закрытые продажи открылись в апреле 2026 года, цена метра на этом этапе — около 1,3 млн ₽. Разрешение на строительство получено, открытый старт по договорам долевого участия последует за публикацией проектной декларации.'),
      body(`Продаваемая площадь дома — около ${N.sellable} м²: 46 резиденций средней площадью 110 м², от 54 до 345 м². По цене закрытого этапа все лоты вместе стоят примерно ${N.gross} млрд ₽. Прежний трёхэтажный корпус на участке сносят, площадка расчищается под стройку.`),
      body(`1,3 млн ₽ за метр — на ${N.toPrem} % ниже средневзвешенной цены премиум-класса Москвы и на ${N.toElite} % ниже средней по элитному сегменту. В когорте из 379 лотов вокруг участка дешевле продаются 300 — четыре пятых предложения.`),
      body('Лоты продаются без отделки, поквартирного прайса и планировок в открытом доступе нет. Проектной декларации на 1 сентября 2026 года тоже нет: сайт проекта по-прежнему пишет об открытии продаж во II–III квартале 2026 года — после её размещения.'),
      body('Готовое жильё с дизайнерским ремонтом в соседних домах — Barkli Park в 890 метрах и Sole Hill в 960 — продаётся по 0,85–1,05 млн ₽ за метр. Новостроек ближе 1,3 км от участка нет ни одной.', { after: 220 }),

      image('hero.jpg', PX, 367, { after: 40 }),
      caption('Дом переменной этажности из двух объёмов: восемь этажей, натуральный камень на фасаде, террасы на верхних уровнях.'),

      // ═══════════════ ПАРАМЕТРЫ ═══════════════
      h1('Что известно о доме', { br: true }),
      body('Официальный сайт проекта параметров почти не раскрывает: там есть количество резиденций, диапазон площадей и паркинг. Остальное — из карточек агрегаторов и обзора стартов продаж; расхождения между источниками отмечены отдельно.', { after: 190 }),
      factSheet(K.card),
      spacer(260),
      h2('Шесть параметров, которые определяют продукт'),
      ...bullets(K.features.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(50),
      note('Проектной декларации на 1 сентября 2026 в открытом доступе нет, поэтому все цифры этой страницы — из маркетинговых материалов проекта и карточек брокеров.'),
      spacer(150),
      imagePair('fas_c.jpg', 'yard_d.jpg',
        'Восемь этажей, верхний уровень отступает вглубь',
        'Двор и террасы верхних уровней на закате'),

      // ═══════════════ ВИЗУАЛИЗАЦИИ ═══════════════
      h1('Визуализации проекта', { br: true }),
      body('Кадры с официального сайта проекта: фасады, верхние уровни с террасами, двор, гранд-лобби и паркинг.', { after: 200 }),
      imageTrio(['fas_a.jpg', 'fas_b.jpg', 'fas_d.jpg'],
        ['Дом со стороны переулка', 'Ракурс снизу, вечерняя подсветка', 'Фасад и озеленение днём']),
      spacer(36),
      imageTrio(['top_a.jpg', 'top_b.jpg', 'top_c.jpg'],
        ['Верхние этажи с террасами', 'Перголы на кровле', 'Терраса резиденции']),
      spacer(36),
      imageTrio(['yard_a.jpg', 'yard_b.jpg', 'yard_c.jpg'],
        ['Двор вечером', 'Водная чаша во дворе', 'Детская площадка']),
      spacer(36),
      imageTrio(['in_a.jpg', 'in_b.jpg', 'in_c.jpg'],
        ['Гранд-лобби', 'Подземный паркинг', 'Интерьер резиденции']),
      spacer(46),
      image('kvartal.jpg', 560, 243, { after: 30 }),
      caption('Квартал с высоты: дом в глубине застройки между проспектом Мира и улицей Щепкина.'),
      spacer(20),
      note('Дом не построен. Всё, что на этой странице, — проектные визуализации; застройщик отдельно оговаривает на сайте, что они ориентировочные и в проект могут вноситься изменения.'),

      // ═══════════════ РАСПОЛОЖЕНИЕ ═══════════════
      h1('Расположение', { br: true }),
      body('Капельский переулок проходит между улицей Щепкина и проспектом Мира, параллельно улице Гиляровского. Транзита через него нет, выезд на Садовое кольцо и ТТК — по проспекту Мира.', { after: 180 }),
      image('map_area.jpg', PX, 433, { after: 30 }),
      caption('Красное — участок, тёмное — станции метро с расстоянием по прямой.'),
      spacer(40),
      ...bullets([
        [txt('410 метров до «Проспекта Мира» по прямой. '), txt('Пересадочный узел Кольцевой и Калужско-Рижской линий; застройщик и агрегаторы указывают 470–490 метров пешком, около пяти минут.', { bold: true })],
        [txt('1,06 км до «Рижской». '), txt('Большая кольцевая, Калужско-Рижская линия и МЦД-2; до Рижского вокзала 13 минут пешком.', { bold: true })],
        [txt('610 метров до спорткомплекса «Олимпийский», 874 метра до «Аптекарского огорода». '), txt('Ботанический сад МГУ — старейший в России, заложен в 1706 году; Дом-музей Васнецова в 920 метрах.', { bold: true })],
        [txt('3,7 км до Кремля. '), txt('Дом стоит за Садовым кольцом, между ним и ТТК: до ТТК 821 метр.', { bold: true })],
      ]),
      spacer(40),
      dataTable(
        ['Объект', 'Что это', 'По прямой'],
        K.nearby, [3400, 4400, 1838],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(30),
      note('Расстояния посчитаны по прямой от контура участка; пешеходный маршрут длиннее на 10–20 %.'),

      // ═══════════════ РАЙОН ═══════════════
      h1('Мещанский район', { br: true }),
      body('Район занимает север Центрального округа между Садовым кольцом и ТТК. Он сложился как городская окраина XVII века — Мещанская слобода, куда селили выходцев из городов Речи Посполитой, — и с тех пор застраивался слоями: усадьбы, доходные дома, советские институты, офисы девяностых и двухтысячных. Свободных участков здесь почти не осталось, поэтому новые дома появляются точечно, на месте снесённых.', { after: 190 }),
      factSheet(K.district),
      spacer(240),

      h2('Что работает на район'),
      ...bullets(K.distPro.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(60),

      h2('Что работает против'),
      ...bullets(K.distContra.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(50),
      note('Площадь, население и плотность — по данным на 2025 год. Расстояния от контура участка посчитаны по прямой.'),

      // ═══════════════ УЧАСТОК И ЗАСТРОЙЩИК ═══════════════
      h1('Участок и застройщик', { br: true }),
      body('Участок в Капельском переулке занимал трёхэтажный кирпичный корпус советской постройки. Сейчас его сносят: площадка расчищается под новое строительство.'),
      body(`На освободившемся месте встанет дом переменной этажности на 46 резиденций — около ${N.sellable} м² продаваемой площади плюс два подземных уровня паркинга на 56 машино-мест и 32 кладовые. Назначение участка меняется с нежилого на жилое, этажность вырастает с трёх до восьми.`),
      body('Окружение участка офисное и институциональное: с юга примыкает территория МОНИКИ имени Владимирского, вдоль проспекта Мира стоят бизнес-центры, включая Olympic Plaza. В списках новостроек Мещанского района проекты премиального и элитного класса сосредоточены у «Сухаревской», «Трубной» и «Цветного бульвара» — в полутора-двух километрах отсюда. У «Проспекта Мира» «Капельский, 5» пока единственный.', { after: 200 }),

      h2('Кто застройщик'),
      body('Сайт kapelsky5.ru принадлежит АО «МАКО» — компания указана оператором персональных данных в документах сайта. Реквизиты: ИНН 7704198529, ОГРН 1027739638805, регистрация 12 июля 1999 года, генеральный директор Первой Илья Николаевич, уставный капитал 2 млн ₽. Основной вид деятельности по ОКВЭД — 64.99, прочие финансовые услуги; всего в выписке 26 видов деятельности, включая строительство и операции с недвижимостью. Финансовый результат 2025 года — убыток 84,6 млн ₽.'),
      body('Публичного портфеля жилых проектов у компании нет: «Капельский, 5» — единственный объект, который связывается с этим юридическим лицом в открытых источниках. Название застройщика, который будет указан в проектной декларации, пока не опубликовано — обычно это отдельное юридическое лицо под конкретный дом.', { after: 200 }),
      image('map_city.jpg', PX, 392, { after: 30 }),
      caption('Дом относительно центра: между Садовым кольцом и ТТК, в створе проспекта Мира.'),

      // ═══════════════ ЦЕНА ═══════════════
      h1('Цена', { br: true }),
      body('Поквартирного прайса у проекта нет ни на сайте, ни у брокеров: в карточках стоит «цена по запросу». Единственный публичный ориентир — цена метра закрытого этапа, около 1,3 млн ₽. Ниже — что из неё следует и как она соотносится с рынком.', { after: 190 }),
      factSheet(K.priceFacts),
      spacer(250),
      body(`Разница между ценой закрытого этапа и средним метром премиум-класса на объёме дома — около ${N.gap} млрд ₽. Это и есть цена входа до получения разрешения на строительство: покупатель закрытого этапа берёт на себя риск проекта без ДДУ, эскроу и проектной декларации.`, { after: 190 }),

      h2('Полный бюджет резиденции'),
      body('Лоты идут без отделки. Ремонт в премиум-сегменте Москвы стоит 300–500 тыс. ₽ за метр; в таблице взята середина вилки — 400 тыс. ₽.', { after: 170 }),
      dataTable(
        ['Площадь, м²', 'Покупка, млн ₽', 'Отделка, млн ₽', 'Итого, млн ₽'],
        K.budget, [2100, 2600, 2400, 2538],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Отделка добавляет к бюджету 31 % — при цене метра 1,3 млн ₽ и отделке 400 тыс. ₽ полная стоимость метра получается 1,7 млн ₽, то есть уровень среднего премиум-метра Москвы.'),

      h2('Рынок элитной первички Москвы, I квартал 2026'),
      dataTable(
        ['Показатель', 'Значение', 'Комментарий'],
        K.market, [3900, 2100, 3638],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(40),
      note('Данные NF Group за январь–март 2026 года. Средневзвешенная цена предложения по элитному сегменту выросла на 3 % за квартал и на 9 % за год, при этом объём сделок упал на 45 % год к году, а средняя площадь проданного лота — со 116 до 99 м².'),

      // ═══════════════ КОНКУРЕНТЫ ═══════════════
      h1('Что продаётся рядом', { br: true }),
      body(`Вокруг участка в радиусе 2,5 км сейчас продаётся ${C.total} квартир дороже 400 тыс. ₽ за метр: ${C.new} в новостройках и ${C.resale} на вторичке, из них ${C.apart} — апартаменты. Ниже — проекты и дома, где в продаже больше трёх лотов.`, { after: 180 }),
      image('map_peers.jpg', 560, 462, { after: 30 }),
      caption('Бронзовое — новостройки, тёмное — вторичка. Номера совпадают с таблицами.'),
      spacer(46),

      h2('Новостройки'),
      dataTable(
        ['№', 'Проект', 'До нас', 'Сдача', 'Отделка', 'Лотов', 'Площади, м²', 'Метр,\nмлн ₽'],
        K.peerNew, [520, 1750, 1000, 950, 1900, 700, 1400, 1418],
        { boldFirstCol: true, leftCols: [1, 4] },
      ),
      spacer(30),
      note(`«Отделка не указана» означает, что застройщик её не заявил; оболочкой такая квартира при этом продаваться не обязана. Медиана метра по трём премиальным проектам (${plural(C.prem, ['лот', 'лота', 'лотов'])}) — ${C.premMed} ₽, по двум высотным бизнес-класса (${plural(C.biz, ['лот', 'лота', 'лотов'])}) — ${C.bizMed} ₽.`),

      h1('Вторичка в готовых домах', { br: true }),
      body(`В радиусе километра от участка новостроек нет вовсе: ближайшие начинаются с 1,3 км. Всё, что продаётся ближе, — ${plural(C.km, ['лот', 'лота', 'лотов'])} во вторичке с медианой метра ${C.kmMed} ₽.`, { after: 180 }),
      dataTable(
        ['№', 'Дом', 'До нас', 'Год', 'Лотов', 'Площади, м²', 'Метр,\nмлн ₽', 'Отделка'],
        K.peerRes, [520, 1900, 1000, 800, 700, 1400, 1418, 1900],
        { boldFirstCol: true, leftCols: [1, 7] },
      ),
      spacer(36),
      image('chart.jpg', PX, 322, { after: 26 }),
      caption('Засечка внутри полосы — медиана. Пунктир — средневзвешенный метр премиум-класса Москвы, 1,6 млн ₽.'),
      spacer(40),
      ...bullets([
        [txt(`1,3 млн ₽ попадают в разрыв между двумя группами. `), txt(`Выше — только три премиальных проекта у «Сухаревской» и «Цветного»: «ФАНТОМ» с медианой 2,48 млн, «Форум» 2,14 млн и «Дом Франка» 1,74 млн. Ниже — всё остальное: ${C.below} лотов из ${C.total} (${C.belowPct} %).`, { bold: true })],
        [txt('Ближайшие новостройки — высотки бизнес-класса. '), txt(`«Ридж» (26 этажей, 120 лотов, 0,51–0,90 млн) и «Мод» (55 этажей, 81 лот, 0,51–0,72 млн) стоят в 1,4 км, у Сущёвского вала. Метр «Капельского» выше их медианы в ${C.toBiz} раза.`, { bold: true })],
        [txt('Готовое жильё по соседству дешевле на 24–53 %. '), txt('Barkli Park в 890 метрах — медиана 1,05 млн ₽ за метр, Sole Hill в 960 метрах — 0,85 млн, Dialog в 1,29 км — 1,03 млн. Все три дома построены и заселены; «Капельский» просит больше за дом, который сдадут через три года.', { bold: true })],
        [txt('Единственный сосед с тем же метром — апартаменты. '), txt('Клубный дом ЦВЕТ32 у Цветного бульвара, медиана 1,27 млн ₽ при 1,3 млн у «Капельского». Все шесть лотов там — нежилой фонд: без прописки и с другой ставкой налога.', { bold: true })],
      ]),

      // ═══════════════ СРЕДНИЕ ПО ДОМАМ ═══════════════
      h1('Средняя цена метра по домам', { br: true }),
      body('Одна строка на дом: сколько лотов в продаже, какая у них средняя площадь, средняя цена лота и средний метр. Дома отсортированы по метру, номера совпадают с картой конкурентов. Последняя строка — «Капельский, 5» по цене закрытых продаж.', { after: 200 }),
      statTiles([
        ['1 300 000 ₽', 'Средний метр Капельского'],
        ['5-е из 12', 'Место по метру в когорте'],
        ['1 018 437 ₽', 'Медиана метра у соседей'],
        ['−47 %', 'К «ФАНТОМу», лидеру когорты', true],
      ]),
      spacer(230),
      dataTable(
        ['№', 'Дом', 'Что это', 'Лотов', 'Средняя\nплощадь, м²', 'Средняя цена\nлота, млн ₽', 'Средний\nметр, ₽'],
        K.houseRows, [520, 2300, 2000, 750, 1300, 1450, 1318],
        { boldFirstCol: true, leftCols: [1, 2], totalLast: true },
      ),
      spacer(46),
      ...bullets([
        [txt('Средний метр «Капельского» выше, чем у семи домов когорты из одиннадцати. '), txt('Дороже только три премиальных новостройки у «Сухаревской» и «Цветного» — «ФАНТОМ» 2,45 млн, «Форум» 2,20 млн, «Дом Франка» 1,82 млн — и апартаменты ЦВЕТ32 с 1,36 млн.', { bold: true })],
        [txt('Средняя резиденция «Капельского» в 110 м² обойдётся в 143,0 млн ₽. '), txt('В Barkli Park в 890 метрах средний лот стоит 217,1 млн при площади 215 м², в Sole Hill в 960 метрах — 74,3 млн при 87 м². Ближайшие новостройки бизнес-класса продают средний лот за 36,9–37,1 млн ₽.', { bold: true })],
        [txt('Разброс среднего метра по когорте четырёхкратный — от 610 061 до 2 449 859 ₽. '), txt('Крайние точки лежат в двух с половиной километрах друг от друга: высотки у Сущёвского вала и клубные дома у Цветного бульвара.', { bold: true })],
        [txt('Средняя площадь лота различается вчетверо — от 54 м² в «Ридже» до 215 м² в Barkli Park. '), txt('110 м² у «Капельского» приходятся на середину когорты и ближе к клубным домам, чем к высоткам: у «Форума» 124 м², у «Дома Франка» 144 м², у «Мода» и «Риджа» 54–64 м².', { bold: true })],
        [txt('Медиана средних метров по одиннадцати соседям — 1 018 437 ₽. '), txt('Цена закрытых продаж выше неё на 28 %. Из продукта эту разницу поддерживают клубный формат на 46 квартир, 1,22 машино-места на квартиру и потолки до 4,18 м; из локации — 410 метров до пересадочного узла.', { bold: true })],
      ]),

      // ═══════════════ КЛУБНЫЙ ФОРМАТ ═══════════════
      h1('Клубные дома того же формата', { br: true }),
      body(`В Мещанском районе клубных домов сопоставимого масштаба в продаже нет. Ближайшие стоят у Кремля, в ${CB.dist} км от участка: «Кло 17» на Знаменке и «Фамильный дом Люче» в Крестовоздвиженском переулке. Оба — MR Group, оба сдаются в ${CB.deadline} года, в обоих столько же резиденций и этажей, сколько заявлено в «Капельском». Сравнение идёт по формату, а не по локации.`, { after: 190 }),
      statTiles([
        [`${CB.ppmMed} ₽`, 'Медиана метра в обоих домах'],
        [`${CB.n} лота`, 'В открытой продаже на двоих'],
        [CB.deadline, 'Срок сдачи у обоих'],
        [`−${CB.toClub} %`, 'Метр «Капельского» к ним', true],
      ]),
      spacer(220),
      dataTable(
        ['Параметр', 'Капельский, 5', 'Кло 17', 'Фамильный дом Люче'],
        K.clubRows, [2600, 2400, 2300, 2338],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Площади, цены и сроки по «Кло 17» и «Люче» — из объявлений застройщика на Циан, срез 04.09.2026. Число резиденций в доме — из карточек проектов.'),

      h2('Несколько лотов для сравнения'),
      dataTable(
        ['Дом', 'Площадь, м²', 'Этаж', 'Цена, млн ₽', 'Метр, ₽', 'Объявление'],
        K.clubLots, [2000, 1600, 1200, 1750, 1750, 1338],
        { boldFirstCol: true },
      ),
      spacer(46),
      ...bullets([
        [txt(`Метр у Кремля вдвое дороже: ${CB.ppmMed} ₽ по медиане против 1,3 млн ₽. `), txt('Разрыв держится по всей линейке: даже самый дешёвый метр в «Кло 17» — 2,30 млн ₽ — выше цены закрытого этапа «Капельского» на 77 %.', { bold: true })],
        [txt(`Ключи там отдают в ${CB.deadline} года, здесь — в 2029-м. `), txt('Все 17 лотов «Кло 17» продаются без отделки, у «Люче» отделка в объявлениях не заявлена; «Капельский» тоже отдаёт оболочку.', { bold: true })],
        [txt(`Лоты крупнее: средняя площадь ${CB.areaAvg} м² против 110 м² у «Капельского». `), txt(`Верхняя точка — пентхаус «Люче» на ${CB.topArea} м² за ${CB.top} млн ₽, метр ${CB.topPpm} ₽. Максимальный лот «Капельского» в 345 м² по цене закрытого этапа стоит 448,5 млн ₽.`, { bold: true })],
        [txt(`${CB.kremlinClub} км до Кремля против ${CB.kremlin} км. `), txt('Оба дома стоят внутри Бульварного кольца, между Знаменкой и Воздвиженкой; «Капельский» — за Садовым, в жилом квартале у проспекта Мира.', { bold: true })],
      ]),

      // ═══════════════ ОТДЕЛКА ═══════════════
      h1('Какие ремонты продаются рядом', { br: true }),
      body(`Лоты «Капельского» продаются без отделки: покупатель получает оболочку и делает ремонт сам. Ниже — двадцать четыре квартиры с дизайнерским ремонтом, которые прямо сейчас продаются в готовых современных домах вокруг Капельского переулка. Это тот уровень отделки, к которому придётся приводить резиденцию, и та цена, по которой такая квартира уходит на рынке.`, { after: 190 }),
      photoCards(K.cards.slice(0, 12), 3),

      photoCards(K.cards.slice(12), 3),
      spacer(60),
      note('Фотографии из объявлений о продаже. Все квартиры — вторичка в домах 2010–2024 годов постройки; расстояния до участка и число лотов в каждом доме — в таблицах когорты.'),
      spacer(40),
      ...bullets([
        [txt(`Диапазон — от ${K.cards[K.cards.length - 1].ppm} до ${K.cards[0].ppm} ₽ за метр, медиана ${K.cardsMed} ₽. `), txt(`Верх задают ${K.cards[0].name} и ЦВЕТ32 у Цветного бульвара, низ — «Волга» и «Мод» у Сущёвского вала. Медиана на 17 % ниже 1,3 млн ₽ у «Капельского», причём въехать в такую квартиру можно сегодня.`, { bold: true })],
        [txt(`Такая же квартира без ремонта в тех же домах стоит на ${K.fin.pct} % дешевле — разница ${K.fin.gap} ₽ за метр. `), txt(`Сам ремонт обходится в 300–500 тыс. ₽ за метр — при перепродаже возвращается ${K.fin.coverLo}–${K.fin.coverHi} % вложенного.`, { bold: true })],
      ]),

      // ═══════════════ РнС ═══════════════
      h1('Что меняет разрешение на строительство', { br: true }),
      body('Разрешение на строительство разделяет проект на два разных режима продаж — юридически и по цене.', { after: 190 }),
      ...bullets(K.rns.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(50),
      note('Проектная декларация публикуется в единой информационной системе жилищного строительства на наш.дом.рф. Из неё станут известны точная этажность, состав лотов, срок ввода и юридическое лицо застройщика.'),

      h2('Хронология'),
      dataTable(
        ['Дата', 'Событие'],
        K.timeline, [2100, 7538],
        { boldFirstCol: true, leftCols: [1] },
      ),

      // ═══════════════ РИСКИ ═══════════════
      h2('Открытые вопросы'),
      body('Шесть мест, где открытые данные расходятся между собой или обрываются.', { after: 170 }),
      ...bullets(K.risks.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),

      // ═══════════════ ВЫВОДЫ ═══════════════
      h1('Выводы', { br: true }),
      ...bullets([
        [txt('Цена закрытого этапа на 23 % ниже среднего премиум-метра и на 43 % ниже среднего элитного. '), txt(`На объёме дома в ${N.sellable} м² это разница около ${N.gap} млрд ₽ между ${N.gross} и ${N.grossPrem} млрд ₽. Скидка оплачена риском: закрытые продажи шли без разрешения на строительство, без ДДУ и без эскроу.`, { bold: true })],
        [txt('Получение разрешения снимает главный риск этапа. '), txt('Дальше продажи идут по 214-ФЗ, деньги лежат на эскроу до ввода дома. Цена при этом обычно пересматривается: ближайший ориентир открытого рынка — «Форум» у «Сухаревской», 1,78–2,67 млн ₽ за метр с чистовой отделкой.', { bold: true })],
        [txt('В радиусе километра от участка новостроек нет вовсе. '), txt(`Ближайшие начинаются с 1,3 км, и это две высотки бизнес-класса с медианой ${C.bizMed} ₽ за метр. Премиальные соседи — «ФАНТОМ», «Форум» и «Дом Франка» — стоят в 1,3–2,2 км, ближе к «Сухаревской» и «Цветному»; их медиана ${C.premMed} ₽. Цена «Капельского» ложится ровно между этими двумя группами. Клубные дома того же формата у Кремля — «Кло 17» и «Люче» — идут по ${CB.ppmMed} ₽ за метр.`, { bold: true })],
        [txt(`Из ${C.total} лотов когорты ${C.below} (${C.belowPct} %) продаются дешевле 1,3 млн ₽ за метр. `), txt('Готовое жильё в соседних домах — Barkli Park в 890 метрах, Sole Hill в 960 — идёт по 0,85–1,05 млн ₽ за метр. За дом со сдачей в 2029 году покупатель платит на 24–53 % больше, чем за квартиру, в которую можно въехать сейчас.', { bold: true })],
        [txt('Бюджет входа — около 70 млн ₽ за 54 м², 143 млн ₽ за среднюю резиденцию в 110 м². '), txt(`Лоты без отделки, ремонт в этом сегменте добавляет 300–500 тыс. ₽ за метр, то есть 33–55 млн ₽ на среднюю квартиру. Полный бюджет средней резиденции получается 176–198 млн ₽. При перепродаже рынок локации возвращает за ремонт ${K.fin.gap} ₽ за метр — ${K.fin.coverLo}–${K.fin.coverHi} % вложенного.`, { bold: true })],
        [txt('46 квартир и 56 машино-мест — 1,22 места на квартиру. '), txt('Для дома внутри ТТК это высокий показатель: место в паркинге не придётся искать на стороне.', { bold: true })],
        [txt('410 метров до пересадочного узла и 3,7 км до Кремля. '), txt('Локация даёт метро Кольцевой линии в пяти минутах пешком и при этом остаётся за Садовым кольцом, где метр стоит дешевле, чем внутри Бульварного.', { bold: true })],
        [txt('Сроки: ключи в 2029 году, три года от разрешения до ввода. '), txt('Дом на 46 квартир строится быстрее крупного жилого комплекса, но проектной декларации с закреплённым сроком в открытом доступе пока нет.', { bold: true })],
        [txt('Рынок сегмента за год сжался по сделкам на 45 % при росте цен на 9 %. '), txt('Средняя площадь проданного лота упала со 116 до 99 м². Диапазон «Капельского» от 54 м² попадает в ту часть спроса, которая растёт: покупатели берут компактнее.', { bold: true })],
        [txt('Застройщик — компания без публичной девелоперской истории. '), txt('АО «МАКО» с основным ОКВЭД 64.99, уставным капиталом 2 млн ₽ и убытком 84,6 млн ₽ за 2025 год. До появления проектной декларации с названием и финансовой моделью застройщика это главный непроверяемый пункт.', { bold: true })],
      ]),

      spacer(30),
      rule(100),
      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 32, line: 206, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      note('Справка составлена 01.09.2026 по открытым источникам. Поквартирного прайса и проектной декларации у проекта на эту дату нет; все ценовые ориентиры — оценочные и офертой не являются.'),


    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'ЖК_Капельский_5_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
