package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1940 to 1950: a codebreaking count, a linear program, a board game and a game of Nim, each a small program. */
val db10Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.PROJECT,
        section = "Codebreaking",
        title = "Colossus-style counting of agreements",
        originalTitle = "Counting agreements between a tape and a wheel pattern, as Colossus did (1943)",
        author = "Written as a teaching example",
        year = 1943,
        summary = "Two bit streams are compared at every offset, and the offset with the most agreements is reported, as Colossus did with a tape and wheel patterns.",
        details = "Colossus compared a tape of intercepted bits with patterns generated for wheel settings, and it counted how often the two streams agreed. The project does the same for a short repeating pattern and a message that starts at an unknown offset, with two damaged bits, and it checks that the offset is found. The data is made up, so the output is fixed.",
        instructions = """
            1. Save the example as colossus_count.py.
            2. Run it: python3 colossus_count.py
        """,
        example = """
            W = "1101001"                                         # the wheel pattern, repeated along the tape
            M = [W[(i + 3) % 7] for i in range(21)]               # the message: the pattern started at offset 3
            M[5] = "0" if M[5] == "1" else "1"                    # two bits damaged on the tape
            M[12] = "0" if M[12] == "1" else "1"
            counts = {s: sum(M[i] == W[(i + s) % 7] for i in range(21)) for s in range(7)}
            best = max(counts, key=counts.get)
            print("best offset:", best)
            print("agreements:", counts[best], "of", len(M))
            print("every other offset agrees on at most 11 positions:", all(counts[s] <= 11 for s in counts if s != best))
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.PROJECT,
        section = "Optimization",
        title = "Dantzig's simplex method for a small linear program",
        originalTitle = "Linear programming and the simplex method (Dantzig, 1947)",
        author = "Written as a teaching example",
        year = 1947,
        summary = "The simplex method moves from corner to corner of the feasible region, improving the objective at each pivot, until no pivot helps.",
        details = "George Dantzig formulated planning problems as linear programs in 1947, and the simplex method solves them. The project maximizes three x plus two y under two limits, with exact fractions for every pivot, and reports the optimum. The optimum is 12, reached at x equal to 4 and y equal to 0.",
        instructions = """
            1. Save the example as simplex.py.
            2. Run it: python3 simplex.py
        """,
        example = """
            from fractions import Fraction as F

            def simplex(c, A, b):
                # Tableau for: maximize c.x subject to A x <= b and x >= 0, with b >= 0 so the origin is feasible.
                m, n = len(A), len(c)
                T = [[F(x) for x in A[i]] + [F(int(i == k)) for k in range(m)] + [F(b[i])] for i in range(m)]
                z = [F(-x) for x in c] + [F(0)] * m + [F(0)]
                basis = [n + i for i in range(m)]
                while True:
                    col = next((j for j in range(n + m) if z[j] < 0), None)   # first column with a negative cost
                    if col is None:
                        break
                    rows = [i for i in range(m) if T[i][col] > 0]
                    row = min(rows, key=lambda i: (T[i][-1] / T[i][col], basis[i]))
                    piv = T[row][col]
                    T[row] = [x / piv for x in T[row]]
                    for i in range(m):
                        if i != row and T[i][col] != 0:
                            f = T[i][col]
                            T[i] = [x - f * y for x, y in zip(T[i], T[row])]
                    f = z[col]
                    z = [x - f * y for x, y in zip(z, T[row])]
                    basis[row] = col
                x = [F(0)] * n
                for i, var in enumerate(basis):
                    if var < n:
                        x[var] = T[i][-1]
                return z[-1], x

            value, point = simplex([3, 2], [[1, 1], [1, 3]], [4, 6])
            print("optimum value:", value)
            print("at x and y:", point[0], point[1])
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.PROJECT,
        section = "Board games",
        title = "A Hex winner check on a small board",
        originalTitle = "Hex, invented by Piet Hein (1942)",
        author = "Written as a teaching example",
        year = 1942,
        summary = "A Hex board is won when one colour links its two opposite sides, and a flood fill from one side finds whether that link exists.",
        details = "Piet Hein introduced Hex in Denmark in 1942, and John Nash later studied it. The project checks two small boards with a flood fill that uses the hexagonal neighbour rule, and it prints whether each colour wins. Blue connects the top and bottom rows, and red connects the left and right columns.",
        instructions = """
            1. Save the example as hex_check.py.
            2. Run it: python3 hex_check.py
        """,
        example = """
            def winner(board, colour, axis):
                # Flood fill from the first side of the board; the colour wins if the fill reaches the far side.
                n = len(board)
                neighbours = [(0, 1), (0, -1), (1, 0), (-1, 0), (-1, 1), (1, -1)]
                coord = lambda r, c: (r, c)[axis]
                stack = [(r, c) for r in range(n) for c in range(n) if board[r][c] == colour and coord(r, c) == 0]
                seen = set(stack)
                while stack:
                    r, c = stack.pop()
                    if coord(r, c) == n - 1:
                        return True
                    for dr, dc in neighbours:
                        nr, nc = r + dr, c + dc
                        if 0 <= nr < n and 0 <= nc < n and board[nr][nc] == colour and (nr, nc) not in seen:
                            seen.add((nr, nc))
                            stack.append((nr, nc))
                return False

            first = ["B..", "BB.", ".BR"]
            second = ["RRR", "...", "..."]
            print("first board, blue top to bottom:", winner(first, "B", 0))
            print("first board, red left to right:", winner(first, "R", 1))
            print("second board, red left to right:", winner(second, "R", 1))
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.PROJECT,
        section = "Games",
        title = "Nim with the nim-sum",
        originalTitle = "Nimatron, the relay Nim player shown in 1940",
        author = "Written as a teaching example",
        year = 1940,
        summary = "Perfect Nim play uses the bitwise XOR of the heap sizes, and the project plays a whole game with that rule against a simple opponent.",
        details = "Westinghouse showed its relay machine, the Nimatron, at the 1940 New York World's Fair. This project does not copy the Nimatron. It uses the nim-sum rule, which finds a winning move whenever one exists, and it prints every move of a short game from the heaps 1, 3 and 5.",
        instructions = """
            1. Save the example as nim.py.
            2. Run it: python3 nim.py
        """,
        example = """
            from functools import reduce
            from operator import xor

            def winning_move(heaps):
                # The nim-sum is the XOR of the heaps; reduce one heap so that the nim-sum becomes zero.
                total = reduce(xor, heaps, 0)
                for i, size in enumerate(heaps):
                    target = size ^ total
                    if target < size:
                        return i, size - target
                return None

            heaps = [1, 3, 5]
            turn = "computer"
            last = None
            while sum(heaps):
                if turn == "computer":
                    i, take = winning_move(heaps)
                else:
                    i = next(j for j, size in enumerate(heaps) if size > 0)
                    take = 1
                heaps[i] -= take
                print(f"{turn} takes {take} from heap {i + 1}, heaps {heaps}")
                last = turn
                turn = "opponent" if turn == "computer" else "computer"
            print(f"{last} took the last object")
        """,
    ),
)
