package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1800 to 1810: complex numbers in the plane, quadratic reciprocity, Gauss's lemma, elimination and index tables. */
val db24Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.GUIDE,
        section = "Complex numbers",
        title = "How Argand's diagram turns multiplication into rotation",
        originalTitle = "Essai sur une maniere de representer les quantites imaginaires (1806)",
        author = "Jean-Robert Argand",
        year = 1806,
        summary = "Argand's picture places each complex number at a point, so multiplying by i turns the point through a right angle and the lengths of products multiply.",
        details = "Argand's essay of 1806 was printed without the author's name in Paris, and its publication status is disputed, though it set out the plane picture of complex numbers. Caspar Wessel had described a similar construction earlier. The example multiplies integer pairs, checks that squared lengths multiply, and turns a point through four right angles back to its start.",
        instructions = """
            1. Save the example as argand.py.
            2. Run it: python3 argand.py
        """,
        example = """
            # A complex number x + y*i is stored as the pair (x, y); all arithmetic is exact integers.
            def mul(z, w):
                a, b = z
                c, d = w
                return (a * c - b * d, a * d + b * c)

            def norm_sq(z):
                return z[0] * z[0] + z[1] * z[1]

            z = (3, 4)
            w = (1, 2)
            p = mul(z, w)
            print("z*w =", p)
            print(f"|z|^2 = {norm_sq(z)}, |w|^2 = {norm_sq(w)}, |z*w|^2 = {norm_sq(p)}")
            print("lengths multiply:", norm_sq(p) == norm_sq(z) * norm_sq(w))
            # Multiplying by i turns the point through a right angle: (x, y) becomes (-y, x).
            i = (0, 1)
            point = (5, 2)
            for turn in range(1, 5):
                point = mul(point, i)
                print(f"after {turn} quarter turns: {point}")
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.GUIDE,
        section = "Projective geometry",
        title = "How Brianchon's diagonals meet on a tangent hexagon",
        originalTitle = "Theorem on hexagons circumscribed about conics, Brianchon (1806)",
        author = "Charles Julien Brianchon",
        year = 1806,
        summary = "Brianchon's theorem says the three diagonals joining opposite corners of a hexagon circumscribed about a conic meet in one point, and the program checks it on a parabola.",
        details = "Brianchon stated the theorem in 1806, as the dual of Pascal's theorem on hexagons inscribed in a conic. The example takes six tangent lines to the parabola y = x squared, forms the six corners, and checks in exact fractions that the three long diagonals meet in one point.",
        instructions = """
            1. Save the example as brianchon.py.
            2. Run it: python3 brianchon.py
        """,
        example = """
            from fractions import Fraction as F

            # Tangent lines y = 2t x - t^2 to the parabola y = x^2, one for each parameter t.
            T = [1, 2, 4, 5, 7, 9]

            def corner(s, t):
                # The meeting point of the tangents at s and t is ((s + t)/2, s t).
                return (F(s + t, 2), F(s * t))

            V = [corner(T[i], T[(i + 1) % 6]) for i in range(6)]
            print("corners: " + ", ".join(f"({x}, {y})" for x, y in V))

            def line(p, q):
                # The line through p and q as (m, c) with y = m x + c.
                m = (q[1] - p[1]) / (q[0] - p[0])
                return m, p[1] - m * p[0]

            # Opposite corners are three apart in the cycle; the three diagonals join them.
            L = [line(V[i], V[i + 3]) for i in range(3)]
            (m1, c1), (m2, c2), (m3, c3) = L
            x = (c2 - c1) / (m1 - m2)
            y = m1 * x + c1
            print(f"first two diagonals meet at ({x}, {y})")
            print("third diagonal passes through the point:", m3 * x + c3 == y)
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.GUIDE,
        section = "Quadratic residues",
        title = "How Gauss's lemma gives a Legendre symbol by counting",
        originalTitle = "Theorematis arithmetici demonstratio nova (1808)",
        author = "Carl Friedrich Gauss",
        year = 1808,
        summary = "Gauss's lemma gives the sign of a Legendre symbol by counting how many multiples of a, reduced modulo p, fall above half of p.",
        details = "Gauss stated the lemma in his 1808 work on the reciprocity law, and the sign is minus one raised to the number of such multiples. The example runs four cases with exact integers and checks each sign against Euler's criterion.",
        instructions = """
            1. Save the example as gauss_lemma.py.
            2. Run it: python3 gauss_lemma.py
        """,
        example = """
            # Gauss's lemma: count the multiples k*a, for k up to (p-1)/2, whose residue mod p lies above p/2.
            def gauss_count(a, p):
                half = (p - 1) // 2
                count = 0
                for k in range(1, half + 1):
                    if (k * a) % p > half:
                        count += 1
                return count

            for p, a in [(7, 3), (7, 2), (11, 3), (13, 5)]:
                count = gauss_count(a, p)
                symbol = (-1) ** count
                euler = pow(a, (p - 1) // 2, p)
                agrees = euler == (1 if symbol == 1 else p - 1)
                print(f"a={a}, p={p}: count {count}, symbol {symbol}, Euler agrees: {agrees}")
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.GUIDE,
        section = "Linear equations",
        title = "How Gauss's elimination solves three equations",
        originalTitle = "Theoria motus corporum coelestium (1809)",
        author = "Carl Friedrich Gauss",
        year = 1809,
        summary = "Gauss's elimination clears the entries below each pivot, and back substitution then gives the unknowns, here for a three-equation system in exact fractions.",
        details = "Gauss used this elimination on the normal equations of his orbit calculations, and it is printed in his Theoria motus of 1809, which is why the method carries his name. The example solves a system whose answer is x equal to 2, y equal to 3 and z equal to -1, and it checks each equation with a residual that is zero.",
        instructions = """
            1. Save the example as elimination.py.
            2. Run it: python3 elimination.py
        """,
        example = """
            from fractions import Fraction as F

            # Coefficients of x, y, z and the constant for each equation.
            equations = [
                [F(2), F(1), F(-1), F(8)],
                [F(-3), F(-1), F(2), F(-11)],
                [F(-2), F(1), F(2), F(-3)],
            ]
            n = 3
            rows = [row[:] for row in equations]
            # Forward elimination: use each row in turn to clear the entries below its pivot.
            for k in range(n):
                for r in range(k + 1, n):
                    factor = rows[r][k] / rows[k][k]
                    rows[r] = [a - factor * b for a, b in zip(rows[r], rows[k])]
            # Back substitution, from the last unknown upward.
            x = [F(0)] * n
            for k in reversed(range(n)):
                total = rows[k][n] - sum(rows[k][j] * x[j] for j in range(k + 1, n))
                x[k] = total / rows[k][k]
            print("x =", x[0])
            print("y =", x[1])
            print("z =", x[2])
            for row in equations:
                print("residual:", sum(row[j] * x[j] for j in range(n)) - row[n])
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.GUIDE,
        section = "Index tables",
        title = "How an index table turns products into sums modulo 11",
        originalTitle = "Disquisitiones Arithmeticae, indices of residues (1801)",
        author = "Carl Friedrich Gauss",
        year = 1801,
        summary = "Once a primitive root is chosen, an index table turns multiplication modulo a prime into addition of exponents, and the example builds the table for 11.",
        details = "Gauss introduced indices, the exponents of a primitive root, in the Disquisitiones of 1801. The example takes 2 as the primitive root of 11, prints the index table, and checks three products and then all hundred products against direct multiplication.",
        instructions = """
            1. Save the example as index_table.py.
            2. Run it: python3 index_table.py
        """,
        example = """
            p = 11
            g = 2   # 2 is a primitive root modulo 11
            # Build the index table: ind[x] is the k with g^k = x mod p, for k from 0 to p-2.
            ind = {}
            power = 1
            for k in range(p - 1):
                ind[power] = k
                power = (power * g) % p
            print("index table:", " ".join(f"{x}->{ind[x]}" for x in sorted(ind)))

            def mul_by_index(a, b):
                # Multiplying residues is adding their indices, modulo p - 1.
                k = (ind[a] + ind[b]) % (p - 1)
                return pow(g, k, p)

            for a, b in [(3, 7), (5, 9), (4, 8)]:
                print(f"{a} x {b} mod {p}: index sum {(ind[a] + ind[b]) % (p - 1)}, product {mul_by_index(a, b)}, direct {(a * b) % p}")
            print("all products agree:", all(mul_by_index(a, b) == (a * b) % p for a in range(1, p) for b in range(1, p)))
        """,
    ),
)
