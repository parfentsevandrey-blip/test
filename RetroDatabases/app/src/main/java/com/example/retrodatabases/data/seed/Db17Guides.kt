package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1870 to 1880: the real-number, geometric, thermodynamic and group-theoretic ideas of the decade, each with a program that runs today. */
val db17Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.GUIDE,
        section = "Real numbers",
        title = "How Dedekind's cut defines the square root of 2",
        originalTitle = "Stetigkeit und irrationale Zahlen (1872)",
        author = "Richard Dedekind",
        year = 1872,
        summary = "A real number is defined as a cut that splits the rationals into a lower set and an upper set with no largest number in the lower set.",
        details = "Dedekind's 1872 essay Stetigkeit und irrationale Zahlen defined irrational numbers as cuts of the rational numbers. The example tests membership in the lower cut of the square root of 2, where a rational q belongs to the cut when q is negative or q squared is below 2, and checks that the cut is closed downward.",
        instructions = """
            1. Save the example as dedekind.py.
            2. Run it: python3 dedekind.py
        """,
        example = """
            from fractions import Fraction

            def in_lower_cut(q):
                # The lower set of the cut for the square root of 2: all negative rationals and all q with q * q below 2.
                return q < 0 or q * q < 2

            samples = [Fraction(-1), Fraction(7, 5), Fraction(24, 17), Fraction(10, 7), Fraction(3, 2)]
            for q in samples:
                print(f"{q} in lower cut: {in_lower_cut(q)}")
            ordered = sorted(samples)
            flags = [in_lower_cut(q) for q in ordered]
            print("flags in increasing order:", flags)
            # Closed downward: every number below a member of the cut is also in the cut.
            closed = all(not f or all(in_lower_cut(r) for r in ordered[:i]) for i, f in enumerate(flags))
            print("downward closed:", closed)
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "Klein's Erlangen program: what an affine map keeps",
        originalTitle = "Vergleichende Betrachtungen uber neuere geometrische Forschungen (1872)",
        author = "Felix Klein",
        year = 1872,
        summary = "Klein defined a geometry as the study of what stays fixed under a group of transformations, and an affine map keeps midpoints and ratios along a line but not distances.",
        details = "Klein's 1872 Erlangen program proposed that each geometry be defined by the transformations it allows. The example applies one affine map of the plane to points and checks which quantities survive: ratios along a line, midpoints and areas survive, while squared distances change.",
        instructions = """
            1. Save the example as klein.py.
            2. Run it: python3 klein.py
        """,
        example = """
            from fractions import Fraction

            def T(p):
                # An affine map of the plane with determinant 1: x goes to 2x + y + 1 and y goes to x + y.
                x, y = p
                return (2 * x + y + 1, x + y)

            def midpoint(p, q):
                return (Fraction(p[0] + q[0], 2), Fraction(p[1] + q[1], 2))

            def squared_distance(p, q):
                return (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2

            def area(a, b, c):
                # Shoelace formula, halved; the absolute value makes the orientation irrelevant.
                return Fraction(abs(a[0] * (b[1] - c[1]) + b[0] * (c[1] - a[1]) + c[0] * (a[1] - b[1])), 2)

            P, Q, R = (0, 0), (1, 1), (3, 3)
            print("images:", T(P), T(Q), T(R))
            ratio_before = Fraction(Q[0] - P[0], R[0] - P[0])
            ratio_after = Fraction(T(Q)[0] - T(P)[0], T(R)[0] - T(P)[0])
            print("ratio along the line before:", ratio_before, "after:", ratio_after)
            M = midpoint(P, R)
            print("midpoint maps to midpoint:", T(M) == midpoint(T(P), T(R)))
            print("squared distance P to Q:", squared_distance(P, Q), "after:", squared_distance(T(P), T(Q)))
            A, B, C = (0, 0), (1, 0), (0, 1)
            print("triangle area before:", area(A, B, C), "after:", area(T(A), T(B), T(C)))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.GUIDE,
        section = "Thermodynamics",
        title = "Gibbs's phase rule, F = C - P + 2",
        originalTitle = "On the equilibrium of heterogeneous substances (1876)",
        author = "Josiah Willard Gibbs",
        year = 1876,
        summary = "The number of free variables of a system in equilibrium equals its number of components minus its number of phases plus two.",
        details = "Gibbs published the rule in the first part of his memoir On the Equilibrium of Heterogeneous Substances in 1876. The example applies F = C - P + 2 to five systems, from one phase of one component to two components with a solid, a liquid and a vapour.",
        instructions = """
            1. Save the example as gibbs.py.
            2. Run it: python3 gibbs.py
        """,
        example = """
            systems = [
                ("one component, one phase", 1, 1),
                ("one component, liquid and vapour", 1, 2),
                ("one component, solid, liquid and vapour", 1, 3),
                ("two components, liquid and vapour", 2, 2),
                ("two components, solid, liquid and vapour", 2, 3),
            ]
            for name, c, p in systems:
                f = c - p + 2
                print(f"{name}: F = {c} - {p} + 2 = {f}")
            print("total of F:", sum(c - p + 2 for _, c, p in systems))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "Clausius's virial theorem for an oscillator",
        originalTitle = "On a mechanical theorem applicable to heat (1870)",
        author = "Rudolf Clausius",
        year = 1870,
        summary = "For a bound system, twice the time average of kinetic energy equals the time average of the virial, the sum of force times position.",
        details = "Clausius stated the theorem in 1870 in a paper of that title. The example applies it to a spring with mass 1, spring constant 1 and amplitude 2, averaging over four phases a quarter period apart, which gives exact averages.",
        instructions = """
            1. Save the example as virial.py.
            2. Run it: python3 virial.py
        """,
        example = """
            from fractions import Fraction

            # One-dimensional oscillator with mass 1 and spring constant 1; amplitude 2.
            # Four phases a quarter period apart give an exact average over a full period.
            amplitude = 2
            samples = [(1, 0), (0, 1), (-1, 0), (0, -1)]  # (cos, sin) at phases 0, 90, 180 and 270 degrees
            xs = [amplitude * c for c, s in samples]
            vs = [-amplitude * s for c, s in samples]
            kinetic = [Fraction(v * v, 2) for v in vs]
            potential = [Fraction(x * x, 2) for x in xs]
            virial = [Fraction(x * x) for x in xs]  # x times dV/dx, where dV/dx = x for this spring
            n = len(samples)
            avg_T = sum(kinetic) / n
            avg_V = sum(potential) / n
            avg_virial = sum(virial) / n
            print("average kinetic energy:", avg_T)
            print("average potential energy:", avg_V)
            print("average of x dV/dx:", avg_virial)
            print("virial check 2T = x dV/dx:", 2 * avg_T == avg_virial)
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.GUIDE,
        section = "Group theory",
        title = "Cayley's group tables and the group axioms",
        originalTitle = "Desiderata and suggestions: No. 2. The theory of groups (1878)",
        author = "Arthur Cayley",
        year = 1878,
        summary = "A group table lists every product of two elements, and a table is a group when it has closure, associativity, an identity and inverses.",
        details = "Cayley's 1878 note in the American Journal of Mathematics described group tables and the abstract definition of a group. The example prints the tables of two groups and of one set that is not a group, and checks closure, associativity, identity and inverses for each.",
        instructions = """
            1. Save the example as cayley.py.
            2. Run it: python3 cayley.py
        """,
        example = """
            from itertools import product

            def cayley_table(elements, op):
                # Row a, column b holds the product a op b.
                return [[op(a, b) for b in elements] for a in elements]

            def is_group(elements, op):
                closed = all(op(a, b) in elements for a, b in product(elements, repeat=2))
                associative = all(op(op(a, b), c) == op(a, op(b, c)) for a, b, c in product(elements, repeat=3))
                identities = [e for e in elements if all(op(e, a) == a and op(a, e) == a for a in elements)]
                if not identities:
                    return False
                e = identities[0]
                inverses = all(any(op(a, b) == e for b in elements) for a in elements)
                return closed and associative and inverses

            def show(name, elements, op):
                for row in cayley_table(elements, op):
                    print(" ".join(str(x) for x in row))
                print(name, "is a group:", is_group(elements, op))

            show("Z4 under addition mod 4", [0, 1, 2, 3], lambda a, b: (a + b) % 4)
            show("V4 under exclusive or", [0, 1, 2, 3], lambda a, b: a ^ b)
            show("left projection a*b = a on 0, 1, 2", [0, 1, 2], lambda a, b: a)
        """,
    ),
)
