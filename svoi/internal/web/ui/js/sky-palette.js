// The living sky of the "Роса" look: the colour of the sky for a given height of the sun, in the same few moods
// the weather app «Роса» of this repository has (apricot at dawn, vivid blue at noon, ink-indigo at night).
// It is a classic script (loaded before js/boot.js) that puts `window.themeshSky` on the page; nothing else here
// touches the page, js/boot.js and js/rosa.js decide when to ask for a palette and where to put it.
//
// The numbers (the key colours by sun height, the way they are mixed, the "bleached" porcelain mood for the light
// appearance, the ink and the accent) are those of Rosa's SkyPalette.kt; the sun and the moon are Astronomy.kt's
// low-precision ephemeris. There is no weather in a mesh, so the sky is always a clear one.
(function (g) {
  "use strict";

  // ------------------------------------------------------------------ colour: sRGB <-> OKLab
  function hex(n) { return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function lin(c) { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  function gam(l) {
    l = l < 0 ? 0 : l > 1 ? 1 : l;
    return (l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055) * 255;
  }
  function lab(c) {
    var r = lin(c[0]), gg = lin(c[1]), b = lin(c[2]);
    var l = Math.cbrt(0.4122214708 * r + 0.5363325363 * gg + 0.0514459929 * b);
    var m = Math.cbrt(0.2119034982 * r + 0.6806995451 * gg + 0.1073969566 * b);
    var s = Math.cbrt(0.0883024619 * r + 0.2817188376 * gg + 0.6299787005 * b);
    return [
      0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
      1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
      0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    ];
  }
  function unlab(v) {
    var l = v[0] + 0.3963377774 * v[1] + 0.2158037573 * v[2];
    var m = v[0] - 0.1055613458 * v[1] - 0.0638541728 * v[2];
    var s = v[0] - 0.0894841775 * v[1] - 1.2914855480 * v[2];
    l = l * l * l; m = m * m * m; s = s * s * s;
    return [
      gam(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
      gam(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
      gam(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    ];
  }
  /** a → b by t (0..1), mixed the way the eye does it (in OKLab), not in sRGB. */
  function mix(a, b, t) {
    if (t <= 0) return a.slice();
    if (t >= 1) return b.slice();
    var x = lab(a), y = lab(b);
    return unlab([x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t]);
  }
  /** WCAG relative luminance, 0..1. */
  function lum(c) { return 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]); }
  function h2(n) { n = Math.round(n); n = n < 0 ? 0 : n > 255 ? 255 : n; return (n < 16 ? "0" : "") + n.toString(16); }
  function css(c) { return "#" + h2(c[0]) + h2(c[1]) + h2(c[2]); }
  function rgb(c) { return Math.round(c[0]) + " " + Math.round(c[1]) + " " + Math.round(c[2]); }
  function clamp(x, lo, hi) { return x < lo ? lo : x > hi ? hi : x; }

  // ------------------------------------------------------------------ the sky by the height of the sun (degrees)
  // [elevation, zenith, horizon, glow, sun]
  var KEYS = [
    [-18, 0x05081A, 0x121637, 0x1C1B45, 0xC9D2FF],
    [-12, 0x0A0F2E, 0x221F55, 0x3B2C66, 0xC9D2FF],
    [-7, 0x141D4C, 0x4A3F86, 0x9C6696, 0xFFC9C0],
    [-3, 0x233A7E, 0x9B6B9E, 0xF0928A, 0xFFB387],
    [0, 0x3558A2, 0xE88C7A, 0xFFB077, 0xFFC27A],
    [4, 0x4577BF, 0xF4B08A, 0xFFD39A, 0xFFE0A8],
    [10, 0x3E82D6, 0xB7D3EE, 0xFFE9C4, 0xFFF2D6],
    [25, 0x2C76D8, 0x9BCBF6, 0xFFF6E3, 0xFFFBF0],
    [60, 0x1E6ACF, 0x8CC3F7, 0xFFFFFF, 0xFFFFFF],
  ];

  // The fixed moods: a bright late morning, the blue hour, deep night.
  var LIGHT_ELEVATION = 30, EVENING_ELEVATION = -1.5, DARK_ELEVATION = -16;
  var WHITE = [255, 255, 255];
  var INK_DARK = hex(0x1B2030), INK_LIGHT = hex(0xFFFBF5);

  function bracket(e) {
    if (e <= KEYS[0][0]) return [KEYS[0], KEYS[0], 0];
    var last = KEYS[KEYS.length - 1];
    if (e >= last[0]) return [last, last, 0];
    var hi = 1;
    while (KEYS[hi][0] < e) hi++;
    var a = KEYS[hi - 1], b = KEYS[hi];
    var t = (e - a[0]) / (b[0] - a[0]);
    return [a, b, t * t * (3 - 2 * t)]; // smoothstep: twilight must not look linear
  }

  /** The clear sky at a height of the sun; moon is the lit part of the moon's disc (it lifts a clear night a little). */
  function sky(e, moon) {
    var br = bracket(e), a = br[0], b = br[1], t = br[2];
    var zenith = mix(hex(a[1]), hex(b[1]), t);
    var horizon = mix(hex(a[2]), hex(b[2]), t);
    var glow = mix(hex(a[3]), hex(b[3]), t);
    var sun = mix(hex(a[4]), hex(b[4]), t);
    var daylight = clamp((e + 8) / 18, 0, 1), night = 1 - daylight;
    if (night > 0) {
      var lift = moon * 0.18 * night;
      zenith = mix(zenith, hex(0x1B2A5C), lift);
      horizon = mix(horizon, hex(0x33407A), lift);
    }
    // legibility is judged where the big type sits (the upper third of the sky)
    var brightness = lum(mix(zenith, horizon, 0.3));
    var light = brightness > 0.36;
    var accent = night > 0.6 ? hex(0xC6CCFF) : e < 8 ? hex(0xFFB48A) : hex(0xFFD37A);
    return {
      zenith: zenith, horizon: horizon, glow: glow, sun: sun,
      ink: light ? INK_DARK : INK_LIGHT, accent: accent, brightness: brightness, night: night, daylight: daylight,
    };
  }

  /** A porcelain sky for the Light appearance: pale, milky, dark ink, a deeper amber (yellow vanishes on pale). */
  function bleached(p) {
    p.zenith = mix(p.zenith, hex(0xF3F6FC), 0.5);
    p.horizon = mix(p.horizon, WHITE, 0.62);
    p.glow = mix(p.glow, WHITE, 0.4);
    p.ink = INK_DARK;
    p.accent = hex(0xC46F1E);
    p.brightness = Math.max(p.brightness, 0.62);
    return p;
  }

  /**
   * The palette of an appearance. appearance: "auto" (the real sky at `elevation`), "light", "evening", "dark".
   * Beyond the colours: `isLight` (the ink is dark), `stars` (0..1, how starry), `smoke` (the colour of dark glass),
   * `deep` (the colour of shadows), `elevation`.
   */
  function palette(appearance, elevation, moon) {
    if (moon == null) moon = 0.5;
    var p, stars;
    if (appearance === "light") { p = bleached(sky(LIGHT_ELEVATION, moon)); stars = 0; elevation = LIGHT_ELEVATION; }
    else if (appearance === "evening") { p = sky(EVENING_ELEVATION, moon); stars = 0.3; elevation = EVENING_ELEVATION; }
    else if (appearance === "dark") { p = sky(DARK_ELEVATION, Math.max(moon, 0.4)); stars = 1; elevation = DARK_ELEVATION; }
    else { p = sky(elevation, moon); stars = clamp((-elevation - 6) / 8, 0, 1); }
    p.isLight = lum(p.ink) < 0.2;
    p.stars = stars;
    p.appearance = appearance === "light" || appearance === "evening" || appearance === "dark" ? appearance : "auto";
    p.elevation = elevation;
    // dark glass keeps the hue of the sky (deep blue by day, violet at dusk) instead of turning grey
    p.smoke = p.isLight ? WHITE : mix(p.zenith, hex(0x0B1020), 0.72);
    // shadows are cast in the sky's own deep colour: navy under a blue day, violet at dusk
    p.deep = mix(p.zenith, [0, 0, 0], 0.55);
    return p;
  }

  // ------------------------------------------------------------------ the sun and the moon (low precision, about 1 degree)
  var RAD = Math.PI / 180, J2000 = 2451545.0, OBLIQUITY = 23.4397 * RAD;
  var SYNODIC = 29.530588853, KNOWN_NEW_MOON = 2451550.2597;
  function norm(d) { return ((d % 360) + 360) % 360; }
  function day(ms) { return ms / 86400000 + 2440587.5 - J2000; }

  function horizontal(d, lat, lon, ra, dec) {
    var la = lat * RAD;
    var st = norm(280.16047 + 360.9856235 * d + lon) * RAD;
    var h = st - ra;
    var el = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(h));
    var az = Math.atan2(-Math.cos(dec) * Math.sin(h), Math.sin(dec) * Math.cos(la) - Math.cos(dec) * Math.cos(h) * Math.sin(la));
    return { elevation: el / RAD, azimuth: norm(az / RAD) };
  }
  function sunAt(ms, lat, lon) {
    var d = day(ms);
    var gg = norm(357.529 + 0.98560028 * d) * RAD;
    var q = norm(280.459 + 0.98564736 * d);
    var lambda = (q + 1.915 * Math.sin(gg) + 0.020 * Math.sin(2 * gg)) * RAD;
    var eps = (23.439 - 0.00000036 * d) * RAD;
    var ra = Math.atan2(Math.cos(eps) * Math.sin(lambda), Math.cos(lambda));
    var dec = Math.asin(Math.sin(eps) * Math.sin(lambda));
    return horizontal(d, lat, lon, ra, dec);
  }
  function moonAt(ms, lat, lon) {
    var d = day(ms);
    var l = (218.316 + 13.176396 * d) * RAD, m = (134.963 + 13.064993 * d) * RAD, f = (93.272 + 13.22935 * d) * RAD;
    var lambda = l + 6.289 * RAD * Math.sin(m), beta = 5.128 * RAD * Math.sin(f);
    var ra = Math.atan2(Math.sin(lambda) * Math.cos(OBLIQUITY) - Math.tan(beta) * Math.sin(OBLIQUITY), Math.cos(lambda));
    var dec = Math.asin(Math.sin(beta) * Math.cos(OBLIQUITY) + Math.cos(beta) * Math.sin(OBLIQUITY) * Math.sin(lambda));
    return horizontal(d, lat, lon, ra, dec);
  }
  /** phase: 0 new, .25 first quarter, .5 full, .75 last quarter; lit: the lit part of the disc, 0..1. */
  function moonPhase(ms) {
    var age = (ms / 86400000 + 2440587.5 - KNOWN_NEW_MOON) / SYNODIC;
    var phase = age - Math.floor(age);
    return { phase: phase, lit: (1 - Math.cos(2 * Math.PI * phase)) / 2 };
  }

  // ------------------------------------------------------------------ where the person is
  // A page does not know where its owner is (and the app never asks for the location), so the place is a guess from
  // what the system tells: the time zone's standard offset gives the longitude (the middle of the zone), the zone's
  // name tells the hemisphere. Mood, not astronomy: a degree or two of latitude does not change the colour of a sky.
  var SOUTH = /^(Australia|Antarctica|Pacific\/(Auckland|Fiji|Tongatapu|Apia|Noumea|Norfolk|Chatham)|Africa\/(Johannesburg|Maputo|Harare|Lusaka|Windhoek|Gaborone)|America\/(Sao_Paulo|Argentina|Santiago|Montevideo|Asuncion|La_Paz|Lima|Bogota)|Indian\/(Mauritius|Reunion|Antananarivo))/;
  var place = null;
  function where(now) {
    if (place) return place;
    var d = now ? new Date(now) : new Date();
    var y = d.getFullYear();
    // getTimezoneOffset is minutes WEST of UTC; the larger of the two (winter, summer) is the standard time
    var std = Math.max(new Date(y, 0, 1).getTimezoneOffset(), new Date(y, 6, 1).getTimezoneOffset());
    var zone = "";
    try { zone = Intl.DateTimeFormat().resolvedOptions().timeZone || ""; } catch (e) { /* no Intl */ }
    place = { lat: SOUTH.test(zone) ? -33 : 52, lon: clamp(-std / 4, -180, 180) }; // 15 degrees per hour = 1 per 4 minutes
    return place;
  }

  /** The sky now (or at `ms`): the height of the sun and, at night, of the moon, and the lit part of the moon. */
  function now(ms) {
    if (ms == null) ms = Date.now();
    var at = where(ms);
    var sun = sunAt(ms, at.lat, at.lon), moon = moonAt(ms, at.lat, at.lon), ph = moonPhase(ms);
    return { ms: ms, sun: sun, moon: moon, phase: ph.phase, lit: ph.lit, elevation: sun.elevation };
  }

  /**
   * Where the sun (or, when it is down, the moon) stands on the screen, in per cent of the screen: it rises low on the
   * left of a patch of sky near the top right corner, is highest around noon and sets low on the right, so it follows
   * the time of day yet never slides behind the type (Rosa's SkyStage; the patch is higher than in the weather app, whose type
   * is shorter: here the first line of a screen — the wordmark of the first screen, the title of a page — is as wide as the
   * screen, and even a low sun must stay above it, behind the buttons of the top bar).
   * `visible` fades it in as it comes over the horizon.
   */
  function body(sky) {
    var useSun = sky.sun.elevation > -5;
    var b = useSun ? sky.sun : sky.moon;
    var path = (1 - Math.sin(b.azimuth * RAD)) / 2; // 0 in the east .. 1 in the west, at any latitude
    var lift = clamp(b.elevation / 50, -0.25, 1);
    return {
      isSun: useSun,
      x: (0.74 + (0.92 - 0.74) * clamp(path, 0, 1)) * 100,
      y: (0.078 - (0.078 - 0.035) * lift) * 100,
      visible: clamp((b.elevation + 2) / 5, 0, 1),
    };
  }

  /** sRGB blend of two colours: `t` of b over a (what the browser does with a translucent layer). */
  function over(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }

  /**
   * The colours of the first and the last row of the page's sky (css/rosa.css: the veil over the top of a vivid sky, and the
   * horizon under the tab bar). The phone app paints the strips under the status bar and the navigation bar with them, so
   * that the bars and the page are one picture.
   */
  function edges(p) {
    var veil = p.isLight ? 0 : 0.22 + 0.30 * p.daylight; // the same numbers as --sky-veil (css/rosa.css)
    return { top: over(p.zenith, p.deep, veil), bottom: p.horizon };
  }

  /**
   * Everything the page's CSS needs, as custom properties (css/rosa.css turns them into a sky, glass tints, ink and
   * accents): the colours as #rrggbb and as "r g b" for rgb(var(--x-rgb) / alpha).
   */
  function vars(p, b, phase) {
    var e = edges(p);
    var out = {
      "--sky-top": css(e.top), "--sky-bottom": css(e.bottom),
      "--sky-zen": css(p.zenith), "--sky-zen-rgb": rgb(p.zenith),
      "--sky-hor": css(p.horizon), "--sky-hor-rgb": rgb(p.horizon),
      // the way from the zenith to the horizon in OKLab: a gradient in sRGB would go through mud between blue and peach
      "--sky-m1": css(mix(p.zenith, p.horizon, 0.25)), "--sky-m2": css(mix(p.zenith, p.horizon, 0.5)), "--sky-m3": css(mix(p.zenith, p.horizon, 0.75)),
      "--sky-glow": css(p.glow), "--sky-glow-rgb": rgb(p.glow),
      "--sky-sun": css(p.sun), "--sky-sun-rgb": rgb(p.sun),
      "--sky-ink": css(p.ink), "--sky-ink-rgb": rgb(p.ink),
      "--sky-accent": css(p.accent), "--sky-accent-rgb": rgb(p.accent),
      "--sky-smoke": css(p.smoke), "--sky-smoke-rgb": rgb(p.smoke),
      "--sky-deep": css(p.deep), "--sky-deep-rgb": rgb(p.deep),
      "--sky-stars": String(Math.round(p.stars * 100) / 100),
      "--sky-daylight": String(Math.round(p.daylight * 100) / 100),
      "--sky-body": String(b ? Math.round(b.visible * 100) / 100 : 0),
      "--sky-body-x": (b ? Math.round(b.x * 10) / 10 : 80) + "%",
      "--sky-body-y": (b ? Math.round(b.y * 10) / 10 : 20) + "%",
      "--sky-moon-lit": String(Math.round((phase ? phase.lit : 0.5) * 100) / 100),
      "--sky-moon-waxing": phase && phase.phase >= 0.5 ? "-1" : "1",
    };
    return out;
  }

  /**
   * The sky of an appearance for `ms`: the palette, the sun or moon on the screen, the custom properties,
   * and the theme the app's own tokens should use ("light" = dark ink on a pale sky, "dark" = light ink).
   * `fixed` (a number, degrees) shows the sky at that height of the sun whatever the clock says (tests, screenshots).
   */
  function skyFor(appearance, ms, fixed) {
    var n = now(ms);
    if (typeof fixed === "number") {
      // the sun at that height (due south), and a moon high in the sky for a night: the picture does not depend on the date
      n.elevation = fixed;
      n.sun = { elevation: fixed, azimuth: 180 };
      n.moon = { elevation: 40, azimuth: 180 };
    }
    var p = palette(appearance, n.elevation, n.lit);
    // fixed moods keep no real sun or moon: a noon sun in "dark" would contradict the light it sets
    var b = p.appearance === "auto" ? body(n) : null;
    return { palette: p, body: b, phase: n, theme: p.isLight ? "light" : "dark", vars: vars(p, b, n) };
  }

  /** The custom properties between two skies (what vars() returns): colours are mixed in OKLab, numbers and percentages linearly. */
  function tween(a, b, t) {
    var out = {};
    for (var k in b) {
      var x = a[k], y = b[k];
      if (x === undefined || x === y) { out[k] = y; continue; }
      var cx = /^#([0-9a-f]{6})$/i.exec(x), cy = /^#([0-9a-f]{6})$/i.exec(y);
      if (cx && cy) { out[k] = css(mix(hex(parseInt(cx[1], 16)), hex(parseInt(cy[1], 16)), t)); continue; }
      var tx = /^(\d+) (\d+) (\d+)$/.exec(x), ty = /^(\d+) (\d+) (\d+)$/.exec(y);
      if (tx && ty) { out[k] = rgb(mix([+tx[1], +tx[2], +tx[3]], [+ty[1], +ty[2], +ty[3]], t)); continue; }
      var nx = /^(-?[\d.]+)(%?)$/.exec(x), ny = /^(-?[\d.]+)(%?)$/.exec(y);
      if (nx && ny && nx[2] === ny[2]) { out[k] = Math.round((+nx[1] + (+ny[1] - +nx[1]) * t) * 100) / 100 + ny[2]; continue; }
      out[k] = t < 0.5 ? x : y;
    }
    return out;
  }

  /** The lit part of the moon as a path in a disc of radius 46 (a waxing moon is lit on the right; a waning one is mirrored). */
  function moonPath(lit) {
    var rx = Math.abs(1 - 2 * lit) * 46;
    return "M0 -46A46 46 0 0 1 0 46A" + rx.toFixed(1) + " 46 0 0 " + (lit < 0.5 ? 0 : 1) + " 0 -46Z";
  }

  var set = [];
  /**
   * Puts the sky of an appearance on the page: the custom properties on `root`, the theme the tokens of the page follow,
   * the body that is up (the sun, the moon or none) and the shape of the moon. Returns what skyFor returns.
   */
  function apply(root, appearance, fixed) {
    var s = skyFor(appearance, undefined, fixed);
    for (var k in s.vars) { root.style.setProperty(k, s.vars[k]); if (set.indexOf(k) < 0) set.push(k); }
    root.setAttribute("data-theme", s.theme);
    root.setAttribute("data-appearance", s.palette.appearance);
    root.setAttribute("data-body", s.body ? (s.body.isSun ? "sun" : "moon") : "none");
    var lit = document.querySelector("#sky .moon__lit");
    if (lit) {
      lit.setAttribute("d", moonPath(s.phase.lit));
      lit.setAttribute("transform", s.phase.phase >= 0.5 ? "scale(-1 1)" : "");
    }
    api.last = s;
    return s;
  }
  /** Takes the sky off the page again (the look was switched to another one). */
  function clear(root) {
    for (var i = 0; i < set.length; i++) root.style.removeProperty(set[i]);
    set = [];
    root.removeAttribute("data-appearance");
    root.removeAttribute("data-body");
  }

  var api = g.themeshSky = {
    palette: palette, sky: skyFor, now: now, body: body, vars: vars, moonPhase: moonPhase, apply: apply, clear: clear, tween: tween,
    lum: lum, mix: mix, css: css, hex: hex, KEYS: KEYS,
    /** The sun's and the moon's place at a moment, and the shape of the lit moon, for tests. */
    sunAt: sunAt, moonAt: moonAt, moonPath: moonPath, edges: edges,
  };
})(window);
