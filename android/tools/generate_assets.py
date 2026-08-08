#!/usr/bin/env python3
"""Generates the drawn assets that are tedious (and error-prone) by hand:

  * the adaptive launcher icon, in colour and monochrome
  * the widget picker preview: a static XML replica of the real widget
  * a vector fallback preview for hosts below API 31

Run from the `android` directory:  python3 tools/generate_assets.py
"""

import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")

INK = "#FF14130F"
PAPER = "#FFF6F5F1"
CINNABAR = "#FFC0402B"


def circle(cx, cy, r):
    return f"M{cx:.2f},{cy - r:.2f}a{r:.2f},{r:.2f} 0 1,0 0.01,0z"


def rounded_bar(x, y, w, h):
    r = h / 2.0
    return (
        f"M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}"
        f"a{r:.2f},{r:.2f} 0 0,1 0,{h:.2f}"
        f"h{-(w - 2 * r):.2f}"
        f"a{r:.2f},{r:.2f} 0 0,1 0,{-h:.2f}z"
    )


# ---------------------------------------------------------------- launcher

# Three week rules and one marked day — the app reduced to its two moves.
# Everything stays inside the 66dp adaptive-icon safe circle: the far corner of
# the top rule sits at r=30 from centre, the disc reaches 23.5.
RULE_YS = (36.0, 54.0, 72.0)
RULE_X0, RULE_X1 = 30.0, 78.0
RULE_H = 3.6
DISC_CX, DISC_CY, DISC_R = 63.0, 54.0, 14.5


def launcher_paths():
    rules = "".join(
        rounded_bar(RULE_X0, y - RULE_H / 2.0, RULE_X1 - RULE_X0, RULE_H) for y in RULE_YS
    )
    disc = circle(DISC_CX, DISC_CY, DISC_R)
    return rules, disc


def write_launcher():
    rules, disc = launcher_paths()

    def vector(body):
        return (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n' + body + "</vector>\n"
        )

    def path(d, colour):
        return f'    <path\n        android:pathData="{d}"\n        android:fillColor="{colour}" />\n'

    write(
        os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
        vector(path(rules, PAPER) + path(disc, CINNABAR)),
    )
    # Themed icons get one colour, so the disc has to stay a disc: the rules are
    # drawn, then the disc is punched back out and re-drawn a size smaller.
    mono_ring = disc + circle(DISC_CX, DISC_CY, DISC_R - 3.4)
    write(
        os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
        vector(
            '    <path\n'
            f'        android:pathData="{rules}"\n'
            '        android:fillColor="#FFFFFFFF" />\n'
            '    <path\n'
            f'        android:pathData="{mono_ring}"\n'
            '        android:fillType="evenOdd"\n'
            '        android:fillColor="#FFFFFFFF" />\n'
        ),
    )


# ------------------------------------------------------------ widget preview

PREVIEW_ROWS = [
    [(str(d), "in") for d in range(1, 8)],
    [(str(d), "in") for d in range(8, 15)],
    [(str(d), "in") for d in range(15, 22)],
    [(str(d), "in") for d in range(22, 29)],
    [("29", "in"), ("30", "in")] + [(str(d), "out") for d in range(1, 6)],
    [(str(d), "out") for d in range(6, 13)],
]
PREVIEW_TODAY = (1, 3)  # row, column
PREVIEW_DOTS = {(0, 1), (0, 4), (1, 0), (1, 5), (2, 2), (2, 3), (3, 1), (3, 6), (4, 0)}
WEEKDAYS = ["M", "T", "W", "T", "F", "S", "S"]


def preview_layout():
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:layout_width="match_parent"',
        '    android:layout_height="match_parent">',
        "",
        "    <ImageView",
        '        android:layout_width="match_parent"',
        '        android:layout_height="match_parent"',
        '        android:importantForAccessibility="no"',
        '        android:scaleType="fitXY"',
        '        android:src="@drawable/preview_surface" />',
        "",
        "    <ImageView",
        '        android:layout_width="match_parent"',
        '        android:layout_height="match_parent"',
        '        android:importantForAccessibility="no"',
        '        android:scaleType="fitXY"',
        '        android:src="@drawable/preview_border" />',
        "",
        "    <LinearLayout",
        '        android:layout_width="match_parent"',
        '        android:layout_height="match_parent"',
        '        android:orientation="vertical"',
        '        android:padding="14dp">',
        "",
        "        <LinearLayout",
        '            android:layout_width="match_parent"',
        '            android:layout_height="wrap_content"',
        '            android:gravity="center_vertical"',
        '            android:orientation="horizontal">',
        "",
        "            <TextView",
        '                android:layout_width="wrap_content"',
        '                android:layout_height="wrap_content"',
        '                android:fontFamily="sans-serif-medium"',
        '                android:letterSpacing="-0.02"',
        '                android:text="September"',
        '                android:textColor="@color/pv_ink"',
        '                android:textSize="15sp" />',
        "",
        "            <TextView",
        '                android:layout_width="wrap_content"',
        '                android:layout_height="wrap_content"',
        '                android:layout_marginStart="5dp"',
        '                android:letterSpacing="-0.02"',
        '                android:text="2025"',
        '                android:textColor="@color/pv_faint"',
        '                android:textSize="15sp" />',
        "        </LinearLayout>",
        "",
        "        <LinearLayout",
        '            android:layout_width="match_parent"',
        '            android:layout_height="wrap_content"',
        '            android:layout_marginTop="9dp"',
        '            android:orientation="horizontal">',
    ]
    for label in WEEKDAYS:
        out += [
            "",
            "            <TextView",
            '                android:layout_width="0dp"',
            '                android:layout_height="wrap_content"',
            '                android:layout_weight="1"',
            '                android:fontFamily="sans-serif-medium"',
            '                android:gravity="center"',
            '                android:letterSpacing="0.14"',
            f'                android:text="{label}"',
            '                android:textColor="@color/pv_ghost"',
            '                android:textSize="9sp" />',
        ]
    out += [
        "        </LinearLayout>",
        "",
        "        <FrameLayout",
        '            android:layout_width="match_parent"',
        '            android:layout_height="1dp"',
        '            android:layout_marginTop="6dp"',
        '            android:background="@color/pv_rule" />',
        "",
        "        <LinearLayout",
        '            android:layout_width="match_parent"',
        '            android:layout_height="0dp"',
        '            android:layout_weight="1"',
        '            android:orientation="vertical">',
    ]
    for r, row in enumerate(PREVIEW_ROWS):
        out += [
            "",
            "            <LinearLayout",
            '                android:layout_width="match_parent"',
            '                android:layout_height="0dp"',
            '                android:layout_weight="1"',
            '                android:gravity="center_vertical"',
            '                android:orientation="horizontal">',
        ]
        for c, (label, kind) in enumerate(row):
            today = (r, c) == PREVIEW_TODAY
            colour = "@color/pv_on_accent" if today else (
                "@color/pv_ink" if kind == "in" else "@color/pv_ghost"
            )
            dot = (r, c) in PREVIEW_DOTS and not today
            cell = [
                "",
                "                <LinearLayout",
                '                    android:layout_width="0dp"',
                '                    android:layout_height="match_parent"',
                '                    android:layout_weight="1"',
                '                    android:gravity="center"',
                '                    android:orientation="vertical">',
                "",
                "                    <TextView",
                '                        android:layout_width="19dp"',
                '                        android:layout_height="19dp"',
            ]
            if today:
                cell.append('                        android:background="@drawable/preview_today"')
            cell += [
                '                        android:gravity="center"',
                f'                        android:text="{label}"',
                f'                        android:textColor="{colour}"',
                '                        android:textSize="11sp" />',
                "",
                "                    <ImageView",
                '                        android:layout_width="3dp"',
                '                        android:layout_height="3dp"',
                '                        android:layout_marginTop="1dp"',
                '                        android:importantForAccessibility="no"',
                '                        android:src="@drawable/preview_dot"',
                f'                        android:visibility="{"visible" if dot else "invisible"}" />',
                "                </LinearLayout>",
            ]
            out += cell
        out += ["            </LinearLayout>"]
    out += [
        "        </LinearLayout>",
        "    </LinearLayout>",
        "</FrameLayout>",
        "",
    ]
    return "\n".join(line for line in out if line is not None)


# ------------------------------------------------- vector preview (pre-31)

def preview_vector():
    w, h = 200.0, 190.0
    pad = 16.0
    body = []
    body.append(
        f'    <path\n        android:pathData="M24,0h152a24,24 0 0,1 24,24v142'
        f'a24,24 0 0,1 -24,24h-152a24,24 0 0,1 -24,-24v-142a24,24 0 0,1 24,-24z"\n'
        f'        android:fillColor="{PAPER}" />\n'
    )
    body.append(f'    <path\n        android:pathData="{rounded_bar(pad, 22.0, 62.0, 7.0)}"\n'
                f'        android:fillColor="#FF14130F" />\n')
    body.append(f'    <path\n        android:pathData="{rounded_bar(pad + 66.0, 22.0, 26.0, 7.0)}"\n'
                f'        android:fillColor="#FF97948A" />\n')
    body.append(f'    <path\n        android:pathData="M{pad},44h{w - 2 * pad}v1h{-(w - 2 * pad)}z"\n'
                f'        android:fillColor="#3314130F" />\n')

    cols, rows = 7, 5
    cell_w = (w - 2 * pad) / cols
    cell_h = (h - 56.0 - pad) / rows
    dots, accents = [], []
    for j in range(rows):
        for i in range(cols):
            cx = pad + (i + 0.5) * cell_w
            cy = 56.0 + (j + 0.5) * cell_h
            if (i, j) == (3, 1):
                accents.append(circle(cx, cy, 8.0))
            else:
                dots.append(circle(cx, cy, 3.2))
    body.append(f'    <path\n        android:pathData="{"".join(accents)}"\n'
                f'        android:fillColor="{CINNABAR}" />\n')
    body.append(f'    <path\n        android:pathData="{"".join(dots)}"\n'
                f'        android:fillColor="#FF56544D" />\n')

    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{int(w)}dp"\n'
        f'    android:height="{int(h)}dp"\n'
        f'    android:viewportWidth="{w:g}"\n'
        f'    android:viewportHeight="{h:g}">\n' + "".join(body) + "</vector>\n"
    )


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote", os.path.relpath(path, RES))


if __name__ == "__main__":
    write_launcher()
    write(os.path.join(RES, "layout", "widget_preview.xml"), preview_layout())
    write(os.path.join(RES, "drawable", "widget_preview_image.xml"), preview_vector())
