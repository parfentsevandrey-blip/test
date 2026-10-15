package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1840 to 1850: Lovelace's Bernoulli table, Babbage's difference columns, Kirchhoff's network and Joule's heating law. */
val db20Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.PROJECT,
        section = "Bernoulli numbers",
        title = "Lovelace's Note G, rebuilt for Bernoulli numbers",
        originalTitle = "Sketch of the Analytical Engine, with the translator's Note G",
        author = "Ada Lovelace",
        year = 1843,
        summary = "Note G gives a table of operations for Babbage's Analytical Engine to compute Bernoulli numbers, and it is often named as the first published program.",
        details = "Lovelace's notes appeared with her 1843 translation of Menabrea's memoir, and the table works through a Bernoulli number using the earlier values. The attribution of the note is discussed in the literature. The example rebuilds the sequence with exact fractions from the standard recurrence, as a modern check rather than a copy of the table.",
        instructions = """
            1. Save the example as bernoulli.py.
            2. Run it: python3 bernoulli.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def bernoulli_numbers(count):
                # B_m = -1/(m+1) times the sum over k below m of C(m+1, k) B_k, with B_0 = 1
                B = [Fraction(1)]
                for m in range(1, count):
                    total = sum(comb(m + 1, k) * B[k] for k in range(m))
                    B.append(-total / (m + 1))
                return B

            values = bernoulli_numbers(9)
            for m, b in enumerate(values):
                print(f"B{m} = {b}")
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.PROJECT,
        section = "Tabulating machines",
        title = "Babbage's Difference Engine No. 2, a table built by columns",
        originalTitle = "Design drawings for Difference Engine No. 2",
        author = "Charles Babbage",
        year = 1847,
        summary = "The engine would tabulate polynomials by the method of differences, using only addition, with each column carrying the next one.",
        details = "Babbage drew the second difference engine between 1847 and 1849, and it was never built in his lifetime. The example runs the method on the cubes with four columns, because a cubic needs three differences.",
        instructions = """
            1. Save the example as difference_engine.py.
            2. Run it: python3 difference_engine.py
        """,
        example = """
            # Method of differences for the cubes n**3: four columns hold the value and its first three differences.
            diffs = [0, 1, 6, 6]        # f(0), first, second and third differences at n = 0
            table = []
            for n in range(6):
                table.append(diffs[0])
                # one step of the engine: each column adds the column to its right
                for i in range(3):
                    diffs[i] += diffs[i + 1]
            print("values by addition:", table)
            print("check against n cubed:", table == [n ** 3 for n in range(6)])
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.PROJECT,
        section = "Circuits",
        title = "Kirchhoff's network equations for galvanic currents",
        originalTitle = "Ueber die Aufloesung der Gleichungen, auf welche man bei der Untersuchung der linearen Vertheilung galvanischer Stroeme gefuehrt wird",
        author = "Gustav Robert Kirchhoff",
        year = 1847,
        summary = "Kirchhoff's laws for currents and voltages turn a network of resistors and batteries into linear equations that give every current.",
        details = "Kirchhoff set out the solution of such equations in 1847. The example solves a two-loop network with a shared resistor exactly, by Cramer's rule, and checks both loop equations.",
        instructions = """
            1. Save the example as kirchhoff.py.
            2. Run it: python3 kirchhoff.py
        """,
        example = """
            from fractions import Fraction as F

            # Two loops sharing one resistor R2; the mesh form of Kirchhoff's voltage law gives two linear equations.
            R1, R2, R3 = F(4), F(2), F(3)     # ohms
            E1, E2 = F(12), F(6)              # volts
            A = [[R1 + R2, -R2], [-R2, R2 + R3]]
            b = [E1, -E2]

            def solve2(M, v):
                # Cramer's rule for a 2 by 2 system, in exact fractions
                det = M[0][0] * M[1][1] - M[0][1] * M[1][0]
                x = (v[0] * M[1][1] - M[0][1] * v[1]) / det
                y = (M[0][0] * v[1] - v[0] * M[1][0]) / det
                return x, y

            I1, I2 = solve2(A, b)
            print("loop currents:", I1, I2)
            print("current through the shared resistor:", I1 - I2)
            print("loop 1 check:", A[0][0] * I1 + A[0][1] * I2 == b[0])
            print("loop 2 check:", A[1][0] * I1 + A[1][1] * I2 == b[1])
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.PROJECT,
        section = "Electric heating",
        title = "Joule's law of heat from a current",
        originalTitle = "On the production of heat by voltaic electricity",
        author = "James Prescott Joule",
        year = 1841,
        summary = "The heat produced in a conductor is proportional to the square of the current, to the resistance and to the time, so heat equals current squared times resistance times time.",
        details = "Joule reported the square law for heating by an electric current in 1841. The example tabulates the heat for three currents through a 3 ohm resistor over 10 seconds and checks that doubling the current quadruples the heat. The figures are illustrative values for the calculation.",
        instructions = """
            1. Save the example as joule.py.
            2. Run it: python3 joule.py
        """,
        example = """
            from fractions import Fraction as F

            # Joule's law: heat in joules = current squared times resistance times time in seconds
            R = F(3)     # ohms
            t = F(10)    # seconds
            for current in [F(1, 2), F(1), F(2)]:
                print("current", current, "heat", current * current * R * t)
            print("ratio of heat at 2 A to heat at 1 A:", (F(2) ** 2 * R * t) / (F(1) ** 2 * R * t))
        """,
    ),
)
