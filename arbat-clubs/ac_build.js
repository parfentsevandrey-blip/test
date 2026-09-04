const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'ac_tables.json'), 'utf8'));
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
const N = K.nums, C = K.coh;

const TILES = [
  [`${N.n} лота`, 'В открытой продаже'],
  [`${N.ppmMed} ₽`, 'Медиана метра', true],
  ['228 – 2 154', 'Бюджет лота, млн ₽'],
  [N.deadline, 'Срок сдачи у обоих'],
];

const SRC = [
  ['Объявления застройщика на Циан: 17 лотов «Кло 17» и 26 лотов «Люче», срез 04.09.2026 — площади, этажи, цены, сроки, форма продажи, срок экспозиции', 'https://www.cian.ru/'],
  ['Карточка «Кло 17»: класс, число резиденций, потолки, паркинг, архитектор', 'https://www.novostroy-m.ru/baza/jk_clos_17_kloz'],
  ['Карточка «Люче»: класс, число квартир, потолки, паркинг, лифты, застройщик', 'https://m2.ru/moskva/novostroyki/klubnii-dom-luce-12346/'],
  ['Параметры «Кло 17» и состав инфраструктуры — каталог элитной недвижимости', 'https://elitnoe.ru/complexes/1122-zhk-clos-17'],
  ['Параметры «Люче»: этажность, потолки, паркинг, стадия строительства', 'https://novostroev.ru/novostroyki/moskva/cao/arbat/luce/'],
  ['Цены элитной первички Москвы по классам, I квартал 2026 — данные NF Group', 'https://www.mirkvartir.ru/journal/news/2026/04/23/elite-novostroiki/'],
  ['Площадь, население и плотность района Арбат — Википедия, оценка на 2025 год', 'https://ru.wikipedia.org/wiki/Арбат_(район_Москвы)'],
  ['MR Group: портфель, выручка от продаж квартир за 2024 год, позиция на рынке — карточка застройщика', 'https://www.novostroy-m.ru/kompanii/mr_group'],
  ['Ход стройки «Люче»: демонтаж, нулевой цикл, генподрядчик, архитектор — новости застройщика', 'https://www.novostroy-m.ru/kompanii/mr_group/news/mr_private_startuet_nulevoy'],
  ['Координаты домов и городских объектов, расстояния по прямой — OpenStreetMap. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Аналитическая справка',
  title: 'Клубные дома «Кло 17» и «Люче»',
  description: 'Два клубных дома MR Group на Арбате: параметры, район, застройщик, поквартирный прайс, структура цены, бюджет с отделкой и окрестная когорта',
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
        txt('«Кло 17» и «Люче» · Арбат, ЦАО · срез 04.09.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      // ═══════════════ СТРАНИЦА 1 ═══════════════
      kicker('Аналитическая справка · Москва · 4 сентября 2026'),
      p({ children: [txt('«Кло 17» и «Люче»', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 60 } }),
      p({ children: [txt('Два клубных дома MR Group на одном квартале у Кремля', { size: 22, color: MUTED })],
          spacing: { after: 260 } }),

      statTiles(TILES),
      spacer(230),

      body(`Дома стоят в ${N.between} метрах друг от друга, спина к спине: «Кло 17» — в Староваганьковском переулке, «Фамильный дом Люче» — в Крестовоздвиженском. Оба строит MR Group, оба сдаёт в ${N.deadline} года, до Кремля от обоих ${N.kremlin} км, до узла из четырёх станций метро — ${N.metro} метров.`),
      body(`В открытой продаже ${N.n} лота: ${N.nClos} в «Кло 17» и ${N.nLuce} в «Люче». Площади от ${N.areaLo} до ${N.areaHi} м², бюджет лота от ${N.priceLo} до ${N.priceHi} млн ₽. Суммарно это ${N.sellable} м² и ${N.gross} млрд ₽ по ценам объявлений.`),
      body(`Медиана метра у обоих домов совпадает до рубля — ${N.ppmMed} ₽. Это на ${N.toElite} % выше средневзвешенной цены элитного сегмента Москвы и на ${N.toLux} % ниже класса делюкс, к которому карточки относят «Кло 17».`),
      body(`Цену внутри дома определяет этаж, а не метраж: от второго этажа к пятому метр прибавляет ${N.floorStep} %, а между мелкими и крупными лотами разницы почти нет. Верхняя точка — пентхаус «Люче» на ${N.penthouseArea} м² за ${N.penthouse} млн ₽, метр ${N.penthousePpm} ₽.`),
      body(`В километре вокруг продаётся ещё ${C.total} лотов дороже 700 тыс. ₽ за метр, сведённых в ${C.groups} групп. Дороже двух домов — ${C.above} из них; лидер когорты, ${C.top}, идёт по ${C.topPpm} ₽ за метр. Медианный срок экспозиции лотов в обоих домах — ${N.days} дней.`),
      body('Дальше — параметры обоих домов бок о бок, район и застройщик, поквартирный прайс со ссылками на объявления, разбор структуры цены по этажам и площадям, полный бюджет резиденции с отделкой, планировки и когорта предложения в километре вокруг.', { after: 220 }),

      image('hero.jpg', PX, 367, { after: 30 }),
      caption('Проектная визуализация «Кло 17»: терраса верхнего уровня с видом на Храм Христа Спасителя.'),

      // ═══════════════ ПАРАМЕТРЫ ═══════════════
      h1('Что это за дома', { br: true }),
      body('Оба проекта — камерные дома на два-четыре десятка резиденций с подземным паркингом и закрытой территорией. Параметры домов взяты из карточек проектов, всё, что касается лотов в продаже, — из объявлений застройщика.', { after: 190 }),
      dataTable(
        ['Параметр', 'Кло 17', 'Фамильный дом Люче'],
        K.cardRows, [3300, 3169, 3169],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Число резиденций, потолки и паркинг — из карточек проектов; площади, цены, этажи, сроки и форма продажи — из объявлений застройщика на Циан. Расхождения между источниками вынесены в раздел «Открытые вопросы».'),
      spacer(150),
      ...bullets([
        [txt('Дома-близнецы по цене и сроку, но не по продукту. '), txt(`Медиана метра совпадает — ${N.ppmMed} ₽, сдача у обоих в ${N.deadline} года. При этом средний лот «Люче» на 53 м² крупнее, потолки выше на 0,7–0,9 м, а машино-мест вдвое больше.`, { bold: true })],
        [txt('Верхние этажи в продажу почти не выведены. '), txt('В «Кло 17» продаются только 2–5-й этажи семиэтажного дома, в «Люче» — 2–6-й. Пентхаусы верхнего уровня, которые описывают карточки проектов, в открытой экспозиции представлены одним лотом.', { bold: true })],
        [txt('Оболочка в обоих домах. '), txt('В «Кло 17» все 17 лотов заявлены без отделки, в «Люче» поле отделки не заполнено у 24 лотов из 26. Ремонт в этом сегменте — ещё 300–500 тыс. ₽ за метр сверх покупки.', { bold: true })],
        [txt('Форма продажи различается. '), txt('«Кло 17» продаётся по договорам долевого участия в рамках 214-ФЗ, у «Люче» в 25 объявлениях из 26 стоит свободная продажа — при том, что дом строится.', { bold: true })],
        [txt('Экспозиция у обоих домов больше года. '), txt('353 дня в «Кло 17» и 367 в «Люче» по медиане. Объявления открыты в сентябре 2025 года: 16 лотов из 17 в один день, 21 из 26 — в другой. Прайс выкладывали разом.', { bold: true })],
        [txt('Паркинг подземный в обоих домах. '), txt('64 места на 46 квартир в «Люче» и 29 на 26 в «Кло 17» — 1,39 и 1,12 места на квартиру. Кладовые и коммерческие помещения карточки описывают только у «Люче».', { bold: true })],
      ]),

      // ═══════════════ ПРОДУКТ ═══════════════
      h1('Что определяет продукт', { br: true }),
      body('Шесть параметров, которые отличают эти два дома от остального предложения в локации и друг от друга.', { after: 190 }),
      ...bullets(K.features.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(90),
      h2('Что входит в дом'),
      dataTable(
        ['Параметр', 'Кло 17', 'Фамильный дом Люче'],
        K.infraRows, [2200, 3719, 3719],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(36),
      note('Состав общих зон — из карточек проектов. Там, где карточки молчат, в таблице так и написано: проектной декларации в открытом доступе нет ни по одному из домов.'),
      spacer(120),
      h2('Что даёт один и тот же бюджет'),
      dataTable(
        ['Бюджет', 'Кло 17', 'Цена, млн ₽', 'Люче', 'Цена, млн ₽'],
        K.equalRows, [1900, 2400, 1700, 2400, 1238],
        { boldFirstCol: true, leftCols: [1, 3] },
      ),
      spacer(36),
      note('Для каждого бюджета взят ближайший по цене лот в каждом доме. На четверти миллиарда «Кло 17» даёт на 8 м² больше, дальше по линейке дома идут вровень.'),

      // ═══════════════ РАСПОЛОЖЕНИЕ ═══════════════
      h1('Расположение', { br: true }),
      body(`Квартал между Воздвиженкой и Знаменкой — та часть Арбата, что примыкает к Кремлю: Дом Пашкова и Российская государственная библиотека через дорогу, Александровский сад в десяти минутах пешком, Пушкинский музей в 450 метрах. Транзитного движения в переулках нет, выезд — на Воздвиженку и Знаменку.`, { after: 180 }),
      image('map_city.jpg', 610, 437, { after: 30 }),
      caption('Красное — оба дома, тёмное — городские объекты с расстоянием по прямой.'),
      spacer(40),
      dataTable(
        ['Объект', 'Что это', 'По прямой'],
        K.nearby, [3400, 4400, 1838],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(36),
      spacer(30),
      note('Расстояния измерены по прямой от точки между двумя домами. Координаты объектов — OpenStreetMap.'),

      // ═══════════════ КВАРТАЛ ═══════════════
      h1('Один квартал на двоих', { br: true }),
      body(`Между домами ${N.between} метров — это ширина одного двора. Два проекта одного застройщика выходят на один и тот же квартал, сдаются в одном квартале года и предлагают покупателю один и тот же метр по одной и той же цене.`, { after: 180 }),
      image('map_block.jpg', 600, 365, { after: 30 }),
      caption('Оба дома на карте Яндекса уже подписаны: «Люче» в Крестовоздвиженском, «Клос 17» в Староваганьковском.'),
      spacer(46),
      ...bullets([
        [txt(`${N.flats} резиденции в двух домах, ${N.n} из них в открытой продаже. `), txt('Остальные либо проданы, либо не выведены в экспозицию: карточки проектов описывают площади до 615 и 706 м², а в объявлениях верхняя точка — 359 м².', { bold: true })],
        [txt('Выбор покупателя сводится к сравнению двух домов друг с другом. '), txt(`На одной улице у него два предложения одного девелопера с одинаковой медианой метра ${N.ppmMed} ₽ и одинаковым сроком ключей. Выбор сводится к площади, высоте потолков и этажу.`, { bold: true })],
        [txt(`${N.parking} машино-места на ${N.flats} резиденции. `), txt('В «Люче» 64 места на 46 квартир — 1,39 на квартиру, в «Кло 17» 29 на 26 — 1,12. Для центра внутри Бульварного кольца это высокий показатель: улица здесь парковку не заменяет.', { bold: true })],
        [txt(`Лоты стоят в экспозиции по ${N.days} дней. `), txt('Объявления обоих домов открыты с сентября 2025 года и с тех пор не менялись в цене. За это время дом прошёл путь от котлована до года до сдачи, а прайс остался прежним.', { bold: true })],
        [txt(`${N.metro} метров до узла из четырёх станций метро. `), txt('«Библиотека имени Ленина», «Арбатская», «Александровский сад» и «Боровицкая» связаны переходами: из одной точки доступны четыре линии, включая Сокольническую и Арбатско-Покровскую.', { bold: true })],
        [txt('Свободных участков в квартале не осталось. '), txt('Оба дома встают в сложившуюся застройку: «Люче» карточки описывают как капитальный ремонт существующего здания, «Кло 17» — как новое строительство на 8,1 тыс. м² общей площади.', { bold: true })],
      ]),
      spacer(30),
      note('Расстояние между домами посчитано по прямой между их адресными точками в OpenStreetMap: 55.751093 / 37.605955 и 55.751618 / 37.605522.'),

      // ═══════════════ РАЙОН ═══════════════
      h1('Район Арбат', { br: true }),
      body('Самый маленький район Центрального округа: 2,11 км² к западу от Кремля, между Кремлёвской стеной и Садовым кольцом. Застройка сложилась к началу XX века и с тех пор менялась точечно — свободных участков здесь нет, новые дома встают на место снесённых или реконструируемых.', { after: 190 }),
      factSheet(K.district),
      spacer(240),

      h2('Что работает на район'),
      ...bullets(K.distPro.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(60),

      h2('Что работает против'),
      ...bullets(K.distContra.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(50),
      note('Площадь, население и плотность — по данным на 2025 год. Расстояния от точки между домами посчитаны по прямой.'),

      // ═══════════════ ЗАСТРОЙЩИК ═══════════════
      h1('Застройщик и стадия стройки', { br: true }),
      body('Оба дома ведёт MR Group — компания на рынке с 2003 года, в топ-3 застройщиков Москвы. Элитные проекты вынесены в отдельное направление MR Private, к нему относятся и «Кло 17», и «Люче».', { after: 190 }),
      factSheet(K.builder),
      spacer(240),
      h2('Хронология'),
      dataTable(
        ['Когда', 'Что произошло'],
        K.timeline, [2400, 7238],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(40),
      note('Даты — из публикаций «Ведомостей» и «Новострой-М», из карточек проектов и из самих объявлений: дата публикации видна в выдаче Циан.'),
      spacer(110),
      ...bullets([
        [txt('«Люче» строится на месте снесённого здания. '), txt('Демонтаж надземной части завершён в декабре 2023 года, дальше шли котлован, археологические исследования и усиление соседней застройки: участок в зоне охраны Кремля.', { bold: true })],
        [txt('Портфель застройщика — 52 объекта и 11,8 млн м². '), txt('Выручка от продаж квартир в новостройках за 2024 год — 128,8 млрд ₽ по расчёту Metrium. Оба клубных дома в открытой продаже стоят 22,1 млрд ₽ — около 17 % годовой выручки компании.', { bold: true })],
        [txt('Архитектуру домов вели разные бюро. '), txt('«Кло 17» проектировало Paris Classical Architecture — французская классика с элементами ар-деко; «Люче» — итальянское Archea Associati, оно же делало гранд-пентхаус. Конструктив у обоих домов монолитно-кирпичный.', { bold: true })],
        [txt('Юрлицо застройщика в объявлениях не раскрыто. '), txt('По данным СПАРК-Интерфакс, компанию проекта «Люче» связывают со структурой MR Group и закрытым паевым фондом «МР 102 development». Название и реквизиты застройщика по каждому дому появятся в проектной декларации.', { bold: true })],
        [txt('Между объявлением проекта и сдачей проходит пять лет. '), txt('О «Люче» MR Group объявила в октябре 2021 года, ключи по карточке проекта обещают во II квартале 2027-го. От объявления до сноса старого здания прошло два года, ещё два ушло на нулевой цикл.', { bold: true })],
      ]),

      // ═══════════════ ВИЗУАЛИЗАЦИИ ═══════════════
      h1('«Кло 17»: как это выглядит', { br: true }),
      body('Семиэтажный дом на 26 резиденций в Староваганьковском переулке. Архитектура — французская классика в исполнении бюро Paris Classical Architecture, в составе общих зон велнес-центр с бассейном, спа и приватный сад.', { after: 200 }),
      imagePair('clos_fas.jpg', 'clos_ent.jpg',
        'Фасад со стороны Староваганьковского переулка', 'Входная группа'),
      spacer(70),
      imagePair('clos_yard.jpg', 'clos_lobby.jpg',
        'Приватный сад во дворе', 'Лобби'),
      spacer(70),
      imagePair('clos_liv.jpg', 'clos_view.jpg',
        'Гостиная резиденции', 'Терраса: вид на Храм Христа Спасителя'),
      spacer(70),
      imagePair('clos_fire.jpg', 'clos_stair.jpg',
        'Каминная зона резиденции', 'Лестница общей зоны'),
      spacer(60),
      note('Дом не построен. Всё, что на этой странице, — проектные визуализации из объявлений застройщика, а не фотографии.'),

      h1('«Люче»: как это выглядит', { br: true }),
      body('Восьмиэтажный корпус на 46 квартир в Крестовоздвиженском переулке, потолки от 3,9 до 4,65 м, подземный паркинг на 64 места. В составе инфраструктуры — спа, оздоровительный центр и приватный парк.', { after: 200 }),
      imagePair('luce_fas.jpg', 'luce_air.jpg',
        'Фасад со стороны Крестовоздвиженского переулка', 'Квартал с высоты'),
      spacer(70),
      imagePair('luce_ent.jpg', 'luce_lobby.jpg',
        'Въездная группа вечером', 'Лобби'),
      spacer(70),
      imagePair('luce_yard.jpg', 'luce_terr.jpg',
        'Двор', 'Терраса верхнего уровня'),
      spacer(70),
      imagePair('luce_fire.jpg', 'luce_park.jpg',
        'Каминная зона общих помещений', 'Подземный паркинг'),
      spacer(60),
      note('Дом строится. Кадры — проектные визуализации из объявлений застройщика.'),

      // ═══════════════ ПРАЙС ═══════════════
      h1('Прайс «Кло 17»', { br: true }),
      body('Все лоты, которые сейчас в открытой продаже: этаж, площадь, цена, цена метра и срок, который объявление провисело на Циан. Ссылка ведёт на объявление застройщика.', { after: 190 }),
      statTiles([
        [`${N.nClos} лотов`, 'В продаже из 26 резиденций'],
        [`${N.ppmClos} ₽`, 'Медиана метра'],
        ['227,5 – 659,7', 'Бюджет лота, млн ₽'],
        ['353 дня', 'Медианная экспозиция'],
      ]),
      spacer(220),
      dataTable(
        ['Этаж', 'Площадь, м²', 'Цена, млн ₽', 'Метр, ₽', 'Дней\nв продаже', 'Объявление'],
        K.lotsClos, [1200, 1900, 1900, 1900, 1400, 1338],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Все 17 лотов продаются по договорам долевого участия, без отделки, паркинг подземный. Этажи 1, 6 и 7 в открытой экспозиции не представлены.'),
      spacer(46),
      ...bullets([
        [txt('Линейка построена вокруг трёх типоразмеров: 83–106, 152–154 и 219–220 м². '), txt('Внутри каждого типоразмера площади совпадают до десятых: это один и тот же план, повторённый по этажам. Цена при этом различается на 30 %.', { bold: true })],
        [txt('Самый дешёвый метр — 2,30 млн ₽ на втором этаже, самый дорогой — 3,05 млн на пятом. '), txt('Разница между крайними лотами дома — 33 %, и она целиком объясняется этажом: площади у них одинаковые.', { bold: true })],
        [txt('Шестнадцать из семнадцати объявлений открыты 16 сентября 2025 года. '), txt('Один лот добавлен в апреле 2026-го. За год экспозиции ни одна цена в объявлениях не менялась.', { bold: true })],
        [txt('Ни одного лота на первом, шестом и седьмом этажах. '), txt('Семиэтажный дом продаёт только середину: 2–5-й этажи. Пентхаусы верхнего уровня, которые описывают карточки проекта, в экспозицию не выведены.', { bold: true })],
        [txt('Средний лот дома — 149 м² за 392 млн ₽. '), txt('Это верхняя часть спроса: по данным NF Group, средняя площадь проданного элитного лота в Москве за год упала со 116 до 99 м². Компактных форматов до 80 м² в доме нет вовсе.', { bold: true })],
      ]),

      h1('Прайс «Люче»', { br: true }),
      body('Двадцать шесть лотов от застройщика, включая пентхаус верхнего уровня. Порядок тот же: этаж, площадь, цена, цена метра и срок экспозиции.', { after: 190 }),
      statTiles([
        [`${N.nLuce} лотов`, 'В продаже из 46 квартир'],
        [`${N.ppmLuce} ₽`, 'Медиана метра'],
        ['270,5 – 2 153,7', 'Бюджет лота, млн ₽'],
        ['367 дней', 'Медианная экспозиция'],
      ]),
      spacer(220),
      dataTable(
        ['Этаж', 'Площадь, м²', 'Цена, млн ₽', 'Метр, ₽', 'Дней\nв продаже', 'Объявление'],
        K.lotsLuce, [1200, 1900, 1900, 1900, 1400, 1338],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Двадцать пять лотов продаёт застройщик, один — агент по переуступке. Пентхаус на шестом этаже — единственный лот дороже миллиарда и единственный с метром 6 млн ₽.'),
      spacer(46),
      ...bullets([
        [txt('Две трети предложения — лоты от 190 до 300 м². '), txt('Семнадцать лотов из двадцати шести попадают в этот диапазон, ещё шесть — от 140 до 175 м². Компактных форматов два: по 96,6 м² на третьем и четвёртом этажах.', { bold: true })],
        [txt('Без пентхауса разброс метра укладывается в 30 %. '), txt('От 2 400 000 ₽ до 3 250 000 ₽, и внутри этого коридора цена определяется этажом. Пентхаус на 358,9 м² выпадает из линейки вдвое.', { bold: true })],
        [txt('Двадцать одно объявление из двадцати шести открыто 2 сентября 2025 года. '), txt('Ещё четыре добавлялись поштучно в течение года, последнее — 19 июня 2026-го. Самый старый лот висит 473 дня.', { bold: true })],
      ]),

      // ═══════════════ СТРУКТУРА ЦЕНЫ ═══════════════
      h1('Как устроена цена', { br: true }),
      body('В обоих домах прайс построен одинаково: метр растёт с этажом и почти не зависит от площади лота. Покупатель платит за высоту и вид; метраж на цену метра почти не влияет.', { after: 190 }),
      image('chart_floor.jpg', PX, 239, { after: 30 }),
      caption('В скобках — сколько лотов на этаже. Шестой этаж есть только в «Люче»: это пентхаус.'),
      spacer(46),
      h2('Медиана метра по этажам'),
      dataTable(
        ['Этаж', 'Кло 17,\nлотов', 'Кло 17,\nметр ₽', 'Люче,\nлотов', 'Люче,\nметр ₽'],
        K.floorRows, [1400, 1900, 2200, 1900, 2238],
        { boldFirstCol: true },
      ),
      spacer(36),
      h2('Медиана метра по размеру лота'),
      dataTable(
        ['Площадь лота', 'Лотов', 'Метр, ₽', 'Цена лота,\nмлн ₽', 'Медианный\nэтаж'],
        K.areaRows, [2400, 1600, 2000, 2000, 1638],
        { boldFirstCol: true },
      ),
      spacer(46),
      ...bullets([
        [txt(`От второго этажа к пятому метр прибавляет ${N.floorStep} %. `), txt('В «Кло 17» это 2 350 000 ₽ против 2 975 000 ₽, в «Люче» — 2 500 000 против 3 000 000. Шаг между соседними этажами ровный, по 100–250 тыс. ₽ за метр.', { bold: true })],
        [txt('Размер лота на цену метра почти не влияет. '), txt('Медианы по четырём группам площадей расходятся на 15 %: от 2 600 000 ₽ до 3 000 000 ₽. Разница объясняется этажом — крупные лоты чаще стоят выше. Скидки за объём в этих домах нет.', { bold: true })],
        [txt(`Пентхаус выпадает из линейки вдвое: ${N.penthousePpm} ₽ за метр. `), txt(`Единственный лот на шестом этаже «Люче», ${N.penthouseArea} м² за ${N.penthouse} млн ₽. Следующий по цене лот стоит 871,7 млн — разрыв в 2,5 раза.`, { bold: true })],
        [txt('Цены не двигались с момента публикации. '), txt(`Объявления «Кло 17» открыты 16 сентября 2025 года, большая часть лотов «Люче» — 2 сентября 2025-го. За ${N.days} дней экспозиции ни один лот не сменил цену.`, { bold: true })],
      ]),

      // ═══════════════ БЮДЖЕТ ═══════════════
      h1('Полный бюджет резиденции', { br: true }),
      body('Лоты в обоих домах идут без отделки: покупатель получает оболочку и доводит её сам. Отделка элитного уровня в Москве стоит 300–500 тыс. ₽ за метр; в таблице взята середина вилки — 400 тыс. ₽, метр покупки — медиана обоих домов, 2 650 000 ₽.', { after: 190 }),
      dataTable(
        ['Площадь, м²', 'Покупка, млн ₽', 'Отделка, млн ₽', 'Итого, млн ₽'],
        K.budget, [2100, 2600, 2400, 2538],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Отделка добавляет к бюджету 15 %: при метре покупки 2,65 млн ₽ и отделке 400 тыс. ₽ полная стоимость метра получается 3,05 млн ₽ — уровень пятого этажа в прайсе застройщика.'),
      spacer(46),
      ...bullets([
        [txt('Средняя резиденция «Кло 17» в 149 м² обходится в 455 млн ₽ с отделкой. '), txt('Покупка 395 млн плюс ремонт 60 млн по средней ставке. По нижней границе вилки — 440 млн, по верхней — 470.', { bold: true })],
        [txt('Средний лот «Люче» в 202 м² — 615 млн ₽ с отделкой. '), txt('Покупка 535 млн плюс 81 млн ремонта. Разница со средней резиденцией «Кло 17» — 160 млн ₽, и она целиком в площади.', { bold: true })],
        [txt('Пентхаус доводится до готовности за 144 млн ₽ сверх покупки. '), txt('359 м² по 400 тыс. ₽ за метр. Итоговый бюджет лота — около 2,3 млрд ₽.', { bold: true })],
      ]),

      h2('Рынок элитной первички Москвы, I квартал 2026'),
      dataTable(
        ['Показатель', 'Значение', 'Комментарий'],
        K.market, [3900, 2100, 3638],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(36),
      note('Данные NF Group. Цена предложения по элитному сегменту выросла на 3 % за квартал и на 9 % за год, при этом объём сделок упал на 45 % год к году, а средняя площадь проданного лота — со 116 до 99 м².'),
      spacer(46),
      ...bullets([
        [txt('Полная стоимость метра выводит оба дома в верх сегмента. '), txt('3,05 млн ₽ за метр с отделкой против средних 2,27 млн по элитному предложению Москвы и 3,20 млн по классу делюкс. По цене готового метра дома встают вплотную к делюксу.', { bold: true })],
        [txt('Рынок сегмента сжимается по сделкам и растёт по цене. '), txt('За год объём продаж упал на 45 %, средняя площадь проданного лота — со 116 до 99 м². Средний лот обоих домов — 149 и 202 м², то есть вдвое крупнее того, что рынок сейчас покупает.', { bold: true })],
        [txt('Отделка стоит полутора лет роста сегмента. '), txt('Цена элитного предложения растёт на 9 % в год, ремонт добавляет к бюджету 15 %. При покупке под сдачу или перепродажу этот расход возвращается медленно.', { bold: true })],
        [txt('Ремонт нельзя начать раньше ключей. '), txt('Сдача домов заявлена на III квартал 2026 года, ключи «Люче» — на II квартал 2027-го. Год работ после этого сдвигает реальное новоселье к 2028 году.', { bold: true })],
      ]),

      // ═══════════════ ПЛАНИРОВКИ ═══════════════
      h1('Планировки', { br: true }),
      body('По три лота на дом: самый компактный, средний по площади и самый крупный из тех, что сейчас в продаже. Планировки — из объявлений застройщика.', { after: 190 }),
      photoCards(K.cards, 2),
      spacer(50),
      note('Планировки даны без масштаба и в справке приведены как иллюстрация линейки: полный набор планировок застройщик публикует в объявлениях.'),

      // ═══════════════ КОГОРТА ═══════════════
      h1('Что ещё продаётся рядом', { br: true }),
      body(`В километре вокруг двух домов сейчас продаётся ${C.total} лотов дороже 700 тыс. ₽ за метр — это ${C.groups} адресов и проектов, где в экспозиции хотя бы три лота. Медиана метра по когорте — ${C.med} ₽.`, { after: 180 }),
      image('map_peers.jpg', 600, 509, { after: 30 }),
      caption('Красное — «Кло 17» и «Люче», бронзовое — строящиеся проекты, тёмное — готовые дома.'),
      spacer(40),
      ...bullets([
        [txt(`Дороже двух домов — ${C.above} проекта из ${C.others}. `), txt(`«БРЮСОВ» с медианой 5 738 760 ₽, «Лё Дом» 4 525 445 ₽, «Дом на Хлебном» 3 309 574 ₽, Stella di Mosca 3 249 768 ₽ и «Никитский-6» 3 037 250 ₽. Все они, кроме «Никитского-6», продают единичные лоты в готовых домах.`, { bold: true })],
        [txt('Прямой конкурент по формату и стадии — «Никитский-6». '), txt('Строящийся проект в 356 метрах, 32 лота площадью 100–276 м², медиана метра 3 037 250 ₽ — на 15 % выше. Это единственный сосед сопоставимого объёма, который тоже ещё не сдан.', { bold: true })],
        [txt('Готовое жильё в квартале стоит дешевле строящегося. '), txt('«Афанасьевский» в 529 метрах идёт по 2 028 986 ₽ за метр, «Поварская 20» — по 1 926 782 ₽, «Клубный дом на Арбате» — по 1 450 704 ₽. Въехать туда можно сегодня.', { bold: true })],
        [txt('Ближайший сосед по цене — «Золотой» в 829 метрах. '), txt('Готовый квартал на Якиманке, семь лотов площадью 88–248 м², медиана 2 448 000 ₽ за метр — на 8 % ниже. Разница в цене метра между ним и двумя домами меньше, чем разница между этажами внутри одного дома.', { bold: true })],
      ]),

      h1('Когорта целиком', { br: true }),
      body('Все группы предложения в километре вокруг, отсортированные по медиане метра. «Строится» означает, что дом ещё не сдан.', { after: 190 }),
      dataTable(
        ['Дом или проект', 'Стадия', 'До нас', 'Лотов', 'Площади, м²', 'Метр, ₽'],
        K.cohortRows, [2900, 1300, 1100, 900, 1800, 1638],
        { boldFirstCol: true },
      ),
      spacer(40),
      note('Порог — 700 тыс. ₽ за метр: ниже него предложение в этих кварталах несопоставимо по классу. Адреса с одним-двумя лотами в таблицу не вошли.'),
      spacer(46),
      ...bullets([
        [txt(`Строящегося предложения в когорте немного: ${C.building} лотов из ${C.total}. `), txt('Это «Кло 17», «Люче», «Никитский-6» и «Лё Дом». Всё остальное — готовые дома, где продаются единичные квартиры.', { bold: true })],
        [txt('Два дома дают половину всего строящегося объёма когорты. '), txt('43 лота из 84 строящихся в километре вокруг — это они. По количеству одновременно выставленных лотов в стройке у них в локации только один сопоставимый сосед.', { bold: true })],
        [txt('Медианы групп расходятся в шесть раз. '), txt('От 900 000 ₽ на Волхонке до 5 738 760 ₽ в «БРЮСОВе». Медиана по всей когорте — 1 926 782 ₽, она проходит по «Поварской 20»; оба клубных дома стоят на 38 % выше неё.', { bold: true })],
        [txt('Крупные форматы в когорте — норма. '), txt('У тринадцати групп из двадцати четырёх нижняя граница площадей начинается со 130 м². Лоты меньше 70 м² есть только в трёх домах: The Book, «Театральном Доме» и «Клубном доме на Арбате».', { bold: true })],
      ]),

      h1('Цена метра в когорте', { br: true }),
      body('Двадцать групп с самым дорогим метром. Полоса показывает, насколько широко расходятся цены внутри одного дома: там, где лотов много, разброс достигает двух с половиной раз.', { after: 190 }),
      image('chart_cohort.jpg', PX, 450, { after: 30 }),
      caption('Пунктир — средневзвешенная цена элитного сегмента Москвы, 2,27 млн ₽ за метр.'),
      spacer(46),
      spacer(40),
      ...bullets([
        [txt(`Медиана метра двух домов — ${N.ppmMed} ₽ — выше среднего элитного метра Москвы на ${N.toElite} %. `), txt('При этом внутри когорты они не самые дорогие: пять групп идут выше, и три из них — готовые дома с единичными лотами по 3,2–5,7 млн ₽ за метр.', { bold: true })],
        [txt('Ширина полосы показывает, насколько разнородна группа. '), txt('У «Люче» полоса тянется до 6 млн ₽ из-за пентхауса, у «Кло 17» она узкая: 2,30–3,05 млн. Самые широкие полосы — у готовых домов, где рядом продаются оболочка и квартира с дизайнерским ремонтом.', { bold: true })],
        [txt('Пунктир — средний элитный метр Москвы, 2,27 млн ₽. '), txt('Левее него по медиане остаются двенадцать групп из двадцати: элитная цена в этих кварталах — верхняя часть предложения, а не типичная.', { bold: true })],
        [txt('Самая узкая полоса когорты — у «Никитского-6». '), txt('32 лота укладываются в 2,47–4,68 млн ₽ за метр при медиане 3 037 250 ₽. Это ровно тот же приём, что и у двух клубных домов: единый прайс от застройщика, разложенный по этажам.', { bold: true })],
        [txt('У готовых домов полоса шире вдвое. '), txt('В «Охотном Ряду 2» метр расходится от 1,91 до 4,49 млн ₽, в «Театральном Доме» — от 1,02 до 2,61 млн. На вторичке в одном доме одновременно продаются оболочка, старый ремонт и свежий дизайнерский.', { bold: true })],
      ]),

      // ═══════════════ ПРЕМИЯ ЗА РЕМОНТ ═══════════════
      h1('Сколько рынок просит за ремонт', { br: true }),
      body(`Чтобы понять, окупается ли отделка, прочитаны карточки ${K.fin.read} лотов в готовых домах вокруг: поле «ремонт» живёт только в карточке объявления. Сравнение идёт внутри одного дома — у квартиры с дизайнерским ремонтом и у соседней с любым другим состоянием совпадают локация, год постройки и класс.`, { after: 170 }),
      dataTable(
        ['Дом', 'Дизайн,\nлотов', 'Метр, ₽', 'Прочие,\nлотов', 'Метр, ₽', 'Разница'],
        K.finRows, [2500, 1300, 1800, 1300, 1800, 938],
        { boldFirstCol: true },
      ),
      spacer(36),
      note(`Медиана разницы по ${K.fin.houses} домам — ${K.fin.med} %: заявленный дизайнерский ремонт в цене объявления в этой локации не читается. Заметная надбавка есть только у двух домов из четырнадцати, у одиннадцати разница укладывается в ±10 %.`),
      spacer(46),
      ...bullets([
        [txt(`Дизайнерским назван ${K.fin.designShare} % прочитанных лотов. `), txt(`${K.fin.design} из ${K.fin.read} карточек; ещё ${K.fin.other} — евроремонт, косметика, оболочка или пустое поле. Слово «дизайнерский» в этом сегменте перестаёт различать квартиры: его пишут почти всем.`, { bold: true })],
        [txt(`Разброс по домам — от ${K.fin.lo} % до ${K.fin.hi} %. `), txt('Плюс девяносто пять процентов в «Театральном Доме» и плюс шестьдесят девять в «Охотном Ряду 2» — там оболочки продаются рядом с готовыми квартирами. В остальных домах цену держит сам дом и этаж.', { bold: true })],
        [txt('Для покупателя оболочки это плохая новость. '), txt('Ремонт в 300–500 тыс. ₽ за метр придётся считать расходом на собственное проживание: в цене перепродажи локация его почти не возвращает.', { bold: true })],
      ]),
      spacer(90),
      h2('Что заявлено в карточках объявлений'),
      dataTable(
        ['Состояние квартиры', 'Лотов', 'Медиана метра, ₽'],
        K.kindRows, [3600, 2400, 3638],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Оболочки в этой выборке дороже квартир с ремонтом: они сосредоточены в дорогих домах у Кремля, тогда как дизайнерский ремонт встречается по всей когорте. Поэтому сравнивать состояния корректно только внутри одного дома — как в таблице выше.'),


      // ═══════════════ РЕМОНТЫ ═══════════════
      h1('Какие ремонты продаются рядом', { br: true }),
      body(`Лоты «Кло 17» и «Люче» продаются оболочкой: покупатель получает бетон и делает ремонт сам. Ниже — квартиры с дизайнерским ремонтом, которые прямо сейчас продаются в готовых домах вокруг. Это тот уровень отделки, к которому придётся приводить резиденцию, и та цена, по которой такая квартира уходит на рынке.`, { after: 190 }),
      photoCards(K.repairCards.slice(0, 12), 3),

      photoCards(K.repairCards.slice(12), 3),
      spacer(60),
      note('Фотографии из объявлений о продаже. Все квартиры — в готовых домах в километре вокруг двух проектов; расстояния и число лотов в каждом доме — в таблицах когорты.'),
      spacer(40),
      ...bullets([
        [txt(`Диапазон — от ${K.repairCards[K.repairCards.length - 1].ppm} до ${K.repairCards[0].ppm} ₽ за метр, медиана ${K.repairCardsMed} ₽. `), txt(`Верх задают «БРЮСОВ» у Никитских Ворот и Stella di Mosca на Большой Никитской, низ — Поварская, Арбат и «Театральный Дом». Медиана готовой квартиры с ремонтом на 21 % ниже метра покупки в двух клубных домах, и въехать в неё можно сегодня.`, { bold: true })],
        [txt(`Двадцать одна карточка отобрана из ${K.fin.design} лотов с дизайнерским ремонтом в километре вокруг — сверху по цене метра. `), txt('Все дома готовые: от дореволюционных особняков на Поварской до клубных новостроек последних лет у Кремля.', { bold: true })],
        [txt('Ремонт в этих домах — не косметика. '), txt('Речь о полной отделке с инженерией, встроенной мебелью и авторским проектом: в элитном сегменте это 300–500 тыс. ₽ за метр и около года работ.', { bold: true })],
        [txt('Прямое сравнение для покупателя выглядит так. '), txt('Квартира 149 м² в «Кло 17» с ремонтом обойдётся в 455 млн ₽ и будет готова к 2028 году; сопоставимая по площади квартира с дизайнерским ремонтом в готовом доме рядом стоит 265–450 млн ₽ и продаётся сегодня.', { bold: true })],
      ]),

      // ═══════════════ ОТКРЫТЫЕ ВОПРОСЫ ═══════════════
      h1('Открытые вопросы', { br: true }),
      body('Что в открытых источниках расходится или не раскрыто. Проверять эти пункты нужно по проектной декларации и договору.', { after: 190 }),
      dataTable(
        ['Параметр', 'Карточки проектов', 'Объявления застройщика'],
        K.sourceRows, [3000, 3300, 3338],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(40),
      note('Карточки проектов — novostroy-m.ru, m2.ru, elitnoe.ru, novostroev.ru. Объявления — Циан, срез 04.09.2026. Там, где источники расходятся, в справке стоит значение из объявлений: они обновляются чаще.'),
      spacer(150),
      ...bullets(K.risks.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(120),
      h2('Что проверить до сделки'),
      ...bullets([
        [txt('Проектная декларация в ЕИСЖС. '), txt('Число квартир, этажность, сроки ввода и передачи ключей, состав общего имущества, паркинг. Это единственный документ, который снимает расхождения между карточками и объявлениями.', { bold: true })],
        [txt('Форма договора по конкретному лоту. '), txt('ДДУ с эскроу или иная схема; в объявлениях «Люче» стоит свободная продажа, и что за ней стоит, видно только в договоре.', { bold: true })],
        [txt('Что входит в цену метра. '), txt('Отделка, инженерия, кладовая и машино-место обычно продаются отдельно; в объявлениях обоих домов паркинг ценой не оговорён.', { bold: true })],
        [txt('Актуальность прайса. '), txt('Цены в объявлениях не менялись год. Перед переговорами имеет смысл запросить действующий прайс-лист и список свободных лотов у застройщика.', { bold: true })],
      ]),

      // ═══════════════ ВЫВОДЫ ═══════════════
      h1('Выводы', { br: true }),
      ...bullets([
        [txt(`Два дома одного застройщика на одном квартале держат одну цену: ${N.ppmMed} ₽ за метр по медиане. `), txt(`Совпадение до рубля при разных площадях, потолках и форме продажи означает, что цена задана позиционированием квартала, а не продуктом конкретного дома.`, { bold: true })],
        [txt(`Метр на ${N.toElite} % выше среднего элитного и на ${N.toLux} % ниже делюкса. `), txt(`В когорте из ${C.groups} групп вокруг дороже только ${C.above}, и четыре из них продают единичные лоты в готовых домах. По цене оба дома стоят в верхней трети локации, но не на её вершине.`, { bold: true })],
        [txt(`Цена внутри дома — это цена этажа: +${N.floorStep} % со второго на пятый. `), txt('Площадь лота на метр почти не влияет, скидки за объём нет. Единственное исключение — пентхаус «Люче» с метром 6 млн ₽, вдвое выше линейки.', { bold: true })],
        [txt(`Экспозиция ${N.days} дней без движения цены. `), txt('Объявления открыты в сентябре 2025 года и с тех пор не менялись, хотя дом за это время приблизился к сдаче на год. Ни скидок, ни индексации в открытых данных не видно.', { bold: true })],
        [txt(`${N.n} лота из ${N.flats} резиденций — это меньше двух третей дома. `), txt('Верхние уровни и самые крупные форматы, которые описывают карточки проектов, в открытую продажу не выведены: площади свыше 359 м² в экспозиции отсутствуют.', { bold: true })],
        [txt('Лоты идут оболочкой, полный бюджет выше на треть. '), txt('При отделке 300–500 тыс. ₽ за метр средняя резиденция «Кло 17» в 149 м² обойдётся в 437–467 млн ₽ вместо 392, а средний лот «Люче» в 202 м² — в 654–694 млн ₽ вместо 593.', { bold: true })],
        [txt('Строящийся сосед сопоставимого объёма один — «Никитский-6». '), txt('32 лота в 356 метрах по 3 037 250 ₽ за метр. Всё остальное дороже 3 млн ₽ — это готовые дома с единичными лотами, а весь массив дешевле 2 млн ₽ — вторичка Арбата и Поварской.', { bold: true })],
        [txt('Полный бюджет резиденции на треть выше цены покупки. '), txt('Средняя резиденция «Кло 17» в 149 м² — 455 млн ₽ с отделкой по средней ставке, средний лот «Люче» в 202 м² — 615 млн. Оболочка в обоих домах, ремонт элитного уровня стоит 300–500 тыс. ₽ за метр.', { bold: true })],
        [txt('Локация даёт то, чего не даст продукт. '), txt('251 метр до узла из четырёх станций метро, 240 метров до Дома Пашкова, 986 до Александровского сада. При этом район плотный и административный: Минобороны на Знаменке, зелени 21 гектар на весь Арбат.', { bold: true })],
        [txt('Ключевые расхождения источников касаются формы продажи и отделки. '), txt('Карточки проектов обещают ДДУ с эскроу и дизайнерскую отделку в «Люче», объявления показывают свободную продажу и пустое поле отделки. До проектной декларации это главный непроверяемый пункт.', { bold: true })],
      ]),
      spacer(90),

      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 32, line: 206, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      note('Справка составлена 4 сентября 2026 года по открытым источникам. Цены — из объявлений застройщика на эту дату; офертой они не являются и в договоре могут отличаться.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'Кло_17_и_Люче_аналитика.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
