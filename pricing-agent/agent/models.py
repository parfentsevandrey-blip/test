"""Доменные модели: объект в продаже, аналог с рынка, вердикт по цене."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum
from typing import Optional


class Finish(str, Enum):
    """Тип отделки. Порядок важен: используется как порядковая шкала в поправках."""

    NONE = "без отделки"
    WHITEBOX = "white box"
    DEVELOPER = "от застройщика"
    DESIGNER = "дизайнерский ремонт"
    DELUXE = "deluxe / авторский интерьер"

    @property
    def tier(self) -> int:
        return list(Finish).index(self)


@dataclass
class Apartment:
    """Квартира из нашего реестра."""

    id: str
    complex_name: str          # ЖК
    address: str
    rooms: int
    area: float                # м²
    floor: int
    floors_total: int
    price: int                 # текущая цена экспозиции, ₽
    finish: Finish
    has_parking: bool          # машино-место входит в лот
    comment: str = ""
    video_url: str = ""
    presentation_url: str = ""
    # internal-id объявления в YRL-фиде Яндекс Недвижимости. Мы его сами и назначаем,
    # поэтому по умолчанию совпадает с id лота — именно по нему выгрузка звонков из
    # кабинета связывается с объектом реестра.
    yandex_internal_id: Optional[str] = None
    # Оперативные данные — заполняются из кабинета площадки/CRM.
    # Без них вердикт менее уверенный: не видно реакции рынка на нашу цену.
    listed_at: Optional[date] = None      # дата выхода в экспозицию
    views_7d: Optional[int] = None        # просмотры объявления за 7 дней
    calls_7d: Optional[int] = None        # звонки за 7 дней
    viewings_30d: Optional[int] = None    # показы за 30 дней
    last_price_change: Optional[date] = None
    price_history: list[tuple[str, int]] = field(default_factory=list)

    @property
    def feed_id(self) -> str:
        return self.yandex_internal_id or self.id

    @property
    def price_per_sqm(self) -> float:
        return self.price / self.area

    @property
    def floor_ratio(self) -> float:
        """Относительная высота этажа, 0..1. Нужна, чтобы сравнивать дома разной этажности."""
        if self.floors_total <= 1:
            return 0.5
        return (self.floor - 1) / (self.floors_total - 1)

    @property
    def days_on_market(self) -> Optional[int]:
        if self.listed_at is None:
            return None
        return (date.today() - self.listed_at).days

    @property
    def title(self) -> str:
        return f"{self.complex_name} · {self.rooms}к · {self.area:g} м² · {self.floor}/{self.floors_total}"


@dataclass
class Comp:
    """Аналог: конкурирующая квартира в экспозиции или закрытая сделка."""

    source: str                # cian | avito | domclick | egrn | internal
    external_id: str
    complex_name: str
    address: str
    rooms: int
    area: float
    floor: int
    floors_total: int
    price: int
    finish: Finish
    has_parking: bool
    same_complex: bool         # тот же ЖК, что у оцениваемого объекта
    distance_km: float = 0.0
    days_on_market: Optional[int] = None
    price_cut_pct: Optional[float] = None   # накопленное снижение цены с момента выхода
    is_closed_deal: bool = False            # True → цена сделки, а не экспозиции
    observed_at: Optional[date] = None
    url: str = ""

    @property
    def price_per_sqm(self) -> float:
        return self.price / self.area


@dataclass
class Adjustment:
    """Одна поправка к цене аналога. Хранится отдельно ради объяснимости вердикта."""

    name: str
    pct: float          # доля, например -0.043 = -4.3%
    explanation: str


@dataclass
class AdjustedComp:
    comp: Comp
    adjustments: list[Adjustment]
    adjusted_price_per_sqm: float
    weight: float

    @property
    def total_adjustment_pct(self) -> float:
        return self.adjusted_price_per_sqm / self.comp.price_per_sqm - 1


@dataclass
class OpsSnapshot:
    """Оперативные данные по объявлению: как рынок реагирует на нашу цену.

    Приходят из кабинета площадки (у Яндекс Недвижимости — выгрузка XLS) или из CRM.
    Это то, чего нет в xlsx-реестре и без чего вердикт опирается только на цены.
    """

    source: str
    listed_at: Optional[date] = None
    views_7d: Optional[int] = None
    calls_7d: Optional[int] = None
    calls_30d: Optional[int] = None
    viewings_30d: Optional[int] = None


@dataclass
class HouseValuation:
    """Независимая оценка ₽/м² по дому — контрольная точка для нашего коридора.

    У Яндекс Недвижимости это ML-калькулятор «Оценка квартиры»: он учитывает адрес,
    комнатность, этаж, площадь и состояние ремонта и обучен на многолетней базе
    объявлений. Наш коридор строится независимо, поэтому расхождение — сигнал
    проверить выборку аналогов, а не повод менять цену.
    """

    source: str
    price_per_sqm: float
    observed_at: Optional[date] = None
    note: str = ""


class Action(str, Enum):
    CUT = "снизить"
    HOLD = "держать"
    RAISE = "поднять"
    MANUAL = "требуется ручная оценка"


@dataclass
class Verdict:
    """Результат работы ядра. Всё, что дальше показывает бот, берётся отсюда."""

    apartment: Apartment
    action: Action
    recommended_price: int
    delta_pct: float                  # рекомендуемое изменение к текущей цене
    corridor: tuple[float, float, float]   # P25, P50, P75 скорректированных ₽/м²
    our_percentile: float             # позиция нашей цены в распределении аналогов, 0..100
    confidence: float                 # 0..1
    comps: list[AdjustedComp]
    signals: list[str]                # человекочитаемые факторы, повлиявшие на вердикт
    warnings: list[str]
    scenarios: list["Scenario"] = field(default_factory=list)
    baseline: Optional[HouseValuation] = None   # независимая оценка по дому, если есть

    @property
    def recommended_price_per_sqm(self) -> float:
        return self.recommended_price / self.apartment.area


@dataclass
class Scenario:
    """Сценарий «цена ↔ срок продажи»."""

    name: str
    price: int
    expected_days: int
    comment: str
