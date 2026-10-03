// Minimal portal (the vendored Preact build has no createPortal): renders
// children into a node under #layers with a separate render root.
// Also keeps a stack of open modal layers to make the app (and lower layers)
// `inert`, which traps focus and hides background content from assistive tech.
import { render, useEffect, useRef } from "../../vendor/preact-htm.js";

export function Portal({ children, class: cls = "portal", modal = false }) {
  const el = useRef(null);
  if (!el.current) {
    el.current = document.createElement("div");
    el.current.className = cls;
  }
  useEffect(() => {
    const node = el.current;
    (document.getElementById("layers") || document.body).appendChild(node);
    if (modal) pushLayer(node);
    return () => {
      if (modal) popLayer(node);
      render(null, node);
      node.remove();
    };
  }, []);
  useEffect(() => {
    render(children, el.current);
  });
  return null;
}

const stack = [];

function sync() {
  const app = document.getElementById("app");
  const open = stack.length > 0;
  if (app) {
    app.inert = open;
    if (open) app.setAttribute("aria-hidden", "true");
    else app.removeAttribute("aria-hidden");
  }
  stack.forEach((n, i) => { n.inert = i !== stack.length - 1; });
  document.documentElement.classList.toggle("has-modal", open);
}

export function pushLayer(node) {
  stack.push(node);
  sync();
}

export function popLayer(node) {
  const i = stack.indexOf(node);
  if (i >= 0) stack.splice(i, 1);
  sync();
}

export function isTopLayer(node) {
  return stack.length > 0 && stack[stack.length - 1] === node;
}

export function topLayer() {
  return stack[stack.length - 1] || null;
}
