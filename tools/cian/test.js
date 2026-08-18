#!/usr/bin/env node
/* Проверки чистой логики — без сети. Запуск: node tools/cian/test.js
   Здесь живёт та инвариантa, нарушение которой уже один раз испортило выдачу:
   дробление запроса обязано сужать, а не расширять. */
const assert = require('assert');
const { normalize, groupSameFlat, dedupe, findTwins, withMarket, median, assessRepair, mergeArchive, archiveStat,
        completeness, comparabilityGaps, features, readiness, finishEvidence, buildingYear, insideGardenRing, ringVerdict,
        gradeLevel, gradeRecord, finishCost, loadedPricePerM2, fairShellPrice, gradeFor, galleryGrew, parseViews, mergedPriceHistory, offersByIds, matchesQuery, expandSimilar, houseClass, profileLot, floorBand, worksScope, buildCohort, metroSummary, metroLine, metroCell, photoKinds, photoIdent, galleryKey, galleryDiff, sweepCost } = require('./cian.js');

let passed = 0;
const pending = [];

/* Асинхронные проверки нельзя просто запустить и забыть: итоговый счётчик
   печатался синхронно и показывал 52 при 101 пройденной. Собираем обещания
   и дожидаемся их перед подсчётом. */
const test = (name, fn) => {
  const ok = () => { passed++; process.stdout.write(`  ok  ${name}\n`); };
  const bad = (e) => { process.stdout.write(`  FAIL ${name}\n       ${e.message}\n`); process.exitCode = 1; };
  try {
    const r = fn();
    if (r && typeof r.then === 'function') { pending.push(r.then(ok, bad)); return; }
    ok();
  } catch (e) { bad(e); }
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

process.stdout.write('Садовое кольцо\n');

/* Проверка на настоящих адресах: район для центра ничего не решает — все
   четыре пары ниже лежат в одном округе, а по разные стороны кольца. */
const at = (lat, lng) => insideGardenRing({ lat, lng });

test('Софийская набережная — внутри', () => {
  assert.strictEqual(at(55.74774, 37.61769), true);  // 326035617, ЖК Золотой
});

test('Ордынский тупик и Большой Толмачёвский — внутри', () => {
  assert.strictEqual(at(55.7355, 37.6237), true);    // Ордынка
  assert.strictEqual(at(55.7420, 37.6210), true);    // Лаврушинский
});

test('Хлебный переулок на Арбате — внутри', () => {
  assert.strictEqual(at(55.7549, 37.5926), true);    // 267826294
});

test('Садовые кварталы на Усачёва — снаружи, хотя это Хамовники', () => {
  assert.strictEqual(at(55.7270, 37.5680), false);   // 331956041, 322282922
});

test('Мантулинская и Шмитовский — снаружи, хотя это Пресненский', () => {
  assert.strictEqual(at(55.7570, 37.5390), false);
  assert.strictEqual(at(55.7580, 37.5230), false);
});

test('без координат — null, а не «снаружи»', () => {
  assert.strictEqual(insideGardenRing({ lat: null, lng: null }), null);
});

process.stdout.write('уровень отделки\n');

/* Признаки списаны с фотографий этой сессии. Проверка в том, что буква,
   выведенная арифметикой, совпадает с тем, как квартиру прочитал глаз. */
const M = {
  premium: { stone: 'слэб', joinery: 'на заказ', kitchen: 'интегрированная',
    light: 'сценарный', furniture: 'полный', bath: 'камень и бренд' },
  ordinary: { stone: 'нет', joinery: 'серийная', kitchen: 'встроенная',
    light: 'базовый', furniture: 'полный', bath: 'плитка' },
};

test('авторский премиум — A', () => {
  // 331300080, Кутузовский XII: камень слэбом, латунь, гнутые фасады, Bosch
  assert.strictEqual(gradeLevel(M.premium), 'A');
});

test('полный, но массовый ремонт — C', () => {
  // 332133656, Ленинский 95Б: ламинат, белая кухня, плитка под мрамор, мебель
  assert.strictEqual(gradeLevel(M.ordinary), 'C');
});

test('отделка есть, кухни и мебели нет — D, баллы не считаются', () => {
  // 330733568, Victory Park: ёлочка и мрамор на месте, вместо кухни выводы воды
  assert.strictEqual(gradeLevel({ stone: 'слэб', joinery: 'нет', kitchen: 'нет',
    light: 'нет', furniture: 'нет', bath: 'камень и бренд' }), 'D');
});

test('бетон — E, а не «плохая отделка»', () => {
  // 327985409, Профсоюзная 2/22: плиты, блоки, стяжка, разводка по полу
  assert.strictEqual(gradeLevel({ stone: 'нет', joinery: 'нет', kitchen: 'нет',
    light: 'нет', furniture: 'нет', bath: 'не отделан' }), 'E');
});

test('меньше четырёх признаков — оценки нет, а не буква наугад', () => {
  assert.strictEqual(gradeLevel({ kitchen: 'встроенная', furniture: 'полный', light: 'базовый' }), null);
});

test('невидимый на кадрах признак не считается отсутствующим', () => {
  const seen = { ...M.premium, bath: null };
  assert.strictEqual(gradeLevel(seen), 'A', 'пять признаков из шести — оценка ставится');
});

test('выдуманное значение признака — ошибка, а не молчаливый ноль', () => {
  assert.throws(() => gradeLevel({ stone: 'мрамор', joinery: 'на заказ',
    kitchen: 'встроенная', light: 'базовый' }), /stone/);
});

test('в запись попадает и уровень, и чем он подтверждён', () => {
  // 326035617, ЖК Золотой: продаётся визуализациями
  const lot = { id: 326035617, totalArea: 87.5, priceRub: 280e6, street: 'Софийская', house: '18',
    decoration: 'turnkey', fromDeveloper: true, description: 'с дизайнерским ремонтом' };
  const r = gradeRecord(lot, { markers: M.premium, proof: 'рендер', photosSeen: 26, gradedAt: '2026-08-13' });
  assert.strictEqual(r.level, 'A');
  assert.strictEqual(r.proof, 'рендер');
  assert.strictEqual(r.pricePerM2, 3200000);
  assert.strictEqual(r.state, 'под ключ');
});

test('выдуманный вид подтверждения — ошибка', () => {
  assert.throws(() => gradeRecord({ id: 1 }, { markers: M.premium, proof: 'со слов агента' }), /подтверждение/);
});

process.stdout.write('стоимость доведения до «под ключ»\n');

test('оболочка сравнивается с готовой только после добавления сметы', () => {
  // 327985409: 79.5 м² бетона за 54 млн выглядит дешевле готовых 610 тыс ₽/м²
  const shell = { totalArea: 79.5, priceRub: 54e6 };
  assert.strictEqual(Math.round(shell.priceRub / shell.totalArea), 679245);
  const loaded = loadedPricePerM2(shell, 'бизнес');
  assert.ok(loaded.mid > 830000 && loaded.mid < 840000, `итог метра ${loaded.mid}`);
  assert.ok(loaded.low < loaded.mid && loaded.mid < loaded.high);
});

test('справедливая цена оболочки — от цены готовой минус смета', () => {
  const fair = fairShellPrice(79.5, 767606, 'бизнес');
  assert.ok(fair.mid > 48e6 && fair.mid < 49e6, `${fair.mid}`);
  assert.ok(fair.low < fair.mid, 'дорогой ремонт оставляет оболочке меньше');
});

test('класс отделки меняет смету, а не переписывается втихую', () => {
  assert.ok(finishCost(100, 'делюкс').mid > finishCost(100, 'бизнес').mid * 2);
  assert.throws(() => finishCost(100, 'эконом'), /эконом/);
});

test('фотографии перебивают текст, но расхождение остаётся видимым', () => {
  // 311102437, Костянский 13: в тексте «под ключ», на кадрах кухня стоит,
  // а комнаты пустые — мебели нет
  const lot = { id: 311102437, totalArea: 109.3, priceRub: 381e6, hasFurniture: true,
    description: 'Пентхаус под ключ, полностью укомплектован мебелью и техникой' };
  const r = gradeRecord(lot, { proof: 'фото', gradedAt: '2026-08-13',
    markers: { stone: 'слэб', joinery: 'на заказ', kitchen: 'интегрированная',
      light: 'нет', furniture: 'нет', bath: 'камень и бренд' } });
  assert.strictEqual(r.claimedState, 'под ключ');
  assert.strictEqual(r.observedState, 'оболочка');
  assert.strictEqual(r.state, 'оболочка', 'в итоговое состояние идут кадры');
  assert.strictEqual(r.conflict, true);
});

test('когда кадры молчат, остаётся заявленное — без выдуманного конфликта', () => {
  const lot = { id: 1, hasFurniture: null, description: 'Меблирована, вся техника остаётся' };
  const r = gradeRecord(lot, { proof: 'фото', markers: { stone: 'нет', joinery: 'серийная' } });
  assert.strictEqual(r.state, 'под ключ');
  assert.strictEqual(r.conflict, false);
});

test('«ремонт не сдан» и пустые кадры — не противоречие, а уточнение', () => {
  // 331424705, МОНЭ: текст говорит, когда работы кончатся, кадры — что их нет.
  // Оба правы, и текст здесь точнее
  const lot = { id: 331424705, hasFurniture: true,
    description: 'Новый дизайнерский ремонт. Ремонтные работы завершатся в августе 2026 года' };
  const r = gradeRecord(lot, { proof: 'фото', markers: { stone: 'нет', joinery: 'нет',
    kitchen: 'нет', light: 'нет', furniture: 'нет', bath: 'не отделан' } });
  assert.strictEqual(r.conflict, false);
  assert.strictEqual(r.state, 'ремонт не сдан', 'уточнение из текста не теряется');
});

process.stdout.write('архив и пропажи\n');

test('пропажа считается только внутри своего запроса', () => {
  // залив по Пресне не делает «ушедшими» квартиры, набранные по Дорогомилову
  const arc = { updated: null, flats: {} };
  mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-01', 'дорогомилово');
  const other = lot({ id: 2, fingerprint: 'f2' });
  const r = mergeArchive(arc, [other], '2026-08-02', 'пресня');
  assert.strictEqual(r.gone.length, 0, 'чужой запрос не объявляет пропажу');
});

test('в своём запросе пропажа находится', () => {
  const arc = { updated: null, flats: {} };
  const a1 = lot({ id: 1, fingerprint: 'f1' });
  const a2 = lot({ id: 2, fingerprint: 'f2' });
  mergeArchive(arc, [a1, a2], '2026-08-01', 'дорогомилово');
  const r = mergeArchive(arc, [a1], '2026-08-02', 'дорогомилово');
  assert.strictEqual(r.gone.length, 1);
  assert.strictEqual(r.gone[0].lastSeen, '2026-08-01');
});

test('сводка по архиву считает повторные встречи и движение цен', () => {
  const arc = { updated: null, flats: {} };
  const a1 = lot({ id: 1, fingerprint: 'f1', priceRub: 25e6 });
  mergeArchive(arc, [a1, lot({ id: 2, fingerprint: 'f2' })], '2026-08-01', 'дорогомилово');
  mergeArchive(arc, [{ ...a1, priceRub: 23e6 }], '2026-08-12', 'дорогомилово');
  const s = archiveStat(arc);
  assert.strictEqual(s.flats, 2);
  assert.deepStrictEqual(s.dates, ['2026-08-01', '2026-08-12']);
  assert.strictEqual(s.repeat, 1, 'вторую квартиру видели один раз');
  assert.strictEqual(s.moved, 1);
  assert.strictEqual(s.stale, 1, 'f2 не попала в последний снимок');
  assert.strictEqual(s.sources['дорогомилово'], 2);
});

test('снимок чужого запроса не делает квартиру кандидатом в ушедшие', () => {
  // 15-го числа залили файлы по Пресне; watchlist в тот день не снимали.
  // Сравнение с общей последней датой архива объявило 805 квартир пропавшими
  const arc = { updated: null, flats: {} };
  mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-14', 'дорогомилово');
  mergeArchive(arc, [lot({ id: 2, fingerprint: 'f2' })], '2026-08-15', 'пресня');
  const s = archiveStat(arc);
  assert.strictEqual(s.stale, 0, 'у дорогомилова свой последний снимок — 14-е');
  // а вот если тот же запрос сняли позже и квартиры в нём не оказалось — это кандидат
  mergeArchive(arc, [lot({ id: 3, fingerprint: 'f3' })], '2026-08-16', 'дорогомилово');
  assert.strictEqual(archiveStat(arc).stale, 1);
});

test('квартира без имени запроса не числится кандидатом в ушедшие', () => {
  // 981 квартира архива залита из файлов; ни один запрос их не видел,
  // и молчать про них честнее, чем записывать в пропавшие
  const arc = { updated: null, flats: {} };
  mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-01', null);
  mergeArchive(arc, [lot({ id: 2, fingerprint: 'f2' })], '2026-08-02', 'дорогомилово');
  const s = archiveStat(arc);
  assert.strictEqual(s.stale, 0, 'у f1 нет имени запроса — судить не по чему');
  assert.strictEqual(s.blind, 1);
});

test('запросы одного снимка не объявляют пропавшими друг друга', () => {
  // watchlist снимается в один день подряд: сначала Дорогомилово, потом Пресня.
  // Соседний запрос в тот же день не должен ронять чужие квартиры в «ушедшие»
  const arc = { updated: null, flats: {} };
  mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-01', 'дорогомилово');
  mergeArchive(arc, [lot({ id: 2, fingerprint: 'f2' })], '2026-08-01', 'пресня');
  const r1 = mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-02', 'дорогомилово');
  const r2 = mergeArchive(arc, [lot({ id: 2, fingerprint: 'f2' })], '2026-08-02', 'пресня');
  assert.strictEqual(r1.gone.length, 0);
  assert.strictEqual(r2.gone.length, 0);
});

test('залив без имени запроса не выдумывает пропаж', () => {
  const arc = { updated: null, flats: {} };
  mergeArchive(arc, [lot({ id: 1, fingerprint: 'f1' })], '2026-08-01', 'дорогомилово');
  const r = mergeArchive(arc, [lot({ id: 9, fingerprint: 'f9' })], '2026-08-02', null);
  assert.strictEqual(r.gone.length, 0);
  assert.strictEqual(r.source, null);
});

test('оценка находится по любому объявлению одной квартиры', () => {
  // 327985409 и 331215568 — одна квартира на Профсоюзной; оценка ставилась
  // первому, а в когорту после схлопывания попал второй
  const grades = { '327985409': { id: 327985409, fingerprint: '66566|15|79.5|3', level: 'E', proof: 'фото' } };
  const collapsed = lot({ id: 331215568, fingerprint: '66566|15|79.5|3',
    alsoListedAs: [{ id: 327985409, priceRub: 54e6 }] });
  assert.strictEqual((gradeFor(grades, collapsed) || {}).level, 'E');
});

test('оценка переживает переклейку объявления через отпечаток', () => {
  const grades = { '327985409': { id: 327985409, fingerprint: '66566|15|79.5|3', level: 'E' } };
  const relisted = lot({ id: 999999999, fingerprint: '66566|15|79.5|3', alsoListedAs: [] });
  assert.strictEqual((gradeFor(grades, relisted) || {}).level, 'E');
});

test('чужая квартира чужую оценку не получает', () => {
  const grades = { '327985409': { id: 327985409, fingerprint: '66566|15|79.5|3', level: 'E' } };
  assert.strictEqual(gradeFor(grades, lot({ id: 5, fingerprint: 'другой' })), null);
});

test('выросшая галерея делает оценку устаревшей', () => {
  // 330937338: смотрели 12 кадров дома, продавец добавил съёмку квартиры —
  // и в добавленных оказалась бетонная коробка
  const g = { photosSeen: 12 };
  // мера названа вслух: без разметки галереи сравнение идёт по всем кадрам
  assert.deepStrictEqual(galleryGrew(g, lot({ photos: new Array(21).fill('u') })),
    { was: 12, now: 21, of: 'кадров в галерее' });
  assert.strictEqual(galleryGrew(g, lot({ photos: new Array(12).fill('u') })), null);
  assert.strictEqual(galleryGrew(g, lot({ photos: new Array(9).fill('u') })), null);
  // без записанного числа кадров сравнивать не с чем — молчим, а не врём
  assert.strictEqual(galleryGrew({ photosSeen: null }, lot({ photos: ['u'] })), null);
});

process.stdout.write('разбор настоящих ответов API\n');

/* Все проверки выше — про чистую логику на выдуманных объектах. Если Циан
   поменяет форму ответа, они останутся зелёными, а сбор молча сломается.
   Здесь лежат настоящие срезы ответов, снятые с живого API, и normalize
   разбирает именно их. */
const FIX = require('./fixtures/offers.json');

test('вторичка: адрес, дом, цена, отпечаток', () => {
  const n = normalize(FIX.resale);
  assert.strictEqual(n.id, 330559973);
  assert.ok(n.totalArea > 0 && n.priceRub > 0, `${n.totalArea} / ${n.priceRub}`);
  assert.ok(n.district && n.street, `район ${n.district}, улица ${n.street}`);
  assert.ok(n.fingerprint && n.fingerprint.split('|').length === 4, `отпечаток ${n.fingerprint}`);
  assert.ok(n.lat > 55 && n.lat < 56 && n.lng > 37 && n.lng < 38, `${n.lat}, ${n.lng}`);
  assert.strictEqual(n.saleType, 'free');
});

test('ДДУ: тип сделки и срок сдачи не теряются', () => {
  const n = normalize(FIX.ddu);
  assert.strictEqual(n.saleType, 'fz214');
  assert.ok(n.deadline && n.deadline.year >= 2024, JSON.stringify(n.deadline));
  assert.notStrictEqual(readiness(n), 'сдан');
});

test('студия: пустой roomsCount не превращается в ноль комнат', () => {
  const n = normalize(FIX.studio);
  assert.strictEqual(n.rooms, null);
  assert.ok(n.flatType, `flatType ${n.flatType}`);
  assert.ok(n.totalArea > 0);
});

test('карточка застройщика: decoration и признак застройщика на месте', () => {
  const n = normalize(FIX['card-developer']);
  assert.strictEqual(n.decoration, 'turnkey');
  assert.strictEqual(n.houseFinished, true);
  assert.strictEqual(completeness({ ...n, fromDeveloper: true }), 'под ключ');
});

test('на каждом срезе normalize отдаёт непустые обязательные поля', () => {
  for (const [name, raw] of Object.entries(FIX)) {
    const n = normalize(raw);
    assert.ok(n.id, `${name}: нет id`);
    assert.ok(n.totalArea > 0, `${name}: нет площади`);
    assert.ok(n.floor != null, `${name}: нет этажа`);
    assert.ok(Array.isArray(n.photos), `${name}: photos не массив`);
  }
});

test('у самой линии кольца ответ помечается ненадёжным', () => {
  // Малая Сухаревская — единственный найденный адрес на самом кольце
  const v = ringVerdict({ lat: 55.77199, lng: 37.62906 });
  assert.ok(v.margin < 300, `запас ${v.margin} м — должен быть в полосе сомнения`);
  assert.strictEqual(v.sure, false);
});

test('глубоко внутри кольца ответ надёжен', () => {
  // Софийская набережная, 18 — напротив Кремля
  const v = ringVerdict({ lat: 55.74774, lng: 37.61769 });
  assert.strictEqual(v.inside, true);
  assert.ok(v.sure, `запас ${v.margin} м`);
});

test('далеко снаружи ответ тоже надёжен', () => {
  // Шмитовский проезд
  const v = ringVerdict({ lat: 55.7580, lng: 37.5230 });
  assert.strictEqual(v.inside, false);
  assert.ok(v.sure && v.margin > 1000, `запас ${v.margin} м`);
});

test('без координат — ни ответа, ни уверенности', () => {
  const v = ringVerdict({ lat: null, lng: null });
  assert.strictEqual(v.inside, null);
  assert.strictEqual(v.sure, false);
});

test('полигон калиброван девятью настоящими адресами', () => {
  // координаты из живой выдачи Пресненского; положение каждого адреса
  // относительно Садового известно по карте, а не выведено из полигона
  const inside = [
    ['Малая Бронная 22С1', 55.762763, 37.595276],
    ['Спиридоновка 8', 55.759051, 37.595905],
    ['Вспольный 16С1', 55.76212, 37.589015],
    ['Скатертный 23', 55.756477, 37.592033],
  ];
  const outside = [
    ['Пресненский Вал 27С1', 55.772608, 37.569081],
    ['Шмитовский 39к6', 55.754772, 37.525802],
    ['Красная Пресня 12', 55.761685, 37.573133],
    ['Васильевская 4', 55.770978, 37.587281],
  ];
  for (const [name, lat, lng] of inside) {
    assert.strictEqual(ringVerdict({ lat, lng }).inside, true, `${name} должен быть внутри`);
  }
  for (const [name, lat, lng] of outside) {
    assert.strictEqual(ringVerdict({ lat, lng }).inside, false, `${name} должен быть снаружи`);
  }
  // Новинский 25к1 стоит на самом кольце: ответ обязан сомневаться
  const edge = ringVerdict({ lat: 55.757065, lng: 37.58111 });
  assert.ok(edge.margin < 300 && !edge.sure, `на кольце запас ${edge.margin} м — полоса сомнения`);
});

process.stdout.write('поля из карточки\n');

test('repairType=no из карточки — оболочка, что бы ни писал продавец', () => {
  // 327985409, Профсоюзная 2/22: бетон, а в тексте «квартира бизнес-класса
  // с прекрасными видовыми характеристиками»
  assert.strictEqual(completeness(lot({ repairType: 'no', hasFurniture: true,
    description: 'Квартира в жилом комплексе бизнес-класса с прекрасными видовыми характеристиками' })), 'оболочка');
});

test('дизайнерский ремонт из карточки оболочкой не делает', () => {
  assert.notStrictEqual(completeness(lot({ repairType: 'design',
    description: 'Меблирована, вся техника остаётся' })), 'оболочка');
});

test('счётчик просмотров разбирается со всеми склонениями', () => {
  assert.deepStrictEqual(parseViews('2046 просмотров, 14 за сегодня'), { total: 2046, today: 14 });
  assert.deepStrictEqual(parseViews('451 просмотр'), { total: 451, today: null });
  assert.deepStrictEqual(parseViews('862 просмотра, нет за сегодня'), { total: 862, today: null });
  assert.deepStrictEqual(parseViews(null), { total: null, today: null });
});

test('repairType=design ничего не доказывает: помечают и простую отделку', () => {
  // 327357005, Lucky, 195 млн — самый дорогой метр кутузовской подборки,
  // в карточке repairType=design, на кадрах кухня эконом и крашеные двери
  const lucky = lot({ repairType: 'design', hasFurniture: true,
    description: 'Современная квартира с новой отделкой в ЖК Lucky' });
  assert.strictEqual(completeness(lucky), 'неизвестно',
    'заявленный дизайнерский ремонт не делает комплектность известной');
});

test('два изменения за одну дату не путают начало ряда с концом', () => {
  // 324077709, Ордынский 4А: 18.11.2025 записаны и 500, и 400 млн.
  // Циан отдаёт новыми вперёд, значит 500 — раньше
  const e = { priceHistory: { '324077709': [
    { date: '2026-07-12', price: 399e6 },
    { date: '2026-02-19', price: 420e6 },
    { date: '2026-01-16', price: 370e6 },
    { date: '2025-11-18', price: 400e6 },
    { date: '2025-11-18', price: 500e6 },
  ] } };
  const all = mergedPriceHistory(e);
  assert.strictEqual(all[0].price, 500e6, 'первым должен стоять самый ранний');
  assert.strictEqual(all[all.length - 1].price, 399e6);
  const pct = (all[all.length - 1].price - all[0].price) / all[0].price * 100;
  assert.ok(pct < -20 && pct > -21, `падение ${pct.toFixed(1)}%`);
});

test('ряды из разных объявлений одной квартиры сливаются по дате', () => {
  const e = { priceHistory: {
    '327985409': [{ date: '2026-08-11', price: 54e6 }, { date: '2026-03-19', price: 57e6 }],
    '331215568': [{ date: '2026-08-11', price: 53999999 }, { date: '2026-06-19', price: 56e6 }],
  } };
  const all = mergedPriceHistory(e);
  assert.strictEqual(all.length, 4);
  assert.strictEqual(all[0].date, '2026-03-19');
});

test('одно изменение — не ряд', () => {
  assert.strictEqual(mergedPriceHistory({ priceHistory: { '1': [{ date: '2026-01-01', price: 1 }] } }), null);
  assert.strictEqual(mergedPriceHistory({}), null);
});

process.stdout.write('запрос по номерам\n');

/* offersByIds ходит в сеть, поэтому проверяем его разбор входа и учёт
   потерь на поддельном контексте — без сети, но на настоящей функции. */
const fakeCtx = (plan) => ({ request: { post: async () => {
  const step = plan.shift();
  return { status: () => step.status, json: async () => ({ offersSerialized: step.offers || [] }) };
} } });

test('мусор во входе не выдаётся за снятые объявления', async () => {
  const ctx = fakeCtx([{ status: 200, offers: [{ cianId: 111 }] }]);
  const r = await offersByIds(ctx, [111, 'abc', 0, null, 111]);
  assert.deepStrictEqual(r.bad, ['abc', 0, null]);
  assert.strictEqual(r.offers.length, 1);
  assert.deepStrictEqual(r.missing, []);
});

test('отказавшая пачка попадает в failed, а не исчезает', async () => {
  const ctx = fakeCtx([{ status: 500 }, { status: 500 }, { status: 500 }, { status: 500 }]);
  const r = await offersByIds(ctx, [111, 222]);
  assert.deepStrictEqual(r.failed, [111, 222]);
  assert.strictEqual(r.offers.length, 0);
  assert.deepStrictEqual(r.missing, [], 'непроверенные не считаются пропавшими');
});

test('не вернувшийся номер при удачном ответе — это missing', async () => {
  const ctx = fakeCtx([{ status: 200, offers: [{ cianId: 111 }] }]);
  const r = await offersByIds(ctx, [111, 222]);
  assert.deepStrictEqual(r.missing, [222]);
  assert.deepStrictEqual(r.failed, []);
});

process.stdout.write('раскрытие схлопнутых групп\n');

/* Тот же приём, что и с offersByIds: настоящая функция на поддельном
   контексте. Страницы поиска раздаются по кругу из плана. */
const fakeSearch = (pages) => ({ request: { post: async (url, opt) => {
  const p = opt.data.jsonQuery.page.value;
  return { status: () => 200, json: async () => ({ data: { offersSerialized: pages[p - 1] || [] } }) };
} } });

const groupOffer = (id) => ({ cianId: id, roomsCount: 2, totalArea: 60, bargainTerms: { priceRur: 10e6 },
  building: { deadline: null, buildYear: 2020 }, floorNumber: 5, geo: { address: [] } });

test('упёршаяся в потолок группа сообщает об обрезке, а не молчит', async () => {
  // лидер обещает 200 похожих, потолок — две страницы по 28
  const full = Array.from({ length: 28 }, (_, i) => groupOffer(1000 + i));
  const full2 = Array.from({ length: 28 }, (_, i) => groupOffer(2000 + i));
  const ctx = fakeSearch([full, full2, full, full2]);
  const lots = [{ id: 7, similarCount: 200 }];
  const r = await expandSimilar(ctx, {}, lots, 2);
  assert.strictEqual(r.cut.length, 1, 'обрезка обязана попасть в отчёт');
  assert.strictEqual(r.cut[0].seen, 56);
  assert.strictEqual(r.cut[0].promised, 200);
  assert.strictEqual(r.short.length, 0, 'обрезанная группа не считается ещё и недодавшей');
});

test('обрыв ровно на обещанном числе — не обрыв', async () => {
  // группа из 56, потолок в две страницы: прочитано всё, кричать не о чем
  const full = Array.from({ length: 28 }, (_, i) => groupOffer(1000 + i));
  const full2 = Array.from({ length: 28 }, (_, i) => groupOffer(2000 + i));
  const ctx = fakeSearch([full, full2]);
  const r = await expandSimilar(ctx, {}, [{ id: 7, similarCount: 56 }], 2);
  assert.deepStrictEqual(r.cut, []);
  assert.deepStrictEqual(r.short, []);
  assert.strictEqual(r.added.length, 56);
});

test('группа, отдавшая меньше обещанного, тоже не молчит', async () => {
  const ctx = fakeSearch([[groupOffer(1001), groupOffer(1002)]]);
  const r = await expandSimilar(ctx, {}, [{ id: 7, similarCount: 9 }], 4);
  assert.deepStrictEqual(r.cut, []);
  assert.strictEqual(r.short.length, 1);
  assert.strictEqual(r.short[0].seen, 2);
  assert.strictEqual(r.short[0].promised, 9);
});

test('целиком перечисленная группа не поднимает тревоги', async () => {
  const ctx = fakeSearch([[groupOffer(1001), groupOffer(1002)]]);
  const r = await expandSimilar(ctx, {}, [{ id: 7, similarCount: 2 }], 4);
  assert.deepStrictEqual(r.cut, []);
  assert.deepStrictEqual(r.short, []);
  assert.deepStrictEqual(r.failed, []);
  assert.strictEqual(r.added.length, 2);
});

Promise.all(pending).then(() => {
  process.stdout.write(`\n${passed} проверок пройдено${process.exitCode ? ', есть провалы' : ''}\n`);
});

test('пол и двери добавляют разрешение, но не переписывают прежние буквы', () => {
  // Признаки добавлены после тридцати оценок; проверка в том, что старые
  // записи без них считаются так же, а с ними — так же
  const base = { stone: 'нет', joinery: 'серийная', kitchen: 'встроенная',
    light: 'базовый', furniture: 'полный', bath: 'плитка' };
  assert.strictEqual(gradeLevel(base), 'C');
  assert.strictEqual(gradeLevel({ ...base, floor: 'ламинат', doors: 'в наличнике' }), 'C');
  const rich = { stone: 'слэб', joinery: 'на заказ', kitchen: 'интегрированная',
    light: 'сценарный', furniture: 'полный', bath: 'камень и бренд' };
  assert.strictEqual(gradeLevel(rich), 'A');
  assert.strictEqual(gradeLevel({ ...rich, floor: 'массив ёлочкой', doors: 'скрытые' }), 'A');
});

test('пол сам по себе букву не делает', () => {
  // Проверял обратное и ошибся: при серийной столярке и кухне ёлочка из
  // массива остаётся C. Одна дорогая позиция не вытягивает ремонт, и это
  // правильно — иначе паркет перевешивал бы всё остальное
  const m = { stone: 'нет', joinery: 'серийная', kitchen: 'встроенная',
    light: 'базовый', furniture: 'полный', bath: 'плитка', doors: 'в наличнике' };
  assert.strictEqual(gradeLevel({ ...m, floor: 'ламинат' }), 'C');
  assert.strictEqual(gradeLevel({ ...m, floor: 'массив ёлочкой' }), 'C');
});

test('на границе B и C пол решает', () => {
  // Столярка на заказ и камень есть, а всё прочее серийное — здесь ёлочка
  // против ламината и правда переводит ступень
  const m = { stone: 'керамогранит', joinery: 'на заказ', kitchen: 'встроенная',
    light: 'базовый', furniture: 'полный', bath: 'плитка', doors: 'в наличнике' };
  assert.strictEqual(gradeLevel({ ...m, floor: 'ламинат' }), 'C');
  assert.strictEqual(gradeLevel({ ...m, floor: 'массив ёлочкой' }), 'B');
});

test('номера кадров — список, а не число', () => {
  assert.throws(() => gradeRecord({ id: 1 }, { markers: { stone: 'нет' }, framesSeen: 5 }), /framesSeen/);
});

process.stdout.write('окрестная когорта\n');

test('лоты без года не выпадают, а ложатся в отдельную корзину', () => {
  // условие (год ?? 9999) < 2005 и (год ?? 0) >= 2005 ложно в обе стороны:
  // на живом разборе так молча выпало самое дешёвое предложение округи
  const at = { lat: 55.76171, lng: 37.54038 };
  const lots = [
    lot({ id: 1, lat: 55.7620, lng: 37.5410, buildYear: 1965 }),
    lot({ id: 2, lat: 55.7625, lng: 37.5400, buildYear: 2020 }),
    lot({ id: 3, lat: 55.7615, lng: 37.5395, buildYear: null }),
  ];
  const c = buildCohort(lots, at, 1500);
  assert.strictEqual(c.old.length, 1);
  assert.strictEqual(c.fresh.length, 1);
  assert.strictEqual(c.unknownYear.length, 1, 'без года — отдельная корзина, не мусор');
  assert.strictEqual(c.old.length + c.fresh.length + c.unknownYear.length, c.near.length, 'ни один лот круга не потерян');
});

test('лот без координат не исчезает молча', () => {
  const at = { lat: 55.76171, lng: 37.54038 };
  const c = buildCohort([lot({ id: 1, lat: null, lng: null })], at, 1500);
  assert.strictEqual(c.near.length, 0);
  assert.strictEqual(c.noCoords.length, 1);
});

test('радиус меряется честно', () => {
  const at = { lat: 55.76171, lng: 37.54038 };
  // ~111 м на 0.001 широты
  const c = buildCohort([lot({ id: 1, lat: 55.76271, lng: 37.54038 })], at, 1500);
  assert.ok(c.near[0].distM > 100 && c.near[0].distM < 125, `ожидал ~111 м, получил ${c.near[0].distM}`);
  assert.strictEqual(buildCohort([lot({ id: 1, lat: 55.78, lng: 37.54038 })], at, 1500).near.length, 0);
});

process.stdout.write('возраст ремонта и вывод\n');

test('возраст без вывода не принимается', () => {
  // каталожная карточка без суждения бесполезна для анализа
  assert.throws(() => gradeRecord({ id: 1 }, { age: 'устаревший' }), /вывода нет/);
  assert.throws(() => gradeRecord({ id: 1 }, { age: 'свежий', verdict: 'ок' }), /вывода нет/, 'отписка короче 40 символов — не вывод');
});

test('возраст с выводом записывается и не трогает букву', () => {
  const m = { stone: 'нет', joinery: 'серийная', kitchen: 'встроенная', light: 'базовый',
    furniture: 'полный', bath: 'плитка', floor: 'ламинат', doors: 'в наличнике' };
  const v = 'Дорогой ремонт 2008 года: вложено много, вернётся ноль — покупатель вычтет демонтаж.';
  const r = gradeRecord({ id: 1 }, { markers: m, age: 'устаревший', verdict: v });
  assert.strictEqual(r.level, 'C');
  assert.strictEqual(r.age, 'устаревший');
  assert.strictEqual(r.verdict, v);
  // тот же набор признаков без возраста даёт ту же букву: оси ортогональны
  assert.strictEqual(gradeRecord({ id: 1 }, { markers: m }).level, 'C');
});

test('чужое значение возраста — ошибка, а не тихий null', () => {
  assert.throws(() => gradeRecord({ id: 1 }, { age: 'старый', verdict: 'достаточно длинный вывод про эту квартиру и её судьбу' }), /возраст/);
});

test('перечень работ проверяется по словарю и сводится в объём', () => {
  assert.throws(() => gradeRecord({ id: 1 }, { works: { крыша: 'менять' } }), /works/);
  assert.throws(() => gradeRecord({ id: 1 }, { works: { окна: 'выкинуть' } }), /works\.окна/);
  assert.strictEqual(worksScope({ окна: 'менять', полы: 'менять', кухня: 'менять', санузел: 'менять' }), 'капитальный');
  assert.strictEqual(worksScope({ окна: 'оставить', стены: 'освежить', полы: 'менять' }), 'частичный');
  assert.strictEqual(worksScope({ окна: 'оставить', стены: 'оставить' }), 'косметика');
  assert.strictEqual(worksScope({}), null);
  assert.strictEqual(worksScope(null), null);
});

test('паспорт называет устаревший ремонт пассивом', () => {
  const v = 'Целый ремонт прошлой эпохи: покупатель заплатит как за жилое и вычтет переделку.';
  const stale = profileLot(lot({ priceRub: 20e6, totalArea: 35 }),
    { grade: { level: 'C', proof: 'фото', state: 'под ключ', age: 'устаревший', verdict: v,
      markers: { kitchen: 'встроенная', furniture: 'полный' } } });
  assert.ok(stale.gaps.some((g) => g.name === 'ремонт не актив'));
  assert.strictEqual(stale.finish.age, 'устаревший');
  assert.strictEqual(stale.finish.verdict, v);
  const fresh = profileLot(lot({ priceRub: 20e6, totalArea: 35 }),
    { grade: { level: 'C', proof: 'фото', state: 'под ключ', age: 'свежий', verdict: v,
      markers: { kitchen: 'встроенная', furniture: 'полный' } } });
  assert.ok(!fresh.gaps.some((g) => g.name === 'ремонт не актив'));
});

process.stdout.write('вторая ось: дом и паспорт лота\n');

test('класс дома считается долей от максимума, а не одним признаком', () => {
  assert.strictEqual(houseClass({ facade: 'камень', lobby: 'дизайнерское', yard: 'закрытый ландшафт',
    amenities: 'много', parking: 'подземный' }), 'делюкс');
  assert.strictEqual(houseClass({ facade: 'штукатурка', lobby: 'простое', yard: 'благоустроенный',
    amenities: 'нет', parking: 'подземный' }), 'бизнес');
  assert.strictEqual(houseClass({ facade: 'панель', lobby: 'простое', yard: 'парковка',
    amenities: 'нет', parking: 'наземный' }), 'комфорт');
  // один дорогой фасад панельную коробку в премиум не вытягивает
  assert.strictEqual(houseClass({ facade: 'камень', lobby: 'нет', yard: 'парковка',
    amenities: 'нет', parking: 'нет' }), 'комфорт');
});

test('типовая кладка не вытягивает хрущёвку в бизнес', () => {
  // силикатный кирпич 1960 года — не клинкер клубного дома: без «типового»
  // значения фасад давал 2 балла и пятиэтажка выходила бизнес-классом
  assert.strictEqual(houseClass({ facade: 'типовой', lobby: 'нет', yard: 'благоустроенный' }), 'комфорт');
  assert.strictEqual(houseClass({ facade: 'типовой', lobby: 'простое', yard: 'парковка' }), 'комфорт');
  // типовой фасад решает класс в одиночку — по состоянию, как E у квартир:
  // продавцы старого фонда показывают дом одним кадром
  assert.strictEqual(houseClass({ facade: 'типовой' }), 'комфорт');
});

test('меньше трёх признаков — класса нет', () => {
  assert.strictEqual(houseClass({ facade: 'камень', lobby: 'дизайнерское' }), null);
  assert.strictEqual(houseClass({}), null);
  assert.strictEqual(houseClass(null), null);
});

test('чужое значение в признаке дома — ошибка, а не тихий ноль', () => {
  assert.throws(() => houseClass({ facade: 'мрамор', lobby: 'простое', yard: 'парковка' }), /facade/);
});

test('паспорт видит разрыв между отделкой и домом в обе стороны', () => {
  const rich = profileLot(lot({ priceRub: 250e6, totalArea: 91 }),
    { grade: { level: 'A', proof: 'фото', state: 'под ключ' }, house: { class: 'комфорт', markers: {} } });
  assert.ok(rich.gaps.some((g) => g.name === 'отделка дороже дома'));

  const bare = profileLot(lot({ priceRub: 325e6, totalArea: 101 }),
    { grade: { level: 'E', proof: 'фото', state: 'оболочка' }, house: { class: 'делюкс', markers: {} } });
  assert.ok(bare.gaps.some((g) => g.name === 'дом дороже отделки'));

  const fine = profileLot(lot({ priceRub: 250e6, totalArea: 91 }),
    { grade: { level: 'A', proof: 'фото', state: 'под ключ' }, house: { class: 'премиум', markers: {} } });
  assert.deepStrictEqual(fine.gaps.filter((g) => g.name.includes('дороже')), []);
});

test('паспорт не выдумывает медиану класса на трёх соседях', () => {
  const peers = [1, 2, 3].map((i) => ({ id: i, pricePerM2: 1e6, houseClass: 'бизнес' }));
  const p = profileLot(lot({ id: 9, priceRub: 300e6, totalArea: 60 }),
    { house: { class: 'бизнес', markers: {} }, peers });
  assert.strictEqual(p.price.vsClassPct, null, 'на трёх соседях медианы нет');
  const more = peers.concat([{ id: 4, pricePerM2: 1e6, houseClass: 'бизнес' }]);
  const p2 = profileLot(lot({ id: 9, priceRub: 300e6, totalArea: 60 }),
    { house: { class: 'бизнес', markers: {} }, peers: more });
  assert.strictEqual(p2.price.vsClassPct, 400);
});

test('паспорт называет непроверенный товар и лишние объявления', () => {
  const p = profileLot(lot({ priceRub: 300e6, totalArea: 100, daysOnMarket: 900 }), {
    grade: { level: 'A', proof: 'рендер', state: 'под ключ' },
    archiveEntry: { firstSeen: '2024-01-01', listings: [{ id: 1, price: 300e6, seen: '2026-08-13' },
      { id: 2, price: 300e6, seen: '2026-08-14' }] },
  });
  assert.ok(p.gaps.some((g) => g.name === 'товар не подтверждён'));
  assert.ok(p.gaps.some((g) => g.name === 'несколько объявлений'));
  assert.ok(p.gaps.some((g) => g.name === 'стоит без движения'));
});

process.stdout.write('раскрытие схлопнутых групп\n');

test('из группы не берётся то, что не подходит под запрос', () => {
  // multi_id отдаёт группу целиком и фильтры игнорирует: на запросе
  // apartment=false раскрытие притащило 178 апартаментов
  const q = { apartment: { type: 'term', value: false },
    room: { type: 'terms', value: [2, 3] },
    total_area: { type: 'range', value: { gte: 70, lte: 92 } } };
  assert.strictEqual(matchesQuery(lot({ isApartments: true, rooms: 3, totalArea: 80 }), q), false);
  assert.strictEqual(matchesQuery(lot({ isApartments: false, rooms: 1, totalArea: 80 }), q), false);
  assert.strictEqual(matchesQuery(lot({ isApartments: false, rooms: 3, totalArea: 95 }), q), false);
  assert.strictEqual(matchesQuery(lot({ isApartments: false, rooms: 3, totalArea: 80 }), q), true);
});

test('чего проверить нечем — то и не отбрасывается', () => {
  const q = { house_year: { type: 'range', value: { gte: 2018 } } };
  assert.strictEqual(matchesQuery(lot({ buildYear: null, deadline: null }), q), true);
  assert.strictEqual(matchesQuery(lot({ buildYear: 2005 }), q), false);
  assert.strictEqual(matchesQuery(lot({ buildYear: null, deadline: { year: 2025 } }), q), true);
});

test('пустой запрос ничего не отбрасывает', () => {
  assert.strictEqual(matchesQuery(lot({ isApartments: true, rooms: 9 }), {}), true);
});

process.stdout.write('метро\n');

/* Станции из живой выдачи: у одного лота их бывает 15, и раньше в записи
   оставалась одна. Здесь проверяется, что выбрасывать перестали. */
const und = (name, time, o = {}) => ({ name, time, id: o.id ?? name.length,
  lineId: o.lineId ?? 1, lineType: o.lineType ?? null,
  transportType: o.transportType ?? 'walk', underConstruction: !!o.underConstruction,
  releaseYear: o.releaseYear ?? null });

test('пересадочный узел считается линиями, а не строчками', () => {
  // Большая Дмитровка: 15 станций на 8 линий — Театральная и Охотный Ряд
  // идут отдельными строчками, но это один узел
  const m = metroSummary([
    und('Театральная', 4, { lineId: 9 }), und('Охотный Ряд', 5, { lineId: 1 }),
    und('Площадь Революции', 5, { lineId: 3 }), und('Кузнецкий Мост', 7, { lineId: 9 }),
    und('Лубянка', 8, { lineId: 1 }),
  ]);
  assert.strictEqual(m.walkStations, 5);
  assert.strictEqual(m.walkLines, 3);
});

test('МЦК и МЦД в счёт метро не идут, но и не пропадают', () => {
  // 211 записей из 941 на живой выдаче — это не метро: интервалы другие
  const m = metroSummary([
    und('Шелепиха', 5, { lineId: 8 }),
    und('Деловой центр', 10, { lineId: 99, lineType: 'mck' }),
    und('Тестовская', 12, { lineId: 98, lineType: 'mcd4' }),
  ]);
  assert.strictEqual(m.walkStations, 1);
  assert.strictEqual(m.walkLines, 1);
  assert.deepStrictEqual(m.rail, ['МЦД-4', 'МЦК']);
});

test('строящаяся станция не считается доступностью, но названа со сроком', () => {
  // Академическая (2026) нашлась у 4 лотов: обещание в графе наблюдения —
  // та же болезнь, что рендер вместо фото у отделки и у дома
  const m = metroSummary([
    und('Шаболовская', 9, { lineId: 6 }),
    und('Академическая', 3, { lineId: 11, underConstruction: true, releaseYear: 2026 }),
  ]);
  assert.strictEqual(m.walkStations, 1);
  assert.strictEqual(m.name, 'Шаболовская');
  assert.deepStrictEqual(m.planned, [{ name: 'Академическая', year: 2026 }]);
});

test('заголовочной берётся ближайшая пешая, а не первая в списке', () => {
  // Циан отдаёт список неотсортированным у 5 лотов из 28
  const m = metroSummary([und('Улица 1905 года', 15, { lineId: 7 }), und('Краснопресненская', 2, { lineId: 5 })]);
  assert.strictEqual(m.name, 'Краснопресненская');
  assert.strictEqual(m.minutes, 2);
  assert.strictEqual(m.byFoot, true);
});

test('если пешком станций нет — это сказано, а не выдано за пешие минуты', () => {
  const m = metroSummary([und('Москва-Сити', 3, { transportType: 'transport', lineId: 8 })]);
  assert.strictEqual(m.byFoot, false);
  assert.strictEqual(m.walkStations, 0);
  const gaps = profileLot(lot({ metro: m, lat: 55.75, lng: 37.6 })).gaps.map((g) => g.name);
  assert.ok(gaps.includes('до метро транспортом'));
});

test('пешком только рельсы — это не метро в шаге ходьбы', () => {
  const m = metroSummary([und('Тестовская', 6, { lineId: 98, lineType: 'mcd4' })]);
  assert.strictEqual(m.walkStations, 0);
  assert.deepStrictEqual(m.rail, ['МЦД-4']);
  const gaps = profileLot(lot({ metro: m, lat: 55.75, lng: 37.6 })).gaps.map((g) => g.name);
  assert.ok(gaps.includes('вместо метро рельсы'));
});

test('станций нет вовсе — null, а не выдуманный ноль', () => {
  assert.strictEqual(metroSummary([]), null);
  assert.strictEqual(metroSummary(null), null);
  assert.strictEqual(metroSummary(undefined), null);
});

test('строка про метро называет все части, ни одну не сводя к другой', () => {
  const s = metroLine(metroSummary([
    und('Краснопресненская', 2, { lineId: 5 }), und('Баррикадная', 6, { lineId: 7 }),
    und('Деловой центр', 12, { lineId: 99, lineType: 'mck' }),
    und('Суворовская', 4, { lineId: 11, underConstruction: true, releaseYear: 2026 }),
  ]));
  assert.ok(s.includes('Краснопресненская, 2 мин пешком'));
  assert.ok(s.includes('2 ст. на 2 лин.'));
  assert.ok(s.includes('МЦК'));
  assert.ok(s.includes('строится: Суворовская 2026'));
});

test('старая запись про метро не выдаётся за ноль линий', () => {
  // файлы выдачи до этой правки хранят только ближайшую станцию: линии там
  // неизвестны, а «0» читалось бы как наблюдение
  assert.strictEqual(metroCell({ name: 'Курская', minutes: 8, byFoot: true }).trim(), '8м/?');
  assert.strictEqual(metroCell({ name: 'Курская', minutes: 8, byFoot: true, walkLines: 3, walkStations: 4 }).trim(), '8м/3л');
  assert.strictEqual(metroCell(null).trim(), '—');
});

process.stdout.write('кадры: планировка — не квартира\n');

const ph = (n, layouts = []) => Array.from({ length: n }, (_, i) => ({
  fullUrl: `https://x/${i + 1}.jpg`, isLayout: layouts.includes(i + 1) }));

test('планировки отделяются от кадров квартиры и называются номерами', () => {
  // на 244 живых лотах планировка нашлась у 203, и чаще всего кадром №1
  const k = photoKinds(ph(10, [1, 7]));
  assert.strictEqual(k.photosRooms, 8);
  assert.deepStrictEqual(k.layoutFrames, [1, 7]);
  assert.strictEqual(k.photosClassified, true);
});

test('неразмеченная галерея — это «неизвестно», а не «планировок нет»', () => {
  // у 41 лота из 244 isLayout пуст у всех кадров разом: считать это нулём
  // значило бы утверждать то, чего не видел
  const k = photoKinds(Array.from({ length: 12 }, () => ({ fullUrl: 'x', isLayout: null })));
  assert.strictEqual(k.photosRooms, null);
  assert.strictEqual(k.photosClassified, false);
  assert.deepStrictEqual(k.layoutFrames, []);
});

test('пустая галерея не выдаёт нулей', () => {
  assert.deepStrictEqual(photoKinds([]), { photosRooms: null, layoutFrames: [], photosClassified: false });
  assert.deepStrictEqual(photoKinds(null), { photosRooms: null, layoutFrames: [], photosClassified: false });
});

test('порог «мало фото» считает квартиру, а не строчки галереи', () => {
  // 9 кадров, из них 2 планировки — квартиры показано семь
  const l = lot({ description: 'ремонт под ключ', photosCount: 9, photosRooms: 7, layoutFrames: [1, 2] });
  const f = assessRepair(l).flags.join(' ');
  assert.ok(/мало кадров квартиры \(7 из 9, планировок 2\)/.test(f), f);
  // размеченная галерея из восьми комнатных кадров порога не задевает
  const ok = assessRepair(lot({ description: 'ремонт под ключ', photosCount: 10, photosRooms: 8, layoutFrames: [1, 2] }));
  assert.ok(!ok.flags.some((x) => /мало кадров/.test(x)), ok.flags.join(' '));
});

test('где галерея не размечена — сказано, что планировки не отделены', () => {
  const f = assessRepair(lot({ description: 'ремонт под ключ', photosCount: 6, photosRooms: null,
    photos: ['a', 'b'] })).flags.join(' ');
  assert.ok(/галерея не размечена/.test(f), f);
});

test('добавленная планировка не сбрасывает оценку', () => {
  // продавец, доложивший чертёж, товар не изменил
  const grade = { photosSeen: 12, photosRoomsSeen: 11 };
  assert.strictEqual(galleryGrew(grade, { photos: ph(13, [1, 5]), photosRooms: 11 }), null);
  // а новые кадры квартиры — меняют
  const g = galleryGrew(grade, { photos: ph(15, [1]), photosRooms: 14 });
  assert.deepStrictEqual(g, { was: 11, now: 14, of: 'кадров квартиры' });
});

test('без размеченных кадров сравнение идёт по галерее и так и названо', () => {
  const g = galleryGrew({ photosSeen: 12 }, { photos: ph(14), photosRooms: null });
  assert.deepStrictEqual(g, { was: 12, now: 14, of: 'кадров в галерее' });
  assert.strictEqual(galleryGrew({ photosSeen: 12 }, { photos: ph(12), photosRooms: null }), null);
});

process.stdout.write('\nотпечаток галереи: те же ли это кадры\n');

/* Ссылки Циана несут номер снимка: …/images/2893840431-1.jpg. Второй
   номер — размер, он в отпечаток не идёт. */
const url = (id, size = 1) => `https://images.cdn-cian.ru/images/${id}-${size}.jpg`;
const gal = (ids, layouts = []) => ({
  photos: ids.map((i) => url(i)),
  photosCount: ids.length,
  layoutFrames: layouts,
  photosClassified: layouts.length > 0 || undefined,
});

test('номер снимка берётся из ссылки, размер — не берётся', () => {
  assert.strictEqual(photoIdent(url(2893840431, 1)), '2893840431');
  assert.strictEqual(photoIdent(url(2893840431, 2)), '2893840431');
  // чужая форма ссылки не отбрасывается молча: она сама становится ключом
  assert.strictEqual(photoIdent('https://x/abc.png'), 'https://x/abc.png');
});

test('перестановка кадров галереи отпечаток не меняет', () => {
  // порядок в галерее продавец двигает мышью; товар от этого не другой
  const a = galleryKey(gal([11, 22, 33]));
  const b = galleryKey(gal([33, 11, 22]));
  assert.strictEqual(a.key, b.key);
  assert.strictEqual(a.n, 3);
});

test('подмена кадров при том же их числе ловится отпечатком, а счётчиком — нет', () => {
  // ровно этот случай счётчик пропускал: восемь тусклых кадров заменили
  // восемью свежими, photosSeen не изменился, оценка осталась от старых
  const was = gradeRecord({ id: 1, ...gal([1, 2, 3, 4, 5, 6, 7, 8]) },
    { photosSeen: 8, markers: {}, gradedAt: '2026-01-01' });
  const now = gal([91, 92, 93, 94, 95, 96, 97, 98]);
  assert.strictEqual(galleryGrew(was, now), null, 'счётчик молчит — в этом и была дыра');
  const d = galleryDiff(was, now);
  assert.strictEqual(d.verdict, 'смотреть');
  assert.ok(/заменены/.test(d.why), d.why);
});

test('та же галерея — «пропустить», и сказано, какой мерой мерили', () => {
  const same = gal([5, 6, 7]);
  const was = gradeRecord({ id: 1, ...same }, { photosSeen: 3, markers: {}, gradedAt: '2026-01-01' });
  const d = galleryDiff(was, same);
  assert.strictEqual(d.verdict, 'пропустить');
  assert.ok(/всей галереи/.test(d.why), d.why);
});

test('добавленная планировка на пересмотр не отправляет', () => {
  // размеченная галерея: ключ считается по кадрам квартиры
  const before = gal([1, 2, 3], [1]);
  const was = gradeRecord({ id: 1, ...before }, { photosSeen: 3, photosRoomsSeen: 2, markers: {}, gradedAt: '2026-01-01' });
  assert.strictEqual(was.galleryKeyOf, 'кадров квартиры');
  const after = { photos: [url(1), url(9), url(2), url(3)], photosCount: 4, layoutFrames: [1, 2], photosClassified: true };
  assert.strictEqual(galleryDiff(was, after).verdict, 'пропустить');
});

test('запись без отпечатка — «неизвестно», а не «пропустить»', () => {
  // так выглядят все оценки, поставленные до появления ключа: молчаливое
  // «пропустить» на них означало бы, что пересъёмку не увидим уже никогда
  const old = { photosSeen: 3, level: 'B' };
  const d = galleryDiff(old, gal([1, 2, 3]));
  assert.strictEqual(d.verdict, 'неизвестно');
  assert.ok(/не записан/.test(d.why), d.why);
  // а если кадров с тех пор прибавилось — старая проверка всё ещё работает
  assert.strictEqual(galleryDiff(old, gal([1, 2, 3, 4])).verdict, 'смотреть');
});

test('ключи, снятые разной мерой, между собой не сравниваются', () => {
  // Циан размечает isLayout не у всех лотов и не всегда сразу: ключ по
  // всей галерее и ключ по кадрам квартиры — разные величины
  const was = gradeRecord({ id: 1, ...gal([1, 2, 3]) }, { photosSeen: 3, markers: {}, gradedAt: '2026-01-01' });
  const now = { photos: [url(1), url(2), url(3)], photosCount: 3, layoutFrames: [1], photosClassified: true };
  const d = galleryDiff(was, now);
  assert.strictEqual(d.verdict, 'неизвестно');
  assert.ok(/меры разные/.test(d.why), d.why);
});

test('оценки нет — смотреть, и причина названа именно так', () => {
  assert.deepStrictEqual(galleryDiff(null, gal([1, 2])), { verdict: 'смотреть', why: 'оценки нет' });
  assert.strictEqual(galleryDiff(null, { photos: [] }).verdict, 'смотреть');
});

test('заглушка без кадров не затирает записанный отпечаток', () => {
  // grade без --lots подаёт lot из одного id: ключ выходит пустым
  const stub = gradeRecord({ id: 1 }, { photosSeen: null, markers: {}, gradedAt: '2026-01-01' });
  assert.strictEqual(stub.galleryKey, null);
  const full = gradeRecord({ id: 1, ...gal([1, 2, 3]) }, { photosSeen: 3, markers: {}, gradedAt: '2026-01-01' });
  assert.ok(full.galleryKey);
});

process.stdout.write('\nцена свипа\n');

test('пачка 19 дороже пачки 5: накопленный контекст растёт быстрее экономии', () => {
  // это и была ошибка совета «берите пачки покрупнее»: постоянные расходы
  // линейны, а контекст квадратичен
  const b5 = sweepCost(115, 5), b19 = sweepCost(115, 19), b29 = sweepCost(115, 29);
  assert.ok(b5.perLot < b19.perLot, `${b5.perLot} против ${b19.perLot}`);
  assert.ok(b19.perLot < b29.perLot, `${b19.perLot} против ${b29.perLot}`);
});

test('формула сходится с замером дорогого флота', () => {
  // замерено: 792к токенов на 105 лотов пачками по ~19
  const got = sweepCost(105, 19).tokens;
  assert.ok(Math.abs(got - 792000) / 792000 < 0.1, `формула даёт ${got}, замерено 792000`);
});

test('пачка из одного листа дороже средней: постоянные расходы не размазаны', () => {
  assert.ok(sweepCost(115, 1).perLot > sweepCost(115, 5).perLot);
});

test('ужатый лист удешевляет свип примерно вдвое', () => {
  const now = sweepCost(115, 8).tokens, small = sweepCost(115, 8, 1526).tokens;
  assert.ok(small < now / 1.5, `${small} против ${now}`);
});
