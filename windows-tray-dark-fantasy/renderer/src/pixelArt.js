// Shared dark-fantasy pixel-art helpers. Loaded before every scene module.
// Exposes window.DF.PixelArt (aliased "PA" by convention in module files)
// and initializes window.DF.modules, the registry each content module writes into.
(function (global) {
  const THREE = global.THREE;

  const PALETTE = {
    stoneDark: 0x241d30,
    stoneMid: 0x362b47,
    stoneLight: 0x4c3e63,
    mortar: 0x140f1c,
    floor: 0x2a2233,
    floorDark: 0x1c1624,
    wood: 0x4a2f23,
    woodDark: 0x2e1c15,
    bone: 0xe8ddc7,
    boneShadow: 0xa89a82,
    fire1: 0xffe066,
    fire2: 0xff9633,
    fire3: 0xe8491d,
    fireDark: 0x7a1d0e,
    ember: 0xff6a2b,
    blood: 0x8c1c2b,
    moonlight: 0xaab8ff,
    moonDark: 0x5a6aa8,
    poison: 0x5fd068,
    poisonDark: 0x1f5c34,
    gold: 0xd4af37,
    purple: 0x6a2c91,
    ghost: 0xcfe8ff,
    catBlack: 0x14121a,
    skin: 0xcaa27a,
    cloth: 0x3a2540,
    clothDark: 0x22162b,
    rug: 0x7a2130,
    rugDark: 0x4a1420,
  };

  function createPixelCanvas(w, h, drawFn) {
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    drawFn(ctx, w, h);
    return canvas;
  }

  // drawFn(ctx, w, h) paints at native pixel resolution (keep w/h small, e.g. 16-64)
  // for a chunky pixel-art look once mapped onto geometry.
  function createPixelTexture(w, h, drawFn, opts) {
    opts = opts || {};
    const canvas = createPixelCanvas(w, h, drawFn);
    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    tex.wrapS = tex.wrapT = opts.repeat ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping;
    if (opts.repeatX || opts.repeatY) tex.repeat.set(opts.repeatX || 1, opts.repeatY || 1);
    if ('colorSpace' in tex) tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return tex;
  }

  // 4-step lookup ramp shared by every toon material so all modules cel-shade identically.
  let _gradient = null;
  function toonGradientMap() {
    if (_gradient) return _gradient;
    const c = createPixelCanvas(4, 1, (ctx) => {
      [42, 108, 176, 255].forEach((v, i) => {
        ctx.fillStyle = 'rgb(' + v + ',' + v + ',' + v + ')';
        ctx.fillRect(i, 0, 1, 1);
      });
    });
    const tex = new THREE.CanvasTexture(c);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    _gradient = tex;
    return tex;
  }

  function toonMaterial(color, opts) {
    opts = opts || {};
    return new THREE.MeshToonMaterial({
      color: color,
      gradientMap: toonGradientMap(),
      map: opts.map || null,
      emissive: opts.emissive !== undefined ? opts.emissive : 0x000000,
      emissiveIntensity: opts.emissiveIntensity !== undefined ? opts.emissiveIntensity : 1,
      transparent: !!opts.transparent,
      opacity: opts.opacity !== undefined ? opts.opacity : 1,
      side: opts.side !== undefined ? opts.side : THREE.FrontSide,
    });
  }

  function glowMaterial(color, opts) {
    opts = opts || {};
    return new THREE.MeshBasicMaterial({
      color: color,
      map: opts.map || null,
      transparent: opts.transparent !== undefined ? opts.transparent : true,
      opacity: opts.opacity !== undefined ? opts.opacity : 1,
      blending: opts.additive ? THREE.AdditiveBlending : THREE.NormalBlending,
      depthWrite: opts.depthWrite !== undefined ? opts.depthWrite : true,
      side: opts.side !== undefined ? opts.side : THREE.FrontSide,
    });
  }

  function box(w, h, d, material) {
    return new THREE.Mesh(new THREE.BoxGeometry(w, h, d), material);
  }

  function rand(a, b) {
    return a + Math.random() * (b - a);
  }

  function clamp(v, a, b) {
    return Math.max(a, Math.min(b, v));
  }

  // Smooth pseudo-random flicker built from layered sines - no external noise dependency.
  // Returns a value in [min, max] that wanders continuously; give every flame/torch its own seed.
  function flicker(time, seed, speed, min, max) {
    const s = seed * 17.13;
    const v =
      Math.sin(time * speed + s) * 0.5 +
      Math.sin(time * speed * 2.7 + s * 1.7) * 0.3 +
      Math.sin(time * speed * 5.3 + s * 3.1) * 0.2;
    return min + (v * 0.5 + 0.5) * (max - min);
  }

  global.DF = global.DF || {};
  global.DF.modules = global.DF.modules || {};
  global.DF.PixelArt = {
    PALETTE: PALETTE,
    createPixelCanvas: createPixelCanvas,
    createPixelTexture: createPixelTexture,
    toonMaterial: toonMaterial,
    glowMaterial: glowMaterial,
    toonGradientMap: toonGradientMap,
    box: box,
    rand: rand,
    clamp: clamp,
    flicker: flicker,
  };
})(window);
