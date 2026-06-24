/* ============================================================
   Кутузовский XII — glshared.js
   ------------------------------------------------------------
   ONE shared WebGL renderer for every on-demand 3D widget
   (gallery depth-parallax, PBR material previews, plan plate),
   so the page never approaches the browser's WebGL-context limit.

   Each consumer renders its own scene into this offscreen GL
   canvas, then blits the pixels into its own 2D <canvas> overlay
   with drawImage(). A single rAF drives only the consumers that
   are currently "active" (hovered / in view); it sleeps otherwise.

   Also builds a procedural PMREM environment once, so the material
   previews get real image-based reflections without bundling an HDR.

   Exposes window.KXGL (or null if WebGL is unavailable).
   ============================================================ */
(function(){
  const THREE = window.THREE;
  if(!THREE){ window.KXGL = null; return; }

  const glCanvas = document.createElement('canvas');
  let renderer;
  try{
    renderer = new THREE.WebGLRenderer({
      canvas: glCanvas, antialias:true, alpha:true,
      premultipliedAlpha:true, preserveDrawingBuffer:true,
      powerPreference:'high-performance'
    });
  }catch(e){ window.KXGL = null; return; }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6));
  renderer.setClearColor(0x000000, 0);
  if('outputEncoding' in renderer) renderer.outputEncoding = THREE.sRGBEncoding;
  if('toneMapping'   in renderer){ renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05; }

  /* ---------- procedural environment (PMREM) ---------- */
  let env = null;
  try{
    const envScene = new THREE.Scene();
    // gradient sky dome
    const sky = new THREE.Mesh(
      new THREE.SphereGeometry(50, 24, 16),
      new THREE.ShaderMaterial({
        side: THREE.BackSide,
        uniforms:{ top:{value:new THREE.Color('#cfd6e2')}, bot:{value:new THREE.Color('#2a2620')} },
        vertexShader:`varying vec3 vN; void main(){ vN = normalize(position); gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0); }`,
        fragmentShader:`varying vec3 vN; uniform vec3 top, bot; void main(){ float t = clamp(vN.y*0.5+0.5,0.0,1.0); gl_FragColor = vec4(mix(bot, top, t), 1.0); }`
      })
    );
    envScene.add(sky);
    // warm key "window" + cool fill, as emissive panels
    const key = new THREE.Mesh(new THREE.PlaneGeometry(18,26),
      new THREE.MeshBasicMaterial({ color:new THREE.Color('#fff0d6') }));
    key.position.set(16, 8, 10); key.lookAt(0,0,0); envScene.add(key);
    const fill = new THREE.Mesh(new THREE.PlaneGeometry(22,22),
      new THREE.MeshBasicMaterial({ color:new THREE.Color('#7e93ad') }));
    fill.position.set(-18, 2, -6); fill.lookAt(0,0,0); envScene.add(fill);

    const pmrem = new THREE.PMREMGenerator(renderer);
    pmrem.compileEquirectangularShader();
    env = pmrem.fromScene(envScene, 0, 0.1, 100).texture;
    sky.geometry.dispose(); sky.material.dispose();
    key.geometry.dispose(); key.material.dispose();
    fill.geometry.dispose(); fill.material.dispose();
  }catch(e){ env = null; }

  /* ---------- shared frame scheduler ---------- */
  const active = new Set();
  let running = false, raf = 0, last = 0;
  function loop(t){
    if(!running) return;
    raf = requestAnimationFrame(loop);
    const dt = last ? Math.min(0.05, (t - last)/1000) : 0.016; last = t;
    active.forEach(c=>{ try{ c.frame(dt); }catch(e){ active.delete(c); } });
    if(!active.size){ running = false; last = 0; }
  }
  function wake(){ if(!running && active.size){ running = true; last = 0; raf = requestAnimationFrame(loop); } }

  document.addEventListener('visibilitychange', ()=>{
    if(document.hidden){ running = false; cancelAnimationFrame(raf); last = 0; }
    else wake();
  });

  window.KXGL = {
    THREE, renderer, env,
    // size the shared GL canvas (device px); call before render()
    size(w, h){ renderer.setSize(Math.max(1,w|0), Math.max(1,h|0), false); },
    render(scene, camera){ renderer.setRenderTarget(null); renderer.render(scene, camera); },
    // blit the current GL pixels into a destination 2D context
    blit(ctx, w, h){ try{ ctx.clearRect(0,0,w,h); ctx.drawImage(glCanvas, 0,0, w,h); }catch(e){} },
    canvas: glCanvas,
    activate(consumer){ active.add(consumer); wake(); },     // consumer = { frame(dt) }
    deactivate(consumer){ active.delete(consumer); }
  };
})();
