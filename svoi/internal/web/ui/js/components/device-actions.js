// Plain-language pieces of a device shared by Home and the Devices page: whose
// it is and what it is ("Андрей · телефон Android"), how it is connected right
// now ("На связи · напрямую", "Не в сети · был 2 дня назад") and three labelled
// actions: send a file, write, open its files. Words only — addresses, keys,
// milliseconds and NAT live under «Технические данные».
import { html } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tx } from "../i18n.js";
import { href } from "../router.js";
import { cx, deviceKind, osName } from "../util.js";
import { Ago } from "./misc.js";
import { toast } from "./toast.js";

/** "Андрей · телефон Android" — the owner (optional) and the kind of device in words. */
export function platformLine(dev, withOwner = true) {
  if (!dev) return "";
  const kind = deviceKind(dev);
  const word = t("dev.kind." + kind);
  const what = dev.os ? t(kind === "server" || kind === "nas" ? "dev.platformOn" : "dev.platform", { kind: word, os: osName(dev.os) }) : word;
  const line = [withOwner && dev.owner, what].filter(Boolean).join(" · ");
  return line.charAt(0).toUpperCase() + line.slice(1);
}

/** on | relay | wait | off — what the dot shows. */
export function connState(p) {
  if (!p.online) return "off";
  if (p.path === "relay") return "relay";
  if (p.path === "lan" || p.path === "direct") return "on";
  return "wait";
}

/** The status sentence of a peer, as text or (offline, with a ticking time) as children. */
export function connText(p) {
  if (!p.online) {
    if (!p.lastSeen) return t("conn.offlineNever");
    return tx(deviceKind(p) === "nas" ? "conn.offlineN" : "conn.offline", { ago: html`<${Ago} ts=${p.lastSeen} />` });
  }
  if (p.path === "relay") return p.relayVia ? t("conn.relay", { via: p.relayVia }) : t("conn.relayAny");
  if (p.path === "lan") return t("conn.lan");
  if (p.path === "direct") return t("conn.direct");
  return t("conn.connecting");
}

/** Big status dot (decorative: the words next to it say the same). */
export function ConnDot({ state }) {
  return html`<span class=${cx("conn__dot", `conn__dot--${state}`)} aria-hidden="true"></span>`;
}

/** «● На связи · через home-server» */
export function ConnLine({ p, class: cls }) {
  const st = connState(p);
  return html`<p class=${cx("conn", `conn--${st}`, cls)} data-testid="conn-line" data-state=${st}>
    <${ConnDot} state=${st} />
    <span class="conn__text">${connText(p)}</span>
  </p>`;
}

/** Why this device's folders can't be opened right now ("" when they can). */
export function filesUnavailable(p) {
  if (p.caps && !p.caps.includes("files")) return t("act.filesNoCap");
  if (!p.online) return t("act.filesOffline");
  if (!p.shares) return t("act.filesNone");
  return "";
}

function ActLink({ icon, label, hint, href: to, testid }) {
  return html`<a class="dact__btn" href=${to} aria-label=${label} title=${hint || label} data-testid=${testid}>
    <${Icon} name=${icon} size=${18} />
    <span class="dact__label">${label}</span>
  </a>`;
}

/**
 * «Отправить файл» · «Написать» · «Файлы». Labels show when the card is wide
 * enough (icons with a tooltip and an accessible name otherwise). Sending and
 * writing work offline too (it waits for the device); the files button stays
 * focusable when unavailable and says why on click.
 */
export function DeviceActions({ p, class: cls }) {
  const why = filesUnavailable(p);
  const filesLabel = t("act.files");
  return html`<div class=${cx("dact", cls)} role="group" aria-label=${t("act.group", { name: p.name })}>
    <${ActLink} icon="send" label=${t("act.send")} hint=${p.online ? "" : t("act.sendOffline")} href=${href(["files", "send"], { to: p.id })} testid="device-act-send" />
    <${ActLink} icon="chat" label=${t("act.chat")} hint=${p.online ? "" : t("act.chatOffline")} href=${href(["chat", p.id])} testid="device-act-chat" />
    ${why
      ? html`<button type="button" class="dact__btn is-disabled" aria-disabled="true" aria-label=${filesLabel} title=${why} data-testid="device-act-files"
            onClick=${() => toast({ level: "info", title: why })}>
          <${Icon} name="folderOpen" size=${18} />
          <span class="dact__label">${filesLabel}</span>
        </button>`
      : html`<${ActLink} icon="folderOpen" label=${filesLabel} href=${href(["files", "browse", p.id])} testid="device-act-files" />`}
  </div>`;
}
