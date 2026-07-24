/* PLACEHOLDER — flat fill so the texture lab runs before the real generator
   lands. Replace with a proper procedural surface. See js/tex/noise.js for
   the toolkit and the generator contract. */

export function charredLog(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x241a14);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.9;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}

export function leaf(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x2f4a2c);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.7;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}
