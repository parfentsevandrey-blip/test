/* ============================================================
   Кутузовский XII — engine3d.js  ·  v2 "living atmosphere"
   ------------------------------------------------------------
   A 3D particle atmosphere (Three.js / WebGL) on the fixed
   #ambient canvas, BEHIND the readable content.

   v2 upgrades over the original dust field:
     · CURL-NOISE FLOW — motes ride a divergence-free curl-noise
       field, so the drift swirls organically instead of looping
       on a sine. Driven on the GPU; free at this count.
     · CURSOR ATTRACTION — a soft world-space pull toward the
       pointer, with falloff, so the field gently gathers where
       you look — like warm air moving past a hand.
     · SCROLL-VELOCITY LIFE — fast scrolling briefly brightens
       and energises the field (more twinkle, longer streak),
       then it settles. The page feels like it has air in it.
     · GATHER AT THE CLOSE — approaching the contact section the
       motes converge toward the centre line, a quiet visual
       resolution as the story ends.
     · PERF — rAF pauses when the tab is hidden OR the canvas is
       scrolled out of view (IntersectionObserver); no
       preserveDrawingBuffer at runtime (re-enabled only for the
       offline capture hook). DPR capped.
     · Theme / accent reactive as before (palette resampled live
       from CSS and eased between themes).
   ============================================================ */
(function(){
  const THREE = window.THREE;
  const canvas = document.getElementById('ambient');
  if(!THREE || !canvas) return;

  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  const mobile = matchMedia('(max-width:760px)').matches;
  const root   = document.documentElement;
  // the offline snapshot tool sets this before capture so the buffer survives toDataURL
  const CAPTURE = !!window.__KX_CAPTURE;

  /* ============================================================
     COLOUR — resolve the live CSS palette to real sRGB
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
    const mote = gold.clone().lerp(new THREE.Color('#ffffff'), 0.44);
    return { fog: bg, mote };
  }
  let pal = buildPalette();

  /* ============================================================
     RENDERER / SCENE / CAMERA
     ============================================================ */
  let renderer;
  try{
    renderer = new THREE.WebGLRenderer({
      canvas, antialias:true, alpha:true, premultipliedAlpha:false,
      preserveDrawingBuffer: CAPTURE, powerPreference:'high-performance'
    });
  }catch(e){ return; }                       // no WebGL → leave the page as-is
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.7));
  renderer.setClearColor(0x000000, 0);
  renderer.setSize(window.innerWidth, window.innerHeight);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth/window.innerHeight, 1, 600);
  camera.position.set(0, 0, 60);

  /* ============================================================
     THE PARTICLE FIELD
     ============================================================ */
  const COUNT = reduce ? 200 : (mobile ? 340 : 560);
  const SPAN_X = 220, RANGE_Y = 160, SPAN_Z = 240, Z0 = 40;

  const pos    = new Float32Array(COUNT * 3);
  const aScale = new Float32Array(COUNT);
  const aSpeed = new Float32Array(COUNT);
  const aPhase = new Float32Array(COUNT);
  for(let i = 0; i < COUNT; i++){
    pos[i*3]   = (Math.random() * 2 - 1) * SPAN_X;
    pos[i*3+1] = (Math.random() * 2 - 1) * RANGE_Y;
    pos[i*3+2] = Z0 - Math.random() * SPAN_Z;
    aScale[i]  = 0.30 + Math.pow(Math.random(), 2.0) * 1.05;
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
    uScroll:  { value: 0 },
    uVel:     { value: 0 },                    // 0..1 scroll energy
    uGather:  { value: 0 },                    // 0..1 converge-to-centre at the close
    uMouse:   { value: new THREE.Vector2(0, 0) },
    uMouseW:  { value: new THREE.Vector3(0, 0, 0) },   // pointer in world space (attraction target)
    uSize:    { value: mobile ? 15 : 20 },
    uPix:     { value: renderer.getPixelRatio() },
    uColor:   { value: pal.mote.clone() },
    uFog:     { value: pal.fog.clone() },
    uFogDens: { value: 0.0050 },
    uRangeY:  { value: RANGE_Y },
    uSpanX:   { value: SPAN_X },
    uOpacity: { value: 0.0 }
  };

  const NOISE_GLSL = `
    vec3 mod289(vec3 x){return x-floor(x*(1.0/289.0))*289.0;}
    vec4 mod289(vec4 x){return x-floor(x*(1.0/289.0))*289.0;}
    vec4 permute(vec4 x){return mod289(((x*34.0)+1.0)*x);}
    vec4 taylorInvSqrt(vec4 r){return 1.79284291400159-0.85373472095314*r;}
    float snoise(vec3 v){
      const vec2 C=vec2(1.0/6.0,1.0/3.0); const vec4 D=vec4(0.0,0.5,1.0,2.0);
      vec3 i=floor(v+dot(v,C.yyy)); vec3 x0=v-i+dot(i,C.xxx);
      vec3 g=step(x0.yzx,x0.xyz); vec3 l=1.0-g;
      vec3 i1=min(g.xyz,l.zxy); vec3 i2=max(g.xyz,l.zxy);
      vec3 x1=x0-i1+C.xxx; vec3 x2=x0-i2+C.yyy; vec3 x3=x0-D.yyy;
      i=mod289(i);
      vec4 p=permute(permute(permute(
            i.z+vec4(0.0,i1.z,i2.z,1.0))
          + i.y+vec4(0.0,i1.y,i2.y,1.0))
          + i.x+vec4(0.0,i1.x,i2.x,1.0));
      float n_=0.142857142857; vec3 ns=n_*D.wyz-D.xzx;
      vec4 j=p-49.0*floor(p*ns.z*ns.z);
      vec4 x_=floor(j*ns.z); vec4 y_=floor(j-7.0*x_);
      vec4 x=x_*ns.x+ns.yyyy; vec4 y=y_*ns.x+ns.yyyy;
      vec4 h=1.0-abs(x)-abs(y);
      vec4 b0=vec4(x.xy,y.xy); vec4 b1=vec4(x.zw,y.zw);
      vec4 s0=floor(b0)*2.0+1.0; vec4 s1=floor(b1)*2.0+1.0;
      vec4 sh=-step(h,vec4(0.0));
      vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy; vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
      vec3 p0=vec3(a0.xy,h.x); vec3 p1=vec3(a0.zw,h.y);
      vec3 p2=vec3(a1.xy,h.z); vec3 p3=vec3(a1.zw,h.w);
      vec4 norm=taylorInvSqrt(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
      p0*=norm.x;p1*=norm.y;p2*=norm.z;p3*=norm.w;
      vec4 m=max(0.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0); m=m*m;
      return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
    }
    vec3 snoiseVec3(vec3 p){
      return vec3(snoise(p),
                  snoise(p+vec3(123.4,256.7,-94.1)),
                  snoise(p+vec3(-45.2,77.3,201.8)));
    }
    vec3 curl(vec3 p){
      const float e=0.55;
      vec3 dx=vec3(e,0.,0.), dy=vec3(0.,e,0.), dz=vec3(0.,0.,e);
      vec3 px0=snoiseVec3(p-dx), px1=snoiseVec3(p+dx);
      vec3 py0=snoiseVec3(p-dy), py1=snoiseVec3(p+dy);
      vec3 pz0=snoiseVec3(p-dz), pz1=snoiseVec3(p+dz);
      float x=(py1.z-py0.z)-(pz1.y-pz0.y);
      float y=(pz1.x-pz0.x)-(px1.z-px0.z);
      float z=(px1.y-px0.y)-(py1.x-py0.x);
      return vec3(x,y,z)/(2.0*e);
    }`;

  const mat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, depthTest: false,
    blending: THREE.AdditiveBlending,
    uniforms: U,
    vertexShader: NOISE_GLSL + `
      attribute float aScale, aSpeed, aPhase;
      uniform float uTime, uScroll, uVel, uGather, uSize, uPix, uRangeY, uSpanX, uFogDens;
      uniform vec2  uMouse;
      uniform vec3  uMouseW;
      varying float vTw;
      varying float vFog;
      void main(){
        vec3 p = position;

        // depth 0 (far) … 1 (near)
        float depth = clamp((p.z + 200.0) / 240.0, 0.0, 1.0);

        // vertical flow through the volume — scroll pulls you upward through it,
        // nearer motes streaming faster (parallax). Wrapped so it never empties.
        float flow = uTime * aSpeed * 2.0 + uScroll * (32.0 + depth * 130.0);
        p.y = mod(p.y + flow + uRangeY, uRangeY * 2.0) - uRangeY;

        // curl-noise advection — organic, divergence-free swirl (energised by scroll)
        vec3 c = curl(p * 0.012 + vec3(0.0, uTime * 0.05, aPhase));
        float swirl = 4.0 + uVel * 7.0;
        p += c * swirl * (0.5 + depth);

        // soft cursor attraction in world space, with falloff
        vec3 toM = uMouseW - p;
        float d2 = dot(toM, toM);
        p += normalize(toM + 1e-4) * (14.0 + depth*22.0) / (1.0 + d2 * 0.012);

        // camera-parallax nudge (kept from v1) + converge-to-centre at the close
        p.x += uMouse.x * (8.0 + depth * 34.0);
        p.y += uMouse.y * (5.0 + depth * 24.0);
        p.x = mix(p.x, p.x * 0.18, uGather);

        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        gl_PointSize = min(aScale * uSize * uPix * (180.0 / -mv.z), 34.0 * uPix);

        float dist = -mv.z;
        vFog = clamp(1.0 - exp(-uFogDens * uFogDens * dist * dist), 0.0, 1.0);
        // twinkle gains a touch of life with scroll energy
        vTw  = (0.62 + 0.38 * sin(uTime * aSpeed * 0.8 + aPhase)) * (1.0 + uVel * 0.5);
      }`,
    fragmentShader: `
      precision highp float;
      uniform vec3  uColor;
      uniform float uOpacity;
      varying float vTw;
      varying float vFog;
      void main(){
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
     THEME / ACCENT REACTIVITY
     ============================================================ */
  let tgt = { mote: pal.mote.clone(), fog: pal.fog.clone() };
  function applyPalette(){ pal = buildPalette(); tgt.mote.copy(pal.mote); tgt.fog.copy(pal.fog); }
  new MutationObserver(applyPalette).observe(root, { attributes:true, attributeFilter:['data-theme','data-accent'] });

  /* ============================================================
     INPUT — pointer (parallax + attraction) + scroll (flow + energy)
     ============================================================ */
  const m = { tx:0, ty:0, x:0, y:0 };
  window.addEventListener('pointermove', e=>{
    m.tx = (e.clientX / window.innerWidth)  * 2 - 1;
    m.ty = 1 - (e.clientY / window.innerHeight) * 2;
  }, { passive:true });

  let scrollT = 0, gatherT = 0, lastSy = window.scrollY || 0, velRaw = 0;
  const heroImg = document.querySelector('.hero .bgwrap img');
  const contact = document.getElementById('contact');
  function onScroll(){
    const sy = window.scrollY || 0;
    const mx = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    scrollT = sy / mx;
    velRaw  = Math.min(1, Math.abs(sy - lastSy) / 60);     // instantaneous scroll energy
    lastSy = sy;
    // converge near the contact section (last ~1.2 viewports)
    if(contact){
      const top = contact.getBoundingClientRect().top;
      gatherT = Math.max(0, Math.min(1, 1 - top / (window.innerHeight * 1.2)));
    }
    // hero photo parallax only survives if the GL hero isn't driving it
    if(heroImg && !reduce && !document.querySelector('.hero.gl-on')){
      heroImg.style.transform = 'scale(1.08) translateY(' + (sy * 0.10).toFixed(1) + 'px)';
    }
  }
  window.addEventListener('scroll', onScroll, { passive:true }); onScroll();

  window.addEventListener('resize', ()=>{
    renderer.setSize(window.innerWidth, window.innerHeight);
    camera.aspect = window.innerWidth / window.innerHeight; camera.updateProjectionMatrix();
    U.uPix.value = renderer.getPixelRatio();
  });

  /* ============================================================
     RUN STATE — pause when hidden or off-screen
     ============================================================ */
  let onScreen = true, running = false, rafId = 0;
  if('IntersectionObserver' in window){
    new IntersectionObserver(es=>{ onScreen = es[0].isIntersecting; pump(); },
      { threshold:0 }).observe(canvas);
  }
  document.addEventListener('visibilitychange', pump);

  /* ============================================================
     RENDER LOOP
     ============================================================ */
  const clock = new THREE.Clock();
  // unproject a screen-space pointer onto the z≈0 plane for world-space attraction
  const _ray = new THREE.Vector3();
  function pointerWorld(nx, ny){
    _ray.set(nx, ny, 0.5).unproject(camera);
    const dir = _ray.sub(camera.position).normalize();
    const t = (0 - camera.position.z) / dir.z;
    return camera.position.clone().add(dir.multiplyScalar(t));
  }

  function tick(){
    if(!running) return;
    rafId = requestAnimationFrame(tick);
    const dt = Math.min(0.05, clock.getDelta());
    const k  = 1 - Math.pow(0.0022, dt);

    if(!reduce) U.uTime.value += dt;

    U.uColor.value.lerp(tgt.mote, k);
    U.uFog.value.lerp(tgt.fog, k);

    const dark = root.getAttribute('data-theme') === 'dark';
    const tgtOp = dark ? (mobile ? 0.48 : 0.54) : (mobile ? 0.28 : 0.36);
    U.uOpacity.value += (tgtOp - U.uOpacity.value) * Math.min(1, dt * 1.4);

    m.x += (m.tx - m.x) * Math.min(1, dt * 2.6);
    m.y += (m.ty - m.y) * Math.min(1, dt * 2.6);
    U.uMouse.value.set(m.x, m.y);
    const w = pointerWorld(m.x, m.y); U.uMouseW.value.copy(w);

    U.uScroll.value += (scrollT - U.uScroll.value) * Math.min(1, dt * 3.0);
    U.uGather.value += (gatherT - U.uGather.value) * Math.min(1, dt * 2.2);
    velRaw *= Math.pow(0.06, dt);                       // decay scroll energy
    U.uVel.value += (velRaw - U.uVel.value) * Math.min(1, dt * 6.0);

    const par = reduce ? 0 : 1;
    camera.position.x += (m.x * 5 * par - camera.position.x) * Math.min(1, dt * 1.8);
    camera.position.y += (m.y * 3.5 * par - camera.position.y) * Math.min(1, dt * 1.8);
    camera.position.z  = 60 - U.uScroll.value * 16;
    camera.lookAt(-m.x * 2 * par, -m.y * 1.4 * par, camera.position.z - 60);

    try{ renderer.render(scene, camera); }
    catch(err){ running = false; cancelAnimationFrame(rafId); }   // shader/context failure → leave page as-is
  }
  function pump(){
    const should = onScreen && !document.hidden;
    if(should && !running){ running = true; clock.getDelta(); tick(); }
    else if(!should && running){ running = false; cancelAnimationFrame(rafId); }
  }
  pump();

  // offline snapshot hook (rAF is paused while the doc is backgrounded for capture)
  window.KX3D = {
    U, renderer, scene, camera,
    render(){ renderer.render(scene, camera); },
    snap(){ U.uColor.value.copy(tgt.mote); U.uFog.value.copy(tgt.fog); }
  };
})();
