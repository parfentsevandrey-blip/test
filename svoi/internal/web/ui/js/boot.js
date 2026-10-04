// Classic (non-module) script loaded in <head>: picks the theme, the skin and the
// language before the first paint so there is no flash of the wrong look.
// Keep the storage keys in sync with js/prefs.js.
(function () {
  var theme = "auto", lang = "auto", skin = "auto";
  try {
    theme = localStorage.getItem("themesh.theme") || "auto";
    lang = localStorage.getItem("themesh.lang") || "auto";
    skin = localStorage.getItem("themesh.skin") || "auto";
  } catch (e) { /* storage may be disabled */ }
  if (theme !== "light" && theme !== "dark") {
    theme = window.matchMedia && matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }
  if (lang !== "ru" && lang !== "en") {
    var nav = (navigator.languages && navigator.languages[0]) || navigator.language || "ru";
    lang = /^(ru|uk|be|kk)\b/i.test(nav) ? "ru" : "en";
  }
  var root = document.documentElement;
  root.setAttribute("data-theme", theme);
  root.setAttribute("lang", lang);

  // The desktop app says in its user agent what it is and what its window can do:
  //   "TheMeshDesktop/0.1.0 (mac; skin=glass; vibrancy; inset)"
  //   = platform; the skin to start with; a window that is see-through to the system material; no title bar
  // A browser or the phone app says nothing and gets the classic skin unless the person chose another.
  var skinByDefault = "classic", shell = "", vibrancy = false, inset = false;
  var app = /TheMeshDesktop\/\S+ \(([^)]*)\)/.exec(navigator.userAgent || "");
  if (app) {
    var facts = app[1].split(/;\s*/);
    shell = facts[0] || "desktop";
    for (var i = 1; i < facts.length; i++) {
      if (facts[i] === "vibrancy") vibrancy = true;
      if (facts[i] === "inset") inset = true;
      var m = /^skin=(glass|classic)$/.exec(facts[i]);
      if (m) skinByDefault = m[1];
    }
  }
  var asked = /[?&]skin=(glass|classic)(&|$)/.exec(location.search); // for tests and screenshots; not remembered
  if (asked) { skin = asked[1]; root.setAttribute("data-skin-url", skin); }
  if (skin !== "glass" && skin !== "classic") skin = skinByDefault;
  root.setAttribute("data-skin", skin);
  root.setAttribute("data-skin-default", skinByDefault);
  if (shell) root.setAttribute("data-shell", shell);
  if (vibrancy) root.setAttribute("data-vibrancy", "on");
  if (inset) root.setAttribute("data-titlebar", "inset");
})();
