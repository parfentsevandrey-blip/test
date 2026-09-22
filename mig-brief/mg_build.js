const fs = require('fs');
const path = require('path');
const D = require('docx');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  WidthType, AlignmentType, HeadingLevel, BorderStyle, ShadingType, VerticalAlign,
  PageBreak, Header, Footer, PageNumber, ExternalHyperlink, convertMillimetersToTwip,
} = D;
const LR = D.LineRuleType.AUTO;

const K  = JSON.parse(fs.readFileSync(path.join(__dirname, 'mg_tables.json'), 'utf8'));
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
      align: (i === 0 && !o.centerFirst) || (o.leftCols || []).includes(i) ? AlignmentType.LEFT : AlignmentType.CENTER,
      top: { style: BorderStyle.SINGLE, size: 4, color: HEAD },
    })),
  });

  const bodyRows = rows.map((r, ri) => {
    const isTotal = o.totalLast && ri === rows.length - 1;
    return new TableRow({
      children: r.map((c, i) => cell(c, {
        w: widths[i],
        bold: isTotal || (o.boldFirstCol && i === 0) || o.boldCol === i,
        fill: isTotal ? SOFT : (ri % 2 === 1 ? 'FBFAF8' : undefined),
        align: ((i === 0 && !o.centerFirst) || (o.leftCols || []).includes(i)) ? AlignmentType.LEFT : AlignmentType.CENTER,
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
const N = K.nums, C = K.coh, M = K.ms, T = K.stat;

const TILES = [
  [`${N.ha} га`, 'Территория'],
  [`${N.total} млн м²`, 'Объём застройки'],
  [`≈ ${N.flats}`, 'Квартир в проекте', true],
  [`${N.years} лет`, 'Срок договора КРТ'],
];

const SRC = [
  ['Параметры КРТ, объёмы застройки и благоустройства — Стройкомплекс Москвы', 'https://stroi.mos.ru/news/tierritoriiu-byvshiegho-zavoda-mig-blaghoustroiat-v-ramkakh-proiekta-krt'],
  ['Мастер-план бюро «Камень», 2,3 млн м² и 1,4 млн м² жилья — «Московская перспектива»', 'https://mperspektiva.ru/topics/na-meste-zavoda-mig-postroyat-2-3-mln-kv-m-nedvizhimosti/'],
  ['Торги по КРТ, победитель ООО «Энимерози», стартовая цена лота — ИРН', 'https://www.irn.ru/news/154814-capital-group-zastroit-territoriyu-byvshego-zavoda.html'],
  ['Проектная декларация первого дома: 311 квартир, II кв. 2028 — Domovka, 24.07.2026', 'https://domovka.ru/zhiloj-kompleks-na-leningradskom-prospekte-sroki-parametry-i-intriga-zastrojshhika/'],
  ['Прайс проекта МАСТЕРС, ул. Викторенко, 16 — сайт Capital Group, 22.09.2026', 'https://cg-projects.ru/projects/masters'],
  ['Класс, число квартир, этажность, ориентир «от 500 000 ₽/м²» — брокерские карточки проекта (на 22.09.2026 сняты с публикации)', 'https://whitewill.ru/developments/mig'],
  ['Реквизиты и дата регистрации ООО СЗ «Энимерози»', 'https://www.rusprofile.ru/id/1237700471876'],
  ['Capital Group: год основания, портфель, известные объекты — карточка застройщика', 'https://www.novostroy-m.ru/kompanii/capital_group'],
  ['Площадь, население и плотность Бегового района — Википедия, оценка на 2025 год', 'https://ru.wikipedia.org/wiki/Беговой'],
  ['Предложение рядом с участком — Циан, 22.09.2026: новостройки Бегового, Хорошёвского, Савёловского районов и района Аэропорт, вторичка у «Динамо» и «ЦСКА»', 'https://www.cian.ru/'],
  ['Координаты станций метро и картографическая основа — Яндекс Карты, координаты остальных объектов — OpenStreetMap', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'Квартал «МИГ» на Ленинградском проспекте',
  description: 'Справка по проекту Capital Group: территория, цены, экономика КРТ, район, застройщик и рынок рядом с участком',
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
        txt('Квартал «МИГ» · Беговой район, САО · срез 22.09.2026', { size: 14, color: MUTED }),
        txt('\t', {}),
        new TextRun({ children: [PageNumber.CURRENT], font: S.SANS, size: 14, color: MUTED }),
      ],
      tabStops: [{ type: D.TabStopType.RIGHT, position: CONTENT_W }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: LINE, space: 6 } },
    })] }) },
    children: [
      // ═══════════════ СТРАНИЦА 1 ═══════════════
      kicker('Информационная справка · Москва · 22 сентября 2026'),
      p({ children: [txt('Квартал «МИГ»', { font: S.GEO, size: 44, bold: true, color: INK })],
          spacing: { after: 60 } }),
      p({ children: [txt('Capital Group застраивает территорию авиазавода на Ленинградском проспекте', { size: 22, color: MUTED })],
          spacing: { after: 260 } }),

      statTiles(TILES),
      spacer(230),

      body(`Capital Group застраивает ${N.ha} га бывшей производственной площадки «МиГ» между Ленинградским проспектом, Боткинскими проездами и улицами Авиаконструктора Сухого и Маргелова. Участок получен на торгах по комплексному развитию территории (КРТ) в августе 2023 года, договор заключён на ${N.years} лет.`),
      body(`По данным Стройкомплекса здесь построят ${N.total} млн м² недвижимости, из них ${N.housing} млн м² жилья. Брокеры оценивают число квартир в ${N.flats}, средняя квартира получается около ${N.flatAvg} м². Мастер-план бюро «Камень» строится вокруг центрального парка с прудом, самые высокие корпуса заявлены до ${N.floors} этажей.`),
      body(`Продажи не открыты, официального прайса нет. Брокеры называли ориентир от 500 000 ₽ за м². Соседний проект Capital Group МАСТЕРС в ${M.cska} км от «ЦСКА» сейчас продаётся по медиане ${M.med} ₽ за м² со скидкой и ${M.orig} ₽ без неё.`),
      body(`В радиусе 2,5 км от участка продаётся ${C.total} квартир и апартаментов в ${C.groups} домах, где выставлено хотя бы три лота: ${C.new} в строящихся домах и ${C.resale} в готовых. Медиана метра ${C.med} ₽, в новостройках ${C.newMed} ₽, на вторичке ${C.resMed} ₽.`),
      body(`Из публичных денег по участку известны две цифры: стартовая цена лота на торгах ${N.lotPrice} млн ₽ и оценка инвестиций около ${N.invest} млрд ₽. На метр жилья это ${N.landPer} ₽ за землю и около ${N.investPer} ₽ вложений.`, { after: 220 }),

      image('hero.jpg', PX, 367, { after: 30 }),
      caption('Проектная визуализация: бульвар и высотные корпуса в центре квартала.'),

      // ═══════════════ ПАРАМЕТРЫ ═══════════════
      h1('Проект', { br: true }),
      body('Данные на 22 сентября 2026 года. Площадь и объёмы взяты из документов по КРТ, класс, этажность и число квартир из брокерских карточек. Декларация опубликована только по первому дому.', { after: 190 }),
      factSheet(K.card),
      spacer(200),
      note('Стройкомплекс и «Московская перспектива» пишут о 2,3 млн м² и 1,4 млн м² жилья, брокеры о «более 2,5 млн м²» и 1 млн м². Расхождения сведены в раздел «Открытые вопросы».'),
      spacer(120),
      ...bullets([
        [txt(`${N.ha} га, восьмая часть Бегового района. `), txt('Площадь района 5,56 км². Внутри ТТК свободных участков такого размера почти не осталось.', { bold: true })],
        [txt(`Средняя квартира около ${N.flatAvg} м². `), txt('Брокеры заявляют студии и однокомнатные от 35 м², семейные квартиры от 60 м², в центральных башнях пентхаусы с террасами.', { bold: true })],
        [txt('Первый дом, скорее всего, для реновации. '), txt('Декларация июля 2026 года: 311 квартир, 33 479 м², сдача во II кв. 2028. По договору КРТ городу передаётся 30 000 м² квартир, площадь дома почти совпадает.', { bold: true })],
        [txt(`${N.renovation} м² квартир городу. `), txt('Кроме того, инвестор строит и передаёт городу школы, детские сады, поликлинику, ФОК с бассейном и отделение МВД.', { bold: true })],
        [txt('Класс заявлен как бизнес и премиум. '), txt('Башни у парка и корпуса вдоль проспекта, вероятно, будут продаваться по разным ценам.', { bold: true })],
        [txt('Без отделки. '), txt(`Так же продаётся большинство соседних новостроек: ${T.noFinishShare} % строящихся лотов рядом заявлены без отделки.`, { bold: true })],
        [txt('Два адреса. '), txt('В публикациях Ленинградский проспект, 33, в брокерских карточках 1-й Боткинский проезд, вл. 7. Оба относятся к одной площадке.', { bold: true })],
        [txt('ПИК, MR Group, Asterus и ЛСР. '), txt('Эти девелоперы упоминаются в публикациях о концепции. Как очереди распределятся между ними, не сообщается.', { bold: true })],
      ]),
      spacer(120),
      h2('Первый дом по декларации'),
      factSheet(K.firstRows),
      spacer(40),
      note('По публикации Domovka от 24.07.2026 о проектной декларации. В дополнении к публикации дом отнесён к программе реновации.'),

      // ═══════════════ ЦЕНЫ ═══════════════
      h1('Что известно о ценах', { br: true }),
      body('Официального прайса у «МИГа» на 22 сентября 2026 года нет. Есть три точки опоры: брокерский ориентир, декларация первого дома и текущий прайс соседнего проекта того же застройщика.', { after: 190 }),
      dataTable(
        ['Источник', 'Что известно', 'Дата'],
        K.priceSrc, [2700, 5438, 1500],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(120),
      image('chart_bench.jpg', PX, 242, { after: 30 }),
      caption('Фиолетовым отмечен МАСТЕРС, бронзовым новостройки рядом, тёмным готовые дома.'),
      h2(`МАСТЕРС: прайс Capital Group в ${M.dist} км от участка`),
      dataTable(
        ['Тип', 'Квартир', 'Площади, м²', 'Цена, млн ₽', 'Медиана метра, ₽'],
        K.msRows, [2400, 1300, 1900, 1900, 2138],
        { boldFirstCol: true },
      ),
      spacer(36),
      note(`Цены со скидкой «Лучшая цена» до 30.09.2026, скидка ${M.discLo}–${M.discHi} %. Без скидки медиана ${M.orig} ₽ за м². Класс премиум, сдача в IV кв. 2029, секции от ${M.floorsLo} до ${M.floorsHi} этажей.`),
      spacer(60),
      ...bullets([
        [txt('Брокеры называли ориентир от 500 000 ₽ за м². '), txt(`Медиана новостроек рядом ${C.newMed} ₽, ориентир ниже неё на ${T.brokerGap} %. Скорее всего, это цена самых дешёвых лотов.`, { bold: true })],
        [txt(`МАСТЕРС продаётся по медиане ${M.med} ₽ за м². `), txt(`${M.n} квартир от ${M.priceLo} до ${M.priceHi} млн ₽, дом в ${M.cska} км от «ЦСКА». Студии дороже всего за метр, трёхкомнатные дешевле всего.`, { bold: true })],
        [txt(`Оценка для первых корпусов «МИГа»: ${T.estLo}–${T.estHi} млн ₽ за м². `), txt(`От цены МАСТЕРС со скидкой до его прайса без скидки. Стройки в 1,2 км от участка идут по ${T.nearNew} ₽. Застройщик цен не объявлял.`, { bold: true })],
        [txt('Себестоимость по публичным цифрам 185–214 тыс. ₽ за м². '), txt('Первая цифра получается из декларации первого дома (6,2 млрд ₽ на 33 479 м²), вторая из оценки инвестиций во всю территорию.', { bold: true })],
      ]),

      // ═══════════════ ПРОДУКТ ═══════════════
      h1('Особенности площадки', { br: true }),
      body('Чем участок отличается от соседних проектов.', { after: 190 }),
      ...bullets(K.features.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(60),
      note('Параметры территории взяты из документов о КРТ, число квартир и этажность из брокерских карточек. Расстояния до метро по прямой от центра участка.'),
      spacer(150),
      image('sky.jpg', PX, 429, { after: 30 }),
      caption('Проектная визуализация: квартал в панораме северной части города.'),

      // ═══════════════ УЧАСТОК ═══════════════
      h1('Участок и окружение', { br: true }),
      body('Длинной стороной территория выходит на Ленинградский проспект. С юга к ней примыкает Боткинская больница, с запада улица Авиаконструктора Сухого и кварталы у «ЦСКА». Станции метро стоят по краям участка.', { after: 180 }),
      image('map_site.jpg', 545, 397, { after: 30 }),
      caption('Красным отмечен центр участка, тёмным городские объекты с расстоянием по прямой.'),
      spacer(40),
      dataTable(
        ['Объект', 'Что это', 'По прямой'],
        K.nearby, [3500, 4300, 1838],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(80),
      ...bullets([
        [txt('Боткинская больница в 358 м. '), txt('ММНКЦ имени Боткина и МНИОИ имени Герцена. Рядом крупная больница, но и постоянный поток машин скорой помощи и посетителей.', { bold: true })],
        [txt('Ленинградский проспект вдоль всей северо-восточной границы. '), txt('Корпуса первой линии окажутся у магистрали, внутренние будут стоять за ними, у парка.', { bold: true })],
        [txt('Ходынское поле в 1,44 км. '), txt('С запада к участку примыкают кварталы у «ЦСКА»: спорткомплекс ЦСКА, «Мегаспорт», парк на 25 га.', { bold: true })],
      ]),
      spacer(30),
      note('Расстояния по прямой от центра участка. Координаты станций метро по Яндекс Картам, остальных объектов по OpenStreetMap.'),

      // ═══════════════ ГОРОД ═══════════════
      h1('Расположение в городе', { br: true }),
      body('Участок внутри ТТК, в 5,6 км от Кремля по прямой. По Ленинградскому проспекту дорога идёт к центру в одну сторону и к Ленинградскому шоссе и Шереметьеву в другую.', { after: 180 }),
      image('map_city.jpg', 630, 425, { after: 30 }),
      caption('Квартал на карте Москвы.'),
      spacer(46),
      ...bullets([
        [txt(`${N.metro} м до «Динамо», ${N.metroBkl} м до «Петровского парка», ${N.metroCska} м до «ЦСКА». `), txt('Замоскворецкая и Большая кольцевая линии. Белорусский вокзал с МЦД-1 и МЦД-4 в 2,2 км.', { bold: true })],
        [txt('ТТК в 1,5 км к югу. '), txt('Выезд по Беговой улице. На север Ленинградский проспект переходит в Ленинградское шоссе.', { bold: true })],
        [txt('ВТБ Арена в 1,2 км, Петровский парк в 970 м. '), txt('Спорткомплекс ЦСКА и «Мегаспорт» примерно в километре к западу.', { bold: true })],
        [txt('5,6 км до Кремля, 2,2 км до Белорусского вокзала. '), txt('Дальше Садового кольца, но внутри ТТК. До Тверской прямая дорога по Ленинградскому проспекту.', { bold: true })],
        [txt('До Шереметьева по Ленинградскому шоссе без съездов. '), txt('Вне часа пик около получаса езды.', { bold: true })],
        [txt(`Белая площадь в ${N.belaya} км, Москва-Сити в ${N.city} км. `), txt('Офисы рядом поддерживают спрос на аренду небольших квартир.', { bold: true })],
      ]),

      // ═══════════════ РАЙОН ═══════════════
      h1('Район Беговой', { br: true }),
      body('Район площадью 5,56 км² на юге Северного округа, между Ленинградским проспектом и ТТК. Здесь ипподром, бывшие авиационные заводы и сталинские дома вдоль проспекта.', { after: 190 }),
      factSheet(K.district),
      spacer(240),

      h2('Плюсы'),
      ...bullets(K.distPro.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(60),

      h2('Минусы'),
      ...bullets(K.distContra.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(50),
      note('Площадь, население и плотность на 2025 год. 19 000 квартир дадут примерно 45 000 новых жителей, население района вырастет примерно вдвое.'),

      // ═══════════════ ЗАСТРОЙЩИК ═══════════════
      h1('Застройщик и хронология', { br: true }),
      body('Проект ведёт Capital Group, девелопер работает в Москве с 1993 года. Под площадку создано отдельное юрлицо, как обычно делают в проектах КРТ.', { after: 190 }),
      factSheet(K.builder),
      spacer(240),
      h2('Хронология'),
      dataTable(
        ['Когда', 'Что произошло'],
        K.timeline, [2300, 7338],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(40),
      note('Даты по публикациям ИРН, «Ведомостей», «Московской перспективы», Domovka и Стройкомплекса, реквизиты юрлица по открытым реестрам.'),
      spacer(110),
      ...bullets([
        [txt('Уставный капитал юрлица 10 000 ₽. '), txt('Для проектов КРТ это обычная практика, обязательства обеспечены договором с городом и эскроу-счетами. Своей финансовой истории у «Энимерози» нет, компания создана в 2023 году под эту площадку.', { bold: true })],
        [txt('У Capital Group больше 11 млн м² построенного и строящегося. '), txt('«Город Столиц» в Москва-Сити, ОКО, Capital Towers, «Бадаевский». Компания имеет статус системообразующего застройщика.', { bold: true })],
        [txt('Участок получен на торгах. '), txt('По договору КРТ инвестор сносит производство, строит социальные объекты и передаёт часть жилья под реновацию в согласованные сроки.', { bold: true })],
        [txt('Рядом у застройщика уже идут продажи. '), txt(`МАСТЕРС на ул. Викторенко, 16, в ${M.dist} км от участка: премиум, ${M.n} квартир в продаже, сдача в IV кв. 2029.`, { bold: true })],
        [txt('Проектная декларация есть только по первому дому. '), txt('По остальным корпусам лист ожидания не создаёт обязательств ни у застройщика, ни у покупателя.', { bold: true })],
      ]),

      // ═══════════════ ЭКОНОМИКА ═══════════════
      h1('Экономика участка', { br: true }),
      body('Прайса нет, но публичные цифры по участку позволяют оценить затраты на метр жилья.', { after: 190 }),
      factSheet(K.economy),
      spacer(250),
      ...bullets([
        [txt(`Земля: ${N.landPer} ₽ на метр жилья. `), txt(`Стартовая цена лота ${N.lotPrice} млн ₽ на ${N.housing} млн м² жилья. ${T.landShare} % медианной цены метра в новостройках рядом.`, { bold: true })],
        [txt(`Инвестиции около ${N.invest} млрд ₽, примерно ${N.investPer} ₽ на метр жилья. `), txt('Оценка приводилась при объявлении итогов торгов и включает жильё, коммерцию, социальные объекты и благоустройство.', { bold: true })],
        [txt('Первый дом: 6,2 млрд ₽ на 33 479 м². '), txt('Около 185 000 ₽ на м², близко к средней оценке по всей территории.', { bold: true })],
        [txt(`Договор на ${N.years} лет, до 2049 года. `), txt('Очереди будут выходить на рынок постепенно, первые корпуса достроят раньше, чем закончится стройка вокруг них.', { bold: true })],
      ]),
      spacer(90),
      h2('Состав застройки'),
      dataTable(
        ['Что', 'Сколько', 'Откуда цифра'],
        K.composition, [3000, 2400, 4238],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(36),
      note('Коммерческая часть посчитана как разница между общим объёмом и жильём. Отдельной цифры по офисам и сервису нет.'),
      spacer(46),
      ...bullets([
        [txt('1,4 млн м² жилья за 26 лет. '), txt('В среднем около 54 000 м² в год.', { bold: true })],
        [txt('Цена лота включает обязательства. '), txt(`Город продаёт право застройки вместе с обязанностью снести производство, построить социальные объекты и передать часть жилья. Отсюда ${N.lotPrice} млн ₽ за 63,55 га внутри ТТК.`, { bold: true })],
      ]),

      // ═══════════════ ВИЗУАЛИЗАЦИИ ═══════════════
      h1('Визуализации мастер-плана', { br: true }),
      body('Бульвар, площадь у башни, центральный парк с прудом, вид на квартал сверху.', { after: 200 }),
      imagePair('boulevard.jpg', 'tower.jpg',
        'Пешеходный бульвар квартала', 'Высотный корпус с бульвара'),
      spacer(70),
      imagePair('square.jpg', 'aerial.jpg',
        'Площадь у башни', 'Квартал с высоты'),
      spacer(70),
      imagePair('plan.jpg', 'night.jpg',
        'Центральный парк с прудом в мастер-плане', 'Высотные корпуса вечером'),
      spacer(60),
      note('Квартал не построен. На странице проектные визуализации из публикаций о мастер-плане, архитектура отдельных корпусов может отличаться.'),
      spacer(46),
      ...bullets([
        [txt('Парк с прудом в центре, бульвар через всю территорию. '), txt('Бульвар задуман как главная пешеходная улица квартала.', { bold: true })],
        [txt('Башни в середине участка. '), txt('Вдоль Ленинградского проспекта проектируется средняя этажность, высотные корпуса стоят в глубине территории.', { bold: true })],
      ]),

      // ═══════════════ КОГОРТА ═══════════════
      h1('Предложение рядом', { br: true }),
      body(`В радиусе 2,5 км от участка на Циан ${C.total} лотов в ${C.groups} домах, где продаётся хотя бы три квартиры или апартаментов. В выборке новостройки Бегового, Хорошёвского, Савёловского районов и района Аэропорт, включая сданные корпуса, и вторичка у «Динамо» и «ЦСКА».`, { after: 180 }),
      statTiles([
        [`${C.total} лотов`, 'В продаже рядом'],
        [`${C.med} ₽`, 'Медиана метра', true],
        [`${C.newMed} ₽`, 'Медиана в новостройках'],
        [`${C.resMed} ₽`, 'Медиана в готовых домах'],
      ]),
      spacer(200),
      image('map_peers.jpg', 560, 467, { after: 30 }),
      caption('Красным отмечен участок, бронзовым строящиеся дома, тёмным готовые. Номера те же, что в таблицах и на графике.'),
      spacer(40),
      ...bullets(T.cohortBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(30),
      note(`Цены предложения на 22 сентября 2026 года. Разброс от ${C.lo} до ${C.hi} ₽ за м².`),

      h1('Новостройки рядом', { br: true }),
      body(`${C.new} лотов в ${C.newGroups} строящихся проектах. С ними покупатель будет сравнивать первые корпуса «МИГа».`, { after: 190 }),
      dataTable(
        ['№', 'Проект', 'Сдача', 'До участка', 'Лотов', 'Площади, м²', 'Метр, млн ₽', 'Медиана, ₽'],
        K.newRows, [500, 1800, 1200, 1200, 800, 1400, 1400, 1338],
        { centerFirst: true, boldCol: 1, leftCols: [1] },
      ),
      spacer(40),
      ...bullets(T.newBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(90),
      h2('Этажность, сроки и отделка'),
      dataTable(
        ['№', 'Проект', 'Этажей', 'Сдача', 'Отделка', 'Лотов', 'Медиана\nплощади, м²', 'Медиана\nметра, ₽'],
        K.newDetail, [500, 1700, 900, 1300, 1500, 800, 1500, 1438],
        { centerFirst: true, boldCol: 1, leftCols: [1] },
      ),
      spacer(36),
      note('Отделка по объявлениям: указан самый частый вариант в проекте. Этажность максимальная среди корпусов в продаже.'),
      spacer(46),
      ...bullets(T.detailBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),

      h1('Готовые дома рядом', { br: true }),
      body(`${C.resale} лотов в ${C.resGroups} готовых домах рядом с участком, от новых башен до домов 1920–1960-х годов.`, { after: 190 }),
      dataTable(
        ['№', 'Дом', 'Год', 'До участка', 'Лотов', 'Площади, м²', 'Метр, млн ₽', 'Медиана, ₽'],
        K.resRows, [500, 1900, 800, 1200, 800, 1400, 1500, 1538],
        { centerFirst: true, boldCol: 1, leftCols: [1] },
      ),
      spacer(40),
      ...bullets(T.resBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(30),
      note('Год по данным объявлений, для домов из нескольких корпусов указан самый поздний.'),
      spacer(120),
      h2('Готовые дома по году постройки'),
      dataTable(
        ['Год постройки', 'Домов', 'Лотов', 'Медиана площади, м²', 'Медиана метра, ₽'],
        K.ageRows, [2600, 1400, 1400, 2100, 2138],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Дома без указанного года в разбивку не вошли.'),

      h1('Дома у «ЦСКА»', { br: true }),
      body(`Дома не дальше 1,5 км от станции «ЦСКА», которые к ней ближе, чем к «Динамо»: Ходынское поле, улица Полины Осипенко, Ленинградский проспект в сторону «Аэропорта».`, { after: 180 }),
      image('map_cska.jpg', 560, 373, { after: 30 }),
      caption('Дома у «ЦСКА». Красным отмечен центр участка «МИГ».'),
      spacer(40),
      dataTable(
        ['№', 'Дом', 'Стадия', 'До «ЦСКА»', 'До участка', 'Лотов', 'Медиана, ₽'],
        K.cskaRows, [500, 2400, 1300, 1300, 1300, 1000, 1838],
        { centerFirst: true, boldCol: 1, leftCols: [1] },
      ),
      spacer(40),
      ...bullets(T.cskaBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(30),
      note('Расстояния по прямой от дома до станции и до центра участка.'),

      // ═══════════════ ГРАФИКИ ═══════════════
      h1('Цена метра по домам', { br: true }),
      body('Полоса показывает разброс цен внутри дома, засечка медиану. Пунктир медиана новостроек рядом.', { after: 190 }),
      image('chart_cohort.jpg', PX, T.chartH, { after: 30 }),
      caption('Номера совпадают с таблицами и картой.'),
      spacer(46),
      ...bullets(T.chartBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),

      h1('Распределение цен', { br: true }),
      body('Все лоты рядом с участком по цене метра, шаг 100 тыс. ₽.', { after: 190 }),
      image('chart_hist.jpg', PX, 257, { after: 30 }),
      caption('Столбец показывает число лотов в диапазоне цены.'),
      spacer(46),
      h2('Ориентиры рынка'),
      dataTable(
        ['Показатель', 'Значение', 'Комментарий'],
        K.market, [3900, 2100, 3638],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(80),
      ...bullets(T.histBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(30),
      note('Цены по Москве по данным NF Group за I квартал 2026 года, цены рядом с участком по Циан на 22 сентября 2026 года.'),

      // ═══════════════ БЮДЖЕТ ═══════════════
      h1('Бюджет покупки', { br: true }),
      body(`Прайса нет, поэтому бюджет посчитан по медиане строящихся домов рядом: ${N.ppmLocal} ₽ за м². Отделка бизнес-класса в Москве стоит 150–250 тыс. ₽ за м², в расчёте взято 200 тыс.`, { after: 190 }),
      dataTable(
        ['Площадь, м²', 'Покупка, млн ₽', 'Отделка, млн ₽', 'Итого, млн ₽'],
        K.budget, [2100, 2600, 2400, 2538],
        { boldFirstCol: true },
      ),
      spacer(40),
      note(`Отделка добавляет к цене ${T.finishPct} %. Полная стоимость метра около ${T.fullPpm} млн ₽.`),
      spacer(46),
      ...bullets([
        [txt(`Квартира ${N.flatAvg} м² с отделкой: ${N.budget74} млн ₽. `), txt(`Без отделки ${T.buy74} млн ₽. По цене МАСТЕРС со скидкой та же квартира стоила бы ${T.ms74} млн ₽ без отделки.`, { bold: true })],
        [txt('Студия 35 м². '), txt(`${T.buy35} млн ₽ покупки и ${T.fin35} млн ₽ отделки. Студии в МАСТЕРС сейчас стоят от ${T.msStudioLo} млн ₽.`, { bold: true })],
        [txt(`${T.noFinishShare} % строящихся лотов рядом продаются без отделки. `), txt('Полный бюджет входа почти везде выше цены в прайсе.', { bold: true })],
      ]),
      spacer(90),
      h2('Что можно купить рядом за те же деньги'),
      dataTable(
        ['Бюджет', 'В стройке', 'Цена, млн ₽', 'В готовом доме', 'Цена, млн ₽'],
        K.equalRows, [1600, 2600, 1500, 2400, 1538],
        { boldFirstCol: true, leftCols: [1, 3] },
      ),
      spacer(36),
      note('Для каждого бюджета взят ближайший по цене лот среди строящихся и среди готовых домов рядом.'),
      spacer(46),
      ...bullets(T.equalBullets.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(90),
      h2('Цены по типам квартир'),
      dataTable(
        ['Тип', 'Лотов\nрядом', 'Площадь\nрядом, м²', 'Цена рядом,\nмлн ₽', 'МАСТЕРС,\nмлн ₽', 'Площадь\nМАСТЕРС, м²'],
        K.roomRows, [2200, 1200, 1500, 1600, 1500, 1638],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Медианы по строящимся домам в радиусе 2,5 км и по прайсу МАСТЕРС со скидкой.'),

      // ═══════════════ РЕМОНТ ═══════════════
      h1('Надбавка за ремонт', { br: true }),
      body(`«МИГ» будет продаваться без отделки. Чтобы понять, окупается ли ремонт, цены квартир с дизайнерским ремонтом сравнены с остальными квартирами в тех же готовых домах у «Динамо».`, { after: 170 }),
      dataTable(
        ['Дом', 'Дизайн,\nлотов', 'Метр, ₽', 'Прочие,\nлотов', 'Метр, ₽', 'Разница'],
        K.finRows, [2500, 1300, 1800, 1300, 1800, 938],
        { boldFirstCol: true },
      ),
      spacer(36),
      note(`Медиана по ${K.fin.houses} домам ${K.fin.med} %. В двух домах квартиры с ремонтом стоят дешевле остальных.`),
      spacer(46),
      ...bullets([
        [txt('«Прайм Парк»: +35 %. '), txt('1 088 889 ₽ за м² с дизайнерским ремонтом, 808 824 ₽ у остальных квартир того же дома. Самый новый и дорогой дом из пяти.', { bold: true })],
        [txt(`«Дизайнерский ремонт» указан в ${K.fin.designShare} % объявлений. `), txt(`${K.fin.design} из ${K.fin.read}, ещё ${K.fin.other} с евроремонтом, косметическим ремонтом или без ремонта. Пометка стоит почти везде и мало что говорит о квартире.`, { bold: true })],
        [txt('Ремонт стоит 150–250 тыс. ₽ за м². '), txt('В новом доме часть этих денег возвращается при продаже, в «Царской площади» и Alcon Tower не возвращается.', { bold: true })],
        [txt(`Разброс от ${K.fin.lo} % до ${K.fin.hi} %. `), txt('В Alcon Tower на три квартиры с ремонтом одна без него, поэтому −19 % может быть случайностью.', { bold: true })],
        [txt('Апартаменты. '), txt('В «Искре-Парке» +11 %, в Alcon Tower −19 %. У нежилого фонда цена больше зависит от площади и этажа.', { bold: true })],
        [txt('Сравнивать надо внутри дома. '), txt('Если сложить все дома вместе, квартиры без ремонта окажутся дороже квартир с евроремонтом: без отделки продаются в новых башнях, а старый ремонт встречается в домах попроще.', { bold: true })],
      ]),
      spacer(70),
      h2('Разница по площадям'),
      body(`Квартиры с дизайнерским ремонтом в среднем больше: медиана ${K.fin.areaDesign} м² против ${K.fin.areaOther} м². Большие квартиры здесь дороже за метр, поэтому разница проверена отдельно для каждой группы площадей.`, { after: 150 }),
      dataTable(
        ['Площадь', 'Дизайн,\nлотов', 'Метр, ₽', 'Прочие,\nлотов', 'Метр, ₽', 'Разница'],
        K.bandRows, [2500, 1300, 1800, 1300, 1800, 938],
        { boldFirstCol: true },
      ),
      spacer(32),
      note('В этом срезе дома смешаны. Надбавка есть во всех трёх группах, но оценка по отдельным домам точнее. По домам с тремя-четырьмя квартирами она приблизительная.'),

      h1('Квартиры с ремонтом рядом', { br: true }),
      body('Восемнадцать квартир с дизайнерским ремонтом, которые сейчас продаются в готовых домах у «Динамо». По ним видно, сколько стоит готовая квартира с отделкой того уровня, который понадобится в «МИГе».', { after: 190 }),
      photoCards(K.cards.slice(0, 9), 3),

      photoCards(K.cards.slice(9), 3),
      spacer(60),
      note('Фотографии из объявлений о продаже.'),
      spacer(40),
      ...bullets([
        [txt(`От ${K.cards[K.cards.length - 1].ppm} до ${K.cards[0].ppm} ₽ за м², медиана ${K.cardsMed} ₽. `), txt(`Дороже всего верхние этажи «Прайм Парка», дешевле всего квартиры в «ВТБ Арена парке». Медиана выше медианы новостроек рядом на ${T.cardsVsNew} %.`, { bold: true })],
        [txt('Почти все квартиры из двух домов. '), txt('«Прайм Парк» и «ВТБ Арена парк». В остальных готовых домах у «Динамо» такие предложения единичны.', { bold: true })],
        [txt('Площади от 42 до 202 м². '), txt('Средняя квартира «МИГа» (74 м²) в середине этого диапазона.', { bold: true })],
        [txt('Ремонт такого уровня стоит 150–250 тыс. ₽ за м². '), txt('Инженерия, встроенная мебель, авторский проект и около года работ после получения ключей.', { bold: true })],
      ]),
      spacer(90),
      h2('Состояние квартир в объявлениях'),
      dataTable(
        ['Состояние квартиры', 'Лотов', 'Медиана метра, ₽'],
        K.kindRows, [3600, 2400, 3638],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Медианы по всем домам вместе, поэтому квартиры без ремонта здесь дороже квартир с евроремонтом.'),


      // ═══════════════ ОТКРЫТЫЕ ВОПРОСЫ ═══════════════
      h1('Открытые вопросы', { br: true }),
      body('Где открытые источники расходятся или молчат. Проверять по проектной декларации и договору КРТ.', { after: 190 }),
      dataTable(
        ['Параметр', 'Документы и профильные СМИ', 'Карточки брокеров'],
        K.sourceRows, [2600, 3600, 3438],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(40),
      note('Где источники расходятся, в справке взята цифра из документов о КРТ и сообщений Стройкомплекса.'),
      spacer(110),
      ...bullets(K.risks.map(([h, t]) => [txt(h + '. '), txt(t, { bold: true })])),
      spacer(120),
      h2('Что проверить перед покупкой'),
      ...bullets([
        [txt('Проектную декларацию нужного корпуса в ЕИСЖС. '), txt('Число квартир, этажность, сроки ввода, паркинг. До её публикации параметры корпуса известны только из рекламы.', { bold: true })],
        [txt('Порядок очередей. '), txt('Какие корпуса и когда будут строиться рядом с выбранным домом, когда закончится стройка в этой части территории.', { bold: true })],
        [txt('Сроки социальных объектов. '), txt('Школы, сады и поликлиника привязаны к очередям. Первые жильцы могут несколько лет обходиться без них.', { bold: true })],
        [txt('Транспортную схему. '), txt('От трассы проезда между Ленинградским проспектом и улицей Сухого зависит, будет ли тихо во дворах.', { bold: true })],
      ]),

      // ═══════════════ ВЫВОДЫ ═══════════════
      h1('Выводы', { br: true }),
      ...bullets(T.conclusions.map(([a, b]) => [txt(a), txt(b, { bold: true })])),
      spacer(90),

      kicker('Источники', INK),
      ...SRC.map(([label, url]) => p({
        children: url
          ? [txt('—   ', { color: BRONZE, bold: true }), txt(label + ' — ', { size: 16, color: MUTED }),
             new ExternalHyperlink({ children: [txt(url, { size: 16, color: '2C5FA8' })], link: url })]
          : [txt('—   ', { color: BRONZE, bold: true }), txt(label, { size: 16, color: MUTED })],
        spacing: { after: 32, line: 206, lineRule: LR }, indent: { left: 170, hanging: 170, right: MEASURE },
      })),
      note('Справка составлена 22 сентября 2026 года по открытым источникам. Прайса у проекта на эту дату нет. Цены в справке относятся к соседним домам и проекту МАСТЕРС, это оценка, а не оферта.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'Квартал_МИГ_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
