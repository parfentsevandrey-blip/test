// «Как это работает?»: four plain sentences about The Mesh and what the status
// marks mean. Opened from the "?" in the top bar and from Home.
import { html } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { setState, useStore } from "../store.js";
import { ConnDot } from "../components/device-actions.js";
import { Drawer } from "../components/modal.js";
import { TopologyLegend } from "./topology.js";

export function openHelp() {
  setState({ help: true });
}

function closeHelp() {
  setState({ help: false });
}

const POINTS = [
  { icon: "key", text: "help.key" },
  { icon: "ticket", text: "help.invite" },
  { icon: "lock", text: "help.direct" },
  { icon: "clock", text: "help.wait" },
];

/** Mount once at the app root. */
export function HelpHost() {
  const open = useStore((s) => s.help);
  if (!open) return null;
  return html`<${Drawer} title=${t("help.title")} onClose=${closeHelp} testid="help-sheet" class="help">
    <p class="help__lead">${t("help.lead")}</p>
    <ul class="help__points">
      ${POINTS.map((p) => html`<li key=${p.icon} class="help__point">
        <span class="help__icon"><${Icon} name=${p.icon} size=${20} /></span>
        <p>${t(p.text)}</p>
      </li>`)}
    </ul>
    <h3 class="section-title">${t("help.legend")}</h3>
    <ul class="help__dots">
      <li><${ConnDot} state="on" />${t("help.dotOn")}</li>
      <li><${ConnDot} state="relay" />${t("help.dotRelay")}</li>
      <li><${ConnDot} state="off" />${t("help.dotOff")}</li>
    </ul>
    <h3 class="section-title">${t("help.lines")}</h3>
    <${TopologyLegend} stacked />
  </${Drawer}>`;
}
