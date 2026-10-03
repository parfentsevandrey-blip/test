// Device selection widgets: multi-select chips (recipients), the
// "Manage device ▾" selector for admins, and a compact single-device menu.
import { html } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { state, useStore } from "../store.js";
import { cx, deviceKind, kindIcon, sortPeers } from "../util.js";
import { fmtAgo } from "../format.js";
import { Menu } from "./menu.js";

/** Multi-select device chips. value: array of ids. */
export function DeviceChips({ value = [], onChange, peers, label, filter, showOwnShortcut = false, allowOffline = true }) {
  const all = useStore((s) => s.peers);
  const self = useStore((s) => s.self);
  const list = sortPeers((peers || all).filter((p) => (filter ? filter(p) : true)));
  const toggle = (id) => {
    const set = new Set(value);
    set.has(id) ? set.delete(id) : set.add(id);
    onChange(Array.from(set));
  };
  const own = self ? list.filter((p) => p.owner && p.owner === self.owner) : [];
  const allOwnSelected = own.length > 0 && own.every((p) => value.includes(p.id));
  if (!list.length) {
    return html`<p class="muted small">${t("dev.noOthers")} <a href="#/home?add=1">${t("dev.add")}</a></p>`;
  }
  return html`<div class="dchips" role="group" aria-label=${label}>
    ${showOwnShortcut && own.length > 1 && html`<button type="button" class=${cx("dchip", "dchip--shortcut", allOwnSelected && "is-on")}
        aria-pressed=${String(allOwnSelected)}
        onClick=${() => {
          const set = new Set(value);
          if (allOwnSelected) own.forEach((p) => set.delete(p.id)); else own.forEach((p) => set.add(p.id));
          onChange(Array.from(set));
        }}>
      <${Icon} name="devices" size=${16} />
      <span>${t("dev.allMine")}</span>
    </button>`}
    ${list.map((p) => {
      const on = value.includes(p.id);
      const disabled = !allowOffline && !p.online;
      return html`<button type="button" key=${p.id} class=${cx("dchip", on && "is-on", !p.online && "is-offline")} data-testid="device-chip" data-id=${p.id} data-name=${p.deviceName || p.name}
          aria-pressed=${String(on)} disabled=${disabled}
          title=${p.online ? t("dev.status.online") : t("dev.offlineQueued", { ago: fmtAgo(p.lastSeen) })}
          onClick=${() => toggle(p.id)}>
        <span class="dchip__icon"><${Icon} name=${kindIcon[deviceKind(p)]} size=${16} />
          <span class=${cx("dchip__dot", p.online ? (p.path === "relay" ? "is-relay" : "is-on") : "is-off")}></span></span>
        <span class="dchip__name">${p.name}</span>
        ${on && html`<${Icon} name="check" size=${14} class="dchip__check" />`}
      </button>`;
    })}
  </div>`;
}

/**
 * "Управлять устройством ▾" — choose which device's configuration to show.
 * Only rendered for admins; offline devices are listed but disabled.
 */
export function ManageDeviceSelect({ value, onChange, filter }) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  if (!self || !self.admin) return null;
  const isSelf = !value || value === "self" || value === self.id;
  const cur = isSelf ? self : peers.find((p) => p.id === value);
  const items = [
    { heading: t("manage.heading") },
    { label: `${self.name} · ${t("dev.thisDeviceShort")}`, icon: kindIcon[deviceKind(self)], checked: isSelf, onClick: () => onChange("self") },
    ...sortPeers(peers.filter((p) => (filter ? filter(p) : true))).map((p) => ({
      label: p.name,
      hint: p.online ? "" : t("dev.status.offline"),
      icon: kindIcon[deviceKind(p)],
      checked: p.id === value,
      disabled: !p.online,
      onClick: () => onChange(p.id),
    })),
  ];
  return html`<div class="manage">
    <span class="manage__label" id="manage-lbl">${t("manage.label")}</span>
    <${Menu} items=${items} align="start" width=${280} label=${t("manage.label")}
      trigger=${(p) => html`<button type="button" class=${cx("manage__btn", !isSelf && "is-remote")} aria-describedby="manage-lbl" data-testid="manage-device" ...${p}>
        <${Icon} name=${kindIcon[deviceKind(cur || self)]} size=${16} />
        <span class="ellipsis">${cur ? cur.name : value}${isSelf ? html` <span class="faint">· ${t("dev.thisDeviceShort")}</span>` : ""}</span>
        <${Icon} name="chevronDown" size=${16} />
      </button>`} />
  </div>`;
}

/** Name of a device id for display, falls back to the short id. */
export function deviceName(id) {
  if (!id) return "";
  if (state.self && (id === "self" || id === state.self.id)) return state.self.name;
  const p = state.peers.find((x) => x.id === id);
  return p ? p.name : id.slice(0, 8);
}
