package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1850 to 1860: a Gauss-type quadrature check, a schoolgirl schedule, long arithmetic for pi and a dodecahedron circuit count. */
val db19Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.PROJECT,
        section = "Pi by series",
        title = "Shanks's digits of pi by Machin's formula",
        originalTitle = "Contributions to Mathematics, comprising chiefly the Rectification of the Circle (1853)",
        author = "William Shanks",
        year = 1853,
        summary = "Shanks computed pi to hundreds of decimals by Machin's arctangent formula, and the program reproduces the first fifty decimals with integer arithmetic.",
        details = "Shanks published 527 decimal places in 1853, and his value was later extended to 707 places. The program uses Machin's formula, pi = 16 arctan(1/5) - 4 arctan(1/239), with guard digits to keep the last place exact.",
        instructions = """
            1. Save the example as shanks_pi.py.
            2. Run it: python3 shanks_pi.py
        """,
        example = """
            def arctan_inverse(x, digits):
                # arctan(1/x) in fixed point, with 10^digits as the unit: the sum of (-1)^k / ((2k+1) x^(2k+1)).
                unit = 10 ** digits
                total = 0
                power = unit // x
                k = 0
                while power != 0:
                    term = power // (2 * k + 1)
                    total += term if k % 2 == 0 else -term
                    power //= x * x
                    k += 1
                return total

            def machin_pi(digits):
                guard = 10
                scaled = 16 * arctan_inverse(5, digits + guard) - 4 * arctan_inverse(239, digits + guard)
                return scaled // 10 ** guard

            DIGITS = 50
            text = str(machin_pi(DIGITS))
            print(f"pi to {DIGITS} decimals:", text[0] + "." + text[1:])
            print("digits in the integer:", len(text))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.PROJECT,
        section = "Combinatorial design",
        title = "Kirkman's schoolgirls, solved with PG(3,2)",
        originalTitle = "Note on an unanswered prize question (1850)",
        author = "Thomas Penyngton Kirkman",
        year = 1850,
        summary = "Kirkman's fifteen schoolgirls problem asks for seven days of five disjoint triples each, so that every pair meets exactly once, and the program builds such a schedule from the lines of PG(3,2).",
        details = "Kirkman posed the problem in 1850, and resolvable triple systems on fifteen points have been studied ever since. The program lists the lines of PG(3,2) as triples, searches for seven disjoint spreads that use every line once, and checks the resulting schedule.",
        instructions = """
            1. Save the example as kirkman.py.
            2. Run it: python3 kirkman.py
        """,
        example = """
            from itertools import combinations

            # Points of PG(3,2) are the nonzero 4-bit numbers 1 to 15. A line is a triple {a, b, a xor b}.
            lines = []
            for a, b in combinations(range(1, 16), 2):
                c = a ^ b
                if c > b:
                    lines.append((a, b, c))
            print("lines:", len(lines))

            def spreads():
                # A spread is five pairwise disjoint lines, which together cover all 15 points.
                found = []

                def grow(start, chosen, used):
                    if len(chosen) == 5:
                        found.append(tuple(chosen))
                        return
                    for i in range(start, len(lines)):
                        if not (set(lines[i]) & used):
                            grow(i + 1, chosen + [i], used | set(lines[i]))

                grow(0, [], set())
                return found

            def schedule(all_spreads):
                # Seven spreads that between them use every line exactly once: one day per spread.
                chosen = []

                def solve(used_lines):
                    if len(chosen) == 7:
                        return True
                    target = min(i for i in range(len(lines)) if i not in used_lines)
                    for sp in all_spreads:
                        if target in sp and not (set(sp) & used_lines):
                            chosen.append(sp)
                            if solve(used_lines | set(sp)):
                                return True
                            chosen.pop()
                    return False

                solve(set())
                return list(chosen)

            days = schedule(spreads())
            print("days:", len(days))
            print("triples used:", sum(len(d) for d in days))
            pair_count = {}
            for day in days:
                for li in day:
                    a, b, c = lines[li]
                    for p in ((a, b), (a, c), (b, c)):
                        pair_count[p] = pair_count.get(p, 0) + 1
            print("pairs met exactly once:", sum(1 for v in pair_count.values() if v == 1), "of", len(pair_count))
            print("each day is a full split of the 15 girls:", all(len({x for li in day for x in lines[li]}) == 15 for day in days))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.PROJECT,
        section = "Numerical integration",
        title = "Gauss-Legendre rules checked by exact integration",
        originalTitle = "Ueber die Gaussische Quadratur und eine Verallgemeinerung derselben (1858)",
        author = "Elwin Bruno Christoffel",
        year = 1858,
        summary = "Gauss's quadrature picks nodes and weights so that an n-point rule integrates every polynomial of degree up to 2n-1 exactly, and the program checks two such rules.",
        details = "Christoffel's 1858 paper on Gauss's quadrature gave a general treatment of these rules and their weights. The project checks the two-point and three-point rules on the monomials x^0 to x^6, showing exactness up to degree 2n-1 and failure at degree 2n.",
        instructions = """
            1. Save the example as gauss_quadrature.py.
            2. Run it: python3 gauss_quadrature.py
        """,
        example = """
            import math

            # Two-point and three-point Gauss-Legendre rules on [-1, 1]: nodes and weights.
            RULES = {
                2: ([-1 / math.sqrt(3), 1 / math.sqrt(3)], [1.0, 1.0]),
                3: ([-math.sqrt(3 / 5), 0.0, math.sqrt(3 / 5)], [5 / 9, 8 / 9, 5 / 9]),
            }

            def integrate(n, power):
                nodes, weights = RULES[n]
                return sum(w * x ** power for x, w in zip(nodes, weights))

            def exact(power):
                # The integral of x^power over [-1, 1] is 2 / (power + 1) for even powers and 0 for odd powers.
                return 0.0 if power % 2 else 2.0 / (power + 1)

            for n, top in ((2, 3), (3, 5)):
                ok = all(abs(integrate(n, p) - exact(p)) < 1e-12 for p in range(top + 1))
                print(f"{n}-point rule exact up to degree {top}: {ok}")
            for n, degree in ((2, 4), (3, 6)):
                missed = abs(integrate(n, degree) - exact(degree)) > 1e-12
                print(f"{n}-point rule misses degree {degree}: {missed}")
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.PROJECT,
        section = "Hamiltonian cycles",
        title = "Hamilton's icosian game on the dodecahedron",
        originalTitle = "Icosian game (1857)",
        author = "William Rowan Hamilton",
        year = 1857,
        summary = "Hamilton's icosian game asks for a path around the twenty vertices of a dodecahedron, and the program counts the closed circuits by depth-first search.",
        details = "The game's invention is dated 1856 in some accounts and 1857 in others, and Hamilton sold it to a London toymaker in 1859. The program builds the dodecahedron from coordinates, checks that every vertex has three neighbours, and counts the Hamiltonian cycles through vertex 0.",
        instructions = """
            1. Save the example as icosian.py.
            2. Run it: python3 icosian.py
        """,
        example = """
            from itertools import product
            from math import sqrt

            PHI = (1 + sqrt(5)) / 2
            INV = 1 / PHI
            verts = [tuple(float(x) for x in s) for s in product((-1, 1), repeat=3)]
            for s1, s2 in product((-1, 1), repeat=2):
                verts.append((0.0, s1 * INV, s2 * PHI))
                verts.append((s1 * INV, s2 * PHI, 0.0))
                verts.append((s2 * PHI, 0.0, s1 * INV))

            def dist2(p, q):
                return sum((a - b) ** 2 for a, b in zip(p, q))

            edge_len2 = min(dist2(verts[i], verts[j]) for i in range(len(verts)) for j in range(i + 1, len(verts)))
            adj = [[j for j in range(len(verts)) if j != i and abs(dist2(verts[i], verts[j]) - edge_len2) < 1e-9]
                   for i in range(len(verts))]
            print("vertices:", len(verts), "degrees all 3:", all(len(a) == 3 for a in adj))

            count = 0

            def walk(path, visited):
                global count
                v = path[-1]
                if len(path) == len(verts):
                    if 0 in adj[v]:
                        count += 1
                    return
                for w in adj[v]:
                    if w not in visited:
                        visited.add(w)
                        path.append(w)
                        walk(path, visited)
                        path.pop()
                        visited.discard(w)

            walk([0], {0})
            print("Hamiltonian cycles through vertex 0, both directions:", count)
            print("undirected Hamiltonian cycles:", count // 2)
        """,
    ),
)
