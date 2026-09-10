#!/usr/bin/env node
/**
 * Карточка ЖК на Циан: id, ссылка на страницу ЖК, застройщик, год сдачи, класс.
 *
 *   node tools/cian/zhk-info.js --names names.json --out zhk-info.json [--delay 4000]
 *
 * names.json — массив названий ЖК (как в выдаче) либо объектов {name, hint}.
 * Подсказка geo-suggest даёт id и ссылку вида /zhiloy-kompleks-<slug>-<id>/;
 * со страницы ЖК снимается текст: «Застройщик», «Сдан в …» / «Срок сдачи», «Класс».
 * Один браузер на всё, пауза между ЖК — иначе WAF отвечает капчей.
 */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');

const CHROME = process.env.CIAN_CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf('--' + k); return i >= 0 ? args[i + 1] : d; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/* 429 отдаёт заглушку вместо карточки — по ней срок сдачи «терялся». Она такой же
   повод притормозить и повторить, как 403 и капча. */
const blocked = (s) => s === 403 || s === 429 || (s !== null && s >= 500);
/* Ждём именно блок характеристик: «Сдача|Срок сдачи → значение → Класс».
   Верхняя плашка со словом «Сдача» появляется раньше, и ожидание по нему срывалось. */
const waitCard = async (page) => {
  await page.waitForFunction(
    () => /(?:Срок сдачи|Сдача)\s*\n\s*[^\n]{2,32}\s*\n\s*Класс/.test(document.body.innerText),
    { timeout: 25000 }).catch(() => {});
};
const delay = parseInt(opt('delay', '4000'), 10);
const REFRESH = args.includes('--refresh');   // перечитать карточки, даже если они уже собраны
const DUMP = opt('dump');                     // куда класть сырой текст карточек — правки парсера без сети
if (DUMP) fs.mkdirSync(DUMP, { recursive: true });
const slug = (s) => s.replace(/[^\wа-яёА-ЯЁ]+/gi, '-').slice(0, 60);
const keep = (name, text) => { if (DUMP) fs.writeFileSync(`${DUMP}/${slug(name)}.txt`, text); };

const names = JSON.parse(fs.readFileSync(opt('names'), 'utf8')).map((n) => (typeof n === 'string' ? { name: n } : n));
const outPath = opt('out', 'zhk-info.json');
const prev = fs.existsSync(outPath) ? JSON.parse(fs.readFileSync(outPath, 'utf8')) : {};

const norm = (s) => (s || '').toLowerCase().replace(/[«»"'()]/g, ' ').replace(/ё/g, 'е').replace(/\s+/g, ' ').trim();

function pick(list, name, hint) {
  const n = norm(name);
  const moscow = list.filter((i) => i.regionId === 1);
  const exact = moscow.find((i) => norm(i.name) === n || norm(i.fullName) === n);
  if (exact) return exact;
  const base = n.split(' ( ')[0].replace(/\s*\(.*$/, '');
  const starts = moscow.find((i) => norm(i.name).startsWith(base) || base.startsWith(norm(i.name)));
  if (starts) return starts;
  if (hint) { const h = moscow.find((i) => norm(i.address).includes(norm(hint))); if (h) return h; }
  return moscow[0] || null;
}

function parse(text) {
  const t = text.replace(/\u00a0/g, ' ');
  const grab = (re) => { const m = t.match(re); return m ? (m[1] || m[0]).trim() : null; };
  /* Значение срока сдачи берём строго из блока характеристик — между «Сдача»/«Срок
     сдачи» и следующим полем «Класс». Иначе цепляются рекламные карточки соседних
     ЖК («Сдача в 4 кв. 2028 · ЖК Бадаевский») и срок уезжает на годы вперёд. */
  const delivery = (grab(/(?:Срок сдачи|Сдача)\s*\n\s*([^\n]{2,32})\s*\n\s*Класс/) || '')
    .replace(/^Сдач[аи]\s+в?о?\s*/i, '').trim() || null;
  /* Сдан или нет — только по плашке в шапке ЖК: она стоит вплотную над адресом
     («Сдан\nМосква, …») или, в старом шаблоне, в строке «Сдача → Сдан → Метро».
     Слово «Сдан» из списка квартир ниже по странице отправляло строящиеся дома
     («Дом Франка», «Аннабель») на лист «Построено». */
  const badge = grab(/\n(Сдан(?:[^\n]{0,30})?|Сдача[^\n]{0,40})\nМосква,/);
  // \b здесь бесполезен: JS считает кириллицу не-словом, и «Сдан» не даёт границы
  const done = /Сдача\s*\n\s*\n\s*Сдан\s*\n/.test(t) || /^Сдан(\s|$)/.test(badge || '');
  // сроки по корпусам: «Золотой (квартал 1)\nСдан в 4 кв. 2021»
  const houses = [...t.matchAll(/\n([^\n]{3,60})\n(Сдан[^\n]{0,30}|Сдача[^\n]{0,30}|\d кв\. 20\d\d)/g)]
    .map((m) => ({ house: m[1].trim(), when: m[2].trim() }))
    .filter((h) => /20\d\d/.test(h.when)).slice(0, 12);
  return {
    developer: grab(/Застройщики?\s*\n+\s*([^\n]{2,80})/),
    badge,                                     // плашка в шапке: «Сдан» / «Сдача в 4 кв. 2026»
    delivery,                                // как на карточке: «2023», «2021–2023», «4 кв. 2026»
    done,                                      // плашка «Сдан» в шапке
    finished: done ? (delivery || 'Сдан') : null,
    deadline: done ? null : delivery,
    yearText: delivery ? (done ? `Сдан ${delivery}` : `Сдача ${delivery}`) : (done ? 'Сдан' : null),
    cls: grab(/Класс\s*\n\s*([^\n]{3,30})/),
    floors: grab(/Этажность\s*\n\s*([^\n]{1,20})/),
    buildings: grab(/Корпуса\s*\n\s*(\d{1,3})/),
    houses,
    stage: done ? 'Сдан' : (delivery ? 'Строится' : null),
  };
}

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true,
    args: ['--no-sandbox', '--ssl-version-max=tls1.2', '--disable-blink-features=AutomationControlled'],
    proxy: process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined });
  const ctx = await browser.newContext({ locale: 'ru-RU', timezoneId: 'Europe/Moscow', userAgent: UA, ignoreHTTPSErrors: true, viewport: { width: 1440, height: 950 } });
  const page = await ctx.newPage();
  await page.goto('https://www.cian.ru/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2500);
  const out = { ...prev };
  let captcha = 0;
  const queue = names.map((n) => ({ ...n, tries: 0 }));
  while (queue.length) {
    const item = queue.shift(); const { name, hint } = item;
    if (out[name] && out[name].id && !REFRESH) continue;
    process.stdout.write(`${name} … `);
    const backoff = async (why) => {
      captcha++; console.log(why + (item.tries < 3 ? ', жду 2 мин и повторяю' : ', сдаюсь'));
      if (item.tries++ < 3) queue.unshift(item);
      await sleep(150000);
    };
    try {
      const known = prev[name] && prev[name].url ? prev[name] : null;
      if (known) {
        const resp0 = await page.goto(known.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
        await waitCard(page);
        await page.waitForTimeout(1500);
        const st0 = resp0 ? resp0.status() : null;
        if (page.url().includes('captcha') || blocked(st0)) { await backoff('отбой http ' + st0); if (captcha > 40) break; continue; }
        const t0 = await page.evaluate(() => document.body.innerText);
        const p0 = parse(t0); keep(name, t0);
        /* Пустая карточка — почти всегда 429/заглушка, а не ЖК без характеристик:
           на настоящей странице всегда есть «Класс». Повторяем, а не пишем пустоту. */
        if (!p0.cls && !p0.delivery && !p0.done) { await backoff('пустая карточка'); if (captcha > 40) break; continue; }
        out[name] = { ...known, ...p0, fetched: new Date().toISOString().slice(0, 10) };
        console.log(`id=${known.id} ${p0.developer || '—'} | ${p0.yearText || '—'} | ${p0.cls || '—'}`);
        fs.writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
        await sleep(delay); continue;
      }
      const r = await ctx.request.get('https://api.cian.ru/geo-suggest/v1/suggest/?query=' + encodeURIComponent((item.query || name).replace(/\s*\(.*$/, '')) + '&regionId=1&offerType=flat&dealType=sale',
        { headers: { referer: 'https://www.cian.ru/', 'user-agent': UA }, timeout: 30000, maxRedirects: 0 });
      if (r.status() !== 200) { await backoff(`suggest http ${r.status()}`); if (captcha > 40) break; continue; }
      const list = ((((await r.json()).data || {}).suggestions || {}).newbuildings || {}).items || [];
      const hit = pick(list, name, hint);
      if (!hit) { out[name] = { id: null, note: 'подсказка не нашла ЖК' }; console.log('не найден'); await sleep(delay); continue; }
      const url = 'https://www.cian.ru' + hit.link;
      const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await waitCard(page);
      await page.waitForTimeout(1500);
      const status = resp ? resp.status() : null;
      if (page.url().includes('captcha') || blocked(status)) { await backoff('отбой http ' + status); if (captcha > 40) break; continue; }
      const text = await page.evaluate(() => document.body.innerText);
      const p = parse(text); keep(name, text);
      if (!p.cls && !p.delivery && !p.done) { await backoff('пустая карточка'); if (captcha > 40) break; continue; }
      out[name] = { id: hit.id, cianName: hit.name, address: hit.address, url, status, ...p, fetched: new Date().toISOString().slice(0, 10) };
      console.log(`id=${hit.id} ${p.developer || '—'} | ${p.yearText || '—'} | ${p.cls || '—'}`);
    } catch (e) {
      out[name] = { id: null, error: e.message.split('\n')[0] };
      console.log('ошибка: ' + e.message.split('\n')[0]);
    }
    fs.writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
    await sleep(delay);
  }
  fs.writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
  console.log(`-> ${outPath}: ${Object.values(out).filter((v) => v.id).length} из ${names.length}`);
  await browser.close();
})();
