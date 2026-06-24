"""
Сбор данных из источников.

Реальные бесплатные источники работают «из коробки»:
  * RSS независимых СМИ          — стандартный парсер на xml.etree;
  * Telegram                      — публичный веб-превью t.me/s/<channel> (без ключей);
  * DeepState                     — открытый эндпоинт /api/history/last, считаем площадь.

Платные/закрытые источники имеют модульные адаптеры с деградацией в сэмпл:
  * X / Twitter                   — реальный API v2, если задан X_BEARER_TOKEN,
                                    иначе data/samples/twitter.json.

Каждый адаптер возвращает (items, status). Item — это dict с полями, понятными
store.upsert_items(); поля relevant/signals заполняет шаг анализа.
"""

from __future__ import annotations

import hashlib
import html
import json
import math
import os
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from html.parser import HTMLParser
from xml.etree import ElementTree as ET

import requests

import config


# --------------------------------------------------------------------------- #
#  Утилиты                                                                     #
# --------------------------------------------------------------------------- #
def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def _to_utc_iso(dt: datetime | None) -> str:
    if dt is None:
        return _now_iso()
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def _parse_date(text: str | None) -> str:
    if not text:
        return _now_iso()
    text = text.strip()
    # RFC-822 (RSS pubDate)
    try:
        return _to_utc_iso(parsedate_to_datetime(text))
    except Exception:
        pass
    # ISO-8601 (Atom)
    try:
        return _to_utc_iso(datetime.fromisoformat(text.replace("Z", "+00:00")))
    except Exception:
        pass
    # Twitter/X формат: "Tue Jun 23 22:31:02 +0000 2026"
    try:
        return _to_utc_iso(datetime.strptime(text, "%a %b %d %H:%M:%S %z %Y"))
    except Exception:
        return _now_iso()


# Браузерный UA для эндпоинтов, которые отвергают «бота» (X/syndication).
BROWSER_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")


_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")


def strip_html(text: str | None, limit: int = 600) -> str:
    if not text:
        return ""
    text = _TAG_RE.sub(" ", text)
    text = html.unescape(text)
    text = _WS_RE.sub(" ", text).strip()
    return text[:limit]


def _mk_id(*parts: str) -> str:
    h = hashlib.sha1("||".join(p or "" for p in parts).encode("utf-8")).hexdigest()
    return h[:20]


def _session() -> requests.Session:
    s = requests.Session()
    s.headers.update({"User-Agent": config.USER_AGENT, "Accept": "*/*"})
    return s


def _http_get(url: str, **kw) -> requests.Response:
    last = None
    sess = kw.pop("session", None) or _session()
    for attempt in range(3):
        try:
            r = sess.get(url, timeout=config.HTTP_TIMEOUT, **kw)
            r.raise_for_status()
            return r
        except Exception as e:  # noqa: BLE001
            last = e
    raise last  # type: ignore[misc]


def _ln(tag: str) -> str:
    """Локальное имя XML-тега без namespace."""
    return tag.rsplit("}", 1)[-1].lower()


# --------------------------------------------------------------------------- #
#  RSS                                                                         #
# --------------------------------------------------------------------------- #
def parse_rss(xml_bytes: bytes) -> list[dict]:
    """Возвращает список {title, link, summary, published} из RSS 2.0 или Atom."""
    root = ET.fromstring(xml_bytes)
    entries: list[dict] = []

    # Соберём все <item> (RSS) и <entry> (Atom) в любом месте дерева.
    nodes = [el for el in root.iter() if _ln(el.tag) in ("item", "entry")]
    for node in nodes:
        title = link = summary = published = None
        for child in node:
            name = _ln(child.tag)
            if name == "title" and title is None:
                title = (child.text or "").strip()
            elif name == "link":
                # Atom: href в атрибуте; RSS: текст узла.
                href = child.attrib.get("href")
                rel = child.attrib.get("rel", "alternate")
                if href and (link is None or rel == "alternate"):
                    link = href
                elif child.text and link is None:
                    link = child.text.strip()
            elif name in ("description", "summary", "encoded", "content") and not summary:
                summary = strip_html(child.text or "".join(child.itertext()))
            elif name in ("pubdate", "published", "updated", "date") and published is None:
                published = child.text
        if title or summary:
            entries.append(
                {
                    "title": title or "",
                    "link": link or "",
                    "summary": summary or "",
                    "published": _parse_date(published),
                }
            )
    return entries


def fetch_rss(source: dict) -> tuple[list[dict], dict]:
    items: list[dict] = []
    status = {
        "source_id": source["id"],
        "name": source["name"],
        "stream": source["stream"],
        "mode": "live",
        "last_ok": None,
        "last_error": None,
        "items_count": 0,
    }
    try:
        r = _http_get(source["url"])
        for e in parse_rss(r.content):
            link = e["link"]
            items.append(
                {
                    "id": _mk_id(source["id"], link, e["title"]),
                    "source_id": source["id"],
                    "source_name": source["name"],
                    "stream": source["stream"],
                    "lang": source.get("lang", "ru"),
                    "title": e["title"],
                    "summary": e["summary"],
                    "url": link,
                    "published": e["published"],
                    "fetched_at": _now_iso(),
                    "relevant": 0,
                    "signals": [],
                    "source_weight": source.get("source_weight", 1.0),
                }
            )
        status["last_ok"] = _now_iso()
        status["items_count"] = len(items)
    except Exception as e:  # noqa: BLE001
        status["mode"] = "error"
        status["last_error"] = f"{type(e).__name__}: {e}"[:300]
    return items, status


# --------------------------------------------------------------------------- #
#  Telegram (публичный веб-превью)                                             #
# --------------------------------------------------------------------------- #
class _TGParser(HTMLParser):
    """Достаёт из t.me/s/<channel> текст постов, ссылку (data-post) и время."""

    def __init__(self) -> None:
        super().__init__()
        self.messages: list[dict] = []
        self._cur: dict | None = None
        self._div_is_text: list[bool] = []  # стек div'ов: текстовый ли он/внутри текста

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "data-post" in a:  # начало нового сообщения
            self._finalize()
            self._cur = {"post": a["data-post"], "datetime": None, "text": []}
        if tag == "div":
            cls = a.get("class", "") or ""
            is_text = ("tgme_widget_message_text" in cls) or any(self._div_is_text)
            self._div_is_text.append(is_text)
        if tag == "time" and self._cur is not None and a.get("datetime"):
            self._cur["datetime"] = a["datetime"]  # последний time = время поста

    def handle_endtag(self, tag):
        if tag == "div" and self._div_is_text:
            self._div_is_text.pop()

    def handle_data(self, data):
        if self._cur is not None and any(self._div_is_text):
            self._cur["text"].append(data)

    def _finalize(self):
        if self._cur and self._cur["text"]:
            text = _WS_RE.sub(" ", "".join(self._cur["text"])).strip()
            if text:
                self.messages.append(
                    {"post": self._cur["post"], "datetime": self._cur["datetime"], "text": text}
                )
        self._cur = None

    def close(self):
        self._finalize()
        super().close()


def fetch_telegram(cfg: dict, limit: int = 30) -> tuple[list[dict], dict]:
    url = f"https://t.me/s/{cfg['channel']}"
    items: list[dict] = []
    status = {
        "source_id": cfg["id"],
        "name": cfg["name"],
        "stream": cfg["stream"],
        "mode": "live",
        "last_ok": None,
        "last_error": None,
        "items_count": 0,
    }
    try:
        r = _http_get(url)
        p = _TGParser()
        p.feed(r.text)
        p.close()
        for m in p.messages[-limit:]:
            text = m["text"]
            link = f"https://t.me/{m['post']}"
            items.append(
                {
                    "id": _mk_id(cfg["id"], m["post"]),
                    "source_id": cfg["id"],
                    "source_name": cfg["name"],
                    "stream": cfg["stream"],
                    "lang": "ru",
                    "title": text[:140],
                    "summary": text[:600],
                    "url": link,
                    "published": _parse_date(m["datetime"]),
                    "fetched_at": _now_iso(),
                    "relevant": 0,
                    "signals": [],
                    "source_weight": cfg.get("source_weight", 0.7),
                }
            )
        status["last_ok"] = _now_iso()
        status["items_count"] = len(items)
    except Exception as e:  # noqa: BLE001
        status["mode"] = "error"
        status["last_error"] = f"{type(e).__name__}: {e}"[:300]
    return items, status


# --------------------------------------------------------------------------- #
#  X / Twitter (реальный API v2 или сэмпл)                                     #
# --------------------------------------------------------------------------- #
def _twitter_sample() -> tuple[list[dict], dict]:
    path = os.path.join(config.SAMPLES_DIR, "twitter.json")
    status = {
        "source_id": "x_analysts",
        "name": "X / военные аналитики",
        "stream": "analysts",
        "mode": "sample",
        "last_ok": _now_iso(),
        "last_error": None,
        "items_count": 0,
    }
    items: list[dict] = []
    try:
        raw = json.load(open(path, encoding="utf-8"))
        base = datetime.now(timezone.utc)
        for idx, t in enumerate(raw):
            # Сэмпл — демо-данные: проставляем свежие метки времени, чтобы посты
            # попадали в окно расчёта независимо от даты файла.
            published = _to_utc_iso(base - timedelta(hours=8 * idx))
            items.append(
                {
                    "id": _mk_id("x", t.get("id", t.get("url", t["text"][:40]))),
                    "source_id": "x_analysts",
                    "source_name": f"X · @{t.get('author', 'analyst')}",
                    "stream": "analysts",
                    "lang": t.get("lang", "en"),
                    "title": t["text"][:140],
                    "summary": t["text"][:600],
                    "url": t.get("url", ""),
                    "published": published,
                    "fetched_at": _now_iso(),
                    "relevant": 0,
                    "signals": [],
                    "source_weight": 0.7,
                }
            )
        status["items_count"] = len(items)
    except Exception as e:  # noqa: BLE001
        status["mode"] = "error"
        status["last_error"] = f"sample: {e}"[:300]
    return items, status


def fetch_twitter() -> tuple[list[dict], dict]:
    """Реальные твиты аналитиков через реверс-инжиниринг публичного веб-эндпоинта.

    Используем внутренний JSON веб-виджета X (syndication timeline-profile):
    он отдаёт ~28 последних твитов аккаунта в блоке __NEXT_DATA__ без ключа и
    без авторизации. Это разбор публично доступной информации (как делают
    OSINT-инструменты), низкая частота запросов. При неудаче — официальный API
    (если задан токен) или сэмпл.
    """
    items: list[dict] = []
    errors: list[str] = []
    for sn in config.X_ACCOUNTS:
        try:
            items += _x_syndication(sn)
        except Exception as e:  # noqa: BLE001
            errors.append(f"{sn}:{type(e).__name__}")
    if items:
        status = {
            "source_id": "x_analysts", "name": "X / военные аналитики", "stream": "analysts",
            "mode": "live", "last_ok": _now_iso(),
            "last_error": ("; ".join(errors)[:200] or None), "items_count": len(items),
        }
        return items, status
    if config.X_BEARER_TOKEN:
        return _twitter_official()
    items, st = _twitter_sample()
    st["last_error"] = "RE failed → sample: " + ("; ".join(errors)[:200])
    return items, st


def _x_syndication(screen_name: str, limit: int = 25) -> list[dict]:
    url = (f"https://syndication.twitter.com/srv/timeline-profile/screen-name/"
           f"{screen_name}?showReplies=false")
    r = _http_get(url, headers={"User-Agent": BROWSER_UA, "Accept": "text/html"})
    return _x_parse(r.text, screen_name, limit)


def _x_parse(text: str, screen_name: str, limit: int = 25) -> list[dict]:
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', text, re.DOTALL)
    if not m:
        raise ValueError("no __NEXT_DATA__")
    data = json.loads(m.group(1))
    entries = (((data.get("props") or {}).get("pageProps") or {}).get("timeline") or {}).get("entries") or []
    out: list[dict] = []
    for e in entries:
        t = (e.get("content") or {}).get("tweet") or {}
        text = t.get("full_text")
        if not text:
            continue
        sn = (t.get("user") or {}).get("screen_name", screen_name)
        ids = t.get("id_str", "")
        perma = t.get("permalink") or ""
        if perma.startswith("/"):
            perma = "https://twitter.com" + perma
        out.append({
            "id": _mk_id("x", ids or text[:40]),
            "source_id": "x_analysts",
            "source_name": f"X · @{sn}",
            "stream": "analysts",
            "lang": t.get("lang", "en"),
            "title": text[:140],
            "summary": text[:600],
            "url": perma or f"https://twitter.com/{sn}/status/{ids}",
            "published": _parse_date(t.get("created_at")),
            "fetched_at": _now_iso(),
            "relevant": 0, "signals": [], "source_weight": 0.7,
        })
        if len(out) >= limit:
            break
    return out


def _twitter_official() -> tuple[list[dict], dict]:
    status = {
        "source_id": "x_analysts",
        "name": "X / военные аналитики",
        "stream": "analysts",
        "mode": "live",
        "last_ok": None,
        "last_error": None,
        "items_count": 0,
    }
    try:
        r = _http_get(
            "https://api.twitter.com/2/tweets/search/recent",
            headers={"Authorization": f"Bearer {config.X_BEARER_TOKEN}"},
            params={
                "query": config.X_QUERY,
                "max_results": 50,
                "tweet.fields": "created_at,lang",
                "expansions": "author_id",
                "user.fields": "username",
            },
        )
        data = r.json()
        users = {u["id"]: u["username"] for u in data.get("includes", {}).get("users", [])}
        items = []
        for t in data.get("data", []):
            author = users.get(t.get("author_id"), "user")
            items.append(
                {
                    "id": _mk_id("x", t["id"]),
                    "source_id": "x_analysts",
                    "source_name": f"X · @{author}",
                    "stream": "analysts",
                    "lang": t.get("lang", "en"),
                    "title": t["text"][:140],
                    "summary": t["text"][:600],
                    "url": f"https://twitter.com/{author}/status/{t['id']}",
                    "published": _parse_date(t.get("created_at")),
                    "fetched_at": _now_iso(),
                    "relevant": 0,
                    "signals": [],
                    "source_weight": 0.7,
                }
            )
        status["last_ok"] = _now_iso()
        status["items_count"] = len(items)
        return items, status
    except Exception as e:  # noqa: BLE001
        # Деградация в сэмпл, чтобы поток не «падал» целиком.
        items, st = _twitter_sample()
        st["last_error"] = f"live failed → sample: {e}"[:300]
        return items, st


# --------------------------------------------------------------------------- #
#  DeepState: площадь оккупации                                                #
# --------------------------------------------------------------------------- #
_EARTH_R_KM = 6371.0088


def _ring_area_km2(coords: list) -> float:
    n = len(coords)
    if n < 3:
        return 0.0
    s = 0.0
    for i in range(n):
        lon1, lat1 = coords[i][0], coords[i][1]
        lon2, lat2 = coords[(i + 1) % n][0], coords[(i + 1) % n][1]
        s += math.radians(lon2 - lon1) * (
            2 + math.sin(math.radians(lat1)) + math.sin(math.radians(lat2))
        )
    return abs(s) * _EARTH_R_KM * _EARTH_R_KM / 2.0


def _polygon_area_km2(rings: list) -> float:
    if not rings:
        return 0.0
    area = _ring_area_km2(rings[0])
    for hole in rings[1:]:
        area -= _ring_area_km2(hole)
    return max(area, 0.0)


def _geometry_area_km2(geom: dict) -> float:
    t = geom.get("type")
    c = geom.get("coordinates")
    if t == "Polygon":
        return _polygon_area_km2(c)
    if t == "MultiPolygon":
        return sum(_polygon_area_km2(poly) for poly in c)
    return 0.0


_STATUS_RE = re.compile(r"geoJSON\.status\.([a-zA-Z_]+)")


def _status_of(props: dict) -> str:
    m = _STATUS_RE.search(str(props.get("name", "")))
    return m.group(1) if m else ""


def fetch_deepstate() -> dict:
    status = {"status": "error", "taken_at": _now_iso(), "occupied_km2": None,
              "unknown_km2": None, "occupied_polys": 0,
              "source_id": "deepstate", "name": "DeepStateMAP",
              "stream": "deepstate", "mode": "error", "last_error": None,
              "last_ok": None}
    try:
        r = _http_get(config.DEEPSTATE_LAST_URL)
        data = r.json()
        feats = data.get("map", data).get("features", [])
        occ = unk = 0.0
        occ_n = 0
        for f in feats:
            g = f.get("geometry", {})
            if g.get("type") not in ("Polygon", "MultiPolygon"):
                continue
            st = _status_of(f.get("properties", {}))
            if st == "occupied":
                occ += _geometry_area_km2(g)
                occ_n += 1
            elif st == "unknown":
                unk += _geometry_area_km2(g)
        taken = _parse_date(data.get("datetime")) if data.get("datetime") else _now_iso()
        status.update(
            status="ok", mode="live", last_ok=_now_iso(), taken_at=taken,
            occupied_km2=round(occ, 1), unknown_km2=round(unk, 1), occupied_polys=occ_n,
        )
    except Exception as e:  # noqa: BLE001
        status["last_error"] = f"{type(e).__name__}: {e}"[:300]
    return status


# --------------------------------------------------------------------------- #
#  Оркестровка сбора                                                           #
# --------------------------------------------------------------------------- #
def collect_all() -> dict:
    """Параллельно собирает все источники.

    Возвращает {items, statuses, deepstate}.
    """
    items: list[dict] = []
    statuses: list[dict] = []
    tasks = []

    with ThreadPoolExecutor(max_workers=12) as ex:
        for src in config.RSS_SOURCES:
            tasks.append(ex.submit(fetch_rss, src))
        for tg in config.TELEGRAM_CHANNELS:
            tasks.append(ex.submit(fetch_telegram, tg))
        tasks.append(ex.submit(fetch_twitter))
        ds_future = ex.submit(fetch_deepstate)

        for fut in as_completed(tasks):
            its, st = fut.result()
            items.extend(its)
            statuses.append(st)
        deepstate = ds_future.result()

    # статус DeepState — в общий список (без поля items_count лишнего)
    statuses.append(
        {
            "source_id": "deepstate",
            "name": "DeepStateMAP (карта фронта)",
            "stream": "deepstate",
            "mode": deepstate.get("mode", "error"),
            "last_ok": deepstate.get("last_ok"),
            "last_error": deepstate.get("last_error"),
            "items_count": deepstate.get("occupied_polys", 0),
        }
    )
    return {"items": items, "statuses": statuses, "deepstate": deepstate}
