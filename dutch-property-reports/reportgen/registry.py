"""Реестр объектов, которые уже уходили заказчику.

Задача одна: не показать объект дважды. Раньше проверка собиралась на лету —
перебором соседних отчётов плюс отдельный список «показано вне репозитория».
Этого мало: подборка, собранная в другой сессии, и объект, отправленный
письмом, в переборе не видны, а держать два источника правды неудобно.

Теперь источник один — data/registry.json. Он пополняется автоматически при
каждой успешной сборке и вручную для всего, что ушло заказчику мимо
генератора. Перед сборкой новая подборка сверяется с реестром, и повтор
печатается предупреждением.

Разные варианты вёрстки одной подборки повтором не считаются: они связаны
полем variant_of и записываются в один и тот же объект реестра.
"""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

REGISTRY = "registry.json"
NOTE = (
    "Реестр объектов, которые уже уходили заказчику. Пополняется автоматически "
    "при сборке отчёта; объекты, показанные мимо генератора, добавляйте руками "
    "с заполненным полем source. Сборка предупреждает, если объект из реестра "
    "попал в новую подборку."
)


def path(data_dir: Path) -> Path:
    return data_dir / REGISTRY


def load(data_dir: Path) -> dict:
    try:
        data = json.loads(path(data_dir).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"note": NOTE, "objects": []}
    data.setdefault("objects", [])
    return data


def save(data_dir: Path, data: dict) -> Path:
    data["note"] = NOTE
    data["updated"] = date.today().isoformat()
    data["objects"] = sorted(data["objects"], key=lambda entry: entry.get("slug", ""))
    destination = path(data_dir)
    destination.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return destination


def _listing_url(obj: dict) -> str:
    for _, value in obj.get("specs", []):
        if str(value).startswith("http"):
            return str(value)
    return ""


def entry_for(obj: dict) -> dict:
    return {
        "slug": obj.get("slug", ""),
        "title": obj.get("title", ""),
        "city": obj.get("city", ""),
        "price": obj.get("price_short", ""),
        "url": _listing_url(obj),
        "reports": [],
    }


def variants(report_path: Path) -> set[str]:
    """Имена отчётов, которые считаются одной и той же подборкой.

    Связь variant_of работает в обе стороны: «dossier» знает про исходный
    отчёт, а исходный про «dossier» — нет, поэтому каталог просматривается
    целиком.
    """
    names = {report_path.name}
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return names
    if report.get("variant_of"):
        names.add(report["variant_of"])
    for other in report_path.parent.glob("report-*.json"):
        try:
            data = json.loads(other.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if data.get("variant_of") in names:
            names.add(other.name)
    return names


def repeats(data_dir: Path, report_path: Path, objects: list[dict]) -> list[str]:
    """Объекты подборки, которые заказчик уже видел, — с указанием где."""
    known = variants(report_path)
    index = {entry.get("slug"): entry for entry in load(data_dir)["objects"]}
    found = []
    for obj in objects:
        entry = index.get(obj.get("slug"))
        if entry is None:
            continue
        elsewhere = [name for name in entry.get("reports", []) if name not in known]
        if elsewhere:
            found.append(f"{entry['slug']} — уже в отчёте {', '.join(elsewhere)}")
        elif not entry.get("reports"):
            found.append(f"{entry['slug']} — {entry.get('source', REGISTRY)}")
    return found


def _merge(data: dict, report_name: str, objects: list[dict]) -> dict:
    index = {entry.get("slug"): entry for entry in data["objects"]}
    for obj in objects:
        slug = obj.get("slug")
        if not slug:
            continue
        entry = index.get(slug)
        if entry is None:
            entry = entry_for(obj)
            data["objects"].append(entry)
            index[slug] = entry
        else:
            # карточка могла обновиться — цена и ссылка берутся из неё
            entry.update({key: value for key, value in entry_for(obj).items()
                          if key != "reports" and value})
        reports = entry.setdefault("reports", [])
        if report_name not in reports:
            reports.append(report_name)
            reports.sort()
    return data


def record(data_dir: Path, report_path: Path, objects: list[dict]) -> Path:
    """Записать объекты собранного отчёта в реестр."""
    return save(data_dir, _merge(load(data_dir), report_path.name, objects))


def _cards(data_dir: Path, report: dict) -> list[dict]:
    cards = []
    for ref in report.get("objects", []):
        if "file" not in ref:
            continue
        try:
            cards.append(json.loads(
                (data_dir / ref["file"]).read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError):
            continue
    return cards


def rebuild(data_dir: Path) -> tuple[Path, int]:
    """Пересобрать реестр по отчётам каталога, сохранив ручные записи.

    Нужна, когда реестр заводится на уже существующем архиве или когда
    отчёты правились в обход генератора.
    """
    data = load(data_dir)
    # ручные записи — те, что не привязаны ни к одному отчёту репозитория
    data["objects"] = [entry for entry in data["objects"] if not entry.get("reports")]
    for report_path in sorted(data_dir.glob("report-*.json")):
        try:
            report = json.loads(report_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        data = _merge(data, report_path.name, _cards(data_dir, report))
    return save(data_dir, data), len(data["objects"])
