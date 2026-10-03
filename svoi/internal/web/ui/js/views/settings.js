// Settings: this device, network (NAT diagnosis), files, interface,
// advanced, about + logs. Admins can switch to another device's settings.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { getLang, t, tx } from "../i18n.js";
import { devPrefix, get, isSelf, post, put } from "../api.js";
import { go, href } from "../router.js";
import { refreshState, stopLive } from "../sse.js";
import { setState, useStore } from "../store.js";
import { fmtBytes, fmtDateTime, fmtDuration, fmtNumber } from "../format.js";
import { useAsync, useInterval } from "../hooks.js";
import { langPref, setLangPref, setThemePref, themePref } from "../prefs.js";
import { cx, deviceKind, isValidHostPort, kindIcon, natTone } from "../util.js";
import { deviceName, ManageDeviceSelect } from "../components/devicepicker.js";
import { FolderPicker } from "../components/folderpicker.js";
import { PageHeader } from "../components/misc.js";
import { Button, Callout, Card, Chip, CopyButton, EmptyState, Field, IconButton, KV, Segmented, Skeleton, Spinner, Switch } from "../components/ui.js";
import { confirmDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { osName } from "./devices.js";
import { LogViewer } from "./logs.js";

const SECTIONS = [
  { id: "device", icon: "laptop" },
  { id: "network", icon: "globe" },
  { id: "tun", icon: "network" },
  { id: "files", icon: "files" },
  { id: "interface", icon: "sun" },
  { id: "advanced", icon: "settings" },
  { id: "about", icon: "info" },
];

/** Self + Settings of this device or of a managed one (through /api/d/:id). */
function useDeviceConfig(dev) {
  const local = isSelf(dev);
  const storeSelf = useStore((s) => s.self);
  const storeSettings = useStore((s) => s.settings);
  const remote = useAsync((signal) => (local ? Promise.resolve(null) : get(`d/${encodeURIComponent(dev)}/state`, { signal })), [dev]);
  useEffect(() => {
    if (local && !storeSettings) get("settings").then((s) => setState({ settings: s })).catch(() => {});
  }, [local]);
  const self = local ? storeSelf : remote.data && remote.data.self;
  const settings = local ? storeSettings : remote.data && remote.data.settings;
  const setSettings = (st) => (local ? setState({ settings: st }) : remote.setData((d) => ({ ...d, settings: st })));
  /** PUT a subset; opts.quiet skips the "saved" toast, opts.inline lets the caller show errors itself. */
  const save = async (patch, opts = {}) => {
    try {
      const r = await put(`${devPrefix(dev)}settings`, patch);
      setSettings(r);
      if (!opts.quiet) toast({ level: "success", title: t("common.saved") });
      return r;
    } catch (e) {
      if (opts.inline) throw e;
      toastError(e, t("set.saveFailed"));
      return null; // callers keep their unsaved input; no unhandled rejection from switches
    }
  };
  const setSelf = (s) => (local ? setState({ self: s }) : remote.setData((d) => ({ ...d, self: s })));
  return { local, self, settings, loading: !local && remote.loading, error: !local ? remote.error : null, reload: remote.reload, save, setSelf, setSettings };
}

function Section({ id, title, icon, children, sub }) {
  return html`<section class="set-sec" id=${"set-" + id} aria-labelledby=${"set-h-" + id} data-testid=${"settings-" + id}>
    <header class="set-sec__head">
      <span class="card__icon"><${Icon} name=${icon} size=${18} /></span>
      <div><h2 class="set-sec__title" id=${"set-h-" + id}>${title}</h2>${sub && html`<p class="card__sub">${sub}</p>`}</div>
    </header>
    ${children}
  </section>`;
}

/** Text/number input that shows a Save button once changed. */
function SaveField({ label, hint, value, onSave, mono, type = "text", placeholder, validate, extra, inputMode, width }) {
  const [v, setV] = useState(value ?? "");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { setV(value ?? ""); setErr(""); }, [value]);
  const dirty = String(v) !== String(value ?? "");
  const save = async (e) => {
    e && e.preventDefault();
    const problem = validate ? validate(v) : "";
    if (problem) { setErr(problem); return; }
    setBusy(true);
    try { await onSave(type === "number" ? Number(v) : v.trim()); } catch { /* toast shown */ }
    setBusy(false);
  };
  return html`<${Field} label=${label} hint=${hint} error=${err}>
    ${(id, d) => html`<form class="savefield" onSubmit=${save}>
      <input id=${id} class=${cx("input", mono && "mono")} type=${type === "number" ? "text" : type} inputmode=${inputMode || (type === "number" ? "numeric" : undefined)}
        value=${v} placeholder=${placeholder} aria-describedby=${d} style=${width ? `max-width:${width}` : undefined}
        spellcheck="false" autocapitalize="off" onInput=${(e) => { setV(e.target.value); setErr(""); }} />
      ${extra}
      ${dirty && html`<${Button} type="submit" size="sm" variant="primary" loading=${busy}>${t("common.save")}</${Button}>
        <${Button} size="sm" variant="ghost" onClick=${() => { setV(value ?? ""); setErr(""); }}>${t("common.cancel")}</${Button}>`}
    </form>`}
  </${Field}>`;
}

// ---------------------------------------------------------------- this device
function DeviceSection({ cfg }) {
  const peers = useStore((s) => s.peers);
  const self = cfg.self;
  if (!self) return null;
  const leave = async () => {
    const lastAdmin = self.admin && !peers.some((p) => p.admin);
    const ok = await confirmDialog({
      title: t("set.leaveTitle", { name: self.meshName }),
      text: html`<p>${t("set.leaveText1", { name: self.meshName })}</p><p>${t("set.leaveReset")}</p><p>${t("set.leaveText2")}</p>
        ${lastAdmin && html`<p class="warn-text strong">${t("set.leaveLastAdmin")}</p>`}`,
      confirmText: t("set.leave"), danger: true, icon: "logout", requireText: self.meshName,
      requireLabel: t("set.leaveType", { name: self.meshName }),
    });
    if (!ok) return;
    try { await post("mesh/leave", {}); toast({ level: "success", title: t("set.left") }); await refreshState(); }
    catch (e) { toastError(e); }
  };
  // Ends only this browser's session; signing in again needs a new link (`svoi open`).
  const logout = async () => {
    const ok = await confirmDialog({
      title: t("set.logoutTitle"), icon: "logout", confirmText: t("set.logout"),
      text: html`<p>${tx("set.logoutText", { open: html`<code class="mono">svoi open</code>`, url: html`<code class="mono">svoi url</code>` })}</p>`,
    });
    if (!ok) return;
    try { await post("logout", {}); }
    catch (e) { if (e.code !== "unauthorized") { toastError(e); return; } } // already gone: fine
    stopLive();
    setState({ authError: true, signedOut: true });
  };
  return html`<${Section} id="device" icon=${kindIcon[deviceKind(self)]} title=${cfg.local ? t("set.sec.device") : t("set.sec.deviceRemote", { name: self.name })}>
    <${Card}>
      <${KV} items=${[
        { k: t("set.devName"), v: self.name },
        self.owner && { k: t("dev.owner"), v: self.owner },
        { k: t("set.role"), v: html`${self.admin ? html`<${Chip} tone="accent" icon="shield" size="sm">${t("dev.admin")}</${Chip}>` : t("set.member")}` },
        { k: t("dev.id"), v: self.id, mono: true, copy: self.id },
        self.ip4 && { k: "IPv4", v: self.ip4, mono: true, copy: self.ip4 },
        self.ip6 && { k: "IPv6", v: self.ip6, mono: true, copy: self.ip6 },
        { k: t("set.mesh"), v: self.meshName || "—" },
        self.meshId && { k: t("set.meshId"), v: self.meshId, mono: true, copy: self.meshId },
        { k: t("dev.os"), v: `${osName(self.os)}${self.arch ? " · " + self.arch : ""}` },
        { k: t("dev.version"), v: self.version ? "v" + self.version : "—" },
        self.started && { k: t("dev.uptime"), v: fmtDuration(Math.floor(Date.now() / 1000) - self.started) },
      ]} />
    </${Card}>
    ${cfg.local && html`<${Card}>
      <div class="row row--top gap-4">
        <div class="grow"><h3 class="strong">${t("set.session")}</h3><p class="muted small">${t("set.sessionHint")}</p></div>
        <${Button} variant="secondary" icon="logout" onClick=${logout} data-testid="logout">${t("set.logout")}</${Button}>
      </div>
    </${Card}>`}
    ${cfg.local && html`<${Card} class="danger-zone">
      <div class="row row--top gap-4">
        <div class="grow"><h3 class="strong">${t("set.leave")}</h3><p class="muted small">${t("set.leaveHint")}</p></div>
        <${Button} variant="danger-ghost" icon="logout" onClick=${leave} data-testid="leave-mesh">${t("set.leaveBtn")}</${Button}>
      </div>
    </${Card}>`}
  </${Section}>`;
}

// ---------------------------------------------------------------- network
function NatCard({ cfg }) {
  const self = cfg.self;
  const [checking, setChecking] = useState(false);
  const nat = (self && self.nat) || {};
  const diff = nat.difficulty || "unknown";
  const tone = natTone(diff);
  const check = async () => {
    setChecking(true);
    try {
      const r = await post("netcheck", {});
      if (r && r.self) cfg.setSelf(r.self);
      toast({ level: "success", title: t("nat.checked"), text: t("nat.title." + ((r && r.self && r.self.nat && r.self.nat.difficulty) || "unknown")) });
    } catch (e) { toastError(e); }
    setChecking(false);
  };
  const eps = (self && self.endpoints) || [];
  const mapped = self && self.portmap && self.portmap.state === "mapped" ? self.portmap : null;
  return html`<${Card} class=${cx("nat", `nat--${tone}`)}>
    <div class="nat__head">
      <span class="nat__badge"><${Icon} name=${tone === "ok" ? "shieldCheck" : tone === "warn" ? "alert" : "radar"} size=${28} /></span>
      <div class="grow">
        <p class="nat__eyebrow">${t("nat.diag")}</p>
        <h3 class="nat__title">${t("nat.head." + diff)}</h3>
        <p class="nat__text">${diff === "open" && mapped
          ? t("nat.text.openMapped", { proto: PM_PROTO[mapped.protocol] || "UPnP" })
          : t("nat.text." + diff, { port: (self && self.udpPort) || 41710 })}</p>
      </div>
      ${cfg.local && html`<${Button} icon="radar" loading=${checking} onClick=${check} class="nat__btn" data-testid="netcheck">${t("nat.check")}</${Button}>`}
    </div>
    <div class="nat__means">
      <h4 class="section-title">${t("nat.meansTitle")}</h4>
      <p class="small muted">${t("nat.means." + diff)}</p>
    </div>
    <dl class="nat__facts">
      <div><dt>${t("nat.public")}</dt><dd class="mono">${(nat.public || []).length ? nat.public.join(", ") : "—"}</dd></div>
      <div><dt>${t("nat.mapping")}</dt><dd>${nat.mappingVaries ? html`<span class="warn-text">${t("nat.mappingVaries")}</span>` : t("nat.mappingStable")}</dd></div>
      <div><dt>IPv6</dt><dd>${nat.hasIPv6 ? t("nat.ipv6Yes") : t("nat.ipv6No")}</dd></div>
      <div><dt>STUN</dt><dd>${nat.stun ? t("nat.stunOn") : html`<span class="faint">${t("nat.stunOff")}</span>`}</dd></div>
    </dl>
    ${eps.length > 0 && html`<div class="nat__eps">
      <h4 class="section-title">${t("nat.endpoints")}</h4>
      <ul class="eplist">${eps.map((e, i) => html`<li key=${i} class="eplist__row">
        <span class="mono small grow">${e.addr}</span>
        <${Chip} size="sm" tone=${e.kind === "local" ? "outline" : e.kind === "stun" ? "info" : e.kind === "mapped" ? "ok" : "accent"} icon=${e.kind === "mapped" ? "shieldCheck" : undefined}
          title=${t("nat.kindHint." + e.kind)} data-kind=${e.kind}>${t("nat.kind." + e.kind)}</${Chip}>
        <${CopyButton} text=${e.addr} />
      </li>`)}</ul>
    </div>`}
    ${!cfg.local && html`<p class="faint xsmall mt-3">${t("nat.remoteNoCheck")}</p>`}
  </${Card}>`;
}

const PM_PROTO = { upnp: "UPnP", natpmp: "NAT-PMP" };

/**
 * Router port mapping status under its switch (self.portmap). While the switch
 * is on but the node has not reported yet, it reads as "searching".
 */
function PortmapStatus({ pm }) {
  const st = (pm && pm.state) || "searching";
  const tone = st === "mapped" ? "ok" : st === "private" ? "warn" : "neutral";
  const proto = PM_PROTO[pm && pm.protocol] || "UPnP";
  const diag = (st === "unavailable" || st === "private") && pm
    ? [pm.gateway && t("pmap.gateway", { gw: pm.gateway }), pm.error].filter(Boolean).join(" · ")
    : "";
  return html`<div class=${cx("pmap", `pmap--${tone}`)} data-testid="portmap-status" data-state=${st} role="status">
    <span class="pmap__icon">${st === "searching" ? html`<${Spinner} size=${14} />`
      : html`<${Icon} name=${st === "mapped" ? "checkCircle" : st === "private" ? "alert" : "info"} size=${16} />`}</span>
    <div class="grow pmap__body">
      ${st === "mapped"
        ? html`<span>${tx("pmap.mapped", { proto, addr: html`<span class="mono strong">${pm.external || "—"}</span>` })}</span>`
        : html`<span>${st === "searching" ? t("pmap.searching") : st === "private" ? t("pmap.private") : t("pmap.unavailable")}</span>`}
      ${diag && html`<span class="pmap__diag mono xsmall">${diag}</span>`}
    </div>
    ${st === "mapped" && pm.external && html`<${CopyButton} text=${pm.external} />`}
  </div>`;
}

function StunEditor({ list, onChange, disabled }) {
  const [v, setV] = useState("");
  const [err, setErr] = useState("");
  const add = (e) => {
    e.preventDefault();
    const x = v.trim();
    if (!isValidHostPort(x)) { setErr(t("set.stunInvalid")); return; }
    if (list.includes(x)) { setErr(t("set.stunDup")); return; }
    onChange([...list, x]);
    setV("");
  };
  return html`<div class="stun">
    <ul class="stun__list">${list.map((s) => html`<li key=${s} class="stun__row"><${Icon} name="globe" size=${15} /><span class="mono small grow">${s}</span>
      <${IconButton} icon="x" size="sm" label=${t("set.stunRemove", { name: s })} disabled=${disabled} onClick=${() => onChange(list.filter((x) => x !== s))} /></li>`)}
      ${!list.length && html`<li class="faint small">${t("set.stunEmpty")}</li>`}</ul>
    <form class="savefield" onSubmit=${add}>
      <input class="input mono" value=${v} placeholder="stun.example.org:3478" aria-label=${t("set.stunAdd")} disabled=${disabled}
        onInput=${(e) => { setV(e.target.value); setErr(""); }} />
      <${Button} type="submit" size="sm" icon="plus" disabled=${disabled || !v.trim()}>${t("common.add")}</${Button}>
    </form>
    ${err && html`<p class="field__error">${err}</p>`}
  </div>`;
}

function NetworkSection({ cfg }) {
  const s = cfg.settings;
  const self = cfg.self;
  // Like the STUN switch: a plain save (the node restarts its network layer for
  // a second). This device reports the mapping through `self` events; a managed
  // device has no events here, so read its state again a moment later.
  const savePortMap = async (v) => {
    const r = await cfg.save({ portMap: v });
    if (r && !cfg.local) setTimeout(() => cfg.reload(), 2000);
  };
  return html`<${Section} id="network" icon="globe" title=${t("set.sec.network")} sub=${t("set.sec.networkSub")}>
    <${NatCard} cfg=${cfg} />
    ${s && html`<${Card}>
      <${Switch} label=${t("set.relay")} description=${html`${t("set.relayHint")}${self && self.relayed && self.relayed.bytes ? html` <span class="tnum">${t("set.relayed", { packets: fmtNumber(self.relayed.packets), bytes: fmtBytes(self.relayed.bytes) })}</span>` : ""}`}
        checked=${s.relay} onChange=${(v) => cfg.save({ relay: v })} testid="setting-relay" />
      <${Switch} label=${t("set.lan")} description=${t("set.lanHint")} checked=${s.lan} onChange=${(v) => cfg.save({ lan: v })} />
      <${Switch} label=${t("set.stun")} description=${t("set.stunHint")} checked=${s.stunEnabled} onChange=${(v) => cfg.save({ stunEnabled: v })} />
      ${s.stunEnabled && html`<div class="set-sub"><span class="field__label">${t("set.stunServers")}</span>
        <${StunEditor} list=${s.stunServers || []} onChange=${(l) => cfg.save({ stunServers: l })} /></div>`}
      ${typeof s.portMap === "boolean" && html`
        <${Switch} label=${t("set.portMap")} description=${t("set.portMapHint")} checked=${s.portMap} onChange=${savePortMap} testid="setting-portmap" />
        ${s.portMap && html`<${PortmapStatus} pm=${self && self.portmap} />`}`}
      <div class="set-sub">
        <${SaveField} label=${t("set.udpPort")} hint=${t("set.udpPortHint")} value=${s.udpPort} type="number" width="160px" mono
          validate=${(v) => (!/^\d+$/.test(String(v).trim()) || Number(v) > 65535 ? t("set.portInvalid") : "")}
          onSave=${(v) => cfg.save({ udpPort: v })} />
      </div>
    </${Card}>`}
  </${Section}>`;
}

// ---------------------------------------------------------------- network interface (TUN)
function TunSection({ cfg, dev }) {
  const tun = cfg.settings && cfg.settings.tun;
  const peers = useStore((s) => s.peers);
  const [busy, setBusy] = useState("");
  const [reqErr, setReqErr] = useState(null);
  // Packet counters are live but there is no SSE event for settings: poll while enabled.
  useInterval(async () => {
    try { cfg.setSettings(await get(`${devPrefix(dev)}settings`)); } catch { /* keep the last value */ }
  }, 3000, !!(tun && tun.enabled));
  if (!tun || !tun.supported) return null;

  const set = async (patch, which) => {
    setBusy(which);
    setReqErr(null);
    // A failed start still answers 200 with state:"error" — shown inline below, not as a toast.
    try { await cfg.save({ tun: patch }, { quiet: true, inline: true }); } catch (e) { setReqErr(e); }
    setBusy("");
  };
  const name = tun.name || "svoi0";
  const sample = peers.find((p) => p.online) || peers[0];
  return html`<${Section} id="tun" icon="network" title=${t("tun.title")} sub=${t("tun.sub")}>
    <${Card} class="tun" data-testid="tun-section">
      <p class="tun__lead">${t("tun.lead", { example: sample ? `${sample.deviceName || sample.name}.svoi` : "nas.svoi", ip: sample ? sample.ip4 : "100.64.0.7" })}</p>
      <div class=${cx("tun__state", `is-${tun.state}`)} data-testid="tun-state" data-state=${tun.state} role="status">
        ${tun.state === "running" ? html`
          <span class="dot dot--ok dot--pulse"></span>
          <span class="strong">${t("tun.running", { name })}</span>
          <span class="tun__counters tnum">${t("tun.counters", { tx: fmtNumber(tun.txPackets || 0), rx: fmtNumber(tun.rxPackets || 0) })}${tun.dropped ? ` · ${t("tun.dropped", { n: fmtNumber(tun.dropped) })}` : ""}</span>`
        : tun.state === "error" ? html`
          <span class="dot dot--err"></span><span class="strong danger-text">${t("tun.error")}</span>`
        : html`<span class="dot dot--off"></span><span class="muted">${t("tun.off")}</span>`}
      </div>
      ${tun.state === "error" && tun.error && html`<${Callout} tone="err" title=${t("tun.errorTitle")} role="alert">
        <p class="mono small break">${tun.error}</p><p class="mt-1">${/permission|root|cap_net_admin/i.test(tun.error) ? t("tun.errorPerm") : t("tun.errorGeneric")}</p>
      </${Callout}>`}
      ${reqErr && html`<${Callout} tone="err" title=${t("set.saveFailed")}>${t("err." + reqErr.code)}${reqErr.message ? ` — ${reqErr.message}` : ""}</${Callout}>`}
      <div>
        <${Switch} label=${t("tun.enable", { name })} description=${t("tun.enableHint")} checked=${!!tun.enabled}
          disabled=${busy === "enabled"} testid="tun-enabled" onChange=${(v) => set({ enabled: v }, "enabled")} />
        <${Switch} label=${t("tun.hosts")} description=${t("tun.hostsHint")} checked=${!!tun.manageHosts}
          disabled=${busy === "hosts"} testid="tun-hosts" onChange=${(v) => set({ manageHosts: v }, "hosts")} />
      </div>
      ${tun.state === "running" && sample && html`<div class="tun__try">
        <span class="faint xsmall">${t("tun.try")}</span>
        <div class="code-line"><span>ping ${sample.ip4}</span><${CopyButton} text=${`ping ${sample.ip4}`} /></div>
        ${tun.manageHosts && html`<div class="code-line"><span>ssh ${sample.deviceName || sample.name}.svoi</span><${CopyButton} text=${`ssh ${sample.deviceName || sample.name}.svoi`} /></div>`}
      </div>`}
    </${Card}>
  </${Section}>`;
}

// ---------------------------------------------------------------- files
function FilesSection({ cfg, dev }) {
  const s = cfg.settings;
  const [picking, setPicking] = useState(false);
  if (!s) return null;
  const owner = cfg.self && cfg.self.owner;
  const opts = [
    { v: "own", title: t("set.aa.own"), text: owner ? t("set.aa.ownText", { owner }) : t("set.aa.ownTextNoOwner") },
    { v: "all", title: t("set.aa.all"), text: t("set.aa.allText") },
    { v: "ask", title: t("set.aa.ask"), text: t("set.aa.askText") },
  ];
  return html`<${Section} id="files" icon="files" title=${t("set.sec.files")} sub=${t("set.sec.filesSub")}>
    <${Card} class="stack stack--lg">
      <${SaveField} label=${t("set.downloadDir")} hint=${t("set.downloadDirHint")} value=${s.downloadDir} mono
        validate=${(v) => (!String(v).trim() ? t("common.required") : "")}
        onSave=${(v) => cfg.save({ downloadDir: v })}
        extra=${html`<${Button} size="sm" icon="folderOpen" onClick=${() => setPicking(true)}>${t("shares.pick")}</${Button}>`} />
      <fieldset class="fieldset">
        <legend class="field__label">${t("set.autoAccept")}</legend>
        <div class="radio-cards">
          ${opts.map((o) => html`<label key=${o.v} class=${cx("radio-card", s.autoAccept === o.v && "is-on")}>
            <input type="radio" name=${"aa-" + dev} checked=${s.autoAccept === o.v} onChange=${() => cfg.save({ autoAccept: o.v })} />
            <span><span class="radio-card__title">${o.title}</span><span class="radio-card__text">${o.text}</span></span>
          </label>`)}
        </div>
      </fieldset>
      ${s.autoAccept !== "ask" && html`<${SaveField} label=${t("set.maxMB")} hint=${t("set.maxMBHint")} value=${s.autoAcceptMaxMB} type="number" width="160px"
        validate=${(v) => (!/^\d+$/.test(String(v).trim()) ? t("set.numberInvalid") : "")}
        onSave=${(v) => cfg.save({ autoAcceptMaxMB: v })} />`}
    </${Card}>
    ${picking && html`<${FolderPicker} dev=${dev} initial=${s.downloadDir} title=${t("set.downloadDir")}
      onPick=${(p) => { setPicking(false); cfg.save({ downloadDir: p }); }} onClose=${() => setPicking(false)} />`}
  </${Section}>`;
}

// ---------------------------------------------------------------- interface
function InterfaceSection() {
  const [lang, setL] = useState(langPref());
  const [theme, setT] = useState(themePref());
  useStore((s) => s.theme);
  return html`<${Section} id="interface" icon="sun" title=${t("set.sec.interface")} sub=${t("set.sec.interfaceSub")}>
    <${Card} class="stack stack--lg">
      <div class="field">
        <span class="field__label">${t("set.ui.language")}</span>
        <${Segmented} label=${t("set.ui.language")} value=${lang} onChange=${(v) => { setL(v); setLangPref(v); }}
          options=${[{ value: "auto", label: t("set.ui.auto"), icon: "languages", testid: "lang-auto" }, { value: "ru", label: "Русский", testid: "lang-ru" }, { value: "en", label: "English", testid: "lang-en" }]} />
        <p class="field__hint">${lang === "auto" ? t("set.ui.langAutoHint", { lang: getLang() === "ru" ? "Русский" : "English" }) : t("set.ui.langHint")}</p>
      </div>
      <div class="field">
        <span class="field__label">${t("set.ui.theme")}</span>
        <${Segmented} label=${t("set.ui.theme")} value=${theme} onChange=${(v) => { setT(v); setThemePref(v); }}
          options=${[{ value: "auto", label: t("set.ui.themeAuto"), icon: "auto", testid: "theme-auto" }, { value: "light", label: t("set.ui.themeLight"), icon: "sun", testid: "theme-light" }, { value: "dark", label: t("set.ui.themeDark"), icon: "moon", testid: "theme-dark" }]} />
      </div>
    </${Card}>
  </${Section}>`;
}

// ---------------------------------------------------------------- advanced
function AdvancedSection({ cfg }) {
  const s = cfg.settings;
  if (!s) return null;
  const socks = s.socks || { enabled: false, listen: "127.0.0.1:1080" };
  return html`<${Section} id="advanced" icon="settings" title=${t("set.sec.advanced")}>
    <${Card}>
      <${Switch} label=${t("set.socks")} description=${t("set.socksHint")} checked=${socks.enabled} onChange=${(v) => cfg.save({ socks: { ...socks, enabled: v } })} />
      ${socks.enabled && html`<div class="set-sub">
        <${SaveField} label=${t("set.socksListen")} hint=${t("set.socksListenHint")} value=${socks.listen} mono width="240px"
          validate=${(v) => (!isValidHostPort(v) ? t("svc.addrInvalid") : "")}
          onSave=${(v) => cfg.save({ socks: { ...socks, listen: v } })} />
      </div>`}
    </${Card}>
  </${Section}>`;
}

// ---------------------------------------------------------------- about
function AboutSection({ cfg, dev }) {
  const version = useStore((s) => s.version);
  const [showLogs, setShowLogs] = useState(false);
  const self = cfg.self;
  return html`<${Section} id="about" icon="info" title=${t("set.sec.about")}>
    <${Card} class="about">
      <div class="about__brand">
        <svg class="logo-mark" width="48" height="48" viewBox="0 0 48 48" aria-hidden="true"><rect x="1.5" y="1.5" width="45" height="45" rx="14" class="logo-mark__bg"/><path d="M24 14.5 14.2 31.5h19.6Z" class="logo-mark__edges"/><circle cx="24" cy="14.5" r="4.6" class="logo-mark__node"/><circle cx="14.2" cy="31.5" r="4.6" class="logo-mark__node"/><circle cx="33.8" cy="31.5" r="4.6" class="logo-mark__node"/></svg>
        <div><p class="about__name">${t("app.name")} <span class="mono small faint">v${(self && self.version) || version}</span></p>
          <p class="muted small">${t("set.about.tagline")}</p></div>
      </div>
      <${KV} items=${[
        { k: t("dev.version"), v: (self && self.version) || version || "—", mono: true },
        self && { k: t("dev.os"), v: `${osName(self.os)} · ${self.arch}` },
        self && self.started && { k: t("set.about.started"), v: fmtDateTime(self.started) },
        { k: t("set.about.licenses"), v: html`<span class="row row--wrap gap-3"><a href="vendor/LICENSE-preact.txt" target="_blank" rel="noopener">Preact (MIT)</a><a href="vendor/LICENSE-htm.txt" target="_blank" rel="noopener">htm (Apache-2.0)</a></span>` },
      ]} />
    </${Card}>
    <${Card} class="logs-card">
      <div class="row row--between">
        <div><h3 class="strong">${t("logs.title")}</h3><p class="muted small">${cfg.local ? t("logs.sub") : t("logs.subRemote", { name: deviceName(dev) })}</p></div>
        <${Button} size="sm" icon=${showLogs ? "chevronUp" : "terminal"} onClick=${() => setShowLogs(!showLogs)} aria-expanded=${String(showLogs)}>${showLogs ? t("logs.hide") : t("logs.show")}</${Button}>
      </div>
      ${showLogs && html`<${LogViewer} dev=${dev} />`}
    </${Card}>
  </${Section}>`;
}

export function SettingsView({ route }) {
  const section = route.parts[1] || null;
  const dev = route.query.get("d") || "self";
  const cfg = useDeviceConfig(dev);
  const remotePeer = useStore((s) => (!cfg.local ? s.peers.find((p) => p.id === dev) || null : null));
  const mainRef = useRef(null);
  const setDev = (id) => go(href(["settings", section], { d: isSelf(id) ? undefined : id }));

  useEffect(() => {
    if (!section) return;
    const el = document.getElementById("set-" + section);
    if (el) setTimeout(() => el.scrollIntoView({ block: "start", behavior: "smooth" }), 60);
  }, [section, cfg.loading]);

  let body;
  if (!cfg.local && remotePeer && !remotePeer.online) {
    body = html`<${Card}><${EmptyState} icon="wifiOff" tone="warn" title=${t("manage.offlineTitle", { name: remotePeer.name })} text=${t("manage.offlineText")} /></${Card}>`;
  } else if (cfg.loading) {
    body = html`<div class="stack stack--lg">${[0, 1, 2].map((i) => html`<${Card} key=${i}><div class="stack"><${Skeleton} w="30%" h=${18} /><${Skeleton} h=${12} /><${Skeleton} w="70%" h=${12} /></div></${Card}>`)}</div>`;
  } else if (cfg.error) {
    body = html`<${Card}><${EmptyState} icon="alertCircle" tone="err" title=${t("set.loadError")} text=${t("err." + cfg.error.code)}>
      <${Button} icon="refresh" onClick=${() => cfg.reload()}>${t("common.retry")}</${Button}></${EmptyState}></${Card}>`;
  } else {
    body = html`
      ${cfg.settings && cfg.settings.restartRequired && html`<${Callout} tone="warn" icon="refresh" title=${t("settings.restartRequired")} class="set-restart">
        ${cfg.local ? t("settings.restartText") : t("settings.restartTextRemote", { name: remotePeer ? remotePeer.name : "" })}</${Callout}>`}
      <${DeviceSection} cfg=${cfg} />
      <${NetworkSection} cfg=${cfg} />
      <${TunSection} cfg=${cfg} dev=${dev} />
      <${FilesSection} cfg=${cfg} dev=${dev} />
      ${cfg.local && html`<${InterfaceSection} />`}
      <${AdvancedSection} cfg=${cfg} />
      <${AboutSection} cfg=${cfg} dev=${dev} />`;
  }

  return html`<div class="page settings">
    <${PageHeader} title=${t("nav.settings")} subtitle=${cfg.local ? t("set.subtitle") : t("set.subtitleRemote", { name: remotePeer ? remotePeer.name : dev.slice(0, 8) })}
      actions=${html`<${ManageDeviceSelect} value=${dev} onChange=${setDev} />`} />
    <div class="settings__layout">
      <nav class="settings__nav" aria-label=${t("set.sections")}>
        ${SECTIONS.filter((s) => (cfg.local || s.id !== "interface") && (s.id !== "tun" || (cfg.settings && cfg.settings.tun && cfg.settings.tun.supported))).map((s) => html`<a key=${s.id} href=${href(["settings", s.id], { d: cfg.local ? undefined : dev })}
            class=${cx("settings__link", section === s.id && "is-active")}><${Icon} name=${s.icon} size=${17} />${!cfg.local && s.id === "device" ? t("set.sec.deviceNav") : t("set.sec." + s.id)}</a>`)}
      </nav>
      <div class="settings__main" ref=${mainRef}>${body}</div>
    </div>
  </div>`;
}
