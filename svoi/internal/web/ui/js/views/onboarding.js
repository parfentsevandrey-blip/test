// Onboarding (configured:false): create a new mesh or join with an invite.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon, Logo } from "../icons.js";
import { t, getLang } from "../i18n.js";
import { post } from "../api.js";
import { refreshState } from "../sse.js";
import { useStore } from "../store.js";
import { langPref, setLangPref, setThemePref, themePref } from "../prefs.js";
import { Button, Callout, Field, Progress, Segmented } from "../components/ui.js";
import { DnsPreview } from "../components/misc.js";
import { toast } from "../components/toast.js";
import { fmtDateTime } from "../format.js";
import { cx, DEVICE_NAME_RE, normalizeDeviceName as normalizeName } from "../util.js";

function suggestName(os) {
  return { darwin: "macbook", windows: "pc", linux: "server", android: "phone", ios: "iphone", freebsd: "server" }[os] || "laptop";
}

function validateName(v) {
  if (!v) return t("common.required");
  if (!DEVICE_NAME_RE.test(v)) return t("dev.nameInvalid");
  return "";
}

/** Shown while GET /api/state carries `removed`: an admin removed this device from its mesh. */
function RemovedNotice({ removed }) {
  return html`<${Callout} tone="warn" icon="userX" class="onb-removed" role="status" data-testid="removed-notice"
      title=${t("onb.removed.title", { mesh: removed.meshName || "—" })}>
    <p>${t("onb.removed.text")}</p>
    ${removed.at ? html`<p class="faint xsmall mt-1">${t("onb.removed.when", { when: fmtDateTime(removed.at) })}</p>` : null}
  </${Callout}>`;
}

function Corner() {
  const [lang, setL] = useState(langPref());
  const [theme, setT] = useState(themePref());
  useStore((s) => s.theme);
  return html`<div class="onb__corner">
    <${Segmented} size="sm" label=${t("set.ui.language")} value=${lang === "auto" ? getLang() : lang}
      options=${[{ value: "ru", label: "RU" }, { value: "en", label: "EN" }]}
      onChange=${(v) => { setL(v); setLangPref(v); }} />
    <${Segmented} size="sm" label=${t("set.ui.theme")} value=${theme}
      options=${[{ value: "auto", label: "", icon: "auto", title: t("set.ui.themeAuto") }, { value: "light", label: "", icon: "sun", title: t("set.ui.themeLight") }, { value: "dark", label: "", icon: "moon", title: t("set.ui.themeDark") }]}
      onChange=${(v) => { setT(v); setThemePref(v); }} />
  </div>`;
}

function CreateForm({ self, onBack }) {
  const [mesh, setMesh] = useState("");
  const [name, setName] = useState("");
  const [owner, setOwner] = useState("");
  const [errs, setErrs] = useState({});
  const [busy, setBusy] = useState(false);
  const [fail, setFail] = useState(null);
  const placeholder = suggestName(self && self.os);

  const submit = async (e) => {
    e.preventDefault();
    const dn = normalizeName(name || placeholder);
    const er = { mesh: mesh.trim() ? "" : t("common.required"), name: validateName(dn) };
    setErrs(er);
    if (er.mesh || er.name) return;
    setBusy(true);
    setFail(null);
    try {
      await post("mesh/create", { meshName: mesh.trim(), deviceName: dn, owner: owner.trim() });
      toast({ level: "success", title: t("onb.created", { name: mesh.trim() }), text: t("onb.createdText") });
      await refreshState();
    } catch (err) {
      setFail(err);
      setBusy(false);
    }
  };

  return html`<form class="onb-form" onSubmit=${submit} noValidate>
    <button type="button" class="onb-back" onClick=${onBack}><${Icon} name="arrowLeft" size=${16} />${t("common.back")}</button>
    <div class="onb-form__head">
      <span class="onb-choice__icon"><${Icon} name="sparkle" size=${24} /></span>
      <div><h2 class="onb-form__title">${t("onb.create.title")}</h2><p class="muted">${t("onb.create.lead")}</p></div>
    </div>
    <div class="form-grid">
      <${Field} label=${t("onb.meshName")} hint=${t("onb.meshNameHint")} error=${errs.mesh}>
        ${(id, d) => html`<input id=${id} class="input" value=${mesh} placeholder=${t("onb.meshNamePh")} maxlength="40" autofocus data-testid="onb-mesh-name"
          aria-describedby=${d} aria-invalid=${errs.mesh ? "true" : undefined} onInput=${(e) => setMesh(e.target.value)} />`}
      </${Field}>
      <${Field} label=${t("onb.deviceName")} hint=${t("onb.deviceNameHint")} error=${errs.name}
          extra=${html`<${DnsPreview} name=${name.trim() ? name : placeholder} />`}>
        ${(id, d) => html`<input id=${id} class="input" value=${name} placeholder=${placeholder} maxlength="63" autocapitalize="off" spellcheck="false" data-testid="onb-device-name"
          aria-describedby=${d} aria-invalid=${errs.name ? "true" : undefined}
          onInput=${(e) => setName(e.target.value)} onBlur=${() => setName(normalizeName(name))} />`}
      </${Field}>
      <${Field} label=${t("onb.owner")} hint=${t("onb.ownerHint")} optional>
        ${(id, d) => html`<input id=${id} class="input" value=${owner} placeholder=${t("onb.ownerPh")} maxlength="64" autocomplete="given-name" data-testid="onb-owner"
          aria-describedby=${d} onInput=${(e) => setOwner(e.target.value)} />`}
      </${Field}>
    </div>
    ${fail && html`<${Callout} tone="err" title=${t("err." + fail.code)} data-testid="onb-error">${fail.message}</${Callout}>`}
    <${Button} type="submit" variant="primary" size="lg" block loading=${busy} icon="sparkle" data-testid="onb-submit">${t("onb.create.submit")}</${Button}>
  </form>`;
}

const JOIN_LIMIT = 25;

// No owner here: whose device this is was decided by the inviting device and
// travels in the invitation (the node ignores an `owner` sent with the join).
function JoinForm({ self, onBack }) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [errs, setErrs] = useState({});
  const [busy, setBusy] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [fail, setFail] = useState(null);
  const timer = useRef(0);
  const placeholder = suggestName(self && self.os);
  useEffect(() => () => clearInterval(timer.current), []);

  const cleanCode = code.replace(/\s+/g, "").toUpperCase();

  const submit = async (e) => {
    e.preventDefault();
    const dn = normalizeName(name || placeholder);
    const er = {
      code: !cleanCode ? t("common.required") : !cleanCode.startsWith("MESH1-") ? t("onb.codeBad") : "",
      name: validateName(dn),
    };
    setErrs(er);
    if (er.code || er.name) return;
    setBusy(true);
    setFail(null);
    setElapsed(0);
    const started = Date.now();
    timer.current = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 250);
    try {
      await post("mesh/join", { invite: cleanCode, deviceName: dn });
      clearInterval(timer.current);
      toast({ level: "success", title: t("onb.joined"), text: t("onb.joinedText") });
      await refreshState();
    } catch (err) {
      clearInterval(timer.current);
      setFail(err);
      setBusy(false);
    }
  };

  if (busy) {
    const pct = Math.min(96, (elapsed / JOIN_LIMIT) * 100);
    return html`<div class="onb-form onb-progress" role="status" aria-live="polite" data-testid="onb-progress">
      <div class="onb-progress__rings" aria-hidden="true"><span></span><span></span><span></span><${Logo} size=${44} /></div>
      <h2 class="onb-form__title center">${t("onb.joining")}</h2>
      <p class="muted center">${elapsed < 8 ? t("onb.joiningStep1") : elapsed < 16 ? t("onb.joiningStep2") : t("onb.joiningStep3")}</p>
      <div class="onb-progress__bar"><${Progress} value=${pct} label=${t("onb.joining")} /></div>
      <p class="faint small center tnum">${t("onb.elapsed", { s: elapsed, max: JOIN_LIMIT })}</p>
    </div>`;
  }

  const failText = fail && (fail.code === "busy" || fail.code === "network" ? t("onb.failReach")
    : fail.code === "invalid" ? t("onb.failInvalid") : fail.code === "denied" ? t("onb.failDenied") : t("err." + fail.code));

  return html`<form class="onb-form" onSubmit=${submit} noValidate>
    <button type="button" class="onb-back" onClick=${onBack}><${Icon} name="arrowLeft" size=${16} />${t("common.back")}</button>
    <div class="onb-form__head">
      <span class="onb-choice__icon onb-choice__icon--join"><${Icon} name="ticket" size=${24} /></span>
      <div><h2 class="onb-form__title">${t("onb.join.title")}</h2><p class="muted">${t("onb.join.lead")}</p></div>
    </div>
    ${fail && html`<${Callout} tone="err" title=${t("onb.failTitle")} role="alert" data-testid="onb-error">
      <p>${failText}</p>${fail.message && html`<p class="mono xsmall mt-1">${fail.message}</p>`}
    </${Callout}>`}
    <div class="form-grid">
      <${Field} label=${t("onb.code")} hint=${t("onb.codeHint")} error=${errs.code}>
        ${(id, d) => html`<textarea id=${id} class="input textarea mono onb-code" value=${code} rows="3" autofocus data-testid="onb-code"
          placeholder="MESH1-AEAWVQFQ-…" spellcheck="false" autocapitalize="characters" autocomplete="off"
          aria-describedby=${d} aria-invalid=${errs.code ? "true" : undefined} onInput=${(e) => setCode(e.target.value)}></textarea>`}
      </${Field}>
      <${Field} label=${t("onb.deviceName")} error=${errs.name} hint=${t("onb.deviceNameHintJoin")}
          extra=${html`<${DnsPreview} name=${name.trim() ? name : placeholder} />`}>
        ${(id, d) => html`<input id=${id} class="input" value=${name} placeholder=${placeholder} maxlength="63" autocapitalize="off" spellcheck="false" data-testid="onb-device-name"
          aria-describedby=${d} aria-invalid=${errs.name ? "true" : undefined}
          onInput=${(e) => setName(e.target.value)} onBlur=${() => setName(normalizeName(name))} />`}
      </${Field}>
    </div>
    <${Button} type="submit" variant="primary" size="lg" block icon="link" data-testid="onb-submit">${t("onb.join.submit")}</${Button}>
  </form>`;
}

export function OnboardingView() {
  const self = useStore((s) => s.self);
  const removed = useStore((s) => s.removed);
  const [mode, setMode] = useState(null); // null | create | join
  const back = () => setMode(null);
  return html`<div class="onb" data-testid="page-onboarding">
    <${Corner} />
    <main class="onb__main" id="main" tabindex="-1">
      ${removed && html`<${RemovedNotice} removed=${removed} />`}
      <header class="onb__hero">
        <div class="onb__brand"><${Logo} size=${56} /><span class="onb__wordmark">${t("app.name")}</span></div>
        <h1 class="onb__title">${t("onb.title")}</h1>
        <p class="onb__lead">${t("onb.lead1")}</p>
        <p class="onb__lead onb__lead--2">${t("onb.lead2")}</p>
      </header>

      ${!mode && html`<div class="onb__choices">
        <button type="button" class="onb-choice" onClick=${() => setMode("create")} data-testid="onb-create">
          <span class="onb-choice__icon"><${Icon} name="sparkle" size=${26} /></span>
          <span class="onb-choice__title">${t("onb.create.title")}</span>
          <span class="onb-choice__text">${t("onb.create.card")}</span>
          <span class="onb-choice__go">${t("onb.create.go")} <${Icon} name="chevronRight" size=${16} /></span>
        </button>
        <button type="button" class="onb-choice" onClick=${() => setMode("join")} data-testid="onb-join">
          <span class="onb-choice__icon onb-choice__icon--join"><${Icon} name="ticket" size=${26} /></span>
          <span class="onb-choice__title">${t("onb.join.title")}</span>
          <span class="onb-choice__text">${t("onb.join.card")}</span>
          <span class="onb-choice__go">${t("onb.join.go")} <${Icon} name="chevronRight" size=${16} /></span>
        </button>
      </div>`}

      ${mode && html`<div class=${cx("onb__panel", `onb__panel--${mode}`)}>
        ${mode === "create" ? html`<${CreateForm} self=${self} onBack=${back} />` : html`<${JoinForm} self=${self} onBack=${back} />`}
      </div>`}

      <ul class="onb__facts">
        <li><${Icon} name="shieldCheck" size=${18} /><span>${t("onb.fact1")}</span></li>
        <li><${Icon} name="zap" size=${18} /><span>${t("onb.fact2")}</span></li>
        <li><${Icon} name="key" size=${18} /><span>${t("onb.fact3")}</span></li>
      </ul>
      ${self && self.id && html`<p class="onb__id faint xsmall">${t("onb.deviceId")} <span class="mono">${self.short || self.id.slice(0, 8)}</span> · The Mesh ${self.version || ""}</p>`}
    </main>
  </div>`;
}
