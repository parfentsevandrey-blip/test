/* ============================================================
   Кутузовский XII — site.js
   theme · float-bar · active nav · elegant one-shot reveals
   (fade / rise / settle — never clips text) · gentle parallax ·
   count-up · magnetic buttons · image tilt
   ============================================================ */
(function(){
  const root = document.documentElement; root.classList.add('js');
  const STORE = 'kutuzovsky-theme';
  const reduce = window.matchMedia('(prefers-reduced-motion:reduce)').matches;
  const fine = window.matchMedia('(hover:hover) and (pointer:fine)').matches;

  /* ---------- THEME ---------- */
  const themes = ['light','evening','dark'];
  function applyTheme(t){
    root.setAttribute('data-theme', t);
    try{ localStorage.setItem(STORE,t); }catch(e){}
    document.querySelectorAll('.theme-switch button').forEach(b=>{
      const on = b.dataset.theme===t; b.classList.toggle('active',on); b.setAttribute('aria-pressed',on);
    });
    syncBrowserChrome();
  }
  // keep the mobile browser chrome (address-bar tint) in step with the theme/accent
  let _tcMeta = document.querySelector('meta[name="theme-color"]');
  function syncBrowserChrome(){
    if(!_tcMeta){ _tcMeta = document.createElement('meta'); _tcMeta.setAttribute('name','theme-color'); document.head.appendChild(_tcMeta); }
    // read the RESOLVED page colour (body bg) so the meta is a concrete rgb(),
    // not an unparseable color-mix() string — mobile chrome needs a real colour.
    const c = getComputedStyle(document.body).backgroundColor;
    if(c && c!=='transparent' && c!=='rgba(0, 0, 0, 0)') _tcMeta.setAttribute('content', c);
  }
  // slow, gentle S-curve — eases in and out so nothing races
  const EASE = 'cubic-bezier(.42,.02,.16,1)';
  // ============================================================
  //  THEME & ACCENT SWITCH ENGINE
  //  A richer, layered transition radiating from the toggle: a colour
  //  bloom in the new hue, twin light-rings on the wavefront, and a
  //  soft luminance pulse — over a themewide surface cross-fade.
  //  Independent self-cleaning nodes, so rapid clicks just stack.
  // ============================================================
  const ACCENT_HUE = { bronze:'#caa14e', marble:'#7fa6b4', crystal:'#3aa9dd', veneer:'#c07a3f' };
  function curGold(){ return getComputedStyle(root).getPropertyValue('--gold').trim() || '#caa14e'; }
  function ignite(x, y, color){
    // cap concurrent overlay nodes so frantic clicking can't pile up hundreds
    const live = document.querySelectorAll('.sw-bloom,.sw-ring,.sw-flash');
    if(live.length > 12){ for(let i=0; i<live.length-8; i++) live[i].remove(); }
    const end = Math.hypot(Math.max(x, innerWidth-x), Math.max(y, innerHeight-y));
    // colour bloom — a wash of the new hue expanding from the toggle
    const bloom = document.createElement('div');
    bloom.className = 'sw-bloom'; bloom.style.left = x+'px'; bloom.style.top = y+'px';
    bloom.style.setProperty('--c', color);
    document.body.appendChild(bloom);
    bloom.animate(
      [ {width:'0px',height:'0px',opacity:0},
        {opacity:.42, offset:.18},
        {width:(end*2.3)+'px',height:(end*2.3)+'px',opacity:0} ],
      { duration:1600, easing:EASE }
    ).finished.then(()=>bloom.remove(), ()=>bloom.remove());
    // twin rings riding the wavefront
    for(let i=0;i<2;i++){
      const ring = document.createElement('div');
      ring.className = 'sw-ring'; ring.style.left = x+'px'; ring.style.top = y+'px';
      ring.style.setProperty('--c', color);
      document.body.appendChild(ring);
      ring.animate(
        [ {width:'0px',height:'0px',opacity:0},
          {opacity:.7, offset:.16},
          {width:(end*2.1)+'px',height:(end*2.1)+'px',opacity:0} ],
        { duration:1750, delay:i*150, easing:EASE }
      ).finished.then(()=>ring.remove(), ()=>ring.remove());
    }
    // luminance pulse — the scene briefly breathes brighter
    const flash = document.createElement('div');
    flash.className = 'sw-flash';
    flash.style.setProperty('--cx', x+'px'); flash.style.setProperty('--cy', y+'px');
    flash.style.setProperty('--c', color);
    document.body.appendChild(flash);
    flash.animate(
      [ {opacity:0}, {opacity:.16, offset:.3}, {opacity:0} ],
      { duration:1100, easing:EASE }
    ).finished.then(()=>flash.remove(), ()=>flash.remove());
  }
  // tactile press: a quick spring-back depress + a gold ripple inside the button
  function pressFX(b){
    b.animate(
      [ {transform:'scale(1)'}, {transform:'scale(.84)', offset:.34}, {transform:'scale(1)'} ],
      { duration:440, easing:'cubic-bezier(.34,1.56,.64,1)' }
    );
    const r = document.createElement('span');
    r.className = 'press-ring';
    b.appendChild(r);
    r.animate(
      [ {transform:'scale(.25)', opacity:.7}, {transform:'scale(2.6)', opacity:0} ],
      { duration:600, easing:EASE }
    ).finished.then(()=>r.remove(), ()=>r.remove());
  }
  let themeTimer=null;
  function setTheme(t, origin){
    if(!themes.includes(t)) t='light';
    if(t === root.getAttribute('data-theme')) return;
    if(reduce){ applyTheme(t); return; }
    const x = origin ? origin.x : innerWidth/2;
    const y = origin ? origin.y : 24;
    root.classList.add('theming');
    clearTimeout(themeTimer);
    themeTimer = setTimeout(()=> root.classList.remove('theming'), 1000);
    ignite(x, y, curGold());
    applyTheme(t);
  }
  let saved='light'; try{ saved = localStorage.getItem(STORE)||'light'; }catch(e){}
  applyTheme(saved);

  /* ---------- MATERIAL ACCENT ---------- */
  const ASTORE='kutuzovsky-accent';
  const accents=['bronze','marble','crystal','veneer'];
  function applyAccent(a){
    root.setAttribute('data-accent', a);
    try{ localStorage.setItem(ASTORE,a); }catch(e){}
    document.querySelectorAll('.mat-switch button').forEach(b=>{
      const on=b.dataset.accent===a; b.classList.toggle('active',on); b.setAttribute('aria-pressed',on);
    });
    syncBrowserChrome();
  }
  let accentTimer=null;
  function setAccent(a, origin){
    if(!accents.includes(a)) a='bronze';
    if(a === root.getAttribute('data-accent')) return;
    if(reduce){ applyAccent(a); return; }
    const x = origin ? origin.x : innerWidth/2;
    const y = origin ? origin.y : 24;
    root.classList.add('theming');
    clearTimeout(accentTimer);
    accentTimer = setTimeout(()=> root.classList.remove('theming'), 1000);
    ignite(x, y, ACCENT_HUE[a] || curGold());
    applyAccent(a);
  }
  let savedA='bronze'; try{ savedA = localStorage.getItem(ASTORE)||'bronze'; }catch(e){}
  applyAccent(savedA);

  document.addEventListener('click', e=>{
    const tb=e.target.closest('.theme-switch button');
    if(tb){ pressFX(tb); const r=tb.getBoundingClientRect(); setTheme(tb.dataset.theme,{x:r.left+r.width/2,y:r.top+r.height/2}); return; }
    const ab=e.target.closest('.mat-switch button');
    if(ab){ pressFX(ab); const r=ab.getBoundingClientRect(); setAccent(ab.dataset.accent,{x:r.left+r.width/2,y:r.top+r.height/2}); }
  });

  /* ---------- float bar ---------- */
  const bar = document.querySelector('.bar');
  function onNav(){ if(bar) bar.classList.toggle('scrolled', window.scrollY>30); }
  onNav(); window.addEventListener('scroll', onNav, {passive:true});

  /* ---------- active nav ---------- */
  const sections = [...document.querySelectorAll('main section[id]')];
  const links = [...document.querySelectorAll('.nav-links a')];
  function activeNav(){
    const mid = window.innerHeight*0.38; let cur=sections[0];
    for(const s of sections){ if(s.getBoundingClientRect().top<=mid) cur=s; }
    if(cur) links.forEach(a=> a.classList.toggle('active', a.getAttribute('href')==='#'+cur.id));
  }
  activeNav(); window.addEventListener('scroll', activeNav, {passive:true});

  /* ============================================================
     PREP — tag elements for the reveal engine
     ============================================================ */
  // sec-head: stagger eyebrow / heading / lead
  document.querySelectorAll('.sec-head[data-reveal]').forEach(h=>{
    h.removeAttribute('data-reveal');
    [...h.children].forEach((k,i)=>{ k.classList.add('reveal'); k.dataset.d=i; });
  });
  // col-media wrappers don't animate themselves — they carry parallax + 3D tilt;
  // the framed image inside handles the reveal.
  document.querySelectorAll('.col-media[data-reveal]').forEach(c=>{ c.removeAttribute('data-reveal'); c.dataset.px='media'; });
  // text columns drift the opposite way for layered depth separation
  document.querySelectorAll('.col-body').forEach(c=>{ c.dataset.px='text'; });
  // big showcase media (floor plan) gets the 3D media treatment too
  document.querySelectorAll('.showcase > .frame').forEach(c=>{ c.dataset.px='media'; });
  // statement backdrop pushes deep behind the copy
  document.querySelectorAll('.statement .bgwrap').forEach(c=>{ c.dataset.px='bg'; });
  // remaining generic blocks (stats, deflists, tags, specs, statement copy, cards…)
  document.querySelectorAll('[data-reveal]').forEach(el=>{ el.classList.add('reveal'); el.removeAttribute('data-reveal'); });
  // framed media + gallery cells -> soft image settle (no clip)
  document.querySelectorAll('.frame, .cell').forEach(f=> f.classList.add('reveal','r-img'));
  // cascade gallery cells within each grid
  document.querySelectorAll('.grid').forEach(g=>{
    [...g.querySelectorAll('.cell')].forEach((c,i)=> c.dataset.d=i);
  });

  /* ============================================================
     CONTINUOUS ANIMATION ENGINE
     Reversible reveals + parallax, recomputed every frame from the
     live scroll position (the ground truth) — so it works scrolling
     down, scrolling up, and at rest, and can never get stuck in a
     one-shot state. Same philosophy as the theme-switch: derive the
     visual from truth each tick instead of latching a flag.
     ============================================================ */
  const reveals = [...document.querySelectorAll('.reveal')].map(el=>{
    const img = el.classList.contains('r-img');
    return { el, d:(+el.dataset.d||0), img, anim:null, top:0, h:0, cur:0, tau:(img?0.17:0.11) };
  });

  // cache document-relative geometry via the offsetTop chain — this is the
  // LAYOUT position, immune to the transforms the engine itself applies (so it
  // never feeds back on itself). Recomputed on resize / image load / font settle.
  function docTop(el){ let y=0, n=el; while(n){ y += n.offsetTop||0; n = n.offsetParent; } return y; }
  function measure(){
    for(const o of reveals){ o.top = docTop(o.el); o.h = o.el.offsetHeight; }
  }

  // smooth, symmetric ease — same shape entering and leaving
  const easeIO = t => t<0.5 ? 4*t*t*t : 1 - Math.pow(-2*t+2, 3)/2;
  // scroll-velocity state (drives motion-blur / drag / stretch)
  let lastSy = window.scrollY||0, vel = 0, lastT = performance.now();

  function frame(){
    const sy = window.scrollY || window.pageYOffset || 0;
    const vh = window.innerHeight;
    const damp = window.innerWidth < 860 ? 0.5 : 1;

    // frame-rate-independent timing + smoothed scroll velocity
    const now = performance.now();
    let dt = (now - lastT)/1000; lastT = now; if(dt>0.05) dt=0.05; if(dt<=0) dt=0.016;
    const instV = sy - lastSy; lastSy = sy;
    vel += (instV - vel) * 0.3;
    const speed = Math.min(1, Math.abs(vel)/55);     // 0..1, how fast we're moving
    const vdir  = vel >= 0 ? 1 : -1;

    /* SYMMETRIC PRESENCE + INERTIA — a bell curve over the viewport gives the
       target; each element's value then EASES toward it (weighted, images
       heavier) so motion has physical momentum, not a 1:1 scroll lock. Scroll
       speed adds motion-blur, a directional drag and a faint stretch — but only
       to elements mid-transition (scaled by 1-e), so the reading zone stays
       crisp. Identical entering top or bottom -> scroll up and down match. */
    const cen  = vh*0.5;
    const plat = vh*0.34;          // half-width of the fully-present plateau
    const fade = vh*0.32;          // distance over which it fades at each edge
    for(const o of reveals){
      const cy = (o.top - sy) + o.h*0.5;              // true element centre (viewport-rel)
      let a = 1 - (Math.abs(cy - cen) - plat + o.d*16)/fade;
      a = a<0?0:a>1?1:a;
      const target = a<1 ? easeIO(a) : 1;
      o.cur += (target - o.cur) * (1 - Math.exp(-dt / o.tau));   // inertial smoothing
      const e = o.cur;
      const sign = cy >= cen ? 1 : -1;                // below -> rise up; above -> keep rising
      if(e >= 0.997){
        if(o.anim !== 1){ o.el.style.opacity='1'; o.el.style.transform=''; o.el.style.filter=''; o.anim=1; }
      } else if(e <= 0.004 && target === 0){
        // settled fully-hidden — write the static end-state once, then skip
        if(o.anim !== sign*2){
          o.el.style.opacity='0';
          o.el.style.transform='translate3d(0,'+(sign*36)+'px,0) scale(0.96)';
          o.el.style.filter='blur(5px)';
          o.anim = sign*2;
        }
      } else {
        const m = 1 - e;
        const rise = sign*m*34 + vdir*speed*m*46;     // base lift + velocity drag
        const sc   = 0.96 + e*0.04;                   // grow into place
        const sYf  = 1 + speed*m*0.05;                // motion stretch while moving fast
        o.el.style.opacity = e.toFixed(3);
        o.el.style.transform = 'translate3d(0,'+rise.toFixed(1)+'px,0) scale('+sc.toFixed(4)+','+(sc*sYf).toFixed(4)+')';
        o.el.style.filter = 'blur('+(m*4.5 + speed*m*5).toFixed(2)+'px)';
        o.anim = 0;
      }
    }
  }

  function loop(){ frame(); requestAnimationFrame(loop); }

  /* ---------- count-up ---------- */
  function fmt(n,dec){ return dec? n.toFixed(1).replace('.', ',') : Math.round(n).toString(); }
  function runCount(c){
    if(c.done) return; c.done=true; const dur=1200, t0=performance.now();
    (function step(t){
      const k = Math.min(1, (t-t0)/dur); const e = 1-Math.pow(1-k,3);
      c.tn.textContent = fmt(c.num*e, c.dec);
      if(k<1) requestAnimationFrame(step); else c.tn.textContent = fmt(c.num, c.dec);
    })(t0);
    setTimeout(()=>{ c.tn.textContent = fmt(c.num, c.dec); }, dur+450);
  }
  const counters = [...document.querySelectorAll('.stat .v')].map(v=>{
    const tn = [...v.childNodes].find(n=>n.nodeType===3 && n.textContent.trim());
    if(!tn) return null;
    const raw = tn.textContent.trim();
    const num = parseFloat(raw.replace(/\s/g,'').replace(',', '.'));
    if(isNaN(num)) return null;
    return { tn, num, dec:(raw.indexOf(',')>=0?1:0), done:false, el:v };
  }).filter(Boolean);
  if(reduce){
    counters.forEach(c=>{ c.tn.textContent = fmt(c.num,c.dec); });
  } else {
    const cmap = new Map(counters.map(c=>[c.el,c]));
    const cio = new IntersectionObserver((entries)=>{
      for(const e of entries){ if(e.isIntersecting){ const c=cmap.get(e.target); if(c){ runCount(c); cio.unobserve(e.target); } } }
    }, { threshold:0.5 });
    counters.forEach(c=> cio.observe(c.el));
  }

  /* ---------- start the continuous engine ---------- */
  if(!reduce){
    measure();
    window.addEventListener('resize', measure, {passive:true});
    window.addEventListener('load', measure);
    document.querySelectorAll('img').forEach(img=>{ if(!img.complete) img.addEventListener('load', measure, {once:true}); });
    setTimeout(measure, 400); setTimeout(measure, 1200);
    loop();   // persistent rAF — always running, always reconciling to scroll truth
  }

  /* ============================================================
     HOVER ENGINE — a single rich, 3D-feeling pointer response for
     every interactive element. Cursor-aware tilt + lift + a soft
     light glare that tracks the pointer, all eased (lerped) so it
     feels weighted, never twitchy. Intensity is tuned per element
     type so it reads as premium, not noisy. Desktop + fine pointer
     only; respects reduced-motion. Runs only for elements actually
     being hovered, so it costs nothing at rest.
     ============================================================ */
  if(fine && !reduce){
    const SEL = '.cta, .frame, .cell, .stat, .spec, .tagp, .glass, .contact-card, .theme-switch button, .mat-switch button, .nav-links a, .brand, .deflist .di';
    // {tilt:deg, lift:px, z:translateZ px (pop toward viewer), glare:0..1, shadow:bool}
    const conf = el =>
      el.matches('.stat, .spec')                              ? {tilt:6,   lift:6, z:38, glare:.18, shadow:1} :
      el.matches('.frame, .cell, .contact-card, .glass')      ? {tilt:4.5, lift:5, z:34, glare:.16, shadow:1} :
      el.matches('.cta')                                      ? {tilt:7,   lift:3, z:30, glare:.22, shadow:1} :
      el.matches('.theme-switch button, .mat-switch button')  ? {tilt:9,   lift:2, z:18, glare:0,   shadow:0} :
      el.matches('.tagp')                                     ? {tilt:6,   lift:2, z:14, glare:0,   shadow:0} :
                                                                {tilt:0,   lift:3, z:0,  glare:0,   shadow:0}; // nav links, brand, di
    // transform-immune document position (offset chain ignores CSS transforms)
    const offsetPos = el => { let x=0,y=0,n=el; while(n){ x+=n.offsetLeft||0; y+=n.offsetTop||0; n=n.offsetParent; } return {x,y}; };
    const active = new Map();
    function glareFor(el, c){
      if(!c.glare) return null;
      let g = el.querySelector(':scope > .hover-glare');
      if(!g){
        if(getComputedStyle(el).position === 'static') el.style.position = 'relative';
        g = document.createElement('div'); g.className = 'hover-glare'; el.appendChild(g);
      }
      return g;
    }
    function state(el){
      let s = active.get(el);
      if(!s){
        const c = conf(el);
        s = { c, glare:glareFor(el,c), hov:false,
              rx:0,ry:0,lf:0,pz:0,gx:50,gy:50,go:0,
              txr:0,tyr:0,tl:0,tz:0,tgx:50,tgy:50,tgo:0 };
        active.set(el, s);
      }
      return s;
    }
    document.addEventListener('pointerover', e=>{
      const el = e.target.closest(SEL); if(!el) return;
      const s = state(el); s.hov = true;
      // cache untransformed geometry once per hover (no per-move getBoundingClientRect)
      const p = offsetPos(el);
      s.docCX = p.x + el.offsetWidth/2; s.docCY = p.y + el.offsetHeight/2;
      s.hw = Math.max(1, el.offsetWidth/2); s.hh = Math.max(1, el.offsetHeight/2);
    }, {passive:true});
    document.addEventListener('pointermove', e=>{
      const el = e.target.closest(SEL); if(!el) return;
      const s = active.get(el); if(!s || s.hw===undefined) return;
      const cx = s.docCX - window.scrollX, cy = s.docCY - window.scrollY;
      let nx = (e.clientX - cx)/s.hw, ny = (e.clientY - cy)/s.hh;   // ~ -1..1 from centre
      nx = nx<-1?-1:nx>1?1:nx; ny = ny<-1?-1:ny>1?1:ny;
      s.tyr = nx * s.c.tilt;     // rotateY toward cursor
      s.txr = -ny * s.c.tilt;    // rotateX toward cursor
      s.tl = -s.c.lift; s.tz = s.c.z;
      s.tgx = (nx*0.5+0.5)*100; s.tgy = (ny*0.5+0.5)*100; s.tgo = s.c.glare;
    }, {passive:true});
    document.addEventListener('pointerout', e=>{
      const el = e.target.closest(SEL); if(!el) return;
      if(e.relatedTarget && el.contains(e.relatedTarget)) return;  // moving within el
      const s = active.get(el); if(!s) return;
      s.hov = false; s.txr=0; s.tyr=0; s.tl=0; s.tz=0; s.tgo=0;
    }, {passive:true});
    let hovT = performance.now();
    function hoverLoop(){
      // frame-rate-independent easing: identical feel at 60Hz and 120Hz
      const now = performance.now();
      let dt = (now - hovT)/1000; hovT = now; if(dt>0.05) dt=0.05; if(dt<=0) dt=0.016;
      const k = 1 - Math.exp(-dt / 0.13);   // ~matches the old 60fps response, now display-agnostic
      active.forEach((s, el)=>{
        s.rx += (s.txr - s.rx)*k; s.ry += (s.tyr - s.ry)*k;
        s.lf += (s.tl - s.lf)*k;  s.pz += (s.tz - s.pz)*k;
        s.go += (s.tgo - s.go)*k; s.gx += (s.tgx - s.gx)*k; s.gy += (s.tgy - s.gy)*k;
        const atRest = !s.hov && Math.abs(s.rx)<0.04 && Math.abs(s.ry)<0.04 &&
                       Math.abs(s.lf)<0.06 && Math.abs(s.pz)<0.1 && s.go<0.004;
        if(atRest){ el.style.transform=''; el.style.boxShadow=''; if(s.glare) s.glare.style.opacity='0'; active.delete(el); return; }
        // real depth: lower perspective + tilt + a genuine Z pop toward the viewer
        el.style.transform = 'perspective(680px) rotateX('+s.rx.toFixed(2)+'deg) rotateY('+s.ry.toFixed(2)+'deg) translate3d(0,'+s.lf.toFixed(1)+'px,'+s.pz.toFixed(1)+'px)';
        // tilt-reactive cast shadow (light from above) — the key volume cue
        if(s.c.shadow){
          const d = s.pz/40;                       // 0..1 how lifted
          const ox = (-s.ry*1.4).toFixed(1), oy = (s.rx*1.4 + 10 + d*14).toFixed(1);
          const bl = (26 + d*30).toFixed(0);
          el.style.boxShadow = ox+'px '+oy+'px '+bl+'px -12px rgba(0,0,0,'+(0.30+d*0.22).toFixed(2)+'), '+
                               (ox*0.4).toFixed(1)+'px '+(oy*0.4).toFixed(1)+'px 12px -8px rgba(0,0,0,.18)';
        }
        if(s.glare){
          s.glare.style.opacity = s.go.toFixed(3);
          s.glare.style.background = 'radial-gradient(circle at '+s.gx.toFixed(1)+'% '+s.gy.toFixed(1)+'%, rgba(255,255,255,.95), rgba(255,255,255,0) 44%)';
        }
      });
      requestAnimationFrame(hoverLoop);
    }
    hoverLoop();
  } else {
    document.querySelectorAll('.tilt').forEach(c=>c.classList.remove('tilt'));
  }

  /* ---------- anchor smooth scroll ---------- */
  document.addEventListener('click', e=>{
    const a = e.target.closest('a[href^="#"]'); if(!a) return;
    const id = a.getAttribute('href');
    if(id==='#'){ e.preventDefault(); window.scrollTo({top:0,behavior:reduce?'auto':'smooth'}); return; }
    const el = document.querySelector(id); if(el){ e.preventDefault(); el.scrollIntoView({behavior:reduce?'auto':'smooth', block:'start'}); }
  });
})();
