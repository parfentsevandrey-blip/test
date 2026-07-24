/* =========================================================================
   Procedural texture toolkit.

   Everything here is TILEABLE: pass a `period` in lattice cells and the
   pattern wraps seamlessly at that interval. Generators must be
   deterministic — all randomness comes from the `seed` arguments.

   A texture generator has this shape:

     export function myGen(size, N) {
       const px = size * size;
       return {
         albedo: Float32Array(px * 3),   // sRGB-ish 0..1, what the eye sees
         rough:  Float32Array(px),       // 0 = mirror, 1 = chalk
         height: Float32Array(px),       // 0..1, drives the normal map
         ao:     Float32Array(px),       // optional, 0..1, folded into albedo
         metal:  Float32Array(px),       // optional, 0..1
       };
     }

   `N` is this module. Index pixels with N.idx(x, y, size).
   ========================================================================= */

export const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
export const lerp = (a, b, t) => a + (b - a) * t;
export const fract = (x) => x - Math.floor(x);
export const idx = (x, y, size) => y * size + x;
export function smoothstep(a, b, x) {
  const t = clamp((x - a) / (b - a || 1e-9), 0, 1);
  return t * t * (3 - 2 * t);
}
/** wrap a lattice coordinate into [0, period) */
const wrap = (v, p) => (p > 0 ? ((v % p) + p) % p : v);

/* ------------------------------------------------------------------ hashes */
export function hash2(x, y, seed = 0) {
  let h = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453123;
  return h - Math.floor(h);
}
export function hash3(x, y, z, seed = 0) {
  let h = Math.sin(x * 127.1 + y * 311.7 + z * 74.7 + seed * 269.5) * 43758.5453123;
  return h - Math.floor(h);
}
/** two independent values from one cell — handy for per-cell colour + size */
export function hash22(x, y, seed = 0) {
  return [hash2(x, y, seed), hash2(x + 37.3, y + 11.9, seed + 5.1)];
}

/* ------------------------------------------------------------------ noises */
/** tileable value noise, 0..1 — soft, good for broad tonal drift */
export function vnoise(x, y, period = 0, seed = 0) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  let fx = x - x0, fy = y - y0;
  fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
  const a = hash2(wrap(x0, period), wrap(y0, period), seed);
  const b = hash2(wrap(x0 + 1, period), wrap(y0, period), seed);
  const c = hash2(wrap(x0, period), wrap(y0 + 1, period), seed);
  const d = hash2(wrap(x0 + 1, period), wrap(y0 + 1, period), seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** tileable gradient (Perlin-style) noise, 0..1 — crisper, better for grain */
export function gnoise(x, y, period = 0, seed = 0) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  const fx = x - x0, fy = y - y0;
  const u = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
  const v = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
  const dot = (ix, iy, dx, dy) => {
    const a = hash2(wrap(ix, period), wrap(iy, period), seed) * 6.283185307;
    return Math.cos(a) * dx + Math.sin(a) * dy;
  };
  const n00 = dot(x0, y0, fx, fy);
  const n10 = dot(x0 + 1, y0, fx - 1, fy);
  const n01 = dot(x0, y0 + 1, fx, fy - 1);
  const n11 = dot(x0 + 1, y0 + 1, fx - 1, fy - 1);
  const nx0 = n00 + u * (n10 - n00);
  const nx1 = n01 + u * (n11 - n01);
  return clamp((nx0 + v * (nx1 - nx0)) * 0.7071 + 0.5, 0, 1);
}

/** fractal sum. `period` is the period of the FIRST octave. */
export function fbm(x, y, period, seed, oct = 5, gain = 0.5, lac = 2, fn = vnoise) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) {
    v += amp * fn(x * f, y * f, period * f, seed + i * 19);
    norm += amp; f *= lac; amp *= gain;
  }
  return v / norm;
}

/** ridged fractal — sharp creases, good for veins, bark, cracks */
export function ridge(x, y, period, seed, oct = 4, gain = 0.5, lac = 2) {
  let v = 0, amp = 1, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) {
    const n = 1 - Math.abs(gnoise(x * f, y * f, period * f, seed + i * 23) * 2 - 1);
    v += amp * n * n; norm += amp; f *= lac; amp *= gain;
  }
  return v / norm;
}

/** tileable Worley/cellular. Returns {f1, f2, cx, cy, id} — f1/f2 in cell units. */
export function worley(x, y, period, seed = 0, jitter = 1) {
  const ix = Math.floor(x), iy = Math.floor(y);
  let f1 = 1e9, f2 = 1e9, bx = 0, by = 0, bid = 0;
  for (let oy = -1; oy <= 1; oy++) {
    for (let ox = -1; ox <= 1; ox++) {
      const cx = ix + ox, cy = iy + oy;
      const wx = wrap(cx, period), wy = wrap(cy, period);
      const px = cx + 0.5 + (hash2(wx, wy, seed) - 0.5) * jitter;
      const py = cy + 0.5 + (hash2(wx + 19.7, wy + 3.1, seed) - 0.5) * jitter;
      const d = Math.hypot(px - x, py - y);
      if (d < f1) { f2 = f1; f1 = d; bx = wx; by = wy; bid = hash2(wx, wy, seed + 91); }
      else if (d < f2) { f2 = d; }
    }
  }
  return { f1, f2, cx: bx, cy: by, id: bid };
}

/** domain warp: returns displaced [x, y] */
export function warp(x, y, period, seed, amt = 1, oct = 3) {
  const wx = fbm(x, y, period, seed, oct) - 0.5;
  const wy = fbm(x, y, period, seed + 57, oct) - 0.5;
  return [x + wx * amt, y + wy * amt];
}

/* -------------------------------------------------------------- array tools */
export const newF = (n) => new Float32Array(n);

/** separable box blur on a single-channel tileable float field */
export function blur(a, size, radius) {
  if (radius < 1) return a;
  const tmp = new Float32Array(a.length), out = new Float32Array(a.length);
  const r = Math.round(radius), n = r * 2 + 1;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let s = 0;
      for (let k = -r; k <= r; k++) s += a[y * size + ((x + k + size * 4) % size)];
      tmp[y * size + x] = s / n;
    }
  }
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let s = 0;
      for (let k = -r; k <= r; k++) s += tmp[((y + k + size * 4) % size) * size + x];
      out[y * size + x] = s / n;
    }
  }
  return out;
}

/** signed-distance-ish sharpen: pushes a field toward 0/1 around `mid` */
export function contrast(a, mid = 0.5, amount = 1) {
  const out = new Float32Array(a.length);
  for (let i = 0; i < a.length; i++) out[i] = clamp(mid + (a[i] - mid) * amount, 0, 1);
  return out;
}

/* ------------------------------------------------------- height → normal/AO */
/** tangent-space normal map from a tileable height field */
export function normalFromHeight(h, size, strength = 1) {
  const out = new Uint8ClampedArray(size * size * 4);
  const at = (x, y) => h[((y + size) % size) * size + ((x + size) % size)];
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      // Sobel keeps the slope stable against the single-texel noise in these fields
      const tl = at(x - 1, y - 1), t = at(x, y - 1), tr = at(x + 1, y - 1);
      const l = at(x - 1, y), r = at(x + 1, y);
      const bl = at(x - 1, y + 1), b = at(x, y + 1), br = at(x + 1, y + 1);
      const dx = (tr + 2 * r + br) - (tl + 2 * l + bl);
      const dy = (bl + 2 * b + br) - (tl + 2 * t + tr);
      let nx = -dx * strength, ny = -dy * strength, nz = 1;
      const inv = 1 / Math.hypot(nx, ny, nz);
      nx *= inv; ny *= inv; nz *= inv;
      const i = (y * size + x) * 4;
      out[i] = (nx * 0.5 + 0.5) * 255;
      out[i + 1] = (ny * 0.5 + 0.5) * 255;
      out[i + 2] = (nz * 0.5 + 0.5) * 255;
      out[i + 3] = 255;
    }
  }
  return out;
}

/** cheap cavity/AO from a height field: how far below the local average a texel sits */
export function aoFromHeight(h, size, radius = 6, strength = 1) {
  const wide = blur(h, size, radius);
  const out = new Float32Array(h.length);
  for (let i = 0; i < h.length; i++) {
    out[i] = clamp(1 - Math.max(0, wide[i] - h[i]) * 6 * strength, 0, 1);
  }
  return out;
}

/* ------------------------------------------------------------ canvas output */
export function canvasOf(size) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  return c;
}
export function rgbCanvas(rgb, size, ao = null, aoAmount = 1) {
  const c = canvasOf(size), g = c.getContext('2d', { willReadFrequently: true });
  const img = g.createImageData(size, size), d = img.data;
  for (let i = 0, p = 0; i < size * size; i++, p += 4) {
    const k = ao ? lerp(1, ao[i], aoAmount) : 1;
    d[p] = clamp(rgb[i * 3] * k, 0, 1) * 255;
    d[p + 1] = clamp(rgb[i * 3 + 1] * k, 0, 1) * 255;
    d[p + 2] = clamp(rgb[i * 3 + 2] * k, 0, 1) * 255;
    d[p + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  return c;
}
export function grayCanvas(a, size) {
  const c = canvasOf(size), g = c.getContext('2d', { willReadFrequently: true });
  const img = g.createImageData(size, size), d = img.data;
  for (let i = 0, p = 0; i < size * size; i++, p += 4) {
    const v = clamp(a[i], 0, 1) * 255;
    d[p] = d[p + 1] = d[p + 2] = v; d[p + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  return c;
}
export function normalCanvas(h, size, strength) {
  const c = canvasOf(size), g = c.getContext('2d', { willReadFrequently: true });
  const bytes = normalFromHeight(h, size, strength);
  g.putImageData(new ImageData(bytes, size, size), 0, 0);
  return c;
}

/* ----------------------------------------------------------------- palettes */
/** sRGB hex → linear-ish 0..1 triple, for authoring albedo from familiar colours */
export function hex(h) {
  return [((h >> 16) & 255) / 255, ((h >> 8) & 255) / 255, (h & 255) / 255];
}
export function mixRGB(a, b, t) {
  return [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
}
/** write one pixel of an albedo array */
export function setRGB(dst, i, c, scale = 1) {
  dst[i * 3] = c[0] * scale; dst[i * 3 + 1] = c[1] * scale; dst[i * 3 + 2] = c[2] * scale;
}
