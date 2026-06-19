/* =========================================================
   ZENITH — Hero 3D scene
   A procedural Moscow-City tower at night, built entirely
   from Three.js primitives (no external 3D assets required).
   ========================================================= */

/* Three.js is loaded via dynamic import so that a CDN/network failure is
   caught here (a static top-level import would abort the whole module before
   any of our error handling could run). On failure we emit `scene:ready` so
   the preloader hides immediately and the CSS gradient on .hero__canvas
   remains as a graceful fallback. */
let THREE, RoomEnvironment;

const canvas = document.getElementById("scene");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

(async () => {
  try {
    THREE = await import("three");
    ({ RoomEnvironment } = await import("three/addons/environments/RoomEnvironment.js"));
    init();
  } catch (err) {
    console.warn("ZENITH 3D scene disabled (using gradient fallback):", err);
    window.dispatchEvent(new Event("scene:ready"));
  }
})();

function init() {
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: "high-performance",
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.12;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x070a12, 0.0125);

  const camera = new THREE.PerspectiveCamera(42, window.innerWidth / window.innerHeight, 0.1, 600);
  camera.position.set(0, 9, 34);

  /* ---- Environment for soft glass reflections ---- */
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;

  /* ---- Atmospheric gradient sky (large inverted sphere) ---- */
  scene.add(makeSky());

  /* ---- Lighting ---- */
  const hemi = new THREE.HemisphereLight(0x2a3a66, 0x05060a, 0.7);
  scene.add(hemi);

  const key = new THREE.DirectionalLight(0xffe9c2, 1.5);
  key.position.set(-18, 30, 18);
  scene.add(key);

  const rim = new THREE.DirectionalLight(0x6f8bd6, 0.8);
  rim.position.set(20, 14, -22);
  scene.add(rim);

  const goldGlow = new THREE.PointLight(0xc9a35e, 60, 60, 2);
  goldGlow.position.set(0, 24, 6);
  scene.add(goldGlow);

  /* ---- City ---- */
  const city = new THREE.Group();
  scene.add(city);

  const hero = makeTower();
  city.add(hero);

  city.add(makeSkyline());
  city.add(makeGround());

  /* ---- Floating light particles ---- */
  const particles = makeParticles();
  scene.add(particles);

  /* ---- Interaction state ---- */
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  let scrollT = 0;

  window.addEventListener("pointermove", (e) => {
    pointer.tx = (e.clientX / window.innerWidth - 0.5);
    pointer.ty = (e.clientY / window.innerHeight - 0.5);
  });

  window.addEventListener("scroll", () => {
    const h = window.innerHeight;
    scrollT = Math.min(1, Math.max(0, window.scrollY / h));
  }, { passive: true });

  window.addEventListener("resize", onResize);
  function onResize() {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  }

  /* ---- Animation loop ---- */
  const clock = new THREE.Clock();
  let angle = 0;
  let firstFrame = true;

  function tick() {
    const dt = Math.min(clock.getDelta(), 0.05);

    if (!reduceMotion) {
      angle += dt * 0.045;
      particles.rotation.y += dt * 0.01;
      particles.material.uniforms.uTime.value += dt;
    }

    pointer.x += (pointer.tx - pointer.x) * 0.05;
    pointer.y += (pointer.ty - pointer.y) * 0.05;

    const radius = 34 - scrollT * 6;
    const baseY = 9 + scrollT * 16;
    camera.position.x = Math.sin(angle) * radius + pointer.x * 6;
    camera.position.z = Math.cos(angle) * radius;
    camera.position.y = baseY + pointer.y * -3;
    camera.lookAt(0, 11 + scrollT * 6, 0);

    // gentle breathing glow on the spire light
    goldGlow.intensity = 50 + Math.sin(clock.elapsedTime * 1.3) * 12;

    renderer.render(scene, camera);

    if (firstFrame) {
      firstFrame = false;
      window.dispatchEvent(new Event("scene:ready"));
    }
    requestAnimationFrame(tick);
  }
  tick();

  // Pause rendering cost is negligible, but stop particle time drift when hidden
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) clock.getDelta();
  });
}

/* =========================================================
   Builders
   ========================================================= */

/* Procedural building facade: a grid of windows, some lit warm. */
function makeFacadeTexture({ cols = 14, rows = 40, lit = 0.42, warm = true } = {}) {
  const cell = 16;
  const c = document.createElement("canvas");
  c.width = cols * cell;
  c.height = rows * cell;
  const ctx = c.getContext("2d");

  ctx.fillStyle = "#05070b";
  ctx.fillRect(0, 0, c.width, c.height);

  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const isLit = Math.random() < lit;
      const px = x * cell + 3;
      const py = y * cell + 3;
      const w = cell - 6;
      const h = cell - 6;
      if (isLit) {
        const g = ctx.createLinearGradient(px, py, px, py + h);
        if (warm && Math.random() > 0.25) {
          g.addColorStop(0, "#ffe6b0");
          g.addColorStop(1, "#d59a4e");
        } else {
          g.addColorStop(0, "#cfe2ff");
          g.addColorStop(1, "#7f97c4");
        }
        ctx.fillStyle = g;
      } else {
        ctx.fillStyle = "#0a0f18";
      }
      ctx.fillRect(px, py, w, h);
    }
  }

  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.anisotropy = 4;
  return tex;
}

/* The hero tower — tiered glass volume with a glowing crown. */
function makeTower() {
  const group = new THREE.Group();

  const facade = makeFacadeTexture({ cols: 16, rows: 60, lit: 0.4 });

  const glass = (repeatX, repeatY, color = 0x0a0e15) => {
    const map = facade.clone();
    map.needsUpdate = true;
    map.repeat.set(repeatX, repeatY);
    return new THREE.MeshPhysicalMaterial({
      color,
      metalness: 0.35,
      roughness: 0.16,
      envMapIntensity: 1.5,
      emissive: 0xffffff,
      emissiveMap: map,
      emissiveIntensity: 1.0,
      clearcoat: 0.6,
      clearcoatRoughness: 0.25,
    });
  };

  // Stacked tiers (footprint narrows as it rises -> premium setback look)
  const tiers = [
    { w: 9, d: 9, h: 22, y: 11 },
    { w: 7.4, d: 7.4, h: 12, y: 28 },
    { w: 5.8, d: 5.8, h: 9, y: 38.5 },
  ];
  tiers.forEach((t) => {
    const geo = new THREE.BoxGeometry(t.w, t.h, t.d);
    const repeatY = Math.round(t.h * 1.6);
    const mesh = new THREE.Mesh(geo, glass(Math.round(t.w * 1.6), repeatY));
    mesh.position.y = t.y;
    group.add(mesh);

    // thin bronze cornice between tiers
    const cap = new THREE.Mesh(
      new THREE.BoxGeometry(t.w + 0.3, 0.4, t.d + 0.3),
      new THREE.MeshStandardMaterial({ color: 0xc9a35e, metalness: 1, roughness: 0.35, envMapIntensity: 1.4 })
    );
    cap.position.y = t.y + t.h / 2;
    group.add(cap);
  });

  // Spire
  const spire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.08, 0.32, 8, 12),
    new THREE.MeshStandardMaterial({ color: 0xc9a35e, metalness: 1, roughness: 0.3 })
  );
  spire.position.y = 47;
  group.add(spire);

  // Glowing crown orb
  const orb = new THREE.Mesh(
    new THREE.SphereGeometry(0.55, 24, 24),
    new THREE.MeshStandardMaterial({ color: 0xffe9c2, emissive: 0xe7c98a, emissiveIntensity: 3 })
  );
  orb.position.y = 51.4;
  group.add(orb);

  const orbLight = new THREE.PointLight(0xe7c98a, 30, 40, 2);
  orbLight.position.y = 51.4;
  group.add(orbLight);

  return group;
}

/* A ring of supporting towers to suggest the wider Moscow-City skyline. */
function makeSkyline() {
  const group = new THREE.Group();
  const facade = makeFacadeTexture({ cols: 10, rows: 30, lit: 0.34 });

  const count = 26;
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const map = facade.clone();
  map.needsUpdate = true;
  const mat = new THREE.MeshStandardMaterial({
    color: 0x070b12,
    metalness: 0.3,
    roughness: 0.4,
    emissive: 0xffffff,
    emissiveMap: map,
    emissiveIntensity: 0.85,
    envMapIntensity: 0.8,
  });

  const mesh = new THREE.InstancedMesh(geo, mat, count);
  const dummy = new THREE.Object3D();

  for (let i = 0; i < count; i++) {
    const ang = (i / count) * Math.PI * 2 + Math.random() * 0.2;
    const dist = 26 + Math.random() * 30;
    const w = 3 + Math.random() * 4;
    const h = 10 + Math.random() * 34;
    const d = 3 + Math.random() * 4;
    dummy.position.set(Math.cos(ang) * dist, h / 2, Math.sin(ang) * dist);
    dummy.scale.set(w, h, d);
    dummy.rotation.y = Math.random() * Math.PI;
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  }
  group.add(mesh);
  return group;
}

/* Reflective ground plane (river/plaza). */
function makeGround() {
  const geo = new THREE.PlaneGeometry(600, 600);
  const mat = new THREE.MeshStandardMaterial({
    color: 0x04060a,
    metalness: 0.85,
    roughness: 0.42,
    envMapIntensity: 0.7,
  });
  const plane = new THREE.Mesh(geo, mat);
  plane.rotation.x = -Math.PI / 2;
  plane.position.y = 0;
  return plane;
}

/* Drifting golden light motes. */
function makeParticles() {
  const count = 1400;
  const positions = new Float32Array(count * 3);
  const seeds = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 120;
    positions[i * 3 + 1] = Math.random() * 70;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 120;
    seeds[i] = Math.random() * Math.PI * 2;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geo.setAttribute("aSeed", new THREE.BufferAttribute(seeds, 1));

  const mat = new THREE.ShaderMaterial({
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    uniforms: { uTime: { value: 0 } },
    vertexShader: `
      uniform float uTime;
      attribute float aSeed;
      varying float vA;
      void main() {
        vec3 p = position;
        p.y += sin(uTime * 0.3 + aSeed) * 1.5;
        p.x += cos(uTime * 0.2 + aSeed) * 1.2;
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = (28.0 / -mv.z) * (0.6 + 0.4 * sin(aSeed));
        vA = 0.4 + 0.6 * abs(sin(uTime * 0.6 + aSeed));
      }
    `,
    fragmentShader: `
      varying float vA;
      void main() {
        vec2 uv = gl_PointCoord - 0.5;
        float d = smoothstep(0.5, 0.0, length(uv));
        gl_FragColor = vec4(0.85, 0.68, 0.4, d * vA);
      }
    `,
  });

  return new THREE.Points(geo, mat);
}

/* Vertical gradient sky dome. */
function makeSky() {
  const geo = new THREE.SphereGeometry(280, 32, 16);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    uniforms: {
      uTop: { value: new THREE.Color(0x05070f) },
      uMid: { value: new THREE.Color(0x0b1322) },
      uBot: { value: new THREE.Color(0x1a1410) },
    },
    vertexShader: `
      varying vec3 vPos;
      void main() {
        vPos = position;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      varying vec3 vPos;
      uniform vec3 uTop;
      uniform vec3 uMid;
      uniform vec3 uBot;
      void main() {
        float h = normalize(vPos).y;
        vec3 col = mix(uMid, uTop, smoothstep(0.0, 0.7, h));
        col = mix(uBot, col, smoothstep(-0.35, 0.08, h));
        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  return new THREE.Mesh(geo, mat);
}
