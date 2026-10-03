// Locale-aware formatting of sizes, speeds, times and durations.
import { getLang, locale, t, tn } from "./i18n.js";
import { nowSec } from "./store.js";

const nf = (opts) => new Intl.NumberFormat(locale(), opts);

export function fmtNumber(n, digits = 0) {
  return nf({ maximumFractionDigits: digits }).format(n || 0);
}

const UNITS = {
  ru: ["Б", "КБ", "МБ", "ГБ", "ТБ"],
  en: ["B", "KB", "MB", "GB", "TB"],
};

/** 1536 → "1,5 КБ" / "1.5 KB" (binary steps, short and friendly). */
export function fmtBytes(n) {
  if (n === null || n === undefined || isNaN(n)) return "—";
  const u = UNITS[getLang()] || UNITS.en;
  let i = 0;
  let v = Math.max(0, n);
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  const digits = i === 0 ? 0 : v < 10 ? 1 : 0;
  return `${nf({ maximumFractionDigits: digits }).format(v)} ${u[i]}`;
}

export function fmtSpeed(bps) {
  if (!bps) return "";
  return `${fmtBytes(bps)}/${getLang() === "ru" ? "с" : "s"}`;
}

export function fmtRtt(ms) {
  if (!ms) return "";
  const digits = ms < 10 ? 1 : 0;
  return `${nf({ maximumFractionDigits: digits }).format(ms)} ${getLang() === "ru" ? "мс" : "ms"}`;
}

export function fmtPercent(done, total) {
  if (!total) return "0 %";
  const p = Math.max(0, Math.min(100, Math.floor((done / total) * 100)));
  return getLang() === "ru" ? `${p} %` : `${p}%`;
}

const d = (ts) => new Date(ts * 1000);

export function fmtTime(ts) {
  if (!ts) return "—";
  return d(ts).toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" });
}

export function fmtDate(ts, { year } = {}) {
  if (!ts) return "—";
  const date = d(ts);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(locale(), {
    day: "numeric", month: "short", year: year || !sameYear ? "numeric" : undefined,
  });
}

export function fmtDateTime(ts) {
  if (!ts) return "—";
  return `${fmtDate(ts)}, ${fmtTime(ts)}`;
}

function startOfDay(date) {
  const x = new Date(date);
  x.setHours(0, 0, 0, 0);
  return x.getTime();
}

/** Day difference between ts and today in local time (0 = today, 1 = yesterday). */
export function dayDiff(ts) {
  return Math.round((startOfDay(Date.now()) - startOfDay(ts * 1000)) / 86400000);
}

/** Compact list-style date: 14:05 · вчера · 3 окт. · 03.10.2023 */
export function fmtShortDate(ts) {
  if (!ts) return "";
  const dd = dayDiff(ts);
  if (dd <= 0) return fmtTime(ts);
  if (dd === 1) return t("time.yesterday");
  const date = d(ts);
  if (date.getFullYear() === new Date().getFullYear()) {
    return date.toLocaleDateString(locale(), { day: "numeric", month: "short" });
  }
  return date.toLocaleDateString(locale(), { day: "2-digit", month: "2-digit", year: "numeric" });
}

/** Day separator in chats: Сегодня · Вчера · 3 октября */
export function fmtDay(ts) {
  const dd = dayDiff(ts);
  if (dd === 0) return t("time.today");
  if (dd === 1) return t("time.yesterday");
  const date = d(ts);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(locale(), { day: "numeric", month: "long", year: sameYear ? undefined : "numeric" });
}

/** "только что", "5 мин назад", "3 ч назад", "вчера", "12 дн. назад"… */
export function fmtAgo(ts) {
  if (!ts) return t("time.never");
  const s = Math.max(0, nowSec() - ts);
  if (s < 45) return t("time.justNow");
  if (s < 3600) return tn("time.minAgo", Math.max(1, Math.round(s / 60)));
  const days = dayDiff(ts); // calendar days, so "yesterday" means yesterday
  if (days <= 0 || s < 6 * 3600) return tn("time.hourAgo", Math.max(1, Math.round(s / 3600)));
  if (days === 1) return t("time.yesterday");
  if (days < 30) return tn("time.dayAgo", days);
  return fmtDate(ts);
}

/** Uptime-like durations: "3 д 4 ч", "12 мин". */
export function fmtDuration(sec) {
  if (!sec || sec < 0) return "—";
  const ru = getLang() === "ru";
  const u = ru ? { d: "д", h: "ч", m: "мин", s: "с" } : { d: "d", h: "h", m: "min", s: "s" };
  const days = Math.floor(sec / 86400);
  const hours = Math.floor((sec % 86400) / 3600);
  const mins = Math.floor((sec % 3600) / 60);
  if (days > 0) return hours ? `${days} ${u.d} ${hours} ${u.h}` : `${days} ${u.d}`;
  if (hours > 0) return mins ? `${hours} ${u.h} ${mins} ${u.m}` : `${hours} ${u.h}`;
  if (mins > 0) return `${mins} ${u.m}`;
  return `${Math.floor(sec)} ${u.s}`;
}

/** Countdown "14:59" / "1:02:03". */
export function fmtCountdown(sec) {
  sec = Math.max(0, Math.floor(sec));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  const p = (x) => String(x).padStart(2, "0");
  return h ? `${h}:${p(m)}:${p(s)}` : `${m}:${p(s)}`;
}

/** Remaining time for transfers: "≈ 2 мин". */
export function fmtEta(remaining, speed) {
  if (!speed || remaining <= 0) return "";
  const sec = remaining / speed;
  if (sec < 60) return t("time.etaSec");
  return `≈ ${fmtDuration(sec)}`;
}
