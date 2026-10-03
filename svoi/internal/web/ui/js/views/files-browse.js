// Files → Browse: device → share → folder browser with previews, uploads
// (drag & drop, 409 → overwrite confirmation), new folder / rename / delete.
import { html, useEffect, useLayoutEffect, useMemo, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { fileUrl, get, peerPath, post, thumbUrl, upload } from "../api.js";
import { go, href } from "../router.js";
import { useStore } from "../store.js";
import { fmtBytes, fmtShortDate, fmtDateTime, fmtPercent } from "../format.js";
import { useAsync, usePersistent } from "../hooks.js";
import { setStartFlag } from "../prefs.js";
import { cx, fileKind, joinPath, sortPeers, uid } from "../util.js";
import { DeviceAvatar, FileIcon } from "../components/avatar.js";
import { Ago, useFileDrop } from "../components/misc.js";
import { Button, Chip, EmptyState, IconButton, Progress, Segmented, Skeleton } from "../components/ui.js";
import { Menu } from "../components/menu.js";
import { confirmDialog, promptDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { PreviewModal } from "./preview.js";

const enc = encodeURIComponent;
const sharesCache = new Map(); // dev → [RemoteShare]

function useDevice(dev) {
  const self = useStore((s) => s.self);
  const peer = useStore((s) => (dev && dev !== "self" ? s.peers.find((p) => p.id === dev) || null : null));
  const isSelf = !dev || dev === "self" || (self && dev === self.id);
  return { isSelf, peer, name: isSelf ? (self ? self.name : "") : peer ? peer.name : (dev || "").slice(0, 8), online: isSelf || !!(peer && peer.online), known: isSelf || !!peer };
}

function OfflineState({ name, peer }) {
  return html`<${EmptyState} icon="wifiOff" tone="warn" title=${t("browse.offlineTitle", { name })}
      text=${peer && peer.lastSeen ? html`${t("browse.offlineText")} ${t("dev.lastSeen").toLowerCase()} <${Ago} ts=${peer.lastSeen} />.` : t("browse.offlineText")}>
    <${Button} href="#/files/browse" icon="arrowLeft">${t("browse.otherDevice")}</${Button}>
  </${EmptyState}>`;
}

function ErrorState({ error, onRetry, name, peer }) {
  if (error && error.code === "offline") return html`<${OfflineState} name=${name} peer=${peer} />`;
  const title = error && error.code === "notfound" ? t("browse.notFound") : error && error.code === "denied" ? t("browse.denied") : t("browse.error");
  return html`<${EmptyState} icon="alertCircle" tone="err" title=${title} text=${error && error.code !== "notfound" && error.code !== "denied" ? t("err." + error.code) : ""}>
    ${onRetry && html`<${Button} icon="refresh" onClick=${() => onRetry()}>${t("common.retry")}</${Button}>`}
  </${EmptyState}>`;
}

// ---------------------------------------------------------------- level 0: devices
function DeviceGrid() {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  const list = sortPeers(peers.filter((p) => !p.caps || p.caps.includes("files")));
  return html`<div class="bgrid">
    <a class="bdev" href=${href(["files", "browse", "self"])} data-testid="browse-device" data-id="self">
      <${DeviceAvatar} dev=${self} size=${48} status="self" />
      <span class="bdev__text"><span class="bdev__name">${self.name}</span><span class="bdev__sub">${t("dev.thisDevice")}</span></span>
      <${Icon} name="chevronRight" size=${18} class="bdev__chev" />
    </a>
    ${!peers.length && html`<div class="bdev bdev--hint">
      <span class="bdev__text"><span class="bdev__sub">${t("browse.noPeersText")}</span></span>
      <${Button} size="sm" variant="primary" icon="userPlus" href="#/home?add=1">${t("dev.add")}</${Button}>
    </div>`}
    ${list.map((p) => {
      const inner = html`
        <${DeviceAvatar} dev=${p} size=${48} />
        <span class="bdev__text"><span class="bdev__name">${p.name}</span>
          <span class="bdev__sub">${p.online ? (p.shares ? tn("dev.sharesN", p.shares) : t("dev.noShares")) : html`${t("dev.status.offline")} · <${Ago} ts=${p.lastSeen} />`}</span></span>
        ${p.online && html`<${Icon} name="chevronRight" size=${18} class="bdev__chev" />`}`;
      return p.online
        ? html`<a key=${p.id} class="bdev" href=${href(["files", "browse", p.id])} data-testid="browse-device" data-id=${p.id} data-name=${p.deviceName || p.name}>${inner}</a>`
        : html`<div key=${p.id} class="bdev is-offline" aria-disabled="true" title=${t("browse.offlineTitle", { name: p.name })} data-testid="browse-device" data-id=${p.id} data-name=${p.deviceName || p.name}>${inner}</div>`;
    })}
  </div>`;
}

// ---------------------------------------------------------------- level 1: shares
function ShareList({ dev }) {
  const d = useDevice(dev);
  const res = useAsync(async (signal) => {
    if (!d.online) return null;
    const list = await get(`${peerPath(dev)}/shares`, { signal });
    sharesCache.set(dev, list);
    return list;
  }, [dev, d.online]);
  const crumbs = html`<${Crumbs} dev=${dev} devName=${d.name} />`;
  // «Начало работы» on Home: having looked into another device's folders (noted as soon as they show).
  useLayoutEffect(() => { if (!d.isSelf && res.data) setStartFlag("browsed"); }, [d.isSelf, res.data]);
  if (!d.known) return html`${crumbs}<${EmptyState} icon="alertCircle" title=${t("dev.notFound")} text=${t("dev.notFoundText")} />`;
  if (!d.online) return html`${crumbs}<${OfflineState} name=${d.name} peer=${d.peer} />`;
  if (res.loading && !res.data) {
    return html`${crumbs}<div class="sgrid">${[0, 1, 2].map((i) => html`<div class="scard" key=${i}><${Skeleton} w=${44} h=${44} r=${12} /><div class="grow stack stack--sm"><${Skeleton} w="60%" h=${16} /><${Skeleton} w="30%" h=${12} /></div></div>`)}</div>`;
  }
  if (res.error) return html`${crumbs}<${ErrorState} error=${res.error} onRetry=${res.reload} name=${d.name} peer=${d.peer} />`;
  const list = res.data || [];
  if (!list.length) {
    return html`${crumbs}<${EmptyState} icon="folder" title=${t("browse.noShares", { name: d.name })} text=${d.isSelf ? t("browse.noSharesSelf") : t("browse.noSharesPeer")}>
      ${d.isSelf && html`<${Button} variant="primary" icon="plus" href="#/files/shares">${t("shares.add")}</${Button}>`}
    </${EmptyState}>`;
  }
  return html`${crumbs}<div class="sgrid">
    ${list.map((s) => html`<a key=${s.id} class="scard" href=${href(["files", "browse", dev, s.id])} data-testid="share-card" data-id=${s.id} data-mode=${s.mode}>
      <${FileIcon} name=${s.name} isDir boxed size=${48} />
      <span class="grow"><span class="scard__name">${s.name}</span>
        <span class="scard__mode">${s.mode === "rw" ? html`<${Icon} name="unlock" size=${13} /> ${t("shares.rw")}` : html`<${Icon} name="lock" size=${13} /> ${t("shares.ro")}`}</span></span>
      <${Icon} name="chevronRight" size=${18} class="bdev__chev" />
    </a>`)}
  </div>`;
}

// ---------------------------------------------------------------- breadcrumbs
function Crumbs({ dev, devName, share, shareName, segs = [] }) {
  const items = [{ label: t("browse.devices"), href: "#/files/browse", icon: "devices" }];
  if (dev) items.push({ label: devName, href: href(["files", "browse", dev]) });
  if (share) items.push({ label: shareName || share, href: href(["files", "browse", dev, share]), icon: "folder" });
  segs.forEach((s, i) => items.push({ label: s, href: href(["files", "browse", dev, share, ...segs.slice(0, i + 1)]) }));
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.scrollLeft = ref.current.scrollWidth; });
  return html`<nav class="crumbs" aria-label=${t("browse.path")} ref=${ref}>
    <ol>
      ${items.map((it, i) => html`<li key=${i}>
        ${i > 0 && html`<${Icon} name="chevronRight" size=${14} class="crumbs__sep" />`}
        ${i === items.length - 1
          ? html`<span class="crumbs__cur" aria-current="page">${it.icon && html`<${Icon} name=${it.icon} size=${15} />`}${it.label}</span>`
          : html`<a href=${it.href}>${it.icon && html`<${Icon} name=${it.icon} size=${15} />`}${it.label}</a>`}
      </li>`)}
    </ol>
  </nav>`;
}

// ---------------------------------------------------------------- thumbnails
/** Thumbnail with fallbacks: thumb → original (small files) → icon. Keyed by path at call sites. */
function Thumb({ dev, share, path, entry, size = 256 }) {
  const [stage, setStage] = useState(0); // 0 thumb · 1 original · 2 icon
  const kind = fileKind(entry.name, entry.mime);
  if (kind !== "image" || stage >= 2) return html`<${FileIcon} name=${entry.name} mime=${entry.mime} boxed size=${size >= 200 ? 64 : 36} />`;
  // Original image only as a fallback for reasonably small files.
  const src = stage === 0 ? thumbUrl(dev, share, path, size) : fileUrl(dev, share, path);
  return html`<img class="thumb" src=${src} alt="" loading="lazy" decoding="async"
    onError=${() => setStage(stage === 0 && entry.size < 6 * 1024 * 1024 ? 1 : 2)} />`;
}

// ---------------------------------------------------------------- sorting
function sortEntries(list, key, dir) {
  const m = dir === "desc" ? -1 : 1;
  const coll = new Intl.Collator(undefined, { numeric: true, sensitivity: "base" });
  return list.slice().sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1; // folders always first
    let r = 0;
    if (key === "size") r = (a.size || 0) - (b.size || 0);
    else if (key === "mtime") r = (a.mtime || 0) - (b.mtime || 0);
    if (r === 0) r = coll.compare(a.name, b.name);
    return r * m;
  });
}

// ---------------------------------------------------------------- uploads
function UploadPanel({ uploads, onClear }) {
  if (!uploads.length) return null;
  const done = uploads.every((u) => u.status !== "uploading" && u.status !== "waiting");
  return html`<aside class="upanel" aria-label=${t("browse.uploads")} data-testid="upload-panel">
    <header class="upanel__head">
      <${Icon} name="upload" size=${16} />
      <span class="grow strong small">${done ? t("browse.uploadsDone") : t("browse.uploading", { n: uploads.filter((u) => u.status === "uploading" || u.status === "waiting").length })}</span>
      ${done && html`<${IconButton} icon="x" size="sm" label=${t("common.close")} onClick=${onClear} />`}
    </header>
    <ul class="upanel__list">
      ${uploads.map((u) => html`<li key=${u.id} class="upanel__item" data-testid="upload-item" data-status=${u.status} data-name=${u.name}>
        <${FileIcon} name=${u.name} size=${16} />
        <div class="grow">
          <div class="row row--between"><span class="ellipsis small">${u.name}</span>
            <span class=${cx("xsmall tnum nowrap", u.status === "error" ? "danger-text" : "faint")}>
              ${u.status === "uploading" ? fmtPercent(u.progress, 1) : u.status === "done" ? t("browse.upDone") : u.status === "skipped" ? t("browse.upSkipped") : u.status === "waiting" ? t("browse.upWaiting") : t("err." + (u.error && u.error.code || "internal"))}
            </span></div>
          ${u.status === "uploading" && html`<${Progress} value=${u.progress * 100} size="sm" label=${u.name} />`}
        </div>
      </li>`)}
    </ul>
  </aside>`;
}

// ---------------------------------------------------------------- level 2: folder
function FolderView({ dev, share, segs }) {
  const d = useDevice(dev);
  const path = "/" + segs.join("/");
  const [view, setView] = usePersistent("themesh.files.view", "list");
  const [sort, setSort] = usePersistent("themesh.files.sort", { key: "name", dir: "asc" });
  const [query, setQuery] = useState("");
  const [preview, setPreview] = useState(-1);
  const [uploads, setUploads] = useState([]);
  const [shareMeta, setShareMeta] = useState(() => (sharesCache.get(dev) || []).find((s) => s.id === share) || null);
  const fileInput = useRef(null);

  useEffect(() => { setQuery(""); setPreview(-1); }, [path, share, dev]);
  useEffect(() => {
    if (shareMeta || !d.online) return;
    get(`${peerPath(dev)}/shares`).then((list) => {
      sharesCache.set(dev, list);
      setShareMeta(list.find((s) => s.id === share) || { id: share, name: share });
    }).catch(() => setShareMeta({ id: share, name: share }));
  }, [dev, share, d.online]);

  const res = useAsync((signal) => (d.online
    ? get(`${peerPath(dev)}/fs?share=${enc(share)}&path=${enc(path)}`, { signal })
    : Promise.resolve(null)), [dev, share, path, d.online]);

  const canWrite = !!(res.data && res.data.canWrite);
  const entries = useMemo(() => {
    const list = (res.data && res.data.entries) || [];
    const q = query.trim().toLowerCase();
    return sortEntries(q ? list.filter((e) => e.name.toLowerCase().includes(q)) : list, sort.key, sort.dir);
  }, [res.data, query, sort.key, sort.dir]);
  const files = entries.filter((e) => !e.isDir);

  const doUpload = async (list) => {
    if (!canWrite) { toast({ level: "warn", title: t("browse.readOnlyToast") }); return; }
    const items = list.map((file) => ({ id: uid("up"), file, name: file.name, size: file.size, progress: 0, status: "waiting", error: null }));
    setUploads((cur) => [...cur.filter((u) => u.status === "uploading" || u.status === "waiting"), ...items]);
    const patch = (id, p) => setUploads((cur) => cur.map((u) => (u.id === id ? { ...u, ...p } : u)));
    for (const it of items) {
      let overwrite = false;
      for (;;) {
        patch(it.id, { status: "uploading", progress: 0 });
        const url = `${peerPath(dev)}/file?share=${enc(share)}&path=${enc(joinPath(path, it.name))}${overwrite ? "&overwrite=1" : ""}`;
        try {
          await upload(url, it.file, { method: "PUT", contentType: "application/octet-stream", onProgress: (l, tot) => patch(it.id, { progress: tot ? l / tot : 0 }) }).promise;
          patch(it.id, { status: "done", progress: 1 });
        } catch (e) {
          if (e.code === "exists" && !overwrite) {
            const ok = await confirmDialog({ title: t("browse.existsTitle"), text: t("browse.existsText", { name: it.name }), confirmText: t("browse.replace"), cancelText: t("browse.skip"), icon: "file" });
            if (ok) { overwrite = true; continue; }
            patch(it.id, { status: "skipped" });
          } else {
            patch(it.id, { status: "error", error: e });
          }
        }
        break;
      }
    }
    res.reload(true);
  };

  const over = useFileDrop(doUpload, d.online && canWrite);

  const mkdir = async () => {
    const name = await promptDialog({ title: t("browse.newFolder"), label: t("browse.folderName"), placeholder: t("browse.folderPh"), confirmText: t("browse.create"), icon: "folderPlus",
      validate: (v) => (!v ? t("common.required") : /[\\/]/.test(v) || v === "." || v === ".." ? t("browse.badName") : "") });
    if (!name) return;
    try { await post(`${peerPath(dev)}/fs`, { op: "mkdir", share, path: joinPath(path, name) }); res.reload(true); }
    catch (e) { toastError(e); }
  };
  const rename = async (e) => {
    const name = await promptDialog({ title: t("browse.rename"), label: t("browse.newName"), value: e.name, selectBase: !e.isDir, icon: "pencil",
      validate: (v) => (!v ? t("common.required") : /[\\/]/.test(v) || v === "." || v === ".." ? t("browse.badName") : "") });
    if (!name || name === e.name) return;
    try { await post(`${peerPath(dev)}/fs`, { op: "rename", share, path: joinPath(path, e.name), to: name }); res.reload(true); toast({ level: "success", title: t("browse.renamed") }); }
    catch (err) { toastError(err); }
  };
  const remove = async (e) => {
    const ok = await confirmDialog({ title: e.isDir ? t("browse.delFolderTitle", { name: e.name }) : t("browse.delFileTitle", { name: e.name }),
      text: e.isDir ? t("browse.delFolderText") : t("browse.delFileText"), confirmText: t("common.delete"), danger: true });
    if (!ok) return;
    try { await post(`${peerPath(dev)}/fs`, { op: "delete", share, path: joinPath(path, e.name) }); res.reload(true); toast({ level: "success", title: t("browse.deleted", { name: e.name }) }); }
    catch (err) { toastError(err); }
  };

  const openEntry = (e) => {
    if (e.isDir) go(href(["files", "browse", dev, share, ...segs, e.name]));
    else setPreview(files.indexOf(e));
  };

  const entryMenu = (e) => [
    !e.isDir && { label: t("browse.open"), icon: "eye", onClick: () => openEntry(e) },
    !e.isDir && { label: t("common.download"), icon: "download", href: fileUrl(dev, share, joinPath(path, e.name), { dl: true }) },
    canWrite && { divider: true },
    canWrite && { label: t("browse.rename"), icon: "pencil", onClick: () => rename(e) },
    canWrite && { label: t("common.delete"), icon: "trash", danger: true, onClick: () => remove(e) },
  ].filter(Boolean);

  const crumbs = html`<${Crumbs} dev=${dev} devName=${d.name} share=${share} shareName=${shareMeta && shareMeta.name} segs=${segs} />`;
  if (!d.known) return html`${crumbs}<${EmptyState} icon="alertCircle" title=${t("dev.notFound")} text=${t("dev.notFoundText")} />`;
  if (!d.online) return html`${crumbs}<${OfflineState} name=${d.name} peer=${d.peer} />`;

  const sortLabel = { name: t("browse.sortName"), size: t("browse.sortSize"), mtime: t("browse.sortDate") }[sort.key];
  const toolbar = html`<div class="ftool">
    <div class="input-wrap ftool__search">
      <${Icon} name="search" size=${16} />
      <input class="input" type="search" value=${query} placeholder=${t("browse.filter")} aria-label=${t("browse.filter")} data-testid="file-filter"
        onInput=${(e) => setQuery(e.target.value)} />
    </div>
    <${Menu} align="end" label=${t("browse.sort")} items=${[
      { heading: t("browse.sortBy") },
      { label: t("browse.sortName"), checked: sort.key === "name", onClick: () => setSort({ ...sort, key: "name" }) },
      { label: t("browse.sortSize"), checked: sort.key === "size", onClick: () => setSort({ ...sort, key: "size" }) },
      { label: t("browse.sortDate"), checked: sort.key === "mtime", onClick: () => setSort({ ...sort, key: "mtime" }) },
      { divider: true },
      { label: t("browse.asc"), checked: sort.dir === "asc", onClick: () => setSort({ ...sort, dir: "asc" }) },
      { label: t("browse.desc"), checked: sort.dir === "desc", onClick: () => setSort({ ...sort, dir: "desc" }) },
    ]} trigger=${(p) => html`<button type="button" class="btn btn--secondary btn--sm ftool__sort" ...${p}>
      <${Icon} name="sort" size=${16} /><span class="btn__label">${sortLabel}</span></button>`} />
    <${Segmented} size="sm" label=${t("browse.view")} value=${view} onChange=${setView}
      options=${[{ value: "list", label: "", icon: "list", title: t("browse.viewList"), testid: "view-list" }, { value: "grid", label: "", icon: "grid", title: t("browse.viewGrid"), testid: "view-grid" }]} />
    <span class="grow ftool__spacer"></span>
    ${res.data && !canWrite && html`<span data-testid="read-only"><${Chip} tone="outline" icon="lock" title=${t("browse.readOnlyHint")}>${t("shares.ro")}</${Chip}></span>`}
    ${canWrite && html`
      <${Button} size="sm" variant="secondary" icon="folderPlus" onClick=${mkdir} data-testid="new-folder" aria-label=${t("browse.newFolder")}><span class="lbl-wide">${t("browse.newFolder")}</span></${Button}>
      <${Button} size="sm" variant="primary" icon="upload" onClick=${() => fileInput.current && fileInput.current.click()} data-testid="upload-button">${t("browse.upload")}</${Button}>
      <input type="file" multiple class="sr-only" ref=${fileInput} tabindex="-1" aria-hidden="true" data-testid="upload-input"
        onChange=${(e) => { const l = Array.from(e.target.files || []); e.target.value = ""; if (l.length) doUpload(l); }} />`}
  </div>`;

  let content;
  if (res.loading && !res.data) {
    content = html`<div class="flist" aria-busy="true">${Array.from({ length: 8 }, (_, i) => html`<div class="frow frow--skel" key=${i}>
      <${Skeleton} w=${32} h=${32} r=${9} /><${Skeleton} w=${`${40 + ((i * 17) % 40)}%`} h=${14} /><span class="grow"></span><${Skeleton} w=${60} h=${12} /></div>`)}</div>`;
  } else if (res.error) {
    content = html`<${ErrorState} error=${res.error} onRetry=${res.reload} name=${d.name} peer=${d.peer} />`;
  } else if (!entries.length) {
    content = query
      ? html`<${EmptyState} icon="search" title=${t("browse.noMatch")} text=${t("browse.noMatchText", { q: query })} compact />`
      : html`<${EmptyState} icon="folderOpen" title=${t("browse.empty")} text=${canWrite ? t("browse.emptyRw") : t("browse.emptyRo")} />`;
  } else if (view === "grid") {
    content = html`<ul class="fgrid">
      ${entries.map((e) => {
        const p = joinPath(path, e.name);
        return html`<li key=${e.name} class=${cx("ftile", e.isDir && "is-dir")} data-testid="file-tile" data-name=${e.name} data-dir=${String(!!e.isDir)}>
          <button type="button" class="ftile__open" onClick=${() => openEntry(e)} title=${e.name}>
            <span class="ftile__thumb">${e.isDir ? html`<${FileIcon} name=${e.name} isDir boxed size=${64} />` : html`<${Thumb} dev=${dev} share=${share} path=${p} entry=${e} key=${p} />`}</span>
            <span class="ftile__name">${e.name}</span>
            <span class="ftile__meta tnum">${e.isDir ? t("browse.folder") : fmtBytes(e.size)}</span>
          </button>
          ${!e.isDir || canWrite ? html`<div class="ftile__menu"><${Menu} items=${entryMenu(e)} label=${e.name}
            trigger=${(tp) => html`<button type="button" class="icon-btn icon-btn--sm icon-btn--secondary" aria-label=${t("browse.actionsFor", { name: e.name })} ...${tp}><${Icon} name="moreV" size=${16} /></button>`} /></div>` : null}
        </li>`;
      })}
    </ul>`;
  } else {
    content = html`<div class="flist" role="table" aria-label=${shareMeta ? shareMeta.name : share}>
      <div class="frow frow--head" role="row">
        <span role="columnheader" class="frow__name">${t("common.name")}</span>
        <span role="columnheader" class="frow__size">${t("browse.size")}</span>
        <span role="columnheader" class="frow__date">${t("browse.modified")}</span>
        <span role="columnheader" class="frow__act"><span class="sr-only">${t("common.actions")}</span></span>
      </div>
      ${entries.map((e) => {
        const p = joinPath(path, e.name);
        const kind = fileKind(e.name, e.mime, e.isDir);
        return html`<div key=${e.name} class=${cx("frow", e.isDir && "is-dir")} role="row" data-testid="file-row" data-name=${e.name} data-dir=${String(!!e.isDir)}>
          <span role="cell" class="frow__name">
            ${e.isDir
              ? html`<a class="frow__link" href=${href(["files", "browse", dev, share, ...segs, e.name])}><${FileIcon} name=${e.name} isDir boxed size=${32} /><span class="ellipsis">${e.name}</span></a>`
              : html`<button type="button" class="frow__link" onClick=${() => openEntry(e)}>
                  ${kind === "image" ? html`<span class="frow__thumb"><${Thumb} dev=${dev} share=${share} path=${p} entry=${e} size=${64} key=${p} /></span>` : html`<${FileIcon} name=${e.name} mime=${e.mime} boxed size=${32} />`}
                  <span class="ellipsis">${e.name}</span></button>`}
          </span>
          <span role="cell" class="frow__size tnum">${e.isDir ? "" : fmtBytes(e.size)}</span>
          <span role="cell" class="frow__date tnum" title=${fmtDateTime(e.mtime)}>${fmtShortDate(e.mtime)}</span>
          <span role="cell" class="frow__act">
            ${!e.isDir && html`<${IconButton} icon="download" size="sm" label=${t("browse.downloadName", { name: e.name })} href=${fileUrl(dev, share, p, { dl: true })} download=${e.name} class="frow__dl" />`}
            ${(!e.isDir || canWrite) && html`<${Menu} items=${entryMenu(e)} label=${e.name}
              trigger=${(tp) => html`<button type="button" class="icon-btn icon-btn--sm icon-btn--ghost" aria-label=${t("browse.actionsFor", { name: e.name })} ...${tp}><${Icon} name="moreV" size=${16} /></button>`} />`}
          </span>
        </div>`;
      })}
    </div>`;
  }

  const items = files.map((e) => {
    const p = joinPath(path, e.name);
    return { name: e.name, mime: e.mime, size: e.size, mtime: e.mtime, url: fileUrl(dev, share, p), dlUrl: fileUrl(dev, share, p, { dl: true }) };
  });

  return html`<div class=${cx("folder", over && "is-over")}>
    ${crumbs}
    ${toolbar}
    ${res.data && html`<p class="folder__count faint xsmall">${tn("browse.countFolders", entries.filter((e) => e.isDir).length)} · ${tn("browse.countFiles", files.length)}${query ? ` · ${t("browse.filtered")}` : ""}</p>`}
    ${content}
    ${over && html`<div class="drop-overlay" aria-hidden="true"><div class="drop-overlay__inner"><${Icon} name="upload" size=${36} /><p>${t("browse.dropHere", { name: segs.length ? segs[segs.length - 1] : (shareMeta ? shareMeta.name : share) })}</p></div></div>`}
    <${UploadPanel} uploads=${uploads} onClear=${() => setUploads([])} />
    ${preview >= 0 && items[preview] && html`<${PreviewModal} items=${items} index=${preview} onIndex=${setPreview} onClose=${() => setPreview(-1)} />`}
  </div>`;
}

export function BrowseTab({ route }) {
  const [, , dev, share, ...segs] = route.parts;
  if (!dev) return html`<${DeviceGrid} />`;
  if (!share) return html`<${ShareList} dev=${dev} key=${dev} />`;
  return html`<${FolderView} dev=${dev} share=${share} segs=${segs} key=${dev + "/" + share} />`;
}
