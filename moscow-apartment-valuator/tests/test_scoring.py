from mav.config import Config
from mav.models import Offer
from mav.valuation.scoring import evaluate


def make_offer(id, price, area_total=40, rooms=1, jk="ЖК Тест", floor=5, floors_total=20):
    return Offer(
        id=id, url=f"https://example.test/{id}", city="Москва",
        price=price, area_total=area_total, rooms=rooms,
        residential_complex=jk, built_year=2020, floor=floor, floors_total=floors_total,
    )


def _pool_around(median_ppsqm, area=40, n=8):
    return [make_offer(f"peer{i}", median_ppsqm * area, area) for i in range(n)]


def test_strong_undervalued():
    cfg = Config()
    pool = _pool_around(300_000)
    target = make_offer("t", 230_000 * 40)  # ~23% below median
    verdict = evaluate(target, [target, *pool], cfg)

    assert verdict.label == "strong_undervalued"
    assert verdict.discount_pct > 15
    assert verdict.comparables_count == 8


def test_moderately_undervalued():
    cfg = Config()
    pool = _pool_around(300_000)
    # ~11.7% raw discount, confidence 0.8 (8 peers) -> score ~9.3, above the
    # undervalued threshold (8) but below strong_undervalued (15).
    target = make_offer("t", 265_000 * 40)
    verdict = evaluate(target, [target, *pool], cfg)
    assert verdict.label == "undervalued"


def test_fair_price():
    cfg = Config()
    pool = _pool_around(300_000)
    target = make_offer("t", 300_000 * 40)
    verdict = evaluate(target, [target, *pool], cfg)
    assert verdict.label == "fair"
    assert abs(verdict.discount_pct) < 1


def test_overvalued():
    cfg = Config()
    pool = _pool_around(300_000)
    target = make_offer("t", 400_000 * 40)  # ~33% above median
    verdict = evaluate(target, [target, *pool], cfg)
    assert verdict.label == "overvalued"


def test_implausible_discount_is_flagged_suspicious_not_a_deal():
    cfg = Config()
    pool = _pool_around(300_000)
    target = make_offer("t", 140_000 * 40)  # ~53% below median
    verdict = evaluate(target, [target, *pool], cfg)

    assert verdict.label == "suspicious"
    assert verdict.discount_pct > cfg.scoring.max_plausible_discount_pct
    assert any("порог" in note for note in verdict.notes)


def test_insufficient_data_when_too_few_peers():
    cfg = Config()
    pool = _pool_around(300_000, n=2)
    target = make_offer("t", 200_000 * 40)
    verdict = evaluate(target, [target, *pool], cfg)

    assert verdict.label == "insufficient_data"
    assert verdict.comparables_count == 0


def test_confidence_scales_down_score_with_fewer_peers():
    cfg = Config()
    small_pool = _pool_around(300_000, n=4)
    large_pool = _pool_around(300_000, n=20)
    target_small = make_offer("t1", 250_000 * 40)
    target_large = make_offer("t2", 250_000 * 40)

    v_small = evaluate(target_small, [target_small, *small_pool], cfg)
    v_large = evaluate(target_large, [target_large, *large_pool], cfg)

    assert v_small.discount_pct == v_large.discount_pct
    assert v_small.confidence < v_large.confidence
    assert v_small.score < v_large.score
