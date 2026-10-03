// Files → My folders: shares of this device (or of another device, for admins,
// through the /api/d/:id prefix). Add / edit with a folder picker, delete.
import { html, useEffect, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { del, devPrefix, get, isSelf, post, put } from "../api.js";
import { go, href } from "../router.js";
import { setState, useEvent, useStore } from "../store.js";
import { useAsync } from "../hooks.js";
import { cx, sortPeers } from "../util.js";
import { FileIcon } from "../components/avatar.js";
import { AccessEditor } from "../components/misc.js";
import { deviceName, ManageDeviceSelect } from "../components/devicepicker.js";
import { FolderPicker } from "../components/folderpicker.js";
import { Button, Callout, Card, Chip, EmptyState, Field, IconButton, Segmented, Skeleton } from "../components/ui.js";
import { confirmDialog, Modal } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";

/** Devices that may appear in an allow-list of `dev` (everyone but the device itself). */
export function useAllowCandidates(dev) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  if (isSelf(dev)) return peers;
  return sortPeers([{ ...self, online: true, path: "lan" }, ...peers.filter((p) => p.id !== dev)]);
}

export function AccessSummary({ allow }) {
  if (!allow || allow.includes("*")) return html`<span class="access-sum"><${Icon} name="devices" size=${14} />${t("access.everyone")}</span>`;
  if (!allow.length) return html`<span class="access-sum warn-text"><${Icon} name="alert" size=${14} />${t("access.nobody")}</span>`;
  return html`<span class="access-sum"><${Icon} name="user" size=${14} /><span class="ellipsis">${allow.map(deviceName).join(", ")}</span></span>`;
}

function ShareDialog({ dev, share, onClose, onSaved }) {
  const candidates = useAllowCandidates(dev);
  const [name, setName] = useState(share ? share.name : "");
  const [path, setPath] = useState(share ? share.path : "");
  const [mode, setMode] = useState(share ? share.mode : "ro");
  const [allow, setAllow] = useState(share ? share.allow : ["*"]);
  const [picking, setPicking] = useState(false);
  const [errs, setErrs] = useState({});
  const [busy, setBusy] = useState(false);
  const [fail, setFail] = useState(null);

  const pickPath = (p) => {
    setPath(p);
    setPicking(false);
    if (!name) {
      const seg = p.split(/[\\/]/).filter(Boolean).pop();
      if (seg) setName(seg);
    }
  };

  const submit = async (e) => {
    e && e.preventDefault();
    const er = { name: name.trim() ? "" : t("common.required"), path: path.trim() ? "" : t("common.required"), allow: !allow.includes("*") && !allow.length ? t("access.noneHint") : "" };
    setErrs(er);
    if (er.name || er.path || er.allow) return;
    setBusy(true);
    setFail(null);
    const body = { name: name.trim(), path: path.trim(), mode, allow };
    try {
      const r = share ? await put(`${devPrefix(dev)}shares/${encodeURIComponent(share.id)}`, body) : await post(`${devPrefix(dev)}shares`, body);
      toast({ level: "success", title: share ? t("shares.updated", { name: body.name }) : t("shares.added", { name: body.name }) });
      onSaved(r);
    } catch (err) {
      setFail(err);
      setBusy(false);
    }
  };

  return html`<${Modal} title=${share ? t("shares.editTitle") : t("shares.addTitle")} icon="folder" onClose=${onClose}
      subtitle=${!isSelf(dev) ? t("manage.remoteNote", { name: deviceName(dev) }) : undefined}
      footer=${html`
        <${Button} variant="ghost" onClick=${onClose}>${t("common.cancel")}</${Button}>
        <${Button} variant="primary" loading=${busy} onClick=${submit} data-testid="share-save">${share ? t("common.save") : t("shares.addBtn")}</${Button}>`}>
    <form class="stack stack--lg" onSubmit=${submit} noValidate>
      ${fail && html`<${Callout} tone="err" title=${t("err." + fail.code)}>${fail.code === "invalid" && /exist/i.test(fail.message || "") ? t("shares.pathMissing") : fail.message}</${Callout}>`}
      <${Field} label=${t("shares.path")} error=${errs.path} hint=${t("shares.pathHint")}>
        ${(id, d) => html`<div class="input-group">
          <input id=${id} class="input mono" value=${path} placeholder=${isSelf(dev) ? "/home/me/Pictures" : "/srv/media"} spellcheck="false" autocapitalize="off" data-testid="share-path"
            aria-describedby=${d} onInput=${(e) => setPath(e.target.value)} />
          <${Button} icon="folderOpen" onClick=${() => setPicking(true)} data-testid="share-pick">${t("shares.pick")}</${Button}>
        </div>`}
      </${Field}>
      <${Field} label=${t("shares.name")} error=${errs.name} hint=${t("shares.nameHint")}>
        ${(id, d) => html`<input id=${id} class="input" value=${name} maxlength="60" placeholder=${t("shares.namePh")} aria-describedby=${d} onInput=${(e) => setName(e.target.value)} data-testid="share-name" />`}
      </${Field}>
      <div class="field">
        <span class="field__label">${t("shares.mode")}</span>
        <${Segmented} label=${t("shares.mode")} value=${mode} onChange=${setMode} full
          options=${[{ value: "ro", label: t("shares.ro"), icon: "lock" }, { value: "rw", label: t("shares.rw"), icon: "unlock" }]} />
        <p class="field__hint">${mode === "rw" ? t("shares.rwHint") : t("shares.roHint")}</p>
      </div>
      <div class=${cx("field", errs.allow && "has-error")}>
        <span class="field__label">${t("access.label")}</span>
        <${AccessEditor} value=${allow} onChange=${setAllow} peers=${candidates} />
        ${errs.allow && html`<p class="field__error">${errs.allow}</p>`}
      </div>
    </form>
    ${picking && html`<${FolderPicker} dev=${dev} initial=${path || undefined} onPick=${pickPath} onClose=${() => setPicking(false)} />`}
  </${Modal}>`;
}

export function SharesTab({ route }) {
  const self = useStore((s) => s.self);
  const dev = route.query.get("d") || "self";
  const local = isSelf(dev);
  const remotePeer = useStore((s) => (!local ? s.peers.find((p) => p.id === dev) || null : null));
  const res = useAsync((signal) => get(`${devPrefix(dev)}shares`, { signal }), [dev]);
  const [editing, setEditing] = useState(null); // null | "new" | share
  const liveShares = useStore((s) => s.shares);

  useEffect(() => { if (local && res.data) setState({ shares: res.data }); }, [res.data, local]);
  useEvent("shares", (list) => { if (local) res.setData(list); });

  const list = local && liveShares ? liveShares : res.data || [];
  const setDev = (id) => go(href(["files", "shares"], { d: isSelf(id) ? undefined : id }));

  const remove = async (s) => {
    const ok = await confirmDialog({ title: t("shares.delTitle", { name: s.name }), text: t("shares.delText"), confirmText: t("shares.del"), danger: true });
    if (!ok) return;
    try {
      await del(`${devPrefix(dev)}shares/${encodeURIComponent(s.id)}`);
      res.setData((cur) => (cur || []).filter((x) => x.id !== s.id));
      if (local) setState((st) => ({ shares: (st.shares || []).filter((x) => x.id !== s.id) }));
      toast({ level: "success", title: t("shares.deleted", { name: s.name }) });
    } catch (e) { toastError(e); }
  };

  const onSaved = (sh) => {
    res.setData((cur) => {
      const arr = (cur || []).slice();
      const i = arr.findIndex((x) => x.id === sh.id);
      if (i >= 0) arr[i] = sh; else arr.push(sh);
      if (local) setState({ shares: arr });
      return arr;
    });
    setEditing(null);
  };

  const remoteName = remotePeer ? remotePeer.name : dev.slice(0, 8);
  let body;
  if (!local && remotePeer && !remotePeer.online) {
    body = html`<${EmptyState} icon="wifiOff" tone="warn" title=${t("manage.offlineTitle", { name: remoteName })} text=${t("manage.offlineText")} />`;
  } else if (res.loading && !res.data) {
    body = html`<div class="stack">${[0, 1].map((i) => html`<div class="share-row" key=${i}><${Skeleton} w=${44} h=${44} r=${12} /><div class="grow stack stack--sm"><${Skeleton} w="40%" h=${16} /><${Skeleton} w="70%" h=${12} /></div></div>`)}</div>`;
  } else if (res.error) {
    body = html`<${EmptyState} icon="alertCircle" tone="err" title=${t("shares.loadError")} text=${t("err." + res.error.code)}>
      <${Button} icon="refresh" onClick=${() => res.reload()}>${t("common.retry")}</${Button}>
    </${EmptyState}>`;
  } else if (!list.length) {
    body = html`<${Card}><${EmptyState} icon="folder" tone="accent" title=${t("shares.emptyTitle")} text=${local ? t("shares.emptyText") : t("shares.emptyTextRemote", { name: remoteName })}>
      <${Button} variant="primary" icon="plus" onClick=${() => setEditing("new")}>${t("shares.add")}</${Button}>
    </${EmptyState}></${Card}>`;
  } else {
    body = html`<ul class="share-list">
      ${list.map((s) => html`<li key=${s.id} class=${cx("share-row", s.exists === false && "is-missing")} data-testid="share-row" data-id=${s.id} data-name=${s.name}>
        <${FileIcon} name=${s.name} isDir boxed size=${44} />
        <div class="grow share-row__main">
          <div class="row row--wrap">
            <span class="share-row__name">${s.name}</span>
            ${s.mode === "rw" ? html`<${Chip} size="sm" tone="info" icon="unlock">${t("shares.rw")}</${Chip}>` : html`<${Chip} size="sm" tone="outline" icon="lock">${t("shares.ro")}</${Chip}>`}
          </div>
          <span class="share-row__path mono" title=${s.path}>${s.path}</span>
          <${AccessSummary} allow=${s.allow} />
          ${s.exists === false && html`<span class="share-row__warn"><${Icon} name="alert" size=${14} />${t("shares.missing")}</span>`}
        </div>
        <div class="share-row__actions">
          ${s.exists !== false && html`<${IconButton} icon="folderOpen" label=${t("shares.browse")} href=${href(["files", "browse", local ? "self" : dev, s.id])} />`}
          <${IconButton} icon="pencil" label=${t("common.edit")} onClick=${() => setEditing(s)} />
          <${IconButton} icon="trash" variant="danger" label=${t("common.delete")} onClick=${() => remove(s)} />
        </div>
      </li>`)}
    </ul>`;
  }

  return html`<div class="shares">
    <div class="shares__bar">
      <${ManageDeviceSelect} value=${dev} onChange=${setDev} />
      ${!(self && self.admin) && html`<p class="muted small">${t("shares.lead")}</p>`}
      <span class="grow"></span>
      <${Button} variant="primary" icon="plus" onClick=${() => setEditing("new")} disabled=${!local && remotePeer && !remotePeer.online} data-testid="share-add">${t("shares.add")}</${Button}>
    </div>
    ${!local && html`<${Callout} tone="warn" icon="settings" class="shares__remote">${t("manage.remoteNote", { name: remoteName })}</${Callout}>`}
    ${body}
    ${editing && html`<${ShareDialog} dev=${dev} share=${editing === "new" ? null : editing} onClose=${() => setEditing(null)} onSaved=${onSaved} />`}
  </div>`;
}
