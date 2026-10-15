package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1810 to 1820: probability, determinants, bisection, polynomial evaluation and hypergeometric series, each with a program. */
val db23Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Laplace's rule of succession estimates a chance",
        originalTitle = "Theorie analytique des probabilites (1812)",
        author = "Pierre-Simon Laplace",
        year = 1812,
        summary = "After s successes in n trials, the chance that the next trial succeeds is (s+1)/(n+2), which the program computes in exact fractions.",
        details = "Laplace stated the rule in a memoir of 1774 and restated it in his 1812 treatise, and the record uses 1812 for the treatise. The example takes a run of outcomes and prints the chance after each trial in exact fractions.",
        instructions = """
            1. Save the example as laplace_rule.py.
            2. Run it: python3 laplace_rule.py
        """,
        example = """
            from fractions import Fraction

            # Laplace's rule of succession: after s successes in n trials, the chance of success next time is (s+1)/(n+2).
            outcomes = "SSFSSSFS"
            successes = 0
            for n, outcome in enumerate(outcomes, start=1):
                if outcome == "S":
                    successes += 1
                chance = Fraction(successes + 1, n + 2)
                print(f"trial {n}, successes so far {successes}: chance {chance}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.GUIDE,
        section = "Determinants",
        title = "How Cauchy's product rule for determinants works",
        originalTitle = "Memoire sur les fonctions qui ne peuvent obtenir que deux valeurs egales et de signes contraires (1812)",
        author = "Augustin-Louis Cauchy",
        year = 1812,
        summary = "Cauchy showed that the determinant of a product of two square arrays is the product of their determinants, and the program checks the rule on two 3 by 3 arrays.",
        details = "Cauchy's 1812 memoir gave the product rule and the name determinant for the signed sum of array terms. The example computes the determinant of two integer arrays as a signed sum over permutations, multiplies the arrays, and checks the rule.",
        instructions = """
            1. Save the example as cauchy_det.py.
            2. Run it: python3 cauchy_det.py
        """,
        example = """
            from itertools import permutations

            def sign(p):
                # Sign of a permutation: the parity of its number of inversions.
                s = 1
                for i in range(len(p)):
                    for j in range(i + 1, len(p)):
                        if p[i] > p[j]:
                            s = -s
                return s

            def det(m):
                # The determinant as a signed sum over all permutations of the columns.
                n = len(m)
                total = 0
                for p in permutations(range(n)):
                    term = sign(p)
                    for i in range(n):
                        term *= m[i][p[i]]
                    total += term
                return total

            def mul(a, b):
                n = len(a)
                return [[sum(a[i][k] * b[k][j] for k in range(n)) for j in range(n)] for i in range(n)]

            A = [[2, 1, 0], [0, 1, 3], [1, 0, 1]]
            B = [[1, 0, 2], [1, 1, 0], [0, 2, 1]]
            AB = mul(A, B)
            print("det A =", det(A))
            print("det B =", det(B))
            print("det AB =", det(AB))
            print("det A times det B =", det(A) * det(B))
            print("product rule holds:", det(AB) == det(A) * det(B))
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.GUIDE,
        section = "Analysis",
        title = "How bisection finds a root, Bolzano's 1817 proof",
        originalTitle = "Rein analytischer Beweis des Lehrsatzes (1817)",
        author = "Bernard Bolzano",
        year = 1817,
        summary = "If a continuous function changes sign across an interval, halving the interval again and again pins down a root, which the program does in exact fractions.",
        details = "Bolzano's 1817 proof of the intermediate value theorem shows that a sign change forces a root, and the same halving gives a bisection method. The example applies it to x squared minus two on the interval from one to two and prints each midpoint and its sign.",
        instructions = """
            1. Save the example as bolzano_bisection.py.
            2. Run it: python3 bolzano_bisection.py
        """,
        example = """
            from fractions import Fraction

            def f(x):
                return x * x - 2

            lo, hi = Fraction(1), Fraction(2)
            for step in range(1, 7):
                mid = (lo + hi) / 2
                value = f(mid)
                print(f"step {step}: midpoint {mid}, f = {value}")
                if value < 0:
                    lo = mid
                else:
                    hi = mid
            print(f"root between {lo} and {hi}")
            print("width", hi - lo)
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.GUIDE,
        section = "Polynomials",
        title = "How Horner's method evaluates a polynomial",
        originalTitle = "A new method of solving numerical equations of all orders, by continuous approximation (1819)",
        author = "William George Horner",
        year = 1819,
        summary = "Horner's scheme rewrites a polynomial in nested form, so that it can be evaluated with one multiplication and one addition per coefficient.",
        details = "Horner's paper appeared in the Philosophical Transactions in 1819, and the nested evaluation is the part used most today. Accounts credit Ruffini with a similar scheme earlier, so the priority is disputed. The example evaluates a cubic at several points in exact integers and compares the result with direct evaluation.",
        instructions = """
            1. Save the example as horner.py.
            2. Run it: python3 horner.py
        """,
        example = """
            coeffs = [2, -6, 2, -1]   # 2x^3 - 6x^2 + 2x - 1, highest power first

            def horner(cs, x):
                # Nested form: ((2x - 6)x + 2)x - 1, evaluated from the inside out.
                value = 0
                for c in cs:
                    value = value * x + c
                return value

            def direct(cs, x):
                n = len(cs) - 1
                total = 0
                for i, c in enumerate(cs):
                    total += c * x ** (n - i)
                return total

            for x in (0, 1, 2, 3, -1):
                print(f"x = {x}: Horner {horner(coeffs, x)}, direct {direct(coeffs, x)}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.GUIDE,
        section = "Series",
        title = "How Gauss's ratio test settles a hypergeometric series",
        originalTitle = "Disquisitiones generales circa seriem infinitam (1812)",
        author = "Carl Friedrich Gauss",
        year = 1812,
        summary = "Gauss compared each term of the hypergeometric series with the next and read convergence from the ratio, which the program prints for a short series.",
        details = "Gauss's 1812 memoir studied the series that now bears the name hypergeometric and used the ratio of neighbouring terms to judge convergence. The example runs the series with the parameters one, one and two, and x equal to one half, printing the terms, the ratios and the partial sums in exact fractions.",
        instructions = """
            1. Save the example as gauss_ratio.py.
            2. Run it: python3 gauss_ratio.py
        """,
        example = """
            from fractions import Fraction

            a, b, c = 1, 1, 2
            x = Fraction(1, 2)
            term = Fraction(1)
            total = Fraction(0)
            for n in range(5):
                total += term
                ratio = Fraction((a + n) * (b + n), (c + n) * (n + 1)) * x
                print(f"term {n}: {term}, partial sum {total}, next ratio {ratio}")
                term = term * ratio
            print("ratio tends to", float(x), "so the series converges")
        """,
    ),
)
