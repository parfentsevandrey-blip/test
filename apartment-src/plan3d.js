/* ============================================================
   Кутузовский XII — plan3d.js   ·   light-table sweep
   ------------------------------------------------------------
   A raster floor-plan can't be reliably extruded to true 3D, so
   instead of faking geometry we light it like an architect's light
   table: a soft gold scan-sweep passes across the drawing — once
   when it first scrolls into view, and again on hover — with a
   faint inner glow. The plan itself stays crisp and readable; the
   overlay is purely additive light.

   WebGL via the shared renderer (glshared.js); transparent blit,
   so the real drawing shows straight through. Degrades to nothing.
   ============================================================ */
(function(){
  const KXGL = window.KXGL, THREE = window.THREE;
  if(!KXGL || !THREE) return;
  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  if(reduce) return;

  const frame = document.querySelector('.frame.plan');
  if(!frame) return;
  const PR = KXGL.renderer.getPixelRatio();

  const overlay = document.createElement('canvas');
  overlay.className = 'gl-sweep';
  const ctx = overlay.getContext('2d');
  frame.appendChild(overlay);

  const cam = new THREE.OrthographicCamera(-1,1,1,-1,0,1);
  const scene = new THREE.Scene();
  const U = {
    uTime:{value:0}, uSweep:{value:0}, uGold:{value:new THREE.Color('#caa14e')}, uAspect:{value:1}
  };
  const mat = new THREE.ShaderMaterial({
    transparent:true, uniforms:U,
    vertexShader:`varying vec2 vUv; void main(){ vUv=uv; gl_Position=vec4(position.xy,0.0,1.0); }`,
    fragmentShader:`
      precision highp float;
      varying vec2 vUv; uniform float uTime, uSweep, uAspect; uniform vec3 uGold;
      void main(){
        // diagonal sweep coordinate
        float d = dot(vUv, normalize(vec2(1.0, 0.55)));
        float s = mix(-0.2, 1.3, uSweep);
        float band = smoothstep(0.10, 0.0, abs(d - s));          // crisp leading line
        float halo = smoothstep(0.34, 0.0, abs(d - s)) * 0.5;     // soft trailing glow
        // keep light off the very edges of the frame
        float edge = smoothstep(0.0,0.06,vUv.x)*smoothstep(1.0,0.94,vUv.x)
                   * smoothstep(0.0,0.06,vUv.y)*smoothstep(1.0,0.94,vUv.y);
        float a = (band*0.9 + halo) * edge;
        // a faint, steady centre glow while active
        float glow = (1.0 - length((vUv-0.5)*vec2(uAspect,1.0))) * 0.06 * uSweep * edge;
        vec3 col = uGold * (a + glow);
        gl_FragColor = vec4(col, clamp(a*0.85 + glow, 0.0, 1.0));
      }`
  });
  scene.add(new THREE.Mesh(new THREE.PlaneGeometry(2,2), mat));

  let cssW=0, cssH=0, sweep=0, target=0, hov=false, playing=false;
  function resize(){
    const r = frame.getBoundingClientRect();
    cssW=r.width; cssH=r.height;
    overlay.width = Math.max(1, Math.round(cssW*PR));
    overlay.height = Math.max(1, Math.round(cssH*PR));
    U.uAspect.value = cssW/Math.max(1,cssH);
  }
  function pickGold(){
    try{
      const probe=document.createElement('span');
      probe.style.cssText='position:fixed;left:-9999px;color:var(--gold)';
      document.body.appendChild(probe);
      const c=getComputedStyle(probe).color; probe.remove();
      const mm=c.match(/\d+/g); if(mm) U.uGold.value.setRGB(mm[0]/255,mm[1]/255,mm[2]/255);
    }catch(e){}
  }

  const consumer = { frame: step };
  function step(dt){
    U.uTime.value += dt;
    // advance the sweep; auto-replay while hovered
    sweep += dt * 0.45;
    if(sweep >= 1){
      if(hov) sweep = 0; else { sweep = 1; playing = false; KXGL.deactivate(consumer); resetSoon(); return; }
    }
    U.uSweep.value = sweep;
    KXGL.size(cssW, cssH);
    KXGL.render(scene, cam);
    KXGL.blit(ctx, overlay.width, overlay.height);
  }
  let resetTimer=0;
  function resetSoon(){ clearTimeout(resetTimer); resetTimer=setTimeout(()=>{ overlay.getContext('2d').clearRect(0,0,overlay.width,overlay.height); }, 60); }

  function play(){ if(playing) return; pickGold(); resize(); sweep=0; playing=true; KXGL.activate(consumer); }

  // one sweep when it first enters view
  if('IntersectionObserver' in window){
    const io = new IntersectionObserver(es=>{ if(es[0].isIntersecting){ play(); io.disconnect(); } }, {threshold:0.4});
    io.observe(frame);
  }
  // and again on hover (desktop)
  if(matchMedia('(hover:hover) and (pointer:fine)').matches){
    frame.addEventListener('pointerenter', ()=>{ hov=true; play(); });
    frame.addEventListener('pointerleave', ()=>{ hov=false; });
  }
})();
