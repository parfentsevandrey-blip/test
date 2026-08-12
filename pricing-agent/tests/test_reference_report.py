"""Регрессия на эталонном отчёте аналитика (Левел Академическая, 79,5 м², 15 этаж).

Отчёт составлен человеком по реальной экспозиции локации и содержит числа, которые
движок обязан воспроизводить: метр готовой квартиры, паритет с соседями после
приведения к нашему этажу, бюджет въезда и итоговый коридор 53–55 млн ₽.

Тест намеренно проверяет ЧИСЛА, а не текст: формулировки будут меняться, а расчёт —
нет. Допуски заданы там, где эталон округлял (в отчёте «≈717 тыс ₽/м²»), и нулевые
там, где число точное.

    python3 -m unittest discover -s tests
"""

from __future__ import annotations

import unittest
from pathlib import Path

from agent.location import load_projects, move_in_budget
from agent.lotreport import build_report
from agent.models import Apartment, Finish
from agent.providers.cian_export import CianExportProvider

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "data" / "location_akademicheskaya"

# Лот из эталонного отчёта. В боевом реестре у него уже другая цена — здесь важна
# именно та, при которой отчёт составлялся, иначе сверять не с чем.
LOT = Apartment(
    id="reference-level-akademicheskaya",
    complex_name="Level Академическая",
    address="Профсоюзная ул., 3",
    rooms=3,
    area=79.5,
    floor=15,
    floors_total=19,
    price=57_000_000,
    finish=Finish.WHITEBOX,
    has_parking=False,
)


class ReferenceReportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.provider = CianExportProvider(FIXTURES, include_developer=False)
        cls.projects = load_projects()
        house = cls.provider.fetch_comps(LOT)
        location = {
            comps[0].complex_name: comps
            for key, comps in cls.provider.by_project().items()
            if comps and key != cls.provider._key(LOT.complex_name)
        }
        cls.report = build_report(
            LOT, house, location, cls.projects, price_list=cls.provider.house_lots(LOT)
        )

    def test_house_comps_loaded(self) -> None:
        """Без выгрузки по дому весь остальной расчёт бессмыслен."""
        self.assertGreaterEqual(len(self.report.peers), 6)
        self.assertTrue(self.report.has_house_data)

    def test_move_in_budget(self) -> None:
        """57,0 млн + доводка 150 тыс ₽/м² = 68,9 млн ₽ — центральное число отчёта."""
        self.assertEqual(self.report.finishing_cost, 150_000)
        self.assertAlmostEqual(self.report.move_in / 1e6, 68.9, delta=0.05)
        self.assertAlmostEqual(self.report.move_in_ppsm, 866_981, delta=1_000)

    def test_floor_premium_measured(self) -> None:
        """Надбавка за этаж измеряется по прайсу дома, а не берётся из конфига.

        Эталон: +0,78%/этаж при R²=0,59. Совпадение до десятых не требуется —
        важно, что регрессия применима и знак/порядок величины те же.
        """
        fp = self.report.floor_premium
        self.assertIsNotNone(fp)
        self.assertTrue(fp.measured, "регрессия не применилась — расчёт ушёл на конфиг")
        self.assertGreater(fp.rate, 0.002)
        self.assertLess(fp.rate, 0.015)

    def test_parity_with_neighbours(self) -> None:
        """Цены соседей, приведённые к 15-му этажу: эталон 717 290 ₽/м²."""
        self.assertAlmostEqual(self.report.parity_ppsm, 717_290, delta=2_000)

    def test_location_competitor_present(self) -> None:
        """Файв Тауэрс — прямой ценовой конкурент из отчёта.

        Он продаётся застройщиком, и фильтр «без лотов застройщика», уместный для
        коридора внутри дома, для анализа локации неверен: там застройщик и есть рынок.
        """
        names = {s.project.name for s in self.report.location}
        self.assertIn("Файв Тауэрс", names)

    def test_ready_alternative_from_report(self) -> None:
        """Новочеремушкинская 17: готовая квартира ~65 млн — то, что покупают вместо нас."""
        ready = [x for x in self.report.alternatives if x.ready]
        self.assertTrue(ready)
        budgets = [
            x.move_in for x in ready if x.project.name == "Новочеремушкинская 17"
        ]
        self.assertTrue(budgets, "конкурент из эталона не попал в альтернативы")
        self.assertLess(min(budgets), self.report.move_in)

    def test_recommended_corridor(self) -> None:
        """Итог отчёта: 53–55 млн ₽."""
        rec = self.report.recommendation
        self.assertGreaterEqual(rec.price, 53_000_000)
        self.assertLessEqual(rec.price, 55_000_000)
        self.assertAlmostEqual(rec.corridor[0] / 1e6, 53.0, delta=0.5)
        self.assertAlmostEqual(rec.corridor[1] / 1e6, 55.0, delta=0.5)

    def test_recommendation_is_explained(self) -> None:
        """Наружу уходит цена И причина. Рекомендация без обоснования — не рекомендация."""
        rec = self.report.recommendation
        self.assertTrue(rec.reasons)
        self.assertIn(rec.binding, {"цель", "дом", "пол", "шаг"})
        self.assertEqual(
            rec.move_in, move_in_budget(rec.price, LOT.area, LOT.finish, self.report.project)
        )


if __name__ == "__main__":
    unittest.main()
