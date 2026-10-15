package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1850 to 1860: tree counts, molecular weights, cubic residues, matrix identities, approximation and cable delay. */
val db19Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Trees",
        title = "How many tree shapes have n nodes",
        originalTitle = "On the theory of the analytical forms called trees (1857)",
        author = "Arthur Cayley",
        year = 1857,
        summary = "Cayley counted the shapes of trees with a given number of nodes, and the same counts later described the isomers of the alkanes.",
        details = "Cayley's 1857 paper counted tree shapes, and his later work tied the same counts to the isomers of the alkanes. The example lists labelled trees by Pruefer sequences, reduces each one to its shape, and prints the counts with and without the limit of four branches per node.",
        rare = true,
        instructions = """
            1. Save the example as cayley_trees.py.
            2. Run it: python3 cayley_trees.py
        """,
        example = """
            from itertools import product

            def encode(v, parent, adj):
                # Rooted code for the subtree at v: the codes of its children, sorted and wrapped in brackets.
                return "(" + "".join(sorted(encode(w, v, adj) for w in adj[v] if w != parent)) + ")"

            def tree_from_prufer(seq, n):
                # Decode a Pruefer sequence into the edges of a labelled tree on n nodes.
                degree = [1] * n
                for x in seq:
                    degree[x] += 1
                edges = []
                for x in seq:
                    leaf = min(i for i in range(n) if degree[i] == 1)
                    edges.append((leaf, x))
                    degree[leaf] -= 1
                    degree[x] -= 1
                u, v = [i for i in range(n) if degree[i] == 1]
                edges.append((u, v))
                return edges

            def shape_key(n, edges):
                adj = [[] for _ in range(n)]
                for a, b in edges:
                    adj[a].append(b)
                    adj[b].append(a)
                # The smallest code over all roots identifies the tree shape, whatever the labels.
                return min(encode(r, -1, adj) for r in range(n)), max(len(a) for a in adj)

            shapes = {1: {"()": 1}}
            for n in range(2, 8):
                found = {}
                for seq in product(range(n), repeat=n - 2):
                    key, most = shape_key(n, tree_from_prufer(seq, n))
                    found[key] = most
                shapes[n] = found
            print("trees with n nodes:", " ".join(str(len(shapes[n])) for n in range(1, 8)))
            print("with at most four branches per node:", " ".join(str(sum(1 for m in shapes[n].values() if m <= 4)) for n in range(1, 8)))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Molecular weights",
        title = "How Cannizzaro fixes molecular weights from vapour density",
        originalTitle = "Sunto di un corso di filosofia chimica (1858)",
        author = "Stanislao Cannizzaro",
        year = 1858,
        summary = "Cannizzaro's rule takes the molecular weight of a gas as twice its vapour density relative to hydrogen, and the rule agrees with the sums of atomic weights.",
        details = "Cannizzaro's 1858 pamphlet set out the rule, which addressed the long confusion over atomic and molecular weights. The example checks six gases against sums of atomic weights and prints each result.",
        instructions = """
            1. Save the example as cannizzaro.py.
            2. Run it: python3 cannizzaro.py
        """,
        example = """
            from fractions import Fraction

            ATOMIC = {"H": 1, "C": 12, "N": 14, "O": 16}
            GASES = [
                ("H2", Fraction(1), {"H": 2}),
                ("O2", Fraction(16), {"O": 2}),
                ("H2O", Fraction(9), {"H": 2, "O": 1}),
                ("CO2", Fraction(22), {"C": 1, "O": 2}),
                ("CH4", Fraction(8), {"C": 1, "H": 4}),
                ("NH3", Fraction(17, 2), {"N": 1, "H": 3}),
            ]
            ok = True
            for name, density, atoms in GASES:
                # Cannizzaro's rule: the molecular weight is twice the vapour density relative to hydrogen.
                cannizzaro = 2 * density
                summed = sum(ATOMIC[el] * n for el, n in atoms.items())
                ok = ok and cannizzaro == summed
                print(f"{name}: 2 x {density} = {cannizzaro}, atoms sum to {summed}")
            print("all six gases agree:", ok)
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Cubic residues",
        title = "Which numbers are cubes modulo a prime",
        originalTitle = "Beweis des Reciprocitaetssatzes fuer die cubischen Reste (1850)",
        author = "Gotthold Eisenstein",
        year = 1850,
        summary = "The nonzero cubic residues modulo a prime p are a third of the nonzero residues, and Eisenstein's cubic reciprocity law was proved in 1850.",
        details = "Eisenstein published his proof of cubic reciprocity in 1850, working in the ring of integers built from cube roots of unity. The example lists the cubic residues modulo 7, 13 and 19 and checks that each set has (p - 1) / 3 members.",
        instructions = """
            1. Save the example as eisenstein.py.
            2. Run it: python3 eisenstein.py
        """,
        example = """
            def cubic_residues(p):
                # The distinct nonzero cubes modulo the prime p.
                return sorted({pow(x, 3, p) for x in range(1, p)})

            for p in (7, 13, 19):
                values = cubic_residues(p)
                joined = " ".join(str(v) for v in values)
                print(f"cubic residues mod {p}: {joined}")
            print("counts are (p - 1) / 3:", all(len(cubic_residues(p)) == (p - 1) // 3 for p in (7, 13, 19)))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Matrices",
        title = "Cayley-Hamilton check for 2 by 2 matrices",
        originalTitle = "A Memoir on the Theory of Matrices (1858)",
        author = "Arthur Cayley",
        year = 1858,
        summary = "Every 2 by 2 matrix satisfies its own characteristic equation, so A squared minus its trace times A plus its determinant times I is the zero matrix.",
        details = "Cayley verified this relation for 2 by 2 matrices in his 1858 memoir and stated it for larger orders. Frobenius gave the general proof in 1878, and the example checks the 2 by 2 case for two matrices.",
        instructions = """
            1. Save the example as cayley_hamilton.py.
            2. Run it: python3 cayley_hamilton.py
        """,
        example = """
            def mul(A, B):
                return [[A[0][0] * B[0][0] + A[0][1] * B[1][0], A[0][0] * B[0][1] + A[0][1] * B[1][1]],
                        [A[1][0] * B[0][0] + A[1][1] * B[1][0], A[1][0] * B[0][1] + A[1][1] * B[1][1]]]

            def check(A, name):
                tr = A[0][0] + A[1][1]
                det = A[0][0] * A[1][1] - A[0][1] * A[1][0]
                A2 = mul(A, A)
                # Cayley-Hamilton: A^2 - (trace) A + (determinant) I should be the zero matrix.
                result = [[A2[i][j] - tr * A[i][j] + (det if i == j else 0) for j in range(2)] for i in range(2)]
                print(f"{name}: trace {tr}, determinant {det}, A^2 - trace*A + det*I = {result}")
                return result == [[0, 0], [0, 0]]

            ok1 = check([[1, 2], [3, 4]], "A")
            ok2 = check([[2, 0], [0, 3]], "B")
            print("both are zero matrices:", ok1 and ok2)
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Approximation",
        title = "Chebyshev's best straight line for x squared",
        originalTitle = "Sur les questions de minima qui se rattachent a la representation approximative des fonctions (1859)",
        author = "Pafnuty Chebyshev",
        year = 1859,
        summary = "The straight line -1/8 + x comes closest to x squared on the interval from 0 to 1, with error 1/8, and its residuals alternate in sign at three points.",
        details = "Chebyshev's 1859 memoir on minimum questions in the approximation of functions studies the best approximations of this kind. The example searches a grid of straight lines a + bx, identifies the best one and prints its residuals at 0, 1/2 and 1.",
        rare = true,
        instructions = """
            1. Save the example as chebyshev_minimax.py.
            2. Run it: python3 chebyshev_minimax.py
        """,
        example = """
            from fractions import Fraction

            # Approximate f(x) = x^2 on [0, 1] by a straight line a + b x, with the error measured at 17 grid points.
            GRID = [Fraction(k, 16) for k in range(17)]
            INTERCEPTS = [Fraction(k, 8) for k in range(-4, 5)]
            SLOPES = [Fraction(k, 8) for k in range(0, 17)]

            def worst(a, b):
                return max(abs(x * x - (a + b * x)) for x in GRID)

            best = None
            for a in INTERCEPTS:
                for b in SLOPES:
                    err = worst(a, b)
                    if best is None or err < best[0]:
                        best = (err, a, b)
            err, a, b = best
            print(f"best line on the grid: {a} + {b} x")
            print(f"worst error on the grid: {err}")
            residuals = " ".join(str(x * x - (a + b * x)) for x in (Fraction(0), Fraction(1, 2), Fraction(1)))
            print("residuals at 0, 1/2, 1:", residuals)
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.EXAMPLE,
        section = "Submarine cables",
        title = "Kelvin's law for signal delay in a cable",
        originalTitle = "On the theory of the electric telegraph (1855)",
        author = "William Thomson, later Lord Kelvin",
        year = 1855,
        summary = "Thomson's 1855 law makes the delay of a signal in a submarine cable grow with the product of resistance, capacitance and the square of the length.",
        details = "Thomson's paper explained why long cables carried signals slowly, with the delay growing as the square of the length. The example uses illustrative values for resistance and capacitance, and prints the delay for cables of 1000, 2000 and 4000 kilometres.",
        instructions = """
            1. Save the example as kelvin_cable.py.
            2. Run it: python3 kelvin_cable.py
        """,
        example = """
            from fractions import Fraction

            R_PER_KM = Fraction(2)       # ohms per kilometre of conductor (illustrative value)
            C_PER_KM = Fraction(1, 2)    # microfarads per kilometre of insulated core (illustrative value)

            def retardation_seconds(length_km):
                # Delay proportional to R * C * L^2; with ohms, microfarads and kilometres the constant is 10^-6 seconds.
                return R_PER_KM * C_PER_KM * Fraction(length_km) ** 2 * Fraction(1, 10 ** 6)

            lengths = (1000, 2000, 4000)
            times = [retardation_seconds(L) for L in lengths]
            for L, t in zip(lengths, times):
                print(f"length {L} km: delay constant {t} s")
            print("doubling the length multiplies the delay by:", times[1] / times[0])
            print("the delay grows as the square of the length:", times[2] == 16 * times[0])
        """,
    ),
)
