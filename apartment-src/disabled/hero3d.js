/* ============================================================
   Кутузовский XII — hero3d.js   ·   the cinematic centrepiece
   ------------------------------------------------------------
   Turns the flat hero photo into a living WebGL plate:

     · 2.5D DEPTH PARALLAX — the façade is sampled through a
       procedural depth field (near at the colonnade base / centre,
       far toward the sky and edges), so cursor + scroll shift the
       building against its background like a real volume.
     · SLOW DOLLY — a continuous, barely-there push-in that
       deepens as you begin to scroll: the shot breathes inward.
     · COALESCE INTRO — on load the image resolves out of warm
       dust: a noise-thresholded develop-in that hands off visually
       from the #ambient mote field.
     · SUN + GOD-RAYS — a soft warm sun is planted behind the
       skyline; post-FX turns it into volumetric light-shafts and
       blooms the bright stone + brass.
     · FILMIC GRADE — bloom, ACES tone curve, split-tone, vignette,
       grain and a touch of chromatic aberration (postfx.js).

   Self-contained second WebGL context on .hero-gl. On ANY failure
   it leaves the original <img> hero untouched.
   ============================================================ */
(function(){
  const THREE = window.THREE;
  if(!THREE || !window.KXPostFX) return;

  const hero   = document.querySelector('.hero');
  const bgwrap = hero && hero.querySelector('.bgwrap');
  const img    = bgwrap && bgwrap.querySelector('img');
  if(!hero || !bgwrap || !img) return;

  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  const root   = document.documentElement;

  // resolve the page colour (for the dust the image emerges from)
  const _pc = document.createElement('canvas'); _pc.width=_pc.height=1;
  const _px = _pc.getContext('2d', {willReadFrequently:true});
  function pageRGB(){
    try{ _px.fillStyle = getComputedStyle(document.body).backgroundColor; _px.fillRect(0,0,1,1);
      const d=_px.getImageData(0,0,1,1).data; return new THREE.Color(d[0]/255,d[1]/255,d[2]/255);
    }catch(e){ return new THREE.Color('#0b0a09'); }
  }

  const canvas = document.createElement('canvas');
  canvas.className = 'hero-gl';
  bgwrap.appendChild(canvas);

  let renderer;
  try{
    renderer = new THREE.WebGLRenderer({ canvas, antialias:false, alpha:false, powerPreference:'high-performance' });
  }catch(e){ canvas.remove(); return; }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.7));

  const scene  = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);

  const U = {
    uFacade:   { value: null },
    uTime:     { value: 0 },
    uMouse:    { value: new THREE.Vector2(0, 0) },
    uScroll:   { value: 0 },
    uIntro:    { value: reduce ? 1 : 0 },
    uAspect:   { value: 1 },
    uImgAspect:{ value: 1 },
    uDust:     { value: pageRGB() },
    uZoom:     { value: 1.06 },
    uParallax: { value: reduce ? 0 : 1 }
  };

  const mat = new THREE.ShaderMaterial({
    uniforms: U,
    vertexShader:`
      varying vec2 vUv;
      void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }`,
    fragmentShader:`
      precision highp float;
      varying vec2 vUv;
      uniform sampler2D uFacade;
      uniform float uTime, uScroll, uIntro, uAspect, uImgAspect, uZoom, uParallax;
      uniform vec2 uMouse; uniform vec3 uDust;

      float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }
      float vnoise(vec2 p){
        vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.0-2.0*f);
        return mix(mix(hash(i),hash(i+vec2(1,0)),u.x),
                   mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y);
      }
      // procedural depth: 0 far (sky/edges) … 1 near (colonnade base / centre)
      float depthAt(vec2 uv){
        float vert   = 1.0 - uv.y;                       // bottom is nearer
        float center = 1.0 - abs(uv.x - 0.5) * 1.4;      // central bays nearer
        float d = clamp(vert*0.62 + center*0.38, 0.0, 1.0);
        return smoothstep(0.0, 1.0, d);
      }
      // cover-fit the photo to the canvas, with a slow zoom (dolly)
      vec2 coverUV(vec2 uv){
        vec2 c = uv - 0.5;
        float ar = uAspect / uImgAspect;
        if(ar > 1.0) c.y /= ar; else c.x *= ar;
        c /= uZoom;
        return c + 0.5;
      }
      void main(){
        vec2 uv = coverUV(vUv);
        float depth = depthAt(uv);

        // 2.5D parallax: near pixels shift more with cursor + scroll
        vec2 shift = (uMouse * 0.045 + vec2(0.0, uScroll * 0.05)) * uParallax;
        vec2 suv = uv + shift * (depth - 0.35);
        suv = clamp(suv, 0.001, 0.999);

        vec3 col = texture2D(uFacade, suv).rgb;
        col = pow(col, vec3(2.2));                        // → ~linear for the post grade

        // soft warm sun planted behind the skyline (drives bloom + god-rays)
        vec2 sun = vec2(0.62, 0.86);
        float sd = distance(vUv * vec2(uAspect,1.0), sun * vec2(uAspect,1.0));
        col += vec3(1.0, 0.82, 0.55) * smoothstep(0.42, 0.0, sd) * 0.45;

        // COALESCE INTRO — image develops out of warm dust
        float n = vnoise(vUv * 7.0) * 0.6 + vnoise(vUv * 23.0) * 0.4;
        float edge = smoothstep(n - 0.12, n + 0.12, uIntro);
        vec3 dust = pow(uDust, vec3(2.2)) + vec3(0.05,0.035,0.02) * (1.0 - edge);
        // a little settle-jitter while resolving
        col = mix(dust, col, edge);

        gl_FragColor = vec4(col, 1.0);
      }`
  });

  const quad = new THREE.Mesh(new THREE.PlaneGeometry(2,2), mat);
  quad.frustumCulled = false;
  scene.add(quad);

  const fx = window.KXPostFX.create(renderer, {
    bloomStrength: 0.95, threshold: 0.58, godStrength: 0.6,
    vignette: 0.36, grain: 0.045, chroma: 1.1, exposure: 1.08, tint: 0.12
  });
  fx.setSun(0.62, 0.86);

  /* ---------- size to the hero element ---------- */
  function size(){
    const w = bgwrap.clientWidth || window.innerWidth;
    const h = bgwrap.clientHeight || window.innerHeight;
    renderer.setSize(w, h, false);
    fx.setSize(w, h);
    U.uAspect.value = w / h;
  }

  /* ---------- input ---------- */
  const m = { tx:0, ty:0, x:0, y:0 };
  if(!reduce){
    window.addEventListener('pointermove', e=>{
      m.tx = (e.clientX/window.innerWidth)*2 - 1;
      m.ty = (e.clientY/window.innerHeight)*2 - 1;
    }, {passive:true});
  }
  let scrollT = 0;
  function onScroll(){
    const h = bgwrap.clientHeight || window.innerHeight;
    scrollT = Math.max(0, Math.min(1, (window.scrollY || 0) / h));
  }
  window.addEventListener('scroll', onScroll, {passive:true}); onScroll();
  window.addEventListener('resize', size);

  new MutationObserver(()=> U.uDust.value.copy(pageRGB()))
    .observe(root, { attributes:true, attributeFilter:['data-theme','data-accent'] });

  /* ---------- run only while the hero is on screen ---------- */
  let onScreen = true, running = false, rafId = 0, t0 = 0;
  if('IntersectionObserver' in window){
    new IntersectionObserver(es=>{ onScreen = es[0].isIntersecting; pump(); }, {threshold:0}).observe(hero);
  }
  document.addEventListener('visibilitychange', pump);

  const clock = new THREE.Clock();
  function tick(){
    if(!running) return;
    rafId = requestAnimationFrame(tick);
    const dt = Math.min(0.05, clock.getDelta());
    U.uTime.value += dt;
    fx.setTime(U.uTime.value);

    if(U.uIntro.value < 1){
      U.uIntro.value = Math.min(1, U.uIntro.value + dt / 1.9);  // ~1.9s develop-in
    }
    m.x += (m.tx - m.x) * Math.min(1, dt*2.4);
    m.y += (m.ty - m.y) * Math.min(1, dt*2.4);
    U.uMouse.value.set(m.x, m.y);
    U.uScroll.value += (scrollT - U.uScroll.value) * Math.min(1, dt*3.0);

    // dolly: slow continuous push-in, deepened by scroll
    const targetZoom = 1.06 + scrollT*0.10 + 0.04*Math.sin(U.uTime.value*0.05);
    U.uZoom.value += (targetZoom - U.uZoom.value) * Math.min(1, dt*0.8);

    try{ fx.render(scene, camera); }
    catch(err){ failHero(); }            // e.g. a texture-upload error → revert to the photo
  }
  function failHero(){
    running = false; cancelAnimationFrame(rafId);
    hero.classList.remove('gl-on');
    try{ fx.dispose(); }catch(e){}
    try{ renderer.dispose(); }catch(e){}
    canvas.remove();
  }
  function pump(){
    const should = onScreen && !document.hidden && U.uFacade.value;
    if(should && !running){ running = true; clock.getDelta(); tick(); }
    else if(!should && running){ running = false; cancelAnimationFrame(rafId); }
  }

  /* ---------- load the façade texture, then go live ---------- */
  const loader = new THREE.TextureLoader();
  const src = img.currentSrc || img.src;
  loader.load(src, tex=>{
    tex.minFilter = THREE.LinearFilter; tex.magFilter = THREE.LinearFilter;
    tex.generateMipmaps = false; tex.wrapS = tex.wrapT = THREE.ClampToEdgeWrapping;
    const iw = (tex.image && tex.image.width) || 16, ih = (tex.image && tex.image.height) || 9;
    U.uImgAspect.value = iw / ih;
    U.uFacade.value = tex;
    size();
    hero.classList.add('gl-on');     // CSS fades the <img> out; engine3d drops its img-parallax
    pump();
  }, undefined, ()=>{ /* load failed → leave the photo hero as-is */ fx.dispose(); renderer.dispose(); canvas.remove(); });

  window.KXHero = { U, fx, render(){ fx.render(scene, camera); } };
})();
