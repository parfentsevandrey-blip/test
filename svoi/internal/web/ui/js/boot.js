// Classic (non-module) script loaded in <head>: picks the theme and language
// before the first paint so there is no flash of the wrong theme.
// Keep the storage keys in sync with js/prefs.js.
(function () {
  var theme = "auto", lang = "auto";
  try {
    theme = localStorage.getItem("svoi.theme") || "auto";
    lang = localStorage.getItem("svoi.lang") || "auto";
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
})();
