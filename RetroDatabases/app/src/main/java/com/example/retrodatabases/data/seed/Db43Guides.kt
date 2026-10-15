package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1610 to 1620: Napier's rules for a spherical triangle, logarithmic products, a shadow's height, a mean motion and Bachet's weights. */
val db43Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.GUIDE,
        section = "Spherical trigonometry",
        title = "How Napier's rules of circular parts solve a right spherical triangle",
        originalTitle = "Mirifici Logarithmorum Canonis Constructio",
        author = "John Napier",
        year = 1619,
        summary = "Napier's rules give the sine of a part of a right spherical triangle as a product of two cosines or of two tangents, so the triangle can be solved from a few parts.",
        details = "The rules were published in Napier's Constructio of 1619, after his death, and they reduce the solution of a right spherical triangle to a few products of trigonometric values. The example takes the hypotenuse as 60 degrees and one oblique angle as 30 degrees, and it prints the legs from the two rules and checks the result against the cosine rule.",
        instructions = """
            1. Save the example as napier_parts.py.
            2. Run it: python3 napier_parts.py
        """,
        example = """
            from math import asin, cos, radians, sqrt, tan

            c = radians(60)   # the hypotenuse, illustrative
            A = radians(30)   # one oblique angle, illustrative

            # Circle order of the five parts: a, b, 90-A, 90-c, 90-B.
            # Rule 1: the sine of a part is the product of the cosines of the two parts opposite to it.
            sin_a = cos(radians(90) - A) * cos(radians(90) - c)
            a = asin(sin_a)

            # Rule 2: the sine of b is the product of the tangents of the two parts adjacent to it, a and 90-A.
            sin_b = tan(a) * tan(radians(90) - A)

            cos_a = sqrt(1 - sin_a ** 2)
            cos_b = sqrt(1 - sin_b ** 2)
            print(f"sin a = {sin_a:.3f}")
            print(f"sin b = {sin_b:.3f}")
            print(f"cos a times cos b = {cos_a * cos_b:.3f}, which is cos c = {cos(c):.3f}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.GUIDE,
        section = "Logarithms",
        title = "How a four-figure log table multiplies two numbers",
        originalTitle = "Mirifici logarithmorum canonis descriptio",
        author = "John Napier",
        year = 1614,
        summary = "Napier's principle turns a product into a sum of logarithms, so two numbers are multiplied by adding their table entries and reading the antilogarithm of the sum.",
        details = "Napier published the principle in his canon of 1614. The example uses the entries log 2.5 = 0.3979 and log 1.2 = 0.0792, which are the standard values rounded to four places, and it multiplies three pairs of numbers by adding the entries and raising ten to the sum.",
        instructions = """
            1. Save the example as log_products.py.
            2. Run it: python3 log_products.py
        """,
        example = """
            # Four-figure entries of a log table: the logarithm of each number to base ten, rounded to four places.
            LOG_TABLE = {2.5: 0.3979, 1.2: 0.0792}

            def product_by_logs(x, y):
                # The product is found by adding the logarithms and then taking the antilogarithm of the sum.
                return 10 ** (LOG_TABLE[x] + LOG_TABLE[y])

            for x, y in ((2.5, 1.2), (1.2, 1.2), (2.5, 2.5)):
                print(f"{x} x {y} = {product_by_logs(x, y):.2f}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How a lunar mountain's height follows from its shadow",
        originalTitle = "Sidereus nuncius",
        author = "Galileo Galilei",
        year = 1610,
        summary = "A peak's height is the length of its shadow times the tangent of the Sun's angle of elevation, the rule Galileo used to estimate the heights of the Moon's mountains in 1610.",
        details = "Galileo measured the shadows of lunar peaks near the edge of the lit part, and the Sidereus nuncius reports heights of mountains found by this rule. The example applies the same rule to three illustrative shadows, and its figures are not Galileo's own measurements.",
        instructions = """
            1. Save the example as lunar_shadow.py.
            2. Run it: python3 lunar_shadow.py
        """,
        example = """
            from math import radians, tan

            def peak_height(shadow_km, sun_elevation_deg):
                # The peak, its shadow and the sun's ray form a right triangle, so the height is the shadow times the tangent of the elevation.
                return shadow_km * tan(radians(sun_elevation_deg))

            for shadow, elevation in ((12, 30), (4, 45), (10, 10)):
                h = peak_height(shadow, elevation)
                print(f"shadow {shadow} km at {elevation} degrees: height {h:.2f} km")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How a mean motion gives a moon's place",
        originalTitle = "Epitome astronomiae Copernicanae",
        author = "Johannes Kepler",
        year = 1618,
        summary = "A moon with a fixed period turns through a set angle each day, so its place after any number of days follows from the fraction of a period that has passed.",
        details = "Kepler's Epitome reduces a planet's place to a mean motion, and the same rule applies to a moon with a fixed period. The example uses two illustrative periods of four and six days, not the real periods of Jupiter's moons, and prints the place of each moon after 10 and 14 days.",
        instructions = """
            1. Save the example as mean_motion.py.
            2. Run it: python3 mean_motion.py
        """,
        example = """
            # Illustrative periods in days, not the real periods of Jupiter's moons.
            PERIODS = {"moon A": 4.0, "moon B": 6.0}

            def place_degrees(period_days, days):
                # The mean motion turns the moon through 360 degrees in each period, so only the fraction of a period matters.
                turns = (days / period_days) % 1.0
                return 360 * turns

            for days in (10, 14):
                for name, period in PERIODS.items():
                    print(f"{name} after {days} days: {place_degrees(period, days):.1f} degrees")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.GUIDE,
        section = "Puzzles",
        title = "How Bachet's four weights weigh any whole number up to 40",
        originalTitle = "Problemes plaisants et delectables qui se font par les nombres",
        author = "Claude Gaspar Bachet de Meziriac",
        year = 1612,
        summary = "Four weights of 1, 3, 9 and 27 can each go on the left pan, on the right pan or be left off, and every whole number from 1 to 40 can be weighed this way.",
        details = "Bachet printed the balance puzzle in his book of 1612 on pleasant and delightful problems with numbers, and the weights of 1, 3, 9 and 27 are the smallest set that covers the range. The example checks all 81 placements of the weights and prints the amounts that can be measured, which run from 1 to 40.",
        instructions = """
            1. Save the example as bachet_weights.py.
            2. Run it: python3 bachet_weights.py
        """,
        example = """
            from itertools import product

            WEIGHTS = (1, 3, 9, 27)

            # Each weight is on the left pan (-1), on the right pan (+1) or left off (0).
            amounts = sorted({sum(w * s for w, s in zip(WEIGHTS, signs)) for signs in product((-1, 0, 1), repeat=4)})
            positive = [a for a in amounts if a > 0]

            print("weights:", WEIGHTS)
            print("amounts measured:", len(positive))
            print("every whole number from 1 to 40 is measured:", positive == list(range(1, 41)))
            print("largest amount:", max(positive))
        """,
    ),

)
