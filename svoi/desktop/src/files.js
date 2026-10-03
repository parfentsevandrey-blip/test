'use strict';
// File names for what the person downloads from the interface.
const fs = require('node:fs');
const path = require('node:path');

/** A file name that is safe to create on every system. */
function safeName(name) {
  let n = path.basename(String(name || 'file')).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').replace(/[. ]+$/, '');
  if (!n) n = 'file';
  if (/^(con|prn|aux|nul|com\d|lpt\d)(\..*)?$/i.test(n)) n = '_' + n;
  return n.slice(0, 200);
}

/** `dir/name`, or `dir/name (2).ext` and so on if it exists. */
function uniquePath(dir, name) {
  const ext = path.extname(name);
  const stem = name.slice(0, name.length - ext.length);
  let p = path.join(dir, name);
  for (let i = 2; fs.existsSync(p) && i < 1000; i++) p = path.join(dir, `${stem} (${i})${ext}`);
  return p;
}

module.exports = { safeName, uniquePath };
