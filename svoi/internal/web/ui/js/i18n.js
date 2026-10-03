// i18n: t("key", {vars}), tn("key", n) with Russian plural rules, tx() for
// messages that embed components. Dictionaries are flat { "section.key": "text" }.
import ru from "./i18n/ru.js";
import en from "./i18n/en.js";

const dicts = { ru, en };
let lang = document.documentElement.getAttribute("lang") === "en" ? "en" : "ru";

export function getLang() { return lang; }
export function locale() { return lang === "ru" ? "ru-RU" : "en-GB"; }

export function setLang(l) {
  lang = l === "en" ? "en" : "ru";
  document.documentElement.setAttribute("lang", lang);
}

function lookup(key) {
  const d = dicts[lang];
  if (d && Object.prototype.hasOwnProperty.call(d, key)) return d[key];
  if (Object.prototype.hasOwnProperty.call(dicts.ru, key)) return dicts.ru[key];
  return undefined;
}

function interpolate(s, vars) {
  if (!vars) return s;
  return s.replace(/\{(\w+)\}/g, (m, k) => (vars[k] !== undefined && vars[k] !== null ? String(vars[k]) : m));
}

export function has(key) {
  return lookup(key) !== undefined;
}

/** Translate a key. Missing keys render as the key itself (easy to spot). */
export function t(key, vars) {
  let s = lookup(key);
  if (s === undefined) return key;
  if (Array.isArray(s)) s = s[s.length - 1];
  return interpolate(s, vars);
}

/** Index of the plural form: ru → one/few/many, en → one/other. */
export function pluralIndex(n, l = lang) {
  const a = Math.abs(n);
  if (l === "ru") {
    if (!Number.isInteger(a)) return 1;
    const m10 = a % 10, m100 = a % 100;
    if (m10 === 1 && m100 !== 11) return 0;
    if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return 1;
    return 2;
  }
  return a === 1 ? 0 : 1;
}

/** Plural translation: dictionary value is an array of forms with "{n}". */
export function tn(key, n, vars) {
  const forms = lookup(key);
  if (forms === undefined) return `${key}:${n}`;
  const arr = Array.isArray(forms) ? forms : [forms];
  const s = arr[Math.min(pluralIndex(n), arr.length - 1)];
  return interpolate(s, { n: new Intl.NumberFormat(locale()).format(n), ...vars });
}

/** Like t() but placeholders may be vnodes; returns an array suitable as children. */
export function tx(key, vars = {}) {
  const s = t(key);
  const out = [];
  let last = 0;
  s.replace(/\{(\w+)\}/g, (m, k, idx) => {
    out.push(s.slice(last, idx));
    out.push(vars[k] !== undefined ? vars[k] : m);
    last = idx + m.length;
    return m;
  });
  out.push(s.slice(last));
  return out.filter((x) => x !== "");
}
