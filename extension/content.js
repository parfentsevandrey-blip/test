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

  const CONFIG = {
    region: 1, delayMin: 300, delayMax: 700,
    maxPages: 54, pageSize: 28,          // реальный потолок Циан ~54 страницы (≈1512)
    minPriceSpan: 200000, priceCeiling: 3000000000,  // дробление по цене
    maxRetries: 4, backoffBase: 1500,    // ретраи на 429/5xx
    reqBudget: 400,                      // защита от runaway-запросов
  };
  const API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/";
  const ROOMS = [9, 7, 1, 2, 3, 4, 5, 6];

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
    const add = (offers) => offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) byId.set(id, o); });

    // Пагинация одного сегмента; seen = сколько УНИКАЛЬНЫХ id вернул сам сегмент.
    async function paginateSegment(filters, label) {
      const seg = new Set();
      let total = 0, page = 1, empty = 0;
      while (page <= CONFIG.maxPages && requests < CONFIG.reqBudget) {
        onProgress(`${label}стр.${page}…`, byId.size, grandTotal);
        let res; try { res = await apiFetch(withFilters(base, Object.assign({}, filters, { page }))); requests++; }
        catch (e) { if (page === 1) throw e; break; }
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
    console.log(`[cian-excel] ИТОГО ${byId.size}/${grandTotal} за ${requests} запросов`);
    return { offers: [...byId.values()], totalsByRoom, totalInJk: grandTotal };
  }

  // ---------- нормализация (как в Python/консольной версии) ----------
  const DEC = { without: "Без отделки", rough: "Черновая", fine: "Чистовая", preFine: "Предчистовая", prefine: "Предчистовая", designer: "Дизайнерская", clean: "Чистовая" };
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

  // ===== ОПРЕДЕЛЕНИЕ ОТДЕЛКИ/РЕМОНТА (definitive) ===========================
  // Слои по убыванию надёжности: 1) поле Циан repairType/decoration (= значения
  // фильтра «Ремонт и отделка»); 2) анализ текста описания; 3) нет данных.
  // Канонические категории = категории фильтра Циан.
  const FIN = {
    none: "Без отделки", rough: "Черновая", prefine: "Предчистовая (white box)",
    fine: "Чистовая", turnkey: "Под ключ / с мебелью",
    norepair: "Без ремонта", cosmetic: "Косметический", euro: "Евроремонт",
    designer: "Дизайнерский", some: "С ремонтом (тип не указан)",
  };
  // качество отделки для индекса привлекательности лота (НЕ порядок отображения
  // в сводке — тут именно ранг «чем выше, тем лучше для проживания»).
  const FIN_QUALITY_RANK = {
    [FIN.none]: 1, [FIN.rough]: 1, [FIN.norepair]: 1,
    [FIN.prefine]: 2,
    [FIN.cosmetic]: 3, [FIN.some]: 3,
    [FIN.fine]: 4,
    [FIN.euro]: 5, [FIN.turnkey]: 5,
    [FIN.designer]: 6,
  };
  // значение поля Циан -> категория
  const FIELD_FIN = {
    without: FIN.none, rough: FIN.rough, draft: FIN.rough,
    prefine: FIN.prefine, preFine: FIN.prefine, whitebox: FIN.prefine,
    fine: FIN.fine, clean: FIN.fine, finish: FIN.fine, chistovaya: FIN.fine,
    turnkey: FIN.turnkey, withFurniture: FIN.turnkey,
    cosmetic: FIN.cosmetic, euro: FIN.euro, good: FIN.euro, normal: FIN.cosmetic,
    designer: FIN.designer, design: FIN.designer,
  };
  // классификатор по тексту описания (порядок = приоритет; качество ремонта/отделки
  // важнее «меблировки»; явные категории раньше общих).
  const FIN_RULES = [
    [FIN.designer, /дизайнерск|дизайн[\s-]?проект|авторск\w*\s+(?:ремонт|отделк)|эксклюзивн\w*\s+(?:ремонт|отделк)/i],
    [FIN.euro, /евро[\s-]?ремонт|евроремонт/i],
    [FIN.prefine, /white\s?box|вайт[\s-]?бокс|предчистов|под\s?чистов\w*\s?отделк|подчистов/i],
    [FIN.rough, /чернов(?:ая|ой)\s?отделк|чернов(?:ая|ой)/i],
    [FIN.none, /без\s?отделк|нет\s?отделк/i],
    [FIN.fine, /чистов(?:ая|ой)\s?отделк|готов(?:ая|ой)\s?отделк|отделк[аи]\s?от\s?застройщик|с\s?(?:полной\s?)?отделк|сдан\w*\s?с\s?отделк/i],
    [FIN.norepair, /без\s?ремонт|требует\s?ремонт|под\s?ремонт|нужен\s?ремонт|убит\w*\s?(?:квартир|состоян)|в\s?строительн\w*\s?состоян/i],
    [FIN.cosmetic, /косметическ\w*|космет\b|жило[емй]\s?состоян|хорош\w*\s?состоян|сделан\s?ремонт|после\s?ремонт|свеж\w*\s?ремонт/i],
    [FIN.turnkey, /под\s?ключ|с\s?мебель|меблирован|с\s?(?:быт\w*\s?)?техник/i],
    [FIN.some, /\bремонт\b|с\s?ремонт|ремонт\s?есть/i],
  ];
  function finishFromText(t) {
    if (!t) return null;
    const s = t.toLowerCase();
    for (const [label, rx] of FIN_RULES) if (rx.test(s)) return label;
    return null;
  }
  function finishOf(o) {
    let rt = o.repairType; if (rt && typeof rt === "object") rt = rt.type || rt.value;
    let dc = o.decoration; if (dc && typeof dc === "object") dc = dc.type || dc.value;
    if (rt && FIELD_FIN[rt]) return { fin: FIELD_FIN[rt], src: "Циан-поле" };
    if (dc && FIELD_FIN[dc]) return { fin: FIELD_FIN[dc], src: "Циан-поле" };
    const ft = finishFromText(o.description);
    if (ft) return { fin: ft, src: "из описания" };
    if (rt || dc) return { fin: DEC[rt || dc] || String(rt || dc), src: "Циан-поле" };  // нестандартное значение
    return { fin: null, src: "" };
  }
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
    const fo = finishOf(o);
    const desc = (o.description || "").replace(/\s+/g, " ").trim();
    return {
      cianId: o.cianId || o.id || null, url: offerUrl(o), category: categoryOf(o),
      area, floor: o.floorNumber != null ? o.floorNumber : null,
      floors: dig(o, "building.floorsCount") || o.floorsCount || null, building: buildingOf(o),
      livingArea: numOr(o.livingArea), kitchenArea: numOr(o.kitchenArea),
      buildYear: dig(o, "building.buildYear") || null, material: mat ? (_MAT[mat] || mat) : null,
      metro: m.name, metroTime: m.time, addr: dig(o, "geo.userInput") || null,
      seller_type: sellerType(o), seller_name: sellerName(o),
      decoration: fo.fin, finishSrc: fo.src, description: desc.slice(0, 600),
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
          const cheapest = grp.slice().sort((a, b) => a.price - b.price)[0];
          r.dupCheapestUrl = cheapest.url; r.dupCheapestSeller = cheapest.seller_name || cheapest.seller_type || "";
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
  const HEADERS = ["№", "Категория", "Площадь, м²", "Этаж", "Корпус / секция", "Год дома", "Материал", "Метро", "До метро, мин", "Тип продавца", "Продавец", "Отделка/ремонт", "Источник отделки", "Цена, ₽", "Цена за м², ₽", "Откл. от средней", "Индекс привлекательности", "Δ цены с 1-й выгрузки, %", "Δ цены с прошлой, %", "Дата подачи (Циан)", "Срок Циан, дн", "Реальный срок, дн", "Переподач", "Дублей", "Первая дата (оценка)", "Описание", "Ссылка"];
  const COLW = [34, 78, 72, 56, 105, 62, 86, 110, 78, 88, 140, 130, 92, 105, 92, 86, 90, 92, 88, 100, 80, 95, 72, 60, 110, 320, 68];

  // Тепловая карта ₽/м²: насколько лот ниже/выше средней цены за м² по его
  // категории (зелёный = дешевле/недооценён, красный = дороже/переоценён). База —
  // средняя ₽/м² по той же комнатности; если в категории <3 лотов, берём общую.
  const HEAT = ["#63BE7B", "#86C97F", "#A9D585", "#CDE08B", "#FFEB84", "#FCC97F", "#F8A77B", "#F58368", "#F8696B"];
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
    let xml = rowXml([{ v: title, s: "title", merge: HEADERS.length - 1 }]) + rowXml([{ v: sub, s: "sub", merge: HEADERS.length - 1 }]) + rowXml([{}]) + rowXml(HEADERS.map((h) => ({ v: h, s: "hdr" })));
    const N = (v, s) => ({ v, t: v != null ? "Number" : "String", s });
    rows.forEach((r, i) => {
      let floorVal = "";                                   // один столбец «этаж/этажность» -> «5/15»
      if (r.floor != null && r.floors != null) floorVal = r.floor + "/" + r.floors;
      else if (r.floor != null) floorVal = String(r.floor);
      else if (r.floors != null) floorVal = "?/" + r.floors;
      const pctCell = (v) => v == null ? {} : { v: (v > 0 ? "+" : "") + v + "%", t: "String", s: v < 0 ? "pgood" : (v > 0 ? "pbad" : null) };
      xml += rowXml([
        { v: i + 1, t: "Number" }, { v: r.category },
        N(r.area, "area"), { v: floorVal }, { v: r.building }, N(r.buildYear), { v: r.material },
        { v: r.metro }, N(r.metroTime),
        { v: r.seller_type }, { v: r.seller_name }, { v: r.decoration }, { v: r.finishSrc },
        N(r.price, "num"),
        { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: r._heat || "num" },   // ₽/м² с подсветкой
        { v: r._dev, s: r._heat || null },                                            // откл. от средней, тот же цвет
        { v: r._score, t: r._score != null ? "Number" : "String", s: r._score == null ? null : (r._score >= 65 ? "scoreHi" : (r._score <= 35 ? "scoreLo" : null)) },
        pctCell(r.priceDeltaFirstPct), pctCell(r.priceDeltaLastRunPct),
        { v: r.published }, N(r.exposure),
        { v: r.realExposure, t: r.realExposure != null ? "Number" : "String", s: r.reset ? "warn" : null },
        { v: r.republish, t: "Number" }, { v: r.dupNow, t: "Number" }, { v: r.firstDate },
        { v: r.description }, r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]);
    });
    return worksheet(name, COLW, xml, true);
  }
  const CATS = ["Студия", "Своб. планировка", "1", "2", "3", "4+"];
  const ROOM_OF_CAT = { "Студия": [9], "Своб. планировка": [7], "1": [1], "2": [2], "3": [3], "4+": [4, 5, 6] };
  const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
  const num = (v) => (v == null ? { v: "—" } : { v, t: "Number", s: "num" });
  function summarySheet(subj, rows, totalsByRoom, totalInJk) {
    const present = CATS.filter((c) => rows.some((r) => r.category === c)), today = fmtDate(new Date());
    let xml = rowXml([{ v: `${subj.title}${subj.id ? " (ID " + subj.id + ")" : ""} — сводка`, s: "title", merge: 5 }]) +
      rowXml([{ v: `Данные Циан на ${today}. Собрано ${rows.length} лотов. «Частник» = собственник/агентство.`, s: "sub", merge: 5 }]) + rowXml([{}]);
    xml += rowXml([{ v: "ОХВАТ ВЫГРУЗКИ", s: "bold" }]) + rowXml(["Категория", "Собрано", "Всего на Циан", "% выдачи"].map((h) => ({ v: h, s: "hdr" })));
    let sumC = 0, sumT = 0;
    present.forEach((c) => {
      const got = rows.filter((r) => r.category === c).length;
      // totalsByRoom есть только если был добор (>лимита); иначе собрано = всё на Циан
      const tot = totalsByRoom ? (ROOM_OF_CAT[c].reduce((s, rm) => s + (totalsByRoom[rm] || 0), 0) || null) : got;
      sumC += got; if (tot) sumT += tot;
      xml += rowXml([{ v: c }, { v: got, t: "Number" }, num(tot), { v: tot ? Math.round((got / tot) * 100) + "%" : "—" }]);
    });
    xml += rowXml([{ v: "ИТОГО (категории)", s: "bold" }, { v: sumC, t: "Number", s: "bold" }, num(sumT || null), { v: sumT ? Math.round((sumC / sumT) * 100) + "%" : "—" }]);
    if (totalInJk) xml += rowXml([{ v: subj.isJk ? "Всего квартир в ЖК (Циан)" : "Всего по фильтру (Циан)", s: "bold" }, { v: rows.length, t: "Number" }, { v: totalInJk, t: "Number" }, { v: Math.round((rows.length / totalInJk) * 100) + "%" }]);
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
      xml += rowXml([{}]) + rowXml([{ v: "БЕНЧМАРК: ЦЕНА ПО ЭТАЖУ", s: "bold" }]) + rowXml(["Этаж", "Лотов", "Средняя ₽/м²", "Откл. от общей средней"].map((h) => ({ v: h, s: "hdr" })));
      const overallPpm = avg(withFloor.map((r) => r.ppm));
      TIERS.forEach((t) => {
        const sub = withFloor.filter((r) => floorTier(r) === t); if (!sub.length) return;
        const a = avg(sub.map((r) => r.ppm));
        xml += rowXml([{ v: t }, { v: sub.length, t: "Number" }, num(a), { v: overallPpm && a ? (a >= overallPpm ? "+" : "−") + Math.round(Math.abs(a / overallPpm - 1) * 100) + "%" : "—" }]);
      });
    }
    // бенчмарк по удалённости от метро
    const withMetro = rows.filter((r) => r.metroTime != null && r.ppm != null);
    if (withMetro.length >= 5) {
      const METRO_BUCKETS = [[0, 5, "0-5 мин"], [6, 10, "6-10 мин"], [11, 15, "11-15 мин"], [16, 20, "16-20 мин"], [21, Infinity, "20+ мин"]];
      xml += rowXml([{}]) + rowXml([{ v: "БЕНЧМАРК: ЦЕНА ПО УДАЛЁННОСТИ ОТ МЕТРО", s: "bold" }]) + rowXml(["До метро", "Лотов", "Средняя ₽/м²", "Откл. от общей средней"].map((h) => ({ v: h, s: "hdr" })));
      const overallPpm2 = avg(withMetro.map((r) => r.ppm));
      METRO_BUCKETS.forEach(([lo, hi, label]) => {
        const sub = withMetro.filter((r) => r.metroTime >= lo && r.metroTime <= hi); if (!sub.length) return;
        const a = avg(sub.map((r) => r.ppm));
        xml += rowXml([{ v: label }, { v: sub.length, t: "Number" }, num(a), { v: overallPpm2 && a ? (a >= overallPpm2 ? "+" : "−") + Math.round(Math.abs(a / overallPpm2 - 1) * 100) + "%" : "—" }]);
      });
    }

    // подсветка ₽/м² — легенда тепловой карты
    xml += rowXml([{}]) + rowXml([{ v: "ПОДСВЕТКА ₽/м² (в листах с лотами)", s: "bold" }]);
    xml += rowXml([{ v: "Зелёный — ниже средней по категории (дешевле/недооценён), красный — выше (дороже/переоценён).", s: "sub" }]);
    xml += rowXml([{ v: "База — средняя ₽/м² по той же комнатности; при <3 лотах в категории берётся общая средняя.", s: "sub" }]);
    xml += rowXml([{ v: "−20% и ниже", s: "h1" }, { v: "−10%", s: "h3" }, { v: "средняя", s: "h5" }, { v: "+10%", s: "h7" }, { v: "+20% и выше", s: "h9" }]);

    // отделка / ремонт
    const FIN_ORDER = ["Без отделки", "Черновая", "Предчистовая (white box)", "Чистовая", "Под ключ / с мебелью", "Без ремонта", "Косметический", "Евроремонт", "Дизайнерский", "С ремонтом (тип не указан)"];
    const finCount = {};
    rows.forEach((r) => { if (r.decoration) finCount[r.decoration] = (finCount[r.decoration] || 0) + 1; });
    const byField = rows.filter((r) => r.finishSrc === "Циан-поле").length;
    const byText = rows.filter((r) => r.finishSrc === "из описания").length;
    const noFin = rows.filter((r) => !r.decoration).length;
    xml += rowXml([{}]) + rowXml([{ v: "ОТДЕЛКА / РЕМОНТ (определено)", s: "bold" }]) + rowXml(["Категория", "Лотов", "Доля"].map((h) => ({ v: h, s: "hdr" })));
    FIN_ORDER.filter((k) => finCount[k]).forEach((k) => {
      xml += rowXml([{ v: k }, { v: finCount[k], t: "Number" }, { v: Math.round(finCount[k] / rows.length * 100) + "%" }]);
    });
    if (noFin) xml += rowXml([{ v: "Не определена" }, { v: noFin, t: "Number" }, { v: Math.round(noFin / rows.length * 100) + "%" }]);
    xml += rowXml([{ v: "Источник: поле Циан / описание / нет", s: "sub" }, { v: `${byField} / ${byText} / ${noFin}` }]);

    // экспозиция
    const real = rows.map((r) => r.realExposure).filter((x) => x != null);
    const cian = rows.map((r) => r.exposure).filter((x) => x !== "" && x != null);
    const resets = rows.filter((r) => r.reset).length;
    xml += rowXml([{}]) + rowXml([{ v: "СРОК ЭКСПОЗИЦИИ", s: "bold" }]);
    xml += rowXml([{ v: "Реальный срок (медиана/среднее), дн" }, num(real.length ? real.slice().sort((a, b) => a - b)[Math.floor(real.length / 2)] : null), num(avg(real))]);
    xml += rowXml([{ v: "По счётчику Циан (среднее), дн" }, { v: "" }, num(avg(cian))]);
    xml += rowXml([{ v: "Найдено сбросов даты (переподач)" }, { v: "" }, { v: resets, t: "Number", s: resets ? "warn" : null }]);
    xml += rowXml([{}]) + rowXml([{ v: "МЕТОДИКА: «Реальный срок» = сегодня − самая ранняя дата подачи среди дублей одной квартиры", s: "sub" }]);
    xml += rowXml([{ v: "и за всю историю наблюдений; сброс/переподача даты Циан его не уменьшает. Чем чаще выгружать — тем точнее.", s: "sub" }]);
    return worksheet("Сводка", [220, 96, 96, 96, 84, 84], xml, false);
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
    let xml = rowXml([{ v: `${subj.title} — дубли: разброс цены между продавцами`, s: "title", merge: HDR.length - 1 }]) +
      rowXml([{ v: "Одна и та же квартира выставлена несколькими продавцами по разной цене. Сортировка по разбросу, ₽.", s: "sub", merge: HDR.length - 1 }]) +
      rowXml([{}]) + rowXml(HDR.map((h) => ({ v: h, s: "hdr" })));
    items.forEach((r) => {
      const floorVal = r.floor != null && r.floors != null ? r.floor + "/" + r.floors : (r.floor != null ? String(r.floor) : "");
      xml += rowXml([
        { v: r.category }, { v: r.area, t: r.area != null ? "Number" : "String", s: "area" }, { v: floorVal }, { v: r.building },
        { v: r.dupNow, t: "Number" }, { v: r.dupMinPrice, t: "Number", s: "num" }, { v: r.dupMaxPrice, t: "Number", s: "num" },
        { v: r.dupSpreadAbs, t: "Number", s: "num" }, { v: r.dupSpreadPct != null ? r.dupSpreadPct + "%" : "" },
        { v: r.dupCheapestSeller }, r.dupCheapestUrl ? { v: "Циан →", href: r.dupCheapestUrl, s: "link" } : {},
      ]);
    });
    return worksheet("Дубли_разброс_цен", W, xml, true);
  }

  // Динамика между запусками: что появилось/пропало/подешевело/подорожало с
  // прошлой выгрузки этого же ЖК/выборки (сравнение по локальному снимку).
  function changesSheet(subj, changes) {
    if (!changes.hasPrev) return null;
    const HDR = ["Тип", "Категория", "Корпус", "Этаж", "Цена, ₽", "Было, ₽", "Δ, %", "Ссылка"];
    const W = [90, 78, 105, 56, 100, 100, 76, 68];
    let xml = rowXml([{ v: `${subj.title} — изменения с прошлой выгрузки`, s: "title", merge: HDR.length - 1 }]) +
      rowXml([{ v: `Новых: ${changes.appeared.length} · Пропало: ${changes.vanished.length} · Подешевело: ${changes.cheaper.length} · Подорожало: ${changes.pricier.length}`, s: "sub", merge: HDR.length - 1 }]) +
      rowXml([{}]) + rowXml(HDR.map((h) => ({ v: h, s: "hdr" })));
    const row = (type, r, price, from, pct, styleId) => {
      const floorVal = r.floor != null ? String(r.floor) : "";
      xml += rowXml([
        { v: type, s: styleId || "bold" }, { v: r.category }, { v: r.building }, { v: floorVal },
        { v: price, t: price != null ? "Number" : "String", s: "num" }, { v: from, t: from != null ? "Number" : "String", s: "num" },
        { v: pct != null ? (pct > 0 ? "+" : "") + pct + "%" : "", s: pct == null ? null : (pct < 0 ? "pgood" : "pbad") },
        r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]);
    };
    changes.cheaper.forEach((c) => row("↓ Подешевел", c.r, c.r.price, c.from, c.pct, "pgood"));
    changes.pricier.forEach((c) => row("↑ Подорожал", c.r, c.r.price, c.from, c.pct, "pbad"));
    changes.appeared.forEach((r) => row("+ Новый", r, r.price, null, null, null));
    changes.vanished.forEach((r) => row("− Пропал (продан/снят?)", r, null, r.price, null, "warn"));
    return worksheet("Изменения", W, xml, true);
  }

  function buildWorkbook(subj, rows, totalsByRoom, totalInJk) {
    rows = rows.slice().sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
    computeHeat(rows);                                    // тепловая карта ₽/м² (зелёный↔красный)
    computeScore(rows);                                   // индекс привлекательности лота
    const changes = computeChanges(subj, rows);            // динамика с прошлой выгрузки (+ сохраняет новый снимок)
    const today = fmtDate(new Date()), sheets = [summarySheet(subj, rows, totalsByRoom, totalInJk)];
    const changesXml = changesSheet(subj, changes); if (changesXml) sheets.push(changesXml);
    const topXml = topLotsSheet(subj, rows); if (topXml) sheets.push(topXml);
    const src = `Источник: Циан${subj.id ? " (ID " + subj.id + ")" : ""}, ${today}. Сортировка по ₽/м². Подсветка ₽/м²: зелёный — дешевле средней по категории, красный — дороже. «Реальный срок» учитывает переподачи.`;
    sheets.push(dataSheet("Все_лоты", `${subj.title} — все лоты`, src, rows));
    const sn = { "Студия": "Студия", "Своб. планировка": "Своб_планировка", "1": "1-комн", "2": "2-комн", "3": "3-комн", "4+": "4-комн" };
    CATS.forEach((c) => { const sub = rows.filter((r) => r.category === c); if (sub.length) sheets.push(dataSheet(sn[c], `${subj.title} — ${c}`, `Собрано ${sub.length}. Сортировка по ₽/м².`, sub)); });
    const dupXml = dupSpreadSheet(subj, rows); if (dupXml) sheets.push(dupXml);
    const heatStyles = HEAT.map((c, i) => `<Style ss:ID="h${i + 1}"><NumberFormat ss:Format="#,##0"/><Interior ss:Color="${c}" ss:Pattern="Solid"/><Alignment ss:Horizontal="Right" ss:Vertical="Center"/></Style>`).join("");
    const styles = `<Styles><Style ss:ID="Default" ss:Name="Normal"><Alignment ss:Vertical="Center"/><Font ss:FontName="Calibri" ss:Size="11"/></Style><Style ss:ID="hdr"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#1F2A44" ss:Pattern="Solid"/><Alignment ss:Horizontal="Center" ss:Vertical="Center" ss:WrapText="1"/></Style><Style ss:ID="title"><Font ss:Bold="1" ss:Size="13"/></Style><Style ss:ID="sub"><Font ss:Italic="1" ss:Color="#555555" ss:Size="9"/></Style><Style ss:ID="bold"><Font ss:Bold="1"/></Style><Style ss:ID="num"><NumberFormat ss:Format="#,##0"/></Style><Style ss:ID="area"><NumberFormat ss:Format="0.0"/></Style><Style ss:ID="link"><Font ss:Color="#1155CC" ss:Underline="Single"/></Style><Style ss:ID="warn"><Font ss:Bold="1" ss:Color="#C25400"/></Style><Style ss:ID="scoreHi"><Font ss:Bold="1" ss:Color="#006100"/><Interior ss:Color="#C6EFCE" ss:Pattern="Solid"/></Style><Style ss:ID="scoreLo"><Font ss:Bold="1" ss:Color="#9C0006"/><Interior ss:Color="#FFC7CE" ss:Pattern="Solid"/></Style><Style ss:ID="pgood"><Font ss:Color="#1D7A43" ss:Bold="1"/></Style><Style ss:ID="pbad"><Font ss:Color="#C25400" ss:Bold="1"/></Style>${heatStyles}</Styles>`;
    return `<?xml version="1.0" encoding="UTF-8"?>\n<?mso-application progid="Excel.Sheet"?>\n<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" xmlns:html="http://www.w3.org/TR/REC-html40">${styles}${sheets.join("")}</Workbook>`;
  }
  const slug = (s) => s.toLowerCase().replace(/\s+/g, "-").replace(/[^0-9a-zа-яё_\-]/g, "") || "jk";
  function download(xml, name) {
    const blob = new Blob(["﻿", xml], { type: "application/vnd.ms-excel;charset=utf-8" });
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
  function computeStats(rows, totalInJk, expInfo) {
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
  .lnk{color:#2c6ecb;cursor:pointer;text-decoration:underline}
  .btn:focus-visible,.min:focus-visible,.fab:focus-visible,.err .retry:focus-visible{
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
              '<div class="cats" id="s-cats"></div>' +
              '<div class="fact" id="s-fact"><span class="lab">💡 Любопытный факт</span><span id="s-facttext"></span></div>' +
              '<div class="file" id="s-file"><span>✓</span><span id="s-fname"></span></div>' +
            '</div>' +
            '<div class="err" id="err"><span class="et" id="err-text"></span><button class="retry" id="err-retry">↻ Повторить</button></div>' +
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
      meta: $("#s-meta"), cats: $("#s-cats"), fact: $("#s-fact"), facttext: $("#s-facttext"),
      file: $("#s-file"), fname: $("#s-fname"), foot: $("#foot"),
      err: $("#err"), errText: $("#err-text"), errRetry: $("#err-retry"),
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
      const { offers, totalsByRoom, totalInJk } = await collectAll(base, (text, got, total) => showProgress(text, total ? got / total : null));
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
      const filename = `cian_${subj.slug}_${new Date().toISOString().slice(0, 10)}.xls`;
      download(buildWorkbook(subj, rows, totalsByRoom, totalInJk), filename);
      showResults(computeStats(rows, totalInJk, expInfo), filename);
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
