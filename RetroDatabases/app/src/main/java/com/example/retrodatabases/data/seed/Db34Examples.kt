package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1700 to 1710: a matching chance, rings of glass, a coin tossed across 82 years, a pressure law, polygons around the circle and a tenth of rents. */
val db34Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Newton's colour mixing by centre of gravity",
        originalTitle = "Opticks",
        author = "Isaac Newton",
        year = 1704,
        summary = "Newton's rule places a mixed colour at the centre of gravity of the colours mixed, weighted by how much of each is used.",
        details = "Newton gave the rule in his Opticks of 1704, where the colours of the spectrum were set out around a circle. The example uses illustrative coordinates on a plane, and it computes the weighted centre for three mixtures.",
        instructions = """
            1. Save the example as colour_mixing.py.
            2. Run it: python3 colour_mixing.py
        """,
        example = """
            from fractions import Fraction as F

            def mixture(points, amounts):
                # The mixed colour is the centre of gravity of the colour points, weighted by the amounts used.
                total = sum(amounts)
                x = F(sum(a * p[0] for p, a in zip(points, amounts)), total)
                y = F(sum(a * p[1] for p, a in zip(points, amounts)), total)
                return x, y

            for label, points, amounts in (
                ("two equal amounts", [(0, 0), (4, 0)], [1, 1]),
                ("one part and three parts", [(0, 0), (4, 0)], [1, 3]),
                ("on a diagonal", [(0, 0), (4, 8)], [3, 1]),
            ):
                x, y = mixture(points, amounts)
                print(f"{label}: ({x}, {y})")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Newton's rings: the radii of the dark rings",
        originalTitle = "Opticks",
        author = "Isaac Newton",
        year = 1704,
        summary = "The radii of the dark rings seen between a lens and a flat glass grow as the square root of the ring number.",
        details = "Newton described the rings in his Opticks of 1704, and they show that the air film at each dark ring is a fixed number of half-wavelengths thick. The example uses an illustrative lens radius and wavelength, chosen so that the numbers stay simple.",
        instructions = """
            1. Save the example as newton_rings.py.
            2. Run it: python3 newton_rings.py
        """,
        example = """
            from math import sqrt

            R = 4.0       # radius of curvature of the lens, in illustrative units
            LAMBDA = 1.0  # wavelength of the light, in the same units

            def dark_ring_radius(k):
                # At a dark ring the air film is k half-wavelengths thick, so r squared is about k times R times lambda.
                return sqrt(k * R * LAMBDA)

            for k in range(1, 5):
                print(f"dark ring {k}: radius {dark_ring_radius(k):.2f}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Arbuthnot's sign test on 82 years of christenings",
        originalTitle = "An Argument for Divine Providence, taken from the constant regularity observ'd in the births of both sexes",
        author = "John Arbuthnot",
        year = 1710,
        summary = "The chance that a fair coin gives one outcome in each of 82 years, computed exactly and then as a decimal.",
        details = "Arbuthnot's counts ran over 82 years of London christenings, from 1629 to 1710, and in each year males were more numerous. The example computes the chance of one outcome in all 82 years under a fair chance, which is one part in 2 to the power 82.",
        instructions = """
            1. Save the example as sign_test.py.
            2. Run it: python3 sign_test.py
        """,
        example = """
            from fractions import Fraction as F

            YEARS = 1710 - 1629 + 1   # 82 years of London christening counts, counted inclusively
            chance = F(1, 2 ** YEARS)

            print("years counted:", YEARS)
            print("chance of one outcome in every year:", chance)
            print(f"as a decimal: {float(chance):.3e}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Thermometry",
        title = "Amontons's pressure law at fixed volume",
        originalTitle = "Memoire of 1702 in the Memoires of the Paris Academy",
        author = "Guillaume Amontons",
        year = 1702,
        summary = "At fixed volume the pressure of air rises in a straight line with the heat, so equal steps of heat give equal rises of pressure.",
        details = "Amontons reported the link in 1702 from his air thermometer, which used the freezing and boiling points of water as its fixed marks. The example uses illustrative units for the pressure and the heat, chosen so that the rise is the same for each degree.",
        instructions = """
            1. Save the example as amontons_law.py.
            2. Run it: python3 amontons_law.py
        """,
        example = """
            P0 = 100   # pressure at the freezing mark, in illustrative units
            RISE = 4   # pressure gained per degree of heat at fixed volume, in the same units

            def pressure(degrees):
                # At fixed volume the pressure rises in a straight line with the heat.
                return P0 + RISE * degrees

            for t in (0, 10, 20, 35):
                print(f"heat {t}: pressure {pressure(t)}")
            print("equal steps of heat give equal rises:", pressure(20) - pressure(10) == pressure(10) - pressure(0))
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Geometry",
        title = "Polygons that enclose the circle ratio Jones named",
        originalTitle = "Synopsis Palmariorum Matheseos",
        author = "William Jones",
        year = 1706,
        summary = "Half the perimeter of a regular polygon drawn inside a circle approaches the circle ratio as the number of sides doubles.",
        details = "Jones's symbol pi named the ratio that inscribed polygons had been used to bracket since antiquity. The example computes the half-perimeter for polygons of 6, 12, 24, 48 and 96 sides, which settle towards 3.141.",
        instructions = """
            1. Save the example as polygons.py.
            2. Run it: python3 polygons.py
        """,
        example = """
            from math import pi, sin

            for n in (6, 12, 24, 48, 96):
                # Half the perimeter of a regular n-sided polygon inside a unit circle is n times sin(pi / n).
                print(f"{n}-gon: {n * sin(pi / n):.3f}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.EXAMPLE,
        section = "Taxation",
        title = "Vauban's tenth of parish incomes",
        originalTitle = "Projet d'une dixme royale",
        author = "Sebastien Le Prestre de Vauban",
        year = 1707,
        summary = "A tenth of each parish's income, summed over the parishes, computed for three illustrative rents.",
        details = "Vauban's proposal taxed a tenth of all income, so the total tax was a tenth of the total income of the parishes surveyed. The rents in the example are illustrative figures chosen for the calculation, not Vauban's own data.",
        instructions = """
            1. Save the example as tithe.py.
            2. Run it: python3 tithe.py
        """,
        example = """
            RENTS = [("parish A", 1200), ("parish B", 850), ("parish C", 2300)]   # illustrative figures, not Vauban's data

            total = 0
            for name, rent in RENTS:
                tithe = rent / 10   # one tenth, as a proposal of this kind would take
                total += tithe
                print(f"{name}: tithe {tithe:.0f}")
            print(f"total tithe: {total:.0f}")
        """,
    ),

)
