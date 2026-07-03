import * as THREE from 'three';

export function build(ctx) {
  const { rng } = ctx;
  const group = new THREE.Group();
  const colliders = [];
  const flickers = [];

  // ---------------------------------------------------------------------------
  // Small deterministic helpers
  // ---------------------------------------------------------------------------
  const rand = (a, b) => a + rng() * (b - a);
  const pick = (arr) => arr[(rng() * arr.length) | 0];

  // ===========================================================================
  // (1) GROUND — 300x300 plane, procedural mottled CanvasTexture
  // ===========================================================================
  function makeGroundTexture() {
    const cnv = document.createElement('canvas');
    cnv.width = 1024;
    cnv.height = 1024;
    const g = cnv.getContext('2d');

    // Base dark earth
    g.fillStyle = '#211f1b';
    g.fillRect(0, 0, 1024, 1024);

    // Muted, desaturated palette: mud, moss, dead-grass, dark earth
    const palette = [
      '#2a271f', '#24221b', '#1d1b15', '#302c22',
      '#33362a', '#2c3226', '#3a3a2e', '#272a22',
      '#37311f', '#2e2a20', '#191712', '#3b3a30'
    ];

    // Large soft blotches (mud / moss patches)
    for (let i = 0; i < 340; i++) {
      const x = rng() * 1024;
      const y = rng() * 1024;
      const r = 22 + rng() * 130;
      const col = pick(palette);
      const rad = g.createRadialGradient(x, y, 0, x, y, r);
      rad.addColorStop(0, col);
      rad.addColorStop(1, 'rgba(0,0,0,0)');
      g.globalAlpha = 0.35 + rng() * 0.4;
      g.fillStyle = rad;
      g.beginPath();
      g.arc(x, y, r, 0, Math.PI * 2);
      g.fill();
    }
    g.globalAlpha = 1;

    // Fine speckle (dead grass / grit)
    for (let i = 0; i < 9000; i++) {
      const x = rng() * 1024;
      const y = rng() * 1024;
      const s = rng() * 2.2;
      const b = 12 + (rng() * 40) | 0;
      const greenish = rng() > 0.6;
      if (greenish) g.fillStyle = `rgb(${b},${b + 8},${(b * 0.7) | 0})`;
      else g.fillStyle = `rgb(${b + 4},${(b * 0.85) | 0},${(b * 0.6) | 0})`;
      g.globalAlpha = 0.5 + rng() * 0.4;
      g.fillRect(x, y, s, s);
    }
    g.globalAlpha = 1;

    // Faint cracks / dark streaks
    g.strokeStyle = 'rgba(8,7,5,0.5)';
    for (let i = 0; i < 120; i++) {
      g.lineWidth = 0.5 + rng() * 1.5;
      g.beginPath();
      let x = rng() * 1024;
      let y = rng() * 1024;
      g.moveTo(x, y);
      const seg = 3 + (rng() * 4) | 0;
      for (let s = 0; s < seg; s++) {
        x += (rng() - 0.5) * 90;
        y += (rng() - 0.5) * 90;
        g.lineTo(x, y);
      }
      g.stroke();
    }

    const tex = new THREE.CanvasTexture(cnv);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(8, 8);
    tex.anisotropy = 4;
    return tex;
  }

  const groundSeg = 96;
  const groundGeo = new THREE.PlaneGeometry(300, 300, groundSeg, groundSeg);
  // Gentle low-freq undulation, flat in walkable interior (|x|<50,|z|<60)
  {
    const pos = groundGeo.attributes.position;
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i);
      const y = pos.getY(i); // plane local Y == world Z after rotation
      let h =
        Math.sin(x * 0.035 + 1.3) * Math.cos(y * 0.03 - 0.7) * 1.6 +
        Math.sin(x * 0.012 - 2.1) * 1.1 +
        Math.cos(y * 0.017 + 0.4) * 1.0;
      // Fade to flat inside walkable interior
      const interior =
        Math.max(0, 1 - Math.max(Math.abs(x) / 50, Math.abs(y) / 60));
      const edge = 1 - interior;
      h *= edge * edge;
      // clamp interior residual
      if (Math.abs(x) < 50 && Math.abs(y) < 60) {
        h = Math.max(-0.15, Math.min(0.15, h));
      }
      pos.setZ(i, h);
    }
    groundGeo.computeVertexNormals();
  }
  const groundMat = new THREE.MeshStandardMaterial({
    map: makeGroundTexture(),
    roughness: 1.0,
    metalness: 0.0,
    color: 0x8a8578
  });
  const ground = new THREE.Mesh(groundGeo, groundMat);
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  group.add(ground);

  // ===========================================================================
  // (2) THE LANE — dirt/cobble strip ~7 wide, x=0, z from +55 to -68
  // ===========================================================================
  function makeLaneTexture() {
    const cnv = document.createElement('canvas');
    cnv.width = 256;
    cnv.height = 1024;
    const g = cnv.getContext('2d');
    g.fillStyle = '#2c281f';
    g.fillRect(0, 0, 256, 1024);

    // Cobble stones
    for (let i = 0; i < 1400; i++) {
      const x = rng() * 256;
      const y = rng() * 1024;
      const w = 6 + rng() * 16;
      const h = 6 + rng() * 14;
      const b = 30 + (rng() * 34) | 0;
      g.fillStyle = `rgb(${b},${(b * 0.92) | 0},${(b * 0.78) | 0})`;
      g.globalAlpha = 0.6 + rng() * 0.35;
      g.beginPath();
      g.ellipse(x, y, w * 0.5, h * 0.5, rng() * Math.PI, 0, Math.PI * 2);
      g.fill();
      // dark rim
      g.globalAlpha = 0.35;
      g.strokeStyle = 'rgba(0,0,0,0.6)';
      g.lineWidth = 1;
      g.stroke();
    }
    g.globalAlpha = 1;

    // Central worn dirt track (lighter)
    const grad = g.createLinearGradient(0, 0, 256, 0);
    grad.addColorStop(0, 'rgba(0,0,0,0)');
    grad.addColorStop(0.5, 'rgba(70,62,48,0.35)');
    grad.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 256, 1024);

    const tex = new THREE.CanvasTexture(cnv);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(1, 10);
    tex.anisotropy = 4;
    return tex;
  }

  const laneZ0 = 55;
  const laneZ1 = -68;
  const laneLen = laneZ0 - laneZ1;
  const laneGeo = new THREE.PlaneGeometry(7, laneLen);
  const laneMat = new THREE.MeshStandardMaterial({
    map: makeLaneTexture(),
    roughness: 1.0,
    metalness: 0.0,
    color: 0x9a9385
  });
  const lane = new THREE.Mesh(laneGeo, laneMat);
  lane.rotation.x = -Math.PI / 2;
  lane.position.set(0, 0.02, (laneZ0 + laneZ1) / 2);
  lane.receiveShadow = true;
  group.add(lane);

  // Faint darker shoulder edges to frame the lane
  const shoulderMat = new THREE.MeshStandardMaterial({
    color: 0x1a1813,
    roughness: 1.0,
    metalness: 0.0
  });
  for (const sx of [-3.9, 3.9]) {
    const sh = new THREE.Mesh(new THREE.PlaneGeometry(1.1, laneLen), shoulderMat);
    sh.rotation.x = -Math.PI / 2;
    sh.position.set(sx, 0.015, (laneZ0 + laneZ1) / 2);
    group.add(sh);
  }

  // ===========================================================================
  // (3) GROUND FOG — large radial-alpha planes, slow drift + rotate
  // ===========================================================================
  function makeFogTexture(tint) {
    const cnv = document.createElement('canvas');
    cnv.width = 256;
    cnv.height = 256;
    const g = cnv.getContext('2d');
    const grad = g.createRadialGradient(128, 128, 0, 128, 128, 128);
    grad.addColorStop(0.0, tint.replace('A', '0.9'));
    grad.addColorStop(0.45, tint.replace('A', '0.4'));
    grad.addColorStop(1.0, tint.replace('A', '0.0'));
    g.fillStyle = grad;
    g.fillRect(0, 0, 256, 256);
    // break up the disc a little
    for (let i = 0; i < 40; i++) {
      const x = rng() * 256;
      const y = rng() * 256;
      const r = 10 + rng() * 50;
      const rad = g.createRadialGradient(x, y, 0, x, y, r);
      rad.addColorStop(0, tint.replace('A', String(0.12 + rng() * 0.15)));
      rad.addColorStop(1, tint.replace('A', '0.0'));
      g.fillStyle = rad;
      g.beginPath();
      g.arc(x, y, r, 0, Math.PI * 2);
      g.fill();
    }
    const tex = new THREE.CanvasTexture(cnv);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  const fogTexPale = makeFogTexture('rgba(150,165,180,A)');
  const fogTexCold = makeFogTexture('rgba(120,140,175,A)');
  const fogPlanes = [];

  function addFog(x, z, size, y, opacity, tex) {
    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
      opacity,
      blending: THREE.NormalBlending,
      color: 0xffffff
    });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(size, size), mat);
    m.rotation.x = -Math.PI / 2;
    m.position.set(x, y, z);
    m.rotation.z = rng() * Math.PI * 2;
    group.add(m);
    fogPlanes.push({
      mesh: m,
      baseX: x,
      baseZ: z,
      driftX: rand(0.2, 0.6) * (rng() > 0.5 ? 1 : -1),
      driftZ: rand(0.15, 0.5) * (rng() > 0.5 ? 1 : -1),
      rotSpeed: rand(0.01, 0.04) * (rng() > 0.5 ? 1 : -1),
      phase: rng() * 6.28
    });
    return m;
  }

  // Scattered general fog across village + wilds
  addFog(-18, 20, 70, 0.7, 0.12, fogTexPale);
  addFog(22, -10, 80, 0.9, 0.11, fogTexPale);
  addFog(-30, -40, 85, 1.0, 0.10, fogTexPale);
  addFog(35, 30, 60, 0.6, 0.13, fogTexPale);
  addFog(0, -55, 90, 1.1, 0.09, fogTexPale);
  addFog(-40, 35, 55, 0.5, 0.14, fogTexPale);
  addFog(45, -35, 70, 0.85, 0.10, fogTexPale);
  addFog(10, 45, 50, 0.5, 0.15, fogTexPale);

  // ===========================================================================
  // (4) WILDERNESS — dead trees, stumps, boulders, logs, reeds
  // ===========================================================================

  // Reusable materials
  const barkMat = new THREE.MeshStandardMaterial({
    color: 0x1c150f,
    roughness: 1.0,
    metalness: 0.0
  });
  const barkMat2 = new THREE.MeshStandardMaterial({
    color: 0x241a12,
    roughness: 1.0,
    metalness: 0.0
  });
  const boulderMat = new THREE.MeshStandardMaterial({
    color: 0x353a30,
    roughness: 1.0,
    metalness: 0.0
  });
  const boulderMat2 = new THREE.MeshStandardMaterial({
    color: 0x2c322b,
    roughness: 1.0,
    metalness: 0.0
  });
  const reedMat = new THREE.MeshStandardMaterial({
    color: 0x2a281c,
    roughness: 1.0,
    metalness: 0.0,
    side: THREE.DoubleSide,
    transparent: true,
    depthWrite: false
  });

  // Reusable geometries
  const branchGeo = new THREE.CylinderGeometry(0.06, 0.11, 1, 5);
  const boulderGeoA = new THREE.IcosahedronGeometry(1, 0);
  const boulderGeoB = new THREE.DodecahedronGeometry(1, 0);
  const stumpGeo = new THREE.CylinderGeometry(0.42, 0.55, 0.7, 8);
  const logGeo = new THREE.CylinderGeometry(0.35, 0.4, 4.5, 8);

  // Is a point in the free lane corridor?
  const inLane = (x) => Math.abs(x) < 4.5;

  // Reject positions that block the lane; keep out of dense village center path
  function validScatter(x, z, margin = 1.2) {
    if (Math.abs(x) < 4.5 + margin) return false; // never in/near lane
    return true;
  }

  // --- Dead trees (~45) ------------------------------------------------------
  function makeDeadTree(x, z, scale) {
    const t = new THREE.Group();
    const trunkH = rand(2.6, 4.6) * scale;
    const trunkR = rand(0.18, 0.34) * scale;
    const trunkGeo = new THREE.CylinderGeometry(trunkR * 0.55, trunkR, trunkH, 7);
    const trunk = new THREE.Mesh(trunkGeo, rng() > 0.5 ? barkMat : barkMat2);
    trunk.position.y = trunkH / 2;
    trunk.castShadow = true;
    t.add(trunk);

    // Branches — several tapering cylinders angled up
    const nBranch = 4 + (rng() * 5) | 0;
    for (let b = 0; b < nBranch; b++) {
      const bl = rand(0.8, 2.2) * scale;
      const br = new THREE.Mesh(branchGeo, trunk.material);
      const yAttach = trunkH * rand(0.45, 0.95);
      const ang = rng() * Math.PI * 2;
      const tilt = rand(0.5, 1.15); // from vertical
      br.scale.set(1, bl, 1);
      // orient: start pointing up (+Y), rotate outward
      br.position.set(0, yAttach, 0);
      br.rotation.z = Math.sin(ang) * tilt;
      br.rotation.x = Math.cos(ang) * tilt;
      // move along its own axis so base sits at attach point
      br.translateY(bl / 2);
      br.castShadow = true;
      t.add(br);

      // occasional sub-twig
      if (rng() > 0.5) {
        const tw = new THREE.Mesh(branchGeo, trunk.material);
        const twl = bl * rand(0.4, 0.7);
        tw.scale.set(0.6, twl, 0.6);
        tw.position.copy(br.position);
        tw.translateY(bl / 2);
        tw.rotation.z = br.rotation.z + rand(-0.8, 0.8);
        tw.rotation.x = br.rotation.x + rand(-0.8, 0.8);
        tw.translateY(twl / 2);
        t.add(tw);
      }
    }
    t.position.set(x, 0, z);
    t.rotation.y = rng() * Math.PI * 2;
    group.add(t);

    // collider only if near walkable area (roughly |x|<55 & |z|<70)
    if (Math.abs(x) < 60 && z > -95 && z < 70) {
      const cr = trunkR + 0.35;
      colliders.push({
        minX: x - cr, maxX: x + cr,
        minZ: z - cr, maxZ: z + cr
      });
    }
  }

  let placedTrees = 0;
  let guard = 0;
  while (placedTrees < 46 && guard < 4000) {
    guard++;
    // ring/scatter placement in outer wilds & sparse mid
    const side = rng();
    let x, z;
    if (side < 0.5) {
      // outer ring by X
      x = (rng() > 0.5 ? 1 : -1) * rand(30, 140);
      z = rand(-140, 140);
    } else {
      // beyond village by Z (north wilds / south wilds)
      x = rand(-140, 140);
      z = (rng() > 0.5 ? 1 : -1) * rand(60, 140);
    }
    if (!validScatter(x, z, 1.5)) continue;
    // keep graveyard (around 55,5) a touch sparse of random trees handled later
    makeDeadTree(x, z, rand(0.8, 1.5));
    placedTrees++;
  }

  // --- Stumps (~12) ----------------------------------------------------------
  let placedStumps = 0;
  guard = 0;
  while (placedStumps < 12 && guard < 2000) {
    guard++;
    const x = rand(-120, 120);
    const z = (rng() > 0.5 ? 1 : -1) * rand(30, 130);
    if (!validScatter(x, z, 1.2)) continue;
    const s = new THREE.Mesh(stumpGeo, rng() > 0.5 ? barkMat : barkMat2);
    const sc = rand(0.7, 1.3);
    s.scale.set(sc, rand(0.6, 1.1), sc);
    s.position.set(x, 0.33 * sc, z);
    s.rotation.y = rng() * Math.PI;
    s.castShadow = true;
    group.add(s);
    placedStumps++;
  }

  // --- Boulders (~30) --------------------------------------------------------
  let placedBoulders = 0;
  guard = 0;
  while (placedBoulders < 32 && guard < 3000) {
    guard++;
    const x = rand(-140, 140);
    const z = rand(-140, 140);
    // allow in outer + mid, but not lane and not far inside village core lane
    if (!validScatter(x, z, 1.5)) continue;
    if (Math.abs(x) < 30 && Math.abs(z) < 45) {
      // keep village core relatively clear of random boulders
      if (rng() > 0.25) continue;
    }
    const geo = rng() > 0.5 ? boulderGeoA : boulderGeoB;
    const b = new THREE.Mesh(geo, rng() > 0.5 ? boulderMat : boulderMat2);
    const s = rand(0.6, 2.6);
    b.scale.set(s * rand(0.8, 1.3), s * rand(0.6, 1.0), s * rand(0.8, 1.3));
    b.position.set(x, s * 0.35, z);
    b.rotation.set(rng() * Math.PI, rng() * Math.PI, rng() * Math.PI);
    b.castShadow = true;
    b.receiveShadow = true;
    group.add(b);
    // collider for larger boulders near walkable area
    if (s > 1.1 && Math.abs(x) < 60 && z > -95 && z < 70) {
      const r = s * 0.9;
      colliders.push({
        minX: x - r, maxX: x + r,
        minZ: z - r, maxZ: z + r
      });
    }
    placedBoulders++;
  }

  // --- Fallen logs (a few) ---------------------------------------------------
  for (let i = 0; i < 6; i++) {
    guard = 0;
    let x, z;
    do {
      x = rand(-110, 110);
      z = (rng() > 0.5 ? 1 : -1) * rand(35, 120);
      guard++;
    } while (!validScatter(x, z, 3) && guard < 60);
    if (!validScatter(x, z, 3)) continue;
    const log = new THREE.Mesh(logGeo, rng() > 0.5 ? barkMat : barkMat2);
    const sc = rand(0.7, 1.3);
    log.scale.set(sc, sc, sc);
    log.rotation.z = Math.PI / 2;
    log.rotation.y = rng() * Math.PI;
    log.position.set(x, 0.35 * sc, z);
    log.castShadow = true;
    group.add(log);
  }

  // --- Dead reed patches -----------------------------------------------------
  function makeReedPatch(x, z) {
    const patch = new THREE.Group();
    const n = 8 + (rng() * 10) | 0;
    for (let i = 0; i < n; i++) {
      const h = rand(0.7, 1.7);
      const blade = new THREE.Mesh(new THREE.PlaneGeometry(0.05, h), reedMat);
      const rx = x + rand(-0.9, 0.9);
      const rz = z + rand(-0.9, 0.9);
      blade.position.set(rx, h / 2, rz);
      blade.rotation.y = rng() * Math.PI;
      blade.rotation.z = rand(-0.25, 0.25);
      patch.add(blade);
    }
    group.add(patch);
  }
  for (let i = 0; i < 14; i++) {
    guard = 0;
    let x, z;
    do {
      x = rand(-120, 120);
      z = (rng() > 0.5 ? 1 : -1) * rand(35, 130);
      guard++;
    } while (!validScatter(x, z, 2) && guard < 60);
    if (validScatter(x, z, 2)) makeReedPatch(x, z);
  }

  // ===========================================================================
  // (5) GRAVEYARD near (55, 0, 5)
  // ===========================================================================
  const gvX = 55;
  const gvZ = 5;
  const graveyard = new THREE.Group();
  group.add(graveyard);

  const stoneMat = new THREE.MeshStandardMaterial({
    color: 0x40444a,
    roughness: 0.95,
    metalness: 0.0
  });
  const stoneMatDark = new THREE.MeshStandardMaterial({
    color: 0x33373d,
    roughness: 0.95,
    metalness: 0.0
  });

  // Tombstone geometry (thin rounded box) — reuse
  const tombGeo = new THREE.BoxGeometry(0.9, 1.3, 0.16);
  const tombTopGeo = new THREE.CylinderGeometry(0.45, 0.45, 0.16, 12, 1, false, 0, Math.PI);

  function makeTombstone(x, z) {
    const t = new THREE.Group();
    const mat = rng() > 0.5 ? stoneMat : stoneMatDark;
    const body = new THREE.Mesh(tombGeo, mat);
    const h = rand(0.9, 1.5);
    body.scale.y = h / 1.3;
    body.position.y = (1.3 * body.scale.y) / 2;
    body.castShadow = true;
    t.add(body);
    // rounded top cap
    const cap = new THREE.Mesh(tombTopGeo, mat);
    cap.rotation.x = Math.PI / 2;
    cap.rotation.z = 0;
    cap.position.y = 1.3 * body.scale.y;
    cap.castShadow = true;
    t.add(cap);

    t.position.set(x, 0, z);
    t.rotation.y = rand(-0.5, 0.5) + (rng() > 0.5 ? 0 : Math.PI);
    // tilt
    t.rotation.z = rand(-0.22, 0.22);
    t.rotation.x = rand(-0.12, 0.12);
    graveyard.add(t);
  }

  // ~14 tombstones in loose rows
  let tCount = 0;
  for (let row = 0; row < 4 && tCount < 14; row++) {
    for (let col = 0; col < 4 && tCount < 14; col++) {
      const x = gvX - 6 + col * 3.4 + rand(-0.7, 0.7);
      const z = gvZ - 6 + row * 3.4 + rand(-0.7, 0.7);
      if (rng() > 0.12) {
        makeTombstone(x, z);
        tCount++;
      }
    }
  }

  // A couple stone crosses
  function makeCross(x, z) {
    const c = new THREE.Group();
    const mat = stoneMatDark;
    const vert = new THREE.Mesh(new THREE.BoxGeometry(0.22, 1.7, 0.22), mat);
    vert.position.y = 0.85;
    vert.castShadow = true;
    c.add(vert);
    const horiz = new THREE.Mesh(new THREE.BoxGeometry(0.9, 0.22, 0.22), mat);
    horiz.position.y = 1.25;
    horiz.castShadow = true;
    c.add(horiz);
    c.position.set(x, 0, z);
    c.rotation.y = rand(-0.4, 0.4);
    c.rotation.z = rand(-0.14, 0.14);
    graveyard.add(c);
  }
  makeCross(gvX - 7.5, gvZ - 1.5);
  makeCross(gvX + 6.5, gvZ + 4);

  // Low rusty iron railing around graveyard (with colliders, not in lane)
  const railMat = new THREE.MeshStandardMaterial({
    color: 0x2b211a,
    roughness: 0.95,
    metalness: 0.25
  });
  const halfW = 9; // graveyard half-extent
  const gMinX = gvX - halfW, gMaxX = gvX + halfW;
  const gMinZ = gvZ - halfW, gMaxZ = gvZ + halfW;

  const postGeo = new THREE.BoxGeometry(0.12, 0.9, 0.12);
  const barGeo = new THREE.BoxGeometry(1, 0.05, 0.05);
  const spikeGeo = new THREE.ConeGeometry(0.07, 0.22, 4);

  function railRun(x0, z0, x1, z1) {
    const dx = x1 - x0, dz = z1 - z0;
    const len = Math.hypot(dx, dz);
    const n = Math.max(1, Math.round(len / 1.2));
    const ang = Math.atan2(dz, dx);
    for (let i = 0; i <= n; i++) {
      const t = i / n;
      const px = x0 + dx * t;
      const pz = z0 + dz * t;
      const post = new THREE.Mesh(postGeo, railMat);
      post.position.set(px, 0.45, pz);
      post.castShadow = true;
      graveyard.add(post);
      const spike = new THREE.Mesh(spikeGeo, railMat);
      spike.position.set(px, 0.9 + 0.11, pz);
      spike.rotation.y = Math.PI / 4;
      graveyard.add(spike);
    }
    // horizontal bars (top & mid)
    for (const by of [0.75, 0.4]) {
      const bar = new THREE.Mesh(barGeo, railMat);
      bar.scale.x = len;
      bar.position.set(x0 + dx / 2, by, z0 + dz / 2);
      bar.rotation.y = -ang;
      graveyard.add(bar);
    }
  }

  // Four sides but leave a gap on the west side (facing village) as an entrance
  // West side entrance gap:
  railRun(gMinX, gMinZ, gMaxX, gMinZ); // south
  railRun(gMinX, gMaxZ, gMaxX, gMaxZ); // north
  railRun(gMaxX, gMinZ, gMaxX, gMaxZ); // east
  // west split for gate
  railRun(gMinX, gMinZ, gMinX, gvZ - 1.6); // west-south segment
  railRun(gMinX, gvZ + 1.6, gMinX, gMaxZ); // west-north segment

  // Fence colliders (thin AABBs along each run). None touch lane (x>=46).
  const fenceT = 0.25;
  colliders.push({ minX: gMinX, maxX: gMaxX, minZ: gMinZ - fenceT, maxZ: gMinZ + fenceT }); // south
  colliders.push({ minX: gMinX, maxX: gMaxX, minZ: gMaxZ - fenceT, maxZ: gMaxZ + fenceT }); // north
  colliders.push({ minX: gMaxX - fenceT, maxX: gMaxX + fenceT, minZ: gMinZ, maxZ: gMaxZ }); // east
  colliders.push({ minX: gMinX - fenceT, maxX: gMinX + fenceT, minZ: gMinZ, maxZ: gvZ - 1.6 }); // west-s
  colliders.push({ minX: gMinX - fenceT, maxX: gMinX + fenceT, minZ: gvZ + 1.6, maxZ: gMaxZ }); // west-n

  // Denser, colder fog over the graveyard
  addFog(gvX, gvZ, 26, 0.5, 0.18, fogTexCold);
  addFog(gvX - 3, gvZ + 4, 22, 0.7, 0.16, fogTexCold);
  addFog(gvX + 4, gvZ - 3, 20, 0.4, 0.17, fogTexCold);

  // A few dead trees flanking the graveyard for mood
  makeDeadTree(gvX - 11, gvZ - 8, 1.3);
  makeDeadTree(gvX + 11, gvZ + 9, 1.4);
  makeDeadTree(gvX + 12, gvZ - 10, 1.1);

  // Some scattered boulders and a couple sunken mounds inside graveyard
  for (let i = 0; i < 5; i++) {
    const x = rand(gMinX + 1.5, gMaxX - 1.5);
    const z = rand(gMinZ + 1.5, gMaxZ - 1.5);
    const b = new THREE.Mesh(boulderGeoA, boulderMat2);
    const s = rand(0.4, 0.8);
    b.scale.set(s, s * 0.5, s);
    b.position.set(x, s * 0.2, z);
    b.rotation.set(rng() * Math.PI, rng() * Math.PI, rng() * Math.PI);
    graveyard.add(b);
  }

  // ===========================================================================
  // update() — drift & rotate fog planes
  // ===========================================================================
  function update(dt, elapsed) {
    for (const f of fogPlanes) {
      const t = elapsed + f.phase;
      f.mesh.position.x = f.baseX + Math.sin(t * 0.05) * 4 + f.driftX * elapsed * 0.15;
      f.mesh.position.z = f.baseZ + Math.cos(t * 0.045) * 4 + f.driftZ * elapsed * 0.15;
      // wrap drift softly so they don't wander to infinity
      f.mesh.position.x = f.baseX + ((f.mesh.position.x - f.baseX + 60) % 120) - 60;
      f.mesh.position.z = f.baseZ + ((f.mesh.position.z - f.baseZ + 60) % 120) - 60;
      f.mesh.rotation.z += f.rotSpeed * dt;
      f.mesh.position.y += Math.sin(t * 0.3) * 0.0008;
    }
  }

  return { group, colliders, flickers, update };
}
