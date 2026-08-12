#!/usr/bin/env node
/* Проверки чистой логики — без сети. Запуск: node tools/cian/test.js
   Здесь живёт та инвариантa, нарушение которой уже один раз испортило выдачу:
   дробление запроса обязано сужать, а не расширять. */
const assert = require('assert');
const { groupSameFlat, dedupe, findTwins, withMarket, median, assessRepair, mergeArchive } = require('./cian.js');

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
