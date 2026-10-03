// The slice of node:crypto the mock server uses, synchronous, on @noble/hashes.
import { Buffer } from "buffer";
import { sha256, sha512 } from "@noble/hashes/sha2.js";
import { hmac } from "@noble/hashes/hmac.js";

const algos = { sha256, sha512 };
const bytes = (d) => (typeof d === "string" ? new TextEncoder().encode(d) : d instanceof Uint8Array ? d : new Uint8Array(d));
const join = (chunks) => {
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0));
  let o = 0;
  for (const c of chunks) { out.set(c, o); o += c.length; }
  return out;
};
const finish = (raw, enc) => { const b = Buffer.from(raw); return enc ? b.toString(enc) : b; };

export function createHash(alg) {
  const H = algos[alg];
  if (!H) throw new Error("demo: unsupported hash " + alg);
  const chunks = [];
  const o = { update(d) { chunks.push(bytes(d)); return o; }, digest: (enc) => finish(H(join(chunks)), enc) };
  return o;
}

export function createHmac(alg, key) {
  const H = algos[alg];
  if (!H) throw new Error("demo: unsupported hmac " + alg);
  const chunks = [];
  const o = { update(d) { chunks.push(bytes(d)); return o; }, digest: (enc) => finish(hmac(H, bytes(key), join(chunks)), enc) };
  return o;
}

export function randomBytes(n) { return Buffer.from(crypto.getRandomValues(new Uint8Array(n))); }

export default { createHash, createHmac, randomBytes };
