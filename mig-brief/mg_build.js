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
  [`${N.ha} га`, 'Территория'],
  [`${N.total} млн м²`, 'Объём застройки'],
  [`≈ ${N.flats}`, 'Квартир в проекте', true],
  [`${N.years} лет`, 'Срок договора КРТ'],
];

const SRC = [
  ['Параметры КРТ, объёмы застройки и благоустройства — Стройкомплекс Москвы', 'https://stroi.mos.ru/news/tierritoriiu-byvshiegho-zavoda-mig-blaghoustroiat-v-ramkakh-proiekta-krt'],
  ['Мастер-план бюро «Камень», 2,3 млн м² и 1,4 млн м² жилья — «Московская перспектива»', 'https://mperspektiva.ru/topics/na-meste-zavoda-mig-postroyat-2-3-mln-kv-m-nedvizhimosti/'],
  ['Торги по КРТ, победитель ООО «Энимерози», стартовая цена лота — ИРН', 'https://www.irn.ru/news/154814-capital-group-zastroit-territoriyu-byvshego-zavoda.html'],
  ['Реквизиты и дата регистрации ООО СЗ «Энимерози»', 'https://www.rusprofile.ru/id/1237700471876'],
  ['Класс, число квартир, этажность, площади и статус продаж — брокерские карточки проекта', 'https://mskpremium.ru/mig'],
  ['Capital Group: год основания, портфель, ключевые объекты — карточка застройщика', 'https://www.novostroy-m.ru/kompanii/capital_group'],
  ['Площадь, население и плотность Бегового района — Википедия, оценка на 2025 год', 'https://ru.wikipedia.org/wiki/Беговой'],
  ['Предложение вокруг участка — Циан, срез 22.09.2026: пешая доступность от «Динамо» и новостройки Бегового района', 'https://www.cian.ru/'],
  ['Координаты участка, метро и городских объектов — OpenStreetMap. Картографическая основа — Яндекс Карты', 'https://yandex.ru/maps/'],
];

const doc = new Document({
  creator: 'Информационная справка',
  title: 'Квартал «МИГ» на Ленинградском проспекте',
  description: 'Справка по проекту Capital Group: территория, экономика КРТ, район, застройщик и рынок вокруг участка',
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

      body(`Проект занимает ${N.ha} гектара бывшей производственной площадки «МиГ» между Ленинградским проспектом, Боткинскими проездами и улицами Авиаконструктора Сухого и Маргелова. Участок достался Capital Group на торгах по комплексному развитию территории в августе 2023 года; договор заключён на ${N.years} лет.`),
      body(`По документам Стройкомплекса на площадке построят ${N.total} млн м² недвижимости, из них ${N.housing} млн м² жилья — это около ${N.flats} квартир и средняя квартира в ${N.flatAvg} м². Мастер-план бюро «Камень» собирает застройку вокруг центрального парка с прудом; высотные акценты заявлены до ${N.floors} этажей.`),
      body(`Цен у проекта пока нет: брокерские карточки показывают статус «проектирование» и собирают лист ожидания, старт продаж заявлен на III–IV квартал 2026 года. Поэтому ценовая часть справки опирается на рынок локации: во сколько она оценивает метр сегодня.`),
      body(`В двух километрах вокруг участка в продаже ${C.total} лотов: ${C.new} в строящихся домах и ${C.resale} на вторичке. Медиана метра по когорте — ${C.med} ₽, по стройке — ${C.newMed} ₽, по готовому жилью — ${C.resMed} ₽. Дороже всех идёт ${C.top} с ${C.topPpm} ₽ за метр.`),
      body(`Публичная экономика участка сводится к двум цифрам: ${N.lotPrice} млрд ₽ стартовой цены лота на торгах и оценка инвестиций в проект около ${N.invest} млрд ₽. В пересчёте на метр жилья это ${N.landPer} ₽ за землю и порядка ${N.investPer} ₽ вложений.`),
      body('Дальше — параметры проекта, участок и его окружение, район Беговой, застройщик и хронология, экономика КРТ, визуализации мастер-плана и подробный разбор рынка вокруг: что, где и почём продаётся сегодня.', { after: 220 }),

      image('hero.jpg', PX, 367, { after: 30 }),
      caption('Проектная визуализация квартала: пешеходный бульвар и высотные акценты в центре территории.'),

      // ═══════════════ ПАРАМЕТРЫ ═══════════════
      h1('Что это за проект', { br: true }),
      body('Всё, что известно о проекте из открытых источников на 22 сентября 2026 года. Параметры территории и объёмы — из документов о комплексном развитии; класс, этажность и число квартир — из карточек брокеров, проектной декларации в открытом доступе нет.', { after: 190 }),
      factSheet(K.card),
      spacer(250),
      note('Объём застройки и площадь жилья в источниках расходятся: Стройкомплекс и «Московская перспектива» пишут о 2,3 млн м² и 1,4 млн м² жилья, брокерские карточки — о «более 2,5 млн м²» и 1 млн м². Расхождения сведены в раздел «Открытые вопросы».'),
      spacer(150),
      ...bullets([
        [txt(`${N.ha} гектара — это масштаб района, а не дома. `), txt(`Для сравнения: весь Беговой район занимает 5,56 км², то есть проект — это восьмая часть района. Внутри Третьего кольца площадок такого размера почти не осталось.`, { bold: true })],
        [txt(`${N.flats} квартир при ${N.housing} млн м² жилья дают среднюю квартиру в ${N.flatAvg} м². `), txt('Это формат массового бизнес-класса, а не премиальных резиденций: студии и однокомнатные заявлены от 35 м², семейные — от 60 м².', { bold: true })],
        [txt('Продажи не открыты. '), txt('На 22 сентября 2026 года карточки проекта показывают статус «проектирование», собирают лист ожидания и обещают прайс после официального старта. Ни цен, ни планировок, ни проектной декларации в открытом доступе нет.', { bold: true })],
        [txt(`Город получает ${N.renovation} м² квартир под реновацию и социальные объекты. `), txt('Школы, детские сады, поликлиника, физкультурный комплекс с бассейном и отделение МВД строятся за счёт инвестора и передаются городу — это условие договора КРТ.', { bold: true })],
        [txt('Класс заявлен как бизнес и премиум одновременно. '), txt('Для территории на 19 тысяч квартир это нормально: башни у парка и корпуса вдоль проспекта неизбежно окажутся в разных ценовых линейках. Единого прайса у такого проекта не бывает.', { bold: true })],
        [txt('Отделки в проекте не обещают. '), txt('Карточки указывают продажу без отделки — как и у всех строящихся соседей: в «Славе» и «С5» без отделки продаются все лоты.', { bold: true })],
        [txt('У проекта два адреса в разных источниках. '), txt('Ленинградский проспект, 33 в публикациях и 1-й Боткинский проезд, вл. 7 в брокерских карточках — это разные стороны одной и той же площадки.', { bold: true })],
        [txt('Название проекту дал завод. '), txt('«МИГ» — это имя авиационного предприятия, которое работало на площадке до середины 2020-х годов. Заводская идентичность становится частью маркетинга квартала.', { bold: true })],
        [txt('Соседние девелоперы участвовали в концепции. '), txt('В публикациях о мастер-плане названы ПИК, MR Group, Asterus и ЛСР: у площадки такого размера один застройщик обычно не забирает все очереди.', { bold: true })],
      ]),

      // ═══════════════ ПРОДУКТ ═══════════════
      h1('Что определяет проект', { br: true }),
      body('Шесть параметров, которые отличают эту площадку от остального предложения в локации.', { after: 190 }),
      ...bullets(K.features.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(60),
      note('Параметры территории — из документов о КРТ, числа по квартирам и этажности — из карточек брокеров. Расстояния до метро посчитаны по прямой от центра контура участка.'),
      spacer(46),
      ...bullets([
        [txt('Ближайший сопоставимый по масштабу проект — ЗИЛ. '), txt('Из крупных московских редевелопментов внутри кольцевых магистралей «МИГ» сопоставим именно с промышленными площадками такого класса: те же десятки гектаров, тот же горизонт в четверть века.', { bold: true })],
      ]),
      spacer(150),
      image('sky.jpg', PX, 279, { after: 30 }),
      caption('Проектная визуализация: силуэт квартала в панораме северной части города.'),

      // ═══════════════ УЧАСТОК ═══════════════
      h1('Участок и что вокруг', { br: true }),
      body('Территория выходит на Ленинградский проспект длинной стороной, с юга примыкает к Боткинской больнице, с запада — к улице Авиаконструктора Сухого и кварталам у ЦСКА. Три станции метро стоят по краям площадки.', { after: 180 }),
      image('map_site.jpg', 545, 397, { after: 30 }),
      caption('Красное — центр контура участка, тёмное — городские объекты с расстоянием по прямой.'),
      spacer(40),
      dataTable(
        ['Объект', 'Что это', 'По прямой'],
        K.nearby, [3500, 4300, 1838],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(36),
      spacer(46),
      ...bullets([
        [txt('Боткинская больница примыкает к территории с юга — 358 метров. '), txt('Крупнейший медицинский узел севера Москвы: ММНКЦ имени Боткина и МНИОИ имени Герцена. Для одних это плюс доступности, для других — соседство со скорыми и потоком посетителей.', { bold: true })],
        [txt('Участок выходит на Ленинградский проспект длинной стороной. '), txt('Это и транспортная доступность, и шум: корпуса первой линии окажутся на вылетной магистрали, внутренние — за ними, у центрального парка.', { bold: true })],
        [txt('С запада территория упирается в кварталы у ЦСКА. '), txt('Спорткомплекс ЦСКА, Мегаспорт и парк Ходынское поле в 1,4 км — сложившаяся спортивная и парковая зона, застраивать которую больше нечем.', { bold: true })],
      ]),
      spacer(30),
      note('Расстояния измерены по прямой от центра контура территории. Координаты объектов — OpenStreetMap.'),

      // ═══════════════ ГОРОД ═══════════════
      h1('Где это в городе', { br: true }),
      body(`Участок лежит внутри Третьего транспортного кольца, в ${(5614 / 1000).toFixed(1).replace('.', ',')} км от Кремля по прямой. Ленинградский проспект связывает его с центром в одну сторону и с Ленинградским шоссе и Шереметьево — в другую.`, { after: 180 }),
      image('map_city.jpg', 630, 425, { after: 30 }),
      caption('Квартал относительно центра Москвы: между Третьим кольцом и Ленинградским проспектом.'),
      spacer(46),
      ...bullets([
        [txt(`${N.metro} метров до «Динамо», ${N.metroBkl} до «Петровского парка», ${N.metroCska} до «ЦСКА». `), txt('Замоскворецкая линия и Большая кольцевая сходятся у границ территории; Белорусский вокзал с МЦД-1 и МЦД-4 — в двух километрах.', { bold: true })],
        [txt('Третье транспортное кольцо проходит в полутора километрах южнее. '), txt('Выезд на него — по Беговой улице; на север Ленинградский проспект уходит к Ленинградскому шоссе и аэропорту Шереметьево.', { bold: true })],
        [txt('Спортивное ядро города рядом. '), txt('ВТБ Арена со стадионом «Динамо» в 1,2 км, спорткомплекс ЦСКА и Мегаспорт — в километре к западу, Петровский парк на 22 гектара — в 970 метрах.', { bold: true })],
        [txt('До Кремля 5,6 км по прямой, до Белорусского вокзала — два. '), txt('По меркам Москвы это вторая линия центра: дальше Садового, но внутри ТТК, с прямым выездом на Тверскую через Ленинградский проспект.', { bold: true })],
        [txt('Аэропорт Шереметьево — в получасе по Ленинградке без съездов. '), txt('Для покупателя, которому важна дорога в аэропорт, это одна из сильных сторон северного направления.', { bold: true })],
        [txt('Соседние деловые точки — «Белая площадь» и Москва-Сити. '), txt('До Белорусской два километра, до Сити — шесть. Для локации это означает устойчивый арендный спрос на компактные форматы.', { bold: true })],
      ]),

      // ═══════════════ РАЙОН ═══════════════
      h1('Район Беговой', { br: true }),
      body('Район занимает 5,56 км² на юге Северного округа, между Ленинградским проспектом и Третьим кольцом. Исторически это территория ипподрома, авиационных заводов и жилых кварталов сталинской застройки вдоль проспекта.', { after: 190 }),
      factSheet(K.district),
      spacer(240),

      h2('Что работает на район'),
      ...bullets(K.distPro.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(60),

      h2('Что работает против'),
      ...bullets(K.distContra.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(50),
      note('Площадь, население и плотность — по данным на 2025 год. Расстояния от центра участка посчитаны по прямой. Плотность района вырастет вместе с вводом очередей: 19 тысяч квартир — это примерно 45 тысяч новых жителей, то есть удвоение нынешнего населения Бегового.'),

      // ═══════════════ ЗАСТРОЙЩИК ═══════════════
      h1('Застройщик и хронология', { br: true }),
      body('Проект ведёт Capital Group — один из крупнейших частных девелоперов Москвы, на рынке с 1993 года. Под площадку создано отдельное юридическое лицо, как это обычно и делается в проектах комплексного развития территории.', { after: 190 }),
      factSheet(K.builder),
      spacer(240),
      h2('Хронология'),
      dataTable(
        ['Когда', 'Что произошло'],
        K.timeline, [2300, 7338],
        { boldFirstCol: true, leftCols: [1] },
      ),
      spacer(40),
      note('Даты — из публикаций ИРН, «Ведомостей», «Московской перспективы» и Стройкомплекса, реквизиты юрлица — из открытых реестров.'),
      spacer(110),
      ...bullets([
        [txt('Уставный капитал юрлица проекта — 10 000 ₽. '), txt('Для проектов КРТ это норма: обязательства обеспечиваются договором с городом и эскроу-счетами, а не капиталом компании. Но публичной финансовой истории у «Энимерози» нет — компания создана под эту площадку.', { bold: true })],
        [txt('У самой Capital Group история длинная. '), txt('Более 11 млн м² построенного и строящегося, «Город Столиц» в Москва-Сити, ОКО, Capital Towers, «Бадаевский». Компания имеет статус системообразующего застройщика.', { bold: true })],
        [txt('Участок получен на торгах, а не куплен у собственника. '), txt('Механизм комплексного развития территории означает договор с городом: инвестор обязан снести производство, построить социальные объекты и передать часть жилья под реновацию в оговорённые сроки.', { bold: true })],
        [txt('Мастер-план делало бюро «Камень». '), txt('Планировочный каркас — центральный парк с прудом и пешеходный бульвар как главная общественная ось; высотные акценты собраны в середине территории, а не по периметру.', { bold: true })],
        [txt('К концепции привлекали других девелоперов. '), txt('В публикациях о мастер-плане упоминаются ПИК, MR Group, Asterus и ЛСР. Как распределятся очереди между участниками, в открытых источниках не сказано.', { bold: true })],
        [txt('Проектной декларации пока нет. '), txt('Пока она не опубликована в ЕИСЖС, юридических обязательств по конкретным корпусам у застройщика перед покупателем не возникает: лист ожидания ни к чему не обязывает ни одну из сторон.', { bold: true })],
      ]),

      // ═══════════════ ЭКОНОМИКА ═══════════════
      h1('Экономика участка', { br: true }),
      body('Прайса у проекта нет, но публичных цифр по участку достаточно, чтобы понять порядок величин: сколько стоила площадка и сколько в неё придётся вложить в пересчёте на метр жилья.', { after: 190 }),
      factSheet(K.economy),
      spacer(250),
      ...bullets([
        [txt(`Земля обошлась в ${N.landPer} ₽ на метр будущего жилья. `), txt(`Стартовая цена лота на торгах — ${N.lotPrice} млрд ₽ на ${N.housing} млн м² жилья. На фоне цены метра в локации — ${C.newMed} ₽ — это доли процента. Основные деньги в проектах КРТ уходят на стройку и обязательства перед городом, земля стоит копейки.`, { bold: true })],
        [txt(`Инвестиции оцениваются в ${N.invest} млрд ₽ — около ${N.investPer} ₽ на метр жилья. `), txt('Оценка публиковалась при объявлении итогов торгов и включает всю территорию: жильё, коммерцию, социальные объекты и благоустройство.', { bold: true })],
        [txt(`${N.renovation} м² квартир передаются городу под реновацию. `), txt('Плюс школы, детские сады, поликлиника, ФОК и отделение МВД — всё это зашито в договор и строится за счёт инвестора.', { bold: true })],
        [txt(`Срок договора — ${N.years} лет, до 2049 года. `), txt('Это горизонт, на котором проект живёт целиком; отдельные очереди будут выходить на рынок постепенно, и первые корпуса встанут в окружении действующей стройплощадки.', { bold: true })],
      ]),
      spacer(90),
      h2('Из чего складывается объём застройки'),
      dataTable(
        ['Что', 'Сколько', 'Откуда цифра'],
        K.composition, [3000, 2400, 4238],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(36),
      note('Коммерческая часть считается как остаток объёма за вычетом жилья: отдельной цифры по офисам и сервису в открытых источниках нет.'),
      spacer(46),
      ...bullets([
        [txt('Проект сопоставим по объёму с годовым вводом жилья в округе. '), txt('1,4 млн м² — это масштаб полноценной программы застройки, растянутой на четверть века.', { bold: true })],
        [txt('Цена лота на торгах — не цена земли в рыночном смысле. '), txt('В КРТ город продаёт право застройки вместе с обязательствами: снести производство, построить социальные объекты и передать часть жилья. Поэтому 987,9 млн ₽ за 63,55 гектара внутри ТТК не должны удивлять.', { bold: true })],
      ]),

      // ═══════════════ ВИЗУАЛИЗАЦИИ ═══════════════
      h1('Как это будет выглядеть', { br: true }),
      body('Кадры мастер-плана: бульвар вдоль квартала, площадь у высотного акцента, центральный парк с прудом и силуэт застройки в панораме города.', { after: 200 }),
      imagePair('boulevard.jpg', 'tower.jpg',
        'Пешеходный бульвар квартала', 'Высотный акцент с бульвара'),
      spacer(70),
      imagePair('square.jpg', 'aerial.jpg',
        'Площадь у башни', 'Квартал с высоты'),
      spacer(70),
      imagePair('plan.jpg', 'night.jpg',
        'Центральный парк с прудом в мастер-плане', 'Высотные акценты вечером'),
      spacer(60),
      note('Квартал не построен. Всё, что на этой странице, — проектные визуализации мастер-плана из публикаций о проекте, а не фотографии; архитектура отдельных корпусов может отличаться.'),
      spacer(46),
      ...bullets([
        [txt('Планировочный каркас — парк и бульвар. '), txt('Мастер-план собирает застройку вокруг центральной зелёной оси с прудом; пешеходный бульвар проходит через всю территорию и служит главным общественным пространством квартала.', { bold: true })],
        [txt('Высотные акценты стоят в середине, а не по периметру. '), txt('Вдоль Ленинградского проспекта проектируется фронт средней этажности, башни подняты в глубине территории — так силуэт читается из города, но не давит на улицу.', { bold: true })],
      ]),

      // ═══════════════ КОГОРТА ═══════════════
      h1('Что продаётся вокруг', { br: true }),
      body(`Прайса у проекта нет, поэтому ценовой ориентир даёт сама локация. В двух километрах вокруг участка на Циан ${C.total} лотов в ${C.groups} домах и проектах, где в продаже хотя бы три квартиры.`, { after: 180 }),
      statTiles([
        [`${C.total} лотов`, 'В продаже вокруг участка'],
        [`${C.med} ₽`, 'Медиана метра по когорте', true],
        [`${C.newMed} ₽`, 'Медиана в стройке'],
        [`${C.resMed} ₽`, 'Медиана на вторичке'],
      ]),
      spacer(220),
      image('map_peers.jpg', 560, 426, { after: 30 }),
      caption('Красное — участок, бронзовое — строящиеся проекты, тёмное — готовые дома.'),
      spacer(40),
      spacer(46),
      ...bullets([
        [txt(`Строящегося предложения в локации всего ${C.new} лотов. `), txt(`Это три проекта: «Прайм Парк», «С5» и «Слава». Остальные ${C.resale} лотов — вторичка, от клубных корпусов до домов 1960-х годов вдоль проспекта.`, { bold: true })],
        [txt(`Медиана метра по когорте — ${C.med} ₽. `), txt(`Стройка идёт по ${C.newMed} ₽, готовое жильё — по ${C.resMed} ₽. Разрыв в 10 % — это премия за новый дом в локации, где новых домов мало.`, { bold: true })],
        [txt('Апартаменты занимают отдельную нишу. '), txt(`Из ${C.total} лотов когорты ${C.apart} — нежилой фонд: «Искра-Парк» и Alcon Tower. Их метр дешевле квартир при тех же расстояниях до метро.`, { bold: true })],
      ]),
      spacer(30),
      note(`Отбор: радиус два километра от центра контура участка, дома с тремя и более лотами в продаже. Разброс по когорте — от ${C.lo} до ${C.hi} ₽ за метр.`),

      h1('Новостройки вокруг', { br: true }),
      body(`Строящегося предложения в локации немного: ${C.newGroups} проекта и ${C.new} лотов. Все три стоят вдоль Ленинградского проспекта и выходят на тот же спрос, на который придёт «МИГ».`, { after: 190 }),
      dataTable(
        ['Проект', 'Сдача', 'До участка', 'Лотов', 'Площади, м²', 'Метр, млн ₽', 'Медиана, ₽'],
        K.newRows, [1900, 1300, 1300, 800, 1600, 1400, 1338],
        { boldFirstCol: true },
      ),
      spacer(40),
      ...bullets([
        [txt(`«Прайм Парк» — главный ориентир локации: ${C.new >= 169 ? '169' : ''} лотов в стройке и 86 на перепродаже. `), txt('Медиана метра в строящихся корпусах — 852 771 ₽, в готовых — 1 005 109 ₽. Разница в 18 % показывает, сколько локация прибавляет за готовность.', { bold: true })],
        [txt('«С5» в 1,1 км идёт по 847 320 ₽ за метр. '), txt('107 лотов площадью 40–126 м² — ближайший по формату конкурент будущих корпусов «МИГа»: тот же бизнес-класс на том же проспекте.', { bold: true })],
        [txt('«Слава» у Белорусской — самый дорогой метр в стройке: 985 320 ₽. '), txt('Проект стоит в двух километрах от участка и ближе к центру; он задаёт верхнюю границу того, сколько сейчас просят за строящийся метр в этой части Ленинградского проспекта.', { bold: true })],
      ]),
      spacer(90),
      h2('Чем различаются стройки вокруг'),
      dataTable(
        ['Проект', 'Этажей', 'Сдача', 'Отделка', 'Лотов', 'Медиана\nплощади, м²', 'Медиана\nметра, ₽'],
        K.newDetail, [1700, 1000, 1500, 1700, 900, 1500, 1338],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Отделка — по полю выдачи Циан: в «Прайм Парке» застройщик её не заявляет, в «С5» и «Славе» все лоты идут без отделки. Этажность — максимальная в корпусах, выставленных на продажу.'),
      spacer(46),
      ...bullets([
        [txt('Все три проекта — башни от 41 до 48 этажей. '), txt('Высотная застройка здесь уже норма: «МИГ» с акцентами до 50 этажей встраивается в сложившийся силуэт Ленинградского проспекта, а не ломает его.', { bold: true })],
        [txt('Ближайший срок сдачи в локации — II квартал 2027 года. '), txt('«Слава» и «Прайм Парк» закрывают спрос тех, кому ключи нужны через год-полтора. «С5» с вводом в I квартале 2030 года — конкурент первых очередей «МИГа» по сроку.', { bold: true })],
        [txt('Медианная площадь лота в стройке — 67–105 м². '), txt('Средняя квартира «МИГа» в 74 м² попадает ровно в середину этого диапазона: проект целится в тот же массовый бизнес-формат.', { bold: true })],
        [txt(`На все три стройки приходится ${C.new} лотов. `), txt('Для локации с населением в 43 тысячи человек это узкое предложение — и именно поэтому первые очереди «МИГа» выйдут на ненасыщенный рынок.', { bold: true })],
        [txt('«Прайм Парк» позиционируется выше остальных. '), txt('Клубные корпуса у Петровского парка, медиана готовых лотов — 1 005 109 ₽ за метр. Это верхняя планка локации, к которой будут тянуться башни «МИГа» у центрального парка.', { bold: true })],
        [txt('Разброс площадей в стройке — от 28 до 613 м². '), txt('Локация умеет продавать и компактные студии, и большие видовые квартиры. У проекта на 19 тысяч квартир линейка будет как минимум не уже.', { bold: true })],
      ]),

      h1('Готовое жильё вокруг', { br: true }),
      body(`Вторичка в радиусе двух километров — это ${C.resGroups} домов и ${C.resale} лотов: от клубных корпусов «Прайм Парка» до сталинок вдоль проспекта. Именно с этими ценами будет сравнивать себя покупатель первых очередей.`, { after: 190 }),
      dataTable(
        ['Дом', 'Год', 'До участка', 'Лотов', 'Площади, м²', 'Метр, млн ₽', 'Медиана, ₽'],
        K.resRows, [1900, 1000, 1300, 800, 1600, 1700, 1338],
        { boldFirstCol: true },
      ),
      spacer(40),
      ...bullets([
        [txt('Разрыв между новым и старым фондом — двукратный. '), txt('«Прайм Парк» 2025 года идёт по 1 005 109 ₽ за метр, дома 1960-х на Новой Башиловке и Беговой — по 419–438 тыс. ₽. Между ними — «Царская площадь» и «ВТБ Арена парк» с 712–768 тыс. ₽.', { bold: true })],
        [txt('Апартаменты держат отдельную планку. '), txt('«Искра-Парк» и Alcon Tower — нежилой фонд: 600 000 и 730 769 ₽ за метр при том же расстоянии до метро, что у квартир.', { bold: true })],
        [txt('Ближайший к участку дом — «Ленинградский, 33А» в 500 метрах. '), txt('Пять лотов площадью 17–53 м² по 535 765 ₽ за метр: это тот самый фонд 1960-х, который стоит вдоль проспекта по соседству с будущей стройкой.', { bold: true })],
        [txt('Готовые корпуса «Прайм Парка» дороже своих же строящихся на 18 %. '), txt('1 005 109 против 852 771 ₽ за метр в одном и том же проекте. Столько локация платит за то, чтобы въехать сейчас, а не через два года.', { bold: true })],
        [txt('Вторичка 1960-х держится в коридоре 0,42–0,54 млн ₽ за метр. '), txt('«Новая Башиловка», «Беговая, 32», «Мишина, 12» и «Ленинградский, 33А» — четыре дома, где метр стоит вдвое дешевле новостройки. Это нижняя граница локации.', { bold: true })],
      ]),
      spacer(30),
      spacer(30),
      note('Вторичка отобрана по домам 2015 года и новее плюс всё, что попало в пешую доступность от «Динамо»; Циан перечислил 219 из 349 заявленных лотов — часть похожих объявлений он схлопывает.'),
      spacer(100),
      ...bullets([
        [txt('Для первых очередей «МИГа» вторичка — прямой конкурент. '), txt('Пока корпуса строятся, покупатель сравнивает будущую квартиру с готовой в «Царской площади» или «ВТБ Арена парке» — по 712–768 тыс. ₽ за метр, с ключами сегодня.', { bold: true })],
        [txt('Долгосрочно давление пойдёт в обратную сторону. '), txt('Девятнадцать тысяч новых квартир в одной локации — это несопоставимый с нынешним предложением объём, и по мере ввода очередей он будет ограничивать рост цен на окрестную вторичку.', { bold: true })],
        [txt('Самый старый фонд стоит ближе всех к участку. '), txt('«Ленинградский, 33А» и «Беговая, 32» — дома 1960-х в 500–800 метрах. Именно они первыми почувствуют и стройку под окнами, и последующий приток новых жителей.', { bold: true })],
        [txt('Апартаменты в локации дешевле квартир на 15–20 %. '), txt('«Искра-Парк» по 600 000 ₽ за метр против 712 329 ₽ в «Царской площади» при схожем расстоянии до метро — обычная скидка за нежилой статус.', { bold: true })],
      ]),

      // ═══════════════ ГРАФИКИ ═══════════════
      h1('Цена метра по домам', { br: true }),
      body('Полоса показывает, насколько широко расходятся цены внутри одного дома, засечка — медиана. Пунктир — медиана строящегося предложения локации.', { after: 190 }),
      image('chart_cohort.jpg', PX, 349, { after: 30 }),
      caption('Двенадцать групп предложения в двух километрах вокруг участка.'),
      spacer(46),
      ...bullets([
        [txt('Внутри «Прайм Парка» метр расходится втрое. '), txt('От 0,57 до 1,73 млн ₽ в строящихся корпусах: в одном проекте продаются и компактные лоты, и видовые квартиры верхних этажей. Это типичная картина для башенной застройки, и ровно с таким разбросом выйдет «МИГ».', { bold: true })],
        [txt('Готовое жильё занимает нижнюю половину графика. '), txt('Семь из двенадцати групп — вторичка с медианой ниже 770 тыс. ₽ за метр. Строящееся предложение стоит выше: покупатель платит премию за новый дом.', { bold: true })],
        [txt('Коридор локации — 0,42–1,01 млн ₽ за метр по медианам. '), txt('В этот коридор и придётся встраиваться прайсу первых корпусов: всё, что заметно выше, в локации сегодня представлено только видовыми лотами в «Прайм Парке» и «Славе».', { bold: true })],
        [txt('Самая узкая полоса — у «С5». '), txt('107 лотов укладываются в 0,65–1,10 млн ₽ за метр: единый прайс застройщика, разложенный по этажам и видам, без хвоста дорогих пентхаусов.', { bold: true })],
        [txt('У домов 1960-х полосы почти вырождаются в точку. '), txt('«Беговая, 32» — четыре лота в пределах 0,43–0,44 млн ₽ за метр. В старом фонде цену держит состояние квартиры и площадь, а не вид с этажа.', { bold: true })],
        [txt('Пунктир медианы стройки проходит по 856 520 ₽. '), txt('Выше него держатся только «Прайм Парк» в готовых корпусах и «Слава»; вся вторичка локации остаётся левее.', { bold: true })],
        [txt('Расстояние до участка на цену почти не влияет. '), txt('«Искра-Парк» в 533 метрах идёт по 600 000 ₽ за метр, «Слава» в двух километрах — по 985 320 ₽. В этой локации цену определяет дом и его возраст, а не близость к конкретной точке.', { bold: true })],
      ]),

      h1('Где стоит основная масса предложения', { br: true }),
      body('Распределение всех лотов когорты по цене метра с шагом в сто тысяч рублей. Видно, что рынок локации собран в узком коридоре.', { after: 190 }),
      image('chart_hist.jpg', PX, 257, { after: 30 }),
      caption('Каждый столбец — число лотов в своём ценовом коридоре.'),
      spacer(46),
      h2('Ориентиры рынка'),
      dataTable(
        ['Показатель', 'Значение', 'Комментарий'],
        K.market, [3900, 2100, 3638],
        { boldFirstCol: true, leftCols: [2] },
      ),
      spacer(36),
      spacer(46),
      ...bullets([
        [txt('Две трети предложения стоят между 0,7 и 1,0 млн ₽ за метр. '), txt('358 лотов из 545 в трёх соседних коридорах. Это и есть рабочий диапазон локации: выше и ниже него рынок тонкий.', { bold: true })],
        [txt('Ниже 0,5 млн ₽ за метр продаётся 68 лотов. '), txt('Весь этот объём — дома 1960-х и небольшие квартиры в них. Новостроек в этом коридоре в локации нет.', { bold: true })],
        [txt('Дороже 1,2 млн ₽ за метр — 22 лота из 545. '), txt('Это верхние этажи «Прайм Парка» и «Славы». Премиальный метр в локации существует, но его мало, и он не формирует рынок.', { bold: true })],
        [txt('Премиум-класс Москвы идёт вдвое дороже локации. '), txt('1,6 млн ₽ за метр против 856 520 ₽ в стройке вокруг участка. Беговой — это бизнес-класс с видом на премиальный сегмент, но не он сам.', { bold: true })],
        [txt('Форма распределения говорит о зрелом рынке. '), txt('Один выраженный пик и короткие хвосты: локация давно сложилась, и резких ценовых разрывов между домами в ней нет.', { bold: true })],
      ]),
      spacer(30),
      note('Цены по Москве — данные NF Group за I квартал 2026 года, цены по локации — выдача Циан на 22 сентября 2026 года.'),

      // ═══════════════ БЮДЖЕТ ═══════════════
      h1('Во что обойдётся квартира', { br: true }),
      body(`Прайса у проекта нет, поэтому бюджет считается по цене локации: ${N.ppmLocal} ₽ за метр — медиана строящегося предложения вокруг участка. Отделка бизнес-класса в Москве стоит 150–250 тыс. ₽ за метр, в таблице взята середина вилки.`, { after: 190 }),
      dataTable(
        ['Площадь, м²', 'Покупка, млн ₽', 'Отделка, млн ₽', 'Итого, млн ₽'],
        K.budget, [2100, 2600, 2400, 2538],
        { boldFirstCol: true },
      ),
      spacer(40),
      note(`Отделка добавляет к бюджету 23 %: при метре ${N.ppmLocal} ₽ и отделке ${N.finish} ₽ полная стоимость метра получается около 1,06 млн ₽.`),
      spacer(46),
      ...bullets([
        [txt(`Средняя квартира проекта в ${N.flatAvg} м² по ценам локации стоит ${N.budget74} млн ₽ с отделкой. `), txt('Без отделки — около 63,4 млн ₽. Это ориентир, а не цена проекта: прайс первых корпусов может выйти и выше, и ниже.', { bold: true })],
        [txt('Стартовый формат — 35 м². '), txt('По ценам локации это 30,0 млн ₽ покупки и 7,0 млн отделки. Студии и однокомнатные такого размера в окрестных новостройках сейчас продаются в «Прайм Парке» и «Славе».', { bold: true })],
        [txt('Лоты без отделки — норма для локации. '), txt('В «Славе» все лоты заявлены без отделки; в «МИГе» карточки проекта тоже обещают продажу без отделки. Полный бюджет входа всегда выше цены в прайсе.', { bold: true })],
      ]),
      spacer(90),
      h2('Что даёт тот же бюджет сегодня'),
      dataTable(
        ['Бюджет', 'В стройке', 'Цена, млн ₽', 'В готовом доме', 'Цена, млн ₽'],
        K.equalRows, [1600, 2600, 1500, 2400, 1538],
        { boldFirstCol: true, leftCols: [1, 3] },
      ),
      spacer(36),
      note('Для каждого бюджета взят ближайший по цене лот среди строящихся и среди готовых домов когорты. В готовом доме за те же деньги покупатель получает на 20–40 % больше площади, но в доме, который уже стоит.'),
      spacer(46),
      ...bullets([
        [txt('Тридцать миллионов — это вход в локацию. '), txt('В стройке за эти деньги берут 36 м² в «Прайм Парке», в готовом доме — 65 м² в панельном доме 1960-х на Мишина. Выбор между новым и просторным здесь встаёт уже на стартовом бюджете.', { bold: true })],
        [txt('Восемьдесят миллионов дают 94 м² в стройке и 120 м² в готовом доме. '), txt('Разница в 26 м² — это и есть цена ожидания ключей и нового дома, выраженная в площади.', { bold: true })],
        [txt('Отделка стоит примерно годового роста цены. '), txt('200 тыс. ₽ за метр — это 23 % бюджета покупки. Для квартиры в 74 м² ремонт обойдётся в 14,8 млн ₽ сверх цены в прайсе.', { bold: true })],
        [txt('Ориентир считается по стройке, а не по вторичке. '), txt('Строящийся метр в локации дороже готового на 10 %: покупатель будущей квартиры в «МИГе» будет сравнивать себя именно с «С5» и «Прайм Парком», а не с домами 1960-х.', { bold: true })],
      ]),

      // ═══════════════ РЕМОНТ ═══════════════
      h1('Сколько рынок просит за ремонт', { br: true }),
      body(`Лоты в «МИГе» будут продаваться без отделки, поэтому важно понимать, возвращает ли локация вложенное в ремонт. Для этого прочитаны карточки ${K.fin.read} лотов в готовых домах вокруг: поле «ремонт» живёт только в карточке объявления. Сравнение идёт внутри одного дома — локация, год постройки и класс у обеих групп совпадают.`, { after: 170 }),
      dataTable(
        ['Дом', 'Дизайн,\nлотов', 'Метр, ₽', 'Прочие,\nлотов', 'Метр, ₽', 'Разница'],
        K.finRows, [2500, 1300, 1800, 1300, 1800, 938],
        { boldFirstCol: true },
      ),
      spacer(36),
      note(`Медиана разницы по ${K.fin.houses} домам — ${K.fin.med} %. В двух новых башнях надбавка заметна, в двух домах она отрицательная: заявленный ремонт объясняет цену не везде.`),
      spacer(46),
      ...bullets([
        [txt('В «Прайм Парке» разница доходит до +35 %. '), txt('1 088 889 ₽ за метр с дизайнерским ремонтом против 808 824 ₽ у остальных лотов того же дома. Это самый новый и самый дорогой дом локации: там ремонт делают под продажу и он окупается.', { bold: true })],
        [txt(`Дизайнерским назван ${K.fin.designShare} % прочитанных лотов. `), txt(`${K.fin.design} карточек из ${K.fin.read}; ещё ${K.fin.other} — евроремонт, косметика или оболочка. В домах бизнес-класса слово «дизайнерский» почти перестало различать квартиры.`, { bold: true })],
        [txt('Для покупателя «МИГа» вывод практический. '), txt('Ремонт в 150–250 тыс. ₽ за метр стоит считать расходом на собственное проживание. В новом доме он частично вернётся при перепродаже, в старом фонде — почти нет.', { bold: true })],
        [txt(`Разброс по домам — от ${K.fin.lo} % до ${K.fin.hi} %. `), txt('Плюс тридцать пять процентов в «Прайм Парке» и минус девятнадцать в Alcon Tower. Там, где выборка мала, разница может оказаться случайной: в Alcon Tower на три квартиры с ремонтом приходится одна без него.', { bold: true })],
        [txt('Апартаменты ведут себя иначе, чем квартиры. '), txt('В «Искре-Парке» надбавка +11 %, в Alcon Tower отрицательная. В нежилом фонде цену сильнее определяет сам лот и его метраж, чем состояние отделки.', { bold: true })],
        [txt('Выборка неполная и это стоит держать в уме. '), txt('Циан отдал 102 карточки из 217 запрошенных: чтения карточек он ограничивает. Направление разницы по каждому дому это не меняет, но точность оценки по домам с тремя-четырьмя лотами невысокая.', { bold: true })],
        [txt('Сравнение корректно только внутри дома. '), txt('По всей выборке квартиры без ремонта выглядят дороже, чем с евроремонтом: оболочки сосредоточены в новых башнях, а старый ремонт — в домах попроще. Поэтому цифры считаются по парам внутри одного адреса.', { bold: true })],
      ]),
      spacer(70),
      h2('Разница по площадям'),
      body(`Квартиры с дизайнерским ремонтом в выборке крупнее остальных: медиана ${K.fin.areaDesign} м² против ${K.fin.areaOther} м². Крупный лот в этой локации дороже за метр сам по себе, поэтому надбавку стоит проверить внутри одинаковых метражей.`, { after: 150 }),
      dataTable(
        ['Площадь', 'Дизайн,\nлотов', 'Метр, ₽', 'Прочие,\nлотов', 'Метр, ₽', 'Разница'],
        K.bandRows, [2500, 1300, 1800, 1300, 1800, 938],
        { boldFirstCol: true },
      ),
      spacer(32),
      note('Срез сквозной по всем домам выборки, поэтому в нём смешаны разные адреса. Надбавка видна во всех трёх группах, но точная величина по-прежнему считается по парам внутри одного дома.'),

      h1('Какие ремонты продаются рядом', { br: true }),
      body('Восемнадцать квартир с дизайнерским ремонтом, которые прямо сейчас продаются в готовых домах вокруг участка. Это тот уровень отделки, к которому придётся приводить квартиру в «МИГе», и та цена, по которой такая квартира уходит на рынке.', { after: 190 }),
      photoCards(K.cards.slice(0, 9), 3),

      photoCards(K.cards.slice(9), 3),
      spacer(60),
      note('Фотографии из объявлений о продаже. Все квартиры — в готовых домах в двух километрах от участка; расстояния и число лотов в каждом доме — в таблицах когорты.'),
      spacer(40),
      ...bullets([
        [txt(`Диапазон — от ${K.cards[K.cards.length - 1].ppm} до ${K.cards[0].ppm} ₽ за метр, медиана ${K.cardsMed} ₽. `), txt('Верх задают верхние этажи «Прайм Парка» с видом на город, низ — квартиры в «ВТБ Арена парке». Медиана готовой квартиры с ремонтом выше медианы строящегося метра локации на четверть.', { bold: true })],
        [txt('Почти вся подборка — два дома. '), txt('«Прайм Парк» и «ВТБ Арена парк»: именно там сосредоточен готовый бизнес-класс с дизайнерским ремонтом. В остальных домах локации такие квартиры единичны.', { bold: true })],
        [txt('Площади — от 42 до 202 м². '), txt('Локация умеет продавать и компактные лоты с полной отделкой, и большие видовые квартиры. Средняя квартира «МИГа» в 74 м² попадает в середину этой линейки.', { bold: true })],
        [txt('Ремонт в этих домах — полный цикл. '), txt('Инженерия, встроенная мебель, авторский проект: в бизнес-классе это 150–250 тыс. ₽ за метр и около года работ после получения ключей.', { bold: true })],
      ]),
      spacer(90),
      h2('Что заявлено в карточках объявлений'),
      dataTable(
        ['Состояние квартиры', 'Лотов', 'Медиана метра, ₽'],
        K.kindRows, [3600, 2400, 3638],
        { boldFirstCol: true },
      ),
      spacer(36),
      note('Медианы по всей выборке, без разбивки по домам: именно поэтому лоты без ремонта здесь выглядят дороже квартир с евроремонтом.'),


      // ═══════════════ ОТКРЫТЫЕ ВОПРОСЫ ═══════════════
      h1('Открытые вопросы', { br: true }),
      body('Что в открытых источниках расходится или не раскрыто. Проверять эти пункты нужно по проектной декларации и договору КРТ.', { after: 190 }),
      dataTable(
        ['Параметр', 'Документы и профильные СМИ', 'Карточки брокеров'],
        K.sourceRows, [2600, 3600, 3438],
        { boldFirstCol: true, leftCols: [1, 2] },
      ),
      spacer(40),
      note('Там, где источники расходятся, в справке стоит цифра из документов о КРТ и публикаций Стройкомплекса — она ближе к первоисточнику.'),
      spacer(110),
      ...bullets(K.risks.map(([h, t]) => [txt(h + ' — '), txt(t, { bold: true })])),
      spacer(120),
      h2('Что проверить перед покупкой'),
      ...bullets([
        [txt('Проектную декларацию первой очереди в ЕИСЖС. '), txt('Число квартир и этажность конкретных корпусов, сроки ввода, состав общего имущества, паркинг. Пока её нет, все параметры проекта — маркетинговые.', { bold: true })],
        [txt('Очередность стройки и что будет за окном. '), txt('Территория застраивается 26 лет: важно понимать, какие корпуса и в каком порядке встанут рядом с выбранным домом и когда закончится стройка в его секторе.', { bold: true })],
        [txt('График ввода социальных объектов. '), txt('Школы, детские сады и поликлиника привязаны к очередям; первым жильцам может достаться район без своей социальной инфраструктуры.', { bold: true })],
        [txt('Транспортную схему территории. '), txt('Планируемый проезд между Ленинградским проспектом и улицей Сухого обсуждается публично; его трассировка влияет на тишину во дворах.', { bold: true })],
      ]),

      // ═══════════════ ВЫВОДЫ ═══════════════
      h1('Выводы', { br: true }),
      ...bullets([
        [txt(`${N.ha} гектара внутри ТТК с тремя станциями метро по краям — редкий актив. `), txt(`Территория размером в восьмую часть Бегового района, ${N.metro} метров до «Динамо», ${N.metroBkl} до «Петровского парка» и ${N.metroCska} до «ЦСКА». Такой площадки в этой части Москвы больше нет.`, { bold: true })],
        [txt('Цен у проекта нет, и это главный пробел справки. '), txt('Старт продаж заявлен на III–IV квартал 2026 года, но на 22 сентября карточки собирают лист ожидания. Любые ценовые ориентиры до публикации прайса — это цена локации, а не проекта.', { bold: true })],
        [txt(`Локация оценивает строящийся метр в ${C.newMed} ₽. `), txt(`Коридор медиан по когорте — от 419 463 до 1 005 109 ₽ за метр, основная масса предложения собрана между 0,7 и 1,0 млн. Прайсу первых корпусов придётся встраиваться в этот коридор.`, { bold: true })],
        [txt(`${N.flats} квартир — это тридцать нынешних когорт. `), txt(`Сейчас в двух километрах вокруг продаётся ${C.total} лотов. Проект за свою жизнь выведет на рынок в тридцать с лишним раз больше — и это главный долгосрочный риск для цены вторички в локации.`, { bold: true })],
        [txt('Экономика участка публична только в двух точках. '), txt(`${N.lotPrice} млрд ₽ за лот на торгах и оценка инвестиций в ${N.invest} млрд ₽ — это ${N.landPer} ₽ и ${N.investPer} ₽ на метр жилья соответственно. Земля в проектах КРТ стоит доли процента от цены метра; всё остальное — стройка и обязательства перед городом.`, { bold: true })],
        [txt('Покупатель первых корпусов платит за будущее, а живёт на стройке. '), txt('Договор КРТ заключён на 26 лет, парк и бульвар появятся вместе с центральными очередями. Готовая среда — это вторая половина горизонта проекта.', { bold: true })],
        [txt('Ближайший конкурент по формату — «С5» и строящиеся корпуса «Прайм Парка». '), txt('847 320 и 852 771 ₽ за метр, те же расстояния до метро, тот же Ленинградский проспект. Именно с ними будут сравнивать прайс «МИГа» в первый день продаж.', { bold: true })],
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
      note('Справка составлена 22 сентября 2026 года по открытым источникам. Поквартирного прайса и проектной декларации у проекта на эту дату нет; все ценовые ориентиры — это рынок локации, они оценочные и офертой не являются.'),
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(__dirname, 'Квартал_МИГ_справка.docx');
  fs.writeFileSync(out, buf);
  console.log('written', out, buf.length, 'bytes');
});
