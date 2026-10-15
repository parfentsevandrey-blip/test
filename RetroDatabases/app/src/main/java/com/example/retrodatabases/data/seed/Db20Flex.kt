package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1840 to 1850: Dirichlet's approximations, Liouville's constant and Bravais's fourteen lattices. */
val db20Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.FLEX,
        section = "Diophantine approximation",
        title = "Dirichlet's approximation by fractions with small denominators",
        originalTitle = "Verallgemeinerung eines Satzes aus der Lehre von den Kettenbruechen",
        author = "Peter Gustav Lejeune Dirichlet",
        year = 1842,
        summary = "For any real number x and any whole number N, some q up to N makes q times x lie within 1 over N of a whole number.",
        details = "Dirichlet proved this pigeonhole-style statement in 1842. The example takes the decimal 0.618034, close to the reciprocal of the golden ratio, lists q times x for q up to 10, and reports the best q.",
        instructions = """
            1. Save the example as dirichlet.py.
            2. Run it: python3 dirichlet.py
        """,
        example = """
            from decimal import Decimal

            # A six-digit decimal close to the reciprocal of the golden ratio
            x = Decimal("0.618034")
            N = 10
            best = None
            for q in range(1, N + 1):
                p = round(q * x)
                gap = abs(q * x - p)
                print(f"q = {q}: p = {p}, distance |q x - p| = {gap}")
                if best is None or gap < best[2]:
                    best = (q, p, gap)
            q, p, gap = best
            print("best q:", q, "p:", p, "distance:", gap)
            print("below 1/N:", gap < Decimal(1) / N)
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.FLEX,
        section = "Transcendental numbers",
        title = "Liouville's constant, an early proof of a transcendental number",
        originalTitle = "Sur des classes tres-etendues de quantites dont la valeur n'est ni algebrique, ni meme reductible a des irrationnelles algebriques",
        author = "Joseph Liouville",
        year = 1844,
        summary = "Liouville's constant has the digit 1 only at the positions 1!, 2!, 3! and so on, and its very close rational approximations rule out any low-degree algebraic root.",
        details = "Liouville published his results on such numbers in 1844 at the Paris academy, giving an explicit example with a proof of transcendence. The example cuts the series after the first terms, prints each fraction, and compares a bound on the error with the square of the denominator.",
        instructions = """
            1. Save the example as liouville.py.
            2. Run it: python3 liouville.py
        """,
        example = """
            from fractions import Fraction as F
            from math import factorial

            # Truncation after the first k terms: p/q with q = 10 to the power k! and p = sum of 10 to the power (k! - j!)
            def truncation(k):
                q = 10 ** factorial(k)
                p = sum(10 ** (factorial(k) - factorial(j)) for j in range(1, k + 1))
                return p, q

            for k in range(1, 4):
                p, q = truncation(k)
                # the tail after the k-th term is below 2 times 10 to the power minus (k+1)!
                bound = F(2, 10 ** factorial(k + 1))
                print(f"k = {k}: p/q = {F(p, q)}, bound below 1/q squared: {bound < F(1, q * q)}")
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.FLEX,
        section = "Lattices",
        title = "Bravais's fourteen lattice types and their points per cell",
        originalTitle = "Memoire sur les systemes formes par des points distribues regulierement sur un plan ou dans l'espace",
        author = "Auguste Bravais",
        year = 1848,
        summary = "Bravais showed that a regular array of points in space comes in fourteen types, grouped under seven crystal systems.",
        details = "Bravais published the classification in 1848. The example lists the fourteen types with their centring and counts the lattice points in one conventional cell, which sums to 25.",
        instructions = """
            1. Save the example as bravais.py.
            2. Run it: python3 bravais.py
        """,
        example = """
            # Points in one conventional cell for each centring: P (primitive), I (body), F (face), C (base)
            points = {"P": 1, "I": 2, "F": 4, "C": 2}
            types = [
                ("triclinic", "P"), ("monoclinic", "P"), ("monoclinic", "C"),
                ("orthorhombic", "P"), ("orthorhombic", "C"), ("orthorhombic", "I"), ("orthorhombic", "F"),
                ("tetragonal", "P"), ("tetragonal", "I"),
                ("hexagonal", "P"), ("rhombohedral", "P"),
                ("cubic", "P"), ("cubic", "I"), ("cubic", "F"),
            ]
            total = sum(points[centring] for _, centring in types)
            for system, centring in types:
                print(system, centring, points[centring])
            print("number of lattice types:", len(types))
            print("points per cell summed over the types:", total)
        """,
    ),
)
