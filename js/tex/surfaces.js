/* PLACEHOLDER — flat fill so the texture lab runs before the real generator
   lands. Replace with a proper procedural surface. See js/tex/noise.js for
   the toolkit and the generator contract. */

export function marble(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x30302f);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.25;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}

export function bookCloth(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0x7a3b2c);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.75;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}

export function brushedMetal(size, N) {
  const px = size * size;
  const albedo = N.newF(px * 3), rough = N.newF(px), height = N.newF(px);
  const base = N.hex(0xb08748);
  for (let i = 0; i < px; i++) {
    N.setRGB(albedo, i, base);
    rough[i] = 0.3;
    height[i] = 0.5;
  }
  return { albedo, rough, height };
}
