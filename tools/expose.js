/* Reads the PNGs a shooting tool produced and reports the exposure of each:
   mean luminance, and how much of the frame is crushed to black or blown to
   white. "Too dark" and "too bright" are the same complaint from opposite
   ends, and both show up here as numbers instead of opinions.

   usage: node tools/expose.js [dir]                                        */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

function decode(file) {
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
  const prev = Buffer.alloc(stride), line = Buffer.alloc(stride);
  let p = 0;
  const lum = new Float32Array(w * h);
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
      lum[y * w + x] = (0.2126 * line[i] + 0.7152 * line[i + 1] + 0.0722 * line[i + 2]) / 255;
    }
  }
  return { w, h, lum };
}

const dir = process.argv[2] || '/tmp/interior';
const rows = [];
for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.png')).sort()) {
  const d = decode(path.join(dir, f));
  if (!d) continue;
  const s = Float32Array.from(d.lum).sort();
  const n = s.length;
  const q = (t) => +s[Math.min(n - 1, Math.floor(t * n))].toFixed(3);
  let sum = 0, dark = 0, blown = 0;
  for (let i = 0; i < n; i++) {
    sum += d.lum[i];
    if (d.lum[i] < 0.02) dark++;
    if (d.lum[i] > 0.90) blown++;
  }
  rows.push({
    shot: f.replace('.png', ''),
    mean: +(sum / n).toFixed(3),
    p05: q(0.05), median: q(0.5), p95: q(0.95),
    black: +(100 * dark / n).toFixed(1) + '%',
    blown: +(100 * blown / n).toFixed(1) + '%',
  });
}
const pad = (s, n) => String(s).padEnd(n);
console.log(pad('shot', 12) + pad('mean', 7) + pad('p05', 7) + pad('med', 7) + pad('p95', 7) + pad('black', 8) + 'blown');
for (const r of rows) {
  console.log(pad(r.shot, 12) + pad(r.mean, 7) + pad(r.p05, 7) + pad(r.median, 7)
            + pad(r.p95, 7) + pad(r.black, 8) + r.blown);
}
