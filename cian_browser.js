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
  const esc = (s) => String(s == null ? "" : s)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g, "")
    .replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/g, "")
    .replace(/(^|[^\uD800-\uDBFF])([\uDC00-\uDFFF])/g, "$1")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
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
        { v: r.finishSrc },
        { v: r.price, t: r.price != null ? "Number" : "String", s: "num" },
        { v: r.ppm, t: r.ppm != null ? "Number" : "String", s: "num" },
        { v: r.published },
        { v: r.exposure, t: r.exposure !== "" ? "Number" : "String" },
        { v: r.updated },
        { v: r.description },
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

    xml += rowXml([{ v: "ОТДЕЛКА / РЕМОНТ", s: "bold" }]);
    xml += rowXml(["Категория отделки", "Лотов"].map((h) => ({ v: h, s: "hdr" })));
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
      .forEach((label) => { xml += rowXml([{ v: label }, { v: finCount[label], t: "Number" }]); });
    const byField = rows.filter((r) => r.finishSrc === "Циан-поле").length;
    const byText = rows.filter((r) => r.finishSrc === "из описания").length;
    const noFin = rows.filter((r) => !r.decoration).length;
    if (noFin) xml += rowXml([{ v: "Не определена" }, { v: noFin, t: "Number" }]);
    xml += rowXml([{ v: "Источник: поле Циан / из описания / нет", s: "sub" },
      { v: `${byField} / ${byText} / ${noFin}` }]);
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
