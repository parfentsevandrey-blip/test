// "More" (phones): the device list, programs, shared folders, settings
// sections and quick preferences.
import { html, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { useStore } from "../store.js";
import { langPref, setLangPref, setThemePref, themePref } from "../prefs.js";
import { getLang } from "../i18n.js";
import { DeviceAvatar } from "../components/avatar.js";
import { ExpertTag, PageHeader } from "../components/misc.js";
import { platformLine } from "../components/device-actions.js";
import { Card, Segmented } from "../components/ui.js";

function Row({ href, icon, label, hint, dot, expert = false, testid }) {
  return html`<a class="more-row" href=${href} data-testid=${testid}>
    <span class="more-row__icon"><${Icon} name=${icon} size=${20} /></span>
    <span class="grow"><span class="more-row__label">${label}${expert && html` <${ExpertTag} />`}</span>${hint && html`<span class="more-row__hint">${hint}</span>`}</span>
    ${dot && html`<span class="nav__dot"></span>`}
    <${Icon} name="chevronRight" size=${18} class="more-row__chev" />
  </a>`;
}

export function MoreView() {
  const self = useStore((s) => s.self);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  const fw = useStore((s) => (s.forwards || []).length);
  const [lang, setL] = useState(langPref());
  const [theme, setT] = useState(themePref());
  useStore((s) => s.theme);
  return html`<div class="page more">
    <${PageHeader} title=${t("nav.more")} subtitle=${t("more.subtitle")} />
    ${self && html`<a class="more-self" href="#/settings/device">
      <${DeviceAvatar} dev=${self} size=${48} status="self" />
      <span class="grow"><span class="more-self__name">${self.name}</span><span class="more-self__sub">${platformLine(self)} · ${t("app.meshName", { name: self.meshName })}</span></span>
      <${Icon} name="chevronRight" size=${18} class="more-row__chev" />
    </a>`}
    <${Card} pad=${false} class="more-list">
      <${Row} href="#/devices" icon="devices" label=${t("nav.devices")} hint=${t("more.devicesHint")} testid="more-devices" />
      <${Row} href="#/files/shares" icon="folder" label=${t("files.tab.shares")} hint=${t("more.sharesHint")} />
      <${Row} href="#/services" icon="services" label=${t("nav.services")} hint=${fw ? t("more.forwards", { n: fw }) : t("more.servicesHint")} expert />
    </${Card}>
    <h2 class="section-title mt-6">${t("nav.settings")}</h2>
    <${Card} pad=${false} class="more-list">
      <${Row} href="#/settings/network" icon="globe" label=${t("set.sec.network")} hint=${t("more.networkHint")} dot=${restart} expert />
      <${Row} href="#/settings/files" icon="files" label=${t("set.sec.files")} hint=${t("more.filesHint")} />
      <${Row} href="#/settings/advanced" icon="settings" label=${t("set.sec.advanced")} hint=${t("set.sec.advancedSub")} expert />
      <${Row} href="#/settings/about" icon="info" label=${t("set.sec.about")} hint=${t("more.aboutHint")} />
    </${Card}>
    <h2 class="section-title mt-6">${t("set.sec.interface")}</h2>
    <${Card} class="stack">
      <div class="field"><span class="field__label">${t("set.ui.language")}</span>
        <${Segmented} full label=${t("set.ui.language")} value=${lang} onChange=${(v) => { setL(v); setLangPref(v); }}
          options=${[{ value: "auto", label: t("set.ui.auto") }, { value: "ru", label: "Русский" }, { value: "en", label: "English" }]} />
        ${lang === "auto" && html`<p class="field__hint">${t("set.ui.langAutoHint", { lang: getLang() === "ru" ? "Русский" : "English" })}</p>`}
      </div>
      <div class="field"><span class="field__label">${t("set.ui.theme")}</span>
        <${Segmented} full label=${t("set.ui.theme")} value=${theme} onChange=${(v) => { setT(v); setThemePref(v); }}
          options=${[{ value: "auto", label: t("set.ui.themeAutoShort"), icon: "auto" }, { value: "light", label: t("set.ui.themeLight"), icon: "sun" }, { value: "dark", label: t("set.ui.themeDark"), icon: "moon" }]} />
      </div>
    </${Card}>
  </div>`;
}
