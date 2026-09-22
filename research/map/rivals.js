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

const km = v => (v < 1 ? Math.round(v*1000) + ' м' : v.toFixed(1).replace('.', ',') + ' км');

/* ═══ ЛИСТ 1 ═══ */
add(H1('Торговые центры в окружении: карта'));
add(P('Четырнадцать торговых объектов, между которыми распределяется спрос жителей Ильинского и соседних посёлков. Расстояния — по прямой от улицы Ленина, 28; по дороге до объектов на правом берегу Москвы-реки (Барвиха, Дрим Хаус) выходит 15–20 километров, потому что ближайшие мосты — у Петрово-Дальнего и Успенского.'));
add(IMG('rivals_map.jpg', 660,
  'Яндекс.Карты. Красным — ТЦ «Амбар 1», ул. Ленина, 28. Синими номерами — торговые центры из таблицы ниже, нумерация по возрастанию расстояния. Слева вся зона, справа улица Ленина крупным планом: на общей карте точки 1–3 сливаются с нашей.'));

add(TBL(['№','Объект','Адрес','По прямой','Формат'],
  [520, 2700, 3300, 1100, 2486],
  MALLS.map(m => [
    {t:String(m.n), align:AlignmentType.CENTER, color:C.S, bold:true},
    {t:m.name, bold:true}, m.addr,
    {t:km(m.km), align:AlignmentType.CENTER}, m.fmt,
  ]), {size:8.0, align:{0:AlignmentType.CENTER}}));

add(CALL('Что показывает карта',
 ['Ближний круг — три объекта в пределах 450 метров, и все три на одной улице. Вместе с нашим зданием это сложившийся ретейл-кластер, внутри которого спрос уже поделён.',
  'Следующий пояс — 3–6 километров: Барвиха, Дрим Хаус, РигаМолл, Архангельское Аутлет, «Глобус». По прямой они близко, но Москва-река превращает эти километры в 15–20 по дороге, поэтому ежедневный спрос они не забирают.',
  'Дальше 9 километров начинаются объекты, за которыми едут специально: «Павлово Подворье», «Октябрь», «Княжий двор», Vegas. Они не конкурируют за поход за хлебом, но конкурируют за то, ради чего человек садится в машину на полчаса, — а именно этим живут вторые и третьи этажи.'], C.navy, 'EEF2F6'));

/* ═══ ЛИСТ 2 ═══ */
add(H1('Что стоит на вторых этажах', true));
add(P('Второй этаж — тот же вопрос, который стоит перед нашими помещениями №1 и №2. Ниже — фактический состав вторых этажей там, где торговые центры публикуют поэтажную раскладку.'));

add(TBL(['Торговый центр','Кто занимает второй этаж','Чем это полезно нам'],
  [2300, 4700, 3106], [
 [{t:'ТЦ «Октябрь»\nПавловская Слобода\n11,3 км\n3 эт. + цоколь', bold:true},
  {t:'DNS (электроника), Ламода, Яндекс Маркет, пункт выдачи OZON, «Весёлая Затея» (товары для праздника), «Нейрополис», ремонт телефонов и ключей.\n\nСвободно помещение 21 — 98 м², предлагается «под стоматологию, кафе или фитнес»', fill:C.warn},
  {t:'Ближайший функциональный аналог: те же три этажа, потолки 3,92–3,94 м против наших 3,9. Показывает, что на второй этаж товарный арендатор идёт — но только электроника и выдача заказов маркетплейсов. Одежды и обуви нет', fill:C.warn}],
 [{t:'ТЦ «Княжий двор»\nНоворижское шоссе\n13,8 км\n3 уровня', bold:true},
  'WHITE FOX Medical & Beauty и отдельно WHITE FOX для детей, WHITE FOX SPA & Barbershop, EXCLUSIVE FITNESS, YOGA TIME NEW RIGA, академия джиу-джитсу «SO FORCA & GOJIRA», студия загара SUN VIBE, ателье «Придворный портной», Fashion Lab, кадровое агентство, «Чулкоff», MIA FURS',
  'Второй этаж почти целиком отдан медицине, красоте и спорту. Товарных арендаторов двое, оба — узкие нишевые. Первый этаж при этом занят продуктами, банками, пунктами выдачи и ресторанами'],
 [{t:'Dream House\nБарвиха\n3,0 км по прямой\n4 уровня', bold:true},
  'DeLight, GRETHER&WELLS, IRAN CARPETS, Salone, Le 5 Avenue, Chobi (интерьер, ковры, мебель), Tartufo, «Сувениры», MFITNESS, «Детский универмаг»',
  'Другая экономика: премиальный катчмент позволяет держать на втором этаже крупноформатный товар низкой частоты. Но и здесь рядом с мебелью стоят фитнес и детский формат'],
 [{t:'ТЦ «Амбар 2»\nул. Ленина, 26\n25 м\n1 эт. + цоколь', bold:true},
  'На втором этаже — кальян-бар SB Lounge. ВкусВилл и «Ароматный Мир» заняли первый этаж, автомойка-детейлинг Dr. Duck — цоколь',
  'Соседний корпус того же собственника подтверждает правило в чистом виде: всё, что генерирует трафик, село на первый этаж, наверх ушёл вечерний формат'],
 [{t:'ТЦ «Ильинский»\nул. Ленина, 11\n440 м\n2 эт. + цоколь', bold:true},
  {t:'Поэтажную раскладку центр не публикует. Из 32 организаций по этажу известно только про три, и все три — на первом. Форматы, которые в подобных центрах обычно занимают верх, здесь есть: фитнес-клуб «Культура», школа шахмат EduChess, «Зов Джунглей» (детские праздники), салон красоты «Золотой», барбершоп Real Men, «Кашемир-Шоп», «Микстон» (оборудование для салонов красоты)', fill:'FDF0F0'},
  {t:'Главный конкурент, и единственный, по которому нельзя сказать точно. Набор арендаторов при этом говорит сам за себя: спрос на услуги, детское и спорт в локации есть и уже обслуживается', fill:'FDF0F0'}],
 [{t:'ТРК «Павлово Подворье»\n9,0 км · 2 эт.\nТК «Ильинка Вилладж»\n75 м', bold:true},
  'Раскладку по этажам не публикуют ни тот, ни другой. По «Павлово Подворью» известен состав целиком: школа танцев TODES, детский центр, «Книжный лабиринт», IL Patio, клиники, салоны',
  'TODES и детский центр — ровно те форматы, что в других центрах стоят наверху. Прямым доказательством по этажу это не является'],
], {size:8.0}));

add(SRC('Источники: официальные сайты торговых центров (tc-oktyabr.ru, tc-dvor.ru, dreamhouse.ru, podvorie.com) и карточки организаций Яндекс.Карт, сентябрь 2026 года. Для ТЦ «Ильинский», ТК «Ильинка Вилладж» и ТРК «Павлово Подворье» поэтажные планы в открытом доступе отсутствуют.'));

add(CALL('Правило, которое видно по всем пяти центрам',
 ['Первый этаж везде занят тем, ради чего человек заезжает по дороге: продукты, аптека, банк, пункт выдачи, еда. За эти места конкуренции нет — они разбираются первыми.',
  'Второй этаж берут четыре категории: красота и медицина, спорт и танцы, детское развитие, бытовые услуги. Из товарных арендаторов наверх поднимаются только электроника и шоурумы маркетплейсов — и только там, где центр крупнее нашего. Сетевой одежды и обуви на вторых этажах нет ни в одном из пяти объектов.',
  'Общий признак арендаторов второго этажа: к ним едут по записи или целенаправленно, витрина им не нужна. Это и есть целевой список для помещений №1 и №2 — и побочная выгода в том, что запись распределяет визиты по времени и снимает пиковую нагрузку на 42 парковочных места.'], C.S, 'EFF7F0'));

/* ═══ сборка ═══ */
const hdr = new Paragraph({alignment:AlignmentType.LEFT, spacing:{after:0},
  border:{bottom:{style:BorderStyle.SINGLE, size:4, color:C.line, space:6}},
  children:[new TextRun({text:'ТЦ «АМБАР 1» · КОНКУРЕНТНОЕ ОКРУЖЕНИЕ · СЕНТЯБРЬ 2026',
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
