// statusbar.js — word/char counts (recomputed on every editor change) and
// the zoom slider, which scales the page via CSS `zoom` on the wrapper.

import { getWordCharCounts, onChange } from './editor.js';

export function initStatusbar() {
  const wordCountEl = document.getElementById('word-count');
  const charCountEl = document.getElementById('char-count');
  const zoomSlider = document.getElementById('zoom-slider');
  const zoomLabel = document.getElementById('zoom-label');
  const pageWrapper = document.getElementById('page-wrapper');

  function updateCounts() {
    const { words, chars } = getWordCharCounts();
    wordCountEl.textContent = `${words} word${words === 1 ? '' : 's'}`;
    charCountEl.textContent = `${chars} character${chars === 1 ? '' : 's'}`;
  }

  onChange(updateCounts);
  updateCounts();

  zoomSlider.addEventListener('input', () => {
    const pct = Number(zoomSlider.value);
    zoomLabel.textContent = `${pct}%`;
    pageWrapper.style.zoom = String(pct / 100);
  });
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
