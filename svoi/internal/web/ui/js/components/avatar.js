// Device avatar (icon chosen from os/name heuristics, online indicator) and file-type icons.
import { html } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { cx, deviceKind, fileKind, kindIcon } from "../util.js";

/**
 * <DeviceAvatar dev={peer|self} size=40 status="online|relay|offline|self" />
 * `status` defaults from dev.online / dev.path.
 */
export function DeviceAvatar({ dev, size = 40, status, showStatus = true, class: cls }) {
  const kind = deviceKind(dev);
  const st = status || (dev && dev.online === undefined ? "self" : dev && dev.online ? (dev.path === "relay" ? "relay" : "online") : "offline");
  const iconSize = Math.round(size * 0.5);
  const label = st === "offline" ? t("dev.status.offline") : st === "relay" ? t("dev.status.relay") : st === "self" ? t("dev.thisDevice") : t("dev.status.online");
  return html`<span class=${cx("avatar", `avatar--${st}`, cls)} style=${`--av:${size}px`}>
    <${Icon} name=${kindIcon[kind]} size=${iconSize} />
    ${showStatus && st !== "self" && html`<span class="avatar__status" role="img" aria-label=${label}></span>`}
  </span>`;
}

const FILE_ICON = {
  folder: "folder", image: "image", video: "film", audio: "music", pdf: "filePdf",
  text: "fileText", code: "fileCode", archive: "archive", other: "file",
};

export function FileIcon({ name, mime, isDir, size = 20, boxed = false }) {
  const k = fileKind(name, mime, isDir);
  if (boxed) {
    return html`<span class=${cx("ficon", `ficon--${k}`)} style=${`--fi:${size}px`}><${Icon} name=${FILE_ICON[k]} size=${Math.round(size * 0.55)} /></span>`;
  }
  return html`<span class=${cx("ficon-plain", `ft-${k}`)}><${Icon} name=${FILE_ICON[k]} size=${size} /></span>`;
}
