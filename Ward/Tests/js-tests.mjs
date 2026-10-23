// Проверка скриптов Ward в Node: синтаксис всех скриптов и поведение скриптлетов на заглушках браузера.
//   node Tests/js-tests.mjs
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const core = join(dirname(fileURLToPath(import.meta.url)), '..', 'Sources', 'Ward', 'Core');
const blocks = (file) => [...readFileSync(join(core, file), 'utf8').matchAll(/#"""\n([\s\S]*?)\n {4}"""#/g)]
  .map((m) => m[1].split('\n').map((l) => (l.startsWith('    ') ? l.slice(4) : l)).join('\n'));
const [fingerprint, observer] = blocks('Scripts.swift');
const [scriptlets] = blocks('ScriptletLibrary.swift');
const [procedural, consent, sponsor] = blocks('PageScripts.swift');

let ok = true;
const check = (c, m) => { console.log((c ? '✓ ' : '✗ ') + m); ok = ok && c; };

const sources = {
  'отпечаток': fingerprint.replace('__WARD_SECRET__', 'x').replace('__WARD_WEBRTC__', 'true').replace('__WARD_TZ__', 'true').replace('__WARD_LANG__', 'true'),
  'наблюдатель': observer, 'скриптлеты': scriptlets.replace('__WARD_CALLS__', '[]'), 'процедурная косметика': procedural.replace('__WARD_PROCEDURAL__', '[]'),
  'отказ от cookie': consent, 'SponsorBlock': sponsor,
};
for (const [name, src] of Object.entries(sources)) {
  try { new Function(src); check(true, `синтаксис: ${name}`); } catch (e) { check(false, `синтаксис: ${name}: ${e.message}`); }
}

// Заглушки браузера.
globalThis.window = globalThis;
globalThis.addEventListener = () => {};
globalThis.document = { readyState: 'complete', addEventListener() {}, documentElement: null, currentScript: null, querySelectorAll() { return []; }, cookie: '' };
globalThis.MutationObserver = class { observe() {} disconnect() {} };
globalThis.CSS = { escape: (s) => s };
globalThis.HTMLScriptElement = class {};
class XHR { open(m, u) { this._u = u; } send() { this.readyState = 4; } get responseText() { return this._body; } get response() { return this._body; } }
globalThis.XMLHttpRequest = XHR;
globalThis.location = { hostname: 'example.com', pathname: '/', href: 'https://example.com/' };
globalThis.fetch = async () => new Response(JSON.stringify({ playerResponse: { adPlacements: [1] }, adSlots: [2], entries: [{ command: { reelWatchEndpoint: { adClientParams: { isAd: true } } } }, { command: { ok: 1 } }] }));
const run = (calls) => new Function(scriptlets.replace('__WARD_CALLS__', JSON.stringify(calls)))();

run([[false, 'set-constant', 'adsEnabled', 'false'], [false, 'set-constant', 'ytInitialPlayerResponse.adPlacements', 'undefined'], [false, 'set-constant', 'big', '99999']]);
window.adsEnabled = true;
check(window.adsEnabled === false, 'set-constant: значение не меняется сайтом');
window.ytInitialPlayerResponse = { adPlacements: [1, 2], videoDetails: {} };
check(window.ytInitialPlayerResponse.adPlacements === undefined && window.ytInitialPlayerResponse.videoDetails, 'set-constant: цепочка на ещё не созданном объекте');
check(window.big === undefined, 'set-constant: большие числа только в доверенных списках');
run([[false, 'abort-on-property-read', 'detectAdBlock']]);
let threw = false;
try { window.detectAdBlock; } catch (e) { threw = e instanceof ReferenceError; }
check(threw, 'abort-on-property-read');
run([[false, 'no-setTimeout-if', 'adblock', '']]);
const fired = [];
setTimeout(function () { fired.push('adblock check'); }, 1);
setTimeout(function () { fired.push('normal'); }, 1);
run([[false, 'json-prune', 'playerResponse.adPlacements adSlots', '']]);
const parsed = JSON.parse('{"playerResponse":{"adPlacements":[1],"x":1},"adSlots":[1],"keep":1}');
check(parsed.playerResponse.adPlacements === undefined && parsed.adSlots === undefined && parsed.keep === 1, 'json-prune');
run([[true, 'replace-fetch-response', '"adSlots"', '"no_ads"', 'player']]);
run([[false, 'json-prune-fetch-response', 'entries.[-].command.reelWatchEndpoint.adClientParams.isAd', '', 'propsToMatch', 'url:/reel_watch']]);
run([[false, 'no-fetch-if', 'ads.example', '']]);
run([[true, 'replace-xhr-response', '/"adPlacements":\\[[^\\]]*\\],?/', '', '/player/']]);
const xhr = new XMLHttpRequest();
xhr.open('GET', 'https://www.youtube.com/youtubei/v1/player');
xhr._body = '{"adPlacements":[1,2],"video":1}';
xhr.send();
check(xhr.responseText === '{"video":1}', 'replace-xhr-response');
run([[false, 'replace-fetch-response', 'a', 'b', '']]);
const r0 = await fetch('https://example.com/a');
check(!(await r0.text()).includes('"b"'), 'replace-fetch-response не работает из недоверенного списка');
const t1 = await (await fetch('https://www.youtube.com/youtubei/v1/player')).text();
check(t1.includes('"no_ads"') && !t1.includes('"adSlots"'), 'replace-fetch-response');
const j2 = await (await fetch('https://www.youtube.com/youtubei/v1/reel_watch_sequence')).json();
check(j2.entries.length === 1 && j2.entries[0].command.ok === 1, 'json-prune-fetch-response с [-]');
check((await (await fetch('https://ads.example/track')).text()) === '', 'no-fetch-if: пустой ответ без запроса');
await new Promise((r) => setTimeout(r, 20));
check(fired.length === 1 && fired[0] === 'normal', 'no-setTimeout-if');

console.log(ok ? 'JS OK' : 'JS FAILED');
process.exit(ok ? 0 : 1);
