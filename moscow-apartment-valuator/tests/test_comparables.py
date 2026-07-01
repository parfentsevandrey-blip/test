from mav.config import ComparablesConfig
from mav.models import Offer
from mav.valuation.comparables import find_comparables


def make_offer(id, price, area_total, rooms=1, jk="ЖК Тест", floor=5, floors_total=20, **kw):
    return Offer(
        id=id, url=f"https://example.test/{id}", city="Москва",
        price=price, area_total=area_total, rooms=rooms,
        residential_complex=jk, built_year=2020, floor=floor, floors_total=floors_total,
        **kw,
    )


def test_no_comparables_without_residential_complex():
    target = make_offer("t", 10_000_000, 40, jk=None)
    pool = [target, make_offer("a", 10_000_000, 40, jk=None)]
    assert find_comparables(target, pool, ComparablesConfig()) is None


def test_tier_a_matches_same_rooms_and_area_band():
    target = make_offer("t", 9_000_000, 40)
    peers = [make_offer(f"p{i}", 12_000_000, 40) for i in range(5)]
    off_area_peer = make_offer("far", 12_000_000, 90)  # outside +/-20% area band
    pool = [target, *peers, off_area_peer]

    result = find_comparables(target, pool, ComparablesConfig(area_tolerance_pct=20, min_comparables=4))

    assert result.tier == "A"
    assert len(result.peers) == 5
    assert off_area_peer not in result.peers


def test_falls_back_to_tier_b_when_tier_a_too_small():
    target = make_offer("t", 9_000_000, 40, rooms=1)
    two_room_peers = [make_offer(f"p{i}", 15_000_000, 60, rooms=2) for i in range(5)]
    one_room_peer = make_offer("only-one", 12_000_000, 40, rooms=1)
    pool = [target, one_room_peer, *two_room_peers]

    cfg = ComparablesConfig(min_comparables=4)
    result = find_comparables(target, pool, cfg)

    assert result.tier == "B"
    assert len(result.peers) == 6  # every other listing in the same ЖК, any room count


def test_insufficient_data_returns_none():
    target = make_offer("t", 9_000_000, 40)
    pool = [target, make_offer("p1", 10_000_000, 40)]  # only 1 peer, below min_comparables

    result = find_comparables(target, pool, ComparablesConfig(min_comparables=4))
    assert result is None


def test_floor_bucket_prefers_same_floor_position_when_enough_peers():
    target = make_offer("t", 9_000_000, 40, floor=1, floors_total=20)
    ground_peers = [make_offer(f"g{i}", 10_000_000, 40, floor=1, floors_total=20) for i in range(4)]
    mid_peers = [make_offer(f"m{i}", 14_000_000, 40, floor=10, floors_total=20) for i in range(4)]
    pool = [target, *ground_peers, *mid_peers]

    cfg = ComparablesConfig(min_comparables=4, floor_bucket=True)
    result = find_comparables(target, pool, cfg)

    assert result.tier == "A"
    assert len(result.peers) == 4
    assert all(p.floor == 1 for p in result.peers)


def test_median_and_mad_are_robust_to_a_single_outlier():
    target = make_offer("t", 9_000_000, 40)
    peers = [make_offer(f"p{i}", 12_000_000, 40) for i in range(5)]
    outlier = make_offer("outlier", 40_000_000, 40)  # a data-entry error among the peers
    pool = [target, *peers, outlier]

    result = find_comparables(target, pool, ComparablesConfig(min_comparables=4))

    # median should sit at the cluster of 12M/40sqm peers, not be dragged toward the outlier
    assert result.median_ppsqm == 12_000_000 / 40


def test_min_comparables_of_zero_is_clamped_to_one_instead_of_crashing():
    # min_comparables is user-editable via config.yaml; 0 would otherwise let an
    # empty peer list through and crash statistics.median() on an empty sequence.
    cfg = ComparablesConfig(min_comparables=0)
    assert cfg.min_comparables == 1

    # No peer at all in the same complex -> genuinely nothing to compare against.
    target = make_offer("t", 9_000_000, 40, jk="ЖК А")
    other_complex = make_offer("o", 20_000_000, 90, jk="ЖК Б")
    assert find_comparables(target, [target, other_complex], cfg) is None

    # Exactly one peer in the same complex (different room count, so tier A is
    # empty) - must fall back to tier B with that single peer, not crash.
    lone_peer = make_offer("p", 12_000_000, 60, rooms=3, jk="ЖК А")
    result = find_comparables(target, [target, lone_peer], cfg)
    assert result.tier == "B"
    assert len(result.peers) == 1


def test_finish_type_strict_prefers_matching_finish_level_when_enough_peers():
    target = make_offer("t", 9_000_000, 40, finish_type_raw="с отделкой")
    finished_peers = [make_offer(f"f{i}", 12_000_000, 40, finish_type_raw="с отделкой") for i in range(4)]
    shell_peers = [make_offer(f"s{i}", 8_000_000, 40, finish_type_raw="без отделки") for i in range(4)]
    pool = [target, *finished_peers, *shell_peers]

    cfg = ComparablesConfig(min_comparables=4, finish_type_strict=True)
    result = find_comparables(target, pool, cfg)

    assert result.tier == "A"
    assert len(result.peers) == 4
    assert all(p.finish_type == "finished" for p in result.peers)
