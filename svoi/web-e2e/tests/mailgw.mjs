// Internet mail: the person sets up an address of their own in Settings, a letter arrives from "the Internet" (spoken to the real SMTP
// server of the gateway the way another mail server does it), is read with its verdict and its formatted text in a frame that may do nothing,
// is answered, and what became of the answer is shown. The DNS is the real one: the sender's domain ends in .invalid, which no DNS knows.
import net from "node:net";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

/** One SMTP conversation with the gateway, as a mail server of the Internet holds it (no encryption: the server offers it, it does not demand it). */
async function smtpDeliver(listenAddr, { from, to, data }) {
  const [host, port] = [listenAddr.slice(0, listenAddr.lastIndexOf(":")), Number(listenAddr.slice(listenAddr.lastIndexOf(":") + 1))];
  const sock = net.connect({ host, port });
  sock.setEncoding("utf8");
  let buf = "";
  const waiters = [];
  const fire = () => {
    // a reply is complete when its last line is "NNN text" (not "NNN-text")
    while (waiters.length) {
      const lines = buf.split("\r\n");
      const end = lines.findIndex((l) => /^\d{3} /.test(l));
      if (end < 0) return;
      const reply = lines.slice(0, end + 1);
      buf = lines.slice(end + 1).join("\r\n");
      waiters.shift()({ code: Number(reply[end].slice(0, 3)), text: reply.join("\n") });
    }
  };
  sock.on("data", (d) => { buf += d; fire(); });
  const reply = () => new Promise((resolve, reject) => { waiters.push(resolve); sock.once("error", reject); fire(); });
  const say = async (line) => { sock.write(line + "\r\n"); return reply(); };
  const expect = (r, code, what) => { if (r.code !== code) throw new Error(`${what}: the gateway said ${r.text}`); return r; };
  try {
    expect(await reply(), 220, "the greeting");
    expect(await say("EHLO mx.sender.invalid"), 250, "EHLO");
    expect(await say(`MAIL FROM:<${from}>`), 250, "MAIL FROM");
    expect(await say(`RCPT TO:<${to}>`), 250, "RCPT TO");
    expect(await say("DATA"), 354, "DATA");
    sock.write(data.replace(/\r?\n/g, "\r\n").replace(/^\./gm, "..") + "\r\n.\r\n");
    expect(await reply(), 250, "the letter");
    await say("QUIT").catch(() => {});
  } finally {
    sock.destroy();
  }
}

// a 1x1 picture that belongs to the letter (cid:logo)
const PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
const LETTER = `From: Friend <friend@sender.invalid>
To: Andrey <andrey@mesh.test>
Subject: Hello from the Internet
Date: Mon, 05 Oct 2026 10:00:00 +0000
Message-ID: <e2e-1@sender.invalid>
MIME-Version: 1.0
Content-Type: multipart/related; boundary=B1

--B1
Content-Type: multipart/alternative; boundary=B2

--B2
Content-Type: text/plain; charset=utf-8

Plain hello, see https://example.com/page
--B2
Content-Type: text/html; charset=utf-8

<h2>Formatted hello</h2><p>Hello <b>world</b></p><script>window.parent.__pwned = 1</script><img src="cid:logo" alt="our logo"><img src="https://tracker.invalid/p.gif" alt=""><a href="https://example.com/page">a link</a>
--B2--
--B1
Content-Type: image/png; name="logo.png"
Content-ID: <logo>
Content-Disposition: inline; filename="logo.png"
Content-Transfer-Encoding: base64

${PNG}
--B1--
`;

group("internet mail", () => {
  test("without an address of its own a device says what is needed to write to the Internet", async ({ browser, dev }) => {
    const page = await open(browser, dev.phone, { hash: "mail/inbox" });
    await tid(page, "mail-compose").first().click();
    await tid(page, "compose-body").waitFor();
    await tid(page, "compose-email-show").click();
    await tid(page, "compose-email").fill("friend@gmail.com");
    await tid(page, "compose-email").press("Enter");
    await tid(page, "email-chip").waitFor();
    // there is no gateway: the letter cannot be sent, and the way to get one is shown
    await tid(page, "compose-no-gateway").waitFor();
    assert((await tid(page, "compose-no-gateway").locator("a").getAttribute("href")).endsWith("#/settings/mailgw"), "the link leads to the setup");
    await tid(page, "compose-subject").fill("hi");
    assert(await tid(page, "compose-send").isDisabled(), "a letter to the Internet cannot be sent without a gateway");
    // a wrong address stays in the field with the reason
    await tid(page, "compose-email").fill("not an address");
    await tid(page, "compose-email").press("Enter");
    await page.getByText("не похоже на адрес почты").waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("the setup of the gateway in Settings: domain, mailbox, advanced, switch on; the records of the domain are listed", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/mailgw" });
    await tid(page, "mailgw").waitFor();
    eq(await tid(page, "mailgw-status").getAttribute("data-state"), "off", "off until it is set up");
    // a mistake is explained next to the field, nothing is sent
    await tid(page, "mailgw-domain").fill("not a domain");
    await tid(page, "mailgw-save").click();
    await page.getByText("Домен пишется так").waitFor();
    await tid(page, "mailgw-domain").fill("mesh.test");
    await tid(page, "mailgw-add-box").click();
    await tid(page, "mailgw-box-name").fill("andrey");
    await page.locator('[data-testid="mailgw-setup"] .tech__summary').click();
    await tid(page, "mailgw-listen").fill("127.0.0.1:0"); // (any free port: the real one is 25, which a test may not take)
    await tid(page, "mailgw-enable").click();
    await tid(page, "mailgw-save").click();
    await page.getByText("Сохранено").first().waitFor();
    await until(async () => (await dev.laptop.api("GET", "/api/mailgw")).status.listening, 10000, "the gateway to listen");
    await page.locator('[data-testid="mailgw-status"][data-state="on"]').waitFor();
    // the address is the mailbox at the domain, and the plan of the DNS names the records mail needs
    const view = await dev.laptop.api("GET", "/api/mailgw");
    eq(view.mailboxes.map((b) => b.address), ["andrey@mesh.test"], "the mailbox");
    const recs = tid(page, "mailgw-rec");
    await recs.first().waitFor();
    const ids = await recs.evaluateAll((l) => l.map((e) => e.dataset.id));
    for (const want of ["mx", "a", "spf", "dkim", "dmarc"]) assert(ids.includes(want), "the plan has the record " + want + ": " + ids);
    const state = await tid(page, "mailgw-dns-verdict").getAttribute("data-ready");
    eq(state, "false", "nothing is published for mesh.test");
    // a record can be copied
    assert((await recs.first().locator("button[aria-label*='Скопировать']").count()) >= 1, "records can be copied");
    // the queue is empty and the switch keeps its state
    await tid(page, "mailgw-queue-empty").waitFor();
    assert(await tid(page, "mailgw-test").isVisible(), "a test letter can be sent now");
    eq(page.problems, [], "console / network problems");
  });

  test("a letter from the Internet: verdict, formatted text in a frame that runs nothing, plain text, the picture of the letter inside", async ({ browser, dev }) => {
    const addr = (await dev.laptop.api("GET", "/api/mailgw")).status.listenAddr;
    assert(addr, "the gateway listens");
    await smtpDeliver(addr, { from: "friend@sender.invalid", to: "andrey@mesh.test", data: LETTER });
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    const item = page.locator('[data-testid="mail-item"][data-ext="in"]', { hasText: "Hello from the Internet" });
    await item.waitFor();
    assert(/Friend/.test(await item.innerText()), "the sender is named in the list");
    await item.click();
    const reader = tid(page, "mail-reader");
    await reader.waitFor();
    assert(/Friend/.test(await tid(page, "mail-from").innerText()), "the sender is named");
    assert(/friend@sender\.invalid/.test(await tid(page, "mail-from-addr").innerText()), "the address of the sender is shown beside the name");
    eq(await tid(page, "mail-verdict").getAttribute("data-verdict"), "unverified", "nobody vouches for sender.invalid");
    assert(/Принято на/.test(await tid(page, "mail-via").innerText()), "the gateway is named");
    // the formatted text is in a frame: no scripts, nothing from the Internet
    const frame = tid(page, "mail-html");
    await frame.waitFor();
    const sandbox = await frame.getAttribute("sandbox");
    assert(!/allow-scripts/.test(sandbox), "the frame may not run scripts: " + sandbox);
    const inner = page.frameLocator('[data-testid="mail-html"]');
    await inner.locator("h2").waitFor();
    assert(/Formatted hello/.test(await inner.locator("h2").innerText()), "the formatted text is shown");
    eq(await inner.locator("script").count(), 0, "no script in the page of the letter");
    eq(await page.evaluate(() => window.__pwned || 0), 0, "the script of the letter did not run");
    const src = await inner.locator("img").first().getAttribute("src");
    assert(/^data:image\/png;base64,/.test(src), "the picture that belongs to the letter is inside the page: " + String(src).slice(0, 30));
    const srcs = await inner.locator("img").evaluateAll((l) => l.map((e) => e.getAttribute("src") || ""));
    assert(!srcs.some((s) => /tracker\.invalid/.test(s)), "the picture of the Internet is not asked for: " + srcs.map((s) => s.slice(0, 30)));
    assert(/не загружен/.test(await tid(page, "mail-images-note").innerText()), "the person is told that pictures of the Internet were not loaded");
    // the page of the letter is served with a policy of its own
    const res = await dev.laptop.api("GET", `/api/mail/${await item.getAttribute("data-id")}/html`, undefined, { raw: true });
    const csp = res.headers.get("content-security-policy");
    assert(/default-src 'none'/.test(csp) && !/script-src/.test(csp), "policy: " + csp);
    // plain text on request
    await tid(page, "mail-view-plain").click();
    await page.getByText("Plain hello", { exact: false }).first().waitFor();
    eq(await tid(page, "mail-html").count(), 0, "the frame is gone in the plain view");
    // the link of the plain text opens in a new tab and does not hand the interface over
    const a = reader.locator("a", { hasText: "https://example.com/page" }).first();
    eq(await a.getAttribute("rel"), "noopener noreferrer", "links leave without a handle on the interface");
    eq(page.problems, [], "console / network problems");
  });

  test("answering a letter from the Internet: the answer goes to the sender from the mailbox the letter came to, and what became of it is shown", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    await page.locator('[data-testid="mail-item"][data-ext="in"]', { hasText: "Hello from the Internet" }).click();
    await tid(page, "mail-reply").click();
    await tid(page, "email-chip").waitFor();
    assert(/friend@sender\.invalid/.test(await tid(page, "email-chip").first().innerText()), "the sender is the recipient");
    await page.waitForFunction(() => /Re: Hello from the Internet/i.test(document.querySelector('[data-testid="compose-subject"]').value));
    assert(/andrey@mesh\.test/.test(await tid(page, "compose-from-hint").innerText()), "the answer goes out from the mailbox the letter came to");
    eq(await page.locator('[data-testid="device-chip"][aria-pressed="true"]').count(), 0, "no device is written to: the gateway is only the way");
    await tid(page, "compose-body").click();
    await page.keyboard.press("Control+Home");
    await page.keyboard.type("Thanks, got it.\n\n");
    await tid(page, "compose-send").click();
    await tid(page, "compose-send").waitFor({ state: "detached" });
    // the DNS does not know sender.invalid: the letter fails for good, and the reason is the server's own words
    await nav(page, dev.laptop, "mail/sent");
    await page.locator('[data-testid="mail-item"][data-ext="out"]', { hasText: "Hello from the Internet" }).first().click();
    const rcpt = page.locator('[data-testid="mail-recipient"][data-kind="email"]');
    await rcpt.waitFor();
    await page.locator('[data-testid="mail-recipient"][data-kind="email"][data-state="failed"]').waitFor({ timeout: 15000 });
    assert(/does not exist/.test(await tid(page, "mail-recipient-why").innerText()), "the reason is shown: " + (await tid(page, "mail-recipient-why").innerText()));
    // it is in the letters of the mesh as a letter that goes out, and the answer keeps the thread
    const sent = (await dev.laptop.api("GET", "/api/mail?folder=sent&q=Hello%20from%20the%20Internet")).items[0];
    eq(sent.ext.dir, "out", "a letter to the Internet");
    eq(sent.ext.from.addr, "andrey@mesh.test", "from the mailbox");
    eq(sent.ext.recipients.map((r) => r.addr), ["friend@sender.invalid"], "to the sender");
    eq(page.problems.filter((p) => !/HTTP 4\d\d/.test(p)), [], "console / network problems");
  });

  test("a letter to a domain that does not exist fails at once and leaves the queue empty", async ({ browser, dev }) => {
    const sent = await dev.laptop.api("POST", "/api/mail", { to: [], emailTo: ["x@no-mail-here.invalid"], subject: "Never delivered", body: "x" });
    await until(async () => (await dev.laptop.api("GET", "/api/mail/" + sent.id)).ext.recipients[0].state === "failed", 15000, "the letter to fail");
    const page = await open(browser, dev.laptop, { hash: "settings/mailgw" });
    await tid(page, "mailgw-queue").waitFor();
    // nothing waits: the failure is final, and the queue shows only what is still on its way (what waits there is tested in internal/api and internal/inetmail)
    await tid(page, "mailgw-queue-empty").waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("the gateway can be switched off again: nothing listens and the letters stay", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/mailgw" });
    await tid(page, "mailgw-enable").click();
    await tid(page, "mailgw-save").click();
    await page.getByText("Сохранено").first().waitFor();
    await until(async () => !(await dev.laptop.api("GET", "/api/mailgw")).status.listening, 10000, "the gateway to stop");
    await page.locator('[data-testid="mailgw-status"][data-state="off"]').waitFor();
    eq((await dev.laptop.api("GET", "/api/mailgw")).domain, "mesh.test", "the setup is kept");
    assert((await dev.laptop.api("GET", "/api/mail?folder=inbox&q=Hello%20from%20the%20Internet")).total >= 1, "the letters are still there");
    eq(page.problems, [], "console / network problems");
  });
});
