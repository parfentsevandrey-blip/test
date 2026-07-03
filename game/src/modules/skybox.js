import * as THREE from 'three';

export function build(ctx) {
  const { rng } = ctx;
  const group = new THREE.Group();
  const colliders = [];
  const flickers = [];

  // ---------------------------------------------------------------
  // (1) SKY DOME — inverted sphere with vertical gradient
  // ---------------------------------------------------------------
  const domeRadius = 430;
  const domeGeo = new THREE.SphereGeometry(domeRadius, 32, 24);
  const domeMat = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    fog: false,
    uniforms: {
      topColor: { value: new THREE.Color(0x05060d) },
      horizonColor: { value: new THREE.Color(0x241a30) },
      radius: { value: domeRadius },
    },
    vertexShader: /* glsl */ `
      varying vec3 vWorldPos;
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        vWorldPos = wp.xyz;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }
    `,
    fragmentShader: /* glsl */ `
      varying vec3 vWorldPos;
      uniform vec3 topColor;
      uniform vec3 horizonColor;
      uniform float radius;
      void main() {
        // normalized height -0.15..1 -> 0..1
        float h = clamp(vWorldPos.y / radius, -0.15, 1.0);
        float t = smoothstep(0.0, 0.85, h);
        vec3 col = mix(horizonColor, topColor, t);
        // subtle darkening below horizon
        col *= mix(0.6, 1.0, smoothstep(-0.15, 0.05, h));
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const dome = new THREE.Mesh(domeGeo, domeMat);
  dome.frustumCulled = false;
  group.add(dome);

  // ---------------------------------------------------------------
  // (3) MOON DirectionalLight
  // ---------------------------------------------------------------
  const moonDir = new THREE.Vector3(-0.5, 0.6, -0.7).normalize();
  const moonLight = new THREE.DirectionalLight(0x8098c8, 0.22);
  moonLight.name = 'moonLight';
  moonLight.position.set(-120, 150, -170);
  moonLight.target.position.set(0, 0, 0);
  group.add(moonLight);
  group.add(moonLight.target);

  // ---------------------------------------------------------------
  // (2) MOON disc + halo, placed along moonDir at distance ~380
  // ---------------------------------------------------------------
  const moonDist = 380;
  const moonPos = moonDir.clone().multiplyScalar(moonDist);
  const moonGroup = new THREE.Group();
  moonGroup.position.copy(moonPos);
  // orient the discs to face the origin (player)
  moonGroup.lookAt(0, 0, 0);
  group.add(moonGroup);

  // soft large additive halo (behind)
  const haloMat = new THREE.MeshBasicMaterial({
    color: 0x9fb6e6,
    transparent: true,
    opacity: 0.16,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    depthTest: false,
    side: THREE.DoubleSide,
    fog: false,
  });
  const halo = new THREE.Mesh(new THREE.CircleGeometry(78, 48), haloMat);
  halo.position.z = -2;
  halo.renderOrder = -8;
  moonGroup.add(halo);

  const halo2 = new THREE.Mesh(new THREE.CircleGeometry(46, 48), haloMat.clone());
  halo2.material.opacity = 0.22;
  halo2.position.z = -1;
  halo2.renderOrder = -7;
  moonGroup.add(halo2);

  // moon disc (glowing pale) — use a canvas texture for a gibbous shading
  const moonCanvas = document.createElement('canvas');
  moonCanvas.width = 256;
  moonCanvas.height = 256;
  const mctx = moonCanvas.getContext('2d');
  // base disc
  const grad = mctx.createRadialGradient(118, 110, 20, 128, 128, 128);
  grad.addColorStop(0, '#eef4ff');
  grad.addColorStop(0.7, '#cfe0ff');
  grad.addColorStop(1, '#9fb2d8');
  mctx.beginPath();
  mctx.arc(128, 128, 124, 0, Math.PI * 2);
  mctx.closePath();
  mctx.fillStyle = grad;
  mctx.fill();
  // gibbous terminator shadow on the lower-right
  mctx.globalCompositeOperation = 'source-atop';
  const shadeGrad = mctx.createRadialGradient(90, 100, 40, 175, 165, 150);
  shadeGrad.addColorStop(0, 'rgba(5,6,13,0)');
  shadeGrad.addColorStop(0.75, 'rgba(5,6,13,0)');
  shadeGrad.addColorStop(1, 'rgba(5,6,13,0.85)');
  mctx.fillStyle = shadeGrad;
  mctx.fillRect(0, 0, 256, 256);
  // a few faint craters
  mctx.globalCompositeOperation = 'source-atop';
  const craters = [[100, 95, 14], [150, 120, 10], [120, 160, 18], [90, 140, 9], [165, 90, 7]];
  for (const [cx, cy, cr] of craters) {
    const cg = mctx.createRadialGradient(cx, cy, 1, cx, cy, cr);
    cg.addColorStop(0, 'rgba(120,135,170,0.35)');
    cg.addColorStop(1, 'rgba(120,135,170,0)');
    mctx.fillStyle = cg;
    mctx.beginPath();
    mctx.arc(cx, cy, cr, 0, Math.PI * 2);
    mctx.fill();
  }
  const moonTex = new THREE.CanvasTexture(moonCanvas);
  moonTex.colorSpace = THREE.SRGBColorSpace;
  const moonMat = new THREE.MeshBasicMaterial({
    map: moonTex,
    transparent: true,
    depthWrite: false,
    depthTest: false,
    fog: false,
  });
  // make it read as an emissive glow via additive-ish bright base
  moonMat.color = new THREE.Color(0xffffff);
  const moonDisc = new THREE.Mesh(new THREE.CircleGeometry(28, 48), moonMat);
  moonDisc.renderOrder = -6;
  moonGroup.add(moonDisc);

  // an emissive standard mesh sibling so bloom picks it up strongly
  const moonEmiss = new THREE.Mesh(
    new THREE.CircleGeometry(27, 48),
    new THREE.MeshStandardMaterial({
      color: 0x0a0c14,
      emissive: 0xcfe0ff,
      emissiveIntensity: 3,
      emissiveMap: moonTex,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      fog: false,
    })
  );
  moonEmiss.position.z = 0.2;
  moonEmiss.renderOrder = -5;
  moonGroup.add(moonEmiss);

  // ---------------------------------------------------------------
  // (4) STARFIELD — ~1600 points on upper hemisphere
  // ---------------------------------------------------------------
  const starCount = 1600;
  const starR = 410;
  const starPos = new Float32Array(starCount * 3);
  const starColor = new Float32Array(starCount * 3);
  const starPhase = new Float32Array(starCount);
  const starBaseSize = new Float32Array(starCount);
  const starBaseOpacity = new Float32Array(starCount);
  const cWarm = new THREE.Color(0xfff2dd);
  const cCool = new THREE.Color(0xbfd4ff);
  const cWhite = new THREE.Color(0xffffff);
  for (let i = 0; i < starCount; i++) {
    // uniform on upper hemisphere, keep a bit above horizon
    const u = rng();
    const v = 0.02 + rng() * 0.98; // avoid exact horizon
    const theta = 2 * Math.PI * u;
    const phi = Math.acos(v); // 0..~pi/2 -> upper
    const sx = Math.sin(phi) * Math.cos(theta);
    const sy = Math.cos(phi); // >=0 upper
    const sz = Math.sin(phi) * Math.sin(theta);
    starPos[i * 3] = sx * starR;
    starPos[i * 3 + 1] = sy * starR;
    starPos[i * 3 + 2] = sz * starR;
    const tint = rng();
    let col;
    if (tint < 0.6) col = cWhite;
    else if (tint < 0.82) col = cCool;
    else col = cWarm;
    starColor[i * 3] = col.r;
    starColor[i * 3 + 1] = col.g;
    starColor[i * 3 + 2] = col.b;
    starPhase[i] = rng() * Math.PI * 2;
    starBaseSize[i] = 1.1 + rng() * rng() * 4.2;
    starBaseOpacity[i] = 0.35 + rng() * 0.65;
  }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute('position', new THREE.Float32BufferAttribute(starPos, 3));
  starGeo.setAttribute('color', new THREE.Float32BufferAttribute(starColor, 3));
  const sizeAttr = new THREE.Float32BufferAttribute(new Float32Array(starCount), 1);
  for (let i = 0; i < starCount; i++) sizeAttr.array[i] = starBaseSize[i];
  starGeo.setAttribute('size', sizeAttr);
  // Soft round sprite so points render as dots, not hard squares.
  const dotTex = (() => {
    const cnv = document.createElement('canvas'); cnv.width = 64; cnv.height = 64;
    const g = cnv.getContext('2d');
    const rad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    rad.addColorStop(0.0, 'rgba(255,255,255,1)');
    rad.addColorStop(0.35, 'rgba(255,255,255,0.75)');
    rad.addColorStop(1.0, 'rgba(255,255,255,0)');
    g.fillStyle = rad; g.fillRect(0, 0, 64, 64);
    const t = new THREE.CanvasTexture(cnv); t.colorSpace = THREE.SRGBColorSpace;
    return t;
  })();
  const starMat = new THREE.PointsMaterial({
    size: 2.4,
    map: dotTex,
    alphaMap: dotTex,
    vertexColors: true,
    transparent: true,
    opacity: 0.9,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    depthTest: false,
    sizeAttenuation: false,
    fog: false,
  });
  const stars = new THREE.Points(starGeo, starMat);
  stars.frustumCulled = false;
  stars.renderOrder = -9;
  group.add(stars);

  // A second sparse layer of a few bright "beacon" stars, larger
  const brightCount = 40;
  const bPos = new Float32Array(brightCount * 3);
  const bPhase = new Float32Array(brightCount);
  for (let i = 0; i < brightCount; i++) {
    const u = rng();
    const v = 0.1 + rng() * 0.9;
    const theta = 2 * Math.PI * u;
    const phi = Math.acos(v);
    bPos[i * 3] = Math.sin(phi) * Math.cos(theta) * starR;
    bPos[i * 3 + 1] = Math.cos(phi) * starR;
    bPos[i * 3 + 2] = Math.sin(phi) * Math.sin(theta) * starR;
    bPhase[i] = rng() * Math.PI * 2;
  }
  const brightGeo = new THREE.BufferGeometry();
  brightGeo.setAttribute('position', new THREE.Float32BufferAttribute(bPos, 3));
  const brightMat = new THREE.PointsMaterial({
    size: 6,
    map: dotTex,
    alphaMap: dotTex,
    color: 0xdfe9ff,
    transparent: true,
    opacity: 0.85,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    depthTest: false,
    sizeAttenuation: false,
    fog: false,
  });
  const brightStars = new THREE.Points(brightGeo, brightMat);
  brightStars.frustumCulled = false;
  brightStars.renderOrder = -9;
  group.add(brightStars);

  // ---------------------------------------------------------------
  // (5) CLOUD planes — large, dark, semi-transparent, drifting
  // ---------------------------------------------------------------
  function makeCloudTexture(seed) {
    const c = document.createElement('canvas');
    c.width = 256;
    c.height = 128;
    const g = c.getContext('2d');
    g.clearRect(0, 0, 256, 128);
    const blobs = 22 + Math.floor(rng() * 14);
    for (let i = 0; i < blobs; i++) {
      const x = 20 + rng() * 216;
      const y = 30 + rng() * 68;
      const r = 14 + rng() * 46;
      const a = 0.05 + rng() * 0.14;
      const rg = g.createRadialGradient(x, y, 1, x, y, r);
      rg.addColorStop(0, `rgba(18,16,26,${a})`);
      rg.addColorStop(1, 'rgba(18,16,26,0)');
      g.fillStyle = rg;
      g.beginPath();
      g.arc(x, y, r, 0, Math.PI * 2);
      g.fill();
    }
    const t = new THREE.CanvasTexture(c);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  }

  const clouds = [];
  const cloudCount = 5;
  for (let i = 0; i < cloudCount; i++) {
    const tex = makeCloudTexture(i);
    const w = 220 + rng() * 180;
    const h = w * (0.4 + rng() * 0.2);
    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      transparent: true,
      opacity: 0.55 + rng() * 0.3,
      depthWrite: false,
      depthTest: false,
      side: THREE.DoubleSide,
      fog: false,
    });
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
    // place on a dome shell, high in the sky
    const ang = rng() * Math.PI * 2;
    const height = 180 + rng() * 130;
    const rad = 330 + rng() * 40;
    plane.position.set(Math.cos(ang) * rad, height, Math.sin(ang) * rad);
    plane.lookAt(0, plane.position.y * 0.3, 0);
    plane.renderOrder = -4;
    group.add(plane);
    clouds.push({
      mesh: plane,
      ang,
      rad,
      height,
      speed: (0.004 + rng() * 0.008) * (rng() < 0.5 ? 1 : -1),
    });
  }

  // ---------------------------------------------------------------
  // (6) HORIZON SILHOUETTE ring — jagged near-black mountains/forest
  // ---------------------------------------------------------------
  function buildHorizonRing() {
    const ringR = 400;
    const segments = 220;
    const positions = [];
    const indices = [];
    // build two layers: far mountains + near forest, both as a jagged band
    function addBand(radius, baseH, jag, layerJitterSeedScale) {
      const startVert = positions.length / 3;
      const heights = [];
      // generate a jagged height profile with a couple of octaves
      let prev = baseH;
      for (let s = 0; s <= segments; s++) {
        const n1 = Math.sin(s * 0.9 + layerJitterSeedScale) * 0.5 + 0.5;
        const n2 = Math.sin(s * 2.7 + layerJitterSeedScale * 2.0) * 0.5 + 0.5;
        const spike = rng() < 0.12 ? rng() * jag * 1.6 : 0;
        let h = baseH + (n1 * 0.6 + n2 * 0.4) * jag + spike + (rng() - 0.5) * jag * 0.5;
        // smooth a bit with previous
        h = prev * 0.35 + h * 0.65;
        prev = h;
        heights.push(Math.max(4, h));
      }
      for (let s = 0; s <= segments; s++) {
        const a = (s / segments) * Math.PI * 2;
        const x = Math.cos(a) * radius;
        const z = Math.sin(a) * radius;
        positions.push(x, 0, z);
        positions.push(x, heights[s], z);
      }
      for (let s = 0; s < segments; s++) {
        const b = startVert + s * 2;
        // two triangles per quad
        indices.push(b, b + 1, b + 2);
        indices.push(b + 1, b + 3, b + 2);
      }
    }
    // far mountains
    addBand(ringR, 26, 34, 0.3);
    // nearer forest ridge, taller spikes, slightly inside
    addBand(ringR - 18, 10, 22, 3.1);

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
    geo.setIndex(indices);
    geo.computeVertexNormals();
    const mat = new THREE.MeshBasicMaterial({
      color: 0x040406,
      side: THREE.DoubleSide,
      fog: false,
      depthWrite: true,
    });
    const ring = new THREE.Mesh(geo, mat);
    ring.frustumCulled = false;
    ring.renderOrder = -3;
    return ring;
  }
  group.add(buildHorizonRing());

  // ---------------------------------------------------------------
  // UPDATE
  // ---------------------------------------------------------------
  const sizeArr = starGeo.attributes.size.array;
  function update(dt, elapsed) {
    // Twinkle: modulate PointsMaterial global opacity subtly + per-star sizes.
    // Per-star size twinkle (sizeAttenuation false + custom size attr isn't used
    // by default PointsMaterial, so we emulate twinkle via opacity waves + global).
    let twk = 0;
    for (let i = 0; i < starCount; i++) {
      const s = Math.sin(elapsed * (1.4 + (i % 7) * 0.11) + starPhase[i]);
      sizeArr[i] = starBaseSize[i] * (0.75 + 0.25 * (s * 0.5 + 0.5));
      twk += s;
    }
    starGeo.attributes.size.needsUpdate = true;
    // global opacity gentle breathing so twinkle is visible even without size shader
    starMat.opacity = 0.78 + 0.14 * Math.sin(elapsed * 0.8);
    brightMat.opacity = 0.7 + 0.25 * (0.5 + 0.5 * Math.sin(elapsed * 1.3 + 1.2));

    // clouds drift
    for (const c of clouds) {
      c.ang += c.speed * dt;
      c.mesh.position.set(
        Math.cos(c.ang) * c.rad,
        c.height,
        Math.sin(c.ang) * c.rad
      );
      c.mesh.lookAt(0, c.height * 0.3, 0);
    }
  }

  return { group, colliders, flickers, update, lights: [moonLight] };
}
