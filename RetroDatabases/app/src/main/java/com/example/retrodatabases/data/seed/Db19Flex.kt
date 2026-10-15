package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1850 to 1860: a polynomial recurrence, a diatomic sequence and a count of triple systems. */
val db19Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.FLEX,
        section = "Polynomials",
        title = "How Chebyshev's recurrence builds T_n",
        originalTitle = "Theorie des mecanismes connus sous le nom de parallelogrammes (1854)",
        author = "Pafnuty Chebyshev",
        year = 1854,
        summary = "Chebyshev's polynomials satisfy T_n(cos t) = cos(n t), and the recurrence T_(n+1) = 2x T_n - T_(n-1) builds them exactly.",
        details = "Chebyshev introduced these polynomials in his work on linkages, and they became central to approximation theory. The example builds T_0 to T_5 as exact coefficient lists and evaluates them at 1/2, where the values are the cosines of multiples of 60 degrees.",
        instructions = """
            1. Save the example as chebyshev_polynomials.py.
            2. Run it: python3 chebyshev_polynomials.py
        """,
        example = """
            from fractions import Fraction

            # Coefficient lists, lowest power first: T_0 = 1, T_1 = x, and T_(n+1) = 2x T_n - T_(n-1).
            def mul_x(p):
                return [Fraction(0)] + p

            def chebyshev(n):
                t_prev = [Fraction(1)]
                t_cur = [Fraction(0), Fraction(1)]
                if n == 0:
                    return t_prev
                for _ in range(n - 1):
                    nxt = [2 * c for c in mul_x(t_cur)]
                    for i, c in enumerate(t_prev):
                        nxt[i] -= c
                    t_prev, t_cur = t_cur, nxt
                return t_cur

            for n in range(6):
                print(f"T_{n}:", " ".join(str(c) for c in chebyshev(n)))

            def evaluate(coeffs, x):
                return sum(c * x ** i for i, c in enumerate(coeffs))

            half = Fraction(1, 2)
            values = [evaluate(chebyshev(n), half) for n in range(6)]
            print("T_n(1/2) for n = 0..5:", " ".join(str(v) for v in values))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "Stern's diatomic sequence",
        originalTitle = "Ueber eine zahlentheoretische Funktion (1858)",
        author = "Moritz Abraham Stern",
        year = 1858,
        summary = "Stern's diatomic sequence is given by s(2n) = s(n) and s(2n+1) = s(n) + s(n+1), and it is closely tied to the Stern-Brocot tree of fractions.",
        details = "Stern published his function in Crelle's journal in 1858, and the Stern-Brocot tree of fractions, described by Brocot in 1861, is built from the same recurrence. The example prints the first sixteen values and checks two families of values.",
        rare = true,
        instructions = """
            1. Save the example as stern.py.
            2. Run it: python3 stern.py
        """,
        example = """
            def stern(n):
                # s(0) = 0, s(1) = 1, s(2n) = s(n), s(2n + 1) = s(n) + s(n + 1)
                if n < 2:
                    return n
                if n % 2 == 0:
                    return stern(n // 2)
                return stern(n // 2) + stern(n // 2 + 1)

            values = [stern(n) for n in range(16)]
            print("s(0) to s(15):", " ".join(str(v) for v in values))
            print("s(2^k + 1) = k + 1 for k = 1..6:", all(stern(2 ** k + 1) == k + 1 for k in range(1, 7)))
            print("s(2^k) = 1 for k = 0..9:", all(stern(2 ** k) == 1 for k in range(0, 10)))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.FLEX,
        section = "Triple systems",
        title = "Steiner triple systems on nine points",
        originalTitle = "Combinatorische Aufgabe (1853)",
        author = "Jakob Steiner",
        year = 1853,
        summary = "A Steiner triple system on nine points is a set of triples in which every pair of points lies in exactly one triple, and the example counts the labelled systems.",
        details = "Steiner posed the combinatorial problem in 1853, and Kirkman had studied the same triple systems in 1847. The example counts the labelled systems on nine points by backtracking and finds 840.",
        instructions = """
            1. Save the example as steiner_triples.py.
            2. Run it: python3 steiner_triples.py
        """,
        example = """
            from itertools import combinations

            POINTS = range(9)
            PAIRS = {frozenset(p) for p in combinations(POINTS, 2)}
            print("pairs of 9 points:", len(PAIRS))

            def count_systems():
                # Each system contains exactly one triple through the smallest uncovered pair, so each system is counted once.
                total = 0

                def extend(covered):
                    nonlocal total
                    uncovered = PAIRS - covered
                    if not uncovered:
                        total += 1
                        return
                    a, b = min(tuple(sorted(p)) for p in uncovered)
                    for c in POINTS:
                        if c in (a, b):
                            continue
                        new = {frozenset((a, b)), frozenset((a, c)), frozenset((b, c))}
                        if new & covered:
                            continue
                        extend(covered | new)

                extend(set())
                return total

            print("labelled Steiner triple systems on 9 points:", count_systems())
        """,
    ),
)
