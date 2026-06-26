/* ============================================================================
 *  cian_browser.js — сбор отчёта по ЖК ПРЯМО В БРАУЗЕРЕ (без терминала/Python).
 *
 *  КАК ПОЛЬЗОВАТЬСЯ:
 *   1. Откройте в браузере страницу нужного ЖК на cian.ru (желательно войдите
 *      в аккаунт и пройдите капчу, если показана).
 *   2. Нажмите F12 -> вкладка Console (Консоль).
 *   3. Вставьте ВЕСЬ этот файл и нажмите Enter.
 *   4. Дождитесь окончания — браузер сам скачает файл
 *      cian_<жк>_<дата>.xls (открывается в Excel: листы, формулы, ссылки).
 *
 *  Работает, потому что запросы к API идут из вашей же вкладки cian.ru с вашей
 *  сессией — антибот не отличает их от обычного просмотра сайта.
 *
 *  Если ЖК не в Москве — поменяйте CONFIG.region (Москва = 1, МО = 4593, СПб = 2).
 * ========================================================================== */
(async () => {
  "use strict";

  const CONFIG = {
    region: 1,            // регион Циан: Москва=1, МО=4593, СПб=2
    delayMin: 1200,       // пауза между запросами, мс (вежливость к серверу)
    delayMax: 2600,
    maxPages: 28,         // потолок страниц на запрос (лимит Циан)
    pageSize: 28,
    jkName: "",           // оставьте пустым — возьмётся из страницы
  };

  const API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/";
  const ROOMS = [9, 7, 1, 2, 3, 4, 5, 6]; // студия=9, своб.=7, 1..6 комнат

  // ---- ID ЖК и имя из URL/страницы ----------------------------------------
  let JKID =
    (location.href.match(/-(\d+)\/(?:\?|$)/) || [])[1] ||
    (location.href.match(/newobject(?:%5B0%5D|\[0\])?=(\d+)/) || [])[1] ||
    (location.href.match(/(\d{6,})/) || [])[1];
  if (!JKID) JKID = prompt("Не нашёл ID ЖК в ссылке. Введите его (число из URL Циан):");
  JKID = parseInt(JKID, 10);
  if (!JKID) { alert("Не задан ID ЖК — отмена."); return; }

  let JKNAME = CONFIG.jkName ||
    ((document.querySelector("h1") || {}).textContent || "").trim() ||
    (document.title.split(/[—|·]/)[0] || "").trim() ||
    ("ЖК " + JKID);
  JKNAME = JKNAME.replace(/\s+/g, " ").slice(0, 60);

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const pause = () => sleep(CONFIG.delayMin + Math.random() * (CONFIG.delayMax - CONFIG.delayMin));
  const log = (...a) => console.log("%c[cian]", "color:#1F7A1F;font-weight:bold", ...a);

  // ---- один запрос к API изнутри страницы (несёт ваши cookie) --------------
  async function fetchPage(room, page) {
    const q = {
      _type: "flatsale",
      engine_version: { type: "term", value: 2 },
      region: { type: "terms", value: [CONFIG.region] },
      newobject: { type: "terms", value: [JKID] },
      page: { type: "term", value: page },
      sort: { type: "term", value: "creation_date_desc" },
    };
    if (room != null) q.room = { type: "terms", value: [room] };
    const r = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "*/*" },
      body: JSON.stringify({ jsonQuery: q }),
      credentials: "include",
    });
    if (!r.ok) throw new Error("HTTP " + r.status);
    const d = await r.json();
    const data = d.data || d;
    let offers = data.offersSerialized || data.offers || data.items || [];
    offers = offers.map((it) => (it && it.offer ? it.offer : it));
    const total = data.offerCount || data.offersCount || data.totalCount || offers.length;
    return { offers, total: parseInt(total, 10) || offers.length };
  }

  // ---- сбор: общий проход + добор по комнатности (обход лимита) ------------
  async function collectAll() {
    const byId = new Map();
    const totalsByRoom = {};
    let totalInJk = 0;
    const add = (offers) => offers.forEach((o) => {
      const id = o.cianId || o.id;
      if (id != null) byId.set(id, o);
    });

    log("Собираю ЖК", JKID, "(" + JKNAME + ") ...");
    // 1) общий проход без фильтра комнат
    for (let page = 1; page <= CONFIG.maxPages; page++) {
      let res;
      try { res = await fetchPage(null, page); }
      catch (e) { log("стр." + page, "ошибка:", e.message); break; }
      if (page === 1) { totalInJk = res.total; log("Всего в ЖК по Циан:", totalInJk); }
      if (!res.offers.length) break;
      add(res.offers);
      log(`стр.${page}: +${res.offers.length} (собрано ${byId.size}/${totalInJk})`);
      if (res.offers.length < CONFIG.pageSize) break;
      await pause();
    }
    // 2) по комнатности — точные «всего на Циан» по категориям + добор остатка
    for (const room of ROOMS) {
      let first;
      try { first = await fetchPage(room, 1); } catch (e) { continue; }
      totalsByRoom[room] = first.total;
      add(first.offers);
      if (first.total > CONFIG.pageSize) {
        for (let page = 2; page <= CONFIG.maxPages; page++) {
          await pause();
          let res;
          try { res = await fetchPage(room, page); } catch (e) { break; }
          if (!res.offers.length) break;
          add(res.offers);
          if (res.offers.length < CONFIG.pageSize) break;
        }
      }
      await pause();
    }
    return { offers: [...byId.values()], totalsByRoom, totalInJk };
  }

  // ---- нормализация одного лота (совпадает с Python-версией) ---------------
  const DEC = { without: "Без отделки", rough: "Черновая", fine: "Чистовая",
    preFine: "Предчистовая", prefine: "Предчистовая", designer: "Дизайнерская", clean: "Чистовая" };

  function categoryOf(o) {
    if (o.isStudio || o.flatType === "studio") return "Студия";
    if (["openPlan", "openplan", "freePlan"].includes(o.flatType)) return "Своб. планировка";
    let rc = o.roomsCount;
    if (rc == null) rc = o.roomsForSaleCount;
    if (rc == null) return null;
    rc = parseInt(rc, 10);
    if (isNaN(rc)) return null;
    if (rc === 0) return "Студия";
    if (rc >= 4) return "4+";
    return String(rc);
  }
  const dig = (o, path) => path.split(".").reduce((a, k) => (a == null ? a : a[k]), o);
  function priceOf(o) {
    const p = dig(o, "bargainTerms.priceRur") || dig(o, "bargainTerms.price") ||
      dig(o, "bargainTerms.prices.rur") || o.price;
    const n = parseFloat(p); return isNaN(n) ? null : n;
  }
  function areaOf(o) {
    const a = o.totalArea; if (a == null) return null;
    const n = parseFloat(String(a).replace(",", ".")); return isNaN(n) ? null : n;
  }
  function buildingOf(o) {
    return dig(o, "newbuilding.house.name") || dig(o, "newbuilding.name") ||
      dig(o, "building.name") || dig(o, "geo.jk.house.name") || null;
  }
  function sellerType(o) {
    const ut = dig(o, "user.userType");
    if (o.isFromBuilder || o.fromDeveloper || dig(o, "newbuilding.isFromBuilder") ||
        dig(o, "newbuilding.isFromDeveloper") || ["developer", "builder"].includes(ut)) return "Застройщик";
    if (o.isByHomeowner || ["homeowner", "owner"].includes(ut)) return "Собственник";
    if (["agency", "realtor", "agent", "managementCompany"].includes(ut)) return "Агентство";
    if (dig(o, "user.agencyName") || dig(o, "user.companyName")) return "Агентство";
    return null;
  }
  const sellerName = (o) => dig(o, "user.agencyName") || dig(o, "user.companyName") ||
    dig(o, "user.title") || dig(o, "user.name") || null;
  function decorationOf(o) {
    let d = o.decoration || o.repairType;
    if (d && typeof d === "object") d = d.type || d.value;
    if (!d) return null;
    return DEC[d] || String(d);
  }
  function pubDate(o) {
    const ts = o.addedTimestamp || o.creationTimestamp;
    if (ts) { const d = new Date(ts * 1000); if (!isNaN(d)) return d; }
    if (o.creationDate) { const d = new Date(o.creationDate); if (!isNaN(d)) return d; }
    return null;
  }
  const updDate = (o) => { const s = o.editDate || o.updatedAt; if (!s) return null; const d = new Date(s); return isNaN(d) ? null : d; };
  const fmtDate = (d) => d ? `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}.${d.getFullYear()}` : "";
  const offerUrl = (o) => o.fullUrl || ((o.cianId || o.id) ? `https://www.cian.ru/sale/flat/${o.cianId || o.id}/` : null);

  function normalize(o) {
    const area = areaOf(o), price = priceOf(o);
    const pub = pubDate(o);
    return {
      cianId: o.cianId || o.id || null,
      url: offerUrl(o),
      category: categoryOf(o),
      area, floor: o.floorNumber != null ? o.floorNumber : null,
      floors: dig(o, "building.floorsCount") || o.floorsCount || null,
      building: buildingOf(o),
      seller_type: sellerType(o), seller_name: sellerName(o),
      decoration: decorationOf(o),
      price, ppm: price && area ? Math.round(price / area) : null,
      published: pub ? fmtDate(pub) : "",
      exposure: pub ? Math.floor((Date.now() - pub.getTime()) / 86400000) : "",
      updated: updDate(o) ? fmtDate(updDate(o)) : "",
    };
  }

  // ---- генерация книги Excel (SpreadsheetML 2003, без библиотек) -----------
  const esc = (s) => String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  function cell(c) {
    if (c == null || c.v == null || c.v === "") return c && c.s ? `<Cell ss:StyleID="${c.s}"/>` : "<Cell/>";
    const a = [];
    if (c.s) a.push(`ss:StyleID="${c.s}"`);
    if (c.href) a.push(`ss:HRef="${esc(c.href)}"`);
    if (c.merge) a.push(`ss:MergeAcross="${c.merge}"`);
    return `<Cell ${a.join(" ")}><Data ss:Type="${c.t || "String"}">${esc(c.v)}</Data></Cell>`;
  }
  const rowXml = (cells) => "<Row>" + cells.map(cell).join("") + "</Row>";
  function worksheet(name, cols, rowsXml, freeze) {
    const colsXml = cols.map((w) => `<Column ss:Width="${w}"/>`).join("");
    const opt = freeze
      ? `<WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel"><FreezePanes/><FrozenNoSplit/><SplitHorizontal>4</SplitHorizontal><TopRowBottomPane>4</TopRowBottomPane><ActivePane>2</ActivePane></WorksheetOptions>`
      : "";
    return `<Worksheet ss:Name="${esc(name)}"><Table>${colsXml}${rowsXml}</Table>${opt}</Worksheet>`;
  }

  const HEADERS = ["№", "ID объявления", "Категория", "Площадь, м²", "Этаж", "Этаж-ность",
    "Корпус / секция", "Тип продавца", "Продавец", "Отделка", "Цена, ₽", "Цена за м², ₽",
    "Дата публикации", "Срок эксп., дн", "Дата обновления", "Ссылка"];
  const COLW = [34, 90, 80, 74, 44, 60, 110, 90, 150, 100, 105, 95, 95, 80, 95, 70];

  function dataSheet(name, title, sub, rows) {
    let xml = "";
    xml += rowXml([{ v: title, s: "title", merge: HEADERS.length - 1 }]);
    xml += rowXml([{ v: sub, s: "sub", merge: HEADERS.length - 1 }]);
    xml += rowXml([{}]);
    xml += rowXml(HEADERS.map((h) => ({ v: h, s: "hdr" })));
    rows.forEach((r, i) => {
      xml += rowXml([
        { v: i + 1, t: "Number" },
        { v: r.cianId, t: r.cianId ? "Number" : "String" },
        { v: r.category },
        { v: r.area, t: r.area != null ? "Number" : "String", s: "area" },
        { v: r.floor, t: r.floor != null ? "Number" : "String" },
        { v: r.floors, t: r.floors != null ? "Number" : "String" },
        { v: r.building },
        { v: r.seller_type },
        { v: r.seller_name },
        { v: r.decoration },
        { v: r.price, t: r.price != null ? "Number" : "String", s: "num" },
        { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: "num" },
        { v: r.published },
        { v: r.exposure, t: r.exposure !== "" ? "Number" : "String" },
        { v: r.updated },
        r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]);
    });
    return worksheet(name, COLW, xml, true);
  }

  // ---- сводный лист (агрегаты считаем в JS) -------------------------------
  const CATS = ["Студия", "Своб. планировка", "1", "2", "3", "4+"];
  const ROOM_OF_CAT = { "Студия": [9], "Своб. планировка": [7], "1": [1], "2": [2], "3": [3], "4+": [4, 5, 6] };
  const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
  const num = (v) => (v == null ? { v: "—" } : { v, t: "Number", s: "num" });

  function summarySheet(rows, totalsByRoom, totalInJk) {
    const present = CATS.filter((c) => rows.some((r) => r.category === c));
    const today = fmtDate(new Date());
    let xml = "";
    xml += rowXml([{ v: `ЖК ${JKNAME} (ID ${JKID}) — сводка`, s: "title", merge: 5 }]);
    xml += rowXml([{ v: `Данные Циан на ${today}. Собрано ${rows.length} лотов. «Частник» = собственник/агентство.`, s: "sub", merge: 5 }]);
    xml += rowXml([{}]);

    xml += rowXml([{ v: "ОХВАТ ВЫГРУЗКИ", s: "bold" }]);
    xml += rowXml(["Категория", "Собрано", "Всего на Циан", "% выдачи"].map((h) => ({ v: h, s: "hdr" })));
    let sumC = 0, sumT = 0;
    present.forEach((c) => {
      const got = rows.filter((r) => r.category === c).length;
      const tot = ROOM_OF_CAT[c].reduce((s, rm) => s + (totalsByRoom[rm] || 0), 0) || null;
      sumC += got; if (tot) sumT += tot;
      xml += rowXml([{ v: c }, { v: got, t: "Number" }, num(tot),
        { v: tot ? Math.round((got / tot) * 100) + "%" : "—" }]);
    });
    xml += rowXml([{ v: "ИТОГО (категории)", s: "bold" }, { v: sumC, t: "Number", s: "bold" },
      num(sumT || null), { v: sumT ? Math.round((sumC / sumT) * 100) + "%" : "—" }]);
    if (totalInJk) xml += rowXml([{ v: "Всего квартир в ЖК (Циан)", s: "bold" },
      { v: rows.length, t: "Number" }, { v: totalInJk, t: "Number" },
      { v: Math.round((rows.length / totalInJk) * 100) + "%" }]);
    xml += rowXml([{}]);

    xml += rowXml([{ v: "СРЕДНЯЯ ЦЕНА ЗА м², ₽", s: "bold" }]);
    xml += rowXml(["Категория", "Частник", "Застройщик", "Все"].map((h) => ({ v: h, s: "hdr" })));
    const ppmBy = (subset) => subset.map((r) => r.ppm).filter((x) => x != null);
    present.concat(["ИТОГО по ЖК"]).forEach((c) => {
      const sub = c === "ИТОГО по ЖК" ? rows : rows.filter((r) => r.category === c);
      const chast = ppmBy(sub.filter((r) => r.seller_type !== "Застройщик"));
      const zast = ppmBy(sub.filter((r) => r.seller_type === "Застройщик"));
      xml += rowXml([{ v: c, s: c === "ИТОГО по ЖК" ? "bold" : "" },
        num(avg(chast)), num(avg(zast)), num(avg(ppmBy(sub)))]);
    });
    xml += rowXml([{}]);

    xml += rowXml([{ v: "ДИАПАЗОН ЦЕН, ₽", s: "bold" }]);
    xml += rowXml(["Категория", "Мин. цена", "Средн. цена", "Макс. цена", "Мин. ₽/м²", "Макс. ₽/м²"].map((h) => ({ v: h, s: "hdr" })));
    present.forEach((c) => {
      const sub = rows.filter((r) => r.category === c);
      const pr = sub.map((r) => r.price).filter((x) => x != null);
      const pm = sub.map((r) => r.ppm).filter((x) => x != null);
      xml += rowXml([{ v: c }, num(pr.length ? Math.min(...pr) : null), num(avg(pr)),
        num(pr.length ? Math.max(...pr) : null), num(pm.length ? Math.min(...pm) : null),
        num(pm.length ? Math.max(...pm) : null)]);
    });
    return worksheet("Сводка", [26, 16, 16, 16, 14, 14].map((w) => w * 6), xml, false);
  }

  function buildWorkbook(rows, totalsByRoom, totalInJk) {
    rows = rows.slice().sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
    const today = fmtDate(new Date());
    const sheets = [];
    sheets.push(summarySheet(rows, totalsByRoom, totalInJk));
    sheets.push(dataSheet("Все_лоты", `ЖК ${JKNAME} — все лоты`,
      `Источник: Циан (ID ${JKID}), ${today}. Сортировка по ₽/м². Срок экспозиции — дни с последней подачи.`, rows));
    const sheetName = { "Студия": "Студия", "Своб. планировка": "Своб_планировка",
      "1": "1-комн", "2": "2-комн", "3": "3-комн", "4+": "4-комн" };
    CATS.forEach((c) => {
      const sub = rows.filter((r) => r.category === c);
      if (sub.length) sheets.push(dataSheet(sheetName[c], `ЖК ${JKNAME} — ${c}`,
        `Собрано ${sub.length}. Сортировка по ₽/м².`, sub));
    });
    const styles = `<Styles>
      <Style ss:ID="Default" ss:Name="Normal"><Alignment ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="11"/></Style>
      <Style ss:ID="hdr"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#1F2A44" ss:Pattern="Solid"/><Alignment ss:Horizontal="Center" ss:Vertical="Center" ss:WrapText="1"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/></Borders></Style>
      <Style ss:ID="title"><Font ss:Bold="1" ss:Size="13"/></Style>
      <Style ss:ID="sub"><Font ss:Italic="1" ss:Color="#555555" ss:Size="9"/></Style>
      <Style ss:ID="bold"><Font ss:Bold="1"/></Style>
      <Style ss:ID="num"><NumberFormat ss:Format="#,##0"/></Style>
      <Style ss:ID="area"><NumberFormat ss:Format="0.0"/></Style>
      <Style ss:ID="link"><Font ss:Color="#1155CC" ss:Underline="Single"/></Style>
    </Styles>`;
    return `<?xml version="1.0" encoding="UTF-8"?>\n<?mso-application progid="Excel.Sheet"?>\n` +
      `<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" ` +
      `xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" ` +
      `xmlns:html="http://www.w3.org/TR/REC-html40">${styles}${sheets.join("")}</Workbook>`;
  }

  function download(xml, name) {
    const blob = new Blob(["﻿", xml], { type: "application/vnd.ms-excel;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a); a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1500);
  }
  const slug = (s) => s.toLowerCase().replace(/\s+/g, "-").replace(/[^0-9a-zа-яё_\-]/g, "") || "jk";

  // ---- запуск -------------------------------------------------------------
  try {
    const { offers, totalsByRoom, totalInJk } = await collectAll();
    if (!offers.length) {
      alert("Не собрано ни одного лота.\nПроверьте, что вы на странице ЖК, вошли в аккаунт и прошли капчу. " +
        "Если ЖК не в Москве — поменяйте CONFIG.region в начале скрипта.");
      return;
    }
    const rows = offers.map(normalize);
    const ppms = rows.map((r) => r.ppm).filter((x) => x != null);
    const exps = rows.map((r) => r.exposure).filter((x) => x !== "" && x != null);
    const direct = rows.filter((r) => r.url && r.url.includes("/sale/flat/")).length;

    const xml = buildWorkbook(rows, totalsByRoom, totalInJk);
    const fname = `cian_${slug(JKNAME)}_${new Date().toISOString().slice(0, 10)}.xls`;
    download(xml, fname);

    log("=".repeat(48));
    log(`ГОТОВО: ${rows.length} лотов${totalInJk ? " из " + totalInJk + " (" + Math.round(rows.length / totalInJk * 100) + "%)" : ""}`);
    log(`Прямых ссылок на лот: ${direct}/${rows.length}`);
    if (ppms.length) log(`₽/м²: мин ${Math.min(...ppms).toLocaleString("ru")} | средн ${avg(ppms).toLocaleString("ru")} | макс ${Math.max(...ppms).toLocaleString("ru")}`);
    if (exps.length) log(`Срок экспозиции: средн ${Math.round(exps.reduce((a, b) => a + b, 0) / exps.length)} дн`);
    log(`Файл скачан: ${fname}`);
    log("=".repeat(48));
    alert(`Готово! Собрано ${rows.length} лотов${totalInJk ? " из " + totalInJk : ""}.\nФайл ${fname} скачан — откройте его в Excel.`);
  } catch (e) {
    console.error(e);
    alert("Ошибка: " + e.message + "\nОткройте F12 -> Console для подробностей. " +
      "Часто помогает: обновить страницу ЖК, войти в аккаунт, пройти капчу и запустить снова.");
  }
})();
