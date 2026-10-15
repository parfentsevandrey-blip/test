package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1900 to 1910: a curve with exact lengths, a prime test from 1909 and a count on small groups. */
val db14Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.FLEX,
        section = "Curves",
        title = "Koch snowflake: side count and perimeter",
        originalTitle = "Sur une courbe continue sans tangente, obtenue par une construction geometrique elementaire (1904)",
        author = "Helge von Koch",
        year = 1904,
        summary = "Each side is replaced by four sides a third as long, so after n steps there are 3 times 4 to the n sides and the perimeter grows by 4/3 each step.",
        details = "Koch described the curve in 1904 as a continuous curve with no tangent. The example keeps every side length as an exact fraction and prints the number of sides and the perimeter at each level. The perimeter grows without limit, but the area stays finite.",
        instructions = """
            1. Save the example as koch.py.
            2. Run it: python3 koch.py
        """,
        example = """
            from fractions import Fraction

            # Each side is replaced by four sides, each one third as long. The perimeter is the sum of the side lengths.
            sides = [Fraction(1)] * 3          # the starting triangle: three sides of length 1
            perimeters = []
            for level in range(5):
                perimeter = sum(sides)
                perimeters.append(perimeter)
                print(f"level {level}: {len(sides)} sides, perimeter {perimeter}")
                sides = [length / 3 for length in sides for _ in range(4)]
            print("ratio per level:", perimeters[1] / perimeters[0])
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "Wieferich primes below 5000",
        originalTitle = "Zum letzten Fermat'schen Theorem (1909)",
        author = "Arthur Wieferich",
        year = 1909,
        summary = "A prime p is a Wieferich prime when p squared divides 2^(p-1) - 1, and the program finds those below 5000.",
        details = "Wieferich's 1909 paper tied this condition to Fermat's Last Theorem. Only 1093 and 3511 are known. The example checks every prime below 5000 with modular powers.",
        instructions = """
            1. Save the example as wieferich.py.
            2. Run it: python3 wieferich.py
        """,
        example = """
            def is_prime(n):
                if n < 2:
                    return False
                i = 2
                while i * i <= n:
                    if n % i == 0:
                        return False
                    i += 1
                return True

            LIMIT = 5000
            primes = [p for p in range(2, LIMIT) if is_prime(p)]
            print(f"primes below {LIMIT}: {len(primes)}")
            # A Wieferich prime p has p squared dividing 2^(p-1) - 1, which pow() checks with modular arithmetic.
            found = [p for p in primes if pow(2, p - 1, p * p) == 1]
            print("Wieferich primes: " + " ".join(str(p) for p in found))
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.FLEX,
        section = "Latin squares",
        title = "Complete mappings of Z6 (36 officers)",
        originalTitle = "Le probleme des 36 officiers (1900)",
        author = "Gaston Tarry",
        year = 1900,
        summary = "Counts the complete mappings of the cyclic groups of orders 3 to 6: there are none for even orders, which fits Tarry's 1900 result on order 6.",
        details = "Tarry's 1900 note settled order 6 for Graeco-Latin squares, and the full proof was printed in 1901. The example counts complete mappings of Z3 to Z6 by brute force. Z4 and Z6 have none, and a parity argument shows that no cyclic group of even order has one.",
        instructions = """
            1. Save the example as tarry.py.
            2. Run it: python3 tarry.py
        """,
        example = """
            from itertools import permutations

            def complete_mappings(n):
                # A complete mapping of Z_n is a permutation s such that x + s(x), taken mod n, is also a permutation.
                count = 0
                for s in permutations(range(n)):
                    if len({(x + s[x]) % n for x in range(n)}) == n:
                        count += 1
                return count

            for n in (3, 4, 5, 6):
                print(f"Z{n}: {complete_mappings(n)} complete mappings")
        """,
    ),
)
