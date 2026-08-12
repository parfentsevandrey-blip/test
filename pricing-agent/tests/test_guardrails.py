"""Ограничители рекомендации: то, что агент не имеет права сделать.

Отчёт по лоту читается человеком и потому самопроверяем — ошибку в тексте видно.
Ограничители не видны: они срабатывают молча и именно поэтому ломаются незаметно.
Здесь проверяется не «красивая цена», а границы, за которые расчёт не выходит.

    python3 -m unittest discover -s tests
"""

from __future__ import annotations

import unittest
from statistics import median

from agent.location import Project
from agent.lotreport import MAX_CUT_STEP, build_report
from agent.models import Apartment, Comp, Finish

HOUSE = "Тестовый дом"
PROJECTS = {"тестовыйдом": Project(name=HOUSE, klass="бизнес", ready=True)}


def lot(price: int, *, area: float = 100.0, floor: int = 10) -> Apartment:
    return Apartment(
        id="test-lot",
        complex_name=HOUSE,
        address="Тестовая ул., 1",
        rooms=3,
        area=area,
        floor=floor,
        floors_total=20,
        price=price,
        finish=Finish.DEVELOPER,
        has_parking=False,
    )


def neighbours(top_ppsm: float, count: int = 8) -> list[Comp]:
    """Соседи на одном этаже с шагом −1% вниз от верхней цены.

    Разброс здесь не для реализма, а чтобы верхняя цена дома (ориентир паритета) и
    нижняя (пол) были разными числами. На плоском прайсе они совпадают, и тест
    перестаёт различать, какое из двух ограничений сработало.
    """
    return [
        Comp(
            source="test",
            external_id=f"n{i}",
            complex_name=HOUSE,
            address="Тестовая ул., 1",
            rooms=3,
            area=100.0,
            floor=10,
            floors_total=20,
            price=int(top_ppsm * (1 - 0.01 * i) * 100.0),
            finish=Finish.DEVELOPER,
            has_parking=False,
            same_complex=True,
        )
        for i in range(count)
    ]


class GuardrailTest(unittest.TestCase):
    def test_no_house_data_means_no_price(self) -> None:
        """Без выгрузки агент говорит «не знаю», а не придумывает цену."""
        r = build_report(lot(100_000_000), [], None, PROJECTS)
        self.assertEqual(r.recommendation.binding, "нет данных")
        self.assertEqual(r.recommendation.price, 100_000_000)
        self.assertEqual(r.recommendation.delta_pct, 0.0)
        self.assertLess(r.recommendation.confidence, 0.2)

    def test_cut_is_capped_by_step(self) -> None:
        """Даже при разрыве в 30% за один пересмотр снижаем не больше чем на 7%."""
        r = build_report(lot(130_000_000), neighbours(1_000_000), None, PROJECTS)
        self.assertGreaterEqual(
            r.recommendation.price, 130_000_000 * (1 - MAX_CUT_STEP) - 500_000
        )
        self.assertEqual(r.recommendation.binding, "шаг")

    def test_house_target_drives_cut(self) -> None:
        """Выпад из распределения цен дома — самостоятельное основание для снижения.

        Локации в этом тесте нет вовсе: если бы снижение зависело только от неё,
        лот дороже собственного дома молча оставался бы «держать».
        """
        r = build_report(lot(103_000_000), neighbours(1_000_000), None, PROJECTS)
        self.assertEqual(r.recommendation.binding, "дом")
        self.assertLess(r.recommendation.price, 103_000_000)
        # Цель — верхний квартиль дома (~98,25 млн), а не медиана (96,5 млн):
        # лот остаётся в верхней части дома, просто перестаёт быть выбросом.
        self.assertGreater(r.recommendation.price, 97_000_000)

    def test_house_target_is_p75_not_median(self) -> None:
        """Снижение до медианы означало бы «все лоты дома стоят одинаково».

        Разброс внутри дома — это вид, планировка и качество ремонта, которых в
        выгрузке нет. Стягивать к середине из-за того, что мы их не измеряем, —
        значит наказывать лот за отсутствие данных, а не за цену.
        """
        r = build_report(lot(103_000_000), neighbours(1_000_000), None, PROJECTS)
        house_median = median(p.adjusted_ppsm for p in r.peers) * 100.0
        self.assertGreater(r.recommendation.price, house_median)

    def test_never_below_house_floor(self) -> None:
        """Ниже самого дешёвого соседа, приведённого к нашему этажу, не опускаемся."""
        r = build_report(lot(103_000_000), neighbours(1_000_000), None, PROJECTS)
        cheapest = min(p.adjusted_ppsm for p in r.peers) * 100.0
        self.assertGreaterEqual(r.recommendation.price, cheapest - 500_000)

    def test_price_inside_house_range_is_held(self) -> None:
        """Лот внутри нормального разброса дома и без конкурентов цену не двигает."""
        r = build_report(lot(96_500_000), neighbours(1_000_000), None, PROJECTS)
        self.assertEqual(r.recommendation.price, 96_500_000)
        self.assertEqual(r.recommendation.binding, "нет ориентира")
        self.assertTrue(
            any("дешевле дома" in x or "корректна" in x for x in r.recommendation.reasons)
        )

    def test_recommendation_never_raises_price(self) -> None:
        """Рекомендация — про снижение или удержание. Поднять цену агент не предлагает.

        Не потому, что это невозможно, а потому, что данных для этого у него нет:
        спрос он видит только по своей экспозиции, и «поднять» на такой основе —
        совет вслепую.
        """
        for price in (80_000_000, 100_000_000, 130_000_000):
            r = build_report(lot(price), neighbours(1_000_000), None, PROJECTS)
            self.assertLessEqual(r.recommendation.price, price, f"цена выросла при {price}")

    def test_finishing_cost_enters_budget(self) -> None:
        """Без отделки бюджет въезда выше цены — иначе сравнение с готовыми враньё."""
        a = lot(100_000_000)
        a.finish = Finish.WHITEBOX
        r = build_report(a, neighbours(1_000_000), None, PROJECTS)
        self.assertEqual(r.finishing_cost, 150_000)
        self.assertEqual(r.move_in, 100_000_000 + 150_000 * 100)


class OwnLotsTest(unittest.TestCase):
    """Наши же лоты не должны попадать в собственную выборку аналогов."""

    def test_portfolio_lots_excluded(self) -> None:
        from agent.providers.cian_export import _is_same_lot

        ours = lot(100_000_000)
        same = neighbours(1_000_000, count=1)[0]  # 100 м², 10 этаж — тот же лот
        self.assertTrue(_is_same_lot(same, ours))

        other = neighbours(1_000_000, count=1)[0]
        other.floor = 11
        self.assertFalse(_is_same_lot(other, ours))


if __name__ == "__main__":
    unittest.main()
