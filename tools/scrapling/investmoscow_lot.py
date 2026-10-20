"""Collect everything about an investmoscow.ru tender lot.

Opens the lot card in Scrapling's stealth browser, clicks the
"Сведения о процедуре" tab, records every API response the page makes,
and saves the raw JSON plus a short summary (price, deposit, step, dates...).

Usage:
    python investmoscow_lot.py 20208859
    python investmoscow_lot.py https://investmoscow.ru/tenders/tender/20208859 20208860 -o lots
    python investmoscow_lot.py 20208859 --proxy http://user:pass@host:port --headful

investmoscow.ru drops connections from non-Russian IPs: run it from Russia or pass a Russian --proxy.
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

from scrapling.fetchers import Fetcher, StealthySession

SITE = "https://investmoscow.ru"
API_BASE = "https://api.investmoscow.ru/investmoscow/tender"
PROCEDURE_TAB = "Сведения о процедуре"

# Labels shown on the card, matched at the start of a line of page text
PAGE_LABELS = {
    "start_price": r"Начальная (?:цена|стоимость)",
    "price_per_m2": r"Цена за (?:1 )?(?:м²|м2|кв\.? ?м)",
    "deposit": r"(?:Размер )?задат",
    "auction_step": r"Шаг (?:аукциона|торгов|понижения)",
    "min_price": r"Минимальная цена|Цена отсечения",
    "trade_form": r"Форма (?:проведения|торгов)|Способ (?:приватизации|продажи)|Вид торгов",
    "procedure_number": r"Номер (?:процедуры|извещения|торгов)|№ (?:процедуры|извещения)",
    "lot_number": r"Номер лота|№ лота",
    "platform": r"(?:Электронная )?(?:торговая )?площадка|Оператор",
    "organizer": r"Организатор",
    "seller": r"Продавец|Собственник|Балансодержатель",
    "status": r"Статус",
    "applications_start": r"Дата начала при[её]ма заявок",
    "applications_end": r"Дата окончания при[её]ма заявок",
    "participants_selection": r"Отбор участников|Определение участников",
    "auction_date": r"Проведение торгов|Дата (?:проведения )?(?:аукциона|торгов)",
    "results": r"Подведение итогов",
    "address": r"Адрес",
    "building_area": r"Площадь здания|Площадь объекта",
    "land_area": r"Площадь земельного участка",
    "cadastral_building": r"Кадастровый номер здания|Кадастровый номер помещения|Кадастровый номер объекта",
    "cadastral_land": r"Кадастровый номер земельного участка",
    "purpose": r"(?:Функциональное )?[Нн]азначение",
    "floors": r"Этажность|Этаж",
    "tenant": r"Наличие арендатора",
    "encumbrances": r"Обременени",
    "heritage": r"Объект культурного наследия",
}

# JSON keys worth surfacing in the summary
JSON_KEY_HINTS = re.compile(
    r"price|cost|sum|deposit|step|auction|bidding|procedure|notice|number|status|organizer|seller|"
    r"etp|platform|link|url|date|start|end|area|square|cadastr|address|name|title|form|type",
    re.IGNORECASE,
)


def tender_id(value: str) -> str:
    match = re.search(r"(\d{5,})", value)
    if not match:
        raise SystemExit(f"Не похоже на ID или ссылку лота: {value!r}")
    return match.group(1)


def flatten(data, prefix=""):
    """Yield (path, value) pairs for every scalar inside a JSON document."""
    if isinstance(data, dict):
        for key, value in data.items():
            yield from flatten(value, f"{prefix}.{key}" if prefix else str(key))
    elif isinstance(data, list):
        for index, value in enumerate(data):
            yield from flatten(value, f"{prefix}[{index}]")
    elif data not in (None, "", [], {}):
        yield prefix, data


def labels_from_text(text: str) -> dict:
    """Read 'Label: value' or 'Label' + next line pairs from the rendered card."""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    found = {}
    for field, pattern in PAGE_LABELS.items():
        regex = re.compile(rf"^(?:{pattern})[^:\n]*?(?::\s*(.+))?$", re.IGNORECASE)
        for index, line in enumerate(lines):
            match = regex.match(line)
            if not match or len(line) > 80:
                continue
            value = match.group(1) or (lines[index + 1] if index + 1 < len(lines) else "")
            if value and not regex.match(value):
                found[field] = value
                break
    return found


def open_procedure_tab(page):
    """page_action: reveal lazy blocks and the procedure tab so their API calls get recorded."""
    try:
        page.get_by_text(PROCEDURE_TAB, exact=False).first.click(timeout=5_000)
        page.wait_for_load_state("networkidle", timeout=15_000)
    except Exception as error:  # the tab may be missing or already open
        print(f"  ! вкладка «{PROCEDURE_TAB}» не нажалась: {type(error).__name__}")
    for _ in range(6):
        page.mouse.wheel(0, 2_000)
        page.wait_for_timeout(400)
    try:
        page.wait_for_load_state("networkidle", timeout=10_000)
    except Exception:
        pass


def fetch_api_directly(tid: str, api_base: str, proxy: str | None) -> dict | None:
    """Fast path without a browser; often blocked, so failures are only reported."""
    url = f"{api_base}/v1/object-info/gettenderobjectinformation?tenderId={tid}"
    try:
        response = Fetcher.get(url, impersonate="chrome", proxy=proxy, timeout=30, retries=1)
        if response.status == 200:
            return {"url": url, "data": response.json()}
        print(f"  ! API напрямую: HTTP {response.status}")
    except Exception as error:
        print(f"  ! API напрямую недоступен: {type(error).__name__}: {error}")
    return None


def collect(tid: str, session: StealthySession, args) -> dict:
    out_dir = Path(args.output) / tid
    (out_dir / "api").mkdir(parents=True, exist_ok=True)
    page_url = f"{args.site}/tenders/tender/{tid}"
    print(f"\n=== Лот {tid}: {page_url}")

    api_payloads = []
    direct = None if args.no_direct_api else fetch_api_directly(tid, args.api_base, args.proxy)
    if direct:
        api_payloads.append(direct)

    session.network.clear()
    page = session.fetch(page_url, network_idle=True, page_action=open_procedure_tab, wait=1_000)
    print(f"  страница: HTTP {page.status}")

    for record in session.network.search(include_static=False, limit=None):
        content_type = record.headers.get("content-type", "") if record.headers else ""
        if "json" not in content_type.lower() or any(p["url"] == record.url for p in api_payloads):
            continue
        try:
            api_payloads.append({"url": record.url, "status": record.status, "data": record.json()})
        except Exception:
            continue

    for index, payload in enumerate(api_payloads, 1):
        slug = re.sub(r"[^a-zA-Z0-9]+", "_", payload["url"].split("?")[0].rsplit("/", 2)[-1])[:60]
        (out_dir / "api" / f"{index:02d}_{slug}.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    text = page.get_all_text(separator="\n", ignore_tags=("script", "style", "noscript"))
    (out_dir / "page.txt").write_text(text, encoding="utf-8")
    (out_dir / "page.md").write_text(page.markdown(), encoding="utf-8")

    json_hits = {}
    for payload in api_payloads:
        for path, value in flatten(payload["data"]):
            if JSON_KEY_HINTS.search(path.rsplit(".", 1)[-1]) and len(str(value)) < 300:
                json_hits.setdefault(path, value)

    summary = {
        "tender_id": tid,
        "url": page_url,
        "title": page.css("h1::text").get() or page.css("title::text").get(),
        "from_page": labels_from_text(text),
        "api_urls": [payload["url"] for payload in api_payloads],
        "from_api": json_hits,
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"  {summary['title']}")
    for field, value in summary["from_page"].items():
        print(f"  {field:24} {value}")
    print(f"  API-ответов сохранено: {len(api_payloads)}, полей из JSON: {len(json_hits)}")
    print(f"  → {out_dir}")
    return summary


def main():
    parser = argparse.ArgumentParser(description="Сбор данных о лотах investmoscow.ru через Scrapling")
    parser.add_argument("lots", nargs="+", help="ID лота или ссылка на него")
    parser.add_argument("-o", "--output", default="investmoscow_lots", help="папка для результатов")
    parser.add_argument("--proxy", help="прокси, например http://user:pass@host:port (нужен российский IP)")
    parser.add_argument("--headful", action="store_true", help="показать окно браузера")
    parser.add_argument("--chromium", default=os.environ.get("SCRAPLING_CHROMIUM"), help="путь к своему Chromium")
    parser.add_argument("--no-direct-api", action="store_true", help="не пробовать API без браузера")
    parser.add_argument("--site", default=SITE, help=argparse.SUPPRESS)
    parser.add_argument("--api-base", default=API_BASE, help=argparse.SUPPRESS)
    args = parser.parse_args()

    ids = [tender_id(value) for value in args.lots]
    results = []
    with StealthySession(
        headless=not args.headful,
        record_requests=True,
        executable_path=args.chromium or None,
        proxy=args.proxy,
        locale="ru-RU",
        timezone_id="Europe/Moscow",
        timeout=60_000,
    ) as session:
        for tid in ids:
            try:
                results.append(collect(tid, session, args))
            except Exception as error:
                print(f"  ✗ {tid}: {type(error).__name__}: {error}")
                print("    Если соединение сбрасывается — сайт блокирует зарубежные IP: запустите из РФ или с --proxy.")

    if len(results) > 1:
        combined = Path(args.output) / "all_lots.json"
        combined.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\nСводка по всем лотам: {combined}")
    return 0 if results else 1


if __name__ == "__main__":
    sys.exit(main())
