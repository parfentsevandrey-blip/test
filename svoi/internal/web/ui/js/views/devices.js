// Devices (home): this device, topology graph, device cards, pending invites.
import { html, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { del } from "../api.js";
import { go, href } from "../router.js";
import { nowSec, useStore } from "../store.js";
import { cx, natTone, sortPeers } from "../util.js";
import { fmtCountdown, fmtDuration, fmtRtt } from "../format.js";
import { useNow } from "../hooks.js";
import { DeviceAvatar } from "../components/avatar.js";
import { Ago, PageHeader } from "../components/misc.js";
import { Button, Card, Chip, CopyButton, EmptyState, IconButton } from "../components/ui.js";
import { confirmDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { Topology, TopologyLegend } from "./topology.js";
import { DeviceDrawer } from "./device-drawer.js";
import { AddDeviceModal } from "./add-device.js";

export function osName(os) {
  const m = { darwin: "macOS", windows: "Windows", linux: "Linux", android: "Android", ios: "iOS", freebsd: "FreeBSD", openbsd: "OpenBSD" };
  return m[(os || "").toLowerCase()] || os || "—";
}

export function pathChip(p) {
  if (!p.online) return html`<${Chip} tone="muted" dot>${t("dev.status.offline")}</${Chip}>`;
  if (p.path === "relay") return html`<${Chip} tone="warn" icon="relay">${t("path.relay")}</${Chip}>`;
  if (p.path === "lan") return html`<${Chip} tone="ok" icon="network">${t("path.lan")}</${Chip}>`;
  if (p.path === "direct") return html`<${Chip} tone="ok" icon="zap">${t("path.direct")}</${Chip}>`;
  return html`<${Chip} tone="info" dot>${t("path.none")}</${Chip}>`;
}

function statusLine(p) {
  if (!p.online) {
    return html`<div class="dcard__status is-off"><${Icon} name="clock" size=${14} /><span class="ellipsis">${t("dev.lastSeen")} <${Ago} ts=${p.lastSeen} /></span></div>`;
  }
  const rtt = p.rttMs ? html`<span class="dcard__rtt mono tnum">${fmtRtt(p.rttMs)}</span>` : null;
  if (p.path === "relay") {
    return html`<div class="dcard__status is-relay"><${Icon} name="relay" size=${14} /><span class="ellipsis">${t("path.via", { via: p.relayVia || "?" })}</span>${rtt}</div>`;
  }
  const label = p.path === "lan" ? t("path.long.lan") : p.path === "direct" ? t("path.direct") : t("path.none");
  return html`<div class="dcard__status is-on"><${Icon} name=${p.path === "lan" ? "network" : p.path === "direct" ? "zap" : "radar"} size=${14} /><span class="ellipsis">${label}</span>${rtt}</div>`;
}

function DeviceCard({ p }) {
  const canFiles = (p.caps || []).includes("files");
  return html`<article class=${cx("dcard", !p.online && "is-offline")} data-testid="device-card" data-id=${p.id} data-name=${p.deviceName || p.name} data-online=${String(!!p.online)}>
    <div class="dcard__head">
      <${DeviceAvatar} dev=${p} size=${44} />
      <div class="grow">
        <h3 class="dcard__name"><a href=${href(["devices", p.id])} class="stretched">${p.name}</a></h3>
        <p class="dcard__sub ellipsis">${[p.owner, osName(p.os)].filter(Boolean).join(" · ")}</p>
      </div>
      ${p.admin && html`<span class="dcard__admin" title=${t("dev.admin")}><${Icon} name="shield" size=${16} label=${t("dev.admin")} /></span>`}
    </div>
    <div class="dcard__body">
      ${statusLine(p)}
      <span class="dcard__ip mono">${p.ip4}</span>
    </div>
    <div class="dcard__actions">
      <${IconButton} icon="send" size="sm" label=${t("dev.act.sendFile")} href=${href(["files", "send"], { to: p.id })} />
      <${IconButton} icon="chat" size="sm" label=${t("dev.act.message")} href=${href(["chat", p.id])} />
      <${IconButton} icon="mail" size="sm" label=${t("dev.act.mail")} href=${href(["mail", "compose"], { to: p.id })} />
      ${canFiles && p.shares > 0 && html`<${IconButton} icon="folderOpen" size="sm" label=${t("dev.act.browse")} href=${href(["files", "browse", p.id])} />`}
      <span class="grow"></span>
      <${CopyButton} text=${p.ip4} label=${t("copy.copyWhat", { what: "IPv4" })} />
    </div>
  </article>`;
}

function SelfCard({ self, peersOnline, total }) {
  const diff = (self.nat && self.nat.difficulty) || "unknown";
  const tone = natTone(diff);
  return html`<${Card} class="selfcard" aria-labelledby="selfcard-title" data-testid="self-card">
    <div class="selfcard__head">
      <${DeviceAvatar} dev=${self} size=${52} status="self" />
      <div class="grow">
        <p class="selfcard__eyebrow">${t("dev.thisDevice")}</p>
        <h2 class="selfcard__name" id="selfcard-title">${self.name}</h2>
        <p class="selfcard__sub">${[self.owner, osName(self.os), self.version && "v" + self.version].filter(Boolean).join(" · ")}</p>
        ${self.admin && html`<div class="mt-2"><${Chip} tone="accent" icon="shield" size="sm">${t("dev.admin")}</${Chip}></div>`}
      </div>
    </div>
    <dl class="selfcard__grid">
      <div><dt>IPv4</dt><dd class="mono"><span class="ellipsis">${self.ip4 || "—"}</span>${self.ip4 && html`<${CopyButton} text=${self.ip4} label=${t("copy.copyWhat", { what: "IPv4" })} />`}</dd></div>
      <div><dt>${t("dev.idShort")}</dt><dd class="mono"><span class="ellipsis">${self.short}</span><${CopyButton} text=${self.id} label=${t("copy.copyWhat", { what: "ID" })} /></dd></div>
      <div><dt>${t("nat.label")}</dt><dd><a href="#/settings/network" class=${cx("selfcard__nat", `is-${tone}`)}>${t("nat.chip." + diff)}</a></dd></div>
      <div><dt>${t("dev.uptime")}</dt><dd class="tnum">${self.started ? fmtDuration(nowSec() - self.started) : "—"}</dd></div>
    </dl>
    <div class="selfcard__meter" aria-label=${t("top.online", { n: peersOnline + 1, total })}>
      <div class="selfcard__meter-row"><span>${t("dev.meshOnline")}</span><span class="tnum strong">${peersOnline + 1} / ${total}</span></div>
      <div class="selfcard__bars" aria-hidden="true">
        ${Array.from({ length: Math.min(total, 24) }, (_, i) => html`<span key=${i} class=${cx("selfcard__bar", i < peersOnline + 1 && "is-on")}></span>`)}
      </div>
    </div>
  </${Card}>`;
}

function InvitesCard({ invites, admin }) {
  useNow(1000);
  if (!invites.length) return null;
  const cancel = async (inv) => {
    const ok = await confirmDialog({ title: t("inv.cancelTitle"), text: t("inv.cancelText"), confirmText: t("inv.cancel"), danger: true });
    if (!ok) return;
    try { await del(`invites/${encodeURIComponent(inv.id)}`); toast({ level: "success", title: t("inv.canceled") }); }
    catch (e) { toastError(e); }
  };
  return html`<${Card} class="invites" pad=${false} data-testid="invites">
    <header class="invites__head">
      <span class="card__icon"><${Icon} name="ticket" size=${18} /></span>
      <div class="grow"><h2 class="card__title">${t("inv.pending")}</h2><p class="card__sub">${t("inv.pendingSub")}</p></div>
    </header>
    <ul class="list">
      ${invites.map((inv) => {
        const left = inv.expires - nowSec();
        return html`<li class="list-row inv-row" key=${inv.id} data-testid="invite-row" data-id=${inv.id}>
          <span class=${cx("inv-row__icon", inv.admin && "is-admin")}><${Icon} name=${inv.admin ? "shield" : "userPlus"} size=${16} /></span>
          <div class="grow">
            <span class="strong small ellipsis">${inv.admin ? t("inv.roleAdminLong") : t("inv.roleRegularLong")}</span>
            <span class="xsmall faint row gap-1"><span class="tnum nowrap" title=${t("inv.expiresIn")}>${left > 0 ? t("inv.left", { time: fmtCountdown(left) }) : t("inv.expired")}</span>
              <span aria-hidden="true">·</span><span class="mono ellipsis" title=${inv.code}>${inv.code}</span></span>
          </div>
          <${CopyButton} text=${inv.code} label=${t("inv.copyCode")} />
          ${admin && html`<${IconButton} icon="x" size="sm" variant="danger" label=${t("inv.cancel")} onClick=${() => cancel(inv)} />`}
        </li>`;
      })}
    </ul>
  </${Card}>`;
}

export function DevicesView({ route }) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  const invites = useStore((s) => s.invites);
  const [adding, setAdding] = useState(false);
  const selId = route.parts[1] || null;
  if (!self) return null;
  const sorted = sortPeers(peers);
  const online = peers.filter((p) => p.online).length;
  const total = peers.length + 1;
  const openAdd = () => setAdding(true);

  return html`<div class="page devices">
    <${PageHeader} title=${t("nav.devices")}
      subtitle=${peers.length ? t("dev.subtitle", { online: online + 1, total }) : t("dev.subtitleAlone")}
      actions=${html`<${Button} variant="primary" icon="userPlus" onClick=${openAdd} aria-label=${t("dev.add")} data-testid="add-device">
        <span class="lbl-wide">${t("dev.add")}</span><span class="lbl-narrow">${t("dev.addShort")}</span>
      </${Button}>`} />

    <div class="devices__top">
      <${Card} class="topo-card" pad=${false} aria-label=${t("topo.label")}>
        <${Topology} self=${self} peers=${peers} selected=${selId}
          onSelect=${(id) => go(href(["devices", id]))} onAdd=${openAdd} />
        <div class="topo-card__foot"><${TopologyLegend} /></div>
      </${Card}>
      <div class="devices__side">
        <${SelfCard} self=${self} peersOnline=${online} total=${total} />
        <${InvitesCard} invites=${invites} admin=${self.admin} />
      </div>
    </div>

    <section class="devices__list" aria-labelledby="devlist-title">
      <div class="row row--between">
        <h2 class="section-title" id="devlist-title">${t("dev.allDevices")} <span class="faint">· ${tn("dev.count", peers.length)}</span></h2>
      </div>
      ${sorted.length
        ? html`<div class="dgrid">${sorted.map((p) => html`<${DeviceCard} key=${p.id} p=${p} />`)}</div>`
        : html`<${Card}><${EmptyState} icon="devices" tone="accent" title=${t("dev.emptyTitle")} text=${t("dev.emptyText")}>
            <${Button} variant="primary" icon="userPlus" onClick=${openAdd}>${t("dev.add")}</${Button}>
          </${EmptyState}></${Card}>`}
    </section>

    ${selId && html`<${DeviceDrawer} id=${selId} onClose=${() => go("#/devices")} />`}
    ${adding && html`<${AddDeviceModal} onClose=${() => setAdding(false)} />`}
  </div>`;
}
