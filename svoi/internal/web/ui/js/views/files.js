// Files section: tabs Send / Browse / My folders.
import { html } from "../../vendor/preact-htm.js";
import { t } from "../i18n.js";
import { useStore } from "../store.js";
import { PageHeader, Tabs } from "../components/misc.js";
import { SendTab } from "./files-send.js";
import { BrowseTab } from "./files-browse.js";
import { SharesTab } from "./files-shares.js";

export function FilesView({ route }) {
  const tab = ["send", "browse", "shares"].includes(route.parts[1]) ? route.parts[1] : "send";
  const offers = useStore((s) => s.counters.offers || 0);
  const items = [
    { id: "send", label: t("files.tab.send"), href: "#/files/send", icon: "send", badge: offers, badgeTone: "warn" },
    { id: "browse", label: t("files.tab.browse"), href: "#/files/browse", icon: "folderOpen" },
    { id: "shares", label: t("files.tab.shares"), href: "#/files/shares", icon: "folder" },
  ];
  return html`<div class="page files">
    <${PageHeader} title=${t("nav.files")} subtitle=${t("files.subtitle." + tab)} />
    <${Tabs} items=${items} active=${tab} label=${t("nav.files")} class="files__tabs" testid="files-tab" />
    <div class="files__body">
      ${tab === "send" && html`<${SendTab} route=${route} />`}
      ${tab === "browse" && html`<${BrowseTab} route=${route} />`}
      ${tab === "shares" && html`<${SharesTab} route=${route} />`}
    </div>
  </div>`;
}
