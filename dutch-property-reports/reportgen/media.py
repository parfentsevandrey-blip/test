"""Загрузка и кэширование изображений объектов."""

from __future__ import annotations

import hashlib
import logging
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image

log = logging.getLogger(__name__)

UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
TIMEOUT = 40


def cache_path(url: str, cache_dir: Path) -> Path:
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    return cache_dir / f"{digest}.jpg"


def fetch(url: str, cache_dir: Path) -> Path:
    """Скачивает изображение (с кэшем на диске) и возвращает путь к файлу.

    Сайты брокеров отдают галереи в WebP, который python-docx вставить не
    умеет, поэтому всё приводится к JPEG сразу при загрузке.
    """
    cache_dir.mkdir(parents=True, exist_ok=True)
    dest = cache_path(url, cache_dir)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    log.info("загрузка изображения %s", url)
    resp = requests.get(url, headers={"User-Agent": UA}, timeout=TIMEOUT)
    resp.raise_for_status()
    with Image.open(BytesIO(resp.content)) as img:
        img.convert("RGB").save(dest, "JPEG", quality=92, subsampling=1)
    return dest


def fetch_all(urls: list[str], cache_dir: Path) -> list[Path]:
    paths: list[Path] = []
    for url in urls:
        try:
            paths.append(fetch(url, cache_dir))
        except Exception as exc:  # одна недоступная картинка не должна ронять отчёт
            log.warning("не удалось загрузить %s: %s", url, exc)
    return paths
