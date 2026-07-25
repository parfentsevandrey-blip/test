/* Render close-ups of real surfaces inside the scene, at the distance and
 * lighting a viewer actually sees them at.  node tools/closeup.js [outDir] */
const { chromium } = require('playwright-core');
const path = require('path'); const fs = require('fs'); const zlib = require('zlib');

/* Minimal PNG reader — a WebGL canvas cannot be read back after compositing
   without preserveDrawingBuffer, so measure the screenshot instead. */
function pngLuminance(file) {
  const buf = fs.readFileSync(file);
  let off = 8, w = 0, h = 0, bitDepth = 0, colorType = 0;
  const idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      w = data.readUInt32BE(0); h = data.readUInt32BE(4);
      bitDepth = data[8]; colorType = data[9];
    } else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (bitDepth !== 8 || (colorType !== 2 && colorType !== 6)) return null;
  const bpp = colorType === 6 ? 4 : 3;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * bpp;
  const prev = Buffer.alloc(stride);
  const line = Buffer.alloc(stride);
  let p = 0, sum = 0, n = 0, hi = 0;
  for (let y = 0; y < h; y++) {
    const filter = raw[p++];
    raw.copy(line, 0, p, p + stride); p += stride;
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      let v = line[i];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
      }
      line[i] = v & 255;
    }
    line.copy(prev);
    for (let x = 0; x < w; x++) {
      const i = x * bpp;
      const l = (0.2126 * line[i] + 0.7152 * line[i + 1] + 0.0722 * line[i + 2]) / 255;
      sum += l; n++; if (l > hi) hi = l;
    }
  }
  return { mean: +(sum / n).toFixed(3), max: +hi.toFixed(3) };
}
const OUT = process.argv[2] || '/tmp/closeup';
fs.mkdirSync(OUT, { recursive: true });

const SHOTS = [
  { n: 'floor',    t: [0.2, 0.02, 0.2],   d: 1.5, th: 0.6,  ph: 2.25 },
  { n: 'sofa',     t: [0.15, 0.55, 1.5],  d: 1.6, th: 0.25, ph: 1.62 },
  { n: 'armchair', t: [-2.55, 0.5, 1.30], d: 1.5, th: 1.1,  ph: 1.62 },
  { n: 'rug',      t: [-0.5, 0.03, 0.35], d: 1.2, th: 0.5,  ph: 2.15 },
  { n: 'stone',    t: [-4.7, 1.7, -0.6],  d: 1.4, th: 1.45, ph: 1.60 },
  { n: 'wall',     t: [4.9, 1.6, 2.5],    d: 1.5, th: -1.4, ph: 1.58 },
  { n: 'table',    t: [-0.55, 0.42, 0.3], d: 1.1, th: 0.4,  ph: 2.05 },
  { n: 'shelf',    t: [4.7, 1.3, 1.9],    d: 1.5, th: -1.3, ph: 1.60 },
];

(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 900, height: 620 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('http://127.0.0.1:8848/room.html', { waitUntil: 'load' });
  await p.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 240000 });
  await p.evaluate(() => { document.getElementById('ui').style.display = 'none'; });

  const stats = {};
  for (const s of SHOTS) {
    await p.evaluate((v) => {
      const c = window.__room.cam, T = window.__room.THREE;
      c.tween = null;
      c.gTarget.set(v.t[0], v.t[1], v.t[2]); c.target.copy(c.gTarget);
      c.gDist = c.dist = v.d; c.gTheta = c.theta = v.th; c.gPhi = c.phi = v.ph;
    }, s);
    const f0 = await p.evaluate(() => window.__room.stats().frame);
    await p.waitForFunction((f) => window.__room.stats().frame > f + 4, f0, { timeout: 180000 });
    await p.screenshot({ path: path.join(OUT, s.n + '.png') });
    // objective read-out: how bright is this surface actually rendering?
    stats[s.n] = pngLuminance(path.join(OUT, s.n + '.png'));
  }
  console.log(JSON.stringify({ errs, out: OUT, luminance: stats }, null, 1));
  await b.close();
})();
