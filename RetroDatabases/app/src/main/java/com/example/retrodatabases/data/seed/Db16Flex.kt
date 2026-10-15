package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1880 to 1890: labelled trees counted by brute force, Heawood's colour bound and a space-filling curve on a grid. */
val db16Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.FLEX,
        section = "Trees",
        title = "Labelled trees counted by Cayley's formula",
        originalTitle = "A Theorem on Trees",
        author = "Arthur Cayley",
        year = 1889,
        summary = "On n labelled vertices there are n to the power n minus 2 trees, and a brute-force count for small n confirms the formula.",
        details = "Cayley's 1889 note gave the count of labelled trees on n vertices as n to the power n minus 2. The example checks every set of n minus 1 edges and keeps those that connect all vertices, for n from 2 to 6. The printed counts are 1, 3, 16, 125 and 1296.",
        instructions = """
            1. Save the example as trees.py.
            2. Run it: python3 trees.py
        """,
        example = """
            from itertools import combinations

            def is_tree(n, edges):
                # n - 1 edges on n vertices form a tree exactly when they never close a cycle.
                parent = list(range(n))
                def find(x):
                    while parent[x] != x:
                        x = parent[x]
                    return x
                for a, b in edges:
                    ra, rb = find(a), find(b)
                    if ra == rb:
                        return False
                    parent[ra] = rb
                return True

            for n in range(2, 7):
                all_edges = list(combinations(range(n), 2))
                count = sum(1 for chosen in combinations(all_edges, n - 1) if is_tree(n, chosen))
                print(f"n = {n}: {count} trees")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.FLEX,
        section = "Colouring",
        title = "Heawood's colour bound for surfaces",
        originalTitle = "Map-Colour Theorem",
        author = "Percy John Heawood",
        year = 1890,
        summary = "A map on a surface of genus g needs at most the number of colours given by Heawood's formula, which is 4 on the sphere and 7 on the torus.",
        details = "Heawood's 1890 paper found a flaw in Kempe's 1879 proof of the four colour theorem and gave a bound for surfaces with g handles. The bound is the integer part of (7 plus the square root of 1 plus 48 g) divided by 2. The example prints the bound for genus 0 to 4, using the integer square root so that no rounding is needed.",
        instructions = """
            1. Save the example as heawood.py.
            2. Run it: python3 heawood.py
        """,
        example = """
            from math import isqrt

            # Heawood's bound: a map on a surface of genus g needs at most H(g) colours,
            # where H(g) = floor((7 + sqrt(1 + 48 g)) / 2). The integer square root gives the same floor.
            for g in range(5):
                h = (7 + isqrt(1 + 48 * g)) // 2
                print(f"genus {g}: {h} colours")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.FLEX,
        section = "Space-filling curves",
        title = "Peano's space-filling curve on a grid",
        originalTitle = "Sur une courbe, qui remplit toute une aire plane",
        author = "Giuseppe Peano",
        year = 1890,
        summary = "A curve that passes through every cell of a grid, one step to a neighbouring cell at a time, built by repeating a three-by-three pattern.",
        details = "Peano published the first space-filling curve in 1890 in Mathematische Annalen, using a three-by-three pattern of blocks in each level. The example builds the curve for levels 1 to 3 and checks that every step moves to a neighbouring cell. The end cell is the far corner of the grid, as the construction requires.",
        instructions = """
            1. Save the example as peano.py.
            2. Run it: python3 peano.py
        """,
        example = """
            def peano(n):
                # Level n visits every cell of a 3^n by 3^n grid, one neighbouring step at a time.
                if n == 0:
                    return [(0, 0)]
                sub = peano(n - 1)
                size = 3 ** (n - 1)
                blocks = [(0, 0), (0, 1), (0, 2), (1, 2), (1, 1), (1, 0), (2, 0), (2, 1), (2, 2)]
                turns = ["id", "flip_x", "id", "flip_y", "turn", "flip_y", "id", "flip_x", "id"]
                cells = []
                for (bx, by), turn in zip(blocks, turns):
                    for x, y in sub:
                        if turn == "flip_x":
                            x, y = size - 1 - x, y
                        elif turn == "flip_y":
                            x, y = x, size - 1 - y
                        elif turn == "turn":
                            x, y = size - 1 - x, size - 1 - y
                        cells.append((bx * size + x, by * size + y))
                return cells

            def unit_steps(cells):
                return all(abs(a[0] - b[0]) + abs(a[1] - b[1]) == 1 for a, b in zip(cells, cells[1:]))

            for n in range(1, 4):
                cells = peano(n)
                print(f"level {n}: {len(cells)} cells, {len(cells) - 1} steps, unit steps: {unit_steps(cells)}, ends at {cells[-1]}")
        """,
    ),
)
