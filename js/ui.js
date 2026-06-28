/* ============================================================================
   PROMPTFIELD — UI & the scroll instrument
   Owns the eased scroll progress + velocity that drives the WebGL scene,
   choreographs the copy overlays, and wires the preloader, cursor, HUD,
   nav and the live "re-forge" prompt. Plain script — shares window.PF with
   the scene module.
   ========================================================================== */
(function () {
  "use strict";

  var PF = (window.PF = window.PF || {});
  if (PF.progress == null) PF.progress = 0;
  if (PF.velocity == null) PF.velocity = 0;
  if (PF.mouse == null) PF.mouse = { x: 0, y: 0 };
  if (PF.reduceMotion == null)
    PF.reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  var clamp01 = function (t) { return t < 0 ? 0 : t > 1 ? 1 : t; };
  var smoothstep = function (a, b, x) { var t = clamp01((x - a) / (b - a)); return t * t * (3 - 2 * t); };
  var lerp = function (a, b, t) { return a + (b - a) * t; };
  var fade = function (lt, inA, inB, outA, outB) { return smoothstep(inA, inB, lt) * (1 - smoothstep(outA, outB, lt)); };

  /* ---- elements ---- */
  var $ = function (id) { return document.getElementById(id); };
  var panels = Array.prototype.slice.call(document.querySelectorAll(".panel"));
  var railFill = $("railFill");
  var navLinks = Array.prototype.slice.call(document.querySelectorAll(".nav a"));
  var hudFps = $("hudFps"), hudParts = $("hudParts"), hudDraws = $("hudDraws");
  var cue = $("scrollCue");
  var preBar = $("preBar"), preMsg = $("preMsg");

  var ranges = panels.map(function (p) {
    return { el: p, start: parseFloat(p.dataset.start), end: parseFloat(p.dataset.end), mode: p.dataset.fade || "normal", vis: false };
  });

  /* ============================================================
     Scroll instrument — eased progress + smoothed velocity
     ============================================================ */
  var raw = 0, prog = 0, lastProg = 0, vel = 0, lastT = 0;
  function readScroll() {
    var doc = document.documentElement;
    var max = doc.scrollHeight - window.innerHeight;
    raw = max > 0 ? clamp01(window.scrollY / max) : 0;
  }
  window.addEventListener("scroll", readScroll, { passive: true });
  window.addEventListener("resize", readScroll, { passive: true });
  readScroll();
  prog = raw; lastProg = raw;

  function tick(now) {
    requestAnimationFrame(tick);
    var dt = lastT ? Math.min((now - lastT) / 1000, 0.05) : 0.016;
    lastT = now;
    document.body.classList.toggle("forging", !!PF.forging); // a word is assembling → clear the stage
    // test hook: pin progress exactly (used by the headless render harness)
    if (window.__pinProgress != null) {
      prog = raw = clamp01(window.__pinProgress);
      lastProg = prog; PF.progress = prog; PF.velocity = 0;
      if (PF.ready && !readyFired) fireReady();
      updateDOM(prog); return;
    }
    readScroll();
    // eased follow with momentum
    prog += (raw - prog) * (PF.reduceMotion ? 0.5 : 0.09);
    var inst = Math.abs(prog - lastProg) / Math.max(dt, 0.001);
    var velTarget = clamp01(inst * 0.55);
    vel += (velTarget - vel) * 0.12;
    lastProg = prog;

    PF.progress = prog; PF.velocity = vel;
    if (PF.ready && !readyFired) fireReady(); // robust against scene:ready timing
    updateDOM(prog);
  }

  function updateDOM(s) {
    if (railFill) railFill.style.height = (s * 100).toFixed(2) + "%";
    if (cue && s > 0.02) { cue.style.opacity = "0"; }

    for (var i = 0; i < ranges.length; i++) {
      var r = ranges[i];
      var lt = (s - r.start) / Math.max(r.end - r.start, 0.0001);
      var o;
      if (r.mode === "first") o = 1 - smoothstep(0.82, 1.04, lt); // visible from the very top
      else if (r.mode === "early") o = fade(lt, 0.0, 0.10, 0.22, 0.40);
      else if (r.mode === "late") o = fade(lt, 0.04, 0.24, 1.3, 1.6);
      else o = fade(lt, 0.0, 0.18, 0.80, 1.0);
      var shift = lerp(34, -34, clamp01(lt));
      var el = r.el;
      if (o < 0.01) {
        if (r.vis) { el.style.opacity = "0"; el.style.visibility = "hidden"; r.vis = false; }
      } else {
        if (!r.vis) { el.style.visibility = "visible"; r.vis = true; }
        el.style.opacity = o.toFixed(3);
        el.style.setProperty("--shift", shift.toFixed(1) + "px");
      }
    }

    // active nav
    var idx = 0;
    for (var k = 0; k < ranges.length; k++) if (s >= ranges[k].start) idx = k;
    for (var n = 0; n < navLinks.length; n++) navLinks[n].classList.toggle("is-active", n === idx);
  }

  requestAnimationFrame(tick);

  /* ============================================================
     HUD readout
     ============================================================ */
  function fmt(n) { return n == null ? "—" : n.toLocaleString("en-US"); }
  setInterval(function () {
    if (hudFps) hudFps.textContent = PF.fps != null ? PF.fps : "—";
    if (hudParts) hudParts.textContent = PF.particles != null ? fmt(PF.particles) : (window.__particles != null ? fmt(window.__particles) : "—");
    if (hudDraws) hudDraws.textContent = PF.drawCalls != null ? PF.drawCalls : "—";
  }, 250);

  /* ============================================================
     Preloader
     ============================================================ */
  var fakeP = 0, readyFired = false;
  var msgs = ["compiling shaders", "seeding 120,000 points", "sampling glyph → particles", "lighting the bloom", "tuning the cinematic grade"];
  var msgI = 0;
  var preTimer = setInterval(function () {
    fakeP = Math.min(fakeP + Math.random() * 14, 92);
    if (preBar) preBar.style.width = fakeP.toFixed(0) + "%";
    if (preMsg && Math.random() < 0.4) { preMsg.textContent = msgs[msgI % msgs.length] + " ·"; msgI++; }
  }, 280);

  function fireReady() {
    if (readyFired) return; readyFired = true;
    clearInterval(preTimer);
    if (preBar) preBar.style.width = "100%";
    setTimeout(function () { document.body.classList.add("is-ready"); }, 180);
  }
  window.addEventListener("scene:ready", fireReady);
  // safety net in case the scene never signals
  setTimeout(fireReady, 9000);

  /* ============================================================
     Custom cursor (fine pointers only)
     ============================================================ */
  var cursor = $("cursor");
  var fine = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
  if (fine && cursor) {
    document.body.classList.add("has-cursor");
    var cx = window.innerWidth / 2, cy = window.innerHeight / 2, tx = cx, ty = cy;
    window.addEventListener("pointermove", function (e) { tx = e.clientX; ty = e.clientY; }, { passive: true });
    (function curse() {
      requestAnimationFrame(curse);
      cx += (tx - cx) * 0.25; cy += (ty - cy) * 0.25;
      cursor.style.transform = "translate3d(" + cx + "px," + cy + "px,0)";
    })();
    var hoverSel = "a,button,input,[data-hover],.prompt__chips button";
    document.addEventListener("pointerover", function (e) {
      if (e.target.closest && e.target.closest(hoverSel)) cursor.classList.add("is-hover");
    });
    document.addEventListener("pointerout", function (e) {
      if (e.target.closest && e.target.closest(hoverSel)) cursor.classList.remove("is-hover");
    });
    window.addEventListener("pointerdown", function () { cursor.classList.add("is-down"); });
    window.addEventListener("pointerup", function () { cursor.classList.remove("is-down"); });
  }

  /* ============================================================
     Nav / brand smooth-scroll to a scroll fraction
     ============================================================ */
  function gotoFraction(f) {
    var max = document.documentElement.scrollHeight - window.innerHeight;
    window.scrollTo({ top: f * max, behavior: PF.reduceMotion ? "auto" : "smooth" });
  }
  document.querySelectorAll("[data-goto]").forEach(function (a) {
    a.addEventListener("click", function (e) { e.preventDefault(); gotoFraction(parseFloat(a.dataset.goto)); });
  });

  /* ============================================================
     Live re-forge prompt (§05)
     ============================================================ */
  var form = $("promptForm"), input = $("promptInput"), chips = $("promptChips");
  function forge(word) {
    if (!word) return;
    if (PF.scene && typeof PF.scene.submitWord === "function") {
      // make sure we're parked at the finale so the camera frames the word
      if (prog < 0.82) gotoFraction(0.9);
      PF.scene.submitWord(word);
    }
  }
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var w = (input && input.value || "").trim();
      forge(w || "AI");
      if (input) input.blur();
    });
  }
  if (chips) {
    chips.addEventListener("click", function (e) {
      var b = e.target.closest("button[data-word]"); if (!b) return;
      if (input) input.value = b.dataset.word;
      forge(b.dataset.word);
    });
  }

  /* react to a reduced-motion preference change live */
  var mq = window.matchMedia("(prefers-reduced-motion: reduce)");
  if (mq.addEventListener) mq.addEventListener("change", function (e) { PF.reduceMotion = e.matches; });
})();
