package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1790 to 1800: coin weights, a continued fraction of pi, a toy cipher, table interpolation, shutter patterns and grades. */
val db25Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Coinage",
        title = "The 1792 silver to gold ratio",
        originalTitle = "Coinage Act of 2 April 1792, weights of the dollar and the eagle",
        author = "Congress of the United States",
        year = 1792,
        summary = "The dollar and the gold eagle were fixed in pure metal so that silver to gold came to 15 to 1 by weight, and the program checks that ratio exactly.",
        details = "The act fixed the silver dollar at 371 and a quarter grains of pure silver, and the gold eagle at 247 and a half grains of pure gold. The example turns these weights into exact fractions, finds the gold in one dollar's worth, and divides to get the ratio.",
        instructions = """
            1. Save the example as coinage.py.
            2. Run it: python3 coinage.py
        """,
        example = """
            from fractions import Fraction

            silver_dollar = Fraction(1485, 4)     # 371 and a quarter grains of pure silver
            gold_eagle = Fraction(495, 2)         # 247 and a half grains of pure gold, for ten dollars
            gold_per_dollar = gold_eagle / 10
            print(f"silver in the dollar: {float(silver_dollar)} grains")
            print(f"gold per dollar of the eagle: {float(gold_per_dollar)} grains")
            print(f"silver to gold ratio: {silver_dollar / gold_per_dollar}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Irrational numbers",
        title = "The continued fraction of pi",
        originalTitle = "Elements de geometrie (1794)",
        author = "Adrien-Marie Legendre",
        year = 1794,
        summary = "Legendre showed in 1794 that pi cannot be a fraction, so its continued fraction never ends, and the program prints the opening terms.",
        details = "Legendre gave the proof in his 1794 Elements de geometrie. The example turns a 40-digit decimal of pi into an exact fraction, expands it as a continued fraction, and prints the first twelve terms.",
        instructions = """
            1. Save the example as pi_fraction.py.
            2. Run it: python3 pi_fraction.py
        """,
        example = """
            from fractions import Fraction

            # Pi to 40 decimal places, written as an exact fraction.
            PI = Fraction("3.1415926535897932384626433832795028841971")
            x = PI
            terms = []
            for _ in range(12):
                a = x.numerator // x.denominator
                terms.append(a)
                x = 1 / (x - a)
            print("continued fraction of pi: " + ", ".join(str(t) for t in terms))
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Ciphers",
        title = "A toy wheel cipher with three alphabets",
        originalTitle = "Wheel cipher idea of Thomas Jefferson (about 1795)",
        author = "Thomas Jefferson",
        year = 1795,
        summary = "Each letter of a message is replaced using the wheel for its place in the message, and the decoder reverses the step with the same wheels.",
        details = "Jefferson's device used many wheels, and this toy uses three fixed alphabets to show the principle rather than his arrangement. The example encodes HELLO and decodes the result back to the message.",
        instructions = """
            1. Save the example as wheels.py.
            2. Run it: python3 wheels.py
        """,
        example = """
            import string

            WHEELS = [
                "QWERTYUIOPASDFGHJKLZXCVBNM",
                "ZYXWVUTSRQPONMLKJIHGFEDCBA",
                "BDFHJLNPRTVXZACEGIKMOQSUWY",
            ]

            def encode(message):
                out = []
                for k, ch in enumerate(message):
                    index = string.ascii_uppercase.index(ch)
                    out.append(WHEELS[k % 3][index])
                return "".join(out)

            def decode(cipher):
                out = []
                for k, ch in enumerate(cipher):
                    index = WHEELS[k % 3].index(ch)
                    out.append(string.ascii_uppercase[index])
                return "".join(out)

            cipher = encode("HELLO")
            print("cipher: " + cipher)
            print("decoded: " + decode(cipher))
            print("round trip: " + str(decode(cipher) == "HELLO"))
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Logarithms",
        title = "Interpolation between table entries",
        originalTitle = "Proportional parts in printed tables of logarithms (1795)",
        author = "Jean-Francois Callet",
        year = 1795,
        summary = "Between two printed entries a logarithm is estimated by a straight line, and the error is small when the entries are close together.",
        details = "Printed tables of logarithms gave proportional parts for this purpose, so a reader could fill in values between the entries. The example estimates log10 of 2.5 and 3.5 from four-figure table values and compares each estimate with the true logarithm.",
        instructions = """
            1. Save the example as interpolate.py.
            2. Run it: python3 interpolate.py
        """,
        example = """
            import math
            from fractions import Fraction

            # Four-figure table values of log10 for 2, 3 and 4.
            table = {2: Fraction("0.3010"), 3: Fraction("0.4771"), 4: Fraction("0.6021")}

            def interpolate(x):
                # A straight line between the two entries that bracket x.
                low = int(x)
                t = Fraction(x) - low
                return table[low] + t * (table[low + 1] - table[low])

            for x in (Fraction(5, 2), Fraction(7, 2)):
                estimate = float(interpolate(x))
                true_value = math.log10(float(x))
                print(f"log10({float(x)}): interpolated {estimate:.5f}, true {true_value:.5f}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Signalling",
        title = "Six-shutter patterns on a station",
        originalTitle = "Shutter frame model (about 1796)",
        author = "British Admiralty",
        year = 1796,
        summary = "A frame of six shutters, each open or shut, gives 64 patterns, and the example counts them by how many shutters are open.",
        details = "A signal reads as one pattern of the frame, so the number of patterns sets the size of the code. The example lists all 64 patterns of six shutters and counts how many have each number of shutters open.",
        instructions = """
            1. Save the example as shutters.py.
            2. Run it: python3 shutters.py
        """,
        example = """
            from collections import Counter
            from itertools import product

            patterns = list(product((0, 1), repeat=6))
            print(f"patterns: {len(patterns)}")
            counts = Counter(sum(p) for p in patterns)
            print("open shutters 0 to 6: " + " ".join(str(counts[k]) for k in range(7)))
            first = next(p for p in patterns if sum(p) == 3)
            print("first pattern with three open: " + "".join(str(s) for s in first))
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.EXAMPLE,
        section = "Angles",
        title = "Grades in place of degrees",
        originalTitle = "Grade, one hundredth of a right angle (1795)",
        author = "French National Convention",
        year = 1795,
        summary = "A right angle is 100 grades instead of 90 degrees, so each degree is ten ninths of a grade, and the example converts a few angles exactly.",
        details = "The grade was introduced with the French decimal units of the 1790s, and the conversion uses only the ratio of a right angle in each system. The example converts 30, 45, 60 and 90 degrees to grades with exact fractions.",
        instructions = """
            1. Save the example as grades.py.
            2. Run it: python3 grades.py
        """,
        example = """
            from fractions import Fraction

            def to_grades(degrees):
                # A right angle is 90 degrees and also 100 grades, so grades = degrees times 100 / 90.
                return Fraction(degrees) * 100 / 90

            for degrees in (30, 45, 60, 90):
                print(f"{degrees} degrees = {to_grades(degrees)} grades")
            print("right angle check: " + str(to_grades(90) == 100))
        """,
    ),
)
