"""Локация: конкурирующие проекты и бюджет въезда.

Центральная идея модуля, без которой аналитика по премиум-лоту неверна:

    покупатель сравнивает не цены лотов, а БЮДЖЕТЫ ВЪЕЗДА.

Лот в бетоне за 57 млн и готовая квартира за 63 млн — это не «дешевле на 6 млн».
Это 68,9 млн плюс год ремонта против 63 млн и ключи сегодня. Продавец видит первое
число и считает цену умеренной, покупатель видит второе и считает её высокой; в этом
разрыве сделка и стоит.

Поэтому всё сравнение здесь идёт по метру ГОТОВОЙ квартиры: к цене лота без отделки
добавляется стоимость доводки, и только после этого проекты сопоставимы между собой.
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from statistics import median

from .models import Comp, Finish

log = logging.getLogger(__name__)

ROOT = Path(__file__).resolve().parent.parent

# Стоимость доводки до состояния «въезжай», ₽/м². Ориентир бизнес-класса: 150 тыс.
# Именно эта величина превращает цену лота в бюджет въезда и делает проекты
# сопоставимыми. Для премиума её нужно поднимать — вынесено в конфиг проекта.
FINISHING_COST: dict[Finish, int] = {
    Finish.NONE: 150_000,
    Finish.WHITEBOX: 150_000,
    Finish.DEVELOPER: 0,
    Finish.DESIGNER: 0,
    Finish.DELUXE: 0,
}


@dataclass
class Project:
    """Проект локации: то, чего нет в выгрузке и что заводится руками."""

    name: str
    klass: str = ""                 # бизнес / премиум / делюкс
    developer: str = ""
    delivery: str = ""              # «сдан в 2024», «ключи в I кв. 2027»
    ready: bool = True              # ключи на руках или ещё стройка
    location: str = ""             # район сопоставления: Академический, Донской…
    metro: str = ""
    metro_minutes: int | None = None
    finishing_cost: int | None = None   # переопределение стоимости доводки
    amenities: dict[str, str] = field(default_factory=dict)
    note: str = ""

    def cost_to_finish(self, finish: Finish) -> int:
        base = FINISHING_COST[finish]
        if base and self.finishing_cost is not None:
            return self.finishing_cost
        return base

    @property
    def amenity_score(self) -> int:
        return sum(1 for v in self.amenities.values() if v and v != "—")


@dataclass
class ProjectStats:
    """Статистика проекта по выгрузке — то, что считается из данных."""

    project: Project
    lots: int
    area_min: float
    area_max: float
    price_min: int
    price_max: int
    avg_ppsm: float            # средневзвешенная по площади
    move_in_ppsm: float        # метр ГОТОВОЙ квартиры
    with_finish: int           # сколько лотов уже с ремонтом
    median_exposure: float | None

    @property
    def finish_share(self) -> float:
        return self.with_finish / self.lots if self.lots else 0.0

    @property
    def basis(self) -> str:
        """Что взято в расчёт — строка для отчёта."""
        if self.with_finish == self.lots:
            return f"{self.lots} с ремонтом"
        if self.with_finish == 0:
            return f"{self.lots} без отделки"
        return f"{self.with_finish} из {self.lots} с ремонтом"


def load_projects(path: Path | str | None = None) -> dict[str, Project]:
    """Реестр проектов, где алиасы — полноправные ключи.

    Один и тот же ЖК в реестре продаж и в выгрузке Циан называется по-разному:
    «Level Академическая» латиницей против «Левел Академической» кириллицей.
    Никакая нормализация это не сведёт — нужен явный список синонимов.
    """
    path = Path(path or ROOT / "data" / "projects.json")
    if not path.exists():
        log.warning("Реестр проектов %s не найден — локация анализироваться не будет", path)
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    out: dict[str, Project] = {}
    for item in raw.get("projects", []):
        p = Project(
            name=item["name"],
            klass=item.get("class", ""),
            developer=item.get("developer", ""),
            delivery=item.get("delivery", ""),
            location=item.get("location", ""),
            ready=item.get("ready", True),
            metro=item.get("metro", ""),
            metro_minutes=item.get("metro_minutes"),
            finishing_cost=item.get("finishing_cost"),
            amenities=item.get("amenities", {}),
            note=item.get("note", ""),
        )
        for name in [p.name, *item.get("aliases", [])]:
            out[normalise(name)] = p
    return out


def normalise(name: str) -> str:
    """Единый нормализатор имён ЖК на весь проект.

    Раньше их было два — в импортёре и здесь, — и они расходились на пробелах:
    выгрузка индексировалась под одним ключом, а искалась под другим.
    """
    return re.sub(r"[^а-яa-z0-9]+", "", str(name).lower().replace("ё", "е"))


def canonical_key(name: str, projects: dict[str, Project]) -> str:
    """Приводит любое написание ЖК к ключу его проекта, если он известен."""
    key = normalise(name)
    project = projects.get(key)
    return normalise(project.name) if project else key


def weighted_ppsm(comps: list[Comp]) -> float:
    """Средневзвешенная по площади — так считает эталонный отчёт.

    Простое среднее ₽/м² перевешивает студии: их метр всегда дороже, и проект
    выглядел бы дороже, чем он есть для покупателя крупного лота.
    """
    total_area = sum(c.area for c in comps)
    return sum(c.price for c in comps) / total_area if total_area else 0.0


def project_stats(
    comps: list[Comp],
    project: Project,
    *,
    area_band: tuple[float, float] | None = None,
) -> ProjectStats | None:
    """Статистика проекта. area_band сужает выборку до сопоставимых по метражу."""
    lots = comps
    if area_band:
        lo, hi = area_band
        lots = [c for c in comps if lo <= c.area <= hi]
    if not lots:
        return None

    # Метр готовой квартиры: к каждому лоту добавляется своя доводка, и только
    # потом берётся среднее. Усреднять сначала цену, а потом добавлять одну общую
    # доводку — ошибка: в проекте с разной отделкой это даёт другое число.
    finished_total = sum(
        c.price + project.cost_to_finish(c.finish) * c.area for c in lots
    )
    area_total = sum(c.area for c in lots)
    doms = [c.days_on_market for c in lots if c.days_on_market is not None]

    return ProjectStats(
        project=project,
        lots=len(lots),
        area_min=min(c.area for c in lots),
        area_max=max(c.area for c in lots),
        price_min=min(c.price for c in lots),
        price_max=max(c.price for c in lots),
        avg_ppsm=weighted_ppsm(lots),
        move_in_ppsm=finished_total / area_total,
        with_finish=sum(1 for c in lots if project.cost_to_finish(c.finish) == 0),
        median_exposure=median(doms) if doms else None,
    )


def move_in_budget(price: int, area: float, finish: Finish, project: Project | None) -> int:
    """Цена лота + доводка до «въезжай». То, чем покупатель реально оперирует."""
    cost = (project or Project(name="")).cost_to_finish(finish)
    return int(price + cost * area)
