'use strict';
/* Осторожный клиент переписи.

   Прежний mass держал до четырёх запросов в полёте, разгонял темп и
   переживал отказы антибота паузами — адрес выхода ловил капчу на десятки
   минут, и свип откатили. Здесь всё наоборот:

   - один запрос за раз, после каждого ответа ровная пауза с разбросом;
   - бюджет запросов на прогон: исчерпан — прогон заканчивается, прочитанное
     остаётся;
   - ПЕРВЫЙ признак антибота останавливает прогон целиком. Без повторов и без
     ожидания на месте: запрос с помеченного адреса только продлевает метку.
     Остановка записывается в файл STOP, и следующий прогон не начнётся, пока
     не выйдет выдержка.

   Каждый запрос пишется строкой в журнал телеметрии: когда, что, код, сколько
   ждали, сколько байт, вердикт. По нему подбирается темп — замером, а не
   догадкой. */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const tls = require('tls');

const SEARCH_API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/';
const BY_IDS_API = 'https://api.cian.ru/search-offers/v1/get-offers-by-ids-desktop/';
const HOME = 'https://www.cian.ru/';
const CHROME = process.env.CIAN_CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';
/* Постквантовый ClientHello OpenSSL 3.5 повисает на новых туннелях прокси;
   с обычными кривыми — в разы реже. Действует на TLS этого процесса, у
   браузера свой стек (ему нужен потолок TLS 1.2). */
const SMALL_CURVES = 'X25519:prime256v1:secp384r1';
const API_HEADERS = {
  'content-type': 'application/json',
  referer: 'https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&offer_type=flat&region=1',
  'user-agent': UA,
  'accept-language': 'ru-RU,ru;q=0.9',
};
const BY_IDS_LIMIT = 28;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class Stop extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

/* Вердикт по ответу API. Антибот отвечает тремя способами: 429 (и 403),
   редиректом на /cian-captcha/ и капчей С КОДОМ 200 — HTML вместо JSON. По
   коду ответа третий случай от удачи не отличить. */
function verdict(status, type, head, location) {
  if (status === 429 || status === 403) return 'antibot';
  if (status >= 300 && status < 400) return /captcha/i.test(location || '') ? 'antibot' : 'error';
  if (status === 200) {
    return /json/i.test(type || '') && String(head || '').trimStart().startsWith('{') ? 'ok' : 'antibot';
  }
  return 'error';
}

/* То же для картинок: вместо кадра может прийти страница капчи. */
function imageVerdict(status, type, location) {
  if (status === 429 || status === 403) return 'antibot';
  if (status >= 300 && status < 400) return /captcha/i.test(location || '') ? 'antibot' : 'error';
  if (status === 200) return /^image\//i.test(type || '') ? 'ok' : 'antibot';
  return 'error';
}

/* Размер JPEG без декодирования: ищем маркер SOF. */
function jpegSize(buf) {
  if (!buf || buf.length < 4 || buf[0] !== 0xff || buf[1] !== 0xd8) return null;
  let i = 2;
  while (i + 9 < buf.length) {
    if (buf[i] !== 0xff) { i++; continue; }
    const m = buf[i + 1];
    const len = buf.readUInt16BE(i + 2);
    if (m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) {
      return { h: buf.readUInt16BE(i + 5), w: buf.readUInt16BE(i + 7) };
    }
    i += 2 + len;
  }
  return null;
}

/* Выдержка после остановки антиботом: пока файл STOP моложе выдержки, прогон
   не начинается и в сеть не идёт ни одного запроса. */
function stopGuard(file, cooldownMs, now = Date.now()) {
  if (!file || !fs.existsSync(file)) return null;
  let s;
  try { s = JSON.parse(fs.readFileSync(file, 'utf8')); } catch (e) { return null; }
  const left = new Date(s.at).getTime() + cooldownMs - now;
  return left > 0 ? { ...s, leftMs: left } : null;
}

async function openSession(o = {}) {
  tls.DEFAULT_ECDH_CURVE = SMALL_CURVES;
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
  const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--ssl-version-max=tls1.2', '--disable-blink-features=AutomationControlled'],
    proxy: proxy ? { server: proxy } : undefined,
  });
  /* Куки прошлого прогона: возвращающийся посетитель, а не каждый раз новый. */
  const state = o.state && fs.existsSync(o.state) ? o.state : undefined;
  const ctx = await browser.newContext({
    locale: 'ru-RU', timezoneId: 'Europe/Moscow', viewport: { width: 1440, height: 950 },
    userAgent: UA, ignoreHTTPSErrors: true, storageState: state,
  });
  const page = await ctx.newPage();
  const t0 = Date.now();
  let status = null;
  try {
    const res = await page.goto(HOME, { waitUntil: 'domcontentloaded', timeout: 60000 });
    status = res ? res.status() : null;
    await page.waitForTimeout(2500);
  } catch (e) {
    await browser.close();
    throw new Stop('NETWORK', `главная не открылась: ${String(e.message || e).split('\n')[0]}`);
  }
  const url = page.url();
  const title = await page.title().catch(() => '');
  const home = { status, url, title, ms: Date.now() - t0, cookies: !!state };
  if (/captcha/i.test(url) || /captcha/i.test(title)) {
    await browser.close();
    const e = new Stop('ANTIBOT', `главная открывается капчей (${url})`);
    e.home = home;
    throw e;
  }
  const close = async () => {
    if (o.state) await ctx.storageState({ path: o.state }).catch(() => {});
    await browser.close();
  };
  return { browser, ctx, page, home, close };
}

/* Сам клиент. `s` — сессия с ctx.request (в тестах — поддельная). */
function careful(s, o = {}) {
  const opt = { gap: 2000, jitter: 1500, budget: 60, tries: 3, telemetry: null, rnd: Math.random, sleep, ...o };
  const st = { sent: 0, ok: 0, errors: 0, bytes: 0, t0: Date.now(), last: 0, stop: null, ms: [] };
  const tele = (rec) => { if (opt.telemetry) fs.appendFileSync(opt.telemetry, JSON.stringify(rec) + '\n'); };

  async function pace() {
    if (!st.last) return;
    const wait = st.last + opt.gap + opt.rnd() * opt.jitter - Date.now();
    if (wait > 0) await opt.sleep(wait);
  }

  async function call(op, method, url, body, accept) {
    if (st.stop) throw new Stop(st.stop.code, st.stop.why);
    for (let a = 1; a <= opt.tries; a++) {
      if (st.sent >= opt.budget) throw new Stop('BUDGET', `бюджет ${opt.budget} запросов исчерпан`);
      await pace();
      st.sent++;
      const t = Date.now();
      let res;
      try {
        res = method === 'post'
          ? await s.ctx.request.post(url, { headers: API_HEADERS, data: body, timeout: 45000, maxRedirects: 0 })
          : await s.ctx.request.get(url, { headers: { 'user-agent': UA, referer: HOME }, timeout: 30000, maxRedirects: 0 });
      } catch (e) {
        st.last = Date.now();
        st.errors++;
        const why = String(e.message || e).split('\n')[0].replace(/^apiRequestContext\.\w+: /, '').slice(0, 80);
        tele({ t: new Date(t).toISOString(), op, ms: st.last - t, v: 'net', why });
        await opt.sleep(3000 * a);
        continue;
      }
      st.last = Date.now();
      const status = res.status();
      const h = res.headers();
      const buf = await res.body().catch(() => Buffer.alloc(0));
      st.bytes += buf.length;
      const v = accept === 'image'
        ? imageVerdict(status, h['content-type'], h.location)
        : verdict(status, h['content-type'], buf.subarray(0, 32).toString('utf8'), h.location);
      const ms = st.last - t;
      tele({ t: new Date(t).toISOString(), op, status, ms, bytes: buf.length, v });
      if (v === 'antibot') {
        const kind = status === 200 ? 'капча с кодом 200' : status >= 300 && status < 400 ? 'редирект на капчу' : `http ${status}`;
        st.stop = { code: 'ANTIBOT', why: `${op}: ${kind}`, at: new Date().toISOString(), after: st.sent };
        throw new Stop('ANTIBOT', st.stop.why);
      }
      if (v === 'error') {
        st.errors++;
        await opt.sleep(3000 * a);
        continue;
      }
      st.ok++;
      st.ms.push(ms);
      return { status, headers: h, buf };
    }
    throw new Stop('FAILED', `${op}: не ответил за ${opt.tries} попыток`);
  }

  async function search(q, page = 1, op = 'search') {
    const r = await call(`${op} p${page}`, 'post', SEARCH_API, { jsonQuery: { ...q, page: { type: 'term', value: page } } });
    const d = JSON.parse(r.buf.toString('utf8')).data || {};
    return {
      count: d.offerCount ?? null,
      aggregated: d.aggregatedCount ?? null,
      offers: d.offersSerialized || d.offers || [],
      queryString: d.queryString || null,
      fullUrl: d.fullUrl || null,
    };
  }

  /* Ручка по списку номеров: не больше 28 за раз, мёртвые просто
     отсутствуют в ответе. */
  async function byIds(ids, dealType = 'flatsale') {
    if (ids.length > BY_IDS_LIMIT) throw new Error(`by-ids: не больше ${BY_IDS_LIMIT} номеров за раз`);
    const r = await call('byids', 'post', BY_IDS_API, { cianOfferIds: ids.map(Number), jsonQuery: { _type: dealType } });
    return JSON.parse(r.buf.toString('utf8')).offersSerialized || [];
  }

  async function image(url) {
    const r = await call('image', 'get', url, null, 'image');
    return { bytes: r.buf.length, type: r.headers['content-type'] || null, size: jpegSize(r.buf) };
  }

  function report() {
    const sec = Math.max(1, (Date.now() - st.t0) / 1000);
    const ms = [...st.ms].sort((a, b) => a - b);
    const med = ms.length ? ms[Math.floor(ms.length / 2)] : null;
    return `запросов ${st.sent}: ответов ${st.ok}, сбоев ${st.errors}` +
      (st.stop ? `, ОСТАНОВКА: ${st.stop.why}` : '') +
      `; ${sec.toFixed(0)} с, ${(st.sent / sec).toFixed(2)} запр/с, медиана ответа ${med ?? '—'} мс, ${(st.bytes / 1e6).toFixed(1)} МБ`;
  }

  return { call, search, byIds, image, report, st };
}

module.exports = { openSession, careful, verdict, imageVerdict, jpegSize, stopGuard, Stop,
  SEARCH_API, BY_IDS_API, BY_IDS_LIMIT, SMALL_CURVES };
