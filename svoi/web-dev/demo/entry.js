// Browser entry of the demo: run the mock server (it registers its request handler with
// shims/http.js while it loads), then wire the page's network to that handler.
import "../mock-server.mjs";
import { installNet } from "./net.js";
import { installDemoPanel } from "./panel.js";

// An explicit light/dark choice of the hosting viewer (data-theme on the root) becomes the
// interface's theme until the person picks one inside the interface itself.
try {
  const t = document.documentElement.getAttribute("data-theme");
  if ((t === "light" || t === "dark") && !localStorage.getItem("themesh.theme")) localStorage.setItem("themesh.theme", t);
} catch { /* storage may be blocked */ }

installNet(globalThis.__themeshMockHandler);
installDemoPanel();
