/* ============================================================
   Кутузовский XII — lightstory.js   ·   the light follows you
   ------------------------------------------------------------
   A quiet colour story across the page. Each section carries a
   subtle mood from a curated palette; as you scroll, a full-scene
   soft-light grade eases toward the mood of the section in the
   reading zone. (The drifting light-shaft and the central reading
   spot are pure CSS — see the build's stylesheet.)

   All automatic — nothing reacts to the cursor. Updates only while
   the colour is still converging, so it costs nothing at rest.
   ============================================================ */
(function(){
  const grade = document.querySelector('.kx-grade');
  const sections = [...document.querySelectorAll('main > section')];
  if(!grade || !sections.length) return;

  // curated, subtle moods cycled across the sections (warm → cool → …)
  const MOODS = [
    [255,219,164],  // warm amber
    [165,194,224],  // cool steel
    [255,237,209],  // warm pearl
    [188,206,198],  // sage neutral
    [212,198,224],  // soft mauve
  ];
  sections.forEach((s, i) => s.__mood = MOODS[i % MOODS.length]);

  const reduce = matchMedia('(prefers-reduced-motion:reduce)').matches;
  let cur = [248,246,243], raf = 0;

  function pick(){
    const mid = innerHeight * 0.5; let best = sections[0], bd = Infinity;
    for(const s of sections){
      const r = s.getBoundingClientRect();
      const d = Math.abs((r.top + r.bottom) / 2 - mid);
      if(d < bd){ bd = d; best = s; }
    }
    return best.__mood || [248,246,243];
  }
  function step(){
    raf = 0;
    const m = pick();
    cur[0] += (m[0]-cur[0])*0.07; cur[1] += (m[1]-cur[1])*0.07; cur[2] += (m[2]-cur[2])*0.07;
    grade.style.backgroundColor = 'rgb('+(cur[0]|0)+','+(cur[1]|0)+','+(cur[2]|0)+')';
    if(Math.abs(m[0]-cur[0]) + Math.abs(m[1]-cur[1]) + Math.abs(m[2]-cur[2]) > 1.2) schedule();
  }
  function schedule(){ if(!raf) raf = requestAnimationFrame(step); }

  if(reduce){ const m = pick(); grade.style.backgroundColor = 'rgb('+m.join(',')+')'; return; }
  addEventListener('scroll', schedule, { passive:true });
  addEventListener('resize', schedule, { passive:true });
  schedule();
})();
