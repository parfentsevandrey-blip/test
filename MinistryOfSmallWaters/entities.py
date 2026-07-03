"""
The living population of the Ministry: fish, the crowned crab, shrimp,
interns, plus pellets, bubbles and little effects.

This module is deliberately GUI-free -- it is pure simulation. The renderer
reads each critter's (sprite_set, frame_index, flip) plus its position and
draws it; the tray pokes and feeds through the `World` API. Keeping it headless
means the whole nation can be unit-tested without a display.
"""

from __future__ import annotations

import math
import random

from config import (
    CANVAS_W, CANVAS_H, SAND_H, WATERLINE, sized,
    FEED_COOLDOWN, FEED_BURST, MAX_FOOD, DAY_LENGTH,
    STATUS_LINES, MOODS,
)

SAND_TOP = CANVAS_H - SAND_H          # y of the sand surface
LEFT, RIGHT = 4, CANVAS_W - 4


def _clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v


# --------------------------------------------------------------------------- #
# base critter
# --------------------------------------------------------------------------- #

class Critter:
    sprite_set = "clownfish"
    kind = "fish"          # which SPRITE_BASE entry governs my size
    diet = "mid"           # "mid" fish eat sinking food; "floor" eat resting food; None
    n_frames = 3

    def __init__(self, x, y):
        self.x, self.y = float(x), float(y)
        self.vx = random.uniform(-20, 20)
        self.vy = 0.0
        self.facing = -1 if random.random() < 0.5 else 1
        self.anim = random.uniform(0, 3)
        self.startle = 0.0
        w, h = sized(self.kind)
        self.hw, self.hh = w / 2.0, h / 2.0
        self.frame_index = 0
        self.flip = self.facing < 0
        # double-poke tracking (the crab's flag easter egg uses this)
        self._poke_streak = 0
        self._poke_window = 0.0

    # -- geometry -----------------------------------------------------------
    def bbox(self):
        return (self.x - self.hw, self.y - self.hh, self.x + self.hw, self.y + self.hh)

    def contains(self, px, py, pad=8):
        x0, y0, x1, y1 = self.bbox()
        return (x0 - pad) <= px <= (x1 + pad) and (y0 - pad) <= py <= (y1 + pad)

    def _register_poke(self):
        """Return the running double/triple poke streak within a short window."""
        if self._poke_window > 0:
            self._poke_streak += 1
        else:
            self._poke_streak = 1
        self._poke_window = 0.8
        return self._poke_streak

    # -- to be overridden ---------------------------------------------------
    def poke(self, world):
        self.startle = 0.6
        ang = random.uniform(-0.4, 0.4)
        away = -1 if random.random() < 0.5 else 1
        spd = 260
        self.vx = away * spd * math.cos(ang)
        self.vy = spd * math.sin(ang) - 40
        world.spatter_bubbles(self.x, self.y - self.hh * 0.3, 3)

    def update(self, dt, world):
        raise NotImplementedError

    # -- helpers shared by swimmers ----------------------------------------
    def _swim_bounds(self, top, bottom):
        if self.x < LEFT + self.hw:
            self.x = LEFT + self.hw
            self.vx = abs(self.vx)
            self._des_vx = abs(getattr(self, "_des_vx", self.vx))
        elif self.x > RIGHT - self.hw:
            self.x = RIGHT - self.hw
            self.vx = -abs(self.vx)
            self._des_vx = -abs(getattr(self, "_des_vx", self.vx))
        if self.y < top:
            self.y = top
            self.vy = abs(self.vy) * 0.5
        elif self.y > bottom:
            self.y = bottom
            self.vy = -abs(self.vy) * 0.5


# --------------------------------------------------------------------------- #
# fish (the Citizen, the Bureaucrat, and friends)
# --------------------------------------------------------------------------- #

class Fish(Critter):
    def __init__(self, x, y, species="clownfish", cruise=55, chase=160,
                 interest=1.0, band=(WATERLINE + 10, None)):
        self.sprite_set = species
        self.kind = "cod" if species == "cod" else "fish"
        super().__init__(x, y)
        self.cruise = cruise
        self.chase = chase
        self.interest = interest        # 0..1 eagerness for food
        top, bottom = band
        self._top = top
        self._bottom = bottom if bottom is not None else SAND_TOP - self.hh - 4
        self._des_vx = random.choice([-1, 1]) * cruise * 0.7
        self._des_vy = 0.0
        self._wp_t = random.uniform(1.0, 3.0)
        self._bob = random.uniform(0, math.tau)

    def _new_waypoint(self):
        self._wp_t = random.uniform(1.8, 4.5)
        self._des_vx = random.choice([-1, 1]) * self.cruise * random.uniform(0.55, 1.0)
        self._des_vy = random.uniform(-0.3, 0.3) * self.cruise

    def update(self, dt, world):
        if self._poke_window > 0:
            self._poke_window -= dt
        slow = world.night_factor()

        if self.startle > 0:
            self.startle -= dt
            self.x += self.vx * dt
            self.y += self.vy * dt
            self.vx *= 0.90
            self.vy *= 0.90
            self.anim += dt * 14
        else:
            target = world.nearest_food_for(self)
            if target is not None and self.interest > 0:
                dx, dy = target.x - self.x, target.y - self.y
                d = math.hypot(dx, dy) + 1e-6
                acc = self.chase * self.interest
                self.vx += (dx / d) * acc * dt
                self.vy += (dy / d) * acc * dt
                cap = self.chase * slow
                sp = math.hypot(self.vx, self.vy)
                if sp > cap:
                    self.vx *= cap / sp
                    self.vy *= cap / sp
                if d < self.hw * 0.75:
                    world.eat(target, self)
            else:
                self._wp_t -= dt
                if self._wp_t <= 0:
                    self._new_waypoint()
                # ease toward desired cruise velocity + a gentle bob
                self._bob += dt * 2.0
                rate = min(1.0, dt * 1.5)
                self.vx += (self._des_vx * slow - self.vx) * rate
                self.vy += (self._des_vy * slow + math.sin(self._bob) * 8 - self.vy) * rate
            self.x += self.vx * dt
            self.y += self.vy * dt
            self.anim += dt * (2.0 + abs(self.vx) * 0.04)

        # edge handling within this fish's vertical band
        self._edge_avoid()
        self._swim_bounds(self._top, self._bottom)

        if abs(self.vx) > 5:
            self.facing = 1 if self.vx > 0 else -1
        self.frame_index = int(self.anim) % self.n_frames
        self.flip = self.facing < 0

    def _edge_avoid(self):
        m = 26
        if self.x < LEFT + self.hw + m:
            self._des_vx = abs(self._des_vx)
        elif self.x > RIGHT - self.hw - m:
            self._des_vx = -abs(self._des_vx)


# --------------------------------------------------------------------------- #
# interns (tiny schooling tetras)
# --------------------------------------------------------------------------- #

class Intern(Critter):
    sprite_set = "tetra"
    kind = "tetra"
    diet = None
    n_frames = 2

    def __init__(self, x, y):
        super().__init__(x, y)
        self._offset = (random.uniform(-40, 40), random.uniform(-24, 24))
        self._jitter = random.uniform(0, math.tau)

    def poke(self, world):
        # interns don't get poked directly; they scatter (handled in update)
        self.startle = 0.5
        self.vx = random.uniform(-180, 180)
        self.vy = random.uniform(-120, 0)

    def update(self, dt, world):
        slow = world.night_factor()
        cx, cy = world.school_center
        self._jitter += dt * 3.0
        tx = cx + self._offset[0] + math.cos(self._jitter) * 8
        ty = cy + self._offset[1] + math.sin(self._jitter * 1.3) * 6

        if self.startle > 0:
            self.startle -= dt
            self.vx *= 0.92
            self.vy *= 0.92
        else:
            dx, dy = tx - self.x, ty - self.y
            self.vx += dx * 2.4 * dt
            self.vy += dy * 2.4 * dt
            self.vx *= 0.90
            self.vy *= 0.90
            cap = 130 * slow
            sp = math.hypot(self.vx, self.vy)
            if sp > cap:
                self.vx *= cap / sp
                self.vy *= cap / sp

        self.x += self.vx * dt
        self.y += self.vy * dt
        self._swim_bounds(WATERLINE + 6, SAND_TOP - self.hh - 30)
        if abs(self.vx) > 4:
            self.facing = 1 if self.vx > 0 else -1
        self.anim += dt * 10
        self.frame_index = int(self.anim) % self.n_frames
        self.flip = self.facing < 0


# --------------------------------------------------------------------------- #
# shrimp (the twitchy Constituents)
# --------------------------------------------------------------------------- #

class Shrimp(Critter):
    sprite_set = "shrimp"
    kind = "shrimp"
    diet = "floor"
    n_frames = 2

    def __init__(self, x, y):
        super().__init__(x, y)
        self._hop_t = random.uniform(0.4, 1.8)
        self._flick_t = 0.0
        self._band_top = SAND_TOP - 96

    def poke(self, world):
        # classic shrimp tail-snap: flick sharply backward
        self.startle = 0.45
        self._flick_t = 0.3
        self.vx = -self.facing * random.uniform(150, 240)
        self.vy = -random.uniform(20, 70)
        world.spatter_bubbles(self.x, self.y, 2)

    def update(self, dt, world):
        self._flick_t = max(0.0, self._flick_t - dt)
        slow = world.night_factor()

        if self.startle > 0:
            self.startle -= dt
        else:
            target = world.nearest_food_for(self)
            if target is not None:
                dx, dy = target.x - self.x, target.y - self.y
                d = math.hypot(dx, dy) + 1e-6
                self.vx += (dx / d) * 120 * dt
                self.vy += (dy / d) * 90 * dt
                self.facing = 1 if dx > 0 else -1
                if d < self.hw * 0.8:
                    world.eat(target, self)
            else:
                self._hop_t -= dt
                if self._hop_t <= 0:
                    self._hop_t = random.uniform(0.7, 2.2) / slow
                    d = random.choice([-1, 1])
                    self.facing = d
                    self.vx = d * random.uniform(35, 80) * slow
                    self.vy = -random.uniform(18, 42)
                    self._flick_t = 0.25

        # buoyancy: shrimp gently sink between hops
        self.vy += 55 * dt
        self.vx *= 0.90
        self.vy *= 0.94
        self.x += self.vx * dt
        self.y += self.vy * dt
        self._swim_bounds(self._band_top, SAND_TOP - self.hh + 3)

        self.frame_index = 1 if (self._flick_t > 0 or self.startle > 0) else 0
        self.flip = self.facing < 0


# --------------------------------------------------------------------------- #
# the crab (Head of State)
# --------------------------------------------------------------------------- #

class Crab(Critter):
    sprite_set = "crab"
    kind = "crab"
    diet = "floor"
    n_frames = 2

    WALK, CEREMONY, SCUTTLE, SULK, PINCH = range(5)

    def __init__(self, x):
        y = SAND_TOP - sized("crab")[1] / 2 + 4
        super().__init__(x, y)
        self.y_home = y
        self.state = self.WALK
        self.dir = random.choice([-1, 1])
        self.timer = random.uniform(3, 7)
        self.walk_speed = 24

    def poke(self, world):
        streak = self._register_poke()
        world.spatter_bubbles(self.x, self.y - self.hh, 3)
        if streak >= 2:
            # double-poke the monarch and he plants a provisional flag
            world.plant_flag(self.x, self.y - self.hh - 6)
            self._poke_streak = 0
        # startle: scuttle away fast, then sulk
        self.dir = -1 if (world and self.x > CANVAS_W / 2) else 1
        self.state = self.SCUTTLE
        self.timer = 0.7

    def update(self, dt, world):
        if self._poke_window > 0:
            self._poke_window -= dt
        slow = world.night_factor()
        self.timer -= dt

        if self.state == self.SCUTTLE:
            self.x += self.dir * 130 * dt
            self.frame_index = 0
            if self.timer <= 0:
                self.state = self.SULK
                self.timer = random.uniform(1.2, 2.0)
        elif self.state == self.SULK:
            self.frame_index = 0            # claws lowered, brooding
            if self.timer <= 0:
                self.state = self.WALK
                self.timer = random.uniform(3, 7)
        elif self.state == self.CEREMONY:
            self.frame_index = 1            # claws raised in ceremony
            if self.timer <= 0:
                self.state = self.WALK
                self.timer = random.uniform(4, 9)
        elif self.state == self.PINCH:
            self.frame_index = 1
            target = world.nearest_food_for(self)
            if target is None:
                self.state = self.WALK
                self.timer = random.uniform(3, 7)
            else:
                d = target.x - self.x
                self.dir = 1 if d > 0 else -1
                self.x += self.dir * 40 * dt * slow
                if abs(d) < self.hw * 0.7:
                    world.eat(target, self)
                    self.state = self.WALK
                    self.timer = random.uniform(2, 5)
        else:  # WALK
            self.frame_index = 0
            self.x += self.dir * self.walk_speed * dt * slow
            # go pinch food that has settled on the sand
            target = world.nearest_food_for(self)
            if target is not None:
                self.state = self.PINCH
            elif self.timer <= 0:
                # occasionally pause for a ceremonial claw-raise
                self.state = self.CEREMONY
                self.timer = random.uniform(1.0, 1.8)

        # patrol the seabed, turning at the glass
        if self.x < LEFT + self.hw:
            self.x = LEFT + self.hw
            self.dir = 1
        elif self.x > RIGHT - self.hw:
            self.x = RIGHT - self.hw
            self.dir = -1
        self.y = self.y_home
        self.facing = 1                     # crab faces the viewer; no flip
        self.flip = False


# --------------------------------------------------------------------------- #
# pellets, bubbles, effects
# --------------------------------------------------------------------------- #

class Food:
    def __init__(self, x, y):
        self.x, self.y = float(x), float(y)
        self.vx = random.uniform(-8, 8)
        self.vy = 0.0
        self.rest = False
        self.life = 1e9
        self.dead = False

    def update(self, dt, world):
        if not self.rest:
            self.vy = min(self.vy + 120 * dt, 80)
            self.vx *= 0.97
            self.x += self.vx * dt
            self.y += self.vy * dt
            if self.y >= SAND_TOP - 2:
                self.y = SAND_TOP - 2
                self.rest = True
                self.life = 16.0            # crumbs dissolve if nobody eats them
        else:
            self.life -= dt
            if self.life <= 0:
                self.dead = True
        self.x = _clamp(self.x, LEFT, RIGHT)


class Bubble:
    def __init__(self, x, y, rise=None, size=None):
        self.x, self.y = float(x), float(y)
        self.rise = rise or random.uniform(24, 46)
        self.phase = random.uniform(0, math.tau)
        self.size = size or random.choice([5, 6, 7])
        self.dead = False

    def update(self, dt, world):
        self.phase += dt * 3.0
        self.y -= self.rise * dt
        self.x += math.sin(self.phase) * 10 * dt
        if self.y < WATERLINE - 2:
            self.dead = True


class Effect:
    """Short-lived flourish: a startle mark, an eat sparkle, or a planted flag."""
    def __init__(self, x, y, kind, life):
        self.x, self.y = float(x), float(y)
        self.kind = kind
        self.life = life
        self.max_life = life
        self.dead = False

    def update(self, dt, world):
        self.life -= dt
        if self.kind == "spark":
            self.y -= 26 * dt
        if self.life <= 0:
            self.dead = True


# --------------------------------------------------------------------------- #
# the World -- the small waters themselves
# --------------------------------------------------------------------------- #

class World:
    def __init__(self):
        self.creatures = []
        self.food = []
        self.bubbles = []
        self.effects = []
        self.time = 0.0
        self.day_t = DAY_LENGTH * 0.08        # start mid-morning
        self.feed_cd = 0.0
        self.vents = [(CANVAS_W * 0.18, SAND_TOP - 2), (CANVAS_W * 0.82, SAND_TOP - 2)]
        self._vent_t = 0.0
        self.school_center = (CANVAS_W * 0.5, CANVAS_H * 0.35)
        self._school_v = [random.uniform(-30, 30), random.uniform(-12, 12)]
        self._populate()

    # -- setup --------------------------------------------------------------
    def _populate(self):
        w, h = CANVAS_W, CANVAS_H
        self.crab = Crab(w * 0.5)
        self.creatures.append(self.crab)
        self.creatures.append(Fish(w * 0.3, h * 0.4, "clownfish", cruise=60, chase=175,
                                   interest=1.0, band=(WATERLINE + 12, SAND_TOP - 40)))
        self.creatures.append(Fish(w * 0.7, h * 0.3, "clownfish", cruise=58, chase=170,
                                   interest=1.0, band=(WATERLINE + 12, SAND_TOP - 40)))
        self.creatures.append(Fish(w * 0.55, h * 0.5, "bluetang", cruise=48, chase=150,
                                   interest=0.85, band=(WATERLINE + 20, SAND_TOP - 30)))
        self.creatures.append(Fish(w * 0.4, h * 0.62, "cod", cruise=20, chase=52,
                                   interest=0.35, band=(h * 0.45, SAND_TOP - 22)))
        for i in range(3):
            self.creatures.append(Shrimp(w * (0.3 + 0.2 * i), SAND_TOP - 24))
        for i in range(3):
            self.creatures.append(Intern(w * (0.4 + 0.08 * i), h * 0.3))

    # -- day / night --------------------------------------------------------
    def phase(self):
        return (self.day_t / DAY_LENGTH) % 1.0        # 0 = noon, 0.5 = midnight

    def brightness(self):
        # 1.0 at noon -> 0.0 at midnight
        return 0.5 * (math.cos(self.phase() * math.tau) + 1.0)

    def is_night(self):
        return self.brightness() < 0.35

    def night_factor(self):
        # movement speed multiplier: lively by day, drowsy at night
        return 0.45 + 0.55 * self.brightness()

    # -- simulation ---------------------------------------------------------
    def step(self, dt):
        dt = min(dt, 0.05)          # keep physics stable if a frame is slow
        self.time += dt
        self.day_t = (self.day_t + dt) % DAY_LENGTH
        self.feed_cd = max(0.0, self.feed_cd - dt)

        # wander the school's guiding point around the upper water
        sx, sy = self.school_center
        sx += self._school_v[0] * dt
        sy += self._school_v[1] * dt
        if sx < 60 or sx > CANVAS_W - 60:
            self._school_v[0] *= -1
            sx = _clamp(sx, 60, CANVAS_W - 60)
        if sy < WATERLINE + 30 or sy > CANVAS_H * 0.5:
            self._school_v[1] *= -1
            sy = _clamp(sy, WATERLINE + 30, CANVAS_H * 0.5)
        self.school_center = (sx, sy)

        # bubbling seabed vents
        self._vent_t -= dt
        if self._vent_t <= 0:
            self._vent_t = random.uniform(0.35, 0.9)
            vx, vy = random.choice(self.vents)
            self.bubbles.append(Bubble(vx + random.uniform(-6, 6), vy, size=random.choice([4, 5])))

        for c in self.creatures:
            c.update(dt, self)
        for f in self.food:
            f.update(dt, self)
        for b in self.bubbles:
            b.update(dt, self)
        for e in self.effects:
            e.update(dt, self)

        self.food = [f for f in self.food if not f.dead]
        self.bubbles = [b for b in self.bubbles if not b.dead]
        self.effects = [e for e in self.effects if not e.dead]

    # -- food ---------------------------------------------------------------
    def nearest_food_for(self, critter):
        if critter.diet is None or not self.food:
            return None
        want_rest = (critter.diet == "floor")
        best, best_d = None, 1e18
        radius = 150 if not want_rest else 220
        for f in self.food:
            if f.rest != want_rest:
                continue
            d = math.hypot(f.x - critter.x, f.y - critter.y)
            if d < best_d and d < radius:
                best, best_d = f, d
        return best

    def eat(self, food, eater):
        if food.dead:
            return
        food.dead = True
        self.effects.append(Effect(food.x, food.y, "spark", 0.4))

    def drop_food(self, x, y):
        if self.feed_cd > 0 or len(self.food) >= MAX_FOOD:
            return False
        self.food.append(Food(x, max(WATERLINE + 2, y)))
        self.feed_cd = FEED_COOLDOWN
        return True

    def feed_nation(self):
        released = 0
        for _ in range(FEED_BURST):
            if len(self.food) >= MAX_FOOD:
                break
            x = random.uniform(LEFT + 20, RIGHT - 20)
            self.food.append(Food(x, WATERLINE + random.uniform(2, 16)))
            released += 1
        return released

    # -- interactions -------------------------------------------------------
    def creature_at(self, x, y):
        for c in reversed(self.creatures):     # topmost draw order first
            if c.contains(x, y):
                return c
        return None

    def click(self, x, y):
        """A left-click: poke a creature, or drop food into open water."""
        c = self.creature_at(x, y)
        if c is not None:
            c.poke(self)
            self.effects.append(Effect(c.x, c.bbox()[1] - 4, "mark", 0.7))
            return ("poke", c)
        dropped = self.drop_food(x, y)
        return ("feed" if dropped else "nofeed", None)

    def poke_head_of_state(self):
        self.crab.poke(self)

    # -- effects ------------------------------------------------------------
    def spatter_bubbles(self, x, y, n):
        for _ in range(n):
            self.bubbles.append(Bubble(x + random.uniform(-8, 8), y,
                                       rise=random.uniform(40, 70), size=random.choice([4, 5])))

    def plant_flag(self, x, y):
        self.effects.append(Effect(x, y, "flag", 4.0))

    # -- status ticker ------------------------------------------------------
    def status_line(self):
        line = random.choice(STATUS_LINES)
        return line.format(
            food=len(self.food),
            pop=len(self.creatures),
            mood=random.choice(MOODS),
            curfew=("in effect" if self.is_night() else "lifted"),
        )

    def census(self):
        return {"population": len(self.creatures), "pellets": len(self.food),
                "night": self.is_night()}
