"""
The founding charter: every tunable constant, the official palette, and the
Ministry's branding live here so the rest of the nation can import one source
of truth.
"""

# --------------------------------------------------------------------------- #
# branding
# --------------------------------------------------------------------------- #

APP_NAME = "Ministry of Small Waters"
APP_SHORT = "Ministry"
TAGLINE = "A sovereign 32x32 nation. The crab is head of state. You are a guest."
VERSION = "1.0 — Founding Charter"
AUTHOR = "The Department of Fish Affairs"

# official state palette (also the tray-seal colours)
PALETTE = {
    "navy":   "#0B1E3A",   # the ministry's official field
    "red":    "#D63A2F",   # vermilion, the colour of the Head of State
    "gold":   "#F2C230",   # crowns and treasure
    "white":  "#F7F7F5",   # paper, eyes, foam
    "teal":   "#37B0A6",   # the civil service
    "ink":    "#2A2A2A",   # outlines
    "sand":   "#D9C48A",   # the seabed
    "sanddk": "#B8A066",
    "deep":   "#061428",   # midnight water
    "day":    "#1E86C7",   # daylight water
    "dusk":   "#C97A3C",   # amber evening water
}

# --------------------------------------------------------------------------- #
# geometry (display pixels)
# --------------------------------------------------------------------------- #

SCALE = 3               # nearest-neighbour upscale factor -> the pixel look
CANVAS_W = 462
CANVAS_H = 300
SAND_H = 38             # height of the sandy seabed strip
WATERLINE = 6           # a few px of foam at the very top
FPS = 30
FRAME_MS = int(1000 / FPS)

# base (pre-scale) sprite sizes, kept in lock-step with pixelart.py makers
SPRITE_BASE = {
    "fish":   (26, 17),
    "cod":    (26, 17),
    "shrimp": (22, 14),
    "crab":   (24, 16),
    "tetra":  (11, 7),
}


def sized(kind):
    """Return the on-screen (w, h) of a sprite kind after scaling."""
    w, h = SPRITE_BASE[kind]
    return w * SCALE, h * SCALE


# --------------------------------------------------------------------------- #
# day / night
# --------------------------------------------------------------------------- #

DAY_LENGTH = 210.0      # seconds for a full noon -> midnight -> noon cycle

# --------------------------------------------------------------------------- #
# feeding policy (the Ministry rations pellets)
# --------------------------------------------------------------------------- #

FEED_COOLDOWN = 0.18    # seconds between individual pellets (anti-spam decree)
FEED_BURST = 6          # pellets released by "Feed the Nation"
MAX_FOOD = 40           # the treasury can only sustain so many pellets

# --------------------------------------------------------------------------- #
# the deadpan "State of the Nation" ticker (tray tooltip / menu)
# --------------------------------------------------------------------------- #

STATUS_LINES = [
    "The Crab has convened parliament.",
    "Shrimp #2 has filed a formal grievance.",
    "Border of the tank: secure.",
    "The Bureaucrat is reviewing your feeding request.",
    "Interns deployed. Morale: bubbly.",
    "The Head of State declines to comment.",
    "GDP measured in pellets. Outlook: hungry.",
    "A citizen has been startled. An inquiry is pending.",
    "The treasury contains {food} pellet(s).",
    "Population holding steady at {pop} souls.",
    "Diplomatic relations with the ceiling remain frosty.",
    "The Crab has vetoed the seaweed's proposal.",
    "National mood: {mood}.",
    "The nightly curfew is {curfew}.",
    "All is calm in the small waters.",
    "The Constituents demand snacks.",
    "Motion to adjourn: carried, unanimously, by bubbles.",
]

MOODS = ["content", "peckish", "regal", "sleepy", "electric", "philosophical"]


def about_text():
    return (
        f"{APP_NAME}  —  v{VERSION}\n"
        f"{TAGLINE}\n\n"
        "A tiny pixel nation that lives in your system tray.\n"
        "  • Left-click a creature to POKE it (it startles).\n"
        "  • Left-click open water (or right-click) to DROP FOOD.\n"
        "  • The crab rules the seabed. Feed him and he may forgive you.\n\n"
        f"Issued by {AUTHOR}. No fish were employed in the making of this state."
    )
