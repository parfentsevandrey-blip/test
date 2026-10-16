// Evaluate a Nuxt 2 `window.__NUXT__=(function(...){...})(...)` payload (pure data) from stdin in an empty vm; print JSON.
const vm = require("vm");
let h = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (c) => (h += c));
process.stdin.on("end", () => {
  const i = h.indexOf("window.__NUXT__=");
  if (i < 0) process.exit(2);
  const ctx = { window: {} };
  vm.createContext(ctx);
  vm.runInContext(h.slice(i, h.indexOf("</script>", i)), ctx, { timeout: 5000 });
  process.stdout.write(JSON.stringify(ctx.window.__NUXT__));
});
