// Generates the tray/app icon procedurally - a small pixel-art candle flame -
// with zero dependencies beyond Node's built-in zlib (PNG encoder + ICO
// container writer implemented by hand below). Run via `npm run icons`.
'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ASSETS_DIR = path.join(__dirname, '..', 'assets');

// ---------------------------------------------------------------------------
// Tiny raster buffer (RGBA8, straight alpha)
// ---------------------------------------------------------------------------
function createRaster(w, h) {
  return { w: w, h: h, data: new Uint8ClampedArray(w * h * 4) };
}

function setPixel(raster, x, y, rgba) {
  if (x < 0 || y < 0 || x >= raster.w || y >= raster.h) return;
  const i = (y * raster.w + x) * 4;
  raster.data[i] = rgba[0];
  raster.data[i + 1] = rgba[1];
  raster.data[i + 2] = rgba[2];
  raster.data[i + 3] = rgba[3];
}

function fillSpan(raster, y, xFrom, xTo, rgba) {
  for (let x = xFrom; x <= xTo; x++) setPixel(raster, x, y, rgba);
}

function nearestUpscale(raster, scale) {
  const out = createRaster(raster.w * scale, raster.h * scale);
  for (let y = 0; y < out.h; y++) {
    const sy = Math.floor(y / scale);
    for (let x = 0; x < out.w; x++) {
      const sx = Math.floor(x / scale);
      const si = (sy * raster.w + sx) * 4;
      const di = (y * out.w + x) * 4;
      out.data[di] = raster.data[si];
      out.data[di + 1] = raster.data[si + 1];
      out.data[di + 2] = raster.data[si + 2];
      out.data[di + 3] = raster.data[si + 3];
    }
  }
  return out;
}

// ---------------------------------------------------------------------------
// The icon design: a 16x16 dark-fantasy candle flame (mirrored left/right so
// hand-authored half-widths stay symmetric), on a transparent background.
// ---------------------------------------------------------------------------
const COLORS = {
  wick: [20, 16, 24, 255],
  outline: [10, 8, 14, 255],
  gold: [201, 162, 39, 255],
  wax: [58, 42, 32, 255],
  waxDark: [38, 27, 20, 255],
  outer: [232, 73, 29, 255],
  mid: [255, 150, 51, 255],
  inner: [255, 224, 102, 255],
  core: [255, 243, 196, 255],
};

function drawIcon16() {
  const raster = createRaster(16, 16);
  const centerL = 7;
  const centerR = 8;

  // Flame rows: y=1..10, each row given half-widths (columns beyond the
  // center pair) for the outer/mid/inner/core color bands. A band is skipped
  // when its half-width is undefined.
  const flameRows = [
    { outer: 0 },
    { outer: 1 },
    { outer: 2, inner: 0 },
    { outer: 2, mid: 1, inner: 0 },
    { outer: 3, mid: 2, inner: 1 },
    { outer: 3, mid: 2, inner: 1, core: 0 },
    { outer: 4, mid: 3, inner: 2, core: 1 },
    { outer: 4, mid: 3, inner: 2, core: 0 },
    { outer: 3, mid: 2, inner: 1 },
    { outer: 2, mid: 1 },
    { outer: 1 },
  ];

  flameRows.forEach((row, i) => {
    const y = 1 + i;
    // paint widest band first so narrower/brighter bands paint over it
    if (row.outer !== undefined) fillSpan(raster, y, centerL - row.outer, centerR + row.outer, COLORS.outer);
    if (row.mid !== undefined) fillSpan(raster, y, centerL - row.mid, centerR + row.mid, COLORS.mid);
    if (row.inner !== undefined) fillSpan(raster, y, centerL - row.inner, centerR + row.inner, COLORS.inner);
    if (row.core !== undefined) fillSpan(raster, y, centerL - row.core, centerR + row.core, COLORS.core);
  });

  // Wick poking out above the candle rim.
  setPixel(raster, centerL, 11, COLORS.wick);
  setPixel(raster, centerR, 11, COLORS.wick);

  // Candle: gold rim then wax body, with a 1px dark outline on both sides.
  const candleRows = [
    { y: 12, fill: COLORS.gold },
    { y: 13, fill: COLORS.wax },
    { y: 14, fill: COLORS.wax },
    { y: 15, fill: COLORS.waxDark },
  ];
  candleRows.forEach(({ y, fill }) => {
    setPixel(raster, 3, y, COLORS.outline);
    fillSpan(raster, y, 4, 11, fill);
    setPixel(raster, 12, y, COLORS.outline);
  });

  return raster;
}

// ---------------------------------------------------------------------------
// Minimal PNG encoder (IHDR + one IDAT + IEND), RGBA8, filter type 0.
// ---------------------------------------------------------------------------
function crc32(buf) {
  return zlib.crc32(buf) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function encodePNG(raster) {
  const { w, h, data } = raster;
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(w, 0);
  ihdrData.writeUInt32BE(h, 4);
  ihdrData[8] = 8; // bit depth
  ihdrData[9] = 6; // color type: RGBA
  ihdrData[10] = 0; // compression
  ihdrData[11] = 0; // filter
  ihdrData[12] = 0; // interlace

  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    const rowStart = y * (w * 4 + 1);
    raw[rowStart] = 0; // filter type: none
    for (let x = 0; x < w * 4; x++) {
      raw[rowStart + 1 + x] = data[y * w * 4 + x];
    }
  }
  const idatData = zlib.deflateSync(raw, { level: 9 });

  return Buffer.concat([
    signature,
    chunk('IHDR', ihdrData),
    chunk('IDAT', idatData),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------------------
// Minimal ICO container: ICONDIR + ICONDIRENTRY[] + raw PNG payloads.
// Modern Windows (Vista+) accepts PNG-compressed entries at any size.
// ---------------------------------------------------------------------------
function encodeICO(pngBuffers) {
  const count = pngBuffers.length;
  const headerSize = 6 + 16 * count;
  let offset = headerSize;
  const header = Buffer.alloc(headerSize);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(count, 4);

  pngBuffers.forEach((entry, i) => {
    const base = 6 + i * 16;
    const side = entry.size >= 256 ? 0 : entry.size; // 0 means 256px
    header[base] = side; // width
    header[base + 1] = side; // height
    header[base + 2] = 0; // color palette
    header[base + 3] = 0; // reserved
    header.writeUInt16LE(1, base + 4); // color planes
    header.writeUInt16LE(32, base + 6); // bits per pixel
    header.writeUInt32LE(entry.buffer.length, base + 8); // bytes in resource
    header.writeUInt32LE(offset, base + 12); // offset
    offset += entry.buffer.length;
  });

  return Buffer.concat([header, ...pngBuffers.map((e) => e.buffer)]);
}

// ---------------------------------------------------------------------------
function main() {
  fs.mkdirSync(ASSETS_DIR, { recursive: true });

  const master = drawIcon16(); // 16x16 source of truth

  const png32 = encodePNG(nearestUpscale(master, 2));
  fs.writeFileSync(path.join(ASSETS_DIR, 'icon.png'), png32);

  const icoSizes = [16, 32, 48, 256];
  const icoEntries = icoSizes.map((size) => ({
    size: size,
    buffer: encodePNG(nearestUpscale(master, size / 16)),
  }));
  fs.writeFileSync(path.join(ASSETS_DIR, 'icon.ico'), encodeICO(icoEntries));

  console.log('Icons written to', ASSETS_DIR);
}

main();
