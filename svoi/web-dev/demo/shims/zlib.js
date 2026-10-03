// node:zlib's deflateSync (the mock draws PNGs), on fflate.
import { Buffer } from "buffer";
import { zlibSync } from "fflate";

export function deflateSync(buf, opts = {}) { return Buffer.from(zlibSync(buf, { level: opts.level ?? 6 })); }
export default { deflateSync };
