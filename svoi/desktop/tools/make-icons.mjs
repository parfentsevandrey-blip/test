#!/usr/bin/env node
// Renders the app and tray icons from the interface's own logo (no image tools needed: a headless
// Chromium draws the SVG). The results are committed, so builds do not depend on this script.
//
//   cd desktop && node tools/make-icons.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const logo = fs.readFileSync(path.resolve(root, '../internal/web/ui/icons/icon.svg'), 'utf8');

// Tray glyph: no tile, so that it reads on a dark and on a light taskbar.
const glyph = (color, opacity = 0.7) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <path d="M32 13 13 46h38Z" fill="none" stroke="${color}" stroke-opacity="${opacity}" stroke-width="5" stroke-linejoin="round"/>
  <circle cx="32" cy="13" r="9.5" fill="${color}"/><circle cx="13" cy="46" r="9.5" fill="${color}"/><circle cx="51" cy="46" r="9.5" fill="${color}"/>
</svg>`;

const browser = await chromium.launch({ args: ['--no-sandbox'], executablePath: process.env.CHROME_PATH || undefined });
async function png(svg, size) {
  const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
  await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`);
  const buf = await page.screenshot({ omitBackground: true, type: 'png' });
  await page.close();
  return buf;
}

/** An .ico that holds PNG images (every Windows since Vista reads them). */
function ico(images) {
  const head = Buffer.alloc(6 + 16 * images.length);
  head.writeUInt16LE(0, 0);
  head.writeUInt16LE(1, 2);
  head.writeUInt16LE(images.length, 4);
  let offset = head.length;
  images.forEach(({ size, data }, i) => {
    const e = 6 + 16 * i;
    head.writeUInt8(size >= 256 ? 0 : size, e);
    head.writeUInt8(size >= 256 ? 0 : size, e + 1);
    head.writeUInt8(0, e + 2);
    head.writeUInt8(0, e + 3);
    head.writeUInt16LE(1, e + 4);
    head.writeUInt16LE(32, e + 6);
    head.writeUInt32LE(data.length, e + 8);
    head.writeUInt32LE(offset, e + 12);
    offset += data.length;
  });
  return Buffer.concat([head, ...images.map((i) => i.data)]);
}

const write = (rel, data) => {
  const f = path.join(root, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, data);
  console.log(`${rel}  ${data.length} bytes`);
};

write('build/icon.png', await png(logo, 1024));
write('assets/icon.png', await png(logo, 256));
write('assets/tray.png', await png(glyph('#1fb98a'), 64));
write('assets/tray.ico', ico(await Promise.all([16, 24, 32, 48, 64].map(async (size) => ({ size, data: await png(glyph('#1fb98a'), size) })))));
write('assets/trayTemplate.png', await png(glyph('#000000', 0.55), 22));
write('assets/trayTemplate@2x.png', await png(glyph('#000000', 0.55), 44));
await browser.close();
