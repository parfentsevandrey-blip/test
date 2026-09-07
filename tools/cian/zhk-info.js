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
const delay = parseInt(opt('delay', '4000'), 10);

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
  const t = text.replace(/ /g, ' ');
  const grab = (re) => { const m = t.match(re); return m ? (m[1] || m[0]).trim() : null; };
  return {
    developer: grab(/Застройщик\s*\n\s*([^\n]{2,80})/) || grab(/Девелопер\s*\n\s*([^\n]{2,80})/),
    finished: grab(/Сдан[аы]?\s+в\s+(\d(?:\s+кв\.\s+)?\d{0,4}(?:\s*\d{4})?)/) || grab(/(Дом сдан|Сдан)\b/),
    deadline: grab(/Срок сдачи\s*\n?\s*([^\n]{4,40})/) || grab(/Сдача[^\n]{0,20}\n?\s*(\d кв\.\s*\d{4}[^\n]{0,20})/),
    yearText: grab(/(Сдан[^\n]{0,40}\d{4}[^\n]{0,20})/) || grab(/((?:Срок сдачи|Сдача)[^\n]{0,40}\d{4}[^\n]{0,20})/),
    cls: grab(/Класс(?: жилья| дома)?\s*\n\s*([^\n]{3,30})/),
    stage: grab(/(Строится|Сдан|Дом сдан|Проектируется)\b/),
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
    if (out[name] && out[name].id) continue;
    process.stdout.write(`${name} … `);
    const backoff = async (why) => {
      captcha++; console.log(why + (item.tries < 3 ? ', жду 2 мин и повторяю' : ', сдаюсь'));
      if (item.tries++ < 3) queue.unshift(item);
      await sleep(120000);
    };
    try {
      const r = await ctx.request.get('https://api.cian.ru/geo-suggest/v1/suggest/?query=' + encodeURIComponent(name.replace(/\s*\(.*$/, '')) + '&regionId=1&offerType=flat&dealType=sale',
        { headers: { referer: 'https://www.cian.ru/', 'user-agent': UA }, timeout: 30000, maxRedirects: 0 });
      if (r.status() !== 200) { await backoff(`suggest http ${r.status()}`); if (captcha > 12) break; continue; }
      const list = ((((await r.json()).data || {}).suggestions || {}).newbuildings || {}).items || [];
      const hit = pick(list, name, hint);
      if (!hit) { out[name] = { id: null, note: 'подсказка не нашла ЖК' }; console.log('не найден'); await sleep(delay); continue; }
      const url = 'https://www.cian.ru' + hit.link;
      const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await page.waitForTimeout(2500);
      const status = resp ? resp.status() : null;
      if (page.url().includes('captcha') || status === 403) { await backoff('капча'); if (captcha > 12) break; continue; }
      const text = await page.evaluate(() => document.body.innerText);
      const p = parse(text);
      out[name] = { id: hit.id, cianName: hit.name, address: hit.address, url, status, ...p, fetched: new Date().toISOString().slice(0, 10) };
      console.log(`id=${hit.id} ${p.developer || '—'} | ${p.yearText || p.deadline || p.finished || '—'} | ${p.cls || '—'}`);
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
