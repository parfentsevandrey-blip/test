/* Два листа А4: карта конкурентов и состав их вторых этажей.
   Отдельный документ, вёрстка та же, что у основного отчёта. */
const fs = require('fs');
const {Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType,
       AlignmentType, BorderStyle, ShadingType, VerticalAlign, ImageRun, PageBreak,
       ExternalHyperlink, LineRuleType} = require('docx');

const C = {ink:'1E1C19', navy:'23303A', gold:'963F26', grey:'6E6860', line:'BCB4A8',
           soft:'F7F4EF', warn:'F0E6D8', hair:'E4DED4', mid:'8C8479', S:'2F4A61', D:'8E3A22'};
const F = 'PT Serif', FS = 'PT Sans', FN = 'PT Sans Narrow';
const pt = n => Math.round(n * 2);
const LR = LineRuleType.AUTO;
const PW = 10106;
const META = JSON.parse(fs.readFileSync('img_meta.json', 'utf8'));
const MALLS = JSON.parse(fs.readFileSync('rivals.json', 'utf8'));
const body = [];
const add = x => Array.isArray(x) ? body.push(...x) : body.push(x);

const H1 = (t, brk) => new Paragraph({spacing:{before:0, after:180}, pageBreakBefore: !!brk,
  border:{bottom:{style:BorderStyle.SINGLE, size:10, color:C.gold, space:6}},
  children:[new TextRun({text:t, bold:true, size:pt(16), color:C.navy, font:F})]});

const H3 = t => new Paragraph({spacing:{before:240, after:110},
  children:[new TextRun({text:t, bold:true, size:pt(9), color:C.gold, font:FS,
                         allCaps:true, characterSpacing:26})]});

const P = (t, o={}) => new Paragraph({alignment:AlignmentType.JUSTIFIED,
  spacing:{after:o.after ?? 140, line:276, lineRule:LR},
  children:[new TextRun({text:t, size:pt(o.size ?? 9.6), color:C.ink, font:F})]});

const SRC = t => new Paragraph({alignment:AlignmentType.LEFT, spacing:{before:60, after:120},
  indent:{right:520}, border:{top:{style:BorderStyle.SINGLE, size:2, color:C.hair, space:6}},
  children:[new TextRun({text:t, size:pt(7.6), color:C.grey, font:FN})]});

function IMG(name, maxW, cap){
  const [w,h] = META[name]; const W = Math.min(maxW, w); const H = Math.round(h*W/w);
  const out = [new Paragraph({alignment:AlignmentType.CENTER, spacing:{before:100, after:cap?30:140},
    children:[new ImageRun({type:'jpg', data:fs.readFileSync('doc_img/'+name),
                            transformation:{width:W, height:H}})]})];
  if (cap) out.push(SRC(cap));
  return out;
}

function TBL(cols, widths, rows, o={}){
  const fs_ = o.size || 8.3;
  const head = new TableRow({tableHeader:true, children:cols.map((t,i)=>new TableCell({
    width:{size:widths[i], type:WidthType.DXA},
    borders:{top:{style:BorderStyle.SINGLE, size:10, color:C.ink},
             bottom:{style:BorderStyle.SINGLE, size:4, color:C.line}},
    verticalAlign:VerticalAlign.BOTTOM, margins:{top:66, bottom:52, left:74, right:74},
    children:[new Paragraph({alignment:o.align?.[i] || AlignmentType.LEFT,
      spacing:{after:0, line:210, lineRule:LR}, keepNext:true,
      children:[new TextRun({text:t, size:pt(Math.max(6.4, fs_-1.4)), color:C.mid, font:FS,
                             allCaps:true, characterSpacing:12})]})]}))});
  const last = rows.length - 1;
  const bodyRows = rows.map((r,ri)=> new TableRow({cantSplit:true, children:r.map((cell,i)=>{
    const isObj = cell && typeof cell === 'object';
    const txt = isObj ? cell.t : cell;
    return new TableCell({
      width:{size:widths[i], type:WidthType.DXA},
      ...(isObj && cell.fill ? {shading:{type:ShadingType.CLEAR, color:'auto', fill:cell.fill}} : {}),
      borders:{bottom:{style:BorderStyle.SINGLE, size: ri===last?10:2,
                       color: ri===last?C.ink:C.hair}},
      verticalAlign:VerticalAlign.CENTER, margins:{top:66, bottom:66, left:74, right:74},
      children:String(txt).split('\n').map(line=>new Paragraph({
        alignment:(isObj&&cell.align) || o.align?.[i] || AlignmentType.LEFT,
        spacing:{after:0, line:236, lineRule:LR},
        children:[new TextRun({text:line, bold:isObj&&cell.bold, font:FS, size:pt(fs_),
                               color:(isObj&&cell.color) || C.ink})]}))});
  })}));
  return new Table({columnWidths:widths, width:{size:widths.reduce((a,b)=>a+b,0), type:WidthType.DXA},
    borders:{top:{style:BorderStyle.NONE}, bottom:{style:BorderStyle.NONE},
             left:{style:BorderStyle.NONE}, right:{style:BorderStyle.NONE},
             insideHorizontal:{style:BorderStyle.NONE}, insideVertical:{style:BorderStyle.NONE}},
    rows:[head, ...bodyRows]});
}

function CALL(title, paras, color, fill){
  const out = [new Paragraph({spacing:{before:200, after:90},
    shading: fill ? {type:ShadingType.CLEAR, color:'auto', fill} : undefined,
    border:{top:{style:BorderStyle.SINGLE, size:10, color, space:8},
            left:{style:BorderStyle.SINGLE, size:2, color:'FFFFFF', space:8},
            right:{style:BorderStyle.SINGLE, size:2, color:'FFFFFF', space:8}},
    children:[new TextRun({text:title, bold:true, size:pt(9), color, font:FS,
                           allCaps:true, characterSpacing:22})]})];
  paras.forEach((t,i)=>out.push(new Paragraph({alignment:AlignmentType.JUSTIFIED,
    spacing:{after:i===paras.length-1?150:90, line:270, lineRule:LR},
    shading: fill ? {type:ShadingType.CLEAR, color:'auto', fill} : undefined,
    border: fill ? {left:{style:BorderStyle.SINGLE, size:2, color:'FFFFFF', space:8},
                    right:{style:BorderStyle.SINGLE, size:2, color:'FFFFFF', space:8},
                    ...(i===paras.length-1 ? {bottom:{style:BorderStyle.SINGLE, size:2, color:fill, space:8}} : {})} : undefined,
    children:[new TextRun({text:t, size:pt(9.4), color:C.ink, font:F})]})));
  return out;
}

const km = v => (v < 1 ? Math.round(v*1000/5)*5 + ' м' : v.toFixed(1).replace('.', ',') + ' км');
const SM = JSON.parse(fs.readFileSync('small_malls.json', 'utf8'));
const WHAT = {
 'ТЦ «Амбар 2»':'ВкусВилл, «Ароматный Мир», кальян-бар SB Lounge, автомойка Dr. Duck',
 'ТК «Ильинка Village»':'«Штолле», салон красоты Di lussio, «Кам.ин» (камины), «Бери заряд»',
 'Proupes Haus':'состав не раскрыт',
 'ТЦ «Ильинский»':'«Да!», «Волконский», ПВЗ Ozon, фитнес-клуб «Культура», школа шахмат EduChess, салон красоты, барбершоп — всего 25 организаций',
 'Центральная, 83':'HookahPlace Well’s, Well’s Beauty, «Азия СПА»',
 'ТЦ «Рублёво»':'ресторан «На огне», «КофеТочка», цветы «Граф»',
 '«Базар»':'«Азбука вкуса», La Maree, аптека, «Хронолюкс», Dry Bar, «Копирка»',
 '«Жуковка Плаза»':'состав не раскрыт; по открытым данным — люксовая мебель и интерьер',
 '«Лабиринт»':'Festival Vin, eLoft.store, «Марипоса», Tempo-Studio (танцы), Vellvet, Fixit Lab, «Антикварикон»',
 'Бузланово Village':'сервис и офисы: бассейны, «СоюзЦветТорг», две управляющие компании',
 '«Бирюза»':'«Магнолия», «Телефоныч»',
 '«Наш»':'«Вкусно — и точка», МТС, 5post, аптека «Неофарм», химчистка, банкомат, «Мозart», Inspot — всего 25 организаций',
 '«Живой дом»':'«Азбука вкуса», ПВЗ Ozon, аптека, «Бьюти Сизонс», банкомат МКБ',
};

/* ═══ ЛИСТ 1 ═══ */
add(H1('Малые торговые центры локации: карта'));
add(P('Тринадцать объектов того же масштаба, что и наш, в границах Ильинского, Глухова, Жуковки, Мечникова и Горок-2. Крупные форматы — РигаМолл, «Глобус», Барвиха Luxury Village, Dream House — в список не вошли: за ежедневный спрос они с нами не конкурируют. Расстояния — по прямой от улицы Ленина, 28.'));
add(IMG('small_map.jpg', 620,
  'Яндекс.Карты. Красным — ТЦ «Амбар 1», ул. Ленина, 28. Синими номерами — центры из таблицы, нумерация по возрастанию расстояния. Справа улица Ленина крупным планом: на общей карте четыре ближайшие точки сливаются с нашей.'));

add(TBL(['№','Объект','Адрес','По прямой','Кто внутри'],
  [470, 1900, 2500, 900, 4336],
  SM.map(m => [
    {t:String(m.n), align:AlignmentType.CENTER, color:C.S, bold:true},
    {t:m.name, bold:true}, m.addr,
    {t:km(m.km), align:AlignmentType.CENTER}, WHAT[m.name] || '—',
  ]), {size:6.9, align:{0:AlignmentType.CENTER}}));

add(CALL('Что это за рынок',
 ['Все тринадцать — объекты сельского масштаба: от одного строения с тремя арендаторами до двух этажей с двадцатью пятью. Набор арендаторов у них почти одинаковый: продуктовый магазин, аптека, банкомат, пункт выдачи заказов, салон красоты, кофейня или ресторан. Это инфраструктура посёлка, а не шопинг.',
  'Прямо в Ильинском конкуренция плотнее всего: четыре объекта в пределах 450 метров, включая соседнее здание того же собственника.'], C.navy, 'EEF2F6'));

/* ═══ ЛИСТ 2 ═══ */
add(H1('Что стоит на вторых этажах', true));
add(P('Главный вывод по локации: второго этажа здесь почти ни у кого нет. Большинство объектов одноэтажные, и там, где второй этаж есть, он либо не раскрывается, либо занят одним-двумя арендаторами. Ниже — всё, что по этажам известно.'));

add(TBL(['Объект','Второй этаж','Первый этаж и примечания'],
  [2100, 4100, 3906], [
 [{t:'«Наш»\nпосёлок Горки-2\n6,3 км\n2 этажа', bold:true},
  {t:'Mozart (одежда), «БасТетБра» (бельё и купальники), «Франтик и Фифочка» (детская обувь), Inspot (компьютерный клуб), Roze (салон красоты), «Вай Тай» (спа-салон), Healthfuel.ru (спортивное питание), «Магазин праздника»', fill:C.warn},
  {t:'Единственный объект локации с полностью раскрытой раскладкой: этаж известен у 18 арендаторов из 25.\n\nНа первом: «Вкусно — и точка», МТС, пункт выдачи 5post, банкомат, линзомат, цветы, химчистка, скупка золота, денежные переводы', fill:C.warn}],
 [{t:'ТЦ «Амбар 1»\nнаш объект\n3 этажа', bold:true},
  '«Бьюти Сфера — руки мастера» (салон красоты)',
  'На третьем — «Студия тела Андросовой» (массаж). Остальные арендаторы на первом этаже и в цоколе. Свободны 430,5 и 39,6 м² на втором, 45,3 м² на третьем'],
 [{t:'«Базар»\nд. Жуковка\n2,6 км', bold:true},
  '«Точка любви» (подарки и сувениры)',
  'На третьем — «Копирка». На первом: «Азбука вкуса», ресторан La Maree, дежурная аптека, «Хронолюкс», Dry Bar'],
 [{t:'«Лабиринт»\nд. Жуковка\n2,8 км', bold:true},
  '«Италон Сантехкерам» (керамическая плитка)',
  'На первом: Festival Vin, eLoft.store, салон красоты «Марипоса», школа танцев Tempo-Studio, меховое ателье, ремонт телефонов, антиквариат'],
 [{t:'«Живой дом»\nпосёлок Горки-2\n6,6 км', bold:true},
  'Аптека',
  'На первом: «Азбука вкуса», пункт выдачи Ozon, «Бьюти Сизонс», банкомат'],
 [{t:'ТЦ «Ильинский»\nул. Ленина, 11\n440 м\n2 этажа + цоколь', bold:true},
  {t:'Поэтажную раскладку центр не публикует. Из 25 организаций этаж известен у трёх — и все три на первом', fill:'FDF0F0'},
  {t:'Главный конкурент, и единственный из соседей, по которому нельзя сказать точно. Форматы, которые в подобных центрах занимают верх, у него есть: фитнес-клуб «Культура», школа шахмат EduChess, «Зов Джунглей», салон красоты «Золотой», барбершоп Real Men', fill:'FDF0F0'}],
 [{t:'Остальные семь\nобъектов локации', bold:true},
  'Второго этажа нет или состав не раскрыт',
  'ТК «Ильинка Village», Proupes Haus, ТЦ «Рублёво», Центральная, 83, Бузланово Village, «Бирюза», «Жуковка Плаза». Характерный пример — Центральная, 83 в Глухове: кальян-бар, салон красоты и спа в одном строении, всё на одном уровне'],
], {size:7.8}));

add(SRC('Источники: карточки зданий и организаций Яндекс.Карт, сентябрь 2026 года. Этаж указывается только там, где его внёс сам арендатор, поэтому по большинству объектов раскладка неполная. Состав арендаторов приведён без блока «Обслуживающие организации» — МФЦ и отделение почты закреплены за адресом и арендаторами не являются.'));

add(CALL('Три вывода для наших помещений',
 ['Первое. В этой локации полноценного заполненного второго этажа нет ни у кого, кроме «Нашего» в Горках-2. Это значит, что прямого конкурента за наших арендаторов верхнего этажа рядом нет — но и доказанного спроса соседи не создают. Арендатора придётся приводить, а не ждать.',
  'Второе. Там, где второй этаж всё-таки заполнен, его берут небольшие форматы, к которым идут целенаправленно: одежда и бельё, детская обувь, красота и спа, компьютерный клуб, спортивное питание, товары для праздника. Ни одного крупного якоря наверху нет ни в одном объекте локации.',
  'Третье. Первый этаж везде устроен одинаково — продукты, аптека, банкомат, пункт выдачи, химчистка, еда. Эти ниши в радиусе двух километров закрыты полностью, и рассчитывать на них не стоит: наши свободные площади лежат выше первого этажа, и конкурировать им нужно не за проходящего мимо, а за того, кто едет по записи.'], C.S, 'EFF7F0'));

/* ═══ сборка ═══ */
const hdr = new Paragraph({alignment:AlignmentType.LEFT, spacing:{after:0},
  border:{bottom:{style:BorderStyle.SINGLE, size:4, color:C.line, space:6}},
  children:[new TextRun({text:'ТЦ «АМБАР 1» · МАЛЫЕ ТОРГОВЫЕ ЦЕНТРЫ ЛОКАЦИИ · СЕНТЯБРЬ 2026',
                         size:pt(7.2), color:C.mid, font:FS, characterSpacing:30})]});

const doc = new Document({
  styles:{default:{document:{run:{font:F, size:pt(9.6), color:C.ink}}}},
  sections:[{properties:{page:{margin:{top:1020, bottom:900, left:900, right:900}}},
    headers:{default:new (require('docx').Header)({children:[hdr]})},
    children:body}],
});

Packer.toBuffer(doc).then(b => {
  fs.writeFileSync('Ambar1_competitors_2026-09.docx', b);
  console.log('OK →', (b.length/1024).toFixed(0), 'KB');
});
