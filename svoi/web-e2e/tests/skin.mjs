import { group, test, assert, eq, open, nav, tid } from "../lib.mjs";

// What the Mac app says about itself in its user agent (desktop/src/window.js, windowLook).
const MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 TheMeshDesktop/0.1.0 (mac; skin=glass; vibrancy; inset)";
// ... and the phone app (android/…/MainActivity): the glass look from the first picture, and a window that paints the backdrop itself.
const ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 TheMeshAndroid/0.1.0 (android; skin=glass)";
const WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 TheMeshDesktop/0.1.0 (win)";
// ... and the phone app as it is now: the look of the weather app «Роса» (a living sky, glass over it)
const ROSA = ANDROID.replace("skin=glass", "skin=rosa");
// the window of the phone app as a stub that remembers what the page tells it
const rosaShell = () => {
  // (open() starts in the dark theme; in Роса that is a fixed mood, and these tests want the real sky: "auto")
  try { localStorage.setItem("themesh.theme", "auto"); } catch {}
  window.themeshShell = {
    calls: [],
    look(theme, skin) { this.calls.push(["look", theme, skin]); },
    sky(top, bottom, light) { this.calls.push(["sky", top, bottom, light]); },
    haptic(kind) { this.calls.push(["haptic", kind]); },
    menu() { this.calls.push(["menu"]); },
  };
};
const sky = (page) => page.evaluate(() => {
  const r = document.documentElement, cs = getComputedStyle(r);
  return {
    skin: r.dataset.skin, look: r.dataset.look || "", theme: r.dataset.theme, appearance: r.dataset.appearance || "", body: r.dataset.body || "", fx: r.dataset.fx,
    native: r.hasAttribute("data-native-backdrop"), zen: cs.getPropertyValue("--sky-zen").trim(), hor: cs.getPropertyValue("--sky-hor").trim(), ink: cs.getPropertyValue("--sky-ink").trim(),
    accent: cs.getPropertyValue("--sky-accent").trim(), stars: cs.getPropertyValue("--sky-stars").trim(), top: cs.getPropertyValue("--sky-top").trim(), bottom: cs.getPropertyValue("--sky-bottom").trim(),
    htmlBg: cs.backgroundColor, calls: window.themeshShell ? window.themeshShell.calls : null,
  };
});

// (with reduced motion every style change is a transition of 0.01 ms: the computed style is the new one only a moment later)
const sidebarBlur = (page, gone) => page.waitForFunction((g) => (getComputedStyle(document.querySelector(".sidebar")).backdropFilter === "none") === g, gone, { timeout: 5000 });

const look = (page) =>
  page.evaluate(() => {
    const r = document.documentElement;
    const side = document.querySelector(".sidebar");
    const cs = side && getComputedStyle(side);
    return { skin: r.dataset.skin, titlebar: r.dataset.titlebar || "", vibrancy: r.dataset.vibrancy || "", shell: r.dataset.shell || "", blur: cs ? cs.backdropFilter : "", padTop: cs ? parseFloat(cs.paddingTop) : 0 };
  });

group("skin", () => {
  test("a browser and the Windows app keep the classic look; the Mac app starts with glass", async ({ browser, dev }) => {
    let page = await open(browser, dev.laptop);
    let l = await look(page);
    eq([l.skin, l.titlebar, l.vibrancy, l.shell, l.blur], ["classic", "", "", "", "none"], "a plain browser");
    eq(page.problems, [], "console / network problems");

    page = await open(browser, dev.laptop, { userAgent: WIN });
    l = await look(page);
    eq([l.skin, l.titlebar, l.shell, l.blur], ["classic", "", "win", "none"], "the Windows app");

    page = await open(browser, dev.laptop, { userAgent: MAC });
    l = await look(page);
    eq([l.skin, l.titlebar, l.vibrancy, l.shell], ["glass", "inset", "on", "mac"], "the Mac app");
    assert(/blur/.test(l.blur), "the sidebar is a pane of glass: " + l.blur);
    assert(l.padTop >= 48, "the sidebar leaves room for the traffic lights: " + l.padTop);
    // the top bar and the strip above the navigation drag the window; what can be clicked does not
    eq(await page.evaluate(() => getComputedStyle(document.querySelector(".topbar")).webkitAppRegion), "drag", "the top bar drags the window");
    eq(await page.evaluate(() => getComputedStyle(document.querySelector(".topbar a, .topbar button")).webkitAppRegion), "no-drag", "its buttons are buttons");
    eq(await page.evaluate(() => getComputedStyle(document.querySelector(".nav__item")).webkitAppRegion), "no-drag", "navigation is clickable");
    eq(page.problems, [], "console / network problems");
  });

  test("the phone app starts with glass, tells its window the look of the page, and the window paints the backdrop", async ({ browser, dev }) => {
    // the window of the phone app gives the page `window.themeshShell` (look, menu); here it is a stub that remembers the calls
    const shell = () => {
      window.themeshShell = { calls: [], look(theme, skin) { this.calls.push(["look", theme, skin]); }, menu() { this.calls.push(["menu"]); } };
    };
    const page = await open(browser, dev.laptop, { userAgent: ANDROID, init: shell, mobile: true, w: 390, h: 844, theme: "dark" });
    const state = () => page.evaluate(() => ({ skin: document.documentElement.dataset.skin, shell: document.documentElement.dataset.shell, native: document.documentElement.hasAttribute("data-native-backdrop"),
      bg: getComputedStyle(document.documentElement).backgroundColor, bodyBg: getComputedStyle(document.body).backgroundColor, calls: window.themeshShell.calls }));
    let st = await state();
    eq([st.skin, st.shell, st.native], ["glass", "android", true], "the phone app: glass, with the backdrop painted by the window");
    eq([st.bg, st.bodyBg], ["rgba(0, 0, 0, 0)", "rgba(0, 0, 0, 0)"], "the page paints nothing behind its panes");
    assert(st.calls.some((c) => c[0] === "look" && c[1] === "dark" && c[2] === "glass"), "the page says what it looks like: " + JSON.stringify(st.calls));
    // a change of the theme or the skin is told to the window at once
    await nav(page, dev.laptop, "settings/interface");
    await tid(page, "skin-classic").click();
    await page.waitForFunction(() => window.themeshShell.calls.some((c) => c[0] === "look" && c[2] === "classic"));
    await tid(page, "skin-auto").click();
    await page.waitForFunction(() => window.themeshShell.calls.filter((c) => c[0] === "look" && c[2] === "glass").length >= 2);
    // the «⋮» button of the top bar asks the window for its menu
    await tid(page, "app-menu-button").click();
    st = await state();
    assert(st.calls.some((c) => c[0] === "menu"), "the menu button reaches the window: " + JSON.stringify(st.calls));
    eq(page.problems, [], "console / network problems");

    // the same page in a browser that pretends to be the phone app but has no window to ask: it paints its own backdrop
    const bare = await open(browser, dev.laptop, { userAgent: ANDROID, mobile: true, w: 390, h: 844 });
    st = await bare.evaluate(() => ({ native: document.documentElement.hasAttribute("data-native-backdrop"), skin: document.documentElement.dataset.skin }));
    eq([st.skin, st.native], ["glass", false], "without a window to paint for it the page keeps its own backdrop");
    eq(bare.problems, [], "console / network problems");
  });

  test("the phone app starts with Роса: a sky of its own, and its window is told the colours of the sky for the bars", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: ROSA, init: rosaShell, mobile: true, w: 390, h: 844, theme: "dark" });
    // ?sky= shows the sky at a height of the sun (here 25 degrees: a day), the way ?skin= shows a look: for screenshots and tests
    await page.goto(`${dev.laptop.origin}/?sky=25#/home`);
    await tid(page, "tab-home").waitFor();
    let st = await sky(page);
    eq([st.skin, st.look, st.native], ["glass", "rosa", false], "Роса is a layer over glass, and the page paints its own sky (the window does not)");
    eq([st.theme, st.zen, st.hor, st.body], ["dark", "#2c76d8", "#9bcbf6", "sun"], "a day: vivid blue, light type, the sun up");
    assert(/^#[0-9a-f]{6}$/.test(st.top) && st.bottom === "#9bcbf6", "the colours of the first and the last row of the sky: " + st.top + " " + st.bottom);
    assert(st.htmlBg !== "rgba(0, 0, 0, 0)", "the page is opaque: " + st.htmlBg);
    assert(st.calls.some((c) => c[0] === "look" && c[1] === "dark" && c[2] === "rosa"), "the window is told the look: " + JSON.stringify(st.calls));
    assert(st.calls.some((c) => c[0] === "sky" && c[1] === st.top && c[2] === st.bottom && c[3] === false), "and the colours of the sky for the bars: " + JSON.stringify(st.calls));
    // the fonts of the look are really there (the display face is fetched when the numeral of Home first needs it; waiting for it here
    // also means that nothing is still on its way when the next address is opened)
    const fonts = await page.evaluate(async () => {
      const display = await document.fonts.load('600 64px "Cormorant Garamond"');
      await document.fonts.ready;
      return { manrope: document.fonts.check('16px "Manrope"'), display: display.length, body: getComputedStyle(document.body).fontFamily.slice(0, 20) };
    });
    assert(fonts.manrope && /Manrope/.test(fonts.body), "the interface is set in Manrope: " + JSON.stringify(fonts));
    eq(fonts.display, 1, "the display face of the big numeral is there");
    // the manner of the sky: noon is the sun in the upper right, night is stars and a moon
    await page.goto(`${dev.laptop.origin}/?sky=-16#/home`);
    await tid(page, "tab-home").waitFor();
    st = await sky(page);
    eq([st.theme, st.body, st.stars], ["dark", "moon", "1"], "a night: the moon and all the stars");
    eq(page.problems, [], "console / network problems");

    // the same page in a browser that pretends to be the app but has no window: no calls to make, nothing breaks
    const bare = await open(browser, dev.laptop, { userAgent: ROSA, mobile: true, w: 390, h: 844 });
    st = await sky(bare);
    eq([st.skin, st.look, st.native], ["glass", "rosa", false], "no window to talk to");
    eq(bare.problems, [], "console / network problems");
  });

  test("the moods of the sky: «По небу», «Светлое», «Вечернее», «Тёмное» are chosen in Settings, and the type follows the sky", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: ROSA, init: rosaShell, mobile: true, w: 390, h: 844 });
    await page.goto(`${dev.laptop.origin}/?sky=25#/settings/interface`);
    await tid(page, "theme-auto").waitFor();
    eq(await page.getByRole("radiogroup", { name: /Небо/ }).count(), 1, "the sky picker is a radio group");
    for (const mood of ["auto", "light", "evening", "dark"]) assert(await tid(page, "theme-" + mood).isVisible(), `the picture of the mood "${mood}" is there`);
    eq(await tid(page, "theme-auto").getAttribute("aria-checked"), "true", "«По небу» is what it starts with");

    await tid(page, "theme-light").click();
    await page.waitForFunction(() => document.documentElement.dataset.appearance === "light");
    await page.waitForFunction(() => document.documentElement.dataset.theme === "light", null, { timeout: 5000 });
    let st = await sky(page);
    eq([st.theme, st.ink, st.accent, st.body, st.stars], ["light", "#1b2030", "#c46f1e", "none", "0"], "Светлое: a pale sky, dark type, a deeper amber, no sun and no stars of its own");
    eq(await page.evaluate(() => localStorage.getItem("themesh.theme")), "light", "the mood is remembered");
    assert(st.calls.some((c) => c[0] === "look" && c[1] === "light" && c[2] === "rosa"), "the window is told: " + JSON.stringify(st.calls.slice(-3)));

    await tid(page, "theme-evening").click();
    await page.waitForFunction(() => document.documentElement.dataset.appearance === "evening");
    st = await sky(page);
    eq([st.theme, st.zen, st.stars, st.body], ["dark", "#2b478d", "0.3", "none"], "Вечернее: the blue hour, the first stars");

    await tid(page, "theme-dark").click();
    await page.waitForFunction(() => document.documentElement.dataset.appearance === "dark");
    st = await sky(page);
    eq([st.theme, st.stars, st.ink], ["dark", "1", "#fffbf5"], "Тёмное: night, all the stars");

    await tid(page, "theme-auto").click();
    await page.waitForFunction(() => document.documentElement.dataset.appearance === "auto");
    st = await sky(page);
    eq([st.zen, st.body], ["#2c76d8", "sun"], "«По небу» is the real sky again (here: the day that ?sky=25 shows)");

    // leave Роса: the sky goes, the glass stays, and the moods are light and dark again
    await tid(page, "skin-glass").click();
    await page.waitForFunction(() => !document.documentElement.hasAttribute("data-look"));
    st = await sky(page);
    eq([st.skin, st.look, st.zen, st.body, st.appearance, st.native], ["glass", "", "", "", "", true], "no sky, and the window paints the backdrop again");
    assert(await tid(page, "theme-dark").isVisible() && !(await tid(page, "theme-evening").count()), "the moods of the sky are Роса's own");
    await tid(page, "skin-rosa").click();
    await page.waitForFunction(() => document.documentElement.dataset.look === "rosa");
    eq(page.problems, [], "console / network problems");
  });

  test("effects: full, calm and still; «Remove animations» of the system means still; the choice is remembered", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: ROSA, init: rosaShell, mobile: true, w: 390, h: 844 }); // (reduced motion, as in all these tests)
    await page.goto(`${dev.laptop.origin}/?sky=25#/settings/interface`);
    await tid(page, "fx-auto").waitFor();
    eq((await sky(page)).fx, "still", "the system asks for less motion: «Авто» is still");
    await tid(page, "fx-full").click();
    await page.waitForFunction(() => document.documentElement.dataset.fx === "full");
    await tid(page, "fx-calm").click();
    await page.waitForFunction(() => document.documentElement.dataset.fx === "calm");
    eq(await page.evaluate(() => localStorage.getItem("themesh.fx")), "calm", "remembered");
    await page.reload();
    await tid(page, "fx-calm").waitFor();
    eq((await sky(page)).fx, "calm", "it survives a reload");
    eq(await tid(page, "fx-calm").getAttribute("aria-checked"), "true");
    // the phone app has a vibrator: the switch for haptics is there (the stub window has `haptic`)
    await tid(page, "haptics").waitFor();
    await tid(page, "haptics").click();
    eq(await page.evaluate(() => localStorage.getItem("themesh.haptics")), "off", "haptics off");
    eq(page.problems, [], "console / network problems");
  });

  test("the tab bar of the phone carries a lens of glass that sits under the open tab and follows a finger across the tabs", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: ROSA, init: rosaShell, mobile: true, w: 390, h: 844 });
    await page.goto(`${dev.laptop.origin}/?sky=25#/home`);
    await tid(page, "tab-home").waitFor();
    const lens = () => page.evaluate(() => {
      const nav = document.querySelector(".tabbar"), on = nav.querySelector(".tabbar__item.is-active");
      return { x: parseFloat(nav.style.getPropertyValue("--lens-x")), w: parseFloat(nav.style.getPropertyValue("--lens-w")), left: on.offsetLeft, width: on.offsetWidth, tab: on.dataset.testid, shown: getComputedStyle(nav.querySelector(".tabbar__lens")).display };
    });
    await page.waitForFunction(() => document.querySelector(".tabbar").style.getPropertyValue("--lens-w") !== "");
    let l = await lens();
    eq([l.shown, l.x, l.w, l.tab], ["block", l.left, l.width, "tab-home"], "the lens is under the open tab");
    // a tap on another tab opens it, and the lens goes there
    await tid(page, "tab-files").click();
    await page.waitForFunction(() => document.querySelector(".tabbar__item.is-active").dataset.testid === "tab-files");
    await page.waitForFunction(() => { const n = document.querySelector(".tabbar"); return parseFloat(n.style.getPropertyValue("--lens-x")) === n.querySelector(".tabbar__item.is-active").offsetLeft; });
    l = await lens();
    eq([l.x, l.tab], [l.left, "tab-files"], "the lens followed the tap");
    // a finger put on the bar lifts the lens, drags it across the tabs (a tick at each), and the tab under it opens when the finger lets go
    const box = async (id) => (await tid(page, id).boundingBox());
    const a = await box("tab-files"), c = await box("tab-chat");
    await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2);
    await page.mouse.down();
    await page.waitForFunction(() => document.querySelector(".tabbar").hasAttribute("data-lifted"));
    for (let i = 1; i <= 6; i++) await page.mouse.move(a.x + a.width / 2 + ((c.x - a.x) * i) / 6, a.y + a.height / 2);
    await page.waitForFunction(() => document.querySelector('.tabbar__item[data-under]') && document.querySelector('.tabbar__item[data-under]').dataset.testid === "tab-chat");
    await page.mouse.up();
    await page.waitForFunction(() => location.hash.startsWith("#/chat"));
    const calls = (await sky(page)).calls.filter((x) => x[0] === "haptic").map((x) => x[1]);
    assert(calls[0] === "press" && calls.includes("tick") && calls[calls.length - 1] === "select", "haptics: a press, ticks across the tabs, a selection: " + JSON.stringify(calls));
    await page.waitForFunction(() => !document.querySelector(".tabbar").hasAttribute("data-lifted"));
    eq(page.problems, [], "console / network problems");
  });

  test("the style picked in Settings wins over what the window starts with, and is remembered", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: MAC, hash: "settings/interface" });
    eq((await look(page)).skin, "glass", "the Mac app starts with glass");
    await tid(page, "skin-classic").click();
    await page.waitForFunction(() => document.documentElement.dataset.skin === "classic");
    await sidebarBlur(page, true);
    eq((await look(page)).blur, "none", "classic means no glass");
    eq(await page.evaluate(() => localStorage.getItem("themesh.skin")), "classic", "the choice is stored");
    await page.reload();
    await tid(page, "skin-classic").waitFor();
    eq((await look(page)).skin, "classic", "it survives a reload");
    eq((await look(page)).titlebar, "inset", "the window is still a window without a title bar: its room for the traffic lights stays");
    assert((await look(page)).padTop >= 48, "also in the classic look");
    await tid(page, "skin-glass").click();
    await page.waitForFunction(() => document.documentElement.dataset.skin === "glass");
    await tid(page, "skin-auto").click();
    await page.waitForFunction(() => document.documentElement.dataset.skin === "glass"); // "auto" in the Mac app is glass
    eq(await page.evaluate(() => localStorage.getItem("themesh.skin")), "auto");

    // and in a browser the same switch works the other way round
    const b = await open(browser, dev.laptop, { hash: "settings/interface" });
    eq((await look(b)).skin, "classic", "a browser starts with classic");
    await tid(b, "skin-glass").click();
    await b.waitForFunction(() => document.documentElement.dataset.skin === "glass");
    await sidebarBlur(b, false);
    assert(/blur/.test((await look(b)).blur), "glass in a browser too");
    await b.reload();
    await tid(b, "skin-glass").waitFor();
    eq((await look(b)).skin, "glass", "remembered");
    eq(page.problems, [], "console / network problems");
    eq(b.problems, [], "console / network problems");
  });

  test("?skin= in the address shows a look for the moment and is not remembered (screenshots, tests)", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop);
    await page.goto(`${dev.laptop.origin}/?skin=glass#/home`);
    await page.waitForFunction(() => document.documentElement.dataset.skin === "glass");
    await tid(page, "nav-home").waitFor();
    eq(await page.evaluate(() => document.documentElement.dataset.skin), "glass", "still glass after the application started");
    eq(await page.evaluate(() => localStorage.getItem("themesh.skin")), null, "nothing was stored");
    await page.goto(`${dev.laptop.origin}/#/home`);
    await page.reload();
    await tid(page, "nav-home").waitFor();
    eq(await page.evaluate(() => document.documentElement.dataset.skin), "classic", "the next visit is classic again");
  });

  test("glass leaves no sideways scrolling on the main screens at desktop, tablet and phone widths, in both themes", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: MAC });
    const routes = ["home", "devices", "files/send", "files/browse", "mail/inbox", "chat", "services", "settings", "more"];
    const bad = [];
    for (const theme of ["light", "dark"]) {
      await page.evaluate((th) => localStorage.setItem("themesh.theme", th), theme);
      for (const [w, h] of [[1280, 800], [900, 700], [480, 820]]) {
        await page.setViewportSize({ width: w, height: h });
        for (const r of routes) {
          await nav(page, dev.laptop, r);
          await page.reload();
          await page.waitForFunction(() => [...document.querySelectorAll('[data-testid^="page-"]')].length > 0, null, { timeout: 8000 }).catch(() => {});
          await page.waitForTimeout(150);
          const m = await page.evaluate(() => ({ th: document.documentElement.dataset.theme, over: document.documentElement.scrollWidth - window.innerWidth, w: window.innerWidth }));
          if (m.th !== theme) bad.push(`${r} ${w}px: theme ${m.th}, wanted ${theme}`);
          if (m.over > 1) bad.push(`${r} ${theme} ${w}px: ${m.over}px of sideways scroll`);
        }
      }
    }
    eq(bad, [], "pages that overflow");
  });

  test("a QR code stays a sharp, opaque picture under glass: no tint, no blur, no transparency", async ({ browser, dev }) => {
    for (const theme of ["light", "dark"]) {
      const page = await open(browser, dev.laptop, { userAgent: MAC, theme, hash: "home" });
      await tid(page, "home-action-add").click();
      await tid(page, "invite-create").click();
      const qr = tid(page, "invite-qr");
      await qr.locator("img").waitFor();
      await page.waitForTimeout(400);
      // nothing from the glass reaches the picture: it and its frame are opaque and unfiltered
      const chain = await page.evaluate(() => {
        const out = [];
        for (let el = document.querySelector('[data-testid="invite-qr"] img'); el && el !== document.body; el = el.parentElement) {
          const cs = getComputedStyle(el);
          out.push({ tag: el.tagName + "." + el.className, op: cs.opacity, bf: cs.backdropFilter, filter: cs.filter, bg: cs.backgroundColor, mix: cs.mixBlendMode });
          if (el.getAttribute("data-testid") === "invite-qr") break;
        }
        return out;
      });
      for (const c of chain) {
        assert(c.op === "1" && c.filter === "none" && c.mix === "normal", `${theme}: ${c.tag} would change the picture: ${JSON.stringify(c)}`);
      }
      const frame = chain.find((c) => /^rgb\(/.test(c.bg));
      assert(frame, `${theme}: the picture has a solid frame behind it: ${JSON.stringify(chain)}`);
      // and what is on screen: the quiet zone around the modules is light everywhere, the modules are dark
      const png = await qr.screenshot();
      const probe = await browser.newPage();
      const stats = await probe.evaluate(async (b64) => {
        const img = new Image();
        img.src = "data:image/png;base64," + b64;
        await img.decode();
        const c = document.createElement("canvas");
        c.width = img.width;
        c.height = img.height;
        const g = c.getContext("2d");
        g.drawImage(img, 0, 0);
        const lum = (x, y) => { const d = g.getImageData(x, y, 1, 1).data; return 0.2126 * d[0] + 0.7152 * d[1] + 0.0722 * d[2]; };
        const m = Math.round(img.width * 0.04);
        const lo = Math.round(img.width * 0.15), hi = Math.round(img.width * 0.85); // (the frame has rounded corners)
        let darkest = 255, lightestEdge = 255;
        for (let i = 0; i < img.width; i += 3) for (let j = 0; j < img.height; j += 3) darkest = Math.min(darkest, lum(i, j));
        for (let i = lo; i < hi; i += 3) lightestEdge = Math.min(lightestEdge, lum(i, m), lum(i, img.height - 1 - m));
        for (let j = lo; j < hi; j += 3) lightestEdge = Math.min(lightestEdge, lum(m, j), lum(img.width - 1 - m, j));
        return { darkest, edgeMin: lightestEdge, w: img.width, h: img.height };
      }, png.toString("base64"));
      await probe.close();
      assert(stats.darkest < 40, `${theme}: the modules are black (darkest ${stats.darkest})`);
      assert(stats.edgeMin > 225, `${theme}: the quiet zone is light all around (darkest edge pixel ${stats.edgeMin})`);
      eq(page.problems, [], "console / network problems");
    }
  });

  test("a Mac that says \"Reduce transparency\" gets solid panels from the very first picture", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: MAC.replace("vibrancy", "reduced-transparency") });
    const l = await page.evaluate(() => ({ skin: document.documentElement.dataset.skin, solid: document.documentElement.hasAttribute("data-reduce-transparency"), vibrancy: document.documentElement.dataset.vibrancy || "" }));
    eq(l, { skin: "glass", solid: true, vibrancy: "" }, "what the page took from the user agent");
    await sidebarBlur(page, true);
    eq((await look(page)).blur, "none", "no blur");
  });

  test("with reduced transparency the glass turns solid", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { userAgent: MAC });
    const before = await page.evaluate(() => { const cs = getComputedStyle(document.querySelector(".sidebar")); return { bf: cs.backdropFilter, bg: cs.backgroundColor }; });
    assert(/blur/.test(before.bf), "glass to begin with: " + before.bf);
    // what the Mac app does when macOS "Reduce transparency" is on
    await page.evaluate(() => { document.documentElement.setAttribute("data-reduce-transparency", ""); document.documentElement.removeAttribute("data-vibrancy"); });
    await sidebarBlur(page, true);
    await page.waitForFunction(() => { const m = /rgba?\(([^)]+)\)/.exec(getComputedStyle(document.querySelector(".sidebar")).backgroundColor); const p = m[1].split(",").map(Number); return (p.length === 4 ? p[3] : 1) >= 0.85; }, null, { timeout: 5000 });
    const after = await page.evaluate(() => { const r = document.documentElement; const cs = getComputedStyle(document.querySelector(".sidebar")); return { bf: cs.backdropFilter, bg: cs.backgroundColor, token: getComputedStyle(r).getPropertyValue("--glass-filter"), attrs: r.getAttributeNames().join(" ") }; });
    eq(after.bf, "none", "no blur (" + JSON.stringify(after) + ")");
    const alpha = (c) => { const m = /rgba?\(([^)]+)\)/.exec(c); const p = m[1].split(",").map(Number); return p.length === 4 ? p[3] : 1; };
    assert(alpha(after.bg) >= 0.85, "the panel is nearly opaque now: " + after.bg);
  });
});
