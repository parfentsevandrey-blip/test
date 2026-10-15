package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1730 to 1740: Stirling's approximation, a clock's rate, a lens, a comma, a count of stamens and a difference quotient. */
val db31Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Asymptotics",
        title = "Stirling's approximation for the factorial",
        originalTitle = "Methodus differentialis, sive tractatus de summatione et interpolatione serierum infinitarum",
        author = "James Stirling",
        year = 1730,
        summary = "The factorial of n is close to the square root of 2 pi n times (n over e) to the power n, and the program prints the ratio for two values of n.",
        details = "James Stirling's approximation for large factorials appears in his book of 1730, and De Moivre printed a form of the result in the same year. Accounts differ on credit for the formula, and the example compares the exact factorials of 5 and 10 with the approximation.",
        instructions = """
            1. Save the example as stirling_approx.py.
            2. Run it: python3 stirling_approx.py
        """,
        example = """
            from math import e, factorial, pi, sqrt

            for n in (5, 10):
                approx = sqrt(2 * pi * n) * (n / e) ** n
                print(f"n = {n}: n! / Stirling = {factorial(n) / approx:.4f}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Navigation",
        title = "Harrison's longitude error from a clock's daily rate",
        originalTitle = "Harrison's H1 marine timekeeper",
        author = "John Harrison",
        year = 1735,
        summary = "A clock that gains a few seconds a day leads to a longitude error, which the program converts into degrees and into nautical miles at the equator.",
        details = "Harrison's problem was to keep time at sea, so that longitude could be read from the difference between the clock's time and the time at home. The example takes a gain of 3 seconds a day over 40 days, which is 120 seconds of time, and converts it with 4 minutes of time per degree of longitude.",
        instructions = """
            1. Save the example as longitude.py.
            2. Run it: python3 longitude.py
        """,
        example = """
            from fractions import Fraction as F

            gain_per_day = 3                   # seconds the clock gains each day, an illustrative rate
            days = 40
            time_error = gain_per_day * days   # seconds of time
            degrees = F(time_error, 240)       # one degree of longitude is 4 minutes, or 240 seconds, of time
            miles = degrees * 60               # at the equator one degree of longitude is 60 nautical miles
            print(f"time error after {days} days: {time_error} seconds")
            print(f"longitude error: {degrees} degree")
            print(f"distance at the equator: {miles} nautical miles")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Calculus",
        title = "Berkeley's leftover term in a difference quotient",
        originalTitle = "The Analyst; or, a Discourse Addressed to an Infidel Mathematician",
        author = "George Berkeley",
        year = 1734,
        summary = "Berkeley's attack on fluxions pointed at the increment that a difference quotient keeps, and the program prints that leftover for x squared and x cubed.",
        details = "Berkeley printed The Analyst in 1734 to argue that the method of fluxions divides by an increment and then drops it, and he called the vanished increments ghosts of departed quantities. The example computes the difference quotients of x squared and x cubed at x = 3 for increments 1/10 and 1/100, and prints the leftover term exactly.",
        instructions = """
            1. Save the example as berkeley.py.
            2. Run it: python3 berkeley.py
        """,
        example = """
            from fractions import Fraction as F

            def quotient(f, x, o):
                # Difference quotient of f at x over the increment o: (f(x + o) - f(x)) / o.
                return (f(x + o) - f(x)) / o

            square = lambda t: t * t
            cube = lambda t: t ** 3
            cases = [("x squared", square, 6, F(1, 10)), ("x squared", square, 6, F(1, 100)), ("x cubed", cube, 27, F(1, 10))]
            for label, f, main, o in cases:
                q = quotient(f, F(3), o)
                print(f"{label} at x = 3, increment {o}: quotient {q}, left over {q - main}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Smith's thin lens focal length",
        originalTitle = "A Compleat System of Opticks",
        author = "Robert Smith",
        year = 1738,
        summary = "A thin lens with two spherical faces has a focal length set by its index of refraction and the radii of its faces, which the program evaluates for two lenses.",
        details = "Smith's optics of 1738 covers the theory of lenses, and the example applies the thin lens rule in its usual modern form with signed radii. The two lenses it treats have focal lengths 4 and 3, which the program prints as exact fractions.",
        instructions = """
            1. Save the example as lens.py.
            2. Run it: python3 lens.py
        """,
        example = """
            from fractions import Fraction as F

            def focal_length(n, r1, r2):
                # Thin lens rule: 1 over f equals (n - 1) times (1 over r1 minus 1 over r2), with signed radii.
                return 1 / ((n - 1) * (F(1, r1) - F(1, r2)))

            print("lens 1: focal length " + str(focal_length(F(3, 2), 4, -4)))
            print("lens 2: focal length " + str(focal_length(F(5, 3), 3, -6)))
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Tuning",
        title = "Neidhardt's Pythagorean comma from twelve fifths",
        originalTitle = "Gaenzlich erschoepfte mathematische Abtheilungen des diatonisch-chromatischen, temperirten Canonis Monochordi",
        author = "Johann Georg Neidhardt",
        year = 1732,
        summary = "Twelve pure fifths overshoot seven octaves by a small ratio, the Pythagorean comma, which the program computes as an exact fraction.",
        details = "Neidhardt's treatise of 1732 works out how tuning systems share out the mismatch between fifths and octaves. The example computes the comma exactly, as twelve fifths of ratio 3 to 2 against seven octaves of ratio 2, which gives 531441 over 524288.",
        instructions = """
            1. Save the example as comma.py.
            2. Run it: python3 comma.py
        """,
        example = """
            from fractions import Fraction as F

            FIFTH = F(3, 2)
            fifths = FIFTH ** 12        # twelve pure fifths, as a ratio
            octaves = F(2) ** 7         # seven octaves
            comma = fifths / octaves
            print("twelve fifths: " + str(fifths))
            print("seven octaves: " + str(octaves))
            print("Pythagorean comma: " + str(comma))
            print(f"comma as a decimal: {float(comma):.5f}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.EXAMPLE,
        section = "Classification",
        title = "Linnaeus's classes from a count of stamens",
        originalTitle = "Systema naturae",
        author = "Carl Linnaeus",
        year = 1735,
        summary = "The sexual system names a plant's class by the number of its stamens, and the program looks up the names for a few counts.",
        details = "Linnaeus's sexual system, set out in the 1735 Systema naturae, sorts plants into classes by their stamens and into orders by their pistils. The example maps the counts from one to ten to the first ten class names, and reports any other count as outside its table.",
        instructions = """
            1. Save the example as stamens.py.
            2. Run it: python3 stamens.py
        """,
        example = """
            CLASS_NAMES = {1: "Monandria", 2: "Diandria", 3: "Triandria", 4: "Tetrandria", 5: "Pentandria",
                           6: "Hexandria", 7: "Heptandria", 8: "Octandria", 9: "Enneandria", 10: "Decandria"}

            def linnaean_class(stamens):
                # The class is named by the number of stamens; this table holds the classes of one to ten stamens.
                return CLASS_NAMES.get(stamens, "outside this table")

            for count in (6, 3, 10, 12):
                print(f"{count} stamens: {linnaean_class(count)}")
        """,
    ),
)
