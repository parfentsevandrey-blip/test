// Device details drawer: addresses, connection path in plain words, traffic,
// what it shares, and admin actions (rename, promote/demote, revoke).
import { html, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn, tx } from "../i18n.js";
import { get, post } from "../api.js";
import { go, href } from "../router.js";
import { useStore } from "../store.js";
import { fmtBytes, fmtDuration, fmtRtt } from "../format.js";
import { DeviceAvatar } from "../components/avatar.js";
import { Ago } from "../components/misc.js";
import { Button, Callout, Chip, KV } from "../components/ui.js";
import { confirmDialog, Drawer, promptDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { osName, pathChip } from "./devices.js";
import { serviceIcon } from "../util.js";

function PathExplainer({ p }) {
  if (!p.online) {
    return html`<${Callout} tone="neutral" icon="wifiOff" title=${t("path.explain.offlineTitle")}>
      <p>${tx("path.explain.offline", { ago: html`<${Ago} ts=${p.lastSeen} />` })}</p>
      ${p.lastError && html`<p class="mt-1">${t("dev.lastError")}: <span class="mono">${p.lastError}</span></p>`}
    </${Callout}>`;
  }
  if (p.path === "relay") {
    return html`<${Callout} tone="warn" icon="relay" title=${t("path.explain.relayTitle", { via: p.relayVia || "?" })}>
      <p>${t("path.explain.relay")}</p>
    </${Callout}>`;
  }
  if (p.path === "lan") {
    return html`<${Callout} tone="ok" icon="network" title=${t("path.explain.lanTitle")}><p>${t("path.explain.lan")}</p></${Callout}>`;
  }
  if (p.path === "direct") {
    return html`<${Callout} tone="ok" icon="zap" title=${t("path.explain.directTitle")}><p>${t("path.explain.direct")}</p></${Callout}>`;
  }
  return html`<${Callout} tone="info" icon="radar" title=${t("path.explain.noneTitle")}><p>${t("path.explain.none")}</p></${Callout}>`;
}

function PingButton({ p }) {
  const [st, setSt] = useState({ busy: false, ms: null, err: null });
  const ping = async () => {
    setSt({ busy: true, ms: null, err: null });
    try {
      const r = await get(`diag/ping?peer=${encodeURIComponent(p.id)}`);
      setSt({ busy: false, ms: r.ms, err: null });
    } catch (e) {
      setSt({ busy: false, ms: null, err: e });
    }
  };
  return html`<div class="row">
    <${Button} size="sm" icon="activity" loading=${st.busy} onClick=${ping} disabled=${!p.online}>${t("dev.ping")}</${Button}>
    <span class="small tnum" aria-live="polite">
      ${st.ms !== null && html`<span class="accent strong">${fmtRtt(st.ms) || "<0,1"}</span>`}
      ${st.err && html`<span class="danger-text">${t("err." + st.err.code)}</span>`}
    </span>
  </div>`;
}

export function DeviceDrawer({ id, onClose }) {
  const peers = useStore((s) => s.peers);
  const self = useStore((s) => s.self);
  const p = peers.find((x) => x.id === id);
  const admin = !!(self && self.admin);

  if (!p) {
    return html`<${Drawer} title=${t("dev.notFound")} onClose=${onClose}>
      <p class="muted">${t("dev.notFoundText")}</p>
    </${Drawer}>`;
  }

  const setAlias = async () => {
    const v = await promptDialog({
      title: t("dev.aliasTitle"), label: t("dev.aliasLabel"), value: p.alias || "", placeholder: p.deviceName,
      hint: t("dev.aliasHint"), allowEmpty: true, confirmText: t("common.save"), icon: "tag",
    });
    if (v === null) return;
    try { await post(`peers/${encodeURIComponent(p.id)}/alias`, { alias: v }); toast({ level: "success", title: v ? t("dev.aliasSaved") : t("dev.aliasCleared") }); }
    catch (e) { toastError(e); }
  };

  const rename = async () => {
    const v = await promptDialog({
      title: t("dev.renameTitle"), label: t("dev.renameLabel"), value: p.deviceName, hint: t("dev.renameHint"), icon: "pencil",
      validate: (s) => (!s ? t("common.required") : !/^[\p{L}\p{N}][\p{L}\p{N}._-]{0,62}$/u.test(s) ? t("dev.nameInvalid") : ""),
    });
    if (v === null || v === p.deviceName) return;
    try { await post(`peers/${encodeURIComponent(p.id)}/rename`, { name: v }); toast({ level: "success", title: t("dev.renamed", { name: v }) }); }
    catch (e) { toastError(e); }
  };

  const setAdmin = async (on) => {
    const ok = await confirmDialog(on ? {
      title: t("dev.promoteTitle", { name: p.name }),
      text: html`<p>${t("dev.promoteText1")}</p><p>${t("dev.promoteText2")}</p>`,
      confirmText: t("dev.promote"), danger: true, icon: "key",
    } : {
      title: t("dev.demoteTitle", { name: p.name }), text: html`<p>${t("dev.demoteText")}</p>`, confirmText: t("dev.demote"),
    });
    if (!ok) return;
    try { await post(`peers/${encodeURIComponent(p.id)}/admin`, { admin: on }); toast({ level: "success", title: on ? t("dev.promoted", { name: p.name }) : t("dev.demoted", { name: p.name }) }); }
    catch (e) { toastError(e); }
  };

  const revoke = async () => {
    const ok = await confirmDialog({
      title: t("dev.revokeTitle", { name: p.name }),
      text: html`<p>${t("dev.revokeText1")}</p><p>${t("dev.revokeText2")}</p>`,
      confirmText: t("dev.revoke"), danger: true, requireText: p.deviceName,
      requireLabel: t("dev.revokeType", { name: p.deviceName }),
    });
    if (!ok) return;
    try { await post(`peers/${encodeURIComponent(p.id)}/revoke`, {}); toast({ level: "success", title: t("dev.revoked", { name: p.name }) }); onClose(); }
    catch (e) { toastError(e); }
  };

  const caps = p.caps || [];
  const versionDiffers = self && p.version && self.version && p.version !== self.version;
  const skew = Math.abs(p.clockSkewMs || 0) > 2000;

  const header = html`<div class="ddr-head">
    <${DeviceAvatar} dev=${p} size=${52} />
    <div class="grow">
      <h2 class="drawer__title ellipsis">${p.name}</h2>
      <p class="muted small ellipsis">
        ${p.alias ? html`${t("dev.realName")}: <span class="mono">${p.deviceName}</span> · ` : ""}${p.owner || ""}
      </p>
      <div class="row mt-1 row--wrap">${pathChip(p)}${p.admin && html`<${Chip} tone="accent" icon="shield">${t("dev.admin")}</${Chip}>`}</div>
    </div>
  </div>`;

  return html`<${Drawer} label=${p.name} header=${header} onClose=${onClose}>
    <div class="ddr">
      <div class="ddr-actions">
        <a class="ddr-act" href=${href(["files", "send"], { to: p.id })}><${Icon} name="send" size=${20} /><span>${t("dev.act.sendFile")}</span></a>
        <a class="ddr-act" href=${href(["chat", p.id])}><${Icon} name="chat" size=${20} /><span>${t("dev.act.message")}</span></a>
        <a class="ddr-act" href=${href(["mail", "compose"], { to: p.id })}><${Icon} name="mail" size=${20} /><span>${t("dev.act.mail")}</span></a>
        ${caps.includes("files") && html`<a class=${"ddr-act" + (!p.online || !p.shares ? " is-disabled" : "")} href=${href(["files", "browse", p.id])}
            aria-disabled=${!p.online || !p.shares ? "true" : undefined}><${Icon} name="folderOpen" size=${20} /><span>${t("dev.act.browse")}</span></a>`}
      </div>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.connection")}</h3>
        <${PathExplainer} p=${p} />
        <${KV} class="mt-3" items=${[
          p.online && { k: t("dev.rtt"), v: p.rttMs ? fmtRtt(p.rttMs) : "—" },
          p.addr && { k: t("dev.addr"), v: p.addr, mono: true, copy: p.addr },
          p.online && p.connectedAt && { k: t("dev.connectedAt"), v: html`<${Ago} ts=${p.connectedAt} />` },
          !p.online && { k: t("dev.lastSeen"), v: html`<${Ago} ts=${p.lastSeen} />` },
          p.endpoints && p.endpoints.length && { k: t("dev.endpoints"), v: p.endpoints.join(", "), mono: true, wrap: true },
        ]} />
        <div class="mt-3"><${PingButton} p=${p} /></div>
      </section>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.addresses")}</h3>
        <${KV} items=${[
          { k: "IPv4", v: p.ip4 || "—", mono: true, copy: p.ip4 },
          p.ip6 && { k: "IPv6", v: p.ip6, mono: true, copy: p.ip6 },
          { k: t("dev.id"), v: p.id, mono: true, copy: p.id, title: p.id },
        ]} />
      </section>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.about")}</h3>
        <${KV} items=${[
          { k: t("dev.os"), v: `${osName(p.os)}${p.arch ? " · " + p.arch : ""}` },
          { k: t("dev.version"), v: html`<span>${p.version ? "v" + p.version : "—"}${versionDiffers ? html` <${Chip} size="sm" tone="warn">${t("dev.versionDiffers")}</${Chip}>` : ""}</span>` },
          p.owner && { k: t("dev.owner"), v: p.owner },
          p.online && p.uptime && { k: t("dev.uptime"), v: fmtDuration(p.uptime) },
          skew && { k: t("dev.clockSkew"), v: html`<span class="warn-text">${t("dev.clockSkewVal", { s: (p.clockSkewMs / 1000).toFixed(1) })}</span>` },
        ]} />
      </section>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.traffic")}</h3>
        <div class="ddr-traffic">
          <div><${Icon} name="arrowOut" size=${16} /><span class="faint small">${t("dev.sent")}</span><strong class="tnum">${fmtBytes(p.txBytes || 0)}</strong></div>
          <div><${Icon} name="arrowIn" size=${16} /><span class="faint small">${t("dev.received")}</span><strong class="tnum">${fmtBytes(p.rxBytes || 0)}</strong></div>
        </div>
        ${(p.txRelay || p.rxRelay) ? html`<p class="small faint mt-2">${t("dev.viaRelayTraffic", { tx: fmtBytes(p.txRelay || 0), rx: fmtBytes(p.rxRelay || 0) })}</p>` : null}
      </section>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.shared")}</h3>
        <div class="stack stack--sm">
          <a class="ddr-link" href=${href(["files", "browse", p.id])}>
            <${Icon} name="folder" size=${18} />
            <span class="grow">${p.shares ? tn("dev.sharesN", p.shares) : t("dev.noShares")}</span>
            <${Icon} name="chevronRight" size=${16} />
          </a>
          <a class="ddr-link" href=${href(["services"], { peer: p.id })}>
            <${Icon} name="services" size=${18} />
            <span class="grow">${p.services && p.services.length ? tn("dev.servicesN", p.services.length) : t("dev.noServices")}</span>
            <${Icon} name="chevronRight" size=${16} />
          </a>
          ${p.services && p.services.length > 0 && html`<div class="row row--wrap">
            ${p.services.map((s) => html`<${Chip} key=${s.name} tone="outline" icon=${serviceIcon(s)}>${s.name} · ${s.port}</${Chip}>`)}
          </div>`}
        </div>
      </section>

      <section class="ddr-sec">
        <h3 class="section-title">${t("dev.sec.manage")}</h3>
        <div class="ddr-manage">
          <button type="button" class="ddr-link" onClick=${setAlias}>
            <${Icon} name="tag" size=${18} />
            <span class="grow"><span class="strong">${t("dev.alias")}</span><span class="ddr-link__hint">${t("dev.aliasHintShort")}</span></span>
          </button>
          ${admin && html`
            <button type="button" class="ddr-link" onClick=${rename}>
              <${Icon} name="pencil" size=${18} />
              <span class="grow"><span class="strong">${t("dev.rename")}</span><span class="ddr-link__hint">${t("dev.renameHintShort")}</span></span>
            </button>
            <button type="button" class="ddr-link" onClick=${() => setAdmin(!p.admin)}>
              <${Icon} name=${p.admin ? "shield" : "key"} size=${18} />
              <span class="grow"><span class="strong">${p.admin ? t("dev.demote") : t("dev.promote")}</span><span class="ddr-link__hint">${p.admin ? t("dev.demoteHint") : t("dev.promoteHint")}</span></span>
            </button>
            <button type="button" class="ddr-link is-danger" onClick=${revoke}>
              <${Icon} name="trash" size=${18} />
              <span class="grow"><span class="strong">${t("dev.revoke")}</span><span class="ddr-link__hint">${t("dev.revokeHint")}</span></span>
            </button>`}
          ${!admin && html`<p class="small faint">${t("dev.adminOnly")}</p>`}
        </div>
      </section>
    </div>
  </${Drawer}>`;
}
