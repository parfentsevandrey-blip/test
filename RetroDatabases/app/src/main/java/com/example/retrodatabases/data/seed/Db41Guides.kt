package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1630 to 1640: slices of a parabola, the signs of a polynomial, sums of powers, a cycloid's area and a slope by adequality. */
val db41Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How Cavalieri's indivisibles compare slices of two regions",
        originalTitle = "Geometria indivisibilibus continuorum nova quadam ratione promota",
        author = "Bonaventura Cavalieri",
        year = 1635,
        summary = "Cavalieri's method of indivisibles treats a region as a stack of slices and compares two regions by pairing their slices, so that sums of the slices give the areas.",
        details = "Cavalieri published the method at Bologna in 1635, and it gave the area under a parabola by summing the squares of its slices. The example sums the squares of the first slices and divides by the cube of their number, so the ratios approach one third.",
        instructions = """
            1. Save the example as cavalieri_slices.py.
            2. Run it: python3 cavalieri_slices.py
        """,
        example = """
            from fractions import Fraction

            for n in range(1, 6):
                # Sum of the squares of the first n slices, compared with the cube of n.
                total = sum(k * k for k in range(1, n + 1))
                print(f"slices {n}: {Fraction(total, n ** 3)}")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Descartes's rule of signs counts the positive roots",
        originalTitle = "La Geometrie",
        author = "Rene Descartes",
        year = 1637,
        summary = "Descartes's rule of signs bounds the number of positive and negative roots of a polynomial by counting the changes of sign in its coefficients.",
        details = "Descartes stated the rule in La Geometrie of 1637, and it gives an upper bound that is often exact. The example applies the count to a cubic and to its reflection, which shows at most two positive roots and at most one negative root for the same cubic.",
        instructions = """
            1. Save the example as sign_rule.py.
            2. Run it: python3 sign_rule.py
        """,
        example = """
            def sign_changes(coefficients):
                # Zero coefficients are skipped; each change of sign between neighbours counts once.
                nonzero = [c for c in coefficients if c != 0]
                return sum(1 for a, b in zip(nonzero, nonzero[1:]) if (a > 0) != (b > 0))

            positive = [1, -1, -4, 4]    # x^3 - x^2 - 4x + 4, highest power first
            negative = [-1, -1, 4, 4]    # the same polynomial with x replaced by -x
            p = sign_changes(positive)
            q = sign_changes(negative)
            print(f"sign changes in f(x): {p}, so at most {p} positive root(s)")
            print(f"sign changes in f(-x): {q}, so at most {q} negative root(s)")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Faulhaber's formulas give the sums of powers",
        originalTitle = "Academia algebrae",
        author = "Johann Faulhaber",
        year = 1631,
        summary = "Faulhaber's formulas of 1631 give the sum of the kth powers of the first n whole numbers as a polynomial in n, without adding the terms one by one.",
        details = "Faulhaber printed formulas of this kind in his Academia algebrae at Augsburg in 1631, reaching to much higher powers than the squares and cubes. The example checks his formulas for the squares, cubes and fourth powers against direct sums, and the two columns agree.",
        instructions = """
            1. Save the example as faulhaber.py.
            2. Run it: python3 faulhaber.py
        """,
        example = """
            def power_sum(k, n):
                return sum(i ** k for i in range(1, n + 1))

            print("sum of squares to 4:", power_sum(2, 4), "; Faulhaber:", 4 * 5 * 9 // 6)
            print("sum of cubes to 4:", power_sum(3, 4), "; square of 1+2+3+4:", (4 * 5 // 2) ** 2)
            print("sum of fourth powers to 3:", power_sum(4, 3), "; Faulhaber:", 3 * 4 * 7 * (3 * 9 + 3 * 3 - 1) // 30)
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.GUIDE,
        section = "Curves",
        title = "How Roberval's slices find the area under a cycloid arch",
        originalTitle = "Area of the cycloid arch",
        author = "Gilles Personne de Roberval",
        year = 1634,
        summary = "Roberval found that the area under one arch of a cycloid is three times the area of the circle that rolls to make it, a result he reached by slicing the arch into thin strips.",
        details = "Roberval's result is dated 1634 here, though accounts differ on when it was first circulated. The example adds up thin strips of the arch numerically and divides by the area of the circle, so the ratio comes out at three.",
        instructions = """
            1. Save the example as cycloid_arch.py.
            2. Run it: python3 cycloid_arch.py
        """,
        example = """
            import math

            N = 10000
            dt = 2 * math.pi / N
            # Along the arch x = t - sin t and y = 1 - cos t, so the area is the sum of y times dx/dt over the steps.
            area = sum((1 - math.cos((i + 0.5) * dt)) ** 2 * dt for i in range(N))
            print(f"area under one arch divided by the circle's area: {area / math.pi:.2f}")
            print(f"area itself with r = 1: {area:.2f}")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Fermat's adequality finds the slope of a power curve",
        originalTitle = "Methodus ad disquirendam maximam et minimam",
        author = "Pierre de Fermat",
        year = 1636,
        summary = "Fermat's adequality finds the slope of a curve by letting a small step e enter the expression and then dropping the powers of e, which gives the slope of y = x^n as n times x to the power n minus one.",
        details = "The method was written up around 1636 and circulated in manuscript, and accounts differ on the dates of its first versions. The example expands x plus e to the power n, keeps the coefficient of the first power of e, and prints the expansion for two cases so that the slope can be read off.",
        instructions = """
            1. Save the example as adequality.py.
            2. Run it: python3 adequality.py
        """,
        example = """
            from math import comb

            def expansion(n, x):
                # Coefficient of e to the power k in (x + e) to the power n, for k from 0 to n.
                return [comb(n, k) * x ** (n - k) for k in range(n + 1)]

            for n, x in ((3, 2), (4, 3)):
                coeffs = expansion(n, x)
                print(f"(x + e)^{n} at x = {x}, by powers of e: {coeffs}")
                # Fermat drops every power of e above the first; the first-power coefficient is the slope.
                print(f"slope of y = x^{n} at x = {x}: {coeffs[1]}")
        """,
    ),

)
