package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1870 to 1880: a stability table, a factor of a Fermat number, a quincunx and ideals in a quadratic ring. */
val db17Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.PROJECT,
        section = "Stability",
        title = "Routh's table for two cubics",
        originalTitle = "A Treatise on the Stability of a Given State of Motion (1877)",
        author = "Edward John Routh",
        year = 1877,
        summary = "Routh's table turns the coefficients of a polynomial into a first column whose sign changes count the roots in the right half-plane.",
        details = "Routh's 1877 treatise on stability gave the table, which decides whether a system settles without anyone solving for the roots. The project builds the table in exact fractions for two cubics, one stable and one not, and counts the sign changes in each first column.",
        instructions = """
            1. Save the example as routh.py.
            2. Run it: python3 routh.py
        """,
        example = """
            from fractions import Fraction

            def routh_first_column(coeffs):
                # coeffs run from the highest power down, for example [1, 2, 3, 1] for s^3 + 2 s^2 + 3 s + 1.
                coeffs = [Fraction(c) for c in coeffs]
                width = (len(coeffs) + 1) // 2
                rows = [coeffs[0::2], coeffs[1::2]]
                rows = [r + [Fraction(0)] * (width - len(r)) for r in rows]
                for _ in range(len(coeffs) - 2):
                    prev, cur = rows[-2], rows[-1]
                    new = [(cur[0] * prev[j + 1] - prev[0] * cur[j + 1]) / cur[0] for j in range(width - 1)]
                    new.append(Fraction(0))
                    rows.append(new)
                return [row[0] for row in rows]

            for name, coeffs in [("s^3 + 2 s^2 + 3 s + 1", [1, 2, 3, 1]), ("s^3 + s^2 + 2 s + 8", [1, 1, 2, 8])]:
                column = routh_first_column(coeffs)
                changes = sum(1 for a, b in zip(column, column[1:]) if (a < 0) != (b < 0))
                print(f"{name}: first column {[str(x) for x in column]}, sign changes {changes}")
                if changes == 0 and all(x > 0 for x in column):
                    print("stable")
                else:
                    print(f"unstable, {changes} roots in the right half plane")
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.PROJECT,
        section = "Fermat numbers",
        title = "Landry's factor of the sixth Fermat number",
        originalTitle = "Factorisation of F6 (1880)",
        author = "F. Landry",
        year = 1880,
        summary = "The sixth Fermat number, two to the 64th power plus one, has the factor 274177, and the program checks the factor and the cofactor with big integers.",
        details = "Euler had shown in 1732 that 641 divides the fifth Fermat number, which disproved the idea that every Fermat number is prime. Landry's 1880 factorisation of the sixth number gives 274177 times 67280421310721, and the example checks that product exactly.",
        instructions = """
            1. Save the example as landry.py.
            2. Run it: python3 landry.py
        """,
        example = """
            # The Fermat numbers are 2 to the power 2 to the n, plus 1.
            F6 = 2 ** 64 + 1
            print("F6 =", F6)
            factor = 274177
            print("274177 divides F6:", F6 % factor == 0)
            cofactor = F6 // factor
            print("cofactor:", cofactor)
            print("product equals F6:", factor * cofactor == F6)
            print("Euler's factor of F5 is 641:", (2 ** 32 + 1) % 641 == 0)
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.PROJECT,
        section = "Probability",
        title = "Galton's quincunx with exact bin counts",
        originalTitle = "Galton board",
        author = "Francis Galton",
        year = 1873,
        summary = "A board of pegs sends each ball left or right at every row, so the bins fill in proportion to binomial coefficients over a power of two.",
        details = "The board is dated to 1873, when a maker first built one, and its first public demonstration is given as 1874. The project computes the exact bin probabilities for six rows as fractions instead of simulating balls.",
        instructions = """
            1. Save the example as quincunx.py.
            2. Run it: python3 quincunx.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            ROWS = 6  # pegs in each row; a ball bounces left or right at each peg
            probs = [Fraction(comb(ROWS, k), 2 ** ROWS) for k in range(ROWS + 1)]
            for k, p in enumerate(probs):
                print(f"bin {k}: {p}")
            print("total probability:", sum(probs))
            mean = sum(k * p for k, p in enumerate(probs))
            print("mean bin:", mean)
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.PROJECT,
        section = "Algebraic number theory",
        title = "Dedekind's ideals and the number 6 in Z[sqrt(-5)]",
        originalTitle = "Uber die Theorie der ganzen algebraischen Zahlen (1871)",
        author = "Richard Dedekind",
        year = 1871,
        summary = "In the numbers a plus b times the square root of minus five, the number 6 has two different factorisations, so elements alone do not settle what is prime.",
        details = "Dedekind's 1871 supplement introduced ideals, which repair the failure of unique factorisation in such rings. The project works with pairs of integers, checks the norms of the factors, and shows that 2 divides a product of two factors without dividing either factor.",
        rare = true,
        instructions = """
            1. Save the example as ideals.py.
            2. Run it: python3 ideals.py
        """,
        example = """
            # Pairs (a, b) stand for a + b * sqrt(-5), with integers a and b.
            def mul(x, y):
                a, b = x
                c, d = y
                return (a * c - 5 * b * d, a * d + b * c)

            def norm(x):
                a, b = x
                return a * a + 5 * b * b

            def conj(x):
                return (x[0], -x[1])

            def divides(x, y):
                # x divides y when y times the conjugate of x is a multiple of norm(x) in both parts.
                p = mul(y, conj(x))
                n = norm(x)
                return p[0] % n == 0 and p[1] % n == 0

            TWO, THREE, P, Q, SIX = (2, 0), (3, 0), (1, 1), (1, -1), (6, 0)
            print("norms of 2, 1 + sqrt(-5), 3:", norm(TWO), norm(P), norm(THREE))
            print("2 * 3 =", mul(TWO, THREE), "and (1 + sqrt(-5)) * (1 - sqrt(-5)) =", mul(P, Q))
            print("2 divides 6:", divides(TWO, SIX))
            print("2 divides 1 + sqrt(-5):", divides(TWO, P))
            print("2 divides 1 - sqrt(-5):", divides(TWO, Q))
            print("2 divides the product but neither factor:", divides(TWO, SIX) and not divides(TWO, P) and not divides(TWO, Q))
        """,
    ),
)
