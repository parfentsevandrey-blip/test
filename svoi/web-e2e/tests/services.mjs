import net from "node:net";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);

// Connects to host:port, optionally sends a payload, resolves with the first bytes received.
const talk = (port, send, { host = "127.0.0.1", timeout = 6000 } = {}) =>
  new Promise((resolve, reject) => {
    const s = net.connect({ host, port });
    const timer = setTimeout(() => (s.destroy(), reject(new Error("no data from port " + port))), timeout);
    s.once("error", (e) => (clearTimeout(timer), reject(e)));
    s.once("connect", () => send && s.write(send));
    s.once("data", (d) => (clearTimeout(timer), s.destroy(), resolve(d.toString())));
  });
const refused = (port) => new Promise((resolve) => { const s = net.connect(port, "127.0.0.1"); s.once("error", (e) => resolve(e.code)); s.once("connect", () => (s.destroy(), resolve("connected"))); });

group("services", () => {
  test("a service of another device can be opened on a local port and closed again", async ({ browser, dev }) => {
    const hs = await peerByName(dev, "home-server");
    const page = await open(browser, dev.laptop, { hash: "services" });
    const ssh = page.locator(`[data-testid="service-card"][data-peer="${hs.id}"][data-service="ssh"]`);
    const web = page.locator(`[data-testid="service-card"][data-peer="${hs.id}"][data-service="web"]`);
    await ssh.waitFor();
    await ssh.getByTestId("service-connect").click();
    await ssh.getByTestId("forward-addr").waitFor();
    const sshAddr = (await ssh.getByTestId("forward-addr").innerText()).trim();
    assert(/^127\.0\.0\.1:\d+$/.test(sshAddr), "forward address: " + sshAddr);
    const sshPort = +sshAddr.split(":")[1];
    assert((await talk(sshPort)).startsWith("SSH-2.0-svoi-demo"), "the SSH banner arrives through the mesh");

    await web.getByTestId("service-connect").click();
    await web.getByTestId("forward-addr").waitFor();
    const webPort = +(await web.getByTestId("forward-addr").innerText()).split(":")[1];
    const resp = await talk(webPort, "GET / HTTP/1.0\r\nHost: x\r\n\r\n");
    assert(/Домашняя страница \(демо\)/.test(resp), "the web service answers: " + resp.slice(0, 60));
    eq((await dev.laptop.api("GET", "/api/forwards")).length, 2, "forwards known to the backend");

    await ssh.getByTestId("service-disconnect").click();
    await page.waitForFunction((p) => document.querySelector(`[data-testid="service-card"][data-service="ssh"]`).dataset.forwarded === "false");
    await until(async () => (await refused(sshPort)) === "ECONNREFUSED", 5000, "the closed forward to refuse connections");
    eq((await dev.laptop.api("GET", "/api/forwards")).length, 1, "one forward left");
    eq(page.problems, [], "console / network problems");
  });

  test("a local service can be published and another device (behind a different NAT) reaches it", async ({ browser, dev }) => {
    // an echo server on this machine stands in for "something running on the laptop"
    const echo = net.createServer((c) => c.pipe(c));
    await new Promise((r) => echo.listen(0, "127.0.0.1", r));
    try {
      const addr = `127.0.0.1:${echo.address().port}`;
      const lap = (await dev.laptop.api("GET", "/api/state")).self;
      const page = await open(browser, dev.laptop, { hash: "services" });
      await tid(page, "service-publish").click();
      await tid(page, "service-name").fill("echo");
      await tid(page, "service-addr").fill(addr);
      await tid(page, "service-save").click();
      await page.locator('[data-testid="published-service"][data-name="echo"]').waitFor();
      // the NAS sees it and opens it through the mesh
      const listed = await until(async () => (await dev.nas.api("GET", `/api/peers/${lap.id}/services`)).find((s) => s.name === "echo"), 10000, "the NAS to see the service");
      assert(listed, "service listed on the NAS");
      const fw = await dev.nas.api("POST", "/api/forwards", { peer: lap.id, service: "echo", listen: "127.0.0.1:0" });
      const port = +fw.listen.split(":")[1];
      for (const msg of ["привет", "x".repeat(50000)]) {
        const got = await new Promise((resolve, reject) => {
          const s = net.connect(port, "127.0.0.1");
          let buf = "";
          const t = setTimeout(() => (s.destroy(), reject(new Error("echo timed out"))), 8000);
          s.on("data", (d) => { buf += d; if (Buffer.byteLength(buf) >= Buffer.byteLength(msg)) (clearTimeout(t), s.destroy(), resolve(buf)); });
          s.on("error", reject);
          s.write(msg);
        });
        eq(got, msg, "echo through the mesh");
      }
      await dev.nas.api("DELETE", `/api/forwards/${fw.id}`);
      // unpublish through the UI
      await page.locator('[data-testid="published-service"][data-name="echo"] button[aria-label]').last().click();
      await tid(page, "confirm-ok").click();
      await page.locator('[data-testid="published-service"][data-name="echo"]').waitFor({ state: "detached" });
      await until(async () => !(await dev.nas.api("GET", `/api/peers/${lap.id}/services`)).some((s) => s.name === "echo"), 10000, "the NAS to see the service vanish");
      eq(page.problems, [], "console / network problems");
    } finally {
      echo.close();
    }
  });

  test("an administrator publishes a service on another device from this one", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { hash: `services?d=${nas.id}` });
    await tid(page, "service-publish").waitFor();
    await tid(page, "service-publish").click();
    await tid(page, "service-name").fill("backup");
    await tid(page, "service-addr").fill("127.0.0.1:873");
    await tid(page, "service-save").click();
    await page.locator('[data-testid="published-service"][data-name="backup"]').waitFor();
    // it exists on the NAS itself (executed there, as if local)
    const local = await dev.nas.api("GET", "/api/services");
    assert(local.some((s) => s.name === "backup" && s.addr === "127.0.0.1:873"), "service created on the NAS");
    // and a non-admin cannot do this to someone else
    const refusal = await dev.nas.api("GET", `/api/d/${(await dev.nas.api("GET", "/api/state")).peers.find((p) => p.deviceName === "laptop").id}/services`, undefined, { raw: true });
    eq(refusal.status, 403, "a member without admin rights cannot manage another device");
    eq(page.problems.filter((p) => !/403/.test(p)), [], "console / network problems");
  });
});
