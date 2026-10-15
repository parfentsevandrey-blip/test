package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1730 to 1740: the bridges of Konigsberg, a bell curve for tosses, Stirling's group counts, a payout game and central differences. */
val db31Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.GUIDE,
        section = "Graph theory",
        title = "Euler's bridges of Konigsberg and the odd-vertex test",
        originalTitle = "Solutio problematis ad geometriam situs pertinentis",
        author = "Leonhard Euler",
        year = 1736,
        summary = "A walk that crosses every bridge exactly once needs zero or two land masses with an odd number of bridges, and the program checks this for the seven bridges.",
        details = "Euler's paper on the bridges of Konigsberg was written in 1736 and printed in St Petersburg in 1741. He showed that the seven bridges cannot be crossed once each, since all four land masses touch an odd number of bridges.",
        instructions = """
            1. Save the example as konigsberg.py.
            2. Run it: python3 konigsberg.py
        """,
        example = """
            from collections import Counter

            # Seven bridges: two between A and B, two between A and C, one between A and D, one between B and D, one between C and D.
            bridges = [("A", "B"), ("A", "B"), ("A", "C"), ("A", "C"), ("A", "D"), ("B", "D"), ("C", "D")]
            degree = Counter()
            for x, y in bridges:
                degree[x] += 1
                degree[y] += 1
            print("degrees: " + " ".join(f"{v}={degree[v]}" for v in "ABCD"))
            odd = [v for v in "ABCD" if degree[v] % 2 == 1]
            print("odd vertices: " + str(len(odd)))
            print("walk crossing every bridge once exists: " + str(len(odd) in (0, 2)))
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "De Moivre's normal curve for the central binomial term",
        originalTitle = "Approximatio ad summam terminorum binomii (a+b)^n in seriem expansi",
        author = "Abraham de Moivre",
        year = 1733,
        summary = "Near the middle of many tosses the chance of an exact count is close to the height of a bell curve, which the program compares with the exact value for 100 tosses.",
        details = "De Moivre gave this approximation in a privately printed paper of 1733 on the sum of binomial terms. The example compares the exact chance of 50 heads in 100 tosses with the height of the bell curve at its middle, which is close to it but not equal.",
        instructions = """
            1. Save the example as demoivre.py.
            2. Run it: python3 demoivre.py
        """,
        example = """
            from fractions import Fraction
            from math import comb, pi, sqrt

            n, k = 100, 50
            p = Fraction(1, 2)
            exact = Fraction(comb(n, k)) * p**k * (1 - p)**(n - k)
            print(f"exact chance of {k} heads in {n} tosses: {float(exact):.4f}")
            variance = n * 0.5 * 0.5
            approx = 1 / sqrt(2 * pi * variance)
            print(f"normal approximation: {approx:.4f}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.GUIDE,
        section = "Combinatorics",
        title = "Stirling's numbers counting groups of a set",
        originalTitle = "Methodus differentialis, sive tractatus de summatione et interpolatione serierum infinitarum",
        author = "James Stirling",
        year = 1730,
        summary = "The number of ways to split a set of n items into k nonempty groups, which the program builds row by row with a recurrence.",
        details = "James Stirling's Methodus differentialis of 1730 is the book usually cited for the numbers that bear his name. The rows of the table grow quickly, and their sums give the Bell numbers, which the example checks.",
        instructions = """
            1. Save the example as stirling_numbers.py.
            2. Run it: python3 stirling_numbers.py
        """,
        example = """
            def stirling2(n, k):
                # S(n, k) counts the ways to split n items into k nonempty groups.
                if n == 0 and k == 0:
                    return 1
                if n == 0 or k == 0:
                    return 0
                return k * stirling2(n - 1, k) + stirling2(n - 1, k - 1)

            rows = {n: [stirling2(n, k) for k in range(1, n + 1)] for n in range(1, 6)}
            print("row 4: " + " ".join(str(x) for x in rows[4]))
            print("row 5: " + " ".join(str(x) for x in rows[5]))
            print("row sums: " + " ".join(str(sum(rows[n])) for n in range(1, 6)))
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "The St Petersburg game with a truncated payout",
        originalTitle = "Specimen theoriae novae de mensura sortis",
        author = "Daniel Bernoulli",
        year = 1738,
        summary = "A game whose payout doubles with each toss until a head appears, which shows why the unlimited game has no finite average payout.",
        details = "Daniel Bernoulli published the paper in St Petersburg in 1738, and the game had been posed by his cousin Nicolas Bernoulli in a letter of 1713. With the game cut off after N tosses, the average payout is exactly N, so it grows without a bound as the limit rises.",
        instructions = """
            1. Save the example as petersburg_truncated.py.
            2. Run it: python3 petersburg_truncated.py
        """,
        example = """
            from fractions import Fraction

            def payout(tosses):
                # Heads first on toss k pays 2 to the power k, and that toss has chance 1/2 to the power k.
                return sum(Fraction(1, 2**k) * 2**k for k in range(1, tosses + 1))

            N = 10
            print(f"expected payout with {N} tosses allowed: {payout(N)}")
            print(f"chance the game lasts past {N} tosses: {float(Fraction(1, 2**N)):.10f}")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.GUIDE,
        section = "Interpolation",
        title = "How Stirling's central differences interpolate a table",
        originalTitle = "Methodus differentialis, sive tractatus de summatione et interpolatione serierum infinitarum",
        author = "James Stirling",
        year = 1730,
        summary = "Stirling's central differences estimate a value between table entries by a series in the differences around that point, and the program checks it on a cubic.",
        details = "Stirling's central-difference series is printed in his Methodus differentialis of 1730, the book that also holds his approximation for the factorial. The example interpolates x cubed at one half from a table at x = -2 to 2, and the series reproduces the cubic exactly.",
        instructions = """
            1. Save the example as stirling_interpolation.py.
            2. Run it: python3 stirling_interpolation.py
        """,
        example = """
            from fractions import Fraction as F

            # Table of f(x) = x cubed at x = -2, -1, 0, 1, 2, with step 1.
            table = {x: x ** 3 for x in range(-2, 3)}
            f0, f1, fm1 = table[0], table[1], table[-1]
            f2, fm2 = table[2], table[-2]
            mean_first = F(f1 - fm1, 2)                     # mean of the central first differences
            second = f1 - 2 * f0 + fm1                      # second central difference at 0
            mean_third = F(f2 - 2 * f1 + 2 * fm1 - fm2, 2)  # mean of the central third differences
            p = F(1, 2)
            value = f0 + p * mean_first + p * p / 2 * second + p * (p * p - 1) / 6 * mean_third
            print(f"interpolated value at x = 1/2: {value}")
            print(f"exact value at x = 1/2: {F(1, 2) ** 3}")
            print("formula reproduces the cubic: " + str(value == F(1, 2) ** 3))
        """,
    ),
)
