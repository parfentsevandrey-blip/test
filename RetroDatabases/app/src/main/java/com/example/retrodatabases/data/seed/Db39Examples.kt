package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1650 to 1660: a pair for Pell's equation, Glauber's salt, Hudde's greatest value, Sluse's mean proportionals, Descartes's consonant ratios and de Witt's latus rectum. */
val db39Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Brouncker's answer to Fermat's sixty-one challenge",
        originalTitle = "Fermat's challenge of 1657",
        author = "William Brouncker",
        year = 1657,
        summary = "Brouncker found whole numbers x and y with x squared minus 61 times y squared equal to one, a case of Pell's equation that Fermat had set to English mathematicians.",
        details = "Fermat posed the sixty-one problem to English mathematicians in 1657, and Brouncker's answer is the well-known smallest solution of that equation. The example checks the pair with exact whole-number arithmetic and prints the result.",
        instructions = """
            1. Save the example as pell61.py.
            2. Run it: python3 pell61.py
        """,
        example = """
            x = 1766319049
            y = 226153980

            print("x squared minus 61 y squared:", x * x - 61 * y * y)
            print(f"x = {x}, y = {y}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Chemistry",
        title = "Glauber's salt and the water in its crystals",
        originalTitle = "De natura salium",
        author = "Johann Rudolf Glauber",
        year = 1658,
        summary = "A crystal of Glauber's salt, sodium sulphate holding ten waters, is more than half water by weight, and the example finds the share from whole-number atomic masses.",
        details = "Glauber made the salt by treating common salt with oil of vitriol, and his book on salts of 1658 described it. The example uses modern whole-number atomic masses, so its figures are not Glauber's own, and it finds the parts of salt and water in one crystal and the salt in 100 grams of crystals.",
        instructions = """
            1. Save the example as glauber_salt.py.
            2. Run it: python3 glauber_salt.py
        """,
        example = """
            SODIUM, SULPHUR, OXYGEN, HYDROGEN = 23, 32, 16, 1   # whole-number atomic masses

            # Glauber's salt is sodium sulphate with ten waters of crystallisation: Na2SO4 and 10 H2O.
            salt = 2 * SODIUM + SULPHUR + 4 * OXYGEN
            water = 10 * (2 * HYDROGEN + OXYGEN)
            crystal = salt + water

            print(f"crystal {crystal} parts: salt {salt}, water {water}")
            print(f"from 100 grams of crystals, {100 * salt / crystal:.1f} grams are salt")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "Hudde's rule for a curve's greatest value",
        originalTitle = "Hudde's rule for maxima and minima",
        author = "Johann Hudde",
        year = 1659,
        summary = "Hudde's rule finds where a polynomial curve reaches its greatest value by an algebraic test that gives the same answer as the later calculus of derivatives.",
        details = "Hudde gave the rule in letters, and it was printed in 1659 in van Schooten's edition of Descartes's Geometry. The example applies it to the curve x squared times (10 minus x) and finds the greatest value at x = 20/3.",
        instructions = """
            1. Save the example as hudde_maximum.py.
            2. Run it: python3 hudde_maximum.py
        """,
        example = """
            from fractions import Fraction

            COEFF = {2: 10, 3: -1}   # f(x) = 10 x^2 - x^3, which is the curve x^2 (10 - x)

            def f(x):
                return sum(c * x ** k for k, c in COEFF.items())

            # Hudde's test: multiply each term by its power, remove one factor of x, and solve what is left.
            # The terms become 2 * 10 * x and 3 * (-1) * x^2; removing x leaves 20 - 3x.
            a = 2 * COEFF[2]   # constant part of what is left: 20
            b = 3 * COEFF[3]   # coefficient of x in what is left: -3
            root = Fraction(-a, b)
            print(f"greatest at x = {root}, about {float(root):.6f}")
            print(f"value there: {f(root)}, about {float(f(root)):.6f}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Geometry",
        title = "Sluse's mean proportionals between 2 and 16",
        originalTitle = "Mesolabum",
        author = "Rene Francois de Sluse",
        year = 1659,
        summary = "Sluse's Mesolabum gave a curve for finding two mean proportionals between two lengths, a classic problem that reduces to a cubic equation.",
        details = "Two mean proportionals x and y between a and b satisfy a over x equal to x over y equal to y over b, so x cubed is a squared times b and y cubed is a times b squared. The example finds both by bisection for a of 2 and b of 16, and checks that the three ratios come out equal.",
        instructions = """
            1. Save the example as mean_proportionals.py.
            2. Run it: python3 mean_proportionals.py
        """,
        example = """
            def cube_root(n):
                # Bisection on x^3 = n for n > 0; the interval halves at each step.
                lo, hi = 0.0, float(max(1, n))
                for _ in range(200):
                    mid = (lo + hi) / 2
                    if mid ** 3 < n:
                        lo = mid
                    else:
                        hi = mid
                return lo

            a, b = 2, 16
            x = cube_root(a * a * b)   # x cubed equals a squared times b
            y = cube_root(a * b * b)   # y cubed equals a times b squared
            print(f"mean proportionals between {a} and {b}: x = {x:.6f}, y = {y:.6f}")
            print(f"check: a/x = {a / x:.6f}, x/y = {x / y:.6f}, y/b = {y / b:.6f}")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "Descartes's consonances as simple ratios",
        originalTitle = "Compendium musicae",
        author = "Rene Descartes",
        year = 1650,
        summary = "Descartes's Compendium of Music, printed in 1650 after his death, rests consonance on simple ratios, and the example lists the chief consonances as exact fractions.",
        details = "The chief consonances are the octave, the fifth, the fourth and the two thirds, with ratios 2 to 1, 3 to 2, 4 to 3, 5 to 4 and 6 to 5. The example prints each ratio as an exact fraction and checks that a fifth and a fourth together make an octave.",
        instructions = """
            1. Save the example as consonances.py.
            2. Run it: python3 consonances.py
        """,
        example = """
            from fractions import Fraction

            RATIOS = {
                "octave": Fraction(2, 1),
                "fifth": Fraction(3, 2),
                "fourth": Fraction(4, 3),
                "major third": Fraction(5, 4),
                "minor third": Fraction(6, 5),
            }

            for name, ratio in RATIOS.items():
                print(f"{name}: {ratio}")
            print("a fifth times a fourth is the octave:", RATIOS["fifth"] * RATIOS["fourth"] == RATIOS["octave"])
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.EXAMPLE,
        section = "Conics",
        title = "De Witt's latus rectum",
        originalTitle = "Elementa curvarum linearum",
        author = "Johan de Witt",
        year = 1659,
        summary = "The latus rectum of an ellipse, the chord through a focus at right angles to the long axis, equals twice the square of the short semi-axis divided by the long one.",
        details = "Johan de Witt's Elementa curvarum linearum of 1659 treated the conic sections with coordinates, and a relation of this kind belongs to that treatment. The example computes the latus rectum for three ellipses from their two semi-axes, using exact fractions.",
        instructions = """
            1. Save the example as latus_rectum.py.
            2. Run it: python3 latus_rectum.py
        """,
        example = """
            from fractions import Fraction

            def latus_rectum(a, b):
                # For x^2 / a^2 + y^2 / b^2 = 1 with a greater than b, the latus rectum is 2 b^2 / a.
                return Fraction(2 * b * b, a)

            for a, b in ((5, 4), (10, 6), (13, 5)):
                print(f"semi-axes {a} and {b}: latus rectum {latus_rectum(a, b)}")
        """,
    ),

)
