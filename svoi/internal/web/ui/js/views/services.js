// Services: forward TCP services of other devices to localhost, and publish
// services of this (or a managed) device.
import { html, useEffect, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { del, devPrefix, get, isSelf, post, put } from "../api.js";
import { go, href } from "../router.js";
import { setState, useStore } from "../store.js";
import { useAsync } from "../hooks.js";
import { cx, HTTPS_PORTS, isValidHostPort, serviceIcon, sortPeers, splitHostPort, WEB_PORTS } from "../util.js";
import { DeviceAvatar } from "../components/avatar.js";
import { AccessEditor, Ago, PageHeader } from "../components/misc.js";
import { deviceName, ManageDeviceSelect } from "../components/devicepicker.js";
import { Button, Callout, Card, Chip, CopyButton, EmptyState, Field, IconButton, Skeleton } from "../components/ui.js";
import { confirmDialog, Modal } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { AccessSummary, useAllowCandidates } from "./files-shares.js";

function useForwards() {
  const forwards = useStore((s) => s.forwards);
  useEffect(() => {
    get("forwards").then((list) => setState({ forwards: Array.isArray(list) ? list : [] })).catch(() => {});
  }, []);
  return forwards;
}

function fwState(fw) {
  if (fw.state === "listening") return html`<${Chip} tone="ok" size="sm" dot>${t("svc.fw.listening")}</${Chip}>`;
  if (fw.state === "error") return html`<${Chip} tone="err" size="sm" icon="alertCircle">${t("svc.fw.error")}</${Chip}>`;
  return html`<${Chip} tone="muted" size="sm">${t("svc.fw.stopped")}</${Chip}>`;
}

/** Contextual "how to use it" line for a forwarded service. */
function UsageHint({ svc, fw }) {
  const { port } = splitHostPort(fw.listen);
  const host = splitHostPort(fw.listen).host || "127.0.0.1";
  if (svc.port === 22 || /ssh/i.test(svc.name)) {
    const cmd = `ssh -p ${port} ${host}`;
    return html`<div class="svc__hint"><span class="faint xsmall">${t("svc.hint.ssh")}</span>
      <div class="code-line"><span>${cmd}</span><${CopyButton} text=${cmd} label=${t("svc.copyCmd")} /></div></div>`;
  }
  if (WEB_PORTS.has(svc.port)) {
    const url = `${HTTPS_PORTS.has(svc.port) ? "https" : "http"}://${host}:${port}/`;
    return html`<div class="svc__hint"><a class="btn btn--subtle btn--sm" href=${url} target="_blank" rel="noopener noreferrer">
      <${Icon} name="external" size=${16} /><span class="btn__label">${t("svc.openBrowser")}</span></a></div>`;
  }
  return html`<div class="svc__hint"><span class="faint xsmall">${t("svc.hint.generic", { addr: fw.listen })}</span></div>`;
}

function ServiceCard({ peer, svc, fw }) {
  const [busy, setBusy] = useState(false);
  const connect = async () => {
    setBusy(true);
    try {
      const r = await post("forwards", { peer: peer.id, service: svc.name, listen: "127.0.0.1:0" });
      setState((s) => ({ forwards: [...(s.forwards || []).filter((x) => x.id !== r.id), r] }));
      toast({ level: "success", title: t("svc.connected", { name: svc.name }), text: t("svc.connectedText", { addr: r.listen }) });
    } catch (e) { toastError(e); }
    setBusy(false);
  };
  const disconnect = async () => {
    setBusy(true);
    try {
      await del(`forwards/${encodeURIComponent(fw.id)}`);
      setState((s) => ({ forwards: (s.forwards || []).filter((x) => x.id !== fw.id) }));
    } catch (e) { toastError(e); }
    setBusy(false);
  };
  return html`<li class=${cx("svc", fw && "is-on", !peer.online && "is-offline")} data-testid="service-card" data-peer=${peer.id} data-service=${svc.name} data-forwarded=${String(!!fw)}>
    <div class="svc__head">
      <span class="svc__icon"><${Icon} name=${serviceIcon(svc)} size=${20} /></span>
      <div class="grow">
        <div class="row"><span class="svc__name ellipsis">${svc.name}</span><span class="svc__port mono">:${svc.port}</span></div>
        ${svc.description && html`<p class="svc__desc">${svc.description}</p>`}
      </div>
      ${fw && fwState(fw)}
    </div>
    ${fw ? html`<div class="svc__fw">
        <div class="svc__addr"><span class="faint xsmall">${t("svc.localAddr")}</span>
          <div class="code-line"><span data-testid="forward-addr">${fw.listen}</span><${CopyButton} text=${fw.listen} label=${t("copy.copyWhat", { what: fw.listen })} /></div></div>
        ${fw.state === "error" && fw.error && html`<p class="danger-text xsmall">${fw.error}</p>`}
        <${UsageHint} svc=${svc} fw=${fw} />
        <div class="row row--between">
          <span class="faint xsmall tnum">${tn("svc.conns", fw.conns || 0)}</span>
          <${Button} size="sm" variant="ghost" icon="stop" loading=${busy} onClick=${disconnect} data-testid="service-disconnect">${t("svc.disconnect")}</${Button}>
        </div>
      </div>`
    : html`<div class="svc__foot">
        <${Button} size="sm" variant=${peer.online ? "subtle" : "secondary"} icon="link" loading=${busy} disabled=${!peer.online} onClick=${connect} data-testid="service-connect">${t("svc.connect")}</${Button}>
        ${!peer.online && html`<span class="faint xsmall">${t("svc.offline")}</span>`}
      </div>`}
  </li>`;
}

function RemoteServices({ focus }) {
  const peers = useStore((s) => s.peers);
  const forwards = useForwards();
  const withSvc = sortPeers(peers.filter((p) => p.services && p.services.length));
  const fws = forwards || [];
  useEffect(() => {
    if (!focus) return;
    const el = document.getElementById("svc-peer-" + focus);
    if (el) { el.scrollIntoView({ block: "start", behavior: "smooth" }); el.classList.add("is-focus"); setTimeout(() => el.classList.remove("is-focus"), 1600); }
  }, [focus, withSvc.length]);
  const orphans = fws.filter((f) => !withSvc.some((p) => p.id === f.peer && p.services.some((s) => s.name === f.service)));
  if (!withSvc.length && !orphans.length) {
    return html`<${Card}><${EmptyState} icon="services" title=${t("svc.noneRemote")} text=${t("svc.noneRemoteText")} /></${Card}>`;
  }
  return html`<div class="svc-groups">
    ${withSvc.map((p) => html`<section class="svc-group" key=${p.id} id=${"svc-peer-" + p.id} aria-labelledby=${"svc-h-" + p.id}>
      <header class="svc-group__head">
        <${DeviceAvatar} dev=${p} size=${36} />
        <div class="grow"><h3 class="svc-group__name" id=${"svc-h-" + p.id}>${p.name}</h3>
          <p class="faint xsmall">${p.online ? t("dev.status.online") : html`${t("dev.status.offline")} · <${Ago} ts=${p.lastSeen} />`}</p></div>
        <a class="btn btn--ghost btn--sm" href=${href(["devices", p.id])}>${t("svc.deviceInfo")}</a>
      </header>
      <ul class="svc-grid">${p.services.map((s) => html`<${ServiceCard} key=${s.name} peer=${p} svc=${s} fw=${fws.find((f) => f.peer === p.id && f.service === s.name)} />`)}</ul>
    </section>`)}
    ${orphans.length > 0 && html`<section class="svc-group">
      <header class="svc-group__head"><span class="card__icon"><${Icon} name="link" size=${18} /></span><h3 class="svc-group__name grow">${t("svc.orphans")}</h3></header>
      <ul class="svc-orphans">${orphans.map((f) => html`<li key=${f.id} class="list-row">
        <span class="mono small grow">${f.listen} → ${f.peerName}:${f.service}</span>${fwState(f)}
        <${IconButton} icon="x" size="sm" label=${t("svc.disconnect")} onClick=${async () => {
          try { await del(`forwards/${encodeURIComponent(f.id)}`); setState((s) => ({ forwards: (s.forwards || []).filter((x) => x.id !== f.id) })); } catch (e) { toastError(e); }
        }} />
      </li>`)}</ul>
    </section>`}
  </div>`;
}

// Quick fills for the publish dialog; descriptions are translated at use time.
const PRESETS = [
  { name: "ssh", addr: "127.0.0.1:22", desc: "svc.preset.ssh" },
  { name: "web", addr: "127.0.0.1:80", desc: "svc.preset.web" },
  { name: "jellyfin", addr: "127.0.0.1:8096", desc: "svc.preset.jellyfin" },
  { name: "home-assistant", addr: "127.0.0.1:8123", desc: "svc.preset.ha" },
  { name: "rdp", addr: "127.0.0.1:3389", desc: "svc.preset.rdp" },
];

function ServiceDialog({ dev, svc, onClose, onSaved }) {
  const candidates = useAllowCandidates(dev);
  const [name, setName] = useState(svc ? svc.name : "");
  const [addr, setAddr] = useState(svc ? svc.addr : "127.0.0.1:");
  const [desc, setDesc] = useState(svc ? svc.description : "");
  const [allow, setAllow] = useState(svc ? svc.allow : ["*"]);
  const [errs, setErrs] = useState({});
  const [busy, setBusy] = useState(false);
  const [fail, setFail] = useState(null);
  const submit = async (e) => {
    e && e.preventDefault();
    const er = {
      name: !name.trim() ? t("common.required") : /\s/.test(name.trim()) ? t("svc.nameNoSpaces") : "",
      addr: !isValidHostPort(addr) ? t("svc.addrInvalid") : "",
      allow: !allow.includes("*") && !allow.length ? t("access.noneHint") : "",
    };
    setErrs(er);
    if (er.name || er.addr || er.allow) return;
    setBusy(true); setFail(null);
    const body = { name: name.trim(), addr: addr.trim(), description: desc.trim(), allow };
    try {
      const r = svc ? await put(`${devPrefix(dev)}services/${encodeURIComponent(svc.id)}`, body) : await post(`${devPrefix(dev)}services`, body);
      toast({ level: "success", title: svc ? t("svc.updated", { name: body.name }) : t("svc.published", { name: body.name }) });
      onSaved(r);
    } catch (err) { setFail(err); setBusy(false); }
  };
  return html`<${Modal} title=${svc ? t("svc.editTitle") : t("svc.addTitle")} icon="services" onClose=${onClose}
      subtitle=${!isSelf(dev) ? t("manage.remoteNote", { name: deviceName(dev) }) : t("svc.addLead")}
      footer=${html`<${Button} variant="ghost" onClick=${onClose}>${t("common.cancel")}</${Button}>
        <${Button} variant="primary" loading=${busy} onClick=${submit} data-testid="service-save">${svc ? t("common.save") : t("svc.publish")}</${Button}>`}>
    <form class="stack stack--lg" onSubmit=${submit} noValidate>
      ${fail && html`<${Callout} tone="err" title=${t("err." + fail.code)}>${fail.message}</${Callout}>`}
      ${!svc && html`<div class="presets"><span class="field__label">${t("svc.presets")}</span><div class="row row--wrap">
        ${PRESETS.map((p) => html`<button type="button" key=${p.name} class="chip chip--outline preset" onClick=${() => { setName(p.name); setAddr(p.addr); setDesc(t(p.desc)); }}>
          <${Icon} name=${serviceIcon({ name: p.name, port: splitHostPort(p.addr).port })} size=${13} /><span>${p.name} · ${splitHostPort(p.addr).port}</span></button>`)}
      </div></div>`}
      <div class="form-row">
        <${Field} label=${t("svc.name")} error=${errs.name} hint=${t("svc.nameHint")}>
          ${(id, d) => html`<input id=${id} class="input" value=${name} maxlength="40" placeholder="ssh" autocapitalize="off" spellcheck="false" aria-describedby=${d} onInput=${(e) => setName(e.target.value)} data-testid="service-name" />`}
        </${Field}>
        <${Field} label=${t("svc.addr")} error=${errs.addr} hint=${t("svc.addrHint")}>
          ${(id, d) => html`<input id=${id} class="input mono" value=${addr} placeholder="127.0.0.1:22" spellcheck="false" aria-describedby=${d} onInput=${(e) => setAddr(e.target.value)} data-testid="service-addr" />`}
        </${Field}>
      </div>
      <${Field} label=${t("svc.desc")} optional>
        ${(id) => html`<input id=${id} class="input" value=${desc} maxlength="120" placeholder=${t("svc.descPh")} onInput=${(e) => setDesc(e.target.value)} />`}
      </${Field}>
      <div class=${cx("field", errs.allow && "has-error")}>
        <span class="field__label">${t("svc.who")}</span>
        <${AccessEditor} value=${allow} onChange=${setAllow} peers=${candidates} />
        ${errs.allow && html`<p class="field__error">${errs.allow}</p>`}
      </div>
    </form>
  </${Modal}>`;
}

function Published({ dev, setDev }) {
  const local = isSelf(dev);
  const remotePeer = useStore((s) => (!local ? s.peers.find((p) => p.id === dev) || null : null));
  const res = useAsync((signal) => get(`${devPrefix(dev)}services`, { signal }), [dev]);
  const [editing, setEditing] = useState(null);
  const remove = async (s) => {
    const ok = await confirmDialog({ title: t("svc.delTitle", { name: s.name }), text: t("svc.delText"), confirmText: t("svc.unpublish"), danger: true });
    if (!ok) return;
    try { await del(`${devPrefix(dev)}services/${encodeURIComponent(s.id)}`); res.setData((cur) => (cur || []).filter((x) => x.id !== s.id)); toast({ level: "success", title: t("svc.unpublished", { name: s.name }) }); }
    catch (e) { toastError(e); }
  };
  const onSaved = (sv) => {
    res.setData((cur) => { const a = (cur || []).slice(); const i = a.findIndex((x) => x.id === sv.id); if (i >= 0) a[i] = sv; else a.push(sv); return a; });
    setEditing(null);
  };
  const offline = !local && remotePeer && !remotePeer.online;
  let body;
  if (offline) body = html`<${EmptyState} compact icon="wifiOff" tone="warn" title=${t("manage.offlineTitle", { name: remotePeer.name })} text=${t("manage.offlineText")} />`;
  else if (res.loading && !res.data) body = html`<div class="stack">${[0, 1].map((i) => html`<div class="pub-row" key=${i}><${Skeleton} w=${40} h=${40} r=${12} /><div class="grow stack stack--sm"><${Skeleton} w="30%" h=${14} /><${Skeleton} w="50%" h=${12} /></div></div>`)}</div>`;
  else if (res.error) body = html`<${EmptyState} compact icon="alertCircle" tone="err" title=${t("svc.loadError")} text=${t("err." + res.error.code)}><${Button} size="sm" icon="refresh" onClick=${() => res.reload()}>${t("common.retry")}</${Button}></${EmptyState}>`;
  else if (!(res.data || []).length) body = html`<${EmptyState} compact icon="services" title=${t("svc.nonePublished")} text=${t("svc.nonePublishedText")}>
      <${Button} variant="primary" icon="plus" onClick=${() => setEditing("new")}>${t("svc.add")}</${Button}></${EmptyState}>`;
  else body = html`<ul class="pub-list">${res.data.map((s) => {
      const { port } = splitHostPort(s.addr);
      return html`<li key=${s.id} class="pub-row" data-testid="published-service" data-id=${s.id} data-name=${s.name}>
        <span class="svc__icon"><${Icon} name=${serviceIcon({ name: s.name, port })} size=${20} /></span>
        <div class="grow pub-row__main">
          <div class="row row--wrap"><span class="svc__name">${s.name}</span><span class="mono small faint">${s.addr}</span></div>
          ${s.description && html`<span class="small muted">${s.description}</span>`}
          <${AccessSummary} allow=${s.allow} />
        </div>
        <div class="row">
          <${IconButton} icon="pencil" label=${t("common.edit")} onClick=${() => setEditing(s)} />
          <${IconButton} icon="trash" variant="danger" label=${t("svc.unpublish")} onClick=${() => remove(s)} />
        </div>
      </li>`;
    })}</ul>`;
  return html`<${Card} class="pub">
    <div class="pub__bar">
      <div class="grow"><h2 class="card__title">${t("svc.publishedTitle")}</h2><p class="card__sub">${t("svc.publishedSub")}</p></div>
      <${ManageDeviceSelect} value=${dev} onChange=${setDev} />
      <${Button} variant="secondary" icon="plus" onClick=${() => setEditing("new")} disabled=${offline} data-testid="service-publish">${t("svc.add")}</${Button}>
    </div>
    ${!local && !offline && html`<${Callout} tone="warn" icon="settings" class="mb-3">${t("manage.remoteNote", { name: remotePeer ? remotePeer.name : dev.slice(0, 8) })}</${Callout}>`}
    ${body}
    ${editing && html`<${ServiceDialog} dev=${dev} svc=${editing === "new" ? null : editing} onClose=${() => setEditing(null)} onSaved=${onSaved} />`}
  </${Card}>`;
}

export function ServicesView({ route }) {
  const dev = route.query.get("d") || "self";
  const focus = route.query.get("peer");
  const setDev = (id) => go(href(["services"], { d: isSelf(id) ? undefined : id }));
  return html`<div class="page services">
    <${PageHeader} title=${t("nav.services")} subtitle=${t("svc.subtitle")} />
    <section class="services__remote" aria-labelledby="svc-remote-h">
      <h2 class="section-title" id="svc-remote-h">${t("svc.remoteTitle")}</h2>
      <${RemoteServices} focus=${focus} />
    </section>
    <section class="services__pub" aria-label=${t("svc.publishedTitle")}>
      <${Published} dev=${dev} setDev=${setDev} />
    </section>
  </div>`;
}
