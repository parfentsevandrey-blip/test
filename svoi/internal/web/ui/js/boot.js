// Classic (non-module) script loaded in <head>: picks the theme, the look and the
// language before the first paint so there is no flash of the wrong look.
// Keep the storage keys in sync with js/prefs.js. (js/sky-palette.js is loaded before this file.)
(function () {
  var theme = "auto", lang = "auto", skin = "auto", fx = "auto";
  try {
    theme = localStorage.getItem("themesh.theme") || "auto";
    lang = localStorage.getItem("themesh.lang") || "auto";
    skin = localStorage.getItem("themesh.skin") || "auto";
    fx = localStorage.getItem("themesh.fx") || "auto";
  } catch (e) { /* storage may be disabled */ }
  if (lang !== "ru" && lang !== "en") {
    var nav = (navigator.languages && navigator.languages[0]) || navigator.language || "ru";
    lang = /^(ru|uk|be|kk)\b/i.test(nav) ? "ru" : "en";
  }
  var root = document.documentElement;
  root.setAttribute("lang", lang);

  // The apps say in their user agent what they are and what their window can do:
  //   "TheMeshDesktop/0.1.0 (mac; skin=glass; vibrancy; inset)"   the desktop app
  //   "TheMeshAndroid/0.1.0 (android; skin=rosa)"                 the phone app
  //   = platform; the look to start with; a window that is see-through to the system material (or "reduced-transparency":
  //     macOS says "Reduce transparency", the panels are solid from the first picture on); no title bar
  // A browser says nothing and gets the classic look unless the person chose another.
  // The looks: "rosa" (a living sky and glass, the design of the weather app «Роса»), "glass" (Liquid Glass) and "classic".
  var skinByDefault = "classic", shell = "", vibrancy = false, inset = false, solid = false;
  var app = /TheMesh(?:Desktop|Android)\/\S+ \(([^)]*)\)/.exec(navigator.userAgent || "");
  if (app) {
    var facts = app[1].split(/;\s*/);
    shell = facts[0] || "desktop";
    for (var i = 1; i < facts.length; i++) {
      if (facts[i] === "vibrancy") vibrancy = true;
      if (facts[i] === "inset") inset = true;
      if (facts[i] === "reduced-transparency") solid = true;
      var m = /^skin=(rosa|glass|classic)$/.exec(facts[i]);
      if (m) skinByDefault = m[1];
    }
  }
  var asked = /[?&]skin=(rosa|glass|classic)(&|$)/.exec(location.search); // for tests and screenshots; not remembered
  if (asked) { skin = asked[1]; root.setAttribute("data-skin-url", skin); }
  if (skin !== "rosa" && skin !== "glass" && skin !== "classic") skin = skinByDefault;
  var sky = window.themeshSky;
  if (skin === "rosa" && !sky) skin = "glass"; // no palette script: no sky
  // "rosa" is a layer over the glass skin (css/rosa.css over css/glass.css): both attributes are set
  root.setAttribute("data-skin", skin === "classic" ? "classic" : "glass");
  root.setAttribute("data-skin-default", skinByDefault);
  if (shell) root.setAttribute("data-shell", shell);
  if (vibrancy) root.setAttribute("data-vibrancy", "on");
  if (inset) root.setAttribute("data-titlebar", "inset");
  if (solid) root.setAttribute("data-reduce-transparency", "");

  // Theme. In the "rosa" look the sky decides: "auto" is the real sky now (dark type on a pale sky only in the Light
  // appearance), and "light", "evening" and "dark" are fixed moods of it; elsewhere "auto" follows the system.
  var lit = null;
  if (skin === "rosa") {
    root.setAttribute("data-look", "rosa");
    var fixed = /[?&]sky=(-?\d+(?:\.\d+)?)(&|$)/.exec(location.search); // the height of the sun in degrees, for tests and screenshots
    if (fixed) root.setAttribute("data-sky-fixed", fixed[1]);
    lit = sky.apply(root, theme, fixed ? Number(fixed[1]) : undefined);
    theme = lit.theme;
    // the interface face is fetched at once, not when the first text is laid out: no flash of the system font at every start
    var face = document.createElement("link");
    face.rel = "preload"; face.as = "font"; face.type = "font/woff2"; face.crossOrigin = "anonymous"; face.href = "fonts/manrope.woff2";
    document.head.appendChild(face);
  } else {
    if (theme === "evening") theme = "dark";
    if (theme !== "light" && theme !== "dark") {
      theme = window.matchMedia && matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    }
    root.setAttribute("data-theme", theme);
  }

  // How much the page moves: "full" (everything), "calm" (panes fade in, no tilting, no twinkling), "still" (nothing).
  // "auto" is full unless the system asks for less motion (js/rosa.js also steps down when the screen cannot keep up).
  if (fx !== "full" && fx !== "calm" && fx !== "still") {
    fx = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches ? "still" : "full";
  }
  root.setAttribute("data-fx", fx);

  // The phone app paints the system bars (and, in the glass look, the backdrop behind a see-through page) itself, so that
  // the status bar, the navigation bar and the page are one picture; it is told the look of the page now (and by js/prefs.js
  // whenever it changes) to match it. In the "rosa" look the page paints its own sky and the app only needs its colours.
  if (shell === "android") {
    try {
      window.themeshShell.look(theme, skin);
      if (skin === "rosa") {
        if (typeof window.themeshShell.sky === "function") window.themeshShell.sky(lit.vars["--sky-top"], lit.vars["--sky-bottom"], lit.palette.isLight);
      } else {
        root.setAttribute("data-native-backdrop", "");
      }
    } catch (e) { /* no bridge (a browser pretending to be the app): the page paints its own backdrop */ }
  }
})();
