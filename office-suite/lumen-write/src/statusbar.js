// statusbar.js — word/char counts (recomputed on every editor change), the
// live pagination indicator, and the zoom slider, which scales the page
// frames/content via CSS `zoom` on #page-wrapper and the (separate, fixed)
// ruler via the same zoom applied to #ruler directly — see the ".ruler-row"
// comment in app.css for why the ruler now lives outside #page-wrapper.

import { getWordCharCounts, onChange } from './editor.js';
import { onPaginationChange } from './pagination.js';

const WORDS_PER_MINUTE = 200;

export function initStatusbar() {
  const wordCountEl = document.getElementById('word-count');
  const charCountEl = document.getElementById('char-count');
  const readingTimeEl = document.getElementById('reading-time');
  const pageIndicatorEl = document.getElementById('page-indicator');
  const zoomSlider = document.getElementById('zoom-slider');
  const zoomLabel = document.getElementById('zoom-label');
  const pageWrapper = document.getElementById('page-wrapper');
  const rulerEl = document.getElementById('ruler');
  const docScrollEl = document.getElementById('doc-scroll');

  function updateCounts() {
    const { words, chars } = getWordCharCounts();
    wordCountEl.textContent = `${words} word${words === 1 ? '' : 's'}`;
    charCountEl.textContent = `${chars} character${chars === 1 ? '' : 's'}`;
    const minutes = Math.max(1, Math.ceil(words / WORDS_PER_MINUTE));
    readingTimeEl.textContent = `${minutes} min read`;
  }

  onChange(updateCounts);
  updateCounts();

  onPaginationChange(({ current, total }) => {
    pageIndicatorEl.textContent = `Page ${current} of ${total}`;
  });

  zoomSlider.addEventListener('input', () => {
    const pct = Number(zoomSlider.value);
    zoomLabel.textContent = `${pct}%`;
    pageWrapper.style.zoom = String(pct / 100);
    // The ruler now lives outside .page-wrapper (see app.css's ".ruler-row"
    // comment) so it doesn't automatically inherit this zoom — apply it
    // separately to keep its tick spacing matching the zoomed page.
    if (rulerEl) rulerEl.style.zoom = String(pct / 100);
  });

  // ...and since it's outside the scrolling flow, it also doesn't
  // automatically pan when the zoomed page is wider than the window and
  // the user scrolls horizontally — nudge it to match by hand.
  if (docScrollEl && rulerEl) {
    docScrollEl.addEventListener('scroll', () => {
      rulerEl.style.transform = docScrollEl.scrollLeft ? `translateX(${-docScrollEl.scrollLeft}px)` : '';
    });
  }
}

export function zoomBy(delta) {
  const zoomSlider = document.getElementById('zoom-slider');
  const next = Math.min(200, Math.max(50, Number(zoomSlider.value) + delta));
  zoomSlider.value = String(next);
  zoomSlider.dispatchEvent(new Event('input'));
}

export function zoomReset() {
  const zoomSlider = document.getElementById('zoom-slider');
  zoomSlider.value = '100';
  zoomSlider.dispatchEvent(new Event('input'));
}
