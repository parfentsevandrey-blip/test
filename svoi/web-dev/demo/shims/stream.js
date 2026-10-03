// A small Readable: new Readable({ read() { this.push(chunk | null) } }).pipe(res).
export class Readable {
  constructor(opts = {}) {
    this._read = opts.read ? opts.read.bind(this) : () => {};
    this._buf = [];
    this._ended = false;
    this._dest = null;
    this._draining = false;
  }
  push(chunk) {
    if (chunk === null) this._ended = true;
    else this._buf.push(chunk);
    if (this._dest) this._drain();
    return true;
  }
  pipe(dest) {
    this._dest = dest;
    this._drain();
    return dest;
  }
  async _drain() {
    if (this._draining) return;
    this._draining = true;
    let n = 0;
    for (;;) {
      if (this._buf.length) {
        this._dest.write(this._buf.shift());
        if (++n % 8 === 0) await new Promise((r) => setTimeout(r, 0)); // let the page breathe
        continue;
      }
      if (this._ended) { this._dest.end(); break; }
      this._read();
      if (!this._buf.length && !this._ended) await new Promise((r) => setTimeout(r, 0));
    }
    this._draining = false;
  }
}
export default { Readable };
