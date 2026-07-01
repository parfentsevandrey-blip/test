from datetime import date
from pathlib import Path

from mav.config import Config
from mav.models import Offer
from mav.pipeline import rank_offers
from mav.providers.file_import import FileImportProvider

SAMPLE = Path(__file__).resolve().parent.parent / "examples" / "sample_offers.json"


def _load_ranked(as_of=date(2026, 7, 1)):
    offers = FileImportProvider(str(SAMPLE)).fetch()
    return rank_offers(offers, Config(), as_of=as_of)


def test_sample_dataset_loads():
    offers = FileImportProvider(str(SAMPLE)).fetch()
    assert len(offers) == 15


def test_cheap_symvol_listing_is_flagged_undervalued():
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert "simvol-108-cheap" in ids
    assert ids["simvol-108-cheap"].verdict.label in {"strong_undervalued", "undervalued"}


def test_implausibly_cheap_listing_is_suspicious_not_a_silent_deal():
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert ids["simvol-109-toocheap"].verdict.label == "suspicious"


def test_expensive_listing_is_excluded_from_the_shortlist():
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert "simvol-110-expensive" not in ids


def test_pre_2019_complex_is_filtered_out_by_market_scope():
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert "oldjk-301" not in ids


def test_non_moscow_listing_is_filtered_out():
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert "spb-401" not in ids


def test_complex_with_too_few_peers_is_never_shortlisted():
    # "Люблинский парк" only has 3 listings, below the default min_comparables (4),
    # so even its cheapest listing must not surface as a "deal" - there isn't enough
    # data to back that claim.
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert "lp-202-cheap" not in ids


def test_strong_deal_outranks_a_suspicious_listing_even_with_a_smaller_raw_discount():
    # simvol-109-toocheap has a *bigger* raw discount than simvol-108-cheap, but it's
    # implausibly large and gets flagged "suspicious" rather than trusted as a real
    # deal - it must not out-rank a confirmed strong_undervalued listing just because
    # its number looks better.
    ranked = _load_ranked()
    ids = {r.offer.id: r for r in ranked}
    assert ids["simvol-108-cheap"].verdict.label == "strong_undervalued"
    assert ids["simvol-109-toocheap"].verdict.label == "suspicious"
    assert ranked.index(ids["simvol-108-cheap"]) < ranked.index(ids["simvol-109-toocheap"])


def test_freshness_breaks_ties_between_equally_scored_listings():
    def make_offer(id, price, listed_at, area_total=40):
        return Offer(
            id=id, url=f"https://example.test/{id}", city="Москва",
            price=price, area_total=area_total, rooms=1,
            residential_complex="ЖК Тест", built_year=2020,
            floor=5, floors_total=20, listed_at=listed_at,
        )

    peers = [make_offer(f"peer{i}", 300_000 * 40, date(2026, 6, 1)) for i in range(6)]
    fresh_deal = make_offer("fresh", 250_000 * 40, date(2026, 6, 29))
    stale_deal = make_offer("stale", 250_000 * 40, date(2026, 1, 1))

    ranked = rank_offers([fresh_deal, stale_deal, *peers], Config(), as_of=date(2026, 7, 1))

    assert [r.offer.id for r in ranked[:2]] == ["fresh", "stale"]
    assert ranked[0].verdict.label == ranked[1].verdict.label == "undervalued"
    assert ranked[0].verdict.score == ranked[1].verdict.score
