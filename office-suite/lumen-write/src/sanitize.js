// sanitize.js — hand-rolled HTML sanitizer for content arriving from
// OUTSIDE the app's own live editor: mammoth's .docx-import output, a
// directly-opened .html file's raw content, and a (possibly hand-edited /
// tampered) .lwrite file's contentHTML field. Applied once, at the single
// point any of those becomes contentHTML — applyOpenedResult() in
// fileio.js — so it covers all three uniformly.
//
// Do NOT call this on content that already lives in the editor (normal
// typing/formatting via execCommand/wrapSelection/insertHTML) — that path
// stays trusted, same as before.
//
// There's no bundler here and no npm sanitizer library reachable from the
// renderer (see README/CLAUDE notes), so this parses the incoming markup
// with the DOM itself — a <template> element's .content is parsed HTML
// that is inert by spec (scripts inside never execute, nothing fetches)
// — then walks every element with a TreeWalker and:
//   - drops <script>/<iframe>/<object>/<embed>/<link>/<meta>/<style>
//     entirely (element + subtree — a <script>'s text is code, not
//     content, so unwrapping it would be wrong)
//   - strips every "on*" attribute from every remaining element
//   - strips href/src whose scheme is javascript: or data:, EXCEPT data:
//     is allowed on <img src> when it starts with "data:image/" (needed
//     for mammoth's inline embedded images)
//   - restricts <a href> to http:/https:/mailto: (or a scheme-less/
//     relative value)
//   - keeps only the tags this editor actually produces/consumes; any
//     other tag is unwrapped (dropped, but its text/children kept) rather
//     than deleted outright, so plain text isn't silently lost.

const DROP_ENTIRELY = new Set(['script', 'iframe', 'object', 'embed', 'link', 'meta', 'style']);

const ALLOWED_TAGS = new Set([
  'p', 'div', 'span',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'b', 'strong', 'i', 'em', 'u', 's', 'strike',
  'ul', 'ol', 'li',
  'a', 'img',
  'table', 'thead', 'tbody', 'tr', 'td', 'th',
  'br', 'hr', 'blockquote', 'pre', 'code', 'font',
]);

const URL_ATTRS = new Set(['href', 'src']);

function isSafeHref(value) {
  const trimmed = value.trim();
  // A scheme-less value (relative path, fragment, etc.) has no scheme to
  // reject. Only allow an explicit scheme if it's http/https/mailto.
  if (!/^[a-z][a-z0-9+.\-]*:/i.test(trimmed)) return true;
  return /^(https?:|mailto:)/i.test(trimmed);
}

function sanitizeAttributes(el) {
  const tag = el.tagName.toLowerCase();
  // Snapshot attribute names first — removeAttribute while iterating a
  // live NamedNodeMap skips entries.
  const names = el.getAttributeNames ? el.getAttributeNames() : Array.from(el.attributes).map((a) => a.name);
  for (const name of names) {
    const lower = name.toLowerCase();

    if (lower.startsWith('on')) {
      el.removeAttribute(name);
      continue;
    }

    if (!URL_ATTRS.has(lower)) continue;

    const value = el.getAttribute(name) || '';
    const trimmed = value.trim();
    const lowerValue = trimmed.toLowerCase();

    if (lowerValue.startsWith('javascript:')) {
      el.removeAttribute(name);
      continue;
    }

    if (lowerValue.startsWith('data:')) {
      const isAllowedImgSrc = tag === 'img' && lower === 'src' && lowerValue.startsWith('data:image/');
      if (!isAllowedImgSrc) el.removeAttribute(name);
      continue;
    }

    if (lower === 'href' && tag === 'a' && !isSafeHref(trimmed)) {
      el.removeAttribute(name);
    }
  }
}

/** Replaces `el` with its own children in place (keeps text/content,
 * drops only the wrapping tag itself) — used for tags we don't
 * recognize/allow. */
function unwrap(el) {
  const parent = el.parentNode;
  if (!parent) return;
  while (el.firstChild) parent.insertBefore(el.firstChild, el);
  parent.removeChild(el);
}

/** Sanitizes a string of externally-sourced HTML and returns a safe HTML
 * string suitable for assigning to an editor page's innerHTML. */
export function sanitizeHTML(html) {
  const template = document.createElement('template');
  template.innerHTML = String(html == null ? '' : html);
  const root = template.content;

  // Drop genuinely dangerous/non-content elements outright, subtree and
  // all, before the walk below (which only ever unwraps, never deletes).
  for (const tag of DROP_ENTIRELY) {
    root.querySelectorAll(tag).forEach((node) => node.remove());
  }

  // Collect every remaining element up front: unwrap() reparents nodes,
  // and mutating the tree mid-walk is unreliable with a live TreeWalker.
  // Since unwrap() only ever moves an element's children up by one level
  // (never detaches a descendant), every element collected here is still
  // reachable in `root` no matter what happens to its ancestors later.
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
  const elements = [];
  let node = walker.nextNode();
  while (node) {
    elements.push(node);
    node = walker.nextNode();
  }

  for (const el of elements) {
    const tag = el.tagName.toLowerCase();
    if (!ALLOWED_TAGS.has(tag)) {
      unwrap(el);
      continue;
    }
    sanitizeAttributes(el);
  }

  const out = document.createElement('div');
  out.appendChild(root); // moves root's (now-sanitized) children into out
  return out.innerHTML;
}
