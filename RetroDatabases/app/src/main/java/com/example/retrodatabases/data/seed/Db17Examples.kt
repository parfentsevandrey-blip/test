package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1870 to 1880: short programs that check the series, probability, inequality and prime-sum results of the decade. */
val db17Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Series for e",
        title = "Partial sums of e, the start of Hermite's proof",
        originalTitle = "Sur la fonction exponentielle (1873)",
        author = "Charles Hermite",
        year = 1873,
        summary = "The partial sums of the series for e are exact fractions, and their error shrinks fast enough to start a proof that e is transcendental.",
        details = "Hermite's 1873 paper proved that e is transcendental, by building rational approximations to e from its series. The example prints the first six partial sums as exact fractions and compares the last one with e.",
        instructions = """
            1. Save the example as hermite.py.
            2. Run it: python3 hermite.py
        """,
        example = """
            from fractions import Fraction
            from math import factorial

            partial = Fraction(0)
            for k in range(6):
                partial += Fraction(1, factorial(k))
                print(f"S_{k} = {partial} = {float(partial):.6f}")
            e = 2.718281828459045
            print(f"error of S_5 against e: {e - float(partial):.6f}")
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Statistical mechanics",
        title = "Boltzmann's H function, a two-state toy",
        originalTitle = "Weitere Studien uber das Warmegleichgewicht unter Gasmolekulen (1872)",
        author = "Ludwig Boltzmann",
        year = 1872,
        summary = "A quantity H built from the probabilities of the states never increases in a toy model, which is a discrete picture of the H theorem.",
        details = "Boltzmann's 1872 H theorem argued that a gas tends toward equilibrium, and that a quantity H never increases along the way. The example runs a two-state chain with a symmetric transition matrix, which is only an analogy for a gas, and prints H at each step.",
        instructions = """
            1. Save the example as boltzmann_h.py.
            2. Run it: python3 boltzmann_h.py
        """,
        example = """
            from fractions import Fraction
            from math import log

            # A symmetric transition matrix whose columns sum to one, so the total probability stays 1.
            M = [[Fraction(3, 4), Fraction(1, 4)], [Fraction(1, 4), Fraction(3, 4)]]

            def step(p):
                return [sum(M[i][j] * p[j] for j in range(2)) for i in range(2)]

            def H(p):
                return sum(float(x) * log(float(x)) for x in p if x > 0)

            p = [Fraction(1), Fraction(0)]
            history = []
            for t in range(4):
                value = H(p)
                history.append(value)
                print(f"step {t}: distribution ({p[0]}, {p[1]}), H = {value:.3f}")
                p = step(p)
            print("H never increases:", all(history[i + 1] <= history[i] for i in range(len(history) - 1)))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Statistical mechanics",
        title = "Boltzmann's entropy from counting microstates",
        originalTitle = "Uber die Beziehung zwischen dem zweiten Hauptsatze der mechanischen Warmetheorie und der Wahrscheinlichkeitsrechnung (1877)",
        author = "Ludwig Boltzmann",
        year = 1877,
        summary = "The entropy of a state is k times the logarithm of the number of microstates that give it, so the most probable split is the one with the most microstates.",
        details = "Boltzmann's 1877 paper connected the second law of thermodynamics with probability, and wrote the entropy as k log W, where W counts the microstates. The example counts the ways four particles can split between the two halves of a box and finds the most probable split.",
        instructions = """
            1. Save the example as boltzmann_w.py.
            2. Run it: python3 boltzmann_w.py
        """,
        example = """
            from math import comb, log

            N = 4  # particles, each in the left or the right half of a box
            total = 0
            for n in range(N + 1):
                W = comb(N, n)  # microstates with n particles on the left
                total += W
                print(f"n = {n} on the left: W = {W}")
            print("total microstates equal 2 to the power N:", total == 2 ** N)
            best = max(range(N + 1), key=lambda n: comb(N, n))
            print(f"most probable: n = {best}, S/k = ln W = {log(comb(N, best)):.3f}")
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Inequalities",
        title = "Lipschitz constant of x squared on a grid",
        originalTitle = "Untersuchungen in Betreff der ganzen homogenen Functionen von beliebig vielen Differentialen (1876)",
        author = "Rudolf Lipschitz",
        year = 1876,
        summary = "A function is Lipschitz when the change between two values is at most a fixed constant times the distance between the points.",
        details = "Lipschitz's 1876 work introduced the condition, which later became the standard hypothesis for proving that differential equations have solutions. The example measures the largest slope between points of a grid for x squared on [0, 2] and compares it with the bound 4 from the derivative.",
        instructions = """
            1. Save the example as lipschitz.py.
            2. Run it: python3 lipschitz.py
        """,
        example = """
            from fractions import Fraction
            from itertools import combinations

            def f(x):
                return x * x

            grid = [Fraction(i, 2) for i in range(5)]  # the points 0, 1/2, 1, 3/2 and 2
            slopes = [Fraction(abs(f(x) - f(y)), abs(x - y)) for x, y in combinations(grid, 2)]
            print("largest grid slope:", max(slopes))
            print("bound from the derivative 2x on [0, 2]:", 2 * max(grid))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Series",
        title = "Weierstrass's series at x = 0",
        originalTitle = "Uber continuirliche Functionen eines reellen Arguments (1872)",
        author = "Karl Weierstrass",
        year = 1872,
        summary = "Weierstrass's 1872 series of cosines is continuous everywhere but has no derivative anywhere, and at x = 0 its partial sums are geometric.",
        details = "Weierstrass presented in 1872 a continuous function with no derivative, built as a sum of cosines whose frequencies grow faster than their amplitudes shrink. The example uses amplitude one half and prints the partial sums at x = 0, which are exact geometric sums.",
        instructions = """
            1. Save the example as weierstrass.py.
            2. Run it: python3 weierstrass.py
        """,
        example = """
            from fractions import Fraction

            a = Fraction(1, 2)  # amplitude factor, 0 < a < 1
            total = Fraction(0)
            for n in range(5):
                total += a ** n  # at x = 0 every cosine term equals 1
                print(f"N={n}: {total}")
            limit = 1 / (1 - a)
            tail = a ** 5 / (1 - a)  # bound for the part of the series after N = 4
            print("limit at x = 0:", limit)
            print("tail bound after N = 4 equals 1/16:", tail == Fraction(1, 16))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.EXAMPLE,
        section = "Primes",
        title = "Mertens's sums of ln p over p",
        originalTitle = "Uber einige asymptotische Gesetze der Zahlentheorie (1874)",
        author = "Franz Mertens",
        year = 1874,
        summary = "The sum of ln p over p across the primes up to x stays within a bounded distance of ln x.",
        details = "Mertens published his asymptotic laws for the primes in 1874. The example computes the sum for the primes up to 10 and up to 20 and prints its difference from ln x, which stays near minus one.",
        instructions = """
            1. Save the example as mertens.py.
            2. Run it: python3 mertens.py
        """,
        example = """
            from math import log

            def primes_up_to(n):
                return [p for p in range(2, n + 1) if all(p % d for d in range(2, int(p ** 0.5) + 1))]

            for x in (10, 20):
                total = sum(log(p) / p for p in primes_up_to(x))
                print(f"x = {x}: sum of ln p / p = {total:.3f}, ln x = {log(x):.3f}, difference = {total - log(x):.3f}")
        """,
    ),
)
