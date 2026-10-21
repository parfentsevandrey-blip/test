// Print build/report.html to PDF with headless Chromium.
//   node pdf.js [output.pdf]
const path = require('path');
const { chromium } = require('playwright');

const HERE = __dirname;
const out = path.resolve(process.argv[2] || path.join(HERE, 'Подборка_квартир_центр_Москвы.pdf'));

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('file://' + path.join(HERE, 'build', 'report.html'), { waitUntil: 'networkidle', timeout: 120000 });
  await page.evaluate(() => document.fonts.ready);
  await page.emulateMedia({ media: 'print' });

  // Layout check: report anything that spills past its page area.
  const overflow = await page.evaluate(() => {
    const issues = [];
    document.querySelectorAll('.page').forEach((pg, i) => {
      pg.querySelectorAll('.body, .hero-side, .jk-side').forEach(box => {
        const r = box.getBoundingClientRect();
        const limit = r.bottom - parseFloat(getComputedStyle(box).paddingBottom) + 1;
        box.querySelectorAll('*').forEach(el => {
          if (el.closest('.foot, .topbar, .desc')) return;
          const b = el.getBoundingClientRect();
          if (b.height && b.bottom > limit) issues.push(`page ${i + 1}: <${el.tagName.toLowerCase()} class="${el.className}"> +${Math.round(b.bottom - limit)}px`);
        });
      });
    });
    return issues;
  });
  if (overflow.length) console.warn('Overflow:\n  ' + [...new Set(overflow)].slice(0, 30).join('\n  '));
  await page.pdf({ path: out, preferCSSPageSize: true, printBackground: true });
  await browser.close();
  console.log('PDF ->', out);
})().catch(e => { console.error(e); process.exit(1); });
