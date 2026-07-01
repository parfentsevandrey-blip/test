"""EXPERIMENTAL, opt-in, best-effort live fetcher for cian.ru search results.

Read this before using it.

- We tested outbound access to cian.ru from a plain server environment and
  got an immediate WAF block ("cian_waf_block") for *every* request,
  including /robots.txt. cian.ru actively fingerprints and blocks
  automated/datacenter traffic. This provider may simply not work for
  you either — it is far more likely to get anywhere (if at all) from an
  ordinary residential home connection, used by hand, slowly.
- cian.ru's site rules reportedly require the site administration's
  permission to collect data with automated tools. This client does not
  try to work around that: it does not spoof a real browser fingerprint,
  rotate proxies/IPs, solve CAPTCHAs, or otherwise hide what it is. It
  sends a small number of requests under an honest User-Agent with a
  conservative delay between them, and gives up the moment it is blocked
  instead of retrying harder or in a different disguise. If it doesn't
  work, that block is cian telling you "no" for this traffic — respect it
  and use `FileImportProvider` (manually exported data) instead, which is
  the supported, reliable way to run this tool.
- Intended for personal, non-commercial, small-scale use (checking a
  handful of searches for your own apartment hunting) — not for building
  a redistributable copy of cian's listing database.

Search-result field extraction mirrors the public DOM markers documented
by the open-source `cianparser` project (MIT-licensed,
github.com/lenarsaitov/cianparser): `article[data-name='CardComponent']`
cards, a `LinkArea` block holding the url/price/specs, and 'ЖК «...»' text
for the residential complex name. cian reshapes its markup periodically;
if this stops matching, that is expected, not a bug to chase.
"""

from __future__ import annotations

import re
import time
from typing import List, Optional

import bs4
import requests

from .._coerce import to_float
from ..models import Offer

SEARCH_URL = "https://www.cian.ru/cat.php"
USER_AGENT = "Mozilla/5.0 (compatible; MoscowApartmentValuator/0.1; personal-use research script)"
BLOCK_MARKERS = ("captcha", "cian_waf_block", "antibot", "подозрительный трафик")


class TosOptInRequired(RuntimeError):
    """Raised when CianHttpProvider is used without acknowledging the risk."""


class CianBlocked(RuntimeError):
    """Raised as soon as cian.ru responds with anything other than a normal page."""


class CianHttpProvider:
    def __init__(
        self,
        rooms: Optional[List[int]] = None,
        max_pages: int = 5,
        enrich_built_year: bool = True,
        max_listings_to_enrich: int = 30,
        min_delay_seconds: float = 5.0,
        i_accept_tos_risk: bool = False,
        session: Optional[requests.Session] = None,
    ):
        if not i_accept_tos_risk:
            raise TosOptInRequired(
                "CianHttpProvider is disabled by default. cian.ru blocks automated "
                "traffic and its site rules reportedly require permission for "
                "automated data collection - read this module's docstring, then "
                "pass i_accept_tos_risk=True only if you understand and accept "
                "that for your own personal use. The recommended, reliable path "
                "is FileImportProvider with manually exported data."
            )
        self.rooms = rooms or [1, 2, 3, 4]
        self.max_pages = max_pages
        self.enrich_built_year = enrich_built_year
        self.max_listings_to_enrich = max_listings_to_enrich
        self.min_delay_seconds = max(min_delay_seconds, 3.0)
        self.session = session or requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT

    def fetch(self) -> List[Offer]:
        offers: List[Offer] = []
        for page in range(1, self.max_pages + 1):
            html = self._get(SEARCH_URL, params={
                "deal_type": "sale",
                "engine_version": 2,
                "offer_type": "flat",
                "region": 1,  # Moscow
                "room": self.rooms,
                "p": page,
            })
            page_offers = self._parse_search_page(html)
            if not page_offers:
                break
            offers.extend(page_offers)
            time.sleep(self.min_delay_seconds)

        if self.enrich_built_year:
            for offer in offers[: self.max_listings_to_enrich]:
                offer.built_year = self._fetch_built_year(offer.url)
                time.sleep(self.min_delay_seconds)

        return offers

    def _get(self, url: str, params: Optional[dict] = None) -> str:
        resp = self.session.get(url, params=params, timeout=20)
        if resp.status_code != 200:
            raise CianBlocked(f"cian.ru returned HTTP {resp.status_code} for {url} - likely blocked; see module docstring")
        lowered = resp.text.lower()
        if any(marker in lowered for marker in BLOCK_MARKERS):
            raise CianBlocked(f"cian.ru served an anti-bot page instead of content for {url}; see module docstring")
        return resp.text

    def _parse_search_page(self, html: str) -> List[Offer]:
        soup = bs4.BeautifulSoup(html, "html.parser")
        cards = soup.select("article[data-name='CardComponent']")
        return [o for o in (self._parse_card(c) for c in cards) if o is not None]

    def _parse_card(self, card: bs4.Tag) -> Optional[Offer]:
        link_area = card.select_one("div[data-name='LinkArea']")
        if link_area is None:
            return None
        a = link_area.select_one("a")
        if a is None or not a.get("href"):
            return None
        url = a["href"]
        url = url if url.startswith("http") else f"https://www.cian.ru{url}"
        offer_id = url.rstrip("/").split("/")[-1]

        price = self._parse_price(link_area)

        info_rows = link_area.select("div[data-name='GeneralInfoSectionRowComponent']")
        title = info_rows[0].get_text() if info_rows else ""

        area_total = None
        area_match = re.search(r"([\d.,]+)\s*м²", title)
        if area_match:
            area_total = to_float(area_match.group(1))

        is_studio = "Студия" in title
        rooms = 1 if is_studio else None
        rooms_match = re.search(r"(\d+)-комн", title)
        if rooms_match:
            rooms = int(rooms_match.group(1))

        floor = floors_total = None
        floor_match = re.search(r"(\d+)/(\d+)\s*этаж", title)
        if floor_match:
            floor, floors_total = int(floor_match.group(1)), int(floor_match.group(2))

        residential_complex = None
        jk_match = re.search(r"ЖК\s*«([^»]+)»", card.get_text())
        if jk_match:
            residential_complex = jk_match.group(1)

        if price is None or area_total is None or rooms is None:
            return None

        return Offer(
            id=offer_id,
            url=url,
            city="Москва",
            price=price,
            area_total=area_total,
            rooms=rooms,
            is_studio=is_studio,
            floor=floor,
            floors_total=floors_total,
            residential_complex=residential_complex,
            source="cian.ru",
            raw={"card_text": card.get_text()[:2000]},
        )

    @staticmethod
    def _parse_price(link_area: bs4.Tag) -> Optional[float]:
        for span in link_area.select("span[data-mark='MainPrice']"):
            text = span.get_text()
            if "₽" in text and "млн" not in text and "мес" not in text:
                digits = re.sub(r"[^\d]", "", text.split("₽")[0])
                if digits:
                    return float(digits)
        return None

    def _fetch_built_year(self, url: str) -> Optional[int]:
        html = self._get(url)
        soup = bs4.BeautifulSoup(html, "html.parser")
        spans = soup.select("span")
        for i, span in enumerate(spans[:-1]):
            text = span.get_text()
            if "Год постройки" in text or "Год сдачи" in text:
                year_match = re.search(r"(19|20)\d{2}", spans[i + 1].get_text())
                if year_match:
                    return int(year_match.group())
        return None
