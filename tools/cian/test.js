#!/usr/bin/env node
/* Проверки чистой логики — без сети. Запуск: node tools/cian/test.js
   Здесь живёт та инвариантa, нарушение которой уже один раз испортило выдачу:
   дробление запроса обязано сужать, а не расширять. */
const assert = require('assert');
const { groupSameFlat, dedupe, findTwins, withMarket, median, assessRepair, mergeArchive,
        completeness, comparabilityGaps, features, readiness, finishEvidence, buildingYear } = require('./cian.js');

let passed = 0;
const test = (name, fn) => {
  try { fn(); passed++; process.stdout.write(`  ok  ${name}\n`); }
  catch (e) { process.stdout.write(`  FAIL ${name}\n       ${e.message}\n`); process.exitCode = 1; }
};

const lot = (o) => ({ id: 1, houseId: 10, floor: 5, rooms: 2, totalArea: 60, priceRub: 10e6,
  district: 'Раменки', street: 'Тестовая', house: '1', photosCount: 12, hasFurniture: true, description: '', ...o });

process.stdout.write('группировка квартир\n');

test('одна квартира из двух объявлений с одинаковой площадью', () => {
  const { groups } = groupSameFlat([lot({ id: 1 }), lot({ id: 2 })]);
  assert.strictEqual(groups.length, 1);
  assert.strictEqual(groups[0].length, 2);
});

test('площади в пределах допуска сшиваются', () => {
  const { groups } = groupSameFlat([lot({ id: 1, totalArea: 65.5 }), lot({ id: 2, totalArea: 65.9 })]);
  assert.strictEqual(groups.length, 1, 'разница 0.4 м² должна попасть в допуск');
});

test('площади за допуском не сшиваются', () => {
  const { groups } = groupSameFlat([lot({ id: 1, totalArea: 60 }), lot({ id: 2, totalArea: 75 })]);
  assert.strictEqual(groups.length, 2);
});

test('разные этажи — разные квартиры, а не дубли', () => {
  const { groups } = groupSameFlat([lot({ id: 1, floor: 5 }), lot({ id: 2, floor: 9 })]);
  assert.strictEqual(groups.length, 2, 'одна планировка на разных этажах — не одна квартира');
});

test('без корпуса в адресе лот уходит в loose, а не склеивается наугад', () => {
  const { groups, loose } = groupSameFlat([lot({ id: 1, houseId: null }), lot({ id: 2, houseId: null })]);
  assert.strictEqual(loose.length, 2);
  assert.strictEqual(groups.length, 0);
});

test('пустая комнатность не разбивает группу одной квартиры', () => {
  // Кутузовский 12: 158.3 м² на 4 этаже за 190 млн, у одного объявления rooms пустой
  const { groups } = groupSameFlat([lot({ id: 1, rooms: 5, totalArea: 158.3, floor: 4 }),
                                    lot({ id: 2, rooms: null, totalArea: 158.3, floor: 4 })]);
  assert.strictEqual(groups.length, 1);
  assert.strictEqual(groups[0].length, 2);
});

test('разная указанная комнатность при равной площади — разные квартиры', () => {
  const { groups } = groupSameFlat([lot({ id: 1, rooms: 2, totalArea: 60, floor: 4 }),
                                    lot({ id: 2, rooms: 3, totalArea: 60, floor: 4 })]);
  assert.strictEqual(groups.length, 2, 'зеркальные планировки склеивать нельзя');
});

process.stdout.write('схлопывание и переплата\n');

test('остаётся самое дешёвое объявление, остальные — в alsoListedAs', () => {
  const { flats } = dedupe([lot({ id: 1, priceRub: 25e6 }), lot({ id: 2, priceRub: 22e6 })]);
  assert.strictEqual(flats.length, 1);
  assert.strictEqual(flats[0].id, 2);
  assert.strictEqual(flats[0].listings, 2);
  assert.strictEqual(flats[0].alsoListedAs.length, 1);
});

test('переплата считается от нижней цены', () => {
  const { flats } = dedupe([lot({ id: 1, priceRub: 20e6 }), lot({ id: 2, priceRub: 25e6 })]);
  assert.strictEqual(flats[0].overpay, 25);
});

test('самая ранняя дата создания переживает переразмещение', () => {
  const { flats } = dedupe([lot({ id: 1, created: '2026-07-27' }), lot({ id: 2, created: '2025-09-24' })]);
  assert.strictEqual(flats[0].earliestCreated, '2025-09-24');
});

test('findTwins возвращает только группы больше одной', () => {
  const t = findTwins([lot({ id: 1 }), lot({ id: 2 }), lot({ id: 3, floor: 12 })]);
  assert.strictEqual(t.length, 1);
});

process.stdout.write('цена относительно рынка\n');

test('медиана', () => {
  assert.strictEqual(median([3, 1, 2]), 2);
  assert.strictEqual(median([4, 1, 2, 3]), 2.5);
  assert.strictEqual(median([]), null);
});

test('лот дешевле медианы корпуса получает отрицательный процент', () => {
  const lots = [1, 2, 3, 4].map((i) => lot({ id: i, priceRub: 12e6 }))
    .concat([lot({ id: 5, floor: 20, priceRub: 6e6 })]);
  const m = withMarket(lots);
  const cheap = m.find((x) => x.id === 5);
  assert.ok(cheap.vsBuildingPct < 0, `ожидал минус, получил ${cheap.vsBuildingPct}`);
});

test('когорта меньше минимума не даёт оценки вместо выдумывания', () => {
  const m = withMarket([lot({ id: 1 })], 4);
  assert.strictEqual(m[0].vsBuildingPct, null);
});

process.stdout.write('комплектность и сопоставимость\n');

test('hasFurniture=false — оболочка, даже при премиальной отделке', () => {
  // 331115316, Victory Park: мрамор, двери Barausse, но ни кухни, ни мебели
  assert.strictEqual(completeness(lot({ hasFurniture: false,
    description: 'Дизайнерская отделка Neo-Deco, натуральный мрамор, двери Barausse' })), 'оболочка');
});

test('decoration=without — оболочка', () => {
  assert.strictEqual(completeness(lot({ hasFurniture: null, decoration: 'without' })), 'оболочка');
});

test('метка отделки на вторичке игнорируется', () => {
  // фильтр decorations_list=without возвращает 469 из 492 по Пресненскому —
  // на вторичке «нет данных» = «без отделки», и метке верить нельзя
  const resale = lot({ hasFurniture: null, decorFilter: 'without', saleType: 'free',
    houseFinished: true, fromDeveloper: false, description: 'Мебель и техника остаются' });
  assert.strictEqual(completeness(resale), 'под ключ');
});

test('на первичке метка отделки работает', () => {
  const primary = lot({ hasFurniture: null, decorFilter: 'without', saleType: 'fz214', fromDeveloper: true });
  assert.strictEqual(completeness(primary), 'оболочка');
});

test('decoration=turnkey из карточки — под ключ на первичке', () => {
  // 326035617, ЖК Золотой: продажа от застройщика, в выдаче поиска поле пустое,
  // значение turnkey отдаёт только карточка объявления
  assert.strictEqual(completeness(lot({ hasFurniture: null, decoration: 'turnkey',
    fromDeveloper: true, description: 'Квартира предлагается к продаже с дизайнерским ремонтом' })), 'под ключ');
});

test('мебель и техника в тексте — под ключ', () => {
  assert.strictEqual(completeness(lot({ hasFurniture: null,
    description: 'Остаётся вся мебель и техника' })), 'под ключ');
});

test('рассказ о прошлой белой коробке не делает квартиру оболочкой', () => {
  assert.notStrictEqual(completeness(lot({ hasFurniture: true,
    description: 'Полностью переделан white box от застройщика' })), 'оболочка');
});

/* Галочка «есть мебель» стоит у 70 вторичных лотов из 187, и хотя бы у двух
   из них она врёт. Пока это единственное основание — комплектность неизвестна. */
test('одной галочки hasFurniture для «под ключ» мало', () => {
  assert.strictEqual(completeness(lot({ hasFurniture: true,
    description: 'Современная квартира с новой отделкой в ЖК Lucky' })), 'неизвестно');
});

test('«готовый интерьер» без слова о мебели — ещё не под ключ', () => {
  // 330733568, Victory Park, 168,5 млн: по тексту «готовый интерьер»,
  // на фото пустые комнаты, вместо кухни выводы воды и розетки
  assert.strictEqual(completeness(lot({ hasFurniture: true,
    description: 'Трехкомнатная квартира с готовым интерьером! Выполнена качественная отделка в светлых тонах' })),
  'неизвестно');
});

test('незавершённый ремонт — отдельное состояние, не «под ключ»', () => {
  // 331424705, Резиденция МОНЭ, 145 млн: двери в плёнке, кухни нет,
  // а в тексте прямо сказано, когда работы кончатся
  assert.strictEqual(completeness(lot({ hasFurniture: true,
    description: 'Новый дизайнерский ремонт. Ремонтные работы в квартире завершатся в августе 2026 года' })),
  'ремонт не сдан');
});

test('незавершённый ремонт ломает сопоставимость с готовой квартирой', () => {
  const gaps = comparabilityGaps(
    lot({ description: 'Ремонтные работы завершатся в августе' }),
    lot({ description: 'Полностью укомплектована мебелью и бытовой техникой' }));
  assert.ok(gaps.some((g) => /ремонт не сдан/.test(g)), gaps.join('; '));
});

process.stdout.write('чем подтверждён уровень отделки\n');

test('поимённая комплектация отличается от «квартиры с новой отделкой»', () => {
  // 329819607, Кутузовский XII, 158 млн — марки перечислены по комнатам
  const rich = finishEvidence(lot({ description: 'Кухня Arrital, столешница Fenix, фартук MaxFine, '
    + 'диван Ditre Italia, люстра MOOOI, сантехника CEA, Fantini, керамогранит FMG' }));
  assert.ok(rich.spelledOut, 'марки названы в ' + rich.categories.length + ' категориях');
  // 327357005, Lucky, 195 млн — самый дорогой метр и ни одной марки
  const bare = finishEvidence(lot({ description: 'Современная квартира с новой отделкой в ЖК Lucky' }));
  assert.strictEqual(bare.brands.length, 0);
  assert.strictEqual(bare.spelledOut, false);
});

test('техника названа, мебель — нет: комплектность не заявлена', () => {
  // 332239634, Capital Towers, 190 млн: Smeg, Grohe, Duravit — и ни слова о мебели
  const ev = finishEvidence(lot({ description: 'Квартира оснащена техникой Smeg, '
    + 'смесители Grohe и сантехника Duravit' }));
  assert.ok(ev.brands.includes('smeg'));
  assert.strictEqual(ev.furnished, false);
});

test('марка не ловится внутри чужого слова', () => {
  const ev = finishEvidence(lot({ description: 'Рядом фитнес-клуб и Гроховская улица, ceao' }));
  assert.strictEqual(ev.brands.length, 0, ev.brands.join(','));
});

test('оболочку и квартиру под ключ не сравнить — разрыв назван', () => {
  const gaps = comparabilityGaps(lot({ hasFurniture: true }), lot({ hasFurniture: false }));
  assert.ok(gaps.some((g) => /комплектность/.test(g)), gaps.join('; '));
});

test('переуступка против ДДУ попадает в разрывы', () => {
  const gaps = comparabilityGaps(lot({ saleType: 'fz214' }), lot({ saleType: 'dupt' }));
  assert.ok(gaps.some((g) => /условия сделки/.test(g)));
});

test('одинаковые лоты сравнимы, разрывов нет', () => {
  assert.deepStrictEqual(comparabilityGaps(lot({}), lot({})), []);
});

test('медиана корпуса считается внутри своей комплектности', () => {
  // четыре оболочки по 1 млн/м² и одна квартира под ключ — она не должна
  // выглядеть дороже рынка только потому, что рядом стоят голые
  const shells = [1, 2, 3, 4].map((i) => lot({ id: i, hasFurniture: false, priceRub: 60e6, totalArea: 60 }));
  const turnkey = lot({ id: 5, hasFurniture: true, priceRub: 90e6, totalArea: 60,
    description: 'Полностью меблирована, вся бытовая техника остаётся' });
  const m = withMarket(shells.concat([turnkey]));
  const t = m.find((x) => x.id === 5);
  assert.strictEqual(t.completeness, 'под ключ');
  assert.strictEqual(t.vsBuildingPct, null, 'не с чем сравнивать: под ключ в доме один');
});

test('признаки из описания: паркинг, евро-планировка, вид', () => {
  const f = features(lot({ description: 'Евро-3, кухня-гостиная, вид на реку, машиноместо оплачивается отдельно' }));
  assert.ok(f.parkingMentioned && f.parkingSeparate && f.euroLayout && f.viewClaimed);
});

test('студия и квартира с комнатами не склеиваются по flatType', () => {
  const { groups } = groupSameFlat([lot({ id: 1, flatType: 'studio', rooms: null, totalArea: 40, floor: 3 }),
                                    lot({ id: 2, flatType: 'rooms', rooms: 1, totalArea: 40, floor: 3 })]);
  assert.strictEqual(groups.length, 2);
});

process.stdout.write('ДДУ и готовность\n');

test('сданный корпус — «сдан»', () => {
  assert.strictEqual(readiness(lot({ houseFinished: true })), 'сдан');
});

test('строящийся корпус несёт год сдачи', () => {
  // Веспер Кутузовский: ДДУ, сдача 2030
  assert.strictEqual(readiness(lot({ houseFinished: false, deadline: { year: 2030, quarter: null } })),
    'строится до 2030');
});

test('свободная продажа в готовом доме — «сдан»', () => {
  assert.strictEqual(readiness(lot({ houseFinished: null, saleType: 'free', buildYear: 2020 })), 'сдан');
});

test('готовую квартиру не сравнить со стройкой', () => {
  const ready = lot({ houseFinished: true, saleType: 'free' });
  const ddu = lot({ houseFinished: false, deadline: { year: 2030 }, saleType: 'fz214' });
  const g = comparabilityGaps(ready, ddu);
  assert.ok(g.some((x) => /готовность/.test(x)), g.join('; '));
  assert.ok(g.some((x) => /ДДУ/.test(x)), g.join('; '));
});

test('разные годы сдачи внутри стройки — тоже разрыв', () => {
  // Бадаевский сдаёт 2026 и 2027 разными корпусами
  const g = comparabilityGaps(lot({ houseFinished: false, deadline: { year: 2026 } }),
                              lot({ houseFinished: false, deadline: { year: 2027 } }));
  assert.ok(g.some((x) => /2026/.test(x) && /2027/.test(x)), g.join('; '));
});

test('переуступка и ДДУ названы по-русски в разрыве', () => {
  const g = comparabilityGaps(lot({ saleType: 'fz214' }), lot({ saleType: 'dupt' }));
  assert.ok(g.some((x) => /ДДУ \/ переуступка/.test(x)), g.join('; '));
});

test('когорта разделяет готовое и строящееся', () => {
  const built = [1, 2, 3, 4].map((i) => lot({ id: i, houseFinished: true, saleType: 'free',
    priceRub: 60e6, totalArea: 60, houseId: null }));
  const site = lot({ id: 9, houseFinished: false, deadline: { year: 2030 },
    priceRub: 120e6, totalArea: 60, houseId: null });
  const m = withMarket(built.concat([site]));
  const s = m.find((x) => x.id === 9);
  assert.strictEqual(s.readiness, 'строится до 2030');
  assert.strictEqual(s.vsCohortPct, null, 'стройку не с чем сравнивать среди готовых');
});

process.stdout.write('проверка ремонта\n');

test('прямое указание на белую коробку — противоречие', () => {
  const r = assessRepair(lot({ description: 'Квартира без отделки, под чистовую' }));
  assert.strictEqual(r.verdict, 'ПРОТИВОРЕЧИЕ');
});

test('мебель и техника в описании — похоже на правду', () => {
  const r = assessRepair(lot({ description: 'Вся мебель и техника Miele остаётся, встроенные шкафы, гардеробная' }));
  assert.strictEqual(r.verdict, 'похоже на правду');
});

test('пустое описание не выдаётся за проверенное', () => {
  const r = assessRepair(lot({ description: '', hasFurniture: null, photosCount: 3 }));
  assert.notStrictEqual(r.verdict, 'похоже на правду');
});

test('hasFurniture=false опровергает заявленный ремонт под ключ', () => {
  // проверено вручную на 332342009: галочка «дизайнерский», на фото голая отделка
  const r = assessRepair(lot({ description: 'Дизайнерский ремонт, мебель и техника', hasFurniture: false }));
  assert.strictEqual(r.verdict, 'ПРОТИВОРЕЧИЕ');
});

test('white box в рассказе о прошлом не считается противоречием', () => {
  // 317582567: «куплена в состоянии white box», а на фото законченный интерьер
  const r = assessRepair(lot({ description: 'Квартира куплена в состоянии white box, сделан ремонт с мебелью', hasFurniture: true }));
  assert.strictEqual(r.verdict, 'похоже на правду');
  assert.ok(r.flags.some((f) => /прошлом/.test(f)));
});

test('white box про нынешнее состояние остаётся противоречием', () => {
  const r = assessRepair(lot({ description: 'Продаётся в состоянии white box', hasFurniture: null }));
  assert.strictEqual(r.verdict, 'ПРОТИВОРЕЧИЕ');
});

test('«переделан white box от застройщика» — про прошлое, не противоречие', () => {
  // 317582567 дословно: «полностью переделан white box от застройщика»
  const r = assessRepair(lot({ description: 'Выполнен дизайнерский ремонт, полностью переделан white box от застройщика, мебель и техника остаются', hasFurniture: true }));
  assert.strictEqual(r.verdict, 'похоже на правду');
});

test('слово против богатого описания понижается до «под вопросом», а не рубит сплеча', () => {
  const r = assessRepair(lot({ description: 'бетон. дизайн-проект, мебель и техника, встроенные шкафы', hasFurniture: null }));
  assert.strictEqual(r.verdict, 'под вопросом');
});

test('незаполненное hasFurniture само по себе не повод для подозрения', () => {
  const r = assessRepair(lot({ description: 'Мебель и техника остаются', hasFurniture: null }));
  assert.strictEqual(r.verdict, 'похоже на правду');
});

process.stdout.write('архив\n');

test('изменение цены между снимками попадает в changes', () => {
  const arc = { flats: {} };
  const a = lot({ id: 1, fingerprint: 'f1', priceRub: 25e6 });
  mergeArchive(arc, [a], '2026-08-01');
  const r = mergeArchive(arc, [{ ...a, priceRub: 23.5e6 }], '2026-08-12');
  assert.strictEqual(r.changes.length, 1);
  assert.strictEqual(r.changes[0].to, 23.5e6);
});

test('первая встреча квартиры не перезаписывается поздним снимком', () => {
  const arc = { flats: {} };
  const a = lot({ id: 1, fingerprint: 'f1' });
  mergeArchive(arc, [a], '2026-05-01');
  mergeArchive(arc, [a], '2026-08-12');
  assert.strictEqual(arc.flats.f1.firstSeen, '2026-05-01');
});

process.stdout.write(`\n${passed} проверок пройдено${process.exitCode ? ', есть провалы' : ''}\n`);

process.stdout.write('год постройки\n');

test('у новостройки год берётся из срока сдачи', () => {
  assert.strictEqual(buildingYear(lot({ buildYear: null, deadline: { year: 2023, quarter: 1 } })), 2023);
});

test('заполненный buildYear важнее срока сдачи', () => {
  assert.strictEqual(buildingYear(lot({ buildYear: 2019, deadline: { year: 2030 } })), 2019);
});

test('когда года нет нигде — null, а не ноль', () => {
  assert.strictEqual(buildingYear(lot({ buildYear: null, deadline: null })), null);
});
