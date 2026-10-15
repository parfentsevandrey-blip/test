package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1940 to 1950: short programs for the methods of the decade, each one run as a simulation or a small calculation. */
val db10Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Stored program",
        title = "The Manchester Baby's first program, simulated",
        originalTitle = "The Baby's first program of 21 June 1948, rebuilt as a simulation",
        author = "Written as a teaching example",
        year = 1948,
        summary = "A Python simulator with six of the Baby's seven instruction names runs a subtraction loop that finds the largest proper divisor of 2 to the 18th.",
        details = "The 1948 program searched downward from 2 to the 18th and found 131072 in about 52 minutes on the real machine. This simulator uses its own program with the same idea: subtract a candidate until the remainder is zero or too small. It prints how many candidates it tested, which is 131072, and the divisor it found. The store layout and the program are rebuilt, so the example shows the method rather than the 1948 code.",
        instructions = """
            1. Save the example as baby_sim.py.
            2. Run it: python3 baby_sim.py
        """,
        example = """
            # A simulator with six of the seven instruction names of the Manchester Baby (JRP is not needed here).
            # LDN loads the negative of a store line, STO stores the accumulator, SUB subtracts a
            # store line, CMP skips the next instruction when the accumulator is negative,
            # JMP jumps to an address, and STOP halts. The store lines are named cells.
            N, C, R, T, ONE, MINUS_ONE = "N", "C", "R", "T", "ONE", "MINUS_ONE"

            # R holds the negated remainder and C the candidate divisor.
            PROGRAM = [
                ("LDN", R),          # 0: acc = remainder
                ("SUB", C),          # 1: acc = remainder - candidate
                ("CMP", None),       # 2: remainder smaller than candidate: skip the next line
                ("JMP", 5),          # 3: otherwise subtract the candidate
                ("JMP", 9),          # 4: remainder smaller: test whether it is zero
                ("STO", T),          # 5: T = remainder - candidate
                ("LDN", T),          # 6: acc = negated new remainder
                ("STO", R),          # 7: R = negated new remainder
                ("JMP", 0),          # 8: go round again
                ("LDN", R),          # 9: acc = remainder
                ("SUB", ONE),        # 10: acc = remainder - 1
                ("CMP", None),       # 11: remainder was zero: skip the next line
                ("JMP", 14),         # 12: not zero: try the next candidate
                ("STOP", None),      # 13: remainder was zero, so the candidate divides
                ("LDN", C),          # 14: acc = negated candidate
                ("SUB", MINUS_ONE),  # 15: acc = 1 - candidate
                ("STO", T),          # 16: T = 1 - candidate
                ("LDN", T),          # 17: acc = candidate - 1
                ("STO", C),          # 18: next candidate
                ("LDN", N),          # 19: acc = negated 2 to the 18th
                ("STO", R),          # 20: reset the remainder to 2 to the 18th
                ("JMP", 0),          # 21: start the test for the new candidate
            ]

            def run(program, store):
                acc, pc, visits = 0, 0, 0
                while True:
                    op, arg = program[pc]
                    pc += 1
                    if op == "LDN":
                        acc = -store[arg]
                    elif op == "SUB":
                        acc -= store[arg]
                    elif op == "STO":
                        store[arg] = acc
                    elif op == "CMP":
                        if acc < 0:
                            pc += 1
                    elif op == "JMP":
                        pc = arg
                    elif op == "STOP":
                        return store, visits
                    if pc == 14:
                        visits += 1      # a candidate has failed

            store = {N: 262144, C: 262143, R: -262144, T: 0, ONE: 1, MINUS_ONE: -1}
            store, failures = run(PROGRAM, store)
            print("candidates tested:", failures + 1)
            print("largest proper divisor of 262144:", store[C])
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Information",
        title = "Entropy of a short text",
        originalTitle = "A Mathematical Theory of Communication (Shannon, 1948)",
        author = "Written as a teaching example",
        year = 1948,
        summary = "Shannon's entropy gives the average information per letter of a text from the frequency of each letter, and the example text needs about 2.04 bits per letter.",
        details = "The example counts the letters of ABRACADABRA and computes the entropy in bits. Eleven letters with five distinct symbols give 2.040 bits per letter, or 22.444 bits for the whole word. No code can use fewer bits per letter on average than the entropy, for letters with these frequencies.",
        instructions = """
            1. Save the example as entropy.py.
            2. Run it: python3 entropy.py
        """,
        example = """
            from collections import Counter
            from math import log2

            text = "ABRACADABRA"
            counts = Counter(text)
            n = len(text)
            bits = -sum((k / n) * log2(k / n) for k in counts.values())
            print("letters:", n, "distinct:", len(counts))
            print(f"bits per letter: {bits:.3f}")
            print(f"total bits for the text: {bits * n:.3f}")
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Sorting",
        title = "Merging sorted lists, and merge sort",
        originalTitle = "The merging routine in von Neumann's 1945 manuscript",
        author = "Written as a teaching example",
        year = 1945,
        summary = "A merge takes two sorted lists and produces one sorted list, and a merge sort applies the merge recursively; the example sorts six numbers and counts the comparisons.",
        details = "Mark Priestley's study describes a 1945 manuscript by von Neumann with a routine for merging two data sequences, written for a larger merge sort program. The example implements that merge step and the recursion around it, then prints the sorted list and the eleven comparisons it makes.",
        instructions = """
            1. Save the example as merge_sort.py.
            2. Run it: python3 merge_sort.py
        """,
        example = """
            def merge(left, right, count):
                out = []
                i = j = 0
                while i < len(left) and j < len(right):
                    count[0] += 1
                    if left[i] <= right[j]:
                        out.append(left[i])
                        i += 1
                    else:
                        out.append(right[j])
                        j += 1
                return out + left[i:] + right[j:]

            def merge_sort(items, count):
                if len(items) <= 1:
                    return items
                mid = len(items) // 2
                return merge(merge_sort(items[:mid], count), merge_sort(items[mid:], count), count)

            count = [0]
            print(merge_sort([5, 2, 4, 6, 1, 3], count))
            print("comparisons:", count[0])
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Linear algebra",
        title = "Matrix inversion by elimination",
        originalTitle = "Numerical inverting of matrices of high order (Goldstine and von Neumann, 1947)",
        author = "Written as a teaching example",
        year = 1947,
        summary = "Gauss-Jordan elimination turns a matrix and the identity matrix placed beside it into the inverse, and the example checks the result with exact fractions.",
        details = "The method is the elimination that Goldstine and von Neumann analysed for matrices of high order in their 1947 paper. The example inverts a 3 by 3 matrix whose inverse has whole-number entries, using fractions so that no rounding occurs, and then confirms that the matrix times its inverse is the identity.",
        instructions = """
            1. Save the example as invert.py.
            2. Run it: python3 invert.py
        """,
        example = """
            from fractions import Fraction

            def invert(matrix):
                # Gauss-Jordan elimination on the matrix with the identity matrix beside it.
                n = len(matrix)
                rows = []
                for i, row in enumerate(matrix):
                    rows.append([Fraction(x) for x in row] + [Fraction(1 if j == i else 0) for j in range(n)])
                for col in range(n):
                    pivot = next(r for r in range(col, n) if rows[r][col] != 0)
                    rows[col], rows[pivot] = rows[pivot], rows[col]
                    p = rows[col][col]
                    rows[col] = [x / p for x in rows[col]]
                    for r in range(n):
                        if r != col and rows[r][col] != 0:
                            f = rows[r][col]
                            rows[r] = [x - f * y for x, y in zip(rows[r], rows[col])]
                return [row[n:] for row in rows]

            A = [[1, 2, 3], [0, 1, 4], [5, 6, 0]]
            inverse = invert(A)
            for row in inverse:
                print(" ".join(str(int(x)) for x in row))
            product = [[sum(A[i][k] * inverse[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
            print("A times its inverse is the identity:", product == [[1, 0, 0], [0, 1, 0], [0, 0, 1]])
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Sampling",
        title = "Aliasing: a 3 Hz tone sampled at 4 Hz",
        originalTitle = "Communication in the presence of noise (Shannon, 1949)",
        author = "Written as a teaching example",
        year = 1949,
        summary = "Sampled 4 times a second, a 3 Hz tone gives the same numbers as a 1 Hz tone with its sign reversed, so the samples cannot tell the two apart.",
        details = "The sampling theorem says that a band-limited signal can be recovered from samples taken at more than twice its highest frequency. Here the rate is too low, so the 3 Hz tone aliases to 1 Hz. Shannon gave the theorem in the form now used in his 1949 paper, and Whittaker and Nyquist had earlier results on the same question.",
        instructions = """
            1. Save the example as alias.py.
            2. Run it: python3 alias.py
        """,
        example = """
            from math import pi, sin

            def samples(freq, rate, count):
                # Sample a sine wave at the given rate; adding 0.0 turns a negative zero into zero.
                return [round(sin(2 * pi * freq * k / rate), 6) + 0.0 for k in range(count)]

            tone = samples(3, 4, 4)
            alias = samples(1, 4, 4)
            print("3 Hz samples:", " ".join(f"{x:.1f}" for x in tone))
            print("1 Hz samples:", " ".join(f"{x:.1f}" for x in alias))
            same = all(abs(a + b) < 1e-9 for a, b in zip(tone, alias))
            print("the 3 Hz samples are the negative of the 1 Hz samples:", same)
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.EXAMPLE,
        section = "Game theory",
        title = "Saddle point of a two-person game",
        originalTitle = "Theory of Games and Economic Behavior (von Neumann and Morgenstern, 1944)",
        author = "Written as a teaching example",
        year = 1944,
        summary = "A two-person zero-sum game has a saddle point when the best row against the worst case equals the best column against the worst case.",
        details = "The example finds the maximin and minimax values of a 2 by 2 payoff table and reports the saddle point. Von Neumann's minimax theorem, published with Morgenstern in 1944, says that the two values always agree once mixed strategies are allowed. This table has a pure saddle point, so no mixing is needed.",
        instructions = """
            1. Save the example as saddle.py.
            2. Run it: python3 saddle.py
        """,
        example = """
            payoff = [[3, 1], [4, 2]]     # rows are the first player's choices, columns the second player's
            maximin = max(min(row) for row in payoff)
            minimax = min(max(payoff[r][c] for r in range(2)) for c in range(2))
            print("maximin:", maximin)
            print("minimax:", minimax)
            for r in range(2):
                for c in range(2):
                    value = payoff[r][c]
                    if value == min(payoff[r]) and value == max(payoff[k][c] for k in range(2)):
                        print(f"saddle point: row {r + 1}, column {c + 1}, value {value}")
        """,
    ),
)
