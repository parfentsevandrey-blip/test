package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 1960 to 1970: showpieces that squeeze the most out of a few lines of code, from line drawing to cycle finding. */
val db7Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.FLEX,
        section = "Graphics",
        title = "Bresenham's line in one loop",
        originalTitle = "Bresenham's line algorithm (1962, published 1965)",
        author = "Jack Bresenham at IBM",
        year = 1962,
        summary = "A line drawn with whole numbers only: an error term decides when to step sideways, so the loop needs no multiplication or division.",
        details = "Bresenham worked the method out at IBM in 1962 for a plotter driven by an IBM 1401, and he published it in 1965 in the IBM Systems Journal. The example draws a shallow line on a small grid.",
        instructions = """
            1. Save the example as line.py.
            2. Run it: python3 line.py
        """,
        example = """
            # Step along the longer axis; the error term decides when to move the other coordinate.
            def bresenham(x0, y0, x1, y1):
                points = []
                dx, dy = abs(x1 - x0), -abs(y1 - y0)
                sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
                err = dx + dy
                while True:
                    points.append((x0, y0))
                    if x0 == x1 and y0 == y1:
                        break
                    e2 = 2 * err
                    if e2 >= dy:
                        err += dy
                        x0 += sx
                    if e2 <= dx:
                        err += dx
                        y0 += sy
                return points

            pts = set(bresenham(0, 0, 9, 3))
            for y in range(4):
                print("".join("#" if (x, y) in pts else "." for x in range(10)))
        """,
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.FLEX,
        section = "Cycles",
        title = "Tortoise and hare for cycles",
        originalTitle = "Floyd's cycle-finding method (1967)",
        author = "Robert Floyd",
        year = 1967,
        summary = "Two pointers walk the same sequence, one twice as fast as the other, and they meet inside any loop. A second pass then finds where the loop begins.",
        details = "Robert Floyd's method is usually dated to 1967. The example finds where a sequence of numbers starts to repeat and how long the repeating part is.",
        instructions = """
            1. Save the example as cycle.py.
            2. Run it: python3 cycle.py
        """,
        example = """
            def step(x):
                return (x * x + 1) % 255

            def find_cycle(start):
                # Phase 1: the hare moves twice as fast as the tortoise, until they meet inside the cycle.
                tortoise = step(start)
                hare = step(step(start))
                while tortoise != hare:
                    tortoise = step(tortoise)
                    hare = step(step(hare))
                # Phase 2: start again from the beginning; they meet where the cycle begins.
                mu = 0
                tortoise = start
                while tortoise != hare:
                    tortoise = step(tortoise)
                    hare = step(hare)
                    mu += 1
                # Phase 3: go once around the cycle to measure its length.
                lam = 1
                hare = step(tortoise)
                while tortoise != hare:
                    hare = step(hare)
                    lam += 1
                return mu, lam

            mu, lam = find_cycle(3)
            print(f"the sequence starts repeating after {mu} steps, with a cycle of length {lam}")
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.FLEX,
        section = "Matrices",
        title = "Strassen's seven multiplications",
        originalTitle = "Strassen's algorithm for 2 by 2 matrices (1969)",
        author = "Volker Strassen",
        year = 1969,
        summary = "A way to multiply two 2 by 2 matrices with seven multiplications instead of eight, the first step towards faster multiplication of large matrices.",
        details = "Strassen published the method in 1969. Its extra additions cost time on small matrices, but the saving grows when the method is applied recursively to blocks. The example checks the result against the usual formula.",
        instructions = """
            1. Save the example as strassen.py.
            2. Run it: python3 strassen.py
        """,
        example = """
            def strassen(a, b):
                (a11, a12), (a21, a22) = a
                (b11, b12), (b21, b22) = b
                m1 = (a11 + a22) * (b11 + b22)
                m2 = (a21 + a22) * b11
                m3 = a11 * (b12 - b22)
                m4 = a22 * (b21 - b11)
                m5 = (a11 + a12) * b22
                m6 = (a21 - a11) * (b11 + b12)
                m7 = (a12 - a22) * (b21 + b22)
                return ((m1 + m4 - m5 + m7, m3 + m5), (m2 + m4, m1 - m2 + m3 + m6))

            def naive(a, b):
                return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(2)) for j in range(2)) for i in range(2))

            a = ((1, 2), (3, 4))
            b = ((5, 6), (7, 8))
            print(strassen(a, b))
            print(strassen(a, b) == naive(a, b))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.FLEX,
        section = "Sets",
        title = "Union-find with path halving",
        originalTitle = "Equivalence classes with trees (Galler and Fischer, 1964)",
        author = "Bernard Galler and Michael Fischer",
        year = 1964,
        summary = "A way to keep groups of items that have been joined together, where each group is a tree and a find walks up to its root. Path halving keeps the trees shallow.",
        details = "Galler and Fischer described equivalence classes with trees in 1964. The path-halving step, which makes every second node point to its grandparent, was refined later. The example joins a few pairs and prints the groups.",
        instructions = """
            1. Save the example as union_find.py.
            2. Run it: python3 union_find.py
        """,
        example = """
            parent = list(range(8))

            def find(x):
                while parent[x] != x:
                    parent[x] = parent[parent[x]]    # path halving: skip every other step upwards
                    x = parent[x]
                return x

            def union(a, b):
                ra, rb = find(a), find(b)
                if ra != rb:
                    parent[rb] = ra

            for a, b in [(0, 1), (2, 3), (1, 3), (5, 6)]:
                union(a, b)

            groups = {}
            for x in range(8):
                groups.setdefault(find(x), []).append(x)
            print(sorted(groups.values()))
        """,
    ),
)
