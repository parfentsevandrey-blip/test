import * as THREE from 'three';

export function build(ctx) {
  const { rng } = ctx;
  const group = new THREE.Group();
  const colliders = [];
  const flickers = [];

  // ------------------------------------------------------------------
  // Shared materials (reused everywhere for perf)
  // ------------------------------------------------------------------
  const stoneMat = new THREE.MeshStandardMaterial({ color: 0x3c3c44, roughness: 1.0, metalness: 0.0 });
  const stoneDark = new THREE.MeshStandardMaterial({ color: 0x303038, roughness: 1.0, metalness: 0.0 });
  const roofMat = new THREE.MeshStandardMaterial({ color: 0x201e26, roughness: 0.95, metalness: 0.0 });
  const woodMat = new THREE.MeshStandardMaterial({ color: 0x2c2119, roughness: 0.95, metalness: 0.0 });
  const metalMat = new THREE.MeshStandardMaterial({ color: 0x14141a, roughness: 0.6, metalness: 0.5 });
  const flameMat = new THREE.MeshStandardMaterial({ color: 0xff8a3c, emissive: 0xff7a26, emissiveIntensity: 2.6, roughness: 0.6 });
  const winWarm = new THREE.MeshStandardMaterial({ color: 0xffb45a, emissive: 0xffa23c, emissiveIntensity: 2.4, roughness: 0.8, side: THREE.DoubleSide });
  const winCold = new THREE.MeshStandardMaterial({ color: 0x6f9bff, emissive: 0x3a6fff, emissiveIntensity: 2.2, roughness: 0.8, side: THREE.DoubleSide });
  const slitMat = new THREE.MeshStandardMaterial({ color: 0xffa94e, emissive: 0xff8a2c, emissiveIntensity: 2.2, roughness: 0.8, side: THREE.DoubleSide });
  const bannerMat = new THREE.MeshStandardMaterial({ color: 0x5a1417, emissive: 0x2a0608, emissiveIntensity: 0.6, roughness: 0.95, side: THREE.DoubleSide, transparent: true, depthWrite: false });
  const flagMat = new THREE.MeshStandardMaterial({ color: 0x431016, roughness: 0.95, side: THREE.DoubleSide });

  // geometry cache
  const geoCache = new Map();
  function box(w, h, d) {
    const k = 'b' + w.toFixed(2) + '_' + h.toFixed(2) + '_' + d.toFixed(2);
    let g = geoCache.get(k);
    if (!g) { g = new THREE.BoxGeometry(w, h, d); geoCache.set(k, g); }
    return g;
  }
  const flameGeo = new THREE.ConeGeometry(0.26, 0.75, 6);
  const merlonRingGeo = new THREE.BoxGeometry(0.7, 1.0, 0.55);

  const swayers = []; // flags / banners animated in update()
  const flames = [];  // flame meshes that pulse subtly

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------
  function addBox(mat, cx, cy, cz, w, h, d) {
    const m = new THREE.Mesh(box(w, h, d), mat);
    m.position.set(cx, cy, cz);
    group.add(m);
    return m;
  }

  function addCollider(minX, maxX, minZ, maxZ) {
    colliders.push({ minX, maxX, minZ, maxZ });
  }

  // wall-top merlons along a run parallel to X
  function crenelX(x0, x1, zc, topY, thick) {
    const period = 1.8, mW = 0.9, mH = 1.0;
    const len = x1 - x0;
    const n = Math.max(1, Math.round(len / period));
    const g = box(mW, mH, thick * 0.95);
    for (let i = 0; i < n; i++) {
      const cx = x0 + (i + 0.5) * len / n;
      const m = new THREE.Mesh(g, stoneMat);
      m.position.set(cx, topY + mH / 2, zc);
      group.add(m);
    }
  }
  // wall-top merlons along a run parallel to Z
  function crenelZ(z0, z1, xc, topY, thick) {
    const period = 1.8, mD = 0.9, mH = 1.0;
    const len = z1 - z0;
    const n = Math.max(1, Math.round(len / period));
    const g = box(thick * 0.95, mH, mD);
    for (let i = 0; i < n; i++) {
      const cz = z0 + (i + 0.5) * len / n;
      const m = new THREE.Mesh(g, stoneMat);
      m.position.set(xc, topY + mH / 2, cz);
      group.add(m);
    }
  }

  // wall-mounted torch: emissive flame + warm PointLight (registered in flickers)
  function addTorch(x, y, z) {
    // iron bracket
    const bracket = new THREE.Mesh(box(0.35, 0.35, 0.35), metalMat);
    bracket.position.set(x, y - 0.4, z);
    group.add(bracket);
    const flame = new THREE.Mesh(flameGeo, flameMat);
    flame.position.set(x, y, z);
    group.add(flame);
    flames.push({ mesh: flame, phase: rng() * 6.28, speed: 8 + rng() * 6 });
    const light = new THREE.PointLight(0xff8a3c, 2.0, 20, 2);
    light.position.set(x, y + 0.1, z);
    group.add(light);
    flickers.push({ light, base: 2.0, amp: 0.35, speed: 7 + rng() * 6, phase: rng() * 6.28 });
  }

  // tattered flag/banner on a pole, swaying in update()
  function addFlag(x, y, z, poleH) {
    const pole = new THREE.Mesh(box(0.18, poleH, 0.18), woodMat);
    pole.position.set(x, y + poleH / 2, z);
    group.add(pole);
    const flagW = 2.2, flagH = 1.3;
    const flag = new THREE.Mesh(new THREE.PlaneGeometry(flagW, flagH, 6, 1), flagMat);
    // pivot: attach to a small group so we can rotate about the pole
    const pivot = new THREE.Group();
    pivot.position.set(x, y + poleH - 0.2, z);
    flag.position.set(flagW / 2, -flagH / 2, 0);
    pivot.add(flag);
    group.add(pivot);
    swayers.push({ obj: pivot, phase: rng() * 6.28, amp: 0.18, speed: 1.2 + rng() * 0.8 });
  }

  // ------------------------------------------------------------------
  // Constants
  // ------------------------------------------------------------------
  const T = 2.5;      // wall thickness
  const H = 9;        // wall height
  const zSouth = -73;
  const zNorth = -142;
  const xEast = 42;
  const xWest = -42;
  const gateHalf = 4.5; // gate opening x in [-4.5,4.5] => 9m wide, keeps the lane fully clear

  // ------------------------------------------------------------------
  // Courtyard flagstone floor (decorative, no collider)
  // ------------------------------------------------------------------
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(84, 69), stoneDark);
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(0, 0.02, (zSouth + zNorth) / 2);
  group.add(floor);

  // ------------------------------------------------------------------
  // (1) CURTAIN WALLS + battlements + walkway ledge
  // ------------------------------------------------------------------
  // North wall (solid, full span)
  addBox(stoneMat, 0, H / 2, zNorth, xEast - xWest, H, T);
  crenelX(xWest, xEast, zNorth, H, T);
  addBox(stoneDark, 0, H - 1.2, zNorth + 1.6, xEast - xWest, 0.4, 1.4); // walkway ledge inner
  addCollider(xWest - T / 2, xEast + T / 2, zNorth - T / 2, zNorth + T / 2);

  // South wall — two segments, GATE GAP at x in [-4.5,4.5] (lane kept fully clear)
  const gh = gateHalf;
  addBox(stoneMat, (xWest - gh) / 2, H / 2, zSouth, (-gh - xWest), H, T);
  crenelX(xWest, -gh, zSouth, H, T);
  addBox(stoneMat, (xEast + gh) / 2, H / 2, zSouth, (xEast - gh), H, T);
  crenelX(gh, xEast, zSouth, H, T);
  addBox(stoneDark, (xWest - gh) / 2, H - 1.2, zSouth - 1.6, (-gh - xWest), 0.4, 1.4);
  addBox(stoneDark, (xEast + gh) / 2, H - 1.2, zSouth - 1.6, (xEast - gh), 0.4, 1.4);
  // colliders leave gap x in [-4.5,4.5]
  addCollider(xWest - T / 2, -gh, zSouth - T / 2, zSouth + T / 2);
  addCollider(gh, xEast + T / 2, zSouth - T / 2, zSouth + T / 2);

  // East wall
  addBox(stoneMat, xEast, H / 2, (zNorth + zSouth) / 2, T, H, (zSouth - zNorth));
  crenelZ(zNorth, zSouth, xEast, H, T);
  addBox(stoneDark, xEast - 1.6, H - 1.2, (zNorth + zSouth) / 2, 1.4, 0.4, (zSouth - zNorth));
  addCollider(xEast - T / 2, xEast + T / 2, zNorth - T / 2, zSouth + T / 2);

  // West wall
  addBox(stoneMat, xWest, H / 2, (zNorth + zSouth) / 2, T, H, (zSouth - zNorth));
  crenelZ(zNorth, zSouth, xWest, H, T);
  addBox(stoneDark, xWest + 1.6, H - 1.2, (zNorth + zSouth) / 2, 1.4, 0.4, (zSouth - zNorth));
  addCollider(xWest - T / 2, xWest + T / 2, zNorth - T / 2, zSouth + T / 2);

  // ------------------------------------------------------------------
  // (2) FOUR corner TOWERS
  // ------------------------------------------------------------------
  function buildTower(cx, cz, r, bodyH, roofH, warm) {
    const bodyGeo = new THREE.CylinderGeometry(r, r * 1.05, bodyH, 16);
    const body = new THREE.Mesh(bodyGeo, stoneMat);
    body.position.set(cx, bodyH / 2, cz);
    group.add(body);

    // crenellation ring
    const rings = 14;
    for (let i = 0; i < rings; i++) {
      const a = (i / rings) * Math.PI * 2;
      const mx = cx + Math.cos(a) * r;
      const mz = cz + Math.sin(a) * r;
      const m = new THREE.Mesh(merlonRingGeo, stoneMat);
      m.position.set(mx, bodyH + 0.5, mz);
      m.rotation.y = -a;
      group.add(m);
    }

    // conical roof (sits above crenellations)
    const roof = new THREE.Mesh(new THREE.ConeGeometry(r * 1.1, roofH, 16), roofMat);
    roof.position.set(cx, bodyH + 1.2 + roofH / 2, cz);
    group.add(roof);

    // thin emissive arrow-slit windows at a few heights/angles
    const slitGeo = new THREE.PlaneGeometry(0.22, 1.5);
    for (let s = 0; s < 4; s++) {
      const a = (s / 4) * Math.PI * 2 + 0.3;
      const yy = bodyH * (0.45 + (s % 2) * 0.22);
      const slit = new THREE.Mesh(slitGeo, slitMat);
      slit.position.set(cx + Math.cos(a) * (r + 0.03), yy, cz + Math.sin(a) * (r + 0.03));
      slit.rotation.y = -a + Math.PI / 2;
      group.add(slit);
    }

    // torch at tower top
    addTorch(cx, bodyH + 0.7, cz + (cz > -107 ? 0.8 : -0.8) * 0 + 0);
    // flag on the roof peak
    addFlag(cx, bodyH + 1.2 + roofH, cz, 2.2);

    // collider (square AABB around the tower)
    addCollider(cx - r, cx + r, cz - r, cz + r);
  }

  const cornerR = 4.5, cornerH = 14, cornerRoof = 6.5;
  buildTower(xWest, zNorth, cornerR, cornerH, cornerRoof);
  buildTower(xEast, zNorth, cornerR, cornerH, cornerRoof);
  buildTower(xWest, zSouth, cornerR, cornerH, cornerRoof);
  buildTower(xEast, zSouth, cornerR, cornerH, cornerRoof);

  // ------------------------------------------------------------------
  // (3) GATEHOUSE — flanking towers, pointed arch, portcullis, banners
  // ------------------------------------------------------------------
  const ghX = 8, ghR = 3, ghH = 12.5, ghRoof = 6;
  buildTower(-ghX, zSouth, ghR, ghH, ghRoof);
  buildTower(ghX, zSouth, ghR, ghH, ghRoof);

  // Pointed arch over the gate opening (lintel above y>=5, NO collider — player walks under)
  addBox(stoneMat, 0, H / 2 + 2.5, zSouth, gateHalf * 2 + 1.6, 4, T + 0.4); // upper lintel block y 5..9
  // pointed peak — two angled slabs meeting at apex
  const peakGeo = box(4.6, 0.6, T + 0.3);
  const peakL = new THREE.Mesh(peakGeo, stoneMat);
  peakL.position.set(-2.0, 9.5, zSouth);
  peakL.rotation.z = 0.6;
  group.add(peakL);
  const peakR = new THREE.Mesh(peakGeo, stoneMat);
  peakR.position.set(2.0, 9.5, zSouth);
  peakR.rotation.z = -0.6;
  group.add(peakR);
  // arch underside emissive keystone glow-line
  const archTrim = new THREE.Mesh(new THREE.TorusGeometry(4.5, 0.16, 6, 22, Math.PI), slitMat);
  archTrim.position.set(0, 5.0, zSouth - 0.1);
  group.add(archTrim);

  // Raised PORTCULLIS — grid of dark bars, bottom at y~4.2 (raised above the player), no collider
  const portTop = 8.6, portBot = 4.2;
  const vBarGeo = box(0.14, portTop - portBot, 0.14);
  for (let i = -4; i <= 4; i++) {
    const bar = new THREE.Mesh(vBarGeo, metalMat);
    bar.position.set(i * 1.0, (portTop + portBot) / 2, zSouth + 0.1);
    group.add(bar);
  }
  const hBarGeo = box(8.8, 0.14, 0.14);
  for (let j = 0; j < 3; j++) {
    const bar = new THREE.Mesh(hBarGeo, metalMat);
    bar.position.set(0, portBot + 0.4 + j * ((portTop - portBot - 0.8) / 2), zSouth + 0.1);
    group.add(bar);
  }

  // Tattered BANNERS either side of the gate (front face, swaying)
  function addBanner(x) {
    const pivot = new THREE.Group();
    pivot.position.set(x, 8.0, zSouth + T / 2 + 0.15);
    const b = new THREE.Mesh(new THREE.PlaneGeometry(1.8, 4.2, 3, 4), bannerMat);
    b.position.set(0, -2.1, 0);
    pivot.add(b);
    group.add(pivot);
    swayers.push({ obj: pivot, phase: rng() * 6.28, amp: 0.09, speed: 0.9 + rng() * 0.5, axisX: true });
  }
  addBanner(-4.4);
  addBanner(4.4);

  // Two BRAZIERS flanking the gate (mounted on gatehouse inner faces)
  addTorch(-5.2, 5.0, zSouth + 1.6);
  addTorch(5.2, 5.0, zSouth + 1.6);

  // Plank DRAWBRIDGE over a shallow dry moat (flat on ground, decorative, no collider on lane)
  const moat = new THREE.Mesh(box(14, 0.3, 5), stoneDark);
  moat.position.set(0, 0.05, zSouth + 4.5);
  group.add(moat);
  const plankGeo = box(1.0, 0.15, 6.2);
  for (let i = -2; i <= 2; i++) {
    const plank = new THREE.Mesh(plankGeo, woodMat);
    plank.position.set(i * 1.05, 0.18, zSouth + 4.5);
    group.add(plank);
  }

  // ------------------------------------------------------------------
  // (4) Central KEEP — dominant looming tower (offset east to keep lane clear)
  // ------------------------------------------------------------------
  const keepCX = 14, keepCZ = -118;
  const keepW = 18, keepD = 16, keepH = 22;
  const keepMinX = keepCX - keepW / 2, keepMaxX = keepCX + keepW / 2;
  const keepMinZ = keepCZ - keepD / 2, keepMaxZ = keepCZ + keepD / 2;
  addBox(stoneMat, keepCX, keepH / 2, keepCZ, keepW, keepH, keepD);
  // battlement merlons around the keep top
  crenelX(keepMinX, keepMaxX, keepMinZ, keepH, T);
  crenelX(keepMinX, keepMaxX, keepMaxZ, keepH, T);
  crenelZ(keepMinZ, keepMaxZ, keepMinX, keepH, T);
  crenelZ(keepMinZ, keepMaxZ, keepMaxX, keepH, T);
  // upper machicolation ledge
  addBox(stoneDark, keepCX, keepH - 0.6, keepCZ, keepW + 0.8, 0.6, keepD + 0.8);
  // big door on south face (facing the gate)
  addBox(woodMat, keepCX, 2.2, keepMaxZ + 0.05, 3.2, 4.4, 0.3);
  // keep windows — warm amber + cold blue, several rows
  const winGeo = new THREE.PlaneGeometry(1.1, 1.8);
  const winRows = [6, 11, 16];
  for (let r0 = 0; r0 < winRows.length; r0++) {
    for (let c = -1; c <= 1; c++) {
      const wm = ((r0 + c) % 3 === 0) ? winCold : winWarm;
      const w0 = new THREE.Mesh(winGeo, wm);
      w0.position.set(keepCX + c * 5, winRows[r0], keepMaxZ + 0.06);
      group.add(w0);
      // east face windows too
      const w1 = new THREE.Mesh(winGeo, ((r0 + c) % 2 === 0) ? winWarm : winCold);
      w1.position.set(keepMaxX + 0.06, winRows[r0], keepCZ + c * 4);
      w1.rotation.y = Math.PI / 2;
      group.add(w1);
    }
  }
  // keep roof cap + big flag
  addBox(roofMat, keepCX, keepH + 0.4, keepCZ, keepW - 2, 0.8, keepD - 2);
  addFlag(keepCX, keepH + 0.8, keepCZ, 4.0);
  addTorch(keepCX - keepW / 2 + 0.5, keepH - 2, keepMaxZ + 0.3);
  addTorch(keepCX + keepW / 2 - 0.5, keepH - 2, keepMaxZ + 0.3);
  addCollider(keepMinX, keepMaxX, keepMinZ, keepMaxZ);

  // ------------------------------------------------------------------
  // (5) BRAZIERS/TORCHES along the battlements
  // ------------------------------------------------------------------
  // along south wall tops
  addTorch(-20, H + 0.6, zSouth - 0.8);
  addTorch(20, H + 0.6, zSouth - 0.8);
  // along north wall tops
  addTorch(-20, H + 0.6, zNorth + 0.8);
  addTorch(20, H + 0.6, zNorth + 0.8);
  // along east & west wall tops
  addTorch(xEast - 0.8, H + 0.6, -95);
  addTorch(xEast - 0.8, H + 0.6, -120);
  addTorch(xWest + 0.8, H + 0.6, -95);
  addTorch(xWest + 0.8, H + 0.6, -120);

  // ------------------------------------------------------------------
  // update(): sway flags/banners, pulse flames
  // ------------------------------------------------------------------
  function update(dt, elapsed) {
    for (let i = 0; i < swayers.length; i++) {
      const s = swayers[i];
      const a = Math.sin(elapsed * s.speed + s.phase) * s.amp;
      if (s.axisX) s.obj.rotation.x = a; else s.obj.rotation.z = a;
    }
    for (let i = 0; i < flames.length; i++) {
      const f = flames[i];
      const sc = 1 + Math.sin(elapsed * f.speed + f.phase) * 0.18;
      f.mesh.scale.set(1, sc, 1);
    }
  }

  return { group, colliders, flickers, update };
}
