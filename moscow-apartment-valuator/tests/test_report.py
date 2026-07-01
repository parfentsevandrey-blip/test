from mav.report import _floor


class _FakeOffer:
    def __init__(self, floor, floors_total):
        self.floor = floor
        self.floors_total = floors_total


def test_floor_zero_renders_as_a_real_floor_not_unknown():
    # floor=0 (e.g. a semi-basement unit) is a legitimate value, not a missing one -
    # a naive truthiness check would blank it out just like a real None would.
    assert _floor(_FakeOffer(floor=0, floors_total=9)) == "0/9"


def test_missing_floor_renders_as_unknown():
    assert _floor(_FakeOffer(floor=None, floors_total=None)) == "—"
