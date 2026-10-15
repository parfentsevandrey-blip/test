package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1950 to 1960: short recipes from the early machines, such as square roots, random sampling, word arithmetic and card sorting. */
val db6Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.EXAMPLE,
        section = "Numbers",
        title = "Square roots by Newton's method",
        originalTitle = "Newton's iteration applied to x squared minus n",
        author = "Written as a teaching example",
        year = 1953,
        summary = "A square root found by repeatedly averaging a guess with the number divided by the guess. Each step roughly doubles the number of correct digits.",
        details = "The method needs only division, addition and halving, so it suits a machine without a square-root instruction, which most early computers lacked. " +
            "Six steps are enough for the three numbers in the example to settle to six decimal places.",
        instructions = """
            1. Save the example as newton.py.
            2. Run it: python3 newton.py
        """,
        example = """
            def newton_sqrt(n, steps=6):
                x = n
                for _ in range(steps):
                    x = (x + n / x) / 2
                return x

            for n in (2, 9, 10):
                print(n, round(newton_sqrt(n), 6))
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.EXAMPLE,
        section = "Random sampling",
        title = "Estimating pi by random points",
        originalTitle = "Monte Carlo estimate of pi",
        author = "Written as a teaching example",
        year = 1953,
        summary = "Random points fall into a unit square, and the share that lands inside the quarter circle gives pi divided by four.",
        details = "Monte Carlo methods were run on the early machines, and the 1953 work on MANIAC used the same kind of random sampling. This small version uses Python's random generator with a fixed seed, so the result is repeatable.",
        instructions = """
            1. Save the example as pi_points.py.
            2. Run it: python3 pi_points.py
        """,
        example = """
            import random

            random.seed(1953)
            samples = 100000
            inside = 0
            for _ in range(samples):
                x = random.random()
                y = random.random()
                if x * x + y * y <= 1.0:
                    inside += 1
            estimate = 4 * inside / samples
            print(f"estimate of pi: {estimate:.2f}")
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.EXAMPLE,
        section = "Word arithmetic",
        title = "36-bit addition in Python",
        originalTitle = "Fixed-width addition on 36-bit words",
        author = "Written as a teaching example",
        year = 1954,
        summary = "Addition on 36-bit words, where the carry out of the top bit is dropped, as in a fixed-width register of the early machines.",
        details = "The mask keeps only the low 36 bits, so a sum wraps around the way it does in a fixed-width register. " +
            "The last line shows 2 to the power 35 plus 2 to the power 35 wrapping to zero, because 2 to the power 36 has no place in a 36-bit word.",
        instructions = """
            1. Save the example as add36.py.
            2. Run it: python3 add36.py
        """,
        example = """
            MASK = (1 << 36) - 1                   # keep only the low 36 bits, as a 36-bit word does

            def add36(a, b):
                return (a + b) & MASK

            print(add36(5, 7))                     # 12
            print(add36(MASK, 1))                  # the sum wraps around to 0
            print(oct(add36(1 << 35, 1 << 35)))    # 2**35 + 2**35 = 2**36, which wraps to 0
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.EXAMPLE,
        section = "Sorting",
        title = "Sorting cards by columns",
        originalTitle = "Radix sort on punched cards",
        author = "Written as a teaching example, after the IBM card sorters",
        year = 1955,
        summary = "A radix sort that works the way a card sorter did: one pass per column, from the last column to the first.",
        details = "A card sorter put each card into one of ten pockets according to the digit punched in one column. Repeating the pass for each column, from right to left, sorts the whole deck, because every pass keeps the order that the previous one produced.",
        instructions = """
            1. Save the example as cards.py.
            2. Run it: python3 cards.py
        """,
        example = """
            # Sort three-digit numbers one column at a time, from the last column to the first.
            def card_sort(numbers, columns=3):
                for column in range(columns):
                    pockets = [[] for _ in range(10)]
                    for number in numbers:
                        digit = (number // 10 ** column) % 10
                        pockets[digit].append(number)
                    numbers = [number for pocket in pockets for number in pocket]
                return numbers

            deck = [482, 17, 905, 61, 237, 490, 100]
            print(card_sort(deck))
        """,
    ),
)
