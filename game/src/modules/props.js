import * as THREE from 'three';

export function build(ctx) {
  const { rng } = ctx;
  const group = new THREE.Group();
  const colliders = [];
  const flickers = [];

  const rand = (a, b) => a + rng() * (b - a);
  const TAU = Math.PI * 2;

  // ===========================================================================
  // Shared materials / geometries (reused for perf)
  // ===========================================================================
  const darkWoodMat = new THREE.MeshStandardMaterial({
    color: 0x2c2119, roughness: 0.95, metalness: 0.05,
  });
  const barkMat = new THREE.MeshStandardMaterial({
    color: 0x1c150f, roughness: 1.0, metalness: 0.0,
  });
  const ironMat = new THREE.MeshStandardMaterial({
    color: 0x14140f, roughness: 0.7, metalness: 0.55,
  });
  const flameMat = new THREE.MeshStandardMaterial({
    color: 0xff8a2a, emissive: 0xff7a1a, emissiveIntensity: 3.0,
    roughness: 0.6, metalness: 0.0,
  });
  const emberCoreMat = new THREE.MeshStandardMaterial({
    color: 0x9fe8d0, emissive: 0x5fd0b0, emissiveIntensity: 2.6,
    roughness: 0.5, metalness: 0.0,
  });
  const lanternGlassMat = new THREE.MeshStandardMaterial({
    color: 0xffb055, emissive: 0xff8a30, emissiveIntensity: 2.4,
    roughness: 0.5, metalness: 0.0,
  });
  const crowMat = new THREE.MeshStandardMaterial({
    color: 0x0a0a0d, roughness: 1.0, metalness: 0.0,
  });

  const postGeo = new THREE.CylinderGeometry(0.11, 0.14, 3.2, 7);
  const flameGeo = new THREE.ConeGeometry(0.16, 0.5, 6);
  const cageBarGeo = new THREE.CylinderGeometry(0.015, 0.015, 0.42, 4);
  const cageRingGeo = new THREE.TorusGeometry(0.19, 0.02, 4, 10);

  // ===========================================================================
  // (1) PATH TORCHES — pairs flanking the lane at x = +-5.2
  // ===========================================================================
  const torchZ = [48, 40, 32, 24, 16, 8, 0, -8, -16, -24, -32, -40, -48, -56, -64];
  const sides = [-5.2, 5.2];

  function makeTorch(x, z) {
    const t = new THREE.Group();
    t.position.set(x, 0, z);

    // Wooden post
    const post = new THREE.Mesh(postGeo, darkWoodMat);
    post.position.y = 1.6;
    t.add(post);

    // Iron cap ring at top
    const capRing = new THREE.Mesh(cageRingGeo, ironMat);
    capRing.position.y = 3.05;
    capRing.rotation.x = Math.PI / 2;
    t.add(capRing);

    // Iron cage — vertical bars around the flame
    const cage = new THREE.Group();
    cage.position.y = 3.35;
    const nBars = 5;
    for (let i = 0; i < nBars; i++) {
      const a = (i / nBars) * TAU;
      const bar = new THREE.Mesh(cageBarGeo, ironMat);
      bar.position.set(Math.cos(a) * 0.17, 0, Math.sin(a) * 0.17);
      cage.add(bar);
    }
    const topRing = new THREE.Mesh(cageRingGeo, ironMat);
    topRing.position.y = 0.21;
    topRing.rotation.x = Math.PI / 2;
    topRing.scale.setScalar(0.85);
    cage.add(topRing);
    t.add(cage);

    // Emissive flame
    const flame = new THREE.Mesh(flameGeo, flameMat);
    flame.position.y = 3.34;
    flame.scale.set(1, rand(0.85, 1.15), 1);
    t.add(flame);

    // Warm point light
    const light = new THREE.PointLight(0xff9a40, 1.8, 17, 2);
    light.position.y = 3.4;
    t.add(light);
    flickers.push({
      light, base: 1.8, amp: 0.35, speed: 7 + rng() * 6, phase: rng() * TAU,
    });

    group.add(t);

    // Thin collider
    colliders.push({
      minX: x - 0.2, maxX: x + 0.2, minZ: z - 0.2, maxZ: z + 0.2,
    });
  }

  for (const z of torchZ) {
    // Skip a pair only if it clashes with the gate near z=-73 (none in list, but guard)
    if (z < -70 && z > -76) continue;
    for (const x of sides) makeTorch(x, z);
  }

  // ===========================================================================
  // (6) ROADSIDE GIBBETS / HANGING LANTERNS on tall poles
  // ===========================================================================
  const gibbetPoleGeo = new THREE.CylinderGeometry(0.13, 0.16, 4.6, 7);
  const armGeo = new THREE.BoxGeometry(1.4, 0.14, 0.14);
  const cageBoxBarGeo = new THREE.CylinderGeometry(0.02, 0.02, 1.1, 4);
  const chainGeo = new THREE.CylinderGeometry(0.015, 0.015, 0.6, 4);
  const lanternBodyGeo = new THREE.CylinderGeometry(0.16, 0.2, 0.42, 6);
  const lanternGlowGeo = new THREE.IcosahedronGeometry(0.13, 0);

  function makeGibbet(x, z, rotY, hanging) {
    const g = new THREE.Group();
    g.position.set(x, 0, z);
    g.rotation.y = rotY;

    const pole = new THREE.Mesh(gibbetPoleGeo, darkWoodMat);
    pole.position.y = 2.3;
    g.add(pole);

    // Horizontal arm reaching over the road
    const arm = new THREE.Mesh(armGeo, darkWoodMat);
    arm.position.set(0.55, 4.4, 0);
    g.add(arm);

    // Chain from arm tip
    const chain = new THREE.Mesh(chainGeo, ironMat);
    chain.position.set(1.15, 4.05, 0);
    g.add(chain);

    if (hanging) {
      // Grim hanging iron cage (empty gibbet)
      const cage = new THREE.Group();
      cage.position.set(1.15, 3.2, 0);
      const nBars = 4;
      for (let i = 0; i < nBars; i++) {
        const a = (i / nBars) * TAU + Math.PI / 4;
        const bar = new THREE.Mesh(cageBoxBarGeo, ironMat);
        bar.position.set(Math.cos(a) * 0.24, 0, Math.sin(a) * 0.24);
        cage.add(bar);
      }
      const rTop = new THREE.Mesh(cageRingGeo, ironMat);
      rTop.position.y = 0.55; rTop.rotation.x = Math.PI / 2;
      rTop.scale.setScalar(1.3);
      cage.add(rTop);
      const rBot = new THREE.Mesh(cageRingGeo, ironMat);
      rBot.position.y = -0.55; rBot.rotation.x = Math.PI / 2;
      rBot.scale.setScalar(1.3);
      cage.add(rBot);
      g.add(cage);
      g.userData.swing = cage;
    } else {
      // Hanging lantern with warm glow + point light
      const lantern = new THREE.Group();
      lantern.position.set(1.15, 3.55, 0);
      const body = new THREE.Mesh(lanternBodyGeo, ironMat);
      lantern.add(body);
      const glass = new THREE.Mesh(lanternGlowGeo, lanternGlassMat);
      glass.scale.set(1, 1.4, 1);
      lantern.add(glass);
      const cap = new THREE.Mesh(
        new THREE.ConeGeometry(0.22, 0.18, 6), ironMat);
      cap.position.y = 0.3;
      lantern.add(cap);
      const light = new THREE.PointLight(0xffa050, 1.4, 12, 2);
      lantern.add(light);
      flickers.push({
        light, base: 1.4, amp: 0.3, speed: 6 + rng() * 5, phase: rng() * TAU,
      });
      g.add(lantern);
      g.userData.swing = lantern;
    }

    group.add(g);

    // Collider for solid pole
    colliders.push({
      minX: x - 0.22, maxX: x + 0.22, minZ: z - 0.22, maxZ: z + 0.22,
    });
    return g;
  }

  const gibbets = [];
  gibbets.push(makeGibbet(-8.5, 30, Math.PI / 2, true));
  gibbets.push(makeGibbet(9.0, -12, -Math.PI / 2, false));
  gibbets.push(makeGibbet(-9.5, -44, Math.PI / 2, true));
  gibbets.push(makeGibbet(8.5, 12, -Math.PI / 2, false));

  // ===========================================================================
  // (2) FLOATING EMBERS / FIREFLIES — ~350 warm motes drifting upward
  // ===========================================================================
  const EMBER_N = 350;
  const emberPos = new Float32Array(EMBER_N * 3);
  const emberData = []; // per-mote: baseX, baseZ, phase, riseSpeed, swayAmp, swaySpeed
  const emberBoundX = 130, emberBoundZ = 130, emberTop = 22;
  for (let i = 0; i < EMBER_N; i++) {
    const bx = rand(-emberBoundX, emberBoundX);
    const bz = rand(-emberBoundZ, emberBoundZ);
    const y = rand(0.3, emberTop);
    emberPos[i * 3] = bx;
    emberPos[i * 3 + 1] = y;
    emberPos[i * 3 + 2] = bz;
    emberData.push({
      bx, bz, phase: rng() * TAU,
      rise: rand(0.35, 0.95),
      swayAmp: rand(0.4, 1.6),
      swaySpeed: rand(0.3, 0.9),
    });
  }
  const emberGeo = new THREE.BufferGeometry();
  emberGeo.setAttribute('position', new THREE.Float32BufferAttribute(emberPos, 3));
  const emberMat = new THREE.PointsMaterial({
    size: 0.15, color: 0xffb347, transparent: true,
    blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.9,
  });
  const embers = new THREE.Points(emberGeo, emberMat);
  embers.frustumCulled = false;
  group.add(embers);

  // ===========================================================================
  // (3) DRIFTING MIST MOTES — ~200 pale points, slow lateral drift
  // ===========================================================================
  const MIST_N = 200;
  const mistPos = new Float32Array(MIST_N * 3);
  const mistData = [];
  const mistBoundX = 140, mistBoundZ = 150;
  for (let i = 0; i < MIST_N; i++) {
    const bx = rand(-mistBoundX, mistBoundX);
    const bz = rand(-mistBoundZ, mistBoundZ);
    const y = rand(0.2, 3.5);
    mistPos[i * 3] = bx;
    mistPos[i * 3 + 1] = y;
    mistPos[i * 3 + 2] = bz;
    mistData.push({
      bx, bz, phase: rng() * TAU,
      driftSpeed: rand(0.15, 0.5),
      driftDir: rng() * TAU,
      bob: rand(0.1, 0.4),
    });
  }
  const mistGeo = new THREE.BufferGeometry();
  mistGeo.setAttribute('position', new THREE.Float32BufferAttribute(mistPos, 3));
  const mistMat = new THREE.PointsMaterial({
    size: 0.55, color: 0x8a94a8, transparent: true,
    blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.22,
  });
  const mist = new THREE.Points(mistGeo, mistMat);
  mist.frustumCulled = false;
  group.add(mist);

  // ===========================================================================
  // (4) FLOCK OF CROWS / RAVENS — ~9 birds circling above
  // ===========================================================================
  const CROW_N = 9;
  const crowBodyGeo = new THREE.ConeGeometry(0.12, 0.7, 5);
  const crowWingGeo = new THREE.PlaneGeometry(0.9, 0.32);
  const crows = [];
  const crowWingMat = new THREE.MeshStandardMaterial({
    color: 0x0a0a0d, roughness: 1.0, metalness: 0.0, side: THREE.DoubleSide,
  });
  for (let i = 0; i < CROW_N; i++) {
    const c = new THREE.Group();
    const body = new THREE.Mesh(crowBodyGeo, crowMat);
    body.rotation.z = Math.PI / 2; // point forward
    c.add(body);

    const wL = new THREE.Mesh(crowWingGeo, crowWingMat);
    wL.position.set(0, 0, -0.42);
    const wLpivot = new THREE.Group();
    wLpivot.add(wL);
    c.add(wLpivot);

    const wR = new THREE.Mesh(crowWingGeo, crowWingMat);
    wR.position.set(0, 0, 0.42);
    const wRpivot = new THREE.Group();
    wRpivot.add(wR);
    c.add(wRpivot);

    group.add(c);
    crows.push({
      grp: c, wingL: wLpivot, wingR: wRpivot,
      radius: rand(45, 80),
      cx: rand(-20, 10), cz: rand(-70, 0),
      y: rand(18, 28),
      angle: rng() * TAU,
      angSpeed: rand(0.06, 0.14) * (rng() > 0.5 ? 1 : -1),
      flapSpeed: rand(6, 10),
      flapPhase: rng() * TAU,
      bobAmp: rand(0.4, 1.2),
    });
  }

  // ===========================================================================
  // (5) WILL-O'-THE-WISPS — faint blue-green lights near graveyard/woods
  // ===========================================================================
  const WISP_N = 5;
  const wisps = [];
  const wispCoreGeo = new THREE.IcosahedronGeometry(0.09, 0);
  // Eerie centers biased toward graveyard (55,0,5) and outer woods
  const wispCenters = [
    { x: 55, z: 5 }, { x: 48, z: 14 }, { x: 62, z: -4 },
    { x: -60, z: 20 }, { x: 40, z: 40 },
  ];
  for (let i = 0; i < WISP_N; i++) {
    const w = new THREE.Group();
    const core = new THREE.Mesh(wispCoreGeo, emberCoreMat);
    w.add(core);
    const light = new THREE.PointLight(0x5fd0b0, 0.5, 10, 2);
    w.add(light);
    const ctr = wispCenters[i % wispCenters.length];
    w.position.set(ctr.x, 1.4, ctr.z);
    group.add(w);
    // gentle flicker on the wisp light
    flickers.push({
      light, base: 0.5, amp: 0.18, speed: 2.5 + rng() * 2, phase: rng() * TAU,
    });
    wisps.push({
      grp: w, cx: ctr.x, cz: ctr.z,
      rx: rand(4, 9), rz: rand(4, 9),
      baseY: rand(1.0, 1.8),
      angle: rng() * TAU,
      angSpeed: rand(0.15, 0.35) * (rng() > 0.5 ? 1 : -1),
      bobAmp: rand(0.25, 0.6), bobSpeed: rand(0.5, 1.1),
      bobPhase: rng() * TAU,
    });
  }

  // ===========================================================================
  // UPDATE
  // ===========================================================================
  function update(dt, elapsed) {
    // Embers: drift upward, sway, respawn at bottom
    const ep = embers.geometry.attributes.position.array;
    for (let i = 0; i < EMBER_N; i++) {
      const d = emberData[i];
      let y = ep[i * 3 + 1] + d.rise * dt;
      if (y > emberTop) {
        y = 0.2;
        d.bx = rand(-emberBoundX, emberBoundX);
        d.bz = rand(-emberBoundZ, emberBoundZ);
      }
      ep[i * 3] = d.bx + Math.sin(elapsed * d.swaySpeed + d.phase) * d.swayAmp;
      ep[i * 3 + 1] = y;
      ep[i * 3 + 2] = d.bz + Math.cos(elapsed * d.swaySpeed * 0.8 + d.phase) * d.swayAmp;
    }
    embers.geometry.attributes.position.needsUpdate = true;

    // Mist: slow lateral drift + gentle bob
    const mp = mist.geometry.attributes.position.array;
    for (let i = 0; i < MIST_N; i++) {
      const d = mistData[i];
      const t = elapsed * d.driftSpeed + d.phase;
      const off = Math.sin(t) * 6.0;
      mp[i * 3] = d.bx + Math.cos(d.driftDir) * off;
      mp[i * 3 + 1] = Math.max(0.15, mp[i * 3 + 1]) + 0; // keep, bob below
      mp[i * 3 + 1] = 1.6 + Math.sin(t * 0.7 + d.phase) * (0.9 + d.bob) + d.bob;
      mp[i * 3 + 2] = d.bz + Math.sin(d.driftDir) * off;
    }
    mist.geometry.attributes.position.needsUpdate = true;

    // Crows: circle orbit + flap wings + bob
    for (const c of crows) {
      c.angle += c.angSpeed * dt;
      const x = c.cx + Math.cos(c.angle) * c.radius;
      const z = c.cz + Math.sin(c.angle) * c.radius;
      const y = c.y + Math.sin(elapsed * 0.5 + c.flapPhase) * c.bobAmp;
      c.grp.position.set(x, y, z);
      // face direction of travel (tangent)
      const heading = c.angle + (c.angSpeed > 0 ? Math.PI / 2 : -Math.PI / 2);
      c.grp.rotation.y = -heading + Math.PI / 2;
      const flap = Math.sin(elapsed * c.flapSpeed + c.flapPhase) * 0.7;
      c.wingL.rotation.x = flap;
      c.wingR.rotation.x = -flap;
    }

    // Will-o'-the-wisps: drift along eerie elliptical paths + bob
    for (const w of wisps) {
      w.angle += w.angSpeed * dt;
      const x = w.cx + Math.cos(w.angle) * w.rx
        + Math.sin(w.angle * 0.4) * 2.0;
      const z = w.cz + Math.sin(w.angle) * w.rz
        + Math.cos(w.angle * 0.6) * 2.0;
      const y = w.baseY + Math.sin(elapsed * w.bobSpeed + w.bobPhase) * w.bobAmp;
      w.grp.position.set(x, y, z);
    }

    // Gibbet cages / lanterns: slow creaking swing
    for (let i = 0; i < gibbets.length; i++) {
      const sw = gibbets[i].userData.swing;
      if (sw) sw.rotation.z = Math.sin(elapsed * 0.6 + i) * 0.06;
    }
  }

  return { group, colliders, flickers, update };
}
