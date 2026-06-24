/* ============================================================
   Кутузовский XII — engine3d.js
   ------------------------------------------------------------
   A 3D particle atmosphere (Three.js / WebGL) on the fixed
   #ambient canvas, BEHIND the readable content.

   Not a flat 2D particle layer — a real volume:

     · Hundreds of warm motes live in a deep perspective field.
       Near motes are large and bright, far ones tiny and faint,
       so the field has genuine depth (perspective point sizing).
     · They drift slowly on their own, and the whole field FLOWS
       with the scroll — scrolling pulls you vertically through
       the volume, nearer motes streaming faster than far ones
       (depth parallax). The field wraps, so it never empties.
     · The cursor parallaxes the camera, so the motes shift like
       a real 3D space reacting to your viewpoint.
     · Exponential fog tinted to the page colour fades distance
       away, and motes dissolve out with depth — so the field
       only ever whispers behind the text, never competes.
     · Fully theme/accent reactive: mote colour + fog resample
       from the live CSS palette and ease between themes.
   ============================================================ */
(function(){
  const THREE = window.THREE;
  const canvas = document.getElementById('ambient');
  if(!THREE || !canvas) return;

  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  const mobile = matchMedia('(max-width:760px)').matches;
  const root   = document.documentElement;

  /* ============================================================
     COLOUR — resolve the live CSS palette to real sRGB
     (custom props use oklch()/color-mix(); THREE can't read those,
      so we round-trip every colour through a 1px canvas)
     ============================================================ */
  const probe = document.createElement('span');
  probe.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;';
  document.body.appendChild(probe);
  const _pc = document.createElement('canvas'); _pc.width = _pc.height = 1;
  const _pctx = _pc.getContext('2d', { willReadFrequently:true });
  function cssToColor(str, fallback){
    try{
      _pctx.clearRect(0,0,1,1); _pctx.fillStyle = '#000'; _pctx.fillStyle = str;
      _pctx.fillRect(0,0,1,1);
      const d = _pctx.getImageData(0,0,1,1).data;
      return new THREE.Color(d[0]/255, d[1]/255, d[2]/255);
    }catch(e){ return new THREE.Color(fallback || '#100c08'); }
  }
  function resolveVar(name, fallback){
    probe.style.color = fallback || '#000';
    probe.style.color = 'var(' + name + ')';
    return cssToColor(getComputedStyle(probe).color, fallback);
  }
  function pageColor(){
    const c = getComputedStyle(document.body).backgroundColor;
    return cssToColor(c && c !== 'transparent' ? c : '#100c08', '#100c08');
  }

  function buildPalette(){
    const bg   = pageColor();
    const gold = resolveVar('--gold', '#c79a3f');
    // motes read as warm light — lift the accent toward white a touch
    const mote = gold.clone().lerp(new THREE.Color('#ffffff'), 0.44);
    return { fog: bg, mote };
  }
  let pal = buildPalette();

  /* ============================================================
     RENDERER / SCENE / CAMERA
     ============================================================ */
  const renderer = new THREE.WebGLRenderer({ canvas, antialias:true, alpha:true, premultipliedAlpha:false, preserveDrawingBuffer:true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.7));
  renderer.setClearColor(0x000000, 0);
  renderer.setSize(window.innerWidth, window.innerHeight);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth/window.innerHeight, 1, 600);
  camera.position.set(0, 0, 60);

  /* ============================================================
     THE PARTICLE FIELD
     ============================================================ */
  const COUNT = reduce ? 200 : (mobile ? 320 : 520);
  const SPAN_X = 220, RANGE_Y = 160, SPAN_Z = 240, Z0 = 40;   // volume

  const pos    = new Float32Array(COUNT * 3);
  const aScale = new Float32Array(COUNT);
  const aSpeed = new Float32Array(COUNT);
  const aPhase = new Float32Array(COUNT);
  for(let i = 0; i < COUNT; i++){
    pos[i*3]   = (Math.random() * 2 - 1) * SPAN_X;
    pos[i*3+1] = (Math.random() * 2 - 1) * RANGE_Y;
    pos[i*3+2] = Z0 - Math.random() * SPAN_Z;                 // 40 → −200 (deep)
    aScale[i]  = 0.30 + Math.pow(Math.random(), 2.0) * 1.05;   // fine dust, no big blobs
    aSpeed[i]  = 0.4 + Math.random() * 1.3;
    aPhase[i]  = Math.random() * 6.2831;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  geo.setAttribute('aScale',   new THREE.BufferAttribute(aScale, 1));
  geo.setAttribute('aSpeed',   new THREE.BufferAttribute(aSpeed, 1));
  geo.setAttribute('aPhase',   new THREE.BufferAttribute(aPhase, 1));

  const U = {
    uTime:    { value: 0 },
    uScroll:  { value: 0 },                    // 0..1 page scroll
    uMouse:   { value: new THREE.Vector2(0, 0) },
    uSize:    { value: mobile ? 15 : 20 },
    uPix:     { value: renderer.getPixelRatio() },
    uColor:   { value: pal.mote.clone() },
    uFog:     { value: pal.fog.clone() },
    uFogDens: { value: 0.0050 },
    uRangeY:  { value: RANGE_Y },
    uOpacity: { value: 0.0 }
  };

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, depthTest: false,
    blending: THREE.AdditiveBlending,
    uniforms: U,
    vertexShader: `
      attribute float aScale, aSpeed, aPhase;
      uniform float uTime, uScroll, uSize, uPix, uRangeY;
      uniform vec2  uMouse;
      varying float vTw;     // twinkle
      varying float vFog;    // 0 near … 1 far
      uniform float uFogDens;
      void main(){
        vec3 p = position;

        // depth 0 (far) … 1 (near)
        float depth = clamp((p.z + 200.0) / 240.0, 0.0, 1.0);

        // slow luxurious float + a gentle scroll nudge (wrapped, never empties)
        float flow = uTime * aSpeed * 2.4 + uScroll * (32.0 + depth * 130.0);
        p.y = mod(p.y + flow + uRangeY, uRangeY * 2.0) - uRangeY;
        p.x += sin(uTime * 0.22 * aSpeed + aPhase) * 4.0;
        p.z += cos(uTime * 0.16 * aSpeed + aPhase) * 4.0;
        p.x += uMouse.x * (8.0 + depth * 34.0);
        p.y += uMouse.y * (5.0 + depth * 24.0);

        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = min(aScale * uSize * uPix * (180.0 / -mv.z), 34.0 * uPix);

        float dist = -mv.z;
        vFog = clamp(1.0 - exp(-uFogDens * uFogDens * dist * dist), 0.0, 1.0);
        vTw  = 0.62 + 0.38 * sin(uTime * aSpeed * 0.8 + aPhase);   // slow, soft twinkle
      }`,
    fragmentShader: `
      precision highp float;
      uniform vec3  uColor;
      uniform float uOpacity;
      varying float vTw;
      varying float vFog;
      void main(){
        // soft round mote with a brighter core
        vec2 q = gl_PointCoord - 0.5;
        float r = length(q);
        float a = smoothstep(0.5, 0.0, r);
        a *= a;
        if(a < 0.003) discard;
        vec3 col = uColor + vec3(1.0) * pow(a, 3.0) * 0.30;
        gl_FragColor = vec4(col, a * vTw * uOpacity * (1.0 - vFog));
      }`
  });

  const points = new THREE.Points(geo, mat);
  points.frustumCulled = false;
  scene.add(points);

  /* ============================================================
     THEME / ACCENT REACTIVITY — resample + ease
     ============================================================ */
  let tgt = { mote: pal.mote.clone(), fog: pal.fog.clone() };
  function applyPalette(){ pal = buildPalette(); tgt.mote.copy(pal.mote); tgt.fog.copy(pal.fog); }
  new MutationObserver(applyPalette).observe(root, { attributes:true, attributeFilter:['data-theme','data-accent'] });

  /* ============================================================
     INPUT — pointer (parallax) + scroll (flow) + hero
     ============================================================ */
  const m = { tx:0, ty:0, x:0, y:0 };
  window.addEventListener('pointermove', e=>{
    m.tx = (e.clientX / window.innerWidth)  * 2 - 1;
    m.ty = 1 - (e.clientY / window.innerHeight) * 2;
  }, { passive:true });

  let scrollT = 0;
  const heroImg = document.querySelector('.hero .bgwrap img');
  function onScroll(){
    const mx = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    scrollT = window.scrollY / mx;
    if(heroImg && !reduce){ heroImg.style.transform = 'scale(1.08) translateY(' + (window.scrollY * 0.10).toFixed(1) + 'px)'; }
  }
  window.addEventListener('scroll', onScroll, { passive:true }); onScroll();

  window.addEventListener('resize', ()=>{
    renderer.setSize(window.innerWidth, window.innerHeight);
    camera.aspect = window.innerWidth / window.innerHeight; camera.updateProjectionMatrix();
    U.uPix.value = renderer.getPixelRatio();
  });

  /* ============================================================
     RENDER LOOP
     ============================================================ */
  const clock = new THREE.Clock();
  function tick(){
    requestAnimationFrame(tick);
    const dt = Math.min(0.05, clock.getDelta());
    const k  = 1 - Math.pow(0.0022, dt);

    if(!reduce) U.uTime.value += dt;

    // ease palette
    U.uColor.value.lerp(tgt.mote, k);
    U.uFog.value.lerp(tgt.fog, k);

    // fade in — denser on dark themes (a dark page swallows low-alpha motes,
    // so they need more presence to read as glowing dust)
    const dark = root.getAttribute('data-theme') === 'dark';
    const tgtOp = dark ? (mobile ? 0.48 : 0.54) : (mobile ? 0.28 : 0.36);
    U.uOpacity.value += (tgtOp - U.uOpacity.value) * Math.min(1, dt * 1.4);

    // pointer + scroll easing
    m.x += (m.tx - m.x) * Math.min(1, dt * 2.6);
    m.y += (m.ty - m.y) * Math.min(1, dt * 2.6);
    U.uMouse.value.set(m.x, m.y);
    U.uScroll.value += (scrollT - U.uScroll.value) * Math.min(1, dt * 3.0);

    // camera parallax + a shallow dolly so scrolling also flies you forward
    const par = reduce ? 0 : 1;
    camera.position.x += (m.x * 5 * par - camera.position.x) * Math.min(1, dt * 1.8);
    camera.position.y += (m.y * 3.5 * par - camera.position.y) * Math.min(1, dt * 1.8);
    camera.position.z  = 60 - U.uScroll.value * 16;
    camera.lookAt(-m.x * 2 * par, -m.y * 1.4 * par, camera.position.z - 60);

    renderer.render(scene, camera);
  }
  tick();

  // inspection hook (rAF is paused while the doc is backgrounded for capture)
  window.KX3D = {
    U, renderer, scene, camera,
    render(){ renderer.render(scene, camera); },
    snap(){ U.uColor.value.copy(tgt.mote); U.uFog.value.copy(tgt.fog); }
  };
})();
