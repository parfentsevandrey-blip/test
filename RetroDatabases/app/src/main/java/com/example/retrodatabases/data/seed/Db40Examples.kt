package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1640 to 1650: Mersenne's prime exponents, Fermat's two squares, perfect numbers, Roberval's tangent, Pascal's hexagram and Torricelli's horn. */
val db40Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Mersenne's 1644 list of prime exponents, checked by a test",
        originalTitle = "Cogitata Physico-Mathematica",
        author = "Marin Mersenne",
        year = 1644,
        summary = "Mersenne claimed that 2 to the power p minus 1 is prime for a list of exponents, and a test run today shows that two of his claims fail and three primes are missing from the list.",
        details = "Mersenne printed the list in Paris in 1644, and it includes 67 and 257, whose numbers are composite, while 61, 89 and 107 are left out although their numbers are prime. The example runs the Lucas-Lehmer test, devised long after Mersenne, on his exponents and on the three missing ones.",
        instructions = """
            1. Save the example as mersenne_list.py.
            2. Run it: python3 mersenne_list.py
        """,
        example = """
            def is_mersenne_prime(p):
                # Lucas-Lehmer test: for an odd prime p, 2 to the power p minus 1 is prime exactly when the sequence ends at 0.
                if p == 2:
                    return True
                m = (1 << p) - 1
                s = 4
                for _ in range(p - 2):
                    s = (s * s - 2) % m
                return s == 0

            claimed = (2, 3, 5, 7, 13, 17, 19, 31, 67, 127, 257)
            for p in claimed:
                print(f"2^{p} - 1 is prime: {is_mersenne_prime(p)}")
            for p in (61, 89, 107):
                print(f"missing from the list, 2^{p} - 1 is prime: {is_mersenne_prime(p)}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Fermat's primes of the form 4n+1 written as two squares",
        originalTitle = "Lettre a Mersenne du 25 decembre 1640",
        author = "Pierre de Fermat",
        year = 1640,
        summary = "Every prime of the form 4n+1 is a sum of two squares in exactly one way, and a search up to 50 finds the squares for each such prime.",
        details = "Fermat announced the theorem in a letter to Mersenne dated 25 December 1640, and he gave no proof in that letter. The example searches for the squares of the primes 4n+1 up to 50 by brute force, so every line of the output can be checked by hand.",
        instructions = """
            1. Save the example as two_squares.py.
            2. Run it: python3 two_squares.py
        """,
        example = """
            def is_prime(n):
                return n > 1 and all(n % d for d in range(2, int(n ** 0.5) + 1))

            def two_squares(p):
                # Brute force: find x <= y with x * x + y * y = p.
                for x in range(1, int(p ** 0.5) + 1):
                    y2 = p - x * x
                    y = int(y2 ** 0.5)
                    if y * y == y2 and y >= x:
                        return x, y
                return None

            count = 0
            for p in range(2, 51):
                if is_prime(p) and p % 4 == 1:
                    count += 1
                    x, y = two_squares(p)
                    print(f"{p} = {x}^2 + {y}^2")
            print(f"primes of the form 4n+1 up to 50: {count}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Perfect numbers from Mersenne's exponents, by Euclid's rule",
        originalTitle = "Euclid's Elements, Book IX, proposition 36",
        author = "Marin Mersenne",
        year = 1644,
        summary = "Euclid's rule turns each prime p of the form 2 to the power p minus 1 into an even perfect number, and Mersenne's exponents supply the first few such numbers.",
        details = "Euclid stated the rule in Book IX of the Elements, and the exponents that Mersenne printed in 1644 include the primes that the rule needs for the first five perfect numbers. The example forms each number and checks that the sum of its proper divisors equals the number.",
        instructions = """
            1. Save the example as perfect_numbers.py.
            2. Run it: python3 perfect_numbers.py
        """,
        example = """
            def proper_divisor_sum(n):
                # Add every divisor below n, pairing each divisor d with n // d.
                total = 1 if n > 1 else 0
                d = 2
                while d * d <= n:
                    if n % d == 0:
                        total += d
                        if d * d != n:
                            total += n // d
                    d += 1
                return total

            for p in (2, 3, 5, 7, 13):
                n = 2 ** (p - 1) * (2 ** p - 1)
                print(f"p = {p}: {n}, proper divisors sum to it: {proper_divisor_sum(n) == n}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "Roberval's tangent to the cycloid by composed motion",
        originalTitle = "Observations sur la composition des mouvemens et sur le moyen de trouver les touchantes des lignes courbes",
        author = "Gilles Personne de Roberval",
        year = 1640,
        summary = "For a point on a rolling wheel the tangent follows the sum of the point's two motions, and for the cycloid this gives a slope equal to the cotangent of half the rolling angle.",
        details = "Roberval's lessons on composed motion were given between 1639 and 1644 and recorded by his pupil Francois du Verdus, so 1640 names the lecture period. The treatise was printed only in 1693, after his death in 1675, and the example adds the two parts of a point's motion to find the slope at three positions, which come out as 1, 0 and -1.",
        instructions = """
            1. Save the example as cycloid_tangent.py.
            2. Run it: python3 cycloid_tangent.py
        """,
        example = """
            from math import sin, cos, pi

            def slope(t):
                # The point's motion splits into two parts; their sum gives the tangent direction (1 - cos t, sin t).
                vx = 1 - cos(t)
                vy = sin(t)
                return vy / vx

            for t in (pi / 2, pi, 3 * pi / 2):
                print(f"angle {t:.4f} rad: slope {slope(t):.4f}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Conics",
        title = "Pascal's mystic hexagram checked with exact fractions",
        originalTitle = "Essai pour les coniques",
        author = "Blaise Pascal",
        year = 1640,
        summary = "If six points lie on a conic, the three points where opposite sides meet lie on one straight line, and the example checks this for six points on a circle.",
        details = "Pascal stated the theorem in his Essai pour les coniques, printed in Paris in 1640 when he was about sixteen, and the figure was later called the mystic hexagram. The example uses six points with whole-number coordinates on the circle x squared plus y squared equals 25, finds the three meeting points with fractions and tests that they are collinear.",
        instructions = """
            1. Save the example as hexagram.py.
            2. Run it: python3 hexagram.py
        """,
        example = """
            from fractions import Fraction as F

            def line(p, q):
                # The line through p and q written as a * x + b * y = c.
                (x1, y1), (x2, y2) = p, q
                a = y2 - y1
                b = x1 - x2
                c = a * x1 + b * y1
                return a, b, c

            def meet(l1, l2):
                a1, b1, c1 = l1
                a2, b2, c2 = l2
                det = a1 * b2 - a2 * b1
                return (c1 * b2 - c2 * b1) / det, (a1 * c2 - a2 * c1) / det

            P = [(F(5), F(0)), (F(3), F(4)), (F(0), F(5)), (F(-3), F(4)), (F(-5), F(0)), (F(-3), F(-4))]
            A = meet(line(P[0], P[1]), line(P[3], P[4]))
            B = meet(line(P[1], P[2]), line(P[4], P[5]))
            C = meet(line(P[2], P[3]), line(P[5], P[0]))
            print(f"A = ({A[0]}, {A[1]})")
            print(f"B = ({B[0]}, {B[1]})")
            print(f"C = ({C[0]}, {C[1]})")
            det = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0])
            print("collinear:", det == 0)
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.EXAMPLE,
        section = "Solids",
        title = "Torricelli's horn, a solid of finite volume",
        originalTitle = "De solido hyperbolico acuto",
        author = "Evangelista Torricelli",
        year = 1644,
        summary = "Turning the curve y equals 1 over x about the x axis past x equals 1 makes a solid whose volume is finite and equal to pi, though its surface has no finite area.",
        details = "Torricelli reached the result in 1641 and printed it in his Opera geometrica of 1644. The example adds up the volume out to several values of x, which is pi times one minus one over x, and shows the sums rising toward pi.",
        instructions = """
            1. Save the example as horn_volume.py.
            2. Run it: python3 horn_volume.py
        """,
        example = """
            from math import pi

            def horn_volume(n):
                # Volume of y = 1/x turned about the x axis from x = 1 to x = n: pi times the integral of 1/x squared, which is pi times (1 - 1/n).
                return pi * (1 - 1 / n)

            for n in (2, 10, 1000):
                print(f"volume out to x = {n}: {horn_volume(n):.4f}")
            print(f"limit as x grows: {pi:.4f}")
        """,
    ),

)
