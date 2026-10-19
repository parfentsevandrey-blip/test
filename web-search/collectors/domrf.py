"""Сборщик каталога новостроек наш.дом.рф (ЕИСЖС) — чистый HTTP, без браузера.

Как сайт отдаёт данные (проверено 2026-10-19, см. docs/research-domrf-cian.md):
- Антибот Servicepipe отсекает Chrome/Firefox-отпечатки (JS-челлендж -> капча), но запросы с TLS-отпечатком
  Safari к JSON-API пропускает -> curl_cffi impersonate="safari18_0".
- Закрыт отдельным правилом только /сервисы/api/object/* (карточка); всё нужное есть в открытых методах:
    GET  /сервисы/api/kn/object?offset&limit<=50&objStatus&place   список объектов (0 строящиеся, 1 проблемные, 2 сданные)
    GET  /сервисы/api/kn/compare?objectId=..x10&diff=false         характеристики объектов (до 10 за запрос)
    POST /portal-kn/api/sales/portal/flats {externalIds, limit<=100} квартиры объекта (если застройщик публикует продажи)
    GET  /portal-kn/api/kn/objects/<objId>/flats?limit&offset        квартиры, краткий вид + total
    GET  /portal-kn/api/kn/objects/near?objId=                       соседние ЖК с квартирографией
    GET  /portal-kn/api/kn/places?search=                            регионы (regionCd)
  Маршруты взяты статическим разбором JS-бандла /сервисы/_next (rea analyze-javascript-application).

Примеры:
  python collectors/domrf.py objects --status 0 --region 77 --pages 2 --details --out data/domrf-objects.jsonl
  python collectors/domrf.py flats --from data/domrf-objects.jsonl --max-objects 20 --out data/domrf-flats.jsonl
  python collectors/domrf.py flats --obj-id 73741 --out data/domrf-flats-73741.jsonl
  python collectors/domrf.py flats --obj-id 73741 --units --out data/domrf-units-73741.jsonl   # вся квартирография
"""
import argparse, json, os, random, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import quote

from curl_cffi import requests

H = "https://xn--80az8a.xn--d1aqf.xn--p1ai"
LIST = quote("/сервисы/api/kn/object")
COMPARE = quote("/сервисы/api/kn/compare")
SALES_FLATS = "/portal-kn/api/sales/portal/flats"
LIST_PAGE, COMPARE_BATCH, FLATS_PAGE = 50, 10, 100


class DomRF:
    def __init__(self, delay=(0.6, 1.2)):
        self.delay = delay
        self._new_session()

    def _new_session(self):
        proxy = os.environ.get("DOMRF_PROXY")
        self.s = requests.Session(impersonate="safari18_0", proxies={"https": proxy} if proxy else None)

    def _req(self, method, url, **kw):
        for attempt in range(5):
            time.sleep(random.uniform(*self.delay))
            try:
                r = self.s.request(method, url, timeout=60, **kw)
            except Exception as e:
                print(f"  {e.__class__.__name__} -> retry", file=sys.stderr)
                self._new_session(); time.sleep(5 * 2 ** attempt); continue
            challenged = "servicepipe" in r.text[:3000] or "заблокирован" in r.text[:3000]
            if r.status_code in (200, 201) and not challenged:
                return r.json()
            if r.status_code == 403 or challenged:
                # блок Servicepipe: новая сессия (новое соединение) и пауза
                print(f"  403/challenge on {url[:90]} -> new session, sleep", file=sys.stderr)
                self._new_session(); time.sleep(15 * 2 ** attempt); continue
            if r.status_code >= 500:
                time.sleep(5 * 2 ** attempt); continue
            raise RuntimeError(f"{r.status_code} {url}: {r.text[:200]}")
        raise RuntimeError(f"gave up: {url}")

    def objects(self, status=0, region=None, max_pages=None):
        page = 0
        while max_pages is None or page < max_pages:
            qs = f"offset={page * LIST_PAGE}&limit={LIST_PAGE}&sortField=objPublDt&sortType=desc&objStatus={status}"
            if region:
                qs += f"&place={region}"
            data = self._req("GET", f"{H}{LIST}?{qs}")["data"]
            if page == 0:
                print(f"  total objects: {data['total']}", file=sys.stderr)
            yield from data["list"]
            page += 1
            if len(data["list"]) < LIST_PAGE:
                break

    def details(self, obj_ids):
        """objId -> карточка из /kn/compare (батчи по 10)."""
        out = {}
        for i in range(0, len(obj_ids), COMPARE_BATCH):
            qs = "&".join(f"objectId={x}" for x in obj_ids[i:i + COMPARE_BATCH]) + "&diff=false"
            for d in self._req("GET", f"{H}{COMPARE}?{qs}")["data"]:
                out[d["objId"]] = d
        return out

    def units(self, obj_id):
        """Полная квартирография дома (в т.ч. не в продаже: status/price = null), краткие поля."""
        offset = 0
        while True:
            d = self._req("GET", f"{H}/portal-kn/api/kn/objects/{obj_id}/flats?limit={FLATS_PAGE}&offset={offset}")
            for u in d["data"]:
                yield dict(u, externalId=obj_id)
            offset += FLATS_PAGE
            if offset >= d["total"] or not d["data"]:
                break

    def flats(self, obj_id):
        offset = 0
        while True:
            body = {"externalIds": [obj_id], "sort": "area", "order": "asc", "limit": FLATS_PAGE, "offset": offset}
            page = self._req("POST", H + SALES_FLATS, json=body)
            yield from page
            if len(page) < FLATS_PAGE:
                break
            offset += FLATS_PAGE


def object_row(o, d=None):
    dev = o.get("developer") or {}
    d = d or {}
    return {
        "source": "domrf", "obj_id": o["objId"], "hobj_id": o.get("hobjId"),
        "url": f"{H}{quote('/сервисы/каталог-новостроек/объект/')}{o['objId']}",
        "jk_name": d.get("objCommercNm"), "status": o.get("siteStatus"), "problem": o.get("problemFlag"),
        "developer": dev.get("shortName"), "developer_group": dev.get("groupName") or d.get("devGroupNm"),
        "developer_inn": dev.get("devInn"), "developer_id": dev.get("devId"),
        "region_cd": o.get("rpdRegionCd"), "address": o.get("objAddr"), "lat": o.get("latitude"), "lng": o.get("longitude"),
        "build_type": o.get("buildType"), "floors_min": o.get("objFloorMin"), "floors_max": o.get("objFloorMax"),
        "flats_count": o.get("objElemLivingCnt"), "living_area": o.get("objSquareLiving"),
        "ready_date": o.get("objReady100PercDt"), "keys_date": d.get("objTransferPlanDt"),
        "escrow": bool(o.get("objGuarantyEscrowFlg")), "guaranty": d.get("objGuaranty"),
        "rpd_num": o.get("rpdNum"), "pd_id": o.get("pdId"), "published": o.get("objPublDt"),
        "class": d.get("objLkClassDesc"), "walls": d.get("objWallMaterialShortDesc"), "finish": d.get("objLkFinishTypeDesc"),
        "entrances": d.get("objLivElemEntrCnt"), "flats_per_floor_avg": d.get("objLivElemEntrFloorAvg"),
        "parking_places": d.get("parkingCnt"), "price_m2_avg": d.get("objPriceAvg"), "sold_pct": d.get("soldOutPerc"),
        "energy_class": d.get("objEnergyEffShortDesc"),
        "metro": [(m.get("metroStationNm"), round(m.get("metroStationDistTime") or 0), "walk" if m.get("isMetroStationDistWalking") else "transport")
                  for m in d.get("objectTransportInfo") or []],
        "render_url": o.get("hobjRenderPhotoUrl"), "ai_description": o.get("aiDescription"),
    }


def flat_row(f):
    return {
        "source": "domrf", "obj_id": f.get("externalId"), "flat_id": f.get("id"), "ods_id": f.get("odsId"),
        "number": f.get("number"), "type": f.get("type"), "rooms": f.get("rooms"), "is_studio": f.get("isStudio"),
        "area_total": f.get("totalArea"), "area_living": f.get("livingArea"), "area_kitchen": f.get("kitchenArea"),
        "floor": f.get("floor", f.get("floorNumber")), "floors_total": f.get("numberFloors"),
        "entrance": f.get("entrance", f.get("entranceNumber")),
        "status": f.get("status"), "price_rub": f.get("price"), "price_m2": f.get("pricePerSquareMeter"),
        "price_changed": f.get("priceChangeDate"), "finish": f.get("state"), "ceiling": f.get("ceilingHeight"),
        "layout_images": [f"{H}/api/ext/file/{i['attachmentId']}" for i in f.get("images") or [] if i.get("attachmentId")],
        "updated": f.get("updatedAt"),
    }


def write_jsonl(rows, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    n = 0
    with open(path, "w") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
            n += 1
    print(f"wrote {n} rows -> {path}", file=sys.stderr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["objects", "flats"])
    ap.add_argument("--status", type=int, default=0, help="0 строящиеся, 1 проблемные, 2 сданные")
    ap.add_argument("--region", type=int, help="regionCd: 77 Москва, 50 МО, 78 СПб ...")
    ap.add_argument("--pages", type=int, help="страниц по 50 объектов (по умолчанию все)")
    ap.add_argument("--details", action="store_true", help="добавить характеристики из /kn/compare")
    ap.add_argument("--obj-id", type=int)
    ap.add_argument("--from", dest="src", help="JSONL объектов (из команды objects)")
    ap.add_argument("--max-objects", type=int)
    ap.add_argument("--units", action="store_true", help="вся квартирография дома, а не только квартиры в продаже")
    ap.add_argument("--workers", type=int, default=1, help="параллельных объектов для flats (стресс-тест: 8 без блоков)")
    ap.add_argument("--raw", action="store_true")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    c = DomRF()

    if a.cmd == "objects":
        objs = list(c.objects(a.status, a.region, a.pages))
        det = c.details([o["objId"] for o in objs]) if a.details else {}
        write_jsonl((dict(o, details=det.get(o["objId"])) if a.raw else object_row(o, det.get(o["objId"])) for o in objs), a.out)
    else:
        if a.obj_id:
            ids = [a.obj_id]
        else:
            ids = [json.loads(l).get("obj_id") or json.loads(l).get("objId") for l in open(a.src)]
        ids = ids[:a.max_objects] if a.max_objects else ids
        local = threading.local()

        def one(oid):
            if not hasattr(local, "c"):
                local.c = DomRF()
            return [f if a.raw else flat_row(f) for f in (local.c.units(oid) if a.units else local.c.flats(oid))]

        def gen():
            failed = []
            with ThreadPoolExecutor(a.workers) as ex:
                futs = {ex.submit(one, oid): oid for oid in ids}
                for i, fut in enumerate(as_completed(futs), 1):
                    oid = futs[fut]
                    try:
                        rows = fut.result()
                    except Exception as e:  # один объект не должен ронять весь прогон
                        failed.append(oid)
                        print(f"  [{i}/{len(ids)}] obj {oid}: FAILED: {str(e)[:120]}", file=sys.stderr)
                        continue
                    print(f"  [{i}/{len(ids)}] obj {oid}: {len(rows)} flats", file=sys.stderr)
                    yield from rows
            if failed:
                print(f"  failed objects ({len(failed)}): {failed} -- перезапустите с --obj-id", file=sys.stderr)

        write_jsonl(gen(), a.out)


if __name__ == "__main__":
    main()
