"""Общее для ежедневного конвейера Циана в пределах МКАД: пути, полигон МКАД, плоская запись лота, ввод-вывод."""
import datetime as dt, gzip, json, os, sys, threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "collectors"))
import cian  # noqa: E402

DATA = os.environ.get("DAILY_DATA") or os.path.join(ROOT, "history")  # история в репозитории; тесты -- DAILY_DATA
GEO = os.path.join(ROOT, "geo")  # полигон МКАД и список ЖК внутри него -- в репозитории
MKAD = json.load(open(os.path.join(GEO, "mkad.json")))["coordinates"][0]
# в запрос Циана -- каждая 8-я вершина (~48 точек): длина запроса разумная, отклонение от МКАД < 300 м;
# точная граница применяется локально по координатам лота
MKAD_QUERY = {"type": "polygon", "name": "МКАД", "coordinates": [[str(x), str(y)] for x, y in MKAD[::8] + [MKAD[0]]]}


def inside_mkad(lat, lng):
    if lat is None or lng is None:
        return False
    c = False
    for (x1, y1), (x2, y2) in zip(MKAD, MKAD[1:]):
        if (y1 > lat) != (y2 > lat) and lng < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
            c = not c
    return c


def day_dir(day):
    d = os.path.join(DATA, day)
    os.makedirs(d, exist_ok=True)
    return d


def today():
    return dt.date.today().isoformat()


_local = threading.local()


def client(delay=None):
    """Своя сессия Циана на поток."""
    if not hasattr(_local, "c"):
        _local.c = cian.Cian(delay)
    return _local.c


def _num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def lot_row(o):
    """Плоская запись лота: всё, что нужно для спроса, цены и проверки «настоящий интерес / накрутка»."""
    bt, b, nb, geo = o.get("bargainTerms") or {}, o.get("building") or {}, o.get("newbuilding") or {}, o.get("geo") or {}
    house, dl, coords, user = nb.get("house") or {}, b.get("deadline") or {}, geo.get("coordinates") or {}, o.get("user") or {}
    addr = {a.get("type"): a.get("name") for a in geo.get("address") or []}
    metro = min(geo.get("undergrounds") or [], key=lambda u: u.get("time") or 99, default={})
    area, price = _num(o.get("totalArea")), bt.get("priceRur")
    disc = o.get("discount") or {}
    return {
        "id": o["id"], "cian_id": o.get("cianId"), "url": o["fullUrl"].split("?")[0],
        "kind": "new" if o.get("category") == "newBuildingFlatSale" else "sec",
        "category": o.get("category"), "share": o.get("shareAmount"),  # доли и комнаты -- не квартира целиком
        "price": price, "pm2": round(price / area) if price and area else None, "area": area,
        "living": _num(o.get("livingArea")), "kitchen": _num(o.get("kitchenArea")),
        "rooms": o.get("roomsCount"), "flat_type": o.get("flatType"), "apartments": o.get("isApartments"),
        "floor": o.get("floorNumber"), "floors": b.get("floorsCount"), "decoration": o.get("decoration"),
        "furniture": o.get("hasFurniture"), "build_year": b.get("buildYear"), "material": b.get("materialType"),
        "deadline": dl.get("quarterEnd"), "deadline_done": dl.get("isComplete"),
        "jk_id": nb.get("id"), "jk": nb.get("name"), "house_id": house.get("id"), "house": house.get("name"),
        "okrug": addr.get("okrug"), "raion": addr.get("raion"), "street": addr.get("street"), "address": geo.get("userInput"),
        "lat": coords.get("lat"), "lng": coords.get("lng"),
        "metro": metro.get("name"), "metro_min": metro.get("time"), "metro_by": metro.get("transportType"),
        "seller": "developer" if o.get("fromDeveloper") else ("owner" if o.get("isByHomeowner") else "agent"),
        "user_id": o.get("userId") or o.get("publishedUserId"), "agency": user.get("agencyName"),
        "sale_type": bt.get("saleType"), "mortgage": bt.get("mortgageAllowed"),
        "created": (o.get("creationDate") or "")[:19], "added_ts": o.get("addedTimestamp"),
        # платные и рекламные усилители просмотров
        "top3": o.get("isTop3"), "premium": o.get("isPremium"), "colorized": o.get("isColorized"),
        "auction": o.get("isAuction"), "branding": o.get("brandingLevel"),
        "promo_name": (o.get("promoInfo") or {}).get("name"), "discount_pct": disc.get("percent"),
        # статусы сделки и признаки качества объявления
        "booked": o.get("isBookedFromDeveloper"), "in_deal": o.get("offerInDeal"), "status": o.get("status"),
        "dup_descr": o.get("isDuplicatedDescription"), "recidivist": o.get("isRecidivist"),
        "rosreestr": o.get("isRosreestrChecked"), "unique": o.get("isUnique"),
        "photos": len(o.get("photos") or []), "layout": any(p.get("isLayout") for p in o.get("photos") or []),
        "videos": len(o.get("videos") or []), "descr_len": len(o.get("description") or ""),
        "minhash": (o.get("descriptionMinhash") or [])[:16], "similar": (o.get("similar") or {}).get("count", 0),
    }


def write_jsonl(path, rows):
    tmp = path + ".tmp"
    n = 0
    with (gzip.open(tmp, "wt", encoding="utf-8") if path.endswith(".gz") else open(tmp, "w", encoding="utf-8")) as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            n += 1
    os.replace(tmp, path)  # атомарно: недописанный файл не выдаётся за готовый
    return n


def read_jsonl(path):
    with (gzip.open(path, "rt", encoding="utf-8") if path.endswith(".gz") else open(path, encoding="utf-8")) as f:
        for line in f:
            yield json.loads(line)


def log(*a):
    print(dt.datetime.now().strftime("%H:%M:%S"), *a, file=sys.stderr, flush=True)
