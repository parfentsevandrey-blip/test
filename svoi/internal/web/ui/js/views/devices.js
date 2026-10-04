// Devices: how the devices are connected (map), this device, every device
// with plain actions, pending invitations. Addresses, keys and the NAT type are
// one click away under «Технические данные».
import { html, useEffect, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { del } from "../api.js";
import { go, href } from "../router.js";
import { nowSec, useStore } from "../store.js";
import { cx, natTone, osName, sortPeers } from "../util.js";
import { fmtCountdown, fmtDuration } from "../format.js";
import { useNow } from "../hooks.js";
import { DeviceAvatar } from "../components/avatar.js";
import { ConnLine, DeviceActions, connState, platformLine } from "../components/device-actions.js";
import { PageHeader, TechDetails } from "../components/misc.js";
import { Button, Card, Chip, CopyButton, EmptyState, IconButton } from "../components/ui.js";
import { confirmDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { Topology, TopologyLegend } from "./topology.js";
import { DeviceDrawer } from "./device-drawer.js";
import { AddDeviceModal } from "./add-device.js";


function DeviceCard({ p }) {
  return html`<article class=${cx("dcard", !p.online && "is-offline")} data-testid="device-card" data-id=${p.id} data-name=${p.deviceName || p.name}
      data-online=${String(!!p.online)} data-state=${connState(p)}>
    <div class="dcard__head">
      <${DeviceAvatar} dev=${p} size=${44} showStatus=${false} />
      <div class="grow">
        <h3 class="dcard__name"><a href=${href(["devices", p.id])} class="stretched">${p.name}</a></h3>
        <p class="dcard__sub ellipsis">${platformLine(p)}</p>
      </div>
      ${p.admin && html`<span class="dcard__admin" title=${t("dev.admin")}><${Icon} name="shield" size=${16} label=${t("dev.admin")} /></span>`}
    </div>
    <${ConnLine} p=${p} />
    <${DeviceActions} p=${p} />
  </article>`;
}

function SelfCard({ self, peers, peersOnline, total }) {
  const diff = (self.nat && self.nat.difficulty) || "unknown";
  const tone = natTone(diff);
  const withIp = sortPeers(peers).filter((p) => p.ip4);
  return html`<${Card} class="selfcard" aria-labelledby="selfcard-title" data-testid="self-card">
    <div class="selfcard__head">
      <${DeviceAvatar} dev=${self} size=${52} status="self" />
      <div class="grow">
        <p class="selfcard__eyebrow">${t("dev.thisDevice")}</p>
        <h2 class="selfcard__name" id="selfcard-title">${self.name}</h2>
        <p class="selfcard__sub">${platformLine(self)}</p>
        ${self.admin && html`<div class="mt-2"><${Chip} tone="accent" icon="shield" size="sm">${t("dev.admin")}</${Chip}></div>`}
      </div>
    </div>
    <div class="selfcard__meter" aria-label=${t("top.online", { n: peersOnline + 1, total })}>
      <div class="selfcard__meter-row"><span>${t("dev.meshOnline")}</span><span class="tnum strong">${peersOnline + 1} / ${total}</span></div>
      <div class="selfcard__bars" aria-hidden="true">
        ${Array.from({ length: Math.min(total, 24) }, (_, i) => html`<span key=${i} class=${cx("selfcard__bar", i < peersOnline + 1 && "is-on")}></span>`)}
      </div>
    </div>
    <${TechDetails} class="selfcard__tech">
      <dl class="selfcard__grid">
        <div><dt>IPv4</dt><dd class="mono"><span class="ellipsis">${self.ip4 || "—"}</span>${self.ip4 && html`<${CopyButton} text=${self.ip4} label=${t("copy.copyWhat", { what: "IPv4" })} />`}</dd></div>
        <div><dt>${t("dev.idShort")}</dt><dd class="mono"><span class="ellipsis">${self.short}</span><${CopyButton} text=${self.id} label=${t("copy.copyWhat", { what: "ID" })} /></dd></div>
        <div><dt>${t("nat.label")}</dt><dd><a href="#/settings/network" class=${cx("selfcard__nat", `is-${tone}`)}>${t("nat.chip." + diff)}</a></dd></div>
        <div><dt>${t("dev.uptime")}</dt><dd class="tnum">${self.started ? fmtDuration(nowSec() - self.started) : "—"}</dd></div>
        ${self.version && html`<div><dt>${t("dev.version")}</dt><dd class="mono"><span class="ellipsis">v${self.version}</span></dd></div>`}
        ${self.os && html`<div><dt>${t("dev.os")}</dt><dd><span class="ellipsis">${osName(self.os)}${self.arch ? " · " + self.arch : ""}</span></dd></div>`}
      </dl>
      ${withIp.length > 0 && html`<p class="tech__label">${t("tech.addresses")}</p>
        <ul class="tech__list">
          ${withIp.map((p) => html`<li key=${p.id} class="tech__row">
            <span class="ellipsis">${p.name}</span>
            <span class="mono tnum">${p.ip4}</span>
            <${CopyButton} text=${p.ip4} label=${t("copy.copyWhat", { what: p.name + " IPv4" })} />
          </li>`)}
        </ul>`}
    </${TechDetails}>
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
        return html`<li class="list-row inv-row" key=${inv.id} data-testid="invite-row" data-id=${inv.id} data-owner=${inv.owner || ""}>
          <span class=${cx("inv-row__icon", inv.admin && "is-admin")}><${Icon} name=${inv.admin ? "shield" : "userPlus"} size=${16} /></span>
          <div class="grow">
            <span class="strong small ellipsis">${inv.owner ? t("inv.for", { owner: inv.owner }) : inv.admin ? t("inv.roleAdminLong") : t("inv.roleRegularLong")}</span>
            <span class="xsmall faint row gap-1">${inv.owner && html`<span class="nowrap">${inv.admin ? t("inv.roleAdmin") : t("inv.roleRegular")}</span><span aria-hidden="true">·</span>`}<span class="tnum nowrap" title=${t("inv.expiresIn")}>${left > 0 ? t("inv.left", { time: fmtCountdown(left) }) : t("inv.expired")}</span>
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
  // `#/devices?add=1` (the «Добавить устройство» item of the desktop app's menu) opens the dialog with the invitation's QR code; the
  // address is cleaned at once, so that a reload does not open it again.
  const wantsAdd = route.query.get("add") === "1";
  useEffect(() => {
    if (!wantsAdd) return;
    setAdding(true);
    go(href(selId ? ["devices", selId] : ["devices"]), { replace: true });
  }, [wantsAdd]);
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
        <${SelfCard} self=${self} peers=${peers} peersOnline=${online} total=${total} />
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
