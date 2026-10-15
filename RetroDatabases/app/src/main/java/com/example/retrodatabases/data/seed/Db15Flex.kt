package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1890 to 1900: prime counts from 1896, Betti numbers of a torus and lattice points in convex regions. */
val db15Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.FLEX,
        section = "Prime numbers",
        title = "Prime counts against x over ln x",
        originalTitle = "Sur la distribution des zeros de la fonction zeta(s) et ses consequences arithmetiques (1896)",
        author = "Jacques Hadamard and Charles de la Vallee Poussin",
        year = 1896,
        summary = "The number of primes up to x is close to x over ln x, a result proved in 1896 by Hadamard and by de la Vallee Poussin.",
        details = "Both proofs used complex analysis and the zeta function, and they established the prime number theorem in 1896. The example counts primes up to 1000 and 10000 with a sieve and compares each count with x over ln x.",
        instructions = """
            1. Save the example as prime_counts.py.
            2. Run it: python3 prime_counts.py
        """,
        example = """
            import math

            def primes_upto(n):
                # Sieve of Eratosthenes.
                sieve = [True] * (n + 1)
                sieve[0] = sieve[1] = False
                for i in range(2, int(n ** 0.5) + 1):
                    if sieve[i]:
                        for j in range(i * i, n + 1, i):
                            sieve[j] = False
                return [i for i, ok in enumerate(sieve) if ok]

            for x in (1000, 10000):
                count = len(primes_upto(x))
                approx = x / math.log(x)
                print(f"pi({x}) = {count}, x / ln x = {approx:.2f}, ratio = {count / approx:.2f}")
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.FLEX,
        section = "Topology",
        title = "Betti numbers of a torus from a triangulation",
        originalTitle = "Analysis situs (1895)",
        author = "Henri Poincare",
        year = 1895,
        summary = "Betti numbers count the independent holes of a space in each dimension, and rank computations on a triangulated torus give 1, 2 and 1.",
        details = "Poincare's 1895 memoir Analysis situs set out homology, the framework in which Betti numbers are computed. The example builds a 3 by 3 triangulated torus with 9 vertices, 27 edges and 18 faces, computes the ranks of its boundary matrices in exact fractions, and checks that the boundary of a boundary is zero.",
        instructions = """
            1. Save the example as torus.py.
            2. Run it: python3 torus.py
        """,
        example = """
            from fractions import Fraction

            N = 3  # the torus is the 3 by 3 grid with opposite sides identified

            def v(i, j):
                return (i % N, j % N)

            vertices = [v(i, j) for i in range(N) for j in range(N)]
            edges = []
            faces = []
            for i in range(N):
                for j in range(N):
                    edges.append((v(i, j), v(i + 1, j)))      # horizontal
                    edges.append((v(i, j), v(i, j + 1)))      # vertical
                    edges.append((v(i, j), v(i + 1, j + 1)))  # diagonal
                    faces.append([v(i, j), v(i + 1, j), v(i + 1, j + 1)])
                    faces.append([v(i, j), v(i + 1, j + 1), v(i, j + 1)])

            edge_index = {}
            for k, (a, b) in enumerate(edges):
                edge_index[(a, b)] = (k, 1)
                edge_index[(b, a)] = (k, -1)

            # Boundary of an edge: head minus tail.
            d1 = [[0] * len(edges) for _ in vertices]
            for k, (a, b) in enumerate(edges):
                d1[vertices.index(b)][k] += 1
                d1[vertices.index(a)][k] -= 1

            # Boundary of a triangle [p, q, r]: the edges pq, qr and rp, with signs.
            d2 = [[0] * len(faces) for _ in edges]
            for f_index, (p, q, r) in enumerate(faces):
                for a, b in ((p, q), (q, r), (r, p)):
                    k, sign = edge_index[(a, b)]
                    d2[k][f_index] += sign

            def rank(matrix):
                # Gaussian elimination over the rationals.
                m = [[Fraction(x) for x in row] for row in matrix]
                r = 0
                cols = len(m[0]) if m else 0
                for c in range(cols):
                    pivot = next((i for i in range(r, len(m)) if m[i][c] != 0), None)
                    if pivot is None:
                        continue
                    m[r], m[pivot] = m[pivot], m[r]
                    for i in range(len(m)):
                        if i != r and m[i][c] != 0:
                            factor = m[i][c] / m[r][c]
                            m[i] = [a - factor * b for a, b in zip(m[i], m[r])]
                    r += 1
                return r

            V, E, F = len(vertices), len(edges), len(faces)
            print(f"vertices {V}, edges {E}, faces {F}, Euler characteristic {V - E + F}")
            r1, r2 = rank(d1), rank(d2)
            print(f"rank of boundary of edges = {r1}")
            print(f"rank of boundary of faces = {r2}")
            composed = all(sum(d1[i][k] * d2[k][j] for k in range(E)) == 0 for i in range(V) for j in range(F))
            print("boundary of boundary is zero:", composed)
            print(f"Betti numbers b0={V - r1} b1={E - r1 - r2} b2={F - r2}")
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.FLEX,
        section = "Geometry of numbers",
        title = "Lattice points in convex regions of area above four",
        originalTitle = "Geometrie der Zahlen (1896)",
        author = "Hermann Minkowski",
        year = 1896,
        summary = "A convex region symmetric about the origin with area more than four contains a whole-number point other than the origin, and the example counts such points for three regions.",
        details = "Minkowski's theorem appears in his 1896 lectures on the geometry of numbers. The example counts nonzero lattice points in an open diamond of area 2, a closed square of area 4 and a closed diamond of area 8.",
        rare = true,
        instructions = """
            1. Save the example as minkowski.py.
            2. Run it: python3 minkowski.py
        """,
        example = """
            def count(region, bound):
                # Nonzero whole-number points (x, y) with |x| and |y| at most bound that pass the region test.
                return sum(1 for x in range(-bound, bound + 1) for y in range(-bound, bound + 1)
                           if (x, y) != (0, 0) and region(x, y))

            regions = [
                ("open diamond |x|+|y| < 1, area 2", lambda x, y: abs(x) + abs(y) < 1, 2),
                ("closed square |x|, |y| <= 1, area 4", lambda x, y: abs(x) <= 1 and abs(y) <= 1, 2),
                ("closed diamond |x|+|y| <= 2, area 8", lambda x, y: abs(x) + abs(y) <= 2, 3),
            ]
            for name, region, bound in regions:
                print(f"{name}: {count(region, bound)} nonzero lattice points")
            print("area 8 is above 4, so a nonzero point is forced:", count(regions[2][1], 3) > 0)
        """,
    ),
)
