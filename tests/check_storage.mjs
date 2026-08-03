// ХРАНИЛИЩЕ: отказ записи, автобэкап, совместимость бэкапа.
//
//   node tests/check_storage.mjs            — прогнать
//   NEGATIVE=1 node tests/check_storage.mjs — негативный контроль (низ файла)
//   CIAN_CONTENT_JS=/путь/content.js …      — проверить другую копию
//
// ЗАЧЕМ ЭТОТ НАБОР. До версии 2.17 `saveHistory`/`saveSnapshots` ловили
// исключение и молча его выбрасывали (`catch (e) { /* ignore */ }`). Квота
// Chrome — 5 MiB на ОРИГИН, ориджин здесь www.cian.ru (делим с самим сайтом),
// замер — 805 символов на квартиру, то есть потолок ~3 250 квартир. Квартиры
// копятся по ВСЕМ выгруженным ЖК, потому что fpOf включает jkId: три-пять
// крупных ЖК исчерпывают квоту за недели. У дошедшего до потолка пользователя
// история переставала обновляться НАВСЕГДА и БЕЗ ЕДИНОГО ПРИЗНАКА — а это
// единственные невосстановимые данные проекта (месяцы реальных сроков
// экспозиции и цен). Здесь проверяется, что отказ виден и что бэкап уезжает
// файлом раньше, чем данные потеряны.
//
// Слой хранения берётся тем же срезом content.js, что и книга
// (tests/check_workbook.mjs), и получает тот же стаб localStorage.

import fs from "fs";
import path from "path";
import { spawnSync } from "child_process";
import { fileURLToPath } from "url";
import { makeFactory, makeDateStub, makeStorage } from "./check_workbook.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CONTENT_JS = process.env.CIAN_CONTENT_JS || path.join(ROOT, "extension", "content.js");

let failed = 0;
const fail = (m) => { console.error("  ✗ " + m); failed++; };
const pass = (m) => console.log("  ✓ " + m);
const check = (cond, ok, bad) => (cond ? pass(ok) : fail(bad));
const eq = (a, b, what) => check(a === b, `${what}: ${JSON.stringify(a)}`,
  `${what}: ожидалось ${JSON.stringify(b)}, получено ${JSON.stringify(a)}`);
const section = (t) => console.log(`\n── ${t} ${"─".repeat(Math.max(0, 66 - t.length))}`);

const DAY = 86400 * 1000;
const T0 = Date.UTC(2026, 6, 1, 12, 0, 0);            // фиксированное «сейчас»

// Стенд: свежий срез content.js на своём хранилище и своих часах.
function bench({ storage, nowMs = T0 } = {}) {
  const store = storage || makeStorage();
  const api = makeFactory(CONTENT_JS)(makeDateStub(nowMs), store);
  return { api, store };
}

// История на N квартир той же формы, что пишет enrichExposure, и в ЗРЕЛОМ
// состоянии: три переподачи, три cianId, полный priceLog (12 записей — потолок
// mergeHistoryFlats). Мерить размер на свежей записи было бы самообманом:
// потолок квоты определяет именно зрелая.
function historyOf(n, { lastSeen = Math.floor(T0 / 1000) } = {}) {
  const s = Math.floor(T0 / 1000), flats = {};
  for (let i = 0; i < n; i++) {
    flats[`170411${i}|кутузовскийпроспект12к${i % 7}|${i % 20}|${(40 + i % 60).toFixed(1)}|Вторичка`] = {
      firstSeen: s - 300 * 86400,
      minAdded: s - 320 * 86400,
      lastSeen,
      addeds: [s - 320 * 86400, s - 180 * 86400, s - 60 * 86400],
      cianIds: [300000000 + i, 310000000 + i, 320000000 + i],
      priceLog: Array.from({ length: 12 }, (_, k) => ({ ts: s - (12 - k) * 20 * 86400, p: 12000000 + i * 1000 + k * 50000 })),
    };
  }
  return { flats };
}

// ===========================================================================
// 1. Отказ записи больше не проглатывается
// ===========================================================================
function testFaultVisible() {
  section("Отказ записи виден: QuotaExceededError не проглатывается");

  // Потолок подобран так, чтобы история заведомо не влезла.
  const store = makeStorage(null, { quotaChars: 4000 });
  const { api } = bench({ storage: store });
  const big = historyOf(200);

  const ok = api.saveHistory(big);
  check(ok === false,
    "saveHistory при переполнении возвращает false, а не undefined (раньше отказ был неотличим от успеха)",
    `saveHistory вернул ${JSON.stringify(ok)} — вызывающий не может отличить отказ от успеха`);

  const f = api.storageFault();
  check(!!f, "отказ записан в storageFault", "storageFault пуст — отказ никуда не попал");
  if (f) {
    eq(f.key, api.HKEY, "ключ отказа");
    eq(f.quota, true, "отказ распознан как переполнение квоты");
  }
  // Ключевое следствие: данные НЕ записались. Раньше об этом никто не узнавал.
  eq(store.getItem(api.HKEY), null, "в хранилище ничего не появилось");
  // Зеркало в памяти НАМЕРЕННО оставляет несохранённое: иначе прогон потерял бы
  // и обогащение этого запуска тоже. Расхождение «в памяти есть, на диске нет»
  // не прячется — его показывает storageFault, красная строка в панели и
  // немедленный автобэкап файлом.
  eq(Object.keys(api.loadHistory().flats).length, 200,
    "прогон продолжает работать на зеркале в памяти (иначе потеряли бы и текущее обогащение)");
  check(store.getItem(api.HKEY) == null && api.storageFault() != null,
    "но на диске этого нет, и расхождение помечено storageFault — молчания не осталось",
    "расхождение памяти и диска ничем не помечено");

  // Успешная запись того же ключа снимает флаг: иначе панель показывала бы
  // «хранилище заполнено» до перезагрузки страницы даже после чистки.
  const ok2 = api.saveHistory(historyOf(2));
  eq(ok2, true, "маленькая история записывается");
  eq(api.storageFault(), null, "успешная запись того же ключа снимает флаг отказа");
}

// ===========================================================================
// 2. Маркер бэкапа проходит там, где не проходит история
// ===========================================================================
// Ради этого маркер и вынесен в отдельный ключ: если бы он лежал внутри
// истории, при полной квоте автобэкап терял бы память ровно тогда, когда он
// единственная защита.
function testMarkerSurvives() {
  section("Крошечный ключ маркера пишется при заполненной квоте");

  const store = makeStorage(null, { quotaChars: 4000 });
  const { api } = bench({ storage: store });

  eq(api.saveHistory(historyOf(200)), false, "история не влезла");
  api.saveMeta({ lastBackupAt: T0, lastBackupFlats: 200 });
  const m = api.loadMeta();
  eq(m.lastBackupAt, T0, "маркер бэкапа записан, несмотря на переполнение");
  eq(m.lastBackupFlats, 200, "число квартир на момент бэкапа записано");
  check(store.getItem(api.MKEY) != null && store.getItem(api.HKEY) == null,
    "маркер в хранилище есть, история — нет: ключи независимы",
    "маркер и история делят судьбу — вынос в отдельный ключ не работает");
}

// ===========================================================================
// 3. Правило «пора делать бэкап»
// ===========================================================================
function testBackupDue() {
  section("backupDue: когда автобэкап срабатывает");

  const { api } = bench();
  const due = (meta, flats, nowMs = T0, fault = null) => api.backupDue(meta, flats, nowMs, fault);

  eq(due({}, 40).due, false, "40 квартир, бэкапа не было: терять нечего, файл не навязываем");
  eq(due({}, 100).due, true, "100 квартир без единого бэкапа: пора");
  eq(due({ lastBackupAt: T0 - 6 * DAY, lastBackupFlats: 500 }, 520).due, false,
    "6 дней назад, рост 4%: не пора");
  eq(due({ lastBackupAt: T0 - 8 * DAY, lastBackupFlats: 500 }, 520).due, true,
    "8 дней назад: пора по возрасту");
  eq(due({ lastBackupAt: T0 - 1 * DAY, lastBackupFlats: 500 }, 601).due, true,
    "вчера, но история выросла на 20%: пора по росту");
  eq(due({ lastBackupAt: T0 - 1 * DAY, lastBackupFlats: 500 }, 600).due, false,
    "ровно 20% — ещё не пора (порог строгий)");

  // Переполнение бьёт любой порог, включая нижний: терять уже начали.
  const hot = due({ lastBackupAt: T0, lastBackupFlats: 10 }, 10, T0, { quota: true });
  eq(hot.due, true, "квота заполнена: бэкап нужен независимо от возраста и размера");
  check(/заполнен/.test(hot.why), `причина названа: «${hot.why}»`,
    `причина отказа не названа: «${hot.why}»`);

  // Причина попадает в текст для пользователя — проверяем, что она непустая.
  const aged = due({ lastBackupAt: T0 - 30 * DAY, lastBackupFlats: 500 }, 520);
  check(/30/.test(aged.why), `причина по возрасту содержит число дней: «${aged.why}»`,
    `причина по возрасту невнятная: «${aged.why}»`);
}

// ===========================================================================
// 4. Маркер отодвигает следующий автобэкап
// ===========================================================================
function testMarkerCycle() {
  section("Цикл: бэкап → маркер → следующий не сразу");

  const { api } = bench();
  api.saveHistory(historyOf(300));
  eq(api.backupDue(api.loadMeta(), 300, T0, null).due, true, "первый прогон: бэкапа не было");

  api.saveMeta({ lastBackupAt: T0, lastBackupFlats: 300 });
  eq(api.backupDue(api.loadMeta(), 300, T0 + DAY, null).due, false,
    "на следующий день второй файл не навязывается");
  eq(api.backupDue(api.loadMeta(), 400, T0 + DAY, null).due, true,
    "но после роста истории до 400 — снова пора");
}

// ===========================================================================
// 5. storageInfo: то, что видит пользователь в панели
// ===========================================================================
function testStorageInfo() {
  section("storageInfo: строка «в истории N · бэкап M дней назад»");

  const { api } = bench();
  api.saveHistory(historyOf(37));
  api.saveSnapshots({ subjects: { "jk:1704112": { ts: Math.floor(T0 / 1000), byFp: {} } } });

  const a = api.storageInfo(T0);
  eq(a.flats, 37, "квартир в истории");
  eq(a.subjects, 1, "снимков");
  eq(a.ageDays, null, "бэкапа не было — возраст null, а не ноль");

  api.saveMeta({ lastBackupAt: T0 - 3 * DAY, lastBackupFlats: 37 });
  eq(api.storageInfo(T0).ageDays, 3, "возраст бэкапа в днях");

  // Склонение видно пользователю в той же строке, а 11-14 ломают наивную
  // формулу «по последней цифре» — их и проверяем в первую очередь.
  const p = (n) => api.plural(n, "квартира", "квартиры", "квартир");
  const cases = [[1, "квартира"], [2, "квартиры"], [4, "квартиры"], [5, "квартир"],
    [11, "квартир"], [12, "квартир"], [14, "квартир"], [21, "квартира"],
    [22, "квартиры"], [101, "квартира"], [111, "квартир"], [0, "квартир"]];
  const wrong = cases.filter(([n, want]) => p(n) !== want);
  check(!wrong.length,
    `склонение по числу верно во всех ${cases.length} случаях, включая 11-14`,
    `склонение ошибается: ${wrong.map(([n, w]) => `${n} → «${p(n)}», ожидалось «${w}»`).join("; ")}`);
}

// ===========================================================================
// 6. Импорт бэкапа при полной квоте не рапортует об успехе
// ===========================================================================
function testImportHonest() {
  section("Импорт: «импортировано N» только если N действительно записано");

  // Сначала честный круг: экспорт → импорт в чистое хранилище.
  const src = bench();
  src.api.saveHistory(historyOf(5));
  const payload = JSON.stringify(src.api.exportBackupData());

  const dst = bench();
  const res = dst.api.importBackupData(payload);
  eq(res.flats, 5, "импортировано квартир");
  eq(Object.keys(dst.api.loadHistory().flats).length, 5, "и они действительно в хранилище");

  // Старые бэкапы начинались с BOM (тогдашний download() добавлял его всем
  // файлам). Обязаны продолжать импортироваться.
  const dst2 = bench();
  eq(dst2.api.importBackupData("﻿" + payload).flats, 5, "бэкап с BOM импортируется");

  // А теперь то же самое в переполненное хранилище.
  const full = bench({ storage: makeStorage(null, { quotaChars: 500 }) });
  let threw = null;
  try { full.api.importBackupData(JSON.stringify(src.api.exportBackupData())); }
  catch (e) { threw = e; }
  check(threw && /не удалось сохранить/i.test(threw.message),
    `импорт при полной квоте падает с внятным сообщением: «${threw ? threw.message : ""}»`,
    threw ? `импорт упал не с тем сообщением: «${threw.message}»` :
      "импорт при полной квоте отрапортовал УСПЕХ — пользователь думает, что данные восстановлены");
  check(!threw || /заполнено/.test(threw.message),
    "и называет причину: хранилище заполнено",
    "причина (переполнение квоты) в сообщении не названа");
}

// ===========================================================================
// 7. Слияние и чистка — поведение, на которое опирается бэкап
// ===========================================================================
function testMergeAndPrune() {
  section("Слияние истории и чистка 400 дней");

  const { api } = bench();
  const early = Math.floor(T0 / 1000) - 300 * 86400;
  const late = Math.floor(T0 / 1000) - 10 * 86400;
  const K = "1|дом|5|55.0|Вторичка";
  const a = { flats: { [K]: { firstSeen: late, minAdded: late, lastSeen: late, addeds: [late], cianIds: [1], priceLog: [{ ts: late, p: 10 }] } } };
  const b = { flats: { [K]: { firstSeen: early, minAdded: early, lastSeen: early, addeds: [early], cianIds: [2], priceLog: [{ ts: early, p: 20 }] } } };

  const m = api.mergeHistoryFlats(a, b).flats;
  eq(m[K].firstSeen, early, "слияние берёт САМУЮ РАННЮЮ firstSeen (иначе импорт ухудшил бы реальный срок)");
  eq(m[K].cianIds.length, 2, "cianIds объединяются");
  eq(m[K].priceLog.length, 2, "priceLog объединяется");

  // Чистка: квартира, не виденная >400 дней, из истории уходит.
  const old = Math.floor(T0 / 1000) - 500 * 86400;
  api.saveHistory({ flats: {
    "1|стар|1|30.0|Вторичка": { lastSeen: old },
    "1|нов|2|40.0|Вторичка": { lastSeen: late },
  } });
  const kept = Object.keys(api.loadHistory().flats);
  eq(kept.length, 1, "после чистки осталось записей");
  eq(kept[0], "1|нов|2|40.0|Вторичка", "осталась именно свежая");
}

// ===========================================================================
// 8. Телеметрия: в хранилище едет только агрегат
// ===========================================================================
// Замер из спеки: 30 прогонов × 400 обращений сырого журнала = 2.1 МБ UTF-16
// при квоте 5 МиБ на ВЕСЬ ориджин, который мы делим с самим www.cian.ru.
// Положить журнал в localStorage — значит ускорить ту самую катастрофу, от
// которой лечит автобэкап. Это проверка, а не устная договорённость.
function testRunsBuffer() {
  section("Кольцевой буфер прогонов: только агрегаты, только 200 штук");

  const { api, store } = bench();
  const agg = (k) => ({
    ts: Math.floor(T0 / 1000) + k, http: 206, pages: 141, byStatus: { 200: 141, 429: 36, NET: 29 },
    p50: 180, p95: 940, minGap: 0, zeroGaps: 24, wallMs: 412000, drift: 1,
    shortfall: 0, raSeen: 36, budget: 0, cancel: 0, waf: 0,
  });

  eq(api.loadRuns().length, 0, "на чистом хранилище прогонов нет");
  for (let k = 0; k < 5; k++) api.rememberRun(agg(k));
  eq(api.loadRuns().length, 5, "агрегаты накапливаются");
  eq(api.loadRuns()[0].ts, agg(0).ts, "порядок сохраняется: первым лежит самый старый");

  // Кольцо: старые вытесняются, новые остаются.
  for (let k = 5; k < api.RUNS_KEEP + 40; k++) api.rememberRun(agg(k));
  const runs = api.loadRuns();
  eq(runs.length, api.RUNS_KEEP, "буфер обрезан до RUNS_KEEP");
  eq(runs[runs.length - 1].ts, agg(api.RUNS_KEEP + 39).ts, "последним лежит самый свежий");
  eq(runs[0].ts, agg(40).ts, "вытеснены именно самые старые");

  const chars = store.getItem(api.RKEY).length;
  console.log(`    · ${api.RUNS_KEEP} прогонов = ${chars} символов (${Math.round(chars / api.RUNS_KEEP)} на прогон, ` +
    `${Math.round(chars * 2 / 1024)} КБ UTF-16)`);
  check(chars < 200 * 1024,
    `буфер укладывается в разумный объём: ${Math.round(chars * 2 / 1024)} КБ UTF-16 при квоте 5 МиБ`,
    `буфер разросся до ${Math.round(chars * 2 / 1024)} КБ — это уже заметная доля квоты`);

  // Главный инвариант: сырого журнала в хранилище нет НИГДЕ.
  const dump = JSON.stringify(store.dump());
  const rawFields = ["\"dur\"", "\"att\"", "\"seg\"", "\"raSeen\":true", "\"ct\""];
  const leaked = rawFields.filter((f) => dump.includes(f));
  check(!leaked.length,
    "сырой журнал в хранилище не попал ни одним полем (dur/att/seg/ct)",
    `в хранилище протекли поля сырого журнала: ${leaked.join(", ")}`);
}

// ===========================================================================
// 9. Миграция localStorage -> IndexedDB
// ===========================================================================
// Подменяется АДАПТЕР, а не IndexedDB: эмулятор IDB — это полторы сотни строк,
// которые сами себя проверять не умеют, и тест на нём проверял бы эмулятор.
// Адаптер — ровно тот контракт, на который опирается content.js.
function fakeStore({ failOn = null, failAt = null } = {}) {
  const data = new Map();
  let writes = 0;
  return {
    kind: "fake",
    calls: () => writes,
    dump: (name) => (data.get(name) || []).slice(),
    getAll: async (name) => (data.get(name) || []).slice(),
    count: async (name) => (data.get(name) || []).length,
    putAll: async (name, recs) => {
      writes++;
      if (failOn === name) throw new Error("стор " + name + " не пишется");
      if (failAt != null && writes >= failAt) throw new Error("отказ на записи " + writes);
      data.set(name, recs.slice());
    },
  };
}

async function testMigration() {
  section("Миграция на IndexedDB: порядок, верификация, откат");

  // --- Золотая миграция.
  {
    const store = makeStorage();
    const { api } = bench({ storage: store });
    api.saveHistory(historyOf(300));
    // ts — СВЕЖИЕ: saveSnapshots чистит всё старше 400 дней, и снимок из 1970-го
    // не дожил бы до миграции (первая версия теста ловила именно это).
    const nowSec = Math.floor(T0 / 1000);
    api.saveSnapshots({ subjects: { "jk:1": { ts: nowSec, byFp: { a: { price: 5 } } }, "jk:2": { ts: nowSec - 86400, byFp: {} } } });
    const before = JSON.stringify(store.dump());

    const db = fakeStore();
    let backups = 0;
    const res = await api.migrateToIdb({ backupFn: () => { backups++; return true; }, makeStore: async () => db, nowMs: T0 });

    eq(res.ok, true, "миграция удалась");
    eq(res.flats, 300, "перенесено квартир");
    eq(res.subjects, 2, "перенесено снимков");
    eq(backups, 1, "бэкап файлом сделан РОВНО один раз и до записи");
    eq(db.dump(api.STORE_FLATS).length, 300, "в сторе flats столько же записей");
    check(db.dump(api.STORE_FLATS).every((r) => r.fp && r.priceLog),
      "у каждой записи есть ключ fp и данные целиком",
      "записи в сторе flats потеряли поля");
    eq(api.loadMeta().storageMode, "idb", "режим переключён только после проверки");
    // Главная гарантия: источник не тронут. Сравниваем длиной и хешем, а не
    // содержимым: в сообщении об ошибке иначе окажется 177 КБ JSON.
    const now = JSON.parse(store.dump()[api.HKEY] ? "{}" : "{}");   // eslint no-unused
    check(store.getItem(api.HKEY) === JSON.parse(before)[api.HKEY],
      `ключ истории в localStorage не изменился (${store.getItem(api.HKEY).length} символов) — ` +
      "переименование или удаление здесь означало бы удвоение расхода квоты ровно тогда, когда она на исходе",
      `ключ истории в localStorage изменился: было ${JSON.parse(before)[api.HKEY].length} символов, стало ${store.getItem(api.HKEY).length}`);
    // И читаем мы теперь оттуда.
    eq(Object.keys(api.loadHistory().flats).length, 300, "после миграции история читается из IndexedDB");
    eq(Object.keys(api.loadSnapshots().subjects).length, 2, "и снимки тоже");
  }

  // --- Бэкап не удался -> миграция не начинается вовсе.
  {
    const { api } = bench();
    api.saveHistory(historyOf(120));
    const db = fakeStore();
    const res = await api.migrateToIdb({ backupFn: () => false, makeStore: async () => db, nowMs: T0 });
    eq(res.ok, false, "миграция отклонена");
    eq(db.calls(), 0, "в IndexedDB не ушло НИ ОДНОЙ записи");
    eq(api.loadMeta().storageMode, undefined, "режим остался легаси");
    check(/бэкап/i.test(res.reason), `причина названа: «${res.reason}»`, `причина невнятная: «${res.reason}»`);
  }

  // --- Отказ на середине записи -> легаси-режим сохранён, источник цел.
  {
    const store = makeStorage();
    const { api } = bench({ storage: store });
    api.saveHistory(historyOf(150));
    const before = store.getItem(api.HKEY);
    const db = fakeStore({ failOn: "snapshots" });
    const res = await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    eq(res.ok, false, "миграция отклонена");
    eq(api.loadMeta().storageMode, undefined, "смешанного режима не возникло: остались целиком на легаси");
    check(store.getItem(api.HKEY) === before,
      `источник в localStorage не изменился (${before.length} символов)`,
      `источник в localStorage изменился: было ${before.length} символов, стало ${(store.getItem(api.HKEY) || "").length}`);
    eq(api.loadMeta().migrationFailedAt, T0, "отметка о неудаче поставлена");
    eq(Object.keys(api.loadHistory().flats).length, 150, "история по-прежнему читается");
  }

  // --- Верификация ловит недоперенос.
  {
    const { api } = bench();
    api.saveHistory(historyOf(200));
    const db = fakeStore();
    // Стор «теряет» часть записей — ровно то, чего верификация обязана не пропустить.
    const honest = db.putAll;
    db.putAll = async (name, recs) => honest(name, name === api.STORE_FLATS ? recs.slice(0, 199) : recs);
    const res = await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    eq(res.ok, false, "недоперенос пойман");
    check(/199 квартир из 200/.test(res.reason), `и назван числом: «${res.reason}»`, `недоперенос назван невнятно: «${res.reason}»`);
    eq(api.loadMeta().storageMode, undefined, "режим не переключился");
  }

  // --- Верификация ловит ИСКАЖЕНИЕ (количество сходится, содержимое нет).
  {
    const { api } = bench();
    api.saveHistory(historyOf(200));
    const db = fakeStore();
    const honest = db.putAll;
    db.putAll = async (name, recs) => honest(name, name === api.STORE_FLATS
      ? recs.map((r, i) => (i === 0 ? Object.assign({}, r, { priceLog: [] }) : r)) : recs);
    const res = await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    eq(res.ok, false, "искажение поймано, хотя количество сошлось");
    check(/искажённой/.test(res.reason), `и названо: «${res.reason}»`, `искажение названо невнятно: «${res.reason}»`);
  }

  // --- IndexedDB недоступен (инкогнито, запрет политикой).
  {
    const { api } = bench();
    api.saveHistory(historyOf(120));
    const res = await api.migrateToIdb({ backupFn: () => true, makeStore: async () => null, nowMs: T0 });
    eq(res.ok, false, "миграция отклонена");
    check(/IndexedDB недоступен/.test(res.reason), `причина названа: «${res.reason}»`, `причина невнятная: «${res.reason}»`);
    eq(Object.keys(api.loadHistory().flats).length, 120, "работа продолжается на легаси-пути");
  }

  // --- Повтор не чаще раза в сутки.
  {
    const { api } = bench();
    eq(api.migrationDue({}, T0), true, "на чистом профиле миграция нужна");
    eq(api.migrationDue({ storageMode: "idb" }, T0), false, "уже мигрировали — не нужна");
    eq(api.migrationDue({ migrationFailedAt: T0 - 3600000 }, T0), false, "час назад не удалось — не долбимся");
    eq(api.migrationDue({ migrationFailedAt: T0 - 25 * 3600000 }, T0), true, "через сутки пробуем снова");
  }

  // --- Идемпотентность: вторая миграция не дублирует и не портит.
  {
    const { api } = bench();
    api.saveHistory(historyOf(180));
    const db = fakeStore();
    await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    const first = JSON.stringify(db.dump(api.STORE_FLATS));
    await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    eq(db.dump(api.STORE_FLATS).length, 180, "записей столько же");
    check(JSON.stringify(db.dump(api.STORE_FLATS)) === first,
      `и содержимое побитово то же (${first.length} символов)`,
      "вторая миграция изменила содержимое стора");
  }

  // --- После миграции запись идёт в IndexedDB, а не в localStorage.
  {
    const store = makeStorage();
    const { api } = bench({ storage: store });
    api.saveHistory(historyOf(140));
    const db = fakeStore();
    await api.migrateToIdb({ backupFn: () => true, makeStore: async () => db, nowMs: T0 });
    const lsBefore = store.getItem(api.HKEY);
    const h = api.loadHistory();
    h.flats["новая|запись|1|50.0|Вторичка"] = { lastSeen: Math.floor(T0 / 1000), addeds: [], cianIds: [], priceLog: [] };
    api.saveHistory(h);
    await new Promise((r) => setTimeout(r, 0));      // запись в IDB асинхронна
    check(store.getItem(api.HKEY) === lsBefore, "localStorage не тронут новой записью",
      `localStorage переписан после миграции: было ${lsBefore.length} символов, стало ${(store.getItem(api.HKEY) || "").length}`);
    eq(db.dump(api.STORE_FLATS).length, 141, "новая запись ушла в IndexedDB");
  }
}

// ===========================================================================
// 10. Сколько на самом деле стоит одна квартира
// ===========================================================================
// Размерный сторож: если кто-то добавит в историю поле, потолок 5 MiB
// придвинется, и об этом надо узнать из теста, а не от пользователя.
function testSizeGuard() {
  section("Размерный сторож: символов на квартиру");

  const { api, store } = bench();
  const N = 500;
  api.saveHistory(historyOf(N));
  const perFlat = Math.round(store.getItem(api.HKEY).length / N);
  const capacity = Math.floor(5 * 1024 * 1024 / 2 / perFlat);   // квота в UTF-16
  console.log(`    · ${perFlat} символов на квартиру → потолок ~${capacity} квартир на 5 MiB`);
  check(perFlat <= 1024,
    `запись истории укладывается в 1 КБ символов (${perFlat})`,
    `запись истории раздулась до ${perFlat} символов — потолок упал до ~${capacity} квартир`);
}

// ===========================================================================
// 9. НЕГАТИВНЫЙ КОНТРОЛЬ
// ===========================================================================
const BREAKAGES = [
  {
    name: "вернули «catch { /* ignore */ }» — отказ записи снова проглатывается",
    from: `      storageFault = { key, quota: isQuotaError(e), message: (e && e.message) || String(e), at: Date.now() };
      console.error(\`[cian-excel] не удалось сохранить \${key}: \${storageFault.message}\`);
      return false;`,
    to: `      return true;   // «ignore», как было до 2.17`,
    expect: "не может отличить отказ от успеха",
  },
  {
    name: "маркер бэкапа переехал в ключ истории",
    from: `  function saveMeta(patch) { const m = Object.assign(loadMeta(), patch); storageWrite(MKEY, m); return m; }`,
    to: `  function saveMeta(patch) { const m = Object.assign(loadMeta(), patch); storageWrite(HKEY, m); return m; }`,
    expect: "маркер бэкапа записан",
  },
  {
    name: "backupDue всегда говорит «не пора»",
    from: `    if (!last) return { due: true, why: "бэкапа ещё не было" };`,
    to: `    if (!last) return { due: false, why: "" };`,
    expect: "100 квартир без единого бэкапа",
  },
  {
    name: "импорт снова рапортует об успехе, не записав данные",
    from: `    if (!okH || !okS) {`,
    to: `    if (false) {`,
    expect: "импорт при полной квоте",
  },
  {
    // Первая из трёх гарантий миграции: бэкап файлом ДО первой записи и
    // блокирующе. Без неё отказ на середине оставляет данные только в одном
    // месте — и это место мы как раз собирались переписывать.
    name: "бэкап перед миграцией перестал быть блокирующим",
    from: `    try { if (!(await backupFn())) return fail("не удалось сохранить бэкап перед миграцией"); }`,
    to: `    try { await backupFn(); }`,
    expect: "в IndexedDB не ушло НИ ОДНОЙ записи",
  },
  {
    // Третья гарантия: успех объявляется только после проверки. Без неё
    // «частично перенесли» неотличимо от «перенесли», а режим уже переключён.
    name: "верификация переноса снята",
    from: `      if (gotFlats.length !== nFlats) return fail(\`перенесено \${gotFlats.length} квартир из \${nFlats}\`);`,
    to: `      if (false) return fail("");`,
    expect: "недоперенос пойман",
  },
  {
    name: "миграция пробует снова на каждом прогоне",
    from: `    if (meta.migrationFailedAt && nowMs - meta.migrationFailedAt < MIGRATION_RETRY_MS) return false;`,
    to: `    if (false) return false;`,
    expect: "час назад не удалось",
  },
];

function negativeControl() {
  const src = fs.readFileSync(CONTENT_JS, "utf8");
  const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || "/tmp", "cian-stor-"));
  let bad = 0;
  console.log(`НЕГАТИВНЫЙ КОНТРОЛЬ: ${BREAKAGES.length} поломки во временных копиях content.js\n  (${dir})`);
  for (const [k, b] of BREAKAGES.entries()) {
    console.log(`\n── ${b.name} ─────────────────────`);
    const n = src.split(b.from).length - 1;
    if (n !== 1) {
      console.error(`  ✗ якорь поломки не найден или неоднозначен (${n} вхождений): «${b.from}»`);
      bad++; continue;
    }
    const file = path.join(dir, `${k + 1}.js`);
    fs.writeFileSync(file, src.replace(b.from, b.to));
    const out = spawnSync(process.execPath, [fileURLToPath(import.meta.url)], {
      encoding: "utf8",
      env: { ...process.env, CIAN_CONTENT_JS: file, NEGATIVE: "" },
    });
    const all = (out.stdout || "") + (out.stderr || "");
    const hit = all.split("\n").filter((l) => l.includes("✗") && l.includes(b.expect));
    if (out.status === 0) { console.error("  ✗ тест остался ЗЕЛЁНЫМ на сломанном коде (код возврата 0)"); bad++; }
    else console.log(`  ✓ тест покраснел (код возврата ${out.status})`);
    if (!hit.length) {
      console.error(`  ✗ среди падений нет сообщения про «${b.expect}» — тест краснеет не по той причине`);
      console.error((all.split("\n").filter((l) => l.includes("✗")).slice(0, 4).map((l) => "      " + l.trim()).join("\n")) || "      (падений вообще нет)");
      bad++;
    } else {
      console.log(`  ✓ причина названа верно:\n      ${hit[0].trim()}`);
      const others = all.split("\n").filter((l) => l.includes("✗") && !l.includes(b.expect)).length;
      if (others) console.log(`      (плюс ${others} сопутствующих падений — поломка задевает и соседние инварианты)`);
    }
  }
  console.log(bad ? `\nНЕГАТИВНЫЙ КОНТРОЛЬ ПРОВАЛЕН: ${bad}` : "\nНегативный контроль пройден: каждая поломка ловится и названа верно.");
  return bad ? 1 : 0;
}

// ===========================================================================
async function main() {
  console.log(`Слой хранения: ${CONTENT_JS}`);
  testFaultVisible();
  testMarkerSurvives();
  testBackupDue();
  testMarkerCycle();
  testStorageInfo();
  testImportHonest();
  testMergeAndPrune();
  testRunsBuffer();
  await testMigration();
  testSizeGuard();
  console.log(failed ? `\nПРОВАЛЕНО: ${failed}` : "\nВсё зелено.");
  return failed ? 1 : 0;
}

process.exit(process.env.NEGATIVE ? negativeControl() : await main());
