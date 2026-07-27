"""Разбор сохранённой страницы объекта funda in business в черновой JSON.

funda закрыта Akamai Bot Manager (страница с reCAPTCHA), поэтому страницу
нельзя скачать скриптом: её нужно сохранить из браузера
(«Сохранить как» → *.html, либо скопировать outerHTML) в каталог raw/.
Дальше этот модуль вытаскивает из HTML всё, что нужно для отчёта:

    python generate_report.py parse raw/steenovenweg.html -o data/objects/draft.json

Полученный черновик остаётся дополнить русским текстом разделов.
"""

from __future__ import annotations

import json
import re
from html import unescape
from html.parser import HTMLParser
from pathlib import Path

MEDIA_RE = re.compile(r"https://cloud\.funda\.nl/[\w/]+_(?:\d+x\d+)\.(?:jpg|jpeg|png)")
META_RE = re.compile(
    r'<meta[^>]+(?:property|name)="([^"]+)"[^>]+content="([^"]*)"', re.I
)
PRICE_RE = re.compile(r"€\s?([\d.]+)")


class _TextExtractor(HTMLParser):
    """Собирает текст, сохраняя структуру списков определений и абзацев."""

    SKIP = {"script", "style", "noscript", "svg"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.chunks: list[str] = []
        self._skip_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self._skip_depth += 1
        # <dd>/<td> намеренно не переводят строку: значение должно остаться
        # на одной строке с подписью из <dt>/<th>, отделённое табуляцией
        elif tag in {"p", "br", "li", "dt", "div", "tr", "h1", "h2", "h3", "h4"}:
            self.chunks.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SKIP and self._skip_depth:
            self._skip_depth -= 1
        elif tag in {"dt", "th"}:
            self.chunks.append("\t")

    def handle_data(self, data):
        if not self._skip_depth and data.strip():
            self.chunks.append(re.sub(r"\s+", " ", data))

    @property
    def text(self) -> str:
        raw = "".join(self.chunks)
        lines = [re.sub(r"[ \t]+\t", "\t", ln).strip() for ln in raw.split("\n")]
        return "\n".join(ln for ln in lines if ln)


def photo_urls(html: str, size: str = "1440x960") -> list[str]:
    """Все фотографии объекта в нужном размере, в порядке появления, без дублей."""
    seen: dict[str, None] = {}
    for url in MEDIA_RE.findall(html):
        normalised = re.sub(r"_\d+x\d+\.", f"_{size}.", url)
        seen.setdefault(normalised, None)
    return list(seen)


def meta_tags(html: str) -> dict[str, str]:
    return {key.lower(): unescape(value) for key, value in META_RE.findall(html)}


def kenmerken(text: str) -> dict[str, str]:
    """Пары «подпись → значение» из таблицы характеристик (dt/dd, th/td)."""
    result: dict[str, str] = {}
    for line in text.split("\n"):
        if "\t" in line:
            label, _, value = line.partition("\t")
            label, value = label.strip(" :"), value.strip()
            if label and value and len(label) < 60:
                result.setdefault(label, value)
    return result


def description(text: str) -> list[str]:
    """Абзацы описания: от заголовка «Omschrijving»/«Description» до контактов."""
    lines = text.split("\n")
    start = next(
        (
            i
            for i, ln in enumerate(lines)
            if ln.strip().lower() in {"omschrijving", "description"}
        ),
        None,
    )
    if start is None:
        return []
    stop_words = ("kenmerken", "characteristics", "makelaar", "contact opnemen")
    out: list[str] = []
    for line in lines[start + 1 :]:
        if line.strip().lower() in stop_words:
            break
        out.append(line)
    return out


def parse(path: Path, url: str | None = None) -> dict:
    html = path.read_text(encoding="utf-8", errors="replace")
    extractor = _TextExtractor()
    extractor.feed(html)
    text = extractor.text
    meta = meta_tags(html)
    facts = kenmerken(text)

    price = ""
    for key in ("Vraagprijs", "Asking price", "Prijs"):
        if key in facts:
            price = facts[key]
            break

    return {
        "source_url": url or meta.get("og:url", ""),
        "source_title": meta.get("og:title", ""),
        "price_raw": price,
        "kenmerken": facts,
        "description_raw": description(text),
        "photos": photo_urls(html),
        "photo_count": len(photo_urls(html)),
    }


def parse_to_file(path: Path, dest: Path, url: str | None = None) -> Path:
    data = parse(path, url)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return dest
