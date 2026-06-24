/* ============================================================
   Кутузовский XII — materials3d.js   ·   real materials
   ------------------------------------------------------------
   The four accent swatches (Бронза · Мрамор · Хрусталь · Шпон)
   are abstract CSS gradients. On hover, this floats a small live
   PBR preview above the swatch — an actual physically-shaded
   sphere (metal / stone / glass / lacquered wood) lit by the
   procedural environment — so "material" becomes literal.

   Shares the one renderer (glshared.js); renders only while a
   swatch is hovered. Fine pointer only; degrades to nothing.
   ============================================================ */
(function(){
  const KXGL = window.KXGL, THREE = window.THREE;
  if(!KXGL || !THREE) return;
  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  const fine   = matchMedia('(hover:hover) and (pointer:fine)').matches;
  if(reduce || !fine) return;

  const PR = KXGL.renderer.getPixelRatio();

  const PRESET = {
    bronze:  { label:'Бронза',  color:'#b5811c', metalness:1.0, roughness:0.26, clearcoat:0.0, transmission:0.0, ior:1.4 },
    marble:  { label:'Мрамор',  color:'#e9ecee', metalness:0.0, roughness:0.16, clearcoat:1.0, transmission:0.0, ior:1.5 },
    crystal: { label:'Хрусталь',color:'#cdeefb', metalness:0.0, roughness:0.04, clearcoat:0.0, transmission:0.92, ior:1.52 },
    veneer:  { label:'Шпон',    color:'#a85d28', metalness:0.0, roughness:0.42, clearcoat:0.6, transmission:0.0, ior:1.45 }
  };

  // floating popover
  const pop = document.createElement('div');
  pop.className = 'mat-preview';
  const cv = document.createElement('canvas');
  const cap = document.createElement('span'); cap.className = 'mat-preview-cap';
  pop.appendChild(cv); pop.appendChild(cap);
  document.body.appendChild(pop);
  const ctx = cv.getContext('2d');
  const SIZE = 150;
  cv.width = Math.round(SIZE*PR); cv.height = Math.round(SIZE*PR);

  // preview scene
  const scene = new THREE.Scene();
  scene.environment = KXGL.env || null;
  const cam = new THREE.PerspectiveCamera(30, 1, 0.1, 50);
  cam.position.set(0, 0, 4.2);

  const sphere = new THREE.Mesh(
    new THREE.SphereGeometry(1, 64, 48),
    new THREE.MeshPhysicalMaterial({ color:0xffffff, metalness:1, roughness:0.3, envMap:KXGL.env || null, envMapIntensity:1.1 })
  );
  scene.add(sphere);
  // soft backdrop disc so glass has something to refract and metals get contrast
  const disc = new THREE.Mesh(
    new THREE.CircleGeometry(2.6, 48),
    new THREE.MeshBasicMaterial({ color:0x1a1712, transparent:true, opacity:0.85 })
  );
  disc.position.z = -1.6; scene.add(disc);
  const key = new THREE.DirectionalLight(0xfff0d8, 1.4); key.position.set(3,4,5); scene.add(key);
  const fill = new THREE.DirectionalLight(0x88a0c0, 0.5); fill.position.set(-4,-1,2); scene.add(fill);

  function setMaterial(name){
    const p = PRESET[name] || PRESET.bronze;
    const mm = sphere.material;
    mm.color.set(p.color); mm.metalness = p.metalness; mm.roughness = p.roughness;
    mm.clearcoat = p.clearcoat; mm.clearcoatRoughness = 0.2;
    mm.transmission = p.transmission; mm.ior = p.ior;
    mm.thickness = p.transmission ? 1.2 : 0; mm.transparent = p.transmission > 0;
    mm.envMapIntensity = p.metalness ? 1.25 : 1.0;
    mm.needsUpdate = true;
    cap.textContent = p.label;
  }

  let hov=false, fade=0, rot=0;
  const consumer = { frame: step };
  function step(dt){
    rot += dt * 0.5;
    sphere.rotation.y = rot; sphere.rotation.x = Math.sin(rot*0.4)*0.18;
    const tf = hov ? 1 : 0; fade += (tf - fade) * Math.min(1, dt*8);
    KXGL.size(SIZE, SIZE);
    KXGL.render(scene, cam);
    KXGL.blit(ctx, cv.width, cv.height);
    pop.style.opacity = fade.toFixed(3);
    pop.style.transform = `translate(-50%,0) scale(${(0.9+0.1*fade).toFixed(3)})`;
    if(!hov && fade < 0.01){ pop.style.opacity='0'; pop.style.visibility='hidden'; KXGL.deactivate(consumer); }
  }

  function place(btn){
    const r = btn.getBoundingClientRect();
    pop.style.left = (r.left + r.width/2) + 'px';
    pop.style.top  = (r.bottom + 12) + 'px';
    pop.style.visibility = 'visible';
  }

  document.querySelectorAll('.mat-switch button').forEach(btn=>{
    btn.addEventListener('pointerenter', ()=>{
      const name = btn.dataset.accent;
      setMaterial(name); place(btn); hov = true; KXGL.activate(consumer);
    });
    btn.addEventListener('pointerleave', ()=>{ hov = false; });
  });
})();
