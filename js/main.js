/* =========================================================
   ZENITH — UI interactions
   ========================================================= */
(function () {
  "use strict";

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- Preloader ---------- */
  const preloader = document.getElementById("preloader");
  let hidden = false;
  function hidePreloader() {
    if (hidden || !preloader) return;
    hidden = true;
    preloader.classList.add("is-done");
  }
  // Hide once the 3D scene paints its first frame (or signals failure)…
  window.addEventListener("scene:ready", () => setTimeout(hidePreloader, 300));
  // …with hard fallbacks so the page can never get stuck behind the loader.
  window.addEventListener("load", () => setTimeout(hidePreloader, 1200));
  setTimeout(hidePreloader, 3000);

  /* ---------- Header / veil / scroll-rail on scroll ---------- */
  const header = document.getElementById("header");
  const veil = document.getElementById("veil");
  const railDot = document.getElementById("railDot");
  const clamp01 = (n) => Math.min(1, Math.max(0, n));

  const onScroll = () => {
    const y = window.scrollY;
    const vh = window.innerHeight;
    header.classList.toggle("is-scrolled", y > 40);

    // Fade the darkening veil in as we leave the hero so text stays legible,
    // but keep the living city visible behind the content (full-3D feel).
    if (veil) {
      const o = clamp01((y - vh * 0.6) / (vh * 0.9)) * 0.55;
      veil.style.opacity = o.toFixed(3);
    }

    // Move the cinematic scroll-rail dot with overall progress
    if (railDot) {
      const max = document.documentElement.scrollHeight - vh;
      const p = max > 0 ? clamp01(y / max) : 0;
      railDot.style.top = (p * 115).toFixed(1) + "px";
    }
  };
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll, { passive: true });

  /* ---------- Mobile nav ---------- */
  const burger = document.getElementById("burger");
  const nav = document.getElementById("nav");
  if (burger && nav) {
    const toggle = (open) => {
      nav.classList.toggle("is-open", open);
      burger.classList.toggle("is-open", open);
      burger.setAttribute("aria-expanded", String(open));
      document.body.style.overflow = open ? "hidden" : "";
    };
    burger.addEventListener("click", () => toggle(!nav.classList.contains("is-open")));
    nav.querySelectorAll("a").forEach((a) => a.addEventListener("click", () => toggle(false)));
  }

  /* ---------- Reveal on scroll ---------- */
  const reveals = document.querySelectorAll("[data-reveal]");
  reveals.forEach((el) => {
    const delay = el.getAttribute("data-delay");
    if (delay) el.style.setProperty("--reveal-delay", delay + "ms");
  });

  if ("IntersectionObserver" in window && !reduceMotion) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.16, rootMargin: "0px 0px -8% 0px" }
    );
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add("is-in"));
  }

  /* ---------- Animated counters ---------- */
  const counters = document.querySelectorAll("[data-count]");
  const animateCount = (el) => {
    const target = parseFloat(el.getAttribute("data-count"));
    const decimals = parseInt(el.getAttribute("data-decimals") || "0", 10);
    const prefix = el.getAttribute("data-prefix") || "";
    const suffix = el.getAttribute("data-suffix") || "";
    const dur = 1600;
    const start = performance.now();
    const ease = (t) => 1 - Math.pow(1 - t, 3);
    const step = (now) => {
      const t = Math.min(1, (now - start) / dur);
      const val = target * ease(t);
      el.textContent = prefix + val.toFixed(decimals) + suffix;
      if (t < 1) requestAnimationFrame(step);
      else el.textContent = prefix + target.toFixed(decimals) + suffix;
    };
    requestAnimationFrame(step);
  };

  if ("IntersectionObserver" in window && !reduceMotion) {
    const cio = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            animateCount(entry.target);
            cio.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.6 }
    );
    counters.forEach((el) => cio.observe(el));
  } else {
    counters.forEach((el) => {
      const decimals = parseInt(el.getAttribute("data-decimals") || "0", 10);
      el.textContent =
        (el.getAttribute("data-prefix") || "") +
        parseFloat(el.getAttribute("data-count")).toFixed(decimals) +
        (el.getAttribute("data-suffix") || "");
    });
  }

  /* ---------- Gallery tilt ---------- */
  if (!reduceMotion && window.matchMedia("(hover: hover)").matches) {
    document.querySelectorAll("[data-tilt]").forEach((card) => {
      card.addEventListener("pointermove", (e) => {
        const r = card.getBoundingClientRect();
        const px = (e.clientX - r.left) / r.width - 0.5;
        const py = (e.clientY - r.top) / r.height - 0.5;
        card.style.transform = `perspective(800px) rotateY(${px * 5}deg) rotateX(${py * -5}deg) translateZ(6px)`;
      });
      card.addEventListener("pointerleave", () => {
        card.style.transform = "";
      });
    });
  }

  /* ---------- Custom cursor ---------- */
  const cursor = document.getElementById("cursor");
  if (cursor && window.matchMedia("(hover: hover) and (pointer: fine)").matches && !reduceMotion) {
    let cx = 0, cy = 0, tx = 0, ty = 0;
    window.addEventListener("pointermove", (e) => {
      tx = e.clientX; ty = e.clientY;
      cursor.classList.add("is-active");
    });
    const render = () => {
      cx += (tx - cx) * 0.18;
      cy += (ty - cy) * 0.18;
      cursor.style.transform = `translate(${cx}px, ${cy}px) translate(-50%, -50%)`;
      requestAnimationFrame(render);
    };
    render();
    const hoverables = document.querySelectorAll("a, button, [data-hover], input, textarea");
    hoverables.forEach((el) => {
      el.addEventListener("pointerenter", () => cursor.classList.add("is-hover"));
      el.addEventListener("pointerleave", () => cursor.classList.remove("is-hover"));
    });
  }

  /* ---------- Contact form ---------- */
  const form = document.getElementById("form");
  const status = document.getElementById("formStatus");
  if (form) {
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      if (!form.checkValidity()) {
        form.classList.add("is-invalid");
        status.textContent = "Please complete the required fields.";
        const firstInvalid = form.querySelector(":invalid");
        if (firstInvalid) firstInvalid.focus();
        return;
      }
      form.classList.remove("is-invalid");
      const name = (form.querySelector('[name="name"]').value || "").trim().split(" ")[0];
      const btn = form.querySelector('button[type="submit"]');
      btn.disabled = true;
      btn.textContent = "Sending…";
      // No backend in this concept build — simulate a confident response.
      setTimeout(() => {
        form.reset();
        btn.disabled = false;
        btn.textContent = "Request Sent ✓";
        status.textContent = `Thank you${name ? ", " + name : ""}. A ZENITH advisor will contact you within 24 hours.`;
        setTimeout(() => { btn.textContent = "Request Viewing"; }, 4000);
      }, 900);
    });
  }

  /* ---------- Video-from-text (no <video>, no visible <img>) ---------- */
  (function textVideo() {
    const screenEl = document.getElementById("txtVideo");
    if (!screenEl) return;
    const galleryImgs = Array.from(document.querySelectorAll(".gallery__item img"));
    if (!galleryImgs.length) return;
    // decode our own copies so sampling never depends on lazy-loaded DOM images
    const frames = galleryImgs.map((i) => { const im = new Image(); im.src = i.getAttribute("src") || i.src; return im; });

    const RAMP = " .'`:,-~+=*coaehx%#WM@";
    const sample = document.createElement("canvas");
    const sctx = sample.getContext("2d", { willReadFrequently: true });
    let COLS = 140, ROWS = 50, running = false, t0 = 0;
    const PERIOD = 6000, FADE = 1200;

    const setSize = () => {
      COLS = window.innerWidth < 760 ? 92 : 144;
      const im = frames[0];
      const a = (im.naturalHeight || 9) / (im.naturalWidth || 16);
      ROWS = Math.max(18, Math.round(COLS * a * 0.52));
      sample.width = COLS; sample.height = ROWS;
    };
    const kb = (img, p, alpha) => {
      const z = 1.12 - 0.12 * p;
      const sw = img.naturalWidth / z, sh = img.naturalHeight / z;
      const sx = (img.naturalWidth - sw) * (0.3 + 0.4 * p);
      const sy = (img.naturalHeight - sh) * (0.6 - 0.3 * p);
      sctx.globalAlpha = alpha;
      sctx.drawImage(img, sx, sy, sw, sh, 0, 0, COLS, ROWS);
    };
    const renderAt = (now) => {
      const cycle = frames.length * PERIOD;
      const tt = (now - t0) % cycle;
      const idx = Math.floor(tt / PERIOD), local = tt - idx * PERIOD, p = local / PERIOD;
      sctx.globalAlpha = 1; sctx.clearRect(0, 0, COLS, ROWS);
      try {
        kb(frames[idx], p, 1);
        if (local > PERIOD - FADE) kb(frames[(idx + 1) % frames.length], 0, (local - (PERIOD - FADE)) / FADE);
        const data = sctx.getImageData(0, 0, COLS, ROWS).data;
        let out = "", run = "", cr = -1, cg = -1, cb = -1;
        for (let y = 0; y < ROWS; y++) {
          for (let x = 0; x < COLS; x++) {
            const o = (y * COLS + x) * 4;
            let r = data[o], g = data[o + 1], b = data[o + 2];
            const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
            const ch = RAMP[Math.min(RAMP.length - 1, (Math.pow(lum, 0.78) * RAMP.length) | 0)];
            const s = 0.42, add = 10;
            r = (255 * Math.pow(Math.min(1, (r + (r - lum * 255) * s + add) / 255), 0.8)) | 0;
            g = (255 * Math.pow(Math.min(1, (g + (g - lum * 255) * s + add) / 255), 0.8)) | 0;
            b = (255 * Math.pow(Math.min(1, (b + (b - lum * 255) * s + add) / 255), 0.8)) | 0;
            const qr = r & 0xF0, qg = g & 0xF0, qb = b & 0xF0;
            if (qr !== cr || qg !== cg || qb !== cb) {
              if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + "</span>";
              run = ""; cr = qr; cg = qg; cb = qb;
            }
            run += ch === "<" ? "&lt;" : ch;
          }
          run += "\n";
        }
        if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + "</span>";
        screenEl.innerHTML = out;
      } catch (e) { /* image not decodable yet */ }
    };
    const loop = (now) => {
      if (!running) return;
      renderAt(now);
      requestAnimationFrame(loop);
    };

    const ready = () => Promise.all(frames.map((i) => (i.complete && i.naturalWidth) ? 0 : new Promise((r) => { i.onload = r; i.onerror = r; })));
    const start = () => {
      if (running) return;
      ready().then(() => {
        setSize();
        t0 = performance.now();
        renderAt(t0);                 // paint one frame immediately (no wait for rAF)
        if (!reduceMotion) { running = true; requestAnimationFrame(loop); }
      });
    };
    const stop = () => { running = false; };

    if ("IntersectionObserver" in window) {
      const io = new IntersectionObserver((es) => es.forEach((e) => (e.isIntersecting ? start() : stop())), { threshold: 0.12 });
      io.observe(screenEl.closest("section") || screenEl);
    } else { start(); }

    window.addEventListener("resize", () => { if (running || reduceMotion) setSize(); });

    const rev = document.getElementById("txtReveal");
    if (rev) rev.addEventListener("click", () => {
      const on = screenEl.classList.toggle("is-reveal");
      rev.textContent = on ? "Свернуть" : "Показать символы";
    });
  })();

  /* ---------- Footer year ---------- */
  const year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear();
})();
