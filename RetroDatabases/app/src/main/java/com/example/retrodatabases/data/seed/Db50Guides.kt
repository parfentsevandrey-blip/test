package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1540 to 1545: Ferrari's quartic, Cardano's cubic, half-angle chords, German miles to degrees and the odd numbers that make squares. */
val db50Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.GUIDE,
        section = "Quartic equations",
        title = "How Ferrari's method reduces a quartic to two quadratics",
        originalTitle = "Ars magna",
        author = "Lodovico Ferrari",
        year = 1545,
        summary = "Ferrari's method turns a quartic into a pair of quadratics by completing a square, and the example checks it on a quartic with four whole roots.",
        details = "The method was printed in Cardano's Ars magna of 1545, which credits the solution of the quartic to Ferrari, Cardano's pupil. The example uses one quartic whose roots are known, x to the fourth minus 5x squared plus 4, and checks each root it finds.",
        instructions = """
            1. Save the example as ferrari_quartic.py.
            2. Run it: python3 ferrari_quartic.py
        """,
        example = """
            import math

            p, q, r = -5, 0, 4          # the quartic x**4 + p*x**2 + q*x + r = 0
            m = 2                       # chosen so that the right side below is a perfect square
            k = math.isqrt(2 * m - p)   # the right side is (2m - p) x**2 - q x + (m*m - r), here 9 x**2
            assert k * k == 2 * m - p and q == 0 and m * m == r

            roots = []
            for s in (1, -1):
                # x**2 + m = s*k*x, so the two roots have sum s*k and product m
                d = (s * k) ** 2 - 4 * m
                root = math.isqrt(d)
                pair = sorted([((s * k) + root) // 2, ((s * k) - root) // 2])
                print(f"quadratic with sum {s * k} and product {m}: roots {pair[0]} and {pair[1]}")
                roots.extend(pair)

            for x in roots:
                print(f"x = {x}: quartic is {x**4 + p*x*x + q*x + r}")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.GUIDE,
        section = "Cubic equations",
        title = "How Cardano's formula solves x cubed plus px equals q",
        originalTitle = "Ars magna",
        author = "Girolamo Cardano",
        year = 1545,
        summary = "Cardano's formula writes a root of x cubed plus px equals q as the sum of two real cube roots, and the example checks it on two equations.",
        details = "Ars magna of 1545 printed the method for this form of cubic, which Cardano had learned from Tartaglia under an oath of secrecy. The example checks the formula on two equations with known roots, x cubed plus 6x equals 20 and x cubed plus 3x equals 4.",
        instructions = """
            1. Save the example as cardano_cubic.py.
            2. Run it: python3 cardano_cubic.py
        """,
        example = """
            import math

            def cube_root(t):
                # The real cube root, which also handles a negative number.
                return math.copysign(abs(t) ** (1 / 3), t)

            def cardano_root(p, q):
                # For x**3 + p*x = q with p > 0, x is the sum of two cube roots of q/2 plus or minus a square root.
                root = math.sqrt((q / 2) ** 2 + (p / 3) ** 3)
                return cube_root(q / 2 + root) + cube_root(q / 2 - root)

            for p, q in ((6, 20), (3, 4)):
                x = cardano_root(p, q)
                print(f"x^3 + {p}x = {q}: x = {round(x, 6)}")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.GUIDE,
        section = "Trigonometry",
        title = "How half-angle steps build a table of chords",
        originalTitle = "De revolutionibus orbium coelestium",
        author = "Nicolaus Copernicus",
        year = 1543,
        summary = "Halving an arc in a table of chords needs only a square root, and the example builds four chords from the chord of sixty degrees.",
        details = "Copernicus's book of 1543 relied on tables of chords, the trigonometry of its day, and the half-chord rule in the example is the kind of step that Ptolemy's chord tables used. The example works on a circle of radius one, where the chord of an arc is twice its sine.",
        instructions = """
            1. Save the example as half_chords.py.
            2. Run it: python3 half_chords.py
        """,
        example = """
            import math

            def halve(chord):
                # The chord of half an arc is the square root of 2 minus the square root of 4 minus the chord squared.
                return math.sqrt(2 - math.sqrt(4 - chord * chord))

            chord = 1.0   # the chord of sixty degrees on a circle of radius one
            for degrees in (60, 30, 15, 7.5):
                print(f"chord of {degrees} degrees: {chord:.4f}")
                chord = halve(chord)
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.GUIDE,
        section = "Measures",
        title = "How a distance in German miles turns into degrees",
        originalTitle = "Cosmographia",
        author = "Sebastian Munster",
        year = 1544,
        summary = "Fifteen German miles make one degree of the equator, so a distance in German miles divides by fifteen to give degrees.",
        details = "German miles were the usual unit of German cosmography in the sixteenth century, and the geographical mile was one fifteenth of a degree. The example converts three illustrative distances with exact fractions and does not use any figure from Munster's book.",
        instructions = """
            1. Save the example as german_miles.py.
            2. Run it: python3 german_miles.py
        """,
        example = """
            from fractions import Fraction as F

            MILES_PER_DEGREE = 15   # the geographical mile: fifteen to a degree of the equator

            for miles in (30, 10, 45):
                print(f"{miles} German miles = {F(miles, MILES_PER_DEGREE)} degrees")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.GUIDE,
        section = "Squares",
        title = "How the first odd numbers add up to squares",
        originalTitle = "Arithmetica integra",
        author = "Michael Stifel",
        year = 1544,
        summary = "The sum of the first n odd numbers is n times n, and a short program checks the rule for three values of n.",
        details = "Stifel's Arithmetica integra of 1544 treated square numbers and their sums, and the rule that odd numbers add up to squares is one of the oldest in number theory. The example checks the rule for three values of n with integer arithmetic only.",
        instructions = """
            1. Save the example as odd_squares.py.
            2. Run it: python3 odd_squares.py
        """,
        example = """
            def odd_sum(n):
                # The sum of the first n odd numbers: 1 + 3 + 5 and so on up to 2n - 1.
                return sum(2 * k - 1 for k in range(1, n + 1))

            for n in (1, 4, 10):
                print(f"the first {n} odd numbers sum to {odd_sum(n)}, which is {n} times {n}")
        """,
    ),

)
