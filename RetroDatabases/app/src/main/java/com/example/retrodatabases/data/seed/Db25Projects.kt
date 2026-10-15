package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1790 to 1800: a prime-count estimate, old French lengths, a survey chain and change from the 1792 coins. */
val db25Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.PROJECT,
        section = "Prime numbers",
        title = "Legendre's prime-count formula",
        originalTitle = "Essai sur la theorie des nombres (1798)",
        author = "Adrien-Marie Legendre",
        year = 1798,
        summary = "Legendre's 1798 estimate for the prime count, x divided by ln x minus 1.08366, is close to the true count for large x.",
        details = "Legendre gave the estimate in his 1798 Essai, with the constant 1.08366 chosen to fit the prime tables of the time. The project tabulates it beside the true prime count for powers of ten, so the reader can set it against Gauss's guess.",
        instructions = """
            1. Save the project as legendre_estimate.py.
            2. Run it: python3 legendre_estimate.py
        """,
        example = """
            import math

            def count_primes(limit):
                sieve = [True] * (limit + 1)
                sieve[0] = sieve[1] = False
                for i in range(2, int(limit ** 0.5) + 1):
                    if sieve[i]:
                        for j in range(i * i, limit + 1, i):
                            sieve[j] = False
                return sum(sieve)

            for k in range(1, 7):
                x = 10 ** k
                estimate = x / (math.log(x) - 1.08366)
                print(f"x = 10^{k}: primes {count_primes(x)}, Legendre estimate {estimate:.1f}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.PROJECT,
        section = "Measures",
        title = "Old French units converted to metres",
        originalTitle = "Pied du roi, toise and pouce of the royal measures (before 1795)",
        author = "Royal measures of France",
        year = 1795,
        summary = "The royal foot, toise and pouce were converted to metres by fixed ratios, so that old lengths could be set beside the new unit.",
        details = "A toise was six royal feet, a foot was twelve pouces, and the royal foot was about 0.3248 metres long. The project converts three lengths given in toises, feet and pouces to metres with fixed ratios and prints each result to three decimals.",
        instructions = """
            1. Save the project as toise.py.
            2. Run it: python3 toise.py
        """,
        example = """
            from fractions import Fraction

            PIED = Fraction(324839, 1000000)    # royal foot in metres, taken as 0.324839
            POUCE = PIED / 12
            TOISE = 6 * PIED

            def to_metres(toises, pieds, pouces):
                return toises * TOISE + pieds * PIED + pouces * POUCE

            for toises, pieds, pouces in [(1, 0, 0), (3, 2, 5), (10, 4, 11)]:
                metres = float(to_metres(toises, pieds, pouces))
                print(f"{toises} toise {pieds} pied {pouces} pouce = {metres:.3f} m")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.PROJECT,
        section = "Surveys",
        title = "A chain of triangles from one baseline",
        originalTitle = "Principal triangulation, solved triangle by triangle (1791)",
        author = "Board of Ordnance",
        year = 1791,
        summary = "Each triangle in a survey chain is solved with the law of sines from one known side and two measured angles, so distances grow outward from the baseline.",
        details = "The Ordnance Survey solved long chains of triangles from a measured baseline. The project starts from a side of 1000 metres, solves two triangles with given angles by the law of sines, and prints the sides it finds.",
        instructions = """
            1. Save the project as chain.py.
            2. Run it: python3 chain.py
        """,
        example = """
            import math

            AB = 1000.0
            # Triangle ABC has all angles 60 degrees, so AC is AB times sin 60 over sin 60.
            AC = AB * math.sin(math.radians(60)) / math.sin(math.radians(60))
            print(f"AC = {AC:.3f} m")
            # Second triangle ACD: angle at A is 45, angle at C is 60, so the angle at D is 75.
            AD = AC * math.sin(math.radians(60)) / math.sin(math.radians(75))
            CD = AC * math.sin(math.radians(45)) / math.sin(math.radians(75))
            print(f"AD = {AD:.3f} m")
            print(f"CD = {CD:.3f} m")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.PROJECT,
        section = "Coinage",
        title = "Making change with the 1792 denominations",
        originalTitle = "Coins of the Coinage Act of 1792",
        author = "Congress of the United States",
        year = 1792,
        summary = "The coins of the 1792 act, from the cent up to the gold eagle, can pay any sum of cents, and a greedy program uses the largest coin first.",
        details = "The act set coins from the cent up to the gold eagle of ten dollars. The project pays three sums in cents with the largest coin first and prints the coins used, leaving out the half cent for simplicity.",
        instructions = """
            1. Save the project as change.py.
            2. Run it: python3 change.py
        """,
        example = """
            DENOMINATIONS = [
                (1000, "eagle"), (500, "half eagle"), (250, "quarter eagle"),
                (100, "dollar"), (50, "half dollar"), (25, "quarter dollar"),
                (10, "disme"), (5, "half disme"), (1, "cent"),
            ]

            def change(amount):
                used = []
                for value, name in DENOMINATIONS:
                    count = amount // value
                    if count:
                        used.append((name, count))
                        amount -= count * value
                return used

            for amount in (1234, 399, 5):
                coins = change(amount)
                text = ", ".join(f"{count} x {name}" for name, count in coins)
                total = sum(count for _, count in coins)
                print(f"{amount} cents: {text} ({total} coins)")
        """,
    ),
)
