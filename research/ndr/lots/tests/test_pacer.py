"""Offline test of lib/pacer.py (L49): gaps, breaks and the hourly cap on a fake clock."""
import importlib.util
import random
from pathlib import Path

spec = importlib.util.spec_from_file_location("pacer", Path(__file__).resolve().parent.parent / "lib" / "pacer.py")
P = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P)


def run(n, **kw):
    t = [0.0]
    p = P.Pacer(clock=lambda: t[0], sleep=lambda s: t.__setitem__(0, t[0] + s), rnd=random.Random(1), **kw)
    stamps = []
    for _ in range(n):
        p.wait()
        stamps.append(t[0])
        t[0] += 1.0                                          # the request itself takes a second
    return stamps


def test_gap_and_breaks():
    s = run(60)
    gaps = [b - a for a, b in zip(s, s[1:])]
    assert min(gaps) >= 25 - 1e-9, min(gaps)
    assert sum(1 for g in gaps if g >= 180) >= 2           # 60 requests -> at least two long breaks


def test_hour_cap():
    s = run(300, gap=(1, 2), break_every=(1000, 1000), hour_cap=80)
    for i in range(len(s)):
        in_hour = sum(1 for x in s[i:] if x < s[i] + 3600)
        assert in_hour <= 80, (i, in_hour)


if __name__ == "__main__":
    test_gap_and_breaks()
    test_hour_cap()
    print("ok test_gap_and_breaks, test_hour_cap")
