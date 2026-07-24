/* Render a procedural texture in the texture lab and save a PNG contact sheet.
 *
 *   node tools/texshot.js <name> [out.png] [jsonOpts]
 *
 * e.g. node tools/texshot.js oakFloor /tmp/oak.png '{"repeat":[4,3]}'
 *
 * The PNG shows the surface on a lit panel / sphere / cylinder, plus the
 * albedo, roughness, normal and height maps tiled 2×2 so seams are obvious.
 * Exits non-zero on any page error, so it doubles as a contract check.
 */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const NAME = process.argv[2];
const OUT = process.argv[3] || `/tmp/tex-${NAME}.png`;
const OPTS = process.argv[4] || '{}';
if (!NAME) { console.error('usage: node tools/texshot.js <name> [out.png] [jsonOpts]'); process.exit(2); }

const PORT = process.env.TEXLAB_PORT || '8848';
const URL_ = `http://127.0.0.1:${PORT}/tools/texlab.html`;

/* serve the repo ourselves if nothing is listening — agents shouldn't have to
   remember to start a static server */
function ensureServer(port) {
  return new Promise((resolve) => {
    const http = require('http');
    const req = http.get({ host: '127.0.0.1', port, path: '/tools/texlab.html', timeout: 1200 }, (res) => {
      res.resume(); resolve(null);
    });
    req.on('error', () => {
      const root = path.resolve(__dirname, '..');
      const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css' };
      const srv = http.createServer((rq, rs) => {
        const f = path.join(root, decodeURIComponent(rq.url.split('?')[0]));
        if (!f.startsWith(root) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { rs.statusCode = 404; return rs.end(); }
        rs.setHeader('Content-Type', types[path.extname(f)] || 'application/octet-stream');
        fs.createReadStream(f).pipe(rs);
      });
      srv.on('error', () => resolve(null));   // someone else won the race — reuse theirs
      srv.listen(port, '127.0.0.1', () => resolve(srv));
    });
    req.on('timeout', () => { req.destroy(); resolve(null); });
  });
}

(async () => {
  const srv = await ensureServer(Number(PORT));
  const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-sandbox'],
  });
  const page = await browser.newPage({ viewport: { width: 1200, height: 830 }, deviceScaleFactor: 1 });
  const errs = [];
  page.on('pageerror', (e) => errs.push('PAGEERROR: ' + (e.stack || e.message)));
  page.on('console', (m) => { if (m.type() === 'error' && !/favicon/.test(m.text())) errs.push('CONSOLE: ' + m.text()); });

  await page.goto(URL_, { waitUntil: 'load', timeout: 60000 });
  await page.waitForFunction(() => window.__labReady === true, { timeout: 120000 });

  let info = null;
  try {
    info = await page.evaluate(([n, o]) => window.showTexture(n, JSON.parse(o)), [NAME, OPTS]);
  } catch (e) {
    errs.push('showTexture threw: ' + e.message);
  }

  fs.mkdirSync(path.dirname(path.resolve(OUT)), { recursive: true });
  await page.screenshot({ path: OUT });
  await browser.close();
  if (srv) srv.close();

  console.log(JSON.stringify({ name: NAME, out: OUT, info, errors: errs }, null, 1));
  process.exit(errs.length ? 1 : 0);
})();
