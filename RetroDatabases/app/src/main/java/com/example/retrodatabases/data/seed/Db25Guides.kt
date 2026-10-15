package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1790 to 1800: prime counts, a mean that gives a lemniscate, interpolation, tables by differences and regular polygons. */
val db25Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.GUIDE,
        section = "Prime numbers",
        title = "Gauss's guess for the prime count",
        originalTitle = "Letter to Encke of 24 December 1849, on work begun in 1792 or 1793",
        author = "Carl Friedrich Gauss",
        year = 1792,
        summary = "The number of primes below x is about x divided by the natural log of x, and the program checks that guess against counted primes.",
        details = "Gauss wrote in his 1849 letter to Encke that he had the idea in 1792 or 1793, and he kept tables of primes that he never printed. The example counts primes below powers of ten with a sieve and sets each count beside x divided by ln x.",
        instructions = """
            1. Save the example as gauss_primes.py.
            2. Run it: python3 gauss_primes.py
        """,
        example = """
            import math

            def count_primes(limit):
                # Sieve of Eratosthenes: True marks a prime.
                sieve = [True] * (limit + 1)
                sieve[0] = sieve[1] = False
                for i in range(2, int(limit ** 0.5) + 1):
                    if sieve[i]:
                        for j in range(i * i, limit + 1, i):
                            sieve[j] = False
                return sum(sieve)

            for k in range(1, 7):
                x = 10 ** k
                guess = x / math.log(x)
                print(f"x = 10^{k}: primes {count_primes(x)}, x/ln x = {guess:.1f}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.GUIDE,
        section = "Means",
        title = "Gauss's arithmetic-geometric mean",
        originalTitle = "Diary note on the mean and the lemniscate (1799)",
        author = "Carl Friedrich Gauss",
        year = 1799,
        summary = "Repeating the arithmetic and geometric means of two numbers closes in on a limit, which Gauss used to compute the lemniscate.",
        details = "Gauss's mathematical diary holds short notes on the mean, and the entry that ties it to the lemniscate is usually dated 1799, which this record takes. The example iterates the two means of 1 and the square root of 2 and prints each step to ten decimals.",
        instructions = """
            1. Save the example as agm.py.
            2. Run it: python3 agm.py
        """,
        example = """
            import math

            a, b = 1.0, math.sqrt(2.0)
            for step in range(1, 6):
                # Both new values are computed from the old pair.
                a, b = (a + b) / 2, math.sqrt(a * b)
                print(f"step {step}: a = {a:.10f}, b = {b:.10f}")
            print(f"AGM(1, sqrt 2) = {a:.10f}")
            print(f"constant 1/AGM = {1 / a:.10f}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.GUIDE,
        section = "Interpolation",
        title = "Lagrange's interpolation formula",
        originalTitle = "Lecons elementaires sur les mathematiques (1795)",
        author = "Joseph-Louis Lagrange",
        year = 1795,
        summary = "A polynomial through given points can be written as a sum of terms, one per point, each built from the other points.",
        details = "Lagrange gave the formula in his lectures at the Ecole normale in 1795, and the course was printed that year. The example fits the parabola through three points with exact fractions and evaluates it at new points.",
        instructions = """
            1. Save the example as lagrange.py.
            2. Run it: python3 lagrange.py
        """,
        example = """
            from fractions import Fraction

            points = [(Fraction(0), Fraction(1)), (Fraction(1), Fraction(3)), (Fraction(2), Fraction(7))]

            def lagrange(x):
                # Each term is the value at one point, times a factor that is 1 at that point and 0 at the others.
                total = Fraction(0)
                for i, (xi, yi) in enumerate(points):
                    term = yi
                    for j, (xj, _) in enumerate(points):
                        if j != i:
                            term *= (x - xj) / (xi - xj)
                    total += term
                return total

            for x in (Fraction(3), Fraction(4), Fraction(1, 2)):
                print(f"f({x}) = {lagrange(x)}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.GUIDE,
        section = "Tables",
        title = "Squares by the method of differences",
        originalTitle = "Method of differences for tables of squares (1795)",
        author = "Gaspard de Prony",
        year = 1795,
        summary = "When the second differences of a sequence are constant, every later value follows by additions alone, which is the method Prony's computing office used.",
        details = "Prony's computing office relied on the method of differences, which goes back to the seventeenth century, so that a table could be built with additions and no multiplications. The example builds the squares from 1 to 100 from the constant second difference of 2 and checks them against multiplication.",
        instructions = """
            1. Save the example as differences.py.
            2. Run it: python3 differences.py
        """,
        example = """
            # The second difference of the squares is always 2, so each first difference grows by 2.
            value, first, second = 0, 1, 2
            row = []
            for n in range(1, 11):
                value += first
                first += second
                row.append(value)
            print("squares by differences: " + " ".join(str(v) for v in row))
            print("check: " + str(all(row[i] == (i + 1) ** 2 for i in range(10))))
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.GUIDE,
        section = "Constructions",
        title = "Gauss's test for constructible polygons",
        originalTitle = "Diary entry on the regular 17-gon (30 March 1796)",
        author = "Carl Friedrich Gauss",
        year = 1796,
        summary = "A regular polygon can be drawn with ruler and compass when its number of sides is a power of two times distinct Fermat primes, and the program lists those sides up to 40.",
        details = "Gauss recorded the construction of the regular 17-gon in his diary on 30 March 1796, and the general test was printed later. Wantzel proved in 1837 that no other regular polygons can be drawn, so the test is complete.",
        instructions = """
            1. Save the example as polygons.py.
            2. Run it: python3 polygons.py
        """,
        example = """
            def is_fermat_prime(p):
                return p in (3, 5, 17, 257, 65537)

            def constructible(n):
                # Remove the factors of two, then factor the odd part by trial division.
                m = n
                while m % 2 == 0:
                    m //= 2
                primes = []
                d = 3
                while d * d <= m:
                    while m % d == 0:
                        primes.append(d)
                        m //= d
                    d += 2
                if m > 1:
                    primes.append(m)
                return len(primes) == len(set(primes)) and all(is_fermat_prime(p) for p in primes)

            good = [n for n in range(3, 41) if constructible(n)]
            print("constructible polygon sides up to 40: " + " ".join(str(n) for n in good))
            print("count: " + str(len(good)))
        """,
    ),
)
