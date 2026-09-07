#!/usr/bin/env node
/**
 * Забор коммерческих лотов по номерам объявлений.
 *
 *   node tools/cian/commercial.js 331537941 318525898 ...
 *   node tools/cian/commercial.js --ids ids.txt --out offers.json
 *
 * Ручка by-ids из cian.js та же, меняется только _type: у коммерции это
 * commercialsale, а не flatsale. Прогрев браузера и потолок TLS 1.2 —
 * обязательны, иначе капча и ERR_CONNECTION_RESET (docs/cian/README.md).
 */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const { offersByIds } = require('./cian.js');

const CHROME = process.env.CIAN_CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';
const log = (...a) => process.stdout.write(a.join(' ') + '\n');

async function open() {
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
  const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--ssl-version-max=tls1.2', '--disable-blink-features=AutomationControlled'],
    proxy: proxy ? { server: proxy } : undefined,
  });
  const ctx = await browser.newContext({
    locale: 'ru-RU', timezoneId: 'Europe/Moscow', viewport: { width: 1440, height: 950 },
    userAgent: UA, ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  await page.goto('https://www.cian.ru/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2500);
  return { browser, ctx, page };
}

(async () => {
  const argv = process.argv.slice(2);
  let ids = [], out = null;
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--out') out = argv[++i];
    else if (argv[i] === '--ids') {
      ids.push(...fs.readFileSync(argv[++i], 'utf8').split(/[\s,]+/).filter(Boolean));
    } else ids.push(argv[i]);
  }
  if (!ids.length) { log('нужны номера объявлений'); process.exit(1); }

  const { browser, ctx } = await open();
  try {
    const r = await offersByIds(ctx, ids, 'commercialsale');
    log(`запрошено ${new Set(ids).size}, пришло ${r.offers.length}`);
    if (r.missing.length) log(`не найдено: ${r.missing.join(', ')}`);
    if (r.failed.length) log(`не проверено: ${r.failed.join(', ')}`);
    if (r.bad.length) log(`мусор на входе: ${r.bad.join(', ')}`);
    if (out) { fs.writeFileSync(out, JSON.stringify(r.offers, null, 2)); log(`-> ${out}`); }
    for (const o of r.offers) {
      const g = o.geo || {};
      log([o.cianId || o.id, (o.bargainTerms || {}).price, o.totalArea,
           (g.userInput || '').slice(0, 70), (o.photos || []).length + ' фото'].join(' | '));
    }
  } finally { await browser.close(); }
})();
