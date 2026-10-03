'use strict';
// The shell's own small settings (window size, "asked about autostart"), one JSON file.
const fs = require('node:fs');
const path = require('node:path');

class Settings {
  constructor(file) {
    this.file = file;
    this.data = {};
    try {
      this.data = JSON.parse(fs.readFileSync(file, 'utf8')) || {};
    } catch {
      this.data = {};
    }
  }

  get(key, fallback) {
    return key in this.data ? this.data[key] : fallback;
  }

  set(key, value) {
    this.data[key] = value;
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      fs.writeFileSync(this.file, JSON.stringify(this.data, null, 1));
    } catch {
      /* settings are a convenience, never a reason to fail */
    }
  }
}

module.exports = { Settings };
