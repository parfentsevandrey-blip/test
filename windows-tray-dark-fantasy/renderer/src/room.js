// room.js - Gothic chamber shell: floor, walls, ceiling, the moonlit arched
// window, two wall-mounted torches, and a pair of cobwebs in the back
// corners. Everything here is architecture/ambience; hearths and props from
// other modules mount against the middle band of the back wall.
window.DF.modules.room = function (THREE, PA) {
  const PAL = PA.PALETTE;

  function hexCss(n) {
    return '#' + ('000000' + n.toString(16)).slice(-6);
  }

  const group = new THREE.Group();
  const lights = [];

  // -----------------------------------------------------------------
  // Procedural textures
  // -----------------------------------------------------------------
  function drawFloorTex(ctx, w, h) {
    ctx.fillStyle = hexCss(PAL.mortar);
    ctx.fillRect(0, 0, w, h);
    const cols = 4;
    const rows = 4;
    const cw = w / cols;
    const ch = h / rows;
    for (let ty = 0; ty < rows; ty++) {
      for (let tx = 0; tx < cols; tx++) {
        const idx = ty * cols + tx;
        const cracked = idx === 3 || idx === 9;
        const base = cracked ? PAL.floorDark : PAL.floor;
        ctx.fillStyle = hexCss(base);
        ctx.fillRect(tx * cw + 1, ty * ch + 1, cw - 2, ch - 2);
        ctx.fillStyle = hexCss(PAL.stoneMid);
        ctx.fillRect(tx * cw + 1, ty * ch + 1, cw - 2, 1);
        if (cracked) {
          ctx.fillStyle = hexCss(PAL.mortar);
          ctx.fillRect(tx * cw + Math.floor(cw * 0.35), ty * ch + 2, 1, ch - 4);
          ctx.fillRect(tx * cw + 2, ty * ch + Math.floor(ch * 0.55), cw - 4, 1);
        }
      }
    }
  }

  function drawBrickTex(ctx, w, h) {
    ctx.fillStyle = hexCss(PAL.mortar);
    ctx.fillRect(0, 0, w, h);
    const rows = 4;
    const rowH = h / rows;
    const brickW = w / 4;
    for (let r = 0; r < rows; r++) {
      const offset = r % 2 === 0 ? 0 : brickW / 2;
      for (let x = -brickW; x < w + brickW; x += brickW) {
        const bx = x + offset;
        const col = Math.floor(bx / brickW) + r * 3;
        const shade = col % 5 === 0 ? PAL.stoneLight : (Math.floor(bx / brickW) + r) % 2 === 0 ? PAL.stoneMid : PAL.stoneDark;
        ctx.fillStyle = hexCss(shade);
        ctx.fillRect(bx + 1, r * rowH + 1, brickW - 2, rowH - 2);
      }
    }
  }

  const floorTex = PA.createPixelTexture(32, 32, drawFloorTex, { repeat: true, repeatX: 6, repeatY: 5 });
  const backWallTex = PA.createPixelTexture(32, 24, drawBrickTex, { repeat: true, repeatX: 6, repeatY: 3 });
  const sideWallTex = PA.createPixelTexture(32, 24, drawBrickTex, { repeat: true, repeatX: 5, repeatY: 3 });

  // Slight warm firelight glow baked into stone/floor materials, flickered
  // in update() so the whole shell feels lit by the torches rather than flat.
  const glowSurfaces = [];

  function surfaceMaterial(tex) {
    const mat = PA.toonMaterial(0xffffff, {
      map: tex,
      emissive: PAL.ember,
      emissiveIntensity: 0.05,
    });
    glowSurfaces.push(mat);
    return mat;
  }

  // -----------------------------------------------------------------
  // Floor, walls, ceiling (room shell - may span the full width)
  // -----------------------------------------------------------------
  const floorMat = surfaceMaterial(floorTex);
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(10, 8), floorMat);
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(0, 0, 0);
  group.add(floor);

  const backWallMat = surfaceMaterial(backWallTex);
  const backWall = new THREE.Mesh(new THREE.PlaneGeometry(10, 6), backWallMat);
  backWall.position.set(0, 3, -4);
  group.add(backWall);

  const leftWallMat = surfaceMaterial(sideWallTex);
  const leftWall = new THREE.Mesh(new THREE.PlaneGeometry(8, 6), leftWallMat);
  leftWall.rotation.y = Math.PI / 2;
  leftWall.position.set(-5, 3, 0);
  group.add(leftWall);

  const rightWall = new THREE.Mesh(new THREE.PlaneGeometry(8, 6), leftWallMat);
  rightWall.rotation.y = -Math.PI / 2;
  rightWall.position.set(5, 3, 0);
  group.add(rightWall);

  const ceilingMat = PA.toonMaterial(PAL.stoneDark, { emissive: PAL.ember, emissiveIntensity: 0.03 });
  glowSurfaces.push(ceilingMat);
  const ceiling = new THREE.Mesh(new THREE.PlaneGeometry(10, 8), ceilingMat);
  ceiling.rotation.x = Math.PI / 2;
  ceiling.position.set(0, 6, 0);
  group.add(ceiling);

  // -----------------------------------------------------------------
  // Gothic arched window (left wall), letting in cold moonlight
  // -----------------------------------------------------------------
  const archShape = new THREE.Shape();
  const ww = 0.68;
  const hh = 1.4;
  const apex = 2.0;
  archShape.moveTo(-ww, 0);
  archShape.lineTo(-ww, hh);
  archShape.quadraticCurveTo(-ww, apex, 0, apex);
  archShape.quadraticCurveTo(ww, apex, ww, hh);
  archShape.lineTo(ww, 0);
  archShape.lineTo(-ww, 0);

  const windowGeo = new THREE.ShapeGeometry(archShape, 6);
  const moonColorBright = new THREE.Color(PAL.moonlight);
  const moonColorDim = new THREE.Color(PAL.moonDark);
  const windowMat = PA.glowMaterial(PAL.moonlight, { opacity: 0.55, depthWrite: false });
  const windowPanel = new THREE.Mesh(windowGeo, windowMat);
  windowPanel.rotation.y = Math.PI / 2;
  windowPanel.position.set(-4.96, 3.0, -2.0);
  group.add(windowPanel);

  const traceryMat = PA.toonMaterial(PAL.stoneDark);
  const windowGroup = new THREE.Group();
  const vBar = PA.box(0.06, apex, 0.06, traceryMat);
  vBar.position.set(-4.9, 3.0 + apex / 2, -2.0);
  windowGroup.add(vBar);
  const hBar = PA.box(0.06, 0.06, ww * 2 + 0.1, traceryMat);
  hBar.position.set(-4.9, 3.0 + hh * 0.55, -2.0);
  windowGroup.add(hBar);
  const sillBar = PA.box(0.08, 0.08, ww * 2 + 0.2, traceryMat);
  sillBar.position.set(-4.92, 3.0, -2.0);
  windowGroup.add(sillBar);
  group.add(windowGroup);

  // -----------------------------------------------------------------
  // Torches (wall bracket + layered flame cones + point light)
  // -----------------------------------------------------------------
  const bracketMat = PA.toonMaterial(PAL.stoneDark);

  function makeFlameCone(radius, height, color, opacity) {
    const geo = new THREE.ConeGeometry(radius, height, 5, 1, true);
    geo.translate(0, height / 2, 0);
    const mat = PA.glowMaterial(color, { additive: true, opacity: opacity, depthWrite: false });
    return new THREE.Mesh(geo, mat);
  }

  const torches = [];

  function buildTorch(x, y, z, seedBase) {
    const torchGroup = new THREE.Group();

    const plate = PA.box(0.1, 0.3, 0.22, bracketMat);
    plate.position.set(x + (x > 0 ? -0.05 : 0.05), y, z + 0.11);
    torchGroup.add(plate);

    const arm = PA.box(0.16, 0.1, 0.28, bracketMat);
    arm.position.set(x, y - 0.05, z + 0.3);
    torchGroup.add(arm);

    const baseY = y + 0.08;
    const outer = makeFlameCone(0.15, 0.5, PAL.fire3, 0.5);
    outer.position.set(x, baseY, z + 0.28);
    const mid = makeFlameCone(0.1, 0.4, PAL.fire2, 0.65);
    mid.position.set(x, baseY, z + 0.28);
    const tip = makeFlameCone(0.055, 0.28, PAL.fire1, 0.85);
    tip.position.set(x, baseY, z + 0.28);
    torchGroup.add(outer, mid, tip);

    const light = new THREE.PointLight(PAL.ember, 1.1, 6.5, 2);
    light.position.set(x, baseY + 0.35, z + 0.35);
    lights.push(light);

    const emberGeo = new THREE.PlaneGeometry(0.05, 0.09);
    const embers = [];
    const emberCount = 4;
    for (let i = 0; i < emberCount; i++) {
      const emat = PA.glowMaterial(PAL.ember, { additive: true, opacity: 0.7, depthWrite: false });
      const em = new THREE.Mesh(emberGeo, emat);
      em.position.set(x, baseY, z + 0.28);
      torchGroup.add(em);
      embers.push({ mesh: em, seed: seedBase * 3.1 + i * 1.7 + 0.4 });
    }

    group.add(torchGroup);

    torches.push({
      x: x,
      z: z + 0.28,
      baseY: baseY,
      outer: outer,
      mid: mid,
      tip: tip,
      light: light,
      seed: seedBase,
      embers: embers,
    });
  }

  buildTorch(-4.6, 3.2, -3.6, 11.0);
  buildTorch(4.6, 3.2, -3.6, 47.0);

  // -----------------------------------------------------------------
  // Cobwebs in back-top corners
  // -----------------------------------------------------------------
  const cobwebMat = PA.glowMaterial(PAL.boneShadow, { opacity: 0.2, depthWrite: false, side: THREE.DoubleSide });
  const cobwebGeo = new THREE.PlaneGeometry(0.85, 0.85);

  const cobwebs = [];
  function buildCobweb(x, y, z, mirror) {
    const mesh = new THREE.Mesh(cobwebGeo, cobwebMat);
    mesh.position.set(x, y, z);
    mesh.rotation.x = -Math.PI / 4;
    mesh.rotation.y = mirror ? Math.PI / 4 : -Math.PI / 4;
    group.add(mesh);
    cobwebs.push({ mesh: mesh, baseRotZ: 0, seed: mirror ? 3.3 : 8.8 });
  }
  buildCobweb(-4.8, 5.4, -3.8, false);
  buildCobweb(4.8, 5.4, -3.8, true);

  // -----------------------------------------------------------------
  // update
  // -----------------------------------------------------------------
  function update(dt, elapsed) {
    // Ambient torchlight breathing across the stone shell.
    const ambientFlicker = PA.flicker(elapsed, 5.5, 0.6, 0.02, 0.09);
    for (let i = 0; i < glowSurfaces.length; i++) {
      glowSurfaces[i].emissiveIntensity = ambientFlicker;
    }

    // Drifting cloud shadow across the moonlit window.
    const cloud = PA.flicker(elapsed, 21.0, 0.12, 0, 1);
    windowMat.opacity = 0.32 + cloud * 0.4;
    windowMat.color.lerpColors(moonColorDim, moonColorBright, cloud);

    // Cobweb sway - barely visible.
    for (let i = 0; i < cobwebs.length; i++) {
      const cw = cobwebs[i];
      cw.mesh.rotation.z = Math.sin(elapsed * 0.4 + cw.seed) * 0.03;
    }

    // Torches: flicker flame scale/opacity, light intensity, and rising embers.
    for (let i = 0; i < torches.length; i++) {
      const t = torches[i];
      const s = t.seed;

      const fOuter = PA.flicker(elapsed, s + 0.1, 6.0, 0.75, 1.15);
      const fMid = PA.flicker(elapsed, s + 0.2, 7.4, 0.7, 1.2);
      const fTip = PA.flicker(elapsed, s + 0.3, 9.1, 0.65, 1.25);
      t.outer.scale.set(1, fOuter, 1);
      t.mid.scale.set(1, fMid, 1);
      t.tip.scale.set(1, fTip, 1);
      t.outer.material.opacity = PA.flicker(elapsed, s + 0.4, 5.0, 0.4, 0.6);
      t.mid.material.opacity = PA.flicker(elapsed, s + 0.5, 6.2, 0.55, 0.75);
      t.tip.material.opacity = PA.flicker(elapsed, s + 0.6, 8.0, 0.7, 0.95);

      const sway = Math.sin(elapsed * 3.1 + s) * 0.03;
      t.outer.rotation.z = sway;
      t.mid.rotation.z = sway * 1.3;
      t.tip.rotation.z = sway * 1.6;

      t.light.intensity = PA.flicker(elapsed, s + 0.7, 8.5, 0.85, 1.45);

      for (let j = 0; j < t.embers.length; j++) {
        const e = t.embers[j];
        const cycle = 2.2;
        const phase = ((elapsed * 0.5 + e.seed) % cycle) / cycle;
        e.mesh.position.set(t.x + Math.sin(elapsed * 1.1 + e.seed) * 0.06, t.baseY + phase * 0.9, t.z - phase * 0.05);
        e.mesh.material.opacity = 0.7 * (1 - phase);
        const sc = 1.0 - phase * 0.5;
        e.mesh.scale.set(sc, sc, sc);
      }
    }
  }

  return {
    group: group,
    lights: lights,
    update: update,
  };
};
