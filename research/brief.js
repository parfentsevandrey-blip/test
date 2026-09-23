/* Краткая версия отчёта: три листа А4 — проблема, действия, арендаторы.
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

/* нумерованный пункт: жирный заголовок + пояснение одним абзацем */
const ITEM = (n, head, text) => new Paragraph({alignment:AlignmentType.JUSTIFIED,
  spacing:{after:110, line:268, lineRule:LR}, indent:{left:340, hanging:340},
  children:[new TextRun({text:n+'\t', bold:true, size:pt(9.8), color:C.gold, font:FS}),
            new TextRun({text:head+' ', bold:true, size:pt(9.8), color:C.navy, font:F}),
            new TextRun({text:text, size:pt(9.6), color:C.ink, font:F})],
  tabStops:[{type:'left', position:340}]});

const LEAD = t => new Paragraph({alignment:AlignmentType.JUSTIFIED,
  spacing:{after:150, line:276, lineRule:LR},
  children:[new TextRun({text:t, size:pt(10.2), color:C.ink, font:F})]});

/* ═══ ЛИСТ 1 — ПРОБЛЕМА ═══ */
add(H1('1. В чём проблема'));
add(LEAD('В ТЦ «Амбар 1» на улице Ленина, 28 свободны три помещения — 515,4 м². Здание само по себе в порядке: продуктовый магазин, аптека, пекарня, пункт выдачи и цветы работают, рейтинг на Яндекс.Картах 4,4. Помещения не сдаются из-за того, как они продаются.'));

add(TBL(['Помещение','Просят сейчас','Рынок для этого этажа','Что не так'],
  [2350, 1850, 1900, 4006], [
 [{t:'№1 — 430,5 м²\n2 этаж, терраса', bold:true},
  {t:'499 000 ₽/мес\n13 909 ₽ за м² в год', align:AlignmentType.CENTER},
  {t:'16 500–18 500 ₽\nза м² в год', align:AlignmentType.CENTER},
  'Цена не мешает — она ниже рынка. Мешает продукт: залы разной отделки, терраса не обустроена, в объявлении четыре назначения сразу'],
 [{t:'№2 — 39,6 м²\n2 этаж', bold:true},
  {t:'129 000 ₽/мес\n39 091 ₽ за м² в год', align:AlignmentType.CENTER, color:C.D, bold:true},
  {t:'24 000–26 000 ₽\nза м² в год', align:AlignmentType.CENTER},
  'Дороже любого малого помещения второго этажа по соседству и дороже части помещений первого'],
 [{t:'№3 — 45,3 м²\n3 этаж', bold:true},
  {t:'98 000 ₽/мес\n25 960 ₽ за м² в год', align:AlignmentType.CENTER, color:C.D, bold:true},
  {t:'20 000–23 000 ₽\nза м² в год', align:AlignmentType.CENTER},
  'Продаётся как «офис», хотя внутри готовая бьюти-студия. Единственное похожее помещение на третьем этаже рядом стоит дешевле и не сдаётся почти год'],
], {size:8.2}));

add(H3('Пять причин, по порядку важности'));
add(ITEM('1', 'Объявлений нет на ЦИАН.',
  'Арендатор подбирает помещение на ЦИАН и Авито. По адресу Ленина, 28 там нет ни одного объявления — все три помещения висят только на сайте собственника. При этом соседний «Амбар 2» того же собственника на ЦИАН размещён. Сделка не срывается на переговорах — она просто не начинается.'));
add(ITEM('2', 'Цена не учитывает этаж.',
  'Второй этаж в этой локации стоит примерно две трети первого: мимо двери никто не проходит случайно. По соседним предложениям граница видна чётко — по 24 000 ₽ за метр в год помещения сдаются за 2–4 месяца, по 30 000 стоят годами. №2 и №3 выставлены выше этой границы.'));
add(ITEM('3', 'В объявлениях написано не то, что продаётся.',
  '№3 назван «офисом на первой линии», хотя офисного рынка в Ильинском нет. №2 назван «торговой площадью», хотя это комната без витрины. №1 предлагается сразу под офис, коворкинг, спорт и танцы — арендатор читает это как «собственник сам не знает, что у него».'));
add(ITEM('4', 'Нет условий аренды.',
  'Ни в одном объявлении не указаны НДС, эксплуатационные и коммунальные платежи. Сетевой арендатор без этих цифр не может посчитать полную стоимость и не берёт объект в работу. В двух объявлениях к тому же стоит кадастровый номер соседнего здания.'));
add(ITEM('5', 'Сильные арендаторы ушли в соседнее здание.',
  'ВкусВилл и «Ароматный Мир» открылись в «Амбаре 2». Самый сильный новый спрос достался соседу, а не свободным метрам в «Амбаре 1».'));

add(CALL('Что изменить нельзя, но нужно учитывать',
 ['Дорога мимо объекта — одна полоса в каждую сторону. Парковка — 42 места на оба здания. Лифта нет, на лестницу есть жалобы. В 440 метрах работает ТЦ «Ильинский» со 120 парковочными местами. Всё это вместе — меньше четверти причин простоя. Остальные три четверти в руках собственника, и первые четыре причины из пяти устраняются за полтора месяца и около 350 тыс. ₽.'], C.navy, 'EEF2F6'));

/* ═══ ЛИСТ 2 — ДЕЙСТВИЯ ═══ */
add(H1('2. Список действий', true));
add(LEAD('Двенадцать шагов на 90 дней. Первые две недели стоят почти ничего и дают больше всего: без них всё остальное не сработает.'));

const PH = (t) => [{t, bold:true, color:C.gold, fill:C.soft}, {t:'', fill:C.soft}, {t:'', fill:C.soft}];
add(TBL(['Что сделать','Сколько стоит','Зачем'], [5500, 1400, 3206], [
 PH('Дни 1–14: цена и объявления'),
 ['1. Снизить ставку: №2 — до 79–86 тыс. ₽/мес, №3 — до 76–87 тыс. ₽/мес. Цену №1 не трогать', {t:'0 ₽', align:AlignmentType.CENTER}, 'Цена впервые совпадает с рынком'],
 ['2. Дописать в каждое объявление НДС, эксплуатационные и коммунальные платежи, каникулы, индексацию, залог', {t:'0 ₽', align:AlignmentType.CENTER}, 'Сетевой арендатор может посчитать объект'],
 ['3. Переписать объявления: №3 — готовая бьюти- или медицинская студия; №2 — под услуги, без «торговой площади» и Wildberries; №1 — одно назначение вместо четырёх', {t:'~50 тыс. ₽', align:AlignmentType.CENTER}, 'Арендатор узнаёт в тексте своё помещение'],
 ['4. Заказать выписку ЕГРН и исправить кадастровый номер во всех объявлениях', {t:'~5 тыс. ₽', align:AlignmentType.CENTER}, 'Юрист сети не остановит сделку'],
 PH('Дни 15–45: площадки и здание'),
 ['5. Разместить все три помещения на ЦИАН, Авито и Яндекс.Недвижимости', {t:'~180 тыс. ₽\nв год', align:AlignmentType.CENTER}, 'Помещения появляются там, где их ищут'],
 ['6. Профессиональная съёмка при дневном свете, планировки с размерами, визуализация террасы', {t:'~120 тыс. ₽', align:AlignmentType.CENTER}, 'Вместо фотографий, которые отпугивают'],
 ['7. Лестница: антискользящее покрытие, поручни, маркировка ступеней, свет', {t:'~350 тыс. ₽', align:AlignmentType.CENTER}, 'Снят главный риск и главная жалоба в отзывах'],
 ['8. Техническое заключение: можно ли поставить лифт и сколько это стоит', {t:'~100 тыс. ₽', align:AlignmentType.CENTER}, 'Первый вопрос детских и медицинских арендаторов'],
 ['9. Подключить 2–3 внешних брокеров на комиссию', {t:'комиссия', align:AlignmentType.CENTER}, 'Больше каналов и взгляд со стороны'],
 PH('Дни 46–90: адресная работа'),
 ['10. Обзвонить 26 компаний со списка на листе 3 с предложением под конкретное помещение', {t:'время\nброкера', align:AlignmentType.CENTER}, '15 разговоров и 5 показов'],
 ['11. Переговоры с якорным арендатором для №1: каникулы до 6 месяцев, участие в отделке', {t:'до 3,35 млн ₽', align:AlignmentType.CENTER}, 'Якорь приводит трафик и для №2 и №3'],
 ['12. Развести профили зданий: «Амбар 2» — торговля и сервис у дороги, «Амбар 1» — услуги, детское, здоровье', {t:'0 ₽', align:AlignmentType.CENTER}, 'Здания перестают отбивать друг у друга арендаторов'],
], {size:8.2}));

add(CALL('Что это даёт в деньгах',
 ['Ничего не делать — минус 0,34 млн ₽ за три года: содержание пустых метров съедает всё, что принесёт поздняя сдача. Шаги 1–6 — около 350 тыс. ₽ вложений и плюс 5,69 млн ₽. Якорь в №1 — 3,35 млн ₽ вложений и плюс 8,62 млн ₽.',
  'Суммы — сколько объект заработает за три года сверх вложений, в пересчёте на сегодняшние деньги.'], C.S, 'EFF7F0'));

/* ═══ ЛИСТ 3 — АРЕНДАТОРЫ ═══ */
add(H1('3. Нужные арендаторы', true));
add(LEAD('Правило, которое видно у соседей: наверх идут небольшие форматы, к которым едут целенаправленно или по записи, — красота и спа, медицина, спорт, детские занятия, бытовые услуги. Витрина им не нужна. Из товаров на второй этаж поднимаются только узкие магазины — бельё, детская обувь, электроника. Третий этаж у соседей занимают только услуги.'));

add(TBL(['Помещение','Кого искать','Кому звонить первым'], [2100, 3900, 4106], [
 [{t:'№1\n430,5 м², 2 этаж\nтерраса, потолки 3,9 м', bold:true},
  'Целиком: частный детский сад или детский клуб — терраса становится прогулочной зоной, которой нет у соседей; бьюти-коворкинг на 15–20 мест.\n\nБлоками по 60–200 м²: студия пилатеса или фитнеса, лаборатория, детский центр, шоурум кухонь',
  {t:'Бэби-клуб / Бэби-сад, SoloSalon — целиком\nArt of Pilates, Pilates Life — блок 150 м²\nГемотест — блок 60–90 м²\n«Сёма», «Чемпионика» — детское и спорт\nGLOW — бьюти-коворкинг\n«Кухонный Двор», кухни «Мария»', bold:true}],
 [{t:'№2\n39,6 м², 2 этаж\nмокрая точка', bold:true},
  'Маникюр, барбершоп, ремонт электроники — небольшие форматы, которым хватает 40 метров и мокрой точки. Второй вариант — узкий магазин вроде белья или детской обуви: такие уже работают на втором этаже у соседей в Горках-2',
  {t:'4hands, «Пальчики» — маникюр\nTOPGUN, OldBoy, BRITVA — барбершопы\nPedant.ru — ремонт электроники', bold:true}],
 [{t:'№3\n45,3 м², 3 этаж\nготовая отделка\nбьюти-студии', bold:true},
  'Косметология и лазерная эпиляция — заезд почти без вложений. Также лаборатория, языковая школа, детское IT-образование, ветклиника',
  {t:'Laser Love, «ТОЧКА красоты»\nKDL — лаборатория\n«Полиглотики», «Алгоритмика»\n«Свой Доктор» — ветклиника', bold:true}],
], {size:8.8}));

add(new Paragraph({spacing:{before:160, after:0}, children:[]}));
add(P('Главный довод в разговоре с медицинскими и детскими сетями: здание нежилое, поэтому лицензия на втором этаже возможна — встроенные помещения в соседних жилых комплексах этого дать не могут. Первым вопросом у них будет лифт, поэтому ответ по шагу 8 нужен до показа.', {size:9.4}));

add(CALL('Кого звать не нужно',
 ['Крупные сети одежды и обуви — им нужны первый этаж и витрина; у соседей наверху стоят только небольшие специализированные магазины. Wildberries — их правила запрещают пункты выдачи выше первого этажа. Продукты и аптеку — эти ниши в радиусе двух километров закрыты полностью. Офисы — офисного рынка в Ильинском нет. Ресторан на весь этаж — нет грузового подъёма, 42 парковки и дневной, а не вечерний поток посетителей.'], C.D, 'FDF0F0'));

/* ═══ сборка ═══ */
const hdr = new Paragraph({alignment:AlignmentType.LEFT, spacing:{after:0},
  border:{bottom:{style:BorderStyle.SINGLE, size:4, color:C.line, space:6}},
  children:[new TextRun({text:'ТЦ «АМБАР 1» · КРАТКАЯ ВЕРСИЯ · СЕНТЯБРЬ 2026',
                         size:pt(7.2), color:C.mid, font:FS, characterSpacing:30})]});

const doc = new Document({
  styles:{default:{document:{run:{font:F, size:pt(9.6), color:C.ink}}}},
  sections:[{properties:{page:{margin:{top:1020, bottom:900, left:900, right:900}}},
    headers:{default:new (require('docx').Header)({children:[hdr]})},
    children:body}],
});
Packer.toBuffer(doc).then(b => {
  fs.writeFileSync('Ambar1_brief_2026-09.docx', b);
  console.log('OK →', (b.length/1024).toFixed(0), 'KB');
});
