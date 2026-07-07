// pagination.js — turns the single, continuous contentEditable `#page`
// into the *illusion* of discrete US-Letter pages.
//
// The editable content itself never gets split into multiple
// contentEditable regions (that would break cursor/selection continuity).
// Instead:
//   1. We measure the page's real top-level children (paragraphs,
//      headings, tables, images, ...) and figure out where a page's
//      1056px frame would end.
//   2. Any child that would otherwise straddle a page boundary gets a
//      `padding-top` push (tracked via `data-lw-pushed`) that shoves it
//      (and everything after it, for free, via normal block flow) down
//      into the next page's frame — reproducing the top/bottom margins
//      and the inter-page gap entirely through invisible spacing.
//   3. A separate, purely decorative "page-frames" layer (white page
//      boxes + "Page N" gap labels + header/footer bands) is rendered
//      behind the content, sized/positioned to match.
//
// Because the push amounts are computed from real layout measurements on
// every (debounced) edit, this is genuinely dynamic: typing past a page
// boundary grows the pushes (and, when needed, adds a page); deleting
// content shrinks/removes them again.

import { getPage, onChange } from './editor.js';

// ---------- Page geometry (Page Setup) ----------
// Everything below was a fixed set of consts before the Page Setup feature
// existed; it's now mutable state recomputed by applyGeometry() whenever
// the user changes page size/margins (File ▸ Page Setup). All downstream
// pagination math (push logic, ruler, frames, header/footer bands) reads
// these `let`s live, so a Page Setup change just needs to recompute them
// and force a re-render — no other function needs to know Page Setup
// exists.

const DPI = 96;

/** US Letter / A4 / Legal at 96dpi, rounded to whole pixels. */
export const PAGE_SIZES = {
  letter: { label: 'Letter (8.5" × 11")', w: 816, h: 1056 },
  a4: { label: 'A4 (8.27" × 11.69")', w: 794, h: 1123 },
  legal: { label: 'Legal (8.5" × 14")', w: 816, h: 1344 },
};

/** Symmetric margin presets, in inches. 'custom' has no fixed values — the
 * dialog collects four independent numbers for it instead. */
export const MARGIN_PRESETS = {
  normal: { label: 'Normal (1")', values: { top: 1, bottom: 1, left: 1, right: 1 } },
  narrow: { label: 'Narrow (0.5")', values: { top: 0.5, bottom: 0.5, left: 0.5, right: 0.5 } },
  wide: { label: 'Wide (1.5")', values: { top: 1.5, bottom: 1.5, left: 1.5, right: 1.5 } },
};

export const MARGIN_MIN_IN = 0.25;
export const MARGIN_MAX_IN = 3;

export const DEFAULT_PAGE_SETUP = {
  sizeKey: 'letter',
  marginKey: 'normal',
  marginsIn: { ...MARGIN_PRESETS.normal.values },
};

let currentSizeKey = DEFAULT_PAGE_SETUP.sizeKey;
let currentMarginKey = DEFAULT_PAGE_SETUP.marginKey;
let currentMarginsIn = { ...DEFAULT_PAGE_SETUP.marginsIn };

let PAGE_W = PAGE_SIZES[currentSizeKey].w;
let PAGE_H = PAGE_SIZES[currentSizeKey].h;
let MARGIN_TOP = Math.round(currentMarginsIn.top * DPI);
let MARGIN_BOTTOM = Math.round(currentMarginsIn.bottom * DPI);
let MARGIN_LEFT = Math.round(currentMarginsIn.left * DPI);
let MARGIN_RIGHT = Math.round(currentMarginsIn.right * DPI);
const GAP = 40; // inter-page gap — not user-configurable, purely a screen affordance
let CONTENT_H = PAGE_H - MARGIN_TOP - MARGIN_BOTTOM;
let STRIDE = PAGE_H + GAP; // vertical distance between two pages' content windows
let HEADER_BAND_H = 40;
let FOOTER_BAND_H = 40;
let HEADER_BAND_TOP = 28; // within the top margin
let FOOTER_BAND_TOP = PAGE_H - MARGIN_BOTTOM + 28; // within the bottom margin

function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n));
}

/** Recomputes every geometry value that derives from PAGE_W/PAGE_H/margins.
 * Header/footer band height scales down for very narrow margins (so a
 * 0.5in-margin document doesn't get a band taller than its own margin)
 * but otherwise matches the original fixed 40px/28px band the app shipped
 * with at the default 1in margin. */
function recomputeDerivedGeometry() {
  CONTENT_H = Math.max(50, PAGE_H - MARGIN_TOP - MARGIN_BOTTOM);
  STRIDE = PAGE_H + GAP;
  HEADER_BAND_H = clamp(MARGIN_TOP * 0.5, 16, 40);
  FOOTER_BAND_H = clamp(MARGIN_BOTTOM * 0.5, 16, 40);
  HEADER_BAND_TOP = (MARGIN_TOP - HEADER_BAND_H) / 2;
  FOOTER_BAND_TOP = PAGE_H - MARGIN_BOTTOM + (MARGIN_BOTTOM - FOOTER_BAND_H) / 2;
}

/** Pushes the current geometry out as CSS custom properties so app.css's
 * page/ruler/frame/band rules (which reference var(--lw-page-w) etc.)
 * pick up the new size without any per-element inline-style bookkeeping. */
function applyGeometryCSSVars() {
  const root = document.documentElement.style;
  root.setProperty('--lw-page-w', `${PAGE_W}px`);
  root.setProperty('--lw-page-h', `${PAGE_H}px`);
  root.setProperty('--lw-margin-top', `${MARGIN_TOP}px`);
  root.setProperty('--lw-margin-bottom', `${MARGIN_BOTTOM}px`);
  root.setProperty('--lw-margin-left', `${MARGIN_LEFT}px`);
  root.setProperty('--lw-margin-right', `${MARGIN_RIGHT}px`);
}

const DEBOUNCE_MS = 200;

let pageEl = null;
let docScrollEl = null;
let framesLayerEl = null;
let bandsLayerEl = null;
let rulerEl = null;

let frames = []; // [{ el, headerEl, footerEl, labelEl }]
let currentPageCount = 1;
let currentViewedPage = 1;
let intersectionObserver = null;

let headerRaw = '';
let footerRaw = '';

let debounceTimer = null;

const paginationListeners = [];

export function onPaginationChange(fn) {
  paginationListeners.push(fn);
}

function notifyPaginationChange() {
  for (const fn of paginationListeners) fn({ current: currentViewedPage, total: currentPageCount });
}

// ---------- Header/footer token resolution ----------

function resolveTemplate(raw, pageNumber, totalPages) {
  return raw.replace(/\{n\}/g, String(pageNumber)).replace(/\{pages\}/g, String(totalPages));
}

export function getHeaderRaw() {
  return headerRaw;
}

export function getFooterRaw() {
  return footerRaw;
}

/** Used when opening/loading a document (fileio.js) to restore saved
 * header/footer text. */
export function setHeaderFooterRaw(header, footer) {
  headerRaw = header || '';
  footerRaw = footer || '';
  renderAllBands();
}

// ---------- DOM scaffolding (ruler, frames, header/footer bands) ----------

function buildRuler() {
  rulerEl.innerHTML = '';
  rulerEl.appendChild(makeMarginBand(0, MARGIN_LEFT));
  rulerEl.appendChild(makeMarginBand(PAGE_W - MARGIN_RIGHT, MARGIN_RIGHT));

  const INCH = DPI;
  const wholeInches = Math.floor(PAGE_W / INCH);
  for (let inch = 0; inch <= wholeInches; inch++) {
    rulerEl.appendChild(makeTick(inch * INCH, true, String(inch)));
    if (inch < wholeInches) rulerEl.appendChild(makeTick(inch * INCH + INCH / 2, false, ''));
  }
  // Final partial tick at the true right edge (e.g. 8.5in for Letter/Legal,
  // 8.27in for A4) if the page width isn't a whole number of inches.
  if (PAGE_W > wholeInches * INCH) {
    rulerEl.appendChild(makeTick(PAGE_W, true, ''));
  }
}

function makeMarginBand(left, width) {
  const band = document.createElement('div');
  band.className = 'ruler__margin';
  band.style.left = `${left}px`;
  band.style.width = `${width}px`;
  return band;
}

function makeTick(left, major, label) {
  const tick = document.createElement('div');
  tick.className = major ? 'ruler__tick' : 'ruler__tick ruler__tick--minor';
  tick.style.left = `${left}px`;
  if (label) {
    const span = document.createElement('span');
    span.className = 'ruler__tick-label';
    span.textContent = label;
    tick.appendChild(span);
  }
  return tick;
}

function buildFrame(index) {
  const frame = document.createElement('div');
  frame.className = 'page-frame';
  frame.style.top = `${index * STRIDE}px`;
  framesLayerEl.appendChild(frame);

  // Header/footer bands live in the separate .page-bands layer (a sibling
  // stacking context *above* #page) so they can actually receive clicks —
  // see the .page-bands comment in app.css for why they can't just be
  // z-index'd children of the (necessarily behind-#page) frame itself.
  const headerEl = document.createElement('div');
  headerEl.className = 'page-frame__band page-frame__band--header';
  headerEl.contentEditable = 'true';
  headerEl.spellcheck = false;
  headerEl.dataset.role = 'header';
  headerEl.style.top = `${index * STRIDE + HEADER_BAND_TOP}px`;
  headerEl.style.height = `${HEADER_BAND_H}px`;

  const footerEl = document.createElement('div');
  footerEl.className = 'page-frame__band page-frame__band--footer';
  footerEl.contentEditable = 'true';
  footerEl.spellcheck = false;
  footerEl.dataset.role = 'footer';
  footerEl.style.top = `${index * STRIDE + FOOTER_BAND_TOP}px`;
  footerEl.style.height = `${FOOTER_BAND_H}px`;

  bandsLayerEl.appendChild(headerEl);
  bandsLayerEl.appendChild(footerEl);

  headerEl.addEventListener('input', () => onBandInput('header', headerEl));
  footerEl.addEventListener('input', () => onBandInput('footer', footerEl));
  headerEl.addEventListener('blur', renderAllBands);
  footerEl.addEventListener('blur', renderAllBands);

  return { el: frame, headerEl, footerEl, labelEl: null };
}

function onBandInput(role, el) {
  if (role === 'header') headerRaw = el.textContent;
  else footerRaw = el.textContent;
  markHeaderFooterDirty();
  renderAllBands();
}

const headerFooterDirtyListeners = [];
export function onHeaderFooterChange(fn) {
  headerFooterDirtyListeners.push(fn);
}
function markHeaderFooterDirty() {
  for (const fn of headerFooterDirtyListeners) fn();
}

function renderAllBands() {
  frames.forEach((frame, i) => {
    const pageNumber = i + 1;
    if (document.activeElement !== frame.headerEl) {
      frame.headerEl.textContent = resolveTemplate(headerRaw, pageNumber, currentPageCount);
    }
    if (document.activeElement !== frame.footerEl) {
      frame.footerEl.textContent = resolveTemplate(footerRaw, pageNumber, currentPageCount);
    }
  });
}

function ensureFrameCount(n) {
  let changed = false;
  while (frames.length < n) {
    frames.push(buildFrame(frames.length));
    changed = true;
  }
  while (frames.length > n) {
    const frame = frames.pop();
    frame.el.remove();
    frame.headerEl.remove();
    frame.footerEl.remove();
    if (frame.labelEl) frame.labelEl.remove();
    changed = true;
  }

  frames.forEach((frame, i) => {
    const needsLabel = i < frames.length - 1;
    if (needsLabel && !frame.labelEl) {
      const label = document.createElement('div');
      label.className = 'page-frame__gap-label';
      framesLayerEl.appendChild(label);
      frame.labelEl = label;
    }
    if (!needsLabel && frame.labelEl) {
      frame.labelEl.remove();
      frame.labelEl = null;
    }
    if (frame.labelEl) {
      frame.labelEl.style.top = `${i * STRIDE + PAGE_H}px`;
      frame.labelEl.textContent = `Page ${i + 2}`;
    }
  });

  const totalHeight = n * PAGE_H + (n - 1) * GAP;
  framesLayerEl.style.height = `${totalHeight}px`;
  pageEl.style.minHeight = `${totalHeight}px`;

  if (changed) setupIntersectionObserver();
}

/** Tears down every frame/band/label so the next paginateNow() rebuilds
 * them from scratch with fresh geometry — used after a Page Setup change,
 * since STRIDE/HEADER_BAND_TOP/etc. baked into each frame's inline styles
 * at creation time would otherwise go stale. */
function destroyAllFrames() {
  frames.forEach((frame) => {
    frame.el.remove();
    frame.headerEl.remove();
    frame.footerEl.remove();
    if (frame.labelEl) frame.labelEl.remove();
  });
  frames = [];
}

function setupIntersectionObserver() {
  if (intersectionObserver) intersectionObserver.disconnect();
  if (!docScrollEl || !frames.length) return;
  intersectionObserver = new IntersectionObserver(
    (entries) => {
      let best = null;
      for (const entry of entries) {
        if (entry.isIntersecting && (!best || entry.intersectionRatio > best.intersectionRatio)) {
          best = entry;
        }
      }
      if (best) {
        const idx = frames.findIndex((f) => f.el === best.target);
        if (idx !== -1 && idx + 1 !== currentViewedPage) {
          currentViewedPage = idx + 1;
          notifyPaginationChange();
        }
      }
    },
    { root: docScrollEl, threshold: [0, 0.1, 0.25, 0.5, 0.75, 1] }
  );
  frames.forEach((f) => intersectionObserver.observe(f.el));
}

// ---------- Content measurement / push logic ----------

/** Any direct child of #page that's a bare, non-empty text node (Chromium
 * inserts these for the very first line typed into an empty
 * contentEditable) gets wrapped in a <div> so it can be measured/pushed
 * like every other top-level block. The text node itself is *moved*
 * (not recreated), so an in-progress selection/caret inside it survives. */
function normalizeLooseTextNodes() {
  const kids = Array.from(pageEl.childNodes);
  for (const node of kids) {
    if (node.nodeType === Node.TEXT_NODE) {
      if (!node.textContent.length) {
        node.remove();
        continue;
      }
      const wrapper = document.createElement('div');
      pageEl.insertBefore(wrapper, node);
      wrapper.appendChild(node);
    }
  }
}

function clearPushes() {
  const pushed = pageEl.querySelectorAll('[data-lw-pushed]');
  pushed.forEach((el) => {
    el.style.paddingTop = '';
    el.removeAttribute('data-lw-pushed');
  });
}

/** Measures the page's real content and pushes any block that would
 * straddle a page boundary down into the next page's window. Returns the
 * number of pages the content now requires. */
function computeAndApplyPushes() {
  normalizeLooseTextNodes();
  clearPushes();

  const children = Array.from(pageEl.children);
  if (!children.length) return 1;

  let pageIndex = 0; // 0-based index of the page window we're currently filling
  let windowEnd = MARGIN_TOP + CONTENT_H; // 960
  let shift = 0;
  let maxPageIndexTouched = 0;

  for (const child of children) {
    const naturalTop = child.offsetTop;
    const naturalHeight = child.offsetHeight;
    let effTop = naturalTop + shift;
    let effBottom = effTop + naturalHeight;

    // Advance the window if a previous push already carried us past it.
    while (effTop > windowEnd + 0.5) {
      pageIndex += 1;
      const windowStart = MARGIN_TOP + pageIndex * STRIDE;
      windowEnd = windowStart + CONTENT_H;
    }

    if (effBottom > windowEnd + 0.5 && naturalHeight <= CONTENT_H) {
      // Doesn't fit in the remaining space on this page, but does fit on
      // a fresh page — push it down to the start of the next one.
      pageIndex += 1;
      const nextWindowStart = MARGIN_TOP + pageIndex * STRIDE;
      const push = nextWindowStart - effTop;
      shift += push;
      effTop += push;
      effBottom += push;
      windowEnd = nextWindowStart + CONTENT_H;

      child.style.paddingTop = `${push}px`;
      child.setAttribute('data-lw-pushed', '1');
    }

    // Record which page this block *starts* on (1-based) so callers that
    // need real page numbers per block — the Table of Contents, notably —
    // can read it straight off the DOM instead of re-deriving it.
    child.dataset.lwPage = String(pageIndex + 1);

    const bottomPageIndex = Math.max(pageIndex, Math.floor((effBottom - MARGIN_TOP) / STRIDE));
    maxPageIndexTouched = Math.max(maxPageIndexTouched, pageIndex, bottomPageIndex);
  }

  return maxPageIndexTouched + 1;
}

/** Looks up the page number (1-based) a given descendant of #page starts
 * on, using the data-lw-page markers computeAndApplyPushes() leaves on
 * every top-level block. Walks up to the nearest top-level child first,
 * since headings/etc. are usually (but not necessarily) direct children
 * themselves. Falls back to page 1 if pagination hasn't run yet. */
export function getPageNumberForElement(el) {
  if (!pageEl || !el) return 1;
  let node = el;
  while (node && node.parentNode !== pageEl) node = node.parentNode;
  const raw = node && node.dataset && node.dataset.lwPage;
  return raw ? Number(raw) || 1 : 1;
}

// ---------- Public API ----------

export function paginateNow() {
  if (!pageEl) return;
  const pageCount = computeAndApplyPushes();
  currentPageCount = pageCount;
  // Adds/removes trailing frames (and re-observes them for the "which
  // page am I looking at" tracking) so existing pages' header/footer
  // bands are never rebuilt/refocused just because the count changed.
  ensureFrameCount(pageCount);
  if (currentViewedPage > pageCount) currentViewedPage = pageCount;
  renderAllBands();
  notifyPaginationChange();
}

function schedulePagination() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(paginateNow, DEBOUNCE_MS);
}

export function getPageCount() {
  return currentPageCount;
}

// ---------- Page Setup ----------

const pageSetupChangeListeners = [];
/** Fired after a *user-initiated* page setup change (i.e. via setPageSetup,
 * not restorePageSetup) — fileio.js uses this to mark the document dirty,
 * mirroring onHeaderFooterChange. */
export function onPageSetupChange(fn) {
  pageSetupChangeListeners.push(fn);
}
function notifyPageSetupChange() {
  for (const fn of pageSetupChangeListeners) fn();
}

export function getPageSetup() {
  return {
    sizeKey: currentSizeKey,
    marginKey: currentMarginKey,
    marginsIn: { ...currentMarginsIn },
  };
}

function applyPageSetupConfig(config) {
  const { sizeKey, marginKey, marginsIn } = { ...DEFAULT_PAGE_SETUP, ...config };
  currentSizeKey = PAGE_SIZES[sizeKey] ? sizeKey : DEFAULT_PAGE_SETUP.sizeKey;
  currentMarginKey = marginKey || DEFAULT_PAGE_SETUP.marginKey;
  const src = marginsIn || DEFAULT_PAGE_SETUP.marginsIn;
  currentMarginsIn = {
    top: clamp(Number(src.top) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
    bottom: clamp(Number(src.bottom) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
    left: clamp(Number(src.left) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
    right: clamp(Number(src.right) || 1, MARGIN_MIN_IN, MARGIN_MAX_IN),
  };

  const size = PAGE_SIZES[currentSizeKey];
  PAGE_W = size.w;
  PAGE_H = size.h;
  MARGIN_TOP = Math.round(currentMarginsIn.top * DPI);
  MARGIN_BOTTOM = Math.round(currentMarginsIn.bottom * DPI);
  MARGIN_LEFT = Math.round(currentMarginsIn.left * DPI);
  MARGIN_RIGHT = Math.round(currentMarginsIn.right * DPI);
  recomputeDerivedGeometry();
  applyGeometryCSSVars();

  if (pageEl) {
    destroyAllFrames();
    buildRuler();
    paginateNow();
  }
}

/** Applies a new page size/margins (File ▸ Page Setup) and re-paginates
 * immediately. Marks the document dirty via onPageSetupChange. */
export function setPageSetup(config) {
  applyPageSetupConfig(config);
  notifyPageSetupChange();
}

/** Same as setPageSetup, but silent — used when loading a document (New,
 * Open, templates) to restore its saved page setup (or fall back to the
 * default) without marking the freshly-loaded document dirty. Mirrors
 * setHeaderFooterRaw's load-time counterpart. */
export function restorePageSetup(config) {
  applyPageSetupConfig(config || DEFAULT_PAGE_SETUP);
}

export function initPagination() {
  pageEl = getPage();
  docScrollEl = document.getElementById('doc-scroll');
  framesLayerEl = document.getElementById('page-frames');
  bandsLayerEl = document.getElementById('page-bands');
  rulerEl = document.getElementById('ruler');

  applyGeometryCSSVars();
  buildRuler();
  onChange(schedulePagination);
  paginateNow();
}
