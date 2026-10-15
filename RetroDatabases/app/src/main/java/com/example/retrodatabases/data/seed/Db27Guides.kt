package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1770 to 1780: determinants of powers, a test for primes, the probability of causes, Buffon's needle and the equilateral points of three bodies. */
val db27Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.GUIDE,
        section = "Determinants",
        title = "How Vandermonde's determinant gives a product of differences",
        originalTitle = "Memoire sur l'elimination",
        author = "Alexandre-Theophile Vandermonde",
        year = 1772,
        summary = "The determinant of the matrix whose rows are the powers of distinct numbers equals the product of all their differences, which the program checks for two sets of points.",
        details = "The identity is named after Vandermonde, but his memoir on elimination does not state the product formula explicitly, and accounts differ on its dates of reading and printing, which fall between 1771 and 1776. The example computes the determinant by the permutation formula for the points 1, 3, 4 and for the points 1, 2, 3, 4, and compares each with the product of differences. The two values agree, 6 and 12.",
        instructions = """
            1. Save the example as vandermonde.py.
            2. Run it: python3 vandermonde.py
        """,
        example = """
            from itertools import permutations

            def sign(perm):
                # The parity of a permutation, counted by its inversions, gives the sign of its term.
                inversions = sum(1 for i in range(len(perm)) for j in range(i + 1, len(perm)) if perm[i] > perm[j])
                return -1 if inversions % 2 else 1

            def det(m):
                # Leibniz formula: the sum over all permutations of the signed product down the chosen positions.
                n = len(m)
                total = 0
                for perm in permutations(range(n)):
                    term = sign(perm)
                    for i in range(n):
                        term *= m[i][perm[i]]
                    total += term
                return total

            def vandermonde_matrix(xs):
                # Row i holds the powers 1, x, x squared, ... of the i-th number.
                return [[x ** k for k in range(len(xs))] for x in xs]

            def product_of_differences(xs):
                # The product of x_j minus x_i over all pairs i less than j.
                out = 1
                for i in range(len(xs)):
                    for j in range(i + 1, len(xs)):
                        out *= xs[j] - xs[i]
                return out

            for xs in ([1, 3, 4], [1, 2, 3, 4]):
                d = det(vandermonde_matrix(xs))
                p = product_of_differences(xs)
                print(f"points {xs}: determinant {d}, product of differences {p}, equal: {d == p}")
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Wilson's theorem tests a number for primality",
        originalTitle = "Meditationes Algebraicae",
        author = "John Wilson, stated by Edward Waring",
        year = 1770,
        summary = "A number above one is prime exactly when the factorial of one less than it leaves remainder one less than it on division, which the program checks for small numbers.",
        details = "Waring published the result in 1770 and credited it to his pupil John Wilson, without a proof. Lagrange gave the first demonstration in 1771. The example tests every number from 2 to 30 by the remainder of its factorial of one less, prints the primes it finds, and prints three sample remainders.",
        instructions = """
            1. Save the example as wilson.py.
            2. Run it: python3 wilson.py
        """,
        example = """
            from math import factorial

            def wilson_prime(n):
                # Wilson's test: n is prime exactly when (n - 1)! leaves remainder n - 1 on division by n.
                return factorial(n - 1) % n == n - 1

            primes = [n for n in range(2, 31) if wilson_prime(n)]
            print("primes up to 30 by Wilson's test:", primes)
            for n in (4, 7, 9):
                print(f"remainder of {n - 1}! mod {n}:", factorial(n - 1) % n)
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Laplace's probability of causes reverses a chance",
        originalTitle = "Memoire sur la probabilite des causes par les evenemens",
        author = "Pierre-Simon Laplace",
        year = 1774,
        summary = "Given the prior chance of each cause and the chance of the event under each cause, the program finds the chance of each cause after the event has been seen.",
        details = "Laplace's memoir was printed in the Academy's volume for 1774, and one commentary places its writing between March and December 1773, so the printed year is used here. It sets out how to reverse conditional chances, working from an observed event back to the causes that may have produced it. The example uses three causes with prior chances 1/2, 1/3 and 1/6 and event chances 1/5, 1/2 and 4/5, and finds the chances after the event as exact fractions, 1/4, 5/12 and 1/3.",
        instructions = """
            1. Save the example as causes.py.
            2. Run it: python3 causes.py
        """,
        example = """
            from fractions import Fraction as F

            causes = ["cause A", "cause B", "cause C"]
            prior = [F(1, 2), F(1, 3), F(1, 6)]    # chance of each cause before the event
            given = [F(1, 5), F(1, 2), F(4, 5)]    # chance of the event under each cause

            joint = [p * g for p, g in zip(prior, given)]
            total = sum(joint)                     # chance of the event under any cause
            for name, j in zip(causes, joint):
                print(f"{name}: {j / total}")
            print("total of the posterior chances:", sum(j / total for j in joint))
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.GUIDE,
        section = "Geometric probability",
        title = "How Buffon's needle estimates pi from crossing chances",
        originalTitle = "Essai d'arithmetique morale",
        author = "Georges-Louis Leclerc, comte de Buffon",
        year = 1777,
        summary = "A needle no longer than the spacing of the lines crosses a line with a chance equal to the average of the sine of its angle, which the program averages to estimate pi.",
        details = "Buffon published the problem in his Essai d'arithmetique morale of 1777, though accounts differ on how early he first posed it. For a needle as long as the line spacing the chance of a crossing is 2 over pi, and the example replaces random throws by a midpoint average of the sine over equal steps of the angle. The estimate for n steps is 2n times the sine of pi over 2n, and the program prints it for 2, 4, 8 and 16 steps, which approach pi slowly.",
        instructions = """
            1. Save the example as buffon_quadrature.py.
            2. Run it: python3 buffon_quadrature.py
        """,
        example = """
            from math import sin, pi

            def estimate(n):
                # Midpoint average of the crossing chance sin(theta) over n equal steps of the angle from 0 to pi.
                steps = [(k + 0.5) * pi / n for k in range(n)]
                average = sum(sin(t) for t in steps) / n    # tends to 2 over pi, the chance of a crossing
                return 2 / average                          # the estimate of pi

            for n in (2, 4, 8, 16):
                print(f"steps {n}: pi is about {estimate(n):.4f}")
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.GUIDE,
        section = "Celestial mechanics",
        title = "How Lagrange's equilateral points hold a small body",
        originalTitle = "Essai sur le probleme des trois corps",
        author = "Joseph-Louis Lagrange",
        year = 1772,
        summary = "A small body at the apex of an equilateral triangle on two larger bodies can stay in equilibrium, and the program applies the stability test usually credited to Routh to two mass ratios.",
        details = "Lagrange's prize essay of 1772 on the three-body problem found that a small body at the apex of an equilateral triangle built on the two larger bodies can stay in equilibrium. The stability test used here is later, and it is usually credited to Routh; it sets a condition on the mass ratio. The example checks two ratios, one thousandth and one half, with exact fractions.",
        instructions = """
            1. Save the example as lagrange_points.py.
            2. Run it: python3 lagrange_points.py
        """,
        example = """
            from fractions import Fraction as F

            def stable(mu):
                # The stability test for the equilateral points: 27 times mu times (1 minus mu) must be below one.
                value = 27 * mu * (1 - mu)
                return value, value < 1

            for mu in (F(1, 1000), F(1, 2)):
                value, ok = stable(mu)
                print(f"mass ratio {mu}: 27 mu (1 - mu) = {value}, stable: {ok}")
        """,
    ),

)
