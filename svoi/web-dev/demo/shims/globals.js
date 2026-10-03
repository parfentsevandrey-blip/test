// Injected into the browser bundle of web-dev/mock-server.mjs: the two Node globals
// it relies on. `process.argv` comes from the page address so one build serves
// every scenario: ?scenario=full|empty|onboarding, &calm=1, &latency=ms.
import { Buffer } from "buffer";

const q = new URLSearchParams(typeof location !== "undefined" ? location.search : "");
// The hosting page may not pass a query string through, so the guide panel also keeps the
// wanted scenario in sessionStorage and reloads (see panel.js).
let stored = "";
try { stored = sessionStorage.getItem("themesh.demo.scenario") || ""; } catch { /* storage may be blocked */ }
const scenario = q.get("scenario") || stored;
const argv = ["node", "mock-server.mjs"];
if (["full", "empty", "onboarding"].includes(scenario)) argv.push("--scenario", scenario);
if (q.get("calm")) argv.push("--calm");
argv.push("--latency", q.get("latency") || "35");

export { Buffer };
export const process = {
  argv,
  env: {},
  platform: "browser",
  exit() {},
  cwd: () => "/",
  on() {},
  nextTick: (f, ...a) => queueMicrotask(() => f(...a)),
  stdout: { write() {} },
  stderr: { write() {} },
};
