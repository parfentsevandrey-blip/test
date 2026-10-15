package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1930 to 1940: short programs for the number, code and machine ideas of the decade. */
val db11Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Numbers",
        title = "Collatz stopping times for small starting numbers",
        originalTitle = "The 3n+1 problem, posed by Lothar Collatz in 1937",
        author = "Lothar Collatz",
        year = 1937,
        summary = "Halve an even number, or take three times it plus one for an odd number, and count the steps until the value reaches 1.",
        details = "Lothar Collatz posed the question in 1937, and nobody has proved that every start reaches 1. The example counts the steps for the numbers 1 to 10 and for 27, which takes 111 steps and climbs above 9000 on the way.",
        instructions = """
            1. Save the example as collatz.py.
            2. Run it: python3 collatz.py
        """,
        example = """
            def stopping_time(n):
                steps = 0
                while n != 1:
                    n = n // 2 if n % 2 == 0 else 3 * n + 1
                    steps += 1
                return steps

            for n in list(range(1, 11)) + [27]:
                print(n, stopping_time(n))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Numbers",
        title = "Benford's first digits in the powers of two",
        originalTitle = "The law of anomalous numbers (Benford, 1938)",
        author = "Frank Benford",
        year = 1938,
        summary = "The first digits of the powers of two favour the small digits, the pattern that Benford's law describes for many collections of numbers.",
        details = "Benford published the law in 1938, after Simon Newcomb had noticed the same pattern in 1881. The example counts the first digits of the first thirty powers of two, which can be checked by hand. The digit 1 leads most often, nine times out of thirty.",
        instructions = """
            1. Save the example as benford.py.
            2. Run it: python3 benford.py
        """,
        example = """
            from collections import Counter

            # Count the leading digit of each power of two from 2 to the power 0 to 2 to the power 29.
            digits = Counter(int(str(2 ** n)[0]) for n in range(30))
            for d in range(1, 10):
                print(d, digits[d])
            print("total", sum(digits.values()))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Codes",
        title = "Encoding a message in the five-bit telegraph code ITA2",
        originalTitle = "International Telegraph Alphabet No. 2 (ITA2), standardised by the CCITT in 1932",
        author = "CCITT, the international telegraph committee",
        year = 1932,
        summary = "A five-bit code gives 32 patterns, enough for the letters and the space, and the message is sent as five marks or spaces per character.",
        details = "The letters use a fixed five-bit table, and two shift codes switch the receiver between letters and figures. The example uses the letter codes and the space, encodes HELLO WORLD, prints the five-bit groups and decodes them again. Some guides date the standard to 1930, but the article that gives the full table says 1932.",
        instructions = """
            1. Save the example as ita2.py.
            2. Run it: python3 ita2.py
        """,
        example = """
            # Five-bit codes of the letters and the space, as in the first column of the ITA2 table.
            ITA2 = {
                "A": "00011", "B": "11001", "C": "01110", "D": "01001", "E": "00001",
                "F": "01101", "G": "11010", "H": "10100", "I": "00110", "J": "01011",
                "K": "01111", "L": "10010", "M": "11100", "N": "01100", "O": "11000",
                "P": "10110", "Q": "10111", "R": "01010", "S": "00101", "T": "10000",
                "U": "00111", "V": "11110", "W": "10011", "X": "11101", "Y": "10101",
                "Z": "10001", " ": "00100",
            }
            DECODE = {bits: letter for letter, bits in ITA2.items()}

            message = "HELLO WORLD"
            groups = [ITA2[c] for c in message]
            print(" ".join(groups))
            decoded = "".join(DECODE[g] for g in groups)
            print("decoded:", decoded)
            print("round trip:", decoded == message)
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Recursion",
        title = "Least roots found by unbounded search",
        originalTitle = "General recursive functions of natural numbers (Kleene, 1936)",
        author = "Stephen Cole Kleene",
        year = 1936,
        summary = "The mu operator finds the least number for which a test holds, by searching upward without a bound, and general recursive functions are built with it.",
        details = "Kleene's 1936 paper defined the general recursive functions with this operator. The example builds addition and multiplication by repetition, then uses the unbounded search to find an integer square root, an integer quotient and the least n whose square reaches 50.",
        instructions = """
            1. Save the example as least_root.py.
            2. Run it: python3 least_root.py
        """,
        example = """
            def add(x, y):
                # Repetition on y: add(x, 0) is x, and each step adds one.
                result = x
                for _ in range(y):
                    result = result + 1
                return result

            def mul(x, y):
                # Repetition on y: mul(x, 0) is 0, and each step adds x.
                result = 0
                for _ in range(y):
                    result = add(result, x)
                return result

            def mu(f):
                # The mu operator: the least n for which f(n) is zero. The search has no upper bound.
                n = 0
                while f(n) != 0:
                    n += 1
                return n

            def isqrt(x):
                return mu(lambda n: 0 if mul(n + 1, n + 1) > x else 1)

            def div(x, d):
                return mu(lambda q: 0 if mul(q + 1, d) > x else 1)

            print("add(17, 5) =", add(17, 5))
            print("mul(6, 7) =", mul(6, 7))
            print("isqrt(50) =", isqrt(50))
            print("div(17, 5) =", div(17, 5))
            print("least n with n*n >= 50:", mu(lambda n: 0 if mul(n, n) >= 50 else 1))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Analog",
        title = "A differential analyser step by step",
        originalTitle = "The differential analyzer, a new machine for solving differential equations (Bush, 1931)",
        author = "Vannevar Bush",
        year = 1931,
        summary = "An integrator adds the slope times a small step to the value at each moment, so a differential equation can be solved step by step.",
        details = "Bush's machine integrated with wheels and discs and was described in 1931. The example models one integrator with fixed steps, not the continuous action of the wheel. It solves y' = y from y = 1, and with steps of a quarter or a half the values are exact, so the output contains no rounding.",
        instructions = """
            1. Save the example as analyser.py.
            2. Run it: python3 analyser.py
        """,
        example = """
            # Each step adds the slope times the step length. The equation is y' = y, so the slope is y.
            h = 0.5
            y = 1.0
            for step in range(1, 5):
                y = y + h * y
                print(f"t={step * h} y={y}")

            h = 0.25
            y = 1.0
            for _ in range(4):
                y = y + h * y
            print("h=0.25 after 4 steps: y=" + str(y))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.EXAMPLE,
        section = "Experiments",
        title = "Yates's algorithm for three factors at two levels",
        originalTitle = "The design and analysis of factorial experiments (Yates, 1937)",
        author = "Frank Yates",
        year = 1937,
        summary = "Yates's algorithm finds the total and all seven effects of a two-level, three-factor experiment in three passes of sums and differences.",
        details = "Yates published the method in 1937. The example runs the three passes on eight treatment totals in standard order, then checks the result against a direct contrast calculation. Each pass needs only additions and subtractions, which made the method practical before electronic calculators.",
        instructions = """
            1. Save the example as yates.py.
            2. Run it: python3 yates.py
        """,
        example = """
            TOTALS = [10, 12, 14, 18, 11, 13, 15, 21]  # (1), a, b, ab, c, ac, bc, abc
            LABELS = ["total", "A", "B", "AB", "C", "AC", "BC", "ABC"]

            def yates_pass(v):
                # Sums of neighbouring pairs first, then their differences.
                sums = [v[2 * i] + v[2 * i + 1] for i in range(len(v) // 2)]
                diffs = [v[2 * i + 1] - v[2 * i] for i in range(len(v) // 2)]
                return sums + diffs

            def yates(totals):
                v = list(totals)
                for _ in range(3):
                    v = yates_pass(v)
                return v

            def contrast(effect):
                # Direct check: a treatment's sign flips for each factor in the effect that it lacks.
                value = 0
                for i, total in enumerate(TOTALS):
                    sign = 1
                    for bit in range(3):
                        if (effect >> bit) & 1 and not (i >> bit) & 1:
                            sign = -sign
                    value += sign * total
                return value

            fast = yates(TOTALS)
            print(" ".join(f"{name}={v}" for name, v in zip(LABELS, fast)))
            print("direct check:", fast == [contrast(e) for e in range(8)])
        """,
    ),
)
