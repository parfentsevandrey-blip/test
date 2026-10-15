package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1890 to 1900: averages of divergent sums, determinants, quaternion units, lattice areas and prime divisors. */
val db15Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Summability",
        title = "Cesaro means of Grandi's series",
        originalTitle = "Sur la multiplication des series (1890)",
        author = "Ernesto Cesaro",
        year = 1890,
        summary = "Averaging the partial sums of 1 minus 1 plus 1 minus 1 gives 1/2, although the partial sums themselves never settle.",
        details = "Cesaro introduced this averaging in 1890, and it assigns a value to some series that do not converge in the ordinary sense. The example prints the first six means and the mean at n = 100, all in exact fractions.",
        instructions = """
            1. Save the example as cesaro.py.
            2. Run it: python3 cesaro.py
        """,
        example = """
            from fractions import Fraction

            def partial_sum(n):
                # Partial sum of 1 - 1 + 1 - 1 ... with n terms.
                return 1 if n % 2 == 1 else 0

            def cesaro_mean(n):
                return Fraction(sum(partial_sum(k) for k in range(1, n + 1)), n)

            print("Cesaro means for n = 1..6:", " ".join(str(cesaro_mean(n)) for n in range(1, 7)))
            print("Cesaro mean at n = 100:", cesaro_mean(100))
            print("raw partial sums are 0 or 1:", all(partial_sum(n) in (0, 1) for n in range(1, 101)))
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Determinants",
        title = "Hadamard's bound on a 3 by 3 determinant",
        originalTitle = "Resolution d'une question relative aux determinants (1893)",
        author = "Jacques Hadamard",
        year = 1893,
        summary = "The square of a determinant never exceeds the product of the squared column lengths, and the example checks this for a 3 by 3 matrix.",
        details = "Hadamard's 1893 note proved an inequality that bounds a determinant by the lengths of its columns. The example computes the determinant of a 3 by 3 matrix exactly and compares its square with the product of the squared column lengths.",
        instructions = """
            1. Save the example as hadamard.py.
            2. Run it: python3 hadamard.py
        """,
        example = """
            A = [[1, 2, 3], [4, 5, 6], [7, 8, 10]]

            def det3(m):
                # Expansion along the first row.
                return (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                        - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                        + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))

            d = det3(A)
            print("det =", d)
            bound = 1
            for j in range(3):
                column = sum(A[i][j] ** 2 for i in range(3))
                bound *= column
                print(f"column {j + 1} squared length {column}")
            print("product of squared column lengths:", bound)
            print("Hadamard inequality det^2 <= product holds:", d * d <= bound)
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Matrices",
        title = "Determinants of the Hilbert matrix",
        originalTitle = "Ein Beitrag zur Theorie des Legendre'schen Polynoms (1894)",
        author = "David Hilbert",
        year = 1894,
        summary = "The matrix with entries 1 over i plus j minus 1 has determinants that shrink fast, and the 3 by 3 case is exactly 1/2160.",
        details = "Hilbert's 1894 paper on Legendre polynomials used this matrix, and its leading determinants are rationals that can be computed exactly. The example runs Gaussian elimination in fractions and prints the determinants of the leading 1 by 1, 2 by 2 and 3 by 3 blocks.",
        instructions = """
            1. Save the example as hilbert_matrix.py.
            2. Run it: python3 hilbert_matrix.py
        """,
        example = """
            from fractions import Fraction as F

            def hilbert(n):
                # Entry (i, j) is 1 / (i + j - 1) with 1-based indices, here written with 0-based i and j.
                return [[F(1, i + j + 1) for j in range(n)] for i in range(n)]

            def det(m):
                # Gaussian elimination in exact fractions.
                m = [row[:] for row in m]
                size = len(m)
                result = F(1)
                for c in range(size):
                    pivot = next((r for r in range(c, size) if m[r][c] != 0), None)
                    if pivot is None:
                        return F(0)
                    if pivot != c:
                        m[c], m[pivot] = m[pivot], m[c]
                        result = -result
                    result *= m[c][c]
                    for r in range(c + 1, size):
                        factor = m[r][c] / m[c][c]
                        for k in range(c, size):
                            m[r][k] -= factor * m[c][k]
                return result

            for n in (1, 2, 3):
                print(f"det H{n} = {det(hilbert(n))}")
            print("ratio H3 / H2 =", det(hilbert(3)) / det(hilbert(2)))
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Quaternions",
        title = "Hurwitz quaternion units counted by brute force",
        originalTitle = "Ueber die Zahlentheorie der Quaternionen (1896)",
        author = "Adolf Hurwitz",
        year = 1896,
        summary = "Quaternions whose four coordinates are all whole numbers or all half-whole numbers include exactly 24 units of norm one, and the program counts them.",
        details = "Hurwitz's 1896 work on the arithmetic of quaternions introduced this ring of integers. The example counts the solutions with doubled coordinates, which split into 8 whole-coordinate units and 16 half-integer units.",
        rare = true,
        instructions = """
            1. Save the example as hurwitz_units.py.
            2. Run it: python3 hurwitz_units.py
        """,
        example = """
            from itertools import product

            units = []
            for coords in product(range(-2, 3), repeat=4):
                # Doubled coordinates: all even or all odd, and the doubled norm is 4, so the norm is 1.
                same_parity = len({x % 2 for x in coords}) == 1
                if same_parity and sum(x * x for x in coords) == 4:
                    units.append(coords)
            print("Hurwitz units (doubled coordinates):", len(units))
            print("Lipschitz units (all even):", sum(1 for u in units if u[0] % 2 == 0))
            print("half-integer units (all odd):", sum(1 for u in units if u[0] % 2 == 1))
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Lattice geometry",
        title = "Pick's theorem checked on a small triangle",
        originalTitle = "Geometrisches zur Zahlenlehre (1899)",
        author = "Georg Pick",
        year = 1899,
        summary = "The area of a lattice polygon equals its interior lattice points plus half its boundary lattice points minus one, and the example checks this on a triangle.",
        details = "Pick's 1899 note stated this formula for polygons whose corners are lattice points. The example counts boundary points from the gcd of each edge's differences, counts interior points directly, and compares the result with the shoelace area.",
        instructions = """
            1. Save the example as pick.py.
            2. Run it: python3 pick.py
        """,
        example = """
            from fractions import Fraction
            from math import gcd

            poly = [(0, 0), (4, 0), (0, 3)]  # corners in counter-clockwise order
            n = len(poly)
            twice_area = sum(poly[i][0] * poly[(i + 1) % n][1] - poly[(i + 1) % n][0] * poly[i][1] for i in range(n))
            area = Fraction(abs(twice_area), 2)
            boundary = sum(gcd(abs(poly[(i + 1) % n][0] - poly[i][0]), abs(poly[(i + 1) % n][1] - poly[i][1])) for i in range(n))

            def strictly_inside(p):
                # A point is strictly inside a counter-clockwise convex polygon when every edge has it on its left.
                for i in range(n):
                    (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
                    if (x2 - x1) * (p[1] - y1) - (y2 - y1) * (p[0] - x1) <= 0:
                        return False
                return True

            interior = sum(1 for x in range(0, 5) for y in range(0, 4) if strictly_inside((x, y)))
            print("area =", area)
            print("boundary points B =", boundary)
            print("interior points I =", interior)
            print("Pick: I + B/2 - 1 =", interior + Fraction(boundary, 2) - 1)
            print("Pick agrees with area:", area == interior + Fraction(boundary, 2) - 1)
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Primitive prime divisors of 2 to the n minus 1",
        originalTitle = "Zur Theorie der Potenzreste (1892)",
        author = "Karl Zsigmondy",
        year = 1892,
        summary = "Each power of 2 minus 1 has, with few exceptions, a prime factor that divides no smaller power, and the case n = 6 is one of the exceptions.",
        details = "Zsigmondy proved the theorem in 1892, and the exception at n = 6 for base 2 is the case the program prints as none. The example factors 2 to the n minus 1 for n from 1 to 8 by trial division and checks each prime against the smaller powers.",
        instructions = """
            1. Save the example as zsigmondy.py.
            2. Run it: python3 zsigmondy.py
        """,
        example = """
            def prime_factors(m):
                found = []
                d = 2
                while d * d <= m:
                    while m % d == 0:
                        if d not in found:
                            found.append(d)
                        m //= d
                    d += 1
                if m > 1 and m not in found:
                    found.append(m)
                return found

            for n in range(1, 9):
                m = 2 ** n - 1
                # A primitive prime divisor of 2^n - 1 does not divide 2^k - 1 for any k below n.
                primitive = [p for p in prime_factors(m) if all((2 ** k - 1) % p != 0 for k in range(1, n))]
                print(f"n = {n}: " + (" ".join(str(p) for p in primitive) or "none"))
        """,
    ),
)
