// Topology graph: this device in the centre, the others on a ring. Edge style
// shows how packets flow (LAN / direct / relay / offline). Pure SVG, sized in
// real pixels (ResizeObserver) so text stays crisp at any width.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { fmtRtt } from "../format.js";
import { cx, deviceKind, kindIcon } from "../util.js";

function useSize(ref) {
  const [size, setSize] = useState({ w: 0 });
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const ro = new ResizeObserver((entries) => {
      const w = Math.round(entries[0].contentRect.width);
      setSize((s) => (s.w === w ? s : { w }));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return size;
}

function statusOf(p) {
  if (!p.online) return "offline";
  if (p.path === "relay") return "relay";
  if (p.path === "lan") return "lan";
  return "direct";
}

function truncate(s, n) {
  return s.length > n ? s.slice(0, n - 1) + "…" : s;
}

export function Topology({ self, peers, selected, onSelect, onAdd }) {
  const box = useRef(null);
  const { w } = useSize(box);
  const list = peers.slice().sort((a, b) => (a.name || "").localeCompare(b.name || "", undefined, { numeric: true }));
  const narrow = w > 0 && w < 520;
  const h = w ? Math.round(narrow ? Math.max(300, Math.min(w * 0.98, 400)) : Math.max(320, Math.min(w * 0.5, 430))) : 0;
  const many = list.length > 9;
  const r = many ? 18 : narrow ? 20 : 23;
  const rc = narrow ? 28 : 32;
  const cxp = w / 2, cyp = h / 2;
  const R = Math.max(80, Math.min(w / 2 - (narrow ? 58 : 90), h / 2 - 48));
  const n = Math.max(list.length, 1);
  // Start at the top and go clockwise; offset a little when there are 2 nodes so labels don't collide with the centre.
  const start = -Math.PI / 2 + (n === 2 ? Math.PI / 2 : 0);
  const nodes = list.map((p, i) => {
    const a = start + (2 * Math.PI * i) / n;
    // Ellipse on wide screens uses horizontal space better.
    const rx = narrow ? R : Math.min(R * 1.55, w / 2 - 80);
    return { p, a, x: cxp + Math.cos(a) * rx, y: cyp + Math.sin(a) * R, st: statusOf(p) };
  });

  const onKey = (e, id) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onSelect(id); }
  };

  return html`<div class="topo" ref=${box}>
    ${w > 0 && html`<svg class="topo__svg" width=${w} height=${h} viewBox=${`0 0 ${w} ${h}`} role="group" aria-label=${t("topo.label")}>
      <defs>
        <radialGradient id="topo-glow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" class="topo__glow-a" />
          <stop offset="100%" class="topo__glow-b" />
        </radialGradient>
      </defs>
      <circle cx=${cxp} cy=${cyp} r=${Math.min(R * 0.95, 160)} fill="url(#topo-glow)" />
      ${!narrow && html`<ellipse class="topo__orbit" cx=${cxp} cy=${cyp} rx=${Math.min(R * 1.55, w / 2 - 80)} ry=${R} />`}
      ${narrow && html`<circle class="topo__orbit" cx=${cxp} cy=${cyp} r=${R} />`}

      ${nodes.map((nd, i) => {
        const dx = nd.x - cxp, dy = nd.y - cyp;
        const len = Math.hypot(dx, dy) || 1;
        const ux = dx / len, uy = dy / len;
        const x1 = cxp + ux * (rc + 4), y1 = cyp + uy * (rc + 4);
        const x2 = nd.x - ux * (r + 4), y2 = nd.y - uy * (r + 4);
        const L = Math.max(1, len - rc - r - 8);
        const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
        const isSel = selected === nd.p.id;
        return html`<g key=${"e" + nd.p.id} class=${cx("topo__edge", `topo__edge--${nd.st}`, isSel && "is-selected")}>
          <line x1=${x1} y1=${y1} x2=${x2} y2=${y2} class="topo__line" />
          ${nd.st !== "offline" && html`<line x1=${x1} y1=${y1} x2=${x2} y2=${y2} class="topo__pulse"
              style=${`stroke-dasharray: 10 ${Math.round(L + 40)}; --len:${Math.round(L + 50)}; animation-delay:${(i * 0.37) % 2.4}s`} />`}
          ${nd.st === "relay" && nd.p.relayVia && html`<g class="topo__relay" transform=${`translate(${mx},${my})`}>
            <rect x=${-(Math.min(nd.p.relayVia.length, 14) * 3.4 + 17)} y="-11" width=${Math.min(nd.p.relayVia.length, 14) * 6.8 + 34} height="22" rx="11" />
            <g transform=${`translate(${-(Math.min(nd.p.relayVia.length, 14) * 3.4 + 11)},-6)`}><${Icon} name="relay" size=${12} strokeWidth=${2.2} /></g>
            <text x="7" y="4" text-anchor="middle">${truncate(nd.p.relayVia, 14)}</text>
          </g>`}
        </g>`;
      })}

      <g class="topo__node topo__node--self" transform=${`translate(${cxp},${cyp})`}>
        <circle r=${rc + 7} class="topo__halo" />
        <circle r=${rc} class="topo__circle" />
        <g transform=${`translate(${-(narrow ? 12 : 14)},${-(narrow ? 12 : 14)})`}><${Icon} name=${kindIcon[deviceKind(self)]} size=${narrow ? 24 : 28} /></g>
        <text y=${rc + 20} class="topo__name" text-anchor="middle">${truncate(self.name, 18)}</text>
        <text y=${rc + 35} class="topo__meta" text-anchor="middle">${t("dev.thisDeviceShort")}</text>
      </g>

      ${nodes.map((nd) => {
        const p = nd.p;
        const isSel = selected === p.id;
        const above = nd.y < cyp - R * 0.35;
        const ly = above ? -r - 26 : r + 18;
        const meta = p.online ? (p.rttMs ? fmtRtt(p.rttMs) : t("path." + (p.path || "none"))) : t("dev.status.offline");
        const label = `${p.name}: ${p.online ? t("path.long." + (p.path || "none"), { via: p.relayVia || "?" }) : t("dev.status.offline")}${p.rttMs && p.online ? ", " + fmtRtt(p.rttMs) : ""}`;
        return html`<g key=${"n" + p.id} class=${cx("topo__node", `topo__node--${nd.st}`, isSel && "is-selected")}
            transform=${`translate(${nd.x},${nd.y})`} tabindex="0" role="button" aria-label=${label} aria-pressed=${String(isSel)}
            onClick=${() => onSelect(p.id)} onKeyDown=${(e) => onKey(e, p.id)}>
          <title>${label}</title>
          <circle r=${r + 12} class="topo__hit" />
          ${isSel && html`<circle r=${r + 6} class="topo__sel" />`}
          <circle r=${r} class="topo__circle" />
          <g transform=${`translate(${-(r * 0.45)},${-(r * 0.45)})`}><${Icon} name=${kindIcon[deviceKind(p)]} size=${Math.round(r * 0.9)} /></g>
          <text y=${ly} class="topo__name" text-anchor="middle">${truncate(p.name, narrow ? 12 : 18)}</text>
          <text y=${ly + 15} class="topo__meta" text-anchor="middle">${meta}</text>
        </g>`;
      })}

      ${list.length === 0 && onAdd && html`<g class="topo__node topo__node--ghost" transform=${`translate(${cxp + Math.min(R * 1.3, w / 2 - 80)},${cyp})`}
          tabindex="0" role="button" aria-label=${t("dev.add")} onClick=${onAdd} onKeyDown=${(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onAdd(); } }}>
        <circle r=${r + 12} class="topo__hit" />
        <circle r=${r} class="topo__circle" />
        <g transform="translate(-10,-10)"><${Icon} name="plus" size=${20} /></g>
        <text y=${r + 18} class="topo__name" text-anchor="middle">${t("dev.addShort")}</text>
      </g>`}
      ${list.length === 0 && onAdd && html`<line class="topo__ghost-line" x1=${cxp + rc + 6} y1=${cyp} x2=${cxp + Math.min(R * 1.3, w / 2 - 80) - r - 6} y2=${cyp} />`}
    </svg>`}
  </div>`;
}

export function TopologyLegend() {
  const items = [
    { k: "lan", label: t("legend.lan") },
    { k: "direct", label: t("legend.direct") },
    { k: "relay", label: t("legend.relay") },
    { k: "offline", label: t("legend.offline") },
  ];
  return html`<ul class="topo-legend" aria-label=${t("legend.title")}>
    ${items.map((it) => html`<li key=${it.k}><svg width="28" height="8" aria-hidden="true" class=${`topo-legend__sw topo__edge--${it.k}`}><line x1="1" y1="4" x2="27" y2="4" class="topo__line" /></svg>${it.label}</li>`)}
  </ul>`;
}
