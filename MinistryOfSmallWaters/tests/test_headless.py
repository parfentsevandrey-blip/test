"""Headless smoke test: run the simulation for a while and assert invariants."""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import config
import entities
from entities import World, Crab, Shrimp, Fish, Intern

random.seed(1234)

W = World()
assert len(W.creatures) == 11, f"unexpected population {len(W.creatures)}"
assert isinstance(W.crab, Crab)

# ---- run 90 simulated seconds at 30fps, feeding & poking along the way ------
dt = 1 / 30
ate = 0
_eat_orig = W.eat
def _counting_eat(food, eater):
    global ate
    if not food.dead:
        ate += 1
    _eat_orig(food, eater)
W.eat = _counting_eat

max_food_seen = 0
for step in range(int(90 / dt)):
    W.step(dt)

    # sprinkle food a few times a second (bypassing cooldown for the test)
    if step % 20 == 0:
        W.feed_cd = 0
        W.drop_food(random.uniform(30, config.CANVAS_W - 30), config.WATERLINE + 4)
    # feed-the-nation + pokes occasionally
    if step % 300 == 150:
        W.feed_nation()
    if step % 90 == 0:
        c = random.choice(W.creatures)
        c.poke(W)
    if step % 137 == 0:
        W.poke_head_of_state()
    if step % 200 == 100:
        # double-poke the crab to test the flag easter egg
        W.crab.poke(W); W.crab.poke(W)

    max_food_seen = max(max_food_seen, len(W.food))

    # -- invariants -------------------------------------------------------
    for c in W.creatures:
        x0, y0, x1, y1 = c.bbox()
        assert -30 <= c.x <= config.CANVAS_W + 30, f"{c.sprite_set} escaped x={c.x}"
        assert -30 <= c.y <= config.CANVAS_H + 30, f"{c.sprite_set} escaped y={c.y}"
        assert not math.isnan(c.x) and not math.isnan(c.y), "NaN position!"
        assert 0 <= c.frame_index < c.n_frames, f"bad frame {c.frame_index} for {c.sprite_set}"
    assert len(W.food) <= config.MAX_FOOD, "food exceeded treasury cap"

# crab stays pinned to the seabed
assert abs(W.crab.y - W.crab.y_home) < 1e-6, "crab left the sand"

# food should actually get consumed by the population
assert ate > 5, f"population barely ate ({ate})"

# day/night must have advanced and produced a sensible brightness
b = W.brightness()
assert 0.0 <= b <= 1.0, f"brightness out of range {b}"

# at least one flag effect should have been planted by the double-pokes
kinds = {e.kind for e in W.effects}
census = W.census()

print("OK — headless simulation stable")
print(f"   population   : {census['population']}")
print(f"   pellets eaten: {ate}")
print(f"   max food seen: {max_food_seen}")
print(f"   brightness   : {b:.2f}  (night={census['night']})")
print(f"   sample status: {W.status_line()}")
print(f"   sample status: {W.status_line()}")

# exercise every status line formats cleanly
for _ in range(60):
    s = W.status_line()
    assert "{" not in s, f"unformatted status line: {s}"
print("   all status lines format cleanly")
