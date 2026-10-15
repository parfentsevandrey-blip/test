package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1590 to 1600: Vieta's quadratic, a globe's gores, a chart's secant, a memory palace and a parish rate, with working code. */
val db45Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Vieta's letters write a quadratic",
        originalTitle = "In artem analyticem isagoge",
        author = "Francois Vieta",
        year = 1591,
        summary = "Vieta's letters let one line stand for every quadratic of the form A squared plus B times A equals D, and a short program finds the roots for three sets of letters.",
        details = "The letter form comes from Vieta's Isagoge of 1591, where the unknown is a vowel and the known magnitudes are consonants. The program fills the letters with numbers and uses the square root of the discriminant, which is exact here because each discriminant is a perfect square.",
        instructions = """
            1. Save the example as vieta_quadratic.py.
            2. Run it: python3 vieta_quadratic.py
        """,
        example = """
            from math import isqrt

            def roots(b, d):
                # A quadratum + B in A aequatur D means A*A + b*A - d = 0, so A = (-b +- root) / 2.
                disc = b * b + 4 * d
                root = isqrt(disc)
                assert root * root == disc
                return ((-b + root) // 2, (-b - root) // 2)

            for b, d in ((3, 4), (2, 8), (5, 6)):
                print(f"A quadratum + {b} in A aequatur {d}: roots {roots(b, d)}")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.GUIDE,
        section = "Globes",
        title = "How a globe's gores divide the equator",
        originalTitle = "Printed gores of a globe",
        author = "Jodocus Hondius",
        year = 1598,
        summary = "A globe printed on gores has an equator of pi times its diameter, so each gore takes that length divided by the number of gores.",
        details = "The rule follows from the circumference of a circle, and the example uses illustrative diameters rather than the dimensions of Hondius's own globes. The widths are printed to four decimal places, so each of the three cases can be checked by multiplying out pi.",
        instructions = """
            1. Save the example as gore_width.py.
            2. Run it: python3 gore_width.py
        """,
        example = """
            from math import pi

            def gore_width(diameter, gores):
                # The equator is pi times the diameter; each gore takes an equal share of it.
                return pi * diameter / gores

            for diameter, gores in ((36, 9), (30, 6), (20, 8)):
                print(f"{gores} gores on a {diameter} inch globe: equator width {gore_width(diameter, gores):.4f} inches")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.GUIDE,
        section = "Soundings",
        title = "How a sounding in fathoms becomes feet and metres",
        originalTitle = "How a sounding in fathoms becomes feet and metres",
        author = "Lucas Janszoon Waghenaer",
        year = 1592,
        summary = "A sounding in fathoms becomes feet by multiplying by six, and feet become metres through the exact foot of 0.3048 metre, so a short program prints three soundings in both units.",
        details = "The English fathom is six feet, and the international foot is defined as exactly 0.3048 metre, so the conversions are exact and the printed metres stop at four places. Sea charts of the period gave depths in fathoms, but the soundings in the example are invented, and the program uses fractions so that no rounding enters before the last step.",
        instructions = """
            1. Save the example as fathom_feet.py.
            2. Run it: python3 fathom_feet.py
        """,
        example = """
            from fractions import Fraction as F

            FEET_PER_FATHOM = 6
            METRES_PER_FOOT = F(3048, 10000)  # the international foot, exactly 0.3048 metre

            def feet(fathoms):
                # The English fathom is six feet, so a sounding in fathoms times six gives feet.
                return F(fathoms) * FEET_PER_FATHOM

            def metres(fathoms):
                return feet(fathoms) * METRES_PER_FOOT

            for fathoms in (2, 5, 12):
                print(f"{fathoms} fathoms: {feet(fathoms)} feet, {float(metres(fathoms)):.4f} metres")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.GUIDE,
        section = "Memory",
        title = "How a memory palace fixes a list to rooms",
        originalTitle = "Xiguo jifa",
        author = "Matteo Ricci",
        year = 1596,
        summary = "A memory palace fixes each item of a list to a room in an imagined house, and walking the rooms in reverse recalls the list backwards.",
        details = "The method is the one taught in Ricci's Chinese treatise of 1596, which uses imagined places to hold items in order. The example places five items in five rooms, then prints the walk and the backward recall, so the output shows both.",
        instructions = """
            1. Save the example as memory_palace.py.
            2. Run it: python3 memory_palace.py
        """,
        example = """
            palace = ["gate", "hall", "stair", "chamber", "kitchen"]
            items = ["bread", "ink", "salt", "candle", "key"]

            # Each item is fixed to a room; recall walks the rooms in order, and then backwards.
            places = dict(zip(palace, items))
            for room in palace:
                print(f"walk to the {room}: remember {places[room]}")
            print("recall backwards:", ", ".join(places[room] for room in reversed(palace)))
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.GUIDE,
        section = "Poor relief",
        title = "How a parish rate is shared among households",
        originalTitle = "An Act for the Relief of the Poor",
        author = "English Parliament",
        year = 1598,
        summary = "A parish rate of 45 pounds on rents of 20 pounds is shared in proportion to each household's rent, so each pound of rent carries 2 and a quarter pounds.",
        details = "The rule here is illustrative, since the Act of 1598 left the rating to parish officers and did not fix a formula of this kind. Each household's share is its rent times the rate, and the program prints the shares as exact fractions so that they add up to the full 45 pounds.",
        instructions = """
            1. Save the example as parish_rate.py.
            2. Run it: python3 parish_rate.py
        """,
        example = """
            from fractions import Fraction as F

            def rate_shares(total, rents):
                # Each household pays the same rate for every pound of its assessed rent.
                rate = F(total) / sum(rents.values())
                return {name: rate * rent for name, rent in rents.items()}

            shares = rate_shares(45, {"Ann": 7, "Bess": 5, "Cole": 8})
            for name, share in shares.items():
                print(f"{name} pays {share}")
            print("total", sum(shares.values()))
        """,
    ),

)
