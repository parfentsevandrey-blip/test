package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1870 to 1880: subgroup counts in S4, a Jordan block and the ideal efficiency of a four-stroke cycle. */
val db17Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.FLEX,
        section = "Group theory",
        title = "Sylow's count in the symmetric group S4",
        originalTitle = "Theoremes sur les groupes de substitutions (1872)",
        author = "Ludwig Sylow",
        year = 1872,
        summary = "The number of Sylow subgroups for a prime p is 1 modulo p and divides the order of the group, and the program counts them in S4.",
        details = "Sylow's 1872 theorems describe the subgroups of prime power order in a finite group. The example lists the permutations of four objects, finds the subgroups of order 3 and of order 8, and checks that their counts meet Sylow's congruences.",
        instructions = """
            1. Save the example as sylow.py.
            2. Run it: python3 sylow.py
        """,
        example = """
            from itertools import permutations

            IDENTITY = (0, 1, 2, 3)

            def compose(p, q):
                # The permutation that applies q first and then p.
                return tuple(p[q[i]] for i in range(4))

            def order(p):
                k, q = 1, p
                while q != IDENTITY:
                    q = compose(p, q)
                    k += 1
                return k

            def generated(gens):
                group = {IDENTITY}
                frontier = [IDENTITY]
                while frontier:
                    x = frontier.pop()
                    for g in gens:
                        y = compose(g, x)
                        if y not in group:
                            group.add(y)
                            frontier.append(y)
                return frozenset(group)

            S4 = list(permutations(range(4)))
            three = {generated([g]) for g in S4 if order(g) == 3}
            two = set()
            for g in S4:
                for h in S4:
                    if order(g) in (1, 2, 4) and order(h) in (1, 2, 4):
                        group = generated([g, h])
                        if len(group) == 8:
                            two.add(group)
            n3, n2 = len(three), len(two)
            print("Sylow 3-subgroups of S4:", n3)
            print("Sylow 2-subgroups of S4 (order 8):", n2)
            print("Sylow congruences hold:", n3 % 3 == 1 and 8 % n3 == 0 and n2 % 2 == 1 and 3 % n2 == 0)
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.FLEX,
        section = "Linear algebra",
        title = "A Jordan block for a repeated eigenvalue",
        originalTitle = "Traite des substitutions et des equations algebriques (1870)",
        author = "Camille Jordan",
        year = 1870,
        summary = "A matrix with a repeated eigenvalue that cannot be diagonalised has a Jordan block, and the program finds it in a two by two example.",
        details = "Jordan's 1870 treatise set out the canonical form of linear substitutions, which is now called the Jordan normal form. The example takes the matrix with eigenvalue 2 and a single off-diagonal one, and checks that its shifted version has rank one and squares to zero.",
        instructions = """
            1. Save the example as jordan.py.
            2. Run it: python3 jordan.py
        """,
        example = """
            def mat_mul(X, Y):
                return [[sum(X[i][k] * Y[k][j] for k in range(2)) for j in range(2)] for i in range(2)]

            def rank2(M):
                # Rank of a two by two integer matrix: 0, 1 or 2.
                if all(x == 0 for row in M for x in row):
                    return 0
                det = M[0][0] * M[1][1] - M[0][1] * M[1][0]
                return 2 if det != 0 else 1

            A = [[2, 1], [0, 2]]
            N = [[A[i][j] - (2 if i == j else 0) for j in range(2)] for i in range(2)]
            N2 = mat_mul(N, N)
            print("N = A - 2I:", N)
            print("rank of N:", rank2(N))
            print("N squared is zero:", all(x == 0 for row in N2 for x in row))
            print("one Jordan block of size 2 for eigenvalue 2:", rank2(N) == 1 and all(x == 0 for row in N2 for x in row))
        """,
    ),

    entry(
        databaseId = 17,
        kind = Kind.FLEX,
        section = "Thermodynamic cycles",
        title = "Ideal efficiency of Otto's four-stroke cycle",
        originalTitle = "Otto's four-stroke gas engine",
        author = "Nikolaus August Otto",
        year = 1876,
        summary = "The ideal efficiency of the Otto cycle is one minus the compression ratio to the power of one minus the ratio of specific heats.",
        details = "Otto built a practical four-stroke gas engine in 1876. The example evaluates the air-standard formula for the ideal cycle at three compression ratios and prints that the efficiency rises with the ratio.",
        instructions = """
            1. Save the example as otto.py.
            2. Run it: python3 otto.py
        """,
        example = """
            gamma = 1.4  # ratio of specific heats for air
            efficiencies = []
            for r in (4, 8, 16):
                eta = 1 - r ** (1 - gamma)
                efficiencies.append(eta)
                print(f"compression ratio {r}: ideal efficiency {eta:.3f}")
            print("efficiency rises with compression:", efficiencies == sorted(efficiencies))
        """,
    ),
)
