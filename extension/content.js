/* ============================================================================
 *  Циан → Excel — content script (world: MAIN).
 *  На странице ЖК показывает кнопку «Выгрузить в Excel». По клику собирает все
 *  активные лоты через API из самой вкладки (с вашей сессией -> проходит антибот)
 *  и скачивает .xls (Сводка + по комнатности + Все_лоты).
 * ========================================================================== */
(() => {
  "use strict";
  if (window.__cianExcelMounted) return;
  window.__cianExcelMounted = true;

  const CONFIG = { region: 1, delayMin: 1100, delayMax: 2400, maxPages: 28, pageSize: 28 };
  const API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/";
  const ROOMS = [9, 7, 1, 2, 3, 4, 5, 6];

  // ---------- определить ID и имя ЖК ----------
  function detectJk() {
    const cands = [location.href];
    const canon = document.querySelector('link[rel="canonical"]');
    if (canon && canon.href) cands.push(canon.href);
    const og = document.querySelector('meta[property="og:url"]');
    if (og && og.content) cands.push(og.content);
    let id = null;
    for (const href of cands) {
      id =
        (href.match(/-(\d+)\/(?:\?|#|$)/) || [])[1] ||
        (href.match(/newobject(?:%5B0%5D|\[0\])?=(\d+)/) || [])[1] ||
        ((/zhiloy-kompleks|kupit-|newobject/.test(href) && (href.match(/(\d{6,})/) || [])[1]) || null);
      if (id) break;
    }
    if (!id) id = scanForId();
    let name = ((document.querySelector("h1") || {}).textContent || "").trim() ||
      (document.title.split(/[—|·|]/)[0] || "").trim() || (id ? "ЖК " + id : "");
    return { id: id ? parseInt(id, 10) : null, name: name.replace(/\s+/g, " ").slice(0, 60) };
  }

  // Настойчивый поиск ID ЖК на странице (нужно для промо-сайтов застройщика
  // zhk-*.cian.ru, где ID нет в адресе): ссылки на основной листинг + данные.
  function scanForId() {
    const RX = [
      /zhiloy-kompleks-[a-z0-9-]*?-(\d{5,})(?:\/|\?|#|$)/i,
      /newobject(?:%5B0%5D|\[0\])?=(\d+)/i,
      /\/(?:kupit|snyat)[^"'\s]*?-(\d{6,})\//i,
    ];
    const tryAll = (s) => { for (const rx of RX) { const m = s && s.match(rx); if (m) return m[1]; } return null; };
    // 1) все ссылки на странице (часто есть «Смотреть на Циан»/«Квартиры»)
    for (const a of document.querySelectorAll('a[href]')) {
      const id = tryAll(a.href || a.getAttribute("href"));
      if (id) return id;
    }
    // 2) встроенные данные Next.js
    try { const id = tryAll(JSON.stringify(window.__NEXT_DATA__ || {})) ||
      (JSON.stringify(window.__NEXT_DATA__ || {}).match(/"(?:newobjectId|jkId)"\s*:\s*"?(\d{5,})/) || [])[1];
      if (id) return id; } catch (e) { /* ignore */ }
    // 3) сырой HTML (ссылки/скрипты), в т.ч. URL-энкод
    try {
      const html = document.documentElement.innerHTML;
      const id = tryAll(html) ||
        (html.match(/"newobjectId"\s*:\s*(\d+)/) ||
         html.match(/newobject%5B0%5D=(\d+)/) ||
         html.match(/cian\.ru[^"'\s]*?-(\d{6,})\//i) || [])[1];
      if (id) return id;
    } catch (e) { /* ignore */ }
    return null;
  }

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const pause = () => sleep(CONFIG.delayMin + Math.random() * (CONFIG.delayMax - CONFIG.delayMin));
  const dig = (o, p) => p.split(".").reduce((a, k) => (a == null ? a : a[k]), o);

  async function fetchPage(jkid, room, page) {
    const q = {
      _type: "flatsale", engine_version: { type: "term", value: 2 },
      region: { type: "terms", value: [CONFIG.region] },
      newobject: { type: "terms", value: [jkid] },
      page: { type: "term", value: page },
      sort: { type: "term", value: "creation_date_desc" },
    };
    if (room != null) q.room = { type: "terms", value: [room] };
    const r = await fetch(API, {
      method: "POST", headers: { "Content-Type": "application/json", Accept: "*/*" },
      body: JSON.stringify({ jsonQuery: q }), credentials: "include",
    });
    if (!r.ok) throw new Error("HTTP " + r.status);
    const d = await r.json();
    const data = d.data || d;
    let offers = data.offersSerialized || data.offers || data.items || [];
    offers = offers.map((it) => (it && it.offer ? it.offer : it));
    const total = data.offerCount || data.offersCount || data.totalCount || offers.length;
    return { offers, total: parseInt(total, 10) || offers.length };
  }

  async function collectAll(jkid, onProgress) {
    const byId = new Map(), totalsByRoom = {};
    let totalInJk = 0;
    const add = (offers) => offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) byId.set(id, o); });
    for (let page = 1; page <= CONFIG.maxPages; page++) {
      let res; try { res = await fetchPage(jkid, null, page); } catch (e) { if (page === 1) throw e; break; }
      if (page === 1) totalInJk = res.total;
      if (!res.offers.length) break;
      add(res.offers);
      onProgress(`Собрано ${byId.size}${totalInJk ? "/" + totalInJk : ""}…`);
      if (res.offers.length < CONFIG.pageSize) break;
      await pause();
    }
    for (const room of ROOMS) {
      let first; try { first = await fetchPage(jkid, room, 1); } catch (e) { continue; }
      totalsByRoom[room] = first.total; add(first.offers);
      if (first.total > CONFIG.pageSize) {
        for (let page = 2; page <= CONFIG.maxPages; page++) {
          await pause();
          let res; try { res = await fetchPage(jkid, room, page); } catch (e) { break; }
          if (!res.offers.length) break;
          add(res.offers);
          onProgress(`Собрано ${byId.size}${totalInJk ? "/" + totalInJk : ""}…`);
          if (res.offers.length < CONFIG.pageSize) break;
        }
      }
      await pause();
    }
    return { offers: [...byId.values()], totalsByRoom, totalInJk };
  }

  // ---------- нормализация (как в Python/консольной версии) ----------
  const DEC = { without: "Без отделки", rough: "Черновая", fine: "Чистовая", preFine: "Предчистовая", prefine: "Предчистовая", designer: "Дизайнерская", clean: "Чистовая" };
  function categoryOf(o) {
    if (o.isStudio || o.flatType === "studio") return "Студия";
    if (["openPlan", "openplan", "freePlan"].includes(o.flatType)) return "Своб. планировка";
    let rc = o.roomsCount; if (rc == null) rc = o.roomsForSaleCount; if (rc == null) return null;
    rc = parseInt(rc, 10); if (isNaN(rc)) return null;
    if (rc === 0) return "Студия"; if (rc >= 4) return "4+"; return String(rc);
  }
  const priceOf = (o) => { const p = dig(o, "bargainTerms.priceRur") || dig(o, "bargainTerms.price") || dig(o, "bargainTerms.prices.rur") || o.price; const n = parseFloat(p); return isNaN(n) ? null : n; };
  const areaOf = (o) => { if (o.totalArea == null) return null; const n = parseFloat(String(o.totalArea).replace(",", ".")); return isNaN(n) ? null : n; };
  const buildingOf = (o) => dig(o, "newbuilding.house.name") || dig(o, "newbuilding.name") || dig(o, "building.name") || dig(o, "geo.jk.house.name") || null;
  function sellerType(o) {
    const ut = dig(o, "user.userType");
    if (o.isFromBuilder || o.fromDeveloper || dig(o, "newbuilding.isFromBuilder") || dig(o, "newbuilding.isFromDeveloper") || ["developer", "builder"].includes(ut)) return "Застройщик";
    if (o.isByHomeowner || ["homeowner", "owner"].includes(ut)) return "Собственник";
    if (["agency", "realtor", "agent", "managementCompany"].includes(ut)) return "Агентство";
    if (dig(o, "user.agencyName") || dig(o, "user.companyName")) return "Агентство";
    return null;
  }
  const sellerName = (o) => dig(o, "user.agencyName") || dig(o, "user.companyName") || dig(o, "user.title") || dig(o, "user.name") || null;
  function decorationOf(o) { let d = o.decoration || o.repairType; if (d && typeof d === "object") d = d.type || d.value; return d ? (DEC[d] || String(d)) : null; }
  function pubDate(o) { const ts = o.addedTimestamp || o.creationTimestamp; if (ts) { const d = new Date(ts * 1000); if (!isNaN(d)) return d; } if (o.creationDate) { const d = new Date(o.creationDate); if (!isNaN(d)) return d; } return null; }
  const updDate = (o) => { const s = o.editDate || o.updatedAt; if (!s) return null; const d = new Date(s); return isNaN(d) ? null : d; };
  const fmtDate = (d) => d ? `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}.${d.getFullYear()}` : "";
  const offerUrl = (o) => o.fullUrl || ((o.cianId || o.id) ? `https://www.cian.ru/sale/flat/${o.cianId || o.id}/` : null);
  function normalize(o) {
    const area = areaOf(o), price = priceOf(o), pub = pubDate(o);
    return {
      cianId: o.cianId || o.id || null, url: offerUrl(o), category: categoryOf(o),
      area, floor: o.floorNumber != null ? o.floorNumber : null,
      floors: dig(o, "building.floorsCount") || o.floorsCount || null, building: buildingOf(o),
      seller_type: sellerType(o), seller_name: sellerName(o), decoration: decorationOf(o),
      price, ppm: price && area ? Math.round(price / area) : null,
      published: pub ? fmtDate(pub) : "", exposure: pub ? Math.floor((Date.now() - pub.getTime()) / 86400000) : "",
      updated: updDate(o) ? fmtDate(updDate(o)) : "",
    };
  }

  // ---------- генерация Excel (SpreadsheetML 2003) ----------
  const esc = (s) => String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  function cell(c) {
    if (c == null || c.v == null || c.v === "") return c && c.s ? `<Cell ss:StyleID="${c.s}"/>` : "<Cell/>";
    const a = []; if (c.s) a.push(`ss:StyleID="${c.s}"`); if (c.href) a.push(`ss:HRef="${esc(c.href)}"`); if (c.merge) a.push(`ss:MergeAcross="${c.merge}"`);
    return `<Cell ${a.join(" ")}><Data ss:Type="${c.t || "String"}">${esc(c.v)}</Data></Cell>`;
  }
  const rowXml = (cells) => "<Row>" + cells.map(cell).join("") + "</Row>";
  function worksheet(name, cols, rowsXml, freeze) {
    const colsXml = cols.map((w) => `<Column ss:Width="${w}"/>`).join("");
    const opt = freeze ? `<WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel"><FreezePanes/><FrozenNoSplit/><SplitHorizontal>4</SplitHorizontal><TopRowBottomPane>4</TopRowBottomPane><ActivePane>2</ActivePane></WorksheetOptions>` : "";
    return `<Worksheet ss:Name="${esc(name)}"><Table>${colsXml}${rowsXml}</Table>${opt}</Worksheet>`;
  }
  const HEADERS = ["№", "ID объявления", "Категория", "Площадь, м²", "Этаж", "Этаж-ность", "Корпус / секция", "Тип продавца", "Продавец", "Отделка", "Цена, ₽", "Цена за м², ₽", "Дата публикации", "Срок эксп., дн", "Дата обновления", "Ссылка"];
  const COLW = [34, 90, 80, 74, 44, 60, 110, 90, 150, 100, 105, 95, 95, 80, 95, 70];
  function dataSheet(name, title, sub, rows) {
    let xml = rowXml([{ v: title, s: "title", merge: HEADERS.length - 1 }]) + rowXml([{ v: sub, s: "sub", merge: HEADERS.length - 1 }]) + rowXml([{}]) + rowXml(HEADERS.map((h) => ({ v: h, s: "hdr" })));
    rows.forEach((r, i) => {
      xml += rowXml([
        { v: i + 1, t: "Number" }, { v: r.cianId, t: r.cianId ? "Number" : "String" }, { v: r.category },
        { v: r.area, t: r.area != null ? "Number" : "String", s: "area" }, { v: r.floor, t: r.floor != null ? "Number" : "String" },
        { v: r.floors, t: r.floors != null ? "Number" : "String" }, { v: r.building }, { v: r.seller_type }, { v: r.seller_name }, { v: r.decoration },
        { v: r.price, t: r.price != null ? "Number" : "String", s: "num" }, { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: "num" },
        { v: r.published }, { v: r.exposure, t: r.exposure !== "" ? "Number" : "String" }, { v: r.updated },
        r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]);
    });
    return worksheet(name, COLW, xml, true);
  }
  const CATS = ["Студия", "Своб. планировка", "1", "2", "3", "4+"];
  const ROOM_OF_CAT = { "Студия": [9], "Своб. планировка": [7], "1": [1], "2": [2], "3": [3], "4+": [4, 5, 6] };
  const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
  const num = (v) => (v == null ? { v: "—" } : { v, t: "Number", s: "num" });
  function summarySheet(jk, rows, totalsByRoom, totalInJk) {
    const present = CATS.filter((c) => rows.some((r) => r.category === c)), today = fmtDate(new Date());
    let xml = rowXml([{ v: `ЖК ${jk.name} (ID ${jk.id}) — сводка`, s: "title", merge: 5 }]) +
      rowXml([{ v: `Данные Циан на ${today}. Собрано ${rows.length} лотов. «Частник» = собственник/агентство.`, s: "sub", merge: 5 }]) + rowXml([{}]);
    xml += rowXml([{ v: "ОХВАТ ВЫГРУЗКИ", s: "bold" }]) + rowXml(["Категория", "Собрано", "Всего на Циан", "% выдачи"].map((h) => ({ v: h, s: "hdr" })));
    let sumC = 0, sumT = 0;
    present.forEach((c) => {
      const got = rows.filter((r) => r.category === c).length, tot = ROOM_OF_CAT[c].reduce((s, rm) => s + (totalsByRoom[rm] || 0), 0) || null;
      sumC += got; if (tot) sumT += tot;
      xml += rowXml([{ v: c }, { v: got, t: "Number" }, num(tot), { v: tot ? Math.round((got / tot) * 100) + "%" : "—" }]);
    });
    xml += rowXml([{ v: "ИТОГО (категории)", s: "bold" }, { v: sumC, t: "Number", s: "bold" }, num(sumT || null), { v: sumT ? Math.round((sumC / sumT) * 100) + "%" : "—" }]);
    if (totalInJk) xml += rowXml([{ v: "Всего квартир в ЖК (Циан)", s: "bold" }, { v: rows.length, t: "Number" }, { v: totalInJk, t: "Number" }, { v: Math.round((rows.length / totalInJk) * 100) + "%" }]);
    xml += rowXml([{}]) + rowXml([{ v: "СРЕДНЯЯ ЦЕНА ЗА м², ₽", s: "bold" }]) + rowXml(["Категория", "Частник", "Застройщик", "Все"].map((h) => ({ v: h, s: "hdr" })));
    const ppmBy = (s) => s.map((r) => r.ppm).filter((x) => x != null);
    present.concat(["ИТОГО по ЖК"]).forEach((c) => {
      const sub = c === "ИТОГО по ЖК" ? rows : rows.filter((r) => r.category === c);
      xml += rowXml([{ v: c, s: c === "ИТОГО по ЖК" ? "bold" : "" }, num(avg(ppmBy(sub.filter((r) => r.seller_type !== "Застройщик")))), num(avg(ppmBy(sub.filter((r) => r.seller_type === "Застройщик")))), num(avg(ppmBy(sub)))]);
    });
    xml += rowXml([{}]) + rowXml([{ v: "ДИАПАЗОН ЦЕН, ₽", s: "bold" }]) + rowXml(["Категория", "Мин. цена", "Средн. цена", "Макс. цена", "Мин. ₽/м²", "Макс. ₽/м²"].map((h) => ({ v: h, s: "hdr" })));
    present.forEach((c) => {
      const sub = rows.filter((r) => r.category === c), pr = sub.map((r) => r.price).filter((x) => x != null), pm = sub.map((r) => r.ppm).filter((x) => x != null);
      xml += rowXml([{ v: c }, num(pr.length ? Math.min(...pr) : null), num(avg(pr)), num(pr.length ? Math.max(...pr) : null), num(pm.length ? Math.min(...pm) : null), num(pm.length ? Math.max(...pm) : null)]);
    });
    return worksheet("Сводка", [156, 96, 96, 96, 84, 84], xml, false);
  }
  function buildWorkbook(jk, rows, totalsByRoom, totalInJk) {
    rows = rows.slice().sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
    const today = fmtDate(new Date()), sheets = [summarySheet(jk, rows, totalsByRoom, totalInJk)];
    sheets.push(dataSheet("Все_лоты", `ЖК ${jk.name} — все лоты`, `Источник: Циан (ID ${jk.id}), ${today}. Сортировка по ₽/м². Срок экспозиции — дни с последней подачи.`, rows));
    const sn = { "Студия": "Студия", "Своб. планировка": "Своб_планировка", "1": "1-комн", "2": "2-комн", "3": "3-комн", "4+": "4-комн" };
    CATS.forEach((c) => { const sub = rows.filter((r) => r.category === c); if (sub.length) sheets.push(dataSheet(sn[c], `ЖК ${jk.name} — ${c}`, `Собрано ${sub.length}. Сортировка по ₽/м².`, sub)); });
    const styles = `<Styles><Style ss:ID="Default" ss:Name="Normal"><Alignment ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="11"/></Style><Style ss:ID="hdr"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#1F2A44" ss:Pattern="Solid"/><Alignment ss:Horizontal="Center" ss:Vertical="Center" ss:WrapText="1"/></Style><Style ss:ID="title"><Font ss:Bold="1" ss:Size="13"/></Style><Style ss:ID="sub"><Font ss:Italic="1" ss:Color="#555555" ss:Size="9"/></Style><Style ss:ID="bold"><Font ss:Bold="1"/></Style><Style ss:ID="num"><NumberFormat ss:Format="#,##0"/></Style><Style ss:ID="area"><NumberFormat ss:Format="0.0"/></Style><Style ss:ID="link"><Font ss:Color="#1155CC" ss:Underline="Single"/></Style></Styles>`;
    return `<?xml version="1.0" encoding="UTF-8"?>\n<?mso-application progid="Excel.Sheet"?>\n<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" xmlns:html="http://www.w3.org/TR/REC-html40">${styles}${sheets.join("")}</Workbook>`;
  }
  const slug = (s) => s.toLowerCase().replace(/\s+/g, "-").replace(/[^0-9a-zа-яё_\-]/g, "") || "jk";
  function download(xml, name) {
    const blob = new Blob(["﻿", xml], { type: "application/vnd.ms-excel;charset=utf-8" });
    const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1500);
  }

  // ---------- кнопка на странице ----------
  function setStatus(btn, text, busy) { btn.textContent = text; btn.style.opacity = busy ? "0.7" : "1"; btn.style.pointerEvents = busy ? "none" : "auto"; }

  const onMainSite = () => /(^|\.)cian\.ru$/.test(location.hostname) && location.hostname.startsWith("www.");

  async function run(btn) {
    const jk = detectJk();
    console.log("[cian-excel] определён ЖК:", jk);
    if (!jk.id) {
      const hint = onMainSite()
        ? "Введите ID ЖК (число из адреса страницы Циан):"
        : "Это промо-сайт застройщика — ID ЖК тут не в адресе.\nЛучше открыть основную страницу ЖК на www.cian.ru (раздел «Квартиры») и нажать кнопку там.\n\nИли введите ID ЖК вручную (число из ссылки www.cian.ru/...-XXXXXXX/):";
      jk.id = parseInt(prompt(hint), 10);
      if (!jk.id) { setStatus(btn, "📊 Выгрузить в Excel", false); return; }
      jk.name = jk.name || ("ЖК " + jk.id);
    }
    setStatus(btn, "Собираю…", true);
    try {
      const { offers, totalsByRoom, totalInJk } = await collectAll(jk.id, (t) => setStatus(btn, t, true));
      if (!offers.length) { setStatus(btn, "📊 Выгрузить в Excel", false); alert("Не собрано ни одного лота. Войдите в аккаунт, пройдите капчу и попробуйте снова. Если ЖК не в Москве — поменяйте region в расширении."); return; }
      const rows = offers.map(normalize);
      download(buildWorkbook(jk, rows, totalsByRoom, totalInJk), `cian_${slug(jk.name)}_${new Date().toISOString().slice(0, 10)}.xls`);
      setStatus(btn, `✓ Готово: ${rows.length} лотов`, false);
      setTimeout(() => setStatus(btn, "📊 Выгрузить в Excel", false), 5000);
    } catch (e) {
      console.error(e); setStatus(btn, "📊 Выгрузить в Excel", false);
      const extra = onMainSite() ? ""
        : "\n\nВы на промо-сайте застройщика (" + location.hostname + "). Откройте основную страницу ЖК на www.cian.ru (раздел «Квартиры») — там выгрузка работает.";
      alert("Ошибка: " + e.message + "\nОбновите страницу, войдите в аккаунт, пройдите капчу и попробуйте снова." + extra);
    }
  }

  function mount() {
    if (!document.body) return;
    if (document.getElementById("cian-excel-btn")) return;
    // Кнопку показываем на всех страницах cian.ru — ЖК определяем при клике.
    const btn = document.createElement("button");
    btn.id = "cian-excel-btn";
    btn.textContent = "📊 Выгрузить в Excel";
    Object.assign(btn.style, {
      position: "fixed", right: "20px", bottom: "20px", zIndex: 2147483647,
      padding: "12px 18px", background: "#1F2A44", color: "#fff", border: "none",
      borderRadius: "10px", fontSize: "14px", fontWeight: "600", cursor: "pointer",
      boxShadow: "0 4px 14px rgba(0,0,0,.25)", fontFamily: "Arial, sans-serif",
    });
    btn.addEventListener("click", () => run(btn));
    document.body.appendChild(btn);
    console.log("[cian-excel] кнопка добавлена");
  }

  function ensure() { try { mount(); } catch (e) { console.warn("[cian-excel] mount:", e); } }

  console.log("[cian-excel] загружен на", location.href);
  ensure();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", ensure);
  // ретраи (страница Циан — SPA, рендерится не сразу)
  let tries = 0;
  const iv = setInterval(() => { ensure(); if (++tries > 20) clearInterval(iv); }, 1000);
  // на SPA-переходах Циан перемонтируем кнопку
  let last = location.href;
  setInterval(() => { if (location.href !== last) { last = location.href; setTimeout(ensure, 800); } }, 1500);
})();
