// Folder picker driven by GET [d/:id/]local/fs — directories only, works for
// POSIX ("/") and Windows ("\\", several drive roots) paths.
import { html, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { devPrefix, get } from "../api.js";
import { useAsync } from "../hooks.js";
import { cx } from "../util.js";
import { Modal } from "./modal.js";
import { Button, EmptyState, IconButton, Skeleton } from "./ui.js";

function crumbsOf(path, sep, roots) {
  if (!path) return [];
  if (sep === "\\") {
    const root = (roots || []).find((r) => path.toUpperCase().startsWith(r.toUpperCase())) || path.slice(0, 3);
    const rest = path.slice(root.length).split("\\").filter(Boolean);
    const out = [{ label: root, path: root }];
    rest.forEach((seg, i) => out.push({ label: seg, path: root + rest.slice(0, i + 1).join("\\") }));
    return out;
  }
  const parts = path.split("/").filter(Boolean);
  const out = [{ label: "/", path: "/" }];
  parts.forEach((seg, i) => out.push({ label: seg, path: "/" + parts.slice(0, i + 1).join("/") }));
  return out;
}

function join(dir, name, sep) {
  if (!dir) return name;
  return dir.endsWith(sep) ? dir + name : dir + sep + name;
}

/** <FolderPicker dev initial onPick(path) onClose /> */
export function FolderPicker({ dev, initial, onPick, onClose, title }) {
  const [path, setPath] = useState(initial || "");
  const res = useAsync((signal) => get(`${devPrefix(dev)}local/fs${path ? `?path=${encodeURIComponent(path)}` : ""}`, { signal }), [dev, path]);
  const data = res.data;
  const sep = (data && data.sep) || "/";
  const cur = data ? data.path : path;
  const crumbs = data ? crumbsOf(data.path, sep, data.roots) : [];
  const dirs = data ? data.entries.filter((e) => e.isDir !== false).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true })) : [];

  return html`<${Modal} title=${title || t("picker.title")} icon="folderOpen" size="md" onClose=${onClose} class="picker-modal" testid="folder-picker"
      footer=${html`
        <span class="picker__sel mono small ellipsis" title=${cur}>${cur}</span>
        <${Button} variant="ghost" onClick=${onClose}>${t("common.cancel")}</${Button}>
        <${Button} variant="primary" icon="check" disabled=${!data} onClick=${() => onPick(cur)} data-testid="picker-choose">${t("picker.choose")}</${Button}>`}>
    <div class="picker">
      <div class="picker__bar">
        <${IconButton} icon="arrowUp" size="sm" variant="secondary" label=${t("picker.up")} disabled=${!data || !data.parent} onClick=${() => data && data.parent && setPath(data.parent)} />
        <${IconButton} icon="home" size="sm" variant="secondary" label=${t("picker.home")} disabled=${!data} onClick=${() => data && setPath(data.home)} />
        <nav class="picker__crumbs" aria-label=${t("browse.path")}>
          ${crumbs.map((c, i) => html`<span key=${c.path} class="picker__crumb">
            ${i > 0 && sep === "/" && i > 1 ? html`<span class="faint">/</span>` : i > 0 && sep === "\\" && i > 1 ? html`<span class="faint">\\</span>` : null}
            <button type="button" class=${cx(i === crumbs.length - 1 && "is-cur")} onClick=${() => setPath(c.path)}>${c.label}</button>
          </span>`)}
        </nav>
      </div>
      ${data && data.roots && data.roots.length > 1 && html`<div class="picker__roots">
        ${data.roots.map((r) => html`<button type="button" key=${r} class=${cx("chip chip--outline", cur.toUpperCase().startsWith(r.toUpperCase()) && "is-on")} onClick=${() => setPath(r)}>
          <${Icon} name="nas" size=${13} /><span>${r}</span></button>`)}
      </div>`}
      <div class="picker__list" role="listbox" aria-label=${t("picker.folders")}>
        ${res.loading && !data && Array.from({ length: 6 }, (_, i) => html`<div class="picker__item" key=${i}><${Skeleton} w=${18} h=${18} r=${4} /><${Skeleton} w=${`${30 + i * 9}%`} h=${12} /></div>`)}
        ${res.error && html`<${EmptyState} compact icon="alertCircle" tone="err" title=${t("picker.error")} text=${t("err." + res.error.code)}>
          <${Button} size="sm" onClick=${() => setPath(data ? data.home : "")}>${t("picker.home")}</${Button}>
        </${EmptyState}>`}
        ${data && !dirs.length && html`<p class="picker__empty muted small">${t("picker.empty")}</p>`}
        ${data && dirs.map((e) => html`<button type="button" role="option" aria-selected="false" key=${e.name} class="picker__item" data-testid="picker-item" data-name=${e.name}
            onClick=${() => setPath(join(data.path, e.name, sep))}>
          <${Icon} name="folder" size=${18} class="ft-folder" />
          <span class="ellipsis">${e.name}</span>
          <${Icon} name="chevronRight" size=${16} class="picker__chev" />
        </button>`)}
      </div>
    </div>
  </${Modal}>`;
}
