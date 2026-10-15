package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1780 to 1790: a plate's nodal lines, a recurrence by series, a jury counted in full and a ternary search. */
val db26Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.PROJECT,
        section = "Acoustics",
        title = "Chladni's nodal lines on a square plate",
        originalTitle = "Entdeckungen uber die Theorie des Klanges",
        author = "Ernst Florens Friedrich Chladni",
        year = 1787,
        summary = "Sand settles on the nodal lines of a vibrating plate, and the program prints where the signs of two square-plate modes change, with zeros on the lines.",
        details = "Chladni's book of 1787 on the theory of sound described the patterns that sand shows on vibrating plates. The example uses the simpler simply supported model, whose modes are products of sines, and prints the signs of two modes on a grid of five by five points, with zeros on the nodal lines.",
        instructions = """
            1. Save the example as chladni.py.
            2. Run it: python3 chladni.py
        """,
        example = """
            import math

            N = 5
            pts = [(k + 0.5) / N for k in range(N)]   # sample points at the centres of five strips of the plate
            TOL = 1e-9

            def sign_char(v):
                # Plus or minus, or zero for a value that is zero to rounding, which marks a nodal line.
                if abs(v) < TOL:
                    return "0"
                return "+" if v > 0 else "-"

            def mode_12(x, y):
                # Simply supported square plate, mode (1,2): sin(pi x) sin(2 pi y).
                return math.sin(math.pi * x) * math.sin(2 * math.pi * y)

            def mode_21(x, y):
                return math.sin(2 * math.pi * x) * math.sin(math.pi * y)

            def grid(f):
                # One text row for each y, with one character for each x.
                return ["".join(sign_char(f(x, y)) for x in pts) for y in pts]

            print("single mode (1,2):")
            for row in grid(mode_12):
                print(row)

            def both(x, y):
                return mode_12(x, y) + mode_21(x, y)

            print("sum of modes (1,2) and (2,1):")
            rows = grid(both)
            for row in rows:
                print(row)
            print("nodal cells in the sum:", sum(row.count("0") for row in rows))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.PROJECT,
        section = "Recurrences",
        title = "Laplace's recurrence solved with generating functions",
        originalTitle = "Memoire sur les suites",
        author = "Pierre-Simon Laplace",
        year = 1782,
        summary = "A recurrence is solved by writing its generating function as a series and reading off the coefficients, which the program checks against a closed form.",
        details = "Laplace's memoir on series used generating functions to solve recurrences; it appeared in the Academy's history volume for 1779, dated 1782, and the year used here is 1782. The example expands the generating function of a(n) = a(n-1) + 2 a(n-2) as a power series, gives the Jacobsthal numbers, and checks them against the closed form (2 to the power n minus (-1) to the power n) over 3.",
        instructions = """
            1. Save the example as laplace.py.
            2. Run it: python3 laplace.py
        """,
        example = """
            # Generating function G(x) = x / (1 - x - 2 x^2): the coefficients obey a(n) = a(n-1) + 2 a(n-2).
            N = 8

            # The series of 1 / (1 - x - 2 x^2) has coefficients c(n) with c(n) = c(n-1) + 2 c(n-2) and c(0) = 1.
            c = [1, 1]
            for n in range(2, N + 1):
                c.append(c[n - 1] + 2 * c[n - 2])

            # Multiplying by x shifts the coefficients up one place, so a(n) = c(n-1), with a(0) = 0.
            a = [0] + c[:N]
            print("coefficients:", a[:N])
            print("closed form holds:", all(3 * a[n] == 2**n - (-1)**n for n in range(N)))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.PROJECT,
        section = "Probability",
        title = "Condorcet's jury counted over every vote pattern",
        originalTitle = "Essai sur l'application de l'analyse a la probabilite des decisions rendues a la pluralite des voix",
        author = "Marie Jean Antoine Nicolas de Caritat, marquis de Condorcet",
        year = 1785,
        summary = "Each of the 32 patterns of five votes is weighed by its chance, and the program adds up the patterns in which a majority is right.",
        details = "Condorcet's essay of 1785 gives the chance that a majority is right, and the example checks the five-voter case by brute force, listing every one of the 32 patterns of right and wrong votes. At chance 1/2 exactly 16 patterns have a right majority, and at chance 2/3 the weighted total is 64/81.",
        instructions = """
            1. Save the example as jury_patterns.py.
            2. Run it: python3 jury_patterns.py
        """,
        example = """
            from fractions import Fraction as F
            from itertools import product

            N = 5   # voters

            def chance(p):
                # Add the weight of every pattern of right and wrong votes in which more than half are right.
                total = F(0)
                for pattern in product((True, False), repeat=N):
                    if sum(pattern) > N // 2:
                        weight = F(1)
                        for right in pattern:
                            weight *= p if right else 1 - p
                        total += weight
                return total

            majority_patterns = sum(1 for pattern in product((True, False), repeat=N) if sum(pattern) > N // 2)
            print(f"patterns with a right majority: {majority_patterns} of {2 ** N}")
            print("chance at p = 1/2:", chance(F(1, 2)))
            print("chance at p = 2/3:", chance(F(2, 3)))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.PROJECT,
        section = "Number theory",
        title = "Legendre's ternary equation: a search for solutions",
        originalTitle = "Recherches d'analyse indeterminee",
        author = "Adrien-Marie Legendre",
        year = 1785,
        summary = "Small positive solutions of a quadratic equation in three unknowns are found by brute force, and the program counts them for two cases.",
        details = "Legendre's memoir of 1785 treats equations of the form a x squared plus b y squared equals c z squared, and asks when they have solutions. The example searches small positive solutions by brute force: x squared plus y squared equals 3 z squared has none, while equals 5 z squared has ten with each of x, y and z at most 10.",
        instructions = """
            1. Save the example as ternary.py.
            2. Run it: python3 ternary.py
        """,
        example = """
            def solutions(a, b, c, limit):
                # Positive solutions of a x^2 + b y^2 = c z^2 with x, y and z at most limit.
                return [
                    (x, y, z)
                    for z in range(1, limit + 1)
                    for x in range(1, limit + 1)
                    for y in range(1, limit + 1)
                    if a * x * x + b * y * y == c * z * z
                ]

            three = solutions(1, 1, 3, 30)
            print(f"x^2 + y^2 = 3 z^2, up to 30: {len(three)} solutions")
            five = solutions(1, 1, 5, 10)
            print(f"x^2 + y^2 = 5 z^2, up to 10: {len(five)} solutions")
            print("first three:", five[:3])
        """,
    ),
)
