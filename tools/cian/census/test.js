#!/usr/bin/env node
/* Проверки осторожного клиента переписи — без сети.
   Запуск: node tools/cian/census/test.js
   Главная инварианта: после первого признака антибота в сеть не уходит ни
   одного запроса — ни повтора, ни следующей страницы. */
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { careful, verdict, imageVerdict, jpegSize, stopGuard } = require('./client');
const { extra, summarizeLots } = require('./pilot');

let passed = 0;
const pending = [];
const test = (name, fn) => {
  const ok = () => { passed++; process.stdout.write(`  ok  ${name}\n`); };
  const bad = (e) => { process.stdout.write(`  FAIL ${name}\n       ${e.message}\n`); process.exitCode = 1; };
  try {
    const r = fn();
    if (r && typeof r.then === 'function') { pending.push(r.then(ok, bad)); return; }
    ok();
  } catch (e) { bad(e); }
};

/* Поддельный ctx.request: отвечает по сценарию и считает, сколько раз его
   дёрнули. Элемент сценария — ответ {status, type, body, location} или
   'throw' (обрыв соединения). */
function fakeSession(script) {
  const calls = [];
  const next = async (url, opts) => {
    calls.push({ url, opts });
    const s = script.length ? script.shift() : { status: 200, type: 'application/json', body: '{"data":{}}' };
    if (s === 'throw') throw new Error('apiRequestContext.post: read ECONNRESET');
    const body = Buffer.from(s.body || '');
    return {
      status: () => s.status,
      headers: () => ({ 'content-type': s.type || '', ...(s.location ? { location: s.location } : {}) }),
      body: async () => body,
    };
  };
  return { ctx: { request: { post: next, get: next } }, calls };
}
const json = (data) => ({ status: 200, type: 'application/json; charset=utf-8', body: JSON.stringify({ data }) });
const fast = { gap: 0, jitter: 0, sleep: async () => {} };

process.stdout.write('вердикт по ответу\n');

test('JSON с кодом 200 — удача', () => {
  assert.strictEqual(verdict(200, 'application/json', '{"data":1}'), 'ok');
});
test('HTML с кодом 200 — капча, а не удача', () => {
  assert.strictEqual(verdict(200, 'text/html; charset=utf-8', '<!DOCTYPE html><title>Captcha'), 'antibot');
});
test('JSON-тип с HTML внутри — тоже капча', () => {
  assert.strictEqual(verdict(200, 'application/json', '<html>'), 'antibot');
});
test('429 и 403 — антибот', () => {
  assert.strictEqual(verdict(429, 'text/html', ''), 'antibot');
  assert.strictEqual(verdict(403, 'application/json', '{}'), 'antibot');
});
test('редирект на капчу — антибот, прочий редирект — сбой', () => {
  assert.strictEqual(verdict(302, '', '', 'https://www.cian.ru/cian-captcha/?redirect=1'), 'antibot');
  assert.strictEqual(verdict(302, '', '', 'https://www.cian.ru/sale/'), 'error');
});
test('5xx — сбой, а не антибот', () => {
  assert.strictEqual(verdict(502, 'text/html', '<html>'), 'error');
});
test('картинка: HTML вместо JPEG — антибот', () => {
  assert.strictEqual(imageVerdict(200, 'image/jpeg'), 'ok');
  assert.strictEqual(imageVerdict(200, 'text/html'), 'antibot');
});
test('размер JPEG читается по маркеру SOF', () => {
  const b = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x04, 0x00, 0x00,
    0xff, 0xc0, 0x00, 0x11, 0x08, 0x01, 0xe0, 0x02, 0x80, 0x03, 0x00, 0x00, 0x00, 0x00]);
  assert.deepStrictEqual(jpegSize(b), { h: 480, w: 640 });
  assert.strictEqual(jpegSize(Buffer.from('<html>')), null);
});

process.stdout.write('остановка и бюджет\n');

test('капча с кодом 200 останавливает прогон с первого раза, без повтора', async () => {
  const s = fakeSession([{ status: 200, type: 'text/html', body: '<html>captcha' }]);
  const api = careful(s, { ...fast, budget: 10 });
  await assert.rejects(api.search({}, 1), (e) => e.code === 'ANTIBOT');
  assert.strictEqual(s.calls.length, 1);
  assert.ok(api.st.stop && /капча/.test(api.st.stop.why));
});
test('после остановки в сеть не уходит ни одного запроса', async () => {
  const s = fakeSession([{ status: 429, type: 'text/html', body: '' }, json({ offerCount: 5 })]);
  const api = careful(s, { ...fast, budget: 10 });
  await assert.rejects(api.search({}, 1), (e) => e.code === 'ANTIBOT');
  await assert.rejects(api.search({}, 2), (e) => e.code === 'ANTIBOT');
  assert.strictEqual(s.calls.length, 1);
});
test('редирект на капчу не проходится: maxRedirects 0 и остановка', async () => {
  const s = fakeSession([{ status: 302, location: '/cian-captcha/?x=1' }]);
  const api = careful(s, { ...fast });
  await assert.rejects(api.search({}, 1), (e) => e.code === 'ANTIBOT');
  assert.strictEqual(s.calls[0].opts.maxRedirects, 0);
});
test('обрыв соединения повторяется и считается в бюджет', async () => {
  const s = fakeSession(['throw', json({ offerCount: 7, aggregatedCount: 6, offersSerialized: [] })]);
  const api = careful(s, { ...fast, budget: 5 });
  const r = await api.search({}, 1);
  assert.strictEqual(r.count, 7);
  assert.strictEqual(api.st.sent, 2);
  assert.strictEqual(api.st.errors, 1);
});
test('бюджет исчерпан — прогон кончается, лишний запрос не уходит', async () => {
  const s = fakeSession([json({}), json({}), json({})]);
  const api = careful(s, { ...fast, budget: 2 });
  await api.search({}, 1);
  await api.search({}, 2);
  await assert.rejects(api.search({}, 3), (e) => e.code === 'BUDGET');
  assert.strictEqual(s.calls.length, 2);
});
test('5xx повторяется не больше tries раз', async () => {
  const bad = { status: 502, type: 'text/html', body: '' };
  const s = fakeSession([bad, bad, bad, json({})]);
  const api = careful(s, { ...fast, tries: 3, budget: 10 });
  await assert.rejects(api.search({}, 1), (e) => e.code === 'FAILED');
  assert.strictEqual(s.calls.length, 3);
});
test('страница уходит в запрос номером, остальной jsonQuery не трогается', async () => {
  const s = fakeSession([json({})]);
  const api = careful(s, { ...fast });
  await api.search({ _type: 'flatsale', price: { type: 'range', value: { lte: 5 } } }, 4);
  const q = s.calls[0].opts.data.jsonQuery;
  assert.deepStrictEqual(q.page, { type: 'term', value: 4 });
  assert.deepStrictEqual(q.price, { type: 'range', value: { lte: 5 } });
});
test('пауза после ответа: gap плюс разброс', async () => {
  const waits = [];
  const s = fakeSession([json({}), json({})]);
  const api = careful(s, { gap: 2000, jitter: 1000, rnd: () => 0.5, sleep: async (ms) => { waits.push(ms); } });
  await api.search({}, 1);
  await api.search({}, 2);
  assert.strictEqual(waits.length, 1);
  assert.ok(waits[0] > 2300 && waits[0] <= 2500, `ждали ${waits[0]}`);
});
test('by-ids не принимает больше 28 номеров за раз', async () => {
  const api = careful(fakeSession([]), { ...fast });
  await assert.rejects(api.byIds(Array.from({ length: 29 }, (_, i) => i + 1)), /28/);
});
test('телеметрия пишет строку на каждый запрос, включая отказ', async () => {
  const f = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'census-')), 'tele.jsonl');
  const s = fakeSession([json({}), { status: 429, type: 'text/html', body: '' }]);
  const api = careful(s, { ...fast, telemetry: f });
  await api.search({}, 1);
  await assert.rejects(api.search({}, 2));
  const lines = fs.readFileSync(f, 'utf8').trim().split('\n').map((l) => JSON.parse(l));
  assert.deepStrictEqual(lines.map((l) => l.v), ['ok', 'antibot']);
});

process.stdout.write('выдержка после антибота\n');

test('свежий STOP не пускает в сеть, старый — пускает', () => {
  const d = fs.mkdtempSync(path.join(os.tmpdir(), 'census-'));
  const f = path.join(d, 'STOP');
  const now = Date.parse('2026-09-24T12:00:00Z');
  fs.writeFileSync(f, JSON.stringify({ at: '2026-09-24T11:00:00Z', why: 'капча' }));
  assert.ok(stopGuard(f, 2 * 3600e3, now));
  assert.strictEqual(stopGuard(f, 0.5 * 3600e3, now), null);
  assert.strictEqual(stopGuard(path.join(d, 'нет'), 3600e3, now), null);
});

process.stdout.write('контур МКАД\n');

const { mkadVerdict, buildRing, ringRadius } = require('./mkad');

test('центр и спальные районы у кольца — внутри, заМКАДные — снаружи', () => {
  const inside = { Кремль: [55.7520, 37.6175], Выхино: [55.7160, 37.8176], Строгино: [55.8038, 37.4030],
    'Тёплый Стан': [55.6186, 37.5055], Бибирево: [55.8839, 37.6030] };
  const outside = { Митино: [55.8457, 37.3620], 'Южное Бутово': [55.5436, 37.5325], Солнцево: [55.6495, 37.3990],
    Жулебино: [55.7020, 37.8517], Некрасовка: [55.7031, 37.9265], Мякинино: [55.8237, 37.3853] };
  for (const [k, [la, lo]] of Object.entries(inside)) assert.ok(mkadVerdict(la, lo).inside, `${k} должно быть внутри`);
  for (const [k, [la, lo]] of Object.entries(outside)) assert.ok(!mkadVerdict(la, lo).inside, `${k} должно быть снаружи`);
});
test('без координат — неизвестно, а не снаружи', () => {
  assert.strictEqual(mkadVerdict(null, 37.6), null);
  assert.strictEqual(mkadVerdict(undefined, undefined), null);
});
test('у самой дороги — сомнительно, в километре — уверенно', () => {
  const ring = { bins: 4, r: [1000, 1000, 1000, 1000], spread: [40, 40, 40, 40] };
  const M_LNG = 111320 * Math.cos((55.7520 * Math.PI) / 180);
  const at = (m) => mkadVerdict(55.7520, 37.6175 + m / M_LNG, ring);
  assert.strictEqual(at(950).sure, false);
  assert.strictEqual(at(950).inside, true);
  assert.strictEqual(at(500).sure, true);
  assert.strictEqual(at(1100).inside, false);
});
test('пустые деления кольца заполняются между соседями', () => {
  const pts = [];
  for (let d = 0; d < 360; d += 1) if (d % 90 !== 45) {
    const a = (d * Math.PI) / 180;
    pts.push({ lat: 55.7520 + (15000 * Math.sin(a)) / 110540, lng: 37.6175 + (15000 * Math.cos(a)) / (111320 * Math.cos((55.7520 * Math.PI) / 180)) });
  }
  const ring = buildRing(pts, 360);
  assert.ok(ring.interpolated > 0);
  assert.ok(Math.abs(ringRadius(ring, Math.PI / 4) - 15000) < 50);
});

process.stdout.write('поля пилота\n');

test('extra берёт размер группы, кадастр, реновацию и отпечаток текста', () => {
  const x = extra({ similar: { count: 12, url: '/x?multi_id=1' }, cadastralNumber: '77:01:1', demolishedInMoscowProgramm: true,
    descriptionMinhash: [1, 2, 3], newbuilding: { id: 5, name: 'ЖК' }, building: {}, photos: [
      { id: 1, isLayout: true, fullUrl: 'a-1.jpg' }, { id: 2, isLayout: false, fullUrl: 'b-1.jpg', thumbnailUrl: 'b-2.jpg' }] });
  assert.deepStrictEqual(x.similar, { count: 12 });
  assert.strictEqual(x.cad, '77:01:1');
  assert.strictEqual(x.demolished, true);
  assert.deepStrictEqual(x.minhash, [1, 2, 3]);
  assert.strictEqual(x.nbId, 5);
  assert.deepStrictEqual(x.photoIds, [1, 2]);
  assert.strictEqual(x.photo.full, 'b-1.jpg', 'первый кадр — не планировка');
});
test('сводка считает лидеров и спрятанное за ними', () => {
  const l = (o) => ({ id: 1, similarCount: 0, houseId: 1, x: { nbId: null }, ...o });
  const s = summarizeLots([l({ id: 1, similarCount: 3, fromDeveloper: true, x: { nbId: 7 } }), l({ id: 2 }), l({ id: 3, similarCount: 2 })], { requests: 2 });
  assert.strictEqual(s.leaders, 2);
  assert.strictEqual(s.hiddenBehindLeaders, 5);
  assert.strictEqual(s.leadersPrimary, 1);
});

Promise.all(pending).then(() => {
  process.stdout.write(`\n${passed} проверок пройдено${process.exitCode ? ', есть провалы' : ''}\n`);
});
