/* PLACEHOLDER — flat fill so the texture lab runs before the real generator
   lands. Replace with a proper procedural surface. See js/tex/noise.js for
   the toolkit and the generator contract. */

export function honedStone(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x6a6560);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.55;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}
