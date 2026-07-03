import * as THREE from 'three';

export function build(ctx) {
  const { rng } = ctx;
  const group = new THREE.Group();
  const colliders = [];
  const flickers = [];

  const smokes = [];   // { geo, arr, base, count, rise, spread }
  const cloths = [];   // { mesh, phase, amp }

  // ---------------------------------------------------------------
  // Shared materials (reuse across many copies for perf)
  // ---------------------------------------------------------------
  const plasterMats = [
    new THREE.MeshStandardMaterial({ color: 0x6b6258, roughness: 0.98, metalness: 0.0 }),
    new THREE.MeshStandardMaterial({ color: 0x635b52, roughness: 0.98, metalness: 0.0 }),
    new THREE.MeshStandardMaterial({ color: 0x5c564d, roughness: 1.0, metalness: 0.0 }),
    new THREE.MeshStandardMaterial({ color: 0x726a5e, roughness: 0.96, metalness: 0.0 }),
  ];
  const timberMat = new THREE.MeshStandardMaterial({ color: 0x2c2119, roughness: 0.95, metalness: 0.0 });
  const darkWoodMat = new THREE.MeshStandardMaterial({ color: 0x241a12, roughness: 0.95, metalness: 0.0 });
  const barkMat = new THREE.MeshStandardMaterial({ color: 0x1c150f, roughness: 1.0, metalness: 0.0 });
  const thatchMat = new THREE.MeshStandardMaterial({ color: 0x3a3226, roughness: 1.0, metalness: 0.0 });
  const slateMat = new THREE.MeshStandardMaterial({ color: 0x3a3a42, roughness: 0.9, metalness: 0.05 });
  const stoneMat = new THREE.MeshStandardMaterial({ color: 0x3a3a42, roughness: 0.95, metalness: 0.0 });
  const stoneDarkMat = new THREE.MeshStandardMaterial({ color: 0x2f2f36, roughness: 0.98, metalness: 0.0 });
  const boardMat = new THREE.MeshStandardMaterial({ color: 0x2a2018, roughness: 0.96, metalness: 0.0 });
  const clothMat = new THREE.MeshStandardMaterial({ color: 0x5b5348, roughness: 1.0, metalness: 0.0, side: THREE.DoubleSide });
  const windowMat = new THREE.MeshStandardMaterial({
    color: 0x140a02, emissive: 0xffb14a, emissiveIntensity: 2.5, roughness: 0.6, metalness: 0.0,
  });
  const windowDimMat = new THREE.MeshStandardMaterial({
    color: 0x0e0803, emissive: 0xd07a2a, emissiveIntensity: 1.6, roughness: 0.6, metalness: 0.0,
  });
  const flameMat = new THREE.MeshStandardMaterial({
    color: 0x2a1400, emissive: 0xffa838, emissiveIntensity: 3.0, roughness: 0.5, metalness: 0.0,
  });
  const candleMat = new THREE.MeshStandardMaterial({
    color: 0x20140a, emissive: 0xffcf7a, emissiveIntensity: 3.0, roughness: 0.5, metalness: 0.0,
  });
  const metalMat = new THREE.MeshStandardMaterial({ color: 0x1a1a1e, roughness: 0.7, metalness: 0.4 });

  // Shared small geometries
  const flameGeo = new THREE.IcosahedronGeometry(0.13, 0);
  const beamXGeo = new THREE.BoxGeometry(1, 1, 1); // scaled per-use
  const boxGeo = new THREE.BoxGeometry(1, 1, 1);   // generic unit box, scaled per-use

  function unitBox(mat, sx, sy, sz, x, y, z, ry) {
    const m = new THREE.Mesh(boxGeo, mat);
    m.scale.set(sx, sy, sz);
    m.position.set(x, y, z);
    if (ry) m.rotation.y = ry;
    return m;
  }

  function addCollider(minX, maxX, minZ, maxZ) {
    // Safety: never intrude on the central lane corridor.
    if (maxX > -4.5 && minX < 4.5) return; // would touch lane -> skip
    colliders.push({ minX, maxX, minZ, maxZ });
  }

  // ---------------------------------------------------------------
  // TORCH / LANTERN helper -> emissive flame + warm PointLight in flickers
  // Parented so caller can attach to a subgroup and rotate.
  // ---------------------------------------------------------------
  function makeLantern(parent, x, y, z, base = 1.6, color = 0xffb24d) {
    const g = new THREE.Group();
    g.position.set(x, y, z);
    // little iron bracket / cage post
    g.add(unitBox(metalMat, 0.05, 0.34, 0.05, 0, 0.17, 0));
    // lantern housing (thin dark frame)
    const housing = unitBox(darkWoodMat, 0.16, 0.22, 0.16, 0, -0.02, 0);
    g.add(housing);
    // flame
    const flame = new THREE.Mesh(flameGeo, flameMat);
    flame.scale.set(0.7, 1.2, 0.7);
    flame.position.set(0, -0.02, 0);
    g.add(flame);
    // light
    const light = new THREE.PointLight(color, base, 17, 2);
    light.position.set(0, -0.02, 0);
    g.add(light);
    parent.add(g);
    flickers.push({ light, base, amp: 0.35, speed: 7 + rng() * 6, phase: rng() * 6.28 });
    return g;
  }

  // ---------------------------------------------------------------
  // SMOKE wisp above a chimney -> additive points, animated rising
  // ---------------------------------------------------------------
  function makeSmoke(x, y, z) {
    const count = 14;
    const arr = new Float32Array(count * 3);
    const base = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      const bx = (rng() - 0.5) * 0.3;
      const bz = (rng() - 0.5) * 0.3;
      const by = rng() * 3.2;
      base[i * 3] = bx; base[i * 3 + 1] = by; base[i * 3 + 2] = bz;
      arr[i * 3] = x + bx; arr[i * 3 + 1] = y + by; arr[i * 3 + 2] = z + bz;
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(arr, 3));
    const mat = new THREE.PointsMaterial({
      size: 0.9, color: 0x2a2a30, transparent: true, opacity: 0.28,
      blending: THREE.NormalBlending, depthWrite: false,
    });
    const pts = new THREE.Points(geo, mat);
    pts.frustumCulled = false;
    group.add(pts);
    smokes.push({ geo, arr, base, count, x, y, z, rise: 0.35 + rng() * 0.25 });
  }

  // ---------------------------------------------------------------
  // COTTAGE builder
  //   Built in local space with the GABLE FRONT facing +X (door/windows/lantern).
  //   depth D along local X, frontage W along local Z, wall height H.
  //   Placed then rotated so the front faces the lane (toward x=0).
  // ---------------------------------------------------------------
  function addCottage(cx, cz, opts) {
    const o = opts || {};
    const D = o.D || (5.0 + rng() * 2.2);   // depth into lot
    const W = o.W || (4.4 + rng() * 2.0);   // frontage width
    const twoStory = o.twoStory || false;
    const storyH = 2.4;
    const H = twoStory ? storyH * 2 : (2.4 + rng() * 0.5);
    const rh = 1.8 + rng() * 1.2;           // roof ridge rise
    const state = o.state || 'ok';          // ok | leaning | collapsed | boarded
    const plaster = plasterMats[(Math.floor(rng() * plasterMats.length)) % plasterMats.length];
    const roofMat = rng() < 0.5 ? thatchMat : slateMat;
    const faceLane = cx > 0 ? Math.PI : 0;  // right side rotates to face -X

    const cg = new THREE.Group();
    cg.position.set(cx, 0, cz);
    cg.rotation.y = faceLane;

    if (state === 'leaning') cg.rotation.z = (rng() < 0.5 ? 1 : -1) * (0.03 + rng() * 0.05);

    const bodyH = state === 'collapsed' ? H * (0.35 + rng() * 0.25) : H;

    // --- BODY ---
    const body = unitBox(plaster, D, bodyH, W, 0, bodyH / 2, 0);
    cg.add(body);

    // stone foundation course
    cg.add(unitBox(stoneMat, D + 0.12, 0.4, W + 0.12, 0, 0.2, 0));

    // --- HALF-TIMBER BEAMS on the front (+X) face ---
    const fx = D / 2 + 0.03;
    const bt = 0.14; // beam thickness (protrudes in X)
    const beamMat = timberMat;
    // corner posts + mid post(s)
    const posts = Math.max(2, Math.round(W / 1.8));
    for (let i = 0; i <= posts; i++) {
      const pz = -W / 2 + (i / posts) * W;
      cg.add(unitBox(beamMat, bt, bodyH, 0.16, fx, bodyH / 2, pz));
    }
    // sill + top rail + (mid rail if two story)
    cg.add(unitBox(beamMat, bt, 0.18, W, fx, 0.5, 0));
    cg.add(unitBox(beamMat, bt, 0.18, W, fx, bodyH - 0.1, 0));
    if (twoStory) cg.add(unitBox(beamMat, bt, 0.18, W, fx, storyH, 0));
    // diagonal braces in a couple of panels
    for (let i = 0; i < posts; i++) {
      if (rng() < 0.55) {
        const z0 = -W / 2 + (i / posts) * W;
        const brace = unitBox(beamMat, bt, Math.hypot(W / posts, storyH * 0.8), 0.13,
          fx, (twoStory ? storyH : bodyH) * 0.5, z0 + (W / posts) * 0.5);
        brace.rotation.x = (rng() < 0.5 ? 1 : -1) * 0.7;
        cg.add(brace);
      }
    }
    // side-face timber (a few verticals) on +Z face
    for (let i = 0; i <= 2; i++) {
      cg.add(unitBox(beamMat, 0.16, bodyH, bt, -D / 2 + (i / 2) * D, bodyH / 2, W / 2 + 0.03));
    }

    // --- DOOR (front) ---
    const doorW = 1.0, doorH = 1.9;
    const doorZ = (rng() - 0.5) * (W - doorW - 0.6);
    cg.add(unitBox(darkWoodMat, 0.1, doorH, doorW, fx + 0.02, doorH / 2, doorZ));
    // door frame
    cg.add(unitBox(beamMat, bt, doorH + 0.2, 0.12, fx + 0.01, doorH / 2, doorZ - doorW / 2 - 0.06));
    cg.add(unitBox(beamMat, bt, doorH + 0.2, 0.12, fx + 0.01, doorH / 2, doorZ + doorW / 2 + 0.06));
    cg.add(unitBox(beamMat, bt, 0.14, doorW + 0.24, fx + 0.01, doorH + 0.07, doorZ));

    // --- WINDOWS (front + one side), warm amber, recessed ---
    const winCount = 2 + Math.floor(rng() * 3);
    function addWindow(px, py, pz, ry, dim) {
      // recess pocket
      const pocket = unitBox(stoneDarkMat, 0.06, 0.72, 0.62, 0, 0, 0);
      // frame
      const frame = new THREE.Group();
      frame.position.set(px, py, pz);
      frame.rotation.y = ry;
      frame.add(unitBox(darkWoodMat, 0.06, 0.78, 0.68, -0.02, 0, 0));
      const pane = unitBox(dim ? windowDimMat : windowMat, 0.04, 0.62, 0.52, 0.01, 0, 0);
      frame.add(pane);
      // muntins (cross bars)
      frame.add(unitBox(darkWoodMat, 0.05, 0.66, 0.06, 0.02, 0, 0));
      frame.add(unitBox(darkWoodMat, 0.05, 0.06, 0.56, 0.02, 0, 0));
      cg.add(frame);
    }
    const boarded = state === 'boarded';
    for (let i = 0; i < winCount; i++) {
      const isBoarded = boarded && rng() < 0.7;
      const wy = (twoStory && rng() < 0.5) ? storyH + 0.9 : 1.35;
      const wz = (rng() - 0.5) * (W - 1.2);
      if (Math.abs(wz - doorZ) < 0.9 && wy < 1.8) continue; // avoid door
      if (isBoarded) {
        // boarded-up: crossed planks, no glow
        for (let b = 0; b < 3; b++) {
          const plank = unitBox(boardMat, 0.05, 0.16, 0.8, fx + 0.03, wy - 0.24 + b * 0.24, wz);
          plank.rotation.x = (b % 2 ? 0.06 : -0.06);
          cg.add(plank);
        }
      } else {
        addWindow(fx + 0.02, wy, wz, 0, rng() < 0.35);
      }
    }
    // one side window on +Z face
    if (rng() < 0.7 && !boarded) addWindow(0, 1.4, W / 2 + 0.02, Math.PI / 2, rng() < 0.4);

    // --- ROOF (gabled, ridge along X, gable faces +X) ---
    if (state !== 'collapsed') {
      const eaveZ = 0.45, eaveX = 0.4;
      const halfW = W / 2 + eaveZ;
      const slope = Math.hypot(halfW, rh);
      const ang = Math.atan2(rh, halfW);
      for (const s of [-1, 1]) {
        const rp = unitBox(roofMat, D + eaveX * 2, 0.16, slope, 0, bodyH + rh / 2, s * halfW / 2);
        rp.rotation.x = -s * ang;
        cg.add(rp);
      }
      // gable triangle fills (front +X and back -X)
      const tri = new THREE.Shape();
      tri.moveTo(-W / 2, 0); tri.lineTo(W / 2, 0); tri.lineTo(0, rh); tri.closePath();
      const triGeo = new THREE.ShapeGeometry(tri);
      for (const s of [1, -1]) {
        const gm = new THREE.Mesh(triGeo, plaster);
        gm.position.set(s * D / 2, bodyH, 0);
        gm.rotation.y = s * Math.PI / 2;
        cg.add(gm);
      }
      // ridge beam
      cg.add(unitBox(darkWoodMat, D + eaveX * 2, 0.14, 0.14, 0, bodyH + rh, 0));
    } else {
      // collapsed: a few broken rafters leaning
      for (let i = 0; i < 5; i++) {
        const r = unitBox(barkMat, 0.12, 0.12, 2.2 + rng(), (rng() - 0.5) * D, bodyH + rng() * 0.4, (rng() - 0.5) * W);
        r.rotation.set(rng() * 0.8, rng() * Math.PI, rng() * 0.8);
        cg.add(r);
      }
    }

    // --- CHIMNEY + smoke ---
    if (state !== 'collapsed' && rng() < 0.85) {
      const chX = (rng() - 0.5) * (D - 1.0);
      const chZ = (rng() < 0.5 ? 1 : -1) * (W / 2 - 0.3);
      const chTop = bodyH + rh + 0.6 + rng() * 0.4;
      cg.add(unitBox(stoneMat, 0.5, chTop, 0.5, chX, chTop / 2, chZ));
      cg.add(unitBox(stoneDarkMat, 0.6, 0.12, 0.6, chX, chTop, chZ));
      // smoke placed in WORLD space (compute from local via rotation about Y)
      const sgn = faceLane === Math.PI ? -1 : 1;
      const wx = cx + sgn * chX;
      const wz = cz + sgn * chZ;
      if (rng() < 0.85) makeSmoke(wx, chTop, wz);
    }

    // --- DOOR LANTERN (parented so rotation carries it lane-side) ---
    const lanZ = doorZ + doorW / 2 + 0.35;
    makeLantern(cg, fx + 0.25, doorH + 0.05, lanZ, 1.5 + rng() * 0.3);

    group.add(cg);

    // --- COLLIDER (footprint, generous margin; symmetric under 180° rot) ---
    const mX = D / 2 + 0.25, mZ = W / 2 + 0.25;
    addCollider(cx - mX, cx + mX, cz - mZ, cz + mZ);
  }

  // ---------------------------------------------------------------
  // Cottage placements (both sides of lane, front toward lane)
  // ---------------------------------------------------------------
  const cottageSpecs = [
    { x: 15, z: 40, twoStory: true, state: 'ok' },
    { x: 18, z: 22, state: 'boarded' },
    { x: 14.5, z: 4, twoStory: true, state: 'leaning' },
    { x: 20, z: -14, state: 'ok' },
    { x: 23, z: -31, state: 'collapsed' },
    { x: -15, z: 44, state: 'ok' },
    { x: -18, z: 26, twoStory: true, state: 'ok' },
    { x: -14.5, z: 7, state: 'boarded' },
    { x: -20, z: -11, state: 'leaning' },
    { x: -16.5, z: -29, twoStory: true, state: 'ok' },
  ];
  for (const s of cottageSpecs) {
    addCottage(s.x, s.z, { twoStory: s.twoStory, state: s.state });
  }

  // A couple of deeper back-row hovels for depth
  addCottage(34, 12, { D: 4.5, W: 4, state: 'boarded' });
  addCottage(-33, -2, { D: 4.5, W: 4, state: 'collapsed' });

  // ---------------------------------------------------------------
  // (3) CENTRAL STONE WELL near (12, 0, 22)
  // ---------------------------------------------------------------
  function buildWell(wx, wz) {
    const wg = new THREE.Group();
    wg.position.set(wx, 0, wz);
    // stone ring (outer cylinder + darker inner to read as a hole)
    const ring = new THREE.Mesh(new THREE.CylinderGeometry(0.95, 1.0, 1.0, 20, 1, false), stoneMat);
    ring.position.y = 0.5; wg.add(ring);
    const lip = new THREE.Mesh(new THREE.TorusGeometry(0.95, 0.12, 8, 20), stoneDarkMat);
    lip.rotation.x = Math.PI / 2; lip.position.y = 1.0; wg.add(lip);
    const hole = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, 0.9, 18), stoneDarkMat);
    hole.position.y = 0.62; wg.add(hole);
    // two posts + roof
    for (const s of [-1, 1]) {
      wg.add(unitBox(barkMat, 0.16, 2.2, 0.16, s * 0.85, 1.1, 0));
    }
    // little gabled roof
    const rr = 0.9;
    for (const s of [-1, 1]) {
      const rp = unitBox(thatchMat, 0.16, 1.3, 2.3, s * 0.55, 2.55, 0);
      rp.rotation.z = -s * 0.7;
      wg.add(rp);
    }
    wg.add(unitBox(darkWoodMat, 0.14, 0.14, 2.4, 0, 2.9, 0));
    // crossbar + hanging bucket
    wg.add(unitBox(darkWoodMat, 0.1, 0.1, 1.9, 0, 2.0, 0));
    const rope = unitBox(metalMat, 0.03, 0.9, 0.03, 0.2, 1.55, 0);
    wg.add(rope);
    const bucket = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.14, 0.28, 10), darkWoodMat);
    bucket.position.set(0.2, 1.0, 0); wg.add(bucket);
    group.add(wg);
    addCollider(wx - 1.3, wx + 1.3, wz - 1.3, wz + 1.3);
  }
  buildWell(12, 22);

  // ---------------------------------------------------------------
  // (5) GRIM CENTREPIECE — a wooden GALLOWS near the square
  // ---------------------------------------------------------------
  function buildGallows(gx, gz) {
    const gg = new THREE.Group();
    gg.position.set(gx, 0, gz);
    // platform
    gg.add(unitBox(boardMat, 2.6, 0.5, 2.6, 0, 0.25, 0));
    // steps
    gg.add(unitBox(boardMat, 0.9, 0.25, 1.0, -1.6, 0.12, 0));
    // two uprights + top beam
    for (const s of [-1, 1]) gg.add(unitBox(barkMat, 0.22, 3.4, 0.22, s * 0.9, 1.7 + 0.5, 0));
    gg.add(unitBox(darkWoodMat, 2.3, 0.22, 0.22, 0, 3.9, 0));
    // diagonal brace
    const br = unitBox(darkWoodMat, 0.16, 1.2, 0.16, 0.55, 3.4, 0);
    br.rotation.z = 0.6; gg.add(br);
    // the noose (rope loop)
    gg.add(unitBox(metalMat, 0.04, 1.1, 0.04, 0.3, 3.25, 0));
    const loop = new THREE.Mesh(new THREE.TorusGeometry(0.13, 0.03, 6, 12), metalMat);
    loop.position.set(0.3, 2.6, 0); gg.add(loop);
    group.add(gg);
    addCollider(gx - 1.5, gx + 1.5, gz - 1.5, gz + 1.5);
  }
  buildGallows(-9, 18);

  // small candle shrine at the square base (extra grim detail, glowing candles)
  function buildShrine(sx, sz) {
    const sg = new THREE.Group();
    sg.position.set(sx, 0, sz);
    sg.add(unitBox(stoneMat, 1.0, 0.6, 0.5, 0, 0.3, 0));
    sg.add(unitBox(stoneDarkMat, 0.7, 0.9, 0.3, 0, 1.0, 0)); // headstone-ish slab
    for (let i = 0; i < 4; i++) {
      const cxp = -0.35 + (i / 3) * 0.7;
      sg.add(unitBox(clothMat, 0.05, 0.14, 0.05, cxp, 0.67, 0.2));
      const fl = new THREE.Mesh(flameGeo, candleMat);
      fl.scale.set(0.35, 0.6, 0.35);
      fl.position.set(cxp, 0.8, 0.2);
      sg.add(fl);
    }
    group.add(sg);
    // one shared soft light for the shrine cluster
    const light = new THREE.PointLight(0xffcf7a, 1.0, 12, 2);
    light.position.set(sx, 1.0, sz + 0.2);
    group.add(light);
    flickers.push({ light, base: 1.0, amp: 0.4, speed: 8 + rng() * 5, phase: rng() * 6.28 });
    addCollider(sx - 0.7, sx + 0.7, sz - 0.5, sz + 0.5);
  }
  buildShrine(8, 30);

  // ---------------------------------------------------------------
  // (4) CLUTTER — stalls, notice board, fences, barrels, crates,
  //     hay bales, hand cart, trough, laundry lines
  // ---------------------------------------------------------------

  // shared clutter geos
  const barrelGeo = new THREE.CylinderGeometry(0.35, 0.32, 0.85, 12);
  const crateGeo = new THREE.BoxGeometry(0.8, 0.8, 0.8);
  const hayGeo = new THREE.CylinderGeometry(0.55, 0.55, 1.0, 12);

  function addBarrel(x, z, r) {
    const m = new THREE.Mesh(barrelGeo, darkWoodMat);
    m.position.set(x, 0.42, z); m.rotation.y = r || 0;
    group.add(m);
    // hoop rings
    for (const yy of [0.62, 0.22]) {
      const hoop = new THREE.Mesh(new THREE.TorusGeometry(0.35, 0.02, 6, 12), metalMat);
      hoop.rotation.x = Math.PI / 2; hoop.position.set(x, yy, z); group.add(hoop);
    }
  }
  function addCrate(x, y, z, s, r) {
    const m = new THREE.Mesh(crateGeo, boardMat);
    m.scale.setScalar(s || 1); m.position.set(x, y, z); m.rotation.y = r || 0;
    group.add(m);
    return m;
  }
  function addHay(x, z, r) {
    const m = new THREE.Mesh(hayGeo, thatchMat);
    m.rotation.z = Math.PI / 2; m.rotation.y = r || 0;
    m.position.set(x, 0.55, z);
    group.add(m);
  }

  // Barrel & crate clusters (a few placements, each with a collider on the group)
  const clusterSpots = [
    [10, 36], [-11, 34], [13, -6], [-12, 40], [17, 10], [-17, -20],
  ];
  for (const [x, z] of clusterSpots) {
    addBarrel(x, z, rng() * 6.28);
    addBarrel(x + 0.75, z + 0.2, rng() * 6.28);
    addCrate(x + 0.2, 0.4, z - 0.85, 0.9, rng() * 0.5);
    addCrate(x + 0.3, 1.15, z - 0.9, 0.7, rng() * 0.5);
    if (rng() < 0.6) addHay(x - 0.9, z + 0.4, rng());
    addCollider(x - 0.9, x + 1.2, z - 1.3, z + 0.7);
  }

  // Standalone hay bales
  addHay(21, 30, 0.3); addHay(-24, 12, 1.1); addHay(-22, -34, 0.7);

  // Market stalls with tattered awnings
  function buildStall(x, z, ry) {
    const sg = new THREE.Group();
    sg.position.set(x, 0, z); sg.rotation.y = ry || 0;
    // 4 posts
    for (const [px, pz] of [[-1, -0.7], [1, -0.7], [-1, 0.7], [1, 0.7]]) {
      sg.add(unitBox(barkMat, 0.1, 2.0, 0.1, px, 1.0, pz));
    }
    // counter
    sg.add(unitBox(boardMat, 2.2, 0.14, 0.5, 0, 1.0, 0.55));
    // tattered awning (sagging plane)
    const aw = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 1.8, 4, 3), clothMat.clone());
    aw.material.color = new THREE.Color(0x5a2f2a);
    aw.rotation.x = -Math.PI / 2 + 0.35;
    aw.position.set(0, 2.05, -0.1);
    // ripple the awning verts for a sagging tattered look
    const pa = aw.geometry.attributes.position;
    for (let i = 0; i < pa.count; i++) {
      pa.setZ(i, pa.getZ(i) + (rng() - 0.5) * 0.25);
    }
    pa.needsUpdate = true;
    sg.add(aw);
    // some goods on counter
    addBarrelToGroup(sg, -0.6, 0.5);
    sg.add(unitBox(boardMat, 0.4, 0.3, 0.4, 0.5, 1.3, 0.5));
    group.add(sg);
    addCollider(x - 1.3, x + 1.3, z - 1.0, z + 0.9);
  }
  function addBarrelToGroup(g, x, z) {
    const m = new THREE.Mesh(barrelGeo, darkWoodMat);
    m.scale.setScalar(0.6); m.position.set(x, 1.25, z); g.add(m);
  }
  buildStall(7, 44, -0.2);
  buildStall(-8, 38, 0.25);
  buildStall(9, 12, Math.PI);

  // Notice / wanted board
  function buildNoticeBoard(x, z, ry) {
    const bg = new THREE.Group();
    bg.position.set(x, 0, z); bg.rotation.y = ry || 0;
    bg.add(unitBox(barkMat, 0.12, 2.0, 0.12, -0.7, 1.0, 0));
    bg.add(unitBox(barkMat, 0.12, 2.0, 0.12, 0.7, 1.0, 0));
    bg.add(unitBox(boardMat, 1.7, 1.2, 0.1, 0, 1.5, 0));
    // little tacked papers (pale planes)
    const paperMat = new THREE.MeshStandardMaterial({ color: 0x8a8272, roughness: 1.0 });
    for (let i = 0; i < 4; i++) {
      const p = unitBox(paperMat, 0.32, 0.42, 0.02, -0.55 + (i % 3) * 0.45, 1.55 - (i > 2 ? 0.45 : 0), 0.06);
      p.rotation.z = (rng() - 0.5) * 0.2;
      bg.add(p);
    }
    group.add(bg);
    addCollider(x - 0.9, x + 0.9, z - 0.2, z + 0.2);
  }
  buildNoticeBoard(6, 26, -0.3);

  // Fences & broken gates between houses (posts + rails). Collider per segment.
  function buildFence(x0, z0, x1, z1, broken) {
    const dx = x1 - x0, dz = z1 - z0;
    const len = Math.hypot(dx, dz);
    const segs = Math.max(1, Math.round(len / 1.2));
    const ang = Math.atan2(dz, dx);
    const fg = new THREE.Group();
    for (let i = 0; i <= segs; i++) {
      const t = i / segs;
      const px = x0 + dx * t, pz = z0 + dz * t;
      // some posts missing when broken
      if (broken && rng() < 0.3) continue;
      const post = unitBox(barkMat, 0.12, 1.1 + rng() * 0.2, 0.12, px, 0.55, pz);
      if (broken) post.rotation.z = (rng() - 0.5) * 0.4;
      fg.add(post);
    }
    // two rails
    for (const ry of [0.4, 0.85]) {
      if (broken && rng() < 0.4) continue;
      const rail = unitBox(darkWoodMat, len, 0.1, 0.08, (x0 + x1) / 2, ry, (z0 + z1) / 2);
      rail.rotation.y = -ang;
      fg.add(rail);
    }
    group.add(fg);
    // thin collider along the fence (skip if it would touch the lane)
    const minX = Math.min(x0, x1) - 0.2, maxX = Math.max(x0, x1) + 0.2;
    const minZ = Math.min(z0, z1) - 0.2, maxZ = Math.max(z0, z1) + 0.2;
    addCollider(minX, maxX, minZ, maxZ);
  }
  // fences link some cottages on each side (all outside the lane)
  buildFence(11.5, 33, 11.5, 15, false);
  buildFence(12, -4, 12, -20, true);
  buildFence(-11.5, 36, -11.5, 17, true);
  buildFence(-12, -2, -12, -18, false);
  buildFence(22, 46, 12, 44, false); // a run near the village edge

  // Hand cart with a broken wheel
  function buildCart(x, z, ry) {
    const cgrp = new THREE.Group();
    cgrp.position.set(x, 0, z); cgrp.rotation.y = ry || 0;
    // bed
    cgrp.add(unitBox(boardMat, 2.0, 0.2, 1.1, 0, 0.7, 0));
    // side rails
    cgrp.add(unitBox(darkWoodMat, 2.0, 0.35, 0.08, 0, 0.95, 0.5));
    cgrp.add(unitBox(darkWoodMat, 2.0, 0.35, 0.08, 0, 0.95, -0.5));
    // handles
    cgrp.add(unitBox(barkMat, 1.2, 0.08, 0.08, 1.4, 0.7, 0.4));
    cgrp.add(unitBox(barkMat, 1.2, 0.08, 0.08, 1.4, 0.7, -0.4));
    // one good wheel
    const wheel = new THREE.Mesh(new THREE.TorusGeometry(0.5, 0.09, 8, 16), barkMat);
    wheel.position.set(-0.5, 0.5, 0.62); cgrp.add(wheel);
    // spokes
    for (let i = 0; i < 4; i++) {
      const sp = unitBox(barkMat, 0.9, 0.06, 0.06, -0.5, 0.5, 0.62);
      sp.rotation.x = i * Math.PI / 4;
      // rotate spoke in wheel plane (around Z at wheel center)
      const spg = new THREE.Group(); spg.position.set(-0.5, 0.5, 0.62);
      const spm = unitBox(barkMat, 0.9, 0.06, 0.06, 0, 0, 0);
      spm.rotation.z = i * Math.PI / 4; spg.add(spm); cgrp.add(spg);
    }
    // broken wheel (tilted, partly sunk)
    const bw = new THREE.Mesh(new THREE.TorusGeometry(0.5, 0.09, 8, 16), barkMat);
    bw.position.set(-0.5, 0.28, -0.72); bw.rotation.set(0.4, 0, 0.5); cgrp.add(bw);
    // tilt cart because of broken wheel
    cgrp.rotation.z = 0.12;
    group.add(cgrp);
    addCollider(x - 1.3, x + 1.3, z - 0.9, z + 0.9);
  }
  buildCart(-10, 8, 0.4);

  // Water trough
  function buildTrough(x, z, ry) {
    const tg = new THREE.Group();
    tg.position.set(x, 0, z); tg.rotation.y = ry || 0;
    tg.add(unitBox(darkWoodMat, 2.2, 0.6, 0.8, 0, 0.3, 0));
    // hollow water (dark emissive-less plane just below rim)
    const water = new THREE.Mesh(new THREE.PlaneGeometry(2.0, 0.6),
      new THREE.MeshStandardMaterial({ color: 0x0c1216, roughness: 0.3, metalness: 0.2 }));
    water.rotation.x = -Math.PI / 2; water.position.set(0, 0.52, 0);
    tg.add(water);
    group.add(tg);
    addCollider(x - 1.3, x + 1.3, z - 0.55, z + 0.55);
  }
  buildTrough(14, 34, 0);

  // Tattered laundry lines (cloth planes that sway in update)
  function buildLaundry(x0, z0, x1, z1, y) {
    const dx = x1 - x0, dz = z1 - z0;
    const len = Math.hypot(dx, dz);
    const ang = Math.atan2(dz, dx);
    // line
    const line = unitBox(metalMat, len, 0.02, 0.02, (x0 + x1) / 2, y, (z0 + z1) / 2);
    line.rotation.y = -ang; group.add(line);
    // hang a few cloths
    const n = Math.max(2, Math.round(len / 1.4));
    for (let i = 1; i < n; i++) {
      const t = i / n;
      const px = x0 + dx * t, pz = z0 + dz * t;
      const w = 0.5 + rng() * 0.3, h = 0.7 + rng() * 0.4;
      const cm = new THREE.Mesh(new THREE.PlaneGeometry(w, h), clothMat.clone());
      cm.material.color = new THREE.Color([0x4a4238, 0x3a3f45, 0x55483f][Math.floor(rng() * 3)]);
      cm.rotation.y = -ang + Math.PI / 2;
      cm.position.set(px, y - h / 2 - 0.03, pz);
      group.add(cm);
      cloths.push({ mesh: cm, phase: rng() * 6.28, amp: 0.06 + rng() * 0.06, baseY: y - h / 2 - 0.03 });
    }
  }
  buildLaundry(-14, 47, -19, 45, 2.2);
  buildLaundry(16, 6, 16, 2, 2.1);
  buildLaundry(19, -12, 21, -16, 2.0);

  // Scattered loose barrels/crates for texture (no colliders needed for tiny ones)
  addBarrel(-9, 44, 0.5);
  addCrate(24, 0.35, -30, 0.8, 0.3);
  addCrate(-25, 0.35, 14, 0.9, 1.1);
  addBarrel(26, 8, 0.2);

  // ---------------------------------------------------------------
  // UPDATE — animate smoke wisps & laundry sway
  // ---------------------------------------------------------------
  function update(dt, elapsed) {
    // smoke rising & wrapping
    for (const sm of smokes) {
      for (let i = 0; i < sm.count; i++) {
        let by = sm.base[i * 3 + 1] + (elapsed * sm.rise + i * 0.21) % 3.4;
        if (by > 3.4) by -= 3.4;
        const drift = Math.sin(elapsed * 0.6 + i) * (0.12 + by * 0.12);
        sm.arr[i * 3] = sm.x + sm.base[i * 3] + drift;
        sm.arr[i * 3 + 1] = sm.y + by;
        sm.arr[i * 3 + 2] = sm.z + sm.base[i * 3 + 2] + Math.cos(elapsed * 0.5 + i) * 0.1;
      }
      sm.geo.attributes.position.needsUpdate = true;
    }
    // laundry gentle sway
    for (const c of cloths) {
      c.mesh.rotation.z = Math.sin(elapsed * 1.3 + c.phase) * c.amp;
    }
  }

  return { group, colliders, flickers, update };
}
