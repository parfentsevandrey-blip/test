// startscreen.js — the template gallery + recent-files view shown in place
// of the editor canvas (see .start-screen/.start-card/.start-recent-item
// in theme.css). Shown on a fresh launch and from File ▸ New. Template
// previews are deliberately abstract (a few gray bars suggesting layout),
// not literal thumbnails — see README "Known limitations".
//
// This module knows nothing about fileio.js — the caller (renderer.js)
// wires it up with two callbacks (onTemplate/onRecent) to keep the module
// graph a simple tree instead of a cycle.

const TEMPLATES = [
  {
    id: 'blank',
    label: 'Blank',
    title: 'Untitled document',
    html: '<p><br></p>',
  },
  {
    id: 'letter',
    label: 'Letter',
    title: 'Untitled letter',
    html: `<p>123 Main Street<br>Springfield, ST 00000</p>
<p>July 6, 2026</p>
<p>Dear Alex,</p>
<p>I hope this note finds you well. I'm writing to follow up on our conversation last week and to confirm the details we discussed before you head out of town.</p>
<p>Please let me know if anything needs to change on my end — otherwise I'll go ahead as planned. Thank you again for your time and patience.</p>
<p>Warm regards,<br>Jordan</p>`,
  },
  {
    id: 'report',
    label: 'Report',
    title: 'Untitled report',
    html: `<h1>Quarterly Report</h1>
<h2>Executive Summary</h2>
<p>This report summarizes performance across the last quarter, highlighting key metrics, notable wins, and the areas that need attention heading into the next planning cycle.</p>
<h2>Key Findings</h2>
<p>Revenue grew steadily while operating costs stayed flat, driven largely by improved retention and a leaner onboarding process.</p>
<h2>Next Steps</h2>
<p>The team will prioritize three initiatives next quarter: expanding the referral program, simplifying the pricing page, and reducing support response time.</p>`,
  },
  {
    id: 'resume',
    label: 'Resume',
    title: 'Untitled resume',
    html: `<h1>Jordan Avery</h1>
<p>Product Designer &middot; jordan.avery@example.com &middot; (555) 012-3456</p>
<h2>Experience</h2>
<p><strong>Senior Product Designer</strong> — Northwind Co. (2022–Present)<br>Led end-to-end design for the onboarding and billing experiences, partnering closely with engineering and research.</p>
<p><strong>Product Designer</strong> — Fieldstone Labs (2019–2022)<br>Shipped design systems and core workflows for a B2B analytics platform used by thousands of teams.</p>
<h2>Education</h2>
<p>B.A. in Human-Computer Interaction, Rivermont University</p>`,
  },
];

// Abstract "line" widths per template, used to fake a page layout inside
// each .start-card__preview without rendering a real screenshot.
const PREVIEW_LINES = {
  letter: ['40%', '85%', '70%', '90%'],
  report: ['55%', '95%', '80%', '90%', '60%'],
  resume: ['50%', '90%', '75%', '40%', '85%'],
};

let screenEl = null;
let pageWrapperEl = null;
let handlers = { onTemplate: () => {}, onRecent: () => {} };

function buildPreview(tpl) {
  const preview = document.createElement('div');
  preview.className = 'start-card__preview';

  if (tpl.id === 'blank') {
    const icon = document.createElement('span');
    icon.className = 'start-card__preview-icon';
    icon.innerHTML = (window.lumen && window.lumen.icons && window.lumen.icons['file-plus']) || '';
    preview.appendChild(icon);
    return preview;
  }

  const widths = PREVIEW_LINES[tpl.id] || ['80%', '60%', '70%'];
  widths.forEach((width, i) => {
    const line = document.createElement('div');
    line.className = i === 0 ? 'start-card__preview-line start-card__preview-line--title' : 'start-card__preview-line';
    line.style.width = width;
    preview.appendChild(line);
  });
  return preview;
}

function buildTemplateGrid() {
  const grid = document.createElement('div');
  grid.className = 'start-screen__grid';
  for (const tpl of TEMPLATES) {
    const card = document.createElement('div');
    card.className = 'start-card';
    card.appendChild(buildPreview(tpl));
    const label = document.createElement('div');
    label.className = 'start-card__label';
    label.textContent = tpl.label;
    card.appendChild(label);
    card.addEventListener('click', () => handlers.onTemplate(tpl.html, tpl.title));
    grid.appendChild(card);
  }
  return grid;
}

function formatOpenedAt(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

async function fetchRecent() {
  try {
    const list = await window.lumen.getRecentFiles();
    return Array.isArray(list) ? list : [];
  } catch (err) {
    return [];
  }
}

function buildRecentSection(entries) {
  if (!entries.length) return null;

  const wrap = document.createElement('div');
  wrap.className = 'start-screen__recent';

  const label = document.createElement('div');
  label.className = 'start-screen__section-label';
  label.textContent = 'Recent';
  wrap.appendChild(label);

  for (const entry of entries) {
    const item = document.createElement('div');
    item.className = 'start-recent-item';

    const name = document.createElement('span');
    name.className = 'start-recent-item__name';
    name.textContent = entry.title || entry.path;

    const meta = document.createElement('span');
    meta.className = 'start-recent-item__meta';
    meta.textContent = formatOpenedAt(entry.openedAt);

    item.appendChild(name);
    item.appendChild(meta);
    item.addEventListener('click', () => handlers.onRecent(entry.path));
    wrap.appendChild(item);
  }
  return wrap;
}

async function render() {
  screenEl.innerHTML = '';

  const title = document.createElement('div');
  title.className = 'start-screen__title';
  title.textContent = 'Lumen Write';

  const subtitle = document.createElement('div');
  subtitle.className = 'start-screen__subtitle';
  subtitle.textContent = 'Start a new document, or pick up where you left off.';

  const sectionLabel = document.createElement('div');
  sectionLabel.className = 'start-screen__section-label';
  sectionLabel.textContent = 'New document';

  screenEl.appendChild(title);
  screenEl.appendChild(subtitle);
  screenEl.appendChild(sectionLabel);
  screenEl.appendChild(buildTemplateGrid());

  const recentSection = buildRecentSection(await fetchRecent());
  if (recentSection) screenEl.appendChild(recentSection);
}

/** Wire the start screen up with callbacks for template/recent selection.
 * Must be called once before showStartScreen(). */
export function initStartScreen({ onTemplate, onRecent }) {
  handlers = {
    onTemplate: onTemplate || (() => {}),
    onRecent: onRecent || (() => {}),
  };
  screenEl = document.getElementById('start-screen');
  pageWrapperEl = document.getElementById('page-wrapper');
}

export async function showStartScreen() {
  if (!screenEl) return;
  // Flip visibility first (synchronously) so there's no flash of the bare
  // editor page while the recent-files list round-trips over IPC below.
  if (pageWrapperEl) pageWrapperEl.hidden = true;
  screenEl.hidden = false;
  await render();
}

export function hideStartScreen() {
  if (!screenEl) return;
  screenEl.hidden = true;
  if (pageWrapperEl) pageWrapperEl.hidden = false;
}

export function isStartScreenVisible() {
  return !!screenEl && !screenEl.hidden;
}
