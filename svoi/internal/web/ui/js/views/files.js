// Files: in the file manager these are places of their own — the drives (devices, Browse), the transfers (Send) and the
// folders this device shares (Shares); the places panel and the path bar are the way between them (js/shell.js).
import { html } from "../../vendor/preact-htm.js";
import { t } from "../i18n.js";
import { PageHeader } from "../components/misc.js";
import { SendTab } from "./files-send.js";
import { BrowseTab } from "./files-browse.js";
import { SharesTab } from "./files-shares.js";

export function FilesView({ route }) {
  const tab = ["send", "browse", "shares"].includes(route.parts[1]) ? route.parts[1] : "browse";
  // A folder of a device is the file manager's own view: the path bar says where it is.
  const head = tab === "send" ? t("nav.transfers") : tab === "shares" ? t("files.tab.shares") : route.parts[2] ? null : t("nav.files");
  return html`<div class=${"page files files--" + tab}>
    ${head && html`<${PageHeader} title=${head} subtitle=${t("files.subtitle." + tab)} />`}
    <div class="files__body">
      ${tab === "send" && html`<${SendTab} route=${route} />`}
      ${tab === "browse" && html`<${BrowseTab} route=${route} />`}
      ${tab === "shares" && html`<${SharesTab} route=${route} />`}
    </div>
  </div>`;
}
