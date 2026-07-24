/* PLACEHOLDER — flat fill so the texture lab runs before the real generator
   lands. Replace with a proper procedural surface. See js/tex/noise.js for
   the toolkit and the generator contract. */

export function linen(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x9c8b78);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.94;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}

export function boucle(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0xbfae97);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.98;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}

export function knit(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0xa8875f);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.97;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}
