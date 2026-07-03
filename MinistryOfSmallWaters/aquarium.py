"""
The window onto the small waters: a Tkinter canvas that draws the pixel tank.

All heavy pixel work is done once at startup (every animation frame of every
creature is pre-scaled into a Tk PhotoImage and cached). Per tick we only move
canvas items and swap their cached image, which keeps a tankful of creatures
smooth at 30fps even on a modest machine.
"""

from __future__ import annotations

import math
import queue
import time
import tkinter as tk

from PIL import Image, ImageDraw, ImageTk

import pixelart
from seal import build_seal
from config import (CANVAS_W, CANVAS_H, WATERLINE, SCALE,
                    FRAME_MS, PALETTE, APP_NAME)
from entities import SAND_TOP


BUBBLE_SCALE = 2
FOOD_SCALE = 2
GRAD_BUCKETS = 24


def _hx(name):
    return PALETTE[name]


def _rgb(name):
    h = PALETTE[name].lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Aquarium:
    def __init__(self, world, on_quit=None):
        self.world = world
        self.on_quit = on_quit
        self.on_status = None       # optional callback(text) -> update tray tooltip
        self._alive = True
        self._last = time.perf_counter()
        self._status = world.status_line()
        self._status_t = 0.0
        self._grad_bucket = -1
        # commands from the tray thread are queued and run on the Tk thread
        self._cmd_q = queue.Queue()

        self.root = tk.Tk()
        self.root.title(APP_NAME)
        self.root.resizable(False, False)
        self.root.configure(bg=_hx("navy"))
        self._place_window()
        try:
            self._icon_photo = ImageTk.PhotoImage(self._seal_image(64))
            self.root.iconphoto(True, self._icon_photo)
        except Exception:
            pass

        self.canvas = tk.Canvas(self.root, width=CANVAS_W, height=CANVAS_H,
                                highlightthickness=0, bd=0, bg=_hx("navy"))
        self.canvas.pack()

        self._build_assets()
        self._build_static_items()
        self._build_creature_items()

        # dynamic item bookkeeping (object id -> canvas id)
        self._food_items = {}
        self._bubble_items = {}
        self._fx_items = []

        # HUD
        self._plaque = self.canvas.create_text(
            8, 7, anchor="nw", text=APP_NAME, fill=_hx("gold"),
            font=("Courier New", 10, "bold"))
        self._plaque_sh = self.canvas.create_text(
            9, 8, anchor="nw", text=APP_NAME, fill="#04101F",
            font=("Courier New", 10, "bold"))
        self.canvas.tag_lower(self._plaque_sh, self._plaque)
        self._build_sky()
        self._ticker = self.canvas.create_text(
            8, CANVAS_H - 6, anchor="sw", text=self._status, fill="#DCE8F0",
            font=("Courier New", 8))

        # input
        self.canvas.bind("<Button-1>", self._on_left)
        self.canvas.bind("<Button-3>", self._on_right)
        self.root.bind("<Key>", self._on_key)
        self.root.protocol("WM_DELETE_WINDOW", self.hide)

        self.root.after(FRAME_MS, self._tick)
        self.root.after(30, self._pump)

    # ------------------------------------------------------------------ #
    # window
    # ------------------------------------------------------------------ #
    def _place_window(self):
        self.root.update_idletasks()
        sw = self.root.winfo_screenwidth()
        sh = self.root.winfo_screenheight()
        x = max(0, sw - CANVAS_W - 24)
        y = max(0, sh - CANVAS_H - 72)      # sit just above the taskbar clock
        self.root.geometry(f"{CANVAS_W}x{CANVAS_H}+{x}+{y}")

    # ------------------------------------------------------------------ #
    # asset construction
    # ------------------------------------------------------------------ #
    def _photo(self, img, scale):
        return ImageTk.PhotoImage(pixelart.scale(img, scale))

    def _build_assets(self):
        # creature animation frames -> cached PhotoImages keyed (idx, flip)
        self.sprites = {}
        fish_species = ["clownfish", "bluetang", "cod", "goldfish", "guppy"]
        makers = {s: (lambda s=s: pixelart.build_fish_frames(s)) for s in fish_species}
        makers["shrimp"] = pixelart.make_shrimp
        makers["crab"] = lambda: pixelart.make_crab(crown=True)
        makers["tetra"] = pixelart.make_tetra
        for name, maker in makers.items():
            frames = maker()
            cache = {}
            for i, fr in enumerate(frames):
                cache[(i, False)] = self._photo(fr, SCALE)
                cache[(i, True)] = self._photo(pixelart.flip_h(fr), SCALE)
            self.sprites[name] = cache

        # pellets & bubbles
        self.food_img = self._photo(pixelart.make_food(6), FOOD_SCALE)
        self.bubble_imgs = {
            s: self._photo(pixelart.make_bubble(s), BUBBLE_SCALE) for s in (4, 5, 6, 7)
        }

        # swaying seaweed frames
        sway_seq = [-2, -1, 0, 1, 2, 1, 0, -1]
        self.seaweed_frames = [self._photo(pixelart.make_seaweed(sway=s), SCALE) for s in sway_seq]

        # day/night gradients
        self.gradients = [self._make_gradient(b / (GRAD_BUCKETS - 1)) for b in range(GRAD_BUCKETS)]

    def _make_gradient(self, brightness):
        """Vertical water gradient tinted by time of day (chunky pixel bands)."""
        day_top, day_bot = (58, 150, 205), (18, 78, 150)
        night_top, night_bot = (10, 26, 58), (4, 12, 30)
        top = _lerp(night_top, day_top, brightness)
        bot = _lerp(night_bot, day_bot, brightness)
        # amber dusk/dawn bump around the twilight band
        tw = max(0.0, 1.0 - abs(brightness - 0.33) / 0.22)
        if tw > 0:
            dusk = (200, 120, 60)
            top = _lerp(top, dusk, tw * 0.35)
            bot = _lerp(bot, (120, 70, 40), tw * 0.2)

        h = CANVAS_H // SCALE
        strip = Image.new("RGB", (2, h))
        px = strip.load()
        for row in range(h):
            t = row / (h - 1)
            col = _lerp(top, bot, t)
            px[0, row] = col
            px[1, row] = col
        strip = strip.resize((CANVAS_W, CANVAS_H), Image.NEAREST)
        return ImageTk.PhotoImage(strip)

    def _seabed_image(self):
        """Sand + coral + treasure chest as one composited RGBA layer."""
        img = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        sand, sanddk = _rgb("sand") + (255,), _rgb("sanddk") + (255,)
        d.rectangle([0, SAND_TOP, CANVAS_W, CANVAS_H], fill=sand)
        d.rectangle([0, SAND_TOP, CANVAS_W, SAND_TOP + SCALE], fill=sanddk)
        # pixel speckles on the seabed (deterministic pattern, no RNG needed)
        for gx in range(0, CANVAS_W, SCALE):
            for gy in range(SAND_TOP + 2 * SCALE, CANVAS_H, SCALE):
                if (gx * 7 + gy * 13) % 11 == 0:
                    d.rectangle([gx, gy, gx + SCALE - 1, gy + SCALE - 1], fill=sanddk)

        coral = pixelart.scale(pixelart.make_coral(), SCALE)
        img.alpha_composite(coral, (int(CANVAS_W * 0.06), SAND_TOP - coral.height + 6))
        coral2 = pixelart.scale(pixelart.make_coral(), SCALE)
        img.alpha_composite(coral2, (int(CANVAS_W * 0.80), SAND_TOP - coral2.height + 6))
        chest = pixelart.scale(pixelart.make_chest(), SCALE)
        img.alpha_composite(chest, (int(CANVAS_W * 0.44), SAND_TOP - chest.height + 8))
        return ImageTk.PhotoImage(img)

    def _build_static_items(self):
        self.bg_item = self.canvas.create_image(0, 0, anchor="nw", image=self.gradients[-1])
        # foam dashes at the waterline
        for fx in range(6, CANVAS_W, 22):
            self.canvas.create_line(fx, WATERLINE, fx + 10, WATERLINE,
                                    fill="#BFE4F5", width=2)
        self.seabed_photo = self._seabed_image()
        self.seabed_item = self.canvas.create_image(0, 0, anchor="nw", image=self.seabed_photo)

        # a few swaying seaweed clumps anchored to the sand
        self._weeds = []
        wf = self.seaweed_frames[0]
        for i, wx in enumerate((0.14, 0.30, 0.66, 0.90)):
            item = self.canvas.create_image(int(CANVAS_W * wx), SAND_TOP + 4,
                                            anchor="s", image=wf)
            self._weeds.append((item, i * 2))     # per-clump phase offset

    def _build_creature_items(self):
        self._crit_items = {}
        for c in self.world.creatures:
            photo = self.sprites[c.sprite_set][(0, c.flip)]
            self._crit_items[id(c)] = self.canvas.create_image(
                int(c.x), int(c.y), image=photo)

    def _build_sky(self):
        """A little drawn sun (day) and moon (night) in the top-right corner --
        drawn shapes rather than a unicode glyph so it looks the same anywhere."""
        sx, sy = CANVAS_W - 24, 22
        self._sun = []
        for a in range(0, 360, 45):
            rad = math.radians(a)
            self._sun.append(self.canvas.create_line(
                sx + math.cos(rad) * 9, sy + math.sin(rad) * 9,
                sx + math.cos(rad) * 13, sy + math.sin(rad) * 13,
                fill=_hx("gold"), width=2))
        self._sun.append(self.canvas.create_oval(
            sx - 7, sy - 7, sx + 7, sy + 7, fill=_hx("gold"), outline="#E7A419"))
        self._moon = [
            self.canvas.create_oval(sx - 7, sy - 7, sx + 7, sy + 7,
                                    fill="#F3EAC6", outline="#CBBE84"),
            self.canvas.create_oval(sx - 3, sy - 9, sx + 12, sy + 6,
                                    fill="#0A1A3C", outline=""),
        ]

    def _update_sky(self, night):
        for it in self._sun:
            self.canvas.itemconfig(it, state="hidden" if night else "normal")
            self.canvas.tag_raise(it)
        for it in self._moon:
            self.canvas.itemconfig(it, state="normal" if night else "hidden")
            self.canvas.tag_raise(it)

    # ------------------------------------------------------------------ #
    # per-frame update
    # ------------------------------------------------------------------ #
    def _tick(self):
        if not self._alive:
            return
        now = time.perf_counter()
        dt = now - self._last
        self._last = now

        self.world.step(dt)
        self._draw(dt)

        self.root.after(FRAME_MS, self._tick)

    def _draw(self, dt):
        w = self.world

        # background bucket by brightness
        b = int(round(w.brightness() * (GRAD_BUCKETS - 1)))
        if b != self._grad_bucket:
            self._grad_bucket = b
            self.canvas.itemconfig(self.bg_item, image=self.gradients[b])

        # seaweed sway
        for item, phase in self._weeds:
            fi = int((w.time * 3 + phase)) % len(self.seaweed_frames)
            self.canvas.itemconfig(item, image=self.seaweed_frames[fi])

        # creatures
        for c in w.creatures:
            item = self._crit_items[id(c)]
            photo = self.sprites[c.sprite_set].get((c.frame_index, c.flip))
            if photo is None:
                photo = self.sprites[c.sprite_set][(0, c.flip)]
            self.canvas.itemconfig(item, image=photo)
            self.canvas.coords(item, int(c.x), int(c.y))

        # food & bubbles via object-identity sync
        self._sync(w.food, self._food_items,
                   lambda o: self.canvas.create_image(int(o.x), int(o.y), image=self.food_img),
                   lambda o, it: self.canvas.coords(it, int(o.x), int(o.y)))
        self._sync(w.bubbles, self._bubble_items,
                   lambda o: self.canvas.create_image(int(o.x), int(o.y),
                                                      image=self.bubble_imgs[o.size]),
                   lambda o, it: self.canvas.coords(it, int(o.x), int(o.y)))

        # effects (few; redraw each frame)
        for it in self._fx_items:
            self.canvas.delete(it)
        self._fx_items = []
        for e in w.effects:
            self._draw_effect(e)

        # keep creatures above the sand/props visually
        self.canvas.tag_raise(self._plaque_sh)
        self.canvas.tag_raise(self._plaque)
        self.canvas.tag_raise(self._ticker)
        self._update_sky(w.is_night())

        # HUD text
        self._status_t += dt
        if self._status_t > 4.5:
            self._status_t = 0.0
            self._status = w.status_line()
            self.canvas.itemconfig(self._ticker, text=self._status)
            if self.on_status:
                try:
                    self.on_status(self._status)
                except Exception:
                    pass

    def _draw_effect(self, e):
        t = 1.0 - max(0.0, e.life / e.max_life)   # 0 -> 1 over lifetime
        if e.kind == "spark":
            r = 2 + t * 9
            self._fx_items.append(self.canvas.create_oval(
                e.x - r, e.y - r, e.x + r, e.y + r, outline=_hx("gold"), width=2))
        elif e.kind == "mark":
            self._fx_items.append(self.canvas.create_text(
                e.x, e.y - t * 10, text="!", fill=_hx("white"),
                font=("Courier New", 12, "bold")))
        elif e.kind == "flag":
            # provisional flag on a little pole, gently waving
            pole_h = 22
            wave = int(math.sin(e.life * 6) * 2)
            x0, y0 = e.x, e.y
            self._fx_items.append(self.canvas.create_line(
                x0, y0, x0, y0 - pole_h, fill=_hx("white"), width=2))
            self._fx_items.append(self.canvas.create_polygon(
                x0, y0 - pole_h, x0 + 16 + wave, y0 - pole_h + 4, x0, y0 - pole_h + 8,
                fill=_hx("red"), outline=_hx("gold")))

    def _sync(self, objs, item_map, factory, updater):
        seen = set()
        for o in objs:
            key = id(o)
            it = item_map.get(key)
            if it is None:
                it = factory(o)
                item_map[key] = it
            updater(o, it)
            seen.add(key)
        for key in list(item_map):
            if key not in seen:
                self.canvas.delete(item_map[key])
                del item_map[key]

    # ------------------------------------------------------------------ #
    # input handlers
    # ------------------------------------------------------------------ #
    def _on_left(self, ev):
        self.world.click(ev.x, ev.y)

    def _on_right(self, ev):
        self.world.feed_cd = 0.0
        self.world.drop_food(ev.x, ev.y)

    def _on_key(self, ev):
        k = ev.keysym.lower()
        if k == "f":
            self.world.feed_nation()
        elif k == "p":
            self.world.poke_head_of_state()
        elif k == "escape":
            self.hide()

    # ------------------------------------------------------------------ #
    # thread-safe controls (called from the tray thread)
    # ------------------------------------------------------------------ #
    def _pump(self):
        """Drain commands queued by the tray thread and run them on the Tk
        thread (Tkinter is not thread-safe, so every cross-thread action lands
        here)."""
        if not self._alive:
            return
        try:
            while True:
                fn = self._cmd_q.get_nowait()
                try:
                    fn()
                except Exception:
                    pass
        except queue.Empty:
            pass
        if self._alive:
            self.root.after(30, self._pump)

    def _do(self, fn):
        # safe to call from any thread: just enqueue for the Tk thread
        self._cmd_q.put(fn)

    def show(self):
        self._do(lambda: (self.root.deiconify(), self.root.lift(),
                          self.root.attributes("-topmost", True),
                          self.root.after(400, lambda: self.root.attributes("-topmost", False))))

    def hide(self):
        self._do(self.root.withdraw)

    def toggle_topmost(self, on):
        self._do(lambda: self.root.attributes("-topmost", bool(on)))

    def feed(self):
        self._do(self.world.feed_nation)

    def poke_crab(self):
        self._do(self.world.poke_head_of_state)

    def current_status(self):
        return self._status

    def show_about(self, text):
        def _a():
            from tkinter import messagebox
            messagebox.showinfo(APP_NAME, text, parent=self.root)
        self._do(_a)

    def quit(self):
        def _q():
            self._alive = False
            try:
                self.root.destroy()
            except Exception:
                pass
        self._do(_q)

    # ------------------------------------------------------------------ #
    # the official state seal (used for the window icon; tray builds its own)
    # ------------------------------------------------------------------ #
    def _seal_image(self, size=64):
        return build_seal(size)

    def run(self):
        self.root.mainloop()
