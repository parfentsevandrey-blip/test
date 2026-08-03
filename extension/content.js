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
    region: 1,
    // ТЕМП. Все константы здесь, чтобы правка по первым боевым журналам была
    // однострочной. Числа объявляются ВРЕМЕННЫМИ: они взяты из чужих парсеров
    // того же эндпоинта (2–11 с, мода 4–8 с; самый дисциплинированный 3–8 с),
    // а не из наших замеров — наших пока нет. Прежние 300–700 мс делали нас
    // в 4–20 раз быстрее всех, и это никогда не измерялось.
    pacer: {
      start: 2000, floor: 800, ceil: 60000,
      // Джиттер ОДНОСТОРОННИЙ, вверх: ×(1…1.5). Симметричный (×0.75…1.25) увёл
      // бы интервал ниже пола, и «пол 800 мс» перестал бы значить пол; клампить
      // же после симметричного джиттера — значит получить ровно на полу кучу
      // одинаковых значений, то есть потерять сам джиттер там, где мы быстрее
      // всего. Постоянный интервал — самый заметный признак робота.
      jitter: 0.5,
      slowdown: 2,           // 429/5xx: мультипликативно вверх
      speedup: 0.9,          // после серии чистых ответов: осторожно вниз
      // Разгон нарочно медленный. При speedupAfter=5 обычный прогон (12–60
      // страниц) успевал сползти к полу, и start становился декоративным:
      // steady state задавал бы пол. При 20 стартовое значение и есть темп
      // типичного прогона, а пол достаётся только длинным и спокойным.
      speedupAfter: 20,
    },
    maxPages: 54, pageSize: 28,          // реальный потолок Циан ~54 страницы (≈1512)
    minPriceSpan: 200000, priceCeiling: 3000000000,  // дробление по цене
    maxRetries: 4, backoffBase: 1500,    // ретраи на 429/5xx
    waitCeiling: 60000,                  // потолок ОДНОЙ паузы между попытками
    reqBudget: 400,                      // мягкий лимит ЛОГИЧЕСКИХ страниц
    // Настоящий предохранитель. reqBudget считает успешные страницы, а ретраи
    // живут внутри apiFetch и в него не входят: измерено на стенде (негативный
    // контроль «снят бюджет реальных HTTP») — 400 «страниц» превращались в 2408
    // реальных обращений к Циан, 2,6 часа сбора; потолок формулы
    // 2*reqBudget*maxRetries + maxRetries = 3204. Для точной отделки перебором
    // фильтра это была бы десятитысячная нагрузка. Считаем то, что видит Циан.
    httpBudget: 900,
    // Второй предохранитель — по стенным часам. Бюджет обращений сам по себе
    // времени не ограничивает: 900 обращений, каждое с бэкоффом до waitCeiling,
    // измерены на стенде как 42 минуты. Через полчаса человек всё равно уже
    // ушёл, и вкладка держится зря.
    timeBudgetMs: 30 * 60 * 1000,
    // Недобор в 1-2 лота дроблением не ищем. Циан регулярно считает в
    // aggregatedCount лот, которого в выдаче нет, и погоня за ним стоит ВСЕГО
    // бюджета: измерено — один фантом превращал честные 12 запросов в 400,
    // из них 289 возвращали ноль лотов. Осознанный размен: до двух лотов
    // против четырёхсот запросов.
    minShortfall: 2,
  };
  const API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/";
  const ROOMS = [9, 7, 1, 2, 3, 4, 5, 6];
  // Диагностика качества сбора текущего run() — сбрасывается в начале
  // collectAll(), пишется из apiFetch()/paginateSegment(). null вне сбора.
  let health = null;
  // Недобор и исчерпание бюджета — куда более веский повод для предупреждения,
  // чем дрейф total (у Циан он норма). Порог по дрейфу поднят с нуля.
  // Явные флаги не зависят от числа собранных страниц: блокировка на ПЕРВОМ же
  // обращении оставляет requests = 0, и прогон, не собравший ничего, выглядел
  // бы штатным. Относительные признаки — только когда есть от чего считать.
  const isHealthWarn = (h) => !!(h && (
    h.budgetExhausted || h.cancelled || h.wafBlock ||
    (h.requests && (h.shortfall > 0 || (h.retries / h.requests > 0.15) || h.totalDrift > 2))));
  // ПОЧЕМУ предупреждение. Одного флага мало: «ретраев: 0» под жёлтой плашкой
  // сбивает с толку, а причины у неё теперь разные. Список общий для панели и
  // листа «Сводка» — чтобы человек видел одно и то же в обоих местах.
  const healthReasons = (h) => {
    const out = [];
    if (!h) return out;
    if (h.wafBlock) out.push("Циан прервал сбор проверкой браузера или блокировкой");
    if (h.cancelled) out.push("сбор остановлен по кнопке «Отмена»");
    if (h.budgetExhausted) out.push("исчерпан бюджет запросов — сбор остановлен досрочно");
    if (!h.requests) return out;
    if (h.shortfall > 0) out.push(`не отдано ${h.shortfall} объявлений из заявленных Циан`);
    if (h.retries / h.requests > 0.15) out.push(`много отказов: неудачных попыток ${h.retries} на ${h.requests} страниц`);
    if (h.totalDrift > 2) out.push(`число объявлений плавало между страницами (расхождений: ${h.totalDrift})`);
    return out;
  };
  // Разбивка отказов по виду. Порядок фиксированный (числовые коды по
  // возрастанию, «сеть» последней), иначе строка в книге плясала бы от прогона
  // к прогону и её нельзя было бы сравнивать между выгрузками.
  const STATUS_LABEL = { network: "сеть (обрыв связи)", 429: "429 (Циан троттлит)", 403: "403 (блокировка)" };
  const statusBreakdown = (m) => Object.keys(m || {})
    .sort((a, b) => (a === "network") - (b === "network") || (parseInt(a, 10) || 0) - (parseInt(b, 10) || 0))
    .map((k) => `${STATUS_LABEL[k] || (/^5\d\d$/.test(k) ? k + " (сбой на стороне Циан)" : k)} × ${m[k]}`)
    .join(" · ");

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

  // ===== Отмена сбора =======================================================
  // Отмены не было ни в каком виде: нажать «Выгрузить» и передумать было
  // нельзя, вкладка оставалась занята до конца — а конец в патологии наступал
  // через десятки минут. Токен живёт РОВНО на время одного прогона.
  let cancelToken = null;
  function CancelError() { const e = new Error("сбор отменён"); e.cancelled = true; return e; }
  function beginRun() {
    // AbortController есть во всех целевых браузерах, но код исполняется и на
    // стенде — проверяем наличие, а не веру в среду.
    const ac = typeof AbortController === "function" ? new AbortController() : null;
    cancelToken = { cancelled: false, wakes: new Set(), ac };
    return cancelToken;
  }
  function endRun() { cancelToken = null; }
  function cancelRun() {
    const t = cancelToken;
    if (!t || t.cancelled) return false;
    t.cancelled = true;
    // Разбудить спящих СРАЗУ. Без этого кнопка декоративна: на бэкоффе в
    // waitCeiling=60 с пользователь жмёт «Отмена» и ещё минуту смотрит на то же
    // самое. Плюс обрываем запрос, который уже в полёте.
    t.wakes.forEach((f) => f());
    try { if (t.ac) t.ac.abort(); } catch (e) { /* ignore */ }
    return true;
  }
  const isCancelled = () => !!(cancelToken && cancelToken.cancelled);

  // Прерываемый сон: обычный setTimeout сделал бы «Отмену» декоративной.
  const sleep = (ms) => new Promise((resolve) => {
    const t = cancelToken;
    if (t && t.cancelled) return resolve();
    let done = false;
    const fire = () => { if (done) return; done = true; if (t) t.wakes.delete(fire); resolve(); };
    setTimeout(fire, ms);
    if (t) t.wakes.add(fire);
  });
  // ===== Темп: AIMD, зазор от ЗАВЕРШЕНИЯ прошлого обращения ================
  // Живёт на уровне ПРОГОНА, а не сегмента: раньше pause() стояла только внутри
  // paginateSegment, и каждый выход из сегмента (любой break) и каждый переход
  // к следующему ценовому диапазону в priceSplit шли без задержки вовсе.
  // Измерено на стенде: 24 из 53 пар запросов уходили залпом, средний интервал
  // 251 мс при обещанных 300 — и максимальная плотность приходилась ровно на
  // аварийный режим, когда сервер и так недоволен.
  let pacer = null;
  // Момент ЗАВЕРШЕНИЯ последнего обращения. Общий для темпа и для журнала:
  // «зазор» в обоих означает одно и то же, иначе журнал мерил бы не то, что
  // соблюдает пацер.
  let lastDoneAt = 0;
  const paceReset = () => { pacer = { gap: CONFIG.pacer.start, clean: 0 }; lastDoneAt = 0; };
  // Ждём не «сколько-нибудь», а РОВНО столько, сколько не хватает до нужного
  // зазора: после долгого бэкоффа доплачивать уже нечего. Приём взят у самого
  // дисциплинированного из измеренных парсеров.
  const pause = () => {
    if (!pacer) return Promise.resolve();
    const target = pacer.gap * (1 + Math.random() * CONFIG.pacer.jitter);
    const since = lastDoneAt ? Date.now() - lastDoneAt : Infinity;
    const wait = Math.max(0, target - since);
    return wait > 0 ? sleep(wait) : Promise.resolve();
  };
  // Классическая асимметрия AIMD: резко вверх по паузе, осторожно вниз. ×2
  // против ×0.9 — это семь к одному за шаг, поэтому разгон после отказов идёт
  // медленно, а торможение мгновенно.
  // Замедляемся ОДИН раз на логический запрос, а не на каждую попытку. Четыре
  // отказа подряд по одной странице — это одно событие перегрузки, а не четыре:
  // реагируя на каждую попытку, пацер за одну страницу улетал бы в ×16 и
  // мгновенно пинился к потолку. Правило «одно снижение на событие» — то же,
  // что в классическом AIMD.
  function paceFeedback(ok) {
    if (!pacer) return;
    if (!ok) { pacer.gap = Math.min(pacer.gap * CONFIG.pacer.slowdown, CONFIG.pacer.ceil); pacer.clean = 0; return; }
    if (++pacer.clean >= CONFIG.pacer.speedupAfter) {
      pacer.clean = 0;
      pacer.gap = Math.max(pacer.gap * CONFIG.pacer.speedup, CONFIG.pacer.floor);
    }
  }
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
  // Бросается, когда исчерпан бюджет реальных обращений. Отдельный тип нужен,
  // чтобы collectAll отличил его от отказа Циан и вернул частичный результат,
  // а не потерял всё собранное.
  function BudgetError(what) { const e = new Error("исчерпан бюджет " + (what || "запросов")); e.budget = true; return e; }
  // WAF Циан. ТЕРМИНАЛЬНОЕ состояние, а не повод для бэкоффа: 403 — это страница
  // cian_waf_block без капчи и без выхода, лечится сменой IP или письмом в
  // поддержку. Ретраить его вредно вдвойне — репутация копится на IP САМОГО
  // пользователя и между запусками, а не в пределах прогона.
  function WafError(msg) { const e = new Error(msg); e.waf = true; return e; }
  // Пауза, о которой видно в панели. Раньше во время бэкоффа onProgress не
  // вызывался вовсе: панель молча показывала прежнее сообщение всю паузу, и
  // отличить «ждём» от «зависло» было нельзя.
  let onWait = null;
  const waitNotice = (what, ms, attempt) => {
    if (onWait) onWait(`Циан отказал (${what}) — жду ${Math.round(ms / 1000)} с, попытка ${attempt + 1} из ${CONFIG.maxRetries}…`);
  };

  // ===== Телеметрия: одна запись на КАЖДУЮ попытку ==========================
  // Не на логическую страницу: страница стоит до maxRetries обращений, и Циан
  // видит именно попытки. Журнал живёт ТОЛЬКО в памяти прогона и уезжает листом
  // в скачиваемую книгу; в хранилище попадает лишь агрегат (замер: 30 прогонов
  // сырого журнала = 2.1 МБ UTF-16 при квоте 5 МиБ на весь ориджин).
  let tele = null, teleMeta = null;
  const TELE_MAX = 4000;
  const hdr = (r, name) => {
    try { return (r && r.headers && r.headers.get && r.headers.get(name)) || ""; }
    catch (e) { return ""; }
  };
  // Content-Type и Content-Length — из CORS-safelist, то есть ЕДИНСТВЕННОЕ, что
  // кросс-ориджин читается гарантированно. На них и строится детектор WAF.
  const teleLog = (rec) => { if (tele && tele.length < TELE_MAX) tele.push(rec); };

  async function apiFetch(body, meta) {
    let delay = CONFIG.backoffBase, lastErr = null;
    const seg = (meta && meta.seg) || "", pg = (meta && meta.page) || null;
    // Одно снижение темпа на ЛОГИЧЕСКИЙ запрос: см. paceFeedback.
    let braked = false;
    for (let attempt = 1; attempt <= CONFIG.maxRetries; attempt++) {
      // Считаем ДО обращения и на КАЖДОЙ попытке: это единственная точка, где
      // видны все обращения без исключения. Здесь же — оба предохранителя и
      // отмена: до fetch, а не после, иначе лишнее обращение всё равно уйдёт.
      if (isCancelled()) throw CancelError();
      if (health) {
        if (health.http >= CONFIG.httpBudget) throw BudgetError("запросов");
        if (health.t0 && Date.now() - health.t0 >= CONFIG.timeBudgetMs) throw BudgetError("времени");
        health.http++;
      }
      // Темп соблюдается ЗДЕСЬ — в единственной точке, через которую проходит
      // каждое обращение. Поэтому пауза есть и между страницами, и на выходе
      // из сегмента, и на переходе к следующему ценовому диапазону.
      await pause();
      if (isCancelled()) throw CancelError();
      const startedAt = Date.now();
      // gap считается от ЗАВЕРШЕНИЯ прошлого обращения, а не от его начала:
      // проверяет темп по факту, а не по намерению.
      const gapMs = lastDoneAt ? startedAt - lastDoneAt : 0;
      const rec = { t: health ? startedAt - health.t0 : 0, att: attempt, gap: gapMs, seg, page: pg };
      let logged = false;
      try {
        const r = await fetch(API, {
          method: "POST", headers: { "Content-Type": "application/json", Accept: "*/*" },
          body: JSON.stringify(body), credentials: "include",
          signal: cancelToken && cancelToken.ac ? cancelToken.ac.signal : undefined,
        });
        const dur = Date.now() - startedAt;
        lastDoneAt = Date.now();
        const ctRaw = hdr(r, "Content-Type");
        const ct = /html/i.test(ctRaw) ? "html" : /json/i.test(ctRaw) ? "json" : (ctRaw.split(";")[0] || "?");
        const len = parseInt(hdr(r, "Content-Length"), 10) || null;
        const raRaw = hdr(r, "Retry-After");
        Object.assign(rec, { dur, st: r.status, ct, len, raSeen: raRaw ? 1 : 0 });
        // «200 с HTML-телом» — это не успех, а проверка браузера; в журнале ему
        // нужен свой код, иначе он растворится среди честных двухсоток.
        if (r.status === 200 && ct === "html") rec.st = "HTML";
        teleLog(rec); logged = true;   // дальше поля дописываются в ту же запись
        // Обратная связь темпа: всё, кроме честной двухсотки, — повод замедлиться.
        const clean = r.status === 200 && ct !== "html";
        if (clean) paceFeedback(true);
        else if (!braked) { braked = true; paceFeedback(false); }

        // Проверка типа идёт ДО r.json(). Раньше капча, отданная с кодом 200 и
        // HTML-телом, превращалась в SyntaxError, падала в общий catch и
        // РЕТРАИЛАСЬ как сетевая ошибка: код отвечал на антибот увеличением
        // нагрузки, а в диагностике это выглядело как «сеть × 4».
        if (r.status === 403 || ct === "html") {
          throw WafError(r.status === 403
            ? "Циан заблокировал доступ (HTTP 403). Это блокировка сети, а не капча: подождите 10–15 минут, смените сеть или войдите в аккаунт на cian.ru."
            : "Циан прислал проверку браузера вместо данных. Откройте cian.ru в этой же вкладке, пройдите проверку и повторите.");
        }
        if (r.status === 200) {
          const d = await r.json(); const data = d.data || d;
          let offers = data.offersSerialized || data.offers || data.items || [];
          offers = offers.map((it) => (it && it.offer ? it.offer : it));
          // aggregatedCount = всего по фильтру; offerCount иногда = размер страницы
          const total = data.aggregatedCount || data.offerCount || data.offersCount || data.totalCount || offers.length;
          const t = parseInt(total, 10) || offers.length;
          rec.n = offers.length; rec.tot = t;
          return { offers, total: t };
        }
        if (r.status === 429 || r.status >= 500) {
          if (health) { health.retries++; health.retryStatuses[r.status] = (health.retryStatuses[r.status] || 0) + 1; }
          // Retry-After берём с ПОТОЛКОМ. Циан вправе прислать 3600, и без
          // ограничения одна такая шапка замораживает вкладку на час: прогресс
          // стоит, кнопка не отвечает, отменить нечем. Лучше подождать минуту и
          // честно упасть, чем выглядеть зависшим.
          const ra = parseInt(raRaw, 10);
          const raMs = ra > 0 ? Math.min(ra * 1000, CONFIG.waitCeiling) : 0;
          const wait = Math.min(raMs || delay, CONFIG.waitCeiling) + Math.random() * 400;
          console.warn(`[cian-excel] HTTP ${r.status} — пауза ${Math.round(wait / 1000)}s (попытка ${attempt}/${CONFIG.maxRetries})`);
          lastErr = "HTTP " + r.status;
          // Перед заведомо последней попыткой спать незачем: раньше на пути
          // 429/5xx это добавляло целый интервал ожидания к каждому фатальному
          // отказу (на сетевом пути такой сон уже был отсечён).
          if (attempt >= CONFIG.maxRetries) break;
          waitNotice(r.status, wait, attempt);
          await sleep(wait); delay *= 2;
          if (isCancelled()) throw CancelError();
          continue;
        }
        throw new Error("HTTP " + r.status);
      } catch (e) {
        // Отмена, бюджет и WAF — не отказы, которые лечатся повтором: ни один
        // из них не идёт ни в статистику неудачных попыток, ни в бэкофф.
        if (e && (e.cancelled || e.budget || e.waf)) throw e;
        if (e && (e.name === "AbortError" || /aborted/i.test((e && e.message) || ""))) throw CancelError();
        lastErr = (e && e.message) || String(e);
        if (/403/.test(lastErr)) throw WafError(lastErr);
        // Обрыв связи и битый JSON под честным content-type в журнал не попали
        // (записи ещё нет) — дописываем здесь, иначе «каждое обращение
        // восстановимо по журналу» перестанет быть правдой.
        if (!logged) {
          lastDoneAt = Date.now();
          Object.assign(rec, { dur: lastDoneAt - startedAt, st: "NET", ct: null, len: null, raSeen: 0 });
          teleLog(rec);
          if (!braked) { braked = true; paceFeedback(false); }   // обрыв связи — тоже событие перегрузки
        }
        // Считаем КАЖДУЮ неудачную попытку, включая последнюю. Раньше сетевой
        // путь инкрементировал после проверки на исчерпание и давал 3 против 4
        // на пути 429 при одинаковых четырёх обращениях — сравнивать статусы
        // между собой было нельзя.
        if (health) { health.retries++; health.retryStatuses.network = (health.retryStatuses.network || 0) + 1; }
        if (attempt >= CONFIG.maxRetries) throw new Error(lastErr);
        const wait = delay + Math.random() * 400;
        waitNotice("сеть", wait, attempt);
        await sleep(wait); delay *= 2;
        if (isCancelled()) throw CancelError();
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
    health = { retries: 0, retryStatuses: {}, totalDrift: 0, http: 0, budgetExhausted: false, cancelled: false, t0: Date.now() };
    beginRun(); paceReset();
    onWait = (text) => onProgress(text, byId.size, grandTotal);
    tele = []; teleMeta = null; lastDoneAt = 0;
    const add = (offers) => offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) byId.set(id, o); });
    // Короткая метка сегмента: «все», «r2», «r2 ₽5.0-9.0». Читается глазами в
    // листе «Журнал_сбора» и не раздувает журнал.
    const mln = (v) => (v == null ? "" : (v / 1e6).toFixed(1));
    const segLabel = (f) => {
      const r = f.room != null ? "r" + f.room : "все";
      return (f.priceGte != null || f.priceLte != null) ? `${r} ₽${mln(f.priceGte)}-${mln(f.priceLte)}` : r;
    };

    // Пагинация одного сегмента; seen = сколько УНИКАЛЬНЫХ id вернул сам сегмент.
    async function paginateSegment(filters, label) {
      const seg = new Set();
      let total = 0, firstTotal = null, page = 1, empty = 0;
      while (page <= CONFIG.maxPages && requests < CONFIG.reqBudget) {
        onProgress(`${label}стр.${page}…`, byId.size, grandTotal);
        // Метка сегмента в журнале — то, чем запись сопоставляется с маршрутом:
        // без неё «дорого» и «много страниц» неразличимы.
        let res; try { res = await apiFetch(withFilters(base, Object.assign({}, filters, { page })), { seg: segLabel(filters), page }); requests++; }
        // Отказ на 2-й и дальше странице обрывает СЕГМЕНТ, а не прогон: остаток
        // ещё можно добрать дроблением. Но отмена, бюджет и БЛОКИРОВКА — про
        // весь прогон. Раньше waf сюда не входил, и после первого же 403 сбор
        // продолжал ходить к Циан: обрыв на странице ≥2 неотличим от штатного
        // недобора, поэтому запускалось дробление — планировщик отвечал на
        // отказ сервера ростом нагрузки.
        catch (e) { if (page === 1 || (e && (e.cancelled || e.budget || e.waf))) throw e; break; }
        // Циан иногда отдаёт РАЗНЫЙ aggregatedCount на разных страницах ОДНОГО
        // и того же сегмента (ротация/нестабильность выдачи) — фиксируем как
        // диагностику качества сбора, не как ошибку (сама логика это переживает).
        if (firstTotal == null) firstTotal = res.total; else if (res.total !== firstTotal) health.totalDrift++;
        total = res.total;
        // Пустая выдача: сервер сам сказал «ничего нет» и ничего не прислал —
        // спрашивать вторую страницу незачем. Для фоновых проверок это ×2
        // трафика на каждое «ничего не изменилось».
        if (!total && !res.offers.length) break;
        if (!res.offers.length) { if (++empty >= 2) break; page++; continue; }
        empty = 0;
        res.offers.forEach((o) => { const id = o.cianId || o.id; if (id != null) seg.add(id); });
        add(res.offers);
        onProgress(`Собрано ${byId.size}${grandTotal ? " из " + grandTotal : ""}…`, byId.size, grandTotal);
        // Сегмент полон, когда счёт сошёлся ТОЧНО. Расхождение в большую
        // сторону (заявлено 10, а на странице 28) означает, что total занижен:
        // раньше на этом пагинация обрывалась после первой страницы, и 28 лотов
        // из 100 уезжали в файл под видом 100% охвата. Верхнюю границу всё
        // равно держит правило ceil(total/pageSize)+2 ниже.
        if (seg.size === total) break;                                  // весь сегмент собран
        if (page >= Math.ceil(total / CONFIG.pageSize) + 2) break;
        // Паузы здесь больше нет: темп соблюдает apiFetch, и одинаково для
        // всех переходов, а не только для межстраничных.
        page++;
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
      } else if (knownTotal - (knownSeen || 0) > CONFIG.minShortfall && (hi0 - lo0) > CONFIG.minPriceSpan) {
        const mid = Math.floor((lo0 + hi0) / 2);                 // вызывающий уже прошёл [lo0,hi0]
        stack.push([lo0, mid]); stack.push([mid + 1, hi0]);
      }
      while (stack.length && requests < CONFIG.reqBudget) {
        const [a, b] = stack.pop();
        const { total, seen } = await paginateSegment(Object.assign({}, filters, { priceGte: a, priceLte: b }), label);
        if (total - seen > CONFIG.minShortfall && (b - a) > CONFIG.minPriceSpan) {
          const mid = Math.floor((a + b) / 2);
          stack.push([a, mid]); stack.push([mid + 1, b]);
        }
        if (grandTotal && byId.size >= grandTotal) break;
      }
    }

    // Исчерпание бюджета — это НЕ отказ: всё собранное к этому моменту остаётся
    // валидным. Раньше такой обрыв был неотличим от штатного завершения, и
    // пользователю показывали неполную выгрузку как полную.
    let totalsByRoom = null;
    try {
      // 1) прямой проход по запросу пользователя
      const first = await paginateSegment({}, "");
      grandTotal = first.total;

      // 2) недобор -> детерминированная декомпозиция
      if (grandTotal && byId.size < grandTotal && requests < CONFIG.reqBudget) {
        if (!base.room) {
          totalsByRoom = {};
          for (const room of ROOMS) {
            if (requests >= CONFIG.reqBudget || byId.size >= grandTotal) break;
            onProgress(`Комнаты ${room}… (${byId.size}/${grandTotal})`, byId.size, grandTotal);
            const pr = await paginateSegment({ room }, `room ${room}: `);
            totalsByRoom[room] = pr.total;
            if (pr.total > pr.seen) await priceSplit({ room }, `room ${room} ₽: `, pr.total, pr.seen);
          }
        } else {
          await priceSplit({}, "₽: ", first.total, first.seen);   // у пользователя уже фильтр по комнатам
        }
      }
    } catch (e) {
      // Бюджет и отмена — единственные исключения, которые мы гасим: собранное
      // к этому моменту валидно и терять его незачем. Остальные (403, капча,
      // отказ на первой же странице) обязаны дойти до пользователя.
      // WAF гасим тоже: терминальность означает «больше не ходить», а НЕ
      // «выбросить собранное». Сообщение сохраняем — его покажет панель, если
      // собрать не успели ничего.
      if (!e || !(e.budget || e.cancelled || e.waf)) { onWait = null; endRun(); throw e; }
      if (e.cancelled) { health.cancelled = true; console.warn("[cian-excel] сбор отменён — выгрузка неполная"); }
      else if (e.waf) { health.wafBlock = true; health.wafMessage = e.message; console.warn("[cian-excel] " + e.message); }
      else { health.budgetExhausted = true; console.warn(`[cian-excel] ${e.message} — выгрузка неполная`); }
    }
    onWait = null; endRun();
    health.requests = requests;
    health.elapsedMs = Date.now() - health.t0;
    // Недобор — главный признак качества выгрузки, а до сих пор его нигде не
    // считали: пользователю показывали охват, посчитанный от того же total,
    // который мог быть занижен.
    health.shortfall = grandTotal ? Math.max(0, grandTotal - byId.size) : 0;
    const log = tele || []; tele = null;
    console.log(`[cian-excel] ИТОГО ${byId.size}/${grandTotal} за ${health.http} обращений (${requests} страниц, неудачных попыток: ${health.retries}, дрейф total: ${health.totalDrift}${health.budgetExhausted ? ", БЮДЖЕТ ИСЧЕРПАН" : ""}${health.cancelled ? ", ОТМЕНЕНО" : ""}${health.wafBlock ? ", БЛОКИРОВКА" : ""})`);
    return { offers: [...byId.values()], totalsByRoom, totalInJk: grandTotal, health, log, agg: aggregate(health, log) };
  }

  // Агрегат прогона: ~300 символов, ровно то, что кладётся в хранилище.
  // Сырой журнал туда не попадает НИКОГДА — замер: 30 прогонов по 400 обращений
  // = 2.1 МБ UTF-16 при квоте 5 МиБ на весь ориджин www.cian.ru.
  function aggregate(h, log) {
    const durs = log.map((r) => r.dur).filter((d) => d != null).sort((a, b) => a - b);
    const gaps = log.map((r) => r.gap).slice(1);
    const at = (p) => (durs.length ? durs[Math.min(durs.length - 1, Math.floor(durs.length * p))] : null);
    const byStatus = {};
    log.forEach((r) => { byStatus[r.st] = (byStatus[r.st] || 0) + 1; });
    return {
      ts: Math.floor(h.t0 / 1000), http: h.http, pages: h.requests || 0, byStatus,
      p50: at(0.5), p95: at(0.95),
      minGap: gaps.length ? Math.min.apply(null, gaps) : null,
      zeroGaps: gaps.filter((g) => g <= 0).length,
      wallMs: h.elapsedMs, drift: h.totalDrift, shortfall: h.shortfall || 0,
      // raSeen отвечает на открытый вопрос: Retry-After не входит в CORS-safelist,
      // и ветка «уважаем просьбу сервера», возможно, мёртвая. Одно поле — один
      // боевой прогон — окончательный ответ.
      raSeen: log.filter((r) => r.raSeen).length,
      // Куда пацер пришёл к концу прогона: если он раз за разом упирается в
      // потолок, стартовое значение выбрано слишком дерзко.
      pacerFinal: pacer ? Math.round(pacer.gap) : null,
      budget: h.budgetExhausted ? 1 : 0, cancel: h.cancelled ? 1 : 0, waf: h.wafBlock ? 1 : 0,
    };
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
  // ===== Запись в localStorage: отказ обязан быть виден =====================
  // Квота Chrome — 5 MiB на ОРИГИН, и ориджин здесь www.cian.ru: квоту мы
  // делим с самим сайтом. Замер: 805 символов на квартиру, то есть потолок
  // ~3 250 квартир, а квартиры копятся по ВСЕМ выгруженным ЖК (fpOf включает
  // jkId) — три-пять крупных ЖК исчерпывают квоту за недели.
  //
  // Раньше отказ записи глотался (`catch (e) { /* ignore */ }`): при
  // QuotaExceededError история просто переставала обновляться — навсегда и без
  // единого признака. «Реальный срок экспозиции» тихо замерзал, «Изменения»
  // переставали видеть изменения, пользователь не узнавал ничего. Это был
  // единственный ДЕЙСТВУЮЩИЙ канал потери невосстановимых данных в проекте.
  let storageFault = null;   // последний отказ записи: {key, quota, message, at}
  // Имена и коды отличаются между браузерами, а сообщение — единственное, что
  // есть всегда. Проверяем всё сразу: ложное срабатывание безобидно (лишний
  // бэкап), пропуск — нет.
  const isQuotaError = (e) => !!e && (
    e.name === "QuotaExceededError" || e.name === "NS_ERROR_DOM_QUOTA_REACHED" ||
    e.code === 22 || e.code === 1014 || /quota|exceed/i.test(String((e && e.message) || "")));
  // Сериализация ВНУТРИ try: на большой истории упасть может и сам JSON.stringify.
  function storageWrite(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      if (storageFault && storageFault.key === key) storageFault = null;
      return true;
    } catch (e) {
      storageFault = { key, quota: isQuotaError(e), message: (e && e.message) || String(e), at: Date.now() };
      console.error(`[cian-excel] не удалось сохранить ${key}: ${storageFault.message}`);
      return false;
    }
  }

  // ===== Адаптер хранилища ==================================================
  // ЗАЧЕМ. localStorage упирается в 5 MiB на ориджин (делим с самим cian.ru) —
  // это ~4 400 квартир, и до потолка доводит ровно фича «сравнение нескольких
  // ЖК». У IndexedDB квота измеряется долями свободного диска, запись
  // асинхронна и не блокирует вкладку (замер синхронного цикла на 10 000
  // квартир: 187 мс, на 20 000: 428 мс).
  //
  // ПОЧЕМУ АДАПТЕР, А НЕ ПРЯМЫЕ ВЫЗОВЫ. Тесты подменяют адаптер, а не
  // IndexedDB: эмулятор IDB — это полторы сотни строк, которые сами себя
  // проверять не умеют. Тот же адаптер понадобится чекпоинту (шаг 3).
  //
  // ПОЧЕМУ ЗЕРКАЛО В ПАМЯТИ. Весь слой выше (enrichExposure, computeChanges,
  // экспорт бэкапа) синхронный и вызывается из buildWorkbook. Делать его
  // асинхронным — это переписывать книгу и её снимок, то есть класть настоящую
  // регрессию под шум диффа. Зеркало сохраняет синхронный контракт, а на диск
  // уходит запись целиком и асинхронно — один раз за прогон.
  const DB_NAME = "cianExcel", DB_VERSION = 1;
  const STORE_FLATS = "flats", STORE_SNAPS = "snapshots";

  // Настоящая реализация на IndexedDB. Возвращает null, если IDB недоступен
  // (инкогнито, запрет политикой) — тогда работаем на легаси-пути целиком.
  function openIdbStore() {
    return new Promise((resolve) => {
      let idb = null;
      try { idb = indexedDB; } catch (e) { idb = null; }
      if (!idb) return resolve(null);
      let req;
      try { req = idb.open(DB_NAME, DB_VERSION); } catch (e) { return resolve(null); }
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE_FLATS)) {
          // Индекс по lastSeen — чтобы чистка 400-дневных шла курсором по
          // индексу, а не перебором всех ключей в JS.
          db.createObjectStore(STORE_FLATS, { keyPath: "fp" }).createIndex("lastSeen", "lastSeen");
        }
        // Снимок хранится ПОСУБЪЕКТНО: сегодня computeChanges грузит снимки
        // ВСЕХ ЖК, чтобы сравнить один.
        if (!db.objectStoreNames.contains(STORE_SNAPS)) db.createObjectStore(STORE_SNAPS, { keyPath: "subj" });
      };
      req.onsuccess = () => resolve(wrapIdb(req.result));
      req.onerror = () => resolve(null);
      req.onblocked = () => resolve(null);
    });
  }

  // Тонкий интерфейс: getAll / putAll / count. Больше слою выше не нужно.
  function wrapIdb(db) {
    const tx = (name, mode, fn) => new Promise((resolve, reject) => {
      let t;
      try { t = db.transaction(name, mode); } catch (e) { return reject(e); }
      const st = t.objectStore(name);
      let out;
      try { out = fn(st); } catch (e) { return reject(e); }
      t.oncomplete = () => resolve(out && out.result !== undefined ? out.result : out);
      t.onerror = () => reject(t.error || new Error("ошибка транзакции " + name));
      t.onabort = () => reject(t.error || new Error("транзакция " + name + " прервана"));
    });
    return {
      kind: "idb",
      getAll: (name) => tx(name, "readonly", (st) => st.getAll()),
      count: (name) => tx(name, "readonly", (st) => st.count()),
      // Полная перезапись стора в ОДНОЙ транзакции: либо новое состояние целиком,
      // либо старое. Полумиграции и полусохранения — худшее, что может случиться
      // с единственными невосстановимыми данными проекта.
      putAll: (name, records) => tx(name, "readwrite", (st) => { st.clear(); records.forEach((r) => st.put(r)); }),
    };
  }

  // Режимы только целиком: "legacy" (localStorage) или "idb". Смешанных нет —
  // иначе при отказе на середине половина данных оказалась бы там, половина тут.
  let store = null, storeMode = "legacy", hydrated = null, memReady = false;
  let mem = { flats: {}, subjects: {} };

  const HKEY = "cianExcelHistory_v1";
  const SKEY = "cianExcelSnapshot_v1";
  const readLegacy = (key, field) => {
    try { const s = localStorage.getItem(key); const o = s ? JSON.parse(s) : null; return (o && o[field]) || {}; }
    catch (e) { return {}; }
  };

  // Легаси-путь синхронен, ждать там нечего: если зеркало ещё не поднимали,
  // поднимаем прямо сейчас. Без этого любой синхронный читатель до первого
  // storageReady() видел бы пустую историю — то есть «истории нет» вместо
  // «история ещё не загружена».
  function ensureMem() {
    if (memReady) return;
    memReady = true;
    mem = { flats: readLegacy(HKEY, "flats"), subjects: readLegacy(SKEY, "subjects") };
  }

  // Однократная загрузка в зеркало. Всё остальное читает уже память.
  function storageReady(makeStore) {
    if (hydrated) return hydrated;
    hydrated = (async () => {
      const meta = loadMeta();
      if (meta.storageMode === "idb") {
        try { store = await (makeStore || openIdbStore)(); } catch (e) { store = null; }
        if (store) {
          storeMode = "idb";
          const flats = {}, subjects = {};
          (await store.getAll(STORE_FLATS)).forEach((r) => { const { fp, ...rest } = r; flats[fp] = rest; });
          (await store.getAll(STORE_SNAPS)).forEach((r) => { const { subj, ...rest } = r; subjects[subj] = rest; });
          mem = { flats, subjects }; memReady = true;
          return storeMode;
        }
        // IDB объявлен рабочим, но не открылся: НЕ смешиваем режимы и не
        // молчим — легаси-ключи целы, читаем их и говорим об этом.
        storageFault = { key: DB_NAME, quota: false, message: "IndexedDB недоступен, работаем на старом хранилище", at: Date.now() };
      }
      storeMode = "legacy";
      memReady = false; ensureMem();
      return storeMode;
    })();
    return hydrated;
  }
  // Для тестов и для повторной миграции: следующий storageReady() перечитает всё.
  const storageResetForTests = () => { hydrated = null; store = null; storeMode = "legacy"; memReady = false; mem = { flats: {}, subjects: {} }; };

  // Запись: зеркало обновляется синхронно, диск — как получится.
  function persist(kind) {
    if (storeMode !== "idb" || !store) {
      return storageWrite(kind === "flats" ? HKEY : SKEY,
        kind === "flats" ? { flats: mem.flats } : { subjects: mem.subjects });
    }
    const recs = kind === "flats"
      ? Object.keys(mem.flats).map((fp) => Object.assign({ fp }, mem.flats[fp]))
      : Object.keys(mem.subjects).map((subj) => Object.assign({ subj }, mem.subjects[subj]));
    store.putAll(kind === "flats" ? STORE_FLATS : STORE_SNAPS, recs).then(
      () => { if (storageFault && storageFault.key === kind) storageFault = null; },
      (e) => {
        storageFault = { key: kind, quota: isQuotaError(e), message: (e && e.message) || String(e), at: Date.now() };
        console.error("[cian-excel] не удалось сохранить в IndexedDB: " + storageFault.message);
      });
    return true;
  }

  function loadHistory() { ensureMem(); return { flats: mem.flats }; }
  function saveHistory(h) {
    ensureMem();
    const cut = Math.floor(Date.now() / 1000) - 400 * 86400;  // чистим квартиры, не виденные >400 дней
    for (const k in h.flats) if ((h.flats[k].lastSeen || 0) < cut) delete h.flats[k];
    mem.flats = h.flats;
    return persist("flats");
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
  function loadSnapshots() { ensureMem(); return { subjects: mem.subjects }; }
  function saveSnapshots(d) {
    ensureMem();
    const cut = Math.floor(Date.now() / 1000) - 400 * 86400;
    for (const k in d.subjects) if ((d.subjects[k].ts || 0) < cut) delete d.subjects[k];
    mem.subjects = d.subjects;
    return persist("snapshots");
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

  // ----- Автобэкап: маркеры и правило «пора» --------------------------------
  // Маркер живёт в ОТДЕЛЬНОМ крошечном ключе, а не внутри истории. Когда квота
  // исчерпана, запись общего объёма не проходит — а маркер обязан пройти,
  // иначе автобэкап теряет память ровно там, где он нужнее всего.
  const MKEY = "cianExcelMeta_v1";
  function loadMeta() {
    try { const s = localStorage.getItem(MKEY); const m = s ? JSON.parse(s) : null; return m && typeof m === "object" ? m : {}; }
    catch (e) { return {}; }
  }
  function saveMeta(patch) { const m = Object.assign(loadMeta(), patch); storageWrite(MKEY, m); return m; }

  // ----- Кольцевой буфер агрегатов прогонов ---------------------------------
  // В хранилище едет ТОЛЬКО агрегат (~300 символов). Сырой журнал не попадает
  // сюда никогда: 30 прогонов по 400 обращений = 2.1 МБ UTF-16 при квоте 5 МиБ
  // на весь ориджин, который мы делим с самим www.cian.ru. Это инвариант,
  // проверяемый тестом, а не устная договорённость.
  const RKEY = "cianExcelRuns_v1";
  const RUNS_KEEP = 200;                         // ≈ 60 000 символов
  function loadRuns() {
    try { const s = localStorage.getItem(RKEY); const a = s ? JSON.parse(s) : null; return Array.isArray(a) ? a : []; }
    catch (e) { return []; }
  }
  function rememberRun(agg) {
    if (!agg) return false;
    const a = loadRuns();
    a.push(agg);
    return storageWrite(RKEY, a.length > RUNS_KEEP ? a.slice(-RUNS_KEEP) : a);
  }

  // ----- Миграция localStorage -> IndexedDB ---------------------------------
  // Порядок такой, что КАЖДЫЙ шаг обратим, а источник цел до самого конца.
  // Трогаем единственные невосстановимые данные проекта — месяцы истории цен и
  // реальных сроков экспозиции, — поэтому гарантий три и нужны все сразу:
  //   1) файл-бэкап сделан ДО первой записи и блокирует миграцию, если не удался;
  //   2) ключи localStorage не изменяются до подтверждённой верификации
  //      (и не переименовываются потом: переименование — это запись тех же
  //      данных под новым именем, то есть ВРЕМЕННОЕ УДВОЕНИЕ расхода квоты
  //      ровно тогда, когда квота на исходе);
  //   3) смешанных режимов нет — либо целиком legacy, либо целиком idb.
  // Двойная запись (dual-write) не делается по той же причине, что и
  // переименование: она удваивает давление на квоту, от которой мы уходим.
  const MIGRATION_RETRY_MS = 24 * 3600 * 1000;
  const VERIFY_SAMPLE = 25;                      // сколько записей сверяем глубоко

  function migrationDue(meta, nowMs) {
    if (meta.storageMode === "idb") return false;
    if (meta.migrationFailedAt && nowMs - meta.migrationFailedAt < MIGRATION_RETRY_MS) return false;
    return true;
  }

  // backupFn обязана вернуть true, иначе миграция не начинается вовсе.
  async function migrateToIdb({ backupFn, makeStore, nowMs, onProgress } = {}) {
    const say = onProgress || (() => {});
    const fail = (reason) => {
      saveMeta({ migrationFailedAt: nowMs || Date.now(), migrationFailReason: reason });
      console.warn("[cian-excel] миграция не удалась: " + reason);
      return { ok: false, reason };
    };
    // 1. Бэкап файлом — блокирующе. Не удался — не мигрируем.
    try { if (!(await backupFn())) return fail("не удалось сохранить бэкап перед миграцией"); }
    catch (e) { return fail("бэкап перед миграцией упал: " + ((e && e.message) || e)); }

    // 2. Читаем источник. Он останется нетронутым до конца и после конца.
    const srcFlats = readLegacy(HKEY, "flats"), srcSubjects = readLegacy(SKEY, "subjects");
    const nFlats = Object.keys(srcFlats).length, nSubj = Object.keys(srcSubjects).length;

    let db = null;
    try { db = await (makeStore || openIdbStore)(); } catch (e) { db = null; }
    if (!db) return fail("IndexedDB недоступен");

    // 3. Пишем. Одна транзакция на стор: 20 000 записей одной транзакцией на всё
    // рискуют упасть целиком, а на стор — приемлемо и атомарно.
    try {
      say(`Перенос истории: ${nFlats} квартир…`);
      await db.putAll(STORE_FLATS, Object.keys(srcFlats).map((fp) => Object.assign({ fp }, srcFlats[fp])));
      say(`Перенос снимков: ${nSubj}…`);
      await db.putAll(STORE_SNAPS, Object.keys(srcSubjects).map((subj) => Object.assign({ subj }, srcSubjects[subj])));
    } catch (e) { return fail("запись в IndexedDB: " + ((e && e.message) || e)); }

    // 4. Верификация ДО объявления успеха: количества сходятся и выборка
    // случайных записей совпадает глубоко. Без неё «частично перенесли» было бы
    // неотличимо от «перенесли».
    try {
      say("Проверка переноса…");
      const gotFlats = await db.getAll(STORE_FLATS), gotSubj = await db.getAll(STORE_SNAPS);
      if (gotFlats.length !== nFlats) return fail(`перенесено ${gotFlats.length} квартир из ${nFlats}`);
      if (gotSubj.length !== nSubj) return fail(`перенесено ${gotSubj.length} снимков из ${nSubj}`);
      const keys = Object.keys(srcFlats);
      const step = Math.max(1, Math.floor(keys.length / VERIFY_SAMPLE));
      const byFp = new Map(gotFlats.map((r) => [r.fp, r]));
      for (let i = 0; i < keys.length; i += step) {
        const fp = keys[i], got = byFp.get(fp);
        if (!got) return fail(`после переноса не нашлась запись ${fp}`);
        const { fp: _drop, ...rest } = got;
        if (JSON.stringify(rest) !== JSON.stringify(srcFlats[fp])) return fail(`запись ${fp} перенеслась искажённой`);
      }
    } catch (e) { return fail("проверка переноса: " + ((e && e.message) || e)); }

    // 5. Только теперь — переключение режима. Легаси-ключи не трогаем.
    saveMeta({ storageMode: "idb", migratedAt: nowMs || Date.now(), migratedFlats: nFlats, migrationFailedAt: 0, migrationFailReason: "" });
    storageResetForTests();
    await storageReady(makeStore || openIdbStore);
    console.log(`[cian-excel] хранилище переведено на IndexedDB: ${nFlats} квартир, ${nSubj} снимков`);
    return { ok: true, flats: nFlats, subjects: nSubj };
  }

  // ----- Выводы по журналу ---------------------------------------------------
  // Журнал заводился ради шести конкретных вопросов, и до сих пор ответы на них
  // приходилось вычитывать из таблицы глазами. Здесь они считаются сами — и по
  // одному прогону, и НАКОПИТЕЛЬНО по кольцевому буферу: четыре вопроса из шести
  // бинарные, им хватает одного прогона, но двум нужна статистика.
  //
  // Каждый вывод обязан уметь сказать «нет данных». Отсутствие отказов — это не
  // «Retry-After не читается», а «проверить было не на чем», и путать эти два
  // ответа хуже, чем не отвечать вовсе.
  const CIAN_CHECK_MS = 5000;      // «проверка браузера» по документации Циан — от 5 с

  // Складывает агрегаты прогонов в одну картину.
  function rollupRuns(runs) {
    const byStatus = {};
    let http = 0, pages = 0, raSeen = 0, zeroGaps = 0, waf = 0, budget = 0, cancel = 0, shortfall = 0;
    const p50s = [], p95s = [], pacers = [];
    (runs || []).forEach((r) => {
      http += r.http || 0; pages += r.pages || 0; raSeen += r.raSeen || 0; zeroGaps += r.zeroGaps || 0;
      waf += r.waf || 0; budget += r.budget || 0; cancel += r.cancel || 0; shortfall += r.shortfall || 0;
      Object.keys(r.byStatus || {}).forEach((k) => { byStatus[k] = (byStatus[k] || 0) + r.byStatus[k]; });
      if (r.p50 != null) p50s.push(r.p50);
      if (r.p95 != null) p95s.push(r.p95);
      if (r.pacerFinal != null) pacers.push(r.pacerFinal);
    });
    const med = (a) => (a.length ? a.slice().sort((x, y) => x - y)[Math.floor(a.length / 2)] : null);
    return { runs: (runs || []).length, http, pages, byStatus, raSeen, zeroGaps, waf, budget, cancel, shortfall,
             p50: med(p50s), p95: p95s.length ? Math.max.apply(null, p95s) : null, pacerFinal: med(pacers) };
  }

  // Сколько отказов было В ПРИНЦИПЕ — знаменатель для вопроса про Retry-After.
  const failCount = (byStatus) => Object.keys(byStatus || {})
    .filter((k) => k !== "200").reduce((n, k) => n + byStatus[k], 0);

  // Возвращает список {q, a, note} — вопрос, ответ, пояснение.
  function journalVerdicts(roll, cfg) {
    const s = roll.byStatus || {}, fails = failCount(s), out = [];

    // 1. Retry-After не входит в CORS-safelist, и вся ветка «уважаем просьбу
    // сервера» может оказаться мёртвой. Одно поле — окончательный ответ.
    out.push(fails === 0
      ? { q: "Читается ли заголовок Retry-After", a: "нет данных", note: "отказов не было — проверять было не на чем" }
      : roll.raSeen > 0
        ? { q: "Читается ли заголовок Retry-After", a: "да", note: `прочитан ${roll.raSeen} раз из ${fails} отказов — Циан действительно говорит, сколько ждать` }
        : { q: "Читается ли заголовок Retry-After", a: "НЕТ", note: `за ${fails} ${plural(fails, "отказ", "отказа", "отказов")} заголовок не прочитан ни разу. Он не входит в CORS-safelist: скорее всего браузер его прячет, и пауза всегда считается локально` });

    // 2. Если 429 на этом эндпоинте не бывает, пацер, построенный на нём как на
    // главном тормозе, не сработает ни разу — и тормозить надо по латентности.
    const n429 = s["429"] || 0;
    out.push(n429 > 0
      ? { q: "Бывает ли на этом API код 429", a: `да, ${n429}`, note: "троттлинг приходит явным кодом — пацеру есть на что реагировать" }
      : { q: "Бывает ли на этом API код 429", a: roll.http ? "не встречался" : "нет данных",
          note: roll.http ? `${roll.http} ${plural(roll.http, "обращение", "обращения", "обращений")} без единого 429. Если так и останется — замедляться придётся по росту задержки, а не по коду` : "обращений ещё не было" });

    // 3. Базовая линия задержки. Без неё «ответы стали медленнее» не с чем сравнить.
    out.push(roll.p50 == null
      ? { q: "Типичная задержка ответа", a: "нет данных", note: "" }
      : { q: "Типичная задержка ответа", a: `${roll.p50} мс (медиана), ${roll.p95} мс (худшие 5%)`,
          note: roll.p95 >= CIAN_CHECK_MS
            ? `Худшие ответы дольше ${CIAN_CHECK_MS / 1000} с — по документации Циан столько занимает «проверка браузера». Стоит присмотреться`
            : "это базовая линия: заметный рост в следующих выгрузках — ранний признак, что Циан начал присматриваться" });

    // 4. Восстановимый уровень антибота на XHR-пути. Один живой образец — самое
    // ценное наблюдение из возможных: без него детектор строить не на чем.
    const nHtml = s.HTML || 0, n403 = s["403"] || 0;
    out.push(nHtml || n403
      ? { q: "Встречалась ли проверка браузера или блокировка", a: `да: проверок ${nHtml}, блокировок ${n403}`,
          note: "строки с кодом HTML/403 в таблице ниже — образец того, как это выглядит на XHR-пути" }
      : { q: "Встречалась ли проверка браузера или блокировка", a: "нет", note: "сессия ни разу не показалась Циан подозрительной" });

    // 5. Держится ли темп по факту. Фоновая вкладка троттлит таймеры, и на
    // стенде это не воспроизводится в принципе.
    out.push({ q: "Соблюдался ли темп", a: roll.zeroGaps ? `НЕТ: ${roll.zeroGaps} обращений подряд без паузы` : "да",
      note: roll.zeroGaps ? "пауза перед обращением где-то не сработала — это дефект, а не настройка" : "ни одного обращения без выдержанного зазора" });

    // 6. Куда пришёл пацер. Постоянный упор в потолок = старт выбран слишком дерзко.
    const ceil = cfg && cfg.pacer ? cfg.pacer.ceil : null;
    const start = cfg && cfg.pacer ? cfg.pacer.start : null;
    out.push(roll.pacerFinal == null
      ? { q: "Где закончил адаптивный темп", a: "нет данных", note: "" }
      : { q: "Где закончил адаптивный темп", a: `${roll.pacerFinal} мс (старт ${start})`,
          note: ceil && roll.pacerFinal >= ceil * 0.9
            ? "упирается в потолок — стартовый темп выбран слишком дерзко, его стоит увеличить"
            : roll.pacerFinal <= start ? "темп не пришлось замедлять — запас есть" : "темп подстроился вверх, но до потолка не дошёл" });
    return out;
  }

  const BACKUP_MIN_FLATS = 100;                  // ниже этого терять ещё нечего
  const BACKUP_MAX_AGE_MS = 7 * 86400 * 1000;
  const BACKUP_GROWTH = 1.2;                     // история выросла на 20%
  // Чистое решение, отделённое от скачивания: скачивание проверить трудно,
  // а правило — легко, и ошибка будет именно в правиле.
  function backupDue(meta, flats, nowMs, fault) {
    // Заполненная квота бьёт любой порог: терять уже начали.
    if (fault && fault.quota) return { due: true, why: "хранилище браузера заполнено" };
    if (!(flats >= BACKUP_MIN_FLATS)) return { due: false, why: "" };
    const last = meta && meta.lastBackupAt;
    if (!last) return { due: true, why: "бэкапа ещё не было" };
    const days = Math.floor((nowMs - last) / 86400000);
    if (nowMs - last > BACKUP_MAX_AGE_MS) return { due: true, why: `с прошлого бэкапа ${days} дн.` };
    const was = (meta && meta.lastBackupFlats) || 0;
    if (flats > was * BACKUP_GROWTH) return { due: true, why: `история выросла с ${was} до ${flats}` };
    return { due: false, why: "" };
  }
  // Русское склонение по числу: «1 квартира / 2 квартиры / 5 квартир».
  // Отдельная функция, потому что 11-14 идут по форме «много» вопреки
  // последней цифре, и написанное «на глаз» ошибается именно там.
  const plural = (n, one, few, many) => {
    const a = Math.abs(n) % 100, b = a % 10;
    return a > 10 && a < 20 ? many : b === 1 ? one : b >= 2 && b <= 4 ? few : many;
  };
  // Сколько накоплено и когда бэкапили — то, чего в панели не было вовсе.
  function storageInfo(nowMs) {
    const flats = Object.keys(loadHistory().flats || {}).length;
    const subjects = Object.keys(loadSnapshots().subjects || {}).length;
    const meta = loadMeta();
    const ageDays = meta.lastBackupAt ? Math.floor((nowMs - meta.lastBackupAt) / 86400000) : null;
    return { flats, subjects, ageDays, mode: storeMode, fault: storageFault };
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
    const okH = saveHistory(mergedHist), okS = saveSnapshots(mergedSnap);
    // Считать импорт успешным по объединённому объекту в памяти нельзя: при
    // заполненной квоте он бы отрапортовал «импортировано N», не записав ничего.
    if (!okH || !okS) {
      throw new Error("не удалось сохранить импортированные данные" +
        (storageFault && storageFault.quota ? ": хранилище браузера заполнено" : ""));
    }
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
      // headerRow — где на листе лежит строка заголовков. По умолчанию 4-я, как
      // на всех листах с лотами; журналу нужна другая — над таблицей у него
      // блок выводов, и таблица, объявленная не с той строки, ломает автофильтр.
      const hr = opts.headerRow || 4;
      sh.table = { ref: rangeA1(1, hr, cols.length, hr + opts.autoFilterRows) };
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
      // Обращения и страницы — разные числа: одна страница стоит до maxRetries
      // обращений, и Циан видит именно первое. Бюджет считается по нему.
      R.push(row([{ v: `Обращений к Циан: ${health.http || health.requests} (страниц: ${health.requests}) · неудачных попыток: ${health.retries} · дрейф total между страницами: ${health.totalDrift}` +
        (health.elapsedMs ? ` · заняло ${Math.max(1, Math.round(health.elapsedMs / 60000))} мин` : ""), s: warn ? "warn" : "sub" }]));
      // retryStatuses собирался в двух местах и не читался НИГДЕ, а это
      // единственное, что отличает «Циан троттлит» от «упал бэкенд» от «плохой
      // wi-fi»: 429 — троттлинг, 5xx — бэкенд, сеть — канал до Циан.
      const st = statusBreakdown(health.retryStatuses);
      if (st) R.push(row([{ v: "Из них отказов: " + st, s: warn ? "warn" : "sub" }]));
      healthReasons(health).forEach((r) => R.push(row([{ v: "· " + r.charAt(0).toUpperCase() + r.slice(1), s: "warn" }])));
      if (warn) R.push(row([{ v: "Выгрузка может быть неполной — проверьте охват выше и по возможности выгрузите повторно.", s: "sub" }]));
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

  // Журнал обращений к Циан — одна строка на КАЖДУЮ попытку, а не на страницу.
  // Живёт только здесь: в localStorage его класть нельзя (замер — 30 прогонов
  // = 2.1 МБ UTF-16 при квоте 5 МиБ, делимой с самим www.cian.ru), а файл и так
  // скачивается. Это делает разбираемой жалобу «выгрузилось не всё» по одному
  // присланному файлу и даёт корпус для настройки темпа.
  function logSheet(log, agg) {
    if (!log || !log.length) return null;
    const HDR = ["#", "t, с", "Длит., мс", "Код", "Тип", "Размер, Б", "Попытка", "Зазор, мс", "Сегмент", "Стр.", "Лотов", "total", "Retry-After"];
    const W = [46, 62, 78, 56, 52, 78, 66, 74, 130, 46, 58, 62, 88];
    const R = [
      row([{ v: "Журнал обращений к api.cian.ru", s: "title", merge: HDR.length - 1 }]),
      row([{ v: `Обращений ${agg.http} на ${agg.pages} логических страниц · медиана ответа ${agg.p50 ?? "—"} мс, 95-й процентиль ${agg.p95 ?? "—"} мс · ` +
        `минимальный зазор ${agg.minGap ?? "—"} мс, нулевых зазоров ${agg.zeroGaps} · Retry-After прочитан ${agg.raSeen} раз`, s: "sub", merge: HDR.length - 1 }]),
    ];
    // Выводы — ВЫШЕ таблицы: смысл журнала в них, а не в трёх сотнях строк.
    // Считаются накопительно по кольцевому буферу прогонов: четырём вопросам из
    // шести хватает одного прогона, двум нужна статистика.
    const runs = loadRuns();
    const roll = rollupRuns(runs.length ? runs : [agg]);
    R.push(row([{}]), row([{ v: roll.runs > 1
      ? `ЧТО ИЗ ЭТОГО СЛЕДУЕТ — по ${roll.runs} последним ${plural(roll.runs, "прогону", "прогонам", "прогонам")} (${roll.http} ${plural(roll.http, "обращение", "обращения", "обращений")})`
      : "ЧТО ИЗ ЭТОГО СЛЕДУЕТ — по этому прогону", s: "bold", merge: HDR.length - 1 }]));
    // Колонки разнесены с ЗАПАСОМ и без пересечений: перекрывающиеся merge —
    // это не «некрасиво», а повреждённая книга, которую Excel чинит молча.
    journalVerdicts(roll, CONFIG).forEach((v) => {
      R.push(row([
        { v: v.q + ":", s: "sub", merge: 1 }, null,
        { v: v.a, s: /^НЕТ/.test(v.a) ? "warn" : "bold", merge: 1 }, null,
        { v: v.note, s: "sub", merge: HDR.length - 5 },
      ]));
    });
    R.push(row([{}]),
      row([{ v: "Ниже — по строке на КАЖДОЕ обращение (не на страницу: одна страница стоит до четырёх попыток).", s: "sub", merge: HDR.length - 1 }]),
      row([{}]),
      row(HDR.map((h) => ({ v: h, s: "hdr" }))));
    const headerRow = R.length;   // строка заголовков таблицы, 1-based
    log.forEach((r, i) => {
      // Рост Длит. — самый ранний признак «проверки браузера» (по документации
      // Циан это 5 с…3 мин), поэтому колонка стоит третьей, а не в конце.
      const bad = r.st !== 200;
      R.push(row([
        { v: i + 1, t: "Number" },
        { v: Math.round(r.t / 100) / 10, t: "Number" },
        { v: r.dur, t: r.dur != null ? "Number" : "String", s: bad ? "warn" : null },
        { v: r.st, t: typeof r.st === "number" ? "Number" : "String", s: bad ? "warn" : null },
        { v: r.ct || "—" },
        { v: r.len, t: r.len != null ? "Number" : "String" },
        { v: r.att, t: "Number" },
        { v: Math.round(r.gap), t: "Number" },
        { v: r.seg || "—" },
        { v: r.page, t: r.page != null ? "Number" : "String" },
        { v: r.n, t: r.n != null ? "Number" : "String" },
        { v: r.tot, t: r.tot != null ? "Number" : "String" },
        { v: r.raSeen ? "да" : "" },
      ]));
    });
    return worksheet("Журнал_сбора", W, R, { freezeRows: headerRow, freezeCols: 1, autoFilterRows: log.length, headerRow });
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

  function buildWorkbook(subj, rows, totalsByRoom, totalInJk, health, log, agg) {
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
    // Журнал — последним листом: он служебный и не должен раздвигать привычный
    // порядок вкладок.
    const logXml = logSheet(log, agg); if (logXml) sheets.push(logXml);
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
      st.healthWhy = healthReasons(health);
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
  .prog .cancel{margin-top:9px;border:1px solid var(--border);background:transparent;color:var(--text-2);
    font-size:11.5px;font-weight:600;padding:6px 11px;border-radius:9px;cursor:pointer}
  .prog .cancel:hover{color:var(--text-1)}
  .prog .cancel:disabled{opacity:.5;cursor:default}
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
  .bk.info{background:var(--stat-bg);color:var(--text-2)}
  .bk .lnk{margin-left:6px}
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
              '<div class="track"><div class="bar indef" id="bar"></div></div>' +
              '<button class="cancel" id="cancel" title="Остановить сбор и сохранить то, что уже собрано">✕ Отмена</button></div>' +
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
            '<div class="bk" id="bk-info"></div>' +
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
      go: $("#go"), prog: $("#prog"), pt: $("#pt"), pp: $("#pp"), bar: $("#bar"), cancel: $("#cancel"),
      res: $("#res"), count: $("#s-count"), cov: $("#s-cov"), ppm: $("#s-ppm"),
      meta: $("#s-meta"), health: $("#s-health"), healthtext: $("#s-healthtext"), cats: $("#s-cats"), fact: $("#s-fact"), facttext: $("#s-facttext"),
      file: $("#s-file"), fname: $("#s-fname"), foot: $("#foot"),
      err: $("#err"), errText: $("#err-text"), errRetry: $("#err-retry"),
      bkExport: $("#bk-export"), bkImport: $("#bk-import"), bkFile: $("#bk-file"),
      bkStatus: $("#bk-status"), bkInfo: $("#bk-info"),
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
    ui.el.cancel.addEventListener("click", () => {
      if (!cancelRun()) return;
      ui.el.cancel.disabled = true;
      ui.el.pt.textContent = "Останавливаюсь — сохраню то, что уже собрано…";
    });
    ui.el.bkExport.addEventListener("click", () => {
      try { showBkStatus(`Бэкап сохранён: ${downloadBackup()} записей в истории.`, true); refreshBkInfo(true); }
      catch (e) { showBkStatus("Не удалось создать бэкап: " + e.message, false); }
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
    refreshBkInfo();
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
      const why = (stats.healthWhy && stats.healthWhy.length) ? stats.healthWhy.join("; ") : "Циан отвечал нестабильно";
      ui.el.healthtext.textContent = why.charAt(0).toUpperCase() + why.slice(1) + " — сверьте охват, при сомнении выгрузите ещё раз.";
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

  // Один путь скачивания для кнопки и для автобэкапа: маркер обязан ставиться
  // в обоих случаях, иначе ручной бэкап не отодвигал бы автоматический.
  function downloadBackup() {
    const data = exportBackupData(), n = Object.keys(data.history.flats || {}).length;
    download(new Blob([JSON.stringify(data)], { type: "application/json;charset=utf-8" }),
      `cian-excel-backup_${new Date().toISOString().slice(0, 10)}.json`);
    saveMeta({ lastBackupAt: Date.now(), lastBackupFlats: n });
    return n;
  }

  // Перевод хранилища на IndexedDB. Делается ПОСЛЕ успешной выгрузки и только
  // при непустой истории: пустую переносить незачем, а внезапный второй файл
  // (обязательный бэкап) на первом же запуске выглядел бы как сбой.
  async function maybeMigrate() {
    try {
      const meta = loadMeta();
      if (!migrationDue(meta, Date.now())) return;
      if (Object.keys(loadHistory().flats || {}).length < BACKUP_MIN_FLATS) return;
      const res = await migrateToIdb({
        backupFn: () => { downloadBackup(); return true; },
        onProgress: (t) => showProgress(t, null),
      });
      if (res.ok) {
        showBkStatus(`Хранилище переведено на IndexedDB: ${res.flats} квартир, ${res.subjects} снимков. ` +
          "Старая копия в памяти браузера не тронута — она удалится сама позже.", true);
      } else {
        showBkStatus("Не удалось перевести хранилище на IndexedDB: " + res.reason +
          ". Работаем по-старому, данные целы, бэкап сохранён.", false);
      }
      refreshBkInfo(true);
    } catch (e) { console.warn("[cian-excel] миграция:", e); }
  }

  // Вызывается ПОСЛЕ скачивания книги: история к этому моменту уже обновлена,
  // и бэкап уезжает свежим. Второй файл подряд — цена невысокая, зато накопленные
  // за месяцы данные перестают зависеть от целости одного localStorage.
  function autoBackup() {
    try {
      const flats = Object.keys(loadHistory().flats || {}).length;
      const d = backupDue(loadMeta(), flats, Date.now(), storageFault);
      if (!d.due) return;
      const n = downloadBackup();
      showBkStatus(`Автобэкап истории сохранён (${d.why}): ${n} записей. ` +
        "Файл нужен только при переустановке расширения или переезде на другой компьютер.", true);
      refreshBkInfo(true);   // строка «бэкап: сегодня» должна обновиться сразу
    } catch (e) { console.warn("[cian-excel] автобэкап:", e); }
  }

  // Строка «сколько накоплено и когда бэкапили» + КРАСНАЯ ветка на отказ
  // записи. До сих пор в панели не было ни того, ни другого.
  //
  // Счёт КЭШИРУЕТСЯ на минуту: refreshHeader() зовётся по таймеру, а storageInfo
  // разбирает всю историю целиком — замер даёт 187 мс на 10 000 квартирах, и
  // раз в пару секунд это заметно подтормаживало бы саму страницу Циан. Отказ
  // записи, наоборот, читается всегда живым: он обязан появиться сразу.
  let bkInfoAt = 0, bkInfoVal = null, bkQuotaStr = "";
  function refreshBkInfo(force) {
    if (!ui.mounted || !ui.el.bkInfo) return;
    const el = ui.el.bkInfo, now = Date.now();
    if (storageFault) {
      el.className = "bk bad";
      el.textContent = storageFault.quota
        ? "Хранилище браузера заполнено — история и снимки больше НЕ обновляются. " +
          "Нажмите «📦 Бэкап истории», сохраните файл: он восстановится через «📥 Восстановить»."
        : "Не удалось сохранить историю: " + storageFault.message + ". Сделайте бэкап.";
      el.style.display = "block";
      return;
    }
    if (force || !bkInfoVal || now - bkInfoAt > 60000) {
      try { bkInfoVal = storageInfo(now); bkInfoAt = now; }
      catch (e) { el.style.display = "none"; return; }
      askQuota();
    }
    const info = bkInfoVal;
    if (!info.flats) { el.style.display = "none"; return; }
    el.className = "bk info";
    el.textContent = `В истории ${info.flats} ${plural(info.flats, "квартира", "квартиры", "квартир")}` +
      (info.mode === "idb" ? " (IndexedDB)" : "") +
      (info.subjects ? ` · снимков ${info.subjects}` : "") +
      " · бэкап: " + (info.ageDays == null ? "не делался"
        : info.ageDays === 0 ? "сегодня"
        : `${info.ageDays} ${plural(info.ageDays, "день", "дня", "дней")} назад`) +
      bkQuotaStr;
    el.style.display = "block";
  }
  // Свободное место — асинхронно и без гарантий: метода может не быть вовсе, а
  // его отсутствие не должно ломать уже показанную строку. Ответ кладём в
  // переменную, а не дописываем в DOM: иначе гонка с очередной перерисовкой.
  function askQuota() {
    try {
      if (!navigator.storage || !navigator.storage.estimate) return;
      navigator.storage.estimate().then((q) => {
        if (!q || !q.quota) return;
        bkQuotaStr = ` · занято ${Math.round((q.usage || 0) / 1048576 * 10) / 10} из ${Math.round(q.quota / 1048576)} МБ`;
      }).catch(() => {});
    } catch (e) { /* ignore */ }
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
    // Зеркало хранилища должно быть готово ДО enrichExposure/computeChanges:
    // весь слой выше синхронный и не умеет ждать.
    await storageReady();
    ui.el.cancel.disabled = false; ui.el.cancel.textContent = "✕ Отмена";
    showProgress("Подключаюсь…", null);
    try {
      const { offers, totalsByRoom, totalInJk, health: collectHealth, log, agg } =
        await collectAll(base, (text, got, total) => showProgress(text, total ? got / total : null));
      // Агрегат кладём СРАЗУ: он нужен и для прогонов, которые кончились ничем.
      rememberRun(agg);
      // Сверка с числом на странице — после сбора, чтобы не делать лишний запрос.
      if (pageCnt != null && totalInJk > pageCnt * 2 + 25) {
        const ok = confirm("На странице показано ~" + pageCnt + " объявлений, а запрос вернул " + totalInJk +
          ".\nВозможно, открыта не та вкладка результатов (фильтры не совпали).\n\nВсё равно сохранить " + offers.length + " лотов?");
        if (!ok) { ui._busy = false; ui.el.go.disabled = false; ui.el.go.textContent = "📊 Выгрузить в Excel"; ui.el.prog.style.display = "none"; return; }
      }
      // Отмена на первых же секундах — не ошибка и не повод советовать капчу.
      if (!offers.length && collectHealth && collectHealth.cancelled) {
        ui.el.prog.style.display = "none";
        ui.el.go.textContent = "📊 Выгрузить в Excel";
        showError("Сбор отменён — собрать не успели ничего, файл не создан. Нажмите «Повторить», когда будете готовы.");
        return;
      }
      // Блокировка/капча: сообщение уже сформулировано в слое сбора и говорит,
      // что именно делать. Общий совет «войдите и пройдите капчу» тут вреден —
      // при WAF-блоке проходить нечего.
      if (!offers.length && collectHealth && collectHealth.wafBlock) {
        ui.el.prog.style.display = "none";
        ui.el.go.textContent = "📊 Выгрузить в Excel";
        showError(collectHealth.wafMessage);
        return;
      }
      if (!offers.length) { throw new Error("не собрано ни одного лота (войдите в аккаунт и пройдите капчу)"); }
      const rows = offers.map(normalize).sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
      const expInfo = enrichExposure(rows, subj.id);   // реальный срок экспозиции (учёт сбросов)
      showProgress("Готовлю Excel…", 1);
      const filename = `cian_${subj.slug}_${new Date().toISOString().slice(0, 10)}_${rows.length}лотов${isHealthWarn(collectHealth) ? "_проверить" : ""}.xlsx`;
      // buildWorkbook отдаёт дерево, buildXlsxBlob упаковывает его в zip
      // (сжатие потоковое, поэтому await)
      download(await buildXlsxBlob(buildWorkbook(subj, rows, totalsByRoom, totalInJk, collectHealth, log, agg)), filename);
      showResults(computeStats(rows, totalInJk, expInfo, collectHealth), filename);
      ui.el.go.textContent = "📊 Выгрузить снова";
      autoBackup();            // после книги: история уже обновлена этим прогоном
      await maybeMigrate();    // и только теперь — перевод хранилища, если пора
      refreshBkInfo(true);     // счёт квартир только что изменился — кэш сбрасываем
    } catch (e) {
      console.error(e);
      ui.el.go.textContent = "📊 Выгрузить в Excel";
      showError("Ошибка: " + e.message + ". Обновите страницу, дождитесь загрузки списка объявлений и нажмите «Повторить».");
    } finally {
      // Токен обязан умереть вместе с прогоном: иначе следующий запуск начался
      // бы уже «отменённым» и упал бы на первом же обращении.
      endRun();
      ui._busy = false; refreshHeader();
    }
  }

  function ensure() {
    // Зеркало греем в фоне: строка «в истории N квартир» должна быть живой ещё
    // до первой выгрузки, но ждать её появления панель не обязана.
    storageReady().then(() => refreshBkInfo(true), () => {});
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
