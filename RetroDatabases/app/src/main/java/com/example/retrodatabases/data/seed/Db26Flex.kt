package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1780 to 1790: a balloon's lift, a wheel's inertia and the weekly count of an eclipsing star's minima. */
val db26Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.FLEX,
        section = "Ballooning",
        title = "Montgolfier's balloon: lift from the temperature ratio",
        originalTitle = "Montgolfiere",
        author = "Joseph-Michel and Jacques-Etienne Montgolfier",
        year = 1783,
        summary = "Hot air inside a balloon is lighter than the air outside, so the lift per unit volume is the difference of the densities, and the program finds the volume that lifts a payload.",
        details = "The Montgolfier brothers flew a hot-air balloon at Annonay in 1783. At fixed pressure the density of air falls as its absolute temperature rises, so the example takes outside air at 273 kelvin and inside air at 373 kelvin as illustrative round values, and computes the lift per unit volume. It then finds the volume that lifts a payload of 1000 in the same units.",
        instructions = """
            1. Save the example as montgolfier.py.
            2. Run it: python3 montgolfier.py
        """,
        example = """
            from fractions import Fraction as F

            AMBIENT = 273   # absolute temperature of the outside air, in kelvin (an illustrative round value)
            HOT = 373       # absolute temperature of the air inside the envelope (an illustrative round value)

            # At fixed pressure the density of air is inversely proportional to its absolute temperature.
            density_hot = F(AMBIENT, HOT)          # density of the hot air, with the outside density taken as 1
            lift_per_volume = 1 - density_hot      # buoyancy per unit volume, in units of outside-air density

            print("lift per unit volume:", lift_per_volume)
            payload = 1000
            print("volume needed to lift a payload of 1000:", payload / lift_per_volume)
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.FLEX,
        section = "Mechanics",
        title = "Atwood's pulley with a solid wheel",
        originalTitle = "A Treatise on the Rectilinear Motion and Rotation of Bodies",
        author = "George Atwood",
        year = 1784,
        summary = "A solid wheel on the pulley adds half its mass to the inertia that must be accelerated, so the program compares the accelerations with and without it.",
        details = "Atwood's treatise of 1784 also treated the rotation of bodies, so the inertia of the pulley can be included. For a solid wheel of mass M the effective extra mass is M/2, and the example prints the acceleration for masses 3 and 1 with a massless pulley and with wheels of mass 4 and 8, in units of g.",
        instructions = """
            1. Save the example as atwood_wheel.py.
            2. Run it: python3 atwood_wheel.py
        """,
        example = """
            from fractions import Fraction as F

            def accel(m1, m2, wheel=0):
                # A solid wheel of mass M adds M/2 to the inertia that must be accelerated along with the masses.
                return F(m1 - m2) / (m1 + m2 + F(wheel, 2))

            for label, wheel in (("massless pulley", 0), ("solid wheel of mass 4", 4), ("solid wheel of mass 8", 8)):
                print(f"{label}: a = {accel(3, 1, wheel)} g")
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.FLEX,
        section = "Variable stars",
        title = "Goodricke's Algol minima week by week",
        originalTitle = "A Series of Observations on, and a Discovery of, the Period of the Variation of the Light of the Bright Star in the Head of Medusa",
        author = "John Goodricke",
        year = 1783,
        summary = "The number of predicted minima in each week is a floor count, which the program finds for the first three weeks from the period in minutes.",
        details = "The example uses the same rounded period of 2 days 20 hours 49 minutes as the Algol example, here in minutes against weeks of 10080 minutes. It counts the minima that fall in each of the first three weeks, which come to 2, 2 and 3.",
        instructions = """
            1. Save the example as algol_weeks.py.
            2. Run it: python3 algol_weeks.py
        """,
        example = """
            PERIOD = 2 * 24 * 60 + 20 * 60 + 49   # rounded period in minutes, as in the Algol example
            WEEK = 7 * 24 * 60                    # a week in minutes

            def minima_in_week(w):
                # Count the k >= 1 with (w - 1) WEEK < k PERIOD <= w WEEK.
                return sum(1 for k in range(1, 100) if (w - 1) * WEEK < k * PERIOD <= w * WEEK)

            counts = [minima_in_week(w) for w in (1, 2, 3)]
            print("minima in each of the first three weeks:", counts)
        """,
    ),
)
