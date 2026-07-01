import pytest

from mav.providers.file_import import FileImportProvider


def test_reads_json(tmp_path):
    path = tmp_path / "offers.json"
    path.write_text(
        """
        [{"id": "a1", "url": "https://example.test/a1", "city": "Москва",
          "price": 12000000, "area_total": 40, "rooms": 1,
          "residential_complex": "Символ", "built_year": 2019,
          "listed_at": "2026-06-01"}]
        """,
        encoding="utf-8",
    )
    offers = FileImportProvider(str(path)).fetch()
    assert len(offers) == 1
    o = offers[0]
    assert o.price == 12_000_000
    assert o.built_year == 2019
    assert o.listed_at.isoformat() == "2026-06-01"
    assert o.price_per_sqm == 300_000


def test_reads_csv(tmp_path):
    path = tmp_path / "offers.csv"
    path.write_text(
        "id,url,city,price,area_total,rooms,residential_complex,built_year\n"
        "a1,https://example.test/a1,Москва,12000000,40,1,Символ,2019\n",
        encoding="utf-8",
    )
    offers = FileImportProvider(str(path)).fetch()
    assert len(offers) == 1
    assert offers[0].built_year == 2019
    assert offers[0].rooms == 1


def test_missing_required_field_raises(tmp_path):
    path = tmp_path / "offers.json"
    path.write_text('[{"id": "a1", "url": "https://example.test/a1", "city": "Москва"}]', encoding="utf-8")
    with pytest.raises(ValueError, match="missing required field"):
        FileImportProvider(str(path)).fetch()


def test_unsupported_extension_raises(tmp_path):
    path = tmp_path / "offers.txt"
    path.write_text("not real data", encoding="utf-8")
    with pytest.raises(ValueError, match="unsupported file extension"):
        FileImportProvider(str(path)).fetch()
