package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1900 to 1910: larger programs for a pricing formula, a telegraph code, an income table and a tide sum. */
val db14Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.PROJECT,
        section = "Finance",
        title = "Bachelier's option price under a normal model",
        originalTitle = "Theorie de la speculation (1900)",
        author = "Louis Bachelier",
        year = 1900,
        summary = "Bachelier priced an option by assuming that prices move like a random walk with a normal spread, which gives a closed formula for the call price.",
        details = "The example implements the closed formula with the normal density and the normal distribution function, then prints three prices: at the money, one unit out of the money and with a wider spread. The values agree with the standard normal tables.",
        instructions = """
            1. Save the example as bachelier.py.
            2. Run it: python3 bachelier.py
        """,
        example = """
            import math

            def phi(x):
                # Standard normal density.
                return math.exp(-x * x / 2) / math.sqrt(2 * math.pi)

            def Phi(x):
                # Standard normal distribution function.
                return 0.5 * (1 + math.erf(x / math.sqrt(2)))

            def call_price(spot, strike, sigma_t):
                # Bachelier's formula for a call when the price moves as an arithmetic Brownian motion over the period.
                d = (spot - strike) / sigma_t
                return (spot - strike) * Phi(d) + sigma_t * phi(d)

            cases = [
                ("at the money", 100, 100, 1),
                ("one below the strike", 100, 101, 1),
                ("wider spread", 100, 95, 10),
            ]
            for label, spot, strike, sigma_t in cases:
                print(f"{label}: {call_price(spot, strike, sigma_t):.4f}")
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.PROJECT,
        section = "Telegraph codes",
        title = "Murray's five-unit code: encode and decode",
        originalTitle = "Printing telegraph with a five-unit code, adapted from Baudot (1901)",
        author = "Donald Murray",
        year = 1901,
        summary = "Each character is sent as five marks, with a shift character for letters, and the program encodes a message and decodes it back.",
        details = "The table uses the five-unit values of the letters and the space in the usual ITA2 chart, which is Murray's code in its standard form. Bit strings are written with the least significant bit on the right, as in those charts. The example encodes HELLO WORLD and decodes it again.",
        instructions = """
            1. Save the example as murray.py.
            2. Run it: python3 murray.py
        """,
        example = """
            # Five-unit values of the letters and the space, as in the usual ITA2 chart.
            # Bit strings are written with the least significant bit on the right.
            LETTERS = {
                "A": 3, "B": 25, "C": 14, "D": 9, "E": 1, "F": 13, "G": 26, "H": 20, "I": 6,
                "J": 11, "K": 15, "L": 18, "M": 28, "N": 12, "O": 24, "P": 22, "Q": 23, "R": 10,
                "S": 5, "T": 16, "U": 7, "V": 30, "W": 19, "X": 29, "Y": 21, "Z": 17, " ": 4,
            }
            LTRS = 31  # letters shift, sent first so that the receiver prints letters

            def to_bits(value):
                return format(value, "05b")

            def encode(text):
                codes = [LTRS] + [LETTERS[ch] for ch in text.upper()]
                return [to_bits(v) for v in codes]

            def decode(bits):
                inverse = {v: k for k, v in LETTERS.items()}
                out = []
                for b in bits:
                    value = int(b, 2)
                    if value != LTRS:
                        out.append(inverse[value])
                return "".join(out)

            message = "HELLO WORLD"
            bits = encode(message)
            print("encoded: " + " ".join(bits))
            print("decoded: " + decode(bits))
            print("round trip: " + str(decode(bits) == message))
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.PROJECT,
        section = "Inequality",
        title = "Lorenz curve and the Gini index for five incomes",
        originalTitle = "Methods of measuring the concentration of wealth (1905)",
        author = "Max Otto Lorenz",
        year = 1905,
        summary = "Sorting incomes and adding them up gives the Lorenz curve, and the area between the curve and the diagonal gives the Gini index.",
        details = "The program reads five incomes, prints the cumulative share of income for each rank, and computes the Gini index two ways: from the area under the curve and from all pairs of incomes. Both give 2/5.",
        instructions = """
            1. Save the example as lorenz.py.
            2. Run it: python3 lorenz.py
        """,
        example = """
            from fractions import Fraction as F

            incomes = sorted([10, 1, 4, 2, 3])   # five incomes, poorest first
            n = len(incomes)
            total = sum(incomes)
            print("sorted incomes:", incomes, "total:", total)

            # Lorenz points: the poorest i people hold a share of the total income.
            points = [(F(0), F(0))]
            running = 0
            for i, x in enumerate(incomes, start=1):
                running += x
                points.append((F(i, n), F(running, total)))
                print(f"poorest {i} of {n} people hold {F(running, total)} of income")

            # Area under the Lorenz curve by the trapezoid rule; the Gini index is 1 minus twice the area.
            area = sum((points[i][1] + points[i - 1][1]) / 2 * (points[i][0] - points[i - 1][0])
                       for i in range(1, len(points)))
            gini_area = 1 - 2 * area
            print("Gini from the area:", gini_area)

            # The same index from all pairs: mean absolute difference divided by twice the mean.
            mean = F(total, n)
            pair_sum = sum(abs(x - y) for x in incomes for y in incomes)
            gini_pairs = F(pair_sum, 2 * n * n) / mean
            print("Gini from all pairs:", gini_pairs)
            print("the two agree:", gini_area == gini_pairs)
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.PROJECT,
        section = "Harmonic analysis",
        title = "Tide heights by adding harmonic terms",
        originalTitle = "Universal Tide Predictor, built by Lege and Co., London (1906-1908)",
        author = "Edward Roberts",
        year = 1908,
        summary = "Tide heights come from adding cosine terms with fixed speeds, the sum that Roberts's machine evaluated mechanically.",
        details = "This is a Python simulation of the harmonic sum behind the Roberts machine, with two invented constituents, a mean level of 2 metres and speeds of 30 and 60 degrees per hour. It prints heights for hours 0 to 4 and finds the high and low water over one 12-hour cycle.",
        rare = true,
        instructions = """
            1. Save the example as tide.py.
            2. Run it: python3 tide.py
        """,
        example = """
            import math

            MEAN_LEVEL = 2.0   # metres, invented for the example

            def height(hours):
                # Two invented constituents: speeds of 30 and 60 degrees per hour, amplitudes 1.0 and 0.5.
                a = math.radians(30 * hours)
                b = math.radians(60 * hours)
                return MEAN_LEVEL + math.cos(a) + 0.5 * math.cos(b)

            for t in range(5):
                print(f"hour {t}: {height(t):.4f} m")

            # Scan one 12-hour cycle in steps of a quarter hour, then find the high and low water.
            samples = [(height(i / 4), i / 4) for i in range(48)]
            high = max(v for v, _ in samples)
            low = min(v for v, _ in samples)
            high_times = [t for v, t in samples if abs(v - high) < 1e-9]
            low_times = [t for v, t in samples if abs(v - low) < 1e-9]
            print(f"high water {high:.4f} m at hours {high_times}")
            print(f"low water {low:.4f} m at hours {low_times}")
        """,
    ),
)
