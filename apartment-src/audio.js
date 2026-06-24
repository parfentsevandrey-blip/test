/* ============================================================
   Кутузовский XII — audio.js   ·   generative ambient sound
   ------------------------------------------------------------
   An elegant, OPT-IN soundscape (off by default). A toggle in the
   header builds — on first click (the required user gesture) — a
   warm WebAudio pad (a few detuned oscillators through a lowpass)
   plus a faint filtered-noise "room tone". It breathes slowly and
   swells a touch with scroll; the chord re-tints with the theme.
   No audio file — fully synthesized, zero payload. The preference
   is remembered; a returning visitor's first interaction resumes
   it (autoplay policy). Bails silently without WebAudio.
   ============================================================ */
(function(){
  const AC = window.AudioContext || window.webkitAudioContext;
  if(!AC) return;
  const barRight = document.querySelector('.bar-right');
  if(!barRight) return;
  const KEY = 'kutuzovsky-sound';

  const ICON_OFF = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9v6h4l5 4V5L8 9H4z"></path><path d="M16 9.5a3.5 3.5 0 0 1 0 5" opacity=".35"></path></svg>';
  const ICON_ON  = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9v6h4l5 4V5L8 9H4z"></path><path d="M16 9.5a3.5 3.5 0 0 1 0 5"></path><path d="M18.5 7.5a7 7 0 0 1 0 9"></path></svg>';

  const wrap = document.createElement('div'); wrap.className = 'snd-switch';
  const btn = document.createElement('button'); btn.type = 'button';
  btn.setAttribute('aria-label', 'Атмосферный звук'); btn.setAttribute('aria-pressed', 'false');
  btn.title = 'Атмосферный звук'; btn.innerHTML = ICON_OFF;
  wrap.appendChild(btn);
  barRight.insertBefore(wrap, barRight.querySelector('.cta') || null);

  let ctx = null, master = null, filt = null, started = false, on = false, raf = 0, lastSy = scrollY || 0, vel = 0;

  function build(){
    ctx = new AC();
    master = ctx.createGain(); master.gain.value = 0.0001; master.connect(ctx.destination);
    filt = ctx.createBiquadFilter(); filt.type = 'lowpass'; filt.frequency.value = 520; filt.Q.value = 0.6; filt.connect(master);

    // warm pad — A major-ish, gently detuned
    [110, 164.81, 220, 277.18].forEach((f, i)=>{
      const o = ctx.createOscillator(); o.type = (i % 2) ? 'sine' : 'triangle';
      o.frequency.value = f; o.detune.value = (i - 1.5) * 5;
      const g = ctx.createGain(); g.gain.value = 0.17 / (i + 1);
      o.connect(g); g.connect(filt); o.start();
    });
    // faint room tone — looped band-passed noise
    const buf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const d = buf.getChannelData(0); for(let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * 0.4;
    const noise = ctx.createBufferSource(); noise.buffer = buf; noise.loop = true;
    const nf = ctx.createBiquadFilter(); nf.type = 'bandpass'; nf.frequency.value = 360; nf.Q.value = 0.5;
    const ng = ctx.createGain(); ng.gain.value = 0.05;
    noise.connect(nf); nf.connect(ng); ng.connect(master); noise.start();
    started = true;
  }

  function breathe(){
    if(!on) return;
    raf = requestAnimationFrame(breathe);
    const sy = scrollY || 0; const v = Math.min(1, Math.abs(sy - lastSy) / 40); lastSy = sy;
    vel += (v - vel) * 0.05;
    const t = ctx.currentTime;
    try{ master.gain.setTargetAtTime(0.05 + 0.012 * Math.sin(t * 0.4) + vel * 0.03, t, 0.4); }catch(e){}
    try{ filt.frequency.setTargetAtTime(480 + vel * 900, t, 0.5); }catch(e){}
  }

  function enable(){
    if(on) return; on = true;
    try{ if(!started) build(); if(ctx.state === 'suspended') ctx.resume(); }catch(e){ on = false; return; }
    btn.classList.add('active'); btn.setAttribute('aria-pressed', 'true'); btn.innerHTML = ICON_ON;
    try{ localStorage.setItem(KEY, '1'); }catch(e){}
    breathe();
  }
  function disable(){
    if(!on) return; on = false;
    try{ master.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.4); }catch(e){}
    cancelAnimationFrame(raf);
    btn.classList.remove('active'); btn.setAttribute('aria-pressed', 'false'); btn.innerHTML = ICON_OFF;
    try{ localStorage.setItem(KEY, '0'); }catch(e){}
  }

  btn.addEventListener('click', ()=> on ? disable() : enable());

  // returning visitor who left it on → resume on first gesture (autoplay policy)
  let pref = null; try{ pref = localStorage.getItem(KEY); }catch(e){}
  if(pref === '1'){
    const kick = ()=>{ enable(); window.removeEventListener('pointerdown', kick); window.removeEventListener('keydown', kick); };
    window.addEventListener('pointerdown', kick, { once:true });
    window.addEventListener('keydown', kick, { once:true });
  }
})();
