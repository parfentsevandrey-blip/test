// How a device is named when it joins a mesh: the name the node would take, and the check of what a person types.
import { t } from "./i18n.js";
import { DEVICE_NAME_RE } from "./util.js";

function suggestName(os) {
  return { darwin: "macbook", windows: "pc", linux: "server", android: "phone", ios: "iphone", freebsd: "server" }[os] || "laptop";
}

/** What the name field offers: the name the node itself would take (its --name, the phone's own name, the host name),
 *  so the preview is the real result; only a node that does not say falls back to a guess from the system. */
export function offeredName(self) {
  return (self && self.defaultName) || suggestName(self && self.os);
}

/** "" when the name is fine, else what is wrong with it, in words. */
export function validateName(v) {
  if (!v) return t("common.required");
  if (!DEVICE_NAME_RE.test(v)) return t("dev.nameInvalid");
  return "";
}
