package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1710 to 1720: Taylor's expansion, rencontres, divided differences, a diving bell's air and a radical index. */
val db33Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.GUIDE,
        section = "Calculus",
        title = "How Taylor's series builds a function from its derivatives",
        originalTitle = "Methodus incrementorum directa et inversa",
        author = "Brook Taylor",
        year = 1715,
        summary = "The coefficient of each power in a Taylor expansion is the derivative at the centre divided by a factorial, and the program finds them for a quartic.",
        details = "Taylor's book of 1715 gives the rule that a function near a point is a sum of its derivatives there, each multiplied by a power of the distance and divided by a factorial. The example expands x to the fourth minus 2 x squared plus 1 about the point 1, finds the coefficients with exact fractions, and checks the series at 3/2.",
        instructions = """
            1. Save the example as taylor.py.
            2. Run it: python3 taylor.py
        """,
        example = """
            from fractions import Fraction as F
            from math import factorial

            # f(x) = x^4 - 2x^2 + 1, with the coefficients listed from x^0 up to x^4.
            f = [1, 0, -2, 0, 1]

            def derivative(p):
                # The term c x^i becomes i c x^(i-1).
                return [i * p[i] for i in range(1, len(p))] or [0]

            def evaluate(p, x):
                return sum(c * x**i for i, c in enumerate(p))

            centre = 1
            coeffs = []
            d = f
            for k in range(len(f)):
                # The coefficient of (x - centre) to the power k is the k-th derivative at the centre over k factorial.
                coeffs.append(F(evaluate(d, centre), factorial(k)))
                d = derivative(d)

            print("Taylor coefficients about 1:", " ".join(str(c) for c in coeffs))
            h = F(1, 2)
            series = sum(c * h**k for k, c in enumerate(coeffs))
            exact = evaluate(f, centre + h)
            print("series at 3/2:", series)
            print("exact value at 3/2:", exact)
            print("series agrees:", series == exact)
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Montmort's rencontres count the chance of a match",
        originalTitle = "Essai d'analyse sur les jeux de hazard",
        author = "Pierre Remond de Montmort",
        year = 1713,
        summary = "A rearrangement with no item left in its own place is a derangement, and the program counts them to find the chance of at least one match.",
        details = "Montmort's essay on games of chance first posed the matching problem in 1708 and returned to it in its second edition of 1713. The example counts the derangements of 2 to 6 items by brute force, and then gives the chance that at least one item lands in its own place, using exact fractions.",
        instructions = """
            1. Save the example as rencontres.py.
            2. Run it: python3 rencontres.py
        """,
        example = """
            from fractions import Fraction
            from itertools import permutations
            from math import factorial

            def derangements(n):
                # A derangement moves every item away from its own place.
                return sum(1 for p in permutations(range(n)) if all(p[i] != i for i in range(n)))

            for n in range(2, 7):
                d = derangements(n)
                chance = 1 - Fraction(d, factorial(n))
                print(f"n = {n}: derangements {d}, chance of a match {chance}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.GUIDE,
        section = "Interpolation",
        title = "How Newton's divided differences fit a polynomial through a table",
        originalTitle = "Methodus differentialis",
        author = "Isaac Newton",
        year = 1711,
        summary = "Divided differences of three table values give the coefficients of Newton's form, which the program evaluates at the table points and at 3.",
        details = "Newton's rule for interpolating from a table was printed in 1711 in Methodus differentialis, and it is usually traced back to his letters of 1676. The example takes the values of x squared plus 1 at 0, 1 and 2, builds the divided differences, and checks the Newton form against the table rule at 0 to 3.",
        instructions = """
            1. Save the example as divided_differences.py.
            2. Run it: python3 divided_differences.py
        """,
        example = """
            from fractions import Fraction as F

            xs = [0, 1, 2]
            ys = [1, 2, 5]          # values of x squared plus 1 at the table points

            def divided_differences(xs, ys):
                table = [F(y) for y in ys]
                coeffs = [table[0]]
                for order in range(1, len(xs)):
                    table = [(table[i + 1] - table[i]) / (xs[i + order] - xs[i]) for i in range(len(table) - 1)]
                    coeffs.append(table[0])
                return coeffs

            c = divided_differences(xs, ys)
            print("divided differences:", " ".join(str(x) for x in c))

            def newton(x):
                # Newton form: c0 + c1 (x - x0) + c2 (x - x0)(x - x1).
                total, product = F(0), F(1)
                for k, ck in enumerate(c):
                    total += ck * product
                    product *= (x - xs[k])
                return total

            for x in range(4):
                print(f"x = {x}: Newton form {newton(x)}, table rule {x * x + 1}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.GUIDE,
        section = "Diving",
        title = "How Boyle's law sets the air left in a diving bell",
        originalTitle = "Diving bell with air barrels",
        author = "Edmond Halley",
        year = 1716,
        summary = "A fixed amount of air shrinks as the water pressure rises, and the program prints the volume of a bell's air at four depths.",
        details = "Halley's bell of 1716 held air at the pressure of the water around it, and Boyle's law from the 1660s says the volume of a fixed amount of air falls in proportion as the pressure rises. The example takes 100 cubic feet of air at the surface and one extra atmosphere for every 33 feet of water, a round figure, and prints the volume at depths of 0, 33, 66 and 99 feet.",
        instructions = """
            1. Save the example as diving_bell.py.
            2. Run it: python3 diving_bell.py
        """,
        example = """
            from fractions import Fraction as F

            SURFACE_AIR = 100           # cubic feet of air in the bell at the surface
            FEET_PER_ATMOSPHERE = 33    # round figure: one extra atmosphere of pressure per 33 feet of water

            def air_volume(depth_feet):
                # Boyle's law: pressure times volume stays constant, and the pressure is 1 + depth / 33 atmospheres.
                pressure = 1 + F(depth_feet, FEET_PER_ATMOSPHERE)
                return F(SURFACE_AIR) / pressure

            for depth in (0, 33, 66, 99):
                print(f"depth {depth} feet: {air_volume(depth)} cubic feet of air")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.GUIDE,
        section = "Chinese writing",
        title = "How a radical index sorts Chinese characters",
        originalTitle = "Kangxi zidian",
        author = "Compiled by a commission under the Kangxi emperor",
        year = 1716,
        summary = "Characters are filed under their radical and then by the number of strokes left after it, which the program applies to a small index.",
        details = "The Kangxi dictionary of 1716 arranged its entries under 214 radicals, and within each radical by the remaining strokes. The example sorts five made-up labels with radical numbers and stroke counts, because the program does not need real characters to show the order.",
        instructions = """
            1. Save the example as radical_index.py.
            2. Run it: python3 radical_index.py
        """,
        example = """
            # Each entry: a made-up label, its radical number in the Kangxi order, and the strokes left after the radical.
            entries = [
                ("gamma", 9, 3),
                ("alpha", 2, 5),
                ("delta", 9, 1),
                ("beta", 1, 2),
                ("epsilon", 2, 1),
            ]

            ordered = sorted(entries, key=lambda e: (e[1], e[2]))
            print("index order: " + " ".join(label for label, radical, strokes in ordered))
            for label, radical, strokes in ordered:
                print(f"radical {radical}, {strokes} strokes left: {label}")
        """,
    ),
)
