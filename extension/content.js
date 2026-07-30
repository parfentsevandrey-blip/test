/* ============================================================================
 *  Циан → Excel — content script (world: MAIN).
 *  На странице ЖК показывает кнопку «Выгрузить в Excel». По клику собирает все
 *  активные лоты через API из самой вкладки (с вашей сессией -> проходит антибот)
 *  и скачивает .xlsx (Сводка + по комнатности + Все_лоты и ещё несколько листов).
 * ========================================================================== */
(() => {
  "use strict";
  if (window.__cianExcelMounted) return;
  window.__cianExcelMounted = true;

  const CONFIG = {
    region: 1, delayMin: 300, delayMax: 700,
    maxPages: 54, pageSize: 28,          // реальный потолок Циан ~54 страницы (≈1512)
    minPriceSpan: 200000, priceCeiling: 3000000000,  // дробление по цене
    maxRetries: 4, backoffBase: 1500,    // ретраи на 429/5xx
    reqBudget: 400,                      // защита от runaway-запросов
  };
  const API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/";
  const ROOMS = [9, 7, 1, 2, 3, 4, 5, 6];
  // Диагностика качества сбора текущего run() — сбрасывается в начале
  // collectAll(), пишется из apiFetch()/paginateSegment(). null вне сбора.
  let health = null;
  const isHealthWarn = (h) => !!(h && h.requests && ((h.retries / h.requests > 0.15) || h.totalDrift > 0));

  // === Перехват настоящего запроса страницы =================================
  // Циан сам шлёт search-offers с ПРАВИЛЬНЫМ фильтром по этому ЖК. Перехватываем
  // тело (jsonQuery), чтобы не угадывать структуру фильтра. Ставится на
  // document_start (см. manifest), до скриптов страницы.
  //
  // На странице ЖК, помимо основного списка квартир, search-offers-desktop могут
  // дёргать и ПОБОЧНЫЕ виджеты («Похожие ЖК», «Рекомендуем», объявления другого
  // комплекса того же застройщика) — их jsonQuery относится к ДРУГОМУ newobject.
  // Наивный «запоминаем последний увиденный запрос» рискует подхватить именно
  // такой виджет вместо актуального (в т.ч. отфильтрованного) списка. Поэтому,
  // если знаем ID текущего ЖК из URL и запрос явно указывает на ЖК-ID через
  // newobject/geo — сверяем и отбрасываем явные непопадания. Если запрос ни на
  // какой ЖК явно не указывает (обычный поиск по фильтрам/карте) — проверка не
  // применяется, поведение как раньше.
  function currentJkIdFromUrl() {
    const href = location.href;
    const id = (href.match(/-(\d+)\/(?:\?|#|$)/) || [])[1] ||
      (href.match(/newobject(?:%5B0%5D|\[0\])?=(\d+)/) || [])[1] || null;
    return id ? parseInt(id, 10) : null;
  }
  function queryMismatchesJk(jq, jkId) {
    const ids = [];
    if (jq.newobject && Array.isArray(jq.newobject.value)) ids.push(...jq.newobject.value);
    const geo = jq.geo && jq.geo.value;
    if (Array.isArray(geo)) geo.forEach((g) => { if (g && (g.type === "newobject" || g.type === "jk") && g.id != null) ids.push(g.id); });
    if (!ids.length) return false;                         // запрос не привязан к конкретному ЖК — не проверяем
    return !ids.map(Number).includes(Number(jkId));
  }
  function rememberQuery(body) {
    try {
      const b = typeof body === "string" ? JSON.parse(body) : body;
      if (!(b && b.jsonQuery && b.jsonQuery._type)) return;
      const jkId = /zhiloy-kompleks|newobject(?:%5B0%5D|\[0\])?=/i.test(location.href) ? currentJkIdFromUrl() : null;
      if (jkId && queryMismatchesJk(b.jsonQuery, jkId)) return;   // запрос про другой ЖК (виджет «похожие») — игнорируем
      window.__cianCapturedQuery = b.jsonQuery;
    } catch (e) { /* ignore */ }
  }
  try {
    const of = window.fetch;
    window.fetch = function (input, init) {
      try {
        const url = typeof input === "string" ? input : (input && input.url);
        if (url && /search-offers/.test(url) && init && init.body) rememberQuery(init.body);
      } catch (e) { /* ignore */ }
      return of.apply(this, arguments);
    };
    const xo = XMLHttpRequest.prototype.open, xs = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (m, u) { this.__cu = u; return xo.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function (body) {
      try { if (this.__cu && /search-offers/.test(this.__cu) && body) rememberQuery(body); } catch (e) { /* ignore */ }
      return xs.apply(this, arguments);
    };
  } catch (e) { /* ignore */ }

  // Найти объект "jsonQuery":{...} в строке. Баланс скобок СТРОКО-АВАРЕ
  // (скобки внутри кавычек и эскейпы не считаются), без жёсткого лимита длины.
  function findJsonQuery(s) {
    let idx = 0;
    while ((idx = s.indexOf('"jsonQuery"', idx)) !== -1) {
      const start = s.indexOf("{", idx);
      if (start === -1) break;
      let depth = 0, end = -1, inStr = false, esc = false;
      for (let i = start; i < s.length; i++) {
        const ch = s[i];
        if (inStr) {
          if (esc) esc = false; else if (ch === "\\") esc = true; else if (ch === '"') inStr = false;
          continue;
        }
        if (ch === '"') inStr = true;
        else if (ch === "{") depth++;
        else if (ch === "}") { if (--depth === 0) { end = i; break; } }
      }
      if (end !== -1) {
        try { const o = JSON.parse(s.slice(start, end + 1)); if (o && o._type) return o; } catch (e) { /* ignore */ }
      }
      idx += 11;
    }
    return null;
  }

  // Запрос страницы: перехваченный -> __NEXT_DATA__ -> сырой HTML.
  function pageJsonQuery() {
    if (window.__cianCapturedQuery) return window.__cianCapturedQuery;
    try { if (window.__NEXT_DATA__) { const q = findJsonQuery(JSON.stringify(window.__NEXT_DATA__)); if (q) return q; } } catch (e) { /* ignore */ }
    try { const q = findJsonQuery(document.documentElement.innerHTML); if (q) return q; } catch (e) { /* ignore */ }
    return null;
  }

  // Используем ПОЛНЫЙ запрос страницы со ВСЕМИ фильтрами пользователя (цена,
  // комнаты, год, полигон/карта и т.п.) — выгружаем ровно то, что он видит.
  // Для пагинации в withFilters() переопределяются только page/room/price.
  function cleanBaseQuery(base) {
    const b = JSON.parse(JSON.stringify(base));
    delete b.page;                 // страницу задаём сами при пагинации
    if (!b._type) b._type = "flatsale";
    if (!b.engine_version) b.engine_version = { type: "term", value: 2 };
    return b;
  }

  // Тема выгрузки: ЖК (по id/имени) либо произвольная выборка по фильтрам/карте.
  function detectSubject() {
    // ЖК только если URL реально про ЖК (иначе на cat.php/карте подхватили бы
    // случайный cianId объявления).
    const isJkUrl = /zhiloy-kompleks|newobject(?:%5B0%5D|\[0\])?=/i.test(location.href);
    if (isJkUrl) {
      const jk = detectJk();
      if (jk.id) return { id: jk.id, isJk: true, title: "ЖК " + (jk.name || jk.id), slug: slug(jk.name || ("jk-" + jk.id)) };
    }
    // выборка по фильтрам / области на карте
    let nm = "";
    const pn = location.href.match(/polygon_name(?:%5B0%5D|\[0\])?=([^&]+)/);
    if (pn) { try { nm = decodeURIComponent(pn[1].replace(/\+/g, " ")).trim(); } catch (e) { /* ignore */ } }
    if (!nm) { const h = (document.querySelector("h1") || {}).textContent || ""; nm = h.replace(/\s+/g, " ").trim().slice(0, 50); }
    return { id: null, isJk: false, title: nm ? ("Выборка Циан · " + nm) : "Выборка Циан (по фильтрам)", slug: slug(nm || "vyborka") };
  }

  // Сколько объявлений показывает сама страница (для сверки/охвата).
  function pageResultCount() {
    try {
      const t = document.body ? (document.body.innerText || "") : "";
      const m = t.match(/Найдено\s+([\d  ]+)\s+объявлен/i) || t.match(/([\d  ]+)\s+объявлени[йяе]\b/i);
      if (m) return parseInt(m[1].replace(/[\s ]/g, ""), 10) || null;
    } catch (e) { /* ignore */ }
    return null;
  }

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
    const raw = ((document.querySelector("h1") || {}).textContent || "").trim() ||
      (document.title.split(/[—|·|]/)[0] || "").trim();
    return { id: id ? parseInt(id, 10) : null, name: cleanName(raw) };
  }

  // Чистое имя ЖК для заголовка/файла (в h1 Циан склеиваются тексты).
  function cleanName(raw) {
    raw = (raw || "").replace(/\s+/g, " ").trim();
    const q = raw.match(/«([^»]+)»/);              // «Симфония 34» -> берём из кавычек
    if (q) return "«" + q[1].trim() + "»";
    raw = raw
      .replace(/^(?:купить|снять)\s+квартир\S*\s+в\s+/i, "")
      .replace(/^квартир\S*\s+в\s+/i, "")
      .replace(/^(?:жилом комплексе|жилой комплекс|жк)\s+/i, "")
      .trim();
    return raw.slice(0, 60);
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

  // Запрос страницы с нашими параметрами поверх фильтров пользователя.
  function withFilters(base, o) {
    o = o || {};
    const q = JSON.parse(JSON.stringify(base));
    if (o.page != null) q.page = { type: "term", value: o.page };
    if (o.room != null) q.room = { type: "terms", value: [o.room] };
    if (o.priceGte != null || o.priceLte != null) {
      const v = {};
      if (o.priceGte != null) v.gte = o.priceGte;
      if (o.priceLte != null) v.lte = o.priceLte;
      q.price = { type: "range", value: v };
    }
    if (o.sort) q.sort = { type: "term", value: o.sort };
    return { jsonQuery: q };
  }

  // POST с ретраями и экспоненциальным бэк-оффом (429/503/5xx/сеть), уважает Retry-After.
  async function apiFetch(body) {
    let delay = CONFIG.backoffBase, lastErr = null;
    for (let attempt = 1; attempt <= CONFIG.maxRetries; attempt++) {
      try {
        const r = await fetch(API, {
          method: "POST", headers: { "Content-Type": "application/json", Accept: "*/*" },
          body: JSON.stringify(body), credentials: "include",
        });
        if (r.status === 200) {
          const d = await r.json(); const data = d.data || d;
          let offers = data.offersSerialized || data.offers || data.items || [];
          offers = offers.map((it) => (it && it.offer ? it.offer : it));
          // aggregatedCount = всего по фильтру; offerCount иногда = размер страницы
          const total = data.aggregatedCount || data.offerCount || data.offersCount || data.totalCount || offers.length;
          return { offers, total: parseInt(total, 10) || offers.length };
        }
        if (r.status === 403) throw new Error("HTTP 403 — нужна авторизация/капча на cian.ru");
        if (r.status === 429 || r.status >= 500) {
          if (health) { health.retries++; health.retryStatuses[r.status] = (health.retryStatuses[r.status] || 0) + 1; }
          const ra = parseInt(r.headers && r.headers.get && r.headers.get("Retry-After"), 10);
          const wait = (ra ? ra * 1000 : delay) + Math.random() * 400;
          console.warn(`[cian-excel] HTTP ${r.status} — пауза ${Math.round(wait / 1000)}s (попытка ${attempt}/${CONFIG.maxRetries})`);
          lastErr = "HTTP " + r.status; await sleep(wait); delay *= 2; continue;
        }
        throw new Error("HTTP " + r.status);
      } catch (e) {
        lastErr = (e && e.message) || String(e);
        if (/403/.test(lastErr)) throw e;
        if (attempt >= CONFIG.maxRetries) throw new Error(lastErr);
        if (health) { health.retries++; health.retryStatuses.network = (health.retryStatuses.network || 0) + 1; }
        await sleep(delay + Math.random() * 400); delay *= 2;
      }
    }
    throw new Error(lastErr || "запрос не удался");
  }
  // совместимость: одиночная страница
  const fetchPage = (base, room, page) => apiFetch(withFilters(base, { page, room }));

  // Детерминированный сбор 100% выдачи: прямой проход -> при недоборе разложить
  // по комнатности (×8 ёмкости) и рекурсивно дробить по цене (обход лимита/ротации).
  async function collectAll(base, onProgress) {
    const byId = new Map();
    let grandTotal = 0, requests = 0;
    health = { retries: 0, retryStatuses: {}, totalDrift: 0 };   // диагностика качества сбора этого run()
    const add = (offers) => offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) byId.set(id, o); });

    // Пагинация одного сегмента; seen = сколько УНИКАЛЬНЫХ id вернул сам сегмент.
    async function paginateSegment(filters, label) {
      const seg = new Set();
      let total = 0, firstTotal = null, page = 1, empty = 0;
      while (page <= CONFIG.maxPages && requests < CONFIG.reqBudget) {
        onProgress(`${label}стр.${page}…`, byId.size, grandTotal);
        let res; try { res = await apiFetch(withFilters(base, Object.assign({}, filters, { page }))); requests++; }
        catch (e) { if (page === 1) throw e; break; }
        // Циан иногда отдаёт РАЗНЫЙ aggregatedCount на разных страницах ОДНОГО
        // и того же сегмента (ротация/нестабильность выдачи) — фиксируем как
        // диагностику качества сбора, не как ошибку (сама логика это переживает).
        if (firstTotal == null) firstTotal = res.total; else if (res.total !== firstTotal) health.totalDrift++;
        total = res.total;
        if (!res.offers.length) { if (++empty >= 2) break; page++; await pause(); continue; }
        empty = 0;
        res.offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) seg.add(id); });
        add(res.offers);
        onProgress(`Собрано ${byId.size}${grandTotal ? " из " + grandTotal : ""}…`, byId.size, grandTotal);
        if (seg.size >= total) break;                                  // весь сегмент собран
        if (page >= Math.ceil(total / CONFIG.pageSize) + 2) break;
        page++; await pause();
      }
      return { total, seen: seg.size };
    }

    // Рекурсивное дробление по цене (если сегмент отдал не всё — ротация/лимит).
    // knownTotal/knownSeen — итог уже выполненного вызывающим прохода по полному
    // диапазону: если он недобрал, сразу делим пополам, не перезапрашивая весь
    // диапазон заново (экономит до 54 запросов на сегмент).
    async function priceSplit(filters, label, knownTotal, knownSeen) {
      const p = (base.price && base.price.value) || {};
      const lo0 = filters.priceGte != null ? filters.priceGte : (p.gte != null ? p.gte : 0);
      const hi0 = filters.priceLte != null ? filters.priceLte : (p.lte != null ? p.lte : CONFIG.priceCeiling);
      const stack = [];
      if (knownTotal == null) {
        stack.push([lo0, hi0]);                                  // полный проход ещё не делался
      } else if (knownTotal > (knownSeen || 0) && (hi0 - lo0) > CONFIG.minPriceSpan) {
        const mid = Math.floor((lo0 + hi0) / 2);                 // вызывающий уже прошёл [lo0,hi0]
        stack.push([lo0, mid]); stack.push([mid + 1, hi0]);
      }
      while (stack.length && requests < CONFIG.reqBudget) {
        const [a, b] = stack.pop();
        const { total, seen } = await paginateSegment(Object.assign({}, filters, { priceGte: a, priceLte: b }), label);
        if (total > seen && (b - a) > CONFIG.minPriceSpan) {
          const mid = Math.floor((a + b) / 2);
          stack.push([a, mid]); stack.push([mid + 1, b]);
        }
        if (grandTotal && byId.size >= grandTotal) break;
      }
    }

    // 1) прямой проход по запросу пользователя
    const first = await paginateSegment({}, "");
    grandTotal = first.total;

    // 2) недобор -> детерминированная декомпозиция
    let totalsByRoom = null;
    if (grandTotal && byId.size < grandTotal && requests < CONFIG.reqBudget) {
      if (!base.room) {
        totalsByRoom = {};
        for (const room of ROOMS) {
          if (requests >= CONFIG.reqBudget || byId.size >= grandTotal) break;
          onProgress(`Комнаты ${room}… (${byId.size}/${grandTotal})`, byId.size, grandTotal);
          const pr = await paginateSegment({ room }, `room ${room}: `);
          totalsByRoom[room] = pr.total;
          if (pr.total > pr.seen) await priceSplit({ room }, `room ${room} ₽: `, pr.total, pr.seen);
          await pause();
        }
      } else {
        await priceSplit({}, "₽: ", first.total, first.seen);   // у пользователя уже фильтр по комнатам
      }
    }
    health.requests = requests;
    console.log(`[cian-excel] ИТОГО ${byId.size}/${grandTotal} за ${requests} запросов (ретраев: ${health.retries}, дрейф total: ${health.totalDrift})`);
    return { offers: [...byId.values()], totalsByRoom, totalInJk: grandTotal, health };
  }

  // ---------- нормализация (как в Python/консольной версии) ----------
  function categoryOf(o) {
    const ft = (o.flatType || "").toString().toLowerCase();
    if (o.isStudio || ft.includes("studio")) return "Студия";
    if (/open|free/.test(ft)) return "Своб. планировка";   // openPlan/freePlan/freeLayout/freeAppointment
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

  // ===== FIN-BLOCK-START =====================================================
  // ОПРЕДЕЛЕНИЕ ОТДЕЛКИ/РЕМОНТА. Один и тот же блок лежит в трёх экспортёрах:
  // extension/content.js, cian_browser.js и (портом) cian_scraper.py.
  // Правите один — правьте все три; расхождение ловит tests/check_finish.mjs.
  //
  // Слои по убыванию надёжности:
  //   1) поле Циан repairType/decoration -> источник «Циан-поле»
  //   2) разбор текста объявления        -> источник «из описания»
  //   3) не нашлось                      -> категория не определена
  //
  // В регулярках ниже ЗАПРЕЩЕНЫ \w, \b, \d, lookbehind и флаги i/u: в JS \w и \b
  // ASCII-only и на кириллице молча не срабатывают, а в Python — срабатывают.
  // Ровно из-за этого правило /авторск\w*\s+ремонт/ никогда не ловило
  // «авторский ремонт». Регистр и «ё» снимает finNorm(), а не флаг.
  const FIN = {
    none: "Без отделки", rough: "Черновая", prefine: "Предчистовая (white box)",
    fine: "Чистовая", turnkey: "Под ключ / с мебелью",
    norepair: "Без ремонта", cosmetic: "Косметический", euro: "Евроремонт",
    designer: "Дизайнерский", some: "С ремонтом (тип не указан)",
  };
  // качество отделки для индекса привлекательности лота (чем выше — тем лучше
  // для проживания); это НЕ порядок отображения в сводке.
  const FIN_QUALITY_RANK = {
    [FIN.none]: 1, [FIN.rough]: 1, [FIN.norepair]: 1,
    [FIN.prefine]: 2,
    [FIN.cosmetic]: 3, [FIN.some]: 3,
    [FIN.fine]: 4,
    [FIN.euro]: 5, [FIN.turnkey]: 5,
    [FIN.designer]: 6,
  };
  // Значение поля Циан -> категория. Подтверждены дампами API:
  //   decoration: without | rough | preFine | fine | fineWithFurniture
  //   repairType: no | cosmetic | euro | design
  // Остальные ключи — толерантные догадки на случай смены словаря; они
  // безвредны, но НЕ считайте их покрытием.
  const FIELD_FIN = {
    without: FIN.none, rough: FIN.rough, draft: FIN.rough,
    prefine: FIN.prefine, preFine: FIN.prefine, whitebox: FIN.prefine,
    fine: FIN.fine, clean: FIN.fine, finish: FIN.fine, chistovaya: FIN.fine,
    fineWithFurniture: FIN.turnkey, turnkey: FIN.turnkey, withFurniture: FIN.turnkey,
    no: FIN.norepair, norepair: FIN.norepair,
    cosmetic: FIN.cosmetic, normal: FIN.cosmetic,
    euro: FIN.euro, good: FIN.euro,
    design: FIN.designer, designer: FIN.designer,
  };
  // Стоп-контексты: вырезаются из текста ДО классификации, чтобы ремонт
  // подъезда, соседнего корпуса или «сделаем под ваш вкус» не приписывался
  // самой квартире. Вырезается только найденный участок, а не всё
  // предложение: в «в доме ремонт подъезда, в квартире евроремонт»
  // евроремонт обязан уцелеть.
  const FIN_STOPS = [
    /(?:^|[^а-яё])(?:кап(?:итальн[а-яё]*)?[\s-]*)?(?:ремонт|отделк)[а-яё]*[\s-]*(?:в[\s-]*|на[\s-]*)?(?:детск[а-яё]*[\s-]*(?:сад|площадк)|мест[а-яё]*[\s-]*общего|подъезд|фасад|кровл|крыш|дорог|тротуар|лифт|двор|подвал|чердак|площадк|набережн|станц|метро|улиц|шоссе|проспект|школ|моп|стояк|трубопровод|инженерн|паркинг|парковк|холл|лобби|вестибюл|входн[а-яё]*[\s-]*групп)[а-яё]*/g,  // S1 — ремонт общедомового/городского объекта: подъезд, фасад, дорога, лифт
    /(?:^|[^а-яё])(?:кап(?:итальн[а-яё]*)?[\s-]*)?(?:ремонт|отделк)[а-яё]*[\s-]*(?:в[\s-]*)?(?:дом[аеу]|здани|корпус|многоквартирн)[а-яё]*/g,  // S2 — ремонт дома/здания/корпуса целиком
    /(?:^|[^а-яё])(?:дом|здани|корпус|подъезд|фасад|кровл|крыш|школ|поликлиник|детск[а-яё]*[\s-]*сад)[а-яё]*[\s-]*(?:был[а-яё]*[\s-]*|уже[\s-]*|недавно[\s-]*)?(?:после|прошел|прошла|прошли|ждет|ожидает|планируется|стоит[\s-]*в[\s-]*плане|под)[\s-]*(?:кап(?:итальн[а-яё]*)?[\s-]*)?(?:ремонт|отделк)[а-яё]*/g,  // S3 — обратный порядок: «дом после капремонта»
    /(?:^|[^а-яё])(?:в|во)[\s-]*(?:дом[еу]|подъезде|здании|корпусе|дворе|холле|лобби|местах[\s-]*общего[\s-]*пользования)(?![а-яё])(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,12}?(?:ремонт|отделк)[а-яё]*|(?:^|[^а-яё])(?:в|во)[\s-]*(?:дом[еу]|подъезде|здании|корпусе|дворе|холле|лобби|местах[\s-]*общего[\s-]*пользования)(?![а-яё])[\s-]*(?:(?:уже|недавно|полностью|сейчас|как[\s-]*раз|только[\s-]*что)[\s-]*)*(?:сделан|выполнен|проведен|проведён|завершен|завершён|идет|идёт|ведется|ведётся|планируется|запланирован)[а-яё]*[\s-]*(?:кап(?:итальн[а-яё]*)?[\s-]*)?(?:ремонт|отделк)[а-яё]*/g,  // S4 — локатив переносит ремонт на дом: «в доме сделан ремонт»
    /(?:^|[^а-яё])(?:ремонт|отделк)[а-яё]*[\s-]*(?:в|у)[\s-]*(?:соседн|друг|перв|втор|треть|остальн)[а-яё]*[\s-]*(?:корпус|дом|подъезд|квартир|секц|башн|блок|очеред)[а-яё]*/g,  // S5 — чужой объект: «ремонт в соседнем корпусе»
    /(?:^|[^а-яё])(?:в|во|у)[\s-]*(?:соседн[а-яё]*|сосед[а-яё]*|друг[а-яё]*)[\s-]*(?:корпус|дом|подъезд|секц|башн|блок|очеред)?[а-яё]*(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,30}?(?:ремонт|отделк)[а-яё]*/g,  // S6 — чужой объект, обратный порядок: «у соседей евроремонт»
    /(?:^|[^а-яё])(?:рядом|неподалеку|поблизости|напротив|через[\s-]*дорогу|по[\s-]*соседству|во[\s-]*дворе)(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,30}?(?:ремонт|отделк)[а-яё]*/g,  // S7 — окружение, а не лот: «рядом идёт ремонт»
    /(?:^|[^а-яё])(?:сделаем|сделаю|выполним|поможем|организуем|подберем|обеспечим|доделаем|предлагаем|обсуждаем|можем[\s-]*сделать|можно[\s-]*(?:сделать|заказать)|готовы[\s-]*(?:сделать|выполнить)|возможн[а-яё]*|планируетс[а-яё]*|остал[а-яё]*[\s-]*(?:сделать|доделать))(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,30}?(?:ремонт|отделк)[а-яё]*(?:[\s-]*(?:под[\s-]*ключ|от[\s-]*застройщика|под[\s-]*ваш[а-яё]*[\s-]*вкус|за[\s-]*доплату))*/g,  // S8 — будущий/гипотетический ремонт: «сделаем ремонт под ваш вкус»
    /(?:^|[^а-яё])(?:ремонт|отделк)[а-яё]*(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,30}?(?:за[\s-]*доплату|под[\s-]*ваш[а-яё]*[\s-]*вкус|по[\s-]*ваш[а-яё]*[\s-]*проект[а-яё]*|под[\s-]*заказ|по[\s-]*желани[а-яё]*[\s-]*покупател[а-яё]*|на[\s-]*ваш[\s-]*выбор|опционально)/g,  // S9 — опциональность: «отделка за доплату»
    /(?:^|[^а-яё])(?:ремонт|отделк)[а-яё]*[\s-]*(?:в[\s-]*подарок|в[\s-]*кредит|в[\s-]*рассрочку|в[\s-]*ипотеку|за[\s-]*счет[\s-]*(?:банка|застройщика))/g,  // S10 — ремонт как бонус/финпродукт: «ремонт в подарок»
    /(?:^|[^а-яё])(?:скидка|рассрочк|кредит|ипотек|субсиди|бонус|сертификат|смет|материал|бригад|подрядчик|дизайн[\s-]*студи)[а-яё]*(?:(?!квартир|апартамент|комнат)[^.!?;,]){0,30}?(?:на[\s-]*)?(?:ремонт|отделк)[а-яё]*/g,  // S11 — реклама услуг и финансирования ремонта: «рассрочка на ремонт»
  ];
  // Порядок = приоритет: явные категории раньше общих, отрицание раньше
  // утверждения («не требует ремонта» должно опередить «требует ремонта»),
  // качество отделки важнее меблировки. Последнее правило — catch-all.
  const FIN_RULES = [
    [FIN.designer, /(?:^|[^а-яё])дизайнерск[а-яё]*[\s-]*(?:ремонт|отделк|интерьер|квартир|апартамент|решени|проект)[а-яё]*|(?:^|[^а-яё])(?:авторск|эксклюзивн)[а-яё]*[\s-]*(?:ремонт|отделк|интерьер|проект)[а-яё]*|(?:ремонт|отделк[а-яё]*|интерьер[а-яё]*)[\s-]*(?:(?:полностью|целиком)[\s-]*)?(?:выполнен[а-яё]*|сделан[а-яё]*|разработан[а-яё]*)?[\s-]*по[\s-]*(?:(?:индивидуальн|авторск|специальн)[а-яё]*[\s-]*)*дизайн[\s-]*проект[а-яё]*|(?:ремонт|отделк[а-яё]*|интерьер[а-яё]*)[\s-]*от[\s-]*(?:известн[а-яё]*[\s-]*)?дизайнер[а-яё]*|(?:^|[^а-яё])реализован[а-яё]*[\s-]*дизайн[\s-]*проект[а-яё]*|(?:^|[^а-яё])(?:отделк|ремонт|интерьер)[а-яё]*[\s:-]*(?:выполнен[а-яё]*[\s:-]*)?дизайнерск[а-яё]*/],
    [FIN.euro, /(?:^|[^а-яё])евро[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])евро[\s-]*отделк[а-яё]*|(?:^|[^а-яё])евростандарт[а-яё]*|ремонт[а-яё]*[\s-]*в[\s-]*евро[\s-]*стиле|(?:^|[^а-яё])(?:отделк|ремонт)[а-яё]*[\s:-]*евро[а-яё]*/],
    [FIN.prefine, /white[\s-]*box|(?:^|[^а-яё])(?:вайт|уайт)[\s-]*бокс[а-яё]*|(?:^|[^а-яё])пред[\s-]*чистов[а-яё]*|(?:^|[^а-яё])под[\s-]*чистов[а-яё]*|(?:^|[^а-яё])улучшенн[а-яё]*[\s-]*чернов[а-яё]*|(?:^|[^а-яё])(?:отделк|ремонт)[а-яё]*[\s:-]*(?:предчистов[а-яё]*|white[\s-]*box)/],
    [FIN.rough, /(?:^|[^а-яё])чернов[а-яё]*[\s-]*(?:отделк|состоян|вариант|вид)[а-яё]*|(?:^|[^а-яё])чернов(?:ая|ой|ую|ое)(?![а-яё])|(?:^|[^а-яё])(?:с|со)[\s-]*чернов[а-яё]*|(?:^|[^а-яё])(?:отделк|ремонт)[а-яё]*[\s:-]*чернов[а-яё]*/],
    [FIN.none, /(?:^|[^а-яё])без[\s-]*(?:как[а-яё]*[\s-]*либо[\s-]*|всяк[а-яё]*[\s-]*)?(?:[а-яё]+(?:ой|ей|ий|ый|ая|ое|ых|ым)[\s-]+){0,2}отделк[а-яё]*|(?:^|[^а-яё])нет[\s-]*отделк[а-яё]*|отделк[а-яё]*[\s-]*(?:полностью[\s-]*)?отсутству[а-яё]*|(?:^|[^а-яё])не[\s-]*выполнен[а-яё]*[\s-]*отделк[а-яё]*|отделк[а-яё]*[\s-]*не[\s-]*(?:выполнен|сделан|производ)[а-яё]*|(?:^|[^а-яё])голы[ех][\s-]*стен[а-яё]*|(?:^|[^а-яё])бетонн[а-яё]*[\s-]*коробк[а-яё]*|отделк[а-яё]*[\s-]*не[\s-]*предусмотрен[а-яё]*|(?:^|[^а-яё])отделк[а-яё]*[\s-]*нет(?![а-яё])/],
    [FIN.fine, /(?:^|[^а-яё])чистов[а-яё]*[\s-]*отделк[а-яё]*|отделк[а-яё]*[\s-]*(?:от[\s-]*)?застройщик[а-яё]*|(?:^|[^а-яё])(?:с|со)[\s-]*(?:полной[\s-]*|готовой[\s-]*|качественной[\s-]*|финишной[\s-]*|чистовой[\s-]*)?отделк[а-яё]*|(?:^|[^а-яё])готов[а-яё]*[\s-]*отделк[а-яё]*|отделк[а-яё]*[\s-]*(?:уже[\s-]*)?(?:выполнен|сделан|готов)[а-яё]*|(?:^|[^а-яё])сдан[а-яё]*[\s-]*(?:с|со)[\s-]*отделк[а-яё]*|(?:^|[^а-яё])(?:отделк|ремонт)[а-яё]*[\s:-]*чистов[а-яё]*/],
    [FIN.some, /(?:^|[^а-яё])не[\s-]*требу[а-яё]*[\s-]*(?:[а-яё]+[\s-]+){0,2}(?:ремонт|вложен|отделк)[а-яё]*|(?:^|[^а-яё])ремонт[а-яё]*[\s-]*(?:[а-яё]+[\s-]+){0,2}не[\s-]*требу[а-яё]*|(?:^|[^а-яё])не[\s-]*нужен[\s-]*ремонт[а-яё]*/],
    [FIN.norepair, /(?:^|[^а-яё])без[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])требу[а-яё]*[\s-]*(?:[а-яё]+[\s-]+){0,2}ремонт[а-яё]*|(?:^|[^а-яё])нужен[\s-]*(?:[а-яё]+[\s-]+){0,1}ремонт[а-яё]*|(?:^|[^а-яё])нужда[а-яё]*[\s-]*в[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])под[\s-]*ремонт(?![а-яё])|(?:^|[^а-яё])убит[а-яё]*[\s-]*(?:квартир|состоян|двушк|трешк|однушк)[а-яё]*|(?:^|[^а-яё])(?:в|во)[\s-]*(?:строительн|первоначальн|плачевн|ужасн|убит|предремонтн)[а-яё]*[\s-]*состоян[а-яё]*|(?:^|[^а-яё])ремонт[а-яё]*[\s-]*(?:[а-яё]+[\s-]+){0,2}не[\s-]*(?:было|делал|начат|производ|провод|дела)[а-яё]*|(?:^|[^а-яё])(?:никогда[\s-]*)?не[\s-]*(?:делал|производил|проводил)[а-яё]*[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])(?:бабушкин|дедушкин|советск)[а-яё]*[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])требует[\s-]*вложени[а-яё]*/],
    [FIN.cosmetic, /(?:^|[^а-яё])косметич[а-яё]*|(?:^|[^а-яё])космет(?![а-яё])[\s-]*ремонт|(?:^|[^а-яё])(?:в[\s-]*)?(?:жило[а-яё]*|хорош[а-яё]*|отличн[а-яё]*|нормальн[а-яё]*|приличн[а-яё]*|достойн[а-яё]*|ухожен[а-яё]*)[\s-]*состоян[а-яё]*|(?:^|[^а-яё])(?:сделан|выполнен|произведен|проведен)[а-яё]*[\s-]*(?:[а-яё]+[\s-]+){0,2}ремонт[а-яё]*|(?:^|[^а-яё])после[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])(?:свеж|недавн|нов|аккуратн|добротн|качественн|современн|легк|хорош|отличн|приличн|достойн)[а-яё]*[\s-]*ремонт[а-яё]*|(?:^|[^а-яё])ремонт[а-яё]*[\s-]*(?:сделан|выполнен)[а-яё]*|(?:^|[^а-яё])(?:отделк|ремонт)[а-яё]*[\s:-]*косметическ[а-яё]*/],
    [FIN.turnkey, /(?:^|[^а-яё])под[\s-]*ключ(?![а-яё])|(?:^|[^а-яё])(?:с|со)[\s-]*(?:всей[\s-]*|полной[\s-]*|новой[\s-]*)?мебел[а-яё]*|(?:^|[^а-яё])меблирован[а-яё]*|(?:^|[^а-яё])(?:с|со)[\s-]*(?:быт[а-яё]*[\s-]*)?техник(?:а|и|е|у|ой)?(?![а-яё])|(?:^|[^а-яё])(?:вся[\s-]*)?мебел[а-яё]*[\s-]*(?:и[\s-]*техник[а-яё]*[\s-]*)?оста(?:ет|ю)[а-яё]*|(?:^|[^а-яё])оста(?:ет|ю)[а-яё]*[\s-]*(?:вся[\s-]*)?мебел[а-яё]*|(?:^|[^а-яё])полностью[\s-]*обставлен[а-яё]*/],
    [FIN.some, /(?:^|[^а-яё])(?:можно[\s-]*(?:сразу[\s-]*)?(?:жить|заезжать|въезжать|заселяться)|(?:за|в)езжай[\s-]*и[\s-]*живи|готов[а-яё]*[\s-]*к[\s-]*(?:заселени|проживани)[а-яё]*)/],
    [FIN.some, /(?:^|[^а-яё])ремонт[а-яё]*|(?:^|[^а-яё])отремонтирован[а-яё]*|(?:^|[^а-яё])(?:с|со)[\s-]*отделк[а-яё]*/],
  ];
  const FIN_DESC_MAX = 600;   // предел читаемости листа, не предел Excel (32767)
  // Пробелы схлопываем ЯВНЫМ классом, а не \s: наборы \s в V8 и в CPython не
  // совпадают (U+0085 и U+001C..U+001F пробельные только в Python, U+FEFF —
  // только в JS), и один такой символ уводил категорию в разные стороны.
  const FIN_WS = /[\u0009-\u000D\u001C-\u001F\u0020\u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+/g;
  // Мини-декодер HTML-сущностей. Именно мини и именно одинаковый в JS и в Python:
  // полноценный html.unescape() есть только в Python, и если пользоваться им,
  // один и тот же оффер получит РАЗНЫЕ категории в разных выгрузках.
  const FIN_ENT = {
    amp: "&", lt: "<", gt: ">", quot: "\"", apos: "'", nbsp: " ", shy: "",
    laquo: "\u00AB", raquo: "\u00BB", mdash: "\u2014", ndash: "\u2013",
    hellip: "\u2026", middot: "\u00B7", times: "\u00D7", deg: "\u00B0",
    lsquo: "\u2018", rsquo: "\u2019", ldquo: "\u201C", rdquo: "\u201D",
    copy: "\u00A9", reg: "\u00AE", euro: "\u20AC", rouble: "\u20BD",
  };
  function finEntities(s) {
    return s.replace(/&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,10});/g, (m, e) => {
      if (e.charAt(0) === "#") {
        const hex = e.charAt(1) === "x" || e.charAt(1) === "X";
        const cp = parseInt(hex ? e.slice(2) : e.slice(1), hex ? 16 : 10);
        if (!(cp >= 1 && cp <= 0x10FFFF) || (cp >= 0xD800 && cp <= 0xDFFF)) return m;
        return String.fromCodePoint(cp);
      }
      const v = FIN_ENT[e.toLowerCase()];
      return v === undefined ? m : v;
    });
  }
  // Текст объявления целиком. Подтверждённое поле — только description (строка на
  // верхнем уровне оффера); title у квартир обычно пустой, но у карточек из
  // DOM-фолбэка это единственный текст.
  function descriptionOf(o) {
    let d = o.description;
    if (d && typeof d === "object") d = d.text || d.value;
    if (typeof d !== "string" || !d.trim()) d = typeof o.title === "string" ? o.title : "";
    if (!d) return "";
    return finEntities(String(d).replace(/<[^>]+>/g, " "))
      .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, "")   // запрещены в XML
      .replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/g, "")           // одинокий суррогат
      .replace(/(^|[^\uD800-\uDBFF])([\uDC00-\uDFFF])/g, "$1")      // ...и хвостовой
      .replace(/[\u00AD\u200B\u200C\u200D\uFEFF]/g, "")            // мягкий перенос, zero-width
      .replace(FIN_WS, " ").replace(/^ | $/g, "");
  }
  // Обрезаем ТОЛЬКО для ячейки. Классификация идёт по полному тексту: признак
  // отделки часто стоит в конце объявления, и на обрезанном тексте категория
  // либо теряется, либо переворачивается на противоположную.
  // Режем по кодовым ТОЧКАМ — slice(0,600) разрубил бы суррогатную пару, а
  // одинокий суррогат делает XML невалидным, и Excel не откроет книгу целиком.
  function clipDesc(d) {
    if (!d) return d;
    const cp = Array.from(d);
    return cp.length > FIN_DESC_MAX ? cp.slice(0, FIN_DESC_MAX).join("") + "\u2026" : d;
  }
  // Значение поля отделки: разворачиваем dict на один уровень и приводим к строке.
  // Сложное значение считаем отсутствующим — иначе поиск по словарю падает.
  function fieldValue(v) {
    if (v && typeof v === "object" && !Array.isArray(v)) v = v.type || v.value;
    if (v == null || typeof v === "object") return null;
    return typeof v === "string" ? (v.trim() || null) : String(v);
  }
  function finNorm(t) {
    return String(t == null ? "" : t).toLowerCase()
      .replace(/\u0451/g, "\u0435")                              // ё -> е
      // Эмодзи: в JS это ДВА code unit, в Python — один. Окна {0,n} в стоп-контекстах
      // считают единицы движка, поэтому без выпиливания астральных символов один и
      // тот же текст даёт разные категории в JS и в Python.
      .replace(/[\uD800-\uDFFF]/g, " ")
      .replace(/[\u00AD\u200B\u200C\u200D\uFEFF]/g, "")          // мягкий перенос, zero-width
      .replace(/[\u2010-\u2015\u2212]/g, "-")                     // типографские тире -> дефис
      .replace(FIN_WS, " ").replace(/^ | $/g, "");
  }
  function finishFromText(t) {
    if (!t) return null;
    let s = finNorm(t);
    for (const rx of FIN_STOPS) s = s.replace(rx, " ");
    s = s.replace(FIN_WS, " ");
    for (const [label, rx] of FIN_RULES) if (rx.test(s)) return label;
    return null;
  }
  // desc — уже подготовленное descriptionOf(o); передаётся, чтобы не чистить
  // один и тот же текст дважды на каждый лот
  function finishOf(o, desc) {
    const rt = fieldValue(o.repairType), dc = fieldValue(o.decoration);
    if (rt && FIELD_FIN[rt]) return { fin: FIELD_FIN[rt], src: "Циан-поле" };
    if (dc && FIELD_FIN[dc]) return { fin: FIELD_FIN[dc], src: "Циан-поле" };
    const ft = finishFromText(desc === undefined ? descriptionOf(o) : desc);
    if (ft) return { fin: ft, src: "из описания" };
    if (rt || dc) return { fin: String(rt || dc), src: "Циан-поле" };   // словарь отстал от Циан
    return { fin: null, src: "" };
  }
  // ===== FIN-BLOCK-END =======================================================
  function pubDate(o) { const ts = o.addedTimestamp || o.creationTimestamp; if (ts) { const d = new Date(ts * 1000); if (!isNaN(d)) return d; } if (o.creationDate) { const d = new Date(o.creationDate); if (!isNaN(d)) return d; } return null; }
  const updDate = (o) => { const s = o.editDate || o.updatedAt; if (!s) return null; const d = new Date(s); return isNaN(d) ? null : d; };
  const fmtDate = (d) => d ? `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}.${d.getFullYear()}` : "";
  const offerUrl = (o) => o.fullUrl || ((o.cianId || o.id) ? `https://www.cian.ru/sale/flat/${o.cianId || o.id}/` : null);
  const numOr = (v) => { const n = parseFloat(String(v).replace(",", ".")); return isNaN(n) ? null : n; };
  const _MAT = { brick: "кирпич", monolith: "монолит", monolithBrick: "монолит-кирпич", panel: "панель", block: "блок", wood: "дерево", stalin: "сталинский", old: "старый фонд", boards: "щитовой" };
  function metroOf(o) {
    const u = dig(o, "geo.undergrounds");
    if (Array.isArray(u) && u.length) {
      // ближайшее метро: минимальное время в пути (при равенстве — пешком),
      // т.к. порядок в массиве у Циан не всегда «ближайшее первым».
      const m = u.slice().sort((a, b) => {
        const ta = a && a.time != null ? a.time : 1e9, tb = b && b.time != null ? b.time : 1e9;
        if (ta !== tb) return ta - tb;
        return (b && b.transportType === "walk" ? 1 : 0) - (a && a.transportType === "walk" ? 1 : 0);
      })[0];
      return { name: m && m.name || null, time: m && (m.time != null ? m.time : null), foot: m && m.transportType === "walk" };
    }
    return { name: null, time: null };
  }
  function normalize(o) {
    const area = areaOf(o), price = priceOf(o), pub = pubDate(o);
    const addedTs = pub ? Math.floor(pub.getTime() / 1000) : null;   // сырой unix для анализа экспозиции
    const m = metroOf(o);
    const mat = dig(o, "building.materialType");
    const desc = descriptionOf(o);          // полный текст — для классификации
    const fo = finishOf(o, desc);
    return {
      cianId: o.cianId || o.id || null, url: offerUrl(o), category: categoryOf(o),
      area, floor: o.floorNumber != null ? o.floorNumber : null,
      floors: dig(o, "building.floorsCount") || o.floorsCount || null, building: buildingOf(o),
      livingArea: numOr(o.livingArea), kitchenArea: numOr(o.kitchenArea),
      buildYear: dig(o, "building.buildYear") || null, material: mat ? (_MAT[mat] || mat) : null,
      metro: m.name, metroTime: m.time, addr: dig(o, "geo.userInput") || null,
      seller_type: sellerType(o), seller_name: sellerName(o),
      decoration: fo.fin, finishSrc: fo.src, description: clipDesc(desc),
      price, ppm: price && area ? Math.round(price / area) : null,
      published: pub ? fmtDate(pub) : "", exposure: pub ? Math.floor((Date.now() - pub.getTime()) / 86400000) : "",
      updated: updDate(o) ? fmtDate(updDate(o)) : "", addedTs,
    };
  }

  // ===== Реальный срок экспозиции (учёт сбросов даты Циан) ===================
  // Циан сбрасывает дату подачи при переподаче. Чтобы узнать РЕАЛЬНЫЙ срок:
  //  (1) дубли в текущей выдаче (одна квартира у нескольких продавцов) — берём
  //      самую раннюю дату из группы;
  //  (2) история между запусками — храним минимальную дату, когда-либо виденную;
  //      сдвиг даты вперёд её не уменьшает.
  const HKEY = "cianExcelHistory_v1";
  function loadHistory() {
    try { const s = localStorage.getItem(HKEY); const h = s ? JSON.parse(s) : null; return h && h.flats ? h : { flats: {} }; }
    catch (e) { return { flats: {} }; }
  }
  function saveHistory(h) {
    try {
      const cut = Math.floor(Date.now() / 1000) - 400 * 86400;  // чистим квартиры, не виденные >400 дней
      for (const k in h.flats) if ((h.flats[k].lastSeen || 0) < cut) delete h.flats[k];
      localStorage.setItem(HKEY, JSON.stringify(h));
    } catch (e) { /* ignore */ }
  }
  // отпечаток физической квартиры (переживает переподачу/смену cianId).
  // Корпус ИЛИ адрес — чтобы на поиске по карте (без корпуса) не склеивать разные дома.
  function fpOf(r, jkId) {
    const loc = (r.building || r.addr || "").toString().toLowerCase().replace(/[«»"'`.,\s]/g, "");
    const a = r.area != null ? Number(r.area).toFixed(1) : "?";
    return [jkId || "?", loc, r.floor != null ? r.floor : "?", a, r.category || "?"].join("|");
  }
  function enrichExposure(rows, jkId) {
    const now = Math.floor(Date.now() / 1000), day = 86400;
    // (1) минимум даты/цены по дублям в текущей выгрузке (для срока и разброса цен)
    const gmin = {}, gcnt = {}, gPrices = {};
    rows.forEach((r) => {
      const fp = fpOf(r, jkId); gcnt[fp] = (gcnt[fp] || 0) + 1;
      if (r.addedTs) gmin[fp] = gmin[fp] ? Math.min(gmin[fp], r.addedTs) : r.addedTs;
      if (r.price != null) (gPrices[fp] = gPrices[fp] || []).push({ price: r.price, seller_name: r.seller_name, seller_type: r.seller_type, url: r.url });
    });
    // (2) история + динамика цены между выгрузками
    const hist = loadHistory();
    rows.forEach((r) => {
      const fp = fpOf(r, jkId);
      let h = hist.flats[fp]; if (!h) h = hist.flats[fp] = { firstSeen: now, minAdded: null, addeds: [], cianIds: [], priceLog: [] };
      if (!h.priceLog) h.priceLog = [];   // обратная совместимость со старой историей (до v2.9)
      if (r.addedTs) { h.minAdded = h.minAdded ? Math.min(h.minAdded, r.addedTs) : r.addedTs; if (h.addeds.indexOf(r.addedTs) < 0) h.addeds.push(r.addedTs); }
      if (r.cianId && h.cianIds.indexOf(r.cianId) < 0) h.cianIds.push(r.cianId);
      if (r.price != null) {
        const first = h.priceLog[0], last = h.priceLog[h.priceLog.length - 1];
        r.priceDeltaFirstPct = first && first.price ? Math.round((r.price / first.price - 1) * 1000) / 10 : null;
        r.priceDeltaLastRunPct = last && last.price ? Math.round((r.price / last.price - 1) * 1000) / 10 : null;
        h.priceLog.push({ ts: now, price: r.price, ppm: r.ppm });
        if (h.priceLog.length > 12) h.priceLog.shift();       // не растим историю бесконечно
      }
      h.lastSeen = now;
    });
    // расчёт срока экспозиции + разброс цен между продавцами одной квартиры
    rows.forEach((r) => {
      const fp = fpOf(r, jkId), h = hist.flats[fp];
      const cand = [now]; if (r.addedTs) cand.push(r.addedTs);
      if (gmin[fp]) cand.push(gmin[fp]); if (h.minAdded) cand.push(h.minAdded); if (h.firstSeen) cand.push(h.firstSeen);
      const origin = Math.min.apply(null, cand);
      r.realExposure = Math.max(0, Math.floor((now - origin) / day));
      r.firstDate = fmtDate(new Date(origin * 1000));
      r.republish = Math.max(0, (h.addeds.length || 1) - 1);   // сколько разных дат подачи видели
      r.dupNow = gcnt[fp] || 1;                                 // дублей в текущей выдаче
      r.reset = !!(r.addedTs && (r.addedTs - origin > 21 * day)); // дата Циан сильно «свежее» реальной
      const grp = gPrices[fp];
      if (grp && grp.length > 1) {
        const prices = grp.map((g) => g.price), lo = Math.min(...prices), hi = Math.max(...prices);
        if (hi > lo) {
          r.dupSpreadAbs = hi - lo; r.dupMinPrice = lo; r.dupMaxPrice = hi;
          r.dupSpreadPct = Math.round((hi / lo - 1) * 1000) / 10;
          const sorted = grp.slice().sort((a, b) => a.price - b.price);
          const cheapest = sorted[0], pricier = sorted[sorted.length - 1];
          r.dupCheapestUrl = cheapest.url; r.dupCheapestSeller = cheapest.seller_name || cheapest.seller_type || "";
          r.dupPricierSeller = pricier.seller_name || pricier.seller_type || "";
        }
      }
    });
    saveHistory(hist);
    return { resets: rows.filter((r) => r.reset).length, withHistory: Object.keys(hist.flats).length };
  }

  // ===== Снимок между запусками: динамика лотов (новые/пропали/подешевели) ===
  const SKEY = "cianExcelSnapshot_v1";
  function loadSnapshots() {
    try { const s = localStorage.getItem(SKEY); const d = s ? JSON.parse(s) : null; return d && d.subjects ? d : { subjects: {} }; }
    catch (e) { return { subjects: {} }; }
  }
  function saveSnapshots(d) {
    try {
      const cut = Math.floor(Date.now() / 1000) - 400 * 86400;
      for (const k in d.subjects) if ((d.subjects[k].ts || 0) < cut) delete d.subjects[k];
      localStorage.setItem(SKEY, JSON.stringify(d));
    } catch (e) { /* ignore */ }
  }
  // ключ снимка: для ЖК — стабильный ID; для выборки по фильтрам — по slug
  // (менее надёжно при смене фильтра, но лучше, чем не сравнивать вовсе).
  const subjSnapshotKey = (subj) => subj.id ? ("jk:" + subj.id) : ("f:" + subj.slug);
  // Сравнивает текущий набор лотов с сохранённым снимком ПРОШЛОГО запуска по
  // тому же ЖК/выборке: новые лоты, пропавшие (сняты с продажи/проданы), и
  // заметно подешевевшие/подорожавшие. Обновляет снимок для следующего раза.
  function computeChanges(subj, rows) {
    const d = loadSnapshots(), key = subjSnapshotKey(subj), now = Math.floor(Date.now() / 1000);
    const prev = d.subjects[key];
    const curFps = new Map();
    rows.forEach((r) => { curFps.set(fpOf(r, subj.id), r); });
    let appeared = [], vanished = [], cheaper = [], pricier = [], hasPrev = false;
    if (prev && prev.byFp) {
      hasPrev = true;
      curFps.forEach((r, fp) => {
        if (!prev.byFp[fp]) appeared.push(r);
        else {
          const p0 = prev.byFp[fp].price;
          if (p0 != null && r.price != null && p0 !== r.price) {
            const pct = Math.round((r.price / p0 - 1) * 1000) / 10;
            (pct < 0 ? cheaper : pricier).push({ r, pct, from: p0 });
          }
        }
      });
      Object.keys(prev.byFp).forEach((fp) => { if (!curFps.has(fp)) vanished.push(prev.byFp[fp]); });
      cheaper.sort((a, b) => a.pct - b.pct); pricier.sort((a, b) => b.pct - a.pct);
    }
    const byFp = {};
    curFps.forEach((r, fp) => { byFp[fp] = { price: r.price, ppm: r.ppm, category: r.category, building: r.building, url: r.url, floor: r.floor }; });
    d.subjects[key] = { ts: now, byFp };
    saveSnapshots(d);
    return { hasPrev, appeared, vanished, cheaper, pricier };
  }

  // ===== Экспорт/импорт бэкапа истории (реальный срок, цены) + снимков =======
  // Единственная страховка от потери накопленных за недели/месяцы данных при
  // переустановке расширения/смене машины — всё это живёт только в localStorage.
  const BACKUP_VERSION = 1;
  function exportBackupData() {
    return { version: BACKUP_VERSION, exportedAt: new Date().toISOString(), history: loadHistory(), snapshots: loadSnapshots() };
  }
  // Честный field-level merge, а не перезапись: если запись есть и там, и там,
  // берём самую раннюю firstSeen/minAdded, объединяем addeds/cianIds/priceLog —
  // иначе импорт с другой машины мог бы затереть более точные локальные данные.
  function mergeHistoryFlats(a, b) {
    const out = {}, keys = new Set([...Object.keys(a.flats || {}), ...Object.keys(b.flats || {})]);
    keys.forEach((k) => {
      const x = (a.flats || {})[k], y = (b.flats || {})[k];
      if (x && !y) { out[k] = x; return; }
      if (y && !x) { out[k] = y; return; }
      const addeds = Array.from(new Set([...(x.addeds || []), ...(y.addeds || [])])).sort((p, q) => p - q);
      const cianIds = Array.from(new Set([...(x.cianIds || []), ...(y.cianIds || [])]));
      const priceLog = [...(x.priceLog || []), ...(y.priceLog || [])]
        .filter((e, i, arr) => arr.findIndex((e2) => e2.ts === e.ts) === i)
        .sort((p, q) => p.ts - q.ts).slice(-12);
      out[k] = {
        firstSeen: Math.min(x.firstSeen || Infinity, y.firstSeen || Infinity),
        minAdded: x.minAdded != null && y.minAdded != null ? Math.min(x.minAdded, y.minAdded) : (x.minAdded != null ? x.minAdded : y.minAdded),
        lastSeen: Math.max(x.lastSeen || 0, y.lastSeen || 0),
        addeds, cianIds, priceLog,
      };
    });
    return { flats: out };
  }
  // Снимки — точка во времени; построчный мерж бессмысленен, берём более свежий по каждому ЖК/выборке.
  function mergeSnapshots(a, b) {
    const out = {}, keys = new Set([...Object.keys(a.subjects || {}), ...Object.keys(b.subjects || {})]);
    keys.forEach((k) => {
      const x = (a.subjects || {})[k], y = (b.subjects || {})[k];
      out[k] = !x || (y && y.ts > x.ts) ? y : x;
    });
    return { subjects: out };
  }
  function importBackupData(text) {
    let data;
    // Бэкапы, сделанные до перехода на .xlsx, начинаются с BOM: тогдашний
    // download() добавлял его всем файлам. Сейчас не добавляет, но старые
    // бэкапы обязаны продолжать импортироваться.
    try { data = JSON.parse(String(text).replace(/^﻿/, "")); } catch (e) { throw new Error("файл повреждён или это не JSON"); }
    if (!data || typeof data !== "object" || !(data.history || data.snapshots)) throw new Error("не похоже на бэкап этого расширения (нет history/snapshots)");
    const mergedHist = data.history && data.history.flats ? mergeHistoryFlats(loadHistory(), data.history) : loadHistory();
    const mergedSnap = data.snapshots && data.snapshots.subjects ? mergeSnapshots(loadSnapshots(), data.snapshots) : loadSnapshots();
    saveHistory(mergedHist); saveSnapshots(mergedSnap);
    return { flats: Object.keys(mergedHist.flats || {}).length, subjects: Object.keys(mergedSnap.subjects || {}).length };
  }

  // ---------- генерация Excel (SpreadsheetML 2003) ----------
  // XML 1.0 запрещает управляющие символы и одинокие суррогаты НА УРОВНЕ ГРАММАТИКИ:
  // закодировать их нельзя даже как &#x1F;, поэтому вырезаем. Один такой символ,
  // просочившийся из описания или из названия ЖК, делает всю книгу нечитаемой.
  // ===== XLSX-BLOCK-START ===================================================
  // СБОРКА КНИГИ EXCEL: настоящий .xlsx (zip + OOXML), без библиотек.
  // Один и тот же блок лежит в extension/content.js и в cian_browser.js.
  // Правите один — правьте оба; расхождение ловит tests/check_export.mjs.
  //
  // Раньше здесь собирался SpreadsheetML 2003 с расширением .xls — Excel на
  // КАЖДОМ открытии показывал «формат файла не соответствует расширению».
  // Кроме предупреждения, тот формат не умеет условного форматирования, поэтому
  // тепловую карту ₽/м² приходилось запекать в стили ячеек: цвет переставал
  // быть функцией значения и не переживал ни сортировку, ни правку цены.
  //
  // Строители листов (dataSheet/summarySheet/...) про формат не знают: они
  // накапливают ДЕРЕВО строк, а превращает его в файл buildXlsxBlob().

  /* ═══════════════════ 0. Экранирование и примитивы XML ═══════════════════ */

  // XML 1.0 запрещает управляющие символы и одинокие суррогаты НА УРОВНЕ
  // ГРАММАТИКИ: закодировать их нельзя даже как &#x1F;, поэтому вырезаем.
  // Один такой символ из описания или названия ЖК делает всю книгу нечитаемой,
  // причём Excel скажет только «обнаружено неисправимое содержимое».
  // Эта функция — дословный перенос esc() из SpreadsheetML-слоя.
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g, "")
      .replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/g, "")
      .replace(/(^|[^\uD800-\uDBFF])([\uDC00-\uDFFF])/g, "$1")
      .replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // Число в текст для <v>. Экспоненциальная запись (1e-7, 1.5e+21) в OOXML
  // формально допустима, но старые сборки Excel и LibreOffice её местами
  // читают как текст — разворачиваем в обычную десятичную.
  function numStr(n) {
    const x = Number(n);
    if (!Number.isFinite(x)) return null;               // NaN/Infinity в книге = «восстановление файла»
    let s = String(x);
    if (s.indexOf("e") >= 0 || s.indexOf("E") >= 0) {
      s = x.toFixed(20).replace(/0+$/, "").replace(/\.$/, "");
      if (s === "" || s === "-") s = "0";
    }
    return s;
  }

  // Excel хранит цвета как ARGB. Альфу всегда пишем FF: полупрозрачную заливку
  // Excel всё равно не покажет, а «AARRGGBB» с другой альфой ломает сравнение цветов.
  function argb(hex) {
    if (!hex) return null;
    let h = String(hex).replace("#", "").toUpperCase();
    if (h.length === 8) h = h.slice(2);                 // пришло уже с альфой — отбрасываем
    if (!/^[0-9A-F]{6}$/.test(h)) return null;
    return "FF" + h;
  }

  /* ═══════════════════ 1. Адресация A1 ═══════════════════ */

  // 1 -> A, 27 -> AA. Колонки в OOXML нумеруются с 1.
  function colName(col) {
    let s = "";
    let c = col;
    while (c > 0) { const r = (c - 1) % 26; s = String.fromCharCode(65 + r) + s; c = (c - 1 - r) / 26; }
    return s || "A";
  }
  const a1 = (col, row) => colName(col) + row;
  const rangeA1 = (c1, r1, c2, r2) => a1(c1, r1) + ":" + a1(c2, r2);

  /* ═══════════════════ 2. CRC32 ═══════════════════ */

  // Таблица считается ОДИН раз на загрузку страницы. Побитовый расчёт на лету
  // для книги в несколько мегабайт — это десятки миллионов итераций и заметная
  // пауза в UI-потоке контент-скрипта.
  const CRC_TABLE = (function () {
    const t = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
      t[n] = c;
    }
    return t;
  })();

  function crc32(buf) {
    let c = -1;
    for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
    return (c ^ -1) >>> 0;
  }

  /* ═══════════════════ 3. ZIP-контейнер ═══════════════════ */

  // Дата в записях зафиксирована (2020-01-01 00:00 в формате DOS). Причина не
  // косметическая: при Date.now() две выгрузки одних и тех же данных дают
  // побайтово разные файлы, и снимок-эталон в tests/ перестаёт быть сравнимым.
  const DOS_DATE = ((2020 - 1980) << 9) | (1 << 5) | 1;   // 0x5021
  const DOS_TIME = 0;

  // Признак «deflate-raw недоступен» кэшируем: CompressionStream бросает
  // TypeError на каждом конструировании, а частей в книге два десятка.
  let DEFLATE_OK = null;

  const utf8 = (s) => new TextEncoder().encode(s);

  // deflate-raw появился только в Chrome 103. Расширение ставят и в старые
  // Chromium-сборки, поэтому недоступность — не ошибка, а переход на метод 0
  // (store): книга станет в 5-10 раз больше, но останется валидным zip.
  async function deflateRaw(bytes) {
    if (DEFLATE_OK === false) return null;
    if (typeof CompressionStream !== "function") { DEFLATE_OK = false; return null; }
    let cs;
    try { cs = new CompressionStream("deflate-raw"); }
    catch (e) { DEFLATE_OK = false; return null; }       // конструктор есть, а формата нет
    DEFLATE_OK = true;
    try {
      const writer = cs.writable.getWriter();
      // ВАЖНО: не await-ить запись ДО начала чтения. Поток отдаёт обратное
      // давление, writer.write() на большом буфере зависнет навсегда, если
      // никто не читает readable. Поэтому запись запускаем как отдельный
      // промис и сразу идём читать.
      const pumped = (async () => { await writer.write(bytes); await writer.close(); })();
      const reader = cs.readable.getReader();
      const chunks = [];
      let total = 0;
      for (;;) {
        const r = await reader.read();
        if (r.done) break;
        chunks.push(r.value); total += r.value.length;
      }
      await pumped;
      const out = new Uint8Array(total);
      let o = 0;
      for (const ch of chunks) { out.set(ch, o); o += ch.length; }
      return out;
    } catch (e) {
      DEFLATE_OK = false;                                // поток сломался на полпути — дальше только store
      return null;
    }
  }

  // Простой растущий буфер: собирать zip конкатенацией Uint8Array дорого
  // (O(n²) копирований), а точный размер заранее неизвестен из-за сжатия.
  function ByteSink() {
    this.buf = new Uint8Array(1 << 16);
    this.len = 0;
  }
  ByteSink.prototype._need = function (n) {
    if (this.len + n <= this.buf.length) return;
    let cap = this.buf.length;
    while (cap < this.len + n) cap *= 2;
    const nb = new Uint8Array(cap);
    nb.set(this.buf.subarray(0, this.len));
    this.buf = nb;
  };
  ByteSink.prototype.u8 = function (v) { this._need(1); this.buf[this.len++] = v & 0xff; };
  ByteSink.prototype.u16 = function (v) { this._need(2); this.buf[this.len++] = v & 0xff; this.buf[this.len++] = (v >>> 8) & 0xff; };
  ByteSink.prototype.u32 = function (v) { this._need(4); this.buf[this.len++] = v & 0xff; this.buf[this.len++] = (v >>> 8) & 0xff; this.buf[this.len++] = (v >>> 16) & 0xff; this.buf[this.len++] = (v >>> 24) & 0xff; };
  ByteSink.prototype.bytes = function (b) { this._need(b.length); this.buf.set(b, this.len); this.len += b.length; };
  ByteSink.prototype.done = function () { return this.buf.subarray(0, this.len); };

  /**
   * Собирает zip из Map<имя, строка|Uint8Array>.
   * Имена частей OOXML — чистый ASCII, поэтому флаг UTF-8 в именах (бит 11)
   * не ставим. Если когда-нибудь появится часть с кириллицей в имени, флаг
   * ставить ОБЯЗАТЕЛЬНО, иначе распаковщики прочитают имя в cp866.
   */
  async function zipParts(parts) {
    const sink = new ByteSink();
    const central = [];
    for (const [name, content] of parts) {
      const raw = typeof content === "string" ? utf8(content) : content;
      const nameBytes = utf8(name);
      const crc = crc32(raw);
      let data = await deflateRaw(raw);
      let method = 8;
      // Сжатие «в плюс» бывает на крошечных частях (_rels/.rels ~ 300 байт):
      // заголовок deflate перевешивает выигрыш. Тогда честнее store.
      if (!data || data.length >= raw.length) { data = raw; method = 0; }
      const offset = sink.len;

      sink.u32(0x04034b50);          // локальный заголовок
      sink.u16(20);                  // версия для распаковки: 2.0 = deflate
      sink.u16(0);                   // флаги: без шифрования, без data descriptor, имена ASCII
      sink.u16(method);
      sink.u16(DOS_TIME); sink.u16(DOS_DATE);
      sink.u32(crc); sink.u32(data.length); sink.u32(raw.length);
      sink.u16(nameBytes.length); sink.u16(0);
      sink.bytes(nameBytes);
      sink.bytes(data);

      central.push({ name: nameBytes, crc, csize: data.length, usize: raw.length, method, offset });
    }

    const cdStart = sink.len;
    for (const e of central) {
      sink.u32(0x02014b50);          // запись центрального каталога
      sink.u16(20);                  // version made by (MS-DOS/FAT, 2.0)
      sink.u16(20);                  // version needed
      sink.u16(0);
      sink.u16(e.method);
      sink.u16(DOS_TIME); sink.u16(DOS_DATE);
      sink.u32(e.crc); sink.u32(e.csize); sink.u32(e.usize);
      sink.u16(e.name.length); sink.u16(0); sink.u16(0);
      sink.u16(0);                   // disk number start
      sink.u16(0);                   // internal attrs
      sink.u32(0);                   // external attrs
      sink.u32(e.offset);            // смещение локального заголовка
      sink.bytes(e.name);
    }
    const cdSize = sink.len - cdStart;

    sink.u32(0x06054b50);            // EOCD
    sink.u16(0); sink.u16(0);
    sink.u16(central.length); sink.u16(central.length);
    sink.u32(cdSize); sink.u32(cdStart);
    sink.u16(0);                     // без комментария

    return sink.done();
  }

  /* ═══════════════════ 4. Стили ═══════════════════ */

  // Палитра тепловой карты ₽/м² — та же, что была в SpreadsheetML (content.js:828).
  // Это стандартная трёхцветная шкала Excel «зелёный-жёлтый-красный»,
  // интерполированная в 9 шагов; крайние и средний цвета совпадают со
  // встроенной шкалой, поэтому colorScale из трёх точек даёт ТЕ ЖЕ оттенки.
  const HEAT = ["#63BE7B", "#86C97F", "#A9D585", "#CDE08B", "#FFEB84", "#FCC97F", "#F8A77B", "#F58368", "#F8696B"];

  // Именованные стили — те же идентификаторы, что в текущем content.js
  // (hdr/title/sub/bold/num/area/link/warn/scoreHi/scoreLo/pgood/pbad/mono/h1..h9),
  // чтобы существующие функции листов переписывались без правки каждого s:.
  const STYLE_SPECS = {
    "":        {},                                                          // Normal
    hdr:       { bold: true, color: "FFFFFF", fill: "1F2A44", hAlign: "center", wrap: true },
    title:     { bold: true, size: 13 },
    sub:       { italic: true, color: "555555", size: 9 },
    subwrap:   { italic: true, color: "555555", size: 9, wrap: true },       // строка пояснений вместо всплывающих комментариев
    bold:      { bold: true },
    num:       { fmt: "#,##0" },
    area:      { fmt: "0.0" },
    link:      { color: "1155CC", underline: true },
    warn:      { bold: true, color: "C25400" },
    scoreHi:   { bold: true, color: "006100", fill: "C6EFCE" },
    scoreLo:   { bold: true, color: "9C0006", fill: "FFC7CE" },
    pgood:     { bold: true, color: "1D7A43" },
    pbad:      { bold: true, color: "C25400" },
    mono:      { name: "Consolas", size: 13, color: "1F2A44" },
    // Отклонение от средней стало ЧИСЛОМ (доля), иначе colorScale его не увидит.
    // Формат рисует то же, что раньше собиралось строкой: +12% / −12% / 0%.
    // «−» здесь U+2212, как в старом коде; в коде формата его обязательно
    // брать в кавычки, иначе Excel считает его знаком минуса секции.
    dev:       { fmt: '"+"0%;"−"0%;0%' },
    pct:       { fmt: "0%" },
  };
  HEAT.forEach((c, i) => { STYLE_SPECS["h" + (i + 1)] = { fmt: "#,##0", fill: c.slice(1), hAlign: "right" }; });

  // styles.xml. Порядок дочерних элементов ЖЁСТКО задан схемой:
  // numFmts, fonts, fills, borders, cellStyleXfs, cellXfs, cellStyles, dxfs.
  // Переставить местами — Excel «восстанавливает» книгу и теряет все форматы.
  function buildStyles(baseFont, baseSize) {
    const fonts = [], fills = [], numFmts = [], xfs = [];
    const fontKey = new Map(), fillKey = new Map(), fmtKey = new Map();
    const styleIndex = new Map();

    // fills[0] и fills[1] обязаны быть ровно none и gray125. Это не традиция,
    // а требование Excel: он адресует их по индексу, и если поставить туда
    // свою заливку, вся книга поедет по цветам.
    fills.push('<fill><patternFill patternType="none"/></fill>');
    fills.push('<fill><patternFill patternType="gray125"/></fill>');
    fillKey.set("none", 0);

    function regFont(sp) {
      const name = sp.name || baseFont, size = sp.size || baseSize;
      const key = [name, size, !!sp.bold, !!sp.italic, !!sp.underline, sp.color || ""].join("|");
      if (fontKey.has(key)) return fontKey.get(key);
      let x = "<font>";
      if (sp.bold) x += "<b/>";
      if (sp.italic) x += "<i/>";
      if (sp.underline) x += '<u val="single"/>';
      x += '<sz val="' + size + '"/>';
      if (sp.color) x += '<color rgb="' + argb(sp.color) + '"/>';
      x += '<name val="' + esc(name) + '"/><family val="2"/></font>';
      fonts.push(x); fontKey.set(key, fonts.length - 1);
      return fonts.length - 1;
    }
    function regFill(hex) {
      if (!hex) return 0;
      const c = argb(hex);
      if (!c) return 0;
      if (fillKey.has(c)) return fillKey.get(c);
      // Сплошную заливку ОБЫЧНОЙ ячейки Excel берёт из fgColor (bgColor там
      // «авто»). В dxf условного форматирования — наоборот, из bgColor.
      // Перепутать = невидимая заливка без всякой ошибки.
      fills.push('<fill><patternFill patternType="solid"><fgColor rgb="' + c + '"/><bgColor indexed="64"/></patternFill></fill>');
      fillKey.set(c, fills.length - 1);
      return fills.length - 1;
    }
    function regFmt(code) {
      if (!code) return 0;
      if (fmtKey.has(code)) return fmtKey.get(code);
      // Нумерация пользовательских форматов начинается со 164: всё, что ниже,
      // зарезервировано под встроенные, и переопределение молча меняет вид
      // дат и процентов по всей книге.
      const id = 164 + numFmts.length;
      numFmts.push('<numFmt numFmtId="' + id + '" formatCode="' + esc(code) + '"/>');
      fmtKey.set(code, id);
      return id;
    }

    for (const name of Object.keys(STYLE_SPECS)) {
      const sp = STYLE_SPECS[name];
      const fontId = regFont(sp), fillId = regFill(sp.fill), fmtId = regFmt(sp.fmt);
      const align = [];
      if (sp.hAlign) align.push('horizontal="' + sp.hAlign + '"');
      align.push('vertical="center"');                    // как в Default старой книги
      if (sp.wrap) align.push('wrapText="1"');
      xfs.push('<xf numFmtId="' + fmtId + '" fontId="' + fontId + '" fillId="' + fillId + '" borderId="0" xfId="0"' +
        (fmtId ? ' applyNumberFormat="1"' : "") + (fontId ? ' applyFont="1"' : "") + (fillId ? ' applyFill="1"' : "") +
        ' applyAlignment="1"><alignment ' + align.join(" ") + "/></xf>");
      styleIndex.set(name, xfs.length - 1);
    }

    const xml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
      (numFmts.length ? '<numFmts count="' + numFmts.length + '">' + numFmts.join("") + "</numFmts>" : "") +
      '<fonts count="' + fonts.length + '">' + fonts.join("") + "</fonts>" +
      '<fills count="' + fills.length + '">' + fills.join("") + "</fills>" +
      // Хотя бы один borderId=0 обязан существовать — на него ссылается каждый xf.
      '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
      '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
      '<cellXfs count="' + xfs.length + '">' + xfs.join("") + "</cellXfs>" +
      '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>' +
      '<dxfs count="0"/>' +
      '<tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>' +
      "</styleSheet>";
    return { xml, styleIndex };
  }

  /* ═══════════════════ 5. Ширины колонок ═══════════════════ */

  // SpreadsheetML задавал ширину числом «пикселей» (COLW в content.js),
  // xlsx — числом СИМВОЛОВ шрифта по умолчанию.
  //
  // Точная формула Excel — chars = (px − 5) / MDW, где MDW («максимальная
  // ширина цифры») для Calibri 11 равна 7 px, а 5 px — внутренние поля ячейки.
  // Здесь она СОЗНАТЕЛЬНО не используется: она аффинная, а не линейная, и
  //   а) ломает отношения ширин, по которым книги сравниваются в снимке-эталоне
  //      (34 px и 78 px дают 2.29 в старой книге и 2.52 по точной формуле);
  //   б) непропорционально раздувает узкие колонки (№, «Дублей»).
  // Берём чистое масштабирование px/7 и округляем до 2 знаков — визуально
  // разница меньше одного символа, зато пропорции листа сохраняются точно.
  const PX_PER_CHAR = 7;
  function pxToChars(px) {
    if (px == null) return null;
    const w = Math.round((Number(px) / PX_PER_CHAR) * 100) / 100;
    // Excel молча игнорирует width вне [0, 255] и ставит ширину по умолчанию.
    return Math.max(0.5, Math.min(255, w));
  }

  /* ═══════════════════ 6. Имена ═══════════════════ */

  // Имя листа: ≤31 символа, без []:*?/\ и без апострофов по краям, уникальное
  // без учёта регистра. Обрезка «в лоб» легко даёт два одинаковых имени —
  // тогда Excel не откроет книгу вовсе, поэтому дедуплицируем суффиксом.
  function sanitizeSheetNames(names) {
    const used = new Set(), out = [];
    names.forEach((raw, i) => {
      let n = String(raw == null ? "" : raw).replace(/[\\/?*[\]:]/g, "_").replace(/^'+|'+$/g, "").trim();
      if (!n) n = "Лист" + (i + 1);
      if (n.length > 31) n = n.slice(0, 31);
      let cand = n, k = 2;
      while (used.has(cand.toLowerCase())) {
        const suf = "_" + k++;
        cand = n.slice(0, 31 - suf.length) + suf;
      }
      used.add(cand.toLowerCase()); out.push(cand);
    });
    return out;
  }

  // Имя таблицы = определённое имя книги: без пробелов и дефисов, не с цифры,
  // уникальное. И ГЛАВНОЕ — оно не должно выглядеть как ссылка на ячейку:
  // «T1» Excel отвергает, потому что это адрес столбца T строки 1. Отсюда
  // подчёркивание в префиксе.
  function tableName(i, want, used) {
    let n = want ? String(want).replace(/[^0-9A-Za-zА-Яа-яЁё_]/g, "_") : "";
    if (!n || /^[0-9]/.test(n)) n = "Tbl_" + i;
    if (/^[A-Za-z]{1,3}[0-9]{1,7}$/.test(n)) n = "Tbl_" + n;   // похоже на адрес ячейки
    let cand = n, k = 2;
    while (used.has(cand.toLowerCase())) cand = n + "_" + k++;
    used.add(cand.toLowerCase());
    return cand;
  }

  /* ═══════════════════ 7. Лист ═══════════════════ */

  // Тип ячейки принимаем и в написании SpreadsheetML ("Number"/"String"),
  // и в коротком xlsx-написании ("n"/"s"): так вызывающий код (dataSheet и
  // компания) переписывается без правки каждой ячейки.
  function isNumeric(c) {
    const t = c.t;
    if (t === "n" || t === "Number") return true;
    if (t === "s" || t === "String" || t === "str") return false;
    return typeof c.v === "number";
  }

  /**
   * sheet: {
   *   name, rows, colWidthsPx | colWidths,
   *   freeze: {rows, cols},
   *   table: {ref} | autoFilter: "A4:AA30",
   *   condFormats: [...], landscape, fitWidth
   * }
   * Возвращает { xml, rels, tables:[{xml, path}] }.
   */
  function buildSheet(sheet, styleIndex, sheetNo, tableNames) {
    const rows = sheet.rows || [];
    let maxCol = 0;
    rows.forEach((r) => { if (r && r.length > maxCol) maxCol = r.length; });
    const widths = sheet.colWidthsPx
      ? sheet.colWidthsPx.map(pxToChars)
      : (sheet.colWidths || null);
    if (widths && widths.length > maxCol) maxCol = widths.length;
    let maxRow = Math.max(rows.length, 1);

    const rels = [];                    // {id, type, target, mode}
    const relByTarget = new Map();      // одинаковые URL -> одна связь
    const hyperlinks = [];
    const merges = [];

    /* --- таблица Excel: сначала чиним шапку, только потом сериализуем ячейки ---
     * Порядок принципиален. Имена колонок таблицы обязаны СОВПАДАТЬ с текстом
     * ячеек шапки — Excel сверяет их при открытии и «восстанавливает» книгу
     * при расхождении. Если чинить шапку после сборки sheetData, правка уже
     * никуда не попадёт: в XML уедет старый текст, а в table.xml — новый. */
    let tableRange = null, tableCols = null;
    if (sheet.table && sheet.table.ref) {
      const rg = parseRange(sheet.table.ref);
      // Таблица без единой строки данных (ref = только шапка) для Excel
      // невалидна — он «восстанавливает» книгу. Тихо откатываемся на обычный
      // автофильтр листа: пустой лист всё равно нечего фильтровать.
      if (rg && rg.r2 > rg.r1) {
        tableRange = rg;
        tableCols = [];
        const seen = new Set();
        const hdr = rows[rg.r1 - 1] = rows[rg.r1 - 1] || [];
        for (let c = rg.c1; c <= rg.c2; c++) {
          let cell = hdr[c - 1];
          let n = cell && cell.v != null && cell.v !== "" ? String(cell.v).trim() : "";
          if (!n) n = "Столбец " + (c - rg.c1 + 1);
          let cand = n, k = 2;
          while (seen.has(cand.toLowerCase())) cand = n + " (" + k++ + ")";
          seen.add(cand.toLowerCase());
          if (!cell) cell = hdr[c - 1] = { v: cand, s: "hdr" };
          else if (String(cell.v == null ? "" : cell.v).trim() !== cand) cell.v = cand;
          tableCols.push(cand);
        }
        if (rg.c2 > maxCol) maxCol = rg.c2;
      }
    }

    const sd = [];
    rows.forEach((row, ri) => {
      const r = ri + 1;
      if (!row || !row.length) return;
      const cells = [];
      for (let ci = 0; ci < row.length; ci++) {
        const c = row[ci];
        if (!c) continue;
        const col = ci + 1;
        const ref = a1(col, r);
        const sIdx = c.s != null && styleIndex.has(c.s) ? styleIndex.get(c.s) : (c.s ? 0 : 0);
        const sAttr = sIdx ? ' s="' + sIdx + '"' : "";

        // merge: число дополнительных колонок вправо (как ss:MergeAcross),
        // mergeDown — вниз. Сохранено ровно как в старом cell().
        const across = Number(c.merge || 0), down = Number(c.mergeDown || 0);
        if (across > 0 || down > 0) {
          merges.push(rangeA1(col, r, col + across, r + down));
          // dimension обязан накрывать объединение: если он меньше занятого
          // диапазона, Excel открывает книгу, но печать и «перейти к концу»
          // видят лист обрезанным.
          if (col + across > maxCol) maxCol = col + across;
          if (r + down > maxRow) maxRow = r + down;
        }

        if (c.href) {
          const key = String(c.href);
          let id = relByTarget.get(key);
          if (!id) {
            id = "rId" + (rels.length + 1);
            rels.push({ id, type: "hyperlink", target: key, external: true });
            relByTarget.set(key, id);
          }
          hyperlinks.push({ ref, id });
        }

        // Формула. Значение НЕ кэшируем: Excel посчитает сам при открытии
        // (в workbook.xml стоит calcPr fullCalcOnLoad). Кэш пришлось бы держать
        // в согласии с формулой, а разошедшийся кэш — это молча неверные числа
        // на первом же экране книги.
        if (c.f) {
          cells.push('<c r="' + ref + '"' + sAttr + "><f>" + esc(String(c.f).replace(/^=/, "")) + "</f></c>");
          continue;
        }
        const empty = c.v == null || c.v === "";
        if (empty) {
          // Пустую ячейку без стиля не пишем вовсе: она ничего не добавляет,
          // а на 27 колонок × 1000 строк это лишние сотни килобайт XML.
          if (sAttr) cells.push("<c r=\"" + ref + "\"" + sAttr + "/>");
          continue;
        }
        if (isNumeric(c)) {
          const v = numStr(c.v);
          if (v !== null) { cells.push('<c r="' + ref + '"' + sAttr + "><v>" + v + "</v></c>"); continue; }
          // NaN/Infinity (деление на ноль в средней по пустой категории) —
          // это ОТСУТСТВУЮЩЕЕ значение, а не значение. Пишем пустую ячейку:
          // «NaN» текстом в колонке цены сортируется и суммируется как мусор,
          // а <v>NaN</v> Excel считает нечитаемым содержимым.
          cells.push(sAttr ? '<c r="' + ref + '"' + sAttr + "/>" : "");
          continue;
        }
        // Строки пишем inline (t="inlineStr"), без sharedStrings — см. отчёт.
        // xml:space="preserve" обязателен: иначе Excel съедает ведущие/хвостовые
        // пробелы, а спарклайн и выравнивание описаний ими и держатся.
        cells.push('<c r="' + ref + '"' + sAttr + ' t="inlineStr"><is><t xml:space="preserve">' + esc(c.v) + "</t></is></c>");
      }
      if (cells.length) sd.push('<row r="' + r + '">' + cells.join("") + "</row>");
    });

    if (maxCol < 1) maxCol = 1;

    /* --- часть таблицы (шапка уже нормализована выше) --- */
    let tablePart = null, autoFilterRef = sheet.autoFilter || null;
    if (tableRange) {
      const tName = tableName(sheetNo, sheet.table.name, tableNames);
      const ref = rangeA1(tableRange.c1, tableRange.r1, tableRange.c2, tableRange.r2);
      tablePart = {
        name: tName,
        xml: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
          '<table xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" id="' + sheetNo +
          '" name="' + esc(tName) + '" displayName="' + esc(tName) + '" ref="' + ref + '" totalsRowShown="0">' +
          '<autoFilter ref="' + ref + '"/>' +
          '<tableColumns count="' + tableCols.length + '">' +
          tableCols.map((n, i) => '<tableColumn id="' + (i + 1) + '" name="' + esc(n) + '"/>').join("") +
          "</tableColumns>" +
          '<tableStyleInfo name="TableStyleLight1" showFirstColumn="0" showLastColumn="0" showRowStripes="1" showColumnStripes="0"/>' +
          "</table>",
      };
      // Автофильтр листа при наличии таблицы НЕ пишем: два фильтра на один
      // диапазон Excel считает конфликтом и чинит книгу.
      autoFilterRef = null;
    } else if (sheet.table && sheet.table.ref) {
      const rg = parseRange(sheet.table.ref);
      if (rg) autoFilterRef = rangeA1(rg.c1, rg.r1, rg.c2, rg.r2);
    }

    if (tablePart) rels.push({ id: "rId" + (rels.length + 1), type: "table", target: "../tables/table" + sheetNo + ".xml" });

    /* --- заморозка --- */
    let paneXml = "";
    const fz = sheet.freeze;
    if (fz && (fz.rows || fz.cols)) {
      const x = fz.cols || 0, y = fz.rows || 0;
      const tl = a1(x + 1, y + 1);
      const active = x && y ? "bottomRight" : (x ? "topRight" : "bottomLeft");
      let sel = "";
      // Excel к заморозке по обеим осям пишет три <selection>. Без них файл
      // валиден, но курсор после открытия оказывается в замороженной области
      // и первый же ввод портит шапку.
      if (x && y) sel = '<selection pane="topRight" activeCell="' + a1(x + 1, 1) + '" sqref="' + a1(x + 1, 1) + '"/>' +
        '<selection pane="bottomLeft" activeCell="' + a1(1, y + 1) + '" sqref="' + a1(1, y + 1) + '"/>';
      sel += '<selection pane="' + active + '" activeCell="' + tl + '" sqref="' + tl + '"/>';
      paneXml = '<pane' + (x ? ' xSplit="' + x + '"' : "") + (y ? ' ySplit="' + y + '"' : "") +
        ' topLeftCell="' + tl + '" activePane="' + active + '" state="frozen"/>' + sel;
    }

    /* --- условное форматирование --- */
    let cfXml = "";
    let prio = 1;
    (sheet.condFormats || []).forEach((cf) => {
      const sqref = Array.isArray(cf.sqref) ? cf.sqref.join(" ") : String(cf.sqref || "");
      if (!sqref) return;
      const pts = cf.points || [];
      // Excel поддерживает ТОЛЬКО 2 или 3 точки в colorScale. Девять цветов
      // HEAT задать напрямую нельзя — и не нужно: три точки (зелёный, жёлтый,
      // красный) Excel интерполирует ровно в те же оттенки.
      if (pts.length < 2 || pts.length > 3) return;
      // Пороги обязаны быть конечными числами и СТРОГО возрастать. Excel на
      // val="NaN" или на неубывающих порогах молча выбрасывает правило целиком:
      // колонка теряет цвет, который в старой книге был запечён в заливку, и
      // потеря никак себя не проявляет. Лучше не писать правило совсем, чем
      // писать заведомо мёртвое.
      const vals = pts.map((p) => Number(p.val));
      if (vals.some((v) => !Number.isFinite(v))) return;
      for (let k = 1; k < vals.length; k++) if (!(vals[k] > vals[k - 1])) return;
      cfXml += '<conditionalFormatting sqref="' + esc(sqref) + '"><cfRule type="colorScale" priority="' + (prio++) + '"><colorScale>' +
        pts.map((p) => '<cfvo type="' + (p.type || "num") + '"' + (p.val != null ? ' val="' + esc(p.val) + '"' : "") + "/>").join("") +
        pts.map((p) => '<color rgb="' + argb(p.color) + '"/>').join("") +
        "</colorScale></cfRule></conditionalFormatting>";
    });

    /* --- сборка листа --- */
    // Пустой <cols/> схема запрещает (нужен хотя бы один <col>), поэтому
    // блок появляется только когда есть что писать.
    const colXml = (widths || []).map((w, i) => w == null ? "" :
      '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + w + '" customWidth="1"/>').join("");
    const cols = colXml ? "<cols>" + colXml + "</cols>" : "";

    const hlXml = hyperlinks.length
      ? "<hyperlinks>" + hyperlinks.map((h) => '<hyperlink ref="' + h.ref + '" r:id="' + h.id + '"/>').join("") + "</hyperlinks>"
      : "";

    // ПОРЯДОК ДОЧЕРНИХ ЭЛЕМЕНТОВ ЛИСТА ЗАДАН СХЕМОЙ И НЕ ТЕРПИТ ПЕРЕСТАНОВОК:
    // sheetPr, dimension, sheetViews, sheetFormatPr, cols, sheetData,
    // autoFilter, mergeCells, conditionalFormatting, hyperlinks,
    // pageMargins, pageSetup, tableParts.
    // Excel не сообщает «неверный порядок» — он молча объявляет книгу
    // повреждённой и выбрасывает всё, что после первого «лишнего» элемента.
    const xml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"' +
      ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
      // fitToPage здесь, а не в pageSetup: без этого флага Excel игнорирует
      // fitToWidth и печатает лист в натуральную величину на 5 страниц вширь.
      '<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>' +
      '<dimension ref="' + rangeA1(1, 1, maxCol, maxRow) + '"/>' +
      '<sheetViews><sheetView workbookViewId="0"' + (sheetNo === 1 ? ' tabSelected="1"' : "") + ">" + paneXml + "</sheetView></sheetViews>" +
      '<sheetFormatPr defaultRowHeight="15"/>' +
      cols +
      "<sheetData>" + sd.join("") + "</sheetData>" +
      (autoFilterRef ? '<autoFilter ref="' + autoFilterRef + '"/>' : "") +
      (merges.length ? '<mergeCells count="' + merges.length + '">' + merges.map((m) => '<mergeCell ref="' + m + '"/>').join("") + "</mergeCells>" : "") +
      cfXml +
      hlXml +
      '<pageMargins left="0.3" right="0.3" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>' +
      '<pageSetup paperSize="9" orientation="' + (sheet.landscape === false ? "portrait" : "landscape") +
      '" fitToWidth="' + (sheet.fitWidth == null ? 1 : sheet.fitWidth) + '" fitToHeight="0"/>' +
      (tablePart ? '<tableParts count="1"><tablePart r:id="' + rels[rels.length - 1].id + '"/></tableParts>' : "") +
      "</worksheet>";

    const relsXml = rels.length
      ? '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
        rels.map((r) => '<Relationship Id="' + r.id + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/' +
          r.type + '" Target="' + esc(r.target) + '"' + (r.external ? ' TargetMode="External"' : "") + "/>").join("") +
        "</Relationships>"
      : null;

    return { xml, relsXml, tablePart };
  }

  // Разбор "A4:AA30" -> {c1,r1,c2,r2}. Нужен только сборщику таблиц.
  function parseRange(s) {
    const m = /^([A-Za-z]+)(\d+)(?::([A-Za-z]+)(\d+))?$/.exec(String(s || "").trim());
    if (!m) return null;
    const cn = (x) => { let n = 0; for (const ch of x.toUpperCase()) n = n * 26 + (ch.charCodeAt(0) - 64); return n; };
    const c1 = cn(m[1]), r1 = Number(m[2]);
    return { c1, r1, c2: m[3] ? cn(m[3]) : c1, r2: m[4] ? Number(m[4]) : r1 };
  }

  /* ═══════════════════ 8. Книга ═══════════════════ */

  /**
   * buildXlsxParts(book) -> Map<путь в архиве, строка XML>
   *
   * book = {
   *   font: "Calibri", fontSize: 11,
   *   sheets: [{
   *     name:        "Все_лоты",
   *     colWidthsPx: [34, 78, ...],          // как COLW; либо colWidths — уже в символах
   *     freeze:      { rows: 4, cols: 2 },
   *     table:       { ref: "A4:AA30", name: "Vse_loty" },  // настоящая таблица + автофильтр
   *     autoFilter:  "A4:AA30",              // если таблица не нужна
   *     landscape:   true, fitWidth: 1,
   *     rows: [ [ {v,t,s,href,merge,mergeDown}, ... ], ... ],
   *     condFormats: [{ sqref: "P5:P30" | ["P5:P9","P12:P14"],
   *                     points: [{type:"num",val:-0.2,color:"#63BE7B"}, ...] }]
   *   }]
   * }
   * Ячейка совместима со старым cell(): v, t ("Number"/"String" или "n"/"s"),
   * s (имя стиля), href, merge (число колонок вправо). Именно поэтому
   * dataSheet/summarySheet переписываются механически: rowXml([...]) -> rows.push([...]).
   *
   * ПОБОЧНЫЙ ЭФФЕКТ: при наличии table сборщик ПРАВИТ ячейки строки-шапки
   * (пустые и повторяющиеся заголовки), потому что Excel требует точного
   * совпадения текста шапки с именами колонок таблицы. Дерево передавать
   * одноразовое — повторная сборка из того же объекта даст тот же результат,
   * но исходные заголовки в нём уже изменены.
   */
  function buildXlsxParts(book) {
    const sheets = (book.sheets || []).filter(Boolean);
    if (!sheets.length) throw new Error("книга без листов");
    const names = sanitizeSheetNames(sheets.map((s) => s.name));
    const { xml: stylesXml, styleIndex } = buildStyles(book.font || "Calibri", book.fontSize || 11);

    const parts = new Map();
    const ctOverrides = [];
    const wbRels = [];
    const sheetEntries = [];
    const tableNames = new Set();

    sheets.forEach((sh, i) => {
      const no = i + 1;
      const built = buildSheet(sh, styleIndex, no, tableNames);
      parts.set("xl/worksheets/sheet" + no + ".xml", built.xml);
      ctOverrides.push('<Override PartName="/xl/worksheets/sheet' + no + '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>');
      if (built.relsXml) parts.set("xl/worksheets/_rels/sheet" + no + ".xml.rels", built.relsXml);
      if (built.tablePart) {
        parts.set("xl/tables/table" + no + ".xml", built.tablePart.xml);
        ctOverrides.push('<Override PartName="/xl/tables/table' + no + '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"/>');
      }
      const rid = "rId" + no;
      wbRels.push('<Relationship Id="' + rid + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' + no + '.xml"/>');
      // sheetId и r:id — разные пространства; совпадение чисел здесь случайно
      // и на него нельзя опираться при вставке листа в середину.
      sheetEntries.push('<sheet name="' + esc(names[i]) + '" sheetId="' + no + '" r:id="' + rid + '"/>');
    });

    const stylesRid = "rId" + (sheets.length + 1);
    wbRels.push('<Relationship Id="' + stylesRid + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>');

    parts.set("xl/styles.xml", stylesXml);
    ctOverrides.push('<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>');

    parts.set("xl/workbook.xml",
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"' +
      ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
      '<workbookPr/><bookViews><workbookView activeTab="0"/></bookViews>' +
      // Без этого Excel показал бы пустоту вместо формул: кэшированных
      // значений мы не пишем принципиально.
      '<calcPr calcId="191029" fullCalcOnLoad="1"/>' +
      "<sheets>" + sheetEntries.join("") + "</sheets></workbook>");
    ctOverrides.push('<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>');

    parts.set("xl/_rels/workbook.xml.rels",
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' + wbRels.join("") + "</Relationships>");

    parts.set("_rels/.rels",
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
      "</Relationships>");

    // Default для «rels» и «xml» обязателен: без него Excel не знает типа
    // ни одной части связей и отказывается открывать пакет.
    parts.set("[Content_Types].xml",
      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' +
      ctOverrides.join("") + "</Types>");

    // Порядок записей в архиве: [Content_Types].xml первым — так делает Excel,
    // и некоторые чужие читалки (в т.ч. старые версии Numbers) на это полагаются.
    const ordered = new Map();
    ordered.set("[Content_Types].xml", parts.get("[Content_Types].xml"));
    ordered.set("_rels/.rels", parts.get("_rels/.rels"));
    for (const [k, v] of parts) if (!ordered.has(k)) ordered.set(k, v);
    return ordered;
  }

  /** Готовый файл. Асинхронно, потому что CompressionStream — поток. */
  async function buildXlsxBlob(book) {
    const bytes = await zipParts(buildXlsxParts(book));
    // BOM здесь категорически недопустим (в .xls он был нужен): три байта
    // перед сигнатурой PK превращают архив в мусор для любого распаковщика.
    return new Blob([bytes], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  }

  /* ═══════════════════ 9. Мелкие помощники для вызывающего кода ═══════════ */

  // Всплывающие комментарии к заголовкам (HEADER_NOTES) в xlsx не переносим:
  // legacy-VML капризен и по-разному рисуется в разных Excel, а в LibreOffice
  // и Google Sheets часто не рисуется вовсе. Вместо них — одна строка текстом
  // под подзаголовком листа (строка 3, которая раньше была пустым разделителем,
  // поэтому нумерация строк и диапазон таблицы не меняются).
  function headerNotesLine(notes) {
    return Object.keys(notes || {})
      .map((k) => k.replace(/,\s*%$/, "") + " — " + notes[k])
      .join("; ");
  }

  // Тепловая карта ₽/м² в виде условного форматирования.
  // heatPoints() — трёхточечная шкала по КОЛОНКЕ ОТКЛОНЕНИЯ (доли: −0.2 / 0 / +0.2),
  // ровно крайние пороги HEAT_THRESH. База не зависит от того, что видно
  // на экране, поэтому «зелёный = дешевле средней» читается одинаково всегда.
  function heatPointsByDeviation(lo, hi) {
    return [
      { type: "num", val: lo == null ? -0.2 : lo, color: HEAT[0] },
      { type: "num", val: 0, color: HEAT[4] },
      { type: "num", val: hi == null ? 0.2 : hi, color: HEAT[8] },
    ];
  }
  // heatPointsByPpm() — та же шкала, но в рублях за м², чтобы красить саму
  // колонку ₽/м². Границы считаются от средней ПО КАТЕГОРИИ, а sqref
  // перечисляет строки только этой категории — так рубли остаются сравнимыми.
  function heatPointsByPpm(catMean) {
    // Средняя по категории приходит из avg(), а он отдаёт null на пустом списке:
    // категория без единой цены (например «Своб. планировка», где у всех лотов
    // нет ₽/м²) иначе дала бы три порога NaN.
    const m = Number(catMean);
    if (!Number.isFinite(m) || m <= 0) return null;
    return [
      { type: "num", val: Math.round(m * 0.8), color: HEAT[0] },
      { type: "num", val: Math.round(m), color: HEAT[4] },
      { type: "num", val: Math.round(m * 1.2), color: HEAT[8] },
    ];
  }

  // Список номеров строк -> компактный sqref («O5:O9 O12 O15:O18»).
  // Один rule на категорию вместо девяти запечённых заливок.
  function sqrefFromRows(col, rowNums) {
    const rs = rowNums.slice().sort((a, b) => a - b);
    const out = [];
    let i = 0;
    while (i < rs.length) {
      let j = i;
      while (j + 1 < rs.length && rs[j + 1] === rs[j] + 1) j++;
      out.push(i === j ? a1(col, rs[i]) : rangeA1(col, rs[i], col, rs[j]));
      i = j + 1;
    }
    return out;
  }
  // ===== XLSX-BLOCK-END =====================================================

  // row(cells) — строка книги как массив ячеек. Раньше эта функция возвращала
  // XML; теперь просто отдаёт ячейки, а имя оставлено, чтобы строители листов
  // читались как раньше.
  const row = (cells) => cells;

  // worksheet(...) — из накопленных строк делает описание листа для сборщика.
  // opts: { freezeRows, freezeCols, autoFilterRows, condFormats }.
  // autoFilterRows = число строк ДАННЫХ (без 4 строк шапки).
  function worksheet(name, cols, rows, opts) {
    if (opts === true) opts = { freezeRows: 4 };
    opts = opts || {};
    const sh = { name: name, colWidthsPx: cols, rows: rows };
    if (opts.freezeRows) {
      sh.freeze = { rows: opts.freezeRows, cols: opts.freezeCols || 0 };
      // Печатный вид ставим только там, где раньше стоял PageSetup, — он шёл
      // в одном блоке с заморозкой. У «Сводки» его не было, и добавлять не надо.
      sh.landscape = true;
      sh.fitWidth = 1;
    }
    if (opts.autoFilterRows) {
      // Настоящая таблица Excel: автофильтр, полосы, structured references.
      sh.table = { ref: rangeA1(1, 4, cols.length, 4 + opts.autoFilterRows) };
    }
    if (opts.condFormats && opts.condFormats.length) sh.condFormats = opts.condFormats;
    return sh;
  }

  const HEADERS = ["№", "Категория", "Площадь, м²", "Этаж", "Корпус / секция", "Год дома", "Материал", "Метро", "До метро, мин", "Тип продавца", "Продавец", "Отделка/ремонт", "Источник отделки", "Цена, ₽", "Цена за м², ₽", "Откл. от средней", "Индекс привлекательности", "Δ цены с 1-й выгрузки, %", "Δ цены с прошлой, %", "Дата подачи (Циан)", "Срок Циан, дн", "Реальный срок, дн", "Переподач", "Дублей", "Первая дата (оценка)", "Описание", "Ссылка"];
  const COLW = [34, 78, 72, 56, 105, 62, 86, 110, 78, 88, 140, 130, 92, 105, 92, 86, 90, 92, 88, 100, 80, 95, 72, 60, 110, 320, 68];
  // Пояснения к расчётным столбцам, у которых нет текстового объяснения на
  // самом листе (в отличие от «Откл. от средней» — та объяснена подзаголовком
  // листа и легендой в Сводке). Комментарий виден при наведении в Excel.
  const HEADER_NOTES = {
    "Индекс привлекательности": "Эвристика 0-100: ниже цена/м² к средней по комнатности + качество отделки + срок экспозиции. Не финансовая оценка.",
    "Δ цены с 1-й выгрузки, %": "Изменение цены этой квартиры с САМОЙ ПЕРВОЙ выгрузки, которую видело расширение. Пусто, если раньше не встречалась.",
    "Δ цены с прошлой, %": "Изменение цены с ПРЕДЫДУЩЕЙ выгрузки этого ЖК/выборки (см. лист «Изменения»). Пусто на первой выгрузке.",
  };

  // Тепловая карта ₽/м²: насколько лот ниже/выше средней цены за м² по его
  // категории (зелёный = дешевле/недооценён, красный = дороже/переоценён). База —
  // средняя ₽/м² по той же комнатности; если в категории <3 лотов, берём общую.
  // HEAT (9 цветов шкалы) объявлен выше, в слое сборки книги: там же он
  // превращается в точки условного форматирования colorScale.
  const HEAT_THRESH = [-0.20, -0.12, -0.06, -0.02, 0.02, 0.06, 0.12, 0.20];   // -> корзины 1..9
  function computeHeat(rows) {
    const byCat = {};
    rows.forEach((r) => { if (r.ppm != null && r.category) (byCat[r.category] = byCat[r.category] || []).push(r.ppm); });
    const meanOf = (a) => a.reduce((x, y) => x + y, 0) / a.length;
    const catMean = {}; Object.keys(byCat).forEach((c) => { catMean[c] = meanOf(byCat[c]); });
    const allPpm = rows.map((r) => r.ppm).filter((x) => x != null);
    const overall = allPpm.length ? meanOf(allPpm) : null;
    rows.forEach((r) => {
      r._heat = null; r._dev = ""; r._devBase = null; r._devNum = null;
      if (r.ppm == null) return;
      const base = (r.category && byCat[r.category] && byCat[r.category].length >= 3) ? catMean[r.category] : overall;
      if (!base) return;
      const d = r.ppm / base - 1;
      let b = 9; for (let i = 0; i < HEAT_THRESH.length; i++) { if (d < HEAT_THRESH[i]) { b = i + 1; break; } }
      r._heat = "h" + b;
      r._dev = (d >= 0 ? "+" : "−") + Math.round(Math.abs(d) * 100) + "%";
      r._devBase = Math.round(base);
      r._devNum = d;                                       // числовое отклонение — для индекса привлекательности
    });
  }

  // ===== Индекс привлекательности лота (composite score, 0-100) ==============
  // Эвристика для быстрой сортировки, НЕ финансовая оценка: половину веса даёт
  // цена за м² относительно средней по комнатности (дешевле = выше балл),
  // остальное — качество отделки и длительность экспозиции (потенциал для
  // торга). Этаж/метро сюда сознательно не включены — см. отдельные таблицы
  // «бенчмарки» в Сводке, чтобы не задваивать веса и не плодить домыслы.
  function computeScore(rows) {
    rows.forEach((r) => {
      if (r._devNum == null) { r._score = null; return; }
      const priceComp = -r._devNum * 120;
      const finRank = FIN_QUALITY_RANK[r.decoration] || 3.5;   // 3.5 = нейтрально, если отделка не определена
      const finComp = (finRank - 3.5) / 2.5 * 8;
      const expComp = Math.min(r.realExposure || 0, 180) / 180 * 10;
      r._score = Math.max(0, Math.min(100, Math.round(50 + priceComp + finComp + expComp)));
    });
  }
  function dataSheet(name, title, sub, rows) {
    // Строка 3 раньше была пустым разделителем, а пояснения к расчётным
    // столбцам висели всплывающими комментариями. В .xlsx комментарии — это
    // legacy-VML: капризный и по-разному рисуется в разных Excel. Текстом
    // надёжнее, и видно в LibreOffice и Google Sheets.
    const R = [row([{ v: title, s: "title", merge: HEADERS.length - 1 }]),
      row([{ v: sub, s: "sub", merge: HEADERS.length - 1 }]),
      row([{ v: headerNotesLine(HEADER_NOTES), s: "subwrap", merge: HEADERS.length - 1 }]),
      row(HEADERS.map((h) => ({ v: h, s: "hdr" })))];
    const N = (v, s) => ({ v, t: v != null ? "Number" : "String", s });
    rows.forEach((r, i) => {
      let floorVal = "";                                   // один столбец «этаж/этажность» -> «5/15»
      if (r.floor != null && r.floors != null) floorVal = r.floor + "/" + r.floors;
      else if (r.floor != null) floorVal = String(r.floor);
      else if (r.floors != null) floorVal = "?/" + r.floors;
      const pctCell = (v) => v == null ? {} : { v: (v > 0 ? "+" : "") + v + "%", t: "String", s: v < 0 ? "pgood" : (v > 0 ? "pbad" : null) };
      R.push(row([
        { v: i + 1, t: "Number" }, { v: r.category },
        N(r.area, "area"), { v: floorVal }, { v: r.building }, N(r.buildYear), { v: r.material },
        { v: r.metro }, N(r.metroTime),
        { v: r.seller_type }, { v: r.seller_name }, { v: r.decoration }, { v: r.finishSrc },
        N(r.price, "num"),
        { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: "num" },              // ₽/м², цвет даёт condFormats
        { v: r._devNum, t: r._devNum != null ? "Number" : "String", s: r._devNum != null ? "dev" : null },  // отклонение ЧИСЛОМ: строку colorScale не красит
        { v: r._score, t: r._score != null ? "Number" : "String", s: r._score == null ? null : (r._score >= 65 ? "scoreHi" : (r._score <= 35 ? "scoreLo" : null)) },
        pctCell(r.priceDeltaFirstPct), pctCell(r.priceDeltaLastRunPct),
        { v: r.published }, N(r.exposure),
        { v: r.realExposure, t: r.realExposure != null ? "Number" : "String", s: r.reset ? "warn" : null },
        { v: r.republish, t: "Number" }, { v: r.dupNow, t: "Number" }, { v: r.firstDate },
        { v: r.description }, r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]));
    });
    // Тепловая карта — условным форматированием, а не запечёнными заливками.
    // Группируем строки по той же базе, по которой считал computeHeat
    // (средняя по комнатности, а при малой выборке — общая), и на каждую базу
    // вешаем свою трёхточечную шкалу: рубли разных категорий несравнимы, один
    // общий colorScale по всей колонке смешал бы студии с четырёхкомнатными.
    const ppmCol = HEADERS.indexOf("Цена за м², ₽") + 1;
    const devCol = HEADERS.indexOf("Откл. от средней") + 1;
    const byBase = new Map();
    rows.forEach((r, i) => {
      if (r._devBase == null || r.ppm == null) return;
      if (!byBase.has(r._devBase)) byBase.set(r._devBase, []);
      byBase.get(r._devBase).push(i + 5);                  // 4 строки шапки
    });
    const cf = [];
    byBase.forEach((rowNums, base) => {
      const pts = heatPointsByPpm(base);
      if (pts) cf.push({ sqref: sqrefFromRows(ppmCol, rowNums), points: pts });
    });
    if (rows.length) {
      cf.push({ sqref: rangeA1(devCol, 5, devCol, 4 + rows.length), points: heatPointsByDeviation() });
    }
    return worksheet(name, COLW, R, { freezeRows: 4, freezeCols: 2, autoFilterRows: rows.length, condFormats: cf });
  }
  const CATS = ["Студия", "Своб. планировка", "1", "2", "3", "4+"];
  const ROOM_OF_CAT = { "Студия": [9], "Своб. планировка": [7], "1": [1], "2": [2], "3": [3], "4+": [4, 5, 6] };
  const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
  const num = (v) => (v == null ? { v: "—" } : { v, t: "Number", s: "num" });
  // Юникод-спарклайн распределения (форма гистограммы одной строкой текста).
  // При малой выборке уменьшаем число бакетов, иначе почти все пустые.
  const SPARK_CHARS = "▁▂▃▄▅▆▇█";
  function sparkline(values) {
    const v = values.filter((x) => x != null);
    if (v.length < 5) return null;
    const n = v.length >= 40 ? 12 : v.length >= 15 ? 8 : 5;
    const lo = Math.min(...v), hi = Math.max(...v);
    if (hi <= lo) return SPARK_CHARS[4].repeat(n);   // все значения одинаковые
    const buckets = new Array(n).fill(0);
    v.forEach((x) => { const i = Math.min(n - 1, Math.floor((x - lo) / (hi - lo) * n)); buckets[i]++; });
    const maxB = Math.max(...buckets);
    return buckets.map((c) => SPARK_CHARS[Math.max(0, Math.round((c / maxB) * (SPARK_CHARS.length - 1)))]).join("");
  }
  function summarySheet(subj, rows, totalsByRoom, totalInJk, health) {
    const present = CATS.filter((c) => rows.some((r) => r.category === c)), today = fmtDate(new Date());
    // Агрегаты — ЖИВЫЕ ФОРМУЛЫ по листу «Все_лоты», а не запечённые числа:
    // поправили цену или отфильтровали строки — сводка пересчиталась. Раньше она
    // устаревала от любой правки, а это первый лист книги, который и показывают.
    // Бенчмарки по этажу и метро остаются числами: они считаются по группам,
    // которых нет отдельными колонками, — формулой это выразить нечем.
    const LOTS = "'Все_лоты'";
    const lastRow = 4 + rows.length;
    const RNG = (h) => `${LOTS}!$${colName(HEADERS.indexOf(h) + 1)}$5:$${colName(HEADERS.indexOf(h) + 1)}$${lastRow}`;
    const catR = RNG("Категория"), sellR = RNG("Тип продавца");
    const priceR = RNG("Цена, ₽"), ppmR = RNG("Цена за м², ₽");
    const rn = () => R.length + 1;                          // номер строки, которую сейчас добавим
    // MINIFS/MAXIFS появились в Excel 2016; префикс _xlfn нужен, чтобы файл
    // открылся и в более старых версиях без #ИМЯ?.
    const F = (f, s) => ({ f, s: s || "num" });
    const R = [row([{ v: `${subj.title}${subj.id ? " (ID " + subj.id + ")" : ""} — сводка`, s: "title", merge: 5 }]), row([{ v: `Данные Циан на ${today}. Собрано ${rows.length} лотов. «Частник» = собственник/агентство.`, s: "sub", merge: 5 }]), row([{}])];
    R.push(row([{ v: "ОХВАТ ВЫГРУЗКИ", s: "bold" }]), row(["Категория", "Собрано", "Всего на Циан", "% выдачи"].map((h) => ({ v: h, s: "hdr" }))));
    let sumT = 0;
    const covFirst = rn();
    present.forEach((c) => {
      const got = rows.filter((r) => r.category === c).length;
      // totalsByRoom есть только если был добор (>лимита); иначе собрано = всё на Циан
      const tot = totalsByRoom ? (ROOM_OF_CAT[c].reduce((s, rm) => s + (totalsByRoom[rm] || 0), 0) || null) : got;
      if (tot) sumT += tot;
      const r = rn();
      R.push(row([{ v: c }, F(`COUNTIF(${catR},"${c}")`, ""), num(tot),
        tot ? F(`IFERROR(B${r}/C${r},"—")`, "pct") : { v: "—" }]));
    });
    const covLast = rn() - 1;
    const totalR = rn();
    R.push(row([{ v: "ИТОГО (категории)", s: "bold" },
      covLast >= covFirst ? F(`SUM(B${covFirst}:B${covLast})`, "bold") : { v: 0, t: "Number", s: "bold" },
      num(sumT || null),
      sumT ? F(`IFERROR(B${totalR}/C${totalR},"—")`, "pct") : { v: "—" }]));
    if (totalInJk) {
      const r = rn();
      R.push(row([{ v: subj.isJk ? "Всего квартир в ЖК (Циан)" : "Всего по фильтру (Циан)", s: "bold" },
        F(`COUNTA(${RNG("Категория")})`, ""), { v: totalInJk, t: "Number" },
        F(`IFERROR(B${r}/C${r},"—")`, "pct")]));
    }
    R.push(row([{}]), row([{ v: "СРЕДНЯЯ ЦЕНА ЗА м², ₽", s: "bold" }]), row(["Категория", "Частник", "Застройщик", "Все"].map((h) => ({ v: h, s: "hdr" }))));
    present.concat(["ИТОГО по ЖК"]).forEach((c) => {
      const all = c === "ИТОГО по ЖК";
      const byCat = all ? "" : `,${catR},"${c}"`;
      R.push(row([{ v: c, s: all ? "bold" : "" },
        F(`IFERROR(AVERAGEIFS(${ppmR},${sellR},"<>Застройщик"${byCat}),"—")`),
        F(`IFERROR(AVERAGEIFS(${ppmR},${sellR},"Застройщик"${byCat}),"—")`),
        all ? F(`IFERROR(AVERAGE(${ppmR}),"—")`) : F(`IFERROR(AVERAGEIFS(${ppmR},${catR},"${c}"),"—")`)]));
    });
    R.push(row([{}]), row([{ v: "ДИАПАЗОН ЦЕН, ₽", s: "bold" }]), row(["Категория", "Мин. цена", "Средн. цена", "Макс. цена", "Мин. ₽/м²", "Макс. ₽/м²"].map((h) => ({ v: h, s: "hdr" }))));
    present.forEach((c) => {
      const w = `,${catR},"${c}"`;
      R.push(row([{ v: c },
        F(`IFERROR(_xlfn.MINIFS(${priceR}${w}),"—")`),
        F(`IFERROR(AVERAGEIFS(${priceR}${w}),"—")`),
        F(`IFERROR(_xlfn.MAXIFS(${priceR}${w}),"—")`),
        F(`IFERROR(_xlfn.MINIFS(${ppmR}${w}),"—")`),
        F(`IFERROR(_xlfn.MAXIFS(${ppmR}${w}),"—")`)]));
    });
    // спарклайн распределения ₽/м² — форма гистограммы одной строкой (перекос/бимодальность видны сразу)
    const allSpark = sparkline(rows.map((r) => r.ppm));
    if (allSpark) {
      R.push(row([{ v: "Распределение ₽/м² (весь набор)" }, { v: allSpark, s: "mono", merge: 4 }]));
    }
    // бенчмарк по этажу: низкий/средний/высокий/последний — частый фактор
    // ценообразования в Москве; это СПРАВОЧНАЯ таблица, тепловую карту она не меняет.
    const withFloor = rows.filter((r) => r.floor != null && r.floors != null && r.ppm != null);
    if (withFloor.length >= 5) {
      const floorTier = (r) => {
        if (r.floor === 1) return "1-й этаж";
        if (r.floor === r.floors) return "Последний";
        const q = r.floor / r.floors;
        return q <= 0.4 ? "Низкие (2 — 40%)" : q <= 0.75 ? "Средние (40-75%)" : "Высокие (75%+)";
      };
      const TIERS = ["1-й этаж", "Низкие (2 — 40%)", "Средние (40-75%)", "Высокие (75%+)", "Последний"];
      R.push(row([{}]), row([{ v: "БЕНЧМАРК: ЦЕНА ПО ЭТАЖУ", s: "bold" }]), row(["Этаж", "Лотов", "Средняя ₽/м²", "Откл. от общей средней"].map((h) => ({ v: h, s: "hdr" }))));
      const overallPpm = avg(withFloor.map((r) => r.ppm));
      TIERS.forEach((t) => {
        const sub = withFloor.filter((r) => floorTier(r) === t); if (!sub.length) return;
        const a = avg(sub.map((r) => r.ppm));
        R.push(row([{ v: t }, { v: sub.length, t: "Number" }, num(a), { v: overallPpm && a ? (a >= overallPpm ? "+" : "−") + Math.round(Math.abs(a / overallPpm - 1) * 100) + "%" : "—" }]));
      });
    }
    // бенчмарк по удалённости от метро
    const withMetro = rows.filter((r) => r.metroTime != null && r.ppm != null);
    if (withMetro.length >= 5) {
      const METRO_BUCKETS = [[0, 5, "0-5 мин"], [6, 10, "6-10 мин"], [11, 15, "11-15 мин"], [16, 20, "16-20 мин"], [21, Infinity, "20+ мин"]];
      R.push(row([{}]), row([{ v: "БЕНЧМАРК: ЦЕНА ПО УДАЛЁННОСТИ ОТ МЕТРО", s: "bold" }]), row(["До метро", "Лотов", "Средняя ₽/м²", "Откл. от общей средней"].map((h) => ({ v: h, s: "hdr" }))));
      const overallPpm2 = avg(withMetro.map((r) => r.ppm));
      METRO_BUCKETS.forEach(([lo, hi, label]) => {
        const sub = withMetro.filter((r) => r.metroTime >= lo && r.metroTime <= hi); if (!sub.length) return;
        const a = avg(sub.map((r) => r.ppm));
        R.push(row([{ v: label }, { v: sub.length, t: "Number" }, num(a), { v: overallPpm2 && a ? (a >= overallPpm2 ? "+" : "−") + Math.round(Math.abs(a / overallPpm2 - 1) * 100) + "%" : "—" }]));
      });
    }

    // подсветка ₽/м² — легенда тепловой карты
    R.push(row([{}]), row([{ v: "ПОДСВЕТКА ₽/м² (в листах с лотами)", s: "bold" }]));
    R.push(row([{ v: "Зелёный — ниже средней по категории (дешевле/недооценён), красный — выше (дороже/переоценён).", s: "sub" }]));
    R.push(row([{ v: "База — средняя ₽/м² по той же комнатности; при <3 лотах в категории берётся общая средняя.", s: "sub" }]));
    R.push(row([{ v: "−20% и ниже", s: "h1" }, { v: "−10%", s: "h3" }, { v: "средняя", s: "h5" }, { v: "+10%", s: "h7" }, { v: "+20% и выше", s: "h9" }]));

    // отделка / ремонт
    // Порядок берём из FIN, а не перепечатываем подписи руками: копия списка
    // разъезжается с блоком при первой же правке подписи и молча обнуляет строку.
    const FIN_ORDER = Object.values(FIN);
    const finCount = {};
    rows.forEach((r) => { if (r.decoration) finCount[r.decoration] = (finCount[r.decoration] || 0) + 1; });
    const byField = rows.filter((r) => r.finishSrc === "Циан-поле").length;
    const byText = rows.filter((r) => r.finishSrc === "из описания").length;
    const noFin = rows.filter((r) => !r.decoration).length;
    R.push(row([{}]), row([{ v: "ОТДЕЛКА / РЕМОНТ (определено)", s: "bold" }]), row(["Категория", "Лотов", "Доля"].map((h) => ({ v: h, s: "hdr" }))));
    // Хвостом — значения, которых в FIN нет (Циан ввёл новое): иначе такой лот
    // исчезает из блока, ведь в «Не определена» он тоже не попадает.
    FIN_ORDER.filter((k) => finCount[k])
      .concat(Object.keys(finCount).filter((k) => !FIN_ORDER.includes(k)).sort())
      .forEach((k) => {
        const r = rn();
        R.push(row([{ v: k }, F(`COUNTIF(${RNG("Отделка/ремонт")},"${k}")`, ""),
          F(`IFERROR(B${r}/COUNTA(${catR}),"—")`, "pct")]));
      });
    if (noFin) {
      const r = rn();
      R.push(row([{ v: "Не определена" }, F(`COUNTBLANK(${RNG("Отделка/ремонт")})`, ""),
        F(`IFERROR(B${r}/COUNTA(${catR}),"—")`, "pct")]));
    }
    R.push(row([{ v: "Источник: поле Циан / описание / нет", s: "sub" }, { v: `${byField} / ${byText} / ${noFin}` }]));

    // экспозиция
    const real = rows.map((r) => r.realExposure).filter((x) => x != null);
    const cian = rows.map((r) => r.exposure).filter((x) => x !== "" && x != null);
    const resets = rows.filter((r) => r.reset).length;
    R.push(row([{}]), row([{ v: "СРОК ЭКСПОЗИЦИИ", s: "bold" }]));
    R.push(row([{ v: "Реальный срок (медиана/среднее), дн" }, num(real.length ? real.slice().sort((a, b) => a - b)[Math.floor(real.length / 2)] : null), num(avg(real))]));
    R.push(row([{ v: "По счётчику Циан (среднее), дн" }, { v: "" }, num(avg(cian))]));
    R.push(row([{ v: "Найдено сбросов даты (переподач)" }, { v: "" }, { v: resets, t: "Number", s: resets ? "warn" : null }]));
    R.push(row([{}]), row([{ v: "МЕТОДИКА: «Реальный срок» = сегодня − самая ранняя дата подачи среди дублей одной квартиры", s: "sub" }]));
    R.push(row([{ v: "и за всю историю наблюдений; сброс/переподача даты Циан его не уменьшает. Чем чаще выгружать — тем точнее.", s: "sub" }]));
    // диагностика качества сбора — сохраняется в архивном файле, не только в эфемерной панели
    if (health && health.requests) {
      const warn = isHealthWarn(health);
      R.push(row([{}]), row([{ v: "ДИАГНОСТИКА СБОРА", s: "bold" }]));
      R.push(row([{ v: `Запросов: ${health.requests} · ретраев (429/5xx/сеть): ${health.retries} · дрейф total между страницами: ${health.totalDrift}`, s: warn ? "warn" : "sub" }]));
      if (warn) R.push(row([{ v: "Много ретраев/нестабильный total — Циан мог троттлить сбор; проверьте охват выше и по возможности выгрузите повторно.", s: "sub" }]));
    }
    return worksheet("Сводка", [220, 96, 96, 96, 84, 84], R, false);
  }

  // Топ-30 лотов по индексу привлекательности — та же таблица (dataSheet),
  // просто отфильтрованная и отсортированная по _score.
  function topLotsSheet(subj, rows) {
    const scored = rows.filter((r) => r._score != null).sort((a, b) => b._score - a._score).slice(0, 30);
    if (!scored.length) return null;
    return dataSheet("Топ_лотов", `${subj.title} — топ ${scored.length} по индексу привлекательности`,
      "Индекс — эвристика (цена/м² относительно средней + отделка + срок экспозиции), не финансовая оценка. Сортировка по индексу.", scored);
  }

  // Одна физическая квартира выставлена разными продавцами по разной цене —
  // разброс может быть демпингом или устаревшей ценой у части объявлений.
  function dupSpreadSheet(subj, rows) {
    const seen = new Set(), items = [];
    rows.forEach((r) => {
      if (!(r.dupNow > 1 && r.dupSpreadAbs)) return;
      const fp = fpOf(r, subj.id);
      if (seen.has(fp)) return;
      seen.add(fp); items.push(r);
    });
    if (!items.length) return null;
    items.sort((a, b) => (b.dupSpreadAbs || 0) - (a.dupSpreadAbs || 0));
    const HDR = ["Категория", "Площадь, м²", "Этаж", "Корпус / секция", "Объявлений", "Мин. цена, ₽", "Макс. цена, ₽", "Разброс, ₽", "Разброс, %", "Дешевле у", "Ссылка на дешёвый"];
    const W = [78, 72, 56, 105, 76, 100, 100, 92, 76, 150, 68];
    const R = [row([{ v: `${subj.title} — дубли: разброс цены между продавцами`, s: "title", merge: HDR.length - 1 }]), row([{ v: "Одна и та же квартира выставлена несколькими продавцами по разной цене. Сортировка по разбросу, ₽.", s: "sub", merge: HDR.length - 1 }]), row([{}]), row(HDR.map((h) => ({ v: h, s: "hdr" })))];
    items.forEach((r) => {
      const floorVal = r.floor != null && r.floors != null ? r.floor + "/" + r.floors : (r.floor != null ? String(r.floor) : "");
      R.push(row([
        { v: r.category }, { v: r.area, t: r.area != null ? "Number" : "String", s: "area" }, { v: floorVal }, { v: r.building },
        { v: r.dupNow, t: "Number" }, { v: r.dupMinPrice, t: "Number", s: "num" }, { v: r.dupMaxPrice, t: "Number", s: "num" },
        { v: r.dupSpreadAbs, t: "Number", s: "num" }, { v: r.dupSpreadPct != null ? r.dupSpreadPct + "%" : "" },
        { v: r.dupCheapestSeller }, r.dupCheapestUrl ? { v: "Циан →", href: r.dupCheapestUrl, s: "link" } : {},
      ]));
    });
    return worksheet("Дубли_разброс_цен", W, R, { freezeRows: 4, freezeCols: 2, autoFilterRows: items.length });
  }

  // Агрегация по продавцам (агентство/застройщик/частник): у кого лоты обычно
  // дешевле/дороже рынка, и кто чаще выигрывает/проигрывает по цене среди
  // дублей одной квартиры (см. dupCheapestSeller/dupPricierSeller). Имена
  // продавцов у Циан НЕ нормализованы (регистр/«ООО»/пробелы) — возможны
  // почти-дубли бакетов, это ограничение метода, не баг.
  function sellersSheet(subj, rows) {
    const groups = {};
    rows.forEach((r) => {
      const key = r.seller_name ? r.seller_name.trim() : (r.seller_type === "Собственник" ? "Собственники (частные)" : (r.seller_type || "Не определено"));
      (groups[key] = groups[key] || { list: [], type: r.seller_type }).list.push(r);
    });
    const keys = Object.keys(groups).filter((k) => groups[k].list.length >= 2);  // единичных продавцов сравнивать не с чем
    if (keys.length < 2) return null;
    const items = keys.map((k) => {
      const g = groups[k].list;
      const uniq = new Set(g.map((r) => fpOf(r, subj.id))).size;
      const cheapN = g.filter((r) => r._heat === "h1" || r._heat === "h2" || r._heat === "h3").length;
      const priceyN = g.filter((r) => r._heat === "h7" || r._heat === "h8" || r._heat === "h9").length;
      return {
        k, type: groups[k].type || "", n: g.length, uniq,
        ppm: avg(g.map((r) => r.ppm).filter((x) => x != null)),
        score: avg(g.map((r) => r._score).filter((x) => x != null)),
        exp: avg(g.map((r) => r.realExposure).filter((x) => x != null)),
        cheapPct: Math.round(cheapN / g.length * 100), priceyPct: Math.round(priceyN / g.length * 100),
        wins: g.filter((r) => r.dupCheapestSeller === k).length,
        losses: g.filter((r) => r.dupPricierSeller === k && r.dupCheapestSeller !== k).length,
      };
    }).sort((a, b) => b.n - a.n);
    const HDR = ["Продавец", "Тип", "Лотов", "Уник. квартир", "Ø ₽/м²", "Ø индекс привлекательности", "Ø реальный срок, дн", "Дешевле рынка, %", "Дороже рынка, %", "Побед в дублях (дешевле)", "Проигрышей в дублях (дороже)"];
    const W = [190, 100, 56, 90, 88, 96, 100, 92, 92, 112, 118];
    const R = [row([{ v: `${subj.title} — продавцы`, s: "title", merge: HDR.length - 1 }]), row([{ v: "Группировка по имени продавца (не нормализовано). Показаны продавцы с 2+ лотами. «Побед/проигрышей в дублях» — среди квартир, выставленных несколькими продавцами.", s: "sub", merge: HDR.length - 1 }]), row([{}]), row(HDR.map((h) => ({ v: h, s: "hdr" })))];
    items.forEach((it) => {
      R.push(row([
        { v: it.k }, { v: it.type }, { v: it.n, t: "Number" }, { v: it.uniq, t: "Number" },
        { v: it.ppm, t: it.ppm != null ? "Number" : "String", s: "num" }, { v: it.score, t: it.score != null ? "Number" : "String" },
        { v: it.exp, t: it.exp != null ? "Number" : "String" },
        { v: it.cheapPct + "%", s: it.cheapPct >= 40 ? "pgood" : null }, { v: it.priceyPct + "%", s: it.priceyPct >= 40 ? "pbad" : null },
        { v: it.wins, t: "Number" }, { v: it.losses, t: "Number" },
      ]));
    });
    return worksheet("Продавцы", W, R, { freezeRows: 4, freezeCols: 1, autoFilterRows: items.length });
  }

  // Агрегация по корпусам/секциям — только для ЖК (subj.isJk): на произвольных
  // поисках по фильтрам/полигону сырые названия корпусов из РАЗНЫХ домов могут
  // случайно совпасть текстом и смешаться в один бакет.
  function buildingsSheet(subj, rows) {
    if (!subj.isJk) return null;
    const groups = {};
    rows.forEach((r) => {
      const raw = (r.building || "").toString().trim();
      const key = raw ? raw.toLowerCase() : "\u0000none";
      (groups[key] = groups[key] || { label: raw || "Без указания корпуса", list: [] }).list.push(r);
    });
    const keys = Object.keys(groups);
    if (keys.length < 2) return null;   // один корпус — сравнивать не с чем
    const overallPpm = avg(rows.map((r) => r.ppm).filter((x) => x != null));
    const items = keys.map((key) => {
      const g = groups[key].list;
      const ppm = avg(g.map((r) => r.ppm).filter((x) => x != null));
      const finOk = g.filter((r) => FIN_QUALITY_RANK[r.decoration] >= 4).length;
      return {
        label: groups[key].label, n: g.length, ppm,
        dev: overallPpm && ppm ? (ppm >= overallPpm ? "+" : "−") + Math.round(Math.abs(ppm / overallPpm - 1) * 100) + "%" : "—",
        exp: avg(g.map((r) => r.realExposure).filter((x) => x != null)),
        finPct: g.length ? Math.round(finOk / g.length * 100) : 0,
      };
    }).sort((a, b) => b.n - a.n);
    const HDR = ["Корпус / секция", "Лотов", "Ø ₽/м²", "Откл. от Ø по ЖК", "Ø реальный срок, дн", "Хорошая отделка+, %"];
    const W = [150, 60, 92, 110, 110, 120];
    const R = [row([{ v: `${subj.title} — по корпусам`, s: "title", merge: HDR.length - 1 }]), row([{ v: "Название корпуса берётся как есть из Циан (может отличаться написанием у разных лотов одного корпуса).", s: "sub", merge: HDR.length - 1 }]), row([{}]), row(HDR.map((h) => ({ v: h, s: "hdr" })))];
    items.forEach((it) => {
      R.push(row([
        { v: it.label }, { v: it.n, t: "Number" },
        { v: it.ppm, t: it.ppm != null ? "Number" : "String", s: "num" }, { v: it.dev },
        { v: it.exp, t: it.exp != null ? "Number" : "String" }, { v: it.finPct + "%" },
      ]));
    });
    return worksheet("По_корпусам", W, R, { freezeRows: 4, freezeCols: 1, autoFilterRows: items.length });
  }

  // Динамика между запусками: что появилось/пропало/подешевело/подорожало с
  // прошлой выгрузки этого же ЖК/выборки (сравнение по локальному снимку).
  function changesSheet(subj, changes) {
    if (!changes.hasPrev) return null;
    const HDR = ["Тип", "Категория", "Корпус", "Этаж", "Цена, ₽", "Было, ₽", "Δ, %", "Ссылка"];
    const W = [90, 78, 105, 56, 100, 100, 76, 68];
    const R = [row([{ v: `${subj.title} — изменения с прошлой выгрузки`, s: "title", merge: HDR.length - 1 }]), row([{ v: `Новых: ${changes.appeared.length} · Пропало: ${changes.vanished.length} · Подешевело: ${changes.cheaper.length} · Подорожало: ${changes.pricier.length}`, s: "sub", merge: HDR.length - 1 }]), row([{}]), row(HDR.map((h) => ({ v: h, s: "hdr" })))];
    const addRow = (type, r, price, from, pct, styleId) => {
      const floorVal = r.floor != null ? String(r.floor) : "";
      R.push(row([
        { v: type, s: styleId || "bold" }, { v: r.category }, { v: r.building }, { v: floorVal },
        { v: price, t: price != null ? "Number" : "String", s: "num" }, { v: from, t: from != null ? "Number" : "String", s: "num" },
        { v: pct != null ? (pct > 0 ? "+" : "") + pct + "%" : "", s: pct == null ? null : (pct < 0 ? "pgood" : "pbad") },
        r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]));
    };
    changes.cheaper.forEach((c) => addRow("↓ Подешевел", c.r, c.r.price, c.from, c.pct, "pgood"));
    changes.pricier.forEach((c) => addRow("↑ Подорожал", c.r, c.r.price, c.from, c.pct, "pbad"));
    changes.appeared.forEach((r) => addRow("+ Новый", r, r.price, null, null, null));
    changes.vanished.forEach((r) => addRow("− Пропал (продан/снят?)", r, null, r.price, null, "warn"));
    const totalRows = changes.cheaper.length + changes.pricier.length + changes.appeared.length + changes.vanished.length;
    return worksheet("Изменения", W, R, { freezeRows: 4, freezeCols: 2, autoFilterRows: totalRows });
  }

  function buildWorkbook(subj, rows, totalsByRoom, totalInJk, health) {
    rows = rows.slice().sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
    computeHeat(rows);                                    // тепловая карта ₽/м² (зелёный↔красный)
    computeScore(rows);                                   // индекс привлекательности лота
    const changes = computeChanges(subj, rows);            // динамика с прошлой выгрузки (+ сохраняет новый снимок)
    const today = fmtDate(new Date()), sheets = [summarySheet(subj, rows, totalsByRoom, totalInJk, health)];
    const changesXml = changesSheet(subj, changes); if (changesXml) sheets.push(changesXml);
    const topXml = topLotsSheet(subj, rows); if (topXml) sheets.push(topXml);
    const src = `Источник: Циан${subj.id ? " (ID " + subj.id + ")" : ""}, ${today}. Сортировка по ₽/м². Подсветка ₽/м²: зелёный — дешевле средней по категории, красный — дороже. «Реальный срок» учитывает переподачи.`;
    sheets.push(dataSheet("Все_лоты", `${subj.title} — все лоты`, src, rows));
    const sn = { "Студия": "Студия", "Своб. планировка": "Своб_планировка", "1": "1-комн", "2": "2-комн", "3": "3-комн", "4+": "4-комн" };
    CATS.forEach((c) => { const sub = rows.filter((r) => r.category === c); if (sub.length) sheets.push(dataSheet(sn[c], `${subj.title} — ${c}`, `Собрано ${sub.length}. Сортировка по ₽/м².`, sub)); });
    const dupXml = dupSpreadSheet(subj, rows); if (dupXml) sheets.push(dupXml);
    const sellersXml = sellersSheet(subj, rows); if (sellersXml) sheets.push(sellersXml);
    const buildingsXml = buildingsSheet(subj, rows); if (buildingsXml) sheets.push(buildingsXml);
    return { font: "Calibri", fontSize: 11, sheets: sheets };
  }
  const slug = (s) => s.toLowerCase().replace(/\s+/g, "-").replace(/[^0-9a-zа-яё_\-]/g, "") || "jk";
  // ВАЖНО: никакого BOM. У .xls SpreadsheetML он был нужен, а .xlsx — это zip,
  // и любой байт перед сигнатурой PK\x03\x04 делает архив нечитаемым: Excel
  // скажет только «файл повреждён», не уточняя причину.
  function download(blob, name) {
    const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1500);
  }

  const OPEN_LIST_MSG =
    "Откройте ОСНОВНУЮ страницу ЖК со списком квартир на www.cian.ru (раздел «Квартиры»), " +
    "дождитесь, пока загрузятся объявления, и нажмите кнопку снова.\n\n" +
    "На промо-сайте застройщика (zhk-*.cian.ru) и на странице без списка квартир выгрузка не работает.";

  const fmt = (n) => (n == null ? "—" : Number(n).toLocaleString("ru-RU"));
  // Любопытные факты — НЕ про Циан/выборку, а из жизни, кода, природы и науки.
  // Показываем случайный после каждой успешной выгрузки (без повтора подряд).
  const FUN_FACTS = [
    "🐙 У осьминога три сердца и голубая кровь, а кожа меняет цвет и фактуру за доли секунды.",
    "🍯 Мёд не портится: в египетских гробницах находили мёд, пригодный в пищу спустя тысячи лет.",
    "🐛 Первый «баг» был настоящим: в 1947-м в реле компьютера Mark II застрял мотылёк — его вклеили в журнал с подписью «first actual case of bug».",
    "🐍 Язык Python назван не в честь змеи, а в честь шоу «Монти Пайтон».",
    "☕ Первую в мире веб-камеру в 1991-м в Кембридже навели на кофейник — чтобы не ходить зря к пустому.",
    "🦈 Акулы появились раньше деревьев — они старше примерно на 50 млн лет.",
    "🪐 Сутки на Венере длиннее её года: оборот вокруг оси — 243 земных дня, вокруг Солнца — 225.",
    "🃏 Способов перетасовать колоду из 52 карт больше, чем атомов на Земле (число из 68 цифр).",
    "🦥 Ленивец задерживает дыхание дольше дельфина — до 40 минут, замедляя сердце.",
    "💎 На Нептуне и Уране, по расчётам учёных, идут дожди из алмазов.",
    "🧠 Мозг — это ~2% массы тела, но забирает ~20% всей его энергии.",
    "👃 Промычать с зажатым носом невозможно — для гудения нужен выход воздуха через нос.",
    "🦩 Стая фламинго называется «flamboyance» — «роскошь».",
    "🌳 Java сначала называлась Oak («дуб») — по дереву за окном разработчика.",
    "🗼 Эйфелева башня летом выше примерно на 15 см — металл расширяется от тепла.",
    "🥄 Чайная ложка вещества нейтронной звезды весила бы миллиарды тонн.",
    "🍌 У человека и банана около 60% общих генов.",
    "⚔️ Самая короткая война в истории длилась ~38 минут (Британия против Занзибара, 1896).",
    "📶 «Wi-Fi» ничего не расшифровывает — это просто звучное название, а не аббревиатура.",
    "🫧 Пузырчатую плёнку (bubble wrap) в 1957-м придумали как… обои.",
    "🎂 Достаточно 23 человек в комнате, чтобы с вероятностью >50% у двоих совпал день рождения.",
    "🏛️ Оксфорд старше империи ацтеков: преподавать там начали уже около 1096 года.",
    "🌑 Следы астронавтов на Луне сохранятся миллионы лет — там нет ветра и воды.",
    "☀️ Свет от Солнца летит до Земли около 8 минут 20 секунд.",
    "🔇 В космосе абсолютная тишина: звуку не в чем распространяться.",
    "💾 Первый жёсткий диск (IBM, 1956) хранил 5 МБ и весил почти тонну.",
    "🐄 У коров есть лучшие подруги, и в разлуке они нервничают.",
    "🌐 Деревья «общаются» через подземную грибницу — учёные зовут это «wood wide web».",
    "👩‍💻 Первым программистом считают Аду Лавлейс — она описала алгоритм ещё в 1840-х, до появления компьютеров.",
    "❤️ Сердце делает около 100 000 ударов в день.",
    "🔢 0,(9) — это ровно 1, а не «почти»: это одно и то же число.",
    "🧊 Вомбаты какают кубиками — так помёт не скатывается и лучше метит территорию.",
    "⌨️ По распространённой версии, раскладку QWERTY придумали, чтобы замедлить машинисток и не заклинивало рычажки.",
    "🌕 Клеопатра жила ближе по времени к высадке на Луну, чем к постройке пирамид.",
    "🐦 Колибри — единственные птицы, которые умеют летать назад.",
    "🔟 Гугол — это единица со ста нулями; название Google появилось как опечатка от него.",
    "🌲 Одна из старейших живых сосен (остистая) старше пирамид — ей около 4800 лет.",
    "💡 Символ @ применяли в торговле («по цене за штуку») за века до электронной почты.",
    "🐧 Самец пингвина Адели «дарит» самке самый гладкий камешек для гнезда.",
    "🕰️ «Jiffy» — это реальная единица времени в физике и электронике.",
  ];
  let _lastFactIdx = -1;
  function pickFact() {
    if (FUN_FACTS.length < 2) return FUN_FACTS[0] || null;
    let i; do { i = Math.floor(Math.random() * FUN_FACTS.length); } while (i === _lastFactIdx);
    _lastFactIdx = i;
    return FUN_FACTS[i];
  }
  function computeStats(rows, totalInJk, expInfo, health) {
    const ppm = rows.map((r) => r.ppm).filter((x) => x != null);
    const exp = rows.map((r) => r.exposure).filter((x) => x !== "" && x != null);
    const real = rows.map((r) => r.realExposure).filter((x) => x != null);
    const areas = rows.map((r) => r.area).filter((x) => x != null);
    const mt = rows.map((r) => r.metroTime).filter((x) => x != null);
    const avg = (a) => (a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null);
    const med = (a) => (a.length ? a.slice().sort((x, y) => x - y)[Math.floor(a.length / 2)] : null);
    const byCat = CATS.map((c) => ({ c, n: rows.filter((r) => r.category === c).length })).filter((x) => x.n);
    const devN = rows.filter((r) => r.seller_type === "Застройщик").length;
    const st = {
      count: rows.length, total: totalInJk || rows.length,
      coverage: totalInJk ? Math.min(100, Math.round((rows.length / totalInJk) * 100)) : 100,
      ppmMin: ppm.length ? Math.min(...ppm) : null, ppmAvg: avg(ppm), ppmMax: ppm.length ? Math.max(...ppm) : null,
      expAvg: avg(exp), realAvg: avg(real), realMed: med(real),
      resets: (expInfo && expInfo.resets) || 0, devPct: rows.length ? Math.round(devN / rows.length * 100) : null, byCat,
    };
    // диагностика качества сбора: заметный % ретраев или нестабильный total — сигнал, что Циан троттлит/капчит
    if (health && health.requests) {
      st.retries = health.retries; st.totalDrift = health.totalDrift;
      st.healthWarn = isHealthWarn(health);
    }
    st.fact = pickFact();   // случайный любопытный факт (из жизни/кода/природы), не про выборку
    return st;
  }

  // ---------- GUI: панель в Shadow DOM ----------
  const CSS = `
  *{box-sizing:border-box;margin:0;padding:0}
  :host{
    --bg-card:#fff; --text-1:#16203a; --text-2:#7a8398; --text-3:#69728a;
    --stat-bg:#f5f7fb; --chip-bg:#eef1f8; --chip-text:#34406e; --border:#eef0f5;
    --track-bg:#edeff5; --shadow:rgba(16,24,49,.30); --btn-disabled:#cdd2de;
    --ok-bg:#e8f7ee; --ok-text:#1d7a43; --warn-bg:#fff3e0; --warn-text:#a96714;
    --fact-bg1:#fff8ea; --fact-bg2:#fff1d4; --fact-border:#ffe2ad; --fact-text:#7a5a14; --fact-lab:#b5781a;
    --err-bg:#fdecea; --err-text:#a32a1f; --err-border:#f5c2bb;
  }
  @media (prefers-color-scheme:dark){:host{
    --bg-card:#1b2133; --text-1:#eef1fa; --text-2:#9aa3bd; --text-3:#8b93ab;
    --stat-bg:#242c47; --chip-bg:#242c47; --chip-text:#b9c3e6; --border:#2b3350;
    --track-bg:#242c47; --shadow:rgba(0,0,0,.55); --btn-disabled:#3a4262;
    --ok-bg:#153826; --ok-text:#6fe3a7; --warn-bg:#3a2a12; --warn-text:#f0b15e;
    --fact-bg1:#2b2410; --fact-bg2:#332a10; --fact-border:#4a3c14; --fact-text:#e9c073; --fact-lab:#f0b84a;
    --err-bg:#3a1a17; --err-text:#f29288; --err-border:#5c2a24;
  }}
  .root{position:fixed;right:22px;bottom:88px;z-index:2147483647;
    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif}
  .card{width:312px;background:var(--bg-card);border-radius:18px;overflow:hidden;
    box-shadow:0 16px 48px var(--shadow);animation:in .25s ease}
  @keyframes in{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
  .root.collapsed .card{display:none}
  .fab{display:none}
  .root.collapsed .fab{display:flex;align-items:center;justify-content:center;width:60px;height:60px;
    margin-left:auto;border:none;border-radius:50%;cursor:pointer;color:#fff;
    background:linear-gradient(135deg,#1F2A44,#3a4a7d);box-shadow:0 10px 26px rgba(16,24,49,.34);
    animation:fab-pulse 1.8s ease-in-out 4}
  @keyframes fab-pulse{0%,100%{box-shadow:0 10px 26px rgba(16,24,49,.34)}
    50%{box-shadow:0 0 0 12px rgba(39,174,96,.22),0 10px 26px rgba(16,24,49,.34)}}
  .root.collapsed .fab:hover{filter:brightness(1.08)}
  .head{display:flex;align-items:center;gap:9px;padding:15px 16px;color:#fff;
    background:linear-gradient(135deg,#1F2A44 0%,#34416f 100%)}
  .head .ic{display:flex}.head .t{font-weight:700;font-size:14.5px;flex:1;letter-spacing:.2px}
  .min{background:rgba(255,255,255,.16);border:none;color:#fff;width:26px;height:26px;
    border-radius:8px;cursor:pointer;font-size:16px;line-height:1}
  .min:hover{background:rgba(255,255,255,.28)}
  .body{padding:16px}
  .jk{font-size:16.5px;font-weight:800;color:var(--text-1);line-height:1.25}
  .sub{font-size:12px;color:var(--text-2);margin-top:3px}
  .pg{margin-top:12px;font-size:12.5px;display:flex;gap:7px;align-items:flex-start;
    padding:9px 11px;border-radius:11px;line-height:1.35}
  .pg.ok{background:var(--ok-bg);color:var(--ok-text)}.pg.warn{background:var(--warn-bg);color:var(--warn-text)}
  .btn{margin-top:14px;width:100%;padding:13px;border:none;border-radius:12px;color:#fff;
    font-size:14.5px;font-weight:700;cursor:pointer;letter-spacing:.2px;
    background:linear-gradient(135deg,#1f9d55,#27ae60);transition:filter .15s,transform .05s}
  .btn:hover{filter:brightness(1.07)}.btn:active{transform:translateY(1px)}
  .btn[disabled]{background:var(--btn-disabled);cursor:default;filter:none}
  .prog{margin-top:14px;display:none}
  .prog .pt{font-size:12.5px;color:var(--text-1);font-weight:600;margin-bottom:7px;display:flex;justify-content:space-between}
  .track{height:11px;background:var(--track-bg);border-radius:7px;overflow:hidden}
  .bar{height:100%;width:0;border-radius:7px;background:linear-gradient(90deg,#27ae60,#1f9d55);
    transition:width .3s ease}
  .bar.indef{width:40%;animation:slide 1.1s infinite ease-in-out}
  @keyframes slide{0%{margin-left:-40%}100%{margin-left:100%}}
  .res{margin-top:14px;display:none}
  .stats{display:flex;gap:8px}
  .stat{flex:1;background:var(--stat-bg);border-radius:12px;padding:10px 6px;text-align:center}
  .stat .v{font-size:17px;font-weight:800;color:var(--text-1);line-height:1}
  .stat .l{font-size:10px;color:var(--text-2);margin-top:4px;text-transform:uppercase;letter-spacing:.3px}
  .stat.full .v{color:#1f9d55}
  .meta{margin-top:10px;font-size:11.5px;color:var(--text-3);line-height:1.5}
  .cats{display:flex;flex-wrap:wrap;gap:5px;margin-top:10px}
  .chip{font-size:11px;background:var(--chip-bg);color:var(--chip-text);padding:4px 9px;border-radius:20px;font-weight:600}
  .fact{margin-top:12px;display:none;background:linear-gradient(135deg,var(--fact-bg1),var(--fact-bg2));
    border:1px solid var(--fact-border);color:var(--fact-text);border-radius:12px;padding:11px 13px;font-size:12.5px;line-height:1.5;
    animation:in .3s ease}
  .fact .lab{font-weight:800;color:var(--fact-lab);display:block;font-size:10.5px;letter-spacing:.4px;margin-bottom:3px;text-transform:uppercase}
  .file{margin-top:12px;font-size:12px;color:var(--ok-text);background:var(--ok-bg);border-radius:10px;
    padding:9px 11px;display:flex;gap:7px;align-items:center;word-break:break-all}
  .err{margin-top:14px;display:none;background:var(--err-bg);border:1px solid var(--err-border);
    color:var(--err-text);border-radius:12px;padding:11px 13px;font-size:12.5px;line-height:1.5;animation:in .3s ease}
  .err .et{display:block}
  .err .retry{margin-top:9px;border:1px solid var(--err-border);background:transparent;color:var(--err-text);
    font-size:12px;font-weight:700;padding:7px 12px;border-radius:9px;cursor:pointer}
  .err .retry:hover{background:var(--err-border)}
  .foot{padding:11px 16px;border-top:1px solid var(--border);font-size:11px;color:var(--text-2);line-height:1.45}
  .lnk{color:#2c6ecb;cursor:pointer;text-decoration:underline;background:none;border:none;font:inherit;padding:0}
  .bk-row{margin-top:13px;display:flex;gap:14px;font-size:11.5px}
  .bk{margin-top:8px;font-size:11.5px;border-radius:10px;padding:8px 10px;display:none;line-height:1.4}
  .bk.ok{background:var(--ok-bg);color:var(--ok-text)}
  .bk.bad{background:var(--err-bg);color:var(--err-text);border:1px solid var(--err-border)}
  .btn:focus-visible,.min:focus-visible,.fab:focus-visible,.err .retry:focus-visible,.lnk:focus-visible{
    outline:2px solid #4c8dff;outline-offset:2px}`;

  const ui = { mounted: false };

  // Инлайн SVG вместо эмодзи — чётче/предсказуемее на всех ОС и масштабах,
  // currentColor подхватывает цвет из .head/.fab (без доп. CSS на каждый размер).
  const ICON_SVG = (size) => `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="12" width="4.5" height="9" rx="1.2" fill="currentColor"/><rect x="9.75" y="7" width="4.5" height="14" rx="1.2" fill="currentColor"/><rect x="16.5" y="3" width="4.5" height="18" rx="1.2" fill="currentColor"/></svg>`;

  function buildPanel() {
    const host = document.createElement("div");
    host.id = "cian-excel-host";
    const sh = host.attachShadow({ mode: "open" });
    sh.innerHTML =
      "<style>" + CSS + "</style>" +
      '<div class="root collapsed" part="root">' +   // по умолчанию свёрнуто (кружок)
        `<button class="fab" title="Циан → Excel">${ICON_SVG(26)}</button>` +
        '<div class="card">' +
          `<div class="head"><span class="ic">${ICON_SVG(18)}</span><span class="t">Циан → Excel</span>` +
            '<button class="min" title="Свернуть">—</button></div>' +
          '<div class="body">' +
            '<div class="jk" id="jk">ЖК не определён</div>' +
            '<div class="sub" id="sub">откройте страницу ЖК с квартирами</div>' +
            '<div class="pg warn" id="pg"><span id="pgi">⚠</span><span id="pgt">Жду страницу со списком квартир…</span></div>' +
            '<button class="btn" id="go" disabled>📊 Выгрузить в Excel</button>' +
            '<div class="prog" id="prog"><div class="pt"><span id="pt">Собираю…</span><span id="pp"></span></div>' +
              '<div class="track"><div class="bar indef" id="bar"></div></div></div>' +
            '<div class="res" id="res">' +
              '<div class="stats">' +
                '<div class="stat"><div class="v" id="s-count">0</div><div class="l">лотов</div></div>' +
                '<div class="stat"><div class="v" id="s-cov">0%</div><div class="l">охват</div></div>' +
                '<div class="stat"><div class="v" id="s-ppm">—</div><div class="l">₽/м² ср.</div></div>' +
              '</div>' +
              '<div class="meta" id="s-meta"></div>' +
              '<div class="pg warn" id="s-health" style="display:none"><span>⚠</span><span id="s-healthtext"></span></div>' +
              '<div class="cats" id="s-cats"></div>' +
              '<div class="fact" id="s-fact"><span class="lab">💡 Любопытный факт</span><span id="s-facttext"></span></div>' +
              '<div class="file" id="s-file"><span>✓</span><span id="s-fname"></span></div>' +
            '</div>' +
            '<div class="err" id="err"><span class="et" id="err-text"></span><button class="retry" id="err-retry">↻ Повторить</button></div>' +
            '<div class="bk-row">' +
              '<button class="lnk" id="bk-export" title="Скачать историю (реальный срок, цены) и снимки в JSON">📦 Бэкап истории</button>' +
              '<button class="lnk" id="bk-import" title="Восстановить/перенести историю из файла бэкапа">📥 Восстановить</button>' +
            '</div>' +
            '<input type="file" id="bk-file" accept="application/json,.json" style="display:none">' +
            '<div class="bk" id="bk-status"></div>' +
          '</div>' +
          '<div class="foot" id="foot">Открой страницу ЖК с квартирами на www.cian.ru и нажми кнопку. Данные берутся из твоей сессии.</div>' +
        '</div>' +
      '</div>';
    document.body.appendChild(host);
    const $ = (s) => sh.querySelector(s);
    ui.host = host; ui.sh = sh; ui.root = $(".root");
    ui.el = {
      jk: $("#jk"), sub: $("#sub"), pg: $("#pg"), pgi: $("#pgi"), pgt: $("#pgt"),
      go: $("#go"), prog: $("#prog"), pt: $("#pt"), pp: $("#pp"), bar: $("#bar"),
      res: $("#res"), count: $("#s-count"), cov: $("#s-cov"), ppm: $("#s-ppm"),
      meta: $("#s-meta"), health: $("#s-health"), healthtext: $("#s-healthtext"), cats: $("#s-cats"), fact: $("#s-fact"), facttext: $("#s-facttext"),
      file: $("#s-file"), fname: $("#s-fname"), foot: $("#foot"),
      err: $("#err"), errText: $("#err-text"), errRetry: $("#err-retry"),
      bkExport: $("#bk-export"), bkImport: $("#bk-import"), bkFile: $("#bk-file"), bkStatus: $("#bk-status"),
    };
    const expand = (e) => { if (e) e.stopPropagation(); ui.root.classList.remove("collapsed"); try { refreshHeader(); } catch (err) { /* ignore */ } };
    const collapse = (e) => { if (e) e.stopPropagation(); ui.root.classList.add("collapsed"); };
    $(".min").addEventListener("click", collapse);   // .min теперь однозначно — кнопка «—»
    $(".fab").addEventListener("click", expand);
    // надёжность: клик по ЛЮБОМУ месту свёрнутого кружка (а не только по кнопке
    // внутри) раскрывает панель — чтобы промах по 1-2 px не «ломал» открытие.
    ui.root.addEventListener("click", () => { if (ui.root.classList.contains("collapsed")) expand(); });
    ui.el.go.addEventListener("click", () => run());
    ui.el.errRetry.addEventListener("click", () => { ui.el.err.style.display = "none"; run(); });
    ui.el.bkExport.addEventListener("click", () => {
      try {
        const data = exportBackupData(), n = Object.keys(data.history.flats || {}).length;
        download(new Blob([JSON.stringify(data)], { type: "application/json;charset=utf-8" }),
          `cian-excel-backup_${new Date().toISOString().slice(0, 10)}.json`);
        showBkStatus(`Бэкап сохранён: ${n} записей в истории.`, true);
      } catch (e) { showBkStatus("Не удалось создать бэкап: " + e.message, false); }
    });
    ui.el.bkImport.addEventListener("click", () => ui.el.bkFile.click());
    ui.el.bkFile.addEventListener("change", () => {
      const file = ui.el.bkFile.files && ui.el.bkFile.files[0];
      ui.el.bkFile.value = "";   // разрешить повторный выбор того же файла
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const res = importBackupData(String(reader.result));
          showBkStatus(`Импортировано: ${res.flats} в истории, ${res.subjects} снимков. Учтётся при следующей выгрузке.`, true);
        } catch (e) { showBkStatus("Ошибка импорта: " + e.message, false); }
      };
      reader.onerror = () => showBkStatus("Не удалось прочитать файл.", false);
      reader.readAsText(file);
    });
    ui.mounted = true;
    console.log("[cian-excel] панель добавлена");
  }

  // обновить шапку: тема выгрузки (ЖК или выборка) + готовность страницы
  function refreshHeader() {
    if (!ui.mounted) return;
    const subj = detectSubject();
    const base = pageJsonQuery();
    const cnt = pageResultCount();
    ui._subj = subj; ui._base = base ? cleanBaseQuery(base) : null;
    ui.el.jk.textContent = subj.title || "Откройте выдачу Циан";
    ui.el.sub.textContent = subj.id ? ("ID " + subj.id + " · cian.ru")
      : (cnt != null ? ("на странице " + cnt + " объявл.") : "выборка по фильтрам / карте");
    const ready = !!ui._base;
    ui.el.pg.className = "pg " + (ready ? "ok" : "warn");
    ui.el.pgi.textContent = ready ? "✓" : "⚠";
    ui.el.pgt.textContent = ready
      ? ("Готово к выгрузке" + (cnt != null ? " — на странице " + cnt + " объявл." : " — список загружен"))
      : "Откройте страницу со списком объявлений (ЖК или поиск по фильтрам) и дождитесь загрузки";
    if (!ui._busy) { ui.el.go.disabled = !ready; }
  }

  function showProgress(text, frac) {
    ui.el.res.style.display = "none";
    ui.el.prog.style.display = "block";
    ui.el.pt.textContent = text;
    if (frac != null && isFinite(frac)) {
      ui.el.bar.classList.remove("indef");
      ui.el.bar.style.width = Math.min(100, Math.round(frac * 100)) + "%";
      ui.el.pp.textContent = Math.min(100, Math.round(frac * 100)) + "%";
    } else { ui.el.bar.classList.add("indef"); ui.el.bar.style.width = ""; ui.el.pp.textContent = ""; }
  }

  function showResults(stats, filename) {
    ui.el.prog.style.display = "none";
    ui.el.res.style.display = "block";
    ui.el.count.textContent = fmt(stats.count);
    const cov = stats.coverage != null ? stats.coverage : 100;
    ui.el.cov.textContent = cov + "%";
    // охват красим: 100% зелёным
    ui.el.cov.parentElement.classList.toggle("full", cov >= 100);
    ui.el.ppm.textContent = stats.ppmAvg ? fmt(stats.ppmAvg) : "—";
    ui.el.meta.textContent =
      "₽/м²: " + fmt(stats.ppmMin) + " – " + fmt(stats.ppmMax) +
      (stats.realMed != null ? " · реальный срок ~" + stats.realMed + " дн" : (stats.expAvg != null ? " · экспозиция ~" + stats.expAvg + " дн" : "")) +
      (stats.resets ? " · переподач " + stats.resets : "") +
      (stats.total ? " · всего " + fmt(stats.total) : "");
    if (stats.healthWarn) {
      ui.el.health.style.display = "flex";
      ui.el.healthtext.textContent = `Циан отвечал нестабильно (ретраев: ${stats.retries}${stats.totalDrift ? ", дрейф total: " + stats.totalDrift : ""}) — сверьте охват, при сомнении выгрузите ещё раз.`;
    } else { ui.el.health.style.display = "none"; }
    ui.el.cats.innerHTML = "";
    stats.byCat.forEach((x) => {
      const c = document.createElement("span"); c.className = "chip";
      c.textContent = (x.c === "Своб. планировка" ? "Своб." : x.c) + " " + x.n;
      ui.el.cats.appendChild(c);
    });
    if (stats.fact) { ui.el.fact.style.display = "block"; ui.el.facttext.textContent = stats.fact; }
    else { ui.el.fact.style.display = "none"; }
    ui.el.fname.textContent = "Файл скачан: " + filename;
  }

  // Инлайн-баннер ошибки в самой панели (вместо alert(), который блокирует
  // вкладку и легко теряется за окном браузера) с кнопкой повтора.
  function showError(msg) {
    ui.el.prog.style.display = "none";
    ui.el.res.style.display = "none";
    ui.el.err.style.display = "block";
    ui.el.errText.textContent = msg;
  }

  // Статус экспорта/импорта бэкапа истории — отдельный от showError()/run(),
  // т.к. кнопка «Повторить» в err-баннере жёстко привязана к повтору сбора.
  function showBkStatus(msg, ok) {
    ui.el.bkStatus.textContent = msg;
    ui.el.bkStatus.className = "bk " + (ok ? "ok" : "bad");
    ui.el.bkStatus.style.display = "block";
  }

  async function run() {
    if (ui._busy) return;
    const subj = detectSubject();   // не из кэша: тема тоже могла смениться (напр. смена ЖК без перезагрузки)
    // ВСЕГДА берём АКТУАЛЬНЫЙ запрос страницы на момент клика, а не ui._base из
    // кэша refreshHeader(): если пользователь поменял фильтр (площадь/цена/...)
    // после того как панель в последний раз обновлялась (авто-обновление идёт
    // только ~36с после загрузки страницы, см. ensure()), ui._base оставался бы
    // старым — и на экспорт уходили бы лоты без учёта фильтра.
    const liveQ = pageJsonQuery();
    let base = liveQ ? cleanBaseQuery(liveQ) : ui._base;
    const pageCnt = pageResultCount();
    console.log("[cian-excel] страница:", location.href, "| тема:", subj, "| на странице:", pageCnt, "| запрос:", base);
    if (!base) { showError("Не удалось получить запрос со страницы. " + OPEN_LIST_MSG); refreshHeader(); return; }

    ui._busy = true; ui.el.go.disabled = true; ui.el.go.textContent = "⏳ Собираю…";
    ui.el.err.style.display = "none";
    showProgress("Подключаюсь…", null);
    try {
      const { offers, totalsByRoom, totalInJk, health: collectHealth } = await collectAll(base, (text, got, total) => showProgress(text, total ? got / total : null));
      // Сверка с числом на странице — после сбора, чтобы не делать лишний запрос.
      if (pageCnt != null && totalInJk > pageCnt * 2 + 25) {
        const ok = confirm("На странице показано ~" + pageCnt + " объявлений, а запрос вернул " + totalInJk +
          ".\nВозможно, открыта не та вкладка результатов (фильтры не совпали).\n\nВсё равно сохранить " + offers.length + " лотов?");
        if (!ok) { ui._busy = false; ui.el.go.disabled = false; ui.el.go.textContent = "📊 Выгрузить в Excel"; ui.el.prog.style.display = "none"; return; }
      }
      if (!offers.length) { throw new Error("не собрано ни одного лота (войдите в аккаунт и пройдите капчу)"); }
      const rows = offers.map(normalize).sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
      const expInfo = enrichExposure(rows, subj.id);   // реальный срок экспозиции (учёт сбросов)
      showProgress("Готовлю Excel…", 1);
      const filename = `cian_${subj.slug}_${new Date().toISOString().slice(0, 10)}_${rows.length}лотов${isHealthWarn(collectHealth) ? "_проверить" : ""}.xlsx`;
      // buildWorkbook отдаёт дерево, buildXlsxBlob упаковывает его в zip
      // (сжатие потоковое, поэтому await)
      download(await buildXlsxBlob(buildWorkbook(subj, rows, totalsByRoom, totalInJk, collectHealth)), filename);
      showResults(computeStats(rows, totalInJk, expInfo, collectHealth), filename);
      ui.el.go.textContent = "📊 Выгрузить снова";
    } catch (e) {
      console.error(e);
      ui.el.go.textContent = "📊 Выгрузить в Excel";
      showError("Ошибка: " + e.message + ". Обновите страницу, дождитесь загрузки списка объявлений и нажмите «Повторить».");
    } finally {
      ui._busy = false; refreshHeader();
    }
  }

  function ensure() {
    try { if (!ui.mounted && document.body) buildPanel(); if (ui.mounted) refreshHeader(); }
    catch (e) { console.warn("[cian-excel] ui:", e); }
  }

  // Клик по иконке расширения (service worker -> bridge.js -> DOM-событие):
  // монтируем панель, если ещё нет, и разворачиваем её.
  window.addEventListener("cian-excel-toggle", () => {
    try {
      if (!ui.mounted) ensure();
      if (ui.root) ui.root.classList.remove("collapsed");   // развернуть панель
      refreshHeader();
    } catch (e) { console.warn("[cian-excel] toggle:", e); }
  });

  console.log("[cian-excel] загружен на", location.href);
  ensure();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", ensure);
  // Первые ~36с — часто (пока догружается SPA-страница Циан), затем реже, но
  // БЕССРОЧНО: пользователь может сменить фильтр (площадь/цена и т.п.) спустя
  // много больше 36с после открытия страницы, и шапка панели (ЖК/статус/на
  // странице N объявл.) должна оставаться живой. Сам экспорт (run()) в любом
  // случае берёт запрос заново на момент клика — это лишь для UI-индикации.
  let tries = 0;
  const ivFast = setInterval(() => {
    ensure();
    if (++tries > 30) { clearInterval(ivFast); setInterval(ensure, 4000); }
  }, 1200);
  let last = location.href;
  setInterval(() => { if (location.href !== last) { last = location.href; setTimeout(ensure, 800); } }, 1500);
})();
