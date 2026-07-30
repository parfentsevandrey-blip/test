/* ============================================================================
 *  cian_browser.js — сбор отчёта по ЖК ПРЯМО В БРАУЗЕРЕ (без терминала/Python).
 *
 *  КАК ПОЛЬЗОВАТЬСЯ:
 *   1. Откройте в браузере страницу нужного ЖК на cian.ru (желательно войдите
 *      в аккаунт и пройдите капчу, если показана).
 *   2. Нажмите F12 -> вкладка Console (Консоль).
 *   3. Вставьте ВЕСЬ этот файл и нажмите Enter.
 *   4. Дождитесь окончания — браузер сам скачает файл
 *      cian_<жк>_<дата>.xlsx (настоящий xlsx: листы, таблицы, ссылки, подсветка).
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
    const desc = descriptionOf(o);          // полный текст — для классификации
    const fo = finishOf(o, desc);
    return {
      cianId: o.cianId || o.id || null,
      url: offerUrl(o),
      category: categoryOf(o),
      area, floor: o.floorNumber != null ? o.floorNumber : null,
      floors: dig(o, "building.floorsCount") || o.floorsCount || null,
      building: buildingOf(o),
      seller_type: sellerType(o), seller_name: sellerName(o),
      decoration: fo.fin, finishSrc: fo.src, description: clipDesc(desc),
      price, ppm: price && area ? Math.round(price / area) : null,
      published: pub ? fmtDate(pub) : "",
      exposure: pub ? Math.floor((Date.now() - pub.getTime()) / 86400000) : "",
      updated: updDate(o) ? fmtDate(updDate(o)) : "",
    };
  }

  // ---- генерация книги Excel (SpreadsheetML 2003, без библиотек) -----------
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

  // row(cells) — строка книги как массив ячеек: строители листов накапливают
  // дерево, а формат из него делает buildXlsxBlob().
  const row = (cells) => cells;

  // worksheet(...) — из накопленных строк собирает описание листа.
  // opts: { freezeRows, autoFilterRows }.
  function worksheet(name, cols, rows, opts) {
    opts = opts || {};
    const sh = { name: name, colWidthsPx: cols, rows: rows };
    if (opts.freezeRows) { sh.freeze = { rows: opts.freezeRows, cols: 0 }; sh.landscape = true; sh.fitWidth = 1; }
    if (opts.autoFilterRows) sh.table = { ref: rangeA1(1, 4, cols.length, 4 + opts.autoFilterRows) };
    return sh;
  }

  // ВНИМАНИЕ: HEADERS, COLW и массив ячеек в dataSheet обязаны совпадать по длине.
  // cell() не умеет ss:Index, поэтому пропуск ОДНОЙ ячейки бесшумно сдвигает все
  // последующие столбцы влево — Excel при этом не покажет ошибки.
  const HEADERS = ["№", "ID объявления", "Категория", "Площадь, м²", "Этаж", "Этаж-ность",
    "Корпус / секция", "Тип продавца", "Продавец", "Отделка/ремонт", "Источник отделки",
    "Цена, ₽", "Цена за м², ₽", "Дата публикации", "Срок эксп., дн", "Дата обновления",
    "Описание", "Ссылка"];
  const COLW = [34, 90, 80, 74, 44, 60, 110, 90, 150, 130, 92, 105, 95, 95, 80, 95, 320, 70];
  if (COLW.length !== HEADERS.length) throw new Error("COLW не совпадает с HEADERS");

  function dataSheet(name, title, sub, rows) {
    const R = [];
    R.push(row([{ v: title, s: "title", merge: HEADERS.length - 1 }]));
    R.push(row([{ v: sub, s: "sub", merge: HEADERS.length - 1 }]));
    R.push(row([{}]));
    R.push(row(HEADERS.map((h) => ({ v: h, s: "hdr" }))));
    rows.forEach((r, i) => {
      R.push(row([
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
        { v: r.finishSrc },
        { v: r.price, t: r.price != null ? "Number" : "String", s: "num" },
        { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: "num" },
        { v: r.published },
        { v: r.exposure, t: r.exposure !== "" ? "Number" : "String" },
        { v: r.updated },
        { v: r.description },
        r.url ? { v: "Циан →", href: r.url, s: "link" } : {},
      ]));
    });
    return worksheet(name, COLW, R, { freezeRows: 4, autoFilterRows: rows.length });
  }

  // ---- сводный лист (агрегаты считаем в JS) -------------------------------
  const CATS = ["Студия", "Своб. планировка", "1", "2", "3", "4+"];
  const ROOM_OF_CAT = { "Студия": [9], "Своб. планировка": [7], "1": [1], "2": [2], "3": [3], "4+": [4, 5, 6] };
  const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
  const num = (v) => (v == null ? { v: "—" } : { v, t: "Number", s: "num" });

  function summarySheet(rows, totalsByRoom, totalInJk) {
    const present = CATS.filter((c) => rows.some((r) => r.category === c));
    const today = fmtDate(new Date());
    const R = [];
    R.push(row([{ v: `ЖК ${JKNAME} (ID ${JKID}) — сводка`, s: "title", merge: 5 }]));
    R.push(row([{ v: `Данные Циан на ${today}. Собрано ${rows.length} лотов. «Частник» = собственник/агентство.`, s: "sub", merge: 5 }]));
    R.push(row([{}]));

    R.push(row([{ v: "ОХВАТ ВЫГРУЗКИ", s: "bold" }]));
    R.push(row(["Категория", "Собрано", "Всего на Циан", "% выдачи"].map((h) => ({ v: h, s: "hdr" }))));
    let sumC = 0, sumT = 0;
    present.forEach((c) => {
      const got = rows.filter((r) => r.category === c).length;
      const tot = ROOM_OF_CAT[c].reduce((s, rm) => s + (totalsByRoom[rm] || 0), 0) || null;
      sumC += got; if (tot) sumT += tot;
      R.push(row([{ v: c }, { v: got, t: "Number" }, num(tot),
        { v: tot ? Math.round((got / tot) * 100) + "%" : "—" }]));
    });
    R.push(row([{ v: "ИТОГО (категории)", s: "bold" }, { v: sumC, t: "Number", s: "bold" },
      num(sumT || null), { v: sumT ? Math.round((sumC / sumT) * 100) + "%" : "—" }]));
    if (totalInJk) R.push(row([{ v: "Всего квартир в ЖК (Циан)", s: "bold" },
      { v: rows.length, t: "Number" }, { v: totalInJk, t: "Number" },
      { v: Math.round((rows.length / totalInJk) * 100) + "%" }]));
    R.push(row([{}]));

    R.push(row([{ v: "СРЕДНЯЯ ЦЕНА ЗА м², ₽", s: "bold" }]));
    R.push(row(["Категория", "Частник", "Застройщик", "Все"].map((h) => ({ v: h, s: "hdr" }))));
    const ppmBy = (subset) => subset.map((r) => r.ppm).filter((x) => x != null);
    present.concat(["ИТОГО по ЖК"]).forEach((c) => {
      const sub = c === "ИТОГО по ЖК" ? rows : rows.filter((r) => r.category === c);
      const chast = ppmBy(sub.filter((r) => r.seller_type !== "Застройщик"));
      const zast = ppmBy(sub.filter((r) => r.seller_type === "Застройщик"));
      R.push(row([{ v: c, s: c === "ИТОГО по ЖК" ? "bold" : "" },
        num(avg(chast)), num(avg(zast)), num(avg(ppmBy(sub)))]));
    });
    R.push(row([{}]));

    R.push(row([{ v: "ОТДЕЛКА / РЕМОНТ", s: "bold" }]));
    R.push(row(["Категория отделки", "Лотов"].map((h) => ({ v: h, s: "hdr" }))));
    const finCount = {};
    rows.forEach((r) => { if (r.decoration) finCount[r.decoration] = (finCount[r.decoration] || 0) + 1; });
    // порядок берём из FIN, а не перепечатываем подписи руками — иначе при первой
    // же правке подписи ключи счётчика перестанут совпадать
    // Сначала известные категории в порядке FIN, затем — фактически встреченные
    // значения, которых в FIN нет (Циан ввёл новое). Без хвоста такой лот исчезал
    // из блока: в «Не определена» он не попадает, decoration у него не пустой.
    const known = Object.values(FIN);
    known.filter((l) => finCount[l])
      .concat(Object.keys(finCount).filter((l) => !known.includes(l)).sort())
      .forEach((label) => { R.push(row([{ v: label }, { v: finCount[label], t: "Number" }])); });
    const byField = rows.filter((r) => r.finishSrc === "Циан-поле").length;
    const byText = rows.filter((r) => r.finishSrc === "из описания").length;
    const noFin = rows.filter((r) => !r.decoration).length;
    if (noFin) R.push(row([{ v: "Не определена" }, { v: noFin, t: "Number" }]));
    R.push(row([{ v: "Источник: поле Циан / из описания / нет", s: "sub" },
      { v: `${byField} / ${byText} / ${noFin}` }]));
    R.push(row([{}]));

    R.push(row([{ v: "ДИАПАЗОН ЦЕН, ₽", s: "bold" }]));
    R.push(row(["Категория", "Мин. цена", "Средн. цена", "Макс. цена", "Мин. ₽/м²", "Макс. ₽/м²"].map((h) => ({ v: h, s: "hdr" }))));
    present.forEach((c) => {
      const sub = rows.filter((r) => r.category === c);
      const pr = sub.map((r) => r.price).filter((x) => x != null);
      const pm = sub.map((r) => r.ppm).filter((x) => x != null);
      R.push(row([{ v: c }, num(pr.length ? Math.min(...pr) : null), num(avg(pr)),
        num(pr.length ? Math.max(...pr) : null), num(pm.length ? Math.min(...pm) : null),
        num(pm.length ? Math.max(...pm) : null)]));
    });
    return worksheet("Сводка", [26, 16, 16, 16, 14, 14].map((w) => w * 6), R, null);
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
    return { font: "Calibri", fontSize: 11, sheets: sheets };
  }

  // Никакого BOM: .xlsx — это zip, и любой байт перед сигнатурой PK делает
  // архив нечитаемым. У прежнего .xls SpreadsheetML он, наоборот, был нужен.
  function download(blob, name) {
    const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1500);
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

    const fname = `cian_${slug(JKNAME)}_${new Date().toISOString().slice(0, 10)}.xlsx`;
    // сжатие потоковое, поэтому await
    download(await buildXlsxBlob(buildWorkbook(rows, totalsByRoom, totalInJk)), fname);

    log("=".repeat(48));
    log(`ГОТОВО: ${rows.length} лотов${totalInJk ? " из " + totalInJk + " (" + Math.round(rows.length / totalInJk * 100) + "%)" : ""}`);
    log(`Прямых ссылок на лот: ${direct}/${rows.length}`);
    if (ppms.length) log(`₽/м²: мин ${Math.min(...ppms).toLocaleString("ru")} | средн ${avg(ppms).toLocaleString("ru")} | макс ${Math.max(...ppms).toLocaleString("ru")}`);
    if (exps.length) log(`Срок экспозиции: средн ${Math.round(exps.reduce((a, b) => a + b, 0) / exps.length)} дн`);
    const withDesc = rows.filter((r) => r.description).length;
    const finText = rows.filter((r) => r.finishSrc === "из описания").length;
    const finAny = rows.filter((r) => r.decoration).length;
    log(`Описание есть у ${withDesc}/${rows.length}. Отделка определена у ${finAny} (из них по тексту ${finText}).`);
    log(`Файл скачан: ${fname}`);
    log("=".repeat(48));
    alert(`Готово! Собрано ${rows.length} лотов${totalInJk ? " из " + totalInJk : ""}.\nФайл ${fname} скачан — откройте его в Excel.`);
  } catch (e) {
    console.error(e);
    alert("Ошибка: " + e.message + "\nОткройте F12 -> Console для подробностей. " +
      "Часто помогает: обновить страницу ЖК, войти в аккаунт, пройти капчу и запустить снова.");
  }
})();
