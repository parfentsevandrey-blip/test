/* PLACEHOLDER — flat fill so the texture lab runs before the real generator
   lands. Replace with a proper procedural surface. See js/tex/noise.js for
   the toolkit and the generator contract. */

export function woolRug(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0xb09a80);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.95;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}
