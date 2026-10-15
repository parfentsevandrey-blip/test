package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1730 to 1740: a tank drained by Torricelli's law, a river profile, a table of degree lengths and a table of intervals. */
val db31Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.PROJECT,
        section = "Fluids",
        title = "Bernoulli's tank draining under Torricelli's law",
        originalTitle = "Hydrodynamica, sive de viribus et motibus fluidorum commentarii",
        author = "Daniel Bernoulli",
        year = 1738,
        summary = "A tank drains through a hole at a speed set by its water height, and the program prints the height at whole time steps until the tank is empty.",
        details = "Torricelli's law gives the outflow speed as the square root of twice gravity times the height, and Bernoulli's book treats outflow from vessels in detail. The example uses an illustrative gravity of 8, so that the square roots are whole numbers, and shows that the square root of the height falls in a straight line.",
        instructions = """
            1. Save the example as tank.py.
            2. Run it: python3 tank.py
        """,
        example = """
            from fractions import Fraction as F

            G = 8                       # illustrative value of g, chosen so that the square root of 2 g is the whole number 4
            AREA_RATIO = F(1, 2)        # outlet area divided by tank area
            root_start = 4              # square root of the starting height 16
            rate = AREA_RATIO * 4 / 2   # the square root of the height falls at this rate per unit of time

            for t in range(0, 5):
                root = root_start - rate * t
                height = root * root if root > 0 else 0
                print(f"height at t={t}: {height}")
            print("empties at t=" + str(F(root_start) / rate))
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.PROJECT,
        section = "Hydraulics",
        title = "Pitot's river profile of speeds",
        originalTitle = "Description de la machine pour mesurer la vitesse des eaux courantes et le sillage des vaisseaux",
        author = "Henri Pitot",
        year = 1732,
        summary = "Five tube readings across a river give five speeds, and their average is the mean speed of the section, which the program computes exactly.",
        details = "Pitot used his tube on the Seine in the early 1730s, and reading it at several points across a channel gives the speed profile. The example uses illustrative heights, each a perfect square in the example's units, and the speed rule with gravity 8, so that speed is 4 times the square root of the height.",
        instructions = """
            1. Save the example as river.py.
            2. Run it: python3 river.py
        """,
        example = """
            from fractions import Fraction as F
            from math import isqrt

            # Speed from a Pitot tube is the square root of 2 g h; with g = 8 this is 4 times the square root of h.
            heights = [4, 9, 16, 9, 4]   # column heights read at five points across the river, in the example's units
            speeds = [4 * isqrt(h) for h in heights]
            for h, v in zip(heights, speeds):
                print(f"height {h}: speed {v}")
            mean = F(sum(speeds), len(speeds))
            print("mean speed across the section: " + str(mean))
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.PROJECT,
        section = "Geodesy",
        title = "Maupertuis's degree table against latitude",
        originalTitle = "La figure de la terre",
        author = "Pierre Louis Moreau de Maupertuis",
        year = 1736,
        summary = "A table of degree lengths from the equator to the pole, built with the first-order rule for an Earth flattened by one part in 300, using illustrative values.",
        details = "Maupertuis's expedition of 1736 measured degrees at different latitudes, and his book of 1738 compared them with a flattened Earth. The example builds the table from an illustrative equatorial degree of 1000 units and a flattening of one part in 300, so that the polar degree is about one per cent longer.",
        instructions = """
            1. Save the example as degrees.py.
            2. Run it: python3 degrees.py
        """,
        example = """
            from fractions import Fraction as F

            EQUATOR = 1000        # illustrative length of a degree at the equator, in arbitrary units
            FLAT = F(1, 300)      # illustrative flattening, one part in 300

            # First order in the flattening: a degree at latitude phi is about EQUATOR times (1 + 3 f sin^2 phi) long.
            SIN2 = {0: F(0), 30: F(1, 4), 45: F(1, 2), 60: F(3, 4), 90: F(1)}
            for lat, s2 in SIN2.items():
                d = EQUATOR * (1 + 3 * FLAT * s2)
                print(f"latitude {lat:>2} degrees: {float(d):.2f}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.PROJECT,
        section = "Music theory",
        title = "Mattheson's just intervals as ratios",
        originalTitle = "Der vollkommene Capellmeister",
        author = "Johann Mattheson",
        year = 1739,
        summary = "The just intervals of tuning are frequency ratios, and the program checks that a fourth and a fifth make an octave while two major thirds fall short of one.",
        details = "Mattheson's manual of 1739 deals with the theory of intervals for the director of a musical group. The example lists the just intervals as exact fractions and checks two facts that tuning theory depends on.",
        instructions = """
            1. Save the example as intervals.py.
            2. Run it: python3 intervals.py
        """,
        example = """
            from fractions import Fraction as F

            intervals = {"fifth": F(3, 2), "fourth": F(4, 3), "major third": F(5, 4), "minor third": F(6, 5), "octave": F(2, 1)}
            for name, ratio in intervals.items():
                print(f"{name}: {ratio}")
            print("fourth times fifth is an octave: " + str(intervals["fourth"] * intervals["fifth"] == intervals["octave"]))
            print("two major thirds fall short of an octave: " + str(intervals["major third"] ** 2 < intervals["octave"]))
        """,
    ),
)
