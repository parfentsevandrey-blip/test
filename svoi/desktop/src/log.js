'use strict';
// A small append-only log file for the shell itself (the core writes its own).
const fs = require('node:fs');
const path = require('node:path');

let file = null;

function init(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true });
    file = path.join(dir, 'desktop.log');
    if (fs.existsSync(file) && fs.statSync(file).size > 1_000_000) fs.renameSync(file, file + '.1');
  } catch {
    file = null;
  }
}

function format(a) {
  if (a instanceof Error) return a.stack || a.message;
  if (typeof a === 'string') return a;
  try {
    return JSON.stringify(a);
  } catch {
    return String(a);
  }
}

function write(level, args) {
  const line = `${new Date().toISOString()} ${level} ${args.map(format).join(' ')}\n`;
  if (file) {
    try {
      fs.appendFileSync(file, line);
    } catch {
      /* the log is best effort */
    }
  }
  if (process.env.THEMESH_DESKTOP_VERBOSE) process.stderr.write(line);
}

module.exports = {
  init,
  dir: () => (file ? path.dirname(file) : null),
  info: (...a) => write('INFO', a),
  warn: (...a) => write('WARN', a),
  error: (...a) => write('ERROR', a),
};
